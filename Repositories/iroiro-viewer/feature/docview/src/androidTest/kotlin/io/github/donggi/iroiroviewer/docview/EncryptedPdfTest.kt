package io.github.donggi.iroiroviewer.docview

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import io.github.donggi.iroiroviewer.docview.pdf.PdfDocument
import io.github.donggi.iroiroviewer.docview.pdf.PdfEngine
import io.github.donggi.iroiroviewer.docview.pdf.PdfOpener
import io.github.donggi.iroiroviewer.docview.pdf.crypt.FileChannelSource
import io.github.donggi.iroiroviewer.docview.pdf.crypt.MemfdSink
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfDecryptor
import io.github.donggi.iroiroviewer.format.FileDocumentSource
import io.github.donggi.iroiroviewer.format.OpenFailure
import io.github.donggi.iroiroviewer.format.OpenOutcome
import io.github.donggi.iroiroviewer.format.OpenedDocument
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileInputStream

/**
 * 암호 PDF 를 **실제 pdfium 으로** 연다.
 *
 * ## 판정은 화소로 한다
 *
 * 표본(`src/test/resources/pdfcrypt/`, 자산으로 실린다)은 pypdf 와 qpdf 가 잠갔고 평문
 * 원본(`plain.pdf`)이 함께 있다. 암호를 넣어 연 문서의 쪽을 그려 **평문 원본을 그린 것과
 * 화소 단위로 같은지** 본다(`Bitmap.sameAs`). 한 바이트만 어긋나게 풀려도 내용 스트림의
 * Flate 가 깨져 쪽이 비거나 그림이 사라진다.
 *
 * ## 두 기기에서 서로 다른 길을 탄다
 *
 * * **API 31**(Android_12_Phone) — 플랫폼이 암호를 받지 않는다. 우리 복호화기가 풀어
 *   메모리 파일(memfd)에 담고 그것을 연다.
 * * **API 35**(Android_15_Tablet) — 플랫폼이 암호를 받는다(`LoadParams`).
 *
 * 그래서 [메모리_파일_경로가_이_기기에서_돈다] 는 API 와 무관하게 우리 길을 직접 탄다 —
 * 35 이상에서 `PdfOpener` 만 보면 우리 길은 한 번도 돌지 않는다.
 */
class EncryptedPdfTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var work: File

    /** 사용자 암호가 걸린 표본과 그 암호. */
    private val locked = mapOf(
        "pypdf-rc4-40.pdf" to USER,
        "pypdf-rc4-128.pdf" to USER,
        "pypdf-aes-128.pdf" to USER,
        "pypdf-aes-256-r5.pdf" to USER,
        "pypdf-aes-256.pdf" to USER,
        "pypdf-aes-256-ko.pdf" to USER_KO,
        "qpdf-r2.pdf" to USER,
        "qpdf-r3.pdf" to USER,
        "qpdf-r4-aes.pdf" to USER,
        "qpdf-r4-rc4.pdf" to USER,
        "qpdf-r6.pdf" to USER,
        "qpdf-r6-objstm.pdf" to USER,
        "qpdf-r4-objstm.pdf" to USER,
        "qpdf-r4-cleartext-meta.pdf" to USER,
        "qpdf-r6-ko.pdf" to USER_KO,
        "qpdf-r4-linear.pdf" to USER,
        "qpdf-r6-linear-objstm.pdf" to USER,
    )

    @Before
    fun setUp() {
        work = File(context.getExternalFilesDir(null), "pdfcrypt-${System.nanoTime()}")
        work.mkdirs()
    }

    @After
    fun tearDown() {
        work.deleteRecursively()
    }

    /** 시험 APK 의 자산을 앱 전용 폴더에 꺼낸다. pdfium 은 파일 서술자를 요구한다. */
    private fun sample(name: String): File = File(work, name).also { out ->
        instrumentation.context.assets.open("pdfcrypt/$name").use { input ->
            out.outputStream().use { input.copyTo(it) }
        }
    }

    /** 연다. 성공이면 문서를, 실패면 이유를 준다. 성공한 문서는 부르는 쪽이 닫는다. */
    private fun open(file: File, password: String?): Pair<PdfDocument?, OpenFailure?> = runBlocking {
        var opened: OpenedDocument? = null
        try {
            when (val outcome = PdfOpener(password?.toCharArray()).open(FileDocumentSource(file)) { opened = it }) {
                is OpenOutcome.Success -> {
                    opened = null
                    (outcome.document as PdfDocument) to null
                }
                is OpenOutcome.Failed -> null to outcome.failure
            }
        } finally {
            opened?.close()
        }
    }

    /** 쪽 하나를 1pt = 1px 로 그린다. */
    private fun draw(doc: PdfDocument, ordinal: Int): Bitmap = runBlocking {
        val size = doc.sizeOf(ordinal)!!
        doc.render(ordinal, PdfEngine.Spec(size[0], size[1], 1.0))!!
    }

    private val plainPages: List<Bitmap> by lazy {
        val (doc, failure) = open(sample("plain.pdf"), null)
        assertEquals(null, failure)
        doc!!.use { d -> (0 until d.pageCount).map { draw(d, it) } }
    }

    private fun assertSameAsPlain(name: String, doc: PdfDocument) {
        assertEquals("$name: 쪽 수", plainPages.size, doc.pageCount)
        for (i in 0 until doc.pageCount) {
            assertTrue("$name: ${i + 1}쪽의 화소가 평문과 다르다", draw(doc, i).sameAs(plainPages[i]))
        }
    }

    @Test
    fun 암호를_넣지_않으면_묻는다() {
        for (name in locked.keys) {
            val (doc, failure) = open(sample(name), null)
            doc?.close()
            assertTrue("$name: 암호를 묻지 않았다 — $failure", failure is OpenFailure.PasswordRequired)
            // 처음 여는 것이므로 '틀렸다' 가 아니다.
            assertFalse("$name: 넣지도 않은 암호가 틀렸다고 한다", (failure as OpenFailure.PasswordRequired).wrongPassword)
        }
    }

    @Test
    fun 소유자_암호만_걸린_문서는_묻지_않고_열린다() {
        // 여기서 암호를 물으면 사용자는 있지도 않은 암호를 요구받는다.
        val (doc, failure) = open(sample("qpdf-r6-owner-only.pdf"), null)
        assertEquals(null, failure)
        doc!!.use { assertSameAsPlain("owner-only", it) }
    }

    @Test
    fun 맞는_암호로_열면_평문과_화소가_같다() {
        for ((name, password) in locked) {
            val (doc, failure) = open(sample(name), password)
            assertEquals("$name: 열지 못했다", null, failure)
            doc!!.use { assertSameAsPlain(name, it) }
        }
    }

    @Test
    fun 소유자_암호로도_열린다() {
        for (name in listOf("pypdf-rc4-40.pdf", "qpdf-r4-aes.pdf", "qpdf-r6.pdf", "pypdf-aes-256-r5.pdf")) {
            val (doc, failure) = open(sample(name), OWNER)
            assertEquals("$name: 소유자 암호로 열지 못했다", null, failure)
            doc!!.use { assertSameAsPlain(name, it) }
        }
    }

    @Test
    fun 틀린_암호는_틀렸다고_말한다() {
        for (name in listOf("pypdf-rc4-40.pdf", "qpdf-r4-aes.pdf", "qpdf-r6.pdf", "qpdf-r6-ko.pdf")) {
            val (doc, failure) = open(sample(name), "iroiro!")
            doc?.close()
            assertTrue("$name: $failure", failure is OpenFailure.PasswordRequired)
            assertTrue("$name: 틀렸다고 하지 않는다", (failure as OpenFailure.PasswordRequired).wrongPassword)
        }
    }

    @Test
    fun 메모리_파일_경로가_이_기기에서_돈다() = runBlocking {
        // API 35 이상에서는 `PdfOpener` 가 플랫폼 길을 타므로 이 길을 직접 부른다.
        // memfd 가 앱 프로세스(SELinux untrusted_app)에서 만들어지는가, pdfium 이 그것을
        // 읽는가 — 둘 다 JVM 시험이 답할 수 없다.
        for (name in listOf("qpdf-r6-linear-objstm.pdf", "pypdf-rc4-40.pdf", "qpdf-r4-cleartext-meta.pdf")) {
            val file = sample(name)
            val descriptor = FileInputStream(file).channel.use { channel ->
                MemfdSink.create().use { sink ->
                    val r = PdfDecryptor.decrypt(FileChannelSource(channel), USER.toCharArray(), sink, 64L shl 20)
                    assertEquals(name, PdfDecryptor.Result.Ok, r)
                    sink.descriptor()
                }
            }
            val opened = PdfEngine.open(descriptor)
            assertTrue("$name: $opened", opened is PdfEngine.Opened.Ok)
            val ok = opened as PdfEngine.Opened.Ok
            PdfDocument(ok.renderer, ok.pageCount).use { assertSameAsPlain("memfd:$name", it) }
        }
        Unit
    }

    @Test
    fun 플랫폼이_암호를_직접_받는다() = runBlocking {
        // API 35 에서 `PdfOpener` 는 플랫폼이 거절하면 우리 길로 한 번 더 해 본다. 그러면
        // 위 시험들은 **플랫폼 길이 죽어 있어도** 통과한다. 플랫폼 길만 따로 태운다.
        org.junit.Assume.assumeTrue("API 35 이상에서만", android.os.Build.VERSION.SDK_INT >= 35)
        for ((name, password) in locked) {
            val opened = PdfEngine.openWithPassword(sample(name), password.toCharArray())
            assertTrue("$name: 플랫폼이 암호를 받지 않았다 — $opened", opened is PdfEngine.Opened.Ok)
            val ok = opened as PdfEngine.Opened.Ok
            PdfDocument(ok.renderer, ok.pageCount).use { assertSameAsPlain("platform:$name", it) }
        }
        Unit
    }

    @Test
    fun 인증서로_잠긴_문서는_묻지_않는다() {
        // 공개 키 처리기는 암호가 아니라 받는 사람의 인증서를 요구한다. 물어도 소용이 없다.
        val file = File(work, "pubsec.pdf").also {
            it.writeBytes(TinyPdf.withEncrypt("<</Filter/Adobe.PubSec/SubFilter/adbe.pkcs7.s5/V 4/R 4>>"))
        }
        val (doc, failure) = open(file, null)
        doc?.close()
        assertTrue("물어도 소용없는 암호를 묻는다: $failure", failure is OpenFailure.Encrypted)
    }

    private companion object {
        const val USER = "iroiro"
        const val OWNER = "owner-pw"
        const val USER_KO = "비밀번호"
    }
}
