package io.github.donggi.iroiroviewer.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 진행 막대의 값 — **0/0 이 앱을 죽이지 않는가.**
 *
 * 재생 중에 다른 소리 파일을 누르면 미니 바의 진행 람다가 새 항목의 길이 0 을 읽어 0/0 = NaN 을 돌려줬고, `coerceIn` 을 그대로
 * 지난 NaN 을 화면 낭독기용 `ProgressBarRangeInfo` 가 받아 던졌다(릴리스 빌드의 크래시). 막대가 쓰는 값은 언제나
 * [progressFraction] 을 지난다.
 */
class ProgressFractionTest {

    @Test
    fun `0 나누기 0 은 0 이다`() {
        val position = 0L
        val duration = 0L
        assertEquals(0f, progressFraction(position.toFloat() / duration))
    }

    @Test
    fun `NaN 은 0 이다`() {
        assertEquals(0f, progressFraction(Float.NaN))
    }

    @Test
    fun `무한대는 끝으로 민다`() {
        assertEquals(1f, progressFraction(Float.POSITIVE_INFINITY))
        assertEquals(0f, progressFraction(Float.NEGATIVE_INFINITY))
    }

    @Test
    fun `범위 안의 값은 그대로이고 밖의 값은 끝으로 민다`() {
        assertEquals(0.25f, progressFraction(0.25f))
        assertEquals(0f, progressFraction(-0.5f))
        assertEquals(1f, progressFraction(1.5f))
    }
}
