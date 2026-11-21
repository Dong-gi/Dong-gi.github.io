package io.github.donggi.iroiroviewer.browser

import io.github.donggi.iroiroviewer.io.FileOpEngine
import io.github.donggi.iroiroviewer.io.FileOpManager
import io.github.donggi.iroiroviewer.io.MimeResolver
import io.github.donggi.iroiroviewer.model.FileEntry
import io.github.donggi.iroiroviewer.model.FileKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 공유에 폴더가 섞였을 때(`ShareFolders`)와 선택 메뉴의 '정보' 가 겨눌 것(`OpenWithRules.singleEntry`).
 */
class ShareFoldersTest {

    private val folder = "/storage/emulated/0/Download"

    private fun entry(name: String, isDirectory: Boolean = false, isSymlink: Boolean = false) = FileEntry(
        name = name,
        path = "$folder/$name",
        isDirectory = isDirectory,
        size = if (isDirectory) 0 else 1,
        lastModified = 0,
        isHidden = false,
        isSymlink = isSymlink,
        kind = MimeResolver.kindOf(name, isDirectory),
    )

    @Test
    fun `파일만 골랐으면 묻지 않는다`() {
        val a = entry("a.jpg")
        val b = entry("b.pdf")
        val split = ShareFolders.split(listOf(a.path, b.path), listOf(a, b)) { error("목록에 있는 것은 묻지 않는다") }
        assertFalse(split.needsChoice)
        assertEquals(listOf(a.path, b.path), split.files)
    }

    @Test
    fun `폴더가 하나라도 섞이면 묻고, 고른 차례대로 가른다`() {
        val a = entry("a.jpg")
        val d = entry("사진", isDirectory = true)
        val b = entry("b.pdf")
        val split = ShareFolders.split(listOf(a.path, d.path, b.path), listOf(a, d, b)) { false }
        assertTrue(split.needsChoice)
        assertEquals(listOf(a.path, b.path), split.files)
        assertEquals(listOf(d.path), split.folders)
        // 묶을 때는 고른 차례 그대로(ZIP 의 맨 위 차례).
        assertEquals(listOf(a.path, d.path, b.path), split.all)
    }

    @Test
    fun `폴더를 가리키는 링크는 폴더가 아니다 — 목록의 값을 쓴다`() {
        // 나열은 lstat 으로 정한다 — 링크는 isDirectory 가 거짓이다. 파일 쪽(공유)이 그것을 다시 거른다(`ShareHelper.uriOf`).
        val ln = entry("바로가기", isSymlink = true)
        val split = ShareFolders.split(listOf(ln.path), listOf(ln)) { true }
        assertFalse(split.needsChoice)
    }

    @Test
    fun `목록에 없는 것(이름 필터에 가려졌다)만 묻는다`() {
        val a = entry("a.jpg")
        val asked = ArrayList<String>()
        val hidden = "$folder/가려진 폴더"
        val split = ShareFolders.split(listOf(a.path, hidden), listOf(a)) { asked += it; true }
        assertEquals(listOf(hidden), asked)
        assertEquals(listOf(hidden), split.folders)
    }

    @Test
    fun `묶어 보내기가 끝난 뒤 할 일`() {
        val ready = FileOpManager.Extra.ShareReady("/data/x/cache/share/zip/u/a.zip")
        assertEquals(ShareFolders.After.SHARE, ShareFolders.after(FileOpEngine.Outcome.Done(3, 0, 0), ready))
        assertEquals(ShareFolders.After.SHARE_WITH_SKIPS, ShareFolders.after(FileOpEngine.Outcome.Done(3, 1, 0), ready))
        assertEquals(ShareFolders.After.SHARE_WITH_SKIPS, ShareFolders.after(FileOpEngine.Outcome.Done(3, 0, 2), ready))
        // 링크만 골랐다 — 만든 ZIP 이 없다. 빈 공유 창을 띄우지 않는다.
        assertEquals(ShareFolders.After.NOTHING, ShareFolders.after(FileOpEngine.Outcome.Done(0, 2, 0), null))
        assertEquals(ShareFolders.After.CANCELLED, ShareFolders.after(FileOpEngine.Outcome.Cancelled, null))
        assertEquals(
            ShareFolders.After.FAILED,
            ShareFolders.after(FileOpEngine.Outcome.Failed(FileOpEngine.Reason.TOO_LARGE, "a.zip"), null),
        )
        // 놓은 뒤에 온 취소는 큐가 '놓였다' 로 바꾼다(`FileOpManager.execute`) — 그때는 Done 과 ZIP 이 함께 온다.
        assertEquals(ShareFolders.After.SHARE, ShareFolders.after(FileOpEngine.Outcome.Done(1, 0, 0), ready))
    }

    @Test
    fun `빠진 것은 0 인 조각을 말하지 않는다`() {
        assertEquals(emptyList(), ShareFolders.leftOut(0, 0))
        assertEquals(listOf(ShareFolders.LeftOut.SKIPPED to 3), ShareFolders.leftOut(3, 0))
        assertEquals(listOf(ShareFolders.LeftOut.UNREADABLE to 2), ShareFolders.leftOut(0, 2))
        assertEquals(
            listOf(ShareFolders.LeftOut.SKIPPED to 1, ShareFolders.LeftOut.UNREADABLE to 4),
            ShareFolders.leftOut(1, 4),
        )
    }

    @Test
    fun `다른 앱에서 연 화면이 맡긴 결과는 맡긴 차례로 하나씩 꺼낸다`() {
        while (ShareZipHandoff.poll() != null) Unit
        val a = FileOpManager.Finished("a", FileOpManager.Kind.SHARE_ZIP, FileOpEngine.Outcome.Done(1, 0, 0))
        val b = FileOpManager.Finished("b", FileOpManager.Kind.SHARE_ZIP, FileOpEngine.Outcome.Cancelled)
        ShareZipHandoff.hold(a)
        ShareZipHandoff.hold(b)
        assertSame(a, ShareZipHandoff.poll())
        assertSame(b, ShareZipHandoff.poll())
        assertNull(ShareZipHandoff.poll())
    }

    @Test
    fun `정보는 하나를 골랐을 때 폴더도 겨눈다`() {
        val dir = entry("폴더", isDirectory = true)
        val pdf = entry("책.pdf")
        val shown = listOf(dir, pdf)
        assertSame(dir, OpenWithRules.singleEntry(setOf(dir.path), shown))
        assertSame(pdf, OpenWithRules.singleEntry(setOf(pdf.path), shown))
        assertNull(OpenWithRules.singleEntry(setOf(dir.path, pdf.path), shown))
        assertNull(OpenWithRules.singleEntry(emptySet(), shown))
        assertNull(OpenWithRules.singleEntry(setOf("$folder/가려진"), shown))
        // '다른 앱으로 열기' 는 여전히 폴더를 겨누지 않는다.
        assertNull(OpenWithRules.singleFile(setOf(dir.path), shown))
        assertEquals(FileKind.FOLDER, dir.kind)
    }
}
