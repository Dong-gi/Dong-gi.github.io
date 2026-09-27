package io.github.donggi.iroiroviewer.format.opc

import io.github.donggi.iroiroviewer.format.ByteArrayDocumentSource
import io.github.donggi.iroiroviewer.format.FlowDocument
import io.github.donggi.iroiroviewer.format.FlowKind
import io.github.donggi.iroiroviewer.format.FlowOutline
import io.github.donggi.iroiroviewer.format.FlowPart
import io.github.donggi.iroiroviewer.format.FormatId
import io.github.donggi.iroiroviewer.format.OpenFailure
import io.github.donggi.iroiroviewer.format.OpenOutcome
import io.github.donggi.iroiroviewer.format.OpenedDocument
import io.github.donggi.iroiroviewer.format.ParseWarning
import io.github.donggi.iroiroviewer.format.UnsupportedFeatures
import kotlinx.coroutines.test.runTest
import java.io.File
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 끝에서 끝까지 — `OoxmlOpener` 가 암호가 걸린 파일을 [OfficeCfb] 로 풀고, 풀린 바이트를 **메모리의 채널**로
 * `OpcPackage` 에 넣어, 패키지가 말하는 종류로 변환기를 부르는가. 변환기는 이 시험의 가짜다.
 */
class OoxmlOpenerCryptTest {

    private val dir = File(javaClass.classLoader!!.getResource("officecrypt/plain.docx")!!.toURI()).parentFile

    private fun source(name: String) = ByteArrayDocumentSource(File(dir, name).readBytes(), name)

    /** 패키지를 들고 닫기만 하는 가짜 흐름 문서. */
    private class FakeDocument(val pkg: OpcPackage) : FlowDocument {
        override val title = ""
        override val kind = FlowKind.DOCUMENT
        override val parts = listOf(FlowPart("~part-0.html", ""))
        override val outline = emptyList<FlowOutline>()
        override fun partHtml(index: Int): String? = null
        override fun openResource(path: String): InputStream? = null
        override fun mediaTypeOf(path: String): String? = null
        override val formatId = FormatId.DOCX
        override val warnings = emptyList<ParseWarning>()
        override val unsupported = UnsupportedFeatures()
        override fun close() = pkg.close()
    }

    private suspend fun openWith(name: String, password: String?): Triple<OpenOutcome, OoxmlKind?, List<String>> {
        var kind: OoxmlKind? = null
        var names = emptyList<String>()
        var opened: OpenedDocument? = null
        val opener = OoxmlOpener(password?.toCharArray()) { pkg, k, _, _ ->
            kind = k
            names = pkg.partNames.sorted()
            FakeDocument(pkg)
        }
        try {
            val outcome = opener.open(source(name)) { opened = it }
            return Triple(outcome, kind, names)
        } finally {
            opened?.close()
        }
    }

    @Test
    fun 암호가_걸린_docx_를_열면_DOCX_로_변환기를_부른다() = runTest {
        val (outcome, kind, names) = openWith("agile.docx", "pw-ascii-123")
        assertIs<OpenOutcome.Success>(outcome)
        assertEquals(OoxmlKind.DOCX, kind)
        assertTrue("word/document.xml" in names, names.toString())
    }

    @Test
    fun 암호가_걸린_xlsx_와_pptx_도_패키지가_말하는_종류로_연다() = runTest {
        val (x, xk, _) = openWith("agile-ko.xlsx", "암호123")
        assertIs<OpenOutcome.Success>(x)
        assertEquals(OoxmlKind.XLSX, xk)
        val (p, pk, _) = openWith("agile.pptx", "Slide Deck #7")
        assertIs<OpenOutcome.Success>(p)
        assertEquals(OoxmlKind.PPTX, pk)
        // Standard 로 잠근 것도 같은 길이다.
        val (s, sk, _) = openWith("standard-aes128.docx", "std-pass-128")
        assertIs<OpenOutcome.Success>(s)
        assertEquals(OoxmlKind.DOCX, sk)
    }

    @Test
    fun 암호가_없거나_틀리면_변환기를_부르지_않는다() = runTest {
        val (none, noneKind, _) = openWith("agile.docx", null)
        assertEquals(OpenFailure.PasswordRequired(OfficeCfb.PASSWORD, wrongPassword = false), (none as OpenOutcome.Failed).failure)
        assertEquals(null, noneKind)
        val (wrong, _, _) = openWith("agile.docx", "x")
        assertEquals(true, ((wrong as OpenOutcome.Failed).failure as OpenFailure.PasswordRequired).wrongPassword)
    }

    @Test
    fun 이전_형식과_IRM_은_여는이가_그대로_알린다() = runTest {
        assertIs<OpenFailure.LegacyFormat>((openWith("legacy-word.doc", null).first as OpenOutcome.Failed).failure)
        assertIs<OpenFailure.Encrypted>((openWith("irm-drm.docx", null).first as OpenOutcome.Failed).failure)
    }
}
