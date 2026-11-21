package io.github.donggi.iroiroviewer.playback

import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FrameStepTest {

    /** 정확 탐색이 보일 프레임 — 표시 시각(k × 길이)이 탐색 자리 이상인 첫 것. */
    private fun shownAfterSeek(ms: Long, frameUs: Double): Long = ceil(ms * 1000.0 / frameUs - 1e-9).toLong()

    @Test
    fun `프레임율이 없거나 이상하면 30 fps 로 본다`() {
        assertEquals(FrameStep.DEFAULT_FRAME_US, FrameStep.frameUs(-1f), 1e-9)
        assertEquals(FrameStep.DEFAULT_FRAME_US, FrameStep.frameUs(0.5f), 1e-9)
        assertEquals(FrameStep.DEFAULT_FRAME_US, FrameStep.frameUs(Float.NaN), 1e-9)
        assertEquals(40_000.0, FrameStep.frameUs(25f), 1e-6)
        assertEquals(1_000_000.0 / 240, FrameStep.frameUs(960f), 1e-6)
    }

    @Test
    fun `앞으로 거듭 넘기면 프레임이 하나씩 늘고 뒤로 거듭 넘기면 하나씩 준다`() {
        for (fps in listOf(23.976f, 24f, 25f, 29.97f, 30f, 50f, 59.94f, 60f, 120f, 240f)) {
            val d = FrameStep.frameUs(fps)
            var pos = 0L
            var expected = 0L
            repeat(3_000) {
                pos = FrameStep.target(pos, d, 0, forward = true)!!
                expected++
                assertEquals(expected, shownAfterSeek(pos, d), "$fps fps 앞으로 ${expected}번째")
            }
            repeat(3_000) {
                pos = FrameStep.target(pos, d, 0, forward = false)!!
                expected--
                assertEquals(expected, shownAfterSeek(pos, d), "$fps fps 뒤로 ${expected}번째")
            }
            assertEquals(0L, expected)
            assertNull(FrameStep.target(pos, d, 0, forward = false), "첫 프레임에서 뒤로는 갈 곳이 없다")
        }
    }

    @Test
    fun `한 시간 뒤에서도 어긋나지 않는다`() {
        val d = FrameStep.frameUs(29.97f)
        var pos = 3_600_000L
        val start = shownAfterSeek(FrameStep.target(pos, d, 0, forward = true)!!, d)
        pos = FrameStep.target(pos, d, 0, forward = true)!!
        repeat(100) { pos = FrameStep.target(pos, d, 0, forward = true)!! }
        assertEquals(start + 100, shownAfterSeek(pos, d))
    }

    @Test
    fun `재생하다 멈춘 자리에서 첫 걸음은 이웃 프레임으로 간다`() {
        val d = FrameStep.frameUs(30f)
        // 1초 + 10 ms — 30번째 프레임(1.000 s)이 보이는 중.
        val fwd = FrameStep.target(1_010, d, 0, forward = true)!!
        val back = FrameStep.target(1_010, d, 0, forward = false)!!
        assertEquals(31L, shownAfterSeek(fwd, d))
        assertEquals(29L, shownAfterSeek(back, d))
    }

    @Test
    fun `마지막 프레임에서 앞으로는 헛걸음하지 않는다`() {
        val d = FrameStep.frameUs(30f)
        // 10초짜리 30 fps — 마지막 프레임은 299(9.967 s). 거기서 앞으로는 갈 곳이 없다.
        val last = FrameStep.target(9_930, d, 10_000, forward = true)!!
        assertEquals(299L, shownAfterSeek(last, d))
        assertNull(FrameStep.target(last, d, 10_000, forward = true))
    }

    @Test
    fun `끝을 넘어 앞으로는 가지 않고 둘째 프레임에서 뒤로는 0 초다`() {
        val d = FrameStep.frameUs(30f)
        assertNull(FrameStep.target(9_990, d, 10_000, forward = true))
        assertEquals(0L, FrameStep.target(33, d, 10_000, forward = false))
        assertTrue(FrameStep.target(5_000, d, 10_000, forward = true)!! < 10_000)
    }
}
