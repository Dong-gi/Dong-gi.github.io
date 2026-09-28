package io.github.donggi.iroiroviewer.docview.pdf

import kotlin.math.ceil
import kotlin.math.floor

/**
 * **두 쪽 보기**(펼침)의 짝 맞추기와 배치. 순수 함수라 JVM 시험이 답한다(`PdfSpreadsTest`).
 *
 * ## 첫 쪽은 혼자 선다
 *
 * 짝은 문서 차례대로 `[1] [2 3] [4 5] …` 다(사람이 세는 번호). 종이책을 펼치면 표지(1쪽)는 오른쪽에 혼자 있고 2·3쪽이
 * 마주 보는 것이 인쇄의 관례이고, 스캔한 책·잡지 PDF 가 대개 그 모양이다. `[1 2] [3 4]` 로 짝지으면 그런 책에서 **모든
 * 펼침이 한 쪽씩 어긋난다**(왼쪽 쪽이 오른쪽에 온다). 반대로 보고서·슬라이드 PDF 는 짝이 어느 쪽이든 뜻이 없다 — 그래서
 * 고르게 하지 않고 책의 관례로 정했다. 오른쪽에서 왼쪽으로 넘기는 책(만화)은 PDF 가 그 방향을 적어도(`/Direction R2L`)
 * 플랫폼이 알려 주지 않아 **왼→오 그대로다**(`DocViewScreen` 의 '방향' 주석과 같은 한계).
 *
 * ## 두 쪽을 한 장의 가상 쪽으로 본다
 *
 * 나란히 놓은 두 쪽을 **크기 `(폭1 + 폭2) × max(높이)` 의 쪽 하나**로 보면 `PdfLimits` 의 맞춤·예산·타일 계산을 한 벌도 다시
 * 쓰지 않고 그대로 쓸 수 있다 — 쪽 크기 계산을 두 벌로 두면 한쪽만 고쳐지는 날이 온다. 비트맵도 **한 장**이다: 두 쪽이
 * 화면의 반씩을 차지하므로 넓이의 합이 한 쪽을 화면에 맞춘 것과 같고, 그래서 예산(`ImageLimits`)의 '한 장' 이 그대로 맞는다.
 */
internal object PdfSpreads {

    /** 화면 한 칸에 보이는 것 — 쪽 하나, 또는 펼침 하나. 값으로 견준다(캐시의 열쇠). */
    data class View(val pages: List<Int>) {
        val first: Int get() = pages.first()
        val isSpread: Boolean get() = pages.size > 1

        companion object {
            fun single(page: Int) = View(listOf(page))
        }
    }

    /** 가상 쪽 안의 한 쪽. 좌표·크기는 포인트다. */
    data class Placement(val page: Int, val left: Int, val top: Int, val width: Int, val height: Int)

    /** 가상 쪽. 쪽 하나면 그 쪽 자신이다. */
    data class Layout(val width: Int, val height: Int, val parts: List<Placement>) {
        /** 한 쪽이 가상 쪽 전체를 덮는가 — 그러면 쪽 하나를 그리던 옛 길과 같다(바닥을 희게 지운다). */
        val coversWhole: Boolean
            get() = parts.size == 1 && parts[0].let { it.left == 0 && it.top == 0 && it.width == width && it.height == height }
    }

    /**
     * 펼침의 수. `[0] [1 2] [3 4] …` — 첫 쪽 뒤의 (n − 1) 쪽을 둘씩 묶고 남으면 하나이므로 `1 + ⌈(n − 1) / 2⌉ = 1 + ⌊n / 2⌋`.
     */
    fun count(pageCount: Int): Int = if (pageCount <= 0) 0 else 1 + pageCount / 2

    /** 펼침 [spread] 의 쪽들(0부터). 범위 밖이면 빈 목록. */
    fun pages(spread: Int, pageCount: Int): List<Int> {
        if (spread < 0 || spread >= count(pageCount)) return emptyList()
        if (spread == 0) return listOf(0)
        val left = spread * 2 - 1
        return if (left + 1 < pageCount) listOf(left, left + 1) else listOf(left)
    }

    /** 쪽 [page] 가 든 펼침. */
    fun spreadOf(page: Int, pageCount: Int): Int {
        if (pageCount <= 0) return 0
        val p = page.coerceIn(0, pageCount - 1)
        return if (p == 0) 0 else (p + 1) / 2
    }

    /**
     * 가상 쪽 안의 한 쪽이 비트맵에서 차지하는 자리(화소, `[왼, 위, 오른, 아래]`) — 펼침을 그릴 때 **그 쪽의 클립**이다.
     *
     * `render` 에 행렬만 주면 pdfium 은 비트맵 전체를 클립으로 쓴다. 그러면 쪽의 자르기 상자(CropBox) 밖에 적힌 것 — 인쇄용
     * PDF 의 재단 여백 그림, 재단선 — 이 **쪽 사이의 회색과 옆 쪽 위에** 그려진다(쪽 하나를 그릴 때는 비트맵이 곧 쪽이라 보이지
     * 않던 것이다). 쪽의 자리로 자른다. 비트맵과 겹치지 않으면 null — 확대한 타일이 옆 쪽에만 걸친 경우라 그 쪽은 **열지도
     * 않는다**(쪽 하나를 여는 값을 아낀다).
     *
     * 가장자리는 바깥으로 연다(왼·위는 내림, 오른·아래는 올림) — 쪽 끝의 반 화소가 잘려 흰 종이에 줄이 남지 않게. 비트맵 밖은
     * 잘라 준다(`render` 는 비트맵 밖의 클립을 `IllegalArgumentException` 으로 거절한다).
     */
    fun clipOf(
        part: Placement,
        scale: Double,
        srcLeft: Int,
        srcTop: Int,
        bitmapWidth: Int,
        bitmapHeight: Int,
    ): IntArray? {
        if (!(scale > 0.0) || !scale.isFinite() || bitmapWidth <= 0 || bitmapHeight <= 0) return null
        val l = floor(part.left * scale - srcLeft).toInt().coerceAtLeast(0)
        val t = floor(part.top * scale - srcTop).toInt().coerceAtLeast(0)
        val r = ceil((part.left.toDouble() + part.width) * scale - srcLeft).toInt().coerceAtMost(bitmapWidth)
        val b = ceil((part.top.toDouble() + part.height) * scale - srcTop).toInt().coerceAtMost(bitmapHeight)
        return if (r <= l || b <= t) null else intArrayOf(l, t, r, b)
    }

    /**
     * 쪽들을 나란히 놓는다. 높이가 다르면 **세로 가운데**에 둔다(작은 쪽을 늘리지 않는다 — 늘리면 글자가 커져 두 쪽의
     * 크기가 달라 보인다). [sizes] 는 쪽마다 `[폭pt, 높이pt]` 다.
     *
     * @return 쪽 수와 크기 수가 다르거나 그릴 수 없는 쪽이 있으면 null.
     */
    fun layout(pages: List<Int>, sizes: List<IntArray>): Layout? {
        if (pages.isEmpty() || pages.size != sizes.size) return null
        if (sizes.any { it.size < 2 || it[0] <= 0 || it[1] <= 0 }) return null
        val height = sizes.maxOf { it[1] }
        val widthL = sizes.fold(0L) { acc, s -> acc + s[0] }
        if (widthL > Int.MAX_VALUE) return null
        var x = 0
        val parts = pages.mapIndexed { i, page ->
            val (w, h) = sizes[i][0] to sizes[i][1]
            Placement(page, x, (height - h) / 2, w, h).also { x += w }
        }
        return Layout(widthL.toInt(), height, parts)
    }
}
