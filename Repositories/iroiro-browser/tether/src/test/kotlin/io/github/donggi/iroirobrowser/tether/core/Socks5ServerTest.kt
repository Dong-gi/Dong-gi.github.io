package io.github.donggi.iroirobrowser.tether.core

import java.io.InputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * SOCKS5 서버(RFC 1928)를 루프백에서 실제로 주고받으며 본다 — 인사·CONNECT 의 세 주소 꼴·거절 답(막힘·풀지 못함)·UDP ASSOCIATE(출발지
 * 고정·조각·이름·막힌 목적지·상한)·머리의 전체 마감·반쯤 닫힌 연결의 늦은 답·포트를 옮기지 않음·멈춘 뒤 다시 세우지 않음·멈추면 중계가
 * 끊김·스레드 예산.
 */
class Socks5ServerTest {

    private val closer = Closer()

    @AfterTest
    fun cleanUp() = closer.close()

    private fun server(env: TetherEnvironment): Socks5Server {
        val s = closer.keep(Socks5Server(env, port = 0))
        assertTrue(s.start() > 0)
        return s
    }

    /** 인사를 마친 소켓. */
    private fun greet(port: Int): Socket {
        val s = closer.keep(Socket(LOOPBACK, port))
        s.soTimeout = 5000
        s.getOutputStream().write(byteArrayOf(5, 2, 0, 2))
        val reply = readExactly(s.getInputStream(), 2)
        assertContentEquals(byteArrayOf(5, 0), reply, "NO_AUTH 를 고른다")
        return s
    }

    private fun request(s: Socket, cmd: Int, atyp: Int, address: ByteArray, port: Int): ByteArray {
        s.getOutputStream().write(requestBytes(cmd, atyp, address, port))
        return readReply(s.getInputStream())
    }

    private fun requestBytes(cmd: Int, atyp: Int, address: ByteArray, port: Int): ByteArray {
        val head = byteArrayOf(5, cmd.toByte(), 0, atyp.toByte())
        val addr = if (atyp == 3) byteArrayOf(address.size.toByte()) + address else address
        return head + addr + byteArrayOf((port shr 8).toByte(), port.toByte())
    }

    /** 답(10바이트 — 이 서버는 늘 IPv4 꼴로 답한다). */
    private fun readReply(inp: InputStream): ByteArray = readExactly(inp, 10)

    private fun connectIpv4(port: Int, target: Int): Socket {
        val s = greet(port)
        val reply = request(s, 1, 1, byteArrayOf(127, 0, 0, 1), target)
        assertEquals(0, reply[1].toInt(), "성공 답")
        return s
    }

    @Test
    fun 인사에서_무엇을_내밀든_NO_AUTH_를_고른다() {
        val srv = server(testEnv())
        val s = closer.keep(Socket(LOOPBACK, srv.actualPort))
        s.soTimeout = 5000
        s.getOutputStream().write(byteArrayOf(5, 1, 2)) // 사용자/비밀번호만 내밀어도
        assertContentEquals(byteArrayOf(5, 0), readExactly(s.getInputStream(), 2))
    }

    @Test
    fun 판이_5가_아니면_답하지_않고_닫는다() {
        val srv = server(testEnv())
        val s = closer.keep(Socket(LOOPBACK, srv.actualPort))
        s.soTimeout = 5000
        s.getOutputStream().write(byteArrayOf(4, 1, 0, 80, 127, 0, 0, 1, 0))
        assertEquals(-1, s.getInputStream().read())
    }

    @Test
    fun CONNECT_IPv4_는_두_방향을_잇고_반쯤_닫힘을_넘긴다() {
        val echo = closer.keep(EchoServer())
        val counters = Counters()
        val srv = server(testEnv(counters = counters))
        val s = greet(srv.actualPort)
        val reply = request(s, 1, 1, byteArrayOf(127, 0, 0, 1), echo.port)
        // BND 는 0.0.0.0:0 — 폰의 셀룰러 주소를 손님에게 알리지 않는다.
        assertContentEquals(byteArrayOf(5, 0, 0, 1, 0, 0, 0, 0, 0, 0), reply)
        s.getOutputStream().write("hello".toByteArray())
        assertEquals("hello", String(readExactly(s.getInputStream(), 5)))
        s.shutdownOutput()
        assertEquals(-1, s.getInputStream().read(), "원격이 쓰기를 닫으면 손님도 끝을 받는다")
        assertTrue(counters.bytesIn.get() >= 5 && counters.bytesOut.get() >= 5)
    }

