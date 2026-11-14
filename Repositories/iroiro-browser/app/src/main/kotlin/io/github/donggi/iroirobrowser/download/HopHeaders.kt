package io.github.donggi.iroirobrowser.download

import android.webkit.CookieManager
import io.github.donggi.iroirobrowser.core.Referrer

/**
 * 앱의 HTTP 가 쪽처럼 보이도록 **홉마다** 싣는 머리 — 내려받기([DownloadCenter])와 찾은 미디어의 미리 읽기
 * ([io.github.donggi.iroirobrowser.browser.MediaProbe])가 함께 쓴다. WebView 와 같은 User-Agent, 이 홉의 Referer, 그 주소의 쿠키(넘겨주기의
 * 홉마다 다시 고른다 — 넘겨주기가 준 쿠키는 [storeCookies] 로 WebView 의 저장소에).
 *
 * Referer 를 짓는 길: 쪽에서 낸 요청은 [fromPage](홉마다 쪽에서 다시 — '내려받기' 의 'Referer 를 홉마다'), 사이트의 규약이 처음 홉의 값을
 * 정한 요청(갤러리)과 열쇠의 '쪽이 보낸 머리 따라 하기'는 부르는 쪽이 홉마다 지은 값을 [of] 에 넘긴다.
 */
internal object HopHeaders {

    /** 머리 — 이 홉 [url] 의 Referer 는 [referer](부르는 쪽이 홉마다 지은 것, null 이면 싣지 않는다), [extra] 는 맨 나중에 덮는다. */
    fun of(userAgent: String, referer: String?, url: String, extra: Map<String, String> = emptyMap()): Map<String, String> =
        buildMap {
            put("User-Agent", userAgent)
            put("Accept", "*/*")
            referer?.let { put("Referer", it) }
            CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotEmpty() }?.let { put("Cookie", it) }
            putAll(extra)
        }

    /**
     * 쪽 [page] 에서 낸 요청의 홉 [hop] 머리 — Referer 를 **홉마다** 쪽에서 다시 짓는다(`strict-origin-when-cross-origin`). 처음 주소에 맞춰
     * 한 번 지은 값을 모든 홉(넘겨주기·HLS·DASH 의 조각·재생목록·열쇠)에 실으면, 처음 주소가 쪽과 같은 출처일 때 쪽의 전체 주소(질의 포함)가
     * 다른 출처의 서버에 간다.
     */
    fun fromPage(userAgent: String, page: String?, hop: String, extra: Map<String, String> = emptyMap()): Map<String, String> =
        of(userAgent, Referrer.strictOriginWhenCrossOrigin(page, hop), hop, extra)

    /** 응답(넘겨주기 포함)이 준 쿠키를 WebView 의 저장소에 — [io.github.donggi.iroirobrowser.net.HttpFetcher.get] 의 `onSetCookie`. */
    val storeCookies: (String, List<String>) -> Unit = { url, values -> values.forEach { CookieManager.getInstance().setCookie(url, it) } }
}
