package io.github.donggi.iroiroviewer.text

import io.github.donggi.iroiroviewer.format.text.MarkdownPreview
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

/**
 * WebView 가 청할 경로마다 **무엇이 나가는가** — 실제 파일로. 마크다운 쪽을 뷰모델과 같은 모양으로 만들고 쪽과 그림 번호를 청해 본다.
 * 이름에 공백·`#`·`%`·`+`·한글이 든 그림도 번호로만 가리키므로 그대로 나간다.
 */
class PreviewServerTest {

    private lateinit var root: File
    private lateinit var site: File

    @BeforeTest
    fun setUp() {
        root = File(System.getProperty("user.dir"), "build/tmp/preview-server-test").absoluteFile
        root.deleteRecursively()
        site = File(root, "site").apply { mkdirs() }
        File(site, "img").mkdirs()
        File(root, "secret.png").writeBytes(png(9))
        File(site, "img/cat.png").writeBytes(png(5))
        File(site, "img/로고 (1)#a+b%.png").writeBytes(png(3))
        File(site, "img/note.txt").writeBytes("글".toByteArray())
    }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    /** 뷰모델(`TextViewModel.buildPreview`)과 같은 모양으로 쪽을 만든다. */
    private fun build(markdown: String): TextViewModel.Preview.Ready {
        val r = MarkdownPreview.render(markdown)
        return TextViewModel.Preview.Ready(
            pagePath = MarkdownPreview.pagePath(1),
            html = r.html.toByteArray(Charsets.UTF_8),
            images = r.images,
            baseDir = site,
            truncated = r.truncated,
        )
    }

    private fun fileBytes(s: PreviewServer.Served?): Pair<String, ByteArray> {
        val f = assertNotNull(s as? PreviewServer.Served.FromFile, "파일이 나가지 않았다")
        return f.mimeType to f.file.readBytes()
    }

    @Test
    fun `쪽 자신은 메모리에서 UTF-8 로`() {
        val p = build("# 제목")
        val s = assertNotNull(PreviewServer.serve(p, p.pagePath) as? PreviewServer.Served.Bytes)
        assertEquals("text/html", s.mimeType)
        assertEquals("utf-8", s.encoding)
        assertContentEquals(p.html, PreviewServer.open(s).use { it.readBytes() })
    }

    @Test
    fun `그림은 번호로, 폴더 안의 그림만`() {
        val p = build("![고양이](img/cat.png) ![밖](../secret.png) ![이름](<img/로고 (1)%23a+b%25.png>) ![글](img/note.txt)")
        assertEquals("image/png" to png(5).toList(), fileBytes(PreviewServer.serve(p, MarkdownPreview.imagePath(0))).let { it.first to it.second.toList() })
        // 폴더 밖(`../`)은 변환기가 번호를 주지 않거나, 주더라도 화면 쪽에서 막힌다.
        for (i in p.images.indices) {
            val served = PreviewServer.serve(p, MarkdownPreview.imagePath(i)) as? PreviewServer.Served.FromFile
            if (served != null) assertEquals(site.canonicalPath, served.file.canonicalFile.parentFile.parentFile.path)
        }
        // 글 파일은 그림이 아니다.
        val txt = p.images.indexOf("img/note.txt")
        if (txt >= 0) assertNull(PreviewServer.serve(p, MarkdownPreview.imagePath(txt)))
        // 표에 없는 번호와 우리가 쓰지 않는 경로는 404.
        assertNull(PreviewServer.serve(p, MarkdownPreview.imagePath(99)))
        assertNull(PreviewServer.serve(p, "r/0"))
        assertNull(PreviewServer.serve(p, "f/"))
        assertNull(PreviewServer.serve(p, "img/cat.png"))
    }

    @Test
    fun `쪽을 만든 뒤에 사라진 파일은 내주지 않는다`() {
        val p = build("![고양이](img/cat.png)")
        File(site, "img/cat.png").delete()
        assertNull(PreviewServer.serve(p, MarkdownPreview.imagePath(0)))
    }

    /** 고를 때 잰 크기까지만 읽는다 — 그 사이에 파일이 자라면(쓰는 중인 그림) 그 자원 하나가 실패한다. */
    @Test
    fun `디스크의 파일은 고를 때 잰 크기까지만 읽는다`() {
        val p = build("![고양이](img/cat.png)")
        val s = assertNotNull(PreviewServer.serve(p, MarkdownPreview.imagePath(0)) as? PreviewServer.Served.FromFile)
        assertEquals(png(5).size.toLong(), s.length)
        assertContentEquals(png(5), PreviewServer.open(s).use { it.readBytes() })
        File(site, "img/cat.png").appendBytes(ByteArray(64 * 1024))
        assertFailsWith<ParseLimitExceededException> { PreviewServer.open(s).use { it.readBytes() } }
    }

    private companion object {
        val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

        /** PNG 의 앞 네 바이트와 표지 한 바이트 — 파일마다 바이트가 다르게. */
        fun png(mark: Int): ByteArray = PNG + byteArrayOf(mark.toByte())
    }
}
