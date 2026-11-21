package io.github.donggi.iroiroviewer.model

import io.github.donggi.iroiroviewer.model.ExifOrientation.Op
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * EXIF 방향 합성. **여덟 방향 × 네 가지 일을 전부 박는다** — 표 하나가 틀리면 그 방향의 사진만 저장된 뒤 화면에서 본 것과 다르게
 * 돌아 있고, 화면 캡처로는 여덟 갈래를 다 볼 수 없다.
 *
 * 정답은 두 겹이다. 손으로 적은 표(아래 [EXPECTED])와, EXIF 명세의 문장('0번째 행이 화면의 어느 쪽, 0번째 열이 어느 쪽')을 좌표로
 * 옮긴 [displayOf] 로 실제 화소를 옮겨 본 것. 후자는 셈과 따로 세운 정의다 — 표가 셈을 베껴 쓴 것이면 둘 다 같이 틀린다.
 */
class ExifOrientationTest {

    /** 명세의 정의. 저장 화소 (x, y) 가 화면의 어디로 가는가 — [w]·[h] 는 저장 치수. */
    private fun displayOf(o: Int, w: Int, h: Int, x: Int, y: Int): Pair<Int, Int> = when (o) {
        2 -> (w - 1 - x) to y            // 0행 위, 0열 오른쪽
        3 -> (w - 1 - x) to (h - 1 - y)  // 0행 아래, 0열 오른쪽
        4 -> x to (h - 1 - y)            // 0행 아래, 0열 왼쪽
        5 -> y to x                      // 0행 왼쪽, 0열 위
        6 -> (h - 1 - y) to x            // 0행 오른쪽, 0열 위
        7 -> (h - 1 - y) to (w - 1 - x)  // 0행 오른쪽, 0열 아래
        8 -> y to (w - 1 - x)            // 0행 왼쪽, 0열 아래
        else -> x to y
    }

    /** 저장 그림(값 = 화소 번호)을 방향 [o] 로 화면에 그린 격자. */
    private fun render(o: Int, w: Int, h: Int): List<List<Int>> {
        val swap = o in 5..8
        val dw = if (swap) h else w
        val dh = if (swap) w else h
        val grid = Array(dh) { IntArray(dw) { -1 } }
        for (y in 0 until h) for (x in 0 until w) {
            val (dx, dy) = displayOf(o, w, h, x, y)
            grid[dy][dx] = y * w + x
        }
        return grid.map { it.toList() }
    }

    /** 화면에 보이는 격자에 화면의 일을 한다. */
    private fun act(op: Op, g: List<List<Int>>): List<List<Int>> {
        val h = g.size
        val w = g[0].size
        return when (op) {
            // 시계 방향: 새 격자는 h×w, 새 (X, Y) 의 값은 옛 (Y, h − 1 − X).
            Op.ROTATE_RIGHT -> List(w) { y -> List(h) { x -> g[h - 1 - x][y] } }
            Op.ROTATE_LEFT -> List(w) { y -> List(h) { x -> g[x][w - 1 - y] } }
            Op.FLIP_HORIZONTAL -> g.map { it.reversed() }
            Op.FLIP_VERTICAL -> g.reversed()
        }
    }

    // 정사각이 아닌, 가로세로가 다른 작은 그림이어야 5·7 과 6·8 이 갈린다.
    private val w = 3
    private val h = 2

    /** 방향 1~8 마다, 오른쪽 회전·왼쪽 회전·좌우 뒤집기·상하 뒤집기 뒤의 방향. */
    private val EXPECTED = mapOf(
        Op.ROTATE_RIGHT to intArrayOf(6, 7, 8, 5, 2, 3, 4, 1),
        Op.ROTATE_LEFT to intArrayOf(8, 5, 6, 7, 4, 1, 2, 3),
        Op.FLIP_HORIZONTAL to intArrayOf(2, 1, 4, 3, 6, 5, 8, 7),
        Op.FLIP_VERTICAL to intArrayOf(4, 3, 2, 1, 8, 7, 6, 5),
    )

    @Test
    fun 여덟_방향과_네_가지_일의_표() {
        for ((op, row) in EXPECTED) for (o in 1..8) {
            assertEquals(row[o - 1], ExifOrientation.apply(o, op), "방향 $o 에 $op")
        }
    }

