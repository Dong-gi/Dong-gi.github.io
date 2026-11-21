package io.github.donggi.iroiroviewer.playback

import java.util.concurrent.CancellationException

/**
 * **SAMI(`.smi`) 자막을 SRT 글로 바꾼다.**
 *
 * ## 왜 바꾸는가
 *
 * media3 의 자막 파서 열 갈래(cea·dvb·pgs·ssa·subrip·ttml·tx3g·vobsub·webvtt)에 SAMI 가 없다. 한국 영상에 흔한 형식이라
 * 바이트가 들어가는 길([SubtitleDataSourceFactory] — 이미 인코딩을 바꾸려고 통째로 읽는다)에서 **SRT 로 고쳐 써서** media3 의
 * `SubripParser` 에 넘긴다. 파서를 새로 붙이지 않으니 트랙·선택·싱크([SubtitleShift])가 SRT 의 길을 그대로 탄다.
 *
 * ## 읽는 법 — HTML 처럼, 너그럽게, 한 번 훑기
 *
 * SAMI 는 HTML 비슷한 글이고 실제 파일은 마크업이 자주 깨져 있다(닫지 않은 `<P>`·`</SYNC>` 없음·따옴표 없는 속성·대문자 태그).
 * 그래서 XML 파서를 쓰지 않고 손으로 한 번 훑는다 — **정규식을 쓰지 않는다**(되짚기가 입력 길이의 제곱으로 자랄 수 있다).
 * 모든 찾기는 앞으로만 가고, 주석 끝(`-->`)을 못 찾았다는 사실은 기억해 다시 찾지 않는다 — 입력 길이에 비례한다.
 *
 * - `<SYNC Start=ms>` 하나가 한 시점이다. 다음 시점까지 그 글이 떠 있다(SAMI 의 뜻 — 다음 SYNC 가 앞의 것을 바꾼다).
 *   `End=` 가 있으면 그것이 먼저 끝낸다. 마지막 시점은 [LAST_CUE_MS] 동안 뜬다
 * - 글이 비었으면(`&nbsp;` 뿐) **지움**이다 — 그 시점에 아무것도 띄우지 않는다
 * - 시점은 **시각 순으로 다시 늘어놓는다**(뒤집힌 차례). 같은 시각이 여럿이면 **파일에서 나중 것**이 이긴다. 그래서 겹침이 없다
 * - 줄바꿈은 `<br>` 뿐이다 — 원문의 줄바꿈·연이은 빈칸은 HTML 처럼 빈칸 하나다. 빈 줄은 내보내지 않는다(SRT 에서 빈 줄은 큐의 끝이다)
 * - 엔티티는 흔한 것(`nbsp amp lt gt quot apos`, 숫자 꼴)만 푼다. 모르는 것은 글자 그대로 둔다. 옛 파일의 `&nbsp`(쌍반점 없음)도 받는다
 * - 주석 `<!-- -->` 은 건너뛴다. 끝이 없으면 다음 `>` 까지만 주석으로 본다(나머지 파일을 통째로 잃지 않게)
 * - `<STYLE>`·`<TITLE>`·`<SCRIPT>` 안의 글은 자막이 아니다. `</BODY>`·`</SAMI>` 뒤는 읽지 않는다
 * - `<` 뒤가 글자·`/`·`!` 가 아니면 태그가 아니라 글이다(`a < b`). 태그는 `>` 에서, `>` 가 빠졌으면 다음 `<` 앞에서 끝난다
 * - `<SYNC` 가 하나도 없으면 **SAMI 가 아니다** — 바꾸지 않고 그대로 돌려준다(이름만 `.smi` 인 SRT 가 있다)
 *
 * ## 언어 클래스 — 하나를 고른다
 *
 * 한 파일에 `<P Class=KRCC>`·`<P Class=ENCC>` 처럼 여러 언어가 들 수 있다. 우리는 **하나**를 고른다:
 * 이름이 `KR`·`KO` 로 시작하거나 `<STYLE>` 이 그 클래스에 `lang:ko…`·`Name:Korean` 을 적은 **한국어 클래스**가 먼저, 없으면
 * **글이 있는 첫 클래스**. 클래스 없는 글(`<P>` 없이 SYNC 바로 뒤, 또는 `Class` 없는 `<P>`)은 모든 클래스의 것으로 본다.
 * **고른 클래스의 `<P>` 가 없는 시점은 그 클래스를 건드리지 않는다** — 영어만 바뀌는 시점이 한국어 자막을 지우지 않게.
 * 다만 `<P>` 가 하나도 없는 시점(`<SYNC Start=…>&nbsp;`)은 모두를 지운다.
 * 클래스마다 트랙을 만들지 않은 것은 같은 파일을 두 번 읽어 트랙 둘로 세우는 길이 media3 의 '자막 파일 하나 = 트랙 하나' 와 어긋나서다.
 *
 * ## 서식은 모두 걷는다
 *
 * `<font color>`·`<b>`·`<i>`·`<ruby>` 따위는 걷고 글자만 남긴다. 이 앱의 자막 층(`feature:player` 의 `SubtitleLayer`)은 큐의 글을
 * 문자열로만 그려 색·굵기를 어차피 쓰지 않고, SRT 쪽 파서가 글을 `Html.fromHtml` 로 읽으므로 남의 파일의 마크업을 그 길에 흘리지
 * 않는 편이 좁다. 그래서 내보내는 글의 `& < >` 는 다시 엔티티로 적는다 — 그래야 `fromHtml` 이 원래 글자로 돌려 놓는다.
 *
 * ## 상한
 *
 * 입력은 부르는 쪽이 4 MiB 로 자른다([SubtitleDataSourceFactory.MAX_BYTES]). 여기서는 시점 [MAX_SYNCS] 개(넘으면 거기서 읽기를
 * 멈춘다), 한 줄 [MAX_LINE_CHARS] 자, 한 큐 [MAX_CUE_LINES] 줄, 한 시점의 조각 [MAX_SEGMENTS] 개, 내보내는 글 [MAX_OUTPUT_CHARS] 자
 * (넘으면 그 큐까지 쓰고 멈춘다). 시각은 아홉 자리까지(넘으면 그 SYNC 를 버린다).
 *
 * android.* 를 쓰지 않는다 — 전부 JVM 시험이 답한다.
 */
