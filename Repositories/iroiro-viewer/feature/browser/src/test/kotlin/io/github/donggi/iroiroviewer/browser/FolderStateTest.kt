package io.github.donggi.iroiroviewer.browser

import io.github.donggi.iroiroviewer.io.DirectoryLister
import io.github.donggi.iroiroviewer.model.FileEntry
import io.github.donggi.iroiroviewer.model.FileKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 폴더 목록의 순수 규칙 — 다시 읽는 동안 붙들기, 사라진 것을 뺀 선택, 보통의 탐색과 같은 백스택.
 */
class FolderStateTest {

    private val folder = "/storage/emulated/0/Download"

    private fun entry(name: String, dir: String = folder) = FileEntry(
        name = name,
        path = "$dir/$name",
        isDirectory = false,
        size = 1,
        lastModified = 0,
        isHidden = false,
        isSymlink = false,
        kind = FileKind.OTHER,
    )

    private fun ready(vararg names: String) =
        DirectoryLister.Listing.Ready(names.map { entry(it) }, hiddenCount = 0, scanMillis = 1)

    private val scanning = DirectoryLister.Listing.Scanning(0)

    // ---- 붙들기 ------------------------------------------------------------------

    @Test
    fun 같은_폴더를_다시_읽는_동안_옛_목록을_그대로_보인다() {
        val first = ready("a.txt", "b.txt")
        var h = ListingHold.next(ListingHold.INITIAL, TaggedListing(folder, 0, scanning))
        h = ListingHold.next(h, TaggedListing(folder, 0, first))
        assertEquals(0, h.completedTick)

        // 다시 읽기(번호 1)가 Scanning(0) 으로 시작한다 — 이것을 그대로 그리면 목록이 '0개 읽는 중' 이 된다.
        h = ListingHold.next(h, TaggedListing(folder, 1, scanning))
        assertSame(first, h.shown)
        assertTrue(h.refreshing)
        assertEquals(0, h.completedTick)

        // 읽는 동안 흘리는 진행(500개마다)도 붙든다.
        h = ListingHold.next(h, TaggedListing(folder, 1, DirectoryLister.Listing.Scanning(500)))
        assertSame(first, h.shown)

        val second = ready("a.txt")
        h = ListingHold.next(h, TaggedListing(folder, 1, second))
        assertSame(second, h.shown)
        assertFalse(h.refreshing)
        assertEquals(1, h.completedTick)
    }

    @Test
    fun 같은_목록이_다시_나와도_끝난_것을_안다() {
        // 꼬리표가 없으면 같은 값으로 합쳐져(StateFlow) 당겨서 새로고침의 표시가 꺼지지 않는다.
        var h = ListingHold.next(ListingHold.INITIAL, TaggedListing(folder, 0, ready("a.txt")))
        h = ListingHold.next(h, TaggedListing(folder, 1, scanning))
        h = ListingHold.next(h, TaggedListing(folder, 1, ready("a.txt")))
        assertEquals(1, h.completedTick)
    }

    @Test
    fun 다른_폴더로_옮기면_붙들지_않는다() {
        var h = ListingHold.next(ListingHold.INITIAL, TaggedListing(folder, 3, ready("a.txt")))
        h = ListingHold.next(h, TaggedListing("/storage/emulated/0/DCIM", 3, scanning))
        // 앞 폴더의 목록이 새 폴더의 이름 아래 보이면 그 위에서 누른 것이 엉뚱한 파일을 연다.
        assertEquals(scanning, h.shown)
        assertFalse(h.refreshing)
        assertEquals(-1, h.completedTick)
        h = ListingHold.next(h, TaggedListing("/storage/emulated/0/DCIM", 3, ready("b.jpg")))
        assertEquals(3, h.completedTick)
    }

    @Test
    fun 실패_화면도_다시_읽는_동안_붙든다() {
        val failed = DirectoryLister.Listing.Failed(DirectoryLister.Reason.NOT_FOUND)
        var h = ListingHold.next(ListingHold.INITIAL, TaggedListing(folder, 0, failed))
        assertEquals(0, h.completedTick)
        h = ListingHold.next(h, TaggedListing(folder, 1, scanning))
        assertSame(failed, h.shown)
        assertTrue(h.refreshing)
    }

    // ---- 선택 --------------------------------------------------------------------

    @Test
    fun 사라진_것은_선택에서_빠진다() {
        val selection = setOf("$folder/a.txt", "$folder/gone.txt")
        assertEquals(setOf("$folder/a.txt"), SelectionRules.prune(selection, ready("a.txt", "b.txt")))
    }

