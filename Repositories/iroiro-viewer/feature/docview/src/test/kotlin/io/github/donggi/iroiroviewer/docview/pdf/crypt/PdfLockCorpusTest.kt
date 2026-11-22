package io.github.donggi.iroiroviewer.docview.pdf.crypt

import io.github.donggi.iroiroviewer.docview.pdf.text.PdfTextEngine
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 실세계 말뭉치(`samples-local/corpus/` — 커밋하지 않는다)로 잠그기·풀기. 말뭉치가 없으면 건너뛴다.
 *
 * 잠그기 — 잠기지 않은 PDF 를 잠가 우리 복호화기로 그 암호로 다시 풀고 쪽 수를 견준다. 풀기 — 암호 PDF(말뭉치·`pdfcrypt/` 표본)를 그
 * 암호로 풀어 잠기지 않은 판을 남긴다. 결과는 `out/pdf-lock/`·`out/pdf-unlock/` 에 남고 `samples-local/corpus/tools/verify_pdf_lock.py` 가
 * qpdf(pikepdf)·엄격한 pypdf 로 읽는다 — 잠근 것은 그 암호로만 열리는가, 푼 것은 잠기지 않았고 글이 원본(그 암호로 연 것)과 같은가.
 */
class PdfLockCorpusTest {

    private val crypt = File(javaClass.classLoader!!.getResource("pdfcrypt/plain.pdf")!!.toURI()).parentFile

    @Test
    fun `말뭉치의 PDF 를 잠갔다 풀고, 암호 PDF 를 푼다`() {
        val dir = Corpus.dir
        assumeTrue("말뭉치가 없다", dir != null)
        // 말뭉치의 잠근 결과는 이 시험만 쓴다 — 지난 결과를 비운다(손으로 지은 표본은 `out/pdf-lock-synthetic/` — `PdfLockerTest`).
        val lockDir = File(dir, "out/pdf-lock").apply { mkdirs() }
        lockDir.listFiles()?.forEach { it.delete() }
        val unlockDir = File(dir, "out/pdf-unlock").apply { mkdirs() }
        unlockDir.listFiles()?.forEach { it.delete() }
        val unlockRows = StringBuilder()
        var locked = 0
        var unlocked = 0
        for (item in Corpus.manifest()) {
            val id = item["id"] as? String ?: continue
            if (!id.startsWith("PDF-")) continue
            val file = File(dir, "$id.pdf")
            if (!file.isFile || file.length() > MAX_BYTES) continue
            val bytes = file.readBytes()
            when (runCatching { PdfDecryptor.inspect(ByteArraySource(bytes)) }.getOrNull()) {
                PdfDecryptor.Inspection.NotEncrypted -> {
                    val password = "말뭉치 $id"
                    val sink = MemorySink()
                    val r = runCatching { PdfDecryptor.lock(ByteArraySource(bytes), password.toCharArray(), sink, Long.MAX_VALUE) }
                    // 우리 해석기가 읽지 못하는 원본(깨진 것)은 잠그지 못해도 된다 — 앱은 그때 '읽지 못했다' 로 말한다.
                    val out = (r.getOrNull() as? PdfDecryptor.LockResult.Ok)?.let { sink.toByteArray() } ?: continue
                    val back = MemorySink()
                    assertEquals(PdfDecryptor.Result.Ok, PdfDecryptor.decrypt(ByteArraySource(out), password.toCharArray(), back, Long.MAX_VALUE), id)
                    val before = runCatching { PdfTextEngine.open(ByteArraySource(bytes))?.pageCount }.getOrNull()
                    val after = PdfTextEngine.open(ByteArraySource(back.toByteArray()))?.pageCount
                    if (before != null) assertEquals(before, after, "$id: 쪽 수")
                    val name = "$id.locked.pdf"
                    File(lockDir, name).writeBytes(out)
                    File(lockDir, "expect.tsv").appendText("$name\t$password\t${file.absolutePath}\n", Charsets.UTF_8)
                    locked++
                }
                PdfDecryptor.Inspection.NeedsPassword, PdfDecryptor.Inspection.OpenWithoutPassword -> {
                    val password = (Corpus.oracle(id)?.get("password_user") as? String).orEmpty()
                    if (unlockOne(bytes, password, unlockDir, "$id.unlocked.pdf", file, unlockRows)) unlocked++
                }
                else -> Unit
            }
        }
        // 손으로 고른 암호 표본 — qpdf·pypdf 가 잠근 것(사용자 암호 `iroiro`, 한글 `비밀번호`, 소유자 암호만).
        for ((name, password) in listOf(
            "qpdf-r6.pdf" to "iroiro", "qpdf-r6-ko.pdf" to "비밀번호", "qpdf-r6-objstm.pdf" to "iroiro",
            "qpdf-r4-aes.pdf" to "iroiro", "pypdf-rc4-40.pdf" to "iroiro", "qpdf-r6-owner-only.pdf" to "",
        )) {
            val f = File(crypt, name)
            if (unlockOne(f.readBytes(), password, unlockDir, "$name.unlocked.pdf", f, unlockRows)) unlocked++
        }
        File(unlockDir, "expect.tsv").writeText(unlockRows.toString(), Charsets.UTF_8)
        assertTrue(locked >= 5, "잠근 말뭉치 $locked 개")
        assertTrue(unlocked >= 6, "푼 것 $unlocked 개")
    }

    private fun unlockOne(bytes: ByteArray, password: String, dir: File, name: String, original: File, rows: StringBuilder): Boolean {
        val sink = MemorySink()
        val r = runCatching { PdfDecryptor.decrypt(ByteArraySource(bytes), password.toCharArray(), sink, Long.MAX_VALUE) }.getOrNull()
        if (r != PdfDecryptor.Result.Ok) return false
        File(dir, name).writeBytes(sink.toByteArray())
        rows.append(name).append('\t').append(original.absolutePath).append('\t').append(password).append('\n')
        return true
    }

    private companion object {
        /** 잠그는 말뭉치의 크기 상한 — 시험 시간(6,766쪽 법령집 같은 큰 것은 뺀다). */
        const val MAX_BYTES = 12L * 1024 * 1024
    }
}
