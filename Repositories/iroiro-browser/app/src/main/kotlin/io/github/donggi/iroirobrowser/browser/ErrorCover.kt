package io.github.donggi.iroirobrowser.browser

import android.app.Activity
import android.graphics.Typeface
import android.net.Uri
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.donggi.iroirobrowser.R
import io.github.donggi.iroirobrowser.browser.PageErrors.Reason
import io.github.donggi.iroirobrowser.browser.PageErrors.Source
import io.github.donggi.iroirobrowser.proxy.NetworkPath
import io.github.donggi.iroirobrowser.ui.Ui

/**
 * 탭이 연 주 문서의 오류 하나 — 그 탭 위에 '열지 못함' 덮개([ErrorCover])가 보인다. 탭마다 두므로 뒤에서 연 탭의 오류(인증서 등)는 그 탭으로
 * 갔을 때 보인다. [phase] 는 오류가 난 때의 길의 상태(오류 직후에 온 길의 소식이면 화면이 옮겨 담는다 — [PageErrors.isPathFailure]),
 * [at] 은 그때(`elapsedRealtime` — 같은 실패가 다시 나면 새로 적는다).
 */
class PageError(val url: String, val source: Source, var phase: NetworkPath.Phase, val proxyOn: Boolean, var at: Long) {

    /** 오류 쪽이 커밋됐다(WebView 가 그 주소로 제 오류 쪽을 열었다) — 그다음 커밋은 새 쪽이라 덮개를 걷는다. */
    var committed = false

    /** [다시 시도] 를 눌러 다시 여는 중. */
    var retrying = false

    /** 사용자가 덮개를 닫았다 — 그 아래(WebView 의 오류 쪽·프록시 서버의 쪽·앞 쪽)를 본다. */
    var dismissed = false

    val verdict: PageErrors.Verdict get() = PageErrors.verdict(source, url, proxyOn, phase)
}

/**
 * '열지 못함' 덮개 — 까닭(사람의 말로), 어느 주소였는지, [다시 시도]·[연결 상태], 그리고 닫기(✕ — 그 아래를 본다). 탭의 쪽 자리에 덮는다.
 * 까닭은 [PageErrors] 가 고르고 여기서는 글만 고른다. 까닭의 글은 알림 영역이라 화면 낭독기가 덮개가 뜰 때 읽는다.
 */
class ErrorCover(private val activity: Activity, private val actions: Actions) {

    interface Actions {
        fun retry()
        fun showStatus()
        fun dismiss()
    }

