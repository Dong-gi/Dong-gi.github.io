package io.github.donggi.iroiroviewer.image

import java.io.File
import java.util.zip.GZIPOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** SVG 를 WebView 로 그리기 전의 판단([SvgSupport]) — 뿌리 요소·치수·우리가 쓰는 쪽·svgz. */
class SvgSupportTest {

    @Test
    fun `뿌리의 치수와 비율을 읽는다`() {
        val r = SvgSupport.rootOf("""<svg xmlns="http://www.w3.org/2000/svg" width="200" height="100"><rect/></svg>""")!!
        assertEquals(200.0, r.width)
        assertEquals(100.0, r.height)
        assertEquals(2.0, r.aspect)
        // 치수가 없으면 viewBox 의 비율이다(쉼표·공백이 섞인다).
        assertEquals(0.5, SvgSupport.rootOf("""<svg viewBox="0,0 50 100">""")!!.aspect)
        // 둘 다 없으면 모른다 — 화면 전체에 맞춘다.
        assertNull(SvgSupport.rootOf("""<svg xmlns="http://www.w3.org/2000/svg">""")!!.aspect)
        // 퍼센트 치수는 기준을 모른다 — viewBox 로 간다.
        assertEquals(1.5, SvgSupport.rootOf("""<svg width="100%" height="100%" viewBox="0 0 300 200">""")!!.aspect)
    }

    @Test
    fun `앞의 선언·주석·DOCTYPE 을 건너뛴다`() {
        val head = """<?xml version="1.0" encoding="UTF-8"?>
            <!-- <svg width="1" height="9"> 는 주석이다 -->
            <!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN" "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd" [
              <!ENTITY x "<svg width='1' height='9'>">
            ]>
            <svg:svg xmlns:svg="http://www.w3.org/2000/svg" width="40" height="10">"""
        assertEquals(4.0, SvgSupport.rootOf(head)!!.aspect)
    }

    @Test
    fun `DOCTYPE 의 닫는 괄호 뒤 공백을 받는다`() {
        // XML 은 `]` 와 `>` 사이의 공백을 허락한다. 처음 판은 이것을 'SVG 가 아니다' 로 보냈다.
        val head = """<!DOCTYPE svg [ <!ENTITY ns "http://www.w3.org/2000/svg"> ] >
            <svg xmlns="&ns;" width="20" height="10">"""
        assertEquals(2.0, SvgSupport.rootOf(head)!!.aspect)
        // 내부 부분집합 안의 `>` 와 따옴표 안의 `]>` 는 끝이 아니다.
        assertEquals(2.0, SvgSupport.rootOf("""<!DOCTYPE svg [ <!ENTITY a "]>"> <!ELEMENT svg ANY> ]><svg width="2" height="1">""")!!.aspect)
    }

    @Test
    fun `적대적인 앞머리도 한 번 훑고 끝난다`() {
        // 처음 판은 속성을 정규식으로 찾아, 이름 글자만 이어진 64 KiB 시작 태그 하나에 53초가 걸렸다. 길이가 두 배면
        // 네 배라, 이 크기면 옛 판은 이 시험의 상한을 확실히 넘는다(함정 표 — '시간 시험은 옛 판이 확실히 넘는 크기로').
        val names = "<svg " + "a".repeat(SvgSupport.HEAD_BYTES - 6) + ">"
        val digits = """<svg width="""" + "1".repeat(32_000) + """!" height="1">"""
        val declarations = "<!a>".repeat(SvgSupport.HEAD_BYTES / 4) + "<svg width='3' height='1'>"
        val started = System.nanoTime()
        assertNull(SvgSupport.rootOf(names)!!.aspect)
        assertNull(SvgSupport.rootOf(digits)!!.aspect)
        assertEquals(3.0, SvgSupport.rootOf(declarations)!!.aspect)
        val ms = (System.nanoTime() - started) / 1_000_000
        assertTrue(ms < 2_000, "${ms}ms")
    }

    @Test
    fun `첫 요소가 svg 가 아니면 SVG 가 아니다`() {
        assertNull(SvgSupport.rootOf("<html><body><svg width='1' height='1'></svg></body></html>"))
        assertNull(SvgSupport.rootOf("그냥 글"))
        assertNull(SvgSupport.rootOf("<svgx width='1'>"))
        // 끝나지 않은 태그·주석도 SVG 로 보지 않는다.
        assertNull(SvgSupport.rootOf("<svg width='1"))
        assertNull(SvgSupport.rootOf("<!-- 끝없는 주석"))
    }

