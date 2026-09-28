package io.github.donggi.iroiroviewer.format.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 마크다운 → HTML. 기대값의 대부분은 CommonMark 명세(0.31)·GFM 명세의 예제를 우리 출력 모양으로 옮긴 것이다 —
 * 우리는 줄바꿈을 끼우지 않고, 바깥 링크를 `<span class="link">` 로, 그림을 번호로 쓴다.
 */
class MarkdownTest {

    private fun html(md: String): String = MarkdownPreview.body(md).html

    // ---- 제목 ------------------------------------------------------------------

    @Test
    fun `ATX 제목`() {
        assertEquals("<h1 id=\"foo\">foo</h1>", html("# foo"))
        assertEquals("<h3 id=\"foo\">foo</h3>", html("### foo ###"))
        assertEquals("<h6 id=\"foo\">foo</h6>", html("###### foo"))
        // 일곱은 제목이 아니고, 공백 없는 `#5` 도 아니다.
        assertEquals("<p>####### foo</p>", html("####### foo"))
        assertEquals("<p>#5 bolt</p>", html("#5 bolt"))
        // 닫는 `#` 은 앞에 공백이 있을 때만.
        assertEquals("<h1 id=\"c\">C#</h1>", html("# C#"))
        assertEquals("<h2></h2>", html("## "))
    }

    @Test
    fun `Setext 제목과 가로줄`() {
        assertEquals("<h1 id=\"foo\">Foo</h1>", html("Foo\n==="))
        assertEquals("<h2 id=\"foo-bar\">Foo\nbar</h2>", html("Foo\nbar\n---"))
        assertEquals("<hr>", html("---"))
        assertEquals("<hr>", html("* * *"))
        assertEquals("<hr>", html("___"))
        assertEquals("<p>a</p><hr>", html("a\n\n---"))
    }

    @Test
    fun `제목 자리 이름 — 한글·중복`() {
        val out = html("# 설치 방법\n## 설치 방법\n# Hello, World!")
        assertEquals(
            "<h1 id=\"설치-방법\">설치 방법</h1><h2 id=\"설치-방법-1\">설치 방법</h2>" +
                "<h1 id=\"hello-world\">Hello, World!</h1>",
            out,
        )
    }

    // ---- 문단·줄바꿈 ----------------------------------------------------------------

    @Test
    fun `문단과 줄바꿈`() {
        assertEquals("<p>aaa\nbbb</p><p>ccc</p>", html("aaa\nbbb\n\nccc"))
        assertEquals("<p>foo<br>bar</p>", html("foo  \nbar"))
        assertEquals("<p>foo<br>bar</p>", html("foo\\\nbar"))
        // 마지막 줄 끝의 공백은 줄바꿈이 아니다.
        assertEquals("<p>foo</p>", html("foo  "))
        // 이어지는 줄의 앞 공백은 버린다.
        assertEquals("<p>aaa\nbbb</p>", html("  aaa\n bbb"))
    }

    // ---- 강조 ------------------------------------------------------------------

    @Test
    fun `강조와 굵게`() {
        assertEquals("<p><em>foo</em> <em>bar</em></p>", html("*foo* _bar_"))
        assertEquals("<p><strong>foo</strong> <strong>bar</strong></p>", html("**foo** __bar__"))
        assertEquals("<p><em><strong>foo</strong></em></p>", html("***foo***"))
        assertEquals("<p><em>foo <strong>bar</strong> baz</em></p>", html("*foo **bar** baz*"))
        // 낱말 안의 밑줄은 강조가 아니다.
        assertEquals("<p>snake_case_name</p>", html("snake_case_name"))
        // 공백 앞뒤의 별은 여닫지 못한다.
        assertEquals("<p>a * foo bar*</p>", html("a * foo bar*"))
        // 셋의 규칙.
        assertEquals("<p><em>foo**bar</em></p>", html("*foo**bar*"))
    }

    @Test
    fun `한국어 옆의 강조`() {
        assertEquals("<p><strong>강조</strong>다</p>", html("**강조**다"))
        assertEquals("<p>이것은 <em>기울임</em>입니다</p>", html("이것은 *기울임*입니다"))
    }

