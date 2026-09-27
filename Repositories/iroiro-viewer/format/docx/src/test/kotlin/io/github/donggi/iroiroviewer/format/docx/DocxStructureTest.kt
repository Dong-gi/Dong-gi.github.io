package io.github.donggi.iroiroviewer.format.docx

import io.github.donggi.iroiroviewer.format.FlowKind
import io.github.donggi.iroiroviewer.format.FlowOutline
import io.github.donggi.iroiroviewer.format.FlowWarnings
import io.github.donggi.iroiroviewer.format.docx.Docx.abstractNum
import io.github.donggi.iroiroviewer.format.docx.Docx.li
import io.github.donggi.iroiroviewer.format.docx.Docx.lvl
import io.github.donggi.iroiroviewer.format.docx.Docx.num
import io.github.donggi.iroiroviewer.format.docx.Docx.p
import io.github.donggi.iroiroviewer.format.docx.Docx.paraStyle
import io.github.donggi.iroiroviewer.format.docx.Docx.styled
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 제목·목차, 목록 번호, 표, 조각 나누기 — 문서의 뼈대. */
class DocxStructureTest {

    private val headingStyles = Docx.styles(
        paraStyle("Normal", "Normal", default = true),
        paraStyle("Heading1", "heading 1", "Normal", "<w:outlineLvl w:val=\"0\"/>", "<w:b/><w:sz w:val=\"32\"/>"),
        paraStyle("Heading2", "heading 2", "Heading1", "<w:outlineLvl w:val=\"1\"/>"),
        // 개요 수준을 적지 않고 제목 2 를 잇는 사용자 스타일 — 수준을 물려받는다.
        paraStyle("MyHeading", "My Heading", "Heading2"),
        defaults = "<w:docDefaults><w:rPrDefault><w:rPr><w:sz w:val=\"22\"/></w:rPr></w:rPrDefault></w:docDefaults>",
    )

    @Test
    fun 제목이_목차와_자리가_된다() {
        val body = styled("Heading1", "서론") + p("본문") + styled("Heading2", "배경") +
            styled("MyHeading", "세부") + p("끝")
        Docx.open(Docx.docx(body, styles = headingStyles)).use { doc ->
            assertEquals(FlowKind.DOCUMENT, doc.kind)
            assertEquals(1, doc.parts.size)
            assertEquals(
                listOf(
                    FlowOutline("서론", 0, 0, "h-0"),
                    FlowOutline("배경", 1, 0, "h-2"),
                    FlowOutline("세부", 1, 0, "h-3"),
                ),
                doc.outline,
            )
            val html = doc.body()
            assertTrue("<h1 id=\"h-0\"" in html, html)
            assertTrue("<h2 id=\"h-2\"" in html, html)
            assertTrue("<h2 id=\"h-3\"" in html, html)
            // 제목의 크기는 문서 기본값(11pt) 대비 백분율 — 16pt 는 145.4545%.
            assertTrue("font-size:145.4545%" in html, html)
            assertEquals(listOf("서론", "본문", "배경", "세부", "끝"), paragraphs(html))
            assertEquals("서론", doc.parts[0].label)
        }
    }

    @Test
    fun 한국어_워드의_숫자_id_와_제목_아님_수준과_제목_스타일() {
        val styles = Docx.styles(
            paraStyle("a", "Normal", default = true),
            paraStyle("1", "heading 1", "a"),
            paraStyle("2", "heading 2", "a"),
            // `TOC Heading` 은 제목 1 을 잇되 개요 수준 9(본문)로 목차에서 빠진다.
            paraStyle("TOCHeading", "TOC Heading", "1", "<w:outlineLvl w:val=\"9\"/>"),
            paraStyle("Title", "Title", "a"),
        )
        val body = styled("Title", "문서 제목") + styled("TOCHeading", "목차") + styled("1", "가장") +
            styled("2", "그 아래") + p("직접 수준", "<w:outlineLvl w:val=\"2\"/>")
        Docx.open(Docx.docx(body, styles = styles)).use { doc ->
            assertEquals(listOf("문서 제목", "가장", "그 아래", "직접 수준"), doc.outline.map { it.title })
            assertEquals(listOf(0, 0, 1, 2), doc.outline.map { it.depth })
            val html = doc.body()
            assertTrue("<h1 id=\"h-0\" class=\"title\"" in html, html)
            assertTrue("<h3 id=\"h-4\"" in html, html)
            assertFalse("id=\"h-1\"" in html, "개요 수준 9 는 제목이 아니다: $html")
        }
    }

