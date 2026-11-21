package io.github.donggi.iroiroviewer.player

/**
 * 자막의 크기·높이를 **눈금으로** 고른다(배속의 `SpeedSteps` 와 같은 까닭 — 기본으로 정확히 돌아올 수 있어야 한다).
 * 눈금 표는 설정이 든다(`AppPreferences.SUBTITLE_SIZES_SP`·`SUBTITLE_LIFTS_PERCENT`).
 */
internal object SubtitleStyle {

    /**
     * [steps] 에서 [current] 의 [delta] 칸 옆. 끝에서 멈춘다. [current] 가 눈금에 없으면 가장 가까운 칸에서 센다
     * (설정은 눈금 밖을 기본값으로 읽지만, 화면이 든 값이 눈금 밖이어도 단추가 죽지 않게).
     */
    fun step(steps: List<Int>, current: Int, delta: Int): Int {
        if (steps.isEmpty()) return current
        val at = steps.indexOf(current).takeIf { it >= 0 }
            ?: steps.indices.minBy { kotlin.math.abs(steps[it] - current) }
        return steps[(at + delta).coerceIn(0, steps.lastIndex)]
    }

    fun canStep(steps: List<Int>, current: Int, delta: Int): Boolean = step(steps, current, delta) != current

    /** 싱크 값의 글 — `+0.3`·`−1.2`·`0.0`(초, 0.1초 자리까지). 기기 로캘과 상관없이 점이다. */
    fun syncText(ms: Long): String {
        val sign = when {
            ms > 0 -> "+"
            ms < 0 -> "−"
            else -> ""
        }
        val a = kotlin.math.abs(ms)
        return "$sign${a / 1000}.${a % 1000 / 100}"
    }
}
