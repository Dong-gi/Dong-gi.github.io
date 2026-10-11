package io.github.donggi.iroirobrowser.torrent.wire

import io.github.donggi.iroirobrowser.torrent.bencode.BBytes
import io.github.donggi.iroirobrowser.torrent.bencode.BDict
import io.github.donggi.iroirobrowser.torrent.bencode.Bencode
import io.github.donggi.iroirobrowser.torrent.bencode.BencodeException
import io.github.donggi.iroirobrowser.torrent.util.readIntBE
import io.github.donggi.iroirobrowser.torrent.util.writeIntBE

/** 피어 와이어(BEP 3·6·10)의 상수와 핸드셰이크. */
object Wire {
    const val CHOKE = 0
    const val UNCHOKE = 1
    const val INTERESTED = 2
    const val NOT_INTERESTED = 3
    const val HAVE = 4
    const val BITFIELD = 5
    const val REQUEST = 6
    const val PIECE = 7
    const val CANCEL = 8
    const val PORT = 9
    const val SUGGEST = 13
    const val HAVE_ALL = 14
    const val HAVE_NONE = 15
    const val REJECT = 16
    const val ALLOWED_FAST = 17
    const val EXTENDED = 20

    /** 블록 크기 — 우리가 청하는 크기이고 받는 piece 메시지의 상한이다. */
    const val BLOCK = 16 * 1024

    const val HANDSHAKE_LENGTH = 68
    private val PROTOCOL = "BitTorrent protocol".toByteArray(Charsets.US_ASCII)

    /** 우리 예약 비트 — 확장(BEP 10, `reserved[5] & 0x10`)과 Fast(BEP 6, `reserved[7] & 0x04`). DHT 비트는 켜지 않는다(듣지 않는다 — BEP 43). */
    fun ourReserved(): ByteArray = ByteArray(8).also {
        it[5] = 0x10
        it[7] = 0x04
    }

    fun handshake(infoHash: ByteArray, peerId: ByteArray): ByteArray {
        require(infoHash.size == 20 && peerId.size == 20)
        val out = ByteArray(HANDSHAKE_LENGTH)
        out[0] = 19
        System.arraycopy(PROTOCOL, 0, out, 1, 19)
        System.arraycopy(ourReserved(), 0, out, 20, 8)
        System.arraycopy(infoHash, 0, out, 28, 20)
        System.arraycopy(peerId, 0, out, 48, 20)
        return out
    }

    class Handshake(val reserved: ByteArray, val infoHash: ByteArray, val peerId: ByteArray) {
        val supportsExtensions: Boolean get() = reserved[5].toInt() and 0x10 != 0
        val supportsFast: Boolean get() = reserved[7].toInt() and 0x04 != 0
    }

    /** 68 바이트 핸드셰이크. 머리가 다르면 null. */
    fun parseHandshake(b: ByteArray, offset: Int = 0): Handshake? {
        if (b.size - offset < HANDSHAKE_LENGTH || b[offset].toInt() != 19) return null
        for (i in 0 until 19) if (b[offset + 1 + i] != PROTOCOL[i]) return null
        return Handshake(
            b.copyOfRange(offset + 20, offset + 28),
            b.copyOfRange(offset + 28, offset + 48),
            b.copyOfRange(offset + 48, offset + 68),
        )
    }

    /** 확장 메시지의 상한(확장 핸드셰이크·ut_metadata 의 사전 + 16 KiB 조각). */
    const val MAX_EXTENDED = 40 * 1024

    /** 모르는 메시지는 이 길이까지만 읽고 버린다. */
    const val MAX_UNKNOWN = 1024

    /** 조각 수를 모를 때(마그넷) bitfield 의 상한 — 조각 수 상한 2^21 의 비트. */
    const val MAX_BITFIELD_UNKNOWN = 0x200000 / 8

