package io.github.donggi.iroiroviewer.format.text

import io.github.donggi.iroiroviewer.charset.TextEncoding
import io.github.donggi.iroiroviewer.charset.WindowedDecoder
import io.github.donggi.iroiroviewer.safety.TextLimits
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.InputStream
import java.nio.charset.Charset

/**
 * 한 행을 끝낸 줄 끝.
 *
 * 줄 끝 보기(14단계)가 쓴다. **행의 글자에 넣지 않고 따로 든다** — 글자에 섞으면 찾기가 그것을
 * 찾고, 강조의 좌표가 어긋나고, 줄 끝을 끄는 순간 모든 행을 다시 읽어야 한다.
 */
enum class LineEnd {
    /** 줄 끝이 아니다 — 긴 줄을 자른 앞 조각이거나, 줄바꿈 없이 끝난 파일의 마지막 행. */
    NONE,
    LF,
    CRLF,

    /** 단독 캐리지리턴(옛 맥). */
    CR,
}

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
    /**
     * 이 행을 끝낸 줄 끝. **논리 줄의 마지막 행에만** 붙는다 — 4,096자에서 자른 앞 조각은
     * [LineEnd.NONE] 이다. 거기에 표시를 달면 줄이 끝나지 않은 자리에 줄 끝이 보인다.
     */
    val lineEnd: LineEnd = LineEnd.NONE,
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
 * ## 다 읽었으면 멈춘다
 *
 * 원하는 행을 다 모으면 **스트림을 끊는다**([StoppableStream]). [WindowedDecoder.decodeAll] 은
 * 스트림 끝까지 돌고 받는 쪽이 멈추라고 말할 길이 없어서, 예전에는 콜백만 일찍 돌아오고
 * 디코딩은 **파일 끝까지** 계속됐다 — 창 하나(400행)를 읽을 때마다 앵커 뒤의 파일 전체를
 * 풀었고, 512행씩 끊어 읽던 찾기는 파일 크기의 **제곱**이었다(14단계가 찾기 진행률을 붙이며
 * 찾았다). 끊은 뒤에는 디코더가 이미 받아 둔 한 창(64 KiB)까지만 더 돈다. 찾기는 이제 끊어
 * 읽지 않고 **한 번에 훑는다**([scan]).
 *
 * ## 줄 끝을 문자 공간에서 가른다
 *
 * `\r\n`·`\n`·단독 `\r` 셋을 모두 줄 끝으로 본다. **단독 `\r` 을 빠뜨리면** 옛 맥에서 온
 * 파일이 통째로 한 줄이 되어 긴 줄 경로를 타고, 화면에는 한 덩어리가 뜬다. 공짜로 얻어지는
 * 것이 아니라 여기서 따로 다뤄야 하는 일이다.
 *
 * ## 긴 줄을 자르는 자리
 *
 * **4,097번째 글자가 올 때** 앞의 4,096자를 한 행으로 낸다. 4,096자째에서 곧바로 내면 길이가
 * 정확히 4,096의 배수인 줄이 끝에 **빈 이어짐 행**을 하나 더 만들어, 바이트로 센 색인([TextIndexer],
 * `ceil(글자 수 / 4,096)`)보다 행이 하나 많아진다 — 그 뒤의 모든 행 번호가 한 칸씩 밀리고 파일의
 * 마지막 행이 목록에서 사라진다(14단계가 줄 끝 표시를 붙이며 찾았다).
 */
object RowReader {

