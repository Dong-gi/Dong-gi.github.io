package io.github.donggi.iroirobrowser.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 짧은 글을 **기기 키**(AndroidKeyStore 의 AES-256-GCM, 밖으로 꺼낼 수 없는 키 — 별칭 [alias] 마다 따로, 처음 쓸 때 만든다)로 잠그고 연다.
 * 프록시 비밀번호(proxy/SecretBox)와 핫스팟 패스프레이즈(tether/TetherSecret)가 별칭을 달리해 함께 쓴다. 꼴은 Base64(IV 길이 1바이트 + IV +
 * 암호문) — 별칭과 이 꼴을 바꾸면 저장해 둔 값을 열지 못한다.
 *
 * **실패를 잡지 않는다** — 무엇을 잡아 null 로 할지는 부르는 쪽이 정한다(브라우저와 테더의 약속이 다르다). 테더 프로세스에서도 도므로 브라우저의
 * 것(WebApp·WebView)을 부르지 않는다(TetherIsolationTest).
 */
internal class DeviceKeyCipher(private val alias: String) {

    fun seal(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(byteArrayOf(iv.size.toByte()) + iv + sealed, Base64.NO_WRAP)
    }

    fun open(blob: String): String {
        val bytes = Base64.decode(blob, Base64.NO_WRAP)
        val ivLength = bytes[0].toInt()
        val iv = bytes.copyOfRange(1, 1 + ivLength)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
        return String(cipher.doFinal(bytes.copyOfRange(1 + ivLength, bytes.size)), Charsets.UTF_8)
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
    }
}
