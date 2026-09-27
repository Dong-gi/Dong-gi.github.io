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
 * **이 시험이 지키는 것은 모양이 아니라 경계다.**
 *
 * 위생기의 결과가 예쁜 HTML 인지는 중요하지 않다 — 그리는 것은 WebView 이고 그쪽이
 * 우리보다 관대하다. 중요한 것은 **무엇이 통과하지 못하는가** 하나이고, 그래서 시험은
 * 거의 전부 `assertFalse(… in result.html)` 꼴이다.
 *
 * 대상 목록을 적어 두는 대신 **우리가 실제로 두려워하는 것**으로 적는다 —
 * 스크립트가 도는 것, 파일이 바깥으로 나가는 것, 우리가 만든 태그를 빠져나가는 것.
 */
class HtmlSanitizerTest {

    /**
     * 문서 안의 상대경로는 전부 내준다.
     *
     * **리졸버가 스킴을 보지 않는 것이 요점이다** — 그 판정은 [Urls] 가 위생기 안에서
     * 하고, 리졸버는 '이 상대경로가 이 문서 안에 있는가' 만 답한다. 시험이 이렇게 순진한
     * 리졸버를 쓰는 것은 **위생기가 스스로 막는지**를 확인하려는 것이다.
     */
    private val local = HtmlSanitizer.Resolver { url -> "iro://book/$url" }

    private fun clean(html: String) = HtmlSanitizer.sanitize(html, local)

    @Test
    fun 스크립트는_내용까지_사라진다() {
        val r = clean("<p>앞</p><script>alert(1)</script><p>뒤</p>")
        assertFalse("alert" in r.html, r.html)
        assertFalse("script" in r.html, r.html)
        assertTrue("앞" in r.html && "뒤" in r.html, "본문까지 날아갔다: ${r.html}")
        assertEquals(1, r.dropped.snapshot()[UnsupportedFeatures.SCRIPT])
    }

    @Test
    fun 중첩된_요소를_첫_닫는_태그에서_끝내지_않는다() {
        // `</object>` 하나를 끝으로 보면 뒤의 '샌다' 가 본문으로 나온다.
        val r = clean("<div><object><object>샌다</object></object></div><p>본문</p>")
        assertFalse("샌다" in r.html, r.html)
        assertTrue("본문" in r.html, r.html)
    }

    @Test
    fun 이벤트_처리기는_이름을_적어_두지_않고_막는다() {
        // 목록으로 막으면 목록에 없는 새 이벤트가 그대로 지나간다.
        val r = clean("""<img src="a.png" onerror="x()" onanimationstart="y()" ONLOAD="z()">""")
        assertFalse("onerror" in r.html.lowercase(), r.html)
        assertFalse("onanimationstart" in r.html.lowercase(), r.html)
        assertFalse("onload" in r.html.lowercase(), r.html)
        assertTrue("iro://book/a.png" in r.html, r.html)
    }

    @Test
    fun 바깥을_가리키는_참조가_사라진다() {
        val r = clean(
            """<img src="https://tracker.example/pixel.gif"><img src="//cdn/x.png">""" +
                """<link rel="stylesheet" href="http://x/y.css">""" +
                """<link rel="preload" href="local.css">"""
        )
        assertFalse("tracker" in r.html, r.html)
        assertFalse("cdn" in r.html, r.html)
        assertFalse("http" in r.html, r.html)
        // `rel=preload` 는 내줄 수 있는 자리를 가리켜도 버린다 — 그리는 데 쓰지 않는다.
        assertFalse("preload" in r.html, r.html)
    }

    @Test
    fun 스타일시트는_남고_주소만_바뀐다() {
        val r = clean("""<link rel="stylesheet" href="css/book.css"/>""")
        assertTrue("iro://book/css/book.css" in r.html, r.html)
    }

    @Test
    fun javascript_스킴은_스크립트로_센다() {
        val r = clean("""<a href="javascript:alert(1)">눌러</a>""")
        assertFalse("javascript" in r.html.lowercase(), r.html)
        assertTrue("눌러" in r.html, r.html)
        assertEquals(1, r.dropped.snapshot()[UnsupportedFeatures.SCRIPT])
    }

