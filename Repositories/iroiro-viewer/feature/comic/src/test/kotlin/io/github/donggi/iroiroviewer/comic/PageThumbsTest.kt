package io.github.donggi.iroiroviewer.comic

import io.github.donggi.iroiroviewer.format.archive.ComicPage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.apache.commons.compress.archivers.sevenz.SevenZMethodConfiguration
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import java.io.File
import java.util.Collections
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 쪽 목록의 썸네일(14단계) — **무엇을 받고, 얼마나 들고, 언제 멈추는가.**
 *
 * 디코딩은 안드로이드의 일이라 여기서는 바이트를 그대로 '썸네일' 로 쓴다(크기 = 바이트 수). 그래서 받은 것이 원본과
 * 같은지까지 단언할 수 있다 — solid 에서 엔트리 번호와 쪽 번호가 어긋나면 다른 쪽의 그림이 칸에 뜬다.
 *
 * 실제 스레드에서 돈다(`runBlocking`) — solid 의 훑기가 `runInterruptible` 로 해제 스레드를 쓰고, 취소가 인터럽트로
 * 닿는지를 보려면 가상 시간으로는 안 된다.
 */
class PageThumbsTest {

    private val tmp: File = File(System.getProperty("java.io.tmpdir"), "iroiro-thumbs-${System.nanoTime()}").apply { mkdirs() }

    @AfterTest
    fun cleanup() {
        tmp.deleteRecursively()
    }

    private fun body(tag: String, size: Int = 64): ByteArray = ByteArray(size) { i -> (tag[i % tag.length].code + i).toByte() }

    private fun writeZip(name: String, entries: List<Pair<String, ByteArray>>): File {
        val f = File(tmp, name)
        ZipArchiveOutputStream(f).use { out ->
            for ((n, b) in entries) {
                val e = ZipArchiveEntry(n)
                e.size = b.size.toLong()
                out.putArchiveEntry(e)
                out.write(b)
                out.closeArchiveEntry()
            }
            out.finish()
        }
        return f
    }

    private fun writeSevenZ(name: String, entries: List<Pair<String, ByteArray>>): File {
        val f = File(tmp, name)
        SevenZOutputFile(f).use { out ->
            out.setContentMethods(listOf(SevenZMethodConfiguration(SevenZMethod.LZMA2)))
            for ((n, b) in entries) {
                val e = SevenZArchiveEntry()
                e.name = n
                e.size = b.size.toLong()
                e.setHasStream(true)
                out.putArchiveEntry(e)
                out.write(b)
                out.closeArchiveEntry()
            }
            out.finish()
        }
        return f
    }

    private suspend fun open(file: File): ComicSource {
        val r = ComicOpen.open(file.path, 1L shl 20)
        assertTrue(r is ComicOpen.Result.Ready, "열려야 한다: $r")
        return r.source
    }

    private fun thumbs(pageCount: Int, cap: Long = 1L shl 30) =
        PageThumbs<ByteArray>(pageCount, cap) { it.size.toLong() }

    /** 원하는 쪽이 모두 들어올 때까지 기다린다(받은 것이든 실패든). */
    private suspend fun awaitAll(t: PageThumbs<ByteArray>, range: IntRange) = withTimeout(20_000) {
        while (range.any { t.get(it) == null && !t.isFailed(it) }) delay(10)
    }

    /** 쪽을 몇 번 읽었는지 세는 무작위 접근 소스. */
    private class CountingSource(count: Int, private val broken: Set<Int> = emptySet()) : ComicSource {
        override val pages = (0 until count).map { ComicPage(ordinal = it, entryIndex = it, name = "$it.png", declaredSize = 8) }
        override val solid = false
        val reads: MutableList<Int> = Collections.synchronizedList(ArrayList())
        override suspend fun bytes(ordinal: Int): ByteArray? {
            reads += ordinal
            return if (ordinal in broken) null else ByteArray(8) { ordinal.toByte() }
        }

        override fun close() = Unit
    }

    // ---- 무작위 접근 ----------------------------------------------------------------------------

    /** 300쪽짜리를 전부 줄이지 않는다 — **보이는 범위만** 읽는다. */
    @Test
    fun `무작위 접근 소스는 원하는 범위만 읽는다`() = runBlocking {
        val src = CountingSource(300)
        val t = thumbs(300)
        t.setWanted(100..119)
        val job = launch(Dispatchers.Default) { t.run(src::scan) { it } }
        awaitAll(t, 100..119)
        job.cancelAndJoin()
        assertEquals((100..119).toSet(), src.reads.toSet())
        assertEquals(20, src.reads.size, "같은 쪽을 두 번 읽었다: ${src.reads}")
        assertNull(t.get(99))
        assertContentEquals(ByteArray(8) { 105.toByte() }, t.get(105))
    }

