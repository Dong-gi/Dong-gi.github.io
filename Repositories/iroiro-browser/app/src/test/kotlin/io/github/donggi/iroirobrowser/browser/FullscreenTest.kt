package io.github.donggi.iroirobrowser.browser

import io.github.donggi.iroirobrowser.browser.EdgeSwipe.State
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EdgeSwipeTest {

    // 픽셀: 슬롭 10, 가장자리 띠 50.
    private fun swipe() = EdgeSwipe(slop = 10f)

    @Test
    fun 띠_안에서_아래로_슬롭을_넘으면_한_번_막대를_부른다() {
        val s = swipe()
        s.down(100f, 10f, edge = 50f, enabled = true)
        assertEquals(State.WATCHING, s.state)
        assertFalse(s.move(100f, 20f)) // 슬롭과 같다 — 아직
        assertEquals(State.WATCHING, s.state)
        assertTrue(s.move(102f, 21f))
        assertEquals(State.TAKEN, s.state)
        assertFalse(s.move(102f, 200f)) // 그 뒤의 움직임은 다시 부르지 않는다(나타난 막대가 탭 목록을 열지 않게)
        s.end()
        assertEquals(State.IDLE, s.state)
    }

    @Test
    fun 띠의_아래_끝은_띠_밖이다() {
        val inside = swipe()
        inside.down(0f, 49.9f, edge = 50f, enabled = true)
        assertEquals(State.WATCHING, inside.state)
        val outside = swipe()
        outside.down(0f, 50f, edge = 50f, enabled = true)
        assertEquals(State.IDLE, outside.state)
        assertFalse(outside.move(0f, 200f)) // 띠 밖에서 시작한 밀기는 지금과 같다(페이지의 것)
    }

    @Test
    fun 막대가_보이면_지켜보지_않는다() {
        val s = swipe()
        s.down(0f, 0f, edge = 50f, enabled = false)
        assertEquals(State.IDLE, s.state)
        assertFalse(s.move(0f, 100f))
    }

    @Test
    fun 띠_안의_두드림은_페이지의_것이다() {
        val s = swipe()
        s.down(30f, 5f, edge = 50f, enabled = true)
        assertFalse(s.move(33f, 9f)) // 슬롭 안의 떨림
        assertEquals(State.WATCHING, s.state)
        s.end()
        assertEquals(State.IDLE, s.state)
    }

    @Test
    fun 위로_밀면_페이지의_것이다() {
        val s = swipe()
        s.down(0f, 40f, edge = 50f, enabled = true)
        assertFalse(s.move(0f, 29f)) // 위로 11
        assertEquals(State.IDLE, s.state)
        assertFalse(s.move(0f, 200f)) // 한 번 페이지의 것이면 끝까지
    }

    @Test
    fun 옆으로_밀면_페이지의_것이다() {
        val s = swipe()
        s.down(100f, 10f, edge = 50f, enabled = true)
        assertFalse(s.move(89f, 12f)) // 왼쪽으로 11
        assertEquals(State.IDLE, s.state)
    }

    @Test
    fun 세로가_우세해야_한다() {
        // 꼭 45° 는 세로가 우세한 것이 아니다 — 옆으로도 슬롭을 넘었으니 페이지의 것.
        val diagonal = swipe()
        diagonal.down(0f, 0f, edge = 50f, enabled = true)
        assertFalse(diagonal.move(15f, 15f))
        assertEquals(State.IDLE, diagonal.state)
        // 세로가 조금이라도 더 길면 막대를 부른다.
        val steep = swipe()
        steep.down(0f, 0f, edge = 50f, enabled = true)
        assertTrue(steep.move(-14f, 15f))
    }

    @Test
    fun 두_손가락은_페이지의_것이다_이미_부른_뒤면_그대로() {
        val pinch = swipe()
        pinch.down(0f, 0f, edge = 50f, enabled = true)
        pinch.pointerDown()
        assertEquals(State.IDLE, pinch.state)
        assertFalse(pinch.move(0f, 100f))
        val taken = swipe()
        taken.down(0f, 0f, edge = 50f, enabled = true)
        assertTrue(taken.move(0f, 30f))
        taken.pointerDown()
        assertEquals(State.TAKEN, taken.state)
    }
}

