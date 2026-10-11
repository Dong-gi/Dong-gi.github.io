package io.github.donggi.iroiroviewer.editor

import io.github.donggi.iroiroviewer.editor.EditorContract.Kind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OutputNamesTest {

    @Test
    fun keepsPageNameAndFixesExtension() {
        assertEquals("보고서.hwp", OutputNames.of("보고서.hwp", "원본.hwp", Kind.HWP))
        assertEquals("보고서.hwpx", OutputNames.of("보고서.hwp", "원본.hwp", Kind.HWPX))
        assertEquals("보고서.hwpx", OutputNames.of("보고서", "원본.hwp", Kind.HWPX))
        assertEquals("v1.2 초안.hwp", OutputNames.of("v1.2 초안", "원본.hwp", Kind.HWP))
    }

    @Test
    fun takesOnlyTheLastSegment() {
        assertEquals("b.hwp", OutputNames.of("../../a/b.hwp", "x.hwp", Kind.HWP))
        assertEquals("b.hwp", OutputNames.of("C:\\a\\b.hwp", "x.hwp", Kind.HWP))
    }

    @Test
    fun dropsCharactersFileSystemsRefuse() {
        assertEquals("ab.hwp", OutputNames.of("a\u0000<>:\"|?*b.hwp", "x.hwp", Kind.HWP))
        assertEquals("a b.hwp", OutputNames.of("a\nb".replace("\n", " "), "x.hwp", Kind.HWP))
    }

    @Test
    fun fallsBackWhenNothingUsableIsLeft() {
        assertEquals("원본.hwpx", OutputNames.of("...", "원본.hwp", Kind.HWPX))
        assertEquals("원본.hwp", OutputNames.of(null, "원본.hwp", Kind.HWP))
        assertEquals("원본.hwp", OutputNames.of(".hwp", "원본.hwp", Kind.HWP))
        assertEquals("document.hwp", OutputNames.of("", "", Kind.HWP))
    }

    @Test
    fun boundsTheLength() {
        // 글자로는 120자까지.
        assertEquals(120 + ".hwp".length, OutputNames.of("a".repeat(500) + ".hwp", "x.hwp", Kind.HWP).length)
        // 그리고 UTF-8 바이트로 255까지 — 받은 문서를 이 이름으로 앱 캐시에 먼저 쓴다. 한글 120자는 360바이트라 그 파일을 만들지
        // 못해 저장이 실패했다. 한글은 83자(249바이트) + `.hwp`.
        val long = "가".repeat(500)
        assertEquals("가".repeat(83) + ".hwp", OutputNames.of("$long.hwp", "x.hwp", Kind.HWP))
        // 255바이트 이름의 `.hwp` 를 `.hwpx` 로 저장 — 확장자가 한 바이트 늘어도 넘치지 않는다.
        val full = "가".repeat(83) + "ab"
        assertEquals("가".repeat(83) + "a.hwpx", OutputNames.of("$full.hwp", "x.hwp", Kind.HWPX))
        // 내보내기도 같다.
        assertTrue(OutputNames.exportName("$long.html", "x.hwp")!!.toByteArray(Charsets.UTF_8).size <= 255)
        // 4바이트 글자(서로게이트 쌍)는 반으로 가르지 않는다 — 120자(UTF-16)면 60개, 앞에 한 글자가 더 있으면 59개(파일 쪽 `EditorSlots.cleanName` 과
        // 같은 자리).
        assertEquals("😀".repeat(60) + ".docx", OutputNames.of("😀".repeat(100), "x.docx", Kind.DOCX))
        assertEquals("a" + "😀".repeat(59) + ".docx", OutputNames.of("a" + "😀".repeat(100), "x.docx", Kind.DOCX))
    }

    @Test
    fun exportsOnlyDocumentFormats() {
        assertEquals("보고서.html", OutputNames.exportName("보고서.html", "원본.hwp"))
        assertEquals("보고서.doc", OutputNames.exportName("보고서.DOC", "원본.hwp"))
        assertEquals("a.htm", OutputNames.exportName("../x/a.htm", "원본.hwp"))
        assertEquals(null, OutputNames.exportName("보고서.hwp", "원본.hwp"))
        assertEquals(null, OutputNames.exportName("보고서.hwpx", "원본.hwp"))
        assertEquals(null, OutputNames.exportName("보고서.js", "원본.hwp"))
        assertEquals(null, OutputNames.exportName("보고서.svg", "원본.docx"))
        assertEquals(null, OutputNames.exportName("보고서.apk", "원본.docx"))
        assertEquals(null, OutputNames.exportName("보고서", "원본.hwp"))
        assertEquals(null, OutputNames.exportName(null, "원본.hwp"))
        assertEquals("text/html", OutputNames.exportMime("a.HTML"))
        assertEquals("application/msword", OutputNames.exportMime("a.doc"))
    }

    @Test
    fun exportsWhatOnlyofficeWrites() {
        assertEquals("보고서.pdf", OutputNames.exportName("보고서.pdf", "원본.docx"))
        assertEquals("보고서.odt", OutputNames.exportName("보고서.odt", "원본.docx"))
        assertEquals("표.csv", OutputNames.exportName("표.CSV", "원본.xlsx"))
        assertEquals("발표.odp", OutputNames.exportName("발표.odp", "원본.pptx"))
        assertEquals("application/pdf", OutputNames.exportMime("a.pdf"))
        assertEquals("text/csv", OutputNames.exportMime("a.csv"))
        assertEquals("application/vnd.oasis.opendocument.text", OutputNames.exportMime("a.odt"))
        assertEquals("application/octet-stream", OutputNames.exportMime("a.exe"))
    }

    @Test
    fun exportFallsBackToTheDocumentName() {
        assertEquals("원본.html", OutputNames.exportName(".html", "원본.hwp"))
        assertEquals("원본.doc", OutputNames.exportName(" ..doc", "원본.hwpx"))
        assertEquals("ab.html", OutputNames.exportName("a<>b.html", "x.hwp"))
    }

    @Test
    fun printTitleDropsTheDocumentExtension() {
        assertEquals("보고서", OutputNames.title("보고서", "원본.hwp"))
        assertEquals("보고서", OutputNames.title("보고서.hwpx", "원본.hwp"))
        assertEquals("원본", OutputNames.title("", "원본.hwp"))
        assertEquals("원본", OutputNames.title(null, "원본.hml"))
        assertEquals("document", OutputNames.title("...", ""))
    }
}
