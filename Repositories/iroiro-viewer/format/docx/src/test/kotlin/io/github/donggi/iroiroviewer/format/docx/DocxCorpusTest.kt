package io.github.donggi.iroiroviewer.format.docx

import io.github.donggi.iroiroviewer.format.FlowWarnings
import io.github.donggi.iroiroviewer.format.UnsupportedFeatures
import io.github.donggi.iroiroviewer.format.opc.OoxmlCorpus
import io.github.donggi.iroiroviewer.format.opc.OoxmlKind
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 실세계 docx·docm·dotx 와 그 암호판·이전 형식(.doc)을 **앱과 같은 길로** 연다(`OoxmlCorpus`).
 * 표본은 `samples-local/corpus/`(저장소에 넣지 않는다)에 있고, 없으면 건너뛴다.
 *
 * 오라클은 python-docx 의 본문 문단·표 칸(Strict 는 lxml)이다. python-docx 는 글상자·머리말·각주·
 * 넣은 글(`w:ins`)·윗주를 읽지 않으므로 **정밀도는 낮게 나오는 것이 정상**이고(우리가 더 그린다),
 * 되부름이 모자라면 우리가 빠뜨린 것이다.
 */
class DocxCorpusTest {

    /** `FormatRegistry.ooxml` 의 DOCX 갈래. 패키지가 다른 종류라고 말하면 이 모듈이 열 것이 아니다. */
    private val build: OoxmlCorpus.Build = { pkg, kind, limits, progress ->
        check(kind == OoxmlKind.DOCX) { "패키지가 $kind 라고 말한다" }
        DocxDocument.open(pkg, limits, progress)
    }

    @Test
    fun 실세계_docx_표본을_앱처럼_연다() {
        assumeTrue("samples-local/corpus 가 없다", OoxmlCorpus.dir != null)
        val entries = OoxmlCorpus.entries(OoxmlCorpus.Family.DOCX)
        assumeTrue("docx 표본이 없다", entries.isNotEmpty())
        val verdicts = entries.map { e ->
            OoxmlCorpus.verify(e, build, minRecall = 0.95, skipMust = SKIP_MUST[e.id].orEmpty()) { r -> extra(e.id, r) }
        }
        OoxmlCorpus.record(OoxmlCorpus.Family.DOCX, verdicts.map { it.line })
        val failed = verdicts.filter { it.problems.isNotEmpty() }
        assertTrue(failed.isEmpty(), failed.joinToString("\n") { "${it.entry}: ${it.problems}" })
    }