class BarTimerTest {

    private fun timer() = BarTimer(autoHideMs = 4_000L)

    @Test
    fun 보인_뒤_정한_시간이_지나면_숨는다() {
        val t = timer()
        t.show(0)
        assertEquals(4_000L, t.nextCheck(0))
        assertFalse(t.due(3_999))
        assertTrue(t.due(4_000))
    }

    @Test
    fun 막대를_만지는_동안은_숨지_않고_떼면_처음부터_센다() {
        val t = timer()
        t.show(0)
        t.touchBar(true, 1_000)
        assertFalse(t.due(5_000))
        assertEquals(9_000L, t.nextCheck(5_000)) // 만지는 중 — 정한 시간 뒤에 다시 본다
        t.touchBar(false, 6_000)
        assertFalse(t.due(9_999))
        assertTrue(t.due(10_000))
    }

    @Test
    fun 붙잡힌_동안은_숨지_않고_풀리면_처음부터_센다() {
        val t = timer()
        t.show(0)
        t.hold(true, 1_000) // 메뉴가 열렸다
        assertFalse(t.due(5_000))
        assertEquals(9_000L, t.nextCheck(5_000))
        t.hold(false, 7_000) // 닫혔다
        assertFalse(t.due(10_999))
        assertTrue(t.due(11_000))
    }

    @Test
    fun 붙잡히지_않았다는_알림을_거듭해도_세기가_늘지_않는다() {
        val t = timer()
        t.show(0)
        t.hold(false, 1_000)
        t.hold(false, 3_000)
        assertTrue(t.due(4_000))
    }

    @Test
    fun 페이지를_누르면_곧바로_숨긴다_붙잡혔거나_막대를_만지는_중이_아니면() {
        val t = timer()
        t.show(0)
        assertTrue(t.pageTouched())
        t.hold(true, 100) // 주소창을 고치는 중
        assertFalse(t.pageTouched())
        t.hold(false, 200)
        t.touchBar(true, 300)
        assertFalse(t.pageTouched())
    }

    @Test
    fun 숨어_있으면_볼_것이_없다() {
        val t = timer()
        assertNull(t.nextCheck(0))
        assertFalse(t.due(100_000))
        assertFalse(t.pageTouched())
        t.show(0)
        t.hide()
        assertNull(t.nextCheck(10_000))
        assertFalse(t.due(10_000))
    }

    @Test
    fun 보이던_막대를_다시_보이면_처음부터_센다() {
        val t = timer()
        t.show(0)
        t.show(3_000) // 보이는 중에 주소창에 초점이 갔다 등
        assertFalse(t.due(6_999))
        assertTrue(t.due(7_000))
    }

    @Test
    fun 숨기까지의_시간은_접근성_설정만큼_늘_수_있다() {
        val t = timer()
        t.autoHideMs = 10_000L
        t.show(0)
        assertFalse(t.due(9_999))
        assertTrue(t.due(10_000))
    }
}

class FullscreenLayoutTest {

    @Test
    fun 설정이_켜져_있고_TalkBack_과_분할_화면이_아닐_때만_전체_화면이다() {
        assertTrue(FullscreenLayout.effective(setting = true, touchExploration = false, multiWindow = false))
        assertFalse(FullscreenLayout.effective(setting = false, touchExploration = false, multiWindow = false))
        assertFalse(FullscreenLayout.effective(setting = true, touchExploration = true, multiWindow = false))
        assertFalse(FullscreenLayout.effective(setting = true, touchExploration = false, multiWindow = true))
    }

    @Test
    fun 가장자리_띠는_상태_표시줄의_높이_노치가_더_깊으면_노치_모르면_대신하는_값() {
        assertEquals(84, FullscreenLayout.edgeHeight(statusTop = 84, cutoutTop = 0, fallback = 72))
        assertEquals(90, FullscreenLayout.edgeHeight(statusTop = 70, cutoutTop = 90, fallback = 72))
        assertEquals(72, FullscreenLayout.edgeHeight(statusTop = 0, cutoutTop = 0, fallback = 72))
    }

