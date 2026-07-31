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
     */
    fun passphraseOrCreate(): String {
        prefs.getString(KEY_PASS, null)?.takeIf { it.isNotEmpty() }?.let { return it }
        val generated = generatePassphrase()
        prefs.edit { putString(KEY_PASS, generated) }
        return generated
    }

    /** 사용자가 입력한 값을 저장한다. */
    fun save(ssid: String, passphrase: String) {
        prefs.edit { putString(KEY_SSID, ssid).putString(KEY_PASS, passphrase) }
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
        private const val KEY_PASS = "passphrase"

        private const val DEFAULT_SSID = "USBTether"

        /** 생성할 패스프레이즈 길이. 56자 알파벳 기준 약 116비트. */
        private const val PASSPHRASE_LENGTH = 20

        /** 혼동되는 문자(0/O, 1/l/I)를 제외한 알파벳. 다른 기기에서 타이핑해야 하므로. */
        private const val PASSPHRASE_ALPHABET =
            "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    }
}
