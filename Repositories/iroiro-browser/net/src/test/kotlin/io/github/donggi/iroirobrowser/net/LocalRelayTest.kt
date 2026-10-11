package io.github.donggi.iroirobrowser.net

import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * 앱 안 중계([LocalRelay])의 규약. 원격 프록시는 이 시험이 세운 가짜다(평문 — TLS 쪽은 기기 실험이 사용자의 Squid 와 같은 설정의 시험
 * 프록시로 본다). 가짜는 받은 첫 줄과 계정을 적어 두어, 중계가 **무엇을 원격에 보냈는지**를 시험이 본다.
 */
class LocalRelayTest {

    private val closeables = ArrayList<Closeable>()

    @AfterTest
    fun cleanUp() {
        closeables.forEach { runCatching { it.close() } }
    }

    private fun <T : Closeable> keep(c: T): T = c.also { closeables += it }

    private fun relayTo(
        upstreamPort: Int,
        user: String = "up-user",
        password: String = "up-pass",
        listener: LocalRelay.Listener = LocalRelay.Listener.NONE,
        maxClients: Int = LocalRelay.MAX_CLIENTS,
        maxPending: Int = LocalRelay.MAX_PENDING,
        authTimeoutMs: Int = LocalRelay.AUTH_TIMEOUT_MS,
        errorPage: (Int, String) -> String? = { _, _ -> null },
    ): LocalRelay = keep(
        LocalRelay(
            UpstreamProxy(false, "127.0.0.1", upstreamPort, user, password),
            null,
            null,
            listener = listener,
            maxClients = maxClients,
            maxPending = maxPending,
            authTimeoutMs = authTimeoutMs,
            errorPage = errorPage,
        ),
    ).start()

    /** 중계가 알린 일을 적는다. */
    private class Events : LocalRelay.Listener {
        val upstream = CopyOnWriteArrayList<LocalRelay.UpstreamEvent>()
        val warmups = java.util.concurrent.atomic.AtomicInteger()
        val tags = CopyOnWriteArrayList<String>()

        override fun onUpstream(event: LocalRelay.UpstreamEvent) {
            upstream += event
        }

        override fun onWarmedUp(tag: String) {
            warmups.incrementAndGet()
            tags += tag
        }

        /**
         * 데우기 알림이 [n] 번 올 때까지(최대 5초) 기다린 뒤의 수. 중계는 204 를 쓴 **뒤에** 알린다 — 답을 읽은 손님이 알림보다 먼저일 수 있다
         * (곧바로 세던 시험이 가끔 0 을 읽었다).
         */
        fun awaitWarmups(n: Int): Int {
            val end = System.nanoTime() + 5_000_000_000L
            while (warmups.get() < n && System.nanoTime() < end) Thread.sleep(5)
            return warmups.get()
        }
    }

    private fun connect(relay: LocalRelay): Socket = keep(Socket("127.0.0.1", relay.port)).apply { soTimeout = 5_000 }

    private fun Socket.send(text: String) {
        getOutputStream().write(text.toByteArray(Charsets.ISO_8859_1))
        getOutputStream().flush()
    }

    private fun Socket.head(): HttpHead = assertNotNull(HttpHead.read(getInputStream()))

    private fun auth(relay: LocalRelay) = "Proxy-Authorization: " + UpstreamProxy.basic(relay.sessionUser, relay.sessionPassword) + "\r\n"

    @Test
    fun 계정을_주지_않으면_407_과_realm_을_준다() {
        val up = keep(FakeUpstream())
        val relay = relayTo(up.port)
        val s = connect(relay)
        s.send("CONNECT example.test:443 HTTP/1.1\r\nHost: example.test:443\r\n\r\n")
        val reply = s.head()
        assertEquals(407, reply.status)
        assertEquals("Basic realm=\"${relay.realm}\"", reply.header("Proxy-Authenticate"))
        assertTrue(relay.realm.startsWith(LocalRelay.REALM_PREFIX))
        assertTrue(up.startLines.isEmpty(), "계정 없는 요청이 원격으로 갔다")
    }

