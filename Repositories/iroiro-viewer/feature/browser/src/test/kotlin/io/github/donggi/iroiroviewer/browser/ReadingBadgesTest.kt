package io.github.donggi.iroiroviewer.browser

import io.github.donggi.iroiroviewer.data.ComicProgressEntity
import io.github.donggi.iroiroviewer.data.DocProgressEntity
import io.github.donggi.iroiroviewer.data.ProgressLookup
import io.github.donggi.iroiroviewer.io.FileKey
import io.github.donggi.iroiroviewer.io.TrashStore
import io.github.donggi.iroiroviewer.model.FileEntry
import io.github.donggi.iroiroviewer.model.FileKind
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 읽던 쪽 배지 — 글자와 '다 읽음' 의 규칙, 키 묶음, 뷰어와 같은 키.
 */
class ReadingBadgesTest {

    // ---- 배지의 규칙 ----------------------------------------------------------------

    @Test
    fun 쪽은_1부터_센다() {
        val b = ReadingBadges.ofPages(page = 11, pageCount = 30, unit = BadgeUnit.PAGE)!!
        assertEquals(12, b.current)
        assertEquals(30, b.total)
        assertFalse(b.finished)
        assertEquals(0.4f, b.fraction)
    }

    @Test
    fun 마지막_쪽에_닿으면_다_읽음이다() {
        assertTrue(ReadingBadges.ofPages(29, 30, BadgeUnit.PAGE)!!.finished)
        assertFalse(ReadingBadges.ofPages(28, 30, BadgeUnit.PAGE)!!.finished)
        // 쪽 수를 넘는 값(있어서는 안 되지만)은 마지막 쪽으로 친다.
        val over = ReadingBadges.ofPages(40, 30, BadgeUnit.PAGE)!!
        assertEquals(30, over.current)
        assertTrue(over.finished)
    }

    @Test
    fun 자리가_하나뿐이면_배지가_없다() {
        // 열기만 해도 마지막 자리에 닿는다 — '다 읽음' 이 거짓이 된다.
        assertNull(ReadingBadges.ofPages(0, 1, BadgeUnit.PART))
        assertNull(ReadingBadges.ofPages(0, 0, BadgeUnit.PAGE))
        assertNull(ReadingBadges.ofPages(-1, 10, BadgeUnit.PAGE))
    }

    @Test
    fun 몫은_끝_가까이에서_다_읽음이다() {
        val near = ReadingBadges.ofProgress(0.995)!!
        assertTrue(near.finished)
        assertEquals(99, near.current)
        val half = ReadingBadges.ofProgress(0.5)!!
        assertFalse(half.finished)
        assertEquals(50, half.current)
        assertEquals(BadgeUnit.PERCENT, half.unit)
        assertTrue(ReadingBadges.ofProgress(1.7)!!.finished)
        assertNull(ReadingBadges.ofProgress(Double.NaN))
    }

    @Test
    fun 문서는_몫이_있으면_몫을_쓰고_없으면_쪽을_쓴다() {
        val withProgress = DocProgressEntity("k", page = 1, pageCount = 10, progress = 0.3, displayName = "", updatedAt = 0)
        assertEquals(BadgeUnit.PERCENT, ReadingBadges.ofDoc(withProgress, FileKind.EBOOK)!!.unit)

        val pdf = DocProgressEntity("k", page = 4, pageCount = 10, displayName = "", updatedAt = 0)
        assertEquals(BadgeUnit.PAGE, ReadingBadges.ofDoc(pdf, FileKind.PDF)!!.unit)
        assertEquals(5, ReadingBadges.ofDoc(pdf, FileKind.PDF)!!.current)
        // EPUB 의 `page` 는 장 번호다.
        assertEquals(BadgeUnit.CHAPTER, ReadingBadges.ofDoc(pdf, FileKind.EBOOK)!!.unit)
        assertEquals(BadgeUnit.SLIDE, ReadingBadges.ofDoc(pdf, FileKind.SLIDE)!!.unit)
        assertEquals(BadgeUnit.PART, ReadingBadges.ofDoc(pdf, FileKind.HWP)!!.unit)

        assertNull(ReadingBadges.ofDoc(DocProgressEntity("k", displayName = "", updatedAt = 0), FileKind.PDF))
    }

    @Test
    fun 만화와_문서_파일에만_배지를_단다() {
        fun e(kind: FileKind, dir: Boolean = false) =
            FileEntry("x", "/x", isDirectory = dir, size = 0, lastModified = 0, isHidden = false, isSymlink = false, kind = kind)
        assertTrue(ReadingBadges.wantsBadge(e(FileKind.COMIC)))
        assertTrue(ReadingBadges.wantsBadge(e(FileKind.PDF)))
        assertTrue(ReadingBadges.wantsBadge(e(FileKind.DOCUMENT)))
        assertFalse(ReadingBadges.wantsBadge(e(FileKind.IMAGE)))
        assertFalse(ReadingBadges.wantsBadge(e(FileKind.ARCHIVE)))
        // 폴더 만화는 뺀다 — 뷰어의 키가 폴더의 st_size 를 쓰는데 목록은 폴더 크기를 0 으로 적는다.
        assertFalse(ReadingBadges.wantsBadge(e(FileKind.FOLDER, dir = true)))
    }

