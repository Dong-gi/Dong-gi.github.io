package io.github.donggi.iroirobrowser.tether

import io.github.donggi.iroirobrowser.R
import io.github.donggi.iroirobrowser.tether.core.Passphrases
import io.github.donggi.iroirobrowser.tether.core.PeerFilter
import io.github.donggi.iroirobrowser.tether.core.PeerRejection
import io.github.donggi.iroirobrowser.tether.core.StartError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 테더의 기계 말을 앱의 글로 — 고르는 일만 본다(자원 번호와 인자). usb-tether 의 영어 글은 모두 여기의 한국어가 되었다. */
class TetherTextsTest {

    @Test
    fun 핫스팟의_모든_까닭에_글이_있고_서로_다르다() {
        val texts = HotspotProblem.entries.map { TetherTexts.hotspot(it, "x").res }
        assertEquals(texts.size, texts.toSet().size)
        assertTrue(texts.all { it != 0 })
        // 덧말(코드·예외 이름)을 싣는 것은 넷 — 나머지는 인자가 없다.
        val withDetail = HotspotProblem.entries.filter { TetherTexts.hotspot(it, "x").args.isNotEmpty() }.toSet()
        assertEquals(
            setOf(HotspotProblem.INVALID_CONFIG, HotspotProblem.CREATE_FAILED, HotspotProblem.CREATE_THREW, HotspotProblem.REMOVE_FAILED),
            withDetail,
        )
        assertEquals(listOf<Any>(""), TetherTexts.hotspot(HotspotProblem.CREATE_FAILED, null).args)
    }

    @Test
    fun 서버를_세우지_못한_까닭은_SOCKS5_는_그_포트_HTTP_파일은_해_본_범위를_싣는다() {
        // SOCKS5 는 1080 하나(옮기지 않는다). HTTP·파일은 기본 포트부터 열 개를 다 해 본 뒤에만 실패한다(사용자 결정 2026-10-10 — +1 씩).
        assertEquals(TetherTexts.Text(R.string.tether_err_socks_port, listOf(1080)), TetherTexts.startError(TetherTexts.Server.SOCKS5, StartError.PORT_TAKEN))
        assertEquals(TetherTexts.Text(R.string.tether_err_http_port, listOf(8282, 8291)), TetherTexts.startError(TetherTexts.Server.HTTP, StartError.PORT_TAKEN))
        assertEquals(TetherTexts.Text(R.string.tether_err_file_port, listOf(8080, 8089)), TetherTexts.startError(TetherTexts.Server.FILES, StartError.PORT_TAKEN))
        for (server in TetherTexts.Server.entries) {
            assertEquals(R.string.tether_err_terminated, TetherTexts.startError(server, StartError.TERMINATED).res)
            // 받기 고리가 죽어 내려간 서버는 그 이름과 함께.
            val died = TetherTexts.startError(server, StartError.DIED)
            assertEquals(R.string.tether_err_died, died.res)
            assertTrue((died.args.single() as TetherTexts.Text).res != 0)
        }
        val names = TetherTexts.Server.entries.map { (TetherTexts.startError(it, StartError.DIED).args.single() as TetherTexts.Text).res }
        assertEquals(3, names.toSet().size)
    }

    @Test
    fun 자격_증명의_문제는_핫스팟의_글로() {
        assertEquals(HotspotProblem.NOT_ASCII, TetherTexts.credential(Passphrases.Problem.NOT_ASCII))
        assertEquals(HotspotProblem.LENGTH, TetherTexts.credential(Passphrases.Problem.LENGTH))
        assertEquals(HotspotProblem.WEAK, TetherTexts.credential(Passphrases.Problem.WEAK))
        assertEquals(HotspotProblem.SSID_TOO_LONG, TetherTexts.credential(Passphrases.Problem.SSID_TOO_LONG))
        assertEquals(R.string.tether_credential_ascii, TetherTexts.hotspot(HotspotProblem.NOT_ASCII, null).res)
    }

