package io.github.donggi.iroirobrowser.download

import io.github.donggi.iroirobrowser.core.Referrer
import io.github.donggi.iroirobrowser.net.HttpFetcher
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 내려받기의 Referer 를 **홉마다** 짓는가. 처음 주소에 맞춰 한 번 지은 값을 모든 홉(넘겨주기·HLS·DASH 의 조각·재생목록·열쇠)에 실으면,
 * 처음 주소가 쪽과 같은 출처일 때 쪽의 전체 주소(질의 포함)가 다른 출처의 서버에 간다. 루프백의 두 출처(포트가 다르다)로
 * 앱의 HTTP([HttpFetcher])가 홉마다 무엇을 싣는지 보고, DownloadCenter·MediaProbe 의 배선은 소스로 본다(CookieManager·MediaStore 에 묶여 JVM
 * 에서 돌릴 수 없다).
 */
class DownloadRefererTest {

    private val closeables = ArrayList<Closeable>()

    @AfterTest
    fun cleanUp() = closeables.forEach { runCatching { it.close() } }

    private fun <T : Closeable> keep(c: T): T = c.also { closeables += it }

    /** 홉마다 쪽에서 — DownloadCenter.headersFromPage 와 같은 셈. */
    private fun fetch(page: String?, url: String) =
        HttpFetcher({ Proxy.NO_PROXY }).get(url, headersFor = { hop -> Referrer.strictOriginWhenCrossOrigin(page, hop)?.let { mapOf("Referer" to it) } ?: emptyMap() })

    @Test
    fun 같은_출처에서_다른_출처로_넘겨주면_그_홉에는_출처만_간다() {
        val a = keep(Origin())
        val b = keep(Origin())
        a.reply["/dl"] = redirect("http://127.0.0.1:${b.port}/file")
        b.reply["/file"] = redirect("http://127.0.0.1:${a.port}/final")
        a.reply["/final"] = OK
        val page = "http://127.0.0.1:${a.port}/watch?v=1&token=secret#t"
        fetch(page, "http://127.0.0.1:${a.port}/dl").use { assertEquals(200, it.code) }
        val full = "http://127.0.0.1:${a.port}/watch?v=1&token=secret"
        assertEquals(listOf("/dl" to full, "/final" to full), a.seen.toList(), "쪽과 같은 출처의 홉은 쪽의 주소 전체")
        assertEquals(listOf("/file" to "http://127.0.0.1:${a.port}/"), b.seen.toList(), "다른 출처의 홉에는 쪽의 출처만 — 질의가 새지 않는다")
    }

    @Test
    fun 조각과_열쇠가_다른_출처인_HLS() {
        val site = keep(Origin())
        val cdn = keep(Origin())
        val keys = keep(Origin())
        for (path in listOf("/hls/index.m3u8")) site.reply[path] = OK
        for (path in listOf("/s1.ts", "/s2.ts")) cdn.reply[path] = OK
        keys.reply["/k.bin"] = OK
        val page = "http://127.0.0.1:${site.port}/video/42?session=abc"
        val parts = listOf(
            "http://127.0.0.1:${site.port}/hls/index.m3u8",
            "http://127.0.0.1:${cdn.port}/s1.ts",
            "http://127.0.0.1:${keys.port}/k.bin",
            "http://127.0.0.1:${cdn.port}/s2.ts",
        )
        for (u in parts) fetch(page, u).use { assertEquals(200, it.code) }
        assertEquals(listOf("/hls/index.m3u8" to page), site.seen.toList())
        val origin = "http://127.0.0.1:${site.port}/"
        assertEquals(listOf("/s1.ts" to origin, "/s2.ts" to origin), cdn.seen.toList())
        assertEquals(listOf("/k.bin" to origin), keys.seen.toList())
    }

