package io.github.donggi.iroirobrowser.tether.core

import java.io.ByteArrayInputStream
import java.net.InetAddress
import java.net.Socket
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * HTTP 프록시를 루프백에서 — CONNECT 터널(머리 뒤에 붙어 온 바이트까지), 절대 URI 전달(origin-form·Host 채우기·hop-by-hop 걷기·
 * `Connection: close`), 스머글링 막기·머리 상한(줄 수·전체 마감), 403·502 본문, 차 있으면 다음 포트로(사용자 결정 2026-10-10), 반쯤 닫힌
 * 연결의 늦은 답.
 */
class HttpProxyServerTest {

    private val closer = Closer()

    @AfterTest
    fun cleanUp() = closer.close()

    private fun proxy(env: TetherEnvironment = testEnv()): HttpProxyServer {
        val p = closer.keep(HttpProxyServer(env, basePort = 0))
        assertTrue(p.start() > 0)
        return p
    }

    private fun ascii(text: String) = text.toByteArray(Charsets.ISO_8859_1)

    @Test
    fun CONNECT_는_200_뒤에_터널을_잇는다() {
        val echo = closer.keep(EchoServer())
        val p = proxy()
        val s = closer.keep(Socket(LOOPBACK, p.actualPort))
        s.soTimeout = 5000
        s.getOutputStream().write(ascii("CONNECT 127.0.0.1:${echo.port} HTTP/1.1\r\nHost: 127.0.0.1:${echo.port}\r\n\r\n"))
        assertEquals("HTTP/1.1 200 Connection Established\r\n\r\n", readHead(s.getInputStream()))
        s.getOutputStream().write(ascii("tunnel"))
        assertEquals("tunnel", String(readExactly(s.getInputStream(), 6)))
    }

    @Test
    fun CONNECT_머리와_함께_온_바이트도_잃지_않는다() {
        // 머리를 읽은 버퍼에 이미 들어온 바이트 — 같은 스트림을 중계까지 넘기지 않으면 사라진다.
        val echo = closer.keep(EchoServer())
        val p = proxy()
        val s = closer.keep(Socket(LOOPBACK, p.actualPort))
        s.soTimeout = 5000
        s.getOutputStream().write(ascii("CONNECT 127.0.0.1:${echo.port} HTTP/1.1\r\n\r\nearly-bytes"))
        assertEquals("HTTP/1.1 200 Connection Established\r\n\r\n", readHead(s.getInputStream()))
        assertEquals("early-bytes", String(readExactly(s.getInputStream(), 11)))
    }

    @Test
    fun 절대_URI_는_origin_form_으로_바꾸고_hop_by_hop_을_걷고_Connection_close_를_적는다() {
        val origin = closer.keep(OriginServer("hello-origin"))
        val names = TestDestinations(names = mapOf("origin.test" to listOf(LOOPBACK)))
        val p = proxy(testEnv(destinations = names))
        val reply = rawHttp(
            p.actualPort,
            ascii(
                "GET http://origin.test:${origin.port}/path?q=1 HTTP/1.1\r\n" +
                    "Proxy-Connection: keep-alive\r\nKeep-Alive: 300\r\nConnection: keep-alive\r\nTE: trailers\r\n" +
                    "Proxy-Authorization: Basic eDp5\r\nUpgrade: websocket\r\nX-Test: a\r\n\r\n",
            ),
        )
        assertEquals(200, reply.status)
        assertEquals("hello-origin", reply.text)
        val head = origin.heads.single()
        assertTrue(head.startsWith("GET /path?q=1 HTTP/1.1\r\n"), head)
        assertTrue(head.contains("\r\nX-Test: a\r\n"))
        assertTrue(head.contains("\r\nHost: origin.test:${origin.port}\r\n"), "Host 가 없으면 채운다")
        assertTrue(head.contains("\r\nConnection: close\r\n"))
        for (gone in listOf("Proxy-Connection", "Keep-Alive", "keep-alive\r\n", "TE:", "Proxy-Authorization", "Upgrade")) {
            assertFalse(head.contains(gone), "$gone 을 넘겼다: $head")
        }
    }

