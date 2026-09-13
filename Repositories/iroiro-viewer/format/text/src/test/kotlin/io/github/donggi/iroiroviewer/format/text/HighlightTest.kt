package io.github.donggi.iroiroviewer.format.text

import io.github.donggi.iroiroviewer.charset.TextEncoding
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 문법 강조.
 *
 * **이 시험이 지키는 것은 두 가지다.** 하나는 색이 맞는가, 다른 하나는 **조각이 성립하는가**
 * — 겹치거나 행 밖으로 나가는 조각은 그리는 쪽에서 예외를 내거나 글자를 먹는다.
 */
class HighlightTest {

    private fun spansOf(h: RowHighlighter, row: String, state: Int = RowHighlighter.START): Pair<List<Span>, Int> {
        val out = ArrayList<Span>()
        val next = h.highlight(row, state, out)
        assertWellFormed(row, out)
        return out to next
    }

    /** 조각은 오름차순이고, 겹치지 않고, 행 안에 있어야 한다. */
    private fun assertWellFormed(row: String, spans: List<Span>) {
        var last = 0
        for (s in spans) {
            assertTrue(s.start >= last, "조각이 겹치거나 뒤로 갔다: $s (앞의 끝 $last) / $row")
            assertTrue(s.start < s.end, "빈 조각: $s / $row")
            assertTrue(s.end <= row.length, "행 밖으로 나간 조각: $s / 길이 ${row.length} / $row")
            last = s.end
        }
    }

    private fun kinds(spans: List<Span>) = spans.map { it.kind }
    private fun texts(row: String, spans: List<Span>) = spans.map { row.substring(it.start, it.end) }

    private val kotlin = TextLanguage.forFileName("Foo.kt")
    private val json = TextLanguage.forFileName("a.json")
    private val python = TextLanguage.forFileName("a.py")
    private val xml = TextLanguage.forFileName("a.xml")

    @Test
    fun `코틀린 한 줄`() {
        val row = "val x = 42 // 설명"
        val (spans, _) = spansOf(kotlin, row)
        assertEquals(listOf(TokenKind.KEYWORD, TokenKind.NUMBER, TokenKind.COMMENT), kinds(spans))
        assertEquals(listOf("val", "42", "// 설명"), texts(row, spans))
    }

    /** **문자열 안의 `//` 는 주석이 아니다.** 훑는 순서가 뒤집히면 여기서 깨진다. */
    @Test
    fun `문자열 안의 주석 기호`() {
        val row = """val u = "https://example.com" // 진짜 주석"""
        val (spans, _) = spansOf(kotlin, row)
        assertEquals(
            listOf(TokenKind.KEYWORD, TokenKind.STRING, TokenKind.COMMENT),
            kinds(spans),
        )
        assertEquals("\"https://example.com\"", texts(row, spans)[1])
    }

    @Test
    fun `블록 주석이 행을 넘는다`() {
        val (s1, st1) = spansOf(kotlin, "code /* 열고")
        assertEquals(TokenKind.COMMENT, s1.last().kind)
        assertTrue(st1 != RowHighlighter.START, "주석 상태가 이어져야 한다")

        val (s2, st2) = spansOf(kotlin, "가운데 줄", st1)
        assertEquals(listOf(TokenKind.COMMENT), kinds(s2))
        assertTrue(st2 != RowHighlighter.START)

        val (s3, st3) = spansOf(kotlin, "닫는다 */ val y = 1", st2)
        assertEquals(
            listOf(TokenKind.COMMENT, TokenKind.KEYWORD, TokenKind.NUMBER),
            kinds(s3),
        )
        assertEquals(RowHighlighter.START, st3)
    }

    /** 코틀린·러스트의 블록 주석은 겹친다. 깊이를 안 세면 안쪽에서 일찍 닫힌다. */
    @Test
    fun `겹친 블록 주석`() {
        val (_, st1) = spansOf(kotlin, "/* 바깥 /* 안쪽")
        val (_, st2) = spansOf(kotlin, "*/ 아직 주석이다", st1)
        assertTrue(st2 != RowHighlighter.START, "안쪽만 닫혔으니 아직 주석이어야 한다")
        val (s3, st3) = spansOf(kotlin, "*/ val z = 0", st2)
        assertEquals(RowHighlighter.START, st3)
        assertEquals(listOf(TokenKind.COMMENT, TokenKind.KEYWORD, TokenKind.NUMBER), kinds(s3))
    }