    @Test
    fun 스타일_표가_없어도_id_로_제목을_짐작하고_번호_표가_없어도_글은_나온다() {
        val body = styled("Heading1", "제목 하나") + li(1, 0, "번호 없는 목록")
        Docx.open(Docx.docx(body)).use { doc ->
            assertEquals(listOf("제목 하나"), doc.outline.map { it.title })
            val html = doc.body()
            assertTrue("<h1 id=\"h-0\"" in html, html)
            assertTrue("번호 없는 목록" in html)
            assertTrue(markers(html).isEmpty())
        }
    }

    private val numbering = Docx.numbering(
        abstractNum(
            0,
            lvl(0, "decimal", "%1.", extra = "<w:pPr><w:ind w:left=\"720\" w:hanging=\"360\"/></w:pPr>"),
            lvl(1, "lowerLetter", "%2)"),
            lvl(2, "lowerRoman", "%1.%2.%3"),
        ),
        abstractNum(1, lvl(0, "decimal", "%1."), lvl(1, "decimal", "%1.%2", extra = "<w:lvlRestart w:val=\"0\"/>")),
        abstractNum(2, lvl(0, "ganada", "%1."), lvl(1, "chosung", "%2)"), lvl(2, "decimalEnclosedCircle", "%3")),
        abstractNum(
            3,
            lvl(0, "bullet", "", extra = "<w:rPr><w:rFonts w:ascii=\"Symbol\" w:hAnsi=\"Symbol\"/></w:rPr>"),
            lvl(1, "bullet", "o", extra = "<w:rPr><w:rFonts w:ascii=\"Courier New\"/></w:rPr>"),
            lvl(2, "bullet", "", extra = "<w:rPr><w:rFonts w:ascii=\"Wingdings\"/></w:rPr>"),
        ),
        abstractNum(4, lvl(0, "upperRoman", "%1."), lvl(1, "decimal", "%1.%2", extra = "<w:isLgl/>")),
        num(1, 0),
        num(2, 0, "<w:lvlOverride w:ilvl=\"0\"><w:startOverride w:val=\"1\"/></w:lvlOverride>"),
        num(3, 1),
        num(4, 2),
        num(5, 3),
        num(6, 4),
    )

    @Test
    fun 목록_번호가_수준마다_이어지고_다시_시작한다() {
        val body = li(1, 0, "A") + li(1, 1, "B") + li(1, 1, "C") + li(1, 2, "D") + li(1, 0, "E") +
            li(1, 1, "F") + li(2, 0, "G") + li(2, 0, "H")
        Docx.open(Docx.docx(body, numbering = numbering)).use { doc ->
            val html = doc.body()
            // F 는 상위 수준(E)이 나아갔으므로 a) 로 다시 시작, G 는 새 번호의 startOverride 로 1 부터.
            assertEquals(listOf("1.", "a)", "b)", "1.b.i", "2.", "a)", "1.", "2."), markers(html))
            assertEquals(listOf("A", "B", "C", "D", "E", "F", "G", "H"), paragraphs(html))
            // 번호 수준의 들여쓰기(720/360 트윕)가 내어쓰기로 간다.
            assertTrue("margin-left:36pt;text-indent:-18pt" in html, html)
            assertTrue("<span class=\"mk\" style=\"min-width:18pt\">1.</span>" in html, html)
        }
    }

