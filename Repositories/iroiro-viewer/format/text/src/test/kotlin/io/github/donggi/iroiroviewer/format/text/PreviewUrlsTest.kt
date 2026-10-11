package io.github.donggi.iroiroviewer.format.text

import io.github.donggi.iroiroviewer.format.text.PreviewUrls.Target
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 주소 하나를 브라우저처럼 읽는가. 함정 표의 둘을 박는다 — **`+` 는 공백이 아니다**(`URLDecoder` 를 쓰지 않는다), **공백은 이름의
 * 일부다**(위생기는 공백을 지운다).
 */
class PreviewUrlsTest {

    private fun local(raw: String, base: String = ""): String? = (PreviewUrls.resolve(raw, base) as? Target.Local)?.path

    @Test
    fun `퍼센트를 풀되 더하기는 그대로`() {
        assertEquals("a+b.png", local("a+b.png"))
        assertEquals("a+b.png", local("a%2Bb.png"))
        assertEquals("a b.png", local("a%20b.png"))
        assertEquals("a b.png", local("a b.png"))
        assertEquals("한글 이름.png", local("%ED%95%9C%EA%B8%80%20이름.png"))
        assertEquals("a#b.png", local("a%23b.png"))
        assertEquals("q?.png", local("q%3F.png"))
        assertEquals("100%.png", local("100%25.png"))
        // 잘못된 `%` 는 글자 그대로 — 그 앞뒤의 바른 것은 푼다.
        assertEquals("100%.png", local("100%.png"))
        assertEquals("a b%.png", local("a%20b%.png"))
        assertEquals("%zz.png", local("%zz.png"))
        // 전각 숫자는 16진 숫자가 아니다.
        assertEquals("%１２.png", local("%１２.png"))
        // UTF-8 이 아닌 퍼센트(옛 EUC-KR 쪽)는 원문을 이름으로 본다.
        assertEquals("%B0%A1.png", local("%B0%A1.png"))
    }

    @Test
    fun `조각과 질의를 뗀다`() {
        assertEquals(Target.Local("a.png", null), PreviewUrls.resolve("a.png?raw=true", ""))
        assertEquals(Target.Local("a.png", "x"), PreviewUrls.resolve("a.png#x", ""))
        assertEquals(Target.Local("a", "b?c"), PreviewUrls.resolve("a#b?c", ""))
        assertEquals(Target.Local("a.png", "f"), PreviewUrls.resolve("a.png?q#f", ""))
        assertEquals(Target.Self("f"), PreviewUrls.resolve("?q#f", ""))
        assertEquals(Target.Self(null), PreviewUrls.resolve("?q", ""))
        assertEquals(Target.Fragment("#top"), PreviewUrls.resolve("  #top ", ""))
        assertEquals(Target.Empty, PreviewUrls.resolve(" \t\n", ""))
    }

    @Test
    fun `앞뒤 공백과 가운데의 탭·줄바꿈`() {
        assertEquals("ab.png", local(" a\nb.png\t"))
        assertEquals("a b.png", local("\u0001a b.png "))
    }

    @Test
    fun `기준 폴더와 점 조각`() {
        assertEquals("page_files/bg.png", local("bg.png", "page_files/"))
        assertEquals("img/a.png", local("../img/a.png", "page_files/"))
        assertEquals("a/b.png", local("./a/./b.png"))
        assertEquals("b.png", local("a/../b.png"))
        assertEquals("page files/win.png", local("page%20files\\win.png"))
        assertNull(local("../../x.png", "page_files/"))
        assertNull(local("%2e%2e/x.png"))
        assertNull(local(".%2E/x.png"))
        assertNull(local("./"))
    }

    @Test
    fun `싣지 않는 곳`() {
        val outside = Target.Outside(script = false)
        for (raw in listOf(
            "../a.png", "/a.png", "//cdn/a.png", "\\\\server\\a.png", "C:/a.png", "c:\\a.png",
            "http://x/a.png", "HTTPS://x/a.png", "file:///sdcard/a.png", "content://x/a", "ftp://x/a", "a%00.png", "a%0A.png",
        )) {
            assertEquals(outside, PreviewUrls.resolve(raw, ""), raw)
        }
        assertEquals(Target.Outside(script = true), PreviewUrls.resolve("javascript:alert(1)", ""))
        assertEquals(Target.Outside(script = true), PreviewUrls.resolve(" JavaScript:x", ""))
        assertEquals(Target.Outside(script = true), PreviewUrls.resolve("vbscript:x", ""))
        assertTrue(PreviewUrls.resolve("data:image/png;base64,AAAA", "") is Target.Data)
        // 영문이 아닌 글자로 시작하면 스킴이 아니다 — 그런 이름의 파일이다.
        assertEquals("한:b.png", local("한:b.png"))
        assertEquals("a/b:c.png", local("a/b:c.png"))
    }

