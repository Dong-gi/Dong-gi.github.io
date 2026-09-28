package io.github.donggi.iroiroviewer.format.pptx

import io.github.donggi.iroiroviewer.format.ByteArrayDocumentSource
import io.github.donggi.iroiroviewer.format.FlowWarnings
import io.github.donggi.iroiroviewer.format.ProgressSink
import io.github.donggi.iroiroviewer.format.UnsupportedFeatures
import io.github.donggi.iroiroviewer.format.opc.OpcPackage
import io.github.donggi.iroiroviewer.safety.ParseLimits
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 독립 오라클 — **python-pptx 가 쓴** 발표 자료를 우리가 읽어, python-pptx 가 **다시 읽은** 글자와 견준다.
 *
 * `oracle.pptx` 는 python-pptx 의 기본 템플릿(실제 파워포인트 마스터·테마·레이아웃)으로 만들고,
 * 쓰지 않는 레이아웃과 템플릿의 이진 조각(인쇄기 설정·썸네일)을 걷어 18KB 로 줄였다. 만든 스크립트는
 * 저장소에 두지 않는다(CLAUDE.md '표본'). `oracle.golden.txt` 는 python-pptx 가 그 파일을 다시 열어
 * 슬라이드마다 도형 순서대로 문단 글자를 적은 것이다.
 *
 * 실세계 발표 자료는 `PptxCorpusTest` 가 연다(`samples-local/corpus/` 의 pptx 무리를 앱과 같은 길로, python-pptx 가 다시 읽은 글과
 * 견주며 — 부분 실패도 거기서 본다). 예전에 여기 있던 '`samples-local/pptx/` 의 표본을 모두 그린다' 는 그 폴더가 없어 늘
 * 건너뛰었고 하는 일이 말뭉치 시험에 다 들어 있어 지웠다.
 */
class PptxOracleTest {

    private fun resource(name: String): ByteArray =
        requireNotNull(javaClass.getResourceAsStream("/pptx/$name")) { name }.use { it.readBytes() }

    /** `# slide N` 으로 나뉜 골든을 슬라이드별 줄 목록으로. */
    private fun golden(text: String): List<List<String>> {
        val out = ArrayList<MutableList<String>>()
        for (line in text.lines()) {
            if (line.startsWith("# slide ")) out.add(ArrayList()) else if (line.isNotBlank()) out.last().add(line.trim())
        }
        return out
    }

    private fun open(bytes: ByteArray): PptxFlowDocument = runBlocking {
        val pkg = OpcPackage.open(ByteArrayDocumentSource(bytes, "oracle.pptx"), ParseLimits.DEFAULT)
        PptxDocument.open(pkg, ParseLimits.DEFAULT, ProgressSink.NONE) as PptxFlowDocument
    }

    @Test
    fun python_pptx_가_쓴_글자와_같다() {
        val expected = golden(resource("oracle.golden.txt").toString(Charsets.UTF_8))
        open(resource("oracle.pptx")).use { doc ->
            assertEquals(expected.size, doc.parts.size)
            for ((i, lines) in expected.withIndex()) {
                assertEquals(lines, textOf(doc.partHtml(i)!!).lines(), "슬라이드 ${i + 1}")
            }
            assertTrue(doc.warnings.none { it.code == FlowWarnings.PART_FAILED || it.code == FlowWarnings.TRUNCATED }, doc.warnings.toString())
            // 제목 틀이 있는 슬라이드는 그 글자가 이름이다. 네 번째(빈 레이아웃)는 없다.
            assertEquals(listOf("분기 보고서 2026", "목차", "표와 도형", ""), doc.parts.map { it.label })
        }
    }

    @Test
    fun 실제_템플릿의_상속을_따른다() {
        open(resource("oracle.pptx")).use { doc ->
            val first = doc.partHtml(0)!!
            // 제목 슬라이드 레이아웃의 ctrTitle 자리(685800, 2130425) — 슬라이드에는 `<p:spPr/>` 뿐이다.
            assertEquals("calc(var(--u)*7.5)", prop(boxStyleOf(first, "분기 보고서 2026"), "left"))
            // 마스터 titleStyle 44pt, 가운데.
            assertEquals("calc(var(--u)*6.1111)", prop(runStyleOf(first, "분기 보고서 2026"), "font-size"))
            assertEquals("center", prop(paraStyleOf(first, "분기 보고서 2026"), "text-align"))
            // 부제목은 레이아웃의 lstStyle(buNone·가운데·tx1 75% 농도) — 마스터 bodyStyle 의 글머리를 덮는다.
            assertTrue("class=\"bu\"" !in first, first)
            val sub = runStyleOf(first, "iroiro 시험용 발표 자료")
            assertTrue(prop(sub, "color")!! != "#000000", sub)

            val second = doc.partHtml(1)!!
            val bullets = Regex("<span class=\"bu\"[^>]*>([^<]*)</span>").findAll(second).map { it.groupValues[1] }.toList()
            assertEquals(listOf("•", "–", "•"), bullets)
            assertEquals("calc(var(--u)*3.8889)", prop(runStyleOf(second, "하위 항목 &amp; &lt;기호&gt;"), "font-size"))

            val third = doc.partHtml(2)!!
            // python-pptx 의 표는 기본 표 서식('보통 스타일 2 - 강조 1') — 머리 행이 테마의 강조 1(4F81BD).
            val head = third.substringBefore("이름").substringAfterLast("<td")
            assertTrue("background-color:#4f81bd" in head, head)
            assertEquals("#ff0000", prop(runStyleOf(third, "빨간 글"), "color"))
            assertEquals("bold", prop(runStyleOf(third, "굵은 "), "font-weight"))

            // 숨긴 슬라이드의 그림은 패키지에서 내준다.
            val fourth = doc.partHtml(3)!!
            val src = Regex("<img src=\"([^\"]+)\"").find(fourth)!!.groupValues[1]
            assertTrue(doc.openResource(src) != null, src)
            assertTrue(doc.unsupported.snapshot()[UnsupportedFeatures.UNSUPPORTED_IMAGE] == null)
        }
    }
}