    @Test
    fun 요청_뒤에_곧바로_붙여_보낸_바이트도_잃지_않는다() {
        // 답을 기다리지 않고 요청 뒤에 데이터를 붙이는 손님(낙관적 데이터) — 머리를 읽은 버퍼를 중계까지 넘긴다.
        val echo = closer.keep(EchoServer())
        val srv = server(testEnv())
        val s = greet(srv.actualPort)
        s.getOutputStream().write(requestBytes(1, 1, byteArrayOf(127, 0, 0, 1), echo.port) + "early".toByteArray())
        assertEquals(0, readReply(s.getInputStream())[1].toInt())
        assertEquals("early", String(readExactly(s.getInputStream(), 5)))
    }

    @Test
    fun CONNECT_이름은_거르개로_한_번만_풀고_그_주소로_연결한다() {
        val echo = closer.keep(EchoServer())
        val names = TestDestinations(names = mapOf("echo.test" to listOf(LOOPBACK)))
        val dialed = CopyOnWriteArrayList<InetAddress>()
        val recording = object : Outbound {
            override fun connect(address: InetAddress, port: Int, timeoutMs: Int): Socket {
                dialed += address
                return Outbound.DIRECT.connect(address, port, timeoutMs)
            }

            override fun openUdp(): DatagramSocket = Outbound.DIRECT.openUdp()
        }
        val srv = server(testEnv(destinations = names, outbound = recording))
        val s = greet(srv.actualPort)
        val reply = request(s, 1, 3, "echo.test".toByteArray(), echo.port)
        assertEquals(0, reply[1].toInt())
        s.getOutputStream().write("x".toByteArray())
        assertEquals('x'.code, s.getInputStream().read())
        assertEquals(listOf("echo.test"), names.asked.toList())
        assertEquals(listOf(LOOPBACK), dialed.toList(), "푼 주소 객체로 — 이름을 다시 풀지 않는다")
    }

    @Test
    fun CONNECT_IPv6_리터럴을_읽는다() {
        val echo = closer.keep(EchoServer())
        // ::1 을 루프백 IPv4 의 가짜 원격으로 돌린다 — 머리의 16바이트를 읽는지만 본다.
        val names = TestDestinations(names = mapOf("0:0:0:0:0:0:0:1" to listOf(LOOPBACK)))
        val srv = server(testEnv(destinations = names))
        val s = greet(srv.actualPort)
        val reply = request(s, 1, 4, ByteArray(15) + byteArrayOf(1), echo.port)
        assertEquals(0, reply[1].toInt())
        assertEquals(listOf("0:0:0:0:0:0:0:1"), names.asked.toList())
    }

    @Test
    fun 막힌_목적지는_규칙이_허락하지_않음으로_답한다() {
        val srv = server(testEnv(destinations = DestinationFilter.SYSTEM))
        for (target in listOf(byteArrayOf(10, 0, 0, 1), byteArrayOf(127, 0, 0, 1), byteArrayOf(192.toByte(), 168.toByte(), 49, 1))) {
            val s = greet(srv.actualPort)
            assertEquals(2, request(s, 1, 1, target, 80)[1].toInt())
        }
    }

    @Test
    fun 풀지_못한_이름은_호스트에_닿지_못함으로_답한다() {
        // 예전에는 풀이 실패도 '규칙이 허락하지 않음'(2)이었다 — 막은 것이 아니다.
        val srv = server(testEnv(destinations = TestDestinations()))
        val s = greet(srv.actualPort)
        assertEquals(4, request(s, 1, 3, "nowhere.test".toByteArray(), 443)[1].toInt())
    }

    @Test
    fun 닫힌_포트는_연결_거부로_답한다() {
        val srv = server(testEnv())
        val s = greet(srv.actualPort)
        // 윈도의 루프백은 닫힌 포트의 거부를 몇 초 뒤에 준다 — 서버의 연결 시간 제한(5초)보다 넉넉히 기다린다.
        s.soTimeout = 15_000
        assertEquals(5, request(s, 1, 1, byteArrayOf(127, 0, 0, 1), closedPort())[1].toInt())
    }

