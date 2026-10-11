package io.github.donggi.iroiroviewer.format.html

import java.util.Locale

/**
 * CSS 의 **참조** — 주소 함수(`url()`·`src()`), `image-set()`·`-webkit-image-set()` 안의 문자열, `@import` — 를 CSS 문법대로
 * 찾아 바꾼다. 위생기(`Css`)와 HTML 미리보기(`format:text`)가 **한 벌**을 쓴다(규칙이 두 벌이면 한쪽만 고쳐진다 — 5단계의 교훈).
 *
 * ## 글자로 찾으면 막지 못한다
 *
 * 예전 위생기는 `url(`·`@import` 를 글자로 찾았다. CSS 는 이름의 **이스케이프를 푼 뒤에** 함수와 규칙을 가른다 —
 * `\75 rl(…)`·`u\72l(…)` 도 `url(` 이고 `@\69mport` 도 `@import` 다. `image-set("…" 1x)` 는 따옴표만으로 주소다. 글자 찾기는
 * 이것들을 못 보고(가로채기의 빈 404 와 `INTERNET` 미선언이 막고 있었다), 거꾸로 문자열 안의 `url(`(`content:"url(x)"`)은
 * 주소로 잘못 보아 글을 `none` 으로 망가뜨렸다. 그리고 `url(` 뒤의 **첫 `)`** 를 끝으로 읽어 `bg(1).png`·SVG 를 품은 `data:` 가
 * 깨졌다. 여기서는 문자열·주석·이스케이프를 알고 읽는다.
 *
 * ## 선형이다
 *
 * 앞으로 한 번만 읽는다. 닫히지 않은 주소·문자열·블록은 명세대로 그 자리(없으면 끝)까지를 한 덩이로 먹는다 — 그 자리부터 다시
 * 훑지 않는다(함정 표의 '되풀이될 수 있는 것의 탐색 범위'). 예전의 `@import` 지우기는 규칙마다 `;`·`{` 를 각각 끝까지 찾아
 * `@import;` 가 되풀이되면 제곱이었다(8 MiB 에 135초).
 */
object CssRefs {

    /** 참조 하나를 어떻게 적을 것인가. */
    sealed interface Out {
        /** 원문 그대로(같은 문서 안의 `#id` 등). */
        data object Keep : Out

        /**
         * `none` 으로. 선언을 지우면 중괄호 균형이 깨지고, 빈 `url()` 은 WebView 가 이 쪽을 다시 가져오려 든다. `image-set` 안이면
         * 그 값 전체가 틀린 값이 되어 브라우저가 선언을 버린다 — 실을 수 없는 것을 싣지 않는 쪽으로 틀린다.
         */
        data object None : Out

        /** `url("…")` 로. [url] 에는 따옴표·역슬래시·줄바꿈이 없어야 한다 — 부르는 쪽이 지킨다. */
        class Url(val url: String) : Out
    }

