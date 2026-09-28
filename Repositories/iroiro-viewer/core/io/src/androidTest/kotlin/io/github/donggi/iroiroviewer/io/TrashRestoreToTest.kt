package io.github.donggi.iroiroviewer.io

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.donggi.iroiroviewer.data.IroiroDatabase
import io.github.donggi.iroiroviewer.data.TrashEntryEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 14단계의 휴지통 — **고른 폴더로 되돌리기**와 **예약된 비우기의 멈춤·끝 알림**.
 *
 * `FileOpEngineTest` 와 같은 까닭으로 계측 시험이다 — 볼륨을 넘는 되돌리기가 정말 복사로 내려가는가, 휴지통 안의
 * 것이 사라졌을 때만 기록이 지워지는가는 파일시스템이 답한다. 같은 자리(앱 전용 외부 디렉터리)에서 돈다.
 */
@RunWith(AndroidJUnit4::class)
class TrashRestoreToTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dao get() = IroiroDatabase.get(context).trash()
    private lateinit var engine: FileOpEngine
    private lateinit var store: TrashStore
    private lateinit var work: File

    private val roots: List<File> by lazy { context.getExternalFilesDirs(null).filterNotNull() }
    private val primaryRoot: File get() = roots.first()
    private val secondaryRoot: File? get() = roots.getOrNull(1)

    private fun fakeVolumes(): List<VolumeRegistry.Volume> = roots.mapIndexed { i, dir ->
        VolumeRegistry.Volume(
            id = if (i == 0) "test-primary" else "test-secondary-$i",
            label = dir.absolutePath,
            path = dir.absolutePath,
            isPrimary = i == 0,
            isRemovable = i > 0,
            totalBytes = 0,
            freeBytes = 0,
        )
    }

    @Before
    fun setUp() {
        engine = FileOpEngine(MediaIndex(context))
        store = TrashStore(context, MediaIndex(context))
        work = File(primaryRoot, "restoreto-${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        work.deleteRecursively()
        secondaryRoot?.let { File(it, work.name).deleteRecursively() }
        roots.forEach { File(it, TrashStore.DIR_NAME).deleteRecursively() }
    }

    private fun file(parent: File, name: String, size: Int): File =
        File(parent, name).apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(size) { (it % 251).toByte() })
        }

    /** 휴지통에 보내고 그 기록을 돌려준다. */
    private suspend fun trash(victim: File): TrashEntryEntity {
        val r = store.moveToTrash(listOf(victim.absolutePath), fakeVolumes())
        assertTrue("휴지통으로 보내야 한다: $r", r is TrashStore.Result.Done)
        return dao.find((r as TrashStore.Result.Done).uuids.single())!!
    }

    private fun stored(entry: TrashEntryEntity): File {
        val volume = fakeVolumes().first { it.id == entry.volumeId }
        return File(File(volume.path, TrashStore.DIR_NAME), entry.uuid)
    }

    @Test
    fun 같은_볼륨의_고른_폴더로_원래_이름으로_되돌린다() = runBlocking {
        val victim = file(work, "원래 이름.txt", 321)
        val bytes = victim.readBytes()
        val entry = trash(victim)
        val picked = File(work, "고른 폴더").apply { mkdirs() }

        val outcome = store.restoreTo(listOf(entry.uuid), picked.absolutePath, FileOpEngine.Conflict.SKIP, fakeVolumes(), engine) { }

        assertEquals(FileOpEngine.Outcome.Done(1, 0, 0), outcome)
        val back = File(picked, "원래 이름.txt")
        assertTrue("uuid 가 아니라 원래 이름으로 나와야 한다", back.isFile)
        assertTrue(bytes.contentEquals(back.readBytes()))
        assertFalse(stored(entry).exists())
        assertNull("휴지통 안의 것이 사라졌으면 기록도 지운다", dao.find(entry.uuid))
        Unit
    }

    @Test
    fun 볼륨을_넘는_폴더도_되돌린다() = runBlocking {
        val second = secondaryRoot
        assumeNotNull("SD 볼륨이 없으면 EXDEV 길을 타지 않는다", second)
        val tree = File(work, "묶음").apply { mkdirs() }
        file(tree, "하나.txt", 100)
        file(File(tree, "안쪽"), "둘.txt", 200)
        val entry = trash(tree)
        val picked = File(second!!, work.name).apply { mkdirs() }

        val outcome = store.restoreTo(listOf(entry.uuid), picked.absolutePath, FileOpEngine.Conflict.SKIP, fakeVolumes(), engine) { }

        assertTrue("다 옮겨야 한다: $outcome", outcome is FileOpEngine.Outcome.Done && outcome.failed == 0)
        assertEquals(100L, File(picked, "묶음/하나.txt").length())
        assertEquals(200L, File(picked, "묶음/안쪽/둘.txt").length())
        assertFalse("볼륨을 넘으면 복사 뒤 원본을 지운다", stored(entry).exists())
        assertNull(dao.find(entry.uuid))
        // 볼륨을 넘는 복사는 임시본을 거친다 — 남은 것이 없어야 한다.
        assertEquals(0, File(picked, "묶음").listFiles { f -> f.name.endsWith(AtomicFileWriter.PART_SUFFIX) }.orEmpty().size)
        Unit
    }

    @Test
    fun 이름이_겹치면_고른_규칙을_따르고_건너뛴_것은_휴지통에_남는다() = runBlocking {
        val picked = File(work, "고른 폴더").apply { mkdirs() }
        file(picked, "겹침.txt", 5)
        val entry = trash(file(work, "겹침.txt", 9))

        val (over, blocked) = store.restoreConflicts(listOf(entry.uuid), picked.absolutePath)
        assertEquals(listOf("겹침.txt"), over)
        assertTrue(blocked.isEmpty())

        val skipped = store.restoreTo(listOf(entry.uuid), picked.absolutePath, FileOpEngine.Conflict.SKIP, fakeVolumes(), engine) { }
        assertEquals(FileOpEngine.Outcome.Done(0, 1, 0), skipped)
        assertEquals("건너뛰었으면 대상은 그대로다", 5L, File(picked, "겹침.txt").length())
        assertTrue("건너뛴 것은 휴지통에 남는다", stored(entry).exists())
        assertNotNull("남은 것의 기록을 지우면 되살릴 길이 끊긴다", dao.find(entry.uuid))

        val kept = store.restoreTo(listOf(entry.uuid), picked.absolutePath, FileOpEngine.Conflict.KEEP_BOTH, fakeVolumes(), engine) { }
        assertEquals(FileOpEngine.Outcome.Done(1, 0, 0), kept)
        assertEquals(5L, File(picked, "겹침.txt").length())
        assertEquals(9L, File(picked, "겹침 (2).txt").length())
        assertNull(dao.find(entry.uuid))
        Unit
    }

    @Test
    fun 종류가_다른_충돌은_덮어쓰지_않는다() = runBlocking {
        val picked = File(work, "고른 폴더").apply { mkdirs() }
        File(picked, "같은이름").mkdirs()
        val entry = trash(file(work, "같은이름", 7))

        val (over, blocked) = store.restoreConflicts(listOf(entry.uuid), picked.absolutePath)
        assertTrue(over.isEmpty())
        assertEquals(listOf("같은이름"), blocked)

        val outcome = store.restoreTo(listOf(entry.uuid), picked.absolutePath, FileOpEngine.Conflict.OVERWRITE, fakeVolumes(), engine) { }
        assertTrue("폴더 자리에 파일을 덮어쓰면 안 된다: $outcome", outcome is FileOpEngine.Outcome.Done && outcome.failed == 1)
        assertTrue(File(picked, "같은이름").isDirectory)
        assertTrue(stored(entry).exists())
        Unit
    }

    @Test
    fun 휴지통_안으로는_되돌리지_않는다() = runBlocking {
        val entry = trash(file(work, "a.txt", 3))
        val trashDir = File(primaryRoot, TrashStore.DIR_NAME)
        val outcome = store.restoreTo(listOf(entry.uuid), trashDir.absolutePath, FileOpEngine.Conflict.SKIP, fakeVolumes(), engine) { }
        assertTrue(outcome is FileOpEngine.Outcome.Failed)
        assertTrue(stored(entry).exists())
        Unit
    }

    @Test
    fun 멈춤_신호를_보면_기간이_지난_것도_지우지_않는다() = runBlocking {
        val entry = trash(file(work, "오래된것.txt", 11))
        // 40일 전에 지운 것으로 적는다.
        dao.insert(entry.copy(deletedAt = System.currentTimeMillis() - 40L * 24 * 3600 * 1000))

        val stopped = store.reconcileAndPurgeExpired(fakeVolumes(), engine, isStopped = { true })
        assertEquals(0, stopped)
        assertTrue("멈춘 뒤에 지웠다", stored(entry).exists())
        assertNotNull(dao.find(entry.uuid))

        val purged = store.reconcileAndPurgeExpired(fakeVolumes(), engine)
        // 앞서 죽은 시험이 남긴 오래된 기록이 있으면 그것도 함께 지워진다 — 적어도 하나.
        assertTrue(purged >= 1)
        assertFalse(stored(entry).exists())
        assertNull(dao.find(entry.uuid))
        Unit
    }

    @Test
    fun 큐의_정합성_검사는_끝을_알리고_멈춤이면_거짓을_알린다() = runBlocking {
        val done = CompletableDeferred<Boolean>()
        FileOpManager.enqueue(context, FileOpManager.Request.Reconcile(done = done))
        assertTrue("끝까지 돌았다고 알려야 한다", withTimeout(20_000) { done.await() })

        val stop = AtomicBoolean(true)
        val stoppedDone = CompletableDeferred<Boolean>()
        FileOpManager.enqueue(context, FileOpManager.Request.Reconcile(done = stoppedDone, stop = stop))
        assertFalse("멈춘 것을 끝까지 돌았다고 알렸다", withTimeout(20_000) { stoppedDone.await() })
        Unit
    }
}
