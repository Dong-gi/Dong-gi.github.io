package io.github.donggi.iroirobrowser.library

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.format.DateUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import io.github.donggi.iroirobrowser.R
import io.github.donggi.iroirobrowser.WebApp
import io.github.donggi.iroirobrowser.ui.Ui

/**
 * 북마크와 방문 기록 — 한 화면에 둘(위의 두 단추로 바꾼다). 누르면 브라우저가 지금 탭에서 연다 — 고른 주소는 **결과로만** 돌려준다(브라우저
 * 화면은 다른 앱이 열 수 있으니 인텐트의 덧붙임으로 주소를 받지 않는다). 길게 누르면 새 탭에서 열기·링크 복사·(북마크) 이름 바꾸기·지우기.
 */
class LibraryActivity : Activity() {

    private lateinit var app: WebApp
    private lateinit var bookmarksTab: Button
    private lateinit var historyTab: Button
    private lateinit var search: EditText
    private lateinit var clearAll: Button
    private lateinit var empty: TextView
    private lateinit var list: ListView
    private var mode = MODE_BOOKMARKS
    private var entries: List<Library.Entry> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = WebApp.of(this)
        mode = savedInstanceState?.getString(EXTRA_MODE) ?: intent.getStringExtra(EXTRA_MODE) ?: MODE_BOOKMARKS
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        Ui.edgeToEdge(this, root)
        root.addView(Ui.header(this, getString(R.string.library_title)) { finish() })
        val switcher = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(Ui.dp(context, 12), 0, Ui.dp(context, 12), 0)
        }
        bookmarksTab = modeButton(R.string.bookmarks) { setMode(MODE_BOOKMARKS) }
        historyTab = modeButton(R.string.history) { setMode(MODE_HISTORY) }
        switcher.addView(bookmarksTab, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        switcher.addView(historyTab, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(switcher, Ui.matchWidth())
        search = EditText(this).apply {
            hint = getString(R.string.library_search_hint)
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) = load()
            })
        }
        root.addView(
            LinearLayout(this).apply {
                setPadding(Ui.dp(context, 16), 0, Ui.dp(context, 16), 0)
                addView(search, Ui.matchWidth())
            },
            Ui.matchWidth(),
        )
        empty = TextView(this).apply {
            textSize = 14f
            alpha = 0.7f
            setPadding(Ui.dp(context, 20), Ui.dp(context, 16), Ui.dp(context, 20), 0)
            visibility = View.GONE
        }
        root.addView(empty, Ui.matchWidth())
        list = ListView(this).apply {
            adapter = Adapter()
            setOnItemClickListener { _, _, position, _ -> open(entries[position].url, newTab = false) }
            setOnItemLongClickListener { _, _, position, _ ->
                actions(entries[position])
                true
            }
        }
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        clearAll = Button(this, null, android.R.attr.borderlessButtonStyle).apply {
            text = getString(R.string.history_clear)
            setOnClickListener { confirmClearHistory() }
        }
        root.addView(clearAll, Ui.matchWidth())
        setContentView(root)
        setMode(mode)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(EXTRA_MODE, mode)
    }

    private fun modeButton(label: Int, onClick: () -> Unit): Button =
        Button(this, null, android.R.attr.borderlessButtonStyle).apply {
            text = getString(label)
            setOnClickListener { onClick() }
        }

    private fun setMode(next: String) {
        mode = next
        val bookmarks = mode == MODE_BOOKMARKS
        bookmarksTab.setTypeface(null, if (bookmarks) Typeface.BOLD else Typeface.NORMAL)
        historyTab.setTypeface(null, if (bookmarks) Typeface.NORMAL else Typeface.BOLD)
        bookmarksTab.alpha = if (bookmarks) 1f else 0.6f
        historyTab.alpha = if (bookmarks) 0.6f else 1f
        // 굵기·흐림만으로는 화면 낭독기가 어느 쪽인지 모른다 — '선택됨' 상태를 둔다.
        bookmarksTab.isSelected = bookmarks
        historyTab.isSelected = !bookmarks
        clearAll.visibility = if (bookmarks) View.GONE else View.VISIBLE
        load()
    }

    private fun load() {
        val q = search.text.toString()
        val wanted = mode
        val show = { found: List<Library.Entry> ->
            if (!isDestroyed && wanted == mode && q == search.text.toString()) {
                entries = found
                empty.text = getString(if (mode == MODE_BOOKMARKS) R.string.bookmarks_empty else R.string.history_empty)
                empty.visibility = if (found.isEmpty()) View.VISIBLE else View.GONE
                (list.adapter as BaseAdapter).notifyDataSetChanged()
            }
        }
        if (mode == MODE_BOOKMARKS) app.library.bookmarks(q, show) else app.library.history(q, done = show)
    }

    /** 고른 주소를 브라우저에 돌려주고 닫는다. */
    private fun open(url: String, newTab: Boolean) {
        setResult(RESULT_OK, Intent().putExtra(RESULT_URL, url).putExtra(RESULT_NEW_TAB, newTab))
        finish()
    }

    private fun actions(entry: Library.Entry) {
        val bookmarks = mode == MODE_BOOKMARKS
        val labels = buildList {
            add(getString(R.string.open_in_new_tab))
            add(getString(R.string.copy_link))
            if (bookmarks) add(getString(R.string.rename))
            add(getString(R.string.delete))
        }
        AlertDialog.Builder(this)
            .setTitle(entry.title.ifBlank { entry.url })
            .setItems(labels.toTypedArray()) { _, which ->
                when (labels[which]) {
                    getString(R.string.open_in_new_tab) -> open(entry.url, newTab = true)
                    getString(R.string.copy_link) -> copy(entry.url)
                    getString(R.string.rename) -> rename(entry)
                    getString(R.string.delete) ->
                        if (bookmarks) app.library.removeBookmark(entry.url) { load() } else app.library.deleteHistory(entry.url) { load() }
                }
            }
            .show()
    }

    private fun copy(url: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(url, url))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    private fun rename(entry: Library.Entry) {
        val input = EditText(this).apply {
            setText(entry.title)
            isSingleLine = true
            setSelectAllOnFocus(true)
        }
        val box = LinearLayout(this).apply {
            setPadding(Ui.dp(context, 20), Ui.dp(context, 8), Ui.dp(context, 20), 0)
            addView(input, Ui.matchWidth())
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.rename)
            .setView(box)
            .setPositiveButton(R.string.settings_save) { _, _ -> app.library.renameBookmark(entry.url, input.text.toString().trim()) { load() } }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmClearHistory() {
        AlertDialog.Builder(this)
            .setMessage(R.string.history_clear_question)
            .setPositiveButton(R.string.delete) { _, _ ->
                app.library.clearHistory {
                    Toast.makeText(this, R.string.history_cleared, Toast.LENGTH_SHORT).show()
                    load()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private inner class Adapter : BaseAdapter() {
        override fun getCount() = entries.size
        override fun getItem(position: Int): Any = entries[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val e = entries[position]
            val box = LinearLayout(this@LibraryActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = Ui.dp(context, 56)
                setPadding(Ui.dp(context, 20), Ui.dp(context, 8), Ui.dp(context, 20), Ui.dp(context, 8))
            }
            box.addView(TextView(this@LibraryActivity).apply {
                text = e.title.ifBlank { Uri.parse(e.url).host ?: e.url }
                textSize = 15f
                isSingleLine = true
            })
            box.addView(TextView(this@LibraryActivity).apply {
                text = if (mode == MODE_HISTORY) {
                    DateUtils.formatDateTime(
                        this@LibraryActivity,
                        e.time,
                        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
                    ) + " · " + e.url
                } else {
                    e.url
                }
                textSize = 12f
                alpha = 0.7f
                isSingleLine = true
            })
            return box
        }
    }

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_BOOKMARKS = "bookmarks"
        const val MODE_HISTORY = "history"
        const val RESULT_URL = "url"
        const val RESULT_NEW_TAB = "new_tab"
    }
}
