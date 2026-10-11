package io.github.donggi.iroirobrowser.core.adblock

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdBlockerTest {

    private fun blocker(vararg lines: String) = AdBlocker(listOf(FilterParser.parse(lines.joinToString("\n"))))

    private fun AdBlocker.blocks(url: String, page: String = "https://site.test/", types: Int = RequestType.SCRIPT): Boolean =
        shouldBlock(url, page(page), types) != null

    private fun pattern(p: String, url: String): Boolean {
        val u = url.lowercase()
        val r = Request.hostRange(u)
        return UrlPattern.parse(p)!!.matches(u, r.first, r.last + 1)
    }

    // ── 무늬 ──

    @Test
    fun 호스트_앵커는_이름_경계에서만() {
        assertTrue(pattern("||example.com/ads/", "https://example.com/ads/x.js"))
        assertTrue(pattern("||example.com/ads/", "https://sub.example.com/ads/"))
        assertFalse(pattern("||example.com/ads/", "https://notexample.com/ads/"))
        assertFalse(pattern("||example.com/ads/", "https://example.com.evil.test/ads/"))
        assertFalse(pattern("||example.com/ads/", "https://other.test/?u=example.com/ads/"))
    }

    @Test
    fun 구분_글자와_끝() {
        assertTrue(pattern("/banner/*/img^", "http://example.com/banner/foo/img"))
        assertTrue(pattern("/banner/*/img^", "http://example.com/banner/foo/img?x"))
        assertFalse(pattern("/banner/*/img^", "http://example.com/banner/foo/imgx"))
        assertTrue(pattern("^ad^", "http://x.test/ad/b"))
        assertTrue(pattern("^ad^", "http://x.test/p?ad=1"))
        assertFalse(pattern("^ad^", "http://x.test/head/b"))
    }

    @Test
    fun 처음과_끝의_앵커() {
        assertTrue(pattern("|https://ads.", "https://ads.example.com/"))
        assertFalse(pattern("|https://ads.", "http://x.test/https://ads."))
        assertTrue(pattern("swf|", "http://x.test/a.swf"))
        assertFalse(pattern("swf|", "http://x.test/a.swf?x"))
        assertTrue(pattern("/ad.js|", "http://x.test/ad.js?no/ad.js"))
    }

    @Test
    fun 별과_대소문자() {
        assertTrue(pattern("*", "http://x.test/"))
        assertTrue(pattern("/ads/*.gif", "http://x.test/ads/a/b.gif"))
        assertTrue(pattern("||Example.com/ADS", "https://example.com/ads"))
    }

    // ── 규칙과 선택지 ──

    @Test
    fun 호스트_규칙은_그_이름과_아래_이름() {
        val b = blocker("||ads.example.com^")
        assertTrue(b.blocks("https://ads.example.com/x.js"))
        assertTrue(b.blocks("https://a.ads.example.com/x.js"))
        assertTrue(b.blocks("https://ads.example.com:8443/x.js"))
        assertFalse(b.blocks("https://example.com/x.js"))
        assertFalse(b.blocks("https://ads.example.com.evil.test/x.js"))
    }

    @Test
    fun 제삼자() {
        val b = blocker("||tracker.test^\$third-party")
        assertTrue(b.blocks("https://tracker.test/t.js", page = "https://news.test/"))
        assertFalse(b.blocks("https://tracker.test/t.js", page = "https://www.tracker.test/"))
        val first = blocker("||cdn.test/ads^\$~third-party")
        assertTrue(first.blocks("https://cdn.test/ads/x", page = "https://cdn.test/"))
        assertFalse(first.blocks("https://cdn.test/ads/x", page = "https://other.test/"))
    }

    @Test
    fun 종류() {
        val b = blocker("||cdn.test/ad.js\$script", "||img.test^\$image")
        assertTrue(b.blocks("https://cdn.test/ad.js", types = RequestType.SCRIPT))
        assertFalse(b.blocks("https://cdn.test/ad.js", types = RequestType.IMAGE))
        assertTrue(b.blocks("https://img.test/a.png", types = RequestType.IMAGE))
        assertFalse(b.blocks("https://img.test/a.css", types = RequestType.STYLESHEET))
        // 어림이 여러 종류를 돌려주면 종류를 좁힌 막는 규칙은 그 모두를 덮을 때만 걸린다 — 확실할 때만 막는다.
        assertFalse(b.blocks("https://cdn.test/ad.js", types = RequestType.SCRIPT or RequestType.XHR))
        assertTrue(blocker("||cdn.test/ad.js\$script,xmlhttprequest").blocks("https://cdn.test/ad.js", types = RequestType.SCRIPT or RequestType.XHR))
        assertTrue(blocker("||cdn.test/ad.js").blocks("https://cdn.test/ad.js", types = RequestType.SCRIPT or RequestType.XHR))
        val negated = blocker("||x.test^\$~image")
        assertFalse(negated.blocks("https://x.test/a", types = RequestType.IMAGE))
        assertTrue(negated.blocks("https://x.test/a", types = RequestType.SCRIPT))
        assertTrue(negated.blocks("https://x.test/a", types = RequestType.SCRIPT or RequestType.XHR))
        assertFalse(negated.blocks("https://x.test/a", types = RequestType.SCRIPT or RequestType.IMAGE))
    }

    @Test
    fun 종류를_모르는_요청은_좁힌_규칙이_막지_않는다() {
        // EasyPrivacy 의 `*$ping,third-party` — 겹치기만 해도 막던 앞 판은 종류를 모르는 모든 제3자 요청(측정 서버·영상 CDN)을 막았다.
        val ping = blocker("*\$ping,third-party")
        val oca = "https://ipv4-c001-x.1.oca.nflxvideo.net/speedtest/range/0-2048?c=us"
        assertFalse(ping.blocks(oca, page = "https://fast.com/", types = RequestType.guess(oca, "*/*", false, "GET")))
        // POST 는 XHR 이거나 핑 — 핑만 막는 규칙에는 확실하지 않고, 핑·XHR 을 함께 막는 규칙에는 확실하다.
        val post = RequestType.guess("https://collector.test/hit", "*/*", false, "POST")
        assertFalse(ping.blocks("https://collector.test/hit", page = "https://news.test/", types = post))
        val pingXhr = blocker("/api/collect|\$ping,xmlhttprequest")
        val collect = "https://site.test/api/collect"
        assertTrue(pingXhr.blocks(collect, types = RequestType.guess(collect, "*/*", false, "POST")))
        assertFalse(pingXhr.blocks(collect, types = RequestType.guess(collect, "*/*", false, "GET")))
        // 종류를 적지 않은 규칙은 종류를 몰라도 막는다.
        assertTrue(blocker("||ads.test^").blocks("https://ads.test/x", types = RequestType.guess("https://ads.test/x", "*/*", false)))
        // 예외는 하나라도 겹치면 걸린다 — 의심스러우면 통과시킨다.
        val ex = blocker("||cdn.test^", "@@||cdn.test/api^\$xmlhttprequest")
        assertFalse(ex.blocks("https://cdn.test/api/items", types = RequestType.guess("https://cdn.test/api/items", "*/*", false)))
        assertTrue(ex.blocks("https://cdn.test/api/lib.js", types = RequestType.guess("https://cdn.test/api/lib.js", "*/*", false)))
    }

    @Test
    fun 쪽의_도메인() {
        val b = blocker("/sponsor.js\$domain=a.test|~b.a.test")
        assertTrue(b.blocks("https://cdn.test/sponsor.js", page = "https://a.test/"))
        assertTrue(b.blocks("https://cdn.test/sponsor.js", page = "https://www.a.test/"))
        assertFalse(b.blocks("https://cdn.test/sponsor.js", page = "https://b.a.test/"))
        assertFalse(b.blocks("https://cdn.test/sponsor.js", page = "https://c.test/"))
    }

    @Test
    fun 예외_중요_badfilter() {
        val b = blocker("||ads.example.com^", "@@||ads.example.com/ok.js")
        assertTrue(b.blocks("https://ads.example.com/no.js"))
        assertFalse(b.blocks("https://ads.example.com/ok.js"))
        val important = blocker("||ads.example.com^\$important", "@@||ads.example.com^")
        assertTrue(important.blocks("https://ads.example.com/x.js"))
        val bad = blocker("||ads.example.com^", "||ads.example.com^\$badfilter")
        assertFalse(bad.blocks("https://ads.example.com/x.js"))
        assertEquals(0, bad.networkRuleCount)
    }

    @Test
    fun 쪽_단위_예외() {
        val b = blocker("||ads.test^", "@@||trusted.test^\$document", "##.ad", "@@||plain.test^\$generichide", "plain.test##.own", "@@||bare.test^\$elemhide")
        assertTrue(b.blocks("https://ads.test/x.js", page = "https://news.test/"))
        assertFalse(b.blocks("https://ads.test/x.js", page = "https://www.trusted.test/a"))
        // `$document` 예외는 그 쪽의 요청을 풀 뿐, 다른 쪽에서 그 호스트로 가는 요청을 풀지 않는다.
        assertTrue(b.page("https://news.test/").let { !it.allowAll })
        val classes = listOf("ad", "own")
        assertEquals("", b.startCss(b.page("https://trusted.test/")) + b.genericCss(b.page("https://trusted.test/"), classes, emptyList()))
        assertTrue(b.startCss(b.page("https://plain.test/")).contains(".own"))
        assertEquals("", b.genericCss(b.page("https://plain.test/"), classes, emptyList()))
        assertEquals("", b.startCss(b.page("https://bare.test/")) + b.genericCss(b.page("https://bare.test/"), classes, emptyList()))
        assertTrue(b.genericCss(b.page("https://news.test/"), classes, emptyList()).contains(".ad{display:none!important}"))
    }

    @Test
    fun hosts_꼴과_정규식() {
        val b = blocker("0.0.0.0 ads.hosts.test", "127.0.0.1 localhost", "/ads[0-9]+\\.js/")
        assertTrue(b.blocks("https://ads.hosts.test/a"))
        assertFalse(b.blocks("http://localhost/a"))
        assertTrue(b.blocks("https://x.test/ads12.js"))
        assertFalse(b.blocks("https://x.test/adsx.js"))
    }

    @Test
    fun 쓸_수_없는_규칙은_건너뛴다() {
        val parsed = FilterParser.parse(
            listOf(
                "||x.test^\$csp=script-src 'none'",
                "||x.test^\$removeparam=utm",
                "||popup.test^\$popup",
                "||doc.test^\$document",
                "example.test##+js(nowebrtc)",
                "##.ad:has-text(광고)",
                "example.test#%#window.x=1",
                "example.test##^script:has-text(ad)",
                "! 주석",
                "[Adblock Plus 2.0]",
            ).joinToString("\n"),
        )
        assertTrue(parsed.network.isEmpty(), parsed.network.joinToString())
        assertTrue(parsed.cosmetic.isEmpty())
        assertEquals(8, parsed.skipped)
    }

    @Test
    fun 막고_바꿔_주기는_막기만() {
        val b = blocker("||ads.test/ad.js\$script,redirect=noop.js")
        assertTrue(b.blocks("https://ads.test/ad.js"))
    }

    @Test
    fun 요소_숨김() {
        val b = blocker(
            "##.ad-banner", "example.test##.sponsored", "example.test#@#.ad-banner", "~foo.test##.x", "#@#.never", "##.never",
            "##a[href^=\"https://ad.\"]", "##div:not(.keep)",
        )
        val classes = listOf("ad-banner", "x", "never", "sponsored")
        fun all(host: String) = b.page("https://$host/").let { b.startCss(it) + b.genericCss(it, classes, emptyList()) }
        val onExample = all("www.example.test")
        assertTrue(onExample.contains(".sponsored{"))
        assertFalse(onExample.contains(".ad-banner{"))
        val onOther = all("other.test")
        assertTrue(onOther.contains(".ad-banner{"))
        assertFalse(onOther.contains(".sponsored{"))
        assertTrue(onOther.contains(".x{"))
        assertFalse(all("foo.test").contains(".x{"))
        assertFalse(onOther.contains(".never{"))
        // 열쇠 없는 일반 규칙은 쪽을 열 때 바로, 열쇠 있는 것은 쪽에 그 이름이 있을 때만.
        val start = b.startCss(b.page("https://other.test/"))
        assertTrue(start.contains("a[href^=\"https://ad.\"]{"))
        assertTrue(start.contains("div:not(.keep){"))
        assertFalse(start.contains(".ad-banner{"))
        assertEquals("", b.genericCss(b.page("https://other.test/"), listOf("nothing"), emptyList()))
    }

    @Test
    fun 선택자의_열쇠() {
        assertEquals(".ad-banner", CosmeticEngine.keyOf(".ad-banner"))
        assertEquals("#top_ad", CosmeticEngine.keyOf("div#top_ad > span"))
        assertEquals(".a", CosmeticEngine.keyOf("[data-x=\".y\"] .a"))
        assertNull(CosmeticEngine.keyOf("a[href*=\".ads.\"]"))
        assertNull(CosmeticEngine.keyOf("div:not(.keep)"))
        assertNull(CosmeticEngine.keyOf(".a\\:b"))
    }

    // ── 도구 ──

    @Test
    fun 종류_어림() {
        assertEquals(RequestType.DOCUMENT, RequestType.guess("https://x.test/", "text/html", isMainFrame = true))
        assertEquals(RequestType.SUBDOCUMENT, RequestType.guess("https://x.test/f", "text/html,application/xhtml+xml", false))
        assertEquals(RequestType.STYLESHEET, RequestType.guess("https://x.test/a", "text/css,*/*;q=0.1", false))
        assertEquals(RequestType.IMAGE, RequestType.guess("https://x.test/a", "image/avif,image/webp,*/*", false))
        assertEquals(RequestType.SCRIPT, RequestType.guess("https://x.test/a.js?v=1", "*/*", false))
        assertEquals(RequestType.FONT, RequestType.guess("https://x.test/f.woff2", "*/*", false))
        val unknown = RequestType.guess("https://x.test/api", "*/*", false)
        assertTrue(unknown and RequestType.SCRIPT != 0 && unknown and RequestType.XHR != 0 && unknown and RequestType.IMAGE == 0)
        // 핑은 언제나 POST 다 — GET 은 핑이 아니고, POST 는 XHR 이거나 핑이다(확장자를 보지 않는다).
        assertEquals(0, unknown and RequestType.PING)
        assertEquals(RequestType.XHR or RequestType.PING, RequestType.guess("https://x.test/a.js", "*/*", false, "POST"))
        assertEquals(RequestType.SUBDOCUMENT, RequestType.guess("https://x.test/form", "text/html", false, "POST"))
        assertEquals(RequestType.MEDIA, RequestType.guess("https://x.test/seg1.ts", "*/*", false))
    }

    @Test
    fun 등록_도메인_어림() {
        assertEquals("example.co.kr", Domains.registrable("www.example.co.kr"))
        assertEquals("example.com", Domains.registrable("a.b.example.com"))
        assertEquals("user.github.io", Domains.registrable("user.github.io"))
        assertEquals("10.0.2.2", Domains.registrable("10.0.2.2"))
        assertTrue(Domains.isThirdParty("cdn.other.com", "www.example.com"))
        assertFalse(Domains.isThirdParty("img.example.com", "www.example.com"))
        assertEquals(listOf("a.b.example.com", "b.example.com", "example.com", "com"), Domains.suffixes("a.b.example.com"))
    }

    @Test
    fun 주소의_호스트_자리() {
        assertEquals("example.com", Request.hostOf("https://user:pw@Example.com:8443/a?b#c"))
        assertEquals("[::1]", Request.hostOf("http://[::1]:8080/x"))
        assertEquals("", Request.hostOf("data:text/plain,x"))
    }

    @Test
    fun 막은_규칙을_돌려준다() {
        val b = blocker("||ads.test^")
        assertNotNull(b.shouldBlock("https://ads.test/x", b.page("https://n.test/"), RequestType.IMAGE))
        assertNull(b.shouldBlock("https://ok.test/x", b.page("https://n.test/"), RequestType.IMAGE))
        // 쪽을 모르면(앱이 아직 주 문서를 보지 못했다) 도메인 조건 없는 규칙만 걸린다.
        assertNotNull(b.shouldBlock("https://ads.test/x", null, RequestType.IMAGE))
    }
}
