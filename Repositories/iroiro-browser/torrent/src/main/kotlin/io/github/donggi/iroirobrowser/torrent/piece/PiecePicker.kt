package io.github.donggi.iroirobrowser.torrent.piece

import io.github.donggi.iroirobrowser.torrent.meta.Metainfo
import io.github.donggi.iroirobrowser.torrent.wire.Wire

/** 블록을 청하는 쪽(피어 연결·웹 시드) — 같은 객체인지로만 가른다. */
interface Requester

class BlockRequest(val piece: Int, val begin: Int, val length: Int) {
    override fun toString(): String = "$piece/$begin+$length"
}

/**
 * **받는 순서(사용자 지정): '100% 다운로드 가능한 파일들에 한하여, 파일 하나씩, 조각 처음부터 끝까지 순차적으로'.**
 *
 * 해석(CLAUDE.md 의 '토렌트 엔진' 절에 같은 글):
 * - 고른 파일들을 **토렌트의 차례대로** 하나씩 받는다. 한 번에 '받는 파일'([activeFile])은 하나다.
 * - 한 파일을 시작하려면 그 파일에 걸친 조각 가운데 아직 없는 것이 **모두 지금 군집에서 구할 수 있어야** 한다 — 연결된 피어의 bitfield·
 *   HAVE·Have All 이나 일하는 웹 시드(모든 조각을 가진다)가 덮을 때. 그런 파일만 시작한다.
 * - 받는 파일의 조각을 **첫 조각부터 끝 조각까지 차례로** 청한다 — 피어마다 앞쪽의 아직 아무도 맡지 않은 조각을 통째로 맡고(조각마다 한
 *   피어 — 해시가 틀리면 누구 탓인지 안다), 블록도 앞에서부터. 남은 블록이 모두 청해졌을 때만(=**파일 끝 무렵**) 엔드게임 — 다른 피어가
 *   맡은 블록을 한 번 더 청한다.
 * - 받는 파일이 군집에서 다 구할 수 없게 되면(피어가 떠나) 그 파일을 **미루고** 다음의 '다 구할 수 있는' 파일로 간다. 받는 파일이 끝나면
 *   다시 처음 차례부터 본다(미룬 파일을 구할 수 있게 됐으면 그것부터). 아무것도 시작할 수 없으면 기다린다([activeFile] = -1).
 * - 파일 경계에 걸친 조각은 앞 파일을 받을 때 받는다(앞 파일의 마지막 조각이다). 앞 파일을 미뤘으면 뒤 파일이 제 첫 조각으로 받는다.
 *
 * 이벤트 루프 스레드에서만 쓴다.
 */
