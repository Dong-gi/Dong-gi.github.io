package io.github.donggi.iroiroviewer.format.archive

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipShort
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.deflate64.Deflate64CompressorInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.CRC32
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 암호가 걸린 ZIP 항목을 푼다. **commons-compress 는 이것을 하지 않는다** — 암호 항목을 열면
 * `UnsupportedZipFeatureException` 이다. 그래서 날것(`ZipFile.getRawInputStream`)을 받아
 * 우리가 풀고, 압축도 우리가 푼다.
 *
 * ## 두 방식 — 둘 다 공개돼 있다
 *
 * | | 전통 방식(ZipCrypto) | WinZip AES(AE-1·AE-2) |
 * |---|---|---|
 * | 근거 | PKWARE APPNOTE 6.1 | WinZip 공개 문서 'AES Encryption Information' |
 * | 표시 | 일반 목적 비트 0 | 압축 방식 99 + 추가 필드 `0x9901` |
 * | 열쇠 | 암호 바이트로 CRC 기반 열쇠 셋을 돌린다 | PBKDF2-HMAC-SHA1(1000회) → AES 열쇠·HMAC 열쇠·검증 2바이트 |
 * | 암호 확인 | 머리 12바이트의 마지막 바이트(1/256 로 틀린 암호가 통과한다) | 검증 2바이트(1/65536) + 끝의 HMAC 10바이트 |
 *
 * 전통 방식의 확인 바이트가 틀린 암호를 1/256 의 확률로 통과시키므로 **끝에서 CRC 를 견준다**
 * — 통과한 틀린 암호는 거기서 '깨졌다' 로 잡힌다.
 *
 * ## 암호의 바이트
 *
 * 명세는 문자 인코딩을 정하지 않는다. 도구마다 다르고, **반디집은 한글 암호를 CP949 로
 * 적는다**(2026-09-23 실측 — UTF-8 로는 안 풀리고 CP949 로 풀렸다). 그래서 UTF-8 · CP949 ·
 * ISO-8859-1 을 차례로 대 본다. 각 후보는 확인 바이트로 거르므로 값이 싸다.
 *
 * **확인 바이트는 틀린 후보도 1/256 로 통과시킨다.** 그래서 항목마다 후보를 고르면 한글 암호의
 * 300쪽짜리 만화에서 한 쪽쯤은 틀린 인코딩으로 풀려 깨진다(검토가 셈했다 — 69%). 후보는
 * **아카이브마다 한 번** 정한다 — 리더가 항목 하나를 끝까지(CRC·인증값까지) 풀어 보고 맞은 후보만
 * 쓴다(`ZipArchiveReader.keys`). 이 객체는 받은 후보를 대 볼 뿐이다.
 *
 * 후보 바이트는 `String` 을 거치지 않고 만들고(`CharsetEncoder`), 쓴 쪽이 0 으로 덮는다. 다만
 * `SecretKeySpec`·`Mac` 이 안에 드는 열쇠 사본은 우리가 덮을 길이 없다.
 *
 * ## PBKDF2 를 손으로 쓴 이유
 *
 * `SecretKeyFactory("PBKDF2WithHmacSHA1")` 은 `char[]` 를 받고, 그것을 바이트로 바꾸는 방식이
 * 제공자마다 다르다(자바 SunJCE 는 UTF-8, 안드로이드의 옛 BouncyCastle 판은 문자의 아래
 * 8비트). 우리가 바이트를 정해야 후보를 대 볼 수 있으므로 `Mac("HmacSHA1")` 로 직접 돈다.
 */
internal object ZipDecryption {

    /** 암호가 맞지 않다. 화면이 '틀렸습니다' 를 띄운다. */
    class WrongPasswordException : IOException("암호가 맞지 않다")

    /** 우리가 풀지 않는 방식(PKWARE 의 강한 암호화 등). */
    class UnsupportedEncryptionException(what: String) : IOException("풀지 않는 암호화: $what")

    private val AES_EXTRA = ZipShort(0x9901)
    private const val METHOD_AES = 99

