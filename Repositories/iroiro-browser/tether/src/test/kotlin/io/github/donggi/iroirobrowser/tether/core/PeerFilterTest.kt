package io.github.donggi.iroirobrowser.tether.core

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 손님 거르개 — 그룹 오너 주소로 왔는가·상대가 그룹의 손님인가·**이 앱의 그룹이 떠 있고 그 주소가 정확히 그 그룹의 인터페이스에 붙었는가**·
 * **연결이 들어온 망(SO_MARK 의 netId)이 로컬 망인가**. 그룹·SO_MARK·인터페이스 조회는 주입한다(기기의 값은 확인하지 못했다 —
 * 화면이 거절한 까닭과 그 값을 보인다).
 */
class PeerFilterTest {

    private val owner = ip("192.168.49.1")
    private val guest = ip("192.168.49.2")

    /** 그룹 인터페이스 `p2p-wlan0-0` 에 explicitlySelected·protectedFromVpn·권한 비트가 선 로컬 망(99)의 fwmark — netd 가 실제로 다는 꼴. */
    private val localMark = 0x70063

    /** 조회가 무엇으로 불렸는지 적는 가짜. */
    private class Lookup(private val answer: (Inet4Address) -> String?) : (Inet4Address) -> String? {
        val asked = CopyOnWriteArrayList<Inet4Address>()

        override fun invoke(address: Inet4Address): String? {
            asked += address
            return answer(address)
        }
    }

    private fun filter(
        group: String? = "p2p-wlan0-0",
        lookup: (Inet4Address) -> String? = { "p2p-wlan0-0" },
        nowMs: () -> Long = { 0L },
    ) = PeerFilter(groupInterface = { group }, socketMark = { error("시험은 주소로 부른다") }, nowMs = nowMs, interfaceName = lookup)

    @Test
    fun 우리_그룹의_인터페이스로_로컬_망에서_들어온_손님은_받는다() {
        assertNull(filter().check(guest, owner) { localMark })
        assertNull(filter().check(ip("192.168.49.254"), owner) { PeerFilter.LOCAL_NET_ID })
        assertEquals(0x63, PeerFilter.LOCAL_NET_ID)
    }

    @Test
    fun 주소가_다_맞아도_폰이_붙은_다른_망으로_들어오면_거절하고_netId_를_알린다() {
        // 폰이 남의 Wi-Fi(STA)에 붙은 채 핫스팟을 켰다. 그 Wi-Fi 쪽 기기가 192.168.49.1 로 가는 경로를 폰의 Wi-Fi 주소로
        // 넣고 출발 192.168.49.77 로 붙는다 — 향한 주소·상대 주소·그 주소가 붙은 인터페이스(우리 그룹)가 모두 맞는다. 들어온 망만 다르다.
        val stationNetwork = 0x30065 // netId 101 — Wi-Fi 의 망
        assertEquals(
            PeerRejection(PeerRejection.Reason.WRONG_NETWORK, netId = 101),
            filter().check(ip("192.168.49.77"), owner) { stationNetwork },
        )
        assertEquals(PeerRejection.Reason.WRONG_NETWORK, filter().check(guest, owner) { 0 }?.reason, "표가 없는 연결(루프백 등)")
        assertEquals(100, filter().check(guest, owner) { 0x10064 }?.netId)
    }

    @Test
    fun 들어온_망을_읽는_길이_없으면_넷째_검사를_건너뛴다() {
        // 앱은 지금 SO_MARK 를 읽을 공개 API 가 없다(PeerFilter 의 머리말 4) — 읽는 함수를 주지 않으면 주소·그룹·인터페이스만 본다.
        val noReader = PeerFilter(groupInterface = { "p2p-wlan0-0" }, interfaceName = { "p2p-wlan0-0" })
        assertNull(noReader.check(guest, owner, null))
        assertEquals(PeerRejection.Reason.NO_GROUP, PeerFilter(groupInterface = { null }).check(guest, owner, null)?.reason)
    }

