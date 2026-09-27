package io.github.donggi.iroiroviewer.docview.pdf.crypt

import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * PDF 의 **값**. 복호화에 필요한 만큼만 안다 — 그리는 것은 pdfium 의 일이다.
 *
 * ## 숫자를 글자로 들고 있는 이유
 *
 * `0.1` 을 `Double` 로 읽었다가 다시 쓰면 `0.1` 이 아니라 `0.10000000000000001` 이 나올 수 있고,
 * 그러면 **우리가 건드리지 않았어야 할 값**(색·좌표·행렬)이 바뀐다. 복호화는 문자열과
 * 스트림만 바꾸는 일이므로 나머지는 **글자 그대로** 돌려놓는다.
 */
internal sealed interface PdfObj

internal object PdfNull : PdfObj

internal data class PdfBool(val value: Boolean) : PdfObj

internal class PdfNum(val text: String) : PdfObj {
    val long: Long? get() = text.toLongOrNull() ?: text.toDoubleOrNull()?.toLong()
    val int: Int? get() = long?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
}

/** 이름. `#xx` 를 푼 바이트를 latin-1 로 읽은 문자열이다(비교에만 쓴다). */
internal data class PdfName(val name: String) : PdfObj

internal class PdfStr(val bytes: ByteArray) : PdfObj

internal class PdfArray(val items: MutableList<PdfObj>) : PdfObj

internal class PdfDict(val map: LinkedHashMap<String, PdfObj>) : PdfObj {
    operator fun get(key: String): PdfObj? = map[key]
    fun name(key: String): String? = (map[key] as? PdfName)?.name
    fun int(key: String): Int? = (map[key] as? PdfNum)?.int
}

internal data class PdfRef(val num: Int, val gen: Int) : PdfObj

/** 문법이 틀렸다. 메시지에 **파일 내용을 넣지 않는다** — 화면·로그로 새는 길이 된다. */
internal class PdfSyntaxException(message: String) : IOException(message)

/**
 * 파일을 무작위로 읽는 것. 우리는 파일 전체를 메모리에 올리지 않는다 — 수백 MB PDF 가
 * 흔하고, 복호화에 필요한 것은 한 번에 객체 하나뿐이다.
 */
internal interface PdfSource {
    val size: Long

    /** [pos] 부터 최대 [len] 바이트. 읽은 수를 돌려준다. 끝이면 0 이하. */
    fun read(pos: Long, buf: ByteArray, off: Int, len: Int): Int
}

/** 시험과 작은 조각을 위한 메모리 원본. */
internal class ByteArraySource(private val data: ByteArray) : PdfSource {
    override val size: Long get() = data.size.toLong()
    override fun read(pos: Long, buf: ByteArray, off: Int, len: Int): Int {
        if (pos >= data.size) return -1
        val n = minOf(len.toLong(), data.size - pos).toInt()
        System.arraycopy(data, pos.toInt(), buf, off, n)
        return n
    }
}

/**
 * 원본 위를 앞으로 걷는 커서. 64 KiB 씩 채워 읽는다.
 *
 * **바이트 단위로 원본을 부르지 않는다** — 파일 채널에 바이트마다 `read` 를 하면 시스템 호출이
 * 수백만 번 일어난다.
 */
internal class PdfCursor(private val src: PdfSource, start: Long) {
    private val buf = ByteArray(64 * 1024)
    private var bufStart = 0L
    private var bufLen = 0

    var pos: Long = start
        private set

    fun seek(p: Long) {
        pos = p
    }

    /** 지금 자리의 바이트. 끝이면 -1. */
    fun peek(): Int {
        if (pos < bufStart || pos >= bufStart + bufLen) {
            if (pos >= src.size || pos < 0) return -1
            bufStart = pos
            bufLen = maxOf(src.read(pos, buf, 0, buf.size), 0)
            if (bufLen == 0) return -1
        }
        return buf[(pos - bufStart).toInt()].toInt() and 0xFF
    }

    fun next(): Int {
        val c = peek()
        if (c >= 0) pos++
        return c
    }

    fun skip(n: Int) {
        pos += n
    }
}

internal object PdfChars {
    fun isWhite(c: Int): Boolean = c == 0 || c == 9 || c == 10 || c == 12 || c == 13 || c == 32
    fun isDelim(c: Int): Boolean =
        c == '('.code || c == ')'.code || c == '<'.code || c == '>'.code || c == '['.code ||
            c == ']'.code || c == '{'.code || c == '}'.code || c == '/'.code || c == '%'.code
    fun isRegular(c: Int): Boolean = c >= 0 && !isWhite(c) && !isDelim(c)
}

