package io.github.donggi.iroiroviewer.text

import androidx.compose.ui.graphics.Color
import io.github.donggi.iroiroviewer.format.text.LineEnd
import io.github.donggi.iroiroviewer.format.text.Span
import io.github.donggi.iroiroviewer.format.text.TextRow
import io.github.donggi.iroiroviewer.format.text.TokenKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 줄 끝 표시(T3)와 찾기 진행률(T2)의 순수한 부분. */
class RowTextTest {

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

    @Test
    fun `줄 끝을 끄면 행의 글자 그대로다`() {
        val row = TextRow("abc", 0, false, lineEnd = LineEnd.LF)
        assertEquals("abc", annotateRow(row, colors, "", true, false, null).text)
    }

    @Test
    fun `줄 끝 세 종류가 서로 다른 표시로 붙는다`() {
        for ((end, mark) in listOf(LineEnd.LF to "↓", LineEnd.CRLF to "⏎", LineEnd.CR to "←")) {
            val out = annotateRow(TextRow("x", 0, false, lineEnd = end), colors, "", true, false, marks)
            assertEquals("x$mark", out.text)
            // 표시는 흐린 색이다.
            val style = out.spanStyles.single { it.start == 1 }
            assertEquals(colors.gutter, style.item.color)
            assertEquals(2, style.end)
        }
    }

    /** 긴 줄을 자른 앞 조각과 줄바꿈 없는 마지막 행에는 표시가 없다. */
    @Test
    fun `줄이 끝나지 않은 행에는 표시가 없다`() {
        val out = annotateRow(TextRow("앞 조각", 3, true, lineEnd = LineEnd.NONE), colors, "", true, false, marks)
        assertEquals("앞 조각", out.text)
        assertTrue(out.spanStyles.isEmpty())
    }

    /** 찾은 자리와 강조는 **행의 글자 안에서만** 칠한다. 표시가 그 좌표를 밀지 않는다. */
    @Test
    fun `표시가 강조와 찾은 자리를 밀지 않는다`() {
        val row = TextRow("val x", 0, false, listOf(Span(0, 3, TokenKind.KEYWORD)), LineEnd.CRLF)
        val out = annotateRow(row, colors, "x", true, true, marks)
        assertEquals("val x⏎", out.text)
        val kw = out.spanStyles.single { it.item.color == colors.keyword }
        assertEquals(0 to 3, kw.start to kw.end)
        val hit = out.spanStyles.single { it.item.background == colors.currentHit }
        assertEquals(4 to 5, hit.start to hit.end)
    }

    @Test
    fun `찾기 진행률`() {
        assertEquals(0, searchPercent(0, 0))
        assertEquals(0, searchPercent(10, 0))
        assertEquals(25, searchPercent(50, 200))
        assertEquals(100, searchPercent(200, 200))
        assertEquals(100, searchPercent(300, 200))
        // 큰 파일에서 곱셈이 넘치지 않는다.
        assertEquals(50, searchPercent(Int.MAX_VALUE / 2, Int.MAX_VALUE - 1))
    }

    /** 진행 알림은 찾기 스레드에서 **늦게** 올 수 있다. 닫은 뒤·새로 찾은 뒤의 상태를 고치면 안 된다. */
    @Test
    fun `늦게 온 진행 알림과 결과는 버린다`() {
        val running = TextViewModel.SearchState(query = "a", running = true, totalRows = 1000, generation = 3)
        assertEquals(400, running.withProgress(3, 400).scannedRows)
        assertEquals(40, running.withProgress(3, 400).percent)
        // 다른 찾기의 알림.
        assertEquals(running, running.withProgress(2, 400))
        // 닫은 뒤(빈 상태, 번호 0).
        val cleared = TextViewModel.SearchState()
        assertEquals(cleared, cleared.withProgress(3, 400))
        assertEquals(cleared, cleared.withResult(3, listOf(1, 2)))
        // 끝난 뒤의 알림.
        val done = running.withResult(3, listOf(5, 9))
        assertEquals(listOf(5, 9), done.hits)
        assertEquals(0, done.cursor)
        assertEquals(false, done.running)
        assertEquals(100, done.percent)
        assertEquals(done, done.withProgress(3, 10))
        // 결과가 없으면 커서가 없다.
        assertEquals(-1, running.withResult(3, emptyList()).cursor)
    }

    /** 찾을 말을 지우고 찾으면 돌던 찾기의 결과·진행이 **나중에 와도** 빈 찾기에 얹히지 않는다. */
    @Test
    fun `찾을 말을 비우면 돌던 찾기의 결과를 버린다`() {
        val running = TextViewModel.SearchState(query = "a", running = true, totalRows = 1000, scannedRows = 300, generation = 3)
        val emptied = running.copy(query = "").withoutQuery(4)
        assertEquals(false, emptied.running)
        assertEquals(0, emptied.percent)
        assertEquals(emptied, emptied.withProgress(3, 900))
        assertEquals(emptied, emptied.withResult(3, listOf(7, 8, 9)))
        assertTrue(emptied.hits.isEmpty())
        assertEquals(-1, emptied.cursor)
    }
}
