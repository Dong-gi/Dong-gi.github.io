package io.github.donggi.iroiroviewer.archive

import android.content.Context
import android.os.StatFs
import io.github.donggi.iroiroviewer.format.FileDocumentSource
import io.github.donggi.iroiroviewer.format.archive.ArchiveEntry
import io.github.donggi.iroiroviewer.format.archive.Archives
import io.github.donggi.iroiroviewer.format.archive.EntrySink
import io.github.donggi.iroiroviewer.io.AtomicFileWriter
import io.github.donggi.iroiroviewer.io.ExtractReport
import io.github.donggi.iroiroviewer.io.ExtractSupport
import io.github.donggi.iroiroviewer.io.FileOpEngine
import io.github.donggi.iroiroviewer.io.FileOpManager
import io.github.donggi.iroiroviewer.io.Iro
import io.github.donggi.iroiroviewer.io.MediaIndex
import io.github.donggi.iroiroviewer.io.PathRules
import io.github.donggi.iroiroviewer.model.IroDispatchers
import io.github.donggi.iroiroviewer.safety.ArchivePath
import io.github.donggi.iroiroviewer.safety.EntryBudget
import io.github.donggi.iroiroviewer.safety.ParseLimitExceededException
import io.github.donggi.iroiroviewer.safety.ParseLimits
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.io.OutputStream

/**
 * 아카이브를 실제로 푼다.
 *
 * ## 이 클래스가 지키는 것
 *
 * **1. 이름이 목적지 밖으로 나가지 않는다.** 엔트리 이름은 아카이브를 만든 쪽이 적은
 * 문자열이고 이 앱은 기기 저장소 전체에 쓸 수 있다. 이름은 세 관문을 지난다 —
 * [ArchivePath.sanitize](탈출·제어문자), 조각마다 [PathRules.sanitize](FAT 금지문자·
 * 255바이트), 그리고 [ArchivePath.resolveInside](canonical 봉쇄). 마지막 것이 없으면
 * 목적지 안에 이미 있던 심볼릭 링크를 타고 밖으로 나갈 수 있다.
 *
 * **2. 파일 하나하나가 원자적이다.** [AtomicFileWriter] 를 쓴다 — 복사·EXIF 회전과
 * 같은 코드다. 중간에 죽어도 반쯤 쓰인 파일이 제 이름으로 남지 않는다.
 *
 * **3. 부분 실패를 되돌리지 않는다.** 200개 중 150번째에서 실패했을 때 앞의 149개를
 * 지우는 것은 '실패했으니 사용자 파일을 지운다' 이고, 4단계 치명 넷이 전부 그 형태였다.
 * 대신 **어디까지 했는지를 결과에 싣는다.**
 *
 * **4. 거부한 것을 말한다.** 위험한 이름·링크·암호 항목은 조용히 건너뛰지 않고
 * [ExtractReport] 로 세어 화면이 말하게 한다.
 */
class ArchiveExtractEngine(private val context: Context) : ExtractSupport.Runner {

    override suspend fun run(
        request: FileOpManager.Request.Extract,
        onProgress: (FileOpEngine.Progress) -> Unit,
    ): ExtractSupport.Result = try {
        runWith(request, onProgress)
    } finally {
        // 요청이 붙든 암호의 주인은 이 작업이다. 성공·실패·취소 어느 쪽이든 여기서 지운다.
        request.password?.fill('\u0000')
    }

