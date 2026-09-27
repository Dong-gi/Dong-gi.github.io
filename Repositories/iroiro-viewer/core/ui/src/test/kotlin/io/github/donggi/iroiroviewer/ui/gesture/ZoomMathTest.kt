package io.github.donggi.iroiroviewer.ui.gesture

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 확대·이동 계산.
 *
 * **이것을 시험으로 못 박는 이유**는 이미지 뷰어에서 가장 자주 망가지는 것이 제스처
 * 인식이 아니라 경계 계산이기 때문이다. 에뮬레이터에서 손으로 밀어 보며 고치면 느리고,
 * 무엇보다 고쳤는지 확인할 방법이 없다 — 실제로 이 단계에서 화면 캡처로 판정하려다
 * 세 번 틀린 결론을 냈다.
 */
class ZoomMathTest {

    private fun near(expected: Float, actual: Float, tolerance: Float = 0.01f) =
        assertTrue(abs(expected - actual) < tolerance, "기대 $expected, 실제 $actual")

    @Test
    fun `그림이 뷰포트보다 작으면 밀 자리가 없다`() {
        // 배율 1 에서 맞춰 그린 그림은 정의상 뷰포트를 넘지 않는다.
        assertEquals(0f, ZoomMath.maxOffset(contentSize = 800f, viewportSize = 1080f, scale = 1f))
        assertFalse(ZoomMath.canPan(0f, 0f))
    }

    @Test
    fun `확대하면 넘친 만큼의 절반까지 민다`() {
        // 1080 폭 그림을 2배 = 2160. 뷰포트 1080 을 1080 넘치고, 가운데 정렬이므로 한쪽으로 540.
        near(540f, ZoomMath.maxOffset(1080f, 1080f, 2f))
        assertTrue(ZoomMath.canPan(540f, 0f))
    }

    @Test
    fun `경계를 넘겨 밀어도 그림이 화면 밖으로 빠지지 않는다`() {
        val limit = ZoomMath.maxOffset(1080f, 1080f, 2f)
        near(limit, ZoomMath.clamp(9999f, 1080f, 1080f, 2f))
        near(-limit, ZoomMath.clamp(-9999f, 1080f, 1080f, 2f))
        near(0f, ZoomMath.clamp(0f, 1080f, 1080f, 1f))
    }

    @Test
    fun `짚은 자리가 확대해도 제자리에 남는다`() {
        // 원점(중앙)을 짚으면 오프셋이 비율만큼만 늘어난다.
        near(0f, ZoomMath.offsetAfterZoom(offset = 0f, pivot = 0f, oldScale = 1f, newScale = 2f))
        // 중앙에서 오른쪽으로 100 떨어진 곳을 짚고 2배로 키우면,
        // 그 점이 제자리에 있으려면 내용이 왼쪽으로 100 밀려야 한다.
        near(-100f, ZoomMath.offsetAfterZoom(offset = 0f, pivot = 100f, oldScale = 1f, newScale = 2f))
        // 되돌리면 원래대로. 왕복이 맞아야 더블탭이 튀지 않는다.
        val once = ZoomMath.offsetAfterZoom(0f, 100f, 1f, 2.5f)
        near(0f, ZoomMath.offsetAfterZoom(once, 100f, 2.5f, 1f))
    }

    @Test
    fun `더블탭은 두 칸만 오간다`() {
        near(2.5f, ZoomMath.doubleTapTarget(current = 1f, zoomedScale = 2.5f))
        near(1f, ZoomMath.doubleTapTarget(current = 2.5f, zoomedScale = 2.5f))
        // 핀치로 어중간하게 키워 둔 상태에서 더블탭하면 **되돌린다.** 더 키우면
        // 사용자는 지금 어디에 있는지 모른 채 두 번 누르게 된다.
        near(1f, ZoomMath.doubleTapTarget(current = 1.4f, zoomedScale = 2.5f))
    }

    @Test
    fun `맞춤 크기는 짧은 쪽에 맞춘다`() {
        // 가로로 긴 그림(2000x1000)을 세로 화면(1080x2400)에 맞추면 폭이 먼저 찬다.
        val (w, h) = ZoomMath.fittedSize(2000, 1000, 1080f, 2400f)
        near(1080f, w)
        near(540f, h)
        // 세로로 긴 그림(1000x2000)이라도 **화면 비율에 따라 폭이 먼저 찰 수 있다.**
        // min(1080/1000, 2400/2000) = min(1.08, 1.2) = 1.08 이라 폭이 1080 에서 멈춘다.
        // '세로 그림이면 높이가 먼저 찬다' 는 직관이 틀리는 자리다 — 그래서 시험한다.
        val (w2, h2) = ZoomMath.fittedSize(1000, 2000, 1080f, 2400f)
        near(1080f, w2)
        near(2160f, h2)

        // 화면보다 훨씬 세로로 긴 그림(1000x5000)이라야 높이가 먼저 찬다.
        val (w3, h3) = ZoomMath.fittedSize(1000, 5000, 1080f, 2400f)
        near(480f, w3)
        near(2400f, h3)
    }