    @Test
    fun `취소선`() {
        assertEquals("<p><del>지움</del> 과 <del>하나</del></p>", html("~~지움~~ 과 ~하나~"))
        // 셋 이상은 글자다(줄 맨 앞의 `~~~` 는 울타리라 가운데에 둔다).
        assertEquals("<p>a ~~~셋~~~ b</p>", html("a ~~~셋~~~ b"))
        // 여닫는 수가 다르면 짝이 아니다.
        assertEquals("<p>~~a~</p>", html("~~a~"))
    }

    // ---- 코드 ------------------------------------------------------------------

    @Test
    fun `줄 안 코드`() {
        assertEquals("<p><code>a&lt;b</code></p>", html("`a<b`"))
        assertEquals("<p><code>foo ` bar</code></p>", html("`` foo ` bar ``"))
        assertEquals("<p><code>foo\\</code>bar`</p>", html("`foo\\`bar`"))
        // 짝이 없으면 글자다.
        assertEquals("<p>```foo``</p>", html("```foo``"))
        // 코드 안의 별은 강조가 아니다.
        assertEquals("<p><code>*a*</code></p>", html("`*a*`"))
    }

    @Test
    fun `울타리 코드 블록`() {
        assertEquals(
            "<pre><code class=\"language-text\">a &lt; b\n  c\n</code></pre>",
            html("```text\na < b\n  c\n```"),
        )
        assertEquals("<pre><code>x\n</code></pre>", html("~~~\nx\n~~~"))
        // 닫히지 않으면 문서 끝까지.
        assertEquals("<pre><code>x\ny\n</code></pre>", html("```\nx\ny"))
        // 울타리 안의 마크다운은 글자다.
        assertEquals("<pre><code># no\n*no*\n</code></pre>", html("```\n# no\n*no*\n```"))
    }

    @Test
    fun `코드 블록을 칠한다`() {
        val out = html("```kotlin\nval s = \"x\" // c\n```")
        assertTrue(out.startsWith("<pre><code class=\"language-kotlin\">"), out)
        assertTrue("<span class=\"t-kw\">val</span>" in out, out)
        assertTrue("<span class=\"t-str\">&quot;x&quot;</span>" in out, out)
        assertTrue("<span class=\"t-com\">// c</span>" in out, out)
    }

    /**
     * 코드 블록의 긴 줄은 뷰어의 행 크기로 끊어 훑개에 넘긴다 — 한 번에 넘기면 쓰지도 않을 조각이 줄 길이만큼 쌓인다.
     * 끊은 자리에서는 상태를 씻지 않고(줄이 끝난 것이 아니다), 진짜 줄바꿈에서만 씻는다.
     */
    @Test
    fun `코드 블록의 긴 줄은 끊어서 칠한다`() {
        val rows = ArrayList<Int>()
        val states = ArrayList<Int>()
        var washes = 0
        val probe = object : RowHighlighter {
            override val label = "시험"
            override val multiline = true
            override fun highlight(row: String, startState: Int, out: MutableList<Span>): Int {
                rows += row.length
                states += startState
                out += Span(0, row.length, TokenKind.STRING)
                return startState + 1
            }
            override fun afterNewline(state: Int): Int {
                washes++
                return 100
            }
        }
        val w = io.github.donggi.iroiroviewer.format.html.HtmlWriter()
        val long = "x".repeat(io.github.donggi.iroiroviewer.safety.TextLimits.SEGMENT_CHARS * 2 + 10)
        MdRenderer.writeHighlighted(w, "$long\nab", probe)
        assertEquals(listOf(4096, 4096, 10, 2), rows)
        // 끊은 자리에서는 상태가 이어지고, 줄바꿈에서 한 번만 씻는다.
        assertEquals(listOf(0, 1, 2, 100), states)
        assertEquals(1, washes)
        assertEquals(long + "\nab", Regex("<[^>]+>").replace(w.toString(), ""))

        // 쓰개가 차면 더 훑지 않는다.
        rows.clear()
        val small = io.github.donggi.iroiroviewer.format.html.HtmlWriter(1_000)
        MdRenderer.writeHighlighted(small, "y".repeat(1_000_000), probe)
        assertTrue(rows.size <= 2, "쓰개가 찬 뒤에도 훑었다: ${rows.size}")
    }

