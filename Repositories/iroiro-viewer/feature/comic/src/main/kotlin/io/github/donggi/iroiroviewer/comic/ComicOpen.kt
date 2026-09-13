package io.github.donggi.iroiroviewer.comic

import io.github.donggi.iroiroviewer.format.FileDocumentSource
import io.github.donggi.iroiroviewer.format.archive.Archives
import io.github.donggi.iroiroviewer.model.IroDispatchers
import io.github.donggi.iroiroviewer.safety.EntryBudget
import io.github.donggi.iroiroviewer.safety.ParseLimitExceededException
import io.github.donggi.iroiroviewer.safety.ParseLimits
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.IOException

/**
 * 경로 하나를 **만화 한 권으로** 연다.
 *
 * 여는 길이 셋이다 — ZIP 계열, solid 아카이브, 폴더. 셋의 차이는 [ComicSource] 구현이
 * 감추고, 여기서는 "어느 것인가" 와 "못 열면 왜인가" 만 정한다.
 *
 * **못 여는 이유를 종류로 돌려준다.** 예외 메시지를 화면에 그대로 보내면 절대경로와
 * 공격자가 심은 문자열이 함께 나간다(저장소 규칙).
 */
object ComicOpen {

    sealed interface Result {
        data class Ready(val source: ComicSource) : Result
        data class Failed(val kind: Kind) : Result
    }

    enum class Kind {
        /** 파일이 없거나 읽을 수 없다. */
        UNREADABLE,

        /** 아카이브가 깨졌다. */
        CORRUPT,

        /** 우리가 열지 않기로 한 갈래(tar·iso·분할 아카이브). */
        UNSUPPORTED,

        /** 방어 상한을 넘었다. */
        TOO_LARGE,

        /** 아카이브 전체에 암호가 걸려 있다. */
        ENCRYPTED,

        /** 열리기는 했는데 **그림이 한 장도 없다.** */
        NO_PAGES,
    }

    /**
     * @param windowCap solid 아카이브에서 힙에 들고 있을 원본 바이트
     *   ([io.github.donggi.iroiroviewer.safety.ComicLimits.windowBytes]).
     * @param onOpen 리더가 **만들어진 그 자리에서** 부른다. 돌려주기 전에 부르는 것이
     *   요점이다 — 아래 '취소가 리더를 잃어버리는 창' 참고.
     */
    suspend fun open(
        path: String,
        windowCap: Long,
        limits: ParseLimits = ParseLimits.DEFAULT,
        onOpen: (ComicSource) -> Unit = {},
    ): Result = withContext(IroDispatchers.parsing) {
        val file = File(path)
        if (file.isDirectory) return@withContext openFolder(file, onOpen)
        if (!file.isFile || !file.canRead()) return@withContext Result.Failed(Kind.UNREADABLE)

        try {
            // **상한이 선언만 되고 아무 데서도 걸리지 않는 일**을 2단계 검토가 잡았다.
            // 무한 루프형 DoS 는 크기 상한에 걸리지 않는다 — 아무것도 만들어 내지
            // 않으면서 돌기 때문이다.
            withTimeout(limits.openTimeoutMs) {
                runInterruptible { openArchive(file, windowCap, limits, onOpen) }
            }
        } catch (e: TimeoutCancellationException) {
            Result.Failed(Kind.TOO_LARGE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ParseLimitExceededException) {
            Result.Failed(Kind.TOO_LARGE)
        } catch (e: IOException) {
            Result.Failed(Kind.CORRUPT)
        } catch (e: IllegalStateException) {
            Result.Failed(Kind.UNSUPPORTED)
        } catch (e: RuntimeException) {
            Result.Failed(Kind.CORRUPT)
        } catch (e: OutOfMemoryError) {
            Result.Failed(Kind.TOO_LARGE)
        }
    }

    /**
     * ## 취소가 리더를 잃어버리는 창
     *
     * 여기서 만든 [ComicSource] 는 `withTimeout` → `withContext` 두 겹을 거쳐 호출자에게
     * 간다. 그 사이에 잡이 취소되면 **코루틴은 값을 버리고 `CancellationException` 을
     * 던진다** — 호출자는 리더를 받은 적이 없으므로 닫을 손잡이가 없고, 열린 `ZipFile`·
     * `SevenZFile` 이 파일 서술자와 7z 사전 메모리를 쥔 채 GC 를 기다린다.
     *
     * 그래서 [onOpen] 을 **만든 자리에서** 부른다. 호출자는 `finally` 에서 그것을 닫으면
     * 되고, 성공 경로에서는 소유권이 넘어갔다고 표시하면 된다. 값을 돌려주는 것과
     * 소유권을 넘기는 것을 갈라 놓는 것이 요점이다.
     */
    private fun openArchive(
        file: File,
        windowCap: Long,
        limits: ParseLimits,
        onOpen: (ComicSource) -> Unit,
    ): Result {
        val source = FileDocumentSource(file)
        if (Archives.probeContainer(source.head(16)) == null) {
            // 확장자가 압축이어도 매직이 아니면 열지 않는다. tar·gz·iso·분할 아카이브가
            // 여기로 온다 — '이 앱이 다루지 않는 압축 형식입니다' 로 정확히 끝낸다.
            return Result.Failed(Kind.UNSUPPORTED)
        }

        val budget = EntryBudget(limits)
        val reader = Archives.open(source, limits, budget)
        var keep = false
        try {
            val pages = archivePagesOf(reader.entries)
            if (pages.isEmpty()) {
                // 그림이 없는데 읽을 수 있는 항목도 없다면 암호일 가능성이 높다.
                val allLocked = reader.entries.isNotEmpty() &&
                    reader.entries.none { it.isDirectory } &&
                    reader.entries.filterNot { it.isDirectory }.all { it.isEncrypted }
                return Result.Failed(if (allLocked) Kind.ENCRYPTED else Kind.NO_PAGES)
            }
            val source = if (reader.randomAccess) {
                keep = true
                ArchiveComicSource(reader, budget, pages)
            } else {
                // solid 는 리더를 들고 있지 않는다 — 패스마다 새로 연다(SolidComicSource
                // 의 주석 참고). 목록을 만든 이 리더는 여기서 닫는다.
                SolidComicSource(file, pages, windowCap, limits)
            }
            onOpen(source)
            return Result.Ready(source)
        } finally {
            if (!keep) reader.close()
        }
    }

    private fun openFolder(dir: File, onOpen: (ComicSource) -> Unit): Result {
        val files = comicFilesIn(dir)
        if (files.isEmpty()) return Result.Failed(Kind.NO_PAGES)
        val source = FolderComicSource(files, folderPagesOf(files))
        onOpen(source)
        return Result.Ready(source)
    }
}