    private val title = TextView(activity).apply {
        textSize = 20f
        setTypeface(typeface, Typeface.BOLD)
    }
    private val host = TextView(activity).apply {
        textSize = 14f
        alpha = 0.7f
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.MIDDLE
        setPadding(0, Ui.dp(activity, 4), 0, Ui.dp(activity, 16))
    }
    private val body = TextView(activity).apply {
        textSize = 15f
        setLineSpacing(0f, 1.15f)
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val detail = TextView(activity).apply {
        textSize = 12f
        alpha = 0.55f
        setPadding(0, Ui.dp(activity, 10), 0, 0)
        setTextIsSelectable(true)
    }
    private val retry = Button(activity).apply { setOnClickListener { actions.retry() } }
    private val status = Button(activity, null, android.R.attr.borderlessButtonStyle).apply {
        text = activity.getString(R.string.status_title)
        setOnClickListener { actions.showStatus() }
    }

    /** 쪽의 자리(탭의 WebView 위)에 덮는다. 누름이 뒤의 쪽에 새지 않는다. */
    val view: FrameLayout = FrameLayout(activity).apply {
        setBackgroundColor(Ui.themeColor(activity, android.R.attr.colorBackground))
        isClickable = true
        visibility = View.GONE
    }

    private val scroll = ScrollView(activity).apply { isFillViewport = true }
    private val close = Ui.iconButton(activity, R.drawable.ic_close, activity.getString(R.string.cover_close)) { actions.dismiss() }

    init {
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            val side = Ui.dp(activity, 28)
            setPadding(side, Ui.dp(activity, 48), side, Ui.dp(activity, 32))
        }
        column.addView(title, Ui.matchWidth())
        column.addView(host, Ui.matchWidth())
        column.addView(body, Ui.matchWidth())
        column.addView(detail, Ui.matchWidth())
        val buttons = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, Ui.dp(activity, 20), 0, 0)
            addView(retry)
            addView(status, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = Ui.dp(activity, 8)
            })
        }
        column.addView(buttons, Ui.matchWidth())
        scroll.addView(column, Ui.matchWidth())
        view.addView(scroll, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        view.addView(close, FrameLayout.LayoutParams(Ui.dp(activity, 48), Ui.dp(activity, 48), Gravity.TOP or Gravity.END))
    }

    val isShowing: Boolean get() = view.visibility == View.VISIBLE

    /**
     * 덮개의 맨 위가 가려진 높이(전체 화면의 막대·노치 — [FullscreenLayout.coveredTop]) — 글과 닫기 단추를 그만큼 내린다. 바탕은 그대로 맨 위부터
     * 칠한다(뒤의 페이지가 비치지 않게).
     */
    fun setTopInset(px: Int) {
        if (scroll.paddingTop != px) scroll.setPadding(0, px, 0, 0)
        val params = close.layoutParams as FrameLayout.LayoutParams
        if (params.topMargin != px) {
            params.topMargin = px
            close.layoutParams = params
        }
    }

    fun show(error: PageError) {
        val verdict = error.verdict
        val renderer = verdict.reason == Reason.RENDERER_CRASHED || verdict.reason == Reason.RENDERER_KILLED
        title.text = activity.getString(if (renderer) R.string.cover_title_renderer else R.string.cover_title)
        val uri = Uri.parse(error.url)
        host.text = uri.host?.takeIf { it.isNotEmpty() } ?: error.url
        detail.text = detailOf(error.source)
        detail.visibility = if (detail.text.isNullOrEmpty()) View.GONE else View.VISIBLE
        retry.text = activity.getString(if (error.retrying) R.string.cover_retrying else R.string.cover_retry)
        retry.isEnabled = !error.retrying
        // 렌더러가 멈춘 것은 길과 상관이 없다.
        status.visibility = if (renderer) View.GONE else View.VISIBLE
        val text = bodyOf(verdict, uri.host.orEmpty())
        val wasShowing = isShowing
        view.visibility = View.VISIBLE
        view.bringToFront()
        // 보이게 한 뒤에 바꾼다 — 알림 영역은 보이는 동안의 글 바뀜을 읽는다.
        if (!wasShowing || body.text.toString() != text) {
            body.text = ""
            body.text = text
        }
    }

    fun hide() {
        if (view.visibility == View.GONE) return
        view.visibility = View.GONE
        body.text = ""
    }

    private fun bodyOf(verdict: PageErrors.Verdict, hostName: String): String = when (verdict.reason) {
        Reason.NOT_CONFIGURED -> activity.getString(R.string.cover_not_configured)
        Reason.PROXY_UNREACHABLE -> activity.getString(R.string.cover_proxy_unreachable)
        Reason.PROXY_AUTH -> activity.getString(R.string.cover_proxy_auth)
        Reason.PROXY_REFUSED -> verdict.port?.let { activity.getString(R.string.cover_proxy_refused_port, it) }
            ?: activity.getString(R.string.cover_proxy_refused)
        Reason.RELAY -> activity.getString(R.string.cover_relay)
        Reason.NAME_NOT_RESOLVED -> activity.getString(R.string.cover_name, hostName)
        Reason.OFFLINE -> activity.getString(R.string.cover_offline)
        Reason.TIMEOUT -> activity.getString(R.string.cover_timeout)
        Reason.CONNECTION -> activity.getString(R.string.cover_connection)
        Reason.CERTIFICATE -> activity.getString(R.string.cover_certificate)
        Reason.TLS -> activity.getString(R.string.cover_tls)
        Reason.REDIRECTS -> activity.getString(R.string.cover_redirects)
        Reason.SITE_ERROR -> activity.getString(R.string.cover_site_error)
        Reason.UNKNOWN -> activity.getString(R.string.cover_unknown)
        Reason.RENDERER_CRASHED -> activity.getString(R.string.cover_renderer_crashed)
        Reason.RENDERER_KILLED -> activity.getString(R.string.cover_renderer_killed)
    }

    /** 기술적인 표(오류 이름·중계와 프록시 서버의 표) — 무엇이 실패했는지 남에게 물을 때 쓴다. */
    private fun detailOf(source: Source): String = when (source) {
        is Source.Network -> source.name.ifBlank { "WebView ${source.code}" }
        is Source.Relay -> activity.getString(R.string.cover_detail_relay, "${source.marker} (${source.status})")
        is Source.Proxy -> activity.getString(R.string.cover_detail_proxy, "${source.squidError} (${source.status})")
        Source.Certificate, is Source.Renderer -> ""
    }
}
