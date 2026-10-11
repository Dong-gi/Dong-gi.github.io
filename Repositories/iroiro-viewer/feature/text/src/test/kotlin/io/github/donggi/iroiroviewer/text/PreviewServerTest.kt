package io.github.donggi.iroiroviewer.text

import io.github.donggi.iroiroviewer.charset.TextEncoding
import io.github.donggi.iroiroviewer.format.text.HtmlPreview
import io.github.donggi.iroiroviewer.format.text.LivePage
import io.github.donggi.iroiroviewer.format.text.MarkdownPreview
import io.github.donggi.iroiroviewer.format.text.PreviewAsset
import io.github.donggi.iroiroviewer.safety.ParseLimitExceededException
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * WebView 가 청할 경로마다 **무엇이 나가는가** — 실제 파일로. 가장 흔한 실물인 '웹 페이지, 전체' 로 저장한 쪽의 모양
 * (`My Page.html` + `My Page_files/`)을 디스크에 만들고, 뷰모델과 같은 차례(앞 패스 → 스타일시트 읽기 → 위생)로 쪽을 만든 뒤
 * 쪽과 스타일시트에 적힌 번호를 하나씩 청해 본다.
 *
 * 이름에는 공백·`#`·`%`·`+`·한글이 든다(함정 표 — 위생기는 공백을 지우고 `#`·`%` 를 조각·인코딩으로 읽는다). `?` 는 이 시험이 도는
 * 윈도의 파일 이름에 쓸 수 없어 주소 단계의 시험(`PreviewUrlsTest`)이 맡는다.
 */
class PreviewServerTest {

    private lateinit var root: File
    private lateinit var site: File
    private lateinit var files: File

    @BeforeTest
    fun setUp() {
        root = File(System.getProperty("user.dir"), "build/tmp/preview-server-test").absoluteFile
        root.deleteRecursively()
        site = File(root, "site").apply { mkdirs() }
        files = File(site, "My Page_files").apply { mkdirs() }
        File(files, "fonts").mkdirs()
        File(root, "secret.png").writeBytes(png(9))
        File(files, "a+b%.png").writeBytes(png(1))
        File(files, "c#d.png").writeBytes(png(2))
        File(files, "로고 (1).png").writeBytes(png(3))
        File(files, "bg (1).png").writeBytes(png(4))
        File(files, "fonts/아이콘.woff2").writeBytes(byteArrayOf(0x77, 0x4F, 0x46, 0x32))
        File(files, "note.txt").writeBytes("글".toByteArray())
        File(files, "style.css").writeText(
            """
            @charset "utf-8";
            body { background: url("bg (1).png") }
            @font-face { font-family: I; src: url(fonts/%EC%95%84%EC%9D%B4%EC%BD%98.woff2) format("woff2") }
            .up { background: url(../../secret.png) }
            .txt { background: url(note.txt) }
            """.trimIndent(),
        )
    }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    private val page = """
        <!DOCTYPE html>
        <html lang="ko"><head><meta charset="utf-8"><title>저장한 쪽</title>
        <link rel="stylesheet" href="My%20Page_files/style.css">
        </head><body>
        <img src="My%20Page_files/a%2Bb%25.png" alt="더하기">
        <img src="./My Page_files/c%23d.png" alt="샵">
        <img src="My Page_files/로고 (1).png" alt="한글">
        <img src="../secret.png" alt="밖">
        <a href="My%20Page.html#s">자리</a> <a href="next.html">다음</a>
        </body></html>
    """.trimIndent()