    @Test
    fun 틀린_계정도_407() {
        val up = keep(FakeUpstream())
        val relay = relayTo(up.port)
        val s = connect(relay)
        s.send("CONNECT example.test:443 HTTP/1.1\r\nProxy-Authorization: " + UpstreamProxy.basic("x", "y") + "\r\n\r\n")
        assertEquals(407, s.head().status)
        assertTrue(up.startLines.isEmpty())
    }

    @Test
    fun CONNECT_는_원격_계정으로_터널을_열고_바이트를_잇는다() {
        val up = keep(FakeUpstream())
        val relay = relayTo(up.port)
        val s = connect(relay)
        s.send("CONNECT example.test:443 HTTP/1.1\r\nHost: example.test:443\r\n" + auth(relay) + "\r\n")
        assertEquals(200, s.head().status)
        s.send("ping")
        val echo = ByteArray(4)
        readFully(s.getInputStream(), echo)
        assertContentEquals("ping".toByteArray(), echo)
        assertEquals(listOf("CONNECT example.test:443 HTTP/1.1"), up.startLines)
        // 원격에는 원격 계정이 가고, 중계의 세션 계정은 가지 않는다.
        assertEquals<List<String?>>(listOf(UpstreamProxy.basic("up-user", "up-pass")), up.authorizations)
    }

    @Test
    fun 이름이_주소_여럿으로_풀리면_붙는_주소를_찾는다() {
        // localhost 가 IPv4·IPv6 둘로 풀리는 곳에서만 돈다. 원격은 IPv6 루프백에서만 듣는다 — 셀룰러가 IPv6 를 먼저 주는데 프록시가 IPv4 로만
        // 받는 것과 거꾸로 된 꼴이다(어느 쪽이 먼저 오든 붙는 주소를 찾아야 한다).
        val addresses = runCatching { InetAddress.getAllByName("localhost").toList() }.getOrDefault(emptyList())
        val v6 = addresses.firstOrNull { it is java.net.Inet6Address } ?: return
        if (addresses.none { it is java.net.Inet4Address }) return
        val up = keep(runCatching { FakeUpstream(bind = v6) }.getOrNull() ?: return)
        val relay = keep(LocalRelay(UpstreamProxy(false, "localhost", up.port, "up-user", "up-pass"), null, null)).start()
        repeat(2) {
            val s = connect(relay)
            s.send("CONNECT example.test:443 HTTP/1.1\r\nHost: example.test:443\r\n" + auth(relay) + "\r\n")
            assertEquals(200, s.head().status)
        }
        assertEquals(2, up.startLines.size)
    }

    @Test
    fun 절대_주소_요청은_프록시_머리를_갈아_끼워_넘긴다() {
        val up = keep(FakeUpstream())
        val relay = relayTo(up.port)
        val s = connect(relay)
        s.send("GET http://example.test/x HTTP/1.1\r\nHost: example.test\r\nProxy-Connection: keep-alive\r\n" + auth(relay) + "\r\n")
        val reply = s.head()
        assertEquals(200, reply.status)
        assertEquals("ok", String(reply.extra) + s.getInputStream().readBytes().decodeToString())
        assertEquals(listOf("GET http://example.test/x HTTP/1.1"), up.startLines)
        assertEquals<List<String?>>(listOf(UpstreamProxy.basic("up-user", "up-pass")), up.authorizations)
        val sent = up.headers.single()
        assertFalse(sent.any { it.first.equals("Proxy-Connection", true) }, "손님의 프록시 머리가 원격으로 갔다")
        assertEquals("close", sent.first { it.first.equals("Connection", true) }.second)
    }

