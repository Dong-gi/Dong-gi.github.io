package io.github.donggi.iroiroviewer.text

import io.github.donggi.iroiroviewer.charset.CharsetDetector
import io.github.donggi.iroiroviewer.charset.TextEncoding
import io.github.donggi.iroiroviewer.format.text.PlainHighlighter
import io.github.donggi.iroiroviewer.format.text.RowIndex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 텍스트 뷰어의 '다른 앱으로 열기' 를 어디에 띄우는가([TextOpenWith]). */
class TextOpenWithTest {

    private val kinds = TextViewModel.State.Failed.Kind.entries

    /** 종류마다 답을 적는다. 종류가 늘면 [모든_종류의_답이_표에_있다] 가 깨진다(화면의 `when` 은 컴파일이 멈춘다). */
    private val expected = mapOf(
        // 글이 아니다 — 그림·압축이 확장자만 글일 수 있다. 그것을 여는 앱으로 넘긴다.
        TextViewModel.State.Failed.Kind.BINARY to true,
        // UTF-32 — 읽는 편집기가 있다.
        TextViewModel.State.Failed.Kind.UNSUPPORTED_ENCODING to true,
        // 없거나 못 읽는다 — 받는 앱도 닿지 못한다.
        TextViewModel.State.Failed.Kind.UNREADABLE to false,
    )

    @Test
    fun 모든_종류의_답이_표에_있다() {
        assertEquals(kinds.toSet(), expected.keys)
    }

    @Test
    fun 실패_화면은_이_앱이_못_보여_줄_때만_권한다() {
        for (kind in kinds) assertEquals(expected.getValue(kind), TextOpenWith.onFailure(kind), kind.name)
    }

    @Test
    fun 여는_중에는_앞머리를_읽은_뒤부터_누를_수_있다() {
        // 닿기 전 — 없거나 열리지 않는 파일일 수 있다. 있는데 열리지 않는 파일은 `ExternalOpen` 이 거르지 못한다.
        assertFalse(TextOpenWith.inMenu(TextViewModel.State.Loading()))
        // 앞머리를 읽었다 — 판정·색인을 기다리지 않는다.
        assertTrue(TextOpenWith.inMenu(TextViewModel.State.Loading(reached = true)))
    }

    @Test
    fun 메뉴는_파일에_닿지_못한_때만_흐리다() {
        assertTrue(TextOpenWith.inMenu(TextViewModel.State.Loading(reached = true)))
        // 큰 로그를 색인하는 동안에도 넘길 수 있다 — 기다리지 않게.
        assertTrue(TextOpenWith.inMenu(TextViewModel.State.Indexing(rows = 10, scanned = 1024, total = 1 shl 20)))
        assertTrue(TextOpenWith.inMenu(TextViewModel.State.Ready(doc())))
        // 실패 화면이 권하는 갈래와 메뉴가 누를 수 있는 갈래가 같다 — 한쪽만 고쳐지지 않게 한 함수를 쓴다.
        for (kind in kinds) {
            assertEquals(expected.getValue(kind), TextOpenWith.inMenu(TextViewModel.State.Failed(kind)), kind.name)
        }
        assertFalse(TextOpenWith.inMenu(TextViewModel.State.Failed(TextViewModel.State.Failed.Kind.UNREADABLE)))
    }

    /** 한 줄짜리 문서. 이 시험은 행을 읽지 않는다 — 상태의 모양만 본다. */
    private fun doc(): TextViewModel.Doc {
        val head = "hello".toByteArray()
        return TextViewModel.Doc(
            path = "/storage/emulated/0/README.md",
            name = "README.md",
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
