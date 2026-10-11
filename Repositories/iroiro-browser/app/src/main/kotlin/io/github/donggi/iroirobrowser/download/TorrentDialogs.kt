package io.github.donggi.iroirobrowser.download

import android.app.Activity
import android.app.AlertDialog
import android.text.TextUtils
import android.text.format.Formatter
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import io.github.donggi.iroirobrowser.R
import io.github.donggi.iroirobrowser.WebApp
import io.github.donggi.iroirobrowser.torrent.TorrentError
import io.github.donggi.iroirobrowser.torrent.TorrentStatus
import io.github.donggi.iroirobrowser.torrent.meta.TorrentFile
import io.github.donggi.iroirobrowser.ui.Ui

/**
 * 토렌트의 창들 — 처음 쓸 때의 안내, 받을 파일 고르기, 받지 않는 까닭, 지우기 확인, 자세히. 브라우저 화면(묻기 — [TorrentPrompt])과 목록 화면
 * ([DownloadsActivity])이 함께 쓴다. 플랫폼 뷰로 짓는다(새 의존 없이). 메인 스레드.
 */
internal object TorrentDialogs {

    /**
     * 처음 쓸 때의 안내(사용자 결정 ㉓ — '프록시를 거치지 않고 이 기기의 IP 로 받습니다 · 저작권 책임') — 한 번만. 이미 보였으면 곧바로 [then].
     * 닫으면(취소) 아무것도 하지 않는다 — 받기로 가지 않는다.
     */
    fun notice(activity: Activity, then: () -> Unit) {
        val settings = WebApp.of(activity).settings
        if (settings.torrentNoticeSeen) {
            then()
            return
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.torrent_notice_title)
            .setMessage(R.string.torrent_notice_message)
            .setPositiveButton(R.string.torrent_notice_ok) { _, _ ->
                settings.torrentNoticeSeen = true
                then()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * 받지 않는 토렌트 — 까닭(순수 v2·private·깨짐·너무 큼…)을 말하고 받지 않는다. [alternative] 가 있으면 그 단추를 하나 더(파일로 저장·다른 앱으로).
     * 엔진의 자세한 기계 말(`detail`)은 보이지 않는다 — 우리 문장만.
     */
    fun refusal(activity: Activity, title: String?, error: TorrentError, alternative: Pair<Int, () -> Unit>? = null) {
        val b = AlertDialog.Builder(activity)
            .setTitle(title ?: activity.getString(R.string.torrent_refused_title))
            .setMessage(activity.getString(TorrentTexts.errorRes(error)))
            .setNegativeButton(R.string.close, null)
        alternative?.let { (label, action) -> b.setPositiveButton(label) { _, _ -> action() } }
        b.show()
    }

    /**
     * 받을 파일 고르기. [files] 는 메타데이터의 파일들(엔진이 정리한 경로), [initial] 은 처음에 고른 것, [current] 가 있으면(이미 넣은 토렌트)
     * 파일마다 진행·상태를 함께 보인다. 패드 파일은 감추고, 심볼릭 링크는 고를 수 없게(만들지 않는다), '내려받기' 로 옮겨 간 파일은 고른 채 바꿀
     * 수 없게 둔다(빼도 이미 옮긴 파일은 그대로고, 다시 고르면 엔진이 받은 조각을 버린 뒤 또 받는다). 하나도 고르지 않으면 [positive] 를 누를 수
     * 없다. 고른 것을 [done] 으로 — 줄이 수만 개여도 목록 뷰가 보이는 줄만 짓는다.
     */
    fun chooseFiles(
        activity: Activity,
        title: String,
        intro: String?,
        files: List<TorrentFile>,
        initial: Set<Int>,
        current: TorrentStatus?,
        positive: Int,
        done: (Set<Int>) -> Unit,
    ): AlertDialog {
        val entries = files.filter { !it.isPad }
        val statusOf = current?.files?.associateBy { it.index }.orEmpty()
        val fixed = BooleanArray(entries.size) { i -> entries[i].isSymlink || statusOf[entries[i].index]?.released == true }
        val checked = BooleanArray(entries.size) { i ->
            val f = entries[i]
            if (f.isSymlink) false else statusOf[f.index]?.released == true || f.index in initial
        }
        val size = { n: Long -> Formatter.formatShortFileSize(activity, n) }
        val pad = Ui.dp(activity, 20)
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, Ui.dp(activity, 8), pad, 0)
        }
        if (intro != null) {
            column.addView(TextView(activity).apply {
                text = intro
                textSize = 14f
                setPadding(0, 0, 0, Ui.dp(activity, 8))
            })
        }
        val summary = TextView(activity).apply {
            textSize = 13f
            alpha = 0.8f
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        val toggle = Button(activity, null, android.R.attr.borderlessButtonStyle)
        val bar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(summary, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(toggle)
        }
        column.addView(bar)
        val list = ListView(activity)
        val rowHeight = Ui.dp(activity, 60)
        val maxHeight = (activity.resources.displayMetrics.heightPixels * 0.45f).toInt()
        column.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, minOf(maxHeight, entries.size * rowHeight + Ui.dp(activity, 8))))

