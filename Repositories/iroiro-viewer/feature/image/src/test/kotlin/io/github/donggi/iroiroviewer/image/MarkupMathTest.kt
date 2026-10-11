package io.github.donggi.iroiroviewer.image

import io.github.donggi.iroiroviewer.ui.gesture.ZoomMath
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 그리기·메모의 셈([MarkupView]·[MarkupMath]). 손가락으로 긋는 일은 기기에서 연결만 보고, 화면 ↔ 그림 좌표·굵기 환산·직선 붙이기·
 * 지우개의 맞힘은 여기서 답한다.
 */
class MarkupMathTest {

    private fun near(a: Float, b: Float, eps: Float = 1e-4f) = assertTrue(abs(a - b) <= eps, "$a ≈ $b")

    /** 1080×1800 뷰포트에 4000×3000 그림 — 맞춤 1080×810. */
    private val fitted = MarkupView(1080f, 1800f, 1080f, 810f, 1f, 0f, 0f)

    @Test
    fun `배율 1 에서 그림의 네 귀퉁이가 화면의 맞춤 상자에 온다`() {
        val top = (1800f - 810f) / 2f
        near(0f, fitted.imageX(0f))
        near(0f, fitted.imageY(top))
        near(1f, fitted.imageX(1080f))
        // 세로도 폭 단위다 — 높이는 3000/4000 = 0.75.
        near(0.75f, fitted.imageY(top + 810f))
        near(top, fitted.screenY(0f))
        near(1080f, fitted.screenX(1f))
    }

    @Test
    fun `화면과 그림 좌표는 확대·이동해도 서로 되돌아간다`() {
        val rnd = Random(7)
        repeat(2_000) {
            val v = MarkupView(
                viewportWidth = 1080f,
                viewportHeight = 1800f,
                fittedWidth = 1080f,
                fittedHeight = 810f,
                scale = 1f + rnd.nextFloat() * 7f,
                offsetX = (rnd.nextFloat() - 0.5f) * 4000f,
                offsetY = (rnd.nextFloat() - 0.5f) * 4000f,
            )
            val sx = rnd.nextFloat() * 1080f
            val sy = rnd.nextFloat() * 1800f
            near(sx, v.screenX(v.imageX(sx)), 0.01f)
            near(sy, v.screenY(v.imageY(sy)), 0.01f)
        }
    }

    /**
     * 그리는 화면의 변환은 뷰어의 확대·이동과 **같은 식**이어야 손가락과 선이 맞는다. 뷰어 쪽 셈(`ZoomMath.visibleFraction` — 화면의 두
     * 끝이 바닥층의 어디인가)과 견준다.
     */
    @Test
    fun `변환이 뷰어의 확대·이동과 같다`() {
        val v = MarkupView(1080f, 1800f, 1080f, 810f, 3f, -400f, 250f)
        val f = ZoomMath.visibleFraction(3f, 1080f, 1800f, 1080f, 810f, -400f, 250f)!!
        near(f[0], v.imageX(0f).coerceIn(0f, 1f))
        near(f[2], v.imageX(1080f).coerceIn(0f, 1f))
        // 세로는 폭 단위라 바닥층의 비율로 옮겨 견준다(× 폭/높이).
        near(f[1], (v.imageY(0f) * 1080f / 810f).coerceIn(0f, 1f))
        near(f[3], (v.imageY(1800f) * 1080f / 810f).coerceIn(0f, 1f))
    }

    @Test
    fun `보이는 굵기는 확대한 만큼 그림에서 가늘다`() {
        val one = fitted
        val four = fitted.copy(scale = 4f)
        // 화면 12화소 — 배율 1 이면 맞춤 폭(1080)의 12/1080, 4배면 그 넷째 몫.
        near(12f / 1080f, one.toImage(12f))
        near(12f / 1080f / 4f, four.toImage(12f))
        // 되돌리면 같은 화면 굵기다.
        near(12f, four.toScreen(four.toImage(12f)))
        // 원본 화소로는: 원본 폭 4000 이면 배율 1 의 12화소 = 원본 44.4화소, 4배면 11.1화소.
        near(44.444f, one.toImage(12f) * 4000f, 0.01f)
        near(11.111f, four.toImage(12f) * 4000f, 0.01f)
    }

