package io.github.donggi.iroirobrowser.browser

import android.app.Activity
import android.net.Uri
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.donggi.iroirobrowser.R
import io.github.donggi.iroirobrowser.library.Library
import io.github.donggi.iroirobrowser.ui.Ui

/**
 * 새 탭의 시작 화면 — 북마크와 자주 간 곳. 쪽(WebView)이 아니라 화면의 뷰다(앱 안의 파일을 쪽으로 열지 않는다 — 파일 접근은 꺼 두었다).
 * 탭에 주소가 없을 때만 보인다. 누르면 그 탭에서 연다.
 */
class StartPage(private val activity: Activity, private val library: Library, private val onOpen: (String) -> Unit) {

    val view: ScrollView = ScrollView(activity)
    private val column = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, Ui.dp(activity, 8), 0, Ui.dp(activity, 24))
    }

    init {
        view.setBackgroundColor(Ui.themeColor(activity, android.R.attr.colorBackground))
        view.addView(column)
    }

    /** 북마크·자주 간 곳을 다시 읽어 그린다. */
    fun refresh() {
        library.bookmarks("") { bookmarks ->
            library.frequent(FREQUENT) { frequent ->
                if (!activity.isDestroyed) render(bookmarks.take(MAX_BOOKMARKS), frequent)
            }
        }
    }

    private fun render(bookmarks: List<Library.Entry>, frequent: List<Library.Entry>) {
        column.removeAllViews()
        if (bookmarks.isEmpty() && frequent.isEmpty()) {
            column.addView(text(activity.getString(R.string.start_empty), 14f, 0.7f, top = 24))
            return
        }
        if (bookmarks.isNotEmpty()) {
            column.addView(section(R.string.bookmarks))
            bookmarks.forEach { column.addView(row(it)) }
        }
        if (frequent.isNotEmpty()) {
            column.addView(section(R.string.start_frequent))
            frequent.forEach { column.addView(row(it)) }
        }
    }

    private fun section(label: Int): View = text(activity.getString(label), 14f, 1f, top = 16).apply {
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setTextColor(Ui.themeColor(activity, android.R.attr.colorAccent))
    }

    private fun row(e: Library.Entry): View = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        minimumHeight = Ui.dp(activity, 52)
        setPadding(Ui.dp(activity, 20), Ui.dp(activity, 6), Ui.dp(activity, 20), Ui.dp(activity, 6))
        setBackgroundResource(TypedValue().also { activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true) }.resourceId)
        isClickable = true
        setOnClickListener { onOpen(e.url) }
        val host = Uri.parse(e.url).host ?: e.url
        addView(TextView(activity).apply {
            text = e.title.ifBlank { host }
            textSize = 15f
            isSingleLine = true
        })
        addView(TextView(activity).apply {
            text = host
            textSize = 12f
            alpha = 0.7f
            isSingleLine = true
        })
    }

    private fun text(value: String, size: Float, alpha: Float, top: Int): TextView = TextView(activity).apply {
        text = value
        textSize = size
        this.alpha = alpha
        setPadding(Ui.dp(activity, 20), Ui.dp(activity, top), Ui.dp(activity, 20), Ui.dp(activity, 4))
    }

    private companion object {
        const val MAX_BOOKMARKS = 30
        const val FREQUENT = 8
    }
}
