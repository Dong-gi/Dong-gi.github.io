package io.github.donggi.iroiroviewer.format.text

import io.github.donggi.iroiroviewer.charset.TextEncoding
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * HTML 미리보기 — 사용자의 쪽을 **그 쪽 그대로**, 그러나 폴더 밖·바깥·스크립트 없이. 가장 흔한 실물은 브라우저의 '웹 페이지, 전체'
 * 로 저장한 쪽(`page.html` + `page_files/`)이라 그 모양을 먼저 박는다.
 */
class HtmlPreviewTest {

    private fun render(
        html: String,
        xhtml: Boolean = false,
        self: String? = "page.html",
        sheets: Map<String, ByteArray> = emptyMap(),
        encoding: TextEncoding = TextEncoding.UTF_8,
    ): HtmlPreview.Result {
        val prepared = HtmlPreview.prepare(html, xhtml, self)
        val loaded = HtmlPreview.loadStylesheets(prepared.stylesheets) { path, max ->
            sheets[path]?.takeIf { it.size <= max }
        }
        return HtmlPreview.finish(prepared, loaded, encoding)
    }

    /** 문서 안의 `/r/N` 이 가리키는 것. */
    private fun HtmlPreview.Result.asset(url: String): PreviewAsset {
        val index = HtmlPreview.assetIndexOf(url.removePrefix("/"))
        assertNotNull(index, "번호가 아니다: $url")
        return assets[index]
    }

    /** 표의 그림·글꼴 경로. */
    private fun HtmlPreview.Result.diskPaths(): List<String> = assets.mapNotNull { (it as? PreviewAsset.Disk)?.path }

    /** 문서에 적힌 `/r/N` 을 차례로. */
    private fun urlsIn(text: String): List<String> = Regex("/r/[0-9]+").findAll(text).map { it.value }.toList()

    private fun sheetCss(r: HtmlPreview.Result, path: String): String {
        val sheet = r.assets.filterIsInstance<PreviewAsset.Sheet>().first { it.path == path }
        return String(assertNotNull(sheet.css, "스타일시트를 싣지 않았다"), Charsets.UTF_8)
    }

    // ---- 저장한 쪽 -----------------------------------------------------------------

    @Test
    fun `저장한 웹 쪽 — page_files 의 그림·스타일시트·글꼴이 번호로 실린다`() {
        val html = """
            <!DOCTYPE html>
            <!-- saved from url=(0024)https://example.com/a/b -->
            <html lang="ko"><head><meta charset="euc-kr">
            <title>저장한 쪽</title>
            <link rel="stylesheet" href="./page_files/style.css">
            <link rel="icon" href="./page_files/favicon.ico">
            <script src="./page_files/app.js"></script>
            </head><body class="home">
            <img src="./page_files/logo.png" alt="로고">
            <p style="background:url('page_files/dot.gif')">본문</p>
            </body></html>
        """.trimIndent()
        val css = """
            @charset "utf-8";
            body { background: url(bg.png) no-repeat; }
            @font-face { font-family: X; src: url("fonts/x.woff2") format("woff2"), url('../page_files/fonts/x.woff') format("woff"); }
            .up { background-image: url("../../escape.png"); }
            .remote { background: url(https://cdn.example.com/a.png); }
        """.trimIndent()
        val r = render(html, sheets = mapOf("page_files/style.css" to css.toByteArray()))

        // 스타일시트는 그 파일의 폴더 기준으로 풀린다 — `bg.png` 는 `page_files/bg.png` 다.
        assertEquals(
            listOf("page_files/logo.png", "page_files/dot.gif", "page_files/bg.png", "page_files/fonts/x.woff2", "page_files/fonts/x.woff"),
            r.diskPaths(),
        )
        val sheetUrl = urlsIn(r.html).first { r.asset(it) is PreviewAsset.Sheet }
        assertEquals("page_files/style.css", (r.asset(sheetUrl) as PreviewAsset.Sheet).path)
        val sheet = sheetCss(r, "page_files/style.css")
        // 시트 안의 번호도 뿌리 기준이라 `/r/…` 그대로 쪽에서 풀린다.
        for (url in urlsIn(sheet)) assertTrue(r.asset(url) is PreviewAsset.Disk, sheet)
        assertEquals(3, urlsIn(sheet).size, sheet)
        assertFalse("escape" in sheet || "https" in sheet || "@charset" in sheet, sheet)

        assertFalse("<script" in r.html || "app.js" in r.html, r.html)
        assertFalse("favicon" in r.html, r.html)
        assertFalse("example.com" in r.html, "주석의 바깥 주소가 남았다: ${r.html}")
        assertTrue("<html lang=\"ko\">" in r.html, r.html)
        assertTrue("<body class=\"home\">" in r.html, r.html)
        assertTrue("본문" in r.html && "저장한 쪽" in r.html, r.html)
        assertTrue(r.withheld, "스크립트를 버렸는데 말하지 않는다")
        assertFalse(r.truncated)
    }

