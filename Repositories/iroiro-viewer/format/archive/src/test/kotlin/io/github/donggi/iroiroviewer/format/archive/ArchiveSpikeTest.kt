package io.github.donggi.iroiroviewer.format.archive

import io.github.donggi.iroiroviewer.format.FileDocumentSource
import io.github.donggi.iroiroviewer.format.FormatId
import io.github.donggi.iroiroviewer.safety.EntryBudget
import io.github.donggi.iroiroviewer.safety.ParseLimitExceededException
import io.github.donggi.iroiroviewer.safety.ParseLimits
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.apache.commons.compress.archivers.sevenz.SevenZMethodConfiguration
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import java.io.File
import java.nio.charset.Charset
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 압축 관통 시험.
 *
 * 이 PC 에 7-Zip·WinRAR·오피스가 하나도 없으므로 **표본을 테스트가 직접 만든다.**
 * commons-compress 는 읽기만이 아니라 쓰기도 하므로 CP949 zip 과 LZMA2 7z 를
 * 정확한 모양으로 찍어 낼 수 있다. RAR 만은 쓰기 구현이 없어 표본을 만들 수 없고,
 * 그래서 RAR 경로는 실제 파일이 생길 때까지 미검증으로 남는다(진단 화면이 그 사실을 표시한다).
 */
class ArchiveSpikeTest {

    private val tmp: File = File(
        System.getProperty("java.io.tmpdir"),
        "iroiro-archive-${System.nanoTime()}",
    ).apply { mkdirs() }

    @AfterTest
    fun cleanup() {
        tmp.deleteRecursively()
    }

    // ---- ZIP 파일명 인코딩 -------------------------------------------------

    private fun writeZip(
        name: String,
        entryName: String,
        encoding: String,
        languageFlag: Boolean,
        body: ByteArray = "내용".toByteArray(),
    ): File {
        val f = File(tmp, name)
        ZipArchiveOutputStream(f).use { out ->
            out.setEncoding(encoding)
            out.setUseLanguageEncodingFlag(languageFlag)
            // 이름을 담지 못하는 인코딩이면 UTF-8 로 몰래 바꾸는 기능이 있는데,
            // 그러면 '플래그 없는 CP949' 라는 시험 대상 자체가 사라진다. 끈다.
            out.setFallbackToUTF8(false)
            out.setCreateUnicodeExtraFields(ZipArchiveOutputStream.UnicodeExtraFieldPolicy.NEVER)
            val e = ZipArchiveEntry(entryName)
            e.size = body.size.toLong()
            out.putArchiveEntry(e)
            out.write(body)
            out.closeArchiveEntry()
        }
        return f
    }

    private fun namesOf(file: File): List<Pair<String, String>> =
        ZipArchiveReader(FileDocumentSource(file), EntryBudget()).use { r ->
            r.entries.map { it.name to it.nameCharset }
        }

    @Test
    fun `CP949 로 적힌 한국어 파일명을 살려 낸다`() {
        // 한국에서 만들어진 아카이브의 대다수가 이 모양이다. 플래그가 없으니
        // 라이브러리는 알 길이 없고, 우리가 바이트를 보고 판정해야 한다.
        val cp949 = EntryNameDecoder.cp949
        if (cp949 == null) {
            // 이 JVM 에 CP949 가 없으면 시험할 것이 없다. 안드로이드에서는 진단 화면이
            // 실제 지원 여부를 보여준다.
            return
        }
        val f = writeZip("cp949.zip", "한글이름.txt", cp949.name(), languageFlag = false)
        val (name, charset) = namesOf(f).single()
        assertEquals("한글이름.txt", name)
        assertEquals(cp949.name(), charset)
    }

    @Test
    fun `UTF-8 플래그가 선 아카이브는 플래그를 따른다`() {
        val f = writeZip("utf8flag.zip", "한글이름.txt", "UTF-8", languageFlag = true)
        val (name, charset) = namesOf(f).single()
        assertEquals("한글이름.txt", name)
        assertEquals("UTF-8(플래그)", charset)
    }

