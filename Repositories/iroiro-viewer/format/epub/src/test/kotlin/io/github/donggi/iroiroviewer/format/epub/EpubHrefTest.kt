package io.github.donggi.iroiroviewer.format.epub

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 주소 계산. **여기가 틀리면 증상은 '그림이 안 보인다' 하나로 뭉쳐 보인다** —
 * 기준 폴더를 잘못 잡았는지, `..` 을 안 풀었는지, 퍼센트 인코딩을 안 풀었는지가
 * 화면에서는 구분되지 않는다. 그래서 초 단위로 도는 시험이 답한다.
 */
class EpubHrefTest {

    @Test
    fun 기준_폴더에서_푼다() {
        assertEquals("OEBPS/ch1.xhtml", EpubHref.resolve("OEBPS", "ch1.xhtml"))
        assertEquals("OEBPS/img/a.png", EpubHref.resolve("OEBPS", "img/a.png"))
        assertEquals("ch1.xhtml", EpubHref.resolve("", "ch1.xhtml"))
    }

    @Test
    fun 점과_점점을_푼다() {
        assertEquals("OEBPS/img/a.png", EpubHref.resolve("OEBPS/text", "../img/a.png"))
        assertEquals("OEBPS/a.png", EpubHref.resolve("OEBPS", "./a.png"))
        assertEquals("a.png", EpubHref.resolve("OEBPS/text", "../../a.png"))
    }

    @Test
    fun 뿌리_위로는_올라가지_않는다() {
        assertNull(EpubHref.resolve("OEBPS", "../../etc/passwd"))
        assertNull(EpubHref.resolve("", "../x"))
        assertNull(EpubHref.resolve("OEBPS/text", "../../../../../../etc/passwd"))
    }

    @Test
    fun 절대_주소는_책_뿌리_기준이다() {
        assertEquals("OEBPS/a.png", EpubHref.resolve("OEBPS/text", "/OEBPS/a.png"))
    }

    @Test
    fun 조각과_질의를_뗀다() {
        assertEquals("OEBPS/ch1.xhtml", EpubHref.resolve("OEBPS", "ch1.xhtml#note"))
        assertEquals("OEBPS/ch1.xhtml", EpubHref.resolve("OEBPS", "ch1.xhtml?v=2"))
        assertNull(EpubHref.resolve("OEBPS", "#note"))
    }

    @Test
    fun 퍼센트_인코딩을_푼다() {
        assertEquals("OEBPS/한글 그림.png", EpubHref.resolve("OEBPS", "%ED%95%9C%EA%B8%80%20%EA%B7%B8%EB%A6%BC.png"))
        assertEquals("OEBPS/a b.png", EpubHref.resolve("OEBPS", "a%20b.png"))
    }

    @Test
    fun 더하기를_공백으로_바꾸지_않는다() {
        // `URLDecoder` 를 쓰면 여기가 `a b.png` 가 되어 파일을 못 찾는다.
        assertEquals("OEBPS/a+b.png", EpubHref.resolve("OEBPS", "a+b.png"))
    }

    @Test
    fun 잘못된_퍼센트는_글자로_둔다() {
        assertEquals("OEBPS/100%.png", EpubHref.resolve("OEBPS", "100%.png"))
        assertEquals("OEBPS/a%zz.png", EpubHref.resolve("OEBPS", "a%zz.png"))
    }

    @Test
    fun 빈_주소는_null_이다() {
        assertNull(EpubHref.resolve("OEBPS", ""))
        assertNull(EpubHref.resolve("OEBPS", "   "))
        assertNull(EpubHref.resolve("OEBPS", "./"))
    }

    @Test
    fun 폴더를_구한다() {
        assertEquals("OEBPS", EpubHref.dirOf("OEBPS/ch1.xhtml"))
        assertEquals("OEBPS/text", EpubHref.dirOf("OEBPS/text/ch1.xhtml"))
        assertEquals("", EpubHref.dirOf("content.opf"))
    }
}
