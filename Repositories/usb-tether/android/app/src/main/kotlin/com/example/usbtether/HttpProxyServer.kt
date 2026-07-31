package com.example.usbtether

import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * HTTP 프록시 서버. `0.0.0.0` 에 바인딩한다. [BASE_PORT], [BASE_PORT]+1, …,
 * [BASE_PORT]+9 를 순서대로 최대 10회 시도해 비어 있는 첫 포트를 잡는다
 * (예: NetShare 가 이미 8282 를 점유한 경우). [start] 가 반환된 뒤
 * [actualPort] 를 읽으면 실제로 채택된 포트를 알 수 있다.
 * 시스템 네트워크별 프록시 설정을 사용하는 Wi-Fi P2P 클라이언트용이다
 * (Android 의 그 설정은 HTTP 만 받고 SOCKS5 는 받지 않는다).
 *
 * 지원 형식:
 *   - CONNECT host:port HTTP/1.1   → 불투명 TCP 터널 (HTTPS, WebSocket 등)
 *   - GET/POST/… http://host[:port]/path HTTP/1.1
 *       → 절대 URI 를 벗겨 origin-form 요청으로 상위에 전달
 *
 * 인증은 없다. 외부로 나가는 소켓은 Android OS 의 TCP 스택이 열기 때문에,
 * 통신사가 보는 것은 평범한 폰 발신 트래픽뿐이다.
 *
 * 리스닝 소켓은 **듀얼스택 와일드카드**다. IPv6 가 있는 JVM 에서 `0.0.0.0` 은 `::` 가
 * 되므로 포트가 모든 인터페이스에서 열린다. 그래서 accept 시점에 [PeerFilter] 로
 * 접근을 통제한다 — 루프백(USB)과 192.168.49.0/24(Wi-Fi Direct)만 서비스한다.
 * 전체 근거와 남는 빈틈은 PeerFilter 의 KDoc 을 볼 것.
 */
