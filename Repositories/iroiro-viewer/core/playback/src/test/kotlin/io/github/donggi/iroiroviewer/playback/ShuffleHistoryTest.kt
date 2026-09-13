package io.github.donggi.iroiroviewer.playback

import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 섞어 듣기 — **누를 때마다 새로 뽑되 방금 들은 것은 빼고.**
 *
 * 씨앗을 고정해 무작위를 시험 안으로 들여온다. 실제로 시험하는 것은 '무엇이 뽑혔나' 가
 * 아니라 **'뽑히면 안 되는 것이 뽑히지 않았나'** 다.
 */
class ShuffleHistoryTest {

    private fun ids(n: Int) = (0 until n).map { "id$it" }

    @Test
    fun 지금_듣는_것은_뽑지_않는다() {
        val h = ShuffleHistory()
        val list = ids(5)
        val r = Random(1)
        repeat(50) {
            val at = GestureMathSeed.pick(r, 5)
            assertNotEquals(at, h.nextIndex(list, at, r), "지금 자리가 다시 뽑혔다")
        }
    }

    /**
     * 한 바퀴를 도는 동안 **같은 것이 두 번 나오지 않는다.**
     *
     * 호출 순서를 프로덕션(`PlaybackConnection.next`)과 똑같이 맞춘다 — **지금 것을 기억한
     * 뒤에 뽑는다.** 순서가 어긋나면 시험이 없는 결함을 잡았다고 말한다(처음에 그랬다).
     */
    @Test
    fun 한_바퀴는_겹치지_않는다() {
        val h = ShuffleHistory()
        val list = ids(6)
        var at = 0
        val seen = linkedSetOf(list[at])
        repeat(5) { i ->
            h.remember(list[at])
            at = h.nextIndex(list, at, Random(i.toLong()))
            assertTrue(list[at] !in seen, "이미 들은 것이 다시 나왔다: ${list[at]}")
            seen.add(list[at])
        }
        assertEquals(6, seen.size, "여섯을 다 돌아야 한다")
    }

    /** 다 들으면 이력을 비우고 다시 시작하되, **그 순간에도 같은 곡을 잇지 않는다.** */
    @Test
    fun 다_들으면_비우고_다시_시작한다() {
        val h = ShuffleHistory()
        val list = ids(3)
        var at = 0
        repeat(20) { i ->
            h.remember(list[at])
            val next = h.nextIndex(list, at, Random(i.toLong()))
            assertNotEquals(at, next, "$i 번째에 같은 곡이 이어졌다")
            at = next
        }
    }

    @Test
    fun 항목이_하나면_뽑을_것이_없다() {
        val h = ShuffleHistory()
        assertEquals(-1, h.nextIndex(ids(1), 0, Random(0)))
        assertEquals(-1, h.nextIndex(emptyList(), 0, Random(0)))
    }

    @Test
    fun 비우면_처음부터() {
        val h = ShuffleHistory()
        val list = ids(4)
        h.remember(list[0]); h.remember(list[1])
        assertEquals(2, h.size())
        h.clear()
        assertEquals(0, h.size())
    }

    /** 큐 밖의 자리(-1)를 줘도 죽지 않는다 — 큐를 갈아 끼우는 사이에 올 수 있다. */
    @Test
    fun 자리가_큐_밖이어도_죽지_않는다() {
        val h = ShuffleHistory()
        val at = h.nextIndex(ids(4), -1, Random(0))
        assertTrue(at in 0..3)
    }
}

private object GestureMathSeed {
    fun pick(r: Random, n: Int) = r.nextInt(n)
}
