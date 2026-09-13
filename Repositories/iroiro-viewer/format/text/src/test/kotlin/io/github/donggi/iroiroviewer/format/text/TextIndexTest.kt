package io.github.donggi.iroiroviewer.format.text

import io.github.donggi.iroiroviewer.charset.TextEncoding
import io.github.donggi.iroiroviewer.safety.TextLimits
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 색인과 행 읽기.
 *
 * **이 시험의 중심은 등가성이다** — 앵커에서 뛰어들어 읽은 행이 파일을 통째로 디코드해
 * 줄로 나눈 것과 **글자 하나까지 같아야** 한다. 색인의 정당성이 전적으로 여기 걸려 있고,
 * 어긋나면 사용자는 엉뚱한 줄을 보면서도 그것을 알 방법이 없다.
 */
class TextIndexTest {

    private fun sourceOf(bytes: ByteArray): (Long) -> InputStream = { offset ->
        ByteArrayInputStream(bytes, offset.toInt(), bytes.size - offset.toInt())
    }

    /** 파일을 통째로 읽어 줄로 나눈다. **정답지다.** */
    private fun goldenRows(text: String): List<String> {
        val lines = ArrayList<String>()
        var i = 0
        val sb = StringBuilder()
        while (i < text.length) {
            val c = text[i]
            when {
                c == '\r' -> {
                    lines += sb.toString(); sb.setLength(0)
                    i++
                    if (i < text.length && text[i] == '\n') i++
                }
                c == '\n' -> {
                    lines += sb.toString(); sb.setLength(0)
                    i++
                }
                else -> {
                    sb.append(c); i++
                }
            }
        }
        if (sb.isNotEmpty()) lines += sb.toString()
        // 긴 줄은 조각으로 나뉜다.
        return lines.flatMap { line ->
            if (line.length <= TextLimits.SEGMENT_CHARS) listOf(line)
            else line.chunked(TextLimits.SEGMENT_CHARS)
        }
    }

    private suspend fun roundTrip(text: String, enc: TextEncoding) {
        val cs = enc.charset ?: return
        val bytes = text.toByteArray(cs)
        val open = sourceOf(bytes)
        val index = TextIndexer.index(open, bytes.size.toLong(), enc)
        val golden = goldenRows(text)

        assertEquals(golden.size, index.rowCount, "${enc.label}: 행 수가 다르다")

        // ① 처음부터 전부 읽기.
        val all = RowReader.read(open, enc, index, 0, golden.size + 10)
        assertEquals(golden, all.map { it.text }, "${enc.label}: 통짜 읽기가 정답지와 다르다")

        // ② **앵커에서 뛰어들어 읽기.** 여기가 이 시험의 핵심이다.
        for (from in golden.indices step maxOf(1, golden.size / 7)) {
            val part = RowReader.read(open, enc, index, from, 3)
            val want = golden.subList(from, minOf(from + 3, golden.size))
            assertEquals(want, part.map { it.text }, "${enc.label}: $from 행부터 뛰어들었을 때")
        }
    }

    @Test
    fun `줄로 나눈 것이 통짜 디코딩과 같다 — 인코딩별`() = runTest {
        // 본문에 줄표(U+2014)를 쓰지 않는다. **CP949 도 Shift_JIS 도 그 글자가 없다** —
        // 둘 다 U+2015(가로줄)만 갖는다. 인코딩하면 `?` 로 대체되어 정답지와 어긋나고,
        // 그러면 시험이 잡는 것은 색인이 아니라 시험 자신의 결함이 된다.
        val text = buildString {
            repeat(300) { i ->
                append("줄 $i. 한글과 ASCII 가 섞인 평범한 줄입니다. line $i\n")
            }
        }
        for (enc in listOf(TextEncoding.UTF_8, TextEncoding.CP949)) roundTrip(text, enc)
    }

