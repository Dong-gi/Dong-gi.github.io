package io.github.donggi.iroirobrowser.net

import java.io.Closeable
import java.io.IOException
import java.net.Authenticator
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 앱이 직접 내는 HTTP([HttpFetcher]). 넘겨주기를 손으로 따르며 홉마다 쿠키와 **길**(바로·프록시)을 다시 고르는지, 그리고 중계를 거칠 때
 * [RelayAuthenticator] 가 **중계에게만** 계정을 주는지 — 예외 규칙으로 바로 붙은 루프백의 다른 곳에는 주지 않는지 — 본다. https 는 기기 실험이
 * 본다(JVM 의 `HttpURLConnection` 은 터널의 Basic 인증을 기본으로 막는다).
 */
class HttpFetcherTest {

    private val closeables = ArrayList<Closeable>()

    @AfterTest
    fun cleanUp() {
        Authenticator.setDefault(null)
        closeables.forEach { runCatching { it.close() } }
    }

    private fun <T : Closeable> keep(c: T): T = c.also { closeables += it }

    private fun proxyAt(port: Int) = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", port))

    @Test
    fun 넘겨주기마다_길을_다시_고른다_바로에서_프록시로() {
        // 사설망의 페이지(바로)가 인터넷 주소로 넘겨준다 — 둘째 홉은 프록시로 가야 한다. 인터넷 쪽에 바로 닿으면 실제 IP 가 샌다.
        val internet = keep(Scripted { ok("direct-leak") })
        val proxy = keep(Scripted { head -> if (head.startLine == "GET http://127.0.0.1:${internet.port}/final HTTP/1.1") ok("via-proxy") else BAD_GATEWAY })
        val lan = keep(Scripted { redirectTo("http://127.0.0.1:${internet.port}/final") })
        val asked = CopyOnWriteArrayList<String>()
        val route = HttpFetcher.Route { u ->
            asked += u.toString()
            if (u.port == lan.port) Proxy.NO_PROXY else proxyAt(proxy.port)
        }
        HttpFetcher(route).get("http://127.0.0.1:${lan.port}/start").use { r ->
            assertEquals(200, r.code)
            assertEquals("via-proxy", r.body.readBytes().decodeToString())
        }
        assertEquals(listOf("GET /start HTTP/1.1"), lan.startLines.toList()) // 첫 홉은 바로(주소의 경로만)
        assertEquals(listOf("GET http://127.0.0.1:${internet.port}/final HTTP/1.1"), proxy.startLines.toList()) // 둘째 홉은 프록시로(절대 주소)
        assertEquals(emptyList(), internet.startLines.toList())
        assertEquals(listOf("http://127.0.0.1:${lan.port}/start", "http://127.0.0.1:${internet.port}/final"), asked.toList())
    }

    @Test
    fun 넘겨주기마다_길을_다시_고른다_프록시에서_바로로() {
        // 인터넷 주소(프록시)가 사설망 주소로 넘겨준다 — 둘째 홉은 바로 가야 한다(원격 프록시는 우리 사설망에 닿지 못한다).
        val lan = keep(Scripted { ok("lan") })
        val internet = keep(Scripted { ok("direct-leak") })
        val proxy = keep(
            Scripted { head ->
                if (head.startLine == "GET http://127.0.0.1:${internet.port}/start HTTP/1.1") redirectTo("http://127.0.0.1:${lan.port}/final") else BAD_GATEWAY
            },
        )
        val route = HttpFetcher.Route { u -> if (u.port == lan.port) Proxy.NO_PROXY else proxyAt(proxy.port) }
        HttpFetcher(route).get("http://127.0.0.1:${internet.port}/start").use { r ->
            assertEquals(200, r.code)
            assertEquals("lan", r.body.readBytes().decodeToString())
            assertEquals("http://127.0.0.1:${lan.port}/final", r.url)
        }
        assertEquals(listOf("GET http://127.0.0.1:${internet.port}/start HTTP/1.1"), proxy.startLines.toList())
        assertEquals(listOf("GET /final HTTP/1.1"), lan.startLines.toList())
        assertEquals(emptyList(), internet.startLines.toList())
    }

