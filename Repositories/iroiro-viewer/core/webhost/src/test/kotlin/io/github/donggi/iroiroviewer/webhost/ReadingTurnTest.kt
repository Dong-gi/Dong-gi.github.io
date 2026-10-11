package io.github.donggi.iroiroviewer.webhost

import io.github.donggi.iroiroviewer.webhost.ReadingScroll.Axis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 장을 넘기는 몸짓. 손가락을 재는 쪽(`ReaderWebView`)은 계측 없이는 돌릴 수 없으므로 판단은 전부 여기 있다.
 */
class ReadingTurnTest {

    @Test
    fun 끝과_처음은_읽는_차례로_잰다() {
        // 세로: 아래 끝이 끝.
        assertTrue(ReadingTurn.atEnd(Axis.VERTICAL, scroll = 4999, max = 5000, rightToLeft = false))
        assertFalse(ReadingTurn.atEnd(Axis.VERTICAL, scroll = 4000, max = 5000, rightToLeft = false))
        assertTrue(ReadingTurn.atStart(Axis.VERTICAL, scroll = 0, max = 5000, rightToLeft = false))
        // 세로쓰기(오른쪽에서 왼쪽 가로): 장의 처음이 오른쪽 끝(스크롤 최대), 끝이 왼쪽 끝(0).
        assertTrue(ReadingTurn.atEnd(Axis.HORIZONTAL, scroll = 0, max = 9000, rightToLeft = true))
        assertTrue(ReadingTurn.atStart(Axis.HORIZONTAL, scroll = 9000, max = 9000, rightToLeft = true))
        assertFalse(ReadingTurn.atEnd(Axis.HORIZONTAL, scroll = 9000, max = 9000, rightToLeft = true))
        // 왼쪽에서 오른쪽 가로는 그 반대.
        assertTrue(ReadingTurn.atEnd(Axis.HORIZONTAL, scroll = 9000, max = 9000, rightToLeft = false))
        // 한 화면에 드는 장은 처음이자 끝이다 — 양쪽으로 당겨진다.
        assertTrue(ReadingTurn.atEnd(null, 0, 0, rightToLeft = false))
        assertTrue(ReadingTurn.atStart(null, 0, 0, rightToLeft = false))
    }

    @Test
    fun 당김은_읽는_차례의_앞이_양수다() {
        // 세로: 손가락이 위로(dy < 0) — 끝 너머로 당긴다.
        assertEquals(100f, ReadingTurn.pullOf(Axis.VERTICAL, rightToLeft = false, dx = 3f, dy = -100f))
        // 세로쓰기: 손가락이 오른쪽으로 — 더 왼쪽(뒤의 줄)을 보려는 것.
        assertEquals(100f, ReadingTurn.pullOf(Axis.HORIZONTAL, rightToLeft = true, dx = 100f, dy = 0f))
        assertEquals(100f, ReadingTurn.pullOf(Axis.HORIZONTAL, rightToLeft = false, dx = -100f, dy = 0f))
        assertEquals(-50f, ReadingTurn.pullOf(null, rightToLeft = false, dx = 0f, dy = 50f))
    }

    @Test
    fun 끝에서_시작한_끌기만_다음_장으로_당긴다() {
        val t = 200f
        // 끝에서 시작해 문턱을 넘겼다 — 놓으면 다음 장.
        val ready = ReadingTurn.pullProgress(startedAtEnd = true, startedAtStart = false, pull = 250f, threshold = t)
        assertEquals(1f, ready)
        assertEquals(1, ReadingTurn.pullTurn(ready))
        // 반만 당겼다 — 놓아도 그대로.
        val half = ReadingTurn.pullProgress(startedAtEnd = true, startedAtStart = false, pull = 100f, threshold = t)
        assertEquals(0.5f, half)
        assertEquals(0, ReadingTurn.pullTurn(half))
        // **끝까지 스크롤해 온 손가락**(시작할 때는 끝이 아니었다)은 넘기지 않는다 — 읽던 끝 줄을 놓친다.
        assertEquals(0f, ReadingTurn.pullProgress(startedAtEnd = false, startedAtStart = false, pull = 900f, threshold = t))
        // 끝에서 시작했어도 거꾸로(위로 스크롤) 끌면 아무것도 아니다.
        assertEquals(0f, ReadingTurn.pullProgress(startedAtEnd = true, startedAtStart = false, pull = -900f, threshold = t))
    }

    @Test
    fun 처음에서_거꾸로_당기면_앞_장이다() {
        val p = ReadingTurn.pullProgress(startedAtEnd = false, startedAtStart = true, pull = -500f, threshold = 200f)
        assertEquals(-1f, p)
        assertEquals(-1, ReadingTurn.pullTurn(p))
        // 한 화면에 드는 장은 양쪽 다.
        assertEquals(1f, ReadingTurn.pullProgress(startedAtEnd = true, startedAtStart = true, pull = 300f, threshold = 200f))
        assertEquals(-1f, ReadingTurn.pullProgress(startedAtEnd = true, startedAtStart = true, pull = -300f, threshold = 200f))
    }

    @Test
    fun 밀기는_가로로_충분히_곧게_밀어야_한다() {
        val t = 120f
        assertEquals(1, ReadingTurn.swipeTurn(dx = -200f, dy = 20f, threshold = t, rightToLeft = false))
        assertEquals(-1, ReadingTurn.swipeTurn(dx = 200f, dy = 20f, threshold = t, rightToLeft = false))
        // 오른쪽에서 왼쪽으로 넘기는 책은 거꾸로다.
        assertEquals(-1, ReadingTurn.swipeTurn(dx = -200f, dy = 20f, threshold = t, rightToLeft = true))
        assertEquals(1, ReadingTurn.swipeTurn(dx = 200f, dy = 20f, threshold = t, rightToLeft = true))
        // 짧거나 비스듬하면 아니다.
        assertEquals(0, ReadingTurn.swipeTurn(dx = -100f, dy = 0f, threshold = t, rightToLeft = false))
        assertEquals(0, ReadingTurn.swipeTurn(dx = -200f, dy = 150f, threshold = t, rightToLeft = false))
    }

    @Test
    fun 가장자리_두드림은_만화와_같은_폭이다() {
        val w = 1080f
        assertEquals(-1, ReadingTurn.tapTurn(50f, w, rightToLeft = false))
        assertEquals(1, ReadingTurn.tapTurn(w - 50f, w, rightToLeft = false))
        assertEquals(0, ReadingTurn.tapTurn(w / 2f, w, rightToLeft = false))
        assertEquals(1, ReadingTurn.tapTurn(50f, w, rightToLeft = true))
        assertEquals(0, ReadingTurn.tapTurn(50f, 0f, rightToLeft = false))
    }
}
