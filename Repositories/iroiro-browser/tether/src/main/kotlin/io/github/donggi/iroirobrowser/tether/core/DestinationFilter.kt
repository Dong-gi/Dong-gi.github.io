package io.github.donggi.iroirobrowser.tether.core

import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections

/** 목적지 판정의 결과. */
sealed interface Destination {
    /** 통과. **[address] 로 연결해야 한다** — 이름을 다시 풀면 DNS 재바인딩에 노출된다. */
    data class Allowed(val address: InetAddress) : Destination

    /** 막는 곳으로만 풀렸다. [reason] 은 기록용이고 호스트 이름을 싣지 않는다. */
    data class Rejected(val reason: String) : Destination

    /**
     * 이름을 풀지 못했다(없는 이름·DNS 실패·결과 없음) — **막은 것이 아니다.** HTTP 는 502, SOCKS5 는 '호스트에 닿지 못함'
     * (예전에는 거절과 같이 403·'규칙이 허락하지 않음' 이라 오타 난 주소가 프록시의 막기처럼 보였다). [reason] 은 기록용.
     */
    data class Unresolved(val reason: String) : Destination
}

/** 프록시가 손님 대신 이어도 되는 목적지 — 서버는 이것으로만 묻는다(시험은 루프백을 허락하는 것으로 바꾼다. 실제 판정은 [DestinationFilter]). */
interface DestinationPolicy {
    /** 이름(또는 주소 글)을 **한 번** 풀어 걸러진 주소를 돌려준다. */
    fun resolve(host: String): Destination

    /** 이미 주소인 목적지(UDP 의 IP 리터럴)를 막는가. */
    fun isBlocked(address: InetAddress): Boolean
}

/** 이 폰의 인터페이스에 붙은 주소 하나(접두 길이와 함께) — [DestinationFilter] 가 폰 자신과 그 LAN 을 막는 데 쓴다. */
class LocalAddress(val interfaceName: String, val address: InetAddress, val prefixLength: Int)

/**
 * 프록시가 연결해도 되는 목적지를 가른다 — 손님의 트래픽이 폰 자신과 폰이 붙은 LAN 에 닿지 못하게.
 *
 * ## 왜 필요한가
 *
 * usb-tether 의 첫 판은 손님이 준 호스트·포트로 그대로 연결했다. 그래서 프록시가
 *
 *  - **폰의 `127.0.0.1`** 에 닿았다. 루프백에만 묶인 서비스는 '같은 기기' 를 믿고 인증을 빼는 일이 많다(그때 기기에서 잰 `ss -ltn` 에
 *    `127.0.0.1:53` 등이 있었다). 서로의 프록시(`CONNECT 127.0.0.1:8282`)도 들어 있어 한쪽만 막으면 사슬로 비켜 간다. Web 에 합친 뒤로는
 *    브라우저의 중계(`127.0.0.1` 의 무작위 포트 — 세션 계정이 필요하다)도 여기에 든다.
 *  - **폰이 붙은 LAN** 에 닿았다 — `CONNECT 192.168.1.1:80` 으로 공유기 관리 페이지·NAS·프린터·IoT. Wi-Fi Direct 망에서 사용자의 집·회사 LAN
 *    으로 넘어가는 다리가 된다.
 *
 * ## 무엇을 막는가
 *
 *  1. **고정 대역**([isBlockedAddress]) — 사설·로컬·예약 대역. 주소만 보고 정한다.
 *  2. **폰 자신의 주소**(모든 인터페이스의 주소 그대로) — 셀룰러가 준 공인 IPv4·IPv6 로 붙으면 폰의 서비스(이 서버들까지)에 닿는다. 고정 대역은
 *     사설 주소만 막아 이것을 몰랐다.
 *  3. **LAN 쪽 인터페이스의 on-link 접두**(Wi-Fi·이더넷·Wi-Fi Direct·모바일 핫스팟 — [LAN_INTERFACE_PREFIXES]) — 집의 IPv6 는 공인 주소라
 *     고정 대역에 걸리지 않는다(같은 /64 의 NAS·프린터). 접두 길이는 인터페이스가 아는 값이다. IPv6 에서 /48 보다 넓은 것은 버린다 — 이상한
 *     값 하나로 인터넷의 큰 덩이를 막지 않게(IPv4 는 /8 보다 넓은 것). **셀룰러(rmnet·ccmni 등)의 접두는 막지 않는다** — 그 /64 는 이 기기
 *     하나의 것이고, 막으면 인터넷이 깨질 수 있다(통신사의 망 안 주소). 정확한 자기 주소만 막는다(2).
 *
 * 2·3 의 인터페이스 목록은 주입하고(시험) [LOCAL_CACHE_MS] 동안 기억한다 — 연결·UDP 데이터그램마다 시스템 호출을 하지 않으려고. 그 사이에 망이
 * 바뀌면 몇 초 동안 새 LAN 의 접두를 모른다. 목록을 읽지 못하면 앞의 것을 쓴다(처음이면 고정 대역만 — 테더를 통째로 막지 않는다).
 *
 * **한계**: on-link 접두 밖의 LAN(같은 공유기가 나눠 준 다른 서브넷, 공인 IPv4 로 된 회사망의 다른 대역)은 모른다.
 *
 * ## DNS 재바인딩
 *
 * 이름을 한 번 풀어 통과시킨 뒤 문자열로 다시 연결하면 다시 풀려 검사를 비켜 갈 수 있다(TTL 0 으로 두 번째 답만 사설 주소를 주는 공격).
 * 그래서 [resolve] 는 통과한 **주소 객체**를 돌려주고, 부르는 쪽은 반드시 그것으로 연결한다.
 *
 * ## 포트는 막지 않는다(뜻한 것)
 *
 * PC 한 대의 트래픽을 통째로 지나게 하는 테더링이라 포트 허용 목록(80/443 등)을 두면 메일·SSH·게임이 모두 깨진다. 25번(스팸 중계)도
 * 생각했지만 폰의 앱은 이미 `INTERNET` 권한으로 25번에 바로 붙을 수 있어 얻는 것이 적다. 위험의 본질은 포트가 아니라 **닿는 범위**라 주소로
 * 막는다.
 *
 * 토렌트 엔진의 `AddressFilter`(남이 준 피어 주소 거르기)와 대역 목록이 다르다 — 그쪽은 1024 밑 포트까지 막는다.
 *
 * @param resolver 이름 풀이(시험은 가짜를 준다 — 재바인딩·사설 주소만 있는 이름). 실제는 [SYSTEM].
 * @param localAddresses 이 폰의 인터페이스 주소(시험은 주입한다). 읽지 못하면 던져도 된다.
 * @param nowMs 단조 시계(밀리초) — 목록을 기억하는 시간.
 */
