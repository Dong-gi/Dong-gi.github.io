package io.github.donggi.iroiroviewer.docview.pdf.text

import io.github.donggi.iroiroviewer.docview.pdf.PdfLinks
import io.github.donggi.iroiroviewer.docview.pdf.PdfOutline
import io.github.donggi.iroiroviewer.docview.pdf.PdfSearch
import io.github.donggi.iroiroviewer.docview.pdf.crypt.ByteArraySource
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 목차와 링크 주석([PdfNavigation]). 표본은 시험이 손으로 짓는 PDF 다 — 목적지의 네 꼴(쪽 참조·정수·이름 사전·이름 나무), 글 문자열의
 * 세 인코딩, 회전·자르기 상자, 그리고 악성 모양(순환·깊은 사슬·거대한 형제 줄·쪽 밖·무한한 수). 암호가 걸린 문서는 pypdf 가 잠근 표본
 * (`pdfcrypt/nav-owner-*.pdf`)이다. 실물과의 대조는 `PdfNavigationCorpusTest`.
 */
class PdfNavigationTest {

    private fun open(pdf: ByteArray): PdfTextEngine = assertNotNull(PdfTextEngine.open(ByteArraySource(pdf)))

    private fun b(s: String) = s.toByteArray(Charsets.ISO_8859_1)