    @Test
    fun `들여쓴 코드 블록`() {
        assertEquals("<pre><code>a\n  b\n</code></pre>", html("    a\n      b\n\n"))
        // 문단을 끊지 못한다.
        assertEquals("<p>foo\nbar</p>", html("foo\n    bar"))
    }

    // ---- 인용·목록 -------------------------------------------------------------------

    @Test
    fun `인용과 게으른 이어짐`() {
        assertEquals("<blockquote><p>a\nb</p></blockquote>", html("> a\nb"))
        assertEquals("<blockquote><blockquote><p>a</p></blockquote></blockquote>", html(">> a"))
        assertEquals("<blockquote><h1 id=\"t\">t</h1></blockquote><p>x</p>", html("> # t\n\nx"))
    }

    @Test
    fun `목록 — 촘촘함과 느슨함`() {
        assertEquals("<ul><li>a</li><li>b</li></ul>", html("- a\n- b"))
        assertEquals("<ul><li><p>a</p></li><li><p>b</p></li></ul>", html("- a\n\n- b"))
        assertEquals("<ol start=\"3\"><li>a</li><li>b</li></ol>", html("3. a\n4. b"))
        assertEquals("<ol><li>a</li></ol>", html("1) a"))
        // 표지가 바뀌면 새 목록이다.
        assertEquals("<ul><li>a</li></ul><ul><li>b</li></ul>", html("- a\n+ b"))
        // 항목 안의 두 블록 사이에 빈 줄 → 느슨.
        assertEquals("<ul><li><p>a</p><p>b</p></li></ul>", html("- a\n\n  b"))
        // 끝의 빈 줄은 느슨하게 만들지 않는다.
        assertEquals("<ul><li>a</li><li>b</li></ul><p>c</p>", html("- a\n- b\n\nc"))
    }

    @Test
    fun `겹친 목록`() {
        assertEquals(
            "<ul><li>a<ul><li>b<ol><li>c</li></ol></li></ul></li><li>d</li></ul>",
            html("- a\n  - b\n    1. c\n- d"),
        )
    }

    @Test
    fun `문단을 끊는 목록`() {
        assertEquals("<p>The number of windows in my house is\n14.  The number of doors is 6.</p>",
            html("The number of windows in my house is\n14.  The number of doors is 6."))
        assertEquals("<p>a</p><ol><li>b</li></ol>", html("a\n1. b"))
        assertEquals("<p>a</p><ul><li>b</li></ul>", html("a\n- b"))
    }

    @Test
    fun `할 일 목록`() {
        val box = " role=\"checkbox\" aria-checked=\"%s\" aria-disabled=\"true\""
        assertEquals(
            "<ul><li class=\"task\"><span class=\"task-box\"${box.format("false")}></span>할 일</li>" +
                "<li class=\"task\"><span class=\"task-box done\"${box.format("true")}></span>끝낸 일</li>" +
                "<li>[y] 보통</li></ul>",
            html("- [ ] 할 일\n- [x] 끝낸 일\n- [y] 보통"),
        )
        // 위생을 지나도 낭독기가 읽을 역할과 상태가 남는다.
        val page = MarkdownPreview.render("- [x] 끝").html
        assertTrue("role=\"checkbox\" aria-checked=\"true\"" in page, page)
    }

    /** 명세가 벗기는 것은 공백·탭뿐이다. 전각 들여쓰기(일본어 글에서 흔하다)와 줄바꿈 없는 공백은 글이다. */
    @Test
    fun `유니코드 공백은 벗기지 않는다`() {
        assertEquals("<p>　들여쓴 문단</p>", html("　들여쓴 문단"))
        assertEquals("<p>끝 </p>", html("끝 "))
        assertEquals("<h1 id=\"제목\">　제목</h1>", html("# 　제목"))
        assertEquals(
            "<div class=\"tbl\"><table><thead><tr><th>　a</th></tr></thead></table></div>",
            html("|　a |\n|-|"),
        )
    }

