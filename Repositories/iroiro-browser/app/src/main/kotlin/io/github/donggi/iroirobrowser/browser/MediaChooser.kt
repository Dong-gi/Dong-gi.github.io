package io.github.donggi.iroirobrowser.browser

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Typeface
import android.os.SystemClock
import android.text.InputType
import android.text.TextUtils
import android.text.format.Formatter
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import io.github.donggi.iroirobrowser.R
import io.github.donggi.iroirobrowser.WebApp
import io.github.donggi.iroirobrowser.core.DownloadNames
import io.github.donggi.iroirobrowser.core.Referrer
import io.github.donggi.iroirobrowser.core.adblock.Request
import io.github.donggi.iroirobrowser.core.media.DashManifest
import io.github.donggi.iroirobrowser.core.media.FoundMediaLog
import io.github.donggi.iroirobrowser.core.media.HlsPlaylist
import io.github.donggi.iroirobrowser.core.media.MediaCodecs
import io.github.donggi.iroirobrowser.core.media.MediaKind
import io.github.donggi.iroirobrowser.core.media.MediaSniffer
import io.github.donggi.iroirobrowser.download.DownloadCenter
import io.github.donggi.iroirobrowser.ui.Ui

/**
 * '찾은 미디어' 의 창들 — 브라우저 화면([Host])이 띄운다(메인 스레드).
 *
 * - **목록**([show]): 지금 페이지의 것을 위에(최근에 요청된 차례), 앞 페이지의 것은 '앞 페이지 — 제목 · n분 전' 아래에 흐리게(사용자가 짚었다 —
 *   어느 페이지의 것인지 가릴 수 없었다. [FoundMediaLog]). 번호만 다른 파일은 한 줄, 읽은 HLS 마스터 아래의 재생목록은 숨긴다.
 * - **받기 확인**([confirmDownload]): 이름 칸(기본은 찾은 페이지의 제목 — 주소 끝의 이름은 흔히 무의미한 글자다)과 프록시 길로 미리 읽은 크기.
 *   고리를 길게 눌러 '링크 내려받기' 도 이 창이다.
 * - **재생목록 읽기**: 읽는 동안 취소할 수 있는 진행 창 하나 — 읽는 중에 다시 누르면 무시한다(앞 판은 2초 토스트 뒤 아무것도 없다가
 *   화질 창이 불쑥 떴고, 다시 누르면 창이 겹쳤다). 하위 재생목록을 고르면 그 마스터의 화질 창으로 돌린다(소리 없는 영상이 받아졌다).
 * - **화질 고르기**: 어림 크기·코덱·'소리만'. 마스터면 가장 높은 화질의 재생목록을 한 번 더 읽어 길이를 안다(실시간·DRM 도 그때 안다).
 * - 실시간 방송은 받지 않는 대신 그 자리에서 '재생 중인 것 받기' 로 잇는다.
 *
 * 받기를 시작하는 곳마다 그 미디어를 찾은 페이지의 주소·제목([MediaRequest.pageUrl]·[MediaRequest.pageTitle])이 손에 있다.
 */
class MediaChooser(private val activity: Activity, private val host: Host) {

    interface Host {
        fun trace(message: String)

        /** '재생 중인 것 받기' — 지금 탭에서 가로채기·녹화를 고른다. */
        fun chooseCapture()

        fun askNotificationPermission()

        /** 그 탭의 찾은 미디어가 바뀌었다(뒤에서 읽은 마스터가 그 아래를 숨겼다 — 막대의 수를 다시 센다). */
        fun onMediaChanged(tab: Tab)
    }

    /**
     * 찾은 미디어 하나를 받을 때 함께 넘길 것 — 쪽의 User-Agent, 그 미디어를 **찾은 페이지**의 주소(Referer 를 정한다)와 제목(기본 이름).
     * [entry] 가 없으면(고리를 길게 눌렀다) 지금 페이지.
     */
    private class MediaRequest(val tab: Tab, val url: String, entry: FoundMediaLog.Entry?) {
        val pageUrl: String = entry?.pageUrl?.ifEmpty { null } ?: tab.committedUrl
        val pageTitle: String = entry?.pageTitle?.ifBlank { null } ?: tab.title
        val userAgent: String = tab.webView?.settings?.userAgentString.orEmpty()
        val referer: String? = Referrer.strictOriginWhenCrossOrigin(pageUrl, url)