    @Test
    fun 같은_연결에서_407_뒤에_계정을_주면_이어진다() {
        // 브라우저는 407 을 받은 연결을 이어 쓸 수 있다(몸이 없고 keep-alive 면).
        val up = keep(FakeUpstream())
        val relay = relayTo(up.port)
        val s = connect(relay)
        s.send("CONNECT example.test:443 HTTP/1.1\r\n\r\n")
        val first = s.head()
        assertEquals(407, first.status)
        assertEquals("keep-alive", first.header("Connection"))
        s.send("CONNECT example.test:443 HTTP/1.1\r\n" + auth(relay) + "\r\n")
        assertEquals(200, s.head().status)
    }

    @Test
    fun 같은_연결에서_407_뒤에_계정을_주면_절대_주소_요청도_넘어간다() {
        // WebView 는 평문 프록시의 407 뒤에 같은 연결로 계정을 실어 다시 보낸다(기기 실측: http 쪽이 여기서 멈췄다).
        val up = keep(FakeUpstream())
        val relay = relayTo(up.port)
        val s = connect(relay)
        val request = "GET http://example.test/x HTTP/1.1\r\nHost: example.test\r\nProxy-Connection: keep-alive\r\n"
        s.send("$request\r\n")
        val first = s.head()
        assertEquals(407, first.status)
        assertEquals("keep-alive", first.header("Connection"))
        s.send(request + auth(relay) + "\r\n")
        val reply = s.head()
        assertEquals(200, reply.status)
        assertEquals("ok", String(reply.extra) + s.getInputStream().readBytes().decodeToString())
    }

    @Test
    fun 원격이_계정을_거절하면_502_이고_407_을_넘기지_않는다() {
        val up = keep(FakeUpstream(user = "other", password = "other"))
        val relay = relayTo(up.port)
        val tunnel = connect(relay)
        tunnel.send("CONNECT example.test:443 HTTP/1.1\r\n" + auth(relay) + "\r\n")
        val reply = tunnel.head()
        assertEquals(502, reply.status)
        assertEquals("upstream-auth", reply.header(LocalRelay.MARKER))
        assertEquals(null, reply.header("Proxy-Authenticate"))

        val get = connect(relay)
        get.send("GET http://example.test/ HTTP/1.1\r\nHost: example.test\r\n" + auth(relay) + "\r\n")
        val forwarded = get.head()
        assertEquals(502, forwarded.status)
        assertEquals("upstream-auth", forwarded.header(LocalRelay.MARKER))
    }

    @Test
    fun 원격에_닿지_못하면_502_로_끝나고_다른_길로_가지_않는다() {
        // 비어 있는 포트 — 열었다 닫는다.
        val dead = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
        val relay = relayTo(dead)
        val s = connect(relay)
        s.send("CONNECT example.test:443 HTTP/1.1\r\n" + auth(relay) + "\r\n")
        val reply = s.head()
        assertEquals(502, reply.status)
        assertEquals("upstream-unreachable", reply.header(LocalRelay.MARKER))
    }

    @Test
    fun 머리가_너무_크면_431() {
        val up = keep(FakeUpstream())
        val relay = relayTo(up.port)
        val s = connect(relay)
        val huge = "X-Fill: " + "a".repeat(HttpHead.MAX_BYTES) + "\r\n"
        runCatching { s.send("CONNECT example.test:443 HTTP/1.1\r\n$huge\r\n") }
        assertEquals(431, s.head().status)
    }