    @Test
    fun `문단을 끊는 블록들`() {
        assertEquals("<p>a</p><h1 id=\"b\">b</h1>", html("a\n# b"))
        assertEquals("<p>a</p><pre><code>b\n</code></pre>", html("a\n```\nb\n```"))
        assertEquals("<p>a</p><blockquote><p>b</p></blockquote>", html("a\n> b"))
        assertEquals("<p>a</p><hr>", html("a\n***"))
    }

    @Test
    fun `목록 항목 안의 블록`() {
        assertEquals(
            "<ul><li>a<pre><code>code\n</code></pre></li><li>b</li></ul>",
            html("- a\n  ```\n  code\n  ```\n- b"),
        )
        // 게으른 이어짐.
        assertEquals("<ul><li>a\nb</li></ul>", html("- a\nb"))
        // 탭은 칸으로 편다.
        assertEquals("<ul><li>foo</li></ul>", html("-\tfoo"))
        // 빈 줄로 시작하는 항목.
        assertEquals("<ul><li>foo</li></ul>", html("-\n  foo"))
        // 들여쓰기가 모자라면 항목 밖이다.
        assertEquals("<ul><li>a</li></ul><p>b</p>", html("- a\n\nb"))
        assertEquals("<ol><li>a</li></ol><ul><li>b</li></ul>", html("1. a\n- b"))
    }

    @Test
    fun `링크의 여러 모양`() {
        assertEquals("<p><span class=\"link\">a</span></p>", html("[a](foo(bar))"))
        assertEquals("<p><span class=\"link\">a</span></p>", html("[a](<b c>)"))
        // 그림을 품은 링크.
        val r = MarkdownPreview.body("[![배지](badge.svg)](https://ci)")
        assertEquals("<p><span class=\"link\"><img src=\"img/0\" alt=\"배지\"></span></p>", r.html)
        // 목적지의 엔티티와 이스케이프를 푼다.
        assertEquals("<p><a href=\"#a&amp;b\">x</a></p>", html("[x](#a&amp;b)"))
        assertEquals("<p><a href=\"#a*b\">x</a></p>", html("[x](#a\\*b)"))
        // 문단 끝의 백슬래시는 글자다.
        assertEquals("<p>foo\\</p>", html("foo\\"))
    }

    // ---- 표 --------------------------------------------------------------------

    @Test
    fun `표와 정렬`() {
        assertEquals(
            "<div class=\"tbl\"><table><thead><tr><th style=\"text-align:left\">a</th>" +
                "<th style=\"text-align:center\">b</th><th>c</th></tr></thead><tbody><tr>" +
                "<td style=\"text-align:left\"><strong>1</strong></td><td style=\"text-align:center\">x|y</td>" +
                "<td></td></tr></tbody></table></div>",
            html("| a | b | c |\n|:--|:-:|---|\n| **1** | x\\|y |"),
        )
    }

    /** GFM 예제 200 — 칸 안의 `\|` 는 줄 안 코드 안에서도 `|` 다. `\\|` 는 역슬래시 뒤의 칸 경계다. */
    @Test
    fun `표 칸의 이스케이프한 파이프`() {
        assertEquals(
            "<div class=\"tbl\"><table><thead><tr><th>f|oo</th></tr></thead><tbody>" +
                "<tr><td><code>b|az</code></td></tr><tr><td>b <strong>|</strong> im</td></tr></tbody></table></div>",
            html("| f\\|oo  |\n| ------ |\n| `b\\|az` |\n| b **\\|** im |"),
        )
        assertEquals(
            "<div class=\"tbl\"><table><thead><tr><th>a\\</th><th>b</th></tr></thead></table></div>",
            html("| a\\\\| b |\n|-|-|"),
        )
    }

