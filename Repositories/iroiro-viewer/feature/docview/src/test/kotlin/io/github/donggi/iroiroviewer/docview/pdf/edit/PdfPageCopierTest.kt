package io.github.donggi.iroiroviewer.docview.pdf.edit

import io.github.donggi.iroiroviewer.docview.pdf.crypt.ByteArraySource
import io.github.donggi.iroiroviewer.docview.pdf.crypt.Corpus
import io.github.donggi.iroiroviewer.docview.pdf.crypt.MemorySink
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfArray
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfFile
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfRef
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfSink
import io.github.donggi.iroiroviewer.docview.pdf.text.MiniPdf
import io.github.donggi.iroiroviewer.docview.pdf.text.PdfObjects
import io.github.donggi.iroiroviewer.docview.pdf.text.PdfTextEngine
import io.github.donggi.iroiroviewer.docview.pdf.text.PdfTextLayout
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.zip.Deflater
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 쪽 뽑기·합치기([PdfPageCopier]). 표본은 손으로 지은 작은 PDF 와 암호 표본(`pdfcrypt/` — pypdf 6.17·qpdf 가 잠갔다). 결과는 **우리 해석기**
 * (`PdfFile`·`PdfTextEngine`)로 다시 읽어 구조를 보고, 독립 오라클(qpdf·pypdf)은 `PdfPagesCorpusTest` 가 써 둔 파일을
 * `samples-local/corpus/tools/verify_pdf_pages.py` 가 읽는다.
 */
class PdfPageCopierTest {

    private fun b(s: String) = s.toByteArray(Charsets.ISO_8859_1)

    private fun flate(s: String): ByteArray {
        val d = Deflater()
        d.setInput(b(s))
        d.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        d.end()
        return out.toByteArray()
    }

    private fun content(n: Int) = MiniPdf.stream("/Filter /FlateDecode", flate("BT /F1 12 Tf 20 20 Td (Page $n) Tj ET"))

    /**
     * 다섯 쪽 — 중간 마디 둘이 자원·미디어 상자·회전을 물려준다(1~3쪽은 90도·300×400, 4~5쪽은 500×500). 5쪽에 링크 넷(1쪽으로,
     * 4쪽으로 가는 GoTo, 이름 목적지 → 2쪽, 바깥 주소). 목차 셋(One→1쪽 · 그 아래 Five→5쪽 · Four→4쪽).
     */
    private fun fivePages(): ByteArray = MiniPdf.build(
        listOf(
            /* 1 */ b("<< /Type /Catalog /Pages 2 0 R /Outlines 18 0 R /Names << /Dests 22 0 R >> >>"),
            /* 2 */ b("<< /Type /Pages /Kids [3 0 R 7 0 R] /Count 5 /Resources << /Font << /F1 15 0 R >> >> /MediaBox [0 0 300 400] >>"),
            /* 3 */ b("<< /Type /Pages /Parent 2 0 R /Kids [4 0 R 5 0 R 6 0 R] /Count 3 /Rotate 90 >>"),
            /* 4 */ b("<< /Type /Page /Parent 3 0 R /Contents 10 0 R >>"),
            /* 5 */ b("<< /Type /Page /Parent 3 0 R /Contents 11 0 R >>"),
            /* 6 */ b("<< /Type /Page /Parent 3 0 R /Contents 12 0 R >>"),
            /* 7 */ b("<< /Type /Pages /Parent 2 0 R /Kids [8 0 R 9 0 R] /Count 2 /MediaBox [0 0 500 500] >>"),
            /* 8 */ b("<< /Type /Page /Parent 7 0 R /Contents 13 0 R >>"),
            /* 9 */ b("<< /Type /Page /Parent 7 0 R /Contents 14 0 R /Annots [16 0 R 17 0 R 23 0 R 24 0 R] >>"),
            /* 10 */ content(1),
            /* 11 */ content(2),
            /* 12 */ content(3),
            /* 13 */ content(4),
            /* 14 */ content(5),
            /* 15 */ b("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"),
            /* 16 */ b("<< /Type /Annot /Subtype /Link /Rect [10 10 50 50] /P 9 0 R /Dest [4 0 R /Fit] >>"),
            /* 17 */ b("<< /Type /Annot /Subtype /Link /Rect [10 60 50 100] /A << /S /GoTo /D [8 0 R /Fit] >> >>"),
            /* 18 */ b("<< /Type /Outlines /First 19 0 R /Last 20 0 R /Count 2 >>"),
            /* 19 */ b("<< /Title (One) /Parent 18 0 R /Next 20 0 R /First 21 0 R /Last 21 0 R /Count 1 /Dest [4 0 R /Fit] >>"),
            /* 20 */ b("<< /Title (Four) /Parent 18 0 R /Prev 19 0 R /Dest [8 0 R /Fit] >>"),
            /* 21 */ b("<< /Title (Five) /Parent 19 0 R /Dest [9 0 R /XYZ 0 0 0] >>"),
            /* 22 */ b("<< /Names [(named) [5 0 R /Fit]] >>"),
            /* 23 */ b("<< /Type /Annot /Subtype /Link /Rect [10 110 50 150] /Dest (named) >>"),
            /* 24 */ b("<< /Type /Annot /Subtype /Link /Rect [10 160 50 200] /A << /S /URI /URI (https://example.invalid/) >> >>"),
        ),
    )

    private fun copy(vararg sources: PdfPageCopier.Source, limits: PdfPageCopier.Limits = PdfPageCopier.Limits()): ByteArray {
        val sink = MemorySink()
        val o = PdfPageCopier.write(sources.toList(), sink, limits)
        assertIs<PdfPageCopier.Outcome.Done>(o)
        return sink.toByteArray().also {
            checkXref(it)
            keepForOracle(it)
        }
    }

    /**
     * 말뭉치가 있는 저장소면 결과를 `out/pages-synthetic/` 에 남긴다 — `verify_pdf_pages.py --synthetic` 가 qpdf·엄격한 pypdf 로
     * 경고 없이 읽히는지 본다(손으로 지은 표본이라 글 대조는 우리 시험이 한다).
     */
    private fun keepForOracle(pdf: ByteArray) {
        val dir = Corpus.dir ?: return
        val out = File(dir, "out/pages-synthetic").apply { mkdirs() }
        File(out, "${javaClass.simpleName}-${System.nanoTime()}.pdf").writeBytes(pdf)
    }