object SamiToSrt {

    /** 시점 수 상한. 두 시간 영화가 두세 언어로 1만 개 안쪽이다. */
    const val MAX_SYNCS = 100_000

    /** 한 줄의 글자 수 상한. 넘는 몫은 버린다. */
    const val MAX_LINE_CHARS = 1_000

    /** 한 큐의 줄 수 상한. */
    const val MAX_CUE_LINES = 16

    /** 뒤따르는 시점이 없는 마지막 글이 떠 있는 시간. */
    const val LAST_CUE_MS = 5_000L

    /** 한 시점이 품는 `<P>` 조각 수 상한 — 큐 줄 상한의 네 배. 넘는 조각의 글은 버린다. */
    const val MAX_SEGMENTS = MAX_CUE_LINES * 4

    /** 내보내는 글의 상한(UTF-16 글자). 넘으면 그 큐까지 쓰고 멈춘다 — 엔티티로 다시 적으면 글이 다섯 배까지 부푼다. */
    const val MAX_OUTPUT_CHARS = 8 * 1024 * 1024

    /** 시각 글자의 자리 수 상한 — 아홉 자리(약 277시간). 넘으면 그 SYNC 를 버린다. */
    private const val MAX_TIME_DIGITS = 9

    /** 엔티티 이름의 길이 상한(`#x10FFFF` 가 여덟 자). */
    private const val MAX_ENTITY_CHARS = 10

    /** 이만큼 걸음마다 취소를 본다. */
    private const val CANCEL_EVERY = 0x3FFF

    /** SAMI 로 보이는가 — `<SYNC` 가 (대소문자 없이) 한 번이라도 있다. */
    fun looksLikeSami(text: String): Boolean = indexOfIgnoreCase(text, "<sync", 0) >= 0

    /**
     * [text](이미 UTF-16 문자열 — 인코딩 판정은 부르는 쪽이 끝냈다)를 SRT 로. SAMI 가 아니면 그대로 돌려준다.
     *
     * @param isCancelled 참이 되면 [CancellationException] 을 던진다(재생기가 읽기를 취소했다).
     */
    fun convert(text: String, isCancelled: () -> Boolean = { false }): String {
        if (!looksLikeSami(text)) return text
        val scan = Scanner(text, isCancelled).also { it.run() }
        val head = if (scan.firstSync >= 0) scan.firstSync else text.length
        val chosen = chooseClass(scan.classOrder, koreanClassesInStyle(text, head))
        return render(scan.blocks, chosen, isCancelled)
    }

    // ---- 언어 클래스 ----------------------------------------------------------------------

