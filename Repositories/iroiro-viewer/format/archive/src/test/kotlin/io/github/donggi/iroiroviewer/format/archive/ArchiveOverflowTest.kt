package io.github.donggi.iroiroviewer.format.archive

import io.github.donggi.iroiroviewer.format.ByteArrayChannel
import io.github.donggi.iroiroviewer.format.FileDocumentSource
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.zip.CRC32
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * **크기 셈의 넘침** — tar 의 크기 칸(256진·PAX)과 xz 끝의 인덱스는 `Long` 의 끝까지 적을 수 있다. 자리 셈(`자리 + 크기`)이 넘쳐
 * 음수가 되면 상한 검사가 꺼지거나(0 폭탄을 끝까지 풀었다) `position(음수)` 가 입출력 예외가 아닌 `IllegalArgumentException` 을
 * 냈다. 2^62 를 넘는 크기는 깨진 머리이고, 견주기는 뺄셈으로 한다.
 */
class ArchiveOverflowTest {

    private val dir: File = Files.createTempDirectory("iroiro-overflow").toFile()

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    private fun file(name: String, bytes: ByteArray) = TarBytes.write(File(dir, name), bytes)

    /** PAX 줄 하나(`길이 열쇠=값\n` — 길이는 줄 전체). */
    private fun paxLine(key: String, value: String): ByteArray {
        val body = " $key=$value\n"
        var len = body.length + 1
        while ((len.toString() + body).length != len) len++
        return (len.toString() + body).toByteArray()
    }

    /** 크기 칸을 256진으로 — 첫 바이트 `0x80`, 끝 8바이트가 큰 끝의 값. */
    private fun base256(v: Long): ByteArray = ByteArray(12).also { b ->
        b[0] = 0x80.toByte()
        for (i in 0 until 8) b[4 + i] = ((v ushr (8 * (7 - i))) and 0xFF).toByte()
    }

    @Test
    fun `PAX 크기가 Long 의 끝 가까이면 깨진 머리다`() {
        val pax = paxLine("size", "9223372036854774784")
        // 뒤를 0 으로 채운다 — 예전에는 상한이 꺼져 이 0 을 끝까지 풀었다(압축하면 몇 KB 가 수 GB 로 풀리는 자리다).
        val tar = TarBytes().header("PaxHeaders/x", 'x', size = pax.size.toLong()).data(pax)
            .header("x", '0', size = 0).raw(ByteArray(64 * 1024)).end()
        for (f in listOf(file("pax.tar", tar), file("pax.tar.gz", TarBytes.gzip(tar)), file("pax.tar.bz2", TarBytes.bzip2(tar)))) {
            val e = assertFailsWith<IOException>(f.name) { Archives.open(FileDocumentSource(f)).use { it.entries } }
            assertTrue(e.message.orEmpty().contains("PAX"), "${f.name}: $e")
        }
    }

    @Test
    fun `256진 크기가 2의 62승을 넘으면 깨진 머리다`() {
        for (size in listOf((1L shl 62) + 1, Long.MAX_VALUE - 511, Long.MAX_VALUE)) {
            val tar = TarBytes().header("big.bin", '0', sizeField = base256(size)).raw(ByteArray(1024)).end()
            for (f in listOf(file("big-$size.tar", tar), file("big-$size.tar.gz", TarBytes.gzip(tar)))) {
                // `IllegalArgumentException` 이 아니라 입출력 예외(깨진 파일)다.
                assertFailsWith<IOException>("${f.name}") { Archives.open(FileDocumentSource(f)).use { it.entries } }
            }
        }
        // 2^62 그 자체는 머리로서는 받는다 — 파일이 그만큼 길지 않아 잘린 파일이 된다.
        val edge = TarBytes().header("edge.bin", '0', sizeField = base256(1L shl 62)).raw(ByteArray(1024)).end()
        assertFailsWith<EOFException> { Archives.open(FileDocumentSource(file("edge.tar", edge))).use { it.entries } }
    }

    /** 압축하지 않은 파일의 건너뛰기 — 남은 것보다 길면 넘치지 않고 잘린 파일이다. */
    @Test
    fun `채널 건너뛰기는 넘치지 않는다`() {
        val src = ChannelScanSource(ByteArrayChannel(ByteArray(100)))
        src.skip(10)
        for (n in listOf(Long.MAX_VALUE, Long.MAX_VALUE - 5, 91L)) {
            assertFailsWith<EOFException>("$n") { src.skip(n) }
        }
        assertEquals(90L, src.remaining())
    }

    /**
     * xz 끝의 인덱스가 블록 크기를 `Long` 의 끝 가까이 적으면 스트림 크기 셈이 넘쳐 `position(음수)` 가 `IllegalArgumentException` 을
     * 냈다 — 목록이 크기를 읽다 '입출력 실패' 로 끝났다. 이제 크기를 보이지 않을 뿐이다(-1).
     */
    @Test
    fun `xz 인덱스의 터무니없는 블록 크기는 크기를 모르는 것이다`() {
        for (unpadded in listOf(1L shl 61, (1L shl 62) + 1, Long.MAX_VALUE - 3)) {
            val f = file("huge-$unpadded.xz", xzWithIndex(unpadded, uncompressed = 5))
            assertEquals(-1L, XzIndex.uncompressedSize(FileDocumentSource(f)), "$unpadded")
        }
    }

    /** 블록 없이 머리·인덱스(레코드 하나)·꼬리만 지은 xz 스트림. 인덱스의 레코드는 부르는 쪽이 정한다(실제 블록과 맞지 않는다). */
    private fun xzWithIndex(unpadded: Long, uncompressed: Long): ByteArray {
        fun le32(v: Long) = ByteArray(4) { ((v ushr (8 * it)) and 0xFF).toByte() }
        fun crc(b: ByteArray) = CRC32().apply { update(b) }.value
        fun vli(v: Long): ByteArray {
            val out = ByteArrayOutputStream()
            var x = v
            while (x >= 0x80) {
                out.write(((x and 0x7F) or 0x80).toInt())
                x = x ushr 7
            }
            out.write(x.toInt())
            return out.toByteArray()
        }
        val flags = byteArrayOf(0, 0)
        val header = byteArrayOf(0xFD.toByte(), 0x37, 0x7A, 0x58, 0x5A, 0) + flags + le32(crc(flags))
        val body = ByteArrayOutputStream().apply {
            write(0)
            write(vli(1))
            write(vli(unpadded))
            write(vli(uncompressed))
        }
        while (body.size() % 4 != 0) body.write(0)
        val indexNoCrc = body.toByteArray()
        val index = indexNoCrc + le32(crc(indexNoCrc))
        val tail = le32(index.size / 4L - 1) + flags
        val footer = le32(crc(tail)) + tail + byteArrayOf('Y'.code.toByte(), 'Z'.code.toByte())
        return header + index + footer
    }
}
