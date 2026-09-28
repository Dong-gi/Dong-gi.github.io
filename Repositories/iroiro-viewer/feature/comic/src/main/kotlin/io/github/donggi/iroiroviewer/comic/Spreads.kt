package io.github.donggi.iroiroviewer.comic

import io.github.donggi.iroiroviewer.safety.ImageLimits
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 한 화면에 쪽을 몇 장 놓는가(14단계). 화면 문구는 `strings.xml` 에 있다 — 여기는 값뿐이다.
 *
 * **세션 동안만 기억한다**(책을 바꿔도 남는다). 앱 설정(`AppPreferences`)에 둘 자리가 아직 없어서다 —
 * `core:data` 에 키가 생기면 VM 이 그 값을 읽으면 된다(CLAUDE.md 의 14단계 만화 절).
 */
enum class PageLayout {
    /** 언제나 한 쪽. */
    SINGLE,

    /** 언제나 두 쪽(세로 화면에서도). */
    DOUBLE,

    /** 가로 화면이면 두 쪽, 세로 화면이면 한 쪽. **기본값이다.** */
    AUTO,
}

/**
 * 쪽을 **펼침**(한 화면)으로 묶는 규칙. **순수 함수다.**
 *
 * ## 쪽 번호는 언제나 '쪽' 이다
 *
 * 펼침은 **보이는 모양**일 뿐이다. 슬라이더·`쪽으로 가기`·이어보기·저장되는 쪽 번호는 전부 쪽을 뜻하고,
 * 펼침 번호는 페이저 안에서만 산다 — 9단계가 읽는 방향을 `reverseLayout` 하나로 끝낸 것과 같은 이유다.
 * 펼침 번호를 저장하면 한 쪽 보기로 바꾼 순간 이어보기가 절반 자리를 가리킨다.
 *
 * ## 첫 쪽은 혼자 선다(표지)
 *
 * 만화책은 첫 쪽이 표지이고 본문은 둘째 쪽부터 맞쪽이다 — 종이책에서 표지를 넘기면 2·3쪽이 함께 펼쳐진다.
 * 그래서 `[0] [1,2] [3,4] …` 로 묶고, 쪽 수가 짝수면 마지막 쪽도 혼자 남는다. 표지를 짝에 넣으면(`[0,1] [2,3]`)
 * 본문의 맞쪽이 모두 한 칸씩 어긋나 한 장짜리 양면 그림이 두 화면에 갈라진다.
 *
 * ## 넓은 쪽(이미 양면으로 스캔한 쪽)을 혼자 두지 않는다
 *
 * 짝이 쪽의 **치수**에 달리면 치수를 알아내는 동안 짝이 바뀌어 보던 자리가 흔들린다 — 그리고 solid 아카이브에서
 * 치수를 알려면 패스가 돈다. 짝은 **번호만으로** 정한다. 넓은 쪽이 짝에 들면 두 쪽을 같은 높이로 맞춰 나란히
 * 놓으므로([SpreadMath]) 넓은 쪽이 폭을 더 가져갈 뿐 잘리지는 않는다.
 */
object Spreads {

    /** 이 뷰포트에서 두 쪽을 놓는가. */
    fun twoUp(layout: PageLayout, viewportWidth: Int, viewportHeight: Int): Boolean = when (layout) {
        PageLayout.SINGLE -> false
        PageLayout.DOUBLE -> true
        PageLayout.AUTO -> viewportWidth > viewportHeight
    }

    /** 펼침의 수. */
    fun count(pageCount: Int, twoUp: Boolean): Int {
        if (pageCount <= 0) return 0
        if (!twoUp) return pageCount
        // 표지 하나 + 나머지를 둘씩(홀수면 마지막이 혼자).
        return 1 + pageCount / 2
    }

    /**
     * 쪽 [page] 가 든 펼침.
     *
     * 범위 밖의 쪽은 가장 가까운 펼침으로 누른다 — 이어보기가 옛 쪽 수로 저장한 값이 와도 페이저가 죽지 않는다.
     */
    fun spreadOf(page: Int, pageCount: Int, twoUp: Boolean): Int {
        if (pageCount <= 0) return 0
        val p = page.coerceIn(0, pageCount - 1)
        if (!twoUp) return p
        return (p + 1) / 2
    }

    /**
     * 펼침 [index] 가 담는 쪽, **읽는 차례로**(앞 쪽이 먼저). 한 쪽이면 원소가 하나다.
     *
     * 화면의 왼쪽·오른쪽은 [visualOrder] 가 정한다.
     */
    fun pagesOf(index: Int, pageCount: Int, twoUp: Boolean): List<Int> {
        if (pageCount <= 0 || index < 0) return emptyList()
        if (!twoUp) return if (index < pageCount) listOf(index) else emptyList()
        if (index == 0) return listOf(0)
        val first = 2 * index - 1
        if (first >= pageCount) return emptyList()
        val second = first + 1
        return if (second < pageCount) listOf(first, second) else listOf(first)
    }