    @Test
    fun `재지 않은 변환은 쓰지 않는다`() {
        assertFalse(fitted.copy(fittedWidth = 0f).valid)
        assertFalse(fitted.copy(scale = Float.NaN).valid)
        assertFalse(fitted.copy(offsetX = Float.POSITIVE_INFINITY).valid)
        assertTrue(fitted.valid)
    }

    @Test
    fun `직선은 가로·세로에 가까우면 붙는다`() {
        // 5° 기운 선 — 가로로 붙는다(끝의 x 는 그대로).
        val dx = 100f
        val dy5 = dx * kotlin.math.tan(Math.toRadians(5.0)).toFloat()
        assertContentEquals(floatArrayOf(110f, 50f), MarkupMath.snapLine(10f, 50f, 110f, 50f + dy5))
        // 7° 는 붙지 않는다.
        val dy7 = dx * kotlin.math.tan(Math.toRadians(7.0)).toFloat()
        assertContentEquals(floatArrayOf(110f, 50f + dy7), MarkupMath.snapLine(10f, 50f, 110f, 50f + dy7))
        // 세로에서 4° — 세로로 붙는다(끝의 y 는 그대로). 아래에서 위로 그어도 같다.
        val dx4 = 100f * kotlin.math.tan(Math.toRadians(4.0)).toFloat()
        assertContentEquals(floatArrayOf(10f, -50f), MarkupMath.snapLine(10f, 50f, 10f - dx4, -50f))
        // 대각선은 그대로.
        assertContentEquals(floatArrayOf(60f, 100f), MarkupMath.snapLine(10f, 50f, 60f, 100f))
        // 제자리 — 그대로.
        assertContentEquals(floatArrayOf(10f, 50f), MarkupMath.snapLine(10f, 50f, 10f, 50f))
        // 왼쪽으로 그은 가로선도 붙는다.
        assertContentEquals(floatArrayOf(-90f, 50f), MarkupMath.snapLine(10f, 50f, -90f, 50f - dy5))
    }

    @Test
    fun `가까운 점은 받지 않는다`() {
        assertFalse(MarkupMath.farEnough(0f, 0f, 0.5f, 0.5f, 1f))
        assertTrue(MarkupMath.farEnough(0f, 0f, 0.6f, 0.8f, 1f))
    }

    @Test
    fun `점과 선분 사이의 거리`() {
        near(1f, MarkupMath.distanceToSegment(5f, 1f, 0f, 0f, 10f, 0f))
        // 끝 너머는 끝점까지.
        near(5f, MarkupMath.distanceToSegment(13f, 4f, 0f, 0f, 10f, 0f))
        // 점으로 줄어든 선분.
        near(5f, MarkupMath.distanceToSegment(3f, 4f, 0f, 0f, 0f, 0f))
    }

    @Test
    fun `엇갈린 선분은 거리 0`() {
        near(0f, MarkupMath.segmentDistance(0f, 0f, 10f, 10f, 0f, 10f, 10f, 0f))
        near(2f, MarkupMath.segmentDistance(0f, 0f, 10f, 0f, 0f, 2f, 10f, 2f))
    }

    private fun stroke(width: Float, vararg xy: Float) = Stroke(1, StrokeKind.PEN, 0, width, xy)

    @Test
    fun `지우개는 획의 굵기와 제 반지름 안에 들어오면 맞힌다`() {
        val s = stroke(0.02f, 0.1f, 0.5f, 0.9f, 0.5f)
        // 획에서 0.03 떨어진 자리 — 반 굵기 0.01 + 반지름 0.025 = 0.035 안.
        assertTrue(MarkupMath.strokeHit(s, 0.5f, 0.53f, 0.5f, 0.53f, 0.025f))
        // 0.04 는 밖.
        assertFalse(MarkupMath.strokeHit(s, 0.5f, 0.54f, 0.5f, 0.54f, 0.025f))
    }

