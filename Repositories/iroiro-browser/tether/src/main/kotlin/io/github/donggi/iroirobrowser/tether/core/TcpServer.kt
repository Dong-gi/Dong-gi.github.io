package io.github.donggi.iroirobrowser.tether.core

import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * 서버 셋이 함께 쓰는 환경 — 누구를 받고(손님), 어디로 잇고(목적지·바깥 문), 얼마나(스레드 예산·시간), 무엇을 알리는지(기록·바이트·거절·
 * 상한·멈춤). 앱은 실제 판정([PeerFilter]·[DestinationFilter.SYSTEM]·[Outbound.DIRECT])으로 짓고, 시험은 루프백을 받는 것으로 바꾼다.
 */
class TetherEnvironment(
    /** 손님 거르기. 기본값이 없다 — 실제 판정([PeerFilter])은 앱이 안드로이드에서 읽는 것(그룹·SO_MARK)을 넣어야 선다. */
    val peers: PeerGate,
    val destinations: DestinationPolicy = DestinationFilter.SYSTEM,
    val outbound: Outbound = Outbound.DIRECT,
    val budget: ThreadBudget = ThreadBudget(),
    val log: TetherLog = TetherLog.NONE,
    /** 듣는 주소. 실제는 `0.0.0.0`(실기기에서는 듀얼스택 와일드카드 `::` 가 된다 — [PeerFilter] 의 머리말), 시험은 루프백. */
    val bindAddress: InetAddress = InetAddress.getByName("0.0.0.0"),
    /** 시간 상수 — 시험은 짧게 준다. */
    val timings: TetherTimings = TetherTimings(),
    /** 단조 시계(밀리초) — 머리의 마감과 기록 묶기. 시험은 손으로 돌린다. */
    val nowMs: () -> Long = { System.nanoTime() / 1_000_000 },
    val onBytesIn: (Long) -> Unit = {},
    val onBytesOut: (Long) -> Unit = {},
    /** 손님을 거절할 때마다(받기 스레드에서). 앱은 수와 마지막 까닭을 화면에 보인다. */
    val onRejected: (PeerRejection) -> Unit = {},
    /** 상한(동시 연결·스레드 예산·UDP 묶음) 때문에 받지 않은 연결마다 — 손님 거르기와 따로 센다. */
    val onRefused: (Refusal) -> Unit = {},
    /** 돌던 서버의 받기 고리가 죽어 서버를 내렸을 때(받기 스레드에서) — 앱은 상태를 내리고 화면에 알린다. */
    val onServerDied: (TcpServer) -> Unit = {},
)

/** 서버의 시간 상수. 기본값이 실제 값이고 시험은 짧게 준다. */
class TetherTimings(
    /**
     * SOCKS5 의 인사·요청, HTTP 프록시의 요청 머리를 **다 받기까지**의 전체 마감. 읽기 하나의 시한(soTimeout)이 아니다 — 그것만으로는 한
     * 바이트씩 흘리는 손님이 일꾼을 끝없이 쥔다([DeadlineInputStream]). 중계가 시작되면 시한이 없다([ConnectionRegistry] 의 머리말).
     */
    val proxyHeaderMs: Long = 10_000,
    /** 파일 서버의 요청 머리를 다 받기까지의 전체 마감. */
    val fileHeaderMs: Long = 30_000,
    /** 파일 서버가 올리기의 본문을 읽을 때 **읽기 하나**의 시한 — 큰 파일은 오래 걸리므로 본문에는 전체 마감을 두지 않는다. */
    val fileBodyReadMs: Int = 30_000,
    /** 중계의 한 방향이 끝난 뒤 반대 방향을 기다리며 멈춤을 보는 주기([TcpServer.relay]). 기다림 자체에는 시한이 없다. */
    val relayPollMs: Long = 1_000,
)

