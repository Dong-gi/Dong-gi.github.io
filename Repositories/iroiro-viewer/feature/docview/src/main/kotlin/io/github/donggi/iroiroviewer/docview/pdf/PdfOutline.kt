package io.github.donggi.iroiroviewer.docview.pdf

/**
 * PDF 의 **목차**(책갈피 — 문서 카탈로그의 `/Outlines`). 순수한 값이라 JVM 시험이 답한다(`PdfOutlineTest`).
 *
 * `PdfRenderer` 에는 목차를 주는 API 가 없다. 그래서 글 고르기의 해석기(`text/PdfNavigation`)가 읽는다 — 새 해석기가 아니라 암호
 * PDF 를 풀려고 세우고 말뭉치로 검증한 xref·객체 읽기 위에 사슬 하나를 더 따라간다. 그 해석기가 원본을 갖지 못하는 문서(플랫폼이
 * 직접 푼 암호 문서 — 평문이 우리 손에 없다)는 목차가 없다.
 *
 * ## 나무를 평평하게 든다
 *
 * 항목은 **앞차례(preorder)로 편 목록**이고 깊이([Entry.depth])가 나무를 적는다 — 어느 항목의 자손은 그 뒤에 이어지는, 깊이가 더 큰
 * 항목들이다. 화면은 접힌 항목의 자손만 건너뛰면 된다([visible]). EPUB 의 목차도 평평하지만(들여쓰기만) PDF 목차는 수백 항목이
 * 흔해 접고 펼친다 — 펼침 상태는 화면이 들고(쪽을 옮겨도 맞춰 줄 것이 없다) 첫 상태만 문서가 정한다([Entry.open] — `/Count` 가
 * 양수면 펼친 채로 저장된 항목이다, 명세 12.3.3).
 */
internal class PdfOutline(val entries: List<Entry>) {

    /**
     * 항목 하나. [page] 는 0부터의 쪽 번호 — 목적지를 풀지 못했거나 문서에 없는 쪽이면 null(줄은 남기고 누를 수 없게 둔다: 목차의
     * 모양이 문서가 말한 그대로여야 한다). [hasChildren] 은 바로 뒤에 더 깊은 항목이 있는가.
     */
    data class Entry(
        val title: String,
        val page: Int?,
        val depth: Int,
        val open: Boolean,
        val hasChildren: Boolean,
    )

    val isEmpty: Boolean get() = entries.isEmpty()

    /** 처음에 펼쳐 둘 항목들(문서가 펼친 채로 저장한 것). */
    fun initiallyOpen(): Set<Int> = entries.indices.filterTo(HashSet()) { entries[it].open && entries[it].hasChildren }

    /**
     * 보이는 항목의 차례들. 깊이 0 은 언제나 보이고, 나머지는 **조상이 모두 [expanded] 에 있을 때만** 보인다. 한 번 훑는다 —
     * 접힌 항목을 만나면 그보다 깊은 항목을 건너뛴다.
     */
    fun visible(expanded: Set<Int>): List<Int> {
        val out = ArrayList<Int>()
        var skipDeeperThan = Int.MAX_VALUE
        for ((i, e) in entries.withIndex()) {
            if (e.depth > skipDeeperThan) continue
            skipDeeperThan = Int.MAX_VALUE
            out += i
            if (e.hasChildren && i !in expanded) skipDeeperThan = e.depth
        }
        return out
    }

    /**
     * 지금 쪽 [page] 가 속한 항목 — 그 쪽 이하를 가리키는 항목 가운데 **가장 뒤의 것**(목차의 차례가 대개 쪽의 차례다). 화면이 그 줄을
     * 굵게 쓰고 그 줄로 목록을 연다. 없으면 -1.
     */
    fun currentFor(page: Int): Int {
        var best = -1
        var bestPage = -1
        for ((i, e) in entries.withIndex()) {
            val p = e.page ?: continue
            if (p <= page && p >= bestPage) {
                best = i
                bestPage = p
            }
        }
        return best
    }

    /** [index] 의 조상들(바깥부터). 지금 항목을 보이게 펼칠 때 쓴다. */
    fun ancestorsOf(index: Int): List<Int> {
        val e = entries.getOrNull(index) ?: return emptyList()
        val out = ArrayList<Int>()
        var depth = e.depth
        var i = index - 1
        while (i >= 0 && depth > 0) {
            if (entries[i].depth < depth) {
                out += i
                depth = entries[i].depth
            }
            i--
        }
        return out.asReversed()
    }

    companion object {
        val EMPTY = PdfOutline(emptyList())

        /**
         * 읽는 쪽의 상한. 넘는 항목은 읽지 않는다 — 목록이 거기서 끝날 뿐 앱이 멎지 않는다. 사람이 만든 목차는 수천을 넘지 않는다
         * (말뭉치에서 가장 큰 것이 35).
         */
        const val MAX_ENTRIES = 10_000

        /** 깊이의 상한. 더 깊은 항목은 버린다(사람이 읽는 목차는 열 겹을 넘지 않는다 — 수만 겹은 악성 파일이다). */
        const val MAX_DEPTH = 32

        /** 제목의 길이 상한(글자). 넘으면 자른다 — 목록의 한 줄이다. */
        const val MAX_TITLE = 300
    }
}
