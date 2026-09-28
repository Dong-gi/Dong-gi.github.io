package io.github.donggi.iroiroviewer.player

import androidx.media3.common.PlaybackException
import io.github.donggi.iroiroviewer.io.ExternalOpen
import io.github.donggi.iroiroviewer.playback.PlaybackFailure

/**
 * 재생 화면에서 '다른 앱으로 열기' 를 **언제, 무엇에** 다는가. 판단을 순수 함수로 모아 JVM 시험이 박는다
 * (`PlayerOpenWithTest`). 여는 일은 `core:io` 의 `ExternalOpen` 이 하고, 이 화면의 두 자리(실패 문구 곁의 단추·조작부 ⋮
 * 메뉴)는 언제나 고르는 창이다 — 기기에 VLC 가 있는 사람이 그것을 고르게 하는 것이 요점이다.
 *
 * ## 실패 문구 곁의 단추 — 이 기기·이 앱이 못 트는 것
 *
 * | 실패 | 단추 | 까닭 |
 * |---|---|---|
 * | NoDecoder | 단다 | 이 기기에 그 코덱이 없다. 자기 디코더를 가진 앱(VLC)은 튼다 |
 * | DecoderFailed | 단다 | 디코더가 해상도·프로파일을 못 받았다. 소프트웨어로 푸는 앱이 있다 |
 * | BadContainer | 단다 | 추출기가 없는 컨테이너(APE·WMA·RealMedia)이거나 깨진 파일이다 |
 * | UnsupportedTrack | 단다 | 영상은 나오는데 소리(AC-3·DTS)가 없다. **재생은 계속되고 있다** — 넘기면 멈춘다(아래) |
 * | NotReadable | 없다 | 파일이 없거나 읽을 권한이 없다. 넘겨도 열리지 않는다 |
 * | Other | 코드로 가른다 | 아래 [OTHER_CODES_TO_HAND_OFF] |
 *
 * `when` 에 `else` 를 두지 않는다 — 실패 종류가 늘면 여기서 컴파일이 멈춰 판단을 다시 하게 한다(함정 표의 '종류를 옮기면
 * 그것을 검사하던 `when` 을 전부 찾아라').
 *
 * ## ⋮ 메뉴 — 사용자가 고른 것이라 넓다. 파일에 닿지 못한 항목만 뺀다
 *
 * 열어 튼 항목에도, 이 앱이 못 튼 항목에도 선다. 지금 항목이 **파일에 닿지 못해** 실패했으면(없다·권한·입출력 — [isUnreachable])
 * 서지 않는다. 받는 앱도 같은 파일에 닿지 못한다.
 *
 * ## 넘길 때 우리 재생은 멈춘다
 *
 * 다른 앱을 **띄웠으면**([ExternalOpen.Result.STARTED]) 우리 것을 멈춘다([pausesAfter]). 같은 파일을 저쪽에서 보려는
 * 것이라 둘이 함께 소리를 내면 안 되고, 오디오 포커스에 맡기면 포커스를 잡지 않는 앱에서는 우리 소리가 계속된다. **끝내지는
 * 않는다**(`stop` 이 아니다) — 큐와 위치가 남아 미니 바·알림으로 돌아올 길이 그대로다. 띄우지 못했으면(파일이 그새
 * 사라졌다·받는 앱이 거절했다) 아무것도 바꾸지 않는다.
 *
 * **대가 — 고르는 창이 떴다는 것만으로 멈춘다.** 고르는 창은 언제나 뜨고(이 형식을 받을 앱이 보이지 않으면 모든 앱에서
 * 고르는 창으로 넓힌다 — `ExternalOpen` 의 '모든 앱에서 고르기') 우리 쪽에는 언제나 '띄웠다' 로 보이며, 사용자가 창에서
 * 물러난 것도 알 수 없다. 그래서 VLC 가 없는 기기에서 모든 앱의 목록을 보고 물러나도 우리 재생은 멈춘 채다 — 재생 단추 한
 * 번으로 돌아온다. 반대로 멈추지 않으면 고른 앱과 우리가 한동안 함께 소리를 낸다. 앞의 것이 덜 나쁘다고 보았다.
 *
 * **PiP 에서는 서지 않는다.** PiP 창에는 영상 말고 아무것도 두지 않고(공식 문서) 우리 UI 를 누를 수도 없다([menuTarget]).
 */
internal object PlayerOpenWith {

    /**
     * `Other` 로 오는 코드 가운데 넘길 것. **파일을 이 앱이 못 풀었다** 는 뜻인 것만 고른다 — 파싱(3xxx), 디코딩(4xxx),
     * 잠금(DRM, 6xxx), 오디오 출력을 이 형식으로 열지 못한 것, 그리고 추출기·디코더가 뜻밖의 예외로 죽을 때 media3 가 쓰는
     * 두 코드(`UNSPECIFIED`·`FAILED_RUNTIME_CHECK`).
     *
     * 빼는 것: 입출력(2xxx — 파일이 없거나 못 읽는다), 시스템이 디코더를 도로 가져간 것(`RESOURCES_RECLAIMED` — 여기서 다시
     * 틀면 된다), 생방송·원격·세션 명령의 오류(로컬 파일과 무관하다).
     */
    private val OTHER_CODES_TO_HAND_OFF = setOf(
        PlaybackException.ERROR_CODE_UNSPECIFIED,
        PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
        PlaybackException.ERROR_CODE_DRM_UNSPECIFIED,
        PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED,
        PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR,
        PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED,
        PlaybackException.ERROR_CODE_DRM_DISALLOWED_OPERATION,
        PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR,
        PlaybackException.ERROR_CODE_DRM_DEVICE_REVOKED,
        PlaybackException.ERROR_CODE_DRM_LICENSE_EXPIRED,
    )

