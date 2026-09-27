package io.github.donggi.iroiroviewer.format.opc

import io.github.donggi.iroiroviewer.format.OpenFailure
import io.github.donggi.iroiroviewer.format.cfb.CfbEntry
import io.github.donggi.iroiroviewer.format.cfb.CfbFile
import java.io.InputStream
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 여는 도중에 '이 결과로 끝낸다' 를 알리는 것. [OfficeCfb.open] 이 받아 그대로 돌려준다.
 *
 * **메시지도 스택도 없다** — [failure] 의 `detail` 은 우리가 쓴 고정 문장뿐이고, 파일에서 읽은 값은
 * 싣지 않는다(CLAUDE.md '예외 메시지를 화면에 그대로 보내지 않는다').
 */
internal class OfficeCryptoFailure(val failure: OpenFailure) : Exception(null, null, false, false)

/**
 * 암호 방식 하나(Agile·Standard). 암호에서 열쇠를 얻고, 그 열쇠로 `EncryptedPackage` 를 푼다.
 *
 * 둘을 가른 것은 **틀린 암호와 깨진 파일을 가르기** 위해서다 — [keyFor] 가 null 이면 암호가 틀렸고,
 * [decrypt] 가 던지면 파일이 깨졌다(암호는 이미 맞았다).
 */
internal interface OfficeDecryptor {

    /**
     * 암호([password] 는 UTF-16LE 바이트)가 맞으면 패키지를 풀 열쇠, 틀리면 null.
     * **돌려받은 열쇠는 부른 쪽이 지운다.** [password] 는 부른 쪽의 것이다 — 쓰기만 한다.
     */
    fun keyFor(password: ByteArray, checkCancelled: () -> Unit): ByteArray?

    /**
     * 열쇠로 패키지를 푼다. 결과는 정확히 [declaredSize] 바이트다(앞 8바이트에 적힌 평문 크기 — 부른 쪽이
     * 스트림 길이·상한과 견준 뒤다). 실패는 [OfficeCryptoFailure].
     */
    fun decrypt(key: ByteArray, cfb: CfbFile, pkg: CfbEntry, declaredSize: Long, checkCancelled: () -> Unit): ByteArray
}

/** MS-OFFCRYPTO 가 받는 해시. 이름은 Agile 설명자의 `hashAlgorithm` 값이다. */
internal enum class OfficeHash(val jce: String, val hmac: String, val size: Int) {
    SHA1("SHA-1", "HmacSHA1", 20),
    SHA256("SHA-256", "HmacSHA256", 32),
    SHA384("SHA-384", "HmacSHA384", 48),
    SHA512("SHA-512", "HmacSHA512", 64),
    ;

    fun digest(): MessageDigest = MessageDigest.getInstance(jce)

    companion object {
        /** `SHA1`·`SHA-1` 을 같게 본다(명세는 앞의 것, 몇몇 구현이 뒤의 것을 쓴다). 모르면 null. */
        fun of(name: String): OfficeHash? = when (name.replace("-", "").uppercase()) {
            "SHA1" -> SHA1
            "SHA256" -> SHA256
            "SHA384" -> SHA384
            "SHA512" -> SHA512
            else -> null
        }
    }
}

/**
 * 두 방식이 함께 쓰는 계산. 원칙 둘:
 *
 * * **암호를 `String` 으로 만들지 않는다** — 불변이라 지울 수 없다. `CharArray` 에서 UTF-16LE 바이트를
 *   직접 만들고([utf16le]) 쓴 쪽이 0 으로 덮는다.
 * * **우리가 만든 중간값(해시·열쇠·IV·복호 버퍼)은 쓰고 나면 덮는다.** JCE 의 `SecretKeySpec`·`Cipher`·
 *   `Mac` 이 안에 드는 사본은 덮을 길이 없다(CLAUDE.md '완전히 지우지는 못한다').
 */
internal object OfficeCrypto {

    /** 명세가 암호 문자를 넣는 방식 — UTF-16LE, BOM 없음, 끝 NUL 없음. **받은 쪽이 지운다.** */
    fun utf16le(password: CharArray): ByteArray {
        val out = ByteArray(password.size * 2)
        for ((i, c) in password.withIndex()) {
            out[i * 2] = c.code.toByte()
            out[i * 2 + 1] = (c.code shr 8).toByte()
        }
        return out
    }

