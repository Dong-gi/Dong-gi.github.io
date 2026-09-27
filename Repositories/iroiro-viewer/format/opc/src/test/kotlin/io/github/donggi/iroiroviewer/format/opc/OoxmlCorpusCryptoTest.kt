package io.github.donggi.iroiroviewer.format.opc

import io.github.donggi.iroiroviewer.format.FileDocumentSource
import io.github.donggi.iroiroviewer.format.FormatId
import io.github.donggi.iroiroviewer.safety.ParseLimits
import org.junit.Assume.assumeTrue
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 실세계 표본(`samples-local/corpus/`, 저장소에 넣지 않는다)으로 보는 **판별과 복호화**. 변환기가 필요 없는
 * 것만 여기 있다 — 여는 길 전체는 docx·xlsx·pptx 모듈의 `*CorpusTest` 가 본다.
 */
class OoxmlCorpusCryptoTest {

    /**
     * `FormatRegistry` 의 판별 차례(HWPX → EPUB → OOXML …)를 밟아 **확장자가 말하는 종류로** 맡는가.
     * 암호 걸린 파일은 CFB 라 확장자로만 가를 수 있고, 겉이 깨진 ZIP 도 확장자로 맡아야 여는이가
     * '깨진 파일' 이라고 말할 수 있다. `.xlsb` 는 맡지 않는다(다루지 않는 이진 통합 문서).
     */
    @Test
    fun 판별기가_표본을_앱처럼_맡는다() {
        assumeTrue("samples-local/corpus 가 없다", OoxmlCorpus.dir != null)
        val entries = OoxmlCorpus.entries()
        assumeTrue(entries.isNotEmpty())
        val bad = ArrayList<String>()
        for (e in entries) {
            val want = when (e.file.name.substringAfterLast('.').lowercase()) {
                "docx", "docm", "dotx" -> FormatId.DOCX
                "xlsx", "xlsm", "xltx" -> FormatId.XLSX
                "pptx", "pptm", "ppsx" -> FormatId.PPTX
                "doc", "xls", "ppt" -> FormatId.LEGACY_OFFICE
                else -> null
            }
            val got = OoxmlCorpus.probeAsApp(FileDocumentSource(e.file))
            println("${e.id}\t${e.file.name}\t판별 $got")
            if (got != want) bad.add("${e.id}: $got ≠ $want")
        }
        assertTrue(bad.isEmpty(), bad.joinToString("\n"))
    }

    /**
     * 암호를 푼 **평문 패키지 바이트**가 독립 오라클과 같은가. 오라클은 msoffcrypto-tool 이 무결성까지
     * 확인하며 푼 것이고, 그 도구가 못 푸는 것(SHA-1 Agile 의 HMAC 확인·열쇠 길이를 늘려야 하는 AES-256/SHA-1·
     * 선언보다 짧은 섹터 사슬)은 **명세대로 따로 쓴 파이썬 복호화기**가 푼 것이다(`samples-local/corpus/tools/` 의
     * `make_oracles.py` — msoffcrypto 가 확인할 수 있는 표본에서 그 도구와 한 바이트도 다르지 않다).
     * 표본을 낸 프로젝트가 적어 둔 값(EN12 의 SHA-256, EN07 의 크기, EN15 의 ZIP 항목 크기)도 함께 본다.
     */
    @Test
    fun 풀린_패키지가_독립_오라클과_한_바이트도_다르지_않다() {
        assumeTrue("samples-local/corpus 가 없다", OoxmlCorpus.dir != null)
        val encrypted = OoxmlCorpus.entries().filter { OoxmlCorpus.oracle(it.id, "sha256") != null }
        assumeTrue(encrypted.isNotEmpty())
        val bad = ArrayList<String>()
        for (e in encrypted) {
            val (wantSha, wantSize, how) = OoxmlCorpus.oracleLines(e.id, "sha256")!!.first().split('\t')
            val password = e.password?.toCharArray()
            // 기본 암호(`VelvetSweatshop`) 표본은 암호 없이 — 앱이 묻지 않고 여는 길 그대로.
            val given = if (e.password == "VelvetSweatshop") null else password
            val result = OfficeCfb.open(FileDocumentSource(e.file), given, ParseLimits.DEFAULT) {}
            password?.fill('\u0000')
            if (result !is OfficeCfb.Result.Decrypted) {
                bad.add("${e.id}: 풀지 못했다 ${(result as OfficeCfb.Result.Failed).failure}")
                continue
            }
            val bytes = result.bytes
            val sha = MessageDigest.getInstance("SHA-256").digest(bytes)
            val hex = sha.joinToString("") { "%02x".format(it) }
            println("${e.id}\t${bytes.size} B\tsha256 ${hex.take(16)}…\t오라클 ${if (hex == wantSha) "같다" else "다르다"}\t($how)")
            if (hex != wantSha || bytes.size != wantSize.toInt()) bad.add("${e.id}: ${bytes.size} B $hex ≠ 오라클 $wantSize B $wantSha")
            val counts = OoxmlCorpus.oracleCounts(e.id)
            counts["published_sha256_b64"]?.let { if (Base64.getEncoder().encodeToString(sha) != it) bad.add("${e.id}: POI 의 SHA-256 과 다르다") }
            counts["published_plain_size"]?.let { if (bytes.size != it.toInt()) bad.add("${e.id}: 적힌 크기 $it 와 다르다(${bytes.size})") }
            counts["published_zip_sizes"]?.let { want ->
                val sizes = zipSizes(bytes).joinToString(",")
                if (sizes != want) bad.add("${e.id}: ZIP 항목 크기 $sizes ≠ POI $want")
            }
            bytes.fill(0)
        }
        assertTrue(bad.isEmpty(), bad.joinToString("\n"))
    }

    private fun zipSizes(bytes: ByteArray): List<Long> {
        val out = ArrayList<Long>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            val buf = ByteArray(8192)
            while (true) {
                z.nextEntry ?: break
                var n = 0L
                while (true) {
                    val r = z.read(buf)
                    if (r < 0) break
                    n += r
                }
                out.add(n)
            }
        }
        return out
    }
}
