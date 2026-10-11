package io.github.donggi.iroirobrowser.torrent.bencode

import java.io.ByteArrayOutputStream

/**
 * bencode(BEP 3)의 값. [start]·[end] 는 읽은 입력에서 이 값이 차지한 바이트 구간이다 — info-hash 는 info 사전을 다시 짓지 않고
 * **원래 바이트 구간**의 SHA-1 로 낸다(키 차례가 틀린 토렌트도 다른 클라이언트와 같은 info-hash 가 나온다).
 */
sealed class BValue(val start: Int, val end: Int)

class BInt(val value: Long, start: Int, end: Int) : BValue(start, end) {
    override fun toString(): String = "i${value}e"
}

class BBytes(val bytes: ByteArray, start: Int, end: Int) : BValue(start, end) {
    /** UTF-8 로 푼 글 — 깨진 바이트는 U+FFFD 로. */
    fun utf8(): String = bytes.toString(Charsets.UTF_8)

    /** 바이트를 하나씩 글자로(ISO-8859-1) — 바이트를 잃지 않는다. 사전의 키가 이 꼴이다. */
    fun latin1(): String = String(bytes, Charsets.ISO_8859_1)

    override fun toString(): String = "${bytes.size}:${latin1().take(40)}"
}

class BList(val items: List<BValue>, start: Int, end: Int) : BValue(start, end) {
    override fun toString(): String = items.joinToString(prefix = "l", postfix = "e")
}

/** 키는 바이트를 ISO-8859-1 로 옮긴 글이다(ASCII 키는 그대로 읽힌다). 들어온 차례를 지킨다. */
class BDict(val entries: Map<String, BValue>, start: Int, end: Int) : BValue(start, end) {
    operator fun get(key: String): BValue? = entries[key]
    fun bytes(key: String): ByteArray? = (entries[key] as? BBytes)?.bytes
    fun string(key: String): String? = (entries[key] as? BBytes)?.utf8()
    fun long(key: String): Long? = (entries[key] as? BInt)?.value
    fun dict(key: String): BDict? = entries[key] as? BDict
    fun list(key: String): BList? = entries[key] as? BList
    override fun toString(): String = entries.entries.joinToString(prefix = "d", postfix = "e") { "${it.key}=${it.value}" }
}

/** 받은 bencode 가 깨졌거나 상한을 넘었다. [offset] 은 문제를 찾은 바이트 자리. */
class BencodeException(message: String, val offset: Int) : Exception("$message @$offset")

/**
 * 남이 지은 bencode 를 읽는다 — 상한을 지키며. 재귀 대신 **명시적 스택**으로 읽어 깊이가 스레드 스택에 닿지 않는다.
 *
 * - 크기·깊이·토큰 수 상한([Limits] — libtorrent 의 `load_torrent_limits` 와 같은 수: 10 MB·토큰 3백만, 깊이는 64).
 * - 문자열 길이는 **할당하기 전에** 남은 바이트와 견준다 — `999999999:` 한 줄로 큰 배열을 잡게 하지 못한다.
 * - 정수: 선행 0(`i03e`), `-0`, 빈 숫자(`ie`·`i-e`), 19자리 넘는 숫자, Long 넘침을 거부한다. 문자열 길이에도 선행 0·음수를 받지 않는다.
 * - 사전: 키가 문자열이 아니거나 같은 키가 두 번이면 거부. 키 차례는 따지지 않는다(차례가 틀린 토렌트가 실제로 있다 — info-hash 는 원래
 *   바이트로 내므로 받아도 해가 없다).
 * - [decode] 는 값 하나 뒤에 남는 바이트가 있으면 거부한다. 남는 바이트가 뜻이 있는 자리(ut_metadata 의 data 메시지 — 사전 뒤에 조각
 *   바이트가 붙는다)는 [decodePrefix] 를 쓴다.
 */
object Bencode {

