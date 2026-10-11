package io.github.donggi.iroirobrowser.torrent.net

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress

/** 그 주소를 무엇으로 쓰려는가 — 포트 규칙이 다르다. */
enum class Purpose {
    /** 트래커가·DHT 가·마그넷이 준 피어(TCP). */
    PEER,

    /** DHT 노드(UDP) — 부트스트랩 노드를 풀어 얻은 것도. */
    DHT_NODE,

    /** HTTP·UDP 트래커(이름을 풀어 얻은 주소, 넘겨주기의 홉마다). */
    TRACKER,

    /** 웹 시드(BEP 19·17 — 넘겨주기의 홉마다). */
    WEB_SEED,
}

/**
 * **남이 준 목적지**를 붙기 전에 거른다. 토렌트의 목적지(트래커·웹 시드·피어·DHT 노드)는 모두 남이 고른 것이다 — 걸러 내지 않으면
 * 남이 이 기기를 시켜 **사용자의 공유기·집 안 기기**(사설·루프백·링크 로컬)에 요청을 보내게 할 수 있다(사용자 결정 ⑨).
 * 이름은 엔진이 스스로 풀고, **푼 주소를 이것으로 거른 뒤 그 주소로** 붙는다(다시 풀지 않는다 — DNS 재바인딩).
 */
fun interface AddressFilter {
    fun allows(address: InetAddress, port: Int, purpose: Purpose): Boolean

    fun allows(target: InetSocketAddress, purpose: Purpose): Boolean {
        val a = target.address ?: return false
        return allows(a, target.port, purpose)
    }

    companion object {
        /** 공용 주소만(아래 [PublicAddresses]). 앱은 늘 이것을 쓴다. */
        val PUBLIC: AddressFilter = AddressFilter { address, port, purpose -> PublicAddresses.allows(address, port, purpose) }
    }
}

/**
 * 공용 인터넷 주소인가. 거르는 대역(RFC 6890 의 특수 목적 대역과 IANA 의 예약 대역에서 골랐다):
 *
 * - IPv4 — 0/8 · 10/8 · 100.64/10(통신사 NAT) · 127/8 · 169.254/16(링크 로컬 — 클라우드의 메타데이터 169.254.169.254 포함) ·
 *   172.16/12 · 192.0.0/24 · 192.0.2/24 · 192.88.99/24 · 192.168/16 · 198.18/15 · 198.51.100/24 · 203.0.113/24 · 224/4(멀티캐스트) ·
 *   240/4(예약·방송 255.255.255.255).
 * - IPv6 — `::`·`::1` · `::/96`(옛 IPv4 호환) · `::ffff:0:0/96`(IPv4-mapped — 안의 IPv4 를 같은 규칙으로) · `64:ff9b::/96`(NAT64 — 안의
 *   IPv4 로) · `64:ff9b:1::/48` · `100::/64` · `2001:db8::/32`·`3fff::/20`(문서) · `2001:2::/48` · `2001:10::/28`·`2001:20::/28`(ORCHID) ·
 *   `2002::/16`(6to4 — 안의 IPv4 로) · `5f00::/16` · `fc00::/7`(ULA) · `fe80::/10`·`fec0::/10` · `ff00::/8`(멀티캐스트).
 *
 * 포트 — 0 은 늘 거른다. 피어·DHT 노드는 **1024 밑을 거른다**(군집이 남의 웹·메일 서버로 연결을 몰아가는 반사 공격에 쓰이지 않게 —
 * 정상 피어가 그런 포트를 쓰는 일은 드물다). 트래커·웹 시드는 HTTP 라 80·443 을 쓴다.
 */
object PublicAddresses {

    fun allows(address: InetAddress, port: Int, purpose: Purpose): Boolean {
        if (port !in 1..65535) return false
        if ((purpose == Purpose.PEER || purpose == Purpose.DHT_NODE) && port < 1024) return false
        return isPublic(address)
    }

    fun isPublic(address: InetAddress): Boolean = when (address) {
        is Inet4Address -> isPublicV4(address.address)
        is Inet6Address -> isPublicV6(address.address)
        else -> false
    }

    fun isPublicV4(b: ByteArray): Boolean {
        val a0 = b[0].toInt() and 0xFF
        val a1 = b[1].toInt() and 0xFF
        val a2 = b[2].toInt() and 0xFF
        return when {
            a0 == 0 || a0 == 10 || a0 == 127 -> false
            a0 == 100 && a1 in 64..127 -> false
            a0 == 169 && a1 == 254 -> false
            a0 == 172 && a1 in 16..31 -> false
            a0 == 192 && a1 == 0 && (a2 == 0 || a2 == 2) -> false
            a0 == 192 && a1 == 88 && a2 == 99 -> false
            a0 == 192 && a1 == 168 -> false
            a0 == 198 && (a1 == 18 || a1 == 19) -> false
            a0 == 198 && a1 == 51 && a2 == 100 -> false
            a0 == 203 && a1 == 0 && a2 == 113 -> false
            a0 >= 224 -> false
            else -> true
        }
    }

    fun isPublicV6(b: ByteArray): Boolean {
        val w = IntArray(8) { ((b[2 * it].toInt() and 0xFF) shl 8) or (b[2 * it + 1].toInt() and 0xFF) }
        // ::/96 (:: · ::1 · 옛 IPv4 호환) 과 ::ffff:0:0/96 (IPv4-mapped)
        if (w[0] == 0 && w[1] == 0 && w[2] == 0 && w[3] == 0 && w[4] == 0) {
            if (w[5] == 0xFFFF) return isPublicV4(b.copyOfRange(12, 16))
            return false
        }
        if (w[0] == 0x64 && w[1] == 0xff9b) {
            if (w[2] == 0 && w[3] == 0 && w[4] == 0 && w[5] == 0) return isPublicV4(b.copyOfRange(12, 16)) // 64:ff9b::/96
            if (w[2] == 1) return false // 64:ff9b:1::/48 (지역 NAT64)
        }
        if (w[0] == 0x100 && w[1] == 0 && w[2] == 0 && w[3] == 0) return false // 100::/64
        if (w[0] == 0x2001) {
            if (w[1] == 0xdb8) return false // 문서
            if (w[1] == 2 && w[2] == 0) return false // 2001:2::/48 (벤치마크)
            if (w[1] in 0x10..0x2f) return false // 2001:10::/28 · 2001:20::/28 (ORCHID)
        }
        if (w[0] == 0x2002) return isPublicV4(b.copyOfRange(2, 6)) // 6to4
        if ((w[0] and 0xFFF0) == 0x3ff0) return false // 3fff::/20 (문서)
        if (w[0] == 0x5f00) return false
        if ((w[0] and 0xFE00) == 0xFC00) return false // ULA
        if ((w[0] and 0xFFC0) == 0xFE80 || (w[0] and 0xFFC0) == 0xFEC0) return false // 링크 로컬·옛 사이트 로컬
        if ((w[0] and 0xFF00) == 0xFF00) return false // 멀티캐스트
        return true
    }
}
