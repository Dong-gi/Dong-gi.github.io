package io.github.donggi.iroiroviewer.comic

import io.github.donggi.iroiroviewer.format.FileDocumentSource
import io.github.donggi.iroiroviewer.format.archive.ArchiveEntry
import io.github.donggi.iroiroviewer.format.archive.ArchiveReader
import io.github.donggi.iroiroviewer.format.archive.Archives
import io.github.donggi.iroiroviewer.format.archive.ComicBook
import io.github.donggi.iroiroviewer.format.archive.ComicPage
import io.github.donggi.iroiroviewer.format.archive.ComicPages
import io.github.donggi.iroiroviewer.format.archive.EntrySink
import io.github.donggi.iroiroviewer.io.Iro
import io.github.donggi.iroiroviewer.model.IroDispatchers
import io.github.donggi.iroiroviewer.safety.EntryBudget
import io.github.donggi.iroiroviewer.safety.ParseLimits
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.OutputStream

/**
 * 만화 한 권의 쪽을 **바이트로** 공급한다.
 *
 * ## 왜 바이트인가 — 디스크에 뽑지 않는다
 *
 * 아카이브 안의 쪽에는 경로가 없다. 캐시 폴더에 뽑아 경로를 만드는 길이 있고 그쪽이
 * 코드는 단순하지만, 썸네일 캐시를 `filesDir` 에 둔 그 논리가 여기서는 **반대 방향으로**
 * 작용한다 — 썸네일은 320px 짜리 파생물이지만 만화 쪽은 **원본 그대로**다. 200쪽짜리
 * 열 권을 보면 사용자의 만화가 통째로 앱 저장소에 평문 복제되고, 숨긴 폴더에서 연
 * 책도 예외가 아니며, 프로세스가 죽으면 청소할 사람이 없다.
 *
 * 그래서 쪽은 **힙에만** 둔다(`ImageIo.decodeFitted(bytes, …)` 가 이 결정 위에 서 있다).
 * 대가는 solid 아카이브에서 되돌아갈 때 다시 푸는 비용이고, 그것은 [SolidComicSource]
 * 의 창으로 눌렀다.
 *
 * ## 스레드 규칙
 *
 * 구현은 **스레드 안전하지 않은 리더**를 쥔다. 모든 진입점이 [Mutex] 를 지나므로
 * 호출자는 아무 코루틴에서나 불러도 되지만, 두 쪽을 동시에 얻을 수는 없다.
 */
interface ComicSource : AutoCloseable {

    val pages: List<ComicPage>

    /** 앞엣것을 풀어야 뒤엣것이 나오는가. 화면이 미리 읽기 폭을 이 값으로 정한다. */
    val solid: Boolean

    /**
     * 쪽 하나의 원본 바이트. 없거나 읽을 수 없으면 null.
     *
     * **블로킹 해제를 안에서 돌린다.** 취소는 스레드 인터럽트로 전달되고 구현이 그것을
     * 본다 — solid 아카이브에서 한 번의 읽기가 몇 초씩 걸릴 수 있어서 필요한 장치다.
     */
    suspend fun bytes(ordinal: Int): ByteArray?
}

/**
 * 무작위 접근이 싼 아카이브(ZIP·CBZ).
 *
 * 엔트리마다 독립 압축이라 필요한 쪽만 푼다. 8단계 실측에서 ZIP 의 `open()` 반복은
 * 엔트리 수에 **선형**이었다(7z 은 제곱이었다) — 그래서 여기서는 창도 미리 읽기도
 * 필요 없고, 화면이 원하는 쪽을 그때그때 준다.
 */
internal class ArchiveComicSource(
    private val reader: ArchiveReader,
    private val budget: EntryBudget,
    override val pages: List<ComicPage>,
) : ComicSource {

    private val mutex = Mutex()
    private val byIndex: Map<Int, ArchiveEntry> = reader.entries.associateBy { it.index }

    override val solid: Boolean get() = false

    override suspend fun bytes(ordinal: Int): ByteArray? {
        val page = pages.getOrNull(ordinal) ?: return null
        val entry = byIndex[page.entryIndex] ?: return null
        return mutex.withLock {
            runInterruptible(IroDispatchers.parsing) {
                // **쪽마다 총량을 끊는다.** 총 출력 상한은 '이 리더로 평생 읽을 양' 이
                // 아니라 '한 번에 풀어낼 양' 이다 — 끊지 않으면 300쪽짜리를 끝까지
                // 넘기는 것만으로 1 GiB 상한에 걸린다(EntryBudget 의 같은 주석 참고).
                budget.resetOutput()
                readOrNull { reader.open(entry).use { it.readAllBounded() } }
            }
        }
    }

    override fun close() = reader.close()
}

