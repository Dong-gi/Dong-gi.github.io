package io.github.donggi.iroirobrowser.torrent.fake

import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.io.SequenceInputStream
import java.math.BigInteger
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** 시더가 받아들이는 연결 꼴. */
enum class Encryption { PLAIN_ONLY, MSE_ONLY, ACCEPT_BOTH }

/** 상대가 보낸 피어 메시지 하나. keepalive 는 id -1. 확장(20)이면 [payload] 는 확장 id 바이트부터. */
class WireMessage(val id: Int, payload: ByteArray) {
    private val bytes = payload.copyOf()

    val payload: ByteArray get() = bytes.copyOf()
    val size: Int get() = bytes.size

    /** payload 의 [at] 에서 big-endian 32비트 정수. */
    fun int(at: Int): Int = ByteBuffer.wrap(bytes).getInt(at)

    override fun toString(): String = "WireMessage(id=$id, ${bytes.size}B)"
}

/**
 * 시더의 행동. 기본값은 모든 조각을 가진 순한 시더다.
 *
 * 적대 손잡이(corrupt…·oversized…·huge…·lie… 등)는 엔진이 잘못된 상대를 걸러 내는지 보려는 것이다.
 * 더한 손잡이: [utMetadataId], [maxRequestLength], [msePadB], [msePadD], [cryptoSelectOverride], [dhtPort].
 *
 * @property have null 이면 모든 조각. 길이가 모자라면 나머지는 없는 것으로 본다.
 * @property selectRc4 상대가 RC4 를 내밀면 RC4, 아니면(또는 false 면) 평문(0x01)을 고른다 — false 면 상대가 평문을
 *   내밀지 않았어도 평문을 고른다(엔진이 내밀지 않은 방식을 거절하는지 보는 적대 꼴).
 * @property haveNone 처음 가진 조각을 비운다. fast 면 Have None, 아니면 0 으로 채운 bitfield. 조각은 [haveLater] 로.
 * @property spareBitsSet bitfield 의 남는 비트를 켠다(조각 수가 8의 배수면 남는 비트가 없어 효과가 없다). Have All 대신 bitfield 를 쓴다.
 * @property utMetadataId 우리 확장 핸드셰이크의 m.ut_metadata. 엔진이 상대의 번호로 보내는지 보려면 엔진의 것과 다르게.
 * @property maxRequestLength 이보다 긴 request 를 받으면 끊는다(BEP 3 — 16 KiB 를 넘는 요청은 끊는 것이 관례).
 * @property msePadB·msePadD MSE 의 PadB·PadD 길이(0~512). null 이면 무작위.
 * @property cryptoSelectOverride crypto_select 를 이 값으로(0x02 일 때만 그 뒤를 RC4 로 잇는다).
 * @property dhtPort 있으면 예약 비트 DHT(reserved[7] & 0x01)를 켜고 첫 메시지 뒤 port 메시지를 보낸다.
 */
@Suppress("ArrayInDataClass")
data class SeederOptions(
    val have: BooleanArray? = null,
    val encryption: Encryption = Encryption.ACCEPT_BOTH,
    val selectRc4: Boolean = true,
    val fast: Boolean = true,
    val useHaveAll: Boolean = true,
    val haveNone: Boolean = false,
    val haveLater: List<Pair<Long, Int>> = emptyList(),
    val extensions: Boolean = true,
    val serveMetadata: Boolean = true,
    val unchoke: Boolean = true,
    val allowedFast: List<Int> = emptyList(),
    val corruptPieces: Set<Int> = emptySet(),
    val sendUnrequestedBlock: Boolean = false,
    val oversizedBlock: Boolean = false,
    val hugeLengthPrefix: Boolean = false,
    val slowDripHandshakeMs: Long = 0,
    val spareBitsSet: Boolean = false,
    val haveOutOfRange: Boolean = false,
    val secondBitfield: Boolean = false,
    val lieMetadataSize: Int? = null,
    val requestFromClient: Boolean = false,
    val requestMetadataFromClient: Boolean = false,
    val disconnectAfterBlocks: Int? = null,
    val chokeAfterBlocks: Int? = null,
    val ignoreRequests: Boolean = false,
    val utMetadataId: Int = 3,
    val maxRequestLength: Int = 16 * 1024,
    val msePadB: Int? = null,
    val msePadD: Int? = null,
    val cryptoSelectOverride: Int? = null,
    val dhtPort: Int? = null,
)

