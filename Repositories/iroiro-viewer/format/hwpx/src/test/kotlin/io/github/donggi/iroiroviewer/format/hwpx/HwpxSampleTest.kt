package io.github.donggi.iroiroviewer.format.hwpx

import io.github.donggi.iroiroviewer.format.FileDocumentSource
import io.github.donggi.iroiroviewer.format.FlowDocument
import io.github.donggi.iroiroviewer.format.FlowWarnings
import io.github.donggi.iroiroviewer.format.OpenFailure
import io.github.donggi.iroiroviewer.format.OpenOutcome
import io.github.donggi.iroiroviewer.format.OpenedDocument
import io.github.donggi.iroiroviewer.format.ProbeContext
import io.github.donggi.iroiroviewer.format.html.HancomChars
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 실세계 표본(`samples-local/hwp/`, 저장소에 넣지 않는다 — 없으면 건너뛴다). 목록과 출처는 그 폴더의 `SOURCES.md`.
 *
 * ## 오라클
 *
 * K 표본(정책브리핑 보도자료)은 정책브리핑 사이트의 **바로보기**(Synap 문서 변환기 — 한컴과 독립)가 같은 파일을
 * HTML 로 바꿔 보여 준다. 그 쪽들을 읽어 글만 뽑은 것이 `samples-local/hwp/oracle/<ID>.txt` 다(표본 곁의
 * `samples-local/hwp/tools/oracle_fetch_hwpx.py`·`oracle_extract_hwpx.py`). 우리가 뽑은 글과 **낱말 단위**로 견준다 — 재현율(오라클의 낱말 가운데
 * 우리에게도 있는 비율)과 정밀도(우리 낱말 가운데 오라클에도 있는 비율).
 *
 * 오라클 쪽에만 있는 것이 있다: 쪽마다의 머리말·꼬리말과 쪽 번호(우리는 흐름 렌더라 그리지 않는다), 차례의 탭
 * 채움 점(`···`, 우리는 탭 하나). 그래서 1.0 이 나오지 않는다. 문턱은 **재현율 0.90·정밀도 0.90** — 본문의
 * 한 부분(표 하나·구역 하나)을 통째로 잃으면 재현율이 그 아래로 떨어진다(K25 는 표가 본문의 대부분이다).
 */
class HwpxSampleTest {

