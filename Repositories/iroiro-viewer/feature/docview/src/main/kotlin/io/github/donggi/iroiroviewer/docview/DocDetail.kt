package io.github.donggi.iroiroviewer.docview

import io.github.donggi.iroiroviewer.safety.PdfLimits
import kotlin.math.floor

/**
 * 확대한 자리를 다시 그릴지, 그린다면 **어디를 얼마로** 그릴지 정하는 계산.
 *
 * 화면에서 갈라 둔 이유는 이 저장소가 여러 번 확인한 것과 같다 — `FolderQueue`·
 * `GestureMath`·`SpeedSteps`·`PipMath` 가 전부 그렇게 갈렸다. **확대·이동의 좌표 계산은
 * 화면 캡처로 판정할 수 있는 종류가 아니다**(6단계가 그것으로 세 번 틀린 결론을 냈다).
 * 여기 있으면 JVM 시험이 초 단위로 답한다.
 *
 * ## 값을 눈금으로 끊는다
 *
 * 화면은 `snapshotFlow` 로 확대 상태를 보는데, 끊지 않으면 손가락이 1화소 움직일 때마다
 * 다른 값이 나와 그때마다 새 타일을 뜨게 된다. pdfium 의 잠금은 **프로세스 전역**이라
 * 그 요청들이 줄을 서고, 줄이 밀리는 만큼 화면이 늦게 따라온다. 눈금은 눈에 보이지
 * 않을 만큼 곱다 — 배율 1/100, 자리 1/200(1080화소 폭에서 5화소).
 */
internal object DocDetail {

    const val SCALE_STEPS = 100
    const val FOCUS_STEPS = 200

    /** 상세 타일 하나를 뜨는 요청. 같은 요청이면 같은 값이라 흐름이 저절로 합쳐진다. */
    data class Want(val scaleStep: Int, val focusStepX: Int, val focusStepY: Int) {
        /** 포인트 → 화소 배율. [PdfLimits.tileFor] 가 받는 값이다. */
        val scale: Double get() = scaleStep.toDouble() / SCALE_STEPS

        /** 쪽 안의 비율 좌표(0~1). */
        val focusX: Double get() = focusStepX.toDouble() / FOCUS_STEPS
        val focusY: Double get() = focusStepY.toDouble() / FOCUS_STEPS
    }

    /**
     * 지금 화면 한가운데에 쪽의 어디가 있는가.
     *
     * `ZoomState` 의 변환은 `translate(offset)` 뒤에 `scale(배율, pivot = 뷰포트 중앙)`
     * 이다. 화면 중앙에 오는 바닥층 좌표 `bx` 를 그 식에서 풀면
     *
     * ```
     * 중앙 = 중앙 + (bx - 중앙) × 배율 + offset   →   bx = 중앙 - offset / 배율
     * ```
     *
     * 이고, 바닥층은 뷰포트 가운데에 놓이므로 쪽 안의 비율은
     * `(bx - 여백) / 바닥층 폭` 이다.
     *
     * @param fitScale 배율 1 에서의 포인트 → 화소 배율.
     * @param zoom 사용자가 건 확대(1 이면 맞춤).
     * @return 확대하지 않았거나([PdfLimits.needsDetail]) 아직 잴 수 없으면 null.
     */
    fun want(
        fitScale: Double,
        zoom: Float,
        viewportWidth: Float,
        viewportHeight: Float,
        fittedWidth: Float,
        fittedHeight: Float,
        offsetX: Float,
        offsetY: Float,
    ): Want? {
        if (fitScale <= 0.0 || !fitScale.isFinite()) return null
        if (zoom <= 0f || !zoom.isFinite()) return null
        if (fittedWidth <= 0f || fittedHeight <= 0f) return null
        if (viewportWidth <= 0f || viewportHeight <= 0f) return null
        if (!offsetX.isFinite() || !offsetY.isFinite()) return null

        val absolute = fitScale * zoom
        if (!PdfLimits.needsDetail(absolute, fitScale)) return null

        val bx = viewportWidth / 2f - offsetX / zoom
        val by = viewportHeight / 2f - offsetY / zoom
        val fx = (bx - (viewportWidth - fittedWidth) / 2f) / fittedWidth
        val fy = (by - (viewportHeight - fittedHeight) / 2f) / fittedHeight
        if (!fx.isFinite() || !fy.isFinite()) return null

        return Want(
            // **반올림 방향이 뜻을 가지면 `floor(x + 0.5)` 로 적는다.**
            // `kotlin.math.round` 는 짝수 쪽으로 반올림한다(함정 표) — 10단계가
            // 9:16 을 1000배 한 562.5 에서 그것으로 한 번 걸렸다.
            scaleStep = floor(absolute * SCALE_STEPS + 0.5).toInt().coerceAtLeast(1),
            focusStepX = floor(fx.coerceIn(0f, 1f) * FOCUS_STEPS + 0.5f).toInt(),
            focusStepY = floor(fy.coerceIn(0f, 1f) * FOCUS_STEPS + 0.5f).toInt(),
        )
    }

    /**
     * 타일을 바닥층 위에 얹을 자리. 넷 다 **바닥층 안의 비율**(0~1)이다.
     *
     * 쪽 크기를 여기서 다시 구하지 않고 [PdfLimits.Tile] 이 싣고 온 것을 쓴다 —
     * 그쪽 주석이 적은 대로, 내림을 반올림으로 다시 적는 순간 타일이 반 화소씩 어긋난다.
     */
    fun fractionOf(tile: PdfLimits.Tile): FloatArray {
        val pw = tile.pageWidth.coerceAtLeast(1).toFloat()
        val ph = tile.pageHeight.coerceAtLeast(1).toFloat()
        return floatArrayOf(
            tile.srcLeft / pw,
            tile.srcTop / ph,
            tile.width / pw,
            tile.height / ph,
        )
    }
}
