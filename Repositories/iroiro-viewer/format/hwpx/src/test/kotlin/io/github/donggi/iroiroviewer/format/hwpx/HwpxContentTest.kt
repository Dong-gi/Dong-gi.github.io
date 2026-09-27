package io.github.donggi.iroiroviewer.format.hwpx

import io.github.donggi.iroiroviewer.format.UnsupportedFeatures
import io.github.donggi.iroiroviewer.format.html.FlowDocumentBase
import io.github.donggi.iroiroviewer.format.html.HancomNumbers
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 본문의 내용 — 글자·문단 모양, 번호, 표, 그림, 각주, 필드, 변경 추적, `hp:switch`. */
class HwpxContentTest {

    private fun html(vararg paragraphs: String, configure: (TinyHwpx) -> Unit = {}): String {
        val t = TinyHwpx().section(sec(*paragraphs))
        configure(t)
        return Hwpx.doc(t).use { it.body() }
    }

    @Test
    fun 글자_모양은_바탕과_다른_것만_적는다() {
        val h = html(p("보통 글"), p("굵고 빨간 큰 글", charPr = 1))
        // 바탕 글자(10pt, 검정, 명조)는 아무것도 적지 않는다 — 부분 전체가 `div.serif` 에 싸인다.
        assertTrue("<div class=\"serif\">" in h, h)
        assertTrue(Regex("<p[^>]*>보통 글</p>").containsMatchIn(h), h)
        assertTrue("font-size:200%" in h && "font-weight:bold" in h && "color:#ff0000" in h && "text-decoration:underline" in h, h)
    }

    @Test
    fun 취소선_모양_3D_는_꺼짐이고_SOLID_는_켜짐이다() {
        // 한글은 바탕 글자 모양에도 `hh:strikeout shape="3D"` 를 적는다(표본 글자 모양의 27%). 그것을 그리면 본문 전체에 줄이 그어진다.
        val h = html(p("삼디", charPr = 3), p("취소", charPr = 4))
        val threeD = Regex("<p[^>]*>(.*?)삼디").find(h)!!.groupValues[1]
        assertFalse("line-through" in threeD, h)
        assertTrue(Regex("<span style=\"[^\"]*text-decoration:line-through[^\"]*\">취소</span>").containsMatchIn(h), h)
        assertTrue("font-style:italic" in h)
    }

    @Test
    fun 위_첨자는_sup_로_감싼다() {
        val h = html(pRaw(run(t("본문")) + run(t("주"), charPr = 2)))
        assertTrue("<sup>주</sup>" in h, h)
    }

    @Test
    fun 문단_여백은_switch_의_HwpUnitChar_가지를_HWPUNIT_으로_읽는다() {
        // 바로보기(Synap)로 가린 값: case 의 -3790·1000 이 -37.9pt·10pt 다. default(두 배 값)를 읽으면 -75.8pt 가 된다.
        val h = html(p("내어쓰기 문단", paraPr = 1))
        assertTrue("text-align:center" in h, h)
        assertTrue("margin-left:37.9pt" in h && "text-indent:-37.9pt" in h && "margin-top:10pt" in h, h)
        assertTrue("line-height:1.3" in h, h)
        assertFalse("75.8pt" in h)
    }

    @Test
    fun 모르는_case_는_건너뛰고_default_를_두_배_값으로_읽는다() {
        val pp = """<hh:paraPr id="20"><hh:heading type="NONE" idRef="0" level="0"/><hp:switch>""" +
            """<hp:case hp:required-namespace="urn:future"><hh:margin><hc:intent value="-100"/><hc:left value="0"/><hc:right value="0"/><hc:prev value="0"/><hc:next value="0"/></hh:margin></hp:case>""" +
            """<hp:default><hh:margin><hc:intent value="-3000"/><hc:left value="0"/><hc:right value="0"/><hc:prev value="0"/><hc:next value="0"/></hh:margin></hp:default>""" +
            """</hp:switch></hh:paraPr>"""
        val header = Hwpx.header().replace("</hh:paraProperties>", "$pp</hh:paraProperties>")
        val h = html(p("미래 문단", paraPr = 20)) { it.header = header }
        assertTrue("text-indent:-15pt" in h, h)
    }

    @Test
    fun switch_가_없는_옛_파일의_여백은_두_배_값이다() {
        // K28(xmlVersion 1.2)을 바로보기와 견준 값: 내어쓰기 -4216 → -21.08pt, 위 간격 1000 → 5pt.
        val h = html(p("옛 문단", paraPr = 6))
        assertTrue("margin-left:21.08pt" in h && "text-indent:-21.08pt" in h && "margin-top:5pt" in h, h)
    }

