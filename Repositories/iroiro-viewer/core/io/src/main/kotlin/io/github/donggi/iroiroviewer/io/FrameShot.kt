package io.github.donggi.iroiroviewer.io

import java.util.Locale
import kotlin.math.roundToInt

/**
 * 영상 화면 캡처의 셈 — 받을 크기와 새 파일 이름의 꼬리. `android.*` 를 쓰지 않아 JVM 시험이 답한다(`FrameShotTest`).
 * 쓰는 일은 [ImageSaver.saveFrame] 이 한다(잘라 저장과 같은 길).
 */
object FrameShot {

    /** 받을 비트맵의 크기(가로·세로 화소). */
    data class Size(val width: Int, val height: Int)

    /**
     * 영상 한 장을 받을 크기 — **보여야 할 비율 그대로, 화소는 줄이지 않는 쪽으로.** 화소가 정사각형이 아니면([pixelRatio] ≠ 1)
     * 좁은 쪽을 늘린다(720×576 에 16:15 → 768×576) — 줄이면 원본의 화소를 버린다. 결과가 [maxPixels] 를 넘으면 비율을 지키며 줄인다
     * (8K 영상 따위 — 상한은 잘라 저장과 같은 값이다, [ImageSaver.maxPixels]). 크기를 모르면 null.
     */
    fun sizeOf(width: Int, height: Int, pixelRatio: Float, maxPixels: Long = ImageSaver.maxPixels()): Size? {
        if (width <= 0 || height <= 0) return null
        val par = if (pixelRatio > 0f && pixelRatio.isFinite()) pixelRatio else 1f
        var w = if (par > 1f) (width * par).roundToInt() else width
        var h = if (par < 1f) (height / par).roundToInt() else height
        if (w.toLong() * h > maxPixels) {
            val scale = kotlin.math.sqrt(maxPixels.toDouble() / (w.toLong() * h))
            w = (w * scale).toInt()
            h = (h * scale).toInt()
        }
        return if (w > 0 && h > 0) Size(w, h) else null
    }

    /**
     * 새 파일 이름의 꼬리 — 영상의 몇 분 몇 초인가를 1000분의 1초까지(`capture_0m12.345s`, 한 시간을 넘으면 `capture_1h02m03.456s`).
     * 프레임 넘기기로 한 초 안의 여러 장을 받으면 초까지만으로는 `(2)` 가 붙어 어느 것이 앞인지 모른다. 숫자는 `Locale.ROOT`.
     */
    fun tag(positionMs: Long): String {
        val t = positionMs.coerceAtLeast(0)
        val ms = t % 1000
        val total = t / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) {
            String.format(Locale.ROOT, "%s_%dh%02dm%02d.%03ds", TAG, h, m, s, ms)
        } else {
            String.format(Locale.ROOT, "%s_%dm%02d.%03ds", TAG, m, s, ms)
        }
    }

    /** 이름 꼬리의 앞 — `영상_capture_0m12.345s.jpg`. 다른 새 파일(`_crop`·`_clip`·`_audio`)처럼 영어로 둔다. */
    const val TAG = "capture"

    /** 저장 형식. 영상의 프레임은 이미 손실 압축을 거친 것이라 PNG 로 쓰면 크기만 서너 배다 — JPEG 95(잘라 저장과 같은 품질). */
    const val EXTENSION = "jpg"
}
