package io.github.donggi.iroiroviewer.docview.pdf.text

import io.github.donggi.iroiroviewer.docview.pdf.crypt.ByteArraySource
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 글 꺼내기([PdfTextEngine]). 표본은 시험이 손으로 짓는 작은 PDF 다 — 무엇을 넣었는지 알아야 꺼낸 자리를 잴 수 있다.
 * 실물 문서와의 대조는 `PdfTextCorpusTest`(pypdf 를 오라클로)가 한다.
 */
class PdfTextEngineTest {

    /** 쪽 하나짜리 PDF. [font] 는 5번 객체, [extra] 는 6번부터. */
    private fun onePage(
        content: String,
        font: String = "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
        pageExtra: String = "",
        extra: List<ByteArray> = emptyList(),
        contentBytes: ByteArray? = null,
        contentDict: String = "",
    ): ByteArray = MiniPdf.build(
        listOf(
            "<< /Type /Catalog /Pages 2 0 R >>".toByteArray(),
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>".toByteArray(),
            ("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] $pageExtra " +
                "/Resources << /Font << /F1 5 0 R >> /XObject << /X1 6 0 R >> >> /Contents 4 0 R >>").toByteArray(),
            MiniPdf.stream(contentDict, contentBytes ?: content.toByteArray(Charsets.ISO_8859_1)),
            font.toByteArray(Charsets.ISO_8859_1),
        ) + extra,
    )

    private fun page(pdf: ByteArray): PageText {
        val engine = assertNotNull(PdfTextEngine.open(ByteArraySource(pdf)))
        assertEquals(1, engine.pageCount)
        return engine.page(0)
    }

    private fun text(p: PageText) = PdfTextLayout.text(p, 0, p.glyphs.size)

    private fun near(expected: Float, actual: Float, tol: Float = 0.01f) =
        assertTrue(abs(expected - actual) <= tol, "기대 $expected, 실제 $actual")

    @Test
    fun `글자와 자리를 보이는 쪽의 좌표로 꺼낸다`() {
        val p = page(onePage("BT /F1 12 Tf 72 700 Td (Hello World) Tj ET"))
        assertEquals("Hello World", text(p))
        assertEquals(11, p.glyphs.size)
        assertEquals(612f, p.width)
        assertEquals(792f, p.height)
        val h = p.glyphs[0]
        near(72f, h.left)
        // 기준선은 792 − 700 = 92. 글자 상자는 그 위 0.8 em, 아래 0.2 em(기술자가 없을 때의 값).
        near(92f, h.originY)
        near(92f - 9.6f, h.top)
        near(92f + 2.4f, h.bottom)
        near(12f, h.size)
    }

    @Test
    fun `폭 배열만큼 정확히 민다`() {
        val font = "<< /Type /Font /Subtype /TrueType /BaseFont /X /FirstChar 65 /LastChar 66 /Widths [500 600] >>"
        val p = page(onePage("BT /F1 10 Tf 100 700 Td (AB) Tj ET", font))
        near(100f, p.glyphs[0].left)
        near(105f, p.glyphs[0].right)
        near(105f, p.glyphs[1].left)
        near(111f, p.glyphs[1].right)
    }

    @Test
    fun `글자 사이·낱말 사이 간격과 TJ 의 조정을 따른다`() {
        // Courier 는 600 이다. 자간 1, 가로 비율 50%.
        val font = "<< /Type /Font /Subtype /Type1 /BaseFont /Courier >>"
        val p = page(onePage("BT /F1 10 Tf 1 Tc 50 Tz 0 0 Td (ab) Tj ET", font))
        // (0.6 × 10 + 1) × 0.5 = 3.5
        near(3.5f, p.glyphs[1].originX)
        // TJ 의 −500 은 글자 크기의 반만큼 오른쪽으로 민다 → 띄어쓰기로 읽힌다.
        val q = page(onePage("BT /F1 10 Tf 0 0 Td [(Hello) -500 (World)] TJ ET", font))
        assertEquals("Hello World", text(q))
        // 작은 조정(자간)은 붙인다.
        val r = page(onePage("BT /F1 10 Tf 0 0 Td [(Wo) 40 (rld)] TJ ET", font))
        assertEquals("World", text(r))
    }