    @Test
    fun `세 겹 따옴표가 행을 넘는다`() {
        val (_, st1) = spansOf(kotlin, "val s = \"\"\"여는 줄")
        val (s2, st2) = spansOf(kotlin, "가운데 \"따옴표\" 하나", st1)
        assertEquals(listOf(TokenKind.STRING), kinds(s2))
        val (_, st3) = spansOf(kotlin, "닫는다\"\"\"", st2)
        assertEquals(RowHighlighter.START, st3)
    }

    /**
     * **닫히지 않은 한 줄 문자열은 줄을 넘지 못한다.** 넘게 두면 따옴표가 하나 빠진
     * 파일에서 나머지 전부가 문자열 색이 된다.
     */
    @Test
    fun `한 줄 문자열은 줄 끝에서 끝난다`() {
        val (_, st) = spansOf(kotlin, "val s = \"닫지 않았다")
        assertTrue(st != RowHighlighter.START, "행 안에서는 아직 문자열이다")
        assertEquals(RowHighlighter.START, kotlin.afterNewline(st), "줄바꿈이 씻어야 한다")
    }

    /** JSON 에는 주석이 없다. 값 안의 `//` 를 주석으로 칠하면 뒤가 통째로 회색이 된다. */
    @Test
    fun `JSON 은 주석을 모른다`() {
        val row = """{"url": "http://a", "n": 3, "ok": true}"""
        val (spans, _) = spansOf(json, row)
        assertEquals(
            listOf(TokenKind.STRING, TokenKind.STRING, TokenKind.STRING, TokenKind.NUMBER,
                TokenKind.STRING, TokenKind.LITERAL),
            kinds(spans),
        )
    }

    @Test
    fun `파이썬 문서 문자열`() {
        val (_, st1) = spansOf(python, "def f():")
        assertEquals(RowHighlighter.START, st1)
        val (_, st2) = spansOf(python, "    \"\"\"설명", st1)
        assertTrue(st2 != RowHighlighter.START)
        val (s3, st3) = spansOf(python, "    \"\"\"", st2)
        assertEquals(listOf(TokenKind.STRING), kinds(s3))
        assertEquals(RowHighlighter.START, st3)
    }

    @Test
    fun `XML 태그와 속성`() {
        val row = """<item id="3" 이름='값'>본문</item>"""
        val (spans, _) = spansOf(xml, row)
        assertEquals(
            listOf(
                TokenKind.TAG, TokenKind.ATTRIBUTE, TokenKind.STRING,
                TokenKind.ATTRIBUTE, TokenKind.STRING, TokenKind.TAG,
            ),
            kinds(spans),
        )
        assertEquals("<item", texts(row, spans)[0])
        assertEquals("\"3\"", texts(row, spans)[2])
    }

    @Test
    fun `XML 주석이 행을 넘는다`() {
        val (_, st1) = spansOf(xml, "<a/><!-- 여는 줄")
        val (s2, st2) = spansOf(xml, "가운데", st1)
        assertEquals(listOf(TokenKind.COMMENT), kinds(s2))
        val (s3, st3) = spansOf(xml, "닫는다 --><b/>", st2)
        assertEquals(listOf(TokenKind.COMMENT, TokenKind.TAG), kinds(s3))
        assertEquals(0, st3)
    }

    @Test
    fun `모르는 확장자는 칠하지 않는다`() {
        val h = TextLanguage.forFileName("메모.txt")
        assertEquals(PlainHighlighter, h)
        val (spans, _) = spansOf(h, "val x = 1 // 주석처럼 보이지만 아니다")
        assertTrue(spans.isEmpty())
    }

    @Test
    fun `이름 전체로 고르는 것들`() {
        assertEquals("셸", TextLanguage.forFileName("Makefile").label)
        assertEquals("셸", TextLanguage.forFileName("Dockerfile").label)
        assertEquals("설정 파일", TextLanguage.forFileName(".gitignore").label)
    }

    // ---- 앵커 상태표 ------------------------------------------------------------

    private fun sourceOf(bytes: ByteArray): (Long) -> InputStream = { offset ->
        ByteArrayInputStream(bytes, offset.toInt(), bytes.size - offset.toInt())
    }

