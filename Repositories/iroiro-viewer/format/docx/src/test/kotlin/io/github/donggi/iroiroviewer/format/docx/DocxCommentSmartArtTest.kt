package io.github.donggi.iroiroviewer.format.docx

import io.github.donggi.iroiroviewer.format.FlowWarnings
import io.github.donggi.iroiroviewer.format.UnsupportedFeatures
import io.github.donggi.iroiroviewer.format.docx.Docx.NS
import io.github.donggi.iroiroviewer.format.docx.Docx.esc
import io.github.donggi.iroiroviewer.format.docx.Docx.p
import io.github.donggi.iroiroviewer.format.docx.Docx.run
import io.github.donggi.iroiroviewer.format.opc.TinyOoxml
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 12단계가 세기만 하던 두 가지 — **메모의 본문**과 **SmartArt 의 글** — 을 그린다. 둘 다 '보인 것은 버린 것으로 세지 않는다' 로
 * 배지를 정한다(보이지 않는 것만 센다).
 */
class DocxCommentSmartArtTest {

    // ---- 메모 ----

    private val relComments = "${TinyOoxml.R}/comments"

    /** 메모 하나. [paras] 는 본문 문단들. */
    private fun comment(id: Int, initials: String?, author: String?, vararg paras: String) =
        "<w:comment w:id=\"$id\"" + (author?.let { " w:author=\"${esc(it)}\"" } ?: "") +
            (initials?.let { " w:initials=\"${esc(it)}\"" } ?: "") + ">" +
            // 워드는 첫 문단 앞에 메모 쪽의 표지 자리(`w:annotationRef`)를 둔다 — 머리가 대신하므로 그리지 않는다.
            "<w:p><w:r><w:annotationRef/></w:r>" + paras.first().removePrefix("<w:p>") + paras.drop(1).joinToString("") +
            "</w:comment>"

    private fun comments(vararg items: String) = "<w:comments $NS>${items.joinToString("")}</w:comments>"

    /** 메모가 달린 문단 — 범위(`commentRangeStart`…`End`) 뒤에 표지가 온다(워드의 모양). */
    private fun commented(id: Int, before: String, target: String, after: String) =
        "<w:p>${run(before)}<w:commentRangeStart w:id=\"$id\"/>${run(target)}<w:commentRangeEnd w:id=\"$id\"/>" +
            "<w:r><w:commentReference w:id=\"$id\"/></w:r>${run(after)}</w:p>"

    private fun withComments(t: TinyOoxml, xml: String): TinyOoxml =
        t.xml("word/comments.xml", null, xml).rel("word/document.xml", "rIdCm", relComments, "comments.xml")

    @Test
    fun 메모는_범위_끝의_표지와_부분_끝의_본문으로_보인다() {
        val body = commented(3, "앞 ", "메모 단 낱말", " 뒤") + p("다음 문단")
        val cms = comments(
            comment(3, "KD", "김동기", p("첫 문단"), p("둘째 문단")),
            comment(7, "XY", "아무개", p("가리키지 않은 메모")),
        )
        Docx.open(withComments(Docx.docx(body), cms)).use { doc ->
            val html = doc.body()
            val main = html.substringBefore("<section class=\"comments\">")
            val side = html.substringAfter("<section class=\"comments\">", "")
            // 표지는 범위 끝, 머리글자 + 메모 부분에 적힌 차례(워드의 옛 표지 `[KD1]`).
            assertTrue("메모 단 낱말<sup class=\"cmref\" id=\"cmref-1\"><a href=\"#cm-1\">[KD1]</a></sup> 뒤" in main, main)
            assertTrue(
                "<div id=\"cm-1\" class=\"cmt\"><p class=\"cmh\"><sup class=\"cmnum\"><a href=\"#cmref-1\">[KD1]</a></sup> 김동기</p>" in side,
                side,
            )
            assertEquals(listOf("[KD1] 김동기", "첫 문단", "둘째 문단"), paragraphs(side), side)
            // 가리키지 않은 메모는 워드도 보이지 않는다.
            assertFalse("가리키지 않은 메모" in html, html)
            // 보인 메모는 버린 것이 아니다 — 12단계는 그리지 않고 셌다.
            assertNull(doc.unsupported.snapshot()[UnsupportedFeatures.COMMENT], doc.unsupported.snapshot().toString())
        }
    }

