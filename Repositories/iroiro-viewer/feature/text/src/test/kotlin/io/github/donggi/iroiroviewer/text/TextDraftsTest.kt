package io.github.donggi.iroiroviewer.text

import io.github.donggi.iroiroviewer.io.FileStamp
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 저장하지 않은 고침(초안 — [TextDrafts])의 적기·읽기·지우기·치우기. 이름에 경로가 드러나지 않는 것도 본다.
 */
class TextDraftsTest {

    private lateinit var dir: File

    @BeforeTest
    fun setUp() {
        dir = TextDrafts.dirOf(Files.createTempDirectory("drafts").toFile())
    }

    @AfterTest
    fun tearDown() {
        dir.parentFile?.deleteRecursively()
    }

    private fun draft(text: String, session: Long = 7L) = TextDrafts.Draft(
        session = session,
        baseSize = 123L,
        baseModified = 1_700_000_000_000L,
        encoding = "CP949",
        selectionStart = 2,
        selectionEnd = 4,
        text = text,
    )

    @Test
    fun `적은 것을 그대로 읽는다 — 폴더가 없으면 만든다`() = runBlocking {
        val file = TextDrafts.fileOf(dir, "/storage/emulated/0/Documents/메모.txt")
        assertNull(TextDrafts.read(file))
        TextDrafts.write(file, draft("고치던 글\n😀 둘째 줄"))
        val back = assertNotNull(TextDrafts.read(file))
        assertEquals("고치던 글\n😀 둘째 줄", back.text)
        assertEquals(7L, back.session)
        assertEquals(123L, back.baseSize)
        assertEquals(1_700_000_000_000L, back.baseModified)
        assertEquals("CP949", back.encoding)
        assertEquals(2, back.selectionStart)
        assertEquals(4, back.selectionEnd)
        // 다시 적으면 덮는다 — 임시본이 남지 않는다.
        TextDrafts.write(file, draft("더 고친 글", session = 8L))
        assertEquals("더 고친 글", TextDrafts.read(file)?.text)
        assertEquals(listOf(file.name), dir.list()!!.toList())
    }

    @Test
    fun `이름은 경로의 해시다 — 경로가 드러나지 않는다`() {
        val path = "/storage/emulated/0/Documents/비밀 메모.txt"
        val file = TextDrafts.fileOf(dir, path)
        assertFalse(file.name.contains("비밀"))
        assertFalse(file.name.contains("Documents"))
        assertEquals(64, TextDrafts.keyOf(path).length)
        assertTrue(TextDrafts.keyOf(path).all { it in '0'..'9' || it in 'a'..'f' })
        // 같은 경로는 같은 이름, 다른 경로는 다른 이름.
        assertEquals(file, TextDrafts.fileOf(dir, path))
        assertTrue(file != TextDrafts.fileOf(dir, "$path.bak"))
    }

    /**
     * 다시 열 때 남은 초안을 어떻게 하는가 — 같은 글이면 묻지 않고 지우고, 같은 세션(프로세스가 죽었다 살아났다)이면 묻지 않고 잇고,
     * 그 밖이면 묻는다. 그 뒤에 파일이 바뀌었으면 그것도 말한다.
     */
    @Test
    fun `남은 초안 — 지우기·잇기·묻기`() {
        val base = FileStamp(123L, 1_700_000_000_000L)
        val changed = FileStamp(130L, 1_700_000_100_000L)
        val d = draft("고친 글", session = 7L)
        assertEquals(TextDrafts.Choice.Discard, TextDrafts.choiceFor(d, "고친 글", session = 9L, current = changed))
        assertEquals(TextDrafts.Choice.Continue, TextDrafts.choiceFor(d, "원래 글", session = 7L, current = changed))
        assertEquals(TextDrafts.Choice.Ask(changedSince = false), TextDrafts.choiceFor(d, "원래 글", session = 9L, current = base))
        assertEquals(TextDrafts.Choice.Ask(changedSince = true), TextDrafts.choiceFor(d, "원래 글", session = 9L, current = changed))
    }

    @Test
    fun `지우면 없다`() = runBlocking {
        val file = TextDrafts.fileOf(dir, "/storage/emulated/0/a.txt")
        TextDrafts.write(file, draft("x"))
        TextDrafts.delete(file)
        assertFalse(file.exists())
        assertNull(TextDrafts.read(file))
    }

    @Test
    fun `망가진 초안은 없는 것으로 보고 지운다`() {
        dir.mkdirs()
        val file = TextDrafts.fileOf(dir, "/storage/emulated/0/b.txt")
        file.writeBytes(byteArrayOf(1, 2, 3))
        assertNull(TextDrafts.read(file))
        assertFalse(file.exists())
    }

    @Test
    fun `오래된 초안과 남은 임시본을 치운다`() = runBlocking {
        val old = TextDrafts.fileOf(dir, "/storage/emulated/0/old.txt")
        val fresh = TextDrafts.fileOf(dir, "/storage/emulated/0/fresh.txt")
        TextDrafts.write(old, draft("old"))
        TextDrafts.write(fresh, draft("fresh"))
        val now = System.currentTimeMillis()
        old.setLastModified(now - TextDrafts.STALE_MS - 60_000)
        val leftover = File(dir, "x.draft.part").apply { writeText("half") }
        leftover.setLastModified(now - 2 * 60 * 60 * 1000)
        TextDrafts.purgeStale(dir, now)
        assertFalse(old.exists())
        assertFalse(leftover.exists())
        assertTrue(fresh.exists())
    }
}
