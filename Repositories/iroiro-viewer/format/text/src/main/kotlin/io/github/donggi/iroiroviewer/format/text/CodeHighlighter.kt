package io.github.donggi.iroiroviewer.format.text

/**
 * 중괄호 계열 언어를 위한 훑개. **정규식을 쓰지 않는다.**
 *
 * ## 왜 정규식이 아닌가
 *
 * 강조를 정규식으로 쓰면 짧게 끝나지만 두 가지가 따라온다. 하나는 **역추적 폭발**이다 —
 * 우리가 여는 것은 사용자가 준 임의의 파일이고, 4,096자 한 행에 역추적이 심한 식이
 * 걸리면 화면이 멎는다. 다른 하나는 **여러 줄 상태를 표현할 수 없다는 것**이다. 주석이
 * 행을 넘어가는 순간 정규식 한 방으로는 답이 안 나와서 결국 손으로 쓴 상태기계가
 * 필요해진다. 처음부터 상태기계로 쓰면 둘 다 없다.
 *
 * ## 훑는 순서가 곧 우선순위다
 *
 * 줄 주석 → 블록 주석 → 세 겹 따옴표 → 따옴표 → 숫자 → 낱말. 이 순서가 바뀌면
 * 문자열 안의 `//` 가 주석이 된다.
 */
