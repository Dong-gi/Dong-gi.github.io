package io.github.donggi.iroiroviewer.io

import kotlin.test.Test
import kotlin.test.assertEquals

/** 쪽 범위 글과 새 이름([PdfPageRanges]). */
class PdfPageRangesTest {

    private fun pages(text: String, count: Int = 20) = (PdfPageRanges.parse(text, count) as PdfPageRanges.Parsed.Pages).pages

    private fun error(text: String, count: Int = 20) = (PdfPageRanges.parse(text, count) as PdfPageRanges.Parsed.Error).kind

    @Test
    fun `범위와 낱쪽을 모아 오름차순 0부터로`() {
        assertEquals(listOf(0, 1, 2, 6, 9, 10, 11), pages("1-3, 7, 10-12"))
        assertEquals(listOf(0, 1, 2, 6), pages("7,1-3,2"))
        assertEquals(listOf(4), pages(" 5 "))
        assertEquals(listOf(0, 1, 2), pages("1 - 3"))
        assertEquals(listOf(0, 2, 4), pages("1 3 5"))
        assertEquals(listOf(19), pages("20"))
        assertEquals(listOf(2, 3, 4), pages("3~5"))
        assertEquals(listOf(2, 3, 4, 6), pages("3–5、7"))
        assertEquals(listOf(1, 2), pages("２-３"))
        assertEquals(listOf(0), pages(",,1,,"))
    }

    @Test
    fun `틀린 글을 가른다`() {
        assertEquals(PdfPageRanges.ErrorKind.EMPTY, error(""))
        assertEquals(PdfPageRanges.ErrorKind.EMPTY, error(" , "))
        assertEquals(PdfPageRanges.ErrorKind.SYNTAX, error("a"))
        assertEquals(PdfPageRanges.ErrorKind.SYNTAX, error("1-"))
        assertEquals(PdfPageRanges.ErrorKind.SYNTAX, error("-3"))
        assertEquals(PdfPageRanges.ErrorKind.SYNTAX, error("1-2-3"))
        assertEquals(PdfPageRanges.ErrorKind.SYNTAX, error("1.5"))
        assertEquals(PdfPageRanges.ErrorKind.SYNTAX, error("1".repeat(PdfPageRanges.MAX_INPUT + 1)))
        assertEquals(PdfPageRanges.ErrorKind.OUT_OF_RANGE, error("0"))
        assertEquals(PdfPageRanges.ErrorKind.OUT_OF_RANGE, error("21"))
        assertEquals(PdfPageRanges.ErrorKind.OUT_OF_RANGE, error("1-99999999999"))
        assertEquals(PdfPageRanges.ErrorKind.OUT_OF_RANGE, error("1", count = 0))
        assertEquals(PdfPageRanges.ErrorKind.REVERSED, error("7-3"))
        // 한 번에 만드는 상한(2만 쪽)을 미리 말한다.
        assertEquals(PdfPageRanges.ErrorKind.TOO_MANY, error("1-20001", count = 30_000))
    }

    @Test
    fun `이름 꼬리는 이어진 것을 묶고 길면 처음·끝·수로`() {
        assertEquals("p1-3_7_10-12", PdfPageRanges.tag(listOf(0, 1, 2, 6, 9, 10, 11)))
        assertEquals("p5", PdfPageRanges.tag(listOf(4)))
        val scattered = (0 until 100 step 2).toList()
        assertEquals("p1-99_50pages", PdfPageRanges.tag(scattered))
        assertEquals("보고서_p1-3_7.pdf", PdfPageRanges.extractName("보고서.pdf", listOf(0, 1, 2, 6)))
        assertEquals("보고서_merged.pdf", PdfPageRanges.mergeName("보고서.PDF"))
        assertEquals(true, PdfPageRanges.isPdfName("a.PDF"))
        assertEquals(false, PdfPageRanges.isPdfName("a.pdf.txt"))
    }

    @Test
    fun `넓은 범위도 상한 안에서 끝난다`() {
        assertEquals(PdfPageRanges.MAX_PAGES, pages("1-${PdfPageRanges.MAX_PAGES}", count = 50_000).size)
    }

    @Test
    fun `목차의 파일 이름은 확장자를 뗀다`() {
        assertEquals("보고서", PdfPageSaver.titleOf("보고서.pdf"))
        assertEquals(".pdf", PdfPageSaver.titleOf(".pdf"))
        assertEquals("a.b", PdfPageSaver.titleOf("a.b.pdf"))
    }

    @Test
    fun `새 까닭의 문구`() {
        assertEquals(R.string.io_error_pdf_locked, FileOpText.reasonRes(FileOpEngine.Reason.PDF_LOCKED))
        assertEquals(R.string.io_error_pdf_unreadable, FileOpText.reasonRes(FileOpEngine.Reason.PDF_UNREADABLE))
        assertEquals(R.string.io_error_pdf_too_big, FileOpText.reasonRes(FileOpEngine.Reason.PDF_TOO_BIG))
        assertEquals(R.string.io_error_pdf_lock_unsupported, FileOpText.reasonRes(FileOpEngine.Reason.PDF_LOCK_UNSUPPORTED))
    }

    @Test
    fun `요청의 쪽 묶음은 암호를 글로 드러내지 않는다`() {
        val part = PdfPagesSupport.Part("/storage/emulated/0/비밀.pdf", listOf(0), password = "secret".toCharArray())
        assertEquals(false, part.toString().contains("secret"))
        assertEquals(false, part.toString().contains("비밀"))
    }
}