        /** 받는 파일의 바탕 이름(확장자는 받으면서 붙는다) — 찾은 페이지의 제목, 없으면 사이트. */
        val base: String = pageTitle.ifBlank { Request.hostOf(pageUrl).ifBlank { "video" } }
    }

    /** 목록 창의 한 줄 — 페이지의 머리 또는 미디어. */
    private sealed class Line {
        class Header(val text: String) : Line()
        class Item(val row: FoundMediaLog.Row, val dim: Boolean) : Line()
    }

    /** 고르는 창의 한 줄 — [label] 아래에 [detail](어림 크기·코덱). */
    private class Choice(val label: String, val detail: String?, val pick: () -> Unit)

    private val app = WebApp.of(activity)
    private val probe = MediaProbe(app.network)

    /** 재생목록을 읽는 중의 진행 창 — 하나만. */
    private var reading: AlertDialog? = null

    /** 지금 읽는 재생목록 — 진행 창을 취소하면 거둔다(일꾼이 더 받지 않고 답도 오지 않는다). */
    private var inspection: DownloadCenter.Inspection? = null

    /** 읽기마다 오르는 번호 — 취소한(또는 앞선) 읽기의 늦은 답을 버린다. */
    private var readingSerial = 0

    fun close() {
        probe.shutdown()
        stopReading()
    }

    // ── 목록 ──────────────────────────────────────────────────────────────────────────────────────────────

    /** '찾은 미디어' — 누르면 받는다(HLS·DASH 는 화질을 고른다). */
    fun show(tab: Tab) {
        val now = SystemClock.elapsedRealtime()
        val listing = tab.media.listing(now)
        if (listing.isEmpty) return
        val lines = ArrayList<Line>()
        if (listing.current.isEmpty()) {
            lines += Line.Header(activity.getString(R.string.media_none_here))
        } else if (listing.previous.isNotEmpty()) {
            lines += Line.Header(activity.getString(R.string.media_this_page))
        }
        listing.current.forEach { lines += Line.Item(it, dim = false) }
        for (section in listing.previous) {
            // 그 페이지에서 찾은 때의 제목(SPA 가 제목을 먼저 바꾸면 페이지의 마지막 제목은 다음 것일 수 있다).
            val title = section.rows.firstNotNullOfOrNull { it.entry.pageTitle.ifBlank { null } }
                ?: section.page.title.ifBlank { Request.hostOf(section.page.url) }.ifBlank { "?" }
            lines += Line.Header(activity.getString(R.string.media_previous_page, title, ago(now - section.page.left)))
            section.rows.forEach { lines += Line.Item(it, dim = true) }
        }
        host.trace("media list current=${listing.current.size} previous=${listing.previous.sumOf { it.rows.size }}")
        var dialog: AlertDialog? = null
        val list = ListView(activity).apply {
            adapter = object : BaseAdapter() {
                override fun getCount() = lines.size
                override fun getItem(position: Int) = lines[position]
                override fun getItemId(position: Int) = position.toLong()
                override fun areAllItemsEnabled() = false
                override fun isEnabled(position: Int) = lines[position] is Line.Item
                override fun getViewTypeCount() = 2
                override fun getItemViewType(position: Int) = if (lines[position] is Line.Header) 0 else 1
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View = when (val line = lines[position]) {
                    is Line.Header -> header(line.text)
                    is Line.Item -> twoLine(label(line.row), detail(line.row), if (line.dim) DIM else 1f)
                }
            }
            setOnItemClickListener { _, _, position, _ ->
                val line = lines[position] as? Line.Item ?: return@setOnItemClickListener
                dialog?.dismiss()
                pick(tab, line.row)
            }
        }
        dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.media_found)
            .setView(list)
            .setNeutralButton(R.string.capture_menu) { _, _ -> host.chooseCapture() }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    /** 줄의 글 — `HLS · master.m3u8`(종류와 주소의 이름). */
    private fun label(row: FoundMediaLog.Row): String = "${activity.getString(kindLabel(row.entry.kind))} · ${MediaSniffer.displayName(row.url)}"

