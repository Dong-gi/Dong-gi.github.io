package io.github.donggi.iroirobrowser.tether.core

import java.security.SecureRandom

/**
 * 핫스팟(Wi-Fi Direct 그룹)의 SSID·패스프레이즈 규칙 — 앱의 화면·서비스·타일이 함께 쓴다(규칙이 여러 곳에 흩어지면 어긋난다).
 *
 * 그룹에 들어온 기기는 프록시와 공유 폴더에 **인증 없이** 닿는다(사용자 결정 — CLAUDE.md '테더링'). 그래서 패스프레이즈가 유일한 통제선이고,
 * 길이만이 아니라 뻔한 값도 막는다.
 */
object Passphrases {

    /** WPA2 의 패스프레이즈 길이 범위. */
    const val MIN_LENGTH = 8
    const val MAX_LENGTH = 63

    /** SSID 의 최대 바이트 — 802.11 의 SSID 요소가 32옥텟이다. 앞머리까지 친다. */
    const val MAX_SSID_BYTES = 32

    /**
     * 이 앱이 붙이는 SSID 앞머리. Wi-Fi Direct 는 `DIRECT-` 다음에 영숫자 두 글자를 요구한다. **바꾸지 않는다** — PC 의 Wi-Fi 프로필이 이
     * 이름으로 붙는다(usb-tether 때부터의 값).
     */
    const val OWN_SSID_PREFIX = "DIRECT-UT-"

    /** 칸을 비우면 쓰는 이름(앞머리는 [normalizeSsid] 가 붙인다) — usb-tether 의 기본값과 같아, 그 값을 쓰던 PC 는 그대로 붙는다. */
    const val DEFAULT_SSID_BODY = "USBTether"

    /** 만드는 패스프레이즈의 길이 — 57자 알파벳이면 20·log2(57) ≈ 116비트. */
    const val GENERATED_LENGTH = 20

    /** 헷갈리는 글자(0/O, 1/l/I)를 뺀 알파벳 — 다른 기기에서 손으로 쳐야 하므로. */
    const val ALPHABET = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    /** 규격을 채우는 앞머리: `DIRECT-` 다음에 영숫자 두 글자. */
    private val DIRECT_PREFIX = Regex("^DIRECT-[A-Za-z0-9]{2}")

    /**
     * 흔한 약한 패스프레이즈. 사전 공격을 모두 막으려는 것이 아니라 기본값을 그대로 쓰는 사고를 막으려는 것이다.
     */
    private val WEAK = setOf(
        "12345678", "123456789", "1234567890", "87654321",
        "password", "passw0rd", "p@ssword", "abcdefgh",
        "qwertyui", "asdfghjk", "iloveyou", "usbtether",
        "00000000", "11111111", "aaaaaaaa", "hotspot1",
    )

    /** 핫스팟을 켜지 못하게 하는 자격 증명의 문제(화면의 글은 앱이 고른다). */
    enum class Problem { NOT_ASCII, LENGTH, WEAK, SSID_TOO_LONG }

    /** 패스프레이즈에 쓸 수 있는 글자의 범위 — 출력 가능한 ASCII(공백 0x20 부터 물결 0x7E 까지). */
    const val FIRST_PRINTABLE = ' '
    const val LAST_PRINTABLE = '~'

    /** [ssid](사용자가 친 그대로)와 [passphrase] 로 핫스팟을 켤 수 있는가. 되면 null. */
    fun check(ssid: String, passphrase: String): Problem? {
        passphraseProblem(passphrase)?.let { return it }
        if (normalizeSsid(ssid).toByteArray(Charsets.UTF_8).size > MAX_SSID_BYTES) return Problem.SSID_TOO_LONG
        return null
    }

    /**
     * 패스프레이즈만의 문제. WPA2 의 패스프레이즈는 **출력 가능한 ASCII 8–63자**다(IEEE 802.11i) — 한글·이모지·제어 문자를 넘기면 프레임워크가
     * 두루뭉술한 오류로 실패하거나, 기기마다 다른 부호로 바꿔 PC 가 같은 글자를 쳐도 붙지 못한다. 예전에는 길이(UTF-16 글자 수)만 보았다.
     * ASCII 를 먼저 본다 — 한글 일곱 자에 '8자 이상' 이라 답하면 한 자를 더 친 뒤에야 진짜 까닭을 듣는다.
     */
    fun passphraseProblem(passphrase: String): Problem? {
        if (!passphrase.all { it in FIRST_PRINTABLE..LAST_PRINTABLE }) return Problem.NOT_ASCII
        if (passphrase.length !in MIN_LENGTH..MAX_LENGTH) return Problem.LENGTH
        if (isWeak(passphrase)) return Problem.WEAK
        return null
    }

    /**
     * 뻔히 짐작되는 패스프레이즈인가 — 알려진 약한 목록, 서로 다른 글자가 둘 이하, 전체가 오름·내림 차례(12345678·abcdefgh).
     * 저장해 둔 값이 약하면 없는 것으로 친다(앱이 새로 만든다 — usb-tether 의 옛 기본값 `12345678` 을 그대로 옮겨 오지 않으려고).
     */
    fun isWeak(value: String): Boolean {
        if (value.lowercase() in WEAK) return true
        if (value.toSet().size <= 2) return true
        return isMonotonicSequence(value)
    }

    /**
     * Wi-Fi Direct 규격에 맞는 네트워크 이름 — [DIRECT_PREFIX] 를 채우면 그대로, 아니면 [OWN_SSID_PREFIX] 를 붙인다(`MyPhone` →
     * `DIRECT-UT-MyPhone`). 규격에 못 미치는 값(`DIRECT-`·`DIRECT-a`)을 그대로 넘기면 프레임워크가 두루뭉술한 오류로만 실패한다 — 앞머리를
     * 붙이는 쪽이 안전하다(`DIRECT-a` 는 `DIRECT-UT-DIRECT-a` 가 되어 보기에 이상하지만 된다). 비어 있으면 [DEFAULT_SSID_BODY].
     */
    fun normalizeSsid(raw: String): String {
        val cleaned = raw.trim().ifEmpty { DEFAULT_SSID_BODY }
        return if (DIRECT_PREFIX.containsMatchIn(cleaned)) cleaned else "$OWN_SSID_PREFIX$cleaned"
    }

    /**
     * 새 패스프레이즈 — 기기마다 다른 [GENERATED_LENGTH] 자 난수. 상수 기본값을 두지 않는다: usb-tether 의 처음 판은 `12345678` 을 채워 두어,
     * 고치지 않고 켠 사용자는 지나가는 사람이 몇 초에 붙는 망을 띄웠다.
     */
    fun generate(random: SecureRandom = SecureRandom()): String {
        val builder = StringBuilder(GENERATED_LENGTH)
        repeat(GENERATED_LENGTH) { builder.append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        return builder.toString()
    }

    /** 이웃한 글자의 코드 차이가 모두 +1 이거나 모두 -1 인가. */
    private fun isMonotonicSequence(value: String): Boolean {
        if (value.length < 2) return false
        val step = value[1].code - value[0].code
        if (step != 1 && step != -1) return false
        return value.zipWithNext().all { (a, b) -> b.code - a.code == step }
    }
}
