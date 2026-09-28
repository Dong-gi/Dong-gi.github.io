package io.github.donggi.iroiroviewer.model

import kotlin.test.Test
import kotlin.test.assertEquals

class PathNamesTest {

    @Test
    fun `마지막 조각만 준다`() {
        assertEquals("보고서 (최종).hwpx", PathNames.lastSegment("/storage/emulated/0/Documents/보고서 (최종).hwpx"))
        assertEquals("a.pdf", PathNames.lastSegment("a.pdf"))
        assertEquals("", PathNames.lastSegment(""))
    }

    @Test
    fun `끝의 빗금은 떼고 자른다`() {
        // 폴더로 연 만화의 경로가 빗금으로 끝날 수 있다(`File` 의 정규화와 같은 답).
        assertEquals("만화", PathNames.lastSegment("/storage/emulated/0/만화/"))
        assertEquals("", PathNames.lastSegment("/"))
    }

    @Test
    fun `역슬래시는 이름의 글자다`() {
        // 안드로이드에서는 구분자가 아니다 — 윈도의 JVM 이 읽는 대로 자르면 기기와 다른 답을 시험한다.
        assertEquals("a\\b.pdf", PathNames.lastSegment("/storage/emulated/0/a\\b.pdf"))
    }
}
