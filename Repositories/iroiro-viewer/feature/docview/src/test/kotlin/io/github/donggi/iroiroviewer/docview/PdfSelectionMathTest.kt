package io.github.donggi.iroiroviewer.docview

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import io.github.donggi.iroiroviewer.docview.pdf.PdfSpreads
import io.github.donggi.iroiroviewer.docview.pdf.text.PageText
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 고르기의 좌표([PdfSelectionMath]). 칠한 자리가 글자에서 미끄러지면 눈으로만 보이고 오류는 없다 — 그래서 여기서 박는다.
 * 기준은 `SearchHighlights`·`ZoomableImage` 의 그리는 변환이다(맞춘 그림을 가운데 두고, 뷰포트 가운데를 축으로 키우고, 옮긴다).
 */
class PdfSelectionMathTest {

    private fun near(e: Float, a: Float) = assertTrue(abs(e - a) < 0.01f, "기대 $e, 실제 $a")

    @Test
    fun `화면과 비율은 서로 거꾸로다`() {
        // 1000×1500 그림을 1080×2400 뷰포트에 맞추면 1080×1620, 위아래 390 씩 띠.
        val viewport = Size(1080f, 2400f)
        val center = PdfSelectionMath.toScreen(0.5f, 0.5f, 1000, 1500, viewport, 1f, Offset.Zero)
        near(540f, center.x)
        near(1200f, center.y)
        val topLeft = PdfSelectionMath.toScreen(0f, 0f, 1000, 1500, viewport, 1f, Offset.Zero)
        near(0f, topLeft.x)
        near(390f, topLeft.y)
        // 두 배로 키우고 옮긴 뒤에도 거꾸로 돌아온다.
        val s = PdfSelectionMath.toScreen(0.3f, 0.7f, 1000, 1500, viewport, 2f, Offset(-100f, 50f))
        val back = assertNotNull(PdfSelectionMath.toFraction(s.x, s.y, 1000, 1500, viewport, 2f, Offset(-100f, 50f)))
        near(0.3f, back.x)
        near(0.7f, back.y)
        // 가운데를 축으로 키운다 — 가운데는 그대로다.
        val c2 = PdfSelectionMath.toScreen(0.5f, 0.5f, 1000, 1500, viewport, 3f, Offset.Zero)
        near(540f, c2.x)
        near(1200f, c2.y)
    }

    @Test
    fun `두 쪽 보기에서 쪽과 쪽의 점을 가른다`() {
        // 600×800 쪽 둘을 나란히 — 가상 쪽 1200×800.
        val layout = PdfSpreads.Layout(1200, 800, listOf(
            PdfSpreads.Placement(1, 0, 0, 600, 800),
            PdfSpreads.Placement(2, 600, 0, 600, 800),
        ))
        val (page, local) = assertNotNull(PdfSelectionMath.fractionToPage(layout, 0.75f, 0.5f))
        assertEquals(2, page)
        near(0.5f, local.x)
        near(0.5f, local.y)
        // 글 꺼내기의 쪽 크기가 플랫폼의 정수 크기와 조금 달라도 비율로 맞춘다.
        val text = PageText(600.4f, 799.6f, emptyList())
        val f = assertNotNull(PdfSelectionMath.pageToFraction(layout, 2, text, 300.2f, 399.8f))
        near(0.75f, f.x)
        near(0.5f, f.y)
    }
}
