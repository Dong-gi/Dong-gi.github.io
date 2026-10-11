package io.github.donggi.iroiroviewer.format.html

/**
 * HTML 속성 값의 **문자 참조**(`&amp;`·`&#40;`·`&lpar;`)를 푼다 — 속성을 브라우저가 읽는 대로 보려고.
 *
 * ## 왜 푸는가
 *
 * 브라우저는 속성 값의 참조를 **먼저 풀고** 그 글을 CSS·주소로 읽는다. 푼 뒤에만 보이는 것이 있다 —
 * `style="background:url&lpar;https://…&rpar;"` 는 브라우저에게 `url(https://…)` 이고, `href="&#35;x"` 는 `#x` 다.
 * 참조를 풀지 않고 글자로 찾던 위생기는 앞의 것을 주소로 보지 못했다(가로채기의 빈 404 와 `INTERNET` 미선언이 막았다 —
 * 겹친 방어의 마지막 두 겹이 일했다는 뜻이다).
 *
 * ## 모르는 이름은 '모른다' 고 말한다
 *
 * HTML 의 이름 참조는 2,000개가 넘는다. 표를 통째로 들지 않는다 — 여기 있는 것은 CSS·주소의 문법에 쓰이는 ASCII 기호 전부와
 * 글에 흔한 것뿐이다. **표에 없는 이름이 남으면 [Decoded.unknown]** 이고, 그 글을 CSS 로 믿을 수 없다(브라우저가 무엇으로 풀지
 * 우리가 모른다). 부르는 쪽이 그 속성을 버린다.
 *
 * 숫자 참조는 언제나 푼다(`;` 가 없어도 — 명세). `;` 없는 이름은 명세의 옛 이름 가운데 CSS 에 뜻이 있는 넷(`amp`·`lt`·`gt`·
 * `quot`, 대문자 포함)만, 뒤에 영문·숫자·`=` 가 오지 않을 때만 푼다(속성 안의 `&copy=2` 는 질의다 — 명세가 그렇게 둔다).
 *
 * ## 글([decodeText])은 규칙이 하나 다르다
 *
 * 그 '뒤에 영문·숫자·`=` 가 오면 풀지 않는다' 는 **속성 값에서만**이다. 글(요소의 내용)에서는 옛 이름이 앞머리만 맞아도 푼다 —
 * `&quotx` 는 `"x`, `&ltimes` 는 `<imes` 다(명세의 '이름 참조' 상태는 표에서 가장 길게 맞는 것을 먹는다). `<svg>`·`<math>` 안의
 * `<style>` 은 글로 읽혀 참조가 풀린다(`HtmlSanitizer` 의 `<style>`) — 속성의 규칙으로 풀면 브라우저가 `"` 로 읽는 자리를 우리는
 * 글자 그대로 보아, 우리가 문자열 안이라고 본 `url(…)` 이 브라우저에게는 문자열 밖이 된다.
 */
object CharRefs {

    /** 푼 글. [unknown] 은 표에 없는 이름 참조(`&이름;`)가 남았다는 뜻이다. */
    class Decoded(val text: String, val unknown: Boolean)

    /** 속성 값의 규칙으로 푼다. */
    fun decode(s: String): Decoded = decode(s, attribute = true)

    /** 글(요소의 내용)의 규칙으로 푼다 — 위 '글은 규칙이 하나 다르다'. */
    fun decodeText(s: String): Decoded = decode(s, attribute = false)

