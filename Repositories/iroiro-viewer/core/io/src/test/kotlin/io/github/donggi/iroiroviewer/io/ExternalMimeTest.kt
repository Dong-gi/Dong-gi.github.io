package io.github.donggi.iroiroviewer.io

import io.github.donggi.iroiroviewer.model.FileKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 다른 앱으로 열 때의 MIME 차례(우리 표 → 플랫폼 표 → 종류의 대표 → `*`/`*`). 플랫폼 표는 함수로 받으므로
 * 기기 없이 차례를 박을 수 있다.
 */
class ExternalMimeTest {

    private val none: (String) -> String? = { null }

    @Test
    fun `우리 표가 먼저다`() {
        assertEquals("application/vnd.android.package-archive", ExternalMime.resolve("app.apk", none))
        // 이 앱이 열지 않는 형식도 표에 있다 — 받는 앱이 알아보게 하려고.
        assertEquals("application/msword", ExternalMime.resolve("옛문서.DOC", none))
        assertEquals("application/vnd.oasis.opendocument.text", ExternalMime.resolve("a.odt", none))
        assertEquals("font/ttf", ExternalMime.resolve("글꼴.ttf", none))
        assertEquals("video/x-flv", ExternalMime.resolve("clip.flv", none))
        // 플랫폼이 다른 값을 줘도 우리 표가 이긴다.
        assertEquals("application/pdf", ExternalMime.resolve("x.pdf") { "application/x-pdf" })
    }

    @Test
    fun `우리가 모르면 플랫폼 표를 쓴다`() {
        assertEquals("application/x-foo", ExternalMime.resolve("a.foo") { if (it == "foo") "application/x-foo" else null })
        // 확장자는 소문자로 묻고, 대답의 공백·대문자는 다듬는다.
        assertEquals("application/x-foo", ExternalMime.resolve("A.FOO") { if (it == "foo") " Application/X-Foo " else null })
    }

    @Test
    fun `플랫폼의 모호하거나 이상한 대답은 쓰지 않는다`() {
        for (bad in listOf("application/octet-stream", "", "foo", "a/b/c", "image/*", "text/plain; charset=utf-8", "/x", "x/")) {
            assertEquals(ExternalMime.ANY, ExternalMime.resolve("a.qqq") { bad }, bad)
        }
    }

    @Test
    fun `종류만 알면 그 계열 전체로 연다`() {
        // `ape` 는 등록된 MIME 이 없다 — 소리를 받는 앱이 모두 나오게 한다.
        assertEquals("audio/*", ExternalMime.resolve("노래.ape", none))
        assertEquals("audio/*", ExternalMime.resolve("x.alac", none))
    }

    @Test
    fun `계열 밖의 등록 MIME 은 열 때 계열 전체가 된다`() {
        // 영상 앱의 필터(영상 계열 전체)는 `application/…` 을 받지 않는다. 등록 MIME 을 주면 영상 앱이 하나도 안 나온다.
        assertEquals("video/*", ExternalMime.resolve("영화.rmvb", none))
        // 공유는 등록된 값을 그대로 쓴다.
        assertEquals("application/vnd.rn-realmedia-vbr", MimeResolver.mimeOf("영화.rmvb"))
    }

    @Test
    fun `플랫폼의 답도 계열 밖이면 쓰지 않는다`() {
        assertEquals("audio/*", ExternalMime.resolve("노래.ape") { "application/x-ape" })
        assertEquals("audio/x-ape", ExternalMime.resolve("노래.ape") { "audio/x-ape" })
        // 계열을 가리지 않는 종류(기타)는 플랫폼의 답을 그대로 쓴다.
        assertEquals("application/x-ape", ExternalMime.resolve("a.qqq") { "application/x-ape" })
    }

    @Test
    fun `글과 코드는 편집기가 받는 text_plain 으로 연다`() {
        // 편집기가 선언하는 것은 `text/plain`(과 `text` 계열)이다. 플랫폼의 `text/x-python` 은 앞의 것에,
        // `application/javascript` 는 둘 다에 닿지 않는다.
        assertEquals("text/plain", ExternalMime.resolve("a.py") { "text/x-python" })
        assertEquals("text/plain", ExternalMime.resolve("a.js") { "application/javascript" })
        assertEquals("text/plain", ExternalMime.resolve("README.md") { "text/markdown" })
        // `application/json` 은 `text` 계열 밖이다 — JSON 만 받는 앱보다 편집기가 훨씬 많다.
        assertEquals("text/plain", ExternalMime.resolve("설정.json", none))
        // 우리 표가 `text` 계열로 적은 것은 그대로다(브라우저·표 앱).
        assertEquals("text/html", ExternalMime.resolve("a.html", none))
        assertEquals("text/xml", ExternalMime.resolve("a.xml", none))
        assertEquals("text/csv", ExternalMime.resolve("a.csv", none))
    }

    @Test
    fun `ts 는 영상이다`() {
        // MPEG-TS 와 TypeScript 가 확장자를 나눠 쓴다. 이 앱은 영상으로 본다(목록·재생·열기가 모두 같은 답).
        assertEquals(FileKind.VIDEO, MimeResolver.kindOf("녹화.ts", isDirectory = false))
        assertEquals("video/mp2t", ExternalMime.resolve("녹화.ts", none))
    }

    @Test
    fun `아무것도 모르면 모든 것이다`() {
        assertEquals(ExternalMime.ANY, ExternalMime.resolve("README", none))
        assertEquals(ExternalMime.ANY, ExternalMime.resolve("data.bin", none))
        assertEquals(ExternalMime.ANY, ExternalMime.resolve(".hidden", none))
    }

    @Test
    fun `와일드카드면 기본 앱을 정하게 두지 않는다`() {
        assertTrue(ExternalMime.isWildcard(ExternalMime.ANY))
        assertTrue(ExternalMime.isWildcard("video/*"))
        assertFalse(ExternalMime.isWildcard("video/mp4"))
        assertFalse(ExternalMime.isWildcard("application/vnd.android.package-archive"))
    }
}
