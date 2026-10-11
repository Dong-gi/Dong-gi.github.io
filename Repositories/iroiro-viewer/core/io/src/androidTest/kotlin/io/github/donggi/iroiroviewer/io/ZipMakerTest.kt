package io.github.donggi.iroiroviewer.io

import android.system.ErrnoException
import android.system.Os
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.random.Random

/**
 * ZIP 만들기([ZipMaker]). 오라클은 플랫폼의 `ZipFile` 이다 — 만든 것을 **다른 코드로 읽어** 이름·내용·시각을 견준다.
 *
 * 계측으로 두는 까닭은 `Os.lstat`·`O_EXCL`·`rename(2)` 이 기기의 파일시스템에서만 답하기 때문이다(복사의 시험과 같다).
 * 앱 전용 외부 폴더(FUSE 위)에서 돈다 — 사용자의 폴더와 같은 길이다.
 */
@RunWith(AndroidJUnit4::class)
class ZipMakerTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var work: File

    @Before
    fun setUp() {
        val root = context.getExternalFilesDirs(null).filterNotNull().first()
        work = File(root, "ziptest-${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        work.deleteRecursively()
    }

    private fun make(sources: List<File>, name: String): ZipMaker.Result = runBlocking {
        ZipMaker.make(sources.map { it.absolutePath }, work.absolutePath, name, media = null, onProgress = {})
    }

    private fun partials(): List<String> =
        work.list().orEmpty().filter { it.startsWith(AtomicFileWriter.PART_PREFIX) }

    @Test
    fun 묶은_것을_다른_코드가_그대로_읽는다() {
        val src = File(work, "src").apply { mkdirs() }
        File(src, "a.txt").writeText("hello")
        val korean = File(src, "한글 폴더").apply { mkdirs() }
        val photo = Random(1).nextBytes(5000)
        File(korean, "사진.jpg").writeBytes(photo)
        File(src, "빈폴더").mkdirs()
        val log = File(work, "b.log").apply { writeText("line\n".repeat(2000)) }

        val result = make(listOf(src, log), "묶음")
        assertEquals(FileOpEngine.Outcome.Done(3, 0, 0), result.outcome)
        val zip = File(work, "묶음.zip")
        assertEquals(zip.absolutePath, result.path)
        assertTrue(partials().isEmpty())

        ZipFile(zip).use { z ->
            val names = z.entries().toList().map(ZipEntry::getName)
            assertEquals(
                listOf("src/", "src/a.txt", "src/빈폴더/", "src/한글 폴더/", "src/한글 폴더/사진.jpg", "b.log"),
                names,
            )
            assertEquals("hello", z.getInputStream(z.getEntry("src/a.txt")).readBytes().decodeToString())
            assertArrayEquals(photo, z.getInputStream(z.getEntry("src/한글 폴더/사진.jpg")).readBytes())
            assertEquals("line\n".repeat(2000), z.getInputStream(z.getEntry("b.log")).readBytes().decodeToString())
            // 글은 줄고, 사진은 다시 압축하지 않는다('저장').
            assertTrue(z.getEntry("b.log").compressedSize < 2000)
            assertEquals(ZipEntry.DEFLATED, z.getEntry("b.log").method)
            assertEquals(ZipEntry.STORED, z.getEntry("src/한글 폴더/사진.jpg").method)
            assertEquals(photo.size.toLong(), z.getEntry("src/한글 폴더/사진.jpg").compressedSize)
            assertTrue(z.getEntry("src/빈폴더/").isDirectory)
        }
        // 이름은 UTF-8 이고 그 표시(일반 목적 비트 11)가 켜져 있다 — 첫 로컬 머리의 6~7 바이트.
        RandomAccessFile(zip, "r").use { f ->
            f.seek(6)
            val flags = f.read() or (f.read() shl 8)
            assertTrue("UTF-8 표시가 꺼져 있다: $flags", flags and 0x0800 != 0)
        }
    }

    @Test
    fun 수정_시각을_지킨다() {
        val file = File(work, "old.txt").apply { writeText("x") }
        // 초 단위(확장 시각의 정밀도)로 맞춘 2020-01-02 03:04:05 UTC.
        val time = 1_577_934_245_000L
        assertTrue(file.setLastModified(time))
        make(listOf(file), "t")
        ZipFile(File(work, "t.zip")).use { z ->
            assertEquals(time, z.getEntry("old.txt").lastModifiedTime.toMillis())
        }
    }

    @Test
    fun 같은_이름이_있으면_덮지_않고_다음_이름이다() {
        val file = File(work, "a.txt").apply { writeText("new") }
        val existing = File(work, "a.zip").apply { writeText("NOT A ZIP — 사용자의 파일") }
        val result = make(listOf(file), "a.zip")
        assertEquals(File(work, "a (2).zip").absolutePath, result.path)
        assertEquals("NOT A ZIP — 사용자의 파일", existing.readText())
        ZipFile(File(result.path!!)).use { z -> assertNotNull(z.getEntry("a.txt")) }
    }

    @Test
    fun 이름에_확장자가_없으면_붙인다() {
        assertEquals("묶음.zip", ZipMaker.zipName("묶음"))
        assertEquals("a.ZIP", ZipMaker.zipName("a.ZIP"))
        assertEquals("a.tar.zip", ZipMaker.zipName("a.tar"))
        assertNull(ZipMaker.zipName("a/b"))
        assertNull(ZipMaker.zipName(""))
    }

    /**
     * 앱 내부 폴더(ext4)에서 돈다 — 외부 폴더의 FUSE 는 링크를 만들지 못할 수 있다(그러면 사용자의 폴더에도 링크가 없다).
     */
    @Test
    fun 링크는_따라가지도_담지도_않는다() {
        work.deleteRecursively()
        work = File(context.filesDir, "ziptest-${System.nanoTime()}").apply { mkdirs() }
        val src = File(work, "src").apply { mkdirs() }
        File(src, "real.txt").writeText("r")
        val outside = File(work, "outside.txt").apply { writeText("밖의 파일") }
        val link = File(src, "link.txt")
        val made = try {
            Os.symlink(outside.absolutePath, link.absolutePath)
            true
        } catch (e: ErrnoException) {
            false
        }
        assumeTrue("이 파일시스템은 심볼릭 링크를 만들지 못한다", made)

        val result = make(listOf(src), "l")
        assertEquals(FileOpEngine.Outcome.Done(1, 1, 0), result.outcome)
        ZipFile(File(work, "l.zip")).use { z ->
            assertEquals(listOf("src/", "src/real.txt"), z.entries().toList().map(ZipEntry::getName))
        }
        // 링크만 고르면 담을 것이 없다 — 빈 ZIP 을 만들지 않는다.
        val none = make(listOf(link), "n")
        assertEquals(FileOpEngine.Outcome.Done(0, 1, 0), none.outcome)
        assertNull(none.path)
        assertFalse(File(work, "n.zip").exists())
    }

    @Test
    fun 취소하면_아무것도_남지_않는다() = runBlocking {
        // 다시 압축하는 형식이 아니라서(.bin) 깊이 압축한다 — 무작위 바이트라 오래 걸린다.
        val big = File(work, "big.bin")
        big.outputStream().use { out ->
            val rnd = Random(7)
            repeat(48) { out.write(rnd.nextBytes(1 shl 20)) }
        }
        val job = async {
            ZipMaker.make(listOf(big.absolutePath), work.absolutePath, "c", media = null, onProgress = {})
        }
        delay(150)
        job.cancel()
        val outcome = runCatching { job.await() }.getOrNull()?.outcome
        // 취소가 늦어 다 만들었으면 이 시험은 아무것도 재지 못한다 — 그때만 건너뛴다.
        assumeTrue("취소보다 먼저 끝났다", outcome == null || outcome == FileOpEngine.Outcome.Cancelled)
        // 정리는 NonCancellable 이다 — 끝날 때까지 잠깐 기다린다.
        repeat(50) { if (partials().isEmpty()) return@repeat; delay(20) }
        assertTrue("임시본이 남았다: ${partials()}", partials().isEmpty())
        assertFalse(File(work, "c.zip").exists())
    }
}
