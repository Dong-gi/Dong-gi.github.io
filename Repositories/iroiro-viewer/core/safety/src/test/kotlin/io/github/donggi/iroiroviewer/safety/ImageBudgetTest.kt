package io.github.donggi.iroiroviewer.safety

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 비트맵 예산의 산수.
 *
 * **9단계가 이 함수의 첫 호출자다.** 6단계가 선언해 두고 아무도 부르지 않은 채 세 단계가
 * 지났고, 그 사이에 `liveCap` 이 예산을 넘을 수 있다는 것이 검토에서 드러났다. 쓰기 전에
 * 산수를 못 박는다 — 이 값이 틀리면 앱이 `OutOfMemoryError` 로 죽고, 그것을 에뮬레이터에서
 * 재현해 고치는 일은 느리고 불확실하다.
 */
class ImageBudgetTest {

    /** 폰 한 대의 현실적인 값. 1080×2400, 힙 등급 256 MB. */
    private val phone = ImageLimits.budgetOf(1080, 2400, 256)

    @Test
    fun `한 장은 화면 화소 곱하기 4다`() {
        assertEquals(1080L * 2400 * 4, phone.baseBytes)
    }

    /** **이 시험이 이 파일의 이유다.** 상한이 예산을 넘으면 안 된다. */
    @Test
    fun `살아 있는 양은 언제나 예산 안이다`() {
        for (mb in listOf(32, 48, 64, 96, 128, 192, 256, 384, 512)) {
            for ((w, h) in listOf(720 to 1280, 1080 to 2400, 1440 to 3200, 2560 to 1600)) {
                val b = ImageLimits.budgetOf(w, h, mb)
                val allowance = mb.toLong() * 1024 * 1024 / 4
                assertTrue(
                    b.liveCap <= maxOf(allowance, b.baseBytes),
                    "${w}x$h/${mb}MB: liveCap=${b.liveCap} > 예산=$allowance",
                )
                assertTrue(
                    b.liveCap + b.detailCap <= maxOf(allowance, b.baseBytes),
                    "${w}x$h/${mb}MB: 바닥+상세가 예산을 넘는다",
                )
            }
        }
    }

    /** 힙이 넉넉하면 네 장을 든다 — 앞뒤 한 장씩에 넘기는 도중의 두 장이다. */
    @Test
    fun `힙이 넉넉하면 네 장이다`() {
        assertEquals(ImageLimits.LIVE_PAGES, phone.livePages)
        assertEquals(phone.baseBytes * ImageLimits.LIVE_PAGES, phone.liveCap)
    }

    /**
     * 힙이 작으면 장수가 줄지만 **두 장 아래로는 안 내려간다.**
     *
     * 한 장만 들면 넘길 때마다 빈 화면이 보인다 — 아끼는 것이 아니라 못 쓰는 것이다.
     */
    @Test
    fun `힙이 작으면 장수가 줄되 둘 아래로는 안 간다`() {
        val small = ImageLimits.budgetOf(1080, 2400, 64) // 예산 16 MB, 한 장 10.4 MB
        assertEquals(ImageLimits.MIN_LIVE_PAGES, small.livePages)
        assertTrue(small.detailCap == 0L, "선명화층은 켜지지 않아야 한다")

        val mid = ImageLimits.budgetOf(1080, 2400, 128) // 예산 32 MB → 3장
        assertEquals(3, mid.livePages)
    }

    @Test
    fun `장수는 언제나 둘과 넷 사이다`() {
        for (mb in 16..1024 step 16) {
            val b = ImageLimits.budgetOf(1080, 2400, mb)
            assertTrue(b.livePages in ImageLimits.MIN_LIVE_PAGES..ImageLimits.LIVE_PAGES, "${mb}MB")
        }
    }

    /** 화면이 0 이어도 나누기가 터지지 않는다. */
    @Test
    fun `이상한 입력에서 터지지 않는다`() {
        val zero = ImageLimits.budgetOf(0, 0, 0)
        assertTrue(zero.baseBytes >= 1)
        assertTrue(zero.livePages >= ImageLimits.MIN_LIVE_PAGES)
    }

    // ---- 표본 크기 --------------------------------------------------------------