    @Test
    fun 경로가_없는_절대_URI_와_본문() {
        val origin = closer.keep(OriginServer())
        val p = proxy()
        val reply = rawHttp(
            p.actualPort,
            ascii("POST http://127.0.0.1:${origin.port} HTTP/1.1\r\nHost: 127.0.0.1:${origin.port}\r\nContent-Length: 7\r\n\r\npayload"),
        )
        assertEquals(200, reply.status)
        assertTrue(origin.heads.single().startsWith("POST / HTTP/1.1\r\n"))
        assertEquals("payload", origin.bodies.single(), "본문은 그대로 넘긴다")
    }

    @Test
    fun 스머글링의_재료와_틀린_요청은_400() {
        val origin = closer.keep(OriginServer())
        val p = proxy()
        val target = "http://127.0.0.1:${origin.port}/"
        val bad = listOf(
            "GET $target HTTP/1.1\r\nContent-Length: 1\r\nTransfer-Encoding: chunked\r\n\r\n",
            "GET $target HTTP/1.1\r\nHost: a\r\nHost: b\r\n\r\n",
            "GET $target HTTP/1.1\r\nX-A: foo\rEvil: 1\r\n\r\n", // 홀로 선 CR
            "GET https://127.0.0.1/ HTTP/1.1\r\n\r\n", // http:// 가 아닌 절대 URI
            "GET /relative HTTP/1.1\r\n\r\n",
            "GET\r\n\r\n",
            "CONNECT 127.0.0.1:notaport HTTP/1.1\r\n\r\n",
        )
        for (request in bad) assertEquals(400, rawHttp(p.actualPort, ascii(request)).status, request)
        assertTrue(origin.heads.isEmpty(), "아무것도 원격에 가지 않았다")
    }

    @Test
    fun 머리_한_줄이나_개수가_상한을_넘으면_431() {
        val p = proxy()
        val longLine = "GET http://127.0.0.1/ HTTP/1.1\r\nX-Long: ${"a".repeat(HttpProxyServer.MAX_HEADER_LINE_BYTES)}\r\n\r\n"
        assertEquals(431, rawHttp(p.actualPort, ascii(longLine)).status)
        val many = buildString {
            append("GET http://127.0.0.1/ HTTP/1.1\r\n")
            repeat(HttpProxyServer.MAX_HEADER_COUNT + 1) { append("X-$it: v\r\n") }
            append("\r\n")
        }
        assertEquals(431, rawHttp(p.actualPort, ascii(many)).status)
    }

    @Test
    fun 콜론_없는_머리_줄도_센다() {
        // 예전에는 담은 머리만 세어 `:` 없는 줄(걷어 버린다)은 끝없이 이어져도 걸리지 않았다.
        val origin = closer.keep(OriginServer())
        val p = proxy()
        val request = buildString {
            append("GET http://127.0.0.1:${origin.port}/ HTTP/1.1\r\n")
            repeat(HttpProxyServer.MAX_HEADER_COUNT + 1) { append("no-colon-$it\r\n") }
            append("\r\n")
        }
        assertEquals(431, rawHttp(p.actualPort, ascii(request)).status)
        assertTrue(origin.heads.isEmpty())
        // 상한까지는 받는다(걷어 버리고 넘긴다).
        val atLimit = buildString {
            append("GET http://127.0.0.1:${origin.port}/ HTTP/1.1\r\n")
            repeat(HttpProxyServer.MAX_HEADER_COUNT) { append("no-colon-$it\r\n") }
            append("\r\n")
        }
        assertEquals(200, rawHttp(p.actualPort, ascii(atLimit)).status)
    }

