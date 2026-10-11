package io.github.donggi.iroirobrowser.download

import io.github.donggi.iroirobrowser.download.GalleryRules.Position
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 갤러리 받기 곁의 파일 — 계획을 적고 읽기, 덧붙이기 기록(드러낸 파일·건너뛴 것·마지막 자리), 끝이 잘린 줄, 지우기, 찌꺼기 치우기. */
class DownloadSetsTest {

    private val dir: File = Files.createTempDirectory("download-sets").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun 계획을_적고_다시_읽는다() {
        val sets = DownloadSets(dir)
        assertNull(sets.readPlan(7))
        assertTrue(sets.writePlan(7, "{\"계획\":1}"))
        assertEquals("{\"계획\":1}", sets.readPlan(7))
        // 다시 적으면 갈아 끼우고 임시 파일이 남지 않는다.
        assertTrue(sets.writePlan(7, "둘"))
        assertEquals("둘", DownloadSets(dir).readPlan(7))
        assertEquals(listOf("7.json"), dir.list()!!.sorted())
        // 끝난 받기는 계획만 버린다(기록은 '받은 파일까지 지우기' 가 쓴다).
        sets.appendSaved(7, 0, 0, "content://media/external/downloads/1")
        sets.dropPlan(7)
        assertNull(sets.readPlan(7))
        assertEquals(1, sets.journal(7).saved)
    }

    @Test
    fun 기록은_드러낸_파일과_건너뛴_것과_마지막_자리() {
        val sets = DownloadSets(dir)
        sets.appendSaved(3, 0, 0, "content://media/external/downloads/100")
        sets.appendSaved(3, 0, 1, "content://media/external/downloads/101")
        sets.appendSkipped(3, 0, 2, login = false)
        sets.appendSkipped(3, 1, -1, login = true)
        sets.appendSaved(3, 2, 0, "content://media/external/downloads/105")
        val journal = DownloadSets(dir).journal(3)
        assertEquals(
            listOf("content://media/external/downloads/100", "content://media/external/downloads/101", "content://media/external/downloads/105"),
            journal.savedUris,
        )
        assertEquals(3, journal.saved)
        assertEquals(2, journal.skipped)
        assertEquals(1, journal.skippedLogin)
        assertEquals(Position(2, 0), journal.last)
        // 다른 항목의 기록과 섞이지 않는다.
        assertEquals(0, sets.journal(4).saved)
        assertNull(sets.journal(4).last)
    }

    @Test
    fun 끝이_잘린_줄과_꼴이_다른_줄은_버린다() {
        val journal = DownloadSets.parse(
            "F\t0\t0\tcontent://a\nS\t0\t1\t0\nF\tx\t0\tcontent://b\nF\t1\t0\t\nQ\t9\t9\t0\nF\t-2\t0\tcontent://d\nF\t1\t1\tcontent://c",
        )
        assertEquals(listOf("content://a"), journal.savedUris)
        assertEquals(1, journal.skipped)
        assertEquals(Position(0, 1), journal.last)
        assertEquals(0, DownloadSets.parse("").saved)
        // 줄바꿈 없는 한 줄뿐 — 쓰다가 죽었다.
        assertNull(DownloadSets.parse("F\t0\t0\tcontent://a").last)
        // 차례가 섞여 적혔어도 마지막 자리는 가장 앞선 자리다.
        assertEquals(Position(4, 2), DownloadSets.parse("F\t4\t2\tcontent://a\nF\t1\t0\tcontent://b\n").last)
    }

    @Test
    fun 줄의_꼴을_깨뜨리는_URI_는_적지_않는다() {
        val sets = DownloadSets(dir)
        sets.appendSaved(5, 0, 0, "content://a\tb")
        sets.appendSaved(5, 0, 1, "content://a\nF\t9\t9\tcontent://evil")
        sets.appendSaved(5, 0, 2, "")
        assertEquals(0, sets.journal(5).saved)
        assertNull(sets.journal(5).last)
    }

    @Test
    fun 목록에서_빼면_계획과_기록을_함께_버린다() {
        val sets = DownloadSets(dir)
        sets.writePlan(8, "p")
        sets.appendSaved(8, 0, 0, "content://x")
        sets.forget(8)
        assertNull(sets.readPlan(8))
        assertEquals(0, sets.journal(8).saved)
        assertTrue(dir.list()!!.isEmpty())
    }

    @Test
    fun 찌꺼기_치우기는_목록에_없는_지금까지의_번호만() {
        val names = listOf("1.json", "1.saved", "2.saved", "3.json.tmp", "9.json", "9.saved", "notes.txt", "x.json", "10.json")
        // 1 은 목록에 있다, 2·3 은 없다, 9·10 은 이 프로세스가 막 넣은 것(지금까지의 번호 8 보다 뒤). 우리 꼴이 아닌 이름은 건드리지 않는다.
        assertEquals(listOf("2.saved", "3.json.tmp"), DownloadSets.stale(names, keep = setOf(1L), upTo = 8))
        val sets = DownloadSets(dir)
        for (name in names) File(dir, name).writeText("x")
        sets.sweep(setOf(1L), 8)
        assertEquals(names.toSet() - setOf("2.saved", "3.json.tmp"), dir.list()!!.toSet())
    }

    @Test
    fun 번호가_아닌_것에는_쓰지_않는다() {
        val sets = DownloadSets(dir)
        assertFalse(sets.writePlan(0, "x"))
        assertFalse(sets.writePlan(-3, "x"))
        sets.appendSaved(-1, 0, 0, "content://x")
        sets.appendSkipped(0, 0, 0, login = false)
        assertTrue(dir.list().isNullOrEmpty())
    }
}