    private fun source(pdf: ByteArray, pages: List<Int>?, title: String = "doc", expected: Int? = null) =
        PdfPageCopier.Source(title, ByteArraySource(pdf), pages, expected)

    /** 결과의 xref 가 그대로 맞는다 — 다시 세우지 않고 열리고, 번호마다 그 자리에 그 번호의 객체가 있다. */
    private fun checkXref(pdf: ByteArray) {
        val f = PdfFile.open(ByteArraySource(pdf))
        assertFalse(f.rebuilt, "xref 를 다시 세웠다")
        for ((num, e) in f.entries) {
            val at = (e as? PdfFile.InFile)?.offset ?: continue
            assertEquals(num, f.readAt(at)?.num, "번호 $num 의 자리")
        }
        assertNull(f.trailer["Encrypt"])
    }

    private fun texts(pdf: ByteArray): List<String> {
        val e = assertNotNull(PdfTextEngine.open(ByteArraySource(pdf)))
        return (0 until e.pageCount).map { i -> e.page(i).let { PdfTextLayout.text(it, 0, it.glyphs.size).orEmpty() } }
    }

    /** xref 의 번호 수(0 번 포함) — 결과는 1부터 빈틈없이 쓰므로 트레일러의 `/Size` 와 같다. */
    private fun size(pdf: ByteArray): Int = PdfFile.open(ByteArraySource(pdf)).entries.size

    @Test
    fun `고른 쪽만 옮기고 물려받은 넷을 쪽에 펴 넣는다`() {
        val out = copy(source(fivePages(), listOf(0, 4)))
        assertEquals(listOf("Page 1", "Page 5"), texts(out))
        val e = assertNotNull(PdfTextEngine.open(ByteArraySource(out)))
        // 1쪽은 90도로 돈 300×400(보이는 크기 400×300), 5쪽은 500×500 — 둘 다 옛 중간 마디에서 물려받은 것이다.
        assertEquals(400f to 300f, e.page(0).let { it.width to it.height })
        assertEquals(500f to 500f, e.page(1).let { it.width to it.height })
        // 객체 — 뿌리·나무·쪽 둘·내용 둘·글꼴·링크 둘(1쪽으로·바깥 주소)·목차 셋. 빠진 쪽(2~4)과 그 내용·옛 나무가 딸려 오지 않았다.
        assertEquals(13, size(out))
    }

    @Test
    fun `빠진 쪽을 가리키는 링크는 지우고 남은 쪽의 것은 새 쪽으로 다시 적는다`() {
        val out = copy(source(fivePages(), listOf(0, 4)))
        val e = assertNotNull(PdfTextEngine.open(ByteArraySource(out)))
        // 5쪽(새 2쪽)의 링크 — 1쪽으로 가는 것만 남는다(4쪽 GoTo·2쪽 이름 목적지는 빠진 쪽). 바깥 주소는 남지만 쪽 링크가 아니다.
        assertEquals(listOf(0), e.annotationLinks(1, withActions = true).map { it.targetPage })
        val f = PdfFile.open(ByteArraySource(out))
        val objs = PdfObjects(f, null, {})
        val pages = PdfTextEngineAccess.pageDicts(objs, f)
        assertEquals(2, objs.array(pages[1]["Annots"])!!.items.size)
        // 주석의 `/P` 는 새 쪽을 가리킨다.
        val link = objs.dict(objs.array(pages[1]["Annots"])!!.items[0])!!
        assertEquals(PdfTextEngineAccess.pageRefs(objs, f)[1], link["P"])
    }

    @Test
    fun `이름 목적지는 남은 쪽이면 쪽을 적은 배열로 바꾼다`() {
        val out = copy(source(fivePages(), listOf(1, 4)))
        val e = assertNotNull(PdfTextEngine.open(ByteArraySource(out)))
        // 이름 나무는 옮기지 않는다 — 이름 `(named)` 가 2쪽(새 0쪽)을 적은 배열이 되었다.
        assertEquals(listOf(0), e.annotationLinks(1).map { it.targetPage })
    }

    @Test
    fun `목차는 남은 쪽의 항목만 남고 빠진 쪽 항목의 아이는 위로 올라간다`() {
        val one = copy(source(fivePages(), listOf(0, 4)))
        assertEquals(
            listOf(Triple("One", 0, 0), Triple("Five", 1, 1)),
            assertNotNull(PdfTextEngine.open(ByteArraySource(one))).outline().entries.map { Triple(it.title, it.page, it.depth) },
        )
        // 1쪽을 빼면 One 이 빠지고 그 아이 Five 가 맨 위로.
        val two = copy(source(fivePages(), listOf(3, 4)))
        assertEquals(
            listOf(Triple("Five", 1, 0), Triple("Four", 0, 0)),
            assertNotNull(PdfTextEngine.open(ByteArraySource(two))).outline().entries.map { Triple(it.title, it.page, it.depth) },
        )
        // 목차를 가리키는 항목이 하나도 없으면 목차가 없다.
        val none = copy(source(fivePages(), listOf(1)))
        assertTrue(assertNotNull(PdfTextEngine.open(ByteArraySource(none))).outline().isEmpty)
    }

    @Test
    fun `합치면 번호가 겹치지 않고 링크·목차가 제 문서의 쪽을 가리킨다`() {
        val out = copy(source(fivePages(), null, "앞"), source(fivePages(), null, "뒤"))
        assertEquals((1..5).map { "Page $it" } + (1..5).map { "Page $it" }, texts(out))
        val e = assertNotNull(PdfTextEngine.open(ByteArraySource(out)))
        // 두 번째 문서의 5쪽(새 9쪽)의 링크들 — 1쪽·4쪽·이름(2쪽)이 **두 번째 문서의** 0+5·3+5·1+5 로.
        assertEquals(setOf(5, 8, 6), e.annotationLinks(9, withActions = true).map { it.targetPage }.toSet())
        assertEquals(setOf(0, 3, 1), e.annotationLinks(4, withActions = true).map { it.targetPage }.toSet())
        // 목차 — 파일마다 이름 항목(펼침), 그 아래 원래 목차.
        assertEquals(
            listOf(
                Triple("앞", 0, 0), Triple("One", 0, 1), Triple("Five", 4, 2), Triple("Four", 3, 1),
                Triple("뒤", 5, 0), Triple("One", 5, 1), Triple("Five", 9, 2), Triple("Four", 8, 1),
            ),
            e.outline().entries.map { Triple(it.title, it.page, it.depth) },
        )
    }

