package io.github.donggi.iroiroviewer.playback

import androidx.media3.common.Player
import io.github.donggi.iroiroviewer.model.FileKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 재생 실패는 **그 항목의 것**이다. 항목이 바뀌면 커넥션이 앞 실패를 버린다(`State.carriedFailure`, 그것을 부르는
 * `State.advancedTo`) — 그러지 않으면 다른 파일의 실패와 '다른 앱으로 열기' 가 지금 곡 위에 서고, 지금 곡이 같은 값으로
 * 실패해도 흐름이 그것을 합쳐 삼킨다.
 *
 * 커넥션의 `push` 는 컨트롤러를 읽기만 하고 새 상태는 `State.advancedTo` 가 만든다. 아래 시험은 **그 함수를 그대로** 부른다 —
 * 컨트롤러 대신 읽은 값(`Frame`)을 손으로 채울 뿐이다.
 */
class PlaybackFailureCarryTest {

    /** 추출기가 없는 형식(WMA 등)의 실패 — [PlaybackFailure.BadContainer] 의 `unsupported`. */
    private val noExtractor = true

    private fun state(key: String?, failure: PlaybackFailure?) =
        PlaybackConnection.State(connected = true, fileKey = key, failure = failure)

    @Test
    fun `항목이 바뀌면 앞 항목의 실패를 버린다`() {
        val failure = PlaybackFailure.BadContainer(noExtractor)
        assertNull(state("A", failure).carriedFailure("B"))
        // 끝냈다(stop) — 항목이 없으면 누구의 실패도 아니다.
        assertNull(state("A", failure).carriedFailure(null))
        // 항목 없이 난 실패 뒤에 항목이 섰다.
        assertNull(state(null, failure).carriedFailure("A"))
        // 새 상태를 만드는 자리도 같은 답이다.
        assertNull(state("A", failure).advancedTo(frame("B")).failure)
        assertNull(state("A", failure).advancedTo(frame(null)).failure)
    }

    /** 한 곡 반복·자막을 바꿔 큐를 다시 세우는 길은 항목이 그대로다 — 그 곡의 실패·트랙 경고가 남는다. */
    @Test
    fun `같은 항목이면 실패를 들고 간다`() {
        val failure = PlaybackFailure.UnsupportedTrack(listOf("audio/eac3"))
        assertSame(failure, state("A", failure).carriedFailure("A"))
        assertNull(state("A", null).carriedFailure("A"))
        assertSame(failure, state("A", failure).advancedTo(frame("A")).failure)
    }

    /**
     * 커넥션의 모양 그대로(`State` 는 `data class`, 흐름은 `MutableStateFlow`) 되짚는다. WMA 가 둘 든 폴더 — 앞 곡이
     * `BadContainer` 로 실패한 뒤 다음 곡이 **같은 값으로** 실패한다. 앞 실패를 들고 가면 새 상태가 앞 상태와 같아 흐름이
     * 앞의 것을 그대로 들고, 실패의 객체는 앞 곡의 것으로 남는다.
     */
    @Test
    fun `다음 곡이 같은 값으로 실패해도 그 실패가 흐른다`() {
        val c = FakeConnection()
        c.transition("A")
        c.error(PlaybackFailure.BadContainer(noExtractor))

        c.transition("B")
        // 앞 곡의 문구와 단추가 지금 곡 위에 남지 않는다.
        assertNull(c.state.value.failure)

        val second = PlaybackFailure.BadContainer(noExtractor)
        c.error(second)
        assertSame(second, c.state.value.failure)
        assertEquals("B", c.state.value.fileKey)
        // 흐름이 실제로 내보냈다 — (항목, 실패가 있는가). 전환이 비워 둔 자리에 새 실패가 들어왔다.
        assertEquals(
            listOf(null to false, "A" to false, "A" to true, "B" to false, "B" to true),
            c.emitted.map { s -> s.fileKey to (s.failure != null) },
        )
    }

    /**
     * 컨트롤러는 한 묶음을 **항목 전환 → 오류 → 트랙** 차례로 알리고 끝에 `onEvents` 가 온다(media3 1.11.1). 전환이 앞
     * 실패를 버린 뒤에 새 항목의 오류가 실리므로, 그 뒤의 갱신이 같은 항목이라 새 실패를 잃지 않는다.
     */
    @Test
    fun `한 묶음의 전환과 오류에서 새 곡의 실패를 잃지 않는다`() {
        val c = FakeConnection()
        c.transition("A")
        c.error(PlaybackFailure.NoDecoder("audio/ac3"))

        val next = PlaybackFailure.NoDecoder("audio/ac3")
        c.batch(to = "B", failure = next)
        assertSame(next, c.state.value.failure)
        assertEquals("B", c.state.value.fileKey)
    }

    /**
     * 트랙 경고(`UnsupportedTrack`)는 실패가 비어 있을 때만 싣는다. 앞 곡의 실패가 남아 있으면 **지금 곡의 경고가 막힌다**
     * — AC-3 영상 다음의 영상이 무음인데 아무 말이 없다.
     */
    @Test
    fun `앞 곡의 실패가 지금 곡의 트랙 경고를 막지 않는다`() {
        val c = FakeConnection()
        c.transition("A")
        c.error(PlaybackFailure.BadContainer(noExtractor))
        c.transition("B")
        c.tracks(unsupported = listOf("audio/ac3"))
        val warning = c.state.value.failure
        assertTrue(warning is PlaybackFailure.UnsupportedTrack, "$warning")
        assertEquals("B", c.state.value.fileKey)
    }

