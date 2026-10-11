package io.github.donggi.iroiroviewer.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.donggi.iroiroviewer.io.CropSupport
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/**
 * 그리기·메모의 **뜨는 쪽** — 원본 해상도로 뜬 그림 위에 그린 화소를 몇 점 견준다. 쓰는 쪽(형식·이름·EXIF)은 `core:io` 의
 * `ImageSaverTest` 가 본다.
 *
 * 그림 좌표(폭 = 1)로 든 획이 뜬 비트맵의 같은 몫의 자리에 같은 몫의 굵기로 남는가, 원본을 줄여 뜨면 함께 줄어드는가, EXIF 방향이
 * 붙은 사진에서 **보이는 방향**으로 그려지는가, 형광펜이 제 위를 지나도 진해지지 않는가, 그리고 화면과 저장이 같은 코드를 배율만
 * 달리해 그리는가(큰 것을 줄인 것과 작게 그린 것이 같은가)를 본다.
 */
@RunWith(AndroidJUnit4::class)
class MarkupRenderTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var work: File

    @Before
    fun setUp() {
        val root = context.getExternalFilesDirs(null).filterNotNull().first()
        work = File(root, "markuptest-${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        work.deleteRecursively()
    }

    private fun png(name: String, w: Int, h: Int, color: Int = Color.WHITE): File = File(work, name).also { f ->
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun render(file: File, w: Int, h: Int, marks: List<Mark>, maxPixels: Long = 100_000_000L): CropSupport.Rendered.Ok =
        runBlocking {
            ImageCropRenderer.render(CropSupport.Ask(file.absolutePath, 0, 0, w, h, w, h, maxPixels, MarkupOverlay(marks)))
        } as CropSupport.Rendered.Ok

    private fun assertColor(expected: Int, actual: Int, tolerance: Int = 12, what: String = "") {
        val ok = abs(Color.red(expected) - Color.red(actual)) <= tolerance &&
            abs(Color.green(expected) - Color.green(actual)) <= tolerance &&
            abs(Color.blue(expected) - Color.blue(actual)) <= tolerance
        assertTrue("$what 기대 ${Integer.toHexString(expected)} · 실제 ${Integer.toHexString(actual)}", ok)
    }

    private val red = MarkupColor.RED.argb

    /** 가로로 한 줄 — y 는 폭 단위다(400×300 의 가운데 줄 150 은 150/400). */
    private fun across(id: Long, kind: StrokeKind, y: Float, width: Float, color: Int = red) =
        Stroke(id, kind, color, width, floatArrayOf(0.1f, y, 0.9f, y))

    @Test
    fun 획이_원본_해상도의_같은_자리에_같은_굵기로_그려진다() {
        val src = png("a.png", 400, 300)
        // 굵기 0.025 = 원본 10화소.
        val ok = render(src, 400, 300, listOf(across(1, StrokeKind.PEN, 150f / 400f, 0.025f)))
        val b = ok.bitmap
        assertEquals(400, b.width)
        assertEquals(300, b.height)
        assertColor(red, b.getPixel(200, 150), what = "획 가운데")
        assertColor(red, b.getPixel(200, 147), what = "굵기 안")
        assertColor(Color.WHITE, b.getPixel(200, 160), what = "굵기 밖")
        // 둥근 끝은 시작점(40)에서 반 굵기(5)만큼 나간다.
        assertColor(Color.WHITE, b.getPixel(30, 150), what = "시작 앞")
        assertEquals(false, ok.downscaled)
        b.recycle()
    }

    @Test
    fun 원본을_줄여_뜨면_획도_같은_몫으로_줄어든다() {
        val src = png("big.png", 400, 300)
        // 화소 상한이 넷째 몫이면 표본 2 — 200×150.
        val ok = render(src, 400, 300, listOf(across(1, StrokeKind.PEN, 150f / 400f, 0.025f)), maxPixels = 400L * 300 / 4)
        val b = ok.bitmap
        assertEquals(200, b.width)
        assertTrue(ok.downscaled)
        assertColor(red, b.getPixel(100, 75), what = "줄인 그림의 같은 몫의 자리")
        // 굵기도 반 — 원본 10화소가 5화소다.
        assertColor(Color.WHITE, b.getPixel(100, 80), what = "줄인 굵기 밖")
        b.recycle()
    }

    @Test
    fun EXIF_방향이_붙은_사진에는_보이는_방향으로_그린다() {
        // 저장은 400×300, 방향 6(시계 방향 90°) — 보이는 것은 300×400.
        val src = File(work, "turned.jpg")
        val stored = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
        src.outputStream().use { stored.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        ExifInterface(src.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        // 보이는 그림의 왼쪽 위에 점 하나(폭 300 의 0.1 = 30화소, 지름 0.1 = 30화소).
        val dot = Stroke(1, StrokeKind.PEN, red, 0.1f, floatArrayOf(0.1f, 0.1f))
        val b = render(src, 300, 400, listOf(dot)).bitmap
        assertEquals(300, b.width)
        assertEquals(400, b.height)
        assertColor(red, b.getPixel(30, 30), tolerance = 30, what = "보이는 왼쪽 위")
        assertColor(Color.GRAY, b.getPixel(270, 370), tolerance = 30, what = "보이는 오른쪽 아래")
        b.recycle()
    }

    /** 반투명 형광펜이 같은 자리를 갔다가 돌아와도 한 번 칠한 빛깔이다 — 한 획을 한 경로로 한 번에 그린다. */
    @Test
    fun 형광펜은_제_위를_겹쳐_지나가도_진해지지_않는다() {
        val src = png("hl.png", 400, 300)
        val yellow = MarkupColor.YELLOW.argb
        val once = Stroke(1, StrokeKind.HIGHLIGHTER, yellow, 0.05f, floatArrayOf(0.1f, 0.2f, 0.9f, 0.2f))
        // 같은 줄을 갔다가 돌아온다.
        val twice = Stroke(2, StrokeKind.HIGHLIGHTER, yellow, 0.05f, floatArrayOf(0.1f, 0.5f, 0.9f, 0.5f, 0.1f, 0.5f))
        val b = render(src, 400, 300, listOf(once, twice)).bitmap
        val single = b.getPixel(200, 80)
        val overlapped = b.getPixel(200, 200)
        assertColor(single, overlapped, tolerance = 3, what = "겹친 자리")
        // 그리고 반투명이다 — 흰 바탕이 비친다(노랑의 파랑 성분 53 이 그대로 나오지 않는다).
        assertTrue("반투명", Color.blue(single) > 120)
        b.recycle()
    }

    @Test
    fun 글_메모의_바탕_상자가_그_자리에_깔린다() {
        val src = png("note.png", 400, 300)
        val note = Note(1, 0.25f, 0.25f, "메모 ABC", Color.WHITE, 0.05f, boxed = true)
        val size = MarkupPainter().noteSize(note)
        val b = render(src, 400, 300, listOf(note)).bitmap
        val left = 100
        val top = 100
        val right = ((0.25f + size[0]) * 400).toInt()
        val bottom = ((0.25f + size[1]) * 400).toInt()
        // 흰 글에는 어두운 상자(검정 60%) — 흰 바탕 위에서 102 언저리. 왼쪽 여백의 가운데 높이를 본다(글자도, 둥근 모서리도 피한다).
        assertColor(Color.rgb(102, 102, 102), b.getPixel(left + 2, (top + bottom) / 2), what = "상자 안")
        assertColor(Color.WHITE, b.getPixel(right + 3, (top + bottom) / 2), what = "상자 오른쪽 밖")
        assertColor(Color.WHITE, b.getPixel((left + right) / 2, bottom + 3), what = "상자 아래 밖")
        b.recycle()
    }

    /**
     * 화면은 맞춤 크기에, 저장은 원본 해상도에 **같은 코드**로 그린다(배율만 다르다). 큰 화폭에 그린 것을 줄인 것과 작은 화폭에 그린 것이
     * 거의 같아야 한다 — 글의 줄 바꿈까지(글을 화폭마다 따로 짜면 줄이 다른 자리에서 꺾여 여기서 크게 어긋난다).
     */
    @Test
    fun 큰_화폭에_그려_줄인_것과_작은_화폭에_그린_것이_같다() {
        val marks = listOf(
            Stroke(1, StrokeKind.PEN, red, 0.01f, floatArrayOf(0.1f, 0.1f, 0.3f, 0.25f, 0.5f, 0.12f, 0.7f, 0.3f)),
            Stroke(2, StrokeKind.HIGHLIGHTER, MarkupColor.YELLOW.argb, 0.04f, floatArrayOf(0.1f, 0.5f, 0.9f, 0.5f)),
            Note(3, 0.1f, 0.55f, "줄이 바뀌는 긴 메모입니다 — 같은 자리에서 꺾여야 합니다", MarkupColor.BLUE.argb, 0.04f, boxed = false),
        )
        fun draw(w: Int): Bitmap = Bitmap.createBitmap(w, w * 3 / 4, Bitmap.Config.ARGB_8888).also { b ->
            b.eraseColor(Color.WHITE)
            MarkupPainter().draw(Canvas(b), marks, w.toFloat())
        }
        val big = draw(1600)
        val small = draw(400)
        // 같은 자리를 덮는 칸(큰 것은 32화소, 작은 것은 8화소 사방)의 평균끼리 견준다 — 화소끼리 견주면 글자 획의 안티에일리어싱·힌팅
        // 차이가 섞인다. 칸 평균은 그 차이를 덮고, 낱말이 다른 줄로 가거나 굵기가 어긋난 것은 그대로 드러낸다(칸 하나가 크게 다르다).
        val a = blockMeans(big, 32)
        val b = blockMeans(small, 8)
        assertEquals(a.size, b.size)
        var sum = 0L
        var worst = 0
        for (i in a.indices) {
            val d = abs(a[i] - b[i])
            sum += d
            if (d > worst) worst = d
        }
        val mean = sum.toDouble() / a.size
        assertTrue("칸 평균 차이 $mean", mean < 3.0)
        assertTrue("가장 다른 칸 $worst", worst <= 48)
        big.recycle()
        small.recycle()
    }

    /** [block] 화소 사방의 칸마다 빨강·초록·파랑의 평균. */
    private fun blockMeans(b: Bitmap, block: Int): IntArray {
        val w = b.width
        val px = IntArray(w * b.height).also { b.getPixels(it, 0, w, 0, 0, w, b.height) }
        val cols = w / block
        val rows = b.height / block
        val out = IntArray(cols * rows * 3)
        for (by in 0 until rows) {
            for (bx in 0 until cols) {
                var r = 0L
                var g = 0L
                var bl = 0L
                for (y in by * block until (by + 1) * block) {
                    for (x in bx * block until (bx + 1) * block) {
                        val c = px[y * w + x]
                        r += Color.red(c)
                        g += Color.green(c)
                        bl += Color.blue(c)
                    }
                }
                val n = block * block
                val i = (by * cols + bx) * 3
                out[i] = (r / n).toInt()
                out[i + 1] = (g / n).toInt()
                out[i + 2] = (bl / n).toInt()
            }
        }
        return out
    }
}