    @Test
    fun 닫은_뒤에는_받지_않는다() {
        val up = keep(FakeUpstream())
        val relay = relayTo(up.port)
        val port = relay.port
        relay.close()
        assertFailsWith<IOException> {
            Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 1_000) }
        }
    }

    @Test
    fun 원격_계정은_문자열에_나오지_않는다() {
        val proxy = UpstreamProxy(true, "proxy.example", 10443, "user-x", "secret-y")
        assertFalse(proxy.toString().contains("user-x"))
        assertFalse(proxy.toString().contains("secret-y"))
        assertEquals("UpstreamProxy(https://proxy.example:10443)", proxy.toString())
    }

    @Test
    fun 세션_계정은_중계마다_다르다() {
        val up = keep(FakeUpstream())
        val a = relayTo(up.port)
        val b = relayTo(up.port)
        assertTrue(a.sessionPassword != b.sessionPassword)
        assertTrue(a.realm != b.realm)
        assertEquals(32, a.sessionPassword.length)
    }

    @Test
    fun 원격이_정해지지_않았으면_계정을_확인한_뒤_502() {
        val relay = keep(LocalRelay(null, null, null)).start()
        assertFalse(relay.isConfigured)
        val s = connect(relay)
        s.send("CONNECT example.test:443 HTTP/1.1\r\n\r\n")
        assertEquals(407, s.head().status)
        s.send("CONNECT example.test:443 HTTP/1.1\r\n" + auth(relay) + "\r\n")
        val reply = s.head()
        assertEquals(502, reply.status)
        assertEquals("not-configured", reply.header(LocalRelay.MARKER))
    }

    @Test
    fun 데우기_주소는_원격에_보내지_않고_204() {
        val up = keep(FakeUpstream())
        val events = Events()
        val relay = relayTo(up.port, listener = events)
        val s = connect(relay)
        s.send("GET ${LocalRelay.WARMUP_URL} HTTP/1.1\r\nHost: warmup.iroiro.invalid\r\n\r\n")
        assertEquals(407, s.head().status)
        assertEquals(0, events.warmups.get(), "계정 없이 데워졌다")
        s.send("GET ${LocalRelay.WARMUP_URL} HTTP/1.1\r\nHost: warmup.iroiro.invalid\r\n" + auth(relay) + "\r\n")
        val reply = s.head()
        assertEquals(204, reply.status)
        assertEquals(1, events.awaitWarmups(1))
        assertTrue(up.startLines.isEmpty(), "데우기가 원격으로 갔다")
        assertTrue(events.upstream.isEmpty())
    }

    @Test
    fun 데우기는_표를_알린다() {
        val up = keep(FakeUpstream())
        val events = Events()
        val relay = relayTo(up.port, listener = events)
        for (target in listOf("${LocalRelay.WARMUP_URL}?p1", LocalRelay.WARMUP_URL)) {
            val s = connect(relay)
            s.send("GET $target HTTP/1.1\r\nHost: warmup.iroiro.invalid\r\n" + auth(relay) + "\r\n")
            assertEquals(204, s.head().status)
        }
        assertEquals(2, events.awaitWarmups(2))
        assertEquals(listOf("p1", ""), events.tags.toList())
        assertTrue(up.startLines.isEmpty())
    }

    @Test
    fun 원격과의_일을_알린다() {
        val up = keep(FakeUpstream())
        val events = Events()
        val relay = relayTo(up.port, listener = events)
        val ok = connect(relay)
        ok.send("CONNECT example.test:443 HTTP/1.1\r\n" + auth(relay) + "\r\n")
        assertEquals(200, ok.head().status)
        ok.close()

        val refused = keep(FakeUpstream(connectStatus = 403))
        val events2 = Events()
        val relay2 = relayTo(refused.port, listener = events2)
        val r = connect(relay2)
        r.send("CONNECT example.test:8443 HTTP/1.1\r\n" + auth(relay2) + "\r\n")
        val denied = r.head()
        assertEquals(502, denied.status)
        assertEquals("upstream-status-403", denied.header(LocalRelay.MARKER))

        val wrong = keep(FakeUpstream(user = "other", password = "other"))
        val events3 = Events()
        val relay3 = relayTo(wrong.port, listener = events3)
        val w = connect(relay3)
        w.send("CONNECT example.test:443 HTTP/1.1\r\n" + auth(relay3) + "\r\n")
        assertEquals(502, w.head().status)

        val dead = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
        val events4 = Events()
        val relay4 = relayTo(dead, listener = events4)
        val d = connect(relay4)
        d.send("GET http://example.test/ HTTP/1.1\r\n" + auth(relay4) + "\r\n")
        assertEquals(502, d.head().status)

        assertEquals(listOf(LocalRelay.UpstreamEvent.REACHED), events.upstream.toList())
        // 원격이 그 목적지만 거절한 것은 '닿았다' 다 — 상태 표시가 '끊김' 으로 바뀌지 않게.
        assertEquals(listOf(LocalRelay.UpstreamEvent.REACHED), events2.upstream.toList())
        assertEquals(listOf(LocalRelay.UpstreamEvent.AUTH_REJECTED), events3.upstream.toList())
        assertEquals(listOf(LocalRelay.UpstreamEvent.UNREACHABLE), events4.upstream.toList())
    }

    @Test
    fun 계정을_보이지_않는_손님이_많아도_진짜_손님을_막지_못한다() {
        // 같은 기기의 다른 앱이 아무 바이트도 보내지 않는 연결을 잔뜩 연다(앞 판은 받자마자 자리를 잡아 이것으로 모든 길이 막혔다).
        val up = keep(FakeUpstream())
        val relay = relayTo(up.port, maxClients = 2, maxPending = 4)
        val idle = (1..20).map { connect(relay) }
        Thread.sleep(300)
        // 진짜 손님(계정을 안다)은 곧바로 터널을 연다 — 자리는 계정을 확인한 뒤에 잡는다.
        val real = connect(relay)
        real.send("CONNECT example.test:443 HTTP/1.1\r\nHost: example.test:443\r\n" + auth(relay) + "\r\n")
        assertEquals(200, real.head().status)
        // 계정을 모르는 새 손님도 407 을 받는다(기다리는 자리가 차 있으면 가장 오래 기다린 것이 나간다).
        val other = connect(relay)
        other.send("CONNECT example.test:443 HTTP/1.1\r\n\r\n")
        assertEquals(407, other.head().status)
        // 가장 먼저 연 버티는 연결은 내보내졌다.
        assertEquals(-1, idle.first().getInputStream().read(), "가장 오래 버틴 연결이 남아 있다")
    }

    @Test
    fun 계정을_정한_시간_안에_보이지_않으면_끊는다() {
        val up = keep(FakeUpstream())
        val relay = relayTo(up.port, authTimeoutMs = 400)
        // 한 바이트씩 흘려 보내도(읽기마다의 시간 제한이 아니라) 받은 때부터 센다.
        val slow = connect(relay)
        val started = System.currentTimeMillis()
        var closedByRelay = false
        try {
            for (c in "CONNECT example.test:443 HTTP/1.1\r\nX-Slow: ") {
                slow.send(c.toString())
                Thread.sleep(50)
            }
            closedByRelay = slow.getInputStream().read() == -1
        } catch (e: IOException) {
            closedByRelay = true
        }
        assertTrue(closedByRelay, "시간을 넘긴 손님이 남아 있다")
        assertTrue(System.currentTimeMillis() - started < 4_000, "너무 늦게 끊었다")
        // 아무것도 보내지 않는 손님도.
        val idle = connect(relay)
        assertEquals(-1, idle.getInputStream().read())
    }

    @Test
    fun 계정을_확인한_손님이_상한을_넘으면_503() {
        val up = keep(FakeUpstream())
        val relay = relayTo(up.port, maxClients = 1)
        val first = connect(relay)
        first.send("CONNECT example.test:443 HTTP/1.1\r\n" + auth(relay) + "\r\n")
        assertEquals(200, first.head().status)
        val second = connect(relay)
        second.send("CONNECT example.test:443 HTTP/1.1\r\n" + auth(relay) + "\r\n")
        val busy = second.head()
        assertEquals(503, busy.status)
        assertEquals("busy", busy.header(LocalRelay.MARKER))
        // 앞 터널이 끝나면 자리가 돌아온다.
        first.close()
        val deadline = System.currentTimeMillis() + 3_000
        var status = -1
        while (System.currentTimeMillis() < deadline) {
            val third = connect(relay)
            third.send("CONNECT example.test:443 HTTP/1.1\r\n" + auth(relay) + "\r\n")
            status = third.head().status
            if (status == 200) break
            Thread.sleep(100)
        }
        assertEquals(200, status)
    }

    @Test
    fun 닫으면_이어져_있던_터널도_끊는다() {
        // 길을 바꾸면(설정·스위치) 옛 중계를 닫는다 — 그때 열려 있던 터널(웹소켓·HTTP/2)이 옛 원격으로 계속 오가지 않아야 한다.
        val up = keep(FakeUpstream())
        val relay = relayTo(up.port)
        val s = connect(relay)
        s.send("CONNECT example.test:443 HTTP/1.1\r\nHost: example.test:443\r\n" + auth(relay) + "\r\n")
        assertEquals(200, s.head().status)
        s.send("ping")
        val echo = ByteArray(4)
        readFully(s.getInputStream(), echo)
        relay.close()
        // 시간 제한에 걸린 것(이어져 있다)과 끊긴 것을 가른다.
        s.soTimeout = 2_000
        val ended = try {
            s.getInputStream().read() == -1
        } catch (e: java.net.SocketTimeoutException) {
            false
        } catch (e: IOException) {
            true
        }
        assertTrue(ended, "닫은 중계의 터널이 이어져 있다")
    }

    @Test
    fun 닫기는_소켓을_부른_스레드에서_닫지_않는다() {
        // TLS 소켓을 닫으면 close_notify 를 쓴다 — 메인 스레드에서 닫으면 안드로이드가 앱을 죽였다(프록시 스위치를 끌 때). 닫는 일은 closer 에 맡긴다.
        val up = keep(FakeUpstream())
        val handedOff = CopyOnWriteArrayList<Runnable>()
        val relay = keep(
            LocalRelay(UpstreamProxy(false, "127.0.0.1", up.port, "up-user", "up-pass"), null, null, closer = { handedOff += it }),
        ).start()
        val s = connect(relay)
        s.send("CONNECT example.test:443 HTTP/1.1\r\nHost: example.test:443\r\n" + auth(relay) + "\r\n")
        assertEquals(200, s.head().status)
        relay.close()
        assertEquals(1, handedOff.size)
        // 맡긴 일이 돌기 전에는 터널이 그대로다 — 부른 스레드는 소켓을 닫지 않았다.
        s.send("ping")
        val echo = ByteArray(4)
        readFully(s.getInputStream(), echo)
        assertEquals("ping", echo.decodeToString())
        handedOff.first().run()
        s.soTimeout = 2_000
        val ended = try {
            s.getInputStream().read() == -1
        } catch (e: java.net.SocketTimeoutException) {
            false
        } catch (e: IOException) {
            true
        }
        assertTrue(ended, "맡긴 일이 돈 뒤에도 터널이 이어져 있다")
    }

    @Test
    fun 닫힌_뒤에_온_원격의_답은_넘기지_않는다() {
        // 연결을 끊는 일은 closer 가 따로 하므로 원격의 답이 그보다 먼저 올 수 있다 — 시험의 closer 는 맡은 일을 들고만 있어 늘 그렇다.
        for (request in listOf(
            "CONNECT example.test:443 HTTP/1.1\r\nHost: example.test:443\r\n",
            "GET http://example.test/ HTTP/1.1\r\nHost: example.test\r\n",
        )) {
            val method = request.substringBefore(' ')
            val gate = java.util.concurrent.CountDownLatch(1)
            val up = keep(FakeUpstream(replyGate = gate))
            val held = CopyOnWriteArrayList<Runnable>()
            val relay = keep(
                LocalRelay(UpstreamProxy(false, "127.0.0.1", up.port, "up-user", "up-pass"), null, null, closer = { held += it }),
            ).start()
            val s = connect(relay)
            s.send(request + auth(relay) + "\r\n")
            val deadline = System.currentTimeMillis() + 5_000
            while (up.startLines.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(20)
            assertEquals(1, up.startLines.size, "원격이 $method 를 받지 못했다")
            relay.close()
            gate.countDown()
            val reply = try {
                HttpHead.read(s.getInputStream())
            } catch (e: java.net.SocketTimeoutException) {
                fail("$method — 중계가 손님을 끝내지 않았다")
            } catch (e: IOException) {
                null
            }
            assertEquals(null, reply?.startLine, "$method — 닫은 중계가 원격의 답을 넘겼다")
            held.forEach { it.run() }
        }
    }

    @Test
    fun 중계가_만든_오류에는_까닭을_적은_쪽을_싣는다() {
        // http 쪽은 몸이 비면 하얀 빈 화면이 된다 — 앱이 준 쪽을 싣는다(문구는 앱이 만든다).
        val relay = keep(
            LocalRelay(null, null, null, errorPage = { status, reason -> "<p>$status $reason 한글</p>" }),
        ).start()
        val s = connect(relay)
        s.send("GET http://example.test/ HTTP/1.1\r\nHost: example.test\r\n" + auth(relay) + "\r\n")
        val reply = s.head()
        assertEquals(502, reply.status)
        assertEquals("not-configured", reply.header(LocalRelay.MARKER))
        assertEquals("text/html; charset=utf-8", reply.header("Content-Type"))
        assertTrue(reply.header("Content-Security-Policy").orEmpty().startsWith("default-src 'none'"))
        val body = reply.extra + s.getInputStream().readBytes()
        assertEquals("<p>502 not-configured 한글</p>", body.decodeToString())
        assertEquals(body.size.toString(), reply.header("Content-Length"))
        // 407 과 데우기에는 싣지 않는다.
        val t = connect(relay)
        t.send("GET http://example.test/ HTTP/1.1\r\n\r\n")
        val challenge = t.head()
        assertEquals(407, challenge.status)
        assertEquals("0", challenge.header("Content-Length"))
    }

    private fun readFully(input: InputStream, into: ByteArray) {
        var at = 0
        while (at < into.size) {
            val n = input.read(into, at, into.size - at)
            if (n < 0) throw IOException("closed after $at bytes")
            at += n
        }
    }

    /** 가짜 원격 프록시(평문). 계정이 맞으면 CONNECT 는 메아리, 절대 주소 요청은 `ok`. 받은 것을 적어 둔다. */
    @Test
    fun 터널을_여는_사이_중계가_닫혀도_일꾼이_죽지_않는다() {
        // 원격이 CONNECT 에 답하기 전에 중계를 닫는다. 예전에는 원격이 200 을 준 뒤 중계가 내려간 일꾼 풀에 터널을 맡기려다 그
        // `RejectedExecutionException` 이 일꾼 스레드 밖으로 나가 **앱이 죽었다**(2026-10-04 기기의 크래시 기록). 지금은 닫힌 뒤 온 원격의
        // 답을 넘기지 않고 끝난다(연결을 끊는 일은 따로 된 스레드가 하므로 답이 그보다 먼저 올 수 있다). 어느 쪽이든 일꾼에서 잡히지 않은
        // 예외가 없어야 한다 — 이 시험이 모은다.
        val uncaught = CopyOnWriteArrayList<Throwable>()
        val before = Thread.getDefaultUncaughtExceptionHandler()
        // 중계의 일꾼만 본다(가짜 원격의 스레드가 닫힌 연결에 쓰다 던지는 것은 이 시험의 일이 아니다).
        Thread.setDefaultUncaughtExceptionHandler { t, e -> if (t.name == "iroiro-relay") uncaught += e }
        try {
            val gate = java.util.concurrent.CountDownLatch(1)
            val up = keep(FakeUpstream(replyGate = gate))
            val relay = relayTo(up.port)
            val s = connect(relay)
            s.send("CONNECT example.test:443 HTTP/1.1\r\nHost: example.test:443\r\n" + auth(relay) + "\r\n")
            val deadline = System.currentTimeMillis() + 5_000
            while (up.startLines.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(20)
            assertEquals(listOf("CONNECT example.test:443 HTTP/1.1"), up.startLines, "원격이 CONNECT 를 받지 못했다")
            relay.close()
            gate.countDown()
            // 닫힌 중계는 원격의 200 을 넘기지 않는다 — 손님은 200 없이 끝을 본다(앞 판은 200 을 준 뒤 닫았다). 일꾼이 죽었다면 예외가 남는다.
            val reply = try {
                HttpHead.read(s.getInputStream())
            } catch (e: IOException) {
                null
            }
            assertEquals(null, reply?.startLine, "닫은 중계가 터널을 열었다")
            Thread.sleep(200)
            assertTrue(uncaught.isEmpty(), "일꾼에서 잡히지 않은 예외: $uncaught")
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(before)
        }
    }

    private class FakeUpstream(
        private val user: String = "up-user",
        private val password: String = "up-pass",
        private val connectStatus: Int = 200,
        /** 주면 답(CONNECT 의 200·절대 주소 요청의 `ok`)을 이것이 열릴 때까지 미룬다. */
        private val replyGate: java.util.concurrent.CountDownLatch? = null,
        bind: InetAddress = InetAddress.getByName("127.0.0.1"),
    ) : Closeable {
        private val server = ServerSocket(0, 50, bind)
        private val pool = Executors.newCachedThreadPool { r -> Thread(r).apply { isDaemon = true } }
        val port: Int = server.localPort
        val startLines = CopyOnWriteArrayList<String>()
        val authorizations = CopyOnWriteArrayList<String?>()
        val headers = CopyOnWriteArrayList<List<Pair<String, String>>>()

        init {
            pool.execute {
                while (!server.isClosed) {
                    val s = try {
                        server.accept()
                    } catch (e: IOException) {
                        return@execute
                    }
                    pool.execute { serve(s) }
                }
            }
        }

        private fun serve(s: Socket) {
            s.use { socket ->
                val input = socket.getInputStream()
                val out = socket.getOutputStream()
                val head = try {
                    HttpHead.read(input)
                } catch (e: IOException) {
                    null
                } ?: return
                startLines += head.startLine
                authorizations += head.header("Proxy-Authorization")
                headers += head.headers
                if (head.header("Proxy-Authorization") != UpstreamProxy.basic(user, password)) {
                    out.write(
                        "HTTP/1.1 407 Proxy Authentication Required\r\nProxy-Authenticate: Basic realm=\"proxy\"\r\nContent-Length: 0\r\n\r\n"
                            .toByteArray(),
                    )
                    out.flush()
                    return
                }
                if (head.startLine.startsWith("CONNECT ") && connectStatus != 200) {
                    out.write("HTTP/1.1 $connectStatus Forbidden\r\nContent-Length: 0\r\n\r\n".toByteArray())
                    out.flush()
                    return
                }
                if (head.startLine.startsWith("CONNECT ")) {
                    replyGate?.await(5, java.util.concurrent.TimeUnit.SECONDS)
                    out.write("HTTP/1.1 200 Connection established\r\n\r\n".toByteArray())
                    out.flush()
                    val buffer = ByteArray(1024)
                    try {
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            out.flush()
                        }
                    } catch (e: IOException) {
                        // 중계가 닫았다.
                    }
                } else {
                    replyGate?.await(5, java.util.concurrent.TimeUnit.SECONDS)
                    out.write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok".toByteArray())
                    out.flush()
                }
            }
        }

        override fun close() {
            server.close()
            pool.shutdownNow()
        }
    }
}