    @Test
    fun 사이트가_정한_Referer_는_처음_홉에만_그대로() {
        // 갤러리 받기 — 해석기가 정한 값(사이트의 규약)은 처음 주소에만, 넘겨주기 뒤의 다른 출처에는 그 값의 출처만.
        val images = keep(Origin())
        val elsewhere = keep(Origin())
        val first = "http://127.0.0.1:${images.port}/1/a.webp"
        images.reply["/1/a.webp"] = redirect("http://127.0.0.1:${elsewhere.port}/a.webp")
        elsewhere.reply["/a.webp"] = OK
        val chosen = "http://site.test/reader/1.html"
        HttpFetcher({ Proxy.NO_PROXY }).get(first, headersFor = { hop -> Referrer.pinnedFirstHop(chosen, first, hop)?.let { mapOf("Referer" to it) } ?: emptyMap() })
            .use { assertEquals(200, it.code) }
        assertEquals(listOf("/1/a.webp" to chosen), images.seen.toList())
        assertEquals(listOf("/a.webp" to "http://site.test/"), elsewhere.seen.toList())
    }

    @Test
    fun 옛_항목은_적어_둔_Referer_를_쪽으로_본다() {
        // 쪽 주소를 적지 않던 옛 downloads.json — 그때 지어 둔 Referer(쪽 주소이거나 그 출처)를 쪽으로 보고 홉마다 다시 짓는다.
        assertEquals("https://a.test/p?q=1", DownloadCenter.refererSourceOf(null, "https://a.test/p?q=1"))
        assertEquals("https://a.test/p?q=1", DownloadCenter.refererSourceOf("", "https://a.test/p?q=1"))
        assertEquals("https://page.test/x", DownloadCenter.refererSourceOf("https://page.test/x", "https://other.test/"))
        assertNull(DownloadCenter.refererSourceOf(null, null))
        // 옛 값이 쪽의 출처뿐이었으면 같은 출처의 홉에도 출처만 — 옛 판보다 더 내보내지 않는다.
        assertEquals("https://a.test/", Referrer.strictOriginWhenCrossOrigin(DownloadCenter.refererSourceOf(null, "https://a.test/"), "https://a.test/f"))
        assertEquals("https://a.test/", Referrer.strictOriginWhenCrossOrigin(DownloadCenter.refererSourceOf(null, "https://a.test/p?q=1"), "https://cdn.test/f"))
    }

    // ── 배선(소스) ──

    private val main = File("src/main/kotlin/io/github/donggi/iroirobrowser")

    /** 소스의 글 — 줄 끝의 CR 은 뗀다(이 저장소에는 CRLF 파일이 섞여 있다). */
    private fun source(path: String): String = File(main, path).readText().replace(Char(13).toString(), "")

    /** [head] 로 시작하는 함수의 몸 — 다음 함수의 머리(네 칸 들여 쓴 `fun`) 앞까지. */
    private fun body(text: String, head: String): String {
        val start = text.indexOf(head)
        assertTrue(start >= 0, "$head 를 찾지 못했다")
        val next = Regex("(?m)^    (private |internal |override )?fun ").find(text, start + head.length)?.range?.first ?: text.length
        return text.substring(start, next)
    }

