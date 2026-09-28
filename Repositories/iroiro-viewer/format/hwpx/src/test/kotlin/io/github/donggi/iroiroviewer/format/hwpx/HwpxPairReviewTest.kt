package io.github.donggi.iroiroviewer.format.hwpx

import io.github.donggi.iroiroviewer.format.FlowWarnings
import io.github.donggi.iroiroviewer.format.UnsupportedFeatures
import io.github.donggi.iroiroviewer.format.html.HancomChunkPolicy
import io.github.donggi.iroiroviewer.format.html.HancomCss
import io.github.donggi.iroiroviewer.format.hwpx.Hwpx.esc
import io.github.donggi.iroiroviewer.format.hwpx.Hwpx.p
import io.github.donggi.iroiroviewer.format.hwpx.Hwpx.pRaw
import io.github.donggi.iroiroviewer.format.hwpx.Hwpx.run
import io.github.donggi.iroiroviewer.format.hwpx.Hwpx.sec
import io.github.donggi.iroiroviewer.format.hwpx.Hwpx.t
import io.github.donggi.iroiroviewer.format.hwpx.Hwpx.tbl
import io.github.donggi.iroiroviewer.format.hwpx.Hwpx.tc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 13단계 짝 대조(같은 보도자료의 HWP·HWPX — K01/K25·K11/K33·K19/K27)가 잡은 어긋남. 두 변환기가 **같은 판단**을 해야 하는
 * 자리마다 HWP 5.0 변환기의 규칙을 그대로 옮겼는지 본다.
 */
class HwpxPairReviewTest {

    private fun doc(vararg paragraphs: String, header: String = Hwpx.header(), options: HwpxOptions = HwpxOptions()) =
        Hwpx.doc(TinyHwpx().also { it.header = header }.section(sec(*paragraphs)), options)

    private fun html(vararg paragraphs: String, header: String = Hwpx.header()): String = doc(*paragraphs, header = header).use { it.body() }

    /** 머리에 글자 모양을 더한다. */
    private fun withChars(vararg charPrs: String): String =
        Hwpx.header().replace("</hh:charProperties>", charPrs.joinToString("") + "</hh:charProperties>")

    private fun caption(side: String?, inner: String): String =
        """<hp:caption${side?.let { " side=\"$it\"" } ?: ""} fullSz="0" width="8504" gap="850" lastWidth="8504"><hp:subList>$inner</hp:subList></hp:caption>"""

    private fun footnote(number: Int?, body: String, autoNum: Int? = number): String =
        """<hp:ctrl><hp:footNote${number?.let { " number=\"$it\"" } ?: ""}><hp:subList><hp:p paraPrIDRef="0" styleIDRef="0"><hp:run charPrIDRef="0">""" +
            (autoNum?.let { """<hp:ctrl><hp:autoNum num="$it" numType="FOOTNOTE"><hp:autoNumFormat type="DIGIT" userChar="" prefixChar="" suffixChar=")" supscript="0"/></hp:autoNum></hp:ctrl>""" } ?: "") +
            """<hp:t> ${esc(body)}</hp:t></hp:run></hp:p></hp:subList></hp:footNote></hp:ctrl>"""

    private fun endnote(body: String): String =
        """<hp:ctrl><hp:endNote number="1"><hp:subList><hp:p paraPrIDRef="0" styleIDRef="0"><hp:run charPrIDRef="0">""" +
            """<hp:t>${esc(body)}</hp:t></hp:run></hp:p></hp:subList></hp:endNote></hp:ctrl>"""

