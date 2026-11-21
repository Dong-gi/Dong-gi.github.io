package io.github.donggi.iroiroviewer.io

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 회전·뒤집기 저장이 걸린 파일 — 잘라 저장·그리기를 끄는 근거다. 같은 파일의 요청이 모두 끝나야 빠진다. */
class PathsInFlightTest {

    @Test
    fun 같은_파일의_요청이_모두_끝나야_빠진다() {
        val f = PathsInFlight()
        // 좌우 두 번 — 화면의 합성은 정방향이지만 요청은 둘이다.
        assertEquals(setOf("/a.jpg"), f.add("1", "/a.jpg"))
        assertEquals(setOf("/a.jpg"), f.add("2", "/a.jpg"))
        assertEquals(setOf("/a.jpg"), f.remove("1"))
        assertEquals(emptySet(), f.remove("2"))
    }

    @Test
    fun 두_번_빼도_된다_모르는_신원은_바뀐_것이_없다() {
        val f = PathsInFlight()
        f.add("1", "/a.jpg")
        f.add("2", "/b.jpg")
        assertEquals(setOf("/b.jpg"), f.remove("1"))
        assertNull(f.remove("1"))
        assertNull(f.remove("없음"))
        assertEquals(setOf("/b.jpg"), f.paths())
    }
}
