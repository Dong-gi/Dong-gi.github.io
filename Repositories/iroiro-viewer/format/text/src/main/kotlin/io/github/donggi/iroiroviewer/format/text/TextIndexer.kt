package io.github.donggi.iroiroviewer.format.text

import io.github.donggi.iroiroviewer.charset.TextEncoding
import io.github.donggi.iroiroviewer.charset.WindowedDecoder
import io.github.donggi.iroiroviewer.safety.TextLimits
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.InputStream

/**
 * 파일을 한 번 훑어 [RowIndex] 를 만든다.
 *
 * ## 두 갈래로 나뉘는 이유
 *
 * 줄을 어디서 나눌 것인가는 **인코딩이 정한다.**
 *
 * - **ASCII 호환**(UTF-8·CP949·Shift_JIS·GB18030·Big5) — 바이트 `0x0A` 를 세면 된다.
 *   전 코드포인트를 각 인코딩으로 인코딩해 산출 바이트에 `0x0A` 가 있는지 **전수 조사**한
 *   결과 0건이다. 즉 `0x0A` 는 언제나 진짜 줄바꿈이고, 디코드 없이 셀 수 있어 빠르다.
 * - **UTF-16** — `0x0A` 가 문자의 일부로 나타난다(8,695개 코드포인트. 예: `U+AC0A '갊'`
 *   = `AC 0A`). 바이트를 세면 **줄이 엉뚱한 자리에서 갈린다.** 그래서 디코드하며 센다.
 *
 * 이 갈림이 [TextEncoding.newlineCanHideInChar] 한 값에 걸려 있다.
 *
 * ## 긴 줄을 자르는 자리
 *
 * 줄 하나가 [TextLimits.SEGMENT_CHARS] 를 넘으면 여러 행으로 나눈다. 자르는 자리는
 * **디코드한 문자 경계**여야 한다 — 바이트에서 자르면 CP949·Shift_JIS 에서 글자가 깨진다.
 * 그래서 긴 줄만 골라 디코드한다. 바이트 길이가 [TextLimits.SEGMENT_CHARS] 이하인 줄은
 * 글자 수가 그보다 많을 수 없으므로(이 인코딩들은 문자당 최소 1바이트다) 디코드가 필요 없다.
 */
object TextIndexer {

    /**
     * @param openAt 주어진 바이트 오프셋에서 새 스트림을 연다. 여러 번 불릴 수 있다.
     * @param sourceBytes 파일 크기. 진행률과 색인 무효화에 쓴다.
     * @param onProgress 훑은 바이트와 지금까지의 행 수.
     */
    suspend fun index(
        openAt: (Long) -> InputStream,
        sourceBytes: Long,
        encoding: TextEncoding,
        bomLength: Int = 0,
        onProgress: (bytesRead: Long, rows: Int) -> Unit = { _, _ -> },
    ): RowIndex =
        if (encoding.newlineCanHideInChar) {
            indexByDecoding(openAt, sourceBytes, encoding, bomLength, onProgress)
        } else {
            indexByBytes(openAt, sourceBytes, encoding, bomLength, onProgress)
        }

