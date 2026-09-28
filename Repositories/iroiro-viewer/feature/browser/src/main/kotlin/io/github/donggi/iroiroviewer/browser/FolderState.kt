package io.github.donggi.iroiroviewer.browser

import io.github.donggi.iroiroviewer.io.DirectoryLister

/**
 * 폴더 목록의 상태를 정하는 순수 규칙. **화면도 ViewModel 도 아니라 여기 두는 것**은 JVM 시험이 초 단위로
 * 지키게 하려는 것이다 — 목록이 한 번 비었다 차는 것, 고른 것이 사라지는 것은 화면에서 눈으로만 보이고 오류가 없다.
 */

/**
 * 나열 하나에 붙인 꼬리표.
 *
 * [tick] 은 다시 읽기(`refresh`)를 셀 때마다 오르는 번호다. **이것이 없으면 '다시 읽기가 끝났다' 를 알 수 없다** —
 * 같은 폴더를 다시 읽어 같은 목록이 나오면 `StateFlow` 가 같은 값으로 보고 합쳐 버려(함정 표의 conflation),
 * 당겨서 새로고침의 표시가 영영 꺼지지 않는다.
 */
internal data class TaggedListing(
    val path: String?,
    val tick: Int,
    val listing: DirectoryLister.Listing,
)

/**
 * 화면에 보일 나열.
 *
 * @param refreshing 옛 목록을 보이며 같은 폴더를 다시 읽는 중인가.
 * @param completedTick 끝난(목록이 나왔거나 실패한) 마지막 나열의 꼬리표. 당겨서 새로고침은 자기가 올린
 *   번호 이상이 여기 올 때까지 표시를 켜 둔다. 아직 끝난 것이 없으면 -1.
 */
internal data class HeldListing(
    val path: String?,
    val shown: DirectoryLister.Listing,
    val refreshing: Boolean,
    val completedTick: Int,
)

internal object ListingHold {

    val INITIAL = HeldListing(
        path = null,
        shown = DirectoryLister.Listing.Scanning(0),
        refreshing = false,
        completedTick = -1,
    )

    /**
     * 다음 상태.
     *
     * **같은 폴더를 다시 읽는 동안에는 옛 목록을 그대로 보인다.** `DirectoryLister.list` 는 언제나 `Scanning(0)`
     * 으로 시작하므로 그것을 그대로 그리면 파일 하나를 지울 때마다, 당겨서 새로고침할 때마다, 다른 앱이 파일을
     * 하나 넣을 때마다 목록이 '0개 읽는 중' 으로 바뀌었다가 돌아온다 — 스크롤 자리도 그때 잃는다. 갤러리가 같은
     * 판단을 했다(`BrowserViewModel.lastGalleryReady`). 실패 화면도 같은 까닭으로 붙들어 둔다.
     *
     * **다른 폴더로 옮겼으면 붙들지 않는다.** 앞 폴더의 목록이 새 폴더의 이름 아래 보이면 그 위에서 누른 것이
     * 엉뚱한 파일을 연다.
     */
    fun next(prev: HeldListing, incoming: TaggedListing): HeldListing {
        val samePath = prev.path == incoming.path
        return when (incoming.listing) {
            is DirectoryLister.Listing.Scanning ->
                if (samePath && prev.shown !is DirectoryLister.Listing.Scanning) {
                    prev.copy(refreshing = true)
                } else {
                    HeldListing(
                        path = incoming.path,
                        shown = incoming.listing,
                        refreshing = false,
                        completedTick = if (samePath) prev.completedTick else -1,
                    )
                }
            is DirectoryLister.Listing.Ready, is DirectoryLister.Listing.Failed ->
                HeldListing(
                    path = incoming.path,
                    shown = incoming.listing,
                    refreshing = false,
                    completedTick = incoming.tick,
                )
        }
    }
}

internal object SelectionRules {

    /**
     * 새 목록이 나왔을 때 고른 것을 **지금 있는 것**으로 줄인다. 바뀌지 않았으면 null.
     *
     * 목록이 저절로 다시 읽히게 되면서(폴더 감시·당겨서 새로고침) 생긴 규칙이다. 다른 앱이 지운 파일이 고른 채로
     * 남으면 '삭제' 가 없는 파일을 겨누고, 개수 표시(`N개 선택`)도 거짓이 된다. 폴더를 옮길 때 선택을 지우는
     * 것(`BrowserViewModel.open`)과 같은 까닭이다 — 선택은 '지금 보고 있는 것' 에 대한 상태다.
     *
     * 이름 필터로 가려진 것은 목록([DirectoryLister.Listing.Ready.entries])에 그대로 있으므로 남는다.
     * 숨김 파일을 끄면 숨김 파일은 빠진다 — 보이지 않는 것을 지우게 두지 않는다.
     */
    fun prune(selection: Set<String>, listing: DirectoryLister.Listing): Set<String>? {
        if (selection.isEmpty()) return null
        return when (listing) {
            is DirectoryLister.Listing.Scanning -> null
            is DirectoryLister.Listing.Failed -> emptySet()
            is DirectoryLister.Listing.Ready -> {
                val present = listing.entries.mapTo(HashSet(listing.entries.size * 2)) { it.path }
                val kept = selection.filterTo(HashSet()) { it in present }
                if (kept.size == selection.size) null else kept
            }
        }
    }
}