/**
 * 시더가 받은 연결 하나의 기록. 목록 게터는 사본을 돌려준다.
 *
 * [encryption]: "pending"(아직 모름) → "plain" | "mse-rc4" | "mse-plain" | "refused"(거절하고 끊음).
 * MSE 를 거쳤으면 [cryptoProvide]·[cryptoSelect]·[msePadALength]·[initialPayloadLength] 가 찬다(더한 필드).
 */
class SeederConnection internal constructor(val remote: InetSocketAddress) {
    private val lock = ReentrantLock()
    private val changed = lock.newCondition()
    private val receivedList = ArrayList<WireMessage>()
    private val metadataReplies = ArrayList<Map<String, Any>>()
    private val metadataPayloads = ArrayList<ByteArray>()
    private val blocks = AtomicInteger()
    private val closedLatch = CountDownLatch(1)

    @Volatile
    private var reservedBytes: ByteArray? = null

    @Volatile
    private var infoHashBytes: ByteArray? = null

    @Volatile
    private var peerIdBytes: ByteArray? = null

    @Volatile
    private var extRaw: ByteArray? = null

    @Volatile
    var encryption: String = "pending"
        internal set

    val clientReserved: ByteArray? get() = reservedBytes?.copyOf()
    val clientInfoHash: ByteArray? get() = infoHashBytes?.copyOf()
    val clientPeerId: ByteArray? get() = peerIdBytes?.copyOf()

    val received: List<WireMessage> get() = lock.withLock { receivedList.toList() }

    /** 상대의 확장 핸드셰이크(차례를 따지지 않고 푼 것). */
    @Volatile
    var clientExtHandshake: Map<String, Any>? = null
        private set

    /** 상대의 확장 핸드셰이크의 날 바이트(정규형 bencode 인지 따로 보려면). */
    val clientExtHandshakeRaw: ByteArray? get() = extRaw?.copyOf()

    /** 우리가 청한 ut_metadata 에 대한 상대의 답 사전(data·reject). */
    val clientUtMetadataReplies: List<Map<String, Any>> get() = lock.withLock { metadataReplies.toList() }

    /** 위 답마다 사전 뒤에 붙어 온 바이트(같은 차례). */
    val clientUtMetadataPayloads: List<ByteArray> get() = lock.withLock { metadataPayloads.map { it.copyOf() } }

    val blocksSent: Int get() = blocks.get()

    /** 상대가 먼저 끊었다(우리가 끊었거나 시더를 닫았으면 false). */
    @Volatile
    var closedByPeer: Boolean = false
        internal set

    @Volatile
    var isClosed: Boolean = false
        private set

    /** 우리가 끊은 까닭(더한 필드). */
    @Volatile
    var closeReason: String? = null
        internal set

    @Volatile
    var cryptoProvide: Int? = null
        internal set

    @Volatile
    var cryptoSelect: Int? = null
        internal set

    @Volatile
    var msePadALength: Int? = null
        internal set

    @Volatile
    var initialPayloadLength: Int? = null
        internal set

    fun awaitClosed(timeoutMs: Long): Boolean = closedLatch.await(timeoutMs, TimeUnit.MILLISECONDS)

    /** [predicate] 에 맞는 받은 메시지가 올 때까지 기다린다. 시간이 다 되거나 연결이 닫히면 그때까지 온 것 중에서 찾는다. */
    fun awaitReceived(timeoutMs: Long, predicate: (WireMessage) -> Boolean): WireMessage? {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        lock.withLock {
            while (true) {
                receivedList.firstOrNull(predicate)?.let { return it }
                val left = deadline - System.nanoTime()
                if (left <= 0 || isClosed) return null
                changed.awaitNanos(left)
            }
        }
    }

    override fun toString(): String = "SeederConnection($remote, $encryption, received=${received.size}, blocksSent=$blocksSent, closed=$isClosed)"

    internal fun setHandshake(reserved: ByteArray, infoHash: ByteArray) {
        reservedBytes = reserved.copyOf()
        infoHashBytes = infoHash.copyOf()
    }

    internal fun setPeerId(peerId: ByteArray) {
        peerIdBytes = peerId.copyOf()
    }

