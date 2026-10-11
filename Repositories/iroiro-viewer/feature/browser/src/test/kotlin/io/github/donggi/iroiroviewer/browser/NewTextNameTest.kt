package io.github.donggi.iroiroviewer.browser

import io.github.donggi.iroiroviewer.io.FileOpEngine
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * '새 글 파일' 의 처음 이름과 겹침([NewTextName]). 겹친 이름은 칸을 닫지 않는다(새 폴더·이름 바꾸기와 같은 규칙 —
 * [NameAttempt]).
 */
class NewTextNameTest {

    @Test
    fun `처음 이름은 새 글_txt 이고 있으면 번호를 붙인다`() {
        val dir = Files.createTempDirectory("newtext").toFile()
        try {
            assertEquals("새 글.txt", NewTextName.suggest(dir, "새 글"))
            File(dir, "새 글.txt").writeText("")
            assertEquals("새 글 (2).txt", NewTextName.suggest(dir, "새 글"))
            File(dir, "새 글 (2).txt").writeText("")
            assertEquals("새 글 (3).txt", NewTextName.suggest(dir, "새 글"))
            // 다른 확장자는 겹치지 않는다(.md 로 고친 것).
            File(dir, "메모.md").writeText("")
            assertEquals("메모.txt", NewTextName.suggest(dir, "메모"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `만들 때 겹치면 칸을 열어 둔다`() {
        assertTrue(NameAttempt.keepsDialog(FileOpEngine.Reason.EXISTS))
        assertEquals("txt", NewTextName.EXTENSION)
    }
}
