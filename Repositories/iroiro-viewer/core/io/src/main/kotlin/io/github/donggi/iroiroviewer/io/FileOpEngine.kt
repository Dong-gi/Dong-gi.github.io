package io.github.donggi.iroiroviewer.io

import android.os.CancellationSignal
import android.os.FileUtils
import android.os.OperationCanceledException
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import androidx.exifinterface.media.ExifInterface
import io.github.donggi.iroiroviewer.model.IroDispatchers
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext

/**
 * 복사·이동·삭제의 실체.
 *
 * ## 이 파일이 지키는 네 가지
 *
 * **1. 원본을 파괴하지 않는다.** 덮어쓰기는 임시 이름으로 쓰고 `fsync` 한 뒤 **원자적
 * `rename` 으로** 제자리에 놓는다. 대상을 먼저 지우지 않는다 — `rename(2)` 는 기존
 * 파일을 원자적으로 대체하므로 `delete` 는 필요 없고, 그 사이에 전원이 나가면 원본도
 * 복사본도 없는 창만 만든다. 이동에서 원본을 지우는 것은 **복사 성공을 확인한 뒤**다.
 *
 * **2. 볼륨을 넘는 이동을 제대로 한다.** 내부 저장소와 SD 카드는 다른 파일시스템이라
 * `rename` 이 `EXDEV` 로 실패한다. `Files.move` 를 쓰지 않는 것은 비어 있지 않은 디렉터리를
 * 볼륨 너머로 옮길 때 `DirectoryNotEmptyException` 을 던지기 때문이다 — 우리가 직접
 * 재귀 복사 후 삭제로 내려간다.
 *
 * **3. 순환을 밟지 않는다.** 심볼릭 링크와 하드링크로 디렉터리 고리를 만들 수 있다.
 * 방문한 (장치, inode) 를 기억하고 깊이 상한을 함께 건다.
 *
 * **4. 한 파일의 실패가 배치를 끝내지 않는다.** 1,000개를 복사하다 3번째가 권한 오류면
 * 나머지 997개를 그대로 옮기고 '3개 실패' 로 센다. 예전에는 그 자리에서 작업 전체가
 * 끝나고 진행 수까지 버려져, 사용자는 무엇이 옮겨졌는지 알 수 없었다. **전체를 멈추는
 * 것은 저장공간 부족 하나뿐이다** — 그때는 계속해 봐야 전부 실패한다.
 */
class FileOpEngine(private val media: MediaIndex) {

    /** 같은 이름이 이미 있을 때 무엇을 할 것인가. */
    enum class Conflict { SKIP, OVERWRITE, KEEP_BOTH }

    data class Progress(
        val bytesDone: Long,
        val bytesTotal: Long,
        val filesDone: Int,
        val filesTotal: Int,
        val currentName: String,
    )

    sealed interface Outcome {
        /** [skipped] 는 충돌로 건너뛴 것, [failed] 는 실패한 것. 둘 다 있어도 작업은 끝났다. */
        data class Done(val moved: Int, val skipped: Int, val failed: Int) : Outcome
        data object Cancelled : Outcome

        /**
         * 작업 전체가 끝났다. **여기까지 한 것을 함께 싣는다** — 이 수를 버리면 화면이
         * '실패했습니다' 한 줄만 보이고, 사용자는 절반이 이미 옮겨진 것을 모른다.
         */
        data class Failed(
            val reason: Reason,
            val name: String,
            val done: Int = 0,
            val skipped: Int = 0,
            val failed: Int = 0,
        ) : Outcome
    }

    enum class Reason { NO_SPACE, NOT_FOUND, PERMISSION, IO, INVALID_NAME, TARGET_INSIDE_SOURCE }

    /** 무엇을 얼마나 옮길 것인가. 진행률의 분모를 얻는다. */
    data class Plan(val files: Int, val bytes: Long)

    /** 여기서 멈춰야 하는 오류. 저장공간 부족처럼 계속해도 전부 실패할 것들만 이걸 던진다. */
    private class FatalOp(val reason: Reason, val name: String) : Exception()