    /**
     * 바이트에서 `0x0A` 를 세는 빠른 길.
     *
     * 긴 줄을 만나야만 디코드한다 — 대부분의 파일에서 디코더가 한 번도 돌지 않는다.
     */
    private suspend fun indexByBytes(
        openAt: (Long) -> InputStream,
        sourceBytes: Long,
        encoding: TextEncoding,
        bomLength: Int,
        onProgress: (Long, Int) -> Unit,
    ): RowIndex {
        val anchors = AnchorBuilder()
        val buffer = ByteArray(TextLimits.WINDOW_BYTES)

        var pos = bomLength.toLong()
        var lineStart = pos
        var row = 0
        var line = 0
        var lastAnchorOffset = -1L
        var lastAnchorRow = -TextLimits.ANCHOR_ROWS

        // 첫 행은 언제나 앵커다. 그래야 파일 시작으로 돌아갈 때 되읽기가 없다.
        anchors.add(pos, 0, 0)
        lastAnchorOffset = pos
        lastAnchorRow = 0

        // 앞 버퍼의 마지막 바이트가 캐리지리턴이었는가. `\r\n` 이 버퍼 경계에 걸쳐도
        // 두 줄로 세지 않으려고 넘긴다.
        var pendingCr = false

        // **캐리지리턴 뒤로 미뤄 둔 앵커가 있는가.**
        //
        // `\r` 자리에서는 줄 시작을 아직 모른다 — 다음 바이트가 `\n` 이면 한 칸 더
        // 밀린다. 예전에는 그래서 그 자리의 앵커를 **버렸는데**, CRLF 파일은 모든 줄
        // 끝이 `\r` 이라 앵커가 첫 개 말고는 하나도 안 찍혔다. 20만 줄 로그의 앵커가
        // 1개가 되어, 끝으로 뛰면 16 MB 를 처음부터 다시 읽었다(실측에서 잡았다).
        // 버리지 말고 **한 바이트 미룬다.**
        var wantAnchor = false

        openAt(pos).use { stream ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val n = stream.read(buffer)
                if (n <= 0) break
                for (i in 0 until n) {
                    val b = buffer[i]
                    val here = pos + i

                    if (pendingCr) {
                        pendingCr = false
                        // `\r` 바로 뒤의 `\n` 은 같은 줄 끝의 일부다. 줄 시작만 한 칸 민다.
                        if (b == LF) lineStart = here + 1
                        // 이제 줄 시작을 확실히 안다. 미뤄 둔 앵커를 여기서 찍는다.
                        if (wantAnchor) {
                            wantAnchor = false
                            anchors.add(lineStart, row, line)
                            lastAnchorOffset = lineStart
                            lastAnchorRow = row
                        }
                        if (b == LF) continue
                    }

                    // **단독 `\r` 도 줄 끝이다.** 이것을 빠뜨리면 옛 맥에서 온 파일이
                    // 통째로 한 줄이 되어 긴 줄 경로를 타고, 색인(1행)과 읽기(여러 행)가
                    // 서로 다른 말을 하게 된다 — 시험이 그것을 잡았다.
                    //
                    // 바이트에서 `0x0D` 를 세도 되는 근거는 `0x0A` 와 같다: 전 코드포인트
                    // 전수 조사에서 ASCII 호환 인코딩의 산출 바이트에 `0x0D` 가 나오는
                    // 경우가 **0건**이다.
                    if (b != LF && b != CR) continue

                    val rowsInLine = rowsOfLine(openAt, encoding, lineStart, here)
                    // 긴 줄이 만드는 둘째 행부터는 앵커를 찍지 않는다 — 그 자리의 바이트
                    // 오프셋을 알려면 디코드해야 하고, 읽는 쪽이 줄 시작에서 앞으로
                    // 세는 것으로 충분하다(한 줄 안이므로 되읽기가 한 줄로 묶인다).
                    row += rowsInLine
                    line++
                    lineStart = here + 1
                    if (b == CR) pendingCr = true

                    val farEnough = lineStart - lastAnchorOffset >= TextLimits.ANCHOR_BYTES ||
                        row - lastAnchorRow >= TextLimits.ANCHOR_ROWS
                    if (farEnough) {
                        if (pendingCr) {
                            // 줄 시작이 아직 안 정해졌다. 다음 바이트에서 찍는다.
                            wantAnchor = true
                        } else {
                            anchors.add(lineStart, row, line)
                            lastAnchorOffset = lineStart
                            lastAnchorRow = row
                        }
                    }
                    if (row % TextLimits.PROGRESS_EVERY_ROWS == 0) onProgress(lineStart, row)
                }
                pos += n
            }
        }

