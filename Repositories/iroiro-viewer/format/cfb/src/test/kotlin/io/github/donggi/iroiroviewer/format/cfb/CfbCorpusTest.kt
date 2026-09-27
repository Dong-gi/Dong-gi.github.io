package io.github.donggi.iroiroviewer.format.cfb

import io.github.donggi.iroiroviewer.format.FileDocumentSource
import org.junit.Assume.assumeTrue
import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * **실세계 CFB**(`samples-local/corpus/` 의 암호 OOXML·이전 형식, 저장소에 넣지 않는다)를 olefile 0.47 이 읽은
 * 목록(`oracle/<ID>.listing` — 저장소 경로, 스트림 경로·크기·SHA-256)과 한 줄씩 견준다. 없으면 건너뛴다.
 *
 * 스트림 크기는 **읽을 수 있는 만큼**(`streamLength` — 선언값과 섹터 사슬 중 짧은 쪽)으로 견준다. 그것이
 * CLAUDE.md '안전' 의 CFB 불변식이고, olefile 도 사슬이 짧으면 사슬만큼 준다(extenXLS 가 쓴 표본 하나가 그렇다).
 */
class CfbCorpusTest {

    private val corpus: File? = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "samples-local/corpus") }
        .firstOrNull { File(it, "oracle").isDirectory }

    private fun sha256(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    /** olefile 목록과 같은 모양. 이름 속 제어 문자는 `\x06` 처럼 적는다(오라클과 같게). */
    private fun listing(cfb: CfbFile): Set<String> {
        val out = HashSet<String>()
        fun show(name: String) = buildString {
            for (c in name) if (c.code < 0x20) append("\\x%02x".format(c.code)) else append(c)
        }
        fun walk(e: CfbEntry, prefix: String) {
            for (c in cfb.children(e)) {
                val path = if (prefix.isEmpty()) show(c.name) else "$prefix/${show(c.name)}"
                if (c.isStream) {
                    val bytes = cfb.readStream(c, 1L shl 26)
                    out.add("stream\t$path\t${bytes.size}\t${sha256(bytes)}")
                } else {
                    out.add("storage\t$path")
                    walk(c, path)
                }
            }
        }
        walk(cfb.root, "")
        return out
    }

    @Test
    fun 실세계_CFB_를_olefile_과_똑같이_읽는다() {
        val dir = corpus
        assumeTrue("samples-local/corpus 가 없다", dir != null)
        val listings = File(dir, "oracle").listFiles { f -> f.name.endsWith(".listing") }.orEmpty().sortedBy { it.name }
        assumeTrue("olefile 목록이 없다", listings.isNotEmpty())
        val bad = ArrayList<String>()
        for (oracle in listings) {
            val id = oracle.name.removeSuffix(".listing")
            val file = dir!!.listFiles { f -> f.name.substringBeforeLast('.') == id && !f.isDirectory }?.firstOrNull() ?: continue
            val want = oracle.readLines(Charsets.UTF_8).filter { it.isNotBlank() }.toSet()
            try {
                CfbFile.open(FileDocumentSource(file)).use { cfb ->
                    val got = listing(cfb)
                    println("$id\t${file.name}\t판 ${cfb.version}\t항목 ${got.size}\t${if (got == want) "같다" else "다르다"}")
                    if (got != want) bad.add("$id: 우리에게만 ${got - want} / olefile 에만 ${want - got}")
                }
            } catch (e: Exception) {
                bad.add("$id: ${e::class.java.simpleName} ${e.message}")
            }
        }
        assertTrue(bad.isEmpty(), bad.joinToString("\n"))
    }
}
