package io.github.donggi.iroirobrowser.tether.core

import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.Socket
import java.net.SocketAddress
import java.net.URI
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 서버 셋을 한 환경(같은 스레드 예산·기록)으로 함께 세워 루프백에서 실제로 주고받는다 — 앱의 TetherService 가 세우는 꼴 그대로, 거르개만
 * 루프백을 받는 것으로 바꿨다. 그리고 바깥 문이 JVM 의 프록시 설정을 타지 않는지(`Socket(Proxy.NO_PROXY)`).
 */
class LoopbackIntegrationTest {

    private val closer = Closer()
    private val root: File = Files.createTempDirectory("tether-share").toFile()

    @AfterTest
    fun cleanUp() {
        closer.close()
        root.deleteRecursively()
    }

    private class Trio(val socks: Socks5Server, val http: HttpProxyServer, val files: FileServer)

    private fun startAll(env: TetherEnvironment): Trio {
        val trio = Trio(
            closer.keep(Socks5Server(env, port = 0)),
            closer.keep(HttpProxyServer(env, basePort = 0)),
            closer.keep(FileServer(env, DirectoryTree(root), { "<html>ui</html>".toByteArray() }, basePort = 0)),
        )
        assertTrue(trio.socks.start() > 0 && trio.http.start() > 0 && trio.files.start() > 0)
        return trio
    }

    @Test
    fun 셋을_함께_세워_프록시_둘과_파일_서버로_주고받는다() {
        val origin = closer.keep(OriginServer("through"))
        val log = LogLines()
        val budget = ThreadBudget()
        val counters = Counters()
        val lan = java.net.InetAddress.getByName("10.0.0.9")
        val names = TestDestinations(names = mapOf("origin.test" to listOf(LOOPBACK), "blocked.test" to listOf(lan)), blocked = setOf(lan))
        val trio = startAll(testEnv(destinations = names, budget = budget, log = log, counters = counters))

        // HTTP 프록시로 절대 URI.
        val viaHttp = rawHttp(trio.http.actualPort, "GET http://origin.test:${origin.port}/a HTTP/1.1\r\n\r\n".toByteArray())
        assertEquals("through", viaHttp.text)

        // SOCKS5 의 CONNECT 위에 맨 HTTP.
        Socket(LOOPBACK, trio.socks.actualPort).use { s ->
            s.soTimeout = 5000
            val out = s.getOutputStream()
            val inp = s.getInputStream()
            out.write(byteArrayOf(5, 1, 0))
            assertContentEquals(byteArrayOf(5, 0), readExactly(inp, 2))
            val name = "origin.test".toByteArray()
            out.write(byteArrayOf(5, 1, 0, 3, name.size.toByte()) + name + byteArrayOf((origin.port shr 8).toByte(), origin.port.toByte()))
            assertEquals(0, readExactly(inp, 10)[1].toInt())
            out.write("GET /b HTTP/1.1\r\nHost: origin.test\r\n\r\n".toByteArray())
            assertEquals("through", parseReply(readAll(inp)).text)
        }

        // 파일 서버 — 올리고, 목록에서 보고, Range 로 받는다.
        val data = ByteArray(70_000) { (it % 251).toByte() }
        val up = rawHttp(
            trio.files.actualPort,
            "PUT /api/file?path=big.bin HTTP/1.1\r\nHost: 192.168.49.1:${trio.files.actualPort}\r\nContent-Length: ${data.size}\r\n\r\n".toByteArray() + data,
        )
        assertEquals(200, up.status)
        val list = rawHttp(trio.files.actualPort, "GET /api/list?path= HTTP/1.1\r\nHost: 192.168.49.1\r\n\r\n".toByteArray())
        assertTrue(list.text.contains("\"name\":\"big.bin\",\"dir\":false,\"size\":70000"))
        val slice = rawHttp(trio.files.actualPort, "GET /api/file?path=big.bin HTTP/1.1\r\nHost: 192.168.49.1\r\nRange: bytes=65536-65545\r\n\r\n".toByteArray())
        assertEquals(206, slice.status)
        assertContentEquals(data.copyOfRange(65536, 65546), slice.body)

        // 실패 둘 — 막힌 목적지(403)와 모르는 이름(풀지 못함 — 502). 그래도 기록에 목적지 이름이 남지 않는다.
        assertEquals(403, rawHttp(trio.http.actualPort, "GET http://blocked.test/ HTTP/1.1\r\n\r\n".toByteArray()).status)
        assertEquals(502, rawHttp(trio.http.actualPort, "CONNECT nowhere.test:443 HTTP/1.1\r\n\r\n".toByteArray()).status)

        waitUntil { budget.inUse == 0 }
        assertTrue(counters.bytesIn.get() >= data.size, "받은 바이트를 센다")
        assertTrue(counters.bytesOut.get() > 0)
        assertTrue(log.lines.isNotEmpty())
        for (line in log.lines) {
            for (secret in listOf("origin.test", "blocked.test", "nowhere.test", ":${origin.port}")) {
                assertFalse(line.contains(secret), "기록에 목적지가 남았다: $line")
            }
        }
    }

