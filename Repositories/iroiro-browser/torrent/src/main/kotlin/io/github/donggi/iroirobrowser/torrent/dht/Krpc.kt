package io.github.donggi.iroirobrowser.torrent.dht

import io.github.donggi.iroirobrowser.torrent.bencode.BBytes
import io.github.donggi.iroirobrowser.torrent.bencode.BDict
import io.github.donggi.iroirobrowser.torrent.bencode.BInt
import io.github.donggi.iroirobrowser.torrent.bencode.Bencode
import io.github.donggi.iroirobrowser.torrent.bencode.BencodeException
import java.net.InetAddress
import java.net.InetSocketAddress

class DhtNode(val id: ByteArray, val address: InetSocketAddress)

/**
 * KRPC(BEP 5) — bencode 사전 하나가 UDP 데이터그램 하나. **우리 질의는 모두 맨 위에 `"ro": 1`(BEP 43 — 읽기 전용)** 을 단다 — 받는 노드는
 * 우리를 라우팅 표에 넣지 않고 남에게 알리지 않는다. 우리는 남의 질의에 답하지 않는다.
 */
object Krpc {
    const val MAX_NODES = 64
    const val MAX_VALUES = 200

    fun getPeers(tid: ByteArray, ourId: ByteArray, infoHash: ByteArray): ByteArray =
        query(tid, "get_peers", mapOf("id" to ourId, "info_hash" to infoHash))

    fun findNode(tid: ByteArray, ourId: ByteArray, target: ByteArray): ByteArray =
        query(tid, "find_node", mapOf("id" to ourId, "target" to target))

    fun ping(tid: ByteArray, ourId: ByteArray): ByteArray = query(tid, "ping", mapOf("id" to ourId))

    private fun query(tid: ByteArray, method: String, args: Map<String, Any>): ByteArray =
        Bencode.encode(mapOf("t" to tid, "y" to "q", "q" to method, "a" to args, "ro" to 1L))

    sealed class Message(val tid: ByteArray) {
        class Response(tid: ByteArray, val id: ByteArray, val nodes: List<DhtNode>, val values: List<InetSocketAddress>, val token: ByteArray?) :
            Message(tid)

        class Error(tid: ByteArray, val code: Long, val text: String) : Message(tid)

        /** 남의 질의 — 우리는 답하지 않는다. */
        class Query(tid: ByteArray, val method: String) : Message(tid)
    }

    /** 데이터그램 하나. 깨졌거나 모양이 틀리면 null. */
    fun parse(data: ByteArray): Message? {
        val d = try {
            Bencode.decode(data, Bencode.Limits.KRPC) as? BDict
        } catch (_: BencodeException) {
            null
        } ?: return null
        val tid = d.bytes("t")?.takeIf { it.size in 1..16 } ?: return null
        return when (d.string("y")) {
            "r" -> {
                val r = d.dict("r") ?: return null
                val id = r.bytes("id")?.takeIf { it.size == 20 } ?: return null
                val nodes = r.bytes("nodes")?.let(::compactNodes).orEmpty()
                val values = (r.list("values")?.items ?: emptyList()).mapNotNull { (it as? BBytes)?.bytes?.let(::compactPeer) }.take(MAX_VALUES)
                Message.Response(tid, id, nodes, values, r.bytes("token")?.takeIf { it.size <= 64 })
            }
            "e" -> {
                val e = d.list("e")?.items ?: return null
                Message.Error(tid, (e.getOrNull(0) as? BInt)?.value ?: 0, (e.getOrNull(1) as? BBytes)?.utf8()?.take(200) ?: "")
            }
            "q" -> Message.Query(tid, d.string("q")?.take(32) ?: "")
            else -> null
        }
    }

    /** 26바이트씩(id 20 + IPv4 4 + 포트 2). 길이가 26 의 배수가 아니면 버린다. */
    fun compactNodes(b: ByteArray): List<DhtNode> {
        if (b.size % 26 != 0) return emptyList()
        val out = ArrayList<DhtNode>()
        var i = 0
        while (i + 26 <= b.size && out.size < MAX_NODES) {
            val id = b.copyOfRange(i, i + 20)
            val addr = InetAddress.getByAddress(b.copyOfRange(i + 20, i + 24))
            val port = ((b[i + 24].toInt() and 0xFF) shl 8) or (b[i + 25].toInt() and 0xFF)
            out += DhtNode(id, InetSocketAddress(addr, port))
            i += 26
        }
        return out
    }

    /** values 의 하나 — 6바이트(IPv4) 또는 18바이트(IPv6, BEP 32). */
    fun compactPeer(b: ByteArray): InetSocketAddress? {
        if (b.size != 6 && b.size != 18) return null
        val addr = InetAddress.getByAddress(b.copyOfRange(0, b.size - 2))
        val port = ((b[b.size - 2].toInt() and 0xFF) shl 8) or (b[b.size - 1].toInt() and 0xFF)
        return InetSocketAddress(addr, port)
    }

    /** XOR 거리 견주기 — [target] 에 [a] 가 [b] 보다 가까우면 음수. */
    fun compareDistance(target: ByteArray, a: ByteArray, b: ByteArray): Int {
        for (i in 0 until 20) {
            val da = (a[i].toInt() xor target[i].toInt()) and 0xFF
            val db = (b[i].toInt() xor target[i].toInt()) and 0xFF
            if (da != db) return da - db
        }
        return 0
    }
}
