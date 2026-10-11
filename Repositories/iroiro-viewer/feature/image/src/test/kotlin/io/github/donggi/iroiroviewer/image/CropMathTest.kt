package io.github.donggi.iroiroviewer.image

import io.github.donggi.iroiroviewer.image.CropMath.Box
import io.github.donggi.iroiroviewer.image.CropMath.Grip
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 잘라 낼 사각형의 셈([CropMath]). 손가락으로 끄는 일은 기기에서 연결만 보고, 셈은 여기서 답한다.
 */
class CropMathTest {

    private val w = 4000f
    private val h = 3000f
    private val box = Box(1000f, 1000f, 3000f, 2000f)

    private fun near(a: Float, b: Float) = assertTrue(abs(a - b) < 0.5f, "$a ≈ $b")

    @Test
    fun `모서리가 변보다 먼저 잡히고 안쪽은 옮기기`() {
        assertEquals(Grip.TOP_LEFT, CropMath.grip(box, 1010f, 990f, 50f))
        assertEquals(Grip.BOTTOM_RIGHT, CropMath.grip(box, 2990f, 2030f, 50f))
        assertEquals(Grip.LEFT, CropMath.grip(box, 1020f, 1500f, 50f))
        assertEquals(Grip.BOTTOM, CropMath.grip(box, 2000f, 2040f, 50f))
        assertEquals(Grip.MOVE, CropMath.grip(box, 2000f, 1500f, 50f))
        assertNull(CropMath.grip(box, 500f, 500f, 50f))
    }

    /** 손가락 두 개보다 작은 사각형도 가운데를 끌면 옮겨진다 — 처음 판은 안쪽 모든 점이 변의 손잡이라 크기가 바뀌었다. */
    @Test
    fun `작은 사각형의 가운데는 옮기기다`() {
        val small = Box(1000f, 1000f, 1040f, 1040f)
        assertEquals(Grip.MOVE, CropMath.grip(small, 1020f, 1020f, 50f))
        // 변 가까이(안쪽 넷째 몫)와 바깥 손잡이는 그대로 변이다.
        assertEquals(Grip.LEFT, CropMath.grip(small, 1003f, 1020f, 50f))
        assertEquals(Grip.RIGHT, CropMath.grip(small, 1080f, 1020f, 50f))
        assertEquals(Grip.TOP_LEFT, CropMath.grip(small, 960f, 960f, 50f))
    }

    @Test
    fun `손가락보다 좁은 사각형은 더 가까운 변을 잡는다`() {
        val thin = Box(1000f, 1000f, 1040f, 2000f)
        assertEquals(Grip.RIGHT, CropMath.grip(thin, 1035f, 1500f, 50f))
        assertEquals(Grip.LEFT, CropMath.grip(thin, 1005f, 1500f, 50f))
    }

    @Test
    fun `자유롭게 끌면 그림 밖으로 나가지 않고 최소 크기를 지킨다`() {
        val out = CropMath.drag(box, Grip.TOP_LEFT, -5000f, -5000f, w, h, null, 100f)
        assertEquals(Box(0f, 0f, 3000f, 2000f), out)
        val tiny = CropMath.drag(box, Grip.RIGHT, -5000f, 0f, w, h, null, 100f)
        assertEquals(1100f, tiny.right)
        val moved = CropMath.drag(box, Grip.MOVE, 5000f, 5000f, w, h, null, 100f)
        assertEquals(Box(2000f, 2000f, 4000f, 3000f), moved)
    }

    @Test
    fun `비율을 고정한 모서리는 맞은편을 붙박고 비율을 지킨다`() {
        // 2:1 사각형의 오른쪽 아래를 끈다 — 비율 2.
        val out = CropMath.drag(box, Grip.BOTTOM_RIGHT, 400f, 0f, w, h, 2f, 100f)
        assertEquals(1000f, out.left)
        assertEquals(1000f, out.top)
        near(out.width / out.height, 2f)
        near(out.width, 2400f)
        // 그림 끝까지 끌면 남은 자리에 맞춰 멈춘다(오른쪽 3000, 아래 2000 이 남는다 → 폭 3000, 높이 1500).
        val edge = CropMath.drag(box, Grip.BOTTOM_RIGHT, 9000f, 9000f, w, h, 2f, 100f)
        near(edge.right, 4000f)
        near(edge.width / edge.height, 2f)
        assertTrue(edge.bottom <= h + 0.01f)
    }

    @Test
    fun `비율을 고정한 변은 가로지르는 축의 가운데를 지키고 가장자리에서 밀린다`() {
        val out = CropMath.drag(box, Grip.RIGHT, 1000f, 0f, w, h, 2f, 100f)
        near(out.width, 3000f)
        near(out.height, 1500f)
        near(out.centerY, 1500f)
        // 위쪽 가장자리 가까이 있는 사각형은 아래로 밀려난다.
        val top = Box(1000f, 0f, 3000f, 1000f)
        val pushed = CropMath.drag(top, Grip.RIGHT, 1000f, 0f, w, h, 2f, 100f)
        assertEquals(0f, pushed.top)
        near(pushed.height, 1500f)
    }

    @Test
    fun `비율을 바꾸면 넓이를 지키며 그림 안에 넣는다`() {
        val sq = CropMath.withRatio(box, 1f, w, h)
        near(sq.width, sq.height)
        // 넓이는 수백만이라 부동소수 오차가 0.5 를 넘는다 — 비율로 견준다.
        assertTrue(abs(sq.width * sq.height / (box.width * box.height) - 1f) < 1e-4f)
        near(sq.centerX, box.centerX)
        // 그림 전체에서 1:1 로 — 넓이를 지키면 넘치므로 짧은 변에 맞춘다.
        val full = CropMath.withRatio(CropMath.full(w, h), 1f, w, h)
        near(full.width, 3000f)
        near(full.height, 3000f)
        assertTrue(full.left >= 0f && full.right <= w)
    }