/**
 * 토큰을 읽어 값을 만드는 것.
 *
 * ## 막는 것
 *
 * 임의의 사용자 파일이다. **깊이**([MAX_DEPTH])와 **문자열·이름 길이**를 자른다 — 배열 속
 * 배열을 수만 겹으로 쌓은 파일은 재귀를 그대로 스택 넘침으로 만들고, 닫히지 않은 문자열은
 * 파일 끝까지 먹는다.
 */
internal class PdfParser(private val cursor: PdfCursor) {

    /** 키워드(`obj`·`stream`·`R`·`xref`…)를 값처럼 돌려주기 위한 내부 표지. */
    class Keyword(val text: String) : PdfObj

    fun skipWhite() {
        while (true) {
            val c = cursor.peek()
            if (c < 0) return
            if (PdfChars.isWhite(c)) {
                cursor.skip(1)
                continue
            }
            if (c == '%'.code) {
                // 주석은 줄 끝까지.
                while (true) {
                    val d = cursor.next()
                    if (d < 0 || d == 10 || d == 13) break
                }
                continue
            }
            return
        }
    }

    /**
     * 값 하나. 참조(`12 0 R`)는 여기서 알아본다.
     *
     * @return 키워드를 만나면 [Keyword]. 파일 끝이면 null.
     */
    fun parseObject(depth: Int = 0): PdfObj? {
        if (depth > MAX_DEPTH) throw PdfSyntaxException("너무 깊이 중첩되었다")
        skipWhite()
        val c = cursor.peek()
        if (c < 0) return null
        return when (c) {
            '/'.code -> readName()
            '('.code -> readLiteral()
            '['.code -> readArray(depth)
            '<'.code -> {
                cursor.skip(1)
                if (cursor.peek() == '<'.code) {
                    cursor.skip(1)
                    readDict(depth)
                } else {
                    readHex()
                }
            }
            ']'.code, '>'.code, ')'.code, '{'.code, '}'.code -> {
                cursor.skip(1)
                Keyword(c.toChar().toString())
            }
            else -> {
                val word = readRegular()
                if (word.isEmpty()) {
                    cursor.skip(1)
                    return Keyword("")
                }
                when {
                    isNumber(word) -> maybeRef(word)
                    word == "true" -> PdfBool(true)
                    word == "false" -> PdfBool(false)
                    word == "null" -> PdfNull
                    else -> Keyword(word)
                }
            }
        }
    }

    /** `n g R` 을 알아보려고 두 토큰을 미리 본다. 아니면 되돌린다. */
    private fun maybeRef(first: String): PdfObj {
        val save = cursor.pos
        if (first.all { it.isDigit() }) {
            skipWhite()
            val second = readRegular()
            if (second.isNotEmpty() && second.all { it.isDigit() }) {
                skipWhite()
                val third = cursor.peek()
                if (third == 'R'.code) {
                    cursor.skip(1)
                    val after = cursor.peek()
                    if (!PdfChars.isRegular(after)) {
                        val num = first.toLongOrNull()
                        val gen = second.toLongOrNull()
                        if (num != null && gen != null && num <= Int.MAX_VALUE && gen <= 65535) {
                            return PdfRef(num.toInt(), gen.toInt())
                        }
                    }
                }
            }
        }
        cursor.seek(save)
        return PdfNum(first)
    }

    private fun readRegular(): String {
        val sb = StringBuilder()
        while (true) {
            val c = cursor.peek()
            if (!PdfChars.isRegular(c)) break
            sb.append(c.toChar())
            cursor.skip(1)
            if (sb.length > MAX_TOKEN) throw PdfSyntaxException("토큰이 너무 길다")
        }
        return sb.toString()
    }

    private fun isNumber(s: String): Boolean {
        if (s.isEmpty()) return false
        var i = 0
        if (s[0] == '+' || s[0] == '-') i++
        var digits = 0
        var dots = 0
        while (i < s.length) {
            val ch = s[i]
            if (ch.isDigit()) digits++ else if (ch == '.') dots++ else return false
            i++
        }
        return digits > 0 && dots <= 1
    }

