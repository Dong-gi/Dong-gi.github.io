package io.github.donggi.iroiroviewer.io

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.donggi.iroiroviewer.data.IroiroDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 편집기가 저장한 문서를 원본 자리에 쓰기([EditorSaver]) — **큐 안에서** 지금 표를 넣을 때 기대한 표와 견주는가. 반영이 큐에
 * 줄 서 있는 사이 다른 앱이 원본을 바꿨으면, 세션의 첫 저장이 아니어도 그 판을 휴지통에 남기고 쓴다(예전에는 넣기 전에만 견주어 그 판을 남기지
 * 않고 덮었다). 판단은 JVM 시험(`EditorSaverRulesTest`)이 본다.
 *
 * [TextSaverTest] 처럼 앱 전용 외부 디렉터리에서 돈다 — 저장 볼륨 아래이고 권한이 필요 없다. 휴지통은 가짜 볼륨에 생긴다.
 */
@RunWith(AndroidJUnit4::class)
class EditorSaverTest {

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
        work = File(root, "editorsavetest-${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        work.deleteRecursively()
        File(root, TrashStore.DIR_NAME).deleteRecursively()
        runBlocking { dao.inVolume(VOLUME_ID).forEach { dao.delete(it) } }
    }

    /** ZIP 머리로 시작하는 바이트 — `docx` 의 앞머리 확인([EditorSaver.looksLike])을 지난다. */
    private fun docx(text: String): ByteArray = byteArrayOf(0x50, 0x4B, 0x03, 0x04) + text.toByteArray()

    private fun save(target: File, bytes: ByteArray, keepPrevious: Boolean, expected: FileStamp?): Pair<FileOpEngine.Outcome, FileOpManager.Extra?> =
        runBlocking {
            val source = File(work, "commit-${System.nanoTime()}").apply { writeBytes(bytes) }
            var extra: FileOpManager.Extra? = null
            val request = FileOpManager.Request.SaveFromEditor(
                id = "e",
                path = target.absolutePath,
                source = source.absolutePath,
                kind = "docx",
                keepPrevious = keepPrevious,
                createNew = false,
                expected = expected,
            )
            val outcome = EditorSaver.save(request, volumes(), TrashStore(context, media), media, { extra = it }, {})
            outcome to extra
        }

    private fun trashCopies(name: String) = runBlocking {
        dao.inVolume(VOLUME_ID).filter { it.originalName == name && it.originalParent == work.canonicalPath }
    }

    @Test
    fun 줄_서_있는_사이_밖에서_바뀐_원본은_첫_저장이_아니어도_휴지통에_남긴다() {
        val f = File(work, "보고서.docx").apply { writeBytes(docx("첫 저장")) }
        // 반영을 넣을 때 본 표 — 그다음 큐에서 차례를 기다리는 사이 다른 앱이 고쳐 썼다.
        val seen = FileStamp.of(f)
        f.writeBytes(docx("다른 앱이 고친 판"))
        val elsewhere = f.readBytes()

        val mine = docx("편집기의 둘째 저장")
        val (outcome, extra) = save(f, mine, keepPrevious = false, expected = seen)
        assertEquals(FileOpEngine.Outcome.Done(1, 0, 0), outcome)
        assertArrayEquals(mine, f.readBytes())
        assertTrue("고치기 전 판을 남겼다고 말해야 한다", (extra as FileOpManager.Extra.DocSaved).keptPrevious)
        val copies = trashCopies("보고서.docx")
        assertEquals(1, copies.size)
        assertArrayEquals("다른 앱이 고친 판이 휴지통에 있어야 한다", elsewhere, TrashStore.storedFileOf(root.absolutePath, copies[0].uuid).readBytes())
    }

    @Test
    fun 바뀌지_않은_원본의_둘째_저장은_휴지통에_넣지_않는다() {
        val f = File(work, "보고서.docx").apply { writeBytes(docx("첫 저장")) }
        val mine = docx("둘째 저장")
        val (outcome, extra) = save(f, mine, keepPrevious = false, expected = FileStamp.of(f))
        assertEquals(FileOpEngine.Outcome.Done(1, 0, 0), outcome)
        assertArrayEquals(mine, f.readBytes())
        assertFalse((extra as FileOpManager.Extra.DocSaved).keptPrevious)
        assertTrue(trashCopies("보고서.docx").isEmpty())
    }

    private companion object {
        const val VOLUME_ID = "editor-save-test"
    }
}
