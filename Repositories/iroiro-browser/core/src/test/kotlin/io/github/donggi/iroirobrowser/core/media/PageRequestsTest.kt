package io.github.donggi.iroirobrowser.core.media

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 쪽이 낸 요청의 머리를 잠깐 쥐고 열쇠 요청에 다시 싣는 일. 주소·머리는 합성한 것이다(`*.test`).
 */
class PageRequestsTest {

    private val key = "https://keys.test/k/1.key"
    private val playlist = "https://cdn.test/v/index.m3u8"
    private val iframe = "https://embed.test/e/abc"

    private fun xhr(referer: String? = iframe, vararg extra: Pair<String, String>): Map<String, String> = buildMap {
        put("Accept", "*/*")
        referer?.let { put("Referer", it) }
        putAll(extra)
    }

    @Test
    fun 쥐지_않는_머리() {
        val crlf = "a" + Char(13) + Char(10) + "Set-Cookie: x"
        val kept = PageRequests.select(
            mapOf(
                "Cookie" to "s=1", "Host" to "keys.test", "Content-Length" to "0", "Range" to "bytes=0-", "If-None-Match" to "x",
                "Accept-Encoding" to "gzip, br", "User-Agent" to "UA", "X-Requested-With" to "", "Origin" to "https://embed.test",
                "Referer" to iframe, "Connection" to "keep-alive", "Transfer-Encoding" to "chunked", "Proxy-Authorization" to "Basic x",
                "Authorization" to "Bearer t0k3n", "X-Token" to "abc", "Accept" to "*/*", "sec-ch-ua" to "Chromium;v=141",
                "Bad Name" to "1", "X-Inject" to crlf, "X-Hangul" to "토큰",
            ),
        )
        assertEquals(listOf("Authorization", "X-Token", "Accept", "sec-ch-ua"), kept.keys.toList())
        assertEquals("Bearer t0k3n", kept["Authorization"])
        // 이름은 대소문자를 가리지 않고 거른다.
        assertTrue(PageRequests.select(mapOf("COOKIE" to "a", "accept-ENCODING" to "br")).isEmpty())
        // 값은 4 KB, 머리는 24개까지.
        assertTrue(PageRequests.select(mapOf("X-Big" to "x".repeat(PageRequests.MAX_VALUE + 1))).isEmpty())
        assertEquals(PageRequests.MAX_HEADERS, PageRequests.select((1..40).associate { "X-H$it" to "v" }).size)
    }

    @Test
    fun 쥘_요청만_쥔다() {
        assertTrue(PageRequests.shouldRecord(key, "GET", "*/*"))
        assertTrue(PageRequests.shouldRecord("https://keys.test/api/key?id=1", null, null))
        assertTrue(PageRequests.shouldRecord(playlist, "GET", "*/*"))
        assertFalse(PageRequests.shouldRecord(key, "POST", "*/*"), "본문이 있는 요청은 다시 낼 수 없다")
        assertFalse(PageRequests.shouldRecord("https://img.test/a", "GET", "image/avif,image/webp,*/*"))
        assertFalse(PageRequests.shouldRecord("https://frame.test/p", "GET", "text/html,application/xhtml+xml"))
        assertFalse(PageRequests.shouldRecord("https://cdn.test/v/seg42.ts", "GET", "*/*"), "조각은 쥐지 않는다")
        assertFalse(PageRequests.shouldRecord("https://cdn.test/a.js?v=1", "GET", "*/*"))
        assertFalse(PageRequests.shouldRecord("blob:https://site.test/1", "GET", null))
        assertFalse(PageRequests.shouldRecord("https://keys.test/" + "a".repeat(PageRequests.MAX_URL), "GET", null))
    }

    @Test
    fun 열쇠_주소_그대로면_쪽의_머리를_싣는다() {
        val log = PageRequests()
        log.record("https://KEYS.test:443/k/1.key#x", "GET", xhr(iframe, "Authorization" to "Bearer t", "Cookie" to "s=1"), 1_000)
        val r = assertNotNull(log.replayFor(key, playlist, 2_000))
        assertEquals(PageRequests.Match.EXACT, r.match)
        assertEquals(key, r.url)
        assertEquals(iframe, r.referer)
        // 다른 출처로의 XHR 이었다 — Origin 은 Referer 의 출처.
        assertEquals("https://embed.test", r.origin)
        val headers = r.headersFor(key)
        assertEquals("Bearer t", headers["Authorization"])
        assertEquals("https://embed.test", headers["Origin"])
        assertNull(headers["Cookie"])
        assertEquals(2, r.headerCount)
    }

