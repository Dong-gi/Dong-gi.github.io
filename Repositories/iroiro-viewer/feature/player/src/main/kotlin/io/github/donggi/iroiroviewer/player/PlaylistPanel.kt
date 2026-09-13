package io.github.donggi.iroiroviewer.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.donggi.iroiroviewer.model.FileKind
import io.github.donggi.iroiroviewer.playback.NowPlayingIcon
import io.github.donggi.iroiroviewer.playback.PlaybackConnection
import io.github.donggi.iroiroviewer.playback.TrackIcon
import io.github.donggi.iroiroviewer.playback.VideoIcon
import io.github.donggi.iroiroviewer.playback.playlistEmptyLabel

/**
 * 재생목록 — **보이고, 아무 줄이나 눌러 그 곡으로 간다.**
 *
 * ## 왜 이 화면이 필요한가
 *
 * 처음 만든 큐는 '만들어 놓고 차례로 트는 것' 뿐이라, 사용자는 다음/이전으로만 움직일 수
 * 있었다. 18곡짜리 폴더에서 16번째를 들으려면 열다섯 번을 눌러야 한다. VLC 를 비롯한
 * 재생기들이 **재생목록을 화면 본체로 두는** 이유가 그것이다 — 목록이 곧 이동 수단이다.
 *
 * ## 무엇을 보여 주고 무엇을 보여 주지 않는가
 *
 * 줄마다 **아이콘과 제목**뿐이다. VLC 는 길이와 아티스트를 함께 적지만 그것은 미디어
 * 라이브러리를 만들어 두었기 때문이다. 우리는 **목록을 그리려고 파일을 열지 않는다**
 * (`FileKind` 의 주석) — 500개의 길이를 알려면 500번 열어야 하고, 그 규칙은 이 앱에서
 * 가장 여러 번 값을 한 규칙이다. 길이는 그 곡을 틀면 아래 막대에 나온다.
 *
 * **순서는 폴더에서 본 그대로**다. 섞기를 켜도 목록은 재배열되지 않고 다음에 무엇이
 * 나올지만 달라진다 — 켤 때마다 줄이 뛰어다니면 방금 본 것을 다시 찾을 수 없다.
 */
@Composable
fun PlaylistPanel(currentIndex: Int, bottomInset: Dp = 0.dp, modifier: Modifier = Modifier) {
    val queue by PlaybackConnection.queue.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // **지금 곡이 보이는 자리로 목록을 옮긴다.** 16번째를 듣고 있는데 목록이 1번부터
    // 서 있으면, 목록을 여는 첫 동작이 언제나 '스크롤해서 나를 찾기' 가 된다.
    LaunchedEffect(currentIndex, queue.size) {
        if (currentIndex in queue.indices) {
            listState.scrollToItem(currentIndex.coerceAtLeast(0))
        }
    }

    // **완전히 덮는다.** 조금이라도 비치면 아래의 제목·영상이 목록 글씨와 겹쳐 어른거린다 —
    // 목록은 읽으라고 여는 것이지 뒤를 곁눈질하라고 여는 것이 아니다.
    //
    // 색은 **테마를 따른다.** 목록이 열려 있는 동안에는 영상이 보이지 않으므로 이 화면만
    // 검을 이유가 없다 — 시스템이 밝은 모드인데 여기만 검으면 그것이 오히려 튄다.
    // (영상이 보이는 동안에는 액티비티가 테마를 어둡게 못 박으므로 여기도 자연히 검다.)
    val surface = MaterialTheme.colorScheme.background
    val onSurface = MaterialTheme.colorScheme.onSurface
    Box(modifier.background(surface)) {
        if (queue.isEmpty()) {
            Text(
                text = playlistEmptyLabel(),
                color = onSurface,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
            return@Box
        }
        // **시스템 바를 피한다.** 영상은 화면 끝까지 차야 하므로 이 층의 부모에는 인셋이
        // 없다. 목록은 글이라 상태 표시줄 아래에서 시작해야 첫 줄이 시계에 물리지 않는다.
        LazyColumn(
            state = listState,
            // 조작부 판이 덮는 만큼 끝에 자리를 비운다. 부르는 쪽이 재서 준다.
            contentPadding = PaddingValues(bottom = bottomInset),
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        ) {
            items(queue, key = { it.index }) { item ->
                QueueRow(
                    title = item.title,
                    kind = item.kind,
                    playing = item.index == currentIndex,
                    onSurface = onSurface,
                    onClick = { PlaybackConnection.playAt(item.index) },
                )
            }
        }
    }
}

@Composable
private fun QueueRow(
    title: String,
    kind: FileKind,
    playing: Boolean,
    onSurface: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            // **영상과 소리를 아이콘으로 가른다.** 전부 음표를 달면 목록만 보고는 무엇이
            // 영상인지 알 수 없다(사용자가 지적했다). 재생 중인 줄은 종류와 무관하게
            // 막대 아이콘이다 — 그 자리에서 묻는 것이 '무엇인가' 가 아니라 '어디인가' 라서다.
            imageVector = when {
                playing -> NowPlayingIcon
                kind == FileKind.VIDEO -> VideoIcon
                else -> TrackIcon
            },
            contentDescription = null,
            tint = if (playing) MaterialTheme.colorScheme.primary else onSurface.copy(alpha = 0.55f),
            modifier = Modifier.size(20.dp),
        )
        Column(Modifier.padding(start = 16.dp)) {
            Text(
                text = title,
                color = if (playing) onSurface else onSurface.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (playing) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                // **가운데를 줄인다.** 같은 회차의 파일은 끝이 다르므로(`01화.mp4`·`02화.mp4`)
                // 끝을 자르면 전부 같은 줄로 보인다.
                overflow = TextOverflow.MiddleEllipsis,
            )
        }
    }
}
