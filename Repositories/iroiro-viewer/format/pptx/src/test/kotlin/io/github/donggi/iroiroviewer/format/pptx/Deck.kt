package io.github.donggi.iroiroviewer.format.pptx

import io.github.donggi.iroiroviewer.format.ProgressSink
import io.github.donggi.iroiroviewer.format.opc.OpcPackage
import io.github.donggi.iroiroviewer.format.opc.TinyOoxml
import io.github.donggi.iroiroviewer.safety.ParseLimits
import kotlinx.coroutines.runBlocking

/**
 * 시험용 프레젠테이션을 **메모리에서** 짠다. `TinyOoxml` 위에 pptx 의 뼈대(프레젠테이션·마스터·
 * 레이아웃·테마)를 기본값으로 얹고, 시험은 바꾸고 싶은 부분만 바꾼다.
 *
 * 기본 마스터는 오피스 기본값을 흉내 낸다 — 제목 44pt, 본문 1단계 32pt·2단계 28pt(글머리 `•`·`–`),
 * 그 밖 18pt. 테마의 `dk1` 은 **일부러 검정이 아닌 `123456`** 이다 — 글자색이 테마를 거쳐 왔는지
 * 시험이 가려낼 수 있게.
 */
internal class Deck {

    val tiny = TinyOoxml()
    private val slideRels = ArrayList<String>()
    private var count = 0

    var sldSz: String? = """<p:sldSz cx="9144000" cy="6858000"/>"""
    var defaultTextStyle: String = ""
    var masterXml: String? = master()
    var layoutXml: String? = layout()
    var themeXml: String? = THEME
    var coreTitle: String? = null

    /** `p:presentation` 요소의 속성(`firstSlideNum="5"` 따위). */
    var presAttrs: String = ""

    /** 슬라이드 하나. [part] 를 주지 않으면 `slideN.xml`. 레이아웃 관계를 걸지 않으려면 [withLayout] 을 거짓으로. */
    fun slide(xml: String, part: String? = null, withLayout: Boolean = true): String {
        count++
        val name = part ?: "ppt/slides/slide$count.xml"
        tiny.xml(name, CT_SLIDE, xml)
        val rid = "rIdS$count"
        tiny.rel("ppt/presentation.xml", rid, TinyOoxml.REL_SLIDE, "/" + name)
        slideRels.add(rid)
        if (withLayout) tiny.rel(name, "rIdL", TinyOoxml.REL_SLIDE_LAYOUT, "../slideLayouts/slideLayout1.xml")
        return name
    }

    /** 슬라이드 순서를 손으로 정한다(관계 id 목록). */
    var order: List<String>? = null

    /** 슬라이드 관계 id 들(추가한 순서). */
    val slideRelIds: List<String> get() = slideRels

    fun build(): TinyOoxml {
        val ids = (order ?: slideRels).mapIndexed { i, rid -> """<p:sldId id="${256 + i}" r:id="$rid"/>""" }.joinToString("")
        tiny.xml(
            "ppt/presentation.xml", TinyOoxml.CT_PPTX_MAIN,
            """<p:presentation $NS $presAttrs><p:sldMasterIdLst><p:sldMasterId id="2147483648" r:id="rIdM"/></p:sldMasterIdLst>""" +
                """<p:sldIdLst>$ids</p:sldIdLst>${sldSz.orEmpty()}<p:notesSz cx="6858000" cy="9144000"/>""" +
                (if (defaultTextStyle.isEmpty()) "" else "<p:defaultTextStyle>$defaultTextStyle</p:defaultTextStyle>") +
                "</p:presentation>",
        )
        tiny.rel(null, "rId1", TinyOoxml.REL_OFFICE_DOCUMENT, "ppt/presentation.xml")
        tiny.rel("ppt/presentation.xml", "rIdM", TinyOoxml.REL_SLIDE_MASTER, "slideMasters/slideMaster1.xml")
        masterXml?.let {
            tiny.xml("ppt/slideMasters/slideMaster1.xml", null, it)
            tiny.rel("ppt/slideMasters/slideMaster1.xml", "rIdT", TinyOoxml.REL_THEME, "../theme/theme1.xml")
        }
        layoutXml?.let {
            tiny.xml("ppt/slideLayouts/slideLayout1.xml", null, it)
            tiny.rel("ppt/slideLayouts/slideLayout1.xml", "rIdM", TinyOoxml.REL_SLIDE_MASTER, "../slideMasters/slideMaster1.xml")
        }
        themeXml?.let { tiny.xml("ppt/theme/theme1.xml", null, it) }
        coreTitle?.let {
            tiny.xml(
                "docProps/core.xml", null,
                """<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" """ +
                    """xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>$it</dc:title></cp:coreProperties>""",
            )
            tiny.rel(null, "rId2", "http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties", "docProps/core.xml")
        }
        return tiny
    }

