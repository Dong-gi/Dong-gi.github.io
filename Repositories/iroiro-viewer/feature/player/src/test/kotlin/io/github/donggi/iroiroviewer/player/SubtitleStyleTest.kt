package io.github.donggi.iroiroviewer.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 자막 크기·높이의 눈금 걸음과 싱크 값의 글(`SubtitleStyle`). */
class SubtitleStyleTest {

    private val sizes = listOf(14, 16, 18, 22, 26, 32)

    @Test
    fun `한 칸씩 옮기고 끝에서 멈춘다`() {
        assertEquals(22, SubtitleStyle.step(sizes, 18, 1))
        assertEquals(16, SubtitleStyle.step(sizes, 18, -1))
        assertEquals(32, SubtitleStyle.step(sizes, 32, 1))
        assertFalse(SubtitleStyle.canStep(sizes, 14, -1))
        assertTrue(SubtitleStyle.canStep(sizes, 14, 1))
    }

    @Test
    fun `눈금 밖의 값은 가장 가까운 칸에서 센다`() {
        assertEquals(22, SubtitleStyle.step(sizes, 19, 1))
        assertEquals(14, SubtitleStyle.step(sizes, 15, -1))
    }

    @Test
    fun `싱크 값은 부호와 0_1초 자리까지`() {
        assertEquals("0.0", SubtitleStyle.syncText(0))
        assertEquals("+0.3", SubtitleStyle.syncText(300))
        assertEquals("−1.2", SubtitleStyle.syncText(-1_200))
        assertEquals("+12.0", SubtitleStyle.syncText(12_000))
    }
}