    @Test
    fun 스크립트의_머리는_같은_출처에만() {
        val log = PageRequests()
        log.record(key, "GET", xhr(iframe, "Authorization" to "Bearer t"), 1_000)
        val r = assertNotNull(log.replayFor(key, null, 1_000))
        // 넘겨주기로 다른 호스트에 가면 인증 머리를 싣지 않는다(Origin 은 남는다).
        val elsewhere = r.headersFor("https://cdn2.test/k/1.key")
        assertNull(elsewhere["Authorization"])
        assertEquals("https://embed.test", elsewhere["Origin"])
        assertEquals("Bearer t", r.headersFor("https://keys.test/other")["Authorization"])
    }

    @Test
    fun Origin_은_다른_출처일_때만() {
        val log = PageRequests()
        log.record(key, "GET", xhr("https://keys.test/player.html"), 1_000)
        assertNull(log.replayFor(key, null, 1_000)!!.origin, "같은 출처의 XHR 에는 Origin 이 없다")
        val none = PageRequests()
        none.record(key, "GET", emptyMap(), 1_000)
        val r = none.replayFor(key, null, 1_000)!!
        assertNull(r.referer, "쪽이 Referer 를 보내지 않았으면 보내지 않는다")
        assertNull(r.origin)
        assertTrue(r.headersFor(key).isEmpty())
    }

    @Test
    fun 쪽이_질의를_더했으면_그_주소로() {
        val log = PageRequests()
        log.record("$key?id=1&t=abc", "GET", xhr(), 1_000)
        val r = log.replayFor("$key?id=1", null, 1_000)!!
        assertEquals(PageRequests.Match.PAGE_QUERY, r.match)
        assertEquals("$key?id=1&t=abc", r.url)
        // 질의가 없는 열쇠 주소에 쪽이 토큰을 붙였다.
        assertEquals("$key?id=1&t=abc", log.replayFor(key, null, 1_000)!!.url)
        // 같은 길이지만 우리 질의를 담지 않은 것(다른 열쇠) — 우리 주소에 머리만.
        val other = log.replayFor("$key?id=2", null, 1_000)!!
        assertEquals(PageRequests.Match.SAME_PATH, other.match)
        assertEquals("$key?id=2", other.url)
        // 더한 주소가 둘이면 어느 것인지 모른다 — 우리 주소로.
        log.record("$key?id=1&t=def", "GET", xhr(), 1_100)
        val ambiguous = log.replayFor("$key?id=1", null, 1_200)!!
        assertEquals(PageRequests.Match.SAME_PATH, ambiguous.match)
        assertEquals("$key?id=1", ambiguous.url)
    }

    @Test
    fun 열쇠_요청이_없으면_재생목록의_Referer_만() {
        val log = PageRequests()
        log.record(playlist, "GET", xhr(iframe, "X-Token" to "abc"), 1_000)
        val r = log.replayFor(key, playlist, 1_000)!!
        assertEquals(PageRequests.Match.PLAYLIST, r.match)
        assertEquals(key, r.url)
        assertEquals(iframe, r.referer)
        assertEquals(mapOf("Origin" to "https://embed.test"), r.headersFor(key))
        assertNull(log.replayFor(key, null, 1_000))
        assertNull(log.replayFor(key, "https://other.test/x.m3u8", 1_000))
    }

