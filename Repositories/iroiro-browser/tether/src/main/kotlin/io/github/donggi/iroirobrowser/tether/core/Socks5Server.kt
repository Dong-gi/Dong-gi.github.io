package io.github.donggi.iroirobrowser.tether.core

import java.io.BufferedInputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * SOCKS5 서버(RFC 1928). **포트 [BASE_PORT](1080) 하나에만** 묶는다 — PC 의 tun2proxy 가 이 포트로 PC 의 트래픽을 통째로 보낸다.
 *
 * **다른 포트로 옮기지 않는다(뜻한 것).** usb-tether 의 예전 판은 1080 이 차 있으면 1081 부터 아홉 개를 훑었고, PC 의 런처는 같은 범위에서
 * SOCKS5 인사에 답하는 첫 포트를 골랐다. 그래서 폰의 악성 앱이 `INTERNET` 권한만으로 1080 을 먼저 잡고 `05 00` 만 답하면 진짜 프록시는 1081
 * 로 밀리고 런처는 그 앱을 골랐다 — **PC 트래픽 전부**가 그 앱을 지난다. 보이는 것은 평문과 TLS 의 SNI 이고, PC 가 믿는 인증서가 없으니 TLS
 * 를 벗기지는 못하지만 연결을 끊거나 엉뚱한 곳으로 잇거나 평문을 바꿀 수 있다. 이제 1080 이 차 있으면 조용히 옮기지 않고 **실패하고
 * 알린다**([StartError.PORT_TAKEN]). PC 의 런처(PC 연결 파일 zip 의 `windows-wifi.bat`·`macos-wifi.sh`)도 1080 만 쓴다. HTTP 프록시·파일 서버는
 * **사용자 결정(2026-10-10)으로 다음 포트로 옮긴다** — 그 머리말들.
 *
 * CONNECT(TCP)와 UDP ASSOCIATE(RFC 1928 7절)를 한다. 인증은 없다 — 무엇을 내밀든 NO_AUTH 를 고른다(손님을 가르는 것은 [PeerFilter] 와 Wi-Fi
 * Direct 의 패스프레이즈다 — 사용자 결정, CLAUDE.md '테더링').
 *
 * 바깥 연결은 모두 [Outbound](프록시 없는 소켓)로 이 폰의 회선에 새로 연다. 그래서 통신사가 보는 것은 폰이 연 소켓이다 — **다만 머리 수준까지다.**
 * 내용은 손대지 않고 지나가므로 PC 쪽 지문(TLS ClientHello 의 JA3/JA4·User-Agent·HTTP/2 SETTINGS 등)은 그대로 남는다. '폰 자신의 트래픽과
 * 구별되지 않는다' 가 아니라 'TTL·TCP 옵션·MSS 같은 머리로 보는 테더링 탐지에는 걸리지 않는다' 가 맞고, 그 경계는 재지 않았다.
 *
 * 상한: 동시 연결 [MAX_SESSIONS](UDP 묶음 포함), 그 가운데 UDP 묶음 [MAX_UDP_ASSOCIATIONS]. 스레드는 [ThreadBudget] 이 셋을 함께 묶는다.
 */
