package io.github.donggi.iroiroviewer.safety

/**
 * 움직이는 그림을 **틀어도 되는가.** 순수 함수다.
 *
 * `AnimatedImageDrawable` 은 우리가 볼 수 없는 곳에서 프레임 버퍼를 잡는다. 그래서
 * 상한은 "지금 몇 바이트인가" 가 아니라 **"틀면 몇 바이트가 될 것인가"** 로 미리
 * 재야 하고, 그 판정을 화면이 아니라 여기 한 곳에서 한다.
 */
object AnimationLimits {

    /**
     * 프레임 버퍼를 몇 장으로 세는가.
     *
     * `AnimatedImageDrawable` 은 지금 그리는 장, 다음에 그릴 장, 그리고 합성에 쓰는
     * 이전 장을 든다(GIF 의 `restore to previous` 처분이 그것을 요구한다). 정확한
     * 수는 구현이 정하고 문서에 없다 — **모르는 값을 낙관적으로 잡지 않는다.**
     */
    const val FRAME_BUFFERS = 3

    /** 이 크기의 프레임 한 장이 몇 바이트인가. */
    fun frameBytes(width: Int, height: Int): Long =
        width.toLong().coerceAtLeast(0) * height.toLong().coerceAtLeast(0) *
            ImageLimits.BYTES_PER_PIXEL

    /**
     * 이 그림을 애니메이션으로 틀어도 되는가.
     *
     * **분모가 [ImageLimits.MAX_BITMAP_BYTES] 가 아니다.** 그 값은 `Canvas` 가 그릴 수
     * 있는 *한 장*의 상한이지 앱이 동시에 들어도 되는 총량이 아니다. 만화에서는
     * 움직이는 쪽 하나 옆에 정지 이웃 쪽들이 이미 살아 있으므로, 그 몫을 빼고 남은
     * 자리로 잰다.
     *
     * 거절하면 화면이 첫 장면만 보여 주고 **그렇게 말한다.** 조용히 멈춘 그림을
     * 정지 그림인 척 내놓지 않는다.
     */
    fun canAnimate(width: Int, height: Int, budget: ImageLimits.Budget): Boolean {
        if (width <= 0 || height <= 0) return false
        val neighbours = budget.baseBytes * (budget.livePages - 1).coerceAtLeast(0)
        val room = (budget.liveCap - neighbours).coerceAtLeast(0L)
        val need = frameBytes(width, height) * FRAME_BUFFERS
        return need > 0 && need <= minOf(room, budget.maxBitmapBytes)
    }
}
