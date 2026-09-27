package io.github.donggi.iroiroviewer.format.docx

import io.github.donggi.iroiroviewer.format.FlowDocument
import io.github.donggi.iroiroviewer.format.ProgressSink
import io.github.donggi.iroiroviewer.format.opc.OpcPackage
import io.github.donggi.iroiroviewer.format.opc.TinyOoxml
import io.github.donggi.iroiroviewer.safety.ParseLimits
import kotlinx.coroutines.runBlocking

/**
 * docx 시험의 짜개. 본문 XML 조각을 받아 `TinyOoxml` 패키지를 만든다 — 이진 표본 없이 구조를 읽을 수 있게.
 */
internal object Docx {
    const val W = TinyOoxml.NS_W
    const val R = TinyOoxml.NS_R

    /** 본문·스타일·번호·각주가 함께 쓰는 이름공간 선언. */
    const val NS = "xmlns:w=\"$W\" xmlns:r=\"$R\"" +
        " xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\"" +
        " xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\"" +
        " xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"" +
        " xmlns:mc=\"http://schemas.openxmlformats.org/markup-compatibility/2006\"" +
        " xmlns:wps=\"http://schemas.microsoft.com/office/word/2010/wordprocessingShape\"" +
        " xmlns:v=\"urn:schemas-microsoft-com:vml\" xmlns:o=\"urn:schemas-microsoft-com:office:office\"" +
        " xmlns:m=\"http://schemas.openxmlformats.org/officeDocument/2006/math\"" +
        " xmlns:w14=\"http://schemas.microsoft.com/office/word/2010/wordml\"" +
        " xmlns:w16se=\"http://schemas.microsoft.com/office/word/2015/wordml/symex\"" +
        " xmlns:cx1=\"http://schemas.microsoft.com/office/drawing/2015/9/8/chartex\"" +
        " xmlns:c=\"http://schemas.openxmlformats.org/drawingml/2006/chart\"" +
        " xmlns:dgm=\"http://schemas.openxmlformats.org/drawingml/2006/diagram\""

    const val CORE_PROPS = "http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties"
    const val REL_SETTINGS = "${TinyOoxml.R}/settings"

    fun document(body: String) =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><w:document $NS><w:body>$body</w:body></w:document>"

    fun styles(vararg defs: String, defaults: String = "") =
        "<w:styles $NS>$defaults${defs.joinToString("")}</w:styles>"