    @Test
    fun `이름에 공백·샵·물음표·퍼센트·더하기·한글이 든 파일`() {
        val html = """
            <img src="my image.png">
            <img src="my%20image2.png">
            <img src="a%23b.png">
            <img src="q%3F.png">
            <img src="100%25.png">
            <img src="100%.png">
            <img src="a+b.png">
            <img src="c%2Bd.png">
            <img src="그림.png">
            <img src="%EA%B7%B8%EB%A6%BC2.png">
            <img src="x&amp;y.png">
            <img src="page%20files\win.png">
            <img src="bg(1).png?ver=2#frag">
        """.trimIndent()
        val r = render(html)
        assertEquals(
            listOf(
                "my image.png", "my image2.png", "a#b.png", "q?.png", "100%.png", "100%.png", "a+b.png", "c+d.png",
                "그림.png", "그림2.png", "x&y.png", "page files/win.png", "bg(1).png",
            ).distinct(),
            r.diskPaths(),
        )
        // 이름은 쪽에 적히지 않는다 — 번호뿐이다(머리의 CSS 는 건너뛰고 본다 — `100%` 가 있다).
        val body = r.html.substringAfter("</style>")
        assertFalse("image" in body || "그림" in body || "%" in body, body)
    }

    @Test
    fun `style 의 media 는 CSS 안으로 옮기고 CSS 가 아닌 것은 걸지 않는다`() {
        // 위생기는 `<style>` 의 속성을 버린다 — 옮기지 않으면 인쇄용 규칙이 화면에 걸린다.
        val r = render(
            """<style media="print">nav{display:none}</style>""" +
                """<style media="screen and (min-width: 600px)">.w{color:red}</style>""" +
                """<style media="all">.a{color:blue}</style>""" +
                """<style type="text/less">.l{color:red}</style>""" +
                """<style media="&lt;x">.e{color:green}</style>""" +
                """<link rel="stylesheet" type="text/less" href="a.css">""",
        )
        assertTrue("@media print{nav{display:none}}" in r.html, r.html)
        assertTrue("@media screen and (min-width: 600px){.w{color:red}}" in r.html, r.html)
        assertTrue(".a{color:blue}" in r.html && "@media all" !in r.html, r.html)
        assertFalse(".l{" in r.html || ".e{" in r.html, r.html)
        assertTrue(r.assets.isEmpty())
    }

    @Test
    fun `CSS 의 괄호 든 이름과 SVG 를 품은 data 주소`() {
        // 크롬은 같은 이름에 `(1)` 을 붙여 저장한다. 위생기 혼자서는 첫 `)` 에서 주소를 끊는다.
        val svg = "data:image/svg+xml,%3csvg xmlns='http://www.w3.org/2000/svg'%3e%3cpath stroke='rgba(0, 0, 0, 0.55)' d='M4 7h22'/%3e%3c/svg%3e"
        val html = """
            <style>
            .a { background: url("bg(1).png") }
            .b { background-image: url("$svg") }
            .c { background: url( "sp ace.png" ) }
            .d { background: url(esc\(2\).png) }
            .e { content: "url(not-a-url.png)" }
            </style>
            <p class="a b c d e">x</p>
        """.trimIndent()
        val r = render(html)
        assertEquals(listOf("bg(1).png", "sp ace.png", "esc(2).png"), r.diskPaths())
        val data = r.assets.filterIsInstance<PreviewAsset.Data>().single()
        assertEquals("image/svg+xml", data.mimeType)
        assertTrue(String(data.bytes, Charsets.UTF_8).contains("stroke='rgba(0, 0, 0, 0.55)'"))
        // 문자열 안의 `url(` 은 주소가 아니다 — 표에 들지 않는다.
        assertFalse(r.diskPaths().any { "not-a-url" in it })
        assertFalse("rgba" in r.html, "data 주소가 쪽에 남았다: ${r.html}")
    }

    @Test
    fun `글꼴을 품은 data 주소는 CSS 에서만 싣는다`() {
        val woff = "data:application/font-woff;charset=utf-8;base64,d09GRgABAAA="
        val r = render(
            """<style>@font-face{font-family:I;src:url($woff) format("woff")}</style>""" +
                """<img src="$woff"><img src="data:text/html,<b>x</b>">""",
        )
        val data = r.assets.filterIsInstance<PreviewAsset.Data>().single()
        assertEquals("font/woff", data.mimeType)
        assertEquals("wOFF", String(data.bytes, 0, 4, Charsets.ISO_8859_1))
        // `<img>` 가 가리킨 글꼴·문서는 싣지 않는다.
        assertEquals(1, urlsIn(r.html).size, r.html)
        assertFalse("text/html" in r.html, r.html)
    }

    // ---- 바깥과 폴더 밖 ------------------------------------------------------------------

