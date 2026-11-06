package io.github.donggi.iroirobrowser.core.media

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.security.GeneralSecurityException
import java.util.Base64
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * HLS AES-128 의 열쇠 응답 읽기와 진단(2026-10-16 — m3u8 열쇠 오류 고침). RFC 8216 5.1 은 열쇠 파일을 16바이트로 정하고 앞 판도 정확히 16바이트만
 * 받았다. 쪽의 재생기(hls.js)는 더 너그럽다 — 16바이트보다 긴 응답은 앞 16바이트로 푼다(WebCrypto 가 거절하면 소프트웨어 AES 로 가고, 그것은 늘 앞
 * 네 낱말만 읽는다 — hls.js 의 `aes-decryptor.ts`, 소스로 읽었다). 사이트의 로더가 hex·base64 글로 온 열쇠를 풀어 넘기는 일도 있다.
 *
 * 그래서 16바이트가 아니면 후보를 짓고([candidates]), **첫 조각을 풀어 HLS 조각의 꼴이 맞을 때만** 그 후보를 쓴다([pick]) — 틀린 열쇠로 쓰레기 파일을
 * 만들지 않는다. 하나도 맞지 않으면 응답의 꼴([classify])을 실패 글의 갈래로 싣는다([Report] — 주소·쿠키·토큰·몸은 싣지 않는다).
 */
object HlsKeys {

    const val KEY_BYTES = 16

    /** 첫 조각에서 꼴을 볼 만큼 — ADTS 의 다음 프레임(8 KB 아래)까지 닿게. */
    const val PROBE_BYTES = 16 * 1024

    /** 열쇠 응답의 꼴 — 실패 글의 갈래. */
    enum class BodyKind { EMPTY, HTML, JSON, HEX, BASE64, TEXT, BINARY, TOO_LARGE }

    /** 열쇠 후보 — [exact] 는 16바이트 응답(확인 없이 쓴다 — 앞 판과 같다), [lenient] 는 첫 조각으로 확인해야 쓰는 것. */
    class Candidates internal constructor(val exact: ByteArray?, val lenient: List<ByteArray>) {
        val isEmpty: Boolean get() = exact == null && lenient.isEmpty()
    }

    /**
     * 실패 글의 갈래 — 응답의 꼴과 길이, 넘겨주기 수, 몇 번째 열쇠인가(재생목록의 서로 다른 열쇠 주소 가운데 — [ordinal]/[count]), 쪽과 다른
     * 사이트인가, 쪽의 요청을 따라 실었는가([replay] — null 이면 못 찾았다). 주소·쿠키·토큰·몸은 담지 않는다 — 이 글은 목록 파일에 남는다.
     */
    class Report(
        val kind: BodyKind,
        val bytes: Long,
        val redirects: Int,
        val ordinal: Int,
        val count: Int,
        val crossSite: Boolean,
        val replay: PageRequests.Match?,
    )

    /** 후보 — 16바이트면 그것 하나, 아니면 32자 hex·24자 base64 글을 푼 것과(hls.js 처럼) 16바이트보다 길면 앞 16바이트. */
    fun candidates(body: ByteArray): Candidates {
        if (body.size == KEY_BYTES) return Candidates(body.copyOf(), emptyList())
        val out = ArrayList<ByteArray>()
        textOf(body)?.let { text ->
            hexKey(text)?.let { out += it }
            base64Key(text)?.let { out += it }
        }
        if (body.size > KEY_BYTES) out += body.copyOf(KEY_BYTES)
        val distinct = ArrayList<ByteArray>()
        for (c in out) if (distinct.none { it.contentEquals(c) }) distinct += c
        return Candidates(null, distinct)
    }

    /** 응답의 꼴 — 비었다·웹 페이지·JSON·hex 글·base64 글·그 밖의 글·바이트. 64 KB 를 넘은 것은 부르는 쪽이 [BodyKind.TOO_LARGE] 로 적는다. */
    fun classify(body: ByteArray): BodyKind {
        if (body.isEmpty()) return BodyKind.EMPTY
        val text = textOf(body) ?: return BodyKind.BINARY
        return when {
            text.isEmpty() -> BodyKind.EMPTY
            looksLikeMarkup(text) -> BodyKind.HTML
            text.startsWith("{") || text.startsWith("[") -> BodyKind.JSON
            hexKey(text) != null -> BodyKind.HEX
            base64Key(text) != null -> BodyKind.BASE64
            else -> BodyKind.TEXT
        }
    }

