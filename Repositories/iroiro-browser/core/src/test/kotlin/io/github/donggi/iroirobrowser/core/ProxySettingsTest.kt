package io.github.donggi.iroirobrowser.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProxySettingsTest {

    @Test
    fun 비밀번호는_문자열에_나오지_않는다() {
        val s = ProxySettings.DEFAULT.copy(host = "proxy.example", user = "user-zz9", password = "s3cret-pw")
        assertFalse("s3cret-pw" in s.toString())
        assertFalse("user-zz9" in s.toString())
    }

    @Test
    fun 기본값은_켜져_있고_설정이_비어_있다() {
        assertTrue(ProxySettings.DEFAULT.enabled)
        assertFalse(ProxySettings.DEFAULT.isComplete)
        assertEquals(10443, ProxySettings.DEFAULT.port)
        assertTrue(ProxySettings.DEFAULT.copy(host = "p.example").isComplete)
    }

    @Test
    fun 호스트_칸() {
        assertNull(ProxySettings.checkHost("proxy.example.com"))
        assertNull(ProxySettings.checkHost("10.0.0.5"))
        assertNull(ProxySettings.checkHost("::1"))
        assertNotNull(ProxySettings.checkHost(""))
        assertNotNull(ProxySettings.checkHost("https://proxy.example.com"))
        assertNotNull(ProxySettings.checkHost("proxy.example.com:10443"))
        assertNotNull(ProxySettings.checkHost("proxy .example.com"))
    }

    @Test
    fun 포트_칸() {
        assertNull(ProxySettings.checkPort("10443"))
        assertNotNull(ProxySettings.checkPort("0"))
        assertNotNull(ProxySettings.checkPort("70000"))
        assertNotNull(ProxySettings.checkPort("abc"))
    }

    @Test
    fun 기본_예외는_정해_둔_목록이다() {
        val rules = Bypass.DEFAULT.rules()
        assertEquals(
            listOf("localhost", "*.local", "*.internal", "127.0.0.0/8", "169.254.0.0/16", "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "fc00::/7"),
            rules,
        )
        assertTrue(Bypass.DEFAULT.simpleHostnames)
    }

    @Test
    fun 사설망을_끄면_빠지고_겹치는_것은_한_번() {
        val b = Bypass(listOf("a.test", "a.test"), listOf("10.0.0.0/8"), privateNetworks = true, simpleHostnames = false)
        assertEquals(listOf("a.test", "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "fc00::/7"), b.rules())
        assertEquals(listOf("a.test", "10.0.0.0/8"), b.copy(privateNetworks = false).rules())
    }

    @Test
    fun 호스트_무늬를_고른다() {
        assertEquals("*.example.com", Bypass.normalizeHost(".Example.com"))
        assertEquals("*.local", Bypass.normalizeHost("*.local"))
        assertEquals("xn--3e0b707e.kr", Bypass.normalizeHost("한국.kr"))
        assertFailsWith<Bypass.Invalid> { Bypass.normalizeHost("*") }
        assertFailsWith<Bypass.Invalid> { Bypass.normalizeHost("http://x.test") }
        assertFailsWith<Bypass.Invalid> { Bypass.normalizeHost("x.test:80") }
        assertFailsWith<Bypass.Invalid> { Bypass.normalizeHost("a b") }
    }

    @Test
    fun 숫자로_끝나는_호스트는_점_네_개의_십진_IPv4_만() {
        // 크로미움은 이것들을 IP 규칙으로 읽는다 — `010.0.0.1` 은 8진수라 8.0.0.1(공인 주소), `10.1` 은 10.0.0.1, `0x0a.0.0.1` 은 10.0.0.1.
        assertEquals("192.168.1.10", Bypass.normalizeHost("192.168.1.10"))
        assertEquals("0.0.0.0", Bypass.normalizeHost("0.0.0.0"))
        for (raw in listOf("010.0.0.1", "10.1", "0x0a.0.0.1", "192.168.001.1", "2130706433", "1.2.3.4.5", "foo.123", "256.0.0.1", "v1.2")) {
            assertFailsWith<Bypass.Invalid>(raw) { Bypass.normalizeHost(raw) }
        }
        // 무늬(`*.`)는 IP 로 읽히지 않는다 — 크로미움도 글자로 견준다.
        assertEquals("*.0.0.1", Bypass.normalizeHost("*.0.0.1"))
        assertEquals("box.123abc", Bypass.normalizeHost("box.123abc"))
    }

    @Test
    fun IP_대역의_앞의_0_은_받지_않는다() {
        // 크로미움은 `010.0.0.0/8` 을 8.0.0.0/8(공인)로 읽고 `08.…` 은 받지 않는다(WebView 에 길을 거는 순간 던진다).
        for (raw in listOf("010.0.0.0/8", "08.0.0.0/8", "192.168.01.0/24", "::ffff:010.0.0.0/104")) {
            val e = assertFailsWith<Bypass.Invalid>(raw) { Bypass.normalizeCidr(raw) }
            assertTrue("8진수" in e.message.orEmpty(), raw)
        }
        assertEquals("0.0.0.0/8", Bypass.normalizeCidr("0.0.0.0/8"))
        assertEquals("::ffff:10.0.0.0/104", Bypass.normalizeCidr("::ffff:10.0.0.0/104"))
        assertEquals("2001:0db8::/32", Bypass.normalizeCidr("2001:0DB8::/32"))
        // 유니코드 숫자는 IP 가 아니다(코틀린의 isDigit 은 받는다).
        assertFailsWith<Bypass.Invalid> { Bypass.normalizeCidr("١٠.0.0.0/8") }
    }

    @Test
    fun IP_대역을_고른다() {
        assertEquals("10.0.0.0/8", Bypass.normalizeCidr("10.0.0.0/8"))
        assertEquals("192.168.0.1/32", Bypass.normalizeCidr("192.168.0.1"))
        assertEquals("fc00::/7", Bypass.normalizeCidr("[FC00::]/7"))
        assertEquals("fe80::1/128", Bypass.normalizeCidr("fe80::1"))
        assertFailsWith<Bypass.Invalid> { Bypass.normalizeCidr("0.0.0.0/0") }
        assertFailsWith<Bypass.Invalid> { Bypass.normalizeCidr("10.0.0.0/33") }
        assertFailsWith<Bypass.Invalid> { Bypass.normalizeCidr("example.com/8") }
        assertFailsWith<Bypass.Invalid> { Bypass.normalizeCidr("::/0") }
    }

    @Test
    fun 공인_대역은_가린다() {
        assertTrue(Bypass.isPublicCidr("8.8.8.0/24"))
        assertTrue(Bypass.isPublicCidr("2001:db8::/32"))
        assertFalse(Bypass.isPublicCidr("10.1.0.0/16"))
        assertFalse(Bypass.isPublicCidr("172.20.0.0/16"))
        assertFalse(Bypass.isPublicCidr("192.168.49.0/24"))
        assertFalse(Bypass.isPublicCidr("169.254.0.0/16"))
        assertFalse(Bypass.isPublicCidr("fd12::/16"))
        assertTrue(Bypass.isPublicCidr("172.32.0.0/16"))
    }

    @Test
    fun 목록_글을_가른다() {
        assertEquals(listOf("a.test", "b.test", "10.0.0.0/8"), Bypass.split("a.test, b.test\n# 주석\n10.0.0.0/8 # 사내\n\n"))
    }

    @Test
    fun 출구_확인의_답() {
        val exit = ExitTrace.parse("fl=1\nh=cloudflare.com\nip=203.0.113.7\nts=1\nloc=us\n")
        assertEquals(ExitInfo("203.0.113.7", "US"), exit)
        assertFalse(exit!!.looksDirect)
        assertTrue(ExitTrace.parse("ip=1.2.3.4\nloc=KR")!!.looksDirect)
        assertNull(ExitTrace.parse("<html>blocked</html>"))
        assertEquals(ExitInfo("1.2.3.4", null), ExitTrace.parse("ip=1.2.3.4\nloc=XYZ"))
    }

    @Test
    fun 길이_같은지() {
        // 설정에서 '저장' 만 눌러도 중계를 다시 세워 받던 HLS·DASH 가 끊겼다 — 길이 같으면 다시 세우지 않는다.
        val on = ProxySettings.DEFAULT.copy(host = "proxy.example", user = "u", password = "p")
        assertTrue(on.sameRoute(on.copy()))
        assertTrue(on.sameRoute(on.copy(host = " proxy.example ")))
        // 출구 확인 주소는 길이 아니다.
        assertTrue(on.sameRoute(on.copy(exitCheckUrl = "https://other.example/trace")))
        assertFalse(on.sameRoute(on.copy(host = "proxy2.example")))
        assertFalse(on.sameRoute(on.copy(port = 443)))
        assertFalse(on.sameRoute(on.copy(user = "v")))
        assertFalse(on.sameRoute(on.copy(password = "q")))
        assertFalse(on.sameRoute(on.copy(bypass = on.bypass.copy(simpleHostnames = false))))
        assertFalse(on.sameRoute(on.copy(enabled = false)))
        // 꺼져 있으면 주소·계정·예외는 길에 쓰이지 않는다 — 켜기만 본다.
        val off = on.copy(enabled = false)
        assertTrue(off.sameRoute(off.copy(host = "proxy2.example", password = "q", bypass = off.bypass.copy(cidrs = emptyList()))))
    }
}
