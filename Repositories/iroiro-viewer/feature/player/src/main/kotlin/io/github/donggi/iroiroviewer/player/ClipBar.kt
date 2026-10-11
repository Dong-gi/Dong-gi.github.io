package io.github.donggi.iroiroviewer.player

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.donggi.iroiroviewer.io.ClipTime
import io.github.donggi.iroiroviewer.io.FileOpManager
import io.github.donggi.iroiroviewer.io.MediaClipper
import io.github.donggi.iroiroviewer.ui.IroProgress
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 재생 화면의 **구간 저장**(사용자 요청 '구간을 지정해 별도 저장')과 **소리만 저장**(사용자 요청 '오디오만 추출하여 별도로
 * 저장').
 *
 * ⋮ 의 '구간 저장' 을 누르면 조작부 안에 한 칸이 펼쳐진다 — 배속 눈금처럼 판 안에서 펼쳐 위의 안내들이 잰 높이를 따라 올라간다.
 * 떠 있는 시트로 두지 않은 까닭은 **진행 막대를 그대로 쓰려는 것**이다: 끌어서 자리를 찾고, 듣다가 '시작을 여기로' 를 누르는 것이
 * 이 일의 전부라 막대가 가려지면 안 된다. 고른 구간은 진행 막대에 띠로 보인다([ClipBand]). 영상이면 그 칸에 '소리만 저장' 이 하나
 * 더 있다(고른 구간의 소리만). ⋮ 의 '소리만 저장' 은 구간을 고르지 않고 소리 트랙 전체를 뽑는다.
 *
 * 처음 구간은 A-B 를 찍어 두었으면 그 구간, 아니면 지금 자리부터 끝까지다. 저장은 파일 작업 큐가 하고(`SaveClip`), 결과는 이
 * 화면이 받는다([ClipJobs] — 앱의 결과 소비자는 다른 액티비티에 있다).
 */
internal data class ClipRange(val key: String, val startMs: Long, val endMs: Long) {
    val lengthMs: Long get() = endMs - startMs

    /** 저장할 수 있는 구간인가 — 엔진이 받는 가장 짧은 길이([MediaClipper.MIN_CLIP_MS])와 같은 값을 쓴다. */
    val valid: Boolean get() = lengthMs >= MediaClipper.MIN_CLIP_MS
}

/**
 * 재생 화면이 시킨 저장의 결과를 받는 자리 — **프로세스에 하나**다. 화면(액티비티)을 쥐지 않는다: 큐에 오래 머문 요청이 끝난 화면을
 * 붙들어 새지 않게, 받는 쪽은 값만 옮겨 두고 화면이 앞에 있을 때 꺼내 간다.
 *
 * - 결과는 **줄**([next])로 받는다 — 둘이 잇달아 끝나도 둘 다 말한다. 처음 판은 값 하나(`StateFlow`)라 뒤의 것이 앞의 것을 덮었다.
 * - 넣은 요청의 신원을 든다([pending]) — 앞의 파일 작업(목록이 시킨 큰 복사)에 밀려 줄 서 있는 동안에도 화면이 '기다리는 중' 을
 *   보이고 저장 단추를 끈다. 처음 판은 큐가 그 작업을 **돌릴 때만** 알아, 줄 선 동안 단추를 다시 눌러 같은 저장이 두 번 들어갔다.
 * - 화면이 앞에 없으면([listening] 이 거짓) 받지 않는다 — 결과는 여느 작업처럼 앱의 결과 소비자에게 가서 파일 목록으로 돌아갔을 때
 *   말해진다(`FileOpManager.ResultSink`). 어느 길로든 한 번이다.
 */
internal object ClipJobs {
    /**
     * 받았지만 아직 말하지 않은 결과. 꺼내 가던 화면이 그 사이에 취소돼 버려지는 것은 앱의 결과 줄로 돌려보낸다
     * (`onUndeliveredElement` — `receive` 가 원소를 꺼낸 뒤 재개되기 전에 화면을 떠나면 그 원소는 그냥 사라진다).
     */
    private val results = Channel<FileOpManager.Finished>(Channel.UNLIMITED, onUndeliveredElement = { FileOpManager.redeliver(it) })

    private val _pending = MutableStateFlow<Set<String>>(emptySet())

    /** 넣었고 아직 결과가 오지 않은 요청들. */
    val pending: StateFlow<Set<String>> = _pending.asStateFlow()

    /** [listening] 을 바꾸는 일과 결과를 받는 일을 한 줄로 세운다 — 받는다고 답한 직후에 화면이 떠나 결과가 갇히지 않게. */
    private val lock = Any()

    /** 재생 화면이 앞에(RESUMED) 있는가. [startListening]·[stopListening] 이 바꾼다. */
    private var listening = false

    val sink = FileOpManager.ResultSink { f ->
        // 어느 길로 가든 기다림은 끝났다.
        _pending.update { it - f.id }
        synchronized(lock) {
            if (listening) {
                results.trySend(f)
                true
            } else {
                false
            }
        }
    }

    /** 요청을 넣기 **전에** 부른다 — 넣은 뒤에 부르면 곧바로 끝난 결과가 먼저 와 신원이 영영 남는다. */
    fun started(id: String) {
        _pending.update { it + id }
    }

    /** 화면이 앞에 섰다 — 이제부터 결과를 받는다. */
    fun startListening() {
        synchronized(lock) { listening = true }
    }

    /**
     * 화면이 앞에서 물러났다 — 받는 것을 멈추고, **받았지만 아직 말하지 못한 결과를 앱의 결과 줄로 돌려보낸다**(파일 목록이 말한다).
     * 그대로 두면 다음에 아무 영상이나 열 때 맥락 없이 떴다 — 실패면 파일 이름 없는 문장('저장 공간이 모자랍니다')이라 지금 영상의 일로
     * 읽혔다.
     */
    fun stopListening() {
        synchronized(lock) {
            listening = false
            while (true) {
                val f = results.tryReceive().getOrNull() ?: break
                FileOpManager.redeliver(f)
            }
        }
    }

    /** 다음 결과. 없으면 기다린다. */
    suspend fun next(): FileOpManager.Finished = results.receive()
}

/** 저장을 넣은 결과 — 넣었으면 그 요청의 신원, 못 넣었으면 그 까닭(문자열 자원). */
internal sealed interface ClipStart {
    data class Started(val id: String) : ClipStart

    data class Refused(@StringRes val why: Int) : ClipStart
}

/** 조작부 안에 펼치는 구간 칸. */
@Composable
internal fun ClipControls(
    range: ClipRange,
    positionMs: () -> Long,
    durationMs: Long,
    hasVideo: Boolean,
    /**
     * 미리 잰 것(`MediaClipper.preview`) — 담을 수 있는가, 영상이면 실제로 시작할 키프레임, 소리만 뽑을 수 있는가. 아직 재는 중이면
     * null(그동안은 일반 안내를 보이고 저장은 켜 둔다 — 못 담는 것은 저장할 때도 말한다).
     */
    preview: MediaClipper.Preview?,
    /** 저장이 돌거나 줄 서 있다 — 저장 단추를 끈다(두 번 누르기). */
    saving: Boolean,
    tint: Color,
    onChange: (ClipRange) -> Unit,
    onPreview: () -> Unit,
    onSave: () -> Unit,
    /** 고른 구간의 소리만 저장한다. 영상이 아니면 null(소리 파일은 '저장' 이 곧 소리다). */
    onSaveAudio: (() -> Unit)?,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(
                R.string.player_clip_range,
                ClipTime.format(range.startMs),
                ClipTime.format(range.endMs),
                ClipTime.format(range.lengthMs.coerceAtLeast(0)),
            ),
            style = MaterialTheme.typography.labelLarge,
            color = tint,
            modifier = Modifier.padding(top = 4.dp),
        )
        // **두 줄로 가른다** — 위는 구간을 고르는 일, 아래는 저장하는 일. 한 줄에 두었더니 세로 폰에서 '소리만 저장' 이 화면 밖으로
        // 밀려 가로로 밀어야 보였다(기기에서 봤다). 줄마다 넘치면 가로로 민다(글꼴 배율이 크면).
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = {
                val at = positionMs().coerceIn(0, durationMs.coerceAtLeast(0))
                // 시작을 끝 너머로 옮기면 끝을 미디어의 끝으로 민다 — 그대로 두면 거꾸로 된 구간이 남아 무엇을 고쳐야 할지 모른다.
                onChange(if (at >= range.endMs) range.copy(startMs = at, endMs = durationMs) else range.copy(startMs = at))
            }) {
                Text(stringResource(R.string.player_clip_set_start), color = tint)
            }
            TextButton(onClick = {
                val at = positionMs().coerceIn(0, durationMs.coerceAtLeast(0))
                onChange(if (at <= range.startMs) range.copy(startMs = 0, endMs = at) else range.copy(endMs = at))
            }) {
                Text(stringResource(R.string.player_clip_set_end), color = tint)
            }
            TextButton(onClick = onPreview) {
                Text(stringResource(R.string.player_clip_preview), color = tint)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val canSave = range.valid && !saving && preview?.supported != false
            TextButton(onClick = onSave, enabled = canSave) {
                Text(
                    text = stringResource(R.string.player_clip_save),
                    fontWeight = FontWeight.Bold,
                    color = if (canSave) MaterialTheme.colorScheme.primary else tint.copy(alpha = 0.38f),
                )
            }
            if (onSaveAudio != null) {
                val canSaveAudio = range.valid && !saving &&
                    (preview?.audio ?: MediaClipper.AudioState.WRITABLE) == MediaClipper.AudioState.WRITABLE
                TextButton(onClick = onSaveAudio, enabled = canSaveAudio) {
                    Text(
                        text = stringResource(R.string.player_save_audio),
                        fontWeight = FontWeight.Bold,
                        color = if (canSaveAudio) MaterialTheme.colorScheme.primary else tint.copy(alpha = 0.38f),
                    )
                }
            }
            TextButton(onClick = onClose) {
                Text(stringResource(R.string.player_clip_close), color = tint)
            }
        }
        // 막는 것(형식·길이)이 먼저, 그다음 영상의 시작이 어디서 담기는가. 영상이면 소리만 뽑을 수 있는지를 덧붙인다.
        val keyframe = preview?.videoStartMs
        val audio = preview?.audio
        val (hint, error) = when {
            preview?.supported == false && hasVideo && audio == MediaClipper.AudioState.WRITABLE ->
                stringResource(R.string.player_clip_video_unsupported_audio_ok) to true
            preview?.supported == false -> stringResource(io.github.donggi.iroiroviewer.io.R.string.io_error_unsupported) to true
            !range.valid -> stringResource(R.string.player_clip_too_short) to true
            keyframe != null -> stringResource(R.string.player_clip_keyframe_at, ClipTime.format(keyframe)) + audioSuffix(hasVideo, audio) to false
            hasVideo -> stringResource(R.string.player_clip_keyframe_hint) + audioSuffix(hasVideo, audio) to false
            else -> null to false
        }
        hint?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = if (error) MaterialTheme.colorScheme.error else tint.copy(alpha = 0.7f),
            )
        }
    }
}

