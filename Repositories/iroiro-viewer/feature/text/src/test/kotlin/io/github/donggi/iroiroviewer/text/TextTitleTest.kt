package io.github.donggi.iroiroviewer.text

import io.github.donggi.iroiroviewer.charset.CharsetDetector
import io.github.donggi.iroiroviewer.charset.TextEncoding
import io.github.donggi.iroiroviewer.format.text.PlainHighlighter
import io.github.donggi.iroiroviewer.format.text.RowIndex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** 텍스트 뷰어 위쪽 막대의 제목([TextTitle]). 다 읽기 전·실패 화면에도 무엇을 열었는지 적는다. */
class TextTitleTest {

    private val path = "/storage/emulated/0/Download/server log (2).txt"

    @Test
    fun 다_읽기_전과_실패_화면은_파일_이름이다() {
        val states = listOf(
            TextViewModel.State.Loading(),
            TextViewModel.State.Loading(reached = true),
            TextViewModel.State.Indexing(rows = 10, scanned = 1024, total = 1 shl 20),
        ) + TextViewModel.State.Failed.Kind.entries.map { TextViewModel.State.Failed(it) }
        for (state in states) assertEquals("server log (2).txt", TextTitle.of(state, path), state.toString())
    }

    @Test
    fun 다_읽은_파일은_그_이름_그대로다() {
        assertEquals("README.md", TextTitle.of(TextViewModel.State.Ready(doc("/storage/emulated/0/README.md")), path))
    }

    @Test
    fun 전체_경로를_적지_않는다() {
        assertFalse('/' in TextTitle.of(TextViewModel.State.Loading(), path))
        assertEquals("a.txt", TextTitle.fileNameOf("/storage/emulated/0/a.txt"))
        // 끝의 빗금은 떼고 자른다(`File` 의 정규화와 같다). 빗금이 없으면 그대로다.
        assertEquals("a.txt", TextTitle.fileNameOf("/storage/emulated/0/a.txt/"))
        assertEquals("a.txt", TextTitle.fileNameOf("a.txt"))
        // 안드로이드에서 역슬래시는 이름의 글자다 — 윈도의 구분자로 읽지 않는다.
        assertEquals("a\\b.txt", TextTitle.fileNameOf("/storage/emulated/0/a\\b.txt"))
        assertEquals("", TextTitle.fileNameOf(""))
    }

    /** 한 줄짜리 문서. 이 시험은 행을 읽지 않는다 — 상태의 모양만 본다. */
    private fun doc(path: String): TextViewModel.Doc {
        val head = "hello".toByteArray()
        return TextViewModel.Doc(
            path = path,
            name = path.substringAfterLast('/'),
            sizeBytes = head.size.toLong(),
            encoding = TextEncoding.UTF_8,
            detection = CharsetDetector.detect(head),
            bomLength = 0,
            index = RowIndex(LongArray(0), IntArray(0), IntArray(0), 0, rowCount = 1, lineCount = 1, complete = true, sourceBytes = 5),
            language = PlainHighlighter,
            highlight = null,
        )
    }
}
