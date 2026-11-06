package io.github.donggi.iroiroviewer.io

import android.annotation.SuppressLint
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * WinZip AES 로 잠그는 데 드는 셈 — 암호의 바이트, 열쇠 유도(PBKDF2-HMAC-SHA1), CTR 암호기와 인증값. 쓰는 것은 [AesZipWriter] 다.
 *
 * ## 형식은 WinZip 의 공개 문서 'AES Encryption Information' 이 정한다
 *
 * | | |
 * |---|---|
 * | 열쇠 | PBKDF2-HMAC-SHA1, 1000회, 항목마다 새 솔트 16바이트 → AES 열쇠 32 + HMAC 열쇠 32 + 확인값 2 |
 * | 데이터 | 솔트 ‖ 확인값 ‖ AES-256-CTR(압축한 바이트) ‖ HMAC-SHA1(암호문)의 앞 10바이트 |
 * | 계수기 | **1 부터 시작하는 리틀 엔디언** 정수(Brian Gladman 의 구현 — 앞 8바이트만 올라가고 뒤 8바이트는 0) |
 *
 * **자바의 `AES/CTR/NoPadding` 을 쓰지 않는다.** 그쪽 계수기는 빅 엔디언으로 블록 전체를 올린다 — 첫 블록(IV 를 리틀 엔디언 1 로 주면)만
 * 같고 둘째 블록부터 다르다. 16바이트를 넘는 항목은 다른 도구에서 전부 깨진다. 그래서 계수기 블록을 우리가 늘어놓고
 * `AES/ECB/NoPadding` 으로 한꺼번에 암호화해 열쇠 흐름을 만든다([AesCtrHmac]) — 한 블록씩 부르면 4 GiB 에 약 2억 7천만 번이다.
 *
 * ## 읽는 쪽과 두 벌이다
 *
 * 푸는 쪽은 `format:archive` 의 `ZipDecryption` 이다. `core:io` 는 그 모듈을 볼 수 없고 그쪽도 이쪽을 볼 수 없다(의존 표). 두 벌이 같은
 * 형식을 말하는지는 셋이 지킨다 — 우리 리더가 읽는다(`feature:archive` 의 `AesZipRoundTripTest`), 독립 구현(JCE 의 PBKDF2·`BigInteger`
 * 계수기)이 읽는다(`AesZipWriterTest`), pyzipper 가 읽는다(CLAUDE.md '14단계 뒤 — AES 암호 ZIP').
 *
 * ## 암호의 바이트는 UTF-8
 *
 * 명세는 문자 인코딩을 정하지 않는다. 7-Zip 이 AES ZIP 의 암호를 UTF-8 로 넣는다고 알려져 있어(확인하지 않았다 — 이 기계에 7-Zip 이 없다) 우리도
 * UTF-8 이다. 영문·숫자·기호(ASCII)는 어느 도구에서나 같은 바이트지만 그 밖의 글자는 도구마다 다르다 — **반디집 7.45 는 AES 에서도 한글 암호를
 * CP949 로 넣는다**(2026-10-16 실측: pyzipper 가 CP949 바이트로 잠근 것은 `bz t -p:비밀번호` 가 열고, UTF-8 로 잠근 것과 우리 것은 열지 못했다).
 * 화면이 그때 경고한다([isAscii]). 이 앱의 리더는 UTF-8·CP949·ISO-8859-1 을 차례로 대 보므로 어느 쪽이든 연다. `String` 을 거치지 않고 옮기고
 * 쓴 사본은 덮는다(문자열은 덮을 수 없다).
 */
object AesZipCrypto {

    /** 압축 방식 칸의 값 — 'AES 로 잠겼다'. 실제 압축 방식은 추가 필드에 적는다. */
    const val METHOD_AES = 99

    /** 추가 필드의 머리 번호. */
    const val EXTRA_ID = 0x9901

    /** 판(vendor version) — AE-2. CRC 칸을 0 으로 둔다(아래 [AesZipWriter] 머리말). */
    const val VENDOR_AE2 = 2

    /** 세기 3 = AES-256. */
    const val STRENGTH_256 = 3

    const val KEY_LEN = 32
    const val SALT_LEN = 16
    const val VERIFIER_LEN = 2
    const val AUTH_LEN = 10
    const val ITERATIONS = 1000

    /** 항목의 데이터에 암호화가 더하는 바이트 — 솔트·확인값·인증값. */
    const val OVERHEAD = SALT_LEN + VERIFIER_LEN + AUTH_LEN

