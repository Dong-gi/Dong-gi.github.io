package io.github.donggi.iroirobrowser.browser

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.OverScroller
import android.widget.TextView
import android.widget.Toast
import io.github.donggi.iroirobrowser.R
import io.github.donggi.iroirobrowser.core.adblock.HideSelectors
import io.github.donggi.iroirobrowser.ui.Ui
import java.util.Locale

/**
 * 요소 숨기기의 **고르는 상태**(사용자 요청, 2026-10-08 — uBlock Origin 의 '요소 선택기' 와 같은 종류). 지금 탭의 페이지 위에 덮개([PickOverlay])와
 * 아래 막대(고른 요소의 설명 · 넓게 · 좁게 · 취소 · 숨기기 — 플랫폼 뷰)를 둔다.
 *
 * - **누름을 앱이 가로챈다** — 덮개가 손가락을 모두 받으므로 고르는 동안 페이지에는 터치·누름이 가지 않는다(고리가 열리지 않고 페이지의
 *   스크립트가 누름에 반응하지 않는다). 누른 자리는 비율 좌표로 고르는 스크립트에 보내고([PageBridge.pickAt]), 끌거나 튕기면 그 자리의 스크롤
 *   상자(없으면 문서)를 스크립트가 민다([PageBridge.pickScroll] — 한 화면 그림마다 모아 보낸다).
 * - **고르는 스크립트**는 페이지가 볼 수 없는 실행 세계의 통로(BRIDGE 의 '요소 고르기')에 잠들어 있다가 이 탭의 주 틀에서 고르는 동안만 깬다.
 *   페이지의 DOM 은 바꾸지 않는다 — 테두리는 덮개가 그린다. 끝나면([stop]) 스크립트가 듣개·시계를 걷는다.
 * - '숨기기' 를 누르면 선택자 후보([HideSelectors.candidates])를 스크립트가 지금 페이지에서 재고, 고른 요소 하나만 맞는 첫 후보
 *   ([HideSelectors.choose])를 화면([Host.hide])이 규칙으로 저장하고 숨긴다. 고르기는 네트워크 요청을 내지 않는다.
 *
 * 끝나는 때 — 취소·뒤로·탭 바꾸기·새로 고침·주소창·찾기·전체 화면은 화면이 [stop] 을, 페이지가 스스로 바뀌면(새 문서) [endForNavigation] 을
 * 부른다(통로가 새 문서의 인사를 보고도 알린다 — [onPickEnded]).
 */
class ElementPicker(private val activity: Activity, private val host: Host) : PageBridge.PickEvents {

    interface Host {
        fun trace(message: String)

        /** 고른 요소를 숨긴다 — [selector] 는 지금 페이지에서 그 요소 하나만 가리킨다(스크립트가 쟀다). 넓은 요소면 화면이 한 번 더 묻는다. */
        fun hide(tab: Tab, selector: String, info: PickProtocol.Info)
    }

    /** '숨기기' 를 눌러 스크립트의 잼을 기다리는 것 — [serial] 이 그 사이 바뀌었으면(다른 것을 골랐다) 그 결과는 버린다. */
    private class PendingCheck(val serial: Int, val candidates: List<String>, val info: PickProtocol.Info)

    private val main = Handler(Looper.getMainLooper())

    /** 고르는 탭 — 고르는 중이 아니면 null. */
    var tab: Tab? = null
        private set
    private var bridge: PageBridge? = null
    private var info: PickProtocol.Info? = null

    /** 아무것도 고르지 않았을 때 막대의 글. */
    private var message = R.string.picker_prompt

    /** 고른 것이 바뀔 때마다 오른다. */
    private var serial = 0
    private var pending: PendingCheck? = null

    /** 끌기를 한 화면 그림마다 모아 보낸다(손가락의 움직임마다 보내면 통로가 붐빈다). */
    private var dragX = 0f
    private var dragY = 0f
    private var dragDx = 0f
    private var dragDy = 0f
    private var flushPosted = false

    val isActive: Boolean get() = tab != null

    private val overlay = PickOverlay(activity, object : PickOverlay.Listener {
        override fun onTap(x: Float, y: Float) = tap(x, y)

        override fun onDrag(x: Float, y: Float, dx: Float, dy: Float) = drag(x, y, dx, dy)
    })

    private val label = TextView(activity).apply {
        textSize = 14f
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        // 고른 것이 바뀌면 화면 낭독기가 읽는다.
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        setPadding(Ui.dp(activity, 8), 0, Ui.dp(activity, 8), Ui.dp(activity, 2))
    }