    private fun readName(): PdfName {
        cursor.skip(1) // '/'
        val out = ByteArrayOutputStream()
        while (true) {
            val c = cursor.peek()
            if (!PdfChars.isRegular(c)) break
            cursor.skip(1)
            if (c == '#'.code) {
                val h1 = hexVal(cursor.peek())
                if (h1 >= 0) {
                    cursor.skip(1)
                    val h2 = hexVal(cursor.peek())
                    if (h2 >= 0) {
                        cursor.skip(1)
                        out.write(h1 * 16 + h2)
                        continue
                    }
                    out.write('#'.code)
                    out.write(hexChar(h1))
                    continue
                }
            }
            out.write(c)
            if (out.size() > MAX_TOKEN) throw PdfSyntaxException("이름이 너무 길다")
        }
        return PdfName(String(out.toByteArray(), Charsets.ISO_8859_1))
    }

    private fun readLiteral(): PdfStr {
        cursor.skip(1) // '('
        val out = ByteArrayOutputStream()
        var nest = 1
        while (true) {
            val c = cursor.next()
            if (c < 0) break // 닫히지 않은 문자열 — 있는 데까지 쓴다
            when (c) {
                '('.code -> {
                    nest++
                    out.write(c)
                }
                ')'.code -> {
                    nest--
                    if (nest == 0) break
                    out.write(c)
                }
                '\\'.code -> {
                    val e = cursor.next()
                    when (e) {
                        'n'.code -> out.write(10)
                        'r'.code -> out.write(13)
                        't'.code -> out.write(9)
                        'b'.code -> out.write(8)
                        'f'.code -> out.write(12)
                        '('.code, ')'.code, '\\'.code -> out.write(e)
                        13 -> if (cursor.peek() == 10) cursor.skip(1) // 줄 이음
                        10 -> Unit
                        in '0'.code..'7'.code -> {
                            var v = e - '0'.code
                            repeat(2) {
                                val d = cursor.peek()
                                if (d in '0'.code..'7'.code) {
                                    v = v * 8 + (d - '0'.code)
                                    cursor.skip(1)
                                }
                            }
                            out.write(v and 0xFF)
                        }
                        -1 -> Unit
                        else -> out.write(e) // 알 수 없는 탈출은 글자만 남긴다(명세)
                    }
                }
                // **줄 끝을 LF 하나로 바꾼다**(명세 7.3.4.2). 암호문 바이트가 CR 을 담고 있을 때
                // 이것을 지키지 않으면 복호화가 한 바이트씩 어긋난다.
                13 -> {
                    if (cursor.peek() == 10) cursor.skip(1)
                    out.write(10)
                }
                else -> out.write(c)
            }
            if (out.size() > MAX_STRING) throw PdfSyntaxException("문자열이 너무 길다")
        }
        return PdfStr(out.toByteArray())
    }

    private fun readHex(): PdfStr {
        val out = ByteArrayOutputStream()
        var hi = -1
        while (true) {
            val c = cursor.next()
            if (c < 0 || c == '>'.code) break
            val v = hexVal(c)
            if (v < 0) continue // 공백과 잡음은 버린다
            if (hi < 0) {
                hi = v
            } else {
                out.write(hi * 16 + v)
                hi = -1
            }
            if (out.size() > MAX_STRING) throw PdfSyntaxException("문자열이 너무 길다")
        }
        if (hi >= 0) out.write(hi * 16) // 홀수 개면 뒤에 0 을 붙인다(명세)
        return PdfStr(out.toByteArray())
    }

    private fun readArray(depth: Int): PdfArray {
        cursor.skip(1) // '['
        val items = ArrayList<PdfObj>()
        while (true) {
            val v = parseObject(depth + 1) ?: break
            if (v is Keyword) {
                if (v.text == "]") break
                continue // 배열 안의 엉뚱한 키워드는 버린다
            }
            items.add(v)
            if (items.size > MAX_ITEMS) throw PdfSyntaxException("배열이 너무 길다")
        }
        return PdfArray(items)
    }

