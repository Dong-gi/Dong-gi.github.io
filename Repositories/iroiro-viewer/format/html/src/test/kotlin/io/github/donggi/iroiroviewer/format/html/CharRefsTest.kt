package io.github.donggi.iroiroviewer.format.html

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 속성 값의 문자 참조를 브라우저가 읽는 대로 푸는가 — 그리고 모르는 것을 '모른다' 고 말하는가. */
class CharRefsTest {

    private fun text(s: String) = CharRefs.decode(s).text

    @Test
    fun CSS_의_기호를_적은_이름_참조() {
        // 우회: 브라우저에게 이것은 `url(https://x)` 다.
        assertEquals("background:url(https://x)", text("background:url&lpar;https://x&rpar;"))
        assertEquals("a\\b{}[]:;,./\"'#!%+=?@^_`|*", text("a&bsol;b&lcub;&rcub;&lsqb;&rsqb;&colon;&semi;&comma;&period;&sol;&quot;&apos;&num;&excl;&percnt;&plus;&equals;&quest;&commat;&Hat;&lowbar;&grave;&vert;&ast;"))
        assertEquals("a\tb\nc", text("a&Tab;b&NewLine;c"))
        assertFalse(CharRefs.decode("&lpar;&rpar;").unknown)
    }

    @Test
    fun 숫자_참조는_언제나_푼다() {
        assertEquals("A(", text("&#65&#x28;"))
        assertEquals("😀", text("&#x1F600;"))
        assertEquals("\uFFFD\uFFFD\uFFFD", text("&#0;&#x110000;&#xD800;"))
        assertEquals("\uFFFD", text("&#99999999999999999999;"))
        assertEquals("&#;&#x;", text("&#;&#x;"))
    }

    @Test
    fun 세미콜론_없는_옛_이름은_넷만_뒤가_영숫자나_등호가_아닐_때만() {
        assertEquals("a & b", text("a &amp b"))
        assertEquals("\" x\" <", text("&quot x&QUOT; &lt"))
        // 속성 안의 `&copy=2`·`&ampx` 는 질의·글이다(명세).
        assertEquals("?a=1&copy=2&ampx&amp=", text("?a=1&copy=2&ampx&amp="))
        assertFalse(CharRefs.decode("?a=1&copy=2").unknown)
    }

    @Test
    fun 표에_없는_이름이_남으면_모른다고_말한다() {
        val d = CharRefs.decode("background:url&lparen;x&rparen;")
        assertTrue(d.unknown)
        assertTrue(CharRefs.decode("&NoSuchName;").unknown)
        // `&T;` 는 브라우저가 풀지 않는 글이지만 이름 꼴이라 '모른다' 로 본다 — 모르면 버리는 쪽으로 틀린다(드물다).
        assertTrue(CharRefs.decode("font-family:AT&T;").unknown)
        // 이름 꼴이 아니거나 `;` 가 없으면 참조가 아니다 — 글자 그대로다.
        assertFalse(CharRefs.decode("a & b; &1; &-x;").unknown)
        assertFalse(CharRefs.decode("a & b").unknown)
        assertFalse(CharRefs.decode("&nosemicolon").unknown)
        assertFalse(CharRefs.decode("&copy; &nbsp; &mdash;").unknown)
        assertEquals("© \u00A0 —", text("&copy; &nbsp; &mdash;"))
    }
}
