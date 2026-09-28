package io.github.donggi.iroiroviewer.player

import androidx.media3.common.PlaybackException
import io.github.donggi.iroiroviewer.io.ExternalOpen
import io.github.donggi.iroiroviewer.playback.PlaybackFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 재생 화면의 '다른 앱으로 열기' 가 **언제, 무엇에** 서는가. 규칙(모든 화면이 같아야 한다): 이 앱이 못 트는 것(코덱·
 * 추출기가 없다, 잠겼다, 깨졌다)에는 단추를 달고, 없는 파일·권한 거부에는 달지 않는다.
 */
class PlayerOpenWithTest {

    @Test
    fun `코덱이 없거나 디코더가 거절하거나 컨테이너를 못 읽으면 단추를 단다`() {
        assertTrue(PlayerOpenWith.offersOnFailure(PlaybackFailure.NoDecoder("audio/ac3")))
        assertTrue(PlayerOpenWith.offersOnFailure(PlaybackFailure.NoDecoder(null)))
        assertTrue(PlayerOpenWith.offersOnFailure(PlaybackFailure.DecoderFailed("video/hevc")))
        // APE·WMA·RealMedia 처럼 추출기가 없는 컨테이너가 이것으로 온다.
        assertTrue(PlayerOpenWith.offersOnFailure(PlaybackFailure.BadContainer(unsupported = true)))
    }

    /** 영상은 나오는데 소리가 없다(AC-3·DTS). 재생은 계속되지만 VLC 는 소리까지 튼다. */
    @Test
    fun `일부 트랙을 못 쓰는 것에도 단추를 단다`() {
        assertTrue(PlayerOpenWith.offersOnFailure(PlaybackFailure.UnsupportedTrack(listOf("audio/eac3"))))
    }

    /** 없는 파일은 넘겨도 열리지 않는다. */
    @Test
    fun `읽지 못한 파일에는 달지 않는다`() {
        assertFalse(PlayerOpenWith.offersOnFailure(PlaybackFailure.NotReadable))
        assertFalse(PlayerOpenWith.offersOnFailure(null))
    }

    /** 커넥션이 따로 가르지 않은 코드 가운데 **파일을 못 풀었다** 는 것만 넘긴다. */
    @Test
    fun `나머지 코드는 파일을 못 풀었다는 것만 넘긴다`() {
        val handOff = listOf(
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DRM_UNSPECIFIED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
            PlaybackException.ERROR_CODE_UNSPECIFIED,
        )
        for (code in handOff) assertTrue(PlayerOpenWith.offersOnFailure(PlaybackFailure.Other(code)), "$code")

        val keep = listOf(
            // 파일이 없거나 못 읽는다.
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
            // 시스템이 디코더를 도로 가져갔다 — 여기서 다시 틀면 된다.
            PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED,
            // 로컬 파일과 무관하다.
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW,
            PlaybackException.ERROR_CODE_REMOTE_ERROR,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
        )
        for (code in keep) assertFalse(PlayerOpenWith.offersOnFailure(PlaybackFailure.Other(code)), "$code")
    }

    /**
     * 실패는 언제나 지금 항목의 것이다(커넥션이 항목이 바뀔 때 앞 실패를 버린다 — `PlaybackFailureCarryTest`). 단추가 넘기는
     * 것은 지금 항목이고, 넘기지 않는 실패에는 서지 않는다.
     */
    @Test
    fun `실패 곁의 단추는 지금 항목을 넘긴다`() {
        val failure = PlaybackFailure.NoDecoder("audio/ac3")
        assertEquals("A", PlayerOpenWith.failureTarget(failure, currentKey = "A"))
        assertNull(PlayerOpenWith.failureTarget(failure, currentKey = null))
        assertNull(PlayerOpenWith.failureTarget(null, currentKey = "A"))
        // 넘기지 않는 실패는 항목이 있어도 서지 않는다.
        assertNull(PlayerOpenWith.failureTarget(PlaybackFailure.NotReadable, currentKey = "A"))
        assertNull(PlayerOpenWith.failureTarget(PlaybackFailure.Other(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND), "A"))
    }

