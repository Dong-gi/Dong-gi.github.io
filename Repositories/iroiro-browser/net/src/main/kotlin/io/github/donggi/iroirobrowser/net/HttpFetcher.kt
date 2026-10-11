package io.github.donggi.iroirobrowser.net

import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.Proxy
import java.net.URL

/**
 * 앱이 직접 내는 HTTP(내려받기·찾은 미디어 미리 읽기·광고 목록·출구 확인). 길은 **홉마다** [route] 에 묻는다 — 중계(`Proxy.Type.HTTP`),
 * `Proxy.NO_PROXY`(바로), 또는 null(길이 아직 없다 — [NoRoute]). **이 클래스는 길을 고르지 않는다.** 고르는 것은 앱의 `NetworkPath` 하나다
 * (예외 규칙에 든 사설망 주소는 바로, 나머지는 중계 — 출구 확인은 늘 중계).
 *
 * 넘겨주기(리다이렉트)를 손으로 따른다: 플랫폼은 http↔https 사이를 따르지 않고, 홉마다 쿠키가 다르다([headersFor] 를 홉마다 다시 부른다).
 * **홉마다 길도 다시 고른다** — 사설망의 페이지가 인터넷으로, 인터넷이 사설망으로 넘겨줄 수 있다(앞 홉의 길을 그대로 쓰면 인터넷 주소가 바로
 * 나가거나, 사설망 주소가 원격 프록시로 가서 닿지 못한다). 넘겨주기 응답의 `Set-Cookie` 도 [onSetCookie] 로 넘긴다(브라우저의 쿠키 저장소에
 * 넣으라고). http·https 밖의 곳으로는 가지 않는다.
 */
class HttpFetcher(
    private val route: Route,
    private val connectTimeoutMs: Int = 20_000,
    private val readTimeoutMs: Int = 30_000,
    private val maxRedirects: Int = 10,
) {

    /**
     * 홉 하나의 길 — 그 홉의 주소(자바가 읽은 그대로)를 받아 쓸 프록시를 돌려준다. null 이면 길이 아직 없다(프록시를 켰는데 중계가 서지
     * 않았다) — [get] 이 연결을 열지 않고 [NoRoute] 를 던진다. 이름을 풀지 않아야 한다(이 함수가 DNS 를 물으면 그것이 새는 길이다).
     */
    fun interface Route {
        fun proxyFor(url: URL): Proxy?
    }

    class Response internal constructor(
        /** 넘겨주기를 다 따른 뒤의 주소. */
        val url: String,
        val code: Int,
        private val connection: HttpURLConnection,
    ) : Closeable {
        val contentLength: Long get() = connection.contentLengthLong
        val contentType: String? get() = connection.contentType

        fun header(name: String): String? = connection.getHeaderField(name)

        val body: InputStream by lazy {
            if (code < 400) connection.inputStream else connection.errorStream ?: ByteArrayInputStream(ByteArray(0))
        }

        override fun close() {
            runCatching { body.close() }
            connection.disconnect()
        }
    }

    class TooManyRedirects(url: String) : IOException("too many redirects: $url")
    class UnsupportedScheme(url: String) : IOException("not http(s): $url")

    /** 이 홉의 길이 없다([Route] 가 null) — 아무 데도 붙지 않았다. */
    class NoRoute : IOException("no route (proxy not ready)")

    fun get(
        url: String,
        headersFor: (String) -> Map<String, String> = { emptyMap() },
        onSetCookie: (url: String, values: List<String>) -> Unit = { _, _ -> },
    ): Response {
        var current = url
        var hops = 0
        while (true) {
            val u = parse(current)
            // 홉마다 다시 고른다(클래스 머리말) — 앞 홉의 길을 들고 가지 않는다.
            val proxy = route.proxyFor(u) ?: throw NoRoute()
            val c = u.openConnection(proxy) as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = connectTimeoutMs
            c.readTimeout = readTimeoutMs
            c.useCaches = false
            for ((name, value) in headersFor(current)) c.setRequestProperty(name, value)
            val code = try {
                c.responseCode
            } catch (e: IOException) {
                c.disconnect()
                throw e
            }
            val cookies = c.headerFields.entries.filter { it.key?.equals("Set-Cookie", ignoreCase = true) == true }.flatMap { it.value }
            if (cookies.isNotEmpty()) onSetCookie(current, cookies)
            val location = c.getHeaderField("Location")
            if (code in REDIRECTS && location != null) {
                c.disconnect()
                if (++hops > maxRedirects) throw TooManyRedirects(url)
                current = try {
                    URL(u, location).toString()
                } catch (e: MalformedURLException) {
                    throw IOException("bad redirect: $location", e)
                }
                continue
            }
            return Response(current, code, c)
        }
    }

    private fun parse(url: String): URL {
        val u = try {
            URL(url)
        } catch (e: MalformedURLException) {
            throw IOException("bad url", e)
        }
        if (u.protocol != "http" && u.protocol != "https") throw UnsupportedScheme(url)
        return u
    }

    companion object {
        private val REDIRECTS = setOf(301, 302, 303, 307, 308)
    }
}
