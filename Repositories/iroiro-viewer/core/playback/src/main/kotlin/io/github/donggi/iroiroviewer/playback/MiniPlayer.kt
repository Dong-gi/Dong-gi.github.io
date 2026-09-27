package io.github.donggi.iroiroviewer.playback

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 목록 아래에 붙는 72dp 짜리 재생 바.
 *
 * 파일을 탐색하면서 재생이 이어지는 감각을 주는 것이 이 바의 전부다. 눌러 들어가면
 * 큰 화면([PlayerActivity])이고, 거기서 나와도 재생은 계속된다.
 */
@Composable
fun MiniPlayer(modifier: Modifier = Modifier) {
    val state by PlaybackConnection.state.collectAsStateWithLifecycle()
    if (!state.hasItem) return
    val context = LocalContext.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 3.dp,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clickable { PlayerSupport.open(context) }
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                    )
                    Text(
                        text = "${formatTime(state.positionMs)} / ${formatTime(state.durationMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                IconButton(onClick = { PlaybackConnection.togglePlayPause() }) {
                    Icon(
                        imageVector = if (state.isPlaying) PauseIcon else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(
                            if (state.isPlaying) R.string.playback_pause else R.string.playback_play
                        ),
                    )
                }
                IconButton(onClick = { PlaybackConnection.stop() }) {
                    Icon(Icons.Filled.Close, stringResource(R.string.playback_stop))
                }
            }
            if (state.durationMs > 0) {
                LinearProgressIndicator(
                    progress = { (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                )
            }
        }
    }
}

/**
 * 일시정지 아이콘. 아이콘 core 세트에 `Pause` 가 없어 직접 만든다 —
 * 이것 하나 때문에 수천 개짜리 확장 세트를 넣지 않는다.
 */
/**
 * 일시정지. **`res/drawable/ic_pip_pause.xml` 이 같은 좌표의 짝이다.**
 *
 * PiP 창의 조작은 `RemoteAction` 이고 그것은 `android.graphics.drawable.Icon` 만 받아
 * `ImageVector` 에서 오는 변환 경로가 없다. 그래서 같은 그림을 두 형식으로 든다 —
 * **한쪽만 고치면 PiP 창과 본화면의 같은 단추가 다른 모양이 된다.**
 */
val PauseIcon: ImageVector by lazy {
    androidx.compose.ui.graphics.vector.ImageVector.Builder(
        name = "Pause",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        val fill = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.Black)
        addPath(
            pathData = androidx.compose.ui.graphics.vector.PathData {
                moveTo(6f, 5f); lineTo(10f, 5f); lineTo(10f, 19f); lineTo(6f, 19f); close()
                moveTo(14f, 5f); lineTo(18f, 5f); lineTo(18f, 19f); lineTo(14f, 19f); close()
            },
            fill = fill,
        )
    }.build()
}

fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
