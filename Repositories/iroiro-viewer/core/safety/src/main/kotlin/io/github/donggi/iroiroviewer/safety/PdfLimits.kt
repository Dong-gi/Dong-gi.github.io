package io.github.donggi.iroiroviewer.safety

import kotlin.math.floor
import kotlin.math.min

/**
 * PDF 쪽을 **어느 크기의 비트맵에 그릴 것인가.** 순수 함수라 JVM 시험으로 박힌다.
 *
 * ## 왜 [ImageLimits] 에 얹지 않고 따로 두는가
 *
 * [ImageLimits.sampleForBudget] 은 **2의 거듭제곱**만 돌려준다. `ImageDecoder` 의
 * `setTargetSampleSize` 가 어차피 거듭제곱으로 내림하기 때문인데, PDF 는 사정이 다르다 —
 * `PdfRenderer` 는 우리가 만든 비트맵에 `Matrix` 로 그리므로 **배율이 연속**이다.
 * 거듭제곱으로 내리면 목표 근처에서 최대 2배(넓이로 4배) 흐려진다. 벡터라서 얻을 수
 * 있는 선명함을 계산 방식 때문에 버릴 이유가 없다.
 *
 * ## 왜 화면이 아니라 여기서 정하는가
 *
 * 값이 틀리면 결과가 `OutOfMemoryError` 이거나 `Canvas` 의 거부다. [ImageLimits] 의
 * 머리말이 그 이유로 '상한은 순수 함수로 빼서 JVM 시험이 답하게 한다' 를 이미 적어
 * 두었고, 이 파일은 그 규칙을 PDF 로 넓힌 것이다. **상한 계산이 화면 코드로 새기 시작하면
 * '상한은 한곳' 이 그 자리에서 깨진다.**
 */
object PdfLimits {

    /**
     * PDF 쪽 좌표의 단위. `PdfRenderer.Page.getWidth/getHeight` 가 이 단위의 정수를 준다.
     *
     * 72분의 1인치다 — A4 는 595×842pt 이고 US Letter 는 612×792pt 다.
     */
    const val POINTS_PER_INCH = 72

    /**
     * 이 쪽의 **원본 크기**를 이 화면의 화소로. 최대 배율(원본의 2배)을 재는 기준이다.
     *
     * PDF 에는 화소가 없으므로 '원본' 은 **종이의 실제 크기**다 — 1pt 는 1/72 인치이고,
     * 화면이 1인치에 [densityDpi] 화소를 쓰면 A4 폭(595pt)은 `595 × dpi / 72` 화소다.
     * 데스크톱 뷰어의 '100%(실제 크기)' 와 같은 뜻이다.
     *
     * [densityDpi] 는 `DisplayMetrics.densityDpi` 다(물리 dpi 인 `xdpi` 가 아니다). 화면의
     * dp 가 이 값에 묶여 있어, 쪽의 실제 크기가 앱의 다른 요소와 같은 잣대로 잰 크기가 된다.
     */
    fun originalWidthPx(widthPt: Int, densityDpi: Int): Int {
        if (widthPt <= 0 || densityDpi <= 0) return 0
        return (widthPt.toLong() * densityDpi / POINTS_PER_INCH).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
    }

    /**
     * 암호를 푼 PDF 를 메모리 파일(memfd)에 담을 수 있는 최대 크기.
     *
     * 안드로이드 14 이하의 `PdfRenderer` 는 암호를 받지 않으므로, 우리가 풀어 **평문 한 벌을
     * RAM 에** 만든 뒤 넘긴다(디스크에는 쓰지 않는다). 그 한 벌이 문서 크기만큼 메모리를
     * 쓰므로 상한이 필요하다. 256 MiB 는 이 앱의 쪽 비트맵 예산(수십 MB)보다 한 자리 크지만,
     * 실물 암호 PDF(명세서·논문·전자책)는 대개 수십 MB 안쪽이라 걸리는 일이 드물다.
     * 넘으면 '너무 커서 열 수 없습니다' 로 끝낸다 — 기기가 메모리를 다 내주다 죽는 것보다 낫다.
     *
     * 안드로이드 15 이상은 플랫폼이 암호를 직접 받으므로 이 상한에 닿지 않는다.
     */
    const val MAX_DECRYPTED_BYTES = DecryptLimits.MAX_BYTES

    /** 한 쪽을 그릴 비트맵. [scale] 은 포인트 → 화소 배율이다. */
    data class PageBitmap(val width: Int, val height: Int, val scale: Double)