    /** 어느 메시지든 가질 수 있는 가장 긴 길이 — 이보다 긴 길이 접두는 id 를 보기 전에 거절한다. */
    fun maxFrameLength(pieceCount: Int): Int =
        maxOf(MAX_EXTENDED, 9 + BLOCK, MAX_UNKNOWN, if (pieceCount > 0) 1 + (pieceCount + 7) / 8 else 1 + MAX_BITFIELD_UNKNOWN)

    /**
     * 메시지 하나(id 포함, 길이 접두 뺀 것)가 가질 수 있는 길이의 범위. 범위 밖이면 그 피어는 규약을 어긴 것이다 — **길이를 보고 바로 끊고
     * 할당하지 않는다**(`0xFFFFFFFF` 같은 길이 접두). [pieceCount] 가 0 이하면 아직 모른다.
     */
    fun lengthRange(id: Int, pieceCount: Int): IntRange = when (id) {
        CHOKE, UNCHOKE, INTERESTED, NOT_INTERESTED, HAVE_ALL, HAVE_NONE -> 1..1
        HAVE, SUGGEST, ALLOWED_FAST -> 5..5
        REQUEST, CANCEL, REJECT -> 13..13
        PIECE -> 10..(9 + BLOCK)
        PORT -> 3..3
        BITFIELD -> if (pieceCount > 0) (1 + (pieceCount + 7) / 8).let { it..it } else 1..(1 + MAX_BITFIELD_UNKNOWN)
        EXTENDED -> 2..MAX_EXTENDED
        else -> 1..MAX_UNKNOWN
    }
}

/**
 * **우리가 보내는 메시지 — 이것이 전부다.** 올리기 0 의 불변식을 구조로 지킨다: `unchoke`·`piece`·`allowed_fast`·`have_all`·
 * `suggest` 와 ut_metadata 의 data 를 짓는 타입이 없다. `choke` 도 보내지 않는다(연결은 choke 로 시작하고 우리는 풀지 않는다).
 * 시험이 이 봉인 클래스의 하위 타입 목록을 박아 둔다.
 */
sealed class Outbound {
    abstract fun encode(): ByteArray

    object KeepAlive : Outbound() {
        override fun encode() = ByteArray(4)
    }

    object Interested : Outbound() {
        override fun encode() = simple(Wire.INTERESTED)
    }

    object NotInterested : Outbound() {
        override fun encode() = simple(Wire.NOT_INTERESTED)
    }

    /** 해시를 확인한 조각만. */
    class Have(val index: Int) : Outbound() {
        override fun encode(): ByteArray = frame(Wire.HAVE, 4).also { it.writeIntBE(5, index) }
    }

    /** 해시를 확인한 조각만 켠 bitfield. */
    class Bitfield(val bits: ByteArray) : Outbound() {
        override fun encode(): ByteArray = frame(Wire.BITFIELD, bits.size).also { System.arraycopy(bits, 0, it, 5, bits.size) }
    }

    class Request(val index: Int, val begin: Int, val length: Int) : Outbound() {
        override fun encode() = triple(Wire.REQUEST, index, begin, length)
    }

    class Cancel(val index: Int, val begin: Int, val length: Int) : Outbound() {
        override fun encode() = triple(Wire.CANCEL, index, begin, length)
    }

    /** BEP 6 — 아무 조각도 없을 때 첫 메시지. */
    object HaveNone : Outbound() {
        override fun encode() = simple(Wire.HAVE_NONE)
    }

    /** BEP 6 — 들어온 요청은 모두 이것으로 거절한다. */
    class RejectRequest(val index: Int, val begin: Int, val length: Int) : Outbound() {
        override fun encode() = triple(Wire.REJECT, index, begin, length)
    }

    /** BEP 10 핸드셰이크 — [ExtensionHandshake.ours] 만. `metadata_size` 를 싣지 않는다. */
    class ExtendedHandshake(val payload: ByteArray) : Outbound() {
        override fun encode(): ByteArray = extended(0, payload)
    }

