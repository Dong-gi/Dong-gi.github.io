package io.github.donggi.iroirobrowser.core.media

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 열쇠 응답을 hls.js 처럼 너그럽게 읽되 첫 조각의 꼴로 확인하는가(2026-10-16 — m3u8 열쇠 오류 고침). 조각은 시험이 지은 합성 바이트(TS 묶음·
 * fMP4 상자·ID3·ADTS 머리)를 JCE 로 잠근 것이다.
 */
class HlsKeysTest {

    private val key = ByteArray(16) { (it * 11 + 3).toByte() }
    private val iv = ByteArray(16) { (it + 100).toByte() }

    private fun encrypt(plain: ByteArray, k: ByteArray = key, v: ByteArray = iv): ByteArray = Cipher.getInstance("AES/CBC/PKCS5Padding").run {
        init(Cipher.ENCRYPT_MODE, SecretKeySpec(k, "AES"), IvParameterSpec(v))
        doFinal(plain)
    }

    /** TS 묶음 [packets] 개 — 188 바이트마다 0x47, 나머지는 무늬. */
    private fun ts(packets: Int) = ByteArray(188 * packets) { i -> if (i % 188 == 0) 0x47 else (i * 31 + 7).toByte() }

    private fun box(type: String, size: Int) = ByteArray(size).also { b ->
        b[0] = (size ushr 24).toByte()
        b[1] = (size ushr 16).toByte()
        b[2] = (size ushr 8).toByte()
        b[3] = size.toByte()
        type.toByteArray(Charsets.ISO_8859_1).copyInto(b, 4)
        for (i in 8 until size) b[i] = (i * 13).toByte()
    }

    /** ADTS 프레임 하나 — 동기·MPEG-4·층 0·보호 없음, 표본율 번호 4(44.1 kHz), 길이 [length]. */
    private fun adts(length: Int) = ByteArray(length).also { b ->
        b[0] = 0xFF.toByte()
        b[1] = 0xF1.toByte()
        b[2] = (1 shl 6 or (4 shl 2)).toByte()
        b[3] = (2 shl 6 or ((length ushr 11) and 0x03)).toByte()
        b[4] = (length ushr 3).toByte()
        b[5] = ((length and 0x07) shl 5 or 0x1F).toByte()
        b[6] = 0xFC.toByte()
        for (i in 7 until length) b[i] = (i * 7).toByte()
    }

    private fun id3() = ByteArray(64).also { b ->
        "ID3".toByteArray(Charsets.ISO_8859_1).copyInto(b)
        b[3] = 4
        b[9] = 53
        for (i in 10 until 64) b[i] = (i * 5).toByte()
    }

    private fun hex(b: ByteArray) = b.joinToString("") { String.format("%02x", it.toInt() and 0xFF) }

    @Test
    fun 열쇠가_16바이트면_그대로_확인_없이() {
        val c = HlsKeys.candidates(key)
        assertContentEquals(key, c.exact)
        assertTrue(c.lenient.isEmpty())
        assertFalse(c.isEmpty)
    }

    @Test
    fun hls_js_처럼_16바이트보다_길면_앞_16바이트() {
        // 끝의 줄바꿈 — hls.js 는 앞 네 낱말만 읽어 이것으로 재생한다. 앞 판은 '잠금을 풀 열쇠를 받지 못했습니다' 로 끝냈다.
        val withNewline = key + byteArrayOf(10)
        val c = HlsKeys.candidates(withNewline)
        assertNull(c.exact)
        assertEquals(1, c.lenient.size)
        assertContentEquals(key, c.lenient[0])
        val segment = encrypt(ts(40))
        assertContentEquals(key, HlsKeys.pick(c.lenient, iv, segment.copyOf(HlsKeys.PROBE_BYTES.coerceAtMost(segment.size)), complete = false))
        // 32바이트(열쇠 뒤에 무엇이 붙었다) — 같다.
        val c32 = HlsKeys.candidates(key + ByteArray(16) { 9 })
        assertContentEquals(key, HlsKeys.pick(c32.lenient, iv, segment, complete = true))
    }

