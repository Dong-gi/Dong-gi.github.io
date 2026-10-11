package io.github.donggi.iroiroviewer.io

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * 새 오피스 문서([NewDocuments] — 서식을 옮겨 적는다)의 차례를 **이 기기의 zlib 과 자산으로** 본다: 네 종류가
 * 그 이름 그대로 놓이고 서식과 같은 항목을 갖는가(머리의 크기를 쓰개가 견주므로, 그 셈이 기기에서도 맞는지가 요점이다), 있는 이름·어긋난
 * 이름·모르는 종류·저장 볼륨 밖이면 아무것도 남기지 않는가.
 *
 * [TextSaverTest] 처럼 앱 전용 외부 디렉터리(`/storage/…/Android/data/…`)에서 돈다 — 저장 볼륨 아래이고 권한이 필요 없다.
 */
@RunWith(AndroidJUnit4::class)
class NewDocumentsTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val media = MediaIndex(context)
    private lateinit var work: File

    @Before
    fun setUp() {
        val root = context.getExternalFilesDirs(null).filterNotNull().first()
        work = File(root, "newdoc-${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        work.deleteRecursively()
    }

    private fun create(name: String, kind: String, dir: File = work): Pair<FileOpEngine.Outcome, FileOpManager.Extra?> = runBlocking {
        var extra: FileOpManager.Extra? = null
        val r = FileOpManager.Request.CreateDocument(UUID.randomUUID().toString(), dir.absolutePath, name, kind)
        NewDocuments.create(r, context.assets, media, { extra = it }, {}, now = 1_760_000_000_000L) to extra
    }

    private fun reason(outcome: FileOpEngine.Outcome): FileOpEngine.Reason? = (outcome as? FileOpEngine.Outcome.Failed)?.reason

    private fun partials(): List<String> = work.list().orEmpty().filter { it.endsWith(".part") }

    private fun names(zip: File): List<String> = ZipFile(zip).use { z -> z.entries().toList().map { it.name } }

    @Test
    fun 네_종류의_새_문서가_서식_그대로_그_이름에_놓인다() {
        for (kind in BlankDocuments.KINDS) {
            val (outcome, extra) = create("새 문서.$kind", kind)
            assertEquals(kind, FileOpEngine.Outcome.Done(1, 0, 0), outcome)
            val made = File(work, "새 문서.$kind")
            assertEquals(made.canonicalPath, (extra as FileOpManager.Extra.Saved).path)
            val template = File.createTempFile("blank", ".$kind", context.cacheDir)
            try {
                context.assets.open(BlankDocuments.assetPath(kind)).use { input -> template.outputStream().use { input.copyTo(it) } }
                assertEquals(kind, names(template), names(made))
            } finally {
                template.delete()
            }
            ZipFile(made).use { z ->
                // 모든 항목을 끝까지 읽는다 — CRC 가 틀리면 던진다.
                for (e in z.entries()) z.getInputStream(e).use { it.readBytes() }
                val stamped = z.getEntry(if (kind == "hwpx") "Contents/content.hpf" else "docProps/core.xml")
                val text = z.getInputStream(stamped).use { String(it.readBytes(), Charsets.UTF_8) }
                assertTrue(kind, "2025-10-09T08:53:20Z" in text)
                assertFalse(kind, BlankDocuments.STAMP in text)
                if (kind == "hwpx") assertEquals(ZipEntry.STORED, z.getEntry("mimetype").method)
            }
        }
        assertTrue(partials().isEmpty())
    }

    @Test
    fun 있는_이름이면_EXISTS_이고_있던_파일을_건드리지_않는다() {
        val there = File(work, "있음.docx").apply { writeBytes(byteArrayOf(9, 9)) }
        val (outcome, _) = create("있음.docx", "docx")
        assertEquals(FileOpEngine.Reason.EXISTS, reason(outcome))
        assertArrayEquals(byteArrayOf(9, 9), there.readBytes())
        assertFalse("번호를 붙여 만들지 않는다", File(work, "있음 (2).docx").exists())
        assertTrue(partials().isEmpty())
    }

    @Test
    fun 이름과_종류가_어긋나거나_서식이_없는_종류면_만들지_않는다() {
        assertEquals(FileOpEngine.Reason.INVALID_NAME, reason(create("보고서.docm", "docx").first))
        assertEquals(FileOpEngine.Reason.INVALID_NAME, reason(create("a/b.docx", "docx").first))
        assertEquals(FileOpEngine.Reason.NOT_EDITABLE, reason(create("표.odt", "odt").first))
        assertTrue(work.list().orEmpty().isEmpty())
    }

    @Test
    fun 새_문서도_저장_볼륨_밖에는_만들지_않는다() {
        val (outcome, _) = create("밖.docx", "docx", dir = context.filesDir)
        assertEquals(FileOpEngine.Reason.PERMISSION, reason(outcome))
        assertFalse(File(context.filesDir, "밖.docx").exists())
    }
}