    /**
     * H0 = H(salt + 암호), Hn = H(LE32(n-1) + Hn-1) 을 [spinCount] 번(MS-OFFCRYPTO 2.3.4.7·2.3.4.11).
     * 만 번마다 [checkCancelled] 를 부른다 — 1,000만 회까지 받으므로 폰에서 수십 초가 걸릴 수 있다.
     * **돌려받은 해시는 부른 쪽이 지운다.**
     */
    fun iteratedHash(hash: OfficeHash, salt: ByteArray, password: ByteArray, spinCount: Int, checkCancelled: () -> Unit): ByteArray {
        val h = ByteArray(hash.size)
        iteratedHashInto(h, hash, salt, password, spinCount, checkCancelled)
        return h
    }

    /**
     * [iteratedHash] 의 몸통 — 결과를 [h] 에 쓴다. **취소든 무엇이든 던지고 끝나면 [h] 를 덮고 던진다.**
     * 중간값 Hn 도 암호에서 유도한 값이다(CLAUDE.md '암호를 다루는 규칙' 의 PBKDF2 중간값과 같다). 예전에는
     * 끝까지 돈 길에서만 부른 쪽이 덮었고, 사용자가 열기를 취소하면 반쯤 돈 Hn 이 버려진 배열에 남았다
     * (검토가 잡았다). `MessageDigest` 는 `digest` 마다 스스로 초기화되므로 [checkCancelled] 가 도는
     * 자리에서는 안에 남은 상태가 없다. 시험이 [h] 를 넘겨 이것을 본다.
     */
    internal fun iteratedHashInto(h: ByteArray, hash: OfficeHash, salt: ByteArray, password: ByteArray, spinCount: Int, checkCancelled: () -> Unit) {
        require(h.size == hash.size) { "해시 크기와 다른 버퍼" }
        try {
            val md = hash.digest()
            md.update(salt)
            md.update(password)
            md.digest(h, 0, h.size)
            val counter = ByteArray(4)
            for (i in 0 until spinCount) {
                if (i % SPIN_CHECK == 0) checkCancelled()
                counter[0] = i.toByte()
                counter[1] = (i shr 8).toByte()
                counter[2] = (i shr 16).toByte()
                counter[3] = (i shr 24).toByte()
                md.update(counter)
                md.update(h)
                md.digest(h, 0, h.size)
            }
        } catch (t: Throwable) {
            h.fill(0)
            throw t
        }
    }

    /** H(a + b). */
    fun hash(hash: OfficeHash, a: ByteArray, b: ByteArray): ByteArray {
        val md = hash.digest()
        md.update(a)
        md.update(b)
        return md.digest()
    }

    /**
     * 길이를 [n] 으로 맞춘다 — 길면 자르고, 짧으면 `0x36` 으로 채운다(MS-OFFCRYPTO 2.3.4.11 의 열쇠,
     * 2.3.4.12 의 IV 가 같은 규칙이다). **[b] 는 지우지 않는다.**
     */
    fun fit(b: ByteArray, n: Int): ByteArray {
        if (b.size >= n) return b.copyOf(n)
        val out = ByteArray(n) { 0x36 }
        b.copyInto(out)
        return out
    }

    /** AES-CBC 복호(덧붙임 없음). [data] 는 블록 배수여야 한다. */
    fun aesCbcDecrypt(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(data)
    }

    /** AES-ECB 복호(덧붙임 없음). */
    fun aesEcbDecrypt(key: ByteArray, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"))
        return cipher.doFinal(data)
    }

    /** 여럿을 한 번에 덮는다. */
    fun wipe(vararg arrays: ByteArray?) {
        for (a in arrays) a?.fill(0)
    }

    /** [n] 바이트를 꼭 채워 읽는다. 스트림이 먼저 끝나면 [OfficeCryptoFailure](잘린 패키지). */
    fun readFully(input: InputStream, b: ByteArray, n: Int) {
        var read = 0
        while (read < n) {
            val r = input.read(b, read, n - read)
            if (r < 0) throw OfficeCryptoFailure(OpenFailure.Corrupt(OfficeCfb.TRUNCATED))
            read += r
        }
    }

    const val SPIN_CHECK = 10_000

    /** 푸는 동안 이만큼마다 취소를 본다. */
    const val DECRYPT_CHECK = 1L shl 20
}