    @Test
    fun `바깥 주소는 싣지 않는다`() {
        val html = """
            <link rel="stylesheet" href="https://cdn.example.com/x.css">
            <link rel="stylesheet" href="//cdn.example.com/y.css">
            <img src="https://tracker.example.com/pixel.gif">
            <img src="http://example.com/a.png" srcset="//example.com/b.png 2x">
            <div style="background:url(https://example.com/bg.png)">x</div>
            <style>@import url("https://example.com/i.css"); .x{background:url(//example.com/c.png)}</style>
            <table background="ftp://example.com/t.gif"><tr><td>칸</td></tr></table>
            <iframe src="https://example.com/frame"></iframe>
            <object data="https://example.com/o.swf"></object>
        """.trimIndent()
        val r = render(html)
        assertTrue(r.assets.isEmpty(), "바깥을 표에 담았다: ${r.assets.size}")
        for (word in listOf("example.com", "http", "ftp:", "//")) {
            assertFalse(word in r.html.substringAfter("</style>", r.html), "$word 가 남았다: ${r.html}")
        }
        assertFalse("example.com" in r.html, r.html)
        assertTrue("칸" in r.html, r.html)
        assertTrue(r.withheld)
    }

    @Test
    fun `폴더 위·절대 경로·드라이브 문자·제어 문자는 싣지 않는다`() {
        val html = """
            <img src="../secret.png">
            <img src="sub/../../secret.png">
            <img src="%2e%2e/secret.png">
            <img src="/sdcard/secret.png">
            <img src="\\server\share\a.png">
            <img src="C:\Users\a.png">
            <img src="file:///sdcard/a.png">
            <img src="content://x/a.png">
            <img src="a%00.png">
            <img src="./">
        """.trimIndent()
        val r = render(html)
        assertTrue(r.assets.isEmpty(), r.diskPaths().toString())
        assertFalse("secret" in r.html || "sdcard" in r.html || "Users" in r.html, r.html)
    }

    /** 문서가 우리 번호를 흉내 내도 표의 다른 항목을 가리키지 못한다 — 모든 상대 주소는 번호로 바뀌거나 지워진다. */
    @Test
    fun `문서가 적은 번호 모양의 주소는 번호가 아니다`() {
        val r = render(
            """<img src="real.png"><img src="/r/0"><a href="/r/0">a</a><blockquote cite="r/0">q</blockquote>""" +
                """<img longdesc="/r/0" src="r/0">""",
        )
        // `r/0` 은 폴더 안의 `r` 폴더에 있는 파일 `0` 으로 읽힌다.
        assertEquals(listOf("real.png", "r/0"), r.diskPaths())
        assertEquals(listOf("/r/0", "/r/1"), urlsIn(r.html), r.html)
        assertFalse("cite=" in r.html || "longdesc" in r.html, r.html)
        assertTrue("href=\"${HtmlPreview.LINK}\"" in r.html, r.html)
    }

    // ---- 링크 ----------------------------------------------------------------------

    @Test
    fun `같은 쪽의 자리는 누를 수 있고 다른 쪽은 아무 일도 하지 않는다`() {
        val html = """
            <a href="#install">설치</a>
            <a href="page.html#usage">사용</a>
            <a href="PAGE.HTML#faq">질문</a>
            <a href="?x=1#q">질의</a>
            <a href="other.html#a">다른 쪽</a>
            <a href="https://example.com/">바깥</a>
            <a href="javascript:alert(1)">스크립트</a>
            <a href="java&#115;cript:alert(2)">숨긴 스크립트</a>
            <a href="mailto:a@b.c">메일</a>
            <a name="top">이름</a>
            <a href="&#35;note">참조로 적은 자리</a>
        """.trimIndent()
        val r = render(html)
        // 문자 참조로 적은 `#` 도 자리다 — 위생기는 참조를 풀지 않으므로 푼 모양으로 적어 넘긴다.
        for (frag in listOf("#install", "#usage", "#faq", "#q", "#note")) assertTrue("href=\"$frag\"" in r.html, "$frag: ${r.html}")
        assertEquals(5, Regex("href=\"${Regex.escape(HtmlPreview.LINK)}\"").findAll(r.html).count(), r.html)
        assertFalse("javascript" in r.html || "alert" in r.html || "other.html" in r.html || "mailto" in r.html, r.html)
        assertTrue("<a name=\"top\">이름</a>" in r.html, r.html)
        // 링크의 글은 남는다.
        assertTrue("다른 쪽" in r.html && "바깥" in r.html, r.html)
        assertTrue(r.withheld, "스크립트 링크를 세지 않았다")
    }

    // ---- 그림 고르기 ---------------------------------------------------------------------

    @Test
    fun `srcset 과 picture 에서 실을 수 있는 것을 고른다`() {
        val html = """
            <img srcset="s.png 1x, m.png 2x, l.png 3x" alt="밀도">
            <img srcset="w320.png 320w, w1200.png 1200w, w2400.png 2400w" sizes="100vw">
            <img src="https://cdn/x.png" srcset="https://cdn/x2.png 2x, local.png 1x">
            <img src="keep.png" srcset="other.png 2x">
            <picture><source srcset="pic.webp" type="image/webp"><img src="https://cdn/pic.jpg"></picture>
            <picture><source srcset="https://cdn/a.avif"><source srcset="b.avif 1x, b2.avif 2x"><img alt="그림"></picture>
            <img srcset="data:image/png;base64,iVBORw0KGgo= 1x">
        """.trimIndent()
        val r = render(html)
        // `src` 는 1x 후보다(명세) — `srcset` 의 2x 가 이긴다(`keep.png` 가 아니라 `other.png`). 폰은 배율이 2~3 이다.
        assertEquals(listOf("m.png", "w1200.png", "local.png", "other.png", "pic.webp", "b2.avif"), r.diskPaths())
        assertEquals(1, r.assets.filterIsInstance<PreviewAsset.Data>().size)
        assertFalse("srcset" in r.html || "sizes" in r.html || "<source" in r.html, r.html)
        assertEquals(7, Regex("<img [^>]*src=\"/r/").findAll(r.html).count(), r.html)
    }

