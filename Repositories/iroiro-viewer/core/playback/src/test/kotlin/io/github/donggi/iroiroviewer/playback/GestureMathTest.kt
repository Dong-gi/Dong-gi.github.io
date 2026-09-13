package io.github.donggi.iroiroviewer.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 제스처가 값을 얼마나 움직이는가.
 *
 * **화면 캡처로 판정하지 않는다**(CLAUDE.md 함정). 6단계가 그것으로 세 번 틀렸다.
 * 계산은 여기서 박고, 기기에서는 연결이 되었는지만 `Iro.d` 로 본다.
 */
class GestureMathTest {

    @Test
    fun 좌우를_반으로_가른다() {
        assertEquals(GestureMath.Side.LEFT, GestureMath.sideOf(0f, 1000f))
        assertEquals(GestureMath.Side.LEFT, GestureMath.sideOf(499f, 1000f))
        assertEquals(GestureMath.Side.RIGHT, GestureMath.sideOf(500f, 1000f))
        assertEquals(GestureMath.Side.RIGHT, GestureMath.sideOf(1000f, 1000f))
        // 폭을 모르면 오른쪽으로 본다 — 가운데 띠를 만들지 않는다.
        assertEquals(GestureMath.Side.RIGHT, GestureMath.sideOf(0f, 0f))
    }

    @Test
    fun 축을_한_번_정한다() {
        assertTrue(GestureMath.isHorizontal(10f, 3f))
        assertFalse(GestureMath.isHorizontal(3f, 10f))
        // 정확히 같으면 가로 — 어느 쪽이든 정해져 있기만 하면 된다.
        assertTrue(GestureMath.isHorizontal(5f, -5f))
    }

    /** 화면 폭을 전부 끌면 90초. **영상 길이에 비례시키지 않는다.** */
    @Test
    fun 탐색은_화면_폭에_비례한다() {
        assertEquals(GestureMath.FULL_SWIPE_SEEK_MS, GestureMath.seekDeltaMs(1000f, 1000f))
        assertEquals(-GestureMath.FULL_SWIPE_SEEK_MS, GestureMath.seekDeltaMs(-1000f, 1000f))
        assertEquals(GestureMath.FULL_SWIPE_SEEK_MS / 2, GestureMath.seekDeltaMs(500f, 1000f))
        assertEquals(0L, GestureMath.seekDeltaMs(100f, 0f))
    }

    @Test
    fun 세로는_위로_끌면_커진다() {
        val h = 1000f
        val full = h * GestureMath.FULL_SWIPE_FRACTION
        assertEquals(1f, GestureMath.levelDelta(-full, h), 0.001f)
        assertEquals(-1f, GestureMath.levelDelta(full, h), 0.001f)
        assertEquals(0f, GestureMath.levelDelta(10f, 0f), 0.001f)
    }

    @Test
    fun 탐색_위치를_길이_안으로_자른다() {
        assertEquals(0L, GestureMath.clampPosition(-5000, 60_000))
        assertEquals(60_000L, GestureMath.clampPosition(90_000, 60_000))
        assertEquals(30_000L, GestureMath.clampPosition(30_000, 60_000))
        // 길이를 모르면 0. 끝을 모르는 채로 뛰지 않는다.
        assertEquals(0L, GestureMath.clampPosition(30_000, 0))
    }

    @Test
    fun 두드림을_창_안에서_센다() {
        assertEquals(1, GestureMath.tapCount(lastAtMs = 0, nowMs = 1000, previousCount = 0))
        assertEquals(2, GestureMath.tapCount(lastAtMs = 1000, nowMs = 1200, previousCount = 1))
        assertEquals(3, GestureMath.tapCount(lastAtMs = 1200, nowMs = 1400, previousCount = 2))
        // 창을 벗어나면 다시 1부터.
        val far = 1400L + GestureMath.MULTI_TAP_WINDOW_MS + 1
        assertEquals(1, GestureMath.tapCount(lastAtMs = 1400, nowMs = far, previousCount = 3))
    }

    /**
     * 토글을 미루는 시간이 **너무 길면 안 된다.**
     *
     * 첫 두드림의 조작부 토글을 이만큼 미뤄 두고 그 안에 두 번째가 오면 취소하는데,
     * 그 값이 크면 한 번만 두드렸을 때 조작부가 늦게 나온다. 안드로이드의 더블탭
     * 기본값(300ms) 언저리를 넘지 않아야 한다.
     */
    @Test
    fun 토글을_미루는_시간이_사람이_기다릴_만하다() {
        assertTrue(GestureMath.MULTI_TAP_WINDOW_MS in 250..400, "${GestureMath.MULTI_TAP_WINDOW_MS}ms")
        // 안내는 그보다 오래 남아야 읽힌다. 다만 한참 남아 있으면 안 된다.
        assertTrue(GestureMath.TAP_FEEDBACK_HOLD_MS > GestureMath.MULTI_TAP_WINDOW_MS)
        assertTrue(GestureMath.TAP_FEEDBACK_HOLD_MS <= 1500)
    }

    /** **첫 두드림은 옮기지 않는다** — 그것은 조작부 토글이다. */
    @Test
    fun 두_번째부터_삼십초씩() {
        assertEquals(0L, GestureMath.tapSeekMs(1, GestureMath.Side.RIGHT))
        assertEquals(0L, GestureMath.tapSeekMs(1, GestureMath.Side.LEFT))
        assertEquals(GestureMath.TAP_SEEK_MS, GestureMath.tapSeekMs(2, GestureMath.Side.RIGHT))
        assertEquals(-GestureMath.TAP_SEEK_MS, GestureMath.tapSeekMs(2, GestureMath.Side.LEFT))
        assertEquals(GestureMath.TAP_SEEK_MS, GestureMath.tapSeekMs(5, GestureMath.Side.RIGHT))
    }
}
