package io.github.donggi.iroiroviewer.format.archive

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 무엇이 쪽이고 어떤 차례인가.
 *
 * **이 시험이 지키는 것은 '사람이 기대하는 차례' 다.** 1·2·10 이 1·10·2 로 서면 만화가
 * 통째로 뒤죽박죽이 되고, 쓰레기 엔트리를 세면 한 장 건너 한 장이 깨진 그림이 된다.
 * 둘 다 화면을 띄우지 않고 확인할 수 있어야 고치는 값이 싸다.
 */
class ComicPagesTest {

    private fun ordered(vararg names: String): List<String> {
        val list = names.toList()
        return ComicPages.order(list).map { list[it] }
    }

    @Test
    fun `사전식이 아니라 자연 정렬이다`() {
        assertEquals(
            listOf("1.png", "2.png", "10.png", "11.png"),
            ordered("10.png", "1.png", "11.png", "2.png"),
        )
    }

    @Test
    fun `앞의 0 이 있어도 같은 차례다`() {
        assertEquals(
            listOf("p001.jpg", "p002.jpg", "p010.jpg"),
            ordered("p010.jpg", "p001.jpg", "p002.jpg"),
        )
    }

    /** 장별 폴더. **폴더 먼저 세우고 그 안에서 파일을 센다.** */
    @Test
    fun `장별 폴더가 차례대로 선다`() {
        assertEquals(
            listOf("ch1/p1.png", "ch1/p10.png", "ch2/p1.png", "ch10/p1.png"),
            ordered("ch10/p1.png", "ch2/p1.png", "ch1/p10.png", "ch1/p1.png"),
        )
    }

    /** macOS 의 `__MACOSX` 는 쪽마다 하나씩 들어 있다. 세면 쪽 수가 두 배가 된다. */
    @Test
    fun `맥 찌꺼기를 세지 않는다`() {
        assertEquals(
            listOf("01.png", "02.png"),
            ordered("__MACOSX/._01.png", "01.png", "__MACOSX/._02.png", "02.png"),
        )
    }

    @Test
    fun `숨김 파일과 알려진 찌꺼기를 세지 않는다`() {
        assertEquals(
            listOf("a.png"),
            ordered("a.png", "Thumbs.db", "desktop.ini", ".DS_Store", "._a.png", "ComicInfo.xml"),
        )
    }

    /** 플랫폼이 못 읽는 확장자는 쪽이 아니다 — 세면 영영 안 열리는 쪽이 생긴다. */
    @Test
    fun `SVG 와 TIFF 는 쪽이 아니다`() {
        assertFalse(ComicPages.isPage("a.svg"))
        assertFalse(ComicPages.isPage("a.tiff"))
        assertFalse(ComicPages.isPage("a.tif"))
        assertTrue(ComicPages.isPage("a.webp"))
        assertTrue(ComicPages.isPage("a.heic"))
    }

    @Test
    fun `대문자 확장자도 쪽이다`() {
        assertTrue(ComicPages.isPage("A.PNG"))
        assertEquals(listOf("A.PNG", "b.JpG"), ordered("A.PNG", "b.JpG"))
    }

    @Test
    fun `확장자가 없으면 쪽이 아니다`() {
        assertFalse(ComicPages.isPage("README"))
        assertFalse(ComicPages.isPage("폴더/"))
        assertFalse(ComicPages.isPage(""))
    }

    /**
     * **자리를 돌려준다. 이름이 아니다.**
     *
     * 같은 이름의 엔트리가 실재하고, 이름으로 되짚으면 사용자가 보는 쪽과 다른 바이트가
     * 열린다 — 2단계가 아카이브에서 이미 한 번 밟은 결함이다.
     */
    @Test
    fun `같은 이름이 둘이어도 둘 다 쪽이다`() {
        val names = listOf("same.png", "same.png", "b.png")
        val order = ComicPages.order(names)
        assertEquals(3, order.size, "하나로 접히면 안 된다")
        // 이름순이므로 `b.png`(자리 2)가 먼저고, 같은 이름끼리는 **원래 자리 순서**를 지킨다.
        assertEquals(listOf(2, 0, 1), order)
    }

    @Test
    fun `한글 이름도 자연 정렬된다`() {
        assertEquals(
            listOf("1화 1.png", "1화 2.png", "2화 1.png", "10화 1.png"),
            ordered("10화 1.png", "2화 1.png", "1화 2.png", "1화 1.png"),
        )
    }

    /**
     * **루트의 표지가 장 폴더보다 앞선다.**
     *
     * `ArchiveTree` 의 정렬은 폴더를 먼저 놓는데(목록 화면의 규칙이다) 만화에서는 그것이
     * 틀리다 — 루트에 `cover.jpg` 가 있고 `ch01/` 이 있는 흔한 구성에서 표지가 맨 뒤로 간다.
     * 여기서는 폴더 부분을 1차 키로 쓰므로 **빈 폴더 이름이 어떤 폴더 이름보다 앞선다.**
     */
    @Test
    fun `루트의 표지가 장 폴더보다 앞선다`() {
        assertEquals(
            listOf("cover.jpg", "ch01/001.jpg", "ch02/001.jpg"),
            ordered("ch02/001.jpg", "ch01/001.jpg", "cover.jpg"),
        )
    }

    @Test
    fun `표지는 첫 쪽이다`() {
        val names = listOf("03.png", "01.png", "Thumbs.db", "02.png")
        assertEquals(1, ComicPages.coverIndex(names))
        assertEquals(-1, ComicPages.coverIndex(listOf("a.txt", "b.txt")))
    }

    @Test
    fun `그림이 하나도 없으면 빈 목록이다`() {
        assertEquals(emptyList(), ComicPages.order(listOf("a.txt", "b.xml", "폴더/")))
    }

    /** 역슬래시로 만든 윈도우 아카이브도 찌꺼기 판정이 된다. */
    @Test
    fun `역슬래시 경로도 가른다`() {
        assertTrue(ComicPages.isJunk("__MACOSX\\._a.png"))
        assertTrue(ComicPages.isPage("ch1\\p1.png"))
    }
}
