package io.github.donggi.iroirobrowser.browser

import io.github.donggi.iroirobrowser.browser.PullTracker.State
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PullTrackerTest {

    // 픽셀: 슬롭 10, '맨 위에서 밀림' 이 와야 하는 거리 40, 놓으면 새로 고치는 동그라미 거리 60, 끝 90.
    private fun tracker() = PullTracker(slop = 10f, window = 40f, trigger = 60f, max = 90f)

    @Test
    fun 맨_위에서_당기면_당김이_되고_충분히_당겨_놓으면_새로_고친다() {
        val t = tracker()
        t.down(100f, 100f, enabled = true)
        t.move(100f, 115f) // 슬롭을 넘었다
        assertTrue(t.overscrolledTop())
        assertEquals(State.PULLING, t.state)
        t.move(100f, 175f) // 손가락 60 → 동그라미 30
        assertEquals(30f, t.offset)
        assertFalse(t.ready)
        t.move(100f, 240f) // 손가락 125 → 62.5
        assertTrue(t.ready)
        assertTrue(t.release())
        assertEquals(State.IDLE, t.state)
    }

    @Test
    fun 덜_당기고_놓으면_새로_고치지_않는다() {
        val t = tracker()
        t.down(0f, 0f, enabled = true)
        t.move(0f, 20f)
        assertTrue(t.overscrolledTop())
        t.move(0f, 80f)
        assertFalse(t.release())
    }

    @Test
    fun 동그라미는_끝까지만_내려온다() {
        val t = tracker()
        t.down(0f, 0f, enabled = true)
        t.move(0f, 20f)
        t.overscrolledTop()
        t.move(0f, 1000f)
        assertEquals(90f, t.offset)
    }

    @Test
    fun 쪽이_맨_위가_아니면_당기지_않는다() {
        val t = tracker()
        t.down(0f, 0f, enabled = false)
        t.move(0f, 30f)
        assertFalse(t.overscrolledTop())
        assertEquals(State.IDLE, t.state)
    }

    @Test
    fun 슬롭을_넘기_전의_밀림은_기다린다() {
        val t = tracker()
        t.down(0f, 0f, enabled = true)
        t.move(0f, 5f)
        assertFalse(t.overscrolledTop())
        assertEquals(State.ARMED, t.state)
        t.move(0f, 14f)
        assertTrue(t.overscrolledTop())
    }

    @Test
    fun 쪽_안의_상자가_먼저_받았으면_그_몸짓은_쪽의_것이다() {
        // 맨 위의 알림이 슬롭을 넘은 뒤 40 안에 오지 않았다 — 쪽 안의 스크롤 상자가 첫 스크롤을 받았다(그 상자를 올리는 중).
        val t = tracker()
        t.down(0f, 0f, enabled = true)
        t.move(0f, 12f)
        t.move(0f, 60f)
        assertEquals(State.IDLE, t.state)
        assertFalse(t.overscrolledTop())
    }

    @Test
    fun 위로나_옆으로_밀면_쪽의_것이다() {
        val up = tracker()
        up.down(0f, 100f, enabled = true)
        up.move(0f, 85f)
        assertFalse(up.overscrolledTop())
        val side = tracker()
        side.down(0f, 0f, enabled = true)
        side.move(30f, 12f)
        assertFalse(side.overscrolledTop())
        assertEquals(State.IDLE, side.state)
    }

    @Test
    fun 취소하면_새로_고치지_않는다() {
        val t = tracker()
        t.down(0f, 0f, enabled = true)
        t.move(0f, 20f)
        t.overscrolledTop()
        t.move(0f, 400f)
        t.cancel()
        assertFalse(t.release())
        assertEquals(0f, t.offset)
    }
}
