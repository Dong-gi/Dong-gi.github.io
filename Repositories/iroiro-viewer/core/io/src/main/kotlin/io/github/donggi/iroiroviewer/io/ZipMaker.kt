package io.github.donggi.iroiroviewer.io

import android.annotation.SuppressLint
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import io.github.donggi.iroiroviewer.model.IroDispatchers
import io.github.donggi.iroiroviewer.safety.ParseLimits
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.file.attribute.FileTime
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 고른 파일·폴더를 ZIP 하나로 묶는다(사용자 요청 'zip 만들기').
 *
 * ## 라이브러리를 늘리지 않는다
 *
 * 플랫폼의 `java.util.zip.ZipOutputStream`(안드로이드 7부터 OpenJDK 의 것)이 필요한 것을 다 한다 — 이름은 UTF-8 로 쓰고
 * 그 표시(일반 목적 비트 11)를 켠다, 4 GiB·65,535개를 넘으면 ZIP64 로 쓴다, `setLastModifiedTime` 이 확장 시각(0x5455,
 * UTC)을 함께 적는다. commons-compress 는 `format:archive` 에 있어 `core:io` 가 볼 수 없고(의존 표), 볼 필요도 없다.
 *
 * ## 원본을 파괴하지 않는다 — 여섯 번째 길
 *
 * 만드는 것은 새 파일이지만 **이름을 잘못 고르면 남의 파일을 덮는다**(`rename(2)` 는 말없이 대체한다). 그래서
 * [AtomicFileWriter] 의 임시본에 다 쓴 뒤, **끝에서** 없는 이름을 `O_EXCL` 로 맡고 그 위로 옮긴다. 화면이 대화상자에서
 * 고른 이름은 제안일 뿐이다 — 묶는 동안 같은 이름이 생겼으면 `이름 (2).zip` 이 된다(결과가 실제 이름을 싣는다). 맡기를
 * 처음이 아니라 끝에 하는 까닭은, 처음에 맡으면 묶는 내내 0바이트짜리 `.zip` 이 목록에 떠 있고 누르면 '깨진 압축 파일' 이
 * 되기 때문이다.
 *
 * ## 고르는 것과 거르는 것
 *
 * - 링크는 따라가지 않고 담지도 않는다(복사·이동과 같다 — 링크 너머는 고른 폴더 밖일 수 있다). 장치·소켓·파이프도.
 * - 빈 폴더도 담는다(`이름/` 항목) — 풀었을 때 폴더 구조가 그대로여야 한다.
 * - 열리지 않는 파일은 담지 않고 센다. **읽는 도중에 실패하면 전부 버린다** — 쓰다 만 항목은 ZIP 에서 지울 수 없고, 그런
 *   ZIP 은 푸는 쪽에서 'CRC 오류' 로 드러나 사용자가 백업을 믿은 뒤에야 알게 된다.
 * - 이미 압축된 형식(사진·영상·음악·압축 파일)은 다시 압축하지 않는다('저장' — 머리에 적을 크기와 CRC 를 재느라 두 번
 *   읽는다). 압축해도 줄지 않으면서 폰에서 몇 배 느리다.
 *
 * ## 저장 공간
 *
 * 다 쓰기 전에는 결과의 크기를 모른다(글은 줄고 영상은 그대로다). 미리 거절하지 않고, 쓰는 동안 [SPACE_CHECK_BYTES] 마다
 * 남은 공간을 보고 [SPACE_MARGIN_BYTES] 밑으로 내려가면 멈춘다 — 디스크를 0 까지 채우면 다른 앱과 시스템이 함께 넘어진다.
 */
object ZipMaker {

    /**
     * 담을 항목 수(폴더 포함)의 상한 — **이 앱이 다시 열 수 있는 수**다(`ParseLimits` 의 엔트리 상한, 압축 목록과 풀기가 쓴다).
     * 처음에는 50만으로 두었는데, 그러면 사진 1만 2천 장을 묶은 ZIP 을 같은 앱이 '너무 크다' 로 열지 못했다.
     */
    val MAX_ENTRIES: Int = ParseLimits.DEFAULT.maxEntries

    /** 남겨 둘 공간. 이 밑으로는 쓰지 않는다. */
    const val SPACE_MARGIN_BYTES = 64L * 1024 * 1024

    /** 남은 공간을 다시 보는 간격(쓴 바이트). */
    private const val SPACE_CHECK_BYTES = 16L * 1024 * 1024

