package io.github.donggi.iroirobrowser.browser

import io.github.donggi.iroirobrowser.browser.PageErrors.Reason
import io.github.donggi.iroirobrowser.browser.PageErrors.Source
import io.github.donggi.iroirobrowser.proxy.NetworkPath.Phase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PageErrorsTest {

    private fun net(name: String, code: Int = -1) = Source.Network(code, name)

    private fun reason(source: Source, url: String = "https://example.com/", proxyOn: Boolean = true, phase: Phase = Phase.OK) =
        PageErrors.verdict(source, url, proxyOn, phase).reason

    @Test
    fun 터널_실패는_길의_상태로_까닭을_가른다() {
        val tunnel = net("net::ERR_TUNNEL_CONNECTION_FAILED", -11)
        // 길은 멀쩡하다(원격이 그 목적지만 거절하면 중계는 '닿음' 으로 알린다) — 프록시가 이 사이트로 잇지 않았다.
        assertEquals(Reason.PROXY_REFUSED, reason(tunnel, phase = Phase.OK))
        assertEquals(Reason.PROXY_UNREACHABLE, reason(tunnel, phase = Phase.UNREACHABLE))
        assertEquals(Reason.PROXY_AUTH, reason(tunnel, phase = Phase.AUTH_REJECTED))
        assertEquals(Reason.NOT_CONFIGURED, reason(tunnel, phase = Phase.NOT_CONFIGURED))
    }

    @Test
    fun 프록시가_막는_포트를_짚는다() {
        val tunnel = net("net::ERR_TUNNEL_CONNECTION_FAILED")
        assertEquals(PageErrors.Verdict(Reason.PROXY_REFUSED, 8443), PageErrors.verdict(tunnel, "https://example.com:8443/a?b", true, Phase.OK))
        assertNull(PageErrors.verdict(tunnel, "https://example.com/", true, Phase.OK).port)
        assertNull(PageErrors.verdict(tunnel, "https://example.com:443/", true, Phase.OK).port)
        assertEquals(10443, PageErrors.verdict(tunnel, "https://u:p@[2001:db8::1]:10443/", true, Phase.OK).port)
        // http 는 CONNECT 가 아니다 — 포트를 탓하지 않는다.
        assertNull(PageErrors.verdict(Source.Relay("upstream-status-403", 502), "http://example.com:8080/", true, Phase.OK).port)
        // 까닭이 포트가 아니면 붙이지 않는다.
        assertNull(PageErrors.verdict(net("net::ERR_CONNECTION_RESET"), "https://example.com:8443/", false, Phase.OFF).port)
    }

    @Test
    fun 연결_오류의_이름() {
        assertEquals(Reason.NAME_NOT_RESOLVED, reason(net("net::ERR_NAME_NOT_RESOLVED"), proxyOn = false, phase = Phase.OFF))
        assertEquals(Reason.OFFLINE, reason(net("net::ERR_INTERNET_DISCONNECTED"), proxyOn = false, phase = Phase.OFF))
        assertEquals(Reason.TIMEOUT, reason(net("net::ERR_CONNECTION_TIMED_OUT"), proxyOn = false, phase = Phase.OFF))
        assertEquals(Reason.CONNECTION, reason(net("net::ERR_CONNECTION_REFUSED"), proxyOn = false, phase = Phase.OFF))
        assertEquals(Reason.CONNECTION, reason(net("net::ERR_ADDRESS_UNREACHABLE"), proxyOn = false, phase = Phase.OFF))
        assertEquals(Reason.CERTIFICATE, reason(net("net::ERR_CERT_DATE_INVALID")))
        assertEquals(Reason.TLS, reason(net("net::ERR_SSL_PROTOCOL_ERROR")))
        assertEquals(Reason.REDIRECTS, reason(net("net::ERR_TOO_MANY_REDIRECTS")))
        assertEquals(Reason.RELAY, reason(net("net::ERR_PROXY_CONNECTION_FAILED")))
        assertEquals(Reason.SITE_ERROR, reason(net("net::ERR_HTTP_RESPONSE_CODE_FAILURE"), proxyOn = false, phase = Phase.OFF))
    }

    @Test
    fun 이름을_모르면_WebView_의_코드로() {
        assertEquals(Reason.NAME_NOT_RESOLVED, reason(net("", -2), proxyOn = false, phase = Phase.OFF))
        assertEquals(Reason.TIMEOUT, reason(net("", -8), proxyOn = false, phase = Phase.OFF))
        assertEquals(Reason.CONNECTION, reason(net("", -6), proxyOn = false, phase = Phase.OFF))
        assertEquals(Reason.TLS, reason(net("", -11), proxyOn = false, phase = Phase.OFF))
        assertEquals(Reason.UNKNOWN, reason(net("net::ERR_FAILED", -1), proxyOn = false, phase = Phase.OFF))
    }

    @Test
    fun 길이_망가졌으면_연결_쪽_오류의_까닭은_길이다() {
        assertEquals(Reason.PROXY_UNREACHABLE, reason(net("net::ERR_CONNECTION_RESET"), phase = Phase.UNREACHABLE))
        assertEquals(Reason.PROXY_UNREACHABLE, reason(net("net::ERR_TIMED_OUT"), phase = Phase.UNREACHABLE))
        assertEquals(Reason.PROXY_AUTH, reason(net("net::ERR_FAILED"), phase = Phase.AUTH_REJECTED))
        // 사이트를 탓할 수 있는 것은 그대로 — 인증서·이름·오프라인.
        assertEquals(Reason.CERTIFICATE, reason(net("net::ERR_CERT_AUTHORITY_INVALID"), phase = Phase.UNREACHABLE))
        assertEquals(Reason.OFFLINE, reason(net("net::ERR_INTERNET_DISCONNECTED"), phase = Phase.UNREACHABLE))
        // 프록시를 껐으면 길의 상태는 보지 않는다.
        assertEquals(Reason.CONNECTION, reason(net("net::ERR_CONNECTION_RESET"), proxyOn = false, phase = Phase.UNREACHABLE))
        assertTrue(PageErrors.isPathFailure(Phase.UNREACHABLE))
        assertTrue(PageErrors.isPathFailure(Phase.NOT_CONFIGURED))
        assertFalse(PageErrors.isPathFailure(Phase.CHECKING))
        assertFalse(PageErrors.isPathFailure(Phase.CHECK_FAILED))
    }

    @Test
    fun 중계의_표() {
        assertEquals(Reason.NOT_CONFIGURED, reason(Source.Relay("not-configured", 502), "http://example.com/"))
        assertEquals(Reason.PROXY_AUTH, reason(Source.Relay("upstream-auth", 502), "http://example.com/"))
        assertEquals(Reason.PROXY_UNREACHABLE, reason(Source.Relay("upstream-unreachable", 502), "http://example.com/"))
        assertEquals(Reason.PROXY_UNREACHABLE, reason(Source.Relay("upstream-closed", 502), "http://example.com/"))
        assertEquals(Reason.PROXY_REFUSED, reason(Source.Relay("upstream-status-403", 502), "http://example.com/"))
        // 모르는 표는 길의 상태로, 그것도 모르면 '프록시에 닿지 못함'.
        assertEquals(Reason.PROXY_AUTH, reason(Source.Relay("something-new", 502), phase = Phase.AUTH_REJECTED))
        assertEquals(Reason.PROXY_UNREACHABLE, reason(Source.Relay("something-new", 502), phase = Phase.OK))
    }

    @Test
    fun 프록시_서버의_오류_쪽() {
        assertEquals(Reason.NAME_NOT_RESOLVED, reason(Source.Proxy("ERR_DNS_FAIL 0", 503), "http://nowhere.example/"))
        assertEquals(Reason.PROXY_REFUSED, reason(Source.Proxy("ERR_ACCESS_DENIED 0", 403), "http://example.com/"))
        assertEquals(Reason.TIMEOUT, reason(Source.Proxy("ERR_READ_TIMEOUT 0", 504), "http://example.com/"))
        assertEquals(Reason.CONNECTION, reason(Source.Proxy("ERR_CONNECT_FAIL 111", 503), "http://example.com/"))
    }

    @Test
    fun 렌더러와_인증서() {
        assertEquals(Reason.RENDERER_CRASHED, reason(Source.Renderer(crashed = true)))
        assertEquals(Reason.RENDERER_KILLED, reason(Source.Renderer(crashed = false)))
        // 길이 망가져도 인증서는 인증서다.
        assertEquals(Reason.CERTIFICATE, reason(Source.Certificate, phase = Phase.UNREACHABLE))
    }

    @Test
    fun 덮지_않는_오류() {
        assertTrue(PageErrors.isIgnored(net("net::ERR_ABORTED", -1)))
        assertTrue(PageErrors.isIgnored(net("net::ERR_UNKNOWN_URL_SCHEME", -10)))
        assertTrue(PageErrors.isIgnored(net("", -10)))
        assertFalse(PageErrors.isIgnored(net("net::ERR_FAILED", -1)))
        assertFalse(PageErrors.isIgnored(Source.Certificate))
        assertFalse(PageErrors.isIgnored(Source.Relay("not-configured", 502)))
    }

    @Test
    fun 더_자세한_앞_오류를_뭉뚱그린_오류가_덮지_않는다() {
        val cert = PageErrors.Verdict(Reason.CERTIFICATE)
        val relay = PageErrors.Verdict(Reason.PROXY_AUTH)
        val unknown = PageErrors.Verdict(Reason.UNKNOWN)
        val site = PageErrors.Verdict(Reason.SITE_ERROR)
        // 중계의 502 뒤에 WebView 가 덧붙이는 'ERR_HTTP_RESPONSE_CODE_FAILURE' 가 계정 거절을 덮지 않는다.
        assertFalse(PageErrors.replaces(relay, sameUrl = true, new = site))
        assertFalse(PageErrors.replaces(cert, sameUrl = true, new = unknown))
        assertTrue(PageErrors.replaces(unknown, sameUrl = true, new = cert))
        assertTrue(PageErrors.replaces(site, sameUrl = true, new = unknown))
        assertTrue(PageErrors.replaces(cert, sameUrl = false, new = unknown))
        assertTrue(PageErrors.replaces(null, sameUrl = false, new = unknown))
        assertTrue(PageErrors.replaces(cert, sameUrl = true, new = relay))
    }
}