    @Test
    fun 메모_부분이_없거나_깨졌으면_표지_없이_버린_것으로_센다() {
        val body = commented(0, "", "글", "")
        Docx.open(Docx.docx(body)).use { doc ->
            assertFalse("cmref" in doc.body(), doc.body())
            assertEquals(1, doc.unsupported.snapshot()[UnsupportedFeatures.COMMENT])
        }
        // 읽지 못하는 메모 부분 — 닫는 태그가 어긋났다. (잘린 XML 은 JVM 시험의 kxml2 가 조용히 끝내므로 쓰지 않는다 —
        // CLAUDE.md 함정 표.)
        val broken = "<w:comments $NS><w:comment w:id=\"0\"><w:p></w:r></w:comment></w:comments>"
        Docx.open(withComments(Docx.docx(body), broken)).use { doc ->
            assertFalse("cmref" in doc.body(), doc.body())
            assertEquals(1, doc.unsupported.snapshot()[UnsupportedFeatures.COMMENT])
            assertTrue(doc.warnings.any { it.code == FlowWarnings.AUX_FAILED }, doc.warnings.toString())
        }
        // 부분은 멀쩡한데 가리키는 메모가 없다 — 보일 글이 없으니 버린 것이다.
        Docx.open(withComments(Docx.docx(body), comments(comment(5, "A", "B", p("다른 메모"))))).use { doc ->
            assertFalse("cmref" in doc.body() || "다른 메모" in doc.body(), doc.body())
            assertEquals(1, doc.unsupported.snapshot()[UnsupportedFeatures.COMMENT])
        }
    }

    @Test
    fun 숨긴_글의_메모_표지는_보이지도_세지도_않는다() {
        val hidden = "<w:p>${run("보이는 글")}<w:r><w:rPr><w:vanish/></w:rPr><w:commentReference w:id=\"0\"/></w:r></w:p>"
        Docx.open(withComments(Docx.docx(hidden), comments(comment(0, "A", "B", p("숨은 메모"))))).use { doc ->
            val html = doc.body()
            assertFalse("cmref" in html || "숨은 메모" in html, html)
            assertNull(doc.unsupported.snapshot()[UnsupportedFeatures.COMMENT])
        }
    }

    @Test
    fun 메모는_가리킨_조각의_끝에_그리고_번호는_조각과_관계없다() {
        val policy = ChunkPolicy(softChars = 1_000_000, softBlocks = 2, hardChars = 1_000_000, hardBlocks = 2)
        val body = p("하나") + p("둘") + commented(9, "", "셋째 문단의 글", "") + commented(4, "", "넷째", "")
        val cms = comments(comment(4, "B", "을", p("메모 을")), comment(9, "A", "갑", p("메모 갑")))
        Docx.open(withComments(Docx.docx(body), cms), DocxOptions(chunk = policy)).use { doc ->
            assertEquals(2, doc.parts.size)
            assertFalse("comments" in doc.body(0), doc.body(0))
            val second = doc.body(1)
            // 번호는 메모 부분에 적힌 차례다(4 가 1번, 9 가 2번) — 본문에 나온 차례가 아니다.
            assertTrue("[A2]" in second && "[B1]" in second, second)
            // 메모 쪽의 차례도 메모 부분의 차례다.
            val side = second.substringAfter("<section class=\"comments\">")
            assertTrue(side.indexOf("메모 을") in 0 until side.indexOf("메모 갑"), side)
        }
    }

    @Test
    fun 각주_안의_메모도_그_조각의_메모_쪽에_든다() {
        val footnotes = "<w:footnotes $NS><w:footnote w:id=\"1\"><w:p><w:r><w:footnoteRef/></w:r>" +
            "${run(" 각주 글")}<w:r><w:commentReference w:id=\"0\"/></w:r></w:p></w:footnote></w:footnotes>"
        val body = "<w:p>${run("본문")}<w:r><w:footnoteReference w:id=\"1\"/></w:r></w:p>"
        val t = withComments(Docx.docx(body, footnotes = footnotes), comments(comment(0, "FN", "각주에 단 이", p("각주의 메모"))))
        Docx.open(t).use { doc ->
            val html = doc.body()
            val notes = html.substringAfter("<section class=\"notes\">").substringBefore("</section>")
            assertTrue("<sup class=\"cmref\" id=\"cmref-1\"><a href=\"#cm-1\">[FN1]</a></sup>" in notes, notes)
            // 메모 쪽은 각주 뒤에 온다.
            assertTrue(html.indexOf("<section class=\"notes\">") < html.indexOf("<section class=\"comments\">"), html)
            assertTrue("각주의 메모" in html.substringAfter("<section class=\"comments\">"), html)
            assertNull(doc.unsupported.snapshot()[UnsupportedFeatures.COMMENT])
        }
    }

