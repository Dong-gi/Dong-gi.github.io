package io.github.donggi.iroirobrowser.browser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClosedTabsTest {

    private fun entry(url: String, index: Int, batch: Long, selected: Boolean = false) =
        ClosedTabs.Entry(url, "t-$url", null, index, null, desktop = false, batch = batch, selected = selected)

    @Test
    fun 새것이_앞이고_넘치면_오래된_것부터_버린다() {
        val closed = ClosedTabs(limit = 3)
        for (i in 1..5) closed.push(entry("https://e$i.example/", 0, closed.newBatch()))
        assertEquals(listOf("https://e5.example/", "https://e4.example/", "https://e3.example/"), closed.all.map { it.url })
    }

    @Test
    fun 빈_탭은_남기지_않는다() {
        val closed = ClosedTabs()
        closed.push(entry("", 0, closed.newBatch()))
        assertTrue(closed.isEmpty)
    }

    @Test
    fun 모두_닫기는_한_묶음으로_자리_차례대로_되살린다() {
        val closed = ClosedTabs()
        val single = closed.newBatch()
        closed.push(entry("https://alone.example/", 2, single))
        val all = closed.newBatch()
        // 닫는 차례는 목록의 차례(자리는 하나씩 당겨진다) — 되살릴 때는 자리 차례로 넣어야 제자리에 선다.
        closed.push(entry("https://a.example/", 0, all))
        closed.push(entry("https://c.example/", 2, all, selected = true))
        closed.push(entry("https://b.example/", 1, all))
        assertEquals(3, closed.countOf(all))
        val back = closed.takeBatch(all)
        assertEquals(listOf(0, 1, 2), back.map { it.index })
        assertTrue(back.single { it.selected }.url == "https://c.example/")
        // 그 묶음만 빠지고 다른 것은 남는다.
        assertEquals(listOf("https://alone.example/"), closed.all.map { it.url })
        assertEquals(0, closed.countOf(all))
        assertTrue(closed.takeBatch(all).isEmpty())
    }

    @Test
    fun 목록에서_하나를_꺼낸다() {
        val closed = ClosedTabs()
        val e = entry("https://one.example/", 0, closed.newBatch())
        closed.push(e)
        assertTrue(closed.take(e))
        assertFalse(closed.take(e))
        assertTrue(closed.isEmpty)
        assertEquals("t-https://one.example/", e.label)
        assertEquals("https://x.example/", ClosedTabs.Entry("https://x.example/", "", null, 0, null, false, 1).label)
    }
}
