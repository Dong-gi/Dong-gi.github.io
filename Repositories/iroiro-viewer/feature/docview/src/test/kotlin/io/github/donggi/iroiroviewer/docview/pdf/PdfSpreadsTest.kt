package io.github.donggi.iroiroviewer.docview.pdf

import io.github.donggi.iroiroviewer.safety.ImageLimits
import io.github.donggi.iroiroviewer.safety.PdfLimits
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 두 쪽 보기의 짝과 배치. 한 쪽씩 어긋나면 모든 펼침이 틀리므로 숫자로 박는다. */
class PdfSpreadsTest {

    @Test
    fun 첫_쪽은_혼자_서고_뒤는_둘씩_묶는다() {
        assertEquals(listOf(listOf(0)), (0 until PdfSpreads.count(1)).map { PdfSpreads.pages(it, 1) })
        assertEquals(listOf(listOf(0), listOf(1)), (0 until PdfSpreads.count(2)).map { PdfSpreads.pages(it, 2) })
        assertEquals(
            listOf(listOf(0), listOf(1, 2), listOf(3, 4)),
            (0 until PdfSpreads.count(5)).map { PdfSpreads.pages(it, 5) },
        )
        assertEquals(
            listOf(listOf(0), listOf(1, 2), listOf(3, 4), listOf(5)),
            (0 until PdfSpreads.count(6)).map { PdfSpreads.pages(it, 6) },
        )
        assertEquals(0, PdfSpreads.count(0))
        assertEquals(emptyList(), PdfSpreads.pages(3, 5))
        assertEquals(emptyList(), PdfSpreads.pages(-1, 5))
    }

    @Test
    fun 모든_쪽이_정확히_한_펼침에_든다() {
        for (n in 1..40) {
            val all = (0 until PdfSpreads.count(n)).flatMap { PdfSpreads.pages(it, n) }
            assertEquals((0 until n).toList(), all, "쪽 $n")
            for (p in 0 until n) {
                assertTrue(p in PdfSpreads.pages(PdfSpreads.spreadOf(p, n), n), "쪽 $p / $n")
            }
        }
        // 범위 밖은 끝으로 누른다.
        assertEquals(PdfSpreads.count(10) - 1, PdfSpreads.spreadOf(99, 10))
        assertEquals(0, PdfSpreads.spreadOf(-3, 10))
    }

    @Test
    fun 두_쪽을_나란히_놓고_높이가_다르면_세로_가운데() {
        val layout = PdfSpreads.layout(listOf(3, 4), listOf(intArrayOf(595, 842), intArrayOf(612, 792)))!!
        assertEquals(595 + 612, layout.width)
        assertEquals(842, layout.height)
        assertEquals(PdfSpreads.Placement(3, 0, 0, 595, 842), layout.parts[0])
        assertEquals(PdfSpreads.Placement(4, 595, 25, 612, 792), layout.parts[1])
        assertFalse(layout.coversWhole)
        // 쪽 하나는 가상 쪽 전체를 덮는다 — 옛 길(통째로 희게 지운다)과 같다.
        assertTrue(PdfSpreads.layout(listOf(0), listOf(intArrayOf(595, 842)))!!.coversWhole)
        assertNull(PdfSpreads.layout(listOf(0, 1), listOf(intArrayOf(595, 842))))
        assertNull(PdfSpreads.layout(listOf(0), listOf(intArrayOf(0, 842))))
    }

    @Test
    fun 펼침의_쪽마다_제_자리로_자른다() {
        // A4 둘(595×842) — 배율 1.5 로 맞춘 한 장(1785×1263). 쪽 사이에 넘치지 않게 각 쪽의 자리가 곧 클립이다.
        val layout = PdfSpreads.layout(listOf(1, 2), listOf(intArrayOf(595, 842), intArrayOf(595, 842)))!!
        val (left, right) = layout.parts
        // 왼 쪽: 0..892.5 → 바깥으로 열어 0..893.
        assertEquals(listOf(0, 0, 893, 1263), PdfSpreads.clipOf(left, 1.5, 0, 0, 1785, 1263)!!.toList())
        // 오른 쪽: 892.5..1785 → 892..1785.
        assertEquals(listOf(892, 0, 1785, 1263), PdfSpreads.clipOf(right, 1.5, 0, 0, 1785, 1263)!!.toList())
    }

    @Test
    fun 타일이_닿지_않는_쪽은_그리지_않는다() {
        // 배율 4 로 확대해 오른 쪽의 가운데(쪽 좌표 x≥595)만 덮는 타일 — 왼 쪽은 그리지 않는다(null), 오른 쪽은 타일 안으로
        // 잘린다(`render` 는 비트맵 밖의 클립을 거절한다).
        val layout = PdfSpreads.layout(listOf(1, 2), listOf(intArrayOf(595, 842), intArrayOf(612, 792)))!!
        val (left, right) = layout.parts
        val srcLeft = 3000
        val srcTop = 1000
        assertNull(PdfSpreads.clipOf(left, 4.0, srcLeft, srcTop, 1000, 1000))
        assertEquals(listOf(0, 0, 1000, 1000), PdfSpreads.clipOf(right, 4.0, srcLeft, srcTop, 1000, 1000)!!.toList())
        // 오른 쪽의 위 가장자리(쪽 좌표 y=25 → 화소 100)가 타일 안에 들면 거기서 시작한다.
        assertEquals(listOf(0, 100, 1000, 1000), PdfSpreads.clipOf(right, 4.0, srcLeft, 0, 1000, 1000)!!.toList())
        // 퇴화한 값.
        assertNull(PdfSpreads.clipOf(right, 0.0, 0, 0, 1000, 1000))
        assertNull(PdfSpreads.clipOf(right, 1.0, 0, 0, 0, 1000))
    }

    @Test
    fun 펼침_한_장의_비트맵은_쪽_하나를_맞춘_것과_같은_예산_안이다() {
        // 가로 화면(2400×1080)에 A4 둘 — 가상 쪽을 맞추면 쪽 하나를 세로로 맞춘 비트맵보다 크지 않다. 그래서 `ImageLimits` 의
        // '한 장' 예산이 그대로 맞는다(머리말).
        val budget = ImageLimits.budgetOf(2400, 1080, 192)
        val cap = ImageLimits.pageCap(budget)
        val layout = PdfSpreads.layout(listOf(1, 2), listOf(intArrayOf(595, 842), intArrayOf(595, 842)))!!
        val scale = PdfLimits.fitScale(layout.width, layout.height, 2400, 1080)
        val plan = PdfLimits.pageBitmap(layout.width, layout.height, scale, cap)!!
        assertTrue(PdfLimits.bytesOf(plan.width, plan.height) <= cap)
        assertTrue(plan.width <= 2400 && plan.height <= 1080)
    }
}
