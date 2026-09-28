package io.github.donggi.iroiroviewer.format.archive

import io.github.donggi.iroiroviewer.format.FileDocumentSource
import io.github.donggi.iroiroviewer.safety.EntryBudget
import io.github.donggi.iroiroviewer.safety.ParseLimits
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InterruptedIOException
import java.io.OutputStream
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 순차 추출이 알리는 **소비한 입력 바이트**(진행률의 분자)와 [ArchiveReader.inputBytesFor](분모).
 * 형식마다 셀 수 있는 것이 달라 약속도 다르다 — `ArchiveReader.inputBytesFor` 의 표를 형식별로 박는다.
 */
class ExtractProgressTest {

    private val dir: File = Files.createTempDirectory("iroiro-progress").toFile()

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    private class Recorder(val select: (ArchiveEntry) -> Boolean = { true }) : EntrySink {
        val consumed = ArrayList<Long>()
        override fun begin(entry: ArchiveEntry): OutputStream? =
            if (entry.isReadable && select(entry)) ByteArrayOutputStream() else null

        override fun finish(entry: ArchiveEntry, written: Long, failure: Throwable?) = Unit

        override fun consumed(inputBytes: Long) {
            consumed += inputBytes
        }
    }

    private fun open(f: File) = Archives.open(FileDocumentSource(f), ParseLimits.DEFAULT, EntryBudget())

    /** ZIP 은 분모가 고른 항목의 압축 크기 합이고, 분자는 항목 스트림이 실제로 읽은 압축 바이트다 — 끝에서 같다. */
    @Test
    fun `ZIP 은 고른 항목의 압축 크기 합에서 끝난다`() {
        val f = ArchiveSamples.zip(File(dir, "p.zip"), count = 8, bytesEach = 50_000)
        val wanted = setOf(2, 5)
        open(f).use { r ->
            val total = r.inputBytesFor(wanted)
            assertEquals(r.entries.filter { it.index in wanted }.sumOf { it.compressedSize }, total)
            val rec = Recorder { it.index in wanted }
            r.extractSequentially(rec)
            assertTrue(rec.consumed.zipWithNext().all { (a, b) -> b >= a }, "뒤로 갔다")
            assertEquals(total, rec.consumed.last())
            assertTrue(rec.consumed.size > 2, "항목 한가운데에서도 알려야 한다: ${rec.consumed}")
        }
    }

    /** 7z 는 패스가 끝까지 푼다 — 분모는 파일 크기이고 분자는 건너뛰며 푼 항목까지 센다(고른 것이 없어도 는다). */
    @Test
    fun `7z 는 건너뛰는 해제까지 세고 파일 크기 안에서 끝난다`() {
        val f = ArchiveSamples.sevenZ(File(dir, "p.7z"), count = 6, bytesEach = 40_000)
        open(f).use { r ->
            val total = r.inputBytesFor(null)
            assertEquals(f.length(), total)
            val rec = Recorder { false }
            r.extractSequentially(rec)
            val last = rec.consumed.last()
            assertTrue(last > 0, "아무것도 고르지 않아도 푼 입력은 센다")
            assertTrue(last <= total, "$last > $total")
            assertTrue(last * 10 >= total * 7, "팩 스트림이 파일의 대부분이어야 한다: $last / $total")
        }
    }

    /** 목록을 먼저 읽고 순차 추출을 해도 **머리를 두 번 세지 않는다**(예산의 엔트리 수는 줄지 않는 값이다). */
    @Test
    fun `tar 의 목록과 순차 추출이 엔트리를 두 번 세지 않는다`() {
        val b = TarBytes()
        repeat(5) { b.file("f$it", byteArrayOf(it.toByte())) }
        val tar = b.end()
        val limits = ParseLimits.DEFAULT.copy(maxEntries = 5)
        for (f in listOf(TarBytes.write(File(dir, "n.tar"), tar), TarBytes.write(File(dir, "n.tar.gz"), TarBytes.gzip(tar)))) {
            Archives.open(FileDocumentSource(f), limits, EntryBudget(limits)).use { r ->
                assertEquals(5, r.entries.size)
                r.extractSequentially(Recorder())
            }
            // 순서를 뒤집어도 — 압축 tar 는 순차 추출이 목록을 대신 세운다. 그 목록으로 항목을 열 수 있어야 한다
            // (자료의 자리까지 함께 세워야 한다).
            Archives.open(FileDocumentSource(f), limits, EntryBudget(limits)).use { r ->
                r.extractSequentially(Recorder())
                assertEquals(5, r.entries.size, f.name)
                assertEquals(3, r.open(r.entries[3]).use { it.readBytes() }.single().toInt(), f.name)
            }
        }
    }

    /** 파일 채널은 인터럽트되면 `ClosedByInterruptException` 을 던진다. 취소의 말로 옮겨야 한다. */
    @Test
    fun `채널의 인터럽트 예외를 취소로 옮긴다`() {
        assertFailsWith<InterruptedIOException> {
            interruptible { throw java.nio.channels.ClosedByInterruptException() }
        }
    }
}
