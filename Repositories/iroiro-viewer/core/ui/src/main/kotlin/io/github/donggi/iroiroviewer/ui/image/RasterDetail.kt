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
 * **여덟 방향을 전부 다룬다**(14단계). 예전에는 90°·270° 만 옮겼는데, 막혀 있던 것은 계산이
 * 아니라 '바닥층이 방향을 적용했는가' 를 모르는 것이었다 — 180°·거울상은 가로세로가 안 바뀌어
 * 치수로는 잴 수 없다. 그 답은 이제 화소로 잰다([OrientationMatch]). 계산이 여기 한 곳에 있으므로
 * 답만 있으면 어느 방향이든 조각이 제자리에 바로 선다.
 *
 * 모든 계산의 뿌리는 [displayOf] 하나다 — 저장 화소 하나가 화면의 어디로 가는가. 사각형 옮기기
 * ([toStored]·[toDisplay])와 조각 돌리기([rotationDegrees]·[mirrorOf])는 그것을 사각형과
 * 변환 행렬의 말로 다시 적은 것이고, 시험이 셋이 같은 답을 내는지 대조한다.
 *
 * 사각형은 `[왼, 위, 오른, 아래]` 이고 오른·아래는 **포함하지 않는다**(`android.graphics.Rect`
 * 와 같다).
 */
object RegionMath {

    /** EXIF 방향 값. `ExifInterface` 의 상수와 같다 — 순수 JVM 에서 쓰려고 옮겨 적었다. */
    const val UNDEFINED = 0
    const val NORMAL = 1
    const val FLIP_HORIZONTAL = 2
    const val ROTATE_180 = 3
    const val FLIP_VERTICAL = 4
    const val TRANSPOSE = 5
    const val ROTATE_90 = 6
    const val TRANSVERSE = 7
    const val ROTATE_270 = 8

    /** 돌린 뒤에 뒤집는 축. */
    enum class Mirror { NONE, HORIZONTAL, VERTICAL }

    /** 가로세로가 바뀌는 방향인가(5~8). 알 수 없는 값은 정방향으로 친다. */
    fun swapsAxes(orientation: Int): Boolean = orientation in TRANSPOSE..ROTATE_270

    /**
     * 저장 화소 `(x, y)` 가 화면의 어느 화소로 가는가. `[X, Y]` 를 돌려준다.
     *
     * EXIF 명세의 문장('0번째 행이 화면의 어느 쪽인가, 0번째 열이 어느 쪽인가')을 좌표로 옮긴
     * 것이다. [w]·[h] 는 **저장** 치수다.
     */
    fun displayOf(orientation: Int, w: Int, h: Int, x: Int, y: Int): IntArray = when (orientation) {
        FLIP_HORIZONTAL -> intArrayOf(w - 1 - x, y)
        ROTATE_180 -> intArrayOf(w - 1 - x, h - 1 - y)
        FLIP_VERTICAL -> intArrayOf(x, h - 1 - y)
        TRANSPOSE -> intArrayOf(y, x)
        ROTATE_90 -> intArrayOf(h - 1 - y, x)
        TRANSVERSE -> intArrayOf(h - 1 - y, w - 1 - x)
        ROTATE_270 -> intArrayOf(y, w - 1 - x)
        else -> intArrayOf(x, y)
    }

    /** 화면 방향 사각형 → 저장 방향 사각형. [w]·[h] 는 저장 치수다. */
    fun toStored(orientation: Int, storedWidth: Int, storedHeight: Int, rect: IntArray): IntArray {
        val w = storedWidth
        val h = storedHeight
        val (l, t, r, b) = rect
        // 각 줄은 [displayOf] 를 거꾸로 푼 것이다. 반열린 구간이라 'W - 1 - x' 가 사각형에서는
        // 'W - 오른 … W - 왼' 이 된다.
        return when (orientation) {
            FLIP_HORIZONTAL -> intArrayOf(w - r, t, w - l, b)
            ROTATE_180 -> intArrayOf(w - r, h - b, w - l, h - t)
            FLIP_VERTICAL -> intArrayOf(l, h - b, r, h - t)
            TRANSPOSE -> intArrayOf(t, l, b, r)
            // 저장 (xs, ys) 를 시계 방향 90° 돌리면 화면 (H - 1 - ys, xs). 거꾸로 풀면
            // xs = yd, ys = H - 1 - xd.
            ROTATE_90 -> intArrayOf(t, h - r, b, h - l)
            TRANSVERSE -> intArrayOf(w - b, h - r, w - t, h - l)
            // 반시계 90°: 화면 (ys, W - 1 - xs). 거꾸로 xs = W - 1 - yd, ys = xd.
            ROTATE_270 -> intArrayOf(w - b, l, w - t, r)
            else -> rect.copyOf()
        }
    }

