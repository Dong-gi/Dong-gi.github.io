package io.github.donggi.iroiroviewer.format.pptx

import io.github.donggi.iroiroviewer.format.FlowKind
import io.github.donggi.iroiroviewer.format.FlowWarnings
import io.github.donggi.iroiroviewer.format.UnsupportedFeatures
import io.github.donggi.iroiroviewer.format.opc.TinyOoxml
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 문서의 뼈대 — 슬라이드 순서·크기·목차, 그리고 pptx 의 핵심인 **상속**(슬라이드 → 레이아웃 → 마스터).
 *
 * 좌표는 슬라이드 폭(9144000 EMU)으로 나누어 떨어지게 골랐다 — 914400 EMU 가 10vw 다.
 */
class PptxDocumentTest {

    private val title = """<p:ph type="title"/>"""
    private val body = """<p:ph idx="1"/>"""

    private fun titleSlide(text: String) = Deck.sld(Deck.sp(2, text, ph = title))

    @Test
    fun 슬라이드_순서는_sldIdLst_를_따른다() {
        val d = Deck()
        d.slide(titleSlide("첫째"))
        d.slide(titleSlide("둘째"))
        d.slide(titleSlide("셋째"))
        // 부분 이름 순서(slide1, slide2, slide3)와 다르게 적는다.
        d.order = listOf("rIdS3", "rIdS1", "rIdS2")
        d.open().use { doc ->
            assertEquals(FlowKind.SLIDES, doc.kind)
            assertEquals(listOf("셋째", "첫째", "둘째"), doc.parts.map { it.label })
            assertTrue("셋째" in textOf(doc.partHtml(0)!!))
            assertTrue("첫째" in textOf(doc.partHtml(1)!!))
            assertEquals(listOf("~part-0.html", "~part-1.html", "~part-2.html"), doc.parts.map { it.path })
            assertEquals(listOf(0, 1, 2), doc.outline.map { it.partIndex })
            assertEquals(listOf("셋째", "첫째", "둘째"), doc.outline.map { it.title })
            assertTrue(doc.outline.all { it.depth == 0 && it.anchor == null })
        }
    }

    @Test
    fun 슬라이드_높이는_비율대로_vw() {
        fun heightOf(sldSz: String?): String {
            val d = Deck()
            d.sldSz = sldSz
            d.slide(titleSlide("가"))
            return d.open().use { doc ->
                val html = doc.partHtml(0)!!
                prop(html.substringAfter("class=\"slide\" style=\"").substringBefore('"'), "height")!!
            }
        }
        assertEquals("calc(var(--u)*56.25)", heightOf("""<p:sldSz cx="12192000" cy="6858000"/>"""))
        assertEquals("calc(var(--u)*75)", heightOf("""<p:sldSz cx="9144000" cy="6858000"/>"""))
        // 없거나 터무니없으면 4:3 기본값.
        assertEquals("calc(var(--u)*75)", heightOf(null))
        assertEquals("calc(var(--u)*75)", heightOf("""<p:sldSz cx="0" cy="6858000"/>"""))
        // 1:56 같은 비율은 1:10 으로 누른다.
        assertEquals("calc(var(--u)*1000)", heightOf("""<p:sldSz cx="9144000" cy="512064000"/>"""))
    }

    @Test
    fun 가로로_긴_화면에서는_높이에_맞춘다() {
        // 4:3 이면 화면의 가로세로비가 4/3 을 넘을 때 단위가 높이 기준(1vh × 4/3)으로 바뀐다.
        assertEquals(
            ".slide{--u:1vw;margin:0 auto;}@media (min-aspect-ratio: 4/3){.slide{--u:1.3333vh;}}",
            PptxFlowDocument.fitCss(9144000, 6858000),
        )
        assertEquals(
            ".slide{--u:1vw;margin:0 auto;}@media (min-aspect-ratio: 16/9){.slide{--u:1.7778vh;}}",
            PptxFlowDocument.fitCss(12192000, 6858000),
        )
        val d = Deck()
        d.slide(titleSlide("가"))
        d.open().use { doc ->
            // 규칙은 문서의 CSS 에 실려 위생을 지나도 남는다.
            assertTrue("min-aspect-ratio: 4/3" in doc.partHtml(0)!!)
        }
    }

