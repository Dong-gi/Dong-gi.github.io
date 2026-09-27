package io.github.donggi.iroiroviewer.format.pptx

import io.github.donggi.iroiroviewer.format.FlowWarnings
import io.github.donggi.iroiroviewer.format.UnsupportedFeatures
import io.github.donggi.iroiroviewer.format.opc.TinyOoxml
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 문서가 적는 **수**가 여는 시간·그리는 시간·메모리를 부풀리지 못한다 — 적대적 검토가 잡은 것들.
 *
 * 부분 하나는 32MB 까지 들어오고 관계 파일 하나는 5만 줄까지 들어온다. 그 자체는 상한 안이지만
 * **같은 것을 되풀이해 가리키면** 읽는 일이 곱절로 늘었다. 시험은 되도록 시간이 아니라 결과
 * (센 수·그린 수)로 가린다.
 */
class PptxLimitsTest {

    private val title = """<p:ph type="title"/>"""

    private fun diagramFrame(dataRel: String) =
        """<p:graphicFrame><p:nvGraphicFramePr><p:cNvPr id="4" name="Diagram"/><p:cNvGraphicFramePr/><p:nvPr/></p:nvGraphicFramePr>""" +
            """<p:xfrm><a:off x="0" y="0"/><a:ext cx="914400" cy="914400"/></p:xfrm><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/diagram">""" +
            """<dgm:relIds xmlns:dgm="http://schemas.openxmlformats.org/drawingml/2006/diagram" r:dm="$dataRel" r:lo="rIdLo" r:qs="rIdQs" r:cs="rIdCs"/></a:graphicData></a:graphic></p:graphicFrame>"""

    private fun drawing(text: String) =
        """<dsp:drawing xmlns:dsp="http://schemas.microsoft.com/office/drawing/2008/diagram" xmlns:a="${TinyOoxml.NS_A}"><dsp:spTree>""" +
            """<dsp:nvGrpSpPr><dsp:cNvPr id="0" name=""/><dsp:cNvGrpSpPr/></dsp:nvGrpSpPr><dsp:grpSpPr/>""" +
            """<dsp:sp modelId="{1}"><dsp:nvSpPr><dsp:cNvPr id="0" name=""/><dsp:cNvSpPr/></dsp:nvSpPr><dsp:spPr>""" +
            """<a:xfrm><a:off x="0" y="0"/><a:ext cx="914400" cy="914400"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></dsp:spPr>""" +
            """<dsp:txBody><a:bodyPr/><a:lstStyle/><a:p><a:r><a:t>$text</a:t></a:r></a:p></dsp:txBody></dsp:sp></dsp:spTree></dsp:drawing>"""

    private val dataModel = """<dgm:dataModel xmlns:dgm="http://schemas.openxmlformats.org/drawingml/2006/diagram"><dgm:ptLst/></dgm:dataModel>"""

    @Test
    fun 같은_메모_부분은_문서_전체에서_한_번만_센다() {
        // 관계 3,000 개가 메모 두 개짜리 부분 하나를 가리킨다. 전에는 관계마다 그 부분을 다시 읽어
        // 6,000 으로 셌고, 부분이 크면 여는 데만 수십 초(시간 상한도 끊지 못하는 자리)였다.
        val d = Deck()
        val s1 = d.slide(Deck.sld(Deck.sp(2, "가", ph = title)))
        val s2 = d.slide(Deck.sld(Deck.sp(2, "나", ph = title)))
        d.tiny.xml(
            "ppt/comments/comment1.xml", null,
            """<p:cmLst ${Deck.NS}><p:cm authorId="0" idx="1"><p:text>하나</p:text></p:cm><p:cm authorId="0" idx="2"><p:text>둘</p:text></p:cm></p:cmLst>""",
        )
        repeat(3_000) { d.tiny.rel(s1, "rIdC$it", Deck.REL_COMMENTS, "../comments/comment1.xml") }
        d.tiny.rel(s2, "rIdC", Deck.REL_COMMENTS, "../comments/comment1.xml")
        d.open().use { doc ->
            assertEquals(2, doc.unsupported.snapshot()[UnsupportedFeatures.COMMENT])
        }
    }

