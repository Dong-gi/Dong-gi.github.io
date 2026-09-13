package io.github.donggi.iroiroviewer.archive

import androidx.test.platform.app.InstrumentationRegistry
import io.github.donggi.iroiroviewer.io.FileOpEngine
import io.github.donggi.iroiroviewer.io.FileOpManager
import kotlinx.coroutines.runBlocking
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * 풀기를 **실제 파일시스템에서** 확인한다.
 *
 * JVM 시험으로 대신할 수 없는 것들이다 — canonical 경로 봉쇄는 심볼릭 링크가 있는
 * 진짜 파일시스템에서라야 뜻이 있고, 임시 파일이 남는지는 디렉터리를 실제로 훑어야 안다.
 *
 * 시험은 **앱 전용 외부 디렉터리**에서 돈다. 권한이 필요 없고, 남겨도 앱을 지우면 사라진다.
 */
class ExtractEngineTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var work: File
    private lateinit var engine: ArchiveExtractEngine

    @Before
    fun setUp() {
        work = File(context.getExternalFilesDir(null), "extract-test-${System.nanoTime()}")
        work.mkdirs()
        engine = ArchiveExtractEngine(context)
    }

    @After
    fun tearDown() {
        work.deleteRecursively()
    }

    private fun zip(name: String, entries: List<Pair<String, String>>): File {
        val f = File(work, name)
        ZipArchiveOutputStream(f).use { out ->
            for ((entryName, body) in entries) {
                val e = ZipArchiveEntry(entryName)
                e.time = FIXED_TIME
                out.putArchiveEntry(e)
                out.write(body.toByteArray())
                out.closeArchiveEntry()
            }
        }
        return f
    }

    private fun run(
        archive: File,
        dest: File,
        newFolder: String? = null,
        conflict: FileOpEngine.Conflict = FileOpEngine.Conflict.KEEP_BOTH,
        indices: List<Int>? = null,
    ) = runBlocking {
        engine.run(
            FileOpManager.Request.Extract(
                id = "t",
                archivePath = archive.absolutePath,
                entryIndices = indices,
                destParent = dest.absolutePath,
                newFolderName = newFolder,
                conflict = conflict,
            ),
        ) { }
    }

    /** 남은 임시 파일. **숨김 이름이라 목록에 안 뜨므로 시험이 대신 본다.** */
    private fun partials(dir: File): List<String> =
        dir.walkTopDown().filter { it.isFile && it.name.startsWith(".iroiro-") }.map { it.name }.toList()

    // ---- 경로 안전 ---------------------------------------------------------------

    /**
     * **목적지 밖에 한 바이트도 쓰지 않는다.**
     *
     * 이 앱은 모든 파일 접근 권한을 가지고 돈다 — 보통 앱이라면 샌드박스가 막아 줄 것을
     * 우리가 막아야 한다.
     */
    @Test
    fun 경로_탈출은_목적지_밖에_쓰지_않는다() {
        val outside = File(work, "밖")
        outside.mkdirs()
        val dest = File(work, "안")
        dest.mkdirs()

        val archive = zip(
            "evil.zip",
            listOf(
                "ok.txt" to "정상",
                "../밖/탈출.txt" to "여기 있으면 안 된다",
                "../../../../탈출2.txt" to "여기도",
                "/etc/passwd" to "루트",
                "C:/Windows/evil.dll" to "윈도우",
            ),
        )
        val result = run(archive, dest)

        assertTrue("목적지 밖에 파일이 생겼다", outside.listFiles().isNullOrEmpty())
        assertTrue(File(dest, "ok.txt").isFile)
        // 절대경로와 드라이브 문자는 **상대경로로 다듬어** 안에 푼다. 거부가 아니다.
        assertTrue(File(dest, "etc/passwd").isFile)
        assertTrue(File(dest, "Windows/evil.dll").isFile)
        assertFalse(File(work, "탈출2.txt").exists())
        assertTrue("거부한 것을 세지 않았다", result.report.refusedUnsafe >= 1)
    }

    /** '여기에 풀기' 에서 **아카이브 자신을 덮어쓰지 않는다.** */
    @Test
    fun 아카이브_자신을_덮어쓰지_않는다() {
        val archive = zip("자기.zip", listOf("자기.zip" to "가짜 내용", "other.txt" to "정상"))
        val before = archive.readBytes()

        val result = run(archive, work, conflict = FileOpEngine.Conflict.OVERWRITE)

        assertTrue("아카이브가 바뀌었다", archive.readBytes().contentEquals(before))
        assertEquals(1, result.report.refusedSelfOverwrite)
        assertTrue(File(work, "other.txt").isFile)
    }

    /** 중간 폴더 자리에 **파일**이 있으면 그것을 건드리지 않는다. */
    @Test
    fun 폴더_자리의_파일을_지우지_않는다() {
        val dest = File(work, "d")
        dest.mkdirs()
        File(dest, "a").writeText("나는 파일이다")

        val archive = zip("x.zip", listOf("a/b.txt" to "안쪽", "c.txt" to "정상"))
        val result = run(archive, dest)

        assertEquals("나는 파일이다", File(dest, "a").readText())
        assertTrue(File(dest, "c.txt").isFile)
        assertEquals(1, result.report.blockedDirs)
    }

    // ---- 데이터 보존 -------------------------------------------------------------

    /** 덮어쓰지 않기로 했으면 **원본이 그대로**여야 한다. */
    @Test
    fun 둘_다_보관이면_원본이_남는다() {
        val dest = File(work, "d")
        dest.mkdirs()
        File(dest, "같은이름.txt").writeText("원래 내용")

        val archive = zip("y.zip", listOf("같은이름.txt" to "아카이브 내용"))
        run(archive, dest, conflict = FileOpEngine.Conflict.KEEP_BOTH)

        assertEquals("원래 내용", File(dest, "같은이름.txt").readText())
        val both = dest.listFiles()!!.filter { it.name.startsWith("같은이름") }
        assertEquals("둘 다 남아야 한다", 2, both.size)
    }

    @Test
    fun 건너뛰기면_새_파일이_생기지_않는다() {
        val dest = File(work, "d")
        dest.mkdirs()
        File(dest, "같은이름.txt").writeText("원래 내용")

        val archive = zip("y.zip", listOf("같은이름.txt" to "아카이브 내용"))
        run(archive, dest, conflict = FileOpEngine.Conflict.SKIP)

        assertEquals("원래 내용", File(dest, "같은이름.txt").readText())
        assertEquals(1, dest.listFiles()!!.size)
    }

    /**
     * **수정시각을 되살린다.**
     *
     * 되살리지 않으면 아카이브를 풀 때마다 수천 개 파일이 '지금' 이 되어 날짜 정렬이
     * 무너지고, 이어보기 키(`sha256(크기+이름+수정시각)`)도 함께 끊긴다.
     */
    @Test
    fun 수정시각을_되살린다() {
        val dest = File(work, "d")
        dest.mkdirs()
        val archive = zip("t.zip", listOf("a.txt" to "내용"))
        run(archive, dest)

        val got = File(dest, "a.txt").lastModified()
        // ZIP 의 시각 해상도는 2초다. 그 안이면 보존된 것으로 본다.
        assertTrue("시각이 $got 인데 $FIXED_TIME 이어야 한다", kotlin.math.abs(got - FIXED_TIME) <= 2000)
    }

    // ---- 임시 파일 ---------------------------------------------------------------

    /** 끝난 뒤 **임시 파일이 남지 않는다.** 숨김 이름이라 남으면 아무도 모른다. */
    @Test
    fun 성공한_풀기는_임시파일을_남기지_않는다() {
        val dest = File(work, "d")
        dest.mkdirs()
        val archive = zip("t.zip", listOf("a.txt" to "가", "b/c.txt" to "나"))
        run(archive, dest)
        assertEquals(emptyList<String>(), partials(dest))
    }

    /** 상한에 걸려 **중간에 끊긴 풀기도** 임시 파일을 남기지 않는다. */
    @Test
    fun 폭탄에_걸려도_임시파일이_남지_않는다() {
        val dest = File(work, "d")
        dest.mkdirs()
        val archive = File(work, "bomb.zip")
        ZipArchiveOutputStream(archive).use { out ->
            out.putArchiveEntry(ZipArchiveEntry("zero.bin"))
            val chunk = ByteArray(1 shl 20)
            repeat(64) { out.write(chunk) } // 64 MiB 의 0 — 압축비 상한에 걸린다
            out.closeArchiveEntry()
        }
        run(archive, dest)

        assertEquals(emptyList<String>(), partials(dest))
        assertFalse("상한에 걸렸는데 파일이 생겼다", File(dest, "zero.bin").exists())
    }

    // ---- 새 폴더 -----------------------------------------------------------------

    @Test
    fun 새_폴더는_이름이_겹치면_번호가_붙는다() {
        val archive = zip("t.zip", listOf("a.txt" to "가"))
        File(work, "풀린곳").mkdirs()

        val r1 = run(archive, work, newFolder = "풀린곳")
        assertTrue(r1.report.destDir.endsWith("(2)"))
        assertTrue(File(r1.report.destDir, "a.txt").isFile)

        val r2 = run(archive, work, newFolder = "풀린곳")
        assertTrue(r2.report.destDir.endsWith("(3)"))
    }

    /** 고른 항목만 푼다. 번호로 고르므로 같은 이름이 있어도 헷갈리지 않는다. */
    @Test
    fun 고른_항목만_푼다() {
        val dest = File(work, "d")
        dest.mkdirs()
        val archive = zip("t.zip", listOf("a.txt" to "가", "b.txt" to "나", "c.txt" to "다"))
        val result = run(archive, dest, indices = listOf(1))

        assertEquals(listOf("b.txt"), dest.list()!!.toList())
        assertEquals("나", File(dest, "b.txt").readText())
        assertTrue(result.outcome is FileOpEngine.Outcome.Done)
    }

    private companion object {
        /** 2026-01-02 03:04:00 UTC. 2초 해상도에 맞춰 짝수 초로 둔다. */
        const val FIXED_TIME = 1767323040000L
    }
}
