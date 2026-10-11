package io.github.donggi.iroirobrowser.core

import java.net.MalformedURLException
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 앱의 HTTP 가 바로 갈 주소([BypassMatcher]). 약속은 **크로미움(`net::ProxyBypassRules`)이 같은 규칙으로 바로 여는 것의 부분집합** — 표마다
 * '바로' 와 '프록시' 를 함께 박고, 크로미움과 다르게 읽힐 수 있는 꼴은 모두 프록시인지 본다(그 꼴을 느슨하게 읽으면 바로가 되는 규칙을 일부러
 * 함께 둔다 — 고침을 되돌리면 깨지게).
 */
class BypassMatcherTest {

    private val defaults = BypassMatcher(Bypass.DEFAULT)

    /** 자바의 URL — 이 앱의 HttpFetcher 가 홉마다 만드는 것과 같은 것. */
    @Suppress("DEPRECATION")
    private fun url(s: String) = URL(s)

    private fun direct(m: BypassMatcher, vararg urls: String) = urls.forEach { assertTrue(m.bypasses(url(it)), "바로여야 한다: $it") }

    private fun proxied(m: BypassMatcher, vararg urls: String) = urls.forEach { assertFalse(m.bypasses(url(it)), "프록시여야 한다: $it") }

    /** 자바가 주소로 읽지 않으면(HttpFetcher 가 길을 묻기 전에 던진다) 그것으로 됐다 — 읽으면 프록시여야 한다. */
    private fun proxiedOrInvalid(m: BypassMatcher, vararg urls: String) = urls.forEach { s ->
        val u = try {
            url(s)
        } catch (e: MalformedURLException) {
            return@forEach
        }
        assertFalse(m.bypasses(u), "프록시여야 한다: $s")
    }

    private fun matcher(
        hosts: List<String> = emptyList(),
        cidrs: List<String> = emptyList(),
        privateNetworks: Boolean = false,
        simpleHostnames: Boolean = false,
    ) = BypassMatcher(Bypass(hosts, cidrs, privateNetworks, simpleHostnames))

    @Test
    fun 기본_예외() {
        direct(
            defaults,
            "http://localhost/", "http://LOCALHOST:8080/x", "http://nas.local/", "https://a.b.nas.local/file.bin", "http://NAS.Local/",
            "http://router.internal/", "http://10.0.0.1/", "http://10.255.255.255/", "http://172.16.0.0/", "http://172.31.255.255/",
            "http://192.168.0.1/", "https://192.168.255.255:8443/", "http://127.0.0.1/", "http://127.255.255.254/", "http://169.254.1.1/",
            "http://[fc00::1]/", "http://[fdff:ffff:ffff:ffff:ffff:ffff:ffff:ffff]/", "http://[::1]/", "http://[fe80::1]/", "http://[FE80::1]/",
            "http://[febf::1]/", "http://nas/", "http://my-router/", "http://my_nas:8000/",
        )
        proxied(
            defaults,
            "http://example.com/", "https://notlocal.com/", "http://local.example.com/", "http://nas.local.example.com/", "http://xlocal.com/a.local",
            "http://9.255.255.255/", "http://11.0.0.0/", "http://172.15.255.255/", "http://172.32.0.0/", "http://192.167.255.255/",
            "http://192.169.0.0/", "http://8.8.8.8/", "http://[fbff:ffff::1]/", "http://[fe00::1]/", "http://[fec0::1]/", "http://[2001:db8::1]/",
            "http://[::2]/",
        )
    }

    @Test
    fun 호스트_무늬() {
        val m = matcher(hosts = listOf("example.com", "*.corp.test"))
        direct(m, "http://example.com/", "https://EXAMPLE.com:8443/a", "http://a.corp.test/", "http://a.b.corp.test/")
        // 무늬 없는 이름은 그 이름 하나, `*.` 무늬는 그 이름 자신이 아니다(base::MatchPattern).
        proxied(m, "http://a.example.com/", "http://corp.test/", "http://acorp.test/", "http://example.com.evil/", "http://xexample.com/")
        // `.example.com` 은 `*.example.com` 으로 저장된다.
        val dot = matcher(hosts = listOf(Bypass.normalizeHost(".Example.COM")))
        direct(dot, "http://www.example.com/")
        proxied(dot, "http://example.com/")
    }

    @Test
    fun 국제화_이름은_퓨니코드로_견준다() {
        val m = matcher(hosts = listOf(Bypass.normalizeHost("한국.kr"), Bypass.normalizeHost("*.한국.kr")))
        direct(m, "http://xn--3e0b707e.kr/", "http://www.xn--3e0b707e.kr/")
        // 유니코드 그대로의 호스트는 크로미움과 같은 퓨니코드인지 보일 수 없다 — 프록시(WebView 가 넘기는 주소는 이미 퓨니코드다).
        proxiedOrInvalid(m, "http://한국.kr/", "http://www.한국.kr/")
    }

