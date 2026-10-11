package io.github.donggi.iroiroviewer.safety

import kotlin.math.abs
import kotlin.math.floor
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * PDF 쪽을 그릴 크기를 정하는 계산.
 *
 * **값이 틀리면 결과가 `OutOfMemoryError` 이거나 `Canvas` 의 거부다.** 그것을 에뮬레이터에서
 * 재현해 고치는 일은 느리고 불확실하므로 여기서 초 단위로 박는다 — `ImageLimits` 가 같은
 * 이유로 순수 함수가 된 그대로다.
 */
class PdfLimitsTest {

    private val A4 = 595 to 842
    private val LETTER_LANDSCAPE = 792 to 612

    @Test
    fun 맞춤_배율이_종횡비를_지킨다() {
        // 세로 뷰포트에 세로 쪽 / 가로 쪽, 가로 뷰포트에 각각 넣어도 비가 유지된다.
        for ((w, h) in listOf(A4, LETTER_LANDSCAPE, 500 to 500)) {
            for ((vw, vh) in listOf(1080 to 2400, 2400 to 1080)) {
                val s = PdfLimits.fitScale(w, h, vw, vh)
                val bmp = assertNotNull(PdfLimits.pageBitmap(w, h, s, 100L * 1024 * 1024))
                val pageRatio = w.toDouble() / h
                val bmpRatio = bmp.width.toDouble() / bmp.height
                assertTrue(
                    abs(pageRatio - bmpRatio) < 0.01,
                    "쪽 ${w}x$h 를 ${vw}x$vh 에 맞췄더니 비가 $pageRatio → $bmpRatio",
                )
                assertTrue(bmp.width <= vw && bmp.height <= vh, "맞춤인데 뷰포트를 넘었다")
            }
        }
    }

    @Test
    fun 비트맵이_예산을_절대_넘지_않는다() {
        // 내림 덕에 성립하는 성질이라 경계에서 깨지기 쉽다 — 무작위로 넓게 훑는다.
        val rnd = Random(20261119)
        repeat(1000) {
            val w = rnd.nextInt(1, 20000)
            val h = rnd.nextInt(1, 20000)
            val cap = rnd.nextLong(1024, 40L * 1024 * 1024)
            val want = rnd.nextDouble(0.01, 20.0)
            val bmp = PdfLimits.pageBitmap(w, h, want, cap) ?: return@repeat
            val bytes = PdfLimits.bytesOf(bmp.width, bmp.height)
            assertTrue(bytes <= cap, "쪽 ${w}x$h 배율 $want 예산 $cap 인데 $bytes 바이트")
        }
    }

    @Test
    fun 거대한_쪽도_절대_상한_안으로_줄어든다() {
        // 14400pt = 200인치. PDF 명세가 허락하는 정상 쪽이다.
        val bmp = assertNotNull(PdfLimits.pageBitmap(14400, 14400, 4.0, Long.MAX_VALUE))
        assertTrue(
            PdfLimits.bytesOf(bmp.width, bmp.height) <= ImageLimits.MAX_BITMAP_BYTES,
            "예산을 무한히 줘도 절대 상한은 남아야 한다",
        )
    }

    @Test
    fun 예산이_0이하여도_죽지_않는다() {
        // 뷰포트를 아직 모르는 첫 프레임이 이 길로 온다.
        val bmp = assertNotNull(PdfLimits.pageBitmap(595, 842, 1.0, 0))
        assertTrue(bmp.width >= 1 && bmp.height >= 1)
        assertTrue(PdfLimits.bytesOf(bmp.width, bmp.height) <= ImageLimits.MAX_BITMAP_BYTES)
        assertNotNull(PdfLimits.pageBitmap(595, 842, 1.0, -5))
    }

    @Test
    fun 쪽_크기가_0이거나_음수면_그리지_않는다() {
        for ((w, h) in listOf(0 to 800, 800 to 0, -1 to 100, 0 to 0)) {
            assertFalse(PdfLimits.isDrawablePage(w, h), "${w}x$h")
            assertNull(PdfLimits.pageBitmap(w, h, 1.0, 1L shl 20))
        }
        assertTrue(PdfLimits.isDrawablePage(1, 1))
    }

    @Test
    fun 이상한_배율은_null_이다() {
        assertNull(PdfLimits.pageBitmap(595, 842, 0.0, 1L shl 20))
        assertNull(PdfLimits.pageBitmap(595, 842, -1.0, 1L shl 20))
        assertNull(PdfLimits.pageBitmap(595, 842, Double.NaN, 1L shl 20))
        assertNull(PdfLimits.pageBitmap(595, 842, Double.POSITIVE_INFINITY, 1L shl 20))
    }

