package io.github.donggi.iroiroviewer.io

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * 잘라 저장의 **쓰는 쪽**([ImageSaver]). 뜨는 쪽은 가짜를 꽂는다 — 화면이 본 그대로 뜨는 일(`ImageCropRenderer`)은 `feature:image`
 * 의 것이고 여기서 보려는 것은 형식·이름·EXIF·원본을 건드리지 않음이다. 결과는 플랫폼 디코더와 `ExifInterface` 로 다시 읽는다.
 */
@RunWith(AndroidJUnit4::class)
class ImageSaverTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var work: File

    @Before
    fun setUp() {
        val root = context.getExternalFilesDirs(null).filterNotNull().first()
        work = File(root, "croptest-${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        work.deleteRecursively()
    }

    private fun request(src: File, w: Int, h: Int) = FileOpManager.Request.SaveCrop("id", src.absolutePath, 0, 0, 10, 10, w, h)

    private fun save(src: File, w: Int, h: Int, rendered: () -> CropSupport.Rendered): ImageSaver.Result = runBlocking {
        ImageSaver.saveCrop(request(src, w, h), { rendered() }, media = null, onProgress = {})
    }

    private fun piece(w: Int, h: Int, color: Int, alpha: Boolean = false): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            eraseColor(color)
            setHasAlpha(alpha)
        }

    private fun partials() = work.list().orEmpty().filter { it.startsWith(AtomicFileWriter.PART_PREFIX) }

    @Test
    fun JPEG_은_곁에_새_파일로_쓰고_날짜만_옮긴다() {
        val src = File(work, "photo.jpg")
        src.outputStream().use { piece(40, 30, Color.BLUE).compress(Bitmap.CompressFormat.JPEG, 90, it) }
        ExifInterface(src.absolutePath).apply {
            setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2026:10:04 09:30:00")
            setAttribute(ExifInterface.TAG_MAKE, "TestCam")
            setLatLong(37.5665, 126.9780)
            saveAttributes()
        }
        val before = src.readBytes()

        val result = save(src, 40, 30) { CropSupport.Rendered.Ok(piece(12, 8, Color.RED), "image/jpeg", downscaled = false) }
        assertEquals(FileOpEngine.Outcome.Done(1, 0, 0), result.outcome)
        val out = File(result.path!!)
        assertEquals("photo_crop.jpg", out.name)
        val decoded = BitmapFactory.decodeFile(out.absolutePath)
        assertEquals(12, decoded.width)
        assertEquals(8, decoded.height)
        val exif = ExifInterface(out.absolutePath)
        assertEquals("2026:10:04 09:30:00", exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
        assertEquals("TestCam", exif.getAttribute(ExifInterface.TAG_MAKE))
        assertNull("위치는 옮기지 않는다", exif.latLong)
        assertArrayEquals("원본은 그대로다", before, src.readBytes())
        assertTrue(partials().isEmpty())
        assertNull(result.note)
    }

    /**
     * 날짜를 입히다(`saveAttributes` 의 제자리 되쓰기) 임시본이 상하면 — 되쓰다 던지고 되살리기까지 실패했거나, 마지막 버퍼를 잃고도
     * 던지지 않았거나(`closeQuietly`) — 상한 것을 놓지 않고 **날짜 없이 다시 쓴다.** 예전에는 실패를 삼키고 잘린 그림을 '저장했습니다'
     * 로 놓을 수 있었다. 상하게 하는 것은 시험이 심는다(`writeDates`).
     */
    @Test
    fun 날짜를_입히다_임시본이_상하면_날짜_없이_온전한_그림을_놓는다() {
        val src = File(work, "photo.jpg")
        src.outputStream().use { piece(40, 30, Color.BLUE).compress(Bitmap.CompressFormat.JPEG, 90, it) }
        ExifInterface(src.absolutePath).apply {
            setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2026:10:04 09:30:00")
            saveAttributes()
        }
        // 같은 비트맵을 같은 품질(95)로 쓴 바이트 — 날짜 없이 다시 쓴 결과와 같아야 한다(인코더는 같은 입력에 같은 바이트를 낸다).
        val plain = ByteArrayOutputStream().also { piece(12, 8, Color.RED).compress(Bitmap.CompressFormat.JPEG, 95, it) }.toByteArray()
        val damages: List<(File) -> Unit> = listOf(
            // 되쓰다 던지고 되살리기도 실패했다 — 반쯤 잘린 채 남았다.
            { dst ->
                RandomAccessFile(dst, "rw").use { it.setLength(it.length() / 2) }
                throw IOException("심은 실패")
            },
            // 마지막 버퍼를 잃고도 던지지 않았다.
            { dst -> RandomAccessFile(dst, "rw").use { it.setLength(it.length() - 100) } },
        )
        for ((i, damage) in damages.withIndex()) {
            val result = runBlocking {
                ImageSaver.saveCrop(
                    request(src, 40, 30),
                    { CropSupport.Rendered.Ok(piece(12, 8, Color.RED), "image/jpeg", downscaled = false) },
                    media = null,
                    onProgress = {},
                    writeDates = { _, dst -> damage(dst) },
                )
            }
            assertEquals("손상 $i", FileOpEngine.Outcome.Done(1, 0, 0), result.outcome)
            val out = File(result.path!!)
            assertArrayEquals("손상 $i — 날짜 없이 다시 쓴 온전한 그림", plain, out.readBytes())
            assertNull(ExifInterface(out.absolutePath).getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
            assertTrue(partials().isEmpty())
            out.delete()
        }
    }

    @Test
    fun 투명한_것은_PNG_로_쓰고_줄였으면_말한다() {
        val src = File(work, "art.heic").apply { writeBytes(ByteArray(10)) }
        val result = save(src, 100, 100) {
            CropSupport.Rendered.Ok(piece(20, 10, Color.argb(128, 0, 255, 0), alpha = true), "image/heif", downscaled = true)
        }
        val out = File(result.path!!)
        assertEquals("art_crop.png", out.name)
        assertTrue(BitmapFactory.decodeFile(out.absolutePath).hasAlpha())
        assertEquals(FileOpManager.SavedNote.Downscaled(20, 10), result.note)
    }

    @Test
    fun 같은_이름이_있으면_번호를_붙인다() {
        val src = File(work, "a.png").apply { outputStream().use { piece(4, 4, Color.WHITE).compress(Bitmap.CompressFormat.PNG, 100, it) } }
        File(work, "a_crop.png").writeText("이미 있다")
        val result = save(src, 4, 4) { CropSupport.Rendered.Ok(piece(2, 2, Color.BLACK), "image/png", downscaled = false) }
        assertEquals("a_crop (2).png", File(result.path!!).name)
        assertEquals("이미 있다", File(work, "a_crop.png").readText())
    }

    /**
     * 그리기·메모 — 그릴 것을 실은 요청은 **그림 전체**를 그릴 것과 함께 뜨는 쪽에 넘기고, `_markup` 으로 쓴다. 형식·날짜는
     * 잘라 저장과 같다. 가짜 뜨는 쪽이 받은 그릴 것을 실제로 그려, 쓴 파일의 화소로 그것이 남았는지 본다.
     */
    @Test
    fun 그린_것은_그림_전체_위에_그려_markup_으로_쓴다() {
        val src = File(work, "photo.jpg")
        src.outputStream().use { piece(40, 30, Color.WHITE).compress(Bitmap.CompressFormat.JPEG, 90, it) }
        ExifInterface(src.absolutePath).apply {
            setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2026:10:08 10:00:00")
            setLatLong(37.5665, 126.9780)
            saveAttributes()
        }
        // 왼쪽 반을 빨갛게 칠하는 '그릴 것'.
        val overlay = CropSupport.Overlay { canvas, w, h ->
            canvas.drawRect(0f, 0f, w / 2f, h.toFloat(), android.graphics.Paint().apply { color = Color.RED })
        }
        var asked: CropSupport.Ask? = null
        val result = runBlocking {
            ImageSaver.saveCrop(
                FileOpManager.Request.SaveCrop("id", src.absolutePath, 0, 0, 40, 30, 40, 30, overlay),
                { ask ->
                    asked = ask
                    val b = piece(40, 30, Color.WHITE)
                    ask.overlay?.draw(android.graphics.Canvas(b), b.width, b.height)
                    CropSupport.Rendered.Ok(b, "image/jpeg", downscaled = false)
                },
                media = null,
                onProgress = {},
            )
        }
        assertEquals(FileOpEngine.Outcome.Done(1, 0, 0), result.outcome)
        val ask = asked!!
        assertTrue("그릴 것이 뜨는 쪽에 갔다", ask.overlay === overlay)
        assertEquals(listOf(0, 0, 40, 30), listOf(ask.left, ask.top, ask.right, ask.bottom))
        val out = File(result.path!!)
        assertEquals("photo_markup.jpg", out.name)
        val decoded = BitmapFactory.decodeFile(out.absolutePath)
        assertTrue("왼쪽은 빨강", Color.red(decoded.getPixel(5, 15)) > 200 && Color.green(decoded.getPixel(5, 15)) < 60)
        assertTrue("오른쪽은 흰색", Color.green(decoded.getPixel(35, 15)) > 200)
        val exif = ExifInterface(out.absolutePath)
        assertEquals("2026:10:08 10:00:00", exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
        assertNull("위치는 옮기지 않는다", exif.latLong)
        assertTrue(partials().isEmpty())
    }

    @Test
    fun 바뀐_파일과_못_뜬_그림은_아무것도_쓰지_않는다() {
        val src = File(work, "b.jpg").apply { writeBytes(ByteArray(10)) }
        val changed = save(src, 4, 4) { CropSupport.Rendered.Changed }
        assertEquals(FileOpEngine.Reason.CHANGED, (changed.outcome as FileOpEngine.Outcome.Failed).reason)
        val unsupported = save(src, 4, 4) { CropSupport.Rendered.Unsupported }
        assertEquals(FileOpEngine.Reason.UNSUPPORTED, (unsupported.outcome as FileOpEngine.Outcome.Failed).reason)
        assertEquals(listOf("b.jpg"), work.list()!!.toList())
    }
}
