package io.github.donggi.iroiroviewer.webhost

import android.annotation.SuppressLint
import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.webkit.WebView
import kotlin.math.roundToInt

/**
 * 읽던 자리를 **Kotlin 에서** 재고 옮기는 WebView.
 *
 * ## 왜 하위 클래스인가
 *
 * 스크롤 범위(`computeVerticalScrollRange` 등)는 `View` 의 `protected` 함수다. 스크립트를 끈 WebView 에서 문서의 길이를
 * 아는 길이 이것뿐이다 — `contentHeight` 는 CSS 화소라 배율을 곱해야 하고, 가로(세로쓰기 장)의 길이는 아예 주지 않는다.
 *
 * ## 옮기기는 배치가 끝난 뒤에
 *
 * 쪽이 처음 보인 순간(`onPageCommitVisible`)에는 아직 글이 다 배치되지 않아 범위가 작다. 그때 비율로 옮기면 앞쪽에
 * 떨어진다. [ReadingScroll.Restore] 가 정한 대로 짧은 간격으로 다시 옮기고, 사용자가 만지면 그만둔다.
 *
 * **옮기는 동안에는 자리를 알리지 않는다.** 배치가 덜 된 쪽의 스크롤(대개 0)을 알리면 저장된 자리가 그 값으로 덮인다.
 *
 * ## 자리와 본 몫
 *
 * 알리는 것이 둘이다 — 화면 **앞 가장자리**의 자리([onFraction], 되살릴 곳)와 **끝 가장자리**까지 본 몫([onSeen], 진행).
 * 한 화면에 드는 쪽은 자리가 언제나 0 이지만 본 몫은 1 이다([ReadingScroll.seenOf]). 본 몫은 쪽이 다 읽힌 뒤에만 알린다 —
 * 배치가 덜 된 쪽은 범위가 작아 본 몫이 부풀어 보인다.
 *
 * ## 장을 넘기는 몸짓
 *
 * [turnMode] 를 주면 손가락으로 장을 넘긴다 — 흐르는 글은 끝에서 더 당겨([ReadingTurn.Mode.PULL]), 쪽 모양이 고정된 것은 좌우로
 * 밀거나 가장자리를 두드려([ReadingTurn.Mode.SWIPE]). 판단은 [ReadingTurn] 이 하고 여기서는 손가락을 잰다. WebView 의 스크롤·확대는
 * 그대로 둔다(이벤트를 삼키지 않는다) — 끝에 닿은 쪽은 더 스크롤되지 않으니 당김과 다투지 않는다.
 */
@SuppressLint("ViewConstructor")
internal class ReaderWebView(context: Context) : WebView(context) {

    private companion object {
        /** 배율의 떨림을 확대로 치지 않는 여유(1%). */
        const val ZOOMED_SLACK = 1.01f
    }

    /** 오른쪽에서 왼쪽으로 넘기는 책인가. 가로로 넘치는 쪽(세로쓰기)을 오른쪽 끝에서 잰다. */
    var rightToLeft: Boolean = false

    /** 사용자가 옮긴 자리(0~1). 옮기기가 끝난 뒤에만 부른다. */
    var onFraction: ((Float) -> Unit)? = null

    /** 화면 끝 가장자리까지 본 몫(0~1). 쪽이 다 읽힌 뒤, 스크롤과 옮기기가 끝날 때 부른다. */
    var onSeen: ((Float) -> Unit)? = null

    /** 지금 쪽이 처음 보였는가. 그 전의 스크롤은 **앞 쪽의 것**이라 알리지 않는다. */
    var visible: Boolean = false

    /** 지금 쪽이 다 읽혔는가(`onPageFinished`). */
    var finished: Boolean = false

    /** 마지막으로 알린(또는 옮겨 둔) 자리. 크기가 바뀌면 이리로 되돌아온다. */
    var lastFraction: Float? = null
        private set

    /** 마지막으로 받아들인 '같은 쪽 안에서 옮겨라' 요청의 번호. */
    var appliedJump: Int = Int.MIN_VALUE

    /** 마지막으로 청한 주소. `url` 은 WebView 가 고쳐 쓴 모양이라 견줄 수 없다(`LockedWebView` 의 주석). */
    var requestedUrl: String? = null
        private set

