package io.github.donggi.iroiroviewer.comic

import io.github.donggi.iroiroviewer.format.FileDocumentSource
import io.github.donggi.iroiroviewer.format.archive.ArchivePasswordException
import io.github.donggi.iroiroviewer.format.archive.Archives
import io.github.donggi.iroiroviewer.format.archive.ComicPages
import io.github.donggi.iroiroviewer.io.FileProbe
import io.github.donggi.iroiroviewer.model.IroDispatchers
import io.github.donggi.iroiroviewer.safety.EntryBudget
import io.github.donggi.iroiroviewer.safety.ParseLimitExceededException
import io.github.donggi.iroiroviewer.safety.ParseLimits
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

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

        /**
         * 암호가 필요하다 — 헤더까지 잠겼거나(7z·RAR), 쪽이 잠겼는데 암호를 받지 않았다.
         * 화면이 암호를 묻는다(ZIP·7z·RAR 의 암호는 공개된 방식이다).
         */
        NEEDS_PASSWORD,

        /** 암호를 받았는데도 풀 수 없는 방식으로 잠겼다(PKWARE 의 강한 암호화 등). */
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
        /**
         * 이번 세션에 넣은 아카이브 암호(없으면 null). 여기서 만드는 소스가 **자기 사본**을 들고
         * 닫을 때 지운다 — 넘긴 배열은 부른 쪽이 지운다.
         */
        password: CharArray? = null,
        /**
         * 파일이 실제로 열리는가([isReachable]). 시험이 '여는 사이에 없어진 파일' 을 흉내 내는 자리다. [onOpen] **앞에** 둔다 —
         * 부르는 쪽이 [onOpen] 을 뒤따르는 람다로 넘긴다.
         */
        reachable: (File) -> Boolean = ::isReachable,
        onOpen: (ComicSource) -> Unit = {},
    ): Result = withContext(IroDispatchers.parsing) {
        val file = File(path)
        if (file.isDirectory) return@withContext openFolder(file, onOpen)
        // **실제로 열어 본다**([isReachable]). `canRead()` 로 물으면 그 답과 여는 답이 어긋나는 파일(FUSE 위의 권한 거부)이
        // 아래에서 `IOException` 으로 떨어져 '깨진 파일'(CORRUPT)이 되고, 열리지 않는 파일에 '다른 앱으로 열기' 가 선다.
        // 여는 것은 디스크 입출력이라 입출력 디스패처로 넘긴다.
        if (!withContext(IroDispatchers.io) { reachable(file) }) return@withContext Result.Failed(Kind.UNREADABLE)

        val result = try {
            // **상한이 선언만 되고 아무 데서도 걸리지 않는 일**을 2단계 검토가 잡았다.
            // 무한 루프형 DoS 는 크기 상한에 걸리지 않는다 — 아무것도 만들어 내지
            // 않으면서 돌기 때문이다.
            withTimeout(limits.openTimeoutMs) {
                runInterruptible { openArchive(file, windowCap, limits, password, onOpen) }
            }
        } catch (e: TimeoutCancellationException) {
            Result.Failed(Kind.TOO_LARGE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ArchivePasswordException) {
            // 헤더까지 잠겼다. `IOException` 보다 먼저 잡는다 — 그쪽은 '깨졌다' 다.
            Result.Failed(Kind.NEEDS_PASSWORD)
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

        // 여는 **사이에** 없어졌을 수도 있다 — solid RAR·7z 를 훑는 몇 초 동안 파일을 지우거나 SD 카드를 뽑으면 리더가
        // `IOException` 을 내고, 압축 판별(`Archives.detect`)은 두 번째 읽기의 실패를 삼켜 '다루지 않는 형식' 으로 돌려준다.
        // 그대로 두면 없는 파일에 '다른 앱으로 열기' 가 선다. 그래서 **단추를 세울 실패**([ComicOpenWith.failureTarget])는
        // 무엇으로 끝났든 한 번 더 열어 본다 — 문서 뷰어의 `failedAfter`, 압축 화면의 `failureKindOf(t, reachable)` 와 같은
        // 판단이다. 성공과 단추가 없는 실패(암호를 묻는다·그림이 없다)는 다시 열지 않는다.
        if (result is Result.Failed && ComicOpenWith.failureTarget(result.kind, path) != null) {
            Result.Failed(failedKind(result.kind, withContext(IroDispatchers.io) { reachable(file) }))
        } else {
            result
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
        password: CharArray?,
        onOpen: (ComicSource) -> Unit,
    ): Result {
        val source = FileDocumentSource(file)
        if (Archives.detect(source) == null) {
            // 확장자가 압축이어도 우리가 여는 아카이브가 아니면 열지 않는다. 판별은 tar 계열(.cbt·압축 tar)까지 보고
            // 압축 스트림은 안의 첫 머리까지 풀어 본다 — `.gz` 로 싼 파일 하나·iso·분할 아카이브가 여기로 와
            // '이 앱이 다루지 않는 압축 형식입니다' 로 끝난다. 판별은 목록을 세우지 않는다(항목이 많은 tar 도 tar 다).
            // 압축 안 한 tar 는 번호로 여는 리더(`randomAccess`), 압축 tar 는 흐름이라 solid 7z 와 같은 길이다.
            return Result.Failed(Kind.UNSUPPORTED)
        }

        val budget = EntryBudget(limits)
        val reader = Archives.open(source, limits, budget, password)
        var keep = false
        try {
            val pages = archivePagesOf(reader.entries)
            if (pages.isEmpty()) {
                // **잠긴 그림이 있으면 '그림이 없다' 가 아니다.** 예전 조건은 '폴더 항목이 하나도
                // 없고 전부 잠겼다' 였는데, 쪽을 폴더에 담은 CBZ(흔하다)는 폴더 항목 때문에
                // '그림이 한 장도 없습니다' 로 나갔다. 잠긴 **그림 이름**이 있는지를 본다.
                val lockedPages = reader.entries.filter {
                    it.isEncrypted && !it.isReadable && ComicPages.isPage(it.name)
                }
                return Result.Failed(
                    when {
                        lockedPages.isEmpty() -> Kind.NO_PAGES
                        // 암호를 넣으면 풀리는 쪽이 있을 때만 묻는다. 암호를 받았는데도 남았거나
                        // 암호로도 못 여는 방식이면(강한 암호화·안쪽 압축 방식) 물어도 소용이 없다.
                        lockedPages.any { it.needsPassword } -> Kind.NEEDS_PASSWORD
                        else -> Kind.ENCRYPTED
                    }
                )
            }
            val source = if (reader.randomAccess) {
                keep = true
                ArchiveComicSource(reader, budget, pages)
            } else {
                // solid 는 리더를 들고 있지 않는다 — 패스마다 새로 연다(SolidComicSource
                // 의 주석 참고). 목록을 만든 이 리더는 여기서 닫는다.
                SolidComicSource(file, pages, windowCap, limits, password)
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

/** 여는 데 실패한 뒤의 종류. 이제 열리지 않으면 무엇이 났든 [ComicOpen.Kind.UNREADABLE] 이다 — 없는 파일은 깨진 파일이 아니다. */
internal fun failedKind(kind: ComicOpen.Kind, reachable: Boolean): ComicOpen.Kind =
    if (reachable) kind else ComicOpen.Kind.UNREADABLE

/**
 * [file] 이 읽으러 갈 수 있는 파일인가 — 보통 파일이고 **실제로 열린다.** 디스크 입출력이다(부르는 쪽이 입출력 디스패처에서
 * 부른다).
 *
 * `canRead()` 로 묻지 않는다 — 묻는 답과 여는 답이 FUSE 위에서 같다는 것을 확인한 적이 없다. 탐침은 [FileProbe] 한 벌이다
 * (문서·압축·이미지 뷰어가 같은 답을 낸다). 책을 여는 문([ComicOpen.open]),
 * 못 연 쪽의 단추([ComicOpenWith.pageFailureTarget]), 다음 권 찾기([NextVolume.find])가 이것을 쓴다 — 셋이 한 답을 내야 권해
 * 준 책이 열리고 단추가 선 파일이 넘어간다. 여는 일은 [open] 이 한다 — 시험이 열리지 않는 파일을
 * 흉내 내는 자리다(JVM 에서는 있는데 열리지 않는 파일을 만들 수 없다).
 */
internal fun isReachable(file: File, open: (File) -> Unit = FileProbe.openAndClose): Boolean = FileProbe.opens(file, open)
