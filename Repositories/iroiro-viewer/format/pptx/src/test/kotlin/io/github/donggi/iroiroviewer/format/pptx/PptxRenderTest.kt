package io.github.donggi.iroiroviewer.format.pptx

import io.github.donggi.iroiroviewer.format.UnsupportedFeatures
import io.github.donggi.iroiroviewer.format.opc.TinyOoxml
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 도형 하나하나를 어떻게 그리는가 — 묶음·표·그림·글머리·선·안전. */
class PptxRenderTest {

    private val mc = "xmlns:mc=\"http://schemas.openxmlformats.org/markup-compatibility/2006\""

    private fun render(shapes: String, setup: (Deck, String) -> Unit = { _, _ -> }): Pair<String, PptxFlowDocument> {
        val d = Deck()
        val name = d.slide(Deck.sld(shapes))
        setup(d, name)
        val doc = d.open()
        return doc.partHtml(0)!! to doc
    }

    private fun grp(off: Long, ext: Long, chOff: Long, chExt: Long, inner: String) =
        """<p:grpSp><p:nvGrpSpPr><p:cNvPr id="9" name="G"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr><a:xfrm>""" +
            """<a:off x="$off" y="$off"/><a:ext cx="$ext" cy="$ext"/><a:chOff x="$chOff" y="$chOff"/><a:chExt cx="$chExt" cy="$chExt"/>""" +
            "</a:xfrm></p:grpSpPr>$inner</p:grpSp>"

    /**
     * 슬라이드 번호 필드(`a:fld type="slidenum"`)는 **그 슬라이드의 번호**로 보인다. 마스터의 글상자에 든 필드는
     * 저장된 글이 '‹#›' 라 그대로 두면 모든 슬라이드에 '‹#›' 가 찍힌다(HCL 의 실세계 표본에서 보였다). 슬라이드
     * 자신의 필드에 적힌 글도 저장할 때의 번호일 뿐이다 — 차례가 바뀌면 틀린다.
     */
    @Test
    fun 슬라이드_번호_필드는_그_슬라이드의_번호로_보인다() {
        fun field(cached: String) =
            """<a:p><a:r><a:t>쪽 </a:t></a:r><a:fld id="{1}" type="slidenum"><a:rPr lang="ko-KR"/><a:t>$cached</a:t></a:fld></a:p>"""
        val boxOnMaster = Deck.sp(7, paras = field("‹#›"), xfrm = Deck.xfrm(0, 6400000, 914400, 300000), nvExtra = "")
        val d = Deck()
        d.masterXml = Deck.master(shapes = Deck.MASTER_PHS + boxOnMaster)
        d.presAttrs = "firstSlideNum=\"5\""
        d.slide(Deck.sld(Deck.sp(3, "첫 장")))
        d.slide(Deck.sld(Deck.sp(3, paras = field("9"), xfrm = Deck.xfrm(0, 0, 914400, 300000))))
        d.open().use { doc ->
            val first = doc.partHtml(0)!!
            val second = doc.partHtml(1)!!
            assertFalse("‹#›" in first || "‹#›" in second, first)
            assertTrue(">5<" in first, first)
            // 두 번째 장: 마스터의 글상자와 슬라이드 자신의 필드 둘 다 6 이다(적힌 '9' 가 아니다).
            assertEquals(2, Regex(">6<").findAll(second).count(), second)
            assertFalse(">9<" in second, second)
        }
    }

    @Test
    fun 묶음은_자식_좌표계를_자기_자리로_늘이고_줄인다() {
        // 자식 좌표계 0..9144000 을 묶음 상자 914400..5486400(폭 4572000)로 — 배율 0.5.
        val child = Deck.sp(3, "자식", xfrm = Deck.xfrm(4572000, 0, 1828800, 914400))
        val (html, doc) = render(grp(914400, 4572000, 0, 9144000, child))
        doc.use {
            val groupStyle = html.substringAfter("class=\"grp\" style=\"").substringBefore('"')
            assertEquals("calc(var(--u)*10)", prop(groupStyle, "left"))
            assertEquals("calc(var(--u)*50)", prop(groupStyle, "width"))
            val st = boxStyleOf(html, "자식")
            assertEquals("calc(var(--u)*25)", prop(st, "left"))
            assertEquals("calc(var(--u)*10)", prop(st, "width"))
            assertEquals("calc(var(--u)*5)", prop(st, "height"))
        }
    }