    /** 표본을 낸 프로젝트의 시험이 단언하는 구조. */
    private fun extra(id: String, r: OoxmlCorpus.Run): List<String> {
        val out = ArrayList<String>()
        val html = r.parts.joinToString("") { it.html.orEmpty() }
        val unsup = r.unsupported
        fun expect(ok: Boolean, what: String) {
            if (!ok) out.add(what)
        }
        when (id) {
            // POI TestXWPFWordExtractor#testGetSimpleText — 문단 셋.
            "DX01" -> expect(paragraphs(html).count { it.isNotBlank() } == 3, "문단이 셋이 아니다: ${paragraphs(html).size}")
            // POI TestXWPFNumbering — 0~3 글머리표, 4 decimal, 5 lowerLetter, 6 lowerRoman, 12 '%1.%2.%3.', 14 'NEW-%1-FORMAT'.
            "DX08" -> {
                val m = markers(html)
                expect(m.take(7) == listOf("•", "o", "▪", "•", "1.", "a.", "i."), "앞 일곱 표지가 다르다: $m")
                expect("1.1.1." in m && "NEW-1-FORMAT" in m, "사용자 수준 표지가 없다: $m")
            }
            // POI testCheckboxes — 'unchecked: |_|'·'Or checked: |X|'·'In Sequence: |X||_||X|'(POI 의 표기).
            // 우리는 ☐·☒ 로 그린다. 옛 양식 확인란이라 결과 글이 비어 있다 — 그리지 않던 결함을 이 표본이 잡았다.
            "DX09" -> {
                val flat = OoxmlCorpus.squash(r.text)
                for (want in listOf("unchecked: ☐", "Or checked: ☒", "☒☐☒")) {
                    expect(want in flat, "확인란 '$want' 이 없다")
                }
            }
            // LO testCommentDoneModel — 메모 둘(하나는 해결됨). 그리지 않고 센다.
            "DX14" -> expect(unsup[UnsupportedFeatures.COMMENT] == 2, "메모를 둘로 세지 않았다: $unsup")
            // python-docx shp-inline-shape-access.feature — 넣은 그림·연결 그림·연결+넣은 그림·SmartArt·차트.
            // 연결만 된 그림은 **가져오지 않는다**(세기만 한다).
            "DX19" -> {
                expect(r.images == 2 && r.imagesServed == 2, "그림 둘이 보여야 한다: ${r.imagesServed}/${r.images}")
                expect(unsup[UnsupportedFeatures.LINKED_FILE] == 1 && unsup[UnsupportedFeatures.SMART_ART] == 1 && unsup[UnsupportedFeatures.CHART] == 1, "셈이 다르다: $unsup")
            }
            // 수식(OMML) 아홉 — 문서에 `m:oMathPara` 가 아홉이다(LO testMathMso2k7 은 그중 여섯을 본다).
            // 오라클(python-docx)은 수식의 글을 보지 않아 정밀도가 0 이다 — 글 대조가 아무것도 말해 주지 않으므로 뜻이
            // 바뀌는 자리를 따로 본다. 이항정리의 이항계수(가로줄 없는 분수)가 나누기 `(n/k)` 로 보이던 결함을 잡았다.
            "DX12" -> {
                expect(unsup[UnsupportedFeatures.EQUATION] == 9, "수식을 아홉으로 세지 않았다: $unsup")
                expect("(n¦k)" in r.text && "(n/k)" !in r.text, "이항계수가 나누기로 보인다")
            }
            // 매크로는 돌리지 않고 알린다. DM02 는 이름만 .docm 이고 본문 형식은 평범한 문서다(매크로 없음).
            "DM01" -> expect(r.warned(FlowWarnings.MACROS) && unsup[UnsupportedFeatures.MACRO] == 1, "매크로를 알리지 않았다")
            "DM02" -> expect(!r.warned(FlowWarnings.MACROS), "매크로가 없는데 알렸다")
            // 빈 워드 문서 — '보여 줄 것이 없다' 가 아니라 빈 부분 하나.
            "DX18" -> expect(r.parts.size == 1, "빈 문서가 부분 하나가 아니다")
            // Strict — 그림·차트·SmartArt·수식·머리말이 다 있다(LO testStrict).
            "DX11" -> expect(r.imagesServed == 1 && listOf(UnsupportedFeatures.CHART, UnsupportedFeatures.SMART_ART, UnsupportedFeatures.EQUATION).all { (unsup[it] ?: 0) >= 1 }, "Strict 의 개체를 놓쳤다: $unsup")
            // 번호 문단 열아홉 가운데 둘은 문단 표시까지 지웠다(lxml 로 셌다) — 표지는 열일곱이어야 한다.
            // 지운 문단의 빈 글머리표가 남던 결함을 이 표본이 잡았다.
            "DX05" -> expect(markers(html).size == 17, "표지가 열일곱이 아니다: ${markers(html).size}")
        }
        return out
    }

    companion object {
        /**
         * 오라클이 단언하지만 이 앱이 **일부러** 그리지 않는 글.
         *
         * `DX05` 의 'pendant worn' 은 **지운 글**(`w:delText`)이다. POI 의 추출기는 지운 글까지 내지만
         * 화면은 워드의 최종본처럼 지운 글을 숨긴다.
         */
        val SKIP_MUST: Map<String, Set<String>> = mapOf("DX05" to setOf("pendant worn"))
    }
}
