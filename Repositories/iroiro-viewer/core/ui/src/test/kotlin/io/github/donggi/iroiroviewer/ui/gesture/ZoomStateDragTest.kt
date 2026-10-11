package io.github.donggi.iroiroviewer.ui.gesture

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 확대한 채 가장자리에서 **새로** 밀면 페이저에 넘기고, 같은 끌기 안에서는 답을 바꾸지 않는다.
 *
 * `transformable` 이 `canPan` 을 묻는 차례를 그대로 흉내 낸다 — 손가락이 닿고(`onDragDown`), 움직이고(`onDragMove`), 슬롭을 넘는
 * 순간 처음 묻고, 그 뒤로는 델타마다 다시 묻는다.
 */
class ZoomStateDragTest {

    /** 1080×2400 화면에 1080×1528 쪽을 맞추고 2배로 키운 상태. 가로 여유는 한쪽 540. */
    private fun zoomed(): ZoomState = ZoomState().apply {
        onLayout(Size(1080f, 2400f), 1080, 1528)
        transform(zoomChange = 2f, panChange = Offset.Zero, pivot = Offset.Zero)
    }

    private fun ZoomState.drag(from: Offset, to: Offset): Boolean {
        onDragDown(from)
        onDragMove(to, pointers = 1)
        return claimsDrag()
    }

    @Test
    fun 가운데에서는_받고_가장자리에_닿은_뒤_새로_밀면_넘긴다() {
        val z = zoomed()
        // 가운데에서 왼쪽으로 민다 — 여유가 있으니 그림이 받는다.
        assertTrue(z.drag(Offset(800f, 1200f), Offset(760f, 1202f)))
        // 같은 끌기 안에서 끝까지 밀었다(그림이 왼쪽 끝 = 오른쪽 가장자리가 보인다). 답은 그대로다 — 같은 끌기로는 넘어가지 않는다.
        z.transform(1f, Offset(-2000f, 0f), Offset.Zero)
        z.onDragMove(Offset(100f, 1210f), pointers = 1)
        assertTrue(z.claimsDrag(), "같은 끌기 안에서는 답을 바꾸지 않는다")
        // 손을 떼고 새로 왼쪽으로 민다 — 이번에는 페이저의 것이다.
        assertFalse(z.drag(Offset(800f, 1200f), Offset(760f, 1203f)))
        // 반대쪽으로 밀면 다시 그림이 받는다.
        assertTrue(z.drag(Offset(300f, 1200f), Offset(340f, 1203f)))
    }

    @Test
    fun 한_번_넘긴_끌기는_끝까지_넘긴다() {
        val z = zoomed()
        z.transform(1f, Offset(-2000f, 0f), Offset.Zero)
        assertFalse(z.drag(Offset(800f, 1200f), Offset(760f, 1200f)))
        // 페이저가 끄는 동안 손가락이 되돌아와도(델타가 반대) 그림이 끌기를 빼앗지 않는다.
        z.onDragMove(Offset(900f, 1200f), pointers = 1)
        assertFalse(z.claimsDrag())
    }

    @Test
    fun 두_손가락은_밀_자리만_본다() {
        val z = zoomed()
        z.transform(1f, Offset(-2000f, 0f), Offset.Zero)
        z.onDragDown(Offset(800f, 1200f))
        z.onDragMove(Offset(760f, 1200f), pointers = 2)
        assertTrue(z.claimsDrag(), "핀치로 키운 그림은 같은 몸짓 안에서 옮길 수 있어야 한다")
    }

    @Test
    fun 배율_1_에서는_받지_않는다() {
        val z = ZoomState().apply { onLayout(Size(1080f, 2400f), 1080, 1528) }
        assertFalse(z.drag(Offset(800f, 1200f), Offset(760f, 1200f)))
        // 같은 끌기 안에서 핀치로 바뀌면 그때는 밀 자리만 본다(아직 확대되지 않았으면 거짓).
        z.onDragMove(Offset(700f, 1200f), pointers = 2)
        assertFalse(z.claimsDrag())
    }
}