    private const val BUFFER = 64 * 1024

    /** 계획한 항목 하나. [name] 은 ZIP 안의 경로다(폴더면 `/` 로 끝난다). */
    internal data class Item(val file: File, val name: String, val isDirectory: Boolean, val size: Long, val modified: Long)

    /** 묶기 전에 훑은 결과. [skipped] 는 링크·특수 파일, [unreadable] 은 열 수 없는 폴더다. */
    internal data class Plan(val items: List<Item>, val skipped: Int, val unreadable: Int, val tooMany: Boolean) {
        val files: Int get() = items.count { !it.isDirectory }

        /** 진행률의 분모 — 읽을 바이트. '저장' 항목은 두 번 읽는다(재고, 쓴다). */
        val work: Long
            get() = items.sumOf { if (it.isDirectory) 0L else if (isCompressed(it.file.name)) it.size * 2 else it.size }
    }

    /** 결과. 성공이면 [path] 가 실제로 만든 파일이다. */
    data class Result(val outcome: FileOpEngine.Outcome, val path: String?)

    private class Stop(val reason: FileOpEngine.Reason, val name: String) : Exception()

    /**
     * 사용자가 적은 이름을 ZIP 파일 이름으로 — `.zip` 이 없으면 붙인다(목록이 확장자로 종류를 정한다). 이름 규칙에 어긋나면
     * null(화면이 이미 [PathRules.validate] 로 거른 값이 온다 — 엔진이 한 번 더 본다).
     */
    fun zipName(raw: String): String? {
        val verdict = PathRules.validate(raw)
        val name = (verdict as? PathRules.Verdict.Ok)?.name ?: return null
        if (name.lowercase(Locale.ROOT).endsWith(".zip")) return name
        val withExt = "$name.zip"
        return (PathRules.validate(withExt) as? PathRules.Verdict.Ok)?.name
    }

    /**
     * 화면이 대화상자에 넣을 이름. 하나를 골랐으면 그 이름(파일이면 확장자를 뗀다), 여럿이면 지금 폴더의 이름. 그 폴더에
     * 이미 있으면 `(2)` 를 붙인 다음 이름이다 — 실제로 맡는 것은 묶기가 끝날 때다.
     */
    fun suggestName(sources: List<File>, destDir: File, fallback: String): String {
        val base = when {
            sources.size == 1 && sources[0].isDirectory -> sources[0].name
            sources.size == 1 -> sources[0].name.substringBeforeLast('.').ifEmpty { sources[0].name }
            else -> destDir.name
        }.ifEmpty { fallback }
        val name = zipName(base) ?: zipName(fallback) ?: "archive.zip"
        return runCatching { PathRules.nextAvailable(destDir, name) }.getOrDefault(name)
    }

