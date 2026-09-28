package io.github.donggi.iroiroviewer.format.text

/*
 * 마크다운의 **블록** — 제목·문단·인용·목록·코드 블록·가로줄·표.
 *
 * CommonMark 의 줄 단위 알고리즘을 따른다: 줄마다 ① 열려 있는 그릇(인용·목록 항목)이 이 줄에서도
 * 이어지는지 보고, ② 새 블록이 시작되는지 보고, ③ 남은 글자를 알맞은 잎(문단·코드)에 붙인다. 되부름이
 * 없고 줄마다 한 번 지나가므로 입력 크기에 선형이다.
 *
 * DOM 을 만들지 않는다는 저장소 규칙과 어긋나 보이지만 사정이 다르다 — 여기서 만드는 나무는 **우리가
 * 정한 상한 안에서만** 자란다. 그릇의 깊이는 [MdBlockParser.MAX_DEPTH], 노드 수는
 * [MdBlockParser.MAX_BLOCKS] 로 묶이고, 넘으면 멈추고 '줄였다' 를 알린다.
 */

internal enum class BlockType { DOCUMENT, BLOCK_QUOTE, LIST, ITEM, PARAGRAPH, HEADING, THEMATIC_BREAK, CODE_BLOCK, TABLE }

/**
 * 마크다운이 벗기는 앞뒤 공백 — **공백·탭·줄 끝뿐이다.** 코틀린의 `trim()` 은 유니코드 공백(전각 공백 U+3000,
 * 줄바꿈 없는 공백 U+00A0)까지 벗겨, 일본어 글에서 흔한 전각 들여쓰기가 문단 첫머리에서 사라진다. 명세(문단·제목·
 * 울타리의 언어 이름)와 GFM(표의 칸)은 이 넷만 벗긴다.
 */
internal fun String.trimMd(): String = trim { it == ' ' || it == '\t' || it == '\n' || it == '\r' }

internal enum class Align { NONE, LEFT, CENTER, RIGHT }

internal class ListData(
    val ordered: Boolean,
    val bulletChar: Char,
    val delimiter: Char,
    val start: Int,
    /** 표지 앞의 들여쓰기. */
    val markerOffset: Int,
    /** 표지와 그 뒤 공백의 폭. 이어지는 줄은 `markerOffset + padding` 만큼 들여써야 항목 안이다. */
    val padding: Int,
)

internal class MdBlock(val type: BlockType) {
    var parent: MdBlock? = null
    val children = ArrayList<MdBlock>(0)
    var open = true
    var depth = 0

    /** 시작·끝 줄 번호. 목록이 느슨한가(항목 사이에 빈 줄이 있는가)를 이 둘의 틈으로 가른다. */
    var startLine = 0
    var endLine = 0

    /** 문단·제목·코드의 글. 잎만 갖는다. */
    var content: StringBuilder? = null
    var level = 0
    var fenced = false
    var fenceChar = ' '
    var fenceLength = 0
    var fenceOffset = 0
    var info = ""
    var literal = ""
    var list: ListData? = null
    var tight = true
    var aligns: List<Align> = emptyList()
    var header: List<String> = emptyList()
    var rows: ArrayList<List<String>>? = null
}

internal class MdBlockParser(private val checkpoint: () -> Unit = {}) {

    /** 참조 정의(`[이름]: 목적지`). 줄 안 문법을 풀기 **전에** 문서 전체에서 모은다 — 정의는 뒤에 와도 된다. */
    val refs = HashMap<String, String>()
    private val refParser = MdInlineParser(refs)

    val doc = MdBlock(BlockType.DOCUMENT)
    private var tip = doc
    private var oldTip = doc
    private var lastMatched = doc
    private var allClosed = true

    private var line = ""
    private var offset = 0
    private var nextNonspace = 0
    private var indent = 0
    private var blank = false
    private var lineNumber = 0
    private var blocks = 1

    /** 노드 상한에 닿아 뒤를 버렸다. */
    var truncated = false
        private set

    fun parse(text: String): MdBlock {
        val n = text.length
        var i = 0
        while (i < n && !truncated) {
            var e = i
            while (e < n && text[e] != '\n' && text[e] != '\r') e++
            incorporate(prepare(text, i, e))
            if (e < n && text[e] == '\r' && e + 1 < n && text[e + 1] == '\n') e++
            i = e + 1
            if (lineNumber % CHECK_EVERY_LINES == 0) checkpoint()
        }
        while (tip !== doc) finalize(tip, lineNumber)
        finalize(doc, lineNumber)
        return doc
    }