    internal fun setExtHandshake(raw: ByteArray, decoded: Map<String, Any>?) {
        extRaw = raw.copyOf()
        clientExtHandshake = decoded
    }

    internal fun addReceived(m: WireMessage) = lock.withLock {
        receivedList += m
        changed.signalAll()
    }

    internal fun addMetadataReply(dict: Map<String, Any>, trailing: ByteArray) = lock.withLock {
        metadataReplies += dict
        metadataPayloads += trailing.copyOf()
        changed.signalAll()
    }

    internal fun countBlock() = blocks.incrementAndGet()

    internal fun markClosed() {
        isClosed = true
        lock.withLock { changed.signalAll() }
        closedLatch.countDown()
    }
}

/**
 * TCP 로 받는 가짜 BitTorrent 피어(BEP 3·6·10·9 + MSE/PE 의 받는 쪽 B). 한 포트에서 평문과 MSE 를 함께 받는다 —
 * 처음 20바이트가 19 + "BitTorrent protocol" 이면 평문, 아니면 MSE 의 Ya 로 본다. 연결마다 스레드 하나.
 *
 * 보통의 흐름: 상대 핸드셰이크(info-hash 가 다르면 끊는다) → 우리 핸드셰이크 → 첫 메시지(bitfield / Have All / Have None)
 * → (fast) allowed_fast → (dhtPort) port → (둘 다 확장) 확장 핸드셰이크 → 메시지 고리.
 * interested 를 받으면 unchoke, 가진 조각의 request 에는 piece 로 답한다. fast 가 맞으면 답할 수 없는 request 에 reject 를 보낸다
 * (choke 중이고 allowed fast 가 아님, 없는 조각, 범위 밖). fast 가 아니면 그런 request 는 조용히 버린다.
 *
 * MSE(B 쪽) 해석: Ya(96) 를 받으면 Yb + PadB 를 보내고, Ya 뒤의 512 + 20 바이트 안에서 HASH('req1', S) 를 찾아 동기를 맞춘다.
 * 그다음 HASH('req2', SKEY) xor HASH('req3', S) 로 info-hash 를 확인하고, keyA 로 VC·crypto_provide·len(PadC)·PadC·len(IA)·IA 를 푼다.
 * IA 는 고른 방식과 상관없이 늘 RC4 로 풀고(명세의 ENCRYPT(IA)), 상대 흐름의 맨 앞으로 되돌려 BitTorrent 핸드셰이크로 읽는다
 * — IA 가 핸드셰이크의 일부만 담았거나 비었으면 나머지는 그 뒤 흐름에서 읽는다. 답은 keyB 로 VC·crypto_select·len(PadD)·PadD,
 * 그 뒤는 RC4 를 골랐으면 같은 두 RC4 흐름을 잇고, 평문이면 날 바이트다. 실패하면 "refused" 로 끊는다.
 *
 * @param bindAddress 설 루프백 주소(기본 127.0.0.1). 엔진이 IP 하나에 연결 하나만 두므로 시더 여럿은 [loopback] 으로 나눠 세운다.
 */
