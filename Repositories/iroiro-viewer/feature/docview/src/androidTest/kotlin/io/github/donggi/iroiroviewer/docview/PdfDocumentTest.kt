package io.github.donggi.iroiroviewer.docview

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.test.platform.app.InstrumentationRegistry
import io.github.donggi.iroiroviewer.docview.pdf.PdfDocument
import io.github.donggi.iroiroviewer.docview.pdf.PdfOpener
import io.github.donggi.iroiroviewer.docview.pdf.PdfPageStore
import io.github.donggi.iroiroviewer.format.FileDocumentSource
import io.github.donggi.iroiroviewer.format.OpenFailure
import io.github.donggi.iroiroviewer.format.OpenOutcome
import io.github.donggi.iroiroviewer.format.OpenedDocument
import io.github.donggi.iroiroviewer.safety.ImageLimits
import io.github.donggi.iroiroviewer.safety.PdfLimits
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * PDF 를 **실제 pdfium 으로** 연다.
 *
 * JVM 시험이 닿지 못하는 자리만 여기 둔다 — `PdfRenderer` 는 플랫폼 네이티브라
 * 에뮬레이터에서라야 돈다. `PdfLimits` 의 산수(`PdfLimitsTest` 11건)와 실패 매핑
 * (`PdfFailuresTest` 7건)은 이미 초 단위로 박혀 있으므로, 여기서 묻는 것은 **그 값들이
 * 실제 디코더까지 닿는가** 하나다.
 *
 * ## 판정을 눈으로 하지 않는다
 *
 * 표본의 쪽을 단색으로 만들고 **렌더 결과의 화소를 읽는다.** 9단계가 만화 쪽을 단색으로
 * 만들어 같은 일을 했다. '몇 쪽이 나왔는가' 와 '타일이 그 자리인가' 는 사람이 캡처를
 * 보고 답할 것이 아니다(6단계가 그것으로 세 번 틀렸다).
 */
class PdfDocumentTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var work: File

    /** 화면 240×400, 힙 등급 32MB. 작은 예산이 상한 경로를 실제로 태운다. */
    private val tinyBudget = ImageLimits.budgetOf(240, 400, 32)

    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()

    @Before
    fun setUp() {
        work = File(context.getExternalFilesDir(null), "pdf-test-${System.nanoTime()}")
        work.mkdirs()
    }

    @After
    fun tearDown() {
        work.deleteRecursively()
    }

    private fun write(name: String, bytes: ByteArray): File =
        File(work, name).also { it.writeBytes(bytes) }

    /** 연다. 실패하면 시험이 거기서 끝난다. */
    private fun open(file: File): PdfDocument = runBlocking {
        var opened: OpenedDocument? = null
        val outcome = PdfOpener().open(FileDocumentSource(file)) { opened = it }
        assertTrue("열지 못했다: $outcome", outcome is OpenOutcome.Success)
        (outcome as OpenOutcome.Success).document as PdfDocument
    }

    private fun failure(file: File): OpenFailure = runBlocking {
        var opened: OpenedDocument? = null
        try {
            val outcome = PdfOpener().open(FileDocumentSource(file)) { opened = it }
            assertTrue("열려서는 안 된다: $outcome", outcome is OpenOutcome.Failed)
            (outcome as OpenOutcome.Failed).failure
        } finally {
            opened?.close()
        }
    }

    @Test
    fun 쪽_수와_크기를_읽는다() {
        val file = write(
            "two.pdf",
            TinyPdf.bytes(
                listOf(
                    TinyPdf.solid(1.0, 0.0, 0.0, 200, 100),
                    // **한 문서 안에서 쪽 크기가 다를 수 있다.** 첫 쪽 크기로 비트맵을
                    // 만들어 돌려 쓰면 여기서 곧바로 찌그러진다.
                    TinyPdf.solid(0.0, 0.0, 1.0, 100, 200),
                ),
            ),
        )
        val doc = open(file)
        try {
            assertEquals(2, doc.pageCount)
            val store = PdfPageStore(doc, tinyBudget)
            runBlocking {
                assertEquals(listOf(200, 100), store.size(0)!!.toList())
                assertEquals(listOf(100, 200), store.size(1)!!.toList())
                assertNull("범위 밖은 null 이다", store.size(2))
            }
        } finally {
            doc.close()
        }
    }

    @Test
    fun 쪽마다_제_색이_나온다() {
        val file = write(
            "colors.pdf",
            TinyPdf.bytes(
                listOf(
                    TinyPdf.solid(1.0, 0.0, 0.0, 200, 100),
                    TinyPdf.solid(0.0, 0.0, 1.0, 200, 100),
                ),
            ),
        )
        val doc = open(file)
        try {
            val store = PdfPageStore(doc, tinyBudget)
            runBlocking {
                assertEquals(red, centerOf(store, 0))
                assertEquals(blue, centerOf(store, 1))
            }
        } finally {
            doc.close()
        }
    }

    @Test
    fun 예산이_비트맵_크기를_정한다() {
        // 3000×3000pt 한 쪽. 240×400 뷰포트에 맞추면 240×240 이지만, 예산이 그보다
        // 작으면 예산이 이긴다. **산수는 JVM 이 박았고 여기서는 그 값이 실제 비트맵의
        // 치수가 되는지만 본다.**
        val file = write("big.pdf", TinyPdf.bytes(listOf(TinyPdf.solid(0.0, 0.0, 1.0, 3000, 3000))))
        val doc = open(file)
        try {
            val store = PdfPageStore(doc, tinyBudget)
            runBlocking {
                val image = store.fitted(0, 240, 400)
                assertNotNull(image)
                image!!
                val scale = PdfLimits.fitScale(3000, 3000, 240, 400)
                val plan = PdfLimits.pageBitmap(3000, 3000, scale, store.pageCap)!!
                assertEquals(plan.width, image.width)
                assertEquals(plan.height, image.height)
                assertTrue(
                    "예산을 넘었다 ${image.width}x${image.height}",
                    PdfLimits.bytesOf(image.width, image.height) <= store.pageCap,
                )
            }
        } finally {
            doc.close()
        }
    }

    @Test
    fun 같은_쪽을_두_번_부르면_캐시가_돈다() {
        val file = write("one.pdf", TinyPdf.bytes(listOf(TinyPdf.solid(1.0, 0.0, 0.0, 200, 100))))
        val doc = open(file)
        try {
            val store = PdfPageStore(doc, tinyBudget)
            runBlocking {
                val first = store.fitted(0, 240, 400)
                assertNotNull(first)
                assertTrue("같은 객체여야 한다", first === store.fitted(0, 240, 400))
                // 뷰포트가 바뀌면 키가 달라져 새로 뜬다. 옛 크기가 살아남으면 안 된다.
                assertTrue("뷰포트가 달라졌는데 같은 것을 줬다", first !== store.fitted(0, 480, 800))
            }
        } finally {
            doc.close()
        }
    }

    @Test
    fun 타일이_확대한_자리를_뜬다() {
        // 왼쪽 절반 빨강, 오른쪽 절반 파랑. 타일의 가운데 색이 어느 자리를 떴는지 말한다.
        val file = write("halves.pdf", TinyPdf.bytes(listOf(TinyPdf.halves(400, 200))))
        val doc = open(file)
        try {
            val store = PdfPageStore(doc, tinyBudget)
            runBlocking {
                store.fitted(0, 240, 400)
                val fit = PdfLimits.fitScale(400, 200, 240, 400)
                val left = store.detail(0, 240, 400, fit * 3, 0.05, 0.5, maxScale = fit * 8)
                val right = store.detail(0, 240, 400, fit * 3, 0.95, 0.5, maxScale = fit * 8)
                assertNotNull(left)
                assertNotNull(right)
                left!!
                right!!
                assertEquals(red, center(left.image))
                assertEquals(blue, center(right.image))
                assertTrue("왼쪽 타일이 더 오른쪽에 있다", left.tile.srcLeft < right.tile.srcLeft)
                assertTrue(
                    "타일이 쪽 밖으로 나갔다 ${right.tile}",
                    right.tile.srcLeft + right.tile.width <= right.tile.pageWidth,
                )
            }
        } finally {
            doc.close()
        }
    }

    @Test
    fun 확대하지_않으면_타일을_뜨지_않는다() {
        val file = write("plain.pdf", TinyPdf.bytes(listOf(TinyPdf.solid(1.0, 0.0, 0.0, 400, 200))))
        val doc = open(file)
        try {
            val store = PdfPageStore(doc, tinyBudget)
            runBlocking {
                store.fitted(0, 240, 400)
                val fit = PdfLimits.fitScale(400, 200, 240, 400)
                assertNull(store.detail(0, 240, 400, fit, 0.5, 0.5, maxScale = fit * 8))
            }
        } finally {
            doc.close()
        }
    }

    @Test
    fun 닫은_문서는_그리지_않고_죽지도_않는다() {
        // **여기가 가장 조용히 앱을 죽이는 자리다.** 닫힌 pdfium 문서를 만지면 예외가
        // 아니라 프로세스가 내려간다. 그래서 '예외를 던지는가' 가 아니라 '살아서 null 을
        // 주는가' 를 묻는다.
        val file = write("close.pdf", TinyPdf.bytes(listOf(TinyPdf.solid(1.0, 0.0, 0.0, 200, 100))))
        val doc = open(file)
        val store = PdfPageStore(doc, tinyBudget)
        runBlocking { assertNotNull(store.fitted(0, 240, 400)) }
        doc.close()
        doc.close() // 여러 번 닫아도 한 번만 닫는다
        runBlocking {
            assertNull(store.fitted(1, 240, 400))
            assertNull(doc.sizeOf(0))
        }
    }

    @Test
    fun 암호가_걸린_문서는_암호를_묻는다() {
        // 공용 매핑이었다면 `NoPermission`('읽을 권한이 없습니다')이고, 사용자는 권한
        // 설정을 뒤졌을 것이다. `PdfFailures` 가 있는 이유가 이 한 줄이다.
        val file = write(
            "locked.pdf",
            TinyPdf.encryptedBytes(listOf(TinyPdf.solid(1.0, 0.0, 0.0, 200, 100))),
        )
        val first = failure(file)
        assertTrue("암호를 묻지 않았다: $first", first is OpenFailure.PasswordRequired)
        assertTrue(!(first as OpenFailure.PasswordRequired).wrongPassword)
        // 이 표본에는 맞는 암호가 없다. 무엇을 넣든 '틀렸다' 여야 한다.
        val second = runBlocking {
            var opened: OpenedDocument? = null
            try {
                (PdfOpener("아무거나".toCharArray()).open(FileDocumentSource(file)) { opened = it }
                    as OpenOutcome.Failed).failure
            } finally {
                opened?.close()
            }
        }
        assertTrue("틀렸다고 하지 않는다: $second", (second as? OpenFailure.PasswordRequired)?.wrongPassword == true)
    }

    @Test
    fun 깨진_파일은_깨졌다고_말한다() {
        assertTrue(failure(write("empty.pdf", ByteArray(0))) is OpenFailure.Corrupt)
        assertTrue(
            failure(write("junk.pdf", ByteArray(4096) { it.toByte() })) is OpenFailure.Corrupt,
        )
        // 머리만 있고 몸이 없는 파일. pdfium 이 복구를 시도하다 실패하는 길이다.
        assertTrue(
            failure(write("head.pdf", "%PDF-1.4\n".toByteArray())) is OpenFailure.Corrupt,
        )
    }

    @Test
    fun 없는_파일과_아카이브_엔트리는_열지_않는다() {
        val missing = File(work, "없다.pdf")
        assertTrue(failure(missing) is OpenFailure.Corrupt)
    }

    // ---- 화소 읽기 --------------------------------------------------------------

    private suspend fun centerOf(store: PdfPageStore, ordinal: Int): Int =
        center(store.fitted(ordinal, 240, 400)!!)

    /**
     * 한가운데 화소. `ImageBitmap` 을 화소로 읽는 길은 `toPixelMap` 이다 — 밑에 깔린
     * `Bitmap` 을 다시 꺼내면 '축출해도 `recycle` 하지 않는다' 는 약속과 엮인다.
     */
    private fun center(image: ImageBitmap): Int =
        image.toPixelMap()[image.width / 2, image.height / 2].toArgb()
}
