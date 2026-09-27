package io.github.donggi.iroiroviewer.format.hwp5

import io.github.donggi.iroiroviewer.format.FileDocumentSource
import io.github.donggi.iroiroviewer.format.FlowDocument
import io.github.donggi.iroiroviewer.format.FlowWarnings
import io.github.donggi.iroiroviewer.format.OpenFailure
import io.github.donggi.iroiroviewer.format.OpenOutcome
import io.github.donggi.iroiroviewer.format.UnsupportedFeatures
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * 실세계 표본(`samples-local/hwp/`, 저장소에 넣지 않는다 — 출처·라이선스는 그 폴더의 `SOURCES.md`). 없으면 건너뛴다.
 *
 * ## 오라클
 *
 * * **배포용 문서** 셋(S01·S02 한컴 명세, O01 hwplib 표본)은 복호화의 오라클이다 — 알고리즘이 틀리면 레코드 흐름이 서지 않고
 *   본문에 명세의 문장이 나오지 않는다.
 * * **정책브리핑의 '바로보기'**(Synap 변환기가 만든 HTML, `oracle/Kxx.txt` — 표본 곁의 `samples-local/hwp/tools/oracle_fetch_hwp5.py` 가 읽기만
 *   해서 모은 것)는 본문 글의 독립 오라클이다. 글자 3-gram 의 **재현율**(바로보기의 글이 우리 글에 얼마나 들었나)과 정밀도를
 *   잰다. 공백은 셈에서 뺀다 — 바로보기가 글자 덩이마다 공백을 넣는다.
 */
class Hwp5SampleTest {