        // 마지막 줄에 줄바꿈이 없으면 그것도 한 줄이다.
        if (lineStart < pos) {
            row += rowsOfLine(openAt, encoding, lineStart, pos)
            line++
        }
        onProgress(pos, row)
        return anchors.build(row, line, complete = true, sourceBytes = sourceBytes)
    }

    /**
     * 이 줄이 몇 행을 차지하는가.
     *
     * 바이트 길이가 [TextLimits.SEGMENT_CHARS] 이하이면 **읽지 않고** 1 이다 —
     * 이 인코딩들은 문자당 최소 1바이트라 글자 수가 바이트 수를 넘을 수 없다.
     * 그 덕에 평범한 파일에서는 디코더가 한 번도 돌지 않는다.
     */
    private fun rowsOfLine(
        openAt: (Long) -> InputStream,
        encoding: TextEncoding,
        start: Long,
        end: Long,
    ): Int {
        val bytes = end - start
        if (bytes <= TextLimits.SEGMENT_CHARS) return 1
        val cs = encoding.charset ?: return 1
        var chars = 0L
        openAt(start).use { stream ->
            WindowedDecoder(cs.newDecoder()).decodeAll(
                LimitedStream(stream, bytes),
            ) { _, _, len -> chars += len }
        }
        if (chars == 0L) return 1
        return ((chars + TextLimits.SEGMENT_CHARS - 1) / TextLimits.SEGMENT_CHARS).toInt()
    }

    /**
     * 디코드하며 세는 길. **UTF-16 전용이다.**
     *
     * 느리지만 다른 방법이 없다 — UTF-16 에서 `0x0A` 는 진짜 줄바꿈일 수도 있고 `갊`
     * 의 절반일 수도 있어서, 바이트만 보고는 구별할 방법이 원리적으로 없다.
     *
     * **앵커를 줄 시작마다 찍지 못한다.** 디코더가 '이 글자가 몇 번째 바이트였나' 를
     * 알려주지 않기 때문이다. 대신 **창 경계**에 찍는다 — 창 경계는 디코더가 소비한
     * 바이트 수로 정확히 알고, UTF-16 은 문자 크기가 2의 배수라 그 자리가 언제나
     * 문자 경계다. 되읽기는 한 창(64 KiB)으로 묶인다.
     */
    private suspend fun indexByDecoding(
        openAt: (Long) -> InputStream,
        sourceBytes: Long,
        encoding: TextEncoding,
        bomLength: Int,
        onProgress: (Long, Int) -> Unit,
    ): RowIndex {
        val cs = encoding.charset
            ?: return RowIndex.empty(sourceBytes)
        val anchors = AnchorBuilder()
        var row = 0
        var line = 0
        var charsInRow = 0

        anchors.add(bomLength.toLong(), 0, 0)

        openAt(bomLength.toLong()).use { stream ->
            var pendingCr = false
            WindowedDecoder(cs.newDecoder()).decodeAll(stream) { buf, off, len ->
                for (i in off until off + len) {
                    val c = buf[i]
                    if (pendingCr) {
                        pendingCr = false
                        // 캐리지리턴 바로 뒤의 줄바꿈은 같은 줄 끝의 일부다.
                        if (c == '\n') continue
                    }
                    when {
                        c == '\r' -> {
                            pendingCr = true
                            row++
                            line++
                            charsInRow = 0
                        }
                        c == '\n' -> {
                            row++
                            line++
                            charsInRow = 0
                        }
                        else -> {
                            // **4,097번째 글자가 올 때** 행을 넘긴다([RowReader] 와 같은 규칙). 4,096자째에서
                            // 넘기면 길이가 정확히 4,096의 배수인 줄이 빈 행을 하나 더 세어, 바이트로 세는
                            // 길(`ceil(글자 수 / 4,096)`)과 행 수가 어긋난다.
                            if (charsInRow >= TextLimits.SEGMENT_CHARS) {
                                row++
                                charsInRow = 0
                            }
                            charsInRow++
                        }
                    }
                }
            }
        }
        if (charsInRow > 0) {
            row++
            line++
        }
        onProgress(sourceBytes, row)
        return anchors.build(row, line, complete = true, sourceBytes = sourceBytes)
    }

    private const val LF: Byte = 0x0A
    private const val CR: Byte = 0x0D
}

/** 앞에서부터 [limit] 바이트만 내어 주는 스트림. 한 줄만 읽을 때 쓴다. */
internal class LimitedStream(
    private val delegate: InputStream,
    private val limit: Long,
) : InputStream() {
    private var read = 0L

    override fun read(): Int {
        if (read >= limit) return -1
        val b = delegate.read()
        if (b >= 0) read++
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (read >= limit) return -1
        val want = minOf(len.toLong(), limit - read).toInt()
        val n = delegate.read(b, off, want)
        if (n > 0) read += n
        return n
    }

    override fun close() = delegate.close()
}
