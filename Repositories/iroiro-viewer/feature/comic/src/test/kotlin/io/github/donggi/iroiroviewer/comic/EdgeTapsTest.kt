package io.github.donggi.iroiroviewer.comic

import io.github.donggi.iroiroviewer.comic.EdgeTaps.Action
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 만화의 가장자리 두드림. 가장자리 30% 는 넘기고 가운데는 막대 — 오른쪽에서 왼쪽으로 읽으면 좌우가 뒤집히고, 확대돼
 * 있으면 어디든 막대다.
 */
class EdgeTapsTest {

    private val w = 1080f

    @Test
    fun `왼쪽에서 오른쪽 책은 오른쪽 가장자리가 다음 쪽이다`() {
        assertEquals(Action.PREVIOUS, EdgeTaps.of(10f, w, rightToLeft = false, zoomed = false))
        assertEquals(Action.PREVIOUS, EdgeTaps.of(w * 0.29f, w, rightToLeft = false, zoomed = false))
        assertEquals(Action.CHROME, EdgeTaps.of(w * 0.5f, w, rightToLeft = false, zoomed = false))
        assertEquals(Action.NEXT, EdgeTaps.of(w * 0.71f, w, rightToLeft = false, zoomed = false))
        assertEquals(Action.NEXT, EdgeTaps.of(w - 1f, w, rightToLeft = false, zoomed = false))
    }

    @Test
    fun `오른쪽에서 왼쪽 책은 좌우를 뒤집는다 — 왼쪽 가장자리가 다음 쪽`() {
        assertEquals(Action.NEXT, EdgeTaps.of(10f, w, rightToLeft = true, zoomed = false))
        assertEquals(Action.PREVIOUS, EdgeTaps.of(w - 10f, w, rightToLeft = true, zoomed = false))
        assertEquals(Action.CHROME, EdgeTaps.of(w / 2f, w, rightToLeft = true, zoomed = false))
    }

    @Test
    fun `확대돼 있으면 가장자리도 막대다`() {
        assertEquals(Action.CHROME, EdgeTaps.of(10f, w, rightToLeft = false, zoomed = true))
        assertEquals(Action.CHROME, EdgeTaps.of(w - 10f, w, rightToLeft = true, zoomed = true))
    }

    @Test
    fun `잴 수 없으면 막대다`() {
        assertEquals(Action.CHROME, EdgeTaps.of(10f, 0f, rightToLeft = false, zoomed = false))
        assertEquals(Action.CHROME, EdgeTaps.of(Float.NaN, w, rightToLeft = false, zoomed = false))
        assertEquals(Action.CHROME, EdgeTaps.of(10f, Float.POSITIVE_INFINITY, rightToLeft = false, zoomed = false))
    }

    @Test
    fun `끝에서는 더 넘길 칸이 없다`() {
        assertEquals(1, EdgeTaps.target(current = 0, count = 5, forward = true))
        assertEquals(3, EdgeTaps.target(current = 4, count = 5, forward = false))
        assertNull(EdgeTaps.target(current = 0, count = 5, forward = false), "첫 칸에서 뒤로")
        assertNull(EdgeTaps.target(current = 4, count = 5, forward = true), "마지막 칸에서 앞으로")
        assertNull(EdgeTaps.target(current = 0, count = 0, forward = true))
    }
}