    /** 저장 방향 사각형 → 화면 방향 사각형. [toStored] 의 역이다. */
    fun toDisplay(orientation: Int, storedWidth: Int, storedHeight: Int, rect: IntArray): IntArray {
        val w = storedWidth
        val h = storedHeight
        val (l, t, r, b) = rect
        return when (orientation) {
            FLIP_HORIZONTAL -> intArrayOf(w - r, t, w - l, b)
            ROTATE_180 -> intArrayOf(w - r, h - b, w - l, h - t)
            FLIP_VERTICAL -> intArrayOf(l, h - b, r, h - t)
            TRANSPOSE -> intArrayOf(t, l, b, r)
            ROTATE_90 -> intArrayOf(h - b, l, h - t, r)
            TRANSVERSE -> intArrayOf(h - b, w - r, h - t, w - l)
            ROTATE_270 -> intArrayOf(t, w - r, b, w - l)
            else -> rect.copyOf()
        }
    }

    /**
     * 잘라 온 조각을 화면 방향으로 만들려면 시계 방향으로 몇 도 돌리는가. **돌린 다음에**
     * [mirrorOf] 의 축으로 뒤집는다 — 차례가 바뀌면 거울상 둘(5·7)이 서로 뒤바뀐다.
     */
    fun rotationDegrees(orientation: Int): Int = when (orientation) {
        ROTATE_180 -> 180
        TRANSPOSE, ROTATE_90, TRANSVERSE -> 90
        ROTATE_270 -> 270
        else -> 0
    }

    /** [rotationDegrees] 만큼 돌린 뒤 뒤집는 축. */
    fun mirrorOf(orientation: Int): Mirror = when (orientation) {
        FLIP_HORIZONTAL, TRANSPOSE -> Mirror.HORIZONTAL
        FLIP_VERTICAL, TRANSVERSE -> Mirror.VERTICAL
        else -> Mirror.NONE
    }

    /**
     * 조각에 입힐 방향. **조각은 바닥층을 따른다** — 바닥층이 방향을 적용해 떠 있으면 파일의 방향을,
     * 적용하지 않았으면(PNG 디코더는 EXIF 를 읽지 않는다) 저장 방향 그대로([NORMAL])를 입힌다.
     * 모르면 null 이고 조각을 뜨지 않는다 — 흐린 채로 확대하는 것이 뒤집힌 조각을 얹는 것보다 낫다.
     *
     * @param applied 바닥층의 디코더가 방향을 적용하는가(`ImageIo.appliedOrientationByPixels`).
     */
    fun tileOrientation(applied: Boolean?, orientation: Int): Int? = when (applied) {
        true -> orientation
        false -> NORMAL
        null -> null
    }

    /**
     * 화소 배열(저장 방향, [w]×[h], 행 우선)을 화면 방향으로 옮긴다. 가로세로가 바뀌는 방향이면
     * 결과는 [h]×[w] 다.
     *
     * **작은 그림에만 쓴다** — 방향 판정([OrientationMatch])의 수십 화소짜리 축소본이다. 조각은
     * 수백만 화소라 배열 둘을 더 드는 이 길이 아니라 변환 행렬로 돌린다(`ImageIo`).
     */
    fun transformPixels(orientation: Int, w: Int, h: Int, pixels: IntArray): IntArray {
        require(w > 0 && h > 0 && pixels.size == w * h) { "화소 수가 치수와 맞지 않는다" }
        val outW = if (swapsAxes(orientation)) h else w
        val out = IntArray(pixels.size)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val d = displayOf(orientation, w, h, x, y)
                out[d[1] * outW + d[0]] = pixels[y * w + x]
            }
        }
        return out
    }
}