/** 서버를 세우지 못한(또는 돌던 서버가 내려간) 까닭. 화면의 글은 앱이 고른다(이 모듈은 기계 말만 — 토렌트 엔진과 같은 꼴). */
enum class StartError {
    /**
     * 해 볼 포트를 모두 다른 앱이 쓰고 있다 — SOCKS5 는 1080 하나(옮기지 않는다 — PC 의 런처가 1080 만 쓴다, [Socks5Server] 의 머리말),
     * HTTP 프록시·파일 서버는 기본 포트부터 열 개(8282…8291·8080…8089)가 모두. HTTP·파일이 옮겨 서는 것은 사용자 결정이다(2026-10-10 —
     * 그 위험을 감수했다, [HttpProxyServer]·[FileServer] 의 머리말). 옮겨 선 포트는 [TcpServer.actualPort] 가 말한다.
     */
    PORT_TAKEN,

    /** 이미 멈춘 서버를 다시 세우려 했다 — 서버는 한 번 쓰고 버린다. */
    TERMINATED,

    /** 돌던 서버의 받기 고리가 죽었다 — 듣는 소켓을 닫고(포트를 놓았다) 연결을 끊고 내렸다. 다시 세우면 된다. */
    DIED,
}

/** 상한 때문에 받지 않은(닫은) 연결의 까닭 — 손님 거르기([PeerRejection])와 따로 센다. */
enum class Refusal {
    /** 서버의 동시 연결 상한. */
    SESSION_LIMIT,

    /** 서버 셋이 함께 쓰는 스레드 예산([ThreadBudget]). */
    THREAD_BUDGET,

    /** SOCKS5 의 UDP 묶음 상한([Socks5Server.MAX_UDP_ASSOCIATIONS]). */
    UDP_LIMIT,
}

/**
 * accept 하고 손님을 가르고 일꾼에 넘기는 몸통 — SOCKS5·HTTP 프록시·파일 서버가 함께 쓴다(usb-tether 에서는 셋이 같은 코드를 따로 들고
 * 있었고 파일 서버만 '멈춘 뒤 다시 세우기' 를 막지 않았다). 프록시 둘의 중계([relay])도 여기 하나다.
 *
 * 받는 차례: 손님 거르기([TetherEnvironment.peers]) → 서버의 동시 연결 상한 → 스레드 예산([ThreadBudget]) → 적기([ConnectionRegistry]) →
 * 일꾼. 상한·예산은 **accept 스레드에서** 센다 — 일꾼 안에서 세면 그 사이에 일이 쌓여 캐시 스레드 풀이 스레드를 끝없이 만든다.
 *
 * [start] 는 호출자가 결과를 곧바로 보도록 **그 자리에서 묶고** 나서 accept 를 시작한다. [basePort] 부터 [attempts] 개를 차례로 해 보아
 * 비어 있는 첫 포트에 묶는다 — SOCKS5 는 하나(옮기지 않는다), HTTP 프록시·파일 서버는 열(사용자 결정 2026-10-10). 모두 차 있으면
 * [StartError.PORT_TAKEN]. 실제로 묶인 포트는 [actualPort] — 화면·알림은 기본 상수가 아니라 이것을 보인다(시험의 0 은 아무 빈 포트이고 한
 * 번만 해 본다).
 */
