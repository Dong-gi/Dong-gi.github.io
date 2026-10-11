package io.github.donggi.iroirobrowser.core.sites

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class SiteUrlsTest {

    private fun key(url: String): String? = SiteUrls.recognize(url)?.key

    @Test
    fun pixiv_작품_주소() {
        assertEquals("pixiv-artwork-123456", key("https://www.pixiv.net/artworks/123456"))
        assertEquals("pixiv-artwork-123456", key("https://pixiv.net/artworks/123456"))
        assertEquals("pixiv-artwork-123456", key("http://www.pixiv.net/artworks/123456"))
        assertEquals("pixiv-artwork-123456", key("HTTPS://WWW.PIXIV.NET/artworks/123456"))
        assertEquals("pixiv-artwork-123456", key("https://www.pixiv.net/artworks/123456/"))
        assertEquals("pixiv-artwork-123456", key("https://www.pixiv.net/artworks/123456?lang=ko#1"))
        assertEquals("pixiv-artwork-123456", key("https://www.pixiv.net:443/artworks/123456"))
        assertEquals("pixiv-artwork-123456", key("http://www.pixiv.net:80/artworks/123456"))
        assertEquals("pixiv-artwork-123456", key("  https://www.pixiv.net/artworks/123456\n"))
        val target = assertIs<SiteTarget.PixivArtwork>(SiteUrls.recognize("https://www.pixiv.net/en/artworks/123456"))
        assertEquals(123456L, target.id)
        assertEquals("https://www.pixiv.net/artworks/123456", target.canonicalUrl)
    }

    @Test
    fun pixiv_언어_머리() {
        for (lang in listOf("en", "ja", "ko", "zh", "zh-tw", "zh_TW", "EN")) {
            assertEquals("pixiv-artwork-77", key("https://www.pixiv.net/$lang/artworks/77"), lang)
            assertEquals("pixiv-user-88", key("https://www.pixiv.net/$lang/users/88"), lang)
        }
        assertNull(key("https://www.pixiv.net/english/artworks/77"))
        assertNull(key("https://www.pixiv.net/e1/artworks/77"))
        assertNull(key("https://www.pixiv.net/en/ko/artworks/77"))
        assertNull(key("https://www.pixiv.net/zh-/artworks/77"))
    }

    @Test
    fun pixiv_옛_주소() {
        assertEquals("pixiv-artwork-55", key("https://www.pixiv.net/member_illust.php?mode=medium&illust_id=55"))
        assertEquals("pixiv-artwork-55", key("https://www.pixiv.net/member_illust.php?illust_id=55&mode=manga"))
        assertEquals("pixiv-user-11", key("https://www.pixiv.net/member.php?id=11"))
        // 작가의 작품 목록(옛 주소) — 작가 받기다.
        assertEquals("pixiv-user-11", key("https://www.pixiv.net/member_illust.php?id=11"))
        assertNull(key("https://www.pixiv.net/member_illust.php?mode=medium&illust_id=abc"))
        assertNull(key("https://www.pixiv.net/member_illust.php?illust_id=1&illust_id=2"))
        assertNull(key("https://www.pixiv.net/member.php"))
        assertNull(key("https://www.pixiv.net/member.php?id="))
        assertNull(key("https://www.pixiv.net/member.php?id=%31%31"))
        assertNull(key("https://www.pixiv.net/member_illust.php?mode=medium&illust_id=0"))
    }

    @Test
    fun pixiv_작가_주소() {
        assertEquals("pixiv-user-11", key("https://www.pixiv.net/users/11"))
        assertEquals("pixiv-user-11", key("https://www.pixiv.net/users/11/"))
        assertEquals("pixiv-user-11", key("https://www.pixiv.net/users/11/illustrations"))
        assertEquals("pixiv-user-11", key("https://www.pixiv.net/users/11/manga"))
        assertEquals("pixiv-user-11", key("https://www.pixiv.net/users/11/artworks"))
        assertEquals("https://www.pixiv.net/users/11", SiteUrls.recognize("https://www.pixiv.net/ko/users/11/manga?p=2")?.canonicalUrl)
        // 작가의 북마크·소설·팔로·태그로 거른 목록은 그 작가의 작품 받기가 아니다.
        assertNull(key("https://www.pixiv.net/users/11/bookmarks/artworks"))
        assertNull(key("https://www.pixiv.net/users/11/novels"))
        assertNull(key("https://www.pixiv.net/users/11/following"))
        assertNull(key("https://www.pixiv.net/users/11/illustrations/태그"))
    }

    @Test
    fun pixiv_그_밖의_쪽은_null() {
        assertNull(key("https://www.pixiv.net/"))
        assertNull(key("https://www.pixiv.net"))
        assertNull(key("https://www.pixiv.net/tags/x/artworks"))
        assertNull(key("https://www.pixiv.net/ranking.php"))
        assertNull(key("https://www.pixiv.net/novel/show.php?id=5"))
        assertNull(key("https://www.pixiv.net/artworks/"))
        assertNull(key("https://www.pixiv.net/artworks/12/34"))
        assertNull(key("https://www.pixiv.net/artworks/12a"))
        assertNull(key("https://www.pixiv.net/artworks/-12"))
        assertNull(key("https://www.pixiv.net/artworks/0"))
        assertNull(key("https://www.pixiv.net/artworks/0123"))
        assertNull(key("https://www.pixiv.net/artworks/1234567890123"))
        assertNull(key("https://www.pixiv.net//artworks/12"))
        assertNull(key("https://www.pixiv.net/artworks//12"))
        assertNull(key("https://www.pixiv.net/i/12"))
    }

    @Test
    fun 적대적인_호스트와_권한부() {
        assertNull(key("https://www.pixiv.net.evil.com/artworks/1"))
        assertNull(key("https://evilpixiv.net/artworks/1"))
        assertNull(key("https://pixiv.net.evil.com/artworks/1"))
        assertNull(key("https://evil.com/www.pixiv.net/artworks/1"))
        assertNull(key("https://evil.com/?u=https://www.pixiv.net/artworks/1"))
        assertNull(key("https://evil.com#https://www.pixiv.net/artworks/1"))
        assertNull(key("https://user@www.pixiv.net/artworks/1"))
        assertNull(key("https://www.pixiv.net@evil.com/artworks/1"))
        assertNull(key("https://user:pass@www.pixiv.net/artworks/1"))
        assertNull(key("https://www.pixiv.net./artworks/1"))
        assertNull(key("https://www%2Epixiv.net/artworks/1"))
        assertNull(key("https://www.pixiv.net:8443/artworks/1"))
        assertNull(key("https://www.pixiv.net:80/artworks/1"))
        assertNull(key("https://www.pixiv.net:x/artworks/1"))
        assertNull(key("https://[::1]/artworks/1"))
        assertNull(key("https://touch.pixiv.net/artworks/1"))
        assertNull(key("https://i.pximg.net/artworks/1"))
        assertNull(key("ftp://www.pixiv.net/artworks/1"))
        assertNull(key("javascript://www.pixiv.net/artworks/1"))
        assertNull(key("file:///www.pixiv.net/artworks/1"))
        assertNull(key("https:/www.pixiv.net/artworks/1"))
        assertNull(key("https:\\\\www.pixiv.net\\artworks\\1"))
        assertNull(key("https://www.pixiv.net\\artworks\\1"))
        assertNull(key("https://www.pixiv.net/artworks\\1"))
        assertNull(key("//www.pixiv.net/artworks/1"))
        assertNull(key("www.pixiv.net/artworks/1"))
        assertNull(key("https://www.pixiv.net/artworks/1 2"))
        assertNull(key("https://www.pïxiv.net/artworks/1"))
        assertNull(key("https://xn--www-pixiv-net/artworks/1"))
        assertNull(key("https://hitomi.la.evil.com/reader/1.html"))
        assertNull(key("https://www.hitomi.la/reader/1.html"))
        assertNull(key("https://me@hitomi.la/reader/1.html"))
        assertNull(key(""))
        assertNull(key(":"))
        assertNull(key("https://"))
        assertNull(key("https://www.pixiv.net/artworks/" + "1".repeat(9000)))
    }

    @Test
    fun hitomi_갤러리_주소() {
        for (kind in listOf("galleries", "reader", "doujinshi", "manga", "cg", "gamecg", "imageset", "anime")) {
            assertEquals("hitomi-gallery-1234567", key("https://hitomi.la/$kind/1234567.html"), kind)
        }
        assertEquals("hitomi-gallery-1234567", key("https://hitomi.la/doujinshi/some-long-title-1234567.html"))
        assertEquals("hitomi-gallery-1234567", key("https://hitomi.la/manga/title-2-1234567.html"))
        assertEquals("hitomi-gallery-1234567", key("https://hitomi.la/reader/1234567.html#5"))
        assertEquals("hitomi-gallery-1234567", key("https://hitomi.la/galleries/1234567.html?x=99"))
        assertEquals("hitomi-gallery-1234567", key("HTTP://HITOMI.LA/Reader/1234567.HTML"))
        assertEquals("hitomi-gallery-1234567", key("https://hitomi.la/doujinshi/한글-제목-1234567.html"))
        val target = assertIs<SiteTarget.HitomiGallery>(SiteUrls.recognize("https://hitomi.la/manga/x-42.html#page-3"))
        assertEquals("https://hitomi.la/reader/42.html", target.canonicalUrl)
    }

    @Test
    fun hitomi_태그_검색_목록은_null() {
        assertNull(key("https://hitomi.la/"))
        assertNull(key("https://hitomi.la/index-all-1.html"))
        assertNull(key("https://hitomi.la/index-korean.html"))
        assertNull(key("https://hitomi.la/tag/female%3Asole-all-1.html"))
        assertNull(key("https://hitomi.la/artist/name-all-1.html"))
        assertNull(key("https://hitomi.la/group/name-all-2.html"))
        assertNull(key("https://hitomi.la/series/name-all-1.html"))
        assertNull(key("https://hitomi.la/type/doujinshi-all-1.html"))
        assertNull(key("https://hitomi.la/search.html?query"))
        assertNull(key("https://hitomi.la/reader/abc.html"))
        assertNull(key("https://hitomi.la/reader/.html"))
        assertNull(key("https://hitomi.la/reader/1234567"))
        assertNull(key("https://hitomi.la/reader/1234567.htm"))
        assertNull(key("https://hitomi.la/reader/1234567.html/"))
        assertNull(key("https://hitomi.la/reader/x/1234567.html"))
        assertNull(key("https://hitomi.la/doujinshi/title1234567.html"))
        assertNull(key("https://hitomi.la/doujinshi/title_1234567.html"))
        assertNull(key("https://hitomi.la/reader/0.html"))
        assertNull(key("https://hitomi.la/reader/007.html"))
        assertNull(key("https://hitomi.la/reader/1234567890123.html"))
        assertNull(key("https://hitomi.la/1234567.html"))
        assertNull(key("https://hitomi.la:8080/reader/1.html"))
    }

    @Test
    fun 대상의_같음과_열쇠() {
        assertEquals(SiteTarget.PixivArtwork(5), SiteTarget.PixivArtwork(5))
        assertEquals(SiteTarget.PixivArtwork(5).hashCode(), SiteTarget.PixivArtwork(5).hashCode())
        kotlin.test.assertNotEquals<SiteTarget>(SiteTarget.PixivArtwork(5), SiteTarget.PixivUser(5))
        assertEquals("hitomi-gallery-9", SiteTarget.HitomiGallery(9).toString())
    }
}
