package io.github.donggi.iroiroviewer.editor

import io.github.donggi.iroiroviewer.editor.EditorRules.Action
import io.github.donggi.iroiroviewer.editor.EditorRules.Error
import io.github.donggi.iroiroviewer.editor.EditorRules.Floating
import io.github.donggi.iroiroviewer.editor.EditorRules.OpenFailure
import io.github.donggi.iroiroviewer.editor.EditorRules.Primary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 편집기 화면의 판단 — 떠 있는 단추, 오류 화면의 단추, 읽던 자리의 꼴, '새 파일로 저장했습니다' 의 횟수. */
class EditorRulesTest {

    // ---- 떠 있는 단추 묶음 ----

    @Test
    fun viewModeWithASlotOffersEditAndTheMoreMenu() {
        assertEquals(Floating(Primary.EDIT, more = true), EditorRules.floating(viewMode = true, opened = true, failure = null, hasSlot = true, hasDocumentUri = true))
    }

    @Test
    fun aReceivedCopyHasNoEditButStillTheMoreMenu() {
        // 받은 사본 — 고쳐 둘 곳이 없다. '다른 앱으로 열기' 는 ⋮ 로 된다.
        assertEquals(Floating(null, more = true), EditorRules.floating(viewMode = true, opened = true, failure = null, hasSlot = false, hasDocumentUri = true))
    }

    @Test
    fun nothingFloatsWhileOpening() {
        // 여는 동안에는 아무것도 띄우지 않는다 — 엔진이 여는 동안 띄우는 암호 칸을 ⋮ 가 가렸다(2026-10-10 기기, rhwp). 넘기려면 암호 물음을
        // 닫는다 — 그때 ⋮ 와 '다시 열기'(아래 시험). '편집' 은 문서를 연 뒤에만.
        assertEquals(Floating(null, more = false), EditorRules.floating(viewMode = true, opened = false, failure = null, hasSlot = true, hasDocumentUri = true))
    }

    @Test
    fun editingShowsNothing() {
        assertEquals(Floating(null, more = false), EditorRules.floating(viewMode = false, opened = true, failure = null, hasSlot = true, hasDocumentUri = true))
        assertEquals(Floating(null, more = false), EditorRules.floating(viewMode = false, opened = false, failure = null, hasSlot = true, hasDocumentUri = true))
    }

    @Test
    fun aCancelledPasswordOffersReopenInBothModes() {
        for (view in listOf(true, false)) {
            val f = EditorRules.floating(viewMode = view, opened = false, failure = OpenFailure.CANCELLED, hasSlot = true, hasDocumentUri = true)
            assertEquals(Floating(Primary.REOPEN, more = true), f, "viewMode=$view")
        }
        // 넘길 문서가 없어도 다시 열기는 된다(같은 인텐트를 다시 읽는다).
        assertEquals(Primary.REOPEN, EditorRules.floating(true, false, OpenFailure.CANCELLED, hasSlot = false, hasDocumentUri = false).primary)
    }

    @Test
    fun otherFailuresOfferOpenWith() {
        for (failure in listOf(OpenFailure.LOCKED, OpenFailure.CORRUPT, OpenFailure.OTHER)) {
            for (view in listOf(true, false)) {
                val f = EditorRules.floating(viewMode = view, opened = false, failure = failure, hasSlot = true, hasDocumentUri = true)
                assertEquals(Floating(Primary.OPEN_WITH, more = true), f, "$failure viewMode=$view")
            }
            assertEquals(Floating(null, more = false), EditorRules.floating(true, false, failure, hasSlot = true, hasDocumentUri = false))
        }
    }

    @Test
    fun aLateReadyDoesNotBringBackEditAfterAFailure() {
        // 화면은 실패를 받은 뒤의 인사를 무시하지만(opened 를 세우지 않는다), 세워졌더라도 실패가 이긴다.
        assertEquals(Primary.OPEN_WITH, EditorRules.floating(true, opened = true, failure = OpenFailure.CORRUPT, hasSlot = true, hasDocumentUri = true).primary)
    }

    @Test
    fun withoutADocumentUriThereIsNoMoreMenu() {
        assertEquals(Floating(Primary.EDIT, more = false), EditorRules.floating(true, true, null, hasSlot = true, hasDocumentUri = false))
    }