    @Test
    fun 선명화_예산이_0이면_선명화를_켜지_않는다() {
        // 힙이 작은 기기에서 `budgetOf` 가 detailCap = 0 을 준다. 저절로 꺼지는 것이 설계 의도다.
        val fit = PdfLimits.fitScale(595, 842, 1080, 2400)
        assertNull(
            PdfLimits.tileFor(595, 842, fit * 3, 1080, 2400, 0.5, 0.5, capBytes = 0, maxScale = fit * 8),
            "예산이 없으면 타일을 뜨지 않는다",
        )
    }

    @Test
    fun 타일이_쪽_밖으로_나가지_않는다() {
        val fit = PdfLimits.fitScale(595, 842, 1080, 2400)
        val cap = 8L * 1024 * 1024
        for (fx in listOf(-1.0, 0.0, 0.5, 1.0, 2.0)) {
            for (fy in listOf(-1.0, 0.0, 0.5, 1.0, 2.0)) {
                val t = assertNotNull(PdfLimits.tileFor(595, 842, fit * 3, 1080, 2400, fx, fy, cap, fit * 8))
                // **쪽 크기를 여기서 다시 구하지 않는다.** 타일이 싣고 오는 값을 그대로
                // 쓴다 — 시험이 자기 식으로 다시 구하면 화면도 그럴 것이고, 그 순간
                // 계산이 두 벌이 된다.
                assertEquals(floor(595 * t.scale).toInt(), t.pageWidth, "쪽 폭이 내림이 아니다 $t")
                assertEquals(floor(842 * t.scale).toInt(), t.pageHeight, "쪽 높이가 내림이 아니다 $t")
                assertTrue(t.srcLeft >= 0 && t.srcTop >= 0, "음수 좌표 $t")
                assertTrue(t.srcLeft + t.width <= t.pageWidth, "오른쪽으로 넘쳤다 $t")
                assertTrue(t.srcTop + t.height <= t.pageHeight, "아래로 넘쳤다 $t")
                assertTrue(PdfLimits.bytesOf(t.width, t.height) <= cap, "타일이 예산을 넘었다 $t")
            }
        }
    }

    @Test
    fun 확대하지_않았으면_타일을_뜨지_않는다() {
        val fit = PdfLimits.fitScale(595, 842, 1080, 2400)
        assertFalse(PdfLimits.needsDetail(fit, fit))
        assertFalse(PdfLimits.needsDetail(fit * 0.5, fit))
        assertNull(PdfLimits.tileFor(595, 842, fit, 1080, 2400, 0.5, 0.5, 8L * 1024 * 1024, fit * 8))
        assertTrue(PdfLimits.needsDetail(fit * 2, fit))
    }

    @Test
    fun 선명화는_화면이_허락하는_최대_확대에서_멈춘다() {
        // 확대가 원본의 2배에서 멈추므로(`ZoomMath.maxScale`) 그 위를 그릴 이유가 없다.
        val fit = PdfLimits.fitScale(595, 842, 1080, 2400)
        val ceiling = fit * 6.4
        val t = assertNotNull(
            PdfLimits.tileFor(595, 842, fit * 50, 1080, 2400, 0.5, 0.5, 32L * 1024 * 1024, ceiling)
        )
        assertEquals(ceiling, t.scale, 1e-9, "상한에 맞춰야 한다")
        // 상한이 문턱보다 낮으면(확대가 막힌 쪽) 타일을 아예 뜨지 않는다.
        assertNull(PdfLimits.tileFor(595, 842, fit * 3, 1080, 2400, 0.5, 0.5, 32L * 1024 * 1024, fit))
    }

    @Test
    fun 좌표가_Int_를_넘지_않는다() {
        // 퇴화한 입력(무한에 가까운 상한)에서도 쪽 좌표가 음수로 넘어가지 않는다.
        val t = assertNotNull(
            PdfLimits.tileFor(14400, 14400, 1e9, 1080, 2400, 1.0, 1.0, 32L * 1024 * 1024, 1e9)
        )
        assertTrue(t.pageWidth > 0 && t.pageHeight > 0 && t.srcLeft >= 0 && t.srcTop >= 0, "$t")
    }

    @Test
    fun 원본_크기는_종이의_실제_크기다() {
        // A4 폭 595pt = 8.26인치. 420dpi 화면에서 3470화소.
        assertEquals(595 * 420 / 72, PdfLimits.originalWidthPx(595, 420))
        assertEquals(0, PdfLimits.originalWidthPx(0, 420))
        assertEquals(0, PdfLimits.originalWidthPx(595, 0))
    }

