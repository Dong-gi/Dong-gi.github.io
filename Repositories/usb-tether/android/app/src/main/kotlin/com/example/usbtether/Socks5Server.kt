package com.example.usbtether

import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.net.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * SOCKS5 server (RFC 1928) listening on 0.0.0.0. Tries [BASE_PORT], [BASE_PORT]+1,
 * ..., [BASE_PORT]+9 in order (up to 10 attempts) and binds the first port that
 * is free. Inspect [actualPort] after [start] returns to discover the chosen port.
 * Reachable both via USB (PC → `adb forward tcp:1080 tcp:1080` → 127.0.0.1) and via
 * the Wi-Fi P2P group when WifiHotspot is active.
 *
 * Supports CONNECT (TCP) and UDP ASSOCIATE (RFC 1928 §7). For UDP ASSOCIATE the
 * server binds a DatagramSocket on an ephemeral port and replies with BND.ADDR =
 * client.localAddress (192.168.49.1 on the hotspot interface, 127.0.0.1 over USB).
 * tun2proxy sends SOCKS5-wrapped UDP datagrams to that port; the server strips the
 * header, forwards to the real destination, and wraps responses back.
 *
 * UDP ASSOCIATE over USB is limited by ADB, which only forwards TCP — the UDP relay
 * socket on the phone is unreachable from the PC in that mode.
 *
 * The carrier sees only normal phone-originated sockets: Android's OS TCP/UDP stack
 * handles every outbound connection, so tethering looks identical to phone activity.
 */
