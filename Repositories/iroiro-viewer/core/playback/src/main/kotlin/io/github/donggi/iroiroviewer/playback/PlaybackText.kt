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
    is PlaybackFailure.BadContainer -> stringResource(
        if (failure.unsupported) R.string.playback_error_container_unsupported else R.string.playback_error_container_malformed,
    )
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

/** 배속 시트의 제목. */
@Composable
fun speedTitle(): String = stringResource(R.string.playback_speed)

/** '재생 속도 1.5×'. 눈에 보이는 것은 숫자뿐이라 낭독기에는 이 말이 필요하다. */
@Composable
fun speedLabel(speed: Float): String =
    stringResource(R.string.playback_speed_current, SpeedSteps.text(speed))

/** 트랙 시트의 제목과 머리글들. */
@Composable
fun tracksTitle(): String = stringResource(R.string.playback_tracks)

@Composable
fun audioTracksHeader(): String = stringResource(R.string.playback_track_audio)

@Composable
fun textTracksHeader(): String = stringResource(R.string.playback_track_text)

@Composable
fun subtitleFilesHeader(): String = stringResource(R.string.playback_track_files)

@Composable
fun subtitleOffLabel(): String = stringResource(R.string.playback_track_off)

@Composable
fun noTracksLabel(): String = stringResource(R.string.playback_track_none)

@Composable
fun unsupportedTrackLabel(): String = stringResource(R.string.playback_track_unsupported)

/**
 * 트랙 한 줄의 이름. **조각을 붙이는 일만 한다** — 조각을 고르는 것은 [TrackLabels] 다.
 *
 * 순서는 '무엇인가 → 어떤 소리인가 → 무엇으로 눌렀는가' 다. 이름이 있으면 그것이 먼저고,
 * 없으면 언어 이름이, 그것도 없으면 번호가 그 자리를 채운다 — **빈 줄은 만들지 않는다.**
 *
 * 언어 이름을 우리 표로 만들지 않는 이유는 플랫폼이 이미 안다는 것이다. `Locale` 이
 * `kor` 을 화면 언어에 맞춰 '한국어' 로 옮겨 준다. 옮기지 못하면 코드를 그대로 쓴다.
 */
@Composable
fun trackLabel(descriptor: TrackLabels.Descriptor, isText: Boolean): String {
    val parts = ArrayList<String>(4)
    val head = descriptor.name
        ?: descriptor.languageCode?.let { languageName(it) }
        ?: stringResource(
            if (isText) R.string.playback_track_unnamed_text else R.string.playback_track_unnamed_audio,
            descriptor.ordinal,
        )
    parts += head
    // 이름이 따로 있으면 언어를 뒤에 덧붙인다 — '감독 해설' 만으로는 무슨 말인지 모른다.
    // 다만 **이름이 이미 그 말을 하고 있으면 덧붙이지 않는다**: `한국어 더빙 · 한국어` 는
    // 같은 말을 두 번 하는 것이고, 기기에서 실제로 그렇게 나왔다.
    if (descriptor.name != null && descriptor.languageCode != null) {
        val language = languageName(descriptor.languageCode)
        if (!descriptor.name.contains(language)) parts += language
    }
    descriptor.channelCount?.let { parts += channelsLabel(it) }
    descriptor.codec?.let { parts += it }
    if (descriptor.forced) parts += stringResource(R.string.playback_track_forced)
    return parts.joinToString(" · ")
}

/** '모노'·'스테레오'·'6채널'. 둘까지는 이름이 있고 그 위는 수로 말하는 것이 관행이다. */
@Composable
private fun channelsLabel(count: Int): String = when (count) {
    1 -> stringResource(R.string.playback_channels_mono)
    2 -> stringResource(R.string.playback_channels_stereo)
    else -> stringResource(R.string.playback_channels_many, count)
}

/**
 * `kor` → '한국어'. **우리가 표를 만들지 않는다.**
 *
 * 옮기지 못하는 코드는 `Locale` 이 입력을 그대로 돌려주므로, 그때는 코드가 그대로 보인다 —
 * 아무것도 안 보이는 것보다 낫다.
 *
 * **`Locale.getDefault()` 를 쓰지 않는다.** 그것은 *기기*의 언어이고 이 앱의 화면은
 * 언제나 한국어다(문자열 자원이 `values/` 하나뿐이다). 기기가 영어로 맞춰져 있으면
 * 나머지 줄은 한국어인데 이 한 조각만 `Korean`·`English` 로 나온다 — 에뮬레이터에서
 * 실제로 그렇게 나왔다. **읽는 사람이 보는 언어를 따라야 한다.**
 */
private fun languageName(code: String): String {
    val locale = java.util.Locale.forLanguageTag(code.replace('_', '-'))
    val name = locale.getDisplayLanguage(java.util.Locale.KOREAN)
    return name.ifBlank { code }
}

/** 단추에 찍히는 'A-B'. 그림으로 그리면 무슨 구간인지 읽히지 않는다. */
@Composable
fun abSymbol(): String = stringResource(R.string.playback_ab_symbol)

/** A-B 단추의 이름. 지금 상태를 말한다 — 색만으로는 낭독기가 읽지 못한다. */
@Composable
fun abLabel(span: AbRepeat.Span?): String = stringResource(
    when {
        span == null -> R.string.playback_ab_off
        span.bMs == null -> R.string.playback_ab_waiting
        else -> R.string.playback_ab_on
    }
)

/** A-B 단추를 누른 결과를 사람의 말로. 화면이 잠깐 띄운다. */
@Composable
fun abResultText(result: AbRepeat.Result): String = when (result) {
    is AbRepeat.Result.Marked ->
        if (result.span.bMs == null) {
            stringResource(R.string.playback_ab_marked_a, formatTime(result.span.aMs))
        } else {
            stringResource(
                R.string.playback_ab_marked_b,
                formatTime(result.span.aMs),
                formatTime(result.span.bMs),
            )
        }
    AbRepeat.Result.Cleared -> stringResource(R.string.playback_ab_cleared)
    AbRepeat.Result.TooShort -> stringResource(R.string.playback_ab_too_short)
}

/** 방향 잠금 단추의 이름. */
@Composable
fun orientationLockLabel(locked: Boolean): String =
    stringResource(if (locked) R.string.playback_orientation_unlock else R.string.playback_orientation_lock)

/** 화면 속 화면 단추의 이름. */
@Composable
fun pipLabel(): String = stringResource(R.string.playback_pip)

/** 화면 속 화면에 들어가지 못했을 때. */
@Composable
fun pipFailedText(): String = stringResource(R.string.playback_pip_failed)

/**
 * **컴포저블이 아닌 자리에서 쓰는 문구들.**
 *
 * PiP 창의 조작(`RemoteAction`)은 액티비티 콜백에서 만들어진다 — 컴포지션 밖이라
 * `stringResource` 를 부를 수 없다. 그렇다고 그 자리에 한국어를 적으면 같은 말이 두 벌이
 * 되고, 5단계가 그것으로 조용한 실패를 한 번 겪었다. **자원은 하나로 두고 읽는 길만 둘로 둔다.**
 */
object PlaybackStrings {
    fun play(context: android.content.Context): String = context.getString(R.string.playback_play)
    fun pause(context: android.content.Context): String = context.getString(R.string.playback_pause)
    fun next(context: android.content.Context): String = context.getString(R.string.playback_next)
    fun previous(context: android.content.Context): String = context.getString(R.string.playback_previous)
}
