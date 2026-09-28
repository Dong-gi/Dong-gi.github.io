package io.github.donggi.iroiroviewer.docview.pdf

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * PDF 찾기의 상태 — 훑는 차례, 결과를 더하는 법, 앞뒤로 옮기기, 강조의 자리. 찾기 자체(플랫폼의 `searchText`)는 API 35
 * 기기에서만 돌아 JVM 에 없다 — 그 대신 결과를 다루는 판단을 전부 여기서 박는다.
 */
class PdfSearchTest {

    private fun m(page: Int, top: Float = 10f) = PdfSearch.Match(page, listOf(PdfSearch.Box(10f, top, 50f, top + 12f)))

    @Test
    fun 보고_있는_쪽부터_끝까지_그다음_처음부터_훑는다() {
        assertEquals(listOf(3, 4, 5, 0, 1, 2), PdfSearch.order(3, 6))
        assertEquals(listOf(0, 1, 2), PdfSearch.order(0, 3))
        assertEquals(listOf(0, 1, 2), PdfSearch.order(-5, 3))
        assertEquals(emptyList(), PdfSearch.order(0, 0))
    }

    @Test
    fun 처음_찾은_결과는_보던_쪽_뒤의_첫_것이다() {
        var s = PdfSearch.State(query = "고양이", total = 6)
        // 3쪽부터 훑는다. 3·4쪽에 없고 5쪽에 있다.
        s = s.add(emptyList(), fromPage = 3).add(emptyList(), 3).add(listOf(m(5)), 3)
        assertEquals(0, s.current)
        assertEquals(5, s.currentMatch!!.page)
        // 처음으로 돌아 1쪽에서 둘을 더 찾았다 — 차례로는 앞이지만 **보던 결과는 그대로** 5쪽이다.
        s = s.add(emptyList(), 3).add(listOf(m(1, 40f), m(1, 20f)), 3).add(emptyList(), 3)
        assertEquals(listOf(1, 1, 5), s.matches.map { it.page })
        // 한 쪽 안에서는 위에서 아래로.
        assertEquals(20f, s.matches[0].boxes[0].top)
        assertEquals(5, s.currentMatch!!.page)
        assertFalse(s.running)
    }

    @Test
    fun 다_훑기_전에는_끝에서_처음으로_돌지_않는다() {
        var s = PdfSearch.State(query = "a", total = 4).add(listOf(m(0), m(0, 30f)), 0)
        assertTrue(s.running)
        s = s.next()
        assertEquals(1, s.current)
        // 뒤에 더 올 수 있으니 제자리.
        assertEquals(1, s.next().current)
        // 다 훑었다 → 돈다.
        s = s.add(emptyList(), 0).add(emptyList(), 0).add(emptyList(), 0)
        assertFalse(s.running)
        assertEquals(0, s.next().current)
        // 처음(0)에서 앞으로 가면 끝(1)으로 돈다.
        assertEquals(1, s.next().previous().current)
    }

    @Test
    fun 결과가_없으면_옮기지_않는다() {
        val s = PdfSearch.State(query = "없는말", total = 1).add(emptyList(), 0)
        assertEquals(-1, s.current)
        assertNull(s.currentMatch)
        assertEquals(s, s.next())
        assertEquals(s, s.previous())
    }

    @Test
    fun 결과가_상한에_닿으면_멈춘다() {
        var s = PdfSearch.State(query = "e", total = 10_000)
        var page = 0
        while (!s.capped) {
            s = s.add(List(PdfSearch.MAX_PER_PAGE) { m(page, it.toFloat()) }, 0)
            page++
        }
        assertEquals(PdfSearch.MAX_MATCHES, s.matches.size)
        // 다 훑은 것으로 친다 — 돌기가 풀리고 '찾는 중' 이 걷힌다.
        assertFalse(s.running)
        assertEquals(s, s.add(listOf(m(9_999)), 0))
    }

    @Test
    fun 찾을_글을_다듬는다() {
        assertEquals("고양이", PdfSearch.normalize("  고양이 "))
        assertNull(PdfSearch.normalize("   "))
        assertEquals(PdfSearch.MAX_QUERY, PdfSearch.normalize("가".repeat(500))!!.length)
    }

    @Test
    fun 강조는_가상_쪽_안의_비율이고_두_쪽이면_옆_쪽만큼_옮긴다() {
        val single = PdfSpreads.layout(listOf(0), listOf(intArrayOf(600, 800)))!!
        assertContentEquals(
            floatArrayOf(0.1f, 0.25f, 0.2f, 0.3f),
            PdfSearch.boxIn(single, 0, PdfSearch.Box(60f, 200f, 120f, 240f))!!,
        )
        val spread = PdfSpreads.layout(listOf(1, 2), listOf(intArrayOf(600, 800), intArrayOf(600, 800)))!!
        // 오른쪽 쪽(2)의 (60,200) 은 가상 쪽의 (660,200).
        assertContentEquals(
            floatArrayOf(660f / 1200f, 0.25f, 720f / 1200f, 0.3f),
            PdfSearch.boxIn(spread, 2, PdfSearch.Box(60f, 200f, 120f, 240f))!!,
        )
        // 쪽 밖으로 삐져나온 것은 그 쪽 안으로 자른다 — 옆 쪽에 번지지 않게.
        val clipped = PdfSearch.boxIn(spread, 1, PdfSearch.Box(580f, 10f, 700f, 20f))!!
        assertEquals(600f / 1200f, clipped[2])
        // 뒤집힌 사각형도 받는다. 이 칸에 없는 쪽·넓이가 없는 것은 null.
        assertTrue(PdfSearch.boxIn(single, 0, PdfSearch.Box(120f, 240f, 60f, 200f)) != null)
        assertNull(PdfSearch.boxIn(single, 5, PdfSearch.Box(60f, 200f, 120f, 240f)))
        assertNull(PdfSearch.boxIn(single, 0, PdfSearch.Box(60f, 200f, 60f, 240f)))
    }
}
