package io.github.donggi.iroirobrowser.tether.core

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.Socket
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipInputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 파일 서버의 읽기 쪽 새 API — 답의 머리(CSP·nosniff·attachment), 화면 자산, 정보(저장 공간·스위치), 목록(휴지통 감춤·폴더만), zip 을 흘려
 * 받기(표·상한·동시 수·흘림), 섬네일(형식 보기·캐시·상한)과 JSON 읽개.
 */
class FileServerReadTest {

    private val kits = ArrayList<FileServerKit>()
    private val temps = ArrayList<File>()

    private fun kit(
        manage: Boolean = false,
        tree: ((File) -> SharedTree)? = null,
        thumbnails: ThumbnailCache? = null,
        maxTreeEntries: Int = SharedOps.MAX_TREE_ENTRIES,
    ): FileServerKit {
        val root = Files.createTempDirectory("tether-read").toFile()
        val base = DirectoryTree(root)
        return FileServerKit(root, manage, base, tree?.invoke(root) ?: base, thumbnails, maxTreeEntries).also { kits += it }
    }

    @AfterTest
    fun cleanUp() {
        kits.forEach { it.close() }
        temps.forEach { it.deleteRecursively() }
    }

    // ── 답의 머리 ────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun 화면은_이_서버의_것만_받는_CSP_와_함께_세_파일로() {
        val k = kit()
        for ((path, type) in listOf("/" to "text/html", "/fileman.js" to "text/javascript", "/fileman.css" to "text/css")) {
            val reply = k.get(path)
            assertEquals(200, reply.status, path)
            assertTrue(reply.header("Content-Type")!!.startsWith(type), path)
            assertEquals(FileServer.UI_CSP, reply.header("Content-Security-Policy"))
            assertEquals("nosniff", reply.header("X-Content-Type-Options"))
            assertEquals("DENY", reply.header("X-Frame-Options"))
            assertEquals("same-origin", reply.header("Cross-Origin-Resource-Policy"))
        }
        assertEquals("ui:fileman.js", k.get("/fileman.js").text)
        // 인라인 스크립트·스타일을 받지 않는다.
        assertFalse(FileServer.UI_CSP.contains("unsafe"))
        assertEquals(404, k.get("/other.js").status)
        assertEquals(404, k.get("/fileman.html").status)
    }

    @Test
    fun 받기는_늘_attachment_이고_sandbox_이며_PDF_만_브라우저로_연다() {
        val k = kit()
        k.file("x.html", "<script>alert(1)</script>")
        k.file("x.svg", "<svg onload=alert(1)>")
        k.file("doc.pdf", "%PDF-1.4")
        for (name in listOf("x.html", "x.svg")) {
            for (inline in listOf("", "&inline=1")) {
                val reply = k.get("/api/file?path=$name$inline")
                assertEquals(200, reply.status)
                assertTrue(reply.header("Content-Disposition")!!.startsWith("attachment;"), "$name$inline")
                assertEquals(FileServer.API_CSP, reply.header("Content-Security-Policy"))
                assertEquals("nosniff", reply.header("X-Content-Type-Options"))
            }
        }
        assertTrue(k.get("/api/file?path=doc.pdf").header("Content-Disposition")!!.startsWith("attachment;"))
        val pdf = k.get("/api/file?path=doc.pdf&inline=1")
        assertTrue(pdf.header("Content-Disposition")!!.startsWith("inline;"))
        assertEquals("application/pdf", pdf.header("Content-Type"))
        assertEquals(FileServer.PDF_CSP, pdf.header("Content-Security-Policy"))
        // JSON 과 오류에도 nosniff·sandbox.
        val list = k.get("/api/list?path=")
        assertEquals("nosniff", list.header("X-Content-Type-Options"))
        assertEquals(FileServer.API_CSP, list.header("Content-Security-Policy"))
        assertEquals(FileServer.API_CSP, k.get("/api/nope").header("Content-Security-Policy"))
    }

    // ── 정보·목록 ────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun 정보는_폴더_이름과_스위치와_저장_공간() {
        val k = kit(manage = true)
        val info = k.get("/api/info").json()
        assertEquals(true, info["available"])
        assertEquals(k.root.name, info["root"])
        assertEquals(true, info["manage"])
        assertEquals(5_000_000_000, info["free"])
        assertEquals(64_000_000_000, info["total"])
        k.tree.available = false
        val missing = k.get("/api/info").json()
        assertEquals(false, missing["available"])
        assertNull(missing["free"])
    }

    @Test
    fun 목록은_휴지통을_감추고_폴더만도_준다() {
        val k = kit()
        k.dir(SharedPaths.TRASH_NAME)
        k.dir("sub/inner")
        k.file("sub/f.txt")
        k.file("a.txt")
        val root = k.get("/api/list?path=").text
        assertFalse(root.contains(SharedPaths.TRASH_NAME))
        assertTrue(root.contains("\"a.txt\""))
        val dirs = k.get("/api/list?path=sub&dirs=1").text
        assertTrue(dirs.contains("\"inner\""))
        assertFalse(dirs.contains("f.txt"))
        // 하위 폴더의 같은 이름은 감추지 않는다(맨 위의 것만 휴지통이다).
        k.dir("sub/${SharedPaths.TRASH_NAME}")
        assertTrue(k.get("/api/list?path=sub").text.contains(SharedPaths.TRASH_NAME))
    }

    // ── zip ──────────────────────────────────────────────────────────────────────────────────────────────

    private fun zipEntries(bytes: ByteArray): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                out[entry.name] = String(zip.readBytes(), Charsets.UTF_8)
            }
        }
        return out
    }

    @Test
    fun 여러_개와_폴더를_zip_으로_받는다() {
        val k = kit()
        k.file("사진/한 장.jpg", "jpg-bytes")
        k.file("사진/sub/메모.txt", "memo")
        k.dir("사진/빈 폴더")
        k.file("other.txt", "o")
        val plan = k.post("/api/zip", """{"base":"","names":["사진","other.txt"]}""")
        assertEquals(200, plan.status, plan.text)
        val ticket = plan.json()
        assertEquals(3L, ticket["count"])
        assertEquals("${k.root.name}.zip", ticket["name"])
        val reply = k.get("/api/zip?id=${ticket["id"]}")
        assertEquals(200, reply.status)
        assertEquals("application/zip", reply.header("Content-Type"))
        assertNull(reply.header("Content-Length"), "길이를 모르고 흘린다")
        assertTrue(reply.header("Content-Disposition")!!.startsWith("attachment; filename*=UTF-8''"))
        val entries = zipEntries(reply.body)
        assertEquals("jpg-bytes", entries["사진/한 장.jpg"])
        assertEquals("memo", entries["사진/sub/메모.txt"])
        assertTrue("사진/빈 폴더/" in entries.keys)
        assertEquals("o", entries["other.txt"])
        // 표는 한 번만 쓴다.
        assertEquals(404, k.get("/api/zip?id=${ticket["id"]}").status)
        assertEquals(404, k.get("/api/zip?id=nope").status)
        // 하나면 그 이름.
        assertEquals("other.txt.zip", k.post("/api/zip", """{"base":"","names":["other.txt"]}""").json()["name"])
    }

    @Test
    fun zip_의_항목_수_상한은_받기_전에_알린다() {
        val k = kit(maxTreeEntries = 4)
        for (i in 1..4) k.file("d/f$i.txt")
        val reply = k.post("/api/zip", """{"base":"","names":["d"]}""")
        assertEquals(413, reply.status)
        assertTrue(reply.text.contains("너무 많습니다"))
        assertEquals(404, k.post("/api/zip", """{"base":"","names":["none"]}""").status)
        assertEquals(415, k.post("/api/zip", """{"base":"","names":["d"]}""", type = "text/plain").status, "단순 POST 는 거절")
    }

    @Test
    fun zip_표가_다_찼으면_펴_보기_전에_거절한다() {
        // 폴더를 펴 보는 것(목록 질의)을 센다 — 표가 찬 동안 몰아 보낸 요청마다 SAF 질의를 쏟지 않게.
        val listed = AtomicInteger()
        val k = kit(tree = { root ->
            val base = DirectoryTree(root)
            object : SharedTree by base {
                override fun list(directory: SharedEntry): List<SharedEntry> {
                    if (directory.name == "big") listed.incrementAndGet()
                    return base.list(directory)
                }
            }
        })
        k.file("a.txt")
        for (i in 1..20) k.file("big/f$i.txt")
        repeat(FileServer.MAX_ZIP_TICKETS) { assertEquals(200, k.post("/api/zip", """{"base":"","names":["a.txt"]}""").status) }
        val reply = k.post("/api/zip", """{"base":"","names":["big"]}""")
        assertEquals(503, reply.status)
        assertTrue(reply.text.contains("기다리는 것이 많습니다"))
        assertEquals(0, listed.get(), "표가 찼으면 폴더를 펴 보지 않는다")
    }

    /** 읽기가 [gate] 가 열릴 때까지 막히는 폴더 — zip 이 흘려 보내는지·동시 수 상한을 본다. */
    private class GatedTree(private val base: SharedTree, val gate: CountDownLatch, val blocked: String) : SharedTree by base {
        override fun openRead(file: SharedEntry): InputStream? {
            val source = base.openRead(file) ?: return null
            if (file.name != blocked) return source
            return object : InputStream() {
                override fun read(): Int {
                    gate.await(10, TimeUnit.SECONDS)
                    return source.read()
                }

                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    gate.await(10, TimeUnit.SECONDS)
                    return source.read(b, off, len)
                }

                override fun close() = source.close()
            }
        }
    }

    @Test
    fun zip_은_흘려_보내고_동시에_둘까지() {
        val gate = CountDownLatch(1)
        val k = kit(tree = { GatedTree(DirectoryTree(it), gate, "slow.bin") })
        k.file("a.txt", "first file")
        k.file("slow.bin", "x".repeat(100_000))
        val sockets = (1..2).map {
            val id = k.post("/api/zip", """{"base":"","names":["a.txt","slow.bin"]}""").json()["id"]
            Socket(LOOPBACK, k.port).apply {
                soTimeout = 5000
                getOutputStream().write("GET /api/zip?id=$id HTTP/1.1\r\nHost: 192.168.49.1\r\n\r\n".toByteArray())
            }
        }
        try {
            // 둘째 파일에서 막혀 있는데도 첫 파일의 머리(PK 03 04 와 이름)와 둘째 파일의 머리가 이미 왔다 — 메모리에 다 모은 뒤 보내지 않는다.
            for (s in sockets) {
                val seen = StringBuilder()
                val buf = ByteArray(4096)
                val input = s.getInputStream()
                while (!seen.contains("a.txt")) {
                    val n = input.read(buf)
                    assertTrue(n > 0, "막히기 전에 첫 파일이 와야 한다")
                    seen.append(String(buf, 0, n, Charsets.ISO_8859_1))
                }
                assertTrue(seen.contains("PK\u0003\u0004"))
            }
            // 둘이 흐르는 동안 셋째는 표부터 거절한다.
            val third = k.post("/api/zip", """{"base":"","names":["a.txt"]}""")
            assertEquals(503, third.status)
            assertTrue(third.text.contains("동시에"))
        } finally {
            gate.countDown()
            sockets.forEach { s -> s.use { readAll(it.getInputStream()) } }
        }
        waitUntil { k.post("/api/zip", """{"base":"","names":["a.txt"]}""").status == 200 }
    }

    // ── 섬네일 ───────────────────────────────────────────────────────────────────────────────────────────

    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(32)

    @Test
    fun 섬네일은_머리로_형식을_보고_캐시한다() {
        val cacheDir = Files.createTempDirectory("tether-thumbs").toFile().also { temps += it }
        val k = kit(thumbnails = ThumbnailCache(cacheDir))
        File(k.root, "p.png").writeBytes(png)
        // 이름은 그림이지만 내용은 HTML — 내보내지 않는다.
        k.file("fake.png", "<html><script>alert(1)</script></html>")
        k.file("t.txt", "text")
        val first = k.get("/api/thumb?path=p.png&size=160")
        assertEquals(200, first.status)
        assertEquals("image/png", first.header("Content-Type"))
        assertContentEquals(png, first.body)
        assertEquals(FileServer.API_CSP, first.header("Content-Security-Policy"))
        assertTrue(first.header("Cache-Control")!!.contains("max-age"))
        assertEquals(200, k.get("/api/thumb?path=p.png&size=160").status)
        assertEquals(1, k.tree.thumbnailCalls.get(), "둘째는 캐시에서")
        assertEquals(200, k.get("/api/thumb?path=p.png&size=999").status)
        assertEquals(2, k.tree.thumbnailCalls.get(), "크기가 다르면 따로")
        assertEquals(2, cacheDir.list()!!.size)
        // 캐시의 이름은 열쇠의 해시뿐 — 손님의 글이 경로에 들지 않는다.
        assertTrue(cacheDir.list()!!.all { it.matches(Regex("[0-9a-f]{64}")) })
        assertEquals(404, k.get("/api/thumb?path=fake.png").status)
        assertEquals(404, k.get("/api/thumb?path=t.txt").status)
        assertEquals(404, k.get("/api/thumb?path=none.png").status)
        assertEquals(404, k.get("/api/thumb?path=../p.png").status)
    }

    @Test
    fun 섬네일_캐시는_개수_상한에서_오래된_것부터_지운다() {
        val dir = Files.createTempDirectory("tether-thumbs").toFile().also { temps += it }
        val cache = ThumbnailCache(dir, maxFiles = 3)
        for (i in 1..5) {
            cache.put("k$i", png)
            File(dir, "k$i").setLastModified(1_000_000L * i)
        }
        cache.trim()
        assertEquals(setOf("k3", "k4", "k5"), dir.list()!!.toSet())
        cache.put("big", ByteArray(Thumbnails.MAX_BYTES + 1))
        assertNull(cache.get("big"))
    }

    @Test
    fun 섬네일_형식_보기() {
        assertEquals("image/jpeg", Thumbnails.sniff(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0)))
        assertEquals("image/gif", Thumbnails.sniff("GIF89a".toByteArray()))
        assertEquals("image/webp", Thumbnails.sniff("RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".toByteArray()))
        assertNull(Thumbnails.sniff("<svg>".toByteArray()))
        assertNull(Thumbnails.sniff(ByteArray(0)))
        assertEquals(Thumbnails.SMALL, Thumbnails.clampSize(null))
        assertEquals(Thumbnails.LARGE, Thumbnails.clampSize(5000))
    }

    // ── JSON 읽개 ────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun JSON_읽개는_엄격하다() {
        assertEquals(
            mapOf("a" to listOf(1L, -2.5, true, false, null), "b" to "x\"\\/\n\u00e9\ud83d\ude00"),
            JsonReader.parse(""" { "a" : [1, -2.5, true, false, null], "b": "x\"\\\/\n\u00e9\ud83d\ude00" } """),
        )
        for (bad in listOf("", "{", "{\"a\":1,}", "[1,]", "01", "1 2", "{\"a\":1,\"a\":2}", "\"\\ud800\"", "\"a\u0001\"", "tru", "\"\\x\"", "-", "1.", "1e")) {
            assertFailsWith<JsonReader.Malformed>(bad) { JsonReader.parse(bad) }
        }
        val deep = "[".repeat(JsonReader.MAX_DEPTH + 1) + "]".repeat(JsonReader.MAX_DEPTH + 1)
        assertFailsWith<JsonReader.Malformed> { JsonReader.parse(deep) }
        // 쓰개와 읽개가 서로 맞는다(이름의 제어 문자·따옴표·줄 구분자).
        val tricky = "q\"u\\o\u0001te\u2028.txt"
        assertEquals(tricky, JsonReader.parse(Json.quote(tricky)))
    }
}
