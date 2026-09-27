package io.github.donggi.iroiroviewer.format.opc

import io.github.donggi.iroiroviewer.format.OpenFailure
import io.github.donggi.iroiroviewer.format.cfb.CfbEntry
import io.github.donggi.iroiroviewer.format.cfb.CfbFile
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * **Standard 암호화**(MS-OFFCRYPTO 2.3.4.5~2.3.4.9) — 오피스 2007 과, 그 방식을 흉내 내는 도구들.
 * `EncryptionInfo` 판 2.2·3.2·4.2 에 `fCryptoAPI` 와 `fAES` 가 함께 켜진 것.
 *
 * * 열쇠: SHA-1 을 소금 + 암호로 시작해 **50,000 번** 돌리고, 블록 0 을 붙여 한 번 더 한 뒤(`Hfinal`),
 *   64바이트 `0x36`·`0x5C` 판에 XOR 해 두 번 해시한 X1·X2 를 이어 열쇠 길이만큼 자른다(2.3.4.7).
 *   AES-128 은 X1 의 앞 16바이트로 끝나고, 192·256 은 X2 까지 쓴다.
 * * 암호 확인: 확인값(16바이트)과 그 SHA-1 을 각각 AES-ECB 로 풀어 견준다(2.3.4.9).
 * * 패키지: 앞 8바이트(평문 크기) 뒤를 **AES-ECB** 로 푼다. 무결성 값이 없다 — 틀린 열쇠는 확인값이,
 *   깨진 내용은 ZIP 리더의 CRC 가 잡는다.
 *
 * RC4(`fAES` 꺼짐)와 Extensible(`fExternal`)은 풀지 않는다([OpenFailure.Unsupported]).
 */
