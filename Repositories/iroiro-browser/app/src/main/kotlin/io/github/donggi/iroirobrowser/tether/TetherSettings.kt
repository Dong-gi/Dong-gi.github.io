package io.github.donggi.iroirobrowser.tether

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import io.github.donggi.iroirobrowser.tether.core.PassphraseVault
import io.github.donggi.iroirobrowser.tether.core.Passphrases
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 테더의 설정 파일 하나(SharedPreferences [PREFS] — 브라우저의 `web`·`adblock`·`sites`·`popups` 와 겹치지 않는다. 테더 프로세스만 쓰고
 * 브라우저 프로세스는 열지 않는다 — SharedPreferences 는 여러 프로세스가 함께 쓰는 것을 지키지 않는다). SSID, 잠근 패스프레이즈, 공유 폴더의
 * 트리 URI. 백업·기기 이전에서 빠진다(`allowBackup=false`, `data_extraction_rules` 가 sharedpref 를 통째로 뺀다).
 *
 * 곧바로 쓴다(commit) — 핫스팟을 켜자마자 앱이 죽어도 바꾼 값이 남게(브라우저의 설정과 같은 까닭).
 *
 * 패스프레이즈는 [PassphraseVault] 가 쥔다 — 프로세스에 하나([vault])라 화면과 서비스가 **같은 값**을 쓴다. 기기 키로 잠그지 못해도 이
 * 프로세스에서는 화면에 넣은 값으로 그룹을 만들고, 잠근 값을 열지 못하면 새 난수로 덮어쓰지 않고 까닭을 알린다.
 */
internal class TetherSettings(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val vault = vault(prefs)

    /** 사용자가 넣은 SSID(앞머리 없이 친 그대로). 없거나 비었으면 [Passphrases.DEFAULT_SSID_BODY]. 실제 이름은 [Passphrases.normalizeSsid]. */
    fun ssid(): String = prefs.getString(K_SSID, null)?.trim()?.takeIf { it.isNotEmpty() } ?: Passphrases.DEFAULT_SSID_BODY

    /**
     * 쓸 패스프레이즈 — 이 프로세스에서 마지막으로 정하거나 연 값, 없으면 저장값, 그것도 없으면 새로 만들어 저장한 값. 저장값이 있는데 열지
     * 못하면 [PassphraseVault.Current.Unreadable] — 덮어쓰지 않는다. **약한 저장값은 없는 것으로 친다**(그대로 쓰면 켜려 할 때마다 '뻔한
     * 패스프레이즈' 로 막힌다).
     */
    fun passphrase(): PassphraseVault.Current = vault.current()

    /**
     * 빈 SSID 는 저장하지 않는다 — 저장하면 타일의 길이 앱을 열어 다시 넣을 때까지 빈 이름으로 실패한다(usb-tether 의 결함). 패스프레이즈를
     * 기기에 잠가 두었는가를 돌려준다(false 여도 이 프로세스는 이 값을 쓴다 — 화면이 '저장하지 못했다' 를 알린다).
     */
    @SuppressLint("ApplySharedPref") // 일부러 commit — 머리말
    fun save(ssid: String, passphrase: String): Boolean {
        val trimmed = ssid.trim()
        if (trimmed.isNotEmpty()) prefs.edit().putString(K_SSID, trimmed).commit()
        return vault.set(passphrase).saved
    }

    /** 공유 폴더의 트리 URI(`ACTION_OPEN_DOCUMENT_TREE` 의 결과). 고르지 않았으면 null. */
    var treeUri: Uri?
        get() = prefs.getString(K_TREE, null)?.let(Uri::parse)
        @SuppressLint("ApplySharedPref") // 일부러 commit — 머리말
        set(value) {
            prefs.edit().apply { if (value == null) remove(K_TREE) else putString(K_TREE, value.toString()) }.commit()
        }

    /** 잠근 블롭은 이 설정 파일에, 자물쇠는 기기 키([TetherSecret]). 잠그지 못하면 평문으로 두지 않는다(usb-tether 가 없앤 바로 그것이다). */
    private class PrefsStore(private val prefs: SharedPreferences) : PassphraseVault.Store {
        override fun readSealed(): String? = prefs.getString(K_PASSPHRASE, null)

        @SuppressLint("ApplySharedPref") // 일부러 commit — 머리말
        override fun writeSealed(sealed: String): Boolean = prefs.edit().putString(K_PASSPHRASE, sealed).commit()

        override fun seal(plain: String): String? = TetherSecret.seal(plain)

        override fun open(sealed: String): String? = TetherSecret.open(sealed)
    }

    private companion object {
        const val PREFS = "tether"
        const val K_SSID = "ssid"
        const val K_PASSPHRASE = "passphrase_sealed"
        const val K_TREE = "tree_uri"

        /** 프로세스에 하나 — 화면과 서비스가 따로 지은 TetherSettings 도 이것을 함께 쓴다. */
        @Volatile
        private var shared: PassphraseVault? = null

        fun vault(prefs: SharedPreferences): PassphraseVault =
            shared ?: synchronized(this) { shared ?: PassphraseVault(PrefsStore(prefs)).also { shared = it } }
    }
}

/**
 * 핫스팟 패스프레이즈를 **기기 키**(AndroidKeyStore 의 AES-256-GCM, 밖으로 꺼낼 수 없는 키)로 잠근다. 별칭 [ALIAS] 는 브라우저의 프록시
 * 비밀번호 키(`web-proxy-password` — proxy/SecretBox)와 겹치지 않는다 — 키를 함께 쓰지 않는다. 같은 앱(UID)이라 그 키도 이 프로세스가 쓸 수
 * 있는 것은 바뀌지 않는다(프로세스를 나눠도 경계는 UID 다). 사용자 인증(지문·PIN)은 걸지 않는다 — 타일로 켤 때마다 묻게 된다.
 *
 * 꼴은 브라우저의 SecretBox 와 같다 — Base64(IV 길이 1바이트 + IV + 암호문).
 */
internal object TetherSecret {

    private const val ALIAS = "tether-hotspot-passphrase"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128

    /**
     * 잠근 글. 잠그지 못하면 null(키를 만들지 못하는 기기 등). 키 저장소는 `GeneralSecurityException` 밖의 것(`IOException`·`ProviderException`)도
     * 던진다 — 모두 잡는다: 테더 프로세스가 죽으면 PC 의 인터넷이 끊긴다.
     */
    fun seal(plain: String): String? = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(byteArrayOf(iv.size.toByte()) + iv + sealed, Base64.NO_WRAP)
    } catch (e: Exception) {
        null
    }

    /** 연 글. 열지 못하면(키가 사라졌다·꼴이 틀렸다) null. */
    fun open(blob: String): String? = try {
        val bytes = Base64.decode(blob, Base64.NO_WRAP)
        val ivLength = bytes[0].toInt()
        val iv = bytes.copyOfRange(1, 1 + ivLength)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
        String(cipher.doFinal(bytes.copyOfRange(1 + ivLength, bytes.size)), Charsets.UTF_8)
    } catch (e: Exception) {
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