    /**
     * 재귀 깊이 상한. 정상 폴더 구조가 이보다 깊은 경우는 없고, 여기 걸린다는 것은
     * 링크 고리이거나 악의적으로 만든 구조라는 뜻이다.
     */
    private val maxDepth = 64

    // ---- 계획 -------------------------------------------------------------

    suspend fun plan(sources: List<String>): Plan = withContext(IroDispatchers.io) {
        var files = 0
        var bytes = 0L
        val visited = HashSet<Long>()
        for (s in sources) {
            walk(File(s), visited, 0) { _, size ->
                files++
                bytes += size
            }
        }
        Plan(files, bytes)
    }

    /**
     * 실체가 있는 파일만 방문한다. 링크는 따라가지 않는다.
     *
     * inode 를 기억하는 것은 하드링크로 만든 고리를 막기 위해서다 — 링크를 따라가지
     * 않아도 디렉터리 하드링크가 있는 파일시스템에서는 고리가 생길 수 있다.
     */
    private suspend fun walk(
        file: File,
        visited: MutableSet<Long>,
        depth: Int,
        onFile: (File, Long) -> Unit,
    ) {
        currentCoroutineContext().ensureActive()
        if (depth > maxDepth) return
        val st = try {
            Os.lstat(file.absolutePath)
        } catch (e: ErrnoException) {
            return
        }
        if (OsConstants.S_ISLNK(st.st_mode)) return
        // 장치 번호와 inode 를 한 값으로 섞는다. 둘 다 봐야 서로 다른 볼륨의 같은
        // inode 를 같은 것으로 착각하지 않는다.
        val key = st.st_dev * 1_000_003L + st.st_ino
        if (!visited.add(key)) return
        if (OsConstants.S_ISDIR(st.st_mode)) {
            file.listFiles()?.forEach { walk(it, visited, depth + 1, onFile) }
        } else {
            onFile(file, st.st_size)
        }
    }

    // ---- 복사 -------------------------------------------------------------

    suspend fun copy(
        sources: List<String>,
        destDir: String,
        conflict: Conflict,
        onProgress: (Progress) -> Unit,
    ): Outcome = run(sources, destDir, move = false, conflict, onProgress)

    suspend fun move(
        sources: List<String>,
        destDir: String,
        conflict: Conflict,
        onProgress: (Progress) -> Unit,
    ): Outcome = run(sources, destDir, move = true, conflict, onProgress)