internal class StandardEncryption private constructor(
    private val keyBytes: Int,
    private val salt: ByteArray,
    private val encryptedVerifier: ByteArray,
    private val encryptedVerifierHash: ByteArray,
) : OfficeDecryptor {

    override fun keyFor(password: ByteArray, checkCancelled: () -> Unit): ByteArray? {
        val h = OfficeCrypto.iteratedHash(OfficeHash.SHA1, salt, password, SPIN_COUNT, checkCancelled)
        var hFinal: ByteArray? = null
        val buf = ByteArray(64)
        var x1: ByteArray? = null
        var x2: ByteArray? = null
        var key: ByteArray? = null
        var verifier: ByteArray? = null
        var verifierHash: ByteArray? = null
        var actual: ByteArray? = null
        try {
            hFinal = OfficeCrypto.hash(OfficeHash.SHA1, h, ByteArray(4))
            val md = OfficeHash.SHA1.digest()
            buf.fill(0x36)
            for (i in hFinal.indices) buf[i] = (buf[i].toInt() xor hFinal[i].toInt()).toByte()
            x1 = md.digest(buf)
            buf.fill(0x5C)
            for (i in hFinal.indices) buf[i] = (buf[i].toInt() xor hFinal[i].toInt()).toByte()
            x2 = md.digest(buf)
            val k = ByteArray(keyBytes)
            for (i in 0 until keyBytes) k[i] = if (i < x1.size) x1[i] else x2[i - x1.size]
            key = k
            verifier = OfficeCrypto.aesEcbDecrypt(k, encryptedVerifier)
            verifierHash = OfficeCrypto.aesEcbDecrypt(k, encryptedVerifierHash)
            actual = md.digest(verifier)
            if (!MessageDigest.isEqual(actual, verifierHash.copyOf(OfficeHash.SHA1.size))) return null
            key = null
            return k
        } finally {
            OfficeCrypto.wipe(h, hFinal, buf, x1, x2, key, verifier, verifierHash, actual)
        }
    }

    override fun decrypt(key: ByteArray, cfb: CfbFile, pkg: CfbEntry, declaredSize: Long, checkCancelled: () -> Unit): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"))
        val chunk = ByteArray(CHUNK)
        val plain = ByteArray(CHUNK)
        var out: ByteArray? = ByteArray(declaredSize.toInt())
        try {
            cfb.openStream(pkg).use { input ->
                OfficeCrypto.readFully(input, chunk, 8)
                val cipherTotal = (declaredSize + 15) / 16 * 16
                var done = 0L
                var sinceCheck = 0L
                while (done < cipherTotal) {
                    val n = minOf(CHUNK.toLong(), cipherTotal - done).toInt()
                    OfficeCrypto.readFully(input, chunk, n)
                    cipher.update(chunk, 0, n, plain, 0)
                    val take = minOf(n.toLong(), declaredSize - done).toInt()
                    plain.copyInto(out!!, done.toInt(), 0, take)
                    done += n
                    sinceCheck += n
                    if (sinceCheck >= OfficeCrypto.DECRYPT_CHECK) {
                        checkCancelled()
                        sinceCheck = 0
                    }
                }
            }
            val result = out!!
            out = null
            return result
        } finally {
            OfficeCrypto.wipe(out, chunk, plain)
        }
    }

    companion object {
        private const val SPIN_COUNT = 50_000
        private const val CHUNK = 64 * 1024

        private const val F_CRYPTO_API = 0x04
        private const val F_EXTERNAL = 0x10
        private const val F_AES = 0x20

        private const val ALG_AES128 = 0x660E
        private const val ALG_AES192 = 0x660F
        private const val ALG_AES256 = 0x6610
        private const val ALG_HASH_SHA1 = 0x8004

        private fun corrupt(): Nothing = throw OfficeCryptoFailure(OpenFailure.Corrupt(OfficeCfb.CORRUPT_INFO))

        private fun unsupported(): Nothing = throw OfficeCryptoFailure(OpenFailure.Unsupported(OfficeCfb.UNSUPPORTED_CRYPTO))

        private fun u32(b: ByteArray, o: Int): Long {
            if (o < 0 || o + 4 > b.size) corrupt()
            return ((b[o].toLong() and 0xFF) or ((b[o + 1].toLong() and 0xFF) shl 8) or
                ((b[o + 2].toLong() and 0xFF) shl 16) or ((b[o + 3].toLong() and 0xFF) shl 24))
        }

        /**
         * 판(4바이트) 뒤의 `EncryptionInfo` 를 읽는다: 깃발 · 머리 크기 · `EncryptionHeader` · `EncryptionVerifier`.
         *
         * @throws OfficeCryptoFailure 깨졌으면 Corrupt, RC4·외부(Extensible)·SHA-1 이 아닌 해시면 Unsupported.
         */
        fun parse(info: ByteArray): StandardEncryption {
            val flags = u32(info, 4).toInt()
            if (flags and F_EXTERNAL != 0) unsupported()
            if (flags and F_CRYPTO_API == 0 || flags and F_AES == 0) unsupported()
            val headerSize = u32(info, 8)
            if (headerSize < 32 || 12 + headerSize > info.size) corrupt()
            val h = 12
            val algId = u32(info, h + 8).toInt()
            val algHash = u32(info, h + 12).toInt()
            val keySize = u32(info, h + 16).toInt()
            val keyBits = when (algId) {
                ALG_AES128 -> 128
                ALG_AES192 -> 192
                ALG_AES256 -> 256
                0 -> 128 // fAES 가 켜진 채 0 이면 AES-128(명세 2.3.2)
                else -> unsupported()
            }
            if (keySize != keyBits) corrupt()
            if (algHash != 0 && algHash != ALG_HASH_SHA1) unsupported()
            val v = h + headerSize.toInt()
            if (u32(info, v) != 16L) corrupt()
            if (v + 4 + 16 + 16 + 4 + 32 > info.size) corrupt()
            val salt = info.copyOfRange(v + 4, v + 20)
            val encryptedVerifier = info.copyOfRange(v + 20, v + 36)
            if (u32(info, v + 36) != 20L) corrupt()
            val encryptedVerifierHash = info.copyOfRange(v + 40, v + 72)
            return StandardEncryption(keyBits / 8, salt, encryptedVerifier, encryptedVerifierHash)
        }
    }
}