    @Test
    fun 길이_없으면_연결을_열지_않는다() {
        val origin = keep(FakeOrigin())
        assertFailsWith<HttpFetcher.NoRoute> { HttpFetcher({ null }).get("http://127.0.0.1:${origin.port}/final") }
        assertEquals(emptyList(), origin.startLines.toList())
        // 둘째 홉에서 길이 없으면 첫 홉만 가고 둘째는 열지 않는다(옛 길을 들고 가지 않는다).
        val internet = keep(Scripted { ok("direct-leak") })
        val lan = keep(Scripted { redirectTo("http://127.0.0.1:${internet.port}/final") })
        assertFailsWith<HttpFetcher.NoRoute> {
            HttpFetcher({ u -> if (u.port == lan.port) Proxy.NO_PROXY else null }).get("http://127.0.0.1:${lan.port}/start")
        }
        assertEquals(listOf("GET /start HTTP/1.1"), lan.startLines.toList())
        assertEquals(emptyList(), internet.startLines.toList())
    }

    @Test
    fun 바로_붙은_루프백의_401_407_에_중계_계정을_주지_않는다() {
        // 루프백은 늘 예외(크로미움의 암묵 규칙)라 바로 붙는다. 인증기는 '프록시·중계의 포트·루프백' 만 보므로, 중계와 **같은 포트의 다른 루프백
        // 주소**가 가장 위험한 자리다 — 바로 붙은 연결의 407 에 플랫폼이 프록시 인증을 물으면 거기서 계정이 나간다.
        val relay = keep(LocalRelay(null, null, null)).start()
        Authenticator.setDefault(RelayAuthenticator { relay })
        val places = ArrayList<Scripted>()
        places += keep(Scripted(reply = challenge(relay.realm)))
        // 127.0.0.2 는 이 PC(윈도)에서 따로 설정 없이 선다(토렌트 시험도 쓴다). 이 자리가 시험의 이빨이다 — 인증기가 사이트의 401 에도 답하게
        // 되돌려 보면 여기서만 계정이 실린다(되돌려 확인했다). 그래서 건너뛰지 않는다.
        places += keep(Scripted("127.0.0.2", relay.port, challenge(relay.realm)))
        for (place in places) {
            for (path in listOf("/407", "/401")) {
                val code = try {
                    HttpFetcher(DIRECT).get("http://${place.host}:${place.port}$path").use { it.code }
                } catch (e: IOException) {
                    -1 // 안드로이드의 OkHttp 는 프록시 없는 연결의 407 을 ProtocolException 으로 끝낸다 — 그것도 '주지 않음' 이다.
                }
                assertTrue(code == -1 || code == path.drop(1).toInt(), "${place.host}:${place.port}$path → $code")
            }
            assertEquals(2, place.startLines.size, "${place.host}: ${place.startLines}")
            assertTrue(place.credentials.all { it == null }, "계정이 실렸다(${place.host}): ${place.credentials}")
        }
        // 중계 자신의 포트로 바로 붙어도 계정이 실리지 않는다 — 실렸으면 중계는 407 이 아니라 502(프록시를 정하지 않음)로 답한다.
        val own = try {
            HttpFetcher(DIRECT).get("http://127.0.0.1:${relay.port}/x").use { it.code }
        } catch (e: IOException) {
            -1
        }
        assertTrue(own == 407 || own == -1, "중계의 답: $own")
    }

    @Test
    fun 넘겨주기를_따르며_홉마다_쿠키를_다시_고른다() {
        val origin = keep(FakeOrigin())
        val jar = HashMap<String, String>()
        val fetcher = HttpFetcher(DIRECT)
        fetcher.get(
            "http://127.0.0.1:${origin.port}/r1",
            headersFor = { url -> mapOf("User-Agent" to "test-agent") + (jar[url.substringBefore("/r").substringBefore("/final")]?.let { mapOf("Cookie" to it) } ?: emptyMap()) },
            onSetCookie = { url, values -> jar[url.substringBefore("/r")] = values.joinToString("; ") { it.substringBefore(';') } },
        ).use { r ->
            assertEquals(200, r.code)
            assertEquals("http://127.0.0.1:${origin.port}/final", r.url)
            assertEquals("done", r.body.readBytes().decodeToString())
        }
        assertEquals(listOf("GET /r1 HTTP/1.1", "GET /r2 HTTP/1.1", "GET /final HTTP/1.1"), origin.startLines.toList())
        // r1 이 준 쿠키가 r2·final 에 실렸다.
        assertEquals(listOf(null, "a=1", "a=1"), origin.cookies.toList())
        assertTrue(origin.agents.all { it == "test-agent" })
    }

    @Test
    fun 넘겨주기가_끝없으면_멈춘다() {
        val origin = keep(FakeOrigin())
        assertFailsWith<HttpFetcher.TooManyRedirects> { HttpFetcher(DIRECT, maxRedirects = 3).get("http://127.0.0.1:${origin.port}/loop") }
    }