    @Test
    fun 모르는_명령과_주소_꼴은_그_답으로_거절한다() {
        val srv = server(testEnv())
        val bind = greet(srv.actualPort)
        assertEquals(7, request(bind, 2, 1, byteArrayOf(127, 0, 0, 1), 80)[1].toInt()) // BIND
        val badAtyp = greet(srv.actualPort)
        badAtyp.getOutputStream().write(byteArrayOf(5, 1, 0, 5))
        assertEquals(8, readReply(badAtyp.getInputStream())[1].toInt())
    }

    @Test
    fun 인사와_요청을_다_받기까지의_전체_마감이_지나면_닫는다() {
        // 한 바이트씩 80ms 마다 — 읽기 하나의 시한은 늘 지키지만 인사·요청 전체는 1초 넘게 걸린다. 인사(3바이트)는 마감 안에
        // 끝나 그 답(05 00)만 오고, 요청은 마감에 걸려 CONNECT 의 답 없이 닫힌다.
        val echo = closer.keep(EchoServer())
        val srv = server(testEnv(timings = TetherTimings(proxyHeaderMs = 400)))
        val bytes = byteArrayOf(5, 1, 0) + requestBytes(1, 1, byteArrayOf(127, 0, 0, 1), echo.port)
        val got = dribble(srv.actualPort, bytes, chunk = 1, gapMs = 80)
        assertContentEquals(byteArrayOf(5, 0), got, "인사의 답뿐이다")
        assertEquals(0, echo.accepted.get(), "원격에 붙지 않았다")
    }

    @Test
    fun 반쯤_닫은_뒤의_늦은_답도_끝까지_넘긴다() {
        // 손님이 요청을 보내고 쓰기를 닫았다. 원격은 그 뒤 아홉 번에 나눠 천천히 답한다 — 예전에는 반대 방향을 정한 시간(5초)만 기다리고
        // 잘랐다. 시간을 줄여 꾸몄다: 멈춤을 보는 주기 50ms, 답은 100ms 간격으로 아홉 조각(옛 꼴이면 50ms 에서 잘린다).
        val pieces = (1..9).map { "piece-$it;" }
        val slow = closer.keep(SlowReplyServer(pieces, gapMs = 100))
        val srv = server(testEnv(timings = TetherTimings(relayPollMs = 50)))
        val s = connectIpv4(srv.actualPort, slow.port)
        s.getOutputStream().write("question".toByteArray())
        s.shutdownOutput()
        assertEquals(pieces.joinToString(""), String(readAll(s.getInputStream()), Charsets.ISO_8859_1))
        assertEquals("question", slow.received.single())
    }

    @Test
    fun 반쯤_닫힌_중계도_멈추면_끊긴다() {
        // 손님은 쓰기를 닫고 원격은 아무것도 보내지 않는다 — 반대 방향을 시한 없이 기다리는 중에도 stop() 은 원격까지 닫아 일꾼을 끝낸다.
        val silent = closer.keep(ServerSocket(0, 50, LOOPBACK))
        val remoteSide = CopyOnWriteArrayList<Socket>()
        thread(isDaemon = true) { runCatching { remoteSide += silent.accept() } }
        val budget = ThreadBudget()
        val srv = server(testEnv(budget = budget, timings = TetherTimings(relayPollMs = 50)))
        val s = connectIpv4(srv.actualPort, silent.localPort)
        s.shutdownOutput()
        waitUntil { remoteSide.isNotEmpty() }
        val remote = remoteSide.single()
        remote.soTimeout = 5000
        assertEquals(-1, remote.getInputStream().read(), "손님의 반쯤 닫힘이 원격에 갔다")
        assertEquals(2, budget.inUse, "중계가 반대 방향을 기다린다")
        srv.stop()
        waitUntil(3000) { budget.inUse == 0 }
        // 서버가 원격 쪽 소켓을 닫았다 — 원격이 쓰면 곧 실패한다(RST).
        val failed = runCatching { repeat(50) { remote.getOutputStream().write(ByteArray(1024)); Thread.sleep(10) } }.isFailure
        assertTrue(failed, "원격 소켓이 닫혔다")
        remote.close()
    }

