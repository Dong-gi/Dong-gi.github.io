package io.github.donggi.iroiroviewer.format.text

import io.github.donggi.iroiroviewer.charset.TextEncoding
import io.github.donggi.iroiroviewer.safety.TextLimits
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.InputStream

/**
 * 이 파일을 어떻게 칠할 것인가. **앵커마다의 시작 상태를 함께 든다.**
 *
 * ## 왜 상태표가 필요한가
 *
 * 읽기는 언제나 앵커에서 시작해 목표 행까지 앞으로 훑는다([RowReader]). 그러니 앵커와
 * 목표 행 사이의 상태는 그 훑기로 저절로 자란다 — 공짜다. 공짜가 아닌 것은 **앵커
 * 자신의 상태**다. 500 MB 파일 한가운데의 앵커가 블록 주석 안인지 아닌지는 파일
 * 앞에서부터 읽어야만 알 수 있다.
 *
 * 그래서 상태표를 미리 만든다. 앵커 하나에 `Int` 하나이므로 1 GB 파일(앵커 약 55,000개)
 * 이라도 220 KB 다. 비싼 것은 표가 아니라 **만드는 패스**다.
 *
 * ## 세 갈래
 *
 * | 언어 | 파일 크기 | 결과 |
 * |---|---|---|
 * | 상태가 행을 넘지 않는다(YAML·INI·셸·파이썬 아닌 것) | 아무 크기 | 표 없이 곧바로 칠한다 |
 * | 상태가 행을 넘는다 | [TextLimits.HIGHLIGHT_MAX_BYTES] 이하 | 한 번 훑어 표를 만든다 |
 * | 상태가 행을 넘는다 | 그보다 크다 | **강조를 끈다.** 화면이 그렇게 말한다 |
 *
 * 셋째 줄이 이 클래스가 `null` 을 돌려주는 유일한 경우다. 그 자리에서 색을 반쯤 맞히는
 * 선택지도 있지만(앵커마다 '평범' 으로 시작한다고 치기), 그러면 큰 로그 파일의 한가운데에서
 * 주석이 **뛰어든 자리마다 다르게** 칠해진다 — 틀린 색보다 색이 없는 편이 정직하다.
 */
class TextHighlight private constructor(
    val highlighter: RowHighlighter,
    /** 앵커마다의 시작 상태. null 이면 모든 앵커가 [RowHighlighter.START] 다. */
    private val anchorStates: IntArray?,
) {

    fun stateAt(anchorIndex: Int): Int =
        if (anchorStates == null || anchorIndex !in anchorStates.indices) RowHighlighter.START
        else anchorStates[anchorIndex]

    /** 표를 만들었는가. 진단 화면과 이 화면의 상태 표시가 쓴다. */
    val hasStateTable: Boolean get() = anchorStates != null

    companion object {

        /** 한 번에 읽는 행 수. 하나를 더 읽는 이유는 [prepare] 주석 참고. */
        private const val BATCH = 512

        /**
         * @return 강조를 쓸 수 있으면 [TextHighlight], 못 쓰면 null.
         */
        suspend fun prepare(
            openAt: (Long) -> InputStream,
            encoding: TextEncoding,
            index: RowIndex,
            highlighter: RowHighlighter,
            maxBytes: Long = TextLimits.HIGHLIGHT_MAX_BYTES,
            onProgress: (rows: Int) -> Unit = {},
        ): TextHighlight? {
            if (highlighter === PlainHighlighter) return null
            if (!highlighter.multiline) return TextHighlight(highlighter, null)
            if (index.sourceBytes > maxBytes) return null
            if (index.anchorCount == 0) return TextHighlight(highlighter, null)

            val states = IntArray(index.anchorCount)
            var state = RowHighlighter.START
            var row = 0
            var nextAnchor = 0
            val spans = ArrayList<Span>(16)

            while (row < index.rowCount) {
                currentCoroutineContext().ensureActive()
                // **한 행을 더 읽는다.** 어떤 행이 진짜 줄바꿈으로 끝났는지는 그 다음
                // 행의 `continuation` 으로만 알 수 있고, 그것을 모르면 한 줄짜리 문자열
                // 상태를 언제 씻을지 알 수 없다. 덤으로 읽은 행은 다음 판에서 다시 읽는다.
                val batch = RowReader.read(openAt, encoding, index, row, BATCH + 1)
                if (batch.isEmpty()) break
                val take = minOf(BATCH, batch.size)
                for (k in 0 until take) {
                    while (nextAnchor < states.size && index.anchorRowAt(nextAnchor) <= row) {
                        // 앵커 행이 바로 이 행이면 지금 상태가 그 앵커의 시작 상태다.
                        if (index.anchorRowAt(nextAnchor) == row) states[nextAnchor] = state
                        nextAnchor++
                    }
                    spans.clear()
                    state = highlighter.highlight(batch[k].text, state, spans)
                    val endsLine = k + 1 >= batch.size || !batch[k + 1].continuation
                    if (endsLine) state = highlighter.afterNewline(state)
                    row++
                }
                onProgress(row)
                if (batch.size <= BATCH) break
            }
            return TextHighlight(highlighter, states)
        }

        /** 상태표 없이 곧바로 쓰는 것. 상태가 행을 넘지 않는 언어에만 쓴다. */
        fun stateless(highlighter: RowHighlighter): TextHighlight =
            TextHighlight(highlighter, null)
    }
}
