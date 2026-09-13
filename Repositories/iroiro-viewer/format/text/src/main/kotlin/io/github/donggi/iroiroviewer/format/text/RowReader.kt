package io.github.donggi.iroiroviewer.format.text

import io.github.donggi.iroiroviewer.charset.TextEncoding
import io.github.donggi.iroiroviewer.charset.WindowedDecoder
import io.github.donggi.iroiroviewer.safety.TextLimits
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.InputStream

/**
 * 화면에 그릴 한 칸.
 *
 * [line] 은 **논리 줄 번호**(0부터)다. 긴 줄이 여러 행으로 나뉘면 그 행들이 같은 줄
 * 번호를 갖고, 둘째 행부터 [continuation] 이 참이다 — 화면은 줄 번호를 한 번만 적고
 * 나머지는 비운다(`less` 도 `bat` 도 같다).
 */
data class TextRow(
    val text: String,
    val line: Int,
    val continuation: Boolean,
    /**
     * 색칠할 조각. 강조를 쓰지 않으면 빈 목록이다.
     *
     * **행 안의 좌표**라서 [text] 와 함께 있어야만 뜻이 있다. 따로 들고 다니면 창이
     * 바뀔 때 어긋나므로 한 몸으로 둔다.
     */
    val spans: List<Span> = emptyList(),
)

/**
 * 앵커에서 시작해 원하는 행까지 읽는다.
 *
 * ## 되읽기가 얼마나 되는가
 *
 * 앵커가 64 KiB 또는 256행마다 하나씩 있으므로, 아무 행이나 읽을 때 앞으로 버리는 양은
 * **64 KiB 와 한 행 가운데 작은 쪽**이다. 그래서 1 GB 파일의 한가운데로 뛰어도 읽는 것은
 * 64 KiB 뿐이다.
 *
 * ## 줄 끝을 문자 공간에서 가른다
 *
 * `\r\n`·`\n`·단독 `\r` 셋을 모두 줄 끝으로 본다. **단독 `\r` 을 빠뜨리면** 옛 맥에서 온
 * 파일이 통째로 한 줄이 되어 긴 줄 경로를 타고, 화면에는 한 덩어리가 뜬다. 공짜로 얻어지는
 * 것이 아니라 여기서 따로 다뤄야 하는 일이다.
 */
object RowReader {

    /**
     * [fromRow] 부터 [count] 행을 읽는다.
     *
     * @param openAt 주어진 바이트 오프셋에서 새 스트림을 연다.
     * @param highlight 문법 강조. null 이면 [TextRow.spans] 가 전부 비어 나온다.
     */
    suspend fun read(
        openAt: (Long) -> InputStream,
        encoding: TextEncoding,
        index: RowIndex,
        fromRow: Int,
        count: Int,
        highlight: TextHighlight? = null,
    ): List<TextRow> {
        if (count <= 0 || fromRow < 0) return emptyList()
        val cs = encoding.charset ?: return emptyList()
        val anchor = index.anchorFor(fromRow)
            ?: RowIndex.Anchor(0, 0L, 0, 0)

        val out = ArrayList<TextRow>(count)
        var row = anchor.row
        var line = anchor.line
        var continuation = false
        val sb = StringBuilder(TextLimits.SEGMENT_CHARS)
        // 앞선 글자가 캐리지리턴이었는가. `\r\n` 을 두 줄로 세지 않으려고 기억한다.
        var pendingCr = false
        // **되읽기 구간도 훑는다.** 버릴 행이라도 색칠 상태는 그 행들을 지나며 자란다 —
        // 건너뛰면 앵커 바로 뒤에서 열린 블록 주석이 목표 행에서 사라진다.
        var hlState = highlight?.stateAt(anchor.index) ?: 0

        openAt(anchor.byteOffset).use { stream ->
            WindowedDecoder(cs.newDecoder()).decodeAll(stream) { buf, off, len ->
                var i = off
                val end = off + len
                while (i < end) {
                    if (out.size >= count) return@decodeAll
                    val c = buf[i]
                    i++

                    if (pendingCr) {
                        pendingCr = false
                        // `\r` 바로 뒤의 `\n` 은 같은 줄 끝의 일부다. 건너뛴다.
                        if (c == '\n') continue
                    }

                    when {
                        c == '\r' || c == '\n' -> {
                            if (c == '\r') pendingCr = true
                            hlState = emit(out, sb, row, line, continuation, fromRow, highlight, hlState)
                            // **진짜 줄바꿈에서만** 한 줄짜리 문자열 상태를 씻는다.
                            // 4,096자에서 자른 자리는 줄이 끝난 것이 아니다.
                            highlight?.let { hlState = it.highlighter.afterNewline(hlState) }
                            row++
                            line++
                            continuation = false
                        }
                        else -> {
                            sb.append(c)
                            if (sb.length >= TextLimits.SEGMENT_CHARS) {
                                hlState =
                                    emit(out, sb, row, line, continuation, fromRow, highlight, hlState)
                                row++
                                // 줄 번호는 그대로. **같은 논리 줄의 이어지는 행**이다.
                                continuation = true
                            }
                        }
                    }
                }
            }
        }
        // 파일 끝에 줄바꿈이 없으면 남은 것이 마지막 행이다.
        if (sb.isNotEmpty() && out.size < count) {
            emit(out, sb, row, line, continuation, fromRow, highlight, hlState)
        }
        return out
    }