    private val wider = button(R.string.picker_wider, R.string.picker_wider_description) { bridge?.pickWider() }
    private val narrower = button(R.string.picker_narrower, R.string.picker_narrower_description) { bridge?.pickNarrower() }
    private val cancel = button(R.string.cancel, R.string.picker_cancel_description) {
        host.trace("pick cancel")
        stop()
    }
    private val hideButton = button(R.string.picker_hide, R.string.picker_hide_description) { askHide() }.apply {
        setTypeface(typeface, Typeface.BOLD)
    }

    private val bar = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        visibility = View.GONE
        // 막대의 빈 자리를 누른 것이 덮개로(페이지를 고르는 누름으로) 새지 않게. 틀 자신은 화면 낭독기의 멈출 자리가 아니다(글과 단추가 있다).
        isClickable = true
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        background = Ui.rounded(
            Ui.themeColor(activity, android.R.attr.colorBackground), 12, activity, Ui.themeColor(activity, android.R.attr.textColorSecondary),
        )
        elevation = Ui.dp(activity, 8).toFloat()
        setPadding(Ui.dp(activity, 8), Ui.dp(activity, 10), Ui.dp(activity, 4), Ui.dp(activity, 2))
        addView(label, Ui.matchWidth())
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(wider)
            addView(narrower)
            addView(View(activity), LinearLayout.LayoutParams(0, 0, 1f))
            addView(cancel)
            addView(hideButton)
        }
        addView(row, Ui.matchWidth())
    }

    private fun button(text: Int, description: Int, click: () -> Unit) = Button(activity, null, android.R.attr.borderlessButtonStyle).apply {
        this.text = activity.getString(text)
        contentDescription = activity.getString(description)
        minWidth = 0
        minimumWidth = 0
        setPadding(Ui.dp(activity, 12), paddingTop, Ui.dp(activity, 12), paddingBottom)
        setOnClickListener { click() }
    }

    /** 쪽의 자리(탭의 WebView 를 담은 판 위)에 덮개와 막대를 붙인다 — 덮개는 WebView 와 같은 크기여야 한다(비율 좌표). */
    fun attach(stage: FrameLayout) {
        stage.addView(overlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        stage.addView(
            bar,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
                val m = Ui.dp(activity, 8)
                setMargins(m, m, m, m)
            },
        )
    }

    /**
     * [tab] 에서 고르기를 시작한다 — [x]·[y](보이는 영역에 대한 비율)가 있으면 그 자리의 요소를 곧바로 고른다(길게 누르기 메뉴의 '이 요소
     * 숨기기'). 주 틀의 통로가 아직 없으면(페이지를 여는 중) false.
     */
    fun start(tab: Tab, bridge: PageBridge, x: Double? = null, y: Double? = null): Boolean {
        stop()
        if (!bridge.startPick(this)) return false
        this.tab = tab
        this.bridge = bridge
        info = null
        message = R.string.picker_prompt
        serial++
        overlay.show(null)
        overlay.visibility = View.VISIBLE
        bar.visibility = View.VISIBLE
        render()
        if (x != null && y != null) bridge.pickAt(x, y)
        host.trace("pick start tab=${tab.id}")
        return true
    }

    /** 고르기를 끝낸다 — 스크립트가 듣개를 걷고 덮개·막대가 사라진다. 고르는 중이 아니면 아무것도 하지 않는다. */
    fun stop() {
        if (tab == null) return
        bridge?.stopPick()
        tab = null
        bridge = null
        info = null
        serial++
        cancelCheck()
        overlay.reset()
        overlay.visibility = View.GONE
        bar.visibility = View.GONE
        dragDx = 0f
        dragDy = 0f
        host.trace("pick stop")
    }

    /** 고르던 페이지가 스스로 바뀌었다(새 문서) — 끝내고 알린다. [of] 가 고르는 탭이 아니거나 이미 끝났으면 아무것도 하지 않는다. */
    fun endForNavigation(of: Tab? = null) {
        val t = tab ?: return
        if (of != null && of != t) return
        stop()
        Toast.makeText(activity, R.string.picker_page_changed, Toast.LENGTH_SHORT).show()
    }

    // ── 덮개의 손가락 ─────────────────────────────────────────────────────────────────────────────────────────

    private fun tap(x: Float, y: Float) {
        val b = bridge ?: return
        val w = overlay.width
        val h = overlay.height
        if (w <= 0 || h <= 0) return
        b.pickAt((x / w).toDouble(), (y / h).toDouble())
    }

    private fun drag(x: Float, y: Float, dx: Float, dy: Float) {
        if (bridge == null) return
        dragX = x
        dragY = y
        dragDx += dx
        dragDy += dy
        if (!flushPosted) {
            flushPosted = true
            overlay.postOnAnimation(flush)
        }
    }

    private val flush = Runnable {
        flushPosted = false
        val b = bridge ?: return@Runnable
        val w = overlay.width
        val h = overlay.height
        if (w <= 0 || h <= 0 || (dragDx == 0f && dragDy == 0f)) return@Runnable
        b.pickScroll((dragX / w).toDouble(), (dragY / h).toDouble(), (dragDx / w).toDouble(), (dragDy / h).toDouble())
        dragDx = 0f
        dragDy = 0f
    }

    // ── 숨기기 ─────────────────────────────────────────────────────────────────────────────────────────────

    /** '숨기기' — 후보를 지어 스크립트가 지금 페이지에서 재게 한다. 답은 [onPickChecked]. */
    private fun askHide() {
        val i = info ?: return
        val b = bridge ?: return
        if (pending != null) return
        val candidates = HideSelectors.candidates(i.chain)
        if (candidates.isEmpty()) {
            Toast.makeText(activity, R.string.picker_no_selector, Toast.LENGTH_LONG).show()
            return
        }
        pending = PendingCheck(serial, candidates, i)
        b.checkSelectors(candidates)
        main.postDelayed(checkTimeout, CHECK_MS)
        render()
    }

    private val checkTimeout = Runnable {
        if (pending == null) return@Runnable
        pending = null
        host.trace("pick check timeout")
        Toast.makeText(activity, R.string.picker_no_answer, Toast.LENGTH_SHORT).show()
        render()
    }

    private fun cancelCheck() {
        pending = null
        main.removeCallbacks(checkTimeout)
    }

    // ── 통로의 알림(PageBridge.PickEvents) ───────────────────────────────────────────────────────────────────

    override fun onPicked(info: PickProtocol.Info) {
        if (!isActive) return
        this.info = info
        serial++
        cancelCheck()
        overlay.show(info.box)
        render()
        host.trace("pick ${HideSelectors.describe(info.target)} cover=${String.format(Locale.ROOT, "%.2f", info.box.cover())}")
    }

    override fun onPickMoved(box: PickProtocol.Box) {
        val i = info ?: return
        info = i.moved(box)
        overlay.show(box)
    }

    override fun onPickNone() = clearSelection(R.string.picker_nothing)

    override fun onPickGone() = clearSelection(R.string.picker_gone)

    private fun clearSelection(text: Int) {
        if (!isActive) return
        info = null
        serial++
        cancelCheck()
        message = text
        overlay.show(null)
        render()
    }

    override fun onPickChecked(checks: List<HideSelectors.Check>) {
        val p = pending ?: return
        cancelCheck()
        val t = tab ?: return
        // 재는 사이 다른 것을 골랐다 — 그 결과는 앞의 것의 것이다.
        if (p.serial != serial) {
            render()
            return
        }
        val selector = HideSelectors.choose(p.candidates, checks)
        host.trace("pick checked ${checks.joinToString(" ") { "${it.count}${if (it.hit) "+" else ""}" }} -> $selector")
        if (selector == null) {
            Toast.makeText(activity, R.string.picker_no_selector, Toast.LENGTH_LONG).show()
            render()
            return
        }
        host.hide(t, selector, p.info)
        render()
    }

    override fun onPickEnded() = endForNavigation()

    private fun render() {
        if (!isActive) return
        val i = info
        label.text = if (i != null) {
            activity.getString(R.string.picker_selected, HideSelectors.describe(i.target), i.cssWidth, i.cssHeight)
        } else {
            activity.getString(message)
        }
        wider.isEnabled = i?.canWiden == true
        narrower.isEnabled = i?.canNarrow == true
        hideButton.isEnabled = i != null && pending == null
    }

    private companion object {
        /** 스크립트가 후보를 재는 데 기다리는 시간 — 페이지의 주 스레드가 바쁘면 늦는다. */
        const val CHECK_MS = 4_000L
    }
}

