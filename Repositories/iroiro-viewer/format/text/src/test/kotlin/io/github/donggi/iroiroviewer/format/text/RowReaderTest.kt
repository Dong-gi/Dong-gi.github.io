package io.github.donggi.iroiroviewer.format.text

import io.github.donggi.iroiroviewer.charset.TextEncoding
import io.github.donggi.iroiroviewer.safety.TextLimits
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 14단계가 행 읽기에 더한 것 — 줄 끝 종류, 다 읽으면 멈추기, 4,096의 배수 길이 줄.
 */
class RowReaderTest {

    private fun sourceOf(bytes: ByteArray): (Long) -> InputStream = { offset ->
        ByteArrayInputStream(bytes, offset.toInt(), bytes.size - offset.toInt())
    }

    /** 몇 바이트를 실제로 읽어 갔는지 세는 원천. 성능 불변식을 **숫자로** 단언하려고 둔다. */
    private class Counting(bytes: ByteArray) {
        var read = 0L
        val open: (Long) -> InputStream = { offset ->
            object : FilterInputStream(ByteArrayInputStream(bytes, offset.toInt(), bytes.size - offset.toInt())) {
                override fun read(): Int = super.read().also { if (it >= 0) read++ }
                override fun read(b: ByteArray, off: Int, len: Int): Int =
                    super.read(b, off, len).also { if (it > 0) read += it }
            }
        }
    }

    private suspend fun rowsOf(text: String, enc: TextEncoding = TextEncoding.UTF_8): List<TextRow> {
        val bytes = text.toByteArray(enc.charset!!)
        val index = TextIndexer.index(sourceOf(bytes), bytes.size.toLong(), enc)
        return RowReader.read(sourceOf(bytes), enc, index, 0, index.rowCount + 5)
    }

    // ---- 줄 끝 ------------------------------------------------------------------

    @Test
    fun `줄 끝 세 종류와 파일 끝을 가른다`() = runTest {
        val rows = rowsOf("가\n나\r\n다\r라")
        assertEquals(listOf("가", "나", "다", "라"), rows.map { it.text })
        assertEquals(listOf(LineEnd.LF, LineEnd.CRLF, LineEnd.CR, LineEnd.NONE), rows.map { it.lineEnd })
    }

    @Test
    fun `파일 끝의 캐리지리턴은 CR 이다`() = runTest {
        val rows = rowsOf("a\r\nb\r")
        assertEquals(listOf(LineEnd.CRLF, LineEnd.CR), rows.map { it.lineEnd })
    }

    @Test
    fun `빈 줄도 줄 끝을 갖는다`() = runTest {
        val rows = rowsOf("\r\n\n\r")
        assertEquals(listOf("", "", ""), rows.map { it.text })
        assertEquals(listOf(LineEnd.CRLF, LineEnd.LF, LineEnd.CR), rows.map { it.lineEnd })
    }

    @Test
    fun `UTF-16 에서도 줄 끝을 가른다`() = runTest {
        for (enc in listOf(TextEncoding.UTF_16LE, TextEncoding.UTF_16BE)) {
            val rows = rowsOf("갊\r\n갊\n갊\r", enc)
            assertEquals(listOf(LineEnd.CRLF, LineEnd.LF, LineEnd.CR), rows.map { it.lineEnd }, enc.label)
        }
    }

    /** **줄 끝은 논리 줄의 마지막 행에만** 붙는다. 자른 앞 조각에 붙으면 줄이 끝나지 않은 자리에 표시가 뜬다. */
    @Test
    fun `긴 줄은 마지막 조각에만 줄 끝이 붙는다`() = runTest {
        val long = "x".repeat(TextLimits.SEGMENT_CHARS * 2 + 5)
        val rows = rowsOf("$long\r\nshort\n")
        assertEquals(4, rows.size)
        assertEquals(listOf(LineEnd.NONE, LineEnd.NONE, LineEnd.CRLF, LineEnd.LF), rows.map { it.lineEnd })
        assertEquals(listOf(false, true, true, false), rows.map { it.continuation })
    }

