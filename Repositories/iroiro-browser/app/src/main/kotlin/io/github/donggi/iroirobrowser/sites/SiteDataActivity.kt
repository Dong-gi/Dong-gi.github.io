package io.github.donggi.iroirobrowser.sites

import android.app.Activity
import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.format.DateUtils
import android.text.format.Formatter
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebStorage
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.webkit.WebStorageCompat
import androidx.webkit.WebViewFeature
import io.github.donggi.iroirobrowser.BuildConfig
import io.github.donggi.iroirobrowser.R
import io.github.donggi.iroirobrowser.WebApp
import io.github.donggi.iroirobrowser.ui.Ui
import java.util.Locale

/**
 * 설정의 '사이트별 데이터' — 사이트 하나의 쿠키(로그인)·저장소·캐시를 지운다(사용자: 익명 탭 대신 설정에서 사이트별 데이터를 지울 수 있으면
 * 된다, 2026-10-04).
 *
 * 목록은 이 브라우저로 연 사이트([SiteList])와 저장소를 쓴 출처(`WebStorage.getOrigins` — 쓴 양도 준다)를 합친 것이다. 쿠키가 있는지는
 * 줄을 그릴 때 그 이름의 쿠키를 물어 본다. 지우기는 WebView 의 `deleteBrowsingDataForSite` 다 — **사이트 단위**(등록 도메인 —
 * `www.example.com` 을 지우면 `example.com` 전체)로 지우고 지운 사이트를 돌려준다. 그 사이트에 딸린 이름을 목록에서 함께 뺀다. 찾기 칸에
 * 목록에 없는 이름을 넣어도 지울 수 있다.
 */
class SiteDataActivity : Activity() {

    private class Row(val host: String, val lastVisit: Long?, val storageBytes: Long)

