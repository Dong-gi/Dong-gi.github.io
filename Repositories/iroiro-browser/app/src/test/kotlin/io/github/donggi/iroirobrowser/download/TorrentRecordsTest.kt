package io.github.donggi.iroirobrowser.download

import io.github.donggi.iroirobrowser.download.TorrentFixtures.ID
import io.github.donggi.iroirobrowser.download.TorrentFixtures.OTHER
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 앱이 토렌트마다 덧붙이는 기록 — 다시 읽기, 끝이 잘린 줄, 지우기, 이름 거르기. */
class TorrentRecordsTest {

    private val dir: File = Files.createTempDirectory("torrent-records").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun 적은_것은_다시_읽어도_같다() {
        val r = TorrentRecords(dir)
        r.markPending(ID, 0, 100)
        r.markMoved(ID, 0, 100)
        r.markPending(ID, 1, 101)
        r.markFailed(ID, 2, "공간이\t모자\n랍니다")
        r.setAwaiting(OTHER, true)
        r.setChoice(OTHER, true)
        r.setAwaiting(OTHER, false)
        val again = TorrentRecords(dir)
        assertEquals(mapOf(0 to 100L), again.movedOf(ID))
        // '옮김' 이 옮기던 것을 지웠다 — 1 만 남는다.
        assertEquals(mapOf(ID to mapOf(1 to 101L)), again.pendingSnapshot())
        // 까닭은 한 줄로(탭·줄바꿈이 기록의 꼴을 깨뜨리지 않게).
        assertEquals(mapOf(2 to "공간이 모자 랍니다"), again.failedOf(ID))
        assertFalse(again.isAwaiting(OTHER))
        assertTrue(again.needsChoice(OTHER))
    }

    @Test
    fun 옮기면_실패와_옮기던_것이_지워진다() {
        val r = TorrentRecords(dir)
        r.markFailed(ID, 3, "x")
        r.markPending(ID, 3, 7)
        r.markMoved(ID, 3, 7)
        val again = TorrentRecords(dir)
        assertTrue(again.failedOf(ID).isEmpty())
        assertTrue(again.pendingSnapshot()[ID].orEmpty().isEmpty())
        assertEquals(mapOf(3 to 7L), again.movedOf(ID))
    }

    @Test
    fun 끝이_잘린_줄은_버린다() {
        TorrentRecords(dir).markMoved(ID, 0, 5)
        // 쓰다가 죽었다 — 줄바꿈 없는 마지막 줄.
        File(dir, "$ID.log").appendText("M\t1\t6")
        assertEquals(mapOf(0 to 5L), TorrentRecords(dir).movedOf(ID))
    }

    @Test
    fun 지우면_기록째_없어진다() {
        val r = TorrentRecords(dir)
        r.markMoved(ID, 0, 5)
        r.forget(ID)
        assertFalse(File(dir, "$ID.log").exists())
        assertTrue(TorrentRecords(dir).movedSnapshot().isEmpty())
    }

    @Test
    fun info_hash_가_아닌_이름은_적지_않는다() {
        val r = TorrentRecords(dir)
        r.markMoved("../../shared_prefs/web", 0, 1)
        r.markMoved(ID.uppercase(), 0, 1)
        assertTrue(dir.listFiles().orEmpty().isEmpty())
        assertTrue(r.movedSnapshot().isEmpty())
    }
}
