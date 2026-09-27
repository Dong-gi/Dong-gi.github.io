package io.github.donggi.iroiroviewer.format.docx

import io.github.donggi.iroiroviewer.format.FlowWarnings
import io.github.donggi.iroiroviewer.format.ProgressSink
import io.github.donggi.iroiroviewer.format.docx.Docx.p
import io.github.donggi.iroiroviewer.format.docx.Docx.run
import io.github.donggi.iroiroviewer.format.opc.OpcPackage
import io.github.donggi.iroiroviewer.format.opc.TinyOoxml
import io.github.donggi.iroiroviewer.safety.ParseLimits
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 깨진 문서·상한·취소·id 의 안전 — 무너지지 않는 것. */
class DocxRobustnessTest {

    @Test
    fun 깨진_본문은_깨진_자리까지_보여_주고_부분_실패를_알린다() {
        val broken = "<?xml version=\"1.0\"?><w:document ${Docx.NS}><w:body>" + p("살아남는 글") +
            "<w:p><w:r><w:t>깨진</w:r></w:p>" + p("못 가는 글") + "</w:body></w:document>"
        Docx.open(Docx.docx("", rawDocument = broken)).use { doc ->
            assertEquals(1, doc.parts.size)
            val html = doc.body()
            assertTrue("살아남는 글" in html, html)
            assertFalse("못 가는 글" in html)
            assertTrue(doc.warnings.any { it.code == FlowWarnings.PART_FAILED }, doc.warnings.toString())
            // 부분 전체를 실패 표시(⚠)로 바꾸지 않는다.
            assertFalse("part-failed" in html)
        }
    }

    @Test
    fun XML_깊이_상한을_넘으면_거기까지만_그리고_알린다() {
        val deep = "<w:customXml>".repeat(300) + p("너무 깊은 글") + "</w:customXml>".repeat(300)
        Docx.open(Docx.docx(p("앞 문단") + deep)).use { doc ->
            val html = doc.body()
            assertTrue("앞 문단" in html)
            assertFalse("너무 깊은 글" in html)
            assertTrue(doc.warnings.any { it.code == FlowWarnings.TRUNCATED }, doc.warnings.toString())
        }
    }

    @Test
    fun 쓰기_상한에_닿으면_앞부분을_닫아서_주고_알린다() {
        val body = (1..400).joinToString("") { p("문단 $it 입니다") }
        Docx.open(Docx.docx(body), DocxOptions(maxChars = 3000)).use { doc ->
            val html = doc.body()
            assertTrue("문단 1 입니다" in html)
            assertFalse("문단 400 입니다" in html)
            assertTrue(doc.warnings.any { it.code == FlowWarnings.TRUNCATED })
            assertEquals(html.occurrences("<p"), html.occurrences("</p>"))
        }
    }

    @Test
    fun 쓰기_상한에_닿은_뒤에는_닫는_태그도_늘지_않는다() {
        // 문단 하나, 글자 덩이 2 만 개. 상한 뒤에 연 태그를 `HtmlWriter` 가 열린 것으로만 세고 닫는 태그는
        // 쓰므로, 걷기가 멈추지 않으면 덩이마다 `</span></sup>` 이 붙어 결과가 상한의 수십 배가 된다.
        val pieces = (1..20_000).joinToString("") { "<w:t>${'a' + it % 26}</w:t>" }
        val body = "<w:p><w:r><w:rPr><w:b/><w:vertAlign w:val=\"superscript\"/></w:rPr>$pieces</w:r></w:p>" + p("뒤 문단")
        Docx.open(Docx.docx(body), DocxOptions(maxChars = 3000)).use { doc ->
            val html = doc.body()
            assertTrue(html.length < 3000 + 500, "길이 ${html.length}")
            assertEquals(html.occurrences("<span"), html.occurrences("</span>"))
            assertEquals(html.occurrences("<sup"), html.occurrences("</sup>"))
            assertTrue(doc.warnings.any { it.code == FlowWarnings.TRUNCATED })
        }
    }