    @Test
    fun 내려받기는_홉마다_쪽에서_Referer_를_짓는다() {
        val center = source("download/DownloadCenter.kt")
        assertTrue(body(center, "private fun headersFromPage(").contains("Referrer.strictOriginWhenCrossOrigin(page, hop)"))
        assertTrue(body(center, "private fun headersPinned(").contains("Referrer.pinnedFirstHop(chosen, first, hop)"))
        // 받기를 넣을 때 지은 값을 홉에 싣는 길이 없다 — 보통 파일·조각·재생목록·열쇠·DASH 목록 모두 쪽 주소에서.
        assertFalse(center.contains("headers(item.userAgent, item.referer"), "넣을 때 지은 Referer 를 홉에 싣는다")
        assertFalse(Regex("""headers\(userAgent, referer, u""").containsMatchIn(center))
        assertTrue(body(center, "private fun fetchFileOnce(").contains("headersFromPage(item.userAgent, item.refererSource, u, extra)"))
        assertTrue(body(center, "private fun fetchPart(").contains("headersFromPage(item.userAgent, item.refererSource, u, extra)"))
        assertTrue(body(center, "private fun planFor(").contains("readPlaylist(url, item.userAgent, item.refererSource, item)"))
        assertTrue(body(center, "private fun getBody(").contains("headersFor ?: { u -> headersFromPage(userAgent, page, u) }"))
        // 열쇠 — 쪽이 보낸 요청을 찾았으면 그 값 그대로(쪽이 실제로 보낸 값), 못 찾았으면 홉마다 쪽에서.
        val key = body(center, "private fun keyHeaders(")
        assertTrue(key.contains("headersFromPage(item.userAgent, item.refererSource, url)"))
        assertTrue(key.contains("headers(item.userAgent, replay.referer, url, replay.headersFor(url))"))
        // 갤러리 — 사이트가 정한 값은 처음 홉에만.
        assertTrue(body(center, "private fun saveSetFileOnce(").contains("headersPinned(item.userAgent, file.referer, file.url, u)"))
        // `headers(` 를 곧바로 부르는 곳은 두 도우미와 열쇠의 따라 하기뿐이다.
        val direct = Regex("""(?<![A-Za-z])headers\((userAgent|item\.userAgent),""").findAll(center).count()
        assertEquals(3, direct, "headers( 를 곧바로 부르는 곳")
        // 받기를 넣는 함수는 미리 지은 Referer 를 받지 않는다(쪽 주소만).
        for (fn in listOf("fun enqueue(", "fun enqueueHls(", "fun enqueueDash(", "fun inspectHls(", "fun inspectDash(")) {
            val signature = center.substring(center.indexOf(fn)).substringBefore(")")
            assertFalse(signature.contains("referer"), "$fn 이 referer 를 받는다")
        }
    }

    @Test
    fun 찾은_미디어의_미리_읽기도_홉마다_짓는다() {
        val probe = source("browser/MediaProbe.kt")
        assertTrue(body(probe, "private fun headers(").contains("Referrer.strictOriginWhenCrossOrigin(page, url)"))
        val chooser = source("browser/MediaChooser.kt")
        assertFalse(chooser.contains("Referrer."), "고르는 창이 Referer 를 미리 짓는다")
        assertFalse(chooser.contains("request.referer"))
        assertTrue(chooser.contains("probe.playlist(url, userAgent, page)"))
        val activity = source("browser/BrowserActivity.kt")
        assertFalse(activity.contains("Referrer.strictOriginWhenCrossOrigin"), "브라우저 화면이 Referer 를 미리 짓는다")
    }

    /** 경로마다 답을 정해 두고, 받은 요청의 경로와 Referer 를 적는 루프백 서버(출처 하나 — 포트로 가른다). */
    private class Origin : Closeable {
        private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        private val pool = Executors.newCachedThreadPool { r -> Thread(r).apply { isDaemon = true } }
        val port: Int = server.localPort
        val reply = java.util.concurrent.ConcurrentHashMap<String, String>()
        val seen = CopyOnWriteArrayList<Pair<String, String?>>()

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
                val lines = readHead(socket) ?: return
                val path = lines.first().split(' ')[1]
                val referer = lines.drop(1).firstOrNull { it.startsWith("Referer:", ignoreCase = true) }?.substringAfter(':')?.trim()
                seen += path to referer
                socket.getOutputStream().write((reply[path] ?: NOT_FOUND).toByteArray(Charsets.ISO_8859_1))
                socket.getOutputStream().flush()
            }
        }

        private fun readHead(socket: Socket): List<String>? {
            val input = socket.getInputStream()
            val out = StringBuilder()
            while (!out.endsWith("\r\n\r\n")) {
                val b = input.read()
                if (b < 0) return null
                out.append(b.toChar())
            }
            return out.removeSuffix("\r\n\r\n").split("\r\n")
        }

        override fun close() {
            server.close()
            pool.shutdownNow()
        }
    }

    private companion object {
        const val OK = "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok"
        const val NOT_FOUND = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"

        fun redirect(location: String) = "HTTP/1.1 302 Found\r\nLocation: $location\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
    }
}