    @Test
    fun http_밖으로는_따르지_않는다() {
        val origin = keep(FakeOrigin())
        assertFailsWith<HttpFetcher.UnsupportedScheme> { HttpFetcher(DIRECT).get("http://127.0.0.1:${origin.port}/ftp") }
        assertFailsWith<HttpFetcher.UnsupportedScheme> { HttpFetcher(DIRECT).get("file:///etc/hosts") }
    }

    @Test
    fun 오류_응답의_몸도_읽는다() {
        val origin = keep(FakeOrigin())
        HttpFetcher(DIRECT).get("http://127.0.0.1:${origin.port}/missing").use { r ->
            assertEquals(404, r.code)
            assertEquals("nope", r.body.readBytes().decodeToString())
        }
    }

    @Test
    fun 중계를_거치면_인증기가_중계에게만_계정을_준다() {
        val origin = keep(FakeOrigin())
        // 원격 프록시 대신 평문의 가짜 원격: 계정이 맞으면 절대 주소 요청을 그 주소로 넘긴다.
        val upstream = keep(ForwardingUpstream())
        val relay = keep(LocalRelay(UpstreamProxy(false, "127.0.0.1", upstream.port, "up-user", "up-pass"), null, null)).start()
        Authenticator.setDefault(RelayAuthenticator { relay })
        val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", relay.port))
        HttpFetcher({ proxy }).get("http://127.0.0.1:${origin.port}/final").use { r ->
            assertEquals(200, r.code)
            assertEquals("done", r.body.readBytes().decodeToString())
        }
        assertEquals(listOf("GET http://127.0.0.1:${origin.port}/final HTTP/1.1"), upstream.startLines.toList())
        // 원격에는 원격 계정이, 목적지에는 아무 계정도 가지 않았다.
        assertEquals(listOf(UpstreamProxy.basic("up-user", "up-pass")), upstream.authorizations.toList())
        assertEquals(listOf<String?>(null), origin.proxyAuthorizations.toList())
    }

    @Test
    fun 인증기는_다른_포트나_사이트에는_답하지_않는다() {
        val relay = keep(LocalRelay(null, null, null)).start()
        val auth = RelayAuthenticator { relay }
        val loopback = InetAddress.getByName("127.0.0.1")
        val ask = { host: String, site: InetAddress?, port: Int, type: Authenticator.RequestorType ->
            Authenticator.setDefault(auth)
            Authenticator.requestPasswordAuthentication(host, site, port, "http", "realm", "Basic", null, type)
        }
        assertEquals(relay.sessionUser, ask("localhost", loopback, relay.port, Authenticator.RequestorType.PROXY)?.userName)
        assertEquals(null, ask("localhost", loopback, relay.port + 1, Authenticator.RequestorType.PROXY))
        assertEquals(null, ask("localhost", loopback, relay.port, Authenticator.RequestorType.SERVER))
        assertEquals(null, ask("evil.test", InetAddress.getByName("10.0.0.1"), relay.port, Authenticator.RequestorType.PROXY))
        assertEquals(null, ask("localhost", null, relay.port, Authenticator.RequestorType.PROXY))
    }