    class Limits(val maxBytes: Int, val maxDepth: Int, val maxTokens: Int) {
        companion object {
            /** .torrent·메타데이터. */
            val TORRENT = Limits(maxBytes = 10_000_000, maxDepth = 64, maxTokens = 3_000_000)

            /** 트래커 응답(본문은 1 MiB 로 이미 묶인다). */
            val TRACKER = Limits(maxBytes = 1_048_576, maxDepth = 16, maxTokens = 200_000)

            /** 피어 메시지 안의 사전(확장 핸드셰이크·ut_metadata). */
            val PEER = Limits(maxBytes = 64 * 1024, maxDepth = 16, maxTokens = 4_096)

            /** DHT 의 KRPC 한 데이터그램. */
            val KRPC = Limits(maxBytes = 64 * 1024, maxDepth = 8, maxTokens = 2_048)
        }
    }

    fun decode(data: ByteArray, limits: Limits = Limits.TORRENT): BValue {
        val v = decodePrefix(data, 0, data.size, limits)
        if (v.end != data.size) throw BencodeException("trailing bytes", v.end)
        return v
    }

    /** [from] 부터 값 하나를 읽는다. 값 뒤에 바이트가 남아도 된다 — 돌려준 값의 [BValue.end] 가 그 끝이다. */
    fun decodePrefix(data: ByteArray, from: Int, until: Int, limits: Limits): BValue {
        require(from in 0..until && until <= data.size)
        if (until - from > limits.maxBytes) throw BencodeException("too large (${until - from} bytes)", from)
        return Reader(data, from, until, limits).read()
    }

    private class Frame(val start: Int, val isDict: Boolean) {
        val items = ArrayList<BValue>()
        val map = LinkedHashMap<String, BValue>()
        var pendingKey: String? = null
    }

    private class Reader(val d: ByteArray, var pos: Int, val end: Int, val limits: Limits) {
        private val stack = ArrayList<Frame>()
        private var tokens = 0

        fun read(): BValue {
            while (true) {
                if (pos >= end) throw BencodeException("unexpected end", pos)
                val c = d[pos].toInt().toChar()
                val value: BValue? = when (c) {
                    'i' -> readInt()
                    'l', 'd' -> {
                        count()
                        if (stack.size >= limits.maxDepth) throw BencodeException("too deep", pos)
                        stack.add(Frame(pos, c == 'd'))
                        pos++
                        null
                    }
                    'e' -> {
                        if (stack.isEmpty()) throw BencodeException("unexpected 'e'", pos)
                        val f = stack.removeAt(stack.size - 1)
                        pos++
                        if (f.isDict) {
                            if (f.pendingKey != null) throw BencodeException("key without value", pos - 1)
                            BDict(f.map, f.start, pos)
                        } else {
                            BList(f.items, f.start, pos)
                        }
                    }
                    in '0'..'9' -> readBytes()
                    else -> throw BencodeException("bad byte 0x%02x".format(d[pos].toInt() and 0xFF), pos)
                }
                if (value == null) continue
                if (stack.isEmpty()) return value
                val top = stack[stack.size - 1]
                if (!top.isDict) {
                    top.items.add(value)
                } else {
                    val key = top.pendingKey
                    if (key == null) {
                        if (value !is BBytes) throw BencodeException("dict key is not a string", value.start)
                        val k = value.latin1()
                        if (top.map.containsKey(k)) throw BencodeException("duplicate key", value.start)
                        top.pendingKey = k
                    } else {
                        top.map[key] = value
                        top.pendingKey = null
                    }
                }
            }
        }

        private fun count() {
            if (++tokens > limits.maxTokens) throw BencodeException("too many tokens", pos)
        }

