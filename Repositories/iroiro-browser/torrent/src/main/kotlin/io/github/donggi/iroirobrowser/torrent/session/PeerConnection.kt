package io.github.donggi.iroirobrowser.torrent.session

import io.github.donggi.iroirobrowser.torrent.util.moveTo
import io.github.donggi.iroirobrowser.torrent.util.flipBuffer
import io.github.donggi.iroirobrowser.torrent.util.clearBuffer
import io.github.donggi.iroirobrowser.torrent.mse.MseInitiator
import io.github.donggi.iroirobrowser.torrent.mse.Rc4
import io.github.donggi.iroirobrowser.torrent.piece.Bitfield
import io.github.donggi.iroirobrowser.torrent.piece.Requester
import io.github.donggi.iroirobrowser.torrent.wire.Inbound
import io.github.donggi.iroirobrowser.torrent.wire.Outbound
import io.github.donggi.iroirobrowser.torrent.wire.Wire
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.SocketChannel
import java.util.ArrayDeque

/**
 * 나가는 피어 연결 하나 — 비차단 상태 기계(연결 → [MSE] → 핸드셰이크 → 메시지). 이벤트 루프에서만 쓴다.
 *
 * 남이 보내는 바이트의 상한:
 * - 메시지 길이는 **길이 접두를 읽자마자** id 별 범위([Wire.lengthRange])와 견주고, 넘으면 할당하지 않고 끊는다(`0xFFFFFFFF` 등).
 *   piece 는 16 KiB 까지이고 **우리가 청한 그 블록의 길이와 같아야** 한다.
 * - bitfield 는 첫 메시지로 한 번만(확장 핸드셰이크 뒤여도 된다), 길이가 맞고 남는 비트가 꺼져 있어야 한다. have 는 범위 안이어야 한다.
 * - 시간 상한은 세션이 1초마다 [checkTimeouts] 로 본다(가상 시계).
 *
 * 우리가 보내는 것은 [Outbound] 뿐이다 — 들어온 request 는 Fast 확장이면 reject 로 답하고 아니면 무시한다.
 */
