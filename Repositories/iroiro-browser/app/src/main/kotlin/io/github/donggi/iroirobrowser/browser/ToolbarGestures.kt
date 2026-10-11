package io.github.donggi.iroirobrowser.browser

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.LinearLayout
import kotlin.math.abs

/**
 * 막대(주소창 줄)의 몸짓 — **좌우로 밀면 앞·뒤 탭, 아래로 당기면 탭 목록**(사용자 요청, 2026-10-05). 크롬(`ToolbarSwipeHandler`)과
 * 파이어폭스(`ToolbarHorizontal/VerticalGesturesHandler`)가 같은 자리에서 하는 일을 따랐다(조사 — CLAUDE.md '탭·세션·막대의 몸짓').
 *
 * - 막대의 아이(주소창·단추)는 그대로 눌린다. 손가락이 움직여 **방향이 정해지는 순간에만** 몸짓을 가져온다 — 아이에게는 취소가 가므로 주소창이
 *   초점을 받지 않고 단추가 눌리지 않는다. 한 번 정하면 바꾸지 않는다.
 * - **좌우**: 슬롭을 넘고 가로축에서 ±25° 안(크롬). 손을 뗄 때까지 [Listener.onSwipe] 로 따라간다.
 * - **아래로**: 슬롭의 두 배(약 16 dp)를 넘고 세로축에서 ±25° 안이면 **그 자리에서** [Listener.onPullDown] 한 번 — 탭 목록을 곧바로 연다.
 *   두 엔진 모두 손가락을 따라오게 하지 않고 정해지는 즉시 연다. 그 뒤의 움직임은 삼킨다.
 * - 위로 미는 것·비스듬한 것은 아이의 것이다.
 *
 * 좌표는 화면 좌표(`rawX`·`rawY`)로 잰다 — 몸짓 중에 화면이 움직여도 흔들리지 않게.
 */
@SuppressLint("ViewConstructor")
class ToolbarGestureLayout(context: Context, private val listener: Listener) : LinearLayout(context) {

    interface Listener {
        /** 이 자리(이 뷰의 좌표)에서 시작한 몸짓을 받는가 — 주소창을 고치는 중이거나 스위치 위면 받지 않는다. */
        fun gesturesEnabled(x: Float, y: Float): Boolean

        /** 좌우로 미는 중 — [dx] 는 시작한 자리부터 옮긴 거리(오른쪽이 +). */
        fun onSwipe(dx: Float)

        /** 좌우로 밀다 손을 뗐다(또는 취소 — [cancelled]). [velocityX] 는 px/s. */
        fun onSwipeEnd(dx: Float, velocityX: Float, cancelled: Boolean)

        /** 아래로 당겼다 — 탭 목록을 연다. 몸짓 하나에 한 번. */
        fun onPullDown()
    }

    private enum class Mode { NONE, UNDECIDED, SWIPE, PULLED, IGNORED }

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var mode = Mode.NONE
    private var startX = 0f
    private var startY = 0f
    private var tracker: VelocityTracker? = null

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = track(ev)

    @SuppressLint("ClickableViewAccessibility") // 몸짓이다 — 누르기가 아니다(접근성의 길은 탭 단추·메뉴가 그대로 있다)
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        // 아이가 받지 않은 자리(막대의 여백)에서 시작하면 여기로 곧바로 온다 — 그때도 같은 판정을 한다.
        track(ev)
        return mode != Mode.IGNORED && mode != Mode.NONE || ev.actionMasked == MotionEvent.ACTION_DOWN
    }

    /** 몸짓을 잰다. 가져와야 하면(방향이 정해졌으면) true. */
    private fun track(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = ev.rawX
                startY = ev.rawY
                mode = if (listener.gesturesEnabled(ev.x, ev.y)) Mode.UNDECIDED else Mode.IGNORED
                tracker?.recycle()
                tracker = VelocityTracker.obtain()
                addMovement(ev)
            }
            MotionEvent.ACTION_MOVE -> {
                addMovement(ev)
                val dx = ev.rawX - startX
                val dy = ev.rawY - startY
                when (mode) {
                    Mode.UNDECIDED -> decide(dx, dy, ev)
                    Mode.SWIPE -> listener.onSwipe(dx)
                    else -> Unit
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                addMovement(ev)
                val t = tracker
                t?.computeCurrentVelocity(1000)
                if (mode == Mode.SWIPE) {
                    listener.onSwipeEnd(ev.rawX - startX, t?.xVelocity ?: 0f, ev.actionMasked == MotionEvent.ACTION_CANCEL)
                }
                t?.recycle()
                tracker = null
                mode = Mode.NONE
            }
        }
        return mode == Mode.SWIPE || mode == Mode.PULLED
    }

    /** 방향을 정한다 — 좌우·아래 가운데 어느 원뿔(±25°)에도 들 수 없게 되면 아이에게 넘긴다. */
    private fun decide(dx: Float, dy: Float, ev: MotionEvent) {
        val adx = abs(dx)
        val ady = abs(dy)
        val horizontal = ady <= adx * TAN_25
        val down = dy > 0 && adx <= dy * TAN_25
        when {
            horizontal && adx > slop -> {
                mode = Mode.SWIPE
                // 판정에 쓴 만큼은 빼고 시작한다 — 정해지는 순간 화면이 슬롭만큼 튀지 않게.
                startX += if (dx > 0) slop else -slop
                listener.onSwipe(ev.rawX - startX)
            }
            down && dy > 2 * slop -> {
                mode = Mode.PULLED
                listener.onPullDown()
            }
            (adx > slop || ady > slop) && !horizontal && !down -> mode = Mode.IGNORED
        }
    }

    /** 속도는 화면 좌표로 잰다(이 뷰의 좌표는 몸짓 중에 움직일 수 있다). */
    private fun addMovement(ev: MotionEvent) {
        val t = tracker ?: return
        val copy = MotionEvent.obtain(ev)
        copy.setLocation(ev.rawX, ev.rawY)
        t.addMovement(copy)
        copy.recycle()
    }

    private companion object {
        /** 방향을 정하는 원뿔의 반각 25°(크롬)의 탄젠트. */
        const val TAN_25 = 0.4663077f
    }
}