class Socks5Server internal constructor(
    env: TetherEnvironment,
    port: Int,
    /** UDP 묶음 상한 — 시험이 작게 준다(실제는 [MAX_UDP_ASSOCIATIONS]). */
    private val maxUdpAssociations: Int,
) : TcpServer(env, "socks5", port, attempts = 1, maxSessions = MAX_SESSIONS, threadsPerSession = THREADS_PER_SESSION) {

    constructor(env: TetherEnvironment, port: Int = BASE_PORT) : this(env, port, MAX_UDP_ASSOCIATIONS)

    /** UDP 목적지 이름의 풀이 캐시 — 근거는 [resolvedOrWarmUp]. 묶음마다가 아니라 서버에 하나(여러 묶음이 같은 이름을 쓰는 일이 흔하다). */
    private val udpDnsCache = ConcurrentHashMap<String, ResolvedName>()

    /** 지금 뒤에서 풀고 있는 이름 — 같은 이름을 겹쳐 묻지 않는다. */
    private val udpDnsInFlight: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** 지금 열린 UDP 묶음 수. */
    private val udpAssociations = AtomicInteger()

    override fun onStopped() {
        udpDnsCache.clear()
        udpDnsInFlight.clear()
    }

    override fun handle(client: Socket) {
        client.tcpNoDelay = true
        // 인사·요청을 다 받기까지의 전체 마감(DeadlineInputStream 의 머리말 — 읽기마다 다시 거는 soTimeout 만으로는 모자라다).
        val deadline = DeadlineInputStream(client, client.getInputStream(), env.timings.proxyHeaderMs, env.nowMs)
        // 버퍼로 감싸고 **이 스트림을 중계까지 그대로 넘긴다** — 답을 기다리지 않고 요청 뒤에 곧바로 데이터를 붙여 보내는 손님(낙관적 데이터)의
        // 바이트가 버퍼에 들어 있을 수 있다(client.getInputStream() 을 다시 부르면 잃는다 — HttpProxyServer 와 같다).
        val inp = BufferedInputStream(deadline, HANDSHAKE_BUFFER_BYTES)
        val out = client.getOutputStream()

        // 인증 협상: 손님이 무엇을 내밀든 NO_AUTH 를 고른다. 읽기는 모두 readByte() 로 — inp.read() 를 그대로 쓰면 끝(-1)이 값처럼 흘러 엉뚱한
        // 예외가 된다(readByte 의 머리말).
        if (inp.readByte() != VERSION) return
        repeat(inp.readByte()) { inp.readByte() }
        out.write(byteArrayOf(VERSION.toByte(), 0))

        // 요청 머리
        if (inp.readByte() != VERSION) return
        val cmd = inp.readByte()
        inp.readByte() // RSV(예약)

        val host = when (inp.readByte()) {
            ATYP_IPV4 -> InetAddress.getByAddress(inp.readN(4)).hostAddress!!
            ATYP_DOMAIN -> String(inp.readN(inp.readByte()), Charsets.US_ASCII)
            ATYP_IPV6 -> InetAddress.getByAddress(inp.readN(16)).hostAddress!!
            else -> {
                sendReply(out, REP_ATYP_UNSUPPORTED)
                return
            }
        }
        val dstPort = (inp.readByte() shl 8) or inp.readByte()
        // 머리를 다 받았다 — 마감을 거둔다. 그 뒤의 이름 풀이·연결은 손님을 읽지 않고, 중계는 시한이 없다(handleConnect·associate 가 0 으로).
        deadline.finish()

        when (cmd) {
            CMD_CONNECT -> handleConnect(client, inp, host, dstPort)
            CMD_UDP_ASSOCIATE -> handleUdpAssociate(client, inp)
            else -> sendReply(out, REP_CMD_UNSUPPORTED)
        }
    }

    private fun handleConnect(client: Socket, clientIn: InputStream, host: String, port: Int) {
        // 이름을 한 번만 풀고 통과한 주소 객체로 연결한다 — 글로 다시 넘기면 다시 풀려 DNS 재바인딩에 노출된다.
        val destination = when (val verdict = env.destinations.resolve(host)) {
            is Destination.Rejected -> {
                env.log.w(TAG, "CONNECT 거절: ${verdict.reason}")
                sendReply(client.getOutputStream(), REP_NOT_ALLOWED)
                return
            }
            // 풀지 못한 이름은 막은 것이 아니다 — '규칙이 허락하지 않음' 이 아니라 '호스트에 닿지 못함'.
            is Destination.Unresolved -> {
                env.log.w(TAG, "CONNECT 이름 풀이 실패: ${verdict.reason}")
                sendReply(client.getOutputStream(), REP_HOST_UNREACHABLE)
                return
            }
            is Destination.Allowed -> verdict.address
        }
        val remote = try {
            env.outbound.connect(destination, port, CONNECT_TIMEOUT_MS)
        } catch (e: Exception) {
            env.log.w(TAG, "연결 실패: ${e.javaClass.simpleName}")
            sendReply(client.getOutputStream(), REP_CONN_REFUSED)
            return
        }
        try {
            // BND.ADDR/BND.PORT 는 0.0.0.0:0 으로 답한다. 예전에는 remote 의 로컬 주소, 곧 폰의 **셀룰러 인터페이스 주소**를 그대로 넣었다 —
            // RFC 가 허락하는 값이지만 손님에게 통신사가 준 IP 를 알려 주는 셈이다. tun2proxy 를 비롯한 보통의 손님은 이 칸을 쓰지 않는다.
            client.getOutputStream().write(byteArrayOf(VERSION.toByte(), 0, 0, ATYP_IPV4.toByte(), 0, 0, 0, 0, 0, 0))
            client.soTimeout = 0
            relay(client, clientIn, remote)
        } finally {
            closeQuietly(remote)
        }
    }

    /**
     * UDP ASSOCIATE(RFC 1928 7절).
     *
     * ## 예전 판의 문제
     *
     * 소켓 **하나**를 `0.0.0.0` 에 묶어 두 방향에 함께 쓰고, '손님이 보낸 것' 과 '원격의 답' 을 출발 주소로 **어림**했다. 손님은 **처음 온 패킷**의
     * 출발지로 정했고 `client.inetAddress` 와 견주지 않았다. 그래서 (1) 먼저 패킷을 보낸 쪽이 손님이 되어 PC 의 DNS 질의가 통째로 그쪽에
     * 넘어갔고(임의 포트라도 전 범위에 하나씩 뿌리면 생기자마자 선점된다), (2) 열린 UDP 프록시가 되었고, (3) 위조한 출발지로 큰 DNS 답을 남에게
     * 튕기게 할 수 있었고, (4) 아무 호스트나 PC 에 답을 꽂아 DNS 캐시를 더럽힐 수 있었다. 이 길은 실제로 쓰였다 — 런처가 `--dns over-tcp` 를
     * 빼므로 PC 의 DNS 질의가 모두 여기를 지난다.
     *
     * ## 지금 구조 — 소켓 둘
     *
     *  - `clientFacing`: `client.localAddress`(192.168.49.1)에만 묶는다 — 다른 인터페이스에서는 닿지도 않는다. 출발 주소가 `client.inetAddress`
     *    가 아니면 버린다(출발지 **고정**). 포트만 첫 패킷에서 배운다.
     *  - `remoteFacing`: 원격으로 보내고 답을 받는 전용 소켓. 여기로 온 것은 뜻으로 '답' 이라 어림할 것이 없다.
     *
     * BND.ADDR/PORT 로는 `clientFacing` 의 주소·포트를 답한다. 묶음 하나가 스레드 셋(이 일꾼 + 두 방향)과 64 KiB 버퍼 둘을 쓴다 — 동시 묶음은
     * [MAX_UDP_ASSOCIATIONS] 까지, 셋째 스레드는 [ThreadBudget] 에서 하나 더 쥔다(못 쥐면 일반 실패로 답한다).
     *
     * ## 남는 한계
     *
     *  - `remoteFacing` 에 온 답이 우리가 보낸 요청의 답인지 견주지 않는다(NAT 표 없음). 보통의 SOCKS5 중계와 같은 수준이고, 길 밖의 공격자는
     *    임의 포트를 맞혀야 한다.
     *  - 요청의 DST.ADDR/DST.PORT 를 쓰지 않는다. 출발 **주소**는 `client.inetAddress` 로 고정되어 탈취는 막힌다. 포트까지 고정하려면 0 이 아닐
     *    때만 써야 한다(RFC 1928 7절은 모를 때 0 을 넣게 한다).
     */
    private fun handleUdpAssociate(client: Socket, control: InputStream) {
        val out = client.getOutputStream()
        if (udpAssociations.incrementAndGet() > maxUdpAssociations) {
            udpAssociations.decrementAndGet()
            // 센다 — 예전에는 기록 한 줄뿐이라 화면에서 보이지 않았다.
            noteRefusal(Refusal.UDP_LIMIT, "UDP 묶음 상한($maxUdpAssociations)이라 거절했다")
            sendReply(out, REP_GENERAL_FAILURE)
            return
        }
        try {
            if (!env.budget.tryAcquire(1)) {
                noteRefusal(Refusal.THREAD_BUDGET, "스레드 예산이 다 차서 UDP 묶음을 거절했다")
                sendReply(out, REP_GENERAL_FAILURE)
                return
            }
            try {
                associate(client, control, out)
            } finally {
                env.budget.release(1)
            }
        } finally {
            udpAssociations.decrementAndGet()
        }
    }

    private fun associate(client: Socket, control: InputStream, out: OutputStream) {
        val clientAddress = client.inetAddress
        val bindAddress = client.localAddress

        val clientFacing = try {
            DatagramSocket(0, bindAddress).apply { soTimeout = UDP_POLL_TIMEOUT_MS }
        } catch (e: Exception) {
            env.log.e(TAG, "UDP 소켓을 만들지 못했다: ${e.javaClass.simpleName}")
            sendReply(out, REP_GENERAL_FAILURE)
            return
        }
        val remoteFacing = try {
            env.outbound.openUdp().apply { soTimeout = UDP_POLL_TIMEOUT_MS }
        } catch (e: Exception) {
            env.log.e(TAG, "UDP 소켓을 만들지 못했다: ${e.javaClass.simpleName}")
            clientFacing.close()
            sendReply(out, REP_GENERAL_FAILURE)
            return
        }

        try {
            // BND.ADDR = clientFacing 이 묶인 인터페이스 주소(192.168.49.1).
            val bindOctets = bindAddress.asIpv4()?.address ?: byteArrayOf(0, 0, 0, 0)
            val relayPort = clientFacing.localPort
            out.write(
                byteArrayOf(
                    VERSION.toByte(), 0, 0, ATYP_IPV4.toByte(),
                    bindOctets[0], bindOctets[1], bindOctets[2], bindOctets[3],
                    (relayPort shr 8).toByte(), (relayPort and 0xFF).toByte(),
                ),
            )
            client.soTimeout = 0

            // 손님의 UDP 출발 포트. 주소는 고정이고 포트만 첫 패킷에서 배운다. 두 스레드가 함께 읽고 쓴다.
            val clientPort = AtomicInteger(-1)

            val toRemote = background("socks5-udp-out-$relayPort") {
                relayClientToRemote(clientFacing, remoteFacing, clientAddress, clientPort)
            }
            val toClient = background("socks5-udp-in-$relayPort") {
                relayRemoteToClient(clientFacing, remoteFacing, clientAddress, clientPort)
            }

            // TCP 제어 연결이 닫힐 때까지 막힌다. 닫히면 소켓을 닫아 두 중계를 멈춘다(머리를 읽은 그 스트림 — 버퍼에 남은 것도 버린다).
            try {
                while (control.read() != -1) { // keepalive 바이트를 버린다
                }
            } catch (_: Exception) {
            }

            clientFacing.close()
            remoteFacing.close()
            // 소켓을 닫았으니 두 스레드는 곧 끝난다(받기가 던진다) — 시한은 그 사이를 묶을 뿐이다.
            toRemote.join(UDP_JOIN_TIMEOUT_MS)
            toClient.join(UDP_JOIN_TIMEOUT_MS)
        } finally {
            clientFacing.close()
            remoteFacing.close()
        }
    }

    /** 손님 → 원격. 출발 주소가 [clientAddress] 가 아닌 패킷은 버린다 — 탈취·반사 증폭을 막는 고갱이다. 포트는 첫 패킷에서 배운다. */
    private fun relayClientToRemote(
        clientFacing: DatagramSocket,
        remoteFacing: DatagramSocket,
        clientAddress: InetAddress,
        clientPort: AtomicInteger,
    ) {
        val buf = ByteArray(UDP_BUF_SIZE)
        val pkt = DatagramPacket(buf, buf.size)
        while (!clientFacing.isClosed && isRunning) {
            try {
                pkt.setData(buf)
                clientFacing.receive(pkt)
            } catch (_: SocketTimeoutException) {
                continue
            } catch (e: Exception) {
                if (!clientFacing.isClosed) env.log.w(TAG, "UDP 받기: ${e.javaClass.simpleName}")
                break
            }

            // 출발지 고정: 이 묶음을 만든 손님의 주소만 받는다.
            if (pkt.address != clientAddress) continue

            val learned = clientPort.get()
            if (learned < 0) {
                clientPort.compareAndSet(-1, pkt.port)
            } else if (pkt.port != learned) {
                continue
            }

            val request = parseUdpHeader(pkt) ?: continue
            val dstAddr = when (val destination = request.destination) {
                is UdpDestination.Literal -> destination.address
                // 이름은 이 스레드에서 풀지 않는다 — resolvedOrWarmUp 의 머리말.
                is UdpDestination.Domain -> resolvedOrWarmUp(destination.name) ?: continue
            }
            // TCP 와 같은 규칙을 UDP 에도 — 없으면 UDP 중계가 루프백·사설 대역으로 가는 샛길이 된다.
            if (env.destinations.isBlocked(dstAddr)) continue
            val payload = request.payload
            env.onBytesIn(payload.size.toLong())
            try {
                remoteFacing.send(DatagramPacket(payload, payload.size, dstAddr, request.port))
            } catch (e: Exception) {
                env.log.w(TAG, "UDP 보내기 실패: ${e.javaClass.simpleName}")
            }
        }
    }

    /** 원격 → 손님. [remoteFacing] 에 온 것은 뜻으로 답이다. 손님의 포트를 아직 모르면 보낼 곳이 없어 버린다. */
    private fun relayRemoteToClient(
        clientFacing: DatagramSocket,
        remoteFacing: DatagramSocket,
        clientAddress: InetAddress,
        clientPort: AtomicInteger,
    ) {
        val buf = ByteArray(UDP_BUF_SIZE)
        val pkt = DatagramPacket(buf, buf.size)
        while (!remoteFacing.isClosed && isRunning) {
            try {
                pkt.setData(buf)
                remoteFacing.receive(pkt)
            } catch (_: SocketTimeoutException) {
                continue
            } catch (e: Exception) {
                if (!remoteFacing.isClosed) env.log.w(TAG, "UDP 받기: ${e.javaClass.simpleName}")
                break
            }

            val target = clientPort.get()
            if (target < 0) continue

            val wrapped = buildUdpHeader(pkt.address, pkt.port, pkt.data, pkt.offset, pkt.length)
            env.onBytesOut(pkt.length.toLong())
            try {
                clientFacing.send(DatagramPacket(wrapped, wrapped.size, clientAddress, target))
            } catch (e: Exception) {
                env.log.w(TAG, "UDP 답 보내기 실패: ${e.javaClass.simpleName}")
            }
        }
    }

    /**
     * 이름 목적지를 **캐시에서만** 푼다. 없으면 뒤에서 풀기를 걸고 null — 이 데이터그램은 버린다.
     *
     * 예전 판은 머리를 읽는 자리에서 `InetAddress.getByName()` 을 그대로 불렀다. 그 스레드는 이 묶음의 손님 → 원격 방향을 맡은 **하나뿐인**
     * 스레드라 답이 느린 이름 하나가 나머지 UDP 를 모두 막았고, 캐시도 없어 같은 이름을 데이터그램마다 다시 물었다. 버리는 쪽을 고른 까닭: UDP 는
     * 잃어버림을 전제하고 손님은 다시 보낸다(그때는 캐시가 차 있다). 스스로 이름을 푸는 손님(tun2proxy 포함)은 IP 리터럴을 보내 이 길을 타지
     * 않는다. 폭주 막기: 동시 풀이 [UDP_DNS_MAX_INFLIGHT], 캐시 [UDP_DNS_CACHE_MAX]. 답의 실제 TTL 은 `InetAddress` 로 볼 수 없어
     * [UDP_DNS_TTL_MS] 를 쓴다.
     */
    private fun resolvedOrWarmUp(domain: String): InetAddress? {
        val cached = udpDnsCache[domain]
        if (cached != null) {
            if (cached.expiresAtMs > System.currentTimeMillis()) return cached.address
            udpDnsCache.remove(domain, cached)
        }
        warmUpName(domain)
        return null
    }

    /**
     * [resolvedOrWarmUp] 의 뒤 풀이. 상한에 걸리면 아무것도 하지 않는다. 상한을 보기 전에 만료된 것을 걷는다 — [resolvedOrWarmUp] 은 **물은
     * 이름만** 만료를 보므로, 그것만으로는 다시 묻지 않는 이름이 만료된 채 자리를 차지해 서로 다른 이름 [UDP_DNS_CACHE_MAX] 개가 한 번 지나간
     * 뒤로 새 이름이 **영영** 풀리지 않았다.
     *
     * 풀이는 [DestinationPolicy.resolve] 로 한다 — 걸러진 주소만 캐시에 들어간다(보낼 때 [DestinationPolicy.isBlocked] 로 한 번 더 본다).
     */
    private fun warmUpName(domain: String) {
        if (udpDnsCache.size >= UDP_DNS_CACHE_MAX) {
            purgeExpiredNames()
            if (udpDnsCache.size >= UDP_DNS_CACHE_MAX) return
        }
        if (udpDnsInFlight.size >= UDP_DNS_MAX_INFLIGHT) return
        if (!udpDnsInFlight.add(domain)) return
        try {
            executor.submit {
                try {
                    when (val verdict = env.destinations.resolve(domain)) {
                        is Destination.Allowed -> {
                            udpDnsCache[domain] = ResolvedName(verdict.address, System.currentTimeMillis() + UDP_DNS_TTL_MS)
                        }
                        // 실패·거절은 캐시하지 않는다 — 다음 데이터그램이 다시 묻는다. 이름은 남기지 않는다.
                        is Destination.Rejected -> {
                            env.log.w(TAG, "UDP 목적지 거절: ${verdict.reason}")
                        }
                        is Destination.Unresolved -> {
                            env.log.w(TAG, "UDP 목적지 이름 풀이 실패: ${verdict.reason}")
                        }
                    }
                } finally {
                    udpDnsInFlight.remove(domain)
                }
            }
        } catch (t: Throwable) {
            // 풀이 일을 받지 못한다(내려갔거나 스레드를 더 못 만든다) — 진행 중 표시를 되돌려야 자리가 새지 않는다.
            udpDnsInFlight.remove(domain)
            env.log.w(TAG, "이름 풀이를 넘기지 못했다: ${t.javaClass.simpleName}")
        }
    }

    private fun purgeExpiredNames() {
        val now = System.currentTimeMillis()
        val iterator = udpDnsCache.entries.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().value.expiresAtMs <= now) iterator.remove()
        }
    }

    private fun sendReply(out: OutputStream, rep: Int) {
        out.write(byteArrayOf(VERSION.toByte(), rep.toByte(), 0, ATYP_IPV4.toByte(), 0, 0, 0, 0, 0, 0))
    }

    companion object {
        private const val TAG = "Socks5Server"

        /** 고정 포트. 옮기지 않는 까닭은 클래스 머리말. PC 의 런처(PC 연결 파일 zip 의 `windows-wifi.bat` 등)가 이 값을 쓴다. */
        const val BASE_PORT = 1080

        /**
         * 동시 연결 상한(UDP 묶음 포함). tun2proxy 로 PC 의 트래픽을 통째로 지나게 하면 TCP 연결 하나가 연결 하나다 — 평소의 상한이 아니라
         * 폭주 막기라 넉넉하게 둔다(usb-tether 와 같은 값).
         */
        const val MAX_SESSIONS = 256

        /**
         * 동시 UDP 묶음 상한 — 묶음 하나가 스레드 셋과 64 KiB 버퍼 둘(힙)을 쥔다. 128 이면 버퍼가 16 MiB 다. tun2proxy 는 UDP
         * 흐름(DNS 질의 하나하나 — 윈도는 질의마다 출발 포트가 다르다)마다 묶음을 열고 한동안(기본 10초로 기억한다 — 확인하지 않았다) 둔다고
         * 보아, 몰리는 순간에도 모자라지 않게 넉넉히 잡았다(tun2proxy 스스로도 동시 세션을 200 으로 묶는다고 기억한다 — 확인하지 않았다).
         * 넘치면 그 묶음은 일반 실패로 답한다(손님은 다시 보낸다).
         */
        const val MAX_UDP_ASSOCIATIONS = 128

        /** TCP 연결 하나가 쥐는 스레드 — 일꾼 + 반대 방향 중계. UDP 묶음은 하나를 더 쥔다. */
        private const val THREADS_PER_SESSION = 2

        private const val VERSION = 5
        private const val CONNECT_TIMEOUT_MS = 5000

        /** 인사·요청을 읽는 버퍼 — 머리는 수십 바이트다. 낙관적 데이터가 붙어 와도 중계가 이 버퍼부터 넘긴다. */
        private const val HANDSHAKE_BUFFER_BYTES = 512

        private const val ATYP_IPV4 = 1
        private const val ATYP_DOMAIN = 3
        private const val ATYP_IPV6 = 4

        private const val UDP_BUF_SIZE = 65535

        /** UDP 받기의 폴링 주기 — 소켓이 닫혔는지 보려면 받기가 때때로 풀려야 한다. */
        private const val UDP_POLL_TIMEOUT_MS = 2000

        /** UDP 이름 목적지의 풀이를 믿는 시간. */
        private const val UDP_DNS_TTL_MS = 60_000L
        private const val UDP_DNS_CACHE_MAX = 256
        private const val UDP_DNS_MAX_INFLIGHT = 8

        /** UDP 두 방향의 스레드가 끝나기를 기다리는 시간(소켓을 닫은 뒤라 곧 끝난다). */
        private const val UDP_JOIN_TIMEOUT_MS = 5000L

        private const val CMD_CONNECT = 1
        private const val CMD_UDP_ASSOCIATE = 3

        private const val REP_GENERAL_FAILURE = 1
        private const val REP_NOT_ALLOWED = 2 // 규칙이 허락하지 않음
        private const val REP_HOST_UNREACHABLE = 4 // 이름을 풀지 못함
        private const val REP_CONN_REFUSED = 5
        private const val REP_CMD_UNSUPPORTED = 7
        private const val REP_ATYP_UNSUPPORTED = 8

        /**
         * SOCKS5 UDP 요청 머리(RFC 1928 7절)를 읽는다. **이름은 풀지 않는다** — 중계 스레드에서 부르므로 막히는 일이 들면 안 된다.
         * 시험이 부르도록 internal.
         */
        internal fun parseUdpHeader(pkt: DatagramPacket): UdpRequest? {
            val data = pkt.data
            val off = pkt.offset
            val end = off + pkt.length
            if (pkt.length < 10) return null
            // data[off], data[off+1] = RSV(무시)
            if (data[off + 2].toInt() and 0xFF != 0) return null // 조각난 데이터그램은 버린다(FRAG != 0)
            val atyp = data[off + 3].toInt() and 0xFF
            var pos = off + 4
            val destination: UdpDestination
            when (atyp) {
                ATYP_IPV4 -> {
                    if (pos + 4 > end) return null
                    // getByAddress 는 DNS 를 타지 않는다.
                    destination = UdpDestination.Literal(InetAddress.getByAddress(data.copyOfRange(pos, pos + 4)))
                    pos += 4
                }
                ATYP_DOMAIN -> {
                    if (pos >= end) return null
                    val nameLen = data[pos++].toInt() and 0xFF
                    if (pos + nameLen > end) return null
                    destination = UdpDestination.Domain(String(data, pos, nameLen, Charsets.US_ASCII))
                    pos += nameLen
                }
                ATYP_IPV6 -> {
                    if (pos + 16 > end) return null
                    destination = UdpDestination.Literal(InetAddress.getByAddress(data.copyOfRange(pos, pos + 16)))
                    pos += 16
                }
                else -> return null
            }
            if (pos + 2 > end) return null
            val dstPort = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
            pos += 2
            return UdpRequest(destination, dstPort, data.copyOfRange(pos, end))
        }

        /** 원격의 답을 SOCKS5 UDP 머리로 감싼다. 시험이 부르도록 internal. */
        internal fun buildUdpHeader(srcAddr: InetAddress, srcPort: Int, data: ByteArray, offset: Int, length: Int): ByteArray {
            val addrBytes = srcAddr.address
            val atyp = if (addrBytes.size == 4) ATYP_IPV4 else ATYP_IPV6
            val hdrSize = 4 + addrBytes.size + 2 // RSV(2) + FRAG(1) + ATYP(1) + 주소 + PORT(2)
            val result = ByteArray(hdrSize + length)
            result[0] = 0
            result[1] = 0 // RSV
            result[2] = 0 // FRAG(조각 없음)
            result[3] = atyp.toByte()
            addrBytes.copyInto(result, 4)
            var p = 4 + addrBytes.size
            result[p++] = (srcPort shr 8).toByte()
            result[p++] = (srcPort and 0xFF).toByte()
            data.copyInto(result, p, offset, offset + length)
            return result
        }
    }
}