    private fun readDict(depth: Int): PdfDict {
        val map = LinkedHashMap<String, PdfObj>()
        while (true) {
            skipWhite()
            val c = cursor.peek()
            if (c < 0) break
            if (c == '>'.code) {
                cursor.skip(1)
                if (cursor.peek() == '>'.code) cursor.skip(1)
                break
            }
            val key = parseObject(depth + 1) ?: break
            if (key !is PdfName) {
                if (key is Keyword && key.text == ">") break
                continue
            }
            val value = parseObject(depth + 1) ?: break
            if (value is Keyword) {
                // 값이 빠진 키. `>>` 가 바로 왔으면 사전을 닫는다.
                if (value.text == ">") {
                    if (cursor.peek() == '>'.code) cursor.skip(1)
                    break
                }
                continue
            }
            map[key.name] = value
            if (map.size > MAX_ITEMS) throw PdfSyntaxException("사전이 너무 크다")
        }
        return PdfDict(map)
    }

    private fun hexVal(c: Int): Int = when (c) {
        in '0'.code..'9'.code -> c - '0'.code
        in 'a'.code..'f'.code -> c - 'a'.code + 10
        in 'A'.code..'F'.code -> c - 'A'.code + 10
        else -> -1
    }

    private fun hexChar(v: Int): Int = "0123456789ABCDEF"[v].code

    companion object {
        const val MAX_DEPTH = 256
        const val MAX_TOKEN = 64 * 1024
        const val MAX_STRING = 16 * 1024 * 1024
        const val MAX_ITEMS = 4 * 1024 * 1024
    }
}

/**
 * 값을 다시 바이트로.
 *
 * **문자열은 언제나 16진으로 쓴다.** 괄호 문자열로 쓰면 복호화된 바이트 속의 괄호·역슬래시·
 * CR 을 전부 제대로 탈출시켜야 하고, 하나라도 빠뜨리면 **그 뒤의 파일 전체가 문자열로 먹힌다.**
 * 16진은 두 배로 길어지지만 틀릴 자리가 없다.
 */
internal object PdfWriter {

    fun write(obj: PdfObj, out: ByteArrayOutputStream) {
        when (obj) {
            is PdfNull -> out.writeAscii("null")
            is PdfBool -> out.writeAscii(if (obj.value) "true" else "false")
            is PdfNum -> out.writeAscii(obj.text)
            is PdfName -> writeName(obj.name, out)
            is PdfStr -> {
                out.write('<'.code)
                for (b in obj.bytes) {
                    val v = b.toInt() and 0xFF
                    out.write(HEX[v shr 4].code)
                    out.write(HEX[v and 15].code)
                }
                out.write('>'.code)
            }
            is PdfArray -> {
                out.write('['.code)
                obj.items.forEachIndexed { i, item ->
                    if (i > 0) out.write(' '.code)
                    write(item, out)
                }
                out.write(']'.code)
            }
            is PdfDict -> {
                out.writeAscii("<<")
                for ((k, v) in obj.map) {
                    writeName(k, out)
                    out.write(' '.code)
                    write(v, out)
                    out.write(' '.code)
                }
                out.writeAscii(">>")
            }
            is PdfRef -> out.writeAscii("${obj.num} ${obj.gen} R")
            is PdfParser.Keyword -> out.writeAscii(obj.text)
        }
    }

    /** 이름. 정규 글자가 아니거나 `#` 이면 `#xx` 로 쓴다(명세 7.3.5). */
    private fun writeName(name: String, out: ByteArrayOutputStream) {
        out.write('/'.code)
        for (ch in name) {
            val c = ch.code and 0xFF
            if (c < 0x21 || c > 0x7E || c == '#'.code || PdfChars.isDelim(c)) {
                out.write('#'.code)
                out.write(HEX[c shr 4].code)
                out.write(HEX[c and 15].code)
            } else {
                out.write(c)
            }
        }
    }

    private fun ByteArrayOutputStream.writeAscii(s: String) = write(s.toByteArray(Charsets.ISO_8859_1))

    private const val HEX = "0123456789ABCDEF"
}

/**
 * 파일 채널 위의 원본. **위치를 주고 읽는다**(`FileChannel.read(buf, pos)`) — 채널의 현재
 * 위치를 옮기지 않으므로 같은 채널을 여러 커서가 나눠 써도 서로 밟지 않는다.
 *
 * 채널은 부르는 쪽이 열고 닫는다.
 */
internal class FileChannelSource(private val channel: java.nio.channels.FileChannel) : PdfSource {
    override val size: Long = channel.size()

    override fun read(pos: Long, buf: ByteArray, off: Int, len: Int): Int {
        if (pos >= size) return -1
        return channel.read(java.nio.ByteBuffer.wrap(buf, off, len), pos)
    }
}
