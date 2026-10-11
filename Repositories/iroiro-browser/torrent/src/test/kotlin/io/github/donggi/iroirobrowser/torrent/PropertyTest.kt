package io.github.donggi.iroirobrowser.torrent

import io.github.donggi.iroirobrowser.torrent.bencode.BBytes
import io.github.donggi.iroirobrowser.torrent.bencode.BDict
import io.github.donggi.iroirobrowser.torrent.bencode.BInt
import io.github.donggi.iroirobrowser.torrent.bencode.BList
import io.github.donggi.iroirobrowser.torrent.bencode.BValue
import io.github.donggi.iroirobrowser.torrent.bencode.Bencode
import io.github.donggi.iroirobrowser.torrent.meta.Metainfo
import io.github.donggi.iroirobrowser.torrent.piece.Bitfield
import io.github.donggi.iroirobrowser.torrent.piece.PiecePicker
import io.github.donggi.iroirobrowser.torrent.piece.Requester
import io.github.donggi.iroirobrowser.torrent.storage.Storage
import java.io.File
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 무작위(고정 씨앗) 속성 시험 — 배치·고르기·가진 조각을 바꿔 가며 불변식을 본다. */
class PropertyTest {

    private class Peer(val n: Int) : Requester {
        override fun toString() = "p$n"
    }

    private fun randomTorrent(rnd: Random): Pair<Metainfo, ByteArray> {
        val pieceLength = 16384 shl rnd.nextInt(3)
        val count = 1 + rnd.nextInt(7)
        val files = (0 until count).map { i ->
            val len = when (rnd.nextInt(6)) {
                0 -> 0
                1 -> 1 + rnd.nextInt(100)
                else -> rnd.nextInt(5 * pieceLength) + 1
            }
            listOf("f$i.bin") to ByteArray(len).also(rnd::nextBytes)
        }.let { if (it.all { f -> f.second.isEmpty() }) it + (listOf("last.bin") to ByteArray(1000)) else it }
        val (torrent, all) = buildTorrent(files, pieceLength)
        return Metainfo.parse(torrent) to all
    }

    /**
     * 조각 고르개: 받는 파일은 늘 '다 구할 수 있는' 파일이고, 새로 고를 때는 차례에서 가장 앞의 그런 파일이다. 청하는 블록은 받는 파일의
     * 조각뿐이고, 한 번 고를 때 같은 블록을 두 번 청하지 않는다. 끝나는 파일은 그때 받던 파일이거나 그 파일의 조각 범위 안에 통째로 든
     * 작은 파일이다(경계 조각과 함께 끝난다). 다 구할 수 있는 고른 파일은 모두 끝난다.
     */
    @Test
    fun pickerOrderInvariants() {
        val rnd = Random(20261009)
        repeat(300) { round ->
            val (m, _) = randomTorrent(rnd)
            val selectable = m.files.filter { it.isSelectable }.map { it.index }
            val selection = selectable.filter { rnd.nextInt(4) != 0 }.toSet().ifEmpty { selectable.toSet() }
            val p = PiecePicker(m, Bitfield(m.pieceCount), windowOverride = 1 + rnd.nextInt(6))
            p.setSelection(selection)
            val peers = (0 until 1 + rnd.nextInt(4)).map { Peer(it) }
            val has = peers.associateWith { Bitfield(m.pieceCount).also { b -> for (i in 0 until m.pieceCount) if (rnd.nextInt(3) != 0) b.set(i) } }
            for (b in has.values) p.addBitfield(b)
            fun available(f: Int) = m.piecesOfFile(f).all { i -> p.have[i] || has.values.any { it[i] } }
            val completedOrder = ArrayList<Int>()
            var steps = 0
            while (steps++ < 10_000) {
                val before = p.activeFile
                val active = p.chooseActiveFile()
                if (active >= 0) {
                    assertTrue(available(active), "round $round: active file $active must be fully available")
                    if (before < 0) {
                        val earliest = (0 until m.files.size).firstOrNull { it in selection && !p.isFileDone(it) && available(it) }
                        assertEquals(earliest, active, "round $round: new active = earliest available")
                    }
                } else {
                    assertTrue((0 until m.files.size).none { it in selection && !p.isFileDone(it) && available(it) }, "round $round: waiting only if nothing available")
                    break
                }
                val peer = peers[rnd.nextInt(peers.size)]
                val reqs = p.pick(peer, { has.getValue(peer)[it] }, 1 + rnd.nextInt(8))
                val range = m.piecesOfFile(active)
                for (r in reqs) {
                    assertTrue(r.piece in range, "round $round: request ${r.piece} outside active file $active $range")
                    assertTrue(has.getValue(peer)[r.piece])
                }
                assertEquals(reqs.size, reqs.map { it.piece to it.begin }.toSet().size, "no duplicate in one pick")
                // 받는다(가끔 거둔다)
                for (r in reqs) {
                    if (rnd.nextInt(10) == 0) {
                        p.dropRequest(peer, r.piece, r.begin)
                        continue
                    }
                    p.onBlock(peer, r.piece, r.begin, r.length, ArrayList())
                }
                for (i in p.inProgressPieces()) {
                    val w = p.work(i, create = false) ?: continue
                    if (w.isComplete) {
                        val activeNow = p.activeFile
                        p.onPieceVerified(i)
                        for (f in m.filesOfPiece(i)) {
                            if (f !in selection || !p.isFileDone(f) || f in completedOrder || m.files[f].length == 0L) continue
                            completedOrder += f
                            // 파일 하나씩 — 끝난 파일은 그때 받던 파일이거나, 그 파일의 조각 범위 안에 통째로 든 작은 파일(경계 조각과 함께 끝난다)이다.
                            val activeRange = if (activeNow >= 0) m.piecesOfFile(activeNow) else IntRange.EMPTY
                            val inside = m.piecesOfFile(f).all { it in activeRange }
                            assertTrue(f == activeNow || inside, "round $round: file $f completed while $activeNow was active")
                        }
                    }
                }
            }
            val shouldFinish = selection.filter { m.files[it].length > 0 && m.piecesOfFile(it).all { i -> has.values.any { b -> b[i] } } }
            assertTrue(shouldFinish.all { p.isFileDone(it) }, "round $round: available files must finish")
        }
    }