    @Test
    fun 실제_거르개로_세우면_루프백_손님은_셋_모두에서_막힌다() {
        val counters = Counters()
        val env = testEnv(peers = realPeerFilter(), counters = counters)
        val trio = startAll(env)
        for (port in listOf(trio.socks.actualPort, trio.http.actualPort, trio.files.actualPort)) {
            val got = try {
                Socket(LOOPBACK, port).use { s ->
                    s.soTimeout = 5000
                    s.getOutputStream().write(byteArrayOf(5, 1, 0))
                    s.getInputStream().read()
                }
            } catch (_: IOException) {
                -1
            }
            assertEquals(-1, got)
        }
        waitUntil { counters.rejections.size == 3 }
        assertTrue(counters.rejections.all { it.reason == PeerRejection.Reason.NOT_GROUP_OWNER_ADDRESS })
    }

    @Test
    fun 셋이_스레드_예산을_함께_쓴다() {
        val echo = closer.keep(EchoServer())
        val budget = ThreadBudget(3)
        val trio = startAll(testEnv(budget = budget))
        // SOCKS5 터널 하나(2) + 파일 서버 연결 하나(1) = 3 — 그 뒤의 HTTP 프록시 연결은 받지 않는다.
        val tunnel = closer.keep(Socket(LOOPBACK, trio.socks.actualPort))
        tunnel.soTimeout = 5000
        tunnel.getOutputStream().write(byteArrayOf(5, 1, 0))
        readExactly(tunnel.getInputStream(), 2)
        tunnel.getOutputStream().write(byteArrayOf(5, 1, 0, 1, 127, 0, 0, 1, (echo.port shr 8).toByte(), echo.port.toByte()))
        readExactly(tunnel.getInputStream(), 10)
        val idleFile = closer.keep(Socket(LOOPBACK, trio.files.actualPort))
        waitUntil { budget.inUse == 3 }
        val refused = try {
            Socket(LOOPBACK, trio.http.actualPort).use { s ->
                s.soTimeout = 5000
                s.getOutputStream().write("GET http://127.0.0.1/ HTTP/1.1\r\n\r\n".toByteArray())
                s.getInputStream().read()
            }
        } catch (_: IOException) {
            -1
        }
        assertEquals(-1, refused)
        idleFile.close()
        tunnel.close()
        waitUntil { budget.inUse == 0 }
    }

    @Test
    fun 바깥_문은_JVM_의_프록시_설정을_타지_않는다() {
        val echo = closer.keep(EchoServer())
        val asked = AtomicInteger()
        val dead = closedPort()
        val original = ProxySelector.getDefault()
        // 모든 연결을 죽은 SOCKS 프록시로 보내는 선택기 — 맨 Socket() 은 이것을 묻는다.
        ProxySelector.setDefault(object : ProxySelector() {
            override fun select(uri: URI?): List<Proxy> {
                asked.incrementAndGet()
                return listOf(Proxy(Proxy.Type.SOCKS, InetSocketAddress(LOOPBACK, dead)))
            }

            override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {}
        })
        try {
            // 대조 — 맨 Socket() 은 선택기를 묻고 죽은 프록시로 가 실패한다(이 덫이 실제로 무는지).
            val plainFailed = try {
                Socket().use { it.connect(InetSocketAddress(LOOPBACK, echo.port), 15_000) }
                false
            } catch (_: IOException) {
                true
            }
            assertTrue(asked.get() > 0 && plainFailed, "대조가 서지 않았다(이 JDK 는 맨 Socket 에 선택기를 묻지 않는다)")
            asked.set(0)
            Outbound.DIRECT.connect(LOOPBACK, echo.port, 5000).use { s ->
                s.soTimeout = 5000
                s.getOutputStream().write("direct".toByteArray())
                assertEquals("direct", String(readExactly(s.getInputStream(), 6)))
            }
            assertEquals(0, asked.get(), "바깥 문은 선택기를 묻지 않는다")
        } finally {
            ProxySelector.setDefault(original)
        }
    }

    @Test
    fun 바깥_문의_UDP_소켓은_와일드카드의_임의_포트() {
        Outbound.DIRECT.openUdp().use { u ->
            assertTrue(u.localPort > 0)
            assertTrue(u.localAddress.isAnyLocalAddress || u.localAddress == InetAddress.getByName("0.0.0.0"))
        }
    }

}