class HttpProxyServer(
    private val onTcpCount: (Int) -> Unit = {},
    private val onBytesIn: (Long) -> Unit = {},
    private val onBytesOut: (Long) -> Unit = {},
) {
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newCachedThreadPool()
    private val sessions = AtomicInteger(0)

    /** 실제로 바인딩된 포트. 서버가 동작 중이 아니면 -1. */
    @Volatile var actualPort: Int = -1
        private set

    /** 호출자가 채택된 포트를 즉시 볼 수 있도록 동기적으로 바인딩한 뒤 accept 를 시작한다. */
    fun start(): Int {
        if (!running.compareAndSet(false, true)) return actualPort
        val sock = bindWithFallback() ?: run {
            running.set(false)
            return -1
        }
        serverSocket = sock
        actualPort = sock.localPort
        Log.i(TAG, "HTTP proxy listening on 0.0.0.0:$actualPort")
        thread(name = "http-proxy-acceptor", isDaemon = true) { acceptLoop() }
        return actualPort
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        try { serverSocket?.close() } catch (_: Exception) {}
        executor.shutdownNow()
        actualPort = -1
    }

    private fun bindWithFallback(): ServerSocket? {
        for (offset in 0 until PORT_FALLBACK_ATTEMPTS) {
            val port = BASE_PORT + offset
            try {
                return ServerSocket(port, 50, InetAddress.getByName("0.0.0.0")).apply {
                    reuseAddress = true
                }
            } catch (e: Exception) {
                Log.w(TAG, "bind on $port failed: ${e.message}")
            }
        }
        Log.e(TAG, "could not bind on $BASE_PORT..${BASE_PORT + PORT_FALLBACK_ATTEMPTS - 1}")
        return null
    }

    private fun acceptLoop() {
        try {
            while (running.get()) {
                val client = try {
                    serverSocket!!.accept()
                } catch (e: Exception) {
                    if (running.get()) Log.e(TAG, "accept() failed", e)
                    break
                }
                // 와일드카드 바인딩은 모든 인터페이스를 덮는다(주소 하나로 좁힐 수 없는
                // 이유는 PeerFilter 참고). USB 루프백 경로나 Wi-Fi Direct 클라이언트가
                // 아니면 한 바이트도 읽기 전에 거부한다.
                if (!PeerFilter.isAllowed(client.inetAddress)) {
                    Log.w(TAG, "허용되지 않은 네트워크의 클라이언트를 거부했다")
                    try { client.close() } catch (_: Exception) {}
                    continue
                }
                executor.submit { handleClient(client) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "acceptLoop crashed", e)
        }
    }

    private fun handleClient(client: Socket) {
        try {
            client.tcpNoDelay = true
            client.soTimeout = 10_000
            val inp = client.getInputStream()
            val out = client.getOutputStream()

            val requestLine = readHeaderLine(inp) ?: return
            val parts = requestLine.split(' ', limit = 3)
            if (parts.size < 3) { sendStatus(out, 400, "Bad Request"); return }
            val method = parts[0]
            val target = parts[1]
            val version = parts[2]

            val headers = mutableListOf<Pair<String, String>>()
            while (true) {
                val line = readHeaderLine(inp) ?: break
                if (line.isEmpty()) break
                val idx = line.indexOf(':')
                if (idx <= 0) continue
                headers += line.substring(0, idx).trim() to line.substring(idx + 1).trim()
            }

            if (method.equals("CONNECT", ignoreCase = true)) {
                handleConnect(client, target)
            } else {
                handleForward(client, method, target, version, headers)
            }
        } catch (e: Exception) {
            Log.w(TAG, "client error: ${e.message}")
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun handleConnect(client: Socket, hostPort: String) {
        val (host, port) = splitHostPort(hostPort, defaultPort = 443) ?: run {
            sendStatus(client.getOutputStream(), 400, "Bad Request"); return
        }
        val remote = try {
            Socket().apply {
                tcpNoDelay = true
                connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            }
        } catch (e: Exception) {
            Log.w(TAG, "CONNECT $host:$port failed: ${e.message}")
            sendStatus(client.getOutputStream(), 502, "Bad Gateway")
            return
        }
        try {
            client.getOutputStream().write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
            client.getOutputStream().flush()
            client.soTimeout = 0
            onTcpCount(sessions.incrementAndGet())
            relay(client, remote)
            onTcpCount(sessions.decrementAndGet())
        } finally {
            try { remote.close() } catch (_: Exception) {}
        }
    }

    private fun handleForward(
        client: Socket,
        method: String,
        absoluteUri: String,
        version: String,
        headers: List<Pair<String, String>>,
    ) {
        if (!absoluteUri.startsWith("http://", ignoreCase = true)) {
            sendStatus(client.getOutputStream(), 400, "Bad Request"); return
        }
        val pathStart = absoluteUri.indexOf('/', "http://".length).let { if (it == -1) absoluteUri.length else it }
        val hostPort = absoluteUri.substring("http://".length, pathStart)
        val path = if (pathStart == absoluteUri.length) "/" else absoluteUri.substring(pathStart)
        val (host, port) = splitHostPort(hostPort, defaultPort = 80) ?: run {
            sendStatus(client.getOutputStream(), 400, "Bad Request"); return
        }

        val remote = try {
            Socket().apply {
                tcpNoDelay = true
                connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            }
        } catch (e: Exception) {
            Log.w(TAG, "HTTP $host:$port failed: ${e.message}")
            sendStatus(client.getOutputStream(), 502, "Bad Gateway")
            return
        }
        try {
            val remoteOut = remote.getOutputStream()
            val sb = StringBuilder()
            sb.append(method).append(' ').append(path).append(' ').append(version).append("\r\n")
            var hostHeaderWritten = false
            for ((k, v) in headers) {
                if (k.equals("Proxy-Connection", ignoreCase = true)) continue
                if (k.equals("Proxy-Authorization", ignoreCase = true)) continue
                if (k.equals("Host", ignoreCase = true)) hostHeaderWritten = true
                sb.append(k).append(": ").append(v).append("\r\n")
            }
            if (!hostHeaderWritten) sb.append("Host: ").append(hostPort).append("\r\n")
            sb.append("\r\n")
            remoteOut.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
            remoteOut.flush()

            client.soTimeout = 0
            onTcpCount(sessions.incrementAndGet())
            relay(client, remote)
            onTcpCount(sessions.decrementAndGet())
        } finally {
            try { remote.close() } catch (_: Exception) {}
        }
    }

    private fun relay(client: Socket, remote: Socket) {
        val t = thread(isDaemon = true) {
            pipe(remote.getInputStream(), client.getOutputStream()) { onBytesOut(it) }
            try { client.shutdownOutput() } catch (_: Exception) {}
        }
        pipe(client.getInputStream(), remote.getOutputStream()) { onBytesIn(it) }
        try { remote.shutdownOutput() } catch (_: Exception) {}
        t.join(5_000)
    }

    private fun pipe(src: InputStream, dst: OutputStream, onBytes: (Long) -> Unit) {
        try {
            val buf = ByteArray(8192)
            var n: Int
            while (src.read(buf).also { n = it } != -1) {
                dst.write(buf, 0, n)
                onBytes(n.toLong())
            }
        } catch (_: Exception) {}
    }

    private fun sendStatus(out: OutputStream, code: Int, reason: String) {
        try {
            out.write("HTTP/1.1 $code $reason\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
        } catch (_: Exception) {}
    }

    private fun splitHostPort(hostPort: String, defaultPort: Int): Pair<String, Int>? {
        if (hostPort.startsWith("[")) {
            val end = hostPort.indexOf(']')
            if (end == -1) return null
            val host = hostPort.substring(1, end)
            val rest = hostPort.substring(end + 1)
            val port = if (rest.startsWith(":")) rest.substring(1).toIntOrNull() ?: return null else defaultPort
            return host to port
        }
        val idx = hostPort.lastIndexOf(':')
        return if (idx == -1) hostPort to defaultPort
        else hostPort.substring(0, idx) to (hostPort.substring(idx + 1).toIntOrNull() ?: return null)
    }

    /**
     * CRLF 로 끝나는 헤더 한 줄을 바이트 단위로 읽는다. 이렇게 해야 요청 본문
     * 바이트가 InputStream 에 남아 relay() 가 상위로 그대로 전달할 수 있다.
     */
    private fun readHeaderLine(src: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = src.read()
            if (b == -1) return if (sb.isEmpty()) null else sb.toString()
            when (b) {
                '\r'.code -> {
                    val next = src.read()
                    if (next == '\n'.code || next == -1) return sb.toString()
                    sb.append('\r').append(next.toChar())
                }
                '\n'.code -> {
                    return sb.toString()
                }
                else -> {
                    sb.append(b.toChar())
                }
            }
        }
    }

    companion object {
        private const val TAG = "HttpProxyServer"
        private const val CONNECT_TIMEOUT_MS = 5000
        private const val PORT_FALLBACK_ATTEMPTS = 10
        const val BASE_PORT = 8282
    }
}
