package io.github.donggi.iroirobrowser.download

import io.github.donggi.iroirobrowser.R
import io.github.donggi.iroirobrowser.download.TorrentFixtures.ID
import io.github.donggi.iroirobrowser.download.TorrentFixtures.file
import io.github.donggi.iroirobrowser.download.TorrentFixtures.status
import io.github.donggi.iroirobrowser.download.TorrentTexts.Extra
import io.github.donggi.iroirobrowser.download.TorrentTexts.Part
import io.github.donggi.iroirobrowser.torrent.TorrentError
import io.github.donggi.iroirobrowser.torrent.TorrentState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 엔진의 기계 말(상태·오류)을 앱의 글로 — 고르는 일만 본다(자원 번호와 인자). */
class TorrentTextsTest {

    private val sizes = TorrentTexts.Sizes { "${it}B" }

    private fun List<Part>.res() = map { it.res }

    @Test
    fun 모든_상태와_오류에_글이_있고_서로_다르다() {
        val states = TorrentState.entries.map { TorrentTexts.stateRes(it) }
        assertEquals(states.size, states.toSet().size)
        assertTrue(states.all { it != 0 })
        val errors = TorrentError.entries.map { TorrentTexts.errorRes(it) }
        assertEquals(errors.size, errors.toSet().size)
        // 사용자 지정의 말 그대로 — '조각이 모자라 기다림'·'망 조건 기다림'.
        assertEquals(R.string.torrent_state_waiting, TorrentTexts.stateRes(TorrentState.WAITING))
        assertEquals(R.string.torrent_state_network, TorrentTexts.stateRes(TorrentState.NETWORK_WAIT))
    }

    @Test
    fun 받는_중이면_몫_크기_속도_피어() {
        val parts = TorrentTexts.summary(status(wantedBytes = 1000, doneBytes = 250, downloadRate = 64, connectedPeers = 5, seeds = 3), Extra(), sizes)
        assertEquals(listOf(R.string.torrent_percent, R.string.torrent_of, R.string.torrent_rate, R.string.torrent_peers_seeds), parts.res())
        assertEquals(listOf<Any>(25), parts[0].args)
        assertEquals(listOf<Any>("250B", "1000B"), parts[1].args)
        assertEquals(listOf<Any>("64B"), parts[2].args)
        assertEquals(listOf<Any>(5, 3), parts[3].args)
        // 시드가 없으면 피어만, 웹 시드가 있으면 덧붙인다.
        val web = TorrentTexts.summary(status(seeds = 0, webSeeds = 2), Extra(), sizes)
        assertEquals(listOf(R.string.torrent_percent, R.string.torrent_of, R.string.torrent_rate, R.string.torrent_peers, R.string.torrent_web_seeds), web.res())
    }

    @Test
    fun 기다림은_까닭을_앞에() {
        val waiting = TorrentTexts.summary(status(state = TorrentState.WAITING), Extra(), sizes)
        assertEquals(R.string.torrent_state_waiting, waiting.first().res)
        val wifi = TorrentTexts.summary(status(state = TorrentState.NETWORK_WAIT), Extra(netWait = TorrentRules.NetWait.NOT_WIFI), sizes)
        assertEquals(listOf(R.string.torrent_state_network, R.string.torrent_wait_wifi, R.string.torrent_percent, R.string.torrent_of), wifi.res())
        val none = TorrentTexts.summary(status(state = TorrentState.NETWORK_WAIT), Extra(netWait = TorrentRules.NetWait.NO_NETWORK), sizes)
        assertEquals(R.string.torrent_wait_no_network, none[1].res)
        assertEquals(R.string.torrent_wait_unknown, TorrentTexts.summary(status(state = TorrentState.NETWORK_WAIT), Extra(), sizes)[1].res)
    }

    @Test
    fun 메타데이터_전에는_몫이_없다() {
        val parts = TorrentTexts.summary(status(state = TorrentState.METADATA, wantedBytes = -1, doneBytes = 0, seeds = 0, connectedPeers = 2), Extra(), sizes)
        assertEquals(listOf(R.string.torrent_state_metadata, R.string.torrent_peers), parts.res())
        assertEquals(-1, TorrentTexts.percent(status(wantedBytes = -1)))
        assertEquals(-1, TorrentTexts.percent(status(wantedBytes = 0, doneBytes = 0)))
        assertEquals(100, TorrentTexts.percent(status(wantedBytes = 10, doneBytes = 12)))
    }

