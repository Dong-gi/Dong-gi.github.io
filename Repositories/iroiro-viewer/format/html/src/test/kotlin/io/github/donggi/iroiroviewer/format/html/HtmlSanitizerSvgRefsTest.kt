package io.github.donggi.iroiroviewer.format.html

import io.github.donggi.iroiroviewer.format.UnsupportedFeatures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * SVG 가 다른 문서를 끌어오는 길 둘 — **외래 콘텐츠의 `<style>` 에서 푸는 문자 참조**와 **표현 속성의 `url()`** — 그리고 CSS 의
 * '다른 파일의 조각'. 셋 다 `<use>`·CSS 에서 막은 '같은 문서만' 규칙을 값으로 비켜 갔다 — EPUB 에서는 책
 * 안의 날것 SVG(위생을 거치지 않는다)가 필터·마스크 문서로 실렸다.
 */
class HtmlSanitizerSvgRefsTest {

    private val local = HtmlSanitizer.Resolver { url -> "iro://book/$url" }

    private fun clean(html: String) = HtmlSanitizer.sanitize(html, local)

    private fun styleOf(html: String): String = html.substringAfter("<style>", "").substringBefore("</style>")

    // ---- <style> 의 문자 참조 ------------------------------------------------------------

    /**
     * `<svg>` 안의 `<style>` 은 보통 요소라 브라우저가 참조를 푼다 — `&#117;rl(…)` 이 `url(…)` 이 된다. 푼 글을 위생기에 넣고 `&` 를
     * CSS 이스케이프로 적어, 날글로 읽든 풀어 읽든 같은 CSS 가 되게 한다.
     */
    @Test
    fun 외래_콘텐츠의_style_에서_참조로_적은_주소도_거른다() {
        val out = clean("<svg><style>rect{filter:&#117;rl(img/x.svg#f)}</style><rect/></svg>").html
        val css = styleOf(out)
        assertFalse("x.svg" in css, "다른 SVG 파일의 조각은 가리키지 않는다: $out")
        assertTrue("filter:none" in css, out)
        assertFalse('&' in css, "남은 참조가 브라우저에서 다시 풀린다: $out")
        // 참조로 적은 다른 주소는 위생기를 지나 우리 주소가 된다.
        assertTrue("iro://book/p.png" in styleOf(clean("<svg><style>a{fill:&#117;rl(p.png)}</style></svg>").html))
        // 이름 참조로 적은 괄호도 같다.
        val named = styleOf(clean("<svg><style>a{background:url&lpar;https://evil/x.png&rpar;}</style></svg>").html)
        assertFalse("evil" in named, named)
    }

    /**
     * 글(요소의 내용)에서는 옛 이름이 앞머리만 맞아도 풀린다 — `&quotx` 는 `"x` 다. 속성의 규칙으로 풀면 브라우저가 문자열을 닫는 자리를
     * 우리는 문자열 안으로 보아 그 뒤의 `url(` 을 놓친다.
     */
    @Test
    fun 글의_규칙으로_풀어_문자열을_닫는_참조를_놓치지_않는다() {
        val out = clean("<svg><style>a{content:\"&quotx url(https://evil/x.png) \"}</style></svg>").html
        assertFalse("evil" in out, out)
    }

    @Test
    fun 표에_없는_이름_참조가_든_style_은_버린다() {
        val out = clean("<svg><style>a{b:&NoSuchRef;}</style><g/></svg>").html
        assertFalse("<style>" in out, out)
        assertTrue("<g>" in out || "<g></g>" in out, out)
    }

    /** 참조가 없는 블록(거의 전부)은 예전 그대로다 — `&` 가 글자로만 있는 것도. */
    @Test
    fun 참조가_없는_style_은_그대로다() {
        val css = "a{color:red}b{content:\"A & B\"}"
        assertEquals(css, styleOf(clean("<style>$css</style>").html))
        assertEquals("a{color:red}", styleOf(clean("<svg><style>a{color:red}</style></svg>").html))
    }

    // ---- 표현 속성 ------------------------------------------------------------------------

