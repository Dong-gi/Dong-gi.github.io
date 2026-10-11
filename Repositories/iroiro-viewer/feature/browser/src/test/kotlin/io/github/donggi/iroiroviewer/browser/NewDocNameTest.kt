package io.github.donggi.iroiroviewer.browser

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * + 메뉴의 새 오피스 문서([NewDocName]) — 처음 이름(겹치면 번호), 지운 확장자를 다시 붙이기, 메뉴의 차례(종류 글자 = 확장자 =
 * 저장 큐의 종류).
 */
class NewDocNameTest {

    @Test
    fun `처음 이름은 확장자까지이고 있으면 번호를 붙인다`() {
        val dir = Files.createTempDirectory("newdoc").toFile()
        try {
            assertEquals("새 문서.docx", NewDocName.suggest(dir, "새 문서", "docx"))
            File(dir, "새 문서.docx").writeText("")
            assertEquals("새 문서 (2).docx", NewDocName.suggest(dir, "새 문서", "docx"))
            // 다른 종류는 겹치지 않는다.
            assertEquals("새 문서.hwpx", NewDocName.suggest(dir, "새 문서", "hwpx"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `확장자를 지우거나 바꾸면 그 종류의 것을 다시 붙인다`() {
        assertEquals("보고서.docx", NewDocName.withExtension("보고서", "docx"))
        assertEquals("보고서.docx", NewDocName.withExtension("  보고서.docx ", "docx"))
        assertEquals("보고서.DOCX", NewDocName.withExtension("보고서.DOCX", "docx"))
        assertEquals("보고서.txt.xlsx", NewDocName.withExtension("보고서.txt", "xlsx"))
        assertEquals(".pptx.pptx", NewDocName.withExtension(".pptx", "pptx"))
        assertEquals("", NewDocName.withExtension("   ", "hwpx"))
    }

    @Test
    fun `메뉴는 워드·엑셀·파워포인트·한글 차례다`() {
        // 메뉴는 서식의 차례를 따른다(`NewDocuments.creatable` — 그 가운데 편집기가 고칠 수 있는 것만).
        assertEquals(listOf("docx", "xlsx", "pptx", "hwpx"), io.github.donggi.iroiroviewer.io.BlankDocuments.KINDS)
    }
}
