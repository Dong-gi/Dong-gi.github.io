package io.github.donggi.iroiroviewer.format.xlsx

import io.github.donggi.iroiroviewer.format.FlowWarnings
import io.github.donggi.iroiroviewer.format.OpenFailure
import io.github.donggi.iroiroviewer.format.UnsupportedFeatures
import io.github.donggi.iroiroviewer.format.opc.OoxmlCorpus
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **독립 오라클** — openpyxl 3.1.5 가 쓴 통합 문서(`xlsx/oracle.xlsx`, 7 KB)를 열어, 각 칸에 보이는
 * 글자가 생성기가 **파이썬의 서식 함수로 따로 계산한 값**(`oracle.golden.txt`)과 같은지 본다.
 * 날짜는 `strftime`, 천 단위는 `format(v, ",.2f")`, 지수는 `"%.2E"` — 우리 코드와 아무것도 나누지
 * 않는다. 수식의 계산 결과는 openpyxl 이 쓰지 않으므로 생성기가 XML 에 직접 넣었다(하나는 일부러 뺐다).
 *
 * 생성기(`make_oracle.py`)는 저장소 밖(스크래치패드)에 있다 — 표본은 20 KB 이하 규칙을 지킨다.
 */
class XlsxOracleTest {

    private fun bytes(name: String): ByteArray =
        requireNotNull(javaClass.getResourceAsStream("/xlsx/$name")) { "표본 $name 이 없다" }.use { it.readBytes() }

    private val golden: List<List<String>> by lazy {
        String(bytes("oracle.golden.txt"), Charsets.UTF_8).lines().filter { it.isNotEmpty() && !it.startsWith("# ") }.map { it.split('\t') }
    }

    @Test
    fun openpyxl_이_쓴_값이_그대로_보인다() {
        Books.open(bytes("oracle.xlsx")).use { doc ->
            // 숨긴 시트('숨김')는 부분이 아니다.
            assertEquals(listOf("데이터", "Second"), doc.parts.map { it.label })
            val grids = doc.parts.indices.associate { doc.parts[it].label to Grid.parse(doc.partHtml(it)!!) }
            var checked = 0
            for (line in golden) {
                if (line[0].startsWith("#")) continue
                val (sheet, ref, text) = Triple(line[0], line[1], line.getOrElse(2) { "" })
                val cell = grids.getValue(sheet).cells[ref] ?: error("$sheet!$ref 칸이 없다")
                assertEquals(text, cell.text, "$sheet!$ref")
                checked++
            }
            assertEquals(29, checked)
            assertEquals(1, doc.unsupported.snapshot()[UnsupportedFeatures.FORMULA_CACHED])
        }
    }

    @Test
    fun openpyxl_이_쓴_모양이_그대로_보인다() {
        val accent1 = golden.first { it[0] == "#accent1" }[1]
        Books.open(bytes("oracle.xlsx")).use { doc ->
            val html = doc.partHtml(0)!!
            val g = Grid.parse(html)
            val a6 = g.cells["A6"]!!.style
            assertTrue("color:#ff0000" in a6, a6)
            assertTrue("font-style:italic" in a6, a6)
            assertTrue("background-color:#ffff00" in a6, a6)
            assertTrue("border-bottom:1px solid #0000ff" in a6, a6)
            // 테마 색 — 기대값은 생성기가 openpyxl 의 theme1.xml 에서 읽은 강조1 이다.
            assertTrue("color:#$accent1" in g.cells["B6"]!!.style, g.cells["B6"]!!.style)
            assertTrue("w" in g.cells["C6"]!!.classes)
            assertTrue("text-align:center" in g.cells["C6"]!!.style)
            assertTrue("font-weight:bold" in g.cells["A1"]!!.style)
            assertEquals(3, g.cells["A8"]!!.colspan)
            assertEquals(2, g.cells["A8"]!!.rowspan)
            assertTrue("n" in g.cells["B2"]!!.classes)
            assertTrue("c" in g.cells["E3"]!!.classes)
            assertTrue("width:123.2pt" in html)
            assertEquals("height:30pt", g.rowAttrs[1]["style"])
            assertFalse("<script" in html)

            val second = doc.partHtml(1)!!
            assertTrue("href=\"~part-0.html\"" in second, second)
            assertFalse("example.com" in second)
        }
    }

    /**
     * 큰 표본 연기 시험 — `samples-local/corpus/`(`manifest.json`, 저장소에 넣지 않는다)의 xlsx 무리 가운데 **256 KiB 를 넘는
     * 것**(BG01 12 MB·XL10·XL23·XM01)을 앱과 같은 길(`OoxmlCorpus.run`)로 열어 모든 시트를 그린다. 상한이 걸리고 시간이 넉넉한지만
     * 본다 — 글은 `XlsxCorpusTest` 가 오라클과 견준다. 말뭉치가 없으면 건너뛴다.
     *
     * 예전에는 생성기가 만든 수만 행짜리 표본을 `samples-local/xlsx/` 에서 찾았는데 그 폴더가 없어 늘 건너뛰었다. 말뭉치의
     * `big` 표본(BG01)이 그 자리를 잇는다 — 그것은 **줄였다(`TRUNCATED`)** 로 열리거나 '너무 크다' 여야 한다.
     */
    @Test
    fun 큰_표본_연기_시험() {
        assumeTrue("samples-local/corpus 가 없다", OoxmlCorpus.dir != null)
        val entries = OoxmlCorpus.entries(OoxmlCorpus.Family.XLSX)
            .filter { (it.expect == OoxmlCorpus.Expect.OPENS || it.expect == OoxmlCorpus.Expect.BIG) && it.file.length() > BIG_BYTES }
            .sortedBy { it.id }
        assumeTrue("큰 xlsx 표본이 없다", entries.isNotEmpty())
        val build: OoxmlCorpus.Build = { pkg, _, limits, progress -> XlsxDocument.open(pkg, limits, progress) }
        for (e in entries) {
            val r = OoxmlCorpus.run(e.file, null, build)
            println("${e.id}: ${e.file.length()} B, ${r.summary()}, ${r.ms}ms")
            assertFalse(r.timedOut, "${e.id} 이 멎었다")
            assertTrue(r.escaped == null, "${e.id}: 예외가 샜다 ${r.escaped}")
            assertTrue(r.ms < 60_000, "${e.id} 이 ${r.ms}ms 걸렸다")
            for (part in r.parts) assertTrue((part.html?.length ?: 0) < 40 * 1024 * 1024, "${e.id} ${part.label}")
            if (e.expect == OoxmlCorpus.Expect.BIG) {
                assertTrue(r.warned(FlowWarnings.TRUNCATED) || r.failure is OpenFailure.TooLarge, "${e.id}: ${r.summary()}")
            } else {
                assertTrue(r.succeeded && r.parts.isNotEmpty(), "${e.id}: ${r.summary()}")
            }
        }
    }

    private companion object {
        /** 연기 시험에 넣는 표본의 크기 하한. */
        const val BIG_BYTES = 256L * 1024
    }
}
