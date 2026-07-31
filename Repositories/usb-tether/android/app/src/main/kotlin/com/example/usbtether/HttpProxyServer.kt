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
 * 접근을 통제한다 — 연결이 그룹 오너 주소(192.168.49.1)로 도착했고 상대가
 * 192.168.49.2–254 일 때만 서비스한다. 로컬 주소를 함께 보므로 **핫스팟이 꺼져
 * 있는 동안에는 어떤 연결도 통과하지 못한다.**
 * 전체 근거와 남는 빈틈은 PeerFilter 의 KDoc 을 볼 것.
 */
class HttpProxyServer(
    private val onBytesIn: (Long) -> Unit = {},
    private val onBytesOut: (Long) -> Unit = {},
) {
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newCachedThreadPool()

    /** 수립된 연결을 추적한다. stop() 이 실제로 끊을 수 있게 하는 유일한 수단이다. */
    private val connections = ConnectionRegistry()

    /** 실제로 바인딩된 포트. 서버가 동작 중이 아니면 -1. */
    @Volatile var actualPort: Int = -1
        private set

    /**
     * 마지막 기동 실패 원인. 정상이면 null.
     *
     * 10회 폴백이 있어 실패가 드물지만, 드물다고 조용히 넘기면 UI 에 이유 없는
     * `—` 만 남아 사용자가 손쓸 방법이 없다. "포트 문제는 조용히 옮기지 않고
     * 알린다"는 원칙을 [Socks5Server] 와 똑같이 적용한다.
     */
    @Volatile var lastError: String? = null
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
        lastError = null
        Log.i(TAG, "HTTP proxy listening on 0.0.0.0:$actualPort")
        thread(name = "http-proxy-acceptor", isDaemon = true) { acceptLoop() }
        return actualPort
    }

    /**
     * 서버를 멈춘다.
     *
     * 리스닝 소켓을 닫는 것만으로는 부족하다. `read()` 에 블록된 워커 스레드는
     * 인터럽트를 무시하므로, 수립된 소켓을 직접 닫아야 릴레이가 실제로 끝난다.
     * 순서가 중요하다: 새 연결을 먼저 막고(리스닝 소켓 닫기), 기존 연결을 끊고,
     * 그다음 스레드풀을 내린다.
     */
    fun stop() {
        if (!running.compareAndSet(true, false)) return
        try { serverSocket?.close() } catch (_: Exception) {}
        connections.closeAll()
        executor.shutdownNow()
        actualPort = -1
    }

    private fun bindWithFallback(): ServerSocket? {
        for (offset in 0 until PORT_FALLBACK_ATTEMPTS) {
            val port = BASE_PORT + offset
            try {
                // reuseAddress 는 설정하지 않는다 — 생성자가 이미 바인딩을 끝낸
                // 뒤라 효과가 없다(오해를 부르는 죽은 코드였다).
                return ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"))
            } catch (e: Exception) {
                Log.w(TAG, "bind on $port failed: ${e.message}")
            }
        }
        val lastPort = BASE_PORT + PORT_FALLBACK_ATTEMPTS - 1
        lastError = "HTTP 포트 $BASE_PORT–$lastPort 를 모두 다른 앱이 쓰고 있습니다"
        Log.e(TAG, "could not bind on $BASE_PORT..$lastPort")
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
                // 이유는 PeerFilter 참고). 상대 주소와 **연결이 도착한 로컬 주소**를
                // 함께 본다 — 로컬 주소가 그룹 오너 주소여야 하므로, 핫스팟이 꺼져
                // 있는 동안에는 어떤 연결도 통과할 수 없다.
                if (!PeerFilter.isAllowed(client.inetAddress, client.localAddress)) {
                    Log.w(TAG, "허용되지 않은 네트워크의 클라이언트를 거부했다")
                    try { client.close() } catch (_: Exception) {}
                    continue
                }
                // 동시 세션 상한. 등록을 accept 시점에 하는 것이 중요하다 — handleClient
                // 안에서 등록하면 accept 와 등록 사이에 태스크가 쌓여 CachedThreadPool 이
                // 무제한으로 스레드를 만든다. 여기서 세면 풀 크기가 구조적으로 묶인다.
                if (connections.size >= MAX_CONCURRENT_SESSIONS) {
                    Log.w(TAG, "동시 세션 상한 초과로 연결을 거부했다")
                    try { client.close() } catch (_: Exception) {}
                    continue
                }
                connections.register(client)
                try {
                    executor.submit { handleClient(client) }
                } catch (t: Throwable) {
                    // Throwable 을 잡는다. RejectedExecutionException 뿐 아니라
                    // 스레드를 더 만들 수 없을 때의 OutOfMemoryError 도 여기로 온다.
                    // Error 를 놓치면 등록된 소켓이 unregister 되지 않아 세션 슬롯을
                    // 영구히 점유하고, accept 스레드까지 죽어 서버가 살아 있는 것처럼
                    // 보이면서 아무 연결도 받지 못하는 상태가 된다.
                    connections.unregister(client)
                    try { client.close() } catch (_: Exception) {}
                    Log.e(TAG, "작업 제출 실패: ${t.javaClass.simpleName}")
                }
            }
        } catch (t: Throwable) {
            // Exception 이 아니라 Throwable — OutOfMemoryError 로 조용히 죽으면
            // UI 는 계속 "Running" 을 표시한다. 원인을 남기고 상태를 내린다.
            Log.e(TAG, "acceptLoop crashed", t)
            running.set(false)
        }
    }

    private fun handleClient(client: Socket) {
        // 등록은 acceptLoop 에서 이미 했다(동시 세션 판정을 정확히 하기 위해).
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
                if (headers.size >= MAX_HEADER_COUNT) throw HeaderTooLargeException()
                val idx = line.indexOf(':')
                if (idx <= 0) continue
                headers += line.substring(0, idx).trim() to line.substring(idx + 1).trim()
            }

            if (method.equals("CONNECT", ignoreCase = true)) {
                handleConnect(client, target)
            } else {
                rejectDesyncHeaders(headers)
                handleForward(client, method, target, version, headers)
            }
        } catch (_: HeaderTooLargeException) {
            Log.w(TAG, "헤더가 상한을 넘어 요청을 거부했다")
            try { sendStatus(client.getOutputStream(), 431, "Request Header Fields Too Large") } catch (_: Exception) {}
        } catch (_: MalformedRequestException) {
            Log.w(TAG, "헤더 문법 오류로 요청을 거부했다")
            try { sendStatus(client.getOutputStream(), 400, "Bad Request") } catch (_: Exception) {}
        } catch (e: Exception) {
            Log.w(TAG, "client error: ${e.message}")
        } finally {
            connections.unregister(client)
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun handleConnect(client: Socket, hostPort: String) {
        val (host, port) = splitHostPort(hostPort, defaultPort = 443) ?: run {
            sendStatus(client.getOutputStream(), 400, "Bad Request"); return
        }
        val destination = resolveOrReject(client, host) ?: return
        val remote = try {
            Socket().apply {
                tcpNoDelay = true
                connect(InetSocketAddress(destination, port), CONNECT_TIMEOUT_MS)
            }
        } catch (e: Exception) {
            // 목적지 호스트·포트를 남기지 않는다. 실패한 접속만 모아도 부분적인
            // 방문 기록이 되고, 이 워크플로는 APK 설치를 위해 ADB 를 쓰므로
            // 연결된 PC 가 `adb logcat` 으로 읽어갈 수 있다. 예외 종류만 남긴다.
            Log.w(TAG, "CONNECT 실패: ${e.javaClass.simpleName}")
            sendStatus(client.getOutputStream(), 502, "Bad Gateway")
            return
        }
        try {
            client.getOutputStream().write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
            client.getOutputStream().flush()
            client.soTimeout = 0
            relay(client, remote)
        } finally {
            try { remote.close() } catch (_: Exception) {}
        }
    }

    /**
     * 요청 스머글링(desync)에 쓰이는 헤더 조합을 거부한다.
     *
     * 이전에는 `Proxy-Connection` 과 `Proxy-Authorization` 만 걸러내고 나머지를
     * 그대로 상위에 전달했다. 그래서 다음이 통과했다.
     *
     *  - `Content-Length` 와 `Transfer-Encoding` **동시 지정**. 상위 CDN·리버스
     *    프록시와 본문 경계 해석이 갈리는 CL.TE / TE.CL desync 의 재료다.
     *  - **중복 `Host`**. 프런트엔드와 백엔드가 서로 다른 Host 를 보고 라우팅해
     *    캐시 오염·라우팅 desync 로 이어진다.
     *
     * 클라이언트가 이미 CONNECT 로 임의 바이트를 쓸 수 있어 권한 상승은 아니지만,
     * 이 폰이 사용자의 통신사 IP 로 desync 공격을 세탁해 주는 경유지가 되는 것을
     * 막는다. RFC 7230 §3.3.3 은 두 헤더가 함께 오면 400 으로 거부하도록 정한다.
     *
     * @throws MalformedRequestException 위 조합이 발견되면
     */
    private fun rejectDesyncHeaders(headers: List<Pair<String, String>>) {
        var hasContentLength = false
        var hasTransferEncoding = false
        var hostCount = 0

        for ((name, _) in headers) {
            when {
                name.equals("Content-Length", ignoreCase = true) -> hasContentLength = true
                name.equals("Transfer-Encoding", ignoreCase = true) -> hasTransferEncoding = true
                name.equals("Host", ignoreCase = true) -> hostCount++
            }
        }
        if (hasContentLength && hasTransferEncoding) throw MalformedRequestException()
        if (hostCount > 1) throw MalformedRequestException()
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

        val destination = resolveOrReject(client, host) ?: return
        val remote = try {
            Socket().apply {
                tcpNoDelay = true
                connect(InetSocketAddress(destination, port), CONNECT_TIMEOUT_MS)
            }
        } catch (e: Exception) {
            // 목적지 호스트·포트를 남기지 않는다. 실패한 접속만 모아도 부분적인
            // 방문 기록이 되고, 이 워크플로는 APK 설치를 위해 ADB 를 쓰므로
            // 연결된 PC 가 `adb logcat` 으로 읽어갈 수 있다. 예외 종류만 남긴다.
            Log.w(TAG, "HTTP 전달 실패: ${e.javaClass.simpleName}")
            sendStatus(client.getOutputStream(), 502, "Bad Gateway")
            return
        }
        try {
            val remoteOut = remote.getOutputStream()
            val sb = StringBuilder()
            sb.append(method).append(' ').append(path).append(' ').append(version).append("\r\n")
            var hostHeaderWritten = false
            for ((k, v) in headers) {
                // hop-by-hop 헤더는 이 홉에서 끝나야 한다(RFC 7230 §6.1).
                // 그대로 전달하면 상위와의 연결 관리·업그레이드 협상이 어긋난다.
                if (HOP_BY_HOP_HEADERS.any { it.equals(k, ignoreCase = true) }) continue
                if (k.equals("Host", ignoreCase = true)) hostHeaderWritten = true
                sb.append(k).append(": ").append(v).append("\r\n")
            }
            if (!hostHeaderWritten) sb.append("Host: ").append(hostPort).append("\r\n")
            // hop-by-hop 헤더를 제거했으므로 이 홉의 연결 관리를 우리가 명시해야 한다.
            // 생략하면 HTTP/1.1 상위는 keep-alive 로 간주하고, 클라이언트가
            // `Connection: close` 를 보냈더라도 상위가 EOF 를 주지 않는다. 그러면
            // relay() 가 클라이언트가 끊을 때까지 매달려 있다가 join(5초)를 다 쓰고,
            // 요청 하나마다 소켓 하나와 스레드 둘을 그만큼 붙잡는다.
            // 이 프록시는 요청마다 새 상위 연결을 열므로 close 가 맞다.
            sb.append("Connection: close\r\n")
            sb.append("\r\n")
            remoteOut.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
            remoteOut.flush()

            client.soTimeout = 0
            relay(client, remote)
        } finally {
            try { remote.close() } catch (_: Exception) {}
        }
    }

    /**
     * 목적지를 해석하고 정책을 적용한다. 거부되면 클라이언트에 403 을 보내고 null 을 반환한다.
     *
     * 통과한 **주소 객체**를 돌려주는 것이 중요하다. 호출부가 다시 문자열로 연결하면
     * 재해석이 일어나 DNS 리바인딩으로 검사를 우회할 수 있다.
     */
    private fun resolveOrReject(client: Socket, host: String): InetAddress? =
        when (val verdict = DestinationFilter.resolve(host)) {
            is DestinationFilter.Result.Rejected -> {
                Log.w(TAG, "목적지 거부: ${verdict.reason}")
                sendStatus(client.getOutputStream(), 403, "Forbidden")
                null
            }
            is DestinationFilter.Result.Allowed -> verdict.address
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
            if (sb.length >= MAX_HEADER_LINE_BYTES) throw HeaderTooLargeException()
            val b = src.read()
            if (b == -1) return if (sb.isEmpty()) null else sb.toString()
            when (b) {
                '\r'.code -> {
                    val next = src.read()
                    if (next == '\n'.code || next == -1) return sb.toString()
                    // LF 가 뒤따르지 않는 CR 은 프로토콜 오류다. 보존하면 헤더 주입이
                    // 된다 — MalformedRequestException KDoc 참고.
                    throw MalformedRequestException()
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

        /** 동시 세션 상한. 근거는 Socks5Server 의 같은 상수 주석 참고. */
        private const val MAX_CONCURRENT_SESSIONS = 256

        /**
         * 헤더 한 줄의 최대 바이트 수.
         *
         * 이전 readHeaderLine 은 CRLF 를 만날 때까지 StringBuilder 에 무제한으로
         * 담았다. CR/LF 가 없는 바이트를 계속 흘리면 문자당 2바이트로 자라고, 읽기가
         * 성공할 때마다 soTimeout 이 갱신되어 시간 제한도 없었다. OutOfMemoryError 는
         * Error 라서 handleClient 의 catch (e: Exception) 에 걸리지 않고 프로세스
         * 전체 힙을 때린다.
         */
        private const val MAX_HEADER_LINE_BYTES = 8 * 1024

        /** 헤더 개수 상한. 무제한 리스트 누적으로도 같은 OOM 에 도달할 수 있다. */
        private const val MAX_HEADER_COUNT = 100

        /**
         * 이 홉에서 소비하고 상위로 전달하지 않는 헤더 (RFC 7230 §6.1).
         *
         * Proxy-Authorization 은 인증을 쓰지 않는 지금도 목록에 둔다 —
         * 자격증명을 목적지 서버로 흘려보내지 않기 위함이다.
         */
        private val HOP_BY_HOP_HEADERS = listOf(
            "Proxy-Connection",
            "Proxy-Authorization",
            "Proxy-Authenticate",
            "Connection",
            "Keep-Alive",
            "TE",
            "Trailer",
            "Upgrade",
        )
        const val BASE_PORT = 8282
    }
}

/** 헤더 길이·개수 상한 초과. handleClient 가 431 로 응답하고 연결을 닫는다. */
private class HeaderTooLargeException : Exception()

/**
 * 헤더 문법 오류. handleClient 가 400 으로 응답하고 연결을 닫는다.
 *
 * 특히 LF 를 동반하지 않는 CR 을 잡는다. 이전 readHeaderLine 은 그런 CR 을
 * 그대로 보존했고, `.trim()` 은 양 끝만 다듬으므로 **줄 중간의** CR 이 살아남았다.
 * 헤더를 ISO_8859_1 로 바이트 단위 그대로 상위에 다시 쓰기 때문에
 * `X-A: foo\rEvil: 1` 이 그대로 전달되고, bare CR 을 줄 구분자로 취급하는
 * 서버·프록시는 헤더가 하나 더 있는 것으로 본다(헤더 주입).
 *
 * 현재는 클라이언트가 이미 `CONNECT host:80` 으로 임의 바이트를 쓸 수 있어
 * 추가 권한을 주지는 않는다. 그러나 CONNECT 를 제한하거나 인증을 붙이는 순간
 * 이것이 우회로가 되므로 지금 막는다.
 */
private class MalformedRequestException : Exception()