    @Test
    fun 다시_시작하지_않는_수준과_법률식_번호() {
        val body = li(3, 0, "a") + li(3, 1, "b") + li(3, 0, "c") + li(3, 1, "d") + li(6, 0, "e") + li(6, 1, "f")
        Docx.open(Docx.docx(body, numbering = numbering)).use { doc ->
            assertEquals(listOf("1.", "1.1", "2.", "2.2", "I.", "1.1"), markers(doc.body()))
        }
    }

    @Test
    fun 몇째_수준부터_다시_시작하는지와_번호_수준의_들여쓰기가_스타일을_이긴다() {
        // 셋째 수준은 `lvlRestart=1`(첫째 수준이 쓰일 때만 다시 시작) — 둘째 수준이 나아가도 이어 센다.
        val restartNumbering = Docx.numbering(
            abstractNum(
                0,
                lvl(0, "decimal", "%1."),
                lvl(1, "decimal", "%1.%2.", extra = "<w:pPr><w:ind w:left=\"1440\" w:hanging=\"360\"/></w:pPr>"),
                lvl(2, "decimal", "%3)", extra = "<w:lvlRestart w:val=\"1\"/>"),
            ),
            num(1, 0),
        )
        // 워드의 목록 문단은 모두 `List Paragraph`(왼쪽 720 트윕)인데 둘째 수준은 더 들어가 보인다 —
        // 번호 수준의 들여쓰기가 문단 스타일을 이긴다.
        val styles = Docx.styles(paraStyle("ListParagraph", "List Paragraph", pPr = "<w:ind w:left=\"720\"/>"))
        val listPara = { ilvl: Int, text: String ->
            p(text, "<w:pStyle w:val=\"ListParagraph\"/><w:numPr><w:ilvl w:val=\"$ilvl\"/><w:numId w:val=\"1\"/></w:numPr>")
        }
        val body = listPara(0, "a") + listPara(2, "b") + listPara(1, "c") + listPara(2, "d") + listPara(0, "e") + listPara(2, "f")
        Docx.open(Docx.docx(body, styles = styles, numbering = restartNumbering)).use { doc ->
            val html = doc.body()
            assertEquals(listOf("1.", "1)", "1.1.", "2)", "2.", "1)"), markers(html))
            assertTrue("margin-left:72pt;text-indent:-18pt" in html, html)
            assertTrue("margin-left:36pt" in html, html)
        }
    }

    @Test
    fun 한국어_번호와_원문자와_글머리표() {
        val body = li(4, 0, "가") + li(4, 1, "ㄱ") + li(4, 2, "①") + li(4, 2, "②") + li(4, 1, "ㄴ") + li(4, 0, "나") +
            li(5, 0, "점") + li(5, 1, "오") + li(5, 2, "네모")
        Docx.open(Docx.docx(body, numbering = numbering)).use { doc ->
            assertEquals(listOf("가.", "ㄱ)", "①", "②", "ㄴ)", "나.", "•", "o", "▪"), markers(doc.body()))
        }
    }

    @Test
    fun 스타일이_준_번호와_직접_서식의_번호_끄기() {
        val styles = Docx.styles(
            paraStyle("Normal", "Normal", default = true),
            paraStyle("ListNumber", "List Number", "Normal", "<w:numPr><w:numId w:val=\"1\"/></w:numPr>"),
        )
        val body = styled("ListNumber", "하나") + styled("ListNumber", "둘") +
            p("번호 끔", "<w:pStyle w:val=\"ListNumber\"/><w:numPr><w:numId w:val=\"0\"/></w:numPr>") +
            styled("ListNumber", "셋")
        Docx.open(Docx.docx(body, styles = styles, numbering = numbering)).use { doc ->
            val html = doc.body()
            assertEquals(listOf("1.", "2.", "3."), markers(html))
            assertEquals(listOf("하나", "둘", "번호 끔", "셋"), paragraphs(html))
        }
    }

    private fun cell(text: String, tcPr: String = "") =
        "<w:tc>" + (if (tcPr.isNotEmpty()) "<w:tcPr>$tcPr</w:tcPr>" else "") + p(text) + "</w:tc>"

