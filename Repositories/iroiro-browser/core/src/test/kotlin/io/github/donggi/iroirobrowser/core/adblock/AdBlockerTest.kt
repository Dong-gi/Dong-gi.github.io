package io.github.donggi.iroirobrowser.core.adblock

import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random
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

    // ── 시간 상한(2026-10-16 보안 점검 — 쪽이 지은 주소가 요청 스레드를 세우지 못하게) ──

    /** [block] 이 [ms] 안에 끝나는가 — 앞 판의 제곱·지수 시간이면 여기서 깨진다(그 스레드는 데몬이라 시험이 끝나면 함께 끝난다). */
    private fun within(ms: Long, block: () -> Unit) {
        var failure: Throwable? = null
        val t = Thread {
            try {
                block()
            } catch (e: Throwable) {
                failure = e
            }
        }.apply {
            isDaemon = true
            start()
        }
        t.join(ms)
        assertFalse(t.isAlive, "${ms}ms 안에 끝나지 않았다")
        failure?.let { throw it }
    }

    @Test
    fun 정규식_규칙은_그대로_맞는다() {
        val b = blocker("/^https?:\\/\\/ads\\d+\\.example\\//", "/banner[0-9]{3}\\.gif/\$image", "/\\/Track\\/[a-f0-9]{8}/\$match-case")
        assertTrue(b.blocks("https://ads12.example/x.js"))
        assertFalse(b.blocks("https://ads.example/x.js"))
        assertTrue(b.blocks("https://cdn.test/BANNER123.gif", types = RequestType.IMAGE), "대소문자를 가리지 않는다")
        assertFalse(b.blocks("https://cdn.test/banner12.gif", types = RequestType.IMAGE))
        assertTrue(b.blocks("https://t.test/Track/0123abcd"))
        assertFalse(b.blocks("https://t.test/track/0123abcd"), "\$match-case")
    }

    @Test
    fun 받지_않는_정규식_규칙은_건너뛴다() {
        // 앞뒤 보기·되돌아 가리키기·모둠의 교집합 — 되짚지 않는 엔진([ListRegex])이 받지 않는다. 목록 넷에는 없다(RealListsBenchmark).
        val parsed = FilterParser.parse(listOf("/ads(?=\\d)/", "/(a)\\1/", "/[a-z&&[^x]]/", "/ok\\d/").joinToString("\n"))
        assertEquals(listOf("/ok\\d/"), parsed.network.map { it.text })
        assertEquals(3, parsed.skipped)
    }

    @Test
    fun 되짚는_엔진에서_터지는_정규식도_긴_주소에서_곧바로_끝나고_켜진_채다() {
        // `(a+)+$` 는 앞 판(java.util.regex — 되짚는다)에서 이런 주소에 길이의 지수 시간이었다. 이 엔진은 길이에 비례하고 예산 안이라 끄지 않는다.
        val off = CopyOnWriteArrayList<String>()
        val b = AdBlocker(listOf(FilterParser.parse("/(a+)+\$/")), onRegexDisabled = { off += it.text })
        val long = "https://x.test/" + "a".repeat(100_000) + "!"
        within(3_000) { assertNull(b.shouldBlock(long, b.page("https://site.test/"), RequestType.SCRIPT)) }
        assertTrue(off.isEmpty())
        assertNotNull(b.shouldBlock("https://x.test/aaa", b.page("https://site.test/"), RequestType.SCRIPT), "켜진 채 맞는다")
    }

    @Test
    fun 예산을_넘은_정규식_규칙은_그_요청에_맞지_않고_꺼진다() {
        // 동시에 살아 있는 실이 많은 넓은 무늬 — 긴 주소에서 한 글자에 수백 걸음이다.
        val wide = "/^.*" + "(?:a?|b?)".repeat(40) + "y/"
        val off = CopyOnWriteArrayList<NetworkRule>()
        val b = AdBlocker(listOf(FilterParser.parse("$wide\n||ads.test^")), onRegexDisabled = { off += it })
        val page = b.page("https://site.test/")
        val long = "https://x.test/" + "x".repeat(50_000) + "y" // 맞는 주소지만 예산을 넘는다
        within(3_000) { assertNull(b.shouldBlock(long, page, RequestType.SCRIPT), "예산을 넘은 요청에는 맞지 않은 것으로 친다") }
        assertEquals(listOf(wide), off.map { it.text })
        assertTrue(off.single().regexDisabled)
        // 꺼진 뒤에는 짧은 주소에도 맞지 않고, 다시 알리지 않는다. 다른 규칙은 그대로다.
        assertNull(b.shouldBlock("https://x.test/y", page, RequestType.SCRIPT))
        assertNull(b.shouldBlock(long, page, RequestType.SCRIPT))
        assertEquals(1, off.size)
        assertNotNull(b.shouldBlock("https://ads.test/x.js", page, RequestType.SCRIPT))
        // 새 엔진(목록을 다시 받거나 켜고 끌 때 짓는다)에서는 되살아난다.
        val fresh = AdBlocker(listOf(FilterParser.parse(wide)))
        assertNotNull(fresh.shouldBlock("https://x.test/y", fresh.page("https://site.test/"), RequestType.SCRIPT))
    }

    @Test
    fun 첫_조각을_되풀이한_긴_주소도_곧바로_끝난다() {
        // 앞 판은 첫 조각이 맞는 자리마다 뒤의 조각을 주소 끝까지 찾아 길이의 제곱이었다(UrlPattern.matches). 뒤 조각의 머리 글자가 자주 나오게
        // 했다 — 드물면 JVM 의 indexOf(벡터 명령)가 워낙 빨라 이 PC 에서는 제곱이 3초 안에 숨는다(폰은 훨씬 느리다).
        val repeated = "https://x.test" + "/ads/".repeat(100_000)
        val range = Request.hostRange(repeated)
        within(3_000) { assertFalse(UrlPattern.parse("/ads/*/adx.gif")!!.matches(repeated, range.first, range.last + 1)) }
        assertTrue(UrlPattern.parse("/ads/*/adx.gif")!!.matches("$repeated/adx.gif", range.first, range.last + 1))
        // `||` — 첫 조각이 맞는 이름 경계가 수만 개인 호스트.
        val host = "https://" + "a.".repeat(100_000) + "test/" + "/a".repeat(50_000)
        val hostRange = Request.hostRange(host)
        within(3_000) { assertFalse(UrlPattern.parse("||a.*/ax.gif")!!.matches(host, hostRange.first, hostRange.last + 1)) }
        val short = "https://a.test/ax.gif"
        assertTrue(UrlPattern.parse("||a.*/ax.gif")!!.matches(short, Request.hostRange(short).first, Request.hostRange(short).last + 1))
    }

    @Test
    fun 같은_낱말을_되풀이한_긴_주소도_곧바로_끝난다() {
        // 앞 판은 낱말을 나오는 대로 모두 담아 되풀이마다 그 낱말의 규칙들을 다시 맞췄다(Request.tokens) — 규칙 하나를 맞추는 데 주소를 한 번
        // 훑으면(여기서는 `-` 마다 견준다) 길이의 제곱이다.
        val b = blocker("-banner/ad.gif", "||ads.test^")
        within(3_000) { assertFalse(b.blocks("https://x.test/" + "banner-".repeat(100_000))) }
        assertTrue(b.blocks("https://x.test/x-banner/ad.gif"))
    }

    @Test
    fun 이름이_많은_호스트도_곧바로_끝난다() {
        // 앞 판은 상위 이름을 모두 늘어놓아 글자가 호스트 길이의 제곱만큼 쌓였다(Domains.suffixes — 광고 차단의 호스트 표).
        val b = blocker("||ads.test^")
        val host = "a.".repeat(100_000) + "ads.test"
        within(3_000) { assertTrue(b.blocks("https://$host/x.js")) }
        within(3_000) { assertFalse(b.blocks("https://" + "a.".repeat(100_000) + "example.test/x.js")) }
    }

    @Test
    fun 긴_호스트의_상위_이름은_DNS_이름의_상한까지만() {
        assertEquals(listOf("a.b.example.com", "b.example.com", "example.com", "com"), Domains.suffixes("a.b.example.com"))
        val labels = List(60) { "l$it" + "x".repeat(it % 7) }
        val big = labels.joinToString(".")
        assertTrue(big.length > Domains.MAX_NAME)
        val all = labels.indices.map { labels.drop(it).joinToString(".") }
        assertEquals(all.filter { it.length <= Domains.MAX_NAME }, Domains.suffixes(big))
        assertEquals(emptyList(), Domains.suffixes("x".repeat(300) + ".com".repeat(0)))
        assertEquals(listOf("b.c", "c"), Domains.suffixes("a".repeat(300) + ".b.c"))
    }

    @Test
    fun 무늬_맞추기는_앞_판과_답이_같다() {
        // 가장 앞의 자리 하나로 족하다는 고침(UrlPattern.matches)이 답을 바꾸지 않았는지 — 앞 판의 맞추기를 그대로 옮긴 것과 무작위로 견준다.
        val r = Random(16)
        val segment = "ab/.^-"
        var compared = 0
        repeat(6_000) {
            val text = buildString {
                when (r.nextInt(4)) {
                    0 -> append("||")
                    1 -> append("|")
                }
                repeat(1 + r.nextInt(3)) { k ->
                    if (k > 0 || r.nextInt(4) == 0) append('*')
                    repeat(1 + r.nextInt(3)) { append(segment[r.nextInt(segment.length)]) }
                }
                if (r.nextInt(4) == 0) append('*')
                if (r.nextInt(3) == 0) append('|')
            }
            val pattern = UrlPattern.parse(text) ?: return@repeat
            val old = OldUrlPattern.parse(text) ?: error("앞 판은 읽었다: $text")
            repeat(30) {
                val url = "https://" + List(1 + r.nextInt(3)) { "ab".random(r).toString().repeat(1 + r.nextInt(2)) }.joinToString(".") +
                    "/" + buildString { repeat(r.nextInt(12)) { append("ab/.^-?=_".random(r)) } }
                val range = Request.hostRange(url)
                assertEquals(old.matches(url, range.first, range.last + 1), pattern.matches(url, range.first, range.last + 1), "$text ← $url")
                compared++
            }
        }
        assertTrue(compared > 100_000)
    }

    /** 앞 판의 [UrlPattern] 맞추기 그대로(첫 조각이 맞는 자리마다 해 본다) — 견주기 시험의 기준. */
    private class OldUrlPattern(
        private val segments: List<String>,
        private val hostAnchor: Boolean,
        private val startAnchor: Boolean,
        private val endAnchor: Boolean,
    ) {
        fun matches(url: String, hostStart: Int, hostEnd: Int): Boolean {
            if (segments.isEmpty()) return true
            val first = segments[0]
            if (hostAnchor) {
                var p = hostStart
                while (p in hostStart until maxOf(hostStart + 1, hostEnd)) {
                    if (matchFrom(url, first, p) && rest(url, p + first.length, 1)) return true
                    val dot = url.indexOf('.', p)
                    if (dot < 0 || dot >= hostEnd) break
                    p = dot + 1
                }
                return false
            }
            if (startAnchor) return matchFrom(url, first, 0) && rest(url, first.length, 1)
            var from = 0
            while (true) {
                val p = find(url, first, from)
                if (p < 0) return false
                if (rest(url, p + first.length, 1)) return true
                from = p + 1
            }
        }

        private fun rest(url: String, pos: Int, index: Int): Boolean {
            if (index == segments.size) return !endAnchor || pos == url.length
            val seg = segments[index]
            if (index == segments.size - 1 && endAnchor) {
                val at = url.length - seg.length
                if (at >= pos && matchFrom(url, seg, at)) return true
                return seg.endsWith('^') && at + 1 >= pos && matchFrom(url, seg, at + 1)
            }
            val p = find(url, seg, pos)
            return p >= 0 && rest(url, p + consumed(url, seg, p), index + 1)
        }

        private fun matchFrom(url: String, seg: String, at: Int): Boolean {
            var i = at
            for (k in seg.indices) {
                val c = seg[k]
                if (c == '^') {
                    if (i == url.length) return k == seg.length - 1
                    if (!UrlPattern.isSeparator(url[i])) return false
                } else {
                    if (i >= url.length || url[i] != c) return false
                }
                i++
            }
            return true
        }

        private fun find(url: String, seg: String, from: Int): Int {
            var p = from
            val head = seg[0]
            while (p <= url.length) {
                if (head != '^') {
                    p = url.indexOf(head, p)
                    if (p < 0) return -1
                }
                if (matchFrom(url, seg, p)) return p
                p++
            }
            return -1
        }

        private fun consumed(url: String, seg: String, p: Int): Int = if (p + seg.length > url.length) url.length - p else seg.length

        companion object {
            fun parse(text: String): OldUrlPattern? {
                var p = text
                var hostAnchor = false
                var startAnchor = false
                var endAnchor = false
                if (p.startsWith("||")) {
                    hostAnchor = true
                    p = p.substring(2)
                } else if (p.startsWith("|")) {
                    startAnchor = true
                    p = p.substring(1)
                }
                if (p.endsWith("|") && !p.endsWith("\\|")) {
                    endAnchor = true
                    p = p.dropLast(1)
                }
                p = p.lowercase()
                if (p.startsWith("*")) {
                    hostAnchor = false
                    startAnchor = false
                }
                if (p.endsWith("*")) endAnchor = false
                val segments = p.split('*').filter { it.isNotEmpty() }
                if (segments.isEmpty() && (hostAnchor || startAnchor || endAnchor)) return null
                return OldUrlPattern(segments, hostAnchor, startAnchor, endAnchor)
            }
        }
    }
}
