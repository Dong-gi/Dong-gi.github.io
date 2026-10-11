package io.github.donggi.iroiroviewer.format.text

import io.github.donggi.iroiroviewer.charset.TextEncoding
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * **작은 힙에서** 원문 상한 크기의 악의적인 쪽. 이 시험은 `memoryTest` 작업이 힙 192 MB 로 따로 돌린다
 * (`format/text/build.gradle.kts`) — 기기의 힙 등급과 같은 값이고, OOM 을 잰 값이다. 시험 작업자의 기본 힙(512 MB)에서는
 * '상한 밖에서 커지는' 결함이 그냥 지나간다. 그래서 **결과의 크기**도 함께 박는다 — 힙이 넉넉한 데서 돌려도 결함이 보이게.
 *
 * 한글 한 자를 섞은 판을 함께 돌린다 — 그러면 문자열이 두 바이트 글자가 되어 같은 결함이 두 배로 크다(사용자의 쪽은 대개 그렇다).
 */
class HtmlPreviewMemoryTest {

    /** 원문 상한(8 MiB)을 ASCII 글자 수로 본 것. */
    private val full = HtmlPreview.MAX_SOURCE_BYTES.toInt()

    private fun render(html: String): HtmlPreview.Result = HtmlPreview.render(html, sheetEncoding = TextEncoding.UTF_8)

    private fun assertBounded(label: String, r: HtmlPreview.Result) {
        assertTrue(r.html.length <= HtmlPreview.MAX_HTML_CHARS + 1024, "$label: ${r.html.length}")
    }

    @Test
    fun `따옴표로 가득 찬 속성`() {
        // `"` 하나가 `&quot;` 여섯 자다. 예전에는 다 만든 뒤에 재어 48 Mi 자를 쥐었다(192 MB 에서 OOM).
        assertBounded("Latin-1", render("<p title='" + "\"".repeat(full - 32) + "'>x</p>"))
        assertBounded("UTF-16", render("<p title='가" + "\"".repeat(full - 32) + "'>x</p>"))
        // 위생기만 거쳐도 같은 자리다 — `<`·`>` 도 참조가 된다.
        assertBounded("꺾쇠", render("<p title='가" + "<>".repeat(full / 2 - 32) + "'>x</p>"))
    }

    @Test
    fun `태그가 아닌 꺾쇠`() {
        // 위생기가 `<` 를 `&lt;` 넷으로 쓴다. 예전에는 위생기가 다 쓴 뒤에 재어 33.5 M 자 쪽을 화면이 그대로 들었다.
        assertBounded("Latin-1", render("<".repeat(full)))
        assertBounded("UTF-16", render("가" + "<".repeat(full - 3)))
        // `<style>` 안의 `<` 는 `\3c ` 넷이다.
        assertBounded("style", render("<style>가" + "<".repeat(full - 16) + "</style>"))
    }

    @Test
    fun `원문 상한 크기의 보통 쪽`() {
        // 상한까지 찬 멀쩡한 쪽이 기기의 힙 안에 든다 — 앞 패스·위생기·머리가 각각 한 벌씩 쥐는 것을 합해도.
        val unit = "<div class=\"row\"><img src=\"page_files/a.png\" alt=\"그림\"><p style=\"color:#333\">가나다라마바사 아자차카타파하</p></div>\n"
        val r = render(unit.repeat(full / unit.toByteArray().size))
        assertBounded("보통 쪽", r)
        assertTrue(!r.truncated && r.html.length > full / 2, "${r.html.length}")
    }
}
