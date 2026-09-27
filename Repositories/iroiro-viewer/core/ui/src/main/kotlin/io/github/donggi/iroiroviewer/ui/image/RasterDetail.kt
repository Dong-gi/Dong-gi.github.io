package io.github.donggi.iroiroviewer.ui.image

import io.github.donggi.iroiroviewer.safety.ImageLimits
import io.github.donggi.iroiroviewer.ui.gesture.ZoomMath
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/**
 * EXIF 방향과 **영역 디코딩의 좌표**를 잇는 계산. 안드로이드를 모른다.
 *
 * `BitmapRegionDecoder` 는 EXIF 방향을 적용하지 않고 **파일에 저장된 그대로의 좌표**로
 * 자른다. 그런데 바닥층(`ImageDecoder`)은 방향을 적용한 **화면 방향**으로 떠 있으므로,
 * 화면에서 고른 사각형을 저장 좌표로 옮겨 자르고, 잘라 온 조각을 다시 돌려야 한다.
 * 틀리면 확대하는 순간 조각이 엉뚱한 자리의 그림이거나 90° 누운 그림이 된다 — 그리고
 * 그것은 화면 캡처로 판정할 종류가 아니다(6단계가 제스처를 캡처로 판정하다 세 번 틀렸다).
 * 그래서 JVM 시험이 답하게 떼어 둔다.
 *
 * **90°·270° 만 다룬다.** 180°·거울상은 가로세로가 안 바뀌어 디코더가 적용했는지를 잴 수
 * 없으므로(`ImageProbe.canUseRegionDecoder`) 애초에 이 길로 오지 않는다.
 *
 * 사각형은 `[왼, 위, 오른, 아래]` 이고 오른·아래는 **포함하지 않는다**(`android.graphics.Rect`
 * 와 같다).
 */
object RegionMath {

    /** EXIF 방향 값. `ExifInterface` 의 상수와 같다 — 순수 JVM 에서 쓰려고 옮겨 적었다. */
    const val UNDEFINED = 0
    const val NORMAL = 1
    const val ROTATE_90 = 6
    const val ROTATE_270 = 8

    /** 화면 방향 사각형 → 저장 방향 사각형. */
    fun toStored(orientation: Int, storedWidth: Int, storedHeight: Int, rect: IntArray): IntArray =
        when (orientation) {
            // 저장 (xs, ys) 를 시계 방향 90° 돌리면 화면 (H - ys, xs). 거꾸로 풀면
            // xs = yd, ys = H - xd.
            ROTATE_90 -> intArrayOf(rect[1], storedHeight - rect[2], rect[3], storedHeight - rect[0])
            // 반시계 90°: 화면 (ys, W - xs). 거꾸로 xs = W - yd, ys = xd.
            ROTATE_270 -> intArrayOf(storedWidth - rect[3], rect[0], storedWidth - rect[1], rect[2])
            else -> rect.copyOf()
        }

    /** 저장 방향 사각형 → 화면 방향 사각형. [toStored] 의 역이다. */
    fun toDisplay(orientation: Int, storedWidth: Int, storedHeight: Int, rect: IntArray): IntArray =
        when (orientation) {
            ROTATE_90 -> intArrayOf(storedHeight - rect[3], rect[0], storedHeight - rect[1], rect[2])
            ROTATE_270 -> intArrayOf(rect[1], storedWidth - rect[2], rect[3], storedWidth - rect[0])
            else -> rect.copyOf()
        }

    /** 잘라 온 조각을 화면 방향으로 만들려면 시계 방향으로 몇 도 돌리는가. */
    fun rotationDegrees(orientation: Int): Int = when (orientation) {
        ROTATE_90 -> 90
        ROTATE_270 -> 270
        else -> 0
    }
}

