package io.github.donggi.iroirobrowser.core.backup

import io.github.donggi.iroirobrowser.core.adblock.HideRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 가져오는 값의 검사 — 보이지 않는 글자는 `Char(코드)` 로 짓는다(소스에 날것으로 두지 않는다 — CLAUDE.md 함정 '도구가 푸는 이스케이프'). */
class BackupRulesTest {

    private fun ch(code: Int) = Char(code).toString()

    @Test
    fun 주소는_http와_https만() {
        assertEquals("https://example.com/a?b=1#c", BackupRules.webUrl("https://example.com/a?b=1#c"))
        assertEquals("http://example.com", BackupRules.webUrl("  http://example.com  "))
        // 스킴만 소문자로 — 나머지는 그대로.
        assertEquals("https://Example.COM/Path", BackupRules.webUrl("HTTPS://Example.COM/Path"))
        for (bad in listOf(
            "javascript:alert(1)", "JavaScript://example.com/%0aalert(1)", "data:text/html,<b>x</b>", "file:///sdcard/a.html",
            "intent://scan/#Intent;scheme=zxing;end", "about:blank", "place:sort=8&maxResults=10", "chrome://settings", "ftp://example.com/",
            "content://media/external/1", "blob:https://example.com/1", "", "   ", "example.com", "//example.com/", "https:example.com",
            "https:/example.com", "https://", "https:///path",
        )) {
            assertNull(BackupRules.webUrl(bad), bad)
        }
    }

    @Test
    fun 주소의_호스트와_포트() {
        assertEquals("http://192.168.0.1:8080/", BackupRules.webUrl("http://192.168.0.1:8080/"))
        assertEquals("http://[::1]:8080/x", BackupRules.webUrl("http://[::1]:8080/x"))
        assertEquals("https://nas/", BackupRules.webUrl("https://nas/"))
        assertEquals("https://xn--3e0b707e.kr/", BackupRules.webUrl("https://xn--3e0b707e.kr/"))
        assertEquals("https://한국.kr/", BackupRules.webUrl("https://한국.kr/"))
        assertEquals("https://example.com./", BackupRules.webUrl("https://example.com./"))
        assertEquals("https://example.com:/", BackupRules.webUrl("https://example.com:/"))
        for (bad in listOf(
            // 사용자 정보 — 속이는 주소의 흔한 꼴.
            "https://good.example@evil.example/", "https://user:pass@example.com/",
            "https://example.com:0/", "https://example.com:65536/", "https://example.com:8o/", "https://example.com:123456/",
            "http://[::1/", "http://[zz::1]/", "http://[::1]x/", "https://a:1:2/", "https://exa mple.com/", "https://ex%61mple.com/",
            "https://example..com/", "https://.example.com/", "https://" + "a".repeat(64) + ".com/",
            // 역슬래시 — 브라우저는 '/' 로 읽는다(evil.example 이 호스트가 된다).
            "https://evil.example\\@good.example/",
        )) {
            assertNull(BackupRules.webUrl(bad), bad)
        }
    }

    @Test
    fun 주소의_보이지_않는_글자와_길이() {
        for (bad in listOf(
            "https://example.com/a" + ch(0x202E) + "gpj.exe", "https://example.com/" + ch(0x200B), "https://example.com/" + ch(0xFEFF),
            "https://example.com/a\tb", "https://example.com/a\nb", "https://example.com/" + ch(0x7F), "https://example.com/" + ch(0x85),
            "https://example.com/a" + ch(0xA0) + "b", "https://example.com/" + ch(0xD800), "https://example.com/" + ch(0xDC00) + "x",
        )) {
            assertNull(BackupRules.webUrl(bad), bad.map { it.code }.toString())
        }
        // 앞뒤의 빈칸(NBSP 포함)은 자른다.
        assertEquals("https://example.com/", BackupRules.webUrl(ch(0xA0) + "https://example.com/" + ch(0x3000)))
        // 짝 맞는 서로게이트(이모지)는 받는다.
        val emoji = "https://example.com/" + ch(0xD83D) + ch(0xDE00)
        assertEquals(emoji, BackupRules.webUrl(emoji))
        val long = "https://example.com/" + "a".repeat(BackupRules.MAX_URL_CHARS)
        assertNull(BackupRules.webUrl(long))
        val fits = "https://example.com/" + "a".repeat(BackupRules.MAX_URL_CHARS - 20)
        assertEquals(fits, BackupRules.webUrl(fits))
    }

