package io.github.donggi.iroirobrowser.tether.core

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * IPv4-mapped IPv6(`::ffff:a.b.c.d`)를 벗겨 IPv4 로 정규화한다.
 *
 * 듀얼스택 소켓은 보통 IPv4 상대에 [Inet4Address] 를 돌려주지만, 매핑된 꼴이 나오는 스택도 있다(usb-tether 때 실기기의 `ss` 출력에
 * `[::ffff:192.168.49.1]` 이 보였다). 주소로 판정하는 곳은 이 정규화를 거치지 않으면 매핑된 꼴로 비켜 간다.
 *
 * [PeerFilter] 와 [DestinationFilter] 가 함께 쓴다. **일부러 좁다** — `DestinationFilter` 는 IPv6 에 IPv4 를 실은 다른 꼴(`::/96`·NAT64·
 * 6to4)을 앞머리째 막는다. 이 함수를 넓히면 `PeerFilter` 가 받아 주는 것까지 넓어진다.
 *
 * @return IPv4 주소. 진짜 IPv6 주소면 null
 */
internal fun InetAddress.asIpv4(): Inet4Address? = when (this) {
    is Inet4Address -> this
    is Inet6Address -> {
        val bytes = address
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