    private val mergedTable = "<w:tbl><w:tblGrid><w:gridCol/><w:gridCol/><w:gridCol/></w:tblGrid>" +
        "<w:tr>" + cell("AB", "<w:gridSpan w:val=\"2\"/>") + cell("C", "<w:vMerge w:val=\"restart\"/>") + "</w:tr>" +
        "<w:tr>" + cell("D") + cell("E") + cell("HIDDEN", "<w:vMerge/>") + "</w:tr>" +
        "<w:tr>" + cell("F") + cell("G") + "<w:tc><w:tcPr><w:vMerge/></w:tcPr><w:p/></w:tc>" + "</w:tr>" +
        "</w:tbl>"

    @Test
    fun 표의_칸_합치기가_colspan_과_rowspan_이_된다() {
        Docx.open(Docx.docx(p("앞") + mergedTable + p("뒤"))).use { doc ->
            val html = doc.body()
            assertTrue(Regex("<td colspan=\"2\"><p>AB</p></td>").containsMatchIn(html), html)
            assertTrue(Regex("<td rowspan=\"3\"><p>C</p></td>").containsMatchIn(html), html)
            // 위 칸에 합쳐진 칸은 그리지 않는다(워드도 그 내용을 보이지 않는다).
            assertFalse("HIDDEN" in html)
            assertEquals(6, html.occurrences("<td"))
            assertEquals(listOf("앞", "AB", "C", "D", "E", "F", "G", "뒤"), paragraphs(html))
        }
    }

    @Test
    fun 칸_안의_표와_깊이_상한() {
        val inner = "<w:tbl><w:tr>" + cell("안쪽") + "</w:tr></w:tbl>"
        val outer = "<w:tbl><w:tr><w:tc>" + p("바깥") + inner + "</w:tc></w:tr></w:tbl>"
        Docx.open(Docx.docx(outer)).use { doc ->
            val html = doc.body()
            assertEquals(2, html.occurrences("<table"))
            assertEquals(listOf("바깥", "안쪽"), paragraphs(html))
        }
        // 열 겹 — 여덟 겹 넘어서는 버리고 알린다.
        var deep = p("가장 안")
        for (i in 10 downTo 1) deep = "<w:tbl><w:tr><w:tc>" + p("겹$i") + deep + "</w:tc></w:tr></w:tbl>"
        Docx.open(Docx.docx(deep)).use { doc ->
            val html = doc.body()
            assertEquals(DocxWalker.MAX_TABLE_DEPTH, html.occurrences("<table"))
            assertTrue("겹8" in html)
            assertFalse("겹9" in html)
            assertFalse("가장 안" in html)
            assertTrue(doc.warnings.any { it.code == FlowWarnings.TRUNCATED })
        }
    }

    @Test
    fun 앞을_비운_행과_행_상한() {
        val gap = "<w:tbl><w:tr>" + cell("1") + cell("2") + cell("3") + "</w:tr>" +
            "<w:tr><w:trPr><w:gridBefore w:val=\"2\"/></w:trPr>" + cell("오른쪽 끝") + "</w:tr></w:tbl>"
        Docx.open(Docx.docx(gap)).use { doc ->
            assertTrue("<td class=\"gap\" colspan=\"2\"></td><td><p>오른쪽 끝</p></td>" in doc.body(), doc.body())
        }
        val rows = (1..DocxWalker.MAX_ROWS + 2).joinToString("") { "<w:tr>" + cell("r$it") + "</w:tr>" }
        Docx.open(Docx.docx("<w:tbl>$rows</w:tbl>" + p("표 뒤"))).use { doc ->
            val html = doc.body()
            assertEquals(DocxWalker.MAX_ROWS, html.occurrences("<tr>"))
            assertTrue("r${DocxWalker.MAX_ROWS}<" in html)
            assertFalse("r${DocxWalker.MAX_ROWS + 1}<" in html)
            assertTrue("표 뒤" in html, "상한 뒤의 블록은 그대로 나온다")
            assertTrue(doc.warnings.any { it.code == FlowWarnings.TRUNCATED })
        }
    }