    /**
     * 묶는다. 취소되면 임시본을 지우고 [FileOpEngine.Outcome.Cancelled] 를 돌려준다(던지지 않는다 — 큐의 경계가 여기다).
     *
     * @param sources 고른 것들. **같은 폴더 안의 것**이어야 한다(목록의 선택이 그렇다) — ZIP 안의 맨 위 이름이 그 이름들이다.
     */
    suspend fun make(
        sources: List<String>,
        destDir: String,
        name: String,
        media: MediaIndex?,
        onProgress: (FileOpEngine.Progress) -> Unit,
        /** ZIP 을 놓은 순간에 불린다(`NewFiles.place`) — 그 뒤에 온 취소가 결과를 버려도 큐가 '놓였다' 를 안다. */
        onPlaced: (Result) -> Unit = {},
    ): Result = withContext(IroDispatchers.io) {
        val dir = File(destDir)
        val zipName = zipName(name) ?: return@withContext Result(FileOpEngine.Outcome.Failed(FileOpEngine.Reason.INVALID_NAME, name), null)
        if (!dir.isDirectory) {
            return@withContext Result(FileOpEngine.Outcome.Failed(FileOpEngine.Reason.NOT_FOUND, dir.name), null)
        }
        // 죽은 프로세스가 남긴 임시본을 걷는다(복사와 같다 — 한 시간 넘은 것만).
        AtomicFileWriter.sweepPartials(dir)

        val plan = plan(sources.map(::File))
        if (plan.tooMany) {
            return@withContext Result(FileOpEngine.Outcome.Failed(FileOpEngine.Reason.TOO_MANY_ITEMS, zipName), null)
        }
        if (plan.items.isEmpty()) {
            // 담을 것이 하나도 없다(링크만 골랐다). 빈 ZIP 을 만들지 않는다.
            return@withContext Result(FileOpEngine.Outcome.Done(0, plan.skipped, plan.unreadable), null)
        }

        val pending = try {
            AtomicFileWriter.begin(File(dir, zipName), 0L)
        } catch (e: IOException) {
            return@withContext Result(FileOpEngine.Outcome.Failed(FileOpEngine.reasonOf(e), zipName), null)
        }
        var unreadable = plan.unreadable
        var added = 0
        // **닫아도 아래 파일은 열어 둔다.** ZIP 흐름을 닫아야 그 안의 `Deflater` 가 네이티브 메모리를 놓는데(닫지 않으면 GC 를
        // 기다린다), 아래 파일은 `commit` 이 fsync 한 뒤에 닫아야 한다.
        val zip = ZipOutputStream(BufferedOutputStream(KeepOpenStream(pending.stream), BUFFER))
        try {
            val progress = Progress(plan.work, plan.files, onProgress)
            val space = SpaceGuard(dir)
            val buf = ByteArray(BUFFER)
            for (item in plan.items) {
                currentCoroutineContext().ensureActive()
                if (item.isDirectory) {
                    zip.putNextEntry(directoryEntry(item))
                    zip.closeEntry()
                    continue
                }
                val stored = isCompressed(item.file.name)
                // '저장' 항목은 머리에 크기와 CRC 를 먼저 적어야 한다 — 한 번 읽어 잰다. 열리지 않으면 담지 않는다.
                var measured: Measured? = null
                if (stored) {
                    progress.begin(item.file.name)
                    measured = measure(item.file, buf, progress)
                    if (measured == null) {
                        unreadable++
                        progress.skip(item.size)
                        continue
                    }
                }
                // **연 다음에 항목을 연다.** 열리지 않는 파일에 항목부터 쓰면 빈 항목이 남는다.
                val input = try {
                    FileInputStream(item.file)
                } catch (e: IOException) {
                    unreadable++
                    progress.skip(item.size)
                    continue
                }
                input.use { stream ->
                    zip.putNextEntry(fileEntry(item, measured))
                    progress.begin(item.file.name)
                    try {
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = try {
                                stream.read(buf)
                            } catch (e: IOException) {
                                // 반쯤 쓴 항목은 지울 수 없다 — ZIP 전체를 버린다(위 '고르는 것과 거르는 것').
                                throw Stop(FileOpEngine.reasonOf(e), item.file.name)
                            }
                            if (n < 0) break
                            zip.write(buf, 0, n)
                            progress.add(n.toLong())
                            space.wrote(n.toLong())
                        }
                        zip.closeEntry()
                    } catch (e: java.util.zip.ZipException) {
                        // 잰 뒤에 파일이 바뀌었다('저장' 항목의 크기·CRC 가 어긋났다). 그 ZIP 은 믿을 수 없다.
                        throw Stop(FileOpEngine.Reason.IO, item.file.name)
                    }
                }
                added++
                progress.fileDone()
            }
            // 가운데 목록을 쓰고 닫는다(아래 파일은 [KeepOpen] 이 열어 둔다).
            zip.close()
            val done = FileOpEngine.Outcome.Done(added, plan.skipped, unreadable)
            val target = try {
                NewFiles.place(pending, dir, zipName, media) { t -> onPlaced(Result(done, t.absolutePath)) }
            } catch (e: NewFiles.ClaimFailed) {
                throw Stop(e.reason, zipName)
            }
            Result(done, target.absolutePath)
        } catch (e: Stop) {
            withContext(NonCancellable) { discard(zip, pending) }
            // **끝낸 수를 싣지 않는다** — ZIP 을 통째로 버렸으므로 '49개는 끝냈습니다' 는 거짓이다.
            Result(FileOpEngine.Outcome.Failed(e.reason, e.name, 0, plan.skipped, unreadable), null)
        } catch (e: kotlinx.coroutines.CancellationException) {
            withContext(NonCancellable) { discard(zip, pending) }
            Result(FileOpEngine.Outcome.Cancelled, null)
        } catch (t: Throwable) {
            withContext(NonCancellable) { discard(zip, pending) }
            Iro.e(message = "ZIP 만들기가 실패했다: ${t::class.java.simpleName}")
            Result(FileOpEngine.Outcome.Failed(FileOpEngine.reasonOf(t), zipName, 0, plan.skipped, unreadable), null)
        }
    }

    /**
     * 버린다 — ZIP 흐름을 닫아 압축기를 놓고(쓰다 만 '저장' 항목이면 닫기가 던진다 — 그때는 GC 가 놓는다) 임시본을 지운다.
     */
    private fun discard(zip: ZipOutputStream, pending: AtomicFileWriter.Pending) {
        try {
            zip.close()
        } catch (e: IOException) {
            // 버리는 길이다 — 닫기의 실패로 할 일은 없다.
        }
        pending.abort()
    }

    // ---- 계획 -------------------------------------------------------------------

    /**
     * 담을 것을 훑는다. **링크는 따라가지 않는다**, 같은 (장치, inode) 를 두 번 들어가지 않는다, 깊이는 64 까지 —
     * 복사의 훑기(`FileOpEngine.walk`)와 같은 세 겹이다.
     */
    internal suspend fun plan(sources: List<File>): Plan {
        val items = ArrayList<Item>()
        val visited = HashSet<Long>()
        var skipped = 0
        var unreadable = 0
        var tooMany = false

        suspend fun visit(file: File, name: String, depth: Int) {
            currentCoroutineContext().ensureActive()
            if (tooMany) return
            if (depth > MAX_DEPTH) {
                skipped++
                return
            }
            val st = try {
                Os.lstat(file.absolutePath)
            } catch (e: ErrnoException) {
                unreadable++
                return
            }
            val mode = st.st_mode
            if (OsConstants.S_ISLNK(mode) || !(OsConstants.S_ISDIR(mode) || OsConstants.S_ISREG(mode))) {
                skipped++
                return
            }
            if (!visited.add(st.st_dev * 1_000_003L + st.st_ino)) {
                skipped++
                return
            }
            if (items.size >= MAX_ENTRIES) {
                tooMany = true
                return
            }
            val modified = st.st_mtime * 1000L
            if (OsConstants.S_ISDIR(mode)) {
                items += Item(file, "$name/", isDirectory = true, size = 0, modified = modified)
                val children = file.listFiles()
                if (children == null) {
                    unreadable++
                    return
                }
                // 이름 차례로 담는다 — 같은 폴더를 두 번 묶으면 같은 차례가 나오고, 푸는 쪽의 목록도 읽기 좋다.
                for (child in children.sortedBy { it.name }) visit(child, "$name/${child.name}", depth + 1)
            } else {
                items += Item(file, name, isDirectory = false, size = st.st_size, modified = modified)
            }
        }

        for (s in sources) visit(s, s.name, 0)
        return Plan(items, skipped, unreadable, tooMany)
    }

    private const val MAX_DEPTH = 64

    // ---- 항목 -------------------------------------------------------------------

    private fun directoryEntry(item: Item): ZipEntry = ZipEntry(item.name).apply {
        // 폴더는 내용이 없으므로 '저장' 으로 둔다 — 압축 방식으로 두면 빈 압축 흐름과 데이터 기술자가 붙는다.
        method = ZipEntry.STORED
        size = 0
        compressedSize = 0
        crc = EMPTY_CRC
        if (item.modified > 0) setLastModifiedTime(FileTime.fromMillis(item.modified))
    }

    /**
     * 파일 항목. [measured] 가 있으면 '저장'(압축하지 않음)이고 그 크기·CRC 를 머리에 적는다. 없으면 압축이다.
     *
     * **압축 수준을 항목마다 바꾸지 않는다.** 처음에는 이미 압축된 형식에 `setLevel(NO_COMPRESSION)` 을 걸었는데, 안드로이드
     * 12 에뮬레이터에서 그 항목이 **풀리지 않았다**(`invalid stored block lengths` — 계측 시험이 잡았다). 하나의 `Deflater` 를
     * 돌려 쓰는 `ZipOutputStream` 에서 수준 바꾸기는 `deflateParams` 를 지나는데, 그 길이 이 판의 zlib 와 어긋나는 것으로 보인다
     * (원인은 확인하지 않았다). '저장' 은 그 길을 지나지 않는다.
     */
    private fun fileEntry(item: Item, measured: Measured?): ZipEntry = ZipEntry(item.name).apply {
        if (measured != null) {
            method = ZipEntry.STORED
            size = measured.size
            compressedSize = measured.size
            crc = measured.crc
        } else {
            method = ZipEntry.DEFLATED
        }
        // 확장 시각(UTC)을 함께 적는다 — DOS 시각만 적으면 푸는 기기의 시간대로 시가 바뀐다.
        if (item.modified > 0) setLastModifiedTime(FileTime.fromMillis(item.modified))
    }

    private class Measured(val size: Long, val crc: Long)

    /** 한 번 읽어 크기와 CRC 를 잰다. 열리지 않거나 읽다 실패하면 null — 그 파일은 담지 않는다(아직 아무것도 쓰지 않았다). */
    private suspend fun measure(file: File, buf: ByteArray, progress: Progress): Measured? {
        val crc = CRC32()
        var size = 0L
        try {
            FileInputStream(file).use { stream ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = stream.read(buf)
                    if (n < 0) break
                    crc.update(buf, 0, n)
                    size += n
                    progress.add(n.toLong())
                }
            }
        } catch (e: IOException) {
            return null
        }
        return Measured(size, crc.value)
    }

    private val EMPTY_CRC: Long = CRC32().value

    /**
     * 다시 압축해도 줄지 않는 형식. 확장자로 고른다 — 틀려도 크기가 조금 다를 뿐 내용은 같다.
     * (문서 가운데 docx·xlsx·pptx·epub·hwpx·cbz 는 그 자체가 ZIP 이고, PDF 는 흐름이 대개 이미 압축돼 있다.)
     */
    internal fun isCompressed(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase(Locale.ROOT) in COMPRESSED

    private val COMPRESSED = setOf(
        "jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "avif", "jxl",
        "mp4", "m4v", "mkv", "webm", "mov", "avi", "3gp", "ts", "m2ts", "wmv", "flv",
        "mp3", "m4a", "aac", "ogg", "oga", "opus", "flac", "wma", "amr",
        "zip", "7z", "rar", "gz", "tgz", "bz2", "tbz2", "xz", "txz", "zst", "lz4", "lzma", "cab",
        "apk", "apks", "xapk", "jar", "aar", "docx", "xlsx", "pptx", "odt", "ods", "odp", "epub", "hwpx",
        "cbz", "cbr", "cb7", "pdf",
    )

    // 이름 맡기는 [NewFiles.claim] 이 한다 — 잘라 저장·구간 저장과 같은 규칙이다.

    // ---- 진행·공간 -------------------------------------------------------------------

    /**
     * 진행 알림. 쓸 때마다 부르지 않는다 — 64 KiB 쓰기마다 부르면 1 GB 에 만육천 번이고, 작은 파일 10만 개면 파일마다
     * 부르는 것도 10만 번이다. [REPORT_NANOS] 에 한 번만 알린다(처음 한 번은 곧바로).
     */
    private class Progress(
        private val totalBytes: Long,
        private val totalFiles: Int,
        private val sink: (FileOpEngine.Progress) -> Unit,
    ) {
        private var bytes = 0L
        private var files = 0
        private var name = ""
        private var lastAt = 0L
        private var reported = false

        fun begin(current: String) {
            name = current
            report()
        }

        fun add(n: Long) {
            bytes += n
            report()
        }

        fun skip(size: Long) {
            bytes += size
            files++
        }

        fun fileDone() {
            files++
        }

        private fun report() {
            val now = System.nanoTime()
            if (reported && now - lastAt < REPORT_NANOS) return
            reported = true
            lastAt = now
            sink(FileOpEngine.Progress(bytes.coerceAtMost(totalBytes), totalBytes, files, totalFiles, name))
        }

        companion object {
            const val REPORT_NANOS = 100_000_000L
        }
    }

    /**
     * 남은 공간을 지킨다. `usableSpace` 는 앱이 실제로 쓸 수 있는 양보다 작게 말할 수 있지만(시스템이 남겨 둔 몫을 뺀다),
     * 여기서는 그 보수적인 값이 맞다 — 남길 몫을 재는 자리다.
     */
    private class SpaceGuard(private val dir: File) {
        private var sinceCheck = 0L

        @SuppressLint("UsableSpace")
        fun wrote(n: Long) {
            sinceCheck += n
            if (sinceCheck < SPACE_CHECK_BYTES) return
            sinceCheck = 0
            if (dir.usableSpace in 1 until SPACE_MARGIN_BYTES) throw Stop(FileOpEngine.Reason.NO_SPACE, dir.name)
        }
    }
}
