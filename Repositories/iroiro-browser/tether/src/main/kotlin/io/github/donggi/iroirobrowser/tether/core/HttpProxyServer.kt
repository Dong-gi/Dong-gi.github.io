package io.github.donggi.iroirobrowser.tether.core

import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket
import java.util.Locale

/**
 * HTTP 프록시. [BASE_PORT](8282)부터 [PORT_ATTEMPTS] 개(8282…8291)를 차례로 해 보아 **비어 있는 첫 포트**에 묶는다 — 열 개가 모두 차
 * 있을 때만 실패를 알린다([StartError.PORT_TAKEN]). 실제로 묶인 포트는 [actualPort] 이고 화면·알림이 그것을 보인다. 시스템의 Wi-Fi 별
 * 프록시 설정을 쓰는 손님용이다(안드로이드·윈도의 그 설정은 HTTP 만 받는다).
 *
 * **옮기는 것은 사용자 결정이다**(2026-10-10 — '서버 포트는 기존처럼 +1씩 하도록 해주세요. 핫스팟을 통해서만 연결할 것이므로 문제
 * 없습니다'). 옮겨 서는 위험은 남는다 — PC 는 `HTTP_PROXY=192.168.49.1:8282` 를
 * 고정으로 쓰므로, 이 폰의 다른 앱이 8282 를 먼저 쥐면(0.0.0.0 에 묶으면 핫스팟의 손님도 그 앱에 닿는다) PC 가 그 앱을 프록시로 써 손님
 * 거르기도 없이 평문 요청과 CONNECT 의 호스트를 보인다([Socks5Server] 의 머리말과 같은 틈). 사용자가 감수했다. 그래서 8282 가 아닌 포트에
 * 섰으면 PC 의 설정을 그 포트로 고치라고 화면이 적는다.
 *
 * 받는 꼴:
 *   - `CONNECT host:port HTTP/1.1` → 불투명 TCP 터널(HTTPS·WebSocket 등)
 *   - `GET/POST/… http://host[:port]/path HTTP/1.1` → 절대 URI 를 벗겨 origin-form 으로 원격에 넘긴다
 *
 * 인증은 없다. 머리는 바이트 단위로 읽어 본문 바이트가 스트림에 남게 하고(같은 스트림을 중계까지 넘긴다), 줄 길이·줄 수(`:` 없는 줄도
 * 센다)·다 받기까지의 시간(전체 마감 — [DeadlineInputStream])에 상한을 둔다. 요청 스머글링의 재료(CL+TE·겹친 Host·홀로 선 CR)는 400 으로
 * 거절한다. 막힌 목적지는 403, 풀지 못한 이름은 502 — 둘 다 까닭을 적은 본문으로 답한다.
 *
 * 상한: 동시 연결 [MAX_SESSIONS](usb-tether 그대로 — 이 PC 처럼 시스템·환경 변수의 프록시로 PC 의 앱들이 통째로 지나는 쓰임이 있다). 스레드는
 * [ThreadBudget] 이 셋을 함께 묶는다.
 */