    @Test
    fun `객체 스트림과 xref 스트림을 쓰는 PDF 15 도 읽는다`() {
        // 1~4(뿌리·나무·쪽·글꼴)는 객체 스트림(6번) 안에, 내용(5번)은 파일에.
        val members = listOf(
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
        )
        val head = StringBuilder()
        val body = StringBuilder()
        members.forEachIndexed { i, m ->
            head.append("${i + 1} ${body.length} ")
            body.append(m).append('\n')
        }
        val objStm = MiniPdf.stream("/Type /ObjStm /N 4 /First ${head.length}", b(head.toString() + body))
        val pdf = MiniPdf.buildWithXrefStream(
            listOf(b("null"), b("null"), b("null"), b("null"), content(7), objStm),
            mapOf(1 to (6 to 0), 2 to (6 to 1), 3 to (6 to 2), 4 to (6 to 3)),
        )
        assertEquals(listOf("Page 7"), texts(pdf))
        val out = copy(source(pdf, listOf(0)))
        assertEquals(listOf("Page 7"), texts(out))
        // 객체 스트림 자체는 옮기지 않는다 — 뿌리·나무·쪽·내용·글꼴 다섯.
        assertEquals(6, size(out))
    }

    @Test
    fun `압축된 스트림은 압축된 채로 옮긴다`() {
        val src = fivePages()
        val out = copy(source(src, listOf(2)))
        val raw = flate("BT /F1 12 Tf 20 20 Td (Page 3) Tj ET")
        assertTrue(indexOf(out, raw) >= 0, "압축된 바이트가 그대로 있어야 한다")
        assertTrue(String(out, Charsets.ISO_8859_1).contains("/FlateDecode"))
    }

    @Test
    fun `길이가 간접 참조인 스트림은 그 객체를 옮기지 않고 실제 길이를 적는다`() {
        val data = b("BT /F1 12 Tf 20 20 Td (Hi) Tj ET")
        val pdf = MiniPdf.build(
            listOf(
                b("<< /Type /Catalog /Pages 2 0 R >>"),
                b("<< /Type /Pages /Kids [3 0 R] /Count 1 >>"),
                b("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] /Resources << /Font << /F1 6 0 R >> >> /Contents 4 0 R >>"),
                b("<< /Length 5 0 R >>\nstream\n") + data + b("\nendstream"),
                b("${data.size}"),
                b("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"),
            ),
        )
        val out = copy(source(pdf, null))
        assertEquals(listOf("Hi"), texts(out))
        assertEquals(6, size(out)) // 뿌리·나무·쪽·내용·글꼴 — 길이 객체는 없다
        assertTrue(String(out, Charsets.ISO_8859_1).contains("/Length ${data.size}"))
    }

    @Test
    fun `소유자 암호만 걸린 문서는 풀어서 평문으로 쓴다`() {
        for (name in listOf("nav-owner-aes128.pdf", "nav-owner-rc4.pdf", "qpdf-r6-owner-only.pdf")) {
            val pdf = resource(name)
            assertEquals(
                PdfPageCopier.Inspection.Readable(PdfTextEngine.open(ByteArraySource(pdf))!!.pageCount, PdfPageCopier.Lock.OWNER_ONLY),
                PdfPageCopier.inspect(ByteArraySource(pdf)),
                name,
            )
            val out = copy(source(pdf, null))
            assertFalse(String(out, Charsets.ISO_8859_1).contains("/Encrypt"), name)
            assertEquals(texts(pdf), texts(out), name)
        }
        // 목차 제목(암호문 문자열이었다)이 평문으로 남는다.
        val nav = copy(source(resource("nav-owner-aes128.pdf"), null))
        assertEquals(
            listOf("1장 시작", "1.1 절", "Named"),
            assertNotNull(PdfTextEngine.open(ByteArraySource(nav))).outline().entries.map { it.title },
        )
    }

    /** pypdf 6.17·qpdf 가 사용자 암호 `iroiro` 로 잠근 표본(`PdfDecryptorTest` 와 한 벌) — 평문 원본은 `plain.pdf`. */
    private val userLocked = listOf(
        "pypdf-rc4-40.pdf", "pypdf-rc4-128.pdf", "pypdf-aes-128.pdf", "pypdf-aes-256-r5.pdf", "pypdf-aes-256.pdf",
        "qpdf-r2.pdf", "qpdf-r3.pdf", "qpdf-r4-aes.pdf", "qpdf-r4-rc4.pdf", "qpdf-r6.pdf", "qpdf-r6-objstm.pdf",
        "qpdf-r4-objstm.pdf", "qpdf-r4-cleartext-meta.pdf", "qpdf-r4-linear.pdf", "qpdf-r6-linear-objstm.pdf",
    )

    @Test
    fun `사용자 암호 문서는 세션 암호로 풀어 잠기지 않은 PDF 로 쓴다`() {
        val plain = texts(resource("plain.pdf"))
        for (name in userLocked) {
            val pdf = resource(name)
            val password = "iroiro".toCharArray()
            assertEquals(
                PdfPageCopier.Inspection.Readable(plain.size, PdfPageCopier.Lock.PASSWORD),
                PdfPageCopier.inspect(ByteArraySource(pdf), password),
                name,
            )
            val out = copy(PdfPageCopier.Source(name, ByteArraySource(pdf), null, password = password))
            assertFalse(String(out, Charsets.ISO_8859_1).contains("/Encrypt"), name)
            assertEquals(plain, texts(out), name)
            // 부르는 쪽의 배열은 덮지 않는다(주인이 덮는다).
            assertEquals("iroiro", String(password), name)
        }
    }

