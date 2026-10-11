package io.github.donggi.iroiroviewer.player

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 프로세스가 죽은 뒤 되살아난 재생 화면이 다시 세울 큐(빈 재생 화면이 남았다, 기기에서 잡았다). 저장 상태에 무엇을
 * 얼마나 적는가, 적어 둔 것으로 무엇을 다시 세우는가.
 */
class PlayerRestoreTest {

    private val dir: File = Files.createTempDirectory("player-restore").toFile()

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    private fun paths(n: Int) = (0 until n).map { "/storage/emulated/0/Music/$it.mp3" }

    @Test
    fun `작은 큐는 그대로 적는다`() {
        val s = assertNotNull(PlayerRestore.snapshotOf(paths(3), 1, 83_000))
        assertEquals(paths(3), s.paths)
        assertEquals(1, s.index)
        assertEquals(83_000L, s.positionMs)
        // 지금 항목의 폴더 — 되살리지 못했을 때 파일 목록이 열 곳이다(구분자는 이 기계의 것으로 견준다).
        assertEquals(File(paths(3)[1]).parent, s.folder)
    }

    /** 위치는 음수일 수 없다(모를 때의 값). 자리가 범위 밖이면 적지 않는다 — 다시 세울 때 엉뚱한 곡이 된다. */
    @Test
    fun `자리가 범위 밖이면 적지 않는다`() {
        assertNull(PlayerRestore.snapshotOf(paths(3), 3, 0))
        assertNull(PlayerRestore.snapshotOf(paths(3), -1, 0))
        assertNull(PlayerRestore.snapshotOf(emptyList(), 0, 0))
        assertEquals(0L, PlayerRestore.snapshotOf(paths(3), 0, -5)?.positionMs)
    }

    /**
     * 큰 큐는 [PlayerRestore.MAX_ITEMS] 개만 — 저장 상태는 바인더 한 번에 실려 간다. **지금 항목은 반드시 남는다**(앞에서 자르면 폴더
     * 뒤쪽을 듣던 사람의 지금 곡이 빠진다 — `FolderQueue.plan` 과 같은 까닭). 앞쪽 1/4 쯤에 둔다.
     */
    @Test
    fun `큰 큐는 지금 항목을 남기고 자른다`() {
        val all = paths(500)
        val max = PlayerRestore.MAX_ITEMS
        for (at in listOf(0, 3, 100, 400, 450, 499)) {
            val s = assertNotNull(PlayerRestore.snapshotOf(all, at, 0))
            assertEquals(max, s.paths.size, "at=$at")
            assertEquals(all[at], s.paths[s.index], "at=$at — 지금 항목이 그 자리에 있어야 한다")
            // 잘라 낸 것은 원래 차례 그대로의 한 덩어리다.
            val from = all.indexOf(s.paths.first())
            assertEquals(all.subList(from, from + max), s.paths, "at=$at")
        }
        assertEquals(max / 4, PlayerRestore.snapshotOf(all, 400, 0)?.index)
        // 끝 쪽이면 창이 끝에 붙는다 — 뒤로 넘어가지 않는다.
        assertEquals(all.takeLast(max), PlayerRestore.snapshotOf(all, 499, 0)?.paths)
    }

    /** 적어 둔 경로로 다시 세운다 — 그사이 사라진 다른 항목은 빼고, 자막 후보는 지금 항목의 폴더에서 다시 고른다. */
    @Test
    fun `사라진 항목은 빼고 다시 세운다`() {
        val a = File(dir, "01화.mp4").apply { writeBytes(ByteArray(10)) }
        val b = File(dir, "02화.mp4").apply { writeBytes(ByteArray(20)) }
        val gone = File(dir, "00화.mp4")
        File(dir, "02화.srt").writeText("1\n00:00:01,000 --> 00:00:02,000\n안녕\n")
        File(dir, "메모.txt").writeText("자막이 아니다")
        val s = PlayerRestore.Snapshot(listOf(gone.path, a.path, b.path), index = 2, positionMs = 61_000)

        val r = assertNotNull(PlayerRestore.load(s))
        assertEquals(listOf(a.path, b.path), r.plan.items.map { it.path })
        assertEquals(1, r.plan.startIndex, "지금 항목(02화)의 자리가 사라진 것을 빼고 다시 잡힌다")
        // 열쇠(이름·크기·수정 시각)를 다시 재야 이어보기 기록과 맞는다.
        assertEquals(20L, r.plan.items[1].size)
        assertEquals(b.lastModified(), r.plan.items[1].lastModified)
        assertEquals(listOf("02화.srt"), r.subtitles.map { it.name })
    }

    /** 지금 항목이 사라졌으면 다른 곡부터 틀지 않는다 — 그것은 '하던 것을 돌려받는다' 가 아니다. 화면이 알리고 파일 목록으로 간다. */
    @Test
    fun `지금 항목이 사라졌으면 다시 세우지 않는다`() {
        val a = File(dir, "a.mp3").apply { writeBytes(ByteArray(3)) }
        val s = PlayerRestore.Snapshot(listOf(a.path, File(dir, "b.mp3").path), index = 1, positionMs = 0)
        assertNull(PlayerRestore.load(s))
        assertEquals(dir.path, s.folder)
    }
}