    @Test
    fun 자리가_없는_개체_틀은_레이아웃의_자리를_받는다() {
        val d = Deck()
        d.layoutXml = Deck.layout(
            """<p:sp><p:nvSpPr><p:cNvPr id="2" name="T"/><p:cNvSpPr/><p:nvPr><p:ph type="title"/></p:nvPr></p:nvSpPr>""" +
                "<p:spPr>${Deck.xfrm(914400, 457200, 4572000, 914400)}</p:spPr></p:sp>",
        )
        d.slide(titleSlide("제목글"))
        d.open().use { doc ->
            val st = boxStyleOf(doc.partHtml(0)!!, "제목글")
            assertEquals("calc(var(--u)*10)", prop(st, "left"))
            assertEquals("calc(var(--u)*5)", prop(st, "top"))
            assertEquals("calc(var(--u)*50)", prop(st, "width"))
            assertEquals("calc(var(--u)*10)", prop(st, "height"))
        }
    }

    @Test
    fun 레이아웃에도_없으면_마스터의_자리를_받는다() {
        // 기본 레이아웃의 개체 틀은 `<p:spPr/>` — 자리가 마스터에만 있다.
        val d = Deck()
        d.slide(titleSlide("제목글"))
        d.open().use { doc ->
            val st = boxStyleOf(doc.partHtml(0)!!, "제목글")
            assertEquals("calc(var(--u)*5)", prop(st, "left"))
            assertEquals("calc(var(--u)*3.0035)", prop(st, "top"))
            assertEquals("calc(var(--u)*90)", prop(st, "width"))
            assertEquals("calc(var(--u)*12.5)", prop(st, "height"))
        }
    }

    @Test
    fun 개체_틀은_idx_로_짝을_짓고_없으면_부류로() {
        val d = Deck()
        d.layoutXml = Deck.layout(
            """<p:sp><p:nvSpPr><p:cNvPr id="2" name="A"/><p:cNvSpPr/><p:nvPr><p:ph idx="1"/></p:nvPr></p:nvSpPr>""" +
                "<p:spPr>${Deck.xfrm(0, 0, 914400, 914400)}</p:spPr></p:sp>" +
                """<p:sp><p:nvSpPr><p:cNvPr id="3" name="B"/><p:cNvSpPr/><p:nvPr><p:ph idx="2"/></p:nvPr></p:nvSpPr>""" +
                "<p:spPr>${Deck.xfrm(4572000, 0, 914400, 914400)}</p:spPr></p:sp>",
        )
        d.slide(
            Deck.sld(
                Deck.sp(2, "둘째칸", ph = """<p:ph idx="2"/>""") +
                    Deck.sp(3, "모르는칸", ph = """<p:ph type="body" idx="13"/>"""),
            ),
        )
        d.open().use { doc ->
            val html = doc.partHtml(0)!!
            // 첫 번째 본문 틀(idx 1)이 아니라 idx 2 와 짝이 된다.
            assertEquals("calc(var(--u)*50)", prop(boxStyleOf(html, "둘째칸"), "left"))
            // idx 13 은 레이아웃에 없다 — 같은 부류(본문)의 첫 틀.
            assertEquals("calc(var(--u)*0)", prop(boxStyleOf(html, "모르는칸"), "left"))
        }
    }

