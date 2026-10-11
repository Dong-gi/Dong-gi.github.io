package io.github.donggi.iroirobrowser.core.adblock

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HideSelectorsTest {

    private fun node(
        tag: String,
        id: String = "",
        classes: String = "",
        nth: Int = 1,
        of: Int = 1,
        attributes: List<Pair<String, String>> = emptyList(),
    ) = PickedNode(tag, id, classes.split(' ').filter { it.isNotEmpty() }, nth, of, attributes)

    private val body = node("body")

    // ── 이름 가리기 ──

    @Test
    fun 무작위_이름() {
        val random = listOf(
            "css-1x2y3z", "css-175oi2r", "css-1abc2de-Button", "sc-bdVaJa", "sc-AxjAm", "jsx-2849182930", "svelte-1abcd2",
            "r-1awozwy", "x1n2onr6", "xdt5ytf", "Button_root__3xYz9", "styles_container__A1b2C", "ng-tns-c55-1", "ember1234",
            "ui-id-12", "mui-3471", ":r1:", "radix-:r3:", "«r5»", "a3f9c2e1", "123e4567-e89b-12d3-a456-426614174000", "jZHkUe",
            "GKUQ5P6BMB", "rnnxgb", "a".repeat(65),
        )
        random.forEach { assertTrue(HideSelectors.isRandomName(it), it) }
        val stable = listOf(
            "ad-banner", "header", "top100", "h264", "mp4player", "col-md-6", "navBarTop", "iPhone", "MyAdBox", "card__title",
            "block--modifier", "div-gpt-ad-1546875362548-0", "onetrust-banner-sdk", "ad300x250", "banner728x90",
            "taboola-below-article", "sidebarColumn", "CybotCookiebotDialog", "__next", "sc-section", "google_ads_iframe_/1234/name_0",
            "광고-영역",
        )
        stable.forEach { assertFalse(HideSelectors.isRandomName(it), it) }
    }

    @Test
    fun 상태와_꼴의_이름() {
        listOf("active", "is-open", "has-dropdown", "ng-touched", "sticky", "lazyloaded").forEach { assertTrue(HideSelectors.isStateName(it), it) }
        listOf("ad-banner", "island", "hasty").forEach { assertFalse(HideSelectors.isStateName(it), it) }
        listOf("row", "container", "mt-3", "col-md-6", "d-none", "w-full", "mx-auto", "md:flex", "w-1/2", "w-[300px]")
            .forEach { assertTrue(HideSelectors.isUtility(it), it) }
        listOf("ad-banner", "top-ad", "sponsored", "ad300x250").forEach { assertFalse(HideSelectors.isUtility(it), it) }
    }

    // ── CSS 의 글 ──

    @Test
    fun 식별자_이스케이프() {
        assertEquals("ad-banner", HideSelectors.escape("ad-banner"))
        assertEquals("\\31 23", HideSelectors.escape("123"))
        assertEquals("-\\31 x", HideSelectors.escape("-1x"))
        assertEquals("\\-", HideSelectors.escape("-"))
        assertEquals("md\\:flex", HideSelectors.escape("md:flex"))
        assertEquals("w-1\\/2", HideSelectors.escape("w-1/2"))
        assertEquals("광고", HideSelectors.escape("광고"))
        assertEquals("a\\a b", HideSelectors.escape("a\nb"))
        assertEquals("a\\20 b", HideSelectors.escape("a b"))
        assertEquals("q\\\"", HideSelectors.escape("q\""))
        assertEquals("x\\7b \\7d body\\7b display\\:none\\7d ", HideSelectors.escape("x{}body{display:none}"))
    }

    @Test
    fun 페이지가_정한_이름이_규칙을_벗어나지_못한다() {
        // id·클래스는 페이지의 것이다 — 규칙을 끝내고 body 를 숨기는 규칙을 새로 열려는 이름도 그 요소 하나의 이름으로만 남는다.
        val evil = listOf(node("div", id = "x{}body{display:none}", classes = "a;b c\nd"), body)
        val candidates = HideSelectors.candidates(evil)
        assertTrue(candidates.isNotEmpty())
        for (c in candidates) {
            assertTrue(HideSelectors.isSafeSelector(c), c)
            val css = CosmeticEngine.css(listOf(c))
            assertEquals(1, css.count { it == '{' }, css)
            assertEquals(1, css.count { it == '}' }, css)
            assertEquals(1, css.count { it == '\n' }, css)
        }
        assertEquals("#x\\7b \\7d body\\7b display\\:none\\7d", candidates.first())
    }

    @Test
    fun 안전한_선택자() {
        listOf("div.ad > span:nth-of-type(2)", "#a\\7b b", "div[data-testid=ad\\20 slot]", "#q\\\"", "#a\\{b")
            .forEach { assertTrue(HideSelectors.isSafeSelector(it), it) }
        listOf("body{display:none}x", "a;b", "a[href=\"x\"]", "a[href='x']", "div[x", "div:not(.a", "a)", "a\nb", "a\\", "", "  ", "a".repeat(1001))
            .forEach { assertFalse(HideSelectors.isSafeSelector(it), it) }
    }

    // ── 선택자 짓기 ──

    @Test
    fun 후보는_id_클래스_조상_경로의_차례() {
        val chain = listOf(node("div", id = "top-ad", classes = "ad-slot wide"), node("main", id = "content"), body)
        assertEquals(
            listOf(
                "#top-ad",
                "div.ad-slot.wide",
                "#content div.ad-slot.wide",
                "#content > div.ad-slot.wide",
                "body > main > div.ad-slot.wide",
            ),
            HideSelectors.candidates(chain),
        )
    }

    @Test
    fun 무작위_상태_꼴_이름은_버리고_가까운_조상에서_내려간다() {
        val chain = listOf(
            node("div", id = ":r7:", classes = "css-1x2y3z active row", nth = 2, of = 3),
            node("div", classes = "sc-bdVaJa"),
            node("section", id = "feed", nth = 1, of = 2),
            body,
        )
        assertEquals(
            listOf("#feed > div > div:nth-of-type(2)", "body > section:nth-of-type(1) > div > div:nth-of-type(2)"),
            HideSelectors.candidates(chain),
        )
    }

    @Test
    fun 시험용_속성() {
        val chain = listOf(
            node("div", classes = "css-1dbjc4n r-1awozwy", attributes = listOf("data-testid" to "sidebarColumn")),
            node("div", attributes = listOf("data-testid" to "primaryColumn")),
            body,
        )
        assertEquals(
            listOf(
                "div[data-testid=sidebarColumn]",
                "div[data-testid=primaryColumn] div[data-testid=sidebarColumn]",
                "div[data-testid=primaryColumn] > div",
                "body > div > div",
            ),
            HideSelectors.candidates(chain),
        )
        // 목록에 없는 속성은 쓰지 않는다.
        val other = listOf(node("div", attributes = listOf("onclick" to "x")), body)
        assertEquals(listOf("body > div"), HideSelectors.candidates(other))
    }

    @Test
    fun 흔한_클래스_하나로는_홀로_쓰지_않는다() {
        val chain = listOf(node("span", classes = "title", nth = 2, of = 2), node("div", id = "news"), body)
        assertEquals(listOf("#news > span:nth-of-type(2)", "body > div > span:nth-of-type(2)"), HideSelectors.candidates(chain))
    }

    @Test
    fun 경로의_걸음에도_쓸_만한_클래스를_붙인다() {
        val chain = listOf(node("a", nth = 3, of = 4), node("li", classes = "promo item", nth = 2, of = 5), node("ul", id = "links"), body)
        val c = HideSelectors.candidates(chain)
        assertEquals("li.promo > a:nth-of-type(3)", c.first())
        assertTrue("#links > li.promo:nth-of-type(2) > a:nth-of-type(3)" in c, c.toString())
        assertEquals("body > ul > li.promo:nth-of-type(2) > a:nth-of-type(3)", c.last())
    }

    @Test
    fun 페이지_전체는_고르지_않는다() {
        assertTrue(HideSelectors.candidates(listOf(body)).isEmpty())
        assertTrue(HideSelectors.candidates(listOf(node("html"))).isEmpty())
        assertTrue(HideSelectors.candidates(emptyList()).isEmpty())
        assertFalse(HideSelectors.isPickable(node("BODY")))
        assertTrue(HideSelectors.isPickable(node("iframe")))
    }

    @Test
    fun 조상을_다_받지_못했으면_받은_맨_위에서_너무_깊으면_짓지_않는다() {
        val cut = listOf(node("span"), node("div", nth = 2, of = 2), node("div"))
        assertEquals(listOf("div > div:nth-of-type(2) > span"), HideSelectors.candidates(cut))
        val deep = List(40) { node("div") } + body
        assertTrue(HideSelectors.candidates(deep).isEmpty())
    }

    @Test
    fun 하나만_맞는_첫_후보를_고른다() {
        val c = listOf("div.ad", "#side div.ad", "body > div")
        val check = HideSelectors::Check
        assertEquals("#side div.ad", HideSelectors.choose(c, listOf(check(3, true), check(1, true), check(1, true))))
        assertEquals("body > div", HideSelectors.choose(c, listOf(check(1, false), check(-1, false), check(1, true))))
        assertNull(HideSelectors.choose(c, listOf(check(1, false), check(-1, false), check(0, false))))
        // 결과가 모자라면(스크립트가 다 재지 못했다) 그 뒤는 고르지 않는다.
        assertNull(HideSelectors.choose(c, listOf(check(2, true))))
    }

    @Test
    fun 설명() {
        assertEquals("div#top.ad-slot.wide…", HideSelectors.describe(node("div", id = "top", classes = "ad-slot wide x")))
        assertEquals("iframe", HideSelectors.describe(node("iframe")))
        assertEquals("div#abc", HideSelectors.describe(node("div", id = "a\u202Eb\nc")))
        assertEquals("div." + "a".repeat(23) + "…", HideSelectors.describe(node("div", classes = "a".repeat(30))))
    }

    @Test
    fun 넓은_요소() {
        assertTrue(HideSelectors.isBroad(0.7))
        assertTrue(HideSelectors.isBroad(1.0))
        assertFalse(HideSelectors.isBroad(0.69))
    }
}
