package io.github.donggi.iroirobrowser.core

import java.net.URLEncoder

/**
 * 주소창에 주소가 아닌 글을 넣었을 때 묻는 곳 — **Google 하나로 고정**(사용자 결정). 검색어 제안(글자마다 보내기)은 하지 않는다 — 치는 글자가
 * 검색 엔진으로 새지 않게.
 */
object SearchEngine {

    const val LABEL = "Google"

    private const val PREFIX = "https://www.google.com/search?q="

    fun searchUrl(query: String): String = PREFIX + URLEncoder.encode(query, Charsets.UTF_8)
}