    @Test
    fun 번호_문단은_번호를_세고_수준이_내려가면_아래_수준을_새로_센다() {
        val extra = """<hh:numbering id="3" start="0"><hh:paraHead start="1" level="1" numFormat="DIGIT">^1.</hh:paraHead>""" +
            """<hh:paraHead start="1" level="2" numFormat="HANGUL_SYLLABLE">^2)</hh:paraHead>""" +
            """<hh:paraHead start="1" level="3" numFormat="LATIN_CAPITAL">^n</hh:paraHead></hh:numbering>"""
        val paraPrs = """<hh:paraPr id="10"><hh:heading type="NUMBER" idRef="3" level="0"/></hh:paraPr>""" +
            """<hh:paraPr id="11"><hh:heading type="NUMBER" idRef="3" level="1"/></hh:paraPr>""" +
            """<hh:paraPr id="12"><hh:heading type="NUMBER" idRef="3" level="2"/></hh:paraPr>"""
        val header = Hwpx.header(extra).replace("</hh:paraProperties>", "$paraPrs</hh:paraProperties>")
        val h = html(
            p("일", paraPr = 10), p("일-가", paraPr = 11), p("일-나", paraPr = 11), p("경로", paraPr = 12),
            p("이", paraPr = 10), p("이-가", paraPr = 11),
        ) { it.header = header }
        assertEquals(listOf("1.", "가)", "나)", "1.나.A", "2.", "가)"), markers(h))
        assertTrue("class=\"li\"" in h)
    }

    @Test
    fun 번호_모양은_KS_X_6101_의_이름대로_옮긴다() {
        // 모양의 이름을 두 변환기가 함께 쓰는 표(`HancomNumbers`)의 값으로 옮긴다.
        fun f(v: Int, name: String) = HancomNumbers.format(v, HancomNumbers.shapeOf(name))
        assertEquals("③", f(3, "CIRCLED_DIGIT"))
        assertEquals("㉑", f(21, "CIRCLED_DIGIT"))
        assertEquals("다", f(3, "HANGUL_SYLLABLE"))
        assertEquals("㉰", f(3, "CIRCLED_HANGUL_SYLLABLE"))
        assertEquals("ㄷ", f(3, "HANGUL_JAMO"))
        assertEquals("IV", f(4, "ROMAN_CAPITAL"))
        assertEquals("iv", f(4, "ROMAN_SMALL"))
        assertEquals("C", f(3, "LATIN_CAPITAL"))
        assertEquals("삼십이", f(32, "HANGUL_PHONETIC"))
        assertEquals("十二", f(12, "IDEOGRAPH"))
        assertEquals("병", f(3, "DECAGON_CIRCLE"))
        assertEquals("7", f(7, "모르는모양"))
        assertEquals("*", AutoNumFormat(HancomNumbers.shapeOf("USER_CHAR"), "*", "", "").label(1))
        assertEquals("(2)", AutoNumFormat(HancomNumbers.shapeOf("DIGIT"), "", "(", ")").label(2))
    }

    @Test
    fun 글머리표의_기호_글꼴_글자는_HWP_5_0_과_같은_글자로_옮긴다() {
        // 머리의 글머리표 1 은 `&#xF0A7;`(Wingdings 의 사설 영역 글자) — 기기에 그 글꼴이 없다. HWP 5.0 변환기와 같은 표
        // (`BulletGlyphs`)로 `▪` 가 된다. 예전 HWPX 는 사설 영역이면 무엇이든 `•` 로 썼다 — 같은 문서가 포맷마다 달랐다.
        val h = html(p("점 항목", paraPr = 5))
        assertEquals(listOf("▪"), markers(h))
    }

    @Test
    fun 표는_칸을_합치고_배경을_칠하고_테두리_없는_칸을_가린다() {
        // 2×3 표 — 첫 칸이 두 열, 둘째 행의 첫 칸이 아래로 두 행(가려진 칸은 한글처럼 빠져 있다).
        val table = tbl(
            3, 3,
            tc(0, 0, p("합친 머리"), colSpan = 2, fill = 2) + tc(2, 0, p("오른쪽")),
            tc(0, 1, p("세로 합침"), rowSpan = 2, fill = 1) + tc(1, 1, p("가")) + tc(2, 1, p("나")),
            tc(1, 2, p("다")) + tc(2, 2, p("라")),
        )
        val h = html(pRaw(run(table)))
        assertTrue(Regex("<td colspan=\"2\" style=\"background-color:#eaf1dd\"><p[^>]*>합친 머리").containsMatchIn(h), h)
        assertTrue(Regex("<td rowspan=\"2\" class=\"nb\"><p[^>]*>세로 합침").containsMatchIn(h), h)
        assertEquals(2 + 3 + 2, h.occurrences("<td"))
        assertFalse("class=\"gap\"" in h)
    }

