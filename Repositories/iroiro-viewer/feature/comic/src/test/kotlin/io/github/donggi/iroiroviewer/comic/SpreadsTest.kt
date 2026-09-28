package io.github.donggi.iroiroviewer.comic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 두 쪽 보기의 짝과 모양(14단계).
 *
 * 화면(페이저)은 펼침 번호로 돌고 VM·이어보기·슬라이더는 쪽 번호로 돈다. 둘을 잇는 함수가 틀리면 '3쪽으로 가기' 가
 * 4쪽을 보이거나, 저장된 쪽이 넘길 때마다 한 칸씩 밀린다 — 화면 캡처로는 판정하기 어려운 종류라(함정 표) 여기서 박는다.
 */
class SpreadsTest {

    @Test
    fun `표지는 혼자이고 본문은 둘씩 묶인다`() {
        val n = 7
        val spreads = (0 until Spreads.count(n, twoUp = true)).map { Spreads.pagesOf(it, n, twoUp = true) }
        assertEquals(listOf(listOf(0), listOf(1, 2), listOf(3, 4), listOf(5, 6)), spreads)
    }

    @Test
    fun `쪽 수가 짝수면 마지막 쪽도 혼자 남는다`() {
        val n = 6
        val spreads = (0 until Spreads.count(n, twoUp = true)).map { Spreads.pagesOf(it, n, twoUp = true) }
        assertEquals(listOf(listOf(0), listOf(1, 2), listOf(3, 4), listOf(5)), spreads)
    }

    /** 모든 쪽이 정확히 한 펼침에 들고, [Spreads.spreadOf] 가 그 펼침을 가리킨다. */
    @Test
    fun `쪽마다 든 펼침이 하나이고 spreadOf 가 그것을 가리킨다`() {
        for (n in 0..40) {
            for (twoUp in listOf(false, true)) {
                val count = Spreads.count(n, twoUp)
                val seen = IntArray(n)
                for (s in 0 until count) {
                    val pages = Spreads.pagesOf(s, n, twoUp)
                    assertTrue(pages.isNotEmpty(), "n=$n twoUp=$twoUp 펼침 $s 가 비었다")
                    for (p in pages) {
                        seen[p]++
                        assertEquals(s, Spreads.spreadOf(p, n, twoUp), "n=$n twoUp=$twoUp 쪽 $p")
                    }
                }
                assertTrue(seen.all { it == 1 }, "n=$n twoUp=$twoUp 쪽이 빠지거나 겹쳤다: ${seen.toList()}")
                assertTrue(Spreads.pagesOf(count, n, twoUp).isEmpty(), "범위 밖 펼침은 비어야 한다")
            }
        }
    }

    @Test
    fun `한 쪽 보기는 쪽과 펼침이 같다`() {
        assertEquals(5, Spreads.count(5, twoUp = false))
        assertEquals(3, Spreads.spreadOf(3, 5, twoUp = false))
        assertEquals(listOf(3), Spreads.pagesOf(3, 5, twoUp = false))
    }

    /** 이어보기가 옛 쪽 수로 저장한 값이 와도 페이저가 죽지 않는다. */
    @Test
    fun `범위 밖의 쪽은 가장 가까운 펼침으로 누른다`() {
        assertEquals(Spreads.count(10, true) - 1, Spreads.spreadOf(99, 10, twoUp = true))
        assertEquals(0, Spreads.spreadOf(-3, 10, twoUp = true))
        assertEquals(0, Spreads.spreadOf(0, 0, twoUp = true))
        assertEquals(0, Spreads.count(0, twoUp = true))
    }

    /** 오른쪽에서 왼쪽으로 읽으면 **앞 쪽이 오른쪽**이다. */
    @Test
    fun `오른쪽에서 왼쪽이면 펼침 안의 두 쪽이 뒤집힌다`() {
        assertEquals(listOf(1, 2), Spreads.visualOrder(listOf(1, 2), rightToLeft = false))
        assertEquals(listOf(2, 1), Spreads.visualOrder(listOf(1, 2), rightToLeft = true))
        assertEquals(listOf(0), Spreads.visualOrder(listOf(0), rightToLeft = true))
    }

    /**
     * **같은 펼침 안의 쪽은 그대로 둔다.** 슬라이더로 3쪽(번호 2)을 고르면 `[1,2]` 펼침이 서는데, 그때 쪽을 펼침의
     * 첫 쪽으로 되돌리면 사용자가 고른 값이 한 칸 밀리고 이어보기에도 그 값이 남는다.
     */
    @Test
    fun `펼침에 서도 그 안의 쪽이면 쪽 번호를 바꾸지 않는다`() {
        assertEquals(2, Spreads.pageAfterSettle(current = 2, spread = 1, pageCount = 10, twoUp = true))
        assertEquals(1, Spreads.pageAfterSettle(current = 1, spread = 1, pageCount = 10, twoUp = true))
        // 다른 펼침으로 넘어갔으면 그 펼침의 첫 쪽이다.
        assertEquals(3, Spreads.pageAfterSettle(current = 2, spread = 2, pageCount = 10, twoUp = true))
        assertEquals(0, Spreads.pageAfterSettle(current = 2, spread = 0, pageCount = 10, twoUp = true))
        // 한 쪽 보기에서는 펼침이 곧 쪽이다.
        assertEquals(4, Spreads.pageAfterSettle(current = 2, spread = 4, pageCount = 10, twoUp = false))
    }

