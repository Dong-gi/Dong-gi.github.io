package io.github.donggi.iroiroviewer.safety

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 만화 한 권의 상한.
 *
 * **이 시험이 지키는 것은 '두 예산이 서로를 안다' 는 것이다.** 원본 바이트 창과 비트맵
 * 예산이 각자 '상한 안' 이면서 합쳐서 힙을 넘기는 것이 9단계 설계 검토가 잡은 결함이다.
 */
class ComicLimitsTest {

    private val phone = ImageLimits.budgetOf(1080, 2400, 256)
    private val small = ImageLimits.budgetOf(1080, 2400, 64)
    private val tablet = ImageLimits.budgetOf(2560, 1600, 512)

    @Test
    fun `창은 비트맵 예산에서 끌어낸다`() {
        assertTrue(ComicLimits.windowBytes(phone) <= phone.liveCap)
        // 힙이 작으면 창도 작아야 한다 — 다만 바닥 아래로는 안 간다.
        assertTrue(ComicLimits.windowBytes(small) <= ComicLimits.windowBytes(phone))
    }

    @Test
    fun `창에 바닥과 천장이 있다`() {
        for (mb in listOf(16, 32, 64, 128, 256, 512, 1024)) {
            for ((w, h) in listOf(720 to 1280, 1080 to 2400, 2560 to 1600)) {
                val v = ComicLimits.windowBytes(ImageLimits.budgetOf(w, h, mb))
                assertTrue(
                    v in ComicLimits.MIN_WINDOW_BYTES..ComicLimits.MAX_WINDOW_BYTES,
                    "${w}x$h/${mb}MB -> $v",
                )
            }
        }
    }

    /** 창과 살아 있는 비트맵을 **함께** 더해도 힙 등급 안이어야 한다. */
    @Test
    fun `창과 비트맵을 합쳐도 힙 안이다`() {
        for (mb in listOf(64, 128, 256, 512)) {
            val b = ImageLimits.budgetOf(1080, 2400, mb)
            val total = b.liveCap + ComicLimits.windowBytes(b)
            assertTrue(total <= mb.toLong() * 1024 * 1024, "${mb}MB: 합계 $total")
        }
    }

    // ---- 세로(웹툰) 모드 ----------------------------------------------------------

    @Test
    fun `웹툰 한 회는 통짜로 들지 않는다`() {
        // 800x12000 을 폭 1080 에 맞추면 높이가 16,200 이다 — 뷰포트의 6.75배.
        assertTrue(ComicLimits.isTall(800, 12000, 1080, 2400))
    }

    @Test
    fun `보통 만화 쪽은 통짜로 든다`() {
        assertFalse(ComicLimits.isTall(1200, 1800, 1080, 2400))
        assertFalse(ComicLimits.isTall(2480, 3508, 1080, 2400)) // A4 스캔
        // 경계: 뷰포트 세 배가 딱 되는 쪽은 아직 통짜다.
        assertFalse(ComicLimits.isTall(1080, 7200, 1080, 2400))
        assertTrue(ComicLimits.isTall(1080, 7300, 1080, 2400))
    }

    @Test
    fun `이상한 입력에서 터지지 않는다`() {
        assertFalse(ComicLimits.isTall(0, 0, 1080, 2400))
        assertFalse(ComicLimits.isTall(800, 12000, 0, 0))
        assertTrue(ComicLimits.bandHeight(0, 0, 0) >= 1)
    }

    @Test
    fun `띠 하나는 화면 한 장 분량이다`() {
        // 폭 800 쪽을 폭 1080 에 그리면 배율 1.35. 뷰포트 높이 2400 은 원본 1777.
        val band = ComicLimits.bandHeight(800, 1080, 2400)
        assertTrue(band in 1700..1800, "띠 높이 $band")
    }

    @Test
    fun `띠 캐시는 선명화층 몫을 쓴다`() {
        assertEquals(phone.detailCap, ComicLimits.bandCacheBytes(phone))
        // 힙이 작으면 0 이고, 그때는 캐시가 저절로 꺼진다.
        assertEquals(0L, ComicLimits.bandCacheBytes(small))
    }

    @Test
    fun `태블릿에서도 값이 성립한다`() {
        assertTrue(ComicLimits.windowBytes(tablet) <= ComicLimits.MAX_WINDOW_BYTES)
        assertTrue(ComicLimits.bandHeight(1000, 2560, 1600) > 0)
    }
}
