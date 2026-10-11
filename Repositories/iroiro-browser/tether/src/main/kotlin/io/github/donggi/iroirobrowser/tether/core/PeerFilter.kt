package io.github.donggi.iroirobrowser.tether.core

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.Socket

/**
 * 손님을 받을지 — 서버가 accept 한 직후에 묻는다. null 이면 받는다. 시험은 루프백을 받는 것으로 바꾼다(실제 판정은 [PeerFilter]).
 */
fun interface PeerGate {
    fun check(client: Socket): PeerRejection?
}

/**
 * 손님을 거절한 까닭. **손님의 주소는 싣지 않는다**([TetherLog] 의 규칙). 덧말은 까닭마다 다르다 — 화면의 '거절한 연결' 줄이 그것을 보여,
 * 사용자 폰에서 손님이 모두 막히면 무엇이 어긋났는지(인터페이스 이름·netId) 알 수 있게 한다.
 */
data class PeerRejection(
    val reason: Reason,
    /** [Reason.NOT_GROUP_INTERFACE] — 그룹 오너 주소가 붙은 인터페이스(모르면 null). */
    val interfaceName: String? = null,
    /** [Reason.NOT_GROUP_INTERFACE] — 지금 떠 있는 우리 그룹의 인터페이스. */
    val groupInterface: String? = null,
    /** [Reason.WRONG_NETWORK] — 연결이 들어온 망(받은 소켓의 SO_MARK 아래 16비트 — netd 의 netId). */
    val netId: Int? = null,
    /** [Reason.NETWORK_UNKNOWN] — 들어온 망을 읽지 못한 까닭(예외의 종류). */
    val detail: String? = null,
) {
    enum class Reason {
        /** 그룹 오너 주소(192.168.49.1)로 오지 않았다 — 핫스팟이 꺼져 있거나 다른 망의 주소로 왔다. */
        NOT_GROUP_OWNER_ADDRESS,

        /** 상대가 그룹의 손님 주소(192.168.49.2–254)가 아니다 — 폰 안의 다른 앱(.1)이거나 다른 대역. */
        NOT_GROUP_CLIENT,

        /** 이 앱의 Wi-Fi Direct 그룹이 떠 있지 않다(그룹을 확인하기 전·내려간 뒤 — 다른 앱의 그룹일 수 있다). */
        NO_GROUP,

        /** 그룹 오너 주소가 붙은 인터페이스가 우리 그룹의 인터페이스가 아니다(이름을 모를 때도). */
        NOT_GROUP_INTERFACE,

        /** 연결이 들어온 망(netId)이 Wi-Fi Direct 그룹이 든 로컬 망이 아니다 — 폰이 붙은 다른 Wi-Fi·셀룰러로 들어왔다. */
        WRONG_NETWORK,

        /** 연결이 들어온 망을 읽지 못했다(SO_MARK). 모르면 받지 않는다. */
        NETWORK_UNKNOWN,
    }
}