    /** 뷰모델(`TextViewModel.buildHtml`)과 같은 차례로 쪽을 만든다. */
    private fun build(html: String, name: String = "My Page.html"): TextViewModel.Preview.Ready {
        val prepared = HtmlPreview.prepare(html, HtmlPreview.isXhtmlName(name), name)
        val sheets = HtmlPreview.loadStylesheets(prepared.stylesheets) { path, max -> PreviewFiles.readStylesheet(site, path, max) }
        val r = HtmlPreview.finish(prepared, sheets, TextEncoding.UTF_8)
        return TextViewModel.Preview.Ready(
            kind = PreviewKind.HTML,
            pagePath = HtmlPreview.pagePath(1),
            html = r.html.toByteArray(Charsets.UTF_8),
            assets = r.assets,
            baseDir = site,
            truncated = r.truncated,
            withheld = r.withheld,
        )
    }

    /** `/r/N` 을 WebView 가 청하는 모양(`WebHost.pathOf` — 앞의 `/` 가 없다)으로. */
    private fun requests(text: String): List<String> = Regex("/(r/[0-9]+)").findAll(text).map { it.groupValues[1] }.toList()

    private fun fileBytes(s: PreviewServer.Served?): Pair<String, ByteArray> {
        val f = assertNotNull(s as? PreviewServer.Served.FromFile, "파일이 나가지 않았다")
        return f.mimeType to f.file.readBytes()
    }

    @Test
    fun `저장한 쪽의 그림·스타일시트·글꼴이 그 파일로 나간다`() {
        val p = build(page)
        val html = String(p.html, Charsets.UTF_8)
        val served = requests(html).map { PreviewServer.serve(p, it) }
        // 쪽의 차례: 스타일시트, 더하기, 샵, 한글. 폴더 밖의 그림은 번호를 받지 못했다.
        assertEquals(4, served.size, html)
        val sheet = assertNotNull(served[0] as? PreviewServer.Served.Bytes)
        assertEquals("text/css", sheet.mimeType)
        assertEquals("utf-8", sheet.encoding)
        assertEquals("image/png" to png(1).toList(), fileBytes(served[1]).let { it.first to it.second.toList() })
        assertEquals(png(2).toList(), fileBytes(served[2]).second.toList())
        assertEquals(png(3).toList(), fileBytes(served[3]).second.toList())

        // 스타일시트 안의 번호 — 그 시트의 폴더 기준으로 풀렸다.
        val css = String(sheet.data, Charsets.UTF_8)
        val inSheet = requests(css).map { PreviewServer.serve(p, it) }
        assertEquals(png(4).toList(), fileBytes(inSheet[0]).second.toList())
        val font = fileBytes(inSheet[1])
        assertEquals("font/woff2", font.first)
        assertContentEquals(byteArrayOf(0x77, 0x4F, 0x46, 0x32), font.second)
        // 폴더 밖은 번호도 없고(`none`), 글 파일은 번호는 받아도 내주지 않는다(형식이 표에 없다).
        assertTrue("secret" !in css && "none" in css, css)
        assertEquals(3, inSheet.size, css)
        assertNull(inSheet[2])

        // 쪽 자신, 그리고 우리가 적지 않은 경로.
        val self = assertNotNull(PreviewServer.serve(p, p.pagePath) as? PreviewServer.Served.Bytes)
        assertEquals("text/html", self.mimeType)
        assertContentEquals(p.html, self.data)
        for (path in listOf("r/999", "r/-1", "../secret.png", "My Page_files/c#d.png", "My Page_files/style.css", "page-2.html", "img/0")) {
            assertNull(PreviewServer.serve(p, path), path)
        }
        // 같은 쪽의 자리는 `#s` 로, 다른 쪽은 아무 일도 하지 않는 주소로.
        assertTrue("href=\"#s\"" in html && "href=\"${HtmlPreview.LINK}\"" in html, html)
        assertTrue("secret" !in html && "My%20Page" !in html, html)
    }

    @Test
    fun `쪽을 만든 뒤에 사라진 파일은 내주지 않는다`() {
        val p = build("<img src=\"My Page_files/c%23d.png\">")
        val path = requests(String(p.html, Charsets.UTF_8)).single()
        assertNotNull(PreviewServer.serve(p, path))
        // 쪽을 만든 뒤에 파일이 사라지면 404 다(번호는 남아도 디스크에서 다시 고른다).
        File(files, "c#d.png").delete()
        assertNull(PreviewServer.serve(p, path))
    }

