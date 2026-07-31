package com.example.usbtether

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * IPv4-mapped IPv6(`::ffff:a.b.c.d`)를 벗겨 IPv4 로 정규화한다.
 *
 * 듀얼스택 소켓은 보통 IPv4 상대에 대해 [Inet4Address] 를 돌려주지만, 매핑된 형태가
 * 나타나는 스택도 있다(이 폰의 `ss` 출력에도 `[::ffff:192.168.49.1]` 이 보인다).
 * 주소 기반 판정을 하는 곳에서는 이 정규화를 거치지 않으면 매핑 형태로 우회당한다.
 *
 * [PeerFilter] 와 [DestinationFilter] 가 공유한다.
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