    /** 한국어 클래스가 먼저, 없으면 글이 있는 첫 클래스, 그것도 없으면 클래스 없는 글(빈 이름). */
    internal fun chooseClass(order: Collection<String>, koreanInStyle: Set<String>): String =
        order.firstOrNull { it.startsWith("KR") || it.startsWith("KO") || it in koreanInStyle }
            ?: order.firstOrNull()
            ?: ""

    /**
     * 첫 SYNC 앞(머리)의 CSS 에서 `.이름 { … lang:ko… }`·`Name:Korean` 인 클래스 이름(대문자). 주석으로 싸인 CSS 도 읽는다 —
     * 머리를 글자 그대로 훑는다. 앞으로만 간다.
     */
    internal fun koreanClassesInStyle(text: String, end: Int): Set<String> {
        val out = HashSet<String>()
        var i = 0
        while (i < end) {
            if (text[i] != '.') { i++; continue }
            var j = i + 1
            while (j < end && isNameChar(text[j])) j++
            if (j == i + 1) { i = j; continue }
            val name = text.substring(i + 1, j).uppercase()
            var k = j
            while (k < end && text[k].isWhitespace()) k++
            if (k >= end || text[k] != '{') { i = j; continue }
            var close = k + 1
            while (close < end && text[close] != '}') close++
            val body = buildString {
                for (p in k + 1 until close) if (!text[p].isWhitespace()) append(text[p].lowercaseChar())
            }
            if ("lang:ko" in body || "name:korean" in body) out += name
            i = close + 1
        }
        return out
    }

