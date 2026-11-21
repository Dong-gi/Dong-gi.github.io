package io.github.donggi.iroiroviewer.io

/**
 * 줄 서 있거나 도는 요청이 **어느 파일**에 걸려 있는가 — 요청의 신원(id)마다 경로 하나. 같은 파일에 요청이 둘이면 둘 다 끝나야 빠진다.
 *
 * 쓰임은 회전·뒤집기 저장이다([FileOpManager.reorienting]). 화면의 미저장 몫은 저장 **하나**가 끝날 때마다 통째로 비고, 좌우 두 번처럼
 * 합성이 정방향이 되는 누름이면 처음부터 정방향이라 '아직 저장 중인가' 를 화면이 알 길이 없다 — 그 사이 연 잘라 저장·그리기는
 * 다음 저장이 바꿀 파일을 보고 고른다. 그래서 큐가 신원으로 든다.
 *
 * 스레드에 안전하지 않다 — 부르는 쪽이 잠근다. 지우기는 몇 번 불러도 된다(큐는 끝난 요청을 여러 길에서 정리한다).
 */
class PathsInFlight {

    private val byId = HashMap<String, String>()

    /** 넣는다. 지금의 경로 집합을 돌려준다. */
    fun add(id: String, path: String): Set<String> {
        byId[id] = path
        return paths()
    }

    /** 뺀다. 없던 신원이면 null(바뀐 것이 없다), 아니면 지금의 경로 집합. */
    fun remove(id: String): Set<String>? {
        byId.remove(id) ?: return null
        return paths()
    }

    fun paths(): Set<String> = byId.values.toSet()
}
