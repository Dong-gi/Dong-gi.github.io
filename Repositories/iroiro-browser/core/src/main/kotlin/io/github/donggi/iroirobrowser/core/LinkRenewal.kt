package io.github.donggi.iroirobrowser.core

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/**
 * **링크 갱신** — 멈춘 내려받기를 새 주소로 잇는다(1DM 의 '링크 갱신'). 서명된 CDN 주소는 표·만료 시각이 질의에 붙어 날마다 바뀌므로, 같은
 * 페이지에서 같은 미디어를 다시 고르면 주소는 달라도 같은 자원이다.
 *
 * 같다고 보는 것: **스킴·호스트·포트·경로가 같다**(질의·조각은 보지 않는다). 경로에 표를 넣는 CDN 이나 고를 때마다 다른 서버로 보내는 CDN 이면
 * 다르다고 나온다 — 그때는 잇지 않고 새로 받는다(다른 것을 이어 붙이는 것보다 안전한 쪽이다).
 */
object LinkRenewal {

    /** 두 주소가 같은 자원을 가리키는가(위). 읽을 수 없는 주소면 false. */
    fun sameResource(a: String, b: String): Boolean {
        val x = parse(a) ?: return false
        val y = parse(b) ?: return false
        return x.scheme.equals(y.scheme, ignoreCase = true) &&
            x.host.orEmpty().lowercase(Locale.ROOT) == y.host.orEmpty().lowercase(Locale.ROOT) &&
            port(x) == port(y) &&
            x.rawPath.orEmpty().ifEmpty { "/" } == y.rawPath.orEmpty().ifEmpty { "/" }
    }

    /** 같은 페이지인가 — 조각(`#…`)만 다르면 같다. 질의는 본다(`watch?v=…` 처럼 질의가 곧 그 페이지다). */
    fun samePage(a: String?, b: String?): Boolean {
        if (a.isNullOrEmpty() || b.isNullOrEmpty()) return false
        return a.substringBefore('#') == b.substringBefore('#')
    }

    /**
     * 받던 줄기들([old])을 새 주소들([new])로 이을 수 있는가 — 줄기 수가 같고 차례대로 같은 자원이다. 줄기는 HLS 의 재생목록(영상·따로 된
     * 소리)이나 DASH 의 첫 조각처럼 그 줄기를 가리키는 주소다.
     */
    fun sameTracks(old: List<String>, new: List<String>): Boolean =
        old.isNotEmpty() && old.size == new.size && old.indices.all { sameResource(old[it], new[it]) }

    private fun parse(url: String): URI? = try {
        URI(url.trim().replace(" ", "%20")).takeIf { it.isAbsolute && it.host != null }
    } catch (e: URISyntaxException) {
        null
    }

    private fun port(u: URI): Int = when {
        u.port != -1 -> u.port
        u.scheme.equals("https", ignoreCase = true) -> 443
        u.scheme.equals("http", ignoreCase = true) -> 80
        else -> -1
    }
}