    /**
     * `\r` 과 `\n` 이 **디코더의 창 경계**에 걸친다. 앞 창의 마지막 글자가 `\r` 이면 그 자리에서는 CR 인지 CRLF 인지
     * 모른다 — 다음 창의 첫 글자를 보고 고쳐야 한다.
     */
    @Test
    fun `창 경계에 걸친 CRLF`() = runTest {
        // 64 KiB 창의 마지막 바이트가 `\r`, 다음 창의 첫 바이트가 `\n` 이 되게 한다.
        val first = "a".repeat(100) + "\n"
        val filler = "b".repeat(65_536 - first.length - 1)
        val text = first + filler + "\r\n" + "끝\n"
        assertEquals('\r', text[65_535])
        val rows = rowsOf(text)
        assertEquals(LineEnd.LF, rows[0].lineEnd)
        // 긴 줄(filler)이 여러 행으로 나뉘고 그 마지막 조각이 CRLF 로 끝난다.
        val lastOfFiller = rows.indexOfLast { it.line == 1 }
        assertEquals(LineEnd.CRLF, rows[lastOfFiller].lineEnd)
        assertTrue(rows.filter { it.line == 1 }.dropLast(1).all { it.lineEnd == LineEnd.NONE })
        assertEquals("끝", rows.last().text)
        assertEquals(LineEnd.LF, rows.last().lineEnd)
    }

    /**
     * **요청한 마지막 행이 `\r` 로 끝날 때.** 다 모았다고 그 자리에서 멈추면 뒤의 `\n` 을 못 보고 CR 로 낸다.
     * 창 하나를 읽을 때마다 그 창의 마지막 행만 줄 끝이 틀리게 된다.
     */
    @Test
    fun `마지막으로 요청한 행이 CRLF 로 끝난다`() = runTest {
        val text = (0 until 50).joinToString("") { "줄 $it\r\n" }
        val bytes = text.toByteArray()
        val index = TextIndexer.index(sourceOf(bytes), bytes.size.toLong(), TextEncoding.UTF_8)
        for (from in listOf(0, 7, 30)) {
            for (count in listOf(1, 2, 5)) {
                val rows = RowReader.read(sourceOf(bytes), TextEncoding.UTF_8, index, from, count)
                assertEquals(count, rows.size)
                assertTrue(rows.all { it.lineEnd == LineEnd.CRLF }, "$from 행부터 $count 행: ${rows.map { it.lineEnd }}")
                assertEquals("줄 $from", rows.first().text)
            }
        }
    }

    // ---- 4,096 의 배수 ---------------------------------------------------------------

    /**
     * 길이가 **정확히 4,096의 배수**인 줄. 예전 읽기는 4,096자째에서 곧바로 행을 내어 줄바꿈 자리에 빈 행을 하나 더
     * 만들었고, 색인(바이트로 센 `ceil(글자 수 / 4,096)`)과 행 수가 어긋나 **그 뒤의 행 번호가 전부 밀렸다.**
     */
    @Test
    fun `4096 의 배수 길이 줄에서 색인과 읽기가 같은 수를 센다`() = runTest {
        for (enc in listOf(TextEncoding.UTF_8, TextEncoding.CP949, TextEncoding.UTF_16LE)) {
            for (mult in 1..3) {
                val unit = if (enc == TextEncoding.UTF_8) "x" else "한"
                val long = unit.repeat(TextLimits.SEGMENT_CHARS * mult)
                val text = "첫 줄\n$long\n뒤 줄\n$long"
                val bytes = text.toByteArray(enc.charset!!)
                val index = TextIndexer.index(sourceOf(bytes), bytes.size.toLong(), enc)
                val rows = RowReader.read(sourceOf(bytes), enc, index, 0, index.rowCount + 5)
                val label = "${enc.label} ×$mult"
                assertEquals(1 + mult + 1 + mult, index.rowCount, "$label: 색인 행 수")
                assertEquals(index.rowCount, rows.size, "$label: 읽은 행 수")
                assertTrue(rows.none { it.text.isEmpty() }, "$label: 빈 이어짐 행이 생겼다")
                assertEquals("뒤 줄", rows[1 + mult].text, "$label: 뒤 줄의 행 번호가 밀렸다")
                // 뒤에서 뛰어들어도 같은 행이 나온다.
                val tail = RowReader.read(sourceOf(bytes), enc, index, index.rowCount - 1, 1)
                assertEquals(rows.last().text, tail.single().text, "$label: 마지막 행")
            }
        }
    }

    // ---- 다 읽으면 멈춘다 --------------------------------------------------------------

