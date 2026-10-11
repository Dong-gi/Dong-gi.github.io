package io.github.donggi.iroirobrowser.browser

import android.app.Activity
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import io.github.donggi.iroirobrowser.ui.Ui

/**
 * 화면 아래에 잠깐 뜨는 한 줄과 단추 하나 — '탭을 닫았습니다 · 되돌리기', '팝업을 막았습니다 · 이 사이트 허용'. 플랫폼 뷰로 짓는다(머티리얼의
 * 스낵바는 새 의존이다). 새 것이 뜨면 앞 것은 사라진다. 화면 낭독기가 읽게 글은 알림 영역(live region)이고, 사라지기까지의 시간은 접근성
 * 설정이 권하는 만큼 늘인다(`getRecommendedTimeoutMillis` — '조작할 시간' 설정).
 */
class MessageBar(private val activity: Activity) {

    private val main = Handler(Looper.getMainLooper())
    private val hideLater = Runnable { hide() }
    private val night = Ui.isNight(activity)

    private val text = TextView(activity).apply {
        textSize = 14f
        setTextColor(if (night) Color.rgb(0x21, 0x21, 0x21) else Color.WHITE)
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }

    private val action = Button(activity, null, android.R.attr.borderlessButtonStyle).apply {
        minWidth = 0
        minimumWidth = 0
        setTextColor(if (night) Color.rgb(0x00, 0x69, 0x5C) else Color.rgb(0x80, 0xCB, 0xC4))
        setPadding(Ui.dp(activity, 12), paddingTop, Ui.dp(activity, 12), paddingBottom)
    }

    /** 부르는 쪽이 화면의 뿌리(FrameLayout)에 [params] 로 붙인다. */
    val view: LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = Ui.dp(activity, 48)
        setPadding(Ui.dp(activity, 16), Ui.dp(activity, 4), Ui.dp(activity, 4), Ui.dp(activity, 4))
        // 크롬·머티리얼의 스낵바처럼 화면과 반대인 빛(밝은 화면엔 짙게, 밤에는 옅게).
        background = Ui.rounded(if (night) Color.rgb(0xE0, 0xE0, 0xE0) else Color.rgb(0x32, 0x32, 0x32), 6, activity)
        elevation = Ui.dp(activity, 6).toFloat()
        visibility = View.GONE
        isClickable = true // 뒤의 쪽에 누름이 새지 않게
        addView(text, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(action)
    }

    fun params(): FrameLayout.LayoutParams =
        FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
            val m = Ui.dp(activity, 8)
            setMargins(m, m, m, m)
        }

    val isShowing: Boolean get() = view.visibility == View.VISIBLE

    /** [message] 를 띄운다 — [actionLabel] 을 누르면 [onAction] 을 하고 닫는다. */
    fun show(message: String, actionLabel: String, onAction: () -> Unit) {
        main.removeCallbacks(hideLater)
        action.text = actionLabel
        action.setOnClickListener {
            hide()
            onAction()
        }
        // 보이게 한 뒤에 글을 바꾼다 — 알림 영역은 보이는 동안의 글 바뀜을 읽는다(같은 글이 잇달아 떠도 읽게 한 번 비운다).
        text.text = ""
        view.visibility = View.VISIBLE
        view.bringToFront()
        text.text = message
        restartTimer()
    }

    /** 떠 있으면 사라지기까지를 처음부터 다시 센다 — 창(대화 상자)이 가리던 동안에는 누를 수 없었다. */
    fun restartTimer() {
        main.removeCallbacks(hideLater)
        if (!isShowing) return
        val a11y = activity.getSystemService(AccessibilityManager::class.java)
        val timeout = a11y?.getRecommendedTimeoutMillis(
            SHOW_MS.toInt(),
            AccessibilityManager.FLAG_CONTENT_TEXT or AccessibilityManager.FLAG_CONTENT_CONTROLS,
        )?.toLong() ?: SHOW_MS
        main.postDelayed(hideLater, timeout)
    }

    fun hide() {
        main.removeCallbacks(hideLater)
        if (view.visibility == View.GONE) return
        view.visibility = View.GONE
        // 다음에 같은 글이 떠도 낭독기가 읽게(알림 영역은 글이 바뀔 때 읽는다).
        text.text = ""
        action.setOnClickListener(null)
    }

    private companion object {
        /** 크롬의 '탭을 닫았습니다' 처럼 몇 초 — 접근성 설정이 더 길게 권하면 그것을 따른다. */
        const val SHOW_MS = 5_000L
    }
}