    /** 마지막으로 읽은 모양의 열쇠(`LockedWebView.contentKey`). 바뀌면 다시 읽는다. */
    var contentKey: Any? = null

    /** 렌더러가 죽어 이미 부순 뷰인가(`LockedWebView` 의 '렌더러가 죽으면'). 그 뒤로는 아무것도 부르지 않는다. */
    var dead: Boolean = false

    /**
     * 확대와 두 손가락 몸짓을 **부모에게 빼앗기지 않는다**(`LockedWebView.holdsGestures`). 켜면 두 손가락이 닿았거나 확대된 동안 부모에게
     * 가로채기를 막아 달라고 한다 — Compose 의 `AndroidView` 는 그 요청을 받으면 움직임을 부모보다 먼저 이 뷰에 주고 소비한다. 그러지
     * 않으면 페이저가 손가락 하나의 가로 움직임을 넘기기로 읽어 WebView 의 핀치를 중간에 끊는다(SVG 쪽에서 실측 — 가로 핀치가 거의
     * 확대되지 않았다). 화면에 맞춘 채 한 손가락으로 미는 것은 부모(넘기기·끌어 닫기)에 맡긴다.
     */
    var holdsGestures: Boolean = false

    /** 지금 확대돼 있는가 — 지금까지 본 가장 작은 배율(화면에 맞춘 배율)보다 크다. [onScaleChanged] 가 정한다. */
    var zoomedIn: Boolean = false
        private set

    /** 지금까지 본 가장 작은 배율. 쪽의 `minimum-scale` 이 1 이라 그것이 화면에 맞춘 배율이다(돌려도 같다 — `device-width`). */
    private var minScale = Float.MAX_VALUE

    /** 마지막으로 알려진 배율([onScaleChanged]). 아직 모르면 0. */
    private var scale = 0f

    /**
     * 두 번 두드리면 화면에 맞춘 배율의 이만큼으로 키운다(`LockedWebView.doubleTapZoom`). null 이면 하지 않는다.
     * **WebView 스스로는 두 번 두드림으로 확대하지 않는다** — 이 호스트의 설정에서 SVG 쪽을 두 번 두드려도 배율이 그대로였다
     * (에뮬레이터 실측. `useWideViewPort` 를 끈 탓으로 보이나 확인하지 않았다).
     */
    var doubleTapZoom: Float? = null