    /**
     * 실패와 같은 자리에서 가르는 **항목에 매인 다른 것들** — 구간·이어보기 제안·영상 여부. `push` 를 순수 함수로 옮기며
     * 예전 판단이 그대로인지 박는다.
     */
    @Test
    fun `항목이 바뀌면 구간을 풀고 트랙을 모르는 동안은 큐의 종류를 쓴다`() {
        val span = AbRepeat.Span(aMs = 1_000, bMs = 5_000)
        val playing = PlaybackConnection.State(
            connected = true, fileKey = "A", abSpan = span, hasVideo = true, videoAspect = 1.5f, resumeOfferMs = 30_000,
        )
        // 같은 항목 — 구간·제안을 들고, 트랙이 잠깐 비어도(자막을 손으로 바꾸는 길) 영상과 비율을 든다.
        val same = playing.advancedTo(frame("A", positionMs = 2_000, videoSelected = null, kind = FileKind.AUDIO))
        assertEquals(span, same.abSpan)
        assertEquals(30_000L, same.resumeOfferMs)
        assertTrue(same.hasVideo)
        assertEquals(1.5f, same.videoAspect)
        // 제안한 위치를 지났으면 버린다.
        assertNull(playing.advancedTo(frame("A", positionMs = 31_000)).resumeOfferMs)
        // 다른 항목 — 구간을 풀고, 트랙을 모르는 동안은 큐가 정한 종류로 영상 여부를 정한다. 앞 항목의 비율은 쓰지 않는다.
        val other = playing.advancedTo(frame("B", videoSelected = null, kind = FileKind.AUDIO))
        assertNull(other.abSpan)
        assertFalse(other.hasVideo)
        assertEquals(0f, other.videoAspect)
        assertTrue(playing.advancedTo(frame("B", videoSelected = null, kind = FileKind.VIDEO)).hasVideo)
        // 트랙을 알면 그것이 이긴다. 모르는 길이는 0 이고, 배속은 눈금으로 떨어진다.
        val known = playing.advancedTo(frame("B", videoSelected = false, kind = FileKind.VIDEO, durationMs = -1, speed = 1.26f))
        assertFalse(known.hasVideo)
        assertEquals(0L, known.durationMs)
        assertEquals(SpeedSteps.nearest(1.26f), known.speed)
    }

    /** 컨트롤러가 준 값 한 벌. 시험에 상관없는 칸은 평범한 값으로 둔다. */
    private fun frame(
        key: String?,
        positionMs: Long = 0,
        durationMs: Long = 60_000,
        videoSelected: Boolean? = false,
        kind: FileKind? = FileKind.AUDIO,
        speed: Float = 1f,
    ) = PlaybackConnection.Frame(
        key = key,
        title = key.orEmpty(),
        isPlaying = true,
        positionMs = positionMs,
        durationMs = durationMs,
        videoSelected = videoSelected,
        videoAspect = 0f,
        queueSize = 3,
        queueIndex = 0,
        kind = kind,
        shuffle = false,
        repeatMode = Player.REPEAT_MODE_OFF,
        speed = speed,
    )

    /**
     * `PlaybackConnection` 의 알림 처리와 같은 모양. [push] 는 커넥션의 `push` 처럼 **`State.advancedTo` 로** 새 상태를
     * 만든다 — 컨트롤러가 없으므로 읽은 값만 손으로 채운다. 오류·트랙 알림이 실패를 싣는 두 줄은 커넥션의
     * `onPlayerError`·`onTracksChanged` 를 옮겨 적은 것이다(`PlaybackException` 은 JVM 에서 만들 수 없다).
     */
    private inner class FakeConnection {
        val state = MutableStateFlow(PlaybackConnection.State(connected = true))

        /** 흐름이 내보낸 값들. `MutableStateFlow` 는 같은 값을 넣으면 내보내지 않는다. */
        val emitted = ArrayList<PlaybackConnection.State>().apply { add(state.value) }

        private fun set(next: PlaybackConnection.State) {
            val before = state.value
            state.value = next
            if (state.value !== before) emitted += state.value
        }

        fun push(key: String?) = set(state.value.advancedTo(frame(key)))

        /** `onMediaItemTransition`. */
        fun transition(key: String) = push(key)

        /** `onPlayerError` — 싣기 전에 항목을 지금 것으로 맞춘다. */
        fun error(failure: PlaybackFailure, current: String? = state.value.fileKey) {
            push(current)
            set(state.value.copy(failure = failure))
        }

        /** `onTracksChanged` — 실패가 비어 있을 때만 경고를 싣는다. */
        fun tracks(unsupported: List<String>, current: String? = state.value.fileKey) {
            push(current)
            if (unsupported.isNotEmpty() && state.value.failure == null) {
                set(state.value.copy(failure = PlaybackFailure.UnsupportedTrack(unsupported)))
            }
            push(current)
        }

        /** 한 번에 받은 변화 — 전환 → 오류 → 트랙 → `onEvents`. */
        fun batch(to: String, failure: PlaybackFailure) {
            transition(to)
            error(failure, current = to)
            tracks(unsupported = emptyList(), current = to)
            push(to)
        }
    }
}