    @Test
    fun 다른_사이트로_가면_버린다() {
        val log = PageRequests()
        log.committed("https://www.site.test/watch/1", null)
        log.record(key, "GET", xhr(), 1_000)
        // 같은 사이트(등록 도메인)의 다른 쪽 — 남긴다.
        log.committed("https://m.site.test/watch/2", 2_000)
        assertEquals(1, log.size)
        // 다른 사이트 — 그 탐색이 시작되기 전에 적은 것은 버리고, 시작된 뒤(새 문서의 것)는 남긴다.
        log.record(playlist, "GET", xhr(), 3_500)
        log.committed("https://other.test/", 3_000)
        assertEquals(1, log.size)
        assertNull(log.replayFor(key, null, 3_600))
        assertNotNull(log.replayFor(playlist, null, 3_600))
        // 시작을 모르면 모두.
        log.committed("https://third.test/", null)
        assertEquals(0, log.size)
    }

    @Test
    fun 탭을_닫으면_버린다() {
        val log = PageRequests()
        log.record(key, "GET", xhr(), 1_000)
        log.clear()
        assertEquals(0, log.size)
        assertNull(log.replayFor(key, playlist, 1_000))
    }

    @Test
    fun 상한과_시간() {
        val log = PageRequests(capacity = 3, maxAgeMs = 10_000)
        log.record("https://a.test/1", "GET", xhr(), 1)
        log.record("https://a.test/2", "GET", xhr(), 2)
        log.record("https://a.test/1", "GET", xhr("https://new.test/"), 3)
        log.record("https://a.test/3", "GET", xhr(), 4)
        log.record("https://a.test/4", "GET", xhr(), 5)
        // 가장 오래된 /2 가 밀려났다 — 다시 적은 /1 은 새것이다.
        assertEquals(3, log.size)
        assertNull(log.replayFor("https://a.test/2", null, 6))
        assertEquals("https://new.test/", log.replayFor("https://a.test/1", null, 6)!!.referer)
        // 오래된 것은 묻는 때 버린다.
        assertNull(log.replayFor("https://a.test/4", null, 5 + 10_001))
        assertEquals(0, log.size)
    }

    @Test
    fun 글자_수에도_상한() {
        val log = PageRequests(capacity = 100, maxAgeMs = 60_000, maxChars = 10_000)
        val big = "v".repeat(4_000)
        log.record("https://a.test/1", "GET", xhr(iframe, "X-Big" to big), 1)
        log.record("https://a.test/2", "GET", xhr(iframe, "X-Big" to big), 2)
        assertEquals(2, log.size)
        // 셋이면 1만 글자를 넘는다 — 가장 오래된 /1 이 밀려났다.
        log.record("https://a.test/3", "GET", xhr(iframe, "X-Big" to big), 3)
        assertEquals(2, log.size)
        assertNull(log.replayFor("https://a.test/1", null, 4))
        assertNotNull(log.replayFor("https://a.test/3", null, 4))
        // 같은 주소를 다시 적으면 앞의 것을 빼고 센다(두 번 세지 않는다).
        repeat(10) { log.record("https://a.test/3", "GET", xhr(iframe, "X-Big" to big), 5L + it) }
        assertEquals(2, log.size)
        // 기본 상한 — 머리를 꽉 채운 요청(24개 × 4 KB, 10만 글자 남짓)은 128개가 아니라 다섯까지만.
        val full = PageRequests()
        val headers = (1..PageRequests.MAX_HEADERS).associate { "X-H$it" to "w".repeat(PageRequests.MAX_VALUE) }
        repeat(PageRequests.CAPACITY) { full.record("https://b.test/$it", "GET", headers, it.toLong()) }
        assertEquals(5, full.size)
    }

    @Test
    fun 여러_스레드가_함께_쓴다() {
        val log = PageRequests(capacity = 64)
        val failure = AtomicReference<Throwable?>(null)
        val start = CountDownLatch(1)
        val threads = (0 until 8).map { t ->
            Thread {
                try {
                    start.await()
                    for (i in 0 until 2_000) {
                        log.record("https://k$t.test/k/$i.key", "GET", xhr(), i.toLong())
                        log.replayFor("https://k$t.test/k/${i / 2}.key", playlist, i.toLong())
                        if (i % 500 == 0) log.committed("https://s$t.test/", i.toLong())
                    }
                } catch (e: Throwable) {
                    failure.set(e)
                }
            }.apply { start() }
        }
        start.countDown()
        threads.forEach { it.join() }
        assertNull(failure.get())
        assertTrue(log.size <= 64)
    }
}
