package io.github.donggi.iroiroviewer.io

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Editor 와의 약속 — 다루는 형식(meta-data)·확장자, 넘겨받은 바이트의 앞머리. */
class EditorAppTest {

    @Test
    fun kindsComeFromTheEditorsManifest() {
        assertEquals(setOf("hwp", "hwpx"), EditorApp.parseKinds("hwp,hwpx"))
        assertEquals(setOf("hwp", "docx"), EditorApp.parseKinds(" HWP , docx ,, exe"))
        assertEquals(setOf("csv", "xls", "pdf"), EditorApp.parseKinds("csv,xls,pdf,rtf"))
        assertEquals(emptySet(), EditorApp.parseKinds(null))
        assertEquals(emptySet(), EditorApp.parseKinds(""))
    }

    @Test
    fun theExtensionPicksTheKind() {
        assertEquals("hwp", EditorApp.kindOfName("/storage/emulated/0/a/보고서.HWP"))
        assertEquals("hwpx", EditorApp.kindOfName("보고서.hwpx"))
        assertNull(EditorApp.kindOfName(".hwp"))
        assertNull(EditorApp.kindOfName("보고서"))
        // ONLYOFFICE 의 형식 — OOXML 의 판, 이전 형식, ODF, 글, PDF.
        assertEquals("docm", EditorApp.kindOfName("보고서.docm"))
        assertEquals("xls", EditorApp.kindOfName("표.XLS"))
        assertEquals("ppsx", EditorApp.kindOfName("발표.ppsx"))
        assertEquals("odp", EditorApp.kindOfName("발표.odp"))
        assertEquals("csv", EditorApp.kindOfName("목록.csv"))
        assertEquals("pdf", EditorApp.kindOfName("명세.pdf"))
        // RTF 는 넘기지 않는다 — ONLYOFFICE 의 변환기가 읽을 때 한글을 버린다.
        assertNull(EditorApp.kindOfName("메모.rtf"))
        assertNull(EditorApp.kindOfName("표.xlsb"))
        assertEquals("hwpx", EditorApp.extensionOf("hwpx"))
        assertEquals("csv", EditorApp.extensionOf("csv"))
        assertNull(EditorApp.extensionOf("exe"))
        assertTrue("xltm" in EditorApp.extensions && "pps" in EditorApp.extensions)
    }

    @Test
    fun bytesMustLookLikeTheirFormat() {
        val cfb = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte())
        val zip = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0)
        val pdf = "%PDF-1.7".toByteArray()
        assertTrue(EditorSaver.looksLike("hwp", cfb))
        assertFalse(EditorSaver.looksLike("hwp", zip))
        assertTrue(EditorSaver.looksLike("hwpx", zip))
        assertFalse(EditorSaver.looksLike("hwpx", cfb))
        assertTrue(EditorSaver.looksLike("pdf", pdf))
        assertFalse(EditorSaver.looksLike("hwpx", ByteArray(0)))
        assertFalse(EditorSaver.looksLike("exe", zip))
    }

    /** ONLYOFFICE 가 쓰는 형식의 앞머리. */
    @Test
    fun officeBytesMustLookLikeTheirFormat() {
        val cfb = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte())
        val zip = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0)
        val pdf = "%PDF-1.7".toByteArray()
        // OOXML — ZIP, 또는 암호를 건 저장이면 CFB(MS-OFFCRYPTO).
        for (k in listOf("docx", "docm", "dotx", "xlsx", "xlsm", "xltx", "pptx", "pptm", "ppsx", "potx")) {
            assertTrue(EditorSaver.looksLike(k, zip), k)
            assertTrue(EditorSaver.looksLike(k, cfb), k)
            assertFalse(EditorSaver.looksLike(k, pdf), k)
        }
        // ODF — 암호가 걸려도 ZIP 이다.
        for (k in listOf("odt", "ods", "odp")) {
            assertTrue(EditorSaver.looksLike(k, zip), k)
            assertFalse(EditorSaver.looksLike(k, cfb), k)
        }
        // 글 — 다른 그릇으로 시작하지만 않으면.
        for (k in listOf("txt", "csv")) {
            assertTrue(EditorSaver.looksLike(k, "이름,점수".toByteArray(Charsets.UTF_8)), k)
            assertTrue(EditorSaver.looksLike(k, byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x74, 0xC7.toByte())), k) // UTF-16LE BOM
            assertFalse(EditorSaver.looksLike(k, zip), k)
            assertFalse(EditorSaver.looksLike(k, cfb), k)
            assertFalse(EditorSaver.looksLike(k, pdf), k)
            assertFalse(EditorSaver.looksLike(k, ByteArray(0)), k)
        }
        // 편집기는 이전 형식을 같은 형식으로 쓰지 않는다(OOXML 로 새 파일) — 그런 바이트는 받지 않는다.
        for (k in listOf("doc", "xls", "ppt", "dot", "xlt", "pot", "pps")) assertFalse(EditorSaver.looksLike(k, cfb), k)
    }

    @Test
    fun `볼 때도 편집기로 여는 것은 한글·오피스·ODF 다`() {
        for (k in listOf("hwp", "hwpx", "docx", "docm", "dotx", "doc", "xlsx", "xls", "ods", "pptx", "ppsx", "ppt", "odt", "odp")) {
            assertTrue(EditorApp.viewsInEditor(k), k)
        }
        // PDF 는 이 앱의 pdfium 뷰어가 쪽 그대로 그린다(편집기는 고칠 수 있는 문서로 바꿔 연다). 글·CSV 는 텍스트 뷰어의 것이다.
        for (k in listOf("pdf", "txt", "csv", "rtf", "epub", "")) assertFalse(EditorApp.viewsInEditor(k), k)
    }
}