abstract class TcpServer internal constructor(
    protected val env: TetherEnvironment,
    private val tag: String,
    private val basePort: Int,
    /** 해 볼 포트 수 — [basePort] 부터 하나씩 올린다. 1 이면 옮기지 않는다. */
    private val attempts: Int,
    private val maxSessions: Int,
    private val threadsPerSession: Int,
) {
    init {
        require(attempts >= 1) { "attempts=$attempts" }
    }

    private val running = AtomicBoolean(false)

    /**
     * 이미 멈췄는가 — 되돌아가지 않는다. 멈출 때 일꾼 풀을 내리고 적어 둔 연결을 닫았으므로, 다시 세우면 **포트를 잡고 화면에는 정상으로
     * 보이면서 모든 연결을 거절하는** 서버가 된다. 1080 을 쥔 채 아무것도 하지 않는 것은 이 서버가 막으려는 바로 그 상황이라 실패로 둔다.
     * [stop] 의 멱등성도 이것으로 본다 — [running] 으로 보면 받기 고리가 먼저 죽어 running 이 내려간 서버의 stop 이 아무것도 하지 않았다.
     */
    private val terminated = AtomicBoolean(false)

    @Volatile
    private var serverSocket: ServerSocket? = null

    protected val executor: ExecutorService = Executors.newCachedThreadPool(namedThreads(tag))
    internal val connections = ConnectionRegistry()

    /** 거절·상한의 기록 묶기 — 같은 까닭은 초당 한 줄. */
    private val refusalLog = LogThrottle(env.nowMs)

    /** 실제로 묶인 포트. 돌고 있지 않으면 -1. */
    @Volatile
    var actualPort: Int = -1
        private set

    /** 마지막으로 세우지 못한(또는 내려간) 까닭. 돌고 있으면 null. */
    @Volatile
    var lastError: StartError? = null
        private set

    /** 그 자리에서 묶고 accept 를 시작한다. 묶인 포트, 못 묶었으면 -1([lastError]). */
    fun start(): Int {
        if (terminated.get()) {
            lastError = StartError.TERMINATED
            env.log.e(tag, "멈춘 서버를 다시 세우려 했다")
            return -1
        }
        if (!running.compareAndSet(false, true)) return actualPort
        val socket = bind() ?: run {
            running.set(false)
            return -1
        }
        serverSocket = socket
        if (terminated.get()) {
            // 묶는 사이에 stop() 이 지나갔다 — 그때 serverSocket 은 아직 비어 있어 닫히지 않았다.
            closeQuietly(socket)
            lastError = StartError.TERMINATED
            return -1
        }
        actualPort = socket.localPort
        lastError = null
        env.log.i(tag, "포트 $actualPort 에서 듣는다")
        thread(name = "$tag-accept", isDaemon = true) { acceptLoop(socket) }
        return actualPort
    }

    /**
     * 멈춘다. 듣는 소켓만 닫아서는 모자라다 — `read()` 에 막힌 일꾼은 인터럽트를 무시하므로 열린 연결을 직접 닫아야 중계가 실제로 끝난다.
     * 차례가 중요하다: 새 연결을 먼저 막고(듣는 소켓), 열린 연결을 끊고, 일꾼 풀을 내린다. 몇 번 불러도 한 번만 한다.
     */
    fun stop() {
        shutdown()
    }

    /** [stop] 의 몸. 이번에 실제로 내렸으면 true(먼저 내린 쪽이 있으면 false). */
    private fun shutdown(): Boolean {
        if (!terminated.compareAndSet(false, true)) return false
        running.set(false)
        closeQuietly(serverSocket)
        connections.closeAll()
        executor.shutdownNow()
        onStopped()
        actualPort = -1
        return true
    }

    /** 지금 열린 연결 수(화면·시험용). */
    val sessionCount: Int get() = connections.size

    /** 연결 하나를 처리한다(일꾼 스레드). 끝나면 몸통이 적은 것에서 빼고 예산을 돌려주고 닫는다. */
    protected abstract fun handle(client: Socket)

    /** [stop] 의 끝에 — 서버마다 따로 쥔 것(이름 풀이 캐시 등)을 비운다. */
    protected open fun onStopped() {}

    protected val isRunning: Boolean get() = running.get()

    /**
     * 서버가 따로 띄우는 스레드(중계의 반대 방향·UDP 두 방향). **잡히지 않은 것이 스레드 밖으로 나가면 안드로이드는 프로세스를 죽인다** — 테더
     * 프로세스가 죽으면 PC 의 인터넷이 끊긴다. 일꾼([executor] 의 submit)은 예외를 Future 에 삼키므로 같은 수준으로 맞춘다(Throwable 까지 —
     * 스레드 하나의 OutOfMemoryError 로 프로세스를 내리지 않고 그 중계만 끝낸다).
     */
    protected fun background(name: String, body: () -> Unit): Thread = thread(isDaemon = true, name = name) {
        try {
            body()
        } catch (t: Throwable) {
            env.log.w(tag, "$name 스레드가 멈췄다: ${t.javaClass.simpleName}")
        }
    }

    /**
     * 상한 때문에 닫는 연결을 센다 — 기록은 같은 까닭끼리 초당 한 줄로 묶고, 수는 앱이 화면에 보인다.
     * SOCKS5 의 UDP 묶음 상한도 이것으로 센다.
     */
    protected fun noteRefusal(refusal: Refusal, message: String) {
        refusalLog.note("refusal-$refusal") { skipped -> env.log.w(tag, message + batched(skipped)) }
        env.onRefused(refusal)
    }

    /**
     * 프록시 둘의 중계 — 손님 → 원격은 이 일꾼에서, 원격 → 손님은 따로 된 스레드에서 돌린다.
     *
     * 한쪽이 끝(EOF)나면 반대편의 쓰기만 닫아(`shutdownOutput`) 상대가 RST 가 아니라 정상 FIN 을 받게 하고, **반대 방향은 시한 없이 끝까지**
     * 넘긴다 — 요청을 보낸 뒤 쓰기를 닫는 손님(반쯤 닫힘)에게 원격의 답이 오래 걸려도 자르지 않는다. 예전에는 반대 방향을 5초 기다린 뒤
     * 일꾼이 돌아가 소켓을 닫아 그 뒤의 답이 잘렸다. 그 대신 기다리는 동안 [TetherTimings.relayPollMs] 마다 멈춤을 본다 —
     * [stop] 이 손님 소켓을 닫았으면 원격도 닫아 반대 방향을 끝낸다. 원격 소켓은 [ConnectionRegistry] 에 없어서, 이렇게 하지 않으면 조용한
     * 원격을 읽는 스레드가 stop() 뒤에도 남는다(멈추면 실제로 끊긴다 — 그 약속을 지킨다). 손님 쪽이 깨져서(EOF 가 아니라 예외) 끝났으면
     * 원격도 곧바로 닫는다 — 받을 사람이 없다.
     *
     * 스트림은 [pipe] 의 try 안에서 얻는다 — usb-tether 는 반대 방향 스레드에서 `client.getOutputStream()` 을 try 밖에서 불러, 그 사이 소켓이
     * 닫히면(손님이 끊음·`stop()`) `SocketException` 이 스레드 밖으로 나갔다. 안드로이드에서 잡히지 않은 예외는 **프로세스를 죽인다**(PC 의
     * 인터넷이 끊긴다). 시험의 `Uncaught` 가 그것을 찾았다(2026-10-10).
     *
     * @param clientIn 머리를 읽은 것과 **같은** 스트림 — 버퍼에 이미 들어온 본문(머리 뒤에 붙어 온 바이트)이 여기 있다.
     */
    protected fun relay(client: Socket, clientIn: InputStream, remote: Socket) {
        val back = background("$tag-pipe") {
            pipe({ remote.getInputStream() }, { client.getOutputStream() }, env.onBytesOut)
            shutdownOutputQuietly(client)
        }
        val clean = pipe({ clientIn }, { remote.getOutputStream() }, env.onBytesIn)
        if (clean) shutdownOutputQuietly(remote) else closeQuietly(remote)
        try {
            while (back.isAlive) {
                back.join(env.timings.relayPollMs)
                if (back.isAlive && (!isRunning || client.isClosed)) closeQuietly(remote)
            }
        } catch (_: InterruptedException) {
            // stop() 의 shutdownNow 가 일꾼을 깨웠다 — 원격을 닫아 반대 방향도 끝내고, 깨움 표시는 되살린다(일꾼 풀이 본다).
            closeQuietly(remote)
            Thread.currentThread().interrupt()
        }
    }

    /** [src] 에서 [dst] 로 끝까지 옮긴다. 정상 끝(EOF)이면 true, 예외로 끝났으면 false(상대가 끊음·stop() 이 소켓을 닫음 — 정상일 때도 많다). */
    private fun pipe(src: () -> InputStream, dst: () -> OutputStream, onBytes: (Long) -> Unit): Boolean {
        try {
            val input = src()
            val output = dst()
            val buf = ByteArray(RELAY_BUFFER_BYTES)
            while (true) {
                val n = input.read(buf)
                if (n == -1) return true
                output.write(buf, 0, n)
                onBytes(n.toLong())
            }
        } catch (e: Exception) {
            // 정상 끝도 여기로 온다 — 그래서 verbose. 종류만 남긴다(목적지를 남기지 않는다 — TetherLog 의 규칙).
            env.log.v(tag, "pipe 끝: ${e.javaClass.simpleName}")
            return false
        }
    }

    /**
     * [basePort] 부터 [attempts] 개를 차례로 묶어 본다 — 비어 있는 첫 포트. 처음 합친 판(과 usb-tether)의 고리를 되살렸다(사용자 결정
     * 2026-10-10). 모두 차 있으면 [StartError.PORT_TAKEN].
     */
    private fun bind(): ServerSocket? {
        for (offset in 0 until attempts) {
            // 0 은 시험의 '아무 빈 포트' — 다음 포트를 셀 수 없으므로 한 번만.
            val port = if (basePort == 0) 0 else basePort + offset
            try {
                // reuseAddress 는 걸지 않는다 — 생성자가 이미 묶은 뒤라 효과가 없다(usb-tether 에서 오해를 부르던 죽은 코드였다).
                return ServerSocket(port, BACKLOG, env.bindAddress)
            } catch (e: Exception) {
                val next = when {
                    attempts == 1 -> " — 옮기지 않는다"
                    basePort != 0 && offset + 1 < attempts -> " — 다음 포트를 해 본다"
                    else -> " — 해 볼 포트가 더 없다"
                }
                env.log.w(tag, "포트 $port 를 묶지 못했다: ${e.javaClass.simpleName}$next")
            }
            if (basePort == 0) break
        }
        lastError = StartError.PORT_TAKEN
        return null
    }

    /**
     * 받기 고리. 두 출구 — accept 가 던짐(stop() 이 닫은 것이 아니면)과 고리 밖으로 나간 Throwable — 모두 [died] 로 간다: 듣는 소켓을 닫고
     * 상태를 내리고 알린다. 예전에는 고리만 끝나 **포트를 쥔 채 화면은 '켜짐'** 이었다.
     */
    private fun acceptLoop(socket: ServerSocket) {
        try {
            while (running.get()) {
                val client = try {
                    socket.accept()
                } catch (e: Exception) {
                    // stop() 이 닫았으면 정상 끝이다.
                    if (running.get()) died(e)
                    return
                }
                try {
                    admit(client)
                } catch (t: Throwable) {
                    // 받은 소켓은 아직 어디에도 적히지 않았다 — 닫고 고리를 내린다(아래).
                    closeQuietly(client)
                    throw t
                }
            }
        } catch (t: Throwable) {
            // Exception 이 아니라 Throwable — OutOfMemoryError 로 조용히 죽으면 화면은 계속 '켜짐' 이다.
            died(t)
        }
    }

    /** 받기 고리가 죽었다 — 서버를 내리고(포트를 놓고 연결을 끊는다) 앱에 알린다. [stop] 이 먼저였으면 아무것도 하지 않는다. */
    private fun died(error: Throwable) {
        if (!shutdown()) return
        lastError = StartError.DIED
        env.log.e(tag, "받기 고리가 죽어 서버를 내렸다", error)
        env.onServerDied(this)
    }

    /** 받은 연결 하나를 거르고 일꾼에 넘긴다(받기 스레드). */
    private fun admit(client: Socket) {
        // 듣는 소켓은 와일드카드라 모든 인터페이스를 덮는다 — 주소·우리 그룹·연결이 들어온 망을 함께 본다(PeerFilter).
        val rejection = try {
            env.peers.check(client)
        } catch (e: Exception) {
            // 거르개가 답하지 못하면 받지 않는다.
            PeerRejection(PeerRejection.Reason.NETWORK_UNKNOWN, detail = e.javaClass.simpleName)
        }
        if (rejection != null) {
            refusalLog.note("peer-${rejection.reason}") { skipped ->
                env.log.w(tag, "허용하지 않은 손님을 거절했다(${rejection.reason})${batched(skipped)}")
            }
            env.onRejected(rejection)
            closeQuietly(client)
            return
        }
        if (connections.size >= maxSessions) {
            noteRefusal(Refusal.SESSION_LIMIT, "동시 연결 상한($maxSessions)이라 거절했다")
            closeQuietly(client)
            return
        }
        if (!env.budget.tryAcquire(threadsPerSession)) {
            noteRefusal(Refusal.THREAD_BUDGET, "스레드 예산(${env.budget.limit})이 다 차서 거절했다")
            closeQuietly(client)
            return
        }
        if (!connections.register(client)) {
            // stop() 이 이미 지나갔다 — 일꾼에 넘기면 중계가 stop() 뒤에도 살아남는다.
            env.budget.release(threadsPerSession)
            closeQuietly(client)
            return
        }
        try {
            executor.submit { serve(client) }
        } catch (t: Throwable) {
            // Throwable — 거절(RejectedExecutionException)뿐 아니라 스레드를 더 못 만들 때의 OutOfMemoryError 도 여기로 온다. 놓치면
            // 적어 둔 소켓이 빠지지 않아 자리를 영영 쥐고, accept 스레드까지 죽는다.
            connections.unregister(client)
            env.budget.release(threadsPerSession)
            closeQuietly(client)
            env.log.e(tag, "일꾼에 넘기지 못했다: ${t.javaClass.simpleName}")
        }
    }

    private fun serve(client: Socket) {
        try {
            handle(client)
        } catch (e: Exception) {
            // 목적지를 남기지 않는다 — 예외의 글에 호스트 이름이 들 수 있다(UnknownHost 등). 종류만.
            env.log.w(tag, "손님 연결 오류: ${e.javaClass.simpleName}")
        } finally {
            connections.unregister(client)
            env.budget.release(threadsPerSession)
            closeQuietly(client)
        }
    }

    private companion object {
        const val BACKLOG = 50

        /** 중계 한 방향의 버퍼. */
        const val RELAY_BUFFER_BYTES = 8192

        fun namedThreads(tag: String): ThreadFactory {
            val count = AtomicInteger()
            return ThreadFactory { r -> Thread(r, "$tag-${count.incrementAndGet()}").apply { isDaemon = true } }
        }

        /** 묶인 줄의 수를 기록 끝에 붙인다. */
        fun batched(skipped: Int): String = if (skipped > 0) " — 앞 줄 뒤로 같은 것 ${skipped}건을 묶었다" else ""
    }
}

internal fun closeQuietly(closeable: AutoCloseable?) {
    try {
        closeable?.close()
    } catch (_: Exception) {
    }
}

/** 쓰기만 닫는다(상대에 정상 FIN). 이미 닫혔으면 조용히. */
internal fun shutdownOutputQuietly(socket: Socket) {
    try {
        socket.shutdownOutput()
    } catch (_: Exception) {
    }
}