    @Test
    fun `zip 에서 받은 썸네일이 그 쪽의 바이트다`() = runBlocking {
        val entries = (1..12).map { "%02d.png".format(it) to body("z$it") }.reversed() // 저장 순서를 뒤집어 둔다
        val src = open(writeZip("grid.cbz", entries))
        src.use {
            val t = thumbs(src.pages.size)
            t.setWanted(0..11)
            val job = launch(Dispatchers.Default) { t.run(src::scan) { it } }
            awaitAll(t, 0..11)
            job.cancelAndJoin()
            for (i in 0 until 12) assertContentEquals(body("z${i + 1}"), t.get(i), "쪽 $i")
        }
    }

    /**
     * **보이는 칸을 먼저** 읽는다 — 위에서 아래로, 그다음 아래 여유, 마지막으로 위 여유(가까운 것부터). ZIP 의 쪽 하나는
     * 수백 ms 가 들 수 있어, 위 여유부터 읽으면 보이는 칸이 한참 비어 있다.
     */
    @Test
    fun `보이는 칸을 먼저 읽고 여유는 나중에 읽는다`() = runBlocking {
        val src = CountingSource(100)
        val t = thumbs(100)
        t.setWanted(10..40, shown = 20..30)
        val job = launch(Dispatchers.Default) { t.run(src::scan) { it } }
        awaitAll(t, 10..40)
        job.cancelAndJoin()
        assertEquals((20..30).toList() + (31..40).toList() + (19 downTo 10).toList(), src.reads.toList())
    }

    /** 읽지 못한 쪽은 실패로 적고 **다시 청하지 않는다** — 되풀이하면 격자가 떠 있는 내내 같은 쪽을 읽는다. */
    @Test
    fun `읽지 못한 쪽은 실패로 적고 되풀이하지 않는다`() = runBlocking {
        val src = CountingSource(10, broken = setOf(3))
        val t = thumbs(10)
        t.setWanted(0..9)
        val job = launch(Dispatchers.Default) { t.run(src::scan) { it } }
        awaitAll(t, 0..9)
        delay(200)
        job.cancelAndJoin()
        assertTrue(t.isFailed(3))
        assertEquals(1, src.reads.count { it == 3 }, "실패한 쪽을 되풀이해 읽었다")
    }

    /** 범위를 옮기면 새 범위를 읽는다. 옛 범위는 다시 읽지 않는다(이미 들었다). */
    @Test
    fun `범위를 옮기면 새로 보이는 쪽만 읽는다`() = runBlocking {
        val src = CountingSource(100)
        val t = thumbs(100)
        t.setWanted(0..9)
        val job = launch(Dispatchers.Default) { t.run(src::scan) { it } }
        awaitAll(t, 0..9)
        t.setWanted(5..14)
        awaitAll(t, 5..14)
        job.cancelAndJoin()
        assertEquals((0..14).toSet(), src.reads.toSet())
        assertEquals(15, src.reads.size)
    }

    // ---- 메모리 ---------------------------------------------------------------------------------

    /** 상한을 넘지 않고, 넘치면 **원하는 범위의 가운데에서 먼 것**부터 놓는다. */
    @Test
    fun `상한을 넘지 않고 먼 것부터 놓는다`() {
        val t = thumbs(100, cap = 80)
        t.setWanted(40..49)
        for (o in 0 until 10) t.deliver(o, ByteArray(10)) // 범위 밖(멀다)
        for (o in 40..49) t.deliver(o, ByteArray(10))
        assertTrue(t.bytesHeld <= 80, "상한을 넘었다: ${t.bytesHeld}")
        for (o in 0 until 10) assertNull(t.get(o), "범위 밖의 $o 가 먼저 나가야 한다")
        // 범위 안에서도 가운데(44.5)에서 먼 것이 나간다 — 40·49 가 먼저, 44·45 는 남는다.
        assertNotNull(t.get(44))
        assertNotNull(t.get(45))
    }