    private suspend fun runWith(
        request: FileOpManager.Request.Extract,
        onProgress: (FileOpEngine.Progress) -> Unit,
    ): ExtractSupport.Result {
        val archive = File(request.archivePath)
        val parent = File(request.destParent)
        if (!archive.isFile) return failed(FileOpEngine.Reason.NOT_FOUND, archive.name, request.destParent)
        if (!parent.isDirectory) return failed(FileOpEngine.Reason.NOT_FOUND, parent.name, request.destParent)

        // 목적지를 먼저 정한다. 새 폴더는 **엔진이 원자적으로 맡는다** — 화면이 미리
        // 고른 이름을 그대로 쓰면 고르는 사이에 다른 앱이 같은 이름을 만들 수 있다.
        val dest = try {
            resolveDestination(parent, request.newFolderName)
        } catch (e: IOException) {
            return failed(FileOpEngine.Reason.IO, parent.name, request.destParent)
        } ?: return failed(FileOpEngine.Reason.INVALID_NAME, parent.name, request.destParent)

        val limits = ParseLimits.forExtract(freeBytes(dest))
        val budget = EntryBudget(limits)
        val counters = Counters()
        val touched = ArrayList<String>(64)
        val dirTimes = DirTimes()
        // 새 폴더는 이번 풀기가 만든 것이다. 대개 아카이브에 그 폴더의 시각이 없어 '지금' 으로 남는다 — 맨 위 폴더
        // 자신을 적은 항목(`./`)이 있는 tar 에서만 그 시각을 받는다(`ExtractSink.begin`).
        if (request.newFolderName != null) dirTimes.created(dest)

        // 앞선 작업이 프로세스 사망으로 남긴 임시 파일을 걷는다. 숨김 이름이라 목록에도
        // 안 뜨고, 2 GB 풀기가 죽으면 2 GB 가 보이지 않는 채 남는다.
        AtomicFileWriter.sweepPartials(dest)

        var outcome: FileOpEngine.Outcome = FileOpEngine.Outcome.Done(0, 0, 0)
        try {
            // **여는 것부터 푸는 것까지 한 경계 안이다.** 해제는 블로킹이라 협조적 취소가 닿지 않는다 — 인터럽트로
            // 바꿔 리더의 스트림 안에서 보게 한다. 예전에는 푸는 한 줄만 이 안에 있었는데, 압축한 tar 는 **목록을
            // 읽는 것 자체가** 스트림을 끝까지 푸는 일이라(아래) 그동안 취소가 몇십 초씩 듣지 않았다.
            runInterruptible(IroDispatchers.io) {
                Archives.open(FileDocumentSource(archive), limits, budget, request.password).use { reader ->
                    val selected = request.entryIndices?.toHashSet()
                    // **압축한 tar 는 여기서 한 번 끝까지 푼다** — 목록을 보려면 그래야 한다(`TarArchiveReader`). 풀기가
                    // 한 번 더 풀므로 목록 한 번 + 풀기 한 번이다. 진행 바와 알림의 파일 수(`filesTotal`)를 알려면
                    // 목록이 먼저라 치르는 값이다 — 그 수가 0 이면 알림이 진행을 아예 그리지 않는다(`FileOpService`).
                    val plan = reader.entries.filter { selected == null || it.index in selected }
                    val sink = ExtractSink(
                        dest = dest,
                        archive = archive,
                        conflict = request.conflict,
                        selected = selected,
                        counters = counters,
                        touched = touched,
                        dirTimes = dirTimes,
                        totalFiles = plan.count { it.isReadable },
                        meter = ExtractMeter(
                            inputTotal = reader.inputBytesFor(selected),
                            declaredTotal = plan.sumOf { it.declaredSize.coerceAtLeast(0L) },
                        ),
                        onProgress = onProgress,
                    )
                    try {
                        reader.extractSequentially(sink)
                    } finally {
                        // 취소와 아카이브 전체를 끝내는 상한은 리더가 [EntrySink.finish] 없이 위로 던진다 — 쓰던 항목의
                        // 임시 파일이 숨은 이름으로 남는다(한 시간 뒤에야 `sweepPartials` 가 걷는다). 여기서 지운다.
                        sink.abandon()
                    }
                }
            }
            outcome = FileOpEngine.Outcome.Done(counters.done, counters.skipped, counters.failed)
        } catch (e: InterruptedIOException) {
            // 인터럽트는 취소다. **`runInterruptible` 은 `InterruptedException` 만 취소로
            // 바꾼다** — 우리 스트림이 던지는 `InterruptedIOException` 은 그대로 올라오므로
            // 여기서 옮기지 않으면 사용자 취소가 '입출력 실패' 로 보고된다.
            currentCoroutineContext().ensureActive()
            outcome = FileOpEngine.Outcome.Cancelled
        } catch (e: ParseLimitExceededException) {
            Iro.d { "풀기 상한: ${e.limitName}" }
            outcome = FileOpEngine.Outcome.Failed(
                FileOpEngine.Reason.NO_SPACE.takeIf { e.limitName == "maxTotalOutput" } ?: FileOpEngine.Reason.IO,
                archive.name,
                counters.done,
                counters.skipped,
                counters.failed,
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (t: Throwable) {
            // 취소가 파일 채널을 닫아 `ClosedByInterruptException`(입출력 예외)으로 올라왔을 수 있다 — 그러면 사용자의
            // 취소가 '입출력 실패' 로 보고된다. 잡이 취소됐으면 취소로 끝낸다(아래 finally 는 그래도 돈다).
            currentCoroutineContext().ensureActive()
            Iro.e(message = "풀기 실패: ${t::class.java.simpleName}")
            outcome = FileOpEngine.Outcome.Failed(
                reasonOf(t),
                archive.name,
                counters.done,
                counters.skipped,
                counters.failed,
            )
        } finally {
            // 폴더 시각은 **모든 쓰기가 끝난 뒤** 한 번에 건다(`DirTimes`). 취소·실패로 끝났어도 거기까지 만든 폴더는
            // 사용자의 저장소에 남으므로 똑같이 건다 — 취소는 위의 `ensureActive` 가 예외로 올려보내므로 `finally`
            // 여야 닿는다. 실패는 무시한다.
            dirTimes.restore()
        }

        // 미디어 색인은 **마지막에 한 번.** 파일마다 부르면 1만 개에서 바인더 호출이
        // 1만 번이 되어 그것만으로 몇 분이 걸린다.
        MediaIndex(context).scanAll(if (touched.size <= MEDIA_SCAN_MAX) touched else listOf(dest.absolutePath))

        return ExtractSupport.Result(outcome, counters.report(dest.absolutePath))
    }

    // ---- 목적지 -----------------------------------------------------------------

    /**
     * 풀어 넣을 폴더. 새 폴더를 요청받으면 **번호를 올리며 원자적으로 맡는다.**
     *
     * `mkdir` 은 이미 있으면 거짓을 준다 — 그것이 여기서 쓰는 원자성이다.
     * `exists()` 로 보고 만드는 사이에 다른 앱이 같은 이름을 만들 수 있다.
     */
    private fun resolveDestination(parent: File, newFolderName: String?): File? {
        if (newFolderName == null) return parent
        val base = PathRules.sanitize(newFolderName).ifBlank { return null }
        repeat(MAX_FOLDER_TRIES) {
            // `nextAvailable` 이 이름을 고르고(255바이트 안에서 번호 자리까지 비운다),
            // `mkdir` 이 그것을 **원자적으로 맡는다.** 둘 사이에 다른 앱이 끼어들면
            // mkdir 이 거짓을 주고 다음 이름으로 넘어간다.
            val name = PathRules.nextAvailable(parent, base)
            val dir = File(parent, name)
            if (dir.mkdir()) return dir
        }
        throw IOException("폴더 이름을 맡지 못했다")
    }

    private fun freeBytes(dir: File): Long = runCatching {
        val st = StatFs(dir.absolutePath)
        st.availableBlocksLong * st.blockSizeLong
    }.getOrDefault(0L)

    // ---- 소비자 -----------------------------------------------------------------

    /** 세어 두는 것들. 결과에 그대로 실린다. */
    private class Counters {
        var done = 0
        var skipped = 0
        var failed = 0
        var unsafe = 0
        var link = 0
        var encrypted = 0
        var blockedDirs = 0
        var selfOverwrite = 0
        val samples = ArrayList<String>(ExtractReport.MAX_SAMPLES)

        fun sample(name: String) {
            if (samples.size < ExtractReport.MAX_SAMPLES) samples += name
        }

        fun report(destDir: String) = ExtractReport(
            destDir = destDir,
            refusedUnsafe = unsafe,
            refusedLink = link,
            refusedEncrypted = encrypted,
            blockedDirs = blockedDirs,
            refusedSelfOverwrite = selfOverwrite,
            samples = samples.toList(),
        )
    }

    /** 중간 폴더를 만들어 본 결과. 경로마다 한 번만 판단하려고 기억한다. */
    private enum class DirState { OK, BLOCKED }

    private inner class ExtractSink(
        private val dest: File,
        private val archive: File,
        private val conflict: FileOpEngine.Conflict,
        private val selected: Set<Int>?,
        private val counters: Counters,
        private val touched: MutableList<String>,
        private val dirTimes: DirTimes,
        private val totalFiles: Int,
        private val meter: ExtractMeter,
        private val onProgress: (FileOpEngine.Progress) -> Unit,
    ) : EntrySink {

        private val dirs = HashMap<String, DirState>()

        /** 이 배치 안에서 이미 쓴 목표 경로. 다듬은 이름이 겹치면 둘 다 보관으로 돌린다. */
        private val usedPaths = HashSet<String>()

        private var pending: AtomicFileWriter.Pending? = null
        private var pendingTarget: File? = null
        private var lastReport = 0L

        /** 진행 바가 적는 지금 항목의 이름. 입력으로 세면 항목 한가운데에서도 알리므로 따로 든다. */
        private var currentName = ""

        /** 아카이브 자신의 canonical 경로. 자기를 덮어쓰는 것을 막는 기준이다. */
        private val archiveCanonical = runCatching { archive.canonicalPath }.getOrNull()

        override fun begin(entry: ArchiveEntry): OutputStream? {
            if (selected != null && entry.index !in selected) return null
            if (entry.isDirectory) {
                // 진짜 디렉터리 엔트리는 미리 만들어 둔다. 빈 폴더도 아카이브의 내용이다.
                //
                // **파일과 같은 이름 계산을 지난다**([ExtractNames.relPathOf]). 예전에는 다듬지 않은 `safeName` 으로
                // 만들어, `a?b/` 같은 폴더 엔트리는 `a?b` 로, 그 안의 파일은 `a_b/` 로 갔다 — 빈 폴더가 하나 더 생기고
                // 폴더 시각이 엉뚱한 곳에 걸린다.
                val rel = ExtractNames.relPathOf(entry)
                if (rel == null) {
                    // 맨 위 폴더 자신(`tar -czf x.tgz .` 의 `./`)의 시각은 풀어 넣는 폴더의 것이다. 그 폴더가 이번 풀기가
                    // 만든 새 폴더일 때만 실제로 걸린다(`DirTimes` 는 만든 폴더만 건드린다).
                    if (entry.isRootDirectory) dirTimes.entryTime(dest, entry.lastModified)
                    return null
                }
                if (ensureDir(rel)) {
                    ArchivePath.resolveInside(dest, rel)?.let { dirTimes.entryTime(it, entry.lastModified) }
                }
                return null
            }
            val safeName = entry.safeName
            if (safeName == null) {
                counters.unsafe++
                counters.sample(entry.name)
                return null
            }
            if (entry.isLink) {
                counters.link++
                counters.sample(entry.name)
                return null
            }
            // 암호를 받았으면 암호 항목도 읽힌다([ArchiveEntry.isReadable]). 못 읽는 것만 센다.
            if (entry.isEncrypted && !entry.isReadable) {
                counters.encrypted++
                counters.sample(entry.name)
                return null
            }

            val target = resolveTarget(entry) ?: return null
            // 자기 자신을 덮어쓰는 것은 **정책과 무관하게** 막는다. '여기에 풀기' 로
            // 같은 폴더에 풀 때 아카이브와 같은 이름의 엔트리가 있으면 원본이 사라진다.
            if (archiveCanonical != null && runCatching { target.canonicalPath }.getOrNull() == archiveCanonical) {
                counters.selfOverwrite++
                counters.sample(entry.name)
                return null
            }

            val finalTarget = when {
                !target.exists() -> target
                target.isDirectory -> {
                    // 종류가 다른 충돌. '파일을 덮어쓴다' 가 '폴더를 지운다' 로 번역되면 안 된다.
                    counters.failed++
                    return null
                }
                !usedPaths.add(target.absolutePath) -> nextAvailable(target) ?: run {
                    counters.failed++
                    return null
                }
                conflict == FileOpEngine.Conflict.SKIP -> {
                    counters.skipped++
                    return null
                }
                conflict == FileOpEngine.Conflict.KEEP_BOTH -> nextAvailable(target) ?: run {
                    counters.failed++
                    return null
                }
                else -> target
            }
            usedPaths += finalTarget.absolutePath

            return try {
                pendingTarget = finalTarget
                currentName = entry.safeName?.substringAfterLast('/') ?: entry.name
                AtomicFileWriter.begin(finalTarget, entry.lastModified).also { pending = it }.stream
            } catch (t: Throwable) {
                pendingTarget = null
                counters.failed++
                null
            }
        }

        override fun finish(entry: ArchiveEntry, written: Long, failure: Throwable?) {
            val p = pending ?: return
            val target = pendingTarget
            pending = null
            pendingTarget = null
            if (failure == null) {
                try {
                    p.commit()
                    counters.done++
                    meter.finished(written)
                    // **절대경로를 싣는다.** MediaIndex 는 실제 경로로 스캔한다.
                    target?.let { touched += it.absolutePath }
                } catch (t: Throwable) {
                    p.abort()
                    counters.failed++
                }
            } else {
                p.abort()
                counters.failed++
            }
            report()
        }

        /**
         * 쓰다 만 항목의 임시 파일을 지운다. 리더가 [finish] 없이 끝났을 때(취소·아카이브 전체를 끝내는 상한) 부른다.
         * 대상 파일은 건드리지 않는다 — [AtomicFileWriter] 는 `commit` 전까지 대상을 만지지 않는다.
         */
        fun abandon() {
            val p = pending ?: return
            pending = null
            pendingTarget = null
            p.abort()
            // 쓰던 항목은 끝내지 못했다 — 상한으로 끝난 작업의 결과에 '실패 n개' 로 함께 싣는다(취소면 수를 보이지 않는다).
            counters.failed++
        }

        /** 리더가 알리는 입력 소비량. 큰 항목 하나를 푸는 동안에도 바가 움직인다. */
        override fun consumed(inputBytes: Long) {
            if (!meter.byInput) return
            meter.consumed(inputBytes)
            report()
        }

        private fun report() {
            val now = System.nanoTime()
            // 100ms 보다 자주 알리지 않는다. 파일마다 UI 를 깨우면 그것이 병목이 된다.
            if (now - lastReport < PROGRESS_INTERVAL_NS) return
            lastReport = now
            onProgress(
                FileOpEngine.Progress(
                    bytesDone = meter.bytesDone,
                    bytesTotal = meter.bytesTotal,
                    filesDone = counters.done,
                    filesTotal = totalFiles,
                    currentName = currentName,
                ),
            )
        }

        /**
         * 엔트리를 실제 파일로. 나가면 null.
         *
         * **계산은 [ExtractNames] 가 한다** — 화면의 충돌 선검사와 같은 함수여야
         * 사용자가 본 목록과 실제로 덮어써지는 목록이 같다.
         */
        private fun resolveTarget(entry: ArchiveEntry): File? {
            val relative = ExtractNames.relPathOf(entry) ?: run {
                counters.unsafe++
                counters.sample(entry.name)
                return null
            }
            val dirPart = relative.substringBeforeLast('/', "")
            if (dirPart.isNotEmpty() && !ensureDir(dirPart)) return null

            // **마지막 봉쇄 검사.** 문자열만 보면 목적지 안의 심볼릭 링크를 타고 밖으로
            // 나갈 수 있다 — canonical 로 비교해야 막힌다.
            return ArchivePath.resolveInside(dest, relative) ?: run {
                counters.unsafe++
                counters.sample(entry.name)
                null
            }
        }

        /**
         * 중간 폴더를 만든다. 그 자리에 **파일**이 있으면 건드리지 않는다.
         *
         * 기존 파일을 지우거나 이름을 바꾸는 것은 사용자가 시키지 않은 파괴다.
         * 한 번 막힌 경로는 기억해 아래 9,999개가 디스크를 다시 만지지 않게 한다.
         */
        private fun ensureDir(relativeDir: String): Boolean {
            dirs[relativeDir]?.let { return it == DirState.OK }
            val dir = ArchivePath.resolveInside(dest, relativeDir)
            if (dir == null) {
                dirs[relativeDir] = DirState.BLOCKED
                counters.unsafe++
                counters.sample(relativeDir)
                return false
            }
            val ok = when {
                dir.isDirectory -> true
                dir.exists() -> {
                    counters.blockedDirs++
                    counters.sample(relativeDir)
                    false
                }
                else -> {
                    // `mkdirs` 가 한 번에 여러 겹을 만든다. **무엇을 새로 만들었는지** 알아야 끝에서 그 폴더들만
                    // 시각을 되살린다(이미 있던 폴더는 사용자의 것이다) — 만들기 전에 없던 조상을 적어 둔다.
                    val missing = generateSequence(dir) { it.parentFile }
                        .takeWhile { it != dest && !it.exists() }
                        .toList()
                    val made = dir.mkdirs() || dir.isDirectory
                    if (made) missing.filter { it.isDirectory }.forEach { dirTimes.created(it) }
                    made
                }
            }
            dirs[relativeDir] = if (ok) DirState.OK else DirState.BLOCKED
            return ok
        }

        private fun nextAvailable(target: File): File? = try {
            File(target.parentFile, PathRules.nextAvailable(target.parentFile!!, target.name))
        } catch (e: IOException) {
            null
        }
    }

    // ---- 도우미 -----------------------------------------------------------------

    private fun failed(reason: FileOpEngine.Reason, name: String, destDir: String) =
        ExtractSupport.Result(FileOpEngine.Outcome.Failed(reason, name), ExtractReport(destDir))

    private fun reasonOf(t: Throwable): FileOpEngine.Reason {
        var e: Throwable? = t
        var hop = 0
        while (e != null && hop < 4) {
            if (e is android.system.ErrnoException) {
                return when (e.errno) {
                    android.system.OsConstants.ENOSPC, android.system.OsConstants.EDQUOT ->
                        FileOpEngine.Reason.NO_SPACE
                    android.system.OsConstants.EACCES, android.system.OsConstants.EPERM ->
                        FileOpEngine.Reason.PERMISSION
                    else -> FileOpEngine.Reason.IO
                }
            }
            e = e.cause
            hop++
        }
        return FileOpEngine.Reason.IO
    }

    private companion object {
        const val MAX_FOLDER_TRIES = 999
        const val MEDIA_SCAN_MAX = 2_000
        const val PROGRESS_INTERVAL_NS = 100_000_000L
    }
}