    @Test
    fun 겹친_묶음은_배율이_곱해진다() {
        val leaf = Deck.sp(4, "손자", xfrm = Deck.xfrm(9144000, 0, 3657600, 914400))
        val inner = grp(0, 9144000, 0, 18288000, leaf)
        val (html, doc) = render(grp(914400, 4572000, 0, 9144000, inner))
        doc.use {
            // 바깥 0.5 × 안쪽 0.5 = 0.25.
            val st = boxStyleOf(html, "손자")
            assertEquals("calc(var(--u)*25)", prop(st, "left"))
            assertEquals("calc(var(--u)*10)", prop(st, "width"))
        }
    }

    @Test
    fun 너무_깊은_묶음은_버리고_센다() {
        var tree = Deck.sp(5, "깊은곳", xfrm = Deck.xfrm(0, 0, 914400, 914400))
        repeat(DrawingParser.MAX_GROUP_DEPTH + 2) { tree = grp(0, 9144000, 0, 9144000, tree) }
        val (html, doc) = render(tree + Deck.sp(6, "얕은곳", xfrm = Deck.xfrm(0, 0, 914400, 914400)))
        doc.use {
            assertFalse("깊은곳" in html)
            assertTrue("얕은곳" in html)
            assertEquals(1, doc.unsupported.snapshot()[UnsupportedFeatures.SHAPE])
        }
    }

    @Test
    fun 표는_병합을_colspan_rowspan_으로() {
        fun tc(text: String, attrs: String = "", tcPr: String = "<a:tcPr/>") =
            """<a:tc $attrs><a:txBody><a:bodyPr/><a:lstStyle/><a:p><a:r><a:t>$text</a:t></a:r></a:p></a:txBody>$tcPr</a:tc>"""
        val tbl = """<a:tbl><a:tblPr firstRow="1" bandRow="1"/><a:tblGrid><a:gridCol w="914400"/><a:gridCol w="1828800"/><a:gridCol w="914400"/></a:tblGrid>""" +
            """<a:tr h="457200">${tc("가로병합", "gridSpan=\"2\"")}${tc("", "hMerge=\"1\"")}${tc("세로병합", "rowSpan=\"2\"")}</a:tr>""" +
            """<a:tr h="457200">${tc("빨강", tcPr = "<a:tcPr><a:solidFill><a:srgbClr val=\"FF0000\"/></a:solidFill></a:tcPr>")}${tc("보통")}${tc("", "vMerge=\"1\"")}</a:tr></a:tbl>"""
        val frame = """<p:graphicFrame><p:nvGraphicFramePr><p:cNvPr id="4" name="Table"/><p:cNvGraphicFramePr/><p:nvPr/></p:nvGraphicFramePr>""" +
            """<p:xfrm><a:off x="914400" y="914400"/><a:ext cx="3657600" cy="914400"/></p:xfrm><a:graphic>""" +
            """<a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/table">$tbl</a:graphicData></a:graphic></p:graphicFrame>"""
        val (html, doc) = render(frame)
        doc.use {
            assertEquals(4, Regex("<td").findAll(html).count(), html)
            assertTrue(Regex("<td colspan=\"2\"[^>]*>.*?가로병합").containsMatchIn(html), html)
            assertTrue(Regex("<td rowspan=\"2\"[^>]*>.*?세로병합").containsMatchIn(html), html)
            val red = html.substringBefore("빨강").substringAfterLast("<td")
            assertTrue("background-color:#ff0000" in red, red)
            assertTrue("<col style=\"width:calc(var(--u)*20)\"/>" in html, html)
            val tableStyle = html.substringAfter("class=\"tbl\" style=\"").substringBefore('"')
            assertEquals("calc(var(--u)*10)", prop(tableStyle, "left"))
            assertEquals("calc(var(--u)*40)", prop(tableStyle, "width"))
            // 표 글자는 마스터의 그 밖 서식(18pt).
            assertEquals("calc(var(--u)*2.5)", prop(runStyleOf(html, "보통"), "font-size"))
        }
    }