    private lateinit var app: WebApp
    private lateinit var filter: EditText
    private lateinit var list: ListView
    private lateinit var empty: TextView
    private var rows: List<Row> = emptyList()
    private var shown: List<Any> = emptyList()
    private val cookieCache = HashMap<String, Boolean>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = WebApp.of(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        Ui.edgeToEdge(this, root)
        root.addView(Ui.header(this, getString(R.string.site_data)) { finish() })
        root.addView(TextView(this).apply {
            text = getString(R.string.site_data_note)
            textSize = 13f
            alpha = 0.75f
            setPadding(Ui.dp(context, 20), 0, Ui.dp(context, 20), Ui.dp(context, 8))
        })
        filter = EditText(this).apply {
            hint = getString(R.string.site_filter_hint)
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) = render()
            })
        }
        root.addView(
            LinearLayout(this).apply {
                setPadding(Ui.dp(context, 16), 0, Ui.dp(context, 16), 0)
                addView(filter, Ui.matchWidth())
            },
            Ui.matchWidth(),
        )
        empty = TextView(this).apply {
            text = getString(R.string.site_empty)
            textSize = 14f
            alpha = 0.7f
            setPadding(Ui.dp(context, 20), Ui.dp(context, 16), Ui.dp(context, 20), 0)
            visibility = View.GONE
        }
        root.addView(empty, Ui.matchWidth())
        list = ListView(this).apply {
            adapter = Adapter()
            setOnItemClickListener { _, _, position, _ ->
                when (val item = shown[position]) {
                    is Row -> confirmDelete(item.host)
                    is String -> confirmDelete(item)
                }
            }
        }
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        load()
    }

    /** 연 사이트와 저장소를 쓴 출처를 모은다. */
    private fun load() {
        val visited = app.sites.all()
        WebStorage.getInstance().getOrigins(
            ValueCallback { result ->
                val usage = HashMap<String, Long>()
                (result as? Map<*, *>)?.values?.forEach { o ->
                    if (o is WebStorage.Origin) {
                        val host = Uri.parse(o.origin).host?.lowercase(Locale.ROOT)
                        if (!host.isNullOrEmpty()) usage[host] = (usage[host] ?: 0L) + o.usage
                    }
                }
                rows = (visited.keys + usage.keys)
                    .map { Row(it, visited[it], usage[it] ?: 0L) }
                    .sortedWith(compareByDescending<Row> { it.lastVisit ?: 0L }.thenBy { it.host })
                cookieCache.clear()
                render()
            },
        )
    }

    private fun render() {
        val q = filter.text.toString().trim().lowercase(Locale.ROOT)
        val matching = if (q.isEmpty()) rows else rows.filter { q in it.host }
        val typed = typedSite(q)
        shown = if (typed != null && matching.none { it.host == typed }) listOf<Any>(typed) + matching else matching
        empty.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
        (list.adapter as BaseAdapter).notifyDataSetChanged()
    }

    /** 찾기 칸의 글이 사이트 이름(또는 주소)으로 보이면 그 호스트. */
    private fun typedSite(q: String): String? {
        if (q.isEmpty() || q.any { it.isWhitespace() }) return null
        val host = (if ("://" in q) Uri.parse(q).host else q.substringBefore('/'))?.trim('.') ?: return null
        return host.takeIf { '.' in it && it.all { c -> c.isLetterOrDigit() || c == '.' || c == '-' } }
    }

    private fun confirmDelete(host: String) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.site_delete_title, host))
            .setMessage(R.string.site_delete_question)
            .setPositiveButton(R.string.site_delete) { _, _ -> delete(host) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun delete(host: String) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)) {
            // 돌려받는 것은 WebView 가 실제로 지운 사이트(등록 도메인)다 — 끝난 알림은 메인 스레드로 늦게 오므로 그때는 정해져 있다.
            var site = host
            site = try {
                WebStorageCompat.deleteBrowsingDataForSite(WebStorage.getInstance(), host) {
                    CookieManager.getInstance().flush()
                    app.sites.removeSite(site)
                    if (BuildConfig.DEBUG) Log.i("WebUI", "site deleted $site")
                    if (!isDestroyed) {
                        Toast.makeText(this, getString(R.string.site_deleted, site), Toast.LENGTH_SHORT).show()
                        filter.setText("")
                        load()
                    }
                }
            } catch (e: IllegalArgumentException) {
                Toast.makeText(this, R.string.site_invalid, Toast.LENGTH_SHORT).show()
                return
            }
        } else {
            Toast.makeText(this, R.string.site_delete_unsupported, Toast.LENGTH_LONG).show()
        }
    }

    private fun hasCookies(host: String): Boolean = cookieCache.getOrPut(host) {
        val name = if (':' in host && !host.startsWith("[")) "[$host]" else host
        val cookies = CookieManager.getInstance()
        !cookies.getCookie("https://$name/").isNullOrEmpty() || !cookies.getCookie("http://$name/").isNullOrEmpty()
    }

    private fun detail(row: Row): String = buildList {
        add(getString(if (hasCookies(row.host)) R.string.site_cookies_yes else R.string.site_cookies_no))
        if (row.storageBytes > 0) add(getString(R.string.site_storage, Formatter.formatShortFileSize(this@SiteDataActivity, row.storageBytes)))
        row.lastVisit?.let {
            add(getString(R.string.site_last, DateUtils.formatDateTime(this@SiteDataActivity, it, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH)))
        }
    }.joinToString(" · ")

    private inner class Adapter : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(position: Int): Any = shown[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val item = shown[position]
            val box = LinearLayout(this@SiteDataActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = Ui.dp(context, 56)
                setPadding(Ui.dp(context, 20), Ui.dp(context, 8), Ui.dp(context, 20), Ui.dp(context, 8))
            }
            when (item) {
                is Row -> {
                    box.addView(TextView(this@SiteDataActivity).apply {
                        text = item.host
                        textSize = 15f
                        isSingleLine = true
                    })
                    box.addView(TextView(this@SiteDataActivity).apply {
                        text = detail(item)
                        textSize = 12f
                        alpha = 0.7f
                    })
                }
                is String -> box.addView(TextView(this@SiteDataActivity).apply {
                    text = getString(R.string.site_delete_typed, item)
                    textSize = 15f
                    setTextColor(Ui.themeColor(context, android.R.attr.colorAccent))
                })
            }
            return box
        }
    }
}
