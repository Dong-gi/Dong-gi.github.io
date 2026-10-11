package io.github.donggi.iroirobrowser.core

import io.github.donggi.iroirobrowser.core.Omnibox.Target
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OmniboxTest {

    private fun url(s: String) = Target.Url(s)
    private fun search(s: String) = Target.Search(s)

    @Test
    fun 스킴이_있는_주소는_그대로() {
        assertEquals(url("https://example.com/a?b=c#d"), Omnibox.classify("https://example.com/a?b=c#d"))
        assertEquals(url("http://10.0.2.2:8080/x"), Omnibox.classify("  http://10.0.2.2:8080/x  "))
        // 스킴은 소문자로 고르고, 호스트의 대소문자는 WebView 가 고른다.
        assertEquals(url("https://Example.com"), Omnibox.classify("HTTPS://Example.com"))
    }

    @Test
    fun 빗금이_빠진_스킴을_채운다() {
        assertEquals(url("https://example.com"), Omnibox.classify("https:example.com"))
        assertEquals(url("http://example.com/x"), Omnibox.classify("http:/example.com/x"))
    }

    @Test
    fun 호스트_없는_스킴은_검색어() {
        assertEquals(search("https://"), Omnibox.classify("https://"))
        assertEquals(search("http://?q"), Omnibox.classify("http://?q"))
    }

    @Test
    fun 자바스크립트는_돌리지_않는다() {
        assertEquals(search("javascript:alert(1)"), Omnibox.classify("javascript:alert(1)"))
        assertEquals(search("JavaScript:alert(1)"), Omnibox.classify("JavaScript:alert(1)"))
    }

    @Test
    fun 마그넷은_검색어가_아니라_토렌트() {
        // 앞 판은 검색어가 되어 info-hash·트래커가 검색 엔진에 갔다(사용자 결정 ⑳).
        val m = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&dn=a+b&tr=udp%3A%2F%2Ft.example%3A80"
        assertEquals(Target.Magnet(m), Omnibox.classify("  $m "))
        assertEquals(Target.Magnet("MAGNET:?xt=urn:btih:X"), Omnibox.classify("MAGNET:?xt=urn:btih:X"))
        // 빈칸이 든 이름(복사해 온 날 글)·깨진 마그넷도 검색으로 돌리지 않는다 — 받을지는 토렌트 쪽이 읽고 말한다.
        assertEquals(Target.Magnet("magnet:?xt=urn:btih:abc&dn=a b"), Omnibox.classify("magnet:?xt=urn:btih:abc&dn=a b"))
        assertEquals(Target.Magnet("magnet:"), Omnibox.classify("magnet:"))
        // 열 쪽이 없다 — resolve 는 주소를 만들지 않는다(검색 주소도).
        assertNull(Omnibox.resolve(m, SearchEngine.GOOGLE))
    }

    @Test
    fun about_과_data_는_주소() {
        assertEquals(url("about:blank"), Omnibox.classify("about:blank"))
        assertEquals(url("data:text/plain,hi"), Omnibox.classify("data:text/plain,hi"))
    }

    @Test
    fun 스킴_없는_도메인은_https() {
        assertEquals(url("https://naver.com"), Omnibox.classify("naver.com"))
        assertEquals(url("https://news.naver.com/main?x=1"), Omnibox.classify("news.naver.com/main?x=1"))
        assertEquals(url("https://example.com:8443/p"), Omnibox.classify("Example.COM:8443/p"))
        assertEquals(url("https://example.com"), Omnibox.classify("example.com."))
    }

    @Test
    fun 국제화_도메인은_퓨니코드() {
        assertEquals(url("https://xn--3e0b707e.kr"), Omnibox.classify("한국.kr"))
        assertEquals(url("https://" + java.net.IDN.toASCII("우리.한국")), Omnibox.classify("우리.한국"))
    }

    @Test
    fun 로컬과_IP_는_http() {
        assertEquals(url("http://localhost:3000/"), Omnibox.classify("localhost:3000/"))
        assertEquals(url("http://192.168.0.1"), Omnibox.classify("192.168.0.1"))
        assertEquals(url("http://[::1]:8080/x"), Omnibox.classify("[::1]:8080/x"))
    }

    @Test
    fun 검색어로_보는_것() {
        assertEquals(search("날씨"), Omnibox.classify("날씨"))
        assertEquals(search("kotlin coroutines"), Omnibox.classify("kotlin coroutines"))
        assertEquals(search("3.14"), Omnibox.classify("3.14"))
        assertEquals(search("1.2.3"), Omnibox.classify("1.2.3"))
        assertEquals(search("example.c"), Omnibox.classify("example.c"))
        assertEquals(search("user@example.com"), Omnibox.classify("user@example.com"))
        assertEquals(search("naver"), Omnibox.classify("naver"))
        assertEquals(search("a..b.com"), Omnibox.classify("a..b.com"))
        assertEquals(search("example.com:99999"), Omnibox.classify("example.com:99999"))
        assertEquals(search("mailto:x@y.z"), Omnibox.classify("mailto:x@y.z"))
        assertEquals(search("192.168.0.256"), Omnibox.classify("192.168.0.256"))
    }

    @Test
    fun 빈_글은_없다() {
        assertNull(Omnibox.classify("   "))
        assertNull(Omnibox.resolve("", SearchEngine.GOOGLE))
    }

    @Test
    fun 검색은_엔진의_주소로_부호화한다() {
        assertEquals("https://www.google.com/search?q=a+b%26c", Omnibox.resolve("a b&c", SearchEngine.GOOGLE))
        assertEquals("https://duckduckgo.com/?q=%ED%95%9C", Omnibox.resolve("한", SearchEngine.DUCKDUCKGO))
        assertEquals("https://search.naver.com/search.naver?query=x", Omnibox.resolve("x", SearchEngine.NAVER))
    }

    @Test
    fun 엔진은_이름으로_찾고_모르면_기본값() {
        assertEquals(SearchEngine.BING, SearchEngine.byId("bing"))
        assertEquals(SearchEngine.DEFAULT, SearchEngine.byId("nope"))
        assertEquals(SearchEngine.DEFAULT, SearchEngine.byId(null))
    }
}