    /** UDP ASSOCIATE 를 맺고 (제어 소켓, 중계 포트)를 돌려준다. */
    private fun associate(port: Int): Pair<Socket, Int> {
        val control = greet(port)
        val reply = request(control, 3, 1, byteArrayOf(0, 0, 0, 0), 0)
        assertEquals(0, reply[1].toInt())
        // BND.ADDR 는 손님이 붙은 로컬 주소(실기기는 192.168.49.1, 여기는 루프백).
        assertContentEquals(byteArrayOf(127, 0, 0, 1), reply.copyOfRange(4, 8))
        val relayPort = ((reply[8].toInt() and 0xFF) shl 8) or (reply[9].toInt() and 0xFF)
        return control to relayPort
    }

    private fun udpPacket(atyp: Int, address: ByteArray, port: Int, payload: ByteArray, frag: Int = 0): ByteArray {
        val addr = if (atyp == 3) byteArrayOf(address.size.toByte()) + address else address
        return byteArrayOf(0, 0, frag.toByte(), atyp.toByte()) + addr + byteArrayOf((port shr 8).toByte(), port.toByte()) + payload
    }

    private fun DatagramSocket.sendTo(relayPort: Int, data: ByteArray) = send(DatagramPacket(data, data.size, LOOPBACK, relayPort))

    private fun DatagramSocket.receiveOrNull(timeoutMs: Int = 1500): ByteArray? {
        soTimeout = timeoutMs
        val buf = ByteArray(65535)
        val p = DatagramPacket(buf, buf.size)
        return try {
            receive(p)
            buf.copyOfRange(0, p.length)
        } catch (_: SocketTimeoutException) {
            null
        }
    }

    @Test
    fun UDP_ASSOCIATE_는_머리를_벗겨_보내고_답을_감싸_돌려준다() {
        val echo = closer.keep(UdpEcho())
        val srv = server(testEnv())
        val (_, relayPort) = associate(srv.actualPort)
        val client = closer.keep(DatagramSocket(0, LOOPBACK))
        client.sendTo(relayPort, udpPacket(1, byteArrayOf(127, 0, 0, 1), echo.port, "ping".toByteArray()))
        val answer = client.receiveOrNull() ?: fail("답이 오지 않았다")
        // RSV RSV FRAG ATYP=1 127.0.0.1 port "ping"
        assertContentEquals(byteArrayOf(0, 0, 0, 1, 127, 0, 0, 1, (echo.port shr 8).toByte(), echo.port.toByte()), answer.copyOfRange(0, 10))
        assertEquals("ping", String(answer.copyOfRange(10, answer.size)))
    }

    @Test
    fun UDP_는_처음_배운_출발_포트만_받는다() {
        val echo = closer.keep(UdpEcho())
        val srv = server(testEnv())
        val (_, relayPort) = associate(srv.actualPort)
        val first = closer.keep(DatagramSocket(0, LOOPBACK))
        first.sendTo(relayPort, udpPacket(1, byteArrayOf(127, 0, 0, 1), echo.port, "a".toByteArray()))
        assertTrue(first.receiveOrNull() != null)
        // 같은 주소의 다른 포트(먼저 패킷을 보낸 쪽이 손님이 되던 예전 판의 탈취 길) — 보내지도 답하지도 않는다.
        val intruder = closer.keep(DatagramSocket(0, LOOPBACK))
        intruder.sendTo(relayPort, udpPacket(1, byteArrayOf(127, 0, 0, 1), echo.port, "b".toByteArray()))
        assertNull(intruder.receiveOrNull(800))
        assertEquals(1, echo.received.get())
    }

