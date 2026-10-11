package io.github.donggi.iroiroviewer.io

import android.annotation.SuppressLint
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * **새 파일**을 남의 파일을 덮지 않고 놓는 자리 — ZIP 만들기·잘라 저장(그림)·구간 저장(소리·영상)이 함께 쓴다.
 *
 * 셋 다 원본을 고치지 않고 곁에 새 파일을 만든다. 그래도 **이름을 잘못 고르면 남의 파일을 덮는다** — `rename(2)` 는 있는 이름을
 * 말없이 대체한다. 그래서 [AtomicFileWriter] 의 임시본에 다 쓴 뒤, **끝에서** 없는 이름을 `O_EXCL` 로 맡고([claim]) 그 위로 옮긴다.
 * 처음에 맡지 않는 까닭은 [ZipMaker] 머리말에 있다(쓰는 내내 0바이트 파일이 목록에 떠 있고, 누르면 깨진 파일로 열린다).
 * 놓는 차례는 [place] 한 곳이 정한다.
 */
object NewFiles {

    /**
     * 다 쓴 임시본을 새 이름에 놓는다 — 셋(ZIP 만들기·잘라 저장·구간 저장)이 함께 쓰는 **마지막 걸음**이다.
     *
     * 1. **내용을 먼저 디스크에 내린다**([AtomicFileWriter.Staged.sync]). 큰 파일이면 몇 초 걸린다 — 이름을 먼저 맡으면 그동안 0바이트
     *    파일이 목록에 떠 있고, 누른 사람은 깨진 파일을 연다.
     * 2. 취소를 마지막으로 본다. 여기까지는 취소하면 임시본을 버린다(부르는 쪽의 몫).
     * 3. **그 뒤로는 취소를 받지 않는다** — 이름을 맡고([claim]) 옮기고 미디어 색인에 알리기는 순간이다. 그 사이 취소를 받으면 파일은
     *    놓였는데 '저장을 취소했습니다' 를 말하게 된다. 놓은 뒤 [onPlaced] 를 부른다 — 부른 쪽의 코루틴이 그사이
     *    취소됐어도 부른다. 큐는 그것으로 '놓였다' 를 안다(`FileOpManager.execute`).
     *
     * @param exact 그 이름 그대로만 맡는다 — 있으면 `(2)` 로 넘어가지 않고 [FileOpEngine.Reason.EXISTS] 로 멈춘다. 사용자가 이름을
     *   친 새 오피스 문서([NewDocuments])가 쓴다: 이름 칸이 '같은 이름이 이미 있습니다' 를 칸 안에서 말한다(새 폴더와 같다).
     * @throws ClaimFailed 이름을 맡지 못했다(부르는 쪽이 임시본을 버리고 그 까닭을 말한다).
     */
    suspend fun place(
        staged: AtomicFileWriter.Staged,
        dir: File,
        name: String,
        media: MediaIndex?,
        exact: Boolean = false,
        onPlaced: (File) -> Unit,
    ): File {
        staged.sync()
        currentCoroutineContext().ensureActive()
        return withContext(NonCancellable) {
            val target = claim(dir, name, exact)
            try {
                staged.moveInto(target)
            } catch (t: Throwable) {
                // 맡아 둔 빈 자리를 되돌린다. 우리가 만든 0바이트 파일일 때만 — 그 사이 무엇이 덮었는지 모른다.
                if (target.length() == 0L) target.delete()
                throw t
            }
            media?.scanAll(listOf(target.absolutePath))
            onPlaced(target)
            target
        }
    }

    /**
     * 쓰기가 까닭 없이 실패했을 때(플랫폼의 그릇이 던진 `IllegalStateException`, `Bitmap.compress` 의 false) — 공간이 거의 없으면
     * 그것이 까닭이다. 그런 실패는 errno 를 싣지 않아 [FileOpEngine.reasonOf] 가 '입출력' 으로 읽는다(디스크가 찬
     * 것을 '이 형식은 저장할 수 없습니다' 로 말했다).
     */
    @SuppressLint("UsableSpace") // 남길 몫을 재는 자리라 보수적인 값이 맞다(ZIP 만들기와 같다).
    fun reasonWhenSilent(dir: File, otherwise: FileOpEngine.Reason): FileOpEngine.Reason = when {
        // 폴더가 사라졌으면(SD 카드를 뺐다) `usableSpace` 도 0 이다 — 공간 부족으로 읽으면 틀린다.
        !dir.isDirectory -> FileOpEngine.Reason.NOT_FOUND
        dir.usableSpace < LOW_SPACE_BYTES -> FileOpEngine.Reason.NO_SPACE
        else -> otherwise
    }

    /**
     * 임시본에 쓰다 던진 것을 화면이 말할 까닭으로 — errno 를 실은 것은 그대로([FileOpEngine.reasonOf]), 까닭 없는 입출력 실패는 공간이
     * 거의 없으면 공간 부족으로 읽는다([reasonWhenSilent] — ZIP 을 쓰다 디스크가 차면 errno 없이 오는 일이 있다). 빈 문서
     * ([NewDocuments])와 편집기의 저장([EditorSaver])이 쓴다.
     */
    fun reasonOfWrite(e: Throwable, dir: File): FileOpEngine.Reason {
        val reason = FileOpEngine.reasonOf(e)
        return if (reason == FileOpEngine.Reason.IO) reasonWhenSilent(dir, FileOpEngine.Reason.IO) else reason
    }