/**
 * solid 아카이브(7z·RAR). **창을 하나 들고 그 안에서만 꺼낸다.**
 *
 * ## 왜 창인가
 *
 * `ArchiveReader` 의 주석이 "만화 뷰어는 열 때 한 번에 다 풀어 캐시" 라고 적어 두었지만,
 * 그 문장은 **어디에** 캐시하는지를 말하지 않았다. 디스크는 위 [ComicSource] 의 이유로
 * 쓰지 않고, 힙에 전부 올리면 300쪽짜리가 수백 MB 다. 그래서 앞에서부터 읽되
 * **바이트 상한에 닿으면 멈추는** 창을 둔다.
 *
 * ## 창을 '쪽 수' 가 아니라 '바이트' 로 닫는 이유
 *
 * 쪽 수로 닫으려면 쪽 하나의 크기를 알아야 하는데, 헤더의 선언 크기는 **공격자가 적는
 * 값**이다. 실제로 받은 바이트를 세면 헤더가 거짓말을 해도 상한이 성립하고, 쪽 수를
 * 미리 알 필요도 없다.
 */
internal class SolidComicSource(
    private val file: File,
    override val pages: List<ComicPage>,
    private val windowCap: Long,
    private val limits: ParseLimits = ParseLimits.DEFAULT,
) : ComicSource {

    private val mutex = Mutex()

    /** 지금 힙에 든 쪽들. 키는 쪽 번호(ordinal). */
    private val window = HashMap<Int, ByteArray>()
    private var windowBytes = 0L

    /** 마지막 패스가 어디서 시작했는가. 진단과 실측에만 쓴다. */
    var windowStart: Int = -1
        private set

    /** 패스를 몇 번 돌았는가. **이 수가 solid 만화의 체감을 정한다** — 시험이 단언한다. */
    var passCount: Int = 0
        private set

    override val solid: Boolean get() = true

    override suspend fun bytes(ordinal: Int): ByteArray? {
        if (ordinal !in pages.indices) return null
        mutex.withLock {
            window[ordinal]?.let { return it }
            // **되돌아가기도 앞으로 가기도 새 패스다.** 리더는 인덱스 오름차순으로 한 번만
            // 훑는 계약이고(junrar 는 되감으면 사전을 초기화한다), 그것을 우회하는 코드를
            // 두는 것보다 패스를 다시 여는 편이 규칙이 하나 적다.
            refill(ordinal)
            return window[ordinal]
        }
    }

    private suspend fun refill(want: Int) = runInterruptible(IroDispatchers.parsing) {
        val began = System.nanoTime()
        // **요청한 쪽보다 앞에서 시작한다.** 화면은 보고 있는 쪽만 요청하지 않는다 —
        // 페이저가 앞뒤 한 장씩을 함께 띄우므로, 창을 요청한 쪽에서 정확히 시작하면
        // **바로 다음 요청(이전 쪽)이 반드시 창 밖**이라 방금 만든 창을 버리고 패스를
        // 한 번 더 돈다. 실측으로 확인했다(15쪽으로 뛰기 = 1683ms + 1021ms).
        //
        // 되돌아가는 중이면 더 앞에서 시작한다. 한 쪽씩 뒤로 넘기는 사람에게는 창이
        // 언제나 '방금 지나온 쪽' 바깥에 있어서, 넘길 때마다 패스가 도는 것을 막는다.
        val lookBehind = if (want < windowStart) {
            (window.size / 2).coerceAtLeast(LOOK_BEHIND)
        } else {
            LOOK_BEHIND
        }
        val from = (want - lookBehind).coerceAtLeast(0)

        window.clear()
        windowBytes = 0L
        windowStart = from
        passCount++

        // **패스마다 예산을 새로 만든다.** `EntryBudget.entryCount` 는 `resetOutput` 이
        // 되돌리지 않는 **단조 증가** 값이고, 리더 생성자가 엔트리마다 `beginEntry()` 를
        // 부른다. 예산을 재사용하면 정해진 횟수만큼 쪽을 넘긴 뒤 멀쩡한 만화가
        // `maxEntries` 로 거절된다.
        val budget = EntryBudget(limits)
        val wanted = HashMap<Int, Int>(pages.size)
        for (i in from until pages.size) wanted[pages[i].entryIndex] = i

        readOrNull {
            try {
                Archives.open(FileDocumentSource(file), limits, budget).use { reader ->
                    reader.extractSequentially(WindowSink(wanted, want))
                }
            } catch (stop: StopPass) {
                // 창이 찼다. **정상 종료다** — 남은 엔트리를 마저 푸는 것은 버릴 바이트를
                // 만드는 일이고, solid 아카이브에서 그것이 패스 시간의 대부분이다.
            }
        } ?: run {
            // 취소가 아닌 실패다. 반쯤 찬 창을 남기면 그 쪽들이 다음에 '있는 것' 으로
            // 보이는데, 아카이브가 깨진 자리라면 그 뒤가 통째로 어긋나 있을 수 있다.
            window.clear()
            windowBytes = 0L
        }
        // **이 수가 solid 만화의 체감을 정한다.** 2단계가 '만화 뷰어가 실제로 페이지를
        // 넘길 때라야 의미 있는 수가 나온다' 며 여기까지 미뤄 둔 실측이다.
        // 릴리스에서는 호출 지점째 사라진다.
        Iro.d(TAG) {
            val ms = (System.nanoTime() - began) / 1_000_000
            "solid 패스 ${want}쪽을 위해 ${from}쪽부터 ${window.size}쪽 ${windowBytes}바이트 ${ms}ms"
        }
        Unit
    }

    /**
     * 창이 찰 때까지 담고, 찬 뒤에는 전부 건너뛴다.
     *
     * 건너뛰는 것이 공짜가 아니라는 점이 solid 의 본질이다 — 7z 리더는 그 분량을
     * 드레인해 예산에 잡고, junrar 는 `skipFile` 로 스스로 지나간다. 어느 쪽이든
     * **우리가 요청하지 않으면 그 바이트가 힙에 남지 않는다.**
     */
    private inner class WindowSink(
        private val wanted: Map<Int, Int>,
        /** 이번에 **반드시** 담아야 하는 쪽. 상한보다 이쪽이 먼저다. */
        private val required: Int,
    ) : EntrySink {

        private val buffers = HashMap<Int, ByteArrayOutputStream>()

        override fun begin(entry: ArchiveEntry): OutputStream? {
            val ordinal = wanted[entry.index] ?: return null
            // **요청받은 쪽은 상한과 무관하게 담는다.** 창은 성능 장치이지 기능 제한이
            // 아니다. 앞을 내다보는 [LOOK_BEHIND] 때문에 창이 요청한 쪽에 닿기 전에 차면,
            // 그 쪽이 영영 안 나오고 요청할 때마다 패스만 도는 무한 반복이 된다.
            if (ordinal == required) return newBuffer(ordinal)
            if (windowBytes >= windowCap && window.isNotEmpty()) return null
            return newBuffer(ordinal)
        }

        private fun newBuffer(ordinal: Int): OutputStream {
            val sink = ByteArrayOutputStream(INITIAL_BUFFER)
            buffers[ordinal] = sink
            return sink
        }

        override fun finish(entry: ArchiveEntry, written: Long, failure: Throwable?) {
            val ordinal = wanted[entry.index] ?: return
            val sink = buffers.remove(ordinal) ?: return
            // 실패한 쪽은 담지 않는다. 화면이 '이 쪽을 열 수 없습니다' 로 말한다.
            if (failure != null || written <= 0L) return
            val bytes = sink.toByteArray()
            window[ordinal] = bytes
            windowBytes += bytes.size
            // **다 찼으면 거기서 그만둔다.** 요청받은 쪽까지 담았다면 남은 엔트리를 마저
            // 푸는 것은 곧바로 버릴 바이트를 만드는 일이다. 표지 한 장을 얻으려고 300쪽을
            // 전부 푸는 것도 이 한 줄이 막는다.
            if (windowBytes >= windowCap && window.containsKey(required)) throw StopPass()
        }
    }

    override fun close() {
        window.clear()
        windowBytes = 0L
    }

    /**
     * 패스를 여기서 끝낸다는 신호. 오류가 아니다.
     *
     * 스택 트레이스를 만들지 않는다 — 쪽을 넘길 때마다 도는 자리라 그 값이 아깝고,
     * 어차피 아무도 읽지 않는다.
     */
    private class StopPass : RuntimeException(null, null, false, false)

    private companion object {
        /** 흔한 만화 쪽 하나의 크기. 여기서 시작하면 되풀이 복사가 대개 없다. */
        const val INITIAL_BUFFER = 512 * 1024
        const val TAG = "Comic"

        /**
         * 창을 요청한 쪽보다 몇 쪽 앞에서 시작하는가.
         *
         * 페이저가 `beyondViewportPageCount = 1` 로 앞뒤 한 장씩을 띄우므로 최소 1 이
         * 필요하다. 그보다 키우면 앞으로 가는 쪽의 창이 그만큼 줄어든다.
         */
        const val LOOK_BEHIND = 1
    }
}