    /** 손가락이 빨리 지나가면 이벤트 사이가 멀다 — 점만 보면 가는 획을 건너뛴다. 지나간 선분으로 본다. */
    @Test
    fun `빨리 지나간 지우개도 가는 획을 놓치지 않는다`() {
        val s = stroke(0.002f, 0.5f, 0.1f, 0.5f, 0.9f)
        // 획의 왼쪽 0.2 에서 오른쪽 0.2 로 한 번에 — 두 점은 모두 멀다.
        assertTrue(MarkupMath.strokeHit(s, 0.3f, 0.5f, 0.7f, 0.5f, 0.005f))
        // 획 위를 지나지 않는 선분.
        assertFalse(MarkupMath.strokeHit(s, 0.3f, 0.95f, 0.7f, 0.95f, 0.005f))
    }

    @Test
    fun `점 하나짜리 획도 맞힌다`() {
        val dot = stroke(0.02f, 0.4f, 0.4f)
        assertTrue(MarkupMath.strokeHit(dot, 0.42f, 0.4f, 0.42f, 0.4f, 0.015f))
        assertFalse(MarkupMath.strokeHit(dot, 0.5f, 0.4f, 0.5f, 0.4f, 0.015f))
    }

    @Test
    fun `메모 상자는 안에 닿거나 가로질러도 맞힌다`() {
        // 상자 (0.2,0.2)-(0.4,0.3).
        assertTrue(MarkupMath.rectHit(0.2f, 0.2f, 0.4f, 0.3f, 0.3f, 0.25f, 0.3f, 0.25f, 0.01f))
        // 반지름만큼 넓혀 본다.
        assertTrue(MarkupMath.rectHit(0.2f, 0.2f, 0.4f, 0.3f, 0.405f, 0.25f, 0.405f, 0.25f, 0.01f))
        assertFalse(MarkupMath.rectHit(0.2f, 0.2f, 0.4f, 0.3f, 0.45f, 0.25f, 0.45f, 0.25f, 0.01f))
        // 두 끝은 밖인데 상자를 가로지른 선분.
        assertTrue(MarkupMath.rectHit(0.2f, 0.2f, 0.4f, 0.3f, 0.1f, 0.25f, 0.5f, 0.25f, 0.001f))
        assertFalse(MarkupMath.rectHit(0.2f, 0.2f, 0.4f, 0.3f, 0.1f, 0.35f, 0.5f, 0.35f, 0.001f))
    }

    @Test
    fun `메모 상자는 그림 안에 든다`() {
        // 오른쪽 끝을 넘는 상자는 왼쪽으로 민다.
        val left = MarkupMath.clampBox(0.9f, 0.1f, 0.3f, 0.1f, 0.75f)
        near(0.7f, left[0])
        near(0.1f, left[1])
        // 아래 끝을 넘는 상자는 위로.
        val up = MarkupMath.clampBox(0.1f, 0.7f, 0.2f, 0.1f, 0.75f)
        near(0.1f, up[0])
        near(0.65f, up[1])
        // 음수 자리는 0 으로.
        assertContentEquals(floatArrayOf(0f, 0f), MarkupMath.clampBox(-0.2f, -0.1f, 0.2f, 0.1f, 0.75f))
        // 그림보다 넓은 상자는 왼쪽(위)에 붙인다 — 글의 처음이 보인다.
        assertContentEquals(floatArrayOf(0f, 0f), MarkupMath.clampBox(0.5f, 0.5f, 1.5f, 0.9f, 0.75f))
    }

    @Test
    fun `밝은 글에는 어두운 상자`() {
        assertTrue(MarkupMath.isLight(MarkupColor.WHITE.argb))
        assertTrue(MarkupMath.isLight(MarkupColor.YELLOW.argb))
        assertFalse(MarkupMath.isLight(MarkupColor.BLACK.argb))
        assertFalse(MarkupMath.isLight(MarkupColor.RED.argb))
        assertFalse(MarkupMath.isLight(MarkupColor.BLUE.argb))
    }

    @Test
    fun `메모는 글자 크기에 비례해 줄을 바꾸고 그림 폭을 넘지 않는다`() {
        near(0.02f * MarkupMath.WRAP_EM, MarkupMath.noteWrapWidth(0.02f))
        assertEquals(MarkupMath.WRAP_MAX, MarkupMath.noteWrapWidth(0.1f))
        // 글자가 줄 폭보다 크면 한 글자는 들어가게.
        assertEquals(1.2f, MarkupMath.noteWrapWidth(1.2f))
    }
}
