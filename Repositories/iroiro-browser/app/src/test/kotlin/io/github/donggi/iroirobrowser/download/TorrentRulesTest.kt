package io.github.donggi.iroirobrowser.download

import io.github.donggi.iroirobrowser.download.TorrentFixtures.ID
import io.github.donggi.iroirobrowser.download.TorrentFixtures.OTHER
import io.github.donggi.iroirobrowser.download.TorrentFixtures.file
import io.github.donggi.iroirobrowser.download.TorrentFixtures.status
import io.github.donggi.iroirobrowser.download.TorrentFixtures.torrentFile
import io.github.donggi.iroirobrowser.download.TorrentRules.Fix
import io.github.donggi.iroirobrowser.download.TorrentRules.Net
import io.github.donggi.iroirobrowser.download.TorrentRules.NetWait
import io.github.donggi.iroirobrowser.torrent.TorrentState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 토렌트를 앱에 잇는 판단 — 무엇이 토렌트인가·망·서비스와 CPU·자리·마그넷의 고르기·앱이 죽은 뒤 마저 할 일. */
class TorrentRulesTest {

    @Test
    fun 마그넷은_스킴으로만_가린다() {
        assertTrue(TorrentRules.isMagnet("magnet:?xt=urn:btih:$ID"))
        assertTrue(TorrentRules.isMagnet("  MAGNET:?xt=urn:btih:x"))
        assertTrue(TorrentRules.isMagnet("magnet:"))
        assertFalse(TorrentRules.isMagnet("magnets:?x"))
        assertFalse(TorrentRules.isMagnet("https://example.com/magnet:?xt=1"))
        assertFalse(TorrentRules.isMagnet(""))
    }

    @Test
    fun 토렌트_파일은_형식_이름_주소의_끝으로() {
        assertTrue(TorrentRules.isTorrentFile("application/x-bittorrent", "x.bin", null))
        assertTrue(TorrentRules.isTorrentFile("Application/X-BitTorrent; charset=binary", null, null))
        // 사이트가 형식을 octet-stream 으로 주는 일이 흔하다 — 이름(Content-Disposition)·주소로도 본다.
        assertTrue(TorrentRules.isTorrentFile("application/octet-stream", "Debian.TORRENT", null))
        assertTrue(TorrentRules.isTorrentFile(null, "download", "https://cdimage.example/debian.iso.torrent?x=1#y"))
        assertFalse(TorrentRules.isTorrentFile("application/octet-stream", "a.zip", "https://example.com/torrent"))
        // data: 는 주소의 끝을 보지 않는다(자료가 그 자리에 있다) — 형식이나 이름으로만.
        assertFalse(TorrentRules.isTorrentFile(null, null, "data:text/plain,a.torrent"))
        assertFalse(TorrentRules.isTorrentFile(null, null, null))
    }

    @Test
    fun 망이_없으면_늘_기다리고_Wi_Fi_에서만은_요금제가_아닌_Wi_Fi_만() {
        val cellular = Net(internet = true, notMetered = false, wifi = false, ethernet = false)
        val wifi = Net(internet = true, notMetered = true, wifi = true, ethernet = false)
        val hotspot = Net(internet = true, notMetered = false, wifi = true, ethernet = false)
        val unmeteredCellular = Net(internet = true, notMetered = true, wifi = false, ethernet = false)
        val ethernet = Net(internet = true, notMetered = true, wifi = false, ethernet = true)
        assertEquals(NetWait.NO_NETWORK, TorrentRules.netWait(false, null))
        assertEquals(NetWait.NO_NETWORK, TorrentRules.netWait(false, Net(internet = false, notMetered = true, wifi = true, ethernet = false)))
        // 기본(끔)은 어느 망이든 받는다.
        assertNull(TorrentRules.netWait(false, cellular))
        assertNull(TorrentRules.netWait(false, hotspot))
        // 켜면: Wi-Fi(요금제 아님)·유선만. 핫스팟처럼 요금제로 표시된 Wi-Fi 와 '요금제가 아니라는' 모바일 망은 기다린다.
        assertNull(TorrentRules.netWait(true, wifi))
        assertNull(TorrentRules.netWait(true, ethernet))
        assertEquals(NetWait.NOT_WIFI, TorrentRules.netWait(true, cellular))
        assertEquals(NetWait.NOT_WIFI, TorrentRules.netWait(true, hotspot))
        assertEquals(NetWait.NOT_WIFI, TorrentRules.netWait(true, unmeteredCellular))
    }

