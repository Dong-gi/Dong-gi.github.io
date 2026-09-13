package io.github.donggi.iroiroviewer.player

import android.app.Activity
import android.media.AudioManager
import android.view.WindowManager
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import io.github.donggi.iroiroviewer.playback.brightnessLabel
import io.github.donggi.iroiroviewer.playback.seekLabel
import io.github.donggi.iroiroviewer.playback.volumeLabel
import io.github.donggi.iroiroviewer.io.Iro
import io.github.donggi.iroiroviewer.playback.GestureMath
import io.github.donggi.iroiroviewer.playback.PlaybackConnection
import kotlin.math.abs

private const val TAG = "Gesture"

/** 제스처가 지금 무엇을 하고 있는지 화면에 잠깐 보여 줄 값. */
sealed interface GestureFeedback {
    data class Seek(val targetMs: Long, val deltaMs: Long) : GestureFeedback
    data class Brightness(val level: Float) : GestureFeedback
    data class Volume(val level: Float, val steps: Int, val max: Int) : GestureFeedback
}

/**
 * VLC 에서 차용한 재생 제스처.
 *
 * | 동작 | 하는 일 |
 * |---|---|
 * | 왼쪽 세로 끌기 | 화면 밝기 |
 * | 오른쪽 세로 끌기 | 소리 |
 * | 좌우 끌기(어디서든) | 재생 위치 |
 * | 한 번 두드리기 | 조작부 여닫기 |
 * | 오른쪽 두 번 이상 | 두 번째부터 +30초씩 |
 * | 왼쪽 두 번 이상 | 두 번째부터 −30초씩 |
 *
 * ## 중재를 형제 순서에 기대지 않는다
 *
 * CLAUDE.md 의 함정 — *형제 `pointerInput` 의 순서에 기대지 말고 계약을 세워라*. 그래서
 * **인식기가 하나뿐이다.** `awaitEachGesture` 안에서 손가락 하나를 처음부터 끝까지 따라가며
 * 두드림인지 끌기인지, 끌기면 어느 축인지를 **한 번 정하고 그 제스처 동안 바꾸지 않는다.**
 * 비스듬히 끌 때 탐색과 소리가 번갈아 바뀌는 일이 이 규칙 하나로 사라진다.
 *
 * ## 판정은 화면 캡처로 하지 않는다
 *
 * 6단계가 제스처를 캡처로 판정하려다 세 번 틀렸다. 여기서는 제스처가 정한 값을 [Iro.d] 로
 * 찍고 `logcat` 으로 읽는다. 계산 자체는 `GestureMath` 로 빼서 JVM 시험이 답한다.
 */