    @Test
    fun `표는 빈 줄이나 다른 블록에서 끝나고 앞 줄은 문단으로 남는다`() {
        val out = html("앞 문단\na | b\n--|--\n1 | 2\n> 인용")
        assertEquals(
            "<p>앞 문단</p><div class=\"tbl\"><table><thead><tr><th>a</th><th>b</th></tr></thead>" +
                "<tbody><tr><td>1</td><td>2</td></tr></tbody></table></div><blockquote><p>인용</p></blockquote>",
            out,
        )
    }

    @Test
    fun `표가 아닌 것`() {
        // 파이프가 없으면 Setext 제목이다.
        assertEquals("<h2 id=\"a\">a</h2>", html("a\n---"))
        // 칸 수가 다르면 표가 아니다.
        assertEquals("<p>| a | b |\n| --- |</p>", html("| a | b |\n| --- |"))
    }

    // ---- 링크·그림 -----------------------------------------------------------------

    @Test
    fun `바깥 링크는 누를 수 없는 글자로`() {
        assertEquals("<p><span class=\"link\">예제</span></p>", html("[예제](https://example.com \"제목\")"))
        assertEquals("<p><span class=\"link\">https://example.com</span></p>", html("<https://example.com>"))
        assertEquals("<p><span class=\"link\">me@example.com</span></p>", html("<me@example.com>"))
        assertFalse("href" in html("[x](https://e.com) <http://a.b>"))
    }

    @Test
    fun `같은 문서 안의 자리로 가는 링크`() {
        assertEquals("<p><a href=\"#설치-방법\">설치</a></p>", html("[설치](#설치-방법)"))
        // 대문자로 적어도 소문자 id 에 닿는다.
        assertEquals("<p><a href=\"#install\">x</a></p>", html("[x](#Install)"))
    }

    @Test
    fun `참조 링크`() {
        val md = "[foo][bar] [bar] [Bar][]\n\n[bar]: #target \"t\"\n[unused]: https://x"
        assertEquals(
            "<p><a href=\"#target\">foo</a> <a href=\"#target\">bar</a> <a href=\"#target\">Bar</a></p>",
            html(md),
        )
        // 정의가 없으면 글자다.
        assertEquals("<p>[nope]</p>", html("[nope]"))
        // 정의만 있는 문단은 사라진다.
        assertEquals("", html("[a]: https://x"))
    }

    @Test
    fun `링크 안에 링크를 두지 않는다`() {
        assertEquals("<p>[a <a href=\"#y\">b</a>](#x)</p>", html("[a [b](#y)](#x)"))
    }

    @Test
    fun `그림 — 이 폴더의 것만 번호로`() {
        val r = MarkdownPreview.body(
            "![고양이](img/cat.png) ![개](./img/../dog%20one.jpg?raw=true) ![또](img/cat.png) " +
                "![원격](https://e.com/x.png) ![위](../secret.png) ![데이터](data:image/png;base64,AAAA) ![](/abs.png)",
        )
        assertEquals(
            "<p><img src=\"img/0\" alt=\"고양이\"> <img src=\"img/1\" alt=\"개\"> <img src=\"img/0\" alt=\"또\"> " +
                "<span class=\"img-alt\">원격</span> <span class=\"img-alt\">위</span> " +
                "<span class=\"img-alt\">데이터</span> </p>",
            r.html,
        )
        assertEquals(listOf("img/cat.png", "dog one.jpg"), r.images)
    }

