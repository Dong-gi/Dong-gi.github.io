package io.github.donggi.iroirobrowser.browser

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.LinearLayout
import kotlin.math.abs
import kotlin.math.max

/*
 * 전체 화면(사용자 요청, 2026-10-08: '브라우저를 항상 전체화면으로 실행 & 주소표시줄을 안드로이드 상태표시줄과 함께 상시 숨기기 … 최상단에서
 * 위->아래로 스와이프할 때만 등장'). 이 파일은 그 판단(가장자리의 밀기인가·언제 숨나·어디가 가려지나 — 화면 밖의 계산이라 시험한다)과
 * 브라우저 화면의 뿌리 뷰다. 화면이 하는 일(상태 표시줄과 막대를 함께 보이고 숨기기)은 BrowserActivity 의 '전체 화면' 절.
 */

/**
 * 막대를 부르는 몸짓의 판정 — **화면 위쪽 띠([FullscreenLayout.edgeBand] — 화면 높이의 위 20%)에서 시작해** 아래로 슬롭을 넘고 세로가
 * 우세한(옆으로 간 것보다 더 내려간) 밀기. 띠 안의 두드림·위로 밀기·옆으로 밀기·두 손가락은 페이지의 것이다 — 이 판정은 지켜보기만 하고
 * 손가락은 그대로 페이지로 간다. 밀기가 되는 그 순간에만 부르는 쪽이 페이지에서 몸짓을 거둔다([BrowserFrame]).
 *
 * 좌표는 뿌리 뷰의 픽셀(창의 맨 위가 0).
 */
class EdgeSwipe(private val slop: Float) {

    enum class State { IDLE, WATCHING, TAKEN }

    var state = State.IDLE
        private set

    private var downX = 0f
    private var downY = 0f

    /** 손가락이 닿았다 — [enabled](막대가 숨어 있다)이고 맨 위 [edge] 픽셀의 띠 안이면 지켜본다. */
    fun down(x: Float, y: Float, edge: Float, enabled: Boolean) {
        downX = x
        downY = y
        state = if (enabled && y < edge) State.WATCHING else State.IDLE
    }

    /** 움직였다 — 막대를 부를 밀기가 된 그 순간에만 true(그 뒤의 움직임은 false). */
    fun move(x: Float, y: Float): Boolean {
        if (state != State.WATCHING) return false
        val dx = abs(x - downX)
        val dy = y - downY
        when {
            dy > slop && dx < dy -> {
                state = State.TAKEN
                return true
            }
            // 옆으로·위로 슬롭을 넘었다(비스듬히 45° 넘게 누운 것도) — 페이지의 것이다.
            dx > slop || -dy > slop -> state = State.IDLE
        }
        return false
    }

    /** 둘째 손가락(확대 등) — 페이지의 것이다. 이미 막대를 불렀으면 그대로 쥔다. */
    fun pointerDown() {
        if (state == State.WATCHING) state = State.IDLE
    }

    /** 손을 뗐다(또는 취소). */
    fun end() {
        state = State.IDLE
    }
}

/**
 * 전체 화면에서 막대가 **언제 숨는가** — 시각은 부르는 쪽이 준다(ms, `SystemClock.uptimeMillis`).
 *
 * - 보인 뒤 [autoHideMs] 동안 막대를 만지지 않으면 숨는다. 막대를 만지는 동안은 세지 않고 손을 떼면 처음부터 다시 센다.
 * - 붙잡혀 있으면([hold] — 주소창을 고치는 중·찾기 줄·메뉴와 창·막대의 몸짓…) 숨지 않고, 풀리면 그때부터 처음부터 센다.
 * - 페이지를 누르면(막대 밖) 곧바로 숨는다([pageTouched]) — 붙잡혀 있거나 막대를 만지는 중이 아니면.
 */
class BarTimer(var autoHideMs: Long) {

    var shown = false
        private set

    private var held = false
    private var touching = false
    private var deadline = 0L

    /** 막대를 보였다(이미 보이던 것이면 세기만 처음부터). */
    fun show(now: Long) {
        if (!shown) touching = false
        shown = true
        deadline = now + autoHideMs
    }

