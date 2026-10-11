package io.github.donggi.iroiroviewer.io

import io.github.donggi.iroiroviewer.model.IroDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException

/**
 * **편집기**(`feature:editor` — [EditorApp])가 저장한 문서를 파일에 쓴다(편집기 연동). 원본을 파괴할 수 있는 길이라 파일 작업
 * 큐([FileOpManager.Request.SaveFromEditor])로만 부른다 — 원본을 파괴할 수 있는 열째 길이다(CLAUDE.md '지켜야 할 규칙' 의 목록).
 *
 * 편집기는 '저장' 할 때마다 문서 **전체**를 Files 의 저장 자리(`app` 의 `EditorSlotProvider` — 앱 캐시)에 쓰고 반영을 부른다. 여기는 그
 * 바이트를 받아 놓는 차례만 안다(형식을 풀지 않는다 — 엔진이 지은 것이다):
 *
 * 1. **자리** — 저장 볼륨 아래이고 휴지통 안이 아니어야 한다([NewDocuments] 와 같은 마지막 관문). 고쳐 쓰기는 보통 파일이 있어야 한다.
 * 2. **바이트** — 비어 있지 않고 [LIMIT] 안이며, 앞머리가 형식과 맞아야 한다([looksLike] — HWP 는 CFB, HWPX·ODF 는 ZIP, OOXML 은 ZIP 또는
 *    암호를 건 CFB, 글은 다른 그릇이 아닐 것, PDF 는 `%PDF-`).
 *    엉뚱한 바이트(반쯤 쓴 것·다른 형식)로 원본을 덮지 않는다. 아니면 [FileOpEngine.Reason.VERIFY_FAILED].
 * 3. **고쳐 쓰기** — 같은 폴더의 임시본에 옮겨 적는다. 마지막으로 취소를 본 뒤로는 쪼개지지 않는다(`NonCancellable`): 세션의 첫 저장이거나
 *    **지금 표가 요청이 기대한 표와 다르면**([keepsPrevious] — 큐 안에서 본다) 지금 판의 사본을 휴지통에 두고(못 두면 쓰지
 *    않는다 — [FileOpEngine.Reason.PREVIOUS_NOT_KEPT]), fsync → 원자적 rename → 폴더 fsync. **원본 경로에 파일이 없는 순간이 없다.** 놓지
 *    못하면 방금 둔 사본을 거둔다.
 * 4. **새로 만들기**([createNew] — 편집기가 형식이나 이름을 바꿔 저장했다) — 그 이름이 비었으면 그대로, 있으면 `(2)`([NewFiles.place] —
 *    남의 파일을 덮지 않는다). 만든 경로는 [FileOpManager.Extra.DocSaved] 가 싣는다.
 */
object EditorSaver {

    /** 문서 하나의 상한 — 편집기가 받는 상한과 같다(512 MiB). */
    const val LIMIT = 512L * 1024 * 1024

