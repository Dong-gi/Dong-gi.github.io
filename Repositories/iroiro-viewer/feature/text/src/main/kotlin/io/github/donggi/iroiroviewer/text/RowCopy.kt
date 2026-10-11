package io.github.donggi.iroiroviewer.text

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.text.selection.SelectionState
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.CancellationException

/**
 * 본문에서 고른 글을 클립보드에 넣는 규칙(글 고르기).
 *
 * ## 왜 Compose 의 복사를 그대로 쓰지 않는가
 *
 * 화면은 행마다 `Text` 하나다. `SelectionContainer` 는 여러 `Text` 에 걸친 선택을 **조각마다 줄바꿈 하나로** 이어 붙인다
 * (compose-foundation 1.12.1 의 `SelectionManager.getSelectedText` — 바이트코드로 확인했다). 그대로 두면 둘이 틀린다.
 *
 * - **4,096자에서 자른 긴 줄**(미니파이 JS)의 조각 사이에 없던 줄바꿈이 들어간다 — 붙여 넣은 코드가 바뀐다.
 * - **줄 끝 표시**(`↓`·`⏎`·`←`)가 글과 함께 복사된다 — 화면에만 있는 기호다.
 *
 * 그래서 행의 글에 표지(문자열 주석)를 달고([annotateRow]), 복사하는 순간 Compose 가 넘긴 글 대신 **조각 목록**
 * (`SelectionState.selectedTexts` — 조각마다 그 행의 주석이 살아 있다)으로 다시 잇는다([join]).
 *
 * ## 상한
 *
 * 클립보드는 바인더로 시스템에 건너간다. 한 번에 넘는 양이 1 MB 를 넘으면 `TransactionTooLargeException` 이 나고, Compose 의
 * 복사는 그것을 잡지 않아 **앱이 죽는다**(넣는 코루틴이 예외를 그대로 던진다). 글자는 UTF-16 으로 두 바이트씩이라
 * [MAX_CHARS] 에서 끊고, 넘으면 넣지 않고 말한다 — 잘라 넣으면 사용자는 앞부분만 붙여 넣고도 모른다.
 */
internal object RowCopy {

    /** 줄 끝 표시의 자리. 복사할 때 뺀다. */
    const val TAG_MARK = "iro.row.mark"

    /** 앞 행을 잇는 행(긴 줄의 둘째 조각부터). 복사할 때 앞 행에 줄을 바꾸지 않고 붙인다. */
    const val TAG_CONTINUES = "iro.row.continues"

    /** 한 번에 넣는 글자 수의 상한. 20만 자 = 40만 바이트 — 바인더 한도(1 MB)의 절반 밑이다. */
    const val MAX_CHARS = 200_000

    /** 복사의 결과. 화면이 알림으로 바꾼다. */
    sealed interface Outcome {
        data class Copied(val chars: Int) : Outcome
        data object TooLong : Outcome
        data object Failed : Outcome
    }

    /**
     * 고른 조각들을 하나의 글로 — 행 사이에는 줄바꿈 하나, **앞 행을 잇는 행 앞에는 아무것도**, 줄 끝 표시는 뺀다.
     *
     * 줄바꿈은 언제나 `\n` 이다 — 원문이 CRLF 여도 그렇다. 붙여 넣는 쪽(입력칸·메신저)이 줄 끝을 하나로 다루고, 화면이 보인
     * 것도 줄 하나다.
     *
     * @return [limit] 를 넘으면 null(넘는 순간 멈춘다 — 다 만들고 버리지 않는다).
     */
    fun join(pieces: List<AnnotatedString>, limit: Int = MAX_CHARS): String? {
        val out = StringBuilder()
        pieces.forEachIndexed { i, piece ->
            if (i > 0 && !continues(piece)) out.append('\n')
            appendWithoutMarks(out, piece)
            if (out.length > limit) return null
        }
        return out.toString()
    }

    /** [joinPrefix] 의 결과. [truncated] 면 [text] 는 앞부분이다. */
    class Joined(val text: String, val truncated: Boolean)

