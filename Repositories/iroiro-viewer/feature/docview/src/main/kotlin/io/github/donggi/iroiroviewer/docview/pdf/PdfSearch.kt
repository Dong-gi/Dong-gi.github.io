package io.github.donggi.iroiroviewer.docview.pdf

/**
 * PDF 안에서 찾은 것과 **지금 몇 번째를 보고 있는가**. 순수한 값이라 JVM 시험이 답한다(`PdfSearchTest`).
 *
 * ## 안드로이드 15(API 35) 이상에서만 있다
 *
 * 찾기는 플랫폼의 `PdfRenderer.Page.searchText`(API 35)가 한다. 그 아래 기기의 레거시 `PdfRenderer` 는 쪽의 글자를 주지
 * 않고, 글자 배치 엔진을 우리가 다시 만드는 일은 11단계가 미룬 그대로다(CLAUDE.md '11단계에서 미룬 것'). 그래서 화면은
 * API 35 아래에서 찾기 단추 자체를 **띄우지 않는다** — 누르면 '안 된다' 고 말하는 단추보다 없는 편이 정직하다.
 *
 * ## 좌표
 *
 * 한 결과([Match])는 쪽 번호와 **쪽 좌표(포인트, 왼쪽 위가 원점)**의 사각형들이다 — 한 낱말이 줄을 넘으면 사각형이 둘이다.
 * 화면이 그 사각형을 쪽 그림 위에 같은 확대·이동으로 얹는다. **플랫폼이 원점을 왼쪽 위로 주는지는 기기에서 확인하지
 * 못했다**(에뮬레이터 확인 목록에 있다).
 */
internal object PdfSearch {

    /** 쪽 좌표의 사각형(포인트). */
    data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float)

    /** 찾은 것 하나. */
    data class Match(val page: Int, val boxes: List<Box>)

    /**
     * 찾기 한 벌의 상태.
     *
     * @param searched 지금까지 훑은 쪽 수. [total] 과 같으면 다 훑었다.
     * @param current 보고 있는 결과의 자리. 결과가 없으면 -1.
     */
    data class State(
        val query: String = "",
        val matches: List<Match> = emptyList(),
        val current: Int = -1,
        val searched: Int = 0,
        val total: Int = 0,
        /** 결과가 [MAX_MATCHES] 에 닿아 훑기를 멈췄다. 화면이 '이만큼 넘게' 라고 말한다. */
        val capped: Boolean = false,
    ) {
        val running: Boolean get() = query.isNotEmpty() && searched < total
        val active: Boolean get() = query.isNotEmpty()
        val currentMatch: Match? get() = matches.getOrNull(current)

        /** 이 쪽들 위의 결과. 화면이 강조를 그린다. */
        fun on(pages: Collection<Int>): List<Match> = matches.filter { it.page in pages }

        /** 다음 결과. 끝에서 처음으로 돈다(다 훑기 전이면 돌지 않는다 — 뒤에 더 올 수 있다). */
        fun next(): State {
            if (matches.isEmpty()) return this
            val n = current + 1
            return when {
                n < matches.size -> copy(current = n)
                running -> this
                else -> copy(current = 0)
            }
        }

        /** 이전 결과. 처음에서 끝으로 돈다. */
        fun previous(): State {
            if (matches.isEmpty()) return this
            val p = current - 1
            return when {
                p >= 0 -> copy(current = p)
                running -> this
                else -> copy(current = matches.size - 1)
            }
        }

        /**
         * 한 쪽을 훑은 결과를 더한다. **처음 찾은 결과는 [fromPage] 뒤의 첫 것**을 가리킨다 — 20쪽을 보다 찾았으면 3쪽이
         * 아니라 20쪽 뒤의 결과로 가야 한다. 훑기는 [fromPage] 부터 돌아 나가므로 첫 결과가 곧 그것이다.
         */
        fun add(found: List<Match>, fromPage: Int): State {
            if (capped) return this
            val room = MAX_MATCHES - matches.size
            val taken = if (found.size > room) found.take(room.coerceAtLeast(0)) else found
            val merged = (matches + taken).sortedWith(compareBy<Match> { it.page }.thenBy { it.boxes.firstOrNull()?.top ?: 0f })
            val cur = when {
                current >= 0 -> merged.indexOf(matches[current])
                merged.isEmpty() -> -1
                else -> merged.indexOfFirst { it.page >= fromPage }.takeIf { it >= 0 } ?: 0
            }
            val full = merged.size >= MAX_MATCHES
            return copy(
                matches = merged,
                current = cur,
                // 상한에 닿으면 다 훑은 것으로 친다 — 돌기(`next`)가 풀리고 화면의 '찾는 중' 이 걷힌다.
                searched = if (full) total else searched + 1,
                capped = full,
            )
        }
    }

    /**
     * 훑는 차례 — 보고 있는 쪽부터 끝까지, 그다음 처음부터 그 앞까지. 첫 결과가 빨리 나오고, 그것이 사용자가 기대하는
     * '다음' 이다.
     */
    fun order(fromPage: Int, pageCount: Int): List<Int> {
        if (pageCount <= 0) return emptyList()
        val start = fromPage.coerceIn(0, pageCount - 1)
        return (start until pageCount) + (0 until start)
    }

    /**
     * 결과 사각형 하나를 **가상 쪽 안의 비율**(0~1)로 — `[왼, 위, 오른, 아래]`. 화면이 맞춘 그림의 크기를 곱해 얹는다.
     *
     * 두 쪽 보기면 그 쪽의 자리([PdfSpreads.Placement])만큼 옮긴다. 쪽 밖으로 삐져나온 것은 그 쪽 안으로 자른다(옆 쪽에
     * 강조가 번지지 않게). 그 쪽이 가상 쪽에 없거나 넓이가 없으면 null.
     */
    fun boxIn(layout: PdfSpreads.Layout, page: Int, box: Box): FloatArray? {
        val part = layout.parts.firstOrNull { it.page == page } ?: return null
        if (layout.width <= 0 || layout.height <= 0) return null
        fun clampX(v: Float) = v.coerceIn(0f, part.width.toFloat())
        fun clampY(v: Float) = v.coerceIn(0f, part.height.toFloat())
        val l = part.left + clampX(minOf(box.left, box.right))
        val r = part.left + clampX(maxOf(box.left, box.right))
        val t = part.top + clampY(minOf(box.top, box.bottom))
        val b = part.top + clampY(maxOf(box.top, box.bottom))
        if (r <= l || b <= t) return null
        val w = layout.width.toFloat()
        val h = layout.height.toFloat()
        return floatArrayOf(l / w, t / h, r / w, b / h)
    }

    /** 찾을 글. 앞뒤 공백을 떼고 [MAX_QUERY] 에서 자른다. 비면 null. */
    fun normalize(raw: String): String? = raw.trim().take(MAX_QUERY).takeIf { it.isNotEmpty() }

    /** 찾을 글의 길이 상한. 한 쪽을 훑는 값이 글 길이에 비례한다. */
    const val MAX_QUERY = 100

    /** 한 쪽의 결과를 이만큼까지 받는다. 'e' 한 글자로 찾은 수천 개가 화면을 사각형으로 덮지 않게. */
    const val MAX_PER_PAGE = 200

    /** 문서 전체의 결과 상한. 넘으면 훑기를 멈춘다(화면이 '이만큼 넘게' 라고 말한다). */
    const val MAX_MATCHES = 5_000
}
