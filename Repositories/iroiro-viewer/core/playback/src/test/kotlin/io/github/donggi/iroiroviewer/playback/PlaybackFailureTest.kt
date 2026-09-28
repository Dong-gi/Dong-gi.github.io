package io.github.donggi.iroiroviewer.playback

import androidx.media3.common.PlaybackException
import kotlin.test.Test
import kotlin.test.assertEquals

/** media3 의 오류 코드를 우리 실패로 옮긴다([PlaybackFailure.ofCode]). */
class PlaybackFailureTest {

    @Test
    fun `형식을 모르는 것과 깨진 것을 가른다`() {
        // WMA·APE 는 추출기가 없다 — '이 앱이 재생하지 못하는 형식' 이고 다른 앱이 열 수 있다.
        assertEquals(
            PlaybackFailure.BadContainer(unsupported = true),
            PlaybackFailure.ofCode(PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED, null),
        )
        for (code in listOf(
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        )) {
            assertEquals(PlaybackFailure.BadContainer(unsupported = false), PlaybackFailure.ofCode(code, null), "$code")
        }
    }

    @Test
    fun `나머지 갈래는 그대로다`() {
        assertEquals(PlaybackFailure.NoDecoder("ac-3"), PlaybackFailure.ofCode(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED, "ac-3"))
        assertEquals(PlaybackFailure.DecoderFailed(null), PlaybackFailure.ofCode(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, null))
        assertEquals(PlaybackFailure.NotReadable, PlaybackFailure.ofCode(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND, null))
        assertEquals(PlaybackFailure.Other(PlaybackException.ERROR_CODE_TIMEOUT), PlaybackFailure.ofCode(PlaybackException.ERROR_CODE_TIMEOUT, null))
    }
}
