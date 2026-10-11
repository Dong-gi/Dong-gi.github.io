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
    fun 웹이_아니면_없다() {
        assertNull(ref(null, "https://a.test/f"))
        assertNull(ref("about:blank", "https://a.test/f"))
        assertNull(ref("data:text/html,x", "https://a.test/f"))
        assertNull(ref("https://a.test/p", "data:text/plain,x"))
        assertNull(ref("not a url", "https://a.test/f"))
    }
}