    @Test
    fun `data 주소는 메모리에서 나간다`() {
        val p = build("<style>.i{background:url(data:image/gif;base64,R0lGODlhAQABAAAAACw=)}</style><img src=\"data:image/png;base64,iVBORw0KGgo=\">")
        val html = String(p.html, Charsets.UTF_8)
        val served = requests(html).map { assertNotNull(PreviewServer.serve(p, it) as? PreviewServer.Served.Bytes) }
        assertEquals(listOf("image/gif", "image/png"), served.map { it.mimeType })
        assertTrue(served.all { it.encoding == null })
        assertEquals("GIF89a", String(served[0].data, 0, 6, Charsets.ISO_8859_1))
    }

    @Test
    fun `마크다운은 그림 번호로`() {
        File(site, "img").mkdirs()
        File(site, "img/cat.png").writeBytes(png(5))
        val r = MarkdownPreview.render("![고양이](img/cat.png) ![밖](../secret.png)")
        val p = TextViewModel.Preview.Ready(
            kind = PreviewKind.MARKDOWN,
            pagePath = MarkdownPreview.pagePath(1),
            html = r.html.toByteArray(Charsets.UTF_8),
            assets = r.images.map { PreviewAsset.Disk(it, PreviewAsset.Use.MARKDOWN) },
            baseDir = site,
            truncated = r.truncated,
        )
        assertEquals(png(5).toList(), fileBytes(PreviewServer.serve(p, MarkdownPreview.imagePath(0))).second.toList())
        assertNull(PreviewServer.serve(p, MarkdownPreview.imagePath(1)))
        // 마크다운 쪽에 HTML 의 번호는 없다.
        assertNull(PreviewServer.serve(p, "r/0"))
    }

    /** 고를 때 잰 크기까지만 읽는다 — 그 사이에 파일이 자라면(쓰는 중인 그림) 그 자원 하나가 실패한다. */
    @Test
    fun `디스크의 파일은 고를 때 잰 크기까지만 읽는다`() {
        val p = build("<img src=\"My Page_files/c%23d.png\">")
        val s = assertNotNull(PreviewServer.serve(p, requests(String(p.html, Charsets.UTF_8)).single()) as? PreviewServer.Served.FromFile)
        assertEquals(png(2).size.toLong(), s.length)
        assertContentEquals(png(2), PreviewServer.open(s).use { it.readBytes() })
        // 고른 뒤에 파일이 자랐다 — 잰 크기를 넘는 순간 읽기가 멈춘다(입출력 예외라 WebView 가 그 자원을 실패로 본다).
        File(files, "c#d.png").appendBytes(ByteArray(64 * 1024))
        assertFailsWith<ParseLimitExceededException> { PreviewServer.open(s).use { it.readBytes() } }
        // 메모리의 것은 그대로다.
        val self = assertNotNull(PreviewServer.serve(p, p.pagePath))
        assertContentEquals(p.html, PreviewServer.open(self).use { it.readBytes() })
    }

    /** 스크립트를 실행하는 쪽 — 뷰모델(`TextViewModel.buildLive`)이 만드는 모양 그대로. */
    private fun live(encoding: String? = null): TextViewModel.Preview.Ready {
        val pageFile = File(site, "My Page.html").apply { writeText(page) }
        return TextViewModel.Preview.Ready(
            kind = PreviewKind.HTML,
            pagePath = LivePage.PAGE_PATH,
            html = ByteArray(0),
            assets = emptyList(),
            baseDir = site,
            truncated = false,
            live = TextViewModel.Preview.Live("s0.live.iroiro.invalid", pageFile, pageFile.length(), encoding),
        )
    }

