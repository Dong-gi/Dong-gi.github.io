package io.github.donggi.iroirobrowser.torrent.fake

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

// 가짜 군집이 함께 쓰는 작은 도구들. 엔진의 코드와 공유하지 않는다.

/**
 * 127.0.0.[n] (n 은 1~254). 가짜 서버를 서로 다른 루프백 IP 에 세울 때 쓴다 — 엔진이 'IP 하나에 연결 하나'를 지키므로
 * 시더 여럿을 모두 127.0.0.1 에 세우면 그중 하나에만 붙는다.
 */
fun loopback(n: Int): InetAddress {
    require(n in 1..254) { "n 은 1~254: $n" }
    return InetAddress.getByAddress("127.0.0.$n", byteArrayOf(127, 0, 0, n.toByte()))
}

/** 가짜 서버는 루프백에만 선다 — 실수로 바깥 인터페이스에 서지 않게. */
internal fun fakeRequireLoopback(address: InetAddress): InetAddress {
    require(address.isLoopbackAddress) { "가짜 서버는 루프백 주소에만 선다: $address" }
    return address
}

/** URL 에 넣을 호스트 글자(IPv6 면 대괄호). */
internal fun fakeUrlHost(address: InetAddress): String =
    if (address is java.net.Inet6Address) "[${address.hostAddress}]" else address.hostAddress

internal fun fakeSha1(vararg parts: ByteArray): ByteArray {
    val md = MessageDigest.getInstance("SHA-1")
    for (p in parts) md.update(p)
    return md.digest()
}

private val HEX = "0123456789abcdef".toCharArray()

internal fun ByteArray.fakeHex(): String {
    val out = CharArray(size * 2)
    for (i in indices) {
        val v = this[i].toInt() and 0xFF
        out[i * 2] = HEX[v ushr 4]
        out[i * 2 + 1] = HEX[v and 0x0F]
    }
    return String(out)
}

private fun isUnreserved(b: Int): Boolean =
    b in 'A'.code..'Z'.code || b in 'a'.code..'z'.code || b in '0'.code..'9'.code ||
        b == '-'.code || b == '.'.code || b == '_'.code || b == '~'.code

/** 퍼센트 인코딩 — 예약되지 않은 글자(A-Z a-z 0-9 - . _ ~)만 그대로 두고 나머지는 %XX(대문자). */
internal fun fakePercentEncode(bytes: ByteArray): String {
    val sb = StringBuilder(bytes.size * 3)
    for (x in bytes) {
        val b = x.toInt() and 0xFF
        if (isUnreserved(b)) sb.append(b.toChar()) else sb.append('%').append(HEX[b ushr 4].uppercaseChar()).append(HEX[b and 0x0F].uppercaseChar())
    }
    return sb.toString()
}

internal fun fakePercentEncode(s: String): String = fakePercentEncode(s.toByteArray(Charsets.UTF_8))

/**
 * 퍼센트를 푼 바이트. 짝이 맞지 않는 '%' 는 글자 그대로 둔다.
 * 입력은 HTTP 머리를 ISO-8859-1 로 읽은 글자라고 본다 — U+00FF 까지의 글자는 그 바이트 하나로(인코딩하지 않고 보낸
 * 날 UTF-8 바이트도 그대로 살아난다), 그 위의 글자는 UTF-8 로 쓴다.
 * [plusAsSpace] 면 '+' 를 공백(0x20)으로 — 폼 인코딩 관례. BEP 3 은 '+' 를 %2B 로 쓰라고 하므로 날 '+' 는 엔진의 잘못을 드러낸다.
 */
internal fun fakePercentDecode(s: String, plusAsSpace: Boolean): ByteArray {
    val out = ByteArrayOutputStream(s.length)
    var i = 0
    while (i < s.length) {
        val c = s[i]
        when {
            c == '%' && i + 2 < s.length && hexVal(s[i + 1]) >= 0 && hexVal(s[i + 2]) >= 0 -> {
                out.write(hexVal(s[i + 1]) * 16 + hexVal(s[i + 2]))
                i += 3
            }
            c == '+' && plusAsSpace -> {
                out.write(0x20)
                i++
            }
            c.code <= 0xFF -> {
                out.write(c.code)
                i++
            }
            else -> {
                val cp = s.codePointAt(i)
                out.write(String(Character.toChars(cp)).toByteArray(Charsets.UTF_8))
                i += Character.charCount(cp)
            }
        }
    }
    return out.toByteArray()
}

private fun hexVal(c: Char): Int = when (c) {
    in '0'..'9' -> c - '0'
    in 'a'..'f' -> c - 'a' + 10
    in 'A'..'F' -> c - 'A' + 10
    else -> -1
}

