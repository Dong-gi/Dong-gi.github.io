package io.github.donggi.iroiroviewer.format.text

import java.util.Locale

/*
 * 마크다운의 **줄 안** 문법 — 강조·코드·링크·그림·자동 링크·엔티티·줄바꿈.
 *
 * CommonMark 명세의 부록('A parsing strategy')과 그 참조 구현(commonmark.js)의 짜임을 따랐다. 규칙을 새로
 * 만들지 않은 것은, 사람들이 읽는 README 는 GitHub 이 그린 모양을 기준으로 쓰였고 그 모양이 곧 이 명세이기
 * 때문이다. 다른 것은 셋이다.
 *
 * 1. **HTML 을 알아보지 않는다.** 명세는 `<b>` 같은 날것의 HTML 을 그대로 내보내라고 하지만, 이 앱에서
 *    그것은 사용자 파일의 마크업을 WebView 에 넣는 일이다. `<` 는 자동 링크일 때만 뜻이 있고 나머지는 글자다.
 * 2. **링크를 따라가지 않는다.** 바깥 주소는 글자로만 남고(화면이 링크처럼 칠한다), 누를 수 있는 것은
 *    같은 문서 안의 `#자리` 뿐이다.
 * 3. **모든 훑기에 상한이 있다.** 입력은 사용자가 고른 아무 파일이다. 명세의 정규식을 그대로 옮기면
 *    `[a](` 를 10만 번 되풀이한 줄 하나가 제곱 시간이 된다 — 그래서 목적지·제목·이름 훑기가 문단마다
 *    예산([SCAN_BUDGET])을 나눠 쓰고, 노드 수·중첩 깊이도 묶는다.
 */

internal enum class InlineType { ROOT, TEXT, SOFT_BREAK, HARD_BREAK, CODE, EMPH, STRONG, STRIKE, LINK, IMAGE, AUTOLINK }

/** 줄 안의 노드 하나. 형제를 잇는 목록이라 강조를 만들 때 사이의 노드를 옮기기만 하면 된다. */
internal class MdInline(val type: InlineType) {
    /** TEXT·CODE·AUTOLINK 의 글자. */
    var buf: StringBuilder? = null

    /** LINK·IMAGE 가 가리키는 곳(이스케이프를 푼 값). */
    var dest: String = ""

    /** 이 노드 아래로 몇 겹인가(잎은 0). [MdInlineParser.MAX_DEPTH] 를 넘는 노드는 만들지 않는다. */
    var height = 0

    /** 뒤에 오는 평범한 글자를 이어 붙여도 되는가. 구분자·괄호 노드는 글자가 줄어들 수 있어 안 된다. */
    var mergeable = false

    var parent: MdInline? = null
    var prev: MdInline? = null
    var next: MdInline? = null
    var first: MdInline? = null
    var last: MdInline? = null

    fun append(child: MdInline) {
        child.unlink()
        child.parent = this
        val tail = last
        if (tail == null) {
            first = child
        } else {
            tail.next = child
            child.prev = tail
        }
        last = child
    }

    fun insertAfter(sibling: MdInline) {
        sibling.unlink()
        val p = parent
        sibling.parent = p
        sibling.prev = this
        sibling.next = next
        next?.prev = sibling
        next = sibling
        if (sibling.next == null) p?.last = sibling
    }

    fun unlink() {
        val p = parent
        if (prev != null) prev!!.next = next else p?.first = next
        if (next != null) next!!.prev = prev else p?.last = prev
        parent = null
        prev = null
        next = null
    }
}