    private val dir: File? = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "samples-local/hwp") }
        .firstOrNull { it.isDirectory }

    private fun sample(id: String, ext: String = "hwpx"): File {
        val d = dir
        assumeTrue("samples-local/hwp 가 없다", d != null)
        val f = File(d, "$id.$ext")
        assumeTrue("$id 가 없다", f.isFile)
        return f
    }

    private fun open(f: File, password: CharArray? = null): OpenOutcome = runBlocking {
        var doc: OpenedDocument? = null
        val o = HwpxOpener(password).open(FileDocumentSource(f)) { doc = it }
        if (o !is OpenOutcome.Success) doc?.close()
        o
    }

    /** 모든 부분의 글(블록 끝마다 줄바꿈). */
    private fun text(doc: FlowDocument): String {
        val sb = StringBuilder()
        for (i in doc.parts.indices) {
            val html = assertNotNull(doc.partHtml(i), "부분 $i")
            val body = html.substringAfter("<body>").substringBeforeLast("</body>")
            sb.append(plain(body.replace(Regex("</(p|h[1-6]|td|div)>|<br>"), "\n"))).append('\n')
        }
        return sb.toString()
    }

    private fun tokens(s: String): List<String> =
        s.split(SPLIT).filter { it.isNotEmpty() }

    private fun overlap(ours: List<String>, oracle: List<String>): Pair<Double, Double> {
        val a = ours.groupingBy { it }.eachCount()
        val b = oracle.groupingBy { it }.eachCount()
        var common = 0
        for ((t, n) in b) common += minOf(n, a[t] ?: 0)
        return common.toDouble() / oracle.size.coerceAtLeast(1) to common.toDouble() / ours.size.coerceAtLeast(1)
    }

    private val all = listOf("K25", "K26", "K27", "K28", "K32", "K33", "S08", "O11", "O14")

    @Test
    fun 한컴이_쓴_HWPX_가_모두_열리고_모든_부분이_그려진다() {
        var seen = 0
        for (id in all) {
            val f = File(dir ?: continue, "$id.hwpx")
            if (!f.isFile) continue
            seen++
            val o = open(f)
            assertTrue(o is OpenOutcome.Success, "$id: $o")
            (o.document as FlowDocument).use { doc ->
                assertTrue(doc.parts.isNotEmpty(), id)
                val t = text(doc)
                assertTrue(t.isNotBlank(), id)
                assertTrue(doc.warnings.none { it.code == FlowWarnings.PART_FAILED }, "$id ${doc.warnings}")
                // 한컴의 네모 숫자(U+F02B1…)가 남으면 폰에서 두부다(K27·K33) — HWP 5.0 과 같은 옮김 표를 지났는가.
                assertTrue(t.codePoints().noneMatch { HancomChars.map(it) != null }, "$id 에 옮기지 않은 한컴 글자")
                println("$id 부분 ${doc.parts.size} 글자 ${t.length} 목차 ${doc.outline.size} 버린 것 ${doc.unsupported.snapshot()} 경고 ${doc.warnings}")
            }
        }
        assumeTrue("표본이 없다", seen > 0)
    }

    @Test
    fun 오라클과_낱말이_맞는다() {
        val d = dir
        assumeTrue("samples-local/hwp 가 없다", d != null)
        var compared = 0
        val report = StringBuilder()
        for (id in listOf("K25", "K26", "K27", "K28", "K32", "K33")) {
            val f = File(d, "$id.hwpx")
            val oracleFile = File(d, "oracle/$id.txt")
            if (!f.isFile || !oracleFile.isFile) continue
            val o = open(f)
            assertTrue(o is OpenOutcome.Success, "$id: $o")
            val ours = (o.document as FlowDocument).use { tokens(text(it)) }
            val oracle = tokens(oracleFile.readText(Charsets.UTF_8))
            val (recall, precision) = overlap(ours, oracle)
            report.append(String.format(java.util.Locale.ROOT, "%s recall=%.4f precision=%.4f ours=%d oracle=%d%n", id, recall, precision, ours.size, oracle.size))
            assertTrue(recall >= 0.90, "$id 재현율 $recall")
            assertTrue(precision >= 0.90, "$id 정밀도 $precision")
            compared++
        }
        println(report)
        assumeTrue("오라클이 없다", compared > 0)
    }

    /**
     * 13단계 짝 대조 — 같은 보도자료의 HWP 판(K01·K19)과 견준 값. HWP 5.0 변환기로 잰 짝의 값은 K01 의 빈 문단 874개 가운데
     * 839개가 바탕 글자 크기를 들고(60% 393개·73% 132개 …), 부분이 6 이다. 처음에는 K25 의 빈 문단이 전부 문서 기본 크기(100%,
     * `font-size` 없음)였고 부분이 4 였다. K27 의 아래쪽 캡션은 두 오라클 모두 표 뒤에 둔다.
     */
    @Test
    fun 짝_표본의_빈_문단_크기_부분_수_캡션_자리가_HWP_판과_같다() {
        val o25 = open(sample("K25"))
        ((o25 as OpenOutcome.Success).document as FlowDocument).use { doc ->
            val html = doc.parts.indices.joinToString("") { doc.partHtml(it)!! }
            val empties = Regex("<(p|h[1-6])\\b([^>]*)><br/></\\1>").findAll(html).toList()
            val sized = empties.count { "font-size:" in it.groupValues[2] }
            assertEquals(874, empties.size)
            assertEquals(839, sized)
            assertEquals(6, doc.parts.size)
        }
        val o27 = open(sample("K27"))
        ((o27 as OpenOutcome.Success).document as FlowDocument).use { doc ->
            val html = doc.parts.indices.joinToString("") { doc.partHtml(it)!! }
            assertTrue(Regex("</table><div class=\"cap\">(?:(?!</div>).)*\\* 공공비영리단체 포함", RegexOption.DOT_MATCHES_ALL).containsMatchIn(html))
        }
    }

    @Test
    fun 암호_HWPX_O16_은_암호를_물고_맞는_암호로_열린다() {
        val f = sample("O16")
        val none = open(f)
        assertEquals(OpenFailure.PasswordRequired("암호가 걸린 HWPX", wrongPassword = false), (none as OpenOutcome.Failed).failure)
        val wrong = open(f, "654321".toCharArray())
        assertTrue(((wrong as OpenOutcome.Failed).failure as OpenFailure.PasswordRequired).wrongPassword)
        val ok = open(f, "123456".toCharArray())
        assertTrue(ok is OpenOutcome.Success, "$ok")
        (ok.document as FlowDocument).use { doc ->
            val t = text(doc)
            assertTrue(t.length > 1000, "글자 ${t.length}")
            // `opf:title` 은 한컴 글꼴의 사설 영역 글자로 시작한다(`U+F53A글 97 안내문`) — 두부 대신 파일 이름이 보이게 버린다.
            assertEquals("", doc.title)
            println("O16 부분 ${doc.parts.size} 글자 ${t.length} ${doc.unsupported.snapshot()}")
            // 풀린 그림은 BMP 의 서명(`BM`)으로 시작해야 한다.
            val head = ByteArray(2)
            assertEquals(2, doc.openResource("BinData/image1.bmp")?.use { it.read(head) })
            assertEquals("BM", String(head, Charsets.ISO_8859_1))

            // 풀린 미리보기 글(한글이 저장할 때 적는 앞부분, 따로 잠긴 항목)의 낱말이 본문에 있어야 한다 — 따로 푼 두
            // 항목이 서로를 확인한다. 미리보기는 표의 칸을 `<` `>` 로 감싸 적으므로 그 기호는 떼고 본다.
            val pkg = HwpxPackage.open(FileDocumentSource(f))
            val preview = pkg.use {
                assertEquals(UnlockResult.Unlocked, it.unlock("123456".toCharArray()) {})
                it.loadContents()
                HwpxFlowDocument.decodePreview(assertNotNull(it.readBytes("Preview/PrvText.txt", 1 shl 20)))
            }
            val words = tokens(preview.replace('<', ' ').replace('>', ' ')).filter { it.length >= 2 }
            assertTrue(words.size > 50, "미리보기 낱말 ${words.size}")
            val body = tokens(t).toHashSet()
            val found = words.count { it in body }
            assertTrue(found * 100 >= words.size * 95, "미리보기 낱말 ${words.size} 가운데 본문에 $found")
        }
    }

    @Test
    fun K28_의_큰_BMP_는_깨진_그림으로_두지_않고_센다() {
        // image11·12·13 은 67~70 MB 짜리 BMP 다. 바탕은 32 MiB 를 넘는 그림을 내주지 않으므로 `img` 를 쓰면 깨진 그림
        // 표시만 뜬다. 나머지 그림(스물하나)은 모두 내줄 수 있어야 한다.
        val o = open(sample("K28"))
        ((o as OpenOutcome.Success).document as FlowDocument).use { doc ->
            val html = doc.parts.indices.joinToString("") { doc.partHtml(it)!! }
            val srcs = Regex("<img src=\"([^\"]+)\"").findAll(html).map { it.groupValues[1] }.toList()
            assertEquals(21, srcs.size, srcs.toString())
            for (big in listOf("image11.bmp", "image12.bmp", "image13.bmp")) assertTrue(srcs.none { it.endsWith(big) }, big)
            for (src in srcs) assertNotNull(doc.openResource(src), src).close()
            assertEquals(3, doc.unsupported.snapshot()[io.github.donggi.iroiroviewer.format.UnsupportedFeatures.UNSUPPORTED_IMAGE])
        }
    }

    @Test
    fun HWPX_라는_이름의_HTML_과_HWP_는_맡지_않는다() {
        val html = sample("K35")
        val cfb = sample("K36")
        for (f in listOf(html, cfb)) {
            val head = f.inputStream().use { s -> ByteArray(64).also { s.read(it) } }
            assertNull(HwpxProbe.probe(ProbeContext(FileDocumentSource(f), "hwpx", head)), f.name)
            val o = open(f)
            assertTrue((o as OpenOutcome.Failed).failure is OpenFailure.Corrupt, "${f.name}: $o")
        }
    }

    @Test
    fun 한컴이_쓴_표본은_판별기가_맡는다() {
        for (id in all + "O16") {
            val f = File(dir ?: continue, "$id.hwpx")
            if (!f.isFile) continue
            val head = f.inputStream().use { s -> ByteArray(64).also { s.read(it) } }
            val names = lazy { ZipFile(f).use { z -> z.entries().toList().map { it.name } } }
            assertEquals(io.github.donggi.iroiroviewer.format.FormatId.HWPX, HwpxProbe.probe(ProbeContext(FileDocumentSource(f), "hwpx", head, names)), id)
        }
    }

    private companion object {
        val SPLIT = Regex("[\\s\\u00A0\\u3000\\u2007\\u202F]+")
    }
}
