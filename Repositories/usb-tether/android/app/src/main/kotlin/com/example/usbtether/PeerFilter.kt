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
 * ## 허용 범위
 *
 * Wi-Fi Direct 그룹의 클라이언트, 즉 `192.168.49.2` ~ `192.168.49.254` 만 허용한다.
 *
 * `192.168.49.1` (그룹 오너 = 이 폰 자신)은 **거부**한다. 폰 내부의 다른 앱이 그
 * 주소로 접속하면 커널이 소스 주소로 같은 인터페이스 주소를 고르므로, 이 조건이
 * 곧 "폰 내부 앱 차단"이 된다. 프록시는 다른 앱의 `INTERNET` 권한을 세탁해
 * VpnService 기반 방화벽과 UID 별 데이터 제한을 우회시키는 통로가 될 수 있으므로
 * 막아야 한다.
 *
 * **미검증 가정**: 로컬 목적지로 connect 할 때 커널이 소스 주소로 그 인터페이스
 * 주소를 고른다는 동작에 의존한다. Linux 의 표준 동작이지만 실기기에서
 * `adb shell` 로 확인하는 편이 좋다.
 *
 * 루프백은 허용하지 않는다. ADB 포트 포워딩을 쓰던 USB 경로가 제거되면서 루프백을
 * 열어둘 이유가 없어졌다.
 *
 * 소켓을 `192.168.49.1` 에만 바인딩하면 이 필터 없이도 다른 네트워크가 차단되지만,
 * 그 주소는 그룹 오너가 떠 있는 동안에만 존재해 프록시와 핫스팟의 수명을 묶어야 한다.
 * 지금은 와일드카드 소켓을 유지하고 accept 시점에 거부한다.
 */
object PeerFilter {

    /** Android 가 Wi-Fi Direct 그룹 오너에게 할당하는 대역: 192.168.49.0/24 */
    private const val P2P_OCTET_0 = 192
    private const val P2P_OCTET_1 = 168
    private const val P2P_OCTET_2 = 49

    /** 그룹 오너(이 폰) 자신의 마지막 옥텟. 폰 내부 앱의 접속을 뜻하므로 거부한다. */
    private const val GROUP_OWNER_OCTET_3 = 1

    /**
     * [peer] 가 서비스해도 되는 클라이언트인지 판정한다.
     *
     * 허용: `192.168.49.2` ~ `192.168.49.254` (Wi-Fi Direct 클라이언트)
     *
     * 거부: 그룹 오너 자신(`192.168.49.1`, 폰 내부 앱), 루프백, 일반 Wi-Fi LAN,
     * 셀룰러 인터페이스, 모든 IPv6.
     */
    fun isAllowed(peer: InetAddress?): Boolean {
        if (peer == null) return false

        val octets = peer.asIpv4()?.address ?: return false
        if ((octets[0].toInt() and 0xFF) != P2P_OCTET_0) return false
        if ((octets[1].toInt() and 0xFF) != P2P_OCTET_1) return false
        if ((octets[2].toInt() and 0xFF) != P2P_OCTET_2) return false

        val host = octets[3].toInt() and 0xFF
        // .0(네트워크 주소)과 .255(브로드캐스트)는 유효한 클라이언트가 아니다.
        return host != GROUP_OWNER_OCTET_3 && host != 0 && host != 255
    }
}
