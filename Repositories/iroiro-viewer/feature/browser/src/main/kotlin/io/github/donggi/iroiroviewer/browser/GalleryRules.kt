package io.github.donggi.iroiroviewer.browser

import io.github.donggi.iroiroviewer.model.ExifPrivacy
import io.github.donggi.iroiroviewer.model.FileEntry

/**
 * 갤러리의 판단 — 뒤로 가기의 차례, 고른 것 다듬기, 고른 것으로 무엇을 할 수 있는가. 화면은 이것을 부르기만 한다(`GalleryRulesTest`).
 */
internal object GalleryRules {

    /** 갤러리에서 뒤로 가기 한 번이 하는 일. */
    enum class Back { CLEAR_SELECTION, CLOSE_ALBUM, CLOSE_GALLERY }

    /**
     * 차례는 **고르기 풀기 → 앨범 닫기 → 갤러리 닫기**. 폴더 목록과 같은 관행이다(고르는 중이면 먼저 고르기를 푼다). 앨범 안에서 고르던
     * 것을 한 번에 앨범까지 닫으면 무엇을 골랐는지 보지 못한 채 고르기가 사라진다.
     */
    fun back(selecting: Boolean, inAlbum: Boolean): Back = when {
        selecting -> Back.CLEAR_SELECTION
        inAlbum -> Back.CLOSE_ALBUM
        else -> Back.CLOSE_GALLERY
    }

    /**
     * 고른 것 가운데 **지금 갤러리에 있는 것만**. 다시 훑은 뒤 사라진 것(지웠다·옮겼다·다른 앱이 지웠다)이 고르기에 남으면 '3개 선택' 이
     * 거짓말을 하고, 누른 일이 없는 파일을 겨눈다. 바뀐 것이 없으면 같은 집합을 그대로 돌려준다(상태를 다시 내지 않게).
     */
    fun prune(selected: Set<String>, items: List<FileEntry>): Set<String> {
        if (selected.isEmpty()) return selected
        val here = items.mapTo(HashSet(items.size * 2)) { it.path }
        return if (here.containsAll(selected)) selected else selected.filterTo(LinkedHashSet()) { it in here }
    }

    /**
     * 고른 것을 **격자의 차례로**(새것부터) — 공유·묶기·옮기기가 이 차례로 싣는다. 누른 차례로 두면 받는 쪽(메신저)의 차례가 사용자가 본
     * 차례와 달라진다. 목록에 없는 것은 뺀다. 항목째 돌려준다 — 썸네일 열쇠를 파일을 다시 보지 않고(크기·시각으로) 셈한다.
     */
    fun ordered(selected: Set<String>, items: List<FileEntry>): List<FileEntry> =
        if (selected.isEmpty()) emptyList() else items.filter { it.path in selected }

    /**
     * 공유 창에 곧바로 싣는 수의 상한. 넘으면 싣지 않고 'ZIP으로 묶어 보내기' 를 권한다 — 수천 개의 주소를 실은 인텐트는 바인더 한 번(1 MB)을 넘어
     * 띄우지 못하거나 받는 앱이 감당하지 못한다(메신저의 첨부 상한은 그보다 훨씬 작다). 위치 지우고 공유도 같다(사본마다 주소 하나).
     */
    const val SHARE_LIMIT = 500

    /** [count] 개를 곧바로 공유할 수 있는가. */
    fun canShareDirectly(count: Int): Boolean = count in 1..SHARE_LIMIT

    /**
     * 고른 것으로 할 수 있는 위치 지우기 — 이름만 본다(엔진이 내용으로 한 번 더 본다).
     *
     * @param scrubCopies '위치 지우고 공유' 가 사본을 만들 수 있는 수(JPEG·PNG·WebP).
     * @param scrubInPlace '원본에서 위치 지우기' 가 고칠 수 있는 수(JPEG·WebP — 회전 저장과 같은 표).
     */
    data class Plan(val total: Int, val scrubCopies: Int, val scrubInPlace: Int) {
        /** 메뉴를 켤 것인가 — 하나라도 할 수 있어야 켠다(눌러 봤자 '없습니다' 로 끝나는 메뉴를 내밀지 않는다). */
        val canShareClean: Boolean get() = scrubCopies > 0
        val canStripInPlace: Boolean get() = scrubInPlace > 0
    }

    fun plan(paths: Collection<String>): Plan {
        var copies = 0
        var inPlace = 0
        for (p in paths) {
            val name = p.substringAfterLast('/')
            if (ExifPrivacy.canScrubCopy(name)) copies++
            if (ExifPrivacy.canScrubInPlace(name)) inPlace++
        }
        return Plan(paths.size, copies, inPlace)
    }
}
