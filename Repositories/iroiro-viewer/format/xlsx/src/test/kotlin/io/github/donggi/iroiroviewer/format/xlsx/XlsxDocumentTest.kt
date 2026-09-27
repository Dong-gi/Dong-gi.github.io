package io.github.donggi.iroiroviewer.format.xlsx

import io.github.donggi.iroiroviewer.format.FlowKind
import io.github.donggi.iroiroviewer.format.FlowWarnings
import io.github.donggi.iroiroviewer.format.ProgressSink
import io.github.donggi.iroiroviewer.format.UnsupportedFeatures
import io.github.donggi.iroiroviewer.format.opc.OpcPackage
import io.github.donggi.iroiroviewer.format.opc.TinyOoxml
import io.github.donggi.iroiroviewer.format.xlsx.Books.book
import io.github.donggi.iroiroviewer.format.xlsx.Books.sheet
import io.github.donggi.iroiroviewer.safety.ParseLimits
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 통합 문서 → 흐름 문서. 표본은 `TinyOoxml` 로 시험 안에서 짠다(읽히는 모양이 곧 시험의 뜻이다). */
class XlsxDocumentTest {

    private fun one(xml: String, sst: String? = null, styles: String? = null, date1904: Boolean = false) =
        Books.open(book(listOf(Books.Sheet("Sheet1", xml)), sst, styles, date1904))

    private fun grid(xml: String, sst: String? = null, styles: String? = null, date1904: Boolean = false): Grid.Parsed =
        one(xml, sst, styles, date1904).use { Grid.parse(it.partHtml(0)!!) }

    private fun inline(ref: String, text: String) = """<c r="$ref" t="inlineStr"><is><t>${Books.esc(text)}</t></is></c>"""

    private fun rows(n: Int, from: Int = 1) = (from until from + n).joinToString("") { """<row r="$it"><c r="A$it"><v>$it</v></c></row>""" }

    // ---- 값 ----

    @Test
    fun 공유_문자열은_조각을_잇고_윗주를_버린다() {
        val sst = """<sst xmlns="${Books.NS_S}">
            <si><t>plain</t></si>
            <si><r><t xml:space="preserve">rich </t></r><r><rPr><b/></rPr><t>text</t></r></si>
            <si><t>漢字</t><rPh sb="0" eb="2"><t>かんじ</t></rPh><phoneticPr fontId="1"/></si>
            <si><t>a_x0041_b</t></si>
        </sst>"""
        val g = grid(
            sheet("""<row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c><c r="C1" t="s"><v>2</v></c><c r="D1" t="s"><v>3</v></c><c r="E1" t="s"><v>99</v></c></row>"""),
            sst = sst,
        )
        assertEquals("plain", g.cells["A1"]!!.text)
        assertEquals("rich text", g.cells["B1"]!!.text)
        assertEquals("漢字", g.cells["C1"]!!.text)
        assertEquals("aAb", g.cells["D1"]!!.text)
        // 없는 번호는 빈 칸이다(던지지 않는다).
        assertEquals("", g.cells["E1"]!!.text)
    }

    @Test
    fun 인라인_문자열() {
        val g = grid(
            sheet(
                """<row r="1"><c r="A1" t="inlineStr"><is><t>just</t></is></c>""" +
                    """<c r="B1" t="inlineStr"><is><r><t>ru</t></r><r><t>ns</t></r><rPh><t>x</t></rPh></is></c></row>""",
            ),
        )
        assertEquals("just", g.cells["A1"]!!.text)
        assertEquals("runs", g.cells["B1"]!!.text)
    }

    @Test
    fun 참거짓과_오류는_가운데_숫자는_오른쪽() {
        val g = grid(
            sheet(
                """<row r="1"><c r="A1" t="b"><v>1</v></c><c r="B1" t="b"><v>0</v></c>""" +
                    """<c r="C1" t="e"><v>#DIV/0!</v></c><c r="D1"><v>1234.5</v></c>${inline("E1", "txt")}</row>""",
            ),
        )
        assertEquals("TRUE", g.cells["A1"]!!.text)
        assertEquals("FALSE", g.cells["B1"]!!.text)
        assertEquals("#DIV/0!", g.cells["C1"]!!.text)
        assertEquals("1234.5", g.cells["D1"]!!.text)
        assertEquals(listOf("c"), g.cells["A1"]!!.classes)
        assertEquals(listOf("c"), g.cells["C1"]!!.classes)
        assertEquals(listOf("n"), g.cells["D1"]!!.classes)
        assertFalse("n" in g.cells["E1"]!!.classes)
    }

    @Test
    fun 계산되지_않은_수식은_빈_칸이고_한_번만_센다() {
        val s1 = sheet("""<row r="1"><c r="A1"><f>1+1</f><v>2</v></c><c r="B1"><f>SUM(A1)</f></c><c r="C1"><f>A1</f><v></v></c><c r="D1" t="str"><f>""</f><v></v></c></row>""")
        val s2 = sheet("""<row r="1"><c r="A1"><f>9</f></c></row>""")
        Books.open(book(listOf(Books.Sheet("a", s1), Books.Sheet("b", s2)))).use { doc ->
            val g = Grid.parse(doc.partHtml(0)!!)
            assertEquals("2", g.cells["A1"]!!.text)
            assertEquals("", g.cells["B1"]!!.text)
            assertEquals("", g.cells["C1"]!!.text)
            assertNotNull(doc.partHtml(1))
            assertEquals(1, doc.unsupported.snapshot()[UnsupportedFeatures.FORMULA_CACHED])
        }
    }

    @Test
    fun 표시_형식과_날짜() {
        val styles = """<styleSheet xmlns="${Books.NS_S}">
            <numFmts><numFmt numFmtId="164" formatCode="yyyy&quot;년&quot; m&quot;월&quot;"/></numFmts>
            <cellXfs>
              <xf numFmtId="0"/><xf numFmtId="10"/><xf numFmtId="14"/><xf numFmtId="164"/><xf numFmtId="22"/><xf numFmtId="4"/>
            </cellXfs></styleSheet>"""
        val g = grid(
            sheet(
                """<row r="1"><c r="A1" s="1"><v>0.256</v></c><c r="B1" s="2"><v>45366</v></c><c r="C1" s="3"><v>45366</v></c>""" +
                    """<c r="D1" s="4" t="d"><v>2024-03-15T13:45:00</v></c><c r="E1" s="5"><v>-1234.5</v></c><c r="F1" s="9"><v>7</v></c></row>""",
            ),
            styles = styles,
        )
        assertEquals("25.60%", g.cells["A1"]!!.text)
        assertEquals("03-15-24", g.cells["B1"]!!.text)
        assertEquals("2024년 3월", g.cells["C1"]!!.text)
        assertEquals("3/15/24 13:45", g.cells["D1"]!!.text)
        assertEquals("-1,234.50", g.cells["E1"]!!.text)
        // 없는 서식 번호는 기본 서식으로.
        assertEquals("7", g.cells["F1"]!!.text)
    }

