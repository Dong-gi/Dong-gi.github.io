package io.github.donggi.iroirobrowser.download

import android.content.Context
import io.github.donggi.iroirobrowser.R
import io.github.donggi.iroirobrowser.torrent.FileStatus
import io.github.donggi.iroirobrowser.torrent.TorrentError
import io.github.donggi.iroirobrowser.torrent.TorrentState
import io.github.donggi.iroirobrowser.torrent.TorrentStatus

/**
 * 토렌트의 글 — 엔진은 기계 말(`TorrentState`·`TorrentError`)만 내고 화면 문구는 앱이 고른다(엔진의 약속 — `TorrentException` 머리말). 목록
 * ([DownloadsActivity])·알림([TorrentService]·[TorrentNotices])·창([TorrentDialogs])이 함께 쓴다. 고르는 일은 자원 번호와 인자로만 해서
 * ([Part]) 기기 없이 시험한다 — 글로 짓는 것은 [text] 하나다.
 */
internal object TorrentTexts {

    /** 글 조각 — 자원과 그 인자. 인자가 [Part] 면 그것을 먼저 글로 짓는다. */
    class Part(val res: Int, vararg args: Any) {
        val args: List<Any> = args.toList()

        override fun toString(): String = "Part($res, $args)"
    }

    /** 바이트 수를 글로 — 화면은 `Formatter.formatShortFileSize`, 시험은 가짜. */
    fun interface Sizes {
        fun format(bytes: Long): String
    }

    /** 엔진의 상태 밖에서 앱이 아는 것 — 파일 고르기를 기다림, 옮기기, 망을 기다리는 까닭. */
    class Extra(
        /** 마그넷의 파일 목록을 받아 사용자가 고르기를 기다린다. */
        val choiceNeeded: Boolean = false,
        /** '내려받기' 로 옮기려고 줄 선·옮기는 파일 수. */
        val moving: Int = 0,
        /** 옮기지 못한(다시 해 볼) 파일 수. */
        val moveFailed: Int = 0,
        /** 망 조건 기다림의 까닭(모르면 null). */
        val netWait: TorrentRules.NetWait? = null,
    )

    fun stateRes(state: TorrentState): Int = when (state) {
        TorrentState.METADATA -> R.string.torrent_state_metadata
        TorrentState.CHECKING -> R.string.torrent_state_checking
        TorrentState.DOWNLOADING -> R.string.torrent_state_downloading
        TorrentState.WAITING -> R.string.torrent_state_waiting
        TorrentState.QUEUED -> R.string.torrent_state_queued
        TorrentState.NETWORK_WAIT -> R.string.torrent_state_network
        TorrentState.PAUSED -> R.string.torrent_state_paused
        TorrentState.COMPLETED -> R.string.torrent_state_completed
        TorrentState.ERROR -> R.string.torrent_state_error
    }

    /** 받지 않거나 멈춘 까닭 — 사용자가 할 수 있는 것까지 한 문장에. */
    fun errorRes(error: TorrentError): Int = when (error) {
        TorrentError.INVALID_TORRENT -> R.string.torrent_error_invalid
        TorrentError.PRIVATE_TORRENT -> R.string.torrent_error_private
        TorrentError.V2_ONLY -> R.string.torrent_error_v2
        TorrentError.TOO_LARGE -> R.string.torrent_error_too_large
        TorrentError.INVALID_MAGNET -> R.string.torrent_error_magnet
        TorrentError.DUPLICATE -> R.string.torrent_error_duplicate
        TorrentError.UNKNOWN_TORRENT -> R.string.torrent_error_unknown
        TorrentError.DISK_FULL -> R.string.torrent_error_disk_full
        TorrentError.DISK_ERROR -> R.string.torrent_error_disk
        TorrentError.ENGINE_CLOSED -> R.string.torrent_error_engine_closed
    }

    fun waitRes(wait: TorrentRules.NetWait?): Int = when (wait) {
        TorrentRules.NetWait.NO_NETWORK -> R.string.torrent_wait_no_network
        TorrentRules.NetWait.NOT_WIFI -> R.string.torrent_wait_wifi
        null -> R.string.torrent_wait_unknown
    }

    /** 토렌트의 이름 — 이름(`dn`) 없는 마그넷은 메타데이터 전에 엔진이 info-hash 를 이름으로 준다. 그것은 '마그넷 · 앞 8자…' 로. */
    fun title(s: TorrentStatus): Part =
        if (s.name == s.id) Part(R.string.torrent_unnamed, s.id.take(8)) else Part(R.string.torrent_name, s.name)

    /** 받은 몫(%) — 고른 파일의 바이트 가운데 해시를 확인한 것. 메타데이터 전·고른 것이 없으면 -1. */
    fun percent(s: TorrentStatus): Int =
        if (s.wantedBytes > 0) (s.doneBytes * 100 / s.wantedBytes).toInt().coerceIn(0, 100) else -1