/** 영상의 소리를 뽑을 수 없으면 그 까닭을 안내 끝에 붙인다(단추가 꺼진 까닭이 보이게). */
@Composable
private fun audioSuffix(hasVideo: Boolean, audio: MediaClipper.AudioState?): String = when {
    !hasVideo -> ""
    audio == MediaClipper.AudioState.NONE -> stringResource(R.string.player_clip_audio_none_suffix)
    audio == MediaClipper.AudioState.UNSUPPORTED -> stringResource(R.string.player_clip_audio_unsupported_suffix)
    else -> ""
}

/**
 * 저장의 진행. 이 화면이 넣은 요청([pending])이 있는 동안만 뜬다 — 큐가 그것을 돌리면 진행을, 앞의 작업에 밀려 줄 서 있으면 '기다리는
 * 중' 을 보인다. 넣은 화면이 다시 섰어도(돌리기) 큐의 상태를 읽으므로 그대로 보인다.
 *
 * **큐의 상태를 여기서 읽는다** — 재생 화면 전체가 그것을 들면 목록이 시킨 복사가 도는 동안 진행이 바뀔 때마다 화면 전체가 다시
 * 그려졌다. 취소는 **이 화면이 넣은 요청만** 끊는다([FileOpManager.cancel]) — 큐의 지금 작업을 끊으면 누르는 사이
 * 다음 작업(다른 화면이 시킨 복사)이 끊겼다.
 */
