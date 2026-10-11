package io.github.donggi.iroiroviewer.format.text

import io.github.donggi.iroiroviewer.charset.TextEncoding
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * **악의적인 HTML.** 입력은 사용자가 고른 아무 파일이고 상한(8 MiB)까지 온다. 앞 패스·위생기·머리까지 한 번에 돌려 시간을 잰다.
 *
 * 크기는 **틀린 판이 확실히 넘는 크기로** 잡았다(함정 표 — 0.56 MB 로 쟀더니 JIT 이 옛 판을 빠르게 돌려 결함을 못 봤다). 되풀이될
 * 수 있는 것마다 '닫히지 않은 것이 쌓이는' 모양을 만든다 — 그 자리에서 끝까지 다시 훑는 구현은 제곱이 된다.
 */
class HtmlPreviewLimitsTest {

    private fun timed(label: String, limitMs: Long = 4_000, block: () -> Unit) {
        val t0 = System.nanoTime()
        try {
            block()
        } catch (e: StackOverflowError) {
            fail("$label: 스택이 넘쳤다")
        }
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue(ms < limitMs, "$label: ${ms}ms")
    }

    private fun render(html: String): HtmlPreview.Result = HtmlPreview.render(html, sheetEncoding = TextEncoding.UTF_8)

    private val size = 1_500_000

    @Test
    fun `주석이 많아도 끝을 한 번만 찾는다`() = timed("주석") {
        // 주석마다 `--!>` 를 파일 끝까지 찾으면 제곱이다.
        val r = render("<!--a-->".repeat(size / 8) + "<p>끝</p>")
        assertTrue("<p>끝</p>" in r.html)
    }

    @Test
    fun `닫히지 않은 CSS 주소`() = timed("CSS 주소") {
        render("<style>" + "url(".repeat(size / 4) + "</style>")
        render("<style>" + "a{b:url(\"".repeat(size / 9) + "</style>")
        render("<style>" + "a{b:url(x y ".repeat(size / 12) + "</style>")
        render("<p style=\"" + "background:url(a.png);".repeat(size / 22) + "\">x</p>")
        render("<style>url(x)" + "/*".repeat(size / 2) + "</style>")
        render("<style>url(x)" + "'\\".repeat(size / 2) + "</style>")
    }

    @Test
    fun `닫히지 않은 스크립트와 태그`() = timed("스크립트·태그") {
        render("<script>".repeat(size / 8))
        render("<script><!--" + "<script ".repeat(size / 8))
        render("<a href=\"".repeat(size / 9))
        render("<".repeat(size))
        render("<p ".repeat(size / 3))
        render("<!DOCTYPE a [>".repeat(size / 14) + "<p>끝</p>")
        render("<style>".repeat(size / 7))
    }

    @Test
    fun `깊은 중첩과 많은 요소`() = timed("중첩") {
        render("<div>".repeat(size / 5))
        render("<picture><source srcset=\"a.webp\">".repeat(size / 34))
        render("<img src=\"a.png\">".repeat(size / 17))
        render((0 until size / 20).joinToString("") { "<img src=\"i$it.png\">" })
        render("<a href=\"b.html#x\">x</a>".repeat(size / 24))
    }

    @Test
    fun `긴 속성 값`() = timed("속성") {
        render("<img src=\"" + "&#65;".repeat(size / 5) + "\">")
        render("<img srcset=\"" + "a.png 1x,".repeat(size / 9) + "\">")
        render("<link rel=\"stylesheet\"" + " href=\"a.css\"".repeat(size / 13) + ">")
        render("<img src=\"data:image/png;base64," + "QUFB".repeat(size / 4) + "\">")
    }

    /**
     * 제곱 시간 — 위생기의 `@import` 지우기가 규칙마다 뒤를 통째로 다시 만들었다(`@import;` 8 Mi 자에 몇 분). 문서의
     * `<style>` 과 디스크의 스타일시트가 같은 길을 타므로 둘 다 잰다.
     */
    @Test
    fun `되풀이한 import·이스케이프·image-set·짝 없는 괄호`() = timed("CSS 되풀이", limitMs = 8_000) {
        // `@import` 는 두 배 크기로 — 옛 판의 제곱이 확실히 한도를 넘게(1.5 M 자면 옛 판도 몇 초라 한도 언저리다).
        render("<style>" + "@import;".repeat(size / 4) + "</style>")
        render("<style>" + "@import{}".repeat(size / 9) + "</style>")
        render("<style>" + "@import url(a.css);".repeat(size / 10) + "</style>")
        render("<style>" + "@\\69mport \"a.css\";".repeat(size / 9) + "</style>")
        render("<style>" + "a{b:\\75 rl(x.png)}".repeat(size / 18) + "</style>")
        render("<style>" + "a{b:image-set(\"x.png\" 1x)}".repeat(size / 26) + "</style>")
        render("<style media=\"print\">" + "}".repeat(size) + "</style>")
        render("<p style=\"" + "background:url&lpar;a.png&rpar;;".repeat(size / 32) + "\">x</p>")
        render("</script>".repeat(size / 9) + "<p>끝</p>")
        val sheet = "@import;".repeat(size / 4).toByteArray()
        val prepared = HtmlPreview.prepare("<link rel=\"stylesheet\" href=\"a.css\">", xhtml = false, selfName = null)
        HtmlPreview.finish(prepared, mapOf("a.css" to sheet), TextEncoding.UTF_8)
    }

    /** 결과가 상한을 넘는 쪽 — 앞 패스가 멈추고 위생기는 그 앞까지만 본다. */
    @Test
    fun `원문 상한 크기의 쪽`() = timed("8 MiB", limitMs = 10_000) {
        val unit = "<div class=\"row\"><img src=\"page_files/a.png\" alt=\"a\"><p>가나다라마바사 abc</p></div>\n"
        val r = render(unit.repeat((HtmlPreview.MAX_SOURCE_BYTES / unit.length).toInt()))
        assertTrue(r.html.length <= HtmlPreview.MAX_HTML_CHARS + 1024, "${r.html.length}")
    }
}
