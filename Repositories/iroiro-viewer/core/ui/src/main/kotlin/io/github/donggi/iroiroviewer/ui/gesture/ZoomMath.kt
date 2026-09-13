package io.github.donggi.iroiroviewer.ui.gesture

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 확대·이동의 **계산만** 모은 곳. 안드로이드도 Compose 도 모른다.
 *
 * ## 왜 떼어 놓았는가
 *
 * 이미지 뷰어에서 가장 자주 망가지는 것은 제스처 인식이 아니라 **경계 계산**이다.
 * 확대한 채 끝까지 밀면 그림이 화면 밖으로 빠지거나, 확대 중심이 어긋나 손가락 사이가
 * 아니라 엉뚱한 곳이 커진다. 그것을 에뮬레이터에서 손으로 밀어 보며 고치는 것은 느리고,
 * 무엇보다 **고쳤는지 확인할 방법이 없다.**
 *
 * 여기 있는 것은 전부 순수 함수라 JVM 테스트가 초 단위로 돈다.
 *
 * ## 좌표 약속
 *
 * - `offset` 은 **화면 픽셀**이다. Compose `graphicsLayer` 의 `translationX/Y` 는
 *   `scaleX/Y` 의 영향을 받지 않으므로 배율로 나누거나 곱하지 않는다.
 * - 원점은 **뷰포트 중앙**이다. 배율 1, 오프셋 0 이면 그림이 가운데에 맞춰져 있다.
 */
object ZoomMath {

    /**
     * 이 배율에서 한쪽으로 밀 수 있는 최대 거리(화면 픽셀).
     *
     * 그림이 뷰포트보다 작으면 0 이다 — 밀 자리가 없다는 뜻이고, 그때 손가락은
     * 페이저에게 가야 한다.
     */
    fun maxOffset(contentSize: Float, viewportSize: Float, scale: Float): Float =
        max(0f, (contentSize * scale - viewportSize) / 2f)

    /** 오프셋을 밀 수 있는 범위 안으로 가둔다. */
    fun clamp(offset: Float, contentSize: Float, viewportSize: Float, scale: Float): Float {
        val limit = maxOffset(contentSize, viewportSize, scale)
        return offset.coerceIn(-limit, limit)
    }

    /**
     * **손가락 사이(또는 더블탭한 자리)를 제자리에 두고** 배율을 바꾼다.
     *
     * 이 보정이 없으면 확대할 때마다 그림이 가운데로 빨려 들어가, 보려던 부분이 화면
     * 밖으로 나간다. 사람은 '내가 짚은 곳이 커진다' 를 기대한다.
     *
     * @param pivot 뷰포트 중앙을 원점으로 한 확대 중심(화면 픽셀).
     */
    fun offsetAfterZoom(offset: Float, pivot: Float, oldScale: Float, newScale: Float): Float {
        if (oldScale <= 0f) return offset
        val k = newScale / oldScale
        return offset * k + pivot * (1f - k)
    }

    /**
     * 이 방향으로 아직 밀 자리가 남았는가.
     *
     * **방향을 보지 않고 남은 여유만 본다.** 인식기가 주는 것이 누적 이동이 아니라 이번
     * 프레임의 델타라, 방향으로 판정하면 손가락이 미세하게 떨릴 때마다 판정이 뒤집힌다.
     */
    fun canPan(maxOffsetX: Float, maxOffsetY: Float): Boolean =
        maxOffsetX > 0.5f || maxOffsetY > 0.5f

    /**
     * 더블탭이 갈 다음 배율.
     *
     * 1 → [zoomedScale] → 1 의 두 칸만 쓴다. 세 칸 이상 두면 사용자가 지금 어디에 있는지
     * 모른 채 두 번 누르게 된다. 이미 조금이라도 확대돼 있으면 원래대로 돌린다.
     */
    fun doubleTapTarget(current: Float, zoomedScale: Float): Float =
        if (current > 1.01f) 1f else zoomedScale

    /**
     * `ContentScale.Fit` 으로 그렸을 때 실제로 그려지는 크기.
     *
     * 최대 배율을 **뷰포트가 아니라 이 크기**로 재야 한다. 뷰포트 폭으로 재면 태블릿에서
     * 4:3 사진이 높이에 먼저 걸려, 폭에는 여유가 있는데도 상한이 낮게 잡힌다.
     */
    fun fittedSize(
        contentWidth: Int,
        contentHeight: Int,
        viewportWidth: Float,
        viewportHeight: Float,
    ): Pair<Float, Float> {
        if (contentWidth <= 0 || contentHeight <= 0) return 0f to 0f
        val k = min(viewportWidth / contentWidth, viewportHeight / contentHeight)
        return contentWidth * k to contentHeight * k
    }

    /**
     * 최대 배율. **원본이 가진 화소 이상으로는 키우지 않는다** — 그 위는 어차피 흐리다.
     *
     * 하한 [MIN_MAX_SCALE] 은 작은 그림에서도 두 배까지는 볼 수 있게 한다. 그래서
     * "확대해도 흐려지지 않는다"고 약속할 수 없고, 우리도 그렇게 적지 않는다.
     */
    fun maxScale(sourceWidth: Int, fittedWidth: Float): Float {
        if (fittedWidth <= 0f) return MIN_MAX_SCALE
        return (sourceWidth / fittedWidth).coerceIn(MIN_MAX_SCALE, MAX_MAX_SCALE)
    }

    /** 이 두 배율을 같은 것으로 볼 것인가. 부동소수 비교를 한 곳에 모은다. */
    fun sameScale(a: Float, b: Float): Boolean = abs(a - b) < 0.01f

    const val MIN_MAX_SCALE = 2f
    const val MAX_MAX_SCALE = 12f

    /** 더블탭이 가는 배율. 화면을 꽉 채운 사진에서 글자가 읽히기 시작하는 정도다. */
    const val DOUBLE_TAP_SCALE = 2.5f
}