    @Test
    fun 문서_안_앵커는_그대로_둔다() {
        val r = clean("""<a href="#note1">주</a>""")
        assertTrue("""href="#note1"""" in r.html, r.html)
    }

    @Test
    fun 표지에_흔한_data_이미지는_남긴다() {
        val src = "data:image/png;base64,iVBORw0KGgo="
        assertTrue(src in clean("""<img src="$src">""").html)
        // 그러나 다른 data: 는 아니다 — `data:text/html` 은 문서를 하나 더 여는 길이다.
        assertFalse("text/html" in clean("""<img src="data:text/html,<b>x">""").html)
    }

    @Test
    fun 속성_값으로_태그를_빠져나갈_수_없다() {
        val r = clean("""<p title='a" onmouseover="steal()'>글</p>""")
        assertFalse("onmouseover=\"" in r.html, r.html)
        assertTrue("글" in r.html, r.html)
    }

    @Test
    fun 엔티티를_두_번_이스케이프하지_않는다() {
        val r = clean("""<p title="a&amp;b">x &amp; y</p>""")
        assertFalse("&amp;amp;" in r.html, r.html)
        assertTrue("a&amp;b" in r.html, r.html)
    }

    @Test
    fun base_는_상대경로를_통째로_바꾸므로_지운다() {
        val r = clean("""<head><base href="https://evil/"></head><img src="a.png">""")
        assertFalse("base" in r.html, r.html)
        assertTrue("iro://book/a.png" in r.html, r.html)
    }

    @Test
    fun 폼은_태그만_지우고_글은_남긴다() {
        val r = clean("<form action='http://x'><label>이름</label><input value='v'></form>")
        assertFalse("<form" in r.html, r.html)
        assertFalse("<input" in r.html, r.html)
        assertTrue("이름" in r.html, r.html)
    }

    @Test
    fun 미디어의_대체_글은_남는다() {
        val r = clean("<video src='a.mp4'>이 브라우저는 영상을 재생하지 못합니다</video>")
        assertFalse("<video" in r.html, r.html)
        assertTrue("재생하지 못합니다" in r.html, r.html)
    }

    @Test
    fun 태그가_아닌_꺾쇠는_글자로_남는다() {
        val r = clean("2 < 3 이고 4 > 3")
        assertTrue("&lt; 3" in r.html, r.html)
    }

    @Test
    fun 상한을_넘는_입력은_거절한다() {
        val small = ParseLimits.DEFAULT.copy(maxXmlBytes = 100)
        assertFailsWith<ParseLimitExceededException> {
            HtmlSanitizer.sanitize("x".repeat(101), local, small)
        }
    }

    @Test
    fun 닫히지_않은_태그에서_멈추지_않는다() {
        // 잘린 파일이 무한 루프를 만들면 안 된다. 결과가 무엇이든 끝나기만 하면 된다.
        clean("<div><p>글<img src=\"a.png\"")
        clean("<!--주석이 닫히지 않았다")
        clean("<")
        clean("<style>body{")
        clean("<!DOCTYPE html [ <!ENTITY a \"b\">")
    }

    // ---- 실세계 말뭉치가 잡은 것(XHTML 을 text/html 로 내준다) ------------------------------
    //
    // 결과는 `text/html` 로 WebView 에 들어간다. XHTML 에서 뜻이 있던 문법이 HTML 파서에서 다르게 읽히는
    // 자리를 우리가 옮겨 적어야 한다 — 글 대조로는 보이지 않고 화면에서만 보이는 결함이다.

    @Test
    fun DOCTYPE_의_내부_부분집합을_글로_남기지_않는다() {
        // W3C epub-tests 의 pub-xml-external-id 와 같은 모양. 첫 `>` 에서 끝내면 `]>` 가 화면 맨 위에 찍혔다.
        val xhtml = "<?xml version=\"1.0\"?>\n<!DOCTYPE html \n [\n <!ENTITY xxe SYSTEM \"foo.xhtml\">\n ]>\n" +
            "<html><body><p>본문</p><p>&xxe;</p></body></html>"
        val r = clean(xhtml)
        assertEquals("<html>", r.html.trim().take(6), r.html)
        assertFalse("]>" in r.html, r.html)
        assertFalse("ENTITY" in r.html, r.html)
        // 바깥 엔티티는 풀지 않는다 — 글자 그대로 남아 WebView 가 모르는 엔티티로 보인다.
        assertTrue("&xxe;" in r.html, r.html)
        // 부분집합이 없는 보통의 DOCTYPE 과, 첫 `>` 뒤에 나오는 `[` 는 예전 그대로다.
        assertEquals("<p>가</p>", clean("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.1//EN\" \"x.dtd\"><p>가</p>").html)
        assertEquals("<p>[나]</p>", clean("<!DOCTYPE html><p>[나]</p>").html)
    }

    @Test
    fun 스스로_닫힌_요소에_닫는_태그를_쓴다() {
        // XHTML 의 `<div/>` 는 빈 요소지만 HTML 파서는 여는 태그로 읽는다 — 뒤의 본문이 그 안에 들어간다.
        assertEquals("<div class=\"body\"></div><p>뒤</p>", clean("<div class=\"body\"/><p>뒤</p>").html)
        assertEquals("<a id=\"p1\"></a>글", clean("<a id=\"p1\"/>글").html)
        // `<title/>` 을 그대로 두면 본문 전체가 제목이 되어 화면이 빈다.
        assertEquals("<head><title></title></head><body>글</body>", clean("<head><title/></head><body>글</body>").html)
        // 짝이 없는 요소는 그대로다.
        assertEquals("<br/><hr/>", clean("<br/><hr>").html)
        // SVG 안에서도 뜻이 같다.
        assertEquals("<svg><path d=\"M0 0\"></path></svg>", clean("<svg><path d=\"M0 0\"/></svg>").html)
    }

    @Test
    fun 짝_없는_요소의_닫는_태그를_쓰지_않는다() {
        // XHTML 의 `<br></br>` 은 줄바꿈 하나다. `</br>` 을 그대로 내면 HTML 파서가 그것을 `<br>` 로 읽어
        // 줄바꿈이 둘이 된다(검토가 잡았다 — `<div/>` 와 같은 갈래).
        assertEquals("""<p>가<br/>나</p>""", clean("""<p>가<br></br>나</p>""").html)
        assertEquals("""<img alt="x"/><hr/>""", clean("""<img alt="x"></img><hr></hr>""").html)
        // 짝이 있는 요소의 닫는 태그는 그대로다.
        assertEquals("""<p><span>가</span></p>""", clean("""<p><span>가</span></p>""").html)
    }

    @Test
    fun 보이지_않는_문서_정보는_버린_것으로_세지_않는다() {
        // 알림은 보이던 것이 빠졌을 때의 말이다. 실세계 책은 장마다 `<meta charset>` 과 `<link rel="license">`
        // 를 두는데, 예전에는 그것만으로 '알 수 없는 요소'·'바깥을 가리키는 참조' 가 장 수만큼 쌓였다.
        val r = clean(
            """<head><meta charset="utf-8"/><link rel="license" href="http://creativecommons.org/x"/>""" +
                """<link rel="stylesheet" href="http://x/y.css"/></head><body><p>글</p></body>"""
        )
        assertEquals(mapOf(UnsupportedFeatures.REMOTE_REFERENCE to 1), r.dropped.snapshot(), "바깥 스타일시트 하나만 센다")
        assertFalse("meta" in r.html || "license" in r.html || "http" in r.html, r.html)
    }

    @Test
    fun xml_lang_이_HTML_에서도_언어로_읽힌다() {
        // HTML 파서는 `xml:lang` 을 언어로 보지 않는다 — 일본어 책의 한자가 기기 로캘의 자형으로 그려졌다.
        assertEquals(
            "<html xml:lang=\"ja\" lang=\"ja\"><body><p xml:lang=\"en\" lang=\"fr\">x</p><p lang=\"ko\">y</p></body></html>",
            clean("<html xml:lang=\"ja\"><body><p xml:lang=\"en\" lang=\"fr\">x</p><p lang=\"ko\">y</p></body></html>").html,
        )
    }

    @Test
    fun 스스로_닫힌_style_이_본문을_삼키지_않는다() {
        // 예전에는 다음 `</style` 까지(없으면 끝까지)를 CSS 로 읽어 본문이 통째로 `<style>` 안에 들어갔다 —
        // 글자는 결과에 남지만 WebView 는 그것을 스타일로 읽어 아무것도 그리지 않는다.
        val r = clean("<head><style/></head><body><p>본문이 남는다</p></body>")
        assertEquals("<head></head><body><p>본문이 남는다</p></body>", r.html)
    }

    @Test
    fun 책의_style_안의_head_닫기로_껍데기를_속여_위생을_건너뛰지_못한다() {
        // 검토가 크롬에서 재현했다: 우리 `<style>` 이 책의 CSS 한가운데 끼면 우리 `</style>` 이 책의 것을 먼저 닫아
        // 뒤의 CSS 글자가 위생을 거치지 않은 마크업(`<img onerror>`·`<iframe>`)이 됐다.
        val raw = "<html><head><style>p{color:#333}</head><img src=\"pic.png\" onerror=\"x()\"/><iframe srcdoc=\"a\"></iframe>" +
            "</style></head><body><p>글</p></body></html>"
        val out = HtmlShell.wrap(clean(raw).html)
        assertFalse("<img" in out, out)
        assertFalse("<iframe" in out, out)
        // 우리 스타일은 진짜 `</head>` 앞에 들어가고, 책의 `<style>` 은 하나로 남는다.
        assertTrue(out.indexOf("pre{white-space:pre-wrap;}") > out.indexOf("p{color:#333}"), out)
        assertEquals(2, Regex("<style>").findAll(out).count(), out)
    }

    @Test
    fun svg_안의_style_은_마크업으로_읽히지_않는다() {
        // `<svg>`·`<math>` 안의 `<style>` 은 HTML 파서에게 보통 요소다 — 그 안의 글이 태그로 읽혔다(검토가 재현).
        for (root in listOf("svg", "math")) {
            val out = clean("<$root><style><img src=\"/px.png\" onerror=\"x()\"><form action=\"/steal\"><input name=\"pw\"></form></style></$root>").html
            assertFalse("<img" in out || "<form" in out || "<input" in out, out)
            assertTrue("\\3c img" in out, out) // CSS 로서는 같은 글자다
        }
    }

    @Test
    fun 되풀이된_DOCTYPE_이_제곱_시간을_만들지_않는다() {
        // 검토가 잰 값: 0.5 MB 에 22초(정규식 판). 부분집합의 끝은 첫 DOCTYPE 에서만, 정해진 폭 안에서만 찾는다.
        // 입력을 2.2 MB 로 둔다 — 제곱인 옛 판은 이 기계에서 0.56 MB 에 1.5초였으므로 여기서 20초를 넘고, 선형인 지금 판은
        // 수십 ms 다. 0.56 MB 로는 옛 판도 3초 안에 들어와 시험이 결함을 보지 못했다(되돌려 확인했다).
        val input = "<!DOCTYPE a [>".repeat(160_000) + "<p>끝</p>"
        val started = System.nanoTime()
        val out = clean(input).html
        val ms = (System.nanoTime() - started) / 1_000_000
        assertTrue("<p>끝</p>" in out, out.takeLast(200))
        assertTrue(ms < 3_000, "${ms}ms")
        // 진짜 내부 부분집합은 그대로 건너뛴다(`]>` 가 화면에 찍히지 않는다).
        val subset = clean("<!DOCTYPE html [<!ENTITY x \"y\">]><p>본문</p>").html
        assertEquals("<p>본문</p>", subset)
    }

    @Test
    fun use_는_같은_문서_안만_가리킨다() {
        // 다른 SVG 파일을 가리키는 `<use>` 는 그 문서를 장 안에 복제한다 — 그 SVG 는 위생 없이 나간다.
        val out = clean("<svg><use href=\"ext.svg#g\"/><use xlink:href=\"ext.svg#h\"/><use href=\"#local\"/></svg>" +
            "<p style=\"filter:url(ext.svg#f);clip-path:url(#c)\">글</p>")
        assertFalse("ext.svg" in out.html, out.html)
        assertTrue("href=\"#local\"" in out.html, out.html)
        assertTrue("clip-path:url(&quot;#c&quot;)" in out.html, out.html) // 속성 값 안의 따옴표는 `&quot;` 로 나간다
        assertEquals(3, out.dropped.snapshot()[UnsupportedFeatures.REMOTE_REFERENCE], out.dropped.snapshot().toString())
    }
}