@Composable
internal fun ClipProgress(pending: Set<String>, tint: Color) {
    val op by FileOpManager.state.collectAsStateWithLifecycle()
    val running = (op as? FileOpManager.State.Running)?.takeIf { it.id in pending }
    val p = running?.progress
    val fraction = if (p != null && p.bytesTotal > 0) (p.bytesDone.toFloat() / p.bytesTotal).coerceIn(0f, 1f) else null
    val audio = running?.kind == FileOpManager.Kind.AUDIO
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when {
                    running == null -> stringResource(R.string.player_clip_waiting)
                    fraction != null -> stringResource(
                        if (audio) R.string.player_clip_audio_saving else R.string.player_clip_saving,
                        (fraction * 100).toInt(),
                    )
                    else -> stringResource(if (audio) R.string.player_clip_audio_saving_wait else R.string.player_clip_saving_wait)
                },
                style = MaterialTheme.typography.labelMedium,
                color = tint,
                // 낭독기가 '기다리는 중 → 저장하는 중' 의 바뀜을 읽는다(퍼센트가 바뀔 때마다 읽지 않게 공손하게).
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            )
            TextButton(onClick = { pending.forEach { FileOpManager.cancel(it) } }) {
                Text(stringResource(R.string.player_clip_cancel), color = tint)
            }
        }
        IroProgress(progress = fraction?.let { f -> { f } }, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * 진행 막대 위의 구간 띠 — 시작부터 끝까지 옅게 칠하고 두 끝에 표시를 찍는다(A-B 표시와 같은 자리에 그린다). 막대가 자르지 않으므로
 * 위아래로 조금 넘친다.
 */
@Composable
internal fun BoxScope.ClipBand(full: Dp, startMs: Long, endMs: Long, durationMs: Long) {
    if (durationMs <= 0) return
    val a = (startMs.toFloat() / durationMs).coerceIn(0f, 1f)
    val b = (endMs.toFloat() / durationMs).coerceIn(0f, 1f)
    if (b <= a) return
    val color = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .align(Alignment.CenterStart)
            .offset(x = full * a)
            .width(full * (b - a))
            .height(BAND_HEIGHT)
            .background(color.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
    )
    for (f in floatArrayOf(a, b)) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = full * f - EDGE_WIDTH / 2)
                .width(EDGE_WIDTH)
                .height(EDGE_HEIGHT)
                .background(color, RoundedCornerShape(1.dp))
        )
    }
}

private val BAND_HEIGHT = 8.dp
private val EDGE_WIDTH = 3.dp
private val EDGE_HEIGHT = 14.dp