    @Test
    fun openFailureReasons() {
        assertEquals(OpenFailure.CANCELLED, OpenFailure.of("cancelled"))
        assertEquals(OpenFailure.LOCKED, OpenFailure.of("locked"))
        assertEquals(OpenFailure.CORRUPT, OpenFailure.of("corrupt"))
        // 예전 어댑터(갈래 없이)·모르는 글은 '그 밖'.
        assertEquals(OpenFailure.OTHER, OpenFailure.of(null))
        assertEquals(OpenFailure.OTHER, OpenFailure.of(""))
        assertEquals(OpenFailure.OTHER, OpenFailure.of("Cancelled"))
    }

    // ---- 오류 화면의 단추 ----

    @Test
    fun documentCausedErrorsOfferOpenWith() {
        for (e in listOf(Error.NO_ENGINE, Error.WEBVIEW, Error.TOO_LARGE)) {
            assertEquals(listOf(Action.OPEN_WITH, Action.CLOSE), EditorRules.actions(e, hasDocumentUri = true), "$e")
        }
    }

    @Test
    fun retryableErrorsOfferReopenFirst() {
        for (e in listOf(Error.READ, Error.RENDERER)) {
            assertEquals(listOf(Action.REOPEN, Action.OPEN_WITH, Action.CLOSE), EditorRules.actions(e, hasDocumentUri = true), "$e")
            assertEquals(listOf(Action.REOPEN, Action.CLOSE), EditorRules.actions(e, hasDocumentUri = false), "$e")
        }
    }

    @Test
    fun untrustedIntentsOnlyClose() {
        for (e in listOf(Error.CALLER, Error.NO_DOCUMENT, Error.SLOT)) {
            assertEquals(listOf(Action.CLOSE), EditorRules.actions(e, hasDocumentUri = true), "$e")
        }
    }

    @Test
    fun closeIsAlwaysLast() {
        for (e in Error.entries) for (uri in listOf(true, false)) assertEquals(Action.CLOSE, EditorRules.actions(e, uri).last())
    }

    @Test
    fun theRendererSentenceOnlyMentionsLostEditsWhenEditing() {
        assertTrue(EditorRules.rendererLostEdits(editing = true, dirty = true))
        assertFalse(EditorRules.rendererLostEdits(editing = true, dirty = false))
        // 보기 모드에는 잃을 것이 없다.
        assertFalse(EditorRules.rendererLostEdits(editing = false, dirty = true))
        assertFalse(EditorRules.rendererLostEdits(editing = false, dirty = false))
    }

    // ---- 읽던 자리 ----

    @Test
    fun positionsInTheAdaptersShapesPass() {
        for (p in listOf("s:0.12345,0.00000", "s:1.00000,0.50000", "p:0", "p:123", "c:0,10,2", "c:3,1024.5,7.25")) {
            assertEquals(p, EditorRules.position(p))
        }
    }

    @Test
    fun anythingElseIsDropped() {
        val bad = listOf(
            null, "", "null", "p:", "P:1", "p:-1", "p:1 ", " p:1", "p:1&x=2", "p:1#a", "javascript:alert(1)", "s:0.1,0.2;", "pp:1", "p1",
            "p:" + "1".repeat(61),
        )
        for (p in bad) assertNull(EditorRules.position(p), "$p")
        assertEquals("p:" + "1".repeat(60), EditorRules.position("p:" + "1".repeat(60)))
    }

    // ---- '새 파일로 저장했습니다' ----

    @Test
    fun aNewFileIsAnnouncedOnce() {
        val notices = EditorRules.NewFileNotices("보고서.doc")
        assertEquals("보고서.docx", notices.toAnnounce("보고서.docx"))
        // 같은 세션의 다음 저장은 그 새 파일을 고쳐 쓸 뿐이다.
        assertNull(notices.toAnnounce("보고서.docx"))
        assertNull(notices.toAnnounce("보고서.docx"))
        // 다른 이름으로 또 새 파일을 만들었으면 그것은 처음이다.
        assertEquals("보고서 (1).docx", notices.toAnnounce("보고서 (1).docx"))
    }

    @Test
    fun savingUnderTheOriginalNameIsNotANewFile() {
        val notices = EditorRules.NewFileNotices("표.xlsx")
        assertNull(notices.toAnnounce("표.xlsx"))
        assertNull(notices.toAnnounce(null))
    }
}
