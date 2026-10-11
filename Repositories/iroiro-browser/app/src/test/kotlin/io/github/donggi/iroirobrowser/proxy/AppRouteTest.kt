package io.github.donggi.iroirobrowser.proxy

import io.github.donggi.iroirobrowser.core.Bypass
import io.github.donggi.iroirobrowser.core.ProxySettings
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 앱의 HTTP 가 홉마다 고르는 길([AppRoute]) — `NetworkPath` 의 세 상태(끔·켰는데 중계 없음·중계 있음)와 출구 확인(늘 중계). 예외 규칙의 세부는
 * `:core` 의 `BypassMatcherTest` 가 본다.
 */
class AppRouteTest {

    private val on = ProxySettings.DEFAULT.copy(host = "proxy.example", user = "u", password = "p")
    private val relayPort = 41234

    @Suppress("DEPRECATION")
    private fun url(s: String) = URL(s)

    private fun assertRelay(proxy: Proxy?, what: String) {
        val p = assertNotNull(proxy, what)
        assertEquals(Proxy.Type.HTTP, p.type(), what)
        val address = p.address() as InetSocketAddress
        assertEquals("127.0.0.1", address.hostString, what)
        assertEquals(relayPort, address.port, what)
    }

    private val lan = listOf("http://192.168.0.10/file.bin", "http://nas.local/", "http://10.0.2.2:8765/a", "http://localhost:8080/", "http://router/")
    private val internet = listOf("https://example.com/", "http://203.0.113.9/", "http://foo.localhost/", "http://nas.local./")

    @Test
    fun 꺼져_있으면_모두_바로() {
        val route = AppRoute.of(on.copy(enabled = false), relayPort = null)
        (lan + internet).forEach { assertEquals(Proxy.NO_PROXY, route.proxyFor(url(it)), it) }
    }

    @Test
    fun 켰는데_중계가_없으면_예외_주소도_보내지_않는다() {
        // WebView 의 예외는 중계와 함께 거는 override 에만 있다 — 중계가 서기 전에 사설망으로 바로 나가지 않는다.
        val route = AppRoute.of(on, relayPort = null)
        (lan + internet).forEach { assertNull(route.proxyFor(url(it)), it) }
    }

    @Test
    fun 중계가_있으면_예외는_바로_나머지는_중계() {
        val route = AppRoute.of(on, relayPort)
        lan.forEach { assertEquals(Proxy.NO_PROXY, route.proxyFor(url(it)), it) }
        internet.forEach { assertRelay(route.proxyFor(url(it)), it) }
    }

    @Test
    fun 프록시를_정하지_않았어도_중계가_있으면_예외는_바로() {
        // 중계가 모든 요청에 502 를 내는 동안에도 WebView 는 같은 규칙으로 사설망을 바로 연다 — 앱의 HTTP 도 같다.
        val route = AppRoute.of(ProxySettings.DEFAULT, relayPort)
        assertEquals(Proxy.NO_PROXY, route.proxyFor(url("http://192.168.1.1/")))
        assertRelay(route.proxyFor(url("https://example.com/")), "example.com")
    }

    @Test
    fun 예외_규칙을_따른다() {
        val none = AppRoute.of(on.copy(bypass = Bypass(emptyList(), emptyList(), privateNetworks = false, simpleHostnames = false)), relayPort)
        assertRelay(none.proxyFor(url("http://192.168.0.10/")), "사설망을 껐다")
        assertRelay(none.proxyFor(url("http://router/")), "점 없는 이름을 껐다")
        // 크로미움이 늘 거는 암묵 규칙(루프백)은 남는다.
        assertEquals(Proxy.NO_PROXY, none.proxyFor(url("http://127.0.0.1:8080/")))
        val more = AppRoute.of(on.copy(bypass = Bypass.DEFAULT.copy(hosts = listOf("*.corp.test"), cidrs = listOf("100.64.0.0/10"))), relayPort)
        assertEquals(Proxy.NO_PROXY, more.proxyFor(url("https://wiki.corp.test/")))
        assertEquals(Proxy.NO_PROXY, more.proxyFor(url("http://100.100.100.100/")))
        assertRelay(more.proxyFor(url("https://corp.test/")), "corp.test")
    }

    @Test
    fun 출구_확인은_예외를_보지_않고_늘_중계() {
        val route = assertNotNull(AppRoute.relayOnly(relayPort))
        (lan + internet).forEach { assertRelay(route.proxyFor(url(it)), it) }
        assertNull(AppRoute.relayOnly(null))
    }
}
