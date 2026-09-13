package io.github.donggi.iroiroviewer.format.archive

import io.github.donggi.iroiroviewer.format.FileDocumentSource
import io.github.donggi.iroiroviewer.safety.EntryBudget
import io.github.donggi.iroiroviewer.safety.ParseLimitExceededException
import io.github.donggi.iroiroviewer.safety.ParseLimits
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 순차 추출.
 *
 * **이 시험이 지키는 것은 셋이다** — 바이트가 맞는가, 비용이 선형인가, 하나가 실패해도
 * 나머지가 가는가. 가운데 것이 이 API 가 존재하는 유일한 이유이므로 **성능 불변식을
 * 직접 단언한다**(7단계에서 CRLF 앵커 결함을 정확성 시험 26건이 전부 놓친 뒤로 생긴 습관이다).
 */
class SequentialExtractTest {

    private val dir: File = Files.createTempDirectory("iroiro-seq2").toFile()

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    /** 받은 것을 메모리에 모으는 소비자. [select] 가 거짓이면 건너뛴다. */
    private class Collector(val select: (ArchiveEntry) -> Boolean = { true }) : EntrySink {
        val got = LinkedHashMap<Int, ByteArray>()
        val failures = LinkedHashMap<Int, Throwable>()
        val finished = ArrayList<Int>()
        private val open = HashMap<Int, ByteArrayOutputStream>()

        override fun begin(entry: ArchiveEntry): OutputStream? {
            if (!entry.isReadable || !select(entry)) return null
            return ByteArrayOutputStream().also { open[entry.index] = it }
        }

        override fun finish(entry: ArchiveEntry, written: Long, failure: Throwable?) {
            finished += entry.index
            val buf = open.remove(entry.index) ?: return
            if (failure != null) failures[entry.index] = failure else got[entry.index] = buf.toByteArray()
            assertEquals(buf.size().toLong(), written, "${entry.index}: 쓴 바이트가 맞지 않는다")
        }
    }

    private fun open(f: File, limits: ParseLimits = ParseLimits.DEFAULT) =
        Archives.open(FileDocumentSource(f), limits, EntryBudget(limits))

    // ---- 바이트 ----------------------------------------------------------------

    @Test
    fun `ZIP 순차 추출이 번호마다 옳은 바이트를 낸다`() {
        val f = ArchiveSamples.zip(File(dir, "a.zip"), count = 12, bytesEach = 777)
        val c = Collector()
        open(f).use { it.extractSequentially(c) }
        assertEquals(12, c.got.size)
        for (i in 0 until 12) assertContentEquals(ArchiveSamples.payload(i, 777), c.got[i], "$i 번")
        assertTrue(c.failures.isEmpty())
    }

    @Test
    fun `7z 순차 추출이 번호마다 옳은 바이트를 낸다`() {
        val f = ArchiveSamples.sevenZ(File(dir, "a.7z"), count = 12, bytesEach = 777)
        val c = Collector()
        open(f).use { it.extractSequentially(c) }
        assertEquals(12, c.got.size)
        for (i in 0 until 12) assertContentEquals(ArchiveSamples.payload(i, 777), c.got[i], "$i 번")
    }

    /** 골라 뽑아도 고른 것만, 그리고 **옳은 것만** 나와야 한다. */
    @Test
    fun `일부만 골라도 그 바이트가 맞는다`() {
        val wanted = setOf(1, 4, 9)
        for (f in listOf(
            ArchiveSamples.zip(File(dir, "s.zip"), count = 12, bytesEach = 512),
            ArchiveSamples.sevenZ(File(dir, "s.7z"), count = 12, bytesEach = 512),
        )) {
            val c = Collector { it.index in wanted }
            open(f).use { it.extractSequentially(c) }
            assertEquals(wanted, c.got.keys, "${f.name}: 고른 것만 나와야 한다")
            for (i in wanted) assertContentEquals(ArchiveSamples.payload(i, 512), c.got[i], "${f.name} $i 번")
        }
    }

    /** 디렉터리·링크·암호·위험한 이름은 [EntrySink.begin] 이 걸러 낸다. */
    @Test
    fun `풀 수 없는 항목은 아예 시작되지 않는다`() {
        val f = ArchiveSamples.zipWithNames(
            File(dir, "mixed.zip"),
            listOf("ok.txt", "../탈출.txt", "trailing. "),
        )
        val c = Collector()
        open(f).use { it.extractSequentially(c) }
        assertEquals(1, c.got.size, "멀쩡한 하나만 풀려야 한다")
        assertEquals(1, c.finished.size, "건너뛴 것에는 finish 를 부르지 않는다")
    }

    // ---- 비용 ----------------------------------------------------------------

    /**
     * **이 API 가 존재하는 이유.** 엔트리를 두 배로 늘려도 시간은 두 배여야 한다.
     *
     * 같은 아카이브를 [ArchiveReader.open] 으로 하나씩 뽑으면 3.85배였다
     * ([SequentialCostTest] 참고). 잡음을 감안해 2.6배를 넘으면 실패로 본다.
     */
    @Test
    fun `7z 순차 추출은 선형이다`() {
        fun time(count: Int): Long {
            val f = ArchiveSamples.sevenZ(File(dir, "t$count.7z"), count, bytesEach = 4096)
            repeat(2) { open(f).use { r -> r.extractSequentially(Collector()) } }
            val t0 = System.nanoTime()
            open(f).use { r -> r.extractSequentially(Collector()) }
            return System.nanoTime() - t0
        }

        val small = time(60)
        val big = time(120)
        val ratio = big.toDouble() / small.toDouble()
        println("7z 순차: 60개 ${small / 1_000_000}ms, 120개 ${big / 1_000_000}ms, 배율 ${"%.2f".format(ratio)}")
        assertTrue(ratio < 2.6, "순차인데 ${"%.2f".format(ratio)}배다 — 제곱 경로로 돌아갔다")
    }

