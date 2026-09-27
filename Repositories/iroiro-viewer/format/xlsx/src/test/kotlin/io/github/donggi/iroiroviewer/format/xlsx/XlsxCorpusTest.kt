package io.github.donggi.iroiroviewer.format.xlsx

import io.github.donggi.iroiroviewer.format.FlowWarnings
import io.github.donggi.iroiroviewer.format.UnsupportedFeatures
import io.github.donggi.iroiroviewer.format.opc.OoxmlCorpus
import io.github.donggi.iroiroviewer.format.opc.OoxmlKind
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 실세계 xlsx·xlsm·xltx 와 그 암호판·xlsb·이전 형식(.xls)을 **앱과 같은 길로** 연다(`OoxmlCorpus`).
 * 표본은 `samples-local/corpus/`(저장소에 넣지 않는다)에 있고, 없으면 건너뛴다.
 *
 * 오라클은 openpyxl(`data_only`, Strict 는 lxml)이 본 **보이는 워크시트의 글자 칸**이다. 표시 형식이
 * 글자를 숨기는 칸(`;;;`)과 숨긴 열·행(`<col hidden>`·`<row hidden>`·높이 0)의 칸은 Excel 도 보이지 않으므로
 * 오라클에서 뺐다 — 빼기 전에는 숨긴 열의 글이 '빠뜨린 글' 로 잡혀 XM01·EN12 의 되부름이 낮았다. 숫자 칸은
 * 표시 형식을 입혀 보이므로 날것 값과 견주지 않고, 대신 POI 의 표시 형식 진리표(XL07·XL08)로 따로 본다.
 */
class XlsxCorpusTest {

    private val build: OoxmlCorpus.Build = { pkg, kind, limits, progress ->
        check(kind == OoxmlKind.XLSX) { "패키지가 $kind 라고 말한다" }
        XlsxDocument.open(pkg, limits, progress)
    }

    @Test
    fun 실세계_xlsx_표본을_앱처럼_연다() {
        assumeTrue("samples-local/corpus 가 없다", OoxmlCorpus.dir != null)
        val entries = OoxmlCorpus.entries(OoxmlCorpus.Family.XLSX)
        assumeTrue("xlsx 표본이 없다", entries.isNotEmpty())
        val verdicts = entries.map { e ->
            OoxmlCorpus.verify(e, build, minRecall = 0.95) { r -> sheetChecks(e.id, r) + extra(e.id, r) }
        }
        OoxmlCorpus.record(OoxmlCorpus.Family.XLSX, verdicts.map { it.line })
        val failed = verdicts.filter { it.problems.isNotEmpty() }
        assertTrue(failed.isEmpty(), failed.joinToString("\n") { "${it.entry}: ${it.problems}" })
    }

    /** 시트 수와 이름 — openpyxl 이 본 '보이는 워크시트'(매크로 시트·차트 시트 뺀 것)와 같아야 한다. */
    private fun sheetChecks(id: String, r: OoxmlCorpus.Run): List<String> {
        val out = ArrayList<String>()
        val counts = OoxmlCorpus.oracleCounts(id)
        counts["parts"]?.toInt()?.let { if (it != r.parts.size) out.add("시트 수 ${r.parts.size} ≠ 오라클 $it") }
        counts["labels"]?.let { want ->
            val ours = r.parts.joinToString("|") { it.label }
            if (want != ours) out.add("시트 이름 '$ours' ≠ 오라클 '$want'")
        }
        // 보이는 차트 시트는 그리지 않고 차트로 센다.
        counts["chartsheets_visible"]?.toInt()?.takeIf { it > 0 }?.let { n ->
            if ((r.unsupported[UnsupportedFeatures.CHART] ?: 0) < n) out.add("차트 시트 $n 개를 세지 않았다: ${r.unsupported}")
        }
        return out
    }

