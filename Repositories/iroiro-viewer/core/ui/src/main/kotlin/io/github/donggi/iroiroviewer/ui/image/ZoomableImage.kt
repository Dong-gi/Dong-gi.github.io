package io.github.donggi.iroiroviewer.ui.image

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import io.github.donggi.iroiroviewer.ui.gesture.ZoomMath
import io.github.donggi.iroiroviewer.ui.gesture.ZoomState
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * 확대·이동되는 한 장.
 *
 * ## 제스처를 두 겹으로 나누는 이유
 *
 * ```
 * Box(바깥)  .pointerInput { 탭·더블탭 }        ← 소비되지 않은 것만 본다
 *   Box(안쪽).transformable(canPan)              ← 두 손가락, 그리고 확대 상태의 팬
 *     Canvas .그리기(scale·translate)            ← 변환은 여기서만 읽는다
 * ```
 *
 * 같은 요소에 `pointerInput` 을 여럿 얹고 **형제 사이의 순서에 기대는 것**이 이미지
 * 뷰어에서 가장 흔한 실패다. 그 순서는 보장된 계약이 아니다. 대신 부모–자식으로 나누면
 * "자식이 먼저, 부모가 나중" 이 문서화된 규칙이 되고, 부모는 자식이 소비하지 않은
 * 이벤트만 보게 된다.
 *
 * 그래서:
 * - 배율 1 → `canPan = false` → `transformable` 이 한 손가락 드래그를 소비하지 않는다
 *   → **페이저가 받아 다음 장으로 넘어간다.**
 * - 배율 > 1 → `transformable` 이 팬을 소비한다 → 페이저는 못 받는다.
 * - 탭·더블탭은 어느 쪽도 소비하지 않으므로 언제나 바깥이 받는다.
 *
 * **받아들이는 한계**: 확대한 채 경계까지 민 뒤 *같은 제스처 안에서* 다음 장으로 넘어가는
 * 것은 안 된다. 한 번 슬롭을 넘긴 인식기는 `canPan` 이 거짓이 되어도 소비를 멈추지 않기
 * 때문이다. Google Photos 도 같다 — 손을 뗐다 다시 밀면 된다.
 */
@Composable
fun ZoomableImage(
    bitmap: ImageBitmap?,
    state: ZoomState,
    modifier: Modifier = Modifier,
    onTap: () -> Unit = {},
    onLongPress: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()

    // **중심점(centroid)을 받는 쪽을 쓴다.** 받지 않는 오버로드는 deprecated 이고,
    // 그것을 쓰면 핀치가 **언제나 화면 가운데를 중심으로** 커진다 — 사람은 자기 두
    // 손가락 사이가 커지기를 기대하므로, 보려던 부분이 화면 밖으로 밀려난다.
    val transformable = rememberTransformableState { centroid, zoomChange, panChange, _ ->
        // 뷰포트 중앙을 원점으로 옮긴다. ZoomState 의 좌표 약속이 그것이다.
        val pivot = Offset(
            centroid.x - state.viewport.width / 2f,
            centroid.y - state.viewport.height / 2f,
        )
        state.transform(zoomChange, panChange, pivot)
    }

    // 손가락이 닿으면 돌고 있던 더블탭 애니메이션을 멈춘다. 그러지 않으면 애니메이션과
    // 제스처가 같은 값을 동시에 써서 화면이 튄다.
    LaunchedEffect(transformable.isTransformInProgress) {
        if (transformable.isTransformInProgress) state.stopAnimation()
    }

    Box(
        modifier = modifier
            .pointerInput(state) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = { onLongPress() },
                    onDoubleTap = { position ->
                        scope.launch {
                            state.stopAnimation()
                            // 뷰포트 중앙을 원점으로 옮긴다. 짚은 자리가 제자리에 남아야 한다.
                            val pivot = Offset(
                                position.x - state.viewport.width / 2f,
                                position.y - state.viewport.height / 2f,
                            )
                            state.animateDoubleTap(pivot)
                        }
                    },
                )
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .transformable(state = transformable, canPan = { state.canPan }),
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged {
                        if (bitmap != null) {
                            state.onLayout(
                                Size(it.width.toFloat(), it.height.toFloat()),
                                bitmap.width,
                                bitmap.height,
                            )
                        }
                    },
            ) {
                if (bitmap != null) drawFitted(bitmap, state)
            }
        }
    }
}

/**
 * 배율 1 에서 화면에 맞추고, 그 위에 확대·이동을 얹어 그린다.
 *
 * **변환을 [ZoomState] 한 곳에서만 읽는다.** 층(흐린 바닥 / 선명한 상세)이 늘어나도 같은
 * 변환을 쓰면 교체되는 프레임에 그림이 튀지 않는다. 층마다 자기 변환을 들면 반드시 어긋난다.
 */
private fun DrawScope.drawFitted(bitmap: ImageBitmap, state: ZoomState) {
    val (fw, fh) = ZoomMath.fittedSize(bitmap.width, bitmap.height, size.width, size.height)
    if (fw <= 0f || fh <= 0f) return
    translate(state.offset.x, state.offset.y) {
        scale(state.scale, pivot = center) {
            val left = (size.width - fw) / 2f
            val top = (size.height - fh) / 2f
            drawImage(
                image = bitmap,
                dstOffset = androidx.compose.ui.unit.IntOffset(left.toInt(), top.toInt()),
                dstSize = androidx.compose.ui.unit.IntSize(fw.toInt(), fh.toInt()),
            )
        }
    }
}