    @Test
    fun 설정의_주소는_설정_화면처럼_앞머리만() {
        assertEquals("https://cloudflare.com/cdn-cgi/trace", BackupRules.settingsUrl(" https://cloudflare.com/cdn-cgi/trace "))
        assertEquals("http://10.0.0.1/list.txt", BackupRules.settingsUrl("http://10.0.0.1/list.txt"))
        for (bad in listOf("ftp://x/", "HTTPS://example.com/", "javascript:x", "https://example.com/a b", "https://example.com/" + ch(0x202E), "")) {
            assertNull(BackupRules.settingsUrl(bad), bad)
        }
    }

    @Test
    fun 제목_다듬기() {
        assertEquals("가 나 다", BackupRules.cleanTitle("  가\n\t나\r\n" + ch(0x2028) + "다  "))
        assertEquals("ab", BackupRules.cleanTitle("a" + ch(0x202E) + ch(0x2066) + ch(0x200E) + ch(0x200B) + ch(0xFEFF) + ch(0xAD) + "b"))
        assertEquals("a b", BackupRules.cleanTitle("a" + ch(0) + ch(0x1F) + ch(0x7F) + ch(0x85) + ch(0xA0) + ch(0x3000) + "b"))
        // 이모지를 잇는 글자는 둔다.
        val family = ch(0xD83D) + ch(0xDC68) + ch(0x200D) + ch(0xD83D) + ch(0xDC67)
        assertEquals(family, BackupRules.cleanTitle(family))
        // 짝 없는 서로게이트는 U+FFFD.
        assertEquals("a" + ch(0xFFFD) + "b", BackupRules.cleanTitle("a" + ch(0xD800) + "b"))
        assertEquals("", BackupRules.cleanTitle(" \n "))
    }

    @Test
    fun 제목의_상한은_서로게이트_쌍을_가르지_않는다() {
        val max = BackupRules.MAX_TITLE_CHARS
        assertEquals(max, BackupRules.cleanTitle("가".repeat(max + 50)).length)
        val emoji = ch(0xD83D) + ch(0xDE00)
        val odd = "a" + emoji.repeat(max)
        val cut = BackupRules.cleanTitle(odd)
        assertEquals(max - 1, cut.length)
        assertTrue(!Character.isHighSurrogate(cut.last()))
        // 다시 다듬어도 같다.
        for (t in listOf(odd, "  a  b  ", "x".repeat(max) + " y", "a" + ch(0xD800))) {
            val once = BackupRules.cleanTitle(t)
            assertEquals(once, BackupRules.cleanTitle(once))
        }
    }

    @Test
    fun 사이트는_등록_도메인으로() {
        assertEquals("example.com", BackupRules.site(" WWW.Example.COM. "))
        assertEquals("example.co.kr", BackupRules.site("m.example.co.kr"))
        assertEquals("10.0.0.1", BackupRules.site("10.0.0.1"))
        assertEquals("nas", BackupRules.site("nas"))
        for (bad in listOf("", "a b.com", "exa\$mple.com", "*.example.com", ".example.com", "example.com/x", "a,b.com")) {
            assertNull(BackupRules.site(bad), bad)
        }
    }

