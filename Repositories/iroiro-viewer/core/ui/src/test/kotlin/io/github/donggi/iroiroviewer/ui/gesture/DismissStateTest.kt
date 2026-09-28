package io.github.donggi.iroiroviewer.ui.gesture

import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 손을 뗀 뒤의 애니메이션 — **끊기면 제자리로, 빼앗기면 그 자리에서.**
 *
 * 끌어 닫기의 상태는 화면에 하나이고 모든 장이 그것을 그린다. 되돌리는 애니메이션은 끌던 장의 인식기
 * 스코프에서 도는데, 그 장이 컴포지션에서 빠지면(페이저를 빠르게 두 장 넘겼다) 스코프가 취소된다. 예전에는
 * 그 자리에서 멈춰 **모든 장이 반쯤 내려가고 옅어진 채** 남았고, 닫히던 중이었으면 새 제스처도 받지 않았다.
 * 시험은 손으로 넘기는 시계 위에서 돈다 — 시각에 기대지 않는다.
 */
class DismissStateTest {

    /** 한 번 부를 때마다 한 프레임(16 ms)을 넘기는 시계. `Animatable` 이 요구한다(시험에는 Choreographer 가 없다). */
    private class StepClock : MonotonicFrameClock {
        private var now = 0L
        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            yield()
            now += 16_000_000L
            return onFrame(now)
        }
    }

    private fun dragged(by: Float) = DismissState().apply {
        viewportHeight = 2400f
        startDrag()
        dragBy(by)
    }

    private suspend fun frames(n: Int) = repeat(n) { yield() }

    @Test
    fun 놓으면_제자리로_돌아간다() = runBlocking(StepClock()) {
        val state = dragged(300f)
        val page = CoroutineScope(coroutineContext + Job())
        state.settleIn(page, dismiss = false) {}
        frames(400)
        assertEquals(0f, state.offsetY)
        assertFalse(state.dragging)
        page.cancel()
    }

    @Test
    fun 끌던_장이_빠져_되돌리기가_끊기면_제자리로_돌아간다() = runBlocking(StepClock()) {
        val state = dragged(300f)
        val page = CoroutineScope(coroutineContext + Job())
        state.settleIn(page, dismiss = false) {}
        frames(3)
        assertTrue(state.offsetY > 0f, "아직 되돌아가는 중이어야 이 시험이 뜻을 가진다")
        page.cancel() // 끌던 장이 컴포지션에서 빠졌다
        page.coroutineContext.job.join()
        assertEquals(0f, state.offsetY, "반쯤 내려간 채 남으면 모든 장이 그렇게 그려진다")
        assertEquals(1f, state.contentAlpha)
    }

    @Test
    fun 닫히던_중에_끊기면_다시_제스처를_받는다() = runBlocking(StepClock()) {
        val state = dragged(600f)
        val page = CoroutineScope(coroutineContext + Job())
        var closed = false
        state.settleIn(page, dismiss = true) { closed = true }
        frames(3)
        assertTrue(state.closing)
        page.cancel()
        page.coroutineContext.job.join()
        assertFalse(closed, "끊긴 것은 닫은 것이 아니다")
        assertFalse(state.closing, "닫히는 중으로 남으면 끌어 닫기가 영영 받지 않는다")
        assertEquals(0f, state.offsetY)
    }

    @Test
    fun 되돌리는_중에_다시_잡으면_그_자리에서_이어_끈다() = runBlocking(StepClock()) {
        // 다른 장의 인식기가 잡아도 같다 — 애니메이션을 상태가 들기 때문이다.
        val state = dragged(300f)
        val page = CoroutineScope(coroutineContext + Job())
        state.settleIn(page, dismiss = false) {}
        frames(3)
        val caught = state.offsetY
        assertTrue(caught > 0f)
        state.startDrag()
        frames(50)
        assertEquals(caught, state.offsetY, "빼앗긴 애니메이션이 계속 쓰면 손가락과 번갈아 써서 떨린다")
        assertTrue(state.dragging, "빼앗긴 것을 끊긴 것으로 보고 되돌리면 끄던 손가락을 놓친다")
        page.cancel()
    }

    @Test
    fun 문턱을_넘겨_놓으면_내보낸_뒤에_닫는다() = runBlocking(StepClock()) {
        val state = dragged(600f)
        val page = CoroutineScope(coroutineContext + Job())
        var closedAt = -1f
        state.settleIn(page, dismiss = true) { closedAt = state.offsetY }
        frames(400)
        assertEquals(2400f, closedAt, "화면 밖으로 내보낸 뒤에 닫는다")
        assertTrue(state.closing)
        page.cancel()
    }
}
