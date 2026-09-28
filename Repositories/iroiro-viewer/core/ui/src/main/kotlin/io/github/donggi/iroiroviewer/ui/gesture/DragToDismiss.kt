package io.github.donggi.iroiroviewer.ui.gesture

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * 아래로 끌어 닫기의 상태. **화면 하나에 하나**다 — 끄는 것은 한 장이지만 막대의 불투명도도 따라가야
 * 해서 화면이 든다. 계산은 [DismissMath] 에 있고 여기는 값과 애니메이션만 쥔다([ZoomState] 와 같은 갈래).
 */
@Stable
class DismissState {

    /** 그림이 내려온 거리(화면 화소). 0 이상이다 — 위로는 끌리지 않는다. */
    var offsetY by mutableFloatStateOf(0f)
        private set

    /** 끄는 자리의 높이. 인식기가 제스처마다 알려 준다. */
    var viewportHeight by mutableFloatStateOf(0f)
        internal set

    /** 문턱을 넘겨 손을 뗐다 — 닫히는 중이다. 이때부터는 새 제스처를 받지 않는다. */
    var closing by mutableStateOf(false)
        private set

    /**
     * 지금 손가락이 그림을 끌고 있는가. 축을 '닫기' 로 정한 순간 참이 되고 손을 떼면 거짓이다. 화면이
     * `Iro.d` 로 찍는다 — 제스처 중재는 화면 캡처가 아니라 로그로 판정한다(6단계가 세 번 틀렸다).
     */
    var dragging by mutableStateOf(false)
        private set

    /**
     * 손을 뗀 뒤 돌고 있는 애니메이션(제자리로 / 화면 밖으로). **상태가 든다** — 인식기는 장마다 하나라,
     * 인식기의 지역 변수로 두면 다른 장에서 새로 끌기 시작해도 앞 장의 되돌리기를 멈추지 못해 두 곳이 같은
     * [offsetY] 를 번갈아 쓴다.
     */
    private var settleJob: Job? = null

    internal fun startDrag() {
        // 되돌리던 것을 멈추고 그 자리에서 이어 끈다. 표에서 먼저 빼고 멈춘다 — 멈춘 쪽이 '빼앗겼다' 를 안다.
        val running = settleJob
        settleJob = null
        running?.cancel()
        dragging = true
    }

    /**
     * 손을 뗀 뒤의 애니메이션을 [scope] 에서 돌린다([settle]).
     *
     * **끌던 장이 컴포지션에서 빠지면 [scope] 가 취소되어 애니메이션이 도중에 멈춘다**(페이저를 빠르게 두 장
     * 넘기면 그 장이 빠진다). 멈춘 자리에 두면 화면 하나가 이 상태를 나눠 쓰므로 **모든 장이 반쯤 내려가고
     * 옅어진 채** 남고, 닫히던 중이었으면 새 제스처도 받지 않는다([closing]). 그래서 빼앗긴 것이 아니라
     * 끊긴 것이면 제자리로 되돌린다.
     */
    internal fun settleIn(scope: CoroutineScope, dismiss: Boolean, onDismissed: () -> Unit) {
        val running = settleJob
        settleJob = null
        running?.cancel()
        settleJob = scope.launch {
            val me = coroutineContext[Job]
            try {
                settle(dismiss, onDismissed)
            } catch (e: CancellationException) {
                if (settleJob === me) reset()
                throw e
            } finally {
                if (settleJob === me) settleJob = null
            }
        }
    }

    val progress: Float get() = DismissMath.progress(offsetY, viewportHeight)

    /** 끄는 그림의 불투명도. */
    val contentAlpha: Float get() = DismissMath.contentAlpha(progress)

    /** 위·아래 막대의 불투명도. */
    val chromeAlpha: Float get() = DismissMath.chromeAlpha(progress)

    internal fun dragBy(dy: Float) {
        offsetY = (offsetY + dy).coerceAtLeast(0f)
    }

    /**
     * 손을 뗀 뒤. 닫으면 화면 아래로 내보낸 **뒤에** [onDismissed] 를 부른다 — 먼저 부르면 화면이
     * 바뀌는 순간 그림이 끄던 자리에서 사라져 '닫혔다' 가 아니라 '끊겼다' 로 보인다.
     */
    internal suspend fun settle(dismiss: Boolean, onDismissed: () -> Unit) {
        dragging = false
        val anim = Animatable(offsetY)
        if (dismiss) {
            closing = true
            anim.animateTo(viewportHeight.coerceAtLeast(offsetY), tween(DISMISS_MS)) { offsetY = value }
            onDismissed()
        } else {
            anim.animateTo(0f, spring()) { offsetY = value }
        }
    }

    /** 제자리. 끌던 장을 떠났거나 제스처가 도중에 끊겼을 때. */
    fun reset() {
        offsetY = 0f
        closing = false
        dragging = false
    }

    private companion object {
        const val DISMISS_MS = 160
    }
}

