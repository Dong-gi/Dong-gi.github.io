package io.github.donggi.iroirobrowser.torrent.net

import io.github.donggi.iroirobrowser.core.IpLiterals
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.IDN
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import java.util.zip.GZIPInputStream
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** http·https 주소를 엔진이 쓰는 만큼만 읽은 것. */
class HttpUrl(val scheme: String, val host: String, val port: Int, val target: String) {
    val isHttps: Boolean get() = scheme == "https"
    val isIpLiteral: Boolean get() = IpLiterals.isIpv4(host) || IpLiterals.isIpv6(host)

    /** `Host:` 머리의 값. */
    val hostHeader: String
        get() {
            val h = if (IpLiterals.isIpv6(host)) "[$host]" else host
            return if (port == defaultPort(scheme)) h else "$h:$port"
        }

    override fun toString(): String = "$scheme://$hostHeader$target"

    companion object {
        private fun defaultPort(scheme: String) = if (scheme == "https") 443 else 80

        /**
         * http·https 만. 사용자 정보(`user@`)는 받지 않는다. 이름은 국제화 도메인이면 퓨니코드로, 소문자로. 경로·질의의 ASCII 밖 글자와
         * 빈칸은 UTF-8 퍼센트 꼴로 바꾸고 제어 문자가 있으면 거부한다(요청 줄을 깨뜨리지 못하게). `#` 뒤는 버린다.
         */
        fun parse(url: String): HttpUrl? {
            val sep = url.indexOf("://")
            if (sep <= 0) return null
            val scheme = url.substring(0, sep).lowercase(Locale.ROOT)
            if (scheme != "http" && scheme != "https") return null
            val rest = url.substring(sep + 3)
            var authEnd = rest.length
            for ((i, c) in rest.withIndex()) if (c == '/' || c == '?' || c == '#') {
                authEnd = i
                break
            }
            val authority = rest.substring(0, authEnd)
            if (authority.isEmpty() || '@' in authority) return null
            var host: String
            var port = defaultPort(scheme)
            if (authority.startsWith("[")) {
                val close = authority.indexOf(']')
                if (close < 0) return null
                host = authority.substring(1, close)
                if (!IpLiterals.isIpv6(host)) return null
                val after = authority.substring(close + 1)
                if (after.isNotEmpty()) {
                    if (!after.startsWith(":")) return null
                    port = parsePort(after.substring(1)) ?: return null
                }
            } else {
                val colon = authority.lastIndexOf(':')
                if (colon >= 0) {
                    port = parsePort(authority.substring(colon + 1)) ?: return null
                    host = authority.substring(0, colon)
                } else {
                    host = authority
                }
                if (host.isEmpty() || ':' in host) return null
                host = try {
                    IDN.toASCII(host, IDN.ALLOW_UNASSIGNED).lowercase(Locale.ROOT)
                } catch (_: IllegalArgumentException) {
                    return null
                }
                if (host.isEmpty() || host.any { !(it.isLetterOrDigit() || it == '-' || it == '.' || it == '_') }) return null
            }
            var target = rest.substring(authEnd).substringBefore('#')
            if (target.isEmpty() || target.startsWith("?")) target = "/$target"
            val clean = cleanTarget(target) ?: return null
            return HttpUrl(scheme, host.lowercase(Locale.ROOT), port, clean)
        }

        private fun parsePort(s: String): Int? {
            if (s.isEmpty() || s.length > 5 || !s.all(Char::isDigit)) return null
            return s.toInt().takeIf { it in 1..65535 }
        }

        private fun cleanTarget(t: String): String? {
            val sb = StringBuilder(t.length)
            var i = 0
            while (i < t.length) {
                val cp = t.codePointAt(i)
                i += Character.charCount(cp)
                when {
                    cp < 0x20 || cp == 0x7F -> return null
                    cp == ' '.code -> sb.append("%20")
                    cp >= 0x80 -> for (b in String(Character.toChars(cp)).toByteArray(Charsets.UTF_8)) {
                        sb.append('%').append("%02X".format(b.toInt() and 0xFF))
                    }
                    else -> sb.append(cp.toChar())
                }
            }
            return sb.toString()
        }

        /** 넘겨주기의 `Location` 을 [base] 에 대어 푼다. */
        fun resolve(base: HttpUrl, location: String): HttpUrl? {
            val loc = location.trim()
            if (loc.isEmpty()) return null
            if (loc.indexOf("://") in 1..10) return parse(loc)
            if (loc.startsWith("//")) return parse("${base.scheme}:$loc")
            val origin = "${base.scheme}://${base.hostHeader}"
            if (loc.startsWith("/")) return parse(origin + loc)
            val path = base.target.substringBefore('?')
            val dir = path.substring(0, path.lastIndexOf('/') + 1)
            return parse(origin + dir + loc)
        }
    }
}

