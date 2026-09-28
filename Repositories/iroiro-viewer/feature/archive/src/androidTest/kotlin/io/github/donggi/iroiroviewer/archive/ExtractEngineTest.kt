package io.github.donggi.iroiroviewer.archive

import androidx.test.platform.app.InstrumentationRegistry
import io.github.donggi.iroiroviewer.io.FileOpEngine
import io.github.donggi.iroiroviewer.io.FileOpManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
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
        password: CharArray? = null,
        onProgress: (FileOpEngine.Progress) -> Unit = { },
    ) = runBlocking {
        engine.run(
            FileOpManager.Request.Extract(
                id = "t",
                archivePath = archive.absolutePath,
                entryIndices = indices,
                destParent = dest.absolutePath,
                newFolderName = newFolder,
                conflict = conflict,
                password = password,
            ),
            onProgress,
        )
    }

    /** 폴더 엔트리까지 시각을 박은 ZIP. 폴더 이름은 `/` 로 끝난다. */
    private fun zipWithDirs(name: String, dirs: List<String>, files: List<Pair<String, String>>): File {
        val f = File(work, name)
        ZipArchiveOutputStream(f).use { out ->
            for (d in dirs) {
                val e = ZipArchiveEntry(d)
                e.time = FIXED_TIME
                out.putArchiveEntry(e)
                out.closeArchiveEntry()
            }
            for ((entryName, body) in files) {
                val e = ZipArchiveEntry(entryName)
                e.time = FIXED_TIME
                out.putArchiveEntry(e)
                out.write(body.toByteArray())
                out.closeArchiveEntry()
            }
        }
        return f
    }

    /** tar.gz — 파일·폴더(시각)·심볼릭 링크. commons-compress 의 작성기가 만든다. */
    private fun tarGz(name: String): File {
        val f = File(work, name)
        TarArchiveOutputStream(GzipCompressorOutputStream(f.outputStream())).use { out ->
            val dir = TarArchiveEntry("책/")
            dir.setModTime(FIXED_TIME)
            out.putArchiveEntry(dir)
            out.closeArchiveEntry()
            val body = "쪽 하나".toByteArray()
            val file = TarArchiveEntry("책/쪽.txt")
            file.size = body.size.toLong()
            file.setModTime(FIXED_TIME)
            out.putArchiveEntry(file)
            out.write(body)
            out.closeArchiveEntry()
            val link = TarArchiveEntry("책/바로가기", TarArchiveEntry.LF_SYMLINK)
            link.linkName = "쪽.txt"
            out.putArchiveEntry(link)
            out.closeArchiveEntry()
        }
        return f
    }

    /** 계측 APK 의 자산으로 붙은 암호 표본(`format:archive` 의 JVM 시험과 같은 벌). */
    private fun asset(name: String): File {
        val ctx = InstrumentationRegistry.getInstrumentation().context
        return File(work, name.substringAfterLast('/')).apply {
            outputStream().use { out -> ctx.assets.open(name).use { it.copyTo(out) } }
        }
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

    /**
     * **폴더의 수정시각도 되살린다** — 풀기가 만든 폴더만, 아카이브에 시각이 적힌 것만, 모든 쓰기가 끝난 뒤에.
     * 이미 있던 폴더('여기에 풀기' 의 목적지)는 사용자의 것이라 건드리지 않는다.
     */
    @Test
    fun 폴더의_수정시각을_되살린다() {
        val dest = File(work, "d")
        dest.mkdirs()
        val archive = zipWithDirs("dirs.zip", listOf("a/", "a/b/"), listOf("a/b/c.txt" to "내용", "a/d.txt" to "둘"))
        run(archive, dest)

        for (p in listOf("a", "a/b")) {
            val got = File(dest, p).lastModified()
            assertTrue("$p 의 시각이 $got 인데 $FIXED_TIME 이어야 한다", kotlin.math.abs(got - FIXED_TIME) <= 2000)
        }
        assertTrue("이미 있던 목적지가 아카이브의 시각을 받았다", kotlin.math.abs(dest.lastModified() - FIXED_TIME) > 2000)
    }

    /** tar.gz 를 순차로 풀고, 링크는 풀지 않고 세고, 폴더 시각을 되살리고, 진행 바는 **아카이브 크기**로 끝난다. */
    @Test
    fun tar_gz_를_풀고_링크는_센다() {
        val dest = File(work, "d")
        dest.mkdirs()
        val archive = tarGz("책.tar.gz")
        val progress = ArrayList<FileOpEngine.Progress>()
        val result = run(archive, dest, onProgress = { progress += it })

        assertTrue("${result.outcome}", result.outcome is FileOpEngine.Outcome.Done)
        assertEquals("쪽 하나", File(dest, "책/쪽.txt").readText())
        assertFalse("링크가 풀렸다", File(dest, "책/바로가기").exists())
        assertEquals(1, result.report.refusedLink)
        val dirTime = File(dest, "책").lastModified()
        assertTrue("폴더 시각이 $dirTime", kotlin.math.abs(dirTime - FIXED_TIME) <= 2000)
        assertTrue("진행 보고가 없다", progress.isNotEmpty())
        assertTrue("분모가 아카이브 크기가 아니다: ${progress.last()}", progress.all { it.bytesTotal == archive.length() })
        assertEquals(emptyList<String>(), partials(dest))
    }

    /**
     * **7z 의 폴더도 되살린다.** 예전에는 7z 리더가 폴더 항목을 소비자에게 주지 않아 빈 폴더조차 생기지 않았다.
     * 만든 것은 commons-compress 의 작성기다(폴더 항목에 시각을 적는다).
     */
    @Test
    fun 칠z_의_빈_폴더를_만들고_시각을_되살린다() {
        val dest = File(work, "d").apply { mkdirs() }
        val f = File(work, "dirs.7z")
        org.apache.commons.compress.archivers.sevenz.SevenZOutputFile(f).use { out ->
            for (name in listOf("빈폴더", "찬폴더")) {
                val d = org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry()
                d.name = name
                d.isDirectory = true
                d.lastModifiedDate = java.util.Date(FIXED_TIME)
                out.putArchiveEntry(d)
                out.closeArchiveEntry()
            }
            val e = org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry()
            e.name = "찬폴더/글.txt"
            e.lastModifiedDate = java.util.Date(FIXED_TIME)
            out.putArchiveEntry(e)
            out.write("내용".toByteArray())
            out.closeArchiveEntry()
        }
        val result = run(f, dest)

        assertTrue("${result.outcome}", result.outcome is FileOpEngine.Outcome.Done)
        assertTrue("빈 폴더가 생기지 않았다", File(dest, "빈폴더").isDirectory)
        for (p in listOf("빈폴더", "찬폴더")) {
            val got = File(dest, p).lastModified()
            assertTrue("$p 의 시각이 $got 인데 $FIXED_TIME 이어야 한다", kotlin.math.abs(got - FIXED_TIME) <= 2000)
        }
    }

    /**
     * `tar -czf x.tgz .` 가 맨 앞에 두는 `./` 항목은 **풀어 넣는 새 폴더의 시각**이다. '여기에 풀기' 라면 그 폴더는
     * 이미 있던 사용자의 것이라 건드리지 않는다.
     */
    @Test
    fun 맨_위_폴더_항목의_시각은_새_폴더가_받는다() {
        val f = File(work, "dot.tar")
        TarArchiveOutputStream(f.outputStream()).use { out ->
            val root = TarArchiveEntry("./")
            root.setModTime(FIXED_TIME)
            out.putArchiveEntry(root)
            out.closeArchiveEntry()
            val body = "쪽".toByteArray()
            val file = TarArchiveEntry("./a.txt")
            file.size = body.size.toLong()
            file.setModTime(FIXED_TIME)
            out.putArchiveEntry(file)
            out.write(body)
            out.closeArchiveEntry()
        }
        val parent = File(work, "p").apply { mkdirs() }
        val result = run(f, parent, newFolder = "dot")
        assertTrue("${result.outcome}", result.outcome is FileOpEngine.Outcome.Done)
        assertEquals(0, result.report.refusedUnsafe)
        val made = File(parent, "dot")
        assertEquals("쪽", File(made, "a.txt").readText())
        assertTrue("새 폴더의 시각이 ${made.lastModified()}", kotlin.math.abs(made.lastModified() - FIXED_TIME) <= 2000)

        val here = File(work, "here").apply { mkdirs() }
        run(f, here)
        assertTrue("이미 있던 폴더가 아카이브의 시각을 받았다", kotlin.math.abs(here.lastModified() - FIXED_TIME) > 2000)
    }

    /**
     * **취소하면 쓰던 항목의 임시 파일이 남지 않는다.** 리더는 취소를 `finish` 없이 위로 던진다 — 예전에는 그 항목의
     * 숨은 임시 파일이 한 시간(`sweepPartials` 의 유예) 동안 남았다. 첫 진행 보고가 온 뒤 취소한다.
     */
    @Test
    fun 취소하면_임시_파일이_남지_않는다() = runBlocking {
        val dest = File(work, "d").apply { mkdirs() }
        val f = File(work, "big.zip")
        val random = java.util.Random(7)
        ZipArchiveOutputStream(f).use { out ->
            val chunk = ByteArray(1 shl 20)
            for (i in 0 until 4) {
                out.putArchiveEntry(ZipArchiveEntry("큰$i.bin"))
                repeat(12) {
                    random.nextBytes(chunk)
                    out.write(chunk)
                }
                out.closeArchiveEntry()
            }
        }
        val started = kotlinx.coroutines.CompletableDeferred<Unit>()
        val job = launch(kotlinx.coroutines.Dispatchers.Default) {
            engine.run(
                FileOpManager.Request.Extract(
                    id = "cancel",
                    archivePath = f.absolutePath,
                    entryIndices = null,
                    destParent = dest.absolutePath,
                    newFolderName = null,
                    conflict = FileOpEngine.Conflict.KEEP_BOTH,
                    password = null,
                ),
            ) { if (it.bytesDone > 0) started.complete(Unit) }
        }
        started.await()
        job.cancel()
        job.join()
        assertEquals(emptyList<String>(), partials(dest))
        assertFalse("취소했는데 네 항목이 다 풀렸다", (0 until 4).all { File(dest, "큰$it.bin").isFile })
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

    // ---- 암호 ---------------------------------------------------------------------

    /**
     * **암호를 받으면 잠긴 항목이 원본 그대로 풀린다** — 전통 ZIP 암호(반디집)·WinZip AES
     * (pyzipper)·헤더까지 잠긴 7z(py7zr). 셋 다 우리가 잠그지 않은 표본이다.
     */
    @Test
    fun 암호를_받으면_잠긴_항목을_푼다() {
        val hello = asset("archivecrypt/hello.txt").readBytes()
        val pattern = asset("archivecrypt/pattern.bin").readBytes()
        for (name in listOf("zipcrypto-bandizip.zip", "aes256-pyzipper.zip", "7z-aes-header-py7zr.7z")) {
            val dest = File(work, "d-$name").apply { mkdirs() }
            val result = run(asset("archivecrypt/$name"), dest, password = "iroiro".toCharArray())

            assertTrue("$name: ${result.outcome}", result.outcome is FileOpEngine.Outcome.Done)
            assertEquals(name, 0, result.report.refusedEncrypted)
            assertTrue(name, File(dest, "hello.txt").readBytes().contentEquals(hello))
            assertTrue(name, File(dest, "pattern.bin").readBytes().contentEquals(pattern))
            assertEquals(name, emptyList<String>(), partials(dest))
        }
    }

    /** 암호가 없으면 잠긴 항목은 **거절로 센다.** 빈 파일이나 깨진 파일을 만들지 않는다. */
    @Test
    fun 암호가_없으면_잠긴_항목을_거절하고_센다() {
        val dest = File(work, "d").apply { mkdirs() }
        val result = run(asset("archivecrypt/aes256-pyzipper.zip"), dest)

        assertEquals(2, result.report.refusedEncrypted)
        assertTrue("잠긴 항목이 풀렸다: ${dest.list()?.toList()}", dest.list().isNullOrEmpty())
        assertEquals(emptyList<String>(), partials(dest))
    }

    /**
     * **틀린 암호는 쓰레기 파일을 남기지 않는다.** WinZip AES 는 항목 머리의 확인값에서,
     * 전통 ZIP 암호는 끝의 CRC 에서 걸린다 — 뒤의 것은 이미 쓴 뒤에 걸리므로 임시 파일을
     * 지우는 길까지 확인해야 한다.
     */
    @Test
    fun 틀린_암호는_파일을_남기지_않는다() {
        for (name in listOf("zipcrypto-bandizip.zip", "aes256-pyzipper.zip")) {
            val dest = File(work, "d-$name").apply { mkdirs() }
            run(asset("archivecrypt/$name"), dest, password = "틀린암호".toCharArray())

            assertTrue("$name: 틀린 암호로 파일이 생겼다 ${dest.list()?.toList()}", dest.list().isNullOrEmpty())
            assertEquals(name, emptyList<String>(), partials(dest))
        }
    }

    /**
     * **요청이 붙든 암호는 작업이 끝나면 0 으로 덮인다.** 요청 객체는 큐에 남아 있을 수 있고
     * (`FileOpManager.Request.Extract` 의 주석), 작업이 끝난 뒤의 사본은 쓸 곳이 없다.
     */
    @Test
    fun 작업이_끝나면_요청의_암호가_지워진다() {
        val pw = "iroiro".toCharArray()
        run(asset("archivecrypt/zipcrypto-bandizip.zip"), File(work, "d").apply { mkdirs() }, password = pw)
        assertTrue("암호가 남아 있다", pw.all { it == '\u0000' })
    }

    private companion object {
        /** 2026-01-02 03:04:00 UTC. 2초 해상도에 맞춰 짝수 초로 둔다. */
        const val FIXED_TIME = 1767323040000L
    }
}