class CodeHighlighter(
    override val label: String,
    private val d: Dialect,
) : RowHighlighter {

    override val multiline: Boolean
        get() = d.blockOpen != null || d.tripleQuotes || d.backtick

    /**
     * 언어마다 다른 것만 모은 것. **훑는 코드는 하나다.**
     *
     * 언어별로 훑개를 따로 쓰면 같은 버그를 언어 수만큼 고치게 된다. 실제로 다른 것은
     * 낱말 목록과 주석 기호뿐이라, 그것만 자료로 두고 코드는 공유한다.
     */
    data class Dialect(
        val keywords: Set<String>,
        /** `true`·`null` 처럼 낱말이지만 값인 것. 다른 색으로 칠한다. */
        val literals: Set<String> = emptySet(),
        /** 줄 끝까지 주석으로 만드는 기호들. 긴 것을 먼저 적어라. */
        val lineComments: List<String> = listOf("//"),
        val blockOpen: String? = "/*",
        val blockClose: String? = "*/",
        /** 코틀린·러스트의 블록 주석은 겹쳐 쓸 수 있다. */
        val nestedBlockComments: Boolean = false,
        val doubleQuote: Boolean = true,
        /** C 계열의 문자 상수. 파이썬처럼 문자열로 쓰는 언어도 참이다. */
        val singleQuote: Boolean = true,
        /** JS 템플릿 리터럴·Go 원시 문자열. **줄을 넘는다.** */
        val backtick: Boolean = false,
        /** 세 겹 따옴표. **줄을 넘는다.** */
        val tripleQuotes: Boolean = false,
        val escape: Char? = '\\',
        /** 낱말에 더 들어갈 수 있는 글자. CSS 의 붙임표 같은 것. */
        val wordExtra: String = "_$",
    )

    override fun afterNewline(state: Int): Int = when (state and MODE_MASK) {
        // 한 줄짜리 문자열은 줄을 넘지 못한다. 닫히지 않은 채 줄이 끝나면 거기서 끝이다
        // — 편집기들이 하는 것과 같은 오류 복구다.
        MODE_DQ, MODE_SQ -> MODE_NORMAL
        else -> state
    }

    override fun highlight(row: String, startState: Int, out: MutableList<Span>): Int {
        val n = row.length
        var mode = startState and MODE_MASK
        var depth = startState ushr DEPTH_SHIFT
        var i = 0

        // ① 앞 행에서 이어지던 것을 먼저 닫는다. 여기서 안 끝나면 이 행은 통째로 그것이다.
        if (mode != MODE_NORMAL) {
            val r = resume(row, mode, depth, out)
            i = r.end
            mode = r.mode
            depth = r.depth
            if (mode != MODE_NORMAL) return pack(mode, depth)
        }

        while (i < n) {
            val c = row[i]

            // ② 줄 주석 — 줄 끝까지.
            if (lineCommentAt(row, i)) {
                out += Span(i, n, TokenKind.COMMENT)
                return MODE_NORMAL
            }

            // ③ 블록 주석.
            if (d.blockOpen != null && row.startsWith(d.blockOpen, i)) {
                val r = scanBlock(row, i + d.blockOpen.length, 1, i, out)
                i = r.end
                mode = r.mode
                depth = r.depth
                if (mode != MODE_NORMAL) return pack(mode, depth)
                continue
            }

            // ④ 세 겹 따옴표. **두 겹 검사보다 먼저** 봐야 빈 문자열 + 따옴표로 갈리지 않는다.
            if (d.tripleQuotes && (row.startsWith(TRIPLE_DQ, i) || row.startsWith(TRIPLE_SQ, i))) {
                val isDouble = c == '"'
                val marker = if (isDouble) TRIPLE_DQ else TRIPLE_SQ
                val m = if (isDouble) MODE_TRIPLE_DQ else MODE_TRIPLE_SQ
                val r = scanTriple(row, i + 3, marker, m, i, out)
                i = r.end
                mode = r.mode
                if (mode != MODE_NORMAL) return pack(mode, 0)
                continue
            }

            // ⑤ 따옴표.
            if (d.backtick && c == '`') {
                val r = scanQuoted(row, i + 1, '`', MODE_BACKTICK, i, out)
                i = r.end
                mode = r.mode
                if (mode != MODE_NORMAL) return pack(mode, 0)
                continue
            }
            if (d.doubleQuote && c == '"') {
                val r = scanQuoted(row, i + 1, '"', MODE_DQ, i, out)
                i = r.end
                mode = r.mode
                if (mode != MODE_NORMAL) return pack(mode, 0)
                continue
            }
            if (d.singleQuote && c == '\'') {
                val r = scanQuoted(row, i + 1, '\'', MODE_SQ, i, out)
                i = r.end
                mode = r.mode
                if (mode != MODE_NORMAL) return pack(mode, 0)
                continue
            }

            // ⑥ 숫자. 낱말 한가운데의 숫자를 잡지 않도록 **앞 글자를 본다.**
            if (c.isDigit() && !isWordChar(row.getOrNull(i - 1))) {
                val end = scanNumber(row, i)
                out += Span(i, end, TokenKind.NUMBER)
                i = end
                continue
            }

            // ⑦ 낱말.
            if (isWordStart(c)) {
                var j = i + 1
                while (j < n && isWordChar(row[j])) j++
                val word = row.substring(i, j)
                when {
                    word in d.literals -> out += Span(i, j, TokenKind.LITERAL)
                    word in d.keywords -> out += Span(i, j, TokenKind.KEYWORD)
                }
                i = j
                continue
            }

            i++
        }
        return pack(mode, depth)
    }

    // ---- 이어지던 것 닫기 ----------------------------------------------------

    private fun resume(row: String, mode: Int, depth: Int, out: MutableList<Span>): Scan = when (mode) {
        MODE_BLOCK -> scanBlock(row, 0, depth, 0, out)
        MODE_TRIPLE_DQ -> scanTriple(row, 0, TRIPLE_DQ, MODE_TRIPLE_DQ, 0, out)
        MODE_TRIPLE_SQ -> scanTriple(row, 0, TRIPLE_SQ, MODE_TRIPLE_SQ, 0, out)
        MODE_BACKTICK -> scanQuoted(row, 0, '`', MODE_BACKTICK, 0, out)
        MODE_DQ -> scanQuoted(row, 0, '"', MODE_DQ, 0, out)
        MODE_SQ -> scanQuoted(row, 0, '\'', MODE_SQ, 0, out)
        else -> Scan(0, MODE_NORMAL, 0)
    }

    // ---- 조각 훑개 ------------------------------------------------------------

    private class Scan(val end: Int, val mode: Int, val depth: Int)

    /** [from] 부터 블록 주석의 끝을 찾는다. [spanStart] 부터 한 조각으로 칠한다. */
    private fun scanBlock(
        row: String,
        from: Int,
        depthIn: Int,
        spanStart: Int,
        out: MutableList<Span>,
    ): Scan {
        val open = d.blockOpen ?: return Scan(row.length, MODE_NORMAL, 0)
        val close = d.blockClose ?: return Scan(row.length, MODE_NORMAL, 0)
        var depth = depthIn
        var i = from
        val n = row.length
        while (i < n) {
            if (d.nestedBlockComments && row.startsWith(open, i)) {
                depth++
                i += open.length
                continue
            }
            if (row.startsWith(close, i)) {
                i += close.length
                depth--
                if (depth <= 0) {
                    out += Span(spanStart, i, TokenKind.COMMENT)
                    return Scan(i, MODE_NORMAL, 0)
                }
                continue
            }
            i++
        }
        out += Span(spanStart, n, TokenKind.COMMENT)
        return Scan(n, MODE_BLOCK, depth)
    }

    private fun scanTriple(
        row: String,
        from: Int,
        marker: String,
        mode: Int,
        spanStart: Int,
        out: MutableList<Span>,
    ): Scan {
        var i = from
        val n = row.length
        while (i < n) {
            // **세 겹 따옴표 안에서 역슬래시를 이스케이프로 보지 않는다.** 코틀린의 원시
            // 문자열에는 이스케이프가 아예 없어서, 보는 쪽이 오히려 틀린다.
            if (row.startsWith(marker, i)) {
                i += marker.length
                out += Span(spanStart, i, TokenKind.STRING)
                return Scan(i, MODE_NORMAL, 0)
            }
            i++
        }
        out += Span(spanStart, n, TokenKind.STRING)
        return Scan(n, mode, 0)
    }

    private fun scanQuoted(
        row: String,
        from: Int,
        quote: Char,
        mode: Int,
        spanStart: Int,
        out: MutableList<Span>,
    ): Scan {
        var i = from
        val n = row.length
        while (i < n) {
            val c = row[i]
            if (d.escape != null && c == d.escape) {
                // 행 끝의 역슬래시는 다음 행으로 이어지지만, 한 줄짜리 문자열은 어차피
                // [afterNewline] 이 씻는다. 여기서는 두 글자를 건너뛰기만 한다.
                i += 2
                continue
            }
            if (c == quote) {
                i++
                out += Span(spanStart, i, TokenKind.STRING)
                return Scan(i, MODE_NORMAL, 0)
            }
            i++
        }
        out += Span(spanStart, n, TokenKind.STRING)
        return Scan(n, mode, 0)
    }

    /** 16진수·자릿수 구분자·지수·접미사를 한 덩어리로 삼킨다. */
    private fun scanNumber(row: String, from: Int): Int {
        var i = from
        val n = row.length
        while (i < n) {
            val c = row[i]
            if (c.isLetterOrDigit() || c == '.' || c == '_' || c == '\'') {
                // 지수의 부호까지 삼켜야 한 덩어리가 된다.
                if ((c == 'e' || c == 'E' || c == 'p' || c == 'P') &&
                    i + 1 < n && (row[i + 1] == '+' || row[i + 1] == '-')
                ) {
                    i += 2
                    continue
                }
                i++
                continue
            }
            break
        }
        return i
    }

    private fun lineCommentAt(row: String, i: Int): Boolean {
        for (k in d.lineComments.indices) {
            if (row.startsWith(d.lineComments[k], i)) return true
        }
        return false
    }

    private fun isWordStart(c: Char): Boolean = c.isLetter() || c in d.wordExtra
    private fun isWordChar(c: Char?): Boolean =
        c != null && (c.isLetterOrDigit() || c in d.wordExtra)

    private fun pack(mode: Int, depth: Int): Int = mode or (depth shl DEPTH_SHIFT)

    companion object {
        // 상태는 정수 하나다 — 낮은 4비트가 무엇 안에 있는지, 나머지가 블록 주석 겹침 깊이다.
        private const val MODE_MASK = 0xF
        private const val DEPTH_SHIFT = 4

        private const val MODE_NORMAL = 0
        private const val MODE_BLOCK = 1
        private const val MODE_DQ = 2
        private const val MODE_SQ = 3
        private const val MODE_BACKTICK = 4
        private const val MODE_TRIPLE_DQ = 5
        private const val MODE_TRIPLE_SQ = 6

        private const val TRIPLE_DQ = "\"\"\""
        private const val TRIPLE_SQ = "'''"
    }
}