    private val dir: File? = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "samples-local/hwp") }
        .firstOrNull { it.isDirectory }

    private fun sample(id: String): File? = dir?.let { d ->
        listOf("hwp", "hwpx").map { File(d, "$id.$it") }.firstOrNull { it.isFile }
    }

    private fun open(f: File): OpenOutcome = runBlocking { Hwp5Opener(null).open(FileDocumentSource(f)) }

    private fun allText(doc: FlowDocument): String = buildString {
        for (i in doc.parts.indices) append(plain(assertNotNull(doc.partHtml(i), "#$i").substringAfter("<body>"))).append('\n')
    }

    @Test
    fun 모든_HWP_표본이_열리고_모든_부분이_그려진다() {
        assumeTrue("samples-local/hwp 가 없다", dir != null)
        val report = StringBuilder()
        var seen = 0
        for (id in SMOKE) {
            val f = sample(id) ?: continue
            seen++
            val t0 = System.nanoTime()
            val o = open(f)
            if (id == "O08") {
                // 암호 문서 — 묻지 않고 '열 수 없는 방식' 으로 끝난다.
                assertTrue(o is OpenOutcome.Failed && o.failure is OpenFailure.Encrypted, "$id $o")
                report.append("$id: Encrypted\n")
                continue
            }
            if (o !is OpenOutcome.Success) fail("$id 가 열리지 않았다: $o")
            val doc = o.document as FlowDocument
            doc.use {
                val text = allText(doc)
                val ms = (System.nanoTime() - t0) / 1_000_000
                val chars = text.count { !it.isWhitespace() }
                // 본문이 가리키는 그림이 전부 꾸러미에서 나온다 — BinItem 차례 → 저장소 번호 → 스트림 이름, 압축 칸.
                var images = 0
                for (i in doc.parts.indices) {
                    for (m in IMG.findAll(doc.partHtml(i)!!)) {
                        val bytes = doc.openResource(m.groupValues[1])?.use { it.readBytes() }
                        assertTrue(bytes != null && bytes.size > 16, "$id ${m.groupValues[1]}")
                        assertTrue(looksLikeImage(bytes), "$id ${m.groupValues[1]} 의 앞머리가 그림이 아니다")
                        images++
                    }
                }
                report.append("$id: parts=${doc.parts.size} chars=$chars outline=${doc.outline.size} images=$images ms=$ms warnings=${doc.warnings} dropped=${doc.unsupported.snapshot()}\n")
                // hwplib 의 기능 표본(O02 각주·O03 수식)은 글이 몇 자뿐이다.
                val min = if (id.startsWith("O0") && id != "O01") 5 else 200
                assertTrue(chars >= min, "$id 글이 너무 적다: $chars")
                assertTrue(doc.warnings.none { it.code == FlowWarnings.PART_FAILED }, "$id ${doc.warnings}")
            }
        }
        println(report)
        assumeTrue("표본이 없다", seen > 0)
    }

    @Test
    fun 배포용_문서가_풀린다() {
        assumeTrue("samples-local/hwp 가 없다", dir != null)
        val expected = mapOf(
            "S01" to listOf("문서 파일 형식", "HWPTAG_PARA_HEADER"),
            "S02" to listOf("수식"),
            "O01" to listOf(""),
        )
        var seen = 0
        for ((id, phrases) in expected) {
            val f = sample(id) ?: continue
            seen++
            val o = open(f)
            assertTrue(o is OpenOutcome.Success, "$id $o")
            val doc = o.document as Hwp5FlowDocument
            doc.use {
                val text = allText(doc)
                for (p in phrases) assertTrue(p in text, "$id 에 '$p' 가 없다")
                // 배포용 문서의 BodyText 는 안내 문단뿐이다 — 본문은 훨씬 길다.
                assertTrue(text.length > 500, "$id ${text.length}")
                println("$id distribution flags=0x${Integer.toHexString(doc.distributionFlags ?: -1)} chars=${text.length}")
            }
        }
        assumeTrue("배포용 표본이 없다", seen > 0)
    }

    @Test
    fun 바로보기_오라클과_본문_글이_맞는다() {
        assumeTrue("samples-local/hwp 가 없다", dir != null)
        val oracleDir = File(dir, "oracle")
        assumeTrue("오라클이 없다", oracleDir.isDirectory)
        val report = StringBuilder()
        var seen = 0
        for (id in ORACLE) {
            val f = sample(id) ?: continue
            val oracleFile = File(oracleDir, "$id.txt")
            if (!oracleFile.isFile) continue
            seen++
            val o = open(f)
            assertTrue(o is OpenOutcome.Success, "$id $o")
            val doc = o.document as FlowDocument
            doc.use {
                val ours = grams(allText(doc))
                val theirs = grams(oracleFile.readText())
                val common = intersect(ours, theirs)
                val recall = common.toDouble() / theirs.values.sum().coerceAtLeast(1)
                val precision = common.toDouble() / ours.values.sum().coerceAtLeast(1)
                report.append("$id: recall=%.3f precision=%.3f\n".format(recall, precision))
                assertTrue(recall >= RECALL_MIN, "$id 재현율 $recall")
                assertTrue(precision >= PRECISION_MIN, "$id 정밀도 $precision")
            }
        }
        println(report)
        assumeTrue("오라클이 없다", seen > 0)
    }

    @Test
    fun 가장_큰_표본의_시간과_메모리() {
        val f = sample("K05")
        assumeTrue("K05 가 없다", f != null)
        val rt = Runtime.getRuntime()
        System.gc()
        val before = rt.totalMemory() - rt.freeMemory()
        val t0 = System.nanoTime()
        val o = open(f!!)
        val t1 = System.nanoTime()
        assertTrue(o is OpenOutcome.Success, "$o")
        val doc = o.document as FlowDocument
        doc.use {
            var html = 0L
            for (i in doc.parts.indices) html += doc.partHtml(i)!!.length
            val t2 = System.nanoTime()
            val after = rt.totalMemory() - rt.freeMemory()
            // 화면이 청하듯 그림을 하나씩 꺼낸다(하나를 다 읽고 버린다).
            val images = Regex("src=\"(BinData/[^\"]+)\"").findAll(doc.partHtml(0)!!).map { it.groupValues[1] }.toList()
            var maxImage = 0
            var total = 0L
            for (name in images) doc.openResource(name)?.use { val n = it.readBytes().size; total += n; maxImage = maxOf(maxImage, n) }
            val t3 = System.nanoTime()
            println(
                "K05: size=${f.length()} open=${(t1 - t0) / 1_000_000}ms render=${(t2 - t1) / 1_000_000}ms parts=${doc.parts.size} " +
                    "html=$html heapDelta=${(after - before) / 1024 / 1024}MiB images=${images.size} imageBytes=$total " +
                    "maxImage=$maxImage imagesMs=${(t3 - t2) / 1_000_000} dropped=${doc.unsupported.snapshot()}"
            )
            assertTrue(images.isNotEmpty())
            assertTrue(doc.unsupported.snapshot().keys.none { it == UnsupportedFeatures.LINKED_FILE })
        }
    }

    @Test
    fun 확장자와_속이_다른_표본() {
        // K35 는 HTML 쪽이 .hwpx 이름으로 올라간 것 — 판별기가 맡지 않고, 여는이도 깨진 파일로 끝낸다.
        val html = dir?.let { File(it, "K35.hwpx") }?.takeIf { it.isFile }
        assumeTrue("K35 가 없다", html != null)
        val o = open(html!!)
        assertTrue(o is OpenOutcome.Failed && o.failure is OpenFailure.Corrupt, "$o")
        // K36 은 속이 HWP 5.0 인 .hwpx — 연다(스모크가 연다).
        val k36 = sample("K36")
        if (k36 != null) assertEquals(true, open(k36) is OpenOutcome.Success)
    }

    /** PNG·JPEG·GIF·BMP·WebP 의 앞머리. */
    private fun looksLikeImage(b: ByteArray): Boolean {
        fun at(i: Int) = b[i].toInt() and 0xFF
        return (at(0) == 0x89 && at(1) == 'P'.code) || (at(0) == 0xFF && at(1) == 0xD8) ||
            (at(0) == 'G'.code && at(1) == 'I'.code) || (at(0) == 'B'.code && at(1) == 'M'.code) ||
            (at(0) == 'R'.code && at(8) == 'W'.code)
    }

    private fun grams(text: String): HashMap<String, Int> {
        val s = text.filterNot { it.isWhitespace() || it == '\u00A0' || it == '\u200B' }
        val m = HashMap<String, Int>()
        for (i in 0..s.length - 3) {
            val g = s.substring(i, i + 3)
            m[g] = (m[g] ?: 0) + 1
        }
        return m
    }

    private fun intersect(a: Map<String, Int>, b: Map<String, Int>): Int {
        var n = 0
        for ((k, v) in b) n += minOf(v, a[k] ?: 0)
        return n
    }

    companion object {
        private val IMG = Regex("<img src=\"([^\"]+)\"")

        private val SMOKE = listOf(
            "K01", "K02", "K04", "K05", "K06", "K09", "K10", "K11", "K15", "K17", "K19", "K36",
            "S01", "S02", "O01", "O02", "O03", "O08",
        )
        private val ORACLE = listOf("K01", "K02", "K04", "K05", "K06", "K09", "K10", "K11", "K15", "K17", "K19", "K36")

        /**
         * 바로보기의 글 3-gram 가운데 우리 글에 든 비율의 하한. 13단계 검토 뒤 표본별로 0.999–1.000 이다. 처음의 0.90 은
         * 절 제목의 번호(한컴 사설 영역 글자)를 잃은 표본 넷(K11 은 0.991)을 통과시켰다 — 그것이 잡히는 높이로 둔다.
         */
        private const val RECALL_MIN = 0.995

        /** 우리 글 3-gram 가운데 바로보기에 든 비율의 하한 — 글이 두 번 나오거나 쓰레기가 글이 되면 떨어진다. 지금 0.996–1.000(K01 은 수식 스크립트). */
        private const val PRECISION_MIN = 0.99
    }
}
