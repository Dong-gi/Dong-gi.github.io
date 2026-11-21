package io.github.donggi.iroiroviewer.io

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 남의 제공자에 막히지 않기([BlockingReads]) — 응답하지 않는 스트림·호출을 흉내 내, 기다림이 상한과 취소로 끊기고 파일 작업 큐가 놓이는지 본다.
 */
class BlockingReadsTest {

    /** `read` 가 영영 돌아오지 않는 스트림. 닫으면 풀린다(제공자의 파이프처럼). */
    private class StuckStream : InputStream() {
        val closed = CountDownLatch(1)
        override fun read(): Int = read(ByteArray(1), 0, 1)
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            closed.await()
            throw IOException("closed")
        }
        override fun close() = closed.countDown()
    }

    @Test
    fun `바이트가 오지 않으면 상한에서 null 을 주고 닫으면 스트림이 닫힌다`() = runBlocking {
        val stream = StuckStream()
        val pump = BlockingReads.Pump(stream).start()
        val started = System.nanoTime()
        assertNull(pump.next(150))
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 5_000)
        pump.close()
        assertTrue(stream.closed.await(5, TimeUnit.SECONDS), "닫기가 스트림에 닿지 않았다")
    }

    @Test
    fun `취소되면 기다림을 곧바로 버린다`() = runBlocking {
        val stream = StuckStream()
        BlockingReads.Pump(stream).start().use { pump ->
            val waiting = async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) { pump.next(60_000) }
            Thread.sleep(50)
            waiting.cancel()
            // 60초를 기다리지 않고 끝난다 — 큐가 놓인다.
            withTimeout(5_000) { waiting.join() }
            assertTrue(waiting.isCancelled)
        }
    }

    @Test
    fun `여느 스트림은 조각을 차례로 주고 끝을 알린다`() = runBlocking {
        val data = ByteArray(200_000) { (it % 251).toByte() }
        val out = java.io.ByteArrayOutputStream()
        BlockingReads.Pump(ByteArrayInputStream(data), chunkBytes = 4096).start().use { pump ->
            while (true) {
                when (val item = pump.next(5_000)) {
                    is BlockingReads.Pump.Item.Chunk -> out.write(item.bytes, 0, item.length)
                    is BlockingReads.Pump.Item.End -> break
                    else -> error("뜻밖의 것: $item")
                }
            }
        }
        assertTrue(data.contentEquals(out.toByteArray()))
    }

    @Test
    fun `읽다 던진 것은 실패로 온다`() = runBlocking {
        val failing = object : InputStream() {
            override fun read(): Int = throw IOException("provider died")
        }
        BlockingReads.Pump(failing).start().use { pump ->
            assertIs<BlockingReads.Pump.Item.Failed>(pump.next(5_000))
        }
        Unit
    }

    private fun awaitAbandoned(count: Int) {
        val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (BlockingReads.abandonedCount() != count && System.nanoTime() < until) Thread.sleep(10)
        assertEquals(count, BlockingReads.abandonedCount())
    }

    /** 막힌 제공자가 스레드를 끝없이 쌓지 않는다 — 버린 스레드가 상한이면 새 호출을 시작하지 않고, 풀리면 다시 부른다. */
    @Test
    fun `버린 스레드가 상한이면 새 호출을 시작하지 않는다`() = runBlocking {
        awaitAbandoned(0)
        val release = CountDownLatch(1)
        var timeouts = 0
        repeat(BlockingReads.MAX_ABANDONED) {
            assertNull(BlockingReads.call(20, { timeouts++ }) { release.await() })
        }
        assertTrue(BlockingReads.saturated())
        var ran = false
        assertNull(BlockingReads.call(5_000, { timeouts++ }) { ran = true })
        assertTrue(!ran, "상한인데 새 호출을 시작했다")
        assertEquals(BlockingReads.MAX_ABANDONED + 1, timeouts)
        release.countDown()
        awaitAbandoned(0)
        assertEquals(3, BlockingReads.call(5_000) { 3 })
    }

    @Test
    fun `답하지 않는 호출은 상한에서 null 이고 늦게 온 스트림은 닫힌다`() = runBlocking {
        val release = CountDownLatch(1)
        val late = object : Closeable {
            val closed = CountDownLatch(1)
            override fun close() = closed.countDown()
        }
        assertNull(BlockingReads.call(100) { release.await(); late })
        release.countDown()
        assertTrue(late.closed.await(5, TimeUnit.SECONDS), "늦게 온 값이 닫히지 않았다")
        // 제때 오면 그대로, 던지면 그대로 던진다.
        assertEquals(7, BlockingReads.call(5_000) { 7 })
        assertFailsWith<IllegalStateException> { BlockingReads.call(5_000) { error("boom") } }
        Unit
    }
}
