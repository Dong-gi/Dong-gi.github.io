package io.github.donggi.iroiroviewer.format.xlsx

import io.github.donggi.iroiroviewer.format.opc.OoxmlXml
import io.github.donggi.iroiroviewer.safety.ParseLimits
import io.github.donggi.iroiroviewer.safety.nextGuarded
import org.xmlpull.v1.XmlPullParser

/** 그림판(`xl/drawings/drawingN.xml`)의 개체 하나 — 읽은 그대로, 관계는 아직 풀지 않았다. */
internal sealed interface DrawingItem {
    /** 그림. [embed] 는 패키지 안 그림의 관계 id, [link] 는 바깥 파일을 가리키는 관계 id. */
    class Picture(val embed: String?, val link: String?, val alt: String?, val widthEmu: Long?) : DrawingItem

    /** 도형. [text] 가 비었으면 모양만 있던 도형이다. [textBox] 면 글상자 — 모양을 잃어도 잃은 것이 거의 없다. */
    class Shape(val text: String, val textBox: Boolean) : DrawingItem

    object Chart : DrawingItem
    object SmartArt : DrawingItem

    /** 연결선. 그리지 않는다. */
    object Connector : DrawingItem

    /** 모르는 개체(잉크·OLE·새 형식). */
    object Other : DrawingItem
}

/**
 * 시트의 그림판을 읽는다 — SpreadsheetML Drawing(`xdr:`), ECMA-376 Part 1 §20.5.
 *
 * ## 자리를 재현하지 않는다
 *
 * 개체는 셀 좌표(`from`·`to`)에 걸려 있지만, 표는 화면 폭에 맞춰 흐르므로 그 좌표에 겹쳐 그리면
 * 글자를 덮는다. **표 아래에 걸린 순서대로** 모은다(쪽 재현을 포기한 흐름 렌더의 판단과 같다).
 *
 * ## 대체 내용(`mc:AlternateContent`)
 *
 * **첫 `Choice` 만** 읽는다. 새 차트(`cx:`)의 `Fallback` 은 '이 버전에서는 차트를 볼 수 없다' 는
 * 글상자라, 그것을 글자로 옮기면 차트가 있었다는 사실보다 못한 것을 보여 준다. `Choice` 가 없을
 * 때만 `Fallback` 을 본다.
 */
internal object XlsxDrawing {

    private val OBJECTS = setOf("pic", "sp", "grpSp", "graphicFrame", "cxnSp", "AlternateContent", "contentPart")

    fun read(p: XmlPullParser, limits: ParseLimits): List<DrawingItem> {
        val out = ArrayList<DrawingItem>()
        if (!XlsxXml.toRoot(p, limits)) return out
        readAnchors(p, limits, out, 0)
        return out
    }

    /** 지금 선 요소(뿌리 또는 `Choice`)의 자식 가운데 닻(anchor)을 읽는다. */
    private fun readAnchors(p: XmlPullParser, limits: ParseLimits, out: MutableList<DrawingItem>, depth: Int) {
        XlsxXml.children(p, limits) { name ->
            when (name) {
                "twoCellAnchor", "oneCellAnchor", "absoluteAnchor" -> {
                    var extCx: Long? = null
                    XlsxXml.children(p, limits) { part ->
                        when (part) {
                            "ext" -> extCx = OoxmlXml.long(p, "cx")
                            in OBJECTS -> readObject(p, limits, part, out, depth, extCx)
                            else -> Unit
                        }
                    }
                }
                "AlternateContent" -> if (depth < XlsxLimits.MAX_GROUP_DEPTH) {
                    alternate(p, limits) { readAnchors(p, limits, out, depth + 1) }
                }
                else -> Unit
            }
        }
    }

    /** `Choice` 하나, 없으면 `Fallback` 하나의 안쪽을 [inside] 로 읽는다. */
    private inline fun alternate(p: XmlPullParser, limits: ParseLimits, inside: () -> Unit) {
        var done = false
        XlsxXml.children(p, limits) { branch ->
            if (!done && (branch == "Choice" || branch == "Fallback")) {
                done = true
                inside()
            }
        }
    }

    private fun readObject(
        p: XmlPullParser,
        limits: ParseLimits,
        name: String,
        out: MutableList<DrawingItem>,
        depth: Int,
        anchorCx: Long?,
    ) {
        if (out.size >= XlsxLimits.MAX_DRAWING_ITEMS) return
        when (name) {
            "pic" -> readPicture(p, limits, anchorCx)?.let { out.add(it) }
            "sp" -> readShape(p, limits)?.let { out.add(it) }
            "graphicFrame" -> readFrame(p, limits)?.let { out.add(it) }
            "cxnSp" -> out.add(DrawingItem.Connector)
            "contentPart" -> out.add(DrawingItem.Other)
            "grpSp" -> if (depth < XlsxLimits.MAX_GROUP_DEPTH) {
                XlsxXml.children(p, limits) { child ->
                    if (child in OBJECTS) readObject(p, limits, child, out, depth + 1, null)
                }
            } else {
                // 너무 깊은 묶음 — 안의 것을 잃었다는 사실만 센다.
                out.add(DrawingItem.Other)
            }
            "AlternateContent" -> if (depth < XlsxLimits.MAX_GROUP_DEPTH) {
                alternate(p, limits) {
                    XlsxXml.children(p, limits) { child ->
                        if (child in OBJECTS) readObject(p, limits, child, out, depth + 1, anchorCx)
                    }
                }
            }
        }
    }

