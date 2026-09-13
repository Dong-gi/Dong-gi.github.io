package io.github.donggi.iroiroviewer.format.text

/**
 * 문법 강조의 최소 어휘.
 *
 * ## 왜 이 일곱 가지뿐인가
 *
 * 편집기는 종류를 수십 개로 나누지만(변수·함수·타입·매크로…), 그러려면 **파싱**을 해야
 * 한다. 우리는 읽기 전용 뷰어라 훑어보기(lexing)만 한다. 훑어보기로 틀림없이 가를 수
 * 있는 것이 이 일곱이고, 그 이상은 틀린 색으로 칠하는 일이 늘어난다.
 *
 * [PLAIN] 은 **조각으로 나오지 않는다.** 기본색으로 그리면 되는 자리라 표시할 것이 없다.
 */
enum class TokenKind { PLAIN, KEYWORD, LITERAL, STRING, NUMBER, COMMENT, TAG, ATTRIBUTE }

/** [start, end) 구간을 [kind] 로 칠한다. 행 안의 좌표다. */
data class Span(val start: Int, val end: Int, val kind: TokenKind)

/**
 * 한 행을 훑어 색칠할 조각을 낸다.
 *
 * ## 상태를 정수 하나로 넘기는 이유
 *
 * 여러 줄 주석과 여러 줄 문자열은 **행을 넘어 이어진다.** 그래서 한 행만 보고는 색을
 * 정할 수 없고, 앞 행이 끝났을 때의 상태가 필요하다. 그 상태를 정수 하나로 좁혀 두면
 * 행마다 들고 다니는 비용이 4바이트고, [RowIndex] 의 앵커에 함께 적어 둘 수도 있다.
 *
 * ## [multiline] 이 가르는 것
 *
 * 상태가 행을 넘지 않는 언어(YAML·INI·셸)는 **아무 행에서나 곧바로** 칠할 수 있다.
 * 파일이 1 GB 라도 상관없다. 반대로 C 계열은 파일 앞에서부터 훑어야 이 행이 주석
 * 안인지 알 수 있어서, 큰 파일에서는 강조를 끄거나 앵커마다 상태를 미리 적어야 한다
 * ([TextHighlight] 가 그 일을 한다).
 */
interface RowHighlighter {
    /** 사람이 읽는 이름. 화면이 '코틀린' 처럼 보여 준다. */
    val label: String

    /** 상태가 행을 넘어 이어지는가. 거짓이면 앵커마다 상태를 적을 필요가 없다. */
    val multiline: Boolean

    /**
     * [row] 를 훑어 [out] 에 조각을 담고 **행이 끝났을 때의 상태**를 돌려준다.
     *
     * 조각은 시작 위치 오름차순이고 서로 겹치지 않는다.
     */
    fun highlight(row: String, startState: Int, out: MutableList<Span>): Int

    /**
     * 진짜 줄바꿈을 만났을 때 상태를 씻는다.
     *
     * **긴 줄을 자른 자리와 진짜 줄바꿈은 다르다.** 한 줄짜리 문자열(`"..."`)은 줄을
     * 넘지 못하지만, 4,096자에서 잘린 자리는 줄이 끝난 것이 아니라 같은 줄의 이어짐이다.
     * 자른 자리에서 상태를 씻으면 미니파이 JS 의 긴 문자열이 중간부터 색을 잃는다.
     */
    fun afterNewline(state: Int): Int = state

    companion object {
        /** 아무것도 이어지지 않은 상태. 파일의 첫 행은 언제나 이것이다. */
        const val START = 0
    }
}

/** 아무것도 칠하지 않는다. `.txt`·`.log` 와 우리가 모르는 확장자가 쓴다. */
object PlainHighlighter : RowHighlighter {
    override val label = "일반 텍스트"
    override val multiline = false
    override fun highlight(row: String, startState: Int, out: MutableList<Span>): Int = 0
}
