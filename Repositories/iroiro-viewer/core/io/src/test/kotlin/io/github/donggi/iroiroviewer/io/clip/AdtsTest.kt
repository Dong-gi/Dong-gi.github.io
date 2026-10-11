package io.github.donggi.iroiroviewer.io.clip

import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * ADTS 머리 떼기([Adts]) — 플랫폼의 TS 추출기가 AAC 를 머리째, PES 하나의 프레임을 묶어 내주는 것을 그릇에 프레임마다 날것으로 담는
 * 셈. 기기에서 그 길 전체는 `MediaClipperTest`(손으로 지은 TS — PES 하나에 프레임 하나, 그리고 다섯)가 본다.
 */
class AdtsTest {

    /** ADTS 프레임 하나 — LC · 44.1 kHz · 모노, [payload] 바이트의 날것([fill]) 뒤에. [crc] 면 머리가 9바이트다. */
    private fun frame(payload: Int, crc: Boolean = false, blocks: Int = 0, lengthDelta: Int = 0, fill: Int = 0x5A): ByteArray {
        val header = if (crc) 9 else 7
        val len = header + payload + lengthDelta
        val b = ByteArray(header + payload) { fill.toByte() }
        b[0] = 0xFF.toByte()
        b[1] = (if (crc) 0xF0 else 0xF1).toByte()
        b[2] = 0x50
        b[3] = (0x40 or ((len shr 11) and 0x03)).toByte()
        b[4] = (len shr 3).toByte()
        b[5] = (((len and 7) shl 5) or 0x1F).toByte()
        b[6] = (0xFC or (blocks and 0x03)).toByte()
        if (crc) {
            b[7] = 0x12
            b[8] = 0x34
        }
        return b
    }

    private fun split(vararg frames: ByteArray): List<Adts.Frame>? {
        val all = frames.reduce { a, b -> a + b }
        return Adts.split(ByteBuffer.wrap(all), all.size)
    }

    @Test
    fun CRC_가_없으면_7바이트_있으면_9바이트를_뗀다() {
        val plain = split(frame(186))!!.single()
        assertEquals(7, plain.rawOffset)
        assertEquals(186, plain.rawLength)
        val withCrc = split(frame(186, crc = true))!!.single()
        assertEquals(9, withCrc.rawOffset)
        assertEquals(186, withCrc.rawLength)
    }

    /**
     * 플랫폼의 TS 추출기는 PES 하나에서 시작하는 프레임을 모두 묶어 표본 하나로 준다(에뮬레이터 실측: 964바이트 = 프레임 다섯 —
     * 192·208·179·193·192). 예전에는 첫 머리의 길이가 표본과 다르다고 통째로 거절했다.
     */
    @Test
    fun 표본_하나에_프레임이_여럿이면_프레임마다_가른다() {
        val sizes = listOf(192, 208, 179, 193, 192)
        val frames = sizes.mapIndexed { i, s -> frame(s - 7, fill = 0x10 + i) }
        val all = frames.reduce { a, b -> a + b }
        assertEquals(964, all.size)
        val got = Adts.split(ByteBuffer.wrap(all), all.size)
        assertNotNull(got)
        assertEquals(5, got.size)
        var at = 0
        for ((i, f) in got.withIndex()) {
            assertEquals(at + 7, f.rawOffset, "프레임 $i 의 날것 자리")
            assertEquals(sizes[i] - 7, f.rawLength)
            // 날것은 그 프레임의 몸 그대로다(머리를 뗐다).
            assertEquals((0x10 + i).toByte(), all[f.rawOffset])
            assertEquals((0x10 + i).toByte(), all[f.rawOffset + f.rawLength - 1])
            at += sizes[i]
        }
    }

    @Test
    fun CRC_있는_프레임과_없는_프레임이_섞여도_가른다() {
        val got = split(frame(100), frame(50, crc = true), frame(20))
        assertNotNull(got)
        assertEquals(listOf(7, 107 + 9, 107 + 59 + 7), got.map { it.rawOffset })
        assertEquals(listOf(100, 50, 20), got.map { it.rawLength })
    }

    @Test
    fun 끝이_잘린_프레임이_있으면_통째로_담지_않는다() {
        // 마지막 프레임이 적힌 길이보다 짧다 — 머리만 떼면 날것이 아니다.
        val whole = frame(186) + frame(186)
        assertNull(Adts.split(ByteBuffer.wrap(whole), whole.size - 10))
        // 적힌 길이가 표본보다 길다(첫 프레임부터 잘렸다).
        assertNull(Adts.split(ByteBuffer.wrap(frame(186)), 150))
        // 머리보다 짧은 길이를 적었다 — 다음 프레임을 찾을 수 없다.
        assertNull(split(frame(186, lengthDelta = -186)))
        // 끝에 프레임이 되지 못하는 바이트가 남는다.
        val tail = frame(186) + byteArrayOf(0xFF.toByte(), 0xF1.toByte(), 0x50)
        assertNull(Adts.split(ByteBuffer.wrap(tail), tail.size))
    }

    @Test
    fun 날것_블록이_여럿인_프레임은_떼지_않는다() {
        // 블록 사이에 자리 표·CRC 가 끼어 머리만 떼어서는 날것이 되지 않는다. 둘째 프레임만 그래도 표본 전체를 담지 않는다.
        assertNull(split(frame(186, blocks = 1)))
        assertNull(split(frame(186), frame(186, blocks = 3)))
    }

    @Test
    fun ADTS_가_아니면_떼지_않는다() {
        // 날것의 AAC(동기 부호가 없다), 층 비트가 0 이 아닌 MPEG 소리 머리(FF FB — MP3), 너무 짧은 것.
        assertNull(Adts.split(ByteBuffer.wrap(ByteArray(200) { 0x21 }), 200))
        assertNull(Adts.split(ByteBuffer.wrap(byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x64, 0, 0, 0, 0)), 8))
        assertNull(Adts.split(ByteBuffer.wrap(byteArrayOf(0xFF.toByte(), 0xF1.toByte(), 0x50)), 3))
        // 둘째 프레임 자리에 동기 부호가 없다.
        assertNull(split(frame(186), ByteArray(40) { 0x21 }))
    }

    @Test
    fun 표본율은_첫_머리의_번호로() {
        assertEquals(44100, Adts.sampleRate(ByteBuffer.wrap(frame(10)), 17))
        // 번호 3(48 kHz) · 6(24 kHz — HE-AAC 의 핵심 표본율도 이렇게 적힌다).
        val f = frame(10)
        f[2] = ((f[2].toInt() and 0xC3) or (3 shl 2)).toByte()
        assertEquals(48000, Adts.sampleRate(ByteBuffer.wrap(f), f.size))
        f[2] = ((f[2].toInt() and 0xC3) or (6 shl 2)).toByte()
        assertEquals(24000, Adts.sampleRate(ByteBuffer.wrap(f), f.size))
        // 예약 번호(13·14)·쓸 수 없는 번호(15)는 모른다 — 부르는 쪽이 트랙 형식의 값을 쓴다.
        f[2] = ((f[2].toInt() and 0xC3) or (13 shl 2)).toByte()
        assertEquals(0, Adts.sampleRate(ByteBuffer.wrap(f), f.size))
        assertEquals(0, Adts.sampleRate(ByteBuffer.wrap(ByteArray(10)), 10))
    }
}