        var dialog: AlertDialog? = null
        val adapter = object : BaseAdapter() {
            override fun getCount() = entries.size
            override fun getItem(position: Int) = entries[position]
            override fun getItemId(position: Int) = entries[position].index.toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = (convertView as? LinearLayout) ?: LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = Ui.dp(activity, 52)
                    setPadding(0, Ui.dp(activity, 4), 0, Ui.dp(activity, 4))
                    addView(CheckBox(activity).apply {
                        // 줄 전체가 눌린다(목록의 누름) — 칸이 따로 누름을 먹으면 줄을 눌러도 바뀌지 않는다.
                        isClickable = false
                        isFocusable = false
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    })
                    addView(LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(Ui.dp(activity, 8), 0, 0, 0)
                        addView(TextView(activity).apply {
                            textSize = 14f
                            maxLines = 2
                            ellipsize = TextUtils.TruncateAt.MIDDLE
                        })
                        addView(TextView(activity).apply {
                            textSize = 12f
                            alpha = 0.7f
                            isSingleLine = true
                        })
                    }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                }
                val f = entries[position]
                val st = statusOf[f.index]
                val detail = buildList {
                    add(size(f.length))
                    when {
                        f.isSymlink -> add(activity.getString(R.string.torrent_file_link))
                        f.isPadLike -> add(activity.getString(R.string.torrent_file_padding))
                    }
                    if (st != null) {
                        // 엔진의 '고르지 않음' 은 적지 않는다 — 고를지는 칸이 말한다. 마그넷은 묻는 동안 엔진의 고르기를 비워 두므로, 적으면 칸마다
                        // 체크가 된 채 '고르지 않음' 이 붙었다(2026-10-10 기기에서 봤다).
                        if (st.released || st.state != "skipped") add(activity.getString(TorrentTexts.fileStateRes(st)))
                        if (!st.released && st.state != "done" && st.doneBytes > 0) add(activity.getString(R.string.torrent_percent, TorrentTexts.filePercent(st)))
                    }
                }.joinToString(" · ")
                (row.getChildAt(0) as CheckBox).apply {
                    isChecked = checked[position]
                    isEnabled = !fixed[position]
                }
                val texts = row.getChildAt(1) as LinearLayout
                (texts.getChildAt(0) as TextView).text = f.displayPath
                (texts.getChildAt(1) as TextView).text = detail
                row.alpha = if (fixed[position]) 0.6f else 1f
                row.contentDescription = activity.getString(
                    if (checked[position]) R.string.torrent_file_row_checked else R.string.torrent_file_row_unchecked,
                    f.displayPath,
                    detail,
                )
                return row
            }

            override fun isEnabled(position: Int): Boolean = !fixed[position]
        }

        fun chosen(): Set<Int> = entries.indices.filter { checked[it] }.map { entries[it].index }.toSet()

        fun render() {
            val picked = entries.indices.filter { checked[it] }
            summary.text = activity.getString(R.string.torrent_chosen, picked.size, size(picked.sumOf { entries[it].length }))
            val allOn = entries.indices.all { checked[it] || fixed[it] }
            toggle.text = activity.getString(if (allOn) R.string.torrent_choose_none else R.string.torrent_choose_all)
            dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = picked.isNotEmpty()
            adapter.notifyDataSetChanged()
        }

        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ ->
            if (!fixed[position]) {
                checked[position] = !checked[position]
                render()
            }
        }
        toggle.setOnClickListener {
            val allOn = entries.indices.all { checked[it] || fixed[it] }
            for (i in entries.indices) if (!fixed[i]) checked[i] = !allOn && !entries[i].isSymlink
            render()
        }
        val d = AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(column)
            .setPositiveButton(positive) { _, _ -> done(chosen()) }
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog = d
        d.show()
        render()
        return d
    }

    /**
     * 토렌트를 지울지 — 앱 안에 받던 것은 늘 지운다(앱의 사적 저장소라 꺼낼 수 없다). '내려받기' 로 옮긴 파일([moved] 개)도 지울지는 고르게
     * 한다(기본은 남긴다 — 다 받은 것은 이미 사용자의 파일이다). [done] 은 옮긴 파일도 지울지.
     */
    fun confirmRemove(activity: Activity, title: String, received: Long, moved: Int, done: (Boolean) -> Unit) {
        val pad = Ui.dp(activity, 20)
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, Ui.dp(activity, 12), pad, 0)
            addView(TextView(activity).apply {
                text = activity.getString(R.string.torrent_remove_message, Formatter.formatShortFileSize(activity, received))
                textSize = 15f
            })
        }
        val alsoMoved = CheckBox(activity).apply {
            text = activity.getString(R.string.torrent_remove_moved, moved)
            visibility = if (moved > 0) View.VISIBLE else View.GONE
        }
        column.addView(alsoMoved)
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(column)
            .setPositiveButton(R.string.download_delete) { _, _ -> done(alsoMoved.isChecked) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** 자세히 — [lines] 를 위에, 그 아래 할 수 있는 일([actions] — 누르면 창을 닫고 한다)과 '닫기'. */
    fun details(activity: Activity, title: String, lines: List<String>, actions: List<Pair<String, () -> Unit>>) {
        val pad = Ui.dp(activity, 20)
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, Ui.dp(activity, 8), pad, 0)
            addView(TextView(activity).apply {
                text = lines.joinToString("\n")
                textSize = 14f
                setTextIsSelectable(true)
                setPadding(0, 0, 0, Ui.dp(activity, 8))
            })
        }
        var dialog: AlertDialog? = null
        for ((label, action) in actions) {
            column.addView(Button(activity, null, android.R.attr.borderlessButtonStyle).apply {
                text = label
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                isAllCaps = false
                setOnClickListener {
                    dialog?.dismiss()
                    action()
                }
            }, Ui.matchWidth())
        }
        val scroll = ScrollView(activity).apply { addView(column) }
        val d = AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(scroll)
            .setNegativeButton(R.string.close, null)
            .create()
        dialog = d
        d.show()
    }
}
