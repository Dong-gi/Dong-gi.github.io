package io.github.donggi.iroiroviewer.docview.pdf.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 고르기의 순수한 부분([PdfTextLayout]). 글자는 시험이 직접 놓는다 — 크기 10, 폭 6 의 가로쓰기. */
class PdfTextLayoutTest {

    /** [text] 를 (x, 기준선 y) 부터 한 글자씩 놓는다. 띄어쓰기 글자도 상자를 갖는다. */
    private fun line(text: String, x: Float, y: Float, step: Float = 6f): List<Glyph> = text.mapIndexed { i, ch ->
        val left = x + i * step
        Glyph(ch.toString(), left, y - 8f, left + 6f, y + 2f, left, y, 1f, 0f, 6f, 10f)
    }

    private fun page(vararg lines: List<Glyph>) = PageText(600f, 800f, lines.flatMap { it })

    @Test
    fun `줄이 바뀌면 줄바꿈, 띄어 놓은 자리는 띄어쓰기`() {
        // 'World' 는 공백 글자 없이 12 포인트 떨어져 있다.
        val p = page(line("Hello", 10f, 20f), line("World", 52f, 20f), line("next", 10f, 40f))
        assertEquals("Hello World\nnext", PdfTextLayout.text(p, 0, p.glyphs.size))
        // 위첨자처럼 조금 뜬 글자는 같은 줄이다.
        val sup = page(line("x", 10f, 20f), line("2", 16f, 16f))
        assertEquals("x2", PdfTextLayout.text(sup, 0, 2))
    }

    @Test
    fun `합자는 풀고 모르는 글자는 빼고 줄 끝 공백은 지운다`() {
        val p = page(line("ﬁn�d ", 10f, 20f), line("x", 10f, 40f))
        assertEquals("find\nx", PdfTextLayout.text(p, 0, p.glyphs.size))
    }

    @Test
    fun `상한을 넘으면 만들지 않는다`() {
        val p = page(line("abcdef", 10f, 20f))
        assertEquals("abcdef", PdfTextLayout.text(p, 0, 6, limit = 6))
        assertNull(PdfTextLayout.text(p, 0, 6, limit = 5))
    }

    @Test
    fun `길게 누른 자리의 낱말을 고른다`() {
        val p = page(line("say hello, world", 10f, 20f))
        // 'e' 는 4번째 글자 다음 — 'hello' 는 4..8.
        assertEquals(4..8, PdfTextLayout.wordAt(p, 5))
        // 문장 부호는 그 글자 하나다.
        assertEquals(9..9, PdfTextLayout.wordAt(p, 9))
        // 줄이 바뀌면 이어진 글자라도 다른 낱말이다.
        val q = page(line("ab", 10f, 20f), line("cd", 22f, 40f))
        assertEquals(0..1, PdfTextLayout.wordAt(q, 1))
    }

    @Test
    fun `한글 낱말은 띄어쓰기까지다`() {
        val p = page(line("가나다 라마", 10f, 20f))
        assertEquals(0..2, PdfTextLayout.wordAt(p, 1))
        assertEquals(4..5, PdfTextLayout.wordAt(p, 5))
    }

    @Test
    fun `누른 자리와 손잡이의 경계`() {
        val p = page(line("abc", 10f, 20f))
        // 'b' 의 상자 안.
        assertEquals(1, PdfTextLayout.hit(p, 18f, 16f, 4f))
        // 멀리 떨어진 곳은 없다.
        assertNull(PdfTextLayout.hit(p, 300f, 300f, 4f))
        // 'b'(16~22)의 가운데(19)보다 앞이면 그 앞, 뒤면 그 뒤.
        assertEquals(1, PdfTextLayout.boundaryAt(p, 17f, 16f))
        assertEquals(2, PdfTextLayout.boundaryAt(p, 21f, 16f))
        // 마지막 글자 뒤로 멀리 가도 끝이다.
        assertEquals(3, PdfTextLayout.boundaryAt(p, 200f, 16f))
    }

    @Test
    fun `칠할 사각형은 줄마다 하나다`() {
        val p = page(line("abc", 10f, 20f), line("de", 10f, 40f))
        val rects = PdfTextLayout.rects(p, 1, 5)
        assertEquals(2, rects.size)
        assertEquals(listOf(16f, 12f, 28f, 22f), rects[0].toList())
        assertEquals(listOf(10f, 32f, 22f, 42f), rects[1].toList())
    }
}
