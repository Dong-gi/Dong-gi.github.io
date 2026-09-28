package io.github.donggi.iroiroviewer.model

/**
 * 경로에서 사람에게 보일 이름을 뗀다.
 *
 * 뷰어 넷(문서·텍스트·압축·만화)이 여는 중·실패 화면의 제목에 파일 이름을 적는다 — 무엇을 열다 실패했는지, '다른 앱으로
 * 열기' 가 무엇을 넘기는지 보이게 하려는 것이다. 처음에는 넷이 같은 한 줄을 모듈마다 한 벌씩 들었다(feature 끼리 참조할
 * 수 없어서다). 판단이 여럿이면 한쪽만 고쳐지는 날이 오므로 여기 하나로 둔다.
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
}
