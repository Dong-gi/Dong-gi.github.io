package io.github.donggi.iroiroviewer.safety

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 움직이는 그림을 틀어도 되는가.
 *
 * 6단계가 애니메이션을 미룬 이유 가운데 하나가 '프레임 버퍼가 얼마나 되는지 모른다'
 * 였다. 모르는 값을 낙관적으로 잡지 않는다는 판단을 시험으로 박는다.
 */
class AnimationLimitsTest {

    private val phone = ImageLimits.budgetOf(1080, 2400, 256)
    private val small = ImageLimits.budgetOf(1080, 2400, 64)

    @Test
    fun `작은 GIF 는 튼다`() {
        assertTrue(AnimationLimits.canAnimate(320, 240, phone))
        assertTrue(AnimationLimits.canAnimate(640, 480, phone))
    }

    /** **분모가 절대 상한이 아니라 예산이다.** 이 시험이 이 파일의 이유다. */
    @Test
    fun `절대 상한 안이어도 예산 밖이면 거절한다`() {
        // 2000x2000 한 장은 16 MB, 버퍼 셋이면 48 MB.
        // MAX_BITMAP_BYTES(100 MiB) 안이지만 이웃 쪽을 뺀 남은 자리보다 크다.
        val need = AnimationLimits.frameBytes(2000, 2000) * AnimationLimits.FRAME_BUFFERS
        assertTrue(need < ImageLimits.MAX_BITMAP_BYTES, "전제: 절대 상한 안이다")
        val neighbours = phone.baseBytes * (phone.livePages - 1)
        assertTrue(need > phone.liveCap - neighbours, "전제: 예산 밖이다")
        assertFalse(AnimationLimits.canAnimate(2000, 2000, phone))
    }

    @Test
    fun `힙이 작으면 더 일찍 거절한다`() {
        assertTrue(AnimationLimits.canAnimate(320, 240, small))
        assertFalse(AnimationLimits.canAnimate(1600, 1200, small))
    }

    @Test
    fun `이상한 크기는 거절한다`() {
        assertFalse(AnimationLimits.canAnimate(0, 100, phone))
        assertFalse(AnimationLimits.canAnimate(-1, -1, phone))
    }

    @Test
    fun `프레임 바이트는 화소 곱하기 4다`() {
        assertTrue(AnimationLimits.frameBytes(100, 200) == 100L * 200 * 4)
    }
}