    fun open(limits: ParseLimits = ParseLimits.DEFAULT): PptxFlowDocument = runBlocking {
        PptxDocument.open(OpcPackage.open(build().source("t.pptx"), limits), limits, ProgressSink.NONE) as PptxFlowDocument
    }

    companion object {
        const val NS = """xmlns:a="${TinyOoxml.NS_A}" xmlns:r="${TinyOoxml.NS_R}" xmlns:p="${TinyOoxml.NS_P}""""
        const val CT_SLIDE = "application/vnd.openxmlformats-officedocument.presentationml.slide+xml"
        const val REL_COMMENTS = "${TinyOoxml.R}/comments"
        const val REL_DIAGRAM_DATA = "${TinyOoxml.R}/diagramData"
        const val REL_DIAGRAM_DRAWING = "http://schemas.microsoft.com/office/2007/relationships/diagramDrawing"
        const val REL_CORE = "http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties"

        private const val TREE_HEAD =
            """<p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/>"""

        /** 슬라이드 하나. [shapes] 는 `spTree` 안, [after] 는 `cSld` 뒤(전환·타이밍), [bg] 는 `cSld` 안 맨 앞. */
        fun sld(shapes: String, attrs: String = "", bg: String = "", after: String = "") =
            """<p:sld $NS $attrs><p:cSld>$bg<p:spTree>$TREE_HEAD$shapes</p:spTree></p:cSld>$after</p:sld>"""

        fun layout(shapes: String = LAYOUT_PHS, attrs: String = "", bg: String = "") =
            """<p:sldLayout $NS $attrs><p:cSld>$bg<p:spTree>$TREE_HEAD$shapes</p:spTree></p:cSld></p:sldLayout>"""

        fun master(
            shapes: String = MASTER_PHS,
            bg: String = """<p:bg><p:bgRef idx="1001"><a:schemeClr val="bg1"/></p:bgRef></p:bg>""",
            txStyles: String = TX_STYLES,
            clrMap: String = CLR_MAP,
        ) = """<p:sldMaster $NS><p:cSld>$bg<p:spTree>$TREE_HEAD$shapes</p:spTree></p:cSld>$clrMap$txStyles</p:sldMaster>"""

        fun xfrm(x: Long, y: Long, cx: Long, cy: Long, attrs: String = "") =
            """<a:xfrm $attrs><a:off x="$x" y="$y"/><a:ext cx="$cx" cy="$cy"/></a:xfrm>"""

        /** 도형 하나. [ph] 는 `<p:ph …/>` 통째로. */
        fun sp(
            id: Int,
            text: String? = null,
            ph: String = "",
            xfrm: String = "",
            spPrExtra: String = "",
            body: String = "<a:bodyPr/>",
            paras: String? = null,
            style: String = "",
            nvExtra: String = "",
        ): String {
            val txBody = when {
                paras != null -> "<p:txBody>$body<a:lstStyle/>$paras</p:txBody>"
                text != null -> "<p:txBody>$body<a:lstStyle/><a:p><a:r><a:rPr lang=\"ko-KR\"/><a:t>$text</a:t></a:r></a:p></p:txBody>"
                else -> ""
            }
            return """<p:sp><p:nvSpPr><p:cNvPr id="$id" name="Shape $id" $nvExtra/><p:cNvSpPr/><p:nvPr>$ph</p:nvPr></p:nvSpPr>""" +
                "<p:spPr>$xfrm$spPrExtra</p:spPr>$style$txBody</p:sp>"
        }

        fun pic(id: Int, rid: String, xfrm: String, blipExtra: String = "", nvPrExtra: String = "", linkAttr: String = "") =
            """<p:pic><p:nvPicPr><p:cNvPr id="$id" name="Picture $id" descr="설명"/><p:cNvPicPr/><p:nvPr>$nvPrExtra</p:nvPr></p:nvPicPr>""" +
                """<p:blipFill><a:blip ${if (rid.isEmpty()) "" else "r:embed=\"$rid\""} $linkAttr/>$blipExtra<a:stretch><a:fillRect/></a:stretch></p:blipFill>""" +
                "<p:spPr>$xfrm<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></p:spPr></p:pic>"

        const val CLR_MAP =
            """<p:clrMap bg1="lt1" tx1="dk1" bg2="lt2" tx2="dk2" accent1="accent1" accent2="accent2" accent3="accent3" """ +
                """accent4="accent4" accent5="accent5" accent6="accent6" hlink="hlink" folHlink="folHlink"/>"""

        /** 마스터의 개체 틀 — 제목·본문(idx 1)·날짜(idx 2). 자리는 여기에만 적혀 있다. */
        const val MASTER_PHS =
            """<p:sp><p:nvSpPr><p:cNvPr id="2" name="Title"/><p:cNvSpPr/><p:nvPr><p:ph type="title"/></p:nvPr></p:nvSpPr>""" +
                """<p:spPr><a:xfrm><a:off x="457200" y="274638"/><a:ext cx="8229600" cy="1143000"/></a:xfrm></p:spPr>""" +
                """<p:txBody><a:bodyPr anchor="ctr"/><a:lstStyle/><a:p><a:r><a:t>마스터 제목</a:t></a:r></a:p></p:txBody></p:sp>""" +
                """<p:sp><p:nvSpPr><p:cNvPr id="3" name="Body"/><p:cNvSpPr/><p:nvPr><p:ph type="body" idx="1"/></p:nvPr></p:nvSpPr>""" +
                """<p:spPr><a:xfrm><a:off x="457200" y="1600200"/><a:ext cx="8229600" cy="4525963"/></a:xfrm></p:spPr>""" +
                """<p:txBody><a:bodyPr/><a:lstStyle/><a:p><a:r><a:t>마스터 본문</a:t></a:r></a:p></p:txBody></p:sp>""" +
                """<p:sp><p:nvSpPr><p:cNvPr id="4" name="Date"/><p:cNvSpPr/><p:nvPr><p:ph type="dt" idx="2"/></p:nvPr></p:nvSpPr>""" +
                """<p:spPr><a:xfrm><a:off x="457200" y="6356350"/><a:ext cx="2133600" cy="365125"/></a:xfrm></p:spPr></p:sp>"""

        /** 레이아웃의 개체 틀 — 자리를 적지 않는다(마스터에서 온다). */
        const val LAYOUT_PHS =
            """<p:sp><p:nvSpPr><p:cNvPr id="2" name="Title 1"/><p:cNvSpPr/><p:nvPr><p:ph type="title"/></p:nvPr></p:nvSpPr>""" +
                """<p:spPr/><p:txBody><a:bodyPr/><a:lstStyle/><a:p><a:r><a:t>레이아웃 제목</a:t></a:r></a:p></p:txBody></p:sp>""" +
                """<p:sp><p:nvSpPr><p:cNvPr id="3" name="Content 2"/><p:cNvSpPr/><p:nvPr><p:ph idx="1"/></p:nvPr></p:nvSpPr>""" +
                """<p:spPr/><p:txBody><a:bodyPr/><a:lstStyle/><a:p><a:r><a:t>레이아웃 본문</a:t></a:r></a:p></p:txBody></p:sp>"""

        const val TX_STYLES =
            "<p:txStyles>" +
                """<p:titleStyle><a:lvl1pPr algn="ctr"><a:defRPr sz="4400"><a:solidFill><a:schemeClr val="tx1"/></a:solidFill></a:defRPr></a:lvl1pPr></p:titleStyle>""" +
                "<p:bodyStyle>" +
                """<a:lvl1pPr marL="342900" indent="-342900"><a:buFont typeface="Arial"/><a:buChar char="•"/><a:defRPr sz="3200"><a:solidFill><a:schemeClr val="tx1"/></a:solidFill></a:defRPr></a:lvl1pPr>""" +
                """<a:lvl2pPr marL="742950" indent="-285750"><a:buFont typeface="Arial"/><a:buChar char="–"/><a:defRPr sz="2800"><a:solidFill><a:schemeClr val="tx1"/></a:solidFill></a:defRPr></a:lvl2pPr>""" +
                "</p:bodyStyle>" +
                """<p:otherStyle><a:lvl1pPr><a:defRPr sz="1800"><a:solidFill><a:schemeClr val="tx1"/></a:solidFill></a:defRPr></a:lvl1pPr></p:otherStyle>""" +
                "</p:txStyles>"

        val THEME =
            """<a:theme xmlns:a="${TinyOoxml.NS_A}" name="T"><a:themeElements><a:clrScheme name="C">""" +
                """<a:dk1><a:sysClr val="windowText" lastClr="123456"/></a:dk1><a:lt1><a:sysClr val="window" lastClr="FFFFFF"/></a:lt1>""" +
                """<a:dk2><a:srgbClr val="1F497D"/></a:dk2><a:lt2><a:srgbClr val="EEECE1"/></a:lt2>""" +
                """<a:accent1><a:srgbClr val="4472C4"/></a:accent1><a:accent2><a:srgbClr val="C0504D"/></a:accent2>""" +
                """<a:accent3><a:srgbClr val="9BBB59"/></a:accent3><a:accent4><a:srgbClr val="8064A2"/></a:accent4>""" +
                """<a:accent5><a:srgbClr val="4BACC6"/></a:accent5><a:accent6><a:srgbClr val="F79646"/></a:accent6>""" +
                """<a:hlink><a:srgbClr val="0000FF"/></a:hlink><a:folHlink><a:srgbClr val="800080"/></a:folHlink></a:clrScheme>""" +
                """<a:fontScheme name="F"><a:majorFont><a:latin typeface="Calibri Light"/><a:ea typeface=""/><a:font script="Hang" typeface="맑은 고딕"/></a:majorFont>""" +
                """<a:minorFont><a:latin typeface="Calibri"/><a:ea typeface=""/><a:font script="Hang" typeface="맑은 고딕"/></a:minorFont></a:fontScheme>""" +
                """<a:fmtScheme name="M"><a:fillStyleLst/><a:lnStyleLst><a:ln w="6350"/><a:ln w="12700"/><a:ln w="19050"/></a:lnStyleLst></a:fmtScheme>""" +
                "</a:themeElements></a:theme>"
    }
}

