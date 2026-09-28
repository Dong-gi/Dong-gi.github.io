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

    // ---- 여덟 방향 ----------------------------------------------------------------

    private val all = (1..8).toList()

    /**
     * **EXIF 명세의 문장에서 따로 끌어낸 오라클.** 명세는 방향마다 '저장된 0번째 행이 화면의 어느
     * 변인가, 0번째 열이 어느 변인가' 를 적는다. [RegionMath.displayOf] 의 식을 베끼지 않고 그 문장만으로
     * 화소가 갈 자리를 구한다 — 같은 식을 두 번 적어 대조하면 아무것도 증명하지 못한다.
     */
    private fun spec(o: Int, w: Int, h: Int, x: Int, y: Int): Pair<Int, Int> {
        val (row0, col0) = when (o) {
            2 -> "top" to "right"
            3 -> "bottom" to "right"
            4 -> "bottom" to "left"
            5 -> "left" to "top"
            6 -> "right" to "top"
            7 -> "right" to "bottom"
            8 -> "left" to "bottom"
            else -> "top" to "left"
        }
        return when (row0) {
            // 행이 위·아래로 놓인다 — 가로세로가 그대로다.
            "top", "bottom" -> {
                val dx = if (col0 == "left") x else w - 1 - x
                val dy = if (row0 == "top") y else h - 1 - y
                dx to dy
            }
            // 행이 왼쪽·오른쪽으로 놓인다 — 저장된 행 번호가 화면의 열 번호가 된다. 화면 폭은 h.
            else -> {
                val dx = if (row0 == "left") y else h - 1 - y
                val dy = if (col0 == "top") x else w - 1 - x
                dx to dy
            }
        }
    }

    @Test
    fun 여덟_방향_모두_화소가_명세의_자리로_간다() {
        val w = 7
        val h = 5
        for (o in all) for (y in 0 until h) for (x in 0 until w) {
            val d = RegionMath.displayOf(o, w, h, x, y)
            assertEquals(spec(o, w, h, x, y), d[0] to d[1], "방향 $o, 화소 ($x,$y)")
        }
    }

    @Test
    fun 여덟_방향_모두_화면의_한_화소가_저장_좌표의_그_화소로_간다() {
        // 7x5 의 모든 화소 — 네 귀퉁이만 보면 가운데를 가로지르는 부호 실수를 못 잡는다.
        val w = 7
        val h = 5
        for (o in all) for (y in 0 until h) for (x in 0 until w) {
            val (dx, dy) = spec(o, w, h, x, y)
            val stored = RegionMath.toStored(o, w, h, intArrayOf(dx, dy, dx + 1, dy + 1))
            assertContentEquals(intArrayOf(x, y, x + 1, y + 1), stored, "방향 $o, 화소 ($x,$y)")
            val back = RegionMath.toDisplay(o, w, h, intArrayOf(x, y, x + 1, y + 1))
            assertContentEquals(intArrayOf(dx, dy, dx + 1, dy + 1), back, "방향 $o 되돌림, 화소 ($x,$y)")
        }
    }

    @Test
    fun 여덟_방향_모두_사각형을_옮겼다_되돌리면_제자리다() {
        val rect = intArrayOf(5, 7, 23, 19)
        for (o in all + 0) {
            val w = 40
            val h = 30
            // 화면 방향 사각형은 화면 치수 안에 있어야 한다. 가로세로가 바뀌는 방향이면 화면은 30x40.
            val there = RegionMath.toStored(o, w, h, rect)
            assertContentEquals(rect, RegionMath.toDisplay(o, w, h, there), "방향 $o")
            assertTrue(there.all { it >= 0 } && there[2] > there[0] && there[3] > there[1], "방향 $o $there")
        }
    }

    /**
     * 조각을 돌리는 방법(시계 방향 회전 다음 거울)이 [RegionMath.displayOf] 와 같은 그림을 내는가.
     *
     * 화면은 이 둘을 `Matrix.postRotate` 와 `postScale(-1, 1)`·`postScale(1, -1)` 로 옮겨 적고
     * `Bitmap.createBitmap` 이 결과를 원점으로 당긴다. 그 두 동작을 **따로** 정수로 적어 차례대로
     * 입힌다 — 차례가 바뀌면 거울상 둘(5·7)이 서로 뒤바뀌는데, 이 시험이 그것을 잡는다.
     */
    @Test
    fun 조각을_돌리고_뒤집으면_명세의_그림이_된다() {
        val w = 6
        val h = 4
        for (o in all) for (y in 0 until h) for (x in 0 until w) {
            var cx = x
            var cy = y
            var cw = w
            var ch = h
            repeat(RegionMath.rotationDegrees(o) / 90) {
                // 시계 방향 90°: (x, y) → (H - 1 - y, x), 폭과 높이가 바뀐다.
                val nx = ch - 1 - cy
                cy = cx
                cx = nx
                val t = cw
                cw = ch
                ch = t
            }
            when (RegionMath.mirrorOf(o)) {
                RegionMath.Mirror.HORIZONTAL -> cx = cw - 1 - cx
                RegionMath.Mirror.VERTICAL -> cy = ch - 1 - cy
                RegionMath.Mirror.NONE -> Unit
            }
            assertEquals(spec(o, w, h, x, y), cx to cy, "방향 $o, 화소 ($x,$y)")
        }
    }

    @Test
    fun 화소_배열을_방향대로_옮긴다() {
        val w = 3
        val h = 2
        val px = IntArray(w * h) { it } // 값 = 저장 자리
        for (o in all) {
            val out = RegionMath.transformPixels(o, w, h, px)
            val ow = if (RegionMath.swapsAxes(o)) h else w
            for (y in 0 until h) for (x in 0 until w) {
                val (dx, dy) = spec(o, w, h, x, y)
                assertEquals(y * w + x, out[dy * ow + dx], "방향 $o, 화소 ($x,$y)")
            }
        }
    }

    @Test
    fun 조각은_바닥층을_따라_돈다() {
        // 바닥층이 방향을 적용했다 — 조각도 파일의 방향대로 돌려 얹는다. 여덟 방향 모두.
        for (o in all) assertEquals(o, RegionMath.tileOrientation(true, o), "방향 $o")
        // 바닥층이 무시했다(PNG) — 조각도 저장 방향 그대로. 돌리면 바닥층 위에 뒤집힌 조각이 얹힌다.
        for (o in all) assertEquals(RegionMath.NORMAL, RegionMath.tileOrientation(false, o), "방향 $o")
        // 모른다 — 조각을 뜨지 않는다(흐린 채로 확대된다).
        for (o in all) assertNull(RegionMath.tileOrientation(null, o), "방향 $o")
    }

    /**
     * 화면의 한 사각형을 **실제로 잘라 돌린** 조각이, 방향을 입힌 그림에서 같은 사각형을 잘라 낸 것과
     * 화소 하나까지 같은가. 좌표 옮기기(`toStored`)·잘라 오기·돌리고 뒤집기(`rotationDegrees`·`mirrorOf`)를
     * 화면이 부르는 차례 그대로 이어 붙인 것이다 — 따로따로 맞아도 이어 붙인 결과가 어긋나면 조각이 튄다.
     */
    @Test
    fun 여덟_방향_모두_잘라_돌린_조각이_화면의_그_자리다() {
        val w = 9
        val h = 6
        val stored = IntArray(w * h) { it }
        for (o in all) {
            val shown = RegionMath.transformPixels(o, w, h, stored)
            val sw = if (RegionMath.swapsAxes(o)) h else w
            val sh = if (RegionMath.swapsAxes(o)) w else h
            for (rect in listOf(intArrayOf(1, 2, 4, 5), intArrayOf(0, 0, sw, sh), intArrayOf(sw - 2, 1, sw, 3))) {
                val (l, t, r, b) = rect
                // 화면에서 그 자리
                val want = IntArray((r - l) * (b - t)) { i -> shown[(t + i / (r - l)) * sw + l + i % (r - l)] }
                // 저장 좌표로 옮겨 잘라 온 조각을 돌리고 뒤집는다
                val (sl, st, sr, sb) = RegionMath.toStored(o, w, h, rect)
                val pw = sr - sl
                val ph = sb - st
                val piece = IntArray(pw * ph) { i -> stored[(st + i / pw) * w + sl + i % pw] }
                var px = piece
                var cw = pw
                var ch = ph
                repeat(RegionMath.rotationDegrees(o) / 90) {
                    val turned = IntArray(px.size)
                    for (y in 0 until ch) for (x in 0 until cw) turned[x * ch + (ch - 1 - y)] = px[y * cw + x]
                    px = turned
                    val t2 = cw
                    cw = ch
                    ch = t2
                }
                px = when (RegionMath.mirrorOf(o)) {
                    RegionMath.Mirror.HORIZONTAL -> IntArray(px.size) { i -> px[(i / cw) * cw + (cw - 1 - i % cw)] }
                    RegionMath.Mirror.VERTICAL -> IntArray(px.size) { i -> px[(ch - 1 - i / cw) * cw + i % cw] }
                    RegionMath.Mirror.NONE -> px
                }
                assertEquals(r - l, cw, "방향 $o ${rect.toList()} 폭")
                assertContentEquals(want, px, "방향 $o ${rect.toList()}")
            }
        }
    }

    // ---- OrientationMatch ----------------------------------------------------------

    /** 위가 밝고 아래가 어둡고 왼쪽이 붉은 그림 — 180°·거울상 어느 쪽으로도 대칭이 아니다. */
    private fun asymmetric(w: Int, h: Int): IntArray = IntArray(w * h) { i ->
        val x = i % w
        val y = i / w
        val r = 255 - x * 255 / (w - 1)
        val g = 255 - y * 255 / (h - 1)
        val b = (x * y * 7) and 0xFF
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** 복호 경로가 달라 생기는 작은 잡음. 두 디코더는 같은 화소를 한두 단계 다르게 낸다. */
    private fun noisy(px: IntArray, amount: Int): IntArray = IntArray(px.size) { i ->
        val d = if (i % 2 == 0) amount else -amount
        val p = px[i]
        fun c(s: Int) = (((p shr s) and 0xFF) + d).coerceIn(0, 255)
        (0xFF shl 24) or (c(16) shl 16) or (c(8) shl 8) or c(0)
    }

    @Test
    fun 바닥층이_방향을_적용했으면_적용했다고_답한다() {
        val w = 32
        val h = 24
        val raw = asymmetric(w, h)
        for (o in listOf(2, 3, 4)) {
            val base = noisy(RegionMath.transformPixels(o, w, h, raw), 3)
            assertEquals(true, OrientationMatch.decide(o, base, raw, w, h), "방향 $o")
        }
        // 정사각이면 90°·거울 대각선도 치수가 그대로라 이 길로 온다.
        val sq = asymmetric(24, 24)
        for (o in listOf(5, 6, 7, 8)) {
            val base = noisy(RegionMath.transformPixels(o, 24, 24, sq), 3)
            assertEquals(true, OrientationMatch.decide(o, base, sq, 24, 24), "정사각 방향 $o")
        }
    }

    @Test
    fun 바닥층이_방향을_무시했으면_무시했다고_답한다() {
        // 안드로이드의 PNG 디코더는 EXIF 를 읽지 않는다(실측) — 바닥층이 저장 방향 그대로다.
        val w = 32
        val h = 24
        val raw = asymmetric(w, h)
        for (o in listOf(2, 3, 4)) {
            assertEquals(false, OrientationMatch.decide(o, noisy(raw, 3), raw, w, h), "방향 $o")
        }
    }

    @Test
    fun 대칭인_그림은_가르지_않는다() {
        // 한 가지 색이면 두 가설이 같은 그림을 낸다. 틀린 조각을 얹느니 흐린 채로 둔다.
        val flat = IntArray(32 * 24) { 0xFF808080.toInt() }
        for (o in listOf(2, 3, 4)) assertNull(OrientationMatch.decide(o, flat, flat, 32, 24), "방향 $o")
        // 좌우가 같은 그림은 좌우 거울(2)로 가를 수 없다 — 180°(3)로는 가를 수 있다.
        val w = 32
        val h = 24
        val mirrored = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            val v = minOf(x, w - 1 - x) * 16 + y * 4
            (0xFF shl 24) or (v.coerceAtMost(255) shl 8)
        }
        assertNull(OrientationMatch.decide(2, mirrored, mirrored, w, h))
        val turned = RegionMath.transformPixels(3, w, h, mirrored)
        assertEquals(true, OrientationMatch.decide(3, turned, mirrored, w, h))
    }

    @Test
    fun 어느_가설과도_닮지_않으면_가르지_않는다() {
        // 바닥층이 전혀 다른 그림이다(색 공간이 어긋났다 등) — 덜 틀린 쪽을 고르지 않는다.
        val w = 32
        val h = 24
        val raw = asymmetric(w, h)
        val other = IntArray(w * h) { i -> if ((i / 3) % 2 == 0) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        assertNull(OrientationMatch.decide(3, other, raw, w, h))
    }

    @Test
    fun 치수가_다른_입력은_가르지_않는다() {
        val raw = asymmetric(32, 24)
        assertNull(OrientationMatch.decide(3, raw.copyOf(10), raw, 32, 24))
        // 정사각이 아닌 90° 는 치수로 이미 갈렸다 — 이 판정이 답할 자리가 아니다.
        assertNull(OrientationMatch.decide(6, raw, raw, 32, 24))
        assertEquals(true, OrientationMatch.decide(1, raw, raw, 32, 24))
    }

    @Test
    fun 칸_평균으로_줄인다() {
        // 2x2 칸 넷: 검정·흰색이 반씩이면 회색 128 언저리.
        val px = intArrayOf(
            0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF101010.toInt(), 0xFF101010.toInt(),
            0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF101010.toInt(), 0xFF101010.toInt(),
        )
        val out = OrientationMatch.boxDownsample(px, 4, 2, 2)
        assertEquals(2, out.size)
        assertEquals(127, (out[0] shr 16) and 0xFF)
        assertEquals(0x10, out[1] and 0xFF)
        assertContentEquals(intArrayOf(32, 24), OrientationMatch.thumbSize(4000, 3000))
        assertContentEquals(intArrayOf(24, 32), OrientationMatch.thumbSize(3000, 4000))
        assertContentEquals(intArrayOf(32, 1), OrientationMatch.thumbSize(20000, 10))
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