    /** 표본을 낸 프로젝트의 시험이 단언하는 것. */
    private fun extra(id: String, r: OoxmlCorpus.Run): List<String> {
        val out = ArrayList<String>()
        fun expect(ok: Boolean, what: String) {
            if (!ok) out.add(what)
        }
        val grids by lazy { r.parts.mapNotNull { it.html?.let { h -> Grid.parse(h) } } }
        when (id) {
            // POI bug50784 — A1 의 글꼴 색은 테마 9번(Excel 의 순서로 accent6)에 명암(tint -0.25)을 입힌 것.
            // 기대값은 파이썬 colorsys 로 명세(§18.8.19)대로 따로 계산했다.
            "XL14" -> {
                // 오라클 파일이 없으면(표본만 받아 둔 경우) 이 단언은 건너뛴다 — 다른 오라클과 같다.
                val want = OoxmlCorpus.oracleLines(id, "theme")?.firstOrNull { it.startsWith("theme9\t") }?.split('\t')?.get(1)
                val a1 = grids[0].cells["A1"]?.style.orEmpty()
                if (want != null) expect("color:#$want" in a1, "A1 이 테마 accent6(#$want)이 아니다: $a1")
            }
            // LO testContentImpl — A1=1, A2=2, B1 'String1', E2:F3 병합, H3 메모.
            "XL04" -> {
                val g = grids[0]
                expect(g.cells["A1"]?.text == "1" && g.cells["A2"]?.text == "2" && g.cells["B1"]?.text == "String1", "값이 다르다")
                // 수식 칸은 저장된 값(`<v>`)을 보인다 — C1 `2*3`=6, C4 `C1+C2`=11(파일에 값이 적혀 있다).
                expect(g.cells["C1"]?.text == "6" && g.cells["C4"]?.text == "11", "수식의 저장된 값이 다르다")
                expect(g.cells["E2"]?.colspan == 2 && g.cells["E2"]?.rowspan == 2, "E2:F3 병합이 아니다")
                expect(r.unsupported[UnsupportedFeatures.COMMENT] == 1, "메모를 하나로 세지 않았다: ${r.unsupported}")
            }
            // TestXSSFSheetMergeRegions — 병합 5만. 그리는 행이 2,000 에서 멈추므로(상한) 그 안의 병합 2,000 이 보인다.
            // 값이 있는 칸이 하나도 없는 시트라 잃는 글이 없다 — '줄였다' 는 값이 있는 행을 버릴 때만 알린다(설계).
            "XL10" -> {
                val merged = grids[0].cells.values.count { it.colspan > 1 || it.rowspan > 1 }
                expect(merged == 2000, "그린 병합이 2,000 이 아니다: $merged")
                expect(!r.warned(FlowWarnings.TRUNCATED), "잃은 글이 없는데 줄였다고 알렸다")
            }
            // Excel 4 매크로 시트는 그리지 않고 한 번 센다(TestXSSFBugs#getMacrosheet).
            "XM02" -> expect(r.unsupported[UnsupportedFeatures.MACRO] == 1, "매크로를 한 번 세지 않았다: ${r.unsupported}")
            // POI bug57181 — 시트 아홉(워크시트 일곱 중 하나는 숨김, 차트 시트 둘). General 시트는 264×252 칸이라
            // 199행에서 5만 칸 상한에 닿지만 값은 131행에서 끝난다(뒤는 테두리만 그은 빈 칸) — 잃은 값이 없으므로
            // '줄였다' 를 알리지 않는다(XL10 과 같은 기준). 예전에는 칸 상한이 값을 보지 않고 알렸다.
            "XM01" -> {
                expect(!r.warned(FlowWarnings.TRUNCATED), "잃은 값이 없는데 줄였다고 알렸다")
                expect(r.unsupported[UnsupportedFeatures.CHART] == 2, "차트 시트 둘을 세지 않았다: ${r.unsupported}")
            }
            // 먼저 나온 항목을 쓰고 알린다(반박 검토가 고친 기대). 두 `sheet1.xml` 은 내용이 다르다 — 앞의 것은 A1 이 공유
            // 문자열 'v1', 뒤의 것은 `<is>` 없이 `<v>v2</v>` 만 적은 인라인 문자열이다. openpyxl 은 뒤의 것을 읽고 거기서 글을
            // 못 찾아 오라클이 비었다(정밀도 0). 그래서 글 대조 대신 **어느 쪽을 썼는지**를 여기서 본다.
            "CR08" -> {
                expect(r.warned(FlowWarnings.DUPLICATE_PART), "같은 이름 항목을 알리지 않았다")
                expect("v1" in r.text && "v2" !in r.text, "먼저 나온 항목을 쓰지 않았다: ${r.text.trim()}")
            }
            // Tika testExcelStrict(돌지 않는 시험의 기대) — 수식의 계산 값이 '10.0' 처럼 보이지 않는다.
            "XL03" -> expect("10.0" !in r.text && "13.0" !in r.text, "수식 값이 실수 모양으로 보인다")
        }
        return out
    }

