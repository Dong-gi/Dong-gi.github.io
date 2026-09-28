package io.github.donggi.iroiroviewer.docview

/**
 * 화면이 고른 값을 **곧바로** 걸고, 저장소(`DataStore`)가 돌려주는 값은 **우리 쓰기가 다 끝난 뒤에만** 받는다.
 *
 * '보기' 판에서 '크게' 를 빠르게 두 번 누르면 쓰기 둘이 줄을 선다. 첫 쓰기가 끝나 저장소가 110% 를 돌려주는 순간 화면은
 * 이미 120% 인데, 그 값을 받으면 글자가 한 번 줄었다가 다시 커지고(여백·바탕이면 쪽을 **두 번** 다시 읽는다), 그 사이에
 * 누른 한 번은 110% 에서 셈해져 사라진다(검토가 잡았다). 쓰는 중에 돌아온 값은 **우리가 이미 건 값의 옛 판**이다.
 *
 * 쓰기가 없을 때 온 값(설정 화면이 바꿨다)은 그대로 받는다. **주 스레드에서만 부른다** — 받는 쪽(`DocViewModel`)의 모음과
 * 쓰기가 둘 다 `viewModelScope`(주 스레드)에서 돈다.
 */
internal class LocalFirst<T : Any> {

    private var pending = 0

    /** 쓰기를 시작한다. 화면에는 이미 그 값을 걸었다. */
    fun beginWrite() {
        pending++
    }

    /** 쓰기가 끝났다(성공이든 실패든). */
    fun endWrite() {
        if (pending > 0) pending--
    }

    /** 저장소가 값을 냈다. 화면에 걸 값이면 그것, 우리 쓰기가 아직 남았으면 null(옛 판이다). */
    fun stored(value: T): T? = if (pending == 0) value else null
}
