package io.github.donggi.iroirobrowser.core

import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/** 상대 주소 풀기(RFC 3986 5.2) — 브라우저·hls.js 와 같은 꼴인가. 시험 주소는 RFC 의 보기와 합성한 것만. */
class UriRefsTest {

    private val base = "http://a/b/c/d;p?q"

    @Test
    fun RFC3986_보통의_보기() {
        // RFC 3986 5.4.1 그대로.
        val cases = listOf(
            "g:h" to "g:h",
            "g" to "http://a/b/c/g",
            "./g" to "http://a/b/c/g",
            "g/" to "http://a/b/c/g/",
            "/g" to "http://a/g",
            "//g" to "http://g",
            "?y" to "http://a/b/c/d;p?y",
            "g?y" to "http://a/b/c/g?y",
            "#s" to "http://a/b/c/d;p?q#s",
            "g#s" to "http://a/b/c/g#s",
            "g?y#s" to "http://a/b/c/g?y#s",
            ";x" to "http://a/b/c/;x",
            "g;x" to "http://a/b/c/g;x",
            "g;x?y#s" to "http://a/b/c/g;x?y#s",
            "" to "http://a/b/c/d;p?q",
            "." to "http://a/b/c/",
            "./" to "http://a/b/c/",
            ".." to "http://a/b/",
            "../" to "http://a/b/",
            "../g" to "http://a/b/g",
            "../.." to "http://a/",
            "../../" to "http://a/",
            "../../g" to "http://a/g",
        )
        for ((ref, want) in cases) assertEquals(want, UriRefs.resolve(base, ref), "참조 '$ref'")
    }

    @Test
    fun RFC3986_어긋난_보기() {
        // RFC 3986 5.4.2 그대로(엄격한 해석기 — `http:g` 는 스킴이 있는 주소다).
        val cases = listOf(
            "../../../g" to "http://a/g",
            "../../../../g" to "http://a/g",
            "/./g" to "http://a/g",
            "/../g" to "http://a/g",
            "g." to "http://a/b/c/g.",
            ".g" to "http://a/b/c/.g",
            "g.." to "http://a/b/c/g..",
            "..g" to "http://a/b/c/..g",
            "./../g" to "http://a/b/g",
            "./g/." to "http://a/b/c/g/",
            "g/./h" to "http://a/b/c/g/h",
            "g/../h" to "http://a/b/c/h",
            "g;x=1/./y" to "http://a/b/c/g;x=1/y",
            "g;x=1/../y" to "http://a/b/c/y",
            "g?y/./x" to "http://a/b/c/g?y/./x",
            "g?y/../x" to "http://a/b/c/g?y/../x",
            "g#s/./x" to "http://a/b/c/g#s/./x",
            "g#s/../x" to "http://a/b/c/g#s/../x",
            "http:g" to "http:g",
        )
        for ((ref, want) in cases) assertEquals(want, UriRefs.resolve(base, ref), "참조 '$ref'")
    }

    @Test
    fun 질의만_있는_참조는_재생목록_이름을_지킨다() {
        // 열쇠 주소가 `URI="?k=1"` 꼴이면 브라우저는 재생목록의 이름에 질의만 바꾼다. java.net.URI(RFC 2396)는 이름을 뗀다.
        val playlist = "https://cdn.test/v/x7/index.m3u8?sig=1"
        assertEquals("https://cdn.test/v/x7/index.m3u8?k=1", UriRefs.resolve(playlist, "?k=1"))
        assertNotEquals(URI(playlist).resolve(URI("?k=1")).toString(), UriRefs.resolve(playlist, "?k=1"))
    }

    @Test
    fun 길이_빈_바탕과_URI_가_받지_않는_글자() {
        // 바탕의 길이 비면 빗금을 넣는다(java.net.URI 는 `http://cdn.testkey.bin` 을 만든다).
        assertEquals("http://cdn.test/key.bin", UriRefs.resolve("http://cdn.test", "key.bin"))
        // java.net.URI 가 받지 않는 글자가 든 상대 주소도 푼다.
        assertEquals("https://cdn.test/v/key|1.bin", UriRefs.resolve("https://cdn.test/v/index.m3u8", "key|1.bin"))
        assertEquals("https://cdn.test/v/a%20b/seg{1}.ts", UriRefs.resolve("https://cdn.test/v/index.m3u8", "a%20b/seg{1}.ts"))
        // 스킴처럼 생겼으나 스킴의 글자가 아닌 앞머리는 길이다.
        assertEquals("https://cdn.test/v/1080p:seg.ts", UriRefs.resolve("https://cdn.test/v/index.m3u8", "1080p:seg.ts"))
        // 스킴이 다른 절대 주소는 그대로(점 마디만 걷는다).
        assertEquals("data:application/octet-stream;base64,AAAA", UriRefs.resolve("https://cdn.test/v/i.m3u8", "data:application/octet-stream;base64,AAAA"))
        assertEquals("skd://key-id", UriRefs.resolve("https://cdn.test/v/i.m3u8", "skd://key-id"))
        // 바탕에 스킴이 없으면 풀 바탕이 없다.
        assertEquals("key.bin", UriRefs.resolve("index.m3u8", "key.bin"))
    }

    @Test
    fun 출처() {
        assertEquals("https://cdn.test", UriRefs.origin("https://CDN.test:443/a/b?c#d"))
        assertEquals("http://cdn.test:8080", UriRefs.origin("http://user:pw@cdn.test:8080/x"))
        assertEquals("https://[::1]:8443", UriRefs.origin("https://[::1]:8443/k"))
        assertEquals("http://cdn.test", UriRefs.origin("HTTP://cdn.test"))
        assertNull(UriRefs.origin("ftp://cdn.test/x"))
        assertNull(UriRefs.origin("https:///x"))
        assertNull(UriRefs.origin("https://cdn.test:port/x"))
        assertNull(UriRefs.origin("/relative/only"))
    }
}
