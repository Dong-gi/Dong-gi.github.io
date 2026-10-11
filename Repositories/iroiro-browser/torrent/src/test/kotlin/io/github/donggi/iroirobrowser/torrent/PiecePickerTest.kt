package io.github.donggi.iroirobrowser.torrent

import io.github.donggi.iroirobrowser.torrent.bencode.Bencode
import io.github.donggi.iroirobrowser.torrent.meta.Metainfo
import io.github.donggi.iroirobrowser.torrent.piece.Bitfield
import io.github.donggi.iroirobrowser.torrent.piece.PiecePicker
import io.github.donggi.iroirobrowser.torrent.piece.Requester
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 단위 시험용 토렌트(엔진의 bencode 로 짓는다 — 통합 시험은 따로 지은 가짜를 쓴다). */
fun buildTorrent(
    files: List<Pair<List<String>, ByteArray>>,
    pieceLength: Int,
    name: String = "t",
    pad: Set<Int> = emptySet(),
    extra: Map<String, Any> = emptyMap(),
): Pair<ByteArray, ByteArray> {
    val stream = java.io.ByteArrayOutputStream()
    files.forEach { stream.write(it.second) }
    val all = stream.toByteArray()
    val count = (all.size + pieceLength - 1) / pieceLength
    val pieces = java.io.ByteArrayOutputStream()
    for (i in 0 until count) pieces.write(sha1(all.copyOfRange(i * pieceLength, minOf(all.size, (i + 1) * pieceLength))))
    val info: Map<String, Any> = if (files.size == 1 && files[0].first == listOf(name)) {
        mapOf("name" to name, "piece length" to pieceLength.toLong(), "pieces" to pieces.toByteArray(), "length" to all.size.toLong())
    } else {
        mapOf(
            "name" to name, "piece length" to pieceLength.toLong(), "pieces" to pieces.toByteArray(),
            "files" to files.mapIndexed { i, (p, d) ->
                if (i in pad) mapOf("length" to d.size.toLong(), "path" to p, "attr" to "p") else mapOf("length" to d.size.toLong(), "path" to p)
            },
        )
    }
    return Bencode.encode(extra + mapOf("info" to info)) to all
}

class PiecePickerTest {

    private class Peer(val name: String) : Requester {
        override fun toString() = name
    }

    // 조각 32 KiB(블록 둘). A 70000(조각 0..2) · B 60000(2..3) · C 40000(3..5). 조각 2 는 A·B, 조각 3 은 B·C 에 걸친다.
    private val meta = Metainfo.parse(
        buildTorrent(
            listOf(listOf("A") to ByteArray(70000), listOf("B") to ByteArray(60000), listOf("C") to ByteArray(40000)),
            pieceLength = 32768,
        ).first,
    )

    private fun picker(): PiecePicker = PiecePicker(meta, Bitfield(meta.pieceCount)).also { it.setSelection(setOf(0, 1, 2)) }

    private fun bits(vararg pieces: Int): Bitfield = Bitfield(meta.pieceCount).also { b -> pieces.forEach { b.set(it) } }

    @Test
    fun sequentialFromTheFirstPieceOfTheFirstFile() {
        val p = picker()
        p.addBitfield(bits(0, 1, 2, 3, 4, 5).also { it.setAll() }) // 시드
        val a = Peer("a")
        val got = p.pick(a, { true }, 5)
        assertEquals(listOf("0/0+16384", "0/16384+16384", "1/0+16384", "1/16384+16384", "2/0+16384"), got.map { it.toString() })
        assertEquals(0, p.activeFile)
        // 둘째 피어 — A 에 남은 것은 a 가 맡은 조각 2 의 둘째 블록뿐이라 그것을 나눠 맡고, 그러면 A 에 청하지 않은 블록이 없으니
        // 남은 자리는 엔드게임(a 가 청한 블록을 한 번 더). 다음 파일(B 의 조각 3)로 넘어가지 않는다.
        val b = Peer("b")
        val gotB = p.pick(b, { true }, 4)
        assertEquals("2/16384+16384", gotB.first().toString())
        assertEquals(4, gotB.size)
        assertTrue(gotB.drop(1).all { r -> got.any { it.piece == r.piece && it.begin == r.begin } })
        assertTrue(gotB.all { it.piece <= 2 })
    }

