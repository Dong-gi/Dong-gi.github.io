package io.github.donggi.iroiroviewer.format.archive

import io.github.donggi.iroiroviewer.format.FileDocumentSource
import io.github.donggi.iroiroviewer.safety.EntryBudget
import io.github.donggi.iroiroviewer.safety.ParseLimitExceededException
import io.github.donggi.iroiroviewer.safety.ParseLimits
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import java.io.File
import java.nio.file.Files
import java.util.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * **엔트리 수를 세는 자리가 옳은가.**
 *
 * 이 시험은 8단계 설계 검토가 찾아낸 치명 결함에서 나왔다. 예전 코드는
 * `파일크기 / 46` 이라는 **상계**를 실제 개수처럼 상한과 견줘서, 460,000바이트를 넘는
 * ZIP 이면 엔트리가 셋이어도 거절했다. 2단계부터 8단계 시작까지 **실사용 크기의 zip 을
 * 거의 다 못 열고 있었고**, 표본이 전부 수백 바이트라 자가시험도 통과했다.
 *
 * 그래서 여기서 두 가지를 못 박는다 — **큰 아카이브가 열리는가**(회귀), 그리고
 * **진짜로 많은 엔트리는 여전히 막히는가**(원래 지키려던 것).
 */
class ZipEntryCountTest {

    private val dir: File = Files.createTempDirectory("iroiro-count").toFile()

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    /** 압축되지 않는 내용으로 채운다 — 잘 압축되면 파일이 커지지 않아 시험이 성립하지 않는다. */
    private fun bigZip(name: String, totalBytes: Int, entries: Int): File {
        val f = File(dir, name)
        ZipArchiveOutputStream(f).use { out ->
            val rnd = Random(1)
            repeat(entries) { i ->
                out.putArchiveEntry(ZipArchiveEntry("f$i.bin"))
                out.write(ByteArray(totalBytes / entries).also { rnd.nextBytes(it) })
                out.closeArchiveEntry()
            }
        }
        return f
    }

    private fun open(f: File, limits: ParseLimits = ParseLimits.DEFAULT) =
        ZipArchiveReader(FileDocumentSource(f), EntryBudget(limits))

    /**
     * **엔트리 셋짜리 30 MB zip 이 열려야 한다.**
     *
     * 예전 코드가 거절하던 바로 그 경우다. 크기를 여럿 두는 것은 상계 방식이 되살아나면
     * 어느 지점에서 깨지는지 바로 보이게 하려는 것이다.
     */
    @Test
    fun `큰 아카이브도 엔트리가 적으면 열린다`() {
        for (bytes in listOf(400 shl 10, 512 shl 10, 2 shl 20, 30 shl 20)) {
            val f = bigZip("z$bytes.zip", bytes, entries = 3)
            open(f).use { assertEquals(3, it.entries.size, "${f.length()}바이트 아카이브") }
        }
    }

    /** 원래 지키려던 것. 진짜로 상한을 넘는 개수는 **열기 전에** 막힌다. */
    @Test
    fun `엔트리가 정말 많으면 열기 전에 막힌다`() {
        val f = ArchiveSamples.zip(File(dir, "many.zip"), count = 1_200, bytesEach = 8)
        val limits = ParseLimits.PROBE // maxEntries = 1,000
        val thrown = assertFailsWith<ParseLimitExceededException> { open(f, limits).use { } }
        assertEquals("maxEntries", thrown.limitName)
        // 적힌 수를 읽었으므로 메시지에 **정확한 개수**가 실린다(상계가 아니다).
        assert(thrown.message!!.contains("1200")) { "메시지에 실제 개수가 없다: ${thrown.message}" }
    }

    @Test
    fun `EOCD 에 적힌 수를 그대로 읽는다`() {
        val f = ArchiveSamples.zip(File(dir, "n.zip"), count = 37, bytesEach = 16)
        assertEquals(37L, ZipEntryCount.read(FileDocumentSource(f)))
    }

    private fun zipWithComment(name: String, comment: String): File {
        val f = File(dir, name)
        ZipArchiveOutputStream(f).use { out ->
            out.setComment(comment)
            repeat(5) { i ->
                out.putArchiveEntry(ZipArchiveEntry("c$i.txt"))
                out.write("내용 $i\n".toByteArray())
                out.closeArchiveEntry()
            }
        }
        return f
    }

    /** 평범한 주석이 붙은 아카이브. 개수도 맞히고 열리기도 해야 한다. */
    @Test
    fun `주석이 붙어 있어도 열린다`() {
        val f = zipWithComment("comment.zip", "이 아카이브에 붙은 주석입니다")
        assertEquals(5L, ZipEntryCount.read(FileDocumentSource(f)))
        open(f).use { assertEquals(5, it.entries.size) }
    }

    /**
     * **주석 안에 EOCD 서명과 같은 바이트가 들어 있어도 우리는 속지 않는다.**
     *
     * 끝에서부터 찾되 **주석 길이 칸이 남은 바이트와 맞는 자리**만 진짜로 보기 때문이다.
     *
     * 여기서 commons-compress 의 한계도 함께 드러난다 — 그쪽의 EOCD 탐색은 길이를
     * 검증하지 않아 가짜 서명에 걸려 넘어진다(`Too many disks for zip archive`).
     * 그래서 이런 아카이브는 **개수는 맞히지만 열리지 않을 수 있다.** 고칠 자리가
     * 라이브러리 안쪽이라 우리가 할 수 있는 것은 실패를 정확히 옮기는 것뿐이고,
     * 그 사실을 시험으로 남겨 다음 사람이 '우리 코드가 틀렸나' 를 다시 파지 않게 한다.
     */
    @Test
    fun `주석 속 가짜 EOCD 서명에 속지 않는다`() {
        val fakeSignature = "PK" + 5.toChar() + 6.toChar()
        val f = zipWithComment("fake-eocd.zip", "주석 $fakeSignature 가짜 서명")
        assertEquals(5L, ZipEntryCount.read(FileDocumentSource(f)), "적힌 개수를 맞혀야 한다")

        val opened = runCatching { open(f).use { it.entries.size } }
        if (opened.isFailure) {
            // 못 열더라도 **입출력 실패**로 끝나야 한다. 화면은 이것을 '깨진 파일' 로 옮긴다.
            assertIs<java.io.IOException>(opened.exceptionOrNull())
        } else {
            assertEquals(5, opened.getOrThrow())
        }
    }

    /** ZIP 이 아니면 null 이다. **추측하지 않는다.** */
    @Test
    fun `ZIP 이 아니면 개수를 모른다고 답한다`() {
        val f = File(dir, "notzip.bin")
        f.writeBytes(ByteArray(1024) { it.toByte() })
        assertNull(ZipEntryCount.read(FileDocumentSource(f)))
    }

    /** 너무 짧은 파일에서 배열 밖을 읽지 않는다. */
    @Test
    fun `아주 짧은 파일에서도 터지지 않는다`() {
        for (n in 0..24) {
            val f = File(dir, "tiny$n.bin")
            f.writeBytes(ByteArray(n) { 0x50 })
            assertNull(ZipEntryCount.read(FileDocumentSource(f)), "$n 바이트")
        }
    }
}
