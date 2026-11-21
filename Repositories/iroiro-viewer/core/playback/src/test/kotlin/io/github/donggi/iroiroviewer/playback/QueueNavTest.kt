package io.github.donggi.iroiroviewer.playback

import androidx.media3.common.Player
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 미니 바의 이전·다음을 흐리게 할지, 자막 싱크를 보일지 — 새 상태를 만드는 `State.advancedTo` 가 정한다(컨트롤러 없이 그대로 부른다).
 */
class QueueNavTest {

    private fun frame(
        queueSize: Int,
        shuffle: Boolean = false,
        hasPrevious: Boolean = false,
        hasNext: Boolean = false,
        canShift: Boolean = false,
        shiftMs: Long = 0,
    ) = PlaybackConnection.Frame(
        key = "A", title = "a.mp3", isPlaying = true, positionMs = 0, durationMs = 1_000,
        videoSelected = false, videoAspect = 0f, queueSize = queueSize, queueIndex = 0, kind = null,
        shuffle = shuffle, repeatMode = Player.REPEAT_MODE_OFF, speed = 1f,
        playerHasPrevious = hasPrevious, playerHasNext = hasNext,
        canShiftSubtitle = canShift, subtitleShiftMs = shiftMs,
    )

    private fun advance(f: PlaybackConnection.Frame) = PlaybackConnection.State().advancedTo(f)

    @Test
    fun `큐의 끝에서는 그쪽이 흐리다`() {
        val first = advance(frame(queueSize = 5, hasPrevious = false, hasNext = true))
        assertFalse(first.canPrevious)
        assertTrue(first.canNext)
        val last = advance(frame(queueSize = 5, hasPrevious = true, hasNext = false))
        assertTrue(last.canPrevious)
        assertFalse(last.canNext)
    }

    @Test
    fun `섞기면 끝에서도 다음이 있다`() {
        val s = advance(frame(queueSize = 5, shuffle = true, hasNext = false))
        assertTrue(s.canNext)
    }

    @Test
    fun `한 곡짜리에는 앞뒤가 없다 — 반복이 플레이어의 답을 참으로 만들어도`() {
        val s = advance(frame(queueSize = 1, shuffle = true, hasPrevious = true, hasNext = true))
        assertFalse(s.canPrevious)
        assertFalse(s.canNext)
    }

    @Test
    fun `옮길 자막이 없으면 싱크 값도 0 이다`() {
        assertEquals(300, advance(frame(queueSize = 1, canShift = true, shiftMs = 300)).subtitleShiftMs)
        val none = advance(frame(queueSize = 1, canShift = false, shiftMs = 300))
        assertFalse(none.canShiftSubtitle)
        assertEquals(0, none.subtitleShiftMs)
    }
}