class HttpProxyServer(
    env: TetherEnvironment,
    basePort: Int = BASE_PORT,
    attempts: Int = PORT_ATTEMPTS,
) : TcpServer(env, "http-proxy", basePort, attempts, maxSessions = MAX_SESSIONS, threadsPerSession = 2) {

    override fun handle(client: Socket) {
        client.tcpNoDelay = true
        // 머리를 다 받기까지의 전체 마감 — 읽기마다 다시 거는 soTimeout 만으로는 한 바이트씩 흘리는 손님이 일꾼을 끝없이 쥔다.
        val deadline = DeadlineInputStream(client, client.getInputStream(), env.timings.proxyHeaderMs, env.nowMs)
        // 머리는 바이트 단위로 읽어야 본문이 스트림에 남는다([readHeaderLine]). 맨 소켓 스트림에 그렇게 하면 바이트마다 시스템 호출이라 버퍼로
        // 감싸고, **이 스트림을 relay() 까지 그대로 넘겨** 버퍼에 이미 들어온 본문을 잃지 않는다(client.getInputStream() 을 다시 부르면 잃는다).
        val inp = BufferedInputStream(deadline, HEADER_BUFFER_BYTES)
        val out = client.getOutputStream()
        try {
            val requestLine = readHeaderLine(inp) ?: return
            val parts = requestLine.split(' ', limit = 3)
            if (parts.size < 3) {
                sendStatus(out, 400, "Bad Request")
                return
            }
            val method = parts[0]
            val target = parts[1]
            val version = parts[2]

            val headers = mutableListOf<Pair<String, String>>()
            var lines = 0
            while (true) {
                val line = readHeaderLine(inp) ?: break
                if (line.isEmpty()) break
                // 줄을 센다 — 담은 머리 수로 세면 `:` 없는 줄(걷어 버린다)이 끝없이 이어져도 걸리지 않았다.
                if (++lines > MAX_HEADER_COUNT) throw HeaderTooLargeException()
                val idx = line.indexOf(':')
                if (idx <= 0) continue
                headers += line.substring(0, idx).trim() to line.substring(idx + 1).trim()
            }
            // 머리를 다 받았다 — 마감을 거둔다(중계는 시한이 없다 — handleConnect·handleForward 가 soTimeout 을 0 으로).
            deadline.finish()

            if (method.equals("CONNECT", ignoreCase = true)) {
                handleConnect(client, inp, target)
            } else {
                rejectDesyncHeaders(headers)
                handleForward(client, inp, method, target, version, headers)
            }
        } catch (_: HeaderTooLargeException) {
            env.log.w(TAG, "머리가 상한을 넘어 거절했다")
            sendStatus(out, 431, "Request Header Fields Too Large")
        } catch (_: MalformedRequestException) {
            env.log.w(TAG, "머리 문법이 틀려 거절했다")
            sendStatus(out, 400, "Bad Request")
        }
    }

    private fun handleConnect(client: Socket, clientIn: InputStream, hostPort: String) {
        val (host, port) = splitHostPort(hostPort, defaultPort = 443) ?: run {
            sendStatus(client.getOutputStream(), 400, "Bad Request")
            return
        }
        val destination = resolveOrReject(client, host) ?: return
        val remote = try {
            env.outbound.connect(destination, port, CONNECT_TIMEOUT_MS)
        } catch (e: Exception) {
            // 목적지를 남기지 않는다(TetherLog 의 규칙). 종류만.
            env.log.w(TAG, "CONNECT 실패: ${e.javaClass.simpleName}")
            sendStatus(client.getOutputStream(), 502, "Bad Gateway")
            return
        }
        try {
            client.getOutputStream().write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
            client.getOutputStream().flush()
            client.soTimeout = 0
            relay(client, clientIn, remote)
        } finally {
            closeQuietly(remote)
        }
    }

    /**
     * 요청 스머글링(desync)에 쓰이는 머리 조합을 거절한다 — `Content-Length` 와 `Transfer-Encoding` 을 **함께**(원격의 CDN·리버스 프록시와 본문의
     * 경계를 달리 읽는 CL.TE/TE.CL 의 재료), **겹친 `Host`**(앞뒤가 다른 Host 를 보고 길을 정해 캐시 오염으로 이어진다). 손님은 이미 CONNECT 로
     * 아무 바이트나 쓸 수 있어 권한이 오르지는 않지만, 이 폰이 사용자의 통신사 IP 로 그런 공격을 세탁해 주는 경유지가 되는 것을 막는다.
     * RFC 7230 3.3.3 은 둘이 함께 오면 400 으로 거절하게 한다.
     *
     * @throws MalformedRequestException 위 조합이 있으면
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
        clientIn: InputStream,
        method: String,
        absoluteUri: String,
        version: String,
        headers: List<Pair<String, String>>,
    ) {
        if (!absoluteUri.startsWith("http://", ignoreCase = true)) {
            sendStatus(client.getOutputStream(), 400, "Bad Request")
            return
        }
        val pathStart = absoluteUri.indexOf('/', "http://".length).let { if (it == -1) absoluteUri.length else it }
        val hostPort = absoluteUri.substring("http://".length, pathStart)
        val path = if (pathStart == absoluteUri.length) "/" else absoluteUri.substring(pathStart)
        val (host, port) = splitHostPort(hostPort, defaultPort = 80) ?: run {
            sendStatus(client.getOutputStream(), 400, "Bad Request")
            return
        }

        val destination = resolveOrReject(client, host) ?: return
        val remote = try {
            env.outbound.connect(destination, port, CONNECT_TIMEOUT_MS)
        } catch (e: Exception) {
            env.log.w(TAG, "HTTP 전달 실패: ${e.javaClass.simpleName}")
            sendStatus(client.getOutputStream(), 502, "Bad Gateway")
            return
        }
        try {
            val remoteOut = remote.getOutputStream()
            val sb = StringBuilder()
            sb.append(method).append(' ').append(path).append(' ').append(version).append("\r\n")
            var hostHeaderWritten = false
            for ((k, v) in headers) {
                // hop-by-hop 머리는 이 홉에서 끝난다(RFC 7230 6.1) — 그대로 넘기면 원격과의 연결 관리·업그레이드 협상이 어긋난다.
                if (isHopByHop(k)) continue
                if (k.equals("Host", ignoreCase = true)) hostHeaderWritten = true
                sb.append(k).append(": ").append(v).append("\r\n")
            }
            if (!hostHeaderWritten) sb.append("Host: ").append(hostPort).append("\r\n")
            // hop-by-hop 을 걷었으니 이 홉의 연결 관리를 우리가 적는다. 빼면 HTTP/1.1 원격은 keep-alive 로 보고 EOF 를 주지 않아, relay() 가
            // 손님이 끊을 때까지 매달린다. 이 프록시는 요청마다 원격 연결을 새로 여므로 close 가 맞다.
            sb.append("Connection: close\r\n")
            sb.append("\r\n")
            remoteOut.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
            remoteOut.flush()

            client.soTimeout = 0
            relay(client, clientIn, remote)
        } finally {
            closeQuietly(remote)
        }
    }

    /**
     * 목적지를 풀고 거른다. 막힌 곳이면 403, 이름을 풀지 못했으면 502 — 둘 다 까닭을 적은 본문을 보내고 null. 예전에는 풀이 실패도 403 과
     * '사설·로컬 주소' 본문이라 오타 난 주소가 프록시의 막기처럼 보였다. 통과한 **주소 객체**를 돌려준다 — 부르는 쪽이 글로 다시
     * 연결하면 다시 풀려 DNS 재바인딩으로 비켜 갈 수 있다.
     */
    private fun resolveOrReject(client: Socket, host: String): InetAddress? =
        when (val verdict = env.destinations.resolve(host)) {
            is Destination.Rejected -> {
                env.log.w(TAG, "목적지 거절: ${verdict.reason}")
                sendError(client.getOutputStream(), 403, "Forbidden", BLOCKED_DESTINATION_HELP)
                null
            }
            is Destination.Unresolved -> {
                env.log.w(TAG, "이름 풀이 실패: ${verdict.reason}")
                sendError(client.getOutputStream(), 502, "Bad Gateway", UNRESOLVED_HELP)
                null
            }
            is Destination.Allowed -> verdict.address
        }

    /**
     * 상태 코드와 까닭을 적은 본문을 보낸다. 본문 없는 4xx 는 브라우저의 일반 오류 화면만 띄워 까닭을 감춘다 — 특히 403 은 왜 거절됐는지가
     * 드러나지 않아 사용자가 프록시를 의심하지 못한다(같은 폰의 파일 서버에 프록시로 붙으려다 받는 것이 흔한 경우다).
     */
    private fun sendError(out: OutputStream, code: Int, reason: String, message: String) {
        try {
            val body = message.toByteArray(Charsets.UTF_8)
            out.write(
                (
                    "HTTP/1.1 $code $reason\r\n" +
                        "Content-Type: text/plain; charset=utf-8\r\n" +
                        "Content-Length: ${body.size}\r\n" +
                        "Connection: close\r\n\r\n"
                    ).toByteArray(Charsets.ISO_8859_1),
            )
            out.write(body)
            out.flush()
        } catch (_: Exception) {
            // 이미 끊긴 연결 — 알릴 길이 없다.
        }
    }

    private fun sendStatus(out: OutputStream, code: Int, reason: String) {
        try {
            out.write("HTTP/1.1 $code $reason\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
            out.flush()
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val TAG = "HttpProxyServer"

        /** 기본 포트 — PC 가 이 값(`HTTP_PROXY=192.168.49.1:8282`)을 쓴다. 차 있으면 다음 포트로(클래스 머리말). */
        const val BASE_PORT = 8282

        /** 해 볼 포트 수 — [BASE_PORT] 부터 하나씩(8282…8291). usb-tether·처음 합친 판과 같은 값. */
        const val PORT_ATTEMPTS = 10

        /** 동시 연결 상한(usb-tether 와 같다). 근거는 클래스 머리말. */
        const val MAX_SESSIONS = 256

        private const val CONNECT_TIMEOUT_MS = 5000

        /**
         * 머리 한 줄의 최대 바이트. 예전 readHeaderLine 은 CRLF 를 만날 때까지 끝없이 담았다 — CR/LF 없는 바이트를 흘리면 글자마다 2바이트로
         * 자랐고, 읽기가 될 때마다 soTimeout 이 새로 걸려 시간 제한도 없었다. OutOfMemoryError 는 Error 라 catch (Exception) 에 걸리지 않는다.
         */
        internal const val MAX_HEADER_LINE_BYTES = 8 * 1024

        /** 머리 읽기용 버퍼 — 한 줄 상한과 같은 크기로 두어 보통의 머리가 대개 한 번의 read 로 찬다. */
        private const val HEADER_BUFFER_BYTES = MAX_HEADER_LINE_BYTES

        /** 머리 줄 수 상한(`:` 없는 줄도 센다) — 끝없이 쌓는 목록으로도 같은 OOM 에 닿고, 끝없는 줄은 일꾼을 쥔다. */
        internal const val MAX_HEADER_COUNT = 100

        /**
         * 이 홉에서 쓰고 원격에 넘기지 않는 머리(RFC 7230 6.1). Proxy-Authorization 은 인증을 쓰지 않는 지금도 둔다 — 자격 증명을 목적지로
         * 흘리지 않게. **소문자로 적고 Set 으로** 둔다 — 소문자로 바꿀 때 반드시 `Locale.ROOT`(기본 로케일이면 터키어에서 `I` 가 `ı` 가 되어
         * `KEEP-ALIVE` 같은 표기가 목록과 어긋나 hop-by-hop 머리가 원격으로 샌다).
         */
        private val HOP_BY_HOP_HEADERS = setOf(
            "proxy-connection",
            "proxy-authorization",
            "proxy-authenticate",
            "connection",
            "keep-alive",
            "te",
            "trailer",
            "upgrade",
        )

        private fun isHopByHop(name: String): Boolean = name.lowercase(Locale.ROOT) in HOP_BY_HOP_HEADERS

        /**
         * 403 본문. 사설 주소를 막는 것은 뜻한 것이고, 사용자가 가장 흔히 밟는 경우는 같은 폰의 파일 서버에 프록시를 거쳐 붙으려다 이것을 받는
         * 것이라 할 일까지 적는다. 손님의 브라우저에 보이는 글이라 이 모듈에 둔다(앱의 문자열 자원과 따로).
         */
        internal val BLOCKED_DESTINATION_HELP = buildString {
            appendLine("이 프록시는 사설·로컬 주소로 연결하지 않습니다 — 폰과 폰이 붙은 LAN 이 손님에게 드러나지 않게.")
            appendLine()
            appendLine("이 폰의 파일 서버에 붙으려면 192.168.49.* 를 브라우저나 시스템의 프록시 예외 목록에 넣으세요(파일 서버는 바로 붙습니다).")
        }

        /** 502 본문 — 이름을 풀지 못했다(없는 이름·폰의 DNS 실패). 막은 것이 아니다. */
        internal val UNRESOLVED_HELP = buildString {
            appendLine("목적지의 이름을 풀지 못했습니다(이름이 없거나 폰의 DNS 가 답하지 않았습니다).")
            appendLine()
            appendLine("주소를 확인하고 다시 해 보세요. 이 프록시가 막은 것이 아닙니다.")
        }

        /** `host:port`·`[v6]:port`·`host` 를 가른다. 포트가 숫자가 아니면 null. */
        internal fun splitHostPort(hostPort: String, defaultPort: Int): Pair<String, Int>? {
            if (hostPort.startsWith("[")) {
                val end = hostPort.indexOf(']')
                if (end == -1) return null
                val host = hostPort.substring(1, end)
                val rest = hostPort.substring(end + 1)
                val port = if (rest.startsWith(":")) rest.substring(1).toIntOrNull() ?: return null else defaultPort
                return host to port
            }
            val idx = hostPort.lastIndexOf(':')
            return if (idx == -1) {
                hostPort to defaultPort
            } else {
                hostPort.substring(0, idx) to (hostPort.substring(idx + 1).toIntOrNull() ?: return null)
            }
        }

        /**
         * CRLF 로 끝나는 머리 한 줄을 바이트 단위로 읽는다 — 그래야 요청 본문이 스트림에 남아 relay() 가 원격에 그대로 넘긴다.
         * 홀로 선 CR(뒤에 LF 가 없는 CR)은 거절한다 — 그대로 두면 `.trim()` 은 양 끝만 다듬으므로 줄 가운데의 CR 이 살아남고, 머리를 ISO-8859-1
         * 바이트 그대로 원격에 다시 쓰므로 `X-A: foo\rEvil: 1` 이 그대로 가서 홀로 선 CR 을 줄 끝으로 보는 서버가 머리를 하나 더 본다(머리 주입).
         * 지금은 손님이 이미 CONNECT 로 아무 바이트나 쓸 수 있어 권한이 오르지 않지만, CONNECT 를 막거나 인증을 붙이는 순간 샛길이 된다.
         */
        internal fun readHeaderLine(src: InputStream): String? {
            val sb = StringBuilder()
            while (true) {
                if (sb.length >= MAX_HEADER_LINE_BYTES) throw HeaderTooLargeException()
                val b = src.read()
                if (b == -1) return if (sb.isEmpty()) null else sb.toString()
                when (b) {
                    '\r'.code -> {
                        val next = src.read()
                        if (next == '\n'.code || next == -1) return sb.toString()
                        throw MalformedRequestException()
                    }
                    '\n'.code -> return sb.toString()
                    else -> sb.append(b.toChar())
                }
            }
        }
    }
}

/** 머리의 길이·개수 상한을 넘었다 — 431 로 답하고 닫는다. */
internal class HeaderTooLargeException : Exception()

/** 머리 문법이 틀렸다(홀로 선 CR·스머글링 조합) — 400 으로 답하고 닫는다. */
internal class MalformedRequestException : Exception()
