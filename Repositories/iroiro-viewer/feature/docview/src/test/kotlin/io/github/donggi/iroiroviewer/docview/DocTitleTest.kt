package io.github.donggi.iroiroviewer.docview

import io.github.donggi.iroiroviewer.format.FlowDocument
import io.github.donggi.iroiroviewer.format.FlowKind
import io.github.donggi.iroiroviewer.format.FlowOutline
import io.github.donggi.iroiroviewer.format.FlowPart
import io.github.donggi.iroiroviewer.format.FormatId
import io.github.donggi.iroiroviewer.format.OpenFailure
import io.github.donggi.iroiroviewer.format.ParseWarning
import io.github.donggi.iroiroviewer.format.UnsupportedFeatures
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** 문서 뷰어 위쪽 막대의 제목([DocTitle]). 여는 중·실패 화면에도 무엇을 열고 있는지 적는다. */
class DocTitleTest {

    private val path = "/storage/emulated/0/Documents/보고서 (최종).hwpx"

    @Test
    fun 여는_중과_실패_화면은_파일_이름이다() {
        val states = listOf(
            DocViewModel.State.Loading(),
            DocViewModel.State.Loading(reached = true),
            DocViewModel.State.Failed(OpenFailure.PasswordRequired("암호")),
            DocViewModel.State.Failed(OpenFailure.Unsupported("여는이가 없다")),
            DocViewModel.State.Failed(OpenFailure.Io("파일이 없다"), unreachable = true),
        )
        for (state in states) assertEquals("보고서 (최종).hwpx", DocTitle.of(state, path), state.toString())
    }

    @Test
    fun 열린_문서는_문서가_준_이름_그대로다() {
        // 메타데이터의 제목이 있으면 그것이다 — 파일 이름으로 바꾸지 않는다(14단계까지의 모양).
        val ready = DocViewModel.State.Ready(DocViewModel.Doc.Flow(path, "2026년 사업 보고", FakeFlow))
        assertEquals("2026년 사업 보고", DocTitle.of(ready, path))
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

    /** 부분 하나짜리 흐름 문서. 이 시험은 문서를 읽지 않는다 — 상태의 모양만 본다. */
    private object FakeFlow : FlowDocument {
        override val formatId = FormatId.HWPX
        override val warnings = emptyList<ParseWarning>()
        override val unsupported = UnsupportedFeatures()
        override val title = "2026년 사업 보고"
        override val kind = FlowKind.DOCUMENT
        override val parts = listOf(FlowPart("Contents/section0.xml", "본문"))
        override val outline = emptyList<FlowOutline>()
        override fun partHtml(index: Int): String? = null
        override fun openResource(path: String): InputStream? = null
        override fun mediaTypeOf(path: String): String? = null
        override fun close() = Unit
    }
}
