package io.github.donggi.iroiroviewer.format.hwp5

import io.github.donggi.iroiroviewer.safety.ParseLimitExceededException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** raw DEFLATE 와 배포용 문서의 복호화. */
class Hwp5StreamsTest {

    private val section = Rec.join(
        listOf(
            P().text("배포용 본문 첫 문단").bytes(0),
            P().text("둘째 문단").also { it.last = true }.bytes(0),
        )
    )

    @Test
    fun raw_DEFLATE_를_머리_없이_푼다() {
        val plain = ByteArray(100_000) { (it % 251).toByte() }
        val packed = HwpFile.deflate(plain)
        assertContentEquals(plain, HwpInflate.inflate(packed, 0, packed.size, 1_000_000, "t"))
        // zlib 머리(78 9C)가 붙은 것은 raw 가 아니다 — 깨진 것으로 본다.
        val zlib = java.util.zip.Deflater().run {
            setInput(plain); finish()
            val out = ByteArray(200_000)
            val n = deflate(out)
            end()
            out.copyOf(n)
        }
        assertFailsWith<HwpFormatException> { HwpInflate.inflate(zlib, 0, zlib.size, 1_000_000, "t") }
    }

    @Test
    fun 압축_폭탄은_상한에서_멈춘다() {
        val zeros = HwpFile.deflate(ByteArray(10_000_000))
        assertTrue(zeros.size < 20_000)
        val e = assertFailsWith<ParseLimitExceededException> { HwpInflate.inflate(zeros, 0, zeros.size, 1_000_000, "maxSectionBytes") }
        assertEquals("maxSectionBytes", e.limitName)
        // 딱 상한만큼은 된다.
        val exact = HwpFile.deflate(ByteArray(1_000_000))
        assertEquals(1_000_000, HwpInflate.inflate(exact, 0, exact.size, 1_000_000, "t").size)
    }

    @Test
    fun 잘린_압축은_깨진_파일이다() {
        val packed = HwpFile.deflate(ByteArray(50_000) { (it * 7).toByte() })
        assertFailsWith<HwpFormatException> { HwpInflate.inflate(packed, 0, packed.size / 2, 1_000_000, "t") }
    }

    @Test
    fun 배포용_스트림을_풀면_레코드_흐름이_나온다() {
        val enc = HwpFile.encryptDistribution(section, compressed = true, seed = 0x5BF764BE, flags = 0x8003)
        val d = Hwp5Distribution.decode(enc, compressed = true, max = 1_000_000)
        assertContentEquals(section, d.bytes)
        assertEquals(0x8003, d.flags)
        // 씨앗이 달라지면 열쇠 자리(4 + 씨앗 & 0xF)와 뒤섞기가 달라진다 — 그래도 풀린다.
        for (seed in listOf(0, 1, 0x7FFFFFFF, -1, 0x1234567F)) {
            val e2 = HwpFile.encryptDistribution(section, compressed = true, seed = seed, flags = 0x8001)
            assertContentEquals(section, Hwp5Distribution.decode(e2, true, 1_000_000).bytes, "seed=$seed")
        }
    }

    @Test
    fun 압축하지_않은_배포용은_블록을_채운_부스러기를_뗀다() {
        val enc = HwpFile.encryptDistribution(section, compressed = false, seed = 77, flags = 0x8001)
        assertTrue((enc.size - Hwp5Distribution.PREFIX) % 16 == 0 && section.size % 16 != 0)
        assertContentEquals(section, Hwp5Distribution.decode(enc, compressed = false, max = 1_000_000).bytes)
    }

    @Test
    fun 뒤섞기는_씨앗을_두고_나머지를_되돌린다() {
        val data = ByteArray(256) { (it * 31).toByte() }
        val copy = data.copyOf()
        Hwp5Distribution.descramble(data)
        assertContentEquals(copy.copyOf(4), data.copyOf(4)) // 씨앗은 그대로
        assertTrue(!copy.contentEquals(data))
        Hwp5Distribution.descramble(data)
        assertContentEquals(copy, data) // XOR 이라 두 번이면 원래대로
    }

    @Test
    fun 첫_레코드가_배포용_자료가_아니면_깨진_파일이다() {
        val enc = HwpFile.encryptDistribution(section, true, 5, 0x8001)
        val wrongTag = enc.copyOf().also { it[0] = HwpTag.PARA_HEADER.toByte() }
        assertFailsWith<HwpFormatException> { Hwp5Distribution.decode(wrongTag, true, 1_000_000) }
        assertFailsWith<HwpFormatException> { Hwp5Distribution.decode(enc.copyOf(100), true, 1_000_000) }
        // 열쇠가 틀리면(뒤섞인 자료가 망가지면) 풀린 것이 DEFLATE 가 아니다.
        val broken = enc.copyOf().also { for (i in 20..100) it[i] = (it[i] + 1).toByte() }
        assertFailsWith<HwpFormatException> { Hwp5Distribution.decode(broken, true, 1_000_000) }
    }
}
