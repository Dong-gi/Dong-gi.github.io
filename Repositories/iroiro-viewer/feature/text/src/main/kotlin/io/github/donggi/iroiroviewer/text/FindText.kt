package io.github.donggi.iroiroviewer.text

/**
 * 편집기의 찾기와 바꾸기 — 글자 그대로 찾는다(정규식이 아니다). **순수 함수**라 JVM 시험이 답한다(`FindTextTest`).
 *
 * 찾기는 끝에 닿으면 처음으로 돌아간다(감싸기 — 모바일 편집기들의 관행이고, 뷰어의 '다음' 도 그렇다). 찾은 것들은 **겹치지 않게**
 * 센다 — '모두 바꾸기' 가 바꾸는 것과 같은 수여야 '3 / 12' 와 '12개를 바꿨습니다' 가 서로 맞는다.
 */
internal object FindText {

    /** [from] 부터 앞으로 찾는다. 없으면 처음부터 [from] 까지 찾는다. 찾은 자리의 시작, 없으면 -1. */
    fun next(text: CharSequence, query: String, from: Int, ignoreCase: Boolean): Int {
        if (query.isEmpty() || query.length > text.length) return -1
        val start = from.coerceIn(0, text.length)
        val ahead = text.indexOf(query, start, ignoreCase)
        if (ahead >= 0) return ahead
        val wrapped = text.indexOf(query, 0, ignoreCase)
        return if (wrapped in 0 until start) wrapped else -1
    }

    /** [before] 앞(그 자리에서 시작하는 것은 빼고)에서 뒤로 찾는다. 없으면 끝에서부터 감싸 찾는다. */
    fun previous(text: CharSequence, query: String, before: Int, ignoreCase: Boolean): Int {
        if (query.isEmpty() || query.length > text.length) return -1
        val end = before.coerceIn(0, text.length)
        if (end > 0) {
            val behind = text.lastIndexOf(query, end - 1, ignoreCase)
            if (behind >= 0) return behind
        }
        val wrapped = text.lastIndexOf(query, text.length - query.length, ignoreCase)
        return if (wrapped >= end) wrapped else -1
    }

    /** 겹치지 않게 찾은 모든 시작 자리. '모두 바꾸기' 가 이것을 뒤에서부터 바꾼다. */
    fun all(text: CharSequence, query: String, ignoreCase: Boolean): List<Int> {
        if (query.isEmpty()) return emptyList()
        val found = ArrayList<Int>()
        var at = text.indexOf(query, 0, ignoreCase)
        while (at >= 0) {
            found += at
            at = text.indexOf(query, at + query.length, ignoreCase)
        }
        return found
    }

    /**
     * '몇 번째 / 모두 몇' — [selectionStart] 에서 시작하는 찾은 것이 몇 번째인가(1부터). 고른 것이 찾은 것이 아니면 0.
     */
    fun position(starts: List<Int>, selectionStart: Int, selectionEnd: Int, queryLength: Int): Int {
        if (selectionEnd - selectionStart != queryLength) return 0
        val i = starts.binarySearch(selectionStart)
        return if (i >= 0) i + 1 else 0
    }

    /** 지금 고른 글이 찾는 말인가 — '바꾸기' 는 그때만 바꾸고, 아니면 다음 것을 고르기만 한다(바꾸기 전에 무엇을 바꾸는지 보인다). */
    fun selects(text: CharSequence, query: String, selectionStart: Int, selectionEnd: Int, ignoreCase: Boolean): Boolean {
        if (query.isEmpty() || selectionEnd - selectionStart != query.length || selectionEnd > text.length) return false
        return text.regionMatches(selectionStart, query, 0, query.length, ignoreCase)
    }
}
