package io.github.donggi.iroiroviewer.format.hwpx

import io.github.donggi.iroiroviewer.format.ByteArrayDocumentSource
import io.github.donggi.iroiroviewer.format.FlowWarnings
import io.github.donggi.iroiroviewer.format.FormatId
import io.github.donggi.iroiroviewer.format.OpenFailure
import io.github.donggi.iroiroviewer.format.OpenOutcome
import io.github.donggi.iroiroviewer.format.ProbeContext
import io.github.donggi.iroiroviewer.format.UnsupportedFeatures
import io.github.donggi.iroiroviewer.format.html.HancomChunkPolicy
import io.github.donggi.iroiroviewer.format.hwpx.Hwpx.p
import io.github.donggi.iroiroviewer.format.hwpx.Hwpx.sec
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 패키지·부분·목차·판별 — 겉모양. */
class HwpxStructureTest {

    @Test
    fun 구역마다_부분이_되고_스파인_차례를_따른다() {
        val t = TinyHwpx().section(sec(p("첫 구역"))).section(sec(p("둘째 구역"))).also { it.title = "문서 제목" }
        Hwpx.doc(t).use { doc ->
            assertEquals(2, doc.parts.size)
            assertEquals(FormatId.HWPX, doc.formatId)
            assertTrue("첫 구역" in doc.body(0) && "둘째 구역" !in doc.body(0))
            assertTrue("둘째 구역" in doc.body(1))
            // 제목은 비워 둔다(공공 문서의 `opf:title` 은 서식 파일의 낡은 값이 흔하다) — 화면이 파일 이름으로 채운다.
            assertEquals("", doc.title)
            assertEquals(doc.parts[1].path, "~part-1.html")
        }
    }

    @Test
    fun 문서의_제목은_쓰지_않고_파일_이름에_맡긴다() {
        // 실물: K26 `3`, S08 `1111`, O16 `U+F53A` + `글 97 안내문`, K27 `공표용 보도자료` — 전부 서식 파일에서 물려받은 값이다.
        // 쓸 만해 보이는 제목도 쓰지 않는다(HWP 5.0 과 같다). 화면은 빈 제목을 파일 이름으로 채운다.
        for (raw in listOf("1111", "3", "글 97 안내문", "  공표용   보도자료 ", "문서 제목")) {
            val t = TinyHwpx().section(sec(p("본문"))).also { it.title = raw }
            Hwpx.doc(t).use { doc -> assertEquals("", doc.title, raw) }
        }
    }

    @Test
    fun 양쪽_정렬은_적지_않고_배분_정렬은_옮긴다() {
        // 좁은 화면에서 양쪽 정렬 + keep-all 은 낱말 사이를 크게 벌린다(13단계 기기 확인). 배분은 문서가 일부러 고른 것이다.
        // 문단 모양 0 이 JUSTIFY, 1 이 CENTER 다.
        Hwpx.doc(TinyHwpx().section(sec(p("양쪽"), p("가운데", paraPr = 1)))).use { doc ->
            val h = doc.body(0)
            assertEquals(1, Regex("text-align").findAll(h).count(), h)
            assertTrue("text-align:center" in h, h)
        }
        val d = TinyHwpx().also { it.header = Hwpx.header().replace("horizontal=\"JUSTIFY\"", "horizontal=\"DISTRIBUTE\"") }
        Hwpx.doc(d.section(sec(p("배분")))).use { doc -> assertTrue("text-align:justify" in doc.body(0), doc.body(0)) }
    }

    @Test
    fun 매니페스트가_없으면_구역_파일을_번호_차례로_찾고_알린다() {
        // section10 이 section2 뒤에 와야 한다 — 이름 차례(사전순)로 늘어놓으면 10 이 2 앞에 선다.
        val t = TinyHwpx().also { it.writeContentHpf = false }
        for (i in 0..10) t.section(sec(p("구역 $i")))
        Hwpx.doc(t).use { doc ->
            assertEquals(11, doc.parts.size)
            assertTrue("구역 2" in doc.body(2), doc.body(2))
            assertTrue("구역 10" in doc.body(10), doc.body(10))
            assertTrue(doc.warnings.any { it.code == FlowWarnings.NO_CONTENT_TYPES }, doc.warnings.toString())
        }
    }

    @Test
    fun META_INF_manifest_가_없는_것은_문제가_아니다() {
        // O14(hwpxlib 의 no_manifest.hwpx)의 모양 — 평문 문서의 ODF 매니페스트는 어차피 비어 있다.
        val bytes = TinyHwpx().section(sec(p("본문"))).build()
        val stripped = rezip(bytes) { it != "META-INF/manifest.xml" }
        Hwpx.doc(stripped).use { doc ->
            assertTrue("본문" in doc.body())
            assertTrue(doc.warnings.isEmpty(), doc.warnings.toString())
        }
    }

    @Test
    fun 머리가_없어도_본문은_기본_서식으로_열린다() {
        val t = TinyHwpx().section(sec(p("머리 없는 본문", paraPr = 1, charPr = 1))).also { it.header = null }
        Hwpx.doc(t).use { doc ->
            assertTrue("머리 없는 본문" in doc.body())
        }
    }

