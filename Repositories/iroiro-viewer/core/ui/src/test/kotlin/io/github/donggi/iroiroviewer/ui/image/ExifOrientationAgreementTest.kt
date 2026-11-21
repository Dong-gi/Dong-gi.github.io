package io.github.donggi.iroiroviewer.ui.image

import io.github.donggi.iroiroviewer.model.ExifOrientation
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 저장의 셈(`core:model` 의 [ExifOrientation])과 확대 조각·잘라 저장이 쓰는 좌표 정의([RegionMath.displayOf])가 **같은 여덟 방향을
 * 말하는가.** 둘이 갈리면 회전·뒤집기를 저장한 사진의 조각이 바닥층과 다른 쪽으로 얹힌다.
 */
class ExifOrientationAgreementTest {

    private fun render(o: Int, w: Int, h: Int): List<List<Int>> {
        val swap = RegionMath.swapsAxes(o)
        val grid = Array(if (swap) w else h) { IntArray(if (swap) h else w) { -1 } }
        for (y in 0 until h) for (x in 0 until w) {
            val (dx, dy) = RegionMath.displayOf(o, w, h, x, y)
            grid[dy][dx] = y * w + x
        }
        return grid.map { it.toList() }
    }

    @Test
    fun 화면의_일을_한_그림과_저장된_방향의_그림이_같다() {
        val w = 4
        val h = 3
        for (o in 1..8) {
            val g = render(o, w, h)
            val gh = g.size
            val gw = g[0].size
            val right = List(gw) { y -> List(gh) { x -> g[gh - 1 - x][y] } }
            val left = List(gw) { y -> List(gh) { x -> g[x][gw - 1 - y] } }
            assertEquals(right, render(ExifOrientation.apply(o, ExifOrientation.Op.ROTATE_RIGHT), w, h), "오른 $o")
            assertEquals(left, render(ExifOrientation.apply(o, ExifOrientation.Op.ROTATE_LEFT), w, h), "왼 $o")
            assertEquals(g.map { it.reversed() }, render(ExifOrientation.apply(o, ExifOrientation.Op.FLIP_HORIZONTAL), w, h), "좌우 $o")
            assertEquals(g.reversed(), render(ExifOrientation.apply(o, ExifOrientation.Op.FLIP_VERTICAL), w, h), "상하 $o")
        }
    }
}
