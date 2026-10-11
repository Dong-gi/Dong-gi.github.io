package io.github.donggi.iroiroviewer.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * 이 앱의 슬라이더 — 재생 화면이 정한 **YouTube·VLC 의 모양**: 얇은 트랙 + 둥근 점, 지나온 쪽은 강조색, 남은 쪽은 옅게, 끝점 표시가
 * 없다. 10단계에 재생 막대에서 세웠고(사용자가 Material 3 의 기본 손잡이와 알약 트랙을 '익숙한 형태가 아니라 어색하다' 고 짚었다),
 * 2026-10-03 사용자 요청으로 **앱의 모든 슬라이더**(재생 위치·만화와 문서의 쪽·설정의 크기)가 이것을 쓴다.
 *
 * Material 3 의 [Slider] 에 트랙과 손잡이만 갈아 끼운다 — 손가락 다루기·눈금 맞추기(`steps`)·화면 낭독기의 의미(진행 범위·값 바꾸기)는
 * 그대로다. 눈금 표시는 그리지 않는다(쪽이 수천인 문서에서 트랙이 회색 띠가 된다). 화면 낭독기가 읽을 이름과 지금 값은 부르는 쪽이
 * [modifier] 의 `semantics` 로 준다(백분율로 읽히지 않게 — CLAUDE.md 함정 표).
 *
 * @param inactiveColor 남은 쪽. 바탕이 검든 희든 보이도록 글자색을 옅혀 쓰는 것이 기본이다 — 어두운 막대 위에서는 부르는 쪽이 흰색을 준다.
 * @param trackOverlay 트랙 위에 얹을 것(재생 화면의 A-B 구간 표시). 트랙의 폭(`maxWidth`)을 알고 그린다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IroSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    enabled: Boolean = true,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = LocalContentColor.current.copy(alpha = INACTIVE_ALPHA),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    trackOverlay: @Composable BoxWithConstraintsScope.() -> Unit = {},
) {
    // 평소에는 작은 점, 잡고 있는 동안에는 커진다. 즉시 바뀌면 튀므로 애니메이션을 건다.
    val dragging by interactionSource.collectIsDraggedAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val dot by animateDpAsState(if (dragging || pressed) THUMB_ACTIVE else THUMB_IDLE, label = "thumb")
    val active = if (enabled) activeColor else activeColor.copy(alpha = DISABLED_ALPHA)
    // NaN 은 Material 3 가 화면 낭독기용으로 만드는 `ProgressBarRangeInfo` 가 던진다 — [IroProgress] 를 죽인 그 값이다(2026-10-05).
    // 지금 부르는 쪽은 모두 막고 있지만, 막는 자리를 공용 부품에도 둔다.
    val safeValue = if (value.isNaN()) valueRange.start else value
    val span = valueRange.endInclusive - valueRange.start
    val fraction = if (span > 0f) progressFraction((safeValue - valueRange.start) / span) else 0f
    Slider(
        value = safeValue,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interactionSource,
        track = {
            // **폭을 알아야 얹을 것을 놓을 수 있다.** 비율로만 두면 `fillMaxWidth` 상자가 가운데 정렬을 물려받아 엉뚱한 자리에
            // 그려진다. `offset(x)` 는 레이아웃 방향을 따르므로 RTL 도 함께 맞는다.
            BoxWithConstraints(Modifier.fillMaxWidth().height(TRACK_HEIGHT)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(TRACK_HEIGHT / 2))
                        .background(inactiveColor),
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction)
                            .background(active),
                    )
                }
                trackOverlay()
            }
        },
        thumb = {
            // **재는 상자는 고정하고 안의 점만 키운다.** Material 3 은 트랙을 `constraints.offset(-손잡이 너비)` 로 재므로(10단계에
            // 바이트코드로 확인) 손잡이의 **측정 크기**를 애니메이션하면 막대의 좌우 여백이 잡을 때마다 함께 흔들린다.
            Box(Modifier.size(THUMB_SLOT), contentAlignment = Alignment.Center) {
                Box(Modifier.size(dot).background(active, CircleShape))
            }
        },
    )
}