    private val doubleTaps by lazy(LazyThreadSafetyMode.NONE) {
        GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val zoom = doubleTapZoom ?: return false
                toggleZoom(zoom, e.x, e.y)
                return true
            }
        })
    }

    // ---- 장을 넘기는 몸짓(머리말) ----

    /** 넘기는 몸짓의 갈래. [ReadingTurn.Mode.NONE] 이면 아무것도 하지 않는다. */
    var turnMode: ReadingTurn.Mode = ReadingTurn.Mode.NONE

    /** 쪽을 넘기는 방향이 오른쪽에서 왼쪽인가(밀기·두드림). 스크롤 축을 정하는 [rightToLeft] 와 따로다 — 고정 쪽은 스크롤이 없다. */
    var turnRightToLeft: Boolean = false

    /** 넘겼다 — 참이면 다음 장. 경계(첫 장·끝 장)는 부르는 쪽이 거른다. */
    var onTurn: ((forward: Boolean) -> Unit)? = null

    /** 당김의 진행(−1~1, 0 이면 걷혔다 — [ReadingTurn.pullProgress]). 화면이 '놓으면 다음 장' 을 보인다. */
    var onPull: ((Float) -> Unit)? = null

    private var touchDownX = 0f
    private var touchDownY = 0f
    private var touchMulti = false
    private var pullFromEnd = false
    private var pullFromStart = false
    private var pullProgress = 0f

    private val turnTaps by lazy(LazyThreadSafetyMode.NONE) {
        GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (turnMode != ReadingTurn.Mode.SWIPE || zoomedIn) return false
                // 링크를 누른 것이면 넘기지 않는다 — 링크는 WebView 가 따른다(장 사이 링크·각주).
                val hit = hitTestResult.type
                if (hit == WebView.HitTestResult.SRC_ANCHOR_TYPE || hit == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) return false
                val turn = ReadingTurn.tapTurn(e.x, width.toFloat(), turnRightToLeft)
                if (turn != 0) onTurn?.invoke(turn > 0)
                return turn != 0
            }
        })
    }

    /** 지금 자리가 읽는 차례로 장의 끝·처음인가. [currentFraction] 과 같은 축으로 잰다. */
    private fun turnEdges(): Pair<Boolean, Boolean> {
        val mx = maxX()
        val my = maxY()
        val axis = ReadingScroll.axisOf(mx, my, rightToLeft)
        val (scroll, max) = when (axis) {
            ReadingScroll.Axis.VERTICAL -> scrollY to my
            ReadingScroll.Axis.HORIZONTAL -> scrollX to mx
            null -> 0 to 0
        }
        return ReadingTurn.atEnd(axis, scroll, max, rightToLeft) to ReadingTurn.atStart(axis, scroll, max, rightToLeft)
    }

    private fun setPull(progress: Float) {
        if (progress == pullProgress) return
        pullProgress = progress
        onPull?.invoke(progress)
    }

    /** 손가락 하나를 잰다. 이벤트를 소비하지 않는다 — WebView 의 스크롤·링크·확대는 그대로 돈다. */
    private fun trackTurn(event: MotionEvent) {
        val density = resources.displayMetrics.density
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.x
                touchDownY = event.y
                touchMulti = false
                setPull(0f)
                if (turnMode == ReadingTurn.Mode.PULL) {
                    // 끌기가 **시작될 때** 끝에 있었는가 — 끝까지 스크롤해 온 손가락은 그대로 넘어가지 않는다(ReadingTurn 머리말).
                    val (end, start) = turnEdges()
                    pullFromEnd = end
                    pullFromStart = start
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                touchMulti = true
                setPull(0f)
            }
            MotionEvent.ACTION_MOVE -> if (turnMode == ReadingTurn.Mode.PULL && !touchMulti) {
                val pull = ReadingTurn.pullOf(
                    ReadingScroll.axisOf(maxX(), maxY(), rightToLeft),
                    rightToLeft,
                    event.x - touchDownX,
                    event.y - touchDownY,
                )
                setPull(ReadingTurn.pullProgress(pullFromEnd, pullFromStart, pull, ReadingTurn.PULL_DP * density))
            }
            MotionEvent.ACTION_UP -> {
                if (!touchMulti) {
                    val turn = when (turnMode) {
                        ReadingTurn.Mode.PULL -> ReadingTurn.pullTurn(pullProgress)
                        ReadingTurn.Mode.SWIPE -> if (zoomedIn) 0 else ReadingTurn.swipeTurn(
                            event.x - touchDownX,
                            event.y - touchDownY,
                            ReadingTurn.SWIPE_DP * density,
                            turnRightToLeft,
                        )
                        ReadingTurn.Mode.NONE -> 0
                    }
                    setPull(0f)
                    if (turn != 0) onTurn?.invoke(turn > 0)
                } else {
                    setPull(0f)
                }
            }
            MotionEvent.ACTION_CANCEL -> setPull(0f)
        }
    }

    /** 앞의 배율이 크기 없는 뷰에서 온 것인가([onScaleChanged]). 그렇다면 다음 알림의 '옛 배율' 도 믿지 않는다. */
    private var lastScaleUnsized = false

    /**
     * `WebViewClient.onScaleChanged` 가 부른다.
     *
     * **크기가 없는 뷰가 알린 배율은 기준에 넣지 않는다.** 페이저는 옆 장을 크기 0 으로 먼저 세우는데, 그동안 WebView 는 가장 작은
     * 배율(맞춤의 1/4)로 갔다고 알리고 자리를 잡은 뒤에 맞춤으로 돌아온다(폰 에뮬레이터 실측 — 가로로 긴 SVG 쪽에서 `0×0` 에서
     * 2.625 → 0.656, `1080×2400` 에서 0.656 → 2.625). 그 0.656 을 '본 가장 작은 배율' 로 두었더니 맞춤 상태가 확대로 읽혀
     * [holdsGestures] 가 페이저의 넘기기를 막았다 — 그 장에서 다음 장으로 넘어가지 않았다.
     */
    fun onScaleChanged(oldScale: Float, newScale: Float) {
        scale = newScale
        if (width == 0 || height == 0) {
            lastScaleUnsized = true
            zoomedIn = false
            return
        }
        minScale = if (lastScaleUnsized) minOf(minScale, newScale) else minOf(minScale, oldScale, newScale)
        lastScaleUnsized = false
        zoomedIn = newScale > minScale * ZOOMED_SLACK
    }

    /**
     * 두 번 두드림의 두 칸 — 화면에 맞춘 채면 [zoom] 배로 키우고, 확대돼 있으면 맞춘 배율로 돌린다(그림 뷰어의
     * `ZoomMath.doubleTapTarget` 과 같은 약속이다). **키울 때는 두드린 점을 손가락 밑에 둔다** — `zoomBy` 는 뷰 가운데를 축으로
     * 키우므로 축과 두드린 점의 거리에 (배율 − 1)을 곱한 만큼 민다. 범위 밖이면 WebView 가 자른다.
     */
    private fun toggleZoom(zoom: Float, x: Float, y: Float) {
        // 확대돼 있다는 것은 배율을 한 번은 받았다는 뜻이다([onScaleChanged] 만 [zoomedIn] 을 켠다) — 나눗셈의 두 값을 안다.
        val factor = if (zoomedIn) minScale / scale else zoom
        // `zoomBy` 는 이 범위 밖의 값에 예외를 던진다(문서의 약속).
        if (!factor.isFinite() || factor < 0.01f || factor > 100f) return
        zoomBy(factor)
        if (factor > 1f) {
            scrollBy(((x - width / 2f) * (factor - 1f)).roundToInt(), ((y - height / 2f) * (factor - 1f)).roundToInt())
        }
    }

    private var restore: ReadingScroll.Restore? = null

    private val retry = Runnable { applyRestore() }

    /** 새 쪽을 연다. 옮길 자리([start])를 먼저 걸어 둔다 — 쪽이 보이는 즉시 옮기기 시작한다. */
    fun open(url: String, start: Float?) {
        visible = false
        finished = false
        zoomedIn = false
        minScale = Float.MAX_VALUE
        scale = 0f
        lastScaleUnsized = false
        cancelRestore()
        restore = start?.let { ReadingScroll.Restore(it) }
        lastFraction = start
        requestedUrl = url
        loadUrl(url)
    }

    /** 같은 쪽을 다시 읽는다(모양이 바뀌었다). 지금 자리로 돌아온다. */
    fun reloadKeepingPlace() {
        val at = currentFraction()
        visible = false
        finished = false
        cancelRestore()
        restore = at?.let { ReadingScroll.Restore(it) }
        reload()
    }

    /**
     * 같은 쪽 안에서 [fraction] 으로 옮긴다.
     *
     * @param relayout 이제 글이 **다시 흐른다**(글자 크기·화면 크기). 범위가 바뀐 뒤에야 멈춘다([ReadingScroll.Restore]).
     */
    fun moveTo(fraction: Float, relayout: Boolean = false) {
        cancelRestore()
        restore = ReadingScroll.Restore(fraction, awaitChange = relayout)
        lastFraction = fraction
        if (visible) applyRestore()
    }

    /** 지금 자리를 잡아 두고, 배치가 바뀐 뒤(글자 크기) 그리로 되돌아온다. */
    fun keepPlace() {
        val at = currentFraction() ?: return
        moveTo(at, relayout = true)
    }

    /** 쪽이 처음 보였다. */
    fun onVisible() {
        visible = true
        if (restore != null) applyRestore()
    }

    /** 쪽이 다 읽혔다. */
    fun onFinished() {
        visible = true
        finished = true
        // 옮기는 중이면 옮기기가 끝날 때 알린다(아래 [applyRestore]). 한 화면에 드는 쪽은 스크롤이 없어 여기가 유일한 때다.
        if (restore != null) applyRestore() else reportSeen()
    }

    /**
     * 지금 자리. 한 화면에 들어 스크롤할 것이 없으면 0. 아직 쪽이 보이지 않았으면 마지막으로 안 자리(없으면 null).
     *
     * **옮기는 중이면 그 목표가 곧 자리다** — 배치가 덜 된 지금의 스크롤(대개 0)은 뜻이 없다. 되살리는 도중에 글자 크기를
     * 바꾸거나 화면을 돌리면 그 값을 잡아 두었다가 처음으로 떨어진다.
     */
    fun currentFraction(): Float? {
        restore?.let { return it.fraction }
        if (!visible) return lastFraction
        val mx = maxX()
        val my = maxY()
        return when (ReadingScroll.axisOf(mx, my, rightToLeft)) {
            ReadingScroll.Axis.VERTICAL -> ReadingScroll.fractionOf(scrollY, my, fromEnd = false)
            ReadingScroll.Axis.HORIZONTAL -> ReadingScroll.fractionOf(scrollX, mx, fromEnd = rightToLeft)
            null -> 0f
        }
    }

    /** 지금 본 몫([ReadingScroll.seenOf]). 쪽이 다 읽히기 전에는 알리지 않는다(머리말). */
    private fun reportSeen() {
        val callback = onSeen ?: return
        if (!finished) return
        val mx = maxX()
        val my = maxY()
        val seen = when (ReadingScroll.axisOf(mx, my, rightToLeft)) {
            ReadingScroll.Axis.VERTICAL ->
                ReadingScroll.seenOf(scrollY, my, computeVerticalScrollExtent(), fromEnd = false)
            ReadingScroll.Axis.HORIZONTAL ->
                ReadingScroll.seenOf(scrollX, mx, computeHorizontalScrollExtent(), fromEnd = rightToLeft)
            null -> 1f
        }
        callback(seen)
    }

    fun cancelRestore() {
        restore = null
        removeCallbacks(retry)
    }

    private fun applyRestore() {
        val r = restore ?: return
        removeCallbacks(retry)
        val mx = maxX()
        val my = maxY()
        val max = when (ReadingScroll.axisOf(mx, my, rightToLeft)) {
            ReadingScroll.Axis.VERTICAL -> {
                scrollTo(scrollX, ReadingScroll.positionOf(r.fraction, my, fromEnd = false))
                my
            }
            ReadingScroll.Axis.HORIZONTAL -> {
                scrollTo(ReadingScroll.positionOf(r.fraction, mx, fromEnd = rightToLeft), scrollY)
                mx
            }
            null -> 0
        }
        if (r.again(max, finished)) {
            postDelayed(retry, ReadingScroll.RETRY_MS)
        } else {
            restore = null
            lastFraction = r.fraction
            // 자리는 알리지 않는다(청한 곳이 곧 자리다 — 기록을 옮기기의 결과로 덮지 않는다). 본 몫은 알린다.
            reportSeen()
        }
    }

    private fun maxX(): Int = (computeHorizontalScrollRange() - computeHorizontalScrollExtent()).coerceAtLeast(0)

    private fun maxY(): Int = (computeVerticalScrollRange() - computeVerticalScrollExtent()).coerceAtLeast(0)

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        if (restore != null || !visible) return
        val f = currentFraction() ?: return
        lastFraction = f
        onFraction?.invoke(f)
        reportSeen()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // **손으로 옮기기 시작하면 우리 옮기기를 버린다.** 되풀이하는 옮기기가 사용자가 밀어 둔 자리를 되돌리면 화면이
        // 손가락과 싸운다.
        if (event.actionMasked == MotionEvent.ACTION_DOWN) cancelRestore()
        if (holdsGestures && (event.pointerCount > 1 || zoomedIn)) parent?.requestDisallowInterceptTouchEvent(true)
        if (doubleTapZoom != null) doubleTaps.onTouchEvent(event)
        if (turnMode != ReadingTurn.Mode.NONE) {
            trackTurn(event)
            if (turnMode == ReadingTurn.Mode.SWIPE) turnTaps.onTouchEvent(event)
        }
        return super.onTouchEvent(event)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        // 회전하면 글이 다시 흘러 같은 화소가 다른 곳을 가리킨다. 보던 자리(비율)로 돌아온다.
        if (ow > 0 && oh > 0 && (w != ow || h != oh) && restore == null) {
            lastFraction?.let { moveTo(it, relayout = true) }
        }
    }

    override fun onDetachedFromWindow() {
        cancelRestore()
        super.onDetachedFromWindow()
    }
}