    @Test
    fun `자동은 가로 화면에서만 두 쪽이다`() {
        assertTrue(Spreads.twoUp(PageLayout.AUTO, 2400, 1080))
        assertEquals(false, Spreads.twoUp(PageLayout.AUTO, 1080, 2400))
        assertEquals(false, Spreads.twoUp(PageLayout.AUTO, 1000, 1000))
        assertTrue(Spreads.twoUp(PageLayout.DOUBLE, 1080, 2400))
        assertEquals(false, Spreads.twoUp(PageLayout.SINGLE, 2400, 1080))
    }

    // ---- 모양 ----------------------------------------------------------------------------------

    @Test
    fun `같은 크기의 두 쪽은 폭이 두 배이고 반씩 나눈다`() {
        val f = SpreadMath.frameOf(700, 1000, 700, 1000)!!
        assertEquals(1400, f.contentWidth)
        assertEquals(1000, f.contentHeight)
        assertEquals(0.5f, f.leftFraction, 1e-6f)
    }

    /** 높이가 다르면 키 큰 쪽에 맞춘다 — 두 쪽 모두 가로세로 비를 지킨다. */
    @Test
    fun `높이가 다른 두 쪽은 같은 높이로 맞춰 비를 지킨다`() {
        // 왼쪽 600×900(2:3), 오른쪽 400×1200(1:3). 공통 높이 1200 → 왼쪽 폭 800, 오른쪽 400.
        val f = SpreadMath.frameOf(600, 900, 400, 1200)!!
        assertEquals(1200, f.contentHeight)
        assertEquals(1200, f.contentWidth)
        assertEquals(800f / 1200f, f.leftFraction, 1e-6f)
    }

    /** 이미 양면으로 스캔한 넓은 쪽이 짝에 들면 그 쪽이 폭을 더 가져간다(잘리지 않는다). */
    @Test
    fun `넓은 쪽은 폭을 더 가져간다`() {
        val f = SpreadMath.frameOf(2000, 1000, 700, 1000)!!
        assertEquals(2700, f.contentWidth)
        assertTrue(f.leftFraction > 0.7f)
    }

    @Test
    fun `크기를 모르면 모양도 없다`() {
        assertNull(SpreadMath.frameOf(0, 100, 100, 100))
        assertNull(SpreadMath.frameOf(100, 100, 100, -1))
        assertEquals(0, SpreadMath.originalWidthOf(0, 1, 1, 1))
    }

    /** 최대 배율은 원본으로 잰다 — 같은 규칙으로 맞춘 원본 두 쪽의 폭. */
    @Test
    fun `원본 폭은 원본 두 쪽을 같은 규칙으로 맞춘 폭이다`() {
        assertEquals(2800, SpreadMath.originalWidthOf(1400, 2000, 1400, 2000))
        assertEquals(2400, SpreadMath.originalWidthOf(1200, 1800, 800, 2400))
    }

    /**
     * 쪽 하나를 '가운데 놓인 한 장' 으로 옮기는 보정. 선명화 조각이 엉뚱한 자리를 뜨면 확대한 쪽에 옆 쪽의 그림이
     * 얹힌다 — 좌표 약속(원점이 뷰포트 중앙)으로 손 계산한 값과 견준다.
     */
    @Test
    fun `쪽의 중심은 펼침 중심에서 반쪽씩 비켜 있다`() {
        // 맞춤 폭 1000, 반씩: 왼쪽 쪽의 중심은 -250, 오른쪽은 +250.
        assertEquals(-250f, SpreadMath.pageCenterOffset(1000f, 0.5f, left = true), 1e-4f)
        assertEquals(250f, SpreadMath.pageCenterOffset(1000f, 0.5f, left = false), 1e-4f)
        // 왼쪽이 80% 면 왼쪽 중심은 -100(= 400 - 500), 오른쪽 중심은 +400(= 900 - 500).
        assertEquals(-100f, SpreadMath.pageCenterOffset(1000f, 0.8f, left = true), 1e-4f)
        assertEquals(400f, SpreadMath.pageCenterOffset(1000f, 0.8f, left = false), 1e-4f)
    }

