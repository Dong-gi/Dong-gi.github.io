package io.github.donggi.iroiroviewer.format.archive

import io.github.donggi.iroiroviewer.format.ByteArrayDocumentSource
import io.github.donggi.iroiroviewer.format.FileDocumentSource
import org.apache.commons.compress.compressors.lz4.FramedLZ4CompressorInputStream
import org.apache.commons.compress.compressors.lz4.FramedLZ4CompressorOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * LZ4 를 **프레임 하나씩** 푸는 고리([Lz4FramesInputStream])와 해제기의 마지막 그물([StackGuardInputStream]).
 *
 * 기기에서 재현한 것: 빈 프레임(11바이트) 20만 개짜리 `x.cbt` 를 둔 폴더를 **열기만 했는데** 표지 썸네일의 판별이
 * commons-compress 의 이어 풀기(`decompressConcatenated = true`)에 들어가 `StackOverflowError` 로 프로세스가 죽었다. 시험은
 * **스택이 작은 스레드**([SMALL_STACK])에서 돈다 — 데스크톱 JVM 의 기본 스택이 기기보다 크면 재귀가 남아 있어도 지나가 버린다.
 */
class Lz4FramesTest {

    private val dir: File = Files.createTempDirectory("iroiro-lz4").toFile()

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    /** 빈 프레임 — 블록 독립·검사값 없음·최대 블록 4 MiB, 머리 검사 바이트 `73`, 끝 표시 `00 00 00 00`. */
    private val emptyFrame = byteArrayOf(0x04, 0x22, 0x4D, 0x18, 0x60, 0x70, 0x73, 0, 0, 0, 0)

    private val skippable = byteArrayOf(0x5A, 0x2A, 0x4D, 0x18, 3, 0, 0, 0, 1, 2, 3)

    private fun repeated(frame: ByteArray, n: Int): ByteArray {
        val out = ByteArray(frame.size * n)
        for (i in 0 until n) frame.copyInto(out, i * frame.size)
        return out
    }

    private fun readAll(bytes: ByteArray, name: String = "x.lz4"): ByteArray =
        CompressedStreams.open(ByteArrayDocumentSource(bytes, name), StreamCodec.LZ4).first.use { it.readBytes() }

    /** [block] 을 스택이 작은 스레드에서 돌린다. 던진 것은 그대로 다시 던진다(`StackOverflowError` 도). */
    private fun <T> onSmallStack(block: () -> T): T {
        var result: Result<T>? = null
        val t = Thread(null, { result = runCatching(block) }, "small-stack", SMALL_STACK)
        t.start()
        t.join()
        return result!!.getOrThrow()
    }

    /**
     * **이 시험의 전제.** 라이브러리의 이어 풀기는 빈 프레임마다 한 겹씩 재귀한다(1.28.0 의 `nextBlock` ↔ `init`). 이것이 넘치지 않게
     * 되었으면(라이브러리가 고쳤다) 이 시험을 지우고 고리를 다시 볼 수 있다 — 그때까지는 우리 고리가 그 길을 쓰지 않아야 한다.
     */
    @Test
    fun `라이브러리의 이어 풀기는 빈 프레임마다 재귀한다`() {
        val bytes = repeated(emptyFrame, 50_000)
        assertFailsWith<StackOverflowError> {
            onSmallStack { FramedLZ4CompressorInputStream(ByteArrayInputStream(bytes), true).use { it.readBytes() } }
        }
    }

    /** 빈 프레임 5만 개는 넘치지 않고 **빈 내용**이다(`lz4 -d` 와 같다 — 깨졌다고 하지 않는다. 까닭은 [Lz4FramesInputStream] 의 주석). */
    @Test
    fun `빈 프레임 5만 개는 넘치지 않고 빈 내용이다`() {
        val out = onSmallStack { readAll(repeated(emptyFrame, 50_000)) }
        assertEquals(0, out.size)
    }

    /**
     * 기기에서 재현한 모양 그대로 — 빈 프레임 20만 개짜리 `x.cbt`. 판별·목록·풀기가 모두 정의된 답을 낸다: 판별은 입력 상한(프레임마다
     * 값을 얹는다)에서 멈춰 안을 보지 못한 항목 하나짜리(LZ4)이고, 그 항목은 빈 내용이다.
     */
    @Test
    fun `빈 프레임만 든 만화 파일을 판별하고 열어도 넘치지 않는다`() {
        val f = File(dir, "x.cbt").apply { writeBytes(repeated(emptyFrame, 200_000)) }
        assertEquals(ArchiveKind.LZ4, onSmallStack { Archives.detect(FileDocumentSource(f)) })
        onSmallStack {
            Archives.open(FileDocumentSource(f)).use { r ->
                assertEquals(ArchiveKind.LZ4, r.kind)
                val e = r.entries.single()
                assertEquals("x", e.name)
                assertEquals(0, r.open(e).use { it.readBytes() }.size)
                val sink = Collector()
                r.extractSequentially(sink)
                assertNull(sink.failure)
                assertEquals(0L, sink.written)
            }
        }
    }