/**
 * 플랫폼 디코더가 이 파일의 방향을 **적용했는가를 화소로 잰다.**
 *
 * ## 왜 필요한가
 *
 * 바닥층(`ImageDecoder`)은 방향을 적용할 수도 안 할 수도 있다 — 형식과 기기 판에 달렸다(PNG 디코더는
 * EXIF 를 읽지 않는다, 실측). 조각(`BitmapRegionDecoder`)은 언제나 적용하지 않는다. 둘을 맞추려면
 * 바닥층이 어느 쪽이었는지 알아야 하는데, 90°·270° 는 치수가 바뀌어 그것으로 답이 나오고
 * (`ImageIo.probe`) **180°·거울상·정사각은 치수가 그대로라 답이 없다.** 6단계가 그 파일들을 흐린 채로
 * 둔 이유다.
 *
 * ## 어떻게 재는가
 *
 * 원본을 바닥층과 같은 디코더로 작게 뜬 것과, 조각과 같은 디코더로 **저장 방향 그대로** 같은 표본으로
 * 뜬 것을 둔다(`ImageIo.appliedOrientationByPixels`). 가설이 둘뿐이다 — 적용했다면 앞의 것 ≈ 방향을 입힌
 * 원본, 안 했다면 앞의 것 ≈ 원본 그대로. 둘과의 평균 차이를 견주어 **한쪽이 뚜렷하게 가까울 때만** 답한다.
 *
 * ## 이 판정이 결정적인가
 *
 * 한 파일·한 기기에서는 그렇다 — 방향을 적용하는가는 형식의 코덱과 플랫폼 판이 정하고(JPEG·WebP 는
 * EXIF 를 적용하고 PNG 는 읽지 않는다, 실측), 같은 파일을 같은 디코더로 다시 뜨면 같은 답이다. 결정적이지
 * **않은** 것은 우리가 그 답을 **미리 알 방법**이다: `ImageDecoder.ImageInfo` 는 방향을 알려 주지 않고,
 * 형식별 표는 판이 바뀌면 조용히 틀린다. 그래서 파일마다 한 번 잰다. 가를 수 없는 그림(아래)은 결정적으로
 * '모른다' 가 나온다 — 같은 그림은 언제나 같은 차이를 낸다.
 *
 * 그림이 그 변환에 대칭이면(한 가지 색, 좌우가 같은 건물) 두 가설이 같은 그림을 내므로 가를 수
 * 없다 — 그때는 null 이고 화면은 예전처럼 흐린 채로 확대한다. **틀린 조각을 얹는 것보다 흐린 편이
 * 낫다.** 뒤집힌 조각은 '앱이 고장났다' 로 읽히지만 흐린 것은 '아직 덜 떴다' 로 읽힌다.
 *
 * 표를 만들지 않는 까닭은 `ImageIo.probe` 와 같다 — '이 형식은 적용한다' 를 적어 두면 기기·판이
 * 바뀔 때 조용히 틀린다. 파일마다 잰다.
 */
object OrientationMatch {

    /** 견줄 축소본의 긴 변(화소). 사진의 큰 구도(하늘과 땅, 왼쪽의 인물)가 남는 크기다. */
    const val THUMB_LONGEST = 32

    /**
     * 축소본을 만들 때 이 배수로 먼저 줄인 뒤 칸 평균을 낸다. 한 번에 수십 분의 일로 줄이면
     * 이중선형 보간이 화소를 건너뛰어 잡음이 된다(에일리어싱).
     */
    const val BOX = 4

    /** 틀린 가설과의 차이가 이보다 작으면 그림이 그 변환에 거의 대칭이다 — 가르지 않는다. 0~255. */
    const val MIN_SEPARATION = 6f

    /** 맞는 가설과의 차이가 이보다 크면 어느 쪽도 그 그림이 아니다(색 공간이 다르다 등) — 가르지 않는다. */
    const val MAX_MATCH = 24f

    /** 맞는 가설이 틀린 가설보다 이 비율 안쪽이어야 '뚜렷하다' 로 본다. */
    const val MAX_RATIO = 0.5f

