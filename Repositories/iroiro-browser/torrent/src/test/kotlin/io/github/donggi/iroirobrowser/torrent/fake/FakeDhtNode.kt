package io.github.donggi.iroirobrowser.torrent.fake

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

/** DHT 노드가 받은 질의 하나(BEP 5). [readOnly] 는 맨 위 "ro"(BEP 43) — 없으면 null. */
class DhtQuery(
    val method: String,
    val transactionId: ByteArray,
    val args: Map<String, Any>,
    val readOnly: Long?,
    val from: InetSocketAddress,
)

/** 질의에 어떻게 답할지. */
sealed class DhtBehavior {
    object Normal : DhtBehavior()

    /** 기록만 하고 답하지 않는다. */
    object Silent : DhtBehavior()

    /** bencode 가 아닌 바이트로 답한다. */
    object Malformed : DhtBehavior()

    /** 정상 답이지만 "t" 가 질의와 다르다. */
    object WrongTransactionId : DhtBehavior()

    /** 모든 질의에 오류 [code]·[message] 로 답한다. */
    class Error(val code: Long, val message: String) : DhtBehavior()
}

/**
 * 가짜 DHT 노드(BEP 5 KRPC over UDP). ping·find_node·get_peers·announce_peer 에 답한다.
 *
 * - 응답의 "r" 에는 늘 자기 "id".
 * - get_peers: 그 info-hash 의 피어가 [peersByInfoHash] 에 있으면 "values"(6바이트 꼴 목록). 피어가 없거나 [nodes] 가
 *   비어 있지 않으면 "nodes"(26바이트씩, info-hash 에 XOR 거리로 가까운 [k] 개)도 넣는다. 늘 "token" 을 준다.
 * - find_node: target 에 가까운 [k] 개를 "nodes" 로.
 * - announce_peer: 기록하고 {id} 로 답한다(token 이 [token] 과 다르면 오류 203). 피어 목록에 더하지는 않는다.
 * - 질의가 깨졌으면(bencode 가 아니거나 정규형이 아님, 필수 인자 없음) 오류 203, 모르는 메서드는 204.
 *   bencode 로 풀리지 않은 패킷은 [badPackets] 에 남는다.
 *
 * @param bindAddress 설 루프백 주소(기본 127.0.0.1). 루프백이 아니면 예외.
 */
class FakeDhtNode(val nodeId: ByteArray, val bindAddress: InetAddress = loopback(1)) : Closeable {
    init {
        require(nodeId.size == 20) { "노드 id 는 20바이트" }
    }

    /** 키: info-hash hex 소문자. */
    val peersByInfoHash: ConcurrentHashMap<String, List<InetSocketAddress>> = ConcurrentHashMap()

    @Volatile
    var nodes: List<Pair<ByteArray, InetSocketAddress>> = emptyList()

    @Volatile
    var behavior: DhtBehavior = DhtBehavior.Normal

    @Volatile
    var token: ByteArray = "tok".toByteArray()

    /** 답에 넣을 가까운 노드의 수(더한 손잡이). */
    @Volatile
    var k: Int = 8

    private val lock = Any()
    private val queryList = ArrayList<DhtQuery>()
    private val badList = ArrayList<ByteArray>()

    val queries: List<DhtQuery> get() = synchronized(lock) { queryList.toList() }
    val badPackets: List<ByteArray> get() = synchronized(lock) { badList.map { it.copyOf() } }

    private val socket = DatagramSocket(InetSocketAddress(fakeRequireLoopback(bindAddress), 0))
    private val threads = FakeThreads("fake-dht")

    val port: Int = socket.localPort
    val address: InetSocketAddress = InetSocketAddress(bindAddress, port)

    init {
        threads.start("recv") { receiveLoop() }
    }

    override fun close() {
        socket.fakeCloseQuietly()
        threads.closeAndJoin()
    }

    private fun receiveLoop() {
        val buf = ByteArray(65_535)
        while (!socket.isClosed) {
            val packet = DatagramPacket(buf, buf.size)
            try {
                socket.receive(packet)
            } catch (_: IOException) {
                return
            }
            handle(buf.copyOfRange(packet.offset, packet.offset + packet.length), packet.socketAddress as InetSocketAddress)
        }
    }

