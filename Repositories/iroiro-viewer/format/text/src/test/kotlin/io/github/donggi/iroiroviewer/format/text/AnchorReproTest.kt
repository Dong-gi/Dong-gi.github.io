package io.github.donggi.iroiroviewer.format.text

import io.github.donggi.iroiroviewer.charset.TextEncoding
import io.github.donggi.iroiroviewer.safety.TextLimits
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **성긴 색인이 실제로 성긴가.**
 *
 * 이 시험은 실측이 잡은 결함에서 나왔다 — CRLF 파일에서 앵커가 **첫 개 하나뿐**이었다.
 * 줄 끝마다 `\r` 이 걸려 "캐리지리턴 뒤에는 앵커를 찍지 않는다" 는 규칙이 건너뛰기가
 * 아니라 **영구 억제**가 된 것이다. 20만 줄 로그의 끝으로 뛰면 16 MB 를 처음부터 다시
 * 읽었고, 앱은 아무 오류도 내지 않고 그냥 느렸다.
 *
 * 행 수와 글자는 그때도 옳았다. 그래서 [TextIndexTest] 의 등가성 시험이 전부 통과했다 —
 * **성능 불변식은 따로 단언해야 한다.**
 */
class AnchorReproTest {

    private fun sourceOf(bytes: ByteArray): (Long) -> InputStream = { offset ->
        ByteArrayInputStream(bytes, offset.toInt(), bytes.size - offset.toInt())
    }

    private suspend fun anchorsOf(text: String): RowIndex {
        val bytes = text.toByteArray()
        return TextIndexer.index(sourceOf(bytes), bytes.size.toLong(), TextEncoding.UTF_8)
    }

    /** 줄 끝 세 종류가 **같은 수의 앵커**를 내야 한다. 하나만 다르면 그것이 결함이다. */
    @Test
    fun `줄 끝 종류가 앵커 수를 바꾸지 않는다`() = runTest {
        val rows = 5_000
        val body = { nl: String -> (0 until rows).joinToString("") { "로그 한 줄 $it$nl" } }

        val lf = anchorsOf(body("\n"))
        val crlf = anchorsOf(body("\r\n"))
        val cr = anchorsOf(body("\r"))

        assertEquals(rows, lf.rowCount, "LF 행 수")
        assertEquals(rows, crlf.rowCount, "CRLF 행 수")
        assertEquals(rows, cr.rowCount, "CR 행 수")

        val expected = rows / TextLimits.ANCHOR_ROWS
        assertTrue(lf.anchorCount >= expected, "LF: 앵커 ${lf.anchorCount}개")
        assertTrue(crlf.anchorCount >= expected, "CRLF: 앵커 ${crlf.anchorCount}개")
        assertTrue(cr.anchorCount >= expected, "CR: 앵커 ${cr.anchorCount}개")
    }

    /**
     * 미뤄 찍은 앵커가 **정말 줄 시작**인가.
     *
     * 한 바이트 어긋나면 CRLF 파일에서 모든 행이 `\n` 으로 시작하는 빈 행이 하나씩
     * 끼어든다. 등가성으로 확인한다 — 앵커에서 뛰어든 것과 통짜로 읽은 것이 같아야 한다.
     */
    @Test
    fun `CRLF 파일에서 앵커로 뛰어들어도 같다`() = runTest {
        val rows = 3_000
        val text = (0 until rows).joinToString("") { "CRLF 줄 $it 입니다\r\n" }
        val bytes = text.toByteArray()
        val open = sourceOf(bytes)
        val index = TextIndexer.index(open, bytes.size.toLong(), TextEncoding.UTF_8)
        assertTrue(index.anchorCount > 10, "앵커가 ${index.anchorCount}개뿐이라 시험이 성립하지 않는다")

        for (row in listOf(0, 257, 999, 2_560, 2_999)) {
            val got = RowReader.read(open, TextEncoding.UTF_8, index, row, 1).single()
            assertEquals("CRLF 줄 $row 입니다", got.text, "$row 행")
            assertEquals(row, got.line, "$row 행의 줄 번호")
        }
    }

    /** `\r` 만 쓰는 옛 맥 파일도 같다. 미룬 앵커가 다음 글자 자리에 찍혀야 한다. */
    @Test
    fun `CR 파일에서 앵커로 뛰어들어도 같다`() = runTest {
        val rows = 3_000
        val text = (0 until rows).joinToString("") { "맥 줄 $it 입니다\r" }
        val bytes = text.toByteArray()
        val open = sourceOf(bytes)
        val index = TextIndexer.index(open, bytes.size.toLong(), TextEncoding.UTF_8)
        assertTrue(index.anchorCount > 10, "앵커가 ${index.anchorCount}개뿐이다")

        for (row in listOf(0, 257, 1_500, 2_999)) {
            val got = RowReader.read(open, TextEncoding.UTF_8, index, row, 1).single()
            assertEquals("맥 줄 $row 입니다", got.text, "$row 행")
        }
    }

    /**
     * **앵커에서 목표 행까지 되읽는 양이 묶여 있는가.**
     *
     * 성긴 색인의 값은 '메모리를 아낀다' 가 아니라 '아껴도 되읽기가 늘지 않는다' 다.
     * 그것을 재는 유일한 방법이 앵커 사이의 바이트·행 간격이므로 여기서 단언한다.
     */
    @Test
    fun `앵커 간격이 상한 안에 있다`() = runTest {
        val text = (0 until 5_000).joinToString("") { "줄 $it\r\n" }
        val bytes = text.toByteArray()
        val index = TextIndexer.index(sourceOf(bytes), bytes.size.toLong(), TextEncoding.UTF_8)

        var prevRow = -1
        for (i in 0 until index.anchorCount) {
            val row = index.anchorRowAt(i)
            if (prevRow >= 0) {
                assertTrue(
                    row - prevRow <= TextLimits.ANCHOR_ROWS + 1,
                    "앵커 간격이 ${row - prevRow}행이다",
                )
            }
            prevRow = row
        }
        assertTrue(index.rowCount - prevRow <= TextLimits.ANCHOR_ROWS + 1, "마지막 앵커 뒤가 너무 길다")
    }
}
