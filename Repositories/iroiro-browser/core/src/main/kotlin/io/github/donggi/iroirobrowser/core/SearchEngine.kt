package io.github.donggi.iroirobrowser.core

import java.net.URLEncoder

/**
 * 주소창에 주소가 아닌 글을 넣었을 때 묻는 곳 — **Google 하나로 고정**(사용자 결정). 검색어 제안(글자마다 보내기)은 하지 않는다 — 치는 글자가
 * 검색 엔진으로 새지 않게.
 */
object SearchEngine {

    const val LABEL = "Google"

    private const val PREFIX = "https://www.google.com/search?q="

    // 문자 집합을 이름("UTF-8")으로 준다 — Charset 을 받는 겹은 안드로이드 13(API 33)부터다(12 에서 부르면 앱이 죽는다).
    fun searchUrl(query: String): String = PREFIX + URLEncoder.encode(query, "UTF-8")
}
