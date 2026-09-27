package io.github.donggi.iroiroviewer.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * PiP 계산.
 *
 * **시네마스코프 영상을 열기만 해도 앱이 죽는 길**이 이 파일이 막는 것이다 —
 * 시스템이 받는 비율은 `[0.41841, 2.39]` 이고, 벗어나면 PiP 로 들어갈 때뿐 아니라
 * 파라미터를 갱신하기만 해도 `IllegalArgumentException` 이 난다.
 */
class PipMathTest {

    @Test
    fun 비율을_모르면_넘기지_않는다() {
        assertNull(PipMath.aspectOf(0f))
        assertNull(PipMath.aspectOf(-1.5f))
        assertNull(PipMath.aspectOf(Float.NaN))
        assertNull(PipMath.aspectOf(Float.POSITIVE_INFINITY))
    }

    @Test
    fun 보통_비율은_1000분의_1_로_옮긴다() {
        val a = PipMath.aspectOf(16f / 9f)!!
        assertEquals(1000, a.denominator)
        assertEquals(1778, a.numerator)
        assertEquals(1.778f, a.numerator / a.denominator.toFloat())
    }

    @Test
    fun 세로_영상은_그대로_통과한다() {
        // 9:16 = 0.5625. 하한 0.41841 위라 자를 것이 없다.
        val a = PipMath.aspectOf(9f / 16f)!!
        assertEquals(563, a.numerator)
    }

    @Test
    fun 시네마스코프는_상한으로_잘린다() {
        val a = PipMath.aspectOf(2.76f)!!
        assertTrue(a.numerator / a.denominator.toFloat() <= PipMath.MAX_ASPECT)
        assertEquals(2380, a.numerator)
    }

    @Test
    fun 극단적으로_긴_세로도_하한으로_잘린다() {
        val a = PipMath.aspectOf(0.1f)!!
        assertTrue(a.numerator / a.denominator.toFloat() >= PipMath.MIN_ASPECT)
        assertEquals(420, a.numerator)
    }

    @Test
    fun 자른_값은_언제나_시스템_한계_안쪽이다() {
        // 시스템 한계는 [0.41841, 2.39] 양끝 포함이다.
        val ratios = listOf(0.01f, 0.4f, 0.41f, 0.42f, 1f, 2.38f, 2.39f, 2.4f, 100f)
        for (r in ratios) {
            val a = PipMath.aspectOf(r)!!
            val v = a.numerator / a.denominator.toFloat()
            assertTrue(v >= 0.41841f, "$r 을 자른 $v 가 하한을 넘었다")
            assertTrue(v <= 2.39f, "$r 을 자른 $v 가 상한을 넘었다")
        }
    }

    @Test
    fun 큐가_없으면_조작은_하나다() {
        assertEquals(listOf(PipMath.Command.PLAY), PipMath.commands(hasQueue = false, isPlaying = false, maxActions = 3))
        assertEquals(listOf(PipMath.Command.PAUSE), PipMath.commands(hasQueue = false, isPlaying = true, maxActions = 3))
    }

    @Test
    fun 큐가_있으면_이전_재생_다음_셋이다() {
        assertEquals(
            listOf(PipMath.Command.PREVIOUS, PipMath.Command.PAUSE, PipMath.Command.NEXT),
            PipMath.commands(hasQueue = true, isPlaying = true, maxActions = 3),
        )
    }

    @Test
    fun 자리가_모자라면_우리가_고른다() {
        // 넘겨도 예외가 아니라 조용히 잘리는데, 자르는 차례를 우리가 고를 수 없다.
        assertEquals(
            listOf(PipMath.Command.PLAY),
            PipMath.commands(hasQueue = true, isPlaying = false, maxActions = 2),
        )
    }

    @Test
    fun 목록이_펼쳐져_있으면_들어가지_않는다() {
        // 그 상태에서는 표면이 컴포지션에 없어 '영상 없는 창' 이 뜬다.
        assertFalse(PipMath.canEnter(hasVideo = true, playlistOpen = true, supported = true))
        assertTrue(PipMath.canEnter(hasVideo = true, playlistOpen = false, supported = true))
    }

    @Test
    fun 소리_파일과_지원하지_않는_기기에서는_들어가지_않는다() {
        assertFalse(PipMath.canEnter(hasVideo = false, playlistOpen = false, supported = true))
        assertFalse(PipMath.canEnter(hasVideo = true, playlistOpen = false, supported = false))
    }

    @Test
    fun 저절로_들어가는_것은_재생_중일_때뿐이다() {
        assertTrue(PipMath.autoEnter(hasVideo = true, isPlaying = true, playlistOpen = false, supported = true))
        assertFalse(
            PipMath.autoEnter(hasVideo = true, isPlaying = false, playlistOpen = false, supported = true),
            "멈춰 둔 영상은 계속 볼 뜻이 아니다",
        )
        // 단추는 추측이 아니라 명시라 멈춰 있어도 들어간다.
        assertTrue(PipMath.canEnter(hasVideo = true, playlistOpen = false, supported = true))
    }
}
