package io.github.donggi.iroiroviewer.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * A-B 구간 반복의 규칙.
 *
 * 되감기는 티커가 하고 티커는 기기에서만 도는데, **무엇을 찍고 언제 푸는가**는 전부
 * 여기서 박힌다. 설계 검토가 잡은 결함 둘 — '꼬리 보호가 A 에는 안 걸린다' 와
 * '배속을 잊는다' — 이 파일이 지키는 것이다.
 */
class AbRepeatTest {

    private val DUR = 180_000L // 3분

    @Test
    fun 길이를_모르면_아무것도_찍지_못한다() {
        assertIs<AbRepeat.Result.TooShort>(AbRepeat.mark(null, 1_000, 0))
        assertIs<AbRepeat.Result.TooShort>(AbRepeat.mark(null, 1_000, -1))
    }

    @Test
    fun 파일이_최소_구간보다_짧으면_찍지_못한다() {
        val tooShort = AbRepeat.MIN_SPAN_MS + AbRepeat.TAIL_GUARD_MS - 1
        assertIs<AbRepeat.Result.TooShort>(AbRepeat.mark(null, 0, tooShort))
    }

    @Test
    fun 세_번_누르면_한_바퀴다() {
        val first = AbRepeat.mark(null, 10_000, DUR)
        val a = assertIs<AbRepeat.Result.Marked>(first).span
        assertEquals(10_000, a.aMs)
        assertEquals(null, a.bMs)
        assertFalse(a.isLooping)

        val second = AbRepeat.mark(a, 30_000, DUR)
        val ab = assertIs<AbRepeat.Result.Marked>(second).span
        assertEquals(10_000, ab.aMs)
        assertEquals(30_000, ab.bMs)
        assertTrue(ab.isLooping)

        assertIs<AbRepeat.Result.Cleared>(AbRepeat.mark(ab, 20_000, DUR))
    }

    @Test
    fun 짧은_구간은_거절하고_A_를_몰래_옮기지_않는다() {
        val a = assertIs<AbRepeat.Result.Marked>(AbRepeat.mark(null, 10_000, DUR)).span
        // 1초가 안 되는 자리에서 B 를 찍었다.
        val r = AbRepeat.mark(a, 10_000 + AbRepeat.MIN_SPAN_MS - 1, DUR)
        assertIs<AbRepeat.Result.TooShort>(r)
        // 부르는 쪽은 `current` 를 그대로 들고 있으면 된다 — 이 함수가 바꾼 것이 없다.
    }

    @Test
    fun A_에도_꼬리_여유가_걸린다() {
        // 끝에 바짝 붙여 찍어도 뒤에 최소 구간 + 꼬리 여유가 남는다.
        val a = assertIs<AbRepeat.Result.Marked>(AbRepeat.mark(null, DUR, DUR)).span
        assertEquals(DUR - AbRepeat.TAIL_GUARD_MS - AbRepeat.MIN_SPAN_MS, a.aMs)
        // 그래서 그 뒤에 B 를 찍는 것이 실제로 가능하다.
        val ab = assertIs<AbRepeat.Result.Marked>(AbRepeat.mark(a, DUR, DUR)).span
        assertEquals(DUR - AbRepeat.TAIL_GUARD_MS, ab.bMs)
        assertTrue(ab.bMs!! <= DUR - AbRepeat.TAIL_GUARD_MS, "B 가 바닥에 붙으면 다음 곡으로 넘어간다")
    }

    @Test
    fun 음수_위치는_0_으로_잘린다() {
        val a = assertIs<AbRepeat.Result.Marked>(AbRepeat.mark(null, -5_000, DUR)).span
        assertEquals(0, a.aMs)
    }

    @Test
    fun B_를_지나면_되감는다() {
        val span = AbRepeat.Span(10_000, 30_000)
        assertFalse(AbRepeat.shouldLoop(span, 29_999))
        assertTrue(AbRepeat.shouldLoop(span, 30_000))
        assertTrue(AbRepeat.shouldLoop(span, 30_500))
    }

    @Test
    fun A_만_찍힌_상태에서는_되감지_않는다() {
        assertFalse(AbRepeat.shouldLoop(AbRepeat.Span(10_000, null), 999_999))
    }

    @Test
    fun 구간_밖으로_건너뛰면_푼다() {
        val span = AbRepeat.Span(10_000, 30_000)
        // 되감기 자신의 넘침은 '건너뜀' 이 아니다 — 그것까지 푸는 판정이면 스스로 해제한다.
        assertFalse(AbRepeat.escaped(span, 30_000 + AbRepeat.TOLERANCE_MS))
        assertTrue(AbRepeat.escaped(span, 30_000 + AbRepeat.TOLERANCE_MS + 1))
        assertFalse(AbRepeat.escaped(span, 10_000 - AbRepeat.TOLERANCE_MS))
        assertTrue(AbRepeat.escaped(span, 10_000 - AbRepeat.TOLERANCE_MS - 1))
    }

    @Test
    fun A_만_찍힌_동안에는_앞으로만_벗어난다() {
        val a = AbRepeat.Span(10_000, null)
        assertFalse(AbRepeat.escaped(a, 999_999), "B 를 아직 안 찍었으니 뒤쪽은 제한이 없다")
        assertTrue(AbRepeat.escaped(a, 0))
    }

    @Test
    fun B_근처에서만_티커를_조인다() {
        val span = AbRepeat.Span(10_000, 30_000)
        assertEquals(500, AbRepeat.tickDelayMs(span, 10_000, 1f, 500))
        assertEquals(500, AbRepeat.tickDelayMs(span, 28_000, 1f, 500))
        assertEquals(AbRepeat.TIGHT_TICK_MS, AbRepeat.tickDelayMs(span, 29_000, 1f, 500))
        assertEquals(AbRepeat.TIGHT_TICK_MS, AbRepeat.tickDelayMs(span, 30_100, 1f, 500))
    }

    @Test
    fun 배속이_빠르면_더_일찍_조인다() {
        val span = AbRepeat.Span(10_000, 30_000)
        // 2배속이면 남은 2초가 벽시계로 1초다 — 조여야 한다.
        assertEquals(AbRepeat.TIGHT_TICK_MS, AbRepeat.tickDelayMs(span, 28_000, 2f, 500))
        // 0.5배속이면 남은 1초가 벽시계로 2초다 — 아직 조이지 않는다.
        assertEquals(500, AbRepeat.tickDelayMs(span, 29_000, 0.5f, 500))
    }

    @Test
    fun 구간이_없거나_A_만_있으면_평소_간격이다() {
        assertEquals(500, AbRepeat.tickDelayMs(null, 1_000, 1f, 500))
        assertEquals(500, AbRepeat.tickDelayMs(AbRepeat.Span(10_000, null), 10_500, 1f, 500))
    }

    @Test
    fun 이상한_배속은_보통으로_본다() {
        val span = AbRepeat.Span(10_000, 30_000)
        assertEquals(500, AbRepeat.tickDelayMs(span, 20_000, 0f, 500))
        assertEquals(500, AbRepeat.tickDelayMs(span, 20_000, Float.NaN, 500))
    }
}