    @Test
    fun 날짜_체계_1904() {
        val styles = """<styleSheet xmlns="${Books.NS_S}"><cellXfs><xf numFmtId="0"/><xf numFmtId="14"/></cellXfs></styleSheet>"""
        val g = grid(
            sheet("""<row r="1"><c r="A1" s="1"><v>0</v></c><c r="B1" s="1" t="d"><v>2024-03-15T10:00:00</v></c></row>"""),
            styles = styles,
            date1904 = true,
        )
        assertEquals("01-01-04", g.cells["A1"]!!.text)
        // ISO 날짜 칸(`t="d"`)도 같은 체계로 일련번호를 만든다 — 1900 체계로 만들면 4년 하루가 밀린다.
        assertEquals("03-15-24", g.cells["B1"]!!.text)
    }

    @Test
    fun 없는_칸은_행_서식_다음_열_서식을_입는다() {
        val styles = """<styleSheet xmlns="${Books.NS_S}">
            <fills count="4"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill>
              <fill><patternFill patternType="solid"><fgColor rgb="FFFFFF00"/></patternFill></fill>
              <fill><patternFill patternType="solid"><fgColor rgb="FF00B0F0"/></patternFill></fill></fills>
            <cellXfs><xf/><xf fillId="2"/><xf fillId="3"/></cellXfs></styleSheet>"""
        val g = grid(
            sheet(
                """<row r="1" s="1" customFormat="1"><c r="A1"><v>1</v></c></row>""" +
                    // `customFormat` 이 없으면 행의 `s` 는 쓰지 않는다(명세).
                    """<row r="2" s="1"><c r="A2"><v>2</v></c><c r="C2"><v>3</v></c></row>""",
                before = """<cols><col min="2" max="2" width="9" style="2"/></cols>""",
            ),
            styles = styles,
        )
        // 있는 칸은 자기 서식(`s` 가 없으면 0)이다.
        assertFalse("background" in g.cells["A1"]!!.style)
        // 행 서식이 열 서식을 이긴다.
        assertTrue("background-color:#ffff00" in g.cells["B1"]!!.style, g.cells["B1"]!!.style)
        assertTrue("background-color:#ffff00" in g.cells["C1"]!!.style, g.cells["C1"]!!.style)
        assertTrue("background-color:#00b0f0" in g.cells["B2"]!!.style, g.cells["B2"]!!.style)
        assertFalse("background" in g.cells["C2"]!!.style)
    }

    @Test
    fun 서식과_공유_문자열이_없어도_그린다() {
        val g = grid(sheet("""<row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" s="7"><v>45366</v></c></row>"""))
        assertEquals("", g.cells["A1"]!!.text)
        assertEquals("45366", g.cells["B1"]!!.text)
    }

    @Test
    fun 표의_글자는_이스케이프되고_바깥_연결은_링크가_아니다() {
        val xml = sheet(
            """<row r="1">${inline("A1", "<script>alert(1)</script>")}${inline("B1", "outside")}</row>""",
            after = """<hyperlinks><hyperlink ref="B1" r:id="rIdX"/></hyperlinks>""",
        )
        val t = book(listOf(Books.Sheet("<img src=x onerror=alert(1)>", xml)))
        t.rel(Books.sheetPart(0), "rIdX", TinyOoxml.REL_HYPERLINK, "https://tracker.example/x", external = true)
        Books.open(t).use { doc ->
            val html = doc.partHtml(0)!!
            assertFalse("<script" in html)
            assertFalse("tracker" in html)
            assertFalse("onerror" in html)
            assertEquals("<script>alert(1)</script>", Grid.parse(html).cells["A1"]!!.text)
            // 시트 이름은 자료다 — 화면이 이스케이프한다. 여기서는 그대로 전한다.
            assertEquals("<img src=x onerror=alert(1)>", doc.parts[0].label)
        }
    }

    // ---- 시트 ----

    @Test
    fun 숨긴_시트는_부분이_되지_않는다() {
        val t = book(
            listOf(
                Books.Sheet("보임", sheet(rows(1))),
                Books.Sheet("숨김", sheet(rows(1)), state = "hidden"),
                Books.Sheet("아주숨김", sheet(rows(1)), state = "veryHidden"),
                Books.Sheet("둘째", sheet(rows(1))),
            ),
        )
        Books.open(t).use { doc ->
            assertEquals(FlowKind.SHEETS, doc.kind)
            assertEquals(listOf("보임", "둘째"), doc.parts.map { it.label })
            assertEquals(listOf("보임", "둘째"), doc.outline.map { it.title })
            assertEquals(listOf(0, 1), doc.outline.map { it.partIndex })
            assertEquals("~part-1.html", doc.parts[1].path)
        }
    }

    @Test
    fun 보이는_시트가_없으면_숨긴_것까지_연다() {
        val t = book(listOf(Books.Sheet("h1", sheet(rows(1)), state = "hidden"), Books.Sheet("h2", sheet(rows(1)), state = "veryHidden")))
        Books.open(t).use { doc -> assertEquals(listOf("h1", "h2"), doc.parts.map { it.label }) }
    }

    @Test
    fun 차트_시트는_세고_부분을_만들지_않는다() {
        val t = book(
            listOf(
                Books.Sheet("차트", """<chartsheet xmlns="${Books.NS_S}"/>""", type = Books.REL_CHARTSHEET),
                Books.Sheet("값", sheet(rows(1))),
            ),
        )
        Books.open(t).use { doc ->
            assertEquals(listOf("값"), doc.parts.map { it.label })
            assertEquals(1, doc.unsupported.snapshot()[UnsupportedFeatures.CHART])
        }
    }

    @Test
    fun 제목은_문서_속성에서() {
        val t = book(listOf(Books.Sheet("a", sheet(rows(1)))))
        t.xml(
            "docProps/core.xml",
            "application/vnd.openxmlformats-package.core-properties+xml",
            """<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title> 분기 보고 </dc:title></cp:coreProperties>""",
        )
        t.rel(null, "rId9", "http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties", "docProps/core.xml")
        Books.open(t).use { assertEquals("분기 보고", it.title) }
        Books.open(book(listOf(Books.Sheet("a", sheet(rows(1)))))).use { assertEquals("", it.title) }
    }

