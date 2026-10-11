package io.github.donggi.iroirobrowser.torrent.dht

import io.github.donggi.iroirobrowser.torrent.util.flipBuffer
import io.github.donggi.iroirobrowser.torrent.util.clearBuffer
import io.github.donggi.iroirobrowser.torrent.net.AddressFilter
import io.github.donggi.iroirobrowser.torrent.net.Purpose
import io.github.donggi.iroirobrowser.torrent.net.Resolver
import io.github.donggi.iroirobrowser.torrent.net.Transport
import io.github.donggi.iroirobrowser.torrent.session.EventLoop
import io.github.donggi.iroirobrowser.torrent.session.IoHandler
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.SelectionKey
import java.util.Random
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

/**
 * DHT(BEP 5) **읽기 전용**(BEP 43) — 피어를 찾기만 한다. `get_peers` 를 반복해 info-hash 에 가까운 노드로 다가가며 `values`(피어)를 모은다.
 *
 * - 남의 질의에는 답하지 않는다(데이터그램을 버린다). `announce_peer` 를 하지 않는다 — 우리는 받기만 하고 들어오는 연결을 받지 않는다.
 * - 모든 질의에 `"ro": 1`. 노드 id 는 무작위(읽기 전용 노드는 BEP 42 의 안전한 id 가 필요 없다 — 남의 표에 들지 않는다).
 * - 남이 준 노드·피어 주소는 [filter] 로 거른다(사설·루프백·예약 대역, 1024 밑 포트). 부트스트랩 노드의 이름도 풀어 거른다.
 * - 상한: 아는 노드 [MAX_NODES](가장 오래 소식 없는 것부터 버린다), 찾기 하나의 후보 [MAX_CANDIDATES]·질의 [MAX_QUERIES_PER_LOOKUP]·
 *   시간 [LOOKUP_TIMEOUT_MS], 동시 질의 [ALPHA], 초당 질의 [MAX_QUERIES_PER_SECOND]. 토큰은 쓰지 않는다(announce 하지 않으니).
 * - 답은 **질의를 보낸 주소에서**, transaction id 가 맞을 때만 받는다.
 *
 * 이벤트 루프에서만 쓴다. 쓸 토렌트가 없으면 채널을 닫는다(배터리·데이터).
 */