    /**
     * 화면의 **왼쪽부터** 놓을 차례.
     *
     * 오른쪽에서 왼쪽으로 읽는 책은 앞 쪽이 **오른쪽**에 선다 — 일본 만화의 맞쪽이 그렇게 인쇄된다.
     * 펼침 사이의 차례는 페이저의 `reverseLayout` 이 뒤집으므로 여기서는 펼침 안만 본다.
     */
    fun visualOrder(pages: List<Int>, rightToLeft: Boolean): List<Int> =
        if (rightToLeft && pages.size == 2) listOf(pages[1], pages[0]) else pages

    /**
     * 페이저가 펼침 [spread] 에 섰을 때 VM 의 쪽을 어떻게 할 것인가.
     *
     * **지금 쪽이 그 펼침 안이면 그대로 둔다.** 슬라이더로 3쪽을 고르면 `[2,3]` 펼침이 서는데, 그때 쪽을 펼침의 첫 쪽(2)
     * 으로 되돌리면 사용자가 고른 값이 한 칸 밀리고 이어보기에도 그 값이 남는다. 다른 펼침으로 넘어갔을 때만 그 펼침의
     * 첫 쪽이 새 쪽이다.
     */
    fun pageAfterSettle(current: Int, spread: Int, pageCount: Int, twoUp: Boolean): Int {
        val pages = pagesOf(spread, pageCount, twoUp)
        if (pages.isEmpty()) return current
        return if (current in pages) current else pages.first()
    }

    /**
     * solid 아카이브의 창이 **뛰어든 자리**(슬라이더·쪽으로 가기·쪽 목록·이어보기)에서 몇 쪽 앞에서 시작해야 하는가.
     *
     * 페이저는 앞뒤 한 칸씩을 함께 띄운다. 한 쪽 보기면 앞 칸이 한 쪽이라 1이다(9단계의 `LOOK_BEHIND` — 창을 요청한
     * 쪽에서 정확히 시작했더니 바로 다음 요청인 앞 쪽이 창 밖이라 패스가 두 번 돌았다). 두 쪽 보기면 **앞 칸이 두 쪽**이고,
     * 가장 먼저 청하는 것이 지금 펼침의 **뒤 쪽**일 수 있다 — 화면 왼쪽을 먼저 뜨는데, 오른쪽에서 왼쪽으로 읽으면 왼쪽이
     * 뒤 쪽이다. 뒤 쪽 `2s` 에서 앞 칸의 첫 쪽 `2s-3` 까지가 3이다. 1로 두면 뛸 때마다 패스가 두 번 돈다(검토가 잡았다).
     */
    fun lookBehind(twoUp: Boolean): Int = if (twoUp) 3 else 1
}

/**
 * 두 쪽을 **같은 높이로 맞춰 나란히** 놓는 계산. **순수 함수다.**
 *
 * 확대·이동은 한 장짜리와 같은 [io.github.donggi.iroiroviewer.ui.gesture.ZoomState] 하나로 한다 — 두 쪽을
 * 폭 `왼쪽 + 오른쪽`, 높이 `공통 높이` 의 **그림 한 장**처럼 다루면 맞춤·경계·최대 배율 계산이 그대로 선다.
 * 두 쪽을 실제로 한 비트맵으로 합치지 않는 것이 요점이다 — 합치면 합친 것 한 장이 더 생겨 예산을 넘긴다.
 */
object SpreadMath {

    /**
     * 펼침의 모양.
     *
     * @property contentWidth·[contentHeight] 두 쪽을 공통 높이로 맞춘 **논리 크기**(비트맵 화소 단위).
     *   `ZoomState.onLayout` 에 그대로 넘긴다 — 가로세로 비만 뜻을 가진다.
     * @property leftFraction 왼쪽 쪽이 전체 폭에서 차지하는 비율(0~1).
     */
    data class Frame(val contentWidth: Int, val contentHeight: Int, val leftFraction: Float)

    /**
     * @param lw·[lh] 왼쪽 쪽의 크기, [rw]·[rh] 오른쪽 쪽의 크기(화소, 어떤 단위든 두 쪽이 같으면 된다).
     * @return 크기를 모르는 쪽이 있으면 null.
     */
    fun frameOf(lw: Int, lh: Int, rw: Int, rh: Int): Frame? {
        if (lw <= 0 || lh <= 0 || rw <= 0 || rh <= 0) return null
        // **키 큰 쪽에 맞춘다.** 작은 쪽을 키우는 것은 논리 크기일 뿐 비트맵을 다시 뜨지 않는다 — 화면에 그릴 때
        // 맞춤 배율이 한 번 더 곱해진다.
        val h = max(lh, rh).toDouble()
        val left = lw * h / lh
        val right = rw * h / rh
        val total = left + right
        val width = ceil(total).toLong().coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
        val height = h.toLong().coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
        return Frame(width, height, (left / total).toFloat())
    }