    @Test
    fun 배율이_연속이다() {
        // `ImageLimits.sampleForBudget` 은 2의 거듭제곱이라 최대 4배 흐려진다.
        // PDF 는 그 손해를 볼 이유가 없다는 것이 이 파일이 따로 있는 이유다.
        val bmp = assertNotNull(PdfLimits.pageBitmap(595, 842, 1.7, 100L * 1024 * 1024))
        assertEquals(1.7, bmp.scale, 1e-9)
        assertEquals((595 * 1.7).toInt(), bmp.width)
    }

    // ---- 이어 보기의 보이는 자리 타일 ----

    @Test
    fun 이어_보기는_바닥층이_이미_그_해상도면_다시_그리지_않는다() {
        // 폰 세로에서 A4 를 폭에 맞춘 배율 그대로 보는 중 — 바닥층이 이미 그 배율이다.
        val base = 1080.0 / 595
        assertNull(PdfLimits.regionTile(595, 842, base, base, 0.0, 0.0, 1.0, 0.6, 8L * 1024 * 1024, base * 6))
    }

    @Test
    fun 이어_보기는_보이는_자리만_뜬다() {
        // 2배로 키우고 쪽의 오른쪽 아래 1/4 이 보인다.
        val base = 1080.0 / 595
        val t = assertNotNull(
            PdfLimits.regionTile(595, 842, base * 2, base, 0.5, 0.5, 1.0, 1.0, 64L * 1024 * 1024, base * 6),
        )
        assertEquals(t.pageWidth / 2, t.srcLeft, "보이는 자리의 왼쪽 = 쪽 가운데")
        assertEquals(t.pageHeight / 2, t.srcTop)
        assertTrue(t.srcLeft + t.width <= t.pageWidth && t.srcTop + t.height <= t.pageHeight, "$t")
        // 쪽 넘김의 타일처럼 화면 한 장만큼이 아니라 보이는 자리만큼이다(쪽의 오른쪽 아래 1/4 — 예산 안이라 줄지 않는다).
        assertEquals(t.pageWidth - t.pageWidth / 2, t.width)
        assertEquals(t.pageHeight - t.pageHeight / 2, t.height)
    }

    @Test
    fun 이어_보기의_타일은_나눠_받은_예산을_넘지_않는다() {
        val base = 1080.0 / 595
        val random = Random(7)
        repeat(500) {
            val cap = 1L + random.nextLong(16L * 1024 * 1024)
            val l = random.nextDouble(0.0, 0.9)
            val tp = random.nextDouble(0.0, 0.9)
            val t = PdfLimits.regionTile(
                595, 842, base * random.nextDouble(1.3, 6.0), base,
                l, tp, l + random.nextDouble(0.01, 1.0), tp + random.nextDouble(0.01, 1.0), cap, base * 6,
            ) ?: return@repeat
            assertTrue(PdfLimits.bytesOf(t.width, t.height) <= maxOf(cap, 4L), "cap=$cap $t")
            assertTrue(t.srcLeft >= 0 && t.srcTop >= 0 && t.srcLeft + t.width <= t.pageWidth && t.srcTop + t.height <= t.pageHeight, "$t")
        }
    }

    @Test
    fun 예산보다_큰_자리는_가운데를_남긴다() {
        // 가로 화면에 A4 세로를 폭에 맞췄는데 바닥층이 상한 때문에 낮은 배율로 그려졌다 — 배율 1 에서도 다시 그린다.
        val wanted = 2400.0 / 595
        val base = wanted * 0.56
        val t = assertNotNull(PdfLimits.regionTile(595, 842, wanted, base, 0.0, 0.3, 1.0, 0.6, 4L * 1024 * 1024, wanted * 2))
        assertTrue(PdfLimits.bytesOf(t.width, t.height) <= 4L * 1024 * 1024)
        val visibleLeft = 0
        val visibleRight = t.pageWidth
        val left = t.srcLeft - visibleLeft
        val right = visibleRight - (t.srcLeft + t.width)
        assertTrue(abs(left - right) <= 1, "좌우 여백이 같아야 한다: $left / $right")
    }

    @Test
    fun 이어_보기도_최대_확대에서_멈춘다() {
        val base = 1080.0 / 595
        val t = assertNotNull(PdfLimits.regionTile(595, 842, base * 40, base, 0.4, 0.4, 0.45, 0.45, 64L * 1024 * 1024, base * 6))
        assertEquals(base * 6, t.scale, 1e-9)
        // 보이는 자리가 없으면 없다.
        assertNull(PdfLimits.regionTile(595, 842, base * 3, base, 0.5, 0.5, 0.5, 0.9, 64L * 1024 * 1024, base * 6))
    }
}