    @Test
    fun `따옴표 안의 꺾쇠에 속지 않는다`() {
        val r = SvgSupport.rootOf("""<svg data-x="a>b" width='30' height="10">""")!!
        assertEquals(3.0, r.aspect)
    }

    @Test
    fun `길이 단위를 CSS 화소로`() {
        assertEquals(10.0, SvgSupport.lengthOf("10"))
        assertEquals(10.0, SvgSupport.lengthOf(" 10px "))
        assertEquals(96.0, SvgSupport.lengthOf("1in"))
        assertEquals(96.0, SvgSupport.lengthOf("2.54cm")!!, 1e-9)
        assertEquals(16.0, SvgSupport.lengthOf("12pt")!!, 1e-9)
        assertNull(SvgSupport.lengthOf("50%"))
        assertNull(SvgSupport.lengthOf("2em"))
        assertNull(SvgSupport.lengthOf("-5"))
        assertNull(SvgSupport.lengthOf("0"))
        assertNull(SvgSupport.lengthOf("auto"))
    }

    @Test
    fun `너무 길쭉한 비율은 자른다`() {
        assertEquals(100.0, SvgSupport.rootOf("""<svg width="100000" height="1">""")!!.aspect)
        assertEquals(0.01, SvgSupport.rootOf("""<svg width="1" height="100000">""")!!.aspect)
    }

    @Test
    fun `우리가 쓰는 쪽에는 파일의 글이 들어가지 않는다`() {
        val html = SvgSupport.pageHtml("image.svg", 2.0)
        assertTrue("<img src=\"image.svg\"" in html)
        assertTrue("calc(100vh*2.0)" in html, html)
        // 지수 표기가 CSS 를 깨지 않는다 — 잘린 비율의 양 끝.
        assertFalse('E' in SvgSupport.pageHtml("image.svg", 0.01).substringAfter("<style>"))
        assertTrue("object-fit:contain" in SvgSupport.pageHtml("image.svg", null))
        assertFalse("<script" in html)
    }

    @Test
    fun `svgz 를 앞머리로 알아보고 풀린 크기를 묶는다`() {
        val dir = createTempDirectory("svg").toFile()
        try {
            val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="3" height="1"/>"""
            val plain = File(dir, "a.svg").apply { writeText(svg) }
            val zipped = File(dir, "b.svgz").apply { writeBytes(gzip(svg.toByteArray())) }
            // 확장자는 .svg 인데 내용이 gzip 인 것도 있다.
            val mislabeled = File(dir, "c.svg").apply { writeBytes(gzip(svg.toByteArray())) }
            for (f in listOf(plain, zipped, mislabeled)) {
                val p = assertNotNull(SvgSupport.prepare(f), f.name)
                assertEquals(3.0, p.root.aspect, f.name)
                assertEquals(svg, p.open().use { it.readBytes() }.toString(Charsets.UTF_8), f.name)
            }
            // 풀면 상한을 넘는 폭탄은 그리지 않는다.
            val bomb = File(dir, "bomb.svgz").apply {
                writeBytes(gzip(ByteArray((SvgSupport.MAX_BYTES + 1).toInt())))
            }
            assertNull(SvgSupport.prepare(bomb))
            // SVG 가 아니거나 비었거나 없는 파일.
            assertNull(SvgSupport.prepare(File(dir, "x.svg").apply { writeText("<html></html>") }))
            assertNull(SvgSupport.prepare(File(dir, "empty.svg").apply { writeBytes(ByteArray(0)) }))
            assertNull(SvgSupport.prepare(File(dir, "none.svg")))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `UTF-16 SVG 의 뿌리도 읽는다`() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + """<svg width="2" height="1">""".toByteArray(Charsets.UTF_16LE)
        assertEquals(2.0, SvgSupport.rootOf(SvgSupport.decodeHead(bytes))!!.aspect)
    }

    @Test
    fun `그림 뷰어가 WebView 로 그릴 이름`() {
        assertTrue(SvgSupport.isSvgName("로고.svg"))
        assertTrue(SvgSupport.isSvgName("A.SVGZ"))
        assertFalse(SvgSupport.isSvgName("a.png"))
    }

    private fun gzip(bytes: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(bytes) }
        return out.toByteArray()
    }
}
