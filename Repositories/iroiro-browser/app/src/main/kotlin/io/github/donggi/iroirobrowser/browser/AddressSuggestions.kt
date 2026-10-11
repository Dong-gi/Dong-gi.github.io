package io.github.donggi.iroirobrowser.browser

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Selection
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.donggi.iroirobrowser.R
import io.github.donggi.iroirobrowser.core.Suggester
import io.github.donggi.iroirobrowser.library.Library
import io.github.donggi.iroirobrowser.ui.Ui
import java.util.concurrent.Executors

/**
 * 주소창의 제안(사용자 요청, 2026-10-05: '주소표시줄 입력 시 방문 이력으로부터 자동 완성'). **방문 기록·북마크·열린 탭에서만** 고른다
 * ([Suggester] — 친 글을 밖에 묻지 않는다). 치는 동안 주소창 아래(쪽 위)에 목록을 덮고, 친 글이 아는 주소의 앞머리면 그 뒤를 **골라 둔 채**
 * 채운다(인라인 완성). 꼴은 크롬의 주소창을 따랐다(조사 — CLAUDE.md '주소창과 제안').
 *
 * - **목록의 차례**: 맨 위가 Enter 가 갈 곳이다 — 인라인 완성이 있으면 그 주소, 없으면 '입력한 그대로'(주소면 이동, 아니면 검색 — 크롬의
 *   what-you-typed 줄). 그다음 친 글에 맞는 **열린 탭**(눌러 그 탭으로 — 둘까지), 그다음 기록·북마크([Suggester.DEFAULT_LIMIT] 줄까지).
 *   빈 글로 초점을 얻으면 자주·최근 간 곳(크롬의 '자주 간 곳' 자리).
 * - 인라인 완성은 **지우기 한 번이 채운 것만 지우고**, 이어 치는 글자가 채운 글자와 같으면 다시 채워져 이어진다. 친 글이 늘지 않았으면(지우기)
 *   채우지 않는다. **조합 중인 글(한글 자판의 조합)이 있으면 채우지 않는다** — 조합 영역 뒤에 글을 붙이면 자판이 조합을 잃는다. 크롬·파이어폭스는
 *   채운 글을 진짜 선택이 아니라 배경색과 숨긴 커서로 그리고 자판과의 주고받기를 감싸는데, 여기서는 진짜 선택으로 그린다(ASCII 에서만 채우므로
 *   조합과 만나지 않는다 — 실기기의 자판에서는 확인하지 않았다).
 * - 기록 줄의 오른쪽 화살은 그 주소를 주소창에 채워 고쳐 쓰게 한다. 기록 줄을 길게 누르면 기록에서 지운다(묻고).
 * - 계산은 일꾼 스레드에서 한다(기록 1만 줄이면 폰에서 수십 ms). 답이 왔을 때 친 글이 그사이 바뀌었으면 버린다.
 */