    @Test
    fun hex_와_base64_글은_풀어_본다() {
        val segment = encrypt(box("styp", 24) + box("moof", 200) + box("mdat", 400))
        val hexText = (hex(key) + Char(13) + Char(10)).toByteArray()
        val h = HlsKeys.candidates(hexText)
        assertContentEquals(key, h.lenient.first())
        assertContentEquals(key, HlsKeys.pick(h.lenient, iv, segment, complete = true))
        assertEquals(HlsKeys.BodyKind.HEX, HlsKeys.classify(hexText))
        val b64Text = Base64.getEncoder().encode(key)
        assertEquals(24, b64Text.size)
        val b = HlsKeys.candidates(b64Text)
        assertContentEquals(key, b.lenient.first())
        assertContentEquals(key, HlsKeys.pick(b.lenient, iv, segment, complete = true))
        assertEquals(HlsKeys.BodyKind.BASE64, HlsKeys.classify(b64Text))
        // URL 판 base64·앞의 0x 도.
        val url = Base64.getUrlEncoder().encodeToString(key).toByteArray()
        assertContentEquals(key, HlsKeys.candidates(url).lenient.first())
        assertContentEquals(key, HlsKeys.candidates(("0x" + hex(key).uppercase()).toByteArray()).lenient.first())
    }

    @Test
    fun 첫_조각의_꼴이_맞을_때만_쓴다() {
        val segment = encrypt(ts(40))
        // 웹 페이지가 왔다 — 앞 16바이트를 후보로 지어도 풀린 것이 TS 가 아니라 쓰지 않는다.
        val page = "<!doctype html><html><head><title>로그인</title></head><body>다시 로그인하세요</body></html>".toByteArray()
        val c = HlsKeys.candidates(page)
        assertEquals(1, c.lenient.size)
        assertNull(HlsKeys.pick(c.lenient, iv, segment, complete = true))
        assertNull(HlsKeys.pick(c.lenient, iv, segment.copyOf(1024), complete = false))
        // 틀린 열쇠·틀린 IV 도.
        val wrong = ByteArray(16) { 1 }
        assertNull(HlsKeys.pick(listOf(wrong), iv, segment, complete = true))
        assertNull(HlsKeys.pick(listOf(key), ByteArray(16), segment.copyOf(1024), complete = false))
        // 맞는 것이 뒤에 있으면 그것.
        assertContentEquals(key, HlsKeys.pick(listOf(wrong, key), iv, segment, complete = true))
        // 조각 전체면 채움까지 — 채움 바이트를 틀리게 하면(끝 앞 블록의 마지막 바이트를 뒤집으면 채움 0x10 이 0x11 이 된다) 앞머리의 꼴이 맞아도
        // 쓰지 않는다.
        val broken = segment.copyOf().also { it[it.size - 17] = (it[it.size - 17].toInt() xor 1).toByte() }
        assertNull(HlsKeys.pick(listOf(key), iv, broken, complete = true))
        assertContentEquals(key, HlsKeys.pick(listOf(key), iv, broken.copyOf(1024), complete = false))
        // 16 의 배수가 아닌 조각 전체는 잠긴 조각이 아니다.
        assertNull(HlsKeys.pick(listOf(key), iv, segment.copyOf(segment.size - 1), complete = true))
    }

    @Test
    fun 조각의_꼴() {
        assertTrue(HlsKeys.looksLikeMedia(ts(3)))
        assertFalse(HlsKeys.looksLikeMedia(ts(2)), "묶음 둘은 우연히 맞을 수 있다")
        assertFalse(HlsKeys.looksLikeMedia(ts(8).also { it[188 * 2] = 0x46 }), "셋째 묶음의 동기 바이트가 틀렸다")
        assertTrue(HlsKeys.looksLikeMedia(box("ftyp", 32) + box("moov", 100)))
        assertTrue(HlsKeys.looksLikeMedia(box("styp", 24) + box("sidx", 44)))
        assertTrue(HlsKeys.looksLikeMedia(box("moof", 100)))
        assertFalse(HlsKeys.looksLikeMedia(box("abcd", 100)), "모르는 상자")
        val tooSmall = ByteArray(100).also { b ->
            b[3] = 4
            "moof".toByteArray(Charsets.ISO_8859_1).copyInto(b, 4)
        }
        assertFalse(HlsKeys.looksLikeMedia(tooSmall), "크기가 상자 머리보다 작다")
        assertTrue(HlsKeys.looksLikeMedia(id3()))
        assertFalse(HlsKeys.looksLikeMedia(id3().also { it[3] = 9 }), "ID3 판이 틀렸다")
        assertTrue(HlsKeys.looksLikeMedia(adts(300) + adts(280) + adts(290)))
        // 동기 하나는 우연히 맞는다 — 다음 프레임이 없으면 ADTS 로 보지 않는다.
        assertFalse(HlsKeys.looksLikeMedia(adts(300) + ByteArray(600) { (it * 3 + 1).toByte() }))
        assertFalse(HlsKeys.looksLikeMedia(ByteArray(0)))
    }