/** SOCKS5 UDP 요청의 목적지 — 리터럴 주소(ATYP 1/4)이거나 이름(ATYP 3). */
internal sealed interface UdpDestination {
    /** 이미 주소 꼴이라 풀 것이 없다. */
    class Literal(val address: InetAddress) : UdpDestination

    /** 풀어야 한다 — 중계 스레드에서 하면 안 된다. */
    class Domain(val name: String) : UdpDestination
}

/** 읽어 낸 SOCKS5 UDP 요청 하나(RFC 1928 7절). */
internal class UdpRequest(
    val destination: UdpDestination,
    val port: Int,
    val payload: ByteArray,
)

/** 이름 풀이의 결과와 만료 시각. */
private class ResolvedName(val address: InetAddress, val expiresAtMs: Long)

/**
 * 1바이트를 읽고 끝이면 예외를 던진다.
 *
 * `InputStream.read()` 는 끝에서 -1 을 돌려주는데 usb-tether 의 예전 핸드셰이크는 그 값을 보지 않고 썼다 — `String(readN(read()), …)` 은
 * `ByteArray(-1)` 로, `(read() shl 8) or read()` 는 어느 쪽이든 -1 이면 결과 전체가 -1 로 더럽혀져(`(-1 shl 8) or -1 == -1`)
 * `InetSocketAddress` 가 던졌다. 둘 다 넓은 catch 에 잡혀 큰 탈은 없었지만 정상 끝을 예외로 보고했다.
 *
 * @throws EOFException 스트림이 끝났으면
 */
private fun InputStream.readByte(): Int {
    val b = read()
    if (b == -1) throw EOFException()
    return b
}

private fun InputStream.readN(n: Int): ByteArray {
    val buf = ByteArray(n)
    var off = 0
    while (off < n) {
        val read = read(buf, off, n - off)
        if (read == -1) throw EOFException()
        off += read
    }
    return buf
}