    @Test
    fun boundaryPieceIsTheEarlierFilesJob() {
        val p = picker()
        p.addBitfield(bits().also { it.setAll() })
        val a = Peer("a")
        // A 의 일은 조각 0·1·2(조각 2 는 B 와 함께) — A 를 다 받기 전에는 조각 3 을 청하지 않는다.
        val got = p.pick(a, { true }, 100)
        assertEquals(setOf(0, 1, 2), got.map { it.piece }.toSet())
        for (r in got) p.onBlock(a, r.piece, r.begin, r.length, ArrayList())
        for (i in 0..2) p.onPieceVerified(i)
        assertEquals(1, p.chooseActiveFile())
        assertEquals(setOf(3), p.pick(a, { true }, 100).map { it.piece }.toSet(), "B 는 조각 2 를 이미 가졌다 — 3 만")
    }

    @Test
    fun onlyFullyAvailableFilesStartAndOthersAreDeferred() {
        val p = picker()
        val a = Peer("a")
        // 조각 1 을 아무도 갖지 않는다 → A 는 다 구할 수 없다 → 미루고 B(조각 2·3)로.
        p.addBitfield(bits(0, 2, 3))
        assertEquals(1, p.chooseActiveFile())
        assertEquals(1, p.missingPieces(0))
        val got = p.pick(a, { it in setOf(0, 2, 3) }, 100)
        assertEquals(setOf(2, 3), got.map { it.piece }.toSet(), "미룬 A 의 조각 0 은 청하지 않는다")
        // 아무 파일도 다 구할 수 없으면 기다린다.
        val q = picker()
        q.addBitfield(bits(0, 4))
        assertEquals(-1, q.chooseActiveFile())
        assertTrue(q.pick(a, { true }, 10).isEmpty())
    }

    @Test
    fun stickyUntilDoneThenBackToEarliest() {
        val p = picker()
        val holder = bits(0, 2, 3)
        p.addBitfield(holder)
        assertEquals(1, p.chooseActiveFile()) // B
        // 이제 조각 1 도 구할 수 있게 됐다 — A 가 앞이지만 B 를 끝낼 때까지 그대로(파일 하나씩).
        p.addAvailability(1)
        assertEquals(1, p.chooseActiveFile())
        for (i in listOf(2, 3)) {
            p.work(i, create = true)
            p.onPieceVerified(i)
        }
        assertEquals(0, p.chooseActiveFile(), "B 가 끝나면 차례의 처음(미뤘던 A)부터")
        // 받던 파일이 구할 수 없게 되면 미룬다 — C 도 조각 4·5 를 구할 수 없으니 기다림.
        p.removeAvailability(1)
        assertEquals(-1, p.chooseActiveFile())
        p.addAvailability(4)
        p.addAvailability(5)
        assertEquals(2, p.chooseActiveFile(), "다 구할 수 있는 다음 파일(C)로")
    }

    @Test
    fun endgameOnlyAtTheEndOfTheFile() {
        val p = picker()
        p.addBitfield(bits().also { it.setAll() })
        val a = Peer("a")
        val b = Peer("b")
        // a 가 A 의 블록 다섯(조각 0·1·2 의 여섯 블록 가운데)을 맡는다 — 남은 블록이 있으니 b 는 겹치지 않는 블록을 얻는다.
        val ra = p.pick(a, { true }, 5)
        val rb = p.pick(b, { true }, 1)
        assertEquals(1, rb.size, "A 에 남은 블록은 하나 — b 는 그것을(겹치지 않는다)")
        assertTrue(rb.none { r -> ra.any { it.piece == r.piece && it.begin == r.begin } })
        // 이제 A 에 청하지 않은 블록이 없다 → 엔드게임: c 는 남이 청한 블록을 한 번 더.
        val c = Peer("c")
        val rc = p.pick(c, { true }, 3)
        assertEquals(3, rc.size)
        assertTrue(rc.all { r -> (ra + rb).any { it.piece == r.piece && it.begin == r.begin } }, "엔드게임 — 겹쳐 청한다")
        assertTrue(rc.all { it.piece <= 2 }, "다음 파일로 넘어가 청하지 않는다(파일 하나씩)")
        // 한 쪽이 받으면 다른 쪽들에 취소를 보낼 곳이 나온다
        val first = ra.first()
        val cancel = ArrayList<Requester>()
        assertEquals(PiecePicker.BlockResult.ACCEPTED, p.onBlock(a, first.piece, first.begin, first.length, cancel))
        assertTrue(c in cancel)
        assertEquals(PiecePicker.BlockResult.DUPLICATE, p.onBlock(c, first.piece, first.begin, first.length, ArrayList()))
    }

