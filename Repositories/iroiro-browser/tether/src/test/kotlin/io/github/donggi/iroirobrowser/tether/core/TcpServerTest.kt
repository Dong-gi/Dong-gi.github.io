package io.github.donggi.iroirobrowser.tether.core

import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 서버 셋의 몸통([TcpServer]) — 받기 고리가 죽으면 포트를 놓고 내리고 알린다, 상한으로 닫은 연결을 따로 센다, 거절의 기록을
 * 묶는다. 시험용 서버는 받은 연결을 손님이 닫을 때까지 쥐고 있는다.
 */
class TcpServerTest {

    private val closer = Closer()

    @AfterTest
    fun cleanUp() = closer.close()

    /** 받은 연결을 손님이 닫을 때까지 쥐는 서버(일꾼 하나·스레드 하나). */
    private class HoldServer(env: TetherEnvironment, maxSessions: Int = 8) :
        TcpServer(env, "hold", 0, attempts = 1, maxSessions = maxSessions, threadsPerSession = 1) {
        override fun handle(client: Socket) {
            client.getOutputStream().write(1)
            client.getInputStream().read()
        }
    }

    private fun held(port: Int): Socket {
        val s = closer.keep(Socket(LOOPBACK, port))
        s.soTimeout = 5000
        assertEquals(1, s.getInputStream().read(), "받아서 일꾼에 넘겼다")
        return s
    }

    /** 닫혔는가 — 읽기가 끝(-1)이거나 던진다. */
    private fun closedByServer(s: Socket): Boolean = try {
        s.getInputStream().read() == -1
    } catch (_: IOException) {
        true
    }

    @Test
    fun 받기_고리가_던지면_포트를_놓고_내리고_알린다() {
        // 거르개가 Error 를 던지면 받기 고리 밖으로 나간다(OutOfMemoryError 와 같은 길). 예전에는 고리만 끝나 포트를 쥔 채 '켜짐' 이었고,
        // stop() 도 running 이 이미 내려가 있어 아무것도 하지 않았다.
        val counters = Counters()
        var calls = 0
        val gate = PeerGate { if (++calls == 2) throw OutOfMemoryError("시험") else null }
        val server = closer.keep(HoldServer(testEnv(peers = gate, counters = counters)))
        val port = server.start()
        val first = held(port)
        Socket(LOOPBACK, port).use { it.soTimeout = 5000 }
        waitUntil { counters.died.isNotEmpty() }
        assertEquals(listOf<TcpServer>(server), counters.died.toList())
        assertEquals(StartError.DIED, server.lastError)
        assertEquals(-1, server.actualPort)
        assertTrue(isFree(port), "포트를 놓았다")
        assertTrue(closedByServer(first), "열린 연결도 끊었다")
        // 내린 뒤의 stop() 은 아무 일 없이 지나가고, 다시 세우지 않는다.
        server.stop()
        assertEquals(-1, server.start())
        assertEquals(StartError.TERMINATED, server.lastError)
    }

    @Test
    fun 듣는_소켓이_밖에서_닫혀_accept_가_던져도_같다() {
        val counters = Counters()
        val server = closer.keep(HoldServer(testEnv(counters = counters)))
        val port = server.start()
        val first = held(port)
        val field = TcpServer::class.java.getDeclaredField("serverSocket").apply { isAccessible = true }
        (field.get(server) as ServerSocket).close()
        waitUntil { counters.died.isNotEmpty() }
        assertEquals(StartError.DIED, server.lastError)
        assertEquals(-1, server.actualPort)
        assertTrue(closedByServer(first))
    }

    @Test
    fun 멈춘_서버는_알리지_않고_stop_은_몇_번_불러도_된다() {
        val counters = Counters()
        val server = closer.keep(HoldServer(testEnv(counters = counters)))
        val port = server.start()
        server.stop()
        server.stop()
        Thread.sleep(100)
        assertTrue(counters.died.isEmpty(), "정상 멈춤은 '죽었다' 가 아니다")
        assertEquals(null, server.lastError)
        assertTrue(isFree(port))
    }

    @Test
    fun 동시_연결_상한으로_닫은_연결을_따로_센다() {
        val counters = Counters()
        val server = closer.keep(HoldServer(testEnv(counters = counters), maxSessions = 1))
        val port = server.start()
        held(port)
        val refused = closer.keep(Socket(LOOPBACK, port))
        refused.soTimeout = 5000
        assertTrue(closedByServer(refused))
        waitUntil { counters.refusals.isNotEmpty() }
        assertEquals(listOf(Refusal.SESSION_LIMIT), counters.refusals.toList())
        assertTrue(counters.rejections.isEmpty())
    }

    @Test
    fun 거절이_몰려도_기록은_까닭마다_초당_한_줄이다() {
        val log = LogLines()
        val counters = Counters()
        val server = closer.keep(HoldServer(testEnv(peers = realPeerFilter(), log = log, counters = counters, nowMs = { 42L })))
        val port = server.start()
        repeat(30) { Socket(LOOPBACK, port).use { s -> s.soTimeout = 5000; closedByServer(s) } }
        waitUntil { counters.rejections.size == 30 }
        val rejectionLines = log.lines.filter { it.contains("거절했다") }
        assertEquals(1, rejectionLines.size, "같은 까닭은 한 줄 — $rejectionLines")
        assertEquals(30, counters.rejections.size, "수는 모두 센다")
    }
}