    @Test
    fun 각주마다_관계_표를_새로_짓지_않는다() {
        // 각주 1 만 개가 저마다 그림을 두고, 각주 부분의 관계가 5 만 개다. 각주마다 관계 표를 새로 지으면
        // 5 억 번을 넣게 되어 조각 하나에 수십 초가 걸리고, 그동안 문서의 잠금을 쥐고 있다.
        val notes = 10_000
        val rels = 50_000
        // 한 조각에 다 들게 문단 열 개에 천 개씩(조각은 블록 3 천 개에서 끊긴다).
        val refs = (0 until 10).joinToString("") { para ->
            "<w:p>" + (1..notes / 10).joinToString("") { "<w:r><w:footnoteReference w:id=\"${para * (notes / 10) + it}\"/></w:r>" } + "</w:p>"
        }
        val foot = StringBuilder("<w:footnotes ${Docx.NS}>")
        for (i in 1..notes) {
            foot.append("<w:footnote w:id=\"$i\"><w:p><w:r><w:drawing><wp:inline><a:graphic><a:graphicData><pic:pic>")
                .append("<pic:blipFill><a:blip r:embed=\"rId${i % rels}\"/></pic:blipFill></pic:pic></a:graphicData>")
                .append("</a:graphic></wp:inline></w:drawing></w:r></w:p></w:footnote>")
        }
        foot.append("</w:footnotes>")
        val t = Docx.docx(refs, footnotes = foot.toString()).bytes("word/media/a.png", null, byteArrayOf(0x89.toByte(), 0x50))
        for (i in 0 until rels) t.rel("word/footnotes.xml", "rId$i", TinyOoxml.REL_IMAGE, "media/a.png")
        Docx.open(t).use { doc ->
            val started = System.nanoTime()
            val html = doc.body()
            val ms = (System.nanoTime() - started) / 1_000_000
            assertEquals(notes, html.occurrences("<img"))
            // 고치기 전에는 이 기계에서 20 초를 넘었고, 고친 뒤에는 0.3 초 안팎이다.
            assertTrue(ms < 5_000, "조각 하나를 그리는 데 ${ms}ms")
        }
    }

    @Test
    fun 조각의_시작_상태가_상한을_넘으면_더_끊지_않는다() {
        val numbering = Docx.numbering(Docx.abstractNum(0, Docx.lvl(0, "decimal", "%1.")), Docx.num(1, 0))
        val body = (1..40).joinToString("") { Docx.li(1, 0, "항목 $it") }
        val tiny = DocxOptions(ChunkPolicy(softChars = Int.MAX_VALUE, softBlocks = 1, hardChars = Int.MAX_VALUE, hardBlocks = 5, snapshotBudget = 1))
        Docx.open(Docx.docx(body, numbering = numbering), tiny).use { doc ->
            // 상한이 첫 상태조차 담지 못하면 한 조각이다 — 글과 번호는 하나도 잃지 않는다.
            assertEquals(1, doc.parts.size)
            assertEquals((1..40).map { "$it." }, markers(doc.body()))
            assertEquals((1..40).map { "항목 $it" }, paragraphs(doc.body()))
        }
    }

    @Test
    fun 목록_수천_개_뒤의_조각_수백_개가_기억을_다_쓰지_않는다() {
        // 목록 2 천 개를 한 번씩 쓴 뒤 빈 문단 2 만 개를 20 개씩 끊는다. 조각마다 목록 상태 전체를 복사하면
        // 천 벌이 되어 시험 JVM(512MB)에서 기억이 바닥난다(실측). 제품 값(3 천 블록)이라도 6MB 짜리 문서로
        // 같은 일이 난다 — 스크래치의 `make_list_bomb.py` 가 만든 표본이 그랬다.
        val lists = 2000
        val numbering = StringBuilder("<w:numbering ${Docx.NS}>")
        for (i in 1..lists) numbering.append(Docx.abstractNum(i, Docx.lvl(0, "decimal", "%1.")))
        for (i in 1..lists) numbering.append(Docx.num(i, i))
        numbering.append("</w:numbering>")
        val body = StringBuilder()
        for (i in 1..lists) body.append(Docx.li(i, 0, "목록 $i"))
        repeat(20_000) { body.append("<w:p/>") }
        body.append(p("마지막 문단"))
        val options = DocxOptions(ChunkPolicy(softChars = Int.MAX_VALUE, softBlocks = 20, hardChars = Int.MAX_VALUE, hardBlocks = 20))
        Docx.open(Docx.docx(body.toString(), numbering = numbering.toString()), options).use { doc ->
            assertTrue(doc.parts.size in 2..200, "조각 수 ${doc.parts.size}")
            assertTrue("마지막 문단" in doc.body(doc.parts.size - 1))
        }
    }

    @Test
    fun 본문_부분이_없으면_부분이_없는_문서다() {
        val t = TinyOoxml().xml("word/other.xml", null, "<x/>")
        Docx.open(t).use { doc ->
            assertTrue(doc.parts.isEmpty())
            assertNull(doc.partHtml(0))
        }
    }

    @Test
    fun 본문_관계가_없어도_관례의_이름으로_찾는다() {
        val t = TinyOoxml().xml("word/document.xml", TinyOoxml.CT_DOCX_MAIN, Docx.document(p("관계 없는 본문")))
        Docx.open(t).use { doc -> assertTrue("관계 없는 본문" in doc.body()) }
    }