    @Test
    fun 틀린_열쇠로_푼_바이트는_우연히_맞지_않는다() {
        // 틀린 열쇠로 푼 바이트는 고르게 흩어진다 — 고정 씨앗의 무작위 바이트 10만 덩이가 하나도 조각의 꼴로 보이지 않아야 한다.
        val rnd = Random(20261016)
        val buffer = ByteArray(2048)
        var hits = 0
        repeat(100_000) {
            rnd.nextBytes(buffer)
            if (HlsKeys.looksLikeMedia(buffer)) hits++
        }
        assertEquals(0, hits)
        // 진짜 조각을 무작위 열쇠 1만 개로 풀어도 하나도 맞지 않는다.
        val segment = encrypt(ts(40))
        val prefix = segment.copyOf(2048)
        var picked = 0
        repeat(10_000) {
            val k = rnd.nextBytes(16)
            if (HlsKeys.pick(listOf(k), iv, prefix, complete = false) != null) picked++
        }
        assertEquals(0, picked)
    }

    @Test
    fun 응답의_꼴() {
        assertEquals(HlsKeys.BodyKind.EMPTY, HlsKeys.classify(ByteArray(0)))
        assertEquals(HlsKeys.BodyKind.EMPTY, HlsKeys.classify("  ".toByteArray() + byteArrayOf(10)))
        assertEquals(HlsKeys.BodyKind.HTML, HlsKeys.classify("<html><body>403</body></html>".toByteArray()))
        assertEquals(HlsKeys.BodyKind.HTML, HlsKeys.classify(("  " + Char(0xFEFF) + "<!DOCTYPE html><p>x</p>").toByteArray()))
        assertEquals(HlsKeys.BodyKind.JSON, HlsKeys.classify("""{"code":403,"msg":"token"}""".toByteArray()))
        assertEquals(HlsKeys.BodyKind.JSON, HlsKeys.classify("[1,2]".toByteArray()))
        assertEquals(HlsKeys.BodyKind.TEXT, HlsKeys.classify("forbidden".toByteArray()))
        assertEquals(HlsKeys.BodyKind.TEXT, HlsKeys.classify("토큰이 만료되었습니다".toByteArray()))
        // 깨진 UTF-8·제어 글자 — 바이트다.
        assertEquals(HlsKeys.BodyKind.BINARY, HlsKeys.classify(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 1, 2, 3)))
        assertEquals(HlsKeys.BodyKind.BINARY, HlsKeys.classify(key + byteArrayOf(10)))
    }

    @Test
    fun 후보가_없는_응답() {
        assertTrue(HlsKeys.candidates(ByteArray(0)).isEmpty)
        assertTrue(HlsKeys.candidates(ByteArray(8) { 7 }).isEmpty)
        // 16바이트보다 짧은 글도(hex·base64 꼴이 아니면).
        assertTrue(HlsKeys.candidates("denied".toByteArray()).isEmpty)
        // 31자 hex·끝이 = 하나인 24자는 hex·base64 로 보지 않는다(앞 16바이트만 후보).
        assertEquals(1, HlsKeys.candidates(hex(key).dropLast(1).toByteArray()).lenient.size)
        assertEquals(HlsKeys.BodyKind.TEXT, HlsKeys.classify("A".repeat(23).plus("=").toByteArray()))
    }
}
