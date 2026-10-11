package io.github.donggi.iroiroviewer.image

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** 그린 것의 목록과 되돌리기·다시 하기([MarkupHistory]). */
class MarkupHistoryTest {

    private fun stroke(id: Long) = Stroke(id, StrokeKind.PEN, 0, 0.01f, floatArrayOf(0.1f, 0.1f, 0.2f, 0.2f))
    private fun note(id: Long, text: String = "메모", x: Float = 0.1f) = Note(id, x, 0.1f, text, 0, 0.05f, boxed = true)
    private fun ids(h: MarkupHistory) = h.marks.map { it.id }

    @Test
    fun `더하고 되돌리고 다시 한다`() {
        var h = MarkupHistory.EMPTY.add(stroke(1)).add(stroke(2)).add(note(3))
        assertEquals(listOf(1L, 2L, 3L), ids(h))
        h = h.undo()
        assertEquals(listOf(1L, 2L), ids(h))
        assertTrue(h.canRedo)
        h = h.undo().undo()
        assertEquals(emptyList(), ids(h))
        assertFalse(h.canUndo)
        // 더 되돌릴 것이 없으면 그대로다.
        assertSame(h, h.undo())
        h = h.redo().redo().redo()
        assertEquals(listOf(1L, 2L, 3L), ids(h))
        assertFalse(h.canRedo)
        assertSame(h, h.redo())
    }

    @Test
    fun `새로 무엇을 하면 다시 하기 줄을 비운다`() {
        val h = MarkupHistory.EMPTY.add(stroke(1)).add(stroke(2)).undo().add(stroke(3))
        assertEquals(listOf(1L, 3L), ids(h))
        assertFalse(h.canRedo)
    }

    /** 지우개 한 번에 여럿 — 되돌리면 한 번에, **원래 차례대로** 돌아온다(위에 그린 것이 위에 남는다). */
    @Test
    fun `여럿을 지운 것은 한 걸음이고 제자리로 돌아온다`() {
        var h = MarkupHistory.EMPTY
        for (id in 1L..5L) h = h.add(stroke(id))
        h = h.remove(setOf(2L, 4L, 5L))
        assertEquals(listOf(1L, 3L), ids(h))
        h = h.undo()
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), ids(h))
        h = h.redo()
        assertEquals(listOf(1L, 3L), ids(h))
    }

    @Test
    fun `없는 것을 지우거나 같은 것으로 바꾸면 걸음을 쌓지 않는다`() {
        val h = MarkupHistory.EMPTY.add(note(1))
        assertSame(h, h.remove(setOf(9L)))
        assertSame(h, h.replace(note(1)))
        assertSame(h, h.replace(note(7)))
        assertEquals(1, h.undoDepth)
    }

    @Test
    fun `메모를 고치거나 옮기면 자리를 지키고 되돌릴 수 있다`() {
        var h = MarkupHistory.EMPTY.add(stroke(1)).add(note(2)).add(stroke(3))
        h = h.replace(note(2, text = "고친 메모", x = 0.5f))
        assertEquals(listOf(1L, 2L, 3L), ids(h))
        assertEquals("고친 메모", (h.marks[1] as Note).text)
        h = h.undo()
        assertEquals("메모", (h.marks[1] as Note).text)
        assertEquals(0.1f, (h.marks[1] as Note).x)
        h = h.redo()
        assertEquals(0.5f, (h.marks[1] as Note).x)
    }

    @Test
    fun `모두 지우기는 한 번에 돌아온다`() {
        val full = MarkupHistory.EMPTY.add(stroke(1)).add(note(2))
        val cleared = full.clear()
        assertTrue(cleared.marks.isEmpty())
        assertEquals(listOf(1L, 2L), ids(cleared.undo()))
        // 빈 것을 모두 지우면 걸음이 아니다.
        assertSame(MarkupHistory.EMPTY, MarkupHistory.EMPTY.clear())
    }

    @Test
    fun `되돌리기는 상한까지만 기억한다`() {
        var h = MarkupHistory.EMPTY
        val n = MarkupHistory.MAX_STEPS + 50
        for (id in 1L..n.toLong()) h = h.add(stroke(id))
        assertEquals(MarkupHistory.MAX_STEPS, h.undoDepth)
        repeat(MarkupHistory.MAX_STEPS) { h = h.undo() }
        assertFalse(h.canUndo)
        // 잊은 걸음(처음 50획)은 남는다 — 지워지지 않는다.
        assertEquals(50, h.marks.size)
    }

    @Test
    fun `되살린 목록은 되돌릴 것이 없다`() {
        val h = MarkupHistory.of(listOf(stroke(1), note(2)))
        assertEquals(listOf(1L, 2L), ids(h))
        assertFalse(h.canUndo)
        assertFalse(h.canRedo)
    }

    /** 요청(`SaveCrop`)과 메모 칸의 값은 로그·예외에 찍힐 수 있다 — 사용자가 쓴 글을 싣지 않는다. */
    @Test
    fun `메모의 글은 로그에 실리지 않는다`() {
        val n = note(1, text = "비밀번호 1234")
        assertFalse(n.toString().contains("비밀번호"))
        assertFalse(MarkupOverlay(listOf(n)).toString().contains("비밀번호"))
        assertFalse(NoteEdit(1, 0f, 0f, "비밀번호 1234", 0, 1, null, true).toString().contains("비밀번호"))
    }
}
