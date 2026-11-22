package io.github.donggi.iroiroviewer.io

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.donggi.iroiroviewer.data.IroiroDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * PDF 잠그기·풀기의 **제자리 바꾸기**([PdfLockSaver]) — 파일시스템이 답하는 것만 본다: 바꾼 판이 원본 자리에 놓이고 바꾸기 전 판이 휴지통에 남는가,
 * 연 뒤에·쓰는 동안 원본이 바뀌면 놓지 않는가, 엔진이 거절하면 원본이 그대로인가, 암호 사본을 덮는가. 엔진은 가짜다(PDF 를 짓는 일은
 * `feature:docview` 의 JVM 시험 `PdfLockerTest` 가 본다).
 */
@RunWith(AndroidJUnit4::class)
class PdfLockSaverTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val media = MediaIndex(context)
    private lateinit var root: File
    private lateinit var work: File
    private var previousEngine: PdfPagesSupport.Engine? = null

    private val dao get() = IroiroDatabase.get(context).trash()

    private fun volumes(): List<VolumeRegistry.Volume> = listOf(
        VolumeRegistry.Volume(
            id = VOLUME_ID,
            label = root.absolutePath,
            path = root.absolutePath,
            isPrimary = true,
            isRemovable = false,
            totalBytes = 0,
            freeBytes = 0,
        ),
    )

    /** 받은 것을 적고, [during] 을 부르고, 거절하거나 표시가 붙은 바이트를 쓴다. */
    private class FakeEngine(
        private val during: (String) -> Unit = {},
        private val refuse: FileOpEngine.Reason? = null,
    ) : PdfPagesSupport.Engine {
        var password: String? = null
        var newPassword: String? = null
        var calls = 0

        override fun inspect(path: String, password: CharArray?, checkCancelled: () -> Unit) = PdfPagesSupport.Inspection.Unreadable

        override fun write(
            parts: List<PdfPagesSupport.Part>,
            titles: List<String>,
            output: PdfPagesSupport.Output,
            checkCancelled: () -> Unit,
            onPage: (Int, Int, String) -> Unit,
        ) = PdfPagesSupport.Written.Refused(FileOpEngine.Reason.IO)

        override fun relock(
            path: String,
            output: PdfPagesSupport.Output,
            password: CharArray?,
            newPassword: CharArray?,
            checkCancelled: () -> Unit,
            onProgress: (Int, Int) -> Unit,
        ): PdfPagesSupport.Written {
            calls++
            this.password = password?.concatToString()
            this.newPassword = newPassword?.concatToString()
            during(path)
            refuse?.let { return PdfPagesSupport.Written.Refused(it) }
            val b = "%PDF-relocked:${this.newPassword ?: "none"}".toByteArray(Charsets.UTF_8)
            output.write(b, 0, b.size)
            return PdfPagesSupport.Written.Done(0)
        }
    }

    @Before
    fun setUp() {
        root = context.getExternalFilesDirs(null).filterNotNull().first()
        work = File(root, "pdflocktest-${System.nanoTime()}").apply { mkdirs() }
        previousEngine = PdfPagesSupport.engine
    }

    @After
    fun tearDown() {
        PdfPagesSupport.engine = previousEngine
        work.deleteRecursively()
        File(root, TrashStore.DIR_NAME).deleteRecursively()
        runBlocking { dao.inVolume(VOLUME_ID).forEach { dao.delete(it) } }
    }

    private fun run(
        file: File,
        expected: FileStamp?,
        password: CharArray? = null,
        newPassword: CharArray? = "새암호".toCharArray(),
        withoutPrevious: Boolean = false,
        volumes: List<VolumeRegistry.Volume> = volumes(),
    ): Pair<FileOpEngine.Outcome, FileOpManager.Extra?> = runBlocking {
        var extra: FileOpManager.Extra? = null
        val request = FileOpManager.Request.PdfRelock("r", file.absolutePath, expected ?: FileStamp(-1, -1), password, newPassword, withoutPrevious)
        val outcome = PdfLockSaver.relock(context, request, volumes, TrashStore(context, media), media, {}, { extra = it }, {})
        outcome to extra
    }

    private fun pdf(name: String, body: String = "%PDF-1.7 original") = File(work, name).apply { writeText(body) }

    private fun trashCopies(name: String) = runBlocking {
        dao.inVolume(VOLUME_ID).filter { it.originalName == name && it.originalParent == work.canonicalPath }
    }

    private fun noPartials() = assertTrue("임시본이 남았다", work.listFiles()!!.none { it.name.startsWith(AtomicFileWriter.PART_PREFIX) })

    @Test
    fun 잠근_판이_원본_자리에_놓이고_바꾸기_전_판이_휴지통에_남고_암호_사본을_덮는다() {
        val engine = FakeEngine().also { PdfPagesSupport.engine = it }
        val f = pdf("문서.pdf")
        val before = f.readBytes()
        val newPassword = "새암호".toCharArray()
        val (outcome, extra) = run(f, FileStamp.of(f), newPassword = newPassword)
        assertEquals(FileOpEngine.Outcome.Done(1, 0, 0), outcome)
        assertEquals("%PDF-relocked:새암호", f.readText())
        assertEquals("새암호", engine.newPassword)
        val r = extra as FileOpManager.Extra.PdfRelocked
        assertTrue(r.locked)
        assertTrue(r.keptPrevious)
        assertEquals(FileStamp.of(f), r.stamp)
        val copies = trashCopies("문서.pdf")
        assertEquals(1, copies.size)
        assertArrayEquals(before, TrashStore.storedFileOf(root.absolutePath, copies[0].uuid).readBytes())
        assertTrue("암호 사본을 덮어야 한다", newPassword.all { it == '\u0000' })
        noPartials()
    }

    @Test
    fun 연_뒤에_바뀐_파일은_엔진을_부르지_않고_CHANGED() {
        val engine = FakeEngine().also { PdfPagesSupport.engine = it }
        val f = pdf("바뀜.pdf")
        val stamp = FileStamp.of(f)
        f.writeText("%PDF-1.7 someone else")
        f.setLastModified(System.currentTimeMillis() - 60_000)
        val (outcome, _) = run(f, stamp)
        assertEquals(FileOpEngine.Reason.CHANGED, (outcome as FileOpEngine.Outcome.Failed).reason)
        assertEquals(0, engine.calls)
        assertEquals("%PDF-1.7 someone else", f.readText())
        noPartials()
    }

    @Test
    fun 쓰는_동안_원본이_바뀌면_놓지_않고_CHANGED() {
        // 엔진이 원본을 읽는 사이 다른 앱이 고쳐 썼다 — 크기와 시각이 바뀐다.
        PdfPagesSupport.engine = FakeEngine(during = { path ->
            File(path).writeText("%PDF-1.7 changed while writing")
            File(path).setLastModified(System.currentTimeMillis() - 120_000)
        })
        val f = pdf("쓰는중.pdf")
        val (outcome, _) = run(f, FileStamp.of(f))
        assertEquals(FileOpEngine.Reason.CHANGED, (outcome as FileOpEngine.Outcome.Failed).reason)
        assertEquals("%PDF-1.7 changed while writing", f.readText())
        assertTrue("휴지통에 넣지 않는다", trashCopies("쓰는중.pdf").isEmpty())
        noPartials()
    }

    @Test
    fun 엔진이_거절하면_원본이_그대로다() {
        PdfPagesSupport.engine = FakeEngine(refuse = FileOpEngine.Reason.PDF_LOCKED)
        val f = pdf("틀림.pdf")
        val before = f.readBytes()
        val password = "틀린암호".toCharArray()
        val (outcome, _) = run(f, FileStamp.of(f), password = password, newPassword = null)
        assertEquals(FileOpEngine.Reason.PDF_LOCKED, (outcome as FileOpEngine.Outcome.Failed).reason)
        assertArrayEquals(before, f.readBytes())
        assertTrue(trashCopies("틀림.pdf").isEmpty())
        assertTrue(password.all { it == '\u0000' })
        noPartials()
    }

    @Test
    fun 휴지통에_남기지_못하면_묻고_그래도_바꾸기면_남기지_않고_바꾼다() {
        val engine = FakeEngine().also { PdfPagesSupport.engine = it }
        val f = pdf("못남김.pdf")
        val before = f.readBytes()
        // 볼륨을 모른다 — 휴지통에 넣을 수 없다.
        val (refused, _) = run(f, FileStamp.of(f), password = CharArray(0), newPassword = null, volumes = emptyList())
        assertEquals(FileOpEngine.Reason.PREVIOUS_NOT_KEPT, (refused as FileOpEngine.Outcome.Failed).reason)
        assertArrayEquals(before, f.readBytes())
        noPartials()
        val (outcome, extra) = run(f, FileStamp.of(f), password = CharArray(0), newPassword = null, withoutPrevious = true, volumes = emptyList())
        assertEquals(FileOpEngine.Outcome.Done(1, 0, 0), outcome)
        assertEquals("%PDF-relocked:none", f.readText())
        assertNull(engine.newPassword)
        val r = extra as FileOpManager.Extra.PdfRelocked
        assertTrue(!r.locked && !r.keptPrevious)
        noPartials()
    }

    private companion object {
        const val VOLUME_ID = "pdflocktest"
    }
}
