package io.github.donggi.iroirobrowser.tether.core

import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 목적지 거르개 — 고정 대역(usb-tether 의 목록 + 문서·464XLAT·벤치마크 대역), **폰 자신의 주소와 LAN 쪽 인터페이스의 on-link 접두**,
 * '한 번 풀어 주소 객체로', 풀지 못한 이름은 거절이 아니다.
 */
class DestinationFilterTest {

    private fun ip(text: String): InetAddress = InetAddress.getByName(text)

    /** 이름은 [names] 로만 풀고(주소 글은 그대로 — DNS 를 쓰지 않는다), 인터페이스는 [interfaces] 로 주는 거르개. */
    private fun filter(
        interfaces: () -> List<LocalAddress> = { emptyList() },
        names: Map<String, List<String>> = emptyMap(),
        nowMs: () -> Long = { 0L },
    ) = DestinationFilter(
        resolver = { host ->
            names[host]?.map { ip(it) }?.toTypedArray()
                ?: if (host.contains(':') || host.all { it.isDigit() || it == '.' }) arrayOf(ip(host)) else throw java.net.UnknownHostException("시험")
        },
        localAddresses = interfaces,
        nowMs = nowMs,
    )

    @Test
    fun 폰_자신과_LAN_과_예약_대역의_IPv4_를_막는다() {
        val blocked = listOf(
            "0.0.0.0", "0.1.2.3", "0.255.255.255",
            "127.0.0.1", "127.255.255.254",
            "10.0.0.1", "10.255.255.255",
            "172.16.0.1", "172.31.255.255",
            "192.168.0.1", "192.168.49.1", "192.168.255.255",
            "169.254.169.254", "169.254.0.1",
            "100.64.0.0", "100.100.100.100", "100.127.255.255",
            "224.0.0.1", "239.255.255.250",
            "240.0.0.1", "250.1.2.3", "255.255.255.255",
        )
        for (text in blocked) assertTrue(DestinationFilter.isBlockedAddress(ip(text)), text)
    }

    @Test
    fun 문서_464XLAT_벤치마크_대역도_막는다() {
        // 192.0.0.0/24(464XLAT 의 CLAT 이 192.0.0.4 에 있다)·198.18.0.0/15·2001:db8::/32.
        for (text in listOf("192.0.0.0", "192.0.0.4", "192.0.0.255", "198.18.0.1", "198.19.255.255", "2001:db8::1", "2001:db8:ffff:1::5")) {
            assertTrue(DestinationFilter.isBlockedAddress(ip(text)), text)
        }
        for (text in listOf("192.0.1.0", "191.255.255.255", "198.17.255.255", "198.20.0.0", "2001:db9::1", "2001:db7::1")) {
            assertFalse(DestinationFilter.isBlockedAddress(ip(text)), text)
        }
    }

    @Test
    fun 공인_IPv4_는_통과한다_대역의_바로_바깥까지() {
        val allowed = listOf(
            "1.1.1.1", "8.8.8.8", "93.184.216.34",
            "11.0.0.1", "9.255.255.255",
            "172.15.255.255", "172.32.0.0",
            "192.167.255.255", "192.169.0.0",
            "100.63.255.255", "100.128.0.0",
            "169.253.255.255", "169.255.0.0",
            "223.255.255.255",
        )
        for (text in allowed) assertFalse(DestinationFilter.isBlockedAddress(ip(text)), text)
    }

    @Test
    fun IPv6_의_로컬과_IPv4_를_실은_꼴을_막는다() {
        val blocked = listOf(
            "::", "::1", "fe80::1", "febf::1", "fc00::1", "fd12:3456::1", "ff02::1",
            "::a00:1", // ::/96 — IPv4-compatible(10.0.0.1)
            "::808:808", // ::/96 은 공인 IPv4 를 실어도 앞머리째 막는다
            "64:ff9b::a00:1", "64:ff9b:1::1", // NAT64
            "2002:a00:1::1", "2002:808:808::1", // 6to4
        )
        for (text in blocked) assertTrue(DestinationFilter.isBlockedAddress(ip(text)), text)
        assertFalse(DestinationFilter.isBlockedAddress(ip("2606:4700:4700::1111")))
        assertFalse(DestinationFilter.isBlockedAddress(ip("2001:4860:4860::8888")))
    }