    @Test
    fun `UTF-8 인데 플래그를 빠뜨린 아카이브도 살려 낸다`() {
        // 플래그를 안 세우는 도구가 흔하다. 엄격 디코드가 성공하면 UTF-8 로 본다.
        val f = writeZip("utf8noflag.zip", "한글이름.txt", "UTF-8", languageFlag = false)
        val (name, charset) = namesOf(f).single()
        assertEquals("한글이름.txt", name)
        assertEquals("UTF-8(플래그 없음)", charset)
    }

    @Test
    fun `ASCII 이름은 ASCII 로 끝낸다`() {
        val f = writeZip("ascii.zip", "readme.txt", "UTF-8", languageFlag = false)
        val (name, charset) = namesOf(f).single()
        assertEquals("readme.txt", name)
        assertEquals("ASCII", charset)
    }

    // ---- 7z / LZMA2 --------------------------------------------------------

    private fun writeSevenZ(name: String, entries: Map<String, ByteArray>): File {
        val f = File(tmp, name)
        SevenZOutputFile(f).use { out ->
            // 기본은 LZMA2 지만 명시한다 — 이 시험의 목적이 바로 LZMA2 경로가
            // 릴리스 빌드에서 살아 있는지 보는 것이다.
            out.setContentMethods(listOf(SevenZMethodConfiguration(SevenZMethod.LZMA2)))
            entries.forEach { (n, body) ->
                val e = SevenZArchiveEntry()
                e.name = n
                e.size = body.size.toLong()
                e.setHasStream(true)
                out.putArchiveEntry(e)
                out.write(body)
                out.closeArchiveEntry()
            }
            out.finish()
        }
        return f
    }

    @Test
    fun `LZMA2 로 압축된 7z 를 읽는다`() {
        // 압축이 실제로 걸리도록 반복이 많은 내용을 쓴다. 저장(무압축) 모드로 새면
        // R8 이 LZMA2 디코더를 지워도 이 시험이 통과해 버린다.
        val body = "가나다라마바사".repeat(2000).toByteArray()
        val f = writeSevenZ("lzma2.7z", mapOf("첫째.txt" to body, "둘째.txt" to body))
        assertTrue(f.length() < body.size / 4, "압축이 실제로 걸려야 이 시험이 의미가 있다")

        SevenZArchiveReader(FileDocumentSource(f), EntryBudget()).use { r ->
            assertEquals(FormatId.SEVEN_Z, r.formatId)
            assertEquals(listOf("첫째.txt", "둘째.txt"), r.entries.map { it.name })
            // solid 라 무작위 접근이 싸지 않다. 만화 뷰어가 이 값을 보고 전략을 바꾼다.
            assertEquals(false, r.randomAccess)
            val read = r.open(r.entries[1]).use { it.readBytes() }
            assertContains(String(read), "가나다라마바사")
            assertEquals(body.size, read.size)
        }
    }

    // ---- 판별 --------------------------------------------------------------

    @Test
    fun `매직 바이트로 컨테이너를 가린다`() {
        val zip = writeZip("p.zip", "a.txt", "UTF-8", true)
        val sevenZ = writeSevenZ("p.7z", mapOf("a.txt" to ByteArray(10)))
        assertEquals(FormatId.ZIP, Archives.probeContainer(FileDocumentSource(zip).head(16)))
        assertEquals(FormatId.SEVEN_Z, Archives.probeContainer(FileDocumentSource(sevenZ).head(16)))
        assertNull(Archives.probeContainer("보통 텍스트 파일".toByteArray()))
    }

    @Test
    fun `확장자가 거짓말을 해도 내용으로 판별한다`() {
        // .cbz 로 적힌 7z 는 실제로 흔하다.
        val f = writeSevenZ("거짓말.cbz", mapOf("001.jpg" to ByteArray(10)))
        assertEquals(FormatId.SEVEN_Z, Archives.probeContainer(FileDocumentSource(f).head(16)))
    }

    // ---- 방어 --------------------------------------------------------------

    @Test
    fun `경로 탈출 이름은 safeName 이 비어 위험 표시가 된다`() {
        val f = writeZip("evil.zip", "../../../etc/passwd", "UTF-8", true)
        ZipArchiveReader(FileDocumentSource(f), EntryBudget()).use { r ->
            val e = r.entries.single()
            assertEquals("../../../etc/passwd", e.name)
            assertNull(e.safeName)
            assertEquals(false, e.isReadable)
            assertFailsWith<IllegalArgumentException> { r.open(e) }
        }
    }

