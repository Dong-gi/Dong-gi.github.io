package io.github.donggi.iroirobrowser.tether.core

import java.net.Socket
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * accept 한 소켓을 적어 두어 서버가 연결을 실제로 끊을 수 있게 한다.
 *
 * ## 왜 필요한가
 *
 * usb-tether 의 첫 `stop()` 은 **듣는 소켓만** 닫고 `executor.shutdownNow()` 를 불렀다. `shutdownNow()` 는 일꾼을 인터럽트하지만
 * `SocketInputStream.read()` 에 막힌 스레드는 인터럽트를 무시한다 — 소켓을 닫아야 풀린다. accept 한 소켓은 어디에도 없었으니 아무도 닫지
 * 않았고, 핸드셰이크 뒤 `soTimeout = 0` 이라 시간 제한도 없었다. 그래서 사용자가 멈춤을 눌러 화면이 '멈춤' 인 동안 **열린 터널이 모두 계속
 * 중계됐다.**
 *
 * ## 쉬는 시간 제한을 두지 않는 까닭
 *
 * 중계 구간의 `soTimeout` 은 0(무한)이다. 유한하게 두면 살아 있지만 조용한 연결(WebSocket·IMAP IDLE·SSH)이 끊긴다. 쉬는 소켓이 쌓이는
 * 것은 동시 연결 상한과 [ThreadBudget] 으로 막는다.
 */
internal class ConnectionRegistry {

    private val sockets: MutableSet<Socket> = Collections.newSetFromMap(ConcurrentHashMap<Socket, Boolean>())

    /** 지금 적어 둔 연결 수 — 동시 연결 상한 판정에 쓴다. */
    val size: Int get() = sockets.size

    /** [closeAll] 이 지나갔는가. 한 번 서면 되돌아가지 않는다(서버는 멈춘 뒤 다시 세우지 않는다 — [TcpServer.start]). */
    private val closed = AtomicBoolean(false)

    /**
     * 소켓을 적는다. **accept 직후, 일꾼에 넘기기 전에** 부른다 — 일꾼 안에서 적으면 accept 와 적기 사이에 일이 쌓여 [size] 로 보는 동시 연결
     * 상한이 뜻을 잃고 캐시 스레드 풀이 스레드를 끝없이 만든다.
     *
     * @return 적었으면 true. [closeAll] 이 이미 지나갔으면 **false** — 부르는 쪽은 그 소켓을 곧바로 닫고 일꾼에 넘기지 않는다(넘기면 `stop()`
     *   뒤에도 중계가 살아남는다).
     */
    fun register(socket: Socket): Boolean {
        if (closed.get()) return false
        sockets.add(socket)
        // 위 검사를 지난 직후 closeAll 이 지나갔을 수 있다 — 그러면 이 소켓은 closeAll 의 스냅샷에 없으므로 직접 걷고 실패로 알린다.
        if (closed.get()) {
            sockets.remove(socket)
            return false
        }
        return true
    }

    /** 적은 것에서 뺀다 — 일꾼의 finally 와, 넘기기가 실패해 일꾼이 아예 돌지 못한 길 두 곳에서. 뒤의 것을 빠뜨리면 자리가 영영 찬다. */
    fun unregister(socket: Socket) {
        sockets.remove(socket)
    }

    /**
     * 적은 소켓을 모두 닫는다 — `read()` 에 막힌 스레드가 이때 풀린다. [closed] 를 먼저 세워 새로 적기를 막고, 실제로 닫은 것만 빼며, 그 사이에
     * 끼어든 것이 있을 수 있어 빌 때까지 돈다(듣는 소켓이 이미 닫혀 끼어들 수 있는 것은 몇 개뿐이라 횟수를 묶는다). 앞 판은 스냅샷을 닫은 뒤
     * 통째로 비워, 그 사이 적힌 소켓이 닫히지도 않고 목록에서도 사라졌다.
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
                    // 이미 닫혔거나 경쟁. 무시한다.
                }
                sockets.remove(socket)
            }
        }
    }

    private companion object {
        const val MAX_DRAIN_ROUNDS = 4
    }
}