    @Test
    fun 다_받으면_크기와_옮기기() {
        val done = TorrentTexts.summary(status(state = TorrentState.COMPLETED, wantedBytes = 700), Extra(), sizes)
        assertEquals(listOf(R.string.torrent_state_completed, R.string.torrent_size, R.string.torrent_in_downloads), done.res())
        val moving = TorrentTexts.summary(status(state = TorrentState.COMPLETED), Extra(moving = 2, moveFailed = 1), sizes)
        assertEquals(listOf(R.string.torrent_state_completed, R.string.torrent_size, R.string.torrent_moving, R.string.torrent_move_failed_count), moving.res())
        assertEquals(listOf<Any>(2), moving[2].args)
    }

    @Test
    fun 오류는_그_까닭을() {
        val disk = TorrentTexts.summary(status(state = TorrentState.ERROR, error = TorrentError.DISK_FULL), Extra(), sizes)
        assertEquals(listOf(R.string.torrent_state_error, R.string.torrent_error_disk_full), disk.res().take(2))
        val unknown = TorrentTexts.summary(status(state = TorrentState.ERROR, error = null), Extra(), sizes)
        assertEquals(R.string.torrent_error_other, unknown[1].res)
        assertEquals(R.string.torrent_error_private, TorrentTexts.errorRes(TorrentError.PRIVATE_TORRENT))
        assertEquals(R.string.torrent_error_v2, TorrentTexts.errorRes(TorrentError.V2_ONLY))
    }

    @Test
    fun 파일을_골라야_하면_그것만() {
        val parts = TorrentTexts.summary(
            status(state = TorrentState.PAUSED, files = listOf(file(0, "a"), file(1, "b"), file(2, ".pad/1", state = "pad"))),
            Extra(choiceNeeded = true),
            sizes,
        )
        assertEquals(listOf(R.string.torrent_choose_needed, R.string.torrent_files_count), parts.res())
        assertEquals(listOf<Any>(2), parts[1].args)
    }

    @Test
    fun 이름_없는_마그넷은_정보_해시의_앞머리로() {
        assertEquals(R.string.torrent_unnamed, TorrentTexts.title(status(name = ID)).res)
        assertEquals(listOf<Any>(ID.take(8)), TorrentTexts.title(status(name = ID)).args)
        assertEquals(listOf<Any>("Debian"), TorrentTexts.title(status(name = "Debian")).args)
    }

    @Test
    fun 지금_받는_파일은_여러_파일_토렌트에서_받는_중일_때만() {
        val files = listOf(file(0, "T/a", state = "done", doneBytes = 100), file(1, "T/b", length = 200, doneBytes = 50, state = "active"))
        val active = TorrentTexts.activeFile(status(activeFile = 1, files = files))
        assertEquals(R.string.torrent_active_file, active?.res)
        assertEquals(listOf<Any>("T/b", 25), active?.args)
        assertNull(TorrentTexts.activeFile(status(state = TorrentState.WAITING, activeFile = 1, files = files)))
        assertNull(TorrentTexts.activeFile(status(activeFile = 0, files = listOf(file(0, "only.iso", state = "active")))))
    }

    @Test
    fun 파일의_상태_글() {
        assertEquals(R.string.torrent_file_moved, TorrentTexts.fileStateRes(file(0, "a", state = "done", released = true)))
        assertEquals(R.string.torrent_file_done, TorrentTexts.fileStateRes(file(0, "a", state = "done")))
        assertEquals(R.string.torrent_file_active, TorrentTexts.fileStateRes(file(0, "a", state = "active")))
        assertEquals(R.string.torrent_file_waiting, TorrentTexts.fileStateRes(file(0, "a", state = "waiting")))
        assertEquals(R.string.torrent_file_skipped, TorrentTexts.fileStateRes(file(0, "a", state = "skipped", selected = false)))
        assertEquals(R.string.torrent_file_queued, TorrentTexts.fileStateRes(file(0, "a", state = "queued")))
        assertEquals(100, TorrentTexts.filePercent(file(0, "empty", length = 0)))
    }

    @Test
    fun 자세히는_있는_것만() {
        val lines = TorrentTexts.details(
            status(
                files = listOf(file(0, "a", selected = true), file(1, "b", selected = false), file(2, "c", released = true, state = "done")),
                peerSources = mapOf("tracker" to 3, "dht" to 7),
                mseAttempts = 4,
                mseSucceeded = 3,
                plainFallbacks = 1,
            ),
            sizes,
        )
        val res = lines.map { it.first().res }
        assertEquals(
            listOf(
                R.string.torrent_detail_wanted,
                R.string.torrent_detail_downloaded,
                R.string.torrent_detail_peers,
                R.string.torrent_detail_sources,
                R.string.torrent_detail_mse,
                R.string.torrent_detail_moved,
            ),
            res,
        )
        assertEquals(listOf<Any>(3, 7, 0), lines[3].first().args)
        // 받을 것: 고른 2개(옮긴 것도 고른 것이다) / 고를 수 있는 3개.
        assertEquals(listOf<Any>("1000B", 2, 3), lines[0].first().args)
    }
}