    @Test
    fun 표현_속성의_주소도_위생기를_지난다() {
        val out = clean(
            "<svg><rect filter=\"url(img/x.svg#f)\" fill=\"url(#grad)\" stroke=\"red\" " +
                "mask=\"url(https://evil/m.svg#m)\" clip-path=\"&#117;rl(img/c.svg#c)\"/></svg>",
        ).html
        assertFalse("x.svg#f" in out, out)
        assertFalse("c.svg#c" in out, "문자 참조를 푼 글로 본다: $out")
        assertFalse("evil" in out, out)
        assertTrue("#grad" in out, "같은 문서 안은 그대로: $out")
        assertTrue("stroke=\"red\"" in out, "참조가 없는 값은 원문 그대로: $out")
        assertTrue("filter=\"none\"" in out, out)
    }

    @Test
    fun 표현_속성의_모르는_이름_참조는_속성째_버린다() {
        val out = clean("<svg><rect filter=\"url&NoSuch;x\" fill=\"blue\"/></svg>").html
        assertFalse("filter" in out, out)
        assertTrue("fill=\"blue\"" in out, out)
    }

    // ---- CSS 의 다른 파일 조각 -------------------------------------------------------------

    /**
     * 다른 파일의 조각으로 요소를 가리키지 못한다. 예전 검사는 이름이 `.svg` 로 끝날 때만 막아 둘이 지나갔다 — 퍼센트로 적은 확장자
     * (`x.sv%67` — 책의 리졸버는 풀어서 찾는다)와, 매니페스트가 SVG 라고 적은 다른 이름(`x.dat`). 앞의 것은 푼 이름으로 보고 지우고,
     * 뒤의 것은 조각을 떼고 가리킨다(이름으로는 가릴 수 없다).
     */
    @Test
    fun CSS_의_다른_파일_조각으로_요소를_가리키지_못한다() {
        for (ref in listOf("img/x.sv%67#f", "img/x.svg#f", "img/X.SVGZ?v=1#f")) {
            val r = HtmlSanitizer.sanitizeCss("a{filter:url($ref)}", local)
            assertEquals("a{filter:none}", r.html, ref)
            assertEquals(1, r.dropped.snapshot()[UnsupportedFeatures.REMOTE_REFERENCE], ref)
        }
        val dat = HtmlSanitizer.sanitizeCss("a{filter:url(img/x.dat#f)}", local)
        assertEquals("a{filter:url(\"iro://book/img/x.dat\")}", dat.html)
        // 같은 문서 안은 그대로다.
        assertTrue("#f" in HtmlSanitizer.sanitizeCss("a{filter:url(#f)}", local).html)
        // 글꼴의 옛 꼼수(`?#iefix`)는 같은 파일을 싣는다 — 세지 않는다.
        val font = HtmlSanitizer.sanitizeCss(
            "@font-face{src:url(f.eot?#iefix) format('embedded-opentype'),url(f.woff) format('woff')}",
            local,
        )
        assertTrue("iro://book/f.eot?" in font.html, font.html)
        assertTrue("iro://book/f.woff" in font.html, font.html)
        assertEquals(null, font.dropped.snapshot()[UnsupportedFeatures.REMOTE_REFERENCE])
    }

    // ---- 글의 규칙으로 푸는 문자 참조 --------------------------------------------------------

    @Test
    fun 글의_규칙은_옛_이름의_앞머리만_맞아도_푼다() {
        assertEquals("\"x <imes &x", CharRefs.decodeText("&quotx &ltimes &ampx").text)
        // 속성의 규칙은 그대로다 — 뒤가 영문·숫자면 풀지 않는다.
        assertEquals("&quotx &ltimes &ampx", CharRefs.decode("&quotx &ltimes &ampx").text)
        // `;` 까지 맞는 이름은 둘 다 표로 푼다. 표에 없는 이름은 둘 다 '모른다'.
        assertEquals("(", CharRefs.decodeText("&lpar;").text)
        assertTrue(CharRefs.decodeText("&NoSuchName;").unknown)
        // 표에 없지만 옛 이름으로 시작하면 글에서는 옛 이름까지만 푼다(명세의 '가장 길게 맞는 것').
        val partial = CharRefs.decodeText("&ampx;")
        assertEquals("&x;", partial.text)
        assertFalse(partial.unknown)
        assertEquals("A<", CharRefs.decodeText("&#65&lt").text)
    }
}