    /**
     * 모아 둔 글자를 한 행으로 내보낸다. [fromRow] 앞의 것은 버린다(되읽기 구간).
     *
     * @return 이 행을 지난 뒤의 강조 상태.
     */
    private fun emit(
        out: MutableList<TextRow>,
        sb: StringBuilder,
        row: Int,
        line: Int,
        continuation: Boolean,
        fromRow: Int,
        highlight: TextHighlight?,
        hlState: Int,
    ): Int {
        val text = sb.toString()
        sb.setLength(0)
        if (highlight == null) {
            if (row >= fromRow) out.add(TextRow(text, line, continuation))
            return hlState
        }
        // 버릴 행도 훑는다 — 값은 화면이 아니라 **상태**다.
        val spans = ArrayList<Span>(8)
        val next = highlight.highlighter.highlight(text, hlState, spans)
        if (row >= fromRow) out.add(TextRow(text, line, continuation, spans))
        return next
    }

    /**
     * 이 논리 줄이 시작하는 **정확한** 행 번호. 못 찾으면 -1.
     *
     * [RowIndex.rowOfLine] 은 앵커까지만 알려 준다 — 앵커가 성기므로 그 앵커의 줄 번호는
     * 찾는 줄보다 **작을 수 있다.** 나머지는 여기서 앞으로 세어 맞춘다. 되읽기는 앵커
     * 간격(64 KiB 또는 256행)으로 묶이므로 1 GB 파일에서도 한 번의 짧은 읽기다.
     */
    suspend fun rowOfLine(
        openAt: (Long) -> InputStream,
        encoding: TextEncoding,
        index: RowIndex,
        line: Int,
    ): Int {
        if (line < 0 || index.rowCount == 0) return -1
        var row = index.rowOfLine(line).coerceAtLeast(0)
        val batch = 512
        while (row < index.rowCount) {
            currentCoroutineContext().ensureActive()
            val rows = read(openAt, encoding, index, row, batch)
            if (rows.isEmpty()) return -1
            for ((k, r) in rows.withIndex()) {
                if (r.line == line && !r.continuation) return row + k
                // 줄 번호는 오름차순이다. 지나쳤으면 그 줄이 없다는 뜻이다.
                if (r.line > line) return -1
            }
            row += rows.size
        }
        return -1
    }

    /**
     * 파일 안에서 찾는다. **디코드한 문자로 찾는다.**
     *
     * 바이트로 찾으면 빠르지만 CP949·Shift_JIS 에서 **가짜 일치**가 난다 — 후행 바이트가
     * ASCII 범위와 겹쳐서, 두 글자에 걸친 바이트가 우연히 찾는 말과 같아진다. 그러면
     * 사용자가 찾지 않은 자리를 찾았다고 말하게 된다.
     *
     * @return 찾은 행 번호들. [TextLimits.MAX_SEARCH_HITS] 까지.
     */
    suspend fun search(
        openAt: (Long) -> InputStream,
        encoding: TextEncoding,
        index: RowIndex,
        query: String,
        ignoreCase: Boolean,
        onProgress: (rowsScanned: Int) -> Unit = {},
    ): List<Int> {
        if (query.isEmpty()) return emptyList()
        val hits = ArrayList<Int>()
        var row = 0
        var scanned = 0
        val batch = 512
        while (row < index.rowCount && hits.size < TextLimits.MAX_SEARCH_HITS) {
            currentCoroutineContext().ensureActive()
            val rows = read(openAt, encoding, index, row, batch)
            if (rows.isEmpty()) break
            for ((k, r) in rows.withIndex()) {
                if (r.text.contains(query, ignoreCase)) {
                    hits += row + k
                    if (hits.size >= TextLimits.MAX_SEARCH_HITS) break
                }
            }
            row += rows.size
            scanned += rows.size
            onProgress(scanned)
        }
        return hits
    }
}
