package io.github.donggi.iroirobrowser.proxy

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 프록시 비밀번호를 **기기 키**(AndroidKeyStore 의 AES-256-GCM, 밖으로 꺼낼 수 없는 키)로 잠가 설정 파일에 둔다. 백업은 이미 꺼져 있다
 * (`allowBackup=false`, 이전 규칙도 모두 뺀다) — 키가 기기를 떠나지 않으니 잠긴 값도 다른 기기에서 열리지 않는다.
 *
 * 열지 못하면(키가 지워졌다 — 앱 데이터를 지우는 등) null — 사용자가 다시 넣는다.
 */
internal object SecretBox {

    private const val ALIAS = "web-proxy-password"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128

    fun seal(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(byteArrayOf(iv.size.toByte()) + iv + sealed, Base64.NO_WRAP)
    }

    fun open(blob: String): String? = try {
        val bytes = Base64.decode(blob, Base64.NO_WRAP)
        val ivLength = bytes[0].toInt()
        val iv = bytes.copyOfRange(1, 1 + ivLength)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
        String(cipher.doFinal(bytes.copyOfRange(1 + ivLength, bytes.size)), Charsets.UTF_8)
    } catch (e: GeneralSecurityException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    } catch (e: IndexOutOfBoundsException) {
        null
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }
}