    /** BEP 9 — 메타데이터 조각을 청한다. */
    class MetadataRequest(val peerExtensionId: Int, val piece: Int) : Outbound() {
        override fun encode(): ByteArray = extended(peerExtensionId, Bencode.encode(mapOf("msg_type" to 0L, "piece" to piece.toLong())))
    }

    /** BEP 9 — 남이 우리에게 청한 메타데이터는 늘 거절한다. */
    class MetadataReject(val peerExtensionId: Int, val piece: Int) : Outbound() {
        override fun encode(): ByteArray = extended(peerExtensionId, Bencode.encode(mapOf("msg_type" to 2L, "piece" to piece.toLong())))
    }

    protected fun frame(id: Int, payloadLength: Int): ByteArray {
        val out = ByteArray(5 + payloadLength)
        out.writeIntBE(0, 1 + payloadLength)
        out[4] = id.toByte()
        return out
    }

    protected fun simple(id: Int): ByteArray = frame(id, 0)

    protected fun triple(id: Int, a: Int, b: Int, c: Int): ByteArray = frame(id, 12).also {
        it.writeIntBE(5, a)
        it.writeIntBE(9, b)
        it.writeIntBE(13, c)
    }

    protected fun extended(extId: Int, payload: ByteArray): ByteArray {
        require(extId in 0..255)
        val out = frame(Wire.EXTENDED, 1 + payload.size)
        out[5] = extId.toByte()
        System.arraycopy(payload, 0, out, 6, payload.size)
        return out
    }
}

/** 받은 메시지(길이 검사를 지난 것). */
sealed class Inbound {
    object Choke : Inbound()
    object Unchoke : Inbound()
    object Interested : Inbound()
    object NotInterested : Inbound()
    class Have(val index: Int) : Inbound()
    class Bitfield(val bits: ByteArray) : Inbound()
    class Request(val index: Int, val begin: Int, val length: Int) : Inbound()
    class Piece(val index: Int, val begin: Int, val data: ByteArray) : Inbound()
    class Cancel(val index: Int, val begin: Int, val length: Int) : Inbound()
    class Port(val port: Int) : Inbound()
    class Suggest(val index: Int) : Inbound()
    object HaveAll : Inbound()
    object HaveNone : Inbound()
    class Reject(val index: Int, val begin: Int, val length: Int) : Inbound()
    class AllowedFast(val index: Int) : Inbound()
    class Extended(val extensionId: Int, val payload: ByteArray) : Inbound()
    class Unknown(val id: Int) : Inbound()

    companion object {
        /** [frame] 은 id 와 그 뒤(길이 접두 뺀 것). 길이는 [Wire.lengthRange] 로 이미 맞춘 것이다. */
        fun parse(frame: ByteArray): Inbound {
            val id = frame[0].toInt() and 0xFF
            return when (id) {
                Wire.CHOKE -> Choke
                Wire.UNCHOKE -> Unchoke
                Wire.INTERESTED -> Interested
                Wire.NOT_INTERESTED -> NotInterested
                Wire.HAVE -> Have(frame.readIntBE(1))
                Wire.BITFIELD -> Bitfield(frame.copyOfRange(1, frame.size))
                Wire.REQUEST -> Request(frame.readIntBE(1), frame.readIntBE(5), frame.readIntBE(9))
                Wire.PIECE -> Piece(frame.readIntBE(1), frame.readIntBE(5), frame.copyOfRange(9, frame.size))
                Wire.CANCEL -> Cancel(frame.readIntBE(1), frame.readIntBE(5), frame.readIntBE(9))
                Wire.PORT -> Port(((frame[1].toInt() and 0xFF) shl 8) or (frame[2].toInt() and 0xFF))
                Wire.SUGGEST -> Suggest(frame.readIntBE(1))
                Wire.HAVE_ALL -> HaveAll
                Wire.HAVE_NONE -> HaveNone
                Wire.REJECT -> Reject(frame.readIntBE(1), frame.readIntBE(5), frame.readIntBE(9))
                Wire.ALLOWED_FAST -> AllowedFast(frame.readIntBE(1))
                Wire.EXTENDED -> Extended(frame[1].toInt() and 0xFF, frame.copyOfRange(2, frame.size))
                else -> Unknown(id)
            }
        }
    }
}