    @Test
    fun 거절의_까닭은_어긋난_값만_싣고_손님의_주소는_없다() {
        assertEquals(
            TetherTexts.Text(R.string.tether_rejected_interface, listOf("wlan0", "p2p-wlan0-0")),
            TetherTexts.rejection(PeerRejection(PeerRejection.Reason.NOT_GROUP_INTERFACE, interfaceName = "wlan0", groupInterface = "p2p-wlan0-0")),
        )
        assertEquals(
            TetherTexts.Text(R.string.tether_rejected_interface_unknown, listOf("p2p-wlan0-0")),
            TetherTexts.rejection(PeerRejection(PeerRejection.Reason.NOT_GROUP_INTERFACE, groupInterface = "p2p-wlan0-0")),
        )
        // 들어온 망 — 사용자 폰에서 다르면 이 번호로 고친다.
        assertEquals(
            TetherTexts.Text(R.string.tether_rejected_network, listOf(101, PeerFilter.LOCAL_NET_ID)),
            TetherTexts.rejection(PeerRejection(PeerRejection.Reason.WRONG_NETWORK, netId = 101)),
        )
        assertEquals(
            TetherTexts.Text(R.string.tether_rejected_network_unknown, listOf("ErrnoException")),
            TetherTexts.rejection(PeerRejection(PeerRejection.Reason.NETWORK_UNKNOWN, detail = "ErrnoException")),
        )
        assertEquals(R.string.tether_rejected_no_group, TetherTexts.rejection(PeerRejection(PeerRejection.Reason.NO_GROUP)).res)
        assertEquals(R.string.tether_rejected_not_group, TetherTexts.rejection(PeerRejection(PeerRejection.Reason.NOT_GROUP_OWNER_ADDRESS)).res)
        assertEquals(R.string.tether_rejected_not_client, TetherTexts.rejection(PeerRejection(PeerRejection.Reason.NOT_GROUP_CLIENT)).res)
        // 모든 까닭의 글이 서로 다르고, 인자는 거절의 덧말(인터페이스·netId·까닭)에서만 온다.
        val full = { reason: PeerRejection.Reason -> PeerRejection(reason, interfaceName = "i", groupInterface = "g", netId = 7, detail = "d") }
        val texts = PeerRejection.Reason.entries.map { TetherTexts.rejection(full(it)) }
        assertEquals(texts.size, texts.map { it.res }.toSet().size)
        assertTrue(texts.flatMap { it.args }.all { it in setOf("i", "g", 7, "d", PeerFilter.LOCAL_NET_ID) })
    }

    private val running = TetherTexts.Status(
        serviceRunning = true, foregroundDenied = false, socksPort = 1080, httpPort = -1, filePort = 8080,
        socksError = null, httpError = StartError.PORT_TAKEN, fileError = null,
        hotspotActive = true, hotspotStarting = false, hotspotSsid = "DIRECT-UT-USBTether", groupInterface = "p2p-wlan0-0",
        hotspotProblem = null, hotspotDetail = null,
    )

    @Test
    fun 라이브_영역의_상태_줄에는_1초마다_바뀌는_수가_없다() {
        // 화면 낭독기는 라이브 영역이 바뀔 때마다 읽는다 — 주고받은 양·거절·상한의 수는 따로 된 줄로.
        val perSecond = setOf(R.string.tether_status_bytes, R.string.tether_status_rejected, R.string.tether_status_refused)
        val states = listOf(
            running,
            running.copy(serviceRunning = false),
            running.copy(hotspotActive = false, hotspotStarting = true),
            running.copy(hotspotActive = false, hotspotProblem = HotspotProblem.GROUP_LOST),
        )
        for (state in states) assertTrue(TetherTexts.status(state).none { it.res in perSecond }, state.toString())
        // 서버 하나가 포트를 얻지 못해도 테더링은 켜진 채 — 그 서버의 줄에 까닭이 보인다.
        assertEquals(
            listOf(
                TetherTexts.Text(R.string.tether_status_ports, listOf("1080", "—", "8080")),
                TetherTexts.Text(R.string.tether_err_http_port, listOf(8282, 8291)),
                TetherTexts.Text(R.string.tether_status_hotspot_on, listOf("DIRECT-UT-USBTether", "p2p-wlan0-0")),
            ),
            TetherTexts.status(running),
        )
        assertEquals(R.string.tether_status_hotspot_starting, TetherTexts.status(running.copy(hotspotActive = false, hotspotStarting = true)).last().res)
        // 옮겨 선 포트는 그대로 보인다(화면·알림은 실제 포트를 쓴다).
        assertEquals(
            TetherTexts.Text(R.string.tether_status_ports, listOf("1080", "8283", "8081")),
            TetherTexts.status(running.copy(httpPort = 8283, httpError = null, filePort = 8081)).first(),
        )
    }

