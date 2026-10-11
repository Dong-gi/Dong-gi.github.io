package io.github.donggi.iroiroviewer.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 미리보기 단추가 서는 파일. 마크다운에 더해 HTML(`.html`·`.htm`·`.xhtml`)에도 선다. */
class PreviewKindTest {

    @Test
    fun `마크다운과 HTML 에만 선다`() {
        for (name in listOf("README.md", "a.MARKDOWN", "b.mdown", "c.mkd", "d.mkdn", "e.mdwn")) {
            assertEquals(PreviewKind.MARKDOWN, PreviewKind.of(name), name)
        }
        for (name in listOf("index.html", "PAGE.HTM", "저장한 쪽.html", "c.xhtml", "My Page.Html")) {
            assertEquals(PreviewKind.HTML, PreviewKind.of(name), name)
        }
        // PHP 의 `.phtml`·`.shtml`·이름만 `html` 인 것·SVG(그림 뷰어가 연다)는 아니다.
        for (name in listOf("a.phtml", "b.shtml", "html", "c.svg", "d.txt", "e.html.txt", "f.htmlx")) {
            assertNull(PreviewKind.of(name), name)
        }
    }

    @Test
    fun `HTML 만 손가락으로 확대한다`() {
        // 사용자의 쪽은 화면보다 넓게 짜였을 수 있다. 마크다운은 우리가 흐르게 짰다.
        assertTrue(PreviewKind.HTML.zoomable)
        assertFalse(PreviewKind.MARKDOWN.zoomable)
    }
}