    private fun handle(data: ByteArray, from: InetSocketAddress) {
        val message = try {
            FakeBencode.decode(data, strict = true)
        } catch (_: FakeBencode.FormatException) {
            synchronized(lock) { badList += data }
            // 정규형이 아니어도 풀리면 그 "t" 로 답한다.
            val lenient = try {
                FakeBencode.decode(data, strict = false)
            } catch (_: FakeBencode.FormatException) {
                null
            }
            val t = (lenient as? Map<*, *>)?.get("t") as? ByteArray ?: ByteArray(0)
            sendError(t, 203, "invalid bencoding", from)
            return
        }
        if (message !is Map<*, *>) {
            synchronized(lock) { badList += data }
            sendError(ByteArray(0), 203, "message is not a dictionary", from)
            return
        }
        val y = (message["y"] as? ByteArray)?.let { String(it, Charsets.ISO_8859_1) }
        if (y != "q") return // 응답·오류는 이 노드가 보낸 질의가 없으므로 무시한다
        val t = message["t"] as? ByteArray
        if (t == null) {
            sendError(ByteArray(0), 203, "missing t", from)
            return
        }
        val method = (message["q"] as? ByteArray)?.let { String(it, Charsets.ISO_8859_1) }
        @Suppress("UNCHECKED_CAST")
        val args = message["a"] as? Map<String, Any>
        if (method == null || args == null) {
            sendError(t, 203, "missing q or a", from)
            return
        }
        synchronized(lock) { queryList += DhtQuery(method, t, args, message["ro"] as? Long, from) }

        val b = behavior
        when (b) {
            DhtBehavior.Silent -> return
            DhtBehavior.Malformed -> {
                sendRaw(MALFORMED, from)
                return
            }
            is DhtBehavior.Error -> {
                sendError(t, b.code, b.message, from)
                return
            }
            DhtBehavior.Normal, DhtBehavior.WrongTransactionId -> Unit
        }
        val replyT = if (b == DhtBehavior.WrongTransactionId) t + byteArrayOf(0x7F) else t

        val id = args["id"] as? ByteArray
        if (id == null || id.size != 20) {
            sendError(replyT, 203, "invalid id", from)
            return
        }
        val r = LinkedHashMap<String, Any>()
        r["id"] = nodeId
        when (method) {
            "ping" -> Unit
            "find_node" -> {
                val target = args["target"] as? ByteArray
                if (target == null || target.size != 20) {
                    sendError(replyT, 203, "invalid target", from)
                    return
                }
                r["nodes"] = compactNodes(target)
            }
            "get_peers" -> {
                val infoHash = args["info_hash"] as? ByteArray
                if (infoHash == null || infoHash.size != 20) {
                    sendError(replyT, 203, "invalid info_hash", from)
                    return
                }
                r["token"] = token
                val values = peersByInfoHash[infoHash.fakeHex()].orEmpty().mapNotNull(::fakeCompact4)
                if (values.isNotEmpty()) r["values"] = values
                if (values.isEmpty() || nodes.isNotEmpty()) r["nodes"] = compactNodes(infoHash)
            }
            "announce_peer" -> {
                val infoHash = args["info_hash"] as? ByteArray
                val tokenArg = args["token"] as? ByteArray
                val portArg = args["port"] as? Long
                val implied = (args["implied_port"] as? Long) == 1L
                if (infoHash == null || infoHash.size != 20 || (portArg == null && !implied)) {
                    sendError(replyT, 203, "invalid arguments", from)
                    return
                }
                if (tokenArg == null || !tokenArg.contentEquals(token)) {
                    sendError(replyT, 203, "bad token", from)
                    return
                }
            }
            else -> {
                sendError(replyT, 204, "Method Unknown", from)
                return
            }
        }
        val reply = LinkedHashMap<String, Any>()
        reply["t"] = replyT
        reply["y"] = "r".toByteArray()
        reply["r"] = r
        sendRaw(FakeBencode.encode(reply), from)
    }

    /** [target] 에 XOR 거리로 가까운 차례로 k 개, 26바이트씩(IPv4 만). */
    private fun compactNodes(target: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        nodes
            .filter { fakeCompact4(it.second) != null && it.first.size == 20 }
            .sortedWith { a, b -> java.util.Arrays.compareUnsigned(xor(a.first, target), xor(b.first, target)) }
            .take(k)
            .forEach { (nid, addr) ->
                out.write(nid)
                out.write(fakeCompact4(addr)!!)
            }
        return out.toByteArray()
    }

    private fun xor(a: ByteArray, b: ByteArray): ByteArray = ByteArray(20) { (a[it].toInt() xor b[it].toInt()).toByte() }

    private fun sendError(t: ByteArray, code: Long, message: String, to: InetSocketAddress) {
        val m = LinkedHashMap<String, Any>()
        m["t"] = t
        m["y"] = "e".toByteArray()
        m["e"] = listOf(code, message.toByteArray(Charsets.UTF_8))
        sendRaw(FakeBencode.encode(m), to)
    }

    private fun sendRaw(bytes: ByteArray, to: InetSocketAddress) {
        try {
            socket.send(DatagramPacket(bytes, bytes.size, to))
        } catch (_: IOException) {
        }
    }

    private companion object {
        val MALFORMED: ByteArray = byteArrayOf(0xFF.toByte(), 0x00, 'n'.code.toByte(), 'o'.code.toByte(), 't'.code.toByte(), ' '.code.toByte(), 'b'.code.toByte(), 'e'.code.toByte())
    }
}