    @Test
    fun `암호가 없거나 틀리면 거절하고 한 바이트도 쓰지 않는다`() {
        for (name in listOf("pypdf-aes-128.pdf", "pypdf-rc4-40.pdf", "qpdf-r6.pdf", "qpdf-r4-objstm.pdf")) {
            val pdf = resource(name)
            for (password in listOf(null, "wrong".toCharArray())) {
                assertEquals(
                    PdfPageCopier.Inspection.Readable(null, PdfPageCopier.Lock.PASSWORD),
                    PdfPageCopier.inspect(ByteArraySource(pdf), password),
                    name,
                )
                val sink = MemorySink()
                assertEquals(
                    PdfPageCopier.Outcome.Refused(PdfPageCopier.Refusal.LOCKED),
                    PdfPageCopier.write(listOf(PdfPageCopier.Source(name, ByteArraySource(pdf), null, password = password)), sink),
                    name,
                )
                // 머리 한 줄도 쓰지 않았다 — 잠긴 문서의 바이트가 새지 않는다.
                assertEquals(0L, sink.position, name)
            }
        }
    }

    @Test
    fun `암호로는 열 수 없는 방식은 따로 거절한다`() {
        // 공개 키 처리기 — 열쇠가 암호가 아니다.
        val pdf = MiniPdf.build(
            listOf(
                b("<< /Type /Catalog /Pages 2 0 R >>"),
                b("<< /Type /Pages /Kids [3 0 R] /Count 1 >>"),
                b("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 10 10] >>"),
                b("<< /Filter /Adobe.PubSec /V 4 /R 4 >>"),
            ),
            trailerExtra = "/Encrypt 4 0 R /ID [<00> <00>]",
        )
        assertEquals(PdfPageCopier.Inspection.Readable(null, PdfPageCopier.Lock.UNSUPPORTED), PdfPageCopier.inspect(ByteArraySource(pdf)))
        assertEquals(
            PdfPageCopier.Outcome.Refused(PdfPageCopier.Refusal.LOCK_UNSUPPORTED),
            PdfPageCopier.write(listOf(source(pdf, null)), MemorySink()),
        )
    }

    @Test
    fun `PDF 가 아니면 읽지 못한다`() {
        assertEquals(PdfPageCopier.Inspection.Unreadable, PdfPageCopier.inspect(ByteArraySource(b("hello"))))
        assertEquals(
            PdfPageCopier.Outcome.Refused(PdfPageCopier.Refusal.UNREADABLE),
            PdfPageCopier.write(listOf(source(b("not a pdf at all"), null)), MemorySink()),
        )
    }

    @Test
    fun `화면이 센 쪽 수와 다르면 거절하고 없는 쪽을 고르면 바뀐 것으로 본다`() {
        assertEquals(
            PdfPageCopier.Outcome.Refused(PdfPageCopier.Refusal.UNREADABLE),
            PdfPageCopier.write(listOf(source(fivePages(), listOf(0), expected = 6)), MemorySink()),
        )
        assertEquals(
            PdfPageCopier.Outcome.Refused(PdfPageCopier.Refusal.CHANGED),
            PdfPageCopier.write(listOf(source(fivePages(), listOf(5))), MemorySink()),
        )
    }

    @Test
    fun `고리·제 자신을 품은 나무·나무 밖의 쪽에서 멎지 않는다`() {
        val pdf = MiniPdf.build(
            listOf(
                /* 1 */ b("<< /Type /Catalog /Pages 2 0 R >>"),
                // 나무가 제 자신을 아이로 품었다.
                /* 2 */ b("<< /Type /Pages /Kids [3 0 R 2 0 R] /Count 1 >>"),
                /* 3 */ b("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 100 100] /Resources << /X 4 0 R /Y 1 0 R >> /Annots [6 0 R] >>"),
                // 서로를 가리키는 두 객체.
                /* 4 */ b("<< /Next 5 0 R /Me 4 0 R >>"),
                /* 5 */ b("<< /Next 4 0 R >>"),
                // 나무 밖의 쪽을 가리키는 주석 — 그 쪽과 그 나무를 끌어오지 않는다.
                /* 6 */ b("<< /Type /Annot /Subtype /Text /Rect [0 0 10 10] /Ref 7 0 R >>"),
                /* 7 */ b("<< /Type /Page /Parent 8 0 R /Contents 9 0 R >>"),
                /* 8 */ b("<< /Type /Pages /Kids [7 0 R] /Count 1 >>"),
                /* 9 */ MiniPdf.stream("", b("BT ET")),
            ),
        )
        val out = copy(source(pdf, null))
        // 뿌리·나무·쪽·4·5·주석·고아 쪽 자리(null) — 번호 1~7. 고아의 나무·내용과 뿌리(`/Y`)는 없다.
        assertEquals(8, size(out))
        assertFalse(String(out, Charsets.ISO_8859_1).contains("BT ET"))
    }

    @Test
    fun `상한을 넘으면 너무 크다고 거절한다`() {
        val limits = PdfPageCopier.Limits(maxObjects = 3)
        assertEquals(
            PdfPageCopier.Outcome.Refused(PdfPageCopier.Refusal.TOO_BIG),
            PdfPageCopier.write(listOf(source(fivePages(), null)), MemorySink(), limits),
        )
        assertEquals(
            PdfPageCopier.Outcome.Refused(PdfPageCopier.Refusal.TOO_BIG),
            PdfPageCopier.write(listOf(source(fivePages(), null)), MemorySink(), PdfPageCopier.Limits(maxPages = 4)),
        )
        assertEquals(
            PdfPageCopier.Outcome.Refused(PdfPageCopier.Refusal.TOO_BIG),
            PdfPageCopier.write(listOf(source(fivePages(), null)), MemorySink(), PdfPageCopier.Limits(maxOutputBytes = 500)),
        )
    }