    /**
     * **상한이 범위보다 작아도 되풀이하지 않는다.** 받고 → 놓고 → 다시 받는 고리가 돌면 격자가 떠 있는 내내 쪽을
     * 푼다. 한 범위 동안 쪽마다 한 번만 읽는다.
     */
    @Test
    fun `상한이 범위보다 작아도 같은 쪽을 되풀이해 읽지 않는다`() = runBlocking {
        val src = CountingSource(50)
        val t = PageThumbs<ByteArray>(50, capBytes = 8L * 5) { it.size.toLong() }
        t.setWanted(0..19)
        val job = launch(Dispatchers.Default) { t.run(src::scan) { it } }
        withTimeout(20_000) { while (src.reads.size < 20) delay(10) }
        delay(300)
        job.cancelAndJoin()
        assertEquals(20, src.reads.size, "되풀이했다: ${src.reads}")
        assertTrue(t.bytesHeld <= 40)
    }

    /** 한 장이 상한보다 크면 들지 않는다(칸에 번호만 남는다). 실패로 치지는 않는다 — 쪽은 멀쩡하다. */
    @Test
    fun `상한보다 큰 한 장은 들지 않는다`() {
        val t = thumbs(10, cap = 5)
        t.setWanted(0..9)
        t.deliver(2, ByteArray(6))
        assertNull(t.get(2))
        assertEquals(false, t.isFailed(2))
        assertEquals(false, t.wants(2), "이번 범위 동안은 다시 청하지 않는다")
        t.setWanted(1..9)
        assertEquals(true, t.wants(2), "범위가 바뀌면 다시 청한다")
    }

    /** 힙이 작아 몫이 0 이면 아무것도 읽지 않는다. */
    @Test
    fun `몫이 0 이면 아무것도 읽지 않는다`() = runBlocking {
        val src = CountingSource(10)
        val t = thumbs(10, cap = 0)
        t.setWanted(0..9)
        withTimeout(5_000) { t.run(src::scan) { it } } // 곧바로 돌아온다
        assertTrue(src.reads.isEmpty())
    }

    @Test
    fun `닫으면 든 것을 전부 놓는다`() {
        val t = thumbs(10)
        t.setWanted(0..9)
        t.deliver(1, ByteArray(10))
        t.clear()
        assertEquals(0L, t.bytesHeld)
        assertNull(t.get(1))
    }

    // ---- solid --------------------------------------------------------------------------------

    /**
     * **이 절의 이유다.** solid 에서 쪽마다 [ComicSource.bytes] 를 부르면 창 하나에 패스 하나다. 격자는 **한 번 훑어**
     * 원하는 쪽을 모두 받아야 하고, 창(읽던 자리)은 건드리지 않는다.
     */
    @Test
    fun `solid 는 한 번 훑어 원하는 쪽을 모두 받고 창을 건드리지 않는다`() = runBlocking {
        // 저장 순서를 섞는다 — 쪽 번호(이름 차례)와 아카이브의 차례가 달라도 제 쪽의 바이트가 가야 한다.
        val entries = (1..30).map { "%02d.png".format(it) to body("s$it", 2048) }.shuffled(java.util.Random(7))
        val src = open(writeSevenZ("grid.cb7", entries)) as SolidComicSource
        src.use {
            val t = thumbs(src.pages.size)
            t.setWanted(10..19)
            val job = launch(Dispatchers.Default) { t.run(src::scan) { it } }
            awaitAll(t, 10..19)
            job.cancelAndJoin()
            assertEquals(1, src.scanCount, "훑기가 한 번이어야 한다")
            assertEquals(0, src.passCount, "창의 패스가 돌았다")
            for (i in 10..19) assertContentEquals(body("s${i + 1}", 2048), t.get(i), "쪽 $i")
            assertNull(t.get(9))
            assertNull(t.get(20))
        }
    }

    /**
     * **원하는 쪽을 다 받으면 그 자리에서 멈춘다.** 뒤의 엔트리를 마저 푸는 것은 버릴 바이트다. 그만둔 뒤로는
     * [ScanDemand.wants] 를 부를 일이 없다 — 부른 횟수로 멈춘 자리를 잰다.
     */
    @Test
    fun `solid 훑기는 원하는 쪽을 다 받으면 멈춘다`() = runBlocking {
        val entries = (1..60).map { "%02d.png".format(it) to body("t$it", 512) }
        val src = open(writeSevenZ("stop.cb7", entries))
        src.use {
            val asked = Collections.synchronizedList(ArrayList<Int>())
            val got = Collections.synchronizedList(ArrayList<Int>())
            val demand = object : ScanDemand {
                override fun next(): Int? = null
                override fun wants(ordinal: Int): Boolean {
                    asked += ordinal
                    return ordinal in 0..4 && ordinal !in got
                }

                override fun wantsAnyExcept(passed: (Int) -> Boolean): Boolean =
                    (0..4).any { it !in got && !passed(it) }
            }
            src.scan(demand) { o, b ->
                assertNotNull(b)
                got += o
            }
            assertEquals((0..4).toList(), got.sorted())
            // 마지막으로 원하던 쪽(4)을 받은 그 자리에서 멈춘다 — 그 뒤의 엔트리(5)를 열어 보지도 않는다.
            assertEquals(5, asked.size, "멈추지 않고 뒤를 계속 물었다: ${asked.size}번")
        }
    }

