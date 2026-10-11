package io.github.donggi.iroirobrowser.download

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import io.github.donggi.iroirobrowser.R
import io.github.donggi.iroirobrowser.WebApp
import io.github.donggi.iroirobrowser.browser.BrowserActivity
import io.github.donggi.iroirobrowser.torrent.TorrentState
import io.github.donggi.iroirobrowser.torrent.TorrentStatus
import io.github.donggi.iroirobrowser.ui.Ui

/**
 * 내려받기 목록. 누르면: 다 받은 것은 **기본 앱으로** 열고(앞 판은 열 때마다 앱 고르기 창이었다), 받는 중이면 일시 정지·받기 취소를 묻고, 멈춘
 * 것은 이어받기를 묻고(주소가 만료됐으면 그 까닭과 '원래 페이지 열기'), 실패한 것은 다시 받을지 묻는다. **길게 누르면 메뉴** — 열기·공유·원래
 * 페이지 열기·목록에서 빼기·파일까지 지우기 가운데 그 줄에 할 수 있는 것(앞 판은 길게 누르면 묻지도 않고 목록에서 뺐고, 멈춘 것이면 받던 것까지
 * 지웠다. 받는 중인 줄은 아무 반응이 없었다). 받은 것을 버리는 일(멈춘 것 지우기·받는 중 취소·파일 지우기)은 한 번 더 묻는다. 목록은 앱이 다시
 * 떠도 남는다.
 *
 * 받는 중의 단추는 '받기 취소(지움)'·'계속 받기' 다 — 이 앱의 다른 창에서 '취소' 는 창을 닫는 단추라, 받은 것을 지우는 단추와 같은 글이면 헷갈린다.
 *
 * **토렌트도 이 목록에**(사용자 결정 ㉒ — 기존 '내려받기' 화면에 함께): 위에 '토렌트' 머리(프록시를 거치지 않는다는 글을 늘 보인다)와 토렌트 줄
 * (상태·몫·속도·피어·지금 받는 파일·'조각이 모자라 기다림'·'망 조건 기다림'·오류 까닭), 아래에 보통 내려받기. 토렌트 줄을 누르면 자세히(피어·
 * 출처·트래커·옮긴 파일)와 할 수 있는 일(일시 정지·이어받기·파일 고르기·옮기기 다시 시도·지우기), 길게 누르면 그 일들의 메뉴. 화면이 보이는
 * 동안만 엔진을 붙잡는다([TorrentCenter.attach]). 마그넷의 파일 목록이 와서 고르기를 기다리면 고르는 창을 한 번 띄운다(알림에서 열어도).
 *
 * **갤러리 받기**(pixiv·hitomi — [DownloadCenter.Kind.SET])는 보통 내려받기 사이에 한 줄 — 제목, 'n / m개'(작가 받기는 '작품 a / b · 파일 n')와
 * 상태·까닭, 셋째 줄에 저장 자리. 누르면 자세히와 할 수 있는 일([GalleryRules.actions] — 일시 정지·이어받기·그만 받기·첫 파일 열기·원래 페이지
 * 열기·목록에서 빼기·받은 파일까지 지우기), 길게 누르면 그 일들의 메뉴. '그만 받기' 는 받은 파일을 남기고, 받은 파일까지 지우는 일은 한 번 더 묻는다.
 */
class DownloadsActivity : Activity() {

    private lateinit var center: DownloadCenter
    private lateinit var torrents: TorrentCenter
    private lateinit var adapter: Adapter
    private lateinit var empty: TextView
    private val listener = DownloadCenter.Listener { refresh() }
    private val torrentListener = TorrentCenter.Listener { refresh() }

    /** 알림에서 '파일을 고르세요' 로 열었다 — 그 토렌트가 목록에 오면 고르는 창을 띄운다. */
    private var pendingChoice: String? = null

