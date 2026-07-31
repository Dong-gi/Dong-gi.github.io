package com.example.usbtether

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.security.SecureRandom

/**
 * 핫스팟 자격증명의 영속 저장소.
 *
 * [MainActivity] 와 [HotspotStartActivity] 가 공유한다. 두 곳에서 SharedPreferences
 * 키와 패스프레이즈 생성 규칙을 각각 들고 있으면 어긋나기 쉬우므로 한곳에 모았다.
 */
internal class HotspotPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 저장된 SSID. 없으면 기본값. */
    fun ssid(): String = prefs.getString(KEY_SSID, DEFAULT_SSID) ?: DEFAULT_SSID

    /**
     * 저장된 패스프레이즈를 읽고, 없으면 새로 생성해 저장한 뒤 반환한다.
     *
     * 예전에는 `12345678` 을 기본값으로 프리필했다. 필드를 고치지 않고 핫스팟을 켠
     * 사용자는 지나가는 사람이 몇 초에 접속하는 망을 띄우게 되고, 접속에 성공하면
     * 프록시(사용자 데이터 요금)와 폰이 붙어 있는 LAN 까지 노출된다. 그래서 상수
     * 기본값을 없애고 기기마다 다른 난수를 생성한다.
     *
     * 값은 [SecretStore] 로 암호화해 저장한다. 복호화에 실패하면(앱 재설치나 키
     * 무효화) 새로 생성한다 — 패스프레이즈는 언제든 재생성 가능한 값이다.
     */
    fun passphraseOrCreate(): String {
        readPassphrase()?.let { return it }
        val generated = generatePassphrase()
        writePassphrase(generated)
        return generated
    }

    /** 사용자가 입력한 값을 저장한다. 패스프레이즈는 암호화된다. */
    fun save(ssid: String, passphrase: String) {
        prefs.edit { putString(KEY_SSID, ssid) }
        writePassphrase(passphrase)
    }

    /**
     * 저장된 패스프레이즈를 읽는다.
     *
     * 이전 버전이 평문으로 남긴 값도 읽어 들이고(마이그레이션), 즉시 암호화해
     * 다시 저장한 뒤 평문 키를 지운다.
     *
     * **취약한 값은 없는 것으로 취급한다.** 이전 버전의 기본값은 `12345678` 이었고
     * [WifiHotspot.isWeakPassphrase] 가 이제 그것을 거부한다. 그대로 옮겨오면
     * 업그레이드한 사용자는 입력란에 채워진 값으로 핫스팟을 켜려다 "추측하기 쉬운
     * 패스프레이즈" 오류만 보고, 스스로 지우고 다시 입력하지 않으면 벗어날 수 없다.
     * null 을 반환하면 호출부가 새 난수를 생성한다.
     */
    private fun readPassphrase(): String? {
        prefs.getString(KEY_PASS_ENCRYPTED, null)
            ?.let { SecretStore.decrypt(it) }
            ?.takeIf { isUsable(it) }
            ?.let { return it }

        val legacy = prefs.getString(KEY_PASS_LEGACY, null)?.takeIf { isUsable(it) }
        if (legacy != null) {
            writePassphrase(legacy)
            return legacy
        }
        return null
    }

    /** 비어 있지 않고 [WifiHotspot] 이 받아줄 값인지. */
    private fun isUsable(passphrase: String): Boolean =
        passphrase.isNotEmpty() && !WifiHotspot.isWeakPassphrase(passphrase)

    /**
     * 패스프레이즈를 암호화해 저장하고 평문 잔재를 제거한다.
     *
     * 암호화가 실패하면 평문으로 되돌리지 않고 저장을 포기한다. 저장 실패는
     * 다음 실행에서 새 패스프레이즈가 생성되는 것으로 끝나지만, 평문 저장은
     * 이 커밋이 없애려는 바로 그 문제이기 때문이다.
     */
    private fun writePassphrase(passphrase: String) {
        val sealed = SecretStore.encrypt(passphrase)
        prefs.edit {
            if (sealed != null) putString(KEY_PASS_ENCRYPTED, sealed) else remove(KEY_PASS_ENCRYPTED)
            remove(KEY_PASS_LEGACY)
        }
    }

    /**
     * WPA2 패스프레이즈를 생성한다.
     *
     * 사용자가 다른 기기에서 직접 입력해야 하므로 혼동되는 문자(0/O, 1/l/I)를 뺀
     * 알파벳을 쓴다. 56자 알파벳 × [PASSPHRASE_LENGTH]자면 약 116비트다.
     * WPA2 규격상 8–63자 출력 가능 ASCII 여야 한다.
     */
    private fun generatePassphrase(): String {
        val random = SecureRandom()
        val builder = StringBuilder(PASSPHRASE_LENGTH)
        repeat(PASSPHRASE_LENGTH) {
            builder.append(PASSPHRASE_ALPHABET[random.nextInt(PASSPHRASE_ALPHABET.length)])
        }
        return builder.toString()
    }

    companion object {
        private const val PREFS_NAME = "usb_tether"
        private const val KEY_SSID = "ssid"

        /** 암호화된 패스프레이즈. */
        private const val KEY_PASS_ENCRYPTED = "passphrase_enc"

        /** 평문으로 저장하던 예전 키. 읽어서 옮긴 뒤 지운다. */
        private const val KEY_PASS_LEGACY = "passphrase"

        private const val DEFAULT_SSID = "USBTether"

        /** 생성할 패스프레이즈 길이. 56자 알파벳 기준 약 116비트. */
        private const val PASSPHRASE_LENGTH = 20

        /** 혼동되는 문자(0/O, 1/l/I)를 제외한 알파벳. 다른 기기에서 타이핑해야 하므로. */
        private const val PASSPHRASE_ALPHABET =
            "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    }
}
