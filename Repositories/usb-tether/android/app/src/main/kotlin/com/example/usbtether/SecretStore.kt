package com.example.usbtether

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystore 로 감싼 짧은 비밀 문자열의 암·복호화.
 *
 * ## 왜 필요한가
 *
 * Wi-Fi 패스프레이즈가 SharedPreferences XML 에 평문으로 저장되어 있었다.
 * `MODE_PRIVATE` 와 `android:allowBackup="false"` 덕분에 다른 앱이나 `adb backup`
 * 으로는 읽을 수 없지만, 다음 경로가 남는다.
 *
 *  - 디버그 빌드에서 `adb shell run-as com.example.usbtether cat shared_prefs/…`
 *  - 루팅된 기기, 물리적·포렌식 접근
 *
 * 이 클래스는 키를 Android Keystore 에 두고(내보낼 수 없다) AES/GCM 으로 값만
 * 암호화한다. 위 두 경로에서 파일을 얻어도 암호문뿐이다.
 *
 * ## 막지 못하는 것
 *
 * 루팅된 기기에서 **이 앱의 UID 로 코드를 실행할 수 있는** 공격자는 Keystore 에
 * 복호화를 요청할 수 있으므로 막을 수 없다. 사용자 인증(생체·PIN)을 요구하도록
 * 하면 그것도 막을 수 있지만, 핫스팟을 켤 때마다 인증을 요구하게 되어 이 앱의
 * 용도에 맞지 않는다.
 *
 * ## 라이브러리를 쓰지 않은 이유
 *
 * `androidx.security:security-crypto` 의 `EncryptedSharedPreferences` 가 통상적인
 * 선택이지만, 짧은 문자열 두 개를 위해 유지보수 상태가 불확실한 의존성을 추가하는
 * 대신 플랫폼 API 만으로 처리했다. minSdk 26 이므로 Keystore AES 를 바로 쓸 수 있다.
 */
internal object SecretStore {

    /**
     * 평문을 암호화해 저장 가능한 문자열로 만든다.
     *
     * 형식은 `base64(iv):base64(ciphertext)` 다. GCM 의 IV 는 매번 새로 생성되며
     * Cipher 가 만든 것을 그대로 쓴다.
     *
     * @return 암호문. 실패하면 null (호출부는 평문 저장 대신 값을 버려야 한다)
     */
    fun encrypt(plaintext: String): String? = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, loadOrCreateKey())
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        "${encode(cipher.iv)}$SEPARATOR${encode(ciphertext)}"
    } catch (e: Exception) {
        Log.w(TAG, "암호화 실패: ${e.javaClass.simpleName}")
        null
    }

    /**
     * [encrypt] 가 만든 문자열을 복호화한다.
     *
     * 앱 재설치나 키 무효화로 키가 사라지면 복호화가 실패한다. 그 경우 null 을
     * 반환하고, 호출부는 값을 새로 만들면 된다(패스프레이즈는 재생성 가능하다).
     *
     * @return 평문. 형식이 맞지 않거나 복호화가 실패하면 null
     */
    fun decrypt(stored: String): String? {
        // 블록 본문이어야 한다. Kotlin 은 표현식 본문 함수 안에서 return 을 허용하지
        // 않는다(RETURN_IN_FUNCTION_WITH_EXPRESSION_BODY).
        return try {
            val parts = stored.split(SEPARATOR)
            require(parts.size == 2) { "형식 불일치" }
            val iv = decode(parts[0])
            val ciphertext = decode(parts[1])
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, loadOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "복호화 실패: ${e.javaClass.simpleName}")
            null
        }
    }

    /** Keystore 의 키를 가져오거나 없으면 만든다. 키는 기기를 떠날 수 없다. */
    private fun loadOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_TYPE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_TYPE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                // 핫스팟을 켤 때마다 생체·PIN 을 요구하게 되므로 사용자 인증은 걸지 않는다.
                .setUserAuthenticationRequired(false)
                .build()
        )
        return generator.generateKey()
    }

    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(text: String): ByteArray = Base64.decode(text, Base64.NO_WRAP)

    private const val TAG = "SecretStore"
    private const val KEYSTORE_TYPE = "AndroidKeyStore"
    private const val KEY_ALIAS = "usb_tether_secret"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val SEPARATOR = ":"
}
