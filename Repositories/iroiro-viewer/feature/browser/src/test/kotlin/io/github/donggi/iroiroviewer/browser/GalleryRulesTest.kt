package io.github.donggi.iroiroviewer.browser

import io.github.donggi.iroiroviewer.io.FileOpEngine
import io.github.donggi.iroiroviewer.io.FileOpManager
import io.github.donggi.iroiroviewer.io.MimeResolver
import io.github.donggi.iroiroviewer.model.FileEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** 갤러리의 판단(`GalleryRules`)과 '위치 지우고 공유' 가 끝난 뒤 할 일(`ShareFolders.afterClean`). */
class GalleryRulesTest {

    private val dir = "/storage/emulated/0/DCIM/Camera"

    private fun e(name: String) = FileEntry(
        name = name,
        path = "$dir/$name",
        isDirectory = false,
        size = 1,
        lastModified = 0,
        isHidden = false,
        isSymlink = false,
        kind = MimeResolver.kindOf(name, false),
    )

    /** 차례는 고르기 풀기 → 앨범 닫기 → 갤러리 닫기. 앨범 안에서 고르던 것은 한 번에 앨범까지 닫지 않는다. */
    @Test
    fun `뒤로 가기의 차례`() {
        assertEquals(GalleryRules.Back.CLEAR_SELECTION, GalleryRules.back(selecting = true, inAlbum = true))
        assertEquals(GalleryRules.Back.CLEAR_SELECTION, GalleryRules.back(selecting = true, inAlbum = false))
        assertEquals(GalleryRules.Back.CLOSE_ALBUM, GalleryRules.back(selecting = false, inAlbum = true))
        assertEquals(GalleryRules.Back.CLOSE_GALLERY, GalleryRules.back(selecting = false, inAlbum = false))
    }

    /** 다시 훑은 뒤 사라진 것은 고르기에서 빠진다. 바뀐 것이 없으면 같은 집합 그대로(상태를 다시 내지 않게). */
    @Test
    fun `사라진 것은 고르기에서 빠진다`() {
        val a = e("a.jpg")
        val b = e("b.jpg")
        val picked = setOf(a.path, b.path)
        assertSame(picked, GalleryRules.prune(picked, listOf(a, b, e("c.jpg"))))
        assertEquals(setOf(a.path), GalleryRules.prune(picked, listOf(a)))
        val none = emptySet<String>()
        assertSame(none, GalleryRules.prune(none, emptyList()))
    }

    /** 싣는 차례는 격자의 차례(새것부터)다 — 누른 차례가 아니다. 목록에 없는 것은 뺀다. */
    @Test
    fun `싣는 차례는 격자의 차례`() {
        val items = listOf(e("3.jpg"), e("2.jpg"), e("1.jpg"))
        val picked = linkedSetOf(items[2].path, items[0].path, "$dir/gone.jpg")
        assertEquals(listOf(items[0], items[2]), GalleryRules.ordered(picked, items))
        assertTrue(GalleryRules.ordered(emptySet(), items).isEmpty())
    }

    /** 곧바로 공유하는 수의 상한 — 넘으면 ZIP 을 권한다. 0 개는 공유가 아니다. */
    @Test
    fun `공유 상한`() {
        assertTrue(GalleryRules.canShareDirectly(1))
        assertTrue(GalleryRules.canShareDirectly(GalleryRules.SHARE_LIMIT))
        assertFalse(GalleryRules.canShareDirectly(GalleryRules.SHARE_LIMIT + 1))
        assertFalse(GalleryRules.canShareDirectly(0))
    }

    /** 위치 지우기 — 사본은 JPEG·PNG·WebP, 원본 자리는 JPEG·WebP. 할 수 있는 것이 하나도 없으면 메뉴를 끈다. */
    @Test
    fun `위치 지우기의 계획`() {
        val plan = GalleryRules.plan(listOf("$dir/a.jpg", "$dir/b.png", "$dir/c.webp", "$dir/d.mp4", "$dir/e.heic"))
        assertEquals(GalleryRules.Plan(total = 5, scrubCopies = 3, scrubInPlace = 2), plan)
        assertTrue(plan.canShareClean)
        assertTrue(plan.canStripInPlace)
        val pngOnly = GalleryRules.plan(listOf("$dir/s.png"))
        assertTrue(pngOnly.canShareClean)
        assertFalse(pngOnly.canStripInPlace, "PNG 는 원본에서 지우지 않는다")
        val videos = GalleryRules.plan(listOf("$dir/v.mp4", "$dir/h.heic"))
        assertFalse(videos.canShareClean)
        assertFalse(videos.canStripInPlace)
    }

    /** 만든 사본이 없으면 보내지 않고 말한다. 건너뛴 것·못 읽은 것이 있으면 보내고 돌아와서 말한다. */
    @Test
    fun `위치 지우고 공유가 끝난 뒤 할 일`() {
        val copies = FileOpManager.Extra.ShareCopies(listOf("/c/share/zip/u/0/a.jpg"))
        assertEquals(ShareFolders.After.SHARE, ShareFolders.afterClean(FileOpEngine.Outcome.Done(1, 0, 0), copies))
        assertEquals(ShareFolders.After.SHARE_WITH_SKIPS, ShareFolders.afterClean(FileOpEngine.Outcome.Done(1, 2, 0), copies))
        assertEquals(ShareFolders.After.SHARE_WITH_SKIPS, ShareFolders.afterClean(FileOpEngine.Outcome.Done(1, 0, 1), copies))
        assertEquals(
            ShareFolders.After.NOTHING,
            ShareFolders.afterClean(FileOpEngine.Outcome.Done(0, 3, 0), FileOpManager.Extra.ShareCopies(emptyList())),
        )
        assertEquals(ShareFolders.After.NOTHING, ShareFolders.afterClean(FileOpEngine.Outcome.Done(0, 3, 0), null))
        assertEquals(ShareFolders.After.CANCELLED, ShareFolders.afterClean(FileOpEngine.Outcome.Cancelled, copies))
        assertEquals(
            ShareFolders.After.FAILED,
            ShareFolders.afterClean(FileOpEngine.Outcome.Failed(FileOpEngine.Reason.NO_SPACE, ""), null),
        )
    }
}