    /** 암호 안쪽의 압축 방식 가운데 우리가 푸는 것 — 저장·deflate·deflate64·bzip2. */
    private val METHODS = setOf(0, 8, 9, 12)

    /**
     * 날것 [raw](이 항목의 압축·암호화된 바이트 전부)를 풀어 **평문**을 읽는 스트림을 준다.
     *
     * @param candidates 암호의 바이트 후보([candidates]). 받은 차례대로 대 본다. 배열은 부른 쪽의 것이다.
     * @param timeHigh 데이터 기술자(비트 3)를 쓰는 항목의 확인 바이트 — **로컬 헤더에 적힌 DOS 시각의
     *   높은 바이트.** 모르면 null(그때는 CRC 쪽만 본다).
     * @throws WrongPasswordException 어느 후보로도 확인 바이트가 맞지 않는다.
     */
    fun open(entry: ZipArchiveEntry, raw: InputStream, candidates: List<ByteArray>, timeHigh: Int? = null): InputStream {
        val bit = entry.generalPurposeBit
        if (bit.usesStrongEncryption()) throw UnsupportedEncryptionException("PKWARE 강한 암호화")
        val aes = aesInfo(entry)
        return if (aes != null) {
            val plain = aesStream(raw, candidates, aes.keyLen, entry.compressedSize)
            // AE-2 는 CRC 를 0 으로 적는다(HMAC 이 대신 지킨다). AE-1 은 CRC 를 적는다.
            checked(decompress(plain, aes.actualMethod), if (aes.vendor == 1) entry.crc else -1)
        } else {
            val plain = zipCryptoStream(raw, candidates, entry, timeHigh)
            checked(decompress(plain, entry.method), entry.crc)
        }
    }

    /**
     * 이 항목을 **어떤 암호로든** 우리가 풀 수 있는가. 거짓이면 목록이 암호를 묻지 않는다
     * (`ArchiveEntry.lockedForGood`) — 7-Zip 이 만드는 'AES + LZMA' ZIP 처럼, 맞는 암호를 넣어도
     * 안쪽을 못 푸는 항목에 암호를 물으면 화면이 '틀렸다' 를 영원히 되풀이한다(검토가 잡았다).
     */
    fun canDecrypt(entry: ZipArchiveEntry): Boolean {
        if (entry.generalPurposeBit.usesStrongEncryption()) return false
        return try {
            val aes = aesInfo(entry)
            (aes?.actualMethod ?: entry.method) in METHODS
        } catch (e: UnsupportedEncryptionException) {
            false
        }
    }

    private class AesInfo(val vendor: Int, val keyLen: Int, val actualMethod: Int)

    /** WinZip AES 항목이면 그 추가 필드를 읽는다. 전통 방식이면 null. */
    private fun aesInfo(entry: ZipArchiveEntry): AesInfo? {
        val aes = entry.getExtraField(AES_EXTRA)
        if (entry.method != METHOD_AES && aes == null) return null
        val data = aes?.localFileDataData ?: aes?.centralDirectoryData
            ?: throw UnsupportedEncryptionException("AES 추가 필드가 없다")
        if (data.size < 7) throw UnsupportedEncryptionException("AES 추가 필드가 짧다")
        val vendor = (data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8)
        val strength = data[4].toInt() and 0xFF
        val actual = (data[5].toInt() and 0xFF) or ((data[6].toInt() and 0xFF) shl 8)
        val keyLen = when (strength) {
            1 -> 16; 2 -> 24; 3 -> 32
            else -> throw UnsupportedEncryptionException("AES 세기 $strength")
        }
        return AesInfo(vendor, keyLen, actual)
    }

    // ---- 암호의 바이트 후보 --------------------------------------------------------

