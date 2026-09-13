package io.github.donggi.iroiroviewer.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.text.Cue
import io.github.donggi.iroiroviewer.playback.PlaybackConnection

/**
 * 자막을 그리는 층. 영상 위에 겹친다.
 *
 * ## 왜 직접 그리는가
 *
 * media3 에도 자막 뷰(`SubtitleView`)가 있지만 그것은 **View 기반**이라 Compose 화면에
 * `AndroidView` 로 끼워 넣어야 하고, 그러려면 `media3-ui` 의존을 새로 들여야 한다.
 * 10단계의 전제가 '새 의존을 들이지 않는다' 이고, 우리가 그리는 것은 **글 몇 줄**이다.
 *
 * ## 무엇을 존중하고 무엇을 버리는가
 *
 * [Cue] 는 위치·정렬·크기·배경색·비트맵까지 들고 있다. 그 전부를 따르면 ASS 조판기를
 * 다시 만드는 일이 된다. 여기서 지키는 것은 **읽는 데 필요한 둘**이다.
 *
 * - **아래냐 위냐.** ASS 의 `\an8`(위쪽 정렬)은 화면 위쪽 글자를 가리지 않으려고 쓰는
 *   것이라 무시하면 대사가 겹친다. [Cue.line] 과 [Cue.lineAnchor] 로 가른다.
 * - **글의 정렬.** 가운데가 기본이고 왼쪽·오른쪽 정렬만 따른다.
 *
 * 버리는 것: 글자색·크기·회전·카라오케·그림 자막(PGS·VobSub). **그림 자막은 아예
 * 그리지 않는다** — [Cue.bitmap] 을 그리려면 좌표계를 영상 화면에 정확히 맞춰야 하고,
 * 그것은 이 층이 하려는 일(글 몇 줄)과 종류가 다르다.
 */
@Composable
fun BoxScope.SubtitleLayer(modifier: Modifier = Modifier) {
    val cues by PlaybackConnection.cues.collectAsStateWithLifecycle()
    if (cues.isEmpty()) return

    val top = cues.filter { it.isTop }
    val bottom = cues.filterNot { it.isTop }

    Box(modifier.matchParentSize()) {
        if (top.isNotEmpty()) {
            CueColumn(top, Modifier.align(Alignment.TopCenter).padding(top = 24.dp))
        }
        if (bottom.isNotEmpty()) {
            CueColumn(bottom, Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
        }
    }
}

/**
 * 화면 위쪽에 붙는 자막인가.
 *
 * [Cue.line] 은 `LINE_TYPE_FRACTION` 이면 0(위)~1(아래) 비율이고, 그 값이 없으면
 * ([Cue.DIMEN_UNSET]) 아래가 기본이다. ASS 의 `\an7~\an9` 가 이 길로 들어온다.
 */
private val Cue.isTop: Boolean
    get() = lineType == Cue.LINE_TYPE_FRACTION && line != Cue.DIMEN_UNSET && line < 0.5f

@Composable
private fun CueColumn(cues: List<Cue>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        for (cue in cues) {
            val text = cue.text?.toString() ?: continue
            if (text.isBlank()) continue
            Text(
                text = text,
                color = Color.White,
                fontSize = 18.sp,
                textAlign = when (cue.textAlignment) {
                    android.text.Layout.Alignment.ALIGN_OPPOSITE -> TextAlign.End
                    android.text.Layout.Alignment.ALIGN_NORMAL -> TextAlign.Start
                    else -> TextAlign.Center
                },
                modifier = Modifier
                    // **반투명한 검은 판을 깐다.** 흰 글자만 얹으면 밝은 장면에서 사라진다.
                    // 테두리(outline)를 그리려면 글자를 두 번 그려야 하는데, Compose 에서
                    // 그것은 같은 `Text` 를 겹치는 일이라 줄바꿈이 어긋날 수 있다.
                    .background(Color(0xA6000000))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

/** 비디오 표면이 없을 때(소리만) 자막을 가운데에 그린다. */
@Composable
fun AudioSubtitleLayer(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) { SubtitleLayer() }
}
