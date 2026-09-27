package io.github.donggi.iroiroviewer.format.opc

import io.github.donggi.iroiroviewer.format.OpenFailure
import io.github.donggi.iroiroviewer.format.cfb.CfbEntry
import io.github.donggi.iroiroviewer.format.cfb.CfbFile
import io.github.donggi.iroiroviewer.safety.ParseLimits
import io.github.donggi.iroiroviewer.safety.SafeXml
import io.github.donggi.iroiroviewer.safety.nextGuarded
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * **Agile 암호화**(MS-OFFCRYPTO 2.3.4.10~2.3.4.15) — 오피스 2010 이후의 기본값. `EncryptionInfo` 판 4.4.
 *
 * ## 구조
 *
 * `EncryptionInfo` 의 앞 8바이트 뒤가 XML 설명자다. 그 안에:
 *
 * * `keyData` — 패키지를 푸는 **비밀 열쇠**의 방식(AES 세기·해시·소금). 비밀 열쇠 자체는 적혀 있지 않다.
 * * `keyEncryptor`(암호) — 암호로 만든 중간 열쇠 셋. 하나는 확인값(verifier)을, 하나는 그 해시를,
 *   하나는 **비밀 열쇠**를 감싼다. 인증서로 감싼 것(`keyEncryptor/certificate`)은 암호로 풀 수 없다.
 * * `dataIntegrity` — 패키지 스트림 **전체**(앞 8바이트 포함)의 HMAC. 비밀 열쇠로 감싸 있다.
 *
 * 패키지는 4096 바이트 조각마다 IV 를 새로 만든다: `H(keyData 소금 + LE32(조각 번호))` 를 블록 크기로 맞춘 것.
 *
 * ## 방어
 *
 * * 설명자는 64 KiB 까지(부르는 쪽이 자른다). XML 은 `SafeXml` 파서로, 깊이 상한을 걸어 읽는다.
 * * `spinCount` 는 명세의 상한 **1,000만**까지만 받는다. 넘으면 풀지 않는다([OpenFailure.Unsupported]) —
 *   받으면 암호 한 번 확인에 몇 분이 걸린다.
 * * 모든 길이(소금·열쇠·블록·해시·감싼 값)를 방식과 견준다. AES 의 블록은 16, 열쇠는 128·192·256비트,
 *   `hashSize` 는 해시의 실제 크기와 같아야 한다.
 * * HMAC 이 맞지 않으면 **평문을 지우고** [OpenFailure.Corrupt] 다. 암호가 맞았는데 내용이 바뀐 것이다.
 *   `dataIntegrity` 가 없는 설명자도 깨진 것으로 본다 — 오피스·LibreOffice 는 암호 방식에서 늘 쓰고,
 *   그것이 없으면 바뀐 암호문을 알아챌 길이 없다.
 */