    /**
     * 원본 두 쪽을 같은 규칙으로 맞춘 폭. **최대 배율(원본의 2배)을 이것으로 잰다** — 바닥층은 반쪽 크기로 떠 있어
     * 그 폭으로 재면 한 장짜리보다 확대가 일찍 멈춘다.
     */
    fun originalWidthOf(lw: Int, lh: Int, rw: Int, rh: Int): Int {
        if (lw <= 0 || lh <= 0 || rw <= 0 || rh <= 0) return 0
        val h = max(lh, rh).toDouble()
        return ceil(lw * h / lh + rw * h / rh).toLong().coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
    }

    /**
     * 쪽 하나가 펼침 **가운데를 원점으로** 얼마나 비켜 있는가(맞춘 화면 화소).
     *
     * 선명화 조각을 쪽마다 뜰 때, 쪽 하나를 '가운데 놓인 한 장' 으로 보이게 하는 보정이다: 그 쪽의 이동량은
     * `offset + 배율 × 이 값` 이 된다(`ZoomMath.visibleFraction` 의 좌표 약속 — 원점이 뷰포트 중앙이다).
     *
     * @param fittedWidth 배율 1 에서 펼침 전체가 그려지는 폭.
     * @param left 왼쪽 쪽인가.
     */
    fun pageCenterOffset(fittedWidth: Float, leftFraction: Float, left: Boolean): Float =
        if (left) -fittedWidth * (1f - leftFraction) / 2f else fittedWidth * leftFraction / 2f

    /**
     * 반쪽 자리(`slotWidth × slotHeight`)에 맞췄을 때 그 쪽의 **긴 변**. 디코딩 목표로 넘긴다.
     *
     * 모르면 자리의 긴 변이다 — 바이트 상한(`pageCap / 2`)이 따로 누르므로 어림이어도 예산은 넘지 않는다.
     */
    fun slotTarget(width: Int, height: Int, slotWidth: Int, slotHeight: Int): Int {
        if (slotWidth <= 0 || slotHeight <= 0) return 1
        if (width <= 0 || height <= 0) return max(slotWidth, slotHeight)
        val k = min(slotWidth.toDouble() / width, slotHeight.toDouble() / height)
        return ceil(max(width, height) * k).toLong().coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
    }

    /**
     * 정지 쪽 하나를 반쪽 자리에 **맞춘 크기 그대로** 뜰 크기 `[폭, 높이]`. 키우지 않는다(작은 쪽은 원본 크기).
     * `ARGB_8888` 로 [capBytes] 를 넘으면 비를 지킨 채 줄인다. 모르는 크기면 null.
     *
     * ## 왜 2의 거듭제곱 표본(`ImageIo.decodeFitted`)으로 뜨지 않는가
     *
     * 표본이 둘 가운데 하나밖에 고르지 못하기 때문이다 — 원본 그대로이거나 가로세로 절반. 가로 폰(2400×1080)의 반쪽
     * 1200×1080 에 흔한 1200×1800 쪽을 넣으면 원본 그대로는 8,640,000 B 라 반쪽 몫(`pageCap / 2` = 5,184,000 B)을 넘고,
     * 절반(600×900)은 자리(720×1080)보다 작아 **1.2배 늘어나 흐리게** 보였다. 태블릿 가로(반쪽 1280×1600)에서는 1.8배였다
     * (검토가 셈으로 잡았다). 맞춘 크기 그대로 뜨면 한 쪽 보기와 같은 선명도이고, 그 넓이는 반쪽 자리 이하라 몫 안이다.
     */
    fun halfSize(width: Int, height: Int, slotWidth: Int, slotHeight: Int, capBytes: Long): IntArray? {
        if (width <= 0 || height <= 0 || slotWidth <= 0 || slotHeight <= 0) return null
        val fit = min(1.0, min(slotWidth.toDouble() / width, slotHeight.toDouble() / height))
        // 올림이 부동소수 오차로 자리를 한 화소 넘지 않게 자리로도 누른다.
        var w = ceil(width * fit).toInt().coerceIn(1, min(width, slotWidth))
        var h = ceil(height * fit).toInt().coerceIn(1, min(height, slotHeight))
        if (capBytes > 0 && w.toLong() * h * ImageLimits.BYTES_PER_PIXEL > capBytes) {
            // 넓이가 배율의 제곱으로 준다. 내림이라 상한 안에 들고, 부동소수 오차는 아래 고리가 한 화소씩 깎는다.
            val k = sqrt(capBytes.toDouble() / (w.toLong() * h * ImageLimits.BYTES_PER_PIXEL))
            w = (w * k).toInt().coerceAtLeast(1)
            h = (h * k).toInt().coerceAtLeast(1)
            while (w.toLong() * h * ImageLimits.BYTES_PER_PIXEL > capBytes && (w > 1 || h > 1)) {
                if (w >= h) w-- else h--
            }
        }
        return intArrayOf(w, h)
    }
}