    /** 세 쪽 + 목차 + 이름 목적지(나무·옛 사전) + 셋째 쪽의 링크 주석. 셋째 쪽은 90도 돌았고 자르기 상자가 있다. */
    private fun navPdf(): ByteArray = MiniPdf.build(
        listOf(
            /* 1 */ b("<< /Type /Catalog /Pages 2 0 R /Outlines 6 0 R /Names << /Dests 18 0 R >> /Dests 22 0 R >>"),
            /* 2 */ b("<< /Type /Pages /Kids [3 0 R 4 0 R 5 0 R] /Count 3 >>"),
            /* 3 */ b("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >>"),
            /* 4 */ b("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >>"),
            /* 5 */ b(
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 600 800] /CropBox [50 100 550 700] /Rotate 90 " +
                    "/Annots [13 0 R 14 0 R 15 0 R 16 0 R 17 0 R 23 0 R 24 0 R 25 0 R] >>",
            ),
            /* 6 */ b("<< /Type /Outlines /First 7 0 R /Last 9 0 R /Count 3 >>"),
            /* 7 */ b("<< /Title (Intro) /Parent 6 0 R /Next 8 0 R /First 10 0 R /Last 11 0 R /Count 2 /Dest [3 0 R /XYZ 0 792 0] >>"),
            // PDFDocEncoding 의 0x8D·0x8E 는 둥근 따옴표다. 옛 `/Dests` 사전의 이름 목적지.
            /* 8 */ b("<< /Title (\\215Q\\216) /Parent 6 0 R /Prev 7 0 R /Next 9 0 R /Dest /old >>"),
            // UTF-8 BOM(PDF 2.0)의 '가'. 정수 쪽 번호. 접힌 채로 저장된 항목(`/Count` 음수).
            /* 9 */ b("<< /Title <EFBBBFEAB080> /Parent 6 0 R /Prev 8 0 R /First 12 0 R /Last 12 0 R /Count -1 /Dest [0 /Fit] >>"),
            // UTF-16BE 의 '한글'.
            /* 10 */ b("<< /Title <FEFFD55CAE00> /Parent 7 0 R /Next 11 0 R /A << /S /GoTo /D [4 0 R /Fit] >> >>"),
            // 이름 나무의 문자열 열쇠 — 값은 `/D` 를 든 사전이다.
            /* 11 */ b("<< /Title (Named  \\t str) /Parent 7 0 R /Prev 10 0 R /Dest (chap2) >>"),
            // 바깥 주소 — 쪽이 없다.
            /* 12 */ b("<< /Title (web) /Parent 9 0 R /A << /S /URI /URI (http://example.invalid/) >> >>"),
            /* 13 */ b("<< /Type /Annot /Subtype /Link /Rect [100 200 300 250] /Dest [3 0 R /Fit] >>"),
            /* 14 */ b("<< /Type /Annot /Subtype /Link /Rect [100 300 300 350] /A << /S /GoTo /D [4 0 R /Fit] >> >>"),
            // 쪽 밖으로 걸친 상자 — 쪽 안으로 자른다. 이름 나무.
            /* 15 */ b("<< /Type /Annot /Subtype /Link /Rect [500 600 700 900] /Dest (chap2) >>"),
            // 넓이가 없다.
            /* 16 */ b("<< /Type /Annot /Subtype /Link /Rect [100 100 100 300] /Dest [3 0 R /Fit] >>"),
            // 링크가 아니다.
            /* 17 */ b("<< /Type /Annot /Subtype /Widget /Rect [10 10 90 90] /Dest [3 0 R /Fit] >>"),
            /* 18 */ b("<< /Kids [19 0 R 20 0 R] >>"),
            /* 19 */ b("<< /Limits [(a) (b)] /Names [(a) [3 0 R /Fit] (b) [3 0 R /Fit]] >>"),
            /* 20 */ b("<< /Limits [(c) (d)] /Names [(chap2) 21 0 R] >>"),
            /* 21 */ b("<< /D [5 0 R /Fit] >>"),
            /* 22 */ b("<< /old [4 0 R /XYZ null null null] >>"),
            // 수로 읽으면 무한이 되는 좌표.
            /* 23 */ b("<< /Type /Annot /Subtype /Link /Rect [0 0 ${"9".repeat(400)} 10] /Dest [3 0 R /Fit] >>"),
            // 쪽 밖에만 있다.
            /* 24 */ b("<< /Type /Annot /Subtype /Link /Rect [5000 5000 6000 6000] /Dest [3 0 R /Fit] >>"),
            // 없는 쪽·없는 이름.
            /* 25 */ b("<< /Type /Annot /Subtype /Link /Rect [60 110 90 140] /Dest (nowhere) >>"),
        ),
    )

    @Test
    fun `목차를 앞차례로 펴고 목적지를 쪽으로 푼다`() {
        val o = open(navPdf()).outline()
        assertEquals(
            listOf(
                PdfOutline.Entry("Intro", 0, 0, open = true, hasChildren = true),
                PdfOutline.Entry("한글", 1, 1, open = false, hasChildren = false),
                PdfOutline.Entry("Named str", 2, 1, open = false, hasChildren = false),
                PdfOutline.Entry("“Q”", 1, 0, open = false, hasChildren = false),
                PdfOutline.Entry("가", 0, 0, open = false, hasChildren = true),
                PdfOutline.Entry("web", null, 1, open = false, hasChildren = false),
            ),
            o.entries,
        )
    }

    @Test
    fun `쪽의 Dest 꼴 링크를 보이는 쪽의 좌표로 옮긴다`() {
        val e = open(navPdf())
        // 셋째 쪽: 자르기 상자 500×600 을 90도 돌려 보이는 쪽은 600×500. 점 (x, y) 는 (y − 100, x − 50) 이 된다.
        val links = e.annotationLinks(2)
        assertEquals(
            listOf(
                PdfLinks.Link(listOf(PdfSearch.Box(100f, 50f, 150f, 250f)), 0),
                // (500,600)→(500,450), (700,900)→(800,650) 을 쪽(600×500) 안으로 자른다.
                PdfLinks.Link(listOf(PdfSearch.Box(500f, 450f, 600f, 500f)), 2),
            ),
            links,
        )
        // `/A GoTo` 꼴은 플랫폼이 준다 — 시험(말뭉치 대조)에서만 함께 읽는다.
        val all = e.annotationLinks(2, withActions = true)
        assertEquals(listOf(0, 1, 2), all.map { it.targetPage })
        assertEquals(PdfSearch.Box(200f, 50f, 250f, 250f), all[1].boxes.single())
        // 링크 주석이 없는 쪽·없는 쪽.
        assertTrue(e.annotationLinks(0).isEmpty())
        assertTrue(e.annotationLinks(7).isEmpty())
        assertTrue(e.annotationLinks(-1).isEmpty())
    }

    /**
     * 목차를 읽던 일의 취소 확인이 **링크를 읽는 일에 남지 않는다.** 남으면 목차를 읽은 코루틴이 끝난 뒤 그 확인이 취소 예외를 던져 두드림이
     * 소리 없이 사라진다(Android 16 에뮬레이터에서 실제로 그랬다 — 쪽 넘김도 막대도 움직이지 않았다).
     */
    @Test
    fun `앞 일의 취소 확인을 다음 일에 쓰지 않는다`() {
        val e = open(navPdf())
        var outlineDone = false
        e.outline { check(!outlineDone) { "끝난 일의 확인" } }
        outlineDone = true
        assertEquals(2, e.annotationLinks(2) {}.size)
        assertEquals(3, e.annotationLinks(2, withActions = true) {}.size)
    }

    @Test
    fun `순환하는 목차는 한 번씩만 따른다`() {
        val pdf = MiniPdf.build(
            listOf(
                b("<< /Type /Catalog /Pages 2 0 R /Outlines 4 0 R >>"),
                b("<< /Type /Pages /Kids [3 0 R] /Count 1 >>"),
                b("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >>"),
                b("<< /Type /Outlines /First 5 0 R >>"),
                // 첫 아이가 자기 자신이다.
                b("<< /Title (A) /Next 6 0 R /First 5 0 R /Dest [3 0 R /Fit] >>"),
                // 다음이 앞으로 돌아가고, 첫 아이가 뿌리다.
                b("<< /Title (B) /Next 5 0 R /First 4 0 R >>"),
            ),
        )
        val o = open(pdf).outline()
        assertEquals(listOf("A", "B"), o.entries.map { it.title })
        assertEquals(listOf(0, 0), o.entries.map { it.depth })
    }

    @Test
    fun `깊은 사슬은 깊이 상한에서 끊는다`() {
        val n = 200
        val items = (0 until n).map { i ->
            val next = if (i + 1 < n) "/First ${i + 7} 0 R" else ""
            b("<< /Title (d$i) $next /Dest [3 0 R /Fit] >>")
        }
        val pdf = MiniPdf.build(
            listOf(
                b("<< /Type /Catalog /Pages 2 0 R /Outlines 4 0 R >>"),
                b("<< /Type /Pages /Kids [3 0 R] /Count 1 >>"),
                b("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >>"),
                b("<< /Type /Outlines /First 6 0 R >>"),
                b("<< >>"),
            ) + items,
        )
        val o = open(pdf).outline()
        assertEquals(PdfOutline.MAX_DEPTH, o.entries.size)
        assertEquals((0 until PdfOutline.MAX_DEPTH).toList(), o.entries.map { it.depth })
        // 끊은 자리의 항목은 아이가 없는 것으로 보인다.
        assertEquals(false, o.entries.last().hasChildren)
    }

    @Test
    fun `거대한 형제 줄은 항목 상한에서 끊는다`() {
        val n = PdfOutline.MAX_ENTRIES + 500
        val items = (0 until n).map { i ->
            val next = if (i + 1 < n) "/Next ${i + 7} 0 R" else ""
            b("<< /Title (s$i) $next /Dest [3 0 R /Fit] >>")
        }
        val pdf = MiniPdf.build(
            listOf(
                b("<< /Type /Catalog /Pages 2 0 R /Outlines 4 0 R >>"),
                b("<< /Type /Pages /Kids [3 0 R] /Count 1 >>"),
                b("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >>"),
                b("<< /Type /Outlines /First 6 0 R >>"),
                b("<< >>"),
            ) + items,
        )
        val o = open(pdf).outline()
        assertEquals(PdfOutline.MAX_ENTRIES, o.entries.size)
        assertEquals("s0", o.entries.first().title)
    }

    @Test
    fun `스스로를 품은 이름 나무에서 멎지 않는다`() {
        val pdf = MiniPdf.build(
            listOf(
                b("<< /Type /Catalog /Pages 2 0 R /Outlines 4 0 R /Names << /Dests 6 0 R >> >>"),
                b("<< /Type /Pages /Kids [3 0 R] /Count 1 >>"),
                b("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >>"),
                b("<< /Type /Outlines /First 5 0 R >>"),
                b("<< /Title (x) /Dest (missing) >>"),
                b("<< /Kids [6 0 R 6 0 R] >>"),
            ),
        )
        val o = open(pdf).outline()
        assertEquals(listOf(PdfOutline.Entry("x", null, 0, open = false, hasChildren = false)), o.entries)
    }

    /**
     * 이름 나무의 마디 **하나**가 거대한 `/Names` 를 들었다 — 마디 수로만 취소를 보면 그 마디를 다 훑기 전에는 시간 상한이 닿지 않는다
     * (칸마다 참조를 풀면 몇 분). 독립 검토가 잡았다 — 고침을 되돌리면 이 시험만 깨진다.
     */
    @Test
    fun `이름 나무 마디 하나를 훑는 동안에도 취소가 닿는다`() {
        val names = (0 until 5_000).joinToString(" ", "[", "]") { "(k$it) [3 0 R /Fit]" }
        val pdf = MiniPdf.build(
            listOf(
                b("<< /Type /Catalog /Pages 2 0 R /Outlines 4 0 R /Names << /Dests 6 0 R >> >>"),
                b("<< /Type /Pages /Kids [3 0 R] /Count 1 >>"),
                b("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >>"),
                b("<< /Type /Outlines /First 5 0 R >>"),
                b("<< /Title (x) /Dest (missing) >>"),
                b("<< /Names $names >>"),
            ),
        )
        val e = open(pdf)
        assertFailsWith<IllegalStateException> { e.outline { throw IllegalStateException("취소") } }
        // 취소가 없으면 끝까지 훑어 '없다' 로 끝난다.
        assertEquals(listOf(PdfOutline.Entry("x", null, 0, open = false, hasChildren = false)), e.outline().entries)
    }

    /**
     * xref 를 다시 세운 파일에서 객체 스트림 속 번호를 찾는 표를 **훑다가 취소되면**(목차 10초·두드림 2초) 반쯤 찬 표가 남아, 그 문서에서
     * 나머지 객체가 영영 '없다' 가 되었다. 독립 검토가 잡았다 — 고침을 되돌리면 이 시험만 깨진다.
     */
    @Test
    fun `객체 스트림 번호표를 훑다 취소되어도 다음에 다시 찾는다`() {
        val inner = b("7 0 << /Type /Outlines /First 5 0 R >>")
        val good = MiniPdf.buildWithXrefStream(
            listOf(
                b("<< /Type /Catalog /Pages 2 0 R /Outlines 7 0 R >>"),
                b("<< /Type /Pages /Kids [3 0 R] /Count 1 >>"),
                b("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >>"),
                MiniPdf.stream("/Type /ObjStm /N 1 /First 4", inner),
                b("<< /Title (T) /Dest [3 0 R /Fit] >>"),
            ),
            inStream = mapOf(7 to (4 to 0)),
        )
        // startxref 를 망가뜨려 xref 를 다시 세우게 한다(객체 스트림 속 7번을 xref 가 알려 주지 않는다).
        val text = String(good, Charsets.ISO_8859_1)
        val at = text.lastIndexOf("startxref\n") + "startxref\n".length
        val broken = (text.substring(0, at) + "1" + text.substring(text.indexOf('\n', at))).toByteArray(Charsets.ISO_8859_1)
        val e = open(broken)
        assertFailsWith<IllegalStateException> { e.outline { throw IllegalStateException("취소") } }
        assertEquals(listOf("T" to 0), e.outline().entries.map { it.title to it.page })
    }

    /** `/Rotate` 180·270 과 원점이 0 이 아닌 자르기 상자 — 90 만 보던 시험의 빈틈. 점 (x, y) 가 보이는 쪽의 어디로 가는지 손으로 셈했다. */
    @Test
    fun `180도와 270도 돈 쪽의 링크 상자`() {
        fun pdf(rotate: Int) = MiniPdf.build(
            listOf(
                b("<< /Type /Catalog /Pages 2 0 R >>"),
                b("<< /Type /Pages /Kids [3 0 R] /Count 1 >>"),
                b(
                    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 600 800] /CropBox [50 100 550 700] /Rotate $rotate " +
                        "/Annots [4 0 R] >>",
                ),
                b("<< /Type /Annot /Subtype /Link /Rect [100 200 300 250] /Dest [3 0 R /Fit] >>"),
            ),
        )
        // 자르기 상자 [50 100 550 700] — 폭 500, 높이 600.
        // 180: (x, y) → (550 − x, y − 100). (100,200)→(450,100), (300,250)→(250,150).
        assertEquals(
            listOf(PdfLinks.Link(listOf(PdfSearch.Box(250f, 100f, 450f, 150f)), 0)),
            open(pdf(180)).annotationLinks(0),
        )
        // 270: (x, y) → (700 − y, 550 − x). (100,200)→(500,450), (300,250)→(450,250). 보이는 쪽은 600×500.
        assertEquals(
            listOf(PdfLinks.Link(listOf(PdfSearch.Box(450f, 250f, 500f, 450f)), 0)),
            open(pdf(270)).annotationLinks(0),
        )
        // 0: (x, y) → (x − 50, 700 − y). (100,200)→(50,500), (300,250)→(250,450).
        assertEquals(
            listOf(PdfLinks.Link(listOf(PdfSearch.Box(50f, 450f, 250f, 500f)), 0)),
            open(pdf(0)).annotationLinks(0),
        )
    }

    @Test
    fun `목차가 없거나 망가졌으면 빈 목차다`() {
        val none = MiniPdf.build(
            listOf(
                b("<< /Type /Catalog /Pages 2 0 R >>"),
                b("<< /Type /Pages /Kids [3 0 R] /Count 1 >>"),
                b("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >>"),
            ),
        )
        assertTrue(open(none).outline().isEmpty)
        val broken = MiniPdf.build(
            listOf(
                b("<< /Type /Catalog /Pages 2 0 R /Outlines 9 0 R >>"),
                b("<< /Type /Pages /Kids [3 0 R] /Count 1 >>"),
                b("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >>"),
            ),
        )
        assertTrue(open(broken).outline().isEmpty)
    }

    @Test
    fun `글 문자열의 세 인코딩과 언어 표지를 읽는다`() {
        fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        assertEquals("한글", PdfNavigation.decodeText(hex("FEFFD55CAE00")))
        // UTF-16BE 안의 언어 표지(ESC ko ESC)를 지운다.
        assertEquals("한", PdfNavigation.decodeText(hex("FEFF001B006B006F001BD55C")))
        assertEquals("한", PdfNavigation.decodeText(hex("FFFE5CD5")))
        assertEquals("가", PdfNavigation.decodeText(hex("EFBBBFEAB080")))
        // PDFDocEncoding: 0x18 은 브레브, 0xA0 은 유로, 0x93 은 fi 합자.
        assertEquals("˘€ﬁ", PdfNavigation.decodeText(hex("18A093")))
        // 제어 글자·빈칸은 하나의 빈칸으로, 앞뒤는 지운다. 홀수 바이트의 UTF-16 은 끝 바이트를 버린다.
        assertEquals("a b", PdfNavigation.decodeText(b("\u0000 a\r\n\tb \u0007")))
        assertEquals("A", PdfNavigation.decodeText(hex("FEFF004100")))
        assertEquals("", PdfNavigation.decodeText(null))
        val long = PdfNavigation.decodeText(b("x".repeat(5000)))
        assertEquals(PdfOutline.MAX_TITLE, long.length)
        // 자른 자리가 대리 쌍의 가운데면 앞 반쪽을 버린다.
        val emoji = PdfNavigation.decodeText(hex("FEFF" + "0061".repeat(PdfOutline.MAX_TITLE - 1) + "D83DDE00"))
        assertEquals(PdfOutline.MAX_TITLE - 1, emoji.length)
    }

    /** pypdf 6.17 이 잠근 표본 — 소유자 암호만, 빈 사용자 암호. 목차 제목·이름 목적지의 열쇠·링크의 `/Dest` 가 암호문 문자열이다. */
    @Test
    fun `소유자 암호만 걸린 문서의 목차와 링크를 푼다`() {
        val dir = File(javaClass.classLoader!!.getResource("pdfcrypt/nav-owner-aes128.pdf")!!.toURI()).parentFile
        for (name in listOf("nav-owner-aes128.pdf", "nav-owner-rc4.pdf")) {
            val e = open(File(dir, name).readBytes())
            assertEquals(
                listOf("1장 시작" to 0, "1.1 절" to 1, "Named" to 2),
                e.outline().entries.map { it.title to it.page },
                name,
            )
            assertEquals(listOf(0, 1, 0), e.outline().entries.map { it.depth }, name)
            // 595×842 쪽의 [100 700 300 720] → 위 842 − 720 = 122.
            assertEquals(listOf(PdfLinks.Link(listOf(PdfSearch.Box(100f, 122f, 300f, 142f)), 2)), e.annotationLinks(0), name)
        }
    }
}
