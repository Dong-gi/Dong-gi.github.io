package io.github.donggi.iroiroviewer.docview

import io.github.donggi.iroiroviewer.safety.PdfLimits
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 확대한 자리를 어디로 읽는가.
 *
 * **이 계산을 화면 캡처로 판정하지 않는다.** 6단계가 제스처를 캡처로 판정하려다 세 번
 * 틀린 결론을 냈고(확대된 줄 알았던 캡처가 사실 다른 사진이었다), 그 교훈이
 * `GestureMath`·`PipMath` 를 순수 함수로 가른 이유다. 여기도 같다.
 *
 * 표본은 폰 세로(1080×2400)에 A4(595×842pt)다. 그 조합에서 바닥층은 폭을 꽉 채우고
 * (`fit = 1080/595 = 1.815`) 위아래에 여백이 남는다 — 여백이 있는 쪽과 없는 쪽을 한
 * 표본이 동시에 시험한다.
 */
class DocDetailTest {

    private val fit = PdfLimits.fitScale(595, 842, 1080, 2400)

    /** 1080×2400 뷰포트에서 A4 의 바닥층 크기. `ZoomMath.fittedSize` 가 내는 값과 같다. */
    private val fittedW = 1080f
    private val fittedH = 842f * (1080f / 595f)

    private fun want(zoom: Float, offsetX: Float = 0f, offsetY: Float = 0f) = DocDetail.want(
        fitScale = fit,
        zoom = zoom,
        viewportWidth = 1080f,
        viewportHeight = 2400f,
        fittedWidth = fittedW,
        fittedHeight = fittedH,
        offsetX = offsetX,
        offsetY = offsetY,
    )

    @Test
    fun 확대하지_않으면_타일을_뜨지_않는다() {
        assertNull(want(1f), "맞춤 배율에서는 바닥층이 이미 그 해상도다")
        assertNull(want(1.1f), "문턱 아래의 확대는 같은 그림을 두 번 그리는 일이다")
        assertNotNull(want(2f))
    }

    @Test
    fun 밀지_않았으면_가운데를_본다() {
        val w = assertNotNull(want(2f))
        assertEquals(0.5, w.focusX, 1e-9)
        // 쪽이 세로로 뷰포트보다 짧아 위아래에 여백이 있다. 그래도 **쪽의** 가운데다.
        assertEquals(0.5, w.focusY, 1e-9)
    }

    @Test
    fun 오른쪽을_보려면_그림을_왼쪽으로_민다() {
        // 배율 2 에서 바닥층 절반 폭만큼 왼쪽으로 밀면 오른쪽 4분의 3 지점이 가운데 온다.
        val shift = fittedW / 2f
        val w = assertNotNull(want(2f, offsetX = -shift))
        assertTrue(w.focusX > 0.5, "왼쪽으로 밀었으니 오른쪽을 봐야 한다: ${w.focusX}")
        assertEquals(0.75, w.focusX, 1.0 / DocDetail.FOCUS_STEPS)
    }

    @Test
    fun 쪽_밖으로는_나가지_않는다() {
        // 있을 수 없을 만큼 크게 밀어도 비율은 0~1 안이다. `tileFor` 가 다시 자르지만
        // **여기서 이미 잘라 두는 것**이 `PdfLimits` 의 계약이 아니라 우리 책임이다.
        for (offset in listOf(-1_000_000f, -5000f, 5000f, 1_000_000f)) {
            val w = assertNotNull(want(3f, offsetX = offset, offsetY = offset))
            assertTrue(w.focusX in 0.0..1.0, "가로가 범위 밖 ${w.focusX}")
            assertTrue(w.focusY in 0.0..1.0, "세로가 범위 밖 ${w.focusY}")
        }
    }

    @Test
    fun 눈금이_요청_수를_줄인다() {
        // 1화소씩 미는 동안 요청이 몇 번 바뀌는가. 눈금이 없으면 200번이다.
        val seen = HashSet<DocDetail.Want>()
        for (px in 0 until 200) seen += assertNotNull(want(2f, offsetX = -px.toFloat()))
        assertTrue(seen.size <= 25, "눈금이 듣지 않는다: ${seen.size}가지")
        assertTrue(seen.size >= 2, "눈금이 너무 굵어 움직임을 못 따라간다: ${seen.size}가지")
    }

    @Test
    fun 배율은_포인트_기준으로_나간다() {
        val w = assertNotNull(want(2f))
        // `tileFor` 가 받는 것은 '포인트 → 화소' 배율이지 사용자가 건 확대가 아니다.
        assertEquals(fit * 2, w.scale, 1.0 / DocDetail.SCALE_STEPS)
        assertNotNull(
            PdfLimits.tileFor(595, 842, w.scale, 1080, 2400, w.focusX, w.focusY, 8L * 1024 * 1024, fit * 8),
            "이 요청으로 실제 타일이 나와야 한다",
        )
    }

    @Test
    fun 잴_수_없으면_null_이다() {
        // 첫 컴포지션에서 바닥층 크기가 0 이다. 그때 계산하면 0으로 나눈다.
        assertNull(DocDetail.want(fit, 2f, 1080f, 2400f, 0f, 0f, 0f, 0f))
        assertNull(DocDetail.want(fit, 2f, 0f, 0f, fittedW, fittedH, 0f, 0f))
        assertNull(DocDetail.want(0.0, 2f, 1080f, 2400f, fittedW, fittedH, 0f, 0f))
        assertNull(DocDetail.want(fit, 0f, 1080f, 2400f, fittedW, fittedH, 0f, 0f))
        assertNull(DocDetail.want(fit, Float.NaN, 1080f, 2400f, fittedW, fittedH, 0f, 0f))
        assertNull(DocDetail.want(fit, 2f, 1080f, 2400f, fittedW, fittedH, Float.NaN, 0f))
    }

    @Test
    fun 타일을_얹는_자리는_쪽_안의_비율이다() {
        val tile = assertNotNull(
            PdfLimits.tileFor(595, 842, fit * 3, 1080, 2400, 0.5, 0.5, 8L * 1024 * 1024, fit * 8)
        )
        val at = DocDetail.fractionOf(tile)
        assertEquals(tile.srcLeft.toFloat() / tile.pageWidth, at[0], 1e-6f)
        assertEquals(tile.srcTop.toFloat() / tile.pageHeight, at[1], 1e-6f)
        for (v in at) assertTrue(v in 0f..1f, "비율이 범위 밖 ${at.toList()}")
        assertTrue(at[0] + at[2] <= 1f + 1e-6f, "오른쪽으로 넘쳤다 ${at.toList()}")
        assertTrue(at[1] + at[3] <= 1f + 1e-6f, "아래로 넘쳤다 ${at.toList()}")
    }
}