    /**
     * 암호를 바이트로. 겹치는 후보는 한 번만. **받은 쪽이 다 쓰고 0 으로 덮는다.**
     *
     * `String` 을 만들지 않는다 — 문자열은 불변이라 덮을 수 없고, 항목을 열 때마다 만들면 힙에
     * 암호 사본이 쌓인다(검토가 잡았다).
     */
    internal fun candidates(password: CharArray): List<ByteArray> {
        val out = ArrayList<ByteArray>(3)
        fun add(b: ByteArray?) {
            if (b == null) return
            if (out.any { it.contentEquals(b) }) b.fill(0) else out.add(b)
        }
        add(encodeStrict(password, Charsets.UTF_8))
        add(cp949()?.let { cs -> encodeStrict(password, cs) })
        add(encodeStrict(password, Charsets.ISO_8859_1))
        return out
    }

    /**
     * 안드로이드는 CP949 의 별칭을 `EUC-KR` 로 정규화한다(CLAUDE.md 실측표). 이름을 하나로
     * 박지 않고 차례로 찾는다 — `EntryNameDecoder` 와 같은 판단이다.
     */
    private fun cp949(): Charset? = listOf("x-windows-949", "MS949", "windows-949", "EUC-KR")
        .firstNotNullOfOrNull { runCatching { Charset.forName(it) }.getOrNull() }

    /** 옮길 수 없는 글자가 있으면 null — `?` 로 바꿔 대 보는 것은 다른 암호를 대 보는 일이다. */
    private fun encodeStrict(password: CharArray, cs: Charset): ByteArray? {
        val enc = cs.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val buf = try {
            enc.encode(CharBuffer.wrap(password))
        } catch (e: java.nio.charset.CharacterCodingException) {
            return null
        }
        val out = ByteArray(buf.remaining())
        buf.get(out)
        // 인코더가 만든 버퍼도 암호의 사본이다.
        if (buf.hasArray()) buf.array().fill(0)
        return out
    }

    // ---- 전통 방식 -------------------------------------------------------------------

    private class ZipCryptoKeys(password: ByteArray) {
        private var k0 = 0x12345678
        private var k1 = 0x23456789
        private var k2 = 0x34567890

        init {
            for (b in password) update(b.toInt() and 0xFF)
        }

        fun update(c: Int) {
            k0 = crc32(k0, c)
            k1 += k0 and 0xFF
            k1 = k1 * 134775813 + 1
            k2 = crc32(k2, k1 ushr 24)
        }

        fun streamByte(): Int {
            val t = (k2 or 2) and 0xFFFF
            return ((t * (t xor 1)) ushr 8) and 0xFF
        }

        fun decrypt(c: Int): Int {
            val p = (c xor streamByte()) and 0xFF
            update(p)
            return p
        }

        private fun crc32(crc: Int, b: Int): Int = (crc ushr 8) xor CRC_TABLE[(crc xor b) and 0xFF]
    }

    private val CRC_TABLE = IntArray(256) { n ->
        var c = n
        repeat(8) { c = if (c and 1 != 0) (c ushr 1) xor 0xEDB88320.toInt() else c ushr 1 }
        c
    }

    private fun zipCryptoStream(raw: InputStream, candidates: List<ByteArray>, entry: ZipArchiveEntry, timeHigh: Int?): InputStream {
        val header = ByteArray(12)
        readFully(raw, header)
        // 확인 바이트: CRC 의 가장 높은 바이트. 데이터 기술자(비트 3)를 쓰는 항목은 도구에 따라
        // **로컬 헤더에 적힌 DOS 시각**의 높은 바이트를 쓴다(Info-ZIP `zip -e`) — 둘 다 받는다.
        //
        // 시각을 `entry.time` 에서 되만들지 않는다. commons-compress 는 UT(0x5455)·NTFS 추가
        // 필드가 있으면 그 UTC 시각을 주고, 그것을 **기기의 시간대**로 DOS 시각에 되돌리면 만든
        // 사람의 시간대와 시가 어긋난다 — 맞는 암호가 '틀렸다' 가 된다(검토가 잡았다). 부른 쪽이
        // 헤더의 날것 두 바이트를 읽어 준다.
        val crcByte = ((entry.crc ushr 24) and 0xFF).toInt()
        val useTime = entry.generalPurposeBit.usesDataDescriptor() && timeHigh != null
        for (candidate in candidates) {
            val keys = ZipCryptoKeys(candidate)
            var last = 0
            for (b in header) last = keys.decrypt(b.toInt() and 0xFF)
            if (last == crcByte || (useTime && last == timeHigh)) {
                return object : FilterInputStream(raw) {
                    override fun read(): Int {
                        val c = super.read()
                        return if (c < 0) c else keys.decrypt(c)
                    }

                    override fun read(b: ByteArray, off: Int, len: Int): Int {
                        val n = super.read(b, off, len)
                        for (i in off until off + maxOf(n, 0)) b[i] = keys.decrypt(b[i].toInt() and 0xFF).toByte()
                        return n
                    }

                    override fun skip(n: Long): Long {
                        // 열쇠는 평문을 따라 돈다. 건너뛰려면 읽어야 한다.
                        val buf = ByteArray(minOf(n, 8192L).toInt().coerceAtLeast(1))
                        val got = read(buf, 0, buf.size)
                        return if (got < 0) 0 else got.toLong()
                    }

                    override fun markSupported() = false
                }
            }
        }
        throw WrongPasswordException()
    }