@Composable
fun Modifier.playerGestures(
    enabled: Boolean,
    durationMs: Long,
    positionMs: () -> Long,
    onToggleControls: () -> Unit,
    onFeedback: (GestureFeedback?) -> Unit,
): Modifier {
    val context = LocalContext.current
    val activity = context as? Activity
    val audio = remember(context) { context.getSystemService(AudioManager::class.java) }
    val touchSlop = LocalViewConfiguration.current.touchSlop
    // **미뤄 둔 일을 돌릴 스코프.** `awaitEachGesture` 안에서는 코루틴을 띄울 수 없고
    // `PointerInputScope` 도 코루틴 스코프가 아니다. 컴포지션에 묶인 스코프를 쓴다 —
    // 화면이 사라지면 미뤄 둔 토글도 함께 취소된다.
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    if (!enabled) return this

    return this.pointerInput(durationMs, enabled) {
        var lastTapAt = 0L
        var tapCount = 0
        // **첫 두드림의 토글은 미뤄 둔다.** 두 번째가 오면 취소한다 — 그러지 않으면
        // 30초를 건너뛸 때마다 조작부가 한 번 깜빡인다.
        var pendingToggle: kotlinx.coroutines.Job? = null
        // 두드림 안내를 지우는 일. 이어 두드리면 다시 시작한다.
        var clearFeedback: kotlinx.coroutines.Job? = null

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val startX = down.position.x
            val startY = down.position.y
            val w = size.width.toFloat()
            val h = size.height.toFloat()
            val side = GestureMath.sideOf(startX, w)

            var dx = 0f
            var dy = 0f
            var mode = Mode.UNDECIDED
            // 끌기를 시작할 때의 기준값. **끌기 시작 순간에 한 번만 읽는다** — 매 프레임
            // 다시 읽으면 우리가 방금 바꾼 값이 기준이 되어 가속이 걸린다.
            var seedPosition = 0L
            var seedLevel = 0f
            var maxVolume = 0

            while (true) {
                val event = awaitPointerEvent()
                val change: PointerInputChange = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break

                val move = change.positionChange()
                dx += move.x
                dy += move.y

                if (mode == Mode.UNDECIDED && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                    mode = if (GestureMath.isHorizontal(dx, dy)) {
                        seedPosition = positionMs()
                        Mode.SEEK
                    } else if (side == GestureMath.Side.LEFT) {
                        seedLevel = currentBrightness(activity)
                        Mode.BRIGHTNESS
                    } else {
                        maxVolume = audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 0
                        seedLevel = currentVolume(audio, maxVolume)
                        Mode.VOLUME
                    }
                    pendingToggle?.cancel()
                    clearFeedback?.cancel()
                    Iro.d(TAG) { "제스처 시작 ${mode} side=$side w=$w h=$h" }
                }

                when (mode) {
                    Mode.UNDECIDED -> Unit
                    Mode.SEEK -> {
                        val delta = GestureMath.seekDeltaMs(dx, w)
                        val target = GestureMath.clampPosition(seedPosition + delta, durationMs)
                        onFeedback(GestureFeedback.Seek(target, target - seedPosition))
                        change.consume()
                    }
                    Mode.BRIGHTNESS -> {
                        val level = (seedLevel + GestureMath.levelDelta(dy, h)).coerceIn(0f, 1f)
                        applyBrightness(activity, level)
                        onFeedback(GestureFeedback.Brightness(level))
                        change.consume()
                    }
                    Mode.VOLUME -> {
                        val level = (seedLevel + GestureMath.levelDelta(dy, h)).coerceIn(0f, 1f)
                        val steps = applyVolume(audio, level, maxVolume)
                        onFeedback(GestureFeedback.Volume(level, steps, maxVolume))
                        change.consume()
                    }
                }
            }

            when (mode) {
                Mode.SEEK -> {
                    val delta = GestureMath.seekDeltaMs(dx, w)
                    val target = GestureMath.clampPosition(seedPosition + delta, durationMs)
                    Iro.d(TAG) { "탐색 끌기 ${seedPosition}ms → ${target}ms" }
                    PlaybackConnection.seekTo(target)
                    onFeedback(null)
                }
                Mode.BRIGHTNESS, Mode.VOLUME -> onFeedback(null)
                Mode.UNDECIDED -> {
                    // 움직이지 않았으면 두드림이다.
                    val now = System.currentTimeMillis()
                    tapCount = GestureMath.tapCount(lastTapAt, now, tapCount)
                    lastTapAt = now
                    // 이어 두드리는 중이면 미뤄 둔 토글을 무른다.
                    pendingToggle?.cancel()
                    clearFeedback?.cancel()
                    val seek = GestureMath.tapSeekMs(tapCount, side)
                    if (seek == 0L) {
                        // **바로 토글하지 않는다.** 두 번째 두드림이 올지 아직 모른다.
                        pendingToggle = scope.launch {
                            kotlinx.coroutines.delay(GestureMath.MULTI_TAP_WINDOW_MS)
                            Iro.d(TAG) { "두드림 1회 — 조작부 토글" }
                            onToggleControls()
                        }
                    } else {
                        val target = GestureMath.clampPosition(positionMs() + seek, durationMs)
                        Iro.d(TAG) { "두드림 ${tapCount}회 side=$side ${seek}ms → ${target}ms" }
                        PlaybackConnection.seekTo(target)
                        onFeedback(GestureFeedback.Seek(target, seek))
                        clearFeedback = scope.launch {
                            kotlinx.coroutines.delay(GestureMath.TAP_FEEDBACK_HOLD_MS)
                            onFeedback(null)
                        }
                    }
                }
            }
        }
    }
}