    @Test
    fun `화소로는 반올림하고 적어도 한 화소`() {
        assertContentEquals(intArrayOf(10, 20, 31, 41), CropMath.toPixels(Box(10.4f, 19.6f, 30.5f, 40.5f), 100, 100))
        assertContentEquals(intArrayOf(99, 99, 100, 100), CropMath.toPixels(Box(99.9f, 99.9f, 99.9f, 99.9f), 100, 100))
    }

    /**
     * 앱이 죽던 자리 — 2560×1600 그림을 4:3 으로 고르고 옆 변을 끝까지 끌면 폭이 `h × 4/3`(2133.3335)에 막히고 높이가
     * `2133.3335 / 1.3333334 = 1600.0001` 이 되어, 가운데를 지키는 `coerceIn(0, h - 높이)` 의 범위가 비었다
     * (`IllegalArgumentException` — 앱이 죽는다).
     */
    @Test
    fun `4대3 으로 옆 변을 끝까지 끌어도 죽지 않고 그림 안에 남는다`() {
        val gw = 2560f
        val gh = 1600f
        val ratio = 4f / 3f
        val start = CropMath.withRatio(CropMath.full(gw, gh), ratio, gw, gh)
        for (grip in Grip.entries) {
            for (d in floatArrayOf(-5000f, -700f, 700f, 5000f)) {
                val out = CropMath.drag(start, grip, d, d, gw, gh, ratio, 48f)
                assertInside(out, gw, gh)
            }
        }
        val wide = CropMath.drag(start, Grip.RIGHT, 5000f, 0f, gw, gh, ratio, 48f)
        near(wide.height, gh)
        near(wide.width / wide.height, ratio)
    }

    /** 비율을 바꿔 넓이를 지키면 한 변이 최소보다 짧아질 수 있다 — 그 사각형을 자유 모드에서 잡아도 죽지 않는다. */
    @Test
    fun `최소보다 작아진 사각형을 자유롭게 끌어도 죽지 않는다`() {
        // 40×40 을 16:9 로 — 53×30 이 그림 위쪽 끝(5~35)에 선다. 아래 변이 최소(48)보다 위라 옛 셈은 위 변의 범위
        // `[0, 아래 - 최소]` 가 비어 죽었다.
        val small = CropMath.withRatio(Box(0f, 0f, 40f, 40f), 16f / 9f, w, h)
        assertTrue(small.height < 48f && small.bottom < 48f)
        for (grip in Grip.entries) {
            for (d in floatArrayOf(-200f, -10f, 10f, 200f)) {
                assertInside(CropMath.drag(small, grip, d, d, w, h, null, 48f), w, h)
            }
        }
    }

    /**
     * 무작위로 — 그림 크기·비율·사각형·잡은 자리·끄는 양을 흩어 2만 번 끈다(끈 결과에서 다시 끈다). 던지지 않고, 언제나 그림 안이며,
     * 오른쪽이 왼쪽보다 앞서지 않는다. 씨앗을 고정해 늘 같은 순서로 돈다.
     */
    @Test
    fun `무작위로 끌어도 언제나 그림 안이다`() {
        val rnd = kotlin.random.Random(20261004)
        repeat(1000) {
            val gw = (1 + rnd.nextInt(6000)).toFloat()
            val gh = (1 + rnd.nextInt(6000)).toFloat()
            val ratio = when (rnd.nextInt(7)) {
                0 -> null
                1 -> 1f
                2 -> 4f / 3f
                3 -> 3f / 4f
                4 -> 16f / 9f
                5 -> 9f / 16f
                else -> gw / gh
            }
            val min = (1 + rnd.nextInt(200)).toFloat()
            var b = CropMath.full(gw, gh)
            if (ratio != null) b = CropMath.withRatio(b, ratio, gw, gh)
            repeat(20) {
                val grip = Grip.entries[rnd.nextInt(Grip.entries.size)]
                val dx = (rnd.nextFloat() * 4 - 2) * gw
                val dy = (rnd.nextFloat() * 4 - 2) * gh
                b = CropMath.drag(b, grip, dx, dy, gw, gh, ratio, min)
                assertInside(b, gw, gh)
                if (rnd.nextInt(5) == 0 && ratio != null) {
                    b = CropMath.withRatio(b, ratio, gw, gh)
                    assertInside(b, gw, gh)
                }
            }
            CropMath.toPixels(b, gw.toInt(), gh.toInt())
        }
    }

    private fun assertInside(b: Box, gw: Float, gh: Float) {
        assertTrue(b.left >= 0f && b.top >= 0f && b.right <= gw && b.bottom <= gh, "그림 밖: $b ($gw×$gh)")
        assertTrue(b.right >= b.left && b.bottom >= b.top, "뒤집힘: $b")
    }

    @Test
    fun `잘라 저장의 표본은 화소 상한에 드는 가장 작은 2의 거듭제곱`() {
        assertEquals(1, ImageCropRenderer.sampleFor(12_000_000, 16_000_000))
        assertEquals(2, ImageCropRenderer.sampleFor(48_000_000, 16_000_000))
        assertEquals(4, ImageCropRenderer.sampleFor(200_000_000, 16_000_000))
    }
}