    @Test
    fun `메뉴는 지금 항목을 넘기고 PiP 에서는 서지 않는다`() {
        assertEquals("A", PlayerOpenWith.menuTarget("A", inPip = false, failure = null))
        assertNull(PlayerOpenWith.menuTarget("A", inPip = true, failure = null))
        assertNull(PlayerOpenWith.menuTarget(null, inPip = false, failure = null))
    }

    /** 메뉴는 사용자가 고른 것이라 넓다 — 이 앱이 못 튼 항목(코덱·추출기·잠금)에도 선다. */
    @Test
    fun `이 앱이 못 튼 항목에도 메뉴가 선다`() {
        val failures = listOf(
            PlaybackFailure.NoDecoder("audio/ac3"),
            PlaybackFailure.DecoderFailed("video/hevc"),
            PlaybackFailure.BadContainer(unsupported = true),
            PlaybackFailure.UnsupportedTrack(listOf("audio/eac3")),
            PlaybackFailure.Other(PlaybackException.ERROR_CODE_DRM_UNSPECIFIED),
            // 시스템이 디코더를 도로 가져갔다 — 파일은 멀쩡하다.
            PlaybackFailure.Other(PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED),
            PlaybackFailure.Other(PlaybackException.ERROR_CODE_UNSPECIFIED),
        )
        for (f in failures) assertEquals("A", PlayerOpenWith.menuTarget("A", inPip = false, failure = f), "$f")
    }

    /** 파일에 닿지 못한 항목은 넘겨도 열리지 않는다 — 없는 파일, 권한 거부, 입출력 코드로 온 `Other`. */
    @Test
    fun `파일에 닿지 못한 항목에는 메뉴가 서지 않는다`() {
        val unreachable = listOf(
            PlaybackFailure.NotReadable,
            PlaybackFailure.Other(PlaybackException.ERROR_CODE_IO_UNSPECIFIED),
            PlaybackFailure.Other(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND),
            PlaybackFailure.Other(PlaybackException.ERROR_CODE_IO_NO_PERMISSION),
            PlaybackFailure.Other(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE),
        )
        for (f in unreachable) {
            assertTrue(PlayerOpenWith.isUnreachable(f), "$f")
            assertNull(PlayerOpenWith.menuTarget("A", inPip = false, failure = f), "$f")
        }
        // 입출력 묶음의 경계 — 파싱(3xxx)은 파일에 닿은 뒤의 실패다.
        assertFalse(PlayerOpenWith.isUnreachable(PlaybackFailure.Other(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED)))
        assertFalse(PlayerOpenWith.isUnreachable(PlaybackFailure.Other(PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK)))
        assertFalse(PlayerOpenWith.isUnreachable(null))
    }

    @Test
    fun `파일 URI 만 경로로 읽는다`() {
        assertEquals("/storage/emulated/0/Movies/a.mkv", PlayerOpenWith.fileOf("file", "/storage/emulated/0/Movies/a.mkv"))
        assertNull(PlayerOpenWith.fileOf("content", "/external/video/media/12"))
        assertNull(PlayerOpenWith.fileOf(null, "/storage/emulated/0/a.mkv"))
        assertNull(PlayerOpenWith.fileOf("file", ""))
        assertNull(PlayerOpenWith.fileOf("file", null))
    }

    /** 띄웠을 때만 우리 것을 멈춘다. 띄우지 못했으면 듣던 것을 끊을 까닭이 없다. */
    @Test
    fun `띄웠을 때만 우리 재생을 멈춘다`() {
        assertTrue(PlayerOpenWith.pausesAfter(ExternalOpen.Result.STARTED))
        for (r in listOf(ExternalOpen.Result.NO_APP, ExternalOpen.Result.NOT_SHAREABLE, ExternalOpen.Result.FAILED)) {
            assertFalse(PlayerOpenWith.pausesAfter(r), "$r")
        }
    }
}
