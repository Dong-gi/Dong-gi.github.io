package io.github.donggi.iroiroviewer.format

import java.io.InputStream

/**
 * **흐름으로 그리는 문서** — docx·xlsx·pptx(12단계), HWPX(13단계).
 *
 * ## 왜 계약이 `format:api` 에 있는가
 *
 * 문서 화면(`feature:docview`)은 포맷 모듈을 **하나**만 볼 수 있다(의존 표 — 그 하나가 이미
 * `format:epub` 이다). 12·13단계가 여는 포맷은 다섯이 는다. 화면이 그 다섯을 직접 보게 하면
 * 규칙이 무너지므로, **화면이 아는 것은 이 계약 하나**로 두고 포맷 모듈이 이것을 구현한다.
 * 무엇을 무엇으로 여는지는 `app/FormatRegistry` 가 안다(`DocumentSupport` 의 이음매).
 *
 * ## 쪽이 아니라 부분(part)
 *
 * 쪽 재현을 포기하고 흐름으로 그리기로 했다(CLAUDE.md '지원하지 않는 것'). 그래서 세는 단위는
 * 쪽이 아니라 **부분**이다 — docx 는 본문(길면 몇 조각), xlsx 는 시트, pptx 는 슬라이드.
 * 이어보기가 저장하는 것도 이 번호다(`doc_progress.page`).
 *
 * ## 본문은 위생기를 지나서만 나간다
 *
 * [partHtml] 이 돌려주는 것은 **`HtmlSanitizer` 를 지난** 문서 하나다. 구현은 자기가 만든
 * HTML 이라도 위생기에 한 번 더 통과시킨다 — 변환기의 이스케이프 실수 하나가 스크립트나
 * 바깥 참조를 여는 길이 되지 않게 하는, 겹쳐 둔 방어다.
 *
 * ## 스레드
 *
 * [partHtml]·[openResource] 는 **WebView 의 다른 스레드**에서 불린다. 구현이 스스로 동기화한다.
 * 닫힌 뒤에 불리면 null 을 준다(예외를 던지지 않는다 — 화면이 사라지는 중이다).
 */
interface FlowDocument : OpenedDocument {

    /** 화면 맨 위의 제목. 문서가 말해 주지 않으면 빈 문자열 — 화면이 파일 이름으로 채운다. */
    val title: String

    /** 무엇을 세는가. 화면이 '시트'·'슬라이드' 같은 말을 고른다. */
    val kind: FlowKind

    /** 부분들. **적어도 하나다** — 없으면 여는이가 실패로 끝낸다. */
    val parts: List<FlowPart>

    /** 목차. 제목(docx)·시트 이름·슬라이드 제목. 없으면 비어 있다. */
    val outline: List<FlowOutline>

    /** 부분 하나를 **위생을 거친 HTML 문서**로. 범위 밖이거나 닫혔으면 null. */
    fun partHtml(index: Int): String?

    /**
     * 부분이 가리키는 자원(그림) 하나. **패키지 안의 이름**(`word/media/image1.png`)으로 찾는다.
     * 없거나 내줄 수 없는 것(바깥 참조·그릴 수 없는 형식)이면 null. 부르는 쪽이 닫는다.
     */
    fun openResource(path: String): InputStream?

    /** 자원의 MIME(`image/png`). 모르면 null — 화면이 확장자로 짐작한다. */
    fun mediaTypeOf(path: String): String?

    /** 이 경로가 부분 하나를 가리키면 그 번호, 아니면 -1. 화면이 링크를 가로챌 때 쓴다. */
    fun partIndexOf(path: String): Int = parts.indexOfFirst { it.path == path }
}

/** 부분이 무엇인가. */
enum class FlowKind {
    /** 글(docx·HWPX). 부분은 대개 하나이고, 길면 조각으로 나뉜다. */
    DOCUMENT,

    /** 시트(xlsx). 부분 하나가 시트 하나다. */
    SHEETS,

    /** 슬라이드(pptx). 부분 하나가 슬라이드 하나다. */
    SLIDES,
}

/**
 * 부분 하나.
 *
 * @param path 화면이 이 부분을 부를 **가상 경로**. 패키지 안의 이름과 겹치지 않게 구현이 정한다
 *   (관례: `~part-0.html`). **폴더를 넣지 않는다** — 본문 안의 상대 주소(`word/media/…`)가
 *   패키지 뿌리를 기준으로 풀려야 한다.
 * @param label 사람에게 보일 이름. 시트 이름, '슬라이드 3', 비어 있으면 화면이 번호로 채운다.
 */
data class FlowPart(val path: String, val label: String)

/**
 * 목차 한 줄.
 *
 * @param depth 들여쓰기 깊이(0 부터). EPUB 목차와 같이 **평평한 목록에 깊이만 준다**.
 * @param partIndex 어느 부분인가.
 * @param anchor 그 부분 안의 자리(`id`). null 이면 부분의 처음이다.
 */
data class FlowOutline(val title: String, val depth: Int, val partIndex: Int, val anchor: String?)

/**
 * 흐름 문서의 경고 코드. **문장은 화면이 만든다**(순수 JVM 모듈은 화면 문구를 만들지 않는다).
 * 코드를 여기 두는 것은 화면이 포맷 모듈을 보지 않고도 문장을 고를 수 있게 하려는 것이다.
 */
object FlowWarnings {
    /** 너무 길어 뒷부분을 보여 주지 않는다(행·셀·글자 상한). [ParseWarning.detail] 은 부분 이름. */
    const val TRUNCATED = "flow.truncated"

    /** 부분 하나를 읽지 못했다(깨진 시트·슬라이드). 나머지는 보인다. detail 은 부분 이름. */
    const val PART_FAILED = "flow.part_failed"

    /** 매크로가 들어 있다. **돌리지 않는다** — 읽기 전용 뷰어다. */
    const val MACROS = "flow.macros"

    /** 같은 이름(대소문자 무관)의 부분이 둘 이상이다. 먼저 나온 것을 쓴다. detail 은 부분 이름. */
    const val DUPLICATE_PART = "flow.duplicate_part"

    /** 관계 파일 하나를 읽지 못했다. 그 관계가 가리키는 그림·링크가 빠질 수 있다. detail 은 파일 이름. */
    const val BROKEN_RELATIONSHIPS = "flow.broken_rels"

    /**
     * 본문이 아닌 **보조 부분**(스타일·번호 매기기·공유 문자열)을 읽지 못했다. 본문은 보이지만 서식·
     * 번호·글자가 빠졌을 수 있다. detail 은 그 부분의 이름. 조용히 기본값으로 그리면 사용자는 문서가
     * 원래 그렇게 생긴 줄 안다.
     */
    const val AUX_FAILED = "flow.aux_failed"

    /** 패키지에 콘텐츠 형식표(`[Content_Types].xml`)가 없어 이름으로 짐작해 열었다. detail 은 비어 있다. */
    const val NO_CONTENT_TYPES = "flow.no_content_types"

    /**
     * 부분 하나의 경고에서 `detail` 이 **이름이 아니라 번호**임을 알리는 앞머리(뒤에 1부터 센 번호).
     *
     * 이름 없는 부분(제목 없는 슬라이드·긴 글의 조각)은 번호로 알리는데, 숫자만 보고 번호로 읽으면
     * '2024' 라는 시트가 '2024번째 부분' 이 된다(12단계 검토가 잡았다). U+0001 은 XML 1.0 의 글자에
     * 들어올 수 없으므로 **어떤 문서의 이름도 이것으로 시작할 수 없다.**
     */
    const val PART_NUMBER_PREFIX = "\u0001"
}