    @Test
    fun blockGeometryAndDrops() {
        val p = picker()
        p.addBitfield(bits().also { it.setAll() })
        val a = Peer("a")
        val r = p.pick(a, { true }, 2)
        assertEquals(PiecePicker.BlockResult.UNEXPECTED, p.onBlock(a, 0, 100, 16384, ArrayList()))
        assertEquals(PiecePicker.BlockResult.UNEXPECTED, p.onBlock(a, 0, 0, 999, ArrayList()))
        assertEquals(PiecePicker.BlockResult.UNEXPECTED, p.onBlock(a, 99, 0, 16384, ArrayList()))
        assertEquals(PiecePicker.BlockResult.DUPLICATE, p.onBlock(a, 4, 0, 16384, ArrayList()), "맡기지 않은 조각 — 버린다")
        assertEquals(2, p.dropRequests(a))
        val b = Peer("b")
        assertEquals(r.map { it.toString() }, p.pick(b, { true }, 2).map { it.toString() }, "거둔 블록을 다시 앞에서부터")
        // 마지막 조각은 짧다(170000 - 5*32768 = 6160)
        val last = PiecePicker(meta, Bitfield(meta.pieceCount).also { bf -> (0..4).forEach { bf.set(it) } })
        last.setSelection(setOf(2))
        last.addBitfield(bits().also { it.setAll() })
        assertEquals(listOf("5/0+6160"), last.pick(a, { true }, 5).map { it.toString() })
    }

    @Test
    fun padBlocksAreNotRequested() {
        // A 10000 + 패드 22768(조각 0 의 끝까지) + B 32768
        val m = Metainfo.parse(
            buildTorrent(
                listOf(listOf("A") to ByteArray(10000), listOf(".pad", "22768") to ByteArray(22768), listOf("B") to ByteArray(32768)),
                pieceLength = 32768,
                pad = setOf(1),
            ).first,
        )
        val p = PiecePicker(m, Bitfield(m.pieceCount))
        p.setSelection(setOf(0, 2))
        p.addBitfield(Bitfield(m.pieceCount).also { it.setAll() })
        val got = p.pick(Peer("a"), { true }, 10)
        assertEquals(listOf("0/0+16384"), got.map { it.toString() }, "조각 0 의 둘째 블록은 모두 패드 — 청하지 않는다")
    }

    /** 순차의 창 — 멀리 앞의 조각(allowed_fast 로 받은 것도)은 청하지 않는다. 앞 조각이 없는 피어는 창이 다가올 때까지 기다린다. */
    @Test
    fun sequentialWindow() {
        val big = Metainfo.parse(buildTorrent(listOf(listOf("big.bin") to ByteArray(100 * 16384)), pieceLength = 16384, name = "big.bin").first)
        val p = PiecePicker(big, Bitfield(big.pieceCount), windowOverride = 8)
        p.setSelection(setOf(0))
        p.addBitfield(Bitfield(big.pieceCount).also { it.setAll() })
        val choked = Peer("choked")
        // choke 중 — allowed_fast 가 멀리 앞(90)이면 청하지 않고, 창 안(3)이면 그것만.
        assertTrue(p.pick(choked, { true }, 4, onlyPieces = setOf(90)).isEmpty())
        assertEquals(listOf(3), p.pick(choked, { true }, 4, onlyPieces = setOf(3, 90)).map { it.piece })
        // 뒤쪽 조각만 가진 피어 — 창(0..7) 밖이라 아무것도 맡지 않는다.
        val late = Peer("late")
        assertTrue(p.pick(late, { it >= 50 }, 4).isEmpty())
        // 앞쪽을 받아 커서가 나아가면 창도 나아간다.
        val front = Peer("front")
        val got = p.pick(front, { true }, 100)
        assertEquals((0..7).toList() - 3, got.map { it.piece }.distinct(), "창 안의 조각만")
        for (i in 0..49) {
            p.work(i, create = true)
            p.onPieceVerified(i)
        }
        assertEquals(listOf(50), p.pick(late, { it >= 50 }, 1).map { it.piece })
    }

    @Test
    fun webSeedRuns() {
        val p = picker()
        p.webSeeds = 1
        assertEquals(0, p.chooseActiveFile())
        val ws = Peer("ws")
        assertEquals(0..1, p.pickRun(ws, 65536))
        assertEquals(2..2, p.pickRun(Peer("ws2"), 65536), "A 의 남은 조각")
        assertTrue(p.pickRun(Peer("ws3"), 65536).isEmpty(), "다음 파일로 넘어가지 않는다")
        val a = Peer("a")
        assertTrue(p.pick(a, { true }, 10).isEmpty() || p.pick(a, { true }, 10).all { it.piece <= 2 })
    }
}
