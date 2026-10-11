package io.github.donggi.iroirobrowser.browser

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.TypedValue
import io.github.donggi.iroirobrowser.ui.Ui

/**
 * 단추 위 오른쪽 모서리에 그리는 작은 수 — 막대의 '찾은 미디어' 단추에 지금 페이지에서 찾은 수를 보인다. 0 이면 그리지 않는다.
 * 단추의 `foreground` 로 건다 — 막대의 배치를 바꾸지 않는다. 화면 낭독기에는 단추의 상태 설명으로 따로 알린다.
 */
class CountBadge(context: Context) : Drawable() {

    var count: Int = 0
        set(value) {
            if (field != value) {
                field = value
                invalidateSelf()
            }
        }

    private val density = context.resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.themeColor(context, android.R.attr.colorAccent) }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10f, context.resources.displayMetrics)
    }
    private val box = RectF()

    override fun draw(canvas: Canvas) {
        if (count <= 0) return
        val label = if (count > 99) "99+" else count.toString()
        val height = 15 * density
        val width = maxOf(height, text.measureText(label) + 8 * density)
        val right = bounds.right - 5 * density
        val top = bounds.top + 5 * density
        box.set(right - width, top, right, top + height)
        canvas.drawRoundRect(box, height / 2, height / 2, fill)
        canvas.drawText(label, box.centerX(), box.centerY() - (text.descent() + text.ascent()) / 2, text)
    }

    override fun setAlpha(alpha: Int) {
        fill.alpha = alpha
        text.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fill.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Drawable.getOpacity 는 API 29 부터 그리기에 쓰이지 않는다 — 추상 메서드라 둔다.")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
