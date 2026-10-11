package io.github.donggi.iroiroviewer.format.html

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 쓰개([HtmlWriter])와 CSS 값([CssValues])의 판단 — 마크다운 미리보기(`format:text`)가
 * 이 쓰개를 쓴다.
 */
class HtmlWriterTest {

    @Test
    fun 줄_간격은_단위_없는_수() {
        assertEquals("1.3", CssValues.number(1.3, 0.5, 5.0))
        assertEquals("1", CssValues.number(1.0, 0.5, 5.0))
        assertEquals("0.5", CssValues.number(0.1, 0.5, 5.0))
        assertEquals("5", CssValues.number(80.0, 0.5, 5.0))
        assertEquals(null, CssValues.number(Double.NaN, 0.5, 5.0))
    }

    @Test
    fun 따로_쓴_것을_잇는다() {
        val side = HtmlWriter()
        side.start("div", "class" to "cap").text("<표 1>").end("div")
        val w = HtmlWriter()
        w.start("table").end("table").append(side)
        assertEquals("<table></table><div class=\"cap\">&lt;표 1&gt;</div>", w.toString())
        val open = HtmlWriter().start("p")
        assertFailsWith<IllegalArgumentException> { w.append(open) }
        // 상한을 넘으면 거기서 멈춘다.
        val small = HtmlWriter(maxChars = 10)
        small.append(side)
        assertTrue(small.full)
    }
}
