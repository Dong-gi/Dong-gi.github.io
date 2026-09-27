package io.github.donggi.iroiroviewer.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 배속 눈금.
 *
 * **0 이 새는 길이 없다는 것**이 이 파일이 지키는 것이다 —
 * `PlaybackParameters(float)` 의 생성자가 `speed > 0` 을 검사하고, 어기면 메인
 * 스레드에서 앱이 죽는다.
 */
class SpeedStepsTest {

    @Test
    fun 눈금은_오름차순이고_보통_속도를_포함한다() {
        assertEquals(SpeedSteps.STEPS.sorted(), SpeedSteps.STEPS)
        assertTrue(SpeedSteps.NORMAL in SpeedSteps.STEPS)
        assertTrue(SpeedSteps.STEPS.all { it > 0f }, "0 이하가 눈금에 들면 앱이 죽는다")
    }

    @Test
    fun 이상한_값은_보통_속도로_떨어진다() {
        assertEquals(SpeedSteps.NORMAL, SpeedSteps.nearest(0f))
        assertEquals(SpeedSteps.NORMAL, SpeedSteps.nearest(-2f))
        assertEquals(SpeedSteps.NORMAL, SpeedSteps.nearest(Float.NaN))
        assertEquals(SpeedSteps.NORMAL, SpeedSteps.nearest(Float.POSITIVE_INFINITY))
    }

    @Test
    fun 가장_가까운_눈금을_고른다() {
        assertEquals(1.25f, SpeedSteps.nearest(1.3f))
        assertEquals(1.0f, SpeedSteps.nearest(1.04f))
        assertEquals(2.0f, SpeedSteps.nearest(5.0f), "눈금 밖은 가장 가까운 끝으로")
        assertEquals(0.5f, SpeedSteps.nearest(0.1f))
    }

    @Test
    fun 표기는_소수점_아래를_떼지_않는다() {
        // 떼면 `1×` 와 `1.25×` 의 폭이 달라 단추가 눌릴 때마다 옆이 밀린다.
        assertEquals("1.0×", SpeedSteps.text(1.0f))
        assertEquals("0.75×", SpeedSteps.text(0.75f))
        assertEquals("1.25×", SpeedSteps.text(1.25f))
        assertEquals("1.5×", SpeedSteps.text(1.5f))
        assertEquals("2.0×", SpeedSteps.text(2.0f))
        assertEquals("0.5×", SpeedSteps.text(0.5f))
    }

    @Test
    fun 보통_속도인지_판정한다() {
        assertTrue(SpeedSteps.isNormal(1.0f))
        assertTrue(SpeedSteps.isNormal(1.01f), "눈금으로 떨어진 뒤 판정한다")
        assertFalse(SpeedSteps.isNormal(1.5f))
    }
}
