package io.github.donggi.iroiroviewer.docview.pdf

import kotlin.test.Test
import kotlin.test.assertEquals

/** 목차 나무를 평평하게 든 셈 — 접힌 항목의 자손 건너뛰기, 지금 쪽의 항목, 조상. 읽기는 `PdfNavigationTest`. */
class PdfOutlineTest {

    private fun e(depth: Int, page: Int?, hasChildren: Boolean = false, open: Boolean = false) =
        PdfOutline.Entry("t", page, depth, open, hasChildren)

    /**
     * 0 1장(0)        — 펼쳐 저장됨
     * 1   1.1(2)
     * 2     1.1.1(3)
     * 3   1.2(5)
     * 4 2장(8)
     * 5   2.1(없음)
     * 6 3장(12)
     */
    private val outline = PdfOutline(
        listOf(
            e(0, 0, hasChildren = true, open = true),
            e(1, 2, hasChildren = true),
            e(2, 3),
            e(1, 5),
            e(0, 8, hasChildren = true),
            e(1, null),
            e(0, 12),
        ),
    )

    @Test
    fun 접힌_항목의_자손은_보이지_않는다() {
        assertEquals(listOf(0, 4, 6), outline.visible(emptySet()))
        assertEquals(listOf(0, 1, 3, 4, 6), outline.visible(setOf(0)))
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6), outline.visible(setOf(0, 1, 4)))
        // 조상이 접혔으면 펼친 자손도 보이지 않는다.
        assertEquals(listOf(0, 4, 5, 6), outline.visible(setOf(1, 4)))
    }

    @Test
    fun 처음에는_문서가_펼친_항목만_펼친다() {
        assertEquals(setOf(0), outline.initiallyOpen())
    }

    @Test
    fun 지금_쪽의_항목은_그_쪽_이하를_가리키는_가장_뒤의_것이다() {
        assertEquals(0, outline.currentFor(0))
        assertEquals(0, outline.currentFor(1))
        assertEquals(2, outline.currentFor(3))
        assertEquals(3, outline.currentFor(7))
        assertEquals(4, outline.currentFor(8))
        assertEquals(6, outline.currentFor(500))
        assertEquals(-1, PdfOutline(listOf(e(0, 4))).currentFor(1))
        assertEquals(-1, PdfOutline.EMPTY.currentFor(0))
    }

    @Test
    fun 조상을_바깥부터_준다() {
        assertEquals(listOf(0, 1), outline.ancestorsOf(2))
        assertEquals(listOf(4), outline.ancestorsOf(5))
        assertEquals(emptyList(), outline.ancestorsOf(6))
        assertEquals(emptyList(), outline.ancestorsOf(-1))
        assertEquals(emptyList(), outline.ancestorsOf(99))
    }
}