/** 본문에서 글자만(글머리 제외). 문단은 줄바꿈 하나. */
internal fun textOf(html: String): String {
    val body = html.substringAfter("<body>").substringBefore("</body>")
    return body
        .replace(Regex("<span class=\"bu\"[^>]*>.*?</span>"), "")
        .replace("</p>", "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")
        .lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
}

/** [text] 를 담은 도형 상자(`class="sp"`)의 style. */
internal fun boxStyleOf(html: String, text: String): String {
    val at = html.indexOf(text)
    require(at >= 0) { "글자가 없다: $text" }
    val open = html.lastIndexOf("<div class=\"sp", at)
    require(open >= 0) { "상자가 없다: $text" }
    return html.substring(open).substringAfter("style=\"").substringBefore('"')
}

/** [text] 를 담은 조각(span)의 style. */
internal fun runStyleOf(html: String, text: String): String {
    val at = html.indexOf(">$text</span>")
    require(at >= 0) { "조각이 없다: $text" }
    val open = html.lastIndexOf("<span", at)
    return html.substring(open, at).substringAfter("style=\"").substringBefore('"')
}

/** [text] 를 담은 문단(p)의 style. */
internal fun paraStyleOf(html: String, text: String): String {
    val at = html.indexOf(text)
    require(at >= 0) { "글자가 없다: $text" }
    val open = html.lastIndexOf("<p", at)
    return html.substring(open, at).substringAfter("style=\"").substringBefore('"')
}

/** style 에서 속성 하나. 속성 값의 `&quot;` 는 풀어서 본다(그 `;` 로 선언이 갈리지 않게). */
internal fun prop(style: String, name: String): String? =
    style.replace("&quot;", "\"").split(';').map { it.trim() }.firstOrNull { it.startsWith("$name:") }?.substringAfter(':')