/** 쿼리 문자열 → (이름 → 값 바이트들). 이름도 퍼센트를 풀어 ISO-8859-1 로 읽는다. */
internal fun fakeParseQuery(raw: String): Map<String, List<ByteArray>> {
    val out = LinkedHashMap<String, MutableList<ByteArray>>()
    if (raw.isEmpty()) return out
    for (part in raw.split('&')) {
        if (part.isEmpty()) continue
        val eq = part.indexOf('=')
        val name = String(fakePercentDecode(if (eq < 0) part else part.substring(0, eq), true), Charsets.ISO_8859_1)
        val value = if (eq < 0) ByteArray(0) else fakePercentDecode(part.substring(eq + 1), true)
        out.getOrPut(name) { ArrayList() }.add(value)
    }
    return out
}

/** IPv4 주소 + 포트 → 6바이트(BEP 23 꼴). IPv4 가 아니면 null. */
internal fun fakeCompact4(a: InetSocketAddress): ByteArray? {
    val ip = a.address?.address ?: return null
    if (ip.size != 4) return null
    return ByteBuffer.allocate(6).put(ip).putShort(a.port.toShort()).array()
}

/** IPv6 주소 + 포트 → 18바이트(BEP 7 꼴). IPv6 가 아니면 null. */
internal fun fakeCompact6(a: InetSocketAddress): ByteArray? {
    val ip = a.address?.address ?: return null
    if (ip.size != 16) return null
    return ByteBuffer.allocate(18).put(ip).putShort(a.port.toShort()).array()
}

internal fun Closeable.fakeCloseQuietly() {
    try {
        close()
    } catch (_: Exception) {
    }
}

/** 읽기·쓰기 중에 소켓이 닫히거나 끊겨서 나는 흔한 예외인가. */
internal fun Throwable.fakeIsDisconnect(): Boolean =
    this is IOException || this is InterruptedException || this is InterruptedIOException

/**
 * 가짜 서버의 스레드에서 난, 예상하지 못한 예외(입출력·끊김·끼어들기 말고). 가짜 자신의 버그이거나 가짜가 다루지 못한 입력이다.
 * 끊긴 연결 뒤에 숨지 않도록 여기 모으고 stderr 에도 찍는다. 통합 시험의 끝에서 비었는지 보면 된다.
 */
object FakeDiagnostics {
    private val lock = Any()
    private val failures = ArrayList<Throwable>()

    val unexpectedErrors: List<Throwable> get() = synchronized(lock) { failures.toList() }

    fun clear() = synchronized(lock) { failures.clear() }

    internal fun report(threadName: String, e: Throwable) {
        synchronized(lock) { failures += e }
        System.err.println("[$threadName] 가짜 서버의 예상하지 못한 예외:")
        e.printStackTrace()
    }
}

/** 스레드 장부. 모든 스레드는 데몬이다. [closeAndJoin] 뒤로는 새 스레드를 띄우지 않는다. */
internal class FakeThreads(private val prefix: String) {
    private val lock = Any()
    private val threads = ArrayList<Thread>()
    private var closed = false

    /** 띄웠으면 true. 이미 닫혔으면 false. */
    fun start(name: String, body: () -> Unit): Boolean {
        val threadName = "$prefix-$name"
        val t = Thread({
            try {
                body()
            } catch (e: Throwable) {
                if (!e.fakeIsDisconnect()) FakeDiagnostics.report(threadName, e)
            }
        }, threadName)
        t.isDaemon = true
        synchronized(lock) {
            if (closed) return false
            threads.removeAll { it.state == Thread.State.TERMINATED }
            threads += t
            t.start()
        }
        return true
    }

    /** 닫고, 돌 때마다 [eachRound](소켓 닫기 등)를 부르고 끼어들기를 보내며 모두 끝나기를 기다린다. */
    fun closeAndJoin(timeoutMs: Long = 5_000, eachRound: () -> Unit = {}) {
        synchronized(lock) { closed = true }
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        val self = Thread.currentThread()
        while (true) {
            eachRound()
            val alive = synchronized(lock) { threads.filter { it.isAlive && it !== self } }
            if (alive.isEmpty()) return
            for (t in alive) t.interrupt()
            val left = (deadline - System.nanoTime()) / 1_000_000
            if (left <= 0) {
                System.err.println("[$prefix] 닫은 뒤에도 남은 스레드: ${alive.map { it.name }}")
                return
            }
            alive.first().join(minOf(left, 50L))
        }
    }
}

/**
 * [bindAddress](루프백)의 빈 포트에서 TCP 를 받는다. 연결마다 데몬 스레드 하나가 [handler] 를 돌리고, 끝나면 소켓을 닫는다.
 * [close] 가 받기를 멈추고 모든 소켓을 닫고 스레드가 끝나기를 기다린다.
 */
internal class FakeTcpListener(label: String, bindAddress: InetAddress, private val handler: (Socket) -> Unit) : Closeable {
    val threads = FakeThreads(label)
    private val server = ServerSocket(0, 128, fakeRequireLoopback(bindAddress))
    private val sockets: MutableSet<Socket> = ConcurrentHashMap.newKeySet()

    @Volatile
    private var closed = false

    val port: Int = server.localPort

    init {
        threads.start("accept") { acceptLoop() }
    }