    @Test
    fun idx_가_같으면_형식이_달라도_형식보다_먼저_짝이다() {
        // ECMA-376 §19.3.1.36 은 idx 로 짝을 짓는다(python-pptx 도 idx 만 본다). 같은 형식의 틀이 따로 있어도
        // idx 가 같은 틀이 이긴다.
        val d = Deck()
        d.layoutXml = Deck.layout(
            """<p:sp><p:nvSpPr><p:cNvPr id="2" name="Ftr"/><p:cNvSpPr/><p:nvPr><p:ph type="ftr" idx="3"/></p:nvPr></p:nvSpPr>""" +
                "<p:spPr>${Deck.xfrm(0, 0, 914400, 914400)}</p:spPr></p:sp>" +
                """<p:sp><p:nvSpPr><p:cNvPr id="3" name="Body7"/><p:cNvSpPr/><p:nvPr><p:ph type="body" idx="7"/></p:nvPr></p:nvSpPr>""" +
                "<p:spPr>${Deck.xfrm(4572000, 0, 914400, 914400)}</p:spPr></p:sp>",
        )
        d.slide(Deck.sld(Deck.sp(2, "바닥글", ph = """<p:ph type="ftr" idx="7"/>""")))
        d.open().use { doc ->
            assertEquals("calc(var(--u)*50)", prop(boxStyleOf(doc.partHtml(0)!!, "바닥글"), "left"))
        }
    }

    @Test
    fun 글자_크기는_마스터_본문_서식의_단계에서_온다() {
        val d = Deck()
        d.slide(
            Deck.sld(
                Deck.sp(
                    2, ph = body,
                    paras = """<a:p><a:r><a:t>일단계</a:t></a:r></a:p><a:p><a:pPr lvl="1"/><a:r><a:t>이단계</a:t></a:r></a:p>""",
                ),
            ),
        )
        d.open().use { doc ->
            val html = doc.partHtml(0)!!
            // 32pt, 28pt ÷ 720pt(슬라이드 폭) × 100.
            assertEquals("calc(var(--u)*4.4444)", prop(runStyleOf(html, "일단계"), "font-size"))
            assertEquals("calc(var(--u)*3.8889)", prop(runStyleOf(html, "이단계"), "font-size"))
            // 2단계의 들여쓰기(marL 742950)도 마스터에서.
            assertEquals("calc(var(--u)*8.125)", prop(paraStyleOf(html, "이단계"), "padding-left"))
        }
    }

    @Test
    fun 글자색은_색_대응표를_거쳐_테마에서_온다() {
        val d = Deck()
        d.slide(titleSlide("기본색"))
        // 슬라이드가 대응표를 덮어써 tx1 을 lt1(흰색)으로 돌린다.
        d.slide(
            Deck.sld(Deck.sp(2, "뒤집힌색", ph = title)).replace(
                "</p:cSld>",
                """</p:cSld><p:clrMapOvr><a:overrideClrMapping bg1="dk1" tx1="lt1" bg2="dk2" tx2="lt2" accent1="accent1" """ +
                    """accent2="accent2" accent3="accent3" accent4="accent4" accent5="accent5" accent6="accent6" hlink="hlink" folHlink="folHlink"/></p:clrMapOvr>""",
            ),
        )
        d.open().use { doc ->
            // 마스터의 titleStyle 은 tx1 → 대응표 → dk1 → 테마의 sysClr lastClr 123456.
            assertEquals("#123456", prop(runStyleOf(doc.partHtml(0)!!, "기본색"), "color"))
            val second = doc.partHtml(1)!!
            assertEquals("#ffffff", prop(runStyleOf(second, "뒤집힌색"), "color"))
            // 배경(bgRef bg1)도 같은 대응표로 — bg1 → dk1.
            assertEquals("#123456", prop(second.substringAfter("class=\"slide\" style=\"").substringBefore('"'), "background-color"))
        }
    }

