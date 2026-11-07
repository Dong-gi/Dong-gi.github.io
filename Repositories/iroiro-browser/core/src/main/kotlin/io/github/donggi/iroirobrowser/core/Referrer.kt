package io.github.donggi.iroirobrowser.core

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/**
 * 앱이 직접 내는 요청(내려받기)의 `Referer` — 크롬의 기본 정책 `strict-origin-when-cross-origin` 과 같게: 같은 출처면 쪽의 주소 전체
 * (조각 `#…` 은 뺀다), 다른 출처면 쪽의 출처만(`https://host/`), https 쪽에서 http 로 가면 보내지 않는다. 쪽이 http(s) 가 아니면 없다.
 *
 * **홉마다 짓는다** — 넘겨주기를 따를 때 브라우저는 홉마다 쪽에서 그 홉으로 다시 고른다. 처음 홉에 맞춰 지은 값을 모든
 * 홉에 들고 가면, 처음 주소가 쪽과 같은 출처일 때 쪽의 전체 주소(질의 포함)가 다른 출처로 넘겨준 곳·다른 출처의 조각·열쇠 서버에 간다.
 */
object Referrer {

    /** 쪽 [pageUrl] 에서 낸 요청이 [targetUrl](넘겨주기면 그 홉)에 실을 Referer. */
    fun strictOriginWhenCrossOrigin(pageUrl: String?, targetUrl: String): String? {
        val page = parse(pageUrl ?: return null) ?: return null
        val target = parse(targetUrl) ?: return null
        if (page.scheme !in WEB || target.scheme !in WEB) return null
        if (page.scheme == "https" && target.scheme == "http") return null
        val pageOrigin = origin(page)
        if (pageOrigin == origin(target)) return pageUrl.substringBefore('#')
        return "$pageOrigin/"
    }

    /**
     * 처음 홉의 Referer 를 사이트의 규약이 정한 요청(갤러리 받기 — pixiv·hitomi 가 바라는 값)의 홉 [hop] 에 실을 것. 처음 주소([first])에는 정한
     * 값([chosen]) 그대로, 넘겨주기 뒤의 홉은 그 값을 쪽의 주소로 보고 [strictOriginWhenCrossOrigin] — 다른 출처로 넘겨주면 출처만 간다.
     */
    fun pinnedFirstHop(chosen: String?, first: String, hop: String): String? =
        if (hop == first) chosen else strictOriginWhenCrossOrigin(chosen, hop)

    private fun parse(url: String): URI? = try {
        URI(url).takeIf { it.host != null }
    } catch (e: URISyntaxException) {
        null
    }

    private fun origin(u: URI): String {
        val scheme = u.scheme.lowercase(Locale.ROOT)
        val default = if (scheme == "https") 443 else 80
        val port = if (u.port == -1 || u.port == default) "" else ":${u.port}"
        return "$scheme://${u.host.lowercase(Locale.ROOT)}$port"
    }

    private val WEB = setOf("http", "https")
}