    private suspend fun run(
        sources: List<String>,
        destDir: String,
        move: Boolean,
        conflict: Conflict,
        onProgress: (Progress) -> Unit,
    ): Outcome = withContext(IroDispatchers.io) {
        val dest = File(destDir)
        if (!dest.isDirectory) return@withContext Outcome.Failed(Reason.NOT_FOUND, dest.name)

        // 자기 안으로 옮기는 것을 막는다. 막지 않으면 무한히 복사하다 저장소를 채운다.
        // canonicalPath 는 IOException 을 던진다 — try 밖에 두면 그것이 그대로 샌다.
        val inPlace = HashSet<String>()
        try {
            val destPath = dest.canonicalPath
            for (s in sources) {
                val src = File(s)
                val srcPath = src.canonicalPath
                if (destPath == srcPath || destPath.startsWith(srcPath + File.separator)) {
                    return@withContext Outcome.Failed(Reason.TARGET_INSIDE_SOURCE, src.name)
                }
                // 대상이 **소스의 부모**이면 target 이 소스 자신이 된다. 덮어쓰기로 오면
                // 자기를 지우고 자기를 복사하는 꼴이라 파일이 사라진다. 제자리 복제는
                // 쓸모 있는 조작이므로 막지 말고 언제나 '둘 다 보관' 으로 돌린다.
                if (destPath == File(srcPath).parent) inPlace += s
            }
        } catch (e: IOException) {
            return@withContext Outcome.Failed(Reason.IO, dest.name)
        }

        // 앞선 작업이 프로세스 사망으로 남긴 임시 파일을 여기서 걷는다. 숨김 이름이라
        // 목록에도 안 뜨고, 2GB 복사가 죽으면 2GB 가 보이지 않는 채 남는다.
        sweepPartials(dest)

        val plan = plan(sources)
        val touched = ArrayList<String>(64)
        var filesDone = 0
        var bytesDone = 0L
        var skipped = 0
        var failed = 0
        var lastReport = 0L

        fun report(name: String, extra: Long = 0L) {
            val now = System.nanoTime()
            // 100ms 보다 자주 알리지 않는다. 파일마다 UI 를 깨우면 그것이 병목이 된다.
            if (now - lastReport < 100_000_000L && extra == 0L) return
            lastReport = now
            onProgress(Progress(bytesDone + extra, plan.bytes, filesDone, plan.files, name))
        }

        val onUnit: (String, Long, Boolean) -> Unit = { name, delta, whole ->
            if (whole) {
                filesDone++
                bytesDone += delta
                touched += name
                report(File(name).name)
            } else {
                report(File(name).name, delta)
            }
        }

        try {
            // 배치 안에서 **다듬은 이름이 겹치는** 소스를 본다. `a?.txt` 와 `a*.txt` 는
            // 둘 다 `a_.txt` 가 되므로, 덮어쓰기로 오면 두 번째가 첫 번째를 지운다.
            // 사용자는 대상 폴더의 파일 하나를 덮어쓴다고 답했지 **자기가 고른 파일끼리**
            // 덮어쓰라고 한 적이 없다.
            val usedNames = HashSet<String>()
            for (s in sources) {
                currentCoroutineContext().ensureActive()
                val src = File(s)
                val c = when {
                    s in inPlace -> Conflict.KEEP_BOTH
                    !usedNames.add(PathRules.sanitize(src.name)) -> Conflict.KEEP_BOTH
                    else -> conflict
                }
                val result = transfer(src, dest, move, c, maxDepth, onUnit)
                skipped += result.first
                failed += result.second
            }
        } catch (e: OperationCanceledException) {
            media.scanAll(touched)
            return@withContext Outcome.Cancelled
        } catch (e: FatalOp) {
            media.scanAll(touched)
            return@withContext Outcome.Failed(e.reason, e.name, filesDone, skipped, failed)
        }

        // 미디어 색인 갱신은 **마지막에 한 번**. 파일마다 부르면 1만 개 작업에서
        // 바인더 호출이 1만 번이 되어 그것만으로 몇 분이 걸린다.
        media.scanAll(touched + sources.takeIf { move }.orEmpty())
        Outcome.Done(filesDone, skipped, failed)
    }