    /** 저장: 블록을 무작위 차례로 써도 조각이 그대로 읽히고 해시가 맞는다. 고르지 않은 파일은 진짜 파일을 만들지 않는다. */
    @Test
    fun storageRandomOrder() {
        val rnd = Random(7)
        repeat(40) { round ->
            val (m, all) = randomTorrent(rnd)
            val dir = tempDir("prop-storage")
            try {
                val chosen = m.files.filter { rnd.nextBoolean() }.map { it.index }.toSet()
                val s = Storage(m, File(dir, "data"), File(dir, "shadow"), chosen)
                val blocks = ArrayList<Pair<Int, Int>>()
                for (p in 0 until m.pieceCount) {
                    var b = 0
                    while (b < m.pieceSize(p)) {
                        blocks += p to b
                        b += 16384
                    }
                }
                blocks.shuffle(rnd)
                for ((p, b) in blocks) {
                    val start = (m.pieceOffset(p) + b).toInt()
                    val len = minOf(16384, m.pieceSize(p) - b)
                    s.write(p, b, all.copyOfRange(start, start + len))
                }
                for (p in 0 until m.pieceCount) {
                    val start = m.pieceOffset(p).toInt()
                    assertContentEquals(all.copyOfRange(start, start + m.pieceSize(p)), s.readPiece(p), "round $round piece $p")
                    assertTrue(s.verify(p))
                }
                s.close()
                for (f in m.files) {
                    if (f.length == 0L) continue
                    val real = File(File(dir, "data"), f.path.joinToString("/"))
                    assertEquals(f.index in chosen, real.isFile, "round $round file ${f.index}")
                }
            } finally {
                dir.deleteRecursively()
            }
        }
    }

    @Test
    fun bitfieldRoundTrip() {
        val rnd = Random(3)
        repeat(500) {
            val n = 1 + rnd.nextInt(300)
            val b = Bitfield(n)
            repeat(rnd.nextInt(n + 1)) { b.set(rnd.nextInt(n)) }
            val back = assertNotNull(Bitfield.fromBytes(n, b.toBytes()))
            assertEquals(b.count, back.count)
            for (i in 0 until n) assertEquals(b[i], back[i])
            if (n % 8 != 0) {
                val bad = b.toBytes()
                bad[bad.size - 1] = (bad[bad.size - 1].toInt() or 1).toByte()
                assertNull(Bitfield.fromBytes(n, bad), "남는 비트")
            }
            assertNull(Bitfield.fromBytes(n, ByteArray((n + 7) / 8 + 1)))
        }
    }

    @Test
    fun bencodeRoundTrip() {
        val rnd = Random(5)
        fun gen(depth: Int): Any = when (if (depth > 4) rnd.nextInt(2) else rnd.nextInt(4)) {
            0 -> rnd.nextLong() shr rnd.nextInt(63)
            1 -> ByteArray(rnd.nextInt(20)).also(rnd::nextBytes)
            2 -> List(rnd.nextInt(5)) { gen(depth + 1) }
            else -> (0 until rnd.nextInt(5)).associate { "k" + rnd.nextInt(1000) to gen(depth + 1) }
        }
        fun same(a: Any, b: BValue): Boolean = when (a) {
            is Long -> b is BInt && b.value == a
            is ByteArray -> b is BBytes && b.bytes.contentEquals(a)
            is List<*> -> b is BList && b.items.size == a.size && a.indices.all { same(a[it]!!, b.items[it]) }
            is Map<*, *> -> b is BDict && b.entries.size == a.size && a.all { (k, v) -> b.entries[k as String]?.let { same(v!!, it) } == true }
            else -> false
        }
        repeat(2000) {
            val v = gen(0)
            val bytes = Bencode.encode(v)
            val back = Bencode.decode(bytes)
            assertTrue(same(v, back))
            assertEquals(bytes.size, back.end)
            val withTail = bytes + byteArrayOf(1, 2, 3)
            assertEquals(bytes.size, Bencode.decodePrefix(withTail, 0, withTail.size, Bencode.Limits.TORRENT).end)
        }
    }
}