    @Test
    fun UDP_조각과_막힌_목적지는_버린다() {
        val echo = closer.keep(UdpEcho())
        val srv = server(testEnv(destinations = TestDestinations(blocked = setOf(InetAddress.getByName("127.0.0.2")))))
        val (_, relayPort) = associate(srv.actualPort)
        val client = closer.keep(DatagramSocket(0, LOOPBACK))
        client.sendTo(relayPort, udpPacket(1, byteArrayOf(127, 0, 0, 1), echo.port, "frag".toByteArray(), frag = 1))
        client.sendTo(relayPort, udpPacket(1, byteArrayOf(127, 0, 0, 2), echo.port, "blocked".toByteArray()))
        client.sendTo(relayPort, byteArrayOf(0, 0, 0, 1, 127)) // 짧다
        assertNull(client.receiveOrNull(800))
        assertEquals(0, echo.received.get())
        // 그 뒤의 정상 패킷은 간다.
        client.sendTo(relayPort, udpPacket(1, byteArrayOf(127, 0, 0, 1), echo.port, "ok".toByteArray()))
        assertTrue(client.receiveOrNull() != null)
    }

    @Test
    fun UDP_이름_목적지는_첫_패킷을_버리고_뒤에서_풀어_다음부터_보낸다() {
        val echo = closer.keep(UdpEcho())
        val names = TestDestinations(names = mapOf("udp.test" to listOf(LOOPBACK)))
        val srv = server(testEnv(destinations = names))
        val (_, relayPort) = associate(srv.actualPort)
        val client = closer.keep(DatagramSocket(0, LOOPBACK))
        val packet = udpPacket(3, "udp.test".toByteArray(), echo.port, "dns".toByteArray())
        client.sendTo(relayPort, packet)
        assertNull(client.receiveOrNull(600), "중계 스레드에서 이름을 풀지 않는다 — 첫 패킷은 버린다")
        var answer: ByteArray? = null
        repeat(10) {
            if (answer == null) {
                client.sendTo(relayPort, packet)
                answer = client.receiveOrNull(500)
            }
        }
        assertTrue(answer != null, "풀린 뒤에는 보낸다")
        assertEquals(listOf("udp.test"), names.asked.distinct())
    }

    @Test
    fun 제어_연결이_닫히면_UDP_중계도_멈춘다() {
        val echo = closer.keep(UdpEcho())
        val budget = ThreadBudget()
        val srv = server(testEnv(budget = budget))
        val (control, relayPort) = associate(srv.actualPort)
        val client = closer.keep(DatagramSocket(0, LOOPBACK))
        client.sendTo(relayPort, udpPacket(1, byteArrayOf(127, 0, 0, 1), echo.port, "1".toByteArray()))
        assertTrue(client.receiveOrNull() != null)
        control.close()
        waitUntil { srv.sessionCount == 0 && budget.inUse == 0 }
        client.sendTo(relayPort, udpPacket(1, byteArrayOf(127, 0, 0, 1), echo.port, "2".toByteArray()))
        assertNull(client.receiveOrNull(800))
    }

    @Test
    fun UDP_묶음_상한에_걸린_것은_일반_실패로_답하고_따로_센다() {
        // 예전에는 기록 한 줄뿐이라 화면에서 보이지 않았다.
        val counters = Counters()
        val srv = closer.keep(Socks5Server(testEnv(counters = counters), port = 0, maxUdpAssociations = 1))
        assertTrue(srv.start() > 0)
        associate(srv.actualPort)
        val second = greet(srv.actualPort)
        assertEquals(1, request(second, 3, 1, byteArrayOf(0, 0, 0, 0), 0)[1].toInt())
        waitUntil { counters.refusals.isNotEmpty() }
        assertEquals(listOf(Refusal.UDP_LIMIT), counters.refusals.toList())
    }

    @Test
    fun 포트를_옮기지_않고_차_있으면_실패를_알린다() {
        val squatter = closer.keep(squatWithFreeNext())
        val srv = closer.keep(Socks5Server(testEnv(), port = squatter.localPort))
        assertEquals(-1, srv.start())
        assertEquals(StartError.PORT_TAKEN, srv.lastError)
        assertEquals(-1, srv.actualPort)
        assertTrue(isFree(squatter.localPort + 1), "다음 포트를 잡지 않았다")
    }

    @Test
    fun 멈춘_서버는_다시_세우지_않는다() {
        val srv = server(testEnv())
        srv.stop()
        assertEquals(-1, srv.start())
        assertEquals(StartError.TERMINATED, srv.lastError)
    }