    @Test
    fun `줄이 바뀌면 줄바꿈이다`() {
        val p = page(onePage("BT /F1 12 Tf 14 TL 72 700 Td (Line one) Tj T* (Line two) Tj 0 -14 Td (3) Tj ET"))
        assertEquals("Line one\nLine two\n3", text(p))
    }

    @Test
    fun `ToUnicode 가 Identity-H 의 코드를 글자로 옮긴다`() {
        val cmap = """
            /CIDInit /ProcSet findresource begin 12 dict begin begincmap
            1 begincodespacerange <0000> <FFFF> endcodespacerange
            2 beginbfchar <0001> <AC00> <0002> <B098> endbfchar
            1 beginbfrange <0010> <0012> <0041> endbfrange
            endcmap CMapName currentdict /CMap defineresource pop end end
        """.trimIndent()
        val font = "<< /Type /Font /Subtype /Type0 /BaseFont /X /Encoding /Identity-H " +
            "/DescendantFonts [7 0 R] /ToUnicode 6 0 R >>"
        val descendant = ("<< /Type /Font /Subtype /CIDFontType2 /BaseFont /X " +
            "/CIDSystemInfo << /Registry (Adobe) /Ordering (Identity) /Supplement 0 >> /DW 1000 /W [16 [500 500 500]] >>").toByteArray()
        val p = page(
            onePage(
                "BT /F1 10 Tf 50 50 Td <0001000200100011> Tj ET",
                font,
                extra = listOf(MiniPdf.stream("", cmap.toByteArray()), descendant),
            ),
        )
        assertEquals("가나AB", text(p))
        // 한글은 DW(1000), 'A' 는 W 의 500.
        near(10f, p.glyphs[0].right - p.glyphs[0].left)
        near(5f, p.glyphs[2].right - p.glyphs[2].left)
    }

    @Test
    fun `미리 정의된 KSCms-UHC-H 는 CP949 로 읽는다`() {
        val font = "<< /Type /Font /Subtype /Type0 /BaseFont /HYGoThic-Medium /Encoding /KSCms-UHC-H " +
            "/DescendantFonts [<< /Type /Font /Subtype /CIDFontType0 /BaseFont /HYGoThic-Medium " +
            "/CIDSystemInfo << /Registry (Adobe) /Ordering (Korea1) /Supplement 1 >> /DW 1000 >>] >>"
        val bytes = "한글A".toByteArray(charset("x-windows-949"))
        val hex = bytes.joinToString("") { "%02X".format(it) }
        val p = page(onePage("BT /F1 10 Tf 50 50 Td <$hex> Tj ET", font))
        assertEquals("한글A", text(p))
        // 한 바이트 글자(ASCII)는 반각으로 친다.
        near(5f, p.glyphs[2].right - p.glyphs[2].left)
    }

    @Test
    fun `차이 배열의 글리프 이름을 글자로 옮긴다`() {
        val font = "<< /Type /Font /Subtype /Type1 /BaseFont /CMR10 /FirstChar 1 /LastChar 3 /Widths [500 500 500] " +
            "/Encoding << /Type /Encoding /Differences [1 /fi /quotedblleft /uni00E9] >> >>"
        val p = page(onePage("BT /F1 10 Tf 0 0 Td <010203> Tj ET", font))
        assertEquals("fi“é", text(p))
    }

    @Test
    fun `쪽 회전을 보이는 쪽에 반영한다`() {
        val p = page(onePage("BT /F1 12 Tf 72 700 Td (A) Tj ET", pageExtra = "/Rotate 90"))
        assertEquals(792f, p.width)
        assertEquals(612f, p.height)
        // 시계 방향 90°: 보이는 x 는 사용자 y, 보이는 y 는 사용자 x.
        near(700f, p.glyphs[0].originX)
        near(72f, p.glyphs[0].originY)
        // 글자는 보이는 쪽에서 아래로 나아간다.
        near(0f, p.glyphs[0].dirX)
        near(1f, p.glyphs[0].dirY)
    }