    @Test
    fun 머리를_다_받기까지의_전체_마감이_지나면_답_없이_닫는다() {
        // 3바이트씩 80ms 마다 흘린다 — 읽기 하나의 시한은 늘 지키지만 머리 전체는 1초 넘게 걸린다. 예전에는 읽기마다 시한이 새로 걸려 끝까지
        // 받고 넘겼다.
        val origin = closer.keep(OriginServer("late"))
        val p = proxy(testEnv(timings = TetherTimings(proxyHeaderMs = 300)))
        val request = ascii("GET http://127.0.0.1:${origin.port}/ HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
        val got = dribble(p.actualPort, request, chunk = 3, gapMs = 80)
        assertEquals(0, got.size, "답 없이 닫는다: ${String(got)}")
        assertTrue(origin.heads.isEmpty())
    }

    @Test
    fun 막힌_목적지는_403_과_까닭을_적은_본문() {
        val p = proxy(testEnv(destinations = DestinationFilter.SYSTEM))
        for (request in listOf("GET http://10.1.2.3/ HTTP/1.1\r\n\r\n", "CONNECT 192.168.49.1:8080 HTTP/1.1\r\n\r\n", "GET http://[::1]:80/ HTTP/1.1\r\n\r\n")) {
            val reply = rawHttp(p.actualPort, ascii(request))
            assertEquals(403, reply.status, request)
            assertEquals("text/plain; charset=utf-8", reply.header("Content-Type"))
            assertEquals(reply.body.size.toString(), reply.header("Content-Length"))
            assertTrue(reply.text.contains("192.168.49.*"))
            assertTrue(reply.text.contains("사설"))
        }
    }

    @Test
    fun 풀지_못한_이름은_403_이_아니라_502_와_그_까닭() {
        // 예전에는 풀이 실패도 '사설·로컬 주소' 403 이라 오타 난 주소가 프록시의 막기처럼 보였다.
        val unresolvable = DestinationFilter({ throw java.net.UnknownHostException("없음") }, localAddresses = { emptyList() })
        val p = proxy(testEnv(destinations = unresolvable))
        for (request in listOf("GET http://nowhere.invalid/ HTTP/1.1\r\n\r\n", "CONNECT nowhere.invalid:443 HTTP/1.1\r\n\r\n")) {
            val reply = rawHttp(p.actualPort, ascii(request))
            assertEquals(502, reply.status, request)
            assertEquals(HttpProxyServer.UNRESOLVED_HELP, reply.text)
            assertFalse(reply.text.contains("사설"))
        }
    }

    @Test
    fun 원격에_닿지_못하면_502() {
        val p = proxy()
        val reply = rawHttp(p.actualPort, ascii("CONNECT 127.0.0.1:${closedPort()} HTTP/1.1\r\n\r\n"), timeoutMs = 15_000)
        assertEquals(502, reply.status)
    }

    @Test
    fun 기본_포트가_차_있으면_다음_빈_포트로_가고_모두_차면_실패를_알린다() {
        // 사용자 결정(2026-10-10) — 8282 가 차 있으면 8283… 로(usb-tether·처음 합친 판처럼).
        val squatter = closer.keep(squatWithFreeNext())
        val base = squatter.localPort
        val p = closer.keep(HttpProxyServer(testEnv(), basePort = base, attempts = 2))
        assertEquals(base + 1, p.start())
        assertEquals(base + 1, p.actualPort)
        assertNull(p.lastError)
        // 옮겨 선 포트에서 정말 이 프록시가 답한다(쥔 소켓은 답하지 않는다).
        assertEquals(400, rawHttp(base + 1, ascii("GET /relative HTTP/1.1\r\n\r\n")).status)
        // 해 볼 자리가 모두 차면(기본 포트는 남이, 다음 포트는 앞 서버가) 실패를 알린다.
        val second = closer.keep(HttpProxyServer(testEnv(), basePort = base, attempts = 2))
        assertEquals(-1, second.start())
        assertEquals(StartError.PORT_TAKEN, second.lastError)
        assertEquals(-1, second.actualPort)
    }

    @Test
    fun 실제_값은_8282부터_열_개() {
        assertEquals(8282, HttpProxyServer.BASE_PORT)
        assertEquals(10, HttpProxyServer.PORT_ATTEMPTS)
    }

    @Test
    fun 반쯤_닫은_뒤의_늦은_답도_끝까지_넘긴다() {
        // 손님이 요청을 보내고 쓰기를 닫았다(반쯤 닫힘). 원격은 그 뒤 아홉 번에 나눠 천천히 답한다 — 예전에는 반대 방향을 정한 시간(5초)만
        // 기다리고 잘랐다. 시간을 줄여 꾸몄다: 멈춤을 보는 주기 50ms, 답은 100ms 간격으로 아홉 조각(옛 꼴이면 50ms 에서 잘린다).
        val pieces = (1..9).map { "piece-$it;" }
        val slow = closer.keep(SlowReplyServer(pieces, gapMs = 100))
        val p = proxy(testEnv(timings = TetherTimings(relayPollMs = 50)))
        // CONNECT 터널.
        Socket(LOOPBACK, p.actualPort).use { s ->
            s.soTimeout = 5000
            s.getOutputStream().write(ascii("CONNECT 127.0.0.1:${slow.port} HTTP/1.1\r\n\r\n"))
            assertEquals("HTTP/1.1 200 Connection Established\r\n\r\n", readHead(s.getInputStream()))
            s.getOutputStream().write(ascii("question"))
            s.shutdownOutput()
            assertEquals(pieces.joinToString(""), String(readAll(s.getInputStream()), Charsets.ISO_8859_1))
        }
        assertEquals("question", slow.received.single())
        // 절대 URI 전달도 같은 중계다.
        Socket(LOOPBACK, p.actualPort).use { s ->
            s.soTimeout = 5000
            s.getOutputStream().write(ascii("GET http://127.0.0.1:${slow.port}/ HTTP/1.1\r\n\r\n"))
            s.shutdownOutput()
            assertEquals(pieces.joinToString(""), String(readAll(s.getInputStream()), Charsets.ISO_8859_1))
        }
    }

    @Test
    fun 머리_줄_읽기와_호스트_포트_가르기() {
        val stream = ByteArrayInputStream(ascii("A: 1\r\nB: 2\n\r\n"))
        assertEquals("A: 1", HttpProxyServer.readHeaderLine(stream))
        assertEquals("B: 2", HttpProxyServer.readHeaderLine(stream))
        assertEquals("", HttpProxyServer.readHeaderLine(stream))
        assertNull(HttpProxyServer.readHeaderLine(stream))
        assertFailsWith<MalformedRequestException> { HttpProxyServer.readHeaderLine(ByteArrayInputStream(ascii("A\rB\r\n"))) }
        assertEquals("::1" to 8080, HttpProxyServer.splitHostPort("[::1]:8080", 80))
        assertEquals("::1" to 443, HttpProxyServer.splitHostPort("[::1]", 443))
        assertEquals("example.com" to 80, HttpProxyServer.splitHostPort("example.com", 80))
        assertEquals("example.com" to 8443, HttpProxyServer.splitHostPort("example.com:8443", 80))
        assertNull(HttpProxyServer.splitHostPort("example.com:x", 80))
        assertNull(HttpProxyServer.splitHostPort("[::1", 80))
    }

    @Test
    fun 바깥_연결은_거른_주소_객체로만_연다() {
        val origin = closer.keep(OriginServer())
        val dialed = java.util.concurrent.CopyOnWriteArrayList<InetAddress>()
        val recording = object : Outbound {
            override fun connect(address: InetAddress, port: Int, timeoutMs: Int): Socket {
                dialed += address
                return Outbound.DIRECT.connect(address, port, timeoutMs)
            }

            override fun openUdp() = Outbound.DIRECT.openUdp()
        }
        val names = TestDestinations(names = mapOf("rebind.test" to listOf(InetAddress.getByName("10.0.0.9"), LOOPBACK)), blocked = setOf(InetAddress.getByName("10.0.0.9")))
        val p = proxy(testEnv(destinations = names, outbound = recording))
        assertEquals(200, rawHttp(p.actualPort, ascii("GET http://rebind.test:${origin.port}/ HTTP/1.1\r\n\r\n")).status)
        assertEquals(listOf(LOOPBACK), dialed.toList())
    }
}

/**
 * [port] 에 [data] 를 [chunk] 바이트씩 [gapMs] 마다 흘려 쓰며 서버가 보낸 것을 연결이 닫힐 때까지 모은다 — 머리의 전체 마감 시험용. 서버가 먼저
 * 닫으면 쓰기가 던지므로 조용히 그친다.
 */
internal fun dribble(port: Int, data: ByteArray, chunk: Int, gapMs: Long): ByteArray =
    Socket(LOOPBACK, port).use { s ->
        s.soTimeout = 10_000
        val writer = thread(isDaemon = true) {
            try {
                for (piece in data.toList().chunked(chunk)) {
                    s.getOutputStream().write(piece.toByteArray())
                    s.getOutputStream().flush()
                    Thread.sleep(gapMs)
                }
            } catch (_: Exception) {
            }
        }
        val got = readAll(s.getInputStream())
        writer.join(10_000)
        got
    }
