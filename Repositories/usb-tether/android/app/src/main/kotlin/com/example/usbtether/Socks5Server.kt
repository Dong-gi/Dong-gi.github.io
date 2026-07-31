package com.example.usbtether

import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.net.*
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * SOCKS5 서버 (RFC 1928). `0.0.0.0:`[BASE_PORT] 에만 바인딩한다.
 *
 * **포트 폴백을 하지 않는다(의도).** 예전에는 [BASE_PORT]+9 까지 훑어 비어 있는 첫
 * 포트를 잡았고, PC 런처는 같은 범위를 훑어 SOCKS5 핸드셰이크에 응답하는 첫 포트를
 * 채택했다. 그래서 폰의 악성 앱이 `INTERNET` 권한만으로 1080 을 먼저 잡고 `05 00`
 * 만 답하면, 진짜 프록시는 1081 로 밀리고 런처는 그 악성 앱을 채택한다. 그 앱은
 * **PC 트래픽 100%** 를 보고 변조할 수 있으며 CONNECT 의 종단이 자신이므로 TLS 도
 * 벗겨낼 수 있다. 견고성을 위한 기능이 탈취 원시요소였다.
 *
 * 이제 1080 이 점유돼 있으면 조용히 옮기지 않고 **실패하고 알린다**. 사용자가
 * 무언가 다른 것이 듣고 있다는 사실을 알게 되는 것이 조용한 이전보다 안전하다.
 * 실패 원인은 [lastError] 로 노출된다.
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

    /** 수립된 연결을 추적한다. stop() 이 실제로 끊을 수 있게 하는 유일한 수단이다. */
    private val connections = ConnectionRegistry()

    /** 바인딩된 포트([BASE_PORT]). 서버가 동작 중이 아니면 -1. */
    @Volatile var actualPort: Int = -1
        private set

    /** 마지막 기동 실패 원인. UI 에 표시해 사용자가 원인을 알 수 있게 한다. */
    @Volatile var lastError: String? = null
        private set

    /** 호출자가 결과를 즉시 볼 수 있도록 동기적으로 바인딩한 뒤 accept 를 시작한다. */
    fun start(): Int {
        if (!running.compareAndSet(false, true)) return actualPort
        val sock = bind() ?: run {
            running.set(false)
            return -1
        }
        serverSocket = sock
        actualPort = sock.localPort
        lastError = null
        Log.i(TAG, "SOCKS5 listening on port $actualPort")
        thread(name = "socks5-acceptor", isDaemon = true) { acceptLoop() }
        return actualPort
    }

    /**
     * [BASE_PORT] 에만 바인딩한다. 다른 포트로 옮기지 않는다 — 클래스 KDoc 의
     * 포트 스쿼팅 설명 참고.
     */
    private fun bind(): ServerSocket? = try {
        ServerSocket(BASE_PORT, 50, InetAddress.getByName("0.0.0.0")).apply {
            reuseAddress = true
        }
    } catch (e: Exception) {
        lastError = "SOCKS5 포트 $BASE_PORT 를 다른 앱이 쓰고 있습니다"
        Log.e(TAG, "bind on $BASE_PORT failed: ${e.message}")
        null
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
                // 동시 세션 상한. 등록을 accept 시점에 하는 것이 중요하다 — handleClient
                // 안에서 등록하면 accept 와 등록 사이에 태스크가 쌓여 CachedThreadPool 이
                // 무제한으로 스레드를 만든다. 여기서 세면 풀 크기가 구조적으로 묶인다.
                if (connections.size >= MAX_CONCURRENT_SESSIONS) {
                    Log.w(TAG, "동시 세션 상한 초과로 연결을 거부했다")
                    try { client.close() } catch (_: Exception) {}
                    continue
                }
                connections.register(client)
                try {
                    executor.submit { handleClient(client) }
                } catch (_: RejectedExecutionException) {
                    connections.unregister(client)
                    try { client.close() } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "acceptLoop crashed", e)
        }
    }

    private fun handleClient(client: Socket) {
        // 등록은 acceptLoop 에서 이미 했다(동시 세션 판정을 정확히 하기 위해).
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
            connections.unregister(client)
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

    /**
     * UDP ASSOCIATE (RFC 1928 §7).
     *
     * ## 이전 구현의 문제
     *
     * 소켓 **하나**를 `0.0.0.0` 에 바인딩해 양방향에 함께 쓰고, "클라이언트가 보낸
     * 패킷"과 "원격이 보낸 응답"을 출처 주소·포트 비교로 **추론**했다. 클라이언트
     * 식별자는 **가장 먼저 도착한 패킷**의 출처에서 확정했고, `client.inetAddress`
     * 와 대조하지 않았다. 연결을 묶기 위해 존재하는 RFC 1928 의 DST.ADDR/DST.PORT
     * 는 파싱된 뒤 버려졌다. 그리고 확정된 클라이언트가 아닌 출처의 패킷은
     * "원격의 응답"으로 간주해 SOCKS5 헤더로 감싸 클라이언트에게 전달했다.
     *
     * 이 경로는 실사용에서 활성이었다 — `windows-wifi.bat` 이 `--dns over-tcp` 를
     * 생략하므로 PC 의 DNS 질의 전량이 이 릴레이를 지난다.
     *
     * 결과적으로 네 가지가 가능했다.
     *  1. **연결 탈취.** tun2proxy 보다 먼저 릴레이 포트에 패킷을 보낸 쪽이
     *     클라이언트가 된다. 이후 PC 의 패킷은 "응답" 분기로 떨어져 공격자에게
     *     전달된다 — 목적지와 DNS 질의 내용이 담긴 원본 datagram 그대로. PC 의
     *     DNS 는 조용히 죽는다. 포트가 임의(ephemeral)라도 전 범위에 1패킷씩
     *     뿌리면(약 28k 패킷) 생성 즉시 선점되므로 통제 수단이 아니다.
     *  2. **오픈 UDP 프록시.** 확정 후 임의 목적지로 보낼 수 있고 폰의 통신사 IP 가
     *     출처로 보인다.
     *  3. **출처 위조 반사 증폭.** 클라이언트 식별자가 위조 가능한 패킷 출처에서
     *     오므로, 피해자로 위조한 1패킷으로 큰 DNS 응답을 피해자에게 반사시킬 수 있다.
     *  4. **PC 로의 응답 주입.** 릴레이 포트에 도달하는 아무 호스트나 자기 주소를
     *     SRC 로 해서 PC 에 페이로드를 넣을 수 있어 DNS 캐시 오염이 가능했다.
     *
     * ## 현재 구조
     *
     * 방향을 추론하지 않고 **소켓 두 개로 구조화**한다.
     *  - `clientFacing`: `client.localAddress`(192.168.49.1)에만 바인딩한다.
     *    다른 인터페이스에서는 아예 도달할 수 없다. 수신 패킷의 출처 주소가
     *    `client.inetAddress` 와 다르면 폐기한다(출처 **고정**). 포트만 첫 패킷에서
     *    학습한다.
     *  - `remoteFacing`: 원격으로 보내고 응답을 받는 전용 소켓. 여기서 온 것은
     *    정의상 "응답"이므로 추론이 필요 없다.
     *
     * BND.ADDR/PORT 로는 `clientFacing` 의 바인딩 주소·포트를 응답한다.
     *
     * **남는 한계**: `remoteFacing` 에 도착한 응답이 실제로 우리가 보낸 요청에
     * 대응하는지 대조하지 않는다(NAT 테이블 미구현). 일반적인 SOCKS5 릴레이 구현과
     * 같은 수준이며, off-path 공격자는 임의 ephemeral 포트를 맞혀야 한다.
     */
    private fun handleUdpAssociate(client: Socket) {
        val out = client.getOutputStream()
        val clientAddress = client.inetAddress
        val bindAddress = client.localAddress

        val clientFacing = try {
            DatagramSocket(0, bindAddress).apply { soTimeout = UDP_POLL_TIMEOUT_MS }
        } catch (e: Exception) {
            Log.e(TAG, "UDP socket creation failed: ${e.message}")
            sendReply(out, REP_GENERAL_FAILURE)
            return
        }
        val remoteFacing = try {
            DatagramSocket().apply { soTimeout = UDP_POLL_TIMEOUT_MS }
        } catch (e: Exception) {
            Log.e(TAG, "UDP socket creation failed: ${e.message}")
            clientFacing.close()
            sendReply(out, REP_GENERAL_FAILURE)
            return
        }

        try {
            // BND.ADDR = clientFacing 이 바인딩된 인터페이스 주소(192.168.49.1).
            val bindOctets = (bindAddress as? Inet4Address)?.address ?: byteArrayOf(0, 0, 0, 0)
            val relayPort = clientFacing.localPort
            out.write(
                byteArrayOf(5, 0, 0, ATYP_IPV4.toByte(),
                    bindOctets[0], bindOctets[1], bindOctets[2], bindOctets[3],
                    (relayPort shr 8).toByte(), (relayPort and 0xFF).toByte())
            )
            client.soTimeout = 0

            // 클라이언트의 UDP 출처 포트. 주소는 고정이고 포트만 첫 패킷에서 배운다.
            // 두 스레드가 함께 읽고 쓰므로 원자적으로 다룬다.
            val clientPort = AtomicInteger(-1)

            val toRemote = thread(isDaemon = true, name = "udp-to-remote-$relayPort") {
                relayClientToRemote(clientFacing, remoteFacing, clientAddress, clientPort)
            }
            val toClient = thread(isDaemon = true, name = "udp-to-client-$relayPort") {
                relayRemoteToClient(clientFacing, remoteFacing, clientAddress, clientPort)
            }

            // TCP 제어 연결이 닫힐 때까지 블록한다. 소켓을 닫으면 두 릴레이가 멈춘다.
            try {
                while (client.getInputStream().read() != -1) { /* keepalive 바이트를 버린다 */ }
            } catch (_: Exception) {}

            clientFacing.close()
            remoteFacing.close()
            toRemote.join(RELAY_JOIN_TIMEOUT_MS)
            toClient.join(RELAY_JOIN_TIMEOUT_MS)
        } finally {
            clientFacing.close()
            remoteFacing.close()
        }
    }

    /**
     * 클라이언트 → 원격 방향.
     *
     * 출처 주소가 [clientAddress] 와 일치하지 않는 패킷은 폐기한다. 이것이 연결
     * 탈취·반사 증폭을 막는 핵심이다. 포트는 첫 유효 패킷에서 학습하고, 이후에는
     * 그 포트만 받는다.
     */
    private fun relayClientToRemote(
        clientFacing: DatagramSocket,
        remoteFacing: DatagramSocket,
        clientAddress: InetAddress,
        clientPort: AtomicInteger,
    ) {
        val buf = ByteArray(UDP_BUF_SIZE)
        val pkt = DatagramPacket(buf, buf.size)
        while (!clientFacing.isClosed && running.get()) {
            try {
                pkt.setData(buf)
                clientFacing.receive(pkt)
            } catch (_: SocketTimeoutException) {
                continue
            } catch (e: Exception) {
                if (!clientFacing.isClosed) Log.w(TAG, "UDP recv: ${e.message}")
                break
            }

            // 출처 고정: 이 연결을 만든 클라이언트의 주소만 받는다.
            if (pkt.address != clientAddress) continue

            val learned = clientPort.get()
            if (learned < 0) {
                clientPort.compareAndSet(-1, pkt.port)
            } else if (pkt.port != learned) {
                continue
            }

            val (dstAddr, dstPort, payload) = parseUdpHeader(pkt) ?: continue
            // TCP 경로와 같은 정책을 UDP 에도 적용한다. 이게 없으면 UDP 릴레이가
            // 루프백·사설 대역으로 가는 우회로가 된다.
            if (DestinationFilter.isBlocked(dstAddr)) continue
            onBytesIn(payload.size.toLong())
            try {
                remoteFacing.send(DatagramPacket(payload, payload.size, dstAddr, dstPort))
            } catch (e: Exception) {
                Log.w(TAG, "UDP 전달 실패: ${e.message}")
            }
        }
    }

    /**
     * 원격 → 클라이언트 방향.
     *
     * [remoteFacing] 에 도착한 것은 정의상 응답이므로 방향 추론이 필요 없다.
     * 클라이언트 포트를 아직 배우지 못했으면 보낼 곳이 없으므로 폐기한다.
     */
    private fun relayRemoteToClient(
        clientFacing: DatagramSocket,
        remoteFacing: DatagramSocket,
        clientAddress: InetAddress,
        clientPort: AtomicInteger,
    ) {
        val buf = ByteArray(UDP_BUF_SIZE)
        val pkt = DatagramPacket(buf, buf.size)
        while (!remoteFacing.isClosed && running.get()) {
            try {
                pkt.setData(buf)
                remoteFacing.receive(pkt)
            } catch (_: SocketTimeoutException) {
                continue
            } catch (e: Exception) {
                if (!remoteFacing.isClosed) Log.w(TAG, "UDP recv: ${e.message}")
                break
            }

            val target = clientPort.get()
            if (target < 0) continue

            val wrapped = buildUdpHeader(pkt.address, pkt.port, pkt.data, pkt.offset, pkt.length)
            onBytesOut(pkt.length.toLong())
            try {
                clientFacing.send(DatagramPacket(wrapped, wrapped.size, clientAddress, target))
            } catch (e: Exception) {
                Log.w(TAG, "UDP 응답 실패: ${e.message}")
            }
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

    /**
     * 서버를 멈춘다.
     *
     * 리스닝 소켓을 닫는 것만으로는 부족하다. `read()` 에 블록된 워커 스레드는
     * 인터럽트를 무시하므로, 수립된 소켓을 직접 닫아야 릴레이가 실제로 끝난다.
     * 순서가 중요하다: 새 연결을 먼저 막고(리스닝 소켓 닫기), 기존 연결을 끊고,
     * 그다음 스레드풀을 내린다.
     */
    fun stop() {
        if (!running.compareAndSet(true, false)) return
        try { serverSocket?.close() } catch (_: Exception) {}
        connections.closeAll()
        executor.shutdownNow()
        actualPort = -1
    }

    companion object {
        private const val TAG = "Socks5Server"
        private const val CONNECT_TIMEOUT_MS = 5000

        /**
         * 동시 세션 상한.
         *
         * 세션 하나가 스레드 두 개를 쓴다(풀 워커 + relay 의 반대 방향 스레드).
         * 상한이 없으면 클라이언트가 연결만 열어두어 `OutOfMemoryError: unable to
         * create new native thread` 를 유발할 수 있다. 정상 사용의 상한이 아니라
         * 폭주 방지선이므로 넉넉하게 잡는다 — tun2proxy 로 PC 트래픽 전량을
         * 통과시키면 TCP 연결 하나당 세션 하나가 된다.
         */
        private const val MAX_CONCURRENT_SESSIONS = 256

        /** 고정 포트. 폴백하지 않는 이유는 클래스 KDoc 참고. */
        const val BASE_PORT = 1080

        private const val ATYP_IPV4   = 1
        private const val ATYP_DOMAIN = 3
        private const val ATYP_IPV6   = 4

        private const val UDP_BUF_SIZE = 65535

        /** UDP 수신 폴링 주기. 소켓이 닫혔는지 확인하려면 receive 가 주기적으로 풀려야 한다. */
        private const val UDP_POLL_TIMEOUT_MS = 2000

        /** 릴레이 스레드 종료를 기다리는 시간. */
        private const val RELAY_JOIN_TIMEOUT_MS = 5000L

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