    /**
     * @param limit 결과가 이보다 길어지면 멈추고 null 을 준다 — 쓰는 도중에 잰다(다 만든 뒤에 재면 그 사이에 메모리가 넘친다).
     * @param checkpoint 참조 [CHECK_EVERY] 개마다 — 부르는 쪽이 취소를 여기서 던진다.
     * @param onImport `@import` 규칙 하나를 지울 때마다. 가리킨 주소를 읽을 수 있으면 그 주소(이스케이프를 푼 것)를 준다.
     * @param map 주소 하나(이스케이프를 푼 값) → 적을 것.
     */
    fun rewrite(
        css: String,
        limit: Int = Int.MAX_VALUE,
        checkpoint: () -> Unit = {},
        onImport: (String?) -> Unit = {},
        map: (String) -> Out,
    ): String? {
        val n = css.length
        val out = StringBuilder(minOf(n, limit) + 16)
        // 괄호 깊이와, `image-set` 이 연 깊이들. 그 **바로 안**의 문자열만 주소다(`type("image/webp")` 의 것은 아니다).
        var depth = 0
        val sets = ArrayList<Int>()
        var refs = 0
        var i = 0
        while (i < n) {
            if (out.length > limit) return null
            val c = css[i]
            when {
                c == '/' && i + 1 < n && css[i + 1] == '*' -> {
                    val close = css.indexOf("*/", i + 2)
                    val end = if (close < 0) n else close + 2
                    out.append(css, i, end)
                    i = end
                }
                c == '"' || c == '\'' -> {
                    val value = StringBuilder()
                    val end = string(css, i, value)
                    if (sets.isNotEmpty() && sets.last() == depth && end.ok) {
                        if (++refs % CHECK_EVERY == 0) checkpoint()
                        write(out, map(value.toString()), css, i, end.at)
                    } else {
                        out.append(css, i, end.at)
                    }
                    i = end.at
                }
                c == '@' && startsIdent(css, i + 1) -> {
                    val name = StringBuilder()
                    val end = ident(css, i + 1, name)
                    if (name.toString().equals("import", ignoreCase = true)) {
                        if (++refs % CHECK_EVERY == 0) checkpoint()
                        i = importRule(css, end, onImport)
                    } else {
                        out.append(css, i, end)
                        i = end
                    }
                }
                startsIdent(css, i) -> {
                    val name = StringBuilder()
                    val end = ident(css, i, name)
                    if (end < n && css[end] == '(') {
                        val lower = name.toString().lowercase(Locale.ROOT)
                        when (lower) {
                            "url", "src" -> {
                                if (++refs % CHECK_EVERY == 0) checkpoint()
                                val value = StringBuilder()
                                // `src()` 는 문자열만 받는다(CSS Values 4). 따옴표 없는 것은 틀린 값이다.
                                val argEnd = urlArg(css, end + 1, value, unquoted = lower == "url")
                                if (argEnd < 0) {
                                    out.append(NONE)
                                    i = badUrlEnd(css, end + 1)
                                } else {
                                    write(out, map(value.toString()), css, i, argEnd)
                                    i = argEnd
                                }
                            }
                            "image-set", "-webkit-image-set" -> {
                                out.append(css, i, end + 1)
                                depth++
                                sets.add(depth)
                                i = end + 1
                            }
                            else -> {
                                out.append(css, i, end + 1)
                                depth++
                                i = end + 1
                            }
                        }
                    } else {
                        out.append(css, i, end)
                        i = end
                    }
                }
                c == '(' -> {
                    depth++
                    out.append(c)
                    i++
                }
                c == ')' -> {
                    if (sets.isNotEmpty() && sets.last() == depth) sets.removeAt(sets.lastIndex)
                    if (depth > 0) depth--
                    out.append(c)
                    i++
                }
                else -> {
                    out.append(c)
                    i++
                }
            }
        }
        return if (out.length > limit) null else out.toString()
    }

    /** CSS 안에 참조(주소 함수·`image-set` 의 문자열·`@import`)가 하나라도 있는가. SMIL 의 값 같은 짧은 글에 쓴다. */
    fun hasRefs(css: String): Boolean {
        var found = false
        rewrite(css, onImport = { found = true }) {
            found = true
            Out.Keep
        }
        return found
    }

    private const val CHECK_EVERY = 256
    private const val NONE = "none"

    private fun write(out: StringBuilder, r: Out, css: String, from: Int, to: Int) {
        when (r) {
            Out.Keep -> out.append(css, from, to)
            Out.None -> out.append(NONE)
            is Out.Url -> out.append("url(\"").append(r.url).append("\")")
        }
    }

    /** 문자열의 끝. [ok] 가 거짓이면 줄바꿈에서 끝난 망가진 문자열이다(명세 — 줄바꿈은 먹지 않는다). */
    private class End(val at: Int, val ok: Boolean)

    /** [open] 의 따옴표부터 문자열 하나를 읽어 푼 값을 [value] 에 쓴다. 파일 끝에서 끝난 것은 명세대로 받는다. */
    private fun string(css: String, open: Int, value: StringBuilder): End {
        val quote = css[open]
        val n = css.length
        var i = open + 1
        while (i < n) {
            val c = css[i]
            when {
                c == quote -> return End(i + 1, true)
                isNewline(c) -> return End(i, false)
                c == '\\' -> {
                    if (i + 1 >= n) return End(n, true)
                    val next = css[i + 1]
                    i = when {
                        // 문자열 안에서 줄을 잇는 이스케이프 — 글자가 아니다.
                        next == '\r' && i + 2 < n && css[i + 2] == '\n' -> i + 3
                        isNewline(next) -> i + 2
                        else -> escape(css, i + 1, value)
                    }
                }
                else -> {
                    value.append(c)
                    i++
                }
            }
        }
        return End(n, true)
    }

