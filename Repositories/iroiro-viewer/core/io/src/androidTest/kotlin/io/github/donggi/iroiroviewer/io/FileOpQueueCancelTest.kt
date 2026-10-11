package io.github.donggi.iroiroviewer.io

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Collections
import java.util.UUID

/**
 * 파일 작업 큐의 **신원으로 취소하기**. 재생 화면이 넣은 구간 저장이 앞 작업(긴 복사)에 밀려 줄 서 있을 때
 * 취소하면 곧바로 '취소' 를 받고, 앞 작업이 끝나면 큐가 Idle 로 돌아와야 한다. 예전에는 줄 선 요청을 건너뛰는 길이 정리를 빼먹어
 * 앞 작업의 `Running` 과 알림이 영영 남았고, 아무것도 돌지 않을 때 누른 '취소' 는 다음에 시킨 작업을 시작하자마자 끊었다.
 *
 * 앞 작업은 시간에 기대지 않는다 — 가짜 뜨는 쪽([CropSupport.renderer])을 꽂아 잘라 저장 하나가 [gate] 가 열릴 때까지 멈춰 있게
 * 한다. 그것은 아무 파일도 쓰지 않고 '이 형식은 저장할 수 없다' 로 끝난다.
 */
@RunWith(AndroidJUnit4::class)
class FileOpQueueCancelTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var work: File
    private var previousRenderer: CropSupport.Renderer? = null

    /** 앞 작업이 뜨는 자리에 들어섰다. */
    private val entered = CompletableDeferred<Unit>()

    /** 열리면 앞 작업이 끝난다. */
    private val gate = CompletableDeferred<Unit>()

    @Before
    fun setUp() = runBlocking {
        val root = context.getExternalFilesDirs(null).filterNotNull().first()
        work = File(root, "queuetest-${System.nanoTime()}").apply { mkdirs() }
        waitIdle()
        previousRenderer = CropSupport.renderer
        CropSupport.renderer = CropSupport.Renderer {
            entered.complete(Unit)
            gate.await()
            CropSupport.Rendered.Unsupported
        }
    }

    @After
    fun tearDown() = runBlocking {
        // 시험이 중간에 깨졌어도 붙잡힌 작업을 풀어 다음 시험의 큐를 비운다.
        gate.complete(Unit)
        waitIdle()
        CropSupport.renderer = previousRenderer
        work.deleteRecursively()
        Unit
    }

    /** 결과를 모으는 자리. 큐의 작업 스레드에서 불린다. */
    private class Collect : FileOpManager.ResultSink {
        val got: MutableList<FileOpManager.Finished> = Collections.synchronizedList(ArrayList())

        override fun offer(finished: FileOpManager.Finished): Boolean {
            got += finished
            return true
        }
    }

    private suspend fun waitFor(what: () -> Boolean) {
        withTimeout(60_000) {
            while (!what()) delay(20)
        }
    }

    private suspend fun waitIdle() = waitFor { FileOpManager.state.value is FileOpManager.State.Idle }

    private fun running(): FileOpManager.State.Running? = FileOpManager.state.value as? FileOpManager.State.Running

    /** [gate] 가 열릴 때까지 멈춰 있는 앞 작업. 받는 자리가 없다 — 결과는 [FileOpManager.results] 로 간다. */
    private suspend fun startBlockingHead(): FileOpManager.Request.SaveCrop {
        val src = File(work, "head-${System.nanoTime()}.png").apply { writeBytes(byteArrayOf(1)) }
        val head = FileOpManager.Request.SaveCrop(UUID.randomUUID().toString(), src.absolutePath, 0, 0, 1, 1, 1, 1)
        FileOpManager.enqueue(context, head)
        withTimeout(20_000) { entered.await() }
        return head
    }

    /** 1초짜리 WAV 의 앞 0.8초 — 곧바로 끝나는 구간 저장. */
    private fun shortClip(sink: FileOpManager.ResultSink): FileOpManager.Request.SaveClip {
        val src = File(work, "short-${System.nanoTime()}.wav").apply { writeBytes(wav(8000, 8000)) }
        return FileOpManager.Request.SaveClip(UUID.randomUUID().toString(), src.absolutePath, 0, 800, audioOnly = false, sink = sink)
    }

    private fun clipsWritten(): List<String> = work.list().orEmpty().filter { it.startsWith("short-") && "_clip" in it }

    @Test
    fun 줄_선_요청을_신원으로_취소하면_곧바로_취소를_받고_큐는_Idle_로_돌아온다() = runBlocking {
        val head = startBlockingHead()
        val sink = Collect()
        val queued = shortClip(sink)
        FileOpManager.enqueue(context, queued)

        FileOpManager.cancel(queued.id)
        // 줄 서 있으니 워커를 기다리지 않고 그 자리에서 '취소' 가 온다(예전에는 앞 작업이 끝날 때까지 아무것도 오지 않았다).
        assertEquals(1, sink.got.size)
        assertEquals(queued.id, sink.got[0].id)
        assertTrue(sink.got[0].outcome.toString(), sink.got[0].outcome is FileOpEngine.Outcome.Cancelled)
        // 앞 작업은 그대로 돈다.
        assertEquals(head.id, running()?.id)

        gate.complete(Unit)
        // 앞 작업이 끝나고 거둔 칸을 건너뛰면 큐가 비었다 — Idle 로 돌아와야 한다(예전에는 앞 작업의 Running 이 남았다).
        waitIdle()
        assertTrue("거둔 요청이 돌았다: ${clipsWritten()}", clipsWritten().isEmpty())
        assertEquals("결과는 한 번뿐이다", 1, sink.got.size)
        Unit
    }

    @Test
    fun 도는_것이_없을_때의_지금_작업_취소는_다음_작업을_끊지_않는다() = runBlocking {
        waitIdle()
        FileOpManager.cancelCurrent()
        val sink = Collect()
        FileOpManager.enqueue(context, shortClip(sink))
        waitFor { sink.got.isNotEmpty() }
        assertTrue(sink.got[0].outcome.toString(), sink.got[0].outcome is FileOpEngine.Outcome.Done)
        assertEquals(1, clipsWritten().size)
        waitIdle()
        Unit
    }

    @Test
    fun 도는_작업을_신원으로_취소하면_그것만_끊기고_뒤의_요청은_돈다() = runBlocking {
        val head = startBlockingHead()
        val sink = Collect()
        FileOpManager.enqueue(context, shortClip(sink))

        // 다른 신원(이미 끝난 요청)으로 온 취소는 도는 작업에 닿지 않는다.
        FileOpManager.cancel("gone-${UUID.randomUUID()}")
        delay(300)
        assertEquals(head.id, running()?.id)

        FileOpManager.cancel(head.id)
        val headResult = withTimeout(20_000) { FileOpManager.results.first { it.id == head.id } }
        assertTrue(headResult.outcome.toString(), headResult.outcome is FileOpEngine.Outcome.Cancelled)
        // 뒤에 줄 선 요청은 끊기지 않았다.
        waitFor { sink.got.isNotEmpty() }
        assertTrue(sink.got[0].outcome.toString(), sink.got[0].outcome is FileOpEngine.Outcome.Done)
        waitIdle()
        Unit
    }

    @Test
    fun 전부_취소는_줄_선_요청에_취소를_주고_그_뒤에_넣은_것은_돈다() = runBlocking {
        startBlockingHead()
        val before = Collect()
        FileOpManager.enqueue(context, shortClip(before))

        FileOpManager.cancelAll()
        // 받는 자리가 있는 줄 선 요청은 '취소' 를 받는다 — 받지 않으면 그 화면이 '기다리는 중' 에 머문다.
        assertEquals(1, before.got.size)
        assertTrue(before.got[0].outcome.toString(), before.got[0].outcome is FileOpEngine.Outcome.Cancelled)
        waitIdle()
        assertTrue(clipsWritten().isEmpty())

        // '전부 취소' 뒤에 넣은 것은 돈다(세대가 넣은 때의 것이다).
        val after = Collect()
        FileOpManager.enqueue(context, shortClip(after))
        waitFor { after.got.isNotEmpty() }
        assertTrue(after.got[0].outcome.toString(), after.got[0].outcome is FileOpEngine.Outcome.Done)
        waitIdle()
        Unit
    }

    /**
     * 거두기(`withdraw`)는 **줄 선 요청만** 거둔다 — 도는 요청은 끊지 않고 거짓을 돌려준다. 편집기의 저장 반영이 기다리다 시간이 넘으면
     * 이것으로 거두고, 거두지 못하면(돌고 있다) 끝을 기다려 진짜 결과로 답한다(예전에는 '바쁘다' 고 답한 요청을 큐에 그대로 두어 나중에
     * 말없이 원본을 바꿨다).
     */
    @Test
    fun 거두기는_줄_선_요청만_거두고_도는_요청은_끊지_않는다() = runBlocking {
        val head = startBlockingHead()
        val sink = Collect()
        val queued = shortClip(sink)
        FileOpManager.enqueue(context, queued)

        assertFalse("도는 요청은 거두지 못한다", FileOpManager.withdraw(head.id))
        delay(300)
        assertEquals("도는 요청은 그대로 돈다", head.id, running()?.id)

        assertTrue(FileOpManager.withdraw(queued.id))
        // 줄 서 있으니 그 자리에서 '취소' 가 온다.
        assertEquals(1, sink.got.size)
        assertTrue(sink.got[0].outcome.toString(), sink.got[0].outcome is FileOpEngine.Outcome.Cancelled)
        assertFalse("두 번 거두지 않는다", FileOpManager.withdraw(queued.id))

        gate.complete(Unit)
        waitIdle()
        assertTrue("거둔 요청이 돌았다: ${clipsWritten()}", clipsWritten().isEmpty())
        assertEquals("결과는 한 번뿐이다", 1, sink.got.size)
        Unit
    }

    /** 16비트 모노 PCM WAV(무음). */
    private fun wav(rate: Int, frames: Int): ByteArray {
        val data = frames * 2
        val b = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + data).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
        b.put("data".toByteArray()).putInt(data)
        return b.array()
    }
}
