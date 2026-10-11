package io.github.donggi.iroirobrowser.core.adblock

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class UserHidesTest {

    private fun rule(host: String, selector: String) = UserHides.rule(host, selector)!!

    @Test
    fun 그_사이트에서만_걸린다() {
        val hides = UserHides(listOf(rule("www.example.com", "div.ad"), rule("news.example.co.kr", "#top"), rule("alice.github.io", "aside")))
        assertEquals(listOf("example.com", "example.co.kr", "alice.github.io"), hides.rules.map { it.site })
        // 그 사이트(등록 도메인)와 그 아래 이름에만.
        listOf("www.example.com", "m.example.com", "example.com", "WWW.EXAMPLE.COM").forEach {
            assertEquals(listOf("div.ad"), hides.selectorsFor(it), it)
        }
        listOf("example.org", "notexample.com", "example.com.evil.test", "").forEach { assertTrue(hides.selectorsFor(it).isEmpty(), it) }
        assertEquals(listOf("#top"), hides.selectorsFor("www.example.co.kr"))
        assertTrue(hides.selectorsFor("other.co.kr").isEmpty())
        // 호스팅 접미사는 사이트마다 따로다.
        assertEquals(listOf("aside"), hides.selectorsFor("alice.github.io"))
        assertTrue(hides.selectorsFor("bob.github.io").isEmpty())
        // 광고 차단의 요소 숨김과 같은 CSS.
        assertEquals("div.ad{display:none!important}\n", hides.cssFor("www.example.com"))
        assertEquals("", hides.cssFor("example.org"))
    }

    @Test
    fun 광고_목록의_예외와_따로다() {
        // 목록이 그 사이트에서 요소 숨김을 끄고(`$elemhide`) 같은 선택자를 풀어도(`#@#`) 사용자의 규칙은 걸린다 — 따로 된 엔진이다.
        val lists = AdBlocker(listOf(FilterParser.parse("@@||example.com^\$elemhide\nexample.com#@#div.ad\n##div.ad")))
        val page = lists.page("https://www.example.com/")
        assertEquals("", lists.startCss(page) + lists.genericCss(page, listOf("ad"), emptyList()))
        assertEquals("div.ad{display:none!important}\n", UserHides(listOf(rule("www.example.com", "div.ad"))).cssFor("www.example.com"))
    }

    @Test
    fun 저장_글의_왕복() {
        val hides = UserHides(emptyList())
            .plus(rule("www.example.com", "div.ad"))
            .plus(rule("example.com", "#a\\7b b > span:nth-of-type(2)"))
            .plus(rule("xn--3e0b707e.kr", "div[data-testid=ad\\20 slot]"))
            .plus(rule("10.0.2.2", ".광고"))
        val text = hides.format()
        assertTrue(text.startsWith("! "))
        assertEquals(5, text.lines().count { it.isNotEmpty() })
        val back = UserHides.parse(text)
        assertEquals(hides.rules, back.rules)
        assertEquals(text, back.format())
        // CRLF 로 바뀐 파일도 같다.
        assertEquals(hides.rules, UserHides.parse(text.replace("\n", "\r\n")).rules)
    }

    @Test
    fun 광고_목록과_같은_꼴이다() {
        val hides = UserHides(listOf(rule("example.com", "div.ad"), rule("example.com", "#a\\7b b")))
        val parsed = FilterParser.parse(hides.format())
        assertEquals(listOf("div.ad", "#a\\7b b"), parsed.cosmetic.map { it.selector })
        assertEquals(listOf("example.com"), parsed.cosmetic.flatMap { it.include }.distinct())
    }

    @Test
    fun 읽을_수_없는_줄은_버린다() {
        val text = listOf(
            "! 주석", "[Adblock Plus 2.0]", "", "example.com##div.ad", "example.com##div.ad", "##.no-site", "Example.com##div.upper",
            "a,b.com##div", "~x.com##div", "x.com##body{display:none}x", "x.com##div[x", "x.com##", "  y.com ##  .ok  ", "x.com#@#div",
        ).joinToString("\n")
        assertEquals(listOf("example.com##div.ad", "example.com##div.upper", "y.com##.ok"), UserHides.parse(text).rules.map { it.toString() })
    }

    @Test
    fun 규칙을_더하고_뺀다() {
        val a = rule("a.example.com", "div.ad")
        val b = rule("example.com", "#top")
        val c = rule("other.test", "aside")
        val hides = UserHides(listOf(a, b, c, a))
        assertEquals(3, hides.size)
        assertEquals(listOf("example.com", "other.test"), hides.sites())
        assertEquals(listOf(a, b), hides.rulesOf("example.com"))
        assertSame(hides, hides.plus(a))
        assertTrue(a in hides)
        assertEquals(listOf(b, c), hides.minus(a).rules)
        assertEquals(listOf(c), hides.minusSite("example.com").rules)
        assertEquals(listOf(c, a, b), hides.minusSite("example.com").plus(listOf(a, b)).rules)
    }

    @Test
    fun 쓸_수_없는_규칙은_만들지_않는다() {
        assertNull(UserHides.rule("example.com", "body{display:none}"))
        assertNull(UserHides.rule("example.com", "  "))
        assertNull(UserHides.rule("", "div"))
        assertNull(UserHides.rule("a,b.com", "div"))
        assertEquals("example.com##div.ad", UserHides.rule("WWW.Example.COM.", " div.ad ").toString())
        assertEquals("example.com", UserHides.siteOf("m.example.com"))
    }
}