    @Test
    fun `표본은 2의 거듭제곱이다`() {
        for (w in listOf(800, 1200, 4000, 12000)) {
            val s = ImageLimits.sampleFor(w, w, 2400)
            assertTrue(s > 0 && (s and (s - 1)) == 0, "$w -> $s")
        }
    }

    /**
     * **거듭제곱이라 최대 2배까지 크게 뜬다.** 12MP 만화 쪽이 그 경우다.
     *
     * 이 시험은 현재 동작을 못 박는 것이지 바람직하다고 말하는 것이 아니다 —
     * 만화 뷰어는 이 초과분 때문에 장수를 예산에서 끌어내야 한다.
     */
    @Test
    fun `거듭제곱 표본은 목표보다 크게 뜬다`() {
        // 4000x3000 을 긴 변 2400 으로: 4000/2 = 2000 < 2400 이라 sample 이 1 에 머문다.
        assertEquals(1, ImageLimits.sampleFor(4000, 3000, 2400))
        assertEquals(4000L * 3000 * 4, ImageLimits.bytesAt(4000, 3000, 1))
    }

    /** 그려 낼 수 없는 크기는 표본을 더 키워서라도 줄인다. */
    @Test
    fun `절대 상한을 넘기면 더 줄인다`() {
        val s = ImageLimits.sampleFor(20000, 20000, 2400)
        assertTrue(ImageLimits.bytesAt(20000, 20000, s) <= ImageLimits.MAX_BITMAP_BYTES)
    }

    // ---- 예산으로 자른 표본 -------------------------------------------------------

    /**
     * **이 시험이 9단계 메모리 설계의 뼈대다.**
     *
     * 만화는 큰 쪽을 네 장 동시에 든다. 목표 해상도만 보면 한 장이 예산의 네 배가 되고,
     * 네 장이면 열여섯 배다. 바이트를 기준으로 자르면 흐려지되 죽지 않는다.
     */
    @Test
    fun `예산을 넘는 쪽은 더 줄여서 뜬다`() {
        val cap = ImageLimits.pageCap(phone)
        // 4000x3000 은 목표만 보면 표본 1(45.8 MiB)이다.
        assertEquals(1, ImageLimits.sampleFor(4000, 3000, 2400))
        val s = ImageLimits.sampleForBudget(4000, 3000, 2400, cap)
        assertTrue(s > 1, "예산을 넘는데 표본이 그대로다")
        assertTrue(ImageLimits.bytesAt(4000, 3000, s) <= cap, "잘랐는데도 예산을 넘는다")
    }

    @Test
    fun `예산 안에 드는 쪽은 그대로 뜬다`() {
        val cap = ImageLimits.pageCap(phone)
        // 1200x1800 만화 쪽은 예산 안이다.
        assertEquals(
            ImageLimits.sampleFor(1200, 1800, 2400),
            ImageLimits.sampleForBudget(1200, 1800, 2400, cap),
        )
    }

    @Test
    fun `네 장을 동시에 들어도 예산 안이다`() {
        val cap = ImageLimits.pageCap(phone)
        for ((w, h) in listOf(4000 to 3000, 2480 to 3508, 800 to 12000, 1200 to 1800)) {
            val s = ImageLimits.sampleForBudget(w, h, 2400, cap)
            val one = ImageLimits.bytesAt(w, h, s)
            assertTrue(one * phone.livePages <= phone.liveCap, "${w}x$h: 한 장 $one × ${phone.livePages}")
        }
    }

    @Test
    fun `상한이 없으면 원래 표본이다`() {
        assertEquals(
            ImageLimits.sampleFor(4000, 3000, 2400),
            ImageLimits.sampleForBudget(4000, 3000, 2400, 0),
        )
    }

    @Test
    fun `아주 작은 상한에서도 끝난다`() {
        val s = ImageLimits.sampleForBudget(20000, 20000, 2400, 16)
        assertTrue(s > 1 && ImageLimits.bytesAt(20000, 20000, s) <= 16 * 64)
    }

    @Test
    fun `그릴 수 있는 크기인가`() {
        assertTrue(ImageLimits.canDraw(4000, 3000))
        assertTrue(!ImageLimits.canDraw(10000, 10000))
    }
}