/**
 * 폴더 만화. 쪽이 이미 디스크 위의 파일이다.
 *
 * 그래도 **바이트로 준다.** 경로를 주면 화면이 두 갈래(경로 디코딩 / 바이트 디코딩)로
 * 갈리고, 그 둘은 표본 크기·취소·EXIF 처리가 서로 다른 길을 타게 된다. 폴더 만화의
 * 쪽은 어차피 한 장씩 읽으므로 바이트로 맞추는 값이 싸다.
 */
internal class FolderComicSource(
    private val files: List<File>,
    override val pages: List<ComicPage>,
) : ComicSource {

    override val solid: Boolean get() = false

    override suspend fun bytes(ordinal: Int): ByteArray? {
        val page = pages.getOrNull(ordinal) ?: return null
        val file = files.getOrNull(page.entryIndex) ?: return null
        return runInterruptible(IroDispatchers.io) {
            readOrNull { file.inputStream().use { it.readAllBounded() } }
        }
    }

    override fun close() = Unit
}

/**
 * 읽기 한 번을 감싸는 경계.
 *
 * **취소를 실패로 세지 않는다.** `runCatching` 으로 뭉뚱그리면 인터럽트(= 코루틴 취소)가
 * '이 쪽을 열 수 없습니다' 로 보고되고, 쪽을 빨리 넘기는 사람에게는 지나간 쪽마다
 * 오류가 찍힌다. 취소는 [InterruptedException] 으로 되던져 [runInterruptible] 이
 * `CancellationException` 으로 바꾸게 한다 — 저장소 규칙의 '경계 catch 의 첫 줄' 이다.
 *
 * @return 성공하면 [Unit] 대신 쓸 값, 실패하면 null.
 */