/** HTTP 일의 상한. [maxBodyBytes] 는 gzip 을 푼 뒤의 바이트로 잰다. */
class HttpLimits(
    val maxBodyBytes: Long,
    val connectTimeoutMs: Int = 10_000,
    val idleTimeoutMs: Int = 15_000,
    val totalTimeoutMs: Long = 30_000,
    val maxRedirects: Int = 3,
    val maxHeaderBytes: Int = 32 * 1024,
)

class HttpException(val kind: Kind, message: String) : IOException("$kind: $message") {
    enum class Kind { BAD_URL, BLOCKED_ADDRESS, RESOLVE, CONNECT, TLS, TIMEOUT, PROTOCOL, TOO_LARGE, TOO_MANY_REDIRECTS, CANCELLED }
}

/** 열린 응답 — [body] 를 다 읽거나 닫는다. 본문은 상한·시간 상한이 걸린 흐름이다. */
class HttpResponse internal constructor(
    val url: HttpUrl,
    val status: Int,
    private val headers: Map<String, List<String>>,
    val body: InputStream,
    private val socket: Socket,
) : Closeable {
    fun header(name: String): String? = headers[name.lowercase(Locale.ROOT)]?.firstOrNull()

    override fun close() {
        runCatching { socket.close() }
    }
}

/**
 * 엔진의 HTTP(트래커·웹 시드) — `HttpURLConnection` 을 쓰지 않고 [Transport] 의 소켓 위에서 GET 하나만 짓는다. 까닭:
 *
 * - **붙는 주소를 엔진이 고른다.** 이름을 [resolver] 로 풀고 [filter] 로 거른 주소에만 붙는다 — 플랫폼 HTTP 는 이름을 스스로 다시 풀어
 *   확인한 주소와 다른 주소(DNS 재바인딩 — 사설 주소)로 붙을 수 있다. **넘겨주기는 3번까지, http·https 만, 홉마다 다시 풀고 거른다.**
 * - **프록시를 타지 않는다**([Transport.connectBlocking] 은 `Proxy.NO_PROXY`) — 플랫폼 HTTP 는 시스템의 Wi-Fi 프록시를 따른다.
 * - 상한: 본문(`Content-Length` 가 넘으면 읽기 전에, 아니면 읽으며), 머리 32 KiB, **전체 시간**(한 바이트씩 흘리는 서버도 끊긴다).
 *   `Accept-Encoding: identity` 를 보내고, 서버가 그래도 gzip 으로 주면 **푼 바이트로 상한을 잰다**(gzip 폭탄). 다른 인코딩은 거부.
 *
 * 시간은 실제 시간이다(소켓의 시간 초과라서) — 엔진의 가상 시계와 따로다.
 */
