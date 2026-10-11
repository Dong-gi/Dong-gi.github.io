package io.github.donggi.iroirobrowser.torrent

import io.github.donggi.iroirobrowser.torrent.bencode.Bencode
import io.github.donggi.iroirobrowser.torrent.meta.Metainfo
import io.github.donggi.iroirobrowser.torrent.meta.TorrentPaths
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class MetainfoTest {

    /** 파이썬으로 따로 지은 토렌트 — info-hash·조각 해시·파일·트래커·웹 시드를 견준다. 조각 해시는 내용 규칙으로 다시 지어 본다. */
    @Test
    fun pythonVectors() {
        for (c in PythonVectors.torrents) {
            val bytes = Base64.getDecoder().decode(c.torrentBase64)
            when (c.expect) {
                "v2-only" -> {
                    val e = assertFailsWith<TorrentException>(c.id) { Metainfo.parse(bytes) }
                    assertEquals(TorrentError.V2_ONLY, e.error, c.id)
                    continue
                }
                "private" -> {
                    val e = assertFailsWith<TorrentException>(c.id) { Metainfo.parse(bytes) }
                    assertEquals(TorrentError.PRIVATE_TORRENT, e.error, c.id)
                    continue
                }
            }
            val m = try {
                Metainfo.parse(bytes)
            } catch (e: TorrentException) {
                fail("${c.id}: $e")
            }
            assertEquals(c.infoHashV1Hex, m.infoHashHex, c.id)
            assertEquals(c.expect == "hybrid", m.isHybrid, c.id)
            assertEquals(c.pieceLength, m.pieceLength, c.id)
            assertEquals(c.totalLength, m.totalLength, c.id)
            assertEquals(c.files.size, m.files.size, c.id)
            assertEquals(c.trackers, m.trackers, c.id)
            assertEquals(c.webSeeds, m.webSeeds, c.id)
            val hashes = unhex(c.pieceHashesHex)
            assertEquals(hashes.size / 20, m.pieceCount, c.id)
            for (i in 0 until m.pieceCount) assertTrue(m.pieceHashEquals(i, hashes.copyOfRange(i * 20, i * 20 + 20)), "${c.id} piece $i")
            // 내용을 다시 지어 조각 해시까지
            val stream = java.io.ByteArrayOutputStream()
            for ((i, f) in c.files.withIndex()) {
                assertEquals(f.length, m.files[i].length, "${c.id} file $i")
                assertEquals(f.pad, m.files[i].isPad, "${c.id} pad $i")
                stream.write(if (f.pad) ByteArray(f.length.toInt()) else vectorPayload(f.seed, f.length))
                // 경로 — 패드가 아니면 정리한 경로가 원래와 같아야 한다(이 벡터들은 깨끗한 이름이다)
                if (!f.pad) assertEquals(if (m.isMultiFile) listOf(m.name) + f.path else f.path, m.files[i].path, "${c.id} path $i")
            }
            val all = stream.toByteArray()
            for (i in 0 until m.pieceCount) {
                val start = m.pieceOffset(i).toInt()
                assertTrue(m.pieceHashEquals(i, sha1(all.copyOfRange(start, start + m.pieceSize(i)))), "${c.id} content piece $i")
            }
        }
    }

    @Test
    fun infoHashUsesOriginalBytes() {
        // 키 차례가 틀린 info — 다시 지으면 해시가 달라진다. 원래 바이트로 낸다.
        val info = "d4:name1:a6:lengthi3e12:piece lengthi16384e6:pieces20:${"x".repeat(20)}e"
        val torrent = "d4:info${info}e".toByteArray(Charsets.ISO_8859_1)
        val m = Metainfo.parse(torrent)
        assertEquals(hex(sha1(info.toByteArray(Charsets.ISO_8859_1))), m.infoHashHex)
    }

    private fun torrentOf(info: Map<String, Any>, extra: Map<String, Any> = emptyMap()): ByteArray = Bencode.encode(extra + mapOf("info" to info))

    private fun multi(files: List<Map<String, Any>>, pieceLength: Long = 16384, name: String = "t"): ByteArray {
        val total = files.sumOf { it["length"] as Long }
        val count = ((total - 1) / pieceLength + 1).toInt()
        return torrentOf(mapOf("name" to name, "piece length" to pieceLength, "pieces" to ByteArray(20 * count), "files" to files))
    }

    @Test
    fun pathsAreSanitized() {
        val m = Metainfo.parse(
            multi(
                listOf(
                    mapOf("length" to 1L, "path" to listOf("..", "..", "etc", "passwd")),
                    mapOf("length" to 1L, "path" to listOf("a", ".", "", "b.txt")),
                    mapOf("length" to 1L, "path" to listOf("con.txt")),
                    mapOf("length" to 1L, "path" to listOf("evil\u202Etxt.exe")),
                    mapOf("length" to 1L, "path" to listOf("x:y*z?.bin")),
                    mapOf("length" to 1L, "path" to listOf("/abs", "C:\\win")),
                ),
                name = "../top",
            ),
        )
        val paths = m.files.map { it.path.joinToString("/") }
        assertEquals("_top", m.name) // 슬래시는 _ 로, 앞의 점은 뗀다
        assertEquals("_top/etc/passwd", paths[0])
        assertEquals("_top/a/b.txt", paths[1])
        assertEquals("_top/con_.txt", paths[2])
        assertEquals("_top/evil_txt.exe", paths[3])
        assertEquals("_top/x_y_z_.bin", paths[4])
        assertEquals("_top/_abs/C__win", paths[5])
        for (p in paths) assertFalse(p.split('/').any { it == ".." || it == "." || it.isEmpty() }, p)
    }

    @Test
    fun collisionsAreRenamed() {
        val m = Metainfo.parse(
            multi(
                listOf(
                    mapOf("length" to 1L, "path" to listOf("A.txt")),
                    mapOf("length" to 1L, "path" to listOf("a.txt")),
                    mapOf("length" to 1L, "path" to listOf("e\u0301.txt")), // 분해형
                    mapOf("length" to 1L, "path" to listOf("\u00e9.txt")), // 조합형 — NFC 로 같다
                    mapOf("length" to 1L, "path" to listOf("dir")),
                    mapOf("length" to 1L, "path" to listOf("dir", "inner.bin")),
                ),
            ),
        )
        val paths = m.files.map { it.path.joinToString("/") }
        assertEquals(listOf("t/A.txt", "t/a (1).txt", "t/\u00e9.txt", "t/\u00e9 (1).txt", "t/dir", "t/dir (1)/inner.bin"), paths)
        assertEquals(paths.size, paths.map { it.lowercase() }.toSet().size)
    }

    @Test
    fun padFilesAndAttributes() {
        val m = Metainfo.parse(
            multi(
                listOf(
                    mapOf("length" to 10L, "path" to listOf("a.bin")),
                    mapOf("length" to 16374L, "path" to listOf(".pad", "16374"), "attr" to "p"),
                    mapOf("length" to 0L, "path" to listOf("link"), "attr" to "l"),
                    mapOf("length" to 5L, "path" to listOf("_____padding_file_0_")),
                    mapOf("length" to 5L, "path" to listOf("b.bin"), "attr" to "x"),
                ),
            ),
        )
        assertTrue(m.files[1].isPad)
        assertTrue(m.files[2].isSymlink)
        assertTrue(m.files[3].isPadLike)
        assertEquals(setOf(0, 4), m.defaultSelection())
        assertEquals(0..0, m.piecesOfFile(0))
        assertEquals(1..1, m.piecesOfFile(4))
        assertEquals(IntRange.EMPTY, m.piecesOfFile(2))
    }

    @Test
    fun limitsAndErrors() {
        // 파일 수 상한
        val tooMany = List(Metainfo.MAX_FILES + 1) { mapOf("length" to 1L, "path" to listOf("f$it")) }
        assertEquals(TorrentError.TOO_LARGE, assertFailsWith<TorrentException> { Metainfo.parse(multi(tooMany)) }.error)
        // 길이 합 넘침
        val overflow = listOf(mapOf("length" to Long.MAX_VALUE, "path" to listOf("a")), mapOf("length" to 10L, "path" to listOf("b")))
        val e = assertFailsWith<TorrentException> {
            Metainfo.parse(torrentOf(mapOf("name" to "t", "piece length" to 16384L, "pieces" to ByteArray(20), "files" to overflow)))
        }
        assertEquals(TorrentError.INVALID_TORRENT, e.error)
        // 조각 해시 길이가 조각 수와 맞지 않는다
        val bad = torrentOf(mapOf("name" to "t", "piece length" to 16384L, "pieces" to ByteArray(40), "length" to 10L))
        assertEquals(TorrentError.INVALID_TORRENT, assertFailsWith<TorrentException> { Metainfo.parse(bad) }.error)
        // 조각 크기
        val zero = torrentOf(mapOf("name" to "t", "piece length" to 0L, "pieces" to ByteArray(20), "length" to 10L))
        assertEquals(TorrentError.INVALID_TORRENT, assertFailsWith<TorrentException> { Metainfo.parse(zero) }.error)
        // 경로 깊이
        val deep = listOf(mapOf("length" to 1L, "path" to List(TorrentPaths.MAX_DEPTH + 1) { "d" }))
        assertEquals(TorrentError.TOO_LARGE, assertFailsWith<TorrentException> { Metainfo.parse(multi(deep)) }.error)
        // private 의 다른 꼴
        val priv = torrentOf(mapOf("name" to "t", "piece length" to 16384L, "pieces" to ByteArray(20), "length" to 10L, "private" to "1"))
        assertEquals(TorrentError.PRIVATE_TORRENT, assertFailsWith<TorrentException> { Metainfo.parse(priv) }.error)
        // 깨진 bencode
        assertEquals(TorrentError.INVALID_TORRENT, assertFailsWith<TorrentException> { Metainfo.parse("d4:info".toByteArray()) }.error)
    }

    @Test
    fun trackersAndSeeds() {
        val t = torrentOf(
            mapOf("name" to "t", "piece length" to 16384L, "pieces" to ByteArray(20), "length" to 10L),
            mapOf(
                "announce" to "http://a/announce",
                "announce-list" to listOf(listOf("udp://b:1/x", "ftp://no", "http://a/announce"), listOf<String>(), listOf("https://c/announce")),
                "url-list" to listOf("https://ws/", "file:///etc/passwd", ""),
                "httpseeds" to listOf("http://hs/seed"),
            ),
        )
        val m = Metainfo.parse(t)
        assertEquals(listOf(listOf("udp://b:1/x", "http://a/announce"), listOf("https://c/announce")), m.trackers)
        assertEquals(listOf("https://ws/"), m.webSeeds)
        assertEquals(listOf("http://hs/seed"), m.httpSeeds)
    }

    @Test
    fun pieceFileMapping() {
        val m = Metainfo.parse(
            multi(
                listOf(
                    mapOf("length" to 20000L, "path" to listOf("a")),
                    mapOf("length" to 0L, "path" to listOf("empty")),
                    mapOf("length" to 30000L, "path" to listOf("b")),
                ),
            ),
        )
        assertEquals(4, m.pieceCount)
        assertEquals(0..1, m.piecesOfFile(0))
        assertEquals(1..3, m.piecesOfFile(2))
        assertEquals(0..2, m.filesOfPiece(1)) // 길이 0 인 파일이 가운데 끼어 있다
        assertEquals(2, m.fileAt(20000))
        assertEquals(0, m.fileAt(19999))
        assertEquals(50000 - 3 * 16384, m.pieceSize(3))
    }
}