    fun hide() {
        shown = false
        touching = false
    }

    /** 붙잡혀 있는가를 알린다 — 붙잡혔다가 풀리는 때부터 처음부터 센다. 같은 것을 거듭 알려도 세기가 늘지 않는다. */
    fun hold(held: Boolean, now: Long) {
        if (this.held && !held) deadline = now + autoHideMs
        this.held = held
    }

    /** 막대(와 그 아래로 내려온 줄들)를 만졌다·뗐다 — 떼는 때부터 처음부터 센다. */
    fun touchBar(down: Boolean, now: Long) {
        if (touching && !down) deadline = now + autoHideMs
        touching = down
    }

    /** 페이지를 눌렀다 — 곧바로 숨길까. */
    fun pageTouched(): Boolean = shown && !held && !touching

    /** 지금 숨길 때인가. */
    fun due(now: Long): Boolean = shown && !held && !touching && now >= deadline

    /**
     * 다음에 볼 때 — 숨길 때가 되는 시각. 붙잡혀 있거나 만지는 중이면 [autoHideMs] 뒤에 다시 본다(풀린 소식을 놓쳐도 숨게). 숨어 있으면 null.
     */
    fun nextCheck(now: Long): Long? = when {
        !shown -> null
        held || touching -> now + autoHideMs
        else -> max(deadline, now)
    }
}

/** 전체 화면의 배치 — 어디를 띄우고 어디가 가려지나(px). */
object FullscreenLayout {

    /**
     * 전체 화면으로 그릴까 — 설정이 켜져 있고, 터치 탐색(TalkBack)이 꺼져 있고(가장자리 밀기를 찾을 수 없다 — 막대를 늘 보이고 쪽을 그 아래에
     * 둔다: 늘 보이는 막대를 겹치면 페이지 맨 위가 늘 가려진다), 분할 화면·자유형 창이 아닐 때(그때는 시스템이 상태 표시줄을 숨기지 못하게
     * 한다 — 숨긴 줄 알고 쪽을 맨 위부터 그리면 상태 표시줄이 페이지를 가린다).
     */
    fun effective(setting: Boolean, touchExploration: Boolean, multiWindow: Boolean): Boolean =
        setting && !touchExploration && !multiWindow

    /**
     * 막대를 부르는 가장자리 띠의 높이 — 상태 표시줄의 높이(숨어 있어도 — `getInsetsIgnoringVisibility`)이고 노치가 그보다 깊으면 노치까지.
     * 둘 다 모르면(0 — 창이 화면 맨 위에 닿지 않는다 등) [fallback].
     */
    fun edgeHeight(statusTop: Int, cutoutTop: Int, fallback: Int): Int = max(statusTop, cutoutTop).takeIf { it > 0 } ?: fallback

    /**
     * 막대를 부르는 띠 — 화면(뿌리) 높이의 위 [EDGE_BAND_FRACTION] 와 [edgeHeight] 의 띠 가운데 큰 것. 상태 표시줄의 높이만큼이면 실기기에서
     * 그 띠에서 시작한 밀기를 시스템이 먼저 가져가 상태 표시줄만 내려오는 일이 잦았다(사용자, 2026-10-10 — '상단 20% 영역으로 확대'). 대가로
     * 막대가 숨어 있는 동안 위 20% 에서 아래로 끌면 페이지가 스크롤되거나 당겨서 새로 고쳐지지 않고 막대가 나온다 — 그 아래에서 시작하면 된다.
     */
    fun edgeBand(edgeHeight: Float, rootHeight: Int): Float = max(edgeHeight, rootHeight * EDGE_BAND_FRACTION)

    /** 막대를 부르는 띠가 덮는 화면 높이의 몫. */
    const val EDGE_BAND_FRACTION = 0.2f

    /** 쪽 위에 겹친 막대가 위에 띄울 높이 — 나타난 상태 표시줄이 막대를 가리지 않게(노치가 더 깊으면 노치까지). */
    fun barTop(statusTop: Int, cutoutTop: Int): Int = max(statusTop, cutoutTop)