    // ---- WinZip AES ------------------------------------------------------------------

    private fun aesStream(raw: InputStream, candidates: List<ByteArray>, keyLen: Int, compressedSize: Long): InputStream {
        val saltLen = keyLen / 2
        val salt = ByteArray(saltLen)
        readFully(raw, salt)
        val verifier = ByteArray(2)
        readFully(raw, verifier)
        val dataLen = compressedSize - saltLen - 2 - AUTH_LEN
        if (dataLen < 0) throw IOException("AES 항목이 너무 짧다")
        for (candidate in candidates) {
            val derived = pbkdf2(candidate, salt, 1000, keyLen * 2 + 2)
            if (derived[keyLen * 2] != verifier[0] || derived[keyLen * 2 + 1] != verifier[1]) {
                // 맞지 않은 후보의 열쇠도 그 암호에서 나온 값이다.
                derived.fill(0)
                continue
            }
            val aesKey = derived.copyOfRange(0, keyLen)
            val macKey = derived.copyOfRange(keyLen, keyLen * 2)
            derived.fill(0)
            return AesCtrStream(raw, aesKey, macKey, dataLen)
        }
        throw WrongPasswordException()
    }

    /**
     * AES-CTR(WinZip 판). 계수기는 1 부터 시작하는 **리틀 엔디언** 128비트 수다(흔한 CTR 의
     * 빅 엔디언과 다르다). 암호문 위에 HMAC-SHA1 을 돌려 끝의 10바이트와 견준다 — 맞지 않으면
     * 틀린 바이트를 준 것이므로 끝에서 던진다.
     */
    private class AesCtrStream(
        private val input: InputStream,
        aesKey: ByteArray,
        macKey: ByteArray,
        private var remaining: Long,
    ) : InputStream() {
        private val ecb = Cipher.getInstance("AES/ECB/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"))
        }
        private val mac = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(macKey, "HmacSHA1")) }
        private val counter = ByteArray(16)
        private val keyStream = ByteArray(16)
        private var used = 16
        private var verified = false

        init {
            aesKey.fill(0)
            macKey.fill(0)
        }

        private fun nextBlock() {
            // 리틀 엔디언으로 1 올린다.
            var i = 0
            while (i < 16) {
                counter[i] = (counter[i] + 1).toByte()
                if (counter[i].toInt() != 0) break
                i++
            }
            ecb.doFinal(counter, 0, 16, keyStream, 0)
            used = 0
        }

        override fun read(): Int {
            val one = ByteArray(1)
            val n = read(one, 0, 1)
            return if (n <= 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (remaining <= 0) {
                verify()
                return -1
            }
            val want = minOf(len.toLong(), remaining).toInt()
            val n = input.read(b, off, want)
            if (n < 0) throw IOException("AES 항목이 중간에서 끝났다")
            mac.update(b, off, n)
            for (i in off until off + n) {
                if (used == 16) nextBlock()
                b[i] = (b[i].toInt() xor keyStream[used++].toInt()).toByte()
            }
            remaining -= n
            if (remaining <= 0) verify()
            return n
        }

        private fun verify() {
            if (verified) return
            verified = true
            val expected = ByteArray(AUTH_LEN)
            readFully(input, expected)
            val actual = mac.doFinal()
            var diff = 0
            for (i in 0 until AUTH_LEN) diff = diff or (expected[i].toInt() xor actual[i].toInt())
            if (diff != 0) throw IOException("AES 인증 값이 맞지 않다 — 파일이 손상됐다")
        }

        override fun close() = input.close()
    }

    /** PBKDF2-HMAC-SHA1. 바이트를 우리가 정한다(위 머리말). */
    internal fun pbkdf2(password: ByteArray, salt: ByteArray, iterations: Int, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA1")
        // 빈 암호도 HMAC 열쇠로는 유효하다. `SecretKeySpec` 은 빈 배열을 거절하므로 1바이트 0 으로
        // 채운다 — HMAC 은 블록보다 짧은 열쇠를 0 으로 채우므로 결과가 같다.
        mac.init(SecretKeySpec(if (password.isEmpty()) ByteArray(1) else password, "HmacSHA1"))
        val out = ByteArray(length)
        var block = 1
        var pos = 0
        while (pos < length) {
            mac.update(salt)
            mac.update(byteArrayOf((block ushr 24).toByte(), (block ushr 16).toByte(), (block ushr 8).toByte(), block.toByte()))
            var u = mac.doFinal()
            val t = u.copyOf()
            repeat(iterations - 1) {
                val next = mac.doFinal(u)
                u.fill(0)
                u = next
                for (i in t.indices) t[i] = (t[i].toInt() xor u[i].toInt()).toByte()
            }
            val n = minOf(t.size, length - pos)
            System.arraycopy(t, 0, out, pos, n)
            u.fill(0)
            t.fill(0)
            pos += n
            block++
        }
        return out
    }

    private const val AUTH_LEN = 10

    // ---- 압축 ------------------------------------------------------------------------

    private fun decompress(plain: InputStream, method: Int): InputStream = when (method) {
        0 -> plain
        8 -> {
            // **Inflater 를 직접 주면 `close` 가 그것을 끝내지 않는다**(`usesDefaultInflater` 가
            // 거짓). 끝내지 않으면 항목마다 네이티브 zlib 상태가 GC 를 기다린다 — 쪽을 넘길 때마다
            // 하나씩 쌓인다(검토가 잡았다). commons-compress 도 닫을 때 `end()` 를 부른다.
            val inflater = Inflater(true)
            object : InflaterInputStream(plain, inflater, 64 * 1024) {
                override fun close() {
                    try {
                        super.close()
                    } finally {
                        inflater.end()
                    }
                }
            }
        }
        9 -> Deflate64CompressorInputStream(plain)
        12 -> BZip2CompressorInputStream(plain)
        else -> throw UnsupportedEncryptionException("압축 방식 $method")
    }

    /** 끝에서 CRC 를 견준다. [expected] 가 음수면 견주지 않는다(AE-2). */
    private fun checked(input: InputStream, expected: Long): InputStream =
        if (expected < 0) input else object : FilterInputStream(input) {
            private val crc = CRC32()
            private var done = false

            override fun read(): Int {
                val c = super.read()
                if (c < 0) finish() else crc.update(c)
                return c
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val n = super.read(b, off, len)
                if (n < 0) finish() else crc.update(b, off, n)
                return n
            }

            override fun skip(n: Long): Long {
                val buf = ByteArray(minOf(n, 8192L).toInt().coerceAtLeast(1))
                val got = read(buf, 0, buf.size)
                return if (got < 0) 0 else got.toLong()
            }

            private fun finish() {
                if (done) return
                done = true
                if (crc.value != expected) throw IOException("CRC 가 맞지 않다 — 암호가 틀렸거나 파일이 손상됐다")
            }

            override fun markSupported() = false
        }

    private fun readFully(input: InputStream, buf: ByteArray) {
        var off = 0
        while (off < buf.size) {
            val n = input.read(buf, off, buf.size - off)
            if (n < 0) throw IOException("항목이 중간에서 끝났다")
            off += n
        }
    }
}
