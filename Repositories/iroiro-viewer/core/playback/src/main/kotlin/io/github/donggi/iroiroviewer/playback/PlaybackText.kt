package io.github.donggi.iroiroviewer.playback

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * 재생 상태를 **사람의 말로** 옮긴다.
 *
 * ## 왜 이 파일이 생겼는가
 *
 * 5단계는 이 문구를 `core:playback` 의 `strings.xml` 에 넣고, 화면은 `feature:browser`
 * 에 만들면서 **같은 문구 여덟 개를 `browser_playback_*` 이라는 이름으로 한 벌 더**
 * 두었다. 그래서 `core:playback` 쪽 여덟은 한 번도 쓰이지 않았고, 재생 화면
 * ([PlayerActivity])은 디코더가 죽어도 아무 말도 하지 않았다 — 문구가 자기 모듈에
 * 있는데도 그것을 읽는 코드가 다른 모듈에만 있었기 때문이다.
 *
 * **문구는 그것을 만들어 내는 타입 곁에 둔다.** [PlaybackFailure] 가 여기 있으므로
 * 문구도 여기다. 화면이 둘이든 셋이든 읽는 곳은 이 함수들 하나뿐이다.
 */

/** 재생 실패를 사람의 말로. 코덱 이름을 함께 적어 '파일이 깨졌다' 는 오해를 막는다. */
@Composable
fun playbackFailureText(failure: PlaybackFailure): String = when (failure) {
    is PlaybackFailure.NoDecoder ->
        stringResource(R.string.playback_error_no_decoder, failure.codecs?.let { " ($it)" } ?: "")
    is PlaybackFailure.DecoderFailed ->
        stringResource(R.string.playback_error_decoder_failed, failure.codecs?.let { " ($it)" } ?: "")
    is PlaybackFailure.BadContainer ->
        stringResource(R.string.playback_error_container, failure.detail)
    PlaybackFailure.NotReadable ->
        stringResource(R.string.playback_error_not_readable)
    is PlaybackFailure.UnsupportedTrack ->
        stringResource(R.string.playback_error_unsupported_track, failure.mimeTypes.joinToString(", "))
    is PlaybackFailure.Other ->
        stringResource(R.string.playback_error_other, failure.code)
}

/**
 * '3:12 부터 이어서 재생할까요?'
 *
 * **묻는 말이다.** 재생은 이미 0초부터 시작했고 이 줄은 제안일 뿐이다
 * (`PlaybackConnection.State.resumeOfferMs`).
 */
@Composable
fun resumeOfferText(fromMs: Long): String =
    stringResource(R.string.playback_resume_offer, formatTime(fromMs))

/** 제안을 받아들이는 단추의 이름. */
@Composable
fun resumeConfirmLabel(): String = stringResource(R.string.playback_resume_confirm)

/** 재생 단추의 이름. 화면이 어느 모듈에 있든 문구는 여기서 나온다. */
@Composable
fun playLabel(): String = stringResource(R.string.playback_play)

/** 일시정지 단추의 이름. */
@Composable
fun pauseLabel(): String = stringResource(R.string.playback_pause)

/** '다음' 단추의 이름. */
@Composable
fun nextLabel(): String = stringResource(R.string.playback_next)

/** '이전' 단추의 이름. */
@Composable
fun previousLabel(): String = stringResource(R.string.playback_previous)

/** 섞기 단추의 이름. 켜짐/꺼짐을 **문구로도** 말한다 — 색만으로는 낭독기가 읽지 못한다. */
@Composable
fun shuffleLabel(on: Boolean): String =
    stringResource(if (on) R.string.playback_shuffle_on else R.string.playback_shuffle_off)

/** 반복 단추의 이름. 지금 상태를 말한다. */
@Composable
fun repeatLabel(mode: Int): String = stringResource(
    when (mode) {
        androidx.media3.common.Player.REPEAT_MODE_ALL -> R.string.playback_repeat_all
        androidx.media3.common.Player.REPEAT_MODE_ONE -> R.string.playback_repeat_one
        else -> R.string.playback_repeat_off
    }
)

/** '3 / 12'. 큐 안에서 몇 번째인가. */
@Composable
fun queuePosition(index: Int, size: Int): String =
    stringResource(R.string.playback_queue_position, index + 1, size)

/** 재생목록 단추의 이름. 열림/닫힘을 문구로도 말한다. */
@Composable
fun playlistLabel(open: Boolean): String =
    stringResource(if (open) R.string.playback_playlist_hide else R.string.playback_playlist_show)

/** 재생목록이 비었을 때. */
@Composable
fun playlistEmptyLabel(): String = stringResource(R.string.playback_playlist_empty)

/** '밝기 60%'. */
@Composable
fun brightnessLabel(level: Float): String =
    stringResource(R.string.playback_gesture_brightness, (level * 100).toInt())

/** '소리 8 / 15'. */
@Composable
fun volumeLabel(steps: Int, max: Int): String =
    stringResource(R.string.playback_gesture_volume, steps, max)

/**
 * '1:15 (+30초)'. 어디로 가는지와 얼마나 움직이는지를 함께 보여 준다 —
 * 목적지만 있으면 얼마나 건너뛰는지 모르고, 변화량만 있으면 어디로 가는지 모른다.
 */
@Composable
fun seekLabel(targetMs: Long, deltaMs: Long): String {
    val sign = if (deltaMs >= 0) "+" else "−"
    val delta = "$sign${formatTime(kotlin.math.abs(deltaMs))}"
    return stringResource(R.string.playback_gesture_seek, formatTime(targetMs), delta)
}
