package io.github.donggi.iroiroviewer.ui.gesture

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
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

    /** 아직 밀 자리가 남았는가(방향을 보지 않는다). 한 손가락 끌기를 받을지는 [claimsDrag] 가 정한다. */
    val canPan: Boolean get() = ZoomMath.canPan(maxOffsetX, maxOffsetY)

    /** 부모가 가로로 넘기는가(페이저 — 기본). [ZoomMath.claimsDrag] 가 끌기의 주된 방향과 견준다. */
    var pagesHorizontally: Boolean = true

    // ---- 끌기 하나의 결정 ----
    // 인식기(`trackZoomDrags`)가 `Initial` 단계에서 적는다 — 같은 이벤트를 `transformable` 이 `Main` 단계에서 받기 전에.
    // 컴포즈 상태가 아니다(그리기와 상관없는 값이라 다시 그릴 까닭이 없다).
    private var dragFrom = Offset.Unspecified
    private var dragTo = Offset.Zero
    private var multiTouch = false
    private var dragClaim: Boolean? = null

    /** 손가락이 처음 닿았다 — 앞 끌기의 결정을 버린다. */
    fun onDragDown(at: Offset) {
        dragFrom = at
        dragTo = at
        multiTouch = false
        dragClaim = null
    }

    /** 끌기가 움직였다. [pointers] 는 눌린 손가락 수다. */
    fun onDragMove(at: Offset, pointers: Int) {
        dragTo = at
        if (pointers > 1) multiTouch = true
    }

    /**
     * `transformable` 의 `canPan` — 이 끌기를 그림이 받는가.
     *
     * **한 손가락 끌기는 처음 물을 때 한 번 정하고 손을 뗄 때까지 그 답을 지킨다**([ZoomMath.claimsDrag]). `transformable` 은 손가락이
     * 슬롭을 넘는 순간 처음 묻고, 그 뒤로는 델타마다 묻는다 — 도중에 답이 바뀌면 그림이 밀리다 말고 멈추거나, 가장자리에 닿은 순간 같은
     * 끌기로 장이 넘어간다. 거짓으로 정했으면 그 끌기는 페이저가 가져간다.
     *
     * **두 손가락은 예전처럼 밀 자리만 본다** — 핀치로 키운 그림을 같은 몸짓 안에서 옮길 수 있어야 한다.
     */
    fun claimsDrag(): Boolean {
        if (multiTouch) return canPan
        dragClaim?.let { return it }
        val moved = if (dragFrom.isSpecified) dragTo - dragFrom else Offset.Zero
        return ZoomMath.claimsDrag(
            dx = moved.x,
            dy = moved.y,
            offsetX = offset.x,
            offsetY = offset.y,
            maxOffsetX = maxOffsetX,
            maxOffsetY = maxOffsetY,
            pagesHorizontally = pagesHorizontally,
        ).also { dragClaim = it }
    }

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

/**
 * 끌기 하나를 [state] 에 알린다 — 손가락이 닿은 자리와 지금 자리([ZoomState.claimsDrag] 가 방향을 잰다). **`Initial` 단계에서 보고
 * 아무것도 소비하지 않는다** — 같은 이벤트를 `transformable` 이 `Main` 단계에서 받아 `canPan` 을 묻기 전에 적어 두려는 것이다.
 * `transformable` 과 같은 상자에, 그보다 앞에 단다.
 */
fun Modifier.trackZoomDrags(state: ZoomState): Modifier = pointerInput(state) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        state.onDragDown(down.position)
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressed = event.changes.count { it.pressed }
            if (pressed == 0) break
            val change = event.changes.firstOrNull { it.id == down.id && it.pressed } ?: event.changes.first { it.pressed }
            state.onDragMove(change.position, pressed)
        }
    }
}
