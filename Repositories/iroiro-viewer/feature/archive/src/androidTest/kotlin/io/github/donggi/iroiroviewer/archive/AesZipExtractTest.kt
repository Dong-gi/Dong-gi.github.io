package io.github.donggi.iroiroviewer.archive

import androidx.test.platform.app.InstrumentationRegistry
import io.github.donggi.iroiroviewer.format.FileDocumentSource
import io.github.donggi.iroiroviewer.format.archive.Archives
import io.github.donggi.iroiroviewer.io.FileOpEngine
import io.github.donggi.iroiroviewer.io.FileOpManager
import io.github.donggi.iroiroviewer.io.ZipMaker
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * **이 앱이 잠근 ZIP 을 이 앱의 풀기가 연다** — 기기에서(2026-10-16). 묶기는 'ZIP으로 압축' 의 실체([ZipMaker], 암호를 넣은 길), 풀기는
 * 압축 화면의 실체([ArchiveExtractEngine])다. JVM 의 왕복(`AesZipRoundTripTest`)과 달리 기기의 JCE(AES·HMAC — PBKDF2 는 그 HMAC 으로 우리가 돈다),
 * 앱 전용 외부 폴더(FUSE) 위의 위치 지정 쓰기(머리 고쳐 쓰기)·`O_EXCL` 이름 맡기·임시본을 함께 탄다.
 */
class AesZipExtractTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var work: File

    @Before
    fun setUp() {
        work = File(context.getExternalFilesDir(null), "aeszip-test-${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        work.deleteRecursively()
    }

    private fun partials(dir: File): List<String> =
        dir.walkTopDown().filter { it.isFile && it.name.startsWith(".iroiro-") }.map { it.name }.toList()

    private fun extract(zip: File, dest: File, password: String?) = runBlocking {
        ArchiveExtractEngine(context).run(
            FileOpManager.Request.Extract(
                id = "t",
                archivePath = zip.absolutePath,
                entryIndices = null,
                destParent = dest.absolutePath,
                newFolderName = null,
                conflict = FileOpEngine.Conflict.KEEP_BOTH,
                password = password?.toCharArray(),
            ),
        ) { }
    }

    @Test
    fun 잠가_묶은_것을_풀면_원본과_같다() {
        val src = File(work, "원본").apply { mkdirs() }
        val text = "한 줄의 글\n".repeat(3000).toByteArray()
        val photo = Random(1).nextBytes(300_000)
        val big = Random(2).nextBytes(5 * 1024 * 1024)
        File(src, "메모.txt").writeBytes(text)
        File(src, "사진").mkdirs()
        File(src, "사진/IMG_0001.jpg").writeBytes(photo)
        File(src, "빈 폴더").mkdirs()
        File(src, "빈 글.txt").writeBytes(ByteArray(0))
        File(src, "큰 파일.bin").writeBytes(big)

        val made = runBlocking {
            ZipMaker.make(listOf(src.absolutePath), work.absolutePath, "잠금", media = null, onProgress = {}, password = "기기 암호 1".toCharArray())
        }
        assertEquals(FileOpEngine.Outcome.Done(4, 0, 0), made.outcome)
        val zip = File(made.path!!)
        assertEquals(emptyList<String>(), partials(work))

        // 목록 — 파일은 잠겼고 폴더는 아니다. 암호 없이 열면 읽을 수 없다.
        Archives.open(FileDocumentSource(zip)).use { r ->
            for (e in r.entries) assertEquals(e.name, !e.isDirectory, e.isEncrypted)
            assertTrue(r.entries.filter { !it.isDirectory }.none { it.isReadable })
        }

        val dest = File(work, "풀기").apply { mkdirs() }
        val result = extract(zip, dest, "기기 암호 1")
        assertTrue("${result.outcome}", result.outcome is FileOpEngine.Outcome.Done)
        assertEquals(0, result.report.refusedEncrypted)
        assertArrayEquals(text, File(dest, "원본/메모.txt").readBytes())
        assertArrayEquals(photo, File(dest, "원본/사진/IMG_0001.jpg").readBytes())
        assertArrayEquals(big, File(dest, "원본/큰 파일.bin").readBytes())
        assertEquals(0L, File(dest, "원본/빈 글.txt").length())
        assertTrue(File(dest, "원본/빈 폴더").isDirectory)
        assertEquals(emptyList<String>(), partials(dest))
    }

    @Test
    fun 틀린_암호로는_아무것도_풀지_않는다() {
        val src = File(work, "a.txt").apply { writeText("비밀") }
        val made = runBlocking {
            ZipMaker.make(listOf(src.absolutePath), work.absolutePath, "w", media = null, onProgress = {}, password = "맞는 암호".toCharArray())
        }
        val dest = File(work, "d").apply { mkdirs() }
        extract(File(made.path!!), dest, "틀린 암호")
        assertTrue("틀린 암호로 파일이 생겼다 ${dest.list()?.toList()}", dest.list().isNullOrEmpty())
        assertEquals(emptyList<String>(), partials(dest))
        assertFalse(File(dest, "a.txt").exists())
    }
}
