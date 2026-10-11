package io.github.donggi.iroiroviewer.docview

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.test.platform.app.InstrumentationRegistry
import io.github.donggi.iroiroviewer.docview.pdf.PdfDocument
import io.github.donggi.iroiroviewer.docview.pdf.PdfOpener
import io.github.donggi.iroiroviewer.docview.pdf.PdfPageStore
import io.github.donggi.iroiroviewer.docview.pdf.PdfSpreads
import io.github.donggi.iroiroviewer.format.FileDocumentSource
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
 * PDF 이어 보기의 그리기를 **실제 pdfium 으로** — 폭에 맞춘 줄이 예산 안이고, 확대한 줄은 보이는 자리만 다시 그린다.
 * 산수(`PdfLimitsTest`·`ImageBudgetTest`·`PdfScrollMathTest`)는 JVM 에서 박혀 있고, 여기서 묻는 것은 그 값이 디코더까지 닿는가다.
 * 판정은 화소로 한다(`PdfDocumentTest` 와 같은 단색 표본).
 */
class PdfScrollStoreTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var work: File

    /** 화면 240×400, 힙 등급 32MB. 작은 예산이 상한 경로를 실제로 태운다. */
    private val tinyBudget = ImageLimits.budgetOf(240, 400, 32)

    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()

    @Before
    fun setUp() {
        work = File(context.getExternalFilesDir(null), "pdf-scroll-test-${System.nanoTime()}")
        work.mkdirs()
    }

    @After
    fun tearDown() {
        work.deleteRecursively()
    }

    private fun open(name: String, pages: List<TinyPdf.Page>): PdfDocument = runBlocking {
        val file = File(work, name).also { it.writeBytes(TinyPdf.bytes(pages)) }
        var opened: OpenedDocument? = null
        val outcome = PdfOpener().open(FileDocumentSource(file)) { opened = it }
        assertTrue("열지 못했다: $outcome", outcome is OpenOutcome.Success)
        (outcome as OpenOutcome.Success).document as PdfDocument
    }

    @Test
    fun 폭에_맞춘_줄은_화면_폭이고_줄_상한을_넘지_않는다() {
        val doc = open("a4.pdf", listOf(TinyPdf.solid(1.0, 0.0, 0.0, 595, 842)))
        try {
            val store = PdfPageStore(doc, tinyBudget)
            runBlocking {
                val image = store.fittedToWidth(PdfSpreads.View.single(0), 240).must()
                assertEquals("폭에 맞춘다", 240, image.width)
                assertTrue(bytes(image) <= ImageLimits.scrollPageCap(tinyBudget))
                assertEquals(red, center(image))
            }
        } finally {
            doc.close()
        }
    }

    @Test
    fun 화면보다_긴_줄은_상한에_걸려_낮은_배율로_그린다() {
        // 폭 100pt·높이 4000pt — 폭에 맞추면 240×9600(9 MB)으로 줄 상한을 넘는다.
        val doc = open("tall.pdf", listOf(TinyPdf.solid(0.0, 0.0, 1.0, 100, 4000)))
        try {
            val store = PdfPageStore(doc, tinyBudget)
            runBlocking {
                val view = PdfSpreads.View.single(0)
                val image = store.fittedToWidth(view, 240).must()
                assertTrue("상한 안 ${bytes(image)}", bytes(image) <= ImageLimits.scrollPageCap(tinyBudget))
                assertTrue("폭보다 작게 그렸다", image.width < 240)
                // 화면이 다시 잰 배율(선명화의 문턱)과 실제로 그린 크기가 같은 식이다.
                val plan = store.widthPlan(store.knownLayout(view).must(), 240).must()
                assertEquals(plan.width, image.width)
                assertEquals(plan.height, image.height)
            }
        } finally {
            doc.close()
        }
    }

    @Test
    fun 확대한_줄은_보이는_자리만_다시_그린다() {
        // 왼쪽 절반 빨강, 오른쪽 절반 파랑. 오른쪽 위 1/4 이 보인다고 하면 조각은 파랑이다.
        val doc = open("halves.pdf", listOf(TinyPdf.halves(400, 200)))
        try {
            val store = PdfPageStore(doc, tinyBudget)
            runBlocking {
                val view = PdfSpreads.View.single(0)
                store.fittedToWidth(view, 240)
                val base = 240.0 / 400
                val tile = store.regionDetail(view, base * 3, base, 0.55, 0.0, 1.0, 0.5, store.detailCap, base * 8).must()
                assertEquals(blue, center(tile.image))
                assertTrue("보이는 자리 밖을 떴다 ${tile.tile}", tile.tile.srcLeft >= tile.tile.pageWidth / 2)
                assertTrue(PdfLimits.bytesOf(tile.image.width, tile.image.height) <= store.detailCap)
                // 바닥층이 이미 그 해상도면 다시 그리지 않는다.
                assertNull(store.regionDetail(view, base, base, 0.0, 0.0, 1.0, 1.0, store.detailCap, base * 8))
            }
        } finally {
            doc.close()
        }
    }

    @Test
    fun 쪽_목록이_열린_동안은_줄_조각을_뜨지_않는다() {
        val doc = open("plain.pdf", listOf(TinyPdf.solid(1.0, 0.0, 0.0, 400, 200)))
        try {
            val store = PdfPageStore(doc, tinyBudget)
            runBlocking {
                val view = PdfSpreads.View.single(0)
                store.fittedToWidth(view, 240)
                store.openGrid()
                val base = 240.0 / 400
                assertNull(store.regionDetail(view, base * 3, base, 0.0, 0.0, 1.0, 1.0, store.detailCap, base * 8))
                store.closeGrid()
            }
        } finally {
            doc.close()
        }
    }

    /** JUnit 의 `assertNotNull` 은 값을 돌려주지 않는다 — 확인하고 그 값을 쓴다. */
    private fun <T : Any> T?.must(): T {
        assertNotNull(this)
        return this!!
    }

    private fun bytes(image: ImageBitmap): Long = image.width.toLong() * image.height * ImageLimits.BYTES_PER_PIXEL

    private fun center(image: ImageBitmap): Int = image.toPixelMap()[image.width / 2, image.height / 2].toArgb()
}
