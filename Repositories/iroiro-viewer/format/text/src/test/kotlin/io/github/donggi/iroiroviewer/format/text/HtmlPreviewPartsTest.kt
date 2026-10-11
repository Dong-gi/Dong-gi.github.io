package io.github.donggi.iroiroviewer.format.text

import io.github.donggi.iroiroviewer.charset.TextEncoding
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * HTML 미리보기의 부품 — `<style>` 의 CDATA·중괄호, 스타일시트의 인코딩, DOCTYPE 의 모드, 스크립트의 끝. CSS 의 주소를 읽는 일은
 * 위생기와 한 벌이라 `format:html` 의 `CssRefsTest` 가 맡는다.
 */
class HtmlPreviewPartsTest {

    @Test
    fun `CDATA 껍데기를 벗긴다`() {
        assertEquals(" p{} ", CssUrls.withoutCdata("<![CDATA[ p{} ]]>"))
        assertEquals("/**/p{}/**/", CssUrls.withoutCdata("/*<![CDATA[*/p{}/*]]>*/"))
    }

    /** 짝 없는 `}` 가 우리 `@media` 를 먼저 닫으면 그 뒤의 인쇄용 규칙이 화면에 걸린다. */
    @Test
    fun `media 로 싸기 전에 중괄호의 짝을 맞춘다`() {
        assertEquals("a{b:c} d{e:f}", CssUrls.balanced("a{b:c}} d{e:f}"))
        assertEquals("a{b:c}", CssUrls.balanced("a{b:c"))
        assertEquals("@media x{a{b:c}}", CssUrls.balanced("@media x{a{b:c}"))
        // 문자열·주석·이스케이프 안의 괄호는 세지 않는다.
        assertEquals("a{content:\"}\"}", CssUrls.balanced("a{content:\"}\"}"))
        assertEquals("a{b:c}/* } */", CssUrls.balanced("a{b:c}/* } */"))
        assertEquals("a{b:\\}}", CssUrls.balanced("a{b:\\}}"))
        assertEquals("p{}", CssUrls.balanced("}p{}"))
        assertEquals("no braces", CssUrls.balanced("no braces"))
    }

    @Test
    fun `스타일시트의 인코딩`() {
        val utf8 = Charsets.UTF_8
        assertEquals("a{}", CssText.decode("@charset \"utf-8\";a{}".toByteArray(), utf8))
        assertEquals("가{}", CssText.decode(byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + "가{}".toByteArray(Charsets.UTF_16BE), utf8))
        assertEquals("가{}", CssText.decode(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "가{}".toByteArray(Charsets.UTF_16LE), utf8))
        // `@charset "utf-16"` 은 UTF-8 이다(ASCII 로 쓴 규칙이 UTF-16 일 수 없다).
        assertEquals("가{}", CssText.decode("@charset \"utf-16\";가{}".toByteArray(utf8), Charsets.ISO_8859_1))
        // 모르는 이름·이름 꼴이 아닌 것은 문서의 인코딩.
        assertEquals("가{}", CssText.decode("@charset \"x-no-such\";가{}".toByteArray(utf8), utf8))
        assertEquals("가{}", CssText.decode("@charset \"a b\";가{}".toByteArray(utf8), utf8))
        // 명세의 모양이 아니면(작은따옴표·공백) 규칙이 아니다 — 문서의 인코딩으로 읽는다.
        assertEquals("가{}", CssText.decode("@charset 'euc-kr';가{}".toByteArray(utf8), utf8))
        assertEquals(Charsets.UTF_8, CssText.fallbackOf(TextEncoding.UTF_16LE))
        assertEquals(Charsets.UTF_8, CssText.fallbackOf(null))
    }

    @Test
    fun `DOCTYPE 의 모드`() {
        fun mode(d: String?, closed: Boolean = true) = DocMode.of(d, closed)
        assertEquals(DocMode.Mode.NO_QUIRKS, mode(" html"))
        assertEquals(DocMode.Mode.NO_QUIRKS, mode("html"))
        assertEquals(DocMode.Mode.NO_QUIRKS, mode(" HTML SYSTEM \"about:legacy-compat\""))
        assertEquals(DocMode.Mode.NO_QUIRKS, mode(" html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"x\""))
        assertEquals(DocMode.Mode.LIMITED_QUIRKS, mode(" html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\""))
        assertEquals(DocMode.Mode.LIMITED_QUIRKS, mode(" html PUBLIC '-//W3C//DTD HTML 4.01 Frameset//EN' 'x'"))
        assertEquals(DocMode.Mode.QUIRKS, mode(" html PUBLIC \"-//W3C//DTD HTML 4.01 Frameset//EN\""))
        assertEquals(DocMode.Mode.QUIRKS, mode(null))
        assertEquals(DocMode.Mode.QUIRKS, mode(" html", closed = false))
        assertEquals(DocMode.Mode.QUIRKS, mode(""))
        assertEquals(DocMode.Mode.QUIRKS, mode(" svg"))
        assertEquals(DocMode.Mode.QUIRKS, mode(" html PUBLIC \"HTML\""))
        assertEquals(DocMode.Mode.QUIRKS, mode(" html PUBLIC \"-//IETF//DTD HTML 2.0//EN\""))
        assertEquals(DocMode.Mode.QUIRKS, mode(" html PUBLIC \"-//Netscape Comm. Corp.//DTD HTML//EN\""))
        // 따옴표가 닫히기 전에 `>` 가 왔다(문자열이 그 자리에서 끝났다) — 강제 쿼크.
        assertEquals(DocMode.Mode.QUIRKS, mode(" html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN"))
        assertEquals(DocMode.Mode.QUIRKS, mode(" html PUBLIC"))
        assertEquals(DocMode.Mode.QUIRKS, mode(" html PUBLICX \"a\""))
        assertEquals(DocMode.Mode.QUIRKS, mode(" html PUBLIC \"a\" x"))
        // 시스템 식별자 뒤의 군글은 강제 쿼크가 아니다.
        assertEquals(DocMode.Mode.NO_QUIRKS, mode(" html SYSTEM \"a\" x"))
    }

    @Test
    fun `스크립트의 끝`() {
        fun end(body: String) = ScriptData.end(body, 0)
        assertEquals(5, end("var a</script>"))
        assertEquals(3, end("x()</SCRIPT\n>"))
        // `</scriptx` 와 `</script` 다음에 글자가 오는 것은 끝이 아니다.
        assertEquals(15, end("</scriptx></a><</script/>"))
        // `<!--` 안의 `<script>` 뒤 `</script>` 는 글이다. `-->` 가 그 상태를 푼다.
        val doubled = "<!-- w(\"<script>a</script>\") -->"
        assertEquals(doubled.length, end("$doubled</script>"))
        // `<!--` 안이라도 `<script` 가 없으면 `</script>` 가 끝이다.
        assertEquals(8, end("<!-- a()</script> -->"))
        // `<!-->` 는 그 자리에서 풀린다.
        assertEquals(5, end("<!--></script>"))
        // 스크립트 안의 여는 태그는 글이다(`<!--` 밖이면).
        assertEquals(8, end("<script></script>"))
        assertEquals(7, end("no end."))
    }
}
