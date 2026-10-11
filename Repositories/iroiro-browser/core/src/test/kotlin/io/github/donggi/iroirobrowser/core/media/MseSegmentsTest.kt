package io.github.donggi.iroirobrowser.core.media

import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class MseSegmentsTest {

    private fun box(type: String, body: Int): ByteArray = ByteArrayOutputStream().apply {
        val size = 8 + body
        write(byteArrayOf((size shr 24).toByte(), (size shr 16).toByte(), (size shr 8).toByte(), size.toByte()))
        write(type.toByteArray(Charsets.ISO_8859_1))
        write(ByteArray(body))
    }.toByteArray()

    @Test
    fun MP4_초기_조각() {
        val init = box("ftyp", 16) + box("moov", 100)
        val media = box("moof", 40) + box("mdat", 300)
        assertEquals(init.size, MseSegments.initLength("video/mp4; codecs=\"avc1.42E01E\"", init))
        // 초기 조각과 첫 조각을 한 번에 넣는 재생기 — 초기 조각만큼.
        assertEquals(init.size, MseSegments.initLength("video/mp4", init + media))
        // 조각만이면 초기 조각이 아니다.
        assertEquals(0, MseSegments.initLength("audio/mp4", media))
        assertEquals(0, MseSegments.initLength("video/mp4", box("styp", 8) + media))
        // ftyp 만 있고 moov 가 없으면 아니다.
        assertEquals(0, MseSegments.initLength("video/mp4", box("ftyp", 16) + media))
    }

    @Test
    fun 앞에_머리가_붙은_덩이() {
        val init = box("ftyp", 16) + box("moov", 100)
        val framed = ByteArray(13) + init
        assertEquals(init.size, MseSegments.initLength("video/mp4", framed, 13))
    }

    @Test
    fun 상자_중간에서_끝난_덩이() {
        val init = box("ftyp", 16) + box("moov", 100)
        val cut = init.copyOf(init.size - 30)
        assertEquals(cut.size, MseSegments.initLength("video/mp4", cut))
    }

    @Test
    fun WebM() {
        val head = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte(), 1, 2, 3, 4, 5, 6)
        val cluster = byteArrayOf(0x1F, 0x43, 0xB6.toByte(), 0x75, 9, 9, 9, 9)
        assertEquals(head.size, MseSegments.initLength("video/webm; codecs=\"vp9\"", head + cluster))
        assertEquals(head.size, MseSegments.initLength("audio/webm", head))
        assertEquals(0, MseSegments.initLength("video/webm", cluster + head))
    }

    @Test
    fun 초기_조각이_없는_흐름() {
        assertEquals(0, MseSegments.initLength("audio/mpeg", ByteArray(64) { 0xFF.toByte() }))
        assertEquals(0, MseSegments.initLength("audio/aac", ByteArray(64)))
        assertEquals(0, MseSegments.initLength("video/mp4", ByteArray(4)))
    }
}