    private fun decode(s: String, attribute: Boolean): Decoded {
        if ('&' !in s) return Decoded(s, false)
        val sb = StringBuilder(s.length)
        var unknown = false
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c != '&') {
                sb.append(c)
                i++
                continue
            }
            val numeric = numericAt(s, i, sb)
            if (numeric > i) {
                i = numeric
                continue
            }
            val named = namedAt(s, i, sb, attribute)
            if (named > i) {
                i = named
                continue
            }
            if (looksNamed(s, i)) unknown = true
            sb.append(c)
            i++
        }
        return Decoded(sb.toString(), unknown)
    }

    /** `&#NNN;`·`&#xHHH;`(`;` 는 없어도 된다). 0·서로게이트·상한 밖은 U+FFFD(명세). 아니면 [at] 을 그대로 준다. */
    private fun numericAt(s: String, at: Int, sb: StringBuilder): Int {
        var j = at + 1
        if (j >= s.length || s[j] != '#') return at
        j++
        val hex = j < s.length && (s[j] == 'x' || s[j] == 'X')
        if (hex) j++
        val start = j
        var cp = 0
        while (j < s.length) {
            val d = digit(s[j], hex)
            if (d < 0) break
            // 넘치기 전에 멈춘다 — 자릿수가 아무리 길어도 상한 밖이라는 사실만 남긴다.
            cp = if (cp > 0x10FFFF) cp else cp * (if (hex) 16 else 10) + d
            j++
        }
        if (j == start) return at
        if (j < s.length && s[j] == ';') j++
        sb.appendCodePoint(if (cp == 0 || cp > 0x10FFFF || cp in 0xD800..0xDFFF) 0xFFFD else cp)
        return j
    }

    private fun digit(c: Char, hex: Boolean): Int = when {
        c in '0'..'9' -> c - '0'
        hex && c in 'a'..'f' -> c - 'a' + 10
        hex && c in 'A'..'F' -> c - 'A' + 10
        else -> -1
    }

    /**
     * 표에 있는 이름 참조. `;` 까지 맞아야 하고, 옛 넷은 `;` 없이도 — 속성([attribute])이면 뒤가 영문·숫자·`=` 가 아닐 때만, 글이면
     * 이름의 앞머리만 맞아도(`&quotx` → `"x`).
     */
    private fun namedAt(s: String, at: Int, sb: StringBuilder, attribute: Boolean): Int {
        var j = at + 1
        while (j < s.length && j - at <= MAX_NAME && (s[j].isAsciiLetter() || s[j] in '0'..'9')) j++
        if (j == at + 1) return at
        val name = s.substring(at + 1, j)
        if (j < s.length && s[j] == ';') {
            NAMED[name]?.let { value ->
                sb.append(value)
                return j + 1
            }
            if (attribute) return at
        }
        if (!attribute) {
            // 가장 긴 것부터 — `quot` 가 `qu…` 보다 먼저다(넷은 서로의 앞머리가 아니라 차례는 뜻이 없지만 명세의 '가장 길게' 를 옮겼다).
            for ((legacyName, value) in LEGACY_LONGEST_FIRST) {
                if (name.startsWith(legacyName)) {
                    sb.append(value)
                    return at + 1 + legacyName.length
                }
            }
            return at
        }
        val legacy = LEGACY[name] ?: return at
        // 이름 뒤가 영문·숫자면 위에서 이미 먹었다 — 남은 것은 `=` 하나다.
        if (j < s.length && s[j] == '=') return at
        sb.append(legacy)
        return j
    }

    /** `&이름;` 모양인가 — 표에 없는 이름 참조가 남았는지 본다. */
    private fun looksNamed(s: String, at: Int): Boolean {
        var j = at + 1
        while (j < s.length && j - at <= MAX_NAME && (s[j].isAsciiLetter() || s[j] in '0'..'9')) j++
        return j > at + 1 && s[at + 1].isAsciiLetter() && j < s.length && s[j] == ';'
    }

    private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

    /** 명세에서 가장 긴 이름이 서른 자 남짓이다. 그보다 긴 것은 이름이 아니다. */
    private const val MAX_NAME = 40

    /** `;` 없이도 푸는 옛 이름 가운데 CSS 에 뜻이 있는 것. */
    private val LEGACY = mapOf(
        "amp" to "&", "AMP" to "&", "lt" to "<", "LT" to "<", "gt" to ">", "GT" to ">", "quot" to "\"", "QUOT" to "\"",
    )

    private val LEGACY_LONGEST_FIRST: List<Pair<String, String>> = LEGACY.entries.sortedByDescending { it.key.length }.map { it.key to it.value }

    /**
     * 이름 참조(`;` 까지). CSS·주소의 문법에 쓰이는 ASCII 기호는 **전부** 있다 — 표에 없는 이름은 '모른다' 가 되어 부르는 쪽이
     * 속성을 통째로 버리므로, 기호를 참조로 적은 멀쩡한 스타일까지 사라진다. 나머지는 글에 흔한 것이다.
     */
    private val NAMED: Map<String, String> = mapOf(
        // ASCII 기호(명세의 이름 그대로 — 같은 글자에 이름이 여럿인 것도 있다).
        "excl" to "!", "quot" to "\"", "QUOT" to "\"", "num" to "#", "dollar" to "$", "percnt" to "%",
        "amp" to "&", "AMP" to "&", "apos" to "'", "lpar" to "(", "rpar" to ")", "ast" to "*", "midast" to "*",
        "plus" to "+", "comma" to ",", "period" to ".", "sol" to "/", "colon" to ":", "semi" to ";",
        "lt" to "<", "LT" to "<", "equals" to "=", "gt" to ">", "GT" to ">", "quest" to "?", "commat" to "@",
        "lsqb" to "[", "lbrack" to "[", "bsol" to "\\", "rsqb" to "]", "rbrack" to "]", "Hat" to "^",
        "lowbar" to "_", "UnderBar" to "_", "grave" to "`", "DiacriticalGrave" to "`", "lcub" to "{", "lbrace" to "{",
        "verbar" to "|", "vert" to "|", "VerticalLine" to "|", "rcub" to "}", "rbrace" to "}",
        "Tab" to "\t", "NewLine" to "\n",
        // 글에 흔한 것.
        "nbsp" to "\u00A0", "NonBreakingSpace" to "\u00A0", "ensp" to "\u2002", "emsp" to "\u2003", "thinsp" to "\u2009",
        "zwj" to "\u200D", "zwnj" to "\u200C", "shy" to "\u00AD",
        "copy" to "©", "COPY" to "©", "reg" to "®", "REG" to "®", "trade" to "™",
        "hellip" to "…", "mdash" to "—", "ndash" to "–", "hyphen" to "‐", "dash" to "‐",
        "minus" to "−", "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“", "rdquo" to "”",
        "sbquo" to "‚", "bdquo" to "„", "laquo" to "«", "raquo" to "»", "lsaquo" to "‹",
        "rsaquo" to "›", "middot" to "·", "centerdot" to "·", "bull" to "•", "bullet" to "•",
        "times" to "×", "divide" to "÷", "deg" to "°", "plusmn" to "±", "micro" to "µ",
        "para" to "¶", "sect" to "§", "cent" to "¢", "pound" to "£", "yen" to "¥",
        "euro" to "€", "curren" to "¤", "iexcl" to "¡", "iquest" to "¿", "dagger" to "†",
        "Dagger" to "‡", "permil" to "‰", "prime" to "′", "Prime" to "″", "larr" to "←",
        "rarr" to "→", "uarr" to "↑", "darr" to "↓", "harr" to "↔", "hearts" to "♥",
        "spades" to "♠", "clubs" to "♣", "diams" to "♦", "check" to "✓", "star" to "☆",
        "starf" to "★", "infin" to "∞", "ne" to "≠", "le" to "≤", "ge" to "≥",
        "frac12" to "½", "frac14" to "¼", "frac34" to "¾", "sup1" to "¹", "sup2" to "²",
        "sup3" to "³", "ordf" to "ª", "ordm" to "º", "not" to "¬", "macr" to "¯",
        "acute" to "´", "cedil" to "¸", "uml" to "¨", "brvbar" to "¦", "tilde" to "˜",
        "circ" to "ˆ", "lrm" to "\u200E", "rlm" to "\u200F",
    )
}