    /** 뿌리의 위 여백 — 전체 화면이면 0(쪽이 화면 맨 위부터 — 막대는 그 위에 겹친다), 아니면 상태 표시줄·노치([safeTop]). */
    fun rootTop(fullscreen: Boolean, safeTop: Int): Int = if (fullscreen) 0 else safeTop

    /**
     * 막대가 보이는 동안 막대 아래 줄들(찾기·받기·알림 띠)을 내리는 거리 — 줄의 글이 막대 바로 아래에 오게(진행 막대는 그 위에 겹쳐 그린다 —
     * 막대가 숨었을 때 페이지 위에 겹치는 것과 같다). 줄들이 위에 띄운 노치만큼([stripsTop])은 막대 밑에 숨는다. 막대가 숨어 있거나 전체 화면이
     * 아니면 0(줄들은 제자리 — 화면 맨 위나 막대 아래).
     */
    fun stripsShift(fullscreen: Boolean, shown: Boolean, barHeight: Int, stripsTop: Int): Int =
        if (fullscreen && shown) max(0, barHeight - stripsTop) else 0

    /**
     * 쪽의 자리 맨 위에서 가려진 높이 — 그 위에 덮는 화면의 뷰(시작 화면·주소창 제안·'열지 못함' 덮개)가 그만큼 내려 그린다. 페이지(WebView)는
     * 내리지 않는다(노치·막대 밑까지 그리는 것이 전체 화면이다).
     *
     * - 전체 화면이 아니면 0 — 쪽의 자리는 막대·줄들 아래에서 시작한다.
     * - 막대가 보이면 막대와 그 아래로 내려온 줄들이 덮는 만큼(줄의 높이는 쪽의 자리를 이미 밀어냈으니 빼면 막대의 높이 − 줄들의 위 여백).
     * - 숨어 있고 줄이 보이면 0(줄들이 노치 밑에서 시작하고 쪽은 그 아래다), 줄이 없으면 노치([cutoutTop]).
     */
    fun coveredTop(fullscreen: Boolean, shown: Boolean, barHeight: Int, stripsTop: Int, stripsShowing: Boolean, cutoutTop: Int): Int =
        when {
            !fullscreen -> 0
            shown -> stripsShift(fullscreen = true, shown = true, barHeight = barHeight, stripsTop = stripsTop)
            stripsShowing -> 0
            else -> cutoutTop
        }
}

/**
 * 막대 아래의 줄들(찾기·받기·알림 띠)을 담는 세로 줄. 전체 화면에서 막대가 숨어 있으면 이것이 화면 맨 위에 오므로 줄이 하나라도 보이는
 * 동안은 노치만큼([inset]) 위를 띄운다 — 바탕은 노치 자리까지 칠하고 글은 노치 밑에서 시작한다. 줄이 없으면 띄우지 않는다(페이지가 맨 위부터).
 */
class TopStrips(context: Context) : LinearLayout(context) {

    var inset = 0
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val top = if ((0 until childCount).any { getChildAt(it).visibility != View.GONE }) inset else 0
        // 줄이 보이고 숨는 것(그 아이의 requestLayout)이나 inset 의 바뀜으로 재는 중이다 — 여기서 바꾼 여백은 이번 배치에 든다.
        if (paddingTop != top) setPadding(paddingLeft, top, paddingRight, paddingBottom)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}

/**
 * 브라우저 화면의 뿌리 — 위의 막대([arrange] 의 chrome: 막대와 진행 막대)와 그 아래(줄들·쪽의 자리를 담은 column)를 놓는다. 전체 화면이면
 * 막대를 쪽 위에 겹친다([overlayChrome] — 쪽은 화면 맨 위부터, 막대는 그 위에서 나타났다 숨는다 — 페이지가 다시 배치되지 않는다). 아니면
 * 지금까지처럼 막대 아래에 쪽을 둔다. 뷰를 옮기지 않고 자리만 바꾼다(주소창의 초점·고치던 글이 그대로다).
 *
 * 막대가 숨어 있는 동안 화면 위쪽 띠에서 시작한 손가락을 지켜보다가([EdgeSwipe]) 막대를 부를 밀기가 되면 그 몸짓을 가져온다 — 아이(페이지)에는
 * 취소를 보내고 손을 뗄 때까지 보내지 않는다. 그래서 그 밀기는 페이지를 스크롤하거나 당겨서 새로 고치지 않고, 나타난 막대의 몸짓(당겨 탭
 * 목록)으로 이어지지도 않는다. 지켜보기는 [dispatchTouchEvent] 에서 한다 — WebView 가 부모의 가로채기를 막아도 지나간다([PullRefreshLayout] 과
 * 같다).
 */