    /**
     * 암호 걸린 xlsb(EN20). 앱에서는 판별기가 `.xlsb` 를 맡지 않아 암호를 묻지 않고 '다루지 않는다' 로 끝난다(본 시험이
     * 그 길을 본다). 확장자가 바뀌어 **여는이에 곧바로** 들어와도 — 적힌 암호로 풀린 뒤 — '깨진 파일' 이 아니라
     * '다루지 않는 갈래' 여야 한다. 고치기 전에는 xlsx 변환기가 `workbook.bin` 을 XML 로 읽다 넘어졌다.
     */
    @Test
    fun 암호_xlsb_를_여는이에_곧바로_넣어도_다루지_않는다고_말한다() {
        assumeTrue("samples-local/corpus 가 없다", OoxmlCorpus.dir != null)
        val e = OoxmlCorpus.entries(OoxmlCorpus.Family.XLSX).firstOrNull { it.id == "EN20" }
        assumeTrue("EN20 이 없다", e != null)
        val password = e!!.password!!.toCharArray()
        var opened: io.github.donggi.iroiroviewer.format.OpenedDocument? = null
        val outcome = try {
            kotlinx.coroutines.runBlocking {
                io.github.donggi.iroiroviewer.format.Documents.open(
                    io.github.donggi.iroiroviewer.format.opc.OoxmlOpener(password, build = build),
                    io.github.donggi.iroiroviewer.format.FileDocumentSource(e.file),
                ) { opened = it }
            }
        } finally {
            password.fill('\u0000')
            opened?.close()
        }
        val failure = (outcome as? io.github.donggi.iroiroviewer.format.OpenOutcome.Failed)?.failure
        println("EN20 여는이 직접: $failure")
        assertTrue(failure is io.github.donggi.iroiroviewer.format.OpenFailure.Unsupported, "Unsupported 여야 하는데 $outcome")
    }

    /**
     * POI `TestCellFormatPart` 의 진리표 — 행마다 '기대 글자(A)·형식(B)·값(C)'. A 는 Excel 이 `TEXT(C, B)` 로
     * 계산해 둔 값이다. **우리 표시 형식 클래스**를 그 표에 직접 견준다(파일을 열어 칸을 그리는 길은 C 를
     * General 로 보이므로 여기서는 쓰지 않는다). 어긋나는 행은 [numfmtKnown] 이 가른 갈래에 들어야 한다 —
     * 그 밖의 어긋남은 새 결함이다.
     */
    @Test
    fun POI_표시_형식_진리표() {
        assumeTrue("samples-local/corpus 가 없다", OoxmlCorpus.dir != null)
        val report = ArrayList<String>()
        val bad = ArrayList<String>()
        val known = LinkedHashMap<String, Int>()
        for (id in listOf("XL07", "XL08")) {
            val rows = OoxmlCorpus.oracleLines(id, "numfmt") ?: continue
            // 값은 통합 문서의 날짜 체계를 따른다 — 두 표 다 맥 Excel 이 쓴 1904 체계다.
            val date1904 = OoxmlCorpus.oracleCounts(id)["epoch1904"] == "1"
            var pass = 0
            for (line in rows.filter { it.isNotBlank() }) {
                val (want, code, raw) = line.split('\t')
                val ref = line.substringAfterLast('\t')
                val got = NumberFormat.parse(code).format(raw.toDouble(), date1904).text
                if (got == want) {
                    pass++
                    continue
                }
                val why = numfmtKnown(code, raw.toDouble())
                if (why != null) known[why] = (known[why] ?: 0) + 1
                else bad.add("$id!$ref [$code] $raw → '$got' (기대 '$want')")
            }
            report.add("$id: ${rows.count { it.isNotBlank() }} 행 중 $pass 행 일치")
        }
        report.forEach { println(it) }
        known.forEach { (why, n) -> println("  알려진 한계 $n 행: $why") }
        bad.forEach { println("  어긋남 $it") }
        assertTrue(bad.isEmpty(), bad.joinToString("\n"))
    }

    /**
     * 진리표의 어긋남 가운데 **알고 두는 것**(CLAUDE.md '지금 지원하지 않는 것'에 적는다). 둘 다 POI 가 Excel 의
     * 괴상한 분수 규칙을 시험하려고 만든 모양이고, 실세계 통합 문서에서는 보기 드물다.
     */
    private fun numfmtKnown(code: String, value: Double): String? {
        val slash = code.indexOf('/')
        if (slash < 0) return null
        val around = code.substring(maxOf(0, slash - 2), minOf(code.length, slash + 2))
        return when {
            // `#\:#=/=#`·`#_#/#` — 분자·분모와 빗금 사이에 글자(`=`)나 폭 자리(`_#`)가 끼면 우리는 그것을 분수로
            // 알아보지 못한다(분자가 빗금 바로 앞의 자리표여야 한다). Excel 은 글자를 건너뛰어 분수를 세운다.
            '=' in around || Regex("_.[/]|_.$").containsMatchIn(code.substring(0, minOf(code.length, slash + 1))) ->
                "분자·분모와 빗금 사이에 글자·폭 자리가 낀 분수"
            // `0#/000` 에 정수 값 — 분수 부분이 0 이어도 `0` 자리표가 '00/001' 을 강제한다. 우리는 정수만 그린다.
            value == Math.rint(value) && Regex("0[#?0]*/[#?0]*0").containsMatchIn(code) -> "분수 부분이 0 인데 분자·분모가 `0` 자리표"
            else -> null
        }
    }
}
