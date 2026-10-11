package io.github.donggi.iroirobrowser.torrent.tracker

import io.github.donggi.iroirobrowser.torrent.util.flipBuffer
import io.github.donggi.iroirobrowser.torrent.util.clearBuffer
import io.github.donggi.iroirobrowser.torrent.net.AddressFilter
import io.github.donggi.iroirobrowser.torrent.net.Purpose
import io.github.donggi.iroirobrowser.torrent.net.Transport
import io.github.donggi.iroirobrowser.torrent.session.EventLoop
import io.github.donggi.iroirobrowser.torrent.session.IoHandler
import io.github.donggi.iroirobrowser.torrent.util.readIntBE
import io.github.donggi.iroirobrowser.torrent.util.readLongBE
import io.github.donggi.iroirobrowser.torrent.util.writeIntBE
import io.github.donggi.iroirobrowser.torrent.util.writeLongBE
import io.github.donggi.iroirobrowser.torrent.util.writeShortBE
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.SelectionKey
import java.util.Random

/**
 * UDP 트래커(BEP 15) — 엔진 하나에 채널 하나, 이벤트 루프에서 돈다. connect(마법수 0x41727101980) → connection id(1분) → announce.
 * 답이 없으면 15·30·60초 뒤 다시 보내고 그래도 없으면 실패. 답은 **보낸 주소에서 온 것**이고 transaction id 가 맞을 때만 받는다(위조 방지).
 * 보내기 전에 주소를 [filter] 로 거른다(이름은 일꾼 스레드가 미리 풀어 거른 주소로 온다).
 */