    @Test
    fun 레이아웃의_서식이_마스터를_이기고_조각의_서식이_모두를_이긴다() {
        val d = Deck()
        d.layoutXml = Deck.layout(
            """<p:sp><p:nvSpPr><p:cNvPr id="2" name="T"/><p:cNvSpPr/><p:nvPr><p:ph type="title"/></p:nvPr></p:nvSpPr><p:spPr/>""" +
                """<p:txBody><a:bodyPr/><a:lstStyle><a:lvl1pPr algn="r"><a:defRPr sz="6000"/></a:lvl1pPr></a:lstStyle><a:p/></p:txBody></p:sp>""",
        )
        d.slide(
            Deck.sld(
                Deck.sp(
                    2, ph = title,
                    paras = """<a:p><a:r><a:t>레이아웃크기</a:t></a:r><a:r><a:rPr sz="1000" b="1" i="1" u="sng" strike="sngStrike">""" +
                        """<a:solidFill><a:srgbClr val="FF0000"/></a:solidFill><a:latin typeface="Arial"/></a:rPr><a:t>조각서식</a:t></a:r></a:p>""",
                ),
            ),
        )
        d.open().use { doc ->
            val html = doc.partHtml(0)!!
            assertEquals("calc(var(--u)*8.3333)", prop(runStyleOf(html, "레이아웃크기"), "font-size"))
            assertEquals("right", prop(paraStyleOf(html, "레이아웃크기"), "text-align"))
            val run = runStyleOf(html, "조각서식")
            assertEquals("calc(var(--u)*1.3889)", prop(run, "font-size"))
            assertEquals("bold", prop(run, "font-weight"))
            assertEquals("italic", prop(run, "font-style"))
            assertEquals("underline line-through", prop(run, "text-decoration"))
            assertEquals("#ff0000", prop(run, "color"))
            assertEquals("\"Arial\"", prop(run, "font-family"))
            // 테마 글꼴은 풀어서 — 제목(+mj-lt 가 아니다: 서식이 글꼴을 적지 않으면 글꼴을 적지 않는다).
            assertEquals(null, prop(runStyleOf(html, "레이아웃크기"), "font-family"))
        }
    }

    @Test
    fun 숨긴_슬라이드도_부분이다() {
        val d = Deck()
        d.slide(titleSlide("보이는"))
        d.slide(Deck.sld(Deck.sp(2, "숨은", ph = title), attrs = "show=\"0\""))
        d.open().use { doc ->
            assertEquals(2, doc.parts.size)
            assertEquals("숨은", doc.parts[1].label)
            assertTrue("숨은" in textOf(doc.partHtml(1)!!))
        }
    }

    @Test
    fun 배경_그래픽_숨기기는_마스터와_레이아웃의_장식을_가린다() {
        fun deck(slideAttrs: String, layoutAttrs: String): String {
            val d = Deck()
            d.masterXml = Deck.master(shapes = Deck.MASTER_PHS + Deck.sp(10, "마스터로고", xfrm = Deck.xfrm(0, 0, 914400, 914400)))
            d.layoutXml = Deck.layout(Deck.LAYOUT_PHS + Deck.sp(11, "레이아웃장식", xfrm = Deck.xfrm(0, 914400, 914400, 914400)), attrs = layoutAttrs)
            d.slide(Deck.sld(Deck.sp(2, "본문글", xfrm = Deck.xfrm(0, 0, 914400, 914400)), attrs = slideAttrs))
            return d.open().use { textOf(it.partHtml(0)!!) }
        }
        val normal = deck("", "")
        assertTrue("마스터로고" in normal && "레이아웃장식" in normal, normal)
        // 장식이 슬라이드 도형보다 **아래**(먼저)다.
        assertTrue(normal.indexOf("마스터로고") < normal.indexOf("레이아웃장식") && normal.indexOf("레이아웃장식") < normal.indexOf("본문글"))
        // 레이아웃·마스터의 개체 틀(안내문)은 그리지 않는다.
        assertFalse("마스터 제목" in normal || "레이아웃 본문" in normal, normal)

        val hidden = deck("showMasterSp=\"0\"", "")
        assertFalse("마스터로고" in hidden || "레이아웃장식" in hidden, hidden)
        assertTrue("본문글" in hidden)

        val layoutHides = deck("", "showMasterSp=\"0\"")
        assertFalse("마스터로고" in layoutHides, layoutHides)
        assertTrue("레이아웃장식" in layoutHides)
    }

