package io.github.donggi.iroirobrowser.browser

import io.github.donggi.iroirobrowser.proxy.NetworkPath.Phase

/**
 * 주 문서가 열리지 않은 까닭을 고른다(앞 판은 WebView 의 영어 오류 쪽·빈 쪽·사라지는 토스트뿐이었다). 재료는 넷이다:
 *
 * - WebView 의 연결 오류 — `net::ERR_…` 이름(설명 글)을 먼저 보고, 모르는 이름이면 `WebViewClient.ERROR_*` 코드로 어림한다(크로미움이 코드를
 *   어떻게 묶는지는 판마다 바뀔 수 있어 이름이 더 믿을 만하다).
 * - 중계가 만든 응답의 표(`X-Iroiro-Relay` — `not-configured`·`upstream-auth`·…) — http 쪽에서만 보인다. https 는 터널이 실패해
 *   WebView 가 `ERR_TUNNEL_CONNECTION_FAILED` 만 알린다.
 * - 프록시 서버(Squid)의 오류 쪽에 달린 `X-Squid-Error`.
 * - **그때의 길의 상태**(`NetworkPath.Phase`) — 프록시가 닿지 않거나 계정이 거절됐으면 연결 오류의 까닭은 그것이다. 원격이 그 목적지만
 *   거절했으면 중계는 '닿음' 으로 알리므로(길은 멀쩡하다) 터널 실패를 '프록시가 이 사이트로 잇지 않았다' 로 읽는다.
 *
 * 화면에 묶이지 않은 판정이라 기기 없이 시험한다. 글은 화면([ErrorCover])이 고른다.
 */
object PageErrors {

    enum class Reason {
        /** 프록시를 켰지만 프록시 주소를 넣지 않았다 — 중계가 막는다. */
        NOT_CONFIGURED,
        PROXY_UNREACHABLE,
        PROXY_AUTH,

        /** 프록시 서버에는 닿았는데 이 목적지로 잇지 않았다(서버가 막았거나 사이트에 닿지 못했다). */
        PROXY_REFUSED,

        /** WebView 가 앱 안의 중계에 닿지 못했거나 중계와 계정이 맞지 않았다(길을 다시 세우는 순간 등). */
        RELAY,
        NAME_NOT_RESOLVED,
        OFFLINE,
        TIMEOUT,
        CONNECTION,

        /** 인증서를 믿을 수 없다(넘기지 않는다 — 1단계의 결정). */
        CERTIFICATE,
        TLS,
        REDIRECTS,

        /** 사이트가 내용 없는 오류 응답을 주었다. */
        SITE_ERROR,
        UNKNOWN,
        RENDERER_CRASHED,
        RENDERER_KILLED,
    }

    /** 오류가 온 길. */
    sealed interface Source {
        /** WebView 의 연결 오류 — [code] 는 `WebViewClient.ERROR_*`, [name] 은 설명 글(`net::ERR_…`). */
        data class Network(val code: Int, val name: String) : Source

        /** 중계가 만든 응답 — [marker] 는 `X-Iroiro-Relay` 머리의 값. */
        data class Relay(val marker: String, val status: Int) : Source

        /** 프록시 서버의 오류 쪽 — [squidError] 는 `X-Squid-Error` 머리의 값(`ERR_ACCESS_DENIED 0`). */
        data class Proxy(val squidError: String, val status: Int) : Source

        data object Certificate : Source

        /** 렌더러가 사라졌다 — [crashed] 면 쪽이 멈추게 했고, 아니면 시스템이 메모리를 찾으려 닫았다. */
        data class Renderer(val crashed: Boolean) : Source
    }

    /** 까닭과, 까닭이 '포트' 를 말할 때 그 포트(https 의 443 이 아닌 것 — 프록시 서버가 흔히 막는다). */
    data class Verdict(val reason: Reason, val port: Int? = null)