    /**
     * 줄 하나를 다듬는다. **탭을 4칸 공백으로 편다** — 들여쓰기로 뜻이 갈리는 문법이라 탭을 칸으로 세야
     * 하는데, 명세처럼 '반쯤 쓴 탭' 을 들고 다니는 대신 먼저 편다. 코드 블록 안의 탭 폭이 달라지는 것이 대가다.
     * NUL 은 명세대로 U+FFFD 로 바꾼다.
     */
    private fun prepare(text: String, from: Int, to: Int): String {
        var special = false
        for (k in from until to) {
            val c = text[k]
            if (c == '\t' || c == '\u0000') {
                special = true
                break
            }
        }
        if (!special) return text.substring(from, to)
        val sb = StringBuilder(to - from + 16)
        for (k in from until to) {
            when (val c = text[k]) {
                '\t' -> repeat(TAB_STOP - sb.length % TAB_STOP) { sb.append(' ') }
                '\u0000' -> sb.append('�')
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    // ---- 줄 하나 -----------------------------------------------------------------

    private fun incorporate(ln: String) {
        line = ln
        offset = 0
        lineNumber++
        var allMatched = true
        var container = doc
        oldTip = tip

        // ① 열린 그릇이 이 줄에서도 이어지는가
        while (true) {
            val last = container.children.lastOrNull() ?: break
            if (!last.open) break
            container = last
            findNextNonspace()
            when (continues(container)) {
                CONTINUE_MATCHED -> Unit
                CONTINUE_FAILED -> allMatched = false
                else -> return // 닫는 울타리까지 읽었다
            }
            if (!allMatched) {
                container = container.parent!!
                break
            }
        }
        allClosed = container === oldTip
        lastMatched = container

        // ② 새 블록이 시작되는가 — 코드 블록 안이면 보지 않는다
        var matchedLeaf = container.type == BlockType.CODE_BLOCK
        while (!matchedLeaf) {
            findNextNonspace()
            if (indent < CODE_INDENT && !maybeSpecial()) {
                offset = nextNonspace
                break
            }
            when (starts(container)) {
                START_CONTAINER -> container = tip
                START_LEAF -> {
                    container = tip
                    matchedLeaf = true
                }
                else -> {
                    offset = nextNonspace
                    break
                }
            }
        }

        // ③ 남은 글자
        if (!allClosed && !blank && tip.type == BlockType.PARAGRAPH) {
            // 게으른 이어짐 — `>` 없이 이어 쓴 인용 문단의 둘째 줄.
            addLine()
            return
        }
        closeUnmatched()
        when (container.type) {
            BlockType.PARAGRAPH, BlockType.CODE_BLOCK -> addLine()
            BlockType.TABLE -> if (!blank && offset < line.length) addRow(container)
            else -> if (offset < line.length && !blank) {
                addChild(BlockType.PARAGRAPH)
                offset = nextNonspace
                addLine()
            }
        }
    }

    private fun findNextNonspace() {
        var i = offset
        while (i < line.length && line[i] == ' ') i++
        nextNonspace = i
        indent = i - offset
        blank = i >= line.length
    }

    private fun maybeSpecial(): Boolean {
        if (nextNonspace >= line.length) return false
        return when (line[nextNonspace]) {
            '#', '`', '~', '*', '+', '_', '=', '<', '>', '-', '|', ':' -> true
            in '0'..'9' -> true
            else -> false
        }
    }

    /** 열린 블록이 이 줄에서도 이어지는가. */
    private fun continues(b: MdBlock): Int = when (b.type) {
        BlockType.DOCUMENT, BlockType.LIST -> CONTINUE_MATCHED
        BlockType.BLOCK_QUOTE ->
            if (!blank && indent <= 3 && line[nextNonspace] == '>') {
                offset = nextNonspace + 1
                if (offset < line.length && line[offset] == ' ') offset++
                CONTINUE_MATCHED
            } else {
                CONTINUE_FAILED
            }
        BlockType.ITEM -> {
            val d = b.list!!
            when {
                // 빈 줄로 시작한 항목은 빈 줄 하나까지만 품는다.
                blank -> if (b.children.isEmpty()) {
                    CONTINUE_FAILED
                } else {
                    offset = nextNonspace
                    CONTINUE_MATCHED
                }
                indent >= d.markerOffset + d.padding -> {
                    offset = minOf(line.length, offset + d.markerOffset + d.padding)
                    CONTINUE_MATCHED
                }
                else -> CONTINUE_FAILED
            }
        }
        BlockType.HEADING, BlockType.THEMATIC_BREAK -> CONTINUE_FAILED
        BlockType.CODE_BLOCK -> if (b.fenced) {
            if (isClosingFence(b)) {
                finalize(b, lineNumber)
                CONTINUE_CLOSED
            } else {
                var k = b.fenceOffset
                while (k > 0 && offset < line.length && line[offset] == ' ') {
                    offset++
                    k--
                }
                CONTINUE_MATCHED
            }
        } else {
            when {
                indent >= CODE_INDENT -> {
                    offset += CODE_INDENT
                    CONTINUE_MATCHED
                }
                blank -> {
                    offset = nextNonspace
                    CONTINUE_MATCHED
                }
                else -> CONTINUE_FAILED
            }
        }
        BlockType.PARAGRAPH, BlockType.TABLE -> if (blank) CONTINUE_FAILED else CONTINUE_MATCHED
    }

    private fun isClosingFence(b: MdBlock): Boolean {
        if (indent > 3 || nextNonspace >= line.length || line[nextNonspace] != b.fenceChar) return false
        var i = nextNonspace
        while (i < line.length && line[i] == b.fenceChar) i++
        if (i - nextNonspace < b.fenceLength) return false
        while (i < line.length && line[i] == ' ') i++
        return i == line.length
    }

    // ---- 새 블록 --------------------------------------------------------------------

    private fun starts(container: MdBlock): Int {
        val indented = indent >= CODE_INDENT
        val c = if (nextNonspace < line.length) line[nextNonspace] else '\n'
        if (!indented) {
            if (c == '>' && container.depth + 1 <= MAX_DEPTH) {
                offset = nextNonspace + 1
                if (offset < line.length && line[offset] == ' ') offset++
                closeUnmatched()
                addChild(BlockType.BLOCK_QUOTE)
                return START_CONTAINER
            }
            if (c == '#' && atxHeading()) return START_LEAF
            if ((c == '`' || c == '~') && fence()) return START_LEAF
            if (container.type == BlockType.PARAGRAPH && table(container)) return START_LEAF
            if (container.type == BlockType.PARAGRAPH && (c == '=' || c == '-') && setext(container)) return START_LEAF
            if ((c == '*' || c == '-' || c == '_') && isThematicBreak()) {
                closeUnmatched()
                addChild(BlockType.THEMATIC_BREAK)
                offset = line.length
                return START_LEAF
            }
        }
        if ((!indented || container.type == BlockType.LIST) && container.depth + 2 <= MAX_DEPTH) {
            val data = listMarker(container)
            if (data != null) {
                closeUnmatched()
                val cur = tip.list
                if (tip.type != BlockType.LIST || cur == null || !listsMatch(cur, data)) {
                    addChild(BlockType.LIST).list = data
                }
                addChild(BlockType.ITEM).list = data
                return START_CONTAINER
            }
        }
        if (indented && tip.type != BlockType.PARAGRAPH && !blank) {
            offset += CODE_INDENT
            closeUnmatched()
            addChild(BlockType.CODE_BLOCK)
            return START_LEAF
        }
        return START_NONE
    }

    private fun atxHeading(): Boolean {
        var i = nextNonspace
        while (i < line.length && line[i] == '#' && i - nextNonspace < 7) i++
        val level = i - nextNonspace
        if (level > 6) return false
        if (i < line.length && line[i] != ' ') return false
        closeUnmatched()
        val h = addChild(BlockType.HEADING)
        h.level = level
        var s = i
        var e = line.length
        while (s < e && line[s] == ' ') s++
        while (e > s && line[e - 1] == ' ') e--
        // 닫는 `#` 들 — 앞에 공백이 있거나 내용이 그것뿐일 때만 닫는 것이다(`# C#` 의 `#` 는 글이다).
        var k = e
        while (k > s && line[k - 1] == '#') k--
        if (k < e && (k == s || line[k - 1] == ' ')) {
            e = k
            while (e > s && line[e - 1] == ' ') e--
        }
        h.content = StringBuilder(line.substring(s, e))
        offset = line.length
        return true
    }

    private fun fence(): Boolean {
        val ch = line[nextNonspace]
        var i = nextNonspace
        while (i < line.length && line[i] == ch) i++
        val n = i - nextNonspace
        if (n < 3) return false
        // 백틱 울타리의 언어 이름에는 백틱이 없어야 한다(아니면 줄 안 코드다).
        if (ch == '`' && line.indexOf('`', i) >= 0) return false
        closeUnmatched()
        val b = addChild(BlockType.CODE_BLOCK)
        b.fenced = true
        b.fenceChar = ch
        b.fenceLength = n
        b.fenceOffset = indent
        offset = i
        return true
    }

    private fun setext(para: MdBlock): Boolean {
        val ch = line[nextNonspace]
        var i = nextNonspace
        while (i < line.length && line[i] == ch) i++
        while (i < line.length && line[i] == ' ') i++
        if (i != line.length) return false
        closeUnmatched()
        // 앞머리의 참조 정의를 먼저 떼어 낸다. 남는 것이 없으면 제목이 아니다(`---` 는 가로줄로 다시 본다).
        val rest = stripReferences(para) ?: return false
        val h = MdBlock(BlockType.HEADING)
        h.level = if (ch == '=') 1 else 2
        h.content = StringBuilder(rest)
        replace(para, h)
        offset = line.length
        return true
    }

    /**
     * GFM 표. 문단의 **마지막 줄**이 머리 줄이고 이 줄이 구분 줄이다. 둘 다 `|` 를 가져야 한다 — `a` 와 `---`
     * 만으로는 Setext 제목과 가를 수 없다. 머리 줄 앞의 줄들은 문단으로 남는다.
     */
    private fun table(para: MdBlock): Boolean {
        val aligns = delimiterRow() ?: return false
        val content = para.content ?: return false
        var end = content.length
        if (end > 0 && content[end - 1] == '\n') end--
        val startOfLast = content.lastIndexOf("\n", end - 1) + 1
        if (startOfLast > end) return false
        val headerLine = content.substring(startOfLast, end)
        if ('|' !in headerLine) return false
        val header = splitRow(headerLine)
        if (header.size != aligns.size) return false
        closeUnmatched()
        content.setLength(startOfLast)
        val parent = para.parent!!
        if (content.isBlank()) {
            para.open = false
            removeChild(parent, para)
            tip = parent
        } else {
            finalize(para, lineNumber - 2)
        }
        val t = addChild(BlockType.TABLE)
        t.startLine = lineNumber - 1
        t.header = header
        t.aligns = aligns
        t.rows = ArrayList()
        offset = line.length
        return true
    }

    private fun delimiterRow(): List<Align>? {
        var e = line.length
        while (e > nextNonspace && line[e - 1] == ' ') e--
        if (nextNonspace >= e) return null
        var pipe = false
        for (k in nextNonspace until e) {
            when (line[k]) {
                '|' -> pipe = true
                '-', ':', ' ' -> Unit
                else -> return null
            }
        }
        if (!pipe) return null
        val cells = splitRow(line.substring(nextNonspace, e))
        if (cells.isEmpty() || cells.size > MAX_COLUMNS) return null
        return cells.map { cell ->
            val dashes = cell.trim(':')
            if (dashes.isEmpty() || dashes.any { it != '-' }) return null
            val left = cell.startsWith(':')
            val right = cell.endsWith(':')
            when {
                left && right -> Align.CENTER
                left -> Align.LEFT
                right -> Align.RIGHT
                else -> Align.NONE
            }
        }
    }

    private fun addRow(table: MdBlock) {
        val cells = splitRow(line.substring(offset))
        val n = table.header.size
        val row = ArrayList<String>(n)
        for (k in 0 until n) row.add(cells.getOrElse(k) { "" })
        table.rows!!.add(row)
        if (++blocks > MAX_BLOCKS) truncated = true
    }

    private fun isThematicBreak(): Boolean {
        val ch = line[nextNonspace]
        var count = 0
        for (k in nextNonspace until line.length) {
            val c = line[k]
            if (c == ch) count++ else if (c != ' ') return false
        }
        return count >= 3
    }

    private fun listMarker(container: MdBlock): ListData? {
        if (indent >= CODE_INDENT) return null
        val p = nextNonspace
        if (p >= line.length) return null
        val c = line[p]
        val ordered: Boolean
        var bullet = ' '
        var delimiter = ' '
        var start = 1
        val markerLen: Int
        if (c == '-' || c == '+' || c == '*') {
            ordered = false
            bullet = c
            markerLen = 1
        } else if (c in '0'..'9') {
            var i = p
            var v = 0
            while (i < line.length && line[i] in '0'..'9' && i - p < 9) {
                v = v * 10 + (line[i] - '0')
                i++
            }
            if (i >= line.length || (line[i] != '.' && line[i] != ')')) return null
            // 문단을 끊는 번호 목록은 1 로 시작해야 한다('2019. 10. 3.' 같은 문장을 목록으로 읽지 않게).
            if (container.type == BlockType.PARAGRAPH && v != 1) return null
            ordered = true
            delimiter = line[i]
            start = v
            markerLen = i - p + 1
        } else {
            return null
        }
        val after = p + markerLen
        if (after < line.length && line[after] != ' ') return null
        if (container.type == BlockType.PARAGRAPH) {
            var k = after
            while (k < line.length && line[k] == ' ') k++
            if (k >= line.length) return null // 빈 항목은 문단을 끊지 못한다
        }
        val spacesStart = after
        var k = spacesStart
        while (k < line.length && line[k] == ' ' && k - spacesStart < 5) k++
        val spaces = k - spacesStart
        val padding: Int
        if (spaces >= 5 || spaces < 1 || k >= line.length) {
            // 공백이 다섯 이상이면 그 뒤는 항목 안의 들여쓴 코드다 — 표지 뒤 한 칸만 표지의 몫이다.
            padding = markerLen + 1
            offset = spacesStart
            if (offset < line.length && line[offset] == ' ') offset++
        } else {
            padding = markerLen + spaces
            offset = k
        }
        return ListData(ordered, bullet, delimiter, start, indent, padding)
    }

    private fun listsMatch(a: ListData, b: ListData): Boolean =
        a.ordered == b.ordered && a.delimiter == b.delimiter && a.bulletChar == b.bulletChar

    // ---- 나무 --------------------------------------------------------------------

    private fun addLine() {
        val t = tip
        val sb = t.content ?: StringBuilder().also { t.content = it }
        if (offset < line.length) sb.append(line, offset, line.length)
        sb.append('\n')
    }

    private fun addChild(type: BlockType): MdBlock {
        closeUnmatched()
        while (!canContain(tip.type, type)) finalize(tip, lineNumber - 1)
        val b = MdBlock(type)
        b.parent = tip
        b.depth = tip.depth + 1
        b.startLine = lineNumber
        tip.children.add(b)
        tip = b
        if (++blocks > MAX_BLOCKS) truncated = true
        return b
    }

    private fun replace(old: MdBlock, new: MdBlock) {
        val parent = old.parent!!
        // 열린 블록은 언제나 부모의 마지막 자식이다. 앞에서부터 찾으면 제목 5만 개짜리 문서가 제곱이 된다.
        val kids = parent.children
        val at = if (kids.lastOrNull() === old) kids.lastIndex else kids.indexOf(old)
        new.parent = parent
        new.depth = old.depth
        new.startLine = old.startLine
        parent.children[at] = new
        old.open = false
        tip = new
    }

    /** 열린 블록은 마지막 자식이다 — 끝에서 뗀다(앞에서부터 찾으면 제곱). */
    private fun removeChild(parent: MdBlock, child: MdBlock) {
        val kids = parent.children
        if (kids.lastOrNull() === child) kids.removeAt(kids.lastIndex) else kids.remove(child)
    }

    private fun canContain(parent: BlockType, child: BlockType): Boolean = when (parent) {
        BlockType.DOCUMENT, BlockType.BLOCK_QUOTE, BlockType.ITEM -> child != BlockType.ITEM
        BlockType.LIST -> child == BlockType.ITEM
        else -> false
    }

    private fun closeUnmatched() {
        if (allClosed) return
        while (oldTip !== lastMatched) {
            val p = oldTip.parent ?: break
            finalize(oldTip, lineNumber - 1)
            oldTip = p
        }
        allClosed = true
    }

    private fun finalize(b: MdBlock, endLine: Int) {
        val above = b.parent
        b.open = false
        b.endLine = endLine
        when (b.type) {
            BlockType.PARAGRAPH -> {
                val rest = stripReferences(b)
                if (rest == null) {
                    if (above != null) removeChild(above, b)
                } else {
                    b.content = StringBuilder(rest)
                }
            }
            BlockType.CODE_BLOCK -> finalizeCode(b)
            BlockType.ITEM -> b.endLine = b.children.lastOrNull()?.endLine ?: b.startLine
            BlockType.LIST -> finalizeList(b)
            else -> Unit
        }
        tip = above ?: doc
    }

    /**
     * 문단 앞머리의 참조 정의를 읽어 [refs] 에 넣고 남은 글을 준다. 남는 것이 없으면 null.
     */
    private fun stripReferences(para: MdBlock): String? {
        val text = para.content?.toString() ?: return null
        var p = 0
        while (p < text.length && text[p] == '[') {
            val n = refParser.parseReference(text, p)
            if (n == 0) break
            p += n
        }
        val rest = if (p == 0) text else text.substring(p)
        return if (rest.isBlank()) null else rest
    }

    private fun finalizeCode(b: MdBlock) {
        val text = b.content?.toString().orEmpty()
        b.content = null
        if (b.fenced) {
            val nl = text.indexOf('\n')
            val first = if (nl < 0) text else text.substring(0, nl)
            b.info = MdInlineParser.unescape(first.trimMd())
            b.literal = if (nl < 0) "" else text.substring(nl + 1)
        } else {
            // 끝의 빈 줄은 코드가 아니다.
            var e = text.length
            while (e > 0 && (text[e - 1] == '\n' || text[e - 1] == ' ')) e--
            val nl = text.indexOf('\n', e)
            b.literal = if (nl < 0) text.substring(0, e) + "\n" else text.substring(0, nl + 1)
        }
    }

    /**
     * 항목 사이, 또는 한 항목 안의 블록 사이에 빈 줄이 있으면 느슨한 목록이다(문단을 `<p>` 로 싼다). 빈 줄은
     * 줄 번호의 틈으로 안다 — 명세의 참조 구현이 쓰는 방법이다.
     */
    private fun finalizeList(b: MdBlock) {
        var tight = true
        loop@ for ((i, item) in b.children.withIndex()) {
            val nextItem = b.children.getOrNull(i + 1)
            if (nextItem != null && nextItem.startLine - item.endLine > 1) {
                tight = false
                break
            }
            for ((j, sub) in item.children.withIndex()) {
                val nextSub = item.children.getOrNull(j + 1) ?: continue
                if (nextSub.startLine - sub.endLine > 1) {
                    tight = false
                    break@loop
                }
            }
        }
        b.tight = tight
        b.endLine = b.children.lastOrNull()?.endLine ?: b.startLine
    }

    companion object {
        /** 인용·목록의 중첩 상한. 그리기가 되부름이라 스택을 지키는 값이고, 넘는 표지는 글자로 남는다. */
        const val MAX_DEPTH = 32

        /** 블록(표의 행 포함) 상한. 넘으면 거기서 멈추고 '줄였다' 를 알린다. */
        const val MAX_BLOCKS = 100_000

        const val MAX_COLUMNS = 64

        private const val CODE_INDENT = 4
        private const val TAB_STOP = 4
        private const val CHECK_EVERY_LINES = 256

        private const val CONTINUE_MATCHED = 0
        private const val CONTINUE_FAILED = 1
        private const val CONTINUE_CLOSED = 2

        private const val START_NONE = 0
        private const val START_CONTAINER = 1
        private const val START_LEAF = 2

        /**
         * 표의 한 줄을 칸으로. 앞뒤의 `|` 는 떼고, `\|` 는 칸을 가르지 않고 **`|` 한 글자가 된다** — GFM 은 줄 안 문법을
         * 풀기 전에 이것을 풀어, 줄 안 코드(`` `a\|b` ``) 안에서도 `|` 로 보인다. 역슬래시는 **다음 글자와 한 쌍**이다:
         * `\\|` 는 역슬래시 하나(`\\`) 뒤의 칸 경계다(cmark-gfm 의 `escaped_char`).
         */
        fun splitRow(row: String): List<String> {
            val s = row.trimMd()
            var i = if (s.startsWith("|")) 1 else 0
            val cells = ArrayList<String>()
            val cur = StringBuilder()
            // 마지막 글자가 칸 경계였는가. 그렇다면 그 뒤의 빈 칸은 칸이 아니다(끝의 `|`).
            var endedWithPipe = false
            while (i < s.length) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) {
                    val next = s[i + 1]
                    if (next == '|') cur.append('|') else cur.append(c).append(next)
                    i += 2
                    endedWithPipe = false
                    continue
                }
                if (c == '|') {
                    cells.add(cur.toString().trimMd())
                    cur.setLength(0)
                    // 칸이 너무 많은 줄은 더 가르지 않는다 — 머리보다 많은 칸은 어차피 버린다.
                    if (cells.size > MAX_COLUMNS) return cells
                    i++
                    endedWithPipe = true
                    continue
                }
                cur.append(c)
                i++
                endedWithPipe = false
            }
            if (!endedWithPipe || cells.isEmpty()) cells.add(cur.toString().trimMd())
            return cells
        }
    }
}