    @Test
    fun 받으려는_것이_있거나_일이_남았으면_서비스를_세워_둔다() {
        for (s in listOf(TorrentState.METADATA, TorrentState.CHECKING, TorrentState.DOWNLOADING, TorrentState.WAITING, TorrentState.QUEUED, TorrentState.NETWORK_WAIT)) {
            assertTrue(TorrentRules.keepsForeground(listOf(TorrentState.PAUSED, s), busy = false), s.name)
        }
        val idle = listOf(TorrentState.PAUSED, TorrentState.COMPLETED, TorrentState.ERROR)
        assertFalse(TorrentRules.keepsForeground(idle, busy = false))
        assertFalse(TorrentRules.keepsForeground(emptyList(), busy = false))
        // 옮길 파일·엔진 일이 남았다.
        assertTrue(TorrentRules.keepsForeground(idle, busy = true))
    }

    @Test
    fun 보는_화면도_일도_없으면_엔진을_닫는다() {
        assertTrue(TorrentRules.idle(0, listOf(TorrentState.PAUSED, TorrentState.COMPLETED), busy = false))
        assertFalse(TorrentRules.idle(1, listOf(TorrentState.PAUSED), busy = false))
        assertFalse(TorrentRules.idle(0, listOf(TorrentState.NETWORK_WAIT), busy = false))
        assertFalse(TorrentRules.idle(0, emptyList(), busy = true))
    }

    @Test
    fun CPU_는_받는_동안만_나아감이_없으면_10분_뒤_놓는다() {
        val w = TorrentRules.StallWatch(graceMs = 1_000)
        assertFalse(w.needsCpu(0, emptyList(), moving = false))
        assertTrue(w.needsCpu(0, listOf(status(downloadedBytes = 100)), moving = false))
        assertTrue(w.needsCpu(999, listOf(status(downloadedBytes = 100)), moving = false))
        // 나아감이 없다 — 놓는다.
        assertFalse(w.needsCpu(1_000, listOf(status(downloadedBytes = 100)), moving = false))
        // 바이트가 늘면 곧바로 다시.
        assertTrue(w.needsCpu(5_000, listOf(status(downloadedBytes = 101)), moving = false))
        assertFalse(w.needsCpu(6_000, listOf(status(downloadedBytes = 101)), moving = false))
        // 상태가 바뀌면(조각이 모자라 기다림 → 받는 중) 새로 센다.
        assertTrue(w.needsCpu(6_001, listOf(status(state = TorrentState.WAITING, downloadedBytes = 101)), moving = false))
        // 망을 기다리는 것·차례를 기다리는 것만이면 붙잡지 않는다.
        assertFalse(w.needsCpu(6_002, listOf(status(state = TorrentState.NETWORK_WAIT), status(id = OTHER, state = TorrentState.QUEUED)), moving = false))
        // 확인(해시)과 옮기기는 끝이 있는 디스크 일 — 늘 붙잡는다.
        assertTrue(w.needsCpu(60_000, listOf(status(state = TorrentState.CHECKING)), moving = false))
        assertTrue(w.needsCpu(120_000, listOf(status(state = TorrentState.PAUSED)), moving = true))
    }

    @Test
    fun 여러_파일_토렌트는_내려받기의_이름_폴더_한_파일은_내려받기_바로_아래() {
        TorrentRules.placement(listOf("Show", "S01", "e1.mkv"), multiFile = true).let {
            assertEquals("Download/Show/S01/", it.relativePath)
            assertEquals("e1.mkv", it.displayName)
        }
        TorrentRules.placement(listOf("Album", "cover.jpg"), multiFile = true).let {
            assertEquals("Download/Album/", it.relativePath)
            assertEquals("cover.jpg", it.displayName)
        }
        TorrentRules.placement(listOf("debian-13.iso"), multiFile = false).let {
            assertEquals("Download/", it.relativePath)
            assertEquals("debian-13.iso", it.displayName)
        }
    }

    @Test
    fun 자리는_엔진이_정리한_것을_한_번_더_거른다() {
        // 엔진의 정리를 빠져나온 성분이 있어도 공용 폴더 밖을 가리키지 못한다.
        TorrentRules.placement(listOf("..", "a/b", "c\u202Etxt.exe"), multiFile = true).let {
            assertEquals("Download/download/a_b/", it.relativePath)
            assertEquals("c_txt.exe", it.displayName)
        }
        TorrentRules.placement(emptyList(), multiFile = true).let {
            assertEquals("Download/", it.relativePath)
            assertEquals("download", it.displayName)
        }
        // 이미 정리한 것은 그대로다(두 번 걸러도 같다).
        assertEquals("Download/a (1)/con_.txt/", TorrentRules.placement(listOf("a (1)", "con_.txt", "x"), multiFile = true).relativePath)
    }