    @Test
    fun 주소가_건너뛴_칸은_빈칸으로_메우고_겹친_비활성_칸은_그리지_않는다() {
        // 둘째 행: 칸 (0,1) 이 빠졌고(주소가 1 부터), 셋째 행에 크기 0 인 칸이 첫 행의 세로 합침과 겹친다.
        val zero = """<hp:tc name=""><hp:subList>${p("")}</hp:subList><hp:cellAddr colAddr="2" rowAddr="2"/><hp:cellSpan colSpan="1" rowSpan="1"/><hp:cellSz width="0" height="0"/></hp:tc>"""
        val table = tbl(
            3, 3,
            tc(0, 0, p("a")) + tc(1, 0, p("b")) + tc(2, 0, p("세로"), rowSpan = 3),
            tc(1, 1, p("건너뛴 뒤")),
            tc(0, 2, p("c")) + tc(1, 2, p("d")) + zero,
        )
        val h = html(pRaw(run(table)))
        assertEquals(1, h.occurrences("class=\"gap\""), h)
        assertTrue(Regex("<td class=\"gap\"></td><td><p[^>]*>건너뛴 뒤").containsMatchIn(h), h)
        // 비활성 칸은 없다: 행마다 칸 수가 격자와 맞는다.
        assertEquals(3 + 2 + 2, h.occurrences("<td"))
    }

    @Test
    fun 표_안의_표와_캡션() {
        val inner = tbl(1, 1, tc(0, 0, p("안쪽 칸")))
        val caption = """<hp:caption side="TOP"><hp:subList>${p("표 1. 캡션")}</hp:subList></hp:caption>"""
        val outer = tbl(1, 1, tc(0, 0, pRaw(run(inner))), caption = caption)
        val h = html(pRaw(run(t("앞 글") + outer + t("뒤 글"))))
        assertTrue(Regex("<div class=\"cap\"><p[^>]*>표 1\\. 캡션</p></div><table>").containsMatchIn(h), h)
        assertTrue(Regex("<td><table><tr><td><p[^>]*>안쪽 칸").containsMatchIn(h), h)
        // 표는 문단 안에 있지만 `p` 안에 `table` 을 둘 수 없다 — 문단을 끊고 이어 연다.
        assertTrue(Regex("앞 글</p>.*</table><p[^>]*>뒤 글</p>", RegexOption.DOT_MATCHES_ALL).containsMatchIn(h), h)
        assertEquals(h.occurrences("<p"), h.occurrences("</p>"))
    }

    @Test
    fun 그림은_매니페스트의_이름으로_걸고_그릴_수_없는_형식은_센다() {
        val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
        val pic = { id: String -> """<hp:pic><hp:curSz width="7200" height="3600"/><hc:img binaryItemIDRef="$id"/><hp:sz width="7200" height="3600"/></hp:pic>""" }
        val t = TinyHwpx().section(sec(pRaw(run(pic("image1") + pic("image2") + pic("image3") + pic("없는것")))))
            .binary("image1", "BinData/my image#1.png", "image/png", png)
            .binary("image2", "BinData/image2.wmf", "image/x-wmf", byteArrayOf(1, 2, 3))
            .item("image3", "http://example.com/a.png", "image/png")
        Hwpx.doc(t).use { doc ->
            val h = doc.body()
            // 이름의 공백·`#` 는 퍼센트로 적는다(위생기가 공백을 지우고 `#` 를 조각으로 읽는다 — 함정 표).
            assertTrue("<img src=\"BinData/my%20image%231.png\" alt=\"\" style=\"width:72pt\"/>" in h, h)
            assertEquals(1, h.occurrences("<img"))
            assertNotNull(doc.openResource("BinData/my image#1.png"))
            assertNull(doc.openResource("BinData/image2.wmf"))
            val u = doc.unsupported.snapshot()
            assertEquals(2, u[UnsupportedFeatures.UNSUPPORTED_IMAGE], u.toString())
            assertEquals(1, u[UnsupportedFeatures.LINKED_FILE], u.toString())
        }
    }

    @Test
    fun 자원_상한을_넘는_그림은_img_를_쓰지_않고_센다() {
        // 바탕은 32 MiB 를 넘는 그림을 내주지 않는다. `img` 를 쓰면 깨진 그림 표시만 뜨고 배지는 조용하다(K28 의 BMP 셋).
        val huge = ByteArray((FlowDocumentBase.MAX_RESOURCE_BYTES + 1).toInt())
        val pic = """<hp:pic><hp:curSz width="7200" height="3600"/><hc:img binaryItemIDRef="big"/></hp:pic>"""
        val t = TinyHwpx().section(sec(pRaw(run(t("앞") + pic + t("뒤")))))
            .item("big", "BinData/big.bmp", "image/bmp").file("BinData/big.bmp", huge, stored = false)
        Hwpx.doc(t).use { doc ->
            val h = doc.body()
            assertFalse("<img" in h, h)
            assertTrue("앞" in h && "뒤" in h)
            assertEquals(1, doc.unsupported.snapshot()[UnsupportedFeatures.UNSUPPORTED_IMAGE], doc.unsupported.snapshot().toString())
        }
    }