    /**
     * 하나를 옮긴다. 돌려주는 것은 (건너뛴 수, 실패한 수).
     *
     * 파일 하나의 실패는 여기서 잡아 세고 넘어간다. 위로 던지는 것은 취소와 [FatalOp] 뿐이다.
     *
     * @param onUnit (경로, 바이트, 파일하나가통째로끝났는가)
     */
    private suspend fun transfer(
        src: File,
        destDir: File,
        move: Boolean,
        conflict: Conflict,
        depth: Int,
        onUnit: (String, Long, Boolean) -> Unit,
    ): Pair<Int, Int> {
        currentCoroutineContext().ensureActive()
        if (depth <= 0) return 0 to 1

        val st = try {
            Os.lstat(src.absolutePath)
        } catch (e: ErrnoException) {
            return 0 to 1
        }
        // 링크는 옮기지 않는다. 따라가면 볼륨 밖으로 나가고, 복제하면 대상이 달라진다.
        if (OsConstants.S_ISLNK(st.st_mode)) return 1 to 0

        val srcIsDir = OsConstants.S_ISDIR(st.st_mode)
        val targetName = PathRules.sanitize(src.name)
        var target = File(destDir, targetName)

        if (target.exists()) {
            // **종류가 다른 충돌은 덮어쓰기를 하지 않는다.** '파일을 덮어쓴다' 는 답이
            // '폴더를 지운다' 로 번역되면 안 된다. 그 항목만 실패로 세고 나머지는 간다.
            if (target.isDirectory != srcIsDir) return 0 to 1
            when (conflict) {
                Conflict.SKIP -> return 1 to 0
                Conflict.KEEP_BOTH -> target = try {
                    File(destDir, PathRules.nextAvailable(destDir, targetName))
                } catch (e: IOException) {
                    return 0 to 1
                }
                Conflict.OVERWRITE -> Unit
            }
        }

        if (srcIsDir) {
            // 폴더 이동은 rename 한 번으로 끝날 수 있다. 같은 볼륨이면 O(1) 이다.
            if (move && !target.exists()) {
                val moved = try {
                    rename(src, target)
                } catch (e: ErrnoException) {
                    if (e.errno == OsConstants.ENOSPC) throw FatalOp(Reason.NO_SPACE, src.name)
                    return 0 to 1
                }
                if (moved) {
                    onUnit(target.absolutePath, 0L, true)
                    return 0 to 0
                }
            }
            if (!target.isDirectory && !target.mkdirs()) return 0 to 1
            var skipped = 0
            var failed = 0
            val children = src.listFiles()
            if (children == null) {
                // 읽을 수 없는 폴더. 빈 대상만 만들어 놓고 '완료' 라고 하면 안 된다.
                return 0 to 1
            }
            for (child in children) {
                val r = transfer(child, target, move, conflict, depth - 1, onUnit)
                skipped += r.first
                failed += r.second
            }
            // 폴더의 수정시각도 되살린다. 복사한 폴더가 전부 '지금' 이 되면 날짜 정렬이 무너진다.
            runCatching { target.setLastModified(src.lastModified()) }
            // 옮긴 뒤 빈 껍데기를 지운다. 건너뛴 것이 남아 있으면 지워지지 않는다.
            if (move && skipped == 0 && failed == 0 && !src.delete()) failed++
            return skipped to failed
        }

        // 파일 이동: 같은 볼륨이면 rename 한 번. EXDEV 면 복사+삭제로 내려간다.
        try {
            if (move && rename(src, target)) {
                onUnit(target.absolutePath, st.st_size, true)
                return 0 to 0
            }
            copyFile(src, target, st.st_size, onUnit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: OperationCanceledException) {
            throw e
        } catch (e: FatalOp) {
            throw e
        } catch (t: Throwable) {
            val reason = reasonOf(t)
            if (reason == Reason.NO_SPACE) throw FatalOp(Reason.NO_SPACE, src.name)
            return 0 to 1
        }

        if (move && !src.delete()) {
            // 복사는 됐는데 원본이 안 지워졌다. 데이터는 안전하므로 실패로만 센다.
            return 0 to 1
        }
        return 0 to 0
    }

    /**
     * `rename(2)`. 볼륨을 넘으면 `EXDEV` 로 실패하고, 그때만 false 를 돌려준다.
     *
     * 다른 오류(권한·없음)는 그대로 던져 위에서 항목 실패로 센다 — 조용히 복사로
     * 내려가면 실패의 진짜 이유가 가려진다.
     */
    private fun rename(src: File, target: File): Boolean = try {
        Os.rename(src.absolutePath, target.absolutePath)
        true
    } catch (e: ErrnoException) {
        when (e.errno) {
            OsConstants.EXDEV -> false
            // 비어 있지 않은 디렉터리를 덮어쓰려 한 경우도 복사 경로로 내려간다.
            OsConstants.ENOTEMPTY, OsConstants.EEXIST, OsConstants.EISDIR, OsConstants.ENOTDIR -> false
            else -> throw e
        }
    }

    /**
     * 파일 하나를 복사한다.
     *
     * **임시 이름으로 쓰고 fsync 한 뒤 원자적으로 제자리에 놓는다.** 대상을 먼저 지우지
     * 않는 것이 중요하다 — `rename(2)` 가 이미 원자적 대체이고, 지우고 옮기는 사이는
     * 원본도 복사본도 없는 창이다.
     *
     * 임시 파일 이름은 **대상 이름과 무관한 고정 길이**다. `.<대상이름>.iroiro-part` 로
     * 만들면 원본 이름이 242바이트를 넘는 순간 임시 이름이 상한을 넘어 `ENAMETOOLONG`
     * 으로 죽는다.
     */
    private suspend fun copyFile(src: File, target: File, size: Long, onUnit: (String, Long, Boolean) -> Unit) {
        val signal = CancellationSignal()
        val handle = currentCoroutineContext().job.invokeOnCompletion { if (it != null) signal.cancel() }
        try {
            // 다섯 걸음(임시본 → fsync → 수정시각 → rename → 부모 fsync)은 [AtomicFileWriter]
            // 하나에 있다. 아카이브 풀기도 같은 것을 쓴다 — 원본을 파괴할 수 있는 경로가
            // 각자 구현하면 다섯 번 중 한 번은 어딘가를 빠뜨린다.
            //
            // 수정시각을 되살리지 않으면 SD↔내부 이동 한 번에 사진 수천 장의 시각이
            // 전부 '지금' 이 되어 날짜 정렬이 무너지고, 이어보기 키도 함께 끊긴다.
            AtomicFileWriter.write(target, src.lastModified()) { output ->
                FileInputStream(src).use { input ->
                    // 512KiB 마다 콜백이 온다. 그것을 그대로 UI 로 올리지 않고 위에서
                    // 100ms 로 묶는다. **executor 를 반드시 넘긴다** — null 이면 리스너가
                    // 한 번도 불리지 않아 큰 파일 하나를 복사하는 내내 진행이 멈춰 보인다.
                    FileUtils.copy(input.fd, output.fd, signal, DIRECT_EXECUTOR) { copied ->
                        onUnit(target.absolutePath, copied, false)
                    }
                }
            }
            onUnit(target.absolutePath, size, true)
        } finally {
            handle.dispose()
        }
    }

    /** 프로세스가 죽어 남은 임시 파일을 걷는다. 구현은 [AtomicFileWriter] 에 있다. */
    private fun sweepPartials(dir: File) = AtomicFileWriter.sweepPartials(dir)

    /**
     * 예외를 이유로 옮긴다.
     *
     * `FileUtils.copy` 는 `ErrnoException` 을 `rethrowAsIOException()` 으로 바꿔 던지므로,
     * **원인을 한 겹 벗겨야** `ENOSPC` 가 보인다. 벗기지 않으면 저장공간 부족이 언제나
     * '입출력 오류' 가 되어, 사용자는 공간을 비우면 된다는 것을 알 수 없다.
     */
    private fun reasonOf(t: Throwable): Reason {
        var e: Throwable? = t
        var hop = 0
        while (e != null && hop < 4) {
            if (e is ErrnoException) return reasonOf(e)
            e = e.cause
            hop++
        }
        return Reason.IO
    }

    private fun reasonOf(e: ErrnoException): Reason = when (e.errno) {
        OsConstants.ENOSPC, OsConstants.EDQUOT -> Reason.NO_SPACE
        OsConstants.ENOENT -> Reason.NOT_FOUND
        OsConstants.EACCES, OsConstants.EPERM, OsConstants.EROFS -> Reason.PERMISSION
        else -> Reason.IO
    }

    // ---- 삭제·이름·폴더 ---------------------------------------------------

    /** 되돌릴 수 없는 삭제. 휴지통을 거치는 삭제는 [TrashStore] 가 한다. */
    suspend fun deletePermanently(
        paths: List<String>,
        onProgress: (Progress) -> Unit,
    ): Outcome = withContext(IroDispatchers.io) {
        val plan = plan(paths)
        var done = 0
        var failed = 0
        val touched = ArrayList<String>(paths.size)
        try {
            for (p in paths) {
                currentCoroutineContext().ensureActive()
                val f = File(p)
                touched += p
                if (deleteRecursively(f, maxDepth)) done++ else failed++
                onProgress(Progress(0, plan.bytes, done, plan.files, f.name))
            }
        } finally {
            withContext(NonCancellable) { media.scanAll(touched) }
        }
        Outcome.Done(done, 0, failed)
    }

    private suspend fun deleteRecursively(file: File, depth: Int): Boolean {
        currentCoroutineContext().ensureActive()
        if (depth <= 0) return false
        val st = try {
            Os.lstat(file.absolutePath)
        } catch (e: ErrnoException) {
            return false
        }
        // 링크는 대상이 아니라 링크 자체만 지운다.
        if (!OsConstants.S_ISLNK(st.st_mode) && OsConstants.S_ISDIR(st.st_mode)) {
            file.listFiles()?.forEach { deleteRecursively(it, depth - 1) }
        }
        return file.delete()
    }

    /**
     * 이름을 바꾼다.
     *
     * **이름을 먼저 맡고 옮긴다.** `exists()` 로 보고 `rename` 하면 그 사이에 다른 앱이
     * 같은 이름을 만들 수 있고, `rename(2)` 는 기존 파일을 말없이 대체한다 — 확인
     * 대화상자도 휴지통도 없이 남의 파일이 사라지는 자리다. 빈 파일(또는 빈 폴더)을
     * `O_EXCL`·`mkdir` 로 먼저 만들어 이름을 선점하면 그 창이 닫힌다.
     */
    fun rename(path: String, newName: String): Outcome {
        val verdict = PathRules.validate(newName)
        if (verdict is PathRules.Verdict.Rejected) return Outcome.Failed(Reason.INVALID_NAME, newName)
        val name = (verdict as PathRules.Verdict.Ok).name
        val src = File(path)
        val target = File(src.parentFile, name)
        if (src.absolutePath == target.absolutePath) return Outcome.Done(1, 0, 0)
        if (!reserveName(target, src.isDirectory)) return Outcome.Failed(Reason.INVALID_NAME, name)
        return try {
            Os.rename(src.absolutePath, target.absolutePath)
            media.scanAll(listOf(path, target.absolutePath))
            Outcome.Done(1, 0, 0)
        } catch (e: ErrnoException) {
            // 맡아 둔 빈 자리를 되돌려 놓는다. 그대로 두면 0바이트 파일이 남는다.
            target.delete()
            Outcome.Failed(reasonOf(e), name)
        }
    }

    /**
     * 이 이름을 우리 것으로 만든다. 이미 있으면 false.
     *
     * 폴더는 빈 폴더로, 파일은 빈 파일로 맡는다 — `rename(2)` 는 빈 폴더 위로 폴더를,
     * 파일 위로 파일을 원자적으로 덮어쓸 수 있다.
     */
    private fun reserveName(target: File, isDirectory: Boolean): Boolean = try {
        if (isDirectory) {
            target.mkdir()
        } else {
            val fd = Os.open(
                target.absolutePath,
                OsConstants.O_CREAT or OsConstants.O_EXCL or OsConstants.O_WRONLY,
                OsConstants.S_IRUSR or OsConstants.S_IWUSR or OsConstants.S_IRGRP or OsConstants.S_IROTH,
            )
            Os.close(fd)
            true
        }
    } catch (e: ErrnoException) {
        false
    }

    /**
     * 사진을 **EXIF 방향 태그만 고쳐** 돌린다. 픽셀을 다시 인코딩하지 않는다.
     *
     * ## 왜 재인코딩하지 않는가
     *
     * 다시 인코딩하면 JPEG 은 화질이 한 번 더 깎이고, HEIC 를 돌리면 JPEG 이 되어
     * **사용자의 파일이 다른 형식으로 바뀐다.** 회전은 '어느 쪽이 위인가' 를 적는 일이지
     * 그림을 다시 그리는 일이 아니다. 태그를 못 읽는 형식(PNG 등)은 **이 함수를 부르지
     * 않는다** — 부르면 파일은 바뀌는데 어느 뷰어에서도 아무 일이 일어나지 않는다.
     *
     * ## 원본을 파괴하지 않는 다섯 걸음
     *
     * `ExifInterface.saveAttributes()` 는 **제자리에서 파일을 다시 쓴다.** 그 도중에
     * 전원이 나가면 반쪽짜리 사진이 남는다. 그래서 복사와 똑같은 절차를 밟는다 —
     * 임시본에 복사 → 임시본의 태그를 고침 → `fsync` → 수정시각 복원 → 원자적 `rename`
     * → **부모 디렉터리 `fsync`**. 마지막 것을 빠뜨리면 저널이 없는 FAT(SD)에서
     * 이름만 사라진 상태가 가능하다.
     *
     * 임시 이름은 반드시 [PART_PREFIX]·[PART_SUFFIX] 규약을 따른다. 다른 이름을 쓰면
     * [sweepPartials] 가 걷어 가지 못해 **사진 원본 크기의 사본이 사용자 폴더에 영영 남는다.**
     *
     * @param degrees 시계 방향 90의 배수.
     */
    suspend fun rotateExif(path: String, degrees: Int): Outcome = withContext(IroDispatchers.io) {
        val src = File(path)
        if (!src.isFile) return@withContext Outcome.Failed(Reason.NOT_FOUND, src.name)
        val turns = ((degrees / 90) % 4 + 4) % 4
        if (turns == 0) return@withContext Outcome.Done(1, 0, 0)
        // **마지막 관문이다.** 화면이 판정을 빠뜨려도 여기서 막는다 — 이 함수 뒤에는
        // 사용자 파일을 다시 쓰는 코드밖에 없다.
        if (!canRotateFile(src)) return@withContext Outcome.Failed(Reason.INVALID_NAME, src.name)

        val parent = src.parentFile ?: return@withContext Outcome.Failed(Reason.IO, src.name)
        val tmp = File(parent, "$PART_PREFIX${UUID.randomUUID().toString().take(8)}$PART_SUFFIX")
        val modified = src.lastModified()

        try {
            withContext(NonCancellable) {
                // ① 임시본으로 복사. 여기서부터 원본은 더 이상 건드리지 않는다.
                FileInputStream(src).use { input ->
                    FileOutputStream(tmp).use { output ->
                        FileUtils.copy(input.fd, output.fd, null, DIRECT_EXECUTOR) { }
                        output.fd.sync()
                    }
                }

                // ② 임시본의 방향 태그만 고친다. 우리 파일 위라 여기서 실패해도 잃을 것이 없다.
                val exif = ExifInterface(tmp.absolutePath)
                val current = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
                exif.setAttribute(ExifInterface.TAG_ORIENTATION, rotated(current, turns).toString())
                exif.saveAttributes()

                // ③ 내용을 디스크에. saveAttributes 는 스스로 sync 하지 않는다.
                FileOutputStream(tmp, true).use { it.fd.sync() }

                // ④ 수정시각을 되살린다. 이것이 없으면 날짜 정렬이 무너지고
                //    이어보기 키(sha256(크기+이름+수정시각))도 끊긴다 — 4단계가 치명으로 고친 자리다.
                tmp.setLastModified(modified)

                // ⑤ 원자적 대체 + 부모 디렉터리 fsync.
                Os.rename(tmp.absolutePath, src.absolutePath)
                runCatching {
                    val dirFd = Os.open(parent.absolutePath, OsConstants.O_RDONLY, 0)
                    try {
                        Os.fsync(dirFd)
                    } finally {
                        Os.close(dirFd)
                    }
                }
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) { tmp.delete() }
            throw e
        } catch (t: Throwable) {
            withContext(NonCancellable) { tmp.delete() }
            return@withContext Outcome.Failed(reasonOf(t), src.name)
        }

        // 죽은 작업이 남긴 임시본이 이 폴더에 있으면 함께 걷는다. 지금까지는 복사·이동의
        // 대상 폴더에서만 돌았고, 회전은 어느 폴더에서나 일어난다.
        sweepPartials(parent)
        media.scanAll(listOf(path))
        Outcome.Done(1, 0, 0)
    }

    /**
     * 이 파일에 회전을 저장할 수 있는가.
     *
     * 기준은 "라이브러리가 쓸 수 있는가" 가 아니라 **"쓰면 실제로 돌아 보이는가"** 다.
     * PNG 은 `saveAttributes` 가 되지만 안드로이드의 PNG 디코더가 EXIF 를 읽지 않아
     * **파일은 바뀌는데 아무 일도 일어나지 않는다.** 움직이는 WebP 는 컨테이너를 다시
     * 쓰는 과정에서 프레임이 보존되는지 확인하지 못했다 — 확인 못 한 것으로 사용자
     * 파일을 걸지 않는다.
     *
     * `core:ui` 의 같은 판정과 뜻이 같아야 한다. 화면은 메뉴를 끄는 데 쓰고, 여기는
     * 실제로 쓰기를 막는 데 쓴다.
     */
    private fun canRotateFile(file: File): Boolean =
        when (file.name.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> true
            "webp" -> !isAnimatedWebp(file)
            else -> false
        }

    /** 움직이는 WebP 인가. RIFF 청크 머리만 훑는다 — 픽셀은 읽지 않는다. */
    private fun isAnimatedWebp(file: File): Boolean = runCatching {
        java.io.RandomAccessFile(file, "r").use { raf ->
            if (raf.length() < 16) return@use false
            val header = ByteArray(12)
            raf.readFully(header)
            if (String(header, 0, 4, Charsets.US_ASCII) != "RIFF") return@use false
            if (String(header, 8, 4, Charsets.US_ASCII) != "WEBP") return@use false
            var pos = 12L
            var scanned = 0
            while (pos + 8 <= raf.length() && scanned < 64) {
                raf.seek(pos)
                val tag = ByteArray(4)
                raf.readFully(tag)
                val name = String(tag, Charsets.US_ASCII)
                val b = ByteArray(4)
                raf.readFully(b)
                val size = (b[0].toInt() and 0xFF) or ((b[1].toInt() and 0xFF) shl 8) or
                    ((b[2].toInt() and 0xFF) shl 16) or ((b[3].toInt() and 0xFF) shl 24)
                if (size < 0) return@use false
                when (name) {
                    "ANIM", "ANMF" -> return@use true
                    "VP8X" -> if (size >= 1) {
                        val flags = raf.read()
                        if (flags >= 0 && (flags and 0x02) != 0) return@use true
                    }
                }
                pos += 8 + size + (size and 1)
                scanned++
            }
            false
        }
    }.getOrDefault(false)

    /**
     * 지금 방향에서 90° 씩 [turns] 번 돌린 방향.
     *
     * **거울상(2·4·5·7)도 제대로 돌린다.** 정방향 넷만 다루고 나머지를 `NORMAL` 로
     * 뭉개면, 좌우가 뒤집힌 사진을 한 번 돌리는 순간 뒤집힘이 조용히 사라진다.
     */
    private fun rotated(orientation: Int, turns: Int): Int {
        // 각 방향을 (회전각, 거울여부)로 풀고 다시 조립한다.
        val table = mapOf(
            ExifInterface.ORIENTATION_NORMAL to (0 to false),
            ExifInterface.ORIENTATION_ROTATE_90 to (90 to false),
            ExifInterface.ORIENTATION_ROTATE_180 to (180 to false),
            ExifInterface.ORIENTATION_ROTATE_270 to (270 to false),
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL to (0 to true),
            ExifInterface.ORIENTATION_TRANSPOSE to (90 to true),
            ExifInterface.ORIENTATION_FLIP_VERTICAL to (180 to true),
            ExifInterface.ORIENTATION_TRANSVERSE to (270 to true),
        )
        val back = table.entries.associate { (k, v) -> v to k }
        val (angle, mirrored) = table[orientation] ?: (0 to false)
        return back[(angle + turns * 90) % 360 to mirrored] ?: ExifInterface.ORIENTATION_NORMAL
    }

    fun createFolder(parent: String, name: String): Outcome {
        val verdict = PathRules.validate(name)
        if (verdict is PathRules.Verdict.Rejected) return Outcome.Failed(Reason.INVALID_NAME, name)
        val dir = File(parent, (verdict as PathRules.Verdict.Ok).name)
        if (dir.exists()) return Outcome.Failed(Reason.INVALID_NAME, name)
        return if (dir.mkdir()) Outcome.Done(1, 0, 0) else Outcome.Failed(Reason.PERMISSION, name)
    }

    internal companion object {
        /**
         * 같은 스레드에서 바로 부르는 실행자.
         *
         * `FileUtils.copy` 에 `null` 을 넘기면 진행 리스너가 **한 번도 불리지 않는다.**
         * AOSP 자신도 같은 파일 안에서 이것을 쓴다.
         */
        val DIRECT_EXECUTOR: java.util.concurrent.Executor = java.util.concurrent.Executor { it.run() }

        /**
         * 임시 파일 이름. **정의는 [AtomicFileWriter] 에 하나만 있다.**
         *
         * 두 곳에 같은 문자열을 두면 한쪽만 고쳤을 때 `sweepPartials` 가 상대의 임시본을
         * 못 걷어 가고, 그러면 원본 크기의 사본이 사용자 폴더에 영영 남는다.
         */
        val PART_PREFIX = AtomicFileWriter.PART_PREFIX
        val PART_SUFFIX = AtomicFileWriter.PART_SUFFIX
    }
}
