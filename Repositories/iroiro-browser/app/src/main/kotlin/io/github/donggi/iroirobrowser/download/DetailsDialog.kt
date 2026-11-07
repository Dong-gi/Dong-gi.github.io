package io.github.donggi.iroirobrowser.download

import android.app.Activity
import android.app.AlertDialog
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.donggi.iroirobrowser.R
import io.github.donggi.iroirobrowser.ui.Ui

/** 내려받기 목록의 '자세히' 창(갤러리 받기) — 플랫폼 뷰로 짓는다(새 의존 없이). 메인 스레드. */
internal object DetailsDialog {

    /** [lines] 를 위에, 그 아래 할 수 있는 일([actions] — 누르면 창을 닫고 한다)과 '닫기'. */
    fun show(activity: Activity, title: String, lines: List<String>, actions: List<Pair<String, () -> Unit>>) {
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