    /**
     * 선명화 타일 하나.
     *
     * [srcLeft]·[srcTop] 은 **쪽 전체를 [scale] 로 그렸다고 쳤을 때의 화소 좌표**이고,
     * 그 '쪽 전체' 의 크기가 [pageWidth]·[pageHeight] 다.
     *
     * **쪽 크기를 같이 싣는 이유**는 호출자가 그것을 다시 구하지 않게 하려는 것이다.
     * 타일을 화면에 얹으려면 쪽 안에서의 비율(`srcLeft / pageWidth`)이 필요한데, 그러려면
     * `floor(폭pt × scale)` 을 호출자가 한 번 더 적어야 한다. 내림을 반올림으로 적는
     * 순간 타일이 반 화소씩 어긋나고, 그 어긋남은 확대한 글자의 가장자리에서 보인다.
     * **계산을 두 벌로 두지 않는다** — 이 저장소가 인코딩 판정과 이름 다듬기에서
     * 두 번 겪은 형태다.
     */
    data class Tile(
        val srcLeft: Int,
        val srcTop: Int,
        val width: Int,
        val height: Int,
        val scale: Double,
        /** 이 배율로 그린 쪽 전체의 화소 크기. 타일은 그 안의 직사각형이다. */
        val pageWidth: Int,
        val pageHeight: Int,
    )

    /**
     * 그릴 수 있는 쪽인가. **0과 음수만 거절한다.**
     *
     * 14400pt(200인치)짜리 쪽은 PDF 명세가 허락하는 정상 쪽이고, 그리는 문제는
     * [pageBitmap] 이 배율로 이미 푼다. **확인하지 못한 명세 상한을 상수로 박지 않는다** —
     * 이 저장소가 ZIP 엔트리 수 상계로 한 번 겪은 형태다(멀쩡한 파일을 거절했다).
     */
    fun isDrawablePage(widthPt: Int, heightPt: Int): Boolean = widthPt > 0 && heightPt > 0

    /**
     * 쪽 전체가 뷰포트에 들어가는 배율.
     *
     * 폰 세로에서 A4 는 이것이 곧 폭 맞춤이 된다(뷰포트 비 0.45 < 쪽 비 0.707).
     */
    fun fitScale(widthPt: Int, heightPt: Int, viewportWidth: Int, viewportHeight: Int): Double {
        if (!isDrawablePage(widthPt, heightPt)) return 0.0
        if (viewportWidth <= 0 || viewportHeight <= 0) return 0.0
        return min(viewportWidth.toDouble() / widthPt, viewportHeight.toDouble() / heightPt)
    }

    /**
     * 이 배율로 그리려면 비트맵이 얼마여야 하는가. 예산을 넘으면 **배율을 낮춘다.**
     *
     * 상한은 `min(capBytes, [ImageLimits.MAX_BITMAP_BYTES])` 다. 100MiB 짜리 비트맵을
     * `PdfRenderer` 는 군말 없이 채워 주지만 그 다음의 `Canvas` 가 API 31 에서 거부한다.
     *
     * **폭·높이를 내림(floor)한다.** `floor(w·s)·floor(h·s) ≤ w·h·s²` 가 언제나
     * 성립하므로 반올림이 상한을 넘기는 경우가 구조적으로 없다. 올림으로 적으면 시험이
     * 통과해도 실제로는 몇 바이트씩 넘는다.
     *
     * @return 그릴 수 없는 쪽이면 null.
     */
    fun pageBitmap(widthPt: Int, heightPt: Int, wantedScale: Double, capBytes: Long): PageBitmap? {
        if (!isDrawablePage(widthPt, heightPt)) return null
        if (!wantedScale.isFinite() || wantedScale <= 0.0) return null

        val cap = capOf(capBytes)
        // 넓이가 배율의 제곱으로 늘므로, 넘으면 제곱근만큼 줄이면 한 번에 든다.
        val area = widthPt.toDouble() * heightPt * ImageLimits.BYTES_PER_PIXEL
        val maxScale = kotlin.math.sqrt(cap.toDouble() / area)
        val scale = min(wantedScale, maxScale)

        val w = floor(widthPt * scale).toInt().coerceAtLeast(1)
        val h = floor(heightPt * scale).toInt().coerceAtLeast(1)
        return PageBitmap(w, h, scale)
    }

    /**
     * 확대한 만큼 다시 그릴 필요가 있는가.
     *
     * 맞춤 배율 이하면 같은 그림을 두 번 그리는 일이 된다 — 그때는 맞춤 층이 이미 그
     * 해상도다.
     */
    fun needsDetail(scale: Double, fitScale: Double): Boolean {
        if (!scale.isFinite() || !fitScale.isFinite()) return false
        if (fitScale <= 0.0) return false
        return scale > fitScale * DETAIL_THRESHOLD
    }

