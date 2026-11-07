package io.github.donggi.iroirobrowser.core.sites

import java.io.IOException

/**
 * 읽개가 받지 않은 글. [tooLarge] 면 꼴은 맞았을지 모르나 **상한**(글자 수·값의 수·글 하나의 길이·숫자의 길이)을 넘은 것이다 — 부르는 쪽이
 * 그것을 TOO_LARGE 로, 나머지(꼴이 틀림·깊이)는 UNEXPECTED 로 옮긴다. `IOException` 인 것은 옮기기를 잊어도 계약(실패는 `IOException`)
 * 밖으로 새지 않게 하려는 것이다.
 */
internal class JsonException(message: String, val tooLarge: Boolean = false) : IOException(message)

/**
 * 사이트의 답(pixiv 의 AJAX·hitomi 의 갤러리 정보)과 우리 계획 글([GalleryCodec])을 읽고 쓰는 작은 JSON. `:core` 에 JSON 읽개가 없고 새 의존을
 * 들이지 않는다(테더의 `Json` 은 쓰기만 하고 다른 모듈이다).
 *
 * 나무는 Map(차례를 지키는 `LinkedHashMap` — 같은 키는 뒤의 것)·List·String·Long(소수점·지수가 없고 Long 에 드는 정수)·Double·Boolean·null.
 * RFC 8259 를 엄격하게 읽는다 — 주석·끝의 쉼표·작은따옴표·`NaN`·글 안의 날것 제어 문자는 받지 않는다(데스크톱의 `json.loads` 도 그랬다).
 * 남이 준 글이라 **상한을 먼저** 본다: 글자 수, 깊이(재귀의 깊이가 곧 이것이다), 글 하나의 길이, 값의 수(키도 센다 — 나무의 메모리), 숫자의 길이.
 */
internal object Json {

    class Limits(
        val maxChars: Int,
        val maxDepth: Int,
        val maxStringChars: Int,
        val maxValues: Int,
    )

    /**
     * 사이트의 답 — 한 덩이는 [GalleryResolver.FETCH_LIMIT] 바이트라 글자도 그만큼까지. 값 50만은 pixiv 의 작품 정보(작가의 모든 작품 번호를
     * `userIllusts` 로 싣는다 — 작품 2,316 개인 공식 계정에서 약 4천 값, 2026-10-11 실측)의 백 배가 넘고, 나무로 펴도 수십 MB 에 그친다.
     */
    val SITE = Limits(maxChars = GalleryResolver.FETCH_LIMIT, maxDepth = 64, maxStringChars = 1 shl 20, maxValues = 500_000)

    /** 숫자 하나의 글자 상한 — `Double.parseDouble` 에 큰 글을 넘기지 않는다. */
    private const val MAX_NUMBER_CHARS = 64

    /** 글 전체가 값 하나여야 한다(앞뒤 공백·맨 앞의 BOM 만 허락). */
    fun parse(text: String, limits: Limits): Any? {
        checkLength(text, limits)
        val start = if (text.isNotEmpty() && text[0] == '\uFEFF') 1 else 0
        val reader = Reader(text, start, limits)
        val value = reader.value(0)
        reader.skipSpace()
        if (!reader.atEnd()) throw JsonException("값 뒤에 글이 남았다(${reader.position})")
        return value
    }

    /** [start] 에서 값 하나를 읽고 (값, 그 뒤의 자리) — 뒤에 무엇이 와도 되는지는 부른 쪽이 정한다(hitomi 의 끝 `;`). */
    fun parsePrefix(text: String, start: Int, limits: Limits): Pair<Any?, Int> {
        checkLength(text, limits)
        val reader = Reader(text, start, limits)
        val value = reader.value(0)
        return value to reader.position
    }

    /** 나무를 글로 — Map 의 키는 글이어야 하고, 숫자는 유한해야 한다(아니면 [IllegalArgumentException] — 우리 코드의 잘못이다). */
    fun write(value: Any?): String = StringBuilder().also { writeTo(it, value, 0) }.toString()

    private fun checkLength(text: String, limits: Limits) {
        if (text.length > limits.maxChars) throw JsonException("글이 너무 길다(${text.length}자)", tooLarge = true)
    }