    @Test
    fun 숨긴_요소는_저장_파일과_같은_검사() {
        assertEquals(HideRule("example.com", "div.ad"), BackupRules.hideRule(" Example.com ", " div.ad "))
        assertNull(BackupRules.hideRule("example.com", "div{}body{display:none}"))
        assertNull(BackupRules.hideRule("example.com", "div[x"))
        assertNull(BackupRules.hideRule("example.com", "div;"))
        assertNull(BackupRules.hideRule("exa mple.com", "div"))
        assertNull(BackupRules.hideRule(".example.com", "div"))
    }

    @Test
    fun 예외_규칙은_설정_화면의_칸과_같다() {
        assertEquals("*.example.com", BackupRules.bypassHost(".Example.com"))
        assertEquals("10.0.0.0/8", BackupRules.bypassCidr("10.0.0.0/8"))
        assertEquals("192.168.1.1/32", BackupRules.bypassCidr("192.168.1.1"))
        // 앞의 0 — 크로미움은 8진수로 읽는다(공인 대역이 된다).
        assertNull(BackupRules.bypassCidr("010.0.0.0/8"))
        assertNull(BackupRules.bypassHost("010.0.0.1"))
        assertNull(BackupRules.bypassHost("*"))
        assertNull(BackupRules.bypassCidr("10.0.0.0/0"))
    }

    @Test
    fun 그_밖의_칸() {
        assertEquals("duckduckgo", BackupRules.searchEngine("duckduckgo"))
        assertNull(BackupRules.searchEngine("yahoo"))
        assertEquals("ubo-filters", BackupRules.listId("ubo-filters"))
        assertNull(BackupRules.listId("Easy List"))
        assertNull(BackupRules.listId(""))
        assertEquals("proxy.example.com", BackupRules.proxyHost(" proxy.example.com "))
        for (bad in listOf("", "https://proxy.example.com", "proxy.example.com:10443", "a b")) assertNull(BackupRules.proxyHost(bad), bad)
        assertEquals("[::1]", BackupRules.proxyHost("[::1]"))
        assertEquals(10443, BackupRules.proxyPort(10443))
        assertNull(BackupRules.proxyPort(0))
        assertNull(BackupRules.proxyPort(65536))
        assertEquals("myproxyuser", BackupRules.proxyUser("myproxyuser"))
        assertEquals("", BackupRules.proxyUser(""))
        assertNull(BackupRules.proxyUser("a\r\nProxy-Authorization: x"))
        assertNull(BackupRules.proxyUser("u".repeat(BackupRules.MAX_USER_CHARS + 1)))
        val now = 1_800_000_000_000L
        assertEquals(now, BackupRules.time(now, now))
        assertNull(BackupRules.time(0, now))
        assertNull(BackupRules.time(-5, now))
        assertNull(BackupRules.time(now + 2 * 24 * 60 * 60 * 1000L, now))
        assertEquals("ab", BackupRules.clip("ab" + ch(0xD83D) + ch(0xDE00), 3))
    }

    @Test
    fun 살아_있는_탭의_수는_설정이_고르게_하는_범위만() {
        assertEquals(1, BackupRules.liveTabs(1))
        assertEquals(7, BackupRules.liveTabs(7)) // 고르는 값이 아니어도 범위 안이면 — 앱의 설정이 그대로 둔다
        assertEquals(20, BackupRules.liveTabs(20))
        assertNull(BackupRules.liveTabs(0))
        assertNull(BackupRules.liveTabs(21))
        assertNull(BackupRules.liveTabs(-1))
        assertNull(BackupRules.liveTabs(Long.MAX_VALUE))
    }

    @Test
    fun 보관_기간은_고르는_값만() {
        assertEquals(0, BackupRules.historyDays(0))
        assertEquals(365, BackupRules.historyDays(365))
        assertNull(BackupRules.historyDays(2))
        assertNull(BackupRules.historyDays(365L + (1L shl 32)))
        assertEquals(1, BackupRules.cacheDays(1))
        assertNull(BackupRules.cacheDays(90))
        assertNull(BackupRules.cacheDays(-1))
    }
}
