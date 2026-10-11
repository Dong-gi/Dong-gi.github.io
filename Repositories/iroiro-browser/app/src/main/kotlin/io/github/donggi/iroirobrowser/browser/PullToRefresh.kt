package io.github.donggi.iroirobrowser.browser

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.Gravity
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageView
import io.github.donggi.iroirobrowser.R
import io.github.donggi.iroirobrowser.ui.Ui
import kotlin.math.abs

/**
 * 탭의 WebView — 쪽의 뿌리가 맨 위에서 더 위로 밀린 것(쪽 안의 누구도 받지 않은 스크롤 — 크로미움이 `overScrollBy` 로 알린다)을 알린다.
 * 당겨서 새로 고침([PullRefreshLayout])이 듣는다. 쪽 안의 스크롤 상자가 받은 스크롤은 여기로 오지 않는다.
 *
 * 마지막으로 손가락이 닿은 자리([lastDownX]·[lastDownY])도 적어 둔다 — 길게 누르기 메뉴의 '이 요소 숨기기' 가 그 자리의 요소부터 고른다
 * (WebView 의 `HitTestResult` 는 고리·그림의 주소만 알려 주고 자리는 알려 주지 않는다).
 */
class PageWebView(context: Context) : WebView(context) {

    /** 쪽의 뿌리가 맨 위에서 더 위로 밀렸다(메인 스레드). */
    var onTopOverscroll: (() -> Unit)? = null

    /** 마지막으로 손가락이 닿은 자리(이 뷰의 픽셀) — 아직 없으면 NaN. */
    var lastDownX = Float.NaN
        private set
    var lastDownY = Float.NaN
        private set

    /**
     * 창이 보이지 않아도(앱이 뒤로 갔다·화면이 꺼졌다) WebView 에는 창이 보인다고 알린다 — 뒤에서 재생을 잇는 탭([BackgroundPlay]). 크로미움은
     * 안드로이드에서 숨은 쪽의 영상을 소리가 있어도 멈추고, 쪽은 `visibilitychange` 로 숨은 것을 안다(유튜브는 그때 스스로 멈춘다) — 창이
     * 보인다고 알리면 둘 다 일어나지 않는다.
     *
     * **창이 실제로 다시 보이면 저절로 꺼진다.** 화면이 돌아올 때(`onResume`) 끄면 창은 아직 보이기 전이라 그 사이 '안 보임' 을 알리게 되고,
     * 쪽이 그 틈에 재생을 멈췄다(2026-10-11 에뮬레이터). 뒤에서 재생이 끝나 끌 때는 지금의 실제 창 상태를 다시 알린다.
     */
    var keepWindowVisible = false
        set(value) {
            if (field == value) return
            field = value
            super.onWindowVisibilityChanged(if (value) View.VISIBLE else windowVisibility)
        }

    override fun onWindowVisibilityChanged(visibility: Int) {
        if (visibility == View.VISIBLE) keepWindowVisible = false
        super.onWindowVisibilityChanged(if (keepWindowVisible) View.VISIBLE else visibility)
    }

    /**
     * 다른 탭을 고른 사이에 뒤에서 재생을 잇는 탭의 WebView 인가([BackgroundPlay]). 쪽의 자리에 **붙인 채** 둔다 — 떼었다 붙이면 크로미움이 그
     * 사이 숨김을 알려 재생이 멈춘다. 대신 그리지 않고(투명 — 그리기가 건너뛰어진다) 손가락·초점·화면 낭독기에서 뺀다.
     */
    var backstaged = false
        set(value) {
            if (field == value) return
            field = value
            if (value && hasFocus()) clearFocus()
            alpha = if (value) 0f else 1f
            descendantFocusability = if (value) FOCUS_BLOCK_DESCENDANTS else FOCUS_BEFORE_DESCENDANTS
            importantForAccessibility = if (value) IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS else IMPORTANT_FOR_ACCESSIBILITY_AUTO
        }

