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

    /**
     * @param onOpen **[OpenedDocument] 를 만든 그 자리에서** 부른다. 돌려주기 전에 부르는
     *   것이 요점이다 — 아래 '값을 돌려주는 것과 소유권을 넘기는 것' 참고. 구현은
     *   문서를 만들자마자 반드시 한 번 부른다.
     */
    suspend fun open(
        source: DocumentSource,
        progress: ProgressSink = ProgressSink.NONE,
        onOpen: (OpenedDocument) -> Unit = {},
    ): OpenOutcome
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
 *
 * ## 값을 돌려주는 것과 소유권을 넘기는 것을 갈라라
 *
 * 이 타입은 **코루틴 경계를 건너 돌아온다** — [Documents.open] 이 `withTimeout` 안에서
 * 만들어 밖으로 준다. 그 사이에 취소되면 코루틴은 **값을 버리고 예외를 던지므로**,
 * 호출자는 받은 적이 없어 닫을 수 없고 자원은 GC 를 기다린다. PDF 에서는 그것이 네이티브
 * pdfium 문서와 사용자 파일을 붙든 `ParcelFileDescriptor` 이고, EPUB 에서는 `ZipFile` 이다.
 *
 * **시험으로 잡히지 않는다**(창이 좁아 재현이 안 된다). 9단계가 만화에서 같은 결함을
 * 겪고 `ComicOpen.open(onOpen = …)` 으로 고쳤다 — 여기도 같은 장치를 쓴다:
 * 만든 자리에서 [DocumentOpener.open] 의 `onOpen` 으로 건네고, 호출자는 `finally` 에서
 * 닫되 성공 경로에서 주인을 바꾼다.
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
 *
 * **스레드 안전하다.** 흐름 문서·EPUB 은 WebView 의 스레드에서 부분을 그리며 세고, 화면은 주 스레드에서
 * [snapshot] 을 읽는다. 맵을 잠금 없이 두면 읽는 도중의 쓰기가 `ConcurrentModificationException` 이나
 * 틀린 수를 낸다(13단계 검토가 잡았다 — 11단계의 EPUB 부터 그랬다).
 */
class UnsupportedFeatures {
    private val counts = LinkedHashMap<String, Int>()

    @Synchronized
    fun record(kind: String, n: Int = 1) {
        if (n <= 0) return
        counts[kind] = (counts[kind] ?: 0) + n
    }

    val isEmpty: Boolean @Synchronized get() = counts.isEmpty()

    val total: Int @Synchronized get() = counts.values.sum()

    @Synchronized
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

        // 12단계(OOXML)가 더한 것.
        const val COMMENT = "메모"
        const val MACRO = "매크로"
        /** 그림인데 화면이 그릴 수 없는 형식(EMF·WMF·TIFF 등). */
        const val UNSUPPORTED_IMAGE = "그릴 수 없는 그림"
        /** 바깥 파일을 가리키는 그림·연결(링크된 그림, 다른 통합 문서). */
        const val LINKED_FILE = "연결된 바깥 파일"
        const val SMART_ART = "SmartArt"
        const val TEXT_EFFECT = "글자 효과"
        const val ANIMATION = "애니메이션"

        // 11단계의 HTML 위생이 쓰는 것. **여기 모아 두는 이유는 2단계와 같다** —
        // 종류 이름을 문자열로 흩뿌리면 같은 것이 두 이름으로 세어지고, 배지의 숫자가
        // 무엇의 개수인지 아무도 모르게 된다.
        const val SCRIPT = "스크립트"
        const val FRAME = "프레임"
        const val REMOTE_REFERENCE = "바깥을 가리키는 참조"
        const val EVENT_HANDLER = "이벤트 처리기"
        const val FORM = "입력 양식"
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
 * **파일의 형식이 깨졌다**는 뜻의 예외. 스트림을 읽다가도 나므로 [IOException] 을 잇지만,
 * [toOpenFailure] 는 이것을 `Io`('입출력이 실패했다')가 아니라 [OpenFailure.Corrupt] 로 옮긴다 —
 * 사용자가 디스크 고장을 의심하게 만들지 않으려는 것이다(PDF 의 실패 매핑이 같은 이유로 따로 있다).
 *
 * 포맷의 형식 오류(CFB·HWP)는 이것을 잇는다. **메시지에는 우리가 쓴 고정 문장만** 넣는다 —
 * 그대로 [OpenFailure.detail] 이 된다.
 */
open class CorruptFormatException(message: String) : IOException(message)

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
        is CorruptFormatException -> OpenFailure.Corrupt(message ?: "형식이 깨졌다")
        is IOException -> OpenFailure.Io("입출력이 실패했다")
        is StackOverflowError -> OpenFailure.TooLarge("구조가 너무 깊다")
        is OutOfMemoryError -> OpenFailure.TooLarge("메모리가 모자랐다")
        // 예외 이름은 남긴다 — 진단에 필요하고 그 자체로는 파일 내용을 담지 않는다.
        else -> OpenFailure.Corrupt(this::class.java.simpleName)
    }
}