    @Test
    fun 형식은_확장자로_모르면_octet_stream() {
        val table = mapOf("mkv" to "video/x-matroska", "iso" to "application/x-iso9660-image")
        val lookup = { ext: String -> table[ext] }
        assertEquals("video/x-matroska", TorrentRules.mimeOf("Movie.MKV", lookup))
        assertEquals("application/x-iso9660-image", TorrentRules.mimeOf("a.b.iso", lookup))
        assertEquals("application/octet-stream", TorrentRules.mimeOf("README", lookup))
        assertEquals("application/octet-stream", TorrentRules.mimeOf("trailing.", lookup))
        assertEquals("application/octet-stream", TorrentRules.mimeOf(".hidden", lookup))
        assertEquals("application/octet-stream", TorrentRules.mimeOf("a.xyz", lookup))
    }

    @Test
    fun 마그넷의_파일이_하나면_묻지_않고_여럿이면_묻는다() {
        val one = listOf(
            torrentFile(0, listOf("T", "movie.mkv")),
            torrentFile(1, listOf("T", ".pad", "1"), pad = true),
            torrentFile(2, listOf("T", "_____padding_file_0"), padLike = true),
            torrentFile(3, listOf("T", "link"), symlink = true),
        )
        assertEquals(setOf(0), TorrentRules.autoSelection(one))
        assertNull(TorrentRules.autoSelection(listOf(torrentFile(0, listOf("T", "a")), torrentFile(1, listOf("T", "b")))))
        // 옛 채움 파일뿐인 이상한 토렌트 — 고를 수 있는 것을 모두.
        assertEquals(setOf(0, 1), TorrentRules.autoSelection(listOf(torrentFile(0, listOf("_____padding_file_0"), padLike = true), torrentFile(1, listOf("_____padding_file_1"), padLike = true))))
    }

    @Test
    fun 엔진을_열면_옮기다_만_항목을_지우고_없는_토렌트의_기록을_버린다() {
        val fixes = TorrentRules.leftovers(
            known = setOf(ID),
            moved = mapOf(ID to mapOf(0 to 10L), OTHER to mapOf(1 to 11L)),
            pending = mapOf(ID to mapOf(2 to 12L), OTHER to mapOf(3 to 13L)),
        )
        // 옮기던 것을 먼저(기록을 버리기 전에) 지운다.
        assertEquals(listOf(Fix.DropPending(ID, 2, 12L), Fix.DropPending(OTHER, 3, 13L), Fix.Forget(OTHER)), fixes)
    }

    @Test
    fun 확인이_끝난_토렌트마다_마저_할_일() {
        val s = status(
            files = listOf(
                file(0, "T/a", state = "done", released = true), // 옮겨 갔는데 앱 안에 남았다
                file(1, "T/b", state = "done", released = true), // 옮겨 갔고 남은 것 없음
                file(2, "T/c", state = "done"), // 옮겼는데 엔진이 몰랐다 — 조각은 그대로
                file(3, "T/d", state = "queued"), // 옮겼는데 엔진이 몰랐고, 지운 파일 탓에 조각을 잃었다
                file(4, "T/e", state = "done"), // 다 받았는데 옮기지 않았다
                file(5, "T/f", state = "done", selected = false), // 고르지 않았다(경계 조각에 든 작은 파일)
                file(6, "T/g", state = "active"),
            ),
        )
        val sizes = mapOf(0 to 100L, 2 to 100L, 4 to 100L, 6 to 30L)
        val fixes = TorrentRules.reconcile(s, moved = mapOf(2 to 20L, 3 to 30L)) { index -> sizes[index] ?: -1L }
        assertEquals(
            listOf(
                Fix.DeleteLeftover(ID, 0),
                Fix.Release(ID, 2, unselect = false),
                Fix.Release(ID, 3, unselect = true),
                Fix.Move(ID, 4),
            ),
            fixes,
        )
    }

    @Test
    fun 알림_번호는_토렌트마다_같고_다른_알림과_겹치지_않는다() {
        val a = TorrentRules.noticeId(ID)
        assertEquals(a, TorrentRules.noticeId(ID))
        assertNotEquals(a, TorrentRules.noticeId(OTHER))
        // 내려받기(1, 1000 + 번호)·녹화(2)·토렌트 받는 중(3)의 자리 밖.
        assertTrue(a >= 0x10000000 && TorrentRules.noticeId(OTHER) >= 0x10000000)
        assertTrue(a < 0x11000000)
    }
}