    @Test
    fun 깨진_머리는_알리고_본문은_연다() {
        val t = TinyHwpx().section(sec(p("본문"))).also { it.header = "<hh:head xmlns:hh=\"x\"><hh:refList><broken" }
        Hwpx.doc(t).use { doc ->
            assertTrue("본문" in doc.body())
            assertTrue(doc.warnings.any { it.code == FlowWarnings.AUX_FAILED && it.detail == "Contents/header.xml" }, doc.warnings.toString())
        }
    }

    @Test
    fun 다른_포맷을_말하는_mimetype_은_깨진_것으로_본다() {
        val t = TinyHwpx().section(sec(p("본문"))).also { it.mimetype = "application/epub+zip" }
        val o = Hwpx.open(t.build())
        assertTrue((o as OpenOutcome.Failed).failure is OpenFailure.Corrupt, o.toString())
    }

    @Test
    fun mimetype_이_없거나_모르는_값이어도_연다() {
        for (m in listOf(null, "application/owpml")) {
            val t = TinyHwpx().section(sec(p("본문"))).also { it.mimetype = m }
            Hwpx.doc(t).use { doc -> assertTrue("본문" in doc.body(), m.toString()) }
        }
    }

    @Test
    fun 보여_줄_구역이_없으면_깨진_파일이다() {
        val o = Hwpx.open(TinyHwpx().build())
        assertTrue((o as OpenOutcome.Failed).failure is OpenFailure.Corrupt, o.toString())
    }

    @Test
    fun ZIP_이_아니면_깨진_파일이다() {
        val html = "\n\n<!DOCTYPE html><html><body>not a document</body></html>".toByteArray()
        val o = Hwpx.open(html)
        assertEquals(OpenFailure.Corrupt("HWPX 가 아니다"), (o as OpenOutcome.Failed).failure)
    }

    @Test
    fun 중간에서_잘린_ZIP_은_입출력_실패가_아니라_깨진_파일이다() {
        val whole = TinyHwpx().section(sec(p("본문"))).build()
        for (cut in listOf(64, whole.size / 2, whole.size - 10)) {
            val o = Hwpx.open(whole.copyOf(cut))
            val f = (o as OpenOutcome.Failed).failure
            assertTrue(f is OpenFailure.Corrupt, "$cut 바이트: $f")
        }
    }

    @Test
    fun 스크립트가_있으면_매크로로_알리고_돌리지_않는다() {
        val t = TinyHwpx().section(sec(p("본문"))).file("Scripts/sourceScripts", "function f(){}".toByteArray())
        Hwpx.doc(t).use { doc ->
            assertEquals(1, doc.unsupported.snapshot()[UnsupportedFeatures.MACRO])
            assertTrue(doc.warnings.any { it.code == FlowWarnings.MACROS })
        }
    }

    @Test
    fun 개요_문단이_목차가_되고_그_제목으로_간다() {
        val t = TinyHwpx().section(
            sec(
                Hwpx.secPrParagraph("머리 글"),
                p("가장 큰 제목", paraPr = 2),
                p("본문 하나"),
                p("작은 제목", paraPr = 3),
                p("다음 큰 제목", paraPr = 2),
                p("그 밑 제목", paraPr = 3),
            ),
        )
        Hwpx.doc(t).use { doc ->
            assertEquals(
                listOf("1. 가장 큰 제목" to 0, "가. 작은 제목" to 1, "2. 다음 큰 제목" to 0, "가. 그 밑 제목" to 1),
                doc.outline.map { it.title to it.depth },
            )
            val html = doc.body()
            for (o in doc.outline) assertTrue("id=\"${o.anchor}\"" in html, "${o.anchor} 가 본문에 없다")
            assertTrue(Regex("<h1 id=\"h-0-1\"[^>]*><span class=\"mk\">1\\.</span>").containsMatchIn(html), html)
            assertTrue("<h2 " in html)
        }
    }

    @Test
    fun 긴_구역은_조각으로_나뉘고_번호가_조각을_넘어_이어진다() {
        // 번호 문단(번호 정의 2, `(^1)` = ①②③…) 30 개를 조각 셋 이상으로 자른다.
        val paras = (1..30).map { p("항목 $it", paraPr = 4) }.toTypedArray()
        val options = HwpxOptions(chunk = HancomChunkPolicy(softChars = 20, softBlocks = 10, hardChars = 40, hardBlocks = 10))
        Hwpx.doc(TinyHwpx().section(sec(*paras)), options).use { doc ->
            assertTrue(doc.parts.size >= 3, "부분 ${doc.parts.size}")
            val all = doc.parts.indices.flatMap { markers(doc.body(it)) }
            assertEquals((1..20).map { "(" + (0x2460 + it - 1).toChar() + ")" } + (21..30).map { "(" + (0x3251 + it - 21).toChar() + ")" }, all)
            // 조각 경계에서 글을 잃거나 겹치지 않는다.
            val texts = doc.parts.indices.flatMap { paragraphs(doc.body(it)) }
            assertEquals((1..30).map { "항목 $it" }, texts)
        }
    }