    @Test
    fun 기본_표_서식은_머리_행을_강조색으로() {
        val tbl = """<a:tbl><a:tblPr firstRow="1" bandRow="1"><a:tableStyleId>{5C22544A-7EE6-4342-B048-85BDC9FD1C3A}</a:tableStyleId></a:tblPr>""" +
            """<a:tblGrid><a:gridCol w="914400"/></a:tblGrid>""" +
            """<a:tr h="457200"><a:tc><a:txBody><a:bodyPr/><a:lstStyle/><a:p><a:r><a:t>머리</a:t></a:r></a:p></a:txBody><a:tcPr/></a:tc></a:tr>""" +
            """<a:tr h="457200"><a:tc><a:txBody><a:bodyPr/><a:lstStyle/><a:p><a:r><a:t>몸통</a:t></a:r></a:p></a:txBody><a:tcPr/></a:tc></a:tr></a:tbl>"""
        val frame = """<p:graphicFrame><p:nvGraphicFramePr><p:cNvPr id="4" name="Table"/><p:cNvGraphicFramePr/><p:nvPr/></p:nvGraphicFramePr>""" +
            """<p:xfrm><a:off x="0" y="0"/><a:ext cx="914400" cy="914400"/></p:xfrm><a:graphic>""" +
            """<a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/table">$tbl</a:graphicData></a:graphic></p:graphicFrame>"""
        val (html, doc) = render(frame)
        doc.use {
            val head = html.substringBefore("머리").substringAfterLast("<td")
            assertTrue("background-color:#4472c4" in head, head)
            assertEquals("#ffffff", prop(runStyleOf(html, "머리"), "color"))
            assertEquals("bold", prop(runStyleOf(html, "머리"), "font-weight"))
            // 몸통 첫 줄은 줄무늬(tint 40%) — 머리 색보다 옅다.
            val bodyCell = html.substringBefore("몸통").substringAfterLast("<td")
            assertFalse("background-color:#4472c4" in bodyCell)
            assertTrue("background-color:#" in bodyCell, bodyCell)
        }
    }

    @Test
    fun 그림은_슬라이드의_관계로_풀고_자원으로_내준다() {
        val (html, doc) = render(Deck.pic(5, "rIdImg", Deck.xfrm(914400, 914400, 1828800, 914400))) { d, s ->
            d.tiny.bytes("ppt/media/image1.png", null, PptxDocumentTest.PNG)
            d.tiny.rel(s, "rIdImg", TinyOoxml.REL_IMAGE, "../media/image1.png")
        }
        doc.use {
            assertTrue("<img src=\"ppt/media/image1.png\" alt=\"설명\"" in html, html)
            val bytes = doc.openResource("ppt/media/image1.png")!!.use { it.readBytes() }
            assertEquals(PptxDocumentTest.PNG.toList(), bytes.toList())
            assertEquals("image/png", doc.mediaTypeOf("ppt/media/image1.png"))
            assertTrue(doc.unsupported.isEmpty, doc.unsupported.snapshot().toString())
        }
    }

    @Test
    fun 그릴_수_없는_그림과_바깥_그림을_센다() {
        val shapes = Deck.pic(5, "rIdEmf", Deck.xfrm(0, 0, 914400, 914400)) +
            Deck.pic(6, "", Deck.xfrm(0, 0, 914400, 914400), linkAttr = "r:link=\"rIdExt\"") +
            Deck.pic(7, "rIdGone", Deck.xfrm(0, 0, 914400, 914400))
        val (html, doc) = render(shapes) { d, s ->
            d.tiny.bytes("ppt/media/image2.emf", null, byteArrayOf(1, 0, 0, 0))
            d.tiny.rel(s, "rIdEmf", TinyOoxml.REL_IMAGE, "../media/image2.emf")
            d.tiny.rel(s, "rIdExt", TinyOoxml.REL_IMAGE, "https://example.com/x.png", external = true)
        }
        doc.use {
            assertFalse("<img" in html, html)
            assertFalse("example.com" in html)
            assertEquals(3, Regex("class=\"sp missing\"").findAll(html).count())
            val counts = doc.unsupported.snapshot()
            assertEquals(2, counts[UnsupportedFeatures.UNSUPPORTED_IMAGE])
            assertEquals(1, counts[UnsupportedFeatures.LINKED_FILE])
        }
    }

    @Test
    fun 그림_자르기는_상자를_넘치게_키워_옮긴다() {
        val (html, doc) = render(Deck.pic(5, "rIdImg", Deck.xfrm(0, 0, 914400, 914400), blipExtra = "<a:srcRect l=\"25000\" r=\"25000\"/>")) { d, s ->
            d.tiny.bytes("ppt/media/image1.png", null, PptxDocumentTest.PNG)
            d.tiny.rel(s, "rIdImg", TinyOoxml.REL_IMAGE, "../media/image1.png")
        }
        doc.use {
            val img = html.substringAfter("<img src=\"ppt/media/image1.png\"").substringBefore(">")
            assertTrue("left:-50%" in img && "width:200%" in img, img)
        }
    }

    @Test
    fun 동영상은_포스터를_그리고_삽입_개체로_센다() {
        val (html, doc) = render(Deck.pic(5, "rIdImg", Deck.xfrm(0, 0, 914400, 914400), nvPrExtra = "<a:videoFile r:link=\"rIdVid\"/>")) { d, s ->
            d.tiny.bytes("ppt/media/image1.png", null, PptxDocumentTest.PNG)
            d.tiny.rel(s, "rIdImg", TinyOoxml.REL_IMAGE, "../media/image1.png")
        }
        doc.use {
            assertTrue("ppt/media/image1.png" in html)
            assertEquals(1, doc.unsupported.snapshot()[UnsupportedFeatures.EMBEDDED_OBJECT])
        }
    }