    /**
     * **뛰어든 자리에서 앞 칸 전체가 창 안에 든다.** 화면은 지금 펼침의 어느 쪽이든 먼저 청할 수 있다(오른쪽에서 왼쪽이면
     * 화면 왼쪽 = 뒤 쪽). 그 쪽에서 [Spreads.lookBehind] 만큼 물러선 자리가 앞 칸의 첫 쪽보다 뒤면 앞 칸을 뜨느라 패스가
     * 한 번 더 돈다 — 9단계가 한 쪽 보기에서 잡은 '15쪽으로 뛰는 데 패스 둘' 이 두 쪽 보기에서 되살아난다.
     */
    @Test
    fun `뛰어든 자리에서 물러서는 쪽 수가 앞 칸 전체를 덮는다`() {
        for (n in 1..40) {
            for (twoUp in listOf(false, true)) {
                val back = Spreads.lookBehind(twoUp)
                for (s in 1 until Spreads.count(n, twoUp)) {
                    val before = Spreads.pagesOf(s - 1, n, twoUp)
                    for (first in Spreads.pagesOf(s, n, twoUp)) {
                        assertTrue(
                            before.all { it >= first - back },
                            "n=$n twoUp=$twoUp 펼침 $s 의 $first 에서 $back 쪽 물러서도 앞 칸 $before 이 다 들지 않는다",
                        )
                    }
                }
            }
        }
        assertEquals(1, Spreads.lookBehind(false), "한 쪽 보기는 9단계 그대로")
    }

    /**
     * **반쪽은 자리에 맞춘 크기 그대로 뜬다.** 2의 거듭제곱 표본은 원본이거나 절반뿐이라, 가로 폰의 반쪽 몫(`pageCap / 2`)
     * 에 들려면 흔한 1200×1800 쪽이 600×900 으로 떠서 자리(720×1080)에 1.2배 늘어났다(검토가 셈으로 잡은 흐림).
     */
    @Test
    fun `반쪽은 자리에 맞춘 크기로 뜨고 몫 안에 든다`() {
        // 1080×2400 폰을 눕힌 반쪽 1200×1080, 힙 192MB 의 반쪽 몫.
        val budget = io.github.donggi.iroiroviewer.safety.ImageLimits.budgetOf(2400, 1080, 192)
        val cap = io.github.donggi.iroiroviewer.safety.ImageLimits.pageCap(budget) / 2
        assertEquals(5_184_000L, cap)
        val size = SpreadMath.halfSize(1200, 1800, 1200, 1080, cap)!!
        assertEquals(listOf(720, 1080), size.toList())
        assertTrue(size[0].toLong() * size[1] * 4 <= cap)
        // 대조 — 표본으로 뜨면 자리보다 작다(늘어나 보인다). 이 차이가 고친 까닭이다.
        val sample = io.github.donggi.iroiroviewer.safety.ImageLimits.sampleForBudget(1200, 1800, 1080, cap)
        assertTrue(1800 / sample < 1080, "표본 $sample 로도 자리를 채운다면 이 시험의 전제가 틀렸다")
        // 태블릿을 눕힌 반쪽 1280×1600 — 표본이면 1.8배 늘어나던 자리.
        assertEquals(listOf(1067, 1600), SpreadMath.halfSize(1200, 1800, 1280, 1600, 8_192_000L)!!.toList())
    }

    @Test
    fun `반쪽은 키우지 않고 몫을 넘으면 비를 지켜 줄인다`() {
        // 작은 쪽은 원본 크기 그대로(그릴 때 늘린다 — 뜬다고 선명해지지 않는다).
        assertEquals(listOf(600, 900), SpreadMath.halfSize(600, 900, 1200, 1080, 5_184_000L)!!.toList())
        // 힙이 작은 기기: 자리에 맞추면 720×1080 = 3,110,400 B 인데 몫이 1,000,000 B.
        val small = SpreadMath.halfSize(1200, 1800, 1200, 1080, 1_000_000L)!!
        assertTrue(small[0].toLong() * small[1] * 4 <= 1_000_000L, "몫을 넘었다: ${small.toList()}")
        assertEquals(1200.0 / 1800.0, small[0].toDouble() / small[1], 0.01)
        assertTrue(small[0] >= 400, "필요 이상으로 줄였다: ${small.toList()}")
        // 몫이 0 이하면 상한을 걸지 않는다(한 장짜리 `decodeFitted` 의 capBytes 와 같은 약속).
        assertEquals(listOf(720, 1080), SpreadMath.halfSize(1200, 1800, 1200, 1080, 0L)!!.toList())
        assertNull(SpreadMath.halfSize(0, 1800, 1200, 1080, 1L))
        assertNull(SpreadMath.halfSize(1200, 1800, 0, 1080, 1L))
    }

    @Test
    fun `반쪽 자리에 맞춘 긴 변`() {
        // 가로 폰 2400×1080 의 반쪽 1200×1080 에 2:3 쪽 → 높이에 걸려 1080.
        assertEquals(1080, SpreadMath.slotTarget(1400, 2100, 1200, 1080))
        // 넓은 쪽 3000×1000 → 폭에 걸려 1200.
        assertEquals(1200, SpreadMath.slotTarget(3000, 1000, 1200, 1080))
        // 모르면 자리의 긴 변.
        assertEquals(1200, SpreadMath.slotTarget(0, 0, 1200, 1080))
    }
}