internal class UdpTrackerClient(
    private val loop: EventLoop,
    private val transport: Transport,
    private val filter: AddressFilter,
    private val random: Random,
    private val log: (String) -> Unit,
) : IoHandler {

    fun interface Callback {
        /** [response] 와 [error] 중 하나만 있다. 이벤트 루프 스레드에서 불린다. */
        fun done(response: AnnounceResponse?, error: String?)
    }

    private inner class Pending(val target: InetSocketAddress, val request: AnnounceRequest, val callback: Callback) {
        var announcing = false
        var attempt = 0
        var tid = 0
        var timer: EventLoop.Timer? = null
        var finished = false
    }

    private var channel: DatagramChannel? = null
    private val pending = HashMap<Int, Pending>()
    private val connectionIds = HashMap<InetSocketAddress, Pair<Long, Long>>()
    private val rx = ByteBuffer.allocate(4096)

    /** 진행 중인 것을 취소할 손잡이. */
    fun interface Handle {
        fun cancel()
    }

    fun announce(target: InetSocketAddress, request: AnnounceRequest, callback: Callback): Handle {
        val p = Pending(target, request, callback)
        if (!filter.allows(target, Purpose.TRACKER)) {
            loop.post { callback.done(null, "blocked address") }
            return Handle { }
        }
        val ch = try {
            ensureChannel()
        } catch (e: Exception) {
            loop.post { callback.done(null, "udp: ${e.message}") }
            return Handle { }
        }
        val conn = connectionIds[target]
        if (conn != null && conn.second > loop.now()) {
            p.announcing = true
            send(ch, p)
        } else {
            send(ch, p)
        }
        return Handle {
            if (!p.finished) {
                p.finished = true
                p.timer?.cancel()
                pending.remove(p.tid)
            }
        }
    }

    private fun ensureChannel(): DatagramChannel {
        channel?.let { if (it.isOpen) return it }
        val ch = transport.openUdp()
        loop.register(ch, SelectionKey.OP_READ, this)
        channel = ch
        return ch
    }

    private fun send(ch: DatagramChannel, p: Pending) {
        pending.remove(p.tid)
        var tid: Int
        do {
            tid = random.nextInt()
        } while (pending.containsKey(tid))
        p.tid = tid
        pending[tid] = p
        val packet: ByteArray
        if (!p.announcing) {
            packet = ByteArray(16)
            packet.writeLongBE(0, PROTOCOL_ID)
            packet.writeIntBE(8, ACTION_CONNECT)
            packet.writeIntBE(12, tid)
        } else {
            val connId = connectionIds[p.target]?.first ?: 0L
            val r = p.request
            packet = ByteArray(98)
            packet.writeLongBE(0, connId)
            packet.writeIntBE(8, ACTION_ANNOUNCE)
            packet.writeIntBE(12, tid)
            System.arraycopy(r.infoHash, 0, packet, 16, 20)
            System.arraycopy(r.peerId, 0, packet, 36, 20)
            packet.writeLongBE(56, r.downloaded)
            packet.writeLongBE(64, r.left)
            packet.writeLongBE(72, 0L) // uploaded — 늘 0(올리기 0)
            packet.writeIntBE(80, r.event.udp)
            packet.writeIntBE(84, 0) // IP — 0 = 보낸 주소
            packet.writeIntBE(88, r.key)
            packet.writeIntBE(92, r.numWant)
            packet.writeShortBE(96, r.port)
        }
        try {
            transport.sendUdp(ch, ByteBuffer.wrap(packet), p.target)
        } catch (e: Exception) {
            finish(p, null, "send: ${e.message}")
            return
        }
        p.timer?.cancel()
        p.timer = loop.schedule(TIMEOUT_MS shl p.attempt) { onTimeout(p) }
    }

    private fun onTimeout(p: Pending) {
        if (p.finished) return
        if (++p.attempt > MAX_RETRIES) {
            finish(p, null, "timeout")
            return
        }
        val ch = channel ?: return finish(p, null, "closed")
        send(ch, p)
    }

    private fun finish(p: Pending, response: AnnounceResponse?, error: String?) {
        if (p.finished) return
        p.finished = true
        p.timer?.cancel()
        pending.remove(p.tid)
        p.callback.done(response, error)
    }

    override fun onReady(key: SelectionKey) {
        val ch = channel ?: return
        while (true) {
            rx.clearBuffer()
            val from = ch.receive(rx) as? InetSocketAddress ?: return
            rx.flipBuffer()
            val n = rx.remaining()
            if (n < 8) continue
            val data = ByteArray(n)
            rx.get(data)
            handle(normalize(from), data)
        }
    }

    private fun handle(from: InetSocketAddress, d: ByteArray) {
        val action = d.readIntBE(0)
        val tid = d.readIntBE(4)
        val p = pending[tid] ?: return
        if (p.target != from) return
        when (action) {
            ACTION_CONNECT -> {
                if (p.announcing || d.size < 16) return
                connectionIds[p.target] = d.readLongBE(8) to loop.now() + CONNECTION_ID_TTL_MS
                p.announcing = true
                p.attempt = 0
                val ch = channel ?: return finish(p, null, "closed")
                send(ch, p)
            }
            ACTION_ANNOUNCE -> {
                if (!p.announcing || d.size < 20) return
                val interval = d.readIntBE(8).toLong()
                val leechers = d.readIntBE(12).toLong() and 0xFFFFFFFFL
                val seeders = d.readIntBE(16).toLong() and 0xFFFFFFFFL
                val v6 = p.target.address is Inet6Address
                val peers = HttpTracker.compact(d.copyOfRange(20, d.size), v6)
                finish(p, AnnounceResponse(peers, Intervals.clamp(interval, null), seeders, leechers, null, null), null)
            }
            ACTION_ERROR -> {
                val msg = String(d, 8, d.size - 8, Charsets.UTF_8).take(200)
                // connection id 가 낡았으면 다음에 다시 connect 한다.
                connectionIds.remove(p.target)
                finish(p, null, "tracker: $msg")
            }
        }
    }

    /** 이중 스택 소켓이 돌려준 IPv4-mapped 주소를 IPv4 로. */
    private fun normalize(a: InetSocketAddress): InetSocketAddress {
        val addr = a.address
        if (addr is Inet6Address) {
            val b = addr.address
            val mapped = (0 until 10).all { b[it].toInt() == 0 } && b[10].toInt() == -1 && b[11].toInt() == -1
            if (mapped) return InetSocketAddress(InetAddress.getByAddress(b.copyOfRange(12, 16)) as Inet4Address, a.port)
        }
        return a
    }

    override fun onFailure(e: Exception) {
        log("udp tracker channel failed: $e")
        runCatching { channel?.close() }
        channel = null
        for (p in pending.values.toList()) finish(p, null, "channel failed")
    }

    fun close() {
        runCatching { channel?.close() }
        channel = null
        pending.clear()
    }

    companion object {
        const val PROTOCOL_ID = 0x41727101980L
        const val ACTION_CONNECT = 0
        const val ACTION_ANNOUNCE = 1
        const val ACTION_ERROR = 3
        const val TIMEOUT_MS = 15_000L
        const val MAX_RETRIES = 2
        const val CONNECTION_ID_TTL_MS = 60_000L
    }
}
