package io.github.donggi.iroirobrowser.tether.core

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 알림·기록 묶기 — 바뀜 알림은 250ms 안의 것을 한 번으로([ChangeCoalescer]), 같은 까닭의 기록은 초당 한 줄과 묶인 수로
 * ([LogThrottle]). 시계와 일 돌리기는 손으로 한다.
 */
class NoticeRateTest {

    /** 손으로 돌리는 시계와 일 목록. */
    private class ManualClock {
        var now = 1_000L
        private val tasks = ArrayList<Pair<Long, () -> Unit>>()

        fun schedule(delayMs: Long, task: () -> Unit) {
            tasks += (now + delayMs) to task
        }

        /** [ms] 를 흘리며 때가 된 일을 차례로 돌린다. */
        fun advance(ms: Long) {
            val end = now + ms
            while (true) {
                val next = tasks.filter { it.first <= end }.minByOrNull { it.first } ?: break
                tasks.remove(next)
                now = maxOf(now, next.first)
                next.second()
            }
            now = end
        }

        val pending: Int get() = tasks.size
    }

    @Test
    fun 몰린_바뀜은_창마다_한_번만_보낸다() {
        val clock = ManualClock()
        var dispatched = 0
        val coalescer = ChangeCoalescer(250, { clock.now }, clock::schedule) { dispatched++ }
        // 거절이 몰린다 — 1ms 마다 1,000번.
        repeat(1000) {
            coalescer.changed()
            clock.advance(1)
        }
        clock.advance(500)
        // 처음 하나는 곧바로, 그 뒤는 250ms 창마다 하나 — 1초 남짓이면 다섯 번 안팎(예전에는 1,000번).
        assertEquals(5, dispatched)
        assertEquals(0, clock.pending)
    }

    @Test
    fun 쉬던_뒤의_바뀜은_곧바로_보내고_합친_것도_마지막_상태를_놓치지_않는다() {
        val clock = ManualClock()
        var state = 0
        val seen = ArrayList<Int>()
        val coalescer = ChangeCoalescer(250, { clock.now }, clock::schedule) { seen += state }
        state = 1
        coalescer.changed()
        clock.advance(0)
        assertEquals(listOf(1), seen, "처음은 곧바로")
        state = 2
        coalescer.changed()
        state = 3
        coalescer.changed()
        clock.advance(249)
        assertEquals(listOf(1), seen, "창 안의 것은 창이 끝날 때")
        clock.advance(1)
        assertEquals(listOf(1, 3), seen, "합쳐도 마지막 상태를 본다")
        clock.advance(10_000)
        state = 4
        coalescer.changed()
        clock.advance(0)
        assertEquals(listOf(1, 3, 4), seen, "오래 쉰 뒤에는 곧바로")
    }

    @Test
    fun 듣개가_도는_사이에_온_바뀜은_다음_차례로_간다() {
        val clock = ManualClock()
        var dispatched = 0
        lateinit var coalescer: ChangeCoalescer
        coalescer = ChangeCoalescer(250, { clock.now }, clock::schedule) {
            dispatched++
            // 듣개 안에서 상태가 또 바뀌었다 — 잃지 않는다.
            if (dispatched == 1) coalescer.changed()
        }
        coalescer.changed()
        clock.advance(0)
        assertEquals(1, dispatched)
        clock.advance(250)
        assertEquals(2, dispatched)
    }

    @Test
    fun 같은_까닭의_기록은_초당_한_줄과_묶인_수() {
        var now = 0L
        val throttle = LogThrottle({ now })
        val lines = ArrayList<String>()
        repeat(50) { throttle.note("peer") { skipped -> lines += "peer+$skipped" } }
        throttle.note("budget") { skipped -> lines += "budget+$skipped" }
        assertEquals(listOf("peer+0", "budget+0"), lines, "까닭마다 첫 줄만")
        now += LogThrottle.DEFAULT_INTERVAL_MS - 1
        throttle.note("peer") { skipped -> lines += "peer+$skipped" }
        assertEquals(2, lines.size)
        now += 1
        throttle.note("peer") { skipped -> lines += "peer+$skipped" }
        assertEquals("peer+50", lines.last(), "그 사이 묶인 수를 붙인다")
        now += 5_000
        throttle.note("peer") { skipped -> lines += "peer+$skipped" }
        assertEquals("peer+0", lines.last())
    }
}