    @Test
    fun 글상자의_글은_div_tb_로_쓰고_글_없는_도형만_센다() {
        val box = """<hp:rect><hp:lineShape color="#000000"/><hp:drawText><hp:subList>${p("상자 안 글")}</hp:subList><hp:textMargin left="0"/></hp:drawText></hp:rect>"""
        val bare = """<hp:ellipse><hp:lineShape color="#000000"/></hp:ellipse>"""
        val group = """<hp:container><hp:line/><hp:rect><hp:drawText><hp:subList>${p("묶음 안 글")}</hp:subList></hp:drawText></hp:rect></hp:container>"""
        val t = TinyHwpx().section(sec(pRaw(run(t("앞") + box + bare + group + t("뒤")))))
        Hwpx.doc(t).use { doc ->
            val h = doc.body()
            assertTrue(Regex("<div class=\"tb\"><p[^>]*>상자 안 글</p></div>").containsMatchIn(h), h)
            assertTrue("묶음 안 글" in h)
            assertEquals(2, doc.unsupported.snapshot()[UnsupportedFeatures.SHAPE], doc.unsupported.snapshot().toString())
        }
    }

    @Test
    fun 수식은_스크립트를_글자로_보이고_센다() {
        val eq = """<hp:equation version="Equation Version 60"><hp:sz width="1"/><hp:shapeComment>수식입니다.</hp:shapeComment><hp:script><![CDATA[{a+b} over {c<d}]]></hp:script></hp:equation>"""
        val t = TinyHwpx().section(sec(pRaw(run(t("식: ") + eq))))
        Hwpx.doc(t).use { doc ->
            assertTrue("<span class=\"math\">{a+b} over {c&lt;d}</span>" in doc.body(), doc.body())
            assertFalse("수식입니다" in doc.body())
            assertEquals(1, doc.unsupported.snapshot()[UnsupportedFeatures.EQUATION])
        }
    }

    @Test
    fun 각주는_번호_표지를_남기고_본문은_부분의_끝에_모인다() {
        val note = { n: Int, body: String ->
            """<hp:ctrl><hp:footNote number="$n"><hp:subList><hp:p paraPrIDRef="0" styleIDRef="0"><hp:run charPrIDRef="0">""" +
                """<hp:ctrl><hp:autoNum num="$n" numType="FOOTNOTE"><hp:autoNumFormat type="DIGIT" userChar="" prefixChar="" suffixChar=")" supscript="0"/></hp:autoNum></hp:ctrl>""" +
                """<hp:t> ${Hwpx.esc(body)}</hp:t></hp:run></hp:p></hp:subList></hp:footNote></hp:ctrl>"""
        }
        val t = TinyHwpx().section(
            sec(
                Hwpx.secPrParagraph("머리"),
                pRaw(run(t("대기업") + note(1, "첫째 주석") + t("은 중견기업") + note(2, "둘째 주석"))),
                p("다음 문단"),
            ),
        )
        Hwpx.doc(t).use { doc ->
            val h = doc.body()
            assertTrue("대기업<sup class=\"fnref\" id=\"fnref-1\"><a href=\"#fn-1\">1)</a></sup>은 중견기업" in plainSpans(h), h)
            val notes = h.substringAfter("<section class=\"notes\">")
            assertTrue("<div id=\"fn-1\" class=\"note\">" in notes && "첫째 주석" in notes, notes)
            assertTrue("<sup class=\"fnnum\"><a href=\"#fnref-2\">2)</a></sup>" in notes, notes)
            // 각주의 글은 본문 자리에 끼어들지 않는다.
            val bodyPart = h.substringBefore("<section class=\"notes\">")
            assertFalse("첫째 주석" in bodyPart)
            assertTrue(bodyPart.indexOf("다음 문단") > 0)
        }
    }

    @Test
    fun 바깥_하이퍼링크는_링크를_만들지_않고_표시만_한다() {
        val f = """<hp:ctrl><hp:fieldBegin id="9" type="HYPERLINK" name=""><hp:parameters cnt="2"><hp:stringParam name="Path">https://example.com/x</hp:stringParam><hp:stringParam name="Command">https\://example.com/x;1;0;0;</hp:stringParam></hp:parameters></hp:fieldBegin></hp:ctrl>"""
        val e = """<hp:ctrl><hp:fieldEnd beginIDRef="9"/></hp:ctrl>"""
        val h = html(pRaw(run(t("주소: ") + f + t("예시 누리집") + e + t(" 끝"))))
        assertTrue("<span class=\"ext\">예시 누리집</span>" in plainSpans(h), h)
        assertFalse("example.com" in h, h)
        assertFalse("<a " in h)
    }