    /** 블록이 앞 블록에 기대는 빈 프레임(해제기가 64 KiB 창을 잡는다)과 내용 검사값이 붙은 빈 프레임도 같다. */
    @Test
    fun `여러 모양의 빈 프레임도 빈 내용이다`() {
        val dependent = StreamBytes.lz4(
            ByteArray(0),
            FramedLZ4CompressorOutputStream.Parameters(FramedLZ4CompressorOutputStream.BlockSize.K64, false, false, true),
        )
        val checked = StreamBytes.lz4(
            ByteArray(0),
            FramedLZ4CompressorOutputStream.Parameters(FramedLZ4CompressorOutputStream.BlockSize.M4, true, false, false),
        )
        for (frame in listOf(dependent, checked)) {
            assertEquals(0, onSmallStack { readAll(repeated(frame, 5_000)) }.size)
        }
    }

    /** 건너뛰는 프레임은 맨 앞·프레임 사이·마지막 프레임 뒤 어디에 와도 지나가고, 빈 프레임이 사이에 끼어도 내용은 이어진다. */
    @Test
    fun `건너뛰는 프레임과 빈 프레임이 섞여도 내용이 이어진다`() {
        val a = "앞 프레임의 글 ".repeat(200).toByteArray()
        val b = "뒤 프레임의 글 ".repeat(300).toByteArray()
        val bytes = skippable + StreamBytes.lz4(a) + emptyFrame + skippable + emptyFrame + StreamBytes.lz4(b) + skippable + skippable
        assertContentEquals(a + b, readAll(bytes))
    }

    @Test
    fun `프레임 뒤의 알 수 없는 자료와 잘린 건너뛰는 프레임은 깨진 파일이다`() {
        val a = "본문".repeat(100).toByteArray()
        for (tail in listOf(byteArrayOf(1, 2, 3, 4, 5), byteArrayOf(1, 2), byteArrayOf(0x5A, 0x2A, 0x4D, 0x18, 100, 0, 0, 0, 1))) {
            assertFailsWith<IOException> { readAll(StreamBytes.lz4(a) + tail) }
        }
    }

    /** 프레임이 하나도 없으면(건너뛰는 프레임뿐) LZ4 가 아니다 — 판별은 '우리가 여는 아카이브가 아니다' 로 끝난다. */
    @Test
    fun `건너뛰는 프레임뿐이면 LZ4 가 아니다`() {
        assertFailsWith<IOException> { readAll(skippable + skippable) }
        val f = File(dir, "only-skip.lz4").apply { writeBytes(skippable) }
        assertNull(Archives.detect(FileDocumentSource(f)))
    }

    /**
     * **마지막 그물** — 해제기 안의 재귀가 스택을 넘기면 그 스트림은 깨진 것이다(`IOException`). 한 번 넘친 해제기는 다시 쓰지 않는다.
     * (bzip2 의 한 스트림 안의 빈 블록이 같은 꼴의 재귀다 — `CompressedStreams.open` 의 주석.)
     */
    @Test
    fun `해제기가 스택을 넘기면 깨진 파일이다`() {
        val guarded = StackGuardInputStream(Bottomless())
        onSmallStack {
            assertFailsWith<IOException> { guarded.read(ByteArray(4), 0, 4) }
            assertFailsWith<IOException> { guarded.read() }
        }
    }

    /** 읽을 때마다 끝없이 제 자신을 부르는 스트림 — 입력이 깊이를 정하는 재귀를 흉내 낸다. */
    private class Bottomless : InputStream() {
        override fun read(): Int = read()

        override fun read(b: ByteArray, off: Int, len: Int): Int = read(b, off, len)
    }

    private class Collector : EntrySink {
        var written = -1L
        var failure: Throwable? = null

        override fun begin(entry: ArchiveEntry): OutputStream = ByteArrayOutputStream()

        override fun finish(entry: ArchiveEntry, written: Long, failure: Throwable?) {
            this.written = written
            this.failure = failure
        }
    }

    private companion object {
        /** 기기의 작업 스레드(1 MiB 남짓)보다 작은 스택. */
        const val SMALL_STACK = 256L * 1024
    }
}
