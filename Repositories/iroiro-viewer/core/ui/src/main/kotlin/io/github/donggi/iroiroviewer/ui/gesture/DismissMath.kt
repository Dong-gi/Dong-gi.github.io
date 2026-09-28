package io.github.donggi.iroiroviewer.ui.gesture

import kotlin.math.abs

/**
 * 아래로 끌어 닫기의 **계산만** 모은 곳. 안드로이드도 Compose 도 모른다.
 *
 * 6단계가 '14단계 이후' 로 미뤄 둔 것이다. 미룬 이유는 '제스처 중재가 한 겹 더 늘어난다' 였고,
 * 그 중재가 여기서 가장 깨지기 쉬운 곳이라 [ZoomMath] 처럼 떼어 JVM 시험이 답하게 한다 — 6단계는
 * 제스처를 화면 캡처로 판정하다 세 번 틀렸다.
 *
 * ## 이미 있는 제스처와 겹치지 않는 까닭
 *
 * 이미지 뷰어의 한 장에는 이미 인식기가 셋 있다 — 페이저의 가로 밀기, `transformable` 의 핀치·팬,
 * 탭·더블탭. 끌어 닫기는 **넷째를 형제로 얹지 않고** 계층으로 끼운다(페이저 ⊃ 끌어 닫기 ⊃ 한 장).
 * 그리고 [DismissArbiter] 가 **축을 한 번만** 정한다(10단계 재생 화면의 규칙):
 *
 * - 손가락 하나가 터치 슬롭을 넘는 순간, 아래쪽이 가로보다 크면 끌어 닫기다. 그 뒤로는 바뀌지 않는다.
 * - 가로가 크거나 위로 가면 **이 제스처 전체를** 놓는다 — 페이저가 받는다. 도중에 세로로 꺾어도
 *   다시 잡지 않는다(비스듬히 밀 때 장이 넘어가다 말고 끌려 내려오는 일이 없다).
 * - 두 번째 손가락이 먼저 오거나 아래층(`transformable`)이 이미 소비했으면 놓는다 — 핀치와 확대 상태의
 *   팬이다. **배율 1 에서만** 켜는 것은 부르는 쪽의 몫이다(확대 상태에서 세로로 끄는 것은 팬이다).
 * - 탭·더블탭은 슬롭을 넘지 않으므로 이 인식기가 아예 잡지 않는다.
 *
 * 우리 판정은 **거리**(√(dx²+dy²) ≥ 슬롭)로 한다. 거리는 가로 성분보다 작을 수 없으므로 페이저가
 * 가로 성분으로 재든 거리로 재든(같은 터치 슬롭이다) 우리 판정이 **같은 이벤트나 그보다 먼저** 온다.
 * 그리고 한 장이 페이저의 자식이라 Main 단계에서 같은 이벤트를 우리가 먼저 본다 — 우리가 소비하면
 * 페이저의 슬롭 대기는 소비된 이동을 보고 물러난다. 그래서 '누가 이기는가' 가 경주가 아니라 규칙이다.
 * (터치 기준이다. 마우스는 페이저의 슬롭이 더 작아 페이저가 먼저 가져갈 수 있다 — 이 앱의 입력이 아니다.)
 */
object DismissMath {

    enum class Axis {
        /** 아직 슬롭 안이다. */
        UNDECIDED,

        /** 아래로 끄는 중이다. 이 제스처가 끝날 때까지 우리 것이다. */
        DISMISS,

        /** 가로·위·두 손가락 — 이 제스처는 남의 것이다. */
        OTHER,
    }

    /**
     * 이 장에서 끌어 닫기를 **받아도 되는가.** 누를 때마다 묻는다(`dragToDismiss` 의 `enabled`).
     *
     * - **확대 상태면 받지 않는다.** 그때의 세로 끌기는 팬이다 — 확대해 둔 사진의 아래쪽을 보려다 화면이
     *   닫히면 되돌릴 길이 없다. (팬을 `transformable` 이 소비하면 [DismissArbiter] 도 물러나지만, 그것은
     *   **자기 슬롭을 넘긴 뒤에야** 소비한다. 우리가 축을 정하는 이벤트에서 그것이 아직 소비하지 않았을 수
     *   있다 — 둘의 슬롭 비교가 같은 이벤트에서 갈리는지는 계약이 아니다. 그 틈을 이 규칙이 막는다.)
     * - **페이저가 움직이는 동안은 받지 않는다.** 넘어가는 중인 페이저는 누르는 즉시 드래그를 잡는다.
     * - **자리 잡은 장이 아니면 받지 않는다.** 넘기는 도중 화면에 걸친 이웃 장이다.
     */
    fun canStart(settledHere: Boolean, pagerScrolling: Boolean, zoomed: Boolean): Boolean =
        settledHere && !pagerScrolling && !zoomed