    @Test
    fun 긴_문서는_제목_앞에서_조각나고_목차가_제_조각을_가리킨다() {
        val sb = StringBuilder()
        var n = 0
        for (chapter in 1..6) {
            sb.append(styled("Heading1", "장 $chapter"))
            repeat(3) {
                n++
                sb.append(li(1, 0, "항목 $n"))
            }
        }
        val options = DocxOptions(ChunkPolicy(softChars = Int.MAX_VALUE, softBlocks = 5, hardChars = Int.MAX_VALUE, hardBlocks = 1000))
        Docx.open(Docx.docx(sb.toString(), styles = headingStyles, numbering = numbering), options).use { doc ->
            assertTrue(doc.parts.size > 1, "조각 수 ${doc.parts.size}")
            assertEquals(doc.parts.indices.map { "~part-$it.html" }, doc.parts.map { it.path })
            val all = doc.parts.indices.map { doc.body(it) }
            // 목차의 자리는 그 조각 안에 있다.
            for (o in doc.outline) {
                assertTrue("id=\"${o.anchor}\"" in all[o.partIndex], "${o.title} 이 ${o.partIndex} 에 없다")
            }
            // 첫 조각 뒤의 조각은 제목으로 시작하고, 그 제목이 이름이 된다.
            for (i in 1 until doc.parts.size) {
                assertTrue(all[i].trimStart().startsWith("<h1"), all[i].take(80))
                assertTrue(doc.parts[i].label.startsWith("장 "))
            }
            // 목록 번호는 조각을 건너 이어진다(조각의 시작 상태를 훑기가 찍어 둔다).
            assertEquals((1..n).map { "$it." }, all.flatMap { markers(it) })
            assertEquals((1..n).map { "항목 $it" }, all.flatMap { paragraphs(it) }.filter { it.startsWith("항목") })
        }
    }

    @Test
    fun 제목이_없으면_단단한_상한에서_자른다() {
        val body = (1..20).joinToString("") { p("문단 $it") }
        val options = DocxOptions(ChunkPolicy(softChars = Int.MAX_VALUE, softBlocks = 2, hardChars = Int.MAX_VALUE, hardBlocks = 6))
        Docx.open(Docx.docx(body), options).use { doc ->
            assertEquals(4, doc.parts.size)
            assertEquals((1..20).map { "문단 $it" }, doc.parts.indices.flatMap { paragraphs(doc.body(it)) })
            assertTrue(doc.parts.all { it.label.isEmpty() })
        }
    }

    @Test
    fun 조각의_시작_상태는_글상자_안의_번호와_앞_조각의_표까지_센다() {
        // 글상자 안의 번호 문단이 앞 조각에 있고, 합친 칸이 있는 표는 뒤 조각에 있다. 훑기가 둘을 세지 않으면
        // 뒤 조각의 번호가 3 이 아니고, 표 번호가 어긋나 행 합치기를 못 찾는다.
        val textBox = "<w:p><w:r><w:pict><v:shape><v:textbox><w:txbxContent>" + li(1, 0, "글상자 안") +
            "</w:txbxContent></v:textbox></v:shape></w:pict></w:r></w:p>"
        val plainTable = "<w:tbl><w:tr>" + cell("앞 표") + "</w:tr></w:tbl>"
        val body = li(1, 0, "첫째") + textBox + plainTable + styled("Heading1", "뒤 조각") + li(1, 0, "셋째") + mergedTable
        val options = DocxOptions(ChunkPolicy(softChars = Int.MAX_VALUE, softBlocks = 1, hardChars = Int.MAX_VALUE, hardBlocks = 1000))
        Docx.open(Docx.docx(body, styles = headingStyles, numbering = numbering), options).use { doc ->
            assertEquals(2, doc.parts.size)
            assertEquals(listOf("1.", "2."), markers(doc.body(0)))
            val second = doc.body(1)
            assertEquals(listOf("3."), markers(second))
            assertTrue("rowspan=\"3\"" in second, second)
            assertFalse("HIDDEN" in second)
        }
    }
}
