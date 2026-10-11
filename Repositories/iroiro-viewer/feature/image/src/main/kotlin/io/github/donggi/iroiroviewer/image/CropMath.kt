package io.github.donggi.iroiroviewer.image

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * 잘라 낼 사각형의 셈 — 손잡이를 잡고, 끌고, 비율을 맞추고, 화소로 옮긴다. 안드로이드를 모른다(JVM 시험이 지킨다 — 제스처를
 * 화면 캡처로 판정하려 들지 않는다, 함정 표).
 *
 * 좌표는 **원본의 화면 방향 화소**(실수)다. 화면 좌표와의 옮김은 부르는 쪽이 배율 하나로 한다 — 그래서 끄는 양도 원본 화소로 받는다.
 */
internal object CropMath {

    data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
        val centerX: Float get() = (left + right) / 2
        val centerY: Float get() = (top + bottom) / 2
    }

    /** 잡은 자리. 모서리·변·안쪽(옮기기). */
    enum class Grip { MOVE, LEFT, TOP, RIGHT, BOTTOM, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    fun full(w: Float, h: Float) = Box(0f, 0f, w, h)

    /**
     * ([x], [y]) 에서 잡히는 손잡이. **모서리가 먼저**, 다음이 변, 안쪽이면 옮기기, 밖이면 null. [reach] 는 손가락이 닿는 거리(원본 화소).
     *
     * 사각형이 손가락보다 좁으면 왼쪽 변과 오른쪽 변이 둘 다 '가깝다' — 더 가까운 쪽을 잡는다. 그러지 않으면 언제나 왼쪽이 이겨 좁은
     * 사각형을 오른쪽으로 넓힐 수 없다.
     *
     * **손가락이 그 축의 범위 안이면 변의 손잡이를 변 길이의 넷째 몫까지만 넓힌다.** 한 변이 손가락 두 개(2 × [reach])보다 짧으면 안쪽의
     * 모든 점이 어느 변의 손잡이 안이라 '옮기기' 가 나오지 않았다 — 얼굴 하나만 한 사각형의 가운데를 끌면 모서리가 끌려 크기가 바뀌었다.
     * 같은 까닭으로 작은 사각형의 오른쪽 바깥 가운데를 잡으면 모서리가 잡혔다. 범위 밖(사각형 바깥쪽)의 손잡이는 그대로다.
     */
    fun grip(b: Box, x: Float, y: Float, reach: Float): Grip? {
        val withinX = x >= b.left - reach && x <= b.right + reach
        val withinY = y >= b.top - reach && y <= b.bottom + reach
        if (!withinX || !withinY) return null
        val rx = if (x > b.left && x < b.right) minOf(reach, b.width / 4) else reach
        val ry = if (y > b.top && y < b.bottom) minOf(reach, b.height / 4) else reach
        val dl = abs(x - b.left)
        val dr = abs(x - b.right)
        val dt = abs(y - b.top)
        val db = abs(y - b.bottom)
        val horizontal = when {
            dl <= rx && dr <= rx -> if (dl <= dr) Side.START else Side.END
            dl <= rx -> Side.START
            dr <= rx -> Side.END
            else -> null
        }
        val vertical = when {
            dt <= ry && db <= ry -> if (dt <= db) Side.START else Side.END
            dt <= ry -> Side.START
            db <= ry -> Side.END
            else -> null
        }
        return when {
            horizontal == Side.START && vertical == Side.START -> Grip.TOP_LEFT
            horizontal == Side.END && vertical == Side.START -> Grip.TOP_RIGHT
            horizontal == Side.START && vertical == Side.END -> Grip.BOTTOM_LEFT
            horizontal == Side.END && vertical == Side.END -> Grip.BOTTOM_RIGHT
            horizontal == Side.START -> Grip.LEFT
            horizontal == Side.END -> Grip.RIGHT
            vertical == Side.START -> Grip.TOP
            vertical == Side.END -> Grip.BOTTOM
            x > b.left && x < b.right && y > b.top && y < b.bottom -> Grip.MOVE
            else -> null
        }
    }

    private enum class Side { START, END }

    /**
     * 잡은 순간의 사각형 [start] 를 [grip] 으로 ([dx], [dy]) 만큼 끈 결과. 그림([w]×[h]) 밖으로 나가지 않고, 한 변이 [min] 보다 작아지지
     * 않는다. [ratio](폭/높이)가 있으면 그 비율을 지킨다 — 모서리는 맞은편 모서리를 붙박고, 변은 가로지르는 축의 가운데를 지키며
     * 넓힌다(그림 가장자리에 닿으면 밀려난다).
     */
    fun drag(start: Box, grip: Grip, dx: Float, dy: Float, w: Float, h: Float, ratio: Float?, min: Float): Box {
        // 받은 사각형부터 그림 안에 둔다 — 비율을 거쳐 지은 사각형은 그림을 0.0001 넘길 수 있다([fit]).
        val s = fit(start, w, h)
        // 그림이 최소 크기보다 작을 수 있다(아이콘 같은 그림) — 최소를 그림에 맞춘다.
        val m = minOf(min, w, h)
        if (grip == Grip.MOVE) {
            val l = clampTo(s.left + dx, 0f, w - s.width)
            val t = clampTo(s.top + dy, 0f, h - s.height)
            return fit(Box(l, t, l + s.width, t + s.height), w, h)
        }
        val out = if (ratio == null || ratio <= 0f) free(s, grip, dx, dy, w, h, m) else locked(s, grip, dx, dy, w, h, ratio, m)
        return fit(out, w, h)
    }

    private fun free(s: Box, g: Grip, dx: Float, dy: Float, w: Float, h: Float, m: Float): Box {
        // **이미 최소보다 작은 사각형은 그 크기까지만 지킨다**(축마다) — 비율을 바꿔 넓이를 지키면 한 변이 최소보다 짧아질 수 있고,
        // 그때 최소를 그대로 쓰면 잡을 수 있는 범위가 비어(`r - m < 0`) 셈이 죽었다.
        val mw = minOf(m, s.width)
        val mh = minOf(m, s.height)
        var l = s.left
        var t = s.top
        var r = s.right
        var b = s.bottom
        if (g == Grip.LEFT || g == Grip.TOP_LEFT || g == Grip.BOTTOM_LEFT) l = clampTo(s.left + dx, 0f, r - mw)
        if (g == Grip.RIGHT || g == Grip.TOP_RIGHT || g == Grip.BOTTOM_RIGHT) r = clampTo(s.right + dx, l + mw, w)
        if (g == Grip.TOP || g == Grip.TOP_LEFT || g == Grip.TOP_RIGHT) t = clampTo(s.top + dy, 0f, b - mh)
        if (g == Grip.BOTTOM || g == Grip.BOTTOM_LEFT || g == Grip.BOTTOM_RIGHT) b = clampTo(s.bottom + dy, t + mh, h)
        return Box(l, t, r, b)
    }

    private fun locked(s: Box, g: Grip, dx: Float, dy: Float, w: Float, h: Float, ratio: Float, m: Float): Box {
        // 한 변이 m 보다 작아지지 않는 가장 작은 폭.
        val minW = maxOf(m, m * ratio)
        when (g) {
            Grip.TOP_LEFT, Grip.TOP_RIGHT, Grip.BOTTOM_LEFT, Grip.BOTTOM_RIGHT -> {
                val east = g == Grip.TOP_RIGHT || g == Grip.BOTTOM_RIGHT
                val south = g == Grip.BOTTOM_LEFT || g == Grip.BOTTOM_RIGHT
                // 맞은편 모서리를 붙박는다.
                val ax = if (east) s.left else s.right
                val ay = if (south) s.top else s.bottom
                val px = (if (east) s.right else s.left) + dx
                val py = (if (south) s.bottom else s.top) + dy
                val wantW = (px - ax) * (if (east) 1 else -1)
                val wantH = (py - ay) * (if (south) 1 else -1)
                val availW = if (east) w - ax else ax
                val availH = if (south) h - ay else ay
                // 더 많이 끈 축을 따른다 — 손가락이 사각형 밖으로 나가면 모서리가 손가락을 따라온다.
                var width = maxOf(wantW, wantH * ratio)
                width = minOf(width, availW, availH * ratio)
                width = maxOf(width, minW)
                if (width > availW + EPS || width / ratio > availH + EPS) return s
                val height = width / ratio
                val l = if (east) ax else ax - width
                val t = if (south) ay else ay - height
                return Box(l, t, l + width, t + height)
            }
            Grip.LEFT, Grip.RIGHT -> {
                val east = g == Grip.RIGHT
                var width = s.width + if (east) dx else -dx
                val avail = if (east) w - s.left else s.right
                width = minOf(width, avail, h * ratio)
                width = maxOf(width, minW)
                if (width > avail + EPS || width / ratio > h + EPS) return s
                // 실수 셈은 높이를 그림보다 0.0001 크게 낼 수 있다(2560×1600 을 4:3 으로 끝까지 끌면 1600.0001) — 그 넘침이
                // 아래 범위를 비워(`h - height < 0`) 앱이 죽었다. 그림에 맞춰 자른다.
                val height = minOf(width / ratio, h)
                val l = if (east) s.left else s.right - width
                val t = clampTo(s.centerY - height / 2, 0f, h - height)
                return Box(l, t, l + width, t + height)
            }
            Grip.TOP, Grip.BOTTOM -> {
                val south = g == Grip.BOTTOM
                var height = s.height + if (south) dy else -dy
                val avail = if (south) h - s.top else s.bottom
                val minH = minW / ratio
                height = minOf(height, avail, w / ratio)
                height = maxOf(height, minH)
                if (height > avail + EPS || height * ratio > w + EPS) return s
                val width = minOf(height * ratio, w)
                val t = if (south) s.top else s.bottom - height
                val l = clampTo(s.centerX - width / 2, 0f, w - width)
                return Box(l, t, l + width, t + height)
            }
            Grip.MOVE -> return s
        }
    }

    /**
     * 비율을 바꿨다 — **넓이를 지키며** 그 비율로 다시 짓는다(가운데는 그대로, 그림 밖으로 나가면 안으로 민다). 넓이를 지키는 까닭은
     * 작게 골라 둔 자리에서 비율만 바꾼 사람이 갑자기 그림 전체를 덮는 사각형을 보지 않게 하려는 것이다. 그림에 들지 않으면 들어갈
     * 만큼 줄인다.
     */
    fun withRatio(b: Box, ratio: Float, w: Float, h: Float): Box {
        if (ratio <= 0f) return b
        var width = sqrt(b.width * b.height * ratio)
        var height = width / ratio
        if (width > w) {
            width = w
            height = width / ratio
        }
        if (height > h) {
            height = h
            width = height * ratio
        }
        width = minOf(width, w)
        height = minOf(height, h)
        val l = clampTo(b.centerX - width / 2, 0f, w - width)
        val t = clampTo(b.centerY - height / 2, 0f, h - height)
        return fit(Box(l, t, l + width, t + height), w, h)
    }

    /**
     * 정수 화소의 `[왼, 위, 오른, 아래]`(오른·아래는 포함하지 않는다). 반올림하고, 그림 안에 두고, 적어도 한 화소다.
     */
    fun toPixels(b: Box, w: Int, h: Int): IntArray {
        val l = round(b.left).coerceIn(0, w - 1)
        val t = round(b.top).coerceIn(0, h - 1)
        val r = round(b.right).coerceIn(l + 1, w)
        val bottom = round(b.bottom).coerceIn(t + 1, h)
        return intArrayOf(l, t, r, bottom)
    }

    /**
     * 사각형을 그림 안에 둔다 — 셈의 입구와 출구에서. 비율을 거친 실수 셈은 한 끝이 그림을 0.0001 넘길 수 있고, 그것이 다음 셈의
     * `coerceIn` 범위를 비우면 앱이 죽는다(`IllegalArgumentException`). 넘친 만큼만 깎으므로 비율은 눈에 띄게
     * 바뀌지 않는다.
     */
    internal fun fit(b: Box, w: Float, h: Float): Box {
        val l = b.left.coerceIn(0f, w)
        val t = b.top.coerceIn(0f, h)
        return Box(l, t, b.right.coerceIn(l, w), b.bottom.coerceIn(t, h))
    }

    /** `coerceIn` 과 같되 범위가 비면(위가 아래보다 작다 — 실수 셈의 찌꺼기) 던지지 않고 가운데를 준다. */
    private fun clampTo(v: Float, lo: Float, hi: Float): Float = if (hi < lo) (lo + hi) / 2 else v.coerceIn(lo, hi)

    /** 반올림 — `kotlin.math.round` 는 짝수 쪽으로 반올림한다(함정 표). */
    private fun round(v: Float): Int = floor(v + 0.5f).toInt()

    private const val EPS = 0.01f
}
