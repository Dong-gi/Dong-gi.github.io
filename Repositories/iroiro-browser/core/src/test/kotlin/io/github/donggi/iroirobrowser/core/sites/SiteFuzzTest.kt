package io.github.donggi.iroirobrowser.core.sites

import io.github.donggi.iroirobrowser.core.sites.SiteFixtures.FakeFetch
import io.github.donggi.iroirobrowser.core.sites.SiteFixtures.HFile
import io.github.donggi.iroirobrowser.core.sites.SiteFixtures.envelope
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
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * 사이트의 답을 뒤틀어 계획·풀기에 넣는다 — 결과는 계획(파일), 또는 우리 실패([SiteException]·[IOException])여야 하고 그 밖의 예외(인덱스·
 * 형 변환·숫자 변환·널)는 하나도 없어야 한다. 받은 계획은 늘 코덱으로 왕복해야 한다(이름·폴더가 다시 읽힌다). 고정 씨앗.
 */
class SiteFuzzTest {

    private fun mutate(seed: String, random: Random): String {
        val s = StringBuilder(seed)
        val alphabet = "{}[]\":,;'\\/ 0123456789-.eEabfntu\u0000\n\uD800가<>=$()"
        repeat(1 + random.nextInt(3)) {
            if (s.isEmpty()) return@repeat
            val at = random.nextInt(s.length)
            when (random.nextInt(4)) {
                0 -> s.deleteCharAt(at)
                1 -> s.insert(at, alphabet[random.nextInt(alphabet.length)])
                2 -> s.setCharAt(at, alphabet[random.nextInt(alphabet.length)])
                else -> s.delete(at, minOf(s.length, at + 1 + random.nextInt(30)))
            }
        }
        return s.toString()
    }

    private inline fun survive(round: Int, text: String, block: () -> GalleryPlan?): Boolean {
        return try {
            val plan = block()
            if (plan != null) GalleryCodec.decode(GalleryCodec.encode(plan))
            true
        } catch (e: IOException) {
            false
        } catch (e: Throwable) {
            fail("판 $round: ${e.javaClass.name}: ${e.message}\n${text.take(400)}")
        }
    }

    @Test
    fun pixiv_답_뒤틀기() {
        val random = Random(9_001)
        val seeds = listOf(
            "illust" to envelope(illustBody(10, title = "제목", pageCount = 2)),
            "illust" to envelope(illustBody(10, title = "움직임", type = 2)),
            "illust" to envelope(illustBody(10, title = "한 쪽")),
            "pages" to envelope(pagesBody(listOf(pximg(10, 0), pximg(10, 1, "jpg")))),
            "ugoira" to envelope(ugoiraBody("https://i.pximg.net/img-zip-ugoira/img/10_ugoira1920x1080.zip")),
        )
        var planned = 0
        repeat(6_000) { round ->
            val fetch = FakeFetch()
            val (which, seed) = seeds[round % seeds.size]
            val text = mutate(seed, random)
            // 쪽을 뒤틀 때는 여러 쪽 작품, zip 을 뒤틀 때는 우고이라 작품의 정보를 함께 준다.
            val illust = when (which) {
                "illust" -> text
                "pages" -> seeds[0].second
                else -> seeds[1].second
            }
            fetch.text("https://www.pixiv.net/ajax/illust/10", illust)
            fetch.text("https://www.pixiv.net/ajax/illust/10/pages", if (which == "pages") text else seeds[3].second)
            fetch.text("https://www.pixiv.net/ajax/illust/10/ugoira_meta", if (which == "ugoira") text else seeds[4].second)
            if (survive(round, text) { GalleryResolver(fetch).plan(SiteTarget.PixivArtwork(10)) }) planned++
        }
        assertTrue(planned > 0)
    }

    @Test
    fun pixiv_작가_답_뒤틀기() {
        val random = Random(9_002)
        val user = envelope(userBody("작가", 20))
        val profile = envelope(profileBody(listOf(3, 1, 2), listOf(5)))
        var planned = 0
        repeat(3_000) { round ->
            val fetch = FakeFetch()
            val text = mutate(if (round % 2 == 0) user else profile, random)
            fetch.text("https://www.pixiv.net/ajax/user/20", if (round % 2 == 0) text else user)
            fetch.text("https://www.pixiv.net/ajax/user/20/profile/all", if (round % 2 == 1) text else profile)
            if (survive(round, text) { GalleryResolver(fetch).plan(SiteTarget.PixivUser(20)) }) planned++
        }
        assertTrue(planned > 0)
    }

    @Test
    fun hitomi_답_뒤틀기() {
        val random = Random(9_003)
        val gallery = hitomiGallery(30, title = "갤러리", files = listOf(HFile("1.jpg", hash(1)), HFile("2.gif", hash(2), avif = 0L), HFile(null, hash(3))))
        var planned = 0
        repeat(4_000) { round ->
            val fetch = FakeFetch()
            val text = mutate(gallery, random)
            fetch.text("https://ltn.gold-usergeneratedcontent.net/galleries/30.js", text)
            fetch.text("https://ltn.gold-usergeneratedcontent.net/gg.js", ggJs())
            val resolver = GalleryResolver(fetch)
            val ok = survive(round, text) {
                resolver.plan(SiteTarget.HitomiGallery(30)).also { plan -> plan.units.forEach { resolver.expand(it) } }
            }
            if (ok) planned++
        }
        assertTrue(planned > 0)
    }

    @Test
    fun gg_뒤틀기() {
        val random = Random(9_004)
        val seed = ggJs(fallback = 1, groups = listOf(0 to listOf(1, 2, 3), 2 to listOf(4)))
        var parsed = 0
        repeat(6_000) { round ->
            val fetch = FakeFetch()
            val text = mutate(seed, random)
            fetch.text("https://ltn.gold-usergeneratedcontent.net/gg.js", text)
            val ok = survive(round, text) {
                GalleryResolver(fetch).expand(GalleryUnit.HitomiImage(1, hash(round), "avif", "a.avif"))
                null
            }
            if (ok) parsed++
        }
        assertTrue(parsed > 0)
    }
}
