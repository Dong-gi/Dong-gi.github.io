package io.github.donggi.iroiroviewer.ui.image

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 사진·만화의 선명화 조각이 **원본의 어디를** 뜨는가.
 *
 * 틀리면 확대한 자리에 엉뚱한 그림이 얹히거나 조각이 누운 채로 얹힌다. 그것을 화면 캡처로
 * 판정하려 들면 6단계처럼 틀린 결론을 낸다 — 좌표는 시험이 답한다.
 */
class RasterDetailTest {

    // ---- RegionMath ------------------------------------------------------------

    /**
     * 화소 하나를 실제로 돌려 본다. 저장 좌표 (x, y) 의 **한 화소짜리 사각형**을 화면으로
     * 옮긴 자리가, 그림을 그 방향으로 돌렸을 때 그 화소가 가는 자리와 같아야 한다.
     */
    private fun pixelGoesTo(orientation: Int, w: Int, h: Int, x: Int, y: Int): Pair<Int, Int> =
        when (orientation) {
            // 시계 방향 90°: 새 폭은 h, (x, y) → (h - 1 - y, x)
            RegionMath.ROTATE_90 -> (h - 1 - y) to x
            // 반시계 90°: (x, y) → (y, w - 1 - x)
            RegionMath.ROTATE_270 -> y to (w - 1 - x)
            else -> x to y
        }

    @Test
    fun 화면의_한_화소가_저장_좌표의_그_화소로_간다() {
        val w = 40
        val h = 30
        for (o in listOf(RegionMath.NORMAL, RegionMath.ROTATE_90, RegionMath.ROTATE_270)) {
            for ((x, y) in listOf(0 to 0, 39 to 0, 0 to 29, 39 to 29, 17 to 11)) {
                val (dx, dy) = pixelGoesTo(o, w, h, x, y)
                val stored = RegionMath.toStored(o, w, h, intArrayOf(dx, dy, dx + 1, dy + 1))
                assertContentEquals(intArrayOf(x, y, x + 1, y + 1), stored, "방향 $o, 화소 ($x,$y)")
            }
        }
    }

    @Test
    fun 옮겼다_되돌리면_제자리다() {
        val rect = intArrayOf(5, 7, 23, 19)
        for (o in listOf(RegionMath.NORMAL, RegionMath.ROTATE_90, RegionMath.ROTATE_270)) {
            val there = RegionMath.toStored(o, 40, 30, rect)
            assertContentEquals(rect, RegionMath.toDisplay(o, 40, 30, there), "방향 $o")
        }
    }

    @Test
    fun 돌린_그림의_사각형은_가로세로가_바뀐다() {
        // 화면에서 가로로 긴 조각(20x4)은 90° 누운 파일에서는 세로로 긴 조각(4x20)이다.
        val s = RegionMath.toStored(RegionMath.ROTATE_90, 40, 30, intArrayOf(0, 0, 20, 4))
        assertEquals(4, s[2] - s[0])
        assertEquals(20, s[3] - s[1])
        assertEquals(90, RegionMath.rotationDegrees(RegionMath.ROTATE_90))
        assertEquals(270, RegionMath.rotationDegrees(RegionMath.ROTATE_270))
        assertEquals(0, RegionMath.rotationDegrees(RegionMath.NORMAL))
        assertEquals(0, RegionMath.rotationDegrees(RegionMath.UNDEFINED))
    }

    // ---- RasterDetail.want -------------------------------------------------------

    /** 폰(1080x2400)에 4000x3000 사진. 바닥층은 1080x810, 조각 예산은 8.4 MiB. */
    private fun want(zoom: Float, offsetX: Float = 0f, offsetY: Float = 0f, cap: Long = 8_859_648) =
        RasterDetail.want(
            zoom = zoom,
            viewportWidth = 1080f,
            viewportHeight = 2400f,
            fittedWidth = 1080f,
            fittedHeight = 810f,
            offsetX = offsetX,
            offsetY = offsetY,
            baseWidth = 1080,
            originalWidth = 4000,
            originalHeight = 3000,
            capBytes = cap,
        )

    @Test
    fun 바닥층이_아직_제_해상도면_뜨지_않는다() {
        assertNull(want(1f))
        assertNull(want(1.1f))
    }

    @Test
    fun 확대한_자리를_원본_화소로_뜬다() {
        val w = assertNotNull(want(4f))
        // 4배면 폭의 1/4 이 보인다. 가운데이므로 원본 1500~2500 언저리(눈금으로 바깥쪽 반올림).
        assertTrue(w.left <= 1500 && w.right >= 2500, "$w")
        assertTrue(w.right - w.left <= 1000 + 2 * 4000 / RasterDetail.GRID + 1, "너무 넓게 뜬다 $w")
        // 세로: 바닥층(810)을 4배 하면 3240 이라 화면(2400)을 넘는다. 가운데 74% 가 보인다
        // — 원본 390~2610. 눈금으로 바깥쪽 반올림하므로 그보다 조금 넓다.
        assertTrue(w.top in 300..390 && w.bottom in 2610..2700, "$w")
    }

    @Test
    fun 조각은_예산을_넘지_않는다() {
        for (z in listOf(1.5f, 2f, 3f, 5f, 7.4f)) {
            for (ox in listOf(-3000f, 0f, 3000f)) {
                val w = want(z, ox) ?: continue
                val w2 = (w.width + w.sample - 1) / w.sample
                val h2 = (w.height + w.sample - 1) / w.sample
                assertTrue(w2.toLong() * h2 * 4 <= 8_859_648, "배율 $z 에서 예산을 넘었다 $w")
                assertTrue(w.left >= 0 && w.top >= 0 && w.right <= 4000 && w.bottom <= 3000, "$w")
            }
        }
    }

    @Test
    fun 조각이_바닥층보다_선명하지_않으면_뜨지_않는다() {
        // 예산이 너무 작아 표본이 커지면 바닥층(1080 폭)보다 흐려진다 — 뜰 이유가 없다.
        assertNull(want(4f, cap = 64L * 64 * 4))
    }

    @Test
    fun 원본이_바닥층보다_크지_않으면_뜨지_않는다() {
        val w = RasterDetail.want(
            zoom = 2f, viewportWidth = 1080f, viewportHeight = 2400f,
            fittedWidth = 1080f, fittedHeight = 810f, offsetX = 0f, offsetY = 0f,
            baseWidth = 800, originalWidth = 800, originalHeight = 600, capBytes = 8_859_648,
        )
        assertNull(w, "작은 그림은 바닥층이 곧 원본이다")
    }

    @Test
    fun 조금_밀어도_같은_요청이다() {
        // 눈금이 없으면 손가락이 1화소 움직일 때마다 원본을 다시 읽는다.
        // (0 은 눈금 위에 정확히 놓여 1화소만 밀어도 칸이 바뀐다 — 칸 안쪽 자리에서 잰다.)
        assertEquals(want(4f, 10f), want(4f, 12f))
    }
}