    @Test
    fun IPv4_mapped_는_벗겨_IPv4_규칙으로_본다_JDK_가_Inet4_로_바꾸지_않은_꼴도() {
        // 자바의 getByName 은 ::ffff:a.b.c.d 를 Inet4Address 로 바꾼다 — 바꾸지 않는 스택을 흉내 내려고 Inet6Address 를 직접 짓는다.
        fun mapped(vararg v4: Int): InetAddress = Inet6Address.getByAddress(
            null, ByteArray(10) + byteArrayOf(0xFF.toByte(), 0xFF.toByte()) + v4.map { it.toByte() }.toByteArray(), null as NetworkInterface?,
        )
        assertTrue(mapped(10, 0, 0, 1) is Inet6Address)
        assertTrue(DestinationFilter.isBlockedAddress(mapped(10, 0, 0, 1)))
        assertTrue(DestinationFilter.isBlockedAddress(mapped(127, 0, 0, 1)))
        assertTrue(DestinationFilter.isBlockedAddress(mapped(100, 64, 0, 1)))
        assertTrue(DestinationFilter.isBlockedAddress(mapped(255, 255, 255, 255)))
        assertTrue(DestinationFilter.isBlockedAddress(mapped(192, 0, 0, 4)))
        assertFalse(DestinationFilter.isBlockedAddress(mapped(8, 8, 8, 8)))
    }

    /** 폰이 집 Wi-Fi(공인 IPv4·IPv6 의 /64)와 셀룰러에 함께 붙었다. */
    private val phone = listOf(
        LocalAddress("wlan0", ip("81.2.3.4"), 24),
        LocalAddress("wlan0", ip("2a00:1450:1:2::abc"), 64),
        LocalAddress("rmnet_data0", ip("2600:380:1:2::5"), 64),
        LocalAddress("rmnet_data0", ip("27.100.1.2"), 30),
        LocalAddress("p2p-wlan0-0", ip("192.168.49.1"), 24),
        LocalAddress("lo", ip("127.0.0.1"), 8),
    )

    @Test
    fun 폰_자신의_주소는_어느_인터페이스든_막는다() {
        val f = filter({ phone })
        for (text in listOf("81.2.3.4", "2a00:1450:1:2::abc", "2600:380:1:2::5", "27.100.1.2")) {
            assertTrue(f.isBlocked(ip(text)), text)
            assertIs<Destination.Rejected>(f.resolve(text), text)
        }
        // 고정 대역만 보는 판정은 이 주소들을 몰랐다.
        assertFalse(DestinationFilter.isBlockedAddress(ip("2600:380:1:2::5")))
    }

    @Test
    fun LAN_쪽_인터페이스의_on_link_접두는_막고_셀룰러의_접두는_막지_않는다() {
        val f = filter({ phone })
        // 집 Wi-Fi 의 같은 망 — 공인 주소라 고정 대역에 걸리지 않던 NAS·프린터.
        for (text in listOf("81.2.3.99", "81.2.3.255", "2a00:1450:1:2::1", "2a00:1450:1:2:ffff:ffff:ffff:ffff")) {
            assertTrue(f.isBlocked(ip(text)), text)
        }
        for (text in listOf("81.2.4.1", "2a00:1450:1:3::1")) assertFalse(f.isBlocked(ip(text)), text)
        // 셀룰러의 /64·/30 은 이 기기 하나의 것 — 막으면 인터넷이 깨질 수 있다. 정확한 자기 주소만 막는다.
        for (text in listOf("2600:380:1:2::6", "27.100.1.1")) assertFalse(f.isBlocked(ip(text)), text)
    }

    @Test
    fun 지나치게_넓은_접두는_버리고_자기_주소만_막는다() {
        val f = filter({
            listOf(
                LocalAddress("wlan0", ip("2a00:1450:1:2::abc"), 40),
                LocalAddress("eth0", ip("81.2.3.4"), 4),
            )
        })
        assertTrue(f.isBlocked(ip("2a00:1450:1:2::abc")))
        assertTrue(f.isBlocked(ip("81.2.3.4")))
        assertFalse(f.isBlocked(ip("2a00:1450:1:9::1")), "/48 보다 넓은 IPv6 접두는 막지 않는다")
        assertFalse(f.isBlocked(ip("81.9.9.9")), "/8 보다 넓은 IPv4 접두는 막지 않는다")
        // /48 은 받는다.
        val wide = filter({ listOf(LocalAddress("swlan0", ip("2a00:1450:1:2::1"), 48)) })
        assertTrue(wide.isBlocked(ip("2a00:1450:1:ffff::1")))
        assertFalse(wide.isBlocked(ip("2a00:1450:2::1")))
    }

