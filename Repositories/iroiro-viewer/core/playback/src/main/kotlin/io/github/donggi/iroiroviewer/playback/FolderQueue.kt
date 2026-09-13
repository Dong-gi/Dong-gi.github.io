package io.github.donggi.iroiroviewer.playback

import io.github.donggi.iroiroviewer.model.FileEntry
import io.github.donggi.iroiroviewer.model.FileKind
import io.github.donggi.iroiroviewer.safety.PlaybackLimits

/**
 * 지금 폴더를 **임시 재생 목록**으로 바꾼다.
 *
 * 확정 요구사항의 'VLC 차용' 이 이것이다 — 현재 폴더(하위 폴더 제외)에 소리를 가진
 * 미디어가 있으면 ▶ 를 띄우고, 누르면 그것들을 차례로 튼다.
 *
 * ## android.* 를 쓰지 않는다
 *
 * 목록을 고르는 일과 상한을 거는 일은 순수 계산이라 **JVM 시험으로 전부 박힌다.**
 * 플레이어를 만지는 일은 [PlaybackConnection] 이 한다. 9단계가 `ComicSource` 를 같은
 * 이유로 갈라 놓았고, 그 덕에 창 계산이 에뮬레이터 없이 시험됐다.
 *
 * ## '소리를 가진 미디어' 를 확장자로 센다
 *
 * 정확히 알려면 파일을 열어 트랙을 봐야 하는데, 그것은 **'목록을 그릴 때 파일을 열지
 * 않는다'**(`FileKind` 의 주석)는 더 단단한 규칙과 정면으로 부딪친다. 1만 개 폴더에서
 * ▶ 를 띄울지 정하려고 1만 번 여는 일은 할 수 없다.
 *
 * 그래서 [FileKind.AUDIO] 와 [FileKind.VIDEO] 를 전부 센다. **무음 영상이 섞인다** —
 * 그것이 이 근사가 틀리는 유일한 방향이고, 틀려도 재생이 안 될 뿐 파일은 다치지 않는다.
 */
object FolderQueue {

    /** 이 항목을 큐에 담을 수 있는가. 폴더·링크·못 들어가는 것은 뺀다. */
    fun isPlayable(e: FileEntry): Boolean =
        !e.isDirectory && !e.isLocked && !e.isSymlink &&
            (e.kind == FileKind.AUDIO || e.kind == FileKind.VIDEO)

    /** 이 폴더에 ▶ 를 띄울 것인가. 하나라도 있으면 띄운다. */
    fun hasPlayable(entries: List<FileEntry>): Boolean = entries.any { isPlayable(it) }

    /**
     * 큐로 쓸 항목과, 그 안에서 시작할 자리.
     *
     * @param startPath 사용자가 누른 파일. 없으면(`null`) 처음부터 튼다 — ▶ 를 누른 경우다.
     */
    data class Plan(val items: List<FileEntry>, val startIndex: Int) {
        val isEmpty: Boolean get() = items.isEmpty()
    }

    /**
     * 목록에서 큐를 만든다. **보이는 순서 그대로**다 — 사용자가 정렬을 골라 두었으면
     * 그 순서로 듣는 것이 기대다. 섞기는 재생 쪽에서 따로 켠다.
     *
     * ## 상한을 넘으면 어디를 자르는가
     *
     * [PlaybackLimits.MAX_QUEUE_ITEMS] 를 넘으면 **누른 항목이 가운데쯤 오도록** 창을
     * 잡는다. 앞에서부터 자르면 폴더 뒤쪽 파일을 눌렀을 때 그것이 큐에서 빠지고 **다른
     * 파일이 재생된다** — 8단계가 '고른 것과 다른 것이 열린다' 로 치명 판정을 내린 형태다.
     *
     * 창의 시작을 구하는 식에 주의한다. `count - MAX` 가 음수일 수 있으므로
     * `coerceIn(0, count - MAX)` 를 그대로 쓰면 **하한이 상한보다 커져 예외로 죽는다**
     * (항목이 상한보다 적은 보통 폴더가 전부 그렇다). `coerceAtMost` 를 먼저 걸어야 한다.
     */
    fun plan(entries: List<FileEntry>, startPath: String?): Plan {
        val playable = entries.filter(isPlayableRef)
        if (playable.isEmpty()) return Plan(emptyList(), 0)

        val tapped = startPath?.let { p -> playable.indexOfFirst { it.path == p } } ?: 0
        // 누른 것이 목록에 없으면(사라졌거나 재생할 수 없는 종류다) 처음부터 튼다.
        val at = if (tapped < 0) 0 else tapped

        val max = PlaybackLimits.MAX_QUEUE_ITEMS
        if (playable.size <= max) return Plan(playable, at)

        val lastStart = playable.size - max
        val from = (at - max / 4).coerceAtLeast(0).coerceAtMost(lastStart)
        // **`subList` 를 그대로 내보내지 않는다.** 원본을 들여다보는 뷰라 수명이 얽히고,
        // 이 저장소는 같은 종류로 한 번 죽은 적이 있다(함정 표의 `SavedStateHandle`).
        return Plan(ArrayList(playable.subList(from, from + max)), at - from)
    }

    private val isPlayableRef: (FileEntry) -> Boolean = ::isPlayable
}
