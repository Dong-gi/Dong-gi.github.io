package io.github.donggi.iroiroviewer.ui.gesture

import io.github.donggi.iroiroviewer.ui.gesture.DismissMath.Axis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 아래로 끌어 닫기 — **누가 이 제스처를 갖는가**, 그리고 **손을 뗐을 때 닫는가.**
 *
 * 이미지 뷰어 한 장에는 이미 페이저·핀치·탭이 있다. 넷째 인식기가 그들과 다투면 사용자에게는
 * '밀었더니 장이 넘어가다 말고 끌려 내려온다', '두 번 두드렸더니 조금 내려갔다' 로 보인다.
 * 그 판정은 화면 캡처로 할 수 없다(6단계가 세 번 틀렸다) — 여기서 박는다.
 */
class DismissMathTest {

    private val slop = 8f

    // ---- 축 ----------------------------------------------------------------------

    @Test
    fun 슬롭_안에서는_아무도_갖지_않는다() {
        assertEquals(Axis.UNDECIDED, DismissMath.decide(0f, 5f, slop))
        assertEquals(Axis.UNDECIDED, DismissMath.decide(4f, 4f, slop))
    }

    @Test
    fun 아래로_끌면_닫기다() {
        assertEquals(Axis.DISMISS, DismissMath.decide(0f, 9f, slop))
        assertEquals(Axis.DISMISS, DismissMath.decide(-5f, 9f, slop))
    }

    @Test
    fun 가로가_크면_페이저의_것이다() {
        assertEquals(Axis.OTHER, DismissMath.decide(9f, 1f, slop))
        assertEquals(Axis.OTHER, DismissMath.decide(-9f, 4f, slop))
        // 정확히 45° 는 가로로 친다 — 넘긴 장은 되밀면 되지만 닫힌 화면은 다시 열어야 한다.
        assertEquals(Axis.OTHER, DismissMath.decide(7f, 7f, slop))
    }

    @Test
    fun 위로_끌면_닫기가_아니다() {
        assertEquals(Axis.OTHER, DismissMath.decide(0f, -9f, slop))
    }

    @Test
    fun 한_번_정한_축은_바뀌지_않는다() {
        // 가로로 시작해 세로로 꺾어도 다시 잡지 않는다 — 넘어가던 장이 끌려 내려오지 않는다.
        val a = DismissArbiter(slop)
        assertEquals(Axis.OTHER, a.onMove(10f, 0f, 1, false))
        assertEquals(Axis.OTHER, a.onMove(0f, 200f, 1, false))
        // 세로로 잡은 뒤 가로로 흘러도 끌기 그대로다.
        val b = DismissArbiter(slop)
        assertEquals(Axis.UNDECIDED, b.onMove(0f, 4f, 1, false))
        assertEquals(Axis.DISMISS, b.onMove(1f, 6f, 1, false))
        assertEquals(Axis.DISMISS, b.onMove(300f, 0f, 1, false))
    }

    @Test
    fun 이동은_누적해서_잰다() {
        // 한 이벤트가 슬롭을 못 넘어도 여럿이 모이면 넘는다. 이벤트마다 따로 재면 느린 손이 영영 못 잡는다.
        val a = DismissArbiter(slop)
        repeat(3) { assertEquals(Axis.UNDECIDED, a.onMove(0f, 2f, 1, false)) }
        assertEquals(Axis.DISMISS, a.onMove(0f, 3f, 1, false))
    }

    @Test
    fun 두_손가락이면_핀치의_것이다() {
        val a = DismissArbiter(slop)
        assertEquals(Axis.OTHER, a.onMove(0f, 20f, 2, false))
        // 한 손가락으로 돌아와도 이 제스처는 끝까지 남의 것이다.
        assertEquals(Axis.OTHER, a.onMove(0f, 20f, 1, false))
    }

    @Test
    fun 아래층이_소비했으면_물러난다() {
        // 확대 상태의 팬 — transformable 이 이미 가져갔다.
        val a = DismissArbiter(slop)
        assertEquals(Axis.OTHER, a.onMove(0f, 20f, 1, true))
    }

    @Test
    fun 배율_1_이고_페이저가_멈춘_자리_잡은_장에서만_받는다() {
        assertTrue(DismissMath.canStart(settledHere = true, pagerScrolling = false, zoomed = false))
        // 확대 상태의 세로 끌기는 팬이다. transformable 이 아직 소비하지 않은 첫 이벤트에서 받으면 화면이 닫힌다.
        assertFalse(DismissMath.canStart(settledHere = true, pagerScrolling = false, zoomed = true))
        assertFalse(DismissMath.canStart(settledHere = true, pagerScrolling = true, zoomed = false))
        assertFalse(DismissMath.canStart(settledHere = false, pagerScrolling = false, zoomed = false))
    }

    // ---- 놓을 때 --------------------------------------------------------------------

    private val height = 2400f
    private val fling = 2625f // 1000 dp/s @ 2.625x

    @Test
    fun 문턱을_넘겨_놓으면_닫는다() {
        assertTrue(DismissMath.shouldDismiss(height * 0.2f, 0f, height, fling))
        assertFalse(DismissMath.shouldDismiss(height * 0.1f, 0f, height, fling))
    }

    @Test
    fun 빠르게_튕기면_짧아도_닫는다() {
        assertTrue(DismissMath.shouldDismiss(40f, fling + 1f, height, fling))
    }

    @Test
    fun 위로_되던지면_문턱을_넘었어도_닫지_않는다() {
        assertFalse(DismissMath.shouldDismiss(height * 0.4f, -fling - 1f, height, fling))
    }

    @Test
    fun 제자리면_닫지_않는다() {
        assertFalse(DismissMath.shouldDismiss(0f, fling * 10, height, fling))
        assertFalse(DismissMath.shouldDismiss(100f, Float.NaN, 0f, fling))
    }

    // ---- 옅어지기 ---------------------------------------------------------------------

    @Test
    fun 끌수록_옅어지되_사라지지는_않는다() {
        assertEquals(0f, DismissMath.progress(0f, height))
        assertEquals(0f, DismissMath.progress(-50f, height))
        assertEquals(1f, DismissMath.progress(height, height))
        assertEquals(1f, DismissMath.contentAlpha(0f))
        assertEquals(DismissMath.MIN_CONTENT_ALPHA, DismissMath.contentAlpha(1f), 1e-6f)
        val half = DismissMath.contentAlpha(DismissMath.progress(height * 0.25f, height))
        assertTrue(half < 1f && half > DismissMath.MIN_CONTENT_ALPHA)
    }

    @Test
    fun 막대가_그림보다_먼저_사라진다() {
        val p = DismissMath.progress(height * DismissMath.DISTANCE_FRACTION, height)
        assertTrue(DismissMath.chromeAlpha(p) < DismissMath.contentAlpha(p))
        assertEquals(0f, DismissMath.chromeAlpha(1f))
        assertEquals(1f, DismissMath.chromeAlpha(0f))
    }
}