    /** 각주 모양(`hp:footNotePr`)을 가진 구역 정의 문단. */
    private fun secPr(numbering: String, newNum: Int = 1, format: String = """type="DIGIT" userChar="" prefixChar="" suffixChar=")"""") =
        """<hp:p paraPrIDRef="0" styleIDRef="0"><hp:run charPrIDRef="0"><hp:secPr id="" outlineShapeIDRef="1">""" +
            """<hp:footNotePr><hp:autoNumFormat $format supscript="0"/><hp:noteLine length="-1" type="SOLID" width="0.12 mm" color="#000000"/>""" +
            """<hp:noteSpacing betweenNotes="283" belowLine="567" aboveLine="850"/><hp:numbering type="$numbering" newNum="$newNum"/>""" +
            """<hp:placement place="EACH_COLUMN" beneathText="0"/></hp:footNotePr></hp:secPr></hp:run><hp:run charPrIDRef="0"><hp:t>머리</hp:t></hp:run></hp:p>"""

    /** 본문의 각주 표지들(문서 차례). */
    private fun refs(h: String): List<String> =
        Regex("<sup class=\"fnref\"[^>]*>(?:<a [^>]*>)?([^<]*)<").findAll(h.substringBefore("<section class=\"notes\">")).map { it.groupValues[1] }.toList()

    /** 주석 쪽의 번호들(문서 차례). */
    private fun noteNumbers(h: String): List<String> =
        Regex("<sup class=\"fnnum\"><a [^>]*>([^<]*)</a>").findAll(h.substringAfter("<section class=\"notes\">")).map { it.groupValues[1] }.toList()

    // ---- 바탕 스타일 ----

    @Test
    fun 바탕_CSS_는_HWP_5_0_과_같은_한_벌이고_캡션은_cap_이다() {
        // 처음에는 변환기마다 한 벌이라 캡션 크기·칸 여백·제목 줄 간격이 포맷마다 달랐다.
        val cap = caption("TOP", p("표 1. 캡션"))
        doc(pRaw(run(tbl(1, 1, tc(0, 0, p("칸")), caption = cap))))
            .use { d ->
                val page = d.partHtml(0)!!
                assertTrue(HancomCss.FLOW.trim() in page, page.take(3000))
                assertTrue("<div class=\"cap\"><p>표 1. 캡션</p></div>" in d.body(), d.body())
                assertFalse("class=\"caption\"" in page || ".caption{" in page, page.take(3000))
            }
    }

    // ---- 문단의 바탕 글자 ----

    @Test
    fun 빈_문단은_바탕_글자의_크기로_줄을_남긴다() {
        // 글자 모양 1 은 20pt·굵게·빨강이다. 빈 문단의 `br` 과 줄 높이가 문서 기본(10pt)이 아니라 그 크기를 따른다 — HWP 5.0 의
        // `paragraphCss` 와 같다. 처음에는 K25 의 빈 문단 874개가 전부 100% 였다(HWP 판 K01 은 30–73%).
        val h = html(pRaw(run("<hp:t/>", charPr = 1)), p("보통 글"))
        assertTrue("<p style=\"font-size:200%;font-weight:bold;color:#ff0000\"><br/></p>" in h, h)
        // 바탕이 문서 기본인 문단에는 아무것도 적지 않는다.
        assertTrue("<p>보통 글</p>" in h, h)
    }

    @Test
    fun 덩이는_문단_바탕과_다른_것만_적는다() {
        // 글자 모양 7: 20pt·굵게·밑줄, 색 없음(`textColor` 가 없다).
        val header = withChars("""<hh:charPr id="7" height="2000" shadeColor="none"><hh:fontRef hangul="1"/><hh:bold/><hh:underline type="BOTTOM" shape="SOLID"/></hh:charPr>""")
        val h = html(pRaw(run(t("큰 글"), charPr = 1) + run(t("보통 글"), charPr = 0) + run(t("색 없음"), charPr = 7)), header = header)
        assertTrue(h.startsWith("<div class=\"serif\"><p style=\"font-size:200%;font-weight:bold;color:#ff0000\">"), h)
        // 바탕과 같은 크기·굵기·색은 적지 않는다. 장식(밑줄)은 물려받지 않으므로 언제나 적는다.
        assertTrue("<span style=\"text-decoration:underline\">큰 글</span>" in h, h)
        // 크기는 바탕의 %, 바탕이 굵은데 덩이가 아니면 normal(HWP 5.0 의 `RunStyle.css(base)`).
        assertTrue("<span style=\"font-size:50%;font-weight:normal;color:#000000\">보통 글</span>" in h, h)
        // 바탕에 색이 있는데 덩이에 없으면 initial.
        assertTrue("<span style=\"color:initial;text-decoration:underline\">색 없음</span>" in h, h)
    }

    @Test
    fun 문단_바탕의_기울임과_고딕은_문단에_적고_덩이가_되돌린다() {
        // 문서 기본은 명조(함초롬바탕)다. 글자 모양 20 은 기울인 고딕 — 문단이 기울임과 `sans-serif` 를 들고, 바로 선 명조
        // 덩이는 `normal`·`serif` 로 되돌린다(HWP 5.0 의 `RunStyle.paragraphCss`·`css(base)`, 13단계 짝 대조).
        val header = withChars("""<hh:charPr id="20" height="1000" textColor="#000000"><hh:fontRef hangul="2"/><hh:italic/></hh:charPr>""")
            .replaceFirst("</hh:fontface>", """<hh:font id="2" face="맑은 고딕" type="TTF"/></hh:fontface>""")
        val h = html(pRaw(run(t("기울인 고딕"), charPr = 20) + run(t("바로 선 명조"), charPr = 0)), header = header)
        assertTrue(
            "<p style=\"font-style:italic;font-family:sans-serif\">기울인 고딕<span style=\"font-style:normal;font-family:serif\">바로 선 명조</span></p>" in h,
            h,
        )
    }

    @Test
    fun 같은_글자_모양도_문단_바탕이_다르면_CSS_가_다르다() {
        // 글자 CSS 는 (모양, 바탕, 밝은가) 마다 한 번 만든다 — 모양만으로 두면 앞 문단의 CSS 를 다음 문단이 물려받는다.
        val h = html(pRaw(run(t("앞"), charPr = 0) + run(t("크게"), charPr = 1)), pRaw(run(t("바탕이 큰"), charPr = 1)))
        assertTrue("<span style=\"font-size:200%;font-weight:bold;color:#ff0000;text-decoration:underline\">크게</span>" in h, h)
        assertTrue("<p style=\"font-size:200%;font-weight:bold;color:#ff0000\"><span style=\"text-decoration:underline\">바탕이 큰</span></p>" in h, h)
    }

    @Test
    fun 제목은_크기와_굵기를_언제나_적고_칸_안의_같은_문단은_적지_않는다() {
        // 개요 문단 모양 2 가 칸 안에 먼저 온다 — 칸 안은 제목이 아니다. 문단 CSS 는 (모양, 바탕, 밝은가, 제목인가) 마다 한 번.
        val table = tbl(1, 1, tc(0, 0, p("칸 개요", paraPr = 2)))
        val h = html(pRaw(run(table)), p("큰 제목", paraPr = 2))
        assertTrue(Regex("<td><p class=\"li\"><span class=\"mk\">1\\.</span>칸 개요</p>").containsMatchIn(h), h)
        assertTrue("<h1 id=\"h-0-1\" class=\"li\" style=\"font-size:100%;font-weight:normal\"><span class=\"mk\">2.</span>큰 제목</h1>" in h, h)
    }

    @Test
    fun 제목의_첫_덩이가_표로_시작해도_제목과_번호가_표_앞에_온다() {
        // 제목·번호 문단은 비어 있어도 연다 — 바탕(첫 덩이의 글자 모양)이 정해진 뒤, 덩이의 자식(표)보다 먼저.
        val table = tbl(1, 1, tc(0, 0, p("칸")))
        val first = """<hp:p paraPrIDRef="2" styleIDRef="0"><hp:run charPrIDRef="1">$table<hp:t>뒤 글</hp:t></hp:run></hp:p>"""
        doc(first).use { d ->
            val h = d.body()
            assertTrue(
                Regex("<h1 id=\"h-0-0\" class=\"li\" style=\"font-size:200%;font-weight:bold;color:#ff0000\"><span class=\"mk\">1\\.</span></h1><table>").containsMatchIn(h),
                h,
            )
            assertTrue(Regex("</table><h1 style=\"font-size:200%;font-weight:bold;color:#ff0000\"><span style=\"text-decoration:underline\">뒤 글</span></h1>").containsMatchIn(h), h)
            assertEquals(listOf("1. 뒤 글"), d.outline.map { it.title })
        }
    }

    @Test
    fun 덩이_없는_제목도_빈_문단이_이어진_뒤에_열린다() {
        // 덩이가 없는 문단은 스타일의 글자 모양이 바탕이다. 빈 문단이 셋 이어진 뒤라도 제목은 연다(목차가 가리킨다).
        val empty = pRaw(run("<hp:t/>"))
        val bare = """<hp:p paraPrIDRef="2" styleIDRef="1"/>"""
        doc(empty, empty, empty, bare, p("본문")).use { d ->
            val h = d.body()
            assertTrue("<h1 id=\"h-0-3\" class=\"li\" style=\"font-size:100%;font-weight:normal\"><span class=\"mk\">1.</span></h1>" in h, h)
            assertEquals(listOf("h-0-3"), d.outline.map { it.anchor })
        }
    }

    // ---- 캡션의 자리 ----

    @Test
    fun 아래쪽_캡션은_표_뒤에_위쪽_캡션은_표_앞에_온다() {
        // K27 의 아래쪽 캡션 `* 공공비영리단체 포함` 이 표 위에 보였다(스키마의 차례상 캡션이 행보다 앞에 적힌다).
        val top = tbl(1, 1, tc(0, 0, p("칸 가")), caption = caption("TOP", p("위 캡션")))
        val bottom = tbl(1, 1, tc(0, 0, p("칸 나")), caption = caption("BOTTOM", p("* 아래 캡션")))
        val none = tbl(1, 1, tc(0, 0, p("칸 다")), caption = caption(null, p("자리 없는 캡션")))
        val h = html(pRaw(run(t("앞") + top + bottom + none + t("뒤"))))
        assertTrue(Regex("<div class=\"cap\"><p>위 캡션</p></div><table><tr><td><p>칸 가</p></td></tr></table>").containsMatchIn(h), h)
        assertTrue(Regex("<table><tr><td><p>칸 나</p></td></tr></table><div class=\"cap\"><p>\\* 아래 캡션</p></div>").containsMatchIn(h), h)
        // `side` 가 없으면 아래(HWP 5.0 이 캡션 속성을 못 읽었을 때와 같다).
        assertTrue(Regex("칸 다</p></td></tr></table><div class=\"cap\"><p>자리 없는 캡션</p></div><p>뒤</p>").containsMatchIn(h), h)
        assertEquals(h.occurrences("<p"), h.occurrences("</p>"))
    }

    @Test
    fun 왼쪽_캡션은_표_앞에_오른쪽_캡션은_표_뒤에_온다() {
        // HWP 5.0 의 `captionOnTop` 과 같다 — 캡션 자리 0 왼쪽·2 위는 개체 앞, 1 오른쪽·3 아래는 개체 뒤.
        val left = tbl(1, 1, tc(0, 0, p("칸 가")), caption = caption("LEFT", p("왼쪽 캡션")))
        val right = tbl(1, 1, tc(0, 0, p("칸 나")), caption = caption("RIGHT", p("오른쪽 캡션")))
        val h = html(pRaw(run(left + right)))
        assertTrue("<div class=\"cap\"><p>왼쪽 캡션</p></div><table><tr><td><p>칸 가</p></td></tr></table>" in h, h)
        assertTrue("<table><tr><td><p>칸 나</p></td></tr></table><div class=\"cap\"><p>오른쪽 캡션</p></div>" in h, h)
    }

    @Test
    fun 차트와_글맵시의_캡션_글도_잃지_않는다() {
        // HWP 5.0 은 차트·글맵시도 그리기 개체(`gso`)라 캡션을 그린다. 처음에는 HWPX 가 두 요소를 통째로 건너뛰었다.
        val chart = """<hp:chart chartIDRef="Chart/chart1.xml"><hp:sz width="1"/>${caption("BOTTOM", p("차트 캡션"))}</hp:chart>"""
        val art = """<hp:textart text="글맵시"><hp:sz width="1"/>${caption("TOP", p("위 캡션"))}${caption("BOTTOM", p("아래 캡션"))}</hp:textart>"""
        doc(pRaw(run(chart)), pRaw(run(t("앞 ") + art + t(" 뒤")))).use { d ->
            val h = d.body()
            assertTrue("<div class=\"cap\"><p>차트 캡션</p></div>" in h, h)
            assertTrue(
                "<p>앞 </p><div class=\"cap\"><p>위 캡션</p></div><p>글맵시</p><div class=\"cap\"><p>아래 캡션</p></div><p> 뒤</p>" in h,
                h,
            )
            assertEquals(1, d.unsupported.snapshot()[UnsupportedFeatures.CHART], d.unsupported.snapshot().toString())
        }
    }

    @Test
    fun 그림의_아래쪽_캡션은_그림_뒤에서_문단을_끊고_붙는다() {
        val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
        val pic = """<hp:pic><hp:sz width="7200" height="3600"/>${caption("BOTTOM", p("그림 1. 설명"))}""" +
            """<hp:curSz width="7200" height="3600"/><hc:img binaryItemIDRef="image1"/></hp:pic>"""
        val t = TinyHwpx().section(sec(pRaw(run(t("앞") + pic + t("뒤"))))).binary("image1", "BinData/a.png", "image/png", png)
        Hwpx.doc(t).use { d ->
            val h = d.body()
            assertTrue(Regex("<p>앞<img src=\"BinData/a.png\"[^>]*></p><div class=\"cap\"><p>그림 1\\. 설명</p></div><p>뒤</p>").containsMatchIn(h), h)
        }
    }

    @Test
    fun 수식_묶음_OLE_동영상의_캡션_글을_잃지_않는다() {
        val eq = """<hp:equation version="Equation Version 60"><hp:sz width="1"/>${caption("BOTTOM", p("수식 캡션"))}<hp:script>a+b</hp:script></hp:equation>"""
        val group = """<hp:container><hp:sz width="1"/>${caption("TOP", p("묶음 캡션"))}<hp:rect><hp:drawText><hp:subList>${p("묶음 안 글")}</hp:subList></hp:drawText></hp:rect></hp:container>"""
        val ole = """<hp:ole binaryItemIDRef="ole1"><hp:sz width="1"/>${caption("BOTTOM", p("OLE 캡션"))}</hp:ole>"""
        val video = """<hp:video videotype="Local"><hp:sz width="1"/>${caption("TOP", p("동영상 캡션"))}</hp:video>"""
        doc(pRaw(run(t("식: ") + eq + t(" 끝"))), pRaw(run(group)), pRaw(run(ole)), pRaw(run(video))).use { d ->
            val h = d.body()
            assertTrue(Regex("<span class=\"math\">a\\+b</span></p><div class=\"cap\"><p>수식 캡션</p></div><p> 끝</p>").containsMatchIn(h), h)
            assertTrue(Regex("<div class=\"cap\"><p>묶음 캡션</p></div><div class=\"tb\"><p>묶음 안 글</p></div>").containsMatchIn(h), h)
            assertTrue("<div class=\"cap\"><p>OLE 캡션</p></div>" in h && "<div class=\"cap\"><p>동영상 캡션</p></div>" in h, h)
            assertEquals(2, d.unsupported.snapshot()[UnsupportedFeatures.EMBEDDED_OBJECT], d.unsupported.snapshot().toString())
        }
    }

    @Test
    fun 아래쪽_캡션_안의_각주도_본문과_링크를_잃지_않는다() {
        val table = tbl(1, 1, tc(0, 0, p("칸")), caption = caption("BOTTOM", pRaw(run(t("출처") + footnote(1, "캡션의 주석")))))
        doc(Hwpx.secPrParagraph("머리"), pRaw(run(table))).use { d ->
            val h = d.body()
            assertTrue(Regex("</table><div class=\"cap\"><p>출처<sup class=\"fnref\" id=\"fnref-1\"><a href=\"#fn-1\">1\\)</a></sup></p></div>").containsMatchIn(h), h)
            val notes = h.substringAfter("<section class=\"notes\">")
            assertTrue("<div id=\"fn-1\" class=\"note\">" in notes && "캡션의 주석" in notes, notes)
        }
    }

    @Test
    fun 따로_쓰는_캡션은_본문의_남은_몫까지만_쓴다() {
        // 캡션을 따로 쓰는 쓰개의 상한이 본문의 남은 몫이 아니면 붙인 뒤 본문이 상한을 몇 배로 넘는다.
        val long = (1..100).joinToString("") { p("캡션 문단 $it " + "가".repeat(100)) }
        val table = tbl(1, 1, tc(0, 0, p("칸")), caption = caption("BOTTOM", long))
        doc(pRaw(run(table)), p("뒤"), options = HwpxOptions(maxChars = 3000)).use { d ->
            val h = d.body()
            assertTrue(h.length < 3000 + 600, "길이 ${h.length}")
            assertTrue("캡션 문단 1 " in h, h)
            assertTrue(d.warnings.any { it.code == FlowWarnings.TRUNCATED }, d.warnings.toString())
            assertEquals(h.occurrences("<div"), h.occurrences("</div>"))
        }
    }

    @Test
    fun 아래쪽_캡션_안의_표도_훑기와_같은_차례로_센다() {
        // 캡션 안의 표(합친 칸)는 훑기와 그리기가 같은 자리에서 센다 — 캡션을 따로 써도 표 번호가 어긋나지 않는다.
        val inner = tbl(2, 2, tc(0, 0, p("캡션 표 머리"), colSpan = 2), tc(0, 1, p("x")) + tc(1, 1, p("y")))
        val outer = tbl(2, 2, tc(0, 0, p("바깥 머리"), colSpan = 2), tc(0, 1, p("a")) + tc(1, 1, p("b")), caption = caption("BOTTOM", pRaw(run(inner))))
        val after = tbl(1, 2, tc(0, 0, p("뒤 표"), colSpan = 2))
        val h = html(pRaw(run(outer)), pRaw(run(after)))
        assertTrue(Regex("<td colspan=\"2\"><p>바깥 머리").containsMatchIn(h), h)
        assertTrue(Regex("<td colspan=\"2\"><p>캡션 표 머리").containsMatchIn(h), h)
        assertTrue(Regex("<td colspan=\"2\"><p>뒤 표").containsMatchIn(h), h)
        assertTrue(h.indexOf("바깥 머리") < h.indexOf("캡션 표 머리"), h)
    }

    // ---- 밑줄의 자리와 상대 크기 ----

    @Test
    fun 밑줄의_자리는_위_아래_가운데를_가리고_상대_크기를_곱한다() {
        val header = withChars(
            """<hh:charPr id="10" height="1000" textColor="#000000"><hh:fontRef hangul="0"/><hh:underline type="TOP" shape="SOLID"/><hh:strikeout shape="NONE"/></hh:charPr>""",
            """<hh:charPr id="11" height="1000" textColor="#000000"><hh:fontRef hangul="0"/><hh:underline type="CENTER" shape="SOLID"/><hh:strikeout shape="NONE"/></hh:charPr>""",
            """<hh:charPr id="12" height="1000" textColor="#000000"><hh:fontRef hangul="0"/><hh:underline type="CENTER" shape="SOLID"/><hh:strikeout shape="3D"/></hh:charPr>""",
            """<hh:charPr id="13" height="1000" textColor="#000000"><hh:fontRef hangul="0"/><hh:relSz hangul="50" latin="100"/></hh:charPr>""",
            """<hh:charPr id="14" height="1000" textColor="#000000"><hh:fontRef hangul="0"/><hh:relSz hangul="300" latin="100"/></hh:charPr>""",
            """<hh:charPr id="15" height="1000" textColor="#000000"><hh:fontRef hangul="0"/><hh:underline type="TOP" shape="SOLID"/><hh:strikeout shape="SOLID"/></hh:charPr>""",
            """<hh:charPr id="16" height="1000" textColor="#000000"><hh:fontRef hangul="0"/><hh:underline type="BOTTOM" shape="SOLID"/><hh:strikeout shape="SOLID"/></hh:charPr>""",
        )
        val runs = run(t("바탕")) + run(t("윗줄"), 10) + run(t("가운데 줄"), 11) + run(t("삼디 가운데"), 12) +
            run(t("반 크기"), 13) + run(t("범위 밖"), 14) + run(t("윗줄 취소"), 15) + run(t("밑줄 취소"), 16)
        val h = html(pRaw(runs), header = header)
        // HWP 5.0 의 밑줄 자리(1 아래·2 가운데 = 취소선·3 위)와 같다. 처음에는 NONE 이 아니면 전부 밑줄이었다.
        assertTrue("<span style=\"text-decoration:overline\">윗줄</span>" in h, h)
        assertTrue("<span style=\"text-decoration:line-through\">가운데 줄</span>" in h, h)
        // 가운데 줄도 취소선 모양이 3D 면 긋지 않는다(HWP 의 `CharShape.strike` 식).
        assertTrue("삼디 가운데" in h && !Regex("<span[^>]*>삼디 가운데").containsMatchIn(h), h)
        // 상대 크기 50% 는 크기에 곱한다. 10–250 밖은 100% 다(HWP 5.0 의 `sizeCentiPt` 와 같다).
        assertTrue("<span style=\"font-size:50%\">반 크기</span>" in h, h)
        assertFalse(Regex("<span[^>]*>범위 밖").containsMatchIn(h), h)
        assertTrue("<span style=\"text-decoration:overline line-through\">윗줄 취소</span>" in h, h)
        assertTrue("<span style=\"text-decoration:underline line-through\">밑줄 취소</span>" in h, h)
    }

    // ---- 제목의 글 ----

    @Test
    fun 제목의_줄바꿈은_공백이고_방향_문자는_버리고_200자까지다() {
        val broken = """<hp:p paraPrIDRef="2" styleIDRef="0"><hp:run charPrIDRef="0"><hp:t>첫 줄<hp:lineBreak/>둘째 줄</hp:t></hp:run></hp:p>"""
        val bidi = p("거꾸로‮ 제목", paraPr = 2)
        val long = p("가".repeat(500), paraPr = 2)
        doc(broken, bidi, long).use { d ->
            val titles = d.outline.map { it.title }
            assertEquals("1. 첫 줄 둘째 줄", titles[0])
            assertEquals("2. 거꾸로 제목", titles[1])
            assertEquals(200, titles[2].length)
        }
    }

    // ---- 번호 ----

    @Test
    fun 번호는_HWP_5_0_과_같은_표로_센다() {
        // 시작 번호 0 은 0 이다(처음에는 1 로 보였다). 26 을 넘는 영문자는 같은 글자를 겹치고, 동그라미 숫자는 21 부터 ㉑.
        val extra = """<hh:numbering id="5" start="0"><hh:paraHead start="0" level="1" numFormat="DIGIT">^1.</hh:paraHead></hh:numbering>""" +
            """<hh:numbering id="6" start="0"><hh:paraHead start="27" level="1" numFormat="LATIN_SMALL">^1)</hh:paraHead></hh:numbering>""" +
            """<hh:numbering id="7" start="0"><hh:paraHead start="21" level="1" numFormat="CIRCLED_DIGIT">^1</hh:paraHead></hh:numbering>"""
        val paraPrs = """<hh:paraPr id="20"><hh:heading type="NUMBER" idRef="5" level="0"/></hh:paraPr>""" +
            """<hh:paraPr id="21"><hh:heading type="NUMBER" idRef="6" level="0"/></hh:paraPr>""" +
            """<hh:paraPr id="22"><hh:heading type="NUMBER" idRef="7" level="0"/></hh:paraPr>"""
        val header = Hwpx.header(extra).replace("</hh:paraProperties>", "$paraPrs</hh:paraProperties>")
        val h = html(p("영", paraPr = 20), p("일", paraPr = 20), p("에이에이", paraPr = 21), p("스물하나", paraPr = 22), header = header)
        assertEquals(listOf("0.", "1.", "aa)", "㉑"), markers(h))
    }

    @Test
    fun 자동_번호의_SYMBOL_모양은_숫자다() {
        // 모양 SYMBOL('네 글자가 차례로 되풀이')은 어떤 글자인지 명세에 없다 — 두 변환기 모두 숫자로 쓴다(처음에는 HWPX 만 `•`).
        val auto = """<hp:ctrl><hp:autoNum num="3" numType="TABLE"><hp:autoNumFormat type="SYMBOL" userChar="" prefixChar="" suffixChar="" supscript="0"/></hp:autoNum></hp:ctrl>"""
        val h = html(pRaw(run(t("<표 ") + auto + t(">"))))
        assertEquals(listOf("<표 3>"), paragraphs(h))
    }

    // ---- 각주·미주 ----

    @Test
    fun 쪽마다_새로_세는_각주는_흐름에서_이어서_센다() {
        // K26 의 각주는 `ON_PAGE` 라 저장된 번호가 쪽마다 1 이다 — 흐름에는 쪽이 없다(HWP 5.0 도 '쪽마다' 를 '이어서' 로 센다).
        // 주석 본문의 자동 번호도 저장된 `num`(1) 이 아니라 주석의 번호다.
        val body = pRaw(run(t("가") + footnote(1, "첫 주석") + t("나") + footnote(1, "둘째 주석")))
        doc(secPr("ON_PAGE"), body).use { d ->
            val h = d.body()
            assertEquals(listOf("1)", "2)"), refs(h), h)
            assertEquals(listOf("1)", "2)"), noteNumbers(h), h)
        }
        // 시작 번호(`newNum`)가 우리 셈의 1 이다.
        doc(secPr("ON_PAGE", newNum = 5), body).use { d -> assertEquals(listOf("5)", "6)"), refs(d.body()), d.body()) }
        // 이어서 세는 문서는 한글이 저장한 번호를 쓴다.
        doc(secPr("CONTINUOUS"), body).use { d -> assertEquals(listOf("1)", "1)"), refs(d.body()), d.body()) }
    }

    @Test
    fun 구역마다_새로_세는_각주는_그_구역에서_1_부터_센다() {
        // 저장된 번호가 없으면 우리 셈이다. `ON_SECTION` 구역의 `hp:secPr` 에서 셈이 0 으로 돌아간다(HWP 5.0 의 `sectionDef`).
        val two = pRaw(run(t("가") + footnote(null, "하나", autoNum = null) + t("나") + footnote(null, "둘", autoNum = null)))
        val t = TinyHwpx().section(sec(secPr("CONTINUOUS"), two)).section(sec(secPr("ON_SECTION"), two))
        Hwpx.doc(t).use { d ->
            assertEquals(listOf("1)", "2)"), refs(d.body(0)), d.body(0))
            assertEquals(listOf("1)", "2)"), refs(d.body(1)), d.body(1))
        }
        val c = TinyHwpx().section(sec(secPr("CONTINUOUS"), two)).section(sec(secPr("CONTINUOUS"), two))
        Hwpx.doc(c).use { d -> assertEquals(listOf("3)", "4)"), refs(d.body(1)), d.body(1)) }
    }

    @Test
    fun 주석_쪽에는_각주를_모두_쓴_뒤_미주를_쓴다() {
        // HWP 5.0 의 `renderNotes` 와 같은 차례다. 처음에는 만난 차례 그대로 섞였다.
        val h = html(Hwpx.secPrParagraph("머리"), pRaw(run(t("가") + endnote("미주 본문") + t("나") + footnote(1, "각주 본문"))))
        val notes = h.substringAfter("<section class=\"notes\">")
        assertTrue(notes.indexOf("<div id=\"fn-2\"") in 0 until notes.indexOf("<div id=\"en-1\""), notes)
        assertTrue(notes.indexOf("각주 본문") < notes.indexOf("미주 본문"), notes)
        // 본문의 표지는 만난 차례 그대로다.
        assertTrue(h.indexOf("href=\"#en-1\"") < h.indexOf("href=\"#fn-2\""), h)
    }

    @Test
    fun 새_번호_지정은_다음_각주가_그_번호를_받게_셈을_옮긴다() {
        // HWP 5.0 의 `nwno` 와 같은 셈(`번호 - 시작 번호`). 우리 셈을 쓰는 주석 — 쪽마다 새로 세는 문서와 저장한 번호가 없는 주석 —
        // 에서만 갈린다. 처음에는 `hp:newNum` 을 버려 그런 주석이 HWP 판과 다른 번호를 보였다(13단계에서 미룬 것).
        val renumber = { type: String, num: Int -> """<hp:ctrl><hp:newNum num="$num" numType="$type"/></hp:ctrl>""" }
        val body = pRaw(
            run(
                t("가") + footnote(1, "하나") + renumber("FOOTNOTE", 7) + t("나") + footnote(1, "둘") +
                    footnote(1, "셋") + renumber("PAGE", 30) + renumber("ENDNOTE", 4) + footnote(1, "넷"),
            ),
        )
        doc(secPr("ON_PAGE"), body).use { d ->
            val h = d.body()
            // 쪽 번호·미주의 새 번호는 각주 셈을 옮기지 않는다.
            assertEquals(listOf("1)", "7)", "8)", "9)"), refs(h), h)
            // 주석 쪽의 번호도 주석의 번호다(자동 번호의 저장된 `num` 이 아니다).
            assertEquals(listOf("1)", "7)", "8)", "9)"), noteNumbers(h), h)
        }
        // 시작 번호가 5 여도 새 번호는 적힌 번호 그대로 보인다(HWP 는 `번호 - 시작 번호` 로 셈을 둔다).
        doc(secPr("ON_PAGE", newNum = 5), body).use { d -> assertEquals(listOf("5)", "7)", "8)", "9)"), refs(d.body()), d.body()) }
        // 저장한 번호가 없는 주석도 우리 셈이다 — 이어 세는 문서에서도 새 번호를 따른다.
        val unsaved = pRaw(run(t("가") + footnote(null, "하나", autoNum = null) + renumber("FOOTNOTE", 3) + footnote(null, "둘", autoNum = null)))
        doc(secPr("CONTINUOUS"), unsaved).use { d -> assertEquals(listOf("1)", "3)"), refs(d.body()), d.body()) }
    }

    @Test
    fun 미주의_새_번호_지정은_미주_셈만_옮긴다() {
        // 각주와 같은 식이다(`번호 - 시작 번호`). 저장한 번호가 없는 미주가 새 번호를 받고, 각주 셈은 그대로다 — 위 시험은
        // 미주의 새 번호가 각주를 건드리지 않는 것만 보아 미주 쪽 셈이 빠져도 깨지지 않았다(검토가 더했다).
        val en = { body: String ->
            """<hp:ctrl><hp:endNote><hp:subList><hp:p paraPrIDRef="0" styleIDRef="0"><hp:run charPrIDRef="0"><hp:t>${esc(body)}</hp:t>""" +
                """</hp:run></hp:p></hp:subList></hp:endNote></hp:ctrl>"""
        }
        val body = pRaw(
            run(t("가") + en("미주 하나") + """<hp:ctrl><hp:newNum num="5" numType="ENDNOTE"/></hp:ctrl>""" + en("미주 둘") + footnote(null, "각주", autoNum = null)),
        )
        doc(secPr("CONTINUOUS"), body).use { d -> assertEquals(listOf("1)", "5)", "1)"), refs(d.body()), d.body()) }
    }

    @Test
    fun 새_번호_지정은_조각의_경계를_건너_이어진다() {
        // 셈은 걷기 상태에 있다 — 훑기가 조각의 시작마다 찍는 상태에도 옮긴 셈이 들어가야 뒤 조각이 같은 번호를 본다.
        val renumber = """<hp:ctrl><hp:newNum num="20" numType="FOOTNOTE"/></hp:ctrl>"""
        val policy = HancomChunkPolicy(softChars = 1_000_000, softBlocks = 2, hardChars = 1_000_000, hardBlocks = 2)
        doc(
            secPr("ON_PAGE"),
            pRaw(run(t("가") + renumber + footnote(1, "앞 조각"))),
            p("사이"),
            pRaw(run(t("나") + footnote(1, "뒤 조각"))),
            options = HwpxOptions(chunk = policy),
        ).use { d ->
            assertEquals(2, d.parts.size)
            assertEquals(listOf("20)"), refs(d.body(0)), d.body(0))
            assertEquals(listOf("21)"), refs(d.body(1)), d.body(1))
        }
    }

    // ---- 그림 설명문 ----

    private val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())

