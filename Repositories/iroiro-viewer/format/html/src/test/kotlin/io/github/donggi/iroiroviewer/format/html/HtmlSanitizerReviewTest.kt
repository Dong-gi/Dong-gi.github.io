package io.github.donggi.iroiroviewer.format.html

import io.github.donggi.iroiroviewer.format.UnsupportedFeatures
import io.github.donggi.iroiroviewer.safety.ParseLimitExceededException
import io.github.donggi.iroiroviewer.safety.ParseLimits
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * HTML 미리보기의 적대적 검토가 위생기에서 찾은 것. EPUB 도 같은 위생기를 지나므로 여기서 박는다.
 */
class HtmlSanitizerReviewTest {

    private val local = HtmlSanitizer.Resolver { url -> "iro://book/$url" }

    private fun clean(html: String) = HtmlSanitizer.sanitize(html, local)

    @Test
    fun 짝_없는_닫는_태그가_뒤의_본문을_지우지_않는다() {
        // 여는 태그로 읽으면 짝을 찾아 문서 끝까지 간다 — `b` 가 사라졌다.
        for (name in listOf("script", "iframe", "object", "template", "noframes", "applet", "frameset", "embed", "frame")) {
            val out = clean("<p>a</p><$name>x</$name></$name><p>b</p>").html
            assertTrue("<p>b</p>" in out, "$name: $out")
            assertFalse("</$name>" in out, "$name: $out")
        }
        assertTrue("<p>b</p>" in clean("</script><p>b</p>").html)
    }

    @Test
    fun frame_은_짝이_없다() {
        // 짝 없는 요소로 알지 못하면 `</frame>` 을 끝까지 찾다가 뒤를 모두 버린다.
        val r = clean("<frame src=\"a.html\"><p>뒤</p>")
        assertTrue("<p>뒤</p>" in r.html, r.html)
        assertEquals(1, r.dropped.snapshot()[UnsupportedFeatures.FRAME])
    }

    @Test
    fun 결과의_상한을_쓰는_도중에_잰다() {
        // `<` 하나가 `&lt;` 넷이 된다. 다 쓴 뒤에 재면 상한의 몇 배를 이미 쥔 뒤다 — 쓰는 도중에 멈추는지 체크포인트 수로 본다.
        // 입력(9만 자)은 상한(10만 자) 안이고 결과는 36만 자다. 쓰는 도중에 재면 2만 5천 자째에서 멈춰 마흔아홉 번쯤 불리고,
        // 다 쓴 뒤에 재는 판이면 태그 512개마다 한 번씩 백칠십 번 넘게 불린다.
        var checks = 0
        assertFailsWith<ParseLimitExceededException> {
            HtmlSanitizer.sanitize("<".repeat(90_000), local, ParseLimits.DEFAULT.copy(maxXmlBytes = 100_000)) { checks++ }
        }
        assertTrue(checks in 1..100, "$checks")
        // 속성 값의 따옴표는 `&quot;` 여섯 자다 — 그 값을 만들기 전에 잰다.
        assertFailsWith<ParseLimitExceededException> {
            HtmlSanitizer.sanitize("<a title='" + "\"".repeat(1_000) + "'>", local, ParseLimits.DEFAULT.copy(maxXmlBytes = 3_000))
        }
        // `<style>` 안의 `<` 는 `\3c ` 넷이 된다.
        assertFailsWith<ParseLimitExceededException> {
            HtmlSanitizer.sanitize("<style>" + "<".repeat(1_000) + "</style>", local, ParseLimits.DEFAULT.copy(maxXmlBytes = 3_000))
        }
        // 상한 안이면 그대로다.
        assertEquals("&lt;".repeat(10), HtmlSanitizer.sanitize("<".repeat(10), local, ParseLimits.DEFAULT.copy(maxXmlBytes = 40)).html)
    }

    @Test
    fun 체크포인트에서_취소가_올라온다() {
        var calls = 0
        assertFailsWith<kotlinx.coroutines.CancellationException> {
            HtmlSanitizer.sanitize("<p>x</p>".repeat(10_000), local) {
                if (++calls == 3) throw kotlinx.coroutines.CancellationException("취소")
            }
        }
        assertEquals(3, calls)
    }