    /** 찾기·줄 찾기가 취소와 진행률을 보는 간격(행). */
    private const val CHECK_EVERY_ROWS = 512

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
        val out = ArrayList<TextRow>(count)
        scan(openAt, cs, index, fromRow, highlight) { r ->
            out.add(r)
            out.size < count
        }
        return out
    }

    /**
     * [fromRow] 부터 행을 하나씩 [onRow] 에 넘긴다. [onRow] 가 거짓을 돌려주면 그 자리에서 멈춘다.
     *
     * **캐리지리턴으로 끝난 행은 한 글자 늦게 넘긴다.** `\r` 자리에서는 그것이 단독 CR 인지 CRLF 의
     * 앞쪽인지 모른다 — 다음 글자가 `\n` 인지 보고 나서야 줄 끝이 정해진다. 그 글자가 디코더의 다음
     * 창에 있어도 같다. 다 모았다고 `\r` 자리에서 멈추면 창마다 마지막 행이 CR 로 잘못 나간다.
     */
    private fun scan(
        openAt: (Long) -> InputStream,
        cs: Charset,
        index: RowIndex,
        fromRow: Int,
        highlight: TextHighlight?,
        onRow: (TextRow) -> Boolean,
    ) {
        val anchor = index.anchorFor(fromRow)
            ?: RowIndex.Anchor(0, 0L, 0, 0)

        var row = anchor.row
        var line = anchor.line
        var continuation = false
        val sb = StringBuilder(TextLimits.SEGMENT_CHARS)
        // 앞선 글자가 캐리지리턴이었는가. `\r\n` 을 두 줄로 세지 않으려고 기억한다.
        var pendingCr = false
        // 캐리지리턴으로 끝나 줄 끝이 아직 안 정해진 행(버릴 행이면 null).
        var held: TextRow? = null
        // **되읽기 구간도 훑는다.** 버릴 행이라도 색칠 상태는 그 행들을 지나며 자란다 —
        // 건너뛰면 앵커 바로 뒤에서 열린 블록 주석이 목표 행에서 사라진다.
        var hlState = highlight?.stateAt(anchor.index) ?: 0
        var stopped = false

        /** 모아 둔 글자로 행을 만든다. [fromRow] 앞의 것은 상태만 굴리고 버린다(되읽기 구간). */
        fun take(lineEnd: LineEnd): TextRow? {
            val text = sb.toString()
            sb.setLength(0)
            val keep = row >= fromRow
            if (highlight == null) {
                return if (keep) TextRow(text, line, continuation, lineEnd = lineEnd) else null
            }
            // 버릴 행도 훑는다 — 값은 화면이 아니라 **상태**다.
            val spans = ArrayList<Span>(8)
            hlState = highlight.highlighter.highlight(text, hlState, spans)
            return if (keep) TextRow(text, line, continuation, spans, lineEnd) else null
        }

        StoppableStream(openAt(anchor.byteOffset)).use { stream ->
            fun deliver(r: TextRow) {
                if (stopped) return
                if (!onRow(r)) {
                    stopped = true
                    stream.stop()
                }
            }

            WindowedDecoder(cs.newDecoder()).decodeAll(stream) { buf, off, len ->
                var i = off
                val end = off + len
                while (i < end && !stopped) {
                    val c = buf[i]

                    if (pendingCr) {
                        pendingCr = false
                        val h = held
                        held = null
                        // `\r` 바로 뒤의 `\n` 은 같은 줄 끝의 일부다. 건너뛰고 줄 끝을 CRLF 로 정한다.
                        if (c == '\n') {
                            if (h != null) deliver(h.copy(lineEnd = LineEnd.CRLF))
                            i++
                            continue
                        }
                        if (h != null) deliver(h)
                        if (stopped) break
                    }
                    i++

                    if (c == '\r' || c == '\n') {
                        val r = take(if (c == '\r') LineEnd.CR else LineEnd.LF)
                        if (c == '\r') {
                            pendingCr = true
                            held = r
                        } else if (r != null) {
                            deliver(r)
                        }
                        // **진짜 줄바꿈에서만** 한 줄짜리 문자열 상태를 씻는다.
                        // 4,096자에서 자른 자리는 줄이 끝난 것이 아니다.
                        highlight?.let { hlState = it.highlighter.afterNewline(hlState) }
                        row++
                        line++
                        continuation = false
                    } else {
                        if (sb.length >= TextLimits.SEGMENT_CHARS) {
                            take(LineEnd.NONE)?.let(::deliver)
                            row++
                            // 줄 번호는 그대로. **같은 논리 줄의 이어지는 행**이다.
                            continuation = true
                        }
                        sb.append(c)
                    }
                }
            }
            if (!stopped) {
                // 파일 끝의 단독 `\r`.
                held?.let(::deliver)
                // 파일 끝에 줄바꿈이 없으면 남은 것이 마지막 행이다.
                if (sb.isNotEmpty()) take(LineEnd.NONE)?.let(::deliver)
            }
        }
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
        val cs = encoding.charset ?: return -1
        val job = currentCoroutineContext()[Job]
        val start = index.rowOfLine(line).coerceAtLeast(0)
        var rowNo = start
        var found = -1
        scan(openAt, cs, index, start, null) { r ->
            when {
                r.line == line && !r.continuation -> {
                    found = rowNo
                    false
                }
                // 줄 번호는 오름차순이다. 지나쳤으면 그 줄이 없다는 뜻이다.
                r.line > line -> false
                else -> {
                    rowNo++
                    if (rowNo % CHECK_EVERY_ROWS == 0) job?.ensureActive()
                    true
                }
            }
        }
        return found
    }

    /**
     * 파일 안에서 찾는다. **디코드한 문자로 찾는다.**
     *
     * 바이트로 찾으면 빠르지만 CP949·Shift_JIS 에서 **가짜 일치**가 난다 — 후행 바이트가
     * ASCII 범위와 겹쳐서, 두 글자에 걸친 바이트가 우연히 찾는 말과 같아진다. 그러면
     * 사용자가 찾지 않은 자리를 찾았다고 말하게 된다.
     *
     * **파일을 한 번만 훑는다.** 취소는 [CHECK_EVERY_ROWS] 행마다 본다 — 훑기가 코루틴 밖의
     * 콜백이라 거기서 잡(`Job`)을 직접 확인한다. 취소되면 `CancellationException` 이 디코더를
     * 뚫고 나와 스트림이 닫힌다.
     *
     * **색인이 아는 행까지만 훑는다**([RowIndex.rowCount]). 보고 있는 로그에 밖에서 줄이 덧붙으면 파일은 색인보다 길어지는데,
     * 그 뒤를 훑으면 목록에 없는 행을 '찾았다' 고 말하고(화면은 그 행으로 갈 수 없다) 계속 자라는 파일에서는 끝나지 않는다.
     *
     * @param onProgress 지금까지 훑은 행 수. **부르는 스레드에서** 불린다 — 받는 쪽이 화면 상태를
     *   고친다면 스스로 원자적으로 고쳐야 한다(`MutableStateFlow.update`). 마지막에 훑은 행 전체로
     *   한 번 더 불린다.
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
        if (query.isEmpty() || index.rowCount == 0) return emptyList()
        val cs = encoding.charset ?: return emptyList()
        val job = currentCoroutineContext()[Job]
        val hits = ArrayList<Int>()
        var rowNo = 0
        scan(openAt, cs, index, 0, null) { r ->
            if (r.text.contains(query, ignoreCase)) hits += rowNo
            rowNo++
            if (rowNo % CHECK_EVERY_ROWS == 0) {
                job?.ensureActive()
                onProgress(rowNo)
            }
            hits.size < TextLimits.MAX_SEARCH_HITS && rowNo < index.rowCount
        }
        currentCoroutineContext().ensureActive()
        onProgress(rowNo)
        return hits
    }
}

/**
 * [stop] 을 부른 뒤로는 스트림 끝(-1)을 내어 주는 스트림.
 *
 * [WindowedDecoder.decodeAll] 을 **밖에서 멈추는** 유일한 길이다. 디코더는 스트림 끝을 보면
 * 남은 바이트를 마무리하고 돌아온다 — 그 마무리가 반쯤 읽힌 글자를 `U+FFFD` 로 내더라도 받는
 * 쪽은 이미 멈춘 뒤라 버린다. `core:charset` 에 멈춤 인자를 더하지 않은 것은, 그 모듈의 계약이
 * 자막·판정과 함께 쓰는 것이라 여기 사정으로 넓히기보다 스트림 한 겹이 싸서다.
 */
internal class StoppableStream(private val delegate: InputStream) : InputStream() {
    @Volatile
    private var stopped = false

    fun stop() {
        stopped = true
    }

    override fun read(): Int = if (stopped) -1 else delegate.read()

    override fun read(b: ByteArray, off: Int, len: Int): Int =
        if (stopped) -1 else delegate.read(b, off, len)

    override fun close() = delegate.close()
}