    @Test
    fun 취소된_코루틴에서는_열지_않는다() {
        assertFailsWith<CancellationException> {
            runBlocking {
                cancel()
                DocxDocument.open(OpcPackage.open(Docx.docx(p("x")).source(), ParseLimits.DEFAULT), ParseLimits.DEFAULT, ProgressSink.NONE)
            }
        }
    }

    @Test
    fun 책갈피_id_는_안전하고_서로_다르다() {
        val names = listOf("_Toc123", "한글 이름", "a-b", "a_b", "a-2db", "A B\"<>", "x".repeat(300), "x".repeat(299) + "y")
        val ids = names.map { DocxIds.bookmark(it) }
        for (id in ids) assertTrue(Regex("^bm-[a-z0-9-]+$").matches(id), id)
        assertEquals(ids.size, ids.toSet().size, ids.toString())
        assertNotEquals(DocxIds.bookmark("a-b"), DocxIds.bookmark("a_b"))
        assertEquals("bm-abc", DocxIds.bookmark("abc"))

        val body = names.take(6).joinToString("") { "<w:p><w:bookmarkStart w:id=\"1\" w:name=\"${Docx.esc(it)}\"/>${run("t")}</w:p>" }
        Docx.open(Docx.docx(body)).use { doc ->
            val found = Regex("id=\"([^\"]*)\"").findAll(doc.body()).map { it.groupValues[1] }.toList()
            assertEquals(ids.take(6), found)
        }
    }

    @Test
    fun 스타일_표가_깨져도_본문은_열린다() {
        val t = Docx.docx(Docx.styled("Heading1", "제목") + p("본문"), styles = "<w:styles ${Docx.NS}><w:style")
        Docx.open(t).use { doc ->
            assertEquals(listOf("제목", "본문"), paragraphs(doc.body()))
            assertEquals(listOf("제목"), doc.outline.map { it.title })
            // 서식을 잃었다는 사실은 말한다 — 조용히 넘기면 문서가 원래 그렇게 생긴 줄 안다.
            assertTrue(doc.warnings.any { it.code == FlowWarnings.AUX_FAILED && it.detail == "word/styles.xml" }, doc.warnings.toString())
        }
    }

    @Test
    fun 성한_보조_부분은_알리지_않는다() {
        Docx.open(Docx.docx(p("본문"))).use { doc ->
            assertFalse(doc.warnings.any { it.code == FlowWarnings.AUX_FAILED }, doc.warnings.toString())
        }
    }

    @Test
    fun 스타일_사슬의_고리를_끊는다() {
        val styles = Docx.styles(
            Docx.paraStyle("A", "Style A", "B"),
            Docx.paraStyle("B", "Style B", "A", rPr = "<w:i/>"),
        )
        Docx.open(Docx.docx(Docx.styled("A", "고리"), styles = styles)).use { doc ->
            assertEquals(listOf("고리"), paragraphs(doc.body()))
            assertTrue("font-style:italic" in doc.body())
        }
    }

    @Test
    fun 번호_스타일_고리를_끊는다() {
        // 추상 번호 0 이 목록 스타일 L 을 가리키고, L 의 번호가 다시 추상 번호 0 을 가리킨다.
        val styles = Docx.styles(
            "<w:style w:type=\"numbering\" w:styleId=\"L\"><w:name w:val=\"L\"/><w:pPr><w:numPr><w:numId w:val=\"1\"/></w:numPr></w:pPr></w:style>",
        )
        val numbering = Docx.numbering(
            "<w:abstractNum w:abstractNumId=\"0\"><w:numStyleLink w:val=\"L\"/>${Docx.lvl(0, "decimal", "%1)")}</w:abstractNum>",
            Docx.num(1, 0),
        )
        Docx.open(Docx.docx(Docx.li(1, 0, "고리 목록"), styles = styles, numbering = numbering)).use { doc ->
            assertEquals(listOf("1)"), markers(doc.body()))
        }
    }

    @Test
    fun 다시_그려도_같은_HTML_이다() {
        val body = (1..30).joinToString("") { Docx.li(1, 0, "항목 $it") }
        val numbering = Docx.numbering(Docx.abstractNum(0, Docx.lvl(0, "decimal", "%1.")), Docx.num(1, 0))
        val options = DocxOptions(ChunkPolicy(softChars = Int.MAX_VALUE, softBlocks = 1, hardChars = Int.MAX_VALUE, hardBlocks = 7))
        Docx.open(Docx.docx(body, numbering = numbering), options).use { doc ->
            val first = doc.parts.indices.map { doc.partHtml(it) }
            // 캐시(셋)에서 밀려난 조각을 다시 그린다 — 조각의 시작 상태가 망가지지 않았어야 한다.
            val again = doc.parts.indices.reversed().map { doc.partHtml(it) }.reversed()
            assertEquals(first, again)
            assertEquals((1..30).map { "$it." }, first.flatMap { markers(it!!) })
        }
    }
}