    /**
     * `url(`·`src(` 뒤의 인자. 문자열이거나(둘 다) 따옴표 없는 주소(`url` 만)다. 닫는 `)` 다음 자리를 주고, 틀렸으면 -1 —
     * 부르는 쪽이 명세의 '망가진 주소' 로 `)` 까지를 버린다. 파일 끝에서 닫히지 않은 것은 명세대로 받는다.
     */
    private fun urlArg(css: String, from: Int, value: StringBuilder, unquoted: Boolean): Int {
        val n = css.length
        var i = skipSpace(css, from)
        if (i >= n) return n
        val c = css[i]
        if (c == '"' || c == '\'') {
            val end = string(css, i, value)
            if (!end.ok) return -1
            i = skipSpace(css, end.at)
            if (i >= n) return n
            return if (css[i] == ')') i + 1 else -1
        }
        if (!unquoted) return -1
        while (i < n) {
            val ch = css[i]
            when {
                ch == ')' -> return i + 1
                isSpace(ch) -> {
                    i = skipSpace(css, i)
                    if (i >= n) return n
                    return if (css[i] == ')') i + 1 else -1
                }
                ch == '"' || ch == '\'' || ch == '(' || isNonPrintable(ch) -> return -1
                ch == '\\' -> {
                    if (i + 1 < n && isNewline(css[i + 1])) return -1
                    i = escape(css, i + 1, value)
                }
                else -> {
                    value.append(ch)
                    i++
                }
            }
        }
        return n
    }

    /** 망가진 주소의 끝 — `)` 다음 자리, 없으면 끝. 이스케이프된 `)` 는 건너뛴다. */
    private fun badUrlEnd(css: String, from: Int): Int {
        var i = from
        val n = css.length
        while (i < n) {
            when (css[i]) {
                ')' -> return i + 1
                '\\' -> i += 2
                else -> i++
            }
        }
        return n
    }

    /**
     * `@import` 규칙 하나를 지운다. 가리킨 주소(문자열이나 `url()`)를 읽어 [onImport] 에 주고, 규칙의 끝(`;`, 또는 블록이 있으면
     * 그 블록의 끝)을 문자열·주석·이스케이프를 알고 찾는다. 앞으로만 읽는다 — 규칙마다 파일 끝까지 다시 찾지 않는다.
     */
    private fun importRule(css: String, from: Int, onImport: (String?) -> Unit): Int {
        val n = css.length
        var i = skipSpace(css, from)
        var url: String? = null
        if (i < n && (css[i] == '"' || css[i] == '\'')) {
            val value = StringBuilder()
            val end = string(css, i, value)
            if (end.ok) url = value.toString()
            i = end.at
        } else if (startsIdent(css, i)) {
            val name = StringBuilder()
            val end = ident(css, i, name)
            if (end < n && css[end] == '(' && name.toString().equals("url", ignoreCase = true)) {
                val value = StringBuilder()
                val argEnd = urlArg(css, end + 1, value, unquoted = true)
                if (argEnd >= 0) {
                    url = value.toString()
                    i = argEnd
                } else {
                    i = badUrlEnd(css, end + 1)
                }
            } else {
                i = end
            }
        }
        onImport(url)
        while (i < n) {
            val c = css[i]
            when {
                c == ';' -> return i + 1
                c == '{' -> return blockEnd(css, i)
                c == '"' || c == '\'' -> i = string(css, i, StringBuilder()).at
                c == '/' && i + 1 < n && css[i + 1] == '*' -> {
                    val close = css.indexOf("*/", i + 2)
                    i = if (close < 0) n else close + 2
                }
                c == '\\' -> i += 2
                else -> i++
            }
        }
        return n
    }

