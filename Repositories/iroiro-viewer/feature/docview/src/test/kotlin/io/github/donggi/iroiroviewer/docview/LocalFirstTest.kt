package io.github.donggi.iroiroviewer.docview

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * '보기' 판의 값과 설정 저장소가 돌려주는 값의 차례. 저장소는 쓰기마다 한 번씩 값을 돌려주는데, 쓰기가 줄을 서 있으면 그 값은
 * 화면이 이미 넘어선 옛 판이다.
 */
class LocalFirstTest {

    @Test
    fun 쓰는_중에_돌아온_옛_값은_받지_않는다() {
        val sync = LocalFirst<Int>()
        // '크게' 두 번: 110 을 쓰고, 끝나기 전에 120 을 쓴다.
        sync.beginWrite()
        sync.beginWrite()
        // 첫 쓰기가 끝나 저장소가 110 을 돌려준다 — 화면은 이미 120 이다.
        sync.endWrite()
        assertNull(sync.stored(110))
        // 둘째 쓰기가 끝나고 120 이 온다.
        sync.endWrite()
        assertEquals(120, sync.stored(120))
    }

    @Test
    fun 쓰기가_없을_때_온_값은_그대로_받는다() {
        // 설정 화면이 바꾼 값, 처음 읽은 값.
        val sync = LocalFirst<String>()
        assertEquals("처음", sync.stored("처음"))
        sync.beginWrite()
        sync.endWrite()
        assertEquals("설정", sync.stored("설정"))
        // 끝을 더 불러도 음수로 가서 다음 값을 막지 않는다.
        sync.endWrite()
        assertEquals("다음", sync.stored("다음"))
    }
}