    @Test
    fun 누름틀의_안내문과_메모의_글은_보이지_않는다() {
        val click = """<hp:ctrl><hp:fieldBegin id="1" type="CLICK_HERE" name="본문"><hp:parameters cnt="1"><hp:stringParam name="Direction">이곳을 누르세요</hp:stringParam></hp:parameters></hp:fieldBegin></hp:ctrl>"""
        val memo = """<hp:ctrl><hp:fieldBegin id="2" type="MEMO" name=""><hp:subList>${p("메모 내용")}</hp:subList></hp:fieldBegin></hp:ctrl>"""
        val t = TinyHwpx().section(
            sec(pRaw(run(click + t("채운 글") + "<hp:ctrl><hp:fieldEnd beginIDRef=\"1\"/></hp:ctrl>" + memo + t("메모 단 글") + "<hp:ctrl><hp:fieldEnd beginIDRef=\"2\"/></hp:ctrl>"))),
        )
        Hwpx.doc(t).use { doc ->
            val h = doc.body()
            assertTrue("채운 글" in h && "메모 단 글" in h)
            assertFalse("이곳을 누르세요" in h || "메모 내용" in h, h)
            assertEquals(1, doc.unsupported.snapshot()[UnsupportedFeatures.COMMENT])
        }
    }

    @Test
    fun 변경_추적의_지운_글은_숨기고_넣은_글은_보인다() {
        val inner = "<hp:t>남은 <hp:deleteBegin Id=\"1\"/>지운 <hp:deleteEnd Id=\"1\"/>글<hp:insertBegin Id=\"2\"/> 넣은 글<hp:insertEnd Id=\"2\"/></hp:t>"
        val h = html(pRaw(run(inner)))
        assertEquals(listOf("남은 글 넣은 글"), paragraphs(h))
    }

    @Test
    fun 글자_안의_탭_줄바꿈_묶음빈칸_형광펜() {
        val inner = "<hp:t>가<hp:tab width=\"100\"/>나<hp:lineBreak/>다<hp:nbSpace/>라<hp:fwSpace/>마<hp:hypen/>바" +
            "<hp:markpenBegin color=\"#FFFF00\"/>칠한 글<hp:markpenEnd/>끝</hp:t>"
        val h = html(pRaw(run(inner)))
        assertTrue("가</span>" !in h)
        val text = plain(h)
        assertTrue("가\t나" in text && "다 라 마-바" in text, text)
        assertTrue("<br/>" in h, h)
        assertTrue("<span style=\"background-color:#ffff00\">칠한 글</span>" in h, h)
        assertFalse(Regex("background-color:#ffff00\">끝").containsMatchIn(h))
    }

    @Test
    fun 본문의_switch_는_알아듣는_case_를_고르고_아니면_default() {
        val known = """<hp:switch><hp:case hp:required-namespace="http://www.hancom.co.kr/hwpml/2016/HwpUnitChar"><hp:t>고른 가지</hp:t></hp:case><hp:default><hp:t>대체 가지</hp:t></hp:default></hp:switch>"""
        val unknown = """<hp:switch><hp:case hp:required-namespace="urn:unknown"><hp:t>모르는 가지</hp:t></hp:case><hp:default><hp:t>기본 가지</hp:t></hp:default></hp:switch>"""
        val chart = """<hp:switch><hp:case hp:required-namespace="http://www.hancom.co.kr/hwpml/2016/ooxmlchart"><hp:chart chartIDRef="Chart/chart1.xml"/></hp:case><hp:default><hp:ole binaryItemIDRef="ole1"/></hp:default></hp:switch>"""
        val t = TinyHwpx().section(sec(pRaw(run(known)), pRaw(run(unknown)), pRaw(run(chart))))
        Hwpx.doc(t).use { doc ->
            val h = doc.body()
            assertTrue("고른 가지" in h && "기본 가지" in h, h)
            assertFalse("대체 가지" in h || "모르는 가지" in h, h)
            val u = doc.unsupported.snapshot()
            assertEquals(1, u[UnsupportedFeatures.CHART], u.toString())
            assertNull(u[UnsupportedFeatures.EMBEDDED_OBJECT])
        }
    }

