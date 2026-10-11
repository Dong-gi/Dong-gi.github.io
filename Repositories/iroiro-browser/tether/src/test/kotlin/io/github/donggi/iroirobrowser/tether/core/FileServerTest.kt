package io.github.donggi.iroirobrowser.tether.core

import java.io.File
import java.io.OutputStream
import java.net.Socket
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 파일 서버를 루프백에서 — **`Host` 검사**(DNS 재바인딩 막기), 목록·경로 검사·받기(Range·한글 이름)·올리기(임시 이름으로 받고 다 받은 뒤
 * 바꾸기 — 끊겨도 원래 파일은 그대로·길이·chunked)·머리 상한(줄 수·전체 마감)·차 있으면 다음 포트로·없는 길. 폴더는 임시 폴더 위의
 * [DirectoryTree](앱은 SAF).
 */
class FileServerTest {

    private val closer = Closer()
    private val root: File = Files.createTempDirectory("tether-share").toFile()

    @AfterTest
    fun cleanUp() {
        closer.close()
        root.deleteRecursively()
    }

    private val uiBytes = "<!DOCTYPE html><title>t</title>".toByteArray()

    private fun server(tree: SharedTree = DirectoryTree(root), env: TetherEnvironment = testEnv()): FileServer {
        val s = closer.keep(FileServer(env, tree, { uiBytes }, basePort = 0))
        assertTrue(s.start() > 0)
        return s
    }

    private fun get(port: Int, target: String, host: String? = "192.168.49.1:8080", extra: String = ""): HttpReply {
        val hostLine = if (host != null) "Host: $host\r\n" else ""
        return rawHttp(port, "GET $target HTTP/1.1\r\n$hostLine$extra\r\n".toByteArray(Charsets.UTF_8))
    }

    private fun put(port: Int, path: String, body: ByteArray, headers: String? = null): HttpReply {
        val head = headers ?: "Content-Length: ${body.size}\r\n"
        return rawHttp(port, "PUT /api/file?path=$path HTTP/1.1\r\nHost: 192.168.49.1:8080\r\n$head\r\n".toByteArray(Charsets.UTF_8) + body)
    }

    private fun enc(text: String): String = java.net.URLEncoder.encode(text, "UTF-8").replace("+", "%20")

    /** 올리기의 임시 파일이 남았는가(숨은 `.이름.part…`). */
    private fun leftovers(dir: File = root): List<String> =
        dir.listFiles().orEmpty().map { it.name }.filter { it.startsWith(".") && it.contains(SharedPaths.UPLOAD_TEMP_SUFFIX) }

    @Test
    fun 화면은_GET_슬래시로_캐시하지_않게_내준다() {
        val s = server()
        val reply = get(s.actualPort, "/")
        assertEquals(200, reply.status)
        assertContentEquals(uiBytes, reply.body)
        assertEquals("text/html; charset=utf-8", reply.header("Content-Type"))
        assertEquals("no-store", reply.header("Cache-Control"))
        assertEquals("close", reply.header("Connection"))
    }

    @Test
    fun Host_가_그룹_오너_주소가_아니면_421_로_거절한다() {
        val s = server()
        assertEquals(200, get(s.actualPort, "/", host = "192.168.49.1").status)
        assertEquals(200, get(s.actualPort, "/", host = "192.168.49.1:8081").status)
        for (host in listOf("evil.example", "evil.example:8080", "192.168.49.10", "192.168.49.1.", "192.168.49.1:", "192.168.49.1:80a", "192.168.49.1:123456", "127.0.0.1", "[::ffff:192.168.49.1]")) {
            val reply = get(s.actualPort, "/api/list?path=", host = host)
            assertEquals(421, reply.status, host)
            assertTrue(reply.text.contains("Host"))
        }
        // 머리가 없는 요청(HTTP/1.0)도.
        assertEquals(421, get(s.actualPort, "/", host = null).status)
        // Host 가 둘이면 어느 것을 볼지 갈린다.
        assertEquals(400, get(s.actualPort, "/", extra = "Host: evil.example\r\n").status)
    }

