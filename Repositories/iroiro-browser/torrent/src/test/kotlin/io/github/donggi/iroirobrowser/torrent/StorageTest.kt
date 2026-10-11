package io.github.donggi.iroirobrowser.torrent

import io.github.donggi.iroirobrowser.torrent.meta.Metainfo
import io.github.donggi.iroirobrowser.torrent.piece.Bitfield
import io.github.donggi.iroirobrowser.torrent.storage.AtomicFiles
import io.github.donggi.iroirobrowser.torrent.storage.ResumeData
import io.github.donggi.iroirobrowser.torrent.storage.Storage
import java.io.File
import java.util.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StorageTest {

    private val dirs = ArrayList<File>()

    @AfterTest
    fun cleanUp() = dirs.forEach { it.deleteRecursively() }

    private fun dir() = tempDir("torrent-storage").also { dirs += it }

    private val rnd = Random(3)
    private val a = ByteArray(20000).also(rnd::nextBytes)
    private val padLen = 32768 - 20000
    private val b = ByteArray(40000).also(rnd::nextBytes)
    private val c = ByteArray(10000).also(rnd::nextBytes)
    private val built = buildTorrent(
        listOf(listOf("A") to a, listOf(".pad", "$padLen") to ByteArray(padLen), listOf("sub", "B") to b, listOf("C") to c),
        pieceLength = 32768,
        pad = setOf(1),
    )
    private val meta = Metainfo.parse(built.first)
    private val all = built.second

    private fun writePiece(s: Storage, p: Int) {
        val start = meta.pieceOffset(p).toInt()
        val size = meta.pieceSize(p)
        var off = 0
        while (off < size) {
            val n = minOf(16384, size - off)
            s.write(p, off, all.copyOfRange(start + off, start + off + n))
            off += n
        }
    }

    @Test
    fun writesAcrossFilesAndSkipsPads() {
        val root = dir()
        val s = Storage(meta, File(root, "data"), File(root, "shadow"), setOf(0, 2, 3))
        for (p in 0 until meta.pieceCount) writePiece(s, p)
        for (p in 0 until meta.pieceCount) assertTrue(s.verify(p), "piece $p")
        s.close()
        assertContentEquals(a, File(root, "data/t/A").readBytes())
        assertContentEquals(b, File(root, "data/t/sub/B").readBytes())
        assertContentEquals(c, File(root, "data/t/C").readBytes())
        assertFalse(File(root, "data/t/pad").exists(), "패드는 쓰지 않는다")
        assertFalse(File(root, "shadow").exists())
    }

    @Test
    fun unselectedFilesGoToShadowAndMaterializeLater() {
        val root = dir()
        // C 만 고른다 — B 와 C 에 걸친 조각 2 의 B 부분은 그림자로.
        val s = Storage(meta, File(root, "data"), File(root, "shadow"), setOf(3))
        val p2 = meta.filesOfPiece(2)
        assertTrue(2 in p2 && 3 in p2)
        writePiece(s, 2)
        assertTrue(s.verify(2))
        assertFalse(File(root, "data/t/sub/B").exists(), "고르지 않은 파일은 만들지 않는다")
        assertTrue(File(root, "shadow/2").exists())
        // 이제 B 를 고른다 — 확인된 조각 2 의 B 부분을 진짜 파일로 옮긴다.
        val have = Bitfield(meta.pieceCount).also { it.set(2) }
        s.materialize(2, have)
        assertFalse(File(root, "shadow/2").exists())
        assertTrue(s.verify(2))
        writePiece(s, 1)
        s.close()
        val bFile = File(root, "data/t/sub/B").readBytes()
        assertContentEquals(b, bFile.copyOf(b.size))
    }

    @Test
    fun releasedFilesAreNotTouched() {
        val root = dir()
        val s = Storage(meta, File(root, "data"), File(root, "shadow"), setOf(0, 2, 3))
        for (p in 0 until meta.pieceCount) writePiece(s, p)
        s.release(2)
        // 앱이 옮겼다(지웠다)
        assertTrue(File(root, "data/t/sub/B").delete())
        assertFalse(s.verify(1), "옮긴 부분은 읽지 않는다 — 다시 받아야 하면 그림자로")
        writePiece(s, 1)
        assertTrue(s.verify(1))
        assertFalse(File(root, "data/t/sub/B").exists(), "옮긴 파일을 다시 만들지 않는다")
        s.close()
    }

    @Test
    fun deleteAll() {
        val root = dir()
        val s = Storage(meta, File(root, "data/abc"), File(root, "shadow"), setOf(0, 2, 3))
        for (p in 0 until meta.pieceCount) writePiece(s, p)
        File(root, "data/abc/t/keep.txt").writeText("not ours")
        s.deleteAll()
        assertFalse(File(root, "data/abc/t/A").exists())
        assertTrue(File(root, "data/abc/t/keep.txt").exists(), "우리 것이 아닌 파일은 지우지 않는다")
    }

    @Test
    fun resumeRoundTrip() {
        val r = ResumeData(ByteArray(20) { 7 }, "이름", null, byteArrayOf(-128, 0), longArrayOf(5, -1), setOf(0, 3), setOf(0, 3), setOf(1), setOf(0), "paused", null, 123, 456)
        val back = ResumeData.decode(r.encode())!!
        assertContentEquals(r.infoHash, back.infoHash)
        assertEquals("이름", back.name)
        assertContentEquals(r.pieces, back.pieces)
        assertContentEquals(r.fileSizes, back.fileSizes)
        assertEquals(setOf(0, 3), back.selected)
        assertEquals(setOf(1), back.released)
        assertEquals(456, back.downloaded)
        assertNull(ResumeData.decode("garbage".toByteArray()))
        assertNull(ResumeData.decode(io.github.donggi.iroirobrowser.torrent.bencode.Bencode.encode(mapOf("v" to 99L))))
    }

    @Test
    fun atomicWriteReplaces() {
        val f = File(dir(), "state/x.resume")
        AtomicFiles.write(f, "one".toByteArray())
        AtomicFiles.write(f, "two".toByteArray())
        assertEquals("two", f.readText())
        assertFalse(File(f.parentFile, "x.resume.tmp").exists())
    }
}