    /**
     * [join] 과 같게 잇되(줄 끝 표시 없이, 4,096자 경계에 줄바꿈 없이) **[limit] 자에서 자른다** — 다른 앱으로 보내는 글(번역 등 —
     * [RowSend])이 쓴다. 클립보드와 달리 잘라서라도 보낸다(크롬도 그렇게 한다). 서로게이트 쌍을 가르지 않는다.
     */
    fun joinPrefix(pieces: List<AnnotatedString>, limit: Int): Joined {
        val out = StringBuilder()
        pieces.forEachIndexed { i, piece ->
            if (i > 0 && !continues(piece)) out.append('\n')
            appendWithoutMarks(out, piece)
            if (out.length > limit) {
                var cut = limit
                if (cut > 0 && Character.isHighSurrogate(out[cut - 1])) cut--
                return Joined(out.substring(0, cut), truncated = true)
            }
        }
        return Joined(out.toString(), truncated = false)
    }

    /**
     * 이 조각이 앞 행을 잇는가. 빈 조각(선택이 행의 맨 앞에서 끝났다)에도 주석이 남는다 — `AnnotatedString.subSequence` 는
     * 빈 범위를 '그것을 품은 주석과 겹친다' 로 본다(`RowCopyTest` 가 박는다).
     */
    private fun continues(piece: AnnotatedString): Boolean =
        piece.hasStringAnnotations(TAG_CONTINUES, 0, piece.length)

    private fun appendWithoutMarks(out: StringBuilder, piece: AnnotatedString) {
        val marks = piece.getStringAnnotations(TAG_MARK, 0, piece.length)
        if (marks.isEmpty()) {
            out.append(piece.text)
            return
        }
        var at = 0
        for (m in marks.sortedBy { it.start }) {
            val start = m.start.coerceIn(at, piece.length)
            out.append(piece.text, at, start)
            at = maxOf(at, m.end.coerceAtMost(piece.length))
        }
        out.append(piece.text, at, piece.length)
    }
}

/**
 * `SelectionContainer` 가 복사할 때 부르는 클립보드를 가로챈다. 넘겨받은 글은 쓰지 않고 [selection] 의 조각으로
 * [RowCopy.join] 이 만든 글을 **평문으로** 넣는다(강조의 색까지 실어 보낼 까닭이 없다).
 *
 * 조각이 하나도 없으면(그럴 일은 없어야 한다) 받은 그대로 넘긴다 — 아무것도 넣지 않는 것보다 낫다.
 */
internal class RowClipboard(
    private val real: Clipboard,
    private val selection: SelectionState,
    private val onOutcome: (RowCopy.Outcome) -> Unit,
) : Clipboard {

    override suspend fun getClipEntry(): ClipEntry? = real.getClipEntry()

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        val pieces = selection.selectedTexts
        if (clipEntry == null || pieces.isEmpty()) {
            put(clipEntry, null)
            return
        }
        val text = RowCopy.join(pieces)
        if (text == null) {
            onOutcome(RowCopy.Outcome.TooLong)
            return
        }
        // 이름표는 일부러 밋밋하게 둔다 — 클립보드를 읽는 다른 앱에도 보이는 값이라 파일 이름을 싣지 않는다.
        put(ClipEntry(ClipData.newPlainText(CLIP_LABEL, text)), text.length)
    }

    private suspend fun put(entry: ClipEntry?, chars: Int?) {
        try {
            real.setClipEntry(entry)
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            // 상한 밑이어도 바인더가 거절할 수 있다(그 순간 다른 큰 전송이 버퍼를 쓰고 있다). 잡지 않으면 앱이 죽는다.
            onOutcome(RowCopy.Outcome.Failed)
            return
        }
        if (chars != null) onOutcome(RowCopy.Outcome.Copied(chars))
    }

    override val nativeClipboard: ClipboardManager get() = real.nativeClipboard

    private companion object {
        const val CLIP_LABEL = "text"
    }
}