    @Test
    fun 들어온_망을_읽지_못하면_거절하고_까닭을_알린다() {
        val rejection = filter().check(guest, owner) { throw java.io.IOException("fd 없음") }
        assertEquals(PeerRejection(PeerRejection.Reason.NETWORK_UNKNOWN, detail = "IOException"), rejection)
    }

    @Test
    fun 우리_그룹이_떠_있지_않으면_p2p_인터페이스여도_거절한다() {
        // 다른 앱(옛 테더 앱 등)의 그룹도 같은 대역·같은 로컬 망을 쓴다 — 예전에는 이름이 p2p 로 시작하기만 하면 받았다.
        val lookup = Lookup { "p2p-wlan0-0" }
        val marks = AtomicInteger()
        for (group in listOf(null, "")) {
            assertEquals(PeerRejection(PeerRejection.Reason.NO_GROUP), filter(group = group, lookup = lookup).check(guest, owner) { marks.incrementAndGet(); localMark })
        }
        assertTrue(lookup.asked.isEmpty() && marks.get() == 0, "그룹이 없으면 인터페이스도 망도 묻지 않는다")
    }

    @Test
    fun 그룹_오너_주소가_우리_그룹이_아닌_인터페이스에_붙었으면_거절하고_두_이름을_알린다() {
        assertEquals(
            PeerRejection(PeerRejection.Reason.NOT_GROUP_INTERFACE, interfaceName = "p2p-wlan0-1", groupInterface = "p2p-wlan0-0"),
            filter(lookup = { "p2p-wlan0-1" }).check(guest, owner) { localMark },
        )
        // 폰이 붙은 다른 Wi-Fi 의 DHCP 가 192.168.49.0/24 를 쓰고 폰에 .1 을 준 경우(usb-tether 에서는 통과했다).
        assertEquals("wlan0", filter(lookup = { "wlan0" }).check(guest, owner) { localMark }?.interfaceName)
        assertEquals(
            PeerRejection(PeerRejection.Reason.NOT_GROUP_INTERFACE, interfaceName = null, groupInterface = "p2p-wlan0-0"),
            filter(lookup = { null }).check(guest, owner) { localMark },
        )
        val throwing = filter(lookup = { throw java.net.SocketException("조회 실패") })
        assertEquals(PeerRejection.Reason.NOT_GROUP_INTERFACE, throwing.check(guest, owner) { localMark }?.reason)
    }

    @Test
    fun 그룹_오너_주소로_오지_않으면_인터페이스도_망도_묻지_않고_거절한다() {
        val lookup = Lookup { "p2p-wlan0-0" }
        val f = filter(lookup = lookup)
        for (local in listOf(ip("192.168.49.2"), ip("10.0.0.1"), ip("192.168.50.1"), ip("127.0.0.1"), InetAddress.getByName("::1"), null)) {
            assertEquals(PeerRejection.Reason.NOT_GROUP_OWNER_ADDRESS, f.check(guest, local) { error("묻지 않는다") }?.reason, "local=$local")
        }
        assertTrue(lookup.asked.isEmpty())
    }

    @Test
    fun 상대가_그룹의_손님이_아니면_거절한다() {
        val lookup = Lookup { "p2p-wlan0-0" }
        val f = filter(lookup = lookup)
        // .1 은 그룹 오너 자신 — 폰 안의 다른 앱이 그 주소로 붙으면 이렇게 보인다.
        for (peer in listOf(owner, ip("192.168.49.0"), ip("192.168.49.255"), ip("192.168.50.2"), ip("10.0.0.2"), InetAddress.getByName("::1"), null)) {
            assertEquals(PeerRejection.Reason.NOT_GROUP_CLIENT, f.check(peer, owner) { error("묻지 않는다") }?.reason, "peer=$peer")
        }
        assertTrue(lookup.asked.isEmpty())
    }

