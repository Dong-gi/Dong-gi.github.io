package com.example.usbtether

import java.net.Inet4Address
import java.net.Inet6Address
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
 * 소켓 하나로 루프백(USB 모드의 `adb forward` 대상)과 P2P 그룹 주소를 동시에 바인딩할
 * 수는 없고, P2P 주소는 그룹 오너가 떠 있는 동안에만 존재한다. 수명이 다른 소켓 두 개를
 * 관리하는 대신, 와일드카드 소켓을 유지하고 accept 시점에 원치 않는 상대를 거부한다.
 *
 * ## 이것으로 막지 못하는 것
 *
 * USB 경로 때문에 루프백은 계속 허용해야 하므로, 이 폰의 다른 앱은 여전히
 * `127.0.0.1` 로 프록시에 도달할 수 있다. 그 앱들은 VpnService 기반 방화벽과 UID 별
 * 데이터 제한을 우회하게 된다(트래픽이 이 앱에 귀속된다). 이를 막으려면 프록시 자체에
 * 인밴드 인증이 필요하다. 대신 적용되는 완화책은 [DestinationFilter] 다 — 그 앱들이
 * 우리를 통해 루프백 서비스나 사설 LAN 에 도달하지는 못한다.
 */
object PeerFilter {

    /** Android 가 Wi-Fi Direct 그룹 오너에게 할당하는 대역: 192.168.49.0/24 */
    private const val P2P_OCTET_0 = 192
    private const val P2P_OCTET_1 = 168
    private const val P2P_OCTET_2 = 49

    /**
     * [peer] 가 서비스해도 되는 클라이언트인지 판정한다.
     *
     * 허용:
     *  - 루프백(IPv4/IPv6) — USB 경로. `adb forward` 가 폰 자신의 루프백을 대상으로 한다
     *  - `192.168.49.0/24` — Wi-Fi Direct 그룹
     *
     * 그 외는 모두 거부한다. 특히 일반 Wi-Fi LAN 의 주소, 셀룰러 인터페이스,
     * 루프백이 아닌 모든 IPv6 가 거부된다.
     */
    fun isAllowed(peer: InetAddress?): Boolean {
        if (peer == null) return false
        if (peer.isLoopbackAddress) return true

        val v4 = toIpv4(peer) ?: return false
        val octets = v4.address
        return (octets[0].toInt() and 0xFF) == P2P_OCTET_0 &&
            (octets[1].toInt() and 0xFF) == P2P_OCTET_1 &&
            (octets[2].toInt() and 0xFF) == P2P_OCTET_2
    }

    /**
     * 주소를 IPv4 로 정규화한다. IPv4-mapped IPv6(`::ffff:a.b.c.d`)는 벗겨낸다.
     *
     * 듀얼스택 소켓은 보통 IPv4 상대에 대해 [Inet4Address] 를 돌려주지만, 매핑된 형태가
     * 나타나는 스택도 있다(이 폰의 `ss` 출력에도 `[::ffff:192.168.49.1]` 이 보인다).
     * 그러니 가정하지 말고 명시적으로 벗겨낸다.
     *
     * @return IPv4 주소. [address] 가 진짜 IPv6 주소면 null
     */
    private fun toIpv4(address: InetAddress): Inet4Address? = when (address) {
        is Inet4Address -> address
        is Inet6Address -> {
            val bytes = address.address
            // ::ffff:0:0/96 — 앞 10바이트가 0, 그다음 0xFF 0xFF, 마지막 4바이트가 IPv4
            val mapped = bytes.size == 16 &&
                (0 until 10).all { bytes[it] == 0.toByte() } &&
                bytes[10] == 0xFF.toByte() && bytes[11] == 0xFF.toByte()
            if (mapped) {
                InetAddress.getByAddress(bytes.copyOfRange(12, 16)) as? Inet4Address
            } else {
                null
            }
        }
        else -> null
    }
}
