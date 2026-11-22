package io.github.donggi.iroiroviewer.io

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 쪽 뽑기·합치기가 놓기 직전에 원본을 다시 견주는 판단([PdfPageSaver.unchanged]). 엔진은 쓰는 동안 원본을 여러 번 다시 읽으므로, 그사이
 * 원본이 제자리에서 바뀌면 앞뒤가 다른 판의 객체가 섞인다 — 그런 결과를 놓지 않는다(`CHANGED`).
 */
class PdfPageSaverStampTest {
    private val dir: File = Files.createTempDirectory("pdfstamp").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun file(name: String, text: String) = File(dir, name).apply {
        writeText(text)
        setLastModified(1_700_000_000_000)
    }

    @Test
    fun `쓰는 동안 그대로면 놓는다`() {
        val a = file("a.pdf", "%PDF-1.7 a")
        val b = file("b.pdf", "%PDF-1.7 b")
        val stamps = listOf(a, b).map { FileStamp.of(it) }
        assertTrue(PdfPageSaver.unchanged(stamps, listOf(a, b)))
    }

    @Test
    fun `쓰는 동안 원본이 바뀌거나 지워지면 놓지 않는다`() {
        val a = file("a.pdf", "%PDF-1.7 a")
        val b = file("b.pdf", "%PDF-1.7 b")
        val sources = listOf(a, b)
        val stamps = sources.map { FileStamp.of(it) }
        // 둘째 원본이 제자리에서 고쳐졌다 — 크기가 같아도 수정 시각이 다르다.
        b.writeText("%PDF-1.7 c")
        b.setLastModified(1_700_000_005_000)
        assertFalse(PdfPageSaver.unchanged(stamps, sources))
        // 크기만 바뀐 것도.
        file("b.pdf", "%PDF-1.7 b longer")
        assertFalse(PdfPageSaver.unchanged(stamps, sources))
        // 지워진 것도.
        file("b.pdf", "%PDF-1.7 b")
        assertTrue(PdfPageSaver.unchanged(stamps, sources))
        a.delete()
        assertFalse(PdfPageSaver.unchanged(stamps, sources))
        // 시작할 때 이미 읽을 수 없던 것(표가 없다)은 같다고 보지 않는다.
        assertFalse(PdfPageSaver.unchanged(listOf(null, stamps[1]), sources))
    }
}
