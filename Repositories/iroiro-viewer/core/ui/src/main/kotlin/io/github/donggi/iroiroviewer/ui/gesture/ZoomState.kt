package io.github.donggi.iroiroviewer.ui.gesture

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * 한 장의 확대·이동 상태. **변환이 사는 유일한 곳이다.**
 *
 * 여러 겹(흐린 바닥층 / 선명한 상세층)을 겹쳐 그려도 변환을 여기 하나에서만 읽으면
 * 층이 바뀌는 순간 그림이 튀지 않는다. 층마다 자기 변환을 들면 교체 프레임에서
 * 반드시 한 번 어긋난다.
 *
 * 계산은 전부 [ZoomMath] 에 있다 — 여기는 상태와 애니메이션만 쥔다.
 */
class ZoomState {

    var scale by mutableFloatStateOf(1f)
        private set

    var offset by mutableStateOf(Offset.Zero)
        private set

    /** 뷰포트 크기(화면 픽셀). 레이아웃이 알려 준다. */
    var viewport by mutableStateOf(Size.Zero)
        private set

    /** 배율 1 에서 실제로 그려지는 크기. `ContentScale.Fit` 의 결과다. */
    var fitted by mutableStateOf(Size.Zero)
        private set

    /** 그려지는 바닥층의 화소 폭. 맞춤 크기를 재는 데 쓴다(가로세로 비). */
    private var sourceWidth = 0

    /** 원본의 폭. 최대 배율을 재는 데 쓴다([ZoomMath.maxScale]). */
    private var originalWidth = 0

    var maxScale by mutableFloatStateOf(ZoomMath.MIN_MAX_SCALE)
        private set

    private val animScale = Animatable(1f)
    private val animOffsetX = Animatable(0f)
    private val animOffsetY = Animatable(0f)

    /** 지금 확대되어 있는가. 페이저에게 손가락을 넘길지 정하는 값이다. */
    val isZoomed: Boolean get() = !ZoomMath.sameScale(scale, 1f)

    val maxOffsetX: Float
        get() = ZoomMath.maxOffset(fitted.width, viewport.width, scale)

    val maxOffsetY: Float
        get() = ZoomMath.maxOffset(fitted.height, viewport.height, scale)

    /** 아직 밀 자리가 남았는가. 남지 않았으면 페이저가 손가락을 가져간다. */
    val canPan: Boolean get() = ZoomMath.canPan(maxOffsetX, maxOffsetY)

    /**
     * @param contentWidth·[contentHeight] 그리는 바닥층 비트맵의 화소.
     * @param original 원본의 폭(화면 방향 기준). 바닥층은 화면에 맞춰 줄여 뜬 것이라 원본이
     *   아니다 — 최대 배율은 이 값으로 잰다. 모르면 바닥층 폭을 준다.
     */
    fun onLayout(viewportSize: Size, contentWidth: Int, contentHeight: Int, original: Int = contentWidth) {
        if (viewportSize == viewport && contentWidth == sourceWidth && original == originalWidth) return
        viewport = viewportSize
        sourceWidth = contentWidth
        originalWidth = original
        val (w, h) = ZoomMath.fittedSize(
            contentWidth, contentHeight, viewportSize.width, viewportSize.height
        )
        fitted = Size(w, h)
        maxScale = ZoomMath.maxScale(original, w)
        // 상한이 내려갔으면(다른 원본으로 바뀌었다) 지금 배율도 그 안으로 들인다.
        if (scale > maxScale) scale = maxScale
        clampNow()
    }

    /** 두 손가락 제스처 한 번. [pivot] 은 뷰포트 중앙을 원점으로 한 좌표다. */
    fun transform(zoomChange: Float, panChange: Offset, pivot: Offset) {
        val old = scale
        val next = (old * zoomChange).coerceIn(1f, maxScale)
        val zoomed = Offset(
            ZoomMath.offsetAfterZoom(offset.x, pivot.x, old, next),
            ZoomMath.offsetAfterZoom(offset.y, pivot.y, old, next),
        )
        scale = next
        offset = zoomed + panChange
        clampNow()
    }

    /**
     * 더블탭. 배율과 오프셋을 **함께** 애니메이션한다.
     *
     * 따로 돌리면 확대되는 동안 그림이 한 번 미끄러진다. 그리고 손가락이 다시 닿으면
     * [stopAnimation] 으로 즉시 멈춰야 한다 — 애니메이션과 제스처가 같은 값을 동시에
     * 쓰면 화면이 튄다.
     */
    suspend fun animateDoubleTap(pivot: Offset) {
        val target = ZoomMath.doubleTapTarget(scale, minOf(ZoomMath.DOUBLE_TAP_SCALE, maxScale))
        val targetOffset = if (ZoomMath.sameScale(target, 1f)) {
            Offset.Zero
        } else {
            Offset(
                ZoomMath.clamp(
                    ZoomMath.offsetAfterZoom(offset.x, pivot.x, scale, target),
                    fitted.width, viewport.width, target,
                ),
                ZoomMath.clamp(
                    ZoomMath.offsetAfterZoom(offset.y, pivot.y, scale, target),
                    fitted.height, viewport.height, target,
                ),
            )
        }
        animateTo(target, targetOffset)
    }

    /** 배율 1 로 되돌린다. 페이지를 떠날 때 부른다 — 다음에 그 장을 열면 처음처럼 보여야 한다. */
    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    private suspend fun animateTo(targetScale: Float, targetOffset: Offset) = coroutineScope {
        animScale.snapTo(scale)
        animOffsetX.snapTo(offset.x)
        animOffsetY.snapTo(offset.y)
        val spec = tween<Float>(durationMillis = 220)
        launch { animScale.animateTo(targetScale, spec) { scale = value } }
        launch { animOffsetX.animateTo(targetOffset.x, spec) { offset = offset.copy(x = value) } }
        launch { animOffsetY.animateTo(targetOffset.y, spec) { offset = offset.copy(y = value) } }
    }

    /** 손가락이 닿으면 돌고 있던 애니메이션을 멈춘다. */
    suspend fun stopAnimation() {
        animScale.stop()
        animOffsetX.stop()
        animOffsetY.stop()
    }

    private fun clampNow() {
        offset = Offset(
            ZoomMath.clamp(offset.x, fitted.width, viewport.width, scale),
            ZoomMath.clamp(offset.y, fitted.height, viewport.height, scale),
        )
    }
}

@Composable
fun rememberZoomState(key: Any?): ZoomState = remember(key) { ZoomState() }
