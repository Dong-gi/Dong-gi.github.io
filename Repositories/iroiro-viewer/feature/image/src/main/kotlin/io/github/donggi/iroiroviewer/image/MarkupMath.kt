package io.github.donggi.iroiroviewer.image

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 그리는 화면의 **변환** — 화면의 한 점과 그림 좌표(`MarkupModel.kt` 머리말) 사이. 바꾸는 식은 뷰어의 확대·이동(`ZoomState` —
 * `ZoomableImage` 의 `drawFitted`)과 같다: 그림을 뷰포트에 맞춰 가운데 두고(맞춤 크기 [fittedWidth]×[fittedHeight]), 뷰포트 가운데를
 * 중심으로 [scale] 배 키운 뒤 [offsetX]·[offsetY](화면 화소) 만큼 민다. 그리는 쪽(`MarkupScreen`)이 같은 식으로 그리므로 손가락이
 * 닿은 자리와 선이 그어지는 자리가 같다.
 *
 * 화면 좌표의 원점은 그리는 자리(뷰포트)의 왼쪽 위다.
 */
internal data class MarkupView(
    val viewportWidth: Float,
    val viewportHeight: Float,
    val fittedWidth: Float,
    val fittedHeight: Float,
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
) {
    /** 셈할 수 있는가 — 아직 재지 않았으면(크기 0) 손가락을 받지 않는다. */
    val valid: Boolean
        get() = fittedWidth > 0f && fittedHeight > 0f && scale > 0f && scale.isFinite() &&
            viewportWidth > 0f && viewportHeight > 0f && offsetX.isFinite() && offsetY.isFinite()

    /** 화면 → 그림 좌표. 그림 밖이어도 그대로 준다(획이 그림 밖으로 나갔다 들어올 수 있다 — 그리는 쪽이 그림으로 잘라 그린다). */
    fun imageX(screenX: Float): Float = ((screenX - viewportWidth / 2f - offsetX) / scale + fittedWidth / 2f) / fittedWidth

    fun imageY(screenY: Float): Float = ((screenY - viewportHeight / 2f - offsetY) / scale + fittedHeight / 2f) / fittedWidth

    /** 그림 좌표 → 화면. */
    fun screenX(imageX: Float): Float = viewportWidth / 2f + offsetX + scale * (imageX * fittedWidth - fittedWidth / 2f)

    fun screenY(imageY: Float): Float = viewportHeight / 2f + offsetY + scale * (imageY * fittedWidth - fittedHeight / 2f)

    /**
     * 화면에 **보이는** 길이(화소) → 그림 좌표. 확대한 채 그린 선의 굵기와 메모의 글자 크기가 이것을 지난다 — 확대한 만큼 그림에서는
     * 가늘다(보이는 굵기 그대로 남는다).
     */
    fun toImage(screenLength: Float): Float = screenLength / (fittedWidth * scale)

    /** 그림 좌표의 길이 → 화면에 보이는 길이(화소). */
    fun toScreen(imageLength: Float): Float = imageLength * fittedWidth * scale
}

/** 그리기·메모의 셈. 전부 순수 함수다(JVM 시험 — `MarkupMathTest`). 길이·거리는 모두 그림 좌표(두 축이 같은 단위)다. */
internal object MarkupMath {

    /** 직선이 가로·세로에 붙는 각도(도). 삼성 갤러리·구글 포토의 곧은 선이 대략 이만큼에서 붙는다. */
    const val SNAP_DEGREES = 6f

    /**
     * 직선의 끝을 붙인다 — 시작([x0], [y0])에서 끝([x1], [y1])까지의 기울기가 가로에서 [toleranceDegrees] 안이면 가로로, 세로에서
     * 그 안이면 세로로 맞춘다(끝을 그 축에 투영한다 — 손가락이 간 거리를 그대로 둔다). 밑줄은 대개 가로이고 손으로 그은 선은 조금씩
     * 기운다. 두 축의 단위가 같아야 각도가 화면 그대로다 — 그림 좌표가 그렇다.
     *
     * @return 붙인 끝 `[x, y]`.
     */
    fun snapLine(x0: Float, y0: Float, x1: Float, y1: Float, toleranceDegrees: Float = SNAP_DEGREES): FloatArray {
        val dx = x1 - x0
        val dy = y1 - y0
        if (dx == 0f && dy == 0f) return floatArrayOf(x1, y1)
        val degrees = Math.toDegrees(atan2(abs(dy), abs(dx)).toDouble()).toFloat()
        return when {
            degrees <= toleranceDegrees -> floatArrayOf(x1, y0)
            degrees >= 90f - toleranceDegrees -> floatArrayOf(x0, y1)
            else -> floatArrayOf(x1, y1)
        }
    }

