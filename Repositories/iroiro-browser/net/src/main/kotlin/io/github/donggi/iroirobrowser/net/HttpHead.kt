package io.github.donggi.iroirobrowser.net

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * HTTP/1.x 의 머리(첫 줄 + 머리 줄들). 프록시 사이에서 오가는 것은 이것뿐이고, 몸은 해석하지 않고 흘려보낸다.
 *
 * [extra] 는 머리 끝(빈 줄) 뒤에 같이 읽힌 바이트다 — 터널의 첫 바이트일 수 있으므로 버리지 않고 먼저 넘긴다.
 *
 * [hasBareLineBreak] — 줄 안에 홀로 선 CR 이나 LF(CRLF 짝이 아닌 것)가 있다. 머리는 CRLF 로만 가르므로 그런 글자는 요청 줄이나 값 안에
 * 살아남는데, 중계는 머리를 ISO-8859-1 바이트 그대로 원격에 다시 쓴다 — 홀로 선 CR·LF 를 줄 끝으로 읽는 서버가 머리를 하나 더 보거나 요청을
 * 둘로 읽는다(머리 주입·스머글링). 중계는 그런 요청을 400 으로 거절한다([LocalRelay] — 테더의 HTTP 프록시와 같게).
 */
internal class HttpHead(
    val startLine: String,
    val headers: List<Pair<String, String>>,
    val extra: ByteArray,
    val hasBareLineBreak: Boolean = false,
) {
    fun header(name: String): String? = headers.firstOrNull { it.first.equals(name, ignoreCase = true) }?.second

    /** 응답의 상태 번호. 응답이 아니거나 읽지 못하면 -1. */
    val status: Int
        get() {
            val parts = startLine.split(' ')
            return if (parts.size >= 2 && parts[0].startsWith("HTTP/")) parts[1].toIntOrNull() ?: -1 else -1
        }

    /** 머리가 상한을 넘었다 — 머리로 메모리를 채우는 상대를 끊는다. */
    class TooLarge : IOException("HTTP head too large")

    companion object {
        /** 머리의 상한. 브라우저의 요청 머리는 쿠키를 실어도 대개 수 KB 다. */
        const val MAX_BYTES = 32 * 1024

        /**
         * 빈 줄까지 읽는다. 아무것도 읽기 전에 끝나면 null(상대가 연결을 닫았다), 머리 중간에 끝나면 [IOException],
         * 상한을 넘으면 [TooLarge].
         */
        fun read(input: InputStream): HttpHead? {
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(4096)
            while (true) {
                val n = input.read(chunk)
                if (n < 0) {
                    if (buffer.size() == 0) return null
                    throw IOException("connection closed inside an HTTP head")
                }
                buffer.write(chunk, 0, n)
                val bytes = buffer.toByteArray()
                val end = endOfHead(bytes)
                if (end >= 0) return parse(bytes, end)
                if (bytes.size > MAX_BYTES) throw TooLarge()
            }
        }

        /** 빈 줄(CRLF CRLF) 바로 뒤의 자리. 없으면 -1. */
        private fun endOfHead(b: ByteArray): Int {
            for (i in 3 until b.size) {
                if (b[i - 3] == CR && b[i - 2] == LF && b[i - 1] == CR && b[i] == LF) return i + 1
            }
            return -1
        }

        private fun parse(bytes: ByteArray, end: Int): HttpHead {
            if (end > MAX_BYTES) throw TooLarge()
            // 머리는 ISO-8859-1 로 읽는다 — 바이트를 잃지 않고 그대로 되돌릴 수 있다.
            val text = String(bytes, 0, end - 4, Charsets.ISO_8859_1)
            val lines = text.split("\r\n")
            val headers = lines.drop(1).filter { it.isNotEmpty() }.mapNotNull { line ->
                val colon = line.indexOf(':')
                if (colon <= 0) null else line.substring(0, colon).trim() to line.substring(colon + 1).trim()
            }
            return HttpHead(lines.first(), headers, bytes.copyOfRange(end, bytes.size), lines.any { it.indexOf('\r') >= 0 || it.indexOf('\n') >= 0 })
        }

        private const val CR = '\r'.code.toByte()
        private const val LF = '\n'.code.toByte()
    }
}