    @Test
    fun 차트는_세고_빈_상자를_둔다() {
        val frame = """<p:graphicFrame><p:nvGraphicFramePr><p:cNvPr id="4" name="Chart"/><p:cNvGraphicFramePr/><p:nvPr/></p:nvGraphicFramePr>""" +
            """<p:xfrm><a:off x="914400" y="0"/><a:ext cx="914400" cy="914400"/></p:xfrm><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/chart">""" +
            """<c:chart xmlns:c="http://schemas.openxmlformats.org/drawingml/2006/chart" r:id="rIdCh"/></a:graphicData></a:graphic></p:graphicFrame>"""
        val (html, doc) = render(frame)
        doc.use {
            assertEquals(1, doc.unsupported.snapshot()[UnsupportedFeatures.CHART])
            val box = html.substringAfter("class=\"sp chart\" style=\"").substringBefore('"')
            assertEquals("calc(var(--u)*10)", prop(box, "left"))
        }
    }

    @Test
    fun SmartArt_는_캐시된_그림이_있으면_그_글자를_그린다() {
        val frame = """<p:graphicFrame><p:nvGraphicFramePr><p:cNvPr id="4" name="Diagram"/><p:cNvGraphicFramePr/><p:nvPr/></p:nvGraphicFramePr>""" +
            """<p:xfrm><a:off x="914400" y="914400"/><a:ext cx="4572000" cy="914400"/></p:xfrm><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/diagram">""" +
            """<dgm:relIds xmlns:dgm="http://schemas.openxmlformats.org/drawingml/2006/diagram" r:dm="rIdDm" r:lo="rIdLo" r:qs="rIdQs" r:cs="rIdCs"/></a:graphicData></a:graphic></p:graphicFrame>"""
        val drawing = """<dsp:drawing xmlns:dsp="http://schemas.microsoft.com/office/drawing/2008/diagram" xmlns:a="${TinyOoxml.NS_A}"><dsp:spTree>""" +
            """<dsp:nvGrpSpPr><dsp:cNvPr id="0" name=""/><dsp:cNvGrpSpPr/></dsp:nvGrpSpPr><dsp:grpSpPr/>""" +
            """<dsp:sp modelId="{1}"><dsp:nvSpPr><dsp:cNvPr id="0" name=""/><dsp:cNvSpPr/></dsp:nvSpPr><dsp:spPr>""" +
            """<a:xfrm><a:off x="914400" y="0"/><a:ext cx="914400" cy="914400"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom>""" +
            """<a:solidFill><a:schemeClr val="accent1"/></a:solidFill></dsp:spPr>""" +
            """<dsp:txBody><a:bodyPr/><a:lstStyle/><a:p><a:r><a:t>스마트아트글</a:t></a:r></a:p></dsp:txBody></dsp:sp></dsp:spTree></dsp:drawing>"""
        val dataWithExt = """<dgm:dataModel xmlns:dgm="http://schemas.openxmlformats.org/drawingml/2006/diagram" xmlns:a="${TinyOoxml.NS_A}">""" +
            """<dgm:ptLst/><dgm:extLst><a:ext uri="http://schemas.microsoft.com/office/drawing/2008/diagram">""" +
            """<dsp:dataModelExt xmlns:dsp="http://schemas.microsoft.com/office/drawing/2008/diagram" relId="rIdDw" minVer="http://schemas.openxmlformats.org/drawingml/2006/diagram"/>""" +
            "</a:ext></dgm:extLst></dgm:dataModel>"
        val (html, doc) = render(frame) { d, s ->
            d.tiny.xml("ppt/diagrams/data1.xml", null, dataWithExt)
            d.tiny.xml("ppt/diagrams/drawing1.xml", null, drawing)
            d.tiny.rel(s, "rIdDm", Deck.REL_DIAGRAM_DATA, "../diagrams/data1.xml")
            d.tiny.rel(s, "rIdDw", Deck.REL_DIAGRAM_DRAWING, "../diagrams/drawing1.xml")
        }
        doc.use {
            assertTrue("스마트아트글" in textOf(html), html)
            // 그림의 좌표는 틀 기준이다 — 틀(10vw) + 도형(10vw).
            val groupStyle = html.substringAfter("class=\"grp\" style=\"").substringBefore('"')
            assertEquals("calc(var(--u)*10)", prop(groupStyle, "left"))
            assertEquals("calc(var(--u)*10)", prop(boxStyleOf(html, "스마트아트글"), "left"))
            assertNull(doc.unsupported.snapshot()[UnsupportedFeatures.SMART_ART])
        }
        val (bare, doc2) = render(frame)
        doc2.use {
            assertEquals(1, doc2.unsupported.snapshot()[UnsupportedFeatures.SMART_ART])
            assertTrue("class=\"sp missing\"" in bare)
        }
    }