    @Test
    fun 배경은_슬라이드_레이아웃_마스터_순서로() {
        fun bgOf(d: Deck): String = d.open().use {
            prop(it.partHtml(0)!!.substringAfter("class=\"slide\" style=\"").substringBefore('"'), "background-color")!!
        }
        val solid = { hex: String -> """<p:bg><p:bgPr><a:solidFill><a:srgbClr val="$hex"/></a:solidFill><a:effectLst/></p:bgPr></p:bg>""" }
        val fromLayout = Deck().apply {
            layoutXml = Deck.layout(bg = solid("00FF00"))
            slide(titleSlide("가"))
        }
        assertEquals("#00ff00", bgOf(fromLayout))
        val own = Deck().apply {
            layoutXml = Deck.layout(bg = solid("00FF00"))
            slide(Deck.sld(Deck.sp(2, "가", ph = title), bg = solid("FF0000")))
        }
        assertEquals("#ff0000", bgOf(own))
        // 마스터의 bgRef 는 그 색(bg1 → lt1 → 흰색). 그라데이션은 첫 멈춤점.
        assertEquals("#ffffff", bgOf(Deck().apply { slide(titleSlide("가")) }))
        val grad = Deck().apply {
            slide(
                Deck.sld(
                    Deck.sp(2, "가", ph = title),
                    bg = """<p:bg><p:bgPr><a:gradFill><a:gsLst><a:gs pos="0"><a:schemeClr val="accent1"><a:lumMod val="75000"/></a:schemeClr></a:gs>""" +
                        """<a:gs pos="100000"><a:srgbClr val="FFFFFF"/></a:gs></a:gsLst></a:gradFill></p:bgPr></p:bg>""",
                ),
            )
        }
        assertEquals("#2f5597", bgOf(grad))
    }

    @Test
    fun 배경_그림은_그림을_가진_부분의_관계로_푼다() {
        val d = Deck()
        d.layoutXml = Deck.layout(bg = """<p:bg><p:bgPr><a:blipFill><a:blip r:embed="rIdBg"/><a:stretch/></a:blipFill></p:bgPr></p:bg>""")
        d.tiny.bytes("ppt/media/bg.png", null, PNG)
        d.tiny.rel("ppt/slideLayouts/slideLayout1.xml", "rIdBg", TinyOoxml.REL_IMAGE, "../media/bg.png")
        d.slide(titleSlide("가"))
        d.open().use { doc ->
            val html = doc.partHtml(0)!!
            assertTrue("class=\"bgimg\" src=\"ppt/media/bg.png\"" in html, html)
        }
    }

    @Test
    fun 제목과_목차와_문서_제목() {
        val d = Deck()
        d.coreTitle = "  분기 보고  "
        d.slide(
            Deck.sld(
                Deck.sp(
                    2, ph = """<p:ph type="ctrTitle"/>""",
                    paras = """<a:p><a:r><a:t>첫 줄</a:t></a:r><a:br/><a:r><a:t>둘째 줄</a:t></a:r></a:p><a:p><a:r><a:t>  셋째  </a:t></a:r></a:p>""",
                ),
            ),
        )
        d.slide(Deck.sld(Deck.sp(2, "제목이 아닌 글", xfrm = Deck.xfrm(0, 0, 914400, 914400))))
        d.slide(titleSlide("가".repeat(200)))
        d.open().use { doc ->
            assertEquals("분기 보고", doc.title)
            assertEquals("첫 줄 둘째 줄 셋째", doc.parts[0].label)
            // 제목 틀이 없으면 빈 이름 — 화면이 '슬라이드 2' 로 채운다.
            assertEquals("", doc.parts[1].label)
            assertEquals("", doc.outline[1].title)
            assertEquals(PptxScan.MAX_LABEL, doc.parts[2].label.length)
        }
    }

    @Test
    fun 깨진_슬라이드는_그_부분만_실패한다() {
        val d = Deck()
        d.slide(titleSlide("멀쩡한"))
        d.slide("""<p:sld ${Deck.NS}><p:cSld><p:spTree><p:sp><a:t>닫히지 않은""")
        d.slide(titleSlide("뒤의것"))
        d.open().use { doc ->
            assertEquals(3, doc.parts.size)
            assertEquals("", doc.parts[1].label)
            assertTrue("멀쩡한" in textOf(doc.partHtml(0)!!))
            val broken = doc.partHtml(1)!!
            assertTrue("part-failed" in broken)
            assertTrue(doc.warnings.any { it.code == FlowWarnings.PART_FAILED })
            assertTrue("뒤의것" in textOf(doc.partHtml(2)!!))
        }
    }

