package io.github.donggi.iroiroviewer.webhost

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.webkit.WebView

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
 */
@SuppressLint("ViewConstructor")
internal class ReaderWebView(context: Context) : WebView(context) {

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

    private var restore: ReadingScroll.Restore? = null

    private val retry = Runnable { applyRestore() }

    /** 새 쪽을 연다. 옮길 자리([start])를 먼저 걸어 둔다 — 쪽이 보이는 즉시 옮기기 시작한다. */
    fun open(url: String, start: Float?) {
        visible = false
        finished = false
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