class DestinationFilter(
    private val resolver: (String) -> Array<InetAddress>,
    private val localAddresses: () -> List<LocalAddress> = { systemLocalAddresses() },
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 },
) : DestinationPolicy {

    /** 폰 자신의 주소와 LAN 의 접두 — 서버 셋의 일꾼이 함께 읽으므로 통째로 갈아 끼운다. */
    @Volatile
    private var local: LocalRules? = null

    /**
     * 목적지를 풀고 거른다. 여러 주소로 풀리면 막히지 않는 첫 주소를 고른다. 모두 막히면 거절, 풀지 못하면 [Destination.Unresolved].
     *
     * @param host IPv4·IPv6 리터럴 또는 이름
     */
    override fun resolve(host: String): Destination {
        val candidates = try {
            resolver(host)
        } catch (_: Exception) {
            return Destination.Unresolved("이름 풀이 실패")
        }
        if (candidates.isEmpty()) return Destination.Unresolved("풀이 결과 없음")
        candidates.firstOrNull { !isBlocked(it) }?.let { return Destination.Allowed(it) }
        return Destination.Rejected("로컬·사설 대역·이 폰과 그 LAN")
    }

    override fun isBlocked(address: InetAddress): Boolean = isBlockedAddress(address) || localRules().blocks(address)

    private fun localRules(): LocalRules {
        val now = nowMs()
        local?.let { if (now - it.atMs in 0 until LOCAL_CACHE_MS) return it }
        val fresh = try {
            LocalRules.of(localAddresses(), now)
        } catch (_: Exception) {
            // 읽지 못했다 — 앞의 것을 쓰고(없으면 비어 있는 것) 다음에 다시 묻는다.
            return local ?: LocalRules.EMPTY
        }
        local = fresh
        return fresh
    }

    /** 폰 자신의 주소(그대로)와 막을 접두. 주소는 바이트로 견준다(IPv6 의 범위 ID 를 보지 않는다). */
    private class LocalRules(val atMs: Long, val exact: List<ByteArray>, val prefixes: List<Prefix>) {

        fun blocks(address: InetAddress): Boolean {
            val bytes = (address.asIpv4() ?: address).address
            if (exact.any { it.contentEquals(bytes) }) return true
            return prefixes.any { it.contains(bytes) }
        }

        companion object {
            val EMPTY = LocalRules(Long.MIN_VALUE, emptyList(), emptyList())

            fun of(addresses: List<LocalAddress>, atMs: Long): LocalRules {
                val exact = ArrayList<ByteArray>()
                val prefixes = ArrayList<Prefix>()
                for (local in addresses) {
                    val bytes = (local.address.asIpv4() ?: local.address).address
                    exact += bytes
                    if (isLanInterface(local.interfaceName) && isSaneOnLinkPrefix(bytes.size, local.prefixLength)) {
                        prefixes += Prefix(bytes, local.prefixLength)
                    }
                }
                return LocalRules(atMs, exact, prefixes)
            }
        }
    }

    /** [network] 의 앞 [length] 비트. 같은 꼴(IPv4·IPv6)의 주소만 견준다. */
    private class Prefix(private val network: ByteArray, private val length: Int) {
        fun contains(address: ByteArray): Boolean {
            if (address.size != network.size) return false
            val whole = length / 8
            for (i in 0 until whole) if (address[i] != network[i]) return false
            val rest = length % 8
            if (rest == 0) return true
            val mask = (0xFF shl (8 - rest)) and 0xFF
            return (address[whole].toInt() and mask) == (network[whole].toInt() and mask)
        }
    }

    companion object {
        /** 기기의 이름 풀이와 인터페이스를 쓰는 실제 거르개. */
        val SYSTEM = DestinationFilter({ InetAddress.getAllByName(it) })

        /** 인터페이스 목록을 기억하는 시간. */
        const val LOCAL_CACHE_MS = 5_000L

        /**
         * on-link 접두를 막는 인터페이스 이름의 앞머리 — Wi-Fi(`wlan`)·이더넷(`eth`)·Wi-Fi Direct(`p2p`)·모바일 핫스팟(`swlan`·`ap`·`softap`)·
         * USB·블루투스 테더링(`rndis`·`usb`·`ncm`·`bt-pan`)·Wi-Fi Aware(`aware`). 셀룰러(`rmnet`·`ccmni`·`seth` 등)·VPN(`tun`)은 넣지 않는다
         * (클래스 머리말).
         */
        val LAN_INTERFACE_PREFIXES = listOf("wlan", "eth", "p2p", "swlan", "ap", "softap", "rndis", "usb", "ncm", "bt-pan", "aware")

        /** IPv6 접두가 이보다 넓으면(짧으면) 막지 않는다. */
        const val MIN_IPV6_PREFIX = 48

        /** IPv4 접두가 이보다 넓으면 막지 않는다. */
        const val MIN_IPV4_PREFIX = 8

        private fun isLanInterface(name: String): Boolean = LAN_INTERFACE_PREFIXES.any { name.startsWith(it) }

        private fun isSaneOnLinkPrefix(addressBytes: Int, prefixLength: Int): Boolean = when (addressBytes) {
            4 -> prefixLength in MIN_IPV4_PREFIX..32
            16 -> prefixLength in MIN_IPV6_PREFIX..128
            else -> false
        }

        /** 기기의 인터페이스 주소 — 접두 길이와 함께. */
        fun systemLocalAddresses(): List<LocalAddress> {
            val out = ArrayList<LocalAddress>()
            val all = NetworkInterface.getNetworkInterfaces() ?: return out
            for (nif in Collections.list(all)) {
                for (ia in nif.interfaceAddresses) {
                    val address = ia.address ?: continue
                    out += LocalAddress(nif.name, address, ia.networkPrefixLength.toInt())
                }
            }
            return out
        }

        /**
         * 막는 고정 대역인가(주소만 보고 — 폰 자신의 주소와 LAN 의 접두는 인스턴스가 따로 본다).
         *
         *  - **IPv4** — 와일드카드(`0.0.0.0`)·루프백·링크 로컬(`169.254.0.0/16`)·사설(RFC 1918)·멀티캐스트·`0.0.0.0/8`·CGNAT(`100.64.0.0/10`)·
         *    IETF 프로토콜용 `192.0.0.0/24`(464XLAT 의 `192.0.0.4` 등 — 이 폰의 CLAT 이 거기 있다)·벤치마크 `198.18.0.0/15`·예약 `240.0.0.0/4`
         *    (제한 브로드캐스트 `255.255.255.255` 포함)
         *  - **IPv6** — 와일드카드(`::`)·루프백(`::1`)·링크 로컬(`fe80::/10`)·멀티캐스트·ULA(`fc00::/7`)·문서용 `2001:db8::/32`
         *  - **IPv6 에 IPv4 를 실은 꼴** — IPv4-mapped(`::ffff:a.b.c.d`)는 벗겨 IPv4 규칙을 다시 보고, IPv4-compatible(`::/96`)·
         *    NAT64(`64:ff9b::/32`)·6to4(`2002::/16`)는 앞머리째 막는다
         */
        fun isBlockedAddress(address: InetAddress): Boolean {
            if (address.isAnyLocalAddress) return true
            if (address.isLoopbackAddress) return true
            if (address.isLinkLocalAddress) return true
            if (address.isSiteLocalAddress) return true
            if (address.isMulticastAddress) return true

            // IPv4-mapped IPv6 로 비켜 갈 수 있으므로 벗긴 뒤 IPv4 규칙을 다시 본다. 지금의 JDK·안드로이드는 `::ffff:a.b.c.d` 를 읽으면
            // Inet4Address 를 돌려주어 위의 검사가 이미 거른다 — 다른 스택이 Inet6Address 를 돌려줄 때를 위해 여기서도 모두 본다.
            val v4 = address.asIpv4()
            if (v4 != null) {
                if (v4.isAnyLocalAddress) return true
                if (v4.isLoopbackAddress) return true
                if (v4.isLinkLocalAddress) return true
                if (v4.isSiteLocalAddress) return true
                if (v4.isMulticastAddress) return true
                val a = v4.address[0].toInt() and 0xFF
                val b = v4.address[1].toInt() and 0xFF
                val c = v4.address[2].toInt() and 0xFF
                if (a == 0) return true // 0.0.0.0/8
                if (a == 100 && b in 64..127) return true // 100.64.0.0/10 CGNAT
                if (a == 192 && b == 0 && c == 0) return true // 192.0.0.0/24 — IETF 프로토콜용(464XLAT 의 192.0.0.4 등)
                if (a == 198 && (b == 18 || b == 19)) return true // 198.18.0.0/15 — 벤치마크
                // 240.0.0.0/4 — 예약(옛 class E). 제한 브로드캐스트 255.255.255.255 가 여기 든다 — 위의 어느 검사에도 걸리지 않는다.
                // DatagramSocket 은 SO_BROADCAST 가 기본으로 켜져 있어 UDP 중계가 폰이 붙은 LAN 에 브로드캐스트를 뿌릴 수 있었다.
                if (a >= 240) return true
                return false
            }

            if (address is Inet6Address) {
                val bytes = address.address
                // fc00::/7 (ULA) — 자바에 판정 함수가 없다.
                if (bytes[0].toInt() and 0xFE == 0xFC) return true
                // 2001:db8::/32 — 문서용(RFC 3849). 인터넷에 없는 대역이라 닿는다면 이 폰이 붙은 망 안의 무엇이다.
                if (bytes[0] == 0x20.toByte() && bytes[1] == 0x01.toByte() && bytes[2] == 0x0D.toByte() && bytes[3] == 0xB8.toByte()) return true

                // 아래 셋은 IPv6 주소에 IPv4 를 실어 위의 IPv4 규칙을 건너뛰는 꼴이다. 실린 자리가 앞머리 길이마다 다르거나(NAT64) 닿는
                // 길이 중계기에 달려(6to4) 주소를 풀어 판정하지 않고 앞머리째 막는다. `64:ff9b::/32` 는 IPv6 전용 망에서 DNS64 가 실제로
                // 돌려주는 값이라 쓸모가 있지만, 손님은 IPv4 P2P 대역에 있고 스스로 이름을 푸는 손님(tun2proxy)은 IPv4 리터럴을 보낸다.
                if ((0 until 12).all { bytes[it] == 0.toByte() }) return true // ::/96 — IPv4-compatible(RFC 4291 2.5.5.1 에서 폐기)
                if (bytes[0] == 0x00.toByte() && bytes[1] == 0x64.toByte() &&
                    bytes[2] == 0xFF.toByte() && bytes[3] == 0x9B.toByte()
                ) return true // 64:ff9b::/32 — NAT64(RFC 6052 의 잘 알려진 앞머리 + RFC 8215 의 지역용)
                if (bytes[0] == 0x20.toByte() && bytes[1] == 0x02.toByte()) return true // 2002::/16 — 6to4(RFC 3056)
            }
            return false
        }
    }
}