    /**
     * 첫 조각의 앞머리([encryptedPrefix] — 잠긴 그대로)를 후보마다 풀어 HLS 조각의 꼴([looksLikeMedia])이면 그 후보. 없으면 null.
     * [complete] 면 앞머리가 조각 전체다 — PKCS#7 채움까지 맞아야 한다. [iv] 는 그 조각의 IV(없으면 부르는 쪽이 조각 번호로 짓는다).
     */
    fun pick(candidates: List<ByteArray>, iv: ByteArray, encryptedPrefix: ByteArray, complete: Boolean): ByteArray? =
        candidates.firstOrNull { it.size == KEY_BYTES && opensAsMedia(it, iv, encryptedPrefix, complete) }

    /**
     * 풀린 앞머리가 HLS 조각의 꼴인가 — MPEG-TS(188 바이트마다 0x47, 셋 이상), ISO BMFF(fMP4 의 초기 조각·조각 — 아는 상자 이름), ID3(묶음 소리 —
     * RFC 8216 3.4 는 조각마다 ID3 PRIV 시각을 앞에 두게 한다), ADTS(프레임 둘). 틀린 열쇠로 푼 바이트는 고르게 흩어져 이 가운데 어느 것도 우연히
     * 맞을 확률이 10억 분의 몇이다.
     */
    fun looksLikeMedia(plain: ByteArray, length: Int = plain.size): Boolean {
        val n = minOf(length, plain.size)
        return isTransportStream(plain, n) || isBox(plain, n) || isId3(plain, n) || isAdts(plain, n)
    }

    private const val BLOCK = 16
    private const val TS_PACKET = 188
    private const val TS_SYNC = 0x47

    /** 아는 상자 — 초기 조각(`ftyp`·`moov`)과 조각(`styp`·`sidx`·`moof`·`emsg`·`prft`)의 첫 상자, 그리고 흔한 채움 상자. */
    private val BOXES = setOf("ftyp", "styp", "sidx", "moof", "moov", "mdat", "free", "skip", "emsg", "prft", "uuid", "ssix", "pdin")

