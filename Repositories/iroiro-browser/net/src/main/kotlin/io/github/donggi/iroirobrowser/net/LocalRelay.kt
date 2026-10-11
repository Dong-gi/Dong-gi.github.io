package io.github.donggi.iroirobrowser.net

import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.Semaphore
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * **앱 안 중계 프록시 — 이 앱의 유일한 네트워크 길.** 기기 안(127.0.0.1)의 평문 HTTP 프록시로 서서, 받은 요청을 원격 프록시([upstream])로
 * 넘긴다 — 원격 프록시까지는 TLS 로 감싸고 Basic 계정을 붙인다. WebView 와 앱의 HTTP(내려받기·출구 확인)가 모두 이것을 거친다.
 *
 * ## 왜 있는가
 *
 * 처음에는 WebView 가 'TLS 로 감싼 프록시 + Basic 인증' 을 다루지 못할 때를 위한 것이었다(`ERR_PROXY_AUTH_UNSUPPORTED` 로 끝났다는
 * 2023년 보고). 실험(WebView 153)에서는 **WebView 가 그것을 다룬다** — 그래도 이 중계를 쓰는 까닭이 둘 남았다(사용자 결정, 2026-10-04):
 *
 * - **앱의 HTTP**(`HttpURLConnection` — 내려받기·출구 확인)는 TLS 로 감싼 프록시를 모른다. 이 중계를 평문 프록시로 쓴다.
 * - **원격 계정을 WebView 에 주지 않는다.** WebView 의 인증 콜백은 host 와 realm 만 주므로, 프록시와 같은 이름의 사이트가 같은 realm 으로
 *   401 을 내면 프록시의 407 과 가릴 수 없다 — 이름으로 답하면 사이트가 프록시 계정을 받는다(실측). 중계를 거치면 WebView 는 세션마다 바뀌는
 *   중계 계정만 알고, 그것도 무작위 [realm] 으로 가린다.
 *
 * ## 지키는 것
 *
 * - **다른 앱이 쓰지 못한다.** 루프백의 포트는 기기의 모든 앱이 열 수 있다. 세션마다 무작위 계정([sessionUser]·[sessionPassword])을
 *   요구하고(407), 그 계정은 이 앱만 안다 — 아니면 다른 앱이 사용자의 프록시 계정으로 밖에 나간다. 동시 연결도 [MAX_CLIENTS] 로 묶는다.
 * - **다른 앱이 자리를 다 차지하지 못한다.** 자리([MAX_CLIENTS])는 **계정을 확인한 뒤에** 잡는다. 계정을 보이기 전의 손님은 따로 된 작은
 *   자리([MAX_PENDING])에서 [AUTH_TIMEOUT_MS] 안에 계정을 보여야 하고, 그 자리가 차면 가장 오래 기다린 손님을 내보낸다 — 같은 기기의 앱이
 *   아무 바이트도 보내지 않는 연결로 자리를 채워 브라우저를 막지 못하게(앞 판은 받자마자 자리를 잡아, 그런 연결 256 개가 30초씩 모든 길을
 *   막을 수 있었다). 이 앱의 손님은 받자마자 머리를 보내므로(데운 뒤에는 계정까지) 기다리는 자리에 머무는 때가 짧다.
 * - **닫으면 이어져 있던 연결도 끊는다**([close]) — 손님과 원격 둘 다. 길을 바꾼 뒤에 옛 중계를 지나는 터널(웹소켓·HTTP/2)이 남지 않게.
 * - **직접 연결로 새지 않는다.** 원격 프록시에 닿지 못하거나(502 `upstream-unreachable`) 계정이 거절되면(502 `upstream-auth`) 그것으로
 *   끝낸다. 원격이 정해지지 않았으면([upstream] 이 null) 모든 요청에 502 `not-configured`. 목적지에 직접 붙는 길은 이 클래스에 없다.
 * - **원격 프록시의 이름을 확인한다.** TLS 의 인증서를 플랫폼의 신뢰 저장소로 확인하고, 이름은 `HTTPS` 규칙과 [hostnameVerifier] 로
 *   두 번 본다.
 * - **원격의 407 을 손님에게 그대로 넘기지 않는다** — 넘기면 WebView 가 중계에게 원격의 realm 으로 계정을 물어 고리가 생긴다. 502 로 바꾼다.
 * - **원격 계정을 로그에 적지 않는다**([UpstreamProxy.toString]).
 *
 * ## 데우기
 *
 * 서비스 워커가 내는 요청은 WebView 가 407 에 답하지 못한다 — 인증 캐시가 먼저 차 있어야 한다(실험 sw-cold). [WARMUP_URL] 은 이 중계가
 * **스스로 204 로 답하는 주소**다(계정을 확인한 뒤, 원격에 아무것도 보내지 않는다). 숨은 WebView 로 이것을 한 번 열면 WebView 가 중계의
 * 407 에 답하며 캐시를 채운다 — 바깥으로 나가는 요청도, 이름 풀이도 없다(`.invalid` 는 RFC 6761 의 쓰지 않는 이름이다).
 *
 * 연결 하나에 요청 하나다(원격과는 `Connection: close`). 손님과의 연결은 407 을 주고받는 동안만 이어 쓴다.
 *
 * 중계가 스스로 만든 오류 응답(502 등)에는 [errorPage] 가 주는 짧은 쪽을 싣는다 — 비어 있으면 http 쪽이 하얀 빈 화면이 된다. 이 모듈은 화면
 * 문구를 만들지 않는다(앱이 준다).
 */
