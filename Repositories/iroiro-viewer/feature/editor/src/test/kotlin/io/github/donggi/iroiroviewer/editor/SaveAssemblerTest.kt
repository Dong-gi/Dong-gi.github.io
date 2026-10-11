package io.github.donggi.iroiroviewer.editor

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SaveAssemblerTest {

    private val dir: File = Files.createTempDirectory("save").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun chunk(id: Long, seq: Long, payload: ByteArray): ByteArray {
        val out = ByteArray(8 + payload.size)
        for (i in 0 until 4) out[i] = (id ushr (24 - 8 * i)).toByte()
        for (i in 0 until 4) out[4 + i] = (seq ushr (24 - 8 * i)).toByte()
        payload.copyInto(out, 8)
        return out
    }

    private fun split(b: ByteArray, size: Int): List<ByteArray> = (b.indices step size).map { b.copyOfRange(it, minOf(b.size, it + size)) }

    @Test
    fun `덩이를 차례로 받아 길이와 해시가 맞으면 파일이 된다`() {
        val data = Random(7).nextBytes(1_000_003)
        val a = SaveAssembler(10_000_000)
        val file = File(dir, "out.hwpx")
        assertIs<SaveAssembler.Step.Ack>(a.begin(5, data.size.toLong(), sha(data), file))
        split(data, 262_144).forEachIndexed { i, part ->
            assertEquals(SaveAssembler.Step.Ack(5, i.toLong()), a.chunk(chunk(5, i.toLong(), part)))
        }
        val done = assertIs<SaveAssembler.Step.Done>(a.end(5))
        assertEquals(data.size.toLong(), done.size)
        assertContentEquals(data, file.readBytes())
    }

    @Test
    fun `빈 문서도 저장된다`() {
        val a = SaveAssembler(100)
        val file = File(dir, "empty.bin")
        a.begin(1, 0, sha(ByteArray(0)), file)
        assertIs<SaveAssembler.Step.Done>(a.end(1))
        assertEquals(0, file.length())
    }

    @Test
    fun `해시가 다르면 실패하고 쓰던 파일을 지운다`() {
        val data = "hello".toByteArray()
        val a = SaveAssembler(100)
        val file = File(dir, "bad.bin")
        a.begin(2, data.size.toLong(), sha("other".toByteArray()), file)
        a.chunk(chunk(2, 0, data))
        assertIs<SaveAssembler.Step.Failed>(a.end(2))
        assertFalse(file.exists())
    }

    @Test
    fun `차례가 어긋나거나 다른 번호의 덩이면 실패한다`() {
        val data = ByteArray(10) { it.toByte() }
        val a = SaveAssembler(100)
        val f1 = File(dir, "a.bin")
        a.begin(3, 10, sha(data), f1)
        assertIs<SaveAssembler.Step.Failed>(a.chunk(chunk(3, 1, data)))
        assertFalse(f1.exists())

        val f2 = File(dir, "b.bin")
        a.begin(4, 10, sha(data), f2)
        assertIs<SaveAssembler.Step.Failed>(a.chunk(chunk(9, 0, data)))
        assertFalse(f2.exists())
    }

    @Test
    fun `적은 크기보다 많이 오거나 상한을 넘으면 실패한다`() {
        val a = SaveAssembler(100)
        val f = File(dir, "c.bin")
        a.begin(1, 4, sha(ByteArray(4)), f)
        assertIs<SaveAssembler.Step.Failed>(a.chunk(chunk(1, 0, ByteArray(5))))
        assertIs<SaveAssembler.Step.Failed>(a.begin(2, 101, sha(ByteArray(0)), File(dir, "d.bin")))
    }

    @Test
    fun `해시 꼴이 아니면 열지 않는다`() {
        val a = SaveAssembler(100)
        assertIs<SaveAssembler.Step.Failed>(a.begin(1, 1, "ABC", File(dir, "e.bin")))
        assertIs<SaveAssembler.Step.Failed>(a.begin(1, 1, "A".repeat(64), File(dir, "e.bin")))
    }

    @Test
    fun `새 저장이 오면 앞의 것은 버린다`() {
        val data = "abc".toByteArray()
        val a = SaveAssembler(100)
        val first = File(dir, "first.bin")
        a.begin(1, 3, sha(data), first)
        a.chunk(chunk(1, 0, "a".toByteArray()))
        val second = File(dir, "second.bin")
        a.begin(2, 3, sha(data), second)
        assertFalse(first.exists())
        assertIs<SaveAssembler.Step.Ack>(a.chunk(chunk(2, 0, data)))
        assertIs<SaveAssembler.Step.Done>(a.end(2))
        assertTrue(second.isFile)
    }

    @Test
    fun `머리보다 짧은 덩이는 실패다`() {
        val a = SaveAssembler(100)
        a.begin(1, 1, sha(ByteArray(1)), File(dir, "f.bin"))
        assertIs<SaveAssembler.Step.Failed>(a.chunk(ByteArray(7)))
    }
}
