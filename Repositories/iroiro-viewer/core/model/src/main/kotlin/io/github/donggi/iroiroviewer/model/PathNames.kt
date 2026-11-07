package io.github.donggi.iroiroviewer.model

/**
 * 경로에서 사람에게 보일 이름을 뗀다.
 *
 * 뷰어 넷(문서·텍스트·압축·만화)이 여는 중·실패 화면의 제목에 파일 이름을 적는다 — 무엇을 열다 실패했는지, '다른 앱으로
 * 열기' 가 무엇을 넘기는지 보이게 하려는 것이다. feature 끼리 참조할 수 없어 모듈마다
 * 한 벌씩 들면 한쪽만 고쳐지는 날이 오므로 여기 하나로 둔다.
 */
object PathNames {

    /**
     * 경로의 마지막 조각. **전체 경로를 화면에 적지 않는다** — 막대 한 줄에 들지 않고, 캡처·화면 공유로 폴더 구조가 새어 나간다.
     *
     * 안드로이드의 구분자는 언제나 `/` 라 글자로 자른다 — `File.name` 은 윈도에서 도는 JVM 시험에서 역슬래시까지 구분자로
     * 읽어, 기기와 다른 답을 시험하게 된다(`ShareHelper.isUnderRoots` 와 같은 까닭). 끝의 빗금은 떼고 자른다(`File` 이 그렇게
     * 정규화한다).
     */
    fun lastSegment(path: String): String = path.trimEnd('/').substringAfterLast('/')

    /**
     * 글의 방향을 바꾸는 **보이지 않는 글자**인가 — 파일 이름에 넣어 확장자를 위장할 수 있다.
     * U+061C(아랍 글자 표시)·U+200E·U+200F(왼쪽에서 오른쪽·오른쪽에서 왼쪽 표시)·U+202A~202E(끼워 넣기·덮어쓰기 — U+202E 가
     * '오른쪽에서 왼쪽으로 덮어쓰기')·U+2066~2069(격리). 이름에 끼면 `invoice` + U+202E + `fdp.apk` 가 화면에서 'invoicekpa.pdf' 로
     * 보인다 — 확장자를 속인다. 이 앱이 짓거나 받는 이름에서는 `_` 로 바꾸고(`PathRules.sanitize`), 이미 있는 이름은 화면에서만
     * [forDisplay] 로 드러낸다. (소스에 그 글자를 날것으로 적지 않는다 — 언제나 이스케이프로.)
     */
    fun isBidiControl(c: Char): Boolean =
        c == '\u061C' || c == '\u200E' || c == '\u200F' ||
            c in '\u202A'..'\u202E' || c in '\u2066'..'\u2069'

    /** 방향 제어 글자를 화면에서 대신하는 표시 — U+FFFD(대체 문자). 무언가 숨어 있었다는 것이 보인다. */
    const val BIDI_MARK = '\uFFFD'

    /**
     * **화면에 보일** 이름 — 방향 제어 글자([isBidiControl])를 눈에 보이는 표시([BIDI_MARK])로 바꾼다. 그 글자가 없으면 같은 문자열을
     * 그대로 돌려준다. **실제 이름은 바꾸지 않는다** — 파일을 찾고 열고 넘기는 데는 언제나 원래 이름을 쓴다. 목록·격자·정보 시트·받은
     * 화면·뷰어의 제목·압축 안의 항목·재생 화면이 이것을 지난다.
     */
    fun forDisplay(name: String): String {
        if (name.none(::isBidiControl)) return name
        return buildString(name.length) { for (c in name) append(if (isBidiControl(c)) BIDI_MARK else c) }
    }
}
