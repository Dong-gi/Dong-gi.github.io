package io.github.donggi.iroiroviewer.docview.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * 문서 안 링크 — 두드린 자리가 어느 링크에 닿는가, 가리키는 쪽이 문서 안에 있는가. 링크 자체(플랫폼의 `getGotoLinks`)는 기기에서만
 * 돌아 JVM 에 없다.
 */
class PdfLinksTest {

    private fun link(target: Int, vararg boxes: PdfSearch.Box) = PdfLinks.Link(boxes.toList(), target)

    @Test
    fun 상자_안을_두드리면_그_링크다() {
        val a = link(4, PdfSearch.Box(10f, 100f, 200f, 112f))
        val b = link(9, PdfSearch.Box(10f, 120f, 200f, 132f))
        assertSame(a, PdfLinks.hit(listOf(a, b), 50f, 105f, slop = 0f))
        assertSame(b, PdfLinks.hit(listOf(a, b), 50f, 125f, slop = 0f))
    }

    @Test
    fun 상자_밖은_여유_안에서_가장_가까운_것이다() {
        val a = link(4, PdfSearch.Box(10f, 100f, 200f, 112f))
        val b = link(9, PdfSearch.Box(10f, 120f, 200f, 132f))
        // 두 줄 사이(112~120)의 117 은 아래 줄에 더 가깝다.
        assertSame(b, PdfLinks.hit(listOf(a, b), 50f, 117f, slop = 6f))
        assertSame(a, PdfLinks.hit(listOf(a, b), 50f, 114f, slop = 6f))
        // 여유 밖이면 아무것도 아니다 — 막대를 여닫는 두드림이다.
        assertNull(PdfLinks.hit(listOf(a, b), 50f, 150f, slop = 6f))
        assertNull(PdfLinks.hit(listOf(a, b), 300f, 105f, slop = 6f))
    }

    @Test
    fun 줄을_넘는_링크는_상자_어느_것이든_닿는다() {
        val a = link(2, PdfSearch.Box(150f, 100f, 200f, 112f), PdfSearch.Box(10f, 114f, 60f, 126f))
        assertSame(a, PdfLinks.hit(listOf(a), 30f, 120f, slope()))
        assertSame(a, PdfLinks.hit(listOf(a), 180f, 105f, slope()))
    }

    @Test
    fun 뒤집힌_상자도_받는다() {
        // 원점이 아래인 좌표에서 온 상자는 위·아래가 뒤바뀐 채 올 수 있다.
        val a = link(1, PdfSearch.Box(200f, 112f, 10f, 100f))
        assertSame(a, PdfLinks.hit(listOf(a), 50f, 105f, slope()))
    }

    @Test
    fun 문서_밖을_가리키는_링크는_따르지_않는다() {
        assertEquals(3, PdfLinks.targetOf(link(3), pageCount = 10))
        assertEquals(0, PdfLinks.targetOf(link(0), pageCount = 10))
        assertNull(PdfLinks.targetOf(link(10), pageCount = 10))
        assertNull(PdfLinks.targetOf(link(-1), pageCount = 10))
    }

    @Test
    fun 자리를_모르면_닿지_않는다() {
        val a = link(1, PdfSearch.Box(0f, 0f, 100f, 100f))
        assertNull(PdfLinks.hit(listOf(a), Float.NaN, 5f, slope()))
        assertNull(PdfLinks.hit(emptyList(), 5f, 5f, slope()))
    }

    @Test
    fun 자리를_모르는_상자는_어디에도_닿지_않는다() {
        // 비교가 모두 거짓인 NaN 상자를 거리 0 으로 읽으면 쪽 어디를 두드려도 그 링크로 간다.
        val broken = link(7, PdfSearch.Box(Float.NaN, 0f, 100f, 100f))
        val fine = link(2, PdfSearch.Box(10f, 10f, 20f, 20f))
        assertNull(PdfLinks.hit(listOf(broken), 500f, 50f, slope()))
        assertNull(PdfLinks.hit(listOf(link(7, PdfSearch.Box(0f, 0f, 100f, Float.NaN))), 50f, 50f, slope()))
        assertSame(fine, PdfLinks.hit(listOf(broken, fine), 15f, 15f, slope()))
    }

    @Test
    fun 플랫폼에_있는_링크는_우리_것으로_겹쳐_넣지_않는다() {
        val p = link(4, PdfSearch.Box(10f, 100f, 200f, 112f))
        // 같은 쪽을 가리키고 상자가 겹친다 — `/Dest` 와 `/A` 를 함께 적은 주석이다.
        val same = link(4, PdfSearch.Box(12f, 101f, 190f, 111f))
        // 같은 쪽이지만 다른 자리, 같은 자리지만 다른 쪽.
        val elsewhere = link(4, PdfSearch.Box(10f, 300f, 200f, 312f))
        val otherPage = link(5, PdfSearch.Box(10f, 100f, 200f, 112f))
        // 가장자리만 닿는 것은 겹친 것이 아니다.
        val touching = link(4, PdfSearch.Box(200f, 100f, 260f, 112f))
        assertEquals(listOf(p, elsewhere, otherPage, touching), PdfLinks.merge(listOf(p), listOf(same, elsewhere, otherPage, touching)))
        assertEquals(listOf(elsewhere), PdfLinks.merge(emptyList(), listOf(elsewhere)))
        assertSame(listOf(p).let { l -> PdfLinks.merge(l, emptyList()) }.first(), p)
        // 자리를 모르는 상자는 겹치지 않는다 — 넣되 `hit` 이 건너뛴다.
        val nan = link(4, PdfSearch.Box(Float.NaN, 100f, 200f, 112f))
        assertEquals(listOf(p, nan), PdfLinks.merge(listOf(p), listOf(nan)))
    }

    @Test
    fun 합친_수는_쪽당_상한에서_끊는다() {
        val many = (0 until PdfLinks.MAX_PER_PAGE).map { link(1, PdfSearch.Box(0f, it.toFloat(), 1f, it + 0.5f)) }
        val extra = link(2, PdfSearch.Box(500f, 500f, 510f, 510f))
        assertEquals(PdfLinks.MAX_PER_PAGE, PdfLinks.merge(many, listOf(extra)).size)
    }

    private fun slope() = 0f
}
