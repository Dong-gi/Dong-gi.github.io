package io.github.donggi.iroiroviewer.ui.image

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    detail: DetailLayer? = null,
    /**
     * 원본의 폭(화면 방향 기준, 화소). **최대 배율을 이것으로 잰다** — 원본의 2배까지
     * ([ZoomMath.maxScale]). [bitmap] 은 화면에 맞춰 줄여 뜬 것이라 원본이 아니다.
     * 주지 않으면 바닥층 폭으로 친다.
     */
    originalWidth: Int = bitmap?.width ?: 0,
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
            // **크기가 그대로여도 원본이 늦게 알려질 수 있다**(사진의 원본 치수를 재는 일이
            // 바닥층 디코딩과 따로 돈다). `onSizeChanged` 는 크기가 바뀔 때만 오므로 그것만
            // 믿으면 최대 배율이 옛 값에 머문다. 재구성마다 한 번 더 알리되, `onLayout` 이
            // 같은 값이면 곧바로 돌아온다.
            var canvasSize by remember { mutableStateOf(Size.Zero) }
            SideEffect {
                if (bitmap != null && canvasSize.width > 0f) {
                    state.onLayout(
                        canvasSize,
                        bitmap.width,
                        bitmap.height,
                        originalWidth.takeIf { w -> w > 0 } ?: bitmap.width,
                    )
                }
            }
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged {
                        canvasSize = Size(it.width.toFloat(), it.height.toFloat())
                        if (bitmap != null) {
                            state.onLayout(
                                canvasSize,
                                bitmap.width,
                                bitmap.height,
                                originalWidth.takeIf { w -> w > 0 } ?: bitmap.width,
                            )
                        }
                    },
            ) {
                if (bitmap != null) drawFitted(bitmap, detail, state)
            }
        }
    }
}

/**
 * 바닥층 위에 얹는 **선명한 조각**.
 *
 * 좌표 넷은 전부 바닥층 안의 **비율**(0~1)이다 — 화소가 아니다. 바닥층은 뷰포트에
 * 맞춰지므로 화면이 돌거나 예산이 바뀌면 그 화소 크기가 달라지는데, 비율로 적어 두면
 * 얹는 자리가 그 변화를 따라간다.
 *
 * PDF 뷰어는 더 큰 배율로 **다시 그린** 타일을, 사진·만화 뷰어는 원본을 **영역 디코딩**한
 * 조각을 얹는다([rememberRasterDetail]). 어느 쪽이든 바닥층은 흐린 채로 남아 조각이 오기
 * 전과 조각이 덮지 못한 자리를 메운다.
 */
data class DetailLayer(
    val image: ImageBitmap,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

/**
 * 배율 1 에서 화면에 맞추고, 그 위에 확대·이동을 얹어 그린다.
 *
 * **변환을 [ZoomState] 한 곳에서만 읽는다.** 층(흐린 바닥 / 선명한 상세)이 늘어나도 같은
 * 변환을 쓰면 교체되는 프레임에 그림이 튀지 않는다. 층마다 자기 변환을 들면 반드시 어긋난다.
 *
 * 상세층은 **바닥층을 지우지 않고 덮는다.** 타일이 도착하기 전에도, 타일이 화면의 일부만
 * 덮을 때도 나머지는 흐린 바닥이 메운다 — 비어 있는 흰 자리가 보이는 것보다 낫다.
 */
private fun DrawScope.drawFitted(bitmap: ImageBitmap, detail: DetailLayer?, state: ZoomState) {
    val (fw, fh) = ZoomMath.fittedSize(bitmap.width, bitmap.height, size.width, size.height)
    if (fw <= 0f || fh <= 0f) return
    translate(state.offset.x, state.offset.y) {
        scale(state.scale, pivot = center) {
            val left = (size.width - fw) / 2f
            val top = (size.height - fh) / 2f
            // **두 층 다 부동소수로 놓는다.** 예전에는 자리와 크기를 `IntOffset`·`IntSize` 로
            // 잘라 **확대 변환 안에서** 그렸다 — 잘린 1화소 미만의 어긋남이 배율만큼 커져,
            // 원본 2배까지 확대하면 선명한 조각이 바닥층에서 수 ~ 수십 화소 떨어진 자리에 얹혀
            // 조각이 올 때마다 그림이 튀었다(적대적 검토가 계산으로 잡았다: 12000 화소
            // 파노라마에서 18 화소). 바닥층과 조각이 `ZoomMath` 가 가정하는 같은 사상을 쓴다.
            translate(left, top) {
                scale(fw / bitmap.width, fh / bitmap.height, pivot = Offset.Zero) {
                    drawImage(bitmap)
                }
            }
            if (detail != null && detail.image.width > 0 && detail.image.height > 0) {
                translate(left + detail.left * fw, top + detail.top * fh) {
                    scale(
                        detail.width * fw / detail.image.width,
                        detail.height * fh / detail.image.height,
                        pivot = Offset.Zero,
                    ) {
                        drawImage(detail.image)
                    }
                }
            }
        }
    }
}