    /** 받은 머리를 적고 [reply] 가 돌려준 답(머리와 몸 — 닫는 답)을 쓴 뒤 닫는 서버. 계정 머리(`Authorization`·`Proxy-Authorization`)도 적는다. */
    private class Scripted(val host: String = "127.0.0.1", port: Int = 0, private val reply: (HttpHead) -> String) : Closeable {
        private val server = ServerSocket(port, 50, InetAddress.getByName(host))
        private val pool = Executors.newCachedThreadPool { r -> Thread(r).apply { isDaemon = true } }
        val port: Int = server.localPort
        val startLines = CopyOnWriteArrayList<String>()
        val credentials = CopyOnWriteArrayList<String?>()

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
                val head = try {
                    HttpHead.read(socket.getInputStream())
                } catch (e: IOException) {
                    null
                } ?: return
                startLines += head.startLine
                credentials += head.header("Authorization") ?: head.header("Proxy-Authorization")
                socket.getOutputStream().write(reply(head).toByteArray())
                socket.getOutputStream().flush()
            }
        }

        override fun close() {
            server.close()
            pool.shutdownNow()
        }
    }

    private companion object {
        val DIRECT = HttpFetcher.Route { Proxy.NO_PROXY }

        fun ok(body: String) = "HTTP/1.1 200 OK\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body"

        const val BAD_GATEWAY = "HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"

        fun redirectTo(location: String) = "HTTP/1.1 302 Found\r\nLocation: $location\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"

        /** `/407` 은 프록시 인증을, `/401` 은 사이트 인증을 묻는다 — 중계와 같은 realm 으로(흉내 내도 계정을 받지 못해야 한다). */
        fun challenge(realm: String): (HttpHead) -> String = { head ->
            when (head.startLine.split(' ')[1]) {
                "/407" -> "HTTP/1.1 407 Proxy Authentication Required\r\nProxy-Authenticate: Basic realm=\"$realm\"\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                "/401" -> "HTTP/1.1 401 Unauthorized\r\nWWW-Authenticate: Basic realm=\"$realm\"\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                else -> BAD_GATEWAY
            }
        }
    }

    /** 목적지 서버. 받은 것을 적어 둔다. */
    private class FakeOrigin : Closeable {
        private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        private val pool = Executors.newCachedThreadPool { r -> Thread(r).apply { isDaemon = true } }
        val port: Int = server.localPort
        val startLines = CopyOnWriteArrayList<String>()
        val cookies = CopyOnWriteArrayList<String?>()
        val agents = CopyOnWriteArrayList<String?>()
        val proxyAuthorizations = CopyOnWriteArrayList<String?>()

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
                val head = try {
                    HttpHead.read(socket.getInputStream())
                } catch (e: IOException) {
                    null
                } ?: return
                startLines += head.startLine
                cookies += head.header("Cookie")
                agents += head.header("User-Agent")
                proxyAuthorizations += head.header("Proxy-Authorization")
                val path = head.startLine.split(' ')[1].substringAfter("127.0.0.1:$port")
                val reply = when (path) {
                    "/r1" -> "HTTP/1.1 302 Found\r\nLocation: /r2\r\nSet-Cookie: a=1; Path=/\r\nContent-Length: 0\r\n\r\n"
                    "/r2" -> "HTTP/1.1 301 Moved\r\nLocation: http://127.0.0.1:$port/final\r\nContent-Length: 0\r\n\r\n"
                    "/final" -> "HTTP/1.1 200 OK\r\nContent-Length: 4\r\nConnection: close\r\n\r\ndone"
                    "/loop" -> "HTTP/1.1 302 Found\r\nLocation: /loop\r\nContent-Length: 0\r\n\r\n"
                    "/ftp" -> "HTTP/1.1 302 Found\r\nLocation: ftp://127.0.0.1/x\r\nContent-Length: 0\r\n\r\n"
                    else -> "HTTP/1.1 404 Not Found\r\nContent-Length: 4\r\nConnection: close\r\n\r\nnope"
                }
                socket.getOutputStream().write(reply.toByteArray())
                socket.getOutputStream().flush()
            }
        }

        override fun close() {
            server.close()
            pool.shutdownNow()
        }
    }

    /** 가짜 원격 프록시(평문). 계정이 맞으면 절대 주소 요청을 그 목적지로 그대로 보내고 답을 돌려준다. */
    private class ForwardingUpstream : Closeable {
        private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        private val pool = Executors.newCachedThreadPool { r -> Thread(r).apply { isDaemon = true } }
        val port: Int = server.localPort
        val startLines = CopyOnWriteArrayList<String>()
        val authorizations = CopyOnWriteArrayList<String?>()

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
                val head = try {
                    HttpHead.read(socket.getInputStream())
                } catch (e: IOException) {
                    null
                } ?: return
                startLines += head.startLine
                authorizations += head.header("Proxy-Authorization")
                if (head.header("Proxy-Authorization") != UpstreamProxy.basic("up-user", "up-pass")) {
                    socket.getOutputStream().write("HTTP/1.1 407 Proxy Authentication Required\r\nContent-Length: 0\r\n\r\n".toByteArray())
                    return
                }
                val target = java.net.URL(head.startLine.split(' ')[1])
                Socket(target.host, target.port).use { origin ->
                    val request = StringBuilder("GET ${target.file} HTTP/1.1\r\n")
                    head.headers.filterNot { it.first.equals("Proxy-Authorization", true) }.forEach { request.append("${it.first}: ${it.second}\r\n") }
                    request.append("\r\n")
                    origin.getOutputStream().write(request.toString().toByteArray())
                    origin.getOutputStream().flush()
                    origin.getInputStream().copyTo(socket.getOutputStream())
                }
            }
        }

        override fun close() {
            server.close()
            pool.shutdownNow()
        }
    }
}
