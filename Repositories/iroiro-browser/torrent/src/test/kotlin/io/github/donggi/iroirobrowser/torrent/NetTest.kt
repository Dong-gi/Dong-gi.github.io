package io.github.donggi.iroirobrowser.torrent

import io.github.donggi.iroirobrowser.torrent.net.HttpException
import io.github.donggi.iroirobrowser.torrent.net.HttpGet
import io.github.donggi.iroirobrowser.torrent.net.HttpLimits
import io.github.donggi.iroirobrowser.torrent.net.HttpUrl
import io.github.donggi.iroirobrowser.torrent.net.PublicAddresses
import io.github.donggi.iroirobrowser.torrent.net.Purpose
import io.github.donggi.iroirobrowser.torrent.net.Resolver
import io.github.donggi.iroirobrowser.torrent.net.Transport
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.zip.GZIPOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NetTest {

    @Test
    fun publicAddresses() {
        val blocked = listOf(
            "0.1.2.3", "10.0.0.1", "100.64.0.1", "100.127.255.255", "127.0.0.1", "169.254.169.254", "172.16.0.1", "172.31.255.255",
            "192.0.0.8", "192.0.2.1", "192.88.99.1", "192.168.1.1", "198.18.0.1", "198.19.255.255", "198.51.100.1", "203.0.113.9",
            "224.0.0.1", "239.255.255.250", "240.0.0.1", "255.255.255.255",
            "::", "::1", "::ffff:10.0.0.1", "::ffff:127.0.0.1", "::ffff:169.254.169.254", "::10.0.0.1", "64:ff9b::a00:1", "64:ff9b:1::1",
            "100::1", "2001:db8::1", "3fff::1", "2001:2::1", "2001:10::1", "2002:a00:1::1", "2002:c0a8:101::1", "5f00::1", "fc00::1", "fd12::1",
            "fe80::1", "fec0::1", "ff02::1",
        )
        val allowed = listOf("1.1.1.1", "8.8.8.8", "100.63.255.255", "100.128.0.1", "172.15.0.1", "172.32.0.1", "193.0.0.1", "::ffff:8.8.8.8",
            "2606:4700::1111", "2001:4860:4860::8888", "2002:808:808::1", "64:ff9b::808:808")
        for (a in blocked) assertFalse(PublicAddresses.isPublic(InetAddress.getByName(a)), a)
        for (a in allowed) assertTrue(PublicAddresses.isPublic(InetAddress.getByName(a)), a)
        val pub = InetAddress.getByName("8.8.8.8")
        assertFalse(PublicAddresses.allows(pub, 0, Purpose.TRACKER))
        assertFalse(PublicAddresses.allows(pub, 80, Purpose.PEER), "피어는 1024 밑 포트를 거른다")
        assertTrue(PublicAddresses.allows(pub, 80, Purpose.TRACKER))
        assertTrue(PublicAddresses.allows(pub, 6881, Purpose.DHT_NODE))
    }

    @Test
    fun urls() {
        val u = HttpUrl.parse("HTTP://Tracker.Example:8080/ann ounce?x=1#frag")!!
        assertEquals("tracker.example", u.host)
        assertEquals(8080, u.port)
        assertEquals("/ann%20ounce?x=1", u.target)
        assertEquals("http://[::1]:81/", HttpUrl.parse("http://[::1]:81")!!.toString())
        assertEquals("/%ED%95%9C", HttpUrl.parse("https://h/한")!!.target)
        assertEquals("xn--3e0b707e.example", HttpUrl.parse("http://한국.example/")!!.host)
        for (bad in listOf("ftp://x/", "http://", "http://user:pw@host/", "http://h:0/", "http://h:99999/", "http://h/\r\nX: y", "http://[zz]/", "javascript:alert(1)")) {
            assertNull(HttpUrl.parse(bad), bad)
        }
        val base = HttpUrl.parse("https://a.example/dir/announce?x")!!
        assertEquals("https://a.example/other", HttpUrl.resolve(base, "/other").toString())
        assertEquals("https://a.example/dir/rel", HttpUrl.resolve(base, "rel").toString())
        assertEquals("http://b.example/", HttpUrl.resolve(base, "http://b.example/").toString())
        assertEquals("https://c.example/x", HttpUrl.resolve(base, "//c.example/x").toString())
        assertNull(HttpUrl.resolve(base, "file:///etc/passwd"))
    }

    // ── 루프백 HTTP 서버 ────────────────────────────────────────────────────

    private val closeables = ArrayList<Closeable>()

    @AfterTest
    fun cleanUp() = closeables.forEach { runCatching { it.close() } }

    /** 연결마다 [handler] 를 부르는 작은 서버. 받은 요청 머리를 적는다. */
    private inner class Server(private val handler: (head: String, out: OutputStream, s: Socket) -> Unit) : Closeable {
        val ss = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val port get() = ss.localPort
        val requests = CopyOnWriteArrayList<String>()

        init {
            closeables += this
            Thread {
                while (!ss.isClosed) {
                    val s = runCatching { ss.accept() }.getOrNull() ?: break
                    Thread {
                        s.use {
                            val head = readHead(it.getInputStream())
                            requests += head
                            runCatching { handler(head, it.getOutputStream(), it) }
                        }
                    }.apply { isDaemon = true }.start()
                }
            }.apply { isDaemon = true }.start()
        }

        override fun close() = ss.close()
    }

    private fun readHead(i: InputStream): String {
        val b = ByteArrayOutputStream()
        while (!b.toString(Charsets.ISO_8859_1).endsWith("\r\n\r\n")) {
            val c = i.read()
            if (c < 0) break
            b.write(c)
        }
        return b.toString(Charsets.ISO_8859_1)
    }

    private fun client(filter: LoopbackFilter, resolver: Resolver = Resolver.SYSTEM) = HttpGet(Transport.DIRECT, resolver, filter, null, null, "Web/0.1")

    private val small = HttpLimits(maxBodyBytes = 1_048_576, connectTimeoutMs = 3_000, idleTimeoutMs = 3_000, totalTimeoutMs = 4_000)

    @Test
    fun plainChunkedAndHeaders() {
        val srv = Server { head, out, _ ->
            if ("GET /chunked" in head) {
                out.write("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n5;x=y\r\nhello\r\n6\r\n world\r\n0\r\nT: 1\r\n\r\n".toByteArray())
            } else {
                out.write("HTTP/1.1 200 OK\r\nContent-Length: 3\r\n\r\nabc".toByteArray())
            }
        }
        val http = client(LoopbackFilter(srv.port))
        val (s1, b1) = http.fetch("http://127.0.0.1:${srv.port}/x?y=1", Purpose.TRACKER, small)
        assertEquals(200, s1)
        assertEquals("abc", String(b1))
        val (_, b2) = http.fetch("http://127.0.0.1:${srv.port}/chunked", Purpose.TRACKER, small)
        assertEquals("hello world", String(b2))
        val head = srv.requests.first()
        assertTrue("Accept-Encoding: identity" in head, head)
        assertTrue("Connection: close" in head, head)
        assertTrue("User-Agent: Web/0.1" in head, head)
        assertFalse("Proxy" in head)
    }

    @Test
    fun bodyLimits() {
        val huge = Server { _, out, _ ->
            out.write("HTTP/1.1 200 OK\r\nContent-Length: 2000000\r\n\r\n".toByteArray())
            runCatching { out.write(ByteArray(2_000_000)) }
        }
        val e1 = assertFailsWith<HttpException> { client(LoopbackFilter(huge.port)).fetch("http://127.0.0.1:${huge.port}/", Purpose.TRACKER, small) }
        assertEquals(HttpException.Kind.TOO_LARGE, e1.kind)
        // 길이를 알리지 않고 흘려보내는 큰 몸
        val stream = Server { _, out, _ ->
            out.write("HTTP/1.1 200 OK\r\nConnection: close\r\n\r\n".toByteArray())
            runCatching { repeat(40) { out.write(ByteArray(64 * 1024)) } }
        }
        val e2 = assertFailsWith<HttpException> { client(LoopbackFilter(stream.port)).fetch("http://127.0.0.1:${stream.port}/", Purpose.TRACKER, small) }
        assertEquals(HttpException.Kind.TOO_LARGE, e2.kind)
    }

    /** gzip 폭탄 — 우리는 gzip 을 청하지 않는다. 서버가 그래도 주면 푼 바이트로 상한을 잰다. */
    @Test
    fun gzipBomb() {
        val bomb = ByteArrayOutputStream().also { b -> GZIPOutputStream(b).use { z -> repeat(64) { z.write(ByteArray(1024 * 1024)) } } }.toByteArray()
        assertTrue(bomb.size < 200_000)
        val srv = Server { _, out, _ ->
            out.write("HTTP/1.1 200 OK\r\nContent-Encoding: gzip\r\nContent-Length: ${bomb.size}\r\n\r\n".toByteArray())
            out.write(bomb)
        }
        val e = assertFailsWith<HttpException> { client(LoopbackFilter(srv.port)).fetch("http://127.0.0.1:${srv.port}/", Purpose.TRACKER, small) }
        assertEquals(HttpException.Kind.TOO_LARGE, e.kind)
        // 작은 gzip 은 된다
        val okBody = ByteArrayOutputStream().also { b -> GZIPOutputStream(b).use { it.write("d8:intervali60ee".toByteArray()) } }.toByteArray()
        val srv2 = Server { _, out, _ ->
            out.write("HTTP/1.1 200 OK\r\nContent-Encoding: gzip\r\nContent-Length: ${okBody.size}\r\n\r\n".toByteArray())
            out.write(okBody)
        }
        assertEquals("d8:intervali60ee", String(client(LoopbackFilter(srv2.port)).fetch("http://127.0.0.1:${srv2.port}/", Purpose.TRACKER, small).second))
        // 모르는 인코딩
        val srv3 = Server { _, out, _ -> out.write("HTTP/1.1 200 OK\r\nContent-Encoding: br\r\nContent-Length: 1\r\n\r\nx".toByteArray()) }
        assertEquals(HttpException.Kind.PROTOCOL, assertFailsWith<HttpException> { client(LoopbackFilter(srv3.port)).fetch("http://127.0.0.1:${srv3.port}/", Purpose.TRACKER, small) }.kind)
    }

    @Test
    fun redirects() {
        lateinit var loop: Server
        loop = Server { _, out, _ -> out.write("HTTP/1.1 302 Found\r\nLocation: http://127.0.0.1:${loop.port}/again\r\nContent-Length: 0\r\n\r\n".toByteArray()) }
        val filter = LoopbackFilter(loop.port)
        val e = assertFailsWith<HttpException> { client(filter).fetch("http://127.0.0.1:${loop.port}/", Purpose.TRACKER, small) }
        assertEquals(HttpException.Kind.TOO_MANY_REDIRECTS, e.kind)
        assertEquals(4, loop.requests.size, "처음 + 넘겨주기 3번")

        // 루프백의 다른 포트·클라우드 메타데이터·사설 주소로 넘기면 붙지 않는다.
        val target = Server { _, out, _ -> out.write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok".toByteArray()) }
        for (location in listOf("http://127.0.0.1:${target.port}/", "http://169.254.169.254/latest/meta-data/", "http://10.0.0.1/", "http://[::1]:${target.port}/")) {
            val r = Server { _, out, _ -> out.write("HTTP/1.1 301 Moved\r\nLocation: $location\r\nContent-Length: 0\r\n\r\n".toByteArray()) }
            val err = assertFailsWith<HttpException>(location) { client(LoopbackFilter(r.port)).fetch("http://127.0.0.1:${r.port}/", Purpose.TRACKER, small) }
            assertEquals(HttpException.Kind.BLOCKED_ADDRESS, err.kind, location)
        }
        assertEquals(0, target.requests.size)
        // http(s) 밖으로는 넘기지 않는다
        val f = Server { _, out, _ -> out.write("HTTP/1.1 302 Found\r\nLocation: file:///etc/passwd\r\nContent-Length: 0\r\n\r\n".toByteArray()) }
        assertEquals(HttpException.Kind.BAD_URL, assertFailsWith<HttpException> { client(LoopbackFilter(f.port)).fetch("http://127.0.0.1:${f.port}/", Purpose.TRACKER, small) }.kind)
    }

    /** 이름은 엔진이 풀고, 푼 주소를 거른 뒤 그 주소로 붙는다(사설로 풀리는 이름은 붙지 않는다). */
    @Test
    fun resolvedAddressesAreFiltered() {
        val srv = Server { _, out, _ -> out.write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok".toByteArray()) }
        val asked = CopyOnWriteArrayList<String>()
        val resolver = Resolver { host ->
            asked += host
            when (host) {
                "good.test" -> listOf(InetAddress.getByName("127.0.0.1"))
                "rebind.test" -> listOf(InetAddress.getByName("192.168.0.1"), InetAddress.getByName("10.1.1.1"))
                else -> throw java.net.UnknownHostException(host)
            }
        }
        val http = client(LoopbackFilter(srv.port), resolver)
        assertEquals("ok", String(http.fetch("http://good.test:${srv.port}/", Purpose.TRACKER, small).second))
        assertEquals(HttpException.Kind.BLOCKED_ADDRESS, assertFailsWith<HttpException> { http.fetch("http://rebind.test:${srv.port}/", Purpose.TRACKER, small) }.kind)
        assertEquals(HttpException.Kind.RESOLVE, assertFailsWith<HttpException> { http.fetch("http://nope.test/", Purpose.TRACKER, small) }.kind)
        assertEquals(listOf("good.test", "rebind.test", "nope.test"), asked)
    }

    /** 한 바이트씩 흘리는 서버 — 쉬는 시간 상한은 지키지만 전체 시간 상한에 걸린다. */
    @Test
    fun slowDripHitsTotalDeadline() {
        val srv = Server { _, out, _ ->
            val body = "HTTP/1.1 200 OK\r\nContent-Length: 1000\r\n\r\n" + "x".repeat(1000)
            for (c in body) {
                out.write(c.code)
                out.flush()
                Thread.sleep(50)
            }
        }
        val limits = HttpLimits(maxBodyBytes = 10_000, connectTimeoutMs = 2_000, idleTimeoutMs = 1_000, totalTimeoutMs = 1_500)
        val t0 = System.nanoTime()
        val e = assertFailsWith<HttpException> { client(LoopbackFilter(srv.port)).fetch("http://127.0.0.1:${srv.port}/", Purpose.TRACKER, limits) }
        assertEquals(HttpException.Kind.TIMEOUT, e.kind)
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 4_000)
    }

    @Test
    fun percent() {
        assertEquals("%00%FFaZ-._~%20%2F", HttpGet.Percent.encode(byteArrayOf(0, -1) + "aZ-._~ /".toByteArray()))
        assertContentEquals("%ED%95%9C".toByteArray(), HttpGet.Percent.encode("한").toByteArray())
    }

    @Test
    fun noProxyEvenWithJvmProxySettings() {
        // JVM 의 프록시 속성이 있어도 엔진의 소켓은 바로 나간다(Proxy.NO_PROXY). 닫힌 SOCKS 포트를 걸어 둔다 — 탔다면 실패한다.
        val srv = Server { _, out, _ -> out.write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok".toByteArray()) }
        val keys = listOf("socksProxyHost", "socksProxyPort", "http.proxyHost", "http.proxyPort")
        val old = keys.associateWith { System.getProperty(it) }
        try {
            System.setProperty("socksProxyHost", "127.0.0.1")
            System.setProperty("socksProxyPort", "1")
            System.setProperty("http.proxyHost", "127.0.0.1")
            System.setProperty("http.proxyPort", "1")
            assertEquals("ok", String(client(LoopbackFilter(srv.port)).fetch("http://127.0.0.1:${srv.port}/", Purpose.TRACKER, small).second))
            val s = Transport.DIRECT.connectBlocking(InetSocketAddress("127.0.0.1", srv.port), 2_000)
            s.close()
        } finally {
            for ((k, v) in old) if (v == null) System.clearProperty(k) else System.setProperty(k, v)
        }
    }
}