    /** 새 점을 받을 만큼 움직였는가 — 앞 점에서 [minStep] 이상. 같은 자리의 이벤트·손떨림으로 점이 불어나지 않게 한다. */
    fun farEnough(lastX: Float, lastY: Float, x: Float, y: Float, minStep: Float): Boolean {
        val dx = x - lastX
        val dy = y - lastY
        return dx * dx + dy * dy >= minStep * minStep
    }

    /** 점([px], [py])에서 선분 (a→b) 까지의 거리. 선분이 점으로 줄어들었으면 그 점까지. */
    fun distanceToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val vx = bx - ax
        val vy = by - ay
        val len2 = vx * vx + vy * vy
        val t = if (len2 <= 0f) 0f else (((px - ax) * vx + (py - ay) * vy) / len2).coerceIn(0f, 1f)
        val cx = ax + t * vx - px
        val cy = ay + t * vy - py
        return sqrt(cx * cx + cy * cy)
    }

    /** 두 선분 (a→b)·(c→d) 사이의 가장 가까운 거리. 엇갈리면 0 이다. */
    fun segmentDistance(
        ax: Float, ay: Float, bx: Float, by: Float,
        cx: Float, cy: Float, dx: Float, dy: Float,
    ): Float {
        if (segmentsCross(ax, ay, bx, by, cx, cy, dx, dy)) return 0f
        return min(
            min(distanceToSegment(ax, ay, cx, cy, dx, dy), distanceToSegment(bx, by, cx, cy, dx, dy)),
            min(distanceToSegment(cx, cy, ax, ay, bx, by), distanceToSegment(dx, dy, ax, ay, bx, by)),
        )
    }

    private fun segmentsCross(
        ax: Float, ay: Float, bx: Float, by: Float,
        cx: Float, cy: Float, dx: Float, dy: Float,
    ): Boolean {
        val d1 = cross(cx, cy, dx, dy, ax, ay)
        val d2 = cross(cx, cy, dx, dy, bx, by)
        val d3 = cross(ax, ay, bx, by, cx, cy)
        val d4 = cross(ax, ay, bx, by, dx, dy)
        // 한 점에서 닿기만 하는 경우(0)는 거리 셈이 0 을 준다 — 여기서는 확실히 엇갈린 것만 본다.
        return ((d1 > 0f && d2 < 0f) || (d1 < 0f && d2 > 0f)) && ((d3 > 0f && d4 < 0f) || (d3 < 0f && d4 > 0f))
    }

    /** (b - a) × (p - a). */
    private fun cross(ax: Float, ay: Float, bx: Float, by: Float, px: Float, py: Float): Float =
        (bx - ax) * (py - ay) - (by - ay) * (px - ax)

    /**
     * 지우개가 (a→b) 를 지나며 이 획을 건드렸는가 — 지우개의 반지름 [radius] 와 획의 반 굵기 안으로 들어왔다. 손가락이 빨리 움직이면
     * 이벤트 사이가 멀어 점만 보면 가는 획을 건너뛴다 — 그래서 손가락이 지난 **선분**으로 본다. 처음 닿은 순간은 a == b 다.
     */
    fun strokeHit(stroke: Stroke, ax: Float, ay: Float, bx: Float, by: Float, radius: Float): Boolean {
        val p = stroke.points
        val n = stroke.pointCount
        if (n == 0) return false
        val reach = radius + stroke.width / 2f
        // 빨리 거른다 — 획의 둘레 상자(굵기까지)에 지나간 선분의 둘레 상자가 닿지 않으면 볼 것이 없다.
        var minX = p[0]
        var maxX = p[0]
        var minY = p[1]
        var maxY = p[1]
        for (i in 1 until n) {
            minX = min(minX, p[2 * i])
            maxX = max(maxX, p[2 * i])
            minY = min(minY, p[2 * i + 1])
            maxY = max(maxY, p[2 * i + 1])
        }
        if (max(ax, bx) < minX - reach || min(ax, bx) > maxX + reach) return false
        if (max(ay, by) < minY - reach || min(ay, by) > maxY + reach) return false
        if (n == 1) return distanceToSegment(p[0], p[1], ax, ay, bx, by) <= reach
        for (i in 0 until n - 1) {
            val d = segmentDistance(p[2 * i], p[2 * i + 1], p[2 * i + 2], p[2 * i + 3], ax, ay, bx, by)
            if (d <= reach) return true
        }
        return false
    }

    /**
     * 지우개가 (a→b) 를 지나며 사각형([left], [top], [right], [bottom] — 글 메모의 상자)을 건드렸는가. 사각형을 [radius] 만큼 넓혀
     * 본다(모서리는 둥글리지 않는다 — 손가락의 폭에서 차이가 나지 않는다).
     */
    fun rectHit(left: Float, top: Float, right: Float, bottom: Float, ax: Float, ay: Float, bx: Float, by: Float, radius: Float): Boolean {
        val l = left - radius
        val t = top - radius
        val r = right + radius
        val b = bottom + radius
        if (inside(ax, ay, l, t, r, b) || inside(bx, by, l, t, r, b)) return true
        // 두 끝이 다 밖인데 선분이 사각형을 가로지른 경우 — 네 변 가운데 하나와 엇갈린다.
        return segmentsCross(ax, ay, bx, by, l, t, r, t) || segmentsCross(ax, ay, bx, by, r, t, r, b) ||
            segmentsCross(ax, ay, bx, by, r, b, l, b) || segmentsCross(ax, ay, bx, by, l, b, l, t)
    }

    /** 점이 사각형 안(테두리 포함)인가. */
    fun inside(x: Float, y: Float, left: Float, top: Float, right: Float, bottom: Float): Boolean =
        x in left..right && y in top..bottom

    /**
     * 글 상자를 그림 안에 둔다 — 왼쪽 위([x], [y])를 옮겨 상자(폭 [boxWidth], 높이 [boxHeight])가 그림(폭 1, 높이 [aspect]) 밖으로
     * 나가지 않게. 저장한 그림에서 메모가 잘려 나가면 안 된다(화면은 그림 밖을 검게 비우므로 거기 놓인 메모는 저장되지 않는다). 상자가
     * 그림보다 크면 왼쪽(위)에 붙인다 — 글의 처음이 보이는 쪽이다.
     *
     * @return 옮긴 왼쪽 위 `[x, y]`.
     */
    fun clampBox(x: Float, y: Float, boxWidth: Float, boxHeight: Float, aspect: Float): FloatArray {
        val cx = if (boxWidth >= 1f) 0f else x.coerceIn(0f, 1f - boxWidth)
        val cy = if (boxHeight >= aspect) 0f else y.coerceIn(0f, aspect - boxHeight)
        return floatArrayOf(cx, cy)
    }

    /**
     * 메모가 줄을 바꾸는 폭(그림 좌표) — 글자 크기의 [WRAP_EM] 배이되 그림 폭의 [WRAP_MAX] 를 넘지 않는다. 글자 크기에 비례하게 두어야
     * 확대한 채 단 작은 메모가 그림 폭 끝까지 한 줄로 늘어지지 않는다. 바꾼 줄은 그리는 쪽이 기준 단위로 한 번 짜므로 화면과 저장이
     * 같은 자리에서 꺾인다(`MarkupPainter`).
     */
    fun noteWrapWidth(size: Float): Float = min(size * WRAP_EM, WRAP_MAX).coerceAtLeast(size)

    /** 메모 한 줄에 드는 글자 폭(글자 크기의 몇 배) — 한글 스무 자 남짓. */
    const val WRAP_EM = 20f

    /** 메모 한 줄의 가장 넓은 폭(그림 폭의 몫). */
    const val WRAP_MAX = 0.9f

    /**
     * 글이 밝은 빛깔인가 — 바탕 상자를 어둡게 깔지 밝게 깔지 정한다(흰 글에는 어두운 상자, 검은 글에는 밝은 상자). 사람 눈의 밝기
     * 가중치(ITU-R BT.601)로 잰다.
     */
    fun isLight(argb: Int): Boolean {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return 0.299f * r + 0.587f * g + 0.114f * b >= LIGHT_THRESHOLD
    }

    private const val LIGHT_THRESHOLD = 150f
}