    @Test
    fun `스크립트를 실행하는 쪽은 파일의 바이트 그대로`() {
        val p = live(encoding = "euc-kr")
        val s = assertNotNull(PreviewServer.serve(p, LivePage.PAGE_PATH) as? PreviewServer.Served.FromFile)
        assertEquals("text/html", s.mimeType)
        assertEquals("euc-kr", s.encoding)
        assertContentEquals(File(site, "My Page.html").readBytes(), PreviewServer.open(s).use { it.readBytes() })
        // 문서가 말하게 둔 쪽에는 인코딩을 붙이지 않는다.
        assertNull(PreviewServer.serve(live(encoding = null), LivePage.PAGE_PATH)?.encoding)
    }

    @Test
    fun `스크립트를 실행하는 XHTML 쪽은 XML 로 나간다`() {
        val xhtml = File(site, "page.xhtml").apply { writeText("<html xmlns=\"http://www.w3.org/1999/xhtml\"><body><div/><p>뒤</p></body></html>") }
        val p = TextViewModel.Preview.Ready(
            kind = PreviewKind.HTML,
            pagePath = LivePage.PAGE_PATH,
            html = ByteArray(0),
            assets = emptyList(),
            baseDir = site,
            truncated = false,
            live = TextViewModel.Preview.Live("s0.live.iroiro.invalid", xhtml, xhtml.length(), null),
        )
        // `text/html` 로 내주면 `<div/>` 가 여는 태그가 되어 뒤의 쪽을 삼킨다.
        assertEquals("application/xhtml+xml", PreviewServer.serve(p, LivePage.PAGE_PATH)?.mimeType)
    }

    @Test
    fun `스크립트를 실행하는 쪽은 그 폴더의 파일을 이름대로 싣는다`() {
        File(files, "app.js").writeText("document.title = '실행'")
        val p = live()
        val js = fileBytes(PreviewServer.serve(p, "f/My Page_files/app.js"))
        assertEquals("text/javascript", js.first)
        assertEquals(png(1).toList(), fileBytes(PreviewServer.serve(p, "f/My Page_files/a+b%.png")).second.toList())
        // 스타일시트도 위생을 거치지 않은 원본이다 — 쪽의 스크립트가 그것을 읽을 수 있다.
        val css = fileBytes(PreviewServer.serve(p, "f/My Page_files/style.css"))
        assertEquals("text/css", css.first)
        assertTrue("../../secret.png" in String(css.second, Charsets.UTF_8))
        // 이름에 든 `#`·`%`·`+`·한글은 WebView 가 풀어서 청한다(`Uri.getPath`) — 그 모양 그대로 찾는다.
        assertEquals("image/png", fileBytes(PreviewServer.serve(p, "f/My Page_files/로고 (1).png")).first)
    }

    @Test
    fun `스크립트를 실행하는 쪽도 폴더 밖과 싣지 않는 형식은 404`() {
        File(files, "doc.pdf").writeBytes(byteArrayOf(0x25, 0x50, 0x44, 0x46))
        val p = live()
        for (path in listOf(
            "f/../secret.png", // WebView 는 `..` 을 풀어 청하지만, 풀지 않은 모양이 와도 디스크에서 막는다
            "secret.png", // 쪽의 폴더(`f/`) 밖
            "f//etc/passwd", // 절대 경로
            "f/My Page_files/", // 폴더
            "f/My Page_files/doc.pdf", // 싣지 않는 형식
            "f/없는.png",
            "r/0", // 위생을 거친 쪽의 번호 표는 여기 없다
            "page-1.html",
        )) {
            assertNull(PreviewServer.serve(p, path), path)
        }
    }

    private companion object {
        val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

        /** PNG 의 앞 네 바이트와 표지 한 바이트 — 파일마다 바이트가 다르게. */
        fun png(mark: Int): ByteArray = PNG + byteArrayOf(mark.toByte())
    }
}
