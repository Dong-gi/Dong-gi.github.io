package io.github.donggi.iroiroviewer.format.xlsx

import io.github.donggi.iroiroviewer.format.FlowDocument
import io.github.donggi.iroiroviewer.format.ProgressSink
import io.github.donggi.iroiroviewer.format.opc.OpcPackage
import io.github.donggi.iroiroviewer.format.opc.TinyOoxml
import io.github.donggi.iroiroviewer.safety.ParseLimits
import kotlinx.coroutines.runBlocking
import kotlin.test.assertTrue
import kotlin.test.fail

/** 시험용 통합 문서 짜기. `TinyOoxml` 위에 xlsx 의 뼈대(통합 문서·시트 관계)만 얹는다. */
internal object Books {
    const val NS_S = TinyOoxml.NS_S
    const val NS_R = TinyOoxml.NS_R
    const val NS_XDR = "http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing"
    const val NS_MC = "http://schemas.openxmlformats.org/markup-compatibility/2006"

    const val CT_SHEET = "application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"
    const val CT_SST = "application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"
    const val CT_STYLES = "application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"
    const val CT_DRAWING = "application/vnd.openxmlformats-officedocument.drawing+xml"

    const val REL_DRAWING = "${TinyOoxml.R}/drawing"
    const val REL_COMMENTS = "${TinyOoxml.R}/comments"
    const val REL_CHARTSHEET = "${TinyOoxml.R}/chartsheet"

    class Sheet(val name: String, val xml: String, val state: String? = null, val type: String = TinyOoxml.REL_WORKSHEET)

    fun sheetPart(i: Int) = "xl/worksheets/sheet${i + 1}.xml"