/**
 * 이 앱의 진행 막대 — [IroSlider] 의 트랙과 같은 모양(얇고 둥근 막대, 끝점 표시·틈 없음). Material 3 의 `LinearProgressIndicator` 는
 * 채운 쪽과 남은 쪽 사이에 틈을 두고 끝에 점을 찍는다 — 재생기에는 없는 기호라 쓰지 않는다(2026-10-03 사용자 요청으로 통일).
 *
 * [progress] 가 null 이면 끝을 모르는 진행(옅은 트랙 위를 짧은 막대가 지나간다). 값은 그리는 자리에서 읽는다 — 진행이 바뀔 때마다
 * 다시 구성하지 않는다.
 *
 * **값은 [progressFraction] 을 지나서만 쓴다.** 그리는 자리에서 읽으므로 부르는 쪽이 둘러 둔 `if (길이 > 0)` 은 그 순간에 이미 지나간
 * 판단일 수 있다 — 재생 상태를 위임 속성(`by`)으로 읽는 람다는 다음 곡이 길이 0 으로 시작하는 순간 0/0 = NaN 을 돌려준다. NaN 은
 * `coerceIn` 을 그대로 지나고(비교가 모두 거짓이다), 의미 정보의 `ProgressBarRangeInfo` 가 그것을 받으면 **던진다** — 재구성이 막대를
 * 걷어 내기 전에 의미 갱신이 먼저 돌아 앱이 죽었다(재생 중에 다른 소리 파일을 누르면).
 */
@Composable
fun IroProgress(
    progress: (() -> Float)?,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = LocalContentColor.current.copy(alpha = INACTIVE_ALPHA),
    height: Dp = TRACK_HEIGHT,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val bar = Modifier
        .fillMaxWidth()
        .height(height)
        .clip(RoundedCornerShape(height / 2))
        .background(trackColor)
    if (progress != null) {
        Box(
            modifier
                .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progressFraction(progress()), 0f..1f) }
                .then(bar)
                .drawBehind {
                    val w = size.width * progressFraction(progress())
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(if (rtl) size.width - w else 0f, 0f),
                        size = Size(w, size.height),
                        cornerRadius = CornerRadius(size.height / 2, size.height / 2),
                    )
                },
        )
    } else {
        val transition = rememberInfiniteTransition(label = "indeterminate")
        val head by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(INDETERMINATE_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "head",
        )
        Box(
            modifier
                .semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }
                .then(bar)
                .drawBehind {
                    // 막대 하나(폭의 1/3)가 왼쪽 밖에서 들어와 오른쪽 밖으로 나간다.
                    val seg = size.width / 3
                    val start = -seg + (size.width + seg) * head
                    val left = if (rtl) size.width - start - seg else start
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(left, 0f),
                        size = Size(seg, size.height),
                        cornerRadius = CornerRadius(size.height / 2, size.height / 2),
                    )
                },
        )
    }
}

/**
 * 진행 값을 막대가 쓸 수 있는 [0, 1] 로 옮긴다. **NaN 은 0 이다** — `NaN.coerceIn(0f, 1f)` 은 NaN 을 돌려준다. 무한대는 `coerceIn` 이
 * 끝으로 민다(0 으로 나눈 양수는 1, 음수는 0).
 */
internal fun progressFraction(value: Float): Float = if (value.isNaN()) 0f else value.coerceIn(0f, 1f)

/**
 * 트랙의 두께. 재생 막대에서 정한 값이다 — YouTube 가 3dp 안팎, VLC 가 그보다 조금 두껍다. 4dp 면 검은 영상 위에서도 보이고 손잡이
 * (11dp 점)와 굵기가 확실히 갈린다(트랙이 두꺼우면 점이 트랙에 묻힌다).
 */
private val TRACK_HEIGHT: Dp = 4.dp

/** 손잡이가 **차지하는** 자리. 점이 커져도 이 값은 변하지 않는다(위 '재는 상자는 고정'). */
private val THUMB_SLOT = 16.dp
private val THUMB_IDLE = 11.dp
private val THUMB_ACTIVE = 16.dp

/** 남은 쪽의 옅기. */
private const val INACTIVE_ALPHA = 0.28f
private const val DISABLED_ALPHA = 0.38f
private const val INDETERMINATE_MS = 1_400