    /** 진짜 손가락이 닿아 있는가(누르고 떼기 전) — 앱이 넣는 누름([tap])이 그 사이에 끼어들지 않게. */
    private var touching = false

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (backstaged) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastDownX = event.x
                lastDownY = event.y
                touching = true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> touching = false
        }
        return super.dispatchTouchEvent(event)
    }

    /**
     * 이 뷰의 ([x], [y]) 픽셀을 손가락으로 한 번 누른다 — 쪽에는 사람의 누름(신뢰된 사건)으로 간다(유튜브 광고 건너뛰기 — [YtGuard]). 앱이 뒤에
     * 있거나 쪽의 자리에 투명하게 남은 탭([backstaged])에도 넣는다. 손가락이 닿아 있으면 넣지 않는다(거짓 — 쪽의 스크립트가 다시 청한다).
     */
    fun tap(x: Float, y: Float): Boolean {
        if (touching || !isAttachedToWindow) return false
        injectTap(x, y) { super.dispatchTouchEvent(it) }
        return true
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean = !backstaged && super.dispatchGenericMotionEvent(event)

    override fun onOverScrolled(scrollX: Int, scrollY: Int, clampedX: Boolean, clampedY: Boolean) {
        super.onOverScrolled(scrollX, scrollY, clampedX, clampedY)
        // 맨 아래에서 밀린 것도 clampedY 다(높이가 화면보다 짧은 쪽은 맨 아래도 0) — 손가락의 방향은 판정([PullTracker])이 본다.
        if (clampedY && scrollY <= 0) onTopOverscroll?.invoke()
    }
}

/**
 * 손가락 하나로 한 번 누른다 — 누름과 뗌을 곧바로 잇달아 넣는다(뗌의 시각은 [TAP_MS] 뒤로 적는다 — 그 사이에 진짜 손가락이 끼어들 틈이
 * 없게). 터치스크린의 손가락으로 적어야 크로미움이 손가락의 두드림(GestureTap → click)으로 읽는다.
 */
internal fun injectTap(x: Float, y: Float, dispatch: (MotionEvent) -> Unit) {
    val down = SystemClock.uptimeMillis()
    val props = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER })
    for ((action, time) in listOf(MotionEvent.ACTION_DOWN to down, MotionEvent.ACTION_UP to down + TAP_MS)) {
        val coords = arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 1f; size = 1f })
        val event = MotionEvent.obtain(down, time, action, 1, props, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        try {
            dispatch(event)
        } finally {
            event.recycle()
        }
    }
}

/** 넣는 누름의 길이(누름 → 뗌). */
private const val TAP_MS = 60L

/**
 * 당겨서 새로 고침의 판정(화면 밖의 계산 — 시험한다). 크롬(`OverscrollRefresh`)처럼 **몸짓을 쪽의 맨 위에서 시작했고, 손가락이 아래로 움직여
 * 생긴 첫 스크롤을 쪽이 받지 않았을 때**만 당김이 된다 — 쪽의 뿌리가 맨 위에서 더 위로 밀렸다는 알림이 곧바로([window] 안에) 와야 한다. 쪽 안의
 * 스크롤 상자가 먼저 받았으면(그 상자를 위로 올리는 중) 알림이 늦게 오므로 그 몸짓에서는 당기지 않는다. 손가락을 위로 올리거나 옆으로 밀면
 * 그 몸짓은 쪽의 것이다.
 *
 * 당긴 거리의 [DAMPING] 만큼 동그라미가 내려오고([offset] — [max] 까지), [trigger] 를 넘겨 놓으면 새로 고친다(플랫폼 SwipeRefreshLayout 의
 * 비율). 좌표는 픽셀.
 */
class PullTracker(private val slop: Float, private val window: Float, private val trigger: Float, private val max: Float) {

    enum class State { IDLE, ARMED, PULLING }

    var state = State.IDLE
        private set

    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var startY = 0f

    /** 손가락이 아래로 슬롭을 넘은 자리(아직이면 NaN). */
    private var crossedAt = Float.NaN

    /** 손가락이 닿았다 — [enabled](쪽이 맨 위에 있고 당겨도 되는 때)면 지켜본다. */
    fun down(x: Float, y: Float, enabled: Boolean) {
        downX = x
        downY = y
        lastY = y
        crossedAt = Float.NaN
        state = if (enabled) State.ARMED else State.IDLE
    }

    fun move(x: Float, y: Float) {
        lastY = y
        if (state != State.ARMED) return
        val dx = x - downX
        val dy = y - downY
        when {
            // 위로(쪽을 내리는 중) 또는 옆으로 — 이 몸짓은 쪽의 것이다.
            dy < -slop || (abs(dx) > slop && abs(dx) > abs(dy)) -> state = State.IDLE
            crossedAt.isNaN() && dy > slop -> crossedAt = y
            // 첫 스크롤을 쪽 안의 누가 받았다 — 맨 위의 알림이 제때 오지 않았다.
            !crossedAt.isNaN() && y - crossedAt > window -> state = State.IDLE
        }
    }