    /** 누적 이동 `(dx, dy)` 로 축을 정한다. 슬롭 안이면 [Axis.UNDECIDED]. */
    fun decide(dx: Float, dy: Float, slop: Float): Axis {
        if (!dx.isFinite() || !dy.isFinite()) return Axis.OTHER
        if (dx * dx + dy * dy < slop * slop) return Axis.UNDECIDED
        // 정확히 45° 는 가로로 친다 — 넘기는 것이 닫는 것보다 되돌리기 쉽다.
        return if (dy > 0f && dy > abs(dx)) Axis.DISMISS else Axis.OTHER
    }

    /**
     * 끈 만큼의 진행(0~1). 뷰포트 높이의 [FADE_FRACTION] 을 내려오면 1 이다.
     * 위로는 끌리지 않으므로 음수는 0 이다.
     */
    fun progress(offsetY: Float, viewportHeight: Float): Float {
        if (viewportHeight <= 0f || offsetY <= 0f || !offsetY.isFinite()) return 0f
        return (offsetY / (viewportHeight * FADE_FRACTION)).coerceIn(0f, 1f)
    }

    /** 그림의 불투명도. 끝까지 끌어도 [MIN_CONTENT_ALPHA] 는 남긴다 — 무엇을 끌고 있는지 보여야 한다. */
    fun contentAlpha(progress: Float): Float = 1f - progress.coerceIn(0f, 1f) * (1f - MIN_CONTENT_ALPHA)

    /** 위·아래 막대의 불투명도. 끌기 시작하면 먼저 사라진다 — 그림만 남아야 '닫힌다' 가 읽힌다. */
    fun chromeAlpha(progress: Float): Float = 1f - (progress.coerceIn(0f, 1f) * CHROME_FADE_SPEED).coerceAtMost(1f)

    /**
     * 손을 뗐을 때 닫을 것인가.
     *
     * - 빠르게 아래로 튕겼으면(≥ [flingVelocity]) 거리와 상관없이 닫는다.
     * - 빠르게 **위로** 되던졌으면 문턱을 넘었어도 닫지 않는다 — 마음을 바꾼 것이다.
     * - 그 밖에는 뷰포트의 [DISTANCE_FRACTION] 을 넘게 내려왔는가.
     *
     * @param velocityY 화소/초, 아래가 양수.
     */
    fun shouldDismiss(offsetY: Float, velocityY: Float, viewportHeight: Float, flingVelocity: Float): Boolean {
        if (offsetY <= 0f || viewportHeight <= 0f) return false
        if (velocityY.isFinite()) {
            if (velocityY >= flingVelocity) return true
            if (velocityY <= -flingVelocity) return false
        }
        return offsetY >= viewportHeight * DISTANCE_FRACTION
    }

    /** 이만큼(뷰포트 높이의 비율) 내려오면 손을 뗄 때 닫힌다. 폰 세로에서 약 130dp 다. */
    const val DISTANCE_FRACTION = 0.15f

    /** 이만큼 내려오면 그림이 가장 옅어진다. */
    const val FADE_FRACTION = 0.5f

    const val MIN_CONTENT_ALPHA = 0.3f

    /** 막대는 그림보다 이 배수만큼 빨리 사라진다. */
    const val CHROME_FADE_SPEED = 3f

    /**
     * 튕김으로 치는 속도(dp/초). 천천히 끄는 손은 수백 dp/초이고 튕기는 손은 수천이다. 그 사이의 값이다.
     */
    const val FLING_DP_PER_SECOND = 1000f
}

/**
 * 한 제스처의 축을 **한 번만** 정한다. [DismissMath.decide] 에 '한 번 정하면 바뀌지 않는다' 를 더한 것이다.
 *
 * 인식기(`dragToDismiss`)는 이벤트마다 이것에 묻고, 답이 [DismissMath.Axis.UNDECIDED] 가 아니게 되는
 * 순간부터 그 답대로만 움직인다.
 */
class DismissArbiter(private val slop: Float) {

    var axis: DismissMath.Axis = DismissMath.Axis.UNDECIDED
        private set

    private var dx = 0f
    private var dy = 0f

    /**
     * @param deltaX·[deltaY] 이번 이벤트의 이동(소비 여부와 상관없는 실제 이동).
     * @param pointers 지금 눌려 있는 손가락 수.
     * @param consumedBelow 아래층(한 장의 `transformable`)이 이 이동을 이미 소비했는가.
     */
    fun onMove(deltaX: Float, deltaY: Float, pointers: Int, consumedBelow: Boolean): DismissMath.Axis {
        if (axis != DismissMath.Axis.UNDECIDED) return axis
        if (pointers != 1 || consumedBelow) {
            axis = DismissMath.Axis.OTHER
            return axis
        }
        dx += deltaX
        dy += deltaY
        axis = DismissMath.decide(dx, dy, slop)
        return axis
    }
}