    /**
     * 까닭을 고른다. [proxyOn] 은 프록시를 켰는가, [phase] 는 오류가 났을 때의 길의 상태(오류 직후에 온 길의 소식은 부르는 쪽이 옮겨 담는다 —
     * 중계의 소식은 같은 실패의 WebView 오류보다 조금 늦게 올 수 있다. [isPathFailure]).
     */
    fun verdict(source: Source, url: String, proxyOn: Boolean, phase: Phase): Verdict {
        val path = if (proxyOn) failingPhase(phase) else null
        val reason = when (source) {
            is Source.Renderer -> return Verdict(if (source.crashed) Reason.RENDERER_CRASHED else Reason.RENDERER_KILLED)
            Source.Certificate -> return Verdict(Reason.CERTIFICATE)
            is Source.Relay -> relayReason(source.marker) ?: path ?: Reason.PROXY_UNREACHABLE
            is Source.Proxy -> squidReason(source.squidError)
            is Source.Network -> {
                val r = networkReason(source)
                // 길이 망가졌으면 연결 쪽 오류의 까닭은 길이다(사이트 탓이 아니다).
                if (path != null && r in PATH_SENSITIVE) path else r
            }
        }
        val port = if (reason == Reason.PROXY_REFUSED) unusualTlsPort(url) else null
        return Verdict(reason, port)
    }

    /** 덮개를 띄우지 않는 오류 — 사용자가 멈췄거나 다른 탐색이 앞선 것(`ERR_ABORTED`), 다른 앱으로 넘긴 스킴. */
    fun isIgnored(source: Source): Boolean =
        source is Source.Network && (IGNORED.any { source.name.contains(it) } || source.code == ERROR_UNSUPPORTED_SCHEME)

    /**
     * 같은 주소의 앞 오류를 새 오류로 바꿀까 — 앞 것이 더 자세하면 두고(중계의 표·인증서 뒤에 WebView 가 덧붙이는 뭉뚱그린 오류가
     * 덮지 않게), 그 밖에는 바꾼다.
     */
    fun replaces(old: Verdict?, sameUrl: Boolean, new: Verdict): Boolean =
        old == null || !sameUrl || new.reason !in GENERIC || old.reason in GENERIC

    /** 길이 망가진 상태인가 — 오류 직후에 이 상태가 오면 그것이 그 오류의 까닭이다. */
    fun isPathFailure(phase: Phase): Boolean = failingPhase(phase) != null

    private fun failingPhase(phase: Phase): Reason? = when (phase) {
        Phase.NOT_CONFIGURED -> Reason.NOT_CONFIGURED
        Phase.UNREACHABLE -> Reason.PROXY_UNREACHABLE
        Phase.AUTH_REJECTED -> Reason.PROXY_AUTH
        else -> null
    }

    private fun relayReason(marker: String): Reason? = when {
        marker == "not-configured" -> Reason.NOT_CONFIGURED
        marker == "upstream-auth" -> Reason.PROXY_AUTH
        marker == "upstream-unreachable" || marker == "upstream-closed" -> Reason.PROXY_UNREACHABLE
        marker.startsWith("upstream-status-") -> Reason.PROXY_REFUSED
        else -> null
    }

    private fun squidReason(header: String): Reason = when (header.trim().substringBefore(' ')) {
        "ERR_DNS_FAIL" -> Reason.NAME_NOT_RESOLVED
        "ERR_READ_TIMEOUT", "ERR_LIFETIME_EXP", "ERR_CONNECT_TIMEOUT" -> Reason.TIMEOUT
        "ERR_CONNECT_FAIL", "ERR_ZERO_SIZE_OBJECT", "ERR_READ_ERROR", "ERR_WRITE_ERROR" -> Reason.CONNECTION
        else -> Reason.PROXY_REFUSED
    }