    @Test
    fun 가리킨_메모의_본문에서만_버린_것을_센다() {
        val chart = "<w:p><w:r><w:drawing><wp:inline><a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/chart\">" +
            "<c:chart r:id=\"rIdC\"/></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>"
        val cms = comments(comment(0, "A", "갑", p("차트가 든 메모"), chart), comment(1, "B", "을", p("가리키지 않음"), chart))
        Docx.open(withComments(Docx.docx(commented(0, "", "글", "")), cms)).use { doc ->
            assertTrue("차트가 든 메모" in doc.body())
            assertEquals(1, doc.unsupported.snapshot()[UnsupportedFeatures.CHART], doc.unsupported.snapshot().toString())
        }
    }

    @Test
    fun 머리글자와_지은이의_제어_문자는_버린다() {
        // 속성 값의 제어 문자는 문자 참조로만 들어온다(XML 이 속성의 공백을 고르게 바꾸기 전에).
        val cms = "<w:comments $NS><w:comment w:id=\"0\" w:author=\"김&#10;동기  씨\" w:initials=\" K&#9;D \">${p("본문")}</w:comment></w:comments>"
        Docx.open(withComments(Docx.docx(commented(0, "", "글", "")), cms)).use { doc ->
            val html = doc.body()
            assertTrue("[KD1]" in html && "김동기 씨" in html, html)
        }
    }

    // ---- SmartArt ----

    private val relData = "${TinyOoxml.R}/diagramData"
    private val relDrawing = "http://schemas.microsoft.com/office/2007/relationships/diagramDrawing"

    /** 본문의 SmartArt 자리(`dgm:relIds`). */
    private fun smartArtRun(dm: String) =
        "<w:r><w:drawing><wp:inline><wp:extent cx=\"5486400\" cy=\"3200400\"/><wp:docPr id=\"1\" name=\"Diagram 1\"/><a:graphic>" +
            "<a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/diagram\">" +
            "<dgm:relIds r:dm=\"$dm\" r:lo=\"rIdLo\" r:qs=\"rIdQs\" r:cs=\"rIdCs\"/></a:graphicData></a:graphic></wp:inline></w:drawing></w:r>"

    private fun data(drawingRelId: String?) =
        "<dgm:dataModel xmlns:dgm=\"http://schemas.openxmlformats.org/drawingml/2006/diagram\" " +
            "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\"><dgm:ptLst/>" +
            (drawingRelId?.let {
                "<dgm:extLst><a:ext uri=\"http://schemas.microsoft.com/office/drawing/2008/diagram\">" +
                    "<dsp:dataModelExt xmlns:dsp=\"http://schemas.microsoft.com/office/drawing/2008/diagram\" relId=\"$it\"/></a:ext></dgm:extLst>"
            } ?: "") + "</dgm:dataModel>"

    /** 도형 하나 — [paras] 는 (글, 수준). 빈 목록이면 글상자만 있고 글이 없는 도형(화살표). */
    private fun sp(prst: String, vararg paras: Pair<String, Int>) =
        "<dsp:sp modelId=\"{0}\"><dsp:nvSpPr><dsp:cNvPr id=\"0\" name=\"\"/><dsp:cNvSpPr/></dsp:nvSpPr><dsp:spPr><a:prstGeom prst=\"$prst\"/></dsp:spPr>" +
            "<dsp:txBody><a:bodyPr/><a:lstStyle/>" +
            (if (paras.isEmpty()) "<a:p><a:endParaRPr/></a:p>" else paras.joinToString("") { (t, lvl) ->
                "<a:p><a:pPr lvl=\"$lvl\"/>" + t.split('|').joinToString("<a:br/>") { "<a:r><a:rPr lang=\"ko-KR\"/><a:t>${esc(it)}</a:t></a:r>" } + "</a:p>"
            }) + "</dsp:txBody></dsp:sp>"

