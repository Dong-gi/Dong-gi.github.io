package io.github.donggi.iroiroviewer.docview.pdf.text

import io.github.donggi.iroiroviewer.docview.pdf.crypt.Corpus
import io.github.donggi.iroiroviewer.docview.pdf.crypt.FileChannelSource
import io.github.donggi.iroiroviewer.docview.pdf.crypt.Json
import org.junit.Assume.assumeTrue
import java.io.File
import java.io.FileInputStream
import kotlin.test.Test
import kotlin.test.fail

/**
 * 실물 PDF 의 목차와 링크 주석을 **pypdf 6.17** 의 것과 견준다(오라클은 `samples-local/corpus/tools/oracle_pdf_nav.py` 가 만든
 * `oracle/PDF-XX.nav.json`). 말뭉치가 없으면 건너뛴다.
 *
 * 목차는 항목마다 제목·깊이·쪽이 같아야 한다(앞차례). 링크는 쪽마다 **가리키는 쪽의 모음**이 같아야 한다 — 우리 쪽은 `/A GoTo` 꼴도
 * 읽게 해서 견준다(앱은 `/Dest` 꼴만 쓰고 `/A` 는 플랫폼이 준다). 말뭉치에 `/Dest` 꼴 링크가 없어 이름 목적지(TeX 문서의 이름 나무)를
 * 푸는 길은 `/A` 의 목적지로 견준다 — 목적지를 푸는 함수는 하나다.
 */
class PdfNavigationCorpusTest {

    @Test
    fun `실물 PDF 의 목차와 링크를 오라클과 견준다`() {
        val dir = Corpus.dir
        assumeTrue("samples-local/corpus 가 없다", dir != null)
        val oracles = File(dir, "oracle").listFiles { f -> f.name.endsWith(".nav.json") }.orEmpty().sortedBy { it.name }
        assumeTrue("목차 오라클이 없다(oracle_pdf_nav.py)", oracles.isNotEmpty())
        val problems = ArrayList<String>()
        var outlines = 0
        var links = 0
        for (o in oracles) {
            val id = o.name.removeSuffix(".nav.json")
            @Suppress("UNCHECKED_CAST")
            val oracle = Json.parse(o.readText(Charsets.UTF_8)) as Map<String, Any?>
            FileInputStream(File(dir, "$id.pdf")).channel.use { ch ->
                val e = PdfTextEngine.open(FileChannelSource(ch))
                if (e == null) {
                    problems += "$id: 열지 못했다"
                    return@use
                }
                @Suppress("UNCHECKED_CAST")
                val want = oracle["outline"] as List<Map<String, Any?>>
                val got = e.outline().entries
                if (got.size != want.size) problems += "$id: 목차 항목 ${got.size} ≠ 오라클 ${want.size}"
                for ((i, w) in want.withIndex()) {
                    val g = got.getOrNull(i) ?: break
                    val wantPage = (w["page"] as Long?)?.toInt()
                    if (g.title != w["title"] || g.depth != (w["depth"] as Long).toInt() || g.page != wantPage) {
                        problems += "$id: 목차 #$i (${g.title}, ${g.depth}, ${g.page}) ≠ (${w["title"]}, ${w["depth"]}, $wantPage)"
                    }
                }
                outlines += got.size
                @Suppress("UNCHECKED_CAST")
                val wantLinks = oracle["links"] as Map<String, List<Map<String, Any?>>>
                for (page in 0 until e.pageCount) {
                    val w = wantLinks[page.toString()].orEmpty().map { (it["target"] as Long).toInt() }.sorted()
                    val g = e.annotationLinks(page, withActions = true).map { it.targetPage }.sorted()
                    if (w != g) problems += "$id: ${page}쪽 링크 $g ≠ 오라클 $w"
                    // 앱이 쓰는 꼴(`/Dest`)만 — 오라클의 `dest` 꼴과 같은 수.
                    val wDest = wantLinks[page.toString()].orEmpty().count { it["form"] == "dest" }
                    val gDest = e.annotationLinks(page).size
                    if (wDest != gDest) problems += "$id: ${page}쪽 /Dest 링크 $gDest ≠ 오라클 $wDest"
                    links += g.size
                }
            }
        }
        println("목차 항목 $outlines, 링크 $links 를 견줬다")
        if (problems.isNotEmpty()) fail(problems.take(40).joinToString("\n"))
    }
}