    private fun networkReason(s: Source.Network): Reason {
        val n = s.name
        return when {
            "ERR_NAME_NOT_RESOLVED" in n || "ERR_NAME_RESOLUTION_FAILED" in n -> Reason.NAME_NOT_RESOLVED
            "ERR_INTERNET_DISCONNECTED" in n -> Reason.OFFLINE
            "ERR_TIMED_OUT" in n || "ERR_CONNECTION_TIMED_OUT" in n -> Reason.TIMEOUT
            "ERR_TUNNEL_CONNECTION_FAILED" in n -> Reason.PROXY_REFUSED
            "ERR_PROXY_" in n || "ERR_UNEXPECTED_PROXY_AUTH" in n -> Reason.RELAY
            "ERR_CERT_" in n -> Reason.CERTIFICATE
            "ERR_SSL_" in n || "ERR_BAD_SSL_CLIENT_AUTH_CERT" in n -> Reason.TLS
            "ERR_TOO_MANY_REDIRECTS" in n -> Reason.REDIRECTS
            "ERR_HTTP_RESPONSE_CODE_FAILURE" in n || "ERR_EMPTY_RESPONSE" in n || "ERR_INVALID_RESPONSE" in n -> Reason.SITE_ERROR
            CONNECTION_NAMES.any { it in n } -> Reason.CONNECTION
            else -> when (s.code) {
                ERROR_HOST_LOOKUP -> Reason.NAME_NOT_RESOLVED
                ERROR_TIMEOUT -> Reason.TIMEOUT
                ERROR_CONNECT, ERROR_IO -> Reason.CONNECTION
                ERROR_FAILED_SSL_HANDSHAKE -> Reason.TLS
                ERROR_REDIRECT_LOOP -> Reason.REDIRECTS
                else -> Reason.UNKNOWN
            }
        }
    }

    /** https 주소의 포트가 443 이 아니면 그 포트 — Squid 의 기본 설정은 CONNECT 를 443(SSL_ports)에만 연다. */
    private fun unusualTlsPort(url: String): Int? {
        if (!url.startsWith("https://", ignoreCase = true)) return null
        val authority = url.substring("https://".length).substringBefore('/').substringBefore('?').substringBefore('#').substringAfterLast('@')
        // IPv6 리터럴(`[::1]:8443`)은 괄호 뒤의 콜론만 본다.
        val port = authority.substringAfter(']', authority).substringAfterLast(':', "").toIntOrNull() ?: return null
        return port.takeIf { it != 443 }
    }

    // WebViewClient.ERROR_* (android.webkit — 바뀌지 않는 공개 상수). 시험이 플랫폼 없이 돌게 값으로 둔다.
    private const val ERROR_HOST_LOOKUP = -2
    private const val ERROR_CONNECT = -6
    private const val ERROR_IO = -7
    private const val ERROR_TIMEOUT = -8
    private const val ERROR_REDIRECT_LOOP = -9
    private const val ERROR_UNSUPPORTED_SCHEME = -10
    private const val ERROR_FAILED_SSL_HANDSHAKE = -11

    private val IGNORED = listOf("ERR_ABORTED", "ERR_UNKNOWN_URL_SCHEME", "ERR_BLOCKED_BY_CLIENT")
    private val CONNECTION_NAMES = listOf(
        "ERR_CONNECTION_", "ERR_ADDRESS_UNREACHABLE", "ERR_ADDRESS_INVALID", "ERR_NETWORK_CHANGED", "ERR_NETWORK_ACCESS_DENIED",
        "ERR_SOCKET_NOT_CONNECTED", "ERR_NETWORK_IO_SUSPENDED",
    )

    /** 길이 망가졌을 때 까닭을 길로 돌릴 오류(사이트를 탓할 수 없는 것). */
    private val PATH_SENSITIVE = setOf(
        Reason.CONNECTION, Reason.TIMEOUT, Reason.PROXY_REFUSED, Reason.RELAY, Reason.SITE_ERROR, Reason.UNKNOWN,
    )

    /** 뭉뚱그린 까닭 — 더 자세한 앞 오류를 덮지 않는다. */
    private val GENERIC = setOf(Reason.UNKNOWN, Reason.SITE_ERROR)
}