    /**
     * 지금 보고 있는 자리를 더 선명하게 그릴 타일.
     *
     * [focusX]·[focusY] 는 쪽 안의 **비율 좌표**(0~1)다. 화면 밖으로 나가지 않도록
     * 잘라 주므로 호출자가 범위를 검사할 필요가 없다.
     *
     * @param maxScale 선명화가 쫓아가는 배율의 상한(포인트 → 화소). 화면이 확대를 허락하는
     *   최대치 — `ZoomState.maxScale × 맞춤 배율` — 를 준다. 그 위로는 확대가 되지 않으니
     *   그릴 이유도 없다. 상한을 화면에서 받는 것은 최대 배율의 계산이 `ZoomMath` 한 곳에
     *   있어야 하기 때문이다 — 여기서 다시 적으면 두 벌이 된다.
     * @return 선명화가 필요 없거나([needsDetail]) 예산이 없으면([capBytes] 가 0 이하) null.
     */
    fun tileFor(
        widthPt: Int,
        heightPt: Int,
        scale: Double,
        viewportWidth: Int,
        viewportHeight: Int,
        focusX: Double,
        focusY: Double,
        capBytes: Long,
        maxScale: Double,
    ): Tile? {
        if (!isDrawablePage(widthPt, heightPt)) return null
        if (viewportWidth <= 0 || viewportHeight <= 0) return null
        if (capBytes <= 0) return null
        if (!maxScale.isFinite() || maxScale <= 0.0) return null

        val fit = fitScale(widthPt, heightPt, viewportWidth, viewportHeight)
        if (!needsDetail(scale, fit)) return null

        // **무한히 선명해지지 않는다.** 화면이 허락하는 최대 확대에서 멈추고, 그것과 별개로
        // 쪽 좌표가 `Int` 를 넘지 않게 절대 상한을 건다(퇴화한 입력에서 음수 좌표가 나온다).
        val absolute = MAX_PAGE_PIXELS / maxOf(widthPt, heightPt).toDouble()
        val capped = minOf(scale, maxScale, absolute)
        if (!needsDetail(capped, fit)) return null
        val pageW = floor(widthPt * capped).toInt().coerceAtLeast(1)
        val pageH = floor(heightPt * capped).toInt().coerceAtLeast(1)

        // 타일은 뷰포트만큼만 뜬다 — 화면에 보이지 않는 자리를 그릴 이유가 없다.
        // 예산이 그보다 작으면 예산이 이긴다.
        val budgetSide = kotlin.math.sqrt(
            capBytes.toDouble() / ImageLimits.BYTES_PER_PIXEL
        )
        var tw = min(viewportWidth, pageW)
        var th = min(viewportHeight, pageH)
        if (tw.toLong() * th * ImageLimits.BYTES_PER_PIXEL > capBytes) {
            val shrink = budgetSide / kotlin.math.sqrt(tw.toDouble() * th)
            tw = floor(tw * shrink).toInt().coerceAtLeast(1)
            th = floor(th * shrink).toInt().coerceAtLeast(1)
        }
        if (tw > pageW) tw = pageW
        if (th > pageH) th = pageH

        val fx = focusX.coerceIn(0.0, 1.0)
        val fy = focusY.coerceIn(0.0, 1.0)
        val left = (fx * pageW - tw / 2.0).toInt().coerceIn(0, (pageW - tw).coerceAtLeast(0))
        val top = (fy * pageH - th / 2.0).toInt().coerceIn(0, (pageH - th).coerceAtLeast(0))
        return Tile(
            srcLeft = left,
            srcTop = top,
            width = tw,
            height = th,
            scale = capped,
            pageWidth = pageW,
            pageHeight = pageH,
        )
    }

    /**
     * 타일 좌표계(배율로 그린 쪽 전체)의 한 변 상한. 비트맵이 아니라 **좌표**의 상한이다 —
     * 실제로 만드는 타일은 뷰포트와 예산이 누른다. `Int` 를 넘지 않게 하려는 것뿐이다.
     */
    private const val MAX_PAGE_PIXELS = 1 shl 28

    /** 이 크기의 비트맵이 몇 바이트인가. */
    fun bytesOf(width: Int, height: Int): Long =
        width.toLong().coerceAtLeast(0) * height.coerceAtLeast(0) * ImageLimits.BYTES_PER_PIXEL

    /**
     * 실제로 쓸 상한. 주어진 예산과 절대 상한 가운데 **작은 쪽**이다.
     *
     * 예산을 0이나 음수로 주는 호출자(아직 뷰포트를 모르는 첫 프레임)를 예외로 죽이지
     * 않는다 — 그때는 절대 상한만 건다.
     */
    private fun capOf(capBytes: Long): Long =
        if (capBytes <= 0) ImageLimits.MAX_BITMAP_BYTES else min(capBytes, ImageLimits.MAX_BITMAP_BYTES)

    /**
     * 이 배수를 넘게 확대해야 선명화를 켠다.
     *
     * 1.0 으로 두면 손가락이 스치기만 해도 타일을 뜬다. 조금 넘는 확대에서는 맞춤 층을
     * 늘려 보여 주는 편이 그리는 값보다 싸다.
     */
    private const val DETAIL_THRESHOLD = 1.2
}