    @Test
    fun 이름이_LAN_과_공인_주소로_풀리면_막히지_않는_주소로_잇는다() {
        val f = filter({ phone }, names = mapOf("nas.home" to listOf("2a00:1450:1:2::50", "93.184.216.34"), "only-lan" to listOf("81.2.3.7")))
        assertEquals(Destination.Allowed(ip("93.184.216.34")), f.resolve("nas.home"))
        assertIs<Destination.Rejected>(f.resolve("only-lan"))
    }

    @Test
    fun 인터페이스_목록은_몇_초_기억하고_읽지_못하면_앞의_것을_쓴다() {
        var clock = 0L
        val asked = AtomicInteger()
        var fail = false
        var current = phone
        val f = filter({
            asked.incrementAndGet()
            if (fail) throw java.net.SocketException("시험")
            current
        }, nowMs = { clock })
        repeat(100) { f.isBlocked(ip("1.1.1.1")) }
        assertEquals(1, asked.get(), "연결·데이터그램마다 묻지 않는다")
        // 망이 바뀌었다 — 기억한 동안은 옛 목록, 지나면 새 목록.
        current = listOf(LocalAddress("wlan0", ip("81.5.5.5"), 24))
        clock += DestinationFilter.LOCAL_CACHE_MS - 1
        assertTrue(f.isBlocked(ip("81.2.3.99")))
        clock += 1
        assertFalse(f.isBlocked(ip("81.2.3.99")))
        assertTrue(f.isBlocked(ip("81.5.5.9")))
        assertEquals(2, asked.get())
        // 읽지 못하면 앞의 것을 쓰고 다음에 다시 묻는다.
        fail = true
        clock += DestinationFilter.LOCAL_CACHE_MS
        assertTrue(f.isBlocked(ip("81.5.5.9")))
        assertTrue(f.isBlocked(ip("81.5.5.9")))
        assertEquals(4, asked.get())
    }

    @Test
    fun 처음부터_인터페이스를_읽지_못하면_고정_대역만_본다() {
        val f = filter({ throw java.net.SocketException("시험") })
        assertTrue(f.isBlocked(ip("10.0.0.1")))
        assertFalse(f.isBlocked(ip("93.184.216.34")))
    }

    @Test
    fun 여러_주소로_풀리면_막히지_않는_첫_주소를_주소_객체로_돌려준다() {
        val f = DestinationFilter(
            resolver = { name ->
                when (name) {
                    "mixed.test" -> arrayOf(ip("10.0.0.5"), ip("93.184.216.34"), ip("1.1.1.1"))
                    "private.test" -> arrayOf(ip("192.168.1.1"), ip("127.0.0.1"))
                    "empty.test" -> emptyArray()
                    else -> throw java.net.UnknownHostException(name)
                }
            },
            localAddresses = { emptyList() },
        )
        assertEquals(Destination.Allowed(ip("93.184.216.34")), f.resolve("mixed.test"))
        assertIs<Destination.Rejected>(f.resolve("private.test"))
        // 풀지 못한 이름은 막은 것이 아니다 — 거절과 따로.
        assertIs<Destination.Unresolved>(f.resolve("empty.test"))
        val unknown = f.resolve("nowhere.test")
        assertIs<Destination.Unresolved>(unknown)
        // 까닭은 기록용이고 호스트 이름을 싣지 않는다.
        assertFalse(unknown.reason.contains("nowhere"))
    }

    @Test
    fun 리터럴은_이름_풀이_없이_판정한다() {
        assertIs<Destination.Rejected>(DestinationFilter.SYSTEM.resolve("127.0.0.1"))
        assertIs<Destination.Rejected>(DestinationFilter.SYSTEM.resolve("192.168.49.1"))
        assertIs<Destination.Rejected>(DestinationFilter.SYSTEM.resolve("::1"))
        assertEquals(Destination.Allowed(ip("1.1.1.1")), DestinationFilter.SYSTEM.resolve("1.1.1.1"))
        assertTrue(DestinationFilter.SYSTEM.isBlocked(ip("10.1.2.3")))
        // 기기의 인터페이스 목록은 던지지 않고 읽힌다(이 기계의 루프백이 든다).
        assertTrue(DestinationFilter.systemLocalAddresses().any { it.address.isLoopbackAddress })
    }
}