    /** 까닭을 아는 쓰기 실패. [reasonOf] 가 그 까닭을 그대로 읽는다. */
    open class Failed(val reason: FileOpEngine.Reason) : IOException()

    /** 이름을 맡지 못했다. [reason] 이 화면이 말할 까닭이다. */
    class ClaimFailed(reason: FileOpEngine.Reason) : Failed(reason)

    /**
     * 없는 이름을 `O_EXCL` 로 맡는다. 맡은 빈 파일 위로 임시본이 원자적으로 옮겨 간다(`Pending.commit(into = …)`).
     *
     * **'이미 있다'(EEXIST)만 다음 이름으로 넘어간다.** 읽기 전용 볼륨·권한 오류를 이름 충돌로 보면 같은 이름을 여덟 번 다시
     * 해 보고 '쓸 수 없는 이름입니다' 로 끝났다 — 그 밖의 오류는 그 까닭으로 멈춘다.
     *
     * @param exact 번호를 붙이지 않는다 — 있으면 [FileOpEngine.Reason.EXISTS]([place] 의 같은 인자).
     * @throws ClaimFailed 맡지 못했다.
     */
    fun claim(dir: File, name: String, exact: Boolean = false): File {
        var candidate = name
        repeat(CLAIM_TRIES) {
            val target = File(dir, candidate)
            try {
                val fd = Os.open(
                    target.absolutePath,
                    OsConstants.O_CREAT or OsConstants.O_EXCL or OsConstants.O_WRONLY,
                    OsConstants.S_IRUSR or OsConstants.S_IWUSR or OsConstants.S_IRGRP or OsConstants.S_IROTH,
                )
                Os.close(fd)
                return target
            } catch (e: ErrnoException) {
                // 사용자가 친 이름이면 까닭을 새 폴더처럼 가른다(`FileOpEngine.reserveName` — 볼륨이 받지 않는 이름은 '쓸 수 없는 이름').
                if (exact) {
                    throw ClaimFailed(
                        when (e.errno) {
                            OsConstants.EEXIST -> FileOpEngine.Reason.EXISTS
                            OsConstants.EINVAL, OsConstants.ENAMETOOLONG -> FileOpEngine.Reason.INVALID_NAME
                            else -> FileOpEngine.reasonOf(e)
                        },
                    )
                }
                if (e.errno != OsConstants.EEXIST) throw ClaimFailed(FileOpEngine.reasonOf(e))
            }
            candidate = try {
                PathRules.nextAvailable(dir, name)
            } catch (e: IOException) {
                throw ClaimFailed(FileOpEngine.Reason.INVALID_NAME)
            }
        }
        throw ClaimFailed(FileOpEngine.Reason.INVALID_NAME)
    }

    /**
     * 원본 이름에서 새 이름을 짓는다 — `사진.jpg`, `crop`, `jpg` → `사진_crop.jpg`.
     *
     * 255바이트를 넘으면 **원본 이름의 앞부분만** 자른다 — 꼬리(`_crop`)와 확장자는 남긴다. `PathRules.truncateToBytes` 로 통째로
     * 자르면 꼬리가 잘려 원본과 같은 이름이 되고, 그러면 사용자는 어느 것이 새 파일인지 모른다. 원본 이름이 비었으면
     * ([FALLBACK_BASE]) 꼬리만 남는다.
     */
    fun derivedName(sourceName: String, tag: String, extension: String): String {
        val dot = sourceName.lastIndexOf('.')
        val base = (if (dot > 0) sourceName.substring(0, dot) else sourceName).trim().ifEmpty { FALLBACK_BASE }
        val tail = "_$tag.$extension"
        val room = (PathRules.MAX_NAME_BYTES - tail.toByteArray().size).coerceAtLeast(1)
        return PathRules.cutToBytes(base, room).ifEmpty { FALLBACK_BASE } + tail
    }

    /** 확장자(점 없이, 소문자). 없으면 빈 문자열. */
    fun extensionOf(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot > 0) name.substring(dot + 1).lowercase(java.util.Locale.ROOT) else ""
    }

    /** 예외를 화면이 말할 까닭으로. 다른 모듈의 쓰기(잘라 저장의 그리기)가 쓴다 — 판단은 [FileOpEngine] 의 것 하나다. */
    fun reasonOf(t: Throwable): FileOpEngine.Reason = if (t is Failed) t.reason else FileOpEngine.reasonOf(t)

    private const val CLAIM_TRIES = 8

    /** 이보다 적게 남았으면 까닭 없는 쓰기 실패를 '공간 부족' 으로 읽는다. 큰 그릇의 색인 한 벌이 이만큼은 든다. */
    private const val LOW_SPACE_BYTES = 8L * 1024 * 1024

    /** 원본 이름이 비었을 때(`.jpg` 같은 이름은 없지만, 확장자만 남는 경우를 막는다). */
    private const val FALLBACK_BASE = "file"
}