internal class MdInlineParser(
    /** 정규화한 이름 → 목적지. 참조 정의를 읽으면([parseReference]) 여기에 더한다. */
    private val refs: MutableMap<String, String>,
) {

    private class Delim(
        val cc: Char,
        var numDelims: Int,
        val origDelims: Int,
        val node: MdInline,
        var previous: Delim?,
        var next: Delim?,
        val canOpen: Boolean,
        val canClose: Boolean,
    )

    private class Bracket(
        val node: MdInline,
        val previous: Bracket?,
        val previousDelimiter: Delim?,
        /** 여는 `[` 의 자리. 이름으로 찾는 링크가 `[…]` 를 통째로 잘라 쓴다. */
        val index: Int,
        val image: Boolean,
        val seq: Int,
    ) {
        var active = true
        var bracketAfter = false
    }

    private var s = ""
    private var pos = 0
    private var delimiters: Delim? = null
    private var brackets: Bracket? = null
    private var bracketSeq = 0

    /** 이 번호까지의 괄호는 이미 꺼 두었다 — 링크 안에 링크를 두지 않는 규칙을 한 번만 적용하려고. */
    private var deactivatedUpTo = -1

    /** 이 번호까지의 괄호는 닫아도 깊이 상한을 넘는다. */
    private var tooDeepUpTo = -1
    private var nodes = 0
    private var delims = 0
    private var scanBudget = SCAN_BUDGET

    /** 백틱 달리기의 길이 → 시작 자리들(오름차순). 문단에서 처음 백틱을 만날 때 한 번 만든다. */
    private var tickRuns: HashMap<Int, IntList>? = null

    private class IntList {
        var data = IntArray(4)
        var size = 0
        var cursor = 0
        fun add(v: Int) {
            if (size == data.size) data = data.copyOf(size * 2)
            data[size++] = v
        }
    }

    /** [subject] 를 줄 안 노드의 나무로. 뿌리는 [InlineType.ROOT] 다. */
    fun parse(subject: String): MdInline {
        reset(subject, 0)
        val root = MdInline(InlineType.ROOT)
        while (pos < s.length) {
            if (nodes >= MAX_NODES) {
                // 노드 예산을 다 썼다. 남은 것은 글자로 둔다 — 보이기는 하되 더 쪼개지 않는다.
                appendText(root, s, pos, s.length)
                pos = s.length
                break
            }
            when (s[pos]) {
                '\n' -> newline(root)
                '\\' -> backslash(root)
                '`' -> backticks(root)
                '*', '_', '~' -> delimiterRun(root, s[pos])
                '[' -> {
                    val n = marker("[", root)
                    addBracket(n, pos, image = false)
                    pos++
                }
                '!' -> if (pos + 1 < s.length && s[pos + 1] == '[') {
                    val n = marker("![", root)
                    addBracket(n, pos + 1, image = true)
                    pos += 2
                } else {
                    appendText(root, s, pos, pos + 1)
                    pos++
                }
                ']' -> closeBracket(root)
                '<' -> if (!autolink(root)) {
                    appendText(root, s, pos, pos + 1)
                    pos++
                }
                '&' -> if (!entity(root)) {
                    appendText(root, s, pos, pos + 1)
                    pos++
                }
                else -> plain(root)
            }
        }
        processEmphasis(null)
        return root
    }

    private fun reset(subject: String, at: Int) {
        s = subject
        pos = at
        delimiters = null
        brackets = null
        bracketSeq = 0
        deactivatedUpTo = -1
        tooDeepUpTo = -1
        nodes = 0
        delims = 0
        scanBudget = SCAN_BUDGET
        tickRuns = null
    }

    // ---- 글자 -------------------------------------------------------------------

    private fun plain(root: MdInline) {
        val start = pos
        while (pos < s.length && !isSpecial(s[pos])) pos++
        if (pos == start) pos++ // 특수 글자인데 아무도 처리하지 않았다(있을 수 없지만 멈추지 않게).
        appendText(root, s, start, pos)
    }

    private fun isSpecial(c: Char): Boolean = when (c) {
        '\n', '\\', '`', '*', '_', '~', '[', ']', '!', '<', '&' -> true
        else -> false
    }

    /** 평범한 글자를 붙인다. 앞 노드가 이어 붙일 수 있는 글자면 거기에 — 노드가 글자마다 생기지 않게. */
    private fun appendText(root: MdInline, src: CharSequence, from: Int, to: Int) {
        if (from >= to) return
        val tail = root.last
        if (tail != null && tail.type == InlineType.TEXT && tail.mergeable) {
            tail.buf!!.append(src, from, to)
            return
        }
        val n = MdInline(InlineType.TEXT)
        n.buf = StringBuilder(to - from).append(src, from, to)
        n.mergeable = true
        nodes++
        root.append(n)
    }

    private fun appendText(root: MdInline, text: String) = appendText(root, text, 0, text.length)

    /** 구분자·괄호 글자. 뒤에서 글자가 줄어들 수 있어 이어 붙이지 않는다. */
    private fun marker(text: String, root: MdInline): MdInline {
        val n = MdInline(InlineType.TEXT)
        n.buf = StringBuilder(text)
        nodes++
        root.append(n)
        return n
    }

    private fun leaf(type: InlineType, root: MdInline, text: String? = null): MdInline {
        val n = MdInline(type)
        if (text != null) n.buf = StringBuilder(text)
        nodes++
        root.append(n)
        return n
    }

    // ---- 줄바꿈·이스케이프 -----------------------------------------------------------

    private fun newline(root: MdInline) {
        pos++
        val tail = root.last
        val b = tail?.buf
        if (tail != null && tail.type == InlineType.TEXT && b != null && b.isNotEmpty() && b[b.length - 1] == ' ') {
            var n = 0
            while (n < b.length && b[b.length - 1 - n] == ' ') n++
            b.setLength(b.length - n)
            leaf(if (n >= 2) InlineType.HARD_BREAK else InlineType.SOFT_BREAK, root)
        } else {
            leaf(InlineType.SOFT_BREAK, root)
        }
        // 다음 줄의 앞 공백은 버린다.
        while (pos < s.length && s[pos] == ' ') pos++
    }

    private fun backslash(root: MdInline) {
        pos++
        when {
            pos < s.length && s[pos] == '\n' -> {
                pos++
                leaf(InlineType.HARD_BREAK, root)
                while (pos < s.length && s[pos] == ' ') pos++
            }
            pos < s.length && isAsciiPunct(s[pos]) -> {
                appendText(root, s, pos, pos + 1)
                pos++
            }
            else -> appendText(root, "\\")
        }
    }

    // ---- 코드 ------------------------------------------------------------------

    private fun backticks(root: MdInline) {
        val start = pos
        while (pos < s.length && s[pos] == '`') pos++
        val n = pos - start
        val closer = findTicks(n, pos)
        if (closer < 0) {
            appendText(root, s, start, pos)
            return
        }
        var content = s.substring(pos, closer).replace('\n', ' ')
        // 앞뒤에 공백이 하나씩 있고 공백만은 아니면 하나씩 벗긴다(`` ` `` 같은 백틱 자체를 쓰려는 모양).
        if (content.length >= 2 && content[0] == ' ' && content[content.length - 1] == ' ' && content.any { it != ' ' }) {
            content = content.substring(1, content.length - 1)
        }
        leaf(InlineType.CODE, root, content)
        pos = closer + n
    }

    /**
     * [from] 뒤에서 길이가 **정확히** [n] 인 백틱 달리기의 시작. 없으면 -1.
     *
     * 닫는 것을 찾을 때마다 뒤를 훑으면 짝 없는 백틱이 많은 줄에서 제곱이 된다. 달리기를 한 번 모아 두고
     * 길이마다 커서를 앞으로만 민다 — 여는 자리가 언제나 앞으로만 가기 때문에 성립한다.
     */
    private fun findTicks(n: Int, from: Int): Int {
        val runs = tickRuns ?: buildTickRuns().also { tickRuns = it }
        val list = runs[n] ?: return -1
        while (list.cursor < list.size && list.data[list.cursor] < from) list.cursor++
        return if (list.cursor < list.size) list.data[list.cursor] else -1
    }

    private fun buildTickRuns(): HashMap<Int, IntList> {
        val map = HashMap<Int, IntList>()
        var i = 0
        while (i < s.length) {
            if (s[i] != '`') {
                i++
                continue
            }
            val start = i
            while (i < s.length && s[i] == '`') i++
            map.getOrPut(i - start) { IntList() }.add(start)
        }
        return map
    }

    // ---- 강조 ------------------------------------------------------------------

    private fun delimiterRun(root: MdInline, c: Char) {
        val start = pos
        while (pos < s.length && s[pos] == c) pos++
        val n = pos - start
        // 취소선은 물결 하나나 둘이다(GFM). 셋 이상은 글자다.
        if (c == '~' && n > 2) {
            appendText(root, s, start, pos)
            return
        }
        val before = if (start == 0) '\n' else s[start - 1]
        val after = if (pos >= s.length) '\n' else s[pos]
        val afterWs = isUnicodeWhitespace(after)
        val afterP = isPunctuation(after)
        val beforeWs = isUnicodeWhitespace(before)
        val beforeP = isPunctuation(before)
        val left = !afterWs && (!afterP || beforeWs || beforeP)
        val right = !beforeWs && (!beforeP || afterWs || afterP)
        val canOpen: Boolean
        val canClose: Boolean
        if (c == '_') {
            // 낱말 안의 밑줄은 강조가 아니다(`snake_case_name`).
            canOpen = left && (!right || beforeP)
            canClose = right && (!left || afterP)
        } else {
            canOpen = left
            canClose = right
        }
        val node = marker(s.substring(start, pos), root)
        if ((canOpen || canClose) && delims < MAX_DELIMITERS) {
            val d = Delim(c, n, n, node, delimiters, null, canOpen, canClose)
            delimiters?.next = d
            delimiters = d
            delims++
        }
    }

    /**
     * 구분자 쌓기를 [stackBottom] 위로 풀어 강조·굵게·취소선을 만든다. 명세의 'process emphasis' 그대로이고,
     * `openersBottom` 이 짝 없는 여는 것을 다시 훑지 않게 해서 전체가 거의 선형이다.
     */
    private fun processEmphasis(stackBottom: Delim?) {
        val openersBottom = HashMap<Int, Delim?>()
        // [stackBottom] 바로 위의 구분자를 찾는다. **[stackBottom] 에 닿으면 멈춘다** — 참조 구현(commonmark.js)처럼
        // `closer.previous !== stackBottom` 을 조건으로 걸면, 바닥이 쌓기의 맨 위일 때(링크 안에 구분자가 없을 때)
        // 멈출 자리를 지나쳐 쌓기 전체를 바닥까지 훑는다. 링크·그림마다 그러면 `~![a](b)` 를 되풀이한 문단이
        // 제곱이 된다(검토가 잰 값: 40만 글자 한 문단에 2.2초. 노드 상한이 문단 하나를 묶을 뿐 문단마다 되풀이되어,
        // `MarkdownLimitsTest` 의 1.6M 글자 두 벌은 75초였다).
        var closer: Delim? = null
        var c = delimiters
        while (c != null && c !== stackBottom) {
            closer = c
            c = c.previous
        }
        // 바닥이 쌓기에 없다(있을 수 없는 일이다). 예전 모양과 같이 아무것도 풀지 않는다.
        if (c !== stackBottom) closer = null
        while (closer != null) {
            if (!closer.canClose) {
                closer = closer.next
                continue
            }
            val cc = closer.cc
            val key = cc.code * 8 + (if (closer.canOpen) 4 else 0) + closer.origDelims % 3
            val bottom = if (openersBottom.containsKey(key)) openersBottom[key] else stackBottom
            var opener = closer.previous
            var found = false
            while (opener != null && opener !== stackBottom && opener !== bottom) {
                if (opener.cc == cc && opener.canOpen) {
                    if (cc == '~') {
                        // 취소선은 여닫는 물결 수가 같아야 한다.
                        if (opener.numDelims == closer.numDelims) {
                            found = true
                            break
                        }
                    } else {
                        // '셋의 규칙' — 여닫기를 겸하는 달리기끼리는 길이의 합이 3의 배수면 짝이 아니다.
                        val odd = (closer.canOpen || opener.canClose) && closer.origDelims % 3 != 0 &&
                            (opener.origDelims + closer.origDelims) % 3 == 0
                        if (!odd) {
                            found = true
                            break
                        }
                    }
                }
                opener = opener.previous
            }
            val oldCloser = closer
            if (found && opener != null) {
                val use = when {
                    cc == '~' -> closer.numDelims
                    closer.numDelims >= 2 && opener.numDelims >= 2 -> 2
                    else -> 1
                }
                val openerInl = opener.node
                val closerInl = closer.node
                var h = 0
                var t = openerInl.next
                while (t != null && t !== closerInl) {
                    if (t.height > h) h = t.height
                    t = t.next
                }
                if (h + 1 > MAX_DEPTH) {
                    // 너무 깊다 — 짝을 찾지 못한 것으로 친다. 구분자는 글자로 남는다.
                    found = false
                } else {
                    opener.numDelims -= use
                    closer.numDelims -= use
                    openerInl.buf!!.setLength(openerInl.buf!!.length - use)
                    closerInl.buf!!.setLength(closerInl.buf!!.length - use)
                    val emph = MdInline(
                        when {
                            cc == '~' -> InlineType.STRIKE
                            use == 1 -> InlineType.EMPH
                            else -> InlineType.STRONG
                        },
                    )
                    emph.height = h + 1
                    nodes++
                    t = openerInl.next
                    while (t != null && t !== closerInl) {
                        val nx = t.next
                        emph.append(t)
                        t = nx
                    }
                    openerInl.insertAfter(emph)
                    // 사이의 구분자는 이제 짝을 찾을 수 없다.
                    if (opener.next !== closer) {
                        opener.next = closer
                        closer.previous = opener
                    }
                    if (opener.numDelims == 0) {
                        openerInl.unlink()
                        removeDelimiter(opener)
                    }
                    if (closer.numDelims == 0) {
                        val nx = closer.next
                        closerInl.unlink()
                        removeDelimiter(closer)
                        closer = nx
                    }
                }
            }
            if (!found) {
                closer = oldCloser.next
                openersBottom[key] = oldCloser.previous
                if (!oldCloser.canOpen) removeDelimiter(oldCloser)
            }
        }
        while (delimiters != null && delimiters !== stackBottom) removeDelimiter(delimiters!!)
    }

    private fun removeDelimiter(d: Delim) {
        d.previous?.next = d.next
        if (d.next != null) d.next!!.previous = d.previous else delimiters = d.previous
        d.previous = null
        d.next = null
    }

    // ---- 링크 ------------------------------------------------------------------

    private fun addBracket(node: MdInline, index: Int, image: Boolean) {
        brackets?.bracketAfter = true
        brackets = Bracket(node, brackets, delimiters, index, image, bracketSeq++)
    }

    private fun closeBracket(root: MdInline) {
        pos++ // `]` 다음
        val startPos = pos
        val opener = brackets
        if (opener == null) {
            appendText(root, "]")
            return
        }
        if (!opener.active) {
            appendText(root, "]")
            brackets = opener.previous
            return
        }
        val image = opener.image
        var dest: String? = null
        var matched = false

        // ① 바로 뒤의 `(목적지 "제목")`
        if (pos < s.length && s[pos] == '(' && scanBudget > 0) {
            pos++
            spnl()
            val d = linkDestination()
            if (d != null) {
                spnl()
                // 제목은 공백 뒤에만 올 수 있다.
                if (pos > 0 && isUnicodeWhitespace(s[pos - 1])) linkTitle()
                spnl()
                if (pos < s.length && s[pos] == ')') {
                    pos++
                    matched = true
                    dest = d
                }
            }
            if (!matched) pos = startPos
        }
        // ② 이름으로 — `[글][이름]`, `[이름][]`, `[이름]`
        if (!matched) {
            val beforeLabel = pos
            val n = linkLabel()
            var label: String? = null
            if (n > 2) {
                label = s.substring(beforeLabel, beforeLabel + n)
            } else if (!opener.bracketAfter) {
                // 둘째 이름이 비었거나 없으면 첫 이름이 곧 이름이다. 안에 괄호가 있었으면 이름이 될 수 없다.
                label = s.substring(opener.index, startPos)
            }
            if (n == 0) pos = startPos
            if (label != null) {
                val ref = refs[normalizeLabel(label)]
                if (ref != null) {
                    dest = ref
                    matched = true
                }
            }
        }
        if (matched && opener.seq <= tooDeepUpTo) matched = false
        if (matched) {
            var h = 0
            var t = opener.node.next
            while (t != null) {
                if (t.height > h) h = t.height
                t = t.next
            }
            if (h + 1 > MAX_DEPTH) {
                matched = false
                // 이 괄호보다 먼저 열린 괄호는 모두 이 깊은 노드를 품게 되니 더 볼 것도 없다. 표시만 해 두지 않으면
                // 바깥 괄호마다 늘어나는 형제를 다시 훑어 `![` 를 겹겹이 쌓은 줄이 제곱이 된다(시험이 158초를 쟀다).
                tooDeepUpTo = opener.seq
            }
        }
        if (!matched) {
            brackets = opener.previous
            pos = startPos
            appendText(root, "]")
            return
        }
        val node = MdInline(if (image) InlineType.IMAGE else InlineType.LINK)
        node.dest = dest ?: ""
        nodes++
        var t = opener.node.next
        var h = 0
        while (t != null) {
            val nx = t.next
            if (t.height > h) h = t.height
            node.append(t)
            t = nx
        }
        node.height = h + 1
        root.append(node)
        processEmphasis(opener.previousDelimiter)
        brackets = opener.previous
        opener.node.unlink()
        if (!image) {
            // 링크 안에 링크를 두지 않는다 — 앞의 여는 괄호를 모두 끈다. 이미 끈 데까지는 다시 훑지 않는다.
            var o = brackets
            while (o != null && o.seq > deactivatedUpTo) {
                if (!o.image) o.active = false
                o = o.previous
            }
            deactivatedUpTo = bracketSeq - 1
        }
    }

    /** 공백과 줄바꿈 하나까지 건너뛴다. */
    private fun spnl() {
        while (pos < s.length && (s[pos] == ' ' || s[pos] == '\t')) pos++
        if (pos < s.length && s[pos] == '\n') {
            pos++
            while (pos < s.length && (s[pos] == ' ' || s[pos] == '\t')) pos++
        }
    }

    /** `<…>` 나 괄호 균형이 맞는 한 덩어리. 못 읽으면 null(자리는 그대로). */
    private fun linkDestination(): String? {
        if (pos < s.length && s[pos] == '<') {
            var i = pos + 1
            val limit = minOf(s.length, pos + 1 + MAX_DEST_CHARS)
            while (i < limit) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length && isAsciiPunct(s[i + 1])) {
                    i += 2
                    continue
                }
                if (c == '>') {
                    val raw = s.substring(pos + 1, i)
                    scanBudget -= i - pos
                    pos = i + 1
                    return unescape(raw)
                }
                if (c == '<' || c == '\n') break
                i++
            }
            scanBudget -= i - pos
            return null
        }
        val start = pos
        var i = pos
        var depth = 0
        val limit = minOf(s.length, pos + MAX_DEST_CHARS)
        while (i < limit) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length && isAsciiPunct(s[i + 1])) {
                i += 2
                continue
            }
            if (c == '(') {
                depth++
                if (depth > MAX_PAREN_DEPTH) break
            } else if (c == ')') {
                if (depth == 0) break
                depth--
            } else if (c <= ' ') {
                break
            }
            i++
        }
        scanBudget -= i - start
        if (i >= limit && limit < s.length) return null // 너무 길다
        if (depth != 0) return null
        if (i == start && (i >= s.length || s[i] != ')')) return null
        pos = i
        return unescape(s.substring(start, i))
    }

    /** `"…"`·`'…'`·`(…)`. 못 읽으면 null(자리는 그대로). 값은 쓰지 않는다 — 제목을 화면에 띄우지 않는다. */
    private fun linkTitle(): String? {
        if (pos >= s.length) return null
        val open = s[pos]
        val close = when (open) {
            '"' -> '"'
            '\'' -> '\''
            '(' -> ')'
            else -> return null
        }
        var i = pos + 1
        val limit = minOf(s.length, pos + 1 + MAX_TITLE_CHARS)
        while (i < limit) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length && isAsciiPunct(s[i + 1])) {
                i += 2
                continue
            }
            if (c == close) {
                val t = s.substring(pos + 1, i)
                scanBudget -= i - pos
                pos = i + 1
                return unescape(t)
            }
            if (open == '(' && c == '(') break
            i++
        }
        scanBudget -= i - pos
        return null
    }

    /** `[이름]` 의 길이(괄호 포함). 없으면 0. 있으면 자리를 그 뒤로 옮긴다. */
    private fun linkLabel(): Int {
        if (pos >= s.length || s[pos] != '[' || scanBudget <= 0) return 0
        var i = pos + 1
        val limit = minOf(s.length, pos + 1 + MAX_LABEL_CHARS + 1)
        while (i < limit) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                i += 2
                continue
            }
            if (c == '[') break
            if (c == ']') {
                val n = i - pos + 1
                scanBudget -= n
                pos = i + 1
                return n
            }
            i++
        }
        scanBudget -= i - pos
        return 0
    }

    // ---- 자동 링크·엔티티 ------------------------------------------------------------

    private fun autolink(root: MdInline): Boolean {
        var i = pos + 1
        val limit = minOf(s.length, pos + 1 + MAX_AUTOLINK_CHARS)
        while (i < limit && s[i] != '>' && s[i] != '<' && s[i] > ' ') i++
        if (i >= limit || s[i] != '>') return false
        val body = s.substring(pos + 1, i)
        if (!isUriAutolink(body) && !isEmailAutolink(body)) return false
        leaf(InlineType.AUTOLINK, root, body)
        pos = i + 1
        return true
    }

    private fun entity(root: MdInline): Boolean {
        val e = decodeEntityAt(s, pos) ?: return false
        appendText(root, e.first)
        pos = e.second
        return true
    }

    // ---- 참조 정의 ----------------------------------------------------------------

    /**
     * [text] 의 [start] 자리에서 `[이름]: 목적지 "제목"` 하나를 읽어 [refs] 에 더한다.
     *
     * @return 읽은 글자 수(줄 끝 포함). 정의가 아니면 0.
     */
    fun parseReference(text: String, start: Int): Int {
        reset(text, start)
        val labelLen = linkLabel()
        if (labelLen == 0) return 0
        val rawLabel = s.substring(start, start + labelLen)
        if (pos >= s.length || s[pos] != ':') return 0
        pos++
        spnl()
        // 빈 목적지는 `<>` 로만 쓸 수 있다 — 그냥 비어 있으면 [linkDestination] 이 null 을 준다.
        val dest = linkDestination() ?: return 0
        val beforeTitle = pos
        spnl()
        if (pos != beforeTitle) {
            if (linkTitle() == null) pos = beforeTitle
        }
        // 줄 끝이어야 한다. 제목 뒤에 무엇이 있으면 제목 없이 다시 본다.
        if (!atLineEnd()) {
            pos = beforeTitle
            if (!atLineEnd()) return 0
        }
        val key = normalizeLabel(rawLabel)
        if (key.isEmpty()) return 0
        if (key !in refs && refs.size < MAX_REFS) refs[key] = dest
        return pos - start
    }

    /** 공백 뒤 줄 끝(또는 글 끝)이면 그 뒤로 옮기고 참. */
    private fun atLineEnd(): Boolean {
        var i = pos
        while (i < s.length && (s[i] == ' ' || s[i] == '\t')) i++
        if (i < s.length && s[i] != '\n') return false
        pos = if (i < s.length) i + 1 else i
        return true
    }

    companion object {
        /** 강조·링크의 중첩 깊이. 그리기가 되부름이라 스택을 지키는 값이다. */
        const val MAX_DEPTH = 24

        /** 문단 하나의 노드 상한. 넘으면 나머지는 글자로 남는다. */
        const val MAX_NODES = 200_000

        private const val MAX_DELIMITERS = 50_000

        /** 링크 목적지·제목·이름을 훑는 데 문단 하나가 쓸 수 있는 글자 수. 넘으면 이름으로 찾는 링크만 본다. */
        const val SCAN_BUDGET = 4_000_000

        private const val MAX_DEST_CHARS = 4_096
        private const val MAX_TITLE_CHARS = 4_096
        private const val MAX_LABEL_CHARS = 999
        private const val MAX_PAREN_DEPTH = 32
        private const val MAX_AUTOLINK_CHARS = 2_048
        private const val MAX_REFS = 20_000

        fun isAsciiPunct(c: Char): Boolean =
            c in '!'..'/' || c in ':'..'@' || c in '['..'`' || c in '{'..'~'

        fun isPunctuation(c: Char): Boolean {
            if (c.code < 128) return isAsciiPunct(c)
            return when (Character.getType(c)) {
                Character.CONNECTOR_PUNCTUATION.toInt(), Character.DASH_PUNCTUATION.toInt(),
                Character.START_PUNCTUATION.toInt(), Character.END_PUNCTUATION.toInt(),
                Character.INITIAL_QUOTE_PUNCTUATION.toInt(), Character.FINAL_QUOTE_PUNCTUATION.toInt(),
                Character.OTHER_PUNCTUATION.toInt(), Character.MATH_SYMBOL.toInt(),
                Character.CURRENCY_SYMBOL.toInt(), Character.MODIFIER_SYMBOL.toInt(),
                Character.OTHER_SYMBOL.toInt(),
                -> true
                else -> false
            }
        }

        fun isUnicodeWhitespace(c: Char): Boolean =
            c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\u000C' ||
                Character.getType(c) == Character.SPACE_SEPARATOR.toInt()

        /** 참조 이름의 비교 형태 — 괄호를 떼고, 공백을 하나로, 대소문자를 접는다. */
        fun normalizeLabel(label: String): String {
            val inner = if (label.length >= 2) label.substring(1, label.length - 1) else label
            val out = StringBuilder(inner.length)
            var space = false
            for (c in inner.trim()) {
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    space = true
                } else {
                    if (space && out.isNotEmpty()) out.append(' ')
                    space = false
                    out.append(c)
                }
            }
            return out.toString().lowercase(Locale.ROOT).uppercase(Locale.ROOT)
        }

        /** 백슬래시 이스케이프와 엔티티를 푼다(목적지·코드 블록의 언어 이름). */
        fun unescape(text: String): String {
            if ('\\' !in text && '&' !in text) return text
            val out = StringBuilder(text.length)
            var i = 0
            while (i < text.length) {
                val c = text[i]
                if (c == '\\' && i + 1 < text.length && isAsciiPunct(text[i + 1])) {
                    out.append(text[i + 1])
                    i += 2
                    continue
                }
                if (c == '&') {
                    val e = decodeEntityAt(text, i)
                    if (e != null) {
                        out.append(e.first)
                        i = e.second
                        continue
                    }
                }
                out.append(c)
                i++
            }
            return out.toString()
        }

        /**
         * [at] 의 `&` 에서 엔티티 하나를 푼다. 글자와 그 다음 자리. 엔티티가 아니면 null.
         *
         * 이름 있는 엔티티는 **자주 쓰는 것만** 안다([ENTITIES]). 모르는 이름은 글자 그대로 남는다 — 틀린
         * 글자로 바꾸는 것보다 원문을 보이는 편이 낫다.
         */
        fun decodeEntityAt(text: String, at: Int): Pair<String, Int>? {
            if (at + 2 >= text.length || text[at] != '&') return null
            var i = at + 1
            if (text[i] == '#') {
                i++
                val hex = i < text.length && (text[i] == 'x' || text[i] == 'X')
                if (hex) i++
                val digitsStart = i
                var value = 0
                val maxDigits = if (hex) 6 else 7
                while (i < text.length && i - digitsStart < maxDigits) {
                    val d = Character.digit(text[i], if (hex) 16 else 10)
                    if (d < 0) break
                    value = value * (if (hex) 16 else 10) + d
                    i++
                }
                if (i == digitsStart || i >= text.length || text[i] != ';') return null
                val cp = if (value == 0 || value > 0x10FFFF || value in 0xD800..0xDFFF) 0xFFFD else value
                return String(Character.toChars(cp)) to i + 1
            }
            val nameStart = i
            while (i < text.length && i - nameStart < 32 && text[i].isLetterOrDigit() && text[i].code < 128) i++
            if (i == nameStart || i >= text.length || text[i] != ';') return null
            val decoded = ENTITIES[text.substring(nameStart, i)] ?: return null
            return decoded to i + 1
        }

        private fun isUriAutolink(body: String): Boolean {
            val colon = body.indexOf(':')
            if (colon < 2 || colon > 32) return false
            if (!body[0].isAsciiLetter()) return false
            for (k in 1 until colon) {
                val c = body[k]
                if (!(c.isAsciiLetter() || c in '0'..'9' || c == '+' || c == '.' || c == '-')) return false
            }
            return true
        }

        private fun isEmailAutolink(body: String): Boolean {
            val at = body.indexOf('@')
            if (at <= 0 || at == body.length - 1) return false
            for (k in 0 until at) {
                val c = body[k]
                if (!(c.isAsciiLetter() || c in '0'..'9' || c in ".!#$%&'*+/=?^_`{|}~-")) return false
            }
            val labels = body.substring(at + 1).split('.')
            for (label in labels) {
                if (label.isEmpty() || label.length > 63) return false
                if (label.first() == '-' || label.last() == '-') return false
                if (!label.all { it.isAsciiLetter() || it in '0'..'9' || it == '-' }) return false
            }
            return true
        }

        private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

        /** 자주 쓰는 이름 있는 엔티티. HTML 의 2,000여 개를 다 싣지 않는다 — README 에 나오는 것이 이 정도다. */
        private val ENTITIES: Map<String, String> = mapOf(
            "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
            "copy" to "©", "reg" to "®", "trade" to "™", "hellip" to "…", "mdash" to "—", "ndash" to "–",
            "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“", "rdquo" to "”", "laquo" to "«", "raquo" to "»",
            "middot" to "·", "bull" to "•", "times" to "×", "divide" to "÷", "deg" to "°", "plusmn" to "±",
            "para" to "¶", "sect" to "§", "micro" to "µ", "frac12" to "½", "frac14" to "¼", "frac34" to "¾",
            "sup2" to "²", "sup3" to "³", "larr" to "←", "rarr" to "→", "uarr" to "↑", "darr" to "↓",
            "harr" to "↔", "lArr" to "⇐", "rArr" to "⇒", "hArr" to "⇔", "le" to "≤", "ge" to "≥",
            "ne" to "≠", "asymp" to "≈", "equiv" to "≡", "infin" to "∞", "minus" to "−", "radic" to "√",
            "sum" to "∑", "prod" to "∏", "part" to "∂", "int" to "∫", "forall" to "∀", "exist" to "∃",
            "isin" to "∈", "notin" to "∉", "cap" to "∩", "cup" to "∪", "sub" to "⊂", "sup" to "⊃",
            "and" to "∧", "or" to "∨", "not" to "¬", "empty" to "∅", "nabla" to "∇",
            "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ", "epsilon" to "ε", "lambda" to "λ",
            "mu" to "μ", "pi" to "π", "sigma" to "σ", "tau" to "τ", "phi" to "φ", "omega" to "ω",
            "Alpha" to "Α", "Beta" to "Β", "Gamma" to "Γ", "Delta" to "Δ", "Sigma" to "Σ", "Omega" to "Ω",
            "euro" to "€", "pound" to "£", "yen" to "¥", "cent" to "¢", "curren" to "¤",
            "iexcl" to "¡", "iquest" to "¿", "shy" to "­", "zwj" to "‍", "zwnj" to "‌",
            "ensp" to " ", "emsp" to " ", "thinsp" to " ", "dagger" to "†", "Dagger" to "‡",
            "permil" to "‰", "prime" to "′", "Prime" to "″", "oline" to "‾", "check" to "✓", "cross" to "✗",
            "star" to "☆", "starf" to "★", "hearts" to "♥", "spades" to "♠", "clubs" to "♣", "diams" to "♦",
            "Tab" to "\t", "NewLine" to "\n", "excl" to "!", "num" to "#", "dollar" to "$", "percnt" to "%",
            "ast" to "*", "lpar" to "(", "rpar" to ")", "lsqb" to "[", "rsqb" to "]", "lowbar" to "_",
            "grave" to "`", "lcub" to "{", "rcub" to "}", "verbar" to "|", "vert" to "|", "tilde" to "˜",
            "colon" to ":", "semi" to ";", "comma" to ",", "period" to ".", "quest" to "?", "commat" to "@",
            "sol" to "/", "bsol" to "\\", "equals" to "=", "plus" to "+", "Hat" to "^",
        )
    }
}
