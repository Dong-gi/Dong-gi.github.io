package io.github.donggi.iroirobrowser.core.media

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * HLS 의 `METHOD=AES-128` — 조각 전체를 AES-128-CBC(PKCS#7)로 잠근 것을 푼다(RFC 8216 5.2). 키는 재생목록이 가리키는 16바이트, IV 는
 * `IV=` 속성이거나 없으면 조각 번호(16바이트 빅엔디언).
 *
 * 받을 때 풀어 받는다(사용자 결정, 2026-10-05 — 앱의 `DownloadCenter`). `SAMPLE-AES` 와 DRM(Widevine·FairPlay)은 풀지 않는다.
 */
object HlsCrypto {

    fun decrypt(data: ByteArray, key: ByteArray, iv: ByteArray?, sequence: Long): ByteArray {
        require(key.size == 16) { "AES-128 키는 16바이트다" }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv ?: ivFor(sequence)))
        return cipher.doFinal(data)
    }

    /** IV 가 없을 때 — 조각 번호를 16바이트 빅엔디언으로. */
    fun ivFor(sequence: Long): ByteArray {
        val iv = ByteArray(16)
        var s = sequence
        for (i in 15 downTo 8) {
            iv[i] = (s and 0xFF).toByte()
            s = s shr 8
        }
        return iv
    }
}
