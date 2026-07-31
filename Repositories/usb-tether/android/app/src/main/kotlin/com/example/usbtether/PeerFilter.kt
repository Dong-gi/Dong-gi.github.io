package com.example.usbtether

import java.net.InetAddress

/**
 * 프록시를 사용할 수 있는 클라이언트를 판별한다.
 *
 * ## 왜 필요한가
 *
 * `ServerSocket(port, backlog, InetAddress.getByName("0.0.0.0"))` 은 IPv4 전용으로
 * 바인딩되지 **않는다**. IPv6 가 가용한 JVM 에서는 OpenJDK 가 `AF_INET6` 소켓을 만들고
 * IPv4 와일드카드(`INADDR_ANY`)를 `in6addr_any`(`::`)로 변환한다. 실기기에서 확인했다.
 *
 * ```
 * $ adb shell ss -ltn
 * LISTEN 0 0   *:1080   *:*      <- 0.0.0.0:1080 이 아니라 듀얼스택 와일드카드
 * LISTEN 0 0   *:8282   *:*
 * ```
 *
 * 즉 두 프록시가 폰이 가진 **모든** 인터페이스에서 도달 가능했다. 폰이 우연히 접속해
 * 있는 일반 Wi-Fi(카페·사무실·집)도 포함되고, 통신사가 전역 라우팅되는 IPv6 를
 * 부여하면 인터넷에서도 도달한다. Wi-Fi Direct 의 WPA2 패스프레이즈는 이 경로들을
 * 전혀 보호하지 못한다.
 *
 * ## 두 가지를 함께 본다
 *
 * 1. **연결이 도착한 로컬 주소**가 그룹 오너 주소(`192.168.49.1`)인가
 * 2. **상대 주소**가 그 대역의 클라이언트(`192.168.49.2`–`192.168.49.254`)인가
 *
 * 1번이 실질적인 인터페이스 확인이다. 상대 주소만 보면 대역이 "P2P 링크로 들어왔다"
 * 의 **대리 지표**일 뿐이고, 다른 네트워크가 같은 대역을 쓰면 그 대리 관계가 깨진다.
 * 폰이 접속한 일반 Wi-Fi 의 DHCP 가 `192.168.49.0/24` 를 쓰면 그 LAN 의
 * `192.168.49.5` 기기가 통과해 버린다. 소비자 공유기에 흔한 설정은 아니지만, 접속하는
 * Wi-Fi 를 통제하는 쪽이 테더링 앱을 노려 서브넷을 그렇게 맞추는 것은 비용이 거의
 * 들지 않는다(호텔·카페·사무실 Wi-Fi).
 *
 * 로컬 주소까지 보면 **핫스팟이 꺼져 있는 동안에는 어떤 연결도 통과할 수 없다.**
 * `192.168.49.1` 이 어느 인터페이스에도 없으므로 그 값이 로컬 주소가 될 수 없다.
 * 프록시 서비스를 상시 실행해도 안전한 근거가 코드로 보장된다.
 *
 * 남는 좁은 경우는 공격자가 폰에 `192.168.49.1` 을 직접 할당하고 동시에 같은 대역의
 * 클라이언트를 붙이는 것이다. `NetworkInterface.getByInetAddress()` 로 인터페이스
 * 이름이 `p2p` 로 시작하는지까지 볼 수 있지만, 연결마다 시스템 호출이 들어가고
 * 인터페이스 명명 규칙에 의존하게 되므로 여기서는 하지 않는다.
 *
 * ## 허용 범위
 *
 * `192.168.49.1` (그룹 오너 = 이 폰 자신)은 상대로서는 **거부**한다. 폰 내부의 다른
 * 앱이 그 주소로 접속하면 커널이 소스 주소로 같은 인터페이스 주소를 고르므로, 이
 * 조건이 곧 "폰 내부 앱 차단"이 된다. 프록시는 다른 앱의 `INTERNET` 권한을 세탁해
 * VpnService 기반 방화벽과 UID 별 데이터 제한을 우회시키는 통로가 될 수 있다.
 *
 * **미검증 가정**: 로컬 목적지로 connect 할 때 커널이 소스 주소로 그 인터페이스
 * 주소를 고른다는 동작에 의존한다. Linux 의 표준 동작이지만 실기기에서
 * `adb shell` 로 확인하는 편이 좋다.
 *
 * 루프백은 허용하지 않는다. ADB 포트 포워딩을 쓰던 USB 경로가 제거되면서 루프백을
 * 열어둘 이유가 없어졌다.
 */
object PeerFilter {

    /** Android 가 Wi-Fi Direct 그룹에 할당하는 대역: 192.168.49.0/24 */
    private const val P2P_OCTET_0 = 192
    private const val P2P_OCTET_1 = 168
    private const val P2P_OCTET_2 = 49

    /** 그룹 오너(이 폰)의 마지막 옥텟. */
    private const val GROUP_OWNER_OCTET_3 = 1

    /** 네트워크 주소와 브로드캐스트 주소. 유효한 클라이언트가 아니다. */
    private const val NETWORK_OCTET_3 = 0
    private const val BROADCAST_OCTET_3 = 255

    /**
     * 이 연결을 서비스해도 되는지 판정한다.
     *
     * @param peer 상대 주소 (`Socket.getInetAddress()`)
     * @param localAddress 연결이 도착한 로컬 주소 (`Socket.getLocalAddress()`)
     */
    fun isAllowed(peer: InetAddress?, localAddress: InetAddress?): Boolean =
        isGroupOwnerAddress(localAddress) && isGroupClientAddress(peer)

    /**
     * 연결이 Wi-Fi Direct 그룹 오너 주소로 도착했는지.
     *
     * 핫스팟이 꺼져 있으면 `192.168.49.1` 이 어느 인터페이스에도 없으므로 항상 false 다.
     */
    private fun isGroupOwnerAddress(address: InetAddress?): Boolean {
        val octets = address?.asIpv4()?.address ?: return false
        return inP2pSubnet(octets) && (octets[3].toInt() and 0xFF) == GROUP_OWNER_OCTET_3
    }

    /**
     * 상대가 그룹의 유효한 클라이언트인지.
     *
     * 그룹 오너 자신(`.1`)은 폰 내부 앱을 뜻하므로 거부한다.
     */
    private fun isGroupClientAddress(address: InetAddress?): Boolean {
        val octets = address?.asIpv4()?.address ?: return false
        if (!inP2pSubnet(octets)) return false

        val host = octets[3].toInt() and 0xFF
        return host != GROUP_OWNER_OCTET_3 &&
            host != NETWORK_OCTET_3 &&
            host != BROADCAST_OCTET_3
    }

    /** 앞 세 옥텟이 192.168.49 인지. */
    private fun inP2pSubnet(octets: ByteArray): Boolean =
        (octets[0].toInt() and 0xFF) == P2P_OCTET_0 &&
            (octets[1].toInt() and 0xFF) == P2P_OCTET_1 &&
            (octets[2].toInt() and 0xFF) == P2P_OCTET_2
}
