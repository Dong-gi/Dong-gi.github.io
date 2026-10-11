package io.github.donggi.iroiroviewer.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 편집기의 찾기와 바꾸기([FindText]). 감싸기, 겹치지 않게 세기, '몇 번째', 바꾸기 전의 확인. */
class FindTextTest {

    private val text = "사과 배 사과 감 사과"

    @Test
    fun `다음은 앞으로 찾고 끝에 닿으면 처음으로 돌아간다`() {
        assertEquals(0, FindText.next(text, "사과", 0, ignoreCase = true))
        assertEquals(5, FindText.next(text, "사과", 2, ignoreCase = true))
        assertEquals(10, FindText.next(text, "사과", 7, ignoreCase = true))
        // 마지막 것 뒤에서 — 처음 것으로 감싼다.
        assertEquals(0, FindText.next(text, "사과", 12, ignoreCase = true))
        assertEquals(-1, FindText.next(text, "포도", 0, ignoreCase = true))
        assertEquals(-1, FindText.next(text, "", 0, ignoreCase = true))
    }

    @Test
    fun `이전은 그 자리 앞에서 찾고 처음에 닿으면 끝으로 돌아간다`() {
        assertEquals(5, FindText.previous(text, "사과", 10, ignoreCase = true))
        assertEquals(0, FindText.previous(text, "사과", 5, ignoreCase = true))
        // 첫 것에서 — 끝의 것으로 감싼다.
        assertEquals(10, FindText.previous(text, "사과", 0, ignoreCase = true))
        assertEquals(-1, FindText.previous("abc", "x", 2, ignoreCase = false))
    }

    @Test
    fun `대소문자 구분`() {
        val code = "Foo foo FOO"
        assertEquals(listOf(0, 4, 8), FindText.all(code, "foo", ignoreCase = true))
        assertEquals(listOf(4), FindText.all(code, "foo", ignoreCase = false))
        assertEquals(4, FindText.next(code, "foo", 1, ignoreCase = false))
    }

    @Test
    fun `겹치지 않게 센다 — 모두 바꾸기가 바꾸는 수와 같다`() {
        assertEquals(listOf(0, 2), FindText.all("aaaa", "aa", ignoreCase = false))
        assertEquals(listOf(0), FindText.all("aaa", "aa", ignoreCase = false))
        // 고른 첫 것 뒤에서 다음을 찾으면 감싸 처음으로 — 겹친 자리(1)를 고르지 않는다.
        assertEquals(0, FindText.next("aaa", "aa", 2, ignoreCase = false))
    }

    @Test
    fun `지금 고른 것이 몇 번째인가`() {
        val starts = FindText.all(text, "사과", ignoreCase = true)
        assertEquals(2, FindText.position(starts, 5, 7, 2))
        assertEquals(3, FindText.position(starts, 10, 12, 2))
        // 찾은 것이 아닌 자리를 골랐다(커서만 있다) — 0.
        assertEquals(0, FindText.position(starts, 3, 3, 2))
        assertEquals(0, FindText.position(starts, 4, 6, 2))
    }

    @Test
    fun `바꾸기는 고른 글이 찾는 말일 때만 바꾼다`() {
        assertTrue(FindText.selects(text, "사과", 5, 7, ignoreCase = true))
        assertFalse(FindText.selects(text, "사과", 4, 6, ignoreCase = true))
        assertFalse(FindText.selects(text, "사과", 5, 5, ignoreCase = true))
        assertTrue(FindText.selects("Foo", "foo", 0, 3, ignoreCase = true))
        assertFalse(FindText.selects("Foo", "foo", 0, 3, ignoreCase = false))
        assertFalse(FindText.selects("Foo", "foo", 0, 9, ignoreCase = true))
    }
}