    /**
     * **이 시험이 이 기능의 전부다.**
     *
     * 파일 한가운데로 뛰어들었을 때 그 행이 블록 주석 안이라는 것을 알아야 한다.
     * 앵커 상태표가 없으면 거기서부터 '평범' 으로 시작해 주석이 코드 색으로 칠해진다.
     */
    @Test
    fun `앵커 한가운데로 뛰어들어도 주석이 이어진다`() = runTest {
        val text = buildString {
            repeat(100) { append("val a$it = $it\n") }
            append("/* 아주 긴 주석이 여기서 열린다\n")
            repeat(400) { append("주석 속 $it 줄. val 처럼 보이는 낱말도 들어 있다.\n") }
            append("*/\n")
            repeat(50) { append("val b$it = $it\n") }
        }
        val cs = TextEncoding.UTF_8.charset!!
        val bytes = text.toByteArray(cs)
        val open = sourceOf(bytes)
        val index = TextIndexer.index(open, bytes.size.toLong(), TextEncoding.UTF_8)
        assertTrue(index.anchorCount > 1, "앵커가 둘 이상이어야 의미 있는 시험이다")

        val hl = TextHighlight.prepare(open, TextEncoding.UTF_8, index, kotlin)
        assertNotNull(hl)
        assertTrue(hl.hasStateTable)

        // 주석 한가운데(300행쯤)로 뛰어든다.
        val rows = RowReader.read(open, TextEncoding.UTF_8, index, 300, 2, hl)
        assertEquals(2, rows.size)
        for (r in rows) {
            assertEquals(listOf(TokenKind.COMMENT), r.spans.map { it.kind }, "주석이어야 한다: ${r.text}")
            assertEquals(0, r.spans.single().start)
            assertEquals(r.text.length, r.spans.single().end)
        }

        // 주석이 끝난 뒤는 다시 코드다.
        val after = RowReader.read(open, TextEncoding.UTF_8, index, 502, 1, hl)
        assertTrue(after.single().spans.any { it.kind == TokenKind.KEYWORD }, "다시 코드여야 한다")
    }

    /** 상태가 행을 넘지 않는 언어는 **크기와 상관없이** 칠한다. */
    @Test
    fun `상태 없는 언어는 상태표가 필요 없다`() = runTest {
        val yaml = TextLanguage.forFileName("a.yaml")
        val bytes = "key: value # 주석\n".repeat(10).toByteArray()
        val open = sourceOf(bytes)
        val index = TextIndexer.index(open, bytes.size.toLong(), TextEncoding.UTF_8)
        val hl = TextHighlight.prepare(open, TextEncoding.UTF_8, index, yaml, maxBytes = 1)
        assertNotNull(hl)
        assertTrue(!hl.hasStateTable, "표 없이 돌아야 한다")
    }

    /** 상한을 넘으면 **끈다.** 반쯤 맞는 색을 내놓지 않는다. */
    @Test
    fun `상한을 넘으면 강조를 끈다`() = runTest {
        val bytes = "val x = 1\n".repeat(10).toByteArray()
        val open = sourceOf(bytes)
        val index = TextIndexer.index(open, bytes.size.toLong(), TextEncoding.UTF_8)
        assertNull(TextHighlight.prepare(open, TextEncoding.UTF_8, index, kotlin, maxBytes = 4))
    }

    /** 긴 줄이 잘려도 문자열 색이 끊기지 않는다 — 자른 자리는 줄바꿈이 아니다. */
    @Test
    fun `잘린 긴 줄에서 문자열이 이어진다`() = runTest {
        val long = "val s = \"" + "가".repeat(io.github.donggi.iroiroviewer.safety.TextLimits.SEGMENT_CHARS * 2) + "\"\n"
        val bytes = long.toByteArray()
        val open = sourceOf(bytes)
        val index = TextIndexer.index(open, bytes.size.toLong(), TextEncoding.UTF_8)
        val hl = TextHighlight.prepare(open, TextEncoding.UTF_8, index, kotlin)
        assertNotNull(hl)
        val rows = RowReader.read(open, TextEncoding.UTF_8, index, 0, 5, hl)
        assertTrue(rows.size >= 3, "세 행 이상으로 나뉘어야 한다")
        // 둘째 행은 통째로 문자열이어야 한다.
        val second = rows[1]
        assertEquals(listOf(TokenKind.STRING), second.spans.map { it.kind })
        assertEquals(second.text.length, second.spans.single().end)
    }
}
