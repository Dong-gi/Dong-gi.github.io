package io.github.donggi.iroiroviewer.docview

import io.github.donggi.iroiroviewer.format.OpenFailure
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** 문서 뷰어 위쪽 막대의 제목([DocTitle]). 여는 중·실패 화면에도 무엇을 열고 있는지 적는다. */
class DocTitleTest {

    private val path = "/storage/emulated/0/Documents/보고서 (최종).epub"

    @Test
    fun 여는_중과_실패_화면은_파일_이름이다() {
        val states = listOf(
            DocViewModel.State.Loading(),
            DocViewModel.State.Loading(reached = true),
            DocViewModel.State.Failed(OpenFailure.PasswordRequired("암호")),
            DocViewModel.State.Failed(OpenFailure.Unsupported("여는이가 없다")),
            DocViewModel.State.Failed(OpenFailure.Io("파일이 없다"), unreachable = true),
        )
        for (state in states) assertEquals("보고서 (최종).epub", DocTitle.of(state, path), state.toString())
    }

    @Test
    fun 열린_문서는_문서가_준_이름_그대로다() {
        // 메타데이터의 제목이 있으면 그것이다 — 파일 이름으로 바꾸지 않는다(14단계까지의 모양).
        val book = TestEpub.open("2026년 사업 보고", File(System.getProperty("java.io.tmpdir") ?: "."))
        try {
            assertEquals("2026년 사업 보고", book.title)
            val ready = DocViewModel.State.Ready(DocViewModel.Doc.Epub(path, book.title, book))
            assertEquals("2026년 사업 보고", DocTitle.of(ready, path))
        } finally {
            book.close()
        }
    }

    @Test
    fun 전체_경로를_적지_않는다() {
        assertEquals("a.pdf", DocTitle.fileNameOf("/storage/emulated/0/a.pdf"))
        assertFalse('/' in DocTitle.of(DocViewModel.State.Loading(), path))
        // 끝의 빗금은 떼고 자른다(`File` 의 정규화와 같다). 빗금이 없으면 그대로다.
        assertEquals("book.epub", DocTitle.fileNameOf("/storage/emulated/0/book.epub/"))
        assertEquals("book.epub", DocTitle.fileNameOf("book.epub"))
        // 안드로이드에서 역슬래시는 이름의 글자다 — 윈도의 구분자로 읽지 않는다.
        assertEquals("a\\b.pdf", DocTitle.fileNameOf("/storage/emulated/0/a\\b.pdf"))
        assertEquals("", DocTitle.fileNameOf(""))
    }
}
