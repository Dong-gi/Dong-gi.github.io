package com.example.usbtether

import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.net.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * SOCKS5 서버 (RFC 1928). `0.0.0.0` 에 바인딩한다. [BASE_PORT], [BASE_PORT]+1, …,
 * [BASE_PORT]+9 를 순서대로 최대 10회 시도해 비어 있는 첫 포트를 잡는다.
 * [start] 가 반환된 뒤 [actualPort] 를 읽으면 실제로 채택된 포트를 알 수 있다.
 * WifiHotspot 이 켜져 있을 때 Wi-Fi P2P 그룹의 클라이언트가 사용한다.
 *
 * CONNECT(TCP)와 UDP ASSOCIATE(RFC 1928 §7)를 지원한다. UDP ASSOCIATE 시에는
 * 임의 포트에 DatagramSocket 을 바인딩하고 BND.ADDR 로 client.localAddress
 * (핫스팟 인터페이스 주소 192.168.49.1)를 응답한다.
 * tun2proxy 가 그 포트로 SOCKS5 헤더가 감싸인 UDP 데이터그램을 보내면, 서버가
 * 헤더를 벗겨 실제 목적지로 전달하고 응답을 다시 감싸서 돌려준다.
 *
 * 통신사가 보는 것은 평범한 폰 발신 소켓뿐이다. 모든 외부 연결을 Android OS 의
 * TCP/UDP 스택이 열기 때문에, 테더링 트래픽이 폰 자체 활동과 구별되지 않는다.
 *
 * 리스닝 소켓은 **듀얼스택 와일드카드**다. IPv6 가 있는 JVM 에서 `0.0.0.0` 은 `::` 가
 * 되므로 포트가 모든 인터페이스에서 열린다. 그래서 accept 시점에 [PeerFilter] 로
 * 접근을 통제한다 — Wi-Fi Direct 클라이언트(192.168.49.2–254)만 서비스한다.
 * 전체 근거와 남는 빈틈은 PeerFilter 의 KDoc 을 볼 것.
 */
class Socks5Server(
    private val onBytesIn: (Long) -> Unit = {},
    private val onBytesOut: (Long) -> Unit = {},
) {
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newCachedThreadPool()

    /** 실제로 바인딩된 포트. 서버가 동작 중이 아니면 -1. */
    @Volatile var actualPort: Int = -1
        private set

    /** 호출자가 채택된 포트를 즉시 볼 수 있도록 동기적으로 바인딩한 뒤 accept 를 시작한다. */
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
                // 와일드카드 바인딩은 모든 인터페이스를 덮는다(주소 하나로 좁힐 수 없는
                // 이유는 PeerFilter 참고). Wi-Fi Direct 클라이언트가 아니면
                // 한 바이트도 읽기 전에 거부한다.
                if (!PeerFilter.isAllowed(client.inetAddress)) {
                    Log.w(TAG, "허용되지 않은 네트워크의 클라이언트를 거부했다")
                    try { client.close() } catch (_: Exception) {}
                    continue
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

            // 인증 협상: 클라이언트가 무엇을 제시하든 NO_AUTH 를 선택한다
            if (inp.read() != 5) return
            repeat(inp.read()) { inp.read() }
            out.write(byteArrayOf(5, 0))

            // 요청 헤더
            if (inp.read() != 5) return
            val cmd = inp.read()
            inp.read()  // RSV (예약 필드)

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
        // 이름을 한 번만 해석하고, 통과한 주소 객체로 연결한다. 문자열을 다시
        // InetSocketAddress 에 넘기면 재해석이 일어나 DNS 리바인딩에 노출된다.
        val destination = when (val verdict = DestinationFilter.resolve(host)) {
            is DestinationFilter.Result.Rejected -> {
                Log.w(TAG, "CONNECT 거부: ${verdict.reason}")
                sendReply(client.getOutputStream(), REP_NOT_ALLOWED)
                return
            }
            is DestinationFilter.Result.Allowed -> verdict.address
        }
        val remote = try {
            Socket().apply {
                tcpNoDelay = true
                connect(InetSocketAddress(destination, port), CONNECT_TIMEOUT_MS)
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
            // BND.ADDR = 이 TCP 연결이 도착한 인터페이스의 주소(192.168.49.1).
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
                        // tun2proxy 가 보낸 패킷: SOCKS5 UDP 헤더를 파싱해 원격으로 전달
                        if (ca == null) {
                            clientUdpAddr = pkt.address
                            clientUdpPort = pkt.port
                        }
                        val (dstAddr, dstPort, payload) = parseUdpHeader(pkt) ?: continue
                        // TCP 경로와 같은 정책을 UDP 에도 적용한다. 이게 없으면
                        // UDP 릴레이가 루프백·사설 대역으로 가는 우회로가 된다.
                        if (DestinationFilter.isBlocked(dstAddr)) continue
                        onBytesIn(payload.size.toLong())
                        try {
                            udpSocket.send(DatagramPacket(payload, payload.size, dstAddr, dstPort))
                        } catch (e: Exception) {
                            Log.w(TAG, "UDP fwd to $dstAddr:$dstPort: ${e.message}")
                        }
                    } else {
                        // 원격이 보낸 패킷: SOCKS5 UDP 헤더로 감싸 tun2proxy 에게 돌려보냄
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

            // TCP 제어 연결이 닫힐 때까지 블록한다. udpSocket 을 닫으면 릴레이 스레드가 멈춘다
            try {
                while (client.getInputStream().read() != -1) { /* keepalive 바이트를 버린다 */ }
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
        // data[off], data[off+1] = RSV (무시)
        if (data[off + 2].toInt() and 0xFF != 0) return null  // 단편화된 datagram 은 폐기 (FRAG != 0)
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
        val hdrSize = 4 + addrBytes.size + 2  // RSV(2) + FRAG(1) + ATYP(1) + 주소 + PORT(2)
        val result = ByteArray(hdrSize + length)
        result[0] = 0; result[1] = 0  // RSV (예약 필드)
        result[2] = 0                  // FRAG (단편화 없음)
        result[3] = atyp.toByte()
        addrBytes.copyInto(result, 4)
        var p = 4 + addrBytes.size
        result[p++] = (srcPort shr 8).toByte()
        result[p++] = (srcPort and 0xFF).toByte()
        data.copyInto(result, p, offset, offset + length)
        return result
    }

    private fun relay(client: Socket, remote: Socket) {
        // 두 파이프를 동시에 돌린다. 각 파이프가 EOF 에서 반대편의 쓰기 방향을 닫아,
        // 상대가 강제 RST 가 아니라 정상적인 FIN 을 받도록 한다.
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
        private const val REP_NOT_ALLOWED      = 2  // 규칙에 의해 허용되지 않음
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