    /** 이 실패가 '이 앱이 못 튼다' 인가 — 실패 문구 곁에 단추를 다는가. */
    fun offersOnFailure(failure: PlaybackFailure?): Boolean = when (failure) {
        null -> false
        is PlaybackFailure.NoDecoder,
        is PlaybackFailure.DecoderFailed,
        is PlaybackFailure.BadContainer,
        is PlaybackFailure.UnsupportedTrack -> true
        PlaybackFailure.NotReadable -> false
        is PlaybackFailure.Other -> failure.code in OTHER_CODES_TO_HAND_OFF
    }

    /**
     * 실패 문구 곁의 단추가 넘길 항목(`mediaId`) — 지금 항목이다. 실패는 **언제나 지금 항목의 것**이다: 커넥션이 항목이 바뀔
     * 때 앞 실패를 버린다(`PlaybackConnection.State.carriedFailure`). 그래서 같은 상태 한 벌에서 읽은 `failure` 와 `fileKey` 는
     * 어긋나지 않는다(함정 표의 '서로 다른 흐름에서 받은 값을 짝지어 판단하지 마라').
     *
     * 예전에는 커넥션이 큐 안에서 넘길 때 실패를 남겼고, 이 화면이 실패의 주인을 적는 객체와 앞 항목의 실패를 지우는 수집으로
     * 그것을 대신 막았다. 고칠 자리가 커넥션이었다 — 지금은 장치가 하나다.
     *
     * @param currentKey 지금 항목(`State.fileKey`).
     */
    fun failureTarget(failure: PlaybackFailure?, currentKey: String?): String? =
        currentKey?.takeIf { offersOnFailure(failure) }

    /**
     * 실패가 **파일에 닿지 못했다** 는 뜻인가 — 없다·읽을 권한이 없다·입출력이 실패했다. 그런 파일은 다른 앱에 넘겨도 열리지
     * 않으므로 ⋮ 메뉴의 줄도 서지 않는다([menuTarget]). 실패 곁의 단추는 이미 달지 않는다([offersOnFailure]).
     *
     * `Other` 는 **입출력 코드(2xxx)** 일 때만이다. media3 는 오류 코드를 천 단위로 묶는다 — 2xxx 가 입출력, 3xxx 가 파싱,
     * 4xxx 가 디코딩, 6xxx 가 잠금이다(`PlaybackException` 의 상수). 커넥션이 따로 가르지 않은 입출력 코드(`IO_UNSPECIFIED` 따위)가
     * 여기로 온다. 네트워크 코드도 그 묶음이지만 이 앱의 큐는 언제나 로컬 파일이라 오지 않는다.
     */
    fun isUnreachable(failure: PlaybackFailure?): Boolean = when (failure) {
        PlaybackFailure.NotReadable -> true
        is PlaybackFailure.Other -> failure.code in IO_CODES
        null,
        is PlaybackFailure.NoDecoder,
        is PlaybackFailure.DecoderFailed,
        is PlaybackFailure.BadContainer,
        is PlaybackFailure.UnsupportedTrack -> false
    }

    /** media3 의 입출력 오류 코드 묶음(2000–2999). */
    private val IO_CODES = PlaybackException.ERROR_CODE_IO_UNSPECIFIED until PlaybackException.ERROR_CODE_IO_UNSPECIFIED + 1000

    /**
     * 조작부 ⋮ 메뉴가 넘길 항목. 항목이 없거나, PiP 거나, **지금 항목이 파일에 닿지 못해 실패했으면**([isUnreachable]) null 이다
     * — 없는 파일을 넘기는 줄은 누르면 '넘길 수 없다' 는 말만 돌아온다(모든 화면이 같은 규칙이다).
     *
     * @param failure 지금 상태의 실패. 커넥션이 지금 항목의 것만 싣는다([failureTarget]).
     */
    fun menuTarget(currentKey: String?, inPip: Boolean, failure: PlaybackFailure?): String? =
        currentKey?.takeUnless { inPip || isUnreachable(failure) }

    /**
     * 큐 항목의 URI 에서 **파일 경로**를 꺼낸다. 이 앱의 큐는 언제나 `file:` 이지만(`PlaybackConnection.itemOf`), 다른
     * 모양이 오면 넘기지 않는다 — `ExternalOpen` 은 경로를 받고, 경로가 아닌 것을 경로로 읽으면 엉뚱한 파일이 된다.
     */
    fun fileOf(scheme: String?, path: String?): String? = path?.takeIf { scheme == "file" && it.isNotEmpty() }

    /**
     * 넘기지 못한 까닭을 알리는 안내를 띄워 두는 시간. 재생 화면에는 스낵바가 없어(앱의 것은 다른 액티비티에 있다) 방금
     * 누른 것의 답을 싣는 짧은 안내 자리가 대신 말한다. 그 자리의 기본 시간(A-B·PiP 실패, 1.8초)은 몇 글자짜리 답에 맞춘
     * 것이고 이것은 **문장**이라, 다른 화면이 같은 결과를 알리는 스낵바의 짧은 기간(Material 3 `Short`, 4초)과 맞춘다.
     */
    const val HANDOFF_NOTE_HOLD_MS = 4_000L

    /** 다른 앱으로 넘긴 뒤 우리 재생을 멈추는가. 띄웠을 때만이다 — 띄우지 못했으면 아무것도 바꾸지 않는다. */
    fun pausesAfter(result: ExternalOpen.Result): Boolean = result == ExternalOpen.Result.STARTED
}