    @Test
    fun IPv4_대역의_경계() {
        val m = matcher(cidrs = listOf("192.168.1.0/24", "10.20.0.0/14", "203.0.113.7/32", "100.64.0.0/10"))
        direct(
            m, "http://192.168.1.0/", "http://192.168.1.255/", "http://10.20.0.0/", "http://10.23.255.255/", "http://203.0.113.7/",
            "http://100.64.0.0/", "http://100.127.255.255/",
        )
        proxied(
            m, "http://192.168.0.255/", "http://192.168.2.0/", "http://10.19.255.255/", "http://10.24.0.0/", "http://203.0.113.6/",
            "http://203.0.113.8/", "http://100.63.255.255/", "http://100.128.0.0/",
        )
        // 대역 주소의 나머지 비트는 보지 않는다(크로미움의 IPAddressMatchesPrefix).
        val hostBits = matcher(cidrs = listOf("192.168.1.77/24"))
        direct(hostBits, "http://192.168.1.5/")
        proxied(hostBits, "http://192.168.2.5/")
    }

    @Test
    fun IPv6_대역의_경계() {
        val m = matcher(cidrs = listOf("2001:db8::/29", "2001:db9:1::/48", "FD12:3456::/32"))
        direct(
            m, "http://[2001:db8::]/", "http://[2001:dbf:ffff:ffff:ffff:ffff:ffff:ffff]/", "http://[2001:0DB8::1]/", "http://[2001:db9:1:ffff::1]/",
            "http://[fd12:3456::1]/", "http://[FD12:3456:ffff::1]/",
        )
        proxied(m, "http://[2001:db7:ffff::1]/", "http://[2001:dc0::1]/", "http://[fd12:3457::1]/")
        // 사설 대역 fc00::/7 의 경계.
        val ula = matcher(privateNetworks = true)
        direct(ula, "http://[fc00::]/", "http://[fdff:ffff::1]/")
        proxied(ula, "http://[fbff:ffff::1]/", "http://[fe00::]/")
    }

    @Test
    fun 점_없는_이름() {
        val simple = matcher(simpleHostnames = true)
        direct(simple, "http://nas/", "http://NAS:8080/x", "http://localhost/")
        // IP 는 점이 없어도 이름이 아니다. 숫자 하나·16진은 크로미움이 IPv4(127.0.0.1)로 읽는다 — 이름으로 다루지 않는다.
        proxied(simple, "http://nas.lan/", "http://[::2]/", "http://2130706433/", "http://0x7f000001/", "http://08/")
        val none = matcher(simpleHostnames = false)
        proxied(none, "http://nas/", "http://local/")
        direct(none, "http://localhost/")
    }

    @Test
    fun 크로미움이_늘_거는_암묵_규칙() {
        val m = matcher()
        direct(
            m, "http://localhost/", "http://127.0.0.1/", "http://127.1.2.3/", "http://169.254.0.1/", "http://169.254.255.255/", "http://[::1]/",
            "http://[0:0:0:0:0:0:0:1]/", "http://[fe80::abcd]/", "http://[febf:ffff::1]/",
        )
        proxied(
            m, "http://128.0.0.1/", "http://126.255.255.255/", "http://169.253.255.255/", "http://169.255.0.0/", "http://[fec0::1]/",
            "http://[fe7f::1]/", "http://[::]/", "http://0.0.0.0/", "http://10.0.0.1/",
        )
    }

    @Test
    fun 크로미움과_다른_것은_모두_프록시() {
        // 1. *.localhost — 크로미움은 스스로 루프백으로 풀지만 자바는 시스템 DNS 로 푼다.
        proxied(defaults, "http://foo.localhost/", "http://a.b.localhost/")
        // 2. 끝의 점.
        proxied(defaults, "http://nas.local./", "http://localhost./", "http://10.0.0.1./", "http://127.0.0.1./")
        // 3. 점 네 개의 십진 꼴이 아닌 IPv4 — `010.0.0.1` 을 십진수로 읽으면 10/8 이라 바로가 되지만 크로미움은 8.0.0.1(프록시)이다.
        proxied(
            defaults, "http://127.1/", "http://0x7f.0.0.1/", "http://0177.0.0.1/", "http://010.0.0.1/", "http://192.168.001.010/",
            "http://2130706433/", "http://0x0a000001/", "http://10.0.0.01/", "http://10.0.0.256/", "http://1.2.3.4.5/", "http://foo.123/",
        )
        // 4. 사용자 정보·포트·이상한 글자. `evil%2ecom` 을 글자 그대로 보면 점 없는 이름(바로)이지만 크로미움은 evil.com 으로 푼다.
        proxied(defaults, "http://user@nas.local/", "http://user:pw@10.0.0.1/", "http://@nas.local/")
        proxied(defaults, "http://nas.local:0/")
        proxiedOrInvalid(
            defaults, "http://nas.local:65536/", "http://evil%2ecom/", "http://n%61s.local/", "http://nаs.local/", "ftp://nas.local/",
            "http:///x", "http:nas.local/x", "http://evil.com\\@nas.local/", "http://nas.local\\@evil.com/",
        )
        direct(defaults, "http://nas.local:65535/", "http://nas.local:1/")
        for (host in listOf("nas .local", "nas\t.local", "nas.local\u0000", "nas\\x.local", "[fe80::1%25eth0]", "[fe80::1%eth0]", "[::1", "", null)) {
            assertFalse(defaults.bypassesHost(host), "프록시여야 한다: $host")
        }
        // 5. IPv4 를 실은 IPv6 — IPv4 대역(10/8)·루프백·링크 로컬로 보지 않는다.
        proxied(defaults, "http://[::ffff:10.0.0.1]/", "http://[::ffff:127.0.0.1]/", "http://[::ffff:7f00:1]/", "http://[::ffff:169.254.1.1]/")
        // 그 꼴의 IPv6 대역에는 IPv6 끼리만 견준다 — IPv4 주소는 견주지 않는다.
        val mapped = matcher(cidrs = listOf("::ffff:10.0.0.0/104"))
        direct(mapped, "http://[::ffff:10.0.0.1]/", "http://[::ffff:a00:1]/")
        proxied(mapped, "http://10.0.0.1/")
        proxiedOrInvalid(mapped, "http://[::ffff:010.0.0.1]/")
    }

