package io.github.donggi.iroiroviewer.archive

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** 풀기의 진행 바([ExtractMeter])와 폴더 시각([DirTimes]). 둘 다 엔진이 쓰는 순수 계산이다. */
class ExtractBookkeepingTest {

    private val root: File = Files.createTempDirectory("iroiro-dirtimes").toFile()

    @AfterTest
    fun cleanup() {
        root.deleteRecursively()
    }

    // ---- 진행 바 -----------------------------------------------------------------

    /** 리더가 입력을 세면 분모·분자가 **아카이브 쪽 바이트**다. 쓴 바이트는 더하지 않는다 — 단위가 다르다. */
    @Test
    fun `입력으로 세면 쓴 바이트를 섞지 않고 줄지도 넘치지도 않는다`() {
        val m = ExtractMeter(inputTotal = 1_000, declaredTotal = 50_000)
        assertTrue(m.byInput)
        assertEquals(1_000, m.bytesTotal)
        m.consumed(300)
        m.finished(40_000)
        assertEquals(300, m.bytesDone, "쓴 바이트가 섞였다")
        m.consumed(200)
        assertEquals(300, m.bytesDone, "진행률이 뒤로 갔다")
        m.consumed(1_064)
        assertEquals(1_000, m.bytesDone, "버퍼가 미리 읽은 몫으로 분모를 넘었다")
    }

    /** 리더가 못 세면(-1) 8단계의 방식 — 선언 크기 합과 쓴 바이트 — 로 물러난다. */
    @Test
    fun `못 세면 선언 크기 합과 쓴 바이트로 물러난다`() {
        val m = ExtractMeter(inputTotal = -1, declaredTotal = 5_000)
        assertFalse(m.byInput)
        assertEquals(5_000, m.bytesTotal)
        m.consumed(4_000)
        assertEquals(0, m.bytesDone, "입력 소비는 물러난 뒤에 쓰지 않는다")
        m.finished(1_200)
        m.finished(800)
        assertEquals(2_000, m.bytesDone)
    }

    // ---- 폴더 시각 ----------------------------------------------------------------

    private val t1 = 1_767_323_040_000L
    private val t2 = 1_700_000_000_000L

    private fun mk(path: String): File = File(root, path).apply { mkdirs() }

    @Test
    fun `만든 폴더에만 적힌 시각을 깊은 것부터 건다`() {
        val a = mk("a")
        val ab = mk("a/b")
        val abc = mk("a/b/c")
        val times = DirTimes()
        for (d in listOf(a, ab, abc)) times.created(d)
        times.entryTime(a, t1)
        times.entryTime(abc, t2)
        // b 는 만들었지만 아카이브가 시각을 적지 않았다 — 지어내지 않는다.

        val order = ArrayList<String>()
        val applied = times.restore { f, t ->
            order += f.name
            f.setLastModified(t)
        }
        assertEquals(2, applied)
        assertEquals(listOf("c", "a"), order, "깊은 것부터")
        assertEquals(t1, a.lastModified())
        assertEquals(t2, abc.lastModified())
        assertNotEquals(t1, ab.lastModified())
        assertNotEquals(t2, ab.lastModified())
    }

    /** '여기에 풀기' 로 이미 있던 폴더 안에 풀었다면 그 폴더는 사용자의 것이다. */
    @Test
    fun `이미 있던 폴더는 건드리지 않는다`() {
        val existing = mk("mine")
        val before = existing.lastModified()
        val times = DirTimes()
        times.entryTime(existing, t1)
        assertEquals(0, times.restore())
        assertEquals(before, existing.lastModified())
    }

    @Test
    fun `모르는 시각과 실패한 걸기는 무시한다`() {
        val d = mk("d")
        val times = DirTimes()
        times.created(d)
        times.entryTime(d, 0L)
        assertEquals(0, times.restore(), "0 은 '모름' 이다")

        val e = mk("e")
        val times2 = DirTimes()
        times2.created(e)
        times2.entryTime(e, t1)
        assertEquals(0, times2.restore { _, _ -> false }, "못 건 것은 세지 않고, 던지지도 않는다")
        assertEquals(0, times2.restore { _, _ -> throw SecurityException("권한") })
    }

    /** 같은 폴더를 다른 모양의 경로로 적어도(끝의 `.`·`..`) 하나로 본다. */
    @Test
    fun `경로의 모양이 달라도 같은 폴더다`() {
        val d = mk("x/y")
        val times = DirTimes()
        times.created(File(root, "x/./y"))
        times.entryTime(File(root, "x/z/../y"), t1)
        assertEquals(1, times.restore())
        assertEquals(t1, d.lastModified())
    }
}