    @Test
    fun 머리말_꼬리말은_한_번만_세고_숨은_설명은_메모로_센다() {
        val hf = """<hp:ctrl><hp:header applyPageType="BOTH"><hp:subList>${p("머리말 글")}</hp:subList></hp:header></hp:ctrl><hp:ctrl><hp:footer applyPageType="BOTH"><hp:subList>${p("꼬리말 글")}</hp:subList></hp:footer></hp:ctrl>"""
        val hidden = """<hp:ctrl><hp:hiddenComment><hp:subList>${p("숨은 글")}</hp:subList></hp:hiddenComment></hp:ctrl>"""
        val t = TinyHwpx().section(sec(pRaw(run(hf + hidden + t("본문"))))).section(sec(pRaw(run(hf + t("둘째")))))
        Hwpx.doc(t).use { doc ->
            assertFalse("머리말 글" in doc.body(0) || "꼬리말 글" in doc.body(0) || "숨은 글" in doc.body(0))
            val u = doc.unsupported.snapshot()
            assertEquals(1, u[UnsupportedFeatures.HEADER_FOOTER], u.toString())
            assertEquals(1, u[UnsupportedFeatures.COMMENT], u.toString())
        }
    }

    @Test
    fun 표_번호는_한글이_셈한_값으로_쪽_번호는_그리지_않는다() {
        val auto = """<hp:ctrl><hp:autoNum num="7" numType="TABLE"><hp:autoNumFormat type="DIGIT" userChar="" prefixChar="" suffixChar="" supscript="0"/></hp:autoNum></hp:ctrl>"""
        val page = """<hp:ctrl><hp:autoNum num="3" numType="PAGE"/></hp:ctrl><hp:ctrl><hp:pageNum pos="BOTTOM_CENTER"/></hp:ctrl>"""
        val h = html(pRaw(run(t("<표 ") + auto + t("> 제목") + page)))
        assertEquals(listOf("<표 7> 제목"), paragraphs(h))
    }

    @Test
    fun 글자는_한_번만_이스케이프되고_스크립트가_되지_않는다() {
        val h = html(p("<script>alert(1)</script> & \"따옴표\""))
        assertTrue("&lt;script&gt;" in h && "<script" !in h, h)
        assertEquals(listOf("<script>alert(1)</script> & \"따옴표\""), paragraphs(h))
    }

    @Test
    fun 빈_문단은_셋까지_줄로_남긴다() {
        val empties = (1..8).map { Hwpx.pRaw(run("<hp:t/>")) }.toTypedArray()
        val h = html(p("앞"), *empties, p("뒤"))
        assertEquals(HwpxLimits.MAX_EMPTY_RUN, h.occurrences("<br/>"), h)
    }

    @Test
    fun 덧말은_ruby_로_글자_겹치기의_모르는_사설_영역_글자는_버리고_센다() {
        val dutmal = """<hp:dutmal posType="TOP"><hp:mainText>本文</hp:mainText><hp:subText>본문</hp:subText></hp:dutmal>"""
        // 한글은 사설 영역 글자를 문자 참조가 아니라 UTF-8 그대로 적는다(K27 실측). JVM 시험의 kxml2 는 BMP 밖의
        // 문자 참조(`&#xF02B6;`)를 16 비트로 잘라 읽는다 — 그래서 여기서도 글자 그대로 넣는다.
        // U+F1234 는 옮김 표(`HancomChars`)에 없는 글자다 — 표에 있는 네모 숫자는 아래 시험이 본다.
        val pua = String(Character.toChars(0xF1234))
        val compose = """<hp:compose composeText="${pua}A"/>"""
        val t = TinyHwpx().section(sec(pRaw(run(dutmal + compose))))
        Hwpx.doc(t).use { doc ->
            val h = doc.body()
            assertTrue("<ruby>本文<rt>본문</rt></ruby>" in h, h)
            assertTrue(Regex("</ruby>A").containsMatchIn(plainSpans(h)), h)
            assertEquals(1, doc.unsupported.snapshot()[UnsupportedFeatures.TEXT_EFFECT])
        }
    }

    @Test
    fun 한컴의_사설_영역_글자는_HWP_5_0_과_같은_글자로_옮긴다() {
        // 같은 보도자료의 HWP(K11·K19)는 `１ 신규 플레이어 진입` 인데 HWPX(K33·K27)는 번호 자리가 두부였다(13단계 기기 확인).
        fun cp(c: Int) = String(Character.toChars(c))
        val compose = """<hp:compose composeText="${cp(0xF02B6)}"/>"""
        val dutmal = """<hp:dutmal posType="TOP"><hp:mainText>${cp(0xF02B2)}</hp:mainText><hp:subText>둘</hp:subText></hp:dutmal>"""
        val t = TinyHwpx().section(
            sec(
                p(cp(0xF02B1) + " 시도별 현황", paraPr = 2),
                p("점검표 " + cp(0xF0854) + "행동 요령" + cp(0xF0855) + " 참고"),
                p("\u00AD 발열이 있는 경우"),
                pRaw(run(compose) + run(t(" 산업별 일자리"))),
                pRaw(run(dutmal)),
                // 표에 없는 사설 영역 글자는 본문에서 그대로 — 대리 쌍이 쪼개지지 않는다(HWP 5.0 과 같다).
                p("모름 " + cp(0xF1234) + " 끝"),
            ),
        )
        Hwpx.doc(t).use { doc ->
            val h = plainSpans(doc.body())
            assertTrue("１ 시도별 현황" in h, h)
            assertTrue("점검표 『행동 요령』 참고" in h, h)
            assertTrue("- 발열이 있는 경우" in h, h)
            assertTrue("６ 산업별 일자리" in h, h)
            assertTrue("<ruby>２<rt>둘</rt></ruby>" in h, h)
            assertTrue("모름 ${cp(0xF1234)} 끝" in h, h)
            assertFalse(cp(0xF02B1) in h || cp(0xF02B6) in h || "\u00AD" in h, h)
            assertEquals("1. １ 시도별 현황", doc.outline.first().title) // 앞은 개요 번호
            assertEquals(null, doc.unsupported.snapshot()[UnsupportedFeatures.TEXT_EFFECT])
        }
    }

