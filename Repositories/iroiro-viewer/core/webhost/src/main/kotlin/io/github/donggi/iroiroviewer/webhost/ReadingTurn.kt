package io.github.donggi.iroiroviewer.webhost

import kotlin.math.abs

/**
 * 글을 읽는 WebView 에서 **장을 넘기는 몸짓**(예전에는 아래 막대의 작은 화살표로만 넘겼다). 순수 함수라 JVM 시험이 답한다
 * (`ReadingTurnTest`). 몸짓을 받는 것은 [ReaderWebView] 다.
 *
 * ## 두 갈래 — 글이 흐르는가, 쪽 모양이 고정됐는가
 *
 * | 갈래 | 무엇에 | 몸짓 |
 * |---|---|---|
 * | [Mode.PULL] | EPUB 의 흐름 장 | 장의 끝에 **이미 닿은 채** 새로 밀어 [PULL_DP] 넘게 당겼다 놓으면 다음 장. 처음에서 거꾸로 당기면 앞 장(그 끝에서 연다) |
 * | [Mode.SWIPE] | 고정 레이아웃 EPUB 쪽 | 좌우로 [SWIPE_DP] 넘게 밀거나 화면 폭의 [EDGE] 가장자리를 두드린다. 오른쪽에서 왼쪽으로 넘기는 책이면 좌우를 뒤집는다 |
 *
 * **당기기는 끌기가 시작될 때 끝에 있었는지를 본다.** 끝까지 스크롤해 내려온 손가락이 그대로 다음 장으로 넘어가면 읽던 끝 줄을
 * 놓친다 — 확대한 사진에서 '가장자리에서 새로 밀면 넘긴다' 와 같은 규칙이다(`ZoomMath.claimsDrag`). 한 화면에 드는 장은 처음이자
 * 끝이라 양쪽으로 당겨진다. 확대돼 있으면 밀기·두드림으로 넘기지 않는다(만화의 가장자리 두드림과 같은 약속).
 */
object ReadingTurn {

    enum class Mode {
        /** 넘기는 몸짓이 없다(기본값 — 장을 넘기지 않는 화면: SVG·HTML·마크다운 미리보기). */
        NONE,
        PULL,
        SWIPE,
    }

    /** 가장자리 하나의 폭(화면 폭에 대한 비율). 만화의 가장자리 두드림과 같은 값이다. */
    const val EDGE = 0.3f

    /** 당겨 넘기는 문턱(dp). 당겨서 새로고침의 문턱과 비슷하게 — 스치는 손가락으로는 넘어가지 않을 만큼. */
    const val PULL_DP = 72f

    /** 밀어 넘기는 문턱(dp). */
    const val SWIPE_DP = 48f

    /** 끝에 닿았다고 보는 여유(화소). 스크롤 범위의 반올림을 덮는다. */
    private const val EDGE_SLACK_PX = 2

    /**
     * 읽는 차례로 **장의 끝**에 있는가. 세로는 아래 끝, 오른쪽에서 왼쪽으로 넘기는 가로(세로쓰기)는 **왼쪽 끝**, 왼쪽에서 오른쪽은
     * 오른쪽 끝이다([ReadingScroll] 의 '세로쓰기는 가로로, 오른쪽에서 잰다'). 축이 없으면(한 화면에 든다) 언제나 끝이다.
     */
    fun atEnd(axis: ReadingScroll.Axis?, scroll: Int, max: Int, rightToLeft: Boolean): Boolean = when (axis) {
        null -> true
        ReadingScroll.Axis.VERTICAL -> scroll >= max - EDGE_SLACK_PX
        ReadingScroll.Axis.HORIZONTAL -> if (rightToLeft) scroll <= EDGE_SLACK_PX else scroll >= max - EDGE_SLACK_PX
    }

    /** 읽는 차례로 **장의 처음**에 있는가. [atEnd] 의 거울이다. */
    fun atStart(axis: ReadingScroll.Axis?, scroll: Int, max: Int, rightToLeft: Boolean): Boolean = when (axis) {
        null -> true
        ReadingScroll.Axis.VERTICAL -> scroll <= EDGE_SLACK_PX
        ReadingScroll.Axis.HORIZONTAL -> if (rightToLeft) scroll >= max - EDGE_SLACK_PX else scroll <= EDGE_SLACK_PX
    }

    /**
     * 손가락이 움직인 거리([dx]·[dy], 화소)를 **읽는 차례의 당김**으로. 양수면 다음 장 쪽(장의 끝 너머로), 음수면 앞 장 쪽이다.
     * 세로는 손가락이 위로 갈수록, 오른쪽에서 왼쪽 가로는 오른쪽으로 갈수록(더 왼쪽 — 뒤의 줄을 보려는 것), 왼쪽에서 오른쪽 가로는
     * 왼쪽으로 갈수록 앞으로다. 축이 없으면 세로로 본다.
     */
    fun pullOf(axis: ReadingScroll.Axis?, rightToLeft: Boolean, dx: Float, dy: Float): Float = when (axis) {
        ReadingScroll.Axis.HORIZONTAL -> if (rightToLeft) dx else -dx
        ReadingScroll.Axis.VERTICAL, null -> -dy
    }

    /**
     * 당김의 진행 −1~1. 끌기가 끝에서 시작했고 앞으로 당기면 양수, 처음에서 시작했고 뒤로 당기면 음수, 그 밖에는 0 이다.
     * 1(또는 −1)에 닿은 채 손을 떼면 넘긴다([pullTurn]). 화면이 이 값으로 '놓으면 다음 장' 을 보인다.
     */
    fun pullProgress(startedAtEnd: Boolean, startedAtStart: Boolean, pull: Float, threshold: Float): Float {
        if (!(threshold > 0f) || !pull.isFinite()) return 0f
        return when {
            startedAtEnd && pull > 0f -> (pull / threshold).coerceAtMost(1f)
            startedAtStart && pull < 0f -> (pull / threshold).coerceAtLeast(-1f)
            else -> 0f
        }
    }

    /** 손을 뗐을 때 넘기는가 — 1 다음, −1 앞, 0 그대로. */
    fun pullTurn(progress: Float): Int = when {
        progress >= 1f -> 1
        progress <= -1f -> -1
        else -> 0
    }

    /**
     * 좌우 밀기 — 1 다음, −1 앞, 0 아니다. 가로로 [threshold] 넘게, 그리고 세로의 두 배 넘게 움직였을 때만이다(비스듬히 끈 것은
     * 넘기기가 아니다). 왼쪽에서 오른쪽으로 넘기는 책은 손가락이 왼쪽으로 가면 다음 쪽, 오른쪽에서 왼쪽이면 거꾸로다.
     */
    fun swipeTurn(dx: Float, dy: Float, threshold: Float, rightToLeft: Boolean): Int {
        if (!dx.isFinite() || !dy.isFinite()) return 0
        if (abs(dx) <= threshold || abs(dx) <= 2f * abs(dy)) return 0
        val leftward = dx < 0f
        return if (leftward != rightToLeft) 1 else -1
    }

    /** 가장자리 두드림 — 1 다음, −1 앞, 0 가운데. 오른쪽에서 왼쪽으로 넘기는 책이면 왼쪽 가장자리가 다음이다. */
    fun tapTurn(x: Float, width: Float, rightToLeft: Boolean): Int {
        if (!(width > 0f) || !x.isFinite() || !width.isFinite()) return 0
        val f = x / width
        val side = when {
            f < EDGE -> -1
            f > 1f - EDGE -> 1
            else -> return 0
        }
        return if (rightToLeft) -side else side
    }
}