    /** 설명문은 스키마의 차례상 그림 **뒤에** 온다(실물 K25·K27 과 같은 자리). */
    private fun pic(comment: String?) =
        """<hp:pic><hp:curSz width="7200" height="3600"/><hc:img binaryItemIDRef="image1"/><hp:sz width="7200" height="3600"/>""" +
            (comment?.let { "<hp:shapeComment>${esc(it)}</hp:shapeComment>" } ?: "") + "</hp:pic>"

    private fun alts(h: String): List<String> = Regex("<img [^>]*alt=\"([^\"]*)\"").findAll(h).map { it.groupValues[1] }.toList()

    @Test
    fun 그림의_설명문은_HWP_와_같은_규칙으로_대체_글이_된다() {
        // HWP 5.0 은 개체 설명문을 대체 글로 쓰고(한글이 저절로 넣은 것은 버린다) HWPX 는 늘 비웠다(13단계에서 미룬 것).
        val auto = "그림입니다.\n원본 그림의 이름: CLP000043080017.bmp\n원본 그림의 크기: 가로 94pixel, 세로 33pixel"
        val t = TinyHwpx().section(
            sec(pRaw(run(pic("2024년 일자리 증감 그래프") + pic(auto) + pic(null) + pic("조직도입니다.") + pic("장식11-4입니다.")))),
        ).binary("image1", "BinData/a.png", "image/png", png)
        Hwpx.doc(t).use { d ->
            val h = d.body()
            assertEquals(listOf("2024년 일자리 증감 그래프", "", "", "조직도입니다.", ""), alts(h), h)
            assertFalse("CLP000043080017" in h, h)
        }
    }