    /**
     * **창 하나를 읽는 데 파일 전체를 풀지 않는다.** 예전에는 원하는 행을 다 모아도 디코더가 스트림 끝까지 돌아,
     * 4 MiB 파일의 첫 10행을 읽는 데 4 MiB 를 읽었다.
     */
    @Test
    fun `앞 몇 행을 읽는 데 파일 끝까지 읽지 않는다`() = runTest {
        val text = (0 until 100_000).joinToString("") { "로그 한 줄 번호 $it 입니다\n" }
        val bytes = text.toByteArray()
        assertTrue(bytes.size > 3_000_000)
        val index = TextIndexer.index(sourceOf(bytes), bytes.size.toLong(), TextEncoding.UTF_8)

        val src = Counting(bytes)
        val rows = RowReader.read(src.open, TextEncoding.UTF_8, index, 0, 10)
        assertEquals(10, rows.size)
        // 디코더가 이미 받아 둔 창 하나(64 KiB)와 이월 몇 바이트까지만 허락한다.
        assertTrue(src.read <= 2L * TextLimits.WINDOW_BYTES, "읽은 바이트 ${src.read}")
    }

    /**
     * 예전 찾기는 512행씩 끊어 읽었고, 끊어 읽을 때마다 앵커 뒤 전체를 풀어 **파일 크기의 제곱**이었다(60,000행
     * 1.6 MB 에서 90 MB 남짓). 지금은 한 번 훑는다 — 파일 크기와 디코더의 창 하나를 넘지 않는다.
     */
    @Test
    fun `찾기가 파일을 한 번만 읽는다`() = runTest {
        val text = (0 until 60_000).joinToString("") { "row $it lorem ipsum dolor\n" }
        val bytes = text.toByteArray()
        val index = TextIndexer.index(sourceOf(bytes), bytes.size.toLong(), TextEncoding.UTF_8)
        val src = Counting(bytes)
        val progress = ArrayList<Int>()
        val hits = RowReader.search(src.open, TextEncoding.UTF_8, index, "row 59999 ", ignoreCase = false) {
            progress += it
        }
        assertEquals(listOf(59_999), hits)
        assertTrue(src.read <= bytes.size + 2L * TextLimits.WINDOW_BYTES, "읽은 바이트 ${src.read} / 파일 ${bytes.size}")
        // 진행률은 오름차순이고 끝에서 전체 행 수에 닿는다.
        assertEquals(progress.sorted(), progress)
        assertEquals(index.rowCount, progress.last())
    }

    /**
     * 색인을 만든 뒤 파일 끝에 줄이 덧붙었다(보고 있는 로그). 찾기는 **색인이 아는 행까지만** 훑는다 — 그 뒤의 일치는
     * 목록에 없는 행이라 화면이 갈 수 없고, 계속 자라는 파일에서는 훑기가 끝나지 않는다.
     */
    @Test
    fun `찾기는 색인이 아는 행까지만 훑는다`() = runTest {
        val before = (0 until 10).joinToString("") { "old $it\n" }
        val after = before + (0 until 50_000).joinToString("") { "new target $it\n" }
        val index = TextIndexer.index(sourceOf(before.toByteArray()), before.length.toLong(), TextEncoding.UTF_8)
        assertEquals(10, index.rowCount)
        val grown = after.toByteArray()
        val src = Counting(grown)
        val progress = ArrayList<Int>()
        val hits = RowReader.search(src.open, TextEncoding.UTF_8, index, "target", ignoreCase = false) { progress += it }
        assertEquals(emptyList(), hits)
        assertEquals(10, progress.last())
        assertTrue(src.read <= 2L * TextLimits.WINDOW_BYTES, "덧붙은 뒤를 끝까지 읽었다: ${src.read} / ${grown.size}")
        // 색인 안의 것은 그대로 찾는다.
        assertEquals(listOf(3), RowReader.search(sourceOf(grown), TextEncoding.UTF_8, index, "old 3", ignoreCase = false))
    }

    /** 한 번에 훑는 동안에도 **취소가 닿는다**. 훑기는 코루틴 밖의 콜백이라 잡을 직접 확인해야 한다. */
    @Test
    fun `찾기 도중 취소하면 거기서 멈춘다`() = runTest {
        val text = (0 until 200_000).joinToString("") { "row $it\n" }
        val bytes = text.toByteArray()
        val index = TextIndexer.index(sourceOf(bytes), bytes.size.toLong(), TextEncoding.UTF_8)
        val src = Counting(bytes)
        var calls = 0
        var finished = false
        val job = launch {
            val self = coroutineContext[Job]!!
            RowReader.search(src.open, TextEncoding.UTF_8, index, "없는 말", ignoreCase = true) {
                calls++
                if (calls == 2) self.cancel()
            }
            finished = true
        }
        job.join()
        assertTrue(job.isCancelled)
        assertFalse(finished)
        assertEquals(2, calls, "취소 뒤에도 진행률이 왔다")
        assertTrue(src.read < bytes.size / 2, "취소 뒤에도 끝까지 읽었다: ${src.read} / ${bytes.size}")
    }
}