    // ---- 글자 코드 --------------------------------------------------------------------

    @Test
    fun `문서의 meta charset 은 남지 않고 우리 utf-8 하나만 선다`() {
        val html = """
            <html><head>
            <meta charset="euc-kr">
            <meta http-equiv="Content-Type" content="text/html; charset=shift_jis">
            <META HTTP-EQUIV="content-type" CONTENT="text/html;charset=iso-8859-1">
            <title>가나다</title></head><body>한글 本文</body></html>
        """.trimIndent()
        val r = render(html)
        assertEquals(1, Regex("(?i)<meta").findAll(r.html).count(), r.html)
        assertEquals(1, Regex("(?i)charset").findAll(r.html).count(), r.html)
        assertTrue(r.html.startsWith("<!DOCTYPE html>").not())
        assertTrue("<meta charset=\"utf-8\">" in r.html, r.html)
        assertFalse("euc-kr" in r.html || "shift_jis" in r.html || "iso-8859-1" in r.html, r.html)
        // 글은 그대로다(바이트로 내주는 것은 화면이 UTF-8 로 한다).
        assertTrue("한글 本文" in r.html)
    }

    @Test
    fun `원문은 뷰어가 판정한 인코딩으로 읽는다`() {
        val text = "<p>한글</p>"
        val cp949 = TextEncoding.CP949.charset ?: return
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + text.toByteArray(Charsets.UTF_8)
        val utf8 = HtmlPreview.readSource({ off -> ByteArrayInputStream(bytes, off.toInt(), bytes.size - off.toInt()) }, TextEncoding.UTF_8, 3)
        assertEquals(text, utf8)
        val legacy = text.toByteArray(cp949)
        assertEquals(text, HtmlPreview.readSource({ ByteArrayInputStream(legacy) }, TextEncoding.CP949, 0))
        // 상한에서 끊는다.
        assertEquals("<p>", HtmlPreview.readSource({ ByteArrayInputStream(legacy) }, TextEncoding.CP949, 0, maxBytes = 3))
    }

    @Test
    fun `스타일시트의 인코딩 — BOM, @charset, 문서의 인코딩`() {
        val cp949 = TextEncoding.CP949.charset ?: return
        val body = "body{font-family:\"맑은 고딕\"}"
        val r = render(
            """<link rel="stylesheet" href="a.css"><link rel="stylesheet" href="b.css"><link rel="stylesheet" href="c.css">""",
            sheets = mapOf(
                "a.css" to byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + body.toByteArray(Charsets.UTF_8),
                "b.css" to "@charset \"euc-kr\";$body".toByteArray(cp949),
                // 스스로 말하지 않는 시트는 문서의 인코딩(여기서는 CP949)으로 읽는다.
                "c.css" to body.toByteArray(cp949),
            ),
            encoding = TextEncoding.CP949,
        )
        for (name in listOf("a.css", "b.css", "c.css")) assertEquals(body, sheetCss(r, name), name)
    }

    // ---- XHTML 과 모드 ----------------------------------------------------------------

