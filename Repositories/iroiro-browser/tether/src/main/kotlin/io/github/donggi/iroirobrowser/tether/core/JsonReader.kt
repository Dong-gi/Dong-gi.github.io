package io.github.donggi.iroirobrowser.tether.core

/**
 * 파일 서버가 받는 JSON 몸(관리 기능의 POST)을 읽는 작고 엄격한 읽개 — 의존을 늘리지 않으려고 손으로 짓는다([Json] 은 쓰기만).
 *
 * 받는 꼴은 RFC 8259 그대로다: 객체는 `Map<String, Any?>`, 배열은 `List<Any?>`, 글은 `String`, 수는 `Long`(정수)·`Double`, 참·거짓,
 * `null`. 글 밖의 찌꺼기·겹친 이름·깊이 [MAX_DEPTH] 넘음·홀로 선 서로게이트는 [Malformed] 로 거절한다. 몸의 크기 상한은 부르는 쪽이 둔다.
 */
object JsonReader {

    /** 이 읽개가 받지 않는 글. */
    class Malformed(message: String) : Exception(message)

    /** 객체·배열의 겹침 상한 — 관리 기능의 몸은 둘을 넘지 않는다. */
    const val MAX_DEPTH = 8

    fun parse(text: String): Any? {
        val parser = Parser(text)
        parser.skipSpace()
        val value = parser.value(0)
        parser.skipSpace()
        if (!parser.atEnd()) throw Malformed("값 뒤에 글이 더 있다")
        return value
    }

    private class Parser(private val text: String) {
        private var at = 0

        fun atEnd(): Boolean = at >= text.length

        fun skipSpace() {
            while (at < text.length && text[at].let { it == ' ' || it == '\t' || it == '\n' || it == '\r' }) at++
        }

        fun value(depth: Int): Any? {
            if (atEnd()) throw Malformed("값이 없다")
            return when (val c = text[at]) {
                '{' -> obj(depth + 1)
                '[' -> array(depth + 1)
                '"' -> string()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c in '0'..'9') number() else throw Malformed("모르는 글자")
            }
        }

        private fun literal(word: String, result: Any?): Any? {
            if (!text.startsWith(word, at)) throw Malformed("모르는 낱말")
            at += word.length
            return result
        }

        private fun obj(depth: Int): Map<String, Any?> {
            if (depth > MAX_DEPTH) throw Malformed("너무 깊다")
            at++ // {
            val out = LinkedHashMap<String, Any?>()
            skipSpace()
            if (peek() == '}') {
                at++
                return out
            }
            while (true) {
                skipSpace()
                if (peek() != '"') throw Malformed("이름이 글이 아니다")
                val key = string()
                if (out.containsKey(key)) throw Malformed("이름이 겹친다")
                skipSpace()
                if (peek() != ':') throw Malformed("':' 가 없다")
                at++
                skipSpace()
                out[key] = value(depth)
                skipSpace()
                when (peek()) {
                    ',' -> at++
                    '}' -> {
                        at++
                        return out
                    }
                    else -> throw Malformed("객체가 닫히지 않았다")
                }
            }
        }

        private fun array(depth: Int): List<Any?> {
            if (depth > MAX_DEPTH) throw Malformed("너무 깊다")
            at++ // [
            val out = ArrayList<Any?>()
            skipSpace()
            if (peek() == ']') {
                at++
                return out
            }
            while (true) {
                skipSpace()
                out += value(depth)
                skipSpace()
                when (peek()) {
                    ',' -> at++
                    ']' -> {
                        at++
                        return out
                    }
                    else -> throw Malformed("배열이 닫히지 않았다")
                }
            }
        }

        private fun string(): String {
            at++ // "
            val out = StringBuilder()
            while (true) {
                if (atEnd()) throw Malformed("글이 닫히지 않았다")
                val c = text[at++]
                when {
                    c == '"' -> break
                    c == '\\' -> {
                        if (atEnd()) throw Malformed("이스케이프가 끊겼다")
                        when (val e = text[at++]) {
                            '"' -> out.append('"')
                            '\\' -> out.append('\\')
                            '/' -> out.append('/')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> out.append(hex4())
                            else -> throw Malformed("모르는 이스케이프 $e")
                        }
                    }
                    c.code < 0x20 -> throw Malformed("글 안의 제어 문자")
                    else -> out.append(c)
                }
            }
            val result = out.toString()
            if (!wellFormed(result)) throw Malformed("홀로 선 서로게이트")
            return result
        }

        private fun hex4(): Char {
            if (at + 4 > text.length) throw Malformed("\\u 가 짧다")
            var v = 0
            repeat(4) {
                val d = Character.digit(text[at++], 16)
                if (d < 0) throw Malformed("\\u 의 16진이 틀렸다")
                v = v * 16 + d
            }
            return v.toChar()
        }

        private fun number(): Any {
            val start = at
            if (peek() == '-') at++
            if (peek() == '0') {
                at++
            } else if (peek() in '1'..'9') {
                while (peek() in '0'..'9') at++
            } else {
                throw Malformed("수가 틀렸다")
            }
            var fraction = false
            if (peek() == '.') {
                fraction = true
                at++
                if (peek() !in '0'..'9') throw Malformed("소수점 뒤에 숫자가 없다")
                while (peek() in '0'..'9') at++
            }
            if (peek() == 'e' || peek() == 'E') {
                fraction = true
                at++
                if (peek() == '+' || peek() == '-') at++
                if (peek() !in '0'..'9') throw Malformed("지수가 틀렸다")
                while (peek() in '0'..'9') at++
            }
            val raw = text.substring(start, at)
            return if (fraction) raw.toDouble() else raw.toLongOrNull() ?: raw.toDouble()
        }

        private fun peek(): Char = if (at < text.length) text[at] else '\u0000'

        private fun wellFormed(s: String): Boolean {
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (Character.isHighSurrogate(c)) {
                    if (i + 1 >= s.length || !Character.isLowSurrogate(s[i + 1])) return false
                    i += 2
                    continue
                }
                if (Character.isLowSurrogate(c)) return false
                i++
            }
            return true
        }
    }
}
