package io.github.donggi.iroiroviewer.docview.pdf

/**
 * PDF 의 **문서 안 링크** — 목차 쪽의 '3장 … 41' 같은, 같은 문서의 다른 쪽을 가리키는 상자. 순수한 값이라 JVM 시험이 답한다
 * (`PdfLinksTest`).
 *
 * 링크는 플랫폼의 `PdfRenderer.Page.getGotoLinks`(API 35 — 2.0.0 이 minSdk 36 이 되어 갈래 없이 쓴다)가 주고([PdfEngine.gotoLinks]),
 * 두드린 자리가 링크 위면 그 쪽으로 간다(쪽 넘김·이어 보기 둘 다). 링크가 아니면 두드림은 예전대로 막대를 여닫는다.
 *
 * **플랫폼은 `/A << /S /GoTo /D … >>` 꼴의 링크만 준다** — 같은 상자를 `/Dest [쪽 /XYZ …]` 로 적은 링크 주석은 빈 목록이었다(Android 16
 * 에뮬레이터, 합성 표본 둘로 견줬다). 쪽 번호는 0부터, 상자는 왼쪽 위가 원점이다(같은 실측). 그 꼴(직접 목적지·이름 목적지)은 **글 고르기의
 * 해석기가 `/Annots` 를 읽어 채운다**(`text/PdfNavigation` — 플랫폼과 같은 좌표로 옮긴다). 둘을 [merge] 가 합친다 — 같은 쪽을 가리키며
 * 상자가 겹치는 것은 플랫폼의 것 하나만 남긴다. 그 해석기가 원본을 갖지 못하는 문서(플랫폼이 직접 푼 암호 문서)에서는 `/Dest` 꼴이 없다.
 *
 * **바깥 주소(`getLinkContents` — 웹 링크)는 따르지 않는다.** 이 앱은 네트워크를 쓰지 않고, 문서가 심은 주소를 누름 한 번에
 * 브라우저로 넘기면 읽던 사람이 모르는 사이 문서가 고른 곳이 열린다. 문서 전체의 목차는 `PdfOutline`.
 *
 * ## 좌표
 *
 * 상자는 찾기([PdfSearch.Box])와 같은 **쪽 좌표(포인트, 왼쪽 위가 원점)**다 — 같은 플랫폼이 같은 모양(`RectF`)으로 준다(에뮬레이터에서
 * `/Rect [55 590 400 630]` 이 842pt 쪽에서 위 212·아래 252 로 왔다).
 */
internal object PdfLinks {

    /** 링크 하나 — 쪽 위의 상자들(한 링크가 줄을 넘으면 여럿)과 가리키는 쪽(플랫폼이 준 0부터의 번호). */
    data class Link(val boxes: List<PdfSearch.Box>, val targetPage: Int)

    /**
     * 쪽 좌표의 점([x], [y] — 포인트)에 닿는 링크. 상자 안이면 그것, 아니면 상자에서 [slop] 포인트 안의 가장 가까운 것 —
     * 손가락은 글자보다 굵다. 닿는 것이 없으면 null.
     */
    fun hit(links: List<Link>, x: Float, y: Float, slop: Float): Link? {
        if (x.isNaN() || y.isNaN()) return null
        var best: Link? = null
        var bestDistance = Float.MAX_VALUE
        for (link in links) {
            for (b in link.boxes) {
                // 자리를 모르는 상자(NaN)는 건너뛴다 — 비교가 모두 거짓이라 거리 0 으로 읽혀 쪽 어디를 두드려도 그 링크가 된다.
                if (b.left.isNaN() || b.top.isNaN() || b.right.isNaN() || b.bottom.isNaN()) continue
                val left = minOf(b.left, b.right)
                val right = maxOf(b.left, b.right)
                val top = minOf(b.top, b.bottom)
                val bottom = maxOf(b.top, b.bottom)
                val dx = when {
                    x < left -> left - x
                    x > right -> x - right
                    else -> 0f
                }
                val dy = when {
                    y < top -> top - y
                    y > bottom -> y - bottom
                    else -> 0f
                }
                val d = maxOf(dx, dy)
                if (d <= slop && d < bestDistance) {
                    best = link
                    bestDistance = d
                }
            }
        }
        return best
    }

    /**
     * 플랫폼의 링크([platform])에 우리가 읽은 `/Dest` 꼴([ours])을 더한다. 같은 쪽을 가리키고 상자가 겹치는 것은 **이미 있는 것**으로
     * 친다 — `/Dest` 와 `/A` 를 함께 적은 주석(명세가 금하지만 있다)은 양쪽에서 온다. 합친 수는 [MAX_PER_PAGE] 에서 끊는다.
     */
    fun merge(platform: List<Link>, ours: List<Link>): List<Link> {
        if (ours.isEmpty()) return platform
        val out = ArrayList<Link>(platform.size + ours.size)
        out += platform.take(MAX_PER_PAGE)
        for (o in ours) {
            if (out.size >= MAX_PER_PAGE) break
            val same = platform.any { p ->
                p.targetPage == o.targetPage && p.boxes.any { pb -> o.boxes.any { ob -> overlaps(pb, ob) } }
            }
            if (!same) out += o
        }
        return out
    }

    /** 두 상자가 넓이를 나누는가. 자리를 모르는 상자(NaN)는 어느 것과도 겹치지 않는다 — 비교가 모두 거짓이다. */
    private fun overlaps(a: PdfSearch.Box, b: PdfSearch.Box): Boolean =
        minOf(a.left, a.right) < maxOf(b.left, b.right) && minOf(b.left, b.right) < maxOf(a.left, a.right) &&
            minOf(a.top, a.bottom) < maxOf(b.top, b.bottom) && minOf(b.top, b.bottom) < maxOf(a.top, a.bottom)

    /** [link] 가 가리키는 쪽. 문서에 없는 쪽(망가진 링크)이면 null — 누름은 여느 두드림이 된다. */
    fun targetOf(link: Link, pageCount: Int): Int? = link.targetPage.takeIf { it in 0 until pageCount }

    /**
     * 한 쪽에서 들고 있는 링크의 수(앞의 것만). 플랫폼은 목록을 다 만든 뒤에 주므로 그 비용은 막지 못한다 — 막는 것은 우리가 쪽마다 기억하는
     * 양과 두드릴 때마다 훑는 양이다. 링크 하나의 상자 수는 플랫폼이 준 그대로다(줄을 넘는 링크라 대개 한둘).
     */
    const val MAX_PER_PAGE = 1_000
}