    private fun opensAsMedia(keyBytes: ByteArray, ivBytes: ByteArray, enc: ByteArray, complete: Boolean): Boolean {
        val whole = enc.size / BLOCK * BLOCK
        if (whole < BLOCK) return false
        if (complete && whole != enc.size) return false
        val plain = try {
            val cipher = Cipher.getInstance("AES/CBC/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(ivBytes))
            cipher.doFinal(enc, 0, whole)
        } catch (e: GeneralSecurityException) {
            return false
        }
        var length = plain.size
        if (complete) {
            val pad = u8(plain, length - 1)
            if (pad < 1 || pad > BLOCK) return false
            for (k in 1..pad) if (u8(plain, length - k) != pad) return false
            length -= pad
        }
        return looksLikeMedia(plain, length)
    }

    /** MPEG-TS — 188 바이트마다 동기 바이트. 앞의 묶음 셋 이상(있는 만큼 여덟까지)이 모두 맞아야 한다(한 바이트는 256 번에 한 번 우연히 맞는다). */
    private fun isTransportStream(p: ByteArray, n: Int): Boolean {
        val starts = minOf(8, (n + TS_PACKET - 1) / TS_PACKET)
        if (starts < 3) return false
        for (k in 0 until starts) if (u8(p, k * TS_PACKET) != TS_SYNC) return false
        return true
    }

    /** ISO BMFF — 첫 상자의 이름이 아는 것이고 크기가 말이 된다(1 은 64비트 크기, 0 은 끝까지 — `mdat` 만). */
    private fun isBox(p: ByteArray, n: Int): Boolean {
        if (n < 8) return false
        val size = (u8(p, 0).toLong() shl 24) or (u8(p, 1).toLong() shl 16) or (u8(p, 2).toLong() shl 8) or u8(p, 3).toLong()
        val type = String(p, 4, 4, Charsets.ISO_8859_1)
        if (type !in BOXES) return false
        return size == 1L || size >= 8 || (size == 0L && type == "mdat")
    }

    /** ID3v2 머리 — `ID3`, 판 2~4, 크기 네 바이트는 7비트씩. */
    private fun isId3(p: ByteArray, n: Int): Boolean =
        n >= 10 && u8(p, 0) == 'I'.code && u8(p, 1) == 'D'.code && u8(p, 2) == '3'.code && u8(p, 3) in 2..4 && u8(p, 4) != 0xFF &&
            (6..9).all { u8(p, it) < 0x80 }

    /** ADTS — 첫 프레임과 **그다음 프레임**의 머리(동기 열두 비트 하나로는 우연히 맞는다). */
    private fun isAdts(p: ByteArray, n: Int): Boolean {
        val first = adtsFrame(p, 0, n)
        return first > 0 && adtsFrame(p, first, n) > 0
    }

    /** [at] 의 ADTS 머리 — 동기·층 0·표본율 번호 13 밑·길이 7 이상이면 그 프레임의 길이, 아니면 -1. */
    private fun adtsFrame(p: ByteArray, at: Int, n: Int): Int {
        if (at + 7 > n) return -1
        if (u8(p, at) != 0xFF || (u8(p, at + 1) and 0xF6) != 0xF0) return -1
        if (((u8(p, at + 2) shr 2) and 0x0F) >= 13) return -1
        val length = ((u8(p, at + 3) and 0x03) shl 11) or (u8(p, at + 4) shl 3) or (u8(p, at + 5) shr 5)
        return if (length >= 7) length else -1
    }

    private fun u8(p: ByteArray, i: Int): Int = p[i].toInt() and 0xFF

    /** 몸이 글인가 — UTF-8 로 엄격히 읽히고 제어 글자(탭·줄바꿈 밖)가 없으면 앞뒤 빈칸·BOM 을 뗀 글, 아니면 null. */
    private fun textOf(body: ByteArray): String? {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = try {
            decoder.decode(ByteBuffer.wrap(body)).toString()
        } catch (e: CharacterCodingException) {
            return null
        }
        if (text.any { (it.code < 0x20 && it.code != 9 && it.code != 10 && it.code != 13) || it.code == 0x7F }) return null
        return text.trim { it.isWhitespace() || it.code == BOM }
    }

    private const val BOM = 0xFEFF

    private fun looksLikeMarkup(text: String): Boolean {
        if (text.startsWith("<")) return true
        val head = text.take(1024).lowercase(Locale.ROOT)
        return head.contains("<html") || head.contains("<body") || head.contains("<!doctype")
    }

    /** 32자 hex(앞의 `0x` 는 떼고) — 16바이트. ASCII 의 숫자·글자만(유니코드의 숫자는 받지 않는다). */
    private fun hexKey(text: String): ByteArray? {
        val h = if (text.startsWith("0x") || text.startsWith("0X")) text.substring(2) else text
        if (h.length != 2 * KEY_BYTES || !h.all { isHex(it) }) return null
        return ByteArray(KEY_BYTES) { k -> ((hexValue(h[2 * k]) shl 4) or hexValue(h[2 * k + 1])).toByte() }
    }

    private fun isHex(c: Char) = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

    private fun hexValue(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        else -> c - 'A' + 10
    }

    /** 24자 base64(끝이 `==` — 16바이트를 담은 꼴, URL 판도). */
    private fun base64Key(text: String): ByteArray? {
        if (text.length != 24 || !text.endsWith("==")) return null
        val decoder = if (text.any { it == '-' || it == '_' }) Base64.getUrlDecoder() else Base64.getDecoder()
        val bytes = try {
            decoder.decode(text)
        } catch (e: IllegalArgumentException) {
            return null
        }
        return bytes.takeIf { it.size == KEY_BYTES }
    }
}