internal fun <T> readOrNull(block: () -> T): T? = try {
    block()
} catch (e: InterruptedIOException) {
    // 인터럽트 플래그는 예외를 던지며 지워졌을 수 있다. 되살려 두어야 바깥 루프도 멈춘다.
    Thread.currentThread().interrupt()
    throw InterruptedException(e.message)
} catch (e: IOException) {
    null
} catch (e: RuntimeException) {
    null
} catch (e: OutOfMemoryError) {
    null
}

/**
 * 스트림을 끝까지 읽되 **상한에서 끊는다.**
 *
 * `readAllBytes`·`readNBytes` 를 쓰지 않는다 — 자바 9 의 함수라 컴파일도 되고 JVM
 * 시험도 통과하는데 **안드로이드에는 API 33 부터** 있어 minSdk 31 기기에서
 * `NoSuchMethodError` 로 앱이 즉사한다(7단계에서 실제로 죽였다).
 */
internal fun InputStream.readAllBounded(
    cap: Long = ParseLimits.DEFAULT.maxSingleOutput,
): ByteArray? {
    val out = ByteArrayOutputStream(64 * 1024)
    val buf = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        val n = read(buf)
        if (n < 0) break
        total += n
        if (total > cap) return null
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}

/** 폴더 안에서 쪽이 될 파일들을, 읽는 차례대로. */
internal fun comicFilesIn(dir: File): List<File> {
    val all = dir.listFiles()?.filter { it.isFile && ComicPages.isPage(it.name) } ?: emptyList()
    // 나열 순서는 파일시스템이 정하는 값이라 기기마다 다르다. 이름으로 세운다.
    val order = ComicPages.order(all.map { it.name })
    return order.map { all[it] }
}

/** 폴더 만화의 쪽 목록. 파일 목록의 **자리**가 [ComicPage.entryIndex] 다. */
internal fun folderPagesOf(files: List<File>): List<ComicPage> =
    files.mapIndexed { i, f ->
        ComicPage(ordinal = i, entryIndex = i, name = f.name, declaredSize = f.length())
    }

/** 아카이브 엔트리에서 쪽 목록. [ComicBook] 이 규칙의 주인이다. */
internal fun archivePagesOf(entries: List<ArchiveEntry>): List<ComicPage> = ComicBook.pagesOf(entries)