    @Test
    fun 막대를_부르는_띠는_화면_높이의_위_20퍼센트이고_상태_표시줄보다_좁지_않다() {
        // 세로 폰(2400) — 위 480 px 까지.
        assertEquals(480f, FullscreenLayout.edgeBand(edgeHeight = 84f, rootHeight = 2400))
        // 가로(1080) — 216 px.
        assertEquals(216f, FullscreenLayout.edgeBand(edgeHeight = 84f, rootHeight = 1080))
        // 아직 재지 않은 뿌리(0)나 아주 낮은 창 — 상태 표시줄·노치의 띠는 남는다.
        assertEquals(84f, FullscreenLayout.edgeBand(edgeHeight = 84f, rootHeight = 0))
        assertEquals(84f, FullscreenLayout.edgeBand(edgeHeight = 84f, rootHeight = 300))
        // 띠 안(위 20% 의 끝 바로 위)에서 시작한 아래 밀기는 막대를 부르고, 그 아래에서 시작한 것은 페이지의 것이다.
        val band = FullscreenLayout.edgeBand(edgeHeight = 84f, rootHeight = 2400)
        val inside = EdgeSwipe(slop = 10f).apply { down(500f, band - 1f, band, enabled = true) }
        assertTrue(inside.move(500f, band + 40f))
        val below = EdgeSwipe(slop = 10f).apply { down(500f, band + 1f, band, enabled = true) }
        assertFalse(below.move(500f, band + 60f))
    }

    @Test
    fun 막대는_상태_표시줄_또는_노치만큼_위를_띄운다() {
        assertEquals(84, FullscreenLayout.barTop(statusTop = 84, cutoutTop = 80))
        assertEquals(96, FullscreenLayout.barTop(statusTop = 84, cutoutTop = 96))
    }

    @Test
    fun 전체_화면이면_뿌리의_위는_비운다() {
        assertEquals(0, FullscreenLayout.rootTop(fullscreen = true, safeTop = 84))
        assertEquals(84, FullscreenLayout.rootTop(fullscreen = false, safeTop = 84))
    }

    @Test
    fun 막대가_보이면_줄들이_막대_바로_아래로_내려온다() {
        // 막대 200(상태 표시줄 포함), 줄들의 노치 여백 80 — 여백은 막대 밑에 숨고 글이 막대 바로 아래(200)에서 시작한다.
        assertEquals(120, FullscreenLayout.stripsShift(fullscreen = true, shown = true, barHeight = 200, stripsTop = 80))
        assertEquals(0, FullscreenLayout.stripsShift(fullscreen = true, shown = false, barHeight = 200, stripsTop = 80))
        assertEquals(0, FullscreenLayout.stripsShift(fullscreen = false, shown = true, barHeight = 200, stripsTop = 0))
        // 아직 재지 않은 막대(0) — 위로 올리지 않는다.
        assertEquals(0, FullscreenLayout.stripsShift(fullscreen = true, shown = true, barHeight = 0, stripsTop = 80))
    }

    @Test
    fun 쪽의_자리에서_가려진_높이() {
        // 전체 화면이 아니면 쪽의 자리는 막대 아래다.
        assertEquals(0, FullscreenLayout.coveredTop(false, shown = true, barHeight = 200, stripsTop = 0, stripsShowing = false, cutoutTop = 80))
        // 막대가 보이면 막대가 덮는다(줄이 있으면 줄의 노치 여백만큼은 줄들이 이미 쪽을 밀어냈다).
        assertEquals(200, FullscreenLayout.coveredTop(true, shown = true, barHeight = 200, stripsTop = 0, stripsShowing = false, cutoutTop = 80))
        assertEquals(120, FullscreenLayout.coveredTop(true, shown = true, barHeight = 200, stripsTop = 80, stripsShowing = true, cutoutTop = 80))
        // 숨어 있으면 노치만 — 줄이 있으면 줄들이 노치 밑에서 시작하고 쪽은 그 아래라 가려지지 않는다.
        assertEquals(80, FullscreenLayout.coveredTop(true, shown = false, barHeight = 200, stripsTop = 0, stripsShowing = false, cutoutTop = 80))
        assertEquals(0, FullscreenLayout.coveredTop(true, shown = false, barHeight = 200, stripsTop = 80, stripsShowing = true, cutoutTop = 80))
    }
}