    @Test
    fun 슬라이드의_관계_파일이_깨져도_문서는_열린다() {
        // 깊이 상한에 걸리는 관계 파일. 여는 동안(메모를 셀 때) 이것이 던지면 문서 전체가 열리지 않았다.
        val bomb = "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<x>".repeat(400) + "</x>".repeat(400) + "</Relationships>"
        val d = Deck()
        d.slide(Deck.sld(Deck.sp(2, "멀쩡한", ph = title)))
        d.slide(Deck.sld(Deck.sp(2, "관계가 깨진", ph = title)), withLayout = false)
        d.tiny.xml("ppt/slides/_rels/slide2.xml.rels", null, bomb)
        d.open().use { doc ->
            assertEquals(2, doc.parts.size)
            assertEquals("관계가 깨진", doc.parts[1].label)
            assertTrue("멀쩡한" in textOf(doc.partHtml(0)!!))
            // 그 슬라이드는 그릴 때 같은 관계를 읽다가 그 부분만 실패한다.
            assertTrue("part-failed" in doc.partHtml(1)!!)
            assertTrue(doc.warnings.any { it.code == FlowWarnings.TRUNCATED && it.detail == "관계가 깨진" }, doc.warnings.toString())
        }
    }

    @Test
    fun 같은_SmartArt_그림은_한_번_읽어_모든_틀에_그린다() {
        // 틀 20 개가 같은 데이터 부분을 가리킨다. 읽는 횟수 상한(8)보다 많아도 한 번만 읽으므로 전부 그린다.
        val n = 20
        val d = Deck()
        val s = d.slide(Deck.sld(diagramFrame("rIdDm").repeat(n)))
        d.tiny.xml("ppt/diagrams/data1.xml", null, dataModel)
        d.tiny.xml("ppt/diagrams/drawing1.xml", null, drawing("공유그림"))
        d.tiny.rel(s, "rIdDm", Deck.REL_DIAGRAM_DATA, "../diagrams/data1.xml")
        d.tiny.rel(s, "rIdDw", Deck.REL_DIAGRAM_DRAWING, "../diagrams/drawing1.xml")
        d.open().use { doc ->
            val html = doc.partHtml(0)!!
            assertEquals(n, Regex("공유그림").findAll(html).count())
            assertNull(doc.unsupported.snapshot()[UnsupportedFeatures.SMART_ART])
        }
    }

    @Test
    fun 한_장에_서로_다른_SmartArt_가_너무_많으면_나머지는_센다() {
        // 서로 다른 데이터 부분 열 개. 부분 하나가 32MB 까지 들어오므로 한 번 그리는 데 읽는 수를 묶는다.
        val n = PptxFlowDocument.MAX_DIAGRAM_READS + 2
        val d = Deck()
        val s = d.slide(Deck.sld((1..n).joinToString("") { diagramFrame("rIdDm$it") }))
        for (i in 1..n) {
            d.tiny.xml("ppt/diagrams/data$i.xml", null, dataModel)
            d.tiny.xml("ppt/diagrams/drawing$i.xml", null, drawing("그림$i"))
            d.tiny.rel(s, "rIdDm$i", Deck.REL_DIAGRAM_DATA, "../diagrams/data$i.xml")
            d.tiny.rel(s, "rIdDw$i", Deck.REL_DIAGRAM_DRAWING, "../diagrams/drawing$i.xml")
        }
        d.open().use { doc ->
            val text = textOf(doc.partHtml(0)!!).lines()
            assertEquals((1..PptxFlowDocument.MAX_DIAGRAM_READS).map { "그림$it" }, text)
            assertEquals(2, doc.unsupported.snapshot()[UnsupportedFeatures.SMART_ART])
        }
    }

    @Test
    fun 링크가_많아도_관계를_조각마다_처음부터_훑지_않는다() {
        // 관계 5만 개와 링크 조각 4만 개. 조각마다 관계 목록을 훑으면 20 억 번 비교한다(데스크톱에서
        // 10 초 남짓). 표로 찾으면 1 초 안팎이다 — 상한은 둘 사이에 넉넉히 둔다.
        val d = Deck()
        val paras = (0 until 10).joinToString("") { k ->
            "<a:p>" + (0 until 4_000).joinToString("") { j ->
                val q = (k * 4_000 + j) * 7_919 % 1_000_003
                """<a:r><a:rPr><a:hlinkClick r:id="rIdNope$q"/></a:rPr><a:t>$q</a:t></a:r>"""
            } + "</a:p>"
        }
        val s = d.slide(Deck.sld(Deck.sp(3, xfrm = Deck.xfrm(0, 0, 914400, 914400), paras = paras)))
        repeat(50_000) { d.tiny.rel(s, "rIdR${100_000 + it}", TinyOoxml.REL_HYPERLINK, "https://e/$it", external = true) }
        d.open().use { doc ->
            val t0 = System.nanoTime()
            val html = doc.partHtml(0)!!
            val ms = (System.nanoTime() - t0) / 1_000_000
            assertTrue(doc.warnings.isEmpty(), doc.warnings.toString())
            // 바깥 링크는 글자로만 남는다 — 조각 4만 개가 전부 그려졌다.
            assertEquals(40_000, Regex("<span style=").findAll(html).count())
            assertTrue(ms < 4_000, "그리는 데 ${ms}ms")
        }
    }