    @Test
    fun `최대 배율은 원본의 두 배다`() {
        // 태블릿(2560x1600)에 4:3 사진(4000x3000). 높이가 먼저 차서 그려진 폭은 2133 이다.
        val (fittedW, _) = ZoomMath.fittedSize(4000, 3000, 2560f, 1600f)
        near(2133.33f, fittedW, tolerance = 1f)
        // 원본의 두 배 = 8000 화소 폭. 맞춤(2133) 기준으로 3.75배다.
        // **뷰포트 폭(2560)으로 재면** 3.125배로 낮게 잡힌다 — 그려진 폭으로 재는 이유.
        near(3.75f, ZoomMath.maxScale(4000, fittedW))
        // 12MP 를 폰 폭에 맞추면 원본의 두 배는 맞춤의 22배다.
        near(22.22f, ZoomMath.maxScale(12000, 1080f), tolerance = 0.05f)
        // 퇴화한 경우만 막는다.
        near(ZoomMath.MAX_MAX_SCALE, ZoomMath.maxScale(99999, 1080f))
    }

    @Test
    fun `원본이 작은 그림도 맞춤의 두 배까지는 키운다`() {
        // 300 화소 아이콘을 1080 폭에 맞추면 이미 원본의 3.6배다. 규칙대로면 확대가 0 이 되고,
        // 두 번 두드려도 아무 일이 없으면 고장으로 읽힌다 — 하한이 그것을 막는다.
        near(ZoomMath.MIN_MAX_SCALE, ZoomMath.maxScale(300, 1080f))
        // 원본을 모르면(0) 하한.
        near(ZoomMath.MIN_MAX_SCALE, ZoomMath.maxScale(0, 1080f))
        near(ZoomMath.MIN_MAX_SCALE, ZoomMath.maxScale(4000, 0f))
    }

    @Test
    fun `예전 식은 줄여 뜬 비트맵을 원본으로 잘못 알았다`() {
        // 바닥층은 화면에 맞춰 줄여 뜬다(1080 폭). 그것을 원본 자리에 넣으면 12MP 사진도
        // 맞춤의 2배에서 멈췄다 — 원본의 18%. 원본(4000)을 넣어야 원본의 2배가 된다.
        near(2f, ZoomMath.maxScale(1080, 1080f))
        near(7.41f, ZoomMath.maxScale(4000, 1080f), tolerance = 0.01f)
    }

    @Test
    fun `보이는 자리는 바닥층 안의 비율이다`() {
        // 배율 1: 폭은 뷰포트에 꽉 차고(0~1), 세로는 바닥층(540)이 가운데라 전부 보인다.
        val whole = ZoomMath.visibleFraction(1f, 1080f, 2400f, 1080f, 540f, 0f, 0f)!!
        near(0f, whole[0]); near(1f, whole[2]); near(0f, whole[1]); near(1f, whole[3])
        // 2배로 가운데: 폭의 가운데 절반이 보인다.
        val mid = ZoomMath.visibleFraction(2f, 1080f, 2400f, 1080f, 540f, 0f, 0f)!!
        near(0.25f, mid[0]); near(0.75f, mid[2])
        // 오른쪽 끝까지 밀면(offset = -540) 오른쪽 절반이 보인다.
        val right = ZoomMath.visibleFraction(2f, 1080f, 2400f, 1080f, 540f, -540f, 0f)!!
        near(0.5f, right[0]); near(1f, right[2])
        // 잴 수 없으면 null.
        kotlin.test.assertNull(ZoomMath.visibleFraction(2f, 1080f, 2400f, 0f, 0f, 0f, 0f))
        kotlin.test.assertNull(ZoomMath.visibleFraction(Float.NaN, 1080f, 2400f, 1080f, 540f, 0f, 0f))
    }

    @Test
    fun `밀 수 있는지는 방향이 아니라 남은 여유로 본다`() {
        // 세로로만 여유가 있어도 참이다. 인식기가 주는 것은 이번 프레임의 델타라
        // 방향으로 판정하면 손가락이 떨릴 때마다 판정이 뒤집힌다.
        assertTrue(ZoomMath.canPan(0f, 300f))
        assertTrue(ZoomMath.canPan(300f, 0f))
        assertFalse(ZoomMath.canPan(0.2f, 0.3f))
    }
}
