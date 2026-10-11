package io.github.donggi.iroirobrowser.core.sites

import io.github.donggi.iroirobrowser.core.sites.SiteException.Reason
import io.github.donggi.iroirobrowser.core.sites.SiteFixtures.FakeFetch
import io.github.donggi.iroirobrowser.core.sites.SiteFixtures.HFile
import io.github.donggi.iroirobrowser.core.sites.SiteFixtures.envelope
import io.github.donggi.iroirobrowser.core.sites.SiteFixtures.errorEnvelope
import io.github.donggi.iroirobrowser.core.sites.SiteFixtures.ggJs
import io.github.donggi.iroirobrowser.core.sites.SiteFixtures.hash
import io.github.donggi.iroirobrowser.core.sites.SiteFixtures.hitomiGallery
import io.github.donggi.iroirobrowser.core.sites.SiteFixtures.illustBody
import io.github.donggi.iroirobrowser.core.sites.SiteFixtures.pagesBody
import io.github.donggi.iroirobrowser.core.sites.SiteFixtures.profileBody
import io.github.donggi.iroirobrowser.core.sites.SiteFixtures.pximg
import io.github.donggi.iroirobrowser.core.sites.SiteFixtures.ugoiraBody
import io.github.donggi.iroirobrowser.core.sites.SiteFixtures.userBody
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GalleryResolverTest {

    private val fetch = FakeFetch()
    private var clock = 1_000_000L
    private val resolver = GalleryResolver(fetch) { clock }

    private fun pixiv(path: String) = "https://www.pixiv.net/ajax/$path"
    private fun ltn(path: String) = "https://ltn.gold-usergeneratedcontent.net/$path"

    private fun reason(block: () -> Unit): Reason = assertFailsWith<SiteException> { block() }.reason

    private fun names(plan: GalleryPlan): List<String> = plan.units.map { (it as GalleryUnit.Direct).file.fileName }

    @Test
    fun pixiv_작품_한_쪽() {
        fetch.text(pixiv("illust/1001"), envelope(illustBody(1001, title = "한 쪽", userName = "작가 갑")))
        val plan = resolver.plan(SiteTarget.PixivArtwork(1001))
        assertEquals("한 쪽", plan.title)
        assertEquals("작가 갑", plan.creator)
        assertEquals(listOf("Pixiv", "작가 갑"), plan.folder)
        assertEquals(1, plan.fileCount)
        val unit = assertIs<GalleryUnit.Direct>(plan.units.single())
        assertEquals(pximg(1001, 0), unit.file.url)
        assertEquals("1001_한 쪽.png", unit.file.fileName)
        assertEquals("https://www.pixiv.net/", unit.file.referer)
        // 요청은 하나 — Referer 는 작품의 정규 주소, 상한은 FETCH_LIMIT.
        val call = fetch.calls.single()
        assertEquals("https://www.pixiv.net/artworks/1001", call.referer)
        assertEquals(GalleryResolver.FETCH_LIMIT, call.limit)
        // Direct 는 묻지 않고 그대로.
        assertEquals(listOf(unit.file), resolver.expand(unit))
        assertEquals(1, fetch.calls.size)
    }

    @Test
    fun pixiv_여러_쪽() {
        fetch.text(pixiv("illust/1002"), envelope(illustBody(1002, title = "여러 쪽", type = 1, pageCount = 3)))
        fetch.text(pixiv("illust/1002/pages"), envelope(pagesBody(listOf(pximg(1002, 0), pximg(1002, 1, "jpg"), pximg(1002, 2)))))
        val plan = resolver.plan(SiteTarget.PixivArtwork(1002))
        assertEquals(3, plan.fileCount)
        assertEquals(listOf("1002_여러 쪽_p000.png", "1002_여러 쪽_p001.jpg", "1002_여러 쪽_p002.png"), names(plan))
        assertEquals(listOf(pixiv("illust/1002"), pixiv("illust/1002/pages")), fetch.urls())
        assertEquals("https://www.pixiv.net/artworks/1002", fetch.calls[1].referer)
        assertTrue(plan.units.all { (it as GalleryUnit.Direct).file.referer == "https://www.pixiv.net/" })
    }

    @Test
    fun pixiv_우고이라() {
        val big = "https://i.pximg.net/img-zip-ugoira/img/2026/01/02/03/04/05/1003_ugoira1920x1080.zip"
        val small = "https://i.pximg.net/img-zip-ugoira/img/2026/01/02/03/04/05/1003_ugoira600x600.zip"
        fetch.text(pixiv("illust/1003"), envelope(illustBody(1003, title = "움직임", type = 2)))
        fetch.text(pixiv("illust/1003/ugoira_meta"), envelope(ugoiraBody(big, small)))
        val plan = resolver.plan(SiteTarget.PixivArtwork(1003))
        val unit = assertIs<GalleryUnit.Direct>(plan.units.single())
        assertEquals(big, unit.file.url)
        assertEquals("1003_움직임_ugoira.zip", unit.file.fileName)
        assertEquals("https://www.pixiv.net/", unit.file.referer)
        assertEquals(1, plan.fileCount)
    }

    @Test
    fun pixiv_작가() {
        fetch.text(pixiv("user/77"), envelope(userBody("작가 을", 77)))
        fetch.text(pixiv("user/77/profile/all"), envelope(profileBody(listOf(5, 30), listOf(12))))
        val plan = resolver.plan(SiteTarget.PixivUser(77))
        assertEquals("작가 을", plan.title)
        assertEquals("작가 을", plan.creator)
        assertEquals(listOf("Pixiv", "작가 을"), plan.folder)
        assertNull(plan.fileCount)
        assertEquals(listOf(30L, 12L, 5L), plan.units.map { assertIs<GalleryUnit.PixivWork>(it).id })
        assertTrue(fetch.calls.all { it.referer == "https://www.pixiv.net/users/77" })
        // 받을 차례가 오면 그 작품을 묻는다.
        fetch.text(pixiv("illust/30"), envelope(illustBody(30, title = "삼십", pageCount = 2, userName = "작가 을")))
        fetch.text(pixiv("illust/30/pages"), envelope(pagesBody(listOf(pximg(30, 0), pximg(30, 1)))))
        val files = resolver.expand(plan.units[0])
        assertEquals(listOf("30_삼십_p000.png", "30_삼십_p001.png"), files.map { it.fileName })
        assertEquals("https://www.pixiv.net/artworks/30", fetch.calls.last().referer)
        // 그 사이 지워진 작품.
        assertEquals(Reason.NOT_FOUND, reason { resolver.expand(plan.units[1]) })
    }

    @Test
    fun pixiv_작가_이름이_다듬으면_비면_번호() {
        fetch.text(pixiv("user/78"), envelope(userBody(" . . ", 78)))
        fetch.text(pixiv("user/78/profile/all"), envelope(profileBody(listOf(1), emptyList())))
        val plan = resolver.plan(SiteTarget.PixivUser(78))
        assertEquals(listOf("Pixiv", "78"), plan.folder)
        assertEquals(". .", plan.title)
    }

    @Test
    fun 로그인이_필요하면_LOGIN_REQUIRED() {
        fetch.text(pixiv("illust/2001"), envelope(illustBody(2001, original = null, xRestrict = 1)))
        assertEquals(Reason.LOGIN_REQUIRED, reason { resolver.plan(SiteTarget.PixivArtwork(2001)) })
        assertEquals(1, fetch.calls.size)

        fetch.text(pixiv("illust/2002"), errorEnvelope("ログインしてください"))
        assertEquals(Reason.LOGIN_REQUIRED, reason { resolver.plan(SiteTarget.PixivArtwork(2002)) })

        fetch.fail(pixiv("illust/2003"), SiteHttpException(401, "HTTP 401"))
        assertEquals(Reason.LOGIN_REQUIRED, reason { resolver.plan(SiteTarget.PixivArtwork(2003)) })

        // 연령 제한 작품의 pages 가 '없음' 이면 로그인 필요로 읽는다.
        fetch.text(pixiv("illust/2004"), envelope(illustBody(2004, pageCount = 2, xRestrict = 1)))
        assertEquals(Reason.LOGIN_REQUIRED, reason { resolver.plan(SiteTarget.PixivArtwork(2004)) })

        fetch.text(pixiv("illust/2005"), envelope(illustBody(2005, pageCount = 2)))
        fetch.text(pixiv("illust/2005/pages"), envelope(pagesBody(listOf(pximg(2005, 0), null))))
        assertEquals(Reason.LOGIN_REQUIRED, reason { resolver.plan(SiteTarget.PixivArtwork(2005)) })

        fetch.text(pixiv("illust/2006"), envelope(illustBody(2006, type = 2, loginOnly = true)))
        fetch.text(pixiv("illust/2006/ugoira_meta"), envelope(ugoiraBody(null, null)))
        assertEquals(Reason.LOGIN_REQUIRED, reason { resolver.plan(SiteTarget.PixivArtwork(2006)) })

        // 작가 받기의 한 작품도 같다.
        assertEquals(Reason.LOGIN_REQUIRED, reason { resolver.expand(GalleryUnit.PixivWork(2001)) })
    }

    @Test
    fun 없으면_NOT_FOUND() {
        assertEquals(Reason.NOT_FOUND, reason { resolver.plan(SiteTarget.PixivArtwork(3001)) })
        fetch.fail(pixiv("illust/3002"), SiteHttpException(410, "HTTP 410"))
        assertEquals(Reason.NOT_FOUND, reason { resolver.plan(SiteTarget.PixivArtwork(3002)) })
        fetch.text(pixiv("illust/3003"), errorEnvelope(""))
        assertEquals(Reason.NOT_FOUND, reason { resolver.plan(SiteTarget.PixivArtwork(3003)) })
        // 제한 없는 작품의 pages 가 없으면 그대로 없음.
        fetch.text(pixiv("illust/3004"), envelope(illustBody(3004, pageCount = 3)))
        assertEquals(Reason.NOT_FOUND, reason { resolver.plan(SiteTarget.PixivArtwork(3004)) })
        assertEquals(Reason.NOT_FOUND, reason { resolver.plan(SiteTarget.HitomiGallery(3005)) })
        assertEquals(Reason.NOT_FOUND, reason { resolver.plan(SiteTarget.PixivUser(3006)) })
        fetch.text(pixiv("user/3007"), envelope(userBody("x", 3007)))
        assertEquals(Reason.NOT_FOUND, reason { resolver.plan(SiteTarget.PixivUser(3007)) })
    }

    @Test
    fun 막히면_BLOCKED() {
        fetch.text(pixiv("illust/4001"), "<!DOCTYPE html><html><head><title>Just a moment...</title></head></html>")
        assertEquals(Reason.BLOCKED, reason { resolver.plan(SiteTarget.PixivArtwork(4001)) })
        fetch.fail(pixiv("illust/4002"), SiteHttpException(403, "HTTP 403"))
        assertEquals(Reason.BLOCKED, reason { resolver.plan(SiteTarget.PixivArtwork(4002)) })
        fetch.text(ltn("galleries/4003.js"), "\r\n<html><body>Access denied</body></html>")
        assertEquals(Reason.BLOCKED, reason { resolver.plan(SiteTarget.HitomiGallery(4003)) })
        fetch.text(ltn("gg.js"), "<html>blocked</html>")
        assertEquals(Reason.BLOCKED, reason { resolver.expand(GalleryUnit.HitomiImage(4004, hash(1), "avif", "a.avif")) })
        fetch.fail(ltn("gg.js"), SiteHttpException(403, "HTTP 403"))
        assertEquals(Reason.BLOCKED, reason { resolver.expand(GalleryUnit.HitomiImage(4004, hash(1), "avif", "a.avif")) })
    }

    @Test
    fun 프록시의_답과_잠깐의_실패는_그대로() {
        for (code in listOf(401, 403, 404, 407, 502)) {
            val proxy = SiteHttpException(code, "프록시의 답", byProxy = true)
            fetch.fail(pixiv("illust/5001"), proxy)
            assertSame(proxy, assertFailsWith<SiteHttpException> { resolver.plan(SiteTarget.PixivArtwork(5001)) })
            fetch.fail(ltn("gg.js"), proxy)
            assertSame(proxy, assertFailsWith<SiteHttpException> { resolver.expand(GalleryUnit.HitomiImage(1, hash(1), "avif", "a.avif")) })
        }
        for (code in listOf(429, 500, 503)) {
            val site = SiteHttpException(code, "HTTP $code")
            fetch.fail(ltn("galleries/5002.js"), site)
            assertSame(site, assertFailsWith<SiteHttpException> { resolver.plan(SiteTarget.HitomiGallery(5002)) })
        }
        val io = IOException("연결이 끊겼다")
        fetch.fail(pixiv("user/5003"), io)
        assertSame(io, assertFailsWith<IOException> { resolver.plan(SiteTarget.PixivUser(5003)) })
    }

    @Test
    fun 취소하면_다음_요청_전에_멈춘다() {
        assertFailsWith<InterruptedIOException> { resolver.plan(SiteTarget.PixivArtwork(1), cancelled = { true }) }
        assertFailsWith<InterruptedIOException> { resolver.plan(SiteTarget.HitomiGallery(1), cancelled = { true }) }
        assertFailsWith<InterruptedIOException> { resolver.plan(SiteTarget.PixivUser(1), cancelled = { true }) }
        assertEquals(0, fetch.calls.size)

        fetch.text(pixiv("illust/6001"), envelope(illustBody(6001, pageCount = 2)))
        fetch.text(pixiv("illust/6001/pages"), envelope(pagesBody(listOf(pximg(6001, 0), pximg(6001, 1)))))
        var asked = 0
        assertFailsWith<InterruptedIOException> { resolver.plan(SiteTarget.PixivArtwork(6001)) { asked++ > 0 } }
        assertEquals(listOf(pixiv("illust/6001")), fetch.urls())

        fetch.text(pixiv("user/6002"), envelope(userBody("작가", 6002)))
        asked = 0
        assertFailsWith<InterruptedIOException> { resolver.plan(SiteTarget.PixivUser(6002)) { asked++ > 0 } }
        assertEquals(pixiv("user/6002"), fetch.urls().last())

        val before = fetch.calls.size
        assertFailsWith<InterruptedIOException> { resolver.expand(GalleryUnit.PixivWork(6001)) { true } }
        assertFailsWith<InterruptedIOException> { resolver.expand(GalleryUnit.HitomiImage(1, hash(1), "avif", "a.avif")) { true } }
        assertEquals(before, fetch.calls.size)
        // Direct 는 요청이 없다.
        val direct = GalleryUnit.Direct(GalleryFile("https://i.pximg.net/a.png", "a.png", "https://www.pixiv.net/"))
        assertEquals(listOf(direct.file), resolver.expand(direct) { true })
    }

    @Test
    fun hitomi_계획은_정보만_받고_주소는_풀_때() {
        val files = listOf(HFile("001.jpg", hash(1, "6c1")), HFile("002.jpg", hash(2, "a1b"), avif = 0L))
        fetch.text(ltn("galleries/7001.js"), hitomiGallery(7001, title = "갤러리", files = files))
        val plan = resolver.plan(SiteTarget.HitomiGallery(7001))
        assertEquals("갤러리", plan.title)
        assertEquals("작가 하나", plan.creator)
        assertEquals(listOf("Hitomi", "작가 하나", "7001_갤러리"), plan.folder)
        assertEquals(2, plan.fileCount)
        val units = plan.units.map { assertIs<GalleryUnit.HitomiImage>(it) }
        assertEquals(listOf(7001L, 7001L), units.map { it.galleryId })
        assertEquals(listOf("avif", "webp"), units.map { it.ext })
        assertEquals(listOf("001.avif", "002.webp"), units.map { it.fileName })
        assertEquals(listOf(ltn("galleries/7001.js")), fetch.urls())
        assertEquals("https://hitomi.la/reader/7001.html", fetch.calls[0].referer)

        fetch.text(ltn("gg.js"), ggJs())
        val first = resolver.expand(units[0]).single()
        // g=364 은 묶음(0) — avif 는 a1.
        assertEquals("https://a1.gold-usergeneratedcontent.net/1700000000/364/${hash(1, "6c1")}.avif", first.url)
        assertEquals("001.avif", first.fileName)
        assertEquals("https://hitomi.la/reader/7001.html", first.referer)
        val second = resolver.expand(units[1]).single()
        // g=2977 도 0 — webp 는 w1(데스크톱의 a 가 아니다).
        assertEquals("https://w1.gold-usergeneratedcontent.net/1700000000/2977/${hash(2, "a1b")}.webp", second.url)
        assertEquals(1, fetch.urls().count { it == ltn("gg.js") })
        assertEquals("https://hitomi.la/", fetch.calls.single { it.url == ltn("gg.js") }.referer)
    }

    private fun ggFetches(): Int = fetch.urls().count { it == ltn("gg.js") }

    @Test
    fun gg_는_기억했다가_때가_지나거나_버리면_다시() {
        val unit = GalleryUnit.HitomiImage(8001, hash(1, "000"), "avif", "001.avif")
        fetch.text(ltn("gg.js"), ggJs(b = "'1/'"))
        assertTrue(resolver.expand(unit).single().url.startsWith("https://a2.gold-usergeneratedcontent.net/1/0/"))
        clock += GalleryResolver.GG_TTL_MS - 1
        resolver.expand(unit)
        assertEquals(1, ggFetches())

        fetch.text(ltn("gg.js"), ggJs(fallback = 0, b = "'2/'"))
        clock += 1
        assertTrue(resolver.expand(unit).single().url.startsWith("https://a1.gold-usergeneratedcontent.net/2/0/"))
        assertEquals(2, ggFetches())

        fetch.text(ltn("gg.js"), ggJs(b = "'3/'"))
        resolver.invalidate()
        assertTrue(resolver.expand(unit).single().url.startsWith("https://a2.gold-usergeneratedcontent.net/3/0/"))
        assertEquals(3, ggFetches())

        // 시계가 거꾸로 가면 낡은 것으로 본다.
        clock -= 10
        resolver.expand(unit)
        assertEquals(4, ggFetches())

        // 기억은 인스턴스마다.
        GalleryResolver(fetch) { clock }.expand(unit)
        assertEquals(5, ggFetches())
    }

    @Test
    fun gg_가_없거나_깨졌으면_UNEXPECTED_그리고_기억하지_않는다() {
        val unit = GalleryUnit.HitomiImage(8002, hash(3), "webp", "003.webp")
        assertEquals(Reason.UNEXPECTED, reason { resolver.expand(unit) })
        fetch.text(ltn("gg.js"), ggJs().replace("m[2]+m[1]", "m[1]+m[2]"))
        assertEquals(Reason.UNEXPECTED, reason { resolver.expand(unit) })
        fetch.text(ltn("gg.js"), ggJs())
        assertTrue(resolver.expand(unit).single().url.endsWith(".webp"))
        assertEquals(3, ggFetches())
    }

    @Test
    fun 여러_일꾼이_함께_풀어도_gg_는_한_번() {
        val count = AtomicInteger()
        val gate = CountDownLatch(1)
        val slow = SiteFetch { url, _, _ ->
            check(url == ltn("gg.js"))
            count.incrementAndGet()
            gate.await(5, TimeUnit.SECONDS)
            ggJs().toByteArray()
        }
        val shared = GalleryResolver(slow) { clock }
        val pool = Executors.newFixedThreadPool(8)
        try {
            val futures = (0 until 8).map { i ->
                pool.submit<String> { shared.expand(GalleryUnit.HitomiImage(1, hash(i), "avif", "$i.avif")).single().url }
            }
            Thread.sleep(100)
            // 받는 동안 invalidate 는 막히지 않는다(다른 자물쇠).
            val started = System.nanoTime()
            shared.invalidate()
            assertTrue(System.nanoTime() - started < TimeUnit.SECONDS.toNanos(1))
            gate.countDown()
            futures.forEach { assertTrue(it.get(10, TimeUnit.SECONDS).startsWith("https://a")) }
        } finally {
            pool.shutdownNow()
        }
        // 첫 받기 하나 — invalidate 가 그 받기의 결과를 기억하지 못하게 했으므로 기다리던 일꾼 가운데 하나가 한 번 더 받을 수 있다.
        assertTrue(count.get() in 1..2, "gg.js 를 ${count.get()} 번 받았다")
    }

    @Test
    fun 너무_크면_TOO_LARGE() {
        fetch.text(pixiv("user/9001"), envelope(userBody("많음", 9001)))
        fetch.text(pixiv("user/9001/profile/all"), envelope(profileBody((1L..GalleryResolver.MAX_UNITS + 1L).toList(), emptyList())))
        assertEquals(Reason.TOO_LARGE, reason { resolver.plan(SiteTarget.PixivUser(9001)) })
        fetch.text(pixiv("user/9002"), envelope(userBody("딱 맞음", 9002)))
        fetch.text(pixiv("user/9002/profile/all"), envelope(profileBody((1L..10_000L).toList(), (10_001L..GalleryResolver.MAX_UNITS.toLong()).toList())))
        assertEquals(GalleryResolver.MAX_UNITS, resolver.plan(SiteTarget.PixivUser(9002)).units.size)

        val many = List(GalleryResolver.MAX_UNITS + 1) { HFile("$it.jpg", hash(it)) }
        fetch.text(ltn("galleries/9003.js"), hitomiGallery(9003, files = many))
        assertEquals(Reason.TOO_LARGE, reason { resolver.plan(SiteTarget.HitomiGallery(9003)) })

        fetch.text(pixiv("illust/9004"), envelope(illustBody(9004, pageCount = GalleryResolver.MAX_UNITS + 1)))
        assertEquals(Reason.TOO_LARGE, reason { resolver.plan(SiteTarget.PixivArtwork(9004)) })
        assertTrue(pixiv("illust/9004/pages") !in fetch.urls())

        // 받기가 상한을 어겨 더 준 덩이.
        fetch.bytes(pixiv("illust/9005"), ByteArray(GalleryResolver.FETCH_LIMIT + 1) { ' '.code.toByte() })
        assertEquals(Reason.TOO_LARGE, reason { resolver.plan(SiteTarget.PixivArtwork(9005)) })
        // JSON 의 값이 상한(50만)을 넘는다.
        fetch.text(pixiv("illust/9006"), "[" + "0,".repeat(600_000) + "0]")
        assertEquals(Reason.TOO_LARGE, reason { resolver.plan(SiteTarget.PixivArtwork(9006)) })
    }

    @Test
    fun 받을_것이_없으면_EMPTY() {
        fetch.text(ltn("galleries/9101.js"), hitomiGallery(9101, files = emptyList()))
        assertEquals(Reason.EMPTY, reason { resolver.plan(SiteTarget.HitomiGallery(9101)) })
        fetch.text(pixiv("user/9102"), envelope(userBody("빈 작가", 9102)))
        fetch.text(pixiv("user/9102/profile/all"), envelope(profileBody(emptyList(), emptyList())))
        assertEquals(Reason.EMPTY, reason { resolver.plan(SiteTarget.PixivUser(9102)) })
        fetch.text(pixiv("illust/9103"), envelope(illustBody(9103, pageCount = 0)))
        assertEquals(Reason.EMPTY, reason { resolver.plan(SiteTarget.PixivArtwork(9103)) })
        fetch.text(pixiv("illust/9104"), envelope(illustBody(9104, pageCount = 2)))
        fetch.text(pixiv("illust/9104/pages"), envelope("[]"))
        assertEquals(Reason.EMPTY, reason { resolver.plan(SiteTarget.PixivArtwork(9104)) })
    }

    @Test
    fun 꼴이_다르면_UNEXPECTED() {
        fetch.text(pixiv("illust/9201"), envelope("{}"))
        assertEquals(Reason.UNEXPECTED, reason { resolver.plan(SiteTarget.PixivArtwork(9201)) })
        fetch.text(pixiv("illust/9202"), "그냥 글")
        assertEquals(Reason.UNEXPECTED, reason { resolver.plan(SiteTarget.PixivArtwork(9202)) })
        fetch.text(pixiv("illust/9203"), envelope(illustBody(9299)))
        assertEquals(Reason.UNEXPECTED, reason { resolver.plan(SiteTarget.PixivArtwork(9203)) })
        fetch.text(pixiv("illust/9204"), envelope(illustBody(9204, type = 2)))
        fetch.text(pixiv("illust/9204/ugoira_meta"), envelope(ugoiraBody(null, null)))
        assertEquals(Reason.UNEXPECTED, reason { resolver.plan(SiteTarget.PixivArtwork(9204)) })
        fetch.text(ltn("galleries/9205.js"), "var galleryinfo = {\"id\":\"9205\",\"files\":[{\"name\":\"a.jpg\"}]}")
        assertEquals(Reason.UNEXPECTED, reason { resolver.plan(SiteTarget.HitomiGallery(9205)) })

        fetch.text(ltn("gg.js"), ggJs())
        for (bad in listOf(
            GalleryUnit.HitomiImage(1, "abc", "avif", "a.avif"),
            GalleryUnit.HitomiImage(1, hash(1), "jpg", "a.jpg"),
            GalleryUnit.HitomiImage(0, hash(1), "avif", "a.avif"),
            GalleryUnit.PixivWork(0),
            GalleryUnit.PixivWork(-5),
        )) {
            assertEquals(Reason.UNEXPECTED, reason { resolver.expand(bad) })
        }
    }

    /** 앱이 계획을 글로 적었다가 다시 뜬 뒤에 읽어 풀어도 같은 파일 — 계약의 두 쪽을 함께. */
    @Test
    fun 적었다_읽은_계획으로도_같은_파일() {
        val files = listOf(HFile("a.jpg", hash(11)), HFile("a.png", hash(12)), HFile(null, hash(13), avif = 0L))
        fetch.text(ltn("galleries/9301.js"), hitomiGallery(9301, title = "왕복", files = files))
        fetch.text(ltn("gg.js"), ggJs())
        val plan = resolver.plan(SiteTarget.HitomiGallery(9301))
        val again = GalleryCodec.decode(GalleryCodec.encode(plan))
        assertEquals(plan.units.flatMap { resolver.expand(it) }.map { it.url to it.fileName }, again.units.flatMap { resolver.expand(it) }.map { it.url to it.fileName })

        fetch.text(pixiv("illust/9302"), envelope(illustBody(9302, title = "왕/복", pageCount = 2)))
        fetch.text(pixiv("illust/9302/pages"), envelope(pagesBody(listOf(pximg(9302, 0), pximg(9302, 1)))))
        val pixivPlan = resolver.plan(SiteTarget.PixivArtwork(9302))
        assertEquals(names(pixivPlan), names(GalleryCodec.decode(GalleryCodec.encode(pixivPlan))))
        assertEquals(listOf("9302_왕_복_p000.png", "9302_왕_복_p001.png"), names(pixivPlan))
    }
}