    fun paraStyle(id: String, name: String, basedOn: String? = null, pPr: String = "", rPr: String = "", default: Boolean = false) =
        "<w:style w:type=\"paragraph\"${if (default) " w:default=\"1\"" else ""} w:styleId=\"$id\"><w:name w:val=\"$name\"/>" +
            (basedOn?.let { "<w:basedOn w:val=\"$it\"/>" } ?: "") +
            (if (pPr.isNotEmpty()) "<w:pPr>$pPr</w:pPr>" else "") +
            (if (rPr.isNotEmpty()) "<w:rPr>$rPr</w:rPr>" else "") + "</w:style>"

    fun charStyle(id: String, name: String, rPr: String) =
        "<w:style w:type=\"character\" w:styleId=\"$id\"><w:name w:val=\"$name\"/><w:rPr>$rPr</w:rPr></w:style>"

    fun numbering(vararg parts: String) = "<w:numbering $NS>${parts.joinToString("")}</w:numbering>"

    fun lvl(ilvl: Int, fmt: String, text: String, start: Int = 1, extra: String = "") =
        "<w:lvl w:ilvl=\"$ilvl\"><w:start w:val=\"$start\"/><w:numFmt w:val=\"$fmt\"/>$extra<w:lvlText w:val=\"${esc(text)}\"/></w:lvl>"

    fun abstractNum(id: Int, vararg levels: String) = "<w:abstractNum w:abstractNumId=\"$id\">${levels.joinToString("")}</w:abstractNum>"

    fun num(numId: Int, abstractId: Int, overrides: String = "") =
        "<w:num w:numId=\"$numId\"><w:abstractNumId w:val=\"$abstractId\"/>$overrides</w:num>"

    /** 글 하나짜리 문단. [pPr]·[rPr] 은 속성 요소의 속. */
    fun p(text: String, pPr: String = "", rPr: String = "") =
        "<w:p>" + (if (pPr.isNotEmpty()) "<w:pPr>$pPr</w:pPr>" else "") + run(text, rPr) + "</w:p>"

    fun run(text: String, rPr: String = "") =
        "<w:r>" + (if (rPr.isNotEmpty()) "<w:rPr>$rPr</w:rPr>" else "") + "<w:t xml:space=\"preserve\">${esc(text)}</w:t></w:r>"

    fun styled(style: String, text: String) = p(text, "<w:pStyle w:val=\"$style\"/>")

    fun li(numId: Int, ilvl: Int, text: String) =
        p(text, "<w:numPr><w:ilvl w:val=\"$ilvl\"/><w:numId w:val=\"$numId\"/></w:numPr>")

    fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    /** 본문을 가진 패키지. 스타일·번호·각주 부분은 있으면 관계와 함께 넣는다. */
    fun docx(
        body: String,
        styles: String? = null,
        numbering: String? = null,
        footnotes: String? = null,
        endnotes: String? = null,
        settings: String? = null,
        rawDocument: String? = null,
    ): TinyOoxml {
        val t = TinyOoxml()
            .xml("word/document.xml", TinyOoxml.CT_DOCX_MAIN, rawDocument ?: document(body))
            .rel(null, "rId1", TinyOoxml.REL_OFFICE_DOCUMENT, "word/document.xml")
        if (styles != null) t.xml("word/styles.xml", null, styles).rel("word/document.xml", "rIdS", TinyOoxml.REL_STYLES, "styles.xml")
        if (numbering != null) t.xml("word/numbering.xml", null, numbering).rel("word/document.xml", "rIdN", TinyOoxml.REL_NUMBERING, "numbering.xml")
        if (footnotes != null) t.xml("word/footnotes.xml", null, footnotes).rel("word/document.xml", "rIdF", TinyOoxml.REL_FOOTNOTES, "footnotes.xml")
        if (endnotes != null) t.xml("word/endnotes.xml", null, endnotes).rel("word/document.xml", "rIdE", TinyOoxml.REL_ENDNOTES, "endnotes.xml")
        if (settings != null) t.xml("word/settings.xml", null, settings).rel("word/document.xml", "rIdT", REL_SETTINGS, "settings.xml")
        return t
    }

    fun open(t: TinyOoxml, options: DocxOptions = DocxOptions()): FlowDocument = runBlocking {
        DocxDocument.open(OpcPackage.open(t.source(), ParseLimits.DEFAULT), ParseLimits.DEFAULT, ProgressSink.NONE, options)
    }

    fun openBytes(bytes: ByteArray, options: DocxOptions = DocxOptions()): FlowDocument = runBlocking {
        val src = io.github.donggi.iroiroviewer.format.ByteArrayDocumentSource(bytes, "sample.docx")
        DocxDocument.open(OpcPackage.open(src, ParseLimits.DEFAULT), ParseLimits.DEFAULT, ProgressSink.NONE, options)
    }
}

/** 부분 하나의 `<body>` 안쪽. */
internal fun FlowDocument.body(index: Int = 0): String =
    partHtml(index)!!.substringAfter("<body>").substringBeforeLast("</body>")

private val BLOCK = Regex("<(p|h[1-6])\\b[^>]*>(.*?)</\\1>", RegexOption.DOT_MATCHES_ALL)
private val MARKER = Regex("<span class=\"mk\"[^>]*>.*?</span>", RegexOption.DOT_MATCHES_ALL)
private val MARKER_TEXT = Regex("<span class=\"mk\"[^>]*>(.*?)</span>", RegexOption.DOT_MATCHES_ALL)
private val TAG = Regex("<[^>]+>")

/** HTML 의 글자만(태그를 떼고 엔티티를 푼다). */
internal fun plain(html: String): String =
    TAG.replace(html, "").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&#39;", "'").replace("&amp;", "&")

/** 문단·제목마다의 글(목록 표지는 뺀다). */
internal fun paragraphs(html: String): List<String> =
    BLOCK.findAll(html).map { plain(MARKER.replace(it.groupValues[2], "")) }.toList()

/** 목록 표지들(문서 차례). */
internal fun markers(html: String): List<String> = MARKER_TEXT.findAll(html).map { plain(it.groupValues[1]) }.toList()

internal fun String.occurrences(needle: String): Int {
    var n = 0
    var i = indexOf(needle)
    while (i >= 0) {
        n++
        i = indexOf(needle, i + needle.length)
    }
    return n
}