    @Test
    fun `취소는 그대로 올라가고 그 밖의 실패는 거절로 옮긴다`() {
        var calls = 0
        assertFailsWith<CancellationException> {
            PdfPageCopier.write(
                listOf(source(fivePages(), null)),
                MemorySink(),
                checkCancelled = { if (++calls > 5) throw CancellationException() },
            )
        }
        // 쓰는 곳의 입출력 실패(공간 부족 등)도 그대로 올라간다 — 큐가 그 까닭을 말한다.
        val full = object : PdfSink {
            override val position: Long get() = 0
            override fun write(b: ByteArray, off: Int, len: Int) = throw IOException("공간 없음")
            override fun patch(at: Long, b: ByteArray) = Unit
        }
        assertFailsWith<IOException> { PdfPageCopier.write(listOf(source(fivePages(), null)), full) }
        // 해석 중의 뜻밖의 실패는 앱을 죽이지 않고 '읽지 못함'.
        assertEquals(
            PdfPageCopier.Outcome.Refused(PdfPageCopier.Refusal.UNREADABLE),
            PdfPageCopier.write(listOf(source(fivePages(), null)), MemorySink(), checkCancelled = { throw IllegalArgumentException() }),
        )
    }

    // ---- 독립 검토(2026-11-22)가 낸 적대 표본 — 고침을 되돌리면 그 시험만 깨진다 ----------------------------------------

    @Test(timeout = 20_000)
    fun `직접 사전 마디가 같은 간접 Kids 를 겹겹이 가리켜도 방문 상한에서 끝난다`() {
        // 참조 번호만 막으면 깊이 64 까지 2^64 번 걷는다(340 바이트).
        val pdf = MiniPdf.build(
            listOf(
                b("<< /Type /Catalog /Pages 2 0 R >>"),
                b("<< /Type /Pages /Kids [3 0 R 4 0 R] /Count 1 /MediaBox [0 0 10 10] >>"),
                b("<< /Type /Page /Parent 2 0 R >>"),
                b("<< /Type /Pages /Parent 2 0 R /Kids 5 0 R >>"),
                b("[<< /Type /Pages /Kids 5 0 R >> << /Type /Pages /Kids 5 0 R >>]"),
            ),
        )
        var calls = 0
        val i = PdfPageCopier.inspect(ByteArraySource(pdf)) { calls++ }
        assertEquals(PdfPageCopier.Inspection.Readable(1, PdfPageCopier.Lock.NONE), i)
        // 취소 확인이 방문 수로 돈다(쪽이 늘지 않는 갈래에서도).
        assertTrue(calls > 100, "취소 확인 $calls 번")
        // 글 꺼내기의 나무 걷기도 같은 상한을 지난다.
        assertEquals(1, assertNotNull(PdfTextEngine.open(ByteArraySource(pdf))).pageCount)
    }

    /** 객체 스트림 [k] 개에 번갈아 든 객체를 차례로 가리킨다 — 수로 묶은 캐시(64)는 65개에서 참조마다 스트림을 통째로 다시 풀었다. */
    private fun objStmPdf(k: Int, perStream: Int): ByteArray {
        val files = ArrayList<ByteArray>()
        files += b("<< /Type /Catalog /Pages 2 0 R >>")
        files += b("<< /Type /Pages /Kids [3 0 R] /Count 1 >>")
        val refs = StringBuilder()
        for (i in 0 until perStream) for (s in 0 until k) refs.append(10_000 + i * k + s).append(" 0 R ")
        files += b("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 10 10] /Resources << /X [$refs] >> >>")
        val map = HashMap<Int, Pair<Int, Int>>()
        for (s in 0 until k) {
            val head = StringBuilder()
            val body = StringBuilder()
            for (i in 0 until perStream) {
                val num = 10_000 + i * k + s
                head.append("$num ${body.length} ")
                body.append("<< /A $i >>\n")
                map[num] = (4 + s) to i
            }
            files += MiniPdf.stream("/Type /ObjStm /N $perStream /First ${head.length}", b(head.toString() + body))
        }
        return MiniPdf.buildWithXrefStream(files, map)
    }

