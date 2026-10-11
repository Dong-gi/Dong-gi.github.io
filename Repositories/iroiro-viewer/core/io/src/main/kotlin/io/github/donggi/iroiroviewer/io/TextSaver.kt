package io.github.donggi.iroiroviewer.io

import io.github.donggi.iroiroviewer.model.IroDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * 파일 하나의 크기·수정 시각 — 글을 연 때와 저장할 때 사이에 **다른 곳에서** 바뀌었는가를 가르는 표(글 편집).
 *
 * 내용의 해시가 아니라 이 둘인 까닭은 값이다 — 저장할 때마다 파일을 다시 읽지 않는다. 다른 앱이 같은 크기로 같은 밀리초 안에 고쳐
 * 쓰면 못 알아보지만, 사람이 손으로 고치는 글에서 그런 일은 없다고 본다. 수정 시각은 `File.lastModified` 의 밀리초다.
 */
data class FileStamp(val size: Long, val modified: Long) {
    companion object {
        /** 지금 [file] 의 표. 보통 파일이 아니거나 없으면 null. 디스크를 읽으므로 입출력 디스패처에서 부른다. */
        fun of(file: File): FileStamp? = if (file.isFile) FileStamp(file.length(), file.lastModified()) else null
    }
}

/**
 * 글 파일을 **고쳐 쓰는** 길과 **새로 만드는** 길(사용자 요청 '글·코드 파일 편집과 새 글 파일 만들기'). 사용자 파일을
 * 쓰는 코드라 `core:io` 에 두고 파일 작업 큐([FileOpManager])로만 부른다.
 *
 * ## 고쳐 쓰기 — 원본을 파괴할 수 있는 아홉째 길
 *
 * (열째는 편집기가 돌려준 문서의 저장이다 — `EditorSaver`.)
 *
 * 1. **어디에 쓰는가** — 링크를 따라간 자리(정규화한 경로). 저장 볼륨(`/storage/`) 아래의 보통 파일이고 휴지통 안이 아니어야 한다
 *    ([placeOf] — 편집 단추를 내는 판단과 같은 함수다. 화면이 판단을 빠뜨려도 여기가 마지막 관문이다).
 * 2. **밖에서 바뀌었는가** — 연 때의 표([FileStamp])와 지금이 다르면(지워졌어도) 쓰지 않고 [FileOpEngine.Reason.CHANGED_ELSEWHERE].
 *    사용자가 '덮어쓰기' 를 고르면 다시 넣는다(`overwriteChanged`).
 * 3. **고치기 전 판** — 세션의 첫 저장이면 지금 파일의 사본을 같은 볼륨의 휴지통에 넣는다([TrashStore.copyToTrash]). 못 넣으면 쓰지
 *    않고 [FileOpEngine.Reason.PREVIOUS_NOT_KEPT] — 말없이 넘어가지 않는다(사용자가 '그래도 저장' 을 고르면 `withoutPrevious`).
 *    빈 파일은 넣지 않는다(새 글 파일의 첫 저장 — 남길 글이 없다).
 * 4. **쓰기** — 임시본 → fsync → 원자적 rename → 부모 fsync([AtomicFileWriter]). **원본 경로에 파일이 없는 순간이 없다** — rename(2)
 *    은 있는 파일을 원자적으로 대체한다. 수정 시각은 지금이 된다(내용이 바뀌었다). 쓰기가 실패하면 3의 사본을 거둔다 — 원본이 그대로라
 *    같은 내용이 '고치기 전 판' 으로 남으면 헷갈린다.
 *
 * 3·4 는 쪼개지지 않는다(`NonCancellable`) — 사본만 넣고 취소되면 휴지통에 같은 글이 하나 더 남고, 쓴 뒤의 취소가 '취소했습니다' 를
 * 말하면 거짓이다. 놓은 자리에서 [FileOpManager] 에 알린다(`onPlaced` — 잘라 저장·구간 저장과 같은 까닭, 함정 표의 `withContext`).
 *
 * ## 새로 만들기
 *
 * 빈 파일을 `O_EXCL` 로 만든다 — 이름을 맡는 것과 만드는 것이 한 번의 `open(2)` 이다([FileOpEngine.reserveName]). 비어 있으므로 UTF-8
 * 이고 BOM 은 쓰지 않는다. 이미 있으면 [FileOpEngine.Reason.EXISTS].
 */