    @Test
    fun 레이아웃도_마스터도_테마도_없어도_글자는_보인다() {
        val d = Deck()
        d.masterXml = null
        d.layoutXml = null
        d.themeXml = null
        d.slide(
            Deck.sld(Deck.sp(2, "외톨이 제목", ph = title) + Deck.sp(3, "글상자", xfrm = Deck.xfrm(0, 0, 914400, 914400))),
            withLayout = false,
        )
        d.open().use { doc ->
            val html = doc.partHtml(0)!!
            assertEquals("외톨이 제목\n글상자", textOf(html))
            // 테마가 없으면 오피스 기본(dk1 검정), 서식이 없으면 제목 44pt·그 밖 18pt.
            assertEquals("#000000", prop(runStyleOf(html, "외톨이 제목"), "color"))
            assertEquals("calc(var(--u)*6.1111)", prop(runStyleOf(html, "외톨이 제목"), "font-size"))
            assertEquals("calc(var(--u)*2.5)", prop(runStyleOf(html, "글상자"), "font-size"))
            // 자리를 모르는 제목 틀은 위쪽 띠에 둔다.
            assertEquals("calc(var(--u)*5)", prop(boxStyleOf(html, "외톨이 제목"), "left"))
            assertTrue(doc.warnings.none { it.code == FlowWarnings.PART_FAILED })
        }
    }

    @Test
    fun 애니메이션과_전환은_슬라이드마다_한_번_센다() {
        val timing = """<p:timing><p:tnLst><p:par><p:cTn id="1" dur="indefinite" nodeType="tmRoot"><p:childTnLst><p:seq><p:cTn id="2">""" +
            """<p:childTnLst><p:par><p:cTn id="3"><p:childTnLst><p:set><p:cBhvr><p:cTn id="4"/><p:tgtEl><p:spTgt spid="2"/></p:tgtEl></p:cBhvr></p:set>""" +
            """<p:anim><p:cBhvr><p:cTn id="5"/><p:tgtEl><p:spTgt spid="2"/></p:tgtEl></p:cBhvr></p:anim></p:childTnLst></p:cTn></p:par></p:childTnLst>""" +
            "</p:cTn></p:seq></p:childTnLst></p:cTn></p:par></p:tnLst></p:timing>"
        val emptyTiming = """<p:timing><p:tnLst><p:par><p:cTn id="1" dur="indefinite" restart="never" nodeType="tmRoot"/></p:par></p:tnLst></p:timing>"""
        val transition = """<mc:AlternateContent xmlns:mc="http://schemas.openxmlformats.org/markup-compatibility/2006">""" +
            """<mc:Choice Requires="p14"><p:transition spd="slow"><p:fade/></p:transition></mc:Choice>""" +
            """<mc:Fallback><p:transition spd="slow"><p:fade/></p:transition></mc:Fallback></mc:AlternateContent>"""
        val d = Deck()
        d.slide(Deck.sld(Deck.sp(2, "가", ph = title), after = timing))
        d.slide(Deck.sld(Deck.sp(2, "나", ph = title), after = emptyTiming))
        d.slide(Deck.sld(Deck.sp(2, "다", ph = title), after = transition))
        d.slide(Deck.sld(Deck.sp(2, "라", ph = title), after = """<p:transition advTm="3000"/>"""))
        d.open().use { doc ->
            assertEquals(2, doc.unsupported.snapshot()[UnsupportedFeatures.ANIMATION])
        }
    }

    @Test
    fun 메모는_개수를_센다() {
        val d = Deck()
        val s = d.slide(titleSlide("가"))
        d.tiny.xml(
            "ppt/comments/comment1.xml", null,
            """<p:cmLst ${Deck.NS}><p:cm authorId="0" idx="1"><p:pos x="10" y="10"/><p:text>하나</p:text></p:cm>""" +
                """<p:cm authorId="0" idx="2"><p:pos x="10" y="10"/><p:text>둘</p:text></p:cm></p:cmLst>""",
        )
        d.tiny.rel(s, "rIdC", Deck.REL_COMMENTS, "../comments/comment1.xml")
        d.open().use { doc ->
            assertEquals(2, doc.unsupported.snapshot()[UnsupportedFeatures.COMMENT])
            // 메모의 글은 본문에 나오지 않는다.
            assertFalse("하나" in doc.partHtml(0)!!)
        }
    }

