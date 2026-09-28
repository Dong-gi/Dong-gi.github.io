package io.github.donggi.iroiroviewer.comic

import io.github.donggi.iroiroviewer.comic.ComicViewModel.Direction
import io.github.donggi.iroiroviewer.comic.ReadingStart.Saved
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 책을 여는 자리 — 설정의 기본 방향(14단계)과 책마다 저장된 값의 차례.
 */
class ReadingStartTest {

    /** **새 책은 설정의 기본 방향으로 연다.** 9단계까지는 언제나 왼쪽에서 오른쪽이었다. */
    @Test
    fun `기록이 없는 책은 설정의 기본 방향으로 연다`() {
        for (d in Direction.entries) {
            val s = ReadingStart.decide(saved = null, pageCount = 10, resumePage = true, defaultDirection = d.code)
            assertEquals(d, s.direction)
            assertEquals(0, s.page)
            assertEquals(false, s.resumed)
        }
    }

    /** 기본값을 바꿨다고 이미 읽던 책이 뒤집히면 안 된다. */
    @Test
    fun `책에 저장된 방향이 설정의 기본값을 이긴다`() {
        val saved = Saved(page = 3, pageCount = 10, direction = Direction.LTR.code)
        val s = ReadingStart.decide(saved, pageCount = 10, resumePage = true, defaultDirection = Direction.RTL.code)
        assertEquals(Direction.LTR, s.direction)
    }

    @Test
    fun `읽던 쪽은 쪽 수가 같을 때만 되살린다`() {
        val saved = Saved(page = 4, pageCount = 10, direction = Direction.RTL.code)
        assertEquals(4, ReadingStart.decide(saved, 10, resumePage = true, defaultDirection = 0).page)
        assertEquals(true, ReadingStart.decide(saved, 10, resumePage = true, defaultDirection = 0).resumed)
        val changed = ReadingStart.decide(saved, 12, resumePage = true, defaultDirection = 0)
        assertEquals(0, changed.page, "쪽 수가 바뀌었으면 옛 번호는 다른 그림을 가리킨다")
        assertEquals(false, changed.resumed)
        assertEquals(Direction.RTL, changed.direction, "쪽을 버려도 방향은 되살린다")
    }

    /** 첫 쪽(0)에서 멈췄던 책은 '이어서 봅니다' 를 띄울 까닭이 없다. */
    @Test
    fun `첫 쪽은 이어보기로 치지 않는다`() {
        val s = ReadingStart.decide(Saved(0, 10, Direction.LTR.code), 10, resumePage = true, defaultDirection = 1)
        assertEquals(false, s.resumed)
        assertEquals(Direction.LTR, s.direction)
    }

    /** 압축 목록에서 그림을 탭해 들어온 길은 쪽을 이미 정했다. 그래도 **방향은** 되살린다(9단계 뒤 감사의 결함). */
    @Test
    fun `엔트리를 지정해 들어와도 방향은 되살리고 쪽은 되살리지 않는다`() {
        val s = ReadingStart.decide(Saved(5, 10, Direction.VERTICAL.code), 10, resumePage = false, defaultDirection = 0)
        assertEquals(Direction.VERTICAL, s.direction)
        assertEquals(false, s.resumed)
        assertEquals(0, s.page)
    }

    /** 옛 판이 적었거나 손으로 고친 값이 와도 화면이 깨지지 않는다. */
    @Test
    fun `모르는 방향 코드는 왼쪽에서 오른쪽이다`() {
        assertEquals(Direction.LTR, ReadingStart.decide(null, 5, true, defaultDirection = 7).direction)
        assertEquals(Direction.LTR, ReadingStart.decide(Saved(1, 5, -1), 5, true, defaultDirection = 1).direction)
    }
}
