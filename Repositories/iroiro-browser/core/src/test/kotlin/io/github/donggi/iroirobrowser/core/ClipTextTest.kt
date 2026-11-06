package io.github.donggi.iroirobrowser.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClipTextTest {

    @Test
    fun 글_전체가_주소면_그대로() {
        assertEquals("https://example.com/a?b=c#d", ClipText.firstLink("  https://example.com/a?b=c#d \n"))
        // 스킴 없는 도메인도 주소창처럼 — 고친 주소가 아니라 친 글을 돌려준다(주소창이 다시 고른다).
        assertEquals("example.com/x", ClipText.firstLink("example.com/x"))
        assertEquals("HTTPS://EXAMPLE.COM", ClipText.firstLink("HTTPS://EXAMPLE.COM"))
        assertEquals("https://example.com/x", ClipText.firstLink("https://example.com/x。"))
    }

    @Test
    fun 글_안의_첫_주소() {
        assertEquals("https://a.example/x", ClipText.firstLink("이것 보세요 https://a.example/x 그리고 https://b.example"))
        assertEquals("http://b.example/y", ClipText.firstLink("앞의 것은 주소가 아니다 http:// 다음 http://b.example/y"))
    }

    @Test
    fun 끝의_문장_부호를_뗀다() {
        assertEquals("https://example.com/x", ClipText.firstLink("여기: https://example.com/x."))
        assertEquals("https://example.com/x", ClipText.firstLink("(https://example.com/x)"))
        assertEquals("https://example.com/x", ClipText.firstLink("보라(https://example.com/x)."))
        // 짝이 맞는 닫는 괄호는 주소의 것이다.
        assertEquals("https://en.wikipedia.org/wiki/Foo_(bar)", ClipText.firstLink("링크 https://en.wikipedia.org/wiki/Foo_(bar), 끝"))
        assertEquals("https://example.com/x", ClipText.firstLink("「https://example.com/x」 의 것"))
    }

    @Test
    fun 꺾쇠와_따옴표에서_끝난다() {
        assertEquals("https://example.com/a", ClipText.firstLink("<a href=\"https://example.com/a\">글</a>"))
        assertEquals("https://example.com/b", ClipText.firstLink("<https://example.com/b>"))
        assertEquals("https://example.com/c", ClipText.firstLink("'https://example.com/c' 를 보라"))
    }

    @Test
    fun 웹_주소만() {
        assertNull(ClipText.firstLink("data:text/html,<p>x</p>"))
        assertNull(ClipText.firstLink("about:blank"))
        assertNull(ClipText.firstLink("javascript:alert(1)"))
        assertNull(ClipText.firstLink("magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567"))
        assertNull(ClipText.firstLink("그냥 글입니다"))
        assertNull(ClipText.firstLink("xhttp://example.com 은 낱말 가운데"))
        assertNull(ClipText.firstLink("https://"))
        assertNull(ClipText.firstLink("   "))
    }

    @Test
    fun 너무_긴_글은_보지_않는다() {
        assertNull(ClipText.firstLink("https://example.com/" + "a".repeat(ClipText.MAX_CHARS)))
        assertEquals("https://example.com/a", ClipText.firstLink("https://example.com/a " + "b".repeat(100)))
    }

    @Test
    fun 이름표는_스킴을_적은_주소만() {
        assertTrue(ClipText.isExplicitLink("https://example.com/a"))
        assertTrue(ClipText.isExplicitLink(" http://10.0.2.2:8080/ "))
        assertFalse(ClipText.isExplicitLink("notes.txt"))
        assertFalse(ClipText.isExplicitLink("example.com"))
        assertFalse(ClipText.isExplicitLink("https://example.com 설명"))
        assertFalse(ClipText.isExplicitLink("data:text/plain,x"))
        assertFalse(ClipText.isExplicitLink("url"))
        assertFalse(ClipText.isExplicitLink(""))
    }
}
