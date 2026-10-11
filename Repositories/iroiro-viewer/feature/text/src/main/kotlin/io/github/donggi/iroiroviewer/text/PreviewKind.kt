package io.github.donggi.iroiroviewer.text

import io.github.donggi.iroiroviewer.format.text.HtmlPreview
import io.github.donggi.iroiroviewer.format.text.MarkdownPreview

/**
 * 텍스트 뷰어가 **그려 볼 수도 있는** 파일의 종류. 이것이 있는 파일에만 위쪽 막대의 '미리보기' 단추가 선다.
 *
 * * [MARKDOWN] — 우리가 HTML 로 바꾼다(CommonMark + GFM). 읽기 CSS 를 얹고, 누를 수 있는 것은 `#자리` 뿐이다.
 * * [HTML] — 그 파일의 HTML 그대로 그린다(`.html`·`.htm`·`.xhtml`). 위생을 거치고, 쪽의 CSS 가 모양을 정한다. 쪽이 화면보다 넓을
 *   수 있어 손가락으로 확대된다.
 *
 * 원문 보기(문법 강조)는 둘 다 그대로다 — 단추가 둘 사이를 오간다.
 */
enum class PreviewKind(
    /**
     * 손가락으로 확대하는가. HTML 은 켠다 — 쪽이 화면보다 넓게 짜였을 수 있다(WebView 는 화면 폭에 맞춰 배치하므로 데스크톱 폭의
     * 표·틀이 옆으로 넘친다). 마크다운은 우리가 흐르게 짰으니 끈다 — 확대하면 줄이 화면 밖으로 나간다(문서 뷰어의 글과 같다).
     */
    val zoomable: Boolean,
) {
    MARKDOWN(zoomable = false),
    HTML(zoomable = true);

    companion object {
        fun of(name: String): PreviewKind? = when {
            MarkdownPreview.isMarkdownName(name) -> MARKDOWN
            HtmlPreview.isHtmlName(name) -> HTML
            else -> null
        }
    }
}