    @Test
    fun 묶음의_설명문은_제_설명이_없는_안의_그림이_물려받는다() {
        // HWP 5.0 은 설명문이 개체 공통 속성에 있어 묶음 하나에 하나이고 안의 모든 그림의 대체 글이 된다. 묶음의 설명문은
        // 안의 개체들 **뒤에** 온다 — 훑기가 적어 둔 값이어야 그리기가 안의 그림을 쓸 때 안다.
        val group = """<hp:container>${pic(null)}${pic("제 설명")}<hp:sz width="1"/><hp:shapeComment>분기별 매출 묶음</hp:shapeComment></hp:container>"""
        val t = TinyHwpx().section(sec(pRaw(run(group + pic(null))))).binary("image1", "BinData/a.png", "image/png", png)
        Hwpx.doc(t).use { d -> assertEquals(listOf("분기별 매출 묶음", "제 설명", ""), alts(d.body()), d.body()) }
    }

    @Test
    fun 뒤_조각의_그림도_제_설명문을_받는다() {
        // 개체 번호는 걷기 상태에 있다 — 조각이 중간에서 시작해도 훑기가 적어 둔 번호와 같은 번호를 센다.
        val policy = HancomChunkPolicy(softChars = 1_000_000, softBlocks = 2, hardChars = 1_000_000, hardBlocks = 2)
        val t = TinyHwpx().section(
            sec(pRaw(run(pic("첫 그림"))), p("사이"), pRaw(run(pic(null) + pic("셋째 그림")))),
        ).binary("image1", "BinData/a.png", "image/png", png)
        Hwpx.doc(t, HwpxOptions(chunk = policy)).use { d ->
            assertEquals(2, d.parts.size)
            assertEquals(listOf("첫 그림"), alts(d.body(0)), d.body(0))
            assertEquals(listOf("", "셋째 그림"), alts(d.body(1)), d.body(1))
        }
    }