    private fun isNameChar(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '_' || c == '-'

    // ---- 내보내기 --------------------------------------------------------------------------

    private class Event(val start: Long, val end: Long?, val lines: List<String>)

    private fun render(blocks: List<Block>, chosen: String, isCancelled: () -> Boolean): String {
        val events = ArrayList<Event>(blocks.size)
        for ((n, b) in blocks.withIndex()) {
            if ((n and CANCEL_EVERY) == 0 && isCancelled()) throw CancellationException()
            var namedP = false
            var touches = false
            val parts = ArrayList<String>(2)
            for (seg in b.segments) {
                if (seg.cls.isEmpty()) {
                    // 클래스 없는 조각은 글이 있을 때만 생긴다(Scanner) — 모든 클래스의 것이다.
                    touches = true
                    parts += seg.text.toString()
                } else {
                    namedP = true
                    if (seg.cls == chosen) {
                        touches = true
                        parts += seg.text.toString()
                    }
                }
            }
            // `<P>` 가 하나도 없는 시점은 모두의 것이다(`<SYNC Start=…>&nbsp;` — 지움).
            if (!namedP) touches = true
            if (touches) events += Event(b.start, b.end, linesOf(parts))
        }
        // 시각 순으로. 같은 시각은 파일에서 나중 것이 이긴다 — 정렬은 안정적이라 같은 시각끼리는 파일 차례가 남는다.
        val sorted = events.sortedBy { it.start }
        val out = StringBuilder(sorted.size * 48)
        var index = 1
        var i = 0
        while (i < sorted.size) {
            if ((i and CANCEL_EVERY) == 0 && isCancelled()) throw CancellationException()
            val e = sorted[i]
            if (i + 1 < sorted.size && sorted[i + 1].start == e.start) { i++; continue }
            val next = if (i + 1 < sorted.size) sorted[i + 1].start else null
            i++
            if (e.lines.isEmpty()) continue
            val explicit = e.end?.takeIf { it > e.start && (next == null || it < next) }
            val end = explicit ?: next ?: (e.start + LAST_CUE_MS)
            out.append(index++).append('\n')
            appendTime(out, e.start)
            out.append(" --> ")
            appendTime(out, end)
            out.append('\n')
            for (line in e.lines) appendEscaped(out, line).append('\n')
            out.append('\n')
            if (out.length >= MAX_OUTPUT_CHARS) break
        }
        return out.toString()
    }

    /** 조각들의 글 → 줄들. 줄마다 앞뒤 빈칸을 걷고 빈 줄을 버린다. 긴 줄은 자르고 줄 수를 누른다. */
    private fun linesOf(parts: List<String>): List<String> {
        val lines = ArrayList<String>(2)
        for (part in parts) {
            for (raw in part.split('\n')) {
                if (lines.size >= MAX_CUE_LINES) return lines
                val t = raw.trim()
                if (t.isEmpty()) continue
                lines += clip(t)
            }
        }
        return lines
    }

    /** [MAX_LINE_CHARS] 로 자르되 대리 쌍을 가르지 않고, 홀로 선 대리 글자는 버린다. */
    private fun clip(line: String): String {
        val sb = StringBuilder(minOf(line.length, MAX_LINE_CHARS))
        var i = 0
        while (i < line.length && sb.length < MAX_LINE_CHARS) {
            val c = line[i]
            if (Character.isHighSurrogate(c) && i + 1 < line.length && Character.isLowSurrogate(line[i + 1])) {
                if (sb.length + 2 > MAX_LINE_CHARS) break
                sb.append(c).append(line[i + 1])
                i += 2
                continue
            }
            if (!Character.isSurrogate(c)) sb.append(c)
            i++
        }
        return sb.toString()
    }

    /**
     * SRT 쪽 파서가 글을 `Html.fromHtml` 로 읽는다 — 원래 글자로 돌아오게 다시 적는다.
     *
     * 그 파서는 `fromHtml` 앞에서 `{` + 역슬래시로 시작해 `}` 로 끝나는 조각을 지우고, `{` + 역슬래시 + `an1`~`an9` 이면 자막의 자리까지
     * 옮긴다(1.11.1 바이트코드). 글에 그 꼴이 있으면 글이 사라지거나 자막이 화면 위로 뛴다 — 둘 사이에 너비 없는 빈칸([BREAK])을 끼워
     * 끊는다. 엔티티로 적으면 그 검사가 `fromHtml` 앞이라 듣지만, 숫자 엔티티를 플랫폼 판마다 똑같이 푸는지 확인하지 않아 글자로 둔다.
     */
    private fun appendEscaped(out: StringBuilder, line: String): StringBuilder {
        var prev = ' '
        for (c in line) {
            when (c) {
                '&' -> out.append("&amp;")
                '<' -> out.append("&lt;")
                '>' -> out.append("&gt;")
                '\\' -> {
                    if (prev == '{') out.append(BREAK)
                    out.append(c)
                }
                else -> out.append(c)
            }
            prev = c
        }
        return out
    }

    /** 너비 없는 빈칸(U+200B) — `{` 와 역슬래시 사이를 끊는다([appendEscaped]). */
    private const val BREAK = '\u200B'

    /** `HH:MM:SS,mmm`. 손으로 적는다 — 기기 로캘의 숫자(아랍-인도 숫자)를 타지 않고, 큐 10만 개에 `format` 을 부르지 않는다. */
    private fun appendTime(out: StringBuilder, ms: Long) {
        pad(out, ms / 3_600_000, 2).append(':')
        pad(out, ms / 60_000 % 60, 2).append(':')
        pad(out, ms / 1000 % 60, 2).append(',')
        pad(out, ms % 1000, 3)
    }

    private fun pad(out: StringBuilder, v: Long, width: Int): StringBuilder {
        val s = v.toString()
        for (k in s.length until width) out.append('0')
        return out.append(s)
    }

    // ---- 훑기 ----------------------------------------------------------------------------

    /** 한 시점. [segments] 는 그 안의 `<P>` 조각들(클래스 없는 글은 빈 이름). */
    private class Block(val start: Long, val end: Long?) {
        val segments = ArrayList<Segment>(2)
    }

    /** 한 조각. [text] 는 빈칸이 하나로 줄었고 줄바꿈은 `<br>` 에서 온 `\n` 뿐이다. */
    private class Segment(val cls: String) {
        val text = StringBuilder()
        var hasText = false
    }

    private class Scanner(private val s: String, private val isCancelled: () -> Boolean) {
        val blocks = ArrayList<Block>()

        /** 글이 든 이름 있는 클래스들, 처음 나온 차례. */
        val classOrder = LinkedHashSet<String>()

        /** 첫 `<SYNC` 의 자리 — 그 앞이 머리(CSS)다. */
        var firstSync = -1

        private var block: Block? = null
        private var segment: Segment? = null
        private var currentClass = ""
        private var skipText = false

        /** `-->` 를 한 번 못 찾았으면 그 뒤에도 없다 — 다시 찾지 않는다(되풀이하면 제곱이 된다). */
        private var noCommentEnd = false

        /** 조각 하나의 글 상한 — 줄 상한 × 줄 수에 줄바꿈 몫. 넘는 글은 어차피 잘린다. */
        private val segmentCap = (MAX_LINE_CHARS + 1) * MAX_CUE_LINES

        fun run() {
            val n = s.length
            var i = 0
            var steps = 0
            while (i < n) {
                if ((++steps and CANCEL_EVERY) == 0 && isCancelled()) throw CancellationException()
                val c = s[i]
                if (c == '<' && i + 1 < n) {
                    val d = s[i + 1]
                    if (d == '!') { i = skipBang(i); continue }
                    if (d == '/' || d in 'a'..'z' || d in 'A'..'Z') {
                        i = tag(i)
                        if (i < 0) return
                        continue
                    }
                }
                if (c == '&') { i = entity(i); continue }
                put(c)
                i++
            }
        }

        /** 태그의 끝 — `>` 의 자리, 또는 `>` 없이 다음 `<`·끝에 닿은 자리. */
        private fun tagEnd(from: Int): Int {
            var j = from + 1
            while (j < s.length && s[j] != '>' && s[j] != '<') j++
            return j
        }

        /** 태그 다음에 읽을 자리. `>` 에서 끝났으면 그 뒤, 아니면 `<`·끝 그 자리. */
        private fun after(end: Int): Int = if (end < s.length && s[end] == '>') end + 1 else end

        private fun skipBang(i: Int): Int {
            if (s.startsWith("<!--", i)) {
                if (!noCommentEnd) {
                    val close = s.indexOf("-->", i + 4)
                    if (close >= 0) return close + 3
                    noCommentEnd = true
                }
            }
            return after(tagEnd(i))
        }

        /** 태그 하나를 처리하고 다음 자리를 준다. 읽기를 멈춰야 하면 -1. */
        private fun tag(i: Int): Int {
            val end = tagEnd(i)
            var k = i + 1
            val closing = s[k] == '/'
            if (closing) k++
            val nameStart = k
            while (k < end && (s[k] in 'a'..'z' || s[k] in 'A'..'Z' || s[k] in '0'..'9')) k++
            val name = s.substring(nameStart, k).lowercase()
            when (name) {
                "sync" -> {
                    closeSegment()
                    if (closing) {
                        block = null
                    } else {
                        if (blocks.size >= MAX_SYNCS) return -1
                        if (firstSync < 0) firstSync = i
                        // 닫지 않은 `<TITLE>` 이 본문 글을 통째로 삼키지 않게 — SYNC 는 본문이다.
                        skipText = false
                        val start = timeOf(attr(k, end, "start"))
                        block = start?.let { Block(it, timeOf(attr(k, end, "end"))) }?.also { blocks += it }
                    }
                    currentClass = ""
                }
                "p" -> {
                    closeSegment()
                    currentClass = if (closing) "" else attr(k, end, "class")?.trim()?.uppercase().orEmpty()
                    // `<P>` 는 글이 없어도 조각이다 — 비어 있으면 그 클래스의 지움이다.
                    if (!closing && currentClass.isNotEmpty()) openSegment()
                }
                "br" -> newline()
                "style", "title", "script" -> skipText = !closing
                "body", "sami" -> if (closing) return -1 else skipText = false
            }
            return after(end)
        }

        /**
         * 그 블록에 [currentClass] 의 조각을 연다. 블록 하나의 조각은 [MAX_SEGMENTS] 개까지 — 넘으면 열지 않고 그 글을 버린다.
         * `<P>` 만 수십만 번 되풀이한 한 시점이 조각(과 그 버퍼)을 그만큼 만들지 않게. 어차피 큐는 [MAX_CUE_LINES] 줄에서 잘린다.
         */
        private fun openSegment() {
            val b = block ?: return
            segment = if (b.segments.size >= MAX_SEGMENTS) null else Segment(currentClass).also { b.segments += it }
        }

        private fun closeSegment() {
            segment = null
        }

        private fun put(c: Char) {
            if (block == null || skipText) return
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\u000C' || c == '\u00A0') {
                val seg = segment ?: return
                val t = seg.text
                if (t.isNotEmpty() && t[t.length - 1] != ' ' && t[t.length - 1] != '\n' && t.length < segmentCap) t.append(' ')
                return
            }
            if (c < ' ' || c == '\u007F' || c in '\u0080'..'\u009F') return
            val seg = segment ?: run {
                // 클래스 없는 글 — 처음 보이는 글자에서 조각을 연다(빈칸뿐인 자리는 조각이 아니다).
                openSegment()
                segment ?: return
            }
            if (seg.text.length >= segmentCap) return
            seg.text.append(c)
            if (!seg.hasText) {
                seg.hasText = true
                if (seg.cls.isNotEmpty()) classOrder += seg.cls
            }
        }

        private fun putCodePoint(cp: Int) {
            if (cp <= 0xFFFF) {
                put(cp.toChar())
            } else {
                val chars = Character.toChars(cp)
                put(chars[0])
                put(chars[1])
            }
        }

        private fun newline() {
            if (block == null || skipText) return
            val seg = segment ?: return
            val t = seg.text
            if (t.length >= segmentCap) return
            if (t.isNotEmpty() && t[t.length - 1] == ' ') t.setLength(t.length - 1)
            t.append('\n')
        }

        /** `&` 에서 엔티티 하나. 풀 수 없으면 `&` 를 글자로 두고 그 뒤부터 읽는다. */
        private fun entity(i: Int): Int {
            var j = i + 1
            while (j < s.length && j - i - 1 < MAX_ENTITY_CHARS &&
                (s[j] in 'a'..'z' || s[j] in 'A'..'Z' || s[j] in '0'..'9' || s[j] == '#')
            ) j++
            val run = s.substring(i + 1, j)
            val semi = j < s.length && s[j] == ';'
            if (run.startsWith("#") && semi) {
                val cp = if (run.length > 1 && (run[1] == 'x' || run[1] == 'X')) {
                    run.substring(2).toIntOrNull(16)
                } else {
                    run.substring(1).toIntOrNull()
                }
                if (cp != null && cp in 1..0x10FFFF && cp !in 0xD800..0xDFFF) {
                    putCodePoint(cp)
                    return j + 1
                }
            } else {
                val lower = run.lowercase()
                if (semi) NAMED[lower]?.let { put(it); return j + 1 }
                // 쌍반점이 빠진 옛 꼴(`&nbsp`)은 흔한 넷만 앞머리로 받는다.
                for (legacy in LEGACY) {
                    if (lower.startsWith(legacy)) {
                        put(NAMED.getValue(legacy))
                        return i + 1 + legacy.length
                    }
                }
            }
            put('&')
            return i + 1
        }

        /** 태그 [from, to) 의 속성 [key] 값. 따옴표 없음·홑·겹따옴표, 대소문자 없이. 없으면 null. */
        private fun attr(from: Int, to: Int, key: String): String? {
            var p = from
            while (p < to) {
                while (p < to && (s[p].isWhitespace() || s[p] == '/')) p++
                val ns = p
                while (p < to && !s[p].isWhitespace() && s[p] != '=' && s[p] != '/') p++
                val nameEnd = p
                while (p < to && s[p].isWhitespace()) p++
                var value: String? = null
                if (p < to && s[p] == '=') {
                    p++
                    while (p < to && s[p].isWhitespace()) p++
                    if (p < to && (s[p] == '"' || s[p] == '\'')) {
                        val q = s[p]
                        val vs = ++p
                        while (p < to && s[p] != q) p++
                        value = s.substring(vs, p)
                        if (p < to) p++
                    } else {
                        val vs = p
                        while (p < to && !s[p].isWhitespace()) p++
                        value = s.substring(vs, p)
                    }
                }
                if (nameEnd > ns && s.regionMatches(ns, key, 0, key.length, ignoreCase = true) && nameEnd - ns == key.length) {
                    return value ?: ""
                }
                if (p == ns) p++ // 앞으로 가지 못하는 글자(빈 이름 뒤의 `=` 따위)는 하나 건너뛴다.
            }
            return null
        }

        /** `1000`·`"1000"`·`1000ms` → 1000. 앞의 숫자만 본다. 숫자가 없거나 [MAX_TIME_DIGITS] 를 넘으면 null. */
        private fun timeOf(value: String?): Long? {
            val v = value?.trim() ?: return null
            var p = if (v.startsWith("+")) 1 else 0
            val ds = p
            while (p < v.length && v[p] in '0'..'9') p++
            val digits = p - ds
            if (digits == 0 || digits > MAX_TIME_DIGITS) return null
            return v.substring(ds, p).toLong()
        }
    }

    private val NAMED = mapOf(
        "nbsp" to ' ',
        "amp" to '&',
        "lt" to '<',
        "gt" to '>',
        "quot" to '"',
        "apos" to '\'',
    )

    private val LEGACY = listOf("nbsp", "amp", "quot", "lt", "gt")

    /** 대소문자 없이 [needle](소문자)을 찾는다. 앞으로만 간다. */
    private fun indexOfIgnoreCase(text: String, needle: String, from: Int): Int {
        val last = text.length - needle.length
        var i = from
        while (i <= last) {
            if (text.regionMatches(i, needle, 0, needle.length, ignoreCase = true)) return i
            i++
        }
        return -1
    }
}
