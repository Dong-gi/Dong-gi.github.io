package io.github.donggi.iroiroviewer.ui.image

import io.github.donggi.iroiroviewer.safety.AnimationLimits
import io.github.donggi.iroiroviewer.safety.ImageLimits

/**
 * 움직이는 그림을 **어떻게 보여 줄 것인가.** 순수 함수다 — 이미지 뷰어(14단계)가 쓴다. 확대 정책은
 * `ZoomablePainter` 에 적었다(정지 그림과 같은 제스처, 선명화 조각 없음).
 *
 * 답은 둘뿐이다. 틀거나([Plan.Animate], 표본과 함께), 첫 장면만 보여 주되 **왜 그런지를 함께**
 * 준다([Plan.Still]). 까닭을 싣는 것이 요점이다 — `AnimationLimits` 가 적어 둔 약속('거절하면
 * 첫 장면만 보여 주고 그렇게 말한다')과, 6단계 감사가 찾은 결함('첫 장면에서 멈춘 채 아무 말도
 * 하지 않는다')이 같은 자리다.
 *
 * ## 만화 뷰어와 갈리는 곳 — 흐리게라도 튼다
 *
 * 만화 뷰어는 원본 치수로 `AnimationLimits.canAnimate` 를 물어 넘으면 정지로 둔다. 만화책 안의
 * GIF 는 드물고 쪽은 글을 읽는 그림이라 그 선택이 맞다. 이미지 뷰어에서 GIF·애니WebP 를 연 사람은
 * **움직임을 보려고** 연 것이므로, 예산이 막으면 표본을 [MAX_EXTRA_HALVINGS] 번까지 더 키워
 * 프레임 버퍼 셋이 예산 안에 들게 한다. 예산을 넘기는 것이 아니라 **예산 안에서 해상도를 내준다.**
 * 그래도 안 들면(힙이 아주 작은 기기) 첫 장면과 [StillReason.TOO_LARGE] 다.
 *
 * ## 무엇이 예산에 묶이는가
 *
 * `canAnimate` 는 살아 있는 장 넷 가운데 이웃 셋의 몫(`baseBytes` 셋)을 빼고 남은 자리에 버퍼 셋을
 * 넣는다. 폰 예산(장 넷)에서 그 자리는 바닥층 **한 장**이다 — 그래서 움직이는 장 하나는 **화면 크기
 * 비트맵 한 장보다 비싸지 않다.** 만화는 이웃도 한 장 몫(`pageCap`)으로 줄여 뜨므로 합이 `liveCap` 안이다.
 * 이미지 뷰어의 정지 이웃은 그렇게 줄이지 않는다(사진은 원본 그대로 뜰 수 있다 — `ImageIo.decodeFitted`
 * 의 `capBytes = 0`). 그러니 이미지 뷰어에서 이 계획이 약속하는 것은 '합이 예산 안' 이 아니라 **'움직이는
 * 장 하나가 화면 크기 비트맵 한 장(`baseBytes`, 폰에서 9.9 MiB)을 넘지 않는다'** 이다 — 12MP 사진 한 장이
 * 원본 그대로 뜰 때(45.8 MiB)보다 작다. 정지 장의 상한은 6단계부터의 것 그대로다.
 */
object AnimationPlan {

    /** 첫 장면만 보여 주는 까닭. 화면이 이것을 문장으로 말한다. */
    enum class StillReason {
        /** 움직이는 PNG. 플랫폼이 첫 장면만 준다(`ImageFormats.isApng`). */
        APNG,

        /** 움직이는 그림인데 프레임 버퍼가 예산에 들지 않는다. */
        TOO_LARGE,

        /**
         * 틀기로 했는데 움직이는 그림으로 열지 못했다(메모리 부족·깨진 프레임). [plan] 은 이것을 내지
         * 않는다 — 디코딩한 뒤에 화면이 정한다. 말하지 않으면 멈춘 그림이 정지 그림인 척한다.
         */
        DECODE_FAILED,
    }

    sealed interface Plan {
        /** [sample] 로 줄여 움직이게 튼다. */
        data class Animate(val sample: Int) : Plan

        /** 정지 한 장. [reason] 이 null 이면 처음부터 정지 그림이다 — 말할 것이 없다. */
        data class Still(val reason: StillReason?) : Plan
    }

    /**
     * @param isAnimated 플랫폼이 움직이는 그림으로 여는가(`ImageProbe.isAnimated`).
     * @param isApng 움직이는 PNG 인가. 플랫폼은 그것을 정지로 준다.
     * @param width·[height] 원본 화소.
     * @param targetLongest 바닥층의 긴 변 목표(화면의 긴 변).
     */
    fun plan(
        isAnimated: Boolean,
        isApng: Boolean,
        width: Int,
        height: Int,
        targetLongest: Int,
        budget: ImageLimits.Budget,
    ): Plan {
        if (isAnimated) {
            val sample = sampleFor(width, height, targetLongest, budget)
            return if (sample != null) Plan.Animate(sample) else Plan.Still(StillReason.TOO_LARGE)
        }
        return Plan.Still(if (isApng) StillReason.APNG else null)
    }

    /**
     * 프레임 버퍼 셋이 예산에 드는 표본. 들지 않으면 null.
     *
     * 시작은 정지 쪽과 같은 규칙이다(`ImageLimits.sampleForBudget` + 한 장 상한 `pageCap`). 거기서
     * `canAnimate` 가 허락할 때까지 두 배씩 키운다.
     */
    fun sampleFor(width: Int, height: Int, targetLongest: Int, budget: ImageLimits.Budget): Int? {
        if (width <= 0 || height <= 0 || targetLongest <= 0) return null
        var sample = ImageLimits.sampleForBudget(width, height, targetLongest, ImageLimits.pageCap(budget))
        repeat(MAX_EXTRA_HALVINGS + 1) {
            val w = (width + sample - 1) / sample
            val h = (height + sample - 1) / sample
            if (AnimationLimits.canAnimate(w, h, budget)) return sample
            sample *= 2
        }
        return null
    }

    /**
     * 정지 쪽 규칙보다 몇 번 더 반으로 줄여도 되는가. 한 번이면 넓이가 1/4 이라 보통 힙에서는 그것으로
     * 늘 든다(한 장 상한 `pageCap` 이 바닥층 한 장이고 버퍼는 셋이다). 두 번째는 힙이 작은 기기를
     * 위한 여유이고, 그 너머는 알아볼 수 없는 크기라 정지로 둔다.
     */
    const val MAX_EXTRA_HALVINGS = 2
}