    @Test
    fun 대체_내용은_Fallback_을_고른다() {
        val shapes = """<mc:AlternateContent $mc><mc:Choice Requires="a14">${Deck.sp(2, "새기능", xfrm = Deck.xfrm(0, 0, 914400, 914400))}</mc:Choice>""" +
            """<mc:Fallback>${Deck.sp(3, "대체그림", xfrm = Deck.xfrm(0, 0, 914400, 914400))}</mc:Fallback></mc:AlternateContent>"""
        val (html, doc) = render(shapes)
        doc.use {
            assertEquals("대체그림", textOf(html))
        }
    }

    @Test
    fun 글머리는_마스터에서_오고_buNone_과_빈_문단에는_없다() {
        val paras = """<a:p><a:r><a:t>점붙은글</a:t></a:r></a:p><a:p><a:pPr><a:buNone/></a:pPr><a:r><a:t>점없는글</a:t></a:r></a:p>""" +
            """<a:p><a:endParaRPr/></a:p><a:p><a:pPr lvl="1"/><a:r><a:t>둘째단계</a:t></a:r></a:p>"""
        val (html, doc) = render(Deck.sp(2, ph = """<p:ph idx="1"/>""", paras = paras))
        doc.use {
            val bullets = Regex("<span class=\"bu\"[^>]*>([^<]*)</span>").findAll(html).map { it.groupValues[1] }.toList()
            assertEquals(listOf("•", "–"), bullets)
            // 내어쓰기(indent −342900)만큼 글머리가 자리를 차지한다.
            val bu = html.substringAfter("<span class=\"bu\" style=\"").substringBefore('"')
            assertEquals("calc(var(--u)*3.75)", prop(bu, "min-width"))
            assertEquals("calc(var(--u)*-3.75)", prop(paraStyleOf(html, "점붙은글"), "text-indent"))
        }
    }

    @Test
    fun 자동_번호는_단계별로_센다() {
        fun p(text: String, lvl: Int = 0, bu: String = "<a:buAutoNum type=\"arabicPeriod\" startAt=\"3\"/>") =
            """<a:p><a:pPr lvl="$lvl">$bu</a:pPr><a:r><a:t>$text</a:t></a:r></a:p>"""
        val paras = p("가") + p("나") + p("가가", 1, "<a:buAutoNum type=\"alphaLcPeriod\"/>") + p("나나", 1, "<a:buAutoNum type=\"alphaLcPeriod\"/>") +
            p("다") + "<a:p><a:endParaRPr/></a:p>" + p("라") + p("끊김", 0, "<a:buNone/>") + p("다시")
        val (html, doc) = render(Deck.sp(2, xfrm = Deck.xfrm(0, 0, 914400, 914400), paras = paras))
        doc.use {
            val numbers = Regex("<span class=\"bu\"[^>]*>([^<]*)</span>").findAll(html).map { it.groupValues[1] }.toList()
            // 빈 문단은 세지도 끊지도 않는다. 번호 없는 문단은 끊는다.
            assertEquals(listOf("3.", "4.", "a.", "b.", "5.", "6.", "3."), numbers)
        }
    }

    @Test
    fun 슬라이드_사이_링크는_살리고_바깥_링크는_글자만() {
        val d = Deck()
        val s1 = d.slide(
            Deck.sld(
                Deck.sp(
                    2, xfrm = Deck.xfrm(0, 0, 914400, 914400),
                    paras = """<a:p><a:r><a:rPr><a:hlinkClick r:id="rIdJump" action="ppaction://hlinksldjump"/></a:rPr><a:t>다음으로</a:t></a:r>""" +
                        """<a:r><a:rPr><a:hlinkClick r:id="rIdWeb"/></a:rPr><a:t>바깥으로</a:t></a:r></a:p>""",
                ),
            ),
        )
        val s2 = d.slide(Deck.sld(Deck.sp(2, "도착", ph = """<p:ph type="title"/>""")))
        d.tiny.rel(s1, "rIdJump", TinyOoxml.REL_SLIDE, "/$s2")
        d.tiny.rel(s1, "rIdWeb", TinyOoxml.REL_HYPERLINK, "https://example.com/", external = true)
        d.open().use { doc ->
            val html = doc.partHtml(0)!!
            assertTrue(Regex("<a href=\"~part-1.html\"><span[^>]*>다음으로</span></a>").containsMatchIn(html), html)
            assertFalse("example.com" in html)
            val web = runStyleOf(html, "바깥으로")
            assertEquals("underline", prop(web, "text-decoration"))
            assertEquals("#0000ff", prop(web, "color"))
            assertEquals(1, doc.partIndexOf("~part-1.html"))
            // 버린 바깥 링크는 센다(다른 형식에서 위생기가 세는 것과 같은 종류). 슬라이드 사이 링크는 세지 않는다.
            assertEquals(1, doc.unsupported.snapshot()[UnsupportedFeatures.REMOTE_REFERENCE])
        }
    }

