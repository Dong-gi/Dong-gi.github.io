package io.github.donggi.iroiroviewer.io

import java.io.File
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 오피스 문서의 저장(편집기 — [EditorSaver])과 새 문서([NewDocuments])의 판단 — 실패 까닭의 문장, 새 문서의 이름, 까닭 없는 쓰기 실패.
 * 서식을 옮겨 적는 일은 [BlankDocumentsTest], 파일시스템의 차례는 계측 시험(`NewDocumentsTest`)이 본다.
 */
class NewDocumentsRulesTest {

    @Test
    fun `새 까닭 둘은 제 문장이다`() {
        for (reason in listOf(FileOpEngine.Reason.NOT_EDITABLE, FileOpEngine.Reason.VERIFY_FAILED)) {
            val mine = FileOpText.reasonRes(reason)
            for (other in FileOpEngine.Reason.entries - reason) {
                assertNotEquals(FileOpText.reasonRes(other), mine, "$reason 가 $other 와 같은 문장")
            }
        }
        assertEquals(R.string.io_error_not_editable, FileOpText.reasonRes(FileOpEngine.Reason.NOT_EDITABLE))
        assertEquals(R.string.io_error_verify_failed, FileOpText.reasonRes(FileOpEngine.Reason.VERIFY_FAILED))
    }

    @Test
    fun `문서의 까닭은 문서의 말로 바꾼다`() {
        val save = FileOpManager.Kind.SAVE_DOC
        val create = FileOpManager.Kind.NEW_DOC
        // 글 편집의 문장은 '글' 을 말한다.
        assertEquals(R.string.io_error_previous_doc_not_kept, FileOpText.docReasonRes(save, FileOpEngine.Reason.PREVIOUS_NOT_KEPT))
        assertEquals(R.string.io_error_not_editable, FileOpText.docReasonRes(save, FileOpEngine.Reason.NOT_EDITABLE))
        // 새 문서에서 '고칠 수 없다' 는 '만들 줄 모른다' 다(서식이 없는 종류).
        assertEquals(R.string.io_error_new_doc_unsupported, FileOpText.docReasonRes(create, FileOpEngine.Reason.NOT_EDITABLE))
        for (reason in FileOpEngine.Reason.entries - FileOpEngine.Reason.PREVIOUS_NOT_KEPT - FileOpEngine.Reason.NOT_EDITABLE) {
            assertEquals(FileOpText.reasonRes(reason), FileOpText.docReasonRes(save, reason), "$reason")
            assertEquals(FileOpText.reasonRes(reason), FileOpText.docReasonRes(create, reason), "$reason")
        }
    }

    @Test
    fun `새 문서의 이름은 종류의 확장자로 끝난다`() {
        assertTrue(NewDocuments.nameFits("새 문서.docx", "docx"))
        assertTrue(NewDocuments.nameFits("보고서.DOCX", "docx"))
        assertTrue(NewDocuments.nameFits("a.b.hwpx", "hwpx"))
        // 매크로 판·서식 판은 빈 문서의 내용 형식과 어긋난다.
        assertFalse(NewDocuments.nameFits("보고서.docm", "docx"))
        assertFalse(NewDocuments.nameFits("보고서", "docx"))
        assertFalse(NewDocuments.nameFits(".docx", "docx"))
        assertFalse(NewDocuments.nameFits("표.xlsx", ""))
    }

    @Test
    fun `까닭 없는 쓰기 실패는 폴더가 없으면 찾을 수 없음이다`() {
        val here = File(System.getProperty("java.io.tmpdir") ?: ".")
        val gone = File(here, "newdoc-gone-${System.nanoTime()}")
        assertEquals(FileOpEngine.Reason.NOT_FOUND, NewFiles.reasonOfWrite(IOException("x"), gone))
        // 공간이 넉넉한 폴더면 그대로 입출력 실패다(빌드 기계의 임시 폴더는 8 MiB 보다 넉넉하다고 본다).
        assertEquals(FileOpEngine.Reason.IO, NewFiles.reasonOfWrite(IOException("x"), here))
    }
}