    // ---- 키 묶음 ------------------------------------------------------------------

    @Test
    fun 키는_상한씩_나눠_묻고_겹치는_것은_한_번만_묻는다() {
        val keys = (0 until 1_201).map { "k$it" }
        val chunks = ReadingBadges.chunks(keys + keys.take(10))
        assertEquals(listOf(ProgressLookup.MAX_KEYS, ProgressLookup.MAX_KEYS, 201), chunks.map { it.size })
        assertEquals(1_201, chunks.flatten().toSet().size)
        assertTrue(ReadingBadges.chunks(emptyList()).isEmpty())
    }

    // ---- 뷰어와 같은 키 -------------------------------------------------------------

    private val tmp: File = Files.createTempDirectory("badges").toFile()

    @AfterTest
    fun cleanUp() {
        tmp.deleteRecursively()
    }

    /** 목록이 만드는 모양 그대로 — 수정 시각은 `st_mtime * 1000`(초 단위)이다. */
    private fun listed(file: File, kind: FileKind) = FileEntry(
        name = file.name,
        path = file.absolutePath,
        isDirectory = false,
        size = file.length(),
        lastModified = file.lastModified() / 1000 * 1000,
        isHidden = false,
        isSymlink = false,
        kind = kind,
    )

    private fun make(name: String, millis: Long = 1_700_000_000_123L): File =
        File(tmp, name).apply {
            writeText("x")
            setLastModified(millis)
        }

    @Test
    fun 키는_뷰어와_같은_호출로_만든다() {
        val f = make("책.cbz")
        assertEquals(FileKey.of(f.name, f.length(), f.lastModified()), ReadingBadges.keyFor(f))
        // 이 시험이 뜻을 가지려면 밀리초가 살아 있어야 한다(NTFS·ext4 는 산다).
        assertNotEquals(f.lastModified() / 1000 * 1000, f.lastModified())
    }

    @Test
    fun 목록의_항목에서_뷰어가_적은_기록을_찾는다() = runTest {
        val comic = make("책.cbz")
        val pdf = make("문서.pdf")
        val text = make("메모.txt")
        val entries = listOf(listed(comic, FileKind.COMIC), listed(pdf, FileKind.PDF), listed(text, FileKind.TEXT))

        // 뷰어가 적는 키 — `FileKey.of(file.name, file.length(), file.lastModified())`.
        val comicKey = FileKey.of(comic.name, comic.length(), comic.lastModified())
        val pdfKey = FileKey.of(pdf.name, pdf.length(), pdf.lastModified())
        val comics = listOf(ComicProgressEntity(comicKey, page = 11, pageCount = 30, readDirection = 0, displayName = "", updatedAt = 0))
        val docs = listOf(DocProgressEntity(pdfKey, page = 9, pageCount = 10, displayName = "", updatedAt = 0))

        val badges = ReadingBadges.lookup(
            folder = tmp.absolutePath,
            entries = entries,
            findComics = { keys -> comics.filter { it.fileKey in keys } },
            findDocs = { keys -> docs.filter { it.fileKey in keys } },
        )
        assertEquals(12, badges[comic.absolutePath]?.current)
        assertTrue(badges[pdf.absolutePath]!!.finished)
        assertNull(badges[text.absolutePath])
    }

    @Test
    fun 한_번에_묻는_키는_상한을_넘지_않는다() = runTest {
        val entries = (0 until 1_100).map { listed(make("권 $it.cbz"), FileKind.COMIC) }
        val asked = ArrayList<Int>()
        ReadingBadges.lookup(
            folder = tmp.absolutePath,
            entries = entries,
            findComics = { keys -> asked += keys.size; emptyList() },
            findDocs = { error("문서가 없는데 물었다") },
        )
        assertEquals(1_100, asked.sum())
        assertTrue(asked.all { it <= ProgressLookup.MAX_KEYS })
    }

    @Test
    fun 휴지통에서는_찾지_않는다() = runTest {
        val trash = "${tmp.absolutePath.replace('\\', '/')}/${TrashStore.DIR_NAME}"
        val entries = listOf(listed(make("책.cbz"), FileKind.COMIC))
        val badges = ReadingBadges.lookup(
            folder = trash,
            entries = entries,
            findComics = { error("휴지통에서 물었다") },
            findDocs = { error("휴지통에서 물었다") },
        )
        assertTrue(badges.isEmpty())
    }
}
