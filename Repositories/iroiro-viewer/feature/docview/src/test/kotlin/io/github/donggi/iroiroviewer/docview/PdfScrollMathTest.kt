package io.github.donggi.iroiroviewer.docview

import io.github.donggi.iroiroviewer.docview.PdfScrollMath.Row
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * PDF 이어 보기의 계산. 화면은 목록을 배율 1 로 놓고 층이 키우므로, 좌표가 맞는지는 화면 캡처가 아니라 여기서 잰다
 * (6단계가 확대를 캡처로 판정하다 세 번 틀렸다).
 */
class PdfScrollMathTest {

    private fun near(expected: Float, actual: Float, tolerance: Float = 0.01f) =
        assertTrue(abs(expected - actual) < tolerance, "기대 $expected, 실제 $actual")

    @Test
    fun 쪽이_든_줄은_두_쪽_보기면_펼침이다() {
        assertEquals(8, PdfScrollMath.rowCount(8, spread = false))
        assertEquals(5, PdfScrollMath.rowCount(8, spread = true)) // [0] [1 2] [3 4] [5 6] [7]
        assertEquals(3, PdfScrollMath.rowOf(3, 8, spread = false))
        assertEquals(2, PdfScrollMath.rowOf(3, 8, spread = true))
        assertEquals(7, PdfScrollMath.rowOf(99, 8, spread = false))
    }

    @Test
    fun 핀치는_손가락_사이를_제자리에_둔다() {
        // 화면 (300, 1200) 을 짚고 1 → 2 배. 그 점은 목록의 (300, 1200) 이었다.
        val tx = PdfScrollMath.txAfterZoom(tx = 0f, pivotX = 300f, from = 1f, to = 2f)
        val scroll = PdfScrollMath.scrollAfterZoom(pivotY = 1200f, panY = 0f, from = 1f, to = 2f)
        // 바뀐 뒤 화면의 300 에는 목록의 (300 − tx) / 2 = 300 이 온다.
        near(300f, (300f - tx) / 2f)
        // 세로는 목록을 600 앞으로 — 화면의 1200 에 목록의 (1200 / 2) + 600 = 1200 이 온다.
        near(600f, scroll)
        // 되돌리면 원래대로(왕복이 맞아야 두 번 두드림이 튀지 않는다).
        near(0f, PdfScrollMath.txAfterZoom(tx, 300f, 2f, 1f))
        near(-600f, PdfScrollMath.scrollAfterZoom(1200f, 0f, 2f, 1f))
    }

    @Test
    fun 두_손가락으로_옮기면_내용이_손가락을_따른다() {
        // 배율 그대로 손가락이 아래로 90 — 목록은 뒤로 90 / 배율.
        near(-45f, PdfScrollMath.scrollAfterZoom(pivotY = 1000f, panY = 90f, from = 2f, to = 2f))
    }

    @Test
    fun 가로_옮김은_키운_만큼만() {
        near(0f, PdfScrollMath.clampTx(50f, 2f, 1080f))
        near(-1080f, PdfScrollMath.clampTx(-5000f, 2f, 1080f))
        near(0f, PdfScrollMath.clampTx(-300f, 1f, 1080f), tolerance = 0.001f)
        near(0f, PdfScrollMath.clampTx(Float.NaN, 2f, 1080f))
    }

    @Test
    fun 끝까지_보이게_아래_여백을_둔다() {
        // 2배면 화면에 보이는 것은 목록의 위 절반 — 나머지 절반만큼 더 내려가야 끝이 보인다.
        near(1200f, PdfScrollMath.bottomPadding(2400f, 2f))
        near(0f, PdfScrollMath.bottomPadding(2400f, 1f))
    }

    @Test
    fun 지금_쪽은_통째로_보이는_맨_위_줄이고_아니면_가운데_줄이다() {
        // 가로 A4(높이 763)가 이어진 목록에서 '쪽으로 가기' 로 3쪽을 맨 위에 세웠다 — 화면 가운데(1200)는 4쪽이지만 고른 것은 3쪽이다.
        val jumped = listOf(Row(3, 0f, 763f), Row(4, 771f, 1534f), Row(5, 1542f, 2305f), Row(6, 2313f, 3076f))
        assertEquals(3, PdfScrollMath.currentRow(jumped, 0f, 2400f))
        // 조금 내려 3쪽 위가 잘렸다 — 이제 가운데 줄이다.
        val scrolled = listOf(Row(3, -200f, 563f), Row(4, 571f, 1334f), Row(5, 1342f, 2105f), Row(6, 2113f, 2876f))
        assertEquals(4, PdfScrollMath.currentRow(scrolled, 0f, 2400f))
        // 화면보다 긴 쪽(세로 A4 를 가로 화면 폭에 맞췄다)이 화면을 덮는다 — 그 쪽이다.
        assertEquals(2, PdfScrollMath.currentRow(listOf(Row(2, -900f, 2500f), Row(3, 2508f, 5900f)), 0f, 1080f))
        // 가운데가 줄 사이 틈이면 그 아래 줄.
        assertEquals(1, PdfScrollMath.currentRow(listOf(Row(0, -500f, 1196f), Row(1, 1204f, 2900f)), 0f, 2400f))
        // 확대하면 창이 작아진다(보이는 것은 목록의 위 높이 / 배율).
        assertEquals(0, PdfScrollMath.currentRow(listOf(Row(0, -100f, 1400f), Row(1, 1408f, 2900f)), 0f, 1200f))
        assertNull(PdfScrollMath.currentRow(emptyList(), 0f, 2400f))
    }