    private fun writeTo(out: StringBuilder, value: Any?, depth: Int) {
        require(depth <= 64) { "너무 깊다" }
        when (value) {
            null -> out.append("null")
            is String -> quote(out, value)
            is Boolean -> out.append(if (value) "true" else "false")
            is Long, is Int, is Short, is Byte -> out.append(value.toString())
            is Double -> {
                require(!value.isNaN() && !value.isInfinite()) { "유한하지 않은 수" }
                out.append(value.toString())
            }
            is Map<*, *> -> {
                out.append('{')
                var first = true
                for ((key, item) in value) {
                    require(key is String) { "키가 글이 아니다" }
                    if (!first) out.append(',')
                    first = false
                    quote(out, key)
                    out.append(':')
                    writeTo(out, item, depth + 1)
                }
                out.append('}')
            }
            is List<*> -> {
                out.append('[')
                for ((index, item) in value.withIndex()) {
                    if (index > 0) out.append(',')
                    writeTo(out, item, depth + 1)
                }
                out.append(']')
            }
            else -> throw IllegalArgumentException("JSON 으로 쓸 수 없는 값: ${value.javaClass.name}")
        }
    }

    /**
     * 글 리터럴. 제어 문자는 이스케이프로, **홀로 선 서로게이트도** `\uXXXX` 로 쓴다 — 그대로 두면 파일에 UTF-8 로 쓸 때 `?` 가 되어 왕복이
     * 깨진다. 16진은 손으로(`String.format` 은 기본 로케일을 탄다).
     */
    private fun quote(out: StringBuilder, text: String) {
        out.append('"')
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '"' -> out.append("\\\"")
                c == '\\' -> out.append("\\\\")
                c == '\n' -> out.append("\\n")
                c == '\r' -> out.append("\\r")
                c == '\t' -> out.append("\\t")
                c.code < 0x20 -> unicodeEscape(out, c)
                Character.isHighSurrogate(c) && i + 1 < text.length && Character.isLowSurrogate(text[i + 1]) -> {
                    out.append(c).append(text[i + 1])
                    i++
                }
                Character.isSurrogate(c) -> unicodeEscape(out, c)
                else -> out.append(c)
            }
            i++
        }
        out.append('"')
    }

    private fun unicodeEscape(out: StringBuilder, c: Char) {
        val code = c.code
        out.append("\\u")
        out.append(HEX[(code shr 12) and 0xF]).append(HEX[(code shr 8) and 0xF])
        out.append(HEX[(code shr 4) and 0xF]).append(HEX[code and 0xF])
    }

    private const val HEX = "0123456789abcdef"

    /** 재귀로 읽는다 — 깊이 상한([Limits.maxDepth])이 스택을 묶는다(층마다 프레임 둘). */
    private class Reader(private val s: String, start: Int, private val limits: Limits) {
        var position = start
            private set
        private var values = 0

        fun atEnd(): Boolean = position >= s.length

        fun skipSpace() {
            while (position < s.length) {
                val c = s[position]
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') position++ else return
            }
        }

        fun value(depth: Int): Any? {
            skipSpace()
            if (position >= s.length) fail("값이 있어야 할 자리에서 글이 끝났다")
            count()
            return when (val c = s[position]) {
                '{' -> obj(depth + 1)
                '[' -> array(depth + 1)
                '"' -> string()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c in '0'..'9') number() else fail("뜻밖의 글자")
            }
        }

        private fun count() {
            values++
            if (values > limits.maxValues) throw JsonException("값이 너무 많다(${limits.maxValues} 넘음)", tooLarge = true)
        }

        private fun obj(depth: Int): Map<String, Any?> {
            if (depth > limits.maxDepth) fail("너무 깊다(${limits.maxDepth} 넘음)")
            position++
            val map = LinkedHashMap<String, Any?>()
            skipSpace()
            if (peek() == '}') {
                position++
                return map
            }
            while (true) {
                skipSpace()
                if (peek() != '"') fail("키가 글이 아니다")
                count()
                val key = string()
                skipSpace()
                if (peek() != ':') fail("키 뒤에 ':' 가 없다")
                position++
                map[key] = value(depth)
                skipSpace()
                when (peek()) {
                    ',' -> position++
                    '}' -> {
                        position++
                        return map
                    }
                    else -> fail("',' 나 '}' 가 없다")
                }
            }
        }

        private fun array(depth: Int): List<Any?> {
            if (depth > limits.maxDepth) fail("너무 깊다(${limits.maxDepth} 넘음)")
            position++
            val list = ArrayList<Any?>()
            skipSpace()
            if (peek() == ']') {
                position++
                return list
            }
            while (true) {
                list.add(value(depth))
                skipSpace()
                when (peek()) {
                    ',' -> position++
                    ']' -> {
                        position++
                        return list
                    }
                    else -> fail("',' 나 ']' 가 없다")
                }
            }
        }

        /** 이스케이프 없는 토막은 통째로 옮긴다(글자마다 붙이면 큰 답에서 느리다). */
        private fun string(): String {
            position++
            val out = StringBuilder()
            while (true) {
                val runStart = position
                while (position < s.length) {
                    val c = s[position]
                    if (c == '"' || c == '\\' || c < ' ') break
                    position++
                }
                out.append(s, runStart, position)
                if (out.length > limits.maxStringChars) throw JsonException("글이 너무 길다", tooLarge = true)
                if (position >= s.length) fail("글이 닫히지 않았다")
                val c = s[position++]
                when {
                    c == '"' -> return out.toString()
                    c == '\\' -> escape(out)
                    else -> fail("글 안에 날것의 제어 문자")
                }
                if (out.length > limits.maxStringChars) throw JsonException("글이 너무 길다", tooLarge = true)
            }
        }

        private fun escape(out: StringBuilder) {
            if (position >= s.length) fail("이스케이프가 끝에서 잘렸다")
            when (s[position++]) {
                '"' -> out.append('"')
                '\\' -> out.append('\\')
                '/' -> out.append('/')
                'b' -> out.append('\b')
                'f' -> out.append('\u000C')
                'n' -> out.append('\n')
                'r' -> out.append('\r')
                't' -> out.append('\t')
                'u' -> {
                    if (position + 4 > s.length) fail("\\u 뒤가 모자라다")
                    var code = 0
                    for (k in 0 until 4) {
                        val digit = hexValue(s[position + k])
                        if (digit < 0) fail("\\u 뒤가 16진이 아니다")
                        code = (code shl 4) or digit
                    }
                    position += 4
                    out.append(code.toChar())
                }
                else -> fail("모르는 이스케이프")
            }
        }

        private fun number(): Any {
            val start = position
            if (peek() == '-') position++
            when {
                peek() == '0' -> position++
                peek() in '1'..'9' -> skipDigits()
                else -> fail("숫자가 아니다")
            }
            var integral = true
            if (peek() == '.') {
                position++
                if (peek() !in '0'..'9') fail("소수점 뒤에 숫자가 없다")
                skipDigits()
                integral = false
            }
            if (peek() == 'e' || peek() == 'E') {
                position++
                if (peek() == '+' || peek() == '-') position++
                if (peek() !in '0'..'9') fail("지수에 숫자가 없다")
                skipDigits()
                integral = false
            }
            if (position - start > MAX_NUMBER_CHARS) throw JsonException("숫자가 너무 길다", tooLarge = true)
            val text = s.substring(start, position)
            if (integral) text.toLongOrNull()?.let { return it }
            val d = text.toDouble()
            if (d.isInfinite() || d.isNaN()) fail("숫자가 범위를 넘는다")
            return d
        }

        /** 긴 숫자도 한 번 훑고 끝난다(선형) — 길이는 다 읽은 뒤 [number] 가 본다. */
        private fun skipDigits() {
            while (position < s.length && s[position] in '0'..'9') position++
        }

        private fun literal(word: String, value: Any?): Any? {
            if (!s.startsWith(word, position)) fail("모르는 낱말")
            position += word.length
            return value
        }

        private fun peek(): Char = if (position < s.length) s[position] else '\u0000'

        private fun fail(what: String): Nothing = throw JsonException("$what($position)")

        private fun hexValue(c: Char): Int = when (c) {
            in '0'..'9' -> c - '0'
            in 'a'..'f' -> c - 'a' + 10
            in 'A'..'F' -> c - 'A' + 10
            else -> -1
        }
    }
}
