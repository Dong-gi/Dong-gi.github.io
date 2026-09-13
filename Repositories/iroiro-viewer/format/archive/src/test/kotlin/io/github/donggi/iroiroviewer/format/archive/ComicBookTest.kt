package io.github.donggi.iroiroviewer.format.archive

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 엔트리 목록 → 쪽 목록.
 *
 * **이 시험이 지키는 것은 `entryIndex` 가 살아 남는다는 것이다.** 정렬과 걸러내기를
 * 거치고 나면 목록 안의 자리와 아카이브 안의 자리가 달라지고, 그것을 혼동하면 사용자가
 * 보는 쪽과 다른 바이트가 열린다 — 2단계가 ZIP 에서, 8단계가 7z 에서 이미 한 번씩 밟았다.
 */
class ComicBookTest {

    private fun entry(
        index: Int,
        name: String,
        size: Long = 100,
        directory: Boolean = false,
        link: Boolean = false,
        encrypted: Boolean = false,
        safe: String? = name,
    ) = ArchiveEntry(
        index = index,
        name = name,
        safeName = safe,
        declaredSize = size,
        compressedSize = size,
        isDirectory = directory,
        isLink = link,
        isEncrypted = encrypted,
        crc = -1,
        nameCharset = "UTF-8",
    )

    @Test
    fun `자리가 아니라 엔트리 번호를 들고 간다`() {
        val entries = listOf(
            entry(0, "readme.txt"),
            entry(1, "10.png"),
            entry(2, "2.png"),
            entry(3, "1.png"),
        )
        val pages = ComicBook.pagesOf(entries)
        assertEquals(listOf("1.png", "2.png", "10.png"), pages.map { it.name })
        assertEquals(listOf(3, 2, 1), pages.map { it.entryIndex })
        assertEquals(listOf(0, 1, 2), pages.map { it.ordinal })
    }

    @Test
    fun `읽을 수 없는 항목은 쪽이 아니다`() {
        val entries = listOf(
            entry(0, "01.png"),
            entry(1, "02.png", encrypted = true),
            entry(2, "03.png", link = true),
            entry(3, "04.png", safe = null),
            entry(4, "05.png", directory = true),
            entry(5, "06.png"),
        )
        val pages = ComicBook.pagesOf(entries)
        assertEquals(listOf("01.png", "06.png"), pages.map { it.name })
        // 번호가 촘촘해야 한다 — 화면이 '2쪽 중 1쪽' 이라고 말하고, 그 말이 맞아야 한다.
        assertEquals(listOf(0, 1), pages.map { it.ordinal })
    }

    @Test
    fun `찌꺼기가 쪽 수를 두 배로 만들지 않는다`() {
        val entries = listOf(
            entry(0, "__MACOSX/._01.png"),
            entry(1, "01.png"),
            entry(2, "__MACOSX/._02.png"),
            entry(3, "02.png"),
            entry(4, "ComicInfo.xml"),
        )
        assertEquals(2, ComicBook.pagesOf(entries).size)
    }

    @Test
    fun `같은 이름 둘이 둘 다 쪽이다`() {
        val entries = listOf(entry(0, "a.png"), entry(1, "a.png"))
        val pages = ComicBook.pagesOf(entries)
        assertEquals(2, pages.size)
        assertEquals(listOf(0, 1), pages.map { it.entryIndex })
    }

    /** 루트의 표지가 장 폴더보다 앞선다 — 만화의 흔한 구성이다. */
    @Test
    fun `표지는 첫 쪽이다`() {
        val entries = listOf(entry(0, "ch01/002.jpg"), entry(1, "cover.jpg"))
        assertEquals("cover.jpg", ComicBook.pagesOf(entries).firstOrNull()?.name)
    }

    @Test
    fun `그림이 없으면 빈 목록이다`() {
        assertTrue(ComicBook.pagesOf(emptyList()).isEmpty())
        assertTrue(ComicBook.pagesOf(listOf(entry(0, "a.txt"))).isEmpty())
    }

    /** 선언 크기는 음수일 수 있다(7z 의 스트림 없는 엔트리). 분모로 쓰이므로 0 으로 눕힌다. */
    @Test
    fun `선언 크기가 음수면 0이다`() {
        val pages = ComicBook.pagesOf(listOf(entry(0, "a.png", size = -1)))
        assertEquals(0L, pages.single().declaredSize)
    }
}