private enum class Mode { UNDECIDED, SEEK, BRIGHTNESS, VOLUME }

/**
 * 지금 창의 밝기.
 *
 * `screenBrightness` 의 기본값은 **−1(시스템을 따름)** 이라 그대로 쓰면 첫 끌기에서
 * 화면이 캄캄해진다. 그때는 절반에서 시작한다 — 시스템 설정을 읽어 오려면 권한 없이는
 * 정확하지 않고, 어차피 한 번 끌면 사용자가 원하는 값에 닿는다.
 */
private fun currentBrightness(activity: Activity?): Float {
    val v = activity?.window?.attributes?.screenBrightness ?: -1f
    return if (v < 0f) 0.5f else v.coerceIn(0f, 1f)
}

/**
 * **창의 밝기만 바꾼다. 시스템 설정은 건드리지 않는다.**
 *
 * `Settings.System` 에 쓰면 이 앱을 나가도 기기 밝기가 바뀐 채로 남고, 그러려면
 * `WRITE_SETTINGS` 권한까지 필요하다. 창 속성은 이 화면이 사라지면 저절로 돌아간다.
 */
private fun applyBrightness(activity: Activity?, level: Float) {
    val window = activity?.window ?: return
    window.attributes = window.attributes.apply { screenBrightness = level }
}

private fun currentVolume(audio: AudioManager?, max: Int): Float {
    if (audio == null || max <= 0) return 0f
    return audio.getStreamMusicVolume() / max.toFloat()
}

/**
 * **시스템 음량을 바꾼다.** 플레이어 자체의 볼륨이 아니다.
 *
 * 사용자가 재생 화면에서 소리를 줄이면 그것은 '기기를 조용히 해라' 는 뜻이고, 볼륨 키로
 * 하는 것과 같은 자리를 만져야 한다. 플레이어 볼륨을 줄이면 볼륨 키를 눌러도 커지지 않는
 * 이상한 상태가 된다.
 */
private fun applyVolume(audio: AudioManager?, level: Float, max: Int): Int {
    if (audio == null || max <= 0) return 0
    val steps = (level * max).toInt().coerceIn(0, max)
    audio.setStreamVolume(AudioManager.STREAM_MUSIC, steps, 0)
    return steps
}

private fun AudioManager.getStreamMusicVolume(): Int = getStreamVolume(AudioManager.STREAM_MUSIC)

/**
 * 제스처가 지금 무엇을 하는지 화면 가운데에 잠깐 보여 준다.
 *
 * **없으면 제스처가 통하는지 알 수 없다.** 소리를 줄이는 것은 들으면 알지만 밝기는
 * 조금씩 바뀌어 눈치채기 어렵고, 탐색은 손을 떼기 전에는 어디로 가는지 알 수 없다.
 */
@Composable
fun GestureNote(feedback: GestureFeedback, modifier: Modifier = Modifier) {
    val text = when (feedback) {
        is GestureFeedback.Seek -> seekLabel(feedback.targetMs, feedback.deltaMs)
        is GestureFeedback.Brightness -> brightnessLabel(feedback.level)
        is GestureFeedback.Volume -> volumeLabel(feedback.steps, feedback.max)
    }
    androidx.compose.material3.Text(
        text = text,
        color = androidx.compose.ui.graphics.Color.White,
        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
        modifier = modifier
            .background(
                androidx.compose.ui.graphics.Color(0xCC000000),
                androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
    )
}