    private fun drawing(vararg shapes: String) =
        "<dsp:drawing xmlns:dsp=\"http://schemas.microsoft.com/office/drawing/2008/diagram\" " +
            "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\"><dsp:spTree><dsp:nvGrpSpPr/><dsp:grpSpPr/>" +
            shapes.joinToString("") + "</dsp:spTree></dsp:drawing>"

    /** 데이터 [n] 과 그림 [n] 을 가진 패키지. [byExt] 면 데이터의 `dataModelExt` 가 그림 관계를 가리킨다. */
    private fun withSmartArt(t: TinyOoxml, n: Int, drawingXml: String?, byExt: Boolean = true): TinyOoxml {
        t.xml("word/diagrams/data$n.xml", null, data(if (byExt) "rIdDr$n" else null))
            .rel("word/document.xml", "rIdDm$n", relData, "diagrams/data$n.xml")
        if (drawingXml != null) {
            t.xml("word/diagrams/drawing$n.xml", null, drawingXml).rel("word/document.xml", "rIdDr$n", relDrawing, "diagrams/drawing$n.xml")
        }
        return t
    }

    private fun smartArtTexts(html: String): List<List<String>> =
        Regex("<div class=\"sa\">(.*?)</div>", RegexOption.DOT_MATCHES_ALL).findAll(html).map { paragraphs(it.groupValues[1]) }.toList()

    @Test
    fun SmartArt_는_캐시된_그림의_글을_도형_차례로_그리고_글_없는_도형만_센다() {
        val body = "<w:p>${run("앞 ")}${smartArtRun("rIdDm1")}${run(" 뒤")}</w:p>"
        // 순환형처럼 자리와 차례가 다른 도형 — 자리(위→아래)로 늘어놓지 않는다.
        val dr = drawing(sp("ellipse", "가" to 0), sp("rightArrow"), sp("ellipse", "나" to 0), sp("rightArrow"), sp("ellipse", "다" to 0))
        Docx.open(withSmartArt(Docx.docx(body), 1, dr)).use { doc ->
            val html = doc.body()
            assertEquals(listOf(listOf("가", "나", "다")), smartArtTexts(html), html)
            // 문단을 끊고 상자를 둔 뒤 이어 연다(글상자와 같다).
            assertTrue(Regex("<p>앞 </p><div class=\"sa\">.*</div><p> 뒤</p>").containsMatchIn(html), html)
            val u = doc.unsupported.snapshot()
            assertNull(u[UnsupportedFeatures.SMART_ART], u.toString())
            assertEquals(2, u[UnsupportedFeatures.SHAPE], u.toString())
        }
    }

    @Test
    fun SmartArt_의_목록_수준은_들여쓰고_줄바꿈은_지킨다() {
        val dr = drawing(sp("roundRect", "제목" to 0, "하위 하나|둘째 줄" to 1, "" to 1))
        Docx.open(withSmartArt(Docx.docx("<w:p>${smartArtRun("rIdDm1")}</w:p>"), 1, dr)).use { doc ->
            val html = doc.body()
            assertTrue("<div class=\"sa\"><p>제목</p><p style=\"margin-left:12pt\">하위 하나<br/>둘째 줄</p></div>" in html, html)
            assertNull(doc.unsupported.snapshot()[UnsupportedFeatures.SHAPE])
        }
    }

    @Test
    fun 그림_부분을_찾지_못하면_SmartArt_로_센다() {
        val body = "<w:p>${smartArtRun("rIdDm1")}</w:p>"
        // LibreOffice 가 쓴 파일처럼 캐시된 그림이 없다 — 글을 알 길이 없다(pptx 와 같은 판단).
        Docx.open(withSmartArt(Docx.docx(body), 1, null)).use { doc ->
            assertFalse("class=\"sa\"" in doc.body(), doc.body())
            assertEquals(1, doc.unsupported.snapshot()[UnsupportedFeatures.SMART_ART])
        }
        // 그림 부분이 깨졌다.
        Docx.open(withSmartArt(Docx.docx(body), 1, "<dsp:drawing><dsp:spTree>")).use { doc ->
            assertFalse("class=\"sa\"" in doc.body(), doc.body())
            assertEquals(1, doc.unsupported.snapshot()[UnsupportedFeatures.SMART_ART])
        }
        // 도형이 하나도 없는 그림.
        Docx.open(withSmartArt(Docx.docx(body), 1, drawing())).use { doc ->
            assertEquals(1, doc.unsupported.snapshot()[UnsupportedFeatures.SMART_ART])
        }
    }