    @Test
    fun 자동_맞춤은_글자를_줄인다() {
        val (html, doc) = render(
            Deck.sp(2, "줄인글", xfrm = Deck.xfrm(0, 0, 914400, 914400), body = "<a:bodyPr><a:normAutofit fontScale=\"50000\" lnSpcReduction=\"20000\"/></a:bodyPr>"),
        )
        doc.use {
            // 18pt 의 절반.
            assertEquals("calc(var(--u)*1.25)", prop(runStyleOf(html, "줄인글"), "font-size"))
            assertEquals("0.96", prop(paraStyleOf(html, "줄인글"), "line-height"))
        }
    }

    @Test
    fun 모양은_둥근_모서리_타원_다각형으로_그리고_모르는_것은_센다() {
        fun geo(id: Int, text: String, prst: String, fill: String = "<a:solidFill><a:srgbClr val=\"00FF00\"/></a:solidFill>") =
            Deck.sp(id, text, xfrm = Deck.xfrm(0, 0, 914400, 914400), spPrExtra = "<a:prstGeom prst=\"$prst\"><a:avLst/></a:prstGeom>$fill")
        val shapes = geo(2, "둥근", "roundRect") + geo(3, "타원", "ellipse") + geo(4, "세모", "triangle") +
            geo(5, "별", "star5") + geo(6, "글상자", "star5", fill = "<a:noFill/>")
        val (html, doc) = render(shapes)
        doc.use {
            // 한 변(10vw) × 16.667%.
            assertEquals("calc(var(--u)*1.6667)", prop(boxStyleOf(html, "둥근"), "border-radius"))
            assertEquals("50%", prop(boxStyleOf(html, "타원"), "border-radius"))
            val tri = html.substringBefore("세모")
            assertTrue("clip-path:polygon(50% 0,100% 100%,0 100%)" in tri.substring(tri.lastIndexOf("class=\"sp\"")), tri)
            // 보이는 별 하나만 센다 — 채우기도 선도 없는 글상자는 모양이 보이지 않는다.
            assertEquals(1, doc.unsupported.snapshot()[UnsupportedFeatures.SHAPE])
        }
    }

    @Test
    fun 연결선은_대각선으로_그린다() {
        val line = """<p:cxnSp><p:nvCxnSpPr><p:cNvPr id="7" name="L"/><p:cNvCxnSpPr/><p:nvPr/></p:nvCxnSpPr><p:spPr>""" +
            Deck.xfrm(914400, 914400, 914400, 914400, "flipV=\"1\"") +
            """<a:prstGeom prst="straightConnector1"><a:avLst/></a:prstGeom><a:ln w="12700"><a:solidFill><a:srgbClr val="FF0000"/></a:solidFill></a:ln></p:spPr></p:cxnSp>"""
        val bent = line.replace("straightConnector1", "bentConnector3")
        val (html, doc) = render(line + bent)
        doc.use {
            val st = html.substringAfter("class=\"ln\" style=\"").substringBefore('"')
            // 상하 뒤집기 — 왼쪽 아래(10vw, 20vw)에서 오른쪽 위로.
            assertEquals("calc(var(--u)*10)", prop(st, "left"))
            assertEquals("calc(var(--u)*20)", prop(st, "top"))
            assertEquals("calc(var(--u)*14.1421)", prop(st, "width"))
            assertEquals("rotate(-45deg)", prop(st, "transform"))
            assertTrue(prop(st, "border-top")!!.endsWith("solid #ff0000"))
            assertEquals(1, doc.unsupported.snapshot()[UnsupportedFeatures.SHAPE])
        }
    }