    /**
     * 영문·숫자·기호만으로 된 암호인가. 아니면 다른 프로그램이 다른 바이트로 옮겨 열지 못할 수 있다 — 화면이 경고한다.
     * 칸의 글자를 그대로 본다(사본을 만들지 않는다).
     */
    fun isAscii(text: CharSequence): Boolean {
        for (i in 0 until text.length) if (text[i].code >= 0x80) return false
        return true
    }

    /**
     * UTF-8 로 옮길 수 있는가 — 짝 없는 서로게이트가 없는가. 화면(확인 단추)과 [AesZipKey.of] 가 같은 판정을 쓴다 — 화면이 받은 암호를
     * 엔진이 거절하는 일이 없게.
     */
    fun isEncodable(text: CharSequence): Boolean {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= text.length || !Character.isLowSurrogate(text[i + 1])) return false
                i += 2
                continue
            }
            if (Character.isLowSurrogate(c)) return false
            i++
        }
        return true
    }

    /** 암호를 UTF-8 바이트로. 옮길 수 없으면 null. **받은 쪽이 다 쓰고 0 으로 덮는다.** */
    internal fun utf8(password: CharArray): ByteArray? {
        val encoder = Charsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val buf = try {
            encoder.encode(CharBuffer.wrap(password))
        } catch (e: CharacterCodingException) {
            return null
        }
        val out = ByteArray(buf.remaining())
        buf.get(out)
        // 인코더가 만든 버퍼도 암호의 사본이다.
        if (buf.hasArray()) buf.array().fill(0)
        return out
    }

    /**
     * PBKDF2(RFC 8018) — [prf] 는 암호로 열쇠를 건 HMAC-SHA1 이다. 항목마다 솔트만 바꿔 부르므로 암호의 사본(JCE 가 안에 든다)은 작업 내내
     * 하나뿐이다. 중간값은 덮는다.
     */
    internal fun pbkdf2(prf: Mac, salt: ByteArray, iterations: Int, length: Int): ByteArray {
        val hLen = prf.macLength
        val out = ByteArray(length)
        val u = ByteArray(hLen)
        val t = ByteArray(hLen)
        val index = ByteArray(4)
        var block = 1
        var pos = 0
        try {
            while (pos < length) {
                index[0] = (block ushr 24).toByte()
                index[1] = (block ushr 16).toByte()
                index[2] = (block ushr 8).toByte()
                index[3] = block.toByte()
                prf.update(salt)
                prf.update(index)
                prf.doFinal(u, 0)
                System.arraycopy(u, 0, t, 0, hLen)
                for (round in 1 until iterations) {
                    // `update` 가 입력을 다 받은 뒤에 같은 배열에 결과를 쓴다.
                    prf.update(u)
                    prf.doFinal(u, 0)
                    for (i in 0 until hLen) t[i] = (t[i].toInt() xor u[i].toInt()).toByte()
                }
                val n = minOf(hLen, length - pos)
                System.arraycopy(t, 0, out, pos, n)
                pos += n
                block++
            }
        } finally {
            u.fill(0)
            t.fill(0)
        }
        return out
    }

    /** 이 계수기 블록 번호(1 부터)의 바이트 — 앞 8바이트에 리틀 엔디언, 뒤 8바이트는 0. */
    internal fun counterBlock(number: Long, into: ByteArray, at: Int) {
        for (j in 0 until 8) into[at + j] = (number ushr (8 * j)).toByte()
        for (j in 8 until 16) into[at + j] = 0
    }
}

/**
 * 암호에서 만든 열쇠의 바탕 — 암호로 열쇠를 건 HMAC-SHA1 하나. 항목마다 솔트를 바꿔 [derive] 한다.
 *
 * 암호 배열은 받지도 들지도 않는다 — [of] 가 UTF-8 로 옮겨 HMAC 에 넣고 그 사본을 곧바로 덮는다. JCE 의 `SecretKeySpec`·`Mac` 이 안에
 * 드는 사본은 우리가 덮을 길이 없다(CLAUDE.md '암호가 걸린 파일' — 완전히 지우지는 못한다).
 */
class AesZipKey private constructor(private val prf: Mac) {

    /** 솔트 하나에서 AES 열쇠 32 · HMAC 열쇠 32 · 확인값 2 를 차례로 낸다. **받은 쪽이 다 쓰고 덮는다.** */
    internal fun derive(salt: ByteArray): ByteArray =
        AesZipCrypto.pbkdf2(prf, salt, AesZipCrypto.ITERATIONS, AesZipCrypto.KEY_LEN * 2 + AesZipCrypto.VERIFIER_LEN)

