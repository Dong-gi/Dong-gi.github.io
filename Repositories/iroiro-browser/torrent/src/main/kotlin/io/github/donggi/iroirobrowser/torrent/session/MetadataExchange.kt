package io.github.donggi.iroirobrowser.torrent.session

import io.github.donggi.iroirobrowser.torrent.util.Sha1
import io.github.donggi.iroirobrowser.torrent.wire.ExtensionHandshake
import io.github.donggi.iroirobrowser.torrent.wire.MetadataMessage

/**
 * 마그넷의 info 사전을 피어에게서 받는다(BEP 9 의 ut_metadata). 16 KiB 조각씩 청해 모은 뒤 **SHA-1 이 info-hash 와 맞을 때만** 넘긴다
 * (그 전에는 파싱하지 않는다).
 *
 * 크기는 피어가 확장 핸드셰이크의 `metadata_size` 로 알린 값이다 — **남이 말한 값**이라: 10 MiB 넘으면 받지 않고, 크기마다 그 크기를 말한
 * 피어를 모아 가장 많은 피어가 말한 크기부터 받는다. 다 모은 것의 해시가 틀리면 그 크기를 버리고 그 크기를 말한 피어를 탓한다(거짓
 * metadata_size). 조각 하나에 30초를 넘기면 다른 피어에게 다시 청한다. 이벤트 루프에서만 쓴다.
 */
internal class MetadataExchange(private val infoHash: ByteArray) {

    private val votes = HashMap<Int, LinkedHashSet<Any>>()
    private val rejectedSizes = HashSet<Int>()
    private var size = -1
    private var pieces: Array<ByteArray?> = emptyArray()
    private var askedFrom: Array<Any?> = emptyArray()
    private var askedAt = LongArray(0)
    private val contributors = LinkedHashSet<Any>()

    /** 지금 모으는 크기(-1 이면 아직). */
    val currentSize: Int get() = size

    /** 받은 조각 수 / 전체. */
    val progress: Pair<Int, Int> get() = pieces.count { it != null } to pieces.size

    fun onPeerSize(peer: Any, metadataSize: Int?) {
        if (metadataSize == null || metadataSize <= 0 || metadataSize > ExtensionHandshake.MAX_METADATA) return
        if (metadataSize in rejectedSizes) return
        votes.getOrPut(metadataSize) { LinkedHashSet() }.add(peer)
        if (size < 0) choose()
    }

    fun onPeerGone(peer: Any) {
        for (v in votes.values) v.remove(peer)
        for (i in askedFrom.indices) if (askedFrom[i] === peer) askedFrom[i] = null
    }

    private fun choose() {
        val best = votes.entries.filter { it.key !in rejectedSizes && it.value.isNotEmpty() }.maxByOrNull { it.value.size } ?: return
        size = best.key
        val count = (size + MetadataMessage.PIECE_SIZE - 1) / MetadataMessage.PIECE_SIZE
        pieces = arrayOfNulls(count)
        askedFrom = arrayOfNulls(count)
        askedAt = LongArray(count)
        contributors.clear()
    }

    /** [peer] 가 우리가 고른 크기를 말했으면 다음에 청할 조각(없으면 null). */
    fun nextRequest(peer: Any, now: Long): Int? {
        if (size < 0) return null
        if (votes[size]?.contains(peer) != true) return null
        // 이 피어가 이미 청을 받고 있으면 하나씩만.
        for (i in askedFrom.indices) if (askedFrom[i] === peer && pieces[i] == null) return null
        for (i in pieces.indices) {
            if (pieces[i] != null) continue
            val owner = askedFrom[i]
            if (owner == null || now - askedAt[i] > PIECE_TIMEOUT_MS) {
                askedFrom[i] = peer
                askedAt[i] = now
                return i
            }
        }
        return null
    }

    sealed class Outcome {
        object Pending : Outcome()
        class Complete(val info: ByteArray) : Outcome()

        /** 다 모은 것의 해시가 틀렸다 — [blame] 의 피어들을 끊는다. */
        class Mismatch(val blame: Set<Any>) : Outcome()
    }

    fun onData(peer: Any, msg: MetadataMessage): Outcome {
        if (size < 0 || msg.piece !in pieces.indices) return Outcome.Pending
        if (msg.totalSize != null && msg.totalSize != size.toLong()) return Outcome.Pending
        val expected = if (msg.piece == pieces.size - 1) size - msg.piece * MetadataMessage.PIECE_SIZE else MetadataMessage.PIECE_SIZE
        if (msg.data.size != expected || pieces[msg.piece] != null) return Outcome.Pending
        pieces[msg.piece] = msg.data
        askedFrom[msg.piece] = null
        contributors.add(peer)
        if (pieces.any { it == null }) return Outcome.Pending
        val all = ByteArray(size)
        var off = 0
        for (p in pieces) {
            System.arraycopy(p!!, 0, all, off, p.size)
            off += p.size
        }
        if (Sha1.digest(all).contentEquals(infoHash)) return Outcome.Complete(all)
        val blame = LinkedHashSet<Any>(contributors)
        blame.addAll(votes[size].orEmpty())
        rejectedSizes.add(size)
        votes.remove(size)
        size = -1
        choose()
        return Outcome.Mismatch(blame)
    }

    fun onReject(peer: Any, piece: Int) {
        if (piece in askedFrom.indices && askedFrom[piece] === peer) {
            askedFrom[piece] = null
            // 거절한 피어에게는 다시 청하지 않게 표에서 뺀다.
            votes[size]?.remove(peer)
        }
    }

    companion object {
        const val PIECE_TIMEOUT_MS = 30_000L
    }
}
