package io.github.donggi.iroiroviewer.docview.pdf.text

import io.github.donggi.iroiroviewer.docview.pdf.crypt.Corpus
import io.github.donggi.iroiroviewer.docview.pdf.crypt.FileChannelSource
import io.github.donggi.iroiroviewer.docview.pdf.crypt.Json
import org.junit.Assume.assumeTrue
import java.io.File
import java.io.FileInputStream
import java.text.Normalizer
import kotlin.test.Test
import kotlin.test.fail

/**
 * 실물 PDF 에서 꺼낸 글을 **pypdf 6.17** 의 글과 견준다(오라클은 `samples-local/corpus/tools/oracle_pdf_text.py` 가 만든
 * `oracle/PDF-XX.text.json`). 말뭉치가 없으면 건너뛴다.
 *
 * 견주는 단위는 공백을 뺀 **글자 세 개짜리 조각**이다 — 두 쪽은 띄어쓰기와 줄바꿈을 서로 다르게 짐작하므로(PDF 에는 그것이
 * 없다) 글자의 차례만 본다. 재현율은 '오라클이 본 글을 우리도 보았는가', 정밀도는 '우리가 지어낸 글이 없는가' 다.
 */
class PdfTextCorpusTest {

    /**
     * 하한. 2026-10-04 에 잰 값은 18 문서 모두 재현율·정밀도 1.0000 이었고 PDF-05(스캔 + 보이지 않는 OCR 글)만 조각 하나가
     * 달랐다(0.9999). 둘이 같은 차례(내용 스트림)를 따르므로 글자가 같다 — 다른 것은 띄어쓰기의 짐작뿐이고 그것은 여기서 재지
     * 않는다. TeX 문서(PDF-26)는 두 쪽 다 CFF 글꼴의 내장 인코딩을 읽지 않는다(pypdf 는 fontTools 가 없다고 경고한다) — 같이
     * 틀릴 수 있는 자리다.
     */
    private val floor = 0.995

    @Test
    fun `실물 PDF 의 글을 오라클과 견준다`() {
        val dir = Corpus.dir
        assumeTrue("samples-local/corpus 가 없다", dir != null)
        val oracles = File(dir, "oracle").listFiles { f -> f.name.endsWith(".text.json") }.orEmpty().sortedBy { it.name }
        assumeTrue("글 오라클이 없다(oracle_pdf_text.py)", oracles.isNotEmpty())
        val problems = ArrayList<String>()
        for (o in oracles) {
            val id = o.name.removeSuffix(".text.json")
            @Suppress("UNCHECKED_CAST")
            val oracle = Json.parse(o.readText(Charsets.UTF_8)) as Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val pages = oracle["pages"] as Map<String, Any?>
            val want = (oracle["pageCount"] as Long).toInt()
            val started = System.nanoTime()
            val engine = FileInputStream(File(dir, "$id.pdf")).channel.use { ch ->
                val e = PdfTextEngine.open(FileChannelSource(ch))
                if (e == null) {
                    problems += "$id: 열지 못했다"
                    null
                } else {
                    if (e.pageCount != want) problems += "$id: 쪽 수 ${e.pageCount} ≠ 오라클 $want"
                    var hit = 0L
                    var total = 0L
                    var ours = 0L
                    var oursHit = 0L
                    for ((index, text) in pages) {
                        val expected = grams(text as? String ?: continue)
                        val page = e.page(index.toInt())
                        val got = grams(PdfTextLayout.text(page, 0, page.glyphs.size, Int.MAX_VALUE).orEmpty())
                        val (h1, t1) = overlap(expected, got)
                        val (h2, t2) = overlap(got, expected)
                        hit += h1
                        total += t1
                        oursHit += h2
                        ours += t2
                    }
                    val recall = if (total == 0L) 1.0 else hit.toDouble() / total
                    val precision = if (ours == 0L) 1.0 else oursHit.toDouble() / ours
                    val ms = (System.nanoTime() - started) / 1_000_000
                    println("$id 재현율 %.4f 정밀도 %.4f (오라클 조각 $total, 우리 $ours) ${ms}ms".format(recall, precision))
                    if (recall < floor) problems += "$id: 재현율 %.4f < $floor".format(recall)
                    if (precision < floor) problems += "$id: 정밀도 %.4f < $floor".format(precision)
                    e
                }
            }
        }
        if (problems.isNotEmpty()) fail(problems.joinToString("\n"))
    }

    /** 공백·제어 문자를 빼고 NFKC 로 맞춘 글자 세 개짜리 조각의 개수. */
    private fun grams(text: String): Map<String, Int> {
        val norm = Normalizer.normalize(text, Normalizer.Form.NFKC).filter { !it.isWhitespace() && it >= ' ' }
        val out = HashMap<String, Int>()
        for (i in 0..norm.length - 3) out.merge(norm.substring(i, i + 3), 1, Int::plus)
        return out
    }

    /** [a] 의 조각이 [b] 에 몇 개 있는가 — (맞은 수, [a] 의 전체 수). */
    private fun overlap(a: Map<String, Int>, b: Map<String, Int>): Pair<Long, Long> {
        var hit = 0L
        var total = 0L
        for ((g, n) in a) {
            total += n
            hit += minOf(n, b[g] ?: 0)
        }
        return hit to total
    }
}
