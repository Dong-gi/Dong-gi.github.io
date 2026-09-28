package io.github.donggi.iroiroviewer.ui.image

import io.github.donggi.iroiroviewer.safety.AnimationLimits
import io.github.donggi.iroiroviewer.safety.ImageLimits
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 이미지 뷰어가 움직이는 그림을 **틀 것인가, 틀면 얼마로 줄일 것인가, 안 틀면 무엇이라 말할 것인가.**
 *
 * 예산표는 9단계 실측의 것이다(폰 1080×2400, 힙 등급 192MB — 한 장 10,368,000 B, 살아 있는 장 넷).
 */
class AnimationPlanTest {

    private val phone = ImageLimits.budgetOf(1080, 2400, 192)

    /** 이 표본으로 뜬 프레임 버퍼 셋이 예산 안인가 — 계획이 지켜야 하는 유일한 약속. */
    private fun fits(w: Int, h: Int, sample: Int, budget: ImageLimits.Budget = phone): Boolean =
        AnimationLimits.canAnimate((w + sample - 1) / sample, (h + sample - 1) / sample, budget)

    @Test
    fun 작은_GIF_는_원본_그대로_튼다() {
        assertEquals(AnimationPlan.Plan.Animate(1), AnimationPlan.plan(true, false, 480, 270, 2400, phone))
    }

    @Test
    fun 예산을_넘는_애니WebP_는_줄여서_튼다() {
        // 1920x1080 은 한 장이 8.3 MB 라 버퍼 셋(24.9 MB)이 남은 자리(10.4 MB)를 넘는다. 반으로 줄이면 든다.
        val p = AnimationPlan.plan(true, false, 1920, 1080, 2400, phone)
        assertEquals(AnimationPlan.Plan.Animate(2), p)
        assertTrue(fits(1920, 1080, 2))
        assertTrue(!fits(1920, 1080, 1), "원본 그대로는 들지 않아야 이 시험이 뜻을 가진다")
    }

    @Test
    fun 어떤_크기든_고른_표본은_예산_안이다() {
        for ((w, h) in listOf(1 to 1, 320 to 240, 1080 to 2400, 4000 to 3000, 4000 to 4000, 16000 to 16000, 30000 to 200)) {
            val s = assertNotNull(AnimationPlan.sampleFor(w, h, 2400, phone), "${w}x$h")
            assertTrue(fits(w, h, s), "${w}x$h 표본 $s")
            // 필요한 것보다 더 줄이지 않는다 — 반만 덜 줄여도 되면 그것을 골랐어야 한다.
            if (s > 1) assertTrue(!fits(w, h, s / 2) || s / 2 < ImageLimits.sampleForBudget(w, h, 2400, ImageLimits.pageCap(phone)), "${w}x$h 표본 $s 는 지나치다")
        }
    }

    @Test
    fun 움직이는_장_하나는_화면_크기_한_장을_넘지_않는다() {
        // 이미지 뷰어의 정지 이웃은 pageCap 으로 줄이지 않는다 — 이 계획이 지키는 것은 '움직이는 장 하나의
        // 프레임 버퍼 셋이 화면 크기 비트맵 한 장(baseBytes) 안' 이다(AnimationPlan 주석).
        for (budget in listOf(phone, ImageLimits.budgetOf(2560, 1600, 256), ImageLimits.budgetOf(1080, 2400, 96))) {
            for ((w, h) in listOf(480 to 270, 1920 to 1080, 4000 to 4000, 30000 to 200)) {
                val s = AnimationPlan.sampleFor(w, h, 2400, budget) ?: continue
                val need = AnimationLimits.frameBytes((w + s - 1) / s, (h + s - 1) / s) * AnimationLimits.FRAME_BUFFERS
                assertTrue(need <= budget.baseBytes, "${w}x$h 표본 $s: $need > ${budget.baseBytes}")
            }
        }
    }

    @Test
    fun 계획은_디코딩_실패를_내지_않는다() {
        // DECODE_FAILED 는 디코딩한 뒤 화면이 정한다. 계획이 그것을 내면 틀어 보지도 않고 포기한 것이다.
        for (animated in listOf(true, false)) for (apng in listOf(true, false)) {
            val p = AnimationPlan.plan(animated, apng, 480, 270, 2400, phone)
            assertTrue(p !is AnimationPlan.Plan.Still || p.reason != AnimationPlan.StillReason.DECODE_FAILED, "$p")
        }
    }

    @Test
    fun 예산이_허락하지_않으면_첫_장면과_까닭을_준다() {
        // 힙이 아주 작은 기기 — 살아 있는 장 둘을 들고 나면 움직이는 장의 버퍼를 둘 자리가 없다.
        val tiny = ImageLimits.budgetOf(1080, 2400, 32)
        assertNull(AnimationPlan.sampleFor(480, 270, 2400, tiny))
        assertEquals(
            AnimationPlan.Plan.Still(AnimationPlan.StillReason.TOO_LARGE),
            AnimationPlan.plan(true, false, 480, 270, 2400, tiny),
        )
    }

    @Test
    fun APNG_는_첫_장면만_보여_주고_그렇게_말한다() {
        // 플랫폼이 APNG 을 정지로 준다(isAnimated=false). 말하지 않으면 멈춘 그림이 고장으로 보인다.
        assertEquals(
            AnimationPlan.Plan.Still(AnimationPlan.StillReason.APNG),
            AnimationPlan.plan(false, true, 480, 270, 2400, phone),
        )
    }

    @Test
    fun 정지_그림은_말할_것이_없다() {
        assertEquals(AnimationPlan.Plan.Still(null), AnimationPlan.plan(false, false, 4000, 3000, 2400, phone))
    }

    @Test
    fun 치수를_모르면_틀지_않는다() {
        assertNull(AnimationPlan.sampleFor(0, 270, 2400, phone))
        assertNull(AnimationPlan.sampleFor(480, 270, 0, phone))
    }
}
