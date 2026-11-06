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

/** 편집기 화면의 판단 — 떠 있는 단추, 오류 화면의 단추, 읽던 자리의 꼴, '새 파일로 저장했습니다' 의 횟수, 고르개가 돌려준 주소. */
class EditorRulesTest {

    // ---- 고르개가 돌려준 주소(그림 넣기·내보내기 — 2026-10-16 보안 점검) ----

    private val own = "io.github.donggi.iroiroviewer"
    private val providers = mapOf(
        "com.android.providers.media.documents" to "com.android.providers.media.module",
        "media" to "com.android.providers.media.module",
        "com.google.android.apps.photos.contentprovider" to "com.google.android.apps.photos",
        // 이 앱이 선언했지만 이름이 꾸러미로 시작하지 않는 제공자 — 주인으로 가른다.
        "odd.authority" to own,
    )
    private fun accepts(scheme: String?, authority: String?) = EditorRules.acceptsReturnedUri(scheme, authority, own) { providers[it] }

    @Test
    fun othersContentAddressesAreAccepted() {
        assertTrue(accepts("content", "com.android.providers.media.documents"))
        assertTrue(accepts("content", "media"))
        assertTrue(accepts("CONTENT", "com.google.android.apps.photos.contentprovider"))
        // 다른 사용자의 같은 제공자(0@ 을 떼고 본다).
        assertTrue(accepts("content", "10@media"))
        // 주인을 모르는 권한자는 이 앱의 것이 아니다 — 여는 것은 그쪽이 막는다.
        assertTrue(accepts("content", "unknown.provider"))
    }

    @Test
    fun ourOwnAddressesAndFilesAreRefused() {
        // file: 은 경로를 그대로 연다 — 이 앱의 신원으로.
        assertFalse(accepts("file", null))
        assertFalse(accepts("file", "localhost"))
        assertFalse(accepts(null, "media"))
        assertFalse(accepts("android.resource", own))
        // 이 앱의 FileProvider·저장 자리·(디버그) 시험대 제공자 — 사용자 번호를 붙여도, 대소문자를 바꿔도.
        for (authority in listOf("$own.files", "$own.editorslot", "$own.editorout", own, "0@$own.files", "IO.GITHUB.DONGGI.IROIROVIEWER.FILES")) {
            assertFalse(accepts("content", authority), authority)
        }
        // 이름은 달라도 주인이 이 앱이면.
        assertFalse(accepts("content", "odd.authority"))
        // 권한자가 없거나 비었다.
        assertFalse(accepts("content", null))
        assertFalse(accepts("content", ""))
        assertFalse(accepts("content", "0@"))
    }

    /** 배선 — 그림 넣기는 걸러서 쪽에 건네고, 내보내기는 걸리면 쓰지 않는다. 쓰는 자리는 그 갈래 하나다. */
    @Test
    fun theActivityFiltersWhatThePickerReturned() {
        val activity = java.io.File("src/main/kotlin/io/github/donggi/iroiroviewer/editor/EditorActivity.kt").readText()
        val image = activity.substringAfter("REQUEST_IMAGE -> {").substringBefore("REQUEST_EXPORT -> {")
        assertTrue("val accepted = picked?.filter(::acceptsReturned)" in image, image)
        assertTrue("chooser?.onReceiveValue(accepted?.takeIf { it.isNotEmpty() }?.toTypedArray())" in image, image)
        assertFalse("onReceiveValue(picked" in image || "onReceiveValue(WebChromeClient" in image, "거르지 않은 것을 건넨다")
        val export = activity.substringAfter("REQUEST_EXPORT -> {").substringBefore("else -> super.onActivityResult")
        assertTrue(export.indexOf("!acceptsReturned(target) ->") in 0 until export.indexOf("else -> writeExport(file, target)"), export)
        assertEquals(1, Regex("""writeExport\(file, target\)""").findAll(activity).count(), "내보내기를 쓰는 자리는 하나다")
        assertTrue("EditorRules.acceptsReturnedUri(uri.scheme, uri.authority, packageName)" in activity)
    }

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