    /** 줄 아래의 글 — 묶음의 수, 읽은 마스터의 화질·소리·자막 수, 아니면 호스트. */
    private fun detail(row: FoundMediaLog.Row): String? {
        val e = row.entry
        if (row.series) return activity.getString(R.string.media_series_detail, row.urls.size)
        e.master?.let { m ->
            return listOfNotNull(
                activity.getString(R.string.media_master_variants, m.variants),
                m.audio.takeIf { it > 0 }?.let { activity.getString(R.string.media_master_audio, it) },
                m.subtitles.takeIf { it > 0 }?.let { activity.getString(R.string.media_master_subtitles, it) },
            ).joinToString(" · ")
        }
        if (e.kind == MediaKind.HLS && e.read == FoundMediaLog.Read.MEDIA) return activity.getString(R.string.media_single_playlist)
        return Request.hostOf(row.url).ifBlank { null }
    }

    private fun kindLabel(kind: MediaKind): Int = when (kind) {
        MediaKind.HLS -> R.string.media_kind_hls
        MediaKind.DASH -> R.string.media_kind_dash
        MediaKind.VIDEO -> R.string.media_kind_video
        MediaKind.AUDIO -> R.string.media_kind_audio
    }

    private fun pick(tab: Tab, row: FoundMediaLog.Row) {
        val e = row.entry
        if (row.series) {
            askSeries(tab, row)
            return
        }
        when (e.kind) {
            MediaKind.VIDEO, MediaKind.AUDIO -> confirmDownload(tab, row.url, e, MediaSniffer.nameFromTitle(e.pageTitle.ifBlank { tab.title }, row.url))
            MediaKind.DASH -> readDash(tab, e)
            // 하위 재생목록이면 그 마스터로 — 소리 없는 영상을 받지 않게.
            MediaKind.HLS -> readHls(tab, tab.media.masterOf(e.url) ?: e)
        }
    }

