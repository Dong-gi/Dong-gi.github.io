package io.github.donggi.iroiroviewer.format.opc

import io.github.donggi.iroiroviewer.format.CorruptFormatException
import io.github.donggi.iroiroviewer.format.FlowKind
import io.github.donggi.iroiroviewer.format.FlowOutline
import io.github.donggi.iroiroviewer.format.FlowPart
import io.github.donggi.iroiroviewer.format.FormatId
import io.github.donggi.iroiroviewer.format.OpenFailure
import io.github.donggi.iroiroviewer.format.ProbeContext
import io.github.donggi.iroiroviewer.format.toOpenFailure
import io.github.donggi.iroiroviewer.format.html.HtmlWriter
import io.github.donggi.iroiroviewer.safety.ParseLimits
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** OPC 겉 — 부분 이름·관계·콘텐츠 형식·종류 판정·판별기, 그리고 흐름 문서 바탕의 약속. */
class OpcPackageTest {

    private fun docxPackage() = TinyOoxml()
        .xml("word/document.xml", TinyOoxml.CT_DOCX_MAIN, "<w:document xmlns:w=\"${TinyOoxml.NS_W}\"/>")
        .bytes("word/media/Image1.PNG", null, byteArrayOf(1, 2, 3))
        .bytes("word/media/pic.emf", null, byteArrayOf(9))
        .rel(null, "rId1", TinyOoxml.REL_OFFICE_DOCUMENT, "word/document.xml")
        .rel("word/document.xml", "rId2", TinyOoxml.REL_IMAGE, "media/image1.png")
        .rel("word/document.xml", "rId3", TinyOoxml.REL_HYPERLINK, "https://example.com/x", external = true)
        .rel("word/document.xml", "rId4", TinyOoxml.REL_IMAGE, "../../escape.png")
        .rel("word/document.xml", "rId5", TinyOoxml.REL_IMAGE, "/word/media/pic.emf")

    @Test
    fun 관계의_대상을_부분_이름으로_푼다() {
        OpcPackage.open(docxPackage().source()).use { pkg ->
            assertEquals("word/document.xml", pkg.mainDocument)
            val img = pkg.relationship("word/document.xml", "rId2")!!
            assertEquals("word/media/image1.png", img.target)
            assertEquals("image", img.typeName)
            // **대소문자를 가리지 않는다** — 관계는 소문자, 항목은 대문자로 적혀 있다.
            assertTrue(pkg.has(img.target))
            assertEquals("word/media/Image1.PNG", pkg.canonical(img.target))
            // 바깥 대상은 적힌 그대로, 뿌리를 벗어나는 대상은 버린다.
            assertTrue(pkg.relationship("word/document.xml", "rId3")!!.external)
            assertNull(pkg.relationship("word/document.xml", "rId4"))
            assertEquals("word/media/pic.emf", pkg.relationship("word/document.xml", "rId5")!!.target)
        }
    }

    @Test
    fun 이름을_풀고_가른다() {
        assertEquals("word/media/한 글.png", OpcNames.resolve("word/document.xml", "media/%ED%95%9C%20%EA%B8%80.png"))
        assertEquals("a+b.png", OpcNames.resolve(null, "a+b.png"))
        assertEquals("customXml/item1.xml", OpcNames.resolve("word/document.xml", "../customXml/item1.xml"))
        assertEquals("word/x.xml", OpcNames.resolve("word/document.xml", "x.xml#frag"))
        assertNull(OpcNames.resolve("word/document.xml", "../../x.xml"))
        assertEquals("word/", OpcNames.dirOf("/word/document.xml"))
        // 잘못된 퍼센트는 글자 그대로.
        assertEquals("100%.png", OpcNames.percentDecode("100%.png"))
    }

    @Test
    fun 콘텐츠_형식은_덮어쓰기가_이기고_확장자로_기본값() {
        OpcPackage.open(docxPackage().source()).use { pkg ->
            assertEquals(TinyOoxml.CT_DOCX_MAIN, pkg.contentType("/word/document.xml"))
            assertEquals("image/png", pkg.contentType("word/media/image1.png"))
            assertEquals(OoxmlKind.DOCX, OoxmlKinds.of(pkg))
        }
    }

    @Test
    fun 콘텐츠_형식표가_없어도_연다() {
        val src = docxPackage().withoutContentTypes().source()
        OpcPackage.open(src).use { pkg ->
            assertTrue(pkg.warnings.any { it.code == OpcPackage.WARN_NO_CONTENT_TYPES })
            // 형식이 없으면 이름으로 짐작한다.
            assertEquals(OoxmlKind.DOCX, OoxmlKinds.of(pkg))
        }
    }