    @Test
    fun 멈추면_열린_중계가_실제로_끊긴다() {
        val echo = closer.keep(EchoServer())
        val srv = server(testEnv())
        val s = connectIpv4(srv.actualPort, echo.port)
        s.getOutputStream().write("a".toByteArray())
        assertEquals('a'.code, s.getInputStream().read())
        srv.stop()
        val end = try {
            s.getInputStream().read()
        } catch (_: Exception) {
            -1
        }
        assertEquals(-1, end)
    }

    @Test
    fun 스레드_예산이_차면_새_연결을_받지_않고_따로_센다() {
        val echo = closer.keep(EchoServer())
        val budget = ThreadBudget(4)
        val counters = Counters()
        val srv = server(testEnv(budget = budget, counters = counters))
        connectIpv4(srv.actualPort, echo.port)
        connectIpv4(srv.actualPort, echo.port)
        assertEquals(4, budget.inUse)
        val third = closer.keep(Socket(LOOPBACK, srv.actualPort))
        third.soTimeout = 5000
        third.getOutputStream().write(byteArrayOf(5, 1, 0))
        val got = try {
            third.getInputStream().read()
        } catch (_: Exception) {
            -1
        }
        assertEquals(-1, got, "받지 않고 닫는다")
        assertEquals(listOf(Refusal.THREAD_BUDGET), counters.refusals.toList())
        assertTrue(counters.rejections.isEmpty(), "손님 거르기와 따로 센다")
    }

    @Test
    fun UDP_묶음은_스레드를_하나_더_쥐고_못_쥐면_일반_실패로_답한다() {
        val budget = ThreadBudget(2)
        val counters = Counters()
        val srv = server(testEnv(budget = budget, counters = counters))
        val control = greet(srv.actualPort)
        assertEquals(1, request(control, 3, 1, byteArrayOf(0, 0, 0, 0), 0)[1].toInt())
        waitUntil { budget.inUse == 0 }
        assertEquals(listOf(Refusal.THREAD_BUDGET), counters.refusals.toList())
        val roomy = ThreadBudget(3)
        val srv2 = server(testEnv(budget = roomy))
        associate(srv2.actualPort)
        assertEquals(3, roomy.inUse)
    }

    @Test
    fun 실제_손님_거르개는_루프백을_거절하고_까닭을_알린다() {
        val counters = Counters()
        val srv = server(testEnv(peers = realPeerFilter(), counters = counters))
        val s = closer.keep(Socket(LOOPBACK, srv.actualPort))
        s.soTimeout = 5000
        s.getOutputStream().write(byteArrayOf(5, 1, 0))
        val got = try {
            s.getInputStream().read()
        } catch (_: Exception) {
            -1
        }
        assertEquals(-1, got)
        waitUntil { counters.rejections.isNotEmpty() }
        assertEquals(PeerRejection.Reason.NOT_GROUP_OWNER_ADDRESS, counters.rejections.first().reason)
    }

    @Test
    fun UDP_머리_읽기와_감싸기() {
        val data = udpPacket(1, byteArrayOf(1, 2, 3, 4), 53, byteArrayOf(9, 8))
        val parsed = Socks5Server.parseUdpHeader(DatagramPacket(data, data.size)) ?: fail("읽지 못했다")
        assertEquals(InetAddress.getByName("1.2.3.4"), (parsed.destination as UdpDestination.Literal).address)
        assertEquals(53, parsed.port)
        assertContentEquals(byteArrayOf(9, 8), parsed.payload)
        val named = udpPacket(3, "a.b".toByteArray(), 443, byteArrayOf(1))
        assertEquals("a.b", (Socks5Server.parseUdpHeader(DatagramPacket(named, named.size))?.destination as UdpDestination.Domain).name)
        val wrapped = Socks5Server.buildUdpHeader(InetAddress.getByName("::1"), 53, byteArrayOf(7, 7, 7), 1, 1)
        assertEquals(4 + 16 + 2 + 1, wrapped.size)
        assertEquals(4, wrapped[3].toInt())
        assertEquals(7, wrapped.last().toInt())
        // 이름 길이가 남은 바이트를 넘으면 버린다.
        val lying = byteArrayOf(0, 0, 0, 3, 50) + ByteArray(8)
        assertNull(Socks5Server.parseUdpHeader(DatagramPacket(lying, lying.size)))
    }
}
