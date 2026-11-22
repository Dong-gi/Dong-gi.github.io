package io.github.donggi.iroiroviewer.docview.pdf.edit

import io.github.donggi.iroiroviewer.docview.pdf.crypt.ByteArraySource
import io.github.donggi.iroiroviewer.docview.pdf.crypt.Corpus
import io.github.donggi.iroiroviewer.docview.pdf.crypt.MemorySink
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfFile
import io.github.donggi.iroiroviewer.docview.pdf.text.PdfTextEngine
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 실세계 말뭉치(`samples-local/corpus/` — 커밋하지 않는다)로 쪽 뽑기·합치기. 말뭉치가 없으면 건너뛴다.
 *
 * 여기서는 **우리 해석기로** 결과를 다시 읽어 쪽 수와 xref 를 보고, 결과와 기대(`out/pages/expect.tsv` — 원본 ID 와 쪽)를 남긴다.
 * 독립 오라클은 그 뒤 `samples-local/corpus/tools/verify_pdf_pages.py` 가 돈다 — qpdf(pikepdf)가 경고 없이 열고 문법 문제가 없고,
 * 엄격한 pypdf 가 경고 없이 읽고, 쪽마다의 글이 원본 오라클(`oracle/<ID>.json` 의 `pypdf_text`)의 그 쪽과 같은가.
 */
class PdfPagesCorpusTest {

    @Test
    fun `말뭉치의 PDF 를 뽑고 합쳐 다시 읽는다`() {
        val dir = Corpus.dir
        assumeTrue("말뭉치가 없다", dir != null)
        val out = File(dir, "out/pages").apply { mkdirs() }
        out.listFiles()?.forEach { it.delete() }
        val rows = StringBuilder()
        val readable = ArrayList<Triple<String, Int, CharArray?>>()
        val refusedUnsupported = ArrayList<String>()
        for (item in Corpus.manifest()) {
            val id = item["id"] as? String ?: continue
            if (!id.startsWith("PDF-")) continue
            val file = File(dir, "$id.pdf")
            if (!file.isFile) continue
            val bytes = file.readBytes()
            // 사용자 암호는 오라클(말뭉치가 문서마다 적은 것 — `oracle/<ID>.json` 의 `password_user`)에서. 세션 암호의 자리다.
            val password = (Corpus.oracle(id)?.get("password_user") as? String)?.takeIf { it.isNotEmpty() }?.toCharArray()
            val inspection = PdfPageCopier.inspect(ByteArraySource(bytes), password)
            val engine = runCatching { PdfTextEngine.open(ByteArraySource(bytes)) }.getOrNull()
            when (inspection) {
                is PdfPageCopier.Inspection.Readable -> when {
                    inspection.lock == PdfPageCopier.Lock.UNSUPPORTED -> {
                        // 인증서 잠금 — 거절한다(쓰지 않는다).
                        val o = PdfPageCopier.write(listOf(PdfPageCopier.Source(id, ByteArraySource(bytes), null)), MemorySink())
                        assertEquals(PdfPageCopier.Outcome.Refused(PdfPageCopier.Refusal.LOCK_UNSUPPORTED), o, id)
                        refusedUnsupported += id
                        continue
                    }
                    inspection.pageCount == null -> {
                        // 암호를 모른다 — 거절한다.
                        val o = PdfPageCopier.write(listOf(PdfPageCopier.Source(id, ByteArraySource(bytes), null)), MemorySink())
                        assertEquals(PdfPageCopier.Outcome.Refused(PdfPageCopier.Refusal.LOCKED), o, id)
                        continue
                    }
                    // 잠기지 않은 문서는 글 꺼내기와 같은 쪽 수(같은 나무 걷기 — 글 꺼내기는 빈 암호로만 연다).
                    inspection.lock != PdfPageCopier.Lock.PASSWORD -> assertEquals(engine?.pageCount, inspection.pageCount, id)
                }
                PdfPageCopier.Inspection.Unreadable -> continue
            }
            val n = inspection.pageCount ?: continue
            if (n == 0) continue
            readable += Triple(id, n, password)
            val pick = listOf(0, n / 2, n - 1).distinct()
            val name = "$id.pick.pdf"
            write(out, name, listOf(PdfPageCopier.Source(id, ByteArraySource(bytes), pick, password = password)), pick.size)
            rows.append(name).append('\t').append(id).append(':').append(pick.joinToString(",")).append('\n')
        }
        // 이웃한 둘씩 통째로 합친다.
        for (k in 0 until readable.size - 1 step 2) {
            val (a, na, pa) = readable[k]
            val (b, nb, pb) = readable[k + 1]
            val name = "$a+$b.merge.pdf"
            write(
                out,
                name,
                listOf(
                    PdfPageCopier.Source(a, ByteArraySource(File(dir, "$a.pdf").readBytes()), null, password = pa),
                    PdfPageCopier.Source(b, ByteArraySource(File(dir, "$b.pdf").readBytes()), null, password = pb),
                ),
                na + nb,
            )
            rows.append(name).append('\t').append("$a:*").append('\t').append("$b:*").append('\n')
        }
        File(out, "expect.tsv").writeText(rows.toString(), Charsets.UTF_8)
        assumeTrue("읽을 수 있는 PDF 가 없다", readable.isNotEmpty())
        // 사용자 암호 문서도 세션 암호로 풀려 들어왔다(말뭉치의 17·18·20·21·22·28 — 오라클에 암호가 적힌 것).
        assertTrue(readable.count { it.third != null } > 0, "암호 문서를 하나도 풀지 못했다")
        // 인증서로 잠긴 PDF-23 은 '이 방식의 잠금' 으로 거절한다.
        if (File(dir, "PDF-23.pdf").isFile) assertTrue("PDF-23" in refusedUnsupported, "$refusedUnsupported")
    }

    private fun write(out: File, name: String, sources: List<PdfPageCopier.Source>, pages: Int) {
        val sink = MemorySink()
        val o = PdfPageCopier.write(sources, sink)
        assertIs<PdfPageCopier.Outcome.Done>(o, name)
        val bytes = sink.toByteArray()
        val f = PdfFile.open(ByteArraySource(bytes))
        assertFalse(f.rebuilt, name)
        assertEquals(pages, assertNotNull(PdfTextEngine.open(ByteArraySource(bytes)), name).pageCount, name)
        File(out, name).writeBytes(bytes)
    }
}