    private fun acceptLoop() {
        while (!closed) {
            val s = try {
                server.accept()
            } catch (_: IOException) {
                return
            }
            sockets += s
            val started = threads.start("conn-${s.port}") {
                try {
                    handler(s)
                } finally {
                    s.fakeCloseQuietly()
                    sockets -= s
                }
            }
            if (!started || closed) {
                s.fakeCloseQuietly()
                sockets -= s
            }
        }
    }

    override fun close() {
        closed = true
        server.fakeCloseQuietly()
        threads.closeAndJoin { sockets.forEach { it.fakeCloseQuietly() } }
        sockets.forEach { it.fakeCloseQuietly() }
    }
}

/**
 * 받은 HTTP 요청의 머리. 헤더 이름은 소문자, 같은 이름이 여럿이면 ", " 로 잇는다.
 * [target] 은 보낸 그대로, [originForm] 은 절대 꼴(http://호스트/경로)이면 경로부터만 뗀 것(RFC 9112 — 서버는 둘 다 받아야 한다).
 */
internal class FakeHttpRequest(val method: String, val target: String, val version: String, val headers: Map<String, String>) {
    val originForm: String = run {
        val schemeEnd = target.indexOf("://")
        if (schemeEnd <= 0 || target.startsWith("/")) return@run target
        var i = schemeEnd + 3
        while (i < target.length && target[i] != '/' && target[i] != '?') i++
        when {
            i >= target.length -> "/"
            target[i] == '?' -> "/" + target.substring(i)
            else -> target.substring(i)
        }
    }
    val rawPath: String get() = originForm.substringBefore('?')
    val rawQuery: String get() = if ('?' in originForm) originForm.substringAfter('?') else ""
}

/** 머리가 너무 크거나 요청 줄이 깨졌다. */
internal class FakeBadRequest(message: String) : IOException(message)

/**
 * 요청 머리를 빈 줄까지 읽는다(64 KiB 한도). 머리가 오기 전에 끊기면 null.
 * 몸은 읽지 않는다 — 가짜들은 GET·HEAD 만 다룬다.
 */
internal fun fakeReadHttpRequest(input: InputStream): FakeHttpRequest? {
    val buf = ByteArrayOutputStream()
    var last4 = 0
    while (true) {
        val c = input.read()
        if (c < 0) return null
        buf.write(c)
        last4 = (last4 shl 8) or c
        if (buf.size() > 65_536) throw FakeBadRequest("요청 머리가 너무 크다")
        if (last4 == 0x0D0A0D0A || (last4 and 0xFFFF) == 0x0A0A) break
    }
    val lines = String(buf.toByteArray(), Charsets.ISO_8859_1).split('\n').map { it.trimEnd('\r') }
    val requestLine = lines.first().split(' ')
    if (requestLine.size != 3) throw FakeBadRequest("요청 줄이 깨졌다: ${lines.first()}")
    val headers = LinkedHashMap<String, String>()
    for (line in lines.drop(1)) {
        if (line.isEmpty()) continue
        val colon = line.indexOf(':')
        if (colon <= 0) continue
        val name = line.substring(0, colon).trim().lowercase()
        val value = line.substring(colon + 1).trim()
        headers[name] = headers[name]?.let { "$it, $value" } ?: value
    }
    return FakeHttpRequest(requestLine[0], requestLine[1], requestLine[2], headers)
}

internal fun fakeReasonPhrase(status: Int): String = when (status) {
    200 -> "OK"
    206 -> "Partial Content"
    301 -> "Moved Permanently"
    302 -> "Found"
    307 -> "Temporary Redirect"
    400 -> "Bad Request"
    403 -> "Forbidden"
    404 -> "Not Found"
    405 -> "Method Not Allowed"
    416 -> "Range Not Satisfiable"
    500 -> "Internal Server Error"
    502 -> "Bad Gateway"
    503 -> "Service Unavailable"
    else -> "Status"
}

/** 상태 줄 + 헤더 + 빈 줄. */
internal fun fakeHttpHead(status: Int, headers: List<Pair<String, String>>): ByteArray {
    val sb = StringBuilder("HTTP/1.1 ").append(status).append(' ').append(fakeReasonPhrase(status)).append("\r\n")
    for ((k, v) in headers) sb.append(k).append(": ").append(v).append("\r\n")
    sb.append("\r\n")
    return sb.toString().toByteArray(Charsets.ISO_8859_1)
}

/** RC4 흐름 하나(JDK 의 ARCFOUR). 만들 때 키 흐름의 앞 1024 바이트를 버린다(MSE). */
internal class FakeRc4(key: ByteArray) {
    private val cipher: Cipher = Cipher.getInstance("ARCFOUR").apply {
        init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "ARCFOUR"))
    }

    init {
        cipher.update(ByteArray(1024))
    }

    fun crypt(data: ByteArray): ByteArray = if (data.isEmpty()) data else cipher.update(data)

    fun cryptInPlace(data: ByteArray, off: Int, len: Int) {
        if (len > 0) cipher.update(data, off, len, data, off)
    }
}
