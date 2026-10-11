package io.github.donggi.iroirobrowser.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SuggesterTest {

    private val now = 1_800_000_000_000L
    private val day = 24 * 60 * 60 * 1000L

    private fun e(url: String, title: String = "", visits: Int = 1, typed: Int = 0, daysAgo: Int = 0, bookmarked: Boolean = false) =
        Suggester.Entry(url, title, visits, typed, now - daysAgo * day, bookmarked)

    @Test
    fun 주소_꼴() {
        assertEquals("naver.com", Suggester.displayUrl("https://www.naver.com/"))
        assertEquals("naver.com/news", Suggester.displayUrl("http://naver.com/news"))
        assertEquals("example.com:8080/a/", Suggester.displayUrl("https://example.com:8080/a/"))
        assertEquals("ko.wikipedia.org/wiki/서울", Suggester.prettyUrl("https://ko.wikipedia.org/wiki/%EC%84%9C%EC%9A%B8"))
        // 엄격한 UTF-8 로 풀리지 않으면 그대로.
        assertEquals("x.test/%FF", Suggester.prettyUrl("https://x.test/%FF"))
    }

    @Test
    fun 인라인은_호스트까지만_그리고_친_글자는_그대로() {
        val s = Suggester(listOf(
            e("https://www.naver.com/", "NAVER", visits = 5, typed = 2),
            e("https://www.naver.com/news/1", "뉴스", visits = 3),
        ))
        val inline = assertNotNull(s.inline("Na", now))
        assertEquals("Naver.com", inline.text)
        // 첫 쪽이 기록에 있으면 그 주소로 간다(www 그대로).
        assertEquals("https://www.naver.com/", inline.url)
        // `/` 를 치면 경로까지.
        assertEquals("naver.com/news/1", s.inline("naver.com/n", now)?.text)
        assertEquals("https://www.naver.com/news/1", s.inline("naver.com/n", now)?.url)
        // `www.` 로 쳐도 맞는다.
        assertEquals("www.naver.com", s.inline("www.na", now)?.text)
        // 스킴을 쳐도 호스트까지만.
        assertEquals("https://www.naver.com", s.inline("https://www.na", now)?.text)
        // 이미 다 쳤으면 채울 것이 없다. 빈칸이 들면 하지 않는다.
        assertNull(s.inline("naver.com", now))
        assertNull(s.inline("na ver", now))
    }

    @Test
    fun 인라인은_친_적이_있거나_자주_간_곳만() {
        val once = Suggester(listOf(e("https://ads.example/", visits = 1)))
        assertNull(once.inline("ad", now))
        assertNotNull(Suggester(listOf(e("https://ads.example/", visits = 2))).inline("ad", now))
        assertNotNull(Suggester(listOf(e("https://ads.example/", visits = 1, typed = 1))).inline("ad", now))
        assertNotNull(Suggester(listOf(e("https://ads.example/", visits = 0, bookmarked = true))).inline("ad", now))
    }

    @Test
    fun 인라인은_같은_호스트의_쪽들을_모아_고른다() {
        // nate.com 의 쪽 셋(각 2번)이 naver.com 의 쪽 하나(3번)보다 많이 갔다 — 모아서 견준다.
        val s = Suggester(listOf(
            e("https://www.naver.com/x", visits = 3),
            e("https://nate.com/a", visits = 2), e("https://nate.com/b", visits = 2), e("https://nate.com/c", visits = 2),
        ))
        val inline = assertNotNull(s.inline("na", now))
        assertEquals("nate.com", inline.text)
        // 첫 쪽이 기록에 없으면 가장 나은 줄의 스킴·호스트로 지은 첫 쪽.
        assertEquals("https://nate.com/", inline.url)
    }

    @Test
    fun 목록은_앞머리_낱말_가운데_차례() {
        val s = Suggester(listOf(
            e("https://blog.test/kotlin-guide", "코틀린 안내"),
            e("https://kotlinlang.org/", "Kotlin"),
            e("https://x.test/a", "The best kotlin tips"),
            e("https://y.test/b", "notkotlin"),
            e("https://z.test/c", "unrelated"),
        ))
        val rows = s.suggest("kotlin", now).map { it.url }
        assertEquals("https://kotlinlang.org/", rows.first())
        assertTrue(rows.indexOf("https://x.test/a") < rows.indexOf("https://y.test/b"))
        assertTrue("https://z.test/c" !in rows)
    }

    @Test
    fun 낱말마다_어딘가에_있어야_한다() {
        val s = Suggester(listOf(
            e("https://news.test/1", "서울 날씨 오늘"),
            e("https://news.test/2", "서울 교통"),
        ))
        assertEquals(listOf("https://news.test/1"), s.suggest("서울 날씨", now).map { it.url })
        // 한글 한 글자도 가운데에서 맞는다(뜻이 있다). 로마자 한 글자는 낱말의 앞머리만.
        assertEquals(2, s.suggest("울", now).size)
        assertEquals(0, Suggester(listOf(e("https://abc.test/", "xyz"))).suggest("b", now).size)
        assertEquals(1, Suggester(listOf(e("https://abc.test/", "xyz"))).suggest("a", now).size)
    }

    @Test
    fun 자주_최근_간_곳이_위() {
        val s = Suggester(listOf(
            e("https://old.test/", "docs", visits = 50, daysAgo = 200),
            e("https://new.test/", "docs", visits = 3, daysAgo = 1),
        ))
        assertEquals("https://new.test/", s.suggest("docs", now).first().url)
        val typed = Suggester(listOf(
            e("https://a.test/docs", "", visits = 5),
            e("https://b.test/docs", "", visits = 2, typed = 3),
        ))
        assertEquals("https://b.test/docs", typed.suggest("docs", now).first().url)
    }

    @Test
    fun 인라인이_가는_곳이_목록의_첫_줄() {
        val s = Suggester(listOf(
            e("https://github.com/", "GitHub", visits = 10, typed = 3),
            e("https://gist.github.com/x", "Gist github", visits = 30),
        ))
        val result = s.query("gi", now)
        assertEquals("github.com", result.inline?.text)
        assertEquals(result.inline?.url, result.rows.first().url)
        assertEquals(result.rows.map { it.url }.distinct(), result.rows.map { it.url })
        assertTrue(result.rows.size <= Suggester.DEFAULT_LIMIT)
    }

    @Test
    fun 같은_곳은_한_줄() {
        // 조각·끝의 `/`·`www.`·스킴만 다른 주소는 하나로 — 점수가 높은 것이 남는다(앞 판은 fast.com 과 fast.com/# 을 두 줄로 보였다).
        val once = Suggester(listOf(
            e("https://fast.com/", "Fast", daysAgo = 5),
            e("https://fast.com/#", "Fast", daysAgo = 0),
            e("http://www.fast.com", "Fast", daysAgo = 5),
            e("https://fast.com/about", "About", daysAgo = 5),
        ))
        assertNull(once.inline("fast", now))
        assertEquals(listOf("https://fast.com/#", "https://fast.com/about"), once.suggest("fast", now).map { it.url })
        // 인라인 완성이 첫 쪽을 고를 때도 `#` 만 다른 것을 첫 쪽으로 치고, 갈 주소에서는 조각을 뗀다.
        val often = Suggester(listOf(
            e("https://fast.com/", "Fast", visits = 1),
            e("https://fast.com/#", "Fast", visits = 5),
            e("https://fast.com/about", "About", visits = 1),
        ))
        val result = often.query("fast", now)
        assertEquals("https://fast.com/", result.inline?.url)
        assertEquals(listOf("https://fast.com/", "https://fast.com/about"), result.rows.map { it.url })
    }

    @Test
    fun 인라인은_ASCII_만_그리고_물음표와_쌍점은_주소_전체() {
        val s = Suggester(listOf(
            e("https://www.naver.com/", "네이버", visits = 5, typed = 2),
            e("https://naver.com/search?q=x", "검색", visits = 3),
            e("http://localhost:8080/admin", "관리", visits = 3),
        ))
        // 한글이 섞이면 채우지 않는다(조합과 부딪친다) — 목록은 제목으로 맞는다.
        assertNull(s.inline("네이", now))
        assertTrue(s.suggest("네이", now).isNotEmpty())
        assertNull(s.inline("naver한", now))
        // `?` 를 치면 주소 전체로, `:`(포트)도.
        assertEquals("naver.com/search?q=x", s.inline("naver.com/search?", now)?.text)
        assertEquals("localhost:8080/admin", s.inline("localhost:", now)?.text)
    }

    @Test
    fun 최근성은_마디_사이를_곧게() {
        assertEquals(1.0, Suggester.recency(0.0))
        assertEquals(1.0, Suggester.recency(4.0))
        assertEquals(0.85, Suggester.recency(9.0), 1e-9)
        assertEquals(0.7, Suggester.recency(14.0), 1e-9)
        assertEquals(0.1, Suggester.recency(400.0))
        // 지난 날이 늘면 줄기만 한다.
        var prev = 2.0
        for (d in 0..500) {
            val r = Suggester.recency(d.toDouble())
            assertTrue(r <= prev)
            prev = r
        }
    }

    @Test
    fun 빈_글이면_자주_간_곳() {
        val s = Suggester(listOf(
            e("https://a.test/", "A", visits = 1, daysAgo = 30),
            e("https://b.test/", "B", visits = 20, daysAgo = 1),
            e("https://b.test/#x", "B", visits = 1, daysAgo = 1),
            e("https://c.test/", "C", visits = 5, daysAgo = 2),
        ))
        assertEquals(listOf("https://b.test/", "https://c.test/", "https://a.test/"), s.top(now).map { it.url })
        assertEquals(1, s.top(now, limit = 1).size)
    }

    @Test
    fun 빈_글과_빈칸() {
        val s = Suggester(listOf(e("https://a.test/", "a")))
        assertTrue(s.suggest("", now).isEmpty())
        assertTrue(s.suggest("   ", now).isEmpty())
        assertNull(s.inline("", now))
    }

    @Test
    fun 많은_기록도_빨리() {
        val many = (0 until 10_000).map { e("https://site$it.example.com/path/$it", "제목 $it 글", visits = it % 7, typed = it % 3, daysAgo = it % 120) }
        val s = Suggester(many)
        val t0 = System.nanoTime()
        repeat(20) { s.query("site12", now) }
        val perQuery = (System.nanoTime() - t0) / 20 / 1_000_000.0
        assertTrue(perQuery < 50, "한 번에 ${perQuery}ms")
    }
}
