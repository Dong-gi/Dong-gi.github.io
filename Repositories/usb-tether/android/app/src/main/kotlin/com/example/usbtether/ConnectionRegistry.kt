package com.example.usbtether

import java.net.Socket
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * accept 된 소켓을 추적해 서버가 실제로 연결을 끊을 수 있게 한다.
 *
 * ## 왜 필요한가
 *
 * 이전 `stop()` 은 **리스닝 소켓만** 닫고 `executor.shutdownNow()` 를 호출했다.
 * `shutdownNow()` 는 워커 스레드를 인터럽트하지만, `SocketInputStream.read()` 에서
 * 블록된 스레드는 인터럽트를 무시한다 — 소켓을 닫아야 풀린다. accept 된 소켓은
 * 어디에도 보관되지 않았으므로 아무도 닫지 않았다.
 *
 * 게다가 핸드셰이크 후 `soTimeout = 0` 으로 만들어 시간 제한마저 없앴다.
 *
 * 결과적으로 사용자가 Stop 을 눌러 UI 가 "Stopped" 를 표시하는 동안 **진행 중인
 * 모든 터널이 계속 중계됐다.** 이상 트래픽을 보고 Stop 을 눌러도 실제로 끊기지
 * 않는다는 뜻이다.
 *
 * ## 유휴 타임아웃을 두지 않는 이유
 *
 * 릴레이 구간의 `soTimeout` 은 0(무한) 으로 유지한다. 유한한 읽기 타임아웃을 두면
 * 살아 있지만 조용한 연결(WebSocket, IMAP IDLE, SSH 세션)이 끊긴다. 유휴 소켓이
 * 쌓이는 문제는 동시 세션 상한으로 막는다.
 */
internal class ConnectionRegistry {

    private val sockets: MutableSet<Socket> =
        Collections.newSetFromMap(ConcurrentHashMap<Socket, Boolean>())

    /** 현재 추적 중인 연결 수. 동시 세션 상한 판정에 쓴다. */
    val size: Int get() = sockets.size

    /**
     * [closeAll] 이 지나갔는지. 한 번 서면 되돌아가지 않는다 — 이 레지스트리는
     * 서버 인스턴스와 수명을 같이하고, 두 서버 모두 `stop()` 후 재기동을 허용하지
     * 않는다(`running` CAS).
     */
    private val closed = AtomicBoolean(false)

    /**
     * 소켓을 추적 대상에 넣는다.
     *
     * **accept 직후, 워커에 넘기기 전에** 호출해야 한다. `handleClient` 안에서
     * 등록하면 accept 와 등록 사이에 태스크가 쌓여 [size] 로 판정하는 동시 세션
     * 상한이 무의미해지고 CachedThreadPool 이 스레드를 무제한으로 만든다.
     *
     * 실제 호출부는 `Socks5Server.acceptLoop` / `HttpProxyServer.acceptLoop` 의
     * `executor.submit` 직전이다.
     *
     * @return 등록됐으면 true. [closeAll] 이 이미 지나갔으면 **false** — 호출부는
     *   그 소켓을 즉시 닫고 워커에 넘기지 않아야 한다. 넘기면 `stop()` 이 끝난 뒤에도
     *   릴레이가 살아남는다.
     */
    fun register(socket: Socket): Boolean {
        if (closed.get()) return false
        sockets.add(socket)
        // 위 검사를 통과한 직후 closeAll 이 지나갔을 수 있다. 그 경우 이 소켓은
        // closeAll 의 스냅샷에 잡히지 않으므로 직접 걷어내고 실패로 알린다.
        if (closed.get()) {
            sockets.remove(socket)
            return false
        }
        return true
    }

    /**
     * 추적 대상에서 제거한다.
     *
     * 두 곳에서 호출한다 — `handleClient` 의 finally, 그리고 `submit` 이 실패해
     * `handleClient` 가 아예 실행되지 못한 경로. 후자를 빠뜨리면 세션 슬롯이
     * 영구히 점유된다.
     */
    fun unregister(socket: Socket) {
        sockets.remove(socket)
    }

    /**
     * 추적 중인 모든 소켓을 닫는다. `read()` 에 블록된 스레드가 이때 풀린다.
     *
     * 닫는 도중 다른 스레드가 [unregister] 를 호출할 수 있으므로 스냅샷을 떠서 돈다.
     *
     * **[sockets] 를 `clear()` 로 비우지 않는다.** 예전에는 스냅샷을 닫은 뒤 통째로
     * 비웠는데, 스냅샷을 뜬 시점과 `clear()` 사이에 등록된 소켓은 닫히지도 않고
     * 추적에서도 사라졌다. 그 소켓의 `handleClient` 가 이어서 실행되면 사용자가
     * Stop 을 눌러 UI 가 "Stopped" 를 표시하는 동안 릴레이가 계속된다 — 이
     * 클래스가 막으려고 존재하는 바로 그 상황이다.
     *
     * 이제 [closed] 를 먼저 세워 새 등록을 막고([register] 가 false 를 돌려준다),
     * 실제로 닫은 것만 제거하며, 그 사이에 끼어든 것이 있을 수 있으므로 비워질
     * 때까지 돈다. 리스닝 소켓은 이 시점에 이미 닫혀 있으므로 끼어들 수 있는 수는
     * accept 직후 단계에 있던 소켓 몇 개뿐이고, 그래도 무한 루프를 만들지 않도록
     * 횟수를 묶는다.
     */
    fun closeAll() {
        closed.set(true)
        var round = 0
        while (round++ < MAX_DRAIN_ROUNDS) {
            val snapshot = sockets.toList()
            if (snapshot.isEmpty()) return
            for (socket in snapshot) {
                try {
                    socket.close()
                } catch (_: Exception) {
                    // 이미 닫힌 소켓이거나 경쟁 상태. 무시한다.
                }
                sockets.remove(socket)
            }
        }
    }

    private companion object {
        /**
         * [closeAll] 이 스냅샷을 다시 뜨는 최대 횟수.
         *
         * 리스닝 소켓이 이미 닫힌 뒤라 accept 파이프라인에 남아 있을 수 있는 소켓은
         * 극소수다. 한 번으로도 대개 충분하지만, [register] 와의 경쟁으로 한 개가
         * 더 들어오는 경우를 흡수할 여유를 둔다.
         */
        const val MAX_DRAIN_ROUNDS = 4
    }
}
