package io.github.donggi.iroiroviewer.io

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.donggi.iroiroviewer.data.IroiroDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 글 편집의 저장과 새 글 파일([TextSaver]). **파일시스템이 답하는 것만** 여기서 본다 — 임시본 → rename 으로 원본 자리에
 * 놓이는가, 고치기 전 판이 휴지통에 기록과 함께 남고 되살리면 지금 파일을 덮지 않는가, 밖에서 바뀐 파일·휴지통에 넣지 못한 판에서
 * 쓰지 않는가. 판단(어디를 고칠 수 있는가·표)은 JVM 시험(`TextSaverRulesTest`)이 본다.
 *
 * [FileOpEngineTest] 처럼 앱 전용 외부 디렉터리(`/storage/…/Android/data/…`)에서 돈다 — 저장 볼륨 아래라 [TextSaver.placeOf] 를
 * 지나고, 권한이 필요 없다. 휴지통은 가짜 볼륨(그 디렉터리를 볼륨 루트로 본다)에 생긴다.
 */
@RunWith(AndroidJUnit4::class)
class TextSaverTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val media = MediaIndex(context)
    private lateinit var root: File
    private lateinit var work: File

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

    @Before
    fun setUp() {
        root = context.getExternalFilesDirs(null).filterNotNull().first()
        work = File(root, "texttest-${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        work.deleteRecursively()
        File(root, TrashStore.DIR_NAME).deleteRecursively()
        // 이 시험이 남긴 휴지통 기록도 거둔다 — 파일은 위에서 지웠다.
        runBlocking { dao.inVolume(VOLUME_ID).forEach { dao.delete(it) } }
    }

    private fun save(
        file: File,
        bytes: ByteArray,
        expected: FileStamp?,
        keepPrevious: Boolean = true,
        overwriteChanged: Boolean = false,
        withoutPrevious: Boolean = false,
        volumes: List<VolumeRegistry.Volume> = volumes(),
    ): Pair<FileOpEngine.Outcome, FileOpManager.Extra?> = runBlocking {
        var extra: FileOpManager.Extra? = null
        val request = FileOpManager.Request.SaveText(
            id = "t",
            path = file.absolutePath,
            bytes = bytes,
            expected = expected ?: FileStamp(-1, -1),
            keepPrevious = keepPrevious,
            overwriteChanged = overwriteChanged,
            withoutPrevious = withoutPrevious,
        )
        val outcome = TextSaver.save(request, volumes, TrashStore(context, media), media, { extra = it }, {})
        outcome to extra
    }

    /** 이 시험의 폴더에서 휴지통으로 간 [name] 의 기록들. */
    private fun trashCopies(name: String) = runBlocking {
        dao.inVolume(VOLUME_ID).filter { it.originalName == name && it.originalParent == work.canonicalPath }
    }

    @Test
    fun 고친_글이_원본_자리에_놓이고_고치기_전_판이_휴지통에_남는다() {
        val f = File(work, "메모.txt").apply { writeBytes("첫 줄\r\n둘째 줄\r\n".toByteArray(Charsets.UTF_8)) }
        val before = f.readBytes()
        val stamp = FileStamp.of(f)
        val after = "첫 줄\r\n고친 줄\r\n".toByteArray(Charsets.UTF_8)

        val (outcome, extra) = save(f, after, stamp)
        assertEquals(FileOpEngine.Outcome.Done(1, 0, 0), outcome)
        assertArrayEquals("새 바이트가 그 자리에 있어야 한다", after, f.readBytes())
        val saved = extra as FileOpManager.Extra.TextSaved
        assertEquals(f.canonicalPath, saved.path)
        assertEquals(FileStamp.of(f), saved.stamp)
        assertTrue("고치기 전 판을 넣었다고 말해야 한다", saved.keptPrevious)
        // 임시본이 남지 않는다.
        assertTrue(work.listFiles()!!.none { it.name.startsWith(AtomicFileWriter.PART_PREFIX) })

        // 휴지통의 사본 — 기록(원래 자리·이름)과 원래 바이트.
        val copies = trashCopies("메모.txt")
        assertEquals(1, copies.size)
        assertEquals(f.canonicalFile.parent, copies[0].originalParent)
        assertArrayEquals(before, TrashStore.storedFileOf(root.absolutePath, copies[0].uuid).readBytes())
    }

    @Test
    fun 고치기_전_판을_되살리면_지금_파일을_덮지_않고_번호가_붙는다() = runBlocking {
        val f = File(work, "글.md").apply { writeText("옛 글\n") }
        save(f, "새 글\n".toByteArray(), FileStamp.of(f))
        val uuid = trashCopies("글.md").single().uuid

        val restored = TrashStore(context, media).restore(uuid, volumes())
        assertTrue("되살려야 한다: $restored", restored is TrashStore.Result.Done)
        assertEquals("새 글\n", f.readText())
        assertEquals("옛 글\n", File(work, "글 (2).md").readText())
    }

    @Test
    fun 밖에서_바뀐_파일은_덮어쓰지_않고_고르면_덮어쓴다() {
        val f = File(work, "바뀜.txt").apply { writeText("처음") }
        val stamp = FileStamp.of(f)
        // 다른 앱이 고쳐 썼다(크기와 시각이 바뀐다).
        f.writeText("다른 앱이 쓴 글")
        f.setLastModified(System.currentTimeMillis() - 60_000)

        val (refused, _) = save(f, "내 글".toByteArray(), stamp)
        assertEquals(FileOpEngine.Reason.CHANGED_ELSEWHERE, (refused as FileOpEngine.Outcome.Failed).reason)
        assertEquals("다른 앱이 쓴 글", f.readText())
        assertTrue("묻기 전에 휴지통에 넣지 않는다", trashCopies("바뀜.txt").isEmpty())

        val (outcome, _) = save(f, "내 글".toByteArray(), stamp, overwriteChanged = true)
        assertEquals(FileOpEngine.Outcome.Done(1, 0, 0), outcome)
        assertEquals("내 글", f.readText())
        // 덮어쓴 것은 다른 앱의 글이다 — 그것이 휴지통에 남는다.
        assertEquals(1, trashCopies("바뀜.txt").size)
    }

    @Test
    fun 휴지통에_남기지_못하면_쓰지_않고_고르면_쓴다() {
        val f = File(work, "판.txt").apply { writeText("원본") }
        val stamp = FileStamp.of(f)
        // 볼륨을 모르면 휴지통을 만들 수 없다.
        val (refused, _) = save(f, "고친".toByteArray(), stamp, volumes = emptyList())
        assertEquals(FileOpEngine.Reason.PREVIOUS_NOT_KEPT, (refused as FileOpEngine.Outcome.Failed).reason)
        assertEquals("원본", f.readText())

        val (outcome, extra) = save(f, "고친".toByteArray(), stamp, withoutPrevious = true, volumes = emptyList())
        assertEquals(FileOpEngine.Outcome.Done(1, 0, 0), outcome)
        assertEquals("고친", f.readText())
        assertFalse((extra as FileOpManager.Extra.TextSaved).keptPrevious)
    }

    @Test
    fun 두번째_저장은_휴지통에_넣지_않는다() {
        val f = File(work, "두번.txt").apply { writeText("1") }
        val (_, first) = save(f, "2".toByteArray(), FileStamp.of(f))
        val stamp = (first as FileOpManager.Extra.TextSaved).stamp
        save(f, "3".toByteArray(), stamp, keepPrevious = false)
        assertEquals("3", f.readText())
        assertEquals(1, trashCopies("두번.txt").size)
    }

    @Test
    fun 빈_파일의_첫_저장은_휴지통에_넣지_않는다() {
        val f = File(work, "새 글.txt").apply { writeBytes(ByteArray(0)) }
        val (outcome, extra) = save(f, "첫 글".toByteArray(), FileStamp.of(f))
        assertEquals(FileOpEngine.Outcome.Done(1, 0, 0), outcome)
        assertEquals("첫 글", f.readText())
        assertFalse((extra as FileOpManager.Extra.TextSaved).keptPrevious)
        assertTrue(trashCopies("새 글.txt").isEmpty())
    }

    @Test
    fun 지워진_파일을_덮어쓰기로_고르면_다시_만든다() {
        val f = File(work, "사라짐.txt").apply { writeText("있었다") }
        val stamp = FileStamp.of(f)
        assertTrue(f.delete())
        val (refused, _) = save(f, "다시".toByteArray(), stamp)
        assertEquals(FileOpEngine.Reason.CHANGED_ELSEWHERE, (refused as FileOpEngine.Outcome.Failed).reason)
        assertFalse(f.exists())

        val (outcome, extra) = save(f, "다시".toByteArray(), stamp, overwriteChanged = true)
        assertEquals(FileOpEngine.Outcome.Done(1, 0, 0), outcome)
        assertEquals("다시", f.readText())
        // 넣을 판이 없었다.
        assertFalse((extra as FileOpManager.Extra.TextSaved).keptPrevious)
    }

    @Test
    fun 저장_볼륨_밖에는_쓰지_않는다() {
        val inside = File(context.filesDir, "texttest-${System.nanoTime()}.txt").apply { writeText("앱 안") }
        try {
            val (outcome, _) = save(inside, "바꿈".toByteArray(), FileStamp.of(inside))
            assertEquals(FileOpEngine.Reason.PERMISSION, (outcome as FileOpEngine.Outcome.Failed).reason)
            assertEquals("앱 안", inside.readText())
        } finally {
            inside.delete()
        }
    }

    @Test
    fun 새_글_파일은_비어_있고_있는_이름은_EXISTS_다() = runBlocking {
        var extra: FileOpManager.Extra? = null
        val made = TextSaver.create(FileOpManager.Request.CreateText("n", work.absolutePath, "새 글.txt"), media, { extra = it }, {})
        assertEquals(FileOpEngine.Outcome.Done(1, 0, 0), made)
        val created = File(work, "새 글.txt")
        assertEquals(created.canonicalPath, (extra as FileOpManager.Extra.Saved).path)
        assertTrue(created.isFile)
        assertEquals(0L, created.length())

        created.writeText("고친 뒤")
        val again = TextSaver.create(FileOpManager.Request.CreateText("n2", work.absolutePath, "새 글.txt"), media, {}, {})
        assertEquals(FileOpEngine.Reason.EXISTS, (again as FileOpEngine.Outcome.Failed).reason)
        assertEquals("있던 파일을 건드리지 않는다", "고친 뒤", created.readText())

        val bad = TextSaver.create(FileOpManager.Request.CreateText("n3", work.absolutePath, "a/b.txt"), media, {}, {})
        assertEquals(FileOpEngine.Reason.INVALID_NAME, (bad as FileOpEngine.Outcome.Failed).reason)
        assertNull(File(work, "a").listFiles())
    }

    @Test
    fun 휴지통_사본의_사이드카에는_이름을_적지_않는다() {
        val f = File(work, "비밀메모.txt").apply { writeText("x") }
        save(f, "y".toByteArray(), FileStamp.of(f))
        val sidecars = File(root, TrashStore.DIR_NAME).listFiles()?.filter { it.name.endsWith(".json") }.orEmpty()
        assertNotNull(sidecars.firstOrNull())
        sidecars.forEach { s -> assertFalse(s.readText().contains("비밀메모")) }
    }

    private companion object {
        const val VOLUME_ID = "text-test"
    }
}
