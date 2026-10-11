package io.github.donggi.iroirobrowser.core

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/**
 * 앱이 직접 내는 요청(내려받기)의 `Referer` — 크롬의 기본 정책 `strict-origin-when-cross-origin` 과 같게: 같은 출처면 쪽의 주소 전체
 * (조각 `#…` 은 뺀다), 다른 출처면 쪽의 출처만(`https://host/`), https 쪽에서 http 로 가면 보내지 않는다. 쪽이 http(s) 가 아니면 없다.
 */
object Referrer {

    fun strictOriginWhenCrossOrigin(pageUrl: String?, targetUrl: String): String? {
        val page = parse(pageUrl ?: return null) ?: return null
        val target = parse(targetUrl) ?: return null
        if (page.scheme !in WEB || target.scheme !in WEB) return null
        if (page.scheme == "https" && target.scheme == "http") return null
        val pageOrigin = origin(page)
        if (pageOrigin == origin(target)) return pageUrl.substringBefore('#')
        return "$pageOrigin/"
    }

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