    @Test
    fun 저장된_방향은_화면에서_한_일과_같은_그림을_그린다() {
        for (op in Op.entries) for (o in 1..8) {
            val seen = act(op, render(o, w, h))
            val saved = render(ExifOrientation.apply(o, op), w, h)
            assertEquals(seen, saved, "방향 $o 에 $op")
        }
    }

    @Test
    fun 좌우가_뒤집힌_사진을_오른쪽으로_돌리면_7이다() {
        // 옛 셈은 5(TRANSPOSE)를 냈다 — 다시 읽으면 화면에서 본 것과 180° 어긋났다.
        assertEquals(ExifOrientation.TRANSVERSE, ExifOrientation.apply(ExifOrientation.FLIP_HORIZONTAL, Op.ROTATE_RIGHT))
    }

    @Test
    fun 되돌리는_길() {
        for (o in 1..8) {
            assertEquals(o, ExifOrientation.apply(ExifOrientation.apply(o, Op.ROTATE_RIGHT), Op.ROTATE_LEFT), "오른·왼 $o")
            assertEquals(o, ExifOrientation.apply(ExifOrientation.apply(o, Op.FLIP_HORIZONTAL), Op.FLIP_HORIZONTAL), "좌우 둘 $o")
            assertEquals(o, ExifOrientation.apply(ExifOrientation.apply(o, Op.FLIP_VERTICAL), Op.FLIP_VERTICAL), "상하 둘 $o")
            var r = o
            repeat(4) { r = ExifOrientation.apply(r, Op.ROTATE_RIGHT) }
            assertEquals(o, r, "오른쪽 넷 $o")
            assertEquals(ExifOrientation.NORMAL, ExifOrientation.compose(ExifOrientation.inverse(o), o), "역 $o")
            assertEquals(ExifOrientation.NORMAL, ExifOrientation.compose(o, ExifOrientation.inverse(o)), "역(오른쪽) $o")
        }
        // 좌우 뒤집고 상하 뒤집으면 180° 회전이다.
        val both = ExifOrientation.apply(ExifOrientation.apply(1, Op.FLIP_HORIZONTAL), Op.FLIP_VERTICAL)
        assertEquals(ExifOrientation.ROTATE_180, both)
    }

    @Test
    fun 합성은_결합법칙을_따르고_정방향이_항등이다() {
        for (a in 1..8) for (b in 1..8) {
            assertEquals(a, ExifOrientation.compose(a, 1))
            assertEquals(b, ExifOrientation.compose(1, b))
            for (c in 1..8) {
                assertEquals(
                    ExifOrientation.compose(a, ExifOrientation.compose(b, c)),
                    ExifOrientation.compose(ExifOrientation.compose(a, b), c),
                    "$a·$b·$c",
                )
            }
        }
    }

    @Test
    fun 범위_밖의_값은_정방향이다() {
        for (bad in listOf(0, -1, 9, 65535)) {
            assertEquals(1, ExifOrientation.normalize(bad))
            assertEquals(ExifOrientation.ROTATE_90, ExifOrientation.apply(bad, Op.ROTATE_RIGHT))
        }
    }

    @Test
    fun 화면의_겹은_안쪽에서_뒤집고_바깥에서_돈다() {
        // 겹이 그리는 그림 = 회전(뒤집기(그림)). 그것이 방향 값이 그리는 것과 같아야 한다.
        for (o in 1..8) {
            val layer = ExifOrientation.layerOf(o)
            var g = render(1, w, h)
            if (layer.mirrored) g = act(Op.FLIP_HORIZONTAL, g)
            repeat(layer.degrees / 90) { g = act(Op.ROTATE_RIGHT, g) }
            assertEquals(render(o, w, h), g, "방향 $o")
        }
        assertEquals(ExifOrientation.Layer(0, false), ExifOrientation.layerOf(1))
        assertTrue(ExifOrientation.layerOf(ExifOrientation.TRANSPOSE).mirrored)
        assertFalse(ExifOrientation.layerOf(ExifOrientation.ROTATE_270).mirrored)
    }
}