class LocalRelay(
    /** 원격 프록시. null 이면 '아직 정하지 않음' — 모든 요청을 502 로 막는다. */
    private val upstream: UpstreamProxy?,
    /** 원격 프록시까지 TLS 를 여는 것. 원격이 평문이면(대조군·JVM 시험) null 이어도 된다. */
    private val tlsFactory: SSLSocketFactory?,
    /** 원격 프록시의 이름을 한 번 더 확인한다(안드로이드: `HttpsURLConnection.getDefaultHostnameVerifier()`). */
    private val hostnameVerifier: HostnameVerifier?,
    private val log: (String) -> Unit = {},
    private val listener: Listener = Listener.NONE,
    /** 계정을 확인한 손님의 동시 상한([MAX_CLIENTS]). 시험이 작게 준다. */
    maxClients: Int = MAX_CLIENTS,
    /** 계정을 아직 보이지 않은 손님의 동시 상한([MAX_PENDING]) — 넘으면 가장 오래 기다린 것을 내보낸다. 시험이 작게 준다. */
    private val maxPending: Int = MAX_PENDING,
    /** 받은 때부터 계정이 실린 머리가 다 오기까지의 시간([AUTH_TIMEOUT_MS]). 시험이 짧게 준다. */
    private val authTimeoutMs: Int = AUTH_TIMEOUT_MS,
    /**
     * 중계가 스스로 만든 오류 응답(400 이상, 407 은 빼고)의 몸 — HTML 글. 받는 것은 상태 번호와 까닭([MARKER] 의 값). null 이면 몸 없이.
     * 중계의 스레드에서 불린다.
     */
    private val errorPage: (status: Int, reason: String) -> String? = { _, _ -> null },
    /**
     * [close] 가 열린 소켓을 닫는 일을 맡기는 곳 — 기본은 따로 된 데몬 스레드. TLS 소켓을 닫으면 close_notify 를 쓰는데(네트워크 쓰기), 부른 쪽이
     * 메인 스레드면 안드로이드가 NetworkOnMainThreadException 으로 앱을 죽인다(프록시 스위치를 끌 때 그랬다). 망이 죽어 있으면 막힐 수도 있다.
     * 시험이 바꿔 낀다.
     */
    private val closer: (Runnable) -> Unit = { task -> Thread(task, "iroiro-relay-close").apply { isDaemon = true }.start() },
) : Closeable {

    /** 원격과의 사이에 생긴 일 — 화면의 상태 표시가 쓴다. 중계의 스레드에서 불린다. */
    interface Listener {
        fun onUpstream(event: UpstreamEvent) {}

        /**
         * [WARMUP_URL] 에 계정과 함께 왔다 — 그 손님(WebView)의 인증 캐시가 찼다. [tag] 는 주소의 `?` 뒤(없으면 빈 글) — 프로필마다 따로
         * 데울 때 어느 것이 왔는지 가린다(인증 캐시는 프로필마다 따로다).
         */
        fun onWarmedUp(tag: String) {}

        companion object {
            val NONE = object : Listener {}
        }
    }

    enum class UpstreamEvent {
        /** 원격이 터널을 열었거나 요청에 답했다(407 이 아닌 답). */
        REACHED,

        /** 원격이 계정을 거절했다(407). */
        AUTH_REJECTED,

        /** 원격에 닿지 못했다(연결·TLS·이름 확인 실패). */
        UNREACHABLE,
    }

    /** 이 세션의 중계 계정 — 이 앱만 안다. WebView 는 407 을 받고 앱에 물어 이것을 넣는다. */
    val sessionUser: String = token()
    val sessionPassword: String = token()

    /**
     * 이 세션의 realm — 무작위다. WebView 의 인증 콜백은 host 와 realm 만 주고(포트도, 프록시인지 사이트인지도 주지 않는다),
     * 같은 기기의 다른 앱이 127.0.0.1 의 어느 포트에서 401 을 내며 고정 realm 을 대면 앱이 세션 계정을 그쪽에 넘길 수 있다.
     * realm 을 이 중계만 알게 하면 콜백이 중계의 것인지 가릴 수 있다.
     */
    val realm: String = REALM_PREFIX + token().take(12)

    val isConfigured: Boolean get() = upstream != null

    private val expectedAuthorization = UpstreamProxy.basic(sessionUser, sessionPassword)
    private val server = ServerSocket()
    private val pool: ExecutorService = Executors.newCachedThreadPool { r -> Thread(r, "iroiro-relay").apply { isDaemon = true } }

    /** 계정을 확인한 손님의 자리. */
    private val slots = Semaphore(maxClients)

    /** 계정을 아직 보이지 않은 손님 — 받은 차례(가장 오래 기다린 것이 앞). 자물쇠는 이 집합 자신. */
    private val pending = LinkedHashSet<Socket>()

    /** 열려 있는 연결 모두(손님·원격) — [close] 가 끊는다. */
    private val open: MutableSet<Socket> = ConcurrentHashMap.newKeySet()

    @Volatile
    private var closed = false

    /** 원격 프록시의 주소 가운데 마지막으로 붙은 것 — 다음 연결이 먼저 해 본다([connectUpstream]). */
    @Volatile
    private var lastGood: InetAddress? = null

    /** 듣는 포트(127.0.0.1). [start] 뒤에 뜻이 있다. */
    val port: Int get() = server.localPort

    fun start(): LocalRelay {
        server.bind(InetSocketAddress(LOOPBACK, 0), BACKLOG)
        pool.execute(::acceptLoop)
        log("relay listening 127.0.0.1:$port → ${upstream ?: "(not configured)"}")
        return this
    }

    /**
     * 닫는다 — 더 받지 않고, **이어져 있던 연결도 모두 끊는다**(손님·원격). 소켓 읽기는 일꾼을 내려도(인터럽트) 풀리지 않으므로 소켓을 직접
     * 닫는다. 그러지 않으면 길을 바꾼 뒤에도 열려 있던 터널(웹소켓·HTTP/2)이 옛 원격으로 계속 오갔다. 듣는 소켓만 여기서 닫고(네트워크에 쓰지
     * 않는다), 연결을 끊는 일은 [closer] 가 한다 — 부른 스레드(메인)에서 하지 않는다.
     */
    override fun close() {
        closed = true
        runCatching { server.close() }
        // 맡긴 일이 돌 때의 목록을 닫는다 — 그 사이 열린 연결까지.
        closer(Runnable {
            open.forEach { closeQuietly(it) }
            pool.shutdownNow()
        })
    }

    private fun acceptLoop() {
        while (!closed) {
            val client = try {
                server.accept()
            } catch (e: IOException) {
                if (closed) return else continue
            }
            admit(client)
            try {
                pool.execute { handle(client) }
            } catch (e: RejectedExecutionException) {
                // 받는 사이에 [close] 가 일꾼을 내렸다 — 받은 손님을 닫고 끝낸다. 던지면 이 스레드가 죽으며 **앱이 함께 죽는다**
                // (2026-10-04 기기에서 한 번 — 0.6.0).
                forget(client)
                closeQuietly(client)
                return
            }
        }
    }

    /**
     * 계정을 보이기 전의 손님으로 들인다. 그 자리([maxPending])가 차 있으면 **가장 오래 기다린 손님을 내보낸다** — 받지 않고 버티는 연결이
     * 새 손님을 막지 못하게(이 앱의 손님은 받자마자 머리를 보내므로 앞에 서 있는 것은 대개 버티는 연결이다).
     */
    private fun admit(client: Socket) {
        open += client
        if (closed) closeQuietly(client)
        val evicted = ArrayList<Socket>()
        synchronized(pending) {
            while (pending.size >= maxPending) {
                val oldest = pending.first()
                pending.remove(oldest)
                evicted += oldest
            }
            pending += client
        }
        if (evicted.isNotEmpty()) log("relay evicted ${evicted.size} unauthenticated client(s)")
        // 닫으면 그 손님의 일꾼은 읽기에서 깨어 끝난다(자리를 잡지 않았으니 돌려줄 것도 없다).
        evicted.forEach { closeQuietly(it) }
    }

    /** 계정을 확인했다 — 기다리는 자리에서 뺀다. 그 사이 내보내졌으면(닫혔다) false. */
    private fun authenticated(client: Socket): Boolean = synchronized(pending) { pending.remove(client) }

    private fun forget(socket: Socket) {
        synchronized(pending) { pending.remove(socket) }
        open -= socket
    }

    private fun handle(client: Socket) {
        var slot = false
        try {
            if (closed) return
            // 작은 조각을 모아 보내지 않는다(Nagle 끄기) — 아래 [connectUpstream] 의 머리말.
            client.tcpNoDelay = true
            // 계정이 실린 머리가 오기까지는 받은 때부터 [authTimeoutMs] 만 기다린다 — 한 바이트씩 흘려 자리를 붙드는 손님도 끊긴다.
            val input = DeadlineInput(client, System.nanoTime() + authTimeoutMs * 1_000_000L)
            val output = client.getOutputStream()
            while (true) {
                val head = HttpHead.read(input) ?: return
                val parts = head.startLine.split(' ')
                if (parts.size != 3 || !parts[2].startsWith("HTTP/1.")) {
                    respond(output, 400, "bad-request")
                    return
                }
                val method = parts[0]
                val target = parts[1]
                val auth = head.header("Proxy-Authorization")
                if (auth != expectedAuthorization) {
                    log("relay 407 $method $target (${if (auth == null) "no credentials" else "wrong credentials"})")
                    // 몸이 딸린 요청이면 연결을 이어 쓸 수 없다(몸을 읽어 버려야 한다) — 닫는다. 손님은 새 연결로 다시 온다.
                    val hasBody = (head.header("Content-Length")?.toLongOrNull() ?: 0L) > 0L || head.header("Transfer-Encoding") != null
                    val keepAlive = !hasBody && head.extra.isEmpty()
                    respond407(output, keepAlive)
                    if (keepAlive) continue else return
                }
                // 계정을 확인했다 — 이제야 자리를 잡는다.
                if (!authenticated(client)) return
                if (!slots.tryAcquire()) {
                    // 이 앱의 WebView 는 프록시당 32 연결을 넘지 않는다 — 넘는 것은 무언가 어긋났다.
                    respond(output, 503, "busy")
                    return
                }
                slot = true
                client.soTimeout = HEAD_TIMEOUT_MS
                if (isWarmup(method, target)) {
                    log("relay warm-up")
                    respond(output, 204, "warm-up")
                    listener.onWarmedUp(target.substringAfter('?', ""))
                    return
                }
                val up = upstream
                if (up == null) {
                    respond(output, 502, "not-configured")
                    return
                }
                log("relay accepted $method $target")
                when {
                    method == "CONNECT" -> tunnel(client, head, target, up)
                    target.startsWith("http://") -> forward(client, head, target, up)
                    else -> respond(output, 400, "unsupported-target")
                }
                return
            }
        } catch (e: HttpHead.TooLarge) {
            runCatching { respond(client.getOutputStream(), 431, "head-too-large") }
        } catch (e: IOException) {
            // 손님이 떠났다(또는 계정을 보이지 않은 채 시간을 넘겼거나 내보내졌다). 할 일이 없다.
            log("relay client gone (${e.javaClass.simpleName})")
        } finally {
            if (slot) slots.release()
            forget(client)
            closeQuietly(client)
        }
    }

    private fun isWarmup(method: String, target: String): Boolean =
        method == "GET" && (target == WARMUP_URL || target.startsWith("$WARMUP_URL?"))

    /** CONNECT — 원격에 같은 CONNECT 를 계정과 함께 보내고, 200 이면 두 연결을 잇는다. */
    private fun tunnel(client: Socket, head: HttpHead, target: String, upstream: UpstreamProxy) {
        val out = client.getOutputStream()
        val up = openUpstream(out, "CONNECT $target", upstream) ?: return
        try {
            val upOut = up.getOutputStream()
            upOut.write(
                ("CONNECT $target HTTP/1.1\r\nHost: $target\r\nProxy-Authorization: ${upstream.authorization}\r\n" +
                    "Proxy-Connection: keep-alive\r\n\r\n").toByteArray(Charsets.ISO_8859_1),
            )
            upOut.flush()
            val reply = HttpHead.read(up.getInputStream())
            // 원격이 답하는 사이 중계가 닫혔다 — 터널을 열지 않는다. 연결을 끊는 일은 [closer] 가 따로 하므로 원격의 답이 그보다 먼저 올 수 있다.
            if (closed) return
            if (reply == null) {
                log("relay CONNECT $target → upstream closed")
                respond(out, 502, "upstream-closed")
                return
            }
            if (reply.status != 200) {
                log("relay CONNECT $target → upstream ${reply.status}")
                if (reply.status == 407) {
                    listener.onUpstream(UpstreamEvent.AUTH_REJECTED)
                } else {
                    // 원격이 이 목적지만 거절했다(Squid 의 SSL_ports 등) — 원격에는 닿았다.
                    listener.onUpstream(UpstreamEvent.REACHED)
                }
                respond(out, 502, if (reply.status == 407) "upstream-auth" else "upstream-status-${reply.status}")
                return
            }
            log("relay CONNECT $target → upstream 200")
            listener.onUpstream(UpstreamEvent.REACHED)
            out.write("HTTP/1.1 200 Connection established\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
            if (reply.extra.isNotEmpty()) out.write(reply.extra)
            out.flush()
            if (head.extra.isNotEmpty()) {
                upOut.write(head.extra)
                upOut.flush()
            }
            client.soTimeout = 0
            up.soTimeout = 0
            pipe(client, up)
        } finally {
            closeQuietly(up)
            open -= up
        }
    }

    /** 절대 주소 요청(`GET http://…`) — 손님의 프록시 머리를 떼고 원격 계정을 붙여 넘긴다. 응답의 끝은 원격이 닫을 때다. */
    private fun forward(client: Socket, head: HttpHead, target: String, upstream: UpstreamProxy) {
        val out = client.getOutputStream()
        val up = openUpstream(out, "${head.startLine.substringBefore(' ')} $target", upstream) ?: return
        try {
            val request = StringBuilder()
            request.append(head.startLine).append("\r\n")
            for ((name, value) in head.headers) {
                if (HOP_BY_HOP.any { it.equals(name, ignoreCase = true) }) continue
                request.append(name).append(": ").append(value).append("\r\n")
            }
            request.append("Proxy-Authorization: ").append(upstream.authorization).append("\r\n")
            request.append("Connection: close\r\n\r\n")
            val upOut = up.getOutputStream()
            upOut.write(request.toString().toByteArray(Charsets.ISO_8859_1))
            if (head.extra.isNotEmpty()) upOut.write(head.extra)
            upOut.flush()
            // 원격의 407 은 그대로 넘기지 않는다(클래스 머리말). 몸이 있는 요청이면 원격이 머리를 보내기 전에 몸을 기다릴 수 있어
            // 몸을 먼저 흘려보내며 응답 머리를 읽는다.
            val pump = try {
                pool.submit { copy(client.getInputStream(), upOut) }
            } catch (e: RejectedExecutionException) {
                // 중계가 닫혔다([close] 가 일꾼을 내렸다) — 넘기지 않고 끝낸다. 던지면 일꾼 스레드가 죽으며 앱이 함께 죽는다([pipe] 와 같다).
                return
            }
            val reply = HttpHead.read(up.getInputStream())
            if (closed) {
                // 원격이 답하는 사이 중계가 닫혔다 — 그 답을 손님에게 넘기지 않는다([tunnel] 과 같다).
                pump.cancel(true)
                return
            }
            if (reply == null) {
                respond(out, 502, "upstream-closed")
                pump.cancel(true)
                return
            }
            log("relay ${head.startLine.substringBefore(' ')} $target → upstream ${reply.status}")
            if (reply.status == 407) {
                listener.onUpstream(UpstreamEvent.AUTH_REJECTED)
                respond(out, 502, "upstream-auth")
                pump.cancel(true)
                return
            }
            listener.onUpstream(UpstreamEvent.REACHED)
            out.write((reply.startLine + "\r\n").toByteArray(Charsets.ISO_8859_1))
            for ((name, value) in reply.headers) out.write("$name: $value\r\n".toByteArray(Charsets.ISO_8859_1))
            out.write("\r\n".toByteArray(Charsets.ISO_8859_1))
            if (reply.extra.isNotEmpty()) out.write(reply.extra)
            out.flush()
            client.soTimeout = 0
            up.soTimeout = 0
            copy(up.getInputStream(), out)
            pump.cancel(true)
        } finally {
            closeQuietly(up)
            open -= up
        }
    }

    /**
     * 원격 프록시에 연결한다. 닿지 못하면 손님에게 502 를 주고 null — **직접 연결로 넘어가지 않는다.** 돌려준 연결은 [open] 에 들어 있다
     * (부르는 쪽이 닫고 뺀다 — TLS 면 감싼 것만 남는다. 감싼 것을 닫으면 밑의 연결도 닫힌다).
     */
    private fun openUpstream(clientOut: OutputStream, what: String, upstream: UpstreamProxy): Socket? {
        var raw: Socket? = null
        var ssl: SSLSocket? = null
        try {
            raw = connectUpstream(upstream)
            raw.soTimeout = HEAD_TIMEOUT_MS
            if (!upstream.tls) return raw
            val factory = tlsFactory ?: throw IOException("no TLS factory for an https proxy")
            val wrapped = factory.createSocket(raw, upstream.host, upstream.port, true) as SSLSocket
            ssl = wrapped
            open += wrapped
            open -= raw
            val params = wrapped.sslParameters
            params.endpointIdentificationAlgorithm = "HTTPS"
            if (!isIpLiteral(upstream.host)) params.serverNames = listOf(SNIHostName(upstream.host))
            wrapped.sslParameters = params
            wrapped.soTimeout = HEAD_TIMEOUT_MS
            wrapped.startHandshake()
            val verifier = hostnameVerifier
            if (verifier != null && !verifier.verify(upstream.host, wrapped.session)) {
                throw SSLPeerUnverifiedException("proxy name does not match its certificate")
            }
            return wrapped
        } catch (e: IOException) {
            for (s in listOfNotNull(ssl, raw)) {
                closeQuietly(s)
                open -= s
            }
            // 중계를 닫아 끊긴 것이면 원격의 일이 아니다 — 알리지 않는다.
            if (closed) return null
            log("relay $what → upstream unreachable (${e.javaClass.simpleName})")
            listener.onUpstream(UpstreamEvent.UNREACHABLE)
            runCatching { respond(clientOut, 502, "upstream-unreachable") }
            return null
        }
    }

    /**
     * 원격 프록시에 TCP 로 붙는다.
     *
     * - **Nagle 을 끈다**(`TCP_NODELAY`). 터널은 작은 조각(TLS 레코드·HTTP/2 프레임)을 잇달아 쓰는데, 켜 두면 앞 조각의 ACK 가 올 때까지
     *   뒤 조각을 붙들어 주고받기마다 왕복 한 번(해외 프록시면 0.1~0.3초)이 더 든다. 크로미움도 프록시 연결에서 끈다. 루프백의 실험실에서는
     *   왕복이 0 이라 보이지 않았다.
     * - **이름이 주소 여럿으로 풀리면 차례로 해 본다.** 셀룰러는 IPv6 를 먼저 주는데 프록시가 IPv4 로만 받으면 첫 주소에서 끝나 버린다.
     *   마지막으로 붙은 주소를 먼저 해 보고, 뒤에 해 볼 주소가 남은 동안은 짧게 기다린다.
     */
    private fun connectUpstream(upstream: UpstreamProxy): Socket {
        val resolved = InetAddress.getAllByName(upstream.host).toList()
        val good = lastGood
        val ordered = if (good != null && good in resolved) listOf(good) + (resolved - good) else resolved
        var failure: IOException? = null
        for ((i, address) in ordered.withIndex()) {
            if (closed) throw IOException("relay closed")
            // 붙는 중에도 [close] 가 끊을 수 있게 먼저 적어 둔다.
            val socket = Socket()
            open += socket
            try {
                socket.tcpNoDelay = true
                val timeout = if (i == ordered.lastIndex) CONNECT_TIMEOUT_MS else NEXT_ADDRESS_TIMEOUT_MS
                socket.connect(InetSocketAddress(address, upstream.port), timeout)
                lastGood = address
                return socket
            } catch (e: IOException) {
                closeQuietly(socket)
                open -= socket
                failure = e
            }
        }
        throw failure ?: IOException("no address for the proxy")
    }

    /** 두 연결을 잇는다. 한쪽이 끝나면 둘 다 닫는다(TLS 소켓은 반만 닫을 수 없다). */
    private fun pipe(a: Socket, b: Socket) {
        val other = try {
            pool.submit {
                copy(b.getInputStream(), a.getOutputStream())
                closeQuietly(a)
                closeQuietly(b)
            }
        } catch (e: RejectedExecutionException) {
            // 터널을 여는 사이 중계가 닫혔다(설정을 바꿨다 — [close] 가 일꾼을 내렸다). 잇지 않고 둘 다 닫는다. 던지면 일꾼 스레드가 죽으며
            // **앱이 함께 죽었다**(2026-10-04 기기에서 한 번 — 0.8.0, 앱이 뒤에 있을 때).
            closeQuietly(a)
            closeQuietly(b)
            return
        }
        copy(a.getInputStream(), b.getOutputStream())
        closeQuietly(a)
        closeQuietly(b)
        other.cancel(true)
    }

    private fun copy(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(16 * 1024)
        try {
            while (true) {
                val n = input.read(buffer)
                if (n < 0) return
                output.write(buffer, 0, n)
                output.flush()
            }
        } catch (e: IOException) {
            // 한쪽이 닫혔다.
        }
    }

    private fun respond407(out: OutputStream, keepAlive: Boolean) {
        out.write(
            ("HTTP/1.1 407 Proxy Authentication Required\r\nProxy-Authenticate: Basic realm=\"$realm\"\r\n" +
                "Content-Length: 0\r\nConnection: ${if (keepAlive) "keep-alive" else "close"}\r\n\r\n").toByteArray(Charsets.ISO_8859_1),
        )
        out.flush()
    }

    /**
     * 중계가 스스로 만든 응답. 오류면 [errorPage] 의 쪽을 싣는다 — 몸이 비면 http 쪽은 하얀 빈 화면이 된다(https 의 터널 실패는 WebView 가 제
     * 오류 화면을 그리므로 몸을 읽지 않는다). 그 쪽은 목적지의 출처에서 열리므로 스크립트·바깥 자원을 막는다(CSP).
     */
    private fun respond(out: OutputStream, status: Int, reason: String) {
        val page = if (status >= 400) runCatching { errorPage(status, reason) }.getOrNull() else null
        val body = page?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
        val type = if (page != null) "Content-Type: text/html; charset=utf-8\r\n$PAGE_POLICY\r\n" else ""
        out.write(
            ("HTTP/1.1 $status ${reasonPhrase(status)}\r\n$MARKER: $reason\r\n$type" +
                "Content-Length: ${body.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n").toByteArray(Charsets.ISO_8859_1),
        )
        if (body.isNotEmpty()) out.write(body)
        out.flush()
    }

    /**
     * 계정을 보이기 전의 읽기 — 받은 때부터 [deadline](`System.nanoTime`)까지만 기다린다. 읽을 때마다 남은 시간을 소켓의 시간 제한으로 건다
     * (한 번 읽기마다의 제한이면 한 바이트씩 흘리는 손님이 머리의 상한까지 오래 버틴다).
     */
    private class DeadlineInput(private val socket: Socket, private val deadline: Long) : InputStream() {
        private val input = socket.getInputStream()

        override fun read(): Int {
            arm()
            return input.read()
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            arm()
            return input.read(b, off, len)
        }

        private fun arm() {
            val left = (deadline - System.nanoTime()) / 1_000_000L
            if (left <= 0) throw SocketTimeoutException("no credentials in time")
            socket.soTimeout = left.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
    }

    companion object {
        /** 중계가 직접 만든 응답에 다는 머리 — 까닭을 싣는다(앱과 실험이 읽는다). */
        const val MARKER = "X-Iroiro-Relay"
        const val REALM_PREFIX = "iroiro-relay-"

        /** 중계가 스스로 답하는 데우기 주소(클래스 머리말의 '데우기'). */
        const val WARMUP_URL = "http://warmup.iroiro.invalid/"

        /** 계정을 확인한 손님의 동시 상한. WebView 는 프록시 하나에 32 연결을 넘지 않는다(Chromium 의 기본값). */
        const val MAX_CLIENTS = 256

        /**
         * 계정을 아직 보이지 않은 손님의 동시 상한 — 넘으면 가장 오래 기다린 것을 내보낸다. WebView 가 한꺼번에 여는 연결(프록시당 32)과
         * 앱의 HTTP 몇을 넉넉히 덮는다.
         */
        const val MAX_PENDING = 64

        /**
         * 받은 때부터 계정이 실린 머리가 다 오기까지. 이 앱의 손님은 곧바로 보낸다 — 407 에 답하는 동안(WebView 가 앱의 메인 스레드에 묻는다)이나
         * 크로미움이 미리 열어 둔 연결도 이 안에 들거나, 넘으면 크로미움이 새 연결로 다시 온다.
         */
        const val AUTH_TIMEOUT_MS = 5_000

        private const val BACKLOG = 50

        /** 오류 쪽에 거는 보안 정책 — 목적지의 출처에서 열리므로 아무것도 불러오거나 돌리지 못하게(글꼴·그림도). 꾸밈은 쪽 안의 것만. */
        private const val PAGE_POLICY = "Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'"

        /** 셀룰러로 해외 프록시에 붙는다 — 넉넉히. */
        private const val CONNECT_TIMEOUT_MS = 15_000

        /** 해 볼 주소가 더 남았을 때 한 주소를 기다리는 시간 — 막힌 IPv6 하나가 연결마다 15초를 먹지 않게. */
        private const val NEXT_ADDRESS_TIMEOUT_MS = 3_000
        private const val HEAD_TIMEOUT_MS = 30_000

        /** 손님과 이 중계 사이에서만 뜻이 있는 머리. 원격으로 넘기지 않는다. */
        private val HOP_BY_HOP = listOf("Proxy-Authorization", "Proxy-Connection", "Connection", "Keep-Alive")

        private val LOOPBACK: InetAddress = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))

        private val random = SecureRandom()

        private fun token(): String {
            val bytes = ByteArray(16)
            random.nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }

        private fun reasonPhrase(status: Int): String = when (status) {
            204 -> "No Content"
            400 -> "Bad Request"
            431 -> "Request Header Fields Too Large"
            502 -> "Bad Gateway"
            503 -> "Service Unavailable"
            else -> "Error"
        }

        internal fun isIpLiteral(host: String): Boolean =
            host.contains(':') || host.matches(Regex("""\d{1,3}(\.\d{1,3}){3}"""))

        private fun closeQuietly(socket: Socket) {
            try {
                socket.close()
            } catch (e: Exception) {
                // 이미 닫혔거나(IOException) TLS 의 마무리 쓰기가 실패했다(런타임 예외) — 어느 쪽이든 그 스레드를 죽이지 않는다(안드로이드는 어느
                // 스레드에서든 잡히지 않은 예외로 프로세스를 죽인다).
            }
        }
    }
}