    /** 숨긴 개체(`cNvPr/@hidden`)는 null — Excel 도 보여 주지 않는다. */
    private fun readPicture(p: XmlPullParser, limits: ParseLimits, anchorCx: Long?): DrawingItem? {
        var alt: String? = null
        var hidden = false
        var embed: String? = null
        var link: String? = null
        var cx: Long? = null
        var xfrmDepth = -1
        walk(p, limits) { ev ->
            if (ev == XmlPullParser.START_TAG) {
                when (p.name) {
                    "cNvPr" -> if (alt == null) {
                        // 길이를 자른다([XlsxLimits.MAX_ALT_CHARS] 의 주석) — 속성 값에는 상한이 없다.
                        alt = (OoxmlXml.attr(p, "descr")?.takeIf { it.isNotBlank() } ?: OoxmlXml.attr(p, "title"))
                            ?.take(XlsxLimits.MAX_ALT_CHARS)
                        hidden = SheetScan.isTrue(OoxmlXml.attr(p, "hidden"))
                    }
                    "blip" -> {
                        embed = OoxmlXml.rel(p, "embed")
                        link = OoxmlXml.rel(p, "link")
                    }
                    "xfrm" -> xfrmDepth = p.depth
                    "ext" -> if (xfrmDepth > 0 && p.depth == xfrmDepth + 1) cx = OoxmlXml.long(p, "cx")
                }
            } else if (ev == XmlPullParser.END_TAG && p.name == "xfrm") {
                xfrmDepth = -1
            }
        }
        if (hidden) return null
        return DrawingItem.Picture(embed, link, alt, cx ?: anchorCx)
    }

    private fun readShape(p: XmlPullParser, limits: ParseLimits): DrawingItem? {
        var hidden = false
        var textBox = false
        val text = StringBuilder()
        var bodyDepth = -1
        var sawParagraph = false
        walk(p, limits) { ev ->
            if (ev == XmlPullParser.START_TAG) {
                when (p.name) {
                    "cNvPr" -> hidden = hidden || SheetScan.isTrue(OoxmlXml.attr(p, "hidden"))
                    "cNvSpPr" -> textBox = SheetScan.isTrue(OoxmlXml.attr(p, "txBox"))
                    "txBody" -> if (bodyDepth < 0) bodyDepth = p.depth
                    "p" -> if (bodyDepth > 0) {
                        if (sawParagraph && text.length < XlsxLimits.MAX_SHAPE_CHARS) text.append('\n')
                        sawParagraph = true
                    }
                    "br" -> if (bodyDepth > 0 && text.length < XlsxLimits.MAX_SHAPE_CHARS) text.append('\n')
                    "t" -> if (bodyDepth > 0) {
                        val room = XlsxLimits.MAX_SHAPE_CHARS - text.length
                        if (room > 0) text.append(OoxmlXml.collectText(p, limits, room)) else OoxmlXml.skip(p, limits)
                    }
                }
            } else if (ev == XmlPullParser.END_TAG && p.name == "txBody" && p.depth == bodyDepth) {
                bodyDepth = -1
            }
        }
        if (hidden) return null
        return DrawingItem.Shape(text.toString().trim(), textBox)
    }

    private fun readFrame(p: XmlPullParser, limits: ParseLimits): DrawingItem? {
        var hidden = false
        var kind: DrawingItem = DrawingItem.Other
        walk(p, limits) { ev ->
            if (ev == XmlPullParser.START_TAG) {
                when (p.name) {
                    "cNvPr" -> hidden = hidden || SheetScan.isTrue(OoxmlXml.attr(p, "hidden"))
                    "graphicData" -> {
                        val uri = OoxmlXml.attr(p, "uri").orEmpty()
                        kind = when {
                            // drawingml/2006/chart, 그리고 2014 의 새 차트(chartex).
                            uri.endsWith("/chart") || uri.endsWith("/chartex") -> DrawingItem.Chart
                            uri.endsWith("/diagram") -> DrawingItem.SmartArt
                            else -> DrawingItem.Other
                        }
                    }
                }
            }
        }
        return if (hidden) null else kind
    }

    /**
     * 지금 선 요소의 하위를 끝까지 걸으며 사건마다 [onEvent] 를 부른다. 끝나면 그 요소의 END_TAG 에
     * 서 있다. [onEvent] 가 하위 요소를 스스로 읽어 치우면(`collectText`) 그 요소의 END_TAG 에서
     * 돌아와야 한다 — 깊이 셈이 거기에 맞춰져 있다.
     */
    private inline fun walk(p: XmlPullParser, limits: ParseLimits, onEvent: (Int) -> Unit) {
        val depth = p.depth
        while (true) {
            val ev = p.nextGuarded(limits)
            if (ev == XmlPullParser.END_DOCUMENT) return
            if (ev == XmlPullParser.END_TAG && p.depth <= depth) return
            onEvent(ev)
        }
    }

    /**
     * 메모 개수. 옛 메모(`comments`)의 `comment`, 없으면 새 메모(`threadedComments`)의 `threadedComment`.
     * 둘 다 있는 파일은 옛 메모가 새 메모의 대체본이라 옛 것만 센다(두 번 세지 않는다).
     */
    fun countComments(p: XmlPullParser, limits: ParseLimits, element: String): Int {
        var n = 0
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT && n < XlsxLimits.MAX_COMMENTS_COUNTED) {
            if (ev == XmlPullParser.START_TAG && p.name == element) n++
            ev = p.nextGuarded(limits)
        }
        return n
    }
}