    @Test
    fun 바뀐_것이_없으면_그대로_둔다() {
        assertNull(SelectionRules.prune(setOf("$folder/a.txt"), ready("a.txt", "b.txt")))
        assertNull(SelectionRules.prune(emptySet(), ready("a.txt")))
        // 읽는 중에는 판단하지 않는다 — 아직 다 모르는 목록으로 줄이면 고른 것이 사라진다.
        assertNull(SelectionRules.prune(setOf("$folder/a.txt"), scanning))
    }

    @Test
    fun 폴더를_못_읽으면_선택을_비운다() {
        val failed = DirectoryLister.Listing.Failed(DirectoryLister.Reason.NOT_FOUND)
        assertEquals(emptySet(), SelectionRules.prune(setOf("$folder/a.txt"), failed))
    }

    // ---- 저절로 다시 읽기를 미루기 ----------------------------------------------------

    @Test
    fun 고르는_중에는_저절로_다시_읽지_않는다() {
        // 새 파일이 끼어들면 줄이 밀려 사용자가 다른 줄을 누른다(당겨서 새로고침을 끄는 것과 같은 까닭).
        assertFalse(AutoRefreshRules.readNow(selecting = true))
        assertTrue(AutoRefreshRules.readNow(selecting = false))
    }

    @Test
    fun 고르기가_끝나면_미룬_폴더에_있을_때만_읽는다() {
        assertTrue(AutoRefreshRules.readDeferred(deferredFor = folder, currentPath = folder))
        // 다른 폴더로 옮기며 선택이 지워졌다 — 그 폴더의 읽기가 이미 돈다.
        assertFalse(AutoRefreshRules.readDeferred(deferredFor = folder, currentPath = "/storage/emulated/0/DCIM"))
        assertFalse(AutoRefreshRules.readDeferred(deferredFor = folder, currentPath = null))
        assertFalse(AutoRefreshRules.readDeferred(deferredFor = null, currentPath = folder))
    }

    // ---- 백스택 ------------------------------------------------------------------

    private val roots = listOf("/storage/emulated/0", "/storage/0000-0000")

    @Test
    fun 보통의_탐색과_같은_백스택을_만든다() {
        assertEquals(
            listOf(
                "/storage/emulated/0",
                "/storage/emulated/0/Download",
                "/storage/emulated/0/Download/만화 1권",
            ),
            FolderStack.stackFor("/storage/emulated/0/Download/만화 1권", roots),
        )
        assertEquals(listOf("/storage/0000-0000"), FolderStack.stackFor("/storage/0000-0000/", roots))
    }

    @Test
    fun 볼륨_밖이면_그_경로_하나다() {
        assertEquals(listOf("/data/local/tmp"), FolderStack.stackFor("/data/local/tmp", roots))
        // 글자로만 앞이 같은 것은 그 볼륨이 아니다.
        assertEquals(listOf("/storage/emulated/01/x"), FolderStack.stackFor("/storage/emulated/01/x", roots))
    }

    @Test
    fun 가장_긴_볼륨이_이긴다() {
        assertEquals(
            listOf("/storage/emulated/0", "/storage/emulated/0/a"),
            FolderStack.stackFor("/storage/emulated/0/a", listOf("/storage", "/storage/emulated/0")),
        )
    }

    // ---- 다른 곳에 복원을 시작할 폴더 ------------------------------------------------

    private val volumes = listOf("primary" to "/storage/emulated/0", "0000-0000" to "/storage/0000-0000")

    @Test
    fun 원래_폴더가_있으면_거기서_시작한다() {
        assertEquals(folder, RestoreStart.folderFor(folder, "primary", volumes) { it == folder })
    }

    @Test
    fun 원래_자리를_모르거나_사라졌으면_그_볼륨_루트다() {
        assertEquals("/storage/0000-0000", RestoreStart.folderFor("", "0000-0000", volumes) { true })
        assertEquals("/storage/emulated/0", RestoreStart.folderFor(folder, "primary", volumes) { false })
    }

    @Test
    fun 그_볼륨이_빠져_있으면_첫_볼륨이고_볼륨이_없으면_갈_곳이_없다() {
        assertEquals("/storage/emulated/0", RestoreStart.folderFor("", "빠진-SD", volumes) { false })
        assertNull(RestoreStart.folderFor("", "primary", emptyList()) { false })
    }
}