    @Test
    fun 흰_글자는_바탕을_아는_곳에서만_흰색으로_쓰고_글상자의_채우기를_칠한다() {
        // HWP 5.0 변환기와 같은 규칙 — 흐름 렌더는 도형을 글 뒤에 깔지 못해, 흰 글자가 흰 화면에서 사라진다(K25 의 칸 하나).
        val header = Hwpx.header()
            .replace("</hh:charProperties>", """<hh:charPr id="5" height="1000" textColor="#FFFFFF" shadeColor="none"><hh:fontRef hangul="0"/></hh:charPr></hh:charProperties>""")
            .replace(
                "</hh:borderFills>",
                """<hh:borderFill id="3"><hc:fillBrush><hc:winBrush faceColor="#1C3D62" hatchColor="#000000" alpha="0"/></hc:fillBrush></hh:borderFill>""" +
                    """<hh:borderFill id="4"><hc:fillBrush><hc:winBrush faceColor="#FFFFFF" hatchColor="#000000" alpha="0"/></hc:fillBrush></hh:borderFill></hh:borderFills>""",
            )
        val table = tbl(1, 3, tc(0, 0, pRaw(run(t("어두운 칸"), charPr = 5)), fill = 3) +
            tc(1, 0, pRaw(run(t("흰 칸"), charPr = 5)), fill = 4) + tc(2, 0, pRaw(run(t("면 없는 칸"), charPr = 5))))
        val box = """<hp:rect><hp:lineShape color="#000000"/><hc:fillBrush><hc:winBrush faceColor="#1C3D62" hatchColor="#000000" alpha="0"/></hc:fillBrush>""" +
            """<hp:drawText><hp:subList>${pRaw(run(t("파란 상자"), charPr = 5))}</hp:subList></hp:drawText></hp:rect>"""
        val t = TinyHwpx().also { it.header = header }
            .section(sec(pRaw(run(t("화면 위"), charPr = 5)), pRaw(run(table)), pRaw(run(box))))
        Hwpx.doc(t).use { doc ->
            val h = doc.body()
            // 바탕 글자(문단의 첫 덩이)의 색은 문단 요소가 든다 — HWP 5.0 의 `paragraphCss` 와 같다.
            assertTrue(Regex("background-color:#1c3d62\"><p style=\"color:#ffffff\">어두운 칸").containsMatchIn(h), h)
            assertTrue(Regex("<div class=\"tb\" style=\"background-color:#1c3d62\"><p style=\"color:#ffffff\">파란 상자").containsMatchIn(h), h)
            assertEquals(2, Regex("color:#ffffff").findAll(h.replace("background-color:#ffffff", "")).count(), h)
            assertFalse("background-color:#ffffff" in h, h) // 흰 면 색은 적지 않는다
            assertTrue("화면 위" in h && "흰 칸" in h && "면 없는 칸" in h, h)
        }
    }

    @Test
    fun 흰_글자는_자기_음영과_어두운_형광펜_위에서_흰색으로_쓴다() {
        // 칸의 면 색만 보면 남색 음영·검은 형광펜 위의 흰 글자가 검게 사라졌다(검토가 잡았다).
        val header = Hwpx.header().replace(
            "</hh:charProperties>",
            """<hh:charPr id="5" height="1000" textColor="#FFFFFF" shadeColor="none"><hh:fontRef hangul="0"/></hh:charPr>""" +
                """<hh:charPr id="6" height="1000" textColor="#FFFFFF" shadeColor="#1C3D62"><hh:fontRef hangul="0"/></hh:charPr></hh:charProperties>""",
        )
        val pen = """<hp:t><hp:markpenBegin color="#000000"/>형광펜 위<hp:markpenEnd/></hp:t>"""
        val t = TinyHwpx().also { it.header = header }.section(sec(pRaw(run(t("음영 위"), charPr = 6)), pRaw(run(pen, charPr = 5))))
        Hwpx.doc(t).use { doc ->
            val h = doc.body()
            // 흰 글자색은 바탕 글자의 것이라 문단에, 음영은 물려받지 않는 것이라 덩이에 적는다.
            assertTrue(Regex("<p style=\"color:#ffffff\"><span style=\"background-color:#1c3d62\">음영 위").containsMatchIn(h), h)
            assertTrue(Regex("background-color:#000000;color:#ffffff\">형광펜 위").containsMatchIn(h), h)
        }
    }

