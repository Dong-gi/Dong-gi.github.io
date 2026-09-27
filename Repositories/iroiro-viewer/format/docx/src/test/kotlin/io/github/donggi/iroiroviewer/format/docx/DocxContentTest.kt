package io.github.donggi.iroiroviewer.format.docx

import io.github.donggi.iroiroviewer.format.UnsupportedFeatures
import io.github.donggi.iroiroviewer.format.docx.Docx.p
import io.github.donggi.iroiroviewer.format.docx.Docx.paraStyle
import io.github.donggi.iroiroviewer.format.docx.Docx.run
import io.github.donggi.iroiroviewer.format.docx.Docx.styled
import io.github.donggi.iroiroviewer.format.opc.TinyOoxml
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 그림·링크·필드·각주·호환 블록·서식 — 본문 안쪽. */
class DocxContentTest {

    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2, 3, 4)

    private fun drawing(rel: String, attr: String = "r:embed", descr: String = "빨간 네모") =
        "<w:p><w:r><w:drawing><wp:inline><wp:extent cx=\"1270000\" cy=\"635000\"/>" +
            "<wp:docPr id=\"1\" name=\"Picture 1\" descr=\"$descr\"/><a:graphic>" +
            "<a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"><pic:pic>" +
            "<pic:blipFill><a:blip $attr=\"$rel\"/></pic:blipFill></pic:pic></a:graphicData></a:graphic>" +
            "</wp:inline></w:drawing></w:r></w:p>"

    @Test
    fun 관계로_가리킨_그림이_img_가_되고_자원으로_나간다() {
        val t = Docx.docx(p("그림 앞") + drawing("rIdImg"))
            .bytes("word/media/image1.png", null, png)
            .rel("word/document.xml", "rIdImg", TinyOoxml.REL_IMAGE, "media/image1.png")
        Docx.open(t).use { doc ->
            val html = doc.body()
            // 100pt = 1270000 EMU. 폭만 주고 높이는 껍데기의 `height:auto` 가 맞춘다.
            assertTrue("<img src=\"word/media/image1.png\" alt=\"빨간 네모\" style=\"width:100pt\"/>" in html, html)
            val bytes = doc.openResource("word/media/image1.png")!!.use { it.readBytes() }
            assertContentEquals(png, bytes)
            assertEquals("image/png", doc.mediaTypeOf("word/media/image1.png"))
            assertTrue(doc.unsupported.isEmpty, doc.unsupported.snapshot().toString())
        }
    }

    @Test
    fun EMF_는_그리지_않고_세고_바깥_그림은_연결된_파일로_센다() {
        val t = Docx.docx(drawing("rIdEmf") + drawing("rIdExt", attr = "r:link") + drawing("rIdExt2") + drawing("rIdNone"))
            .bytes("word/media/pic.emf", null, byteArrayOf(1, 0, 0, 0))
            .rel("word/document.xml", "rIdEmf", TinyOoxml.REL_IMAGE, "media/pic.emf")
            .rel("word/document.xml", "rIdExt", TinyOoxml.REL_IMAGE, "http://tracker.example/x.png", external = true)
            .rel("word/document.xml", "rIdExt2", TinyOoxml.REL_IMAGE, "file:///C:/secret.png", external = true)
        Docx.open(t).use { doc ->
            // 버린 것은 **열자마자** 센다(훑기). 그린 뒤에도 늘지 않는다.
            val atOpen = doc.unsupported.snapshot()
            assertEquals(2, atOpen[UnsupportedFeatures.UNSUPPORTED_IMAGE], atOpen.toString())
            assertEquals(2, atOpen[UnsupportedFeatures.LINKED_FILE], atOpen.toString())
            val html = doc.body()
            assertFalse("<img" in html, html)
            assertFalse("tracker" in html || "secret" in html)
            assertEquals(atOpen, doc.unsupported.snapshot())
        }
    }

    @Test
    fun 바깥_링크는_글자만_문서_안_링크는_책갈피로_간다() {
        val body = "<w:p><w:hyperlink r:id=\"rIdLink\">${run("예제 사이트")}</w:hyperlink></w:p>" +
            "<w:p><w:hyperlink w:anchor=\"목표 지점\">${run("여기로")}</w:hyperlink></w:p>" +
            "<w:p><w:hyperlink w:anchor=\"없는곳\">${run("갈 곳 없음")}</w:hyperlink></w:p>" +
            "<w:p><w:bookmarkStart w:id=\"0\" w:name=\"목표 지점\"/>${run("목표")}<w:bookmarkEnd w:id=\"0\"/></w:p>"
        val t = Docx.docx(body).rel("word/document.xml", "rIdLink", TinyOoxml.REL_HYPERLINK, "https://example.com/", external = true)
        Docx.open(t).use { doc ->
            val html = doc.body()
            val id = DocxIds.bookmark("목표 지점")
            assertTrue("<span class=\"ext\">" in html && "예제 사이트" in html, html)
            assertFalse("example.com" in html)
            assertTrue("<a href=\"#$id\">" in html, html)
            assertTrue("<span id=\"$id\"></span>" in html, html)
            assertEquals(1, html.occurrences("<a "), "갈 곳 없는 링크는 만들지 않는다: $html")
            assertTrue("갈 곳 없음" in html)
        }
    }

    @Test
    fun 다른_조각의_책갈피는_그_조각의_주소로_간다() {
        val styles = Docx.styles(paraStyle("Heading1", "heading 1", pPr = "<w:outlineLvl w:val=\"0\"/>"))
        val body = "<w:p><w:hyperlink w:anchor=\"끝\">${run("끝으로")}</w:hyperlink></w:p>" +
            styled("Heading1", "둘째 조각") +
            "<w:p><w:bookmarkStart w:id=\"1\" w:name=\"끝\"/>${run("끝 자리")}</w:p>" +
            "<w:p><w:hyperlink w:anchor=\"끝\">${run("같은 조각")}</w:hyperlink></w:p>"
        val options = DocxOptions(ChunkPolicy(softChars = 1, softBlocks = 1, hardChars = Int.MAX_VALUE, hardBlocks = 1000))
        Docx.open(Docx.docx(body, styles = styles), options).use { doc ->
            assertEquals(2, doc.parts.size)
            val id = DocxIds.bookmark("끝")
            assertTrue("href=\"~part-1.html#$id\"" in doc.body(0), doc.body(0))
            val second = doc.body(1)
            assertTrue("id=\"$id\"" in second)
            assertTrue("href=\"#$id\"" in second, second)
        }
    }

    @Test
    fun 본문_끝의_책갈피도_자리가_남는다() {
        // 마지막 블록 뒤의 책갈피 — 이을 문단이 없어도 `id` 는 남아야 링크가 갈 곳이 있다.
        val body = "<w:p><w:hyperlink w:anchor=\"맨끝\">${run("맨 끝으로")}</w:hyperlink></w:p>" + p("본문") +
            "<w:bookmarkStart w:id=\"1\" w:name=\"맨끝\"/><w:bookmarkEnd w:id=\"1\"/><w:sectPr/>"
        Docx.open(Docx.docx(body)).use { doc ->
            val html = doc.body()
            val id = DocxIds.bookmark("맨끝")
            assertTrue("<a href=\"#$id\">" in html, html)
            assertTrue("<span id=\"$id\"></span>" in html, html)
        }
    }

    @Test
    fun 무늬가_solid_인_음영은_무늬_색이_바탕이다() {
        // 옛 문서의 '검은 바탕에 흰 글자'. 채움(`w:fill`)만 보면 흰 글자가 흰 바탕에서 사라진다.
        val solid = "<w:shd w:val=\"solid\" w:color=\"000000\" w:fill=\"auto\"/>"
        val body = p("흰 글자", rPr = "<w:color w:val=\"FFFFFF\"/>$solid") +
            p("채움만", rPr = "<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"FFFF00\"/>") +
            "<w:tbl><w:tr><w:tc><w:tcPr><w:shd w:val=\"solid\" w:color=\"1F3864\" w:fill=\"FFFFFF\"/></w:tcPr>${p("칸")}</w:tc></w:tr></w:tbl>"
        Docx.open(Docx.docx(body)).use { doc ->
            val html = doc.body()
            assertTrue("<span style=\"color:#ffffff;background-color:#000000\">흰 글자</span>" in html, html)
            assertTrue("<span style=\"background-color:#ffff00\">채움만</span>" in html, html)
            assertTrue("<td style=\"background-color:#1f3864\">" in html, html)
        }
    }

    @Test
    fun 조각의_마지막_문단에_든_책갈피는_그_조각을_가리킨다() {
        // 책갈피가 문단 **안**에 있으면 그 문단의 조각이다. 다음 블록의 번호로 적으면 뒤 조각을 가리킨다.
        val styles = Docx.styles(paraStyle("Heading1", "heading 1", pPr = "<w:outlineLvl w:val=\"0\"/>"))
        val body = p("첫 문단") + "<w:p><w:bookmarkStart w:id=\"1\" w:name=\"앞\"/>${run("앞 조각의 끝")}</w:p>" +
            styled("Heading1", "둘째 조각") + "<w:p><w:hyperlink w:anchor=\"앞\">${run("앞으로")}</w:hyperlink></w:p>"
        val options = DocxOptions(ChunkPolicy(softChars = 1, softBlocks = 1, hardChars = Int.MAX_VALUE, hardBlocks = 1000))
        Docx.open(Docx.docx(body, styles = styles), options).use { doc ->
            assertEquals(2, doc.parts.size)
            val id = DocxIds.bookmark("앞")
            assertTrue("id=\"$id\"" in doc.body(0), doc.body(0))
            assertTrue("href=\"~part-0.html#$id\"" in doc.body(1), doc.body(1))
        }
    }

    @Test
    fun 지운_글은_빠지고_넣은_글은_나온다() {
        val body = "<w:p>${run("남은 ")}<w:del w:id=\"1\" w:author=\"a\"><w:r><w:delText>지운 글</w:delText></w:r></w:del>" +
            "<w:ins w:id=\"2\" w:author=\"a\">${run("넣은 글")}</w:ins>" +
            "<w:moveFrom w:id=\"3\" w:author=\"a\">${run("옮기기 전")}</w:moveFrom>" +
            "<w:moveTo w:id=\"4\" w:author=\"a\">${run("옮긴 뒤")}</w:moveTo></w:p>"
        Docx.open(Docx.docx(body)).use { doc ->
            assertEquals(listOf("남은 넣은 글옮긴 뒤"), paragraphs(doc.body()))
        }
    }

    /**
     * 문단 표시까지 지운 번호 문단(`pPr/rPr/del`)은 최종본에 없다. 빈 표지를 그리거나 번호를 세면 안 된다 —
     * Tika 의 `delins.docx` 에서 빈 '•' 둘이 보였고, 번호 목록이었다면 뒤의 번호가 밀렸다.
     */
    @Test
    fun 문단_표시를_지운_번호_문단은_표지도_번호도_남기지_않는다() {
        val numbering = Docx.numbering(Docx.abstractNum(1, Docx.lvl(0, "decimal", "%1.")), Docx.num(1, 1))
        val numPr = "<w:numPr><w:ilvl w:val=\"0\"/><w:numId w:val=\"1\"/></w:numPr>"
        val deletedMark = "<w:rPr><w:del w:id=\"9\" w:author=\"a\"/></w:rPr>"
        val body = Docx.li(1, 0, "하나") +
            // 글도 표시도 지웠다 — 통째로 없다.
            "<w:p><w:pPr>$numPr$deletedMark</w:pPr><w:del w:id=\"10\" w:author=\"a\"><w:r><w:delText>지운 항목</w:delText></w:r></w:del></w:p>" +
            Docx.li(1, 0, "둘") +
            // 표시만 지웠다 — 글은 남지만 번호 문단으로 서지 않는다.
            "<w:p><w:pPr>$numPr$deletedMark</w:pPr>${run("남은 글")}</w:p>" +
            Docx.li(1, 0, "셋")
        Docx.open(Docx.docx(body, numbering = numbering)).use { doc ->
            val html = doc.body()
            assertEquals(listOf("1.", "2.", "3."), markers(html))
            assertEquals(listOf("하나", "둘", "남은 글", "셋"), paragraphs(html))
            assertFalse("지운 항목" in html)
        }
    }

    /**
     * 옛 양식 확인란(`FORMCHECKBOX`)은 결과 글이 비어 있다 — 상자를 그리지 않으면 확인란이 통째로 사라진다(POI 의
     * `checkboxes.docx`). `w:checked` 가 있으면 그것, 없으면 `w:default` 가 켜짐·꺼짐이다.
     */
    @Test
    fun 옛_양식_확인란은_켜짐과_꺼짐을_상자로_그린다() {
        fun box(inner: String) =
            "<w:r><w:fldChar w:fldCharType=\"begin\"><w:ffData><w:name w:val=\"C\"/><w:checkBox><w:sizeAuto/>$inner</w:checkBox></w:ffData></w:fldChar></w:r>" +
                instr(" FORMCHECKBOX ") + fld("separate") + fld("end")
        val body = "<w:p>${run("끔: ")}${box("<w:default w:val=\"0\"/>")}</w:p>" +
            "<w:p>${run("켬: ")}${box("<w:default w:val=\"1\"/>")}</w:p>" +
            "<w:p>${run("바꿈: ")}${box("<w:default w:val=\"0\"/><w:checked/>")}</w:p>" +
            // 확인란이 아닌 양식 필드는 그대로(아무것도 그리지 않는다).
            "<w:p>${run("글칸: ")}<w:r><w:fldChar w:fldCharType=\"begin\"><w:ffData><w:textInput/></w:ffData></w:fldChar></w:r>" +
            instr(" FORMTEXT ") + fld("separate") + run("적은 값") + fld("end") + "</w:p>"
        Docx.open(Docx.docx(body)).use { doc ->
            assertEquals(listOf("끔: ☐", "켬: ☒", "바꿈: ☒", "글칸: 적은 값"), paragraphs(doc.body()))
        }
    }

    private fun fld(type: String) = "<w:r><w:fldChar w:fldCharType=\"$type\"/></w:r>"
    private fun instr(s: String) = "<w:r><w:instrText xml:space=\"preserve\">${Docx.esc(s)}</w:instrText></w:r>"

    @Test
    fun 필드는_결과만_보이고_명령은_숨는다() {
        val page = "<w:p>${run("쪽 ")}${fld("begin")}${instr(" PAGE ")}${fld("separate")}${run("7")}${fld("end")}</w:p>"
        // 명령 안에 든 필드의 결과도 명령의 일부다.
        val nested = "<w:p>${fld("begin")}${instr("IF ")}${fld("begin")}${instr("DATE")}${fld("separate")}" +
            "${run("안쪽 결과")}${fld("end")}${instr(" = 1 ")}${fld("separate")}${run("바깥 결과")}${fld("end")}</w:p>"
        val toc = "<w:p>${fld("begin")}${instr(" HYPERLINK \\l \"_Toc1\" ")}${fld("separate")}${run("목차 항목")}${fld("end")}</w:p>"
        val simple = "<w:p><w:fldSimple w:instr=\" DATE \">${run("2026-09-24")}</w:fldSimple></w:p>"
        val target = "<w:p><w:bookmarkStart w:id=\"9\" w:name=\"_Toc1\"/>${run("본문 제목")}</w:p>"
        Docx.open(Docx.docx(page + nested + toc + simple + target)).use { doc ->
            val html = doc.body()
            assertEquals(listOf("쪽 7", "바깥 결과", "목차 항목", "2026-09-24", "본문 제목"), paragraphs(html))
            assertFalse("PAGE" in html || "DATE" in html || "안쪽 결과" in html, html)
            assertTrue("<a href=\"#${DocxIds.bookmark("_Toc1")}\">목차 항목</a>" in html, html)
        }
    }

    private val notes = "<w:footnotes ${Docx.NS}>" +
        "<w:footnote w:type=\"separator\" w:id=\"-1\"><w:p><w:r><w:separator/></w:r></w:p></w:footnote>" +
        "<w:footnote w:type=\"continuationSeparator\" w:id=\"0\"><w:p><w:r><w:continuationSeparator/></w:r></w:p></w:footnote>" +
        "<w:footnote w:id=\"1\"><w:p><w:r><w:rPr><w:vertAlign w:val=\"superscript\"/></w:rPr><w:footnoteRef/></w:r>${run(" 첫 각주 본문")}</w:p></w:footnote>" +
        "<w:footnote w:id=\"2\"><w:p><w:r><w:footnoteRef/></w:r>${run(" 둘째 각주 본문")}</w:p></w:footnote>" +
        "<w:footnote w:id=\"3\"><w:p>${run("안 쓰인 각주")}</w:p></w:footnote>" +
        "</w:footnotes>"

    private val endnotes = "<w:endnotes ${Docx.NS}>" +
        "<w:endnote w:type=\"separator\" w:id=\"0\"><w:p><w:r><w:separator/></w:r></w:p></w:endnote>" +
        "<w:endnote w:id=\"1\"><w:p><w:r><w:endnoteRef/></w:r>${run(" 미주 본문")}</w:p></w:endnote>" +
        "</w:endnotes>"

    @Test
    fun 각주는_차례로_번호를_받고_조각_끝에_모인다() {
        val body = "<w:p>${run("가")}<w:r><w:footnoteReference w:id=\"1\"/></w:r></w:p>" +
            "<w:p>${run("나")}<w:r><w:footnoteReference w:id=\"2\"/></w:r></w:p>" +
            "<w:p>${run("다")}<w:r><w:endnoteReference w:id=\"1\"/></w:r></w:p>"
        Docx.open(Docx.docx(body, footnotes = notes, endnotes = endnotes)).use { doc ->
            val html = doc.body()
            assertTrue("<sup class=\"fnref\" id=\"fnref-1\"><a href=\"#fn-1\">1</a></sup>" in html, html)
            assertTrue("<sup class=\"fnref\" id=\"fnref-2\"><a href=\"#fn-2\">2</a></sup>" in html, html)
            // 미주의 기본 모양은 워드처럼 i·ii·iii.
            assertTrue("<sup class=\"fnref\" id=\"enref-1\"><a href=\"#en-1\">i</a></sup>" in html, html)
            val notesHtml = html.substringAfter("<section class=\"notes\">")
            assertTrue("<div id=\"fn-1\" class=\"note\">" in notesHtml, notesHtml)
            assertTrue("<a href=\"#fnref-1\">1</a>" in notesHtml, notesHtml)
            assertTrue(notesHtml.indexOf("첫 각주 본문") < notesHtml.indexOf("둘째 각주 본문"))
            assertTrue("<div id=\"en-1\" class=\"note\">" in notesHtml && "미주 본문" in notesHtml, notesHtml)
            // 각주 1 과 미주 1 은 id 가 같아도 다른 것이다 — 미주는 제 번호(i)와 제 자리로 돌아간다.
            assertTrue("<a href=\"#enref-1\">i</a>" in notesHtml, notesHtml)
            assertEquals(1, notesHtml.occurrences("<a href=\"#fnref-1\">"), notesHtml)
            assertFalse("안 쓰인 각주" in html)
        }
    }

    private val textBoxRun = "<w:r><mc:AlternateContent><mc:Choice Requires=\"wps\"><w:drawing><wp:anchor>" +
        "<wp:extent cx=\"100\" cy=\"100\"/><wp:docPr id=\"2\" name=\"Text Box 2\"/><a:graphic>" +
        "<a:graphicData uri=\"http://schemas.microsoft.com/office/word/2010/wordprocessingShape\"><wps:wsp><wps:txbx>" +
        "<w:txbxContent>${p("글상자 안의 글")}</w:txbxContent></wps:txbx></wps:wsp></a:graphicData></a:graphic>" +
        "</wp:anchor></w:drawing></mc:Choice><mc:Fallback><w:pict><v:shape style=\"width:100pt\"><v:textbox>" +
        "<w:txbxContent>${p("글상자 안의 글")}</w:txbxContent></v:textbox></v:shape></w:pict></mc:Fallback>" +
        "</mc:AlternateContent></w:r>"

    @Test
    fun 호환_블록은_한_가지만_그리고_글상자는_문단을_끊고_들어간다() {
        val body = "<w:p>${run("앞 글 ")}$textBoxRun${run(" 뒤 글")}</w:p>"
        Docx.open(Docx.docx(body)).use { doc ->
            val html = doc.body()
            assertEquals(1, html.occurrences("글상자 안의 글"), html)
            assertTrue("</p><div class=\"tb\"><p>글상자 안의 글</p></div><p>" in html, html)
            assertEquals(listOf("앞 글 ", "글상자 안의 글", " 뒤 글"), paragraphs(html))
            assertTrue(doc.unsupported.isEmpty, doc.unsupported.snapshot().toString())
        }
    }

    @Test
    fun 이해하는_가지는_대체보다_먼저다() {
        // 두 가지의 글이 다르면 어느 쪽을 골랐는지 보인다. `wps` 는 이해하는 이름공간이라 새 방식이 뽑힌다.
        val body = "<w:p><w:r><mc:AlternateContent><mc:Choice Requires=\"wps\"><w:t>새 방식</w:t></mc:Choice>" +
            "<mc:Fallback><w:t>옛 방식</w:t></mc:Fallback></mc:AlternateContent></w:r></w:p>" +
            // 접두어를 이름공간으로 푼다 — 같은 `wps` 라도 모르는 이름공간이면 대체로 간다.
            "<w:p><w:r><mc:AlternateContent xmlns:wps=\"urn:example:unknown\"><mc:Choice Requires=\"wps\"><w:t>가짜 새 방식</w:t>" +
            "</mc:Choice><mc:Fallback><w:t>진짜 대체</w:t></mc:Fallback></mc:AlternateContent></w:r></w:p>"
        Docx.open(Docx.docx(body)).use { doc ->
            assertEquals(listOf("새 방식", "진짜 대체"), paragraphs(doc.body()))
        }
    }

    @Test
    fun 이해하지_못하는_가지는_대체를_고르고_대체가_없으면_센다() {
        val emoji = "<w:p><w:r><mc:AlternateContent><mc:Choice Requires=\"w16se\">" +
            "<w16se:symEx w16se:font=\"Segoe UI Emoji\" w16se:char=\"1F600\"/></mc:Choice>" +
            "<mc:Fallback><w:t>\uD83D\uDE00</w:t></mc:Fallback></mc:AlternateContent></w:r></w:p>"
        val chartEx = "<w:p><w:r><mc:AlternateContent><mc:Choice Requires=\"cx1\"><w:drawing/></mc:Choice>" +
            "</mc:AlternateContent></w:r>${run("차트 뒤")}</w:p>"
        val vmlOnly = "<w:p><w:r><w:pict><v:shape><v:textbox><w:txbxContent>${p("VML 글상자")}</w:txbxContent>" +
            "</v:textbox></v:shape></w:pict></w:r></w:p>"
        Docx.open(Docx.docx(emoji + chartEx + vmlOnly)).use { doc ->
            val html = doc.body()
            assertTrue("\uD83D\uDE00" in html, html)
            assertTrue("차트 뒤" in html && "VML 글상자" in html)
            assertEquals(1, doc.unsupported.snapshot()[UnsupportedFeatures.UNKNOWN_ELEMENT])
        }
    }

    @Test
    fun 글자_서식은_문단_바탕과_다른_것만_적는다() {
        val styles = Docx.styles(
            paraStyle("Normal", "Normal", default = true),
            paraStyle("Heading1", "heading 1", "Normal", "<w:outlineLvl w:val=\"0\"/>", "<w:b/>"),
            Docx.charStyle("Strong", "Strong", "<w:i/>"),
            defaults = "<w:docDefaults><w:rPrDefault><w:rPr><w:sz w:val=\"22\"/></w:rPr></w:rPrDefault></w:docDefaults>",
        )
        val body = "<w:p>" + run("굵게", "<w:b/>") + run("기울임", "<w:rStyle w:val=\"Strong\"/>") +
            run("밑줄취소", "<w:u w:val=\"single\"/><w:strike/>") + run("위", "<w:vertAlign w:val=\"superscript\"/>") +
            run("빨강형광", "<w:color w:val=\"FF0000\"/><w:highlight w:val=\"yellow\"/>") +
            run("크게", "<w:sz w:val=\"44\"/>") + run("<script>나쁜 색</script>", "<w:color w:val=\"red;background:url(http://x)\"/>") +
            run("숨김", "<w:vanish/>") + run("밑줄 없음", "<w:u w:val=\"none\"/>") + "</w:p>" +
            "<w:p><w:pPr><w:pStyle w:val=\"Heading1\"/></w:pPr>" + run("제목 굵게") + run(" 제목 보통", "<w:b w:val=\"0\"/>") + "</w:p>"
        Docx.open(Docx.docx(body, styles = styles)).use { doc ->
            val html = doc.body()
            assertTrue("<span style=\"font-weight:bold\">굵게</span>" in html, html)
            assertTrue("<span style=\"font-style:italic\">기울임</span>" in html, html)
            assertTrue("<span style=\"text-decoration:underline line-through\">밑줄취소</span>" in html, html)
            assertTrue("<sup>위</sup>" in html, html)
            assertTrue("<span style=\"color:#ff0000;background-color:#ffff00\">빨강형광</span>" in html, html)
            assertTrue("<span style=\"font-size:200%\">크게</span>" in html, html)
            assertTrue("&lt;script&gt;나쁜 색&lt;/script&gt;" in html && "url(" !in html, html)
            assertFalse("숨김" in html)
            assertTrue("&lt;/script&gt;밑줄 없음</p>" in html, html)
            // 문단 스타일의 굵게는 문단 요소에 한 번, 끈 글자만 따로. 제목은 크기를 늘 적는다(브라우저의 2em 을 막는다).
            assertTrue(
                "<h1 id=\"h-1\" style=\"font-size:100%;font-weight:bold\">제목 굵게<span style=\"font-weight:normal\"> 제목 보통</span></h1>" in html,
                html,
            )
        }
    }

    @Test
    fun 기호와_탭과_수식() {
        val body = "<w:p><w:r><w:sym w:font=\"Symbol\" w:char=\"F061\"/><w:sym w:font=\"Wingdings\" w:char=\"F0FC\"/>" +
            "<w:sym w:font=\"Wingdings\" w:char=\"F021\"/></w:r>${run("가\t나")}</w:p>" +
            "<w:p>${run("식: ")}<m:oMath><m:f><m:num><m:r><m:t>a+b</m:t></m:r></m:num><m:den><m:r><m:t>c</m:t></m:r></m:den></m:f>" +
            "<m:sSup><m:e><m:r><m:t>x</m:t></m:r></m:e><m:sup><m:r><m:t>2</m:t></m:r></m:sup></m:sSup></m:oMath></w:p>"
        Docx.open(Docx.docx(body)).use { doc ->
            val html = doc.body()
            assertTrue("\u03B1\u2713" in html, html)
            assertTrue("가\t나" in html)
            assertTrue("<span class=\"math\">(a+b)/cx^2</span>" in html, html)
            assertEquals(1, doc.unsupported.snapshot()[UnsupportedFeatures.EQUATION])
            assertTrue(doc.partHtml(0)!!.contains("white-space:pre-wrap"))
        }
    }

    /**
     * 가로줄 없는 분수(`m:type=noBar`)는 나누기가 아니다 — 이항계수가 그것이다. `(n/k)` 로 적으면 n÷k 로 읽혀
     * 뜻이 바뀐다(실세계 표본 DX12 의 이항정리가 그렇게 보였다). 가로줄이 있는 분수는 그대로 `/` 다.
     */
    @Test
    fun 가로줄_없는_분수는_나누기로_적지_않는다() {
        fun frac(type: String?) = "<m:f>" + (type?.let { "<m:fPr><m:type m:val=\"$it\"/></m:fPr>" } ?: "") +
            "<m:num><m:r><m:t>n</m:t></m:r></m:num><m:den><m:r><m:t>k</m:t></m:r></m:den></m:f>"
        val body = "<w:p><m:oMath><m:d><m:e>${frac("noBar")}</m:e></m:d></m:oMath></w:p>" +
            "<w:p><m:oMath>${frac("lin")}</m:oMath></w:p>" +
            "<w:p><m:oMath>${frac(null)}</m:oMath></w:p>"
        Docx.open(Docx.docx(body)).use { doc ->
            val html = doc.body()
            assertTrue("<span class=\"math\">(n¦k)</span>" in html, html)
            assertEquals(2, Regex("<span class=\"math\">n/k</span>").findAll(html).count(), html)
        }
    }

    @Test
    fun 그리지_못하는_것을_열자마자_센다() {
        val chart = "<w:p><w:r><w:drawing><wp:inline><wp:extent cx=\"1\" cy=\"1\"/><wp:docPr id=\"3\" name=\"Chart 1\"/><a:graphic>" +
            "<a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/chart\"><c:chart r:id=\"rIdC\"/></a:graphicData>" +
            "</a:graphic></wp:inline></w:drawing></w:r></w:p>"
        val smartArt = "<w:p><w:r><w:drawing><wp:inline><a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/diagram\">" +
            "<dgm:relIds r:dm=\"rId1\" r:lo=\"rId2\" r:qs=\"rId3\" r:cs=\"rId4\"/></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>"
        val shape = "<w:p><w:r><w:drawing><wp:anchor><a:graphic><a:graphicData uri=\"http://schemas.microsoft.com/office/word/2010/wordprocessingShape\">" +
            "<wps:wsp><wps:spPr><a:prstGeom prst=\"rightArrow\"/></wps:spPr><wps:bodyPr/></wps:wsp></a:graphicData></a:graphic></wp:anchor></w:drawing></w:r></w:p>"
        val effect = p("빛나는 글", rPr = "<w14:glow w14:rad=\"63500\"/>")
        val ole = "<w:p><w:r><w:object><v:shape/></w:object></w:r></w:p>"
        val comment = "<w:p><w:commentRangeStart w:id=\"0\"/>${run("메모 단 글")}<w:commentRangeEnd w:id=\"0\"/><w:r><w:commentReference w:id=\"0\"/></w:r></w:p>"
        val section = "<w:p><w:pPr><w:sectPr><w:headerReference w:type=\"default\" r:id=\"rIdH\"/></w:sectPr></w:pPr></w:p>"
        val bodySection = "<w:sectPr><w:footerReference w:type=\"default\" r:id=\"rIdF\"/></w:sectPr>"
        Docx.open(Docx.docx(chart + smartArt + shape + effect + ole + comment + section + bodySection)).use { doc ->
            val counts = doc.unsupported.snapshot()
            assertEquals(1, counts[UnsupportedFeatures.CHART], counts.toString())
            assertEquals(1, counts[UnsupportedFeatures.SMART_ART], counts.toString())
            assertEquals(1, counts[UnsupportedFeatures.SHAPE], counts.toString())
            assertEquals(1, counts[UnsupportedFeatures.TEXT_EFFECT], counts.toString())
            assertEquals(1, counts[UnsupportedFeatures.EMBEDDED_OBJECT], counts.toString())
            assertEquals(1, counts[UnsupportedFeatures.COMMENT], counts.toString())
            // 머리글·바닥글은 구역이 몇이든 문서에 한 번.
            assertEquals(1, counts[UnsupportedFeatures.HEADER_FOOTER], counts.toString())
            val html = doc.body()
            assertTrue("빛나는 글" in html && "메모 단 글" in html)
            assertEquals(counts, doc.unsupported.snapshot())
        }
    }

    @Test
    fun 문서_속성의_제목과_빈_문단_줄이기() {
        val core = "<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\"" +
            " xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:title>  보고서\n 제목 </dc:title></cp:coreProperties>"
        val body = p("A") + "<w:p/>".repeat(10) + p("B")
        val t = Docx.docx(body).xml("docProps/core.xml", null, core).rel(null, "rIdCore", Docx.CORE_PROPS, "docProps/core.xml")
        Docx.open(t).use { doc ->
            assertEquals("보고서 제목", doc.title)
            val html = doc.body()
            assertEquals(DocxWalker.MAX_EMPTY_RUN, html.occurrences("<br"), html)
            assertEquals(listOf("A", "", "", "", "B"), paragraphs(html))
        }
    }

    @Test
    fun VML_그림과_각주_안의_그림은_제_부분의_관계로_푼다() {
        val vml = "<w:p><w:r><w:pict><v:shape style=\"width:36pt;height:18pt\"><v:imagedata r:id=\"rIdV\" o:title=\"\"/>" +
            "</v:shape></w:pict></w:r></w:p>"
        val body = vml + "<w:p>${run("각주 있음")}<w:r><w:footnoteReference w:id=\"1\"/></w:r></w:p>"
        val foot = "<w:footnotes ${Docx.NS}><w:footnote w:id=\"1\"><w:p><w:r><w:footnoteRef/></w:r>" +
            drawing("rIdF").removePrefix("<w:p>").removeSuffix("</w:p>") + "</w:p></w:footnote></w:footnotes>"
        // 같은 id(`rIdF`)가 본문 관계에서는 다른 그림을 가리킨다 — 각주는 각주 부분의 관계로 풀어야 한다.
        val t = Docx.docx(body, footnotes = foot)
            .bytes("word/media/vml.png", null, png)
            .bytes("word/media/note.png", null, png)
            .bytes("word/media/wrong.png", null, png)
            .rel("word/document.xml", "rIdV", TinyOoxml.REL_IMAGE, "media/vml.png")
            .rel("word/document.xml", "rIdF", TinyOoxml.REL_IMAGE, "media/wrong.png")
            .rel("word/footnotes.xml", "rIdF", TinyOoxml.REL_IMAGE, "media/note.png")
        Docx.open(t).use { doc ->
            val html = doc.body()
            assertTrue("<img src=\"word/media/vml.png\" alt=\"\" style=\"width:36pt\"/>" in html, html)
            val notesHtml = html.substringAfter("<section class=\"notes\">")
            assertTrue("src=\"word/media/note.png\"" in notesHtml, notesHtml)
            assertFalse("wrong.png" in html)
        }
    }

    @Test
    fun 사용자_각주_표지는_뒤따르는_글자를_링크로_감싸고_번호를_쓰지_않는다() {
        val body = "<w:p>${run("가")}<w:r><w:footnoteReference w:customMarkFollows=\"1\" w:id=\"1\"/><w:t>*</w:t></w:r>" +
            "${run("나")}<w:r><w:footnoteReference w:id=\"2\"/></w:r></w:p>"
        Docx.open(Docx.docx(body, footnotes = notes)).use { doc ->
            val html = doc.body()
            assertTrue("<a href=\"#fn-1\">*</a>" in html, html)
            // 사용자 표지는 번호를 쓰지 않으므로 다음 각주가 1 이다.
            assertTrue("<sup class=\"fnref\" id=\"fnref-1\"><a href=\"#fn-2\">1</a></sup>" in html, html)
        }
    }

    @Test
    fun 각주_설정의_번호_모양을_따른다() {
        val settings = "<w:settings ${Docx.NS}><w:footnotePr><w:numFmt w:val=\"upperLetter\"/><w:numStart w:val=\"3\"/></w:footnotePr></w:settings>"
        val body = "<w:p>${run("가")}<w:r><w:footnoteReference w:id=\"1\"/></w:r></w:p>"
        Docx.open(Docx.docx(body, footnotes = notes, settings = settings)).use { doc ->
            val html = doc.body()
            assertTrue("<a href=\"#fn-1\">C</a>" in html, html)
            assertNotNull(html.substringAfter("<section class=\"notes\">", "").takeIf { "첫 각주 본문" in it })
        }
    }
}