    @Test(timeout = 30_000)
    fun `객체 스트림을 번갈아 가리켜도 제곱 시간이 되지 않는다`() {
        // 옛 판은 이 크기(65 × 900)에 199초였다.
        val pdf = objStmPdf(65, 900)
        val t0 = System.nanoTime()
        val o = PdfPageCopier.write(listOf(source(pdf, null)), CountingSink())
        assertEquals(PdfPageCopier.Outcome.Done(1), o)
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 15_000, "너무 느리다")
    }

    @Test(timeout = 60_000)
    fun `객체 스트림이 메모리를 다 먹지 않는다`() {
        // 백만 칸(모두 같은 자리의 `[]`)짜리 객체 스트림 열여섯 — 값을 미리 다 읽던 판은 OOM 이었다. 지금은 번호표만 들고 무게로 내보낸다.
        val streams = 16
        val members = 1_000_000
        val files = ArrayList<ByteArray>()
        files += b("<< /Type /Catalog /Pages 2 0 R >>")
        files += b("<< /Type /Pages /Kids [3 0 R] /Count 1 >>")
        val refs = (0 until streams).joinToString(" ") { "${10_000 + it} 0 R" }
        files += b("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 10 10] /Resources << /X [$refs] >> >>")
        val map = HashMap<Int, Pair<Int, Int>>()
        for (s in 0 until streams) {
            val head = StringBuilder()
            repeat(members) { head.append("${10_000 + s} 0 ") }
            files += MiniPdf.stream("/Type /ObjStm /Filter /FlateDecode /N $members /First ${head.length}", flate(head.toString() + "[]"))
            map[10_000 + s] = (4 + s) to 0
        }
        val pdf = MiniPdf.buildWithXrefStream(files, map)
        // 예산은 1 GiB 로 준다(기본값은 힙의 1/4 — 시험 JVM 에서는 그보다 작다). 그 안이라 끝까지 쓴다 — 거절로 빠져나가면 이 시험이
        // 무엇도 재지 않는다(독립 검토 2차). 재는 것은 메모리다.
        val o = PdfPageCopier.write(listOf(source(pdf, null)), CountingSink(), PdfPageCopier.Limits(readBudget = 1L shl 30))
        assertEquals(PdfPageCopier.Outcome.Done(1), o)
    }

    /** 객체 스트림 하나(압축 약 16 KB) 안의 8 MiB 값 한 자리를 칸 [members] 개가 가리킨다(독립 검토 2차의 재현). */
    private fun bigValuePdf(members: Int, asPages: Boolean): ByteArray {
        val files = ArrayList<ByteArray>()
        files += b("<< /Type /Catalog /Pages 2 0 R >>")
        val refs = (0 until members).joinToString(" ") { "${10_000 + it} 0 R" }
        if (asPages) {
            files += b("<< /Type /Pages /Kids [$refs] /Count $members /MediaBox [0 0 10 10] >>")
            files += b("null")
        } else {
            files += b("<< /Type /Pages /Kids [3 0 R] /Count 1 >>")
            files += b("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 10 10] /Resources << /X [$refs] >> >>")
        }
        val head = StringBuilder()
        repeat(members) { head.append("${10_000 + it} 0 ") }
        val big = "a".repeat(8 shl 20)
        val value = if (asPages) "<< /Type /Page /Junk ($big) >>" else "($big)"
        files += MiniPdf.stream("/Type /ObjStm /Filter /FlateDecode /N $members /First ${head.length}", flate(head.toString() + value))
        val map = HashMap<Int, Pair<Int, Int>>()
        repeat(members) { map[10_000 + it] = 4 to it }
        return MiniPdf.buildWithXrefStream(files, map)
    }

    @Test(timeout = 60_000)
    fun `같은 자리를 가리키는 칸 수백 개가 큰 값을 그만큼 만들지 않는다`() {
        // 옛 판은 칸마다 8 MiB 를 새로 읽어 객체 캐시(수로만 묶었다)에 두었다 — 300 벌, 2.4 GB 로 힙이 넘쳤다.
        val pages = bigValuePdf(300, asPages = true)
        assertEquals(PdfPageCopier.Inspection.Readable(300, PdfPageCopier.Lock.NONE), PdfPageCopier.inspect(ByteArraySource(pages)))

        val pdf = bigValuePdf(300, asPages = false)
        val sink = CountingSink()
        assertEquals(PdfPageCopier.Outcome.Done(1), PdfPageCopier.write(listOf(source(pdf, null)), sink))
        // 같은 자리의 값은 결과에서도 한 객체다 — 16진으로 쓴 8 MiB 한 벌(16 MiB 남짓). 칸마다 쓰면 4.8 GB 다.
        assertTrue(sink.n < 20L shl 20, "결과 ${sink.n} 바이트")
    }

    /** 쪽 [pages] 개가 저마다 **다른 자리**의 8 MiB 문자열을 품는다(객체 스트림 하나에 일곱 쪽씩) — 독립 검토 3차의 재현. */
    private fun bigPagesPdf(pages: Int): ByteArray {
        val files = ArrayList<ByteArray>()
        files += b("<< /Type /Catalog /Pages 2 0 R >>")
        files += b("<< /Type /Pages /Kids [" + (0 until pages).joinToString(" ") { "${10_000 + it} 0 R" } + "] /Count $pages /MediaBox [0 0 10 10] >>")
        val map = HashMap<Int, Pair<Int, Int>>()
        var p = 0
        var streamNum = 3
        while (p < pages) {
            val head = StringBuilder()
            val body = StringBuilder()
            var i = 0
            while (i < 7 && p < pages) {
                head.append("${10_000 + p} ${body.length} ")
                body.append("<< /Type /Page /Parent 2 0 R /Junk (").append(('a' + p % 26).toString().repeat(8 shl 20)).append(") >>\n")
                map[10_000 + p] = streamNum to i
                p++
                i++
            }
            files += MiniPdf.stream("/Type /ObjStm /Filter /FlateDecode /N $i /First ${head.length}", flate(head.toString() + body))
            streamNum++
        }
        return MiniPdf.buildWithXrefStream(files, map)
    }

    @Test(timeout = 300_000)
    fun `쪽 사전을 들고 있지 않아 쪽마다 큰 문서도 힙을 채우지 않는다`() {
        // 쪽 사전을 다 들면 쪽 수 × 8 MiB 다 — 이 JVM 의 힙보다 많은 쪽을 짓는다. 예산을 풀어(무한) 예산이 아니라 붙듦이 막히는지 본다.
        val max = Runtime.getRuntime().maxMemory()
        val pages = (max / (8L shl 20)).toInt() + 8
        val pdf = bigPagesPdf(pages)
        assertEquals(PdfPageCopier.Inspection.Readable(pages, PdfPageCopier.Lock.NONE), PdfPageCopier.inspect(ByteArraySource(pdf), budget = Long.MAX_VALUE))
        val sink = CountingSink()
        val o = PdfPageCopier.write(listOf(source(pdf, listOf(0, pages - 1))), sink, PdfPageCopier.Limits(readBudget = Long.MAX_VALUE))
        assertEquals(PdfPageCopier.Outcome.Done(2), o)
        // 예산은 힙에 맞춘다 — 힙의 1/4 을 넘지 않는다. 그 예산이면 이 문서는 너무 크다고 거절된다(쪽을 다 읽으면 힙만큼이다).
        assertTrue(PdfPageCopier.readBudget() <= max / 4)
        assertEquals(PdfPageCopier.Inspection.Unreadable, PdfPageCopier.inspect(ByteArraySource(pdf)))
    }

    @Test
    fun `같은 자리를 가리키는 두 주석 번호는 결과의 Annots 에 한 번만 든다`() {
        val head = "100 0 101 0 "
        val files = ArrayList<ByteArray>()
        files += b("<< /Type /Catalog /Pages 2 0 R >>")
        files += b("<< /Type /Pages /Kids [3 0 R] /Count 1 >>")
        files += b("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 10 10] /Annots [100 0 R 101 0 R] >>")
        files += MiniPdf.stream(
            "/Type /ObjStm /N 2 /First ${head.length}",
            b(head + "<< /Type /Annot /Subtype /Text /Rect [0 0 5 5] /Contents (hi) >>"),
        )
        val pdf = MiniPdf.buildWithXrefStream(files, mapOf(100 to (4 to 0), 101 to (4 to 1)))
        val out = copy(source(pdf, null))
        val f = PdfFile.open(ByteArraySource(out))
        val objs = PdfObjects(f, null, {})
        val page = assertNotNull(objs.dict(objs.array(objs.dict(objs.dict(f.trailer["Root"])?.get("Pages"))?.get("Kids"))?.items?.first()))
        assertEquals(1, objs.array(page["Annots"])?.items?.size)
    }

    @Test
    fun `위 마디의 큰 직접 자원은 쪽마다 펴 넣지 않고 한 객체로 나눠 쓴다`() {
        val pages = 2000
        val files = ArrayList<ByteArray>()
        files += b("<< /Type /Catalog /Pages 2 0 R >>")
        val kids = (0 until pages).joinToString(" ") { "${5 + it} 0 R" }
        val fonts = (0 until 10_000).joinToString(" ") { "/F$it 4 0 R" }
        files += b("<< /Type /Pages /Kids [$kids] /Count $pages /MediaBox [0 0 10 10] /Resources << /Font << $fonts >> >> >>")
        files += b("null")
        files += b("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>")
        for (i in 0 until pages) files += b("<< /Type /Page /Parent 2 0 R >>")
        val pdf = MiniPdf.build(files)
        val out = copy(source(pdf, null))
        // 쪽마다 펴 넣으면 2,000 × 120 KB(318 MB)다. 한 객체면 원본과 같은 수준이다.
        assertTrue(out.size < pdf.size * 2, "결과 ${out.size} 바이트, 원본 ${pdf.size}")
        val f = PdfFile.open(ByteArraySource(out))
        val objs = PdfObjects(f, null, {})
        val kidsOut = assertNotNull(objs.array(objs.dict(objs.dict(f.trailer["Root"])?.get("Pages"))?.get("Kids"))).items
        val first = assertNotNull(objs.dict(kidsOut.first()))
        val last = assertNotNull(objs.dict(kidsOut.last()))
        // 두 쪽이 같은 자원 객체를 가리키고, 작은 미디어 상자는 그대로 펴 넣었다.
        val res = assertIs<PdfRef>(first["Resources"])
        assertEquals(res, last["Resources"])
        assertEquals(10_000, objs.dict(objs.dict(res)?.get("Font"))?.map?.size)
        assertIs<PdfArray>(first["MediaBox"])
    }

    @Test
    fun `동작이 아닌 사전의 S·A·Next 는 건드리지 않는다`() {
        val pdf = MiniPdf.build(
            listOf(
                b("<< /Type /Catalog /Pages 2 0 R >>"),
                b("<< /Type /Pages /Kids [3 0 R] /Count 1 >>"),
                // 자원의 사용자 사전 — `/S`·`/A`·`/Next` 가 이름만 같다. 주석에 든 자바스크립트 동작의 `/A` 는 뗀다.
                b(
                    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 10 10] /Annots [5 0 R] " +
                        "/Resources << /Properties << /P1 << /S /Custom /A /Value /Next 4 0 R >> >> >> >>",
                ),
                b("<< /Kind /Other >>"),
                b("<< /Type /Annot /Subtype /Link /Rect [0 0 5 5] /A << /S /JavaScript /JS (app.alert(1)) >> >>"),
            ),
        )
        val out = copy(source(pdf, null))
        val f = PdfFile.open(ByteArraySource(out))
        val objs = PdfObjects(f, null, {})
        val page = assertNotNull(objs.dict(objs.array(objs.dict(objs.dict(f.trailer["Root"])?.get("Pages"))?.get("Kids"))?.items?.first()))
        val p1 = assertNotNull(objs.dict(objs.dict(objs.dict(page["Resources"])?.get("Properties"))?.get("P1")))
        assertEquals("Value", objs.name(p1["A"]))
        assertEquals("Other", objs.name(objs.dict(p1["Next"])?.get("Kind")))
        // 자바스크립트만 든 링크는 갈 곳이 없어 통째로 빠졌다.
        assertNull(page["Annots"])
    }

    @Test
    fun `여러 쪽이 함께 가리키는 Annots 는 결과에서도 한 객체다`() {
        val pages = 200
        val files = ArrayList<ByteArray>()
        files += b("<< /Type /Catalog /Pages 2 0 R >>")
        val kids = (0 until pages).joinToString(" ") { "${5 + it} 0 R" }
        files += b("<< /Type /Pages /Kids [$kids] /Count $pages /MediaBox [0 0 10 10] >>")
        files += b("[" + (0 until 1000).joinToString(" ") { "4 0 R" } + "]") // 3: 같은 링크 천 번
        files += b("<< /Type /Annot /Subtype /Link /Rect [0 0 5 5] /Dest [5 0 R /Fit] >>") // 4
        for (i in 0 until pages) files += b("<< /Type /Page /Parent 2 0 R /Annots 3 0 R >>")
        val pdf = MiniPdf.build(files)
        val out = copy(source(pdf, null))
        // 배열을 쪽마다 펴 쓰면 200 × 1000 개의 참조(1 MB 넘게)다. 한 객체면 원본과 같은 크기 수준이다.
        assertTrue(out.size < pdf.size * 2, "결과 ${out.size} 바이트, 원본 ${pdf.size}")
        val e = assertNotNull(PdfTextEngine.open(ByteArraySource(out)))
        assertEquals(0, e.annotationLinks(150).first().targetPage)
    }

    @Test
    fun `쪽보다 먼저 끌려온 링크도 목적지가 다시 적힌다`() {
        // 1쪽의 주석(`/IRT`)이 2쪽의 링크(빠진 3쪽으로 간다)를 먼저 끌어온다 — 옛 판은 `/Dest [null …]` 로 남겼다.
        val pdf = MiniPdf.build(
            listOf(
                b("<< /Type /Catalog /Pages 2 0 R >>"),
                b("<< /Type /Pages /Kids [3 0 R 4 0 R 5 0 R] /Count 3 /MediaBox [0 0 100 100] >>"),
                b("<< /Type /Page /Parent 2 0 R /Annots [6 0 R] >>"),
                b("<< /Type /Page /Parent 2 0 R /Annots [7 0 R] >>"),
                b("<< /Type /Page /Parent 2 0 R >>"),
                b("<< /Type /Annot /Subtype /Text /Rect [0 0 5 5] /IRT 7 0 R >>"),
                b("<< /Type /Annot /Subtype /Link /Rect [0 0 5 5] /Dest [5 0 R /Fit] >>"),
            ),
        )
        val out = String(copy(source(pdf, listOf(0, 1))), Charsets.ISO_8859_1)
        assertFalse(out.contains("[null"), out)
        assertFalse(out.contains("/Dest"), out)
    }

    @Test
    fun `자바스크립트·Launch 동작과 첨부는 옮기지 않는다`() {
        val pdf = MiniPdf.build(
            listOf(
                b("<< /Type /Catalog /Pages 2 0 R >>"),
                b("<< /Type /Pages /Kids [3 0 R] /Count 1 /MediaBox [0 0 100 100] >>"),
                b(
                    "<< /Type /Page /Parent 2 0 R /AA << /O << /S /JavaScript /JS (app.alert(1)) >> >> " +
                        "/Annots [4 0 R 5 0 R 6 0 R 7 0 R 8 0 R] >>",
                ),
                // 자바스크립트만 하는 링크 — 갈 곳이 없어 쪽에서 지운다.
                b("<< /Type /Annot /Subtype /Link /Rect [0 0 5 5] /A << /S /JavaScript /JS (app.alert(2)) >> >>"),
                // 바깥 주소 — 남는다. 뒤에 이은 자바스크립트(`/Next`)는 뗀다.
                b("<< /Type /Annot /Subtype /Link /Rect [0 0 5 5] /A << /S /URI /URI (https://example.invalid/) /Next << /S /JavaScript /JS (x) >> >> >>"),
                // 위젯의 자바스크립트 동작과 추가 동작은 떼고 모양은 남긴다.
                b("<< /Type /Annot /Subtype /Widget /Rect [0 0 5 5] /A << /S /Launch /F (calc.exe) >> /AA << /D 9 0 R >> >>"),
                // 첨부 — 쪽에서 지운다.
                b("<< /Type /Annot /Subtype /FileAttachment /Rect [0 0 5 5] /FS 10 0 R >>"),
                // 다른 파일로 가는 링크 — 따르지 않는 동작이라 지운다.
                b("<< /Type /Annot /Subtype /Link /Rect [0 0 5 5] /A << /S /GoToR /F (other.pdf) /D [0 /Fit] >> >>"),
                b("<< /S /JavaScript /JS (app.alert(3)) >>"),
                b("<< /Type /Filespec /F (secret.bin) /EF << /F 11 0 R >> >>"),
                MiniPdf.stream("/Type /EmbeddedFile", b("SECRET-ATTACHMENT")),
            ),
        )
        val out = String(copy(source(pdf, null)), Charsets.ISO_8859_1)
        for (gone in listOf("JavaScript", "app.alert", "Launch", "calc.exe", "FileAttachment", "SECRET-ATTACHMENT", "GoToR", "/AA", "/Next")) {
            assertFalse(out.contains(gone), gone)
        }
        assertTrue(out.contains("/URI"))
        assertTrue(out.contains("/Widget"))
    }

    private class CountingSink : PdfSink {
        var n = 0L
        override val position: Long get() = n
        override fun write(b: ByteArray, off: Int, len: Int) {
            n += len
        }
        override fun patch(at: Long, b: ByteArray) = Unit
    }

    @Test
    fun `쪽마다 진행을 알린다`() {
        val seen = ArrayList<Pair<Int, Int>>()
        PdfPageCopier.write(
            listOf(source(fivePages(), listOf(0, 1), "a"), source(fivePages(), listOf(4), "b")),
            MemorySink(),
            onPage = { done, total, _ -> seen += done to total },
        )
        assertEquals(listOf(1 to 3, 2 to 3, 3 to 3), seen)
    }

    @Test
    fun `위젯은 필드 나무를 떼어 다른 쪽의 위젯을 끌어오지 않는다`() {
        val pdf = MiniPdf.build(
            listOf(
                /* 1 */ b("<< /Type /Catalog /Pages 2 0 R /AcroForm << /Fields [5 0 R] >> >>"),
                /* 2 */ b("<< /Type /Pages /Kids [3 0 R 4 0 R] /Count 2 /MediaBox [0 0 100 100] >>"),
                /* 3 */ b("<< /Type /Page /Parent 2 0 R /Annots [6 0 R] >>"),
                /* 4 */ b("<< /Type /Page /Parent 2 0 R /Annots [7 0 R] >>"),
                /* 5 */ b("<< /FT /Tx /T (name) /Kids [6 0 R 7 0 R] >>"),
                /* 6 */ b("<< /Type /Annot /Subtype /Widget /Parent 5 0 R /Rect [0 0 10 10] /P 3 0 R >>"),
                /* 7 */ b("<< /Type /Annot /Subtype /Widget /Parent 5 0 R /Rect [0 0 10 10] /P 4 0 R /Big (${"x".repeat(50)}) >>"),
            ),
        )
        val out = copy(source(pdf, listOf(0)))
        assertEquals(5, size(out)) // 뿌리·나무·쪽·위젯 하나 — 번호 1~4
        assertFalse(String(out, Charsets.ISO_8859_1).contains("/Parent 5"))
    }

    private fun resource(name: String): ByteArray =
        File(javaClass.classLoader!!.getResource("pdfcrypt/$name")!!.toURI()).readBytes()

    private fun indexOf(hay: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..hay.size - needle.size) {
            for (j in needle.indices) if (hay[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }
}

/** 결과의 쪽 사전·참조를 시험이 본다(쪽 나무를 따라 — 결과는 뿌리 하나에 쪽을 차례로 단다). */
internal object PdfTextEngineAccess {
    fun pageRefs(objs: PdfObjects, f: PdfFile): List<PdfRef> {
        val root = objs.dict(f.trailer["Root"])!!
        val pages = objs.dict(root["Pages"])!!
        return (objs.resolve(pages["Kids"]) as PdfArray).items.map { it as PdfRef }
    }

    fun pageDicts(objs: PdfObjects, f: PdfFile) = pageRefs(objs, f).map { objs.dict(it)!! }
}