/**
 * 아래로 끌어 닫기 인식기.
 *
 * ## 놓는 자리 — 끄는 그림의 **부모**, 페이저의 **자식**
 *
 * ```
 * HorizontalPager                     ← 가로 밀기. 우리가 소비하면 물러난다
 *   Box.dragToDismiss                 ← 이것. 좌표계가 움직이지 않는다
 *     Box.graphicsLayer(translationY) ← 끌리는 것은 여기부터
 *       ZoomableImage(탭 / transformable)
 * ```
 *
 * **이 수정자를 단 노드를 옮기지 마라.** `graphicsLayer` 로 자기 자신을 내리면 포인터 좌표가 그 변환을
 * 따라가, 손가락이 내려간 만큼 좌표계도 내려가서 이동이 0 으로 읽힌다(그림이 손가락을 따라오다 멈춘다).
 * 옮기는 층은 반드시 **안쪽 자식**이어야 한다.
 *
 * 한 장의 인식기들(탭·`transformable`)이 **자식**이라 Main 단계에서 먼저 본다. `transformable` 이 핀치나
 * 확대 상태의 팬을 소비했으면 우리는 그것을 보고 물러나고([DismissArbiter] 의 `consumedBelow`), 우리가
 * 끌기를 잡아 소비하면 탭 인식기는 Final 단계에서 소비를 보고 두드림을 취소한다 — 끌다 놓았는데 막대가
 * 토글되는 일이 없다. 첫 누름은 탭 인식기가 소비하므로 `requireUnconsumed = false` 로 받는다.
 *
 * **두 람다는 처음 붙을 때 한 번 잡힌다**(`pointerInput(state)` 의 키가 상태뿐이다 — 람다를 키에 넣으면
 * 재구성마다 인식기가 다시 서서 끄던 손가락을 놓친다). 그래서 바뀌는 값(지금 장의 확대 상태, 닫는 곳)은
 * 부르는 쪽이 `rememberUpdatedState` 로 들고 람다 안에서 읽어야 한다. 옛 값을 쥔 람다는 오류 없이
 * 엉뚱한 장의 배율을 본다.
 *
 * @param enabled 누를 때마다 묻는다. 배율 1 이고 페이저가 멈춰 있을 때만 참이어야 한다 — 확대 상태의
 *   세로 끌기는 팬이고, 넘어가는 중인 페이저는 누르는 즉시 드래그를 잡는다.
 * @param onDismiss 문턱을 넘겨 손을 뗐다. 뒤로가기와 **같은 곳**으로 가야 한다.
 */
fun Modifier.dragToDismiss(
    state: DismissState,
    enabled: () -> Boolean,
    onDismiss: () -> Unit,
): Modifier = pointerInput(state) {
    val fling = DismissMath.FLING_DP_PER_SECOND.dp.toPx()
    coroutineScope {
        // 제자리로 돌아가는 중에 다시 잡으면 그 애니메이션을 멈춘다(`DismissState.startDrag`). 멈추지 않으면
        // 애니메이션과 손가락이 같은 값을 번갈아 써서 그림이 떨린다(더블탭 애니메이션과 같은 이유 — `ZoomState`).
        // 애니메이션은 상태가 든다 — 다른 장의 인식기가 잡아도 멈춰야 한다.
        fun settleLater(dismiss: Boolean) = state.settleIn(this, dismiss, onDismiss)
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (state.closing || !enabled()) return@awaitEachGesture
            state.viewportHeight = size.height.toFloat()
            val arbiter = DismissArbiter(viewConfiguration.touchSlop)
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)

            // 1) 축을 정한다 — 한 번만.
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                if (!change.pressed) return@awaitEachGesture // 슬롭 안에서 뗐다 — 두드림이다
                val move = change.positionChangeIgnoreConsumed()
                val axis = arbiter.onMove(
                    move.x, move.y,
                    pointers = event.changes.count { it.pressed },
                    consumedBelow = change.isConsumed,
                )
                tracker.addPosition(change.uptimeMillis, change.position)
                when (axis) {
                    DismissMath.Axis.UNDECIDED -> continue
                    DismissMath.Axis.OTHER -> return@awaitEachGesture
                    DismissMath.Axis.DISMISS -> {
                        change.consume()
                        state.startDrag()
                        break
                    }
                }
            }

            // 2) 끄는 동안. 제스처가 도중에 끊기면(화면이 바뀌었다) 그림을 제자리로 돌린다 —
            //    화면 하나가 상태 하나를 나눠 쓰므로 남겨 두면 다음 장까지 내려간 채로 그려진다.
            var handedOver = false
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || event.changes.count { it.pressed } > 1) {
                        // 손가락이 사라졌거나 두 번째 손가락이 왔다(핀치로 바꾸려는 것) — 닫지 않는다.
                        handedOver = true
                        settleLater(dismiss = false)
                        return@awaitEachGesture
                    }
                    if (!change.pressed) {
                        val velocity = tracker.calculateVelocity().y
                        val dismiss = DismissMath.shouldDismiss(state.offsetY, velocity, state.viewportHeight, fling)
                        change.consume()
                        handedOver = true
                        settleLater(dismiss)
                        return@awaitEachGesture
                    }
                    tracker.addPosition(change.uptimeMillis, change.position)
                    state.dragBy(change.positionChangeIgnoreConsumed().y)
                    change.consume()
                }
            } finally {
                if (!handedOver) state.reset()
            }
        }
    }
}