/**
 * **저절로** 다시 읽는 것(폴더 감시·화면으로 돌아왔을 때의 수정 시각 대조)을 언제 미루는가.
 *
 * 고르는 중에는 미룬다. 당겨서 새로고침을 고르는 중에 끄는 것과 같은 까닭이다 — 다시 읽혀 새 파일이 끼어들면
 * 줄이 밀리고, 사용자는 방금 보던 자리가 아닌 줄을 누른다. 고르기가 끝나면 그때 한 번 읽는다. **사라진 것은
 * 미루는 동안에도 선택에 남지 않는다** — 사용자가 시킨 작업의 결과(`onOperationFinished`)는 미루지 않고 읽으며,
 * 그 목록이 [SelectionRules.prune] 을 지난다.
 */
internal object AutoRefreshRules {

    /** 알림이 왔다. 지금 읽는가(아니면 미룬다). */
    fun readNow(selecting: Boolean): Boolean = !selecting

    /**
     * 고르기가 끝났다. 미뤄 둔 것을 읽는가. **미룬 폴더에 아직 있을 때만** — 다른 폴더로 옮기며 선택이 지워진
     * 것이면 그 폴더의 읽기가 이미 돌고 있다(거기서 또 읽으면 도는 읽기를 끊고 처음부터 한다).
     */
    fun readDeferred(deferredFor: String?, currentPath: String?): Boolean =
        deferredFor != null && deferredFor == currentPath
}

internal object RestoreStart {

    /**
     * '다른 곳에 복원' 을 고르기 시작할 폴더.
     *
     * 원래 폴더가 아직 있으면 거기다 — 대개 그 근처에 두고 싶어 한다. 원래 자리를 모르거나(정합성 검사가 되살린
     * 고아는 [io.github.donggi.iroiroviewer.io.TrashStore.UNKNOWN_PARENT]) 폴더가 사라졌으면 **그 항목의 볼륨 루트**,
     * 그 볼륨이 빠져 있으면 첫 볼륨이다. 붙은 볼륨이 하나도 없으면 null(갈 곳이 없다).
     *
     * @param volumes (볼륨 id, 경로).
     * @param isDirectory 파일시스템 질문. 시험은 가짜를 넘긴다.
     */
    fun folderFor(
        originalParent: String,
        volumeId: String,
        volumes: List<Pair<String, String>>,
        isDirectory: (String) -> Boolean,
    ): String? {
        if (originalParent.isNotEmpty() && isDirectory(originalParent)) return originalParent
        return volumes.firstOrNull { it.first == volumeId }?.second ?: volumes.firstOrNull()?.second
    }
}

internal object FolderStack {

    /**
     * 경로 하나로 **보통의 탐색과 같은** 백스택을 만든다 — 첫 화면에서 볼륨을 누르고 폴더를 하나씩 들어간 것과
     * 같은 모양이다. 그래야 빵부스러기가 `저장소 › 내부 저장소 › Download › 풀린 폴더` 로 보이고, 위로 가기가
     * 한 칸씩 올라간다. 경로 하나만 쌓으면 위로 한 번에 첫 화면으로 떨어진다.
     *
     * 볼륨 밖(알 수 없는 경로)이면 그 경로 하나다. 가장 긴 볼륨 경로가 이긴다(`VolumeRegistry.volumeOf` 와 같다).
     * 목록이 만드는 경로가 `부모/이름` 이므로 여기서도 `/` 로 잇는다.
     */
    fun stackFor(path: String, volumeRoots: List<String>): List<String> {
        val target = path.trimEnd('/').ifEmpty { "/" }
        val root = volumeRoots
            .map { it.trimEnd('/') }
            .filter { it.isNotEmpty() && (target == it || target.startsWith("$it/")) }
            .maxByOrNull { it.length }
            ?: return listOf(target)
        val out = ArrayList<String>()
        out += root
        var cur = root
        for (segment in target.removePrefix(root).split('/')) {
            if (segment.isEmpty()) continue
            cur = "$cur/$segment"
            out += cur
        }
        return out
    }
}