internal class Dht(
    private val loop: EventLoop,
    private val transport: Transport,
    private val resolver: Resolver,
    private val filter: AddressFilter,
    private val io: Executor,
    private val random: Random,
    private val bootstrap: List<Pair<String, Int>>,
    private val log: (String) -> Unit,
) : IoHandler {

    val ourId: ByteArray = ByteArray(20).also(random::nextBytes)
    private var channel: DatagramChannel? = null
    private val nodes = object : LinkedHashMap<InetSocketAddress, DhtNode>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<InetSocketAddress, DhtNode>?): Boolean = size > MAX_NODES
    }
    private var bootstrapNodes: List<InetSocketAddress> = emptyList()
    private var bootstrapState = 0 // 0 아직, 1 푸는 중, 2 다 풀었다
    private val inflight = HashMap<Int, Query>()
    private val lookups = ArrayList<Lookup>()
    private val rx = ByteBuffer.allocate(2048)
    private var windowStart = 0L
    private var sentInWindow = 0

    /** 지금까지 받은 답 수·찾은 피어 수(화면·실험용). */
    var responses = 0
        private set
    var peersFound = 0
        private set
    var queriesSent = 0
        private set
    var timeouts = 0
        private set

    internal class Candidate(var id: ByteArray?, val address: InetSocketAddress) {
        var state = NEW
    }

    private inner class Query(val tid: Int, val target: InetSocketAddress, val lookup: Lookup, val candidate: Candidate) {
        var timer: EventLoop.Timer? = null
    }

    inner class Lookup internal constructor(
        val infoHash: ByteArray,
        private val onPeers: (List<InetSocketAddress>) -> Unit,
        private val onDone: (Int) -> Unit,
    ) {
        internal val candidates = ArrayList<Candidate>()
        internal val seen = HashSet<InetSocketAddress>()
        internal val peers = HashSet<InetSocketAddress>()
        internal var queries = 0
        internal var inflightCount = 0
        internal var finished = false
        internal val startedAt = loop.now()

        fun cancel() {
            if (finished) return
            finished = true
            lookups.remove(this)
        }

        internal fun add(id: ByteArray?, address: InetSocketAddress) {
            if (!seen.add(address)) return
            if (id != null && id.contentEquals(ourId)) return
            candidates.add(Candidate(id, address))
            if (candidates.size > MAX_CANDIDATES) {
                // 가장 먼 새 후보를 버린다.
                sortCandidates()
                val idx = candidates.indexOfLast { it.state == NEW }
                if (idx >= 0) candidates.removeAt(idx)
            }
        }

        internal fun sortCandidates() {
            candidates.sortWith { a, b ->
                val ia = a.id
                val ib = b.id
                when {
                    ia == null && ib == null -> 0
                    ia == null -> 1
                    ib == null -> -1
                    else -> Krpc.compareDistance(infoHash, ia, ib)
                }
            }
        }

        internal fun deliver(found: List<InetSocketAddress>) {
            val fresh = found.filter { peers.add(it) }
            if (fresh.isNotEmpty()) {
                peersFound += fresh.size
                onPeers(fresh)
            }
        }

        internal fun finish() {
            if (finished) return
            finished = true
            lookups.remove(this)
            log("dht lookup done: peers ${peers.size}, queries $queries, ${loop.now() - startedAt} ms")
            onDone(peers.size)
        }
    }

    /** [infoHash] 의 피어를 찾는다. 피어가 나올 때마다 [onPeers], 끝나면 [onDone](찾은 수). */
    fun lookup(infoHash: ByteArray, onPeers: (List<InetSocketAddress>) -> Unit, onDone: (Int) -> Unit): Lookup {
        val l = Lookup(infoHash, onPeers, onDone)
        lookups.add(l)
        val known = nodes.values.sortedWith { a, b -> Krpc.compareDistance(infoHash, a.id, b.id) }.take(SEED_NODES)
        for (n in known) l.add(n.id, n.address)
        if (known.size < ALPHA) {
            for (b in bootstrapNodes) l.add(null, b)
            if (bootstrapState == 0 && bootstrap.isNotEmpty()) resolveBootstrap()
        }
        pump(l)
        return l
    }

    private fun resolveBootstrap() {
        bootstrapState = 1
        try {
            io.execute {
                val out = ArrayList<InetSocketAddress>()
                for ((host, port) in bootstrap) {
                    val addrs: List<InetAddress> = try {
                        resolver.resolve(host)
                    } catch (e: Exception) {
                        continue
                    }
                    // 이중 스택이 없는 망도 있다 — IPv4 를 먼저.
                    addrs.sortedBy { if (it is Inet6Address) 1 else 0 }
                        .firstOrNull { filter.allows(it, port, Purpose.DHT_NODE) && it !is Inet6Address }
                        ?.let { out += InetSocketAddress(it, port) }
                }
                loop.post {
                    log("dht bootstrap resolved: ${out.size} nodes")
                    bootstrapNodes = out
                    // 하나도 못 풀었으면(망이 없던 때) 다음 찾기에서 다시 푼다.
                    bootstrapState = if (out.isEmpty()) 0 else 2
                    for (l in lookups.toList()) {
                        for (b in out) l.add(null, b)
                        pump(l)
                    }
                }
            }
        } catch (_: RejectedExecutionException) {
            bootstrapState = 0
        }
    }

    private fun pump(l: Lookup) {
        if (l.finished) return
        if (loop.now() - l.startedAt > LOOKUP_TIMEOUT_MS || l.queries >= MAX_QUERIES_PER_LOOKUP) {
            if (l.inflightCount == 0) l.finish()
            return
        }
        l.sortCandidates()
        for (c in l.candidates) {
            if (l.inflightCount >= ALPHA) break
            if (c.state != NEW) continue
            if (!rateOk()) {
                loop.schedule(200) { pump(l) }
                break
            }
            if (!send(l, c)) c.state = FAILED
        }
        if (l.inflightCount == 0 && isConverged(l)) l.finish()
        else if (l.inflightCount == 0 && l.candidates.none { it.state == NEW } && bootstrapState != 1) l.finish()
    }

    /** 가까운 [K] 개가 모두 답했고, 그보다 가까운 묻지 않은 후보가 없다. */
    private fun isConverged(l: Lookup): Boolean {
        val responded = l.candidates.filter { it.state == OK && it.id != null }
        if (responded.size < K) return l.candidates.none { it.state == NEW || it.state == INFLIGHT } && bootstrapState != 1
        val kth = responded[K - 1].id!!
        return l.candidates.none { (it.state == NEW || it.state == INFLIGHT) && it.id != null && Krpc.compareDistance(l.infoHash, it.id!!, kth) < 0 }
    }

    private fun rateOk(): Boolean {
        val now = loop.now()
        if (now - windowStart >= 1000) {
            windowStart = now
            sentInWindow = 0
        }
        return sentInWindow < MAX_QUERIES_PER_SECOND
    }

    private fun send(l: Lookup, c: Candidate): Boolean {
        if (!filter.allows(c.address, Purpose.DHT_NODE)) return false
        val ch = try {
            ensureChannel()
        } catch (e: Exception) {
            log("dht channel: $e")
            return false
        }
        var tid: Int
        do {
            tid = random.nextInt() and 0xFFFF
        } while (inflight.containsKey(tid))
        val tidBytes = byteArrayOf((tid ushr 8).toByte(), tid.toByte())
        val packet = Krpc.getPeers(tidBytes, ourId, l.infoHash)
        try {
            transport.sendUdp(ch, ByteBuffer.wrap(packet), c.address)
        } catch (e: Exception) {
            return false
        }
        sentInWindow++
        queriesSent++
        val q = Query(tid, c.address, l, c)
        inflight[tid] = q
        c.state = INFLIGHT
        l.inflightCount++
        l.queries++
        q.timer = loop.schedule(QUERY_TIMEOUT_MS) { onTimeout(q) }
        return true
    }

    private fun onTimeout(q: Query) {
        if (inflight.remove(q.tid) == null) return
        q.candidate.state = FAILED
        timeouts++
        q.lookup.inflightCount--
        nodes.remove(q.target)
        pump(q.lookup)
    }

    private fun ensureChannel(): DatagramChannel {
        channel?.let { if (it.isOpen) return it }
        val ch = transport.openUdp()
        loop.register(ch, SelectionKey.OP_READ, this)
        channel = ch
        return ch
    }

    override fun onReady(key: SelectionKey) {
        val ch = channel ?: return
        while (true) {
            rx.clearBuffer()
            val from = ch.receive(rx) as? InetSocketAddress ?: return
            rx.flipBuffer()
            val data = ByteArray(rx.remaining())
            rx.get(data)
            handle(from, data)
        }
    }

    private fun handle(from: InetSocketAddress, data: ByteArray) {
        val msg = Krpc.parse(data) ?: return
        if (msg is Krpc.Message.Query) return // 읽기 전용 — 답하지 않는다
        if (msg.tid.size != 2) return
        val tid = ((msg.tid[0].toInt() and 0xFF) shl 8) or (msg.tid[1].toInt() and 0xFF)
        val q = inflight[tid] ?: return
        if (!sameAddress(q.target, from)) return
        inflight.remove(tid)
        q.timer?.cancel()
        val l = q.lookup
        l.inflightCount--
        when (msg) {
            is Krpc.Message.Response -> {
                responses++
                q.candidate.state = OK
                q.candidate.id = msg.id
                nodes[q.target] = DhtNode(msg.id, q.target)
                if (!l.finished) {
                    for (n in msg.nodes) {
                        if (filter.allows(n.address, Purpose.DHT_NODE)) l.add(n.id, n.address)
                    }
                    l.deliver(msg.values.filter { filter.allows(it, Purpose.PEER) })
                }
            }
            is Krpc.Message.Error -> q.candidate.state = FAILED
            is Krpc.Message.Query -> Unit
        }
        pump(l)
    }

    private fun sameAddress(a: InetSocketAddress, b: InetSocketAddress): Boolean {
        if (a.port != b.port) return false
        val x = a.address?.address ?: return false
        val y = b.address?.address ?: return false
        if (x.contentEquals(y)) return true
        // 이중 스택 소켓이 IPv4-mapped 로 돌려줄 때
        fun v4(z: ByteArray): ByteArray? = when {
            z.size == 4 -> z
            z.size == 16 && (0 until 10).all { z[it].toInt() == 0 } && z[10].toInt() == -1 && z[11].toInt() == -1 -> z.copyOfRange(12, 16)
            else -> null
        }
        val vx = v4(x)
        val vy = v4(y)
        return vx != null && vy != null && vx.contentEquals(vy)
    }

    override fun onFailure(e: Exception) {
        log("dht channel failed: $e")
        runCatching { channel?.close() }
        channel = null
    }

    /** 찾기가 하나도 없으면 채널을 닫는다(노드 표는 남긴다 — 다음 찾기가 빠르다). */
    fun idle() {
        if (lookups.isNotEmpty()) return
        for (q in inflight.values) q.timer?.cancel()
        inflight.clear()
        runCatching { channel?.close() }
        channel = null
    }

    fun close() {
        for (l in lookups.toList()) l.cancel()
        idle()
    }

    val knownNodes: Int get() = nodes.size

    companion object {
        const val K = 8
        const val ALPHA = 4
        const val SEED_NODES = 16
        const val MAX_NODES = 512
        const val MAX_CANDIDATES = 256
        const val MAX_QUERIES_PER_LOOKUP = 300
        const val MAX_QUERIES_PER_SECOND = 40
        const val QUERY_TIMEOUT_MS = 5_000L
        const val LOOKUP_TIMEOUT_MS = 60_000L

        private const val NEW = 0
        private const val INFLIGHT = 1
        private const val OK = 2
        private const val FAILED = 3

        /** 공개 부트스트랩 노드(사용자 승인 — 공개 노드라 써도 된다). */
        val BOOTSTRAP = listOf(
            "router.bittorrent.com" to 6881,
            "dht.transmissionbt.com" to 6881,
            "router.utorrent.com" to 6881,
            "dht.libtorrent.org" to 25401,
        )
    }
}