    /** 쪽의 뿌리가 맨 위에서 더 위로 밀렸다 — 지켜보던 몸짓이면 이제부터 당김이다(true — 부르는 쪽이 쪽에서 몸짓을 거둔다). */
    fun overscrolledTop(): Boolean {
        if (state != State.ARMED || crossedAt.isNaN()) return false
        state = State.PULLING
        startY = lastY
        return true
    }

    /** 동그라미가 내려온 거리. */
    val offset: Float get() = if (state == State.PULLING) ((lastY - startY) * DAMPING).coerceIn(0f, max) else 0f

    /** 지금 손을 떼면 새로 고친다. */
    val ready: Boolean get() = state == State.PULLING && offset >= trigger

    /** 손을 뗐다 — 새로 고칠까. */
    fun release(): Boolean {
        val go = ready
        state = State.IDLE
        return go
    }

    fun cancel() {
        state = State.IDLE
    }

    companion object {
        /** 손가락이 움직인 거리에 대한 동그라미의 거리(플랫폼 SwipeRefreshLayout 의 DRAG_RATE). */
        const val DAMPING = 0.5f
    }
}

/**
 * 쪽의 자리(탭의 WebView·시작 화면·오류 덮개를 담는다) — **당겨서 새로 고침**. 쪽이 맨 위에 있을 때 쪽을 아래로 당기면 막대
 * 아래로 동그라미가 내려오고, 충분히 당겨 놓으면 새로 고친다. 막대를 아래로 당기면 탭 목록이 열리는 몸짓([ToolbarGestureLayout] — 8단계)과는
 * 자리가 다르다 — 이것은 쪽에서 시작한 손가락만 본다.
 *
 * 아이(WebView)에게 가는 손가락을 지켜만 보다가([dispatchTouchEvent] — WebView 가 부모의 가로채기를 막아도 지나간다), 당김이 되는 순간
 * 아이에게 취소를 보내고 그 몸짓을 가져온다. 어느 쪽의 WebView 를 볼지·당겨도 되는지는 [Listener] 가 정한다.
 */
@SuppressLint("ViewConstructor") // 코드로만 만든다(배치 파일에서 쓰지 않는다).
class PullRefreshLayout(context: Context, private val listener: Listener) : FrameLayout(context) {

    interface Listener {
        /** 손가락이 닿은 지금 당김을 받아도 되는가 — 탭의 쪽이 맨 위에 있고, 주소창을 고치는 중이 아니고, 덮개가 없을 때. */
        fun canPull(): Boolean

        /** 당겨 놓았다 — 지금 탭을 새로 고친다. */
        fun onRefresh()
    }

    private val size = Ui.dp(context, INDICATOR_DP)
    private val tracker = PullTracker(
        slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat(),
        window = Ui.dp(context, WINDOW_DP).toFloat(),
        trigger = Ui.dp(context, TRIGGER_DP).toFloat(),
        max = Ui.dp(context, MAX_DP).toFloat(),
    )
    private val idleTint = ColorStateList.valueOf(Ui.themeColor(context, android.R.attr.textColorSecondary))
    private val readyTint = ColorStateList.valueOf(Ui.themeColor(context, android.R.attr.colorAccent))

