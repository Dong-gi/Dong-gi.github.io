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
     * 실세계 CFB 를 모두 열어 **모든 스트림을 끝까지** 읽는다 — `samples-local/hwp/` 의 한글 문서(HWP 5.0 은 CFB 다)와
     * `samples-local/corpus/` 의 CFB(오피스의 암호 문서·doc·xls). **커밋하지 않는다**(CLAUDE.md '표본'). 없으면 건너뛴다.
     *
     * 오피스 쪽은 `CfbCorpusTest` 가 olefile 의 목록과 한 줄씩 견준다. 여기서 더 보는 것은 **한컴이 쓴 CFB** 다 — 그 가운데
     * K04(DIFAT 섹터 1)·K05(DIFAT 섹터 4, 33 MB)는 **남이 쓴 DIFAT 체인**을 지난다. 예전에는 `samples-local/cfb/` 를
     * 찾아 늘 건너뛰었고, DIFAT 은 msoffcrypto 가 잠근 9 MB 표본(`OfficeCfbTest`, 역시 늘 건너뛰었다)만 지났다.
     *
     * 스트림은 흘려 읽어 센다 — 그림 하나가 수십 MB 인 한글 문서가 있다. 읽은 양이 `streamLength`(선언값과 섹터 사슬 중 짧은
     * 쪽 — CLAUDE.md '안전' 의 CFB 불변식)와 같아야 한다.
     */
    @Test
    fun 로컬_표본이_있으면_모든_스트림을_끝까지_읽는다() {
        val local = generateSequence(dir) { it.parentFile }.map { File(it, "samples-local") }.firstOrNull { it.isDirectory }
        val files = listOf("hwp", "corpus")
            .flatMap { sub -> local?.let { File(it, sub).listFiles()?.toList() }.orEmpty() }
            .filter { f -> f.isFile && f.length() >= 512 && f.inputStream().use { s -> ByteArray(8).also { s.read(it) } }.let(CfbFile::isCfb) }
            .sortedBy { it.name }
        assumeTrue("samples-local/hwp·corpus 에 CFB 가 없다", files.isNotEmpty())
        var difat = 0
        val buf = ByteArray(64 * 1024)
        for (f in files) {
            var streams = 0
            CfbFile.open(FileDocumentSource(f)).use { cfb ->
                fun walk(e: CfbEntry) {
                    for (c in cfb.children(e)) {
                        if (c.isStream) {
                            var n = 0L
                            cfb.openStream(c).use { input ->
                                while (true) {
                                    val r = input.read(buf)
                                    if (r < 0) break
                                    n += r
                                }
                            }
                            assertEquals(cfb.streamLength(c), n, "${f.name} ${c.name}")
                            streams++
                        } else {
                            walk(c)
                        }
                    }
                }
                walk(cfb.root)
            }
            assertTrue(streams > 0, f.name)
            if (difatSectors(f) > 0) difat++
        }
        println("CFB ${files.size}개, DIFAT 을 쓰는 것 $difat 개")
        // 한글 표본이 있으면 DIFAT 을 쓰는 것도 있다(K04·K05) — 그 체인을 지나야 이 시험이 뜻이 있다.
        if (files.any { it.name.endsWith(".hwp", ignoreCase = true) }) assertTrue(difat > 0, "DIFAT 을 쓰는 표본이 없다")
    }

    /** 머리의 DIFAT 섹터 수(자리 0x48). 우리 리더를 거치지 않고 읽는다. */
    private fun difatSectors(f: File): Long {
        val head = f.inputStream().use { s -> ByteArray(0x4C).also { s.read(it) } }
        return (head[0x48].toLong() and 0xFF) or ((head[0x49].toLong() and 0xFF) shl 8) or
            ((head[0x4A].toLong() and 0xFF) shl 16) or ((head[0x4B].toLong() and 0xFF) shl 24)
    }
}
