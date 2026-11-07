package io.github.donggi.iroirobrowser.sites

import android.content.Context
import io.github.donggi.iroirobrowser.core.adblock.Domains

/**
 * 팝업을 허용한 사이트. 사용자가 누르지 않은 새 창(쪽이 누름 뒤 한참 있다가 여는 결제 창 등)은 막고 알리는데, 그 알림에서
 * '이 사이트 허용' 을 고른 사이트는 그 뒤로 막지 않는다. **등록 도메인**(어림 — [Domains.registrable]) 단위로 기억한다 — 광고 차단을 끈
 * 사이트와 같은 단위다(`www.example.com` 에서 허용하면 `pay.example.com` 도). 설정의 '팝업을 허용한 사이트' 와 메뉴에서 푼다.
 * 앱의 사적 저장소(설정 파일 `popups`). 메인 스레드에서 쓴다.
 */
class PopupSites(context: Context) {

    private val prefs = context.getSharedPreferences("popups", Context.MODE_PRIVATE)

    @Volatile
    private var allowed: Set<String> = prefs.getStringSet(K_ALLOWED, emptySet())?.toSet() ?: emptySet()

    /** 이 호스트의 사이트에서 팝업을 허용했는가. */
    fun isAllowed(host: String): Boolean = host.isNotEmpty() && Domains.registrable(host) in allowed

    /** 허용하거나 푼다 — 등록 도메인으로. */
    fun setAllowed(host: String, allow: Boolean) {
        val site = Domains.registrable(host)
        if (site.isEmpty()) return
        allowed = if (allow) allowed + site else allowed - site
        prefs.edit().putStringSet(K_ALLOWED, allowed).apply()
    }

    /** 허용한 사이트(등록 도메인) — 이름 차례. */
    fun sites(): List<String> = allowed.sorted()

    /** 사이트 이름(등록 도메인) — 알림·메뉴에 쓴다. */
    fun siteOf(host: String): String = Domains.registrable(host)

    private companion object {
        const val K_ALLOWED = "allowed"
    }
}