object TextSaver {

    /** 편집할 수 있는 자리인가 — 그 까닭. */
    enum class Place {
        /** 저장 볼륨의 보통 파일이고 쓸 수 있다. */
        OK,

        /** 저장 볼륨 밖(앱 캐시의 받은 사본·원격 사본, 앱 내부)이거나 휴지통 안이다. */
        OUTSIDE,

        /** 보통 파일이 아니다(없다·폴더·장치). */
        NOT_FILE,

        /** 쓸 수 없다(읽기 전용 볼륨·권한). 임시본을 만들 폴더에도 쓸 수 있어야 한다. */
        READ_ONLY,
    }

    /** [path] 를 편집할 수 있는가. 정규화(링크를 따라간다)하므로 입출력 디스패처에서 부른다. */
    fun placeOf(path: String): Place {
        val file = try {
            File(path).canonicalFile
        } catch (e: IOException) {
            return Place.OUTSIDE
        }
        return placeOf(
            canonicalPath = file.path,
            isFile = file.isFile,
            writable = file.canWrite() && file.parentFile?.canWrite() == true,
        )
    }

    /**
     * 판단만 — JVM 시험이 답한다. 받은 사본(`share/inbox`)은 앱 캐시라 저장 볼륨 밖이다 —
     * 따로 가리지 않아도 여기서 빠진다(`ShareHelper.shareDir`). 압축 파일 안의 항목은 애초에 경로가 없다.
     */
    internal fun placeOf(canonicalPath: String, isFile: Boolean, writable: Boolean): Place = when {
        !IncomingFiles.isUnderStorage(canonicalPath) || TrashStore.isTrashPath(canonicalPath) -> Place.OUTSIDE
        !isFile -> Place.NOT_FILE
        !writable -> Place.READ_ONLY
        else -> Place.OK
    }

