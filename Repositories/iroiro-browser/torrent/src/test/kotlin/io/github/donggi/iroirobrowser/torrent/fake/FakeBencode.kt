package io.github.donggi.iroirobrowser.torrent.fake

import java.io.ByteArrayOutputStream

/**
 * 가짜 쪽 전용 bencode(BEP 3). 엔진의 것과 일부러 따로 지었다.
 *
 * 값의 꼴: [Long], [ByteArray](문자열), [List], [Map]<String, Any>.
 * 사전 키는 ISO-8859-1 로 바이트를 그대로 보존한다(키 바이트 0x00~0xFF ↔ 글자 U+0000~U+00FF).
 * 인코더는 편의로 Int·Short·Byte 와 String(값이면 UTF-8)도 받는다. 사전 키는 바이트 차례로 정렬해 쓴다.
 */
object FakeBencode {

    /** 풀 수 없는 입력. [offset] 은 문제가 드러난 자리. */
    class FormatException(message: String, val offset: Int) : IllegalArgumentException("$message (offset $offset)")

    private const val MAX_DEPTH = 256

    fun encode(value: Any): ByteArray {
        val out = ByteArrayOutputStream()
        encodeTo(out, value, 0)
        return out.toByteArray()
    }

    /**
     * 입력 전체가 값 하나여야 한다.
     * [strict] 면 사전 키의 차례(바이트 오름차순)와 중복, 문자열 길이의 앞자리 0 도 본다.
     */
    fun decode(bytes: ByteArray, strict: Boolean = true): Any {
        val (value, end) = decodePrefix(bytes, 0, strict)
        if (end != bytes.size) throw FormatException("값 뒤에 바이트가 남았다", end)
        return value
    }

    /** [offset] 에서 값 하나를 풀어 (값, 끝 오프셋)을 돌려준다. ut_metadata 처럼 사전 뒤에 날 바이트가 붙는 꼴에 쓴다. */
    fun decodePrefix(bytes: ByteArray, offset: Int = 0, strict: Boolean = true): Pair<Any, Int> {
        val reader = Reader(bytes, strict)
        reader.pos = offset
        val value = reader.value(0)
        return value to reader.pos
    }

    private fun encodeTo(out: ByteArrayOutputStream, value: Any?, depth: Int) {
        require(depth <= MAX_DEPTH) { "너무 깊게 겹쳤다" }
        when (value) {
            is Long -> writeAscii(out, "i${value}e")
            is Int, is Short, is Byte -> writeAscii(out, "i${(value as Number).toLong()}e")
            is ByteArray -> writeString(out, value)
            is String -> writeString(out, value.toByteArray(Charsets.UTF_8))
            is List<*> -> {
                out.write('l'.code)
                for (item in value) encodeTo(out, item, depth + 1)
                out.write('e'.code)
            }
            is Map<*, *> -> {
                val entries = value.entries.map { (k, v) ->
                    require(k is String) { "사전 키는 String 이어야 한다: $k" }
                    require(k.all { it.code <= 0xFF }) { "사전 키는 ISO-8859-1 글자만: $k" }
                    k.toByteArray(Charsets.ISO_8859_1) to v
                }.sortedWith { a, b -> java.util.Arrays.compareUnsigned(a.first, b.first) }
                for (i in 1 until entries.size) {
                    require(!entries[i - 1].first.contentEquals(entries[i].first)) { "사전 키가 겹친다" }
                }
                out.write('d'.code)
                for ((k, v) in entries) {
                    writeString(out, k)
                    encodeTo(out, requireNotNull(v) { "null 값은 쓸 수 없다" }, depth + 1)
                }
                out.write('e'.code)
            }
            else -> throw IllegalArgumentException("bencode 로 쓸 수 없는 값: ${value?.javaClass?.name}")
        }
    }

    private fun writeAscii(out: ByteArrayOutputStream, s: String) {
        out.write(s.toByteArray(Charsets.US_ASCII))
    }

    private fun writeString(out: ByteArrayOutputStream, bytes: ByteArray) {
        writeAscii(out, "${bytes.size}:")
        out.write(bytes)
    }

    private class Reader(private val b: ByteArray, private val strict: Boolean) {
        var pos = 0

        fun value(depth: Int): Any {
            if (depth > MAX_DEPTH) fail("너무 깊게 겹쳤다")
            return when (val c = peek()) {
                'i' -> {
                    pos++
                    integer()
                }
                'l' -> {
                    pos++
                    val list = ArrayList<Any>()
                    while (peek() != 'e') list += value(depth + 1)
                    pos++
                    list
                }
                'd' -> {
                    pos++
                    val map = LinkedHashMap<String, Any>()
                    var previous: ByteArray? = null
                    while (peek() != 'e') {
                        val keyAt = pos
                        if (peek() !in '0'..'9') fail("사전 키가 문자열이 아니다")
                        val key = string()
                        if (previous != null) {
                            val order = java.util.Arrays.compareUnsigned(previous, key)
                            if (order == 0) fail("사전 키가 겹친다", keyAt)
                            if (strict && order > 0) fail("사전 키 차례가 어긋났다", keyAt)
                        }
                        val name = String(key, Charsets.ISO_8859_1)
                        if (map.containsKey(name)) fail("사전 키가 겹친다", keyAt)
                        previous = key
                        map[name] = value(depth + 1)
                    }
                    pos++
                    map
                }
                in '0'..'9' -> string()
                else -> fail("모르는 머리 글자 '${printable(c)}'")
            }
        }

        private fun peek(): Char {
            if (pos >= b.size) fail("값이 도중에 끊겼다")
            return (b[pos].toInt() and 0xFF).toChar()
        }

        /** 'i' 뒤 — 정수와 끝의 'e'. */
        private fun integer(): Long {
            val start = pos
            val negative = peek() == '-'
            if (negative) pos++
            val digitsAt = pos
            while (peek() in '0'..'9') pos++
            if (pos == digitsAt) fail("정수에 숫자가 없다", start)
            if (peek() != 'e') fail("정수 끝이 'e' 가 아니다")
            val digits = String(b, digitsAt, pos - digitsAt, Charsets.US_ASCII)
            if (digits.length > 1 && digits[0] == '0') fail("정수의 앞자리 0", start)
            if (negative && digits == "0") fail("-0 은 쓸 수 없다", start)
            pos++
            return (if (negative) "-$digits" else digits).toLongOrNull() ?: fail("정수가 Long 범위를 넘는다", start)
        }

        /** 길이:바이트. */
        private fun string(): ByteArray {
            val start = pos
            while (peek() in '0'..'9') pos++
            if (pos == start) fail("문자열 길이가 없다", start)
            if (peek() != ':') fail("문자열 길이 뒤가 ':' 가 아니다")
            val digits = String(b, start, pos - start, Charsets.US_ASCII)
            if (strict && digits.length > 1 && digits[0] == '0') fail("문자열 길이의 앞자리 0", start)
            pos++
            val length = digits.toLongOrNull() ?: fail("문자열 길이가 너무 크다", start)
            if (length > b.size - pos) fail("문자열이 도중에 끊겼다", start)
            val out = b.copyOfRange(pos, pos + length.toInt())
            pos += length.toInt()
            return out
        }

        private fun printable(c: Char): String = if (c.code in 0x20..0x7E) c.toString() else "\\x%02x".format(c.code)

        private fun fail(message: String, at: Int = pos): Nothing = throw FormatException(message, at)
    }
}
