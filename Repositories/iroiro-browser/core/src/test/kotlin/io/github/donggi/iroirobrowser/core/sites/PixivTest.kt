package io.github.donggi.iroirobrowser.core.sites

import io.github.donggi.iroirobrowser.core.SafeFileName
import io.github.donggi.iroirobrowser.core.sites.SiteFixtures.envelope
import io.github.donggi.iroirobrowser.core.sites.SiteFixtures.errorEnvelope
import io.github.donggi.iroirobrowser.core.sites.SiteFixtures.illustBody
import io.github.donggi.iroirobrowser.core.sites.SiteFixtures.pagesBody
import io.github.donggi.iroirobrowser.core.sites.SiteFixtures.profileBody
import io.github.donggi.iroirobrowser.core.sites.SiteFixtures.pximg
import io.github.donggi.iroirobrowser.core.sites.SiteFixtures.ugoiraBody
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PixivTest {

    private fun reason(block: () -> Unit): SiteException.Reason = assertFailsWith<SiteException> { block() }.reason

    private fun bytes(text: String) = text.toByteArray(Charsets.UTF_8)

    @Test
    fun 봉투() {
        assertEquals(listOf(1L), Pixiv.body(bytes(envelope("[1]")), "t"))
        assertEquals(SiteException.Reason.NOT_FOUND, reason { Pixiv.body(bytes(errorEnvelope("")), "t") })
        assertEquals(SiteException.Reason.NOT_FOUND, reason { Pixiv.body(bytes(errorEnvelope("該当作品は削除されたか、存在しない作品IDです。")), "t") })
        for (message in listOf("ログインしてください", "Please log in.", "로그인이 필요합니다", "请登录", "R-18作品", "Login required")) {
            assertEquals(SiteException.Reason.LOGIN_REQUIRED, reason { Pixiv.body(bytes(errorEnvelope(message)), "t") }, message)
        }
        assertEquals(SiteException.Reason.UNEXPECTED, reason { Pixiv.body(bytes("""{"message":"","body":{}}"""), "t") })
        assertEquals(SiteException.Reason.UNEXPECTED, reason { Pixiv.body(bytes("""{"error":false,"message":""}"""), "t") })
        assertEquals(SiteException.Reason.UNEXPECTED, reason { Pixiv.body(bytes("[1,2]"), "t") })
        assertEquals(SiteException.Reason.UNEXPECTED, reason { Pixiv.body(bytes("not json"), "t") })
        assertEquals(SiteException.Reason.BLOCKED, reason { Pixiv.body(bytes("\n <!DOCTYPE html><title>Just a moment...</title>"), "t") })
        // 맨 앞의 BOM 은 뗀다.
        assertEquals(emptyMap<String, Any?>(), Pixiv.body(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + bytes(envelope("{}")), "t"))
    }

    private fun illust(text: String, id: Long): Pixiv.Illust = Pixiv.illust(Pixiv.body(bytes(envelope(text)), "t"), id)

    @Test
    fun 작품_정보() {
        val info = illust(illustBody(101, title = "제목", type = 1, pageCount = 3, userId = 77, userName = "작가"), 101)
        assertEquals("제목", info.title)
        assertEquals(1L, info.type)
        assertEquals(3L, info.pageCount)
        assertEquals(77L, info.userId)
        assertEquals("작가", info.userName)
        assertEquals(pximg(101, 0), info.original)
        assertFalse(info.restricted)
        // 번호가 글이 아니라 수로 와도.
        assertEquals(101L, illust(illustBody(101, idAsString = false), 101).id)
    }

    @Test
    fun 로그인이_필요한_작품의_표시() {
        val info = illust(illustBody(102, original = null, xRestrict = 1), 102)
        assertNull(info.original)
        assertTrue(info.restricted)
        assertTrue(illust(illustBody(103, loginOnly = true), 103).restricted)
    }

    @Test
    fun 작품_정보의_꼴이_다르면_UNEXPECTED() {
        assertEquals(SiteException.Reason.UNEXPECTED, reason { illust(illustBody(104), 105) })
        assertEquals(SiteException.Reason.UNEXPECTED, reason { illust("""{"illustId":"104"}""", 104) })
        assertEquals(SiteException.Reason.UNEXPECTED, reason { illust("[]", 104) })
        val noUrls = illustBody(104).replace("\"urls\":{", "\"urlz\":{")
        assertEquals(SiteException.Reason.UNEXPECTED, reason { illust(noUrls, 104) })
        val noOriginal = illustBody(104).replace("\"original\":", "\"origin\":")
        assertEquals(SiteException.Reason.UNEXPECTED, reason { illust(noOriginal, 104) })
        // 그림 주소는 https 의 pximg.net 만.
        for (bad in listOf(
            "http://i.pximg.net/img-original/img/a_p0.png",
            "https://i.pximg.net.evil.com/a_p0.png",
            "https://evil.com/i.pximg.net/a_p0.png",
            "https://i.pximg.net",
            "https://i.pximg.net/a b_p0.png",
            "https://i.pximg.net/" + "a".repeat(2000) + ".png",
        )) {
            assertEquals(SiteException.Reason.UNEXPECTED, reason { illust(illustBody(104, original = bad), 104) }, bad)
        }
    }

    @Test
    fun 쪽들() {
        val urls = listOf(pximg(5, 0), pximg(5, 1, "jpg"), pximg(5, 2, "gif"))
        assertEquals(urls, Pixiv.pages(Pixiv.body(bytes(envelope(pagesBody(urls))), "t"), 5))
        assertEquals(SiteException.Reason.LOGIN_REQUIRED, reason {
            Pixiv.pages(Pixiv.body(bytes(envelope(pagesBody(listOf(pximg(5, 0), null)))), "t"), 5)
        })
        assertEquals(SiteException.Reason.UNEXPECTED, reason { Pixiv.pages(Pixiv.body(bytes(envelope("{}")), "t"), 5) })
        assertEquals(SiteException.Reason.UNEXPECTED, reason { Pixiv.pages(Pixiv.body(bytes(envelope("[{}]")), "t"), 5) })
    }

    @Test
    fun 우고이라_zip() {
        val big = "https://i.pximg.net/img-zip-ugoira/img/2026/01/02/03/04/05/7_ugoira1920x1080.zip"
        val small = "https://i.pximg.net/img-zip-ugoira/img/2026/01/02/03/04/05/7_ugoira600x600.zip"
        assertEquals(big, Pixiv.ugoiraZip(Pixiv.body(bytes(envelope(ugoiraBody(big, small))), "t"), 7))
        assertEquals(small, Pixiv.ugoiraZip(Pixiv.body(bytes(envelope(ugoiraBody(null, small))), "t"), 7))
        assertNull(Pixiv.ugoiraZip(Pixiv.body(bytes(envelope(ugoiraBody(null, null))), "t"), 7))
    }

    @Test
    fun 작가의_작품_번호는_일러스트와_만화를_큰_것부터() {
        val body = Pixiv.body(bytes(envelope(profileBody(listOf(30, 10, 50), listOf(40, 20)))), "t")
        assertEquals(listOf(50L, 40L, 30L, 20L, 10L), Pixiv.workIds(body, 1))
        assertEquals(emptyList<Long>(), Pixiv.workIds(Pixiv.body(bytes(envelope(profileBody(emptyList(), emptyList()))), "t"), 1))
        assertEquals(listOf(3L), Pixiv.workIds(Pixiv.body(bytes(envelope("""{"illusts":{"3":null},"manga":[]}""")), "t"), 1))
        // 같은 번호가 두 군데 있어도 한 번.
        assertEquals(listOf(9L), Pixiv.workIds(Pixiv.body(bytes(envelope("""{"illusts":{"9":null},"manga":{"9":null}}""")), "t"), 1))
        for (bad in listOf("""{"illusts":{"abc":null}}""", """{"illusts":{"0":null}}""", """{"illusts":{"-1":null}}""", """{"illusts":"x"}""", "[]")) {
            assertEquals(SiteException.Reason.UNEXPECTED, reason { Pixiv.workIds(Pixiv.body(bytes(envelope(bad)), "t"), 1) }, bad)
        }
    }

    @Test
    fun 이름은_데스크톱과_같은_꼴() {
        assertEquals("123_제목.png", Pixiv.pageName(123, "제목", null, ".png"))
        assertEquals("123_제목_p000.png", Pixiv.pageName(123, "제목", 0, ".png"))
        assertEquals("123_제목_p012.jpg", Pixiv.pageName(123, "제목", 12, ".jpg"))
        assertEquals("123_제목_p1000.jpg", Pixiv.pageName(123, "제목", 1000, ".jpg"))
        assertEquals("123_제목_ugoira.zip", Pixiv.ugoiraName(123, "제목"))
        // 경로 구분자·FAT 금지 글자·방향 제어 문자는 `_`, 제목 끝의 점·빈칸은 뗀다.
        assertEquals("123_a_b_c_d_.png", Pixiv.pageName(123, "a/b\\c:d?", null, ".png"))
        assertEquals("123_gpj._exe_p000.png", Pixiv.pageName(123, "gpj.\u202Eexe", 0, ".png"))
        assertEquals("123_끝_p001.png", Pixiv.pageName(123, " 끝. . ", 1, ".png"))
        assertEquals("123_.png", Pixiv.pageName(123, "", null, ".png"))
    }

    @Test
    fun 긴_제목도_쪽_번호를_잃지_않는다() {
        val title = "가".repeat(500)
        val names = (0 until 3).map { Pixiv.pageName(123456789, title, it, ".png") }
        assertEquals(3, names.toSet().size)
        for ((i, name) in names.withIndex()) {
            assertTrue(name.endsWith("_p00$i.png"), name)
            assertTrue(name.toByteArray(Charsets.UTF_8).size <= SafeFileName.MAX_BYTES, name)
            assertTrue(SiteNames.isClean(name), name)
        }
        val emoji = "😀".repeat(100)
        val name = Pixiv.ugoiraName(1, emoji)
        assertTrue(name.endsWith("_ugoira.zip"))
        assertFalse(SiteNames.hasLoneSurrogate(name))
    }

    @Test
    fun 확장자() {
        assertEquals(".png", Pixiv.extension(pximg(1, 0), "t"))
        assertEquals(".jpg", Pixiv.extension("https://i.pximg.net/a/1_p0.JPG?x=1#y", "t"))
        assertEquals(SiteException.Reason.UNEXPECTED, reason { Pixiv.extension("https://i.pximg.net/a/1_p0", "t") })
        assertEquals(SiteException.Reason.UNEXPECTED, reason { Pixiv.extension("https://i.pximg.net/a.b/1_p0", "t") })
        assertEquals(SiteException.Reason.UNEXPECTED, reason { Pixiv.extension("https://i.pximg.net/a/1_p0.p-g", "t") })
    }
}