    @Test
    fun 깨진_통합_문서는_열지_못한다() {
        val t = TinyOoxml()
            .xml("xl/workbook.xml", TinyOoxml.CT_XLSX_MAIN, """<workbook xmlns="${Books.NS_S}"><sheets><sheet name="a"""")
            .rel(null, "rId1", TinyOoxml.REL_OFFICE_DOCUMENT, "xl/workbook.xml")
        // 여는이(`OoxmlOpener`)가 이 예외를 `toOpenFailure` 로 옮기고 패키지를 닫는다.
        assertFailsWith<org.xmlpull.v1.XmlPullParserException> { Books.open(t) }
    }

    @Test
    fun 통합_문서_부분이_없으면_부분도_없다() {
        val t = TinyOoxml().xml("xl/other.xml", null, "<x/>")
        Books.open(t).use { doc -> assertTrue(doc.parts.isEmpty()) }
    }

    @Test
    fun 깨진_시트는_그_부분만_실패한다() {
        val broken = """<worksheet xmlns="${Books.NS_S}"><sheetData><row r="1"><c r="A1"><v>1</v></c></row><<<</sheetData></worksheet>"""
        val t = book(listOf(Books.Sheet("good", sheet(rows(2))), Books.Sheet("bad", broken)))
        Books.open(t).use { doc ->
            val bad = doc.partHtml(1)
            assertNotNull(bad)
            assertTrue("part-failed" in bad)
            assertTrue(doc.warnings.any { it.code == FlowWarnings.PART_FAILED && it.detail == "bad" })
            assertEquals("2", Grid.parse(doc.partHtml(0)!!).cells["A2"]!!.text)
        }
    }

    // ---- 병합 ----

    @Test
    fun 병합은_sheetData_뒤에_있어도_colspan_rowspan_이_되고_덮인_칸은_빠진다() {
        val xml = sheet(
            """<row r="1">${inline("A1", "M")}${inline("B1", "covered-1")}<c r="C1"><v>3</v></c></row>""" +
                """<row r="2"><c r="A2"><v>444</v></c><c r="B2"><v>555</v></c><c r="C2"><v>6</v></c></row>""" +
                """<row r="3"><c r="A3"><v>7</v></c></row>""",
            after = """<mergeCells count="1"><mergeCell ref="A1:B2"/></mergeCells>""",
        )
        one(xml).use { doc ->
            val html = doc.partHtml(0)!!
            val g = Grid.parse(html)
            val m = g.cells["A1"]!!
            assertEquals("M", m.text)
            assertEquals(2, m.colspan)
            assertEquals(2, m.rowspan)
            assertEquals("3", g.cells["C1"]!!.text)
            assertEquals("6", g.cells["C2"]!!.text)
            assertNull(g.cells["B1"])
            assertNull(g.cells["A2"])
            assertFalse("covered-1" in html)
            assertFalse("444" in html)
            assertFalse("555" in html)
            assertEquals("7", g.cells["A3"]!!.text)
        }
    }

    @Test
    fun 겹친_병합은_먼저_온_것만_받는다() {
        val xml = sheet(
            (1..4).joinToString("") { r -> """<row r="$r">${(1..4).joinToString("") { c -> inline(CellRefs.columnName(c) + r, "v$c$r") }}</row>""" },
            after = """<mergeCells><mergeCell ref="A1:B2"/><mergeCell ref="B2:C3"/><mergeCell ref="D1:D4"/><mergeCell ref="A4:C4"/></mergeCells>""",
        )
        // Grid.parse 가 겹침과 모자란 칸을 잡는다.
        val g = grid(xml)
        assertEquals(2, g.cells["A1"]!!.colspan)
        assertEquals(1, g.cells["C2"]?.colspan ?: 1)
        assertEquals(4, g.cells["D1"]!!.rowspan)
        assertEquals(3, g.cells["A4"]!!.colspan)

        // 대표 칸(A2)은 비어 있는데 범위가 앞 병합(B1:B3)의 몸을 지나는 경우 — 받으면 표가 겹친다.
        val cross = sheet(
            (1..3).joinToString("") { r -> """<row r="$r">${(1..3).joinToString("") { c -> inline(CellRefs.columnName(c) + r, "v$c$r") }}</row>""" },
            after = """<mergeCells><mergeCell ref="B1:B3"/><mergeCell ref="A2:C2"/></mergeCells>""",
        )
        val g2 = grid(cross)
        assertEquals(3, g2.cells["B1"]!!.rowspan)
        assertEquals(1, g2.cells["A2"]!!.colspan)
        assertEquals("v32", g2.cells["C2"]!!.text)
    }

    @Test
    fun 숨긴_행과_열은_그리지_않고_병합은_보이는_칸만_센다() {
        val xml = sheet(
            """<row r="1">${inline("A1", "wide")}${inline("B1", "x")}${inline("C1", "y")}${inline("D1", "d")}</row>""" +
                """<row r="2" hidden="1">${inline("A2", "secret-row")}</row>""" +
                """<row r="3">${inline("A3", "a3")}${inline("B3", "secret-col")}</row>""",
            before = """<cols><col min="2" max="2" hidden="1"/></cols>""",
            after = """<mergeCells><mergeCell ref="A1:C1"/></mergeCells>""",
        )
        one(xml).use { doc ->
            val html = doc.partHtml(0)!!
            val g = Grid.parse(html)
            assertEquals(listOf("A", "C", "D"), g.columns)
            assertEquals(listOf("1", "3"), g.rowHeads)
            assertEquals(2, g.cells["A1"]!!.colspan)
            assertFalse("secret-row" in html)
            assertFalse("secret-col" in html)
        }
    }

    // ---- 희소한 시트와 상한 ----

    @Test
    fun 긴_빈_구간은_한_줄로_접는다() {
        val xml = sheet("""<row r="1"><c r="A1"><v>1</v></c></row><row r="2"><c r="B2"><v>2</v></c></row><row r="1000000"><c r="A1000000"><v>9</v></c></row>""")
        val g = grid(xml)
        assertEquals(listOf("1", "2", "", "1000000"), g.rowHeads)
        assertEquals("9", g.cells["A1000000"]!!.text)
        one(xml).use { assertTrue("class=\"gap\"" in it.partHtml(0)!!) }
    }

    @Test
    fun 짧은_빈_구간은_그대로_그린다() {
        val g = grid("""${sheet("""<row r="3"><c r="A3"><v>3</v></c></row><row r="40"><c r="A40"><v>40</v></c></row>""")}""")
        assertEquals((1..40).map { it.toString() }, g.rowHeads)
    }

    @Test
    fun 행_상한에서_멈추고_시트_이름으로_알린다() {
        one(sheet(rows(6000))).use { doc ->
            val g = Grid.parse(doc.partHtml(0)!!)
            assertEquals(XlsxLimits.MAX_RENDERED_ROWS, g.rowHeads.size)
            assertTrue(doc.warnings.any { it.code == FlowWarnings.TRUNCATED && it.detail == "Sheet1" })
        }
    }

    @Test
    fun 상한에_닿지_않으면_알리지_않는다() {
        one(sheet(rows(100))).use { doc ->
            assertNotNull(doc.partHtml(0))
            assertFalse(doc.warnings.any { it.code == FlowWarnings.TRUNCATED })
        }
    }

    @Test
    fun 열_상한() {
        val far = CellRefs.columnName(300)
        one(sheet("""<row r="1"><c r="A1"><v>1</v></c><c r="${far}1"><v>2</v></c></row>""")).use { doc ->
            val html = doc.partHtml(0)!!
            val g = Grid.parse(html)
            assertEquals(XlsxLimits.MAX_RENDERED_COLUMNS, g.columns.size)
            assertEquals("IV", g.columns.last())
            assertTrue(doc.warnings.any { it.code == FlowWarnings.TRUNCATED })
        }
    }

    @Test
    fun 칸_상한() {
        val xml = sheet((1..1000).joinToString("") { """<row r="$it"><c r="A$it"><v>$it</v></c><c r="IV$it"><v>$it</v></c></row>""" })
        one(xml).use { doc ->
            val html = doc.partHtml(0)!!
            val tds = Regex("<td").findAll(html).count()
            assertTrue(tds <= XlsxLimits.MAX_RENDERED_CELLS, "칸 $tds 개")
            assertTrue(tds > XlsxLimits.MAX_RENDERED_CELLS - 300)
            assertTrue(doc.warnings.any { it.code == FlowWarnings.TRUNCATED })
        }
    }

    @Test
    fun 순서가_뒤집힌_행에_무너지지_않는다() {
        val g = grid(sheet("""<row r="5">${inline("A5", "five")}</row><row r="3">${inline("A3", "three")}</row><row r="7">${inline("A7", "seven")}</row>"""))
        assertEquals("five", g.cells["A5"]!!.text)
        assertEquals("seven", g.cells["A7"]!!.text)
    }

    // ---- 모양 ----

    @Test
    fun 테마_색과_명도_조정을_칠한다() {
        val styles = """<styleSheet xmlns="${Books.NS_S}">
            <fonts count="2"><font><sz val="11"/><color theme="1"/><name val="Calibri"/></font>
              <font><b/><sz val="14"/><color theme="4" tint="0.3999755851924192"/><name val="Calibri"/></font></fonts>
            <fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill>
              <fill><patternFill patternType="solid"><fgColor theme="0" tint="-0.1499984740745262"/><bgColor indexed="64"/></patternFill></fill></fills>
            <borders count="2"><border/><border><left style="thin"><color indexed="10"/></left><bottom style="medium"/></border></borders>
            <cellXfs count="4"><xf fontId="0" fillId="0" borderId="0"/><xf fontId="1"/><xf fillId="2" borderId="1"/><xf fillId="1"/></cellXfs>
            </styleSheet>"""
        val g = grid(sheet("""<row r="1">${inline("A1", "plain")}<c r="B1" s="1" t="inlineStr"><is><t>big</t></is></c><c r="C1" s="2"/><c r="D1" s="3"><v>1</v></c></row>"""), styles = styles)
        assertEquals("", g.cells["A1"]!!.style)
        val b = g.cells["B1"]!!.style
        assertTrue("color:#95b3d7" in b, b)
        assertTrue("font-weight:bold" in b, b)
        assertTrue("font-size:14pt" in b, b)
        val c = g.cells["C1"]!!.style
        assertTrue("background-color:#d9d9d9" in c, c)
        assertTrue("border-left:1px solid #ff0000" in c, c)
        assertTrue("border-bottom:2px solid #000000" in c, c)
        // 무늬 채우기(gray125)는 칠하지 않는다.
        assertFalse("background" in g.cells["D1"]!!.style)
    }

    @Test
    fun 조건부_서식의_글꼴이_글꼴_번호를_밀지_않는다() {
        val styles = """<styleSheet xmlns="${Books.NS_S}">
            <dxfs><dxf><font><b/></font><fill><patternFill patternType="solid"><fgColor rgb="FFFF0000"/></patternFill></fill></dxf></dxfs>
            <fonts><font/><font><i/></font></fonts>
            <cellXfs><xf/><xf fontId="1"/></cellXfs></styleSheet>"""
        val s = grid(sheet("""<row r="1"><c r="A1" s="1" t="inlineStr"><is><t>i</t></is></c></row>"""), styles = styles).cells["A1"]!!.style
        assertTrue("font-style:italic" in s, s)
        assertFalse("bold" in s, s)
        assertFalse("background" in s, s)
    }

    @Test
    fun 열_너비_행_높이_맞춤_줄바꿈() {
        val styles = """<styleSheet xmlns="${Books.NS_S}"><cellXfs><xf/><xf><alignment horizontal="center" vertical="top" wrapText="1"/></xf></cellXfs></styleSheet>"""
        one(
            sheet(
                """<row r="1" ht="30" customHeight="1"><c r="A1" s="1" t="inlineStr"><is><t>w</t></is></c></row>""",
                before = """<cols><col min="1" max="1" width="20" customWidth="1"/></cols>""",
            ),
            styles = styles,
        ).use { doc ->
            val html = doc.partHtml(0)!!
            // 너비 20 자 × 화면 숫자 폭(11pt × 0.56 em).
            assertTrue("width:123.2pt" in html, html)
            val g = Grid.parse(html)
            assertEquals("height:30pt", g.rowAttrs[0]["style"])
            val a = g.cells["A1"]!!
            assertTrue("w" in a.classes)
            assertTrue("text-align:center" in a.style)
            assertTrue("vertical-align:top" in a.style)
        }
    }

    @Test
    fun 일반_형식의_수는_칸_폭에_맞춰_줄인다() {
        val xml = sheet(
            """<row r="1"><c r="A1"><v>323832.4567</v></c><c r="B1"><v>323832.4567</v></c><c r="C1"><v>123456789</v></c><c r="D1"><v>323832.4567</v></c></row>""",
            before = """<cols><col min="2" max="2" width="20" customWidth="1"/></cols>""",
            after = """<mergeCells><mergeCell ref="D1:E1"/></mergeCells>""",
        )
        val g = grid(xml)
        // 기본 폭(8.43 자)에는 여덟 자 — Excel 과 같다.
        assertEquals("323832.5", g.cells["A1"]!!.text)
        assertEquals("323832.4567", g.cells["B1"]!!.text)
        assertEquals("1.23E+08", g.cells["C1"]!!.text)
        // 병합은 덮는 열을 합친 폭이다.
        assertEquals("323832.4567", g.cells["D1"]!!.text)
    }

    @Test
    fun 오른쪽이_빈_글자만_넘쳐_보인다() {
        val g = grid(
            sheet(
                """<row r="1">${inline("A1", "a long title")}</row><row r="2">${inline("A2", "clipped")}<c r="B2"><v>1</v></c></row>""" +
                    """<row r="3">${inline("A3", "stops before D")}${inline("D3", "D")}</row>""",
            ),
        )
        assertTrue("ov" in g.cells["A1"]!!.classes)
        assertFalse("ov" in g.cells["A2"]!!.classes)
        // 넘치는 글자는 **다음 글자 칸 앞에서** 잘린다 — 기본 열 56.32pt 셋(A·B·C)에서 칸 여백 4.5pt 를 뺀 폭.
        // 끝을 주지 않으면 두 칸 너머의 글자(D3) 위에 겹쳐 그려지던 결함의 회귀 시험이다.
        val a3 = g.cells["A3"]!!
        assertTrue("ov" in a3.classes)
        assertTrue("""<div style="width:164.46pt">""" in a3.inner, a3.inner)
        // 오른쪽 끝까지 비었으면 표의 오른쪽 선(D 열의 끝)에서 잘린다.
        assertTrue("""<div style="width:220.78pt">""" in g.cells["A1"]!!.inner, g.cells["A1"]!!.inner)
    }

    @Test
    fun 격자선을_끈_시트() {
        one(sheet(rows(1), before = """<sheetViews><sheetView showGridLines="0" workbookViewId="0"/></sheetViews>""")).use {
            assertTrue("sheet nogrid" in it.partHtml(0)!!)
        }
    }

    @Test
    fun 다른_시트로_가는_연결만_링크가_된다() {
        val s1 = sheet(rows(1))
        val s2 = sheet(
            """<row r="1">${inline("A1", "back")}${inline("B1", "self")}${inline("C1", "nowhere")}</row>""",
            after = """<hyperlinks><hyperlink ref="A1" location="'Sheet One'!A1"/><hyperlink ref="B1" location="Two!B2"/><hyperlink ref="C1" location="Missing!A1"/></hyperlinks>""",
        )
        Books.open(book(listOf(Books.Sheet("Sheet One", s1), Books.Sheet("Two", s2)))).use { doc ->
            val html = doc.partHtml(1)!!
            assertTrue("href=\"~part-0.html\"" in html, html)
            assertEquals(1, Regex("<a ").findAll(html).count())
        }
    }

    // ---- 그림판·메모 ----

    private fun drawingBook(): TinyOoxml {
        val anchor = """<xdr:from><xdr:col>0</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>0</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:from>"""
        val drawing = """<xdr:wsDr xmlns:xdr="${Books.NS_XDR}" xmlns:a="${TinyOoxml.NS_A}" xmlns:r="${Books.NS_R}" xmlns:mc="${Books.NS_MC}">
            <xdr:twoCellAnchor>$anchor<xdr:to/>
              <xdr:pic><xdr:nvPicPr><xdr:cNvPr id="2" name="P" descr="고양이"/><xdr:cNvPicPr/></xdr:nvPicPr>
                <xdr:blipFill><a:blip r:embed="rId1"/></xdr:blipFill>
                <xdr:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="1270000" cy="635000"/></a:xfrm></xdr:spPr></xdr:pic><xdr:clientData/></xdr:twoCellAnchor>
            <xdr:oneCellAnchor>$anchor<xdr:ext cx="100" cy="100"/>
              <xdr:pic><xdr:nvPicPr><xdr:cNvPr id="3" name="E"/></xdr:nvPicPr><xdr:blipFill><a:blip r:embed="rId2"/></xdr:blipFill></xdr:pic><xdr:clientData/></xdr:oneCellAnchor>
            <xdr:twoCellAnchor>$anchor<xdr:to/>
              <xdr:graphicFrame><xdr:nvGraphicFramePr><xdr:cNvPr id="4" name="C"/></xdr:nvGraphicFramePr>
                <a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/chart"><c:chart xmlns:c="http://schemas.openxmlformats.org/drawingml/2006/chart" r:id="rId3"/></a:graphicData></a:graphic></xdr:graphicFrame><xdr:clientData/></xdr:twoCellAnchor>
            <xdr:twoCellAnchor>$anchor<xdr:to/>
              <xdr:sp><xdr:nvSpPr><xdr:cNvPr id="5" name="S"/><xdr:cNvSpPr/></xdr:nvSpPr><xdr:spPr/>
                <xdr:txBody><a:bodyPr/><a:p><a:r><a:t>첫 줄</a:t></a:r></a:p><a:p><a:r><a:t>둘째 &lt;b&gt;</a:t></a:r></a:p></xdr:txBody></xdr:sp><xdr:clientData/></xdr:twoCellAnchor>
            <xdr:absoluteAnchor><xdr:pos x="0" y="0"/><xdr:ext cx="1" cy="1"/>
              <xdr:sp><xdr:nvSpPr><xdr:cNvPr id="6" name="T"/><xdr:cNvSpPr txBox="1"/></xdr:nvSpPr><xdr:txBody><a:p><a:r><a:t>글상자</a:t></a:r></a:p></xdr:txBody></xdr:sp><xdr:clientData/></xdr:absoluteAnchor>
            <mc:AlternateContent><mc:Choice Requires="a14"><xdr:twoCellAnchor>$anchor<xdr:to/>
              <xdr:pic><xdr:nvPicPr><xdr:cNvPr id="7" name="H" hidden="1"/></xdr:nvPicPr><xdr:blipFill><a:blip r:embed="rId1"/></xdr:blipFill></xdr:pic><xdr:clientData/></xdr:twoCellAnchor></mc:Choice>
              <mc:Fallback><xdr:twoCellAnchor>$anchor<xdr:to/><xdr:sp><xdr:nvSpPr><xdr:cNvPr id="8" name="F"/><xdr:cNvSpPr txBox="1"/></xdr:nvSpPr><xdr:txBody><a:p><a:r><a:t>fallback-text</a:t></a:r></a:p></xdr:txBody></xdr:sp><xdr:clientData/></xdr:twoCellAnchor></mc:Fallback>
            </mc:AlternateContent>
            </xdr:wsDr>"""
        val t = book(listOf(Books.Sheet("그림", sheet(rows(1)))))
        t.xml("xl/drawings/drawing1.xml", Books.CT_DRAWING, drawing)
        t.rel(Books.sheetPart(0), "rIdD", Books.REL_DRAWING, "../drawings/drawing1.xml")
        t.bytes("xl/media/image1.png", null, byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
        t.bytes("xl/media/image2.emf", null, byteArrayOf(1, 0, 0, 0))
        t.rel("xl/drawings/drawing1.xml", "rId1", TinyOoxml.REL_IMAGE, "../media/image1.png")
        t.rel("xl/drawings/drawing1.xml", "rId2", TinyOoxml.REL_IMAGE, "../media/image2.emf")
        t.rel("xl/drawings/drawing1.xml", "rId3", TinyOoxml.REL_CHART, "../charts/chart1.xml")
        t.xml("xl/comments1.xml", null, """<comments xmlns="${Books.NS_S}"><authors><author>a</author></authors><commentList><comment ref="A1" authorId="0"><text><t>x</t></text></comment><comment ref="B1" authorId="0"><text><t>y</t></text></comment></commentList></comments>""")
        t.rel(Books.sheetPart(0), "rIdC", Books.REL_COMMENTS, "../comments1.xml")
        return t
    }

    @Test
    fun 그림은_표_아래에_차트와_도형은_센다() {
        Books.open(drawingBook()).use { doc ->
            val html = doc.partHtml(0)!!
            val pics = html.substringAfter("<div class=\"pics\">", "")
            assertTrue(pics.isNotEmpty(), html)
            assertTrue(html.indexOf("</table>") < html.indexOf("class=\"pics\""))
            assertTrue("src=\"xl/media/image1.png\"" in pics, pics)
            assertTrue("alt=\"고양이\"" in pics)
            assertTrue("width:100pt" in pics)
            assertFalse("image2.emf" in html)
            assertTrue("첫 줄\n둘째 &lt;b&gt;" in pics, pics)
            assertTrue("글상자" in pics)
            // 숨긴 그림과, Choice 를 읽었으므로 Fallback 은 없다.
            assertEquals(1, Regex("<img").findAll(pics).count())
            assertFalse("fallback-text" in html)
            val u = doc.unsupported.snapshot()
            assertEquals(1, u[UnsupportedFeatures.UNSUPPORTED_IMAGE])
            assertEquals(1, u[UnsupportedFeatures.CHART])
            assertEquals(1, u[UnsupportedFeatures.SHAPE])
            assertEquals(2, u[UnsupportedFeatures.COMMENT])
            // 바탕이 그 그림을 실제로 내준다.
            assertNotNull(doc.openResource("xl/media/image1.png"))
        }
    }

    @Test
    fun 버린_것은_부분마다_한_번만_센다() {
        val comment = """<comments xmlns="${Books.NS_S}"><commentList><comment ref="A1"/></commentList></comments>"""
        val t = book((0 until 5).map { Books.Sheet("s$it", sheet(rows(1))) })
        for (i in 0 until 5) {
            t.xml("xl/comments$i.xml", null, comment)
            t.rel(Books.sheetPart(i), "rIdC", Books.REL_COMMENTS, "../comments$i.xml")
        }
        Books.open(t).use { doc ->
            for (i in 0 until 5) assertNotNull(doc.partHtml(i))
            // 캐시는 셋뿐이라 0 은 다시 그려진다.
            assertNotNull(doc.partHtml(0))
            assertNotNull(doc.partHtml(1))
            assertEquals(5, doc.unsupported.snapshot()[UnsupportedFeatures.COMMENT])
        }
    }

    @Test
    fun 같은_그림판과_메모를_여러_번_거는_관계는_한_번만_따른다() {
        // 관계 파일은 공격자가 적는다. 같은 그림판을 다섯 번 걸면 그림이 다섯 벌 그려지고 메모가 다섯 배로
        // 세이던 결함의 회귀 시험. 대상 이름의 대소문자만 다른 것도 같은 부분이다.
        val drawing = """<xdr:wsDr xmlns:xdr="${Books.NS_XDR}" xmlns:a="${TinyOoxml.NS_A}" xmlns:r="${Books.NS_R}">
            <xdr:oneCellAnchor><xdr:ext cx="100" cy="100"/>
              <xdr:pic><xdr:nvPicPr><xdr:cNvPr id="3" name="E"/></xdr:nvPicPr><xdr:blipFill><a:blip r:embed="rId1"/></xdr:blipFill></xdr:pic><xdr:clientData/></xdr:oneCellAnchor>
            </xdr:wsDr>"""
        val t = book(listOf(Books.Sheet("s", sheet(rows(1)))))
        t.xml("xl/drawings/drawing1.xml", Books.CT_DRAWING, drawing)
        t.bytes("xl/media/image1.png", null, byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
        t.rel("xl/drawings/drawing1.xml", "rId1", TinyOoxml.REL_IMAGE, "../media/image1.png")
        t.xml("xl/comments1.xml", null, """<comments xmlns="${Books.NS_S}"><commentList><comment ref="A1"/></commentList></comments>""")
        for (i in 0 until 5) {
            t.rel(Books.sheetPart(0), "rIdD$i", Books.REL_DRAWING, if (i == 4) "../DRAWINGS/Drawing1.xml" else "../drawings/drawing1.xml")
            t.rel(Books.sheetPart(0), "rIdC$i", Books.REL_COMMENTS, "../comments1.xml")
        }
        Books.open(t).use { doc ->
            val html = doc.partHtml(0)!!
            assertEquals(1, Regex("<img").findAll(html).count(), html)
            assertEquals(1, doc.unsupported.snapshot()[UnsupportedFeatures.COMMENT])
        }
    }

    @Test
    fun 그림의_대체_글자는_길이를_자른다() {
        // `descr` 은 길이 상한이 없는 속성이고 `"` 는 이스케이프하면 여섯 자(`&quot;`)가 된다. 본문이
        // 위생기의 입력 상한(maxXmlBytes)을 넘으면 바탕의 `FlowHtml.finish` 가 **잡히지 않는 자리에서**
        // 던진다 — 작은 상한으로 같은 길을 태운다.
        val limits = ParseLimits(maxXmlBytes = 256 * 1024)
        val quotes = "\"".repeat(60_000)
        val drawing = """<xdr:wsDr xmlns:xdr="${Books.NS_XDR}" xmlns:a="${TinyOoxml.NS_A}" xmlns:r="${Books.NS_R}">
            <xdr:oneCellAnchor><xdr:ext cx="100" cy="100"/>
              <xdr:pic><xdr:nvPicPr><xdr:cNvPr id="3" name="E" descr='$quotes'/></xdr:nvPicPr><xdr:blipFill><a:blip r:embed="rId1"/></xdr:blipFill></xdr:pic><xdr:clientData/></xdr:oneCellAnchor>
            </xdr:wsDr>"""
        val t = book(listOf(Books.Sheet("s", sheet(rows(1)))))
        t.xml("xl/drawings/drawing1.xml", Books.CT_DRAWING, drawing)
        t.rel(Books.sheetPart(0), "rIdD", Books.REL_DRAWING, "../drawings/drawing1.xml")
        t.bytes("xl/media/image1.png", null, byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
        t.rel("xl/drawings/drawing1.xml", "rId1", TinyOoxml.REL_IMAGE, "../media/image1.png")
        Books.open(t, limits).use { doc ->
            val html = doc.partHtml(0)!!
            assertTrue("<img" in html)
            assertFalse(doc.warnings.any { it.code == FlowWarnings.PART_FAILED })
            val alt = Regex("""alt="([^"]*)"""").find(html)!!.groupValues[1]
            assertTrue(alt.length <= XlsxLimits.MAX_ALT_CHARS * 6, "대체 글자 ${alt.length}자")
        }
    }

    @Test
    fun 병합의_오른쪽_선은_오른쪽_끝_칸에서_읽는다() {
        // Excel 의 '바깥쪽 테두리' — A1 에 왼·위·아래, B1 에 위·아래, C1 에 오른·위·아래.
        val styles = """<styleSheet xmlns="${Books.NS_S}">
            <borders count="5"><border/>
              <border><left style="thin"/><top style="thin"/><bottom style="thin"/></border>
              <border><top style="thin"/><bottom style="thin"/></border>
              <border><right style="medium"><color rgb="FFFF0000"/></right><top style="thin"/><bottom style="thin"/></border>
              <border><left style="thin"/><right style="thin"/><top style="thin"/><bottom style="thin"/></border>
            </borders>
            <cellXfs><xf/><xf borderId="1"/><xf borderId="2"/><xf borderId="3"/><xf borderId="4"/></cellXfs></styleSheet>"""
        val xml = sheet(
            """<row r="1"><c r="A1" s="1" t="inlineStr"><is><t>title</t></is></c><c r="B1" s="2"/><c r="C1" s="3"/></row>""" +
                // 모든 칸에 같은 서식을 적는 도구(XlsxWriter) — 오른쪽 끝 칸에 선이 없으면 대표 칸의 것을 둔다.
                """<row r="2"><c r="A2" s="4" t="inlineStr"><is><t>all</t></is></c><c r="B2"/></row>""",
            after = """<mergeCells><mergeCell ref="A1:C1"/><mergeCell ref="A2:B2"/></mergeCells>""",
        )
        val g = grid(xml, styles = styles)
        val a1 = g.cells["A1"]!!.style
        assertTrue("border-left:1px solid #000000" in a1, a1)
        assertTrue(a1.endsWith("border-right:2px solid #ff0000"), a1)
        assertTrue("border-right:1px solid #000000" in g.cells["A2"]!!.style, g.cells["A2"]!!.style)
    }

    @Test
    fun 표에_없는_서식_번호는_캐시를_키우지_않는다() {
        val styles = """<styleSheet xmlns="${Books.NS_S}"><cellXfs><xf/><xf/></cellXfs></styleSheet>"""
        val s = XlsxStyles.read(
            io.github.donggi.iroiroviewer.safety.SafeXml.newParser(styles.byteInputStream(), null, ParseLimits.DEFAULT),
            ParseLimits.DEFAULT,
            XlsxColors.DEFAULT_THEME,
        )
        for (i in 0 until 10_000) {
            assertTrue(s.cellCss(1_000 + i).isEmpty())
            assertTrue(s.emptyCellCss(-1 - i).isEmpty())
        }
        s.cellCss(1)
        s.emptyCellCss(1)
        assertEquals(2, s.cachedCssCount)
    }

    // ---- 여는 과정 ----

    @Test
    fun 여는_동안_진행률을_알리고_취소를_본다() {
        val reports = ArrayList<Pair<Long, Long>>()
        Books.open(book(listOf(Books.Sheet("a", sheet(rows(1))))), progress = ProgressSink { d, t -> reports.add(d to t) }).use {
            assertEquals(listOf(2L to 3L), reports)
        }
        val pkg = OpcPackage.open(book(listOf(Books.Sheet("a", sheet(rows(1))))).source("t.xlsx"), ParseLimits.DEFAULT)
        val doc = XlsxFlowDocument(pkg, ParseLimits.DEFAULT)
        doc.use {
            assertFailsWith<CancellationException> { it.load(Job().apply { cancel() }) }
        }
    }

    @Test
    fun 공유_문자열_상한은_들어가는_데까지만_든다() {
        val sst = """<sst xmlns="${Books.NS_S}">""" + (0 until 10).joinToString("") { "<si><t>s$it</t></si>" } + "</sst>"
        fun read(maxItems: Int, maxChars: Long) = SharedStrings.read(
            io.github.donggi.iroiroviewer.safety.SafeXml.newParser(sst.byteInputStream(), null, ParseLimits.DEFAULT),
            ParseLimits.DEFAULT,
            maxItems,
            maxChars,
        ) {}
        val all = read(100, 1000)
        assertEquals(10, all.size)
        assertFalse(all.truncated)
        val byCount = read(3, 1000)
        assertEquals(3, byCount.size)
        assertTrue(byCount.truncated)
        assertEquals("s2", byCount[2])
        assertEquals("", byCount[3])
        // 글자 수: s0..s4 가 10자. 여섯째(s5)가 들어가면 12자라 넘는다.
        val byChars = read(100, 11)
        assertEquals(5, byChars.size)
        assertTrue(byChars.truncated)
    }

    @Test
    fun 깨진_서식_표와_공유_문자열은_보조_부분의_실패로_알린다() {
        // 값은 보이지만 서식·글자가 빠졌다는 것을 말해야 한다 — 조용히 기본값으로 그리면 사용자는
        // 문서가 원래 그렇게 생긴 줄 안다. 상한에서 자른 것(TRUNCATED)과는 다른 말이다.
        val t = book(
            listOf(Books.Sheet("a", sheet("""<row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1"><v>3</v></c></row>"""))),
            sst = """<sst xmlns="${Books.NS_S}"><si><t>a</t></si><si><t""",
            styles = """<styleSheet xmlns="${Books.NS_S}"><cellXfs count="1"><xf""",
        )
        Books.open(t).use { doc ->
            val g = Grid.parse(doc.partHtml(0)!!)
            assertEquals("3", g.cells["B1"]!!.text)
            val aux = doc.warnings.filter { it.code == FlowWarnings.AUX_FAILED }.map { it.detail }
            assertTrue("xl/styles.xml" in aux, doc.warnings.toString())
            // 공유 문자열은 깨진 자리까지 살리거나(TRUNCATED) 통째로 잃는다(AUX_FAILED). 어느 쪽이든 말한다.
            assertTrue(
                "xl/sharedStrings.xml" in aux || doc.warnings.any { it.code == FlowWarnings.TRUNCATED },
                doc.warnings.toString(),
            )
        }
    }

    @Test
    fun 엑셀4_매크로_시트는_매크로로_센다() {
        val t = book(
            listOf(
                Books.Sheet("a", sheet("""<row r="1"><c r="A1"><v>1</v></c></row>""")),
                Books.Sheet("m", sheet(""), type = "${TinyOoxml.R}/xlMacrosheet"),
            ),
        )
        Books.open(t).use { doc ->
            assertEquals(1, doc.parts.size)
            assertEquals(1, doc.unsupported.snapshot()[UnsupportedFeatures.MACRO])
        }
    }

    @Test
    fun 매크로가_든_통합_문서의_매크로_시트는_두_번_세지_않는다() {
        val t = book(
            listOf(
                Books.Sheet("a", sheet("""<row r="1"><c r="A1"><v>1</v></c></row>""")),
                Books.Sheet("m", sheet(""), type = "${TinyOoxml.R}/xlMacrosheet"),
            ),
        ).bytes("xl/vbaProject.bin", null, byteArrayOf(0))
        Books.open(t).use { doc ->
            assertEquals(1, doc.unsupported.snapshot()[UnsupportedFeatures.MACRO])
        }
    }

    @Test
    fun 숫자뿐인_시트_이름은_번호가_아니라_이름으로_알린다() {
        // '2024' 라는 시트가 잘리면 알림은 '2024번째 부분' 이 아니라 '2024' 여야 한다 — 번호는 표지로 가른다.
        val t = book(listOf(Books.Sheet("2024", sheet(rows(XlsxLimits.MAX_RENDERED_ROWS + 10)))))
        Books.open(t).use { doc ->
            assertNotNull(doc.partHtml(0))
            val w = doc.warnings.single { it.code == FlowWarnings.TRUNCATED }
            assertEquals("2024", w.detail)
            assertFalse(w.detail.startsWith(FlowWarnings.PART_NUMBER_PREFIX))
        }
    }

    @Test
    fun 시트_이름은_보이는_모양으로_다듬는다() {
        // 방향 바꾸기 문자는 알림 문장의 뒤를 거꾸로 보이게 할 수 있다. 제어 문자·겹친 공백·긴 이름도 다듬는다.
        assertEquals("매출 요약", SheetNames.display("‮매출\t\t요약\u0007 "))
        assertEquals(SheetNames.MAX_CHARS, SheetNames.display("가".repeat(500)).length)
        // 자른 자리가 대리 쌍 가운데면 반쪽을 남기지 않는다.
        val emoji = "a".repeat(SheetNames.MAX_CHARS - 1) + "😀"
        assertFalse(SheetNames.display(emoji).last().isHighSurrogate())
        val t = book(listOf(Books.Sheet("‮위험‬ 시트", sheet("""<row r="1"><c r="A1"><v>1</v></c></row>"""))))
        Books.open(t).use { doc ->
            assertEquals("위험 시트", doc.parts[0].label)
            assertEquals("위험 시트", doc.outline[0].title)
        }
    }

    @Test
    fun 칸_상한이_행_경계에_걸려도_칸_없는_행_머리를_남기지_않는다() {
        // 250열 × 200행 = 5만 칸에서 정확히 멈춘다. 예전에는 201행의 머리(<th>)만 열린 채 남았다.
        val cols = 250
        val last = CellRefs.columnName(cols)
        val xml = sheet((1..300).joinToString("") { """<row r="$it"><c r="A$it"><v>$it</v></c><c r="$last$it"><v>$it</v></c></row>""" })
        one(xml).use { doc ->
            val g = Grid.parse(doc.partHtml(0)!!)
            assertEquals(XlsxLimits.MAX_RENDERED_CELLS / cols, g.rowHeads.size)
            assertTrue(doc.warnings.any { it.code == FlowWarnings.TRUNCATED })
        }
    }

    /**
     * 칸 상한에서 멈췄어도 **뒤에 값이 없으면** '줄였다' 가 아니다 — 행·열 상한과 같은 기준이다. 실세계 표본(POI 의
     * `57181.xlsm`)의 General 시트는 값이 131행에서 끝나고 테두리만 그은 빈 칸이 264행까지 있어, 199행의 칸
     * 상한에서 잃은 것 없이 '줄였다' 를 냈다. 값이 상한 뒤에 하나라도 있으면(같은 줄의 오른쪽이든 아래 줄이든)
     * 여전히 알린다.
     */
    @Test
    fun 칸_상한_뒤에_빈_칸만_남았으면_줄였다고_알리지_않는다() {
        val cols = 250
        val last = CellRefs.columnName(cols)
        // 1~100행에는 값, 101~300행에는 서식만 입힌 빈 칸(`s` 만 있는 `c`). 5만 칸은 200행에서 찬다.
        fun styledRows(tail: String) = (1..300).joinToString("") { r ->
            val a = if (r <= 100) """<c r="A$r"><v>$r</v></c>""" else """<c r="A$r" s="0"/>"""
            """<row r="$r">$a<c r="$last$r" s="0"/></row>"""
        } + tail
        one(sheet(styledRows(""))).use { doc ->
            val g = Grid.parse(doc.partHtml(0)!!)
            assertEquals(XlsxLimits.MAX_RENDERED_CELLS / cols, g.rowHeads.size)
            assertEquals("100", g.cells["A100"]?.text)
            assertFalse(doc.warnings.any { it.code == FlowWarnings.TRUNCATED }, doc.warnings.toString())
        }
        // 상한 뒤(250행)에 값 하나.
        one(sheet(styledRows("").replace("""<c r="A250" s="0"/>""", """<c r="A250"><v>250</v></c>"""))).use { doc ->
            assertNotNull(doc.partHtml(0))
            assertTrue(doc.warnings.any { it.code == FlowWarnings.TRUNCATED })
        }
        // 멈춘 줄의 오른쪽에 값 하나 — 256열 × 195행 = 49,920칸이라 196행의 81번째 칸에서 멈춘다.
        val wide = CellRefs.columnName(256)
        val rowCut = (1..196).joinToString("") { r ->
            val tailCell = if (r == 196) """<c r="$wide$r"><v>9</v></c>""" else """<c r="$wide$r" s="0"/>"""
            """<row r="$r"><c r="A$r"><v>$r</v></c>$tailCell</row>"""
        }
        one(sheet(rowCut)).use { doc ->
            // 마지막 줄은 중간에서 끊긴다(칸 상한) — 그래서 `Grid.parse` 로 읽지 않는다.
            val html = doc.partHtml(0)!!
            assertTrue("<th>196</th>" in html && "<th>195</th>" in html, "196행까지 그려야 한다")
            assertTrue(doc.warnings.any { it.code == FlowWarnings.TRUNCATED })
        }
    }

    @Test
    fun 공유_문자열이_입력_상한에서_잘리면_빈_칸과_경고() {
        // XML 입력 상한을 작게 걸어 32 MiB 짜리 표와 같은 길을 태운다. 파서가 입력을 8 KiB 쯤씩 미리
        // 읽으므로 상한은 그보다 넉넉히 크게, 표는 상한보다 넉넉히 크게 둔다.
        val limits = ParseLimits(maxXmlBytes = 64 * 1024)
        val sst = """<sst xmlns="${Books.NS_S}">""" + (0 until 10_000).joinToString("") { "<si><t>s$it</t></si>" } + "</sst>"
        val t = book(listOf(Books.Sheet("a", sheet("""<row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>9999</v></c></row>"""))), sst = sst)
        Books.open(t, limits).use { doc ->
            val g = Grid.parse(doc.partHtml(0)!!)
            assertEquals("s0", g.cells["A1"]!!.text)
            assertEquals("", g.cells["B1"]!!.text)
            assertTrue(doc.warnings.any { it.code == FlowWarnings.TRUNCATED && it.detail == "" })
        }
    }
}