class PiecePicker(
    private val meta: Metainfo,
    val have: Bitfield,
    /** 순차의 창을 조각 수로 정한다(시험용). 없으면 [WINDOW_BYTES] 에서. */
    windowOverride: Int? = null,
) {

    private val pieceCount = meta.pieceCount
    private val fileCount = meta.files.size

    /** 연결된 피어 가운데 그 조각을 가진 수(모든 조각을 가진 피어는 [seeds] 로 따로 센다). */
    private val availability = IntArray(pieceCount)

    /** 모든 조각을 가진 연결 피어(Have All 이나 가득 찬 bitfield). */
    var seeds = 0
        private set

    /** 일하는 웹 시드 수 — 웹 시드는 모든 조각을 가진다. */
    var webSeeds = 0
        set(value) {
            field = maxOf(0, value)
        }

    private var selected = BooleanArray(fileCount)

    /** 파일마다: 없는 조각 가운데 연결 피어 누구도 갖지 않은 조각 수. 0 이면 그 파일은 피어만으로 다 구할 수 있다. */
    private val unavailable = IntArray(fileCount)

    /** 받는 중인 조각. */
    private val inProgress = HashMap<Int, PieceWork>()

    /** 지금 받는 파일. -1 이면 없다(기다림이거나 다 받음). */
    var activeFile = -1
        private set

    /** 받는 파일에서 아직 청할 블록이 있을 수 있는 첫 조각. */
    private var cursor = 0

    /** 순차의 창(조각 수). */
    val windowPieces: Int = windowOverride ?: maxOf(MIN_WINDOW_PIECES.toLong(), WINDOW_BYTES / meta.pieceLength).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    init {
        for (f in 0 until fileCount) {
            var n = 0
            for (p in meta.piecesOfFile(f)) if (!have[p]) n++
            unavailable[f] = n
        }
    }

    class PieceWork(val index: Int, val size: Int) {
        val blockCount = (size + Wire.BLOCK - 1) / Wire.BLOCK
        val received = BooleanArray(blockCount)
        var receivedCount = 0
            internal set
        internal val requesters = arrayOfNulls<ArrayList<Requester>>(blockCount)
        val contributors = LinkedHashSet<Requester>()

        fun blockLength(b: Int): Int = if (b == blockCount - 1) size - b * Wire.BLOCK else Wire.BLOCK
        val isComplete: Boolean get() = receivedCount == blockCount

        internal fun requestedBy(b: Int): List<Requester> = requesters[b] ?: emptyList()

        internal fun isUnrequested(b: Int): Boolean = !received[b] && requesters[b].isNullOrEmpty()

        internal fun hasUnrequested(): Boolean = (0 until blockCount).any { isUnrequested(it) }

        internal fun ownedByOther(r: Requester): Boolean =
            requesters.any { list -> list != null && list.any { it !== r } } || (receivedCount > 0 && r !in contributors && contributors.isNotEmpty())
    }

    // ── 고르기 ──────────────────────────────────────────────────────────────

    fun setSelection(files: Set<Int>) {
        selected = BooleanArray(fileCount) { it in files && meta.files[it].isSelectable }
        if (activeFile >= 0 && !selected[activeFile]) activeFile = -1
    }

    fun isSelected(file: Int): Boolean = selected[file]

    fun isFileDone(file: Int): Boolean {
        for (p in meta.piecesOfFile(file)) if (!have[p]) return false
        return true
    }

    /** 고른 파일이 모두 끝났다. */
    fun allSelectedDone(): Boolean = (0 until fileCount).none { selected[it] && !isFileDone(it) }

    /** 그 파일의 없는 조각이 지금 모두 구할 수 있는가. */
    fun isFullyAvailable(file: Int): Boolean = webSeeds > 0 || seeds > 0 || unavailable[file] == 0

    /** 그 파일에 걸친 조각 가운데 지금 구할 수 없는 수(화면의 '기다림' 까닭). */
    fun missingPieces(file: Int): Int = if (webSeeds > 0 || seeds > 0) 0 else unavailable[file]

    /**
     * 받는 파일을 정한다 — 지금 것이 아직 구할 수 있으면 그대로, 아니면 차례대로 첫 '다 구할 수 있는' 고른 파일. 없으면 -1.
     */
    fun chooseActiveFile(): Int {
        val cur = activeFile
        if (cur >= 0 && selected[cur] && !isFileDone(cur) && isFullyAvailable(cur)) return cur
        activeFile = -1
        for (f in 0 until fileCount) {
            if (!selected[f] || isFileDone(f)) continue
            if (isFullyAvailable(f)) {
                activeFile = f
                cursor = meta.piecesOfFile(f).first
                break
            }
        }
        return activeFile
    }

    /** 받는 파일의 아직 없는 조각들(차례대로). */
    private fun workPieces(): IntRange = if (activeFile < 0) IntRange.EMPTY else meta.piecesOfFile(activeFile)

    /**
     * [r] 에게 청할 블록을 [max] 개까지. [peerHas] 는 그 피어가 가진 조각인가. [onlyPieces] 가 있으면(choke 중의 allowed_fast) 그 조각만.
     *
     * 청하는 조각은 **커서(아직 청할 것이 남은 첫 조각) 앞의 창**([windowPieces]) 안에서만 고른다 — 순차라 멀리 앞의 조각을 미리 받지
     * 않는다. allowed_fast(BEP 6 — 상대가 우리 IP 로 고른 무작위 조각)도 창 안에 있을 때만 쓴다(실제 군집에서 choke 중에 조각 ~1112 를
     * 먼저 받아 순차를 어긴 것을 보고 묶었다).
     */
    fun pick(r: Requester, peerHas: (Int) -> Boolean, max: Int, onlyPieces: Set<Int>? = null): List<BlockRequest> {
        if (max <= 0) return emptyList()
        chooseActiveFile()
        val range = workPieces()
        if (range.isEmpty()) return emptyList()
        advanceCursor(range)
        val windowEnd = minOf(range.last, maxOf(cursor, range.first) + windowPieces - 1)
        val out = ArrayList<BlockRequest>()
        fun usable(p: Int) = p <= windowEnd && !have[p] && peerHas(p) && (onlyPieces == null || p in onlyPieces)

        // 1) 이 피어가 이미 맡은 조각을 먼저 잇는다.
        for (w in inProgress.values.sortedBy { it.index }) {
            if (out.size >= max) break
            if (w.index !in range || !usable(w.index)) continue
            if (w.requesters.any { list -> list != null && list.any { it === r } } || r in w.contributors) {
                takeBlocks(w, r, max, out)
            }
        }
        // 2) 앞에서부터 아무도 맡지 않은 조각을 통째로 맡는다(창 안에서).
        var p = maxOf(cursor, range.first)
        while (out.size < max && p <= windowEnd) {
            if (usable(p)) {
                val w = inProgress[p]
                if (w == null) {
                    takeBlocks(work(p), r, max, out)
                } else if (!w.ownedByOther(r) && w.hasUnrequested()) {
                    takeBlocks(w, r, max, out)
                }
            }
            p++
        }
        // 3) 남은 것이 남이 맡은 조각뿐이면 그 조각의 청하지 않은 블록을 나눠 맡는다.
        if (out.size < max) {
            for (w in inProgress.values.sortedBy { it.index }) {
                if (out.size >= max) break
                if (w.index !in range || !usable(w.index) || !w.hasUnrequested()) continue
                takeBlocks(w, r, max, out)
            }
        }
        // 4) 엔드게임 — 받는 파일에 청하지 않은 블록이 하나도 없을 때(파일 끝 무렵)만, 남이 청한 블록을 한 번 더.
        if (out.size < max && isEndgame(range)) {
            for (w in inProgress.values.sortedBy { it.index }) {
                if (out.size >= max) break
                if (w.index !in range || !usable(w.index)) continue
                for (b in 0 until w.blockCount) {
                    if (out.size >= max) break
                    if (w.received[b]) continue
                    val list = w.requesters[b] ?: continue
                    if (list.any { it === r } || list.size >= MAX_ENDGAME_REQUESTERS) continue
                    list.add(r)
                    out.add(BlockRequest(w.index, b * Wire.BLOCK, w.blockLength(b)))
                }
            }
        }
        return out
    }

    /**
     * 웹 시드에게 줄 이어진 조각들 — 앞에서부터 아무도 맡지 않은 조각을 [maxBytes] 까지(적어도 한 조각). 그 블록을 모두 [r] 이 청한 것으로.
     */
    fun pickRun(r: Requester, maxBytes: Long): IntRange {
        chooseActiveFile()
        val range = workPieces()
        if (range.isEmpty()) return IntRange.EMPTY
        advanceCursor(range)
        var first = -1
        var last = -1
        var bytes = 0L
        var p = maxOf(cursor, range.first)
        while (p <= range.last) {
            val free = !have[p] && inProgress[p] == null
            if (first < 0) {
                if (free) {
                    first = p
                    last = p
                    bytes = meta.pieceSize(p).toLong()
                }
            } else {
                if (!free || bytes + meta.pieceSize(p) > maxBytes) break
                last = p
                bytes += meta.pieceSize(p)
            }
            p++
        }
        if (first < 0) return IntRange.EMPTY
        for (q in first..last) {
            val w = work(q)
            for (b in 0 until w.blockCount) if (!w.received[b]) w.requesters[b] = arrayListOf(r)
        }
        return first..last
    }

    private fun isEndgame(range: IntRange): Boolean {
        for (p in maxOf(cursor, range.first)..range.last) {
            if (have[p]) continue
            val w = inProgress[p] ?: return false
            if (w.hasUnrequested()) return false
        }
        return true
    }

    private fun advanceCursor(range: IntRange) {
        if (cursor < range.first) cursor = range.first
        while (cursor <= range.last) {
            if (have[cursor]) {
                cursor++
                continue
            }
            val w = inProgress[cursor] ?: break
            if (w.hasUnrequested()) break
            cursor++
        }
    }

    private fun takeBlocks(w: PieceWork, r: Requester, max: Int, out: MutableList<BlockRequest>) {
        for (b in 0 until w.blockCount) {
            if (out.size >= max) return
            if (!w.isUnrequested(b)) continue
            (w.requesters[b] ?: ArrayList<Requester>(2).also { w.requesters[b] = it }).add(r)
            out.add(BlockRequest(w.index, b * Wire.BLOCK, w.blockLength(b)))
        }
    }

    private fun work(p: Int): PieceWork = inProgress.getOrPut(p) {
        PieceWork(p, meta.pieceSize(p)).also { w -> markPadBlocks(w) }
    }

    /** 패드 파일 안에만 있는 블록은 받지 않는다(내용은 0 이고 쓰지 않는다 — BEP 47). */
    private fun markPadBlocks(w: PieceWork) {
        val base = meta.pieceOffset(w.index)
        for (b in 0 until w.blockCount) {
            val start = base + b * Wire.BLOCK
            val end = start + w.blockLength(b)
            var pos = start
            var allPad = true
            var fi = meta.fileAt(pos)
            while (pos < end && fi < meta.files.size) {
                val f = meta.files[fi]
                if (f.length == 0L || pos >= f.end) {
                    fi++
                    continue
                }
                if (!f.isPad) {
                    allPad = false
                    break
                }
                pos = minOf(end, f.end)
                fi++
            }
            if (allPad) {
                w.received[b] = true
                w.receivedCount++
            }
        }
    }

    // ── 받은 것 ─────────────────────────────────────────────────────────────

    enum class BlockResult { ACCEPTED, DUPLICATE, UNEXPECTED }

    /**
     * 블록이 왔다 — **그 피어가 그 블록을 청했는지는 연결 쪽이 이미 보았다**(청하지 않은 블록은 여기까지 오지 않고 그 피어를 끊는다).
     * 여기서는: 모양이 틀리면 [BlockResult.UNEXPECTED], 이미 받았거나 그 조각을 버렸으면(해시 실패 뒤 늦게 온 것·엔드게임의 겹침)
     * [BlockResult.DUPLICATE] — 버린다. 받아들였으면 [cancelFor] 에 같은 블록을 청했던 다른 쪽들을 넣는다(취소를 보낼 곳).
     */
    fun onBlock(r: Requester, piece: Int, begin: Int, length: Int, cancelFor: MutableList<Requester>): BlockResult {
        if (piece !in 0 until pieceCount || begin < 0 || begin % Wire.BLOCK != 0) return BlockResult.UNEXPECTED
        val size = meta.pieceSize(piece)
        val b = begin / Wire.BLOCK
        val expected = if (begin >= size) -1 else minOf(Wire.BLOCK, size - begin)
        if (length != expected) return BlockResult.UNEXPECTED
        val w = inProgress[piece] ?: return BlockResult.DUPLICATE
        if (w.received[b]) return BlockResult.DUPLICATE
        w.received[b] = true
        w.receivedCount++
        w.contributors.add(r)
        w.requesters[b]?.forEach { if (it !== r) cancelFor.add(it) }
        w.requesters[b] = null
        return BlockResult.ACCEPTED
    }

    fun work(piece: Int, create: Boolean): PieceWork? = if (create) work(piece) else inProgress[piece]

    /** [r] 이 청한 것을 모두 거둔다(choke·끊김·시간 초과). 거둔 블록 수. */
    fun dropRequests(r: Requester): Int {
        var n = 0
        for (w in inProgress.values) {
            for (b in 0 until w.blockCount) {
                val list = w.requesters[b] ?: continue
                if (list.removeAll { it === r }) {
                    n++
                    if (list.isEmpty()) w.requesters[b] = null
                    if (w.index < cursor) cursor = w.index
                }
            }
        }
        return n
    }

    /** 블록 하나를 거둔다(reject). */
    fun dropRequest(r: Requester, piece: Int, begin: Int) {
        val w = inProgress[piece] ?: return
        if (begin % Wire.BLOCK != 0) return
        val b = begin / Wire.BLOCK
        if (b >= w.blockCount) return
        val list = w.requesters[b] ?: return
        list.removeAll { it === r }
        if (list.isEmpty()) w.requesters[b] = null
        if (piece < cursor) cursor = piece
    }

    /** 해시가 맞았다. 받는 파일이 끝났으면 다음 파일을 고르게 된다. */
    fun onPieceVerified(piece: Int) {
        inProgress.remove(piece)
        if (have[piece]) return
        have.set(piece)
        if (availability[piece] == 0) for (f in fileRange(piece)) unavailable[f]--
        if (activeFile >= 0 && isFileDone(activeFile)) activeFile = -1
    }

    /** 해시가 틀렸다 — 그 조각을 처음부터 다시. 블록을 보낸 쪽들을 돌려준다(탓할 곳). */
    fun onPieceFailed(piece: Int): Set<Requester> {
        val w = inProgress.remove(piece) ?: return emptySet()
        if (piece < cursor) cursor = piece
        return w.contributors
    }

    /** 받는 중인 조각을 버린다(고르기가 바뀌어 그림자 파일을 옮길 때 등). */
    fun resetPiece(piece: Int) {
        inProgress.remove(piece)
        if (piece < cursor) cursor = piece
    }

    fun inProgressPieces(): Set<Int> = inProgress.keys.toSet()

    // ── 구할 수 있음 ─────────────────────────────────────────────────────────

    fun addAvailability(piece: Int) {
        if (availability[piece]++ == 0 && !have[piece]) for (f in fileRange(piece)) unavailable[f]--
    }

    fun removeAvailability(piece: Int) {
        if (availability[piece] <= 0) return
        if (--availability[piece] == 0 && !have[piece]) for (f in fileRange(piece)) unavailable[f]++
    }

    fun addBitfield(bits: Bitfield) {
        if (bits.isComplete) {
            seeds++
            return
        }
        for (p in 0 until pieceCount) if (bits[p]) addAvailability(p)
    }

    fun removeBitfield(bits: Bitfield, wasSeed: Boolean) {
        if (wasSeed) {
            seeds = maxOf(0, seeds - 1)
            return
        }
        for (p in 0 until pieceCount) if (bits[p]) removeAvailability(p)
    }

    /** 피어가 HAVE 로 모두 갖게 되면 시드로 옮긴다(조각별 수를 빼고 [seeds] 를 늘린다). */
    fun promoteToSeed(bits: Bitfield) {
        for (p in 0 until pieceCount) if (bits[p]) removeAvailability(p)
        seeds++
    }

    fun availabilityOf(piece: Int): Int = availability[piece] + seeds

    /** 그 조각이 걸친 파일 가운데 길이가 있는 것(길이 0 인 파일은 조각이 없다 — 세지 않는다). */
    private fun fileRange(piece: Int): List<Int> = meta.filesOfPiece(piece).filter { meta.files[it].length > 0 }

    companion object {
        const val MAX_ENDGAME_REQUESTERS = 3

        /** 순차의 창 — 커서 앞으로 이만큼(바이트) 안에서만 청한다. 피어 30개가 조각을 하나씩 맡아도 남는 크기. */
        const val WINDOW_BYTES = 64L * 1024 * 1024

        /** 창의 최소 조각 수(조각이 큰 토렌트). */
        const val MIN_WINDOW_PIECES = 32
    }
}
