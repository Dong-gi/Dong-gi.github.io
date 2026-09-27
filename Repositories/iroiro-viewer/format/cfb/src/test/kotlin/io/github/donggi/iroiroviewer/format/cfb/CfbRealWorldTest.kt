package io.github.donggi.iroiroviewer.format.cfb

import io.github.donggi.iroiroviewer.format.FileDocumentSource
import org.junit.Assume.assumeTrue
import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **다른 구현이 쓴 CFB** 를 읽는다.
 *
 * `cfb/msoffcrypto-agile.cfb` 는 msoffcrypto-tool 6.0.0 이 암호를 걸어 쓴 docx 다(MS-OFFCRYPTO 의 그릇 —
 * `EncryptionInfo`·`EncryptedPackage`·`\u0006DataSpaces` 아래 저장소 셋, 작은 스트림 여섯, 일반 스트림 하나).
 * 옆의 `.listing` 은 **olefile 0.47** 이 같은 파일을 읽어 적은 목록(저장소 경로, 스트림 경로·크기·SHA-256)이다.
 * 우리 리더가 그것과 한 줄도 어긋나지 않아야 한다.
 */
class CfbRealWorldTest {

    private val dir = File(javaClass.classLoader!!.getResource("cfb/msoffcrypto-agile.cfb")!!.toURI()).parentFile

    private fun sha256(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    /** 우리 리더로 본 목록 — olefile 목록과 같은 모양(`stream\t경로\t크기\tsha` / `storage\t경로`). */
    private fun listing(cfb: CfbFile): Set<String> {
        val out = HashSet<String>()
        fun walk(e: CfbEntry, prefix: String) {
            for (c in cfb.children(e)) {
                val path = (if (prefix.isEmpty()) c.name else "$prefix/${c.name}").replace("\u0006", "\\x06")
                if (c.isStream) {
                    val bytes = cfb.readStream(c, 1 shl 24)
                    assertEquals(c.size, bytes.size.toLong(), path)
                    assertEquals(c.size, cfb.streamLength(c), path)
                    out.add("stream\t$path\t${bytes.size}\t${sha256(bytes)}")
                } else {
                    out.add("storage\t$path")
                    walk(c, if (prefix.isEmpty()) c.name else "$prefix/${c.name}")
                }
            }
        }
        walk(cfb.root, "")
        return out
    }

    @Test
    fun msoffcrypto_가_쓴_그릇을_olefile_과_똑같이_읽는다() {
        val expected = File(dir, "msoffcrypto-agile.listing").readLines().filter { it.isNotBlank() }.toSet()
        CfbFile.open(FileDocumentSource(File(dir, "msoffcrypto-agile.cfb"))).use { cfb ->
            assertEquals(3, cfb.version)
            assertEquals(expected, listing(cfb))
            // 작은 스트림과 일반 스트림이 둘 다 있다.
            assertTrue(cfb.find("EncryptedPackage")!!.size >= 4096)
            assertTrue(cfb.find("\u0006DataSpaces", "Version")!!.size < 4096)
            // 이름은 대소문자를 가리지 않는다.
            assertEquals(cfb.find("EncryptionInfo")!!.id, cfb.find("encryptioninfo")!!.id)
        }
    }

    /**
     * 실세계 CFB(오피스가 쓴 암호 문서·doc·xls·HWP)를 `samples-local/cfb/` 에 두면 전부 열어 끝까지 읽는다.
     * **커밋하지 않는다**(CLAUDE.md '표본'). 없으면 건너뛴다.
     */
    @Test
    fun 로컬_표본이_있으면_모든_스트림을_끝까지_읽는다() {
        val local = generateSequence(dir) { it.parentFile }.map { File(it, "samples-local/cfb") }.firstOrNull { it.isDirectory }
        val files = local?.listFiles()?.filter { it.isFile }.orEmpty()
        assumeTrue("samples-local/cfb 가 없다", files.isNotEmpty())
        for (f in files) {
            val head = f.inputStream().use { s -> ByteArray(8).also { s.read(it) } }
            if (!CfbFile.isCfb(head)) continue
            CfbFile.open(FileDocumentSource(f)).use { cfb ->
                val seen = listing(cfb)
                assertTrue(seen.isNotEmpty(), f.name)
            }
        }
    }
}