    @Test
    fun 다른_조각의_책갈피로_가는_링크는_그_조각의_경로와_조각을_가리킨다() {
        val link = """<hp:p paraPrIDRef="0" styleIDRef="0"><hp:run charPrIDRef="0"><hp:ctrl><hp:fieldBegin id="7" type="HYPERLINK" name=""><hp:parameters cnt="1"><hp:stringParam name="Command">?끝 자리;0;0;0;</hp:stringParam></hp:parameters></hp:fieldBegin></hp:ctrl><hp:t>끝으로 가기</hp:t><hp:ctrl><hp:fieldEnd beginIDRef="7"/></hp:ctrl></hp:run></hp:p>"""
        val mark = """<hp:p paraPrIDRef="0" styleIDRef="0"><hp:run charPrIDRef="0"><hp:ctrl><hp:bookmark name="끝 자리"/></hp:ctrl><hp:t>여기가 끝</hp:t></hp:run></hp:p>"""
        val filler = (1..20).map { p("채움 $it") }.toTypedArray()
        val options = HwpxOptions(chunk = HancomChunkPolicy(softChars = 10, softBlocks = 5, hardChars = 20, hardBlocks = 5))
        Hwpx.doc(TinyHwpx().section(sec(link, *filler, mark)), options).use { doc ->
            val last = doc.parts.size - 1
            assertTrue(last > 0)
            val id = HwpxIds.bookmark("끝 자리")
            assertTrue("<a href=\"~part-$last.html#$id\">끝으로 가기</a>" in doc.body(0), doc.body(0))
            assertTrue("<span id=\"$id\"></span>" in doc.body(last), doc.body(last))
        }
    }

    @Test
    fun 판별기는_저장된_mimetype_을_맡고_EPUB_과_OOXML_은_맡지_않는다() {
        val hwpx = TinyHwpx().section(sec(p("본문"))).build()
        assertEquals(FormatId.HWPX, probe(hwpx, null))

        // mimetype 이 압축됐어도 `Contents/content.hpf` 가 있으면 맡는다.
        val compressed = rezip(hwpx, storeMimetype = false) { true }
        assertEquals(FormatId.HWPX, probe(compressed, names(compressed)))

        val epub = zip(listOf("mimetype" to "application/epub+zip", "META-INF/container.xml" to "<container/>", "OEBPS/content.opf" to "<package/>"))
        assertNull(probe(epub, names(epub)))
        val docx = zip(listOf("[Content_Types].xml" to "<Types/>", "word/document.xml" to "<document/>"))
        assertNull(probe(docx, names(docx)))
        assertNull(probe("<!DOCTYPE html>".toByteArray(), null))
    }

    // ---- 짜개 ----

    private fun probe(bytes: ByteArray, names: List<String>?): FormatId? =
        HwpxProbe.probe(ProbeContext(ByteArrayDocumentSource(bytes, "x.hwpx"), "hwpx", bytes.copyOf(minOf(64, bytes.size)), lazy { names }))

    private fun names(bytes: ByteArray): List<String> {
        val out = ArrayList<String>()
        java.util.zip.ZipInputStream(bytes.inputStream()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                out.add(e.name)
            }
        }
        return out
    }

    private fun zip(entries: List<Pair<String, String>>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            for ((n, d) in entries) {
                z.putNextEntry(ZipEntry(n))
                z.write(d.toByteArray())
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /** 항목을 거르거나 `mimetype` 을 압축해 다시 싼다. */
    private fun rezip(bytes: ByteArray, storeMimetype: Boolean = true, keep: (String) -> Boolean): ByteArray {
        val entries = ArrayList<Pair<String, ByteArray>>()
        java.util.zip.ZipInputStream(bytes.inputStream()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (keep(e.name)) entries.add(e.name to z.readBytes())
            }
        }
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            for ((n, d) in entries) {
                val e = ZipEntry(n)
                if (n == "mimetype" && storeMimetype) {
                    e.method = ZipEntry.STORED
                    e.size = d.size.toLong()
                    e.compressedSize = d.size.toLong()
                    e.crc = java.util.zip.CRC32().apply { update(d) }.value
                }
                z.putNextEntry(e)
                z.write(d)
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun 같은_이름의_항목이_둘이면_먼저_것을_쓰고_알린다() {
        val bytes = TinyHwpx().section(sec(p("먼저 것"))).build()
        val entries = ArrayList<Pair<String, ByteArray>>()
        java.util.zip.ZipInputStream(bytes.inputStream()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                entries.add(e.name to z.readBytes())
            }
        }
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            for ((n, d) in entries) {
                z.putNextEntry(ZipEntry(n))
                z.write(d)
                z.closeEntry()
            }
            z.putNextEntry(ZipEntry("contents/SECTION0.xml"))
            z.write(sec(p("나중 것")).toByteArray())
            z.closeEntry()
        }
        Hwpx.doc(out.toByteArray()).use { doc ->
            assertTrue("먼저 것" in doc.body())
            assertFalse("나중 것" in doc.body())
            assertTrue(doc.warnings.any { it.code == FlowWarnings.DUPLICATE_PART }, doc.warnings.toString())
        }
    }
}