internal class AgileEncryption private constructor(
    private val keyData: Params,
    private val encryptedHmacKey: ByteArray,
    private val encryptedHmacValue: ByteArray,
    private val passwordKey: Params,
    private val spinCount: Int,
    private val encryptedVerifierHashInput: ByteArray,
    private val encryptedVerifierHashValue: ByteArray,
    private val encryptedKeyValue: ByteArray,
) : OfficeDecryptor {

    /** `keyData`·`p:encryptedKey` 가 함께 갖는 방식 칸. */
    private class Params(val hash: OfficeHash, val keyBits: Int, val blockSize: Int, val salt: ByteArray) {
        val keyBytes: Int get() = keyBits / 8
    }

    override fun keyFor(password: ByteArray, checkCancelled: () -> Unit): ByteArray? {
        val p = passwordKey
        val h = OfficeCrypto.iteratedHash(p.hash, p.salt, password, spinCount, checkCancelled)
        var k1: ByteArray? = null
        var k2: ByteArray? = null
        var k3: ByteArray? = null
        var input: ByteArray? = null
        var value: ByteArray? = null
        var actual: ByteArray? = null
        var unwrapped: ByteArray? = null
        val iv = OfficeCrypto.fit(p.salt, p.blockSize)
        try {
            k1 = derive(h, BLOCK_VERIFIER_INPUT)
            k2 = derive(h, BLOCK_VERIFIER_VALUE)
            input = OfficeCrypto.aesCbcDecrypt(k1, iv, encryptedVerifierHashInput)
            value = OfficeCrypto.aesCbcDecrypt(k2, iv, encryptedVerifierHashValue)
            // 확인값은 소금 크기만큼, 그 해시는 해시 크기만큼이 뜻이 있다 — 나머지는 블록을 채운 자리다.
            val md = p.hash.digest()
            md.update(input, 0, p.salt.size)
            actual = md.digest()
            if (!MessageDigest.isEqual(actual, value.copyOf(p.hash.size))) return null
            k3 = derive(h, BLOCK_KEY_VALUE)
            unwrapped = OfficeCrypto.aesCbcDecrypt(k3, iv, encryptedKeyValue)
            return unwrapped.copyOf(keyData.keyBytes)
        } finally {
            OfficeCrypto.wipe(h, k1, k2, k3, input, value, actual, unwrapped, iv)
        }
    }

    /** 중간 열쇠 — H(Hn + 블록 열쇠) 를 열쇠 길이로 맞춘다(모자라면 0x36). */
    private fun derive(h: ByteArray, blockKey: ByteArray): ByteArray {
        val full = OfficeCrypto.hash(passwordKey.hash, h, blockKey)
        return try {
            OfficeCrypto.fit(full, passwordKey.keyBytes)
        } finally {
            full.fill(0)
        }
    }

    /** keyData 쪽 IV — H(keyData 소금 + 블록 열쇠) 를 블록 크기로 맞춘다. */
    private fun keyDataIv(blockKey: ByteArray): ByteArray {
        val full = OfficeCrypto.hash(keyData.hash, keyData.salt, blockKey)
        return OfficeCrypto.fit(full, keyData.blockSize).also { full.fill(0) }
    }

    override fun decrypt(key: ByteArray, cfb: CfbFile, pkg: CfbEntry, declaredSize: Long, checkCancelled: () -> Unit): ByteArray {
        val size = keyData.hash.size
        var hmacKey: ByteArray? = null
        var expected: ByteArray? = null
        var actual: ByteArray? = null
        val segment = ByteArray(SEGMENT)
        val plain = ByteArray(SEGMENT)
        val ivSource = ByteArray(keyData.salt.size + 4)
        var out: ByteArray? = ByteArray(declaredSize.toInt())
        try {
            val iv1 = keyDataIv(BLOCK_INTEGRITY_KEY)
            val iv2 = keyDataIv(BLOCK_INTEGRITY_VALUE)
            val hk = OfficeCrypto.aesCbcDecrypt(key, iv1, encryptedHmacKey)
            val hv = OfficeCrypto.aesCbcDecrypt(key, iv2, encryptedHmacValue)
            hmacKey = hk.copyOf(size)
            expected = hv.copyOf(size)
            OfficeCrypto.wipe(hk, hv, iv1, iv2)

            val mac = Mac.getInstance(keyData.hash.hmac)
            mac.init(SecretKeySpec(hmacKey, keyData.hash.hmac))
            val cipher = Cipher.getInstance("AES/CBC/NoPadding")
            val keySpec = SecretKeySpec(key, "AES")
            keyData.salt.copyInto(ivSource)
            val md = keyData.hash.digest()

            cfb.openStream(pkg).use { input ->
                // HMAC 은 스트림 **전체**다 — 앞 8바이트(평문 크기)와 끝의 남는 바이트까지.
                OfficeCrypto.readFully(input, segment, 8)
                mac.update(segment, 0, 8)
                val cipherTotal = roundUp(declaredSize, BLOCK)
                var done = 0L
                var produced = 0L
                var index = 0
                var sinceCheck = 0L
                while (done < cipherTotal) {
                    val n = minOf(SEGMENT.toLong(), cipherTotal - done).toInt()
                    OfficeCrypto.readFully(input, segment, n)
                    mac.update(segment, 0, n)
                    ivSource[keyData.salt.size] = index.toByte()
                    ivSource[keyData.salt.size + 1] = (index shr 8).toByte()
                    ivSource[keyData.salt.size + 2] = (index shr 16).toByte()
                    ivSource[keyData.salt.size + 3] = (index shr 24).toByte()
                    val full = md.digest(ivSource)
                    val iv = OfficeCrypto.fit(full, keyData.blockSize)
                    cipher.init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(iv))
                    cipher.doFinal(segment, 0, n, plain, 0)
                    OfficeCrypto.wipe(full, iv)
                    val take = minOf(n.toLong(), declaredSize - produced).toInt()
                    plain.copyInto(out!!, produced.toInt(), 0, take)
                    produced += take
                    done += n
                    index++
                    sinceCheck += n
                    if (sinceCheck >= OfficeCrypto.DECRYPT_CHECK) {
                        checkCancelled()
                        sinceCheck = 0
                    }
                }
                while (true) {
                    val n = input.read(segment)
                    if (n < 0) break
                    mac.update(segment, 0, n)
                    sinceCheck += n
                    if (sinceCheck >= OfficeCrypto.DECRYPT_CHECK) {
                        checkCancelled()
                        sinceCheck = 0
                    }
                }
            }
            actual = mac.doFinal()
            if (!MessageDigest.isEqual(actual, expected)) {
                throw OfficeCryptoFailure(OpenFailure.Corrupt(OfficeCfb.INTEGRITY))
            }
            val result = out!!
            out = null
            return result
        } finally {
            // 성공하면 [out] 은 null 이다(주인이 부른 쪽으로 바뀌었다). 실패한 평문은 지운다.
            OfficeCrypto.wipe(out, hmacKey, expected, actual, segment, plain, ivSource)
        }
    }

    companion object {
        private const val NS_ENCRYPTION = "http://schemas.microsoft.com/office/2006/encryption"
        private const val NS_PASSWORD = "http://schemas.microsoft.com/office/2006/keyEncryptor/password"
        private const val NS_CERTIFICATE = "http://schemas.microsoft.com/office/2006/keyEncryptor/certificate"

        /** 명세(2.3.4.11)의 상한. 넘으면 암호 확인 한 번이 몇 분이다. */
        const val MAX_SPIN_COUNT = 10_000_000

        private const val SEGMENT = 4096
        private const val BLOCK = 16

        private val BLOCK_VERIFIER_INPUT = bytes(0xfe, 0xa7, 0xd2, 0x76, 0x3b, 0x4b, 0x9e, 0x79)
        private val BLOCK_VERIFIER_VALUE = bytes(0xd7, 0xaa, 0x0f, 0x6d, 0x30, 0x61, 0x34, 0x4e)
        private val BLOCK_KEY_VALUE = bytes(0x14, 0x6e, 0x0b, 0xe7, 0xab, 0xac, 0xd0, 0xd6)
        private val BLOCK_INTEGRITY_KEY = bytes(0x5f, 0xb2, 0xad, 0x01, 0x0c, 0xb9, 0xe1, 0xf6)
        private val BLOCK_INTEGRITY_VALUE = bytes(0xa0, 0x67, 0x7f, 0x02, 0xb2, 0x2c, 0x84, 0x33)

        private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

        private fun roundUp(n: Long, block: Int): Long = (n + block - 1) / block * block

        private fun corrupt(): Nothing = throw OfficeCryptoFailure(OpenFailure.Corrupt(OfficeCfb.CORRUPT_INFO))

        private fun unsupported(): Nothing = throw OfficeCryptoFailure(OpenFailure.Unsupported(OfficeCfb.UNSUPPORTED_CRYPTO))

        /**
         * `EncryptionInfo` 의 앞 8바이트 뒤(XML 설명자)를 읽는다.
         *
         * @throws OfficeCryptoFailure 깨졌으면 Corrupt, 풀지 않는 방식이면 Unsupported, 인증서로만 감쌌으면 Encrypted.
         */
        fun parse(xml: ByteArray, offset: Int, limits: ParseLimits): AgileEncryption {
            try {
                return parseXml(xml, offset, limits)
            } catch (e: XmlPullParserException) {
                corrupt()
            }
        }

        private class Seen {
            var keyData: Params? = null
            var hmacKey: ByteArray? = null
            var hmacValue: ByteArray? = null
            var password: Params? = null
            var spin = 0
            var verifierInput: ByteArray? = null
            var verifierValue: ByteArray? = null
            var keyValue: ByteArray? = null
            var certificate = false
        }

        private fun parseXml(xml: ByteArray, offset: Int, limits: ParseLimits): AgileEncryption {
            val p = SafeXml.newParser(ByteArrayInputStream(xml, offset, xml.size - offset), null, limits)
            val seen = Seen()
            var event = p.eventType
            var rootSeen = false
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    val ns = p.namespace
                    val name = p.name
                    if (!rootSeen) {
                        if (ns != NS_ENCRYPTION || name != "encryption") corrupt()
                        rootSeen = true
                    } else {
                        when {
                            ns == NS_ENCRYPTION && name == "keyData" && seen.keyData == null ->
                                seen.keyData = params(p)
                            ns == NS_ENCRYPTION && name == "dataIntegrity" && seen.hmacKey == null -> {
                                seen.hmacKey = base64(p, "encryptedHmacKey")
                                seen.hmacValue = base64(p, "encryptedHmacValue")
                            }
                            ns == NS_PASSWORD && name == "encryptedKey" && seen.password == null -> {
                                val spin = attr(p, "spinCount")?.trim()?.toLongOrNull() ?: corrupt()
                                if (spin < 0) corrupt()
                                if (spin > MAX_SPIN_COUNT) unsupported()
                                seen.spin = spin.toInt()
                                seen.password = params(p)
                                seen.verifierInput = base64(p, "encryptedVerifierHashInput")
                                seen.verifierValue = base64(p, "encryptedVerifierHashValue")
                                seen.keyValue = base64(p, "encryptedKeyValue")
                            }
                            ns == NS_CERTIFICATE && name == "encryptedKey" -> seen.certificate = true
                        }
                    }
                } else if (event == XmlPullParser.END_TAG && p.depth == 1) {
                    // 뿌리가 닫혔다. 그 뒤(채움 바이트 등)는 읽지 않는다.
                    break
                }
                event = p.nextGuarded(limits)
            }
            if (!rootSeen) corrupt()
            val password = seen.password
            if (password == null) {
                // 인증서로만 감쌌다 — 암호를 물어도 소용이 없다.
                if (seen.certificate) throw OfficeCryptoFailure(OpenFailure.Encrypted(OfficeCfb.ENCRYPTED))
                corrupt()
            }
            val keyData = seen.keyData ?: corrupt()
            val hmacKey = seen.hmacKey ?: corrupt()
            val hmacValue = seen.hmacValue ?: corrupt()
            val input = seen.verifierInput!!
            val value = seen.verifierValue!!
            val keyValue = seen.keyValue!!
            // 감싼 값은 블록 배수이고, 풀었을 때 쓸 만큼은 있어야 한다.
            fun wrapped(b: ByteArray, blockSize: Int, atLeast: Int) {
                if (b.isEmpty() || b.size % blockSize != 0 || b.size < atLeast) corrupt()
            }
            wrapped(input, password.blockSize, password.salt.size)
            wrapped(value, password.blockSize, password.hash.size)
            wrapped(keyValue, password.blockSize, keyData.keyBytes)
            wrapped(hmacKey, keyData.blockSize, keyData.hash.size)
            wrapped(hmacValue, keyData.blockSize, keyData.hash.size)
            return AgileEncryption(keyData, hmacKey, hmacValue, password, seen.spin, input, value, keyValue)
        }

        /** 방식 칸을 읽고 방식과 견준다. */
        private fun params(p: XmlPullParser): Params {
            if (attr(p, "cipherAlgorithm") != "AES") unsupported()
            if (attr(p, "cipherChaining") != "ChainingModeCBC") unsupported()
            val hash = OfficeHash.of(attr(p, "hashAlgorithm") ?: corrupt()) ?: unsupported()
            val keyBits = int(p, "keyBits")
            val blockSize = int(p, "blockSize")
            val hashSize = int(p, "hashSize")
            val saltSize = int(p, "saltSize")
            if (keyBits != 128 && keyBits != 192 && keyBits != 256) corrupt()
            if (blockSize != BLOCK) corrupt()
            if (hashSize != hash.size) corrupt()
            val salt = base64(p, "saltValue")
            if (saltSize < 1 || salt.size != saltSize) corrupt()
            return Params(hash, keyBits, blockSize, salt)
        }

        private fun attr(p: XmlPullParser, name: String): String? {
            for (i in 0 until p.attributeCount) {
                if (p.getAttributeName(i) == name && p.getAttributeNamespace(i).isNullOrEmpty()) return p.getAttributeValue(i)
            }
            return null
        }

        private fun int(p: XmlPullParser, name: String): Int = attr(p, name)?.trim()?.toIntOrNull() ?: corrupt()

        /** base64 값. 공백은 걷어 내고 엄격하게 푼다 — 틀린 글자가 있으면 깨진 것이다. */
        private fun base64(p: XmlPullParser, name: String): ByteArray {
            val v = attr(p, name) ?: corrupt()
            val clean = v.filterNot { it == ' ' || it == '\n' || it == '\r' || it == '\t' }
            return try {
                Base64.getDecoder().decode(clean)
            } catch (e: IllegalArgumentException) {
                corrupt()
            }
        }
    }
}
