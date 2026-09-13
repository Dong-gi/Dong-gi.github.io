package io.github.donggi.iroiroviewer.format

import io.github.donggi.iroiroviewer.safety.ParseLimitExceededException
import java.io.EOFException
import java.io.IOException
import java.util.concurrent.CancellationException

/**
 * 포맷 하나를 여는 것. 모든 뷰어가 이 계약 뒤에 선다.
 *
 * `suspend` 인 것은 취소 때문이다. 파서는 협조적 취소만 지키면 된다 —
 * 루프 안에서 `currentCoroutineContext().ensureActive()` 를 부르는 것. 시간 상한은
 * 파서가 아니라 부르는 쪽이 `withTimeout` 으로 건다.
 */
interface DocumentOpener {

    /** 이 여는이가 다루는 포맷. [FormatId] 로 레지스트리가 고른다. */
    val handles: Set<FormatId>

    suspend fun open(source: DocumentSource, progress: ProgressSink = ProgressSink.NONE): OpenOutcome
}

/**
 * 여는 데 성공했는가. 예외가 아니라 값으로 돌려주는 것은 **부분 성공을 1급으로 다루기
 * 위해서**다. 표를 못 읽었다고 문서를 통째로 못 보여줄 이유는 없다.
 */
sealed interface OpenOutcome {
    data class Success(val document: OpenedDocument) : OpenOutcome
    data class Failed(val failure: OpenFailure) : OpenOutcome
}

/**
 * 열린 문서. 구체적인 내용(쪽, 시트, 흐름 텍스트)은 하위 타입이 정한다.
 *
 * [AutoCloseable] 인 것은 대부분의 구현이 파일 핸들이나 채널을 쥐고 있기 때문이다.
 */
interface OpenedDocument : AutoCloseable {
    val formatId: FormatId

    /** 읽었지만 온전하지 않은 것. 화면이 배너로 알린다. */
    val warnings: List<ParseWarning>

    /** 이 문서에서 버린 것의 종류별 개수. 배지를 탭하면 이 목록이 나온다. */
    val unsupported: UnsupportedFeatures
}

/**
 * 읽다가 만난 문제. 치명적이지 않아서 [OpenFailure] 가 되지 못한 것들이다.
 *
 * @param code 종류. 화면이 문구를 고르는 데 쓴다.
 * @param detail 사람이 읽을 설명. **파일 경로를 넣지 마라.**
 */
data class ParseWarning(val code: String, val detail: String)

/**
 * 구현하지 않아 버린 기능의 집계. 파서가 무언가를 건너뛸 때마다 여기에 센다.
 *
 * 이것이 있어야 "간이 렌더" 배지가 거짓말을 하지 않는다 — 사용자는 원문에 무엇이
 * 있었는지 모르므로, 빠진 것이 있다는 사실 자체를 앱이 말해 주어야 한다.
 */
class UnsupportedFeatures {
    private val counts = LinkedHashMap<String, Int>()

    fun record(kind: String, n: Int = 1) {
        if (n <= 0) return
        counts[kind] = (counts[kind] ?: 0) + n
    }

    val isEmpty: Boolean get() = counts.isEmpty()

    val total: Int get() = counts.values.sum()

    fun snapshot(): Map<String, Int> = LinkedHashMap(counts)

    companion object {
        // 종류 이름을 문자열로 흩뿌리지 않도록 여기에 모은다.
        const val EQUATION = "수식"
        const val CHART = "차트"
        const val SHAPE = "도형"
        const val EMBEDDED_OBJECT = "삽입 개체"
        const val FOOTNOTE = "각주"
        const val HEADER_FOOTER = "머리말·꼬리말"
        const val FORMULA_CACHED = "계산되지 않은 수식"
        const val UNKNOWN_ELEMENT = "알 수 없는 요소"
    }
}

/**
 * 진행률. 파서는 이것을 통해서만 바깥과 말한다.
 *
 * @param done 지금까지 처리한 양. 단위는 구현이 정한다(바이트든 쪽이든).
 * @param total 전체. 모르면 -1.
 */
fun interface ProgressSink {
    fun report(done: Long, total: Long)

    companion object {
        val NONE = ProgressSink { _, _ -> }
    }
}

/**
 * 파서 경계에서 예외를 [OpenFailure] 로 바꾼다.
 *
 * **취소는 실패가 아니다.** `CancellationException` 을 여기서 삼키면 코루틴 취소가
 * 먹히지 않아 사용자가 화면을 나가도 파서가 계속 돈다. 그래서 맨 먼저 다시 던진다.
 * (코틀린에서 `kotlin.coroutines.cancellation.CancellationException` 은
 * `java.util.concurrent.CancellationException` 의 typealias 이고, 코루틴의 것도 이것을
 * 상속하므로 아래 한 줄이 세 가지를 모두 잡는다.)
 *
 * **예외 메시지를 그대로 담지 않는다.** 메시지에는 절대경로와 공격자가 심은 문자열이
 * 들어 있고, [OpenFailure.detail] 은 화면에 그대로 나간다. 종류별로 우리가 쓴 문장만 쓴다.
 */
fun Throwable.toOpenFailure(): OpenFailure {
    if (this is CancellationException) throw this
    return when (this) {
        // 상한 예외의 메시지는 우리가 만든 것이라 경로가 들어 있지 않다.
        is ParseLimitExceededException -> OpenFailure.TooLarge(message ?: limitName)
        is SecurityException -> OpenFailure.NoPermission("읽을 권한이 없다")
        is EOFException -> OpenFailure.Corrupt("파일이 중간에서 끝났다")
        is java.io.InterruptedIOException -> OpenFailure.Timeout("읽는 중에 중단되었다")
        is IOException -> OpenFailure.Io("입출력이 실패했다")
        is StackOverflowError -> OpenFailure.TooLarge("구조가 너무 깊다")
        is OutOfMemoryError -> OpenFailure.TooLarge("메모리가 모자랐다")
        // 예외 이름은 남긴다 — 진단에 필요하고 그 자체로는 파일 내용을 담지 않는다.
        else -> OpenFailure.Corrupt(this::class.java.simpleName)
    }
}
