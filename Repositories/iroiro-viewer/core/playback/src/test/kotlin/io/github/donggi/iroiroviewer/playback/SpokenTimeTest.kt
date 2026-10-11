package io.github.donggi.iroiroviewer.playback

import io.github.donggi.iroiroviewer.playback.SpokenTime.Kind.HOURS
import io.github.donggi.iroiroviewer.playback.SpokenTime.Kind.MINUTES
import io.github.donggi.iroiroviewer.playback.SpokenTime.Kind.SECONDS
import io.github.donggi.iroiroviewer.playback.SpokenTime.Part
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 재생 막대를 화면 낭독기가 읽는 말의 조각. '1분 23초 / 45분' 꼴 — 백분율도 `1:23` 도 아니다.
 */
class SpokenTimeTest {

    @Test
    fun `분과 초로 나눈다`() {
        assertEquals(listOf(Part(MINUTES, 1), Part(SECONDS, 23)), SpokenTime.partsOf(83_000))
        assertEquals(listOf(Part(SECONDS, 5)), SpokenTime.partsOf(5_000))
    }

    /** '45분 0초' 가 아니라 '45분'. 시간도 같다 — 0 인 조각은 말하지 않는다. */
    @Test
    fun `0 인 조각은 뺀다`() {
        assertEquals(listOf(Part(MINUTES, 45)), SpokenTime.partsOf(45 * 60_000L))
        assertEquals(listOf(Part(HOURS, 1)), SpokenTime.partsOf(3_600_000))
        assertEquals(listOf(Part(HOURS, 1), Part(SECONDS, 5)), SpokenTime.partsOf(3_605_000))
        assertEquals(listOf(Part(HOURS, 1), Part(MINUTES, 2), Part(SECONDS, 3)), SpokenTime.partsOf(3_723_000))
    }

    /** 빈 말을 만들지 않는다 — 처음 자리는 '0초' 다. 음수(모를 때의 값)도 같다. 초 아래는 버린다(화면의 `0:00` 과 같다). */
    @Test
    fun `모두 0 이면 0초 한 조각이다`() {
        assertEquals(listOf(Part(SECONDS, 0)), SpokenTime.partsOf(0))
        assertEquals(listOf(Part(SECONDS, 0)), SpokenTime.partsOf(999))
        assertEquals(listOf(Part(SECONDS, 0)), SpokenTime.partsOf(-5_000))
        assertEquals(listOf(Part(MINUTES, 1)), SpokenTime.partsOf(60_999))
    }

    /** 62분짜리 `.ts`(사용자의 실물 — CLAUDE.md '`.ts` 되감기') 같은 긴 것도 시간으로 올린다. */
    @Test
    fun `한 시간을 넘으면 시간으로 올린다`() {
        assertEquals(listOf(Part(HOURS, 1), Part(MINUTES, 2), Part(SECONDS, 19)), SpokenTime.partsOf((62 * 60 + 19) * 1000L))
        assertEquals(listOf(Part(HOURS, 25)), SpokenTime.partsOf(25 * 3_600_000L))
    }
}
