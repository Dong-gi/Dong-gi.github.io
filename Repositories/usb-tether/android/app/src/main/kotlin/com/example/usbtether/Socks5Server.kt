package com.example.usbtether

import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.net.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * SOCKS5 server (RFC 1928) listening on 127.0.0.1:port.
 * PC connects via `adb forward tcp:1080 tcp:1080` and uses tun2proxy as its SOCKS5 client.
 *
 * ADB only forwards TCP, so only CONNECT is supported. UDP ASSOCIATE requests are
 * rejected with REP_CMD_UNSUPPORTED (tun2proxy falls back to DNS-over-TCP via --dns over-tcp).
 *
 * The carrier sees only normal phone-originated sockets: Android's OS TCP stack
 * handles every outbound connection, so tethering looks identical to phone activity.
 */
class Socks5Server(
    private val port: Int,
    private val onTcpCount: (Int) -> Unit = {},
    private val onBytesIn: (Long) -> Unit = {},
    private val onBytesOut: (Long) -> Unit = {},
) {
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newCachedThreadPool()
    private val tcpSessions = AtomicInteger(0)

    fun start() {
        if (!running.compareAndSet(false, true)) return
        thread(name = "socks5-acceptor", isDaemon = true) { acceptLoop() }
    }

    private fun acceptLoop() {
        try {
            serverSocket = ServerSocket(port, 50, InetAddress.getByName("127.0.0.1")).apply {
                reuseAddress = true
            }
            Log.i(TAG, "SOCKS5 listening on 127.0.0.1:$port")
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
                CMD_CONNECT -> handleConnect(client, host, dstPort)
                else        -> sendReply(out, REP_CMD_UNSUPPORTED)
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

            onTcpCount(tcpSessions.incrementAndGet())
            relay(client, remote)
            onTcpCount(tcpSessions.decrementAndGet())
        } finally {
            try { remote.close() } catch (_: Exception) {}
        }
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
    }

    companion object {
        private const val TAG = "Socks5Server"
        private const val CONNECT_TIMEOUT_MS = 5000

        private const val ATYP_IPV4   = 1
        private const val ATYP_DOMAIN = 3
        private const val ATYP_IPV6   = 4

        private const val CMD_CONNECT = 1

        private const val REP_CONN_REFUSED    = 5
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
