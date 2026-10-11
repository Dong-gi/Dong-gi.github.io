package io.github.donggi.iroirobrowser.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinkRenewalTest {

    @Test
    fun 질의만_다른_서명된_주소는_같은_자원이다() {
        assertTrue(
            LinkRenewal.sameResource(
                "https://cdn.test/v/720p/index.m3u8?Expires=1&Signature=a",
                "https://CDN.test:443/v/720p/index.m3u8?Expires=2&Signature=b#t=1",
            ),
        )
        assertTrue(LinkRenewal.sameResource("http://a.test/f.zip", "http://a.test:80/f.zip?token=x"))
    }

    @Test
    fun 경로_호스트_스킴이_다르면_다르다() {
        // 다른 화질(경로)·다른 서버·경로에 표를 넣는 CDN — 잇지 않고 새로 받는다.
        assertFalse(LinkRenewal.sameResource("https://cdn.test/v/720p/index.m3u8", "https://cdn.test/v/1080p/index.m3u8"))
        assertFalse(LinkRenewal.sameResource("https://cdn1.test/v/a.m3u8", "https://cdn2.test/v/a.m3u8"))
        assertFalse(LinkRenewal.sameResource("https://cdn.test/tok/abc/a.m3u8", "https://cdn.test/tok/def/a.m3u8"))
        assertFalse(LinkRenewal.sameResource("http://cdn.test/a.mp4", "https://cdn.test/a.mp4"))
        assertFalse(LinkRenewal.sameResource("https://cdn.test:8443/a.mp4", "https://cdn.test/a.mp4"))
        assertFalse(LinkRenewal.sameResource("not a url", "not a url"))
    }

    @Test
    fun 같은_페이지는_조각만_보지_않는다() {
        assertTrue(LinkRenewal.samePage("https://site.test/watch?v=1#t=30", "https://site.test/watch?v=1"))
        assertFalse(LinkRenewal.samePage("https://site.test/watch?v=1", "https://site.test/watch?v=2"))
        assertFalse(LinkRenewal.samePage(null, "https://site.test/"))
        assertFalse(LinkRenewal.samePage("", ""))
    }

    @Test
    fun 줄기는_차례대로_모두_같아야_한다() {
        val old = listOf("https://c.test/v/720/i.m3u8?s=1", "https://c.test/a/ko/i.m3u8?s=1")
        assertTrue(LinkRenewal.sameTracks(old, listOf("https://c.test/v/720/i.m3u8?s=2", "https://c.test/a/ko/i.m3u8?s=2")))
        // 따로 된 소리가 없어졌거나 차례가 바뀌었다.
        assertFalse(LinkRenewal.sameTracks(old, listOf("https://c.test/v/720/i.m3u8?s=2")))
        assertFalse(LinkRenewal.sameTracks(old, old.reversed()))
        assertFalse(LinkRenewal.sameTracks(emptyList(), emptyList()))
    }
}