/**
 * 사진·만화 쪽을 확대했을 때 **원본에서 다시 뜰 자리**를 정하는 계산.
 *
 * PDF 는 더 큰 배율로 다시 그리면 되지만(`DocDetail`), 래스터 그림은 원본 화소를 다시
 * 읽어야 한다. 바닥층은 화면에 맞춰 줄여 뜬 것이라, 원본의 2배까지 확대하면(`ZoomMath.maxScale`)
 * 바닥층 한 화소가 화면 여러 화소로 번져 흐리다.
 *
 * ## 값을 눈금으로 끊는다
 *
 * 화면이 확대 상태를 `snapshotFlow` 로 보는데, 끊지 않으면 손가락이 1화소 움직일 때마다
 * 다른 사각형이 나와 그때마다 원본을 다시 읽는다. 사각형을 그림의 1/[GRID] 눈금에 맞춰
 * **바깥쪽으로** 넓힌다 — 요청 수가 줄고, 조금 밀어도 조각 가장자리가 드러나지 않는다.
 */
object RasterDetail {

    /** 그림 한 변을 몇 칸으로 끊는가. */
    const val GRID = 64

    /**
     * 바닥층이 화면에서 이 배수를 넘게 늘어나야 다시 뜬다. PDF 의 선명화 문턱과 같은 값이다
     * (`PdfLimits` 의 `DETAIL_THRESHOLD`) — 그 아래는 눈으로 가려지지 않는다.
     */
    const val THRESHOLD = 1.2f

    /**
     * 원본에서 뜰 사각형(화면 방향의 **원본 화소**)과 표본.
     *
     * 같은 요청이면 같은 값이라 흐름이 저절로 합쳐진다.
     */
    data class Want(val left: Int, val top: Int, val right: Int, val bottom: Int, val sample: Int) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
    }

    /**
     * @param baseWidth 바닥층 비트맵의 폭(화소).
     * @param originalWidth·[originalHeight] 원본(화면 방향)의 화소.
     * @param capBytes 조각 한 장의 바이트 상한(`ImageLimits.Budget.detailCap`). 0 이하면
     *   선명화를 켜지 않는다 — 힙이 작은 기기에서 예산이 그렇게 나온다.
     * @return 뜰 필요가 없거나 잴 수 없으면 null.
     */
    fun want(
        zoom: Float,
        viewportWidth: Float,
        viewportHeight: Float,
        fittedWidth: Float,
        fittedHeight: Float,
        offsetX: Float,
        offsetY: Float,
        baseWidth: Int,
        originalWidth: Int,
        originalHeight: Int,
        capBytes: Long,
    ): Want? {
        if (capBytes <= 0 || baseWidth <= 0 || originalWidth <= 0 || originalHeight <= 0) return null
        // 원본이 바닥층보다 크지 않으면 다시 뜰 것이 없다(작은 그림은 바닥층이 곧 원본이다).
        if (originalWidth <= baseWidth) return null
        // 바닥층이 화면에서 아직 제 해상도 근처면 뜨지 않는다.
        if (fittedWidth * zoom <= baseWidth * THRESHOLD) return null

        val f = ZoomMath.visibleFraction(
            zoom, viewportWidth, viewportHeight, fittedWidth, fittedHeight, offsetX, offsetY,
        ) ?: return null
        val l = floor(f[0] * GRID) / GRID
        val t = floor(f[1] * GRID) / GRID
        val r = ceil(f[2] * GRID) / GRID
        val b = ceil(f[3] * GRID) / GRID

        val left = floor(l * originalWidth).toInt().coerceIn(0, originalWidth - 1)
        val top = floor(t * originalHeight).toInt().coerceIn(0, originalHeight - 1)
        val right = ceil(r * originalWidth).toInt().coerceIn(left + 1, originalWidth)
        val bottom = ceil(b * originalHeight).toInt().coerceIn(top + 1, originalHeight)

        // 이 조각이 화면에서 차지하는 긴 변. 그만큼의 화소면 충분하다(더 뜨면 버린다).
        val onScreen = max(
            (right - left).toFloat() / originalWidth * fittedWidth * zoom,
            (bottom - top).toFloat() / originalHeight * fittedHeight * zoom,
        )
        val sample = ImageLimits.sampleForBudget(
            right - left, bottom - top, ceil(onScreen).toInt().coerceAtLeast(1), capBytes,
        )
        // 표본을 걸고 나니 바닥층보다 선명하지 않다면(예산이 작다) 뜰 이유가 없다.
        if (originalWidth.toFloat() / sample <= baseWidth * THRESHOLD) return null
        return Want(left, top, right, bottom, sample)
    }
}