    @Test
    fun `자르기 상자의 왼쪽 위가 원점이다`() {
        val p = page(onePage("BT /F1 12 Tf 72 700 Td (A) Tj ET", pageExtra = "/CropBox [50 100 562 742]"))
        assertEquals(512f, p.width)
        assertEquals(642f, p.height)
        near(22f, p.glyphs[0].originX)
        near(42f, p.glyphs[0].originY)
    }

    @Test
    fun `폼 XObject 안의 글을 그 행렬로 옮긴다`() {
        val form = MiniPdf.stream(
            "/Type /XObject /Subtype /Form /BBox [0 0 100 100] /Matrix [1 0 0 1 100 0]",
            "BT /F1 10 Tf 0 0 Td (in form) Tj ET".toByteArray(),
        )
        val p = page(onePage("q 1 0 0 1 0 100 cm /X1 Do Q BT /F1 10 Tf 0 0 Td (out) Tj ET", extra = listOf(form)))
        assertEquals("in form\nout", text(p))
        near(100f, p.glyphs[0].originX)
        near(692f, p.glyphs[0].originY)
    }

    @Test
    fun `인라인 그림의 바이트를 건너뛴다`() {
        val out = ByteArrayOutputStream()
        out.write("BI /W 4 /H 1 /CS /G /BPC 8 ID ".toByteArray())
        out.write(byteArrayOf('A'.code.toByte(), 'E'.code.toByte(), 'I'.code.toByte(), 0x28))
        out.write("\nEI\nBT /F1 12 Tf 10 10 Td (after) Tj ET".toByteArray())
        val p = page(onePage("", contentBytes = out.toByteArray()))
        assertEquals("after", text(p))
    }

    @Test
    fun `압축된 내용 스트림을 푼다`() {
        val raw = "BT /F1 12 Tf 10 10 Td (deflated) Tj ET".toByteArray()
        val d = Deflater()
        d.setInput(raw)
        d.finish()
        val buf = ByteArray(1024)
        val n = d.deflate(buf)
        d.end()
        val p = page(onePage("", contentBytes = buf.copyOf(n), contentDict = "/Filter /FlateDecode"))
        assertEquals("deflated", text(p))
        val hex = raw.joinToString("") { "%02x".format(it) } + ">"
        val q = page(onePage("", contentBytes = hex.toByteArray(), contentDict = "/Filter /ASCIIHexDecode"))
        assertEquals("deflated", text(q))
    }

    /** 명세 7.4.4.2 의 예 — `-----A---B` 를 LZW 로 적은 바이트. */
    @Test
    fun `LZW 는 명세의 예를 그대로 푼다`() {
        val bytes = byteArrayOf(0x80.toByte(), 0x0B, 0x60, 0x50, 0x22, 0x0C, 0x0C, 0x85.toByte(), 0x01)
        assertEquals("-----A---B", String(Lzw.decode(bytes, 1, 1 shl 20), Charsets.ISO_8859_1))
    }

    @Test
    fun `망가진 내용은 거기까지 꺼낸다`() {
        val p = page(onePage("BT /F1 12 Tf 10 10 Td (ok) Tj [ (unclosed"))
        assertEquals("ok", text(p))
    }

    @Test
    fun `세로쓰기는 아래로 나아간다`() {
        val font = "<< /Type /Font /Subtype /Type0 /BaseFont /X /Encoding /Identity-V /DescendantFonts [<< /Type /Font " +
            "/Subtype /CIDFontType0 /BaseFont /X /CIDSystemInfo << /Registry (Adobe) /Ordering (Identity) /Supplement 0 >> >>] " +
            "/ToUnicode 6 0 R >>"
        val cmap = "1 begincodespacerange <0000> <FFFF> endcodespacerange 2 beginbfchar <0001> <4E00> <0002> <4E8C> endbfchar"
        val p = page(onePage("BT /F1 10 Tf 100 700 Td <00010002> Tj ET", font, extra = listOf(MiniPdf.stream("", cmap.toByteArray()))))
        assertEquals(2, p.glyphs.size)
        near(10f, p.glyphs[1].originY - p.glyphs[0].originY)
        near(1f, p.glyphs[0].dirY)
        // 세로로 이어진 두 글자는 한 줄이다(띄어 쓰지 않는다).
        assertEquals("一二", text(p))
    }