    @Test
    fun `XHTML 은 표준 모드로, 스스로 닫은 요소는 빈 요소로`() {
        val xhtml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN" "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">
            <html xmlns="http://www.w3.org/1999/xhtml" xml:lang="ja"><head><title/>
            <style type="text/css"><![CDATA[ p { color: red } ]]></style></head>
            <body><div class="x"/><p>本文<br/>次</p><a id="n1"/><span/>後</body></html>
        """.trimIndent()
        val r = render(xhtml, xhtml = true, self = "page.xhtml")
        assertTrue(r.html.startsWith("<!DOCTYPE html>"), r.html.take(80))
        assertEquals(1, Regex("(?i)<!DOCTYPE").findAll(r.html).count(), r.html)
        assertFalse("<?xml" in r.html, r.html)
        assertTrue("<div class=\"x\"></div>" in r.html, r.html)
        assertTrue("<title></title>" in r.html, r.html)
        assertTrue("<a id=\"n1\"></a>" in r.html && "<span></span>後" in r.html, r.html)
        assertTrue("<br/>" in r.html, r.html)
        assertTrue("lang=\"ja\"" in r.html, r.html)
        // XML 의 CDATA 껍데기는 벗겨 CSS 로 남긴다 — HTML 로 읽으면 첫 규칙이 깨진다.
        assertTrue(Regex("<style[^>]*> p \\{ color: red \\} </style>").containsMatchIn(r.html), r.html)
    }

    @Test
    fun `HTML 은 문서의 DOCTYPE 이 정하는 모드로 그린다`() {
        fun standards(doc: String) = render("$doc<p>x</p>").html.startsWith("<!DOCTYPE html>")
        assertTrue(standards("<!DOCTYPE html>"))
        assertTrue(standards("<!-- 앞 주석 -->\n<!doctype HTML>"))
        assertTrue(standards("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"x.dtd\">"))
        assertTrue(standards("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\" \"http://www.w3.org/TR/html4/loose.dtd\">"))
        assertTrue(standards("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\">"))
        // 쿼크 — DOCTYPE 이 없다, 옛 공개 식별자, 시스템 식별자 없는 4.01 Transitional, 내용 뒤의 DOCTYPE.
        assertFalse(standards(""))
        assertFalse(standards("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 3.2 Final//EN\">"))
        assertFalse(standards("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\">"))
        assertFalse(standards("<!DOCTYPE html SYSTEM \"http://www.ibm.com/data/dtd/v11/ibmxhtml1-transitional.dtd\">"))
        assertFalse(standards("<p>먼저</p><!DOCTYPE html>"))
        assertFalse(standards("<!DOCTYPE html [ <!ENTITY a \"b\"> ]>"))
        assertFalse(standards("<!DOCTYPE svg>"))
    }

    @Test
    fun `DOCTYPE 의 내부 부분집합이 글로 남지 않는다`() {
        val r = render("<!DOCTYPE html [ <!ENTITY a \"b\"> ]><p>본문</p>")
        assertFalse("]>" in r.html || "ENTITY" in r.html, r.html)
        assertTrue("<p>본문</p>" in r.html, r.html)
    }

    // ---- 스크립트 ----------------------------------------------------------------------

    @Test
    fun `스크립트는 브라우저가 끝내는 자리에서 끝난다 — 뒤의 본문을 삼키지 않는다`() {
        // 위생기 혼자서는 문자열 속의 `<script>` 를 중첩으로 세어 뒤의 본문까지 지운다.
        val html = """
            <p>앞</p>
            <script>var s = "<script>"; document.title = s;</script>
            <p>가운데</p>
            <script><!-- document.write("<script src='x.js'></script>"); --></script>
            <p>뒤</p>
            <script type="application/ld+json">{"@type":"Thing"}</script>
            <SCRIPT>alert(1)</SCRIPT >
            <p>끝</p>
        """.trimIndent()
        val r = render(html)
        for (word in listOf("앞", "가운데", "뒤", "끝")) assertTrue("<p>$word</p>" in r.html, "$word 가 사라졌다: ${r.html}")
        for (word in listOf("script", "SCRIPT", "alert", "document", "Thing")) assertFalse(word in r.html, "$word: ${r.html}")
        // 첫 `</script` 에서 끊으면 `<!-- … -->` 안 문자열의 뒤(`"); -->`)가 글로 샌다. 예전에는 그 뒤의 짝 없는 `</script>` 가
        // 본문을 통째로 지워 위의 검사가 잡았지만, 이제 그 태그는 버려지므로 샌 글을 따로 본다(되돌림 확인이 짚었다).
        assertFalse("-->" in r.html || "\");" in r.html, r.html)
        assertTrue(r.withheld)
    }

    @Test
    fun `스크립트·바깥이 없는 쪽은 말할 것이 없다`() {
        val r = render("<p onclick>x</p>".replace(" onclick", "") + "<img src=\"a.png\"><a href=\"b.html\">b</a>")
        assertFalse(r.withheld)
        assertTrue(render("<p onclick=\"go()\">x</p>").withheld, "이벤트 처리기도 스크립트다")
        assertTrue(render("<iframe src=\"a.html\"></iframe>").withheld, "프레임")
        assertTrue(render("<style>.x{behavior:url(a.htc)}</style>").withheld, "옛 스크립트 문법")
    }

    // ---- 머리 ----------------------------------------------------------------------

    @Test
    fun `머리는 쪽의 CSS 에 진다 — 읽기 CSS 를 덮어씌우지 않는다`() {
        val r = render("<html><head><style>body{margin:0 auto;max-width:600px}</style></head><body>x</body></html>")
        val shellEnd = r.html.indexOf("</style>")
        val page = r.html.indexOf("body{margin:0 auto")
        assertTrue(shellEnd in 0 until page, "우리 스타일이 쪽의 것보다 뒤에 있다: ${r.html}")
        val shell = r.html.substring(0, shellEnd)
        // 특이도 0 — 쪽의 어떤 규칙에도 진다.
        assertFalse(Regex("(^|})\\s*[a-z]+\\{").containsMatchIn(shell.substringAfter("<style>")), shell)
        for (word in listOf("padding", "keep-all", "pre-wrap", "background", "font-size", "line-height")) {
            assertFalse(word in shell, "$word 를 쪽에 강요한다: $shell")
        }
    }

    @Test
    fun `body 와 html 의 속성은 그대로 남는다`() {
        val r = render("<!DOCTYPE html><html lang=\"ja\" dir=\"rtl\"><head></head><body class=\"a b\" style=\"color:red\">x</body></html>")
        assertTrue("<html lang=\"ja\" dir=\"rtl\">" in r.html, r.html)
        assertTrue("<body class=\"a b\" style=\"color:red\">" in r.html, r.html)
    }

    // ---- 상한과 취소 ---------------------------------------------------------------------

    @Test
    fun `결과 상한에 닿으면 표시 경계에서 멈추고 줄였다고 말한다`() {
        val p = HtmlPreview.prepare("<p>가나다라</p>".repeat(10_000), xhtml = false, selfName = null, maxChars = 50_000)
        val r = HtmlPreview.finish(p, emptyMap(), TextEncoding.UTF_8)
        assertTrue(r.truncated)
        assertTrue(r.html.length < 52_000, "${r.html.length}")
        // 태그를 반쯤 쓰지 않는다.
        assertFalse(r.html.trimEnd().endsWith("<"), r.html.takeLast(40))
    }

    @Test
    fun `큰 글 덩이도 들어가는 데까지 보인다`() {
        val p = HtmlPreview.prepare("<p>" + "가".repeat(100_000) + "&amp;끝</p>", xhtml = false, selfName = null, maxChars = 50_000)
        val r = HtmlPreview.finish(p, emptyMap(), TextEncoding.UTF_8)
        assertTrue(r.truncated)
        assertTrue(r.html.contains("가".repeat(40_000)), "글이 통째로 빠졌다")
    }

    @Test
    fun `표의 상한을 넘은 그림은 싣지 않는다`() {
        val html = (0 until HtmlPreview.MAX_ASSETS + 50).joinToString("") { "<img src=\"i$it.png\">" }
        val r = render(html)
        assertEquals(HtmlPreview.MAX_ASSETS, r.assets.size)
        assertEquals(HtmlPreview.MAX_ASSETS, urlsIn(r.html).size)
    }

    @Test
    fun `스타일시트 상한 — 수와 크기`() {
        val many = (0 until HtmlPreview.MAX_SHEETS + 5).joinToString("") { "<link rel=\"stylesheet\" href=\"s$it.css\">" }
        val p = HtmlPreview.prepare(many, xhtml = false, selfName = null)
        assertEquals(HtmlPreview.MAX_SHEETS, p.stylesheets.size)
        // 하나의 상한을 넘는 시트는 읽지 않고, 합의 상한에 닿으면 멈춘다.
        val asked = ArrayList<Long>()
        val loaded = HtmlPreview.loadStylesheets(p.stylesheets) { _, max ->
            asked.add(max)
            ByteArray(minOf(max, 3L * 1024 * 1024).toInt())
        }
        assertTrue(asked.all { it <= HtmlPreview.MAX_SHEET_BYTES })
        assertTrue(loaded.values.sumOf { it.size.toLong() } <= HtmlPreview.MAX_SHEETS_TOTAL_BYTES)
        assertEquals(4, loaded.size)
    }

    @Test
    fun `취소는 체크포인트에서 던진다`() {
        var calls = 0
        try {
            HtmlPreview.prepare("<p>x</p>".repeat(10_000), xhtml = false, selfName = null) {
                calls++
                if (calls == 3) throw kotlinx.coroutines.CancellationException("취소")
            }
            kotlin.test.fail("던지지 않았다")
        } catch (e: kotlinx.coroutines.CancellationException) {
            assertEquals(3, calls)
        }
    }

    // ---- 적대적 검토가 잡은 것 ----------------------------------------------------

    @Test
    fun `늦게 싣는 쪽의 자리표시 그림보다 srcset 의 진짜 그림`() {
        // 1×1 GIF 자리표시가 `src` 를 차지하고 진짜 그림은 `srcset` 에 있다. 예전에는 `src` 가 언제나 이겼다.
        val gif = "data:image/gif;base64,R0lGODlhAQABAAAAACw="
        val r = render("<img src=\"$gif\" srcset=\"real.png 1x, real2.png 2x\"><img src=\"$gif\" srcset=\"w.png 800w\">")
        assertEquals(listOf("real2.png", "w.png"), r.diskPaths())
        assertTrue(r.assets.none { it is PreviewAsset.Data }, "자리표시를 실었다")
        // 실을 수 있는 후보가 없으면 `src` 로 돌아간다(저장한 쪽은 `srcset` 이 바깥 주소로 남는다).
        val fallback = render("<img src=\"saved.png\" srcset=\"https://cdn/a.png 1x, https://cdn/b.png 2x\">")
        assertEquals(listOf("saved.png"), fallback.diskPaths())
    }

    @Test
    fun `HTML 의 script·style 스스로 닫기는 여는 태그다 — XHTML 은 빈 요소다`() {
        // 브라우저는 HTML 의 `<script/>` 뒤를 `</script>` 까지 스크립트로 읽는다. 우리만 그 사이를 보이면 쪽이 달라진다.
        val html = "<p>a</p><script src=\"a.js\"/><p>숨은 줄</p></script><p>b</p>" +
            "<style/>p{color:red}</style><p>c</p>"
        val r = render(html)
        assertTrue("<p>a</p>" in r.html && "<p>b</p>" in r.html && "<p>c</p>" in r.html, r.html)
        assertFalse("숨은 줄" in r.html, r.html)
        assertTrue("<style>p{color:red}</style>" in r.html, r.html)
        // XHTML 은 빈 요소다 — 그 뒤는 본문이고, 짝 없는 `</script>` 는 버린다(예전에는 위생기가 그것을 여는 태그로 읽어 뒤를 지웠다).
        val x = render(html, xhtml = true, self = "page.xhtml")
        assertTrue("<p>숨은 줄</p>" in x.html && "<p>b</p>" in x.html && "<p>c</p>" in x.html, x.html)
        // `<style/>` 도 빈 요소다 — 뒤의 `p{color:red}` 는 CSS 가 아니라 글이다(짝 없는 `</style>` 은 브라우저가 버린다).
        assertFalse("<style>p{color:red}" in x.html, x.html)
        assertTrue("p{color:red}" in x.html.substringAfter("</style>"), x.html)
    }

    @Test
    fun `짝 없는 닫는 script 가 뒤의 본문을 지우지 않는다`() {
        val r = render("<p>a</p><script>x()</script></script><p>b</p></iframe><p>c</p></object><p>d</p>")
        for (word in listOf("a", "b", "c", "d")) assertTrue("<p>$word</p>" in r.html, "$word: ${r.html}")
        assertFalse("</script>" in r.html, r.html)
        // 앞 패스도 버린다 — 위생기에만 맡기지 않는다(겹친 방어. 위생기가 예전처럼 그것을 여는 태그로 읽어도 뒤가 남는다).
        val prepared = HtmlPreview.prepare("<p>a</p></script><p>b</p>", xhtml = false, selfName = null)
        assertEquals("<p>a</p><p>b</p>", prepared.take())
    }

    @Test
    fun `이스케이프로 쓴 주소·image-set·import 도 번호로 바꾸거나 지운다`() {
        val css = "a{background:\\75 rl(page_files/a.png)}b{background:u\\72l(https://x/b.png)}" +
            "c{background:image-set(\"page_files/c.png\" 1x, \"https://x/c2.png\" 2x)}" +
            "d{filter:\\75 rl(page_files/f.svg#f)}@\\69mport \"page_files/i.css\";e{color:red}"
        val r = render("<style>$css</style><p style=\"background:url&lpar;page_files/s.png&rpar;\">글</p>")
        assertEquals(listOf("page_files/a.png", "page_files/c.png", "page_files/s.png"), r.diskPaths())
        for (word in listOf("https", "f.svg", "mport", "i.css", "&lpar;")) assertFalse(word in r.html, "$word: ${r.html}")
        assertTrue("e{color:red}" in r.html, r.html)
        // 우리 표에 없는 이름 참조가 남은 `style` 은 믿지 않는다 — 위생기도 같은 판단이다.
        val unknown = render("<p style=\"color:red;background:url&lparen;x.png&rparen;\">글</p>")
        assertTrue("<p>글</p>" in unknown.html, unknown.html)
        assertTrue(unknown.assets.isEmpty())
    }

    /**
     * `<svg>` 안의 `<style>` 은 브라우저가 문자 참조를 풀고, SVG 의 표현 속성(`filter`·`fill` …)의 값은 CSS 다 — 둘 다 앞 패스가
     * 위생기와 같은 규칙으로 본다. 참조로 적은 주소도 번호가 되고, 다른 SVG 파일의 조각은 지운다.
     */
    @Test
    fun `외래 콘텐츠의 style 과 표현 속성의 주소도 번호로 바꾸거나 지운다`() {
        val r = render(
            "<svg><style>rect{fill:&#117;rl(page_files/p.png)}</style>" +
                "<rect filter=\"url(page_files/f.svg#f)\" fill=\"url(#g)\" stroke=\"red\"/></svg>",
        )
        assertEquals(listOf("page_files/p.png"), r.diskPaths())
        assertFalse("&#117;" in r.html, r.html)
        assertFalse("f.svg" in r.html, r.html)
        assertTrue("#g" in r.html, r.html)
        assertTrue("stroke=\"red\"" in r.html, r.html)
        // 우리 표에 없는 이름 참조가 든 블록은 비운다(위생기는 블록째 버린다).
        val unknown = render("<svg><style>a{b:&NoSuchRef;url(page_files/q.png)}</style></svg>")
        assertTrue(unknown.assets.isEmpty(), unknown.html)
        assertFalse("q.png" in unknown.html, unknown.html)
    }

    @Test
    fun `안내 줄의 오탐 — 문자열 안의 url·폴더 안의 import·integrity`() {
        // 예전에는 위생기가 `content:"url(x)"` 를 망가뜨리며 바깥 참조로 셌고, 모든 `@import`·`integrity` 도 바깥으로 셌다.
        val r = render(
            "<style>p::before{content:\"url(x)\"}@import \"local.css\";</style>" +
                "<link rel=\"stylesheet\" href=\"a.css\" integrity=\"sha384-x\" crossorigin=\"anonymous\"><p>글</p>",
        )
        assertFalse(r.withheld)
        assertTrue("content:\"url(x)\"" in r.html, r.html)
        // 바깥을 가리키는 `@import` 는 바깥 자원이다.
        assertTrue(render("<style>@import url(https://x/y.css);</style>").withheld)
    }

    @Test
    fun `인쇄용 시트의 짝 없는 중괄호가 화면에 새지 않는다`() {
        // 그대로 싸면 `}` 하나가 우리 `@media` 를 먼저 닫아 `body{display:none}` 이 화면에 걸린다(쪽 전체가 숨는다).
        val r = render("<style media=\"print\">a{color:red}} body{display:none}</style><p>글</p>")
        assertTrue("@media print{a{color:red} body{display:none}}" in r.html, r.html)
    }

    @Test
    fun `쿼크 모드에는 그림의 height auto 를 걸지 않는다`() {
        // 옛 쪽의 틈 그림(`width=1 height=50`)이 본디 비율(1:1)을 따라 1 픽셀이 된다.
        assertFalse("height:auto" in render("<img src=\"spacer.gif\" width=\"1\" height=\"50\">").html)
        assertTrue("height:auto" in render("<!DOCTYPE html><img src=\"a.png\" width=\"10\" height=\"20\">").html)
    }

    @Test
    fun `속성 값이 상한 밖에서 커지지 않는다`() {
        // 따옴표 하나가 `&quot;` 여섯 자다. 예전에는 다 만든 뒤에 재어 `"` 8 Mi 자 속성 하나가 192 MB 힙을 넘겼다.
        val p = HtmlPreview.prepare("<p>앞</p><a href=\"x.html\" title='" + "\"".repeat(200_000) + "'>a</a><p>뒤</p>",
            xhtml = false, selfName = null, maxChars = 50_000)
        val r = HtmlPreview.finish(p, emptyMap(), TextEncoding.UTF_8)
        assertTrue(r.truncated)
        assertTrue(r.html.length < 1_000, "${r.html.length}")
        assertTrue("<p>앞</p>" in r.html, r.html)
        // 같은 까닭으로 태그가 아닌 `<` 는 앞 패스가 `&lt;` 로 적는다 — 위생기에서 넷이 되지 않는다.
        val lt = HtmlPreview.prepare("<".repeat(100_000), xhtml = false, selfName = null, maxChars = 40_000)
        val out = HtmlPreview.finish(lt, emptyMap(), TextEncoding.UTF_8)
        assertTrue(out.truncated)
        assertTrue(out.html.length < 41_000, "${out.html.length}")
    }

    @Test
    fun `위생기가 넘치면 앞을 잘라 다시 거른다`() {
        // 앞 패스가 키울 것을 먼저 키우므로 실제로는 드물다 — 위생기의 상한을 작게 주어 그 길을 탄다.
        val p = HtmlPreview.prepare("<p>가나다라마바사</p>".repeat(1_000), xhtml = false, selfName = null)
        val r = HtmlPreview.finish(p, emptyMap(), TextEncoding.UTF_8, {}, cap = 6_000)
        assertTrue(r.truncated)
        assertTrue(r.html.length < 6_300, "${r.html.length}")
        assertTrue(r.html.contains("<p>가나다라마바사</p>"), r.html.take(200))
    }

    /** 앞 패스가 놓친 상대 주소가 있어도 위생기의 리졸버가 우리 번호 밖을 막는다(두 번째 겹). */
    @Test
    fun `위생기의 리졸버는 우리 번호와 링크만 통과시킨다`() {
        val resolver = HtmlPreview.RESOLVER
        assertEquals("/r/0", resolver.resolve("/r/0"))
        assertEquals("/r/123", resolver.resolve("/r/123"))
        assertEquals(HtmlPreview.LINK, resolver.resolve(HtmlPreview.LINK))
        for (url in listOf("a.png", "r/0", "/r/", "/r/0/../../x.png", "/r/0#f", "/r/x", "../r/0", "/page-1.html", "/~links")) {
            assertNull(resolver.resolve(url), url)
        }
    }

    @Test
    fun `번호 경로 읽기`() {
        assertEquals(0, HtmlPreview.assetIndexOf("r/0"))
        assertEquals(123, HtmlPreview.assetIndexOf("r/123"))
        assertNull(HtmlPreview.assetIndexOf("r/"))
        assertNull(HtmlPreview.assetIndexOf("r/1a"))
        assertNull(HtmlPreview.assetIndexOf("r/1234567"))
        assertNull(HtmlPreview.assetIndexOf("img/1"))
        assertNull(HtmlPreview.assetIndexOf(HtmlPreview.pagePath(3)))
        assertTrue(HtmlPreview.isHtmlName("a.HTML") && HtmlPreview.isHtmlName("b.htm") && HtmlPreview.isHtmlName("c.xhtml"))
        assertFalse(HtmlPreview.isHtmlName("d.phtml") || HtmlPreview.isHtmlName("html") || HtmlPreview.isHtmlName("e.md"))
        assertTrue(HtmlPreview.isXhtmlName("c.XHTML"))
        assertFalse(HtmlPreview.isXhtmlName("c.html"))
    }
}