    @Test
    fun 다르게_읽힐_수_있는_규칙은_없는_셈() {
        // 앞의 0 — 크로미움은 8진수로 읽는다(8.0.0.1·8.0.0.0/8). 여기서는 8.x 도 10.x 도 바로 보내지 않는다.
        val octal = matcher(hosts = listOf("010.0.0.1", "10.1", "0x0a.0.0.2"), cidrs = listOf("010.0.0.0/8", "08.0.0.0/8"))
        proxied(octal, "http://8.0.0.1/", "http://10.0.0.1/", "http://10.0.0.2/", "http://8.1.2.3/", "http://10.1.2.3/")
        // 모양이 크로미움과 다르게 읽힐 수 있는 것들.
        val odd = matcher(
            hosts = listOf("*", "*example.com", "foo.*.com", "exa?ple.com", "nas.local:8080", "http://nas.lan", "nas.local.", "　box.lan"),
            cidrs = listOf("[fc00::]/7", "10.0.0.0/8/9", "10.0.0.0/33", "10.0.0.0/", "10.0.0.0/+8", "fe80::1%eth0/64"),
        )
        proxied(
            odd, "http://example.com/", "http://aexample.com/", "http://foo.x.com/", "http://exaxple.com/", "http://nas.local/", "http://nas.lan/",
            "http://box.lan/", "http://[fc00::1]/", "http://10.0.0.1/",
        )
        // 같은 것을 바로 적으면 바로다(위가 거른 것이 꼴 때문임을 보인다). ASCII 공백은 크로미움도 벗긴다.
        direct(matcher(hosts = listOf(" box.lan ", "NAS.LAN"), cidrs = listOf("fc00::/7", "10.0.0.0/8")), "http://box.lan/", "http://nas.lan/", "http://[fc00::1]/", "http://10.0.0.1/")
    }

    @Test
    fun 특수_규칙() {
        // `<local>` 은 크로미움처럼 점 없는 이름(대소문자 무시) — 설정에서는 스위치(simpleHostnames)로만 온다.
        direct(matcher(hosts = listOf("<LOCAL>")), "http://nas/")
        // `<-loopback>` 이 있으면 아무것도 바로 보내지 않는다(크로미움에서는 차례에 따라 암묵 규칙이 빠진다).
        val subtract = BypassMatcher(Bypass.DEFAULT.copy(hosts = Bypass.DEFAULT.hosts + "<-loopback>"))
        proxied(subtract, "http://localhost/", "http://127.0.0.1/", "http://10.0.0.1/", "http://nas.local/", "http://nas/", "http://[::1]/")
        // 길이 0 의 대역은 크로미움처럼 그 꼴의 모든 IP 글자 — 이름과 다른 꼴의 주소는 아니다.
        val all = matcher(cidrs = listOf("0.0.0.0/0"))
        direct(all, "http://8.8.8.8/", "http://203.0.113.9/")
        proxied(all, "http://example.com/", "http://[2001:db8::1]/")
    }

    @Test
    fun IPv4_글자에도_무늬를_견준다() {
        // 크로미움은 호스트 무늬를 `url.host()` 의 글자에 견준다 — 점 네 개의 IPv4 도 그 글자다. 무늬 없는 IPv4 는 그 주소 하나.
        val m = matcher(hosts = listOf("*.0.0.1", "192.0.2.10"))
        direct(m, "http://10.0.0.1/", "http://192.0.2.10/")
        proxied(m, "http://10.0.0.2/", "http://0.0.1/", "http://192.0.2.11/")
    }
}
