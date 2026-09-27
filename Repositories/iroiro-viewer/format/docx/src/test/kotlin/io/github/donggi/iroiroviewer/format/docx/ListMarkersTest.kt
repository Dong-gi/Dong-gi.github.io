package io.github.donggi.iroiroviewer.format.docx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 번호 모양과 기호 글꼴 — 값 하나를 글자로. */
class ListMarkersTest {

    private fun f(v: Int, fmt: String, custom: String? = null) = ListMarkers.format(v, fmt, custom)

    @Test
    fun 로마자와_글자_번호() {
        assertEquals(listOf("i", "iv", "ix", "xiv", "mcmxcix"), listOf(1, 4, 9, 14, 1999).map { f(it, "lowerRoman") })
        assertEquals("XL", f(40, "upperRoman"))
        // 3999 를 넘으면 숫자로.
        assertEquals("4000", f(4000, "upperRoman"))
        // 워드는 26 을 넘으면 같은 글자를 겹친다.
        assertEquals(listOf("a", "z", "aa", "bb", "zz", "aaa"), listOf(1, 26, 27, 28, 52, 53).map { f(it, "lowerLetter") })
        assertEquals("C", f(3, "upperLetter"))
        assertEquals("0", f(0, "lowerLetter"))
        assertEquals("100000", f(100_000, "lowerLetter"))
        assertEquals(listOf("1st", "2nd", "3rd", "4th", "11th", "12th", "21st"), listOf(1, 2, 3, 4, 11, 12, 21).map { f(it, "ordinal") })
        assertEquals(listOf("07", "10"), listOf(7, 10).map { f(it, "decimalZero") })
        assertEquals("", f(3, "none"))
        assertEquals("5", f(5, "hebrew1"))
    }

    @Test
    fun 한국어_모양() {
        assertEquals(listOf("가", "나", "하", "가"), listOf(1, 2, 14, 15).map { f(it, "ganada") })
        assertEquals(listOf("ㄱ", "ㄴ", "ㅎ"), listOf(1, 2, 14).map { f(it, "chosung") })
        assertEquals(listOf("①", "⑳", "21"), listOf(1, 20, 21).map { f(it, "decimalEnclosedCircle") })
        assertEquals("⑴", f(1, "decimalEnclosedParen"))
        assertEquals(listOf("일", "십", "십일", "이십삼", "백", "천구백구십구"), listOf(1, 10, 11, 23, 100, 1999).map { f(it, "koreanDigital") })
        assertEquals(listOf("하나", "열", "열하나", "스물셋", "아흔아홉", "100"), listOf(1, 10, 11, 23, 99, 100).map { f(it, "koreanCounting") })
        assertEquals("二十三", f(23, "koreanDigital2"))
        assertEquals("１２", f(12, "decimalFullWidth"))
    }

    @Test
    fun 워드_2010_의_0_채운_번호() {
        assertEquals(listOf("001", "012", "1234"), listOf(1, 12, 1234).map { f(it, "custom", "001, 002, 003, ...") })
        assertEquals("7", f(7, "custom", "一, 二, 三"))
    }

    @Test
    fun 글머리표와_기호_글꼴() {
        assertEquals("•", ListMarkers.bullet("", "Symbol"))
        assertEquals("▪", ListMarkers.bullet("", "Wingdings"))
        // 글꼴을 모르면 흔한 글머리표 표로 짐작한다 — U+F0A7 은 Symbol 의 ♣ 가 아니라 Wingdings 의 네모다.
        assertEquals("▪", ListMarkers.bullet("", null))
        assertEquals("➢", ListMarkers.bullet("", null))
        // Wingdings `v`(0x76)는 네 쪽 마름모(❖)다. 검은 마름모(◆)는 `u`(0x75).
        assertEquals("❖", ListMarkers.bullet("", null))
        assertEquals("❖", ListMarkers.bullet("v", "Wingdings"))
        assertEquals("◆", ListMarkers.bullet("u", "Wingdings"))
        assertEquals("▪", ListMarkers.bullet("§", "Wingdings"))
        assertEquals("•", ListMarkers.bullet("·", "Symbol"))
        assertEquals("o", ListMarkers.bullet("o", "Courier New"))
        assertEquals("•", ListMarkers.bullet("", null))
        assertEquals("•", ListMarkers.bullet(null, null))
        assertEquals("α", ListMarkers.glyph(0xF061, "Symbol"))
        assertEquals("≤", ListMarkers.glyph(0xF0A3, "Symbol"))
        assertNull(ListMarkers.glyph(0xF021, "Wingdings"))
        assertEquals("°", ListMarkers.glyph(0x00B0, "Arial"))
    }

    @Test
    fun 필드_명령에서_링크를_가려낸다() {
        assertEquals("_Toc1", FieldInstr.parse(" HYPERLINK \\l \"_Toc1\" \\h ")?.anchor)
        val external = FieldInstr.parse("HYPERLINK \"https://example.com\" \\o \"도움말\"")
        assertEquals(null, external?.anchor)
        assertEquals(true, external != null)
        assertEquals("그림1", FieldInstr.parse(" REF 그림1 \\h ")?.anchor)
        assertNull(FieldInstr.parse(" REF 그림1 "))
        assertNull(FieldInstr.parse(" PAGE "))
        assertNull(FieldInstr.parse(""))
    }
}