    // ---- 검토가 잡은 적대적 입력 ----

    /**
     * 객체 스트림의 `/Length` 가 그 스트림 안의 객체를 가리킨다 — 예전에는 `objStream → resolve → load → objStream` 이 끝없이
     * 돌아 `StackOverflowError` 로 앱이 죽었다. 이제 그 참조는 '없다' 가 되고, 길이는 `endstream` 을 찾아 정한다.
     */
    @Test
    fun `제 안을 가리키는 객체 스트림의 길이에 넘어지지 않는다`() {
        val font = "<< /Type /Font /Subtype /Type1 /BaseFont /Courier >>"
        val objstmBody = "7 0 $font".toByteArray(Charsets.ISO_8859_1)
        val objstm = ByteArrayOutputStream().apply {
            write("<< /Type /ObjStm /N 1 /First 4 /Length 7 0 R >>\nstream\n".toByteArray(Charsets.ISO_8859_1))
            write(objstmBody)
            write("\nendstream".toByteArray(Charsets.ISO_8859_1))
        }.toByteArray()
        val pdf = MiniPdf.buildWithXrefStream(
            listOf(
                "<< /Type /Catalog /Pages 2 0 R >>".toByteArray(),
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>".toByteArray(),
                ("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] " +
                    "/Resources << /Font << /F1 7 0 R >> >> /Contents 4 0 R >>").toByteArray(),
                MiniPdf.stream("", "BT /F1 12 Tf 10 10 Td (inside) Tj ET".toByteArray()),
                objstm,
            ),
            inStream = mapOf(7 to (5 to 0)),
        )
        assertEquals("inside", text(page(pdf)))
    }

    /** `/W` 가 같은 간접 배열을 수천 번 가리킨다 — 예전에는 몇 KB 로 수십 GB 를 청했다. 값의 수를 표 전체로 묶는다. */
    @Test
    fun `폭 표 폭탄을 묶는다`() {
        val widths = (1..10_000).joinToString(" ", "[", "]") { "500" }
        val w = (0 until 5_000).joinToString(" ", "[", "]") { "${it * 10_000} 7 0 R" }
        val font = "<< /Type /Font /Subtype /Type0 /BaseFont /X /Encoding /Identity-H /DescendantFonts [6 0 R] >>"
        val descendant = ("<< /Type /Font /Subtype /CIDFontType2 /BaseFont /X " +
            "/CIDSystemInfo << /Registry (Adobe) /Ordering (Identity) /Supplement 0 >> /W $w >>").toByteArray()
        val started = System.nanoTime()
        val p = page(onePage("BT /F1 10 Tf 0 0 Td <0001> Tj ET", font, extra = listOf(descendant, widths.toByteArray())))
        assertEquals(1, p.glyphs.size)
        // 5천 × 1만 = 5천만 값을 청한다 — 묶지 않으면 메모리가 넘치거나 수십 초가 걸린다.
        assertTrue((System.nanoTime() - started) / 1_000_000 < 5_000, "너무 오래 걸렸다")
    }

    /** 폼은 `Do` 를 부른 자리의 글꼴을 물려받는다(명세 8.10.1). 예전에는 빈 상태로 시작해 글이 빠졌다. */
    @Test
    fun `폼이 부른 자리의 글꼴을 물려받는다`() {
        val form = MiniPdf.stream(
            "/Type /XObject /Subtype /Form /BBox [0 0 100 100]",
            "BT 0 0 Td (inherited) Tj ET".toByteArray(),
        )
        val p = page(onePage("BT /F1 10 Tf ET /X1 Do", extra = listOf(form)))
        assertEquals("inherited", text(p))
    }

