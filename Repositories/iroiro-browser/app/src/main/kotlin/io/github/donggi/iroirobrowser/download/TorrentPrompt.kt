package io.github.donggi.iroirobrowser.download

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.text.format.Formatter
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import io.github.donggi.iroirobrowser.R
import io.github.donggi.iroirobrowser.WebApp
import io.github.donggi.iroirobrowser.core.DataUrl
import io.github.donggi.iroirobrowser.torrent.TorrentError
import io.github.donggi.iroirobrowser.torrent.TorrentException
import io.github.donggi.iroirobrowser.torrent.TorrentInfo
import io.github.donggi.iroirobrowser.torrent.meta.MagnetLink
import io.github.donggi.iroirobrowser.ui.Ui

/**
 * 브라우저 화면에서 토렌트로 들어가는 길 — 사용자 결정(2026-10-09)대로 **묻는다**:
 *
 * - 페이지의 `.torrent` 내려받기는 '토렌트로 받기 / 파일로 저장'(⑲). 토렌트로 받으면 그 파일을 **웹 쪽 길**(앱의 HTTP — 프록시)로 메모리에
 *   받아([DownloadCenter.fetchSmall]) 엔진이 읽고(`inspect` — 이름·파일 목록·크기), 받을 파일을 고르게 한 뒤 넣는다.
 * - 마그넷(페이지의 고리·주소창)은 '이 앱에서 받기 / 다른 앱으로'(⑱·⑳). 마그넷은 파일 목록이 피어에게서 오므로 그 뒤에 고른다([TorrentCenter]).
 *   마그넷은 엔진의 `MagnetLink.parse`(엔진의 `inspectMagnet` 이 부르는 그것 — 엔진을 열 것 없이)로 읽는다.
 * - 순수 v2·private·깨진 토렌트는 까닭을 말하고 받지 않는다(다른 길 — 파일로 저장·다른 앱으로 — 은 남긴다).
 * - 어느 길이든 처음 한 번 안내한다(㉓ — [TorrentDialogs.notice]): 프록시를 거치지 않고 이 기기의 IP 로 받는다·저작권 책임.
 *
 * 다른 앱이 건네는 마그넷·`.torrent`(VIEW 인텐트)는 지금처럼 받지 않는다(㉑) — 이 길은 이 브라우저 안에서 연 것뿐이다. 메인 스레드.
 */
internal class TorrentPrompt(private val activity: Activity, private val host: Host) {

    interface Host {
        fun trace(message: String)

        fun askNotificationPermission()

        /** 아래에 잠깐 뜨는 한 줄과 단추 하나('토렌트를 받기 시작했습니다 · 목록 보기'). */
        fun showMessage(text: String, action: String, onAction: () -> Unit)
    }

    private val app = WebApp.of(activity)

    /** 토렌트 파일을 받거나 읽는 중의 진행 창 — 하나만. */
    private var reading: AlertDialog? = null
    private var fetch: DownloadCenter.Inspection? = null
    private var readingSerial = 0

    fun close() {
        stopReading()
    }