    /** 시험 글이 그 인코딩으로 **손실 없이** 적히는가. 아니면 시험이 거짓말을 한다. */
    private fun assertEncodable(text: String, enc: TextEncoding) {
        val cs = enc.charset ?: return
        val back = String(text.toByteArray(cs), cs)
        assertEquals(text, back, "${enc.label}: 시험 글이 이 인코딩으로 안 적힌다")
    }

    @Test
    fun `일본어도 같다`() = runTest {
        val text = buildString {
            repeat(200) { i -> append("行 $i. 日本語のテキストです。カタカナもあります。\n") }
        }
        assertEncodable(text, TextEncoding.SHIFT_JIS)
        roundTrip(text, TextEncoding.SHIFT_JIS)
        roundTrip(text, TextEncoding.UTF_8)
    }

    /**
     * **줄 끝 세 종류.** 단독 `\r` 을 빠뜨리면 옛 맥 파일이 통째로 한 줄이 된다.
     */
    @Test
    fun `줄 끝 세 종류를 모두 가른다`() = runTest {
        roundTrip("가\n나\n다\n", TextEncoding.UTF_8)
        roundTrip("가\r\n나\r\n다\r\n", TextEncoding.UTF_8)
        roundTrip("가\r나\r다\r", TextEncoding.UTF_8)
        // 섞인 것도.
        roundTrip("가\r\n나\n다\r라\n", TextEncoding.UTF_8)
    }

    /**
     * **줄바꿈 없는 긴 줄.** 480 KB 짜리 미니파이 JS 가 실재한다.
     * 거부하지 않고 여러 행으로 나눠야 한다.
     */
    @Test
    fun `긴 줄을 거부하지 않고 나눈다`() = runTest {
        val long = "x".repeat(TextLimits.SEGMENT_CHARS * 3 + 17)
        val cs = TextEncoding.UTF_8.charset!!
        val bytes = long.toByteArray(cs)
        val open = sourceOf(bytes)
        val index = TextIndexer.index(open, bytes.size.toLong(), TextEncoding.UTF_8)

        assertEquals(4, index.rowCount, "4행으로 나뉘어야 한다")
        assertEquals(1, index.lineCount, "논리 줄은 하나다")

        val rows = RowReader.read(open, TextEncoding.UTF_8, index, 0, 10)
        assertEquals(4, rows.size)
        assertEquals(TextLimits.SEGMENT_CHARS, rows[0].text.length)
        assertEquals(17, rows[3].text.length)
        // 줄 번호는 전부 같고, 둘째 행부터 이어짐 표시가 붙는다.
        assertTrue(rows.all { it.line == 0 })
        assertFalse(rows[0].continuation)
        assertTrue(rows[1].continuation && rows[2].continuation && rows[3].continuation)
        assertEquals(long, rows.joinToString("") { it.text })
    }

    /** 긴 줄을 **한글로** 채우면 조각 경계가 글자 한가운데로 떨어지면 안 된다. */
    @Test
    fun `긴 한글 줄의 조각 경계가 글자를 자르지 않는다`() = runTest {
        val long = "한글".repeat(TextLimits.SEGMENT_CHARS)
        for (enc in listOf(TextEncoding.UTF_8, TextEncoding.CP949)) {
            val cs = enc.charset ?: continue
            val bytes = long.toByteArray(cs)
            val open = sourceOf(bytes)
            val index = TextIndexer.index(open, bytes.size.toLong(), enc)
            val rows = RowReader.read(open, enc, index, 0, index.rowCount + 2)
            val joined = rows.joinToString("") { it.text }
            assertEquals(long, joined, "${enc.label}: 긴 한글 줄이 깨졌다")
            assertTrue(rows.none { it.text.contains('�') }, "${enc.label}: 깨진 글자가 생겼다")
        }
    }