    /** 범위가 많은 CMap — 이분 탐색이 경계와 겹친 범위를 옳게 찾는다. */
    @Test
    fun `범위가 많은 CMap 에서 찾는다`() {
        val sb = StringBuilder("1 begincodespacerange <0000> <FFFF> endcodespacerange 1000 beginbfrange ")
        // 0000~0009 → U+AC00…, 0010~0019 → U+AC10… 처럼 열 개씩 천 개.
        for (i in 0 until 1000) {
            val lo = i * 16
            sb.append("<%04X> <%04X> <%04X> ".format(lo, lo + 9, 0xAC00 + lo))
        }
        sb.append("endbfrange")
        val cmap = PdfCMap.parse(sb.toString().toByteArray())
        assertEquals("\uAC00", cmap.unicode(0x0000, 2))
        assertEquals("\uAC09", cmap.unicode(0x0009, 2))
        // 범위 사이의 빈 자리(000A~000F)는 없다.
        assertEquals(null, cmap.unicode(0x000A, 2))
        assertEquals("%c".format((0xAC00 + 999 * 16 + 9).toChar()), cmap.unicode((999 * 16 + 9).toLong(), 2))
        // 길이가 다르면 다른 코드다.
        assertEquals(null, cmap.unicode(0x0001, 1))
    }
}

/** 시험용 PDF 짓기 — 객체 번호는 1부터, xref 는 실제 자리로 적는다. */
internal object MiniPdf {
    fun stream(dict: String, data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("<< $dict /Length ${data.size} >>\nstream\n".toByteArray(Charsets.ISO_8859_1))
        out.write(data)
        out.write("\nendstream".toByteArray(Charsets.ISO_8859_1))
        return out.toByteArray()
    }

    /**
     * xref **스트림**으로 짓는다 — 객체 스트림 속 번호([inStream]: 번호 → (스트림 번호, 차례))를 적을 수 있다. xref 스트림은
     * 마지막 번호 다음 자리에 둔다.
     */
    fun buildWithXrefStream(objects: List<ByteArray>, inStream: Map<Int, Pair<Int, Int>>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("%PDF-1.7\n%\u00e2\u00e3\n".toByteArray(Charsets.ISO_8859_1))
        val offsets = ArrayList<Int>()
        objects.forEachIndexed { i, body ->
            offsets += out.size()
            out.write("${i + 1} 0 obj\n".toByteArray(Charsets.ISO_8859_1))
            out.write(body)
            out.write("\nendobj\n".toByteArray(Charsets.ISO_8859_1))
        }
        val xrefNum = maxOf(objects.size, inStream.keys.maxOrNull() ?: 0) + 1
        val xrefAt = out.size()
        val rows = ByteArrayOutputStream()
        fun row(type: Int, f2: Int, f3: Int) {
            rows.write(type)
            for (sh in intArrayOf(24, 16, 8, 0)) rows.write((f2 shr sh) and 0xFF)
            rows.write((f3 shr 8) and 0xFF)
            rows.write(f3 and 0xFF)
        }
        for (n in 0..xrefNum) {
            when {
                n == 0 -> row(0, 0, 0xFFFF)
                n == xrefNum -> row(1, xrefAt, 0)
                inStream.containsKey(n) -> row(2, inStream.getValue(n).first, inStream.getValue(n).second)
                n <= objects.size -> row(1, offsets[n - 1], 0)
                else -> row(0, 0, 0)
            }
        }
        val data = rows.toByteArray()
        out.write("$xrefNum 0 obj\n<< /Type /XRef /Size ${xrefNum + 1} /W [1 4 2] /Root 1 0 R /Length ${data.size} >>\nstream\n"
            .toByteArray(Charsets.ISO_8859_1))
        out.write(data)
        out.write("\nendstream\nendobj\nstartxref\n$xrefAt\n%%EOF\n".toByteArray(Charsets.ISO_8859_1))
        return out.toByteArray()
    }

    fun build(objects: List<ByteArray>, trailerExtra: String = ""): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("%PDF-1.7\n%âã\n".toByteArray(Charsets.ISO_8859_1))
        val offsets = ArrayList<Int>()
        objects.forEachIndexed { i, body ->
            offsets += out.size()
            out.write("${i + 1} 0 obj\n".toByteArray(Charsets.ISO_8859_1))
            out.write(body)
            out.write("\nendobj\n".toByteArray(Charsets.ISO_8859_1))
        }
        val xref = out.size()
        val sb = StringBuilder("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
        for (o in offsets) sb.append("%010d 00000 n \n".format(o))
        sb.append("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R $trailerExtra >>\nstartxref\n$xref\n%%EOF\n")
        out.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
        return out.toByteArray()
    }
}