/** BEP 10 확장 핸드셰이크. */
class ExtensionHandshake(
    /** 상대가 정한 ut_metadata 의 확장 id(0 이면 없음). */
    val utMetadataId: Int,
    /** 상대가 알린 메타데이터 크기(없으면 null). */
    val metadataSize: Int?,
    /** 상대가 받는 요청 수(`reqq`). */
    val requestQueue: Int?,
    /** 상대 클라이언트의 이름(`v`) — 기록용. */
    val client: String?,
) {
    companion object {
        /** 우리가 쓰는 ut_metadata 의 확장 id — 상대는 이 id 로 우리에게 보낸다. */
        const val OUR_UT_METADATA_ID = 3

        /** 메타데이터 상한(BEP 9 의 사전 크기) — 피어가 말한 크기가 이보다 크면 받지 않는다. */
        const val MAX_METADATA = 10 * 1024 * 1024

        /**
         * 우리 핸드셰이크. ut_metadata 는 **메타데이터가 필요할 때만** 알리고(그때도 남이 청하면 거절), `metadata_size` 는 싣지 않는다.
         * `v`(클라이언트 이름)·`p`·`yourip`·`ipv4`·`ipv6`·`reqq` 도 싣지 않는다 — 우리를 가리는 표지와 우리 주소를 줄인다(BEP 37 꼴의 샘).
         */
        fun ours(wantMetadata: Boolean): ByteArray {
            val m: Map<String, Any> = if (wantMetadata) mapOf("ut_metadata" to OUR_UT_METADATA_ID.toLong()) else emptyMap()
            return Bencode.encode(mapOf("m" to m))
        }

        /** 상대의 것. 깨졌으면 null(그 피어는 확장이 없는 것으로 다룬다). */
        fun parse(payload: ByteArray): ExtensionHandshake? {
            val d = try {
                Bencode.decode(payload, Bencode.Limits.PEER) as? BDict
            } catch (_: BencodeException) {
                null
            } ?: return null
            val m = d.dict("m")
            val ut = m?.long("ut_metadata")?.takeIf { it in 1..255 }?.toInt() ?: 0
            val size = d.long("metadata_size")?.takeIf { it in 1..MAX_METADATA.toLong() }?.toInt()
            val reqq = d.long("reqq")?.takeIf { it in 1..100_000 }?.toInt()
            val v = (d["v"] as? BBytes)?.utf8()?.take(64)
            return ExtensionHandshake(ut, size, reqq, v)
        }
    }
}

/** BEP 9 의 메시지(우리에게 온 것). */
class MetadataMessage(val type: Int, val piece: Int, val totalSize: Long?, val data: ByteArray) {
    companion object {
        const val REQUEST = 0
        const val DATA = 1
        const val REJECT = 2
        const val PIECE_SIZE = 16 * 1024

        fun parse(payload: ByteArray): MetadataMessage? {
            val dict = try {
                Bencode.decodePrefix(payload, 0, payload.size, Bencode.Limits.PEER) as? BDict
            } catch (_: BencodeException) {
                null
            } ?: return null
            val type = dict.long("msg_type")?.toInt() ?: return null
            val piece = dict.long("piece")?.takeIf { it in 0..1024 }?.toInt() ?: return null
            val total = dict.long("total_size")
            return MetadataMessage(type, piece, total, payload.copyOfRange(dict.end, payload.size))
        }
    }
}