/**
 * 테더 서버에 붙어도 되는 손님을 가른다.
 *
 * ## 왜 필요한가
 *
 * `ServerSocket(port, backlog, InetAddress.getByName("0.0.0.0"))` 은 IPv4 전용으로 묶이지 **않는다**. IPv6 가 되는 JVM 에서는 `AF_INET6`
 * 소켓을 만들고 `0.0.0.0` 을 `::` 로 바꾼다(usb-tether 때 실기기의 `ss -ltn` 에서 `*:1080` 으로 확인했다). 그래서 서버들이 폰의 **모든**
 * 인터페이스에서 닿는다 — 폰이 붙어 있는 보통 Wi-Fi(카페·사무실·집)와, 통신사가 공인 IPv6 를 주면 인터넷에서도. Wi-Fi Direct 의 WPA2
 * 패스프레이즈는 그 길들을 지키지 못한다.
 *
 * ## 넷을 함께 본다
 *
 * 1. **연결이 향한 로컬 주소**가 그룹 오너 주소(`192.168.49.1`)인가 — 핫스팟이 꺼져 있으면 그 주소가 어느 인터페이스에도 없다.
 * 2. **상대 주소**가 그 대역의 손님(`192.168.49.2`–`192.168.49.254`)인가. `.1`(그룹 오너 = 이 폰)은 거절한다 — 폰 안의 다른 앱이 그 주소로
 *    붙으면 커널이 출발 주소로 같은 인터페이스 주소를 고르므로 이것이 곧 '폰 안의 앱 막기' 다(다른 앱이 이 프록시로 제 `INTERNET` 권한을
 *    세탁해 VPN 방화벽·UID 별 데이터 제한을 비켜 가지 못하게). 에뮬레이터에서 그렇게 막혔다(실기기에서는 확인하지 않았다).
 * 3. **이 앱의 그룹이 떠 있고, 그룹 오너 주소가 붙은 인터페이스가 정확히 그 그룹의 인터페이스인가**([groupInterface] — 앱이
 *    `WifiP2pGroup.getInterface()` 로 받아 둔 이름). 그룹이 없으면 거절한다 — 다른 앱(옛 테더 앱 등)의 Wi-Fi Direct 그룹도 같은 대역을 쓰고
 *    같은 로컬 망에 든다. 예전에는 이름이 `p2p` 로 시작하는지만 보았다.
 * 4. **연결이 들어온 망**(받은 소켓의 SO_MARK — [socketMark])이 로컬 망([LOCAL_NET_ID])인가 — **읽는 함수가 주어졌을 때만**(아래). 이것이
 *    '어느 인터페이스로 **들어왔는가**' 를 보는 검사다 — 1·3 은 연결이 **향한** 주소와 그 주소가 붙은 인터페이스를 볼 뿐이다. 그래서 폰이
 *    남의 Wi-Fi(STA)에 붙은 채 핫스팟을 켜면, 그 Wi-Fi 쪽 기기가 `192.168.49.1` 로 가는 경로를 폰의 Wi-Fi 주소로 넣고 출발 주소
 *    `192.168.49.77` 로 SYN 을 보내 1–3 을 다 지날 수 있다(커널의 rp_filter 가 꺼져 있으면 — 에뮬레이터에서 꺼져 있었다. 답은 들어온 망의
 *    경로표를 따라 그 Wi-Fi 로 나가므로 그 Wi-Fi 의 공유기를 쥔 공격자에게 닿는다).
 *
 *    안드로이드는 `net.ipv4.tcp_fwmark_accept=1` 이라 받은 소켓이 들어온 SYN 의 fwmark 를 물려받고, netd 는 들어오는 패킷에 그 인터페이스가 든
 *    망의 netId 를 표시한다(fwmark 의 아래 16비트). Wi-Fi Direct 의 그룹 인터페이스는 tethering 모듈의 IpServer 가 **로컬 망(netId 99 —
 *    `INetd.LOCAL_NET_ID`)** 에 넣는다고 AOSP 에서 읽었다(기기에서 확인하지 못했다). 다르면 손님이 모두 [PeerRejection.Reason.WRONG_NETWORK]
 *    로 막히고 화면에 읽은 netId 가 보인다. 읽지 못하면 거절한다.
 *
 *    **앱은 지금 이 검사를 켜지 못한다.** 일반 앱이 받은 소켓의 SO_MARK 를 읽을 공개 API 가 없다 — `android.system.Os.getsockoptInt` 는
 *    `@SystemApi(client = MODULE_LIBRARIES)` 라 SDK 에 없고(컴파일되지 않는다), 반사로 부르면 숨은 API 목록의 `MAX_TARGET_O` 라 targetSdk 36
 *    에서 막힌다(lint 의 private-apis 로 확인). 공개된 `getsockoptTimeval` 은 timeval 이 아닌 옵션에 IllegalArgumentException 이나 엉뚱한 값을
 *    준다고 문서에 있다. 읽으려면 NDK(네이티브 코드 — 새 빌드 의존)가 든다 — 사용자·주 세션이 정할 일이다. 그래서 [socketMark] 는 비워 둘 수
 *    있고, 비어 있으면 이 검사를 건너뛴다(판정은 시험이 지킨다 — 읽는 길이 생기면 넣기만 하면 된다). 그동안 위의 길은 막히지 않는다(CLAUDE.md
 *    '테더링' 의 '확인하지 못한 것과 남은 위험').
 *
 * 인터페이스 조회는 주소가 맞는 연결에만 한다(`NetworkInterface.getByInetAddress` — 인터페이스를 모두 훑는 시스템 호출. 시험은 [interfaceName]
 * 을 주입한다). usb-tether 가 이 검사를 미룬 까닭이 '연결마다 시스템 호출' 이었으므로, 답을 [CACHE_MS] 동안 기억한다(그룹의 인터페이스가 바뀌면
 * 곧바로 다시 묻는다) — tun2proxy 는 페이지 하나에 연결 수십 개를 한꺼번에 연다. SO_MARK 는 (읽을 수 있다면) 연결마다 읽는다(소켓마다 다르다).
 * 조회에는 매핑을 벗긴 IPv4 를 넘긴다 — `::ffff:192.168.49.1` 로 물으면 인터페이스의 IPv4 와 맞지 않아 늘 null 이 된다.
 *
 * 루프백은 받지 않는다(usb-tether 의 USB 길이 없어진 뒤로 열어 둘 까닭이 없다).
 *
 * @param groupInterface 지금 떠 있는 **이 앱의** Wi-Fi Direct 그룹의 인터페이스 이름. 그룹이 없으면 null — 그러면 모두 거절한다.
 * @param socketMark 받은 소켓의 SO_MARK. 읽지 못하면 던진다. null 이면 넷째 검사를 건너뛴다(앱은 지금 읽을 길이 없다 — 위).
 * @param nowMs 단조 시계(밀리초) — 시험은 손으로 돌린다.
 * @param interfaceName 인터페이스 조회(시험은 주입한다).
 */