    // ---- 실패와 예산 ----------------------------------------------------------

    /** 한 엔트리가 터져도 **나머지는 간다.** 1,000개 중 3번째 실패로 997개를 버리지 않는다. */
    @Test
    fun `엔트리 하나가 실패해도 나머지는 계속 간다`() {
        val f = ArchiveSamples.zip(File(dir, "fail.zip"), count = 6, bytesEach = 256)
        val sink = object : EntrySink {
            val done = ArrayList<Int>()
            val failed = ArrayList<Int>()
            override fun begin(entry: ArchiveEntry): OutputStream? =
                if (entry.index == 2) {
                    object : OutputStream() {
                        override fun write(b: Int) = throw java.io.IOException("디스크가 꽉 찼다고 치자")
                        override fun write(b: ByteArray, off: Int, len: Int) =
                            throw java.io.IOException("디스크가 꽉 찼다고 치자")
                    }
                } else {
                    ByteArrayOutputStream()
                }

            override fun finish(entry: ArchiveEntry, written: Long, failure: Throwable?) {
                if (failure == null) done += entry.index else failed += entry.index
            }
        }
        open(f).use { it.extractSequentially(sink) }
        assertEquals(listOf(2), sink.failed)
        assertEquals(listOf(0, 1, 3, 4, 5), sink.done)
    }

    /**
     * **건너뛴 엔트리도 예산에 잡힌다.**
     *
     * solid 아카이브에서 건너뛰는 것은 '안 푸는 것' 이 아니라 '풀어서 버리는 것' 이다.
     * 그 바이트를 세지 않으면 압축폭탄이 계측 바깥으로 샌다 — 아무것도 고르지 않고
     * 아카이브를 지나가는 것만으로 상한을 넘길 수 있어야 한다.
     */
    @Test
    fun `7z 에서 아무것도 고르지 않아도 푼 양이 예산에 잡힌다`() {
        val f = ArchiveSamples.sevenZ(File(dir, "budget.7z"), count = 8, bytesEach = 64 * 1024)
        val budget = EntryBudget(ParseLimits.DEFAULT)
        Archives.open(FileDocumentSource(f), ParseLimits.DEFAULT, budget).use { r ->
            r.extractSequentially(Collector { false })
        }
        assertEquals(8L * 64 * 1024, budget.totalOutput, "건너뛴 바이트가 세어지지 않았다")
    }

    /** 상한을 넘기면 순차 추출도 끊긴다. 화면은 이것을 '너무 큽니다' 로 옮긴다. */
    @Test
    fun `상한을 넘기면 순차 추출이 끊긴다`() {
        val f = ArchiveSamples.zip(File(dir, "over.zip"), count = 6, bytesEach = 200 * 1024)
        val limits = ParseLimits.DEFAULT.copy(maxTotalOutput = 300L * 1024)
        assertFailsWith<ParseLimitExceededException> {
            open(f, limits).use { it.extractSequentially(Collector()) }
        }
    }

    // ---- 수정시각 --------------------------------------------------------------

    @Test
    fun `수정시각을 읽어 온다`() {
        val zip = ArchiveSamples.zip(File(dir, "t.zip"), count = 3, bytesEach = 64)
        open(zip).use { r ->
            assertTrue(r.entries.all { it.lastModified > 0 }, "ZIP 은 수정시각이 있어야 한다")
        }
        val sevenZ = ArchiveSamples.sevenZ(File(dir, "t.7z"), count = 3, bytesEach = 64)
        open(sevenZ).use { r ->
            // commons-compress 가 7z 에 시각을 안 적을 수 있다. **모르면 0 이어야 한다** —
            // 지어낸 값이 들어오면 푼 파일의 시각이 거짓이 된다.
            assertTrue(r.entries.all { it.lastModified >= 0 })
        }
    }

    /** 이름 없는 엔트리가 있어도 번호와 실제 위치가 어긋나지 않는다(7z 색인 회귀). */
    @Test
    fun `7z 의 번호는 물리적 위치다`() {
        val f = ArchiveSamples.sevenZ(File(dir, "pos.7z"), count = 5, bytesEach = 128)
        open(f).use { r ->
            assertEquals(listOf(0, 1, 2, 3, 4), r.entries.map { it.index })
            for (e in r.entries) {
                assertContentEquals(
                    ArchiveSamples.payload(e.index, 128),
                    r.open(e).use { it.readBytes() },
                    "${e.index} 번",
                )
            }
        }
    }

    @Test
    fun `빈 아카이브도 조용히 끝난다`() {
        val f = ArchiveSamples.zip(File(dir, "empty.zip"), count = 0, bytesEach = 0)
        val c = Collector()
        open(f).use { it.extractSequentially(c) }
        assertTrue(c.got.isEmpty())
        assertNull(c.failures[0])
    }
}