    /**
     * 페이지가 건넨 `.torrent` 내려받기 — 토렌트로 받을지 파일로 저장할지 묻는다. [saveFile] 은 지금까지의 내려받기(파일로 저장 — 다른 토렌트 앱에
     * 넘길 수 있다). [referer]·[userAgent] 는 그 페이지의 것(토렌트 파일도 그 페이지의 쿠키로 받는다).
     */
    fun offerFile(name: String, size: String, url: String, userAgent: String, referer: String?, saveFile: () -> Unit) {
        host.trace("torrent file offered $name")
        AlertDialog.Builder(activity)
            .setTitle(R.string.torrent_file_question)
            .setMessage(activity.getString(R.string.torrent_file_detail, name, size))
            .setPositiveButton(R.string.torrent_as_torrent) { _, _ -> TorrentDialogs.notice(activity) { readTorrentFile(url, userAgent, referer, saveFile) } }
            .setNeutralButton(R.string.torrent_as_file) { _, _ -> saveFile() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * 마그넷 — 이 앱에서 받을지 다른 앱으로 넘길지 묻는다. [otherApp] 은 지금까지의 '다른 앱으로'(프록시를 거치지 않는다 — 묻는 글이 말한다).
     * 읽지 못하는 마그넷(순수 v2·깨짐)은 까닭을 말하고, 다른 앱으로 넘길 길만 남긴다.
     */
    fun offerMagnet(uri: String, otherApp: () -> Unit) {
        val magnet = try {
            MagnetLink.parse(uri)
        } catch (e: TorrentException) {
            host.trace("magnet refused ${e.error}")
            TorrentDialogs.refusal(activity, activity.getString(R.string.torrent_magnet_question), e.error, R.string.torrent_other_app to otherApp)
            return
        }
        host.trace("magnet offered ${magnet.infoHashHex}")
        val text = listOf(
            magnet.displayName?.takeIf { it.isNotBlank() } ?: activity.getString(R.string.torrent_magnet_unnamed),
            activity.getString(R.string.torrent_magnet_hash, magnet.infoHashHex),
            activity.getString(R.string.torrent_magnet_sources, magnet.trackers.size, magnet.webSeeds.size, magnet.peers.size),
            "",
            activity.getString(R.string.torrent_magnet_note),
        ).joinToString("\n")
        AlertDialog.Builder(activity)
            .setTitle(R.string.torrent_magnet_question)
            .setMessage(text)
            .setPositiveButton(R.string.torrent_this_app) { _, _ -> TorrentDialogs.notice(activity) { addMagnet(uri) } }
            .setNeutralButton(R.string.torrent_other_app) { _, _ -> otherApp() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ── .torrent ────────────────────────────────────────────────────────────────────────────────────────

    private fun readTorrentFile(url: String, userAgent: String, referer: String?, saveFile: () -> Unit) {
        if (url.startsWith("data:", ignoreCase = true)) {
            val data = DataUrl.parse(url)
            if (data == null) TorrentDialogs.refusal(activity, null, TorrentError.INVALID_TORRENT) else inspect(data.bytes, saveFile, startReading())
            return
        }
        val serial = startReading() ?: return
        fetch = app.downloads.fetchSmall(url, userAgent, referer, MAX_TORRENT_BYTES) { result ->
            if (!stillReading(serial)) return@fetchSmall
            fetch = null
            when (result) {
                is DownloadCenter.SmallFile.Ready -> inspect(result.bytes, saveFile, serial)
                is DownloadCenter.SmallFile.Failed -> {
                    stopReading()
                    host.trace("torrent file fetch failed ${result.message}")
                    AlertDialog.Builder(activity)
                        .setTitle(R.string.torrent_fetch_failed)
                        .setMessage(result.message)
                        .setNegativeButton(R.string.close, null)
                        .show()
                }
            }
        }
    }

    /** 엔진이 읽는다(넣지 않는다) — 받지 않는 것이면 까닭과 '파일로 저장'. 읽는 동안 진행 창을 그대로 둔다. */
    private fun inspect(bytes: ByteArray, saveFile: () -> Unit, serial: Int?) {
        app.torrents.inspect(bytes) { result ->
            if (activity.isDestroyed || (serial != null && !stillReading(serial))) return@inspect
            stopReading()
            when (result) {
                is TorrentCenter.Inspected.Refused -> {
                    host.trace("torrent refused ${result.error} ${result.detail ?: ""}")
                    TorrentDialogs.refusal(activity, null, result.error, R.string.torrent_as_file to saveFile)
                }
                is TorrentCenter.Inspected.Ok -> ask(result.info, bytes)
            }
        }
    }

    /** 받을 파일을 고르게 하고 넣는다. 고를 것이 하나뿐이면 목록 없이 묻는다. */
    private fun ask(info: TorrentInfo, bytes: ByteArray) {
        val size = { n: Long -> Formatter.formatShortFileSize(activity, n) }
        val selectable = info.files.filter { it.isSelectable }
        val intro = listOf(
            activity.getString(R.string.torrent_ask_summary, size(info.totalLength), selectable.size, info.trackers.sumOf { it.size }, info.webSeeds.size),
            activity.getString(R.string.torrent_direct_note),
        ).joinToString("\n")
        if (selectable.size <= 1) {
            AlertDialog.Builder(activity)
                .setTitle(info.name)
                .setMessage(intro)
                .setPositiveButton(R.string.download) { _, _ -> add(bytes, selectable.map { it.index }.toSet()) }
                .setNegativeButton(R.string.cancel, null)
                .show()
            return
        }
        TorrentDialogs.chooseFiles(activity, info.name, intro, info.files, info.defaultSelection, null, R.string.download) { chosen -> add(bytes, chosen) }
    }

    private fun add(bytes: ByteArray, selection: Set<Int>) {
        host.askNotificationPermission()
        app.torrents.add(bytes, selection) { result -> afterAdd(result) }
    }

    // ── 마그넷 ──────────────────────────────────────────────────────────────────────────────────────────

    private fun addMagnet(uri: String) {
        host.askNotificationPermission()
        app.torrents.addMagnet(uri) { result -> afterAdd(result, magnet = true) }
    }

    private fun afterAdd(result: TorrentCenter.Added, magnet: Boolean = false) {
        if (activity.isDestroyed) return
        when (result) {
            is TorrentCenter.Added.Ok -> host.showMessage(
                activity.getString(if (magnet) R.string.torrent_started_magnet else R.string.torrent_started),
                activity.getString(R.string.torrent_open_list),
            ) { openList() }
            is TorrentCenter.Added.Refused -> if (result.error == TorrentError.DUPLICATE) {
                AlertDialog.Builder(activity)
                    .setMessage(R.string.torrent_error_duplicate)
                    .setPositiveButton(R.string.torrent_open_list) { _, _ -> openList() }
                    .setNegativeButton(R.string.close, null)
                    .show()
            } else {
                host.trace("torrent add refused ${result.error} ${result.detail ?: ""}")
                TorrentDialogs.refusal(activity, null, result.error)
            }
        }
    }

    private fun openList() {
        activity.startActivity(Intent(activity, DownloadsActivity::class.java))
    }

    // ── 진행 창 ─────────────────────────────────────────────────────────────────────────────────────────

    /** 토렌트 파일을 받는(읽는) 중 — 취소할 수 있다. 이미 떠 있으면 null(거듭 누름은 무시). */
    private fun startReading(): Int? {
        if (reading != null) return null
        val serial = ++readingSerial
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(activity, 24), Ui.dp(activity, 20), Ui.dp(activity, 24), Ui.dp(activity, 8))
            addView(ProgressBar(activity).apply { isIndeterminate = true })
            addView(TextView(activity).apply {
                text = activity.getString(R.string.torrent_reading)
                textSize = 16f
                setPadding(Ui.dp(activity, 16), 0, 0, 0)
            })
        }
        val dialog = AlertDialog.Builder(activity)
            .setView(row)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnDismissListener {
            // 사용자가 닫았다 — 늦게 오는 답을 버린다(답이 와서 닫을 때는 먼저 비우므로 여기에 오지 않는다).
            if (reading === dialog) {
                reading = null
                readingSerial++
                fetch?.cancel()
                fetch = null
            }
        }
        reading = dialog
        dialog.show()
        return serial
    }

    private fun stillReading(serial: Int): Boolean = serial == readingSerial && reading != null && !activity.isDestroyed

    private fun stopReading() {
        val d = reading ?: return
        reading = null
        fetch = null
        d.dismiss()
    }

    private companion object {
        /** `.torrent` 의 상한 — 엔진의 bencode 상한(`Bencode.Limits.TORRENT` 의 10,000,000 바이트)과 같다. 그보다 큰 것은 엔진이 어차피 읽지 않는다. */
        const val MAX_TORRENT_BYTES = 10_000_000
    }
}
