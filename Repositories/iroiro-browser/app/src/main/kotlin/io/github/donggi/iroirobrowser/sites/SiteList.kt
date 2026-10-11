package io.github.donggi.iroirobrowser.sites

import android.content.Context
import android.net.Uri
import java.util.Locale

/**
 * 이 브라우저로 연 사이트(주 문서의 호스트)와 마지막으로 연 때. 설정의 '사이트별 데이터' 가 데이터가 있을 만한 곳을 아는 길이다 — WebView
 * 에는 쿠키가 있는 곳을 늘어놓는 API 가 없다(저장소는 `WebStorage.getOrigins` 가 알려 준다). 서드파티 쿠키는 막혀 있어 쿠키는 연 사이트에만
 * 생긴다. 앱의 사적 저장소(설정 파일 `sites`)에 두고 최근 [MAX] 곳까지 둔다. 메인 스레드에서 쓴다.
 */
class SiteList(context: Context) {

    private val prefs = context.getSharedPreferences("sites", Context.MODE_PRIVATE)

    /** 주 문서를 열었다 — http(s) 만 적는다. */
    fun record(url: String) {
        val uri = Uri.parse(url)
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") return
        val host = uri.host?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return
        prefs.edit().putLong(host, System.currentTimeMillis()).apply()
        if (prefs.all.size > MAX) trim()
    }

    /** 호스트 → 마지막으로 연 때(밀리초). */
    fun all(): Map<String, Long> = prefs.all.mapNotNull { (k, v) -> (v as? Long)?.let { k to it } }.toMap()

    /**
     * [site] 를 지웠다 — WebView 가 지운 사이트(`deleteBrowsingDataForSite` 가 돌려준 등록 도메인, 예: `example.com`)와 그 아래 이름
     * (`www.example.com`·`m.example.com`)을 목록에서 뺀다.
     */
    fun removeSite(site: String) {
        val s = site.lowercase(Locale.ROOT).trimEnd('.')
        val gone = prefs.all.keys.filter { it == s || it.endsWith(".$s") }
        if (gone.isEmpty()) return
        prefs.edit().apply { gone.forEach { remove(it) } }.apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun trim() {
        val oldest = all().entries.sortedBy { it.value }.take(prefs.all.size - MAX).map { it.key }
        prefs.edit().apply { oldest.forEach { remove(it) } }.apply()
    }

    private companion object {
        const val MAX = 1000
    }
}