class Socks5Server(
    private val onBytesIn: (Long) -> Unit = {},
    private val onBytesOut: (Long) -> Unit = {},
) {
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newCachedThreadPool()

    /** Port actually bound, or -1 if the server isn't running. */
    @Volatile var actualPort: Int = -1
        private set

    /** Bind synchronously so the caller sees the chosen port immediately, then start accepting. */
    fun start(): Int {
        if (!running.compareAndSet(false, true)) return actualPort
        val sock = bindWithFallback() ?: run {
            running.set(false)
            return -1
        }
        serverSocket = sock
        actualPort = sock.localPort
        Log.i(TAG, "SOCKS5 listening on 0.0.0.0:$actualPort")
        thread(name = "socks5-acceptor", isDaemon = true) { acceptLoop() }
        return actualPort
    }

    private fun bindWithFallback(): ServerSocket? {
        for (offset in 0 until PORT_FALLBACK_ATTEMPTS) {
            val port = BASE_PORT + offset
            try {
                return ServerSocket(port, 50, InetAddress.getByName("0.0.0.0")).apply {
                    reuseAddress = true
                }
            } catch (e: Exception) {
                Log.w(TAG, "bind on $port failed: ${e.message}")
            }
        }
        Log.e(TAG, "could not bind on $BASE_PORT..${BASE_PORT + PORT_FALLBACK_ATTEMPTS - 1}")
        return null
    }

    private fun acceptLoop() {
        try {
            while (running.get()) {
                val client = try {
                    serverSocket!!.accept()
                } catch (e: Exception) {
                    if (running.get()) Log.e(TAG, "accept() failed", e)
                    break
                }
                executor.submit { handleClient(client) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "acceptLoop crashed", e)
        }
    }

    private fun handleClient(client: Socket) {
        try {
            client.tcpNoDelay = true
            client.soTimeout = 10_000
            val inp = client.getInputStream()
            val out = client.getOutputStream()

            // Auth negotiation: pick NO_AUTH regardless of what client offers
            if (inp.read() != 5) return
            repeat(inp.read()) { inp.read() }
            out.write(byteArrayOf(5, 0))

            // Request header
            if (inp.read() != 5) return
            val cmd = inp.read()
            inp.read()  // RSV

            val host = when (inp.read()) {
                ATYP_IPV4   -> InetAddress.getByAddress(inp.readN(4)).hostAddress!!
                ATYP_DOMAIN -> String(inp.readN(inp.read()), Charsets.US_ASCII)
                ATYP_IPV6   -> InetAddress.getByAddress(inp.readN(16)).hostAddress!!
                else -> { sendReply(out, REP_ATYP_UNSUPPORTED); return }
            }
            val dstPort = (inp.read() shl 8) or inp.read()

            when (cmd) {
                CMD_CONNECT       -> handleConnect(client, host, dstPort)
                CMD_UDP_ASSOCIATE -> handleUdpAssociate(client)
                else              -> sendReply(out, REP_CMD_UNSUPPORTED)
            }
        } catch (e: Exception) {
            Log.w(TAG, "client error: ${e.message}")
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun handleConnect(client: Socket, host: String, port: Int) {
        val remote = try {
            Socket().apply {
                tcpNoDelay = true
                connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            }
        } catch (e: Exception) {
            Log.w(TAG, "connect $host:$port failed: ${e.message}")
            sendReply(client.getOutputStream(), REP_CONN_REFUSED)
            return
        }
        try {
            val localAddr = (remote.localAddress as? Inet4Address)?.address ?: ByteArray(4)
            val lp = remote.localPort
            client.getOutputStream().write(
                byteArrayOf(5, 0, 0, ATYP_IPV4.toByte(),
                    localAddr[0], localAddr[1], localAddr[2], localAddr[3],
                    (lp shr 8).toByte(), (lp and 0xFF).toByte())
            )
            client.soTimeout = 0
            relay(client, remote)
        } finally {
            try { remote.close() } catch (_: Exception) {}
        }
    }

    private fun handleUdpAssociate(client: Socket) {
        val out = client.getOutputStream()
        val udpSocket = try {
            DatagramSocket(0, InetAddress.getByName("0.0.0.0")).apply { soTimeout = 2000 }
        } catch (e: Exception) {
            Log.e(TAG, "UDP socket creation failed: ${e.message}")
            sendReply(out, REP_GENERAL_FAILURE)
            return
        }
        try {
            // BND.ADDR = the interface this TCP connection arrived on.
            // For Wi-Fi hotspot clients this is 192.168.49.1; for USB it is 127.0.0.1.
            val bindAddr = (client.localAddress as? Inet4Address)?.address ?: byteArrayOf(0, 0, 0, 0)
            val udpPort = udpSocket.localPort
            out.write(
                byteArrayOf(5, 0, 0, ATYP_IPV4.toByte(),
                    bindAddr[0], bindAddr[1], bindAddr[2], bindAddr[3],
                    (udpPort shr 8).toByte(), (udpPort and 0xFF).toByte())
            )
            Log.i(TAG, "UDP ASSOCIATE: relay on port $udpPort")
            client.soTimeout = 0

            var clientUdpAddr: InetAddress? = null
            var clientUdpPort: Int = -1

            thread(isDaemon = true, name = "udp-relay-$udpPort") {
                val buf = ByteArray(UDP_BUF_SIZE)
                val pkt = DatagramPacket(buf, buf.size)
                while (!udpSocket.isClosed && running.get()) {
                    try {
                        pkt.setData(buf)
                        udpSocket.receive(pkt)
                    } catch (_: SocketTimeoutException) {
                        continue
                    } catch (e: Exception) {
                        if (!udpSocket.isClosed) Log.w(TAG, "UDP recv: ${e.message}")
                        break
                    }

                    val ca = clientUdpAddr
                    val cp = clientUdpPort
                    if (ca == null || (pkt.address == ca && pkt.port == cp)) {
                        // Packet from tun2proxy: parse SOCKS5 UDP header and forward to remote
                        if (ca == null) {
                            clientUdpAddr = pkt.address
                            clientUdpPort = pkt.port
                        }
                        val (dstAddr, dstPort, payload) = parseUdpHeader(pkt) ?: continue
                        onBytesIn(payload.size.toLong())
                        try {
                            udpSocket.send(DatagramPacket(payload, payload.size, dstAddr, dstPort))
                        } catch (e: Exception) {
                            Log.w(TAG, "UDP fwd to $dstAddr:$dstPort: ${e.message}")
                        }
                    } else {
                        // Packet from remote: wrap with SOCKS5 UDP header and send back to tun2proxy
                        val target = clientUdpAddr ?: continue
                        val targetPort = clientUdpPort.takeIf { it >= 0 } ?: continue
                        val wrapped = buildUdpHeader(pkt.address, pkt.port, pkt.data, pkt.offset, pkt.length)
                        onBytesOut(pkt.length.toLong())
                        try {
                            udpSocket.send(DatagramPacket(wrapped, wrapped.size, target, targetPort))
                        } catch (e: Exception) {
                            Log.w(TAG, "UDP reply to $target:$targetPort: ${e.message}")
                        }
                    }
                }
            }

            // Block until the TCP control connection closes; closing udpSocket stops the relay thread
            try {
                while (client.getInputStream().read() != -1) { /* drain keepalive bytes */ }
            } catch (_: Exception) {}
            Log.i(TAG, "UDP ASSOCIATE ended on port $udpPort")
        } finally {
            udpSocket.close()
        }
    }

    private fun parseUdpHeader(pkt: DatagramPacket): Triple<InetAddress, Int, ByteArray>? {
        val data = pkt.data
        val off = pkt.offset
        val end = off + pkt.length
        if (pkt.length < 10) return null
        // data[off], data[off+1] = RSV (ignored)
        if (data[off + 2].toInt() and 0xFF != 0) return null  // drop fragments (FRAG != 0)
        val atyp = data[off + 3].toInt() and 0xFF
        var pos = off + 4
        val dstAddr: InetAddress
        when (atyp) {
            ATYP_IPV4 -> {
                if (pos + 4 > end) return null
                dstAddr = InetAddress.getByAddress(data.copyOfRange(pos, pos + 4))
                pos += 4
            }
            ATYP_DOMAIN -> {
                if (pos >= end) return null
                val nameLen = data[pos++].toInt() and 0xFF
                if (pos + nameLen > end) return null
                dstAddr = InetAddress.getByName(String(data, pos, nameLen, Charsets.US_ASCII))
                pos += nameLen
            }
            ATYP_IPV6 -> {
                if (pos + 16 > end) return null
                dstAddr = InetAddress.getByAddress(data.copyOfRange(pos, pos + 16))
                pos += 16
            }
            else -> return null
        }
        if (pos + 2 > end) return null
        val dstPort = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
        pos += 2
        return Triple(dstAddr, dstPort, data.copyOfRange(pos, end))
    }

    private fun buildUdpHeader(
        srcAddr: InetAddress, srcPort: Int,
        data: ByteArray, offset: Int, length: Int,
    ): ByteArray {
        val addrBytes = srcAddr.address
        val atyp = if (addrBytes.size == 4) ATYP_IPV4 else ATYP_IPV6
        val hdrSize = 4 + addrBytes.size + 2  // RSV(2) + FRAG(1) + ATYP(1) + addr + PORT(2)
        val result = ByteArray(hdrSize + length)
        result[0] = 0; result[1] = 0  // RSV
        result[2] = 0                  // FRAG
        result[3] = atyp.toByte()
        addrBytes.copyInto(result, 4)
        var p = 4 + addrBytes.size
        result[p++] = (srcPort shr 8).toByte()
        result[p++] = (srcPort and 0xFF).toByte()
        data.copyInto(result, p, offset, offset + length)
        return result
    }

    private fun relay(client: Socket, remote: Socket) {
        // Two pipes run concurrently; each shuts down the opposite write-side on EOF
        // so the peer receives a clean FIN rather than a forceful RST.
        val t = thread(isDaemon = true) {
            pipe(remote.getInputStream(), client.getOutputStream()) { onBytesOut(it) }
            try { client.shutdownOutput() } catch (_: Exception) {}
        }
        pipe(client.getInputStream(), remote.getOutputStream()) { onBytesIn(it) }
        try { remote.shutdownOutput() } catch (_: Exception) {}
        t.join(5_000)
    }

    private fun pipe(src: InputStream, dst: OutputStream, onBytes: (Long) -> Unit) {
        try {
            val buf = ByteArray(8192)
            var n: Int
            while (src.read(buf).also { n = it } != -1) {
                dst.write(buf, 0, n)
                onBytes(n.toLong())
            }
        } catch (_: Exception) {}
    }

    private fun sendReply(out: OutputStream, rep: Int) {
        out.write(byteArrayOf(5, rep.toByte(), 0, 1, 0, 0, 0, 0, 0, 0))
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        try { serverSocket?.close() } catch (_: Exception) {}
        executor.shutdownNow()
        actualPort = -1
    }

    companion object {
        private const val TAG = "Socks5Server"
        private const val CONNECT_TIMEOUT_MS = 5000
        private const val PORT_FALLBACK_ATTEMPTS = 10
        const val BASE_PORT = 1080

        private const val ATYP_IPV4   = 1
        private const val ATYP_DOMAIN = 3
        private const val ATYP_IPV6   = 4

        private const val UDP_BUF_SIZE = 65535

        private const val CMD_CONNECT       = 1
        private const val CMD_UDP_ASSOCIATE = 3

        private const val REP_GENERAL_FAILURE  = 1
        private const val REP_CONN_REFUSED     = 5
        private const val REP_CMD_UNSUPPORTED  = 7
        private const val REP_ATYP_UNSUPPORTED = 8
    }
}

private fun InputStream.readN(n: Int): ByteArray {
    val buf = ByteArray(n)
    var off = 0
    while (off < n) {
        val read = read(buf, off, n - off)
        if (read == -1) throw java.io.EOFException()
        off += read
    }
    return buf
}
