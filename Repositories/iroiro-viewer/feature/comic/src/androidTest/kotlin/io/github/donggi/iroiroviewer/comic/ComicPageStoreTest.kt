package io.github.donggi.iroiroviewer.comic

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import io.github.donggi.iroiroviewer.safety.ComicLimits
import io.github.donggi.iroiroviewer.safety.ImageLimits
import kotlinx.coroutines.runBlocking
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 쪽을 **실제 디코더로** 뜬다.
 *
 * JVM 시험이 닿지 못하는 자리만 여기 둔다 — `ImageDecoder`·`BitmapRegionDecoder` 는
 * 플랫폼 네이티브라 에뮬레이터에서라야 돈다. 그래서 확인하는 것도 셋뿐이다.
 *
 * 1. **예산이 실제로 표본을 키우는가.** 산수는 `ImageBudgetTest` 가 이미 못 박았지만,
 *    그 값이 디코더까지 닿는지는 여기서만 알 수 있다.
 * 2. **띠가 제 자리를 뜨는가.** 웹툰의 뼈대다.
 * 3. **같은 쪽을 두 번 부르면 캐시가 도는가.**
 *
 * 예산을 작게 만들어 시험한다 — 12MP 표본을 만들려면 그 자체로 48 MB 비트맵이 필요해
 * 시험이 먼저 죽는다. **상한을 내리는 것과 그림을 키우는 것은 같은 경로를 탄다.**
 */
class ComicPageStoreTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var work: File

    /** 화면 240×400, 힙 등급 32MB. 한 장 384,000 B, 쪽 하나의 상한도 384,000 B. */
    private val tinyBudget = ImageLimits.budgetOf(240, 400, 32)

    @Before
    fun setUp() {
        work = File(context.getExternalFilesDir(null), "comic-test-${System.nanoTime()}")
        work.mkdirs()
    }

    @After
    fun tearDown() {
        work.deleteRecursively()
    }

    /** 단색 PNG. 내용이 아니라 치수가 시험의 대상이다. */
    private fun png(width: Int, height: Int, color: Int): ByteArray {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(color)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        bmp.recycle()
        return out.toByteArray()
    }

    private fun cbz(name: String, pages: List<Pair<String, ByteArray>>): File {
        val f = File(work, name)
        ZipArchiveOutputStream(f).use { z ->
            for ((n, b) in pages) {
                val e = ZipArchiveEntry(n)
                e.size = b.size.toLong()
                z.putArchiveEntry(e)
                z.write(b)
                z.closeArchiveEntry()
            }
            z.finish()
        }
        return f
    }

    private fun storeOf(file: File, budget: ImageLimits.Budget = tinyBudget): ComicPageStore {
        val result = runBlocking { ComicOpen.open(file.path, ComicLimits.windowBytes(budget)) }
        assertTrue("열려야 한다: $result", result is ComicOpen.Result.Ready)
        return ComicPageStore((result as ComicOpen.Result.Ready).source, budget)
    }

    /**
     * **이 시험이 이 파일의 이유다.**
     *
     * 목표 해상도만 보면 1200×1800 한 장이 8.64 MB 다. 쪽 하나의 예산은 384,000 B 이므로
     * 표본이 더 커져야 하고, 그 판단이 디코더까지 닿아야 한다.
     */
    @Test
    fun 예산을_넘는_쪽은_더_줄여서_뜬다() {
        val f = cbz("big.cbz", listOf("01.png" to png(1200, 1800, 0xFFCC2222.toInt())))
        val store = storeOf(f)
        val bitmap = runBlocking { store.still(0, targetLongest = 2400) }
        assertNotNull(bitmap)
        val bytes = bitmap!!.width.toLong() * bitmap.height * ImageLimits.BYTES_PER_PIXEL
        assertTrue("한 장이 ${bytes}B 로 상한 ${store.pageCap}B 를 넘는다", bytes <= store.pageCap)
        // 흐려지되 사라지지는 않는다.
        assertTrue(bitmap.width >= 64)
    }

    /** 넉넉한 예산에서는 목표 해상도 그대로 뜬다 — 상한이 언제나 끼어들면 안 된다. */
    @Test
    fun 예산_안에_드는_쪽은_그대로_뜬다() {
        val big = ImageLimits.budgetOf(1080, 2400, 256)
        val f = cbz("ok.cbz", listOf("01.png" to png(600, 900, 0xFF2288CC.toInt())))
        val store = storeOf(f, big)
        val bitmap = runBlocking { store.still(0, targetLongest = 2400) }
        assertNotNull(bitmap)
        assertEquals(600, bitmap!!.width)
        assertEquals(900, bitmap.height)
    }

    @Test
    fun 치수와_애니메이션_여부를_디코딩_없이_잰다() {
        val f = cbz("info.cbz", listOf("01.png" to png(320, 480, 0xFF333333.toInt())))
        val store = storeOf(f)
        val info = runBlocking { store.info(0) }
        assertNotNull(info)
        assertEquals(320, info!!.width)
        assertEquals(480, info.height)
        assertFalse("정지 PNG 을 애니메이션으로 보면 안 된다", info.animated)
        assertFalse("APNG 이 아니다", info.apng)
    }

    /**
     * 긴 쪽은 **띠로** 뜬다.
     *
     * 400×3000 을 폭 240 에 맞추면 그려질 높이가 1800 이고 뷰포트(400)의 4.5배다 —
     * [ComicLimits.MAX_TALL_RATIO] 를 넘으므로 띠가 여럿이어야 한다.
     */
    @Test
    fun 긴_쪽은_띠로_나뉘어_뜬다() {
        val f = cbz("tall.cbz", listOf("01.png" to png(400, 3000, 0xFF22AA44.toInt())))
        val store = storeOf(f)
        val info = runBlocking { store.info(0) }!!
        assertTrue(ComicLimits.isTall(info.width, info.height, 240, 400))

        val count = store.bandCount(info, 240, 400)
        assertTrue("띠가 여럿이어야 한다: $count", count > 1)

        val bandSrc = ComicLimits.bandHeight(info.width, 240, 400)
        val first = runBlocking { store.band(0, 0, 240, 400) }
        assertNotNull(first)
        // 표본이 1이면 원본 띠 높이 그대로, 더 줄었으면 그 이하다.
        assertTrue(first!!.height in 1..bandSrc)
        assertEquals(info.width / (info.width / first.width).coerceAtLeast(1), first.width)

        // **마지막 띠는 쪽을 넘지 않는다.** 넘으면 디코더가 빈 비트맵을 주거나 던진다.
        val last = runBlocking { store.band(0, count - 1, 240, 400) }
        assertNotNull(last)
        // 쪽 밖의 띠는 없다.
        assertNull(runBlocking { store.band(0, count + 8, 240, 400) })
    }

    /** 같은 쪽을 두 번 부르면 **같은 비트맵**이다. 되돌아 넘길 때 다시 디코딩하지 않는다. */
    @Test
    fun 같은_쪽은_캐시에서_나온다() {
        val f = cbz("cache.cbz", listOf("01.png" to png(200, 300, 0xFF884422.toInt())))
        val store = storeOf(f)
        val a = runBlocking { store.still(0, 2400) }
        val b = runBlocking { store.still(0, 2400) }
        assertNotNull(a)
        assertSame(a, b)
    }

    /** 화면 크기가 바뀌면(회전) 옛 크기로 뜬 것은 버린다. */
    @Test
    fun 화면이_바뀌면_캐시를_버린다() {
        val f = cbz("rot.cbz", listOf("01.png" to png(200, 300, 0xFF224488.toInt())))
        val store = storeOf(f)
        val a = runBlocking { store.still(0, 2400) }
        store.clearBitmaps()
        val b = runBlocking { store.still(0, 2400) }
        assertNotNull(a)
        assertNotNull(b)
        assertTrue("버렸다면 같은 객체가 아니다", a !== b)
    }

    /** 깨진 쪽은 그 쪽만 실패한다. 나머지는 그대로 열린다. */
    @Test
    fun 깨진_쪽만_실패한다() {
        val good = png(200, 300, 0xFF00AA00.toInt())
        val broken = good.copyOf(good.size / 3)
        val f = cbz("broken.cbz", listOf("01.png" to good, "02.png" to broken, "03.png" to good))
        val store = storeOf(f)
        assertEquals(3, store.pageCount)
        assertNotNull(runBlocking { store.still(0, 2400) })
        assertNull(runBlocking { store.still(1, 2400) })
        assertNotNull(runBlocking { store.still(2, 2400) })
    }
}
