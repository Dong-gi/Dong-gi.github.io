package io.github.donggi.iroiroviewer.io

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** PDF 잠그기·풀기의 판단 — 결과 문장의 까닭, 요청의 종류, 요청이 암호를 싣지 않고 말하는가. */
class PdfLockRulesTest {

    @Test
    fun `실패의 까닭은 둘만 이 일의 말로 바꾸고 나머지는 그대로다`() {
        assertEquals(R.string.io_error_previous_pdf_not_kept, FileOpText.pdfLockReasonRes(FileOpEngine.Reason.PREVIOUS_NOT_KEPT))
        assertEquals(R.string.io_error_pdf_changed, FileOpText.pdfLockReasonRes(FileOpEngine.Reason.CHANGED))
        for (reason in FileOpEngine.Reason.entries - FileOpEngine.Reason.PREVIOUS_NOT_KEPT - FileOpEngine.Reason.CHANGED) {
            assertEquals(FileOpText.reasonRes(reason), FileOpText.pdfLockReasonRes(reason), "$reason")
        }
    }

    @Test
    fun `요청은 암호도 경로도 글로 내보내지 않는다`() {
        val r = FileOpManager.Request.PdfRelock("id-1", "/storage/emulated/0/비밀/문서.pdf", FileStamp(1, 2), "옛암호".toCharArray(), "새암호".toCharArray())
        val text = r.toString()
        assertTrue("암호" !in text && "비밀" !in text && "문서" !in text, text)
    }
}