    /** 축소본의 치수 `[폭, 높이]`. 긴 변이 [longest] 이고 짧은 변은 최소 1. */
    fun thumbSize(width: Int, height: Int, longest: Int = THUMB_LONGEST): IntArray {
        if (width <= 0 || height <= 0) return intArrayOf(1, 1)
        return if (width >= height) {
            intArrayOf(longest, (longest.toLong() * height / width).toInt().coerceAtLeast(1))
        } else {
            intArrayOf((longest.toLong() * width / height).toInt().coerceAtLeast(1), longest)
        }
    }

    /**
     * @param base 바닥층과 같은 디코더로 뜬 것을 [w]×[h] 로 줄인 것 — 방향을 적용했을 수도 안 했을 수도 있다.
     * @param raw 원본을 **저장 방향 그대로** 같은 크기로 줄인 것. 이 판정이 불리는 파일은 언제나
     *   저장 치수와 화면 치수가 같다(치수가 다르면 `probe` 가 이미 답했다).
     * @return 적용했다 true, 안 했다 false, 가를 수 없다 null.
     */
    fun decide(orientation: Int, base: IntArray, raw: IntArray, w: Int, h: Int): Boolean? {
        if (w <= 0 || h <= 0 || base.size != w * h || raw.size != w * h) return null
        // 정방향은 잴 것이 없다. 가로세로가 바뀌는 방향인데 정사각이 아니면 치수로 이미 갈렸다.
        if (orientation == RegionMath.NORMAL || orientation == RegionMath.UNDEFINED) return true
        if (orientation !in RegionMath.FLIP_HORIZONTAL..RegionMath.ROTATE_270) return null
        if (RegionMath.swapsAxes(orientation) && w != h) return null
        val turned = RegionMath.transformPixels(orientation, w, h, raw)
        val applied = meanAbsDiff(base, turned)
        val notApplied = meanAbsDiff(base, raw)
        val near = minOf(applied, notApplied)
        val far = maxOf(applied, notApplied)
        if (far < MIN_SEPARATION) return null
        if (near > MAX_MATCH) return null
        if (near > far * MAX_RATIO) return null
        return applied < notApplied
    }

    /** 두 그림의 평균 차이. 빛깔 세 채널을 따로 재어 평균한다(알파는 보지 않는다). 0~255. */
    fun meanAbsDiff(a: IntArray, b: IntArray): Float {
        if (a.isEmpty() || a.size != b.size) return Float.MAX_VALUE
        var sum = 0L
        for (i in a.indices) {
            val p = a[i]
            val q = b[i]
            sum += kotlin.math.abs(((p shr 16) and 0xFF) - ((q shr 16) and 0xFF))
            sum += kotlin.math.abs(((p shr 8) and 0xFF) - ((q shr 8) and 0xFF))
            sum += kotlin.math.abs((p and 0xFF) - (q and 0xFF))
        }
        return sum.toFloat() / (a.size * 3)
    }

    /**
     * [factor]×[factor] 칸의 평균으로 줄인다. [w]·[h] 는 [factor] 의 배수여야 한다.
     * 결과는 불투명(알파 255)이다 — 판정은 빛깔만 본다.
     */
    fun boxDownsample(pixels: IntArray, w: Int, h: Int, factor: Int): IntArray {
        require(factor > 0 && w % factor == 0 && h % factor == 0 && pixels.size == w * h) {
            "칸으로 나누어떨어지지 않는다"
        }
        val ow = w / factor
        val oh = h / factor
        val out = IntArray(ow * oh)
        val n = factor * factor
        for (oy in 0 until oh) {
            for (ox in 0 until ow) {
                var r = 0
                var g = 0
                var b = 0
                for (dy in 0 until factor) {
                    val row = (oy * factor + dy) * w + ox * factor
                    for (dx in 0 until factor) {
                        val p = pixels[row + dx]
                        r += (p shr 16) and 0xFF
                        g += (p shr 8) and 0xFF
                        b += p and 0xFF
                    }
                }
                out[oy * ow + ox] = (0xFF shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
            }
        }
        return out
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