    @Test
    fun 사용자_글자_각주는_그_글자를_표지로_쓴다() {
        val h = html(secPr("ON_PAGE", format = """type="USER_CHAR" userChar="*" prefixChar="" suffixChar="""""), pRaw(run(t("가") + footnote(1, "별표 주석", autoNum = null))))
        assertEquals(listOf("*"), refs(h), h)
    }

    // ---- 부분 나누기 ----

    @Test
    fun 표의_칸은_블록_하나로_센다() {
        // HWP 5.0 과 같은 셈(`HancomChunkMeter`). 처음에는 칸을 글자 60자로 쳐서 같은 보도자료가 HWP 6부분·HWPX 4부분이었다.
        val cells = (0 until 3).joinToString("") { r -> "<hp:tr>" + (0 until 4).joinToString("") { c -> tc(c, r, p("$r-$c")) } + "</hp:tr>" }
        val table = """<hp:tbl rowCnt="3" colCnt="4"><hp:sz width="1" height="1"/>$cells</hp:tbl>"""
        val policy = HancomChunkPolicy(softChars = 1_000_000, softBlocks = 10, hardChars = 1_000_000, hardBlocks = 10)
        doc(pRaw(run(table)), p("뒤 하나"), p("뒤 둘"), options = HwpxOptions(chunk = policy)).use { d ->
            assertEquals(2, d.parts.size)
            assertTrue("2-3" in d.body(0) && "뒤 하나" !in d.body(0), d.body(0))
            assertEquals(listOf("뒤 하나", "뒤 둘"), paragraphs(d.body(1)))
        }
    }
}
