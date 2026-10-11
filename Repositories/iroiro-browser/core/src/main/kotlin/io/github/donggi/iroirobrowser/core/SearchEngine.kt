package io.github.donggi.iroirobrowser.core

import java.net.URLEncoder

/** 주소창에 주소가 아닌 글을 넣었을 때 묻는 곳. 검색어 제안(글자마다 보내기)은 하지 않는다 — 치는 글자가 검색 엔진으로 새지 않게. */
enum class SearchEngine(val id: String, val label: String, private val prefix: String) {
    GOOGLE("google", "Google", "https://www.google.com/search?q="),
    DUCKDUCKGO("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q="),
    BING("bing", "Bing", "https://www.bing.com/search?q="),
    NAVER("naver", "네이버", "https://search.naver.com/search.naver?query="),
    ;

    // 문자 집합을 이름("UTF-8")으로 준다 — Charset 을 받는 겹은 안드로이드 13(API 33)부터다(12 에서 검색하면 앱이 죽었다).
    fun searchUrl(query: String): String = prefix + URLEncoder.encode(query, "UTF-8")

    companion object {
        val DEFAULT = GOOGLE

        fun byId(id: String?): SearchEngine = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
