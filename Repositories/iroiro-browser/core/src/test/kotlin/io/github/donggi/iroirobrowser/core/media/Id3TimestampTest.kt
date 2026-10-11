package io.github.donggi.iroirobrowser.core.media

import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class Id3TimestampTest {

    private fun syncsafe(n: Int) = byteArrayOf(((n shr 21) and 0x7F).toByte(), ((n shr 14) and 0x7F).toByte(), ((n shr 7) and 0x7F).toByte(),
        (n and 0x7F).toByte())

    /** ID3v2.4 태그 — PRIV 틀 하나(앞에 다른 틀 하나를 둘 수 있다). */
    private fun tag(pts: Long, before: ByteArray = ByteArray(0), owner: String = "com.apple.streaming.transportStreamTimestamp"): ByteArray {
        val priv = ByteArrayOutputStream().apply {
            write(owner.toByteArray(Charsets.ISO_8859_1))
            write(0)
            for (k in 7 downTo 0) write(((pts shr (8 * k)) and 0xFF).toInt())
        }.toByteArray()
        val frames = ByteArrayOutputStream().apply {
            write(before)
            write("PRIV".toByteArray())
            write(syncsafe(priv.size))
            write(byteArrayOf(0, 0))
            write(priv)
        }.toByteArray()
        return ByteArrayOutputStream().apply {
            write("ID3".toByteArray())
            write(byteArrayOf(4, 0, 0))
            write(syncsafe(frames.size))
            write(frames)
            write(byteArrayOf(0xFF.toByte(), 0xF1.toByte())) // 뒤따르는 ADTS
        }.toByteArray()
    }

    @Test
    fun PRIV_의_33비트_시각() {
        // 10초 = 900,000 (90 kHz). 위의 비트는 버린다(33 비트).
        assertEquals(10_000_000L, Id3Timestamp.firstTimestampUs(tag(900_000L)))
        assertEquals(10_000_000L, Id3Timestamp.firstTimestampUs(tag(900_000L or (1L shl 40))))
    }

    @Test
    fun 다른_틀_뒤의_PRIV() {
        val text = byteArrayOf('T'.code.toByte(), 'I'.code.toByte(), 'T'.code.toByte(), '2'.code.toByte()) + syncsafe(3) + byteArrayOf(0, 0, 3, 'a'.code.toByte(), 'b'.code.toByte())
        assertEquals(1_000_000L, Id3Timestamp.firstTimestampUs(tag(90_000L, before = text)))
    }

    @Test
    fun 없거나_다른_소유자() {
        assertNull(Id3Timestamp.firstTimestampUs(byteArrayOf(0xFF.toByte(), 0xF1.toByte(), 0x50, 0x80.toByte(), 0, 0x1F, 0xFC.toByte())))
        assertNull(Id3Timestamp.firstTimestampUs(tag(90_000L, owner = "com.example.other")))
        assertNull(Id3Timestamp.firstTimestampUs(ByteArray(0)))
    }
}