    @Test
    fun 도형_서식_참조가_채우기_선_글자색을_준다() {
        // 파워포인트 기본 도형 — 채우기 accent1, 선은 accent1 의 50% 음영(테마 선 목록 2번 12700), 글자는 lt1.
        val style = """<p:style><a:lnRef idx="2"><a:schemeClr val="accent1"><a:shade val="50000"/></a:schemeClr></a:lnRef>""" +
            """<a:fillRef idx="1"><a:schemeClr val="accent1"/></a:fillRef><a:effectRef idx="0"><a:schemeClr val="accent1"/></a:effectRef>""" +
            """<a:fontRef idx="minor"><a:schemeClr val="lt1"/></a:fontRef></p:style>"""
        val (html, doc) = render(
            Deck.sp(2, "기본도형", xfrm = Deck.xfrm(0, 0, 914400, 914400), spPrExtra = "<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom>", style = style),
        )
        doc.use {
            val box = boxStyleOf(html, "기본도형")
            assertEquals("#4472c4", prop(box, "background-color"))
            assertTrue(prop(box, "border")!!.startsWith("calc(var(--u)*0.1389) solid #"), box)
            val run = runStyleOf(html, "기본도형")
            assertEquals("#ffffff", prop(run, "color"))
            // 테마의 부 글꼴(라틴 Calibri, 한글 맑은 고딕).
            assertEquals("\"Calibri\",\"맑은 고딕\"", prop(run, "font-family"))
        }
    }

    @Test
    fun 세로쓰기와_줄바꿈_없음() {
        val (html, doc) = render(
            Deck.sp(2, "세로글", xfrm = Deck.xfrm(0, 0, 914400, 914400), body = "<a:bodyPr vert=\"eaVert\" wrap=\"none\" anchor=\"b\"/>") +
                Deck.sp(3, "눕힌글", xfrm = Deck.xfrm(0, 0, 914400, 914400), body = "<a:bodyPr vert=\"vert270\"/>"),
        )
        doc.use {
            val tx = html.substringBefore("세로글").let { it.substring(it.lastIndexOf("class=\"tx\"")) }
            assertTrue("writing-mode:vertical-rl" in tx && "justify-content:flex-end" in tx, tx)
            assertEquals("pre", prop(paraStyleOf(html, "세로글"), "white-space"))
            assertEquals(1, doc.unsupported.snapshot()[UnsupportedFeatures.TEXT_EFFECT])
        }
    }

    @Test
    fun 숨긴_도형은_묶음_안에서도_그리지_않는다() {
        val hidden = Deck.sp(2, "숨은도형", xfrm = Deck.xfrm(0, 0, 914400, 914400), nvExtra = "hidden=\"1\"")
        val (html, doc) = render(
            hidden + grp(0, 9144000, 0, 9144000, hidden.replace("숨은도형", "숨은자식")) +
                Deck.sp(3, "보이는도형", xfrm = Deck.xfrm(0, 0, 914400, 914400), nvExtra = "hidden=\"0\""),
        )
        doc.use {
            assertEquals("보이는도형", textOf(html))
        }
    }

    @Test
    fun 위첨자와_아래첨자는_작게_올리고_내린다() {
        val paras = """<a:p><a:r><a:t>바탕</a:t></a:r><a:r><a:rPr baseline="30000"/><a:t>위</a:t></a:r>""" +
            """<a:r><a:rPr baseline="-25000"/><a:t>아래</a:t></a:r></a:p>"""
        val (html, doc) = render(Deck.sp(2, xfrm = Deck.xfrm(0, 0, 914400, 914400), paras = paras))
        doc.use {
            assertNull(prop(runStyleOf(html, "바탕"), "vertical-align"))
            assertEquals("super", prop(runStyleOf(html, "위"), "vertical-align"))
            assertEquals("sub", prop(runStyleOf(html, "아래"), "vertical-align"))
            // 18pt 의 2/3 = 12pt.
            assertEquals("calc(var(--u)*1.6667)", prop(runStyleOf(html, "위"), "font-size"))
        }
    }

    @Test
    fun 글상자_여백은_위_오른쪽_아래_왼쪽_순서로() {
        fun txStyle(html: String, text: String): String {
            val before = html.substringBefore(text)
            return before.substring(before.lastIndexOf("class=\"tx\"")).substringAfter("style=\"").substringBefore('"')
        }
        val (html, doc) = render(
            Deck.sp(2, "여백글", xfrm = Deck.xfrm(0, 0, 914400, 914400), body = """<a:bodyPr lIns="914400" tIns="0" rIns="457200" bIns="91440"/>""") +
                Deck.sp(3, "기본여백", xfrm = Deck.xfrm(0, 0, 914400, 914400)),
        )
        doc.use {
            assertEquals("calc(var(--u)*0) calc(var(--u)*5) calc(var(--u)*1) calc(var(--u)*10)", prop(txStyle(html, "여백글"), "padding"))
            // 적지 않으면 좌우 0.1 인치, 위아래 0.05 인치.
            assertEquals("calc(var(--u)*0.5) calc(var(--u)*1) calc(var(--u)*0.5) calc(var(--u)*1)", prop(txStyle(html, "기본여백"), "padding"))
        }
    }