    /**
     * 편집기가 저장한 바이트([FileOpManager.Request.SaveFromEditor.source])를 쓴다. 파일 작업 큐의 작업 스레드에서 돈다.
     *
     * @param onExtra 놓은 뒤의 표([FileOpManager.Extra.DocSaved] — 실제 경로와 쓴 뒤의 크기·시각).
     * @param onPlaced 놓았다 — 그 뒤에 온 취소가 결과를 '취소' 로 바꾸지 않게 큐에 먼저 알린다.
     */
    suspend fun save(
        request: FileOpManager.Request.SaveFromEditor,
        volumes: List<VolumeRegistry.Volume>,
        trash: TrashStore,
        media: MediaIndex?,
        onExtra: (FileOpManager.Extra) -> Unit,
        onPlaced: (FileOpEngine.Outcome) -> Unit,
    ): FileOpEngine.Outcome = withContext(IroDispatchers.io) {
        val shownName = File(request.path).name
        val wanted = try {
            File(request.path).canonicalFile
        } catch (e: IOException) {
            return@withContext FileOpEngine.Outcome.Failed(FileOpEngine.Reason.IO, shownName)
        }
        val dir = wanted.parentFile
        if (dir == null || !dir.isDirectory) return@withContext FileOpEngine.Outcome.Failed(FileOpEngine.Reason.NOT_FOUND, shownName)
        if (!IncomingFiles.isUnderStorage(wanted.path) || TrashStore.isTrashPath(wanted.path)) {
            return@withContext FileOpEngine.Outcome.Failed(FileOpEngine.Reason.PERMISSION, shownName)
        }
        // 고쳐 쓸 원본이 사라졌거나 폴더가 됐다 — 새로 만들지 않는다(받은 것은 그 원본을 고친 판이다. 지운 사람의 뜻을 거스르지 않는다).
        if (!request.createNew && !wanted.isFile) return@withContext FileOpEngine.Outcome.Failed(FileOpEngine.Reason.NOT_FOUND, shownName)

        val source = File(request.source)
        val size = source.length()
        if (!source.isFile || size <= 0 || size > LIMIT || !looksLike(request.kind, head(source))) {
            Iro.d { "편집기가 보낸 바이트가 형식과 맞지 않는다: kind=${request.kind} size=$size" }
            return@withContext FileOpEngine.Outcome.Failed(FileOpEngine.Reason.VERIFY_FAILED, shownName)
        }

        AtomicFileWriter.sweepPartials(dir)
        val pending = try {
            AtomicFileWriter.begin(wanted, lastModified = 0L)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            return@withContext FileOpEngine.Outcome.Failed(FileOpEngine.reasonOf(e), shownName)
        }
        var handedOff = false
        try {
            try {
                FileInputStream(source).use { input -> input.copyTo(pending.stream, BUFFER) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Iro.d { "편집기의 문서를 옮겨 적지 못했다: ${e.javaClass.simpleName}" }
                return@withContext FileOpEngine.Outcome.Failed(NewFiles.reasonOfWrite(e, dir), shownName)
            }
            // 여기까지는 취소하면 임시본을 버린다. 이 뒤로는 받지 않는다.
            currentCoroutineContext().ensureActive()
            handedOff = true
            withContext(NonCancellable) {
                if (request.createNew) {
                    placeNew(pending, dir, wanted.name, media, onExtra, onPlaced, shownName)
                } else {
                    replace(request, wanted, pending, volumes, trash, media, onExtra, onPlaced, shownName)
                }
            }
        } finally {
            if (!handedOff) withContext(NonCancellable) { pending.abort() }
        }
    }

    /** 3 — 고치기 전 판을 휴지통에 두고 제자리에 놓는다. `NonCancellable` 안에서 부른다. */
    private suspend fun replace(
        request: FileOpManager.Request.SaveFromEditor,
        target: File,
        pending: AtomicFileWriter.Pending,
        volumes: List<VolumeRegistry.Volume>,
        trash: TrashStore,
        media: MediaIndex?,
        onExtra: (FileOpManager.Extra) -> Unit,
        onPlaced: (FileOpEngine.Outcome) -> Unit,
        shownName: String,
    ): FileOpEngine.Outcome {
        val before = FileStamp.of(target)
        val worthKeeping = keepsPrevious(request.keepPrevious, request.expected, before)
        val kept = if (worthKeeping) trash.copyToTrash(target.path, volumes) else null
        if (worthKeeping && kept == null) {
            pending.abort()
            return FileOpEngine.Outcome.Failed(FileOpEngine.Reason.PREVIOUS_NOT_KEPT, shownName)
        }
        try {
            pending.commit()
        } catch (e: Exception) {
            pending.abort()
            kept?.let { trash.dropCopy(it, volumes) }
            Iro.d { "편집기의 문서를 놓지 못했다: ${e.javaClass.simpleName}" }
            return FileOpEngine.Outcome.Failed(FileOpEngine.reasonOf(e), shownName)
        }
        val done = FileOpEngine.Outcome.Done(1, 0, 0)
        val stamp = FileStamp.of(target) ?: FileStamp(target.length(), target.lastModified())
        onExtra(FileOpManager.Extra.DocSaved(target.path, stamp, keptPrevious = kept != null))
        onPlaced(done)
        media?.scanAll(listOf(target.path))
        return done
    }

    /**
     * 고쳐 쓰기 전에 지금 판([now])을 휴지통에 남기는가 — 판단만(JVM 시험). 세션의 첫 저장이면([keepPrevious]) 남기고, 아니어도 **지금 표가 넣는
     * 쪽이 기대한 표([expected])와 다르면** 남긴다. 넣는 쪽은 넣기 전에만 견줄 수 있다 — 반영이 앞 작업(긴 복사)에 밀려 줄
     * 서 있는 사이 동기화 앱·PC 가 원본을 바꾸면, 그 판을 남기지 않고 덮었다(되돌릴 수 없다). 그래서 글 저장([TextSaver])처럼 큐 안에서 다시 본다.
     * 기대한 표를 모르면(null) 남긴다. 빈 파일·없는 파일은 남길 것이 없다.
     */
    internal fun keepsPrevious(keepPrevious: Boolean, expected: FileStamp?, now: FileStamp?): Boolean =
        now != null && now.size > 0 && (keepPrevious || now != expected)

    /** 4 — 곁에 새 이름으로 놓는다(있으면 `(2)`). `NonCancellable` 안에서 부른다. */
    private suspend fun placeNew(
        pending: AtomicFileWriter.Pending,
        dir: File,
        name: String,
        media: MediaIndex?,
        onExtra: (FileOpManager.Extra) -> Unit,
        onPlaced: (FileOpEngine.Outcome) -> Unit,
        shownName: String,
    ): FileOpEngine.Outcome {
        val done = FileOpEngine.Outcome.Done(1, 0, 0)
        return try {
            NewFiles.place(pending, dir, name, media) { placed ->
                val stamp = FileStamp.of(placed) ?: FileStamp(placed.length(), placed.lastModified())
                onExtra(FileOpManager.Extra.DocSaved(placed.path, stamp, keptPrevious = false))
                onPlaced(done)
            }
            done
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            pending.abort()
            FileOpEngine.Outcome.Failed(NewFiles.reasonOf(e), shownName)
        }
    }

    private fun head(file: File): ByteArray = try {
        FileInputStream(file).use { input ->
            val b = ByteArray(HEAD)
            var n = 0
            while (n < b.size) {
                val r = input.read(b, n, b.size - n)
                if (r < 0) break
                n += r
            }
            b.copyOf(n)
        }
    } catch (e: IOException) {
        ByteArray(0)
    }

    /**
     * [head] 가 [kind] 의 앞머리인가 — 판단만(JVM 시험). 엔진이 지은 파일을 다시 풀어 보지는 않는다(편집기가 쓰는 형식은 우리 변환기가
     * 고치는 형식보다 넓다). 그릇만 맞으면 지나간다 — 엉뚱한 바이트를 막는 겹이다.
     *
     * OOXML 은 ZIP 이거나, 암호를 건 저장이면 CFB 다(MS-OFFCRYPTO — 암호 문서를 고쳐 저장하면 같은 암호가 걸린다). ODF 는 암호가 걸려도 ZIP 이다.
     * 글(txt·csv)은 그릇이 없다 — 다른 형식의 그릇(ZIP·CFB·PDF)으로 시작하지만 않으면 지나간다. 편집기가 같은 형식으로 쓰지 않는 형식
     * (이전 오피스 형식·PDF 를 연 것은 OOXML 로 저장한다)은 받지 않는다 — PDF 는 예외로 남겼다(앞으로 PDF 를 쓰는 엔진이 붙을 때).
     */
    internal fun looksLike(kind: String, head: ByteArray): Boolean = when (kind) {
        "hwp" -> head.startsWith(CFB)
        "hwpx", "odt", "ods", "odp" -> head.startsWith(ZIP)
        "docx", "docm", "dotx", "dotm", "xlsx", "xlsm", "xltx", "xltm", "pptx", "pptm", "ppsx", "ppsm", "potx", "potm" ->
            head.startsWith(ZIP) || head.startsWith(CFB)
        "txt", "csv" -> head.isNotEmpty() && !head.startsWith(ZIP) && !head.startsWith(CFB) && !head.startsWith(PDF)
        "pdf" -> head.startsWith(PDF)
        else -> false
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private const val HEAD = 8
    private const val BUFFER = 256 * 1024
    private val CFB = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte())
    private val ZIP = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    private val PDF = "%PDF-".toByteArray(Charsets.US_ASCII)
}