    @Test
    fun IPv4_mapped_꼴도_벗겨_보고_인터페이스_조회에는_IPv4_를_넘긴다() {
        val lookup = Lookup { "p2p-wlan0-0" }
        val mappedOwner = mapped(192, 168, 49, 1)
        val mappedGuest = mapped(192, 168, 49, 7)
        assertTrue(mappedOwner is Inet6Address && mappedGuest is Inet6Address)
        assertNull(filter(lookup = lookup).check(mappedGuest, mappedOwner) { localMark })
        // ::ffff:192.168.49.1 로 물으면 인터페이스의 IPv4 와 맞지 않아 늘 null 이 된다 — 벗긴 것으로 묻는다.
        assertEquals(listOf(owner), lookup.asked.toList())
        assertEquals(PeerRejection.Reason.NOT_GROUP_CLIENT, filter(lookup = lookup).check(mapped(192, 168, 49, 1), mappedOwner) { localMark }?.reason)
    }

    @Test
    fun IPv4_compatible_같은_다른_IPv6_꼴은_받지_않는다() {
        val compatible = Inet6Address.getByAddress(null, ByteArray(12) + byteArrayOf(192.toByte(), 168.toByte(), 49, 2), null as java.net.NetworkInterface?)
        assertEquals(PeerRejection.Reason.NOT_GROUP_CLIENT, filter().check(compatible, owner) { localMark }?.reason)
    }

    @Test
    fun 인터페이스_조회는_2초_동안_기억하고_그_뒤나_그룹이_바뀌면_다시_묻는다() {
        var clock = 1_000L
        var answer: String? = "p2p-wlan0-0"
        var group = "p2p-wlan0-0"
        val lookup = Lookup { answer }
        val f = PeerFilter(groupInterface = { group }, socketMark = { 0 }, nowMs = { clock }, interfaceName = lookup)
        repeat(50) { assertNull(f.check(ip("192.168.49.${2 + it}"), owner) { localMark }) }
        assertEquals(1, lookup.asked.size, "연결이 몰려도 한 번만 묻는다")
        // 그룹이 내려가 같은 주소가 다른 인터페이스로 갔다 — 기억한 동안은 옛 답, 지나면 새 답.
        answer = "wlan0"
        clock += PeerFilter.CACHE_MS - 1
        assertNull(f.check(guest, owner) { localMark })
        clock += 1
        assertEquals("wlan0", f.check(guest, owner) { localMark }?.interfaceName)
        assertEquals(2, lookup.asked.size)
        // 시계가 뒤로 가도(단조 시계라 그럴 일은 없지만) 옛 답을 믿지 않는다.
        clock -= 10_000
        f.check(guest, owner) { localMark }
        assertEquals(3, lookup.asked.size)
        // 그룹이 다시 서며 인터페이스 이름이 바뀌었다 — 기억한 시간 안이어도 곧바로 다시 묻는다.
        answer = "p2p-wlan0-1"
        group = "p2p-wlan0-1"
        assertNull(f.check(guest, owner) { localMark })
        assertEquals(4, lookup.asked.size)
    }

    @Test
    fun 기기의_인터페이스_조회는_던지지_않는다() {
        // 루프백의 이름은 기계마다 다르다(lo·loopback_0) — 던지지 않고 답하는지만 본다.
        PeerFilter.systemInterfaceName(owner)
        PeerFilter.systemInterfaceName(ip("127.0.0.1"))
    }

    private fun ip(text: String): Inet4Address = InetAddress.getByName(text) as Inet4Address

    private fun mapped(a: Int, b: Int, c: Int, d: Int): InetAddress =
        Inet6Address.getByAddress(
            null,
            ByteArray(10) + byteArrayOf(0xFF.toByte(), 0xFF.toByte(), a.toByte(), b.toByte(), c.toByte(), d.toByte()),
            null as java.net.NetworkInterface?,
        )
}
