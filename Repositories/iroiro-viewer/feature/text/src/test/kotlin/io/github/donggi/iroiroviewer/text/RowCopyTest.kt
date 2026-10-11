package io.github.donggi.iroiroviewer.text

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import io.github.donggi.iroiroviewer.format.text.LineEnd
import io.github.donggi.iroiroviewer.format.text.TextRow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 고른 글을 잇는 규칙([RowCopy.join]). 조각은 화면이 그리는 것과 같은 [annotateRow] 의 결과를 Compose 의
 * `AnnotatedString.subSequence` 로 자른 것이다 — `SelectionContainer` 가 조각을 만드는 길이 그것이다.
 */
class RowCopyTest {

    private val colors = CodeColors(
        foreground = Color(0xFF000000),
        keyword = Color(0xFF110000),
        literal = Color(0xFF220000),
        string = Color(0xFF330000),
        number = Color(0xFF440000),
        comment = Color(0xFF550000),
        tag = Color(0xFF660000),
        attribute = Color(0xFF770000),
        gutter = Color(0xFF888888),
        hit = Color(0x55000099),
        currentHit = Color(0xAA0000FF),
    )

    private val marks = LineEndMarks(lf = "↓", crlf = "⏎", cr = "←")

    private fun shown(row: TextRow, withMarks: Boolean = true): AnnotatedString =
        annotateRow(row, colors, "", true, false, if (withMarks) marks else null)

    @Test
    fun `행 사이에는 줄바꿈 하나다`() {
        val a = shown(TextRow("abc", 0, false, lineEnd = LineEnd.LF), withMarks = false)
        val b = shown(TextRow("def", 1, false, lineEnd = LineEnd.LF), withMarks = false)
        assertEquals("abc\ndef", RowCopy.join(listOf(a, b)))
        // 가운데부터 가운데까지.
        assertEquals("bc\nde", RowCopy.join(listOf(a.subSequence(1, 3), b.subSequence(0, 2))))
    }

    @Test
    fun `줄 끝 표시는 복사하지 않는다`() {
        val a = shown(TextRow("abc", 0, false, lineEnd = LineEnd.CRLF))
        val b = shown(TextRow("def", 1, false, lineEnd = LineEnd.LF))
        assertEquals("abc⏎", a.text) // 화면에는 있다
        assertEquals("abc\ndef", RowCopy.join(listOf(a, b)))
        // 표시 하나만 고른 조각(선택이 줄 끝에서 시작했다)은 빈 글이 되고, 그 뒤의 줄바꿈은 남는다.
        assertEquals("\nde", RowCopy.join(listOf(a.subSequence(3, 4), b.subSequence(0, 2))))
        // 원문에 같은 글자가 있으면 그것은 글이다 — 지우는 것은 표지가 붙은 자리뿐이다.
        val arrow = shown(TextRow("x ↓ y", 2, false, lineEnd = LineEnd.LF))
        assertEquals("x ↓ y", RowCopy.join(listOf(arrow)))
    }

    @Test
    fun `긴 줄의 조각 사이에는 줄바꿈이 없다`() {
        val head = shown(TextRow("aaaa", 0, false, lineEnd = LineEnd.NONE))
        val tail = shown(TextRow("bbbb", 0, true, lineEnd = LineEnd.LF))
        val next = shown(TextRow("cc", 1, false, lineEnd = LineEnd.LF))
        assertEquals("aaaabbbb\ncc", RowCopy.join(listOf(head, tail, next)))
        // 둘째 조각의 가운데부터 골라도 표지가 남는다.
        assertEquals("aabb", RowCopy.join(listOf(head.subSequence(2, 4), tail.subSequence(0, 2))))
    }

    /** 선택이 행의 맨 앞에서 끝나면 그 행의 조각은 비어 있다. Compose 의 빈 범위가 주석을 지키는지 박는다. */
    @Test
    fun `빈 조각도 행의 사정을 안다`() {
        val head = shown(TextRow("aaaa", 0, false, lineEnd = LineEnd.NONE))
        val tail = shown(TextRow("bbbb", 0, true, lineEnd = LineEnd.LF))
        val next = shown(TextRow("cc", 1, false, lineEnd = LineEnd.LF))
        // 이어지는 행의 맨 앞에서 끝났다 — 줄이 끝나지 않았으므로 줄바꿈도 없다.
        assertEquals("aaaa", RowCopy.join(listOf(head, tail.subSequence(0, 0))))
        // 다음 줄의 맨 앞에서 끝났다 — 줄이 끝났으므로 줄바꿈이 따라온다(편집기들과 같다).
        assertEquals("bbbb\n", RowCopy.join(listOf(tail.subSequence(0, 4), next.subSequence(0, 0))))
    }

    @Test
    fun `상한을 넘으면 만들지 않는다`() {
        val a = shown(TextRow("abcde", 0, false, lineEnd = LineEnd.LF))
        val b = shown(TextRow("fghij", 1, false, lineEnd = LineEnd.LF))
        assertEquals("abcde\nfghij", RowCopy.join(listOf(a, b), limit = 11))
        assertNull(RowCopy.join(listOf(a, b), limit = 10))
    }

    /**
     * 다른 앱으로 보내는 글([RowSend])은 복사와 같게 잇되 **상한에서 자른다**. Compose 의 '번역' 항목은 날것의 선택을 상한
     * 없이 실어 큰 선택에서 앱이 죽었다. 줄 끝 표시와 4,096자 경계의 줄바꿈이 빠지고, 서로게이트 쌍을 가르지 않는다.
     */
    @Test
    fun `보내는 글은 복사처럼 잇고 상한에서 자른다`() {
        val a = shown(TextRow("abc", 0, false, lineEnd = LineEnd.CRLF))
        val b = shown(TextRow("def", 0, true, lineEnd = LineEnd.LF))
        val whole = RowCopy.joinPrefix(listOf(a, b), 100)
        assertEquals("abcdef", whole.text)
        assertEquals(false, whole.truncated)
        val cut = RowCopy.joinPrefix(listOf(a, b), 4)
        assertEquals("abcd", cut.text)
        assertEquals(true, cut.truncated)
        // 상한이 서로게이트 쌍의 가운데면 그 앞에서 자른다.
        val emoji = AnnotatedString("ab😀c")
        assertEquals("ab", RowCopy.joinPrefix(listOf(emoji), 3).text)
        assertEquals("", RowCopy.joinPrefix(emptyList(), 10).text)
    }

    @Test
    fun `두 손가락의 글자 크기는 반올림하고 범위를 누른다`() {
        assertEquals(13, PinchFont.sizeFor(13, 1f))
        assertEquals(20, PinchFont.sizeFor(13, 1.5f))
        assertEquals(io.github.donggi.iroiroviewer.data.TextViewerDefaults.MAX_FONT_SP, PinchFont.sizeFor(13, 10f))
        assertEquals(io.github.donggi.iroiroviewer.data.TextViewerDefaults.MIN_FONT_SP, PinchFont.sizeFor(13, 0.1f))
        assertEquals(13, PinchFont.sizeFor(13, Float.NaN))
    }
}