    /** 번호만 다른 파일 묶음 — 영상의 조각이면 하나만 받아서는 볼 수 없다. '재생 중인 것 받기' 를 권하고, 그래도 하나를 고를 수 있게. */
    private fun askSeries(tab: Tab, row: FoundMediaLog.Row) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.media_series_title)
            .setMessage(activity.getString(R.string.media_series_question, row.urls.size))
            .setPositiveButton(R.string.capture_menu) { _, _ -> host.chooseCapture() }
            .setNeutralButton(R.string.media_series_pick) { _, _ ->
                showChoices(
                    R.string.media_series_title,
                    row.urls.take(MAX_SERIES_LIST).map { u -> Choice(MediaSniffer.displayName(u), null) { confirmDownload(tab, u, row.entry, null) } },
                )
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    // ── 받기 확인 ─────────────────────────────────────────────────────────────────────────────────────────

    /**
     * 파일 하나를 받을까 묻는다 — 이름 칸(기본은 [suggested], 없으면 주소의 이름)과 프록시 길로 미리 읽은 크기. [entry] 는 그 파일을 찾은 항목
     * (고리를 길게 눌렀으면 null — 그때는 지금 페이지가 Referer 다).
     */
    fun confirmDownload(tab: Tab, url: String, entry: FoundMediaLog.Entry?, suggested: String?) {
        val request = MediaRequest(tab, url, entry)
        val downloads = app.downloads
        val original = suggested ?: downloads.names.choose(null, url, null)
        val name = nameField(original)
        val size = note(activity.getString(R.string.size_checking))
        host.trace("download asked $original")
        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.download_question)
            .setView(form(name, size))
            .setPositiveButton(R.string.download) { _, _ ->
                val chosen = DownloadNames.edited(name.text.toString(), original)
                host.askNotificationPermission()
                // 찾은 페이지를 함께 적는다 — 주소가 만료되면 다시 열 곳이고, 같은 것을 다시 고르면 멈춘 항목을 잇는다(BR-NET).
                downloads.enqueue(url, chosen, null, request.userAgent, request.referer, request.pageUrl, request.pageTitle)
                host.trace("download started $chosen")
                Toast.makeText(activity, R.string.download_started, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
        probe.size(url, request.userAgent, request.referer) { bytes ->
            if (!dialog.isShowing) return@size
            size.text = if (bytes > 0) Formatter.formatShortFileSize(activity, bytes) else activity.getString(R.string.size_unknown)
        }
    }

    // ── HLS ──────────────────────────────────────────────────────────────────────────────────────────────

    private fun readHls(tab: Tab, entry: FoundMediaLog.Entry) {
        val serial = startReading() ?: return
        inspectHls(tab, entry, serial, redirected = false)
    }

    private fun inspectHls(tab: Tab, entry: FoundMediaLog.Entry, serial: Int, redirected: Boolean) {
        val request = MediaRequest(tab, entry.url, entry)
        inspection = app.downloads.inspectHls(entry.url, request.userAgent, request.referer) { info ->
            if (!stillReading(serial)) return@inspectHls
            when (info) {
                is DownloadCenter.HlsInfo.Failed -> {
                    stopReading()
                    host.trace("media refused ${info.message}")
                    Toast.makeText(activity, activity.getString(R.string.media_failed, info.message), Toast.LENGTH_LONG).show()
                }
                is DownloadCenter.HlsInfo.Single -> {
                    // 하위 재생목록이었다 — 그새 뒤에서 읽힌 마스터가 있으면 그 화질 창으로(소리 없는 영상을 받지 않게).
                    val master = tab.media.masterOf(entry.url)?.takeIf { !redirected && it !== entry }
                    if (master != null) {
                        host.trace("media master ${master.url}")
                        inspectHls(tab, master, serial, redirected = true)
                    } else {
                        stopReading()
                        confirmHls(request, info.media)
                    }
                }
                is DownloadCenter.HlsInfo.Choices -> readTopVariant(request, info.master, serial)
            }
        }
    }

    /** 마스터 — 가장 높은 화질의 재생목록을 한 번 더 읽어 길이(크기 어림)·실시간·DRM 을 본 뒤 화질 창. 읽지 못해도 화질 창은 연다(크기 없이). */
    private fun readTopVariant(request: MediaRequest, master: HlsPlaylist.Master, serial: Int) {
        inspection = app.downloads.inspectHls(master.variants.first().uri, request.userAgent, request.referer) { info ->
            if (!stillReading(serial)) return@inspectHls
            stopReading()
            val media = (info as? DownloadCenter.HlsInfo.Single)?.media
            val refusal = media?.let { refusalOf(it) }
            if (refusal != null) refuse(refusal) else chooseHlsQuality(request, master, media?.totalDuration ?: 0.0)
        }
    }

    private fun refusalOf(media: HlsPlaylist.Media): String? = when {
        !media.ended -> DownloadCenter.HLS_LIVE
        media.segments.any { it.key?.isAes128 == false } -> DownloadCenter.HLS_DRM
        else -> null
    }

    /** 화질 하나짜리 재생목록 — 조각 수·길이와 이름 칸. 그 마스터를 찾지 못했으므로 소리가 따로인 영상이면 소리가 빠진다고 적는다. */
    private fun confirmHls(request: MediaRequest, media: HlsPlaylist.Media) {
        refusalOf(media)?.let {
            refuse(it)
            return
        }
        val name = nameField(request.base)
        val detail = note(
            activity.getString(R.string.media_hls_parts, media.segments.size, duration(media.totalDuration)) + "\n" +
                activity.getString(R.string.media_hls_single_note),
        )
        AlertDialog.Builder(activity)
            .setTitle(R.string.download_question)
            .setView(form(name, detail))
            .setPositiveButton(R.string.download) { _, _ -> startHls(request, request.url, DownloadNames.edited(name.text.toString(), request.base), null) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun chooseHlsQuality(request: MediaRequest, master: HlsPlaylist.Master, seconds: Double) {
        val choices = ArrayList<Choice>()
        for (v in master.variants) {
            val bytes = MediaCodecs.estimateBytes(if (v.averageBandwidth > 0) v.averageBandwidth else v.bandwidth, seconds)
            choices += Choice(v.label, detailOf(v.codecs, bytes)) {
                // 따로 된 소리(그 화질의 소리 묶음 가운데 기본 — 없으면 첫째)도 받아 영상과 한 파일로 담는다.
                val audio = v.audioGroup?.let { g ->
                    val group = master.audio.filter { it.groupId == g && it.uri != null }
                    (group.firstOrNull { it.isDefault } ?: group.firstOrNull())?.uri
                }
                startHls(request, v.uri, request.base, audio)
            }
        }
        // 소리만 — 따로 된 소리 재생목록마다(말이 여럿이면 하나씩).
        for (r in master.audio.filter { it.uri != null }.distinctBy { it.uri }) {
            val uri = r.uri ?: continue
            choices += Choice(activity.getString(R.string.media_audio_only, r.name.ifBlank { r.language ?: r.groupId }), null) {
                startHls(request, uri, activity.getString(R.string.media_audio_name, request.base), null, audioOnly = true)
            }
        }
        host.trace("media variants ${master.variants.joinToString { it.label }}")
        showChoices(R.string.media_choose_quality, choices)
    }

    private fun startHls(request: MediaRequest, url: String, base: String, audioUrl: String?, audioOnly: Boolean = false) {
        host.askNotificationPermission()
        app.downloads.enqueueHls(url, audioUrl, base, request.userAgent, request.referer, request.pageUrl, request.pageTitle, audioOnly)
        Toast.makeText(activity, if (audioUrl != null) R.string.media_separate_audio else R.string.download_started, Toast.LENGTH_LONG).show()
        host.trace("hls started $url audio=${audioUrl ?: "-"}")
    }

    // ── DASH ─────────────────────────────────────────────────────────────────────────────────────────────

    private fun readDash(tab: Tab, entry: FoundMediaLog.Entry) {
        val serial = startReading() ?: return
        val request = MediaRequest(tab, entry.url, entry)
        inspection = app.downloads.inspectDash(entry.url, request.userAgent, request.referer) { info ->
            if (!stillReading(serial)) return@inspectDash
            stopReading()
            onDash(request, info)
        }
    }

    /** DASH — 실시간·DRM 은 받지 않고, 영상 화질을 고르면 가장 좋은 소리를 함께 받아 한 파일로 담는다. '소리만' 도 고를 수 있다. */
    private fun onDash(request: MediaRequest, info: DownloadCenter.DashInfo) {
        val manifest = when (info) {
            is DownloadCenter.DashInfo.Failed -> {
                host.trace("media refused ${info.message}")
                Toast.makeText(activity, activity.getString(R.string.media_failed, info.message), Toast.LENGTH_LONG).show()
                return
            }
            is DownloadCenter.DashInfo.Ready -> info.manifest
        }
        val videos = manifest.videos.filter { !it.protected }
        val audios = manifest.audios.filter { !it.protected }
        val refusal = when {
            manifest.dynamic -> DownloadCenter.HLS_LIVE
            videos.isEmpty() && audios.isEmpty() -> DownloadCenter.HLS_DRM
            else -> null
        }
        if (refusal != null) {
            refuse(refusal)
            return
        }
        val audio = audios.firstOrNull()
        if (videos.isEmpty()) {
            startDash(request, null, audio, request.base)
            return
        }
        val choices = ArrayList<Choice>()
        for (v in videos) {
            val bytes = MediaCodecs.estimateBytes(v.bandwidth + (audio?.bandwidth ?: 0L), manifest.duration)
            choices += Choice(v.label, detailOf(listOfNotNull(v.codecs, audio?.codecs).joinToString(","), bytes)) {
                startDash(request, v, audio, request.base)
            }
        }
        if (audio != null) {
            val bytes = MediaCodecs.estimateBytes(audio.bandwidth, manifest.duration)
            choices += Choice(activity.getString(R.string.media_audio_only, audio.label), detailOf(audio.codecs, bytes)) {
                startDash(request, null, audio, activity.getString(R.string.media_audio_name, request.base))
            }
        }
        host.trace("media variants ${videos.joinToString { it.label }}")
        showChoices(R.string.media_choose_quality, choices)
    }

    private fun startDash(request: MediaRequest, video: DashManifest.Representation?, audio: DashManifest.Representation?, base: String) {
        if (video == null && audio == null) return
        host.askNotificationPermission()
        app.downloads.enqueueDash(video, audio, base, request.userAgent, request.referer, request.pageUrl, request.pageTitle)
        Toast.makeText(activity, if (video != null && audio != null) R.string.media_separate_audio else R.string.download_started, Toast.LENGTH_LONG).show()
        host.trace("dash started ${video?.id ?: "-"} ${audio?.id ?: "-"}")
    }

    /** 받지 않는 것 — 실시간 방송이면 그 자리에서 '재생 중인 것 받기' 로 잇는다. 그 밖(DRM 등)은 까닭만. */
    private fun refuse(reason: String) {
        host.trace("media refused $reason")
        if (reason != DownloadCenter.HLS_LIVE) {
            Toast.makeText(activity, reason, Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.media_live_title)
            .setMessage(R.string.media_live_message)
            .setPositiveButton(R.string.capture_menu) { _, _ -> host.chooseCapture() }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    // ── 뒤에서 읽기 ───────────────────────────────────────────────────────────────────────────────────────

    /**
     * 찾은 HLS 재생목록을 뒤에서 읽어 마스터인지 본다 — 마스터면 그 아래 재생목록(화질·소리·자막)을 목록에서 숨긴다. 재생기가 이미
     * 받은 것을 한 번 더 받는다(프록시로, 페이지마다 [FoundMediaLog.MAX_READS_PER_PAGE] 개까지).
     */
    fun readBehind(tab: Tab, url: String) {
        if (!tab.media.claimRead(url)) return
        val page = tab.media.currentPage.url.ifEmpty { tab.committedUrl }
        val userAgent = tab.webView?.settings?.userAgentString.orEmpty()
        probe.playlist(url, userAgent, Referrer.strictOriginWhenCrossOrigin(page, url)) { playlist ->
            tab.media.readDone(url, playlist)
            val what = when (playlist) {
                is HlsPlaylist.Master -> "master ${playlist.variants.size}"
                is HlsPlaylist.Media -> "media"
                null -> "failed"
            }
            host.trace("media read $what $url")
            host.onMediaChanged(tab)
        }
    }

    // ── 진행 창 ──────────────────────────────────────────────────────────────────────────────────────────

    /** 읽기를 시작한다 — 진행 창을 띄우고 번호를 준다. 이미 읽는 중이면(다시 누름) null — 무시한다. */
    private fun startReading(): Int? {
        if (reading != null) return null
        val serial = ++readingSerial
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(activity, 24), Ui.dp(activity, 20), Ui.dp(activity, 24), Ui.dp(activity, 8))
            addView(ProgressBar(activity).apply { isIndeterminate = true })
            addView(TextView(activity).apply {
                text = activity.getString(R.string.media_reading)
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
            // 사용자가 닫았다(취소·뒤로) — 늦게 오는 답을 버린다. 답이 와서 닫을 때는 먼저 비우므로 여기에 오지 않는다.
            if (reading === dialog) {
                reading = null
                readingSerial++
                inspection?.cancel()
                inspection = null
                host.trace("media reading cancelled")
            }
        }
        reading = dialog
        dialog.show()
        return serial
    }

    private fun stillReading(serial: Int): Boolean = serial == readingSerial && reading != null && !activity.isDestroyed

    /** 답이 왔다 — 진행 창을 닫는다(먼저 비운다 — 닫힘을 취소로 보지 않게). */
    private fun stopReading() {
        val d = reading ?: return
        reading = null
        inspection = null
        d.dismiss()
    }

    // ── 화면 조각 ────────────────────────────────────────────────────────────────────────────────────────

    private fun showChoices(title: Int, choices: List<Choice>) {
        var dialog: AlertDialog? = null
        val list = ListView(activity).apply {
            adapter = object : BaseAdapter() {
                override fun getCount() = choices.size
                override fun getItem(position: Int) = choices[position]
                override fun getItemId(position: Int) = position.toLong()
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                    twoLine(choices[position].label, choices[position].detail, 1f)
            }
            setOnItemClickListener { _, _, position, _ ->
                dialog?.dismiss()
                choices[position].pick()
            }
        }
        dialog = AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(list)
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** 두 줄 — 위가 고르는 이름(시험 도구가 이 글로 누른다), 아래가 덧붙임. [rowAlpha] 가 1 보다 작으면 흐리게(앞 페이지). */
    private fun twoLine(primary: String, secondary: String?, rowAlpha: Float): View = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = Ui.dp(activity, 52)
        setPadding(Ui.dp(activity, 24), Ui.dp(activity, 8), Ui.dp(activity, 24), Ui.dp(activity, 8))
        alpha = rowAlpha
        addView(TextView(activity).apply {
            text = primary
            textSize = 16f
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.MIDDLE
        })
        if (!secondary.isNullOrBlank()) {
            addView(TextView(activity).apply {
                text = secondary
                textSize = 13f
                alpha = 0.7f
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
            })
        }
    }

    private fun header(text: String): View = TextView(activity).apply {
        this.text = text
        textSize = 13f
        alpha = 0.75f
        setTypeface(typeface, Typeface.BOLD)
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        isAccessibilityHeading = true
        setPadding(Ui.dp(activity, 24), Ui.dp(activity, 14), Ui.dp(activity, 24), Ui.dp(activity, 4))
    }

    private fun nameField(value: String): EditText = EditText(activity).apply {
        setText(value)
        isSingleLine = true
        hint = activity.getString(R.string.download_name)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    }

    private fun note(text: String): TextView = TextView(activity).apply {
        this.text = text
        textSize = 14f
        alpha = 0.8f
        setPadding(Ui.dp(activity, 4), Ui.dp(activity, 8), Ui.dp(activity, 4), 0)
    }

    private fun form(vararg views: View): View = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(Ui.dp(activity, 20), Ui.dp(activity, 8), Ui.dp(activity, 20), 0)
        views.forEach { addView(it, Ui.matchWidth()) }
    }

    /** 화질 줄의 덧붙임 — 코덱과 어림 크기('약 120 MB'). 둘 다 모르면 null. */
    private fun detailOf(codecs: String?, bytes: Long?): String? =
        listOfNotNull(MediaCodecs.describe(codecs), bytes?.let { activity.getString(R.string.media_size_about, Formatter.formatShortFileSize(activity, it)) })
            .joinToString(" · ").ifEmpty { null }

    /** 재생 시간 — 1분이 안 되면 초로(앞 판은 '약 0분' 이었다). */
    private fun duration(seconds: Double): String {
        val s = seconds.toLong()
        return when {
            s < 60 -> activity.getString(R.string.duration_seconds, s.toInt())
            s < 3600 -> activity.getString(R.string.duration_minutes, ((s + 30) / 60).toInt())
            else -> activity.getString(R.string.duration_hours, (s / 3600).toInt(), (s / 60 % 60).toInt())
        }
    }

    /** 앞 페이지를 떠난 지 — '방금'·'3분 전'·'1시간 전'. */
    private fun ago(ms: Long): String {
        val minutes = ms / 60_000
        return when {
            minutes < 1 -> activity.getString(R.string.media_ago_now)
            minutes < 60 -> activity.getString(R.string.media_ago_minutes, minutes.toInt())
            else -> activity.getString(R.string.media_ago_hours, (minutes / 60).toInt())
        }
    }

    private companion object {
        /** 앞 페이지의 줄. */
        const val DIM = 0.55f

        /** 번호만 다른 파일을 하나 고를 때 보이는 수. */
        const val MAX_SERIES_LIST = 100
    }
}
