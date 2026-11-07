package io.github.donggi.iroirobrowser.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReferrerTest {

    private fun ref(page: String?, target: String) = Referrer.strictOriginWhenCrossOrigin(page, target)

    @Test
    fun 같은_출처는_주소_전체_조각은_뺀다() {
        assertEquals("https://a.test/p/q?x=1", ref("https://a.test/p/q?x=1#frag", "https://a.test/file.zip"))
        assertEquals("http://a.test:8080/p", ref("http://a.test:8080/p", "http://A.test:8080/f"))
        assertEquals("https://a.test/p", ref("https://a.test/p", "https://a.test:443/f"))
    }

    @Test
    fun 다른_출처는_출처만() {
        assertEquals("https://a.test/", ref("https://a.test/secret/path?token=1", "https://cdn.test/f"))
        assertEquals("https://a.test/", ref("https://a.test/p", "https://a.test:8443/f"))
        assertEquals("http://a.test/", ref("http://a.test/p", "https://a.test/f"))
    }

    @Test
    fun https_에서_http_로는_보내지_않는다() {
        assertNull(ref("https://a.test/p", "http://a.test/f"))
    }

    @Test
    fun 넘겨주기의_홉마다_쪽에서_다시_고른다() {
        // 같은 출처의 처음 홉 → 다른 출처로 넘겨줌 → 다시 쪽의 출처로(처음 홉의 값을 모든 홉에 싣지 않는다).
        val page = "https://a.test/watch?v=1&token=secret#t"
        val hops = listOf("https://a.test/dl/file.mp4", "https://cdn.test/x/file.mp4?sig=1", "https://a.test/again", "http://plain.test/f")
        assertEquals(listOf("https://a.test/watch?v=1&token=secret", "https://a.test/", "https://a.test/watch?v=1&token=secret", null), hops.map { ref(page, it) })
        // HLS — 재생목록은 쪽과 같은 출처, 조각·열쇠는 다른 출처.
        val hls = listOf("https://a.test/hls/index.m3u8", "https://seg.test/s1.ts", "https://keys.test/k.bin")
        assertEquals(listOf("https://a.test/watch?v=1&token=secret", "https://a.test/", "https://a.test/"), hls.map { ref(page, it) })
    }

    @Test
    fun 사이트가_정한_Referer_는_처음_홉에만_그대로() {
        val chosen = "https://hitomi.test/reader/1.html"
        val first = "https://img.test/1/a.webp"
        assertEquals(chosen, Referrer.pinnedFirstHop(chosen, first, first), "처음 홉은 사이트의 규약 그대로(다른 출처여도)")
        assertEquals("https://hitomi.test/", Referrer.pinnedFirstHop(chosen, first, "https://other.test/a.webp"), "넘겨주기 뒤 다른 출처는 출처만")
        assertEquals(chosen, Referrer.pinnedFirstHop(chosen, first, "https://hitomi.test/b"))
        assertNull(Referrer.pinnedFirstHop(chosen, first, "http://img.test/1/a.webp"))
        assertNull(Referrer.pinnedFirstHop(null, first, "https://other.test/a"))
        assertNull(Referrer.pinnedFirstHop(null, first, first))
    }

    @Test
    fun 웹이_아니면_없다() {
        assertNull(ref(null, "https://a.test/f"))
        assertNull(ref("about:blank", "https://a.test/f"))
        assertNull(ref("data:text/html,x", "https://a.test/f"))
        assertNull(ref("https://a.test/p", "data:text/plain,x"))
        assertNull(ref("not a url", "https://a.test/f"))
    }
}