class FakeSeeder(
    val torrent: FakeTorrent,
    val options: SeederOptions = SeederOptions(),
    val bindAddress: InetAddress = loopback(1),
) : Closeable {
    private val connLock = ReentrantLock()
    private val connAdded = connLock.newCondition()
    private val conns = ArrayList<SeederConnection>()

    @Volatile
    private var closing = false

    private val ourPeerId: ByteArray = ("-FS0001-" + (1..12).map { "0123456789"[RANDOM.nextInt(10)] }.joinToString("")).toByteArray(Charsets.US_ASCII)

    /** 이 시더의 peer id(더한 것). */
    val peerId: ByteArray get() = ourPeerId.copyOf()

    private val listener = FakeTcpListener("fake-seeder", bindAddress) { serve(it) }

    val port: Int = listener.port
    val address: InetSocketAddress = InetSocketAddress(bindAddress, port)

    val connections: List<SeederConnection> get() = connLock.withLock { conns.toList() }

    /** 받은 연결이 [n] 개가 될 때까지 기다린다. */
    fun awaitConnections(n: Int, timeoutMs: Long): Boolean {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        connLock.withLock {
            while (conns.size < n) {
                val left = deadline - System.nanoTime()
                if (left <= 0) return false
                connAdded.awaitNanos(left)
            }
            return true
        }
    }

    override fun close() {
        closing = true
        listener.close()
    }

    private fun serve(socket: Socket) {
        socket.tcpNoDelay = true
        val conn = SeederConnection(InetSocketAddress(socket.inetAddress, socket.port))
        connLock.withLock {
            conns += conn
            connAdded.signalAll()
        }
        Session(socket, conn).run()
    }

    /** 연결 하나의 상태. 메시지 고리는 이 연결의 스레드에서만 돈다. 쓰기는 [writeLock] 아래에서(haveLater 스레드와 나눈다). */
    private inner class Session(private val socket: Socket, private val conn: SeederConnection) {
        private val opts = options
        private val rawIn = BufferedInputStream(socket.getInputStream())
        private val rawData = DataInputStream(rawIn)
        private val rawOut = socket.getOutputStream()
        private val writeLock = Any()
        private val startedAt = System.nanoTime()
        private var input: DataInputStream = rawData
        private var encryptor: FakeRc4? = null

        @Volatile
        private var muted = false

        @Volatile
        private var closedByUs = false

        private val haveLock = Any()
        private var have = BooleanArray(0)
        private var amChoking = true
        private var chokedForGood = false
        private var fast = false
        private var ext = false
        private var oversizedDone = false
        private var unrequestedDone = false
        private var metadataRequested = false
        private var clientMetadataId: Int? = null

        fun run() {
            try {
                if (!openStream()) return
                if (!exchangeHandshakes()) return
                sendInitialMessages()
                startHaveLater()
                readLoop()
            } catch (e: Throwable) {
                if (!e.fakeIsDisconnect()) throw e
                if (!closedByUs && !closing) conn.closedByPeer = true
            } finally {
                socket.fakeCloseQuietly()
                conn.markClosed()
            }
        }

        // ---- 평문인가 MSE 인가

        private fun openStream(): Boolean {
            val first = ByteArray(20)
            rawData.readFully(first)
            val plain = first[0].toInt() == 19 && first.copyOfRange(1, 20).contentEquals(PSTR)
            if (plain) {
                if (opts.encryption == Encryption.MSE_ONLY) return refuse("평문 핸드셰이크를 받지 않는다(MSE_ONLY)")
                conn.encryption = "plain"
                input = DataInputStream(SequenceInputStream(ByteArrayInputStream(first), rawIn))
                return true
            }
            if (opts.encryption == Encryption.PLAIN_ONLY) return refuse("MSE 를 받지 않는다(PLAIN_ONLY)")
            val ya = ByteArray(96)
            first.copyInto(ya)
            rawData.readFully(ya, 20, 76)
            return acceptMse(ya)
        }

        private fun acceptMse(ya: ByteArray): Boolean {
            val xb = BigInteger(160, RANDOM).setBit(159)
            val yb = MSE_G.modPow(xb, MSE_P)
            val padB = ByteArray(opts.msePadB ?: RANDOM.nextInt(513)).also { RANDOM.nextBytes(it) }
            synchronized(writeLock) {
                rawOut.write(to96(yb))
                rawOut.write(padB)
                rawOut.flush()
            }
            val s = to96(BigInteger(1, ya).modPow(xb, MSE_P))
            val infoHash = torrent.infoHashInternal

            // 동기: Ya 뒤에 PadA(0~512) 와 HASH('req1', S)(20) 가 온다.
            val req1 = fakeSha1(ascii("req1"), s)
            val window = ByteArray(512 + 20)
            var n = 0
            var synced = false
            while (n < window.size) {
                val c = rawIn.read()
                if (c < 0) throw EOFException("MSE 동기 중에 끊겼다")
                window[n++] = c.toByte()
                if (n >= 20 && regionEquals(window, n - 20, req1)) {
                    synced = true
                    break
                }
            }
            if (!synced) return refuse("PadA 512 바이트 안에서 HASH('req1', S) 를 찾지 못했다")
            conn.msePadALength = n - 20

            val obfuscated = ByteArray(20).also { rawData.readFully(it) }
            val expected = xor(fakeSha1(ascii("req2"), infoHash), fakeSha1(ascii("req3"), s))
            if (!obfuscated.contentEquals(expected)) return refuse("HASH('req2', SKEY) xor HASH('req3', S) 가 이 토렌트가 아니다")

            val fromA = FakeRc4(fakeSha1(ascii("keyA"), s, infoHash))
            val toA = FakeRc4(fakeSha1(ascii("keyB"), s, infoHash))
            val head = fromA.crypt(ByteArray(14).also { rawData.readFully(it) })
            if (head.copyOfRange(0, 8).any { it != 0.toByte() }) return refuse("VC 가 0 이 아니다")
            val provide = ByteBuffer.wrap(head, 8, 4).int
            conn.cryptoProvide = provide
            val padCLength = u16(head, 12)
            if (padCLength > 512) return refuse("len(PadC) 가 512 를 넘는다: $padCLength")
            fromA.crypt(ByteArray(padCLength).also { rawData.readFully(it) })
            val iaLength = u16(fromA.crypt(ByteArray(2).also { rawData.readFully(it) }), 0)
            conn.initialPayloadLength = iaLength
            val ia = fromA.crypt(ByteArray(iaLength).also { rawData.readFully(it) })

            val override = opts.cryptoSelectOverride
            if (override == null && (provide and 0x03) == 0) return refuse("crypto_provide 에 평문도 RC4 도 없다: $provide")
            val select = override ?: if (opts.selectRc4 && (provide and 0x02) != 0) 0x02 else 0x01
            conn.cryptoSelect = select
            val padD = ByteArray(opts.msePadD ?: RANDOM.nextInt(513))
            val reply = ByteBuffer.allocate(14 + padD.size).put(ByteArray(8)).putInt(select).putShort(padD.size.toShort()).put(padD).array()
            synchronized(writeLock) {
                rawOut.write(toA.crypt(reply))
                rawOut.flush()
            }
            val rc4 = select == 0x02
            conn.encryption = if (rc4) "mse-rc4" else "mse-plain"
            encryptor = if (rc4) toA else null
            val rest: InputStream = if (rc4) Rc4InputStream(rawIn, fromA) else rawIn
            input = DataInputStream(SequenceInputStream(ByteArrayInputStream(ia), rest))
            return true
        }

        // ---- BitTorrent 핸드셰이크와 첫 메시지들

        private fun exchangeHandshakes(): Boolean {
            val pstrLength = input.readUnsignedByte()
            val pstr = ByteArray(pstrLength).also { input.readFully(it) }
            if (pstrLength != 19 || !pstr.contentEquals(PSTR)) return closeByUs("핸드셰이크 머리가 19 + 'BitTorrent protocol' 이 아니다")
            val reserved = ByteArray(8).also { input.readFully(it) }
            val infoHash = ByteArray(20).also { input.readFully(it) }
            conn.setHandshake(reserved, infoHash)
            if (!infoHash.contentEquals(torrent.infoHashInternal)) return closeByUs("info-hash 가 이 토렌트가 아니다")
            conn.setPeerId(ByteArray(20).also { input.readFully(it) })

            fast = opts.fast && (reserved[7].toInt() and 0x04) != 0
            ext = opts.extensions && (reserved[5].toInt() and 0x10) != 0
            val ours = ByteArray(8)
            if (opts.extensions) ours[5] = 0x10
            var last = 0
            if (opts.fast) last = last or 0x04
            if (opts.dhtPort != null) last = last or 0x01
            ours[7] = last.toByte()
            val hs = ByteBuffer.allocate(68).put(19).put(PSTR).put(ours).put(torrent.infoHashInternal).put(ourPeerId).array()
            if (opts.slowDripHandshakeMs > 0) {
                synchronized(writeLock) {
                    for (b in hs) {
                        val one = byteArrayOf(b)
                        rawOut.write(encryptor?.crypt(one) ?: one)
                        rawOut.flush()
                        Thread.sleep(opts.slowDripHandshakeMs)
                    }
                }
            } else {
                writeRaw(hs)
            }
            return true
        }

        private fun sendInitialMessages() {
            val count = torrent.pieceCount
            val given = opts.have
            synchronized(haveLock) {
                have = BooleanArray(count) { i -> !opts.haveNone && (given == null || (i < given.size && given[i])) }
            }
            val all = count > 0 && synchronized(haveLock) { have.all { it } }
            val none = synchronized(haveLock) { have.none { it } }
            when {
                opts.secondBitfield -> {
                    sendBitfield()
                    sendBitfield()
                }
                opts.spareBitsSet -> sendBitfield()
                fast && opts.haveNone -> send(HAVE_NONE)
                fast && all && opts.useHaveAll -> send(HAVE_ALL)
                fast && none -> send(HAVE_NONE)
                else -> sendBitfield()
            }
            if (opts.hugeLengthPrefix) {
                writeRaw(byteArrayOf(-1, -1, -1, -1))
                muted = true
                return
            }
            if (fast) opts.allowedFast.forEach { send(ALLOWED_FAST, ints(it)) }
            opts.dhtPort?.let { send(PORT, ByteBuffer.allocate(2).putShort(it.toShort()).array()) }
            if (ext) sendExtHandshake()
            if (opts.haveOutOfRange) send(HAVE, ints(count))
            if (opts.requestFromClient) {
                send(INTERESTED)
                send(REQUEST, ints(0, 0, 16384))
                send(REQUEST, ints(0, 16384, 16384))
                send(REQUEST, ints(1, 0, 16384))
            }
        }

        private fun sendBitfield() {
            val count = torrent.pieceCount
            val bits = ByteArray((count + 7) / 8)
            synchronized(haveLock) {
                for (i in 0 until count) if (have[i]) setBit(bits, i)
            }
            if (opts.spareBitsSet) for (i in count until bits.size * 8) setBit(bits, i)
            send(BITFIELD, bits)
        }

        private fun sendExtHandshake() {
            val hs = LinkedHashMap<String, Any>()
            hs["m"] = mapOf("ut_metadata" to opts.utMetadataId.toLong())
            hs["metadata_size"] = metadataSize().toLong()
            hs["p"] = socket.localPort.toLong()
            hs["reqq"] = 250L
            hs["v"] = "FakeSeeder 1".toByteArray(Charsets.US_ASCII)
            socket.inetAddress?.address?.let { hs["yourip"] = it }
            sendExtended(0, FakeBencode.encode(hs))
        }

        private fun startHaveLater() {
            if (opts.haveLater.isEmpty() || muted) return
            listener.threads.start("have-later") {
                for ((afterMs, piece) in opts.haveLater.sortedBy { it.first }) {
                    val due = startedAt + afterMs * 1_000_000
                    while (true) {
                        val left = due - System.nanoTime()
                        if (left <= 0) break
                        Thread.sleep(maxOf(1L, left / 1_000_000))
                    }
                    if (conn.isClosed || closedByUs) return@start
                    synchronized(haveLock) { if (piece in have.indices) have[piece] = true }
                    send(HAVE, ints(piece))
                }
            }
        }

        // ---- 메시지 고리

        private fun readLoop() {
            while (!closedByUs) {
                val length = input.readInt()
                if (length == 0) {
                    conn.addReceived(WireMessage(-1, EMPTY))
                    continue
                }
                if (length < 0 || length > MAX_MESSAGE) {
                    closeByUs("메시지 길이가 허용 밖: ${length.toUInt()}")
                    return
                }
                val id = input.readUnsignedByte()
                val payload = ByteArray(length - 1).also { input.readFully(it) }
                conn.addReceived(WireMessage(id, payload))
                when (id) {
                    INTERESTED -> onInterested()
                    REQUEST -> onRequest(payload)
                    EXTENDED -> onExtended(payload)
                    else -> Unit
                }
            }
        }

        private fun onInterested() {
            if (!opts.unchoke || !amChoking || chokedForGood) return
            amChoking = false
            send(UNCHOKE)
            if (opts.sendUnrequestedBlock && !unrequestedDone && torrent.pieceCount > 0) {
                unrequestedDone = true
                val length = minOf(16384, torrent.pieceSize(0))
                sendPiece(0, 0, blockData(0, 0, length))
                afterBlock()
            }
        }

        private fun onRequest(payload: ByteArray) {
            if (payload.size != 12) {
                closeByUs("request 의 길이가 12 가 아니다: ${payload.size}")
                return
            }
            val bb = ByteBuffer.wrap(payload)
            val index = bb.getInt(0)
            val begin = bb.getInt(4)
            val length = bb.getInt(8)
            if (opts.ignoreRequests) return
            if (length > opts.maxRequestLength) {
                closeByUs("request 가 너무 길다: $length")
                return
            }
            val allowedWhileChoked = fast && index in opts.allowedFast
            if (amChoking && !allowedWhileChoked) {
                if (fast) send(REJECT_REQUEST, payload)
                return
            }
            val valid = index in 0 until torrent.pieceCount && begin >= 0 && length > 0 &&
                begin.toLong() + length <= torrent.pieceSize(index)
            val owned = valid && synchronized(haveLock) { have[index] }
            if (!owned) {
                if (fast) send(REJECT_REQUEST, payload)
                return
            }
            if (opts.oversizedBlock && !oversizedDone) {
                oversizedDone = true
                val available = minOf(32 * 1024, torrent.pieceSize(index) - begin)
                val data = ByteArray(32 * 1024) { 0xA5.toByte() }
                blockData(index, begin, available).copyInto(data)
                sendPiece(index, begin, data)
            } else {
                sendPiece(index, begin, blockData(index, begin, length))
            }
            afterBlock()
        }

        private fun afterBlock() {
            val sent = conn.blocksSent
            val disconnectAt = opts.disconnectAfterBlocks
            if (disconnectAt != null && sent >= disconnectAt) {
                closeByUs("disconnectAfterBlocks=$disconnectAt")
                return
            }
            val chokeAt = opts.chokeAfterBlocks
            if (chokeAt != null && sent >= chokeAt && !chokedForGood) {
                chokedForGood = true
                amChoking = true
                send(CHOKE)
            }
        }

        private fun onExtended(payload: ByteArray) {
            if (payload.isEmpty()) return
            val extId = payload[0].toInt() and 0xFF
            val body = payload.copyOfRange(1, payload.size)
            if (extId == 0) {
                @Suppress("UNCHECKED_CAST")
                val dict = try {
                    FakeBencode.decode(body, strict = false) as? Map<String, Any>
                } catch (_: FakeBencode.FormatException) {
                    null
                }
                conn.setExtHandshake(body, dict)
                val m = dict?.get("m") as? Map<*, *>
                clientMetadataId = (m?.get("ut_metadata") as? Long)?.toInt()?.takeIf { it in 1..255 }
                val target = clientMetadataId
                if (opts.requestMetadataFromClient && target != null && !metadataRequested) {
                    metadataRequested = true
                    sendExtended(target, FakeBencode.encode(mapOf("msg_type" to 0L, "piece" to 0L)))
                }
                return
            }
            if (extId != opts.utMetadataId) return
            val (message, end) = try {
                FakeBencode.decodePrefix(body, 0, strict = false)
            } catch (_: FakeBencode.FormatException) {
                return
            }
            @Suppress("UNCHECKED_CAST")
            val dict = message as? Map<String, Any> ?: return
            when (dict["msg_type"] as? Long) {
                0L -> serveMetadataPiece(dict)
                1L, 2L -> conn.addMetadataReply(dict, body.copyOfRange(end, body.size))
                else -> Unit // BEP 9: 모르는 msg_type 은 무시한다
            }
        }

        private fun serveMetadataPiece(dict: Map<String, Any>) {
            val piece = dict["piece"] as? Long ?: return
            val target = clientMetadataId ?: return // 상대 번호를 모르면 답할 수 없다
            val size = metadataSize()
            val count = if (size <= 0) 0 else (size + 16383) / 16384
            if (!opts.serveMetadata || piece < 0 || piece >= count) {
                sendExtended(target, FakeBencode.encode(mapOf("msg_type" to 2L, "piece" to piece)))
                return
            }
            val from = piece.toInt() * 16384
            val length = minOf(16384, size - from)
            val data = if (opts.lieMetadataSize != null) {
                FakeTorrent.data(0x6D657461 + piece.toInt(), length)
            } else {
                torrent.infoBytesInternal.copyOfRange(from, from + length)
            }
            val head = FakeBencode.encode(mapOf("msg_type" to 1L, "piece" to piece, "total_size" to size.toLong()))
            sendExtended(target, head + data)
        }

        private fun metadataSize(): Int = opts.lieMetadataSize ?: torrent.infoBytesInternal.size

        private fun blockData(index: Int, begin: Int, length: Int): ByteArray {
            val data = torrent.slice(index.toLong() * torrent.pieceLength + begin, length)
            if (index in opts.corruptPieces) for (i in data.indices) data[i] = (data[i].toInt() xor 0xFF).toByte()
            return data
        }

        // ---- 쓰기

        private fun sendPiece(index: Int, begin: Int, data: ByteArray) {
            val payload = ByteBuffer.allocate(8 + data.size).putInt(index).putInt(begin).put(data).array()
            if (send(PIECE, payload)) conn.countBlock()
        }

        private fun sendExtended(extId: Int, body: ByteArray) {
            send(EXTENDED, byteArrayOf(extId.toByte()) + body)
        }

        /** 보냈으면 true(입을 닫았거나 끊었으면 false). */
        private fun send(id: Int, payload: ByteArray = EMPTY): Boolean {
            val message = ByteBuffer.allocate(5 + payload.size).putInt(1 + payload.size).put(id.toByte()).put(payload).array()
            return writeRaw(message)
        }

        private fun writeRaw(bytes: ByteArray): Boolean {
            synchronized(writeLock) {
                if (muted || closedByUs) return false
                rawOut.write(encryptor?.crypt(bytes) ?: bytes)
                rawOut.flush()
                return true
            }
        }

        private fun refuse(reason: String): Boolean {
            conn.encryption = "refused"
            return closeByUs(reason)
        }

        /** 우리가 끊는다. 늘 false(호출한 곳이 그대로 돌려주게). */
        private fun closeByUs(reason: String): Boolean {
            closedByUs = true
            conn.closeReason = reason
            socket.fakeCloseQuietly()
            return false
        }
    }

    /** 상대→우리 흐름을 RC4 로 푼다. */
    private class Rc4InputStream(private val inner: InputStream, private val rc4: FakeRc4) : InputStream() {
        override fun read(): Int {
            val c = inner.read()
            if (c < 0) return -1
            val one = byteArrayOf(c.toByte())
            rc4.cryptInPlace(one, 0, 1)
            return one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = inner.read(b, off, len)
            if (n > 0) rc4.cryptInPlace(b, off, n)
            return n
        }

        override fun available(): Int = inner.available()
    }

    private companion object {
        val RANDOM = SecureRandom()
        val EMPTY = ByteArray(0)
        val PSTR: ByteArray = "BitTorrent protocol".toByteArray(Charsets.US_ASCII)

        /** 블록(16 KiB)·ut_metadata 조각·큰 bitfield 를 넉넉히 덮는다. */
        const val MAX_MESSAGE = 1024 * 1024 + 64

        const val CHOKE = 0
        const val UNCHOKE = 1
        const val INTERESTED = 2
        const val HAVE = 4
        const val BITFIELD = 5
        const val REQUEST = 6
        const val PIECE = 7
        const val PORT = 9
        const val HAVE_ALL = 14
        const val HAVE_NONE = 15
        const val REJECT_REQUEST = 16
        const val ALLOWED_FAST = 17
        const val EXTENDED = 20

        /** MSE 의 768비트 소수 P 와 생성원 G. */
        val MSE_P = BigInteger(
            "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA63B139B22514A08798E3404DDEF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245E485B576625E7EC6F44C42E9A63A36210000000000090563",
            16,
        )
        val MSE_G: BigInteger = BigInteger.valueOf(2)

        fun ascii(s: String): ByteArray = s.toByteArray(Charsets.US_ASCII)

        /** 96바이트 big-endian(앞을 0 으로 채운다). */
        fun to96(v: BigInteger): ByteArray {
            val b = v.toByteArray()
            return when {
                b.size == 96 -> b
                b.size > 96 -> b.copyOfRange(b.size - 96, b.size)
                else -> ByteArray(96 - b.size) + b
            }
        }

        fun xor(a: ByteArray, b: ByteArray): ByteArray = ByteArray(a.size) { (a[it].toInt() xor b[it].toInt()).toByte() }

        fun regionEquals(buf: ByteArray, at: Int, needle: ByteArray): Boolean {
            for (i in needle.indices) if (buf[at + i] != needle[i]) return false
            return true
        }

        fun u16(b: ByteArray, at: Int): Int = ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)

        fun ints(vararg v: Int): ByteArray = ByteBuffer.allocate(4 * v.size).also { bb -> v.forEach { bb.putInt(it) } }.array()

        fun setBit(bits: ByteArray, i: Int) {
            bits[i / 8] = (bits[i / 8].toInt() or (0x80 ushr (i % 8))).toByte()
        }
    }
}
