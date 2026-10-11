package io.github.donggi.iroiroviewer.comic

/**
 * 만화의 **가장자리 두드림**. 순수 함수라 JVM 시험이 답한다.
 *
 * 화면 폭의 [EDGE] 씩이 양쪽 가장자리다. 가장자리는 넘기고 가운데는 막대를 여닫는다 — 만화·전자책 뷰어(Mihon·Perfect Viewer·
 * Play 북)가 몸에 익혀 둔 관행이다. 예전에는 모든 두드림이 막대 토글이라 화면 끝을 두드려 넘기려 하면 막대만 깜빡였다.
 *
 * - **오른쪽에서 왼쪽으로 읽는 책이면 좌우를 뒤집는다** — 왼쪽 가장자리가 다음 쪽이다(종이책을 넘기는 손의 방향).
 * - **확대돼 있으면 어디를 두드려도 막대다** — 확대한 그림의 가장자리를 짚었다고 쪽이 넘어가면 보던 자리를 잃는다.
 * - 세로 스크롤(웹툰)에는 좌우의 뜻이 없으므로 오른쪽이 앞(아래로 한 화면 [SCROLL_STEP] 만큼), 왼쪽이 뒤다.
 *
 * 볼륨 키(설정)도 같은 넘기기를 부른다 — 아래 키가 앞이다(`ViewerWindowHost`).
 */
internal object EdgeTaps {

    enum class Action { PREVIOUS, NEXT, CHROME }

    /** 가장자리 하나의 폭(화면 폭에 대한 비율). */
    const val EDGE = 0.3f

    /**
     * 웹툰에서 한 번 넘길 때 내리는 양(화면 높이에 대한 비율). 한 화면보다 조금 적게 — 넘긴 뒤에도 앞 화면의 끝자락이 위에 남아
     * 어디까지 읽었는지 잃지 않는다.
     */
    const val SCROLL_STEP = 0.8f

    /**
     * [x] 를 두드렸을 때 할 일. [width] 는 두드린 상자의 폭이다(같은 좌표계). 잴 수 없으면(폭 0·무한) 막대다.
     */
    fun of(x: Float, width: Float, rightToLeft: Boolean, zoomed: Boolean): Action {
        if (zoomed || !(width > 0f) || !x.isFinite() || !width.isFinite()) return Action.CHROME
        val f = x / width
        return when {
            f < EDGE -> if (rightToLeft) Action.NEXT else Action.PREVIOUS
            f > 1f - EDGE -> if (rightToLeft) Action.PREVIOUS else Action.NEXT
            else -> Action.CHROME
        }
    }

    /**
     * 넘길 칸. 끝에서 더 갈 곳이 없으면 null — 화면이 '첫 쪽입니다'·'마지막 쪽입니다' 를 알린다.
     *
     * @param current 지금 칸(페이저의 칸 — 두 쪽 보기면 펼침).
     * @param count 칸의 수.
     */
    fun target(current: Int, count: Int, forward: Boolean): Int? {
        if (count <= 0) return null
        val next = if (forward) current + 1 else current - 1
        return next.takeIf { it in 0 until count }
    }
}