    @Test
    fun 데이터가_그림을_가리키지_않으면_번호로_짝짓는다() {
        // `dataModelExt` 가 없는 파일 — 오피스가 붙이는 이름(`data2.xml` ↔ `drawing2.xml`)으로 짝짓는다.
        val body = "<w:p>${smartArtRun("rIdDm2")}</w:p>"
        val t = withSmartArt(withSmartArt(Docx.docx(body), 1, drawing(sp("rect", "첫째 그림" to 0)), byExt = false), 2, drawing(sp("rect", "둘째 그림" to 0)), byExt = false)
        Docx.open(t).use { doc -> assertEquals(listOf(listOf("둘째 그림")), smartArtTexts(doc.body()), doc.body()) }
    }

    @Test
    fun 뒤_조각의_SmartArt_도_제_글을_그린다() {
        // 번호는 걷기 상태에 있다 — 조각이 중간에서 시작해도 훑기가 적어 둔 번호와 같은 번호를 센다.
        val policy = ChunkPolicy(softChars = 1_000_000, softBlocks = 2, hardChars = 1_000_000, hardBlocks = 2)
        val body = "<w:p>${smartArtRun("rIdDm1")}</w:p>" + p("사이") + "<w:p>${smartArtRun("rIdDm2")}</w:p>"
        val t = withSmartArt(withSmartArt(Docx.docx(body), 1, drawing(sp("rect", "앞 조각" to 0))), 2, drawing(sp("rect", "뒤 조각" to 0)))
        Docx.open(t, DocxOptions(chunk = policy)).use { doc ->
            assertEquals(2, doc.parts.size)
            assertEquals(listOf(listOf("앞 조각")), smartArtTexts(doc.body(0)), doc.body(0))
            assertEquals(listOf(listOf("뒤 조각")), smartArtTexts(doc.body(1)), doc.body(1))
        }
    }

    @Test
    fun SmartArt_의_글이_상한을_넘으면_자르고_알린다() {
        val long = "가".repeat(DocxSmartArt.MAX_CHARS + 500)
        Docx.open(withSmartArt(Docx.docx("<w:p>${smartArtRun("rIdDm1")}</w:p>"), 1, drawing(sp("rect", long to 0)))).use { doc ->
            val texts = smartArtTexts(doc.body())
            assertEquals(DocxSmartArt.MAX_CHARS, texts.single().single().length)
            assertTrue(doc.warnings.any { it.code == FlowWarnings.TRUNCATED }, doc.warnings.toString())
        }
    }

    // ---- 검토가 더한 것 ----

    @Test
    fun SmartArt_의_문단_수에도_상한이_있다() {
        // 글자 상한(2만 자)만 두면 한 글자짜리 도형 2만 개가 그 안에 든다 — 문단마다 객체가 따로라 글자보다 수십 배 무겁다.
        val many = drawing(*Array(DocxSmartArt.MAX_PARAGRAPHS + 50) { sp("rect", "가" to 0) })
        Docx.open(withSmartArt(Docx.docx("<w:p>${smartArtRun("rIdDm1")}</w:p>"), 1, many)).use { doc ->
            assertEquals(DocxSmartArt.MAX_PARAGRAPHS, smartArtTexts(doc.body()).single().size)
            assertTrue(doc.warnings.any { it.code == FlowWarnings.TRUNCATED }, doc.warnings.toString())
        }
    }

    @Test
    fun 훑기가_적어_두는_SmartArt_의_합은_글자가_아니라_무게로_잰다() {
        // 한 글자짜리 문단 천 개 — 글자는 천 자지만 객체는 삼천 개다. 글자 합으로 재면 이런 그림 수천 개(수백 MB)가 상한 안에 든다.
        val art = SmartArtText(List(DocxSmartArt.MAX_PARAGRAPHS) { listOf(SmartArtPara("가", 0)) }, 0, truncated = false)
        val collector = ScanCollector(ChunkPolicy(), WalkState()) {}
        var stored = 0
        while (stored < 100_000 && collector.smartArt(stored, art)) stored++
        assertTrue(stored > 0)
        assertTrue(stored.toLong() * art.approxBytes() <= ScanCollector.MAX_SMART_ART_BYTES, "적어 둔 것 $stored 개")
        // 무게는 글자의 두 배(UTF-16)보다 훨씬 크다 — 문단마다의 객체 머리를 센다.
        assertTrue(art.approxBytes() > 50L * art.chars, "무게 ${art.approxBytes()}")
    }