    /** [open] 의 `{` 와 짝인 `}` 다음 자리. 문자열·주석·이스케이프 안의 괄호는 세지 않는다. 짝이 없으면 끝. */
    private fun blockEnd(css: String, open: Int): Int {
        val n = css.length
        var depth = 0
        var i = open
        while (i < n) {
            val c = css[i]
            when {
                c == '{' -> {
                    depth++
                    i++
                }
                c == '}' -> {
                    depth--
                    i++
                    if (depth == 0) return i
                }
                c == '"' || c == '\'' -> i = string(css, i, StringBuilder()).at.let { if (it == i) i + 1 else it }
                c == '/' && i + 1 < n && css[i + 1] == '*' -> {
                    val close = css.indexOf("*/", i + 2)
                    i = if (close < 0) n else close + 2
                }
                c == '\\' -> i += 2
                else -> i++
            }
        }
        return n
    }

    // ---- 이름 -------------------------------------------------------------------------

    /** [i] 에서 이름(ident)이 시작되는가(명세의 'would start an ident sequence'). */
    private fun startsIdent(css: String, i: Int): Boolean {
        if (i >= css.length) return false
        val c = css[i]
        return when {
            isNameStart(c) -> true
            c == '-' -> i + 1 < css.length && (isNameStart(css[i + 1]) || css[i + 1] == '-' || isEscape(css, i + 1))
            c == '\\' -> isEscape(css, i)
            else -> false
        }
    }

    /** 이름 하나를 읽어 푼 값을 [name] 에 쓰고 다음 자리를 준다. */
    private fun ident(css: String, from: Int, name: StringBuilder): Int {
        var i = from
        while (i < css.length) {
            val c = css[i]
            when {
                isNameChar(c) -> {
                    name.append(c)
                    i++
                }
                isEscape(css, i) -> i = escape(css, i + 1, name)
                else -> return i
            }
        }
        return i
    }

    /**
     * [at] 은 역슬래시 다음 자리. 16진 숫자 1~6개(뒤의 공백 하나까지) 또는 글자 하나를 풀어 [sb] 에 쓰고 다음 자리를 준다.
     * 0·서로게이트·상한 밖·파일 끝은 U+FFFD(명세).
     */
    private fun escape(css: String, at: Int, sb: StringBuilder): Int {
        val n = css.length
        if (at >= n) {
            sb.append('\uFFFD')
            return n
        }
        if (hexValue(css[at]) < 0) {
            val cp = css.codePointAt(at)
            sb.appendCodePoint(cp)
            return at + Character.charCount(cp)
        }
        var i = at
        var cp = 0
        while (i < n && i - at < 6) {
            val d = hexValue(css[i])
            if (d < 0) break
            cp = cp * 16 + d
            i++
        }
        if (i < n && css[i] == '\r' && i + 1 < n && css[i + 1] == '\n') {
            i += 2
        } else if (i < n && isSpace(css[i])) {
            i++
        }
        sb.appendCodePoint(if (cp == 0 || cp > 0x10FFFF || cp in 0xD800..0xDFFF) 0xFFFD else cp)
        return i
    }

    /** 역슬래시가 이스케이프를 여는가 — 뒤가 줄바꿈이면 아니다(명세). */
    private fun isEscape(css: String, i: Int): Boolean =
        css[i] == '\\' && (i + 1 >= css.length || !isNewline(css[i + 1]))

    private fun isNameStart(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z' || c == '_' || c.code >= 0x80

    private fun isNameChar(c: Char): Boolean = isNameStart(c) || c in '0'..'9' || c == '-'

    private fun hexValue(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> -1
    }

    private fun skipSpace(css: String, from: Int): Int {
        var i = from
        while (i < css.length && isSpace(css[i])) i++
        return i
    }

    private fun isSpace(c: Char): Boolean = c == ' ' || c == '\t' || isNewline(c)

    private fun isNewline(c: Char): Boolean = c == '\n' || c == '\r' || c == '\u000C'

    /** 따옴표 없는 주소에 올 수 없는 제어 문자(명세의 non-printable). */
    private fun isNonPrintable(c: Char): Boolean =
        c in '\u0000'..'\u0008' || c == '\u000B' || c in '\u000E'..'\u001F' || c == '\u007F'
}