    @Test
    fun `문자 참조`() {
        assertEquals("a&b.png", PreviewUrls.decodeEntities("a&amp;b.png"))
        assertEquals("javascript:", PreviewUrls.decodeEntities("java&#115;cript:"))
        assertEquals("javascript:", PreviewUrls.decodeEntities("java&#x73;cript:"))
        assertEquals("<\"'>\u00A0", PreviewUrls.decodeEntities("&lt;&quot;&apos;&gt;&nbsp;"))
        // `;` 없는 옛 이름(`&amp`·`&lt`…)은 뒤가 영숫자나 `=` 가 아닐 때만 푼다 — 명세의 속성 규칙이고, 브라우저가 그렇게 읽는다
        // (`&copy=2`·`&ampx` 는 질의·글이다). 위생기와 한 벌인 `CharRefs` 다. 숫자 참조는 언제나 푼다.
        assertEquals("?a=1&copy=2&", PreviewUrls.decodeEntities("?a=1&copy=2&amp"))
        assertEquals("?a=1&copy=2&ampx", PreviewUrls.decodeEntities("?a=1&copy=2&ampx"))
        assertEquals("A", PreviewUrls.decodeEntities("&#65"))
        assertEquals("\uFFFD\uFFFD", PreviewUrls.decodeEntities("&#0;&#x110000;"))
        assertEquals("\uFFFD", PreviewUrls.decodeEntities("&#99999999999999999999;"))
        assertEquals("😀", PreviewUrls.decodeEntities("&#x1F600;"))
        assertEquals("&#;&#x;&", PreviewUrls.decodeEntities("&#;&#x;&"))
    }

    @Test
    fun `srcset 읽기`() {
        val c = PreviewUrls.parseSrcset("a.png 1x, b.png 2x,c.png 3x , d.png")
        assertEquals(listOf("a.png", "b.png", "c.png", "d.png"), c.map { it.url })
        assertEquals(listOf(1.0, 2.0, 3.0, 1.0), c.map { it.density })
        // 주소 안의 쉼표는 주소다.
        val data = PreviewUrls.parseSrcset("data:image/png;base64,AAAA 1x, e.png 2x")
        assertEquals(listOf("data:image/png;base64,AAAA", "e.png"), data.map { it.url })
        // 쉼표로 끝나는 주소에는 서술자가 없다. 공백 없이 붙인 쉼표는 주소의 일부다(명세 — 후보 하나).
        assertEquals(listOf("f.png", "g.png"), PreviewUrls.parseSrcset("f.png, g.png").map { it.url })
        assertEquals(listOf("f.png,g.png"), PreviewUrls.parseSrcset("f.png,g.png").map { it.url })
        val w = PreviewUrls.parseSrcset("s.png 320w, l.png 1280w")
        assertEquals(listOf(320, 1280), w.map { it.width })
        // 틀린 서술자는 그 후보만 버린다.
        assertEquals(listOf("ok.png"), PreviewUrls.parseSrcset("bad.png 2q, ok.png 1x, dup.png 1x 2x, mix.png 1x 100w").map { it.url })
    }

    @Test
    fun `srcset 고르는 차례 — 2x·1600w 이하에서 큰 것부터`() {
        fun order(s: String) = PreviewUrls.preferred(PreviewUrls.parseSrcset(s)).map { it.url }
        assertEquals(listOf("b", "a", "c"), order("a 1x, b 2x, c 3x"))
        assertEquals(listOf("m", "s", "l"), order("s 320w, l 2400w, m 1600w"))
        assertEquals(listOf("x", "y"), order("y 4x, x 3x"))
    }

    @Test
    fun `data 주소 풀기`() {
        val png = PreviewUrls.decodeData("data:image/png;base64,iVBO Rw0K\nGgo=", 1024)!!
        assertEquals("image/png", png.mediaType)
        assertContentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A), png.bytes)
        // 끝의 `=` 는 없어도 된다.
        assertContentEquals("ab".toByteArray(), PreviewUrls.decodeData("data:x/y;base64,YWI", 16)!!.bytes)
        val svg = PreviewUrls.decodeData("data:image/svg+xml;charset=utf-8,%3Csvg%20a='1'%3E+x", 1024)!!
        assertEquals("image/svg+xml", svg.mediaType)
        assertEquals("<svg a='1'>+x", String(svg.bytes, Charsets.UTF_8))
        assertEquals("text/plain", PreviewUrls.decodeData("data:,hi", 16)!!.mediaType)
        assertNull(PreviewUrls.decodeData("data:image/png;base64,@@@@", 16))
        assertNull(PreviewUrls.decodeData("data:image/png;base64,A", 16))
        assertNull(PreviewUrls.decodeData("data:image/png", 16))
        assertNull(PreviewUrls.decodeData("data:x/y,0123456789abcdef!", 16))
    }

    @Test
    fun `내주는 형식은 표에서만`() {
        val image = PreviewAsset.Use.IMAGE
        val style = PreviewAsset.Use.STYLE
        assertEquals("image/png", PreviewTypes.ofFile("A.PNG", image))
        assertEquals("image/svg+xml", PreviewTypes.ofFile("dir/i.svg", image))
        assertEquals("font/woff2", PreviewTypes.ofFile("f.woff2", style))
        assertNull(PreviewTypes.ofFile("f.woff2", image))
        assertNull(PreviewTypes.ofFile("x.css", style))
        assertNull(PreviewTypes.ofFile("x.html", image))
        assertNull(PreviewTypes.ofFile("png", image))
        assertNull(PreviewTypes.ofFile("dir.png/x", image))
        assertTrue(PreviewTypes.isStylesheetName("a/B.CSS"))
        assertTrue(!PreviewTypes.isStylesheetName("a.css.php"))
        assertEquals("image/jpeg", PreviewTypes.ofData("IMAGE/JPG", image))
        assertEquals("font/woff", PreviewTypes.ofData("application/x-font-woff", style))
        assertNull(PreviewTypes.ofData("application/x-font-woff", image))
        assertNull(PreviewTypes.ofData("text/html", style))
        assertEquals("image/svg+xml", PreviewTypes.ofData("image/svg+xml; charset=utf-8", image))
    }
}