    /** 이 화면에서 이미 고르는 창을 띄운 토렌트 — 닫았으면 다시 띄우지 않는다(줄을 누르면 다시 연다). */
    private val askedChoice = HashSet<String>()
    private var resumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.downloads)
        center = WebApp.of(this).downloads
        torrents = WebApp.of(this).torrents
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        Ui.edgeToEdge(this, root)
        empty = TextView(this).apply {
            text = getString(R.string.downloads_empty)
            setPadding(Ui.dp(context, 16), Ui.dp(context, 24), Ui.dp(context, 16), 0)
        }
        val list = ListView(this)
        adapter = Adapter()
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ ->
            when (val row = adapter.getItem(position)) {
                is Row.Download -> onTap(row.item)
                is Row.Torrent -> onTorrentTap(row.status)
                is Row.Header -> Unit
            }
        }
        list.setOnItemLongClickListener { _, _, position, _ ->
            when (val row = adapter.getItem(position)) {
                is Row.Download -> showMenu(row.item)
                is Row.Torrent -> showTorrentMenu(row.status)
                is Row.Header -> return@setOnItemLongClickListener false
            }
            true
        }
        root.addView(Ui.header(this, getString(R.string.downloads)) { finish() })
        root.addView(empty)
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        center.addListener(listener)
        torrents.addListener(torrentListener)
        torrents.attach()
        refresh()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        refresh()
    }

    override fun onPause() {
        resumed = false
        super.onPause()
    }

    override fun onStop() {
        center.removeListener(listener)
        torrents.removeListener(torrentListener)
        torrents.detach()
        super.onStop()
    }

    /**
     * 알림의 '원래 페이지 열기' — 그 항목의 페이지를 브라우저에서 연다. 알림의 '파일을 고르세요' — 그 토렌트의 고르는 창. 이 화면은 내보내지
     * 않았다 — 이 앱의 알림만 이것을 연다.
     */
    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_OPEN_PAGE -> center.find(intent.getLongExtra(EXTRA_ID, -1))?.let { openPage(it) }
            ACTION_CHOOSE_TORRENT -> intent.getStringExtra(EXTRA_TORRENT)?.let {
                pendingChoice = it
                askedChoice.remove(it)
            }
        }
    }

    private fun refresh() {
        val ts = torrents.statuses()
        val items = center.items()
        val rows = ArrayList<Row>()
        if (ts.isNotEmpty()) {
            rows += Row.Header(getString(R.string.torrent_section))
            ts.forEach { rows += Row.Torrent(it) }
            if (items.isNotEmpty()) rows += Row.Header(getString(R.string.downloads))
        }
        items.forEach { rows += Row.Download(it) }
        adapter.rows = rows
        adapter.notifyDataSetChanged()
        empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        offerChoice(ts)
    }

    /**
     * 파일을 골라야 하는 마그넷 — 알림에서 열었거나 화면이 앞에 있는 동안 메타데이터가 왔으면 고르는 창을 한 번 띄운다. 창을 닫았으면 다시
     * 띄우지 않는다(줄에 '받을 파일을 고르세요' 가 남고 누르면 연다).
     */
    private fun offerChoice(ts: List<TorrentStatus>) {
        if (!resumed || isFinishing) return
        val target = ts.firstOrNull { it.id == pendingChoice && torrents.needsChoice(it.id) }
            ?: ts.firstOrNull { torrents.needsChoice(it.id) && it.id !in askedChoice }
            ?: return
        if (torrents.info(target.id) == null) return
        pendingChoice = null
        askedChoice += target.id
        chooseFiles(target)
    }

    // ── 보통 내려받기 ───────────────────────────────────────────────────────────────────────────────────

    private fun onTap(item: DownloadCenter.Item) {
        if (item.gallery != null) {
            showSet(item, item.gallery)
            return
        }
        when (item.status) {
            DownloadCenter.Status.DONE -> open(item)
            DownloadCenter.Status.QUEUED, DownloadCenter.Status.RUNNING -> {
                // 보통 파일·HLS·DASH 는 멈췄다 이어받을 수 있다. 넘겨받는 것(blob)·마무리(가로채기·녹화)는 다시 할 수 없어 취소만.
                val b = AlertDialog.Builder(this)
                    .setTitle(item.savedName)
                    .setMessage(if (item.pausable) R.string.download_running_question else R.string.download_cancel_question)
                    .setNegativeButton(R.string.download_keep_going, null)
                    .setNeutralButton(R.string.download_cancel) { _, _ -> center.cancel(item) }
                if (item.pausable) b.setPositiveButton(R.string.download_pause) { _, _ -> center.pause(item) }
                b.show()
            }
            DownloadCenter.Status.PAUSED -> if (item.expired) {
                showExpired(item)
            } else {
                AlertDialog.Builder(this)
                    .setTitle(item.savedName)
                    .setMessage(getString(R.string.download_paused_question, describe(item)))
                    .setPositiveButton(R.string.download_resume) { _, _ -> center.resume(item) }
                    .setNeutralButton(R.string.download_delete) { _, _ -> confirmRemovePaused(item) }
                    .setNegativeButton(R.string.close, null)
                    .show()
            }
            DownloadCenter.Status.FAILED, DownloadCenter.Status.CANCELLED -> {
                val b = AlertDialog.Builder(this)
                    .setTitle(item.savedName)
                    .setMessage(item.error ?: getString(R.string.download_cancelled))
                    .setNegativeButton(R.string.close, null)
                if (item.pausable) {
                    b.setPositiveButton(R.string.download_retry) { _, _ -> center.retry(item) }
                }
                b.show()
            }
        }
    }

    /**
     * 주소가 만료됐거나 막혀 멈춘 것 — 같은 주소로 이어받아도 같으므로 까닭을 말하고 원래 페이지를 열게 한다. 그 페이지에서 같은 미디어를 다시
     * 고르면 이 항목에 새 주소를 붙여 받은 데서부터 잇는다(링크 갱신). 이어받기도 남긴다(잠깐 막혔던 것일 수 있다).
     */
    private fun showExpired(item: DownloadCenter.Item) {
        val page = item.pageUrl
        val message = buildString {
            append(item.error.orEmpty())
            append("\n\n")
            append(getString(if (page != null) R.string.download_expired_how else R.string.download_expired_no_page))
            if (page != null) append("\n\n").append(item.pageTitle?.takeIf { it.isNotBlank() } ?: page)
        }
        val b = AlertDialog.Builder(this)
            .setTitle(item.savedName)
            .setMessage(message)
            .setNeutralButton(R.string.download_resume) { _, _ -> center.resume(item) }
            .setNegativeButton(R.string.close, null)
        if (page != null) b.setPositiveButton(R.string.download_open_page) { _, _ -> openPage(item) }
        b.show()
    }

    /** 길게 누르기 — 그 줄에 할 수 있는 것만. */
    private fun showMenu(item: DownloadCenter.Item) {
        if (item.gallery != null) {
            val actions = setActions(item, item.gallery)
            AlertDialog.Builder(this)
                .setTitle(item.gallery.title)
                .setItems(actions.map { it.first }.toTypedArray()) { _, which -> actions[which].second() }
                .show()
            return
        }
        val actions = ArrayList<Pair<String, () -> Unit>>()
        fun add(text: Int, action: () -> Unit) {
            actions += getString(text) to action
        }
        when (item.status) {
            DownloadCenter.Status.DONE -> {
                add(R.string.open) { open(item) }
                if (item.savedUri != null) add(R.string.share) { share(item) }
            }
            DownloadCenter.Status.QUEUED, DownloadCenter.Status.RUNNING -> {
                if (item.pausable) add(R.string.download_pause) { center.pause(item) }
                add(R.string.download_cancel) { confirmCancel(item) }
            }
            DownloadCenter.Status.PAUSED -> add(R.string.download_resume) { center.resume(item) }
            DownloadCenter.Status.FAILED, DownloadCenter.Status.CANCELLED ->
                if (item.pausable) add(R.string.download_retry) { center.retry(item) }
        }
        if (item.pageUrl != null) add(R.string.download_open_page) { openPage(item) }
        when (item.status) {
            DownloadCenter.Status.DONE -> {
                add(R.string.download_remove_from_list) { center.remove(item) }
                if (item.savedUri != null) add(R.string.download_delete_file) { confirmDeleteFile(item) }
            }
            DownloadCenter.Status.PAUSED -> add(R.string.download_remove_paused) { confirmRemovePaused(item) }
            DownloadCenter.Status.FAILED, DownloadCenter.Status.CANCELLED -> add(R.string.download_remove_from_list) { center.remove(item) }
            DownloadCenter.Status.QUEUED, DownloadCenter.Status.RUNNING -> Unit
        }
        AlertDialog.Builder(this)
            .setTitle(item.savedName)
            .setItems(actions.map { it.first }.toTypedArray()) { _, which -> actions[which].second() }
            .show()
    }

    /** 다 받은 파일을 기본 앱으로 연다 — 정해 둔 앱이 없으면 시스템이 고르는 창을 띄운다. */
    private fun open(item: DownloadCenter.Item) {
        val intent = center.openIntent(item) ?: return
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.download_no_app, Toast.LENGTH_SHORT).show()
        }
    }

    private fun share(item: DownloadCenter.Item) {
        val intent = center.shareIntent(item) ?: return
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_app, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 원래 페이지를 브라우저의 새 탭에서 연다. 주소는 **프로세스 안에서만** 넘긴다([DownloadCenter.openPageLater] — 브라우저 화면은 내보낸 화면이라
     * 인텐트로 주소를 받지 않는다). 브라우저 화면은 `singleTask` 라 이 화면을 걷고 앞으로 나온다.
     */
    private fun openPage(item: DownloadCenter.Item) {
        val url = item.pageUrl ?: return
        DownloadCenter.openPageLater(url)
        startActivity(Intent(this, BrowserActivity::class.java))
        finish()
    }

    /** 받는 중인 것을 취소한다 — 받은 것을 지우므로 한 번 더 묻는다. */
    private fun confirmCancel(item: DownloadCenter.Item) {
        AlertDialog.Builder(this)
            .setTitle(item.savedName)
            .setMessage(getString(R.string.download_cancel_confirm, Formatter.formatShortFileSize(this, item.received)))
            .setPositiveButton(R.string.download_cancel) { _, _ -> center.cancel(item) }
            .setNegativeButton(R.string.download_keep_going, null)
            .show()
    }

    /** 멈춘 것을 지운다 — 받던 것(감춘 '내려받기' 항목·임시 파일)까지 지우므로 한 번 더 묻는다. */
    private fun confirmRemovePaused(item: DownloadCenter.Item) {
        AlertDialog.Builder(this)
            .setTitle(item.savedName)
            .setMessage(getString(R.string.download_remove_paused_question, Formatter.formatShortFileSize(this, item.received)))
            .setPositiveButton(R.string.download_delete) { _, _ -> center.remove(item) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** 다 받은 파일을 '내려받기' 폴더에서 지운다 — 되돌릴 수 없으므로 한 번 더 묻는다. */
    private fun confirmDeleteFile(item: DownloadCenter.Item) {
        AlertDialog.Builder(this)
            .setTitle(item.savedName)
            .setMessage(getString(R.string.download_delete_file_question, item.savedName))
            .setPositiveButton(R.string.download_delete_file) { _, _ ->
                center.deleteFile(item) { deleted ->
                    Toast.makeText(this, if (deleted) R.string.download_file_deleted else R.string.download_file_not_deleted, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun describe(item: DownloadCenter.Item): String {
        val size = { n: Long -> Formatter.formatShortFileSize(this, n) }
        val progress = when {
            item.parts > 0 -> getString(R.string.download_parts, item.partsDone, item.parts) + " · " + size(item.received)
            item.total > 0 -> "${size(item.received)} / ${size(item.total)} · ${DownloadTexts.percent(item)}%"
            else -> size(item.received)
        }
        return when (item.status) {
            DownloadCenter.Status.QUEUED -> getString(R.string.download_queued)
            DownloadCenter.Status.RUNNING -> when {
                item.merging -> DownloadTexts.merging(this, item)
                item.retrying > 0 -> getString(R.string.download_retrying, item.retrying, item.error ?: "") + " · " + progress
                else -> progress
            }
            DownloadCenter.Status.PAUSED -> getString(R.string.download_paused, item.error ?: "") + " · " + progress
            DownloadCenter.Status.DONE -> getString(R.string.download_saved, size(item.received)) + (item.note?.let { "\n$it" } ?: "")
            DownloadCenter.Status.FAILED -> getString(R.string.download_failed, item.error ?: "")
            DownloadCenter.Status.CANCELLED -> getString(R.string.download_cancelled)
        }
    }

    // ── 갤러리(pixiv·hitomi) ─────────────────────────────────────────────────────────────────────────────

    /** 누르면 — 자세히(상태·작가·저장 자리·건너뛴 것)와 할 수 있는 일. */
    private fun showSet(item: DownloadCenter.Item, g: DownloadCenter.Gallery) {
        val lines = ArrayList<String>()
        lines += describeSet(item, g)
        g.creator?.let { lines += getString(R.string.gallery_creator, it) }
        lines += getString(R.string.gallery_place, GalleryRules.relativePath(g.folder))
        // 다 받은 줄은 이미 건너뛴 수를 싣는다.
        if (item.status != DownloadCenter.Status.DONE) GalleryRules.note(g.skipped, g.skippedLogin)?.let { lines += TorrentTexts.text(this, it) }
        TorrentDialogs.details(this, g.title, lines, setActions(item, g))
    }

    /** 둘째 줄 — 상태와 진행(받은 파일 수·크기), 멈춘·실패한 까닭. */
    private fun describeSet(item: DownloadCenter.Item, g: DownloadCenter.Gallery): String {
        val progress = DownloadTexts.gallery(this, g)
        val size = Formatter.formatShortFileSize(this, item.received)
        return when (item.status) {
            DownloadCenter.Status.QUEUED -> getString(R.string.download_queued)
            DownloadCenter.Status.RUNNING ->
                if (item.retrying > 0) getString(R.string.download_retrying, item.retrying, item.error ?: "") + " · " + progress else "$progress · $size"
            DownloadCenter.Status.PAUSED -> getString(R.string.download_paused, item.error ?: "") + " · " + progress
            DownloadCenter.Status.DONE -> getString(R.string.gallery_done_line, g.filesSaved, size) + (item.note?.let { " · $it" } ?: "")
            DownloadCenter.Status.FAILED -> getString(R.string.download_failed, item.error ?: "")
            DownloadCenter.Status.CANCELLED -> getString(R.string.gallery_stopped_line, g.filesSaved)
        }
    }

    /** 지금 할 수 있는 일 — 고르는 것은 [GalleryRules.actions], 여기서는 글과 하는 일만 붙인다. */
    private fun setActions(item: DownloadCenter.Item, g: DownloadCenter.Gallery): List<Pair<String, () -> Unit>> =
        GalleryRules.actions(item.status, g.filesSaved, item.savedUri != null, item.pageUrl != null).map { action ->
            val pair: Pair<String, () -> Unit> = when (action) {
                GalleryRules.Action.PAUSE -> getString(R.string.download_pause) to { center.pause(item) }
                GalleryRules.Action.RESUME -> getString(R.string.download_resume) to { center.resume(item) }
                GalleryRules.Action.STOP -> getString(R.string.gallery_stop) to { center.cancel(item) }
                GalleryRules.Action.OPEN_FIRST -> getString(R.string.gallery_open_first) to { open(item) }
                GalleryRules.Action.OPEN_PAGE -> getString(R.string.download_open_page) to { openPage(item) }
                GalleryRules.Action.REMOVE -> getString(R.string.download_remove_from_list) to { center.remove(item) }
                GalleryRules.Action.DELETE_FILES -> getString(R.string.gallery_delete_files, g.filesSaved) to { confirmDeleteSetFiles(item, g) }
            }
            pair
        }

    /** 받은 파일까지 지운다 — 되돌릴 수 없으므로 한 번 더 묻는다. */
    private fun confirmDeleteSetFiles(item: DownloadCenter.Item, g: DownloadCenter.Gallery) {
        AlertDialog.Builder(this)
            .setTitle(g.title)
            .setMessage(getString(R.string.gallery_delete_files_question, g.filesSaved))
            .setPositiveButton(R.string.download_delete) { _, _ ->
                center.deleteSetFiles(item) { deleted ->
                    Toast.makeText(this, getString(R.string.gallery_files_deleted, deleted), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ── 토렌트 ──────────────────────────────────────────────────────────────────────────────────────────

    private val sizes = TorrentTexts.Sizes { Formatter.formatShortFileSize(this, it) }

    private fun torrentTitle(s: TorrentStatus): String = TorrentTexts.text(this, TorrentTexts.title(s))

    /** 누르면 — 파일을 골라야 하는 마그넷이면 고르는 창, 아니면 자세히와 할 수 있는 일. */
    private fun onTorrentTap(s: TorrentStatus) {
        if (torrents.needsChoice(s.id)) {
            chooseFiles(s)
            return
        }
        val lines = ArrayList<String>()
        lines += TorrentTexts.join(this, TorrentTexts.summary(s, torrents.extra(s), sizes))
        TorrentTexts.activeFile(s)?.let { lines += TorrentTexts.text(this, it) }
        for (parts in TorrentTexts.details(s, sizes)) lines += TorrentTexts.join(this, parts)
        val failed = torrents.failedMoves(s.id)
        for ((index, reason) in failed.entries.take(MAX_FAILED_LINES)) {
            lines += getString(R.string.torrent_move_failed_line, s.files.getOrNull(index)?.path ?: "#$index", reason)
        }
        if (failed.size > MAX_FAILED_LINES) lines += getString(R.string.download_more, failed.size - MAX_FAILED_LINES)
        lines += ""
        lines += getString(R.string.torrent_direct_note)
        TorrentDialogs.details(this, torrentTitle(s), lines, torrentActions(s))
    }

    /** 길게 누르기 — 할 수 있는 일의 메뉴. */
    private fun showTorrentMenu(s: TorrentStatus) {
        val actions = torrentActions(s)
        AlertDialog.Builder(this)
            .setTitle(torrentTitle(s))
            .setItems(actions.map { it.first }.toTypedArray()) { _, which -> actions[which].second() }
            .show()
    }

    /** 그 토렌트에 지금 할 수 있는 일 — 일시 정지·이어받기·파일 고르기·옮기기 다시 시도·지우기. */
    private fun torrentActions(s: TorrentStatus): List<Pair<String, () -> Unit>> = buildList {
        val needsChoice = torrents.needsChoice(s.id)
        when {
            needsChoice -> add(getString(R.string.torrent_choose_files) to { chooseFiles(s) })
            TorrentRules.isRunning(s.state) -> add(getString(R.string.download_pause) to { torrents.pause(s.id) })
            s.state == TorrentState.PAUSED || s.state == TorrentState.ERROR -> add(getString(R.string.download_resume) to { torrents.resume(s.id) })
            else -> Unit
        }
        if (!needsChoice && s.wantedBytes >= 0 && torrents.info(s.id) != null) add(getString(R.string.torrent_choose_files) to { chooseFiles(s) })
        if (torrents.failedMoves(s.id).isNotEmpty()) add(getString(R.string.torrent_retry_moves) to { torrents.retryMoves(s.id) })
        add(getString(R.string.torrent_remove_action) to { confirmRemoveTorrent(s) })
    }

    /**
     * 받을 파일 고르기. 마그넷이 파일 목록을 받아 기다리는 중이면 고른 뒤 받기 시작하고, 다 받은 토렌트에 받지 않은 파일을 더 고르면 이어받는다
     * (멈춘 것은 그대로 둔다 — 이어받기는 사용자가 누른다).
     */
    private fun chooseFiles(s: TorrentStatus) {
        val info = torrents.info(s.id)
        if (info == null) {
            Toast.makeText(this, R.string.torrent_not_ready, Toast.LENGTH_SHORT).show()
            return
        }
        val needsChoice = torrents.needsChoice(s.id)
        val initial = if (needsChoice) info.defaultSelection else s.files.filter { it.selected }.map { it.index }.toSet()
        TorrentDialogs.chooseFiles(
            this,
            torrentTitle(s),
            if (needsChoice) getString(R.string.torrent_choose_intro) else null,
            info.files,
            initial,
            s,
            if (needsChoice) R.string.download else R.string.torrent_choose_apply,
        ) { chosen ->
            val more = chosen.any { index -> s.files.getOrNull(index)?.let { it.state != "done" && !it.released } == true }
            torrents.selectFiles(s.id, chosen, resume = needsChoice || (s.state == TorrentState.COMPLETED && more))
        }
    }

    /** 지운다 — 앱 안에 받던 것은 늘, '내려받기' 로 옮긴 파일은 고르면. */
    private fun confirmRemoveTorrent(s: TorrentStatus) {
        val inApp = s.files.filter { !it.released }.sumOf { minOf(it.doneBytes, it.length) }
        TorrentDialogs.confirmRemove(this, torrentTitle(s), inApp, torrents.movedCount(s.id)) { alsoMoved ->
            torrents.remove(s.id, alsoMoved) { deleted ->
                if (alsoMoved) Toast.makeText(this, getString(R.string.torrent_removed_files, deleted), Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── 목록 ────────────────────────────────────────────────────────────────────────────────────────────

    private sealed class Row {
        class Header(val text: String) : Row()
        class Torrent(val status: TorrentStatus) : Row()
        class Download(val item: DownloadCenter.Item) : Row()
    }

    private inner class Adapter : BaseAdapter() {
        var rows: List<Row> = emptyList()

        override fun getCount() = rows.size
        override fun getItem(position: Int) = rows[position]
        override fun getItemId(position: Int) = when (val r = rows[position]) {
            is Row.Download -> r.item.id
            is Row.Torrent -> r.status.id.hashCode().toLong() shl 1 or 1L
            is Row.Header -> -position.toLong() - 1
        }

        override fun getViewTypeCount() = 2
        override fun getItemViewType(position: Int) = if (rows[position] is Row.Header) 0 else 1
        override fun isEnabled(position: Int) = rows[position] !is Row.Header

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val r = rows[position]
            if (r is Row.Header) {
                val header = (convertView as? TextView) ?: TextView(this@DownloadsActivity).apply {
                    textSize = 13f
                    setTextColor(Ui.themeColor(context, android.R.attr.colorAccent))
                    setPadding(Ui.dp(context, 16), Ui.dp(context, 16), Ui.dp(context, 16), Ui.dp(context, 4))
                    isFocusable = false
                    isAccessibilityHeading = true
                }
                set(header, r.text)
                return header
            }
            val row = (convertView as? LinearLayout) ?: LinearLayout(this@DownloadsActivity).apply {
                orientation = LinearLayout.VERTICAL
                val pad = Ui.dp(context, 16)
                setPadding(pad, Ui.dp(context, 12), pad, Ui.dp(context, 12))
                addView(TextView(context).apply { textSize = 16f; isSingleLine = true })
                addView(TextView(context).apply { textSize = 13f; alpha = 0.7f; maxLines = 2 })
                addView(TextView(context).apply { textSize = 12f; alpha = 0.6f; isSingleLine = true })
            }
            val title = row.getChildAt(0) as TextView
            val line = row.getChildAt(1) as TextView
            val third = row.getChildAt(2) as TextView
            when (r) {
                is Row.Download -> {
                    val g = r.item.gallery
                    if (g != null) {
                        // 갤러리 — 제목, 진행·상태, 셋째 줄에 저장 자리(받는 동안 0.4초마다 오지만 글이 바뀔 때만 고친다 — [set]).
                        set(title, g.title)
                        set(line, describeSet(r.item, g))
                        set(third, GalleryRules.relativePath(g.folder))
                        third.visibility = View.VISIBLE
                    } else {
                        set(title, r.item.savedName)
                        set(line, describe(r.item))
                        third.visibility = View.GONE
                    }
                }
                is Row.Torrent -> {
                    val s = r.status
                    set(title, torrentTitle(s))
                    set(line, TorrentTexts.join(this@DownloadsActivity, TorrentTexts.summary(s, torrents.extra(s), sizes)))
                    val active = TorrentTexts.activeFile(s)
                    if (active != null) {
                        set(third, TorrentTexts.text(this@DownloadsActivity, active))
                        third.visibility = View.VISIBLE
                    } else {
                        third.visibility = View.GONE
                    }
                }
                is Row.Header -> Unit
            }
            return row
        }

        /** 글이 바뀔 때만 고친다 — 토렌트는 1초마다 상태를 내므로, 같은 글을 다시 쓰면 접근성 이벤트가 끊이지 않는다(7단계의 '늘 바쁜 화면'). */
        private fun set(view: TextView, text: String) {
            if (view.text.toString() != text) view.text = text
        }
    }

    companion object {
        /** 알림의 '원래 페이지 열기'(이 화면은 내보내지 않았다 — 이 앱의 알림만 이 동작으로 연다). */
        const val ACTION_OPEN_PAGE = "io.github.donggi.iroirobrowser.action.OPEN_DOWNLOAD_PAGE"
        const val EXTRA_ID = "id"

        /** 알림의 '파일을 고르세요' — 마그넷의 파일 목록이 왔다. */
        const val ACTION_CHOOSE_TORRENT = "io.github.donggi.iroirobrowser.action.CHOOSE_TORRENT_FILES"
        const val EXTRA_TORRENT = "torrent"

        /** 자세히에 보이는 '옮기지 못함' 줄의 수. */
        private const val MAX_FAILED_LINES = 3
    }
}
