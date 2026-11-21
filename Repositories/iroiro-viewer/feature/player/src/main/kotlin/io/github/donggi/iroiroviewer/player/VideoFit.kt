package io.github.donggi.iroiroviewer.player

/**
 * 영상을 화면에 놓는 방식. 단추를 누를 때마다 이 차례로 돈다(MX Player·VLC 의 관행).
 *
 * | 방식 | 영상 상자 |
 * |---|---|
 * | 맞춤 | 영상 비율 그대로 화면 안에(처음부터의 동작) |
 * | 채움 | 영상 비율 그대로 화면을 덮는다 — 넘친 쪽은 잘린다 |
 * | 늘림 | 화면 그대로 — 비율이 틀어진다 |
 * | 16:9·4:3 | 그 비율로 화면 안에 — 비율이 잘못 적힌 영상을 바로잡는 데 쓴다 |
 */
enum class AspectMode { FIT, FILL, STRETCH, R16_9, R4_3 }

/** 영상 상자의 크기와 두 손가락 확대를 정하는 셈. android·Compose 를 쓰지 않는다 — JVM 시험이 답한다. */
internal object VideoFit {

    data class Size(val width: Float, val height: Float)

    fun next(mode: AspectMode): AspectMode = AspectMode.entries[(mode.ordinal + 1) % AspectMode.entries.size]

    /**
     * 화면([containerW]×[containerH]) 안의 영상 상자. [videoAspect] 는 가로/세로(화소 비율을 곱한 값), 0 이면 아직 모른다 —
     * 그때는 화면 전체(처음부터의 동작). **채움만 화면보다 클 수 있다.**
     */
    fun boxOf(mode: AspectMode, containerW: Float, containerH: Float, videoAspect: Float): Size {
        if (containerW <= 0f || containerH <= 0f) return Size(containerW, containerH)
        val aspect = when (mode) {
            AspectMode.FIT, AspectMode.FILL -> videoAspect
            AspectMode.STRETCH -> return Size(containerW, containerH)
            AspectMode.R16_9 -> 16f / 9f
            AspectMode.R4_3 -> 4f / 3f
        }
        if (aspect <= 0f || aspect.isNaN() || aspect.isInfinite()) return Size(containerW, containerH)
        val byWidth = Size(containerW, containerW / aspect)
        val byHeight = Size(containerH * aspect, containerH)
        val widthFits = byWidth.height <= containerH
        return if (mode == AspectMode.FILL) {
            if (widthFits) byHeight else byWidth
        } else {
            if (widthFits) byWidth else byHeight
        }
    }

    /** 화면 안에 보이는 몫 — 자막·PiP 출발 사각형이 쓰는 상자. 채움이 넘친 쪽을 자른 것이다. */
    fun visibleOf(box: Size, containerW: Float, containerH: Float): Size =
        Size(minOf(box.width, containerW), minOf(box.height, containerH))

    /** 두 손가락 확대·이동. [offsetX]·[offsetY] 는 상자 가운데로부터 옮긴 화소. */
    data class Zoom(val scale: Float = 1f, val offsetX: Float = 0f, val offsetY: Float = 0f) {
        val isIdentity: Boolean get() = scale == 1f && offsetX == 0f && offsetY == 0f
    }

    val NO_ZOOM = Zoom()
    const val MAX_ZOOM = 4f

    /** 이만큼 아래로 줄이면 원래대로 붙인다 — 손으로 정확히 1배에 맞추기는 어렵다. */
    const val SNAP_ZOOM = 1.05f

    /**
     * [zoom] 에 핀치 한 프레임(배율 [factor], 이동 [panX]·[panY], 두 손가락 가운데 [centroidX]·[centroidY] — 화면 가운데로부터)을 얹는다.
     * 손가락 밑의 점이 손가락을 따라가게 가운데를 축으로 키우고, 배율은 1~[MAX_ZOOM], 이동은 키운 상자의 가장자리가 화면 안으로 들어오지
     * 않을 만큼만 — 영상 밖의 검은 자리를 끌어오지 않는다(키운 상자가 화면보다 좁은 축은 가운데에 둔다).
     */
    fun apply(
        zoom: Zoom,
        factor: Float,
        panX: Float,
        panY: Float,
        centroidX: Float,
        centroidY: Float,
        box: Size,
        containerW: Float,
        containerH: Float,
    ): Zoom {
        val f = if (factor.isNaN() || factor <= 0f) 1f else factor
        val scale = (zoom.scale * f).coerceIn(1f, MAX_ZOOM)
        val k = scale / zoom.scale
        // 화면의 점 p = q·s + o. 손가락 밑(c)의 q 를 그대로 두면 o' = c − (c − o)·s'/s.
        var ox = centroidX - (centroidX - zoom.offsetX) * k + panX
        var oy = centroidY - (centroidY - zoom.offsetY) * k + panY
        ox = ox.coerceIn(-limit(box.width * scale, containerW), limit(box.width * scale, containerW))
        oy = oy.coerceIn(-limit(box.height * scale, containerH), limit(box.height * scale, containerH))
        return Zoom(scale, ox, oy)
    }

    /**
     * 화면이나 상자의 크기가 바뀌었을 때(회전 — 이 화면은 회전에 다시 서지 않는다, 영상 비율이 늦게 알려짐) 이동을 새 범위로 누른다.
     * 누르지 않으면 가로에서 구석까지 민 이동이 세로 화면에 그대로 남아 검은 띠가 생긴다.
     */
    fun clamp(zoom: Zoom, box: Size, containerW: Float, containerH: Float): Zoom {
        if (zoom.isIdentity) return zoom
        val ox = zoom.offsetX.coerceIn(-limit(box.width * zoom.scale, containerW), limit(box.width * zoom.scale, containerW))
        val oy = zoom.offsetY.coerceIn(-limit(box.height * zoom.scale, containerH), limit(box.height * zoom.scale, containerH))
        return if (ox == zoom.offsetX && oy == zoom.offsetY) zoom else Zoom(zoom.scale, ox, oy)
    }

    /** 손을 뗄 때 — 거의 1배면 원래대로. */
    fun settle(zoom: Zoom): Zoom = if (zoom.scale < SNAP_ZOOM) NO_ZOOM else zoom

    private fun limit(scaled: Float, container: Float): Float = maxOf(0f, (scaled - container) / 2f)
}
