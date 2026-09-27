package io.github.donggi.iroiroviewer.docview

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * EPUB 의 자원을 **날것으로** 내줄 때의 형식(`rawResourceType`). 책이 적은 형식 문자열을 그대로 넘기면
 * 크롬이 스타일시트로 적용하는 형식이 있다(`application/x-unknown-content-type`·`text/css,x`·빈 형식) — 그림·글꼴 표에
 * 있는 것만 우리 이름으로 나간다(실세계 말뭉치 검토).
 */
class EpubResourceTypeTest {

    @Test
    fun 그림과_글꼴만_우리_표의_이름으로_나간다() {
        assertEquals("image/png", rawResourceType("image/png", "a.png"))
        assertEquals("image/jpeg", rawResourceType("image/jpg", "a.jpg"))
        assertEquals("font/otf", rawResourceType("application/vnd.ms-opentype", "f.otf"))
        // 형식을 모르거나 틀리게 적었으면 이름을 본다.
        assertEquals("image/png", rawResourceType(null, "a.png"))
        assertEquals("image/png", rawResourceType("application/octet-stream", "a.png"))
    }

    @Test
    fun 스타일시트로_적용될_수_있는_형식과_문서는_내주지_않는다() {
        assertNull(rawResourceType("application/x-unknown-content-type", "s.dat"))
        assertNull(rawResourceType("text/css,x", "s.dat"))
        assertNull(rawResourceType("", "s.dat"))
        assertNull(rawResourceType("application/xhtml+xml", "c.xhtml"))
        assertNull(rawResourceType("application/xml", "c.xml"))
        assertNull(rawResourceType("text/html", "c.html"))
    }
}
