package io.github.donggi.iroiroviewer.comic

/**
 * 책을 열 때 **어느 방향·어느 쪽에서** 시작하는가. **순수 함수다.**
 *
 * `ComicViewModel` 은 Room 과 DataStore 를 직접 불러 JVM 시험이 닿지 못한다. 판단만 여기로 떼어 두어, 9단계가
 * 기기에서 잡은 '앞 책의 방향이 새 책에 옮겨 간다' 와 14단계의 '새 책은 설정의 기본 방향' 이 JVM 시험으로 선다.
 *
 * ## 차례 — 저장된 값이 이긴다
 *
 * 1. 이 책의 기록(`comic_progress`)이 있으면 **그 방향**이다. 사용자가 이 책에서 고른 것이고, 설정의 기본값은
 *    '아직 고른 적 없는 책' 을 위한 값이다. 기본값을 바꿨다고 이미 읽던 책이 뒤집히면 안 된다.
 * 2. 없으면 **설정의 기본 방향**(`AppPreferences.comicDefaultDirection`). 9단계까지는 언제나 왼쪽에서 오른쪽이었다.
 * 3. 쪽은 기록이 있고, 쪽 수가 같고, 엔트리를 지정해 들어오지 않았을 때만 되살린다(9단계 그대로).
 *
 * **앞 책의 값은 입력에 없다** — 그것이 이 함수의 요점이다. VM 이 여는 첫머리에서 상태를 되돌리고 이 결과만 얹는다.
 */
internal object ReadingStart {

    /** 이 책의 기록 가운데 판단에 쓰는 것. */
    data class Saved(val page: Int, val pageCount: Int, val direction: Int)

    data class Start(
        val direction: ComicViewModel.Direction,
        /** 시작 쪽(0부터). */
        val page: Int,
        /** 이어보기로 되살렸는가 — 화면이 '처음부터' 를 권한다. */
        val resumed: Boolean,
    )

    /**
     * @param resumePage 읽던 쪽까지 되살릴 것인가. 압축 목록에서 그림 항목을 탭해 들어온 길은 쪽을 이미 지정했으므로
     *   거짓이다 — 그래도 **방향은** 되살린다(9단계 뒤 감사가 고친 결함).
     * @param defaultDirection 설정의 기본 방향 코드. 범위 밖이면 왼쪽에서 오른쪽.
     */
    fun decide(saved: Saved?, pageCount: Int, resumePage: Boolean, defaultDirection: Int): Start {
        if (saved == null) {
            return Start(ComicViewModel.Direction.of(defaultDirection), page = 0, resumed = false)
        }
        val direction = ComicViewModel.Direction.of(saved.direction)
        val resume = resumePage && saved.pageCount == pageCount && saved.page in 1 until pageCount
        return Start(direction, page = if (resume) saved.page else 0, resumed = resume)
    }
}
