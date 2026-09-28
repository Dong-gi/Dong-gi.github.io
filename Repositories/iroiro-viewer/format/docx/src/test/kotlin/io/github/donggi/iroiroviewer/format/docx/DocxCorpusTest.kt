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
            // LO testCommentDoneModel — 메모 둘(둘째 문단 둘·셋). 12단계는 그리지 않고 셌다. 이제 범위 끝에 표지를 두고
            // 부분 끝에 본문을 그린다 — **보인 메모는 세지 않는다.**
            "DX14" -> {
                expect(unsup[UnsupportedFeatures.COMMENT] == null, "보인 메모를 버린 것으로 셌다: $unsup")
                val comments = html.substringAfter("<section class=\"comments\">", "")
                expect(comments.isNotEmpty(), "메모 쪽이 없다")
                val bodies = listOf(
                    "A two-paragraph comment.", "This is the second paragraph, and it is done.",
                    "A three-paragraph comment.", "The second paragraph.", "The third paragraph.",
                )
                expect(bodies.all { it in comments }, "메모 본문이 빠졌다: ${comments.take(800)}")
                // 머리글자(MK) + 문서 차례. 표지는 본문 쪽에, 누르면 메모로 — 메모의 머리는 표지로 돌아간다.
                val body = html.substringBefore("<section class=\"comments\">")
                for (n in 1..2) {
                    expect("<sup class=\"cmref\" id=\"cmref-$n\"><a href=\"#cm-$n\">[MK$n]</a></sup>" in body, "본문에 메모 표지 $n 이 없다")
                    expect("<div id=\"cm-$n\" class=\"cmt\"><p class=\"cmh\"><sup class=\"cmnum\"><a href=\"#cmref-$n\">[MK$n]</a></sup> Mike Kaganski</p>" in comments, "메모 $n 의 머리가 다르다")
                }
                // 메모가 달린 낱말 바로 뒤에 표지가 온다(`w:commentReference` 는 범위 끝에 있다).
                expect(Regex("eget(</span>)?<sup class=\"cmref\"").containsMatchIn(body), "표지가 범위 끝에 있지 않다")
            }
            // python-docx shp-inline-shape-access.feature — 넣은 그림·연결 그림·연결+넣은 그림·SmartArt·차트.
            // 연결만 된 그림은 **가져오지 않는다**(세기만 한다). SmartArt 는 캐시된 그림의 글(foo·bar·baz)을 그리고, 글 없는
            // 화살표 둘은 도형으로 센다 — 보인 SmartArt 는 SmartArt 로 세지 않는다(pptx 와 같은 판단).
            "DX19" -> {
                expect(r.images == 2 && r.imagesServed == 2, "그림 둘이 보여야 한다: ${r.imagesServed}/${r.images}")
                expect(
                    unsup[UnsupportedFeatures.LINKED_FILE] == 1 && unsup[UnsupportedFeatures.SMART_ART] == null &&
                        unsup[UnsupportedFeatures.SHAPE] == 2 && unsup[UnsupportedFeatures.CHART] == 1,
                    "셈이 다르다: $unsup",
                )
                expect(smartArtTexts(html) == listOf(listOf("foo", "bar", "baz")), "SmartArt 의 글이 다르다: ${smartArtTexts(html)}")
            }
            // LO 의 SmartArt 시험 표본 — 목록형 셋. 캐시된 그림의 도형 차례대로, 뒤의 큰 화살표 하나는 도형으로.
            "DX13" -> {
                expect(smartArtTexts(html) == listOf(listOf("Sample", "SmartArt", "LibreOffice?")), "SmartArt 의 글이 다르다: ${smartArtTexts(html)}")
                expect(unsup[UnsupportedFeatures.SMART_ART] == null && unsup[UnsupportedFeatures.SHAPE] == 1, "셈이 다르다: $unsup")
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
            // Strict — 그림·차트·SmartArt·수식·머리말이 다 있다(LO testStrict). SmartArt 는 순환형 a·b·c — 자리로 늘어놓으면
            // a·c·b 가 되므로 그림 부분의 도형 차례(데이터의 노드 차례)를 따르는지 본다. 화살표 셋은 도형으로 센다.
            "DX11" -> {
                expect(r.imagesServed == 1 && listOf(UnsupportedFeatures.CHART, UnsupportedFeatures.EQUATION).all { (unsup[it] ?: 0) >= 1 }, "Strict 의 개체를 놓쳤다: $unsup")
                expect(smartArtTexts(html) == listOf(listOf("a", "b", "c")), "SmartArt 의 글이 다르다: ${smartArtTexts(html)}")
                expect(unsup[UnsupportedFeatures.SMART_ART] == null && unsup[UnsupportedFeatures.SHAPE] == 3, "셈이 다르다: $unsup")
            }
            // 번호 문단 열아홉 가운데 둘은 문단 표시까지 지웠다(lxml 로 셌다) — 표지는 열일곱이어야 한다.
            // 지운 문단의 빈 글머리표가 남던 결함을 이 표본이 잡았다.
            "DX05" -> expect(markers(html).size == 17, "표지가 열일곱이 아니다: ${markers(html).size}")
        }
        return out
    }

    /** SmartArt 상자(`div.sa`)마다 그 안 문단의 글. */
    private fun smartArtTexts(html: String): List<List<String>> =
        Regex("<div class=\"sa\">(.*?)</div>", RegexOption.DOT_MATCHES_ALL).findAll(html).map { paragraphs(it.groupValues[1]) }.toList()

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
