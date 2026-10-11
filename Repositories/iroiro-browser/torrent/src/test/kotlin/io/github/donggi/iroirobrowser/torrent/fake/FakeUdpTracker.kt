package io.github.donggi.iroirobrowser.torrent.fake

import java.io.Closeable
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/** UDP 트래커가 받은 connect(BEP 15). */
class UdpConnect(val transactionId: Int, val from: InetSocketAddress, val answered: Boolean = true)

/**
 * UDP 트래커가 받은 announce(BEP 15, IPv4 꼴 98바이트).
 * [ip]·[key] 는 부호 없는 32비트를 Int 로 그대로 담는다. [port] 는 0~65535.
 * [answered] 는 더한 필드 — dropFirst 로 버렸거나 모르는 connection id 라 무시했으면 false.
 */
class UdpAnnounce(
    val connectionId: Long,
    val transactionId: Int,
    val infoHash: ByteArray,
    val peerId: ByteArray,
    val downloaded: Long,
    val left: Long,
    val uploaded: Long,
    val event: Int,
    val ip: Int,
    val key: Int,
    val numWant: Int,
    val port: Int,
    val from: InetSocketAddress,
    val answered: Boolean = true,
)

/** UDP 트래커가 받은 scrape(더한 것). */
class UdpScrape(val connectionId: Long, val transactionId: Int, val infoHashes: List<ByteArray>, val from: InetSocketAddress, val answered: Boolean)

/**
 * 가짜 UDP 트래커(BEP 15). connect·announce·scrape 에 답한다.
 *
 * - 받은 패킷은 모두 센다. 처음 [dropFirst] 개에는 답하지 않는다(그래도 기록은 한다 — 재전송 수를 셀 수 있게).
 * - [requireConnectionId] 면 이 트래커가 내준 적 없는 connection id 의 announce·scrape 는 무시한다.
 * - [errorMessage] 가 있으면 announce 에 action 3(error)으로 답한다.
 * - [wrongTransactionId] 면 모든 답의 transaction id 를 틀리게(xor 0x55AA55AA) 보낸다.
 * - connection id 의 만료(BEP 15 의 2분)는 흉내 내지 않는다.
 *
 * @param bindAddress 설 루프백 주소(기본 127.0.0.1). 루프백이 아니면 예외.
 */
class FakeUdpTracker(val bindAddress: InetAddress = loopback(1)) : Closeable {
    @Volatile
    var peers: List<InetSocketAddress> = emptyList()

    @Volatile
    var interval: Int = 1800

    @Volatile
    var seeders: Int = 1

    @Volatile
    var leechers: Int = 0

    @Volatile
    var dropFirst: Int = 0

    @Volatile
    var errorMessage: String? = null

    @Volatile
    var wrongTransactionId: Boolean = false

    @Volatile
    var requireConnectionId: Boolean = true

    private val lock = Any()
    private val connectList = ArrayList<UdpConnect>()
    private val announceList = ArrayList<UdpAnnounce>()
    private val scrapeList = ArrayList<UdpScrape>()

    val connects: List<UdpConnect> get() = synchronized(lock) { connectList.toList() }
    val announces: List<UdpAnnounce> get() = synchronized(lock) { announceList.toList() }
    val scrapes: List<UdpScrape> get() = synchronized(lock) { scrapeList.toList() }

    private val issued: MutableSet<Long> = ConcurrentHashMap.newKeySet()
    private val random = SecureRandom()
    private var receivedCount = 0
    private val socket = DatagramSocket(InetSocketAddress(fakeRequireLoopback(bindAddress), 0))
    private val threads = FakeThreads("fake-udp-tracker")

    val port: Int = socket.localPort

    /** "udp://<bindAddress>:<port>/announce". */
    val announceUrl: String = "udp://${fakeUrlHost(bindAddress)}:$port/announce"

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
            val data = buf.copyOfRange(packet.offset, packet.offset + packet.length)
            handle(data, packet.socketAddress as InetSocketAddress)
        }
    }

    private fun handle(data: ByteArray, from: InetSocketAddress) {
        receivedCount++
        val dropped = receivedCount <= dropFirst
        if (data.size < 16) return
        val bb = ByteBuffer.wrap(data)
        val first = bb.getLong(0)
        val action = bb.getInt(8)
        val tx = bb.getInt(12)

        if (first == PROTOCOL_ID && action == ACTION_CONNECT) {
            synchronized(lock) { connectList += UdpConnect(tx, from, !dropped) }
            if (dropped) return
            var id: Long
            do {
                id = random.nextLong()
            } while (id == 0L || id == PROTOCOL_ID)
            issued += id
            send(ByteBuffer.allocate(16).putInt(ACTION_CONNECT).putInt(answerTx(tx)).putLong(id).array(), from)
            return
        }

        val known = first in issued
        when (action) {
            ACTION_ANNOUNCE -> {
                if (data.size < 98) return
                val infoHash = data.copyOfRange(16, 36)
                val peerId = data.copyOfRange(36, 56)
                val answered = !dropped && (known || !requireConnectionId)
                val record = UdpAnnounce(
                    connectionId = first,
                    transactionId = tx,
                    infoHash = infoHash,
                    peerId = peerId,
                    downloaded = bb.getLong(56),
                    left = bb.getLong(64),
                    uploaded = bb.getLong(72),
                    event = bb.getInt(80),
                    ip = bb.getInt(84),
                    key = bb.getInt(88),
                    numWant = bb.getInt(92),
                    port = bb.getShort(96).toInt() and 0xFFFF,
                    from = from,
                    answered = answered,
                )
                synchronized(lock) { announceList += record }
                if (!answered) return
                val error = errorMessage
                if (error != null) {
                    val msg = error.toByteArray(Charsets.UTF_8)
                    send(ByteBuffer.allocate(8 + msg.size).putInt(ACTION_ERROR).putInt(answerTx(tx)).put(msg).array(), from)
                    return
                }
                val compact = peers.mapNotNull(::fakeCompact4)
                val reply = ByteBuffer.allocate(20 + compact.size * 6)
                    .putInt(ACTION_ANNOUNCE).putInt(answerTx(tx)).putInt(interval).putInt(leechers).putInt(seeders)
                compact.forEach { reply.put(it) }
                send(reply.array(), from)
            }
            ACTION_SCRAPE -> {
                val hashes = (16 until data.size - 19 step 20).map { data.copyOfRange(it, it + 20) }
                val answered = !dropped && (known || !requireConnectionId)
                synchronized(lock) { scrapeList += UdpScrape(first, tx, hashes, from, answered) }
                if (!answered) return
                val reply = ByteBuffer.allocate(8 + hashes.size * 12).putInt(ACTION_SCRAPE).putInt(answerTx(tx))
                repeat(hashes.size) { reply.putInt(seeders).putInt(0).putInt(leechers) }
                send(reply.array(), from)
            }
            else -> Unit
        }
    }

    private fun answerTx(tx: Int): Int = if (wrongTransactionId) tx xor 0x55AA55AA else tx

    private fun send(bytes: ByteArray, to: InetSocketAddress) {
        try {
            socket.send(DatagramPacket(bytes, bytes.size, to))
        } catch (_: IOException) {
        }
    }

    private companion object {
        const val PROTOCOL_ID = 0x41727101980L
        const val ACTION_CONNECT = 0
        const val ACTION_ANNOUNCE = 1
        const val ACTION_SCRAPE = 2
        const val ACTION_ERROR = 3
    }
}