    @Test
    fun `마지막 줄에 줄바꿈이 없어도 센다`() = runTest {
        val text = "첫 줄\n둘째 줄\n마지막은 줄바꿈 없음"
        roundTrip(text, TextEncoding.UTF_8)
        val cs = TextEncoding.UTF_8.charset!!
        val bytes = text.toByteArray(cs)
        val index = TextIndexer.index(sourceOf(bytes), bytes.size.toLong(), TextEncoding.UTF_8)
        assertEquals(3, index.rowCount)
    }

    @Test
    fun `빈 파일과 줄바꿈만 있는 파일`() = runTest {
        val empty = ByteArray(0)
        val i1 = TextIndexer.index(sourceOf(empty), 0, TextEncoding.UTF_8)
        assertEquals(0, i1.rowCount)

        val newlines = "\n\n\n".toByteArray()
        val i2 = TextIndexer.index(sourceOf(newlines), 3, TextEncoding.UTF_8)
        assertEquals(3, i2.rowCount)
        val rows = RowReader.read(sourceOf(newlines), TextEncoding.UTF_8, i2, 0, 5)
        assertEquals(listOf("", "", ""), rows.map { it.text })
    }

    /** 앵커가 성기게, 그러나 **반드시 행 시작에** 놓인다. */
    @Test
    fun `앵커가 성기고 행 시작에 놓인다`() = runTest {
        val text = buildString { repeat(2000) { append("줄 $it 내용이 조금 있습니다.\n") } }
        val cs = TextEncoding.UTF_8.charset!!
        val bytes = text.toByteArray(cs)
        val index = TextIndexer.index(sourceOf(bytes), bytes.size.toLong(), TextEncoding.UTF_8)

        // 줄마다 앵커를 두면 2000개다. 성기게 두었으므로 훨씬 적어야 한다.
        assertTrue(index.anchorCount < 2000 / 4, "앵커가 너무 촘촘하다: ${index.anchorCount}")
        assertTrue(index.anchorCount > 0)

        // 모든 앵커에서 읽은 첫 행이 정답지의 그 행과 같아야 한다 = 행 시작이라는 뜻이다.
        val golden = goldenRows(text)
        for (row in listOf(0, 300, 700, 1300, 1999)) {
            val r = RowReader.read(sourceOf(bytes), TextEncoding.UTF_8, index, row, 1)
            assertEquals(golden[row], r.single().text, "$row 행")
        }
    }

    @Test
    fun `UTF-16 은 디코드하며 센다`() = runTest {
        // `갊`(U+AC0A)은 UTF-16BE 로 `AC 0A` 다 — 바이트를 세면 줄이 여기서 갈린다.
        val text = "갊갊갊\n둘째 줄\n갊\n"
        for (enc in listOf(TextEncoding.UTF_16LE, TextEncoding.UTF_16BE)) {
            val cs = enc.charset ?: continue
            val bytes = text.toByteArray(cs)
            val index = TextIndexer.index(sourceOf(bytes), bytes.size.toLong(), enc)
            assertEquals(3, index.rowCount, "${enc.label}: 줄 수가 틀렸다")
        }
    }

    @Test
    fun `찾기가 가짜 일치를 내지 않는다`() = runTest {
        // CP949 의 후행 바이트는 ASCII 범위와 겹친다. 바이트로 찾으면 두 글자에 걸친
        // 바이트가 우연히 맞아떨어진다. 문자로 찾으면 그 일이 없다.
        val text = "첫 줄에 찾을말 있음\n둘째 줄\n셋째 줄에도 찾을말\n"
        val cs = TextEncoding.CP949.charset ?: return@runTest
        val bytes = text.toByteArray(cs)
        val open = sourceOf(bytes)
        val index = TextIndexer.index(open, bytes.size.toLong(), TextEncoding.CP949)
        val hits = RowReader.search(open, TextEncoding.CP949, index, "찾을말", ignoreCase = false)
        assertEquals(listOf(0, 2), hits)
    }
}