class AddressSuggestions(
    private val activity: Activity,
    private val address: EditText,
    private val library: Library,
    private val host: Host,
) {
    /** 브라우저 화면이 주는 것 — 열린 탭, 친 글의 뜻, 고른 줄을 여는 일. 모두 메인 스레드에서 불린다. */
    interface Host {
        /** 지금 탭을 뺀 열린 탭(주소가 있는 것). */
        fun openTabs(): List<OpenTab>

        /** 친 글을 Enter 로 열면 무엇이 되는가 — 주소면 그 주소, 아니면 검색(엔진 이름). 빈 글이면 null. */
        fun describe(text: String): Typed?

        /** 기록·북마크 줄 — 그 주소로 간다(주소창에서 친 것으로 센다). */
        fun open(url: String)

        /** '입력한 그대로' 줄 — Enter 와 같다. */
        fun go(text: String)

        /** '탭으로 전환' 줄. */
        fun switchTab(id: Long)

        /**
         * 지금 탭이 새 탭의 시작 화면(북마크·자주 간 곳)을 보이는 중 — 그때는 빈 글로 초점을 얻어도 자주 간 곳을 덮지 않는다(시작 화면이
         * 이미 보인다 — 덮으면 북마크가 가려졌다). 치기 시작하면 목록이 뜬다.
         */
        fun showsStartPage(): Boolean

        /** 목록 아래의 빈 곳을 눌렀다 — 목록을 걷고 주소창의 고치기를 끝낸다. */
        fun dismiss()
    }

    class OpenTab(val id: Long, val url: String, val title: String)

    /** 친 글의 뜻 — [url] 이면 [detail] 은 열 주소, 아니면 [detail] 은 검색 엔진의 이름. */
    class Typed(val url: Boolean, val detail: String)

    private enum class Kind { GO, SEARCH, TAB, HISTORY, BOOKMARK }

    private class Row(val kind: Kind, val title: String, val subtitle: String, val url: String, val tabId: Long = -1)

    /**
     * 쪽 위에 덮는 목록. 부르는 쪽이 쪽의 자리(막대 아래)에 붙인다. 제안이 없으면 숨는다. 줄 아래의 빈 곳까지 채워(`fillViewport`) 그곳을
     * 누르면 목록을 걷는다 — 앞 판은 빈 곳을 눌러도 아무 일이 없었다.
     */
    val view: ScrollView = ScrollView(activity).apply {
        visibility = View.GONE
        isFillViewport = true
        setBackgroundColor(Ui.themeColor(activity, android.R.attr.colorBackground))
    }
    private val column = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "web-suggest").apply { isDaemon = true } }
    private var suggester: Suggester? = null
    private var loading = false
    private var generation = 0

    /** 우리가 주소창의 글을 바꾸는 중(인라인 완성) — 그 바뀜은 친 것이 아니다. */
    private var selfChange = false

    /** 마지막으로 바뀐 것이 지우기였다(친 글이 늘지 않았다) — 그때는 채우지 않는다. 채운 것을 지우기 한 번으로 걷어 낸 뒤 다시 채우지 않게. */
    private var deleting = false

    /** 마지막으로 사용자가 친 글(인라인 완성은 뺀 것) — 다음 바뀜이 지우기인지 가린다. */
    private var lastTyped = ""

    /** 지금 주소창에 채워 둔 인라인 완성(없으면 null). */
    private var inline: Suggester.Inline? = null

    private val watcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

        override fun afterTextChanged(s: Editable) {
            if (selfChange || !address.hasFocus()) return
            val text = s.toString()
            // 채워 고른 글을 지우기 한 번으로 걷으면 친 글만 남는다(길이가 같다) — 그것도 지우기다. 글자를 치면 고른 글이 그 글자로 바뀌어 늘어난다.
            deleting = text.length <= lastTyped.length
            lastTyped = text
            inline = null
            query(text)
        }
    }

    init {
        view.addView(column, Ui.matchWidth())
        address.addTextChangedListener(watcher)
    }

    /** 주소창의 초점이 바뀌었다. 얻으면 기록을 (다시) 읽어 두고, 잃으면 목록을 걷는다. */
    fun onFocusChanged(focused: Boolean) {
        if (!focused) {
            hide()
            return
        }
        deleting = false
        lastTyped = ""
        inline = null
        reload()
    }

    /**
     * Enter 를 눌렀다 — 갈 주소. 인라인 완성이 그대로 주소창에 있으면 그것이 가리키는 곳(예: `naver.com` 으로 채웠으면 기록의
     * `https://www.naver.com/`), 아니면 null(부르는 쪽이 친 글을 주소·검색어로 가른다).
     */
    fun destinationFor(text: String): String? = inline?.takeIf { it.text == text }?.url

    /** 목록을 걷는다(초점을 잃거나 갔다). */
    fun hide() {
        generation++
        column.removeAllViews()
        view.visibility = View.GONE
    }

    fun close() {
        hide()
        worker.shutdownNow()
    }

    /** 기록이 바뀌었을 수 있다 — 지은 것을 새로 받아 지금 글로 다시 묻는다. */
    private fun reload() {
        if (loading) return
        loading = true
        library.suggester { s ->
            loading = false
            suggester = s
            if (address.hasFocus()) query(currentTyped())
        }
    }

    /** 사용자가 친 글 — 인라인 완성으로 채워 고른 부분은 뺀다. 초점을 얻어 주소 전체가 골라진 채면 빈 글이다(친 것이 아니다). */
    private fun currentTyped(): String {
        val text = address.text
        val start = Selection.getSelectionStart(text)
        val end = Selection.getSelectionEnd(text)
        if (start == 0 && end == text.length) return ""
        return if (inline != null && end == text.length && start in 0 until end) text.substring(0, start) else text.toString()
    }

    private fun query(text: String) {
        val s = suggester
        val gen = ++generation
        if (s == null || (text.isBlank() && host.showsStartPage())) {
            render(emptyList())
            return
        }
        // 열린 탭과 친 글의 뜻은 메인 스레드에서 떠 간다(탭 목록은 메인 스레드의 것이다).
        val tabs = if (text.isBlank()) emptyList() else host.openTabs()
        val typed = if (text.isBlank()) null else host.describe(text)
        val now = System.currentTimeMillis()
        worker.execute {
            val rows = ArrayList<Row>()
            var completion: Suggester.Inline? = null
            if (text.isBlank()) {
                s.top(now).forEach { rows += historyRow(it) }
            } else {
                val result = s.query(text, now)
                completion = result.inline
                // 맨 위는 Enter 가 갈 곳 — 인라인 완성이 있으면 그 줄(기록의 첫 줄이다), 없으면 '입력한 그대로'.
                var history = result.rows
                if (completion != null && history.isNotEmpty()) {
                    rows += historyRow(history.first())
                    history = history.drop(1)
                } else if (typed != null) {
                    rows += if (typed.url) Row(Kind.GO, text.trim(), typed.detail, typed.detail) else Row(Kind.SEARCH, text.trim(), typed.detail, "")
                }
                val tabRows = tabMatches(tabs, text, now)
                rows += tabRows
                val shown = tabRows.mapTo(HashSet()) { Suggester.sameKey(it.url) }
                rows.firstOrNull()?.takeIf { it.kind == Kind.HISTORY || it.kind == Kind.BOOKMARK }?.let { shown += Suggester.sameKey(it.url) }
                history.filter { Suggester.sameKey(it.url) !in shown }.forEach { rows += historyRow(it) }
            }
            main.post {
                // 그사이 더 쳤으면 버린다 — 견주는 것은 친 글이다(채워 둔 것은 뺀다 — 기록을 다시 읽어 묻는 길은 채워 둔 채로 묻는다).
                if (gen != generation || !address.hasFocus() || currentTyped() != text) return@post
                render(rows)
                // 앞서 채워 둔 것이 있으면(지운 기록을 다시 읽었다) 걷고 새 답으로 채운다.
                clearInline(text)
                completion?.let { applyInline(text, it) }
            }
        }
    }

    /** 친 글에 맞는 열린 탭 — 기록과 같은 맞추기로, 둘까지. */
    private fun tabMatches(tabs: List<OpenTab>, text: String, now: Long): List<Row> {
        if (tabs.isEmpty()) return emptyList()
        val byUrl = tabs.associateBy { it.url }
        val entries = tabs.map { Suggester.Entry(it.url, it.title, 1, 0, now, false) }
        return Suggester(entries).suggest(text, now, MAX_TAB_ROWS, top = null).mapNotNull { s ->
            val tab = byUrl[s.url] ?: return@mapNotNull null
            Row(Kind.TAB, s.title.ifBlank { s.display }, activity.getString(R.string.suggest_switch_tab, s.display), s.url, tab.id)
        }
    }

    private fun historyRow(s: Suggester.Suggestion): Row =
        Row(if (s.bookmarked) Kind.BOOKMARK else Kind.HISTORY, s.title.ifBlank { s.display }, if (s.title.isBlank()) "" else s.display, s.url)

    /** 채워 둔 인라인 완성을 걷는다 — 주소창이 아직 친 글 + 채운 글(골라 둔 채)일 때만. */
    private fun clearInline(typed: String) {
        val shown = inline ?: return
        inline = null
        val text = address.text
        if (text.toString() != shown.text || Selection.getSelectionStart(text) != typed.length || Selection.getSelectionEnd(text) != text.length) return
        selfChange = true
        try {
            text.delete(typed.length, text.length)
        } finally {
            selfChange = false
        }
    }

    /** 친 글([typed]) 뒤에 인라인 완성을 골라 둔 채 붙인다 — 지우는 중·조합 중·커서가 끝에 없으면 하지 않는다. */
    private fun applyInline(typed: String, completion: Suggester.Inline) {
        val text = address.text
        if (deleting || completion.text.length <= typed.length || !completion.text.startsWith(typed)) return
        if (Selection.getSelectionStart(text) != typed.length || Selection.getSelectionEnd(text) != typed.length) return
        if (BaseInputConnection.getComposingSpanStart(text) >= 0) return
        selfChange = true
        try {
            text.append(completion.text.substring(typed.length))
            address.setSelection(typed.length, text.length)
        } finally {
            selfChange = false
        }
        inline = completion
    }

    private fun render(rows: List<Row>) {
        column.removeAllViews()
        if (rows.isEmpty()) {
            view.visibility = View.GONE
            return
        }
        rows.forEach { column.addView(rowView(it)) }
        // 줄 아래의 빈 곳 — 누르면 목록을 걷는다(크롬처럼).
        column.addView(View(activity).apply {
            contentDescription = activity.getString(R.string.suggest_dismiss)
            setOnClickListener { host.dismiss() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        view.scrollTo(0, 0)
        view.visibility = View.VISIBLE
    }

    private fun rowView(r: Row): View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = Ui.dp(activity, 56)
        setPadding(Ui.dp(activity, 16), Ui.dp(activity, 4), Ui.dp(activity, 4), Ui.dp(activity, 4))
        setBackgroundResource(TypedValue().also { activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true) }.resourceId)
        isClickable = true
        setOnClickListener {
            when (r.kind) {
                Kind.GO, Kind.SEARCH -> host.go(r.title)
                Kind.TAB -> host.switchTab(r.tabId)
                Kind.HISTORY, Kind.BOOKMARK -> host.open(r.url)
            }
        }
        if (r.kind == Kind.HISTORY || r.kind == Kind.BOOKMARK) {
            setOnLongClickListener {
                confirmDelete(r)
                true
            }
        }
        val tint = ColorStateList.valueOf(Ui.themeColor(activity, android.R.attr.textColorSecondary))
        val (icon, label) = when (r.kind) {
            Kind.GO -> R.drawable.ic_go to R.string.suggest_go
            Kind.SEARCH -> R.drawable.ic_search to R.string.suggest_search_row
            Kind.TAB -> R.drawable.ic_tab to R.string.suggest_tab
            Kind.HISTORY -> R.drawable.ic_history to R.string.suggest_history
            Kind.BOOKMARK -> R.drawable.ic_star to R.string.suggest_bookmark
        }
        addView(ImageView(activity).apply {
            setImageResource(icon)
            imageTintList = tint
            contentDescription = activity.getString(label)
        }, LinearLayout.LayoutParams(Ui.dp(activity, 20), Ui.dp(activity, 20)).apply { marginEnd = Ui.dp(activity, 16) })
        val texts = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(activity).apply {
            text = r.title
            textSize = 15f
            isSingleLine = true
        })
        val subtitle = if (r.kind == Kind.SEARCH) activity.getString(R.string.suggest_search, r.subtitle) else r.subtitle
        if (subtitle.isNotBlank()) {
            texts.addView(TextView(activity).apply {
                text = subtitle
                textSize = 12f
                alpha = 0.7f
                isSingleLine = true
            })
        }
        addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (r.kind == Kind.HISTORY || r.kind == Kind.BOOKMARK) {
            addView(ImageButton(activity).apply {
                setImageResource(R.drawable.ic_fill)
                imageTintList = tint
                contentDescription = activity.getString(R.string.suggest_fill)
                setBackgroundResource(TypedValue().also { activity.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, it, true) }.resourceId)
                setOnClickListener { fill(r.url) }
            }, LinearLayout.LayoutParams(Ui.dp(activity, 48), Ui.dp(activity, 48)))
        } else {
            // 화살이 없는 줄도 글의 오른쪽 끝을 맞춘다.
            addView(View(activity), LinearLayout.LayoutParams(Ui.dp(activity, 48), Ui.dp(activity, 48)))
        }
    }

    /** 화살 — 그 주소를 주소창에 채워 고쳐 쓰게(커서는 끝에). */
    private fun fill(url: String) {
        inline = null
        selfChange = true
        try {
            address.setText(url)
            address.setSelection(address.text.length)
        } finally {
            selfChange = false
        }
        lastTyped = url
        deleting = true // 채운 주소 뒤에 또 채우지 않는다
        query(url)
    }

    private fun confirmDelete(r: Row) {
        AlertDialog.Builder(activity)
            .setTitle(r.title)
            .setMessage(R.string.suggest_delete_question)
            .setPositiveButton(R.string.delete) { _, _ ->
                library.deleteHistory(r.url) { reload() }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private companion object {
        /** '탭으로 전환' 줄의 수. */
        const val MAX_TAB_ROWS = 2
    }
}
