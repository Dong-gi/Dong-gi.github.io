package io.github.donggi.iroirobrowser.browser

import io.github.donggi.iroirobrowser.R
import io.github.donggi.iroirobrowser.proxy.NetworkPath.Phase
import java.util.IllformedLocaleException
import java.util.Locale

/**
 * 막대의 프록시 상태 — 스위치 옆 글(누르면 '연결 상태'), 그 빛, 화면 낭독기가 읽는 말. 막대의 글은 들어갈 만큼 짧게 두고 뜻은 말(낭독기·연결
 * 상태 창)로 푼다. 글은 **스위치의 글이 아니다** — 스위치의 글이면 그 글을 누르는 것이 곧 프록시 끄기였다(묻지 않는 스위치라 열린 탭이 모두
 * 직접 연결로 다시 열렸다). 화면에 묶이지 않은 판정이라 기기 없이 시험한다.
 */
object ProxyStatus {

    enum class Tone { GOOD, WARN, BAD }

    /**
     * 막대의 글은 [labelRes](0 이 아니면 그 자원) 또는 [labelText] — 둘 다 비면 글을 숨긴다(꺼졌거나 프록시 주소가 없다 — 그때는 알림 띠가 말한다).
     * [spokenRes] 는 낭독기가 읽는 말이다(`%1$s` 가 있으면 출구 나라의 이름).
     */
    class Look(val labelText: String, val labelRes: Int, val tone: Tone, val spokenRes: Int) {
        val hidden: Boolean get() = labelRes == 0 && labelText.isEmpty()
    }

    fun of(phase: Phase, country: String?): Look = when (phase) {
        Phase.OFF -> Look("", 0, Tone.BAD, R.string.proxy_spoken_off)
        Phase.NOT_CONFIGURED -> Look("", 0, Tone.BAD, R.string.proxy_spoken_not_configured)
        Phase.CHECKING -> Look(CHECKING_DOTS, 0, Tone.WARN, R.string.proxy_spoken_checking)
        Phase.OK -> if (country.isNullOrBlank()) {
            Look("", R.string.state_ok, Tone.GOOD, R.string.proxy_spoken_ok_unknown)
        } else {
            Look(country, 0, Tone.GOOD, R.string.proxy_spoken_ok)
        }
        Phase.LOOKS_DIRECT -> Look("${country ?: "KR"}!", 0, Tone.BAD, R.string.proxy_spoken_looks_direct)
        Phase.UNREACHABLE -> Look("", R.string.state_unreachable, Tone.BAD, R.string.proxy_spoken_unreachable)
        Phase.AUTH_REJECTED -> Look("", R.string.state_auth, Tone.BAD, R.string.proxy_spoken_auth)
        Phase.CHECK_FAILED -> Look("", R.string.state_check_failed, Tone.WARN, R.string.proxy_spoken_check_failed)
        Phase.UNSUPPORTED -> Look("", R.string.state_unsupported, Tone.BAD, R.string.proxy_spoken_unsupported)
    }

    /**
     * 출구 나라의 코드(확인 쪽의 `loc=` — `US`)를 낭독기가 읽을 이름(`미국`)으로. 모르는 코드(Cloudflare 의 `T1`·`XX` 등)면 코드 그대로.
     * 화면의 글은 한국어뿐이라 기기의 언어가 아니라 한국어로 이름을 고른다.
     */
    fun countryName(code: String?): String {
        val c = code?.trim().orEmpty()
        if (c.isEmpty()) return ""
        return try {
            Locale.Builder().setRegion(c).build().getDisplayCountry(Locale.KOREAN).ifBlank { c }
        } catch (e: IllformedLocaleException) {
            c
        }
    }

    private const val CHECKING_DOTS = "···"
}