    @Test
    fun 슬라이드는_2000_장까지만() {
        val d = Deck()
        repeat(PptxFlowDocument.MAX_SLIDES + 1) { d.slide(Deck.sld("")) }
        d.open().use { doc ->
            assertEquals(PptxFlowDocument.MAX_SLIDES, doc.parts.size)
            assertTrue(doc.warnings.any { it.code == FlowWarnings.TRUNCATED })
        }
    }

    @Test
    fun 마스터의_버린_것은_슬라이드마다_그려져도_한_번만_센다() {
        val d = Deck()
        d.masterXml = Deck.master(shapes = Deck.MASTER_PHS + Deck.pic(20, "rIdLogo", Deck.xfrm(0, 0, 914400, 914400)))
        d.tiny.bytes("ppt/media/logo.emf", null, byteArrayOf(1, 0, 0, 0))
        d.tiny.rel("ppt/slideMasters/slideMaster1.xml", "rIdLogo", TinyOoxml.REL_IMAGE, "../media/logo.emf")
        repeat(5) { d.slide(titleSlide("쪽$it")) }
        d.open().use { doc ->
            // 캐시(셋)를 넘겨 다시 그리게 만든다.
            for (i in 0 until 5) assertNotNull(doc.partHtml(i))
            for (i in 0 until 5) assertNotNull(doc.partHtml(i))
            assertEquals(1, doc.unsupported.snapshot()[UnsupportedFeatures.UNSUPPORTED_IMAGE])
            assertFalse("logo.emf" in doc.partHtml(0)!!)
        }
    }

    @Test
    fun 조각_상한을_넘으면_앞부분만_그리고_알린다() {
        val runs = (0..DrawingParser.MAX_RUNS).joinToString("") { "<a:r><a:t>r$it </a:t></a:r>" }
        val d = Deck()
        d.slide(Deck.sld(Deck.sp(2, "제목", ph = title) + Deck.sp(3, xfrm = Deck.xfrm(0, 0, 914400, 914400), paras = "<a:p>$runs</a:p>")))
        d.open().use { doc ->
            val html = doc.partHtml(0)!!
            assertTrue("r0 " in html && "r4999 " in html)
            assertFalse("r5000 " in html)
            assertTrue(doc.warnings.any { it.code == FlowWarnings.TRUNCATED && it.detail == "제목" }, doc.warnings.toString())
        }
    }

    @Test
    fun 너무_깊은_XML_은_그_슬라이드만_자른다() {
        val bomb = "<a:x>".repeat(400) + "</a:x>".repeat(400)
        val d = Deck()
        d.slide(Deck.sld(Deck.sp(2, "앞", ph = title)).replace("</p:spTree>", "$bomb</p:spTree>"))
        d.slide(titleSlide("뒤"))
        d.open().use { doc ->
            assertEquals(2, doc.parts.size)
            assertTrue("part-failed" in doc.partHtml(0)!!)
            assertTrue(doc.warnings.any { it.code == FlowWarnings.TRUNCATED })
            assertTrue("뒤" in textOf(doc.partHtml(1)!!))
        }
    }

    @Test
    fun 빈_개체_틀은_그리지_않는다() {
        val d = Deck()
        d.slide(Deck.sld(Deck.sp(2, ph = body, paras = "<a:p><a:endParaRPr lang=\"ko-KR\"/></a:p>") + Deck.sp(3, "제목만", ph = title)))
        d.open().use { doc ->
            val html = doc.partHtml(0)!!
            assertEquals("제목만", textOf(html))
            assertEquals(1, Regex("class=\"sp\"").findAll(html).count(), html)
        }
    }

    companion object {
        val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)
    }
}
