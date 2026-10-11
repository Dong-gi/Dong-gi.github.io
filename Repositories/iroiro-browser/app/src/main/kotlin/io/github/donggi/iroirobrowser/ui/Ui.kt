package io.github.donggi.iroirobrowser.ui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import io.github.donggi.iroirobrowser.R

/** 화면 몇 개가 함께 쓰는 작은 도구. 플랫폼 뷰만 쓴다(새 의존 없이 — 이 앱의 의존은 androidx.webkit 하나다). */
object Ui {

    fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density + 0.5f).toInt()

    fun isNight(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    fun themeColor(context: Context, attr: Int): Int {
        val tv = TypedValue()
        context.theme.resolveAttribute(attr, tv, true)
        return if (tv.resourceId != 0) context.getColor(tv.resourceId) else tv.data
    }

    fun rounded(color: Int, radiusDp: Int, context: Context, strokeColor: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(context, radiusDp).toFloat()
            if (strokeColor != null) setStroke(dp(context, 1), strokeColor)
        }

    /**
     * 막대 아래까지 그리고(edge-to-edge — Android 15 이상은 강제), 막대·키보드·노치만큼 [root] 에 안쪽 여백을 준다. 막대 글자색은 낮·밤에 맞춘다.
     * [inset] 이 false 를 돌려주는 동안은 여백을 주지 않는다(전체 화면 영상).
     */
    fun edgeToEdge(activity: Activity, root: View, inset: () -> Boolean = { true }) {
        drawBehindBars(activity, root)
        root.setOnApplyWindowInsetsListener { v, insets ->
            if (inset()) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime() or WindowInsets.Type.displayCutout())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                v.setPadding(0, 0, 0, 0)
            }
            insets
        }
    }

    /**
     * [edgeToEdge] 의 여백을 뺀 나머지 — 막대 아래까지 그리게 하고 막대 글자색과 [root] 의 바탕을 맞춘다. 여백은 부르는 쪽이 제 듣개로 준다
     * (브라우저 화면의 전체 화면 — 위는 비우고 막대가 상태 표시줄의 높이를 제가 띄운다).
     */
    fun drawBehindBars(activity: Activity, root: View) {
        val window = activity.window
        // setContentView 전에 부르므로 장식 뷰를 먼저 만든다 — 없으면 Android 12 의 getInsetsController 가 NPE 를 낸다(기기에서 앱이 죽었다).
        window.decorView
        // Android 15 이상은 늘 이렇게 그린다(그래서 폐기 예정) — 12~14 에서 같게 하려고 부른다.
        @Suppress("DEPRECATION")
        window.setDecorFitsSystemWindows(false)
        @Suppress("DEPRECATION")
        window.statusBarColor = Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.TRANSPARENT
        val light = if (isNight(activity)) 0 else WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        window.insetsController?.setSystemBarsAppearance(
            light,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
        )
        root.setBackgroundColor(themeColor(activity, android.R.attr.colorBackground))
    }

    /** 위의 막대: 뒤로 단추와 제목. */
    fun header(activity: Activity, title: String, onBack: () -> Unit): View {
        val bar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(activity, 56)
            setPadding(dp(activity, 4), 0, dp(activity, 16), 0)
        }
        bar.addView(iconButton(activity, R.drawable.ic_back, activity.getString(R.string.back)) { onBack() })
        bar.addView(
            TextView(activity).apply {
                text = title
                textSize = 20f
                setPadding(dp(activity, 8), 0, 0, 0)
            },
        )
        return bar
    }

    fun iconButton(context: Context, icon: Int, description: String, onClick: (View) -> Unit): ImageButton =
        ImageButton(context).apply {
            setImageResource(icon)
            contentDescription = description
            setBackgroundResource(TypedValue().also { context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, it, true) }.resourceId)
            imageTintList = android.content.res.ColorStateList.valueOf(themeColor(context, android.R.attr.textColorPrimary))
            val size = dp(context, 48)
            layoutParams = LinearLayout.LayoutParams(size, size)
            setOnClickListener(onClick)
        }

    fun matchWidth(): ViewGroup.LayoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
}