    companion object {
        /**
         * 암호를 열쇠의 바탕으로. 비었거나 UTF-8 로 옮길 수 없으면 null([AesZipCrypto.isEncodable]).
         * **받은 배열은 건드리지 않는다** — 지우는 것은 넘긴 쪽이다.
         */
        fun of(password: CharArray): AesZipKey? {
            if (password.isEmpty()) return null
            val bytes = AesZipCrypto.utf8(password) ?: return null
            try {
                val mac = Mac.getInstance("HmacSHA1")
                mac.init(SecretKeySpec(bytes, "HmacSHA1"))
                return AesZipKey(mac)
            } finally {
                bytes.fill(0)
            }
        }
    }
}

/**
 * 한 항목의 AES-256-CTR(WinZip 판) 암호기와 HMAC-SHA1 인증값. 항목마다 [start] 로 새 열쇠를 건다 — 같은 열쇠로 계수기를 1 부터 다시
 * 쓰면 두 항목의 열쇠 흐름이 같아져 평문의 XOR 이 드러난다. 그래서 열쇠는 솔트와 함께 항목마다 새로 유도한다([AesZipWriter.beginFile]).
 */
internal class AesCtrHmac {
    // lint 의 `GetInstance`('ECB 를 쓰지 마라')는 데이터를 ECB 로 암호화하는 것을 막는 규칙이다. 여기서 ECB 로 암호화하는 것은 계수기
    // 블록뿐이고 그 결과가 열쇠 흐름이다 — 그것이 CTR 이다(자바의 CTR 은 계수기의 엔디언이 달라 쓸 수 없다 — 머리말).
    @SuppressLint("GetInstance")
    private val ecb: Cipher = Cipher.getInstance("AES/ECB/NoPadding")
    private val mac: Mac = Mac.getInstance("HmacSHA1")

    /** 지금까지 쓴 블록 수. 다음 블록의 계수기는 이것 + 1 이다. */
    private var blocks = 0L
    private val counters = ByteArray(CHUNK)
    private val keyStream = ByteArray(CHUNK)
    private var have = 0
    private var used = 0

    /** 새 항목의 열쇠를 건다. 열쇠 배열은 건 뒤에 덮는다(부른 쪽의 것이다 — 여기서 덮는다). */
    fun start(aesKey: ByteArray, macKey: ByteArray) {
        try {
            ecb.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"))
            mac.init(SecretKeySpec(macKey, "HmacSHA1"))
        } finally {
            aesKey.fill(0)
            macKey.fill(0)
        }
        blocks = 0
        have = 0
        used = 0
    }

    /** [b] 의 [off] 부터 [len] 바이트를 **제자리에서** 암호화하고 그 암호문을 인증값에 넣는다. */
    fun encrypt(b: ByteArray, off: Int, len: Int) {
        var i = off
        val end = off + len
        while (i < end) {
            if (used == have) refill(end - i)
            val n = minOf(end - i, have - used)
            for (k in 0 until n) b[i + k] = (b[i + k].toInt() xor keyStream[used + k].toInt()).toByte()
            i += n
            used += n
        }
        // 인증값은 암호문 위에서 잰다(명세 — 솔트·확인값은 들지 않는다).
        mac.update(b, off, len)
    }

    /** 인증값(HMAC-SHA1 의 앞 10바이트). 항목의 끝에 한 번 부른다. */
    fun authCode(): ByteArray {
        val full = mac.doFinal()
        return full.copyOf(AesZipCrypto.AUTH_LEN)
    }

    /** 남은 열쇠 흐름을 덮는다(항목이 끝났거나 버려졌다). */
    fun forget() {
        keyStream.fill(0)
        have = 0
        used = 0
    }

    /** 앞으로 [wanted] 바이트를 덮을 만큼(한 번에 [CHUNK] 까지) 계수기를 늘어놓고 한꺼번에 암호화한다. */
    private fun refill(wanted: Int) {
        val count = ((wanted + 15) / 16).coerceIn(1, CHUNK / 16)
        for (k in 0 until count) {
            blocks++
            AesZipCrypto.counterBlock(blocks, counters, k * 16)
        }
        val n = ecb.doFinal(counters, 0, count * 16, keyStream, 0)
        check(n == count * 16) { "AES 블록이 모자란다" }
        have = n
        used = 0
    }

    private companion object {
        /** 열쇠 흐름을 한 번에 만드는 양 — 64 KiB(블록 4,096개). */
        const val CHUNK = 64 * 1024
    }
}