    @Test
    fun 판별기는_엔트리_이름과_CFB_의_확장자로_가른다() {
        val bytes = docxPackage().build()
        val zipNames = listOf("[Content_Types].xml", "word/document.xml")
        fun ctx(head: ByteArray, ext: String, names: List<String>?) =
            ProbeContext(io.github.donggi.iroiroviewer.format.ByteArrayDocumentSource(bytes, "x.$ext"), ext, head, lazy { names })
        assertEquals(FormatId.DOCX, OoxmlProbe.probe(ctx(bytes.copyOf(64), "zip", zipNames)))
        // [Content_Types].xml 이 없는 ZIP 은 맡지 않는다(그냥 압축 파일이다).
        assertNull(OoxmlProbe.probe(ctx(bytes.copyOf(64), "docx", listOf("word/document.xml"))))
        val cfb = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte())
        assertEquals(FormatId.XLSX, OoxmlProbe.probe(ctx(cfb, "xlsx", null)))
        assertEquals(FormatId.LEGACY_OFFICE, OoxmlProbe.probe(ctx(cfb, "doc", null)))
        // HWP 5.0 도 CFB 다 — 맡지 않는다(13단계).
        assertNull(OoxmlProbe.probe(ctx(cfb, "hwp", null)))
        // 이름을 못 읽은 ZIP(중앙 디렉터리가 깨졌다)은 오피스 확장자면 맡는다 — 맡지 않으면 앱이 '다루지 않는
        // 문서' 라고 말한다. 여는이가 '깨진 파일' 로 끝낸다. 오피스 확장자가 아니면 여전히 맡지 않는다.
        assertEquals(FormatId.DOCX, OoxmlProbe.probe(ctx(bytes.copyOf(64), "docx", null)))
        assertEquals(FormatId.PPTX, OoxmlProbe.probe(ctx(bytes.copyOf(64), "ppsx", null)))
        assertNull(OoxmlProbe.probe(ctx(bytes.copyOf(64), "zip", null)))
    }

    /**
     * 이진 통합 문서(xlsb)는 OPC 겉만 같다 — 본문 형식에 `ms-excel` 이 들었다고 xlsx 변환기에 넘기면 XML 이 아닌
     * `workbook.bin` 을 읽다 '깨진 파일' 로 끝난다. 여는이가 '다루지 않는 갈래' 라고 말해야 한다(암호 걸린 실세계
     * xlsb 를 풀어 여는이에 곧바로 넣어 잡았다).
     */
    @Test
    fun 이진_통합_문서는_변환기에_넘기지_않고_다루지_않는다고_말한다() = kotlinx.coroutines.test.runTest {
        val xlsb = TinyOoxml()
            .bytes("xl/workbook.bin", "application/vnd.ms-excel.sheet.binary.macroEnabled.main", byteArrayOf(0x83.toByte(), 1, 0, 0x80.toByte(), 1, 0))
            .rel(null, "rId1", TinyOoxml.REL_OFFICE_DOCUMENT, "xl/workbook.bin")
        OpcPackage.open(xlsb.source("x.xlsx")).use { pkg -> assertNull(OoxmlKinds.of(pkg)) }
        val outcome = OoxmlOpener(null) { _, _, _, _ -> error("변환기까지 가지 않는다") }.open(xlsb.source("x.xlsx"))
        val failure = (outcome as io.github.donggi.iroiroviewer.format.OpenOutcome.Failed).failure
        assertTrue(failure is OpenFailure.Unsupported, failure.toString())
    }

    /**
     * 겉(중앙 디렉터리)이 깨진 ZIP 은 **깨진 파일**이지 입출력 실패가 아니다. commons-compress 는 끝 레코드가
     * 없으면 `ZipException`, 중앙 디렉터리 자리가 엉뚱하면 맨 `IOException` 을 던지는데, 둘 다 그대로 두면
     * '입출력이 실패했다' 가 된다(Tika 의 잘린 docx·POI 의 퍼저 표본이 잡았다).
     */
    @Test
    fun 겉이_깨진_ZIP_은_입출력_실패가_아니라_깨진_파일이다() = kotlinx.coroutines.test.runTest {
        val bytes = docxPackage().build()
        // 끝 레코드까지 잘렸다.
        val truncated = bytes.copyOf(bytes.size * 2 / 3)
        // 끝 레코드는 있는데 그것이 가리키는 중앙 디렉터리의 서명이 지워졌다.
        val eocd = (bytes.size - 22 downTo 0).first {
            bytes[it] == 0x50.toByte() && bytes[it + 1] == 0x4B.toByte() && bytes[it + 2] == 0x05.toByte() && bytes[it + 3] == 0x06.toByte()
        }
        val cdOffset = (0 until 4).sumOf { (bytes[eocd + 16 + it].toInt() and 0xFF) shl (8 * it) }
        val noCentral = bytes.copyOf().also { b -> for (i in 0 until 4) b[cdOffset + i] = 0 }
        for ((label, broken) in listOf("잘림" to truncated, "중앙 디렉터리 없음" to noCentral)) {
            val source = io.github.donggi.iroiroviewer.format.ByteArrayDocumentSource(broken, "x.docx")
            val thrown = assertNotNull(runCatching { OpcPackage.open(source).close() }.exceptionOrNull(), label)
            assertEquals(OpenFailure.Corrupt(OpcPackage.CORRUPT_ZIP), thrown.toOpenFailure(), label)
            val outcome = OoxmlOpener(null) { _, _, _, _ -> error("변환기까지 가지 않는다") }.open(source)
            assertEquals(OpenFailure.Corrupt(OpcPackage.CORRUPT_ZIP), (outcome as io.github.donggi.iroiroviewer.format.OpenOutcome.Failed).failure, label)
        }
    }

    /**
     * 위의 '깨진 파일' 옮기기가 **다른 뜻을 삼키지 않는다.** 상한 예외(`ParseLimitExceededException`)도 `IOException`
     * 이라, 옮기기의 제외 목록에서 빠지면 항목이 너무 많은 문서가 '너무 크다' 대신 '깨진 파일' 이 된다. 인터럽트와
     * 사라진 파일도 그 예외 그대로 나가야 한다. 제외 목록은 깨진 ZIP 수정이 새로 만든 갈림길이라 따로 박는다.
     */
    @Test
    fun 깨진_ZIP_옮기기는_상한과_중단과_사라진_파일을_삼키지_않는다() {
        val bytes = docxPackage().build()
        val tooMany = runCatching {
            OpcPackage.open(io.github.donggi.iroiroviewer.format.ByteArrayDocumentSource(bytes, "x.docx"), ParseLimits(maxEntries = 2)).close()
        }.exceptionOrNull()
        assertTrue(tooMany is io.github.donggi.iroiroviewer.safety.ParseLimitExceededException, tooMany.toString())
        assertTrue(tooMany.toOpenFailure() is OpenFailure.TooLarge, tooMany.toOpenFailure().toString())
        val cases = listOf(java.io.InterruptedIOException("취소"), java.io.FileNotFoundException("없다"))
        for (cause in cases) {
            // 채널을 열려는 순간 실패하는 원본 — 저장소의 문제이지 파일 형식의 문제가 아니다.
            val source = object : io.github.donggi.iroiroviewer.format.DocumentSource {
                override val displayName = "x.docx"
                override val length = bytes.size.toLong()
                override fun openStream() = bytes.inputStream()
                override fun openChannel(): java.nio.channels.SeekableByteChannel = throw cause
            }
            val thrown = runCatching { OpcPackage.open(source).close() }.exceptionOrNull()
            assertTrue(thrown === cause, "${cause::class.java.simpleName} 이 ${thrown} 로 바뀌었다")
        }
        assertEquals(OpenFailure.Timeout("읽는 중에 중단되었다"), cases[0].toOpenFailure())
    }

    /** 바탕이 지키는 약속 — 그림만 내주고, 위생을 지나고, 닫힌 뒤에는 null. */
    private class Dummy(pkg: OpcPackage, private val body: String) : OpcFlowDocument(pkg, ParseLimits.DEFAULT, FormatId.DOCX) {
        override val title = ""
        override val kind = FlowKind.DOCUMENT
        override val parts = listOf(FlowPart(partPath(0), ""), FlowPart(partPath(1), ""))
        override val outline = emptyList<FlowOutline>()
        override val css = "p{margin:0}"
        override fun renderBody(index: Int): String = if (index == 1) error("깨진 부분") else body
    }

    @Test
    fun 흐름_문서_바탕이_위생과_자원을_지킨다() {
        val w = HtmlWriter()
        w.start("p").text("<script>alert(1)</script>").end("p")
        w.void("img", "src" to "word/media/image1.png")
        w.void("img", "src" to "word/media/pic.emf")
        w.void("img", "src" to "https://tracker/x.gif")
        w.start("a", "href" to "~part-1.html#h2").text("다음").end("a")
        val body = w.toString() + "<script>evil()</script>"
        val doc = Dummy(OpcPackage.open(docxPackage().source()), body)
        doc.use {
            val html = it.partHtml(0)!!
            assertFalse("<script" in html, html)
            assertTrue("&lt;script&gt;" in html)
            assertTrue("src=\"word/media/Image1.PNG\"" in html, html)
            assertFalse("pic.emf" in html)
            assertFalse("tracker" in html)
            assertTrue("href=\"~part-1.html#h2\"" in html)
            assertTrue(it.unsupported.snapshot().isNotEmpty())
            // 부분 하나의 실패는 그 부분으로 끝난다.
            assertNotNull(it.partHtml(1))
            assertTrue(it.warnings.any { w2 -> w2.code == io.github.donggi.iroiroviewer.format.FlowWarnings.PART_FAILED })
            // 그림만 내준다. EMF 는 화면이 못 그린다.
            assertNotNull(it.openResource("word/media/image1.png"))
            assertNull(it.openResource("word/media/pic.emf"))
            assertNull(it.openResource("word/document.xml"))
            assertEquals("image/png", it.mediaTypeOf("word/media/image1.png"))
        }
        assertNull(doc.partHtml(0))
        assertNull(doc.openResource("word/media/image1.png"))
    }

    @Test
    fun 공백이나_샵이_든_그림_이름도_사라지지_않는다() {
        // 위생기가 URL 속성의 공백을 지우므로(`Urls.rewrite`) 이름을 그대로 적으면 `myimage.png` 가
        // 되어 그림이 사라진다. `#` 는 조각으로 잘린다. 변환기가 `toUrl` 을 쓰든 빠뜨리든 한 모양으로 모인다.
        val pkg = TinyOoxml()
            .xml("word/document.xml", TinyOoxml.CT_DOCX_MAIN, "<w:document xmlns:w=\"${TinyOoxml.NS_W}\"/>")
            .bytes("word/media/my image#1.png", null, byteArrayOf(1))
            .bytes("word/media/한글 그림.png", null, byteArrayOf(2))
            .rel(null, "rId1", TinyOoxml.REL_OFFICE_DOCUMENT, "word/document.xml")
        assertEquals("word/media/my%20image%231.png", OpcNames.toUrl("/word/media/my image#1.png"))
        assertEquals("word/media/%ED%95%9C%EA%B8%80%20%EA%B7%B8%EB%A6%BC.png", OpcNames.toUrl("word/media/한글 그림.png"))
        val w = HtmlWriter()
        w.void("img", "src" to OpcNames.toUrl("word/media/my image#1.png"))
        w.void("img", "src" to "word/media/%ED%95%9C%EA%B8%80%20%EA%B7%B8%EB%A6%BC.png")
        Dummy(OpcPackage.open(pkg.source()), w.toString()).use {
            val html = it.partHtml(0)!!
            assertTrue("src=\"word/media/my%20image%231.png\"" in html, html)
            assertTrue("src=\"word/media/%ED%95%9C%EA%B8%80%20%EA%B7%B8%EB%A6%BC.png\"" in html, html)
            // 화면이 청하는 경로는 디코딩된 모양이다(`WebHost.pathOf` 가 `Uri.path` 를 준다).
            assertNotNull(it.openResource("word/media/my image#1.png"))
            assertNotNull(it.openResource("word/media/한글 그림.png"))
        }
    }

    @Test
    fun 매크로가_있으면_알리고_돌리지_않는다() {
        val pkg = docxPackage().bytes("word/vbaProject.bin", "application/vnd.ms-office.vbaProject", byteArrayOf(0))
        Dummy(OpcPackage.open(pkg.source()), "<p>x</p>").use {
            assertTrue(it.warnings.any { w -> w.code == io.github.donggi.iroiroviewer.format.FlowWarnings.MACROS })
            assertEquals(1, it.unsupported.snapshot()[io.github.donggi.iroiroviewer.format.UnsupportedFeatures.MACRO])
        }
        Dummy(OpcPackage.open(docxPackage().source()), "<p>x</p>").use {
            assertFalse(it.warnings.any { w -> w.code == io.github.donggi.iroiroviewer.format.FlowWarnings.MACROS })
        }
    }

    @Test
    fun 위생기의_입력_상한을_넘는_본문은_그_부분의_너무_크다로_끝난다() {
        // 변환기의 본문이 `maxXmlBytes` 를 넘으면 위생기가 던진다. 바탕이 잡지 않으면 화면의 자원
        // 제공자가 404 로 삼켜 빈 화면에 아무 말도 없다.
        val limits = ParseLimits(maxXmlBytes = 1024)
        val big = "<p>" + "가".repeat(4000) + "</p>"
        val doc = object : OpcFlowDocument(OpcPackage.open(docxPackage().source()), limits, FormatId.DOCX) {
            override val title = ""
            override val kind = FlowKind.SLIDES
            override val parts = listOf(FlowPart(partPath(0), ""))
            override val outline = emptyList<FlowOutline>()
            override val css = ""
            override fun renderBody(index: Int) = big
        }
        doc.use {
            assertNotNull(it.partHtml(0))
            // 이름 없는 부분은 번호로 알린다 — 빈 문자열이면 부분끼리의 경고가 하나로 합쳐진다.
            assertTrue(it.warnings.any { w -> w.code == io.github.donggi.iroiroviewer.format.FlowWarnings.TRUNCATED && w.detail == io.github.donggi.iroiroviewer.format.FlowWarnings.PART_NUMBER_PREFIX + "1" }, it.warnings.toString())
        }
    }

    @Test
    fun 버린_것은_부분마다_한_번만_센다() {
        // 캐시는 셋이다. 네 부분을 두 바퀴 돌면 모든 부분이 한 번씩 밀려나 다시 그려진다.
        val doc = object : OpcFlowDocument(OpcPackage.open(docxPackage().source()), ParseLimits.DEFAULT, FormatId.PPTX) {
            override val title = ""
            override val kind = FlowKind.SLIDES
            override val parts = (0 until 4).map { FlowPart(partPath(it), "") }
            override val outline = emptyList<FlowOutline>()
            override val css = ""
            override fun renderBody(index: Int) = "<p>x</p><script>1</script>"
        }
        doc.use {
            repeat(2) { _ -> for (i in 0 until 4) assertNotNull(it.partHtml(i)) }
            assertEquals(4, it.unsupported.snapshot()[io.github.donggi.iroiroviewer.format.UnsupportedFeatures.SCRIPT])
        }
    }

    @Test
    fun 풀어_낸_평문은_닫을_때_지운다() {
        val bytes = docxPackage().build()
        val pkg = OpcPackage.open(io.github.donggi.iroiroviewer.format.ByteArrayDocumentSource(bytes, "x.docx"), wipeOnClose = bytes)
        assertTrue(bytes.any { it != 0.toByte() })
        pkg.close()
        assertTrue(bytes.all { it == 0.toByte() })
        // 여는 데 실패해도 지운다(ZIP 이 아니다).
        val junk = ByteArray(64) { 7 }
        runCatching { OpcPackage.open(io.github.donggi.iroiroviewer.format.ByteArrayDocumentSource(junk, "x.docx"), wipeOnClose = junk) }
        assertTrue(junk.all { it == 0.toByte() })
    }

    @Test
    fun 형식_오류는_입출력이_아니라_깨진_파일이다() {
        val f = CorruptFormatException("섹터 번호가 범위를 벗어났다").toOpenFailure()
        assertTrue(f is OpenFailure.Corrupt, f.toString())
    }

    @Test
    fun 상한에서_멈춘_뒤에_연_태그는_닫는_태그도_쓰지_않는다() {
        val w = HtmlWriter(maxChars = 20)
        w.start("div").text("0123456789012345678901")
        assertTrue(w.full)
        repeat(1000) { w.start("span").end("span") }
        w.closeAll()
        assertEquals("<div>0123456789012345678901</div>", w.toString())
    }

    @Test
    fun 쓰기는_글자를_이스케이프하고_상한에서_멈춘다() {
        val w = HtmlWriter(maxChars = 50)
        w.start("div", "title" to "\"><img src=x onerror=1>")
        repeat(20) { w.start("p").text("가나다라마바사").end("p") }
        assertTrue(w.full)
        w.end("div")
        val s = w.toString()
        assertFalse("<img" in s)
        assertTrue(s.endsWith("</div>"))
        assertEquals("&lt;a&gt;&amp;&quot;&#39;", HtmlWriter.escape("<a>&\"'"))
        // 짝 잃은 대리 문자는 버린다.
        assertEquals("ab", HtmlWriter.escape("a\uD800b"))
    }
}