    @Test
    fun style_속성은_문자_참조를_푼_뒤_거른다() {
        // 브라우저에게 이것은 `url(https://…)` 다.
        val r = clean("<p style=\"background:url&lpar;https://x/y.png&rpar;\">a</p>")
        assertFalse("https" in r.html, r.html)
        assertTrue("background:none" in r.html, r.html)
        assertEquals(1, r.dropped.snapshot()[UnsupportedFeatures.REMOTE_REFERENCE])
        // 모르는 이름 참조가 남으면 그 속성을 믿지 않는다.
        assertEquals("<p>b</p>", clean("<p style=\"color:red&lparen;x&rparen;\">b</p>").html)
        // 푼 글을 다시 적는다 — 따옴표는 `&quot;`, `&` 는 `&amp;`.
        assertEquals("<p style=\"font-family:&quot;Noto&quot;\">c</p>", clean("<p style=\"font-family:&quot;Noto&quot;\">c</p>").html)
        assertEquals("<p style=\"content:'a&amp;b'\">d</p>", clean("<p style=\"content:'a&amp;b'\">d</p>").html)
    }

    @Test
    fun 이스케이프로_쓴_주소와_image_set_과_import() {
        val css = "a{background:\\75 rl(https://x/1.png)}b{background:u\\72l(https://x/2.png)}" +
            "c{background:image-set(\"https://x/3.png\" 1x)}d{background:-webkit-image-set('https://x/4.png' 1x)}" +
            "e{filter:\\75 rl(ext.svg#f)}@\\69mport \"https://x/5.css\";f{color:red}"
        for (out in listOf(clean("<style>$css</style>").html, HtmlSanitizer.sanitizeCss(css, local).html)) {
            assertFalse("https" in out || "ext.svg" in out || "mport" in out, out)
            assertTrue("f{color:red}" in out, out)
        }
        // 문서 안의 주소는 이스케이프를 풀어 리졸버에 묻는다.
        assertTrue("url(\"iro://book/a.png\")" in HtmlSanitizer.sanitizeCss("x{background:\\75 rl(a.png)}", local).html)
        // 문자열 안의 `url(` 은 주소가 아니다 — 예전에는 `content:"none"` 으로 망가뜨리고 바깥 참조로 셌다.
        val text = HtmlSanitizer.sanitizeCss("x::before{content:\"url(x)\"}", local)
        assertEquals("x::before{content:\"url(x)\"}", text.html)
        assertTrue(text.dropped.isEmpty, text.dropped.snapshot().toString())
    }

    @Test
    fun SMIL_이_href_를_바꿔_같은_문서_규칙을_비켜_가지_못한다() {
        val out = clean(
            "<svg><use href=\"#x\"><set attributeName=\"href\" to=\"other.svg#g\"/>" +
                "<animate attributeName=\"xlink:href\" values=\"#a;other.svg#b\"/></use>" +
                "<rect><animate attributeName=\"fill\" values=\"url(#a);url(ext.svg#p)\"/>" +
                "<animate attributeName=\"opacity\" values=\"0;1;0\"/><set attributeName=\"fill\" to=\"\\75 rl(ext.svg#q)\"/></rect>" +
                "<path id=\"p\"/><mpath href=\"other.svg#p\"/><textPath href=\"#p\">글</textPath>" +
                "<set href=\"other.svg#t\" attributeName=\"x\" to=\"5\"/>" +
                // 이름을 문자 참조로 적어도 `href` 다.
                "<use href=\"#y\"><set attributeName=\"hr&#101;f\" to=\"other.svg#h\"/></use></svg>",
        ).html
        assertFalse("other.svg" in out || "ext.svg" in out, out)
        assertTrue("values=\"0;1;0\"" in out, out)
        assertTrue("<textpath href=\"#p\">" in out, out)
        assertTrue("to=\"5\"" in out, out)
    }

    /**
     * 잰 값: `@import;` 가 되풀이되면 0.8 M 자에 1초, 8 MiB 에 135초였다. 규칙마다 끝까지 다시 찾았다. 크기는 **옛 판이 확실히
     * 넘는 크기로** 잡았다(3.2 M 자 — 옛 판이면 십몇 초, 지금은 1초 안).
     */
    @Test
    fun 되풀이된_import_가_제곱_시간을_만들지_않는다() {
        for (css in listOf("@import;".repeat(400_000), "@import{}".repeat(360_000), "@import url(a.css);".repeat(170_000))) {
            val t0 = System.nanoTime()
            val r = clean("<style>${css}p{color:red}</style><p>끝</p>")
            val ms = (System.nanoTime() - t0) / 1_000_000
            assertTrue(ms < 3_000, "${css.take(10)}: ${ms}ms")
            assertTrue("p{color:red}" in r.html && "<p>끝</p>" in r.html, r.html.takeLast(80))
            val t1 = System.nanoTime()
            HtmlSanitizer.sanitizeCss(css, local)
            assertTrue((System.nanoTime() - t1) / 1_000_000 < 3_000)
        }
    }
}
