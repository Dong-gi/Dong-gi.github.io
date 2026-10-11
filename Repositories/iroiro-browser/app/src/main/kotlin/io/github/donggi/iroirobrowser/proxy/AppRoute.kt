package io.github.donggi.iroirobrowser.proxy

import io.github.donggi.iroirobrowser.core.BypassMatcher
import io.github.donggi.iroirobrowser.core.ProxySettings
import io.github.donggi.iroirobrowser.net.HttpFetcher
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

/**
 * 앱의 HTTP(내려받기·찾은 미디어 미리 읽기·광고 목록)가 **홉마다** 고르는 길 — [NetworkPath] 가 설정·중계가 바뀔 때마다 새로 짓고, 받기
 * 하나(시도 하나)는 시작할 때 얻은 것을 끝까지 쓴다. 그래서 받는 도중에 설정이 바뀌어도 한 받기의 홉들이 서로 다른 기준으로 고르지 않는다
 * (옛 중계가 닫혔으면 그 받기는 연결 오류로 끝나고, 다음 시도가 새 길을 얻는다). 순수 함수라 기기 없이 시험한다(`AppRouteTest`).
 *
 * - 프록시를 껐다 → 모두 바로(`Proxy.NO_PROXY`) — 사용자가 고른 것.
 * - 켰는데 중계가 없다 → 모두 null(길이 아직 없다 — 받지 않는다). **예외 주소도 바로 가지 않는다** — WebView 의 예외는 중계와 함께 거는
 *   override 에만 있다. 중계가 없는 동안 바로 보내면 WebView 보다 앞서 직접 나간다.
 * - 중계가 있다 → 예외 규칙([BypassMatcher] — WebView 에 넘기는 규칙을 크로미움처럼, 모르면 프록시로)에 맞으면 바로, 아니면 중계(127.0.0.1).
 *   프록시를 정하지 않은 동안(중계가 모든 요청에 502)에도 그렇다 — WebView 도 그때 같은 규칙으로 바로 연다.
 */
class AppRoute private constructor(
    private val enabled: Boolean,
    private val relay: Proxy?,
    private val matcher: BypassMatcher?,
) : HttpFetcher.Route {

    override fun proxyFor(url: URL): Proxy? = when {
        !enabled -> Proxy.NO_PROXY
        relay == null -> null
        matcher != null && matcher.bypasses(url) -> Proxy.NO_PROXY
        else -> relay
    }

    companion object {
        /** [settings] 와 지금 중계의 포트([relayPort] — 중계가 없으면 null)로. */
        fun of(settings: ProxySettings, relayPort: Int?): AppRoute {
            val relay = relayPort?.let { relayProxy(it) }
            val matcher = if (settings.enabled && relay != null) BypassMatcher(settings.bypass) else null
            return AppRoute(settings.enabled, relay, matcher)
        }

        /**
         * 출구 확인의 길 — **홉마다 늘 중계**(예외 규칙을 보지 않는다). 프록시의 출구를 보려는 것이라, 확인 주소나 그 넘겨주기가 예외에 든 곳이어도
         * 바로 가면 확인이 거짓말을 한다. 중계가 없으면 null(확인하지 않는다).
         */
        fun relayOnly(relayPort: Int?): HttpFetcher.Route? {
            val relay = relayPort?.let { relayProxy(it) } ?: return null
            return HttpFetcher.Route { relay }
        }

        /** 중계 — 루프백의 평문 HTTP 프록시. 주소는 글자 그대로(이름 풀이 없이) 127.0.0.1 이다(중계가 거기서만 듣는다). */
        fun relayProxy(port: Int): Proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", port))
    }
}