internal class PeerConnection(
    private val host: Host,
    val address: InetSocketAddress,
    /** MSE 를 먼저 시도하는 연결인가. */
    val encrypted: Boolean,
    private val channel: SocketChannel,
    private val now: () -> Long,
) : IoHandler, Requester {

    /** 연결이 세션에 알리는 것. */
    interface Host {
        val infoHash: ByteArray
        val ourPeerId: ByteArray

        /** 조각 수 — 메타데이터 전이면 0. */
        val pieceCount: Int
        fun newMse(handshake: ByteArray): MseInitiator?
        fun onHandshake(c: PeerConnection)
        fun onMessage(c: PeerConnection, m: Inbound)
        fun onClosed(c: PeerConnection, reason: String, violation: Boolean)
        fun canRead(): Boolean
    }

    enum class Stage { CONNECTING, MSE, HANDSHAKE, ACTIVE, CLOSED }

    var stage = Stage.CONNECTING
        private set
    lateinit var key: SelectionKey

    val startedAt = now()
    var connectedAt = -1L
        private set
    var handshakeAt = -1L
        private set
    var lastReceiveAt = now()
        private set
    var lastSendAt = now()
        private set

    // 핸드셰이크에서
    var peerId: ByteArray? = null
        private set
    var fast = false
        private set
    var extensions = false
        private set
    var usedMse = false
        private set

    // 상대의 상태 — 세션이 쓴다
    var peerChoking = true
    var peerInterested = false
    var amInterested = false
    var bitfield: Bitfield? = null
    var isSeed = false

    /** 메타데이터 전에 받은 bitfield·have(검사는 메타데이터를 받은 뒤). */
    var pendingBitfield: ByteArray? = null
    val pendingHaves = HashSet<Int>()
    var pendingHaveAll = false

    /**
     * 조각 정보(bitfield·Have All·Have None·have) 가운데 처음 온 것. bitfield·Have All·Have None 은 그보다 먼저여야 한다 — 두 번째
     * bitfield 나 have 뒤의 bitfield 는 규약 위반이다. 확장 핸드셰이크·port·allowed_fast·choke 등은 앞에 와도 된다(실제 클라이언트가
     * 그렇게 보낸다 — 실제 인터넷 시험에서 이 규칙이 정상 피어를 끊은 것을 보고 좁혔다).
     */
    var availabilitySeen: String? = null

    /** 상대가 가진 조각 가운데 우리가 원하는 수. */
    var wantedCount = 0
    val allowedFast = HashSet<Int>()

    /** 우리가 청한 블록 — 열쇠 [blockKey], 값은 (길이, 청한 때). */
    val requested = LinkedHashMap<Long, LongArray>()

    /** 취소했거나 choke 로 거둔 요청 — 그래도 늦게 오면 받는다. */
    val canceled = HashMap<Long, Int>()
    var snubbedUntil = 0L
    var lastBlockAt = -1L
    var receivedRequests = 0
    var hashFails = 0
    var downloaded = 0L
    val rate = RateMeter()
    var uninterestingSince = -1L
    var peerUtMetadataId = 0
    var peerMetadataSize: Int? = null
    var peerRequestQueue: Int? = null
    var client: String? = null

    private var mse: MseInitiator? = null
    private var rc4In: Rc4? = null
    private var rc4Out: Rc4? = null
    private var inBuf: ByteBuffer = ByteBuffer.allocate(INITIAL_BUFFER)
    private var handshakeDone = false
    private val outQueue = ArrayDeque<ByteBuffer>()
    private var outBytes = 0L
    private var readPaused = false

    val isActive: Boolean get() = stage == Stage.ACTIVE

    fun register(loop: EventLoop) {
        key = loop.register(channel, SelectionKey.OP_CONNECT, this)
    }

    override fun onReady(key: SelectionKey) {
        if (stage == Stage.CLOSED) return
        if (key.isConnectable) onConnectable()
        if (stage == Stage.CLOSED) return
        if (key.isValid && key.isReadable) onReadable()
        if (stage == Stage.CLOSED) return
        if (key.isValid && key.isWritable) flush()
    }

    override fun onFailure(e: Exception) {
        close(if (e is IOException) "io: ${e.message}" else "error: $e", violation = false)
    }

    private fun onConnectable() {
        try {
            if (!channel.finishConnect()) return
        } catch (e: IOException) {
            close("connect: ${e.message}", violation = false)
            return
        }
        connectedAt = now()
        lastReceiveAt = connectedAt
        key.interestOps(SelectionKey.OP_READ)
        val hs = Wire.handshake(host.infoHash, host.ourPeerId)
        val m = if (encrypted) host.newMse(hs) else null
        if (m != null) {
            mse = m
            usedMse = true
            stage = Stage.MSE
            enqueueRaw(m.message1())
        } else {
            stage = Stage.HANDSHAKE
            enqueueRaw(hs)
        }
        flush()
    }

    private fun onReadable() {
        var total = 0
        while (stage != Stage.CLOSED && total < MAX_READ_PER_EVENT) {
            if (!host.canRead()) {
                pauseReading()
                return
            }
            if (!inBuf.hasRemaining()) {
                // [processFrames] 가 검사를 지난 큰 메시지만큼 이미 키운다 — 여기 오면 무언가 어긋났다.
                close("buffer full", violation = true)
                return
            }
            val start = inBuf.position()
            val n = try {
                channel.read(inBuf)
            } catch (e: IOException) {
                close("read: ${e.message}", violation = false)
                return
            }
            if (n < 0) {
                close("closed by peer", violation = false)
                return
            }
            if (n == 0) return
            total += n
            lastReceiveAt = now()
            rc4In?.process(inBuf, start, start + n)
            if (stage == Stage.MSE) {
                val bytes = ByteArray(n)
                inBuf.moveTo(start)
                inBuf.get(bytes)
                inBuf.clearBuffer()
                onMseBytes(bytes)
            } else {
                processFrames()
            }
        }
    }

    private fun onMseBytes(bytes: ByteArray) {
        val m = mse ?: return
        when (val r = m.receive(bytes)) {
            is MseInitiator.Result.NeedMore -> m.takeOutgoing()?.let { enqueueRaw(it); flush() }
            is MseInitiator.Result.Failed -> close("mse: ${r.reason}", violation = false)
            is MseInitiator.Result.Done -> {
                m.takeOutgoing()?.let { enqueueRaw(it) }
                rc4In = r.decrypt
                rc4Out = r.encrypt
                mse = null
                stage = Stage.HANDSHAKE
                // 핸드셰이크는 IA 에 실어 보냈다.
                inBuf.clearBuffer()
                ensureCapacity(r.leftover.size)
                inBuf.put(r.leftover)
                flush()
                processFrames()
            }
        }
    }

    private fun ensureCapacity(extra: Int) {
        if (inBuf.remaining() >= extra) return
        val bigger = ByteBuffer.allocate(inBuf.position() + extra + INITIAL_BUFFER)
        inBuf.flipBuffer()
        bigger.put(inBuf)
        inBuf = bigger
    }

    /**
     * 버퍼(쓰기 꼴)에 쌓인 바이트에서 메시지를 꺼낸다. 끝나지 않은 메시지가 버퍼보다 크면 — 길이 검사를 지난 것만 — 그만큼 키운다.
     */
    private fun processFrames() {
        inBuf.flipBuffer()
        var needed = 0
        while (stage != Stage.CLOSED) {
            if (!handshakeDone) {
                if (inBuf.remaining() < Wire.HANDSHAKE_LENGTH) break
                val hs = ByteArray(Wire.HANDSHAKE_LENGTH)
                inBuf.get(hs)
                if (!onHandshakeBytes(hs)) return
                continue
            }
            if (inBuf.remaining() < 4) break
            val p = inBuf.position()
            val len = inBuf.getInt(p).toLong() and 0xFFFFFFFFL
            if (len == 0L) {
                inBuf.moveTo(p + 4)
                continue
            }
            // id 바이트를 기다리지 않는다 — 어느 메시지보다도 긴 길이면 곧바로 끊는다(길이 접두 0xFFFFFFFF 만 보내고 멈추는 피어).
            if (len > Wire.maxFrameLength(host.pieceCount)) {
                close("bad length $len (longer than any message)", violation = true)
                return
            }
            if (inBuf.remaining() < 5) break
            val id = inBuf.get(p + 4).toInt() and 0xFF
            val range = Wire.lengthRange(id, host.pieceCount)
            if (len < range.first || len > range.last) {
                close("bad length $len for id $id", violation = true)
                return
            }
            if (inBuf.remaining() < 4 + len) {
                needed = (4 + len).toInt()
                break
            }
            val frame = ByteArray(len.toInt())
            inBuf.moveTo(p + 4)
            inBuf.get(frame)
            host.onMessage(this, Inbound.parse(frame))
        }
        if (stage == Stage.CLOSED) return
        inBuf.compact()
        if (needed > inBuf.capacity()) {
            val bigger = ByteBuffer.allocate(needed + 1024)
            inBuf.flipBuffer()
            bigger.put(inBuf)
            inBuf = bigger
        } else if (inBuf.position() == 0 && inBuf.capacity() > INITIAL_BUFFER * 2) {
            // 큰 메시지를 다 읽었으면 줄인다(연결 60개가 큰 버퍼를 붙들지 않게).
            inBuf = ByteBuffer.allocate(INITIAL_BUFFER)
        }
    }

    private fun onHandshakeBytes(b: ByteArray): Boolean {
        val hs = Wire.parseHandshake(b)
        if (hs == null) {
            close(if (usedMse) "bad handshake after mse" else "bad handshake", violation = !encrypted)
            return false
        }
        if (!hs.infoHash.contentEquals(host.infoHash)) {
            close("wrong info-hash", violation = true)
            return false
        }
        if (hs.peerId.contentEquals(host.ourPeerId)) {
            close("connected to self", violation = true)
            return false
        }
        handshakeDone = true
        peerId = hs.peerId
        fast = hs.supportsFast
        extensions = hs.supportsExtensions
        handshakeAt = now()
        stage = Stage.ACTIVE
        host.onHandshake(this)
        return stage != Stage.CLOSED
    }

    // ── 쓰기 ────────────────────────────────────────────────────────────────

    fun send(m: Outbound) {
        if (stage == Stage.CLOSED) return
        val bytes = m.encode()
        rc4Out?.process(bytes)
        enqueueRaw(bytes)
    }

    private fun enqueueRaw(bytes: ByteArray) {
        outQueue.add(ByteBuffer.wrap(bytes))
        outBytes += bytes.size
        lastSendAt = now()
        if (outBytes > MAX_OUT_BYTES) {
            close("peer not reading", violation = false)
            return
        }
        if (stage != Stage.CONNECTING && key.isValid) key.interestOps(key.interestOps() or SelectionKey.OP_WRITE)
    }

    /** 쌓인 것을 보낼 수 있는 만큼 보낸다. */
    fun flush() {
        if (stage == Stage.CLOSED || stage == Stage.CONNECTING) return
        try {
            while (outQueue.isNotEmpty()) {
                val b = outQueue.peekFirst()
                val n = channel.write(b)
                outBytes -= n
                if (b.hasRemaining()) break
                outQueue.pollFirst()
            }
        } catch (e: IOException) {
            close("write: ${e.message}", violation = false)
            return
        }
        if (!key.isValid) return
        val ops = key.interestOps()
        key.interestOps(if (outQueue.isEmpty()) ops and SelectionKey.OP_WRITE.inv() else ops or SelectionKey.OP_WRITE)
    }

    private fun pauseReading() {
        if (readPaused || !key.isValid) return
        readPaused = true
        key.interestOps(key.interestOps() and SelectionKey.OP_READ.inv())
    }

    /** 디스크가 따라잡았다 — 다시 읽는다. */
    fun resumeReading() {
        if (!readPaused || stage == Stage.CLOSED || !key.isValid) return
        readPaused = false
        key.interestOps(key.interestOps() or SelectionKey.OP_READ)
    }

    // ── 끝 ──────────────────────────────────────────────────────────────────

    /** 시간 상한(1초마다). 끊었으면 참. */
    fun checkTimeouts(t: Long): Boolean {
        when (stage) {
            Stage.CONNECTING -> if (t - startedAt > CONNECT_TIMEOUT_MS) {
                close("connect timeout", violation = false)
                return true
            }
            Stage.MSE, Stage.HANDSHAKE -> if (t - connectedAt > HANDSHAKE_TIMEOUT_MS) {
                close(if (stage == Stage.MSE) "mse timeout" else "handshake timeout", violation = false)
                return true
            }
            Stage.ACTIVE -> {
                if (t - lastReceiveAt > IDLE_TIMEOUT_MS) {
                    close("idle", violation = false)
                    return true
                }
                if (t - lastSendAt > KEEPALIVE_MS) {
                    send(Outbound.KeepAlive)
                    flush()
                }
            }
            Stage.CLOSED -> return true
        }
        return false
    }

    fun close(reason: String, violation: Boolean) {
        if (stage == Stage.CLOSED) return
        val wasStage = stage
        stage = Stage.CLOSED
        runCatching { if (::key.isInitialized) key.cancel() }
        runCatching { channel.close() }
        outQueue.clear()
        host.onClosed(this, if (wasStage == Stage.MSE) "mse-stage: $reason" else reason, violation)
    }

    /** MSE 단계에서 끊겼다(상대가 MSE 를 모르는 것으로 본다 — 평문으로 다시 붙을 때). */
    val failedDuringMse: Boolean get() = usedMse && !handshakeDone

    override fun toString(): String = "peer $address"

    companion object {
        const val INITIAL_BUFFER = 32 * 1024 + 64
        const val MAX_READ_PER_EVENT = 256 * 1024
        const val MAX_OUT_BYTES = 1L shl 20

        const val CONNECT_TIMEOUT_MS = 10_000L
        const val HANDSHAKE_TIMEOUT_MS = 20_000L
        const val IDLE_TIMEOUT_MS = 120_000L
        const val KEEPALIVE_MS = 90_000L

        fun blockKey(piece: Int, begin: Int): Long = (piece.toLong() shl 32) or (begin.toLong() and 0xFFFFFFFFL)
    }
}

/** 최근 몇 초의 받은 바이트로 속도를 낸다(가상 시계). */
internal class RateMeter(private val windowMs: Long = 5_000) {
    private val times = LongArray(64)
    private val bytes = LongArray(64)
    private var head = 0
    private var count = 0

    fun add(now: Long, n: Long) {
        val slot = now / 1000
        val last = (head + count - 1 + times.size) % times.size
        if (count > 0 && times[last] == slot) {
            bytes[last] += n
            return
        }
        val idx = (head + count) % times.size
        if (count == times.size) {
            head = (head + 1) % times.size
            count--
        }
        times[idx] = slot
        bytes[idx] = n
        count++
    }

    /** 바이트/초. */
    fun rate(now: Long): Long {
        val from = (now - windowMs) / 1000
        var sum = 0L
        for (i in 0 until count) {
            val idx = (head + i) % times.size
            if (times[idx] > from) sum += bytes[idx]
        }
        return sum * 1000 / windowMs
    }
}