    /**
     * 고친 글을 쓴다([FileOpManager.Request.SaveText] — 차례는 머리말). 파일 작업 큐의 작업 스레드에서 돈다.
     *
     * @param onExtra 쓴 뒤의 표([FileOpManager.Extra.TextSaved]).
     * @param onPlaced 썼다 — 그 뒤에 온 취소가 결과를 '취소' 로 바꾸지 않게 큐에 먼저 알린다.
     */
    suspend fun save(
        request: FileOpManager.Request.SaveText,
        volumes: List<VolumeRegistry.Volume>,
        trash: TrashStore,
        media: MediaIndex,
        onExtra: (FileOpManager.Extra) -> Unit,
        onPlaced: (FileOpEngine.Outcome) -> Unit,
    ): FileOpEngine.Outcome = withContext(IroDispatchers.io) {
        val shownName = File(request.path).name
        val target = try {
            File(request.path).canonicalFile
        } catch (e: IOException) {
            return@withContext FileOpEngine.Outcome.Failed(FileOpEngine.Reason.IO, shownName)
        }
        val dir = target.parentFile
        if (dir == null || !dir.isDirectory) {
            return@withContext FileOpEngine.Outcome.Failed(FileOpEngine.Reason.NOT_FOUND, shownName)
        }
        // 마지막 관문 — 저장 볼륨 밖·휴지통 안에는 쓰지 않는다. 그 자리에 폴더가 생겼으면 덮지 않는다(rename 이 폴더를 대체하지 못하고,
        // '덮어쓰기' 를 다시 물어도 같은 자리에서 멈춘다 — 그래서 '바뀌었다' 가 아니라 '찾을 수 없다' 다).
        if (!IncomingFiles.isUnderStorage(target.path) || TrashStore.isTrashPath(target.path)) {
            return@withContext FileOpEngine.Outcome.Failed(FileOpEngine.Reason.PERMISSION, shownName)
        }
        if (target.isDirectory) return@withContext FileOpEngine.Outcome.Failed(FileOpEngine.Reason.NOT_FOUND, shownName)

        // 밖에서 바뀌었는가. 지워진 것도 바뀐 것이다 — 덮어쓰기를 고르면 다시 만든다.
        val now = FileStamp.of(target)
        if (!request.overwriteChanged && now != request.expected) {
            return@withContext FileOpEngine.Outcome.Failed(FileOpEngine.Reason.CHANGED_ELSEWHERE, shownName)
        }

        withContext(NonCancellable) {
            // 죽은 쓰기가 남긴 임시본을 걷는다(회전과 같다 — 편집은 어느 폴더에서나 일어난다).
            AtomicFileWriter.sweepPartials(dir)
            // 빈 파일(방금 만든 새 글 파일)은 남길 것이 없다 — 휴지통에 빈 '고치기 전 판' 을 쌓지 않는다.
            val worthKeeping = request.keepPrevious && now != null && now.size > 0
            val kept = if (worthKeeping) trash.copyToTrash(target.path, volumes) else null
            if (worthKeeping && kept == null && !request.withoutPrevious) {
                return@withContext FileOpEngine.Outcome.Failed(FileOpEngine.Reason.PREVIOUS_NOT_KEPT, shownName)
            }
            try {
                AtomicFileWriter.write(target, lastModified = 0L) { out -> out.write(request.bytes) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // 원본은 그대로다 — 방금 넣은 사본은 지금 파일과 같은 글이라 거둔다.
                kept?.let { trash.dropCopy(it, volumes) }
                Iro.d { "글을 쓰지 못했다: ${e.javaClass.simpleName}" }
                return@withContext FileOpEngine.Outcome.Failed(FileOpEngine.reasonOf(e), shownName)
            }
            val done = FileOpEngine.Outcome.Done(1, 0, 0)
            val stamp = FileStamp.of(target) ?: FileStamp(request.bytes.size.toLong(), target.lastModified())
            onExtra(FileOpManager.Extra.TextSaved(target.path, stamp, keptPrevious = kept != null))
            onPlaced(done)
            media.scanAll(listOf(target.path))
            done
        }
    }

    /**
     * 빈 글 파일을 만든다([FileOpManager.Request.CreateText]). 이름은 사용자가 친 그대로를 검사만 한다([PathRules.validate] — 고쳐
     * 주지 않는다. 새 폴더와 같다).
     */
    suspend fun create(
        request: FileOpManager.Request.CreateText,
        media: MediaIndex,
        onExtra: (FileOpManager.Extra) -> Unit,
        onPlaced: (FileOpEngine.Outcome) -> Unit,
    ): FileOpEngine.Outcome = withContext(IroDispatchers.io) {
        val verdict = PathRules.validate(request.name)
        if (verdict is PathRules.Verdict.Rejected) {
            return@withContext FileOpEngine.Outcome.Failed(FileOpEngine.Reason.INVALID_NAME, request.name)
        }
        val name = (verdict as PathRules.Verdict.Ok).name
        val dir = try {
            File(request.dir).canonicalFile
        } catch (e: IOException) {
            return@withContext FileOpEngine.Outcome.Failed(FileOpEngine.Reason.IO, name)
        }
        if (!dir.isDirectory) return@withContext FileOpEngine.Outcome.Failed(FileOpEngine.Reason.NOT_FOUND, name)
        if (!IncomingFiles.isUnderStorage(dir.path) || TrashStore.isTrashPath(dir.path)) {
            return@withContext FileOpEngine.Outcome.Failed(FileOpEngine.Reason.PERMISSION, name)
        }
        val target = File(dir, name)
        withContext(NonCancellable) {
            FileOpEngine.reserveName(target, isDirectory = false)?.let { why ->
                return@withContext FileOpEngine.Outcome.Failed(why, name)
            }
            AtomicFileWriter.syncDirectory(dir)
            val done = FileOpEngine.Outcome.Done(1, 0, 0)
            onExtra(FileOpManager.Extra.Saved(target.path))
            onPlaced(done)
            media.scanAll(listOf(target.path))
            done
        }
    }
}