    /**
     * 목록의 둘째 줄·알림의 글 — 조각들을 ' · ' 로 잇는다. 상태에 따라 보일 것만: 받는 중이면 몫·크기·속도·피어, 기다리면 그 까닭, 다 받았으면
     * 크기와 옮기기, 오류면 그 까닭.
     */
    fun summary(s: TorrentStatus, extra: Extra, sizes: Sizes): List<Part> {
        val out = ArrayList<Part>()
        if (extra.choiceNeeded) {
            out += Part(R.string.torrent_choose_needed)
            if (s.wantedBytes >= 0) out += Part(R.string.torrent_files_count, s.files.count { it.state != "pad" })
            return out
        }
        fun progress() {
            val p = percent(s)
            if (p < 0) return
            out += Part(R.string.torrent_percent, p)
            out += Part(R.string.torrent_of, sizes.format(s.doneBytes), sizes.format(s.wantedBytes))
        }
        fun peers() {
            out += if (s.seeds > 0) Part(R.string.torrent_peers_seeds, s.connectedPeers, s.seeds) else Part(R.string.torrent_peers, s.connectedPeers)
            if (s.webSeeds > 0) out += Part(R.string.torrent_web_seeds, s.webSeeds)
        }
        when (s.state) {
            TorrentState.METADATA -> {
                out += Part(stateRes(s.state))
                peers()
            }
            TorrentState.DOWNLOADING -> {
                progress()
                out += Part(R.string.torrent_rate, sizes.format(s.downloadRate))
                peers()
            }
            TorrentState.WAITING -> {
                out += Part(stateRes(s.state))
                progress()
                peers()
            }
            TorrentState.NETWORK_WAIT -> {
                out += Part(stateRes(s.state))
                out += Part(waitRes(extra.netWait))
                progress()
            }
            TorrentState.CHECKING, TorrentState.QUEUED, TorrentState.PAUSED -> {
                out += Part(stateRes(s.state))
                progress()
            }
            TorrentState.COMPLETED -> {
                out += Part(stateRes(s.state))
                if (s.wantedBytes >= 0) out += Part(R.string.torrent_size, sizes.format(s.wantedBytes))
                if (extra.moving == 0 && extra.moveFailed == 0) out += Part(R.string.torrent_in_downloads)
            }
            TorrentState.ERROR -> {
                out += Part(stateRes(s.state))
                out += s.error?.let { Part(errorRes(it)) } ?: Part(R.string.torrent_error_other)
                progress()
            }
        }
        if (extra.moving > 0) out += Part(R.string.torrent_moving, extra.moving)
        if (extra.moveFailed > 0) out += Part(R.string.torrent_move_failed_count, extra.moveFailed)
        return out
    }

    /** 지금 받는 파일(여러 파일 토렌트에서 받는 중일 때) — 셋째 줄. 없으면 null. */
    fun activeFile(s: TorrentStatus): Part? {
        if (s.state != TorrentState.DOWNLOADING || s.files.count { it.state != "pad" } < 2) return null
        val f = s.files.getOrNull(s.activeFile) ?: return null
        return Part(R.string.torrent_active_file, f.path, filePercent(f))
    }

    fun filePercent(f: FileStatus): Int = if (f.length > 0) (minOf(f.doneBytes, f.length) * 100 / f.length).toInt() else 100

    /** 파일 하나의 상태 글(파일 고르기 창의 줄). */
    fun fileStateRes(f: FileStatus): Int = when {
        f.released -> R.string.torrent_file_moved
        f.state == "done" -> R.string.torrent_file_done
        f.state == "active" -> R.string.torrent_file_active
        f.state == "waiting" -> R.string.torrent_file_waiting
        f.state == "skipped" -> R.string.torrent_file_skipped
        else -> R.string.torrent_file_queued
    }

    /** 자세히 — 줄마다 한 조각(또는 조각들). 상태 줄은 부르는 쪽이 [summary] 로 맨 위에 둔다. */
    fun details(s: TorrentStatus, sizes: Sizes): List<List<Part>> {
        val out = ArrayList<List<Part>>()
        if (s.wantedBytes >= 0) {
            val selectable = s.files.count { it.state != "pad" }
            out += listOf(Part(R.string.torrent_detail_wanted, sizes.format(s.wantedBytes), s.files.count { it.selected }, selectable))
        }
        out += listOf(Part(R.string.torrent_detail_downloaded, sizes.format(s.downloadedBytes)))
        out += listOf(Part(R.string.torrent_detail_peers, s.connectedPeers, s.seeds, s.knownPeers))
        val sources = s.peerSources
        if (sources.isNotEmpty()) {
            out += listOf(Part(R.string.torrent_detail_sources, sources["tracker"] ?: 0, sources["dht"] ?: 0, sources["magnet"] ?: 0))
        }
        if (s.trackers.isNotEmpty()) {
            out += listOf(Part(R.string.torrent_detail_trackers, s.trackers.size, s.trackers.count { it.state == "ok" }))
        }
        if (s.webSeeds > 0) out += listOf(Part(R.string.torrent_web_seeds, s.webSeeds))
        if (s.mseAttempts > 0) out += listOf(Part(R.string.torrent_detail_mse, s.mseSucceeded, s.mseAttempts, s.plainFallbacks))
        if (s.hashFailures > 0) out += listOf(Part(R.string.torrent_detail_hash_failures, s.hashFailures))
        val moved = s.files.count { it.released }
        if (moved > 0) out += listOf(Part(R.string.torrent_detail_moved, moved))
        return out
    }

    // ── 글로 짓기 ────────────────────────────────────────────────────────────────────────────────────────

    fun text(context: Context, part: Part): String {
        val args = part.args.map { if (it is Part) text(context, it) else it }
        return if (args.isEmpty()) context.getString(part.res) else context.getString(part.res, *args.toTypedArray())
    }

    fun join(context: Context, parts: List<Part>): String = parts.joinToString(" · ") { text(context, it) }
}
