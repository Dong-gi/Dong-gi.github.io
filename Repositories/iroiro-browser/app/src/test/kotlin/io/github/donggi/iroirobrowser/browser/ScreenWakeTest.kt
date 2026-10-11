package io.github.donggi.iroirobrowser.browser

import io.github.donggi.iroirobrowser.browser.PlayingMedia.State
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayingMediaTest {

    @Test
    fun 소리가_나는_재생을_알리면_바뀌고_같은_알림은_바뀌지_않는다() {
        val p = PlayingMedia()
        assertEquals(State.NONE, p.state(0))
        assertTrue(p.report("f1", audible = true, video = false, now = 0))
        assertEquals(State(audible = true, video = false), p.state(0))
        // 재생 중이면 10초마다 다시 알린다 — 상태는 그대로다.
        assertFalse(p.report("f1", audible = true, video = false, now = 10_000))
    }

    @Test
    fun 멈춤을_알리면_그_틀을_잊는다() {
        val p = PlayingMedia()
        p.report("f1", audible = true, video = true, now = 0)
        assertTrue(p.report("f1", audible = false, video = false, now = 1_000))
        assertEquals(State.NONE, p.state(1_000))
        assertNull(p.nextStale(1_000))
        // 재생 중이 아닌 틀의 알림(탭을 고를 때 물은 답)은 아무것도 바꾸지 않는다.
        assertFalse(p.report("f2", audible = false, video = false, now = 2_000))
    }

    @Test
    fun 틀마다의_상태를_합친다() {
        val p = PlayingMedia()
        p.report("top", audible = false, video = true, now = 0) // 음소거 배경 영상
        assertEquals(State(audible = false, video = true), p.state(0))
        assertTrue(p.report("ad-frame", audible = true, video = false, now = 0)) // 다른 출처 틀의 소리
        assertEquals(State(audible = true, video = true), p.state(0))
        assertTrue(p.report("ad-frame", audible = false, video = false, now = 5_000))
        assertEquals(State(audible = false, video = true), p.state(5_000))
    }

    @Test
    fun 소식이_없는_틀은_30초_뒤에_잊는다() {
        val p = PlayingMedia()
        p.report("f1", audible = true, video = false, now = 1_000)
        assertEquals(31_001L, p.nextStale(1_000))
        assertTrue(p.state(31_000).audible) // 꼭 30초는 아직 산다
        assertEquals(State.NONE, p.state(31_001))
        assertNull(p.nextStale(31_001))
    }

    @Test
    fun 다시_알림이_오면_잊는_때가_밀린다() {
        val p = PlayingMedia()
        p.report("f1", audible = true, video = false, now = 0)
        p.report("f1", audible = true, video = false, now = 20_000)
        assertTrue(p.state(45_000).audible)
        assertEquals(50_001L, p.nextStale(45_000))
    }

    @Test
    fun 잊는_때는_가장_먼저_오래되는_틀의_것이다() {
        val p = PlayingMedia()
        p.report("a", audible = true, video = false, now = 0)
        p.report("b", audible = false, video = true, now = 10_000)
        assertEquals(30_001L, p.nextStale(10_000))
        assertEquals(State(audible = false, video = true), p.state(30_001))
        assertEquals(40_001L, p.nextStale(30_001))
    }

    @Test
    fun 오래된_틀은_다음_알림에_치운다() {
        val p = PlayingMedia()
        p.report("old", audible = true, video = false, now = 0)
        // 그 틀이 사라진 뒤 다른 틀이 알린다 — 앞의 것은 이미 오래돼 세지 않았으므로 '바뀌었다'(없음 → 소리)이고, 잊는 때는 새 틀의 것이다.
        assertTrue(p.report("new", audible = true, video = false, now = 40_000))
        assertEquals(70_001L, p.nextStale(40_000))
        assertTrue(p.report("new", audible = false, video = false, now = 41_000))
        assertEquals(State.NONE, p.state(41_000))
    }

    @Test
    fun 모두_잊으면_재생_중이었을_때만_바뀐다() {
        val p = PlayingMedia()
        assertFalse(p.clear(0))
        p.report("f1", audible = false, video = true, now = 0)
        assertTrue(p.clear(1_000))
        assertEquals(State.NONE, p.state(1_000))
        // 이미 오래된 것만 남았으면 바뀐 것이 없다.
        p.report("f1", audible = true, video = false, now = 0)
        assertFalse(p.clear(40_000))
    }
}

class ScreenWakeTest {

    @Test
    fun 소리가_나는_미디어는_화면을_켜_둔다() {
        assertTrue(ScreenWake.wanted(State(audible = true, video = false), fullscreenVideo = false)) // 소리만
        assertTrue(ScreenWake.wanted(State(audible = true, video = true), fullscreenVideo = false)) // 소리가 나는 영상
        assertTrue(ScreenWake.wanted(State(audible = true, video = true), fullscreenVideo = true))
    }

    @Test
    fun 페이지_안의_음소거_영상은_켜_두지_않는다() {
        assertFalse(ScreenWake.wanted(State(audible = false, video = true), fullscreenVideo = false))
    }

    @Test
    fun 전체_화면으로_보는_영상은_음소거여도_켜_둔다() {
        assertTrue(ScreenWake.wanted(State(audible = false, video = true), fullscreenVideo = true))
    }

    @Test
    fun 재생_중인_것이_없으면_전체_화면이어도_켜_두지_않는다() {
        // 전체 화면에서 멈춰 두었다 — 시스템의 꺼짐 시간을 따른다.
        assertFalse(ScreenWake.wanted(State.NONE, fullscreenVideo = true))
        assertFalse(ScreenWake.wanted(State.NONE, fullscreenVideo = false))
    }
}