    /** 막대 아래 가운데로 내려오는 동그라미 — 부르는 쪽이 이 레이아웃 위(쪽의 자리)에 [indicatorParams] 로 붙인다. */
    val indicator: ImageView = ImageView(context).apply {
        setImageResource(R.drawable.ic_reload)
        imageTintList = idleTint
        val pad = Ui.dp(context, 9)
        setPadding(pad, pad, pad, pad)
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Ui.themeColor(context, android.R.attr.colorBackground))
        }
        elevation = Ui.dp(context, 4).toFloat()
        visibility = View.GONE
        // 몸짓의 그림일 뿐이다 — 새로 고침은 막대의 단추로도 된다.
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun indicatorParams(): LayoutParams = LayoutParams(size, size, Gravity.TOP or Gravity.CENTER_HORIZONTAL)

    /** 이 몸짓을 당김으로 가져왔다(쪽에는 취소를 보냈다) — 손을 뗄 때까지 쪽에 보내지 않는다. */
    private var taken = false

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val action = ev.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            taken = false
            tracker.down(ev.x, ev.y, listener.canPull())
        }
        if (taken) {
            val pulling = tracker.state == PullTracker.State.PULLING
            when (action) {
                MotionEvent.ACTION_MOVE -> {
                    tracker.move(ev.x, ev.y)
                    if (pulling) drawPull()
                }
                // 두 손가락(확대) — 당김을 거둔다. 남은 손가락은 손을 뗄 때까지 아무 데도 가지 않는다(쪽은 이미 취소를 받았다).
                MotionEvent.ACTION_POINTER_DOWN -> if (pulling) {
                    tracker.cancel()
                    settle(refresh = false)
                }
                MotionEvent.ACTION_UP -> {
                    val refresh = pulling && tracker.release()
                    if (pulling) settle(refresh)
                    taken = false
                    if (refresh) listener.onRefresh()
                }
                MotionEvent.ACTION_CANCEL -> {
                    tracker.cancel()
                    if (pulling) settle(refresh = false)
                    taken = false
                }
            }
            return true
        }
        when (action) {
            MotionEvent.ACTION_MOVE -> tracker.move(ev.x, ev.y)
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> tracker.cancel()
        }
        return super.dispatchTouchEvent(ev)
    }

    /**
     * 쪽의 뿌리가 맨 위에서 더 위로 밀렸다([PageWebView]) — 지켜보던 몸짓이면 당김으로 가져온다. 가져오기(쪽에 취소를 보낸다)는 지금 하던
     * 일이 끝난 뒤에 한다 — 이 알림은 WebView 가 손가락을 처리하는 도중에 올 수도 있어 그 안에서 취소를 보내면 몸짓이 겹친다.
     */
    fun onTopOverscroll() {
        if (tracker.overscrolledTop()) post { takeOver() }
    }

    private fun takeOver() {
        // 그 사이 손을 뗐으면 쪽의 몸짓으로 끝났다.
        if (tracker.state != PullTracker.State.PULLING || taken) return
        taken = true
        // 쪽에서 몸짓을 거둔다 — 이제부터 이 손가락은 새로 고침의 것이다(쪽은 touchcancel 을 받는다).
        val now = SystemClock.uptimeMillis()
        val cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
        super.dispatchTouchEvent(cancel)
        cancel.recycle()
        // 앞 당김의 마무리(올라가는 중)가 남아 있으면 멈추고 새 당김을 그린다.
        indicator.animate().cancel()
        drawPull()
    }

    private fun drawPull() {
        val offset = tracker.offset
        val trigger = Ui.dp(context, TRIGGER_DP).toFloat()
        indicator.visibility = View.VISIBLE
        indicator.translationY = offset - size
        indicator.rotation = offset / trigger * 270f
        indicator.alpha = 0.3f + 0.7f * (offset / trigger).coerceAtMost(1f)
        indicator.imageTintList = if (tracker.ready) readyTint else idleTint
    }

    /** 손을 뗐다 — 새로 고치면 한 바퀴 돌고 올라가고, 아니면 곧바로 올라간다. */
    private fun settle(refresh: Boolean) {
        val hide = Runnable {
            indicator.visibility = View.GONE
            indicator.imageTintList = idleTint
        }
        if (refresh) {
            indicator.animate().rotationBy(360f).setDuration(SPIN_MS).withEndAction {
                indicator.animate().translationY(-size.toFloat()).alpha(0f).setDuration(RETRACT_MS).withEndAction(hide).start()
            }.start()
        } else {
            indicator.animate().translationY(-size.toFloat()).alpha(0f).setDuration(RETRACT_MS).withEndAction(hide).start()
        }
    }

    private companion object {
        const val INDICATOR_DP = 40
        /** 손가락이 아래로 슬롭을 넘은 뒤 이 거리 안에 '맨 위에서 밀림' 이 와야 당김이 된다(그 뒤면 쪽 안의 상자가 받은 것이다). */
        const val WINDOW_DP = 48
        /** 동그라미가 이만큼 내려오면(손가락은 그 두 배) 놓았을 때 새로 고친다 — SwipeRefreshLayout 의 64 dp. */
        const val TRIGGER_DP = 64
        const val MAX_DP = 96
        const val SPIN_MS = 350L
        const val RETRACT_MS = 200L
    }
}