    @Test
    fun `그림 경로 정규화`() {
        assertEquals("a/b.png", MarkdownPreview.localImagePath("a/./b.png"))
        assertEquals("b.png", MarkdownPreview.localImagePath("a/../b.png"))
        assertEquals("한글 이름.png", MarkdownPreview.localImagePath("%ED%95%9C%EA%B8%80%20이름.png"))
        assertEquals("100%.png", MarkdownPreview.localImagePath("100%.png"))
        assertNull(MarkdownPreview.localImagePath("../a.png"))
        assertNull(MarkdownPreview.localImagePath("a/../../a.png"))
        assertNull(MarkdownPreview.localImagePath("/sdcard/a.png"))
        assertNull(MarkdownPreview.localImagePath("file:///sdcard/a.png"))
        assertNull(MarkdownPreview.localImagePath("content://x/a.png"))
        assertNull(MarkdownPreview.localImagePath("C:/a.png"))
        assertNull(MarkdownPreview.localImagePath("a\\b.png"))
        assertNull(MarkdownPreview.localImagePath("#frag"))
        assertNull(MarkdownPreview.localImagePath("%2e%2e/a.png"))
        assertNull(MarkdownPreview.localImagePath("a%00.png"))
        assertEquals(0, MarkdownPreview.imageIndexOf("img/0"))
        assertEquals(12, MarkdownPreview.imageIndexOf("img/12"))
        assertNull(MarkdownPreview.imageIndexOf("img/../x"))
        assertNull(MarkdownPreview.imageIndexOf("img/"))
        assertNull(MarkdownPreview.imageIndexOf("md-1.html"))
    }

    // ---- 이스케이프·엔티티 -----------------------------------------------------------------

    @Test
    fun `이스케이프와 엔티티`() {
        assertEquals("<p>*not* #not</p>", html("\\*not\\* \\#not"))
        assertEquals("<p>&amp; © A A &amp;foo; &amp;</p>", html("&amp; &copy; &#65; &#x41; &foo; &"))
        // 엔티티로 쓴 별은 강조가 되지 않는다.
        assertEquals("<p>*a*</p>", html("&ast;a&ast;"))
        assertEquals("<p>\uFFFD</p>", html("&#0;"))
    }

    // ---- 안전 ------------------------------------------------------------------

    @Test
    fun `날것의 HTML 은 글자로 남는다`() {
        val md = "<script>alert(1)</script>\n\n<img src=x onerror=alert(1)>\n\n" +
            "<div style=\"background:url(https://t/p)\">x</div>\n\n*<b>굵게</b>*"
        val body = html(md)
        assertFalse("<script" in body, body)
        assertFalse("<img" in body, body)
        assertFalse("<div" in body, body)
        assertTrue("&lt;script&gt;alert(1)&lt;/script&gt;" in body, body)
        assertTrue("<em>&lt;b&gt;굵게&lt;/b&gt;</em>" in body, body)
        val page = MarkdownPreview.render(md).html.substringAfter("<body>")
        assertFalse("<script" in page, page)
        assertFalse("<img" in page, page)
        assertFalse("<div style" in page, page)
    }

    @Test
    fun `속성 밖으로 새지 않는다`() {
        val body = html("[x](#a\"onmouseover=\"alert(1)) ![y\" onerror=\"z](a.png) [q](javascript:alert(1))")
        assertFalse("\"onmouseover" in body, body)
        assertFalse("\" onerror" in body, body)
        assertTrue("&quot;" in body)
        assertFalse("href=\"javascript" in body, body)
        val page = MarkdownPreview.render("[q](javascript:alert(1)) [r](JaVaScRiPt:x)").html
        assertFalse("href" in page.substringAfter("<body>"), page)
    }

    @Test
    fun `위생과 껍데기를 지난다`() {
        val r = MarkdownPreview.render("# 제목\n\n![a](a.png) [b](#제목)", fontPercent = 150)
        assertTrue(r.html.startsWith("<!DOCTYPE html>"), r.html.take(80))
        assertTrue("<img src=\"img/0\"" in r.html, r.html)
        assertTrue("href=\"#제목\"" in r.html, r.html)
        assertTrue("font-size:150%" in r.html, r.html)
        assertTrue("id=\"제목\"" in r.html)
        assertFalse(r.truncated)
    }

    @Test
    fun `NUL 과 제어 문자`() {
        assertEquals("<p>a\uFFFDb</p>", html("a\u0000b"))
        // 쓰개가 제어 문자를 버린다.
        assertEquals("<p>ab</p>", html("a\u0001b"))
    }
}