        private fun readInt(): BInt {
            count()
            val start = pos
            pos++ // 'i'
            var negative = false
            if (pos < end && d[pos] == '-'.code.toByte()) {
                negative = true
                pos++
            }
            val digitsStart = pos
            // 음수 쪽으로 쌓는다 — Long.MIN_VALUE 도 넘침 없이 읽힌다.
            var acc = 0L
            while (pos < end && d[pos] != 'e'.code.toByte()) {
                val ch = d[pos].toInt()
                if (ch < '0'.code || ch > '9'.code) throw BencodeException("bad digit in int", pos)
                if (pos - digitsStart >= 19) throw BencodeException("int too long", pos)
                val digit = ch - '0'.code
                if (acc < Long.MIN_VALUE / 10) throw BencodeException("int overflow", pos)
                acc *= 10
                if (acc < Long.MIN_VALUE + digit) throw BencodeException("int overflow", pos)
                acc -= digit
                pos++
            }
            if (pos >= end) throw BencodeException("unterminated int", start)
            val n = pos - digitsStart
            if (n == 0) throw BencodeException("empty int", start)
            if (d[digitsStart] == '0'.code.toByte() && (n > 1 || negative)) throw BencodeException("leading zero or -0", start)
            pos++ // 'e'
            val value = if (negative) acc else {
                if (acc == Long.MIN_VALUE) throw BencodeException("int overflow", start)
                -acc
            }
            return BInt(value, start, pos)
        }

        private fun readBytes(): BBytes {
            count()
            val start = pos
            var len = 0L
            while (pos < end && d[pos] != ':'.code.toByte()) {
                val ch = d[pos].toInt()
                if (ch < '0'.code || ch > '9'.code) throw BencodeException("bad string length", pos)
                if (pos - start >= 10) throw BencodeException("string length too long", pos)
                len = len * 10 + (ch - '0'.code)
                pos++
            }
            if (pos >= end) throw BencodeException("unterminated string length", start)
            if (pos - start > 1 && d[start] == '0'.code.toByte()) throw BencodeException("leading zero in length", start)
            pos++ // ':'
            // 할당하기 전에 남은 바이트와 견준다.
            if (len > end - pos) throw BencodeException("string longer than input ($len)", start)
            val bytes = d.copyOfRange(pos, pos + len.toInt())
            pos += len.toInt()
            return BBytes(bytes, start, pos)
        }
    }

    /**
     * 코틀린 값을 bencode 로 — `Long`·`Int`, `ByteArray`, `String`(UTF-8), `List`, `Map<String, *>`(키도 UTF-8 바이트로 적고 바이트
     * 차례로 정렬). 우리가 보내는 것(확장 핸드셰이크·KRPC·이어받기 자료)만 짓는다 — 남이 준 값을 다시 짓지 않는다.
     */
    fun encode(value: Any): ByteArray {
        val out = ByteArrayOutputStream()
        write(out, value, 0)
        return out.toByteArray()
    }

    private fun write(out: ByteArrayOutputStream, value: Any, depth: Int) {
        require(depth < 32) { "too deep" }
        when (value) {
            is Long -> out.writeAscii("i${value}e")
            is Int -> out.writeAscii("i${value}e")
            is ByteArray -> {
                out.writeAscii("${value.size}:")
                out.write(value)
            }
            is String -> write(out, value.toByteArray(Charsets.UTF_8), depth)
            is List<*> -> {
                out.write('l'.code)
                for (item in value) write(out, requireNotNull(item), depth + 1)
                out.write('e'.code)
            }
            is Map<*, *> -> {
                out.write('d'.code)
                val sorted = value.entries.map { (k, v) -> (k as String).toByteArray(Charsets.UTF_8) to requireNotNull(v) }
                    .sortedWith { a, b -> compareBytes(a.first, b.first) }
                for ((k, v) in sorted) {
                    write(out, k, depth + 1)
                    write(out, v, depth + 1)
                }
                out.write('e'.code)
            }
            else -> throw IllegalArgumentException("cannot bencode ${value::class.java.name}")
        }
    }

    private fun ByteArrayOutputStream.writeAscii(s: String) = write(s.toByteArray(Charsets.US_ASCII))

    fun compareBytes(a: ByteArray, b: ByteArray): Int {
        val n = minOf(a.size, b.size)
        for (i in 0 until n) {
            val c = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
            if (c != 0) return c
        }
        return a.size - b.size
    }
}
