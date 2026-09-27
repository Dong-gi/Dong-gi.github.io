package io.github.donggi.iroiroviewer.format.pptx

import io.github.donggi.iroiroviewer.format.FlowWarnings
import io.github.donggi.iroiroviewer.format.UnsupportedFeatures
import io.github.donggi.iroiroviewer.format.opc.OoxmlCorpus
import io.github.donggi.iroiroviewer.format.opc.OoxmlKind
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 실세계 pptx·pptm·ppsx 와 그 암호판을 **앱과 같은 길로** 연다(`OoxmlCorpus`).
 * 표본은 `samples-local/corpus/`(저장소에 넣지 않는다)에 있고, 없으면 건너뛴다.
 */
class PptxCorpusTest {

    private val build: OoxmlCorpus.Build = { pkg, kind, limits, progress ->
        check(kind == OoxmlKind.PPTX) { "패키지가 $kind 라고 말한다" }
        PptxDocument.open(pkg, limits, progress)
    }

    @Test
    fun 실세계_pptx_표본을_앱처럼_연다() {
        assumeTrue("samples-local/corpus 가 없다", OoxmlCorpus.dir != null)
        val entries = OoxmlCorpus.entries(OoxmlCorpus.Family.PPTX)
        assumeTrue("pptx 표본이 없다", entries.isNotEmpty())
        val verdicts = entries.map { e ->
            OoxmlCorpus.verify(e, build, minRecall = RECALL[e.id] ?: 0.95, skipMust = SKIP_MUST[e.id].orEmpty()) { r -> slideChecks(e.id, r) }
        }
        OoxmlCorpus.record(OoxmlCorpus.Family.PPTX, verdicts.map { it.line })
        val failed = verdicts.filter { it.problems.isNotEmpty() }
        assertTrue(failed.isEmpty(), failed.joinToString("\n") { "${it.entry}: ${it.problems}" })
    }

    /**
     * 슬라이드 수 — python-pptx 가 센 것(숨긴 슬라이드 포함 — 우리도 넣는다)과 같아야 한다. 슬라이드 크기(가로:세로)는
     * 화면 맞춤 CSS 의 `min-aspect-ratio` 에 줄인 비로 들어간다 — python-pptx 의 `slide_width/height` 와 견준다.
     */
    private fun slideChecks(id: String, r: OoxmlCorpus.Run): List<String> {
        val out = ArrayList<String>()
        val counts = OoxmlCorpus.oracleCounts(id)
        counts["slides"]?.toInt()?.let { want -> if (want != r.parts.size) out.add("슬라이드 수 ${r.parts.size} ≠ 오라클 $want") }
        val cx = counts["slide_width_emu"]?.toLongOrNull()
        val cy = counts["slide_height_emu"]?.toLongOrNull()
        if (cx != null && cy != null && cx > 0 && cy > 0) {
            val g = gcd(cx, cy)
            val want = "min-aspect-ratio: ${cx / g}/${cy / g}"
            val html = r.parts.firstOrNull()?.html.orEmpty()
            if (want !in html) out.add("슬라이드 비가 '$want' 가 아니다")
        }
        // 마스터·레이아웃의 틀 글(슬라이드 번호 자리의 '‹#›', 개체 틀의 안내 글)은 슬라이드에 보이지 않는다.
        if ("‹#›" in r.text) out.add("슬라이드 번호 필드가 '‹#›' 로 보인다")
        if (Regex("Click to edit", RegexOption.IGNORE_CASE).containsMatchIn(r.text)) out.add("개체 틀의 안내 글이 보인다")
        when (id) {
            // Tika testPPTXGroups — 묶음 밖의 글상자는 **한 번만** 나온다. SmartArt 의 글('smart1')은 캐시된
            // 그림(`drawing1.xml`)에서 그린다.
            "PP07" -> {
                if (Regex("Ungrouped text box").findAll(r.text).count() != 1) out.add("'Ungrouped text box' 가 한 번이 아니다")
                if ("smart1" !in r.text) out.add("SmartArt 의 캐시된 그림을 그리지 않았다")
            }
            // SmartArt 에 캐시된 그림이 없다(반박 검토의 짐작대로) — LO 의 'a~e' 가 아니라 SmartArt 배지다.
            "PP08" -> if (r.unsupported[UnsupportedFeatures.SMART_ART] != 1) out.add("SmartArt 를 세지 않았다: ${r.unsupported}")
            // POI testGetMasterText — 마스터의 글과 바닥글이 슬라이드에 보인다.
            "PP03" -> if ("Footer from the master slide" !in r.text) out.add("마스터의 바닥글이 없다")
            // 효과 확장은 없었다(조사 노트의 짐작이 틀렸다) — 대신 TIFF 그림 하나를 그릴 수 없는 그림으로 센다.
            // 마스터의 글상자에 든 슬라이드 번호 필드가 첫 장에서 '1' 로 보인다(이 표본이 잡은 결함).
            "PP17" -> {
                if (r.unsupported[UnsupportedFeatures.UNSUPPORTED_IMAGE] != 1) out.add("TIFF 를 세지 않았다: ${r.unsupported}")
                if (r.parts[0].text.lines().none { it.trim() == "1" }) out.add("첫 장의 슬라이드 번호가 1 로 보이지 않는다")
            }
            // 매크로는 돌리지 않고 알린다.
            "PM02" -> if (!r.warned(FlowWarnings.MACROS)) out.add("매크로를 알리지 않았다")
        }
        return out
    }

    private tailrec fun gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)

    companion object {
        val RECALL: Map<String, Double> = emptyMap()
        val SKIP_MUST: Map<String, Set<String>> = emptyMap()
    }
}
