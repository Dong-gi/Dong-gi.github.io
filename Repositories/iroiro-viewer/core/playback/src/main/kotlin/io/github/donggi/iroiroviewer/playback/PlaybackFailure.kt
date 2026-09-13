package io.github.donggi.iroiroviewer.playback

import androidx.media3.common.PlaybackException

/**
 * 재생이 안 될 때 **무엇 때문인지** 말한다.
 *
 * "재생할 수 없습니다" 한 줄로 끝내면 사용자는 파일이 깨진 줄 안다. 실제로는 기기에
 * 그 코덱이 없는 것이고, 그것은 파일 문제가 아니라 기기 문제다. 둘을 구분해 말하는 것이
 * 이 앱이 "코덱은 기기가 주는 만큼" 이라는 정책을 정직하게 지키는 방법이다.
 */
sealed interface PlaybackFailure {

    /** 이 기기에 디코더가 없다. [codecs] 는 알아낸 코덱 이름. */
    data class NoDecoder(val codecs: String?) : PlaybackFailure

    /** 디코더는 있는데 초기화에 실패했다(해상도·프로파일 초과 등). */
    data class DecoderFailed(val codecs: String?) : PlaybackFailure

    /** 컨테이너를 읽지 못했다. 파일이 깨졌거나 우리가 모르는 형식이다. */
    data class BadContainer(val detail: String) : PlaybackFailure

    /** 파일을 열지 못했다. */
    data object NotReadable : PlaybackFailure

    /**
     * 재생은 되지만 일부 트랙을 못 쓴다.
     *
     * 영상은 나오는데 소리가 없는 경우가 대표적이다(AC-3·DTS 가 없는 기기). 이것을
     * 알리지 않으면 사용자는 파일이 무음인 줄 안다.
     */
    data class UnsupportedTrack(val mimeTypes: List<String>) : PlaybackFailure

    data class Other(val code: Int) : PlaybackFailure

    companion object {
        fun of(error: PlaybackException, codecHint: String?): PlaybackFailure = when (error.errorCode) {
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> NoDecoder(codecHint)
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED -> DecoderFailed(codecHint)
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> BadContainer(
                error.errorCodeName
            )
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE -> NotReadable
            else -> Other(error.errorCode)
        }
    }
}