class HttpGet(
    private val transport: Transport,
    private val resolver: Resolver,
    private val filter: AddressFilter,
    private val tls: SSLSocketFactory?,
    private val hostnameVerifier: HostnameVerifier?,
    private val userAgent: String,
) {

    fun open(
        url: String,
        purpose: Purpose,
        limits: HttpLimits,
        extraHeaders: Map<String, String> = emptyMap(),
        cancelled: () -> Boolean = { false },
    ): HttpResponse {
        var current = HttpUrl.parse(url) ?: throw HttpException(HttpException.Kind.BAD_URL, "bad url")
        val deadline = System.nanoTime() / 1_000_000 + limits.totalTimeoutMs
        var hops = 0
        while (true) {
            if (cancelled()) throw HttpException(HttpException.Kind.CANCELLED, "cancelled")
            val socket = connect(current, purpose, limits, deadline)
            try {
                val input = DeadlineInput(socket, deadline, limits.idleTimeoutMs, cancelled)
                val out = socket.getOutputStream()
                out.write(requestBytes(current, extraHeaders))
                out.flush()
                val head = readHead(input, limits.maxHeaderBytes)
                if (head.status in REDIRECTS) {
                    val location = head.headers["location"]?.firstOrNull()
                    if (location != null) {
                        socket.close()
                        if (++hops > limits.maxRedirects) throw HttpException(HttpException.Kind.TOO_MANY_REDIRECTS, "> ${limits.maxRedirects}")
                        current = HttpUrl.resolve(current, location) ?: throw HttpException(HttpException.Kind.BAD_URL, "bad redirect")
                        continue
                    }
                }
                val body = bodyStream(head, input, limits.maxBodyBytes)
                return HttpResponse(current, head.status, head.headers, body, socket)
            } catch (e: Exception) {
                runCatching { socket.close() }
                throw e
            }
        }
    }

    /** 본문을 모두 읽는다(상한 안에서). */
    fun fetch(url: String, purpose: Purpose, limits: HttpLimits, extraHeaders: Map<String, String> = emptyMap()): Pair<Int, ByteArray> {
        open(url, purpose, limits, extraHeaders).use { r ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            while (true) {
                val n = r.body.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
            }
            return r.status to out.toByteArray()
        }
    }

    private fun connect(url: HttpUrl, purpose: Purpose, limits: HttpLimits, deadline: Long): Socket {
        val candidates: List<InetAddress> = try {
            if (url.isIpLiteral) listOf(InetAddress.getByName(url.host)) else resolver.resolve(url.host)
        } catch (e: UnknownHostException) {
            throw HttpException(HttpException.Kind.RESOLVE, url.host)
        } catch (e: SecurityException) {
            throw HttpException(HttpException.Kind.RESOLVE, url.host)
        }
        val allowed = candidates.filter { filter.allows(it, url.port, purpose) }
        if (allowed.isEmpty()) throw HttpException(HttpException.Kind.BLOCKED_ADDRESS, url.host)
        var last: Exception? = null
        for (address in allowed.take(3)) {
            val remaining = deadline - System.nanoTime() / 1_000_000
            if (remaining <= 0) throw HttpException(HttpException.Kind.TIMEOUT, "connect")
            val plain = try {
                transport.connectBlocking(InetSocketAddress(address, url.port), minOf(limits.connectTimeoutMs.toLong(), remaining).toInt())
            } catch (e: IOException) {
                last = e
                continue
            }
            if (!url.isHttps) return plain
            return try {
                wrapTls(plain, url, limits, deadline)
            } catch (e: Exception) {
                runCatching { plain.close() }
                throw e
            }
        }
        throw HttpException(HttpException.Kind.CONNECT, last?.message ?: "connect failed")
    }

    private fun wrapTls(plain: Socket, url: HttpUrl, limits: HttpLimits, deadline: Long): Socket {
        val factory = tls ?: throw HttpException(HttpException.Kind.TLS, "no TLS")
        val ssl = try {
            factory.createSocket(plain, url.host, url.port, true) as SSLSocket
        } catch (e: IOException) {
            throw HttpException(HttpException.Kind.TLS, e.message ?: "tls")
        }
        try {
            val params = ssl.sslParameters
            params.endpointIdentificationAlgorithm = "HTTPS"
            if (!url.isIpLiteral) params.serverNames = listOf(SNIHostName(url.host))
            ssl.sslParameters = params
            val remaining = deadline - System.nanoTime() / 1_000_000
            if (remaining <= 0) throw HttpException(HttpException.Kind.TIMEOUT, "tls")
            ssl.soTimeout = minOf(limits.idleTimeoutMs.toLong(), remaining).toInt()
            ssl.startHandshake()
            val verifier = hostnameVerifier
            if (verifier != null && !verifier.verify(url.host, ssl.session)) throw HttpException(HttpException.Kind.TLS, "hostname")
        } catch (e: HttpException) {
            runCatching { ssl.close() }
            throw e
        } catch (e: SocketTimeoutException) {
            runCatching { ssl.close() }
            throw HttpException(HttpException.Kind.TIMEOUT, "tls")
        } catch (e: IOException) {
            runCatching { ssl.close() }
            throw HttpException(HttpException.Kind.TLS, e.message ?: "tls")
        }
        return ssl
    }

    private fun requestBytes(url: HttpUrl, extra: Map<String, String>): ByteArray {
        val sb = StringBuilder()
        sb.append("GET ").append(url.target).append(" HTTP/1.1\r\n")
        sb.append("Host: ").append(url.hostHeader).append("\r\n")
        sb.append("User-Agent: ").append(userAgent).append("\r\n")
        sb.append("Accept: */*\r\n")
        sb.append("Accept-Encoding: identity\r\n")
        sb.append("Connection: close\r\n")
        for ((k, v) in extra) {
            require(k.none { it.code < 0x21 || it == ':' } && v.none { it.code < 0x20 }) { "bad header" }
            sb.append(k).append(": ").append(v).append("\r\n")
        }
        sb.append("\r\n")
        return sb.toString().toByteArray(Charsets.ISO_8859_1)
    }

    private class Head(val status: Int, val headers: Map<String, List<String>>)

    private fun readHead(input: InputStream, maxBytes: Int): Head {
        var used = 0
        fun line(): String {
            val sb = StringBuilder()
            while (true) {
                val b = input.read()
                if (b < 0) throw HttpException(HttpException.Kind.PROTOCOL, "eof in head")
                if (++used > maxBytes) throw HttpException(HttpException.Kind.TOO_LARGE, "head")
                if (b == '\n'.code) break
                sb.append(b.toChar())
            }
            // StringBuilder.setLength 는 안드로이드의 표에 없다(숨은 부모의 것) — 글로 만든 뒤 자른다.
            return sb.toString().removeSuffix("\r")
        }
        val statusLine = line()
        val parts = statusLine.split(' ', limit = 3)
        if (parts.size < 2 || !parts[0].startsWith("HTTP/1.")) throw HttpException(HttpException.Kind.PROTOCOL, "status line")
        val status = parts[1].toIntOrNull()?.takeIf { it in 100..599 } ?: throw HttpException(HttpException.Kind.PROTOCOL, "status")
        val headers = LinkedHashMap<String, MutableList<String>>()
        var lastName: String? = null
        var count = 0
        while (true) {
            val l = line()
            if (l.isEmpty()) break
            if (++count > 100) throw HttpException(HttpException.Kind.TOO_LARGE, "headers")
            if ((l[0] == ' ' || l[0] == '\t') && lastName != null) {
                val list = headers.getValue(lastName)
                list[list.size - 1] = list[list.size - 1] + " " + l.trim()
                continue
            }
            val colon = l.indexOf(':')
            if (colon <= 0) throw HttpException(HttpException.Kind.PROTOCOL, "header")
            val name = l.substring(0, colon).trim().lowercase(Locale.ROOT)
            headers.getOrPut(name) { ArrayList() }.add(l.substring(colon + 1).trim())
            lastName = name
        }
        if (status in 100..199) return readHead(input, maxBytes - used) // 100 Continue 등 — 다음 머리
        return Head(status, headers)
    }

    private fun bodyStream(head: Head, input: InputStream, maxBody: Long): InputStream {
        // InputStream.nullInputStream() 은 안드로이드 API 33 부터다.
        if (head.status == 204 || head.status == 304) return java.io.ByteArrayInputStream(ByteArray(0))
        val te = head.headers["transfer-encoding"]?.joinToString(",")?.lowercase(Locale.ROOT)?.trim()
        val raw: InputStream = when {
            te != null && te.isNotEmpty() -> {
                if (te.split(',').map { it.trim() }.last() != "chunked") throw HttpException(HttpException.Kind.PROTOCOL, "transfer-encoding $te")
                ChunkedInput(input)
            }
            head.headers["content-length"] != null -> {
                val values = head.headers.getValue("content-length").map { it.trim() }.distinct()
                if (values.size != 1) throw HttpException(HttpException.Kind.PROTOCOL, "content-length")
                val length = values[0].toLongOrNull()?.takeIf { it >= 0 } ?: throw HttpException(HttpException.Kind.PROTOCOL, "content-length")
                LengthInput(input, length)
            }
            else -> input
        }
        val ce = head.headers["content-encoding"]?.joinToString(",")?.lowercase(Locale.ROOT)?.trim()
        val decoded = when (ce) {
            null, "", "identity" -> {
                if (raw is LengthInput && raw.length > maxBody) throw HttpException(HttpException.Kind.TOO_LARGE, "content-length ${raw.length}")
                raw
            }
            "gzip", "x-gzip" -> try {
                GZIPInputStream(raw)
            } catch (e: IOException) {
                if (e is HttpException) throw e
                throw HttpException(HttpException.Kind.PROTOCOL, "gzip")
            }
            else -> throw HttpException(HttpException.Kind.PROTOCOL, "content-encoding $ce")
        }
        return BoundedInput(decoded, maxBody)
    }

    /** 소켓 읽기 — 읽을 때마다 남은 전체 시간과 쉬는 시간 상한을 다시 건다. */
    private class DeadlineInput(
        private val socket: Socket,
        private val deadline: Long,
        private val idleMs: Int,
        private val cancelled: () -> Boolean,
    ) : InputStream() {
        private val inner = socket.getInputStream()
        private val one = ByteArray(1)

        override fun read(): Int = if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (cancelled()) throw HttpException(HttpException.Kind.CANCELLED, "cancelled")
            val remaining = deadline - System.nanoTime() / 1_000_000
            if (remaining <= 0) throw HttpException(HttpException.Kind.TIMEOUT, "total")
            socket.soTimeout = minOf(idleMs.toLong(), remaining).toInt().coerceAtLeast(1)
            return try {
                inner.read(b, off, len)
            } catch (e: SocketTimeoutException) {
                throw HttpException(HttpException.Kind.TIMEOUT, "read")
            }
        }
    }

    private class LengthInput(private val inner: InputStream, val length: Long) : InputStream() {
        private var left = length
        override fun read(): Int {
            val b = ByteArray(1)
            return if (read(b, 0, 1) < 0) -1 else b[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (left <= 0) return -1
            val n = inner.read(b, off, minOf(len.toLong(), left).toInt())
            if (n < 0) throw HttpException(HttpException.Kind.PROTOCOL, "truncated body")
            left -= n
            return n
        }
    }

    private class ChunkedInput(private val inner: InputStream) : InputStream() {
        private var left = 0L
        private var done = false
        private var started = false

        override fun read(): Int {
            val b = ByteArray(1)
            return if (read(b, 0, 1) < 0) -1 else b[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (done) return -1
            if (left == 0L) {
                if (started) crlf()
                started = true
                val sizeLine = line(256).substringBefore(';').trim()
                if (sizeLine.isEmpty() || sizeLine.length > 15) throw HttpException(HttpException.Kind.PROTOCOL, "chunk size")
                left = sizeLine.toLongOrNull(16) ?: throw HttpException(HttpException.Kind.PROTOCOL, "chunk size")
                if (left == 0L) {
                    var trailerBytes = 0
                    while (true) {
                        val t = line(8192)
                        trailerBytes += t.length
                        if (trailerBytes > 8192) throw HttpException(HttpException.Kind.TOO_LARGE, "trailer")
                        if (t.isEmpty()) break
                    }
                    done = true
                    return -1
                }
            }
            val n = inner.read(b, off, minOf(len.toLong(), left).toInt())
            if (n < 0) throw HttpException(HttpException.Kind.PROTOCOL, "truncated chunk")
            left -= n
            return n
        }

        private fun crlf() {
            val l = line(2)
            if (l.isNotEmpty()) throw HttpException(HttpException.Kind.PROTOCOL, "chunk end")
        }

        private fun line(max: Int): String {
            val sb = StringBuilder()
            while (true) {
                val c = inner.read()
                if (c < 0) throw HttpException(HttpException.Kind.PROTOCOL, "eof in chunk")
                if (c == '\n'.code) break
                if (sb.length > max) throw HttpException(HttpException.Kind.PROTOCOL, "chunk line")
                sb.append(c.toChar())
            }
            // StringBuilder.setLength 는 안드로이드의 표에 없다(숨은 부모의 것) — 글로 만든 뒤 자른다.
            return sb.toString().removeSuffix("\r")
        }
    }

    /** 푼 뒤의 바이트로 상한을 잰다 — 넘으면 [HttpException.Kind.TOO_LARGE]. */
    private class BoundedInput(private val inner: InputStream, private val max: Long) : InputStream() {
        private var count = 0L
        override fun read(): Int {
            val b = ByteArray(1)
            return if (read(b, 0, 1) < 0) -1 else b[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            // 상한까지 읽고, 상한에 닿았으면 한 바이트 더 있는지 본다.
            val room = max - count
            val n = if (room <= 0) {
                val probe = inner.read()
                if (probe < 0) return -1
                throw HttpException(HttpException.Kind.TOO_LARGE, "body > $max")
            } else {
                try {
                    inner.read(b, off, minOf(len.toLong(), room).toInt())
                } catch (e: java.util.zip.ZipException) {
                    throw HttpException(HttpException.Kind.PROTOCOL, "gzip")
                } catch (e: java.io.EOFException) {
                    throw HttpException(HttpException.Kind.PROTOCOL, "truncated")
                }
            }
            if (n > 0) count += n
            return n
        }

        override fun close() = inner.close()
    }

    /** 경로·질의에 넣을 바이트를 퍼센트 꼴로(RFC 3986 의 비예약 글자만 그대로). */
    object Percent {
        fun encode(bytes: ByteArray): String {
            val sb = StringBuilder(bytes.size * 3)
            for (b in bytes) {
                val c = b.toInt() and 0xFF
                if ((c in 'A'.code..'Z'.code) || (c in 'a'.code..'z'.code) || (c in '0'.code..'9'.code) || c == '-'.code || c == '.'.code ||
                    c == '_'.code || c == '~'.code
                ) {
                    sb.append(c.toChar())
                } else {
                    sb.append('%').append(HEX[c shr 4]).append(HEX[c and 0xF])
                }
            }
            return sb.toString()
        }

        fun encode(text: String): String = encode(text.toByteArray(Charsets.UTF_8))

        private const val HEX = "0123456789ABCDEF"
    }

    companion object {
        private val REDIRECTS = setOf(301, 302, 303, 307, 308)
    }
}
