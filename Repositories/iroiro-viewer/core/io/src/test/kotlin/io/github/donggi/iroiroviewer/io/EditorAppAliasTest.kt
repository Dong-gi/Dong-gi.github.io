package io.github.donggi.iroiroviewer.io

import io.github.donggi.iroiroviewer.model.FileKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 확장자의 별칭 — 한글 서식(`.hwt`)·HWPX 서식(`.hwtx`)·OWPML(`.owpml`)은 목록이 '한글 문서' 로 그리는데, 뷰어 교체(10-09)
 * 뒤 편집기의 형식 표에 없어 누르면 '이 앱이 다루지 않는 문서입니다' 였다. 별칭으로 HWP·HWPX 가 되어 편집기의 보기 모드로 간다.
 */
class EditorAppAliasTest {

    @Test
    fun `한글 서식과 OWPML 은 HWP·HWPX 로 읽는다`() {
        assertEquals("hwp", EditorApp.kindOfName("/storage/emulated/0/서식/신청서.hwt"))
        assertEquals("hwp", EditorApp.kindOfName("신청서.HWT"))
        assertEquals("hwpx", EditorApp.kindOfName("양식.hwtx"))
        assertEquals("hwpx", EditorApp.kindOfName("문서.owpml"))
        // 볼 때도 편집기로 연다(오피스·한글과 같다).
        for (name in listOf("a.hwt", "a.hwtx", "a.owpml")) assertTrue(EditorApp.viewsInEditor(EditorApp.kindOfName(name)!!), name)
        // 점으로 시작하는 이름은 확장자가 없는 이름이다(다른 형식과 같은 규칙).
        assertNull(EditorApp.kindOfName(".hwt"))
    }

    /**
     * 별칭은 **편집기가 쓰는 확장자가 아니다** — 고쳐 저장하면 rhwp 가 `.hwp`·`.hwpx` 로 내고, 파일 쪽은 이름이 원본과 다르니 곁에 새 파일로
     * 둔다(`app` 의 `EditorSlots.plan` — 시험은 `HancomTemplateSaveTest`). 별칭을 [EditorApp.extensions] 에 넣으면 저장 이름을 다듬는 쪽이
     * `.hwt` 를 문서 확장자로 떼어, 이름을 견주는 규칙에 따라서는 서식 파일을 제자리에서 덮을 수 있다.
     */
    @Test
    fun `별칭은 쓰는 확장자도 편집기가 알리는 형식도 아니다`() {
        for (ext in listOf("hwt", "hwtx", "owpml")) {
            assertFalse(ext in EditorApp.extensions, ext)
            assertNull(EditorApp.extensionOf(ext), ext)
        }
        assertEquals("hwp", EditorApp.extensionOf("hwp"))
        assertEquals("hwpx", EditorApp.extensionOf("hwpx"))
        // 편집기 화면의 매니페스트가 알리는 것은 진짜 형식뿐이다 — 별칭 글자는 버린다.
        assertEquals(setOf("hwp"), EditorApp.parseKinds("hwp,hwt,owpml"))
    }

    /**
     * **목록이 문서로 그리는 것은 모두 갈 곳이 있다** — 오피스·한글 확장자(`MimeResolver` 의 DOCUMENT·SHEET·SLIDE·HWP)는 편집기의 형식이 된다.
     * 예외는 RTF 하나다(ONLYOFFICE 의 변환기가 읽을 때 한글을 버린다 — 문서 뷰어가 '다루지 않는 문서' 와 '다른 앱으로 열기' 로 끝낸다).
     * 목록의 표와 편집기의 표가 따로라, 한쪽에만 더한 확장자가 '목록에는 한글 문서인데 열리지 않는다' 가 된다(이번 결함이 그 모양이었다).
     */
    @Test
    fun `목록이 오피스·한글로 그리는 확장자는 RTF 말고 모두 편집기의 형식이다`() {
        val office = setOf(FileKind.DOCUMENT, FileKind.SHEET, FileKind.SLIDE, FileKind.HWP)
        val exts = MimeResolver.extensionTable.filter { it.first in office }.flatMap { it.second }
        assertTrue(exts.size > 20, "표를 읽지 못했다: $exts")
        for (ext in exts) {
            val kind = EditorApp.kindOfName("x.$ext")
            if (ext == "rtf") assertNull(kind, ext) else assertTrue(kind != null && EditorApp.viewsInEditor(kind), ext)
        }
    }
}
