package io.github.donggi.iroirobrowser.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RetentionTest {

    private val now = 1_800_000_000_000L
    private val day = Retention.DAY_MS

    @Test
    fun 방문_기록은_정한_날보다_오래된_것만() {
        assertNull(Retention.historyCutoff(0, now))
        assertEquals(now - 30 * day, Retention.historyCutoff(30, now))
        assertEquals(now - 365 * day, Retention.historyCutoff(365, now))
        // 고르는 값이 아니면(손으로 고친 설정) 지우지 않는다.
        assertNull(Retention.historyCutoff(2, now))
        assertNull(Retention.historyCutoff(-1, now))
    }

    @Test
    fun 캐시는_비운_지_정한_날이_지나면() {
        assertFalse(Retention.cacheDue(0, now - 400 * day, now))
        // 켜기 전에는 비운 때가 없다 — 켜는 순간부터 센다.
        assertFalse(Retention.cacheDue(7, 0, now))
        assertFalse(Retention.cacheDue(7, now - 7 * day + 1, now))
        assertTrue(Retention.cacheDue(7, now - 7 * day, now))
        assertTrue(Retention.cacheDue(1, now - day, now))
        // 시계가 거꾸로 갔다 — 비우고 다시 센다.
        assertTrue(Retention.cacheDue(30, now + day, now))
        // 모르는 값은 하지 않는다.
        assertFalse(Retention.cacheDue(3, now - 400 * day, now))
    }

    @Test
    fun 고르는_값의_차례() {
        assertEquals(0, Retention.HISTORY_DAYS.first())
        assertEquals(0, Retention.CACHE_DAYS.first())
        assertEquals(Retention.HISTORY_DAYS.drop(1), Retention.HISTORY_DAYS.drop(1).sortedDescending())
        assertEquals(Retention.CACHE_DAYS.drop(1), Retention.CACHE_DAYS.drop(1).sortedDescending())
    }
}
