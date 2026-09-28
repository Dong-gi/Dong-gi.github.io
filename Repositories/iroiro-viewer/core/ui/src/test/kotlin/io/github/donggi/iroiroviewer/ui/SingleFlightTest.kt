package io.github.donggi.iroiroviewer.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * 썸네일 요청 병합 — **한 칸의 취소가 같은 사진을 기다리던 칸에 빈 칸을 건네지 않는가.**
 *
 * 예전 병합은 일하던 요청이 취소되면 기다리던 요청에 null 을 줬다. 격자에서는 스크롤로 벗어났다
 * 곧바로 돌아온 칸이 방금 만든 썸네일 대신 종류 배지를 그리는 것으로 보인다. 시험은 한 스레드
 * (`runBlocking`) 위에서 문을 손으로 여닫아 차례를 정한다 — 시각에 기대지 않는다.
 */
class SingleFlightTest {

    @Test
    fun 겹친_요청은_한_번만_일한다() = runBlocking {
        val flight = SingleFlight<String>()
        val gate = CompletableDeferred<Unit>()
        var runs = 0
        val a = async { flight.run("k") { runs++; gate.await(); "그림" } }
        val b = async { flight.run("k") { runs++; "다른 그림" } }
        yield()
        gate.complete(Unit)
        assertEquals("그림", a.await())
        assertEquals("그림", b.await(), "기다린 쪽은 일한 쪽의 결과를 받는다")
        assertEquals(1, runs)
    }

    @Test
    fun 일하던_쪽이_취소되면_기다리던_쪽이_이어받는다() = runBlocking {
        val flight = SingleFlight<String>()
        val never = CompletableDeferred<Unit>()
        var runs = 0
        val owner = async { flight.run("k") { runs++; never.await(); "못 받는 그림" } }
        yield() // owner 가 표에 이름을 올리고 문 앞에서 멈춘다
        val waiter = async { flight.run("k") { runs++; "이어받은 그림" } }
        yield() // waiter 가 owner 를 기다리기 시작한다
        owner.cancel()
        assertFailsWith<CancellationException> { owner.await() }
        assertEquals("이어받은 그림", waiter.await(), "취소는 기다리던 쪽의 사정이 아니다 — null 을 받으면 안 된다")
        assertEquals(2, runs)
        assertFalse(flight.isRunning("k"))
    }

    @Test
    fun 일이_던지면_던진_것은_부른_쪽에만_간다() = runBlocking {
        val flight = SingleFlight<String>()
        val gate = CompletableDeferred<Unit>()
        // 자식 안에서 받는다 — 실패한 `async` 는 부모(runBlocking)까지 취소한다.
        val owner = async {
            try {
                flight.run("k") {
                    gate.await()
                    throw IllegalStateException("디코더가 죽었다")
                }
                null
            } catch (e: IllegalStateException) {
                e
            }
        }
        yield()
        val waiter = async { flight.run("k") { "다시 한 결과" } }
        yield()
        gate.complete(Unit)
        assertIs<IllegalStateException>(owner.await())
        assertEquals("다시 한 결과", waiter.await())
    }

    @Test
    fun 끝나면_표가_비고_다음_요청은_새로_일한다() = runBlocking {
        val flight = SingleFlight<String>()
        assertNull(flight.run("k") { null }, "못 만든 것(null)도 결과다")
        assertFalse(flight.isRunning("k"))
        var runs = 0
        flight.run("k") { runs++; "그림" }
        flight.run("k") { runs++; "그림" }
        assertEquals(2, runs, "병합은 겹친 요청만 합친다 — 결과를 기억하는 것은 메모리 캐시의 일이다")
    }

    @Test
    fun 취소된_채_홀로_끝나도_표에_남지_않는다() = runBlocking {
        // 표에 끝난 약속이 남으면 그 키는 이 세션 내내 곧바로 null 을 받는다.
        val flight = SingleFlight<String>()
        val never = CompletableDeferred<Unit>()
        val owner = async(start = CoroutineStart.UNDISPATCHED) { flight.run("k") { never.await(); "x" } }
        owner.cancel()
        assertFailsWith<CancellationException> { owner.await() }
        assertFalse(flight.isRunning("k"))
        assertEquals("새 그림", flight.run("k") { "새 그림" })
    }

    @Test
    fun 키가_다르면_따로_일한다() = runBlocking {
        val flight = SingleFlight<String>()
        val gate = CompletableDeferred<Unit>()
        val a = async { flight.run("a") { gate.await(); "가" } }
        yield()
        // a 가 문 앞에 서 있어도 b 는 기다리지 않는다.
        assertEquals("나", flight.run("b") { "나" })
        gate.complete(Unit)
        assertEquals("가", a.await())
    }
}
