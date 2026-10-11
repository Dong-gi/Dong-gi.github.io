package io.github.donggi.iroiroviewer.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MimeTest {

    @Test
    fun `스크립트와 웹어셈블리는 엔진이 요구하는 형식이다`() {
        // 모듈 스크립트·instantiateStreaming 은 형식을 따진다.
        assertEquals("text/javascript", Mime.of("assets/index.js"))
        assertEquals("text/javascript", Mime.of("x.MJS"))
        assertEquals("application/wasm", Mime.of("pkg/rhwp_bg.wasm"))
        assertEquals("font/woff2", Mime.of("fonts/a.woff2"))
    }

    @Test
    fun `표에 없는 것은 내주지 않는다`() {
        assertNull(Mime.of("x.php"))
        assertNull(Mime.of("noext"))
        assertNull(Mime.of(".htaccess"))
        assertNull(Mime.of("trailing."))
        assertNull(Mime.of("dir.js/file"))
    }

    @Test
    fun `글 형식에만 문자 집합을 붙인다`() {
        assertTrue(Mime.isText("text/html"))
        assertTrue(Mime.isText("image/svg+xml"))
        assertFalse(Mime.isText("application/wasm"))
        assertFalse(Mime.isText("font/woff2"))
    }
}