/**
 * 고르는 동안 페이지 위에 덮는 판 — **손가락을 모두 받아** 페이지에 누름이 가지 않게 하고, 고른 요소의 자리([show] — 보이는 영역에 대한
 * 비율)에 테두리를 그린다(둘레는 옅게 어둡게 — 무엇을 골랐는지 보이게). 누르면 [Listener.onTap], 끌거나 튕기면 [Listener.onDrag](튕김은
 * [OverScroller] 로 이어 민다). 페이지의 DOM 에는 아무것도 넣지 않는다. 화면 낭독기로 두 번 누르면 가운데의 요소를 고른다.
 */
@SuppressLint("ViewConstructor") // 코드로만 만든다(배치 파일에서 쓰지 않는다).
class PickOverlay(context: Context, private val listener: Listener) : View(context) {

    interface Listener {
        fun onTap(x: Float, y: Float)

        /** 손가락이 [x]·[y](이 판의 픽셀)에서 페이지를 [dx]·[dy] 픽셀 밀었다 — 양수면 페이지의 아래·오른쪽이 들어온다. */
        fun onDrag(x: Float, y: Float, dx: Float, dy: Float)
    }

    private var box: PickProtocol.Box? = null
    private val accent = Ui.themeColor(context, android.R.attr.colorAccent)
    private val dim = Paint().apply { color = Color.argb(if (Ui.isNight(context)) 0x70 else 0x48, 0, 0, 0) }
    private val fill = Paint().apply { color = Color.argb(0x38, Color.red(accent), Color.green(accent), Color.blue(accent)) }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = Ui.dp(context, 2).toFloat()
        color = accent
    }
    private val shade = Path().apply { fillType = Path.FillType.EVEN_ODD }
    private val rect = RectF()

    /** 손가락이 누른 자리 — 없으면(화면 낭독기의 누름) 가운데. */
    private var tapX = Float.NaN
    private var tapY = Float.NaN
    private var tapped = false

    private val scroller = OverScroller(context)
    private var flingX = 0f
    private var flingY = 0f
    private var lastFlingX = 0
    private var lastFlingY = 0

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            stopFling()
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            tapX = e.x
            tapY = e.y
            tapped = true
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            listener.onDrag(e2.x, e2.y, distanceX, distanceY)
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            fling(e2.x, e2.y, velocityX, velocityY)
            return true
        }
    }).apply {
        // 두 번 누르기를 기다리지 않는다(한 번 누르면 곧바로 고른다). 길게 누르기도 쓰지 않는다.
        setOnDoubleTapListener(null)
        setIsLongpressEnabled(false)
    }

    init {
        contentDescription = context.getString(R.string.picker_overlay)
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        visibility = GONE
    }

    /** 테두리를 둘 자리 — null 이면 아직 고르지 않았다(판의 가장자리에 테두리 — 고르는 중임을 보인다). */
    fun show(box: PickProtocol.Box?) {
        this.box = box
        invalidate()
    }

    fun reset() {
        stopFling()
        tapped = false
        tapX = Float.NaN
        tapY = Float.NaN
        show(null)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        tapped = false
        gestures.onTouchEvent(event)
        if (tapped) performClick()
        return true
    }

    /** 누름 — 손가락이면 그 자리, 화면 낭독기의 '두 번 눌러 활성화' 면 가운데. */
    override fun performClick(): Boolean {
        super.performClick()
        val x = if (tapX.isNaN()) width / 2f else tapX
        val y = if (tapY.isNaN()) height / 2f else tapY
        tapX = Float.NaN
        tapY = Float.NaN
        listener.onTap(x, y)
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val b = box
        if (b == null) {
            val inset = edge.strokeWidth / 2
            canvas.drawRect(inset, inset, w - inset, h - inset, edge)
            return
        }
        rect.set((b.left * w).toFloat(), (b.top * h).toFloat(), (b.right * w).toFloat(), (b.bottom * h).toFloat())
        shade.rewind()
        shade.addRect(0f, 0f, w, h, Path.Direction.CW)
        shade.addRect(rect, Path.Direction.CW)
        canvas.drawPath(shade, dim)
        canvas.drawRect(rect, fill)
        canvas.drawRect(rect, edge)
    }

    /** 튕김 — 손가락의 속도로 페이지를 이어 민다(한 화면 그림마다 그만큼). 다음 누름이 멈춘다. */
    private fun fling(x: Float, y: Float, velocityX: Float, velocityY: Float) {
        stopFling()
        flingX = x
        flingY = y
        lastFlingX = 0
        lastFlingY = 0
        scroller.fling(0, 0, -velocityX.toInt(), -velocityY.toInt(), -FLING_RANGE, FLING_RANGE, -FLING_RANGE, FLING_RANGE)
        postOnAnimation(flingStep)
    }

    private val flingStep = object : Runnable {
        override fun run() {
            if (!scroller.computeScrollOffset()) return
            val dx = scroller.currX - lastFlingX
            val dy = scroller.currY - lastFlingY
            lastFlingX = scroller.currX
            lastFlingY = scroller.currY
            if (dx != 0 || dy != 0) listener.onDrag(flingX, flingY, dx.toFloat(), dy.toFloat())
            if (!scroller.isFinished) postOnAnimation(this)
        }
    }

    private fun stopFling() {
        scroller.forceFinished(true)
        removeCallbacks(flingStep)
    }

    private companion object {
        const val FLING_RANGE = 1_000_000
    }
}