class PeerFilter(
    private val groupInterface: () -> String?,
    private val socketMark: ((Socket) -> Int)? = null,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 },
    private val interfaceName: (Inet4Address) -> String? = { systemInterfaceName(it) },
) : PeerGate {

    /** 마지막 조회(주소·그때의 그룹·답·때) — 서버 셋의 accept 스레드가 함께 읽으므로 통째로 갈아 끼운다. */
    @Volatile
    private var cached: Lookup? = null

    private class Lookup(val address: Inet4Address, val group: String, val name: String?, val atMs: Long)

    override fun check(client: Socket): PeerRejection? {
        val reader = socketMark
        return check(client.inetAddress, client.localAddress, reader?.let { { it(client) } })
    }

    /** [check] 의 판정 — 소켓 없이 시험한다. [mark] 는 앞의 검사를 모두 지난 연결에만 부르고, null 이면 넷째 검사를 건너뛴다. */
    internal fun check(peer: InetAddress?, local: InetAddress?, mark: (() -> Int)?): PeerRejection? {
        val localV4 = local?.asIpv4()
        if (localV4 == null || !isGroupOwnerAddress(localV4)) return PeerRejection(PeerRejection.Reason.NOT_GROUP_OWNER_ADDRESS)
        if (!isGroupClientAddress(peer)) return PeerRejection(PeerRejection.Reason.NOT_GROUP_CLIENT)
        val group = groupInterface()?.takeIf { it.isNotEmpty() } ?: return PeerRejection(PeerRejection.Reason.NO_GROUP)
        val name = interfaceOf(localV4, group)
        if (name != group) {
            return PeerRejection(PeerRejection.Reason.NOT_GROUP_INTERFACE, interfaceName = name, groupInterface = group)
        }
        if (mark == null) return null
        val netId = try {
            mark() and NET_ID_MASK
        } catch (e: Exception) {
            return PeerRejection(PeerRejection.Reason.NETWORK_UNKNOWN, detail = e.javaClass.simpleName)
        }
        if (netId != LOCAL_NET_ID) return PeerRejection(PeerRejection.Reason.WRONG_NETWORK, netId = netId)
        return null
    }

    private fun interfaceOf(address: Inet4Address, group: String): String? {
        val now = nowMs()
        cached?.let { if (it.address == address && it.group == group && now - it.atMs in 0 until CACHE_MS) return it.name }
        val name = try {
            interfaceName(address)
        } catch (e: Exception) {
            null
        }
        cached = Lookup(address, group, name, now)
        return name
    }

    companion object {
        /** 그룹 오너(이 폰)의 주소 — 파일 서버의 `Host` 검사([FileServer])도 이것을 쓴다. PC 의 스크립트·Wi-Fi 프로필이 이 값을 쓴다. */
        const val GROUP_OWNER = "192.168.49.1"

        /**
         * netd 의 로컬 망 번호(`INetd.LOCAL_NET_ID`) — 테더링·Wi-Fi Direct 의 인터페이스가 드는 망. AOSP 의 값이고 사용자 폰에서는 확인하지
         * 못했다(클래스 머리말 4).
         */
        const val LOCAL_NET_ID = 99

        /** fwmark 에서 netId 가 든 자리(아래 16비트 — 위는 explicitlySelected·protectedFromVpn·권한 비트). */
        const val NET_ID_MASK = 0xFFFF

        /** 인터페이스 조회의 답을 기억하는 시간. */
        const val CACHE_MS = 2_000L

        /** 안드로이드가 Wi-Fi Direct 그룹에 주는 대역: 192.168.49.0/24 */
        private const val P2P_OCTET_0 = 192
        private const val P2P_OCTET_1 = 168
        private const val P2P_OCTET_2 = 49

        /** 그룹 오너(이 폰)의 마지막 옥텟. */
        private const val GROUP_OWNER_OCTET_3 = 1

        /** 네트워크 주소와 브로드캐스트 주소 — 손님이 아니다. */
        private const val NETWORK_OCTET_3 = 0
        private const val BROADCAST_OCTET_3 = 255

        /** 기기의 인터페이스 이름 — 그 주소가 붙은 인터페이스가 없거나 묻지 못하면 null. */
        fun systemInterfaceName(address: Inet4Address): String? = NetworkInterface.getByInetAddress(address)?.name

        private fun isGroupOwnerAddress(address: Inet4Address): Boolean {
            val octets = address.address
            return inP2pSubnet(octets) && (octets[3].toInt() and 0xFF) == GROUP_OWNER_OCTET_3
        }

        /** 상대가 그룹의 손님인가. 그룹 오너 자신(`.1`)은 폰 안의 앱이므로 거절한다. */
        private fun isGroupClientAddress(address: InetAddress?): Boolean {
            val octets = address?.asIpv4()?.address ?: return false
            if (!inP2pSubnet(octets)) return false
            val host = octets[3].toInt() and 0xFF
            return host != GROUP_OWNER_OCTET_3 && host != NETWORK_OCTET_3 && host != BROADCAST_OCTET_3
        }

        /** 앞 세 옥텟이 192.168.49 인가. */
        private fun inP2pSubnet(octets: ByteArray): Boolean =
            (octets[0].toInt() and 0xFF) == P2P_OCTET_0 &&
                (octets[1].toInt() and 0xFF) == P2P_OCTET_1 &&
                (octets[2].toInt() and 0xFF) == P2P_OCTET_2
    }
}