    @Test
    fun `압축 폭탄은 푸는 도중에 끊는다`() {
        // 0 으로 채운 20MB 는 zip 에서 20KB 안쪽으로 줄어든다. 선언된 크기를 믿지
        // 않고 실제로 읽은 바이트를 세는지 확인한다.
        val bomb = ByteArray(20 * 1024 * 1024)
        val f = File(tmp, "bomb.zip")
        ZipArchiveOutputStream(f).use { out ->
            val e = ZipArchiveEntry("bomb.bin")
            out.putArchiveEntry(e)
            out.write(bomb)
            out.closeArchiveEntry()
        }
        assertTrue(f.length() < 100_000, "폭탄 표본이 실제로 작아야 한다")

        val limits = ParseLimits(maxSingleOutput = 1024 * 1024)
        ZipArchiveReader(FileDocumentSource(f), EntryBudget(limits)).use { r ->
            assertFailsWith<ParseLimitExceededException> {
                r.open(r.entries.single()).use { it.readBytes() }
            }
        }
    }

    @Test
    fun `엔트리 수 상한이 목록 단계에서 걸린다`() {
        val f = File(tmp, "many.zip")
        ZipArchiveOutputStream(f).use { out ->
            repeat(50) { i ->
                out.putArchiveEntry(ZipArchiveEntry("f$i.txt"))
                out.write(byteArrayOf(1))
                out.closeArchiveEntry()
            }
        }
        val limits = ParseLimits(maxEntries = 10)
        assertFailsWith<ParseLimitExceededException> {
            ZipArchiveReader(FileDocumentSource(f), EntryBudget(limits)).close()
        }
    }

    @Test
    fun `같은 이름의 엔트리가 둘이어도 서로 다른 내용을 돌려준다`() {
        // ZIP 명세는 같은 이름을 금지하지 않는다. 이름을 신원으로 쓰면 두 엔트리가
        // 하나로 접혀, 사용자가 고른 것과 **다른 파일의 바이트**가 열린다.
        val f = File(tmp, "dup.zip")
        ZipArchiveOutputStream(f).use { out ->
            listOf("첫째", "둘째").forEach { body ->
                out.putArchiveEntry(ZipArchiveEntry("같은이름.txt"))
                out.write(body.toByteArray())
                out.closeArchiveEntry()
            }
        }
        ZipArchiveReader(FileDocumentSource(f), EntryBudget()).use { r ->
            assertEquals(2, r.entries.size)
            assertEquals(listOf(0, 1), r.entries.map { it.index })
            assertEquals("첫째", String(r.open(r.entries[0]).use { it.readBytes() }))
            assertEquals("둘째", String(r.open(r.entries[1]).use { it.readBytes() }))
        }
    }

    @Test
    fun `엔트리 목록을 만들다 실패해도 핸들이 새지 않는다`() {
        // 생성자가 던진 객체는 호출자가 close() 할 방법이 없다. 리더가 스스로 닫아야 한다.
        // 파일을 지울 수 있으면 아무도 그 파일을 잡고 있지 않다는 뜻이다(윈도우 기준).
        val f = File(tmp, "many2.zip")
        ZipArchiveOutputStream(f).use { out ->
            repeat(20) { i ->
                out.putArchiveEntry(ZipArchiveEntry("f$i.txt"))
                out.write(byteArrayOf(1))
                out.closeArchiveEntry()
            }
        }
        assertFailsWith<ParseLimitExceededException> {
            ZipArchiveReader(FileDocumentSource(f), EntryBudget(ParseLimits(maxEntries = 5)))
        }
        assertTrue(f.delete(), "핸들이 남아 있으면 윈도우에서 지워지지 않는다")
    }

    @Test
    fun `Charset 탐색이 이 JVM 에서 무엇을 찾았는지 남긴다`() {
        // 실패시키지 않는다. 안드로이드와 JVM 의 별칭 지원이 다를 수 있어서,
        // 이 값은 '확인된 사실' 로 기록하는 것이 목적이다.
        val found = EntryNameDecoder.cp949Name
        println("이 JVM 의 CP949 = $found")
        println("EUC-KR 지원 = ${Charset.isSupported("EUC-KR")}")
    }
}