    fun book(
        sheets: List<Sheet>,
        sst: String? = null,
        styles: String? = null,
        date1904: Boolean = false,
    ): TinyOoxml {
        val t = TinyOoxml()
        val wb = StringBuilder()
        wb.append("""<workbook xmlns="$NS_S" xmlns:r="$NS_R">""")
        if (date1904) wb.append("""<workbookPr date1904="1"/>""")
        wb.append("<sheets>")
        sheets.forEachIndexed { i, s ->
            wb.append("""<sheet name="${esc(s.name)}" sheetId="${i + 1}" r:id="rId${i + 1}"""")
            if (s.state != null) wb.append(""" state="${s.state}"""")
            wb.append("/>")
        }
        wb.append("</sheets></workbook>")
        t.xml("xl/workbook.xml", TinyOoxml.CT_XLSX_MAIN, wb.toString())
        t.rel(null, "rId1", TinyOoxml.REL_OFFICE_DOCUMENT, "xl/workbook.xml")
        sheets.forEachIndexed { i, s ->
            t.xml(sheetPart(i), CT_SHEET, s.xml)
            t.rel("xl/workbook.xml", "rId${i + 1}", s.type, "worksheets/sheet${i + 1}.xml")
        }
        if (sst != null) {
            t.xml("xl/sharedStrings.xml", CT_SST, sst)
            t.rel("xl/workbook.xml", "rIdS", TinyOoxml.REL_SHARED_STRINGS, "sharedStrings.xml")
        }
        if (styles != null) {
            t.xml("xl/styles.xml", CT_STYLES, styles)
            t.rel("xl/workbook.xml", "rIdT", TinyOoxml.REL_STYLES, "styles.xml")
        }
        return t
    }

    /** 시트 XML. [before] 는 `sheetData` 앞(`cols`·`sheetViews`), [after] 는 뒤(`mergeCells`·`hyperlinks`). */
    fun sheet(rows: String, before: String = "", after: String = ""): String =
        """<worksheet xmlns="$NS_S" xmlns:r="$NS_R">$before<sheetData>$rows</sheetData>$after</worksheet>"""

    fun open(t: TinyOoxml, limits: ParseLimits = ParseLimits.DEFAULT, progress: ProgressSink = ProgressSink.NONE): FlowDocument =
        runBlocking { XlsxDocument.open(OpcPackage.open(t.source("t.xlsx"), limits), limits, progress) }

    fun open(bytes: ByteArray): FlowDocument = runBlocking {
        XlsxDocument.open(
            OpcPackage.open(io.github.donggi.iroiroviewer.format.ByteArrayDocumentSource(bytes, "t.xlsx"), ParseLimits.DEFAULT),
            ParseLimits.DEFAULT,
            ProgressSink.NONE,
        )
    }

    fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}

/** 표의 칸 하나. [ref] 는 머리글에서 읽은 `B3` 모양의 주소. */
internal class HCell(val ref: String, val tag: String, val attrs: Map<String, String>, val inner: String) {
    val text: String get() = Grid.unescape(inner.replace(Regex("<[^>]+>"), ""))
    val style: String get() = attrs["style"].orEmpty()
    val classes: List<String> get() = attrs["class"]?.split(' ').orEmpty()
    val colspan: Int get() = attrs["colspan"]?.toInt() ?: 1
    val rowspan: Int get() = attrs["rowspan"]?.toInt() ?: 1
}

/**
 * 부분의 HTML 에서 시트 표를 읽는다. **HTML 표 배치 규칙대로** 칸의 자리를 계산하므로, 병합이 표를
 * 비틀면(겹친 칸·모자란 칸) 여기서 실패한다 — 병합 시험의 판별력이 거기서 나온다.
 */
internal object Grid {
    private val TR = Regex("""<tr([^>]*)>(.*?)</tr>""", RegexOption.DOT_MATCHES_ALL)
    private val CELL = Regex("""<(td|th)([^>]*)>(.*?)</\1>""", RegexOption.DOT_MATCHES_ALL)
    private val ATTR = Regex("""([a-z][a-z0-9-]*)="([^"]*)"""")

    class Parsed(val columns: List<String>, val rowHeads: List<String>, val cells: Map<String, HCell>, val rowAttrs: List<Map<String, String>>)

    fun unescape(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")

    private fun attrs(s: String): Map<String, String> = ATTR.findAll(s).associate { it.groupValues[1] to it.groupValues[2] }

    fun parse(html: String): Parsed {
        val thead = html.substringAfter("<thead>", "").substringBefore("</thead>")
        val columns = CELL.findAll(thead).map { unescape(it.groupValues[3]) }.drop(1).toList()
        val width = columns.size
        val body = html.substringAfter("<tbody>", "").substringBefore("</tbody>")
        val rows = TR.findAll(body).toList()
        val occupied = Array(rows.size) { BooleanArray(width) }
        val cells = LinkedHashMap<String, HCell>()
        val heads = ArrayList<String>()
        val rowAttrs = ArrayList<Map<String, String>>()
        for ((r, m) in rows.withIndex()) {
            rowAttrs.add(attrs(m.groupValues[1]))
            val parts = CELL.findAll(m.groupValues[2]).toList()
            val head = parts.firstOrNull { it.groupValues[1] == "th" }
            heads.add(head?.let { unescape(it.groupValues[3]) }.orEmpty())
            var col = 0
            for (c in parts) {
                if (c.groupValues[1] != "td") continue
                while (col < width && occupied[r][col]) col++
                if (col >= width) fail("줄 ${r + 1} 에 칸이 남는다: ${m.value.take(300)}")
                val a = attrs(c.groupValues[2])
                val cs = a["colspan"]?.toInt() ?: 1
                val rs = a["rowspan"]?.toInt() ?: 1
                for (rr in r until minOf(rows.size, r + rs)) {
                    for (cc in col until col + cs) {
                        if (cc >= width) fail("줄 ${r + 1} 의 colspan 이 표를 넘는다")
                        if (occupied[rr][cc]) fail("칸이 겹친다: 줄 ${rr + 1}, 열 ${cc + 1}")
                        occupied[rr][cc] = true
                    }
                }
                val ref = columns[col] + heads[r]
                cells[ref] = HCell(ref, "td", a, c.groupValues[3])
                col += cs
            }
            for (cc in 0 until width) assertTrue(occupied[r][cc], "줄 ${r + 1}(${heads[r]}) 의 ${cc + 1}번째 칸이 비었다 — 표가 비틀렸다")
        }
        return Parsed(columns, heads, cells, rowAttrs)
    }
}