    @Test
    fun 필드와_줄바꿈과_회전() {
        val paras = """<a:p><a:r><a:t>앞</a:t></a:r><a:br/><a:fld id="{1}" type="slidenum"><a:t>7</a:t></a:fld></a:p>"""
        val (html, doc) = render(Deck.sp(2, xfrm = Deck.xfrm(0, 0, 914400, 914400, "rot=\"5400000\""), paras = paras))
        doc.use {
            // 슬라이드 번호 필드는 적힌 '7' 이 아니라 이 슬라이드의 번호(1)로 보인다(`슬라이드_번호_필드는_…`).
            assertTrue(Regex("앞</span><br/?><span[^>]*>1</span>").containsMatchIn(html), html)
            assertEquals("rotate(90deg)", prop(boxStyleOf(html, "앞"), "transform"))
        }
    }

    @Test
    fun 일본어_중국어_조각에는_언어를_단다() {
        val paras = """<a:p><a:r><a:rPr lang="ja-JP"/><a:t>日本語</a:t></a:r><a:r><a:rPr lang="zh-Hant-TW"/><a:t>漢字</a:t></a:r>""" +
            """<a:r><a:rPr lang="ko-KR"/><a:t>한국어</a:t></a:r><a:r><a:rPr lang="ja&quot; onclick=&quot;x"/><a:t>깨진태그</a:t></a:r></a:p>"""
        val (html, doc) = render(Deck.sp(2, xfrm = Deck.xfrm(0, 0, 914400, 914400), paras = paras))
        doc.use {
            assertTrue(Regex("<span lang=\"ja-JP\"[^>]*>日本語</span>").containsMatchIn(html), html)
            assertTrue(Regex("<span lang=\"zh-Hant-TW\"[^>]*>漢字</span>").containsMatchIn(html), html)
            // 한국어는 화면의 기본 언어라 달지 않는다. 모양이 틀린 태그는 버린다.
            assertTrue(Regex("<span style=\"[^\"]*\">한국어</span>").containsMatchIn(html), html)
            assertTrue(Regex("<span style=\"[^\"]*\">깨진태그</span>").containsMatchIn(html), html)
            assertFalse("onclick" in html)
        }
    }

    @Test
    fun 문서의_글자는_태그가_되지_못한다() {
        val evil = Deck.sp(
            2, xfrm = Deck.xfrm(0, 0, 914400, 914400),
            paras = """<a:p><a:r><a:rPr><a:solidFill><a:srgbClr val="red;background:url(https://x)"/></a:solidFill>""" +
                """<a:latin typeface="&quot;;background:url(https://x);&quot;"/></a:rPr><a:t>&lt;script&gt;alert(1)&lt;/script&gt;</a:t></a:r></a:p>""",
        )
        val (html, doc) = render(evil)
        doc.use {
            assertFalse("<script" in html)
            assertTrue("&lt;script&gt;" in html)
            assertFalse("url(" in html.substringAfter("<body>"))
            assertFalse("https://x" in html)
        }
    }

    @Test
    fun 그리기_상한에_닿으면_자르고_알린다() {
        val many = (1..200).joinToString("") { Deck.sp(it + 1, "도형$it", xfrm = Deck.xfrm(0, 0, 914400, 914400)) }
        val d = Deck()
        d.slide(Deck.sld(many))
        d.open().use { doc ->
            val slide = DrawingParser(
                io.github.donggi.iroiroviewer.safety.SafeXml.newParser(
                    Deck.sld(many).byteInputStream(), null, io.github.donggi.iroiroviewer.safety.ParseLimits.DEFAULT,
                ),
                io.github.donggi.iroiroviewer.safety.ParseLimits.DEFAULT,
            ).parsePart("ppt/slides/slide1.xml")
            val host = object : RenderHost {
                override fun relationship(source: String, id: String) = null
                override fun canonical(name: String): String? = null
                override fun isDisplayableImage(name: String) = false
                override fun slideIndexOf(partName: String) = -1
                override fun partPath(index: Int) = "~part-$index.html"
                override fun diagramDrawing(source: String, dataRelId: String): PartModel? = null
            }
            val r = SlideRenderer(host, 9_144_000, 6_858_000, slide, null, null, null, null, maxChars = 4_000)
            val html = r.render()
            assertTrue(r.truncated)
            assertTrue(html.endsWith("</div>"))
            assertFalse("도형200" in html)
            assertNotNull(doc.partHtml(0))
        }
    }
}