    @Test
    fun 어두운_칸_안의_흰_칸과_각주와_표_뒤의_빈_줄() {
        val header = Hwpx.header()
            .replace("</hh:charProperties>", """<hh:charPr id="5" height="1000" textColor="#FFFFFF" shadeColor="none"><hh:fontRef hangul="0"/></hh:charPr></hh:charProperties>""")
            .replace(
                "</hh:borderFills>",
                """<hh:borderFill id="3"><hc:fillBrush><hc:winBrush faceColor="#1C3D62" hatchColor="#000000" alpha="0"/></hc:fillBrush></hh:borderFill>""" +
                    """<hh:borderFill id="4"><hc:fillBrush><hc:winBrush faceColor="#FFFFFF" hatchColor="#000000" alpha="0"/></hc:fillBrush></hh:borderFill></hh:borderFills>""",
            )
        val inner = tbl(1, 1, tc(0, 0, p("안쪽 흰 칸"), fill = 4))
        // 남색 칸의 흰 글자에 달린 각주 — 각주 본문은 부분 끝의 흰 화면에 그려지므로 흰색을 적으면 안 보인다.
        val note = """<hp:ctrl><hp:footNote number="1"><hp:subList><hp:p paraPrIDRef="0" styleIDRef="0"><hp:run charPrIDRef="5">""" +
            """<hp:t>흰 각주 본문</hp:t></hp:run></hp:p></hp:subList></hp:footNote></hp:ctrl>"""
        val outer = tbl(1, 1, tc(0, 0, pRaw(run(inner)) + pRaw(run(t("흰 글") + note, charPr = 5)), fill = 3))
        val empties = tbl(1, 1, tc(0, 0, p("칸") + p("") + p("") + p("") + p("")))
        val t = TinyHwpx().also { it.header = header }
            .section(sec(pRaw(run(outer)), pRaw(run(empties)), p(""), p(""), p("뒤")))
        Hwpx.doc(t).use { doc ->
            val h = doc.body()
            assertTrue("background-color:#ffffff" in h, h) // 남색 안의 흰 칸
            val notes = h.substringAfter("class=\"notes\"")
            assertTrue("흰 각주 본문" in notes && "color:#ffffff" !in notes, notes)
            val between = h.substringAfterLast("</table>").substringBefore("뒤")
            assertEquals(2, Regex("<p[^>]*><br/></p>").findAll(between).count(), h)
        }
    }

    @Test
    fun 칸의_세로_정렬은_위와_아래만_적는다() {
        // `hp:subList/@vertAlign` 은 칸의 여는 태그보다 뒤에 온다 — 훑기가 적어 두고 그리기가 읽는다(`TableScan`).
        val table = tbl(
            1, 3,
            tc(0, 0, p("위")).replace("<hp:subList>", "<hp:subList vertAlign=\"TOP\">") +
                tc(1, 0, p("가운데")).replace("<hp:subList>", "<hp:subList vertAlign=\"CENTER\">") +
                tc(2, 0, p("아래")).replace("<hp:subList>", "<hp:subList vertAlign=\"BOTTOM\">"),
        )
        val h = html(pRaw(run(table)))
        assertTrue(Regex("<td style=\"vertical-align:top\"><p[^>]*>위").containsMatchIn(h), h)
        assertTrue(Regex("<td><p[^>]*>가운데").containsMatchIn(h), h)
        assertTrue(Regex("<td style=\"vertical-align:bottom\"><p[^>]*>아래").containsMatchIn(h), h)
    }

    /** `span` 을 벗긴 HTML(구조 비교를 서식과 떼어 놓는다). */
    private fun plainSpans(h: String): String = h.replace(Regex("<span style=\"[^\"]*\">|<span>"), "").let {
        var s = it
        // 짝이 된 닫는 `</span>` 가운데 `class` 가 없는 것만 벗긴다 — 간단히 모두 떼고 ext 만 되살린다.
        s = s.replace("</span>", "\u0000")
        s = s.replace(Regex("<span class=\"(ext|mk|math)\">([^\u0000]*)\u0000")) { m -> "<span class=\"${m.groupValues[1]}\">${m.groupValues[2]}</span>" }
        s.replace("\u0000", "")
    }
}