    @Test
    fun 꺼진_테더링은_한_줄이고_꺼진_까닭을_남긴다() {
        // 켜기·끄기는 하나(사용자 결정 2026-10-10) — 꺼지면 '테더링 꺼짐' 하나. 핫스팟이 서지 못했거나 내려가 꺼졌으면 그 까닭이 남는다.
        val off = running.copy(
            serviceRunning = false, socksPort = -1, httpPort = -1, filePort = -1, httpError = null,
            hotspotActive = false, hotspotSsid = null, groupInterface = null,
        )
        assertEquals(listOf(TetherTexts.Text(R.string.tether_status_off)), TetherTexts.status(off))
        assertEquals(
            listOf(
                TetherTexts.Text(R.string.tether_status_off),
                TetherTexts.Text(R.string.tether_status_hotspot_problem, listOf(TetherTexts.hotspot(HotspotProblem.GROUP_LOST, null))),
            ),
            TetherTexts.status(off.copy(hotspotProblem = HotspotProblem.GROUP_LOST)),
        )
        // 시간 안에 서지 않았다·위치 서비스 — 어느 까닭이든 같은 꼴.
        for (problem in listOf(HotspotProblem.NOT_FORMED, HotspotProblem.LOCATION_OFF, HotspotProblem.BUSY_NO_GROUP)) {
            assertEquals(R.string.tether_status_hotspot_problem, TetherTexts.status(off.copy(hotspotProblem = problem)).last().res, problem.name)
        }
        assertEquals(R.string.tether_status_denied, TetherTexts.status(off.copy(foregroundDenied = true)).first().res)
    }

    @Test
    fun 켜기_끄기는_하나_서비스와_핫스팟이_함께여야_켜짐() {
        assertTrue(TetherTexts.isOn(serviceRunning = true, hotspotActive = true, hotspotStarting = false))
        assertTrue(TetherTexts.isOn(serviceRunning = true, hotspotActive = false, hotspotStarting = true), "켜는 중도 켜짐 — 단추는 끄기")
        // 서비스가 떠 있어도 핫스팟이 없으면(서지 못해 끄는 사이) 꺼짐 — 그때 누르면 다시 켠다.
        assertFalse(TetherTexts.isOn(serviceRunning = true, hotspotActive = false, hotspotStarting = false))
        assertFalse(TetherTexts.isOn(serviceRunning = false, hotspotActive = false, hotspotStarting = false))
        assertFalse(TetherTexts.isOn(serviceRunning = false, hotspotActive = true, hotspotStarting = false))
    }

    @Test
    fun 오가는_양과_상한으로_닫은_연결은_따로_보인다() {
        val quiet = TetherTexts.Traffic("1 MB", "2 MB", rejected = 0, lastRejection = null, refusedBudget = 0, refusedSessions = 0, refusedUdp = 0)
        assertEquals(listOf(TetherTexts.Text(R.string.tether_status_bytes, listOf("1 MB", "2 MB"))), TetherTexts.traffic(quiet))
        // 예산·UDP 상한으로 닫은 연결은 예전에 기록 한 줄뿐이었다 — 손님 거르기와 따로 센다.
        val busy = quiet.copy(rejected = 3, lastRejection = PeerRejection(PeerRejection.Reason.NO_GROUP), refusedBudget = 4, refusedSessions = 1, refusedUdp = 2)
        val lines = TetherTexts.traffic(busy)
        assertEquals(R.string.tether_status_rejected, lines[1].res)
        assertEquals(TetherTexts.Text(R.string.tether_status_refused, listOf(7L, 4L, 1L, 2L)), lines[2])
        assertFalse(TetherTexts.traffic(quiet.copy(refusedUdp = 1)).none { it.res == R.string.tether_status_refused })
    }

    @Test
    fun 못_묶은_포트는_줄표() {
        assertEquals("1080", TetherTexts.port(1080))
        assertEquals("—", TetherTexts.port(-1))
        assertEquals("—", TetherTexts.port(0))
    }
}