    /**
     * 훑는 도중 사용자가 격자를 **뒤로** 밀면, 원하는 쪽이 이미 지나간 자리에 있다. 그 훑기가 끝나면 **한 번 더**
     * 훑어 받는다.
     */
    @Test
    fun `지나간 자리를 원하게 되면 한 번 더 훑는다`() = runBlocking {
        val entries = (1..40).map { "%02d.png".format(it) to body("u$it", 256) }
        val src = open(writeSevenZ("back.cb7", entries)) as SolidComicSource
        src.use {
            val t = thumbs(src.pages.size)
            t.setWanted(30..39)
            val moved = CompletableDeferred<Unit>()
            val job = launch(Dispatchers.Default) {
                t.run(src::scan) { bytes ->
                    // 첫 썸네일을 받는 순간 사용자가 앞쪽으로 되돌아갔다.
                    if (!moved.isCompleted) {
                        t.setWanted(0..9)
                        moved.complete(Unit)
                    }
                    bytes
                }
            }
            awaitAll(t, 0..9)
            job.cancelAndJoin()
            assertTrue(src.scanCount >= 2, "되돌아간 자리를 받으려면 훑기가 한 번 더 돌아야 한다: ${src.scanCount}")
            for (i in 0..9) assertContentEquals(body("u${i + 1}", 256), t.get(i), "쪽 $i")
        }
    }

    /**
     * **격자를 닫으면 훑기가 멈추고 잠금이 풀린다.** 훑기는 창과 같은 잠금을 잡으므로(7z 사전 두 벌을 막는다), 취소가
     * 닿지 않으면 격자를 닫은 뒤에도 읽던 쪽이 뜨지 않는다.
     */
    @Test
    fun `격자를 닫으면 훑기가 멈추고 쪽을 다시 읽을 수 있다`() = runBlocking {
        val entries = (1..200).map { "%03d.png".format(it) to body("v$it", 8192) }
        val src = open(writeSevenZ("cancel.cb7", entries))
        src.use {
            val t = thumbs(src.pages.size)
            t.setWanted(180..199)
            val started = CompletableDeferred<Unit>()
            val job = launch(Dispatchers.Default) {
                t.run({ demand, onPage ->
                    started.complete(Unit)
                    src.scan(demand, onPage)
                }) { bytes ->
                    Thread.sleep(50) // 느린 디코딩
                    bytes
                }
            }
            started.await()
            delay(30)
            withTimeout(10_000) { job.cancelAndJoin() }
            // 잠금이 풀렸다면 곧바로 읽힌다.
            val page = withTimeout(10_000) { withContext(Dispatchers.Default) { src.bytes(0) } }
            assertContentEquals(body("v1", 8192), page)
        }
    }

    /** 깨진 아카이브라 훑기가 아무것도 못 주면, 같은 훑기를 끝없이 되풀이하지 않고 실패로 적는다. */
    @Test
    fun `아무것도 주지 못하는 훑기는 되풀이하지 않는다`() = runBlocking {
        val scans = java.util.concurrent.atomic.AtomicInteger()
        val t = thumbs(10)
        t.setWanted(0..4)
        val job = launch(Dispatchers.Default) {
            t.run({ _, _ -> scans.incrementAndGet() }) { it }
        }
        withTimeout(10_000) { while ((0..4).any { !t.isFailed(it) }) delay(10) }
        delay(200)
        job.cancelAndJoin()
        assertEquals(1, scans.get(), "같은 훑기를 되풀이했다")
    }

    // ---- 크기 ---------------------------------------------------------------------------------

    @Test
    fun `썸네일은 칸 안에 비를 지켜 들고 키우지 않는다`() {
        assertContentEquals(intArrayOf(200, 300), thumbSize(1400, 2100, 200, 400))
        assertContentEquals(intArrayOf(200, 100), thumbSize(3000, 1500, 200, 400))
        assertContentEquals(intArrayOf(50, 80), thumbSize(50, 80, 200, 400), "작은 그림은 그대로")
        assertNull(thumbSize(0, 10, 200, 300))
    }
}
