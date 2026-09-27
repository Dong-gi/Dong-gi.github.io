package io.github.donggi.iroiroviewer.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.donggi.iroiroviewer.playback.PlaybackConnection
import io.github.donggi.iroiroviewer.playback.audioTracksHeader
import io.github.donggi.iroiroviewer.playback.noTracksLabel
import io.github.donggi.iroiroviewer.playback.subtitleFilesHeader
import io.github.donggi.iroiroviewer.playback.subtitleOffLabel
import io.github.donggi.iroiroviewer.playback.textTracksHeader
import io.github.donggi.iroiroviewer.playback.trackLabel
import io.github.donggi.iroiroviewer.playback.tracksTitle
import io.github.donggi.iroiroviewer.playback.unsupportedTrackLabel

/**
 * 소리·자막 트랙을 고르는 시트.
 *
 * ## 왜 바텀시트인가
 *
 * 조작부 안에 붙이는 길을 먼저 생각했는데, 그쪽은 **가로 영상에서 잘린다** — 판 위에 남는
 * 높이가 절반뿐이라 줄이 넷만 넘어가도 아래가 보이지 않고 스크롤할 자리도 없다. 시트는
 * 자기 높이를 스스로 정하고 스크롤을 갖는다.
 *
 * ## 세 갈래를 한 시트에 담는다
 *
 * 소리 트랙, 자막 트랙, **같은 폴더의 자막 파일**이다. 사용자에게는 셋 다 '무슨 소리로
 * 볼까, 무슨 자막으로 볼까' 하나의 물음이라 화면을 나눌 이유가 없다. 안에서 하는 일은
 * 다르다 — 앞의 둘은 `TrackSelectionParameters` 한 줄이고 자막 파일은 항목을 다시
 * 세우는 무거운 길이지만(`PlaybackConnection.selectSubtitleFile`), 그것은 우리 사정이지
 * 보는 사람의 사정이 아니다.
 *
 * ## 그림 자막은 여기 오지 않는다
 *
 * PGS·VobSub 는 목록을 만드는 자리에서 걸러진다([PlaybackConnection] 의 `refreshTracks`).
 * 우리 자막 층은 글만 그리므로(`SubtitleLayer`), 고를 수 있게 두면 **점만 옮겨 가고
 * 화면에는 아무 글자도 뜨지 않는다.**
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackSheet(onDismiss: () -> Unit) {
    val choices by PlaybackConnection.tracks.collectAsStateWithLifecycle()
    // **자막이 꺼져 있으면 어느 자막 줄에도 표시를 하지 않는다.** 끈 채로 파일이 체크되어
    // 있으면 '붙어 있는데 안 나온다' 로 읽힌다 — 붙어 있는 것은 사실이지만 지금 보이는
    // 것은 아니다. 체크는 '지금 이것을 보고 있다' 는 뜻이어야 한다.
    val textOn = !choices.textDisabled

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text(
                text = tracksTitle(),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )

            if (choices.isEmpty) {
                Text(
                    text = noTracksLabel(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                return@Column
            }

            // 소리 트랙은 **둘 이상일 때만** 보여 준다. 하나뿐인 목록은 아무것도 가르치지 않는다.
            if (choices.audio.size > 1) {
                SectionHeader(audioTracksHeader())
                for (option in choices.audio) {
                    ChoiceRow(
                        label = trackLabel(option.descriptor, isText = false),
                        selected = option.selected,
                        enabled = option.supported,
                        onClick = { PlaybackConnection.selectTrack(option.id) },
                    )
                }
            }

            if (choices.text.isNotEmpty() || choices.files.isNotEmpty()) {
                SectionHeader(textTracksHeader())
                // **끄기가 맨 위다.** 자막을 끄려는 사람은 목록을 읽으러 온 것이 아니다.
                ChoiceRow(
                    label = subtitleOffLabel(),
                    selected = choices.textDisabled,
                    enabled = true,
                    onClick = { PlaybackConnection.setTextEnabled(false) },
                )
                for (option in choices.text) {
                    ChoiceRow(
                        label = trackLabel(option.descriptor, isText = true),
                        selected = textOn && option.selected,
                        enabled = option.supported,
                        onClick = { PlaybackConnection.selectTrack(option.id) },
                    )
                }
            }

            if (choices.files.isNotEmpty()) {
                SectionHeader(subtitleFilesHeader())
                for (file in choices.files) {
                    ChoiceRow(
                        label = file.name,
                        selected = textOn && file.selected,
                        enabled = true,
                        onClick = { PlaybackConnection.selectSubtitleFile(file.path) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    HorizontalDivider(Modifier.padding(top = 8.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 4.dp),
    )
}

/**
 * 고를 수 있는 줄 하나.
 *
 * 고른 것을 **왼쪽 체크**로 말한다. 라디오 단추 대신 체크를 쓰는 것은 자리가 셋으로
 * 나뉘어 있어(소리·자막·자막 파일) 한 무리 안의 배타 선택으로 읽히지 않기 때문이다.
 *
 * 못 푸는 트랙도 **보여 주되 누를 수 없게** 한다. 목록에서 빼면 '내 영상에 일본어 소리가
 * 있는데 왜 안 보이지' 가 되고, 그 답이 '이 기기가 못 푼다' 라는 것을 말할 자리가 사라진다.
 */
@Composable
private fun ChoiceRow(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                androidx.compose.ui.graphics.Color.Transparent
            },
            modifier = Modifier.size(20.dp),
        )
        Column(Modifier.padding(start = 16.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                },
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                // 자막 파일 이름은 끝이 다르다(`영화.ko.srt`·`영화.en.srt`) — 끝을 자르면
                // 전부 같은 줄로 보인다. 재생목록이 같은 이유로 같은 규칙을 쓴다.
                overflow = TextOverflow.MiddleEllipsis,
            )
            if (!enabled) {
                Text(
                    text = unsupportedTrackLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
