package io.github.donggi.iroirobrowser.core.adblock

/**
 * 광고 목록의 정규식 규칙(`/…/`)을 맞추는 작은 정규식 엔진 — **주소 길이에 비례하는 시간**으로 끝난다(톰슨 NFA 를 한 글자씩 함께 굴린다 —
 * 되짚지 않는다).
 *
 * **왜 따로 두나**(2026-10-16 보안 점검 — ReDoS). 앞 판은 목록의 정규식을 `Regex`(java.util.regex)로 맞췄다. 되짚는 엔진이라 `(a+)+$` 같은 한
 * 줄이 긴 주소를 만나면 시간이 주소 길이의 지수로 는다. 정규식 규칙은 낱말 색인 밖이라 하위 요청마다 돌고(WebView 의 요청 스레드) 시간 상한이
 * 없었다 — 사용자가 주소로 더한 목록의 한 줄, 또는 기존 규칙과 쪽이 지은 아주 긴 주소가 그 스레드를 세울 수 있었다. 읽은 글자를 세는
 * `CharSequence` 감싸개로 예산을 두는 길은 **안드로이드에서 듣지 않는다** — 안드로이드의 java.util.regex 는 ICU(네이티브)이고 `Matcher` 가
 * 입력을 `toString()` 으로 바꿔 ICU 에 넘긴다(SDK 소스 `java/util/regex/Matcher.java` 의 `reset` — `charAt` 이 불리지 않는다). JVM 에서도 글자를
 * 읽지 않고 도는 되짚기(입력 끝에서 `(?:a?|b?){40}` 이 갈래마다 0 글자로 맞는 것)는 세지 못한다.
 *
 * **받는 문법** — 실제 목록이 쓰는 것: 글자·`.`·글자 모둠(`[…]`, 범위, `\d \w \s` 와 그 반대)·묶음(`(…)`·`(?:…)`)·`|`·되풀이(`* + ?`·`{n}`·
 * `{n,}`·`{n,m}` — 게으른 꼴 `*?` 은 맞는지가 같아 같게 본다)·`^ $ \b \B`·`\t \n \r \f`·`\xHH`·`\uHHHH`·`\` + ASCII 기호. 뜻은 java.util.regex
 * 와 같다(JVM 시험이 무작위 무늬로 견준다). 그 밖은 받지 않고 그 규칙을 건너뛴다 — 앞뒤 보기·되돌아 가리키기(`\1`)·소유 되풀이(`*+`)·안쪽 깃발
 * (`(?i)`)·이름 붙은 묶음·겹친 모둠·모둠의 `&&`·`\p{…}`·`\Q…\E`, java 와 자바스크립트(목록이 쓰는 엔진)가 다르게 읽는 것(`[]…]`·`\v`·`\a`·`\e`),
 * 단언의 되풀이, 겹친 되풀이, 빈 글에 맞을 수 있는 것을 두 번 넘게 되풀이하는 것(`(\b|.){2,}` — 엔진마다 뜻이 다르다). 목록 넷(2026-10-04 받은
 * 것)의 정규식 규칙 58개는 모두 받는다(`RealListsBenchmark`).
 *
 * **걸음 예산** — 걸음은 명령 하나를 보거나 글자 하나를 견주는 것이다. 되짚지 않으므로 걸음이 (주소 길이 + 1) × 명령 수 × 2 를 넘지 않지만,
 * 명령이 많은 규칙과 아주 긴 주소(WebView 는 2 MB 까지 넘긴다)가 만나면 그것도 크다 — [budgetFor](주소 길이에 비례)를 넘으면 그 자리에서
 * [Result.OVER_BUDGET]. 같은 입력이면 언제나 같은 걸음이다(시간에 기대지 않는다).
 *
 * 대소문자 무시는 ASCII 만 접는다(맞추는 쪽이 주소를 소문자로 준다 — [Request.urlLower], 주소는 ASCII 다). 글자는 UTF-16 단위로 본다
 * (java.util.regex 는 코드 포인트 — 주소에는 BMP 밖 글자가 없다). 만든 뒤에는 바뀌지 않아 여러 스레드가 함께 쓴다.
 */