    @Test
    fun SmartArt_를_읽다가_취소되면_멈춘다() {
        // 그림 부분 하나가 32 MB 까지 들어온다 — 도형을 읽는 동안에도 취소를 본다(여는 동안의 시간 상한이 끼어들 틈).
        val xml = drawing(*Array(200) { sp("rect", "글 $it" to 0) })
        val p = io.github.donggi.iroiroviewer.safety.SafeXml.newParser(xml.byteInputStream(), null, io.github.donggi.iroiroviewer.safety.ParseLimits.DEFAULT)
        var calls = 0
        kotlin.test.assertFailsWith<java.util.concurrent.CancellationException> {
            DocxSmartArt.read(p, io.github.donggi.iroiroviewer.safety.ParseLimits.DEFAULT) {
                if (++calls == 2) throw java.util.concurrent.CancellationException("취소")
            }
        }
        assertEquals(2, calls)
    }

    @Test
    fun SmartArt_상자_뒤의_빈_줄은_앞의_빈_문단_셈에_먹히지_않는다() {
        // 빈 문단은 셋까지 줄로 남긴다(`MAX_EMPTY_RUN`). 상자를 담은 문단은 끊긴 채 닫혀 그 셈을 되돌리지 않았다 — 상자가 보였는데도
        // 바로 뒤의 빈 줄이 '넷째 빈 문단' 으로 사라졌다.
        val empty = "<w:p/>"
        val body = empty.repeat(DocxWalker.MAX_EMPTY_RUN) + "<w:p>${smartArtRun("rIdDm1")}</w:p>" + empty + p("끝")
        Docx.open(withSmartArt(Docx.docx(body), 1, drawing(sp("rect", "상자" to 0)))).use { doc ->
            val html = doc.body()
            assertTrue(html.substringAfter("<div class=\"sa\">").substringAfter("</div>").startsWith("<p><br/></p><p>끝</p>"), html)
        }
    }

    @Test
    fun 같은_데이터를_가리키는_SmartArt_는_한_번_읽어_둘_다_그린다() {
        val body = "<w:p>${smartArtRun("rIdDm1")}</w:p>" + p("사이") + "<w:p>${smartArtRun("rIdDm1")}</w:p>"
        Docx.open(withSmartArt(Docx.docx(body), 1, drawing(sp("rect", "같은 그림" to 0), sp("rightArrow")))).use { doc ->
            assertEquals(listOf(listOf("같은 그림"), listOf("같은 그림")), smartArtTexts(doc.body()), doc.body())
            // 글 없는 도형은 가리킬 때마다 센다 — 그림 둘에 화살표가 하나씩 보이지 않았다.
            assertEquals(2, doc.unsupported.snapshot()[UnsupportedFeatures.SHAPE])
        }
    }

    @Test
    fun 같은_id_의_메모가_둘이면_첫째만_그리고_센다() {
        // 목록은 먼저 나온 것을 쓴다. 뒤의 것까지 그리면 같은 `id` 의 상자가 둘이 되고 뒤의 본문이 앞의 번호를 단다.
        val chart = "<w:p><w:r><w:drawing><wp:inline><a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/chart\">" +
            "<c:chart r:id=\"rIdC\"/></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>"
        val cms = comments(comment(0, "A", "갑", p("첫째 메모")), comment(0, "B", "을", p("같은 번호의 둘째"), chart))
        Docx.open(withComments(Docx.docx(commented(0, "", "글", "")), cms)).use { doc ->
            val html = doc.body()
            assertEquals(1, html.occurrences("id=\"cm-1\""), html)
            assertTrue("첫째 메모" in html && "같은 번호의 둘째" !in html, html)
            assertNull(doc.unsupported.snapshot()[UnsupportedFeatures.CHART], doc.unsupported.snapshot().toString())
        }
    }
}
