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
 * HTTP proxy server bound to 0.0.0.0. Tries [BASE_PORT], [BASE_PORT]+1, ...,
 * [BASE_PORT]+9 in order (up to 10 attempts) and binds the first port that
 * is free (e.g. NetShare is already squatting on 8282). Inspect [actualPort]
 * after [start] returns to discover the chosen port.
 * Used by Wi-Fi P2P clients that set the system per-network proxy (Android only
 * accepts HTTP for that setting, not SOCKS5).
 *
 * Supports:
 *   - CONNECT host:port HTTP/1.1   → opaque TCP tunnel (HTTPS, WebSocket, etc.)
 *   - GET/POST/... http://host[:port]/path HTTP/1.1
 *       → strip absolute URI, forward to upstream as origin-form request
 *
 * No authentication. Outbound sockets are opened by Android's OS TCP stack,
 * so the carrier sees only normal phone-originated traffic.
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

    /** Port actually bound, or -1 if the server isn't running. */
    @Volatile var actualPort: Int = -1
        private set

    /** Bind synchronously so the caller sees the chosen port immediately, then start accepting. */
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
     * Read one CRLF-terminated header line byte-by-byte so request body bytes stay
     * in the InputStream — relay() will forward them to the upstream unchanged.
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