    @Test
    fun 개체_틀이_많아도_도형마다_레이아웃과_마스터를_훑지_않는다() {
        // 슬라이드·레이아웃·마스터에 짝이 없는 틀 1 만 개씩. 틀마다 목록을 네 번(마스터는 두 번) 훑으면
        // 3 억 번 남짓 비교한다(데스크톱에서 4 초 남짓). 찾기표면 0.1 초 안팎이다.
        val n = DrawingParser.MAX_SHAPES - 10
        fun phs(type: String, from: Int) = (0 until n).joinToString("") {
            """<p:sp><p:nvSpPr><p:cNvPr id="${it + 10}" name="P"/><p:cNvSpPr/><p:nvPr><p:ph type="$type" idx="${from + it}"/></p:nvPr></p:nvSpPr><p:spPr/></p:sp>"""
        }
        val d = Deck()
        d.masterXml = Deck.master(shapes = Deck.MASTER_PHS + phs("pic", 1_000_000))
        d.layoutXml = Deck.layout(phs("body", 100))
        d.slide(Deck.sld(phs("ftr", 50_000) + Deck.sp(2, "제목", ph = title)))
        d.open().use { doc ->
            val t0 = System.nanoTime()
            val html = doc.partHtml(0)!!
            val ms = (System.nanoTime() - t0) / 1_000_000
            // 짝은 그대로 — 레이아웃에 제목 틀이 없으니 마스터의 제목 자리(5vw)를 받는다.
            assertEquals("calc(var(--u)*5)", prop(boxStyleOf(html, "제목"), "left"))
            assertTrue(ms < 1_500, "그리는 데 ${ms}ms")
        }
    }

    @Test
    fun 그림_설명은_잘라서_alt_로_쓴다() {
        // 속성 값에는 길이 상한이 없다. 전에는 1,200 만 자 설명 하나가 쓰기 상한을 넘기고 위생기의
        // 입력 상한에 걸려 `partHtml` 이 던졌다.
        val d = Deck()
        val long = (0 until 10_000).joinToString("") { ('a' + it % 26).toString() }
        val s = d.slide(Deck.sld(Deck.pic(5, "rIdImg", Deck.xfrm(0, 0, 914400, 914400)).replace("descr=\"설명\"", "descr=\"$long\"")))
        d.tiny.bytes("ppt/media/image1.png", null, PptxDocumentTest.PNG)
        d.tiny.rel(s, "rIdImg", TinyOoxml.REL_IMAGE, "../media/image1.png")
        d.open().use { doc ->
            val alt = Regex("alt=\"([^\"]*)\"").find(doc.partHtml(0)!!)!!.groupValues[1]
            assertEquals(long.substring(0, DrawingParser.MAX_ATTR_CHARS), alt)
        }
    }

    @Test
    fun 무거운_레이아웃은_캐시에_두지_않는다() {
        // 레이아웃의 장식 글상자에 64,000 자 조각 열한 개(가중치 2 만 남짓). 가벼운 마스터는 캐시에 든다.
        val chunk = (0 until 64_000).joinToString("") { ('a' + it % 26).toString() }
        val heavyText = (0 until 11).joinToString("") { "<a:r><a:t>$it$chunk</a:t></a:r>" }
        fun deck(layoutShapes: String) = Deck().apply {
            layoutXml = Deck.layout(Deck.LAYOUT_PHS + layoutShapes)
            slide(Deck.sld(Deck.sp(2, "첫째", ph = title)))
            slide(Deck.sld(Deck.sp(2, "둘째", ph = title)))
        }
        deck(Deck.sp(20, xfrm = Deck.xfrm(0, 0, 914400, 914400), paras = "<a:p>$heavyText</a:p>")).open().use { doc ->
            val first = doc.partHtml(0)!!
            assertTrue("첫째" in first && "10$chunk" in first)
            assertEquals(1, doc.cachedPartCount)
            // 다시 읽어 그린다.
            assertTrue("10$chunk" in doc.partHtml(1)!!)
            assertEquals(1, doc.cachedPartCount)
        }
        deck(Deck.sp(20, "가벼운장식", xfrm = Deck.xfrm(0, 0, 914400, 914400))).open().use { doc ->
            assertTrue("가벼운장식" in doc.partHtml(0)!!)
            assertEquals(2, doc.cachedPartCount)
        }
    }
}
