package io.github.donggi.iroiroviewer.comic

import io.github.donggi.iroiroviewer.comic.ComicOpen.Kind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** 만화 뷰어 위쪽 막대의 제목([ComicTitle]). 여는 중·실패 화면에도 무엇을 열고 있는지 적는다. */
class ComicTitleTest {

    private val path = "/storage/emulated/0/만화/원피스 01권.cbz"

    @Test
    fun `여는 중과 실패 화면은 여는 책의 이름이다`() {
        val states = listOf(
            ComicViewModel.State.Loading,
            ComicViewModel.State.Failed(Kind.CORRUPT, path),
            ComicViewModel.State.Failed(Kind.UNREADABLE, null),
            ComicViewModel.State.Failed(Kind.NEEDS_PASSWORD, path),
        )
        for (state in states) {
            // 열린 뒤의 이름(`Book.name` — 확장자를 뗀다)과 같은 모양이다. 열리는 순간 제목 글자가 바뀌지 않는다.
            assertEquals("원피스 01권", ComicTitle.of(state, path), state.toString())
            assertFalse('/' in ComicTitle.of(state, path), "전체 경로를 적지 않는다")
        }
    }

    @Test
    fun `폴더로 연 만화는 폴더 이름이다`() {
        assertEquals("웹툰", ComicTitle.of(ComicViewModel.State.Loading, "/storage/emulated/0/웹툰/"))
        // 점이 든 폴더 이름은 확장자가 아니다 — 만화·압축 확장자만 뗀다.
        assertEquals("Vol.1", ComicTitle.of(ComicViewModel.State.Loading, "/storage/emulated/0/Vol.1"))
    }

    @Test
    fun `압축 파일로 연 만화도 확장자를 뗀다`() {
        // 일반 압축 안의 그림으로 들어온 길 — 책은 그 압축 파일이다(`Book.name` 이 `nameWithoutExtension`).
        assertEquals("사진", ComicTitle.bookNameOf("사진.zip"))
        assertEquals("backup.tar", ComicTitle.bookNameOf("backup.tar.gz"))
        assertEquals("권1", ComicTitle.bookNameOf("권1.CB7"))
    }
}