    @Test
    fun 끝까지_내렸으면_마지막_줄이다() {
        // 짧은 가로 쪽 셋이 보이는 끝 — 목록은 마지막 쪽을 맨 위로 올리지 못한다. '마지막 쪽으로 가기' 가 7쪽으로 읽히면 안 된다.
        val end = listOf(Row(7, 0f, 763f), Row(8, 771f, 1534f), Row(9, 1542f, 2305f))
        assertEquals(7, PdfScrollMath.currentRow(end, 0f, 2400f))
        assertEquals(9, PdfScrollMath.currentRow(end, 0f, 2400f, atEnd = true))
    }

    @Test
    fun 두드린_자리는_줄_안의_비율이다() {
        val rows = listOf(Row(4, 0f, 1528f), Row(5, 1536f, 3064f))
        // 2배, 오른쪽으로 540 옮김(tx = −540). 화면 (540, 1600) = 목록 (540, 800) → 4쪽의 (0.5, 0.52).
        val hit = assertNotNull(PdfScrollMath.rowAt(540f, 1600f, scale = 2f, tx = -540f, width = 1080f, rows = rows))
        assertEquals(4, hit.row)
        near(0.5f, hit.x)
        near(800f / 1528f, hit.y)
        // 줄 사이 틈은 아무 줄도 아니다.
        assertNull(PdfScrollMath.rowAt(100f, 1532f, scale = 1f, tx = 0f, width = 1080f, rows = rows))
    }

    @Test
    fun 보이는_자리는_줄마다_잘라_잰다() {
        val rows = listOf(Row(0, -400f, 1128f), Row(1, 1136f, 2664f), Row(2, 2672f, 4200f))
        // 2배, 가로 가운데(tx = −540): 보이는 창은 목록의 x 270~810, y 0~1200.
        val regions = PdfScrollMath.visibleRegions(rows, width = 1080f, height = 2400f, scale = 2f, tx = -540f)
        // 1쪽(1136~)은 창의 아래 끝에 64 화소만 걸친다. 2쪽은 창 밖이다 — 목록이 구성해 두었어도 자리를 받지 않는다.
        assertEquals(listOf(0, 1), regions.map { it.row })
        near(0.25f, regions[0].left, 0.001f)
        near(0.75f, regions[0].right, 0.001f)
        near(64f / 1528f, regions[1].bottom, 0.001f)
    }

    @Test
    fun 창에_걸친_줄만_자리를_받는다() {
        val rows = listOf(Row(0, -400f, 1128f), Row(1, 1136f, 2664f))
        val regions = PdfScrollMath.visibleRegions(rows, width = 1080f, height = 2400f, scale = 1.5f, tx = -270f)
        // 창: x 180~900, y 0~1600 → 0쪽은 (0.1667, 0.2617)~(0.8333, 1), 1쪽은 (0.1667, 0)~(0.8333, 0.3029).
        assertEquals(listOf(0, 1), regions.map { it.row })
        val first = regions[0]
        near(180f / 1080f, first.left, 0.001f)
        near(900f / 1080f, first.right, 0.001f)
        near(400f / 1528f, first.top, 0.001f)
        near(1f, first.bottom, 0.001f)
        val second = regions[1]
        near(0f, second.top, 0.001f)
        near((1600f - 1136f) / 1528f, second.bottom, 0.001f)
        // 넓이는 화면 화소² — 위 줄이 더 많이 보인다.
        assertTrue(first.area > second.area)
    }

    @Test
    fun 선명화_예산은_보이는_넓이대로_나눠_합이_넘지_않는다() {
        val cap = 8_859_648L
        val parts = PdfScrollMath.shares(listOf(3f, 1f), cap)
        assertTrue(parts.sum() <= cap, "$parts")
        assertEquals(cap * 3 / 4, parts[0])
        // 넓이가 없으면 몫도 없다.
        assertEquals(listOf(0L, 0L), PdfScrollMath.shares(listOf(0f, 0f), cap))
        assertEquals(listOf(0L), PdfScrollMath.shares(listOf(5f), 0L))
    }

    @Test
    fun 찾은_자리는_화면의_위_삼분의_일에_온다() {
        // 줄 높이 3000 에서 상자 위가 0.5 → 1500. 창 높이 1200 의 1/3(400)만큼 위에 둔다.
        assertEquals(1100, PdfScrollMath.matchOffset(0.5f, 3000f, 1200f))
        assertEquals(0, PdfScrollMath.matchOffset(0.01f, 3000f, 1200f))
    }

    @Test
    fun 찾은_것이_가로로_밖이면_보이게_옮긴다() {
        // 2배, 왼쪽 끝을 보고 있다(tx = 0 → 목록 x 0~540 이 보인다). 상자가 목록 x 800~900 이면 옮긴다.
        val tx = PdfScrollMath.txToReveal(tx = 0f, scale = 2f, width = 1080f, left = 800f, right = 900f)
        val visibleLeft = -tx / 2f
        val visibleRight = (1080f - tx) / 2f
        assertTrue(800f >= visibleLeft && 900f <= visibleRight, "보이는 창 $visibleLeft~$visibleRight")
        // 이미 보이면 그대로.
        near(0f, PdfScrollMath.txToReveal(tx = 0f, scale = 2f, width = 1080f, left = 100f, right = 200f))
    }
}