class ListRegex private constructor(
    /** 무늬의 글(목록의 `/` 사이). */
    val source: String,
    /** 대소문자를 가리지 않는다(`$match-case` 가 아니면). */
    val ignoreCase: Boolean,
    private val op: ByteArray,
    /** CHAR 면 모둠 번호, SPLIT·JMP 면 (첫) 갈 곳, ASSERT 면 단언의 종류. */
    private val arg: IntArray,
    /** SPLIT 의 둘째 갈 곳. */
    private val alt: IntArray,
    private val sets: Array<CharSet>,
    /** 맨 앞이 `^` — 주소의 처음에서만 시작하고, 그 실이 다 죽으면 끝낸다. */
    private val anchored: Boolean,
) {

    /** [find] 의 답. */
    enum class Result { MATCH, NO_MATCH, OVER_BUDGET }

    /** 받지 않는 무늬 — 까닭을 글로 싣는다(시험). */
    class Unsupported internal constructor(reason: String) : IllegalArgumentException(reason)

    /** 명령의 수 — 걸음은 (주소 길이 + 1) × 이것 × 2 를 넘지 않는다. */
    val size: Int get() = op.size

    /**
     * [input] 안에 맞는 곳이 있는가(`Regex.containsMatchIn` 과 같은 뜻). 걸음이 [budget] 을 넘으면 그 자리에서 [Result.OVER_BUDGET].
     * 여러 스레드가 함께 불러도 된다(쓰는 자리는 스레드마다 하나).
     */
    fun find(input: CharSequence, budget: Long = budgetFor(input.length)): Result = run(input, budget, null)

    /** [input] 을 끝까지 맞추는 데 든 걸음(예산 없이) — 예산을 정하는 근거와 시험. */
    internal fun stepsFor(input: CharSequence): Long {
        val out = LongArray(1)
        run(input, Long.MAX_VALUE, out)
        return out[0]
    }

    private fun run(input: CharSequence, budget: Long, stepsOut: LongArray?): Result {
        val count = op.size
        val scratch = SCRATCH.get()
        if (scratch.stack.size < count) {
            scratch.cur = Threads(count)
            scratch.next = Threads(count)
            scratch.stack = IntArray(count)
        }
        var cur = scratch.cur
        var next = scratch.next
        val stack = scratch.stack
        cur.size = 0
        var steps = 0L
        val length = input.length
        var pos = 0
        var result = Result.NO_MATCH
        while (true) {
            if (pos == 0 || !anchored) {
                val added = close(cur, 0, input, pos, stack)
                if (added < 0) {
                    result = Result.MATCH
                    break
                }
                steps += added
            }
            if (pos == length || (anchored && cur.size == 0)) break
            val c = input[pos]
            next.size = 0
            var matched = false
            for (k in 0 until cur.size) {
                val pc = cur.dense[k]
                if (op[pc] != CHAR) continue
                steps++
                if (sets[arg[pc]].contains(c)) {
                    val added = close(next, pc + 1, input, pos + 1, stack)
                    if (added < 0) {
                        matched = true
                        break
                    }
                    steps += added
                }
            }
            if (matched) {
                result = Result.MATCH
                break
            }
            if (steps > budget) {
                result = Result.OVER_BUDGET
                break
            }
            val t = cur
            cur = next
            next = t
            pos++
        }
        stepsOut?.set(0, steps)
        return result
    }

    /**
     * [from] 에서 글자를 읽지 않고 닿는 명령들을 [into] 에 더한다(단언은 [pos] 자리에서 본다). `MATCH` 에 닿으면 -1, 아니면 더한 수(걸음).
     * 명령 하나는 한 자리에서 한 번만 더해진다 — 비어 있을 수 있는 되풀이(`(a*)*`)가 끝없이 돌지 않고, 걸음이 명령 수를 넘지 않는다.
     */
    private fun close(into: Threads, from: Int, input: CharSequence, pos: Int, stack: IntArray): Int {
        if (into.has(from)) return 0
        into.add(from)
        var added = 1
        var top = 0
        stack[top++] = from
        while (top > 0) {
            val pc = stack[--top]
            var a = -1
            var b = -1
            when (op[pc]) {
                MATCH -> return -1
                JMP -> a = arg[pc]
                SPLIT -> {
                    a = arg[pc]
                    b = alt[pc]
                }
                ASSERT -> if (holds(arg[pc], input, pos)) a = pc + 1
            }
            if (b >= 0 && !into.has(b)) {
                into.add(b)
                stack[top++] = b
                added++
            }
            if (a >= 0 && !into.has(a)) {
                into.add(a)
                stack[top++] = a
                added++
            }
        }
        return added
    }

    override fun toString(): String = "/$source/"

    /** 한 자리의 실들 — 성긴 집합(비우기가 한 번의 대입이다). */
    private class Threads(capacity: Int) {
        val dense = IntArray(capacity)
        private val sparse = IntArray(capacity)
        var size = 0

        fun has(v: Int): Boolean {
            val k = sparse[v]
            return k < size && dense[k] == v
        }

        fun add(v: Int) {
            sparse[v] = size
            dense[size++] = v
        }
    }

    /** 스레드마다 하나 — 규칙 수십 개를 요청마다 맞추며 배열을 새로 잡지 않게. */
    private class Scratch {
        var cur = Threads(0)
        var next = Threads(0)
        var stack = IntArray(0)
    }

    companion object {
        /** 무늬 글의 상한 — 목록 넷의 가장 긴 정규식은 241자다. 넘으면 그 규칙을 건너뛴다. */
        const val MAX_SOURCE = 1024

        /** 명령 수의 상한 — `{n,m}` 은 그만큼 펼쳐진다. */
        const val MAX_PROGRAM = 4096

        /** 걸음 예산의 바탕 — 짧은 주소에서 명령이 많은 규칙도 넉넉히 맞춘다. */
        const val BASE_STEPS = 16_384L

        /**
         * 주소 한 글자에 주는 걸음. 목록 넷의 규칙은 보통 주소에서 한 글자에 몇 걸음이고, 일부러 지은 긴 주소에서 가장 많은 것이 65 걸음이다(uBO 의
         * pussyspace 예외 규칙 — 선택적인 숫자 묶음이 잇달아 실이 스무 개 남짓 산다, `RealListsBenchmark`). 그 두 배를 준다 — 쪽이 긴 주소로 목록의
         * 규칙을 끄지 못하게. 넘는 것은 동시에 살아 있는 실이 훨씬 많은 규칙(넓은 되풀이를 여럿 겹친 것)과 긴 주소가 만난 때다.
         */
        const val STEPS_PER_CHAR = 128L

        /** 주소 길이 [length] 의 걸음 예산. */
        fun budgetFor(length: Int): Long = BASE_STEPS + STEPS_PER_CHAR * length

        /** 묶음이 겹치는 깊이의 상한. */
        private const val MAX_DEPTH = 32

        /** `{n,m}` 의 수 상한(java.util.regex 는 훨씬 크게 받는다 — 그만큼 명령이 는다). */
        private const val MAX_COUNT = 1000

        private const val CHAR: Byte = 0
        private const val SPLIT: Byte = 1
        private const val JMP: Byte = 2
        private const val ASSERT: Byte = 3
        private const val MATCH: Byte = 4

        private const val BEGIN = 0
        private const val END = 1
        private const val WORD_BOUNDARY = 2
        private const val NOT_WORD_BOUNDARY = 3

        private const val INFINITE = -1

        private val SCRATCH: ThreadLocal<Scratch> = ThreadLocal.withInitial { Scratch() }

        /** [source] 를 읽는다 — 받지 않는 문법이거나 틀렸거나 너무 크면 null(그 규칙은 건너뛴다). */
        fun compile(source: String, ignoreCase: Boolean): ListRegex? = try {
            compileOrThrow(source, ignoreCase)
        } catch (e: Unsupported) {
            null
        }

        /** [compile] 과 같되 받지 않는 까닭을 던진다([Unsupported]). */
        internal fun compileOrThrow(source: String, ignoreCase: Boolean): ListRegex {
            if (source.length > MAX_SOURCE) throw Unsupported("무늬가 너무 길다(${source.length}자)")
            val node = Parser(source, ignoreCase).parse()
            val size = sizeOf(node) + 1
            if (size > MAX_PROGRAM) throw Unsupported("명령이 너무 많다")
            val emitter = Emitter(size.toInt())
            emitter.emit(node)
            emitter.op[emitter.pc] = MATCH
            val anchored = emitter.op[0] == ASSERT && emitter.arg[0] == BEGIN
            return ListRegex(source, ignoreCase, emitter.op, emitter.arg, emitter.alt, emitter.sets.toTypedArray(), anchored)
        }

        /** 규칙 글의 앞부분 — 디버그 기록에 규칙 전체를 싣지 않는다. */
        fun preview(text: String, max: Int = 48): String = if (text.length <= max) text else text.take(max) + "…"

        /** 명령 수(펼친 뒤) — 넘칠 만큼 크면 [MAX_PROGRAM] + 1 에서 멈춘다. */
        private fun sizeOf(node: Node): Long {
            val n: Long = when (node) {
                is Node.Chars, is Node.Assert -> 1L
                is Node.Seq -> node.items.sumOf { sizeOf(it) }
                is Node.Alt -> node.branches.sumOf { sizeOf(it) } + 2L * (node.branches.size - 1)
                is Node.Repeat -> {
                    val s = sizeOf(node.node)
                    node.min * s + if (node.max == INFINITE) s + 2 else (node.max - node.min) * (s + 1)
                }
            }
            return n.coerceAtMost(MAX_PROGRAM + 1L)
        }

        private fun holds(kind: Int, input: CharSequence, pos: Int): Boolean = when (kind) {
            BEGIN -> pos == 0
            END -> atEnd(input, pos)
            WORD_BOUNDARY -> isWordAt(input, pos - 1) != isWordAt(input, pos)
            else -> isWordAt(input, pos - 1) == isWordAt(input, pos)
        }

        /**
         * java.util.regex 의 `$`(여러 줄 깃발 없이) — 입력의 끝, 또는 끝에 붙은 줄 바꿈 하나(`\n`·`\r\n`·`\r`·U+0085·U+2028·U+2029) 바로 앞.
         * `\r\n` 의 가운데는 아니다.
         */
        private fun atEnd(input: CharSequence, pos: Int): Boolean {
            val n = input.length
            return when (pos) {
                n -> true
                n - 1 -> {
                    val c = input[pos].code
                    if (c == LF) !(pos > 0 && input[pos - 1].code == CR) else c == CR || c == NEL || c == LS || c == PS
                }
                n - 2 -> input[pos].code == CR && input[pos + 1].code == LF
                else -> false
            }
        }

        private fun isWordAt(input: CharSequence, k: Int): Boolean = k >= 0 && k < input.length && isWordChar(input[k].code)

        /** `\w`·`\b` 의 글자 — ASCII 글자·숫자·`_`(java.util.regex 의 기본, 자바스크립트도 같다). */
        private fun isWordChar(c: Int): Boolean = c in 'a'.code..'z'.code || c in 'A'.code..'Z'.code || c in '0'.code..'9'.code || c == '_'.code

        private const val LF = 10
        private const val CR = 13
        private const val NEL = 0x85
        private const val LS = 0x2028
        private const val PS = 0x2029
    }

    /** 읽은 무늬. */
    private sealed class Node {
        class Chars(val set: CharSet) : Node()
        class Assert(val kind: Int) : Node()
        class Seq(val items: List<Node>) : Node()
        class Alt(val branches: List<Node>) : Node()

        /** [max] 가 [INFINITE] 면 끝없이. */
        class Repeat(val node: Node, val min: Int, val max: Int) : Node()
    }

    /** 글자 하나의 모둠 — ASCII 는 128 비트 표, 그 밖은 범위들. [negated] 면 뒤집는다(대소문자는 지을 때 접었다). */
    private class CharSet(private val low: Long, private val high: Long, private val ranges: IntArray, private val negated: Boolean) {
        fun contains(c: Char): Boolean {
            val code = c.code
            val inside = when {
                code < 64 -> (low ushr code) and 1L != 0L
                code < 128 -> (high ushr (code - 64)) and 1L != 0L
                else -> inRanges(code)
            }
            return inside != negated
        }

        private fun inRanges(code: Int): Boolean {
            var k = 0
            while (k < ranges.size) {
                if (code < ranges[k]) return false
                if (code <= ranges[k + 1]) return true
                k += 2
            }
            return false
        }
    }

    /** 모둠을 짓는다 — 더하고(합집합), 대소문자를 접고, 뒤집기는 맞출 때. */
    private class SetBuilder {
        private var low = 0L
        private var high = 0L
        private val ranges = ArrayList<IntArray>()

        fun add(c: Int) = addRange(c, c)

        fun addRange(from: Int, to: Int) {
            var x = from
            while (x <= to && x < 128) {
                setBit(x)
                x++
            }
            if (to >= 128) ranges += intArrayOf(maxOf(from, 128), to)
        }

        private fun setBit(x: Int) {
            if (x < 64) low = low or (1L shl x) else high = high or (1L shl (x - 64))
        }

        private fun hasBit(x: Int): Boolean = if (x < 64) (low ushr x) and 1L != 0L else (high ushr (x - 64)) and 1L != 0L

        /** `\d \w \s`(ASCII — java.util.regex 의 기본)와 그 반대(`\D \W \S` — ASCII 밖은 모두 든다). */
        fun addClassEscape(e: Char) {
            when (e) {
                'd' -> addRange('0'.code, '9'.code)
                'w' -> {
                    addRange('a'.code, 'z'.code)
                    addRange('A'.code, 'Z'.code)
                    addRange('0'.code, '9'.code)
                    add('_'.code)
                }
                's' -> {
                    add(' '.code)
                    addRange(9, 13) // \t \n \x0B \f \r
                }
                else -> {
                    val positive = SetBuilder().apply { addClassEscape(e.lowercaseChar()) }
                    for (x in 0 until 128) if (!positive.hasBit(x)) setBit(x)
                    addRange(128, 0xFFFF)
                }
            }
        }

        /** ASCII 글자의 대소문자를 접는다(java.util.regex 의 대소문자 무시 — 접은 뒤에 뒤집는다: `[^a]` 는 `A` 에도 맞지 않는다). */
        fun fold() {
            for (x in 'A'.code..'Z'.code) {
                if (hasBit(x)) setBit(x + 32)
                if (hasBit(x + 32)) setBit(x)
            }
        }

        fun build(negated: Boolean): CharSet {
            val sorted = ranges.sortedBy { it[0] }
            val merged = ArrayList<Int>()
            for (r in sorted) {
                if (merged.isNotEmpty() && r[0] <= merged[merged.size - 1] + 1) {
                    merged[merged.size - 1] = maxOf(merged[merged.size - 1], r[1])
                } else {
                    merged += r[0]
                    merged += r[1]
                }
            }
            return CharSet(low, high, merged.toIntArray(), negated)
        }
    }

    /** 무늬를 읽는다(재귀 내림 — 깊이는 [MAX_DEPTH] 로 묶는다). 받지 않는 꼴이면 [Unsupported]. */
    private class Parser(private val s: String, private val fold: Boolean) {
        private var i = 0
        private var depth = 0

        fun parse(): Node {
            val node = alternation()
            if (i < s.length) unsupported("')' 의 짝이 없다")
            return node
        }

        private fun unsupported(reason: String): Nothing = throw Unsupported(reason)

        private fun alternation(): Node {
            val first = sequence()
            if (i >= s.length || s[i] != '|') return first
            val branches = arrayListOf(first)
            while (i < s.length && s[i] == '|') {
                i++
                branches += sequence()
            }
            return Node.Alt(branches)
        }

        private fun sequence(): Node {
            val items = ArrayList<Node>()
            while (i < s.length && s[i] != '|' && s[i] != ')') items += quantified(atom())
            return if (items.size == 1) items[0] else Node.Seq(items)
        }

        private fun atom(): Node = when (val c = s[i]) {
            '(' -> group()
            '[' -> charClass()
            '.' -> {
                i++
                Node.Chars(DOT)
            }
            '^' -> {
                i++
                Node.Assert(BEGIN)
            }
            '$' -> {
                i++
                Node.Assert(END)
            }
            '\\' -> escape()
            '*', '+', '?', '{' -> unsupported("앞에 받을 것이 없는 되풀이")
            else -> {
                i++
                Node.Chars(single(c.code))
            }
        }

        private fun quantified(atom: Node): Node {
            if (i >= s.length) return atom
            val min: Int
            val max: Int
            when (s[i]) {
                '*' -> {
                    min = 0
                    max = INFINITE
                    i++
                }
                '+' -> {
                    min = 1
                    max = INFINITE
                    i++
                }
                '?' -> {
                    min = 0
                    max = 1
                    i++
                }
                '{' -> {
                    i++
                    min = number() ?: unsupported("'{' 뒤에 수가 없다")
                    max = if (i < s.length && s[i] == ',') {
                        i++
                        if (i < s.length && s[i] == '}') INFINITE else number() ?: unsupported("',' 뒤에 수가 없다")
                    } else {
                        min
                    }
                    if (i >= s.length || s[i] != '}') unsupported("'}' 가 없다")
                    i++
                    if (max != INFINITE && max < min) unsupported("{n,m} 의 m 이 n 보다 작다")
                }
                else -> return atom
            }
            if (atom is Node.Assert) unsupported("단언을 되풀이한다")
            // 빈 글에 맞을 수 있는 묶음을 두 번 넘게 되풀이하면 엔진마다 뜻이 다르다 — java.util.regex 는 한 번이 빈 글로 끝나면 남은 횟수를
            // 채우지 않고 되풀이를 끝낸다(`(\b|.){2,}`). 목록에는 없는 꼴이라 받지 않는다(한 번까지는 뜻이 같다).
            if (min >= 2 && nullable(atom)) unsupported("빈 글에 맞을 수 있는 것을 두 번 넘게 되풀이한다")
            // 게으른 되풀이 — 맞는지 여부는 같다.
            if (i < s.length && s[i] == '?') i++
            // 소유 되풀이(`*+`)는 맞는 글이 달라지고, 겹친 되풀이는 java.util.regex 가 받지 않거나 뜻이 엉뚱하다.
            if (i < s.length && (s[i] == '+' || s[i] == '*' || s[i] == '?' || s[i] == '{')) unsupported("되풀이가 겹친다")
            return Node.Repeat(atom, min, max)
        }

        /** 글자를 읽지 않고 맞을 수 있는가(단언·빈 묶음·`?`·`*`). */
        private fun nullable(node: Node): Boolean = when (node) {
            is Node.Chars -> false
            is Node.Assert -> true
            is Node.Seq -> node.items.all { nullable(it) }
            is Node.Alt -> node.branches.any { nullable(it) }
            is Node.Repeat -> node.min == 0 || nullable(node.node)
        }

        private fun number(): Int? {
            val start = i
            while (i < s.length && s[i] in '0'..'9') i++
            if (i == start) return null
            val digits = s.substring(start, i).trimStart('0')
            if (digits.length > 4) unsupported("되풀이 수가 너무 크다")
            val n = if (digits.isEmpty()) 0 else digits.toInt()
            if (n > MAX_COUNT) unsupported("되풀이 수가 너무 크다")
            return n
        }

        private fun group(): Node {
            i++ // '('
            if (i < s.length && s[i] == '?') {
                if (i + 1 < s.length && s[i + 1] == ':') i += 2 else unsupported("'(?' 꼴(앞뒤 보기·이름·깃발)")
            }
            if (++depth > MAX_DEPTH) unsupported("묶음이 너무 깊다")
            val inner = alternation()
            depth--
            if (i >= s.length || s[i] != ')') unsupported("')' 가 없다")
            i++
            return inner
        }

        private fun escape(): Node {
            i++ // '\'
            if (i >= s.length) unsupported("끝의 역슬래시")
            return when (val e = s[i++]) {
                'b' -> Node.Assert(WORD_BOUNDARY)
                'B' -> Node.Assert(NOT_WORD_BOUNDARY)
                'd', 'D', 'w', 'W', 's', 'S' -> Node.Chars(SetBuilder().apply { addClassEscape(e) }.build(negated = false))
                else -> Node.Chars(single(escapedChar(e)))
            }
        }

        /** 글자 하나로 풀리는 역슬래시 꼴(모둠 안팎이 같다). */
        private fun escapedChar(e: Char): Int = when (e) {
            't' -> 9
            'n' -> 10
            'r' -> 13
            'f' -> 12
            'x' -> hex(2)
            'u' -> hex(4)
            // ASCII 기호 앞의 역슬래시는 그 글자다(java.util.regex·자바스크립트 같다). 글자·숫자 앞의 것은 저마다 뜻이 다르다.
            else -> if (e.code < 128 && !e.isLetterOrDigit()) e.code else unsupported("받지 않는 역슬래시 꼴 '$e'")
        }

        private fun hex(digits: Int): Int {
            if (i + digits > s.length) unsupported("16진수가 모자라다")
            var v = 0
            repeat(digits) {
                val d = Character.digit(s[i++], 16)
                if (d < 0) unsupported("16진수가 아니다")
                v = v * 16 + d
            }
            return v
        }

        private fun charClass(): Node {
            i++ // '['
            val b = SetBuilder()
            var negated = false
            if (i < s.length && s[i] == '^') {
                negated = true
                i++
            }
            // java.util.regex 는 첫 `]` 를 글자로, 자바스크립트는 빈 모둠으로 읽는다.
            if (i < s.length && s[i] == ']') unsupported("모둠의 처음이 ']'")
            while (true) {
                if (i >= s.length) unsupported("']' 가 없다")
                val c = s[i]
                if (c == ']') {
                    i++
                    break
                }
                if (c == '[') unsupported("겹친 모둠")
                if (c == '&' && i + 1 < s.length && s[i + 1] == '&') unsupported("모둠의 '&&'")
                val low = classAtom(b)
                // `-` 는 글자 하나 뒤에 오고 `]` 앞이 아닐 때만 범위다 — 모둠(`\d`) 뒤나 범위 뒤의 `-` 는 글자다(java.util.regex·자바스크립트 같다).
                if (low >= 0 && i + 1 < s.length && s[i] == '-' && s[i + 1] != ']') {
                    i++ // '-'
                    if (s[i] == '[') unsupported("겹친 모둠")
                    val high = classAtom(null)
                    if (high < low) unsupported("거꾸로 된 범위")
                    b.addRange(low, high)
                } else if (low >= 0) {
                    b.add(low)
                }
            }
            if (fold) b.fold()
            return Node.Chars(b.build(negated))
        }

        /** 모둠 안의 하나 — 글자면 그 번호, `\d` 따위면 [into] 에 더하고 -1([into] 가 null 이면 — 범위의 끝 — 받지 않는다). */
        private fun classAtom(into: SetBuilder?): Int {
            val c = s[i++]
            if (c != '\\') return c.code
            if (i >= s.length) unsupported("끝의 역슬래시")
            return when (val e = s[i++]) {
                'd', 'D', 'w', 'W', 's', 'S' -> {
                    (into ?: unsupported("범위의 끝이 모둠이다")).addClassEscape(e)
                    -1
                }
                'b' -> unsupported("모둠 안의 '\\b'")
                else -> escapedChar(e)
            }
        }

        private fun single(code: Int): CharSet {
            val b = SetBuilder()
            b.add(code)
            if (fold) b.fold()
            return b.build(negated = false)
        }

        private companion object {
            /** `.` — 줄 바꿈(`\n`·`\r`·U+0085·U+2028·U+2029) 밖의 모든 글자(java.util.regex, DOTALL 없이). */
            val DOT: CharSet = SetBuilder().apply {
                addRange(0, 9)
                addRange(11, 12)
                addRange(14, 0x84)
                addRange(0x86, 0x2027)
                addRange(0x202A, 0xFFFF)
            }.build(negated = false)
        }
    }

    /** 무늬를 명령으로 — SPLIT 은 두 갈래, JMP 는 건너뛰기, 되풀이는 펼친다(`{2,4}` → 둘 + 셋까지의 선택). */
    private class Emitter(size: Int) {
        val op = ByteArray(size)
        val arg = IntArray(size)
        val alt = IntArray(size)
        val sets = ArrayList<CharSet>()
        private val setIndex = java.util.IdentityHashMap<CharSet, Int>()
        var pc = 0

        fun emit(node: Node) {
            when (node) {
                is Node.Chars -> {
                    op[pc] = CHAR
                    arg[pc] = setIndex.getOrPut(node.set) {
                        sets += node.set
                        sets.size - 1
                    }
                    pc++
                }
                is Node.Assert -> {
                    op[pc] = ASSERT
                    arg[pc] = node.kind
                    pc++
                }
                is Node.Seq -> node.items.forEach { emit(it) }
                is Node.Alt -> {
                    val jumps = IntArray(node.branches.size - 1)
                    for (k in 0 until node.branches.size - 1) {
                        val split = pc++
                        op[split] = SPLIT
                        arg[split] = pc
                        emit(node.branches[k])
                        jumps[k] = pc
                        op[pc++] = JMP
                        alt[split] = pc
                    }
                    emit(node.branches.last())
                    for (j in jumps) arg[j] = pc
                }
                is Node.Repeat -> {
                    repeat(node.min) { emit(node.node) }
                    if (node.max == INFINITE) {
                        val split = pc++
                        op[split] = SPLIT
                        arg[split] = pc
                        emit(node.node)
                        op[pc] = JMP
                        arg[pc] = split
                        pc++
                        alt[split] = pc
                    } else {
                        repeat(node.max - node.min) {
                            val split = pc++
                            op[split] = SPLIT
                            arg[split] = pc
                            emit(node.node)
                            alt[split] = pc
                        }
                    }
                }
            }
        }
    }
}
