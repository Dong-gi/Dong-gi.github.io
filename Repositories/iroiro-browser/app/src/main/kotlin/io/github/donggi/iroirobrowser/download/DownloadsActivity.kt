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
import io.github.donggi.iroirobrowser.ui.Ui

/**
 * 내려받기 목록. 누르면: 다 받은 것은 **기본 앱으로** 열고, 받는 중이면 일시 정지·받기 취소를 묻고, 멈춘 것은 이어받기를 묻고(주소가
 * 만료됐으면 그 까닭과 '원래 페이지 열기'), 실패한 것은 다시 받을지 묻는다. **길게 누르면 메뉴** — 열기·공유·원래 페이지 열기·목록에서 빼기·
 * 파일까지 지우기 가운데 그 줄에 할 수 있는 것. 받은 것을 버리는 일(멈춘 것 지우기·받는 중 취소·파일 지우기)은 한 번 더 묻는다. 목록은 앱이
 * 다시 떠도 남는다.
 *
 * 받는 중의 단추는 '받기 취소(지움)'·'계속 받기' 다 — 이 앱의 다른 창에서 '취소' 는 창을 닫는 단추라, 받은 것을 지우는 단추와 같은 글이면 헷갈린다.
 *
 * **갤러리 받기**(pixiv·hitomi — [DownloadCenter.Kind.SET])는 보통 내려받기 사이에 한 줄 — 제목, 'n / m개'(작가 받기는 '작품 a / b · 파일 n')와
 * 상태·까닭, 셋째 줄에 저장 자리. 누르면 자세히와 할 수 있는 일([GalleryRules.actions] — 일시 정지·이어받기·그만 받기·첫 파일 열기·원래 페이지
 * 열기·목록에서 빼기·받은 파일까지 지우기), 길게 누르면 그 일들의 메뉴. '그만 받기' 는 받은 파일을 남기고, 받은 파일까지 지우는 일은 한 번 더 묻는다.
 */
class DownloadsActivity : Activity() {

    private lateinit var center: DownloadCenter
    private lateinit var adapter: Adapter
    private lateinit var empty: TextView
    private val listener = DownloadCenter.Listener { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.downloads)
        center = WebApp.of(this).downloads
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        Ui.edgeToEdge(this, root)
        empty = TextView(this).apply {
            text = getString(R.string.downloads_empty)
            setPadding(Ui.dp(context, 16), Ui.dp(context, 24), Ui.dp(context, 16), 0)
        }
        val list = ListView(this)
        adapter = Adapter()
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ -> onTap(adapter.getItem(position)) }
        list.setOnItemLongClickListener { _, _, position, _ ->
            showMenu(adapter.getItem(position))
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
        refresh()
    }

    override fun onStop() {
        center.removeListener(listener)
        super.onStop()
    }

    /** 알림의 '원래 페이지 열기' — 그 항목의 페이지를 브라우저에서 연다. 이 화면은 내보내지 않았다 — 이 앱의 알림만 이것을 연다. */
    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_OPEN_PAGE) center.find(intent.getLongExtra(EXTRA_ID, -1))?.let { openPage(it) }
    }

    private fun refresh() {
        adapter.rows = center.items()
        adapter.notifyDataSetChanged()
        empty.visibility = if (adapter.rows.isEmpty()) View.VISIBLE else View.GONE
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
                    .setMessage(failureMessage(item))
                    .setNegativeButton(R.string.close, null)
                if (item.pausable) {
                    b.setPositiveButton(R.string.download_retry) { _, _ -> center.retry(item) }
                }
                b.show()
            }
        }
    }

    /**
     * 실패·취소한 것의 글 — 잠긴 HLS 의 열쇠를 받지 못한 것이면(글이 그 머리로 시작한다) '재생 중인 것 받기' 를 권하는 한 줄을 붙인다(가로채기는
     * 재생기가 푼 뒤의 바이트를 담으므로 열쇠가 필요 없다).
     */
    private fun failureMessage(item: DownloadCenter.Item): String {
        val error = item.error ?: return getString(R.string.download_cancelled)
        if (!error.startsWith(getString(R.string.download_key_invalid))) return error
        return buildString {
            append(error)
            append(Char(10)).append(Char(10))
            append(getString(R.string.download_key_hint))
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
        if (item.status != DownloadCenter.Status.DONE) GalleryRules.note(g.skipped, g.skippedLogin)?.let { lines += TextParts.text(this, it) }
        DetailsDialog.show(this, g.title, lines, setActions(item, g))
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

    // ── 목록 ────────────────────────────────────────────────────────────────────────────────────────────

    private inner class Adapter : BaseAdapter() {
        var rows: List<DownloadCenter.Item> = emptyList()

        override fun getCount() = rows.size
        override fun getItem(position: Int) = rows[position]
        override fun getItemId(position: Int) = rows[position].id

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val item = rows[position]
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
            val g = item.gallery
            if (g != null) {
                // 갤러리 — 제목, 진행·상태, 셋째 줄에 저장 자리(받는 동안 0.4초마다 오지만 글이 바뀔 때만 고친다 — [set]).
                set(title, g.title)
                set(line, describeSet(item, g))
                set(third, GalleryRules.relativePath(g.folder))
                third.visibility = View.VISIBLE
            } else {
                set(title, item.savedName)
                set(line, describe(item))
                third.visibility = View.GONE
            }
            return row
        }

        /** 글이 바뀔 때만 고친다 — 받는 동안 상태가 잦게 오므로, 같은 글을 다시 쓰면 접근성 이벤트가 끊이지 않는다(CLAUDE.md 함정 '늘 바쁜 화면'). */
        private fun set(view: TextView, text: String) {
            if (view.text.toString() != text) view.text = text
        }
    }

    companion object {
        /** 알림의 '원래 페이지 열기'(이 화면은 내보내지 않았다 — 이 앱의 알림만 이 동작으로 연다). */
        const val ACTION_OPEN_PAGE = "io.github.donggi.iroirobrowser.action.OPEN_DOWNLOAD_PAGE"
        const val EXTRA_ID = "id"
    }
}