    @Test
    fun Host_검사는_순수_함수로도_같다() {
        assertTrue(FileServer.isAllowedHost("192.168.49.1", "192.168.49.1"))
        assertTrue(FileServer.isAllowedHost(" 192.168.49.1:8080 ", "192.168.49.1"))
        assertFalse(FileServer.isAllowedHost(null, "192.168.49.1"))
        assertFalse(FileServer.isAllowedHost("192.168.49.12", "192.168.49.1"))
        assertFalse(FileServer.isAllowedHost("192.168.49.1:-1", "192.168.49.1"))
    }

    @Test
    fun 목록은_JSON_으로_폴더_먼저_이름_차례() {
        File(root, "b.txt").writeText("bb")
        File(root, "A.txt").writeText("a")
        File(root, "zdir").mkdir()
        val s = server()
        val reply = get(s.actualPort, "/api/list?path=")
        assertEquals(200, reply.status)
        assertEquals("application/json; charset=utf-8", reply.header("Content-Type"))
        val names = Regex("\"name\":\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(reply.text).map { it.groupValues[1] }.toList()
        assertEquals("zdir", names.first())
        assertTrue(names.indexOf("A.txt") < names.indexOf("b.txt"))
        assertTrue(reply.text.contains("\"root\":\"${root.name}\""))
    }

    @Test
    fun 하위_폴더와_한글_이름() {
        val sub = File(root, "사진").apply { mkdir() }
        File(sub, "한 장.jpg").writeBytes(ByteArray(3))
        val s = server()
        val reply = get(s.actualPort, "/api/list?path=${enc("사진")}")
        assertEquals(200, reply.status)
        assertTrue(reply.text.contains("\"name\":\"한 장.jpg\",\"dir\":false,\"size\":3"))
        assertTrue(reply.text.contains("\"path\":\"사진\""))
    }

    @Test
    fun 경로를_벗어나려는_요청과_없는_것은_404() {
        File(root, "f.txt").writeText("x")
        File(root, "d").mkdir()
        val s = server()
        for (path in listOf("..", "../etc", "/etc", "d/../f.txt", "d//x", "a%5Cb", "nope", "f.txt", "d/%00")) {
            assertEquals(404, get(s.actualPort, "/api/list?path=$path").status, path)
        }
        for (path in listOf("..%2Ff.txt", "d", "nope.txt", "/f.txt")) {
            assertEquals(404, get(s.actualPort, "/api/file?path=$path").status, path)
        }
    }

    @Test
    fun 폴더를_고르지_않았으면_503_과_needsFolder() {
        val s = server(DirectoryTree(root, available = false))
        val list = get(s.actualPort, "/api/list?path=")
        assertEquals(503, list.status)
        assertTrue(list.text.contains("\"needsFolder\":true"))
        assertEquals(503, get(s.actualPort, "/api/file?path=x").status)
        assertEquals(503, put(s.actualPort, "x", ByteArray(1)).status)
    }

    @Test
    fun 받기는_길이와_Range_와_한글_filename_별표를_준다() {
        val content = ByteArray(100) { it.toByte() }
        File(root, "한글 파일.bin").writeBytes(content)
        val s = server()
        val path = enc("한글 파일.bin")
        val full = get(s.actualPort, "/api/file?path=$path")
        assertEquals(200, full.status)
        assertContentEquals(content, full.body)
        assertEquals("100", full.header("Content-Length"))
        assertEquals("bytes", full.header("Accept-Ranges"))
        assertEquals("attachment; filename*=UTF-8''%ED%95%9C%EA%B8%80%20%ED%8C%8C%EC%9D%BC.bin", full.header("Content-Disposition"))

        val part = get(s.actualPort, "/api/file?path=$path", extra = "Range: bytes=10-19\r\n")
        assertEquals(206, part.status)
        assertEquals("bytes 10-19/100", part.header("Content-Range"))
        assertContentEquals(content.copyOfRange(10, 20), part.body)

        val tail = get(s.actualPort, "/api/file?path=$path", extra = "Range: bytes=-5\r\n")
        assertContentEquals(content.copyOfRange(95, 100), tail.body)
        val open = get(s.actualPort, "/api/file?path=$path", extra = "Range: bytes=98-\r\n")
        assertContentEquals(content.copyOfRange(98, 100), open.body)

        for (bad in listOf("bytes=100-", "bytes=0-1,5-6", "bytes=x")) {
            val reply = get(s.actualPort, "/api/file?path=$path", extra = "Range: $bad\r\n")
            assertEquals(416, reply.status, bad)
            assertEquals("bytes */100", reply.header("Content-Range"))
        }
    }

    @Test
    fun 빈_파일도_받는다() {
        File(root, "empty").writeBytes(ByteArray(0))
        val s = server()
        val reply = get(s.actualPort, "/api/file?path=empty")
        assertEquals(200, reply.status)
        assertEquals("0", reply.header("Content-Length"))
        assertEquals(0, reply.body.size)
    }

    @Test
    fun 올리기는_덮어쓰고_자르고_임시를_남기지_않는다() {
        File(root, "doc.txt").writeText("a much longer previous content")
        val s = server()
        val reply = put(s.actualPort, "doc.txt", "short".toByteArray())
        assertEquals(200, reply.status)
        assertEquals("""{"ok":true,"name":"doc.txt","bytes":5}""", reply.text)
        assertEquals("short", File(root, "doc.txt").readText())
        val sub = File(root, "d").apply { mkdir() }
        assertEquals(200, put(s.actualPort, "d/${enc("새 파일.txt")}", "새".toByteArray()).status)
        assertEquals("새", File(sub, "새 파일.txt").readText())
        assertEquals(emptyList(), leftovers() + leftovers(sub))
    }

    @Test
    fun 덮어쓰기가_끊겨도_원래_파일은_그대로이고_임시는_남지_않는다() {
        // 예전에는 본문을 받기 전에 같은 이름의 파일을 지워, 덮어쓰기가 끊기면 원래 파일까지 사라졌다.
        File(root, "keep.txt").writeText("original")
        val s = server()
        Socket(LOOPBACK, s.actualPort).use { sock ->
            sock.soTimeout = 5000
            sock.getOutputStream().write("PUT /api/file?path=keep.txt HTTP/1.1\r\nHost: 192.168.49.1\r\nContent-Length: 100\r\n\r\n12345".toByteArray())
            sock.shutdownOutput()
            val reply = parseReply(readAll(sock.getInputStream()))
            assertEquals(400, reply.status)
            assertTrue(reply.text.contains("원래 파일은 그대로"))
        }
        assertEquals("original", File(root, "keep.txt").readText())
        assertEquals(emptyList(), leftovers())
    }

    @Test
    fun 받는_동안에는_원래_파일이_그대로_있다() {
        // 본문의 앞부분만 보낸 채 멈춘다 — 그동안 목록과 받기는 원래 파일을 본다.
        File(root, "live.txt").writeText("original")
        val s = server()
        Socket(LOOPBACK, s.actualPort).use { sock ->
            sock.soTimeout = 5000
            sock.getOutputStream().write("PUT /api/file?path=live.txt HTTP/1.1\r\nHost: 192.168.49.1\r\nContent-Length: 10\r\n\r\nnew".toByteArray())
            sock.getOutputStream().flush()
            waitUntil { leftovers().isNotEmpty() }
            assertEquals("original", String(get(s.actualPort, "/api/file?path=live.txt").body))
            sock.getOutputStream().write("-conten".toByteArray())
            sock.shutdownOutput()
            assertEquals(200, parseReply(readAll(sock.getInputStream())).status)
        }
        assertEquals("new-conten", File(root, "live.txt").readText())
        assertEquals(emptyList(), leftovers())
    }

    @Test
    fun 쓰기를_열지_못하면_빈_파일을_남기지_않는다() {
        File(root, "same.txt").writeText("original")
        val broken = object : SharedTree by DirectoryTree(root) {
            override fun openWrite(file: SharedEntry): OutputStream? = null
        }
        val s = server(tree = broken)
        assertEquals(500, put(s.actualPort, "same.txt", "x".toByteArray()).status)
        assertEquals(500, put(s.actualPort, "new.txt", "x".toByteArray()).status)
        assertEquals("original", File(root, "same.txt").readText())
        assertFalse(File(root, "new.txt").exists())
        assertEquals(emptyList(), leftovers())
    }

    @Test
    fun 올리기의_길이_머리와_경로를_검사한다() {
        File(root, "dir").mkdir()
        val s = server()
        assertEquals(411, put(s.actualPort, "a.txt", "x".toByteArray(), headers = "").status)
        assertEquals(411, put(s.actualPort, "a.txt", "x".toByteArray(), headers = "Content-Length: -1\r\n").status)
        assertEquals(501, put(s.actualPort, "a.txt", "x".toByteArray(), headers = "Content-Length: 1\r\nTransfer-Encoding: chunked\r\n").status)
        // TE 만 와도 501 이다 — 예전에는 길이부터 보아 411 이었다(문서와 어긋났다).
        assertEquals(501, put(s.actualPort, "a.txt", "1\r\nx\r\n0\r\n\r\n".toByteArray(), headers = "Transfer-Encoding: chunked\r\n").status)
        assertEquals(400, put(s.actualPort, "nope/a.txt", "x".toByteArray()).status, "부모 폴더는 만들지 않는다")
        assertEquals(400, put(s.actualPort, "dir", "x".toByteArray()).status, "폴더를 파일로 덮지 않는다")
        assertEquals(400, put(s.actualPort, "../a.txt", "x".toByteArray()).status)
        assertEquals(400, put(s.actualPort, "", "x".toByteArray()).status)
        assertFalse(File(root, "a.txt").exists())
        assertTrue(File(root, "dir").isDirectory)
        assertEquals(emptyList(), leftovers())
    }

    @Test
    fun 선언보다_짧게_끊긴_새_올리기는_아무것도_남기지_않는다() {
        val s = server()
        Socket(LOOPBACK, s.actualPort).use { sock ->
            sock.soTimeout = 5000
            sock.getOutputStream().write("PUT /api/file?path=cut.bin HTTP/1.1\r\nHost: 192.168.49.1\r\nContent-Length: 100\r\n\r\n12345".toByteArray())
            sock.shutdownOutput()
            val reply = parseReply(readAll(sock.getInputStream()))
            assertEquals(400, reply.status)
        }
        assertFalse(File(root, "cut.bin").exists())
        assertEquals(emptyList(), leftovers())
    }

    @Test
    fun 머리_줄은_콜론이_없거나_이름이_겹쳐도_센다() {
        // 예전에는 담은 머리(이름마다 하나)만 세어 `:` 없는 줄이나 같은 이름이 끝없이 이어져도 걸리지 않았다.
        val s = server()
        for (line in listOf("no-colon", "X-Same: v")) {
            val many = buildString { repeat(FileServer.MAX_HEADER_COUNT + 1) { append(line).append("\r\n") } }
            assertEquals(400, get(s.actualPort, "/", extra = many).status, line)
        }
        val atLimit = buildString { repeat(FileServer.MAX_HEADER_COUNT - 1) { append("X-Same: v\r\n") } }
        assertEquals(200, get(s.actualPort, "/", extra = atLimit).status, "Host 까지 상한 안이면 받는다")
    }

    @Test
    fun 머리를_다_받기까지의_전체_마감이_지나면_답_없이_닫는다() {
        val s = server(env = testEnv(timings = TetherTimings(fileHeaderMs = 300)))
        val got = dribble(s.actualPort, "GET / HTTP/1.1\r\nHost: 192.168.49.1\r\n\r\n".toByteArray(), chunk = 3, gapMs = 80)
        assertEquals(0, got.size, "답 없이 닫는다: ${String(got)}")
    }

    @Test
    fun 기본_포트가_차_있으면_다음_빈_포트로_가고_모두_차면_실패를_알린다() {
        // 사용자 결정(2026-10-10) — 8080 이 차 있으면 8081… 로(usb-tether·처음 합친 판처럼).
        val squatter = closer.keep(squatWithFreeNext())
        val base = squatter.localPort
        val s = closer.keep(FileServer(testEnv(), DirectoryTree(root), { uiBytes }, basePort = base, attempts = 2))
        assertEquals(base + 1, s.start())
        assertEquals(base + 1, s.actualPort)
        assertNull(s.lastError)
        // 옮겨 선 포트로 온 Host(`192.168.49.1:<그 포트>`)도 받는다 — Host 검사는 포트를 가리지 않는다.
        assertEquals(200, get(base + 1, "/", host = "192.168.49.1:${base + 1}").status)
        val second = closer.keep(FileServer(testEnv(), DirectoryTree(root), { uiBytes }, basePort = base, attempts = 2))
        assertEquals(-1, second.start())
        assertEquals(StartError.PORT_TAKEN, second.lastError)
    }

    @Test
    fun 실제_값은_8080부터_열_개() {
        assertEquals(8080, FileServer.BASE_PORT)
        assertEquals(10, FileServer.PORT_ATTEMPTS)
    }

    @Test
    fun 다른_메서드와_길은_404() {
        File(root, "f").writeText("x")
        val s = server()
        for (request in listOf("OPTIONS /api/file?path=f", "HEAD /api/file?path=f", "POST /api/file?path=f", "GET /api/nope", "DELETE /api/file?path=f")) {
            assertEquals(404, rawHttp(s.actualPort, "$request HTTP/1.1\r\nHost: 192.168.49.1\r\nContent-Length: 0\r\n\r\n".toByteArray()).status, request)
        }
        assertTrue(File(root, "f").exists())
    }

    @Test
    fun 화면을_읽지_못하면_500() {
        val s = closer.keep(FileServer(testEnv(), DirectoryTree(root), { null }, basePort = 0))
        assertTrue(s.start() > 0)
        assertEquals(500, get(s.actualPort, "/").status)
    }

    @Test
    fun 실제_손님_거르개는_루프백을_거절한다() {
        val counters = Counters()
        val s = server(env = testEnv(peers = realPeerFilter(), counters = counters))
        val got = try {
            Socket(LOOPBACK, s.actualPort).use { sock ->
                sock.soTimeout = 5000
                sock.getOutputStream().write("GET / HTTP/1.1\r\nHost: 192.168.49.1\r\n\r\n".toByteArray())
                readAll(sock.getInputStream())
            }
        } catch (_: Exception) {
            ByteArray(0)
        }
        assertEquals(0, got.size, "답 없이 닫는다")
        waitUntil { counters.rejections.isNotEmpty() }
        assertEquals(PeerRejection.Reason.NOT_GROUP_OWNER_ADDRESS, counters.rejections.firstOrNull()?.reason)
    }

    @Test
    fun JSON_의_이름은_이스케이프된다() {
        val tree = object : SharedTree by DirectoryTree(root) {
            override fun list(directory: SharedEntry): List<SharedEntry> = listOf(SharedEntry("x", "q\"uote\\n\u0001.txt", false, 1, 0))
        }
        val s = server(tree = tree)
        val reply = get(s.actualPort, "/api/list?path=")
        assertTrue(reply.text.contains("\"name\":\"q\\\"uote\\\\n\\u0001.txt\""), reply.text)
        assertNull(reply.header("Content-Range"))
    }
}