@SuppressLint("ViewConstructor") // 코드로만 만든다(배치 파일에서 쓰지 않는다).
class BrowserFrame(context: Context, private val listener: Listener) : FrameLayout(context) {

    interface Listener {
        /** 가장자리 밀기를 지켜볼까 — 전체 화면이고 막대가 숨어 있을 때(전체 화면 영상 중이 아니고). */
        fun edgeEnabled(): Boolean

        /** 상태 표시줄·노치의 띠 높이(px) — 실제 띠는 이것과 화면 높이의 위 20% 가운데 큰 것([FullscreenLayout.edgeBand]). */
        fun edgeHeight(): Float

        /** 가장자리에서 아래로 밀었다 — 막대를 부른다. 그 손가락은 손을 뗄 때까지 이 뷰가 쥔다. */
        fun onEdgeSwipe()

        /** 손가락이 닿았다(이 뷰의 좌표) — 페이지로 가기 전에 불린다. */
        fun onTouchStart(x: Float, y: Float)

        /** 손가락을 모두 뗐다(또는 몸짓이 취소됐다). */
        fun onTouchEnd()
    }

    /** 막대를 쪽 위에 겹칠까(전체 화면). */
    var overlayChrome = false
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    private var chrome: View? = null
    private var column: View? = null

    private val edge = EdgeSwipe(ViewConfiguration.get(context).scaledTouchSlop.toFloat())

    /** 이 몸짓을 막대를 부르는 밀기로 가져왔다(페이지에는 취소를 보냈다) — 손을 뗄 때까지 아이에게 보내지 않는다. */
    private var taken = false

    /** [chrome] 은 맨 위에 붙는 막대(FrameLayout.LayoutParams·Gravity.TOP), [column] 은 그 아래의 나머지 — 둘 다 이 뷰의 아이로 붙인 뒤에 부른다. */
    fun arrange(chrome: View, column: View) {
        this.chrome = chrome
        this.column = column
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val c = chrome
        val params = column?.layoutParams as? MarginLayoutParams
        if (c != null && params != null) {
            // 막대 아래에 둘 때만 막대의 높이를 먼저 재어 나머지를 그만큼 내린다(겹칠 때는 맨 위부터). 막대는 아래 super 가 한 번 더 잰다.
            params.topMargin = if (overlayChrome || c.visibility == View.GONE) {
                0
            } else {
                measureChildWithMargins(c, widthMeasureSpec, 0, heightMeasureSpec, 0)
                c.measuredHeight
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                taken = false
                edge.down(ev.x, ev.y, FullscreenLayout.edgeBand(listener.edgeHeight(), height), listener.edgeEnabled())
                listener.onTouchStart(ev.x, ev.y)
            }
            MotionEvent.ACTION_POINTER_DOWN -> edge.pointerDown()
            MotionEvent.ACTION_MOVE -> if (!taken && edge.move(ev.x, ev.y)) {
                taken = true
                // 페이지(와 그 위의 것들)에서 몸짓을 거둔다 — 이제부터 이 손가락은 막대를 부르는 것이다(페이지는 touchcancel 을 받는다).
                val cancel = MotionEvent.obtain(ev)
                cancel.action = MotionEvent.ACTION_CANCEL
                super.dispatchTouchEvent(cancel)
                cancel.recycle()
                listener.onEdgeSwipe()
                return true
            }
        }
        val handled = if (taken) true else super.dispatchTouchEvent(ev)
        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
            taken = false
            edge.end()
            listener.onTouchEnd()
        }
        return handled
    }
}
