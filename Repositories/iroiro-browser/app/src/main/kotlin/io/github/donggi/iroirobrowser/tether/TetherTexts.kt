package io.github.donggi.iroirobrowser.tether

import io.github.donggi.iroirobrowser.R
import io.github.donggi.iroirobrowser.tether.core.FileServer
import io.github.donggi.iroirobrowser.tether.core.HttpProxyServer
import io.github.donggi.iroirobrowser.tether.core.Passphrases
import io.github.donggi.iroirobrowser.tether.core.PeerFilter
import io.github.donggi.iroirobrowser.tether.core.PeerRejection
import io.github.donggi.iroirobrowser.tether.core.Socks5Server
import io.github.donggi.iroirobrowser.tether.core.StartError

/**
 * 테더의 기계 말(서버의 [StartError]·핫스팟의 [HotspotProblem]·거르개의 [PeerRejection]·[Passphrases.Problem]) → 화면의 글(자원 번호와 인자).
 * `:tether` 모듈은 기계 말만 낸다(토렌트 엔진과 같은 꼴) — 자원 번호와 인자로만 골라 기기 없이 시험한다(`TetherTextsTest`). 인자가 [Text] 면
 * 화면이 그것을 먼저 글로 바꾼다(겹친 글).
 */
internal object TetherTexts {

    /** 자원 번호와 그 인자. */
    data class Text(val res: Int, val args: List<Any> = emptyList())

    enum class Server { SOCKS5, HTTP, FILES }

    /** 상태 줄(라이브 영역)의 재료 — 서버와 핫스팟. 화면이 [TetherState] 에서 그때그때 짓는다. */
    data class Status(
        val serviceRunning: Boolean,
        val foregroundDenied: Boolean,
        val socksPort: Int,
        val httpPort: Int,
        val filePort: Int,
        val socksError: StartError?,
        val httpError: StartError?,
        val fileError: StartError?,
        val hotspotActive: Boolean,
        val hotspotStarting: Boolean,
        val hotspotSsid: String?,
        val groupInterface: String?,
        val hotspotProblem: HotspotProblem?,
        val hotspotDetail: String?,
    )

    /** 오가는 양과 센 수의 재료. 바이트는 화면이 사람이 읽는 꼴로 바꿔 넣는다. */
    data class Traffic(
        val bytesIn: String,
        val bytesOut: String,
        val rejected: Long,
        val lastRejection: PeerRejection?,
        val refusedBudget: Long,
        val refusedSessions: Long,
        val refusedUdp: Long,
    )

    /**
     * 테더링이 켜져 있는가(핫스팟을 켜는 중 포함) — 하나뿐인 켜기·끄기(화면의 단추·타일·트램폴린)의 판정(사용자 결정 2026-10-10 — 서버 셋과
     * 핫스팟을 함께 켜고 끈다). 서비스가 떠 있어도 핫스팟이 없으면(서지 못해 테더링을 끄는 사이) 꺼짐이다 — 그때 '켜기' 를 누르면 다시 켠다.
     */
    fun isOn(serviceRunning: Boolean, hotspotActive: Boolean, hotspotStarting: Boolean): Boolean =
        serviceRunning && (hotspotActive || hotspotStarting)

    /**
     * 상태 줄 — 화면 낭독기가 바뀔 때마다 읽는 **라이브 영역**에 든다. 그래서 1초마다 바뀌는 것(주고받은 양·거절·상한의 수)을 넣지 않는다
     * ([traffic] 이 따로 맡는다) — 예전에는 한 글에 함께 있어 1초마다 다시 쓰여 낭독기가 쉬지 않고 읽었다.
     *
     * 꺼져 있으면 '테더링 꺼짐' 한 줄과 — 핫스팟이 서지 못했거나 내려가 테더링이 꺼졌으면(또는 끄며 그룹을 내리지 못했으면) — 그 까닭.
     * 켜져 있으면 서버 줄(실제 포트와 세우지 못한 서버의 까닭)과 핫스팟 줄(SSID·그룹 인터페이스).
     */
    fun status(s: Status): List<Text> {
        val lines = ArrayList<Text>()
        if (!s.serviceRunning) {
            lines += Text(if (s.foregroundDenied) R.string.tether_status_denied else R.string.tether_status_off)
            if (s.hotspotProblem != null) lines += Text(R.string.tether_status_hotspot_problem, listOf(hotspot(s.hotspotProblem, s.hotspotDetail)))
            return lines
        }
        lines += Text(R.string.tether_status_ports, listOf(port(s.socksPort), port(s.httpPort), port(s.filePort)))
        for ((server, error) in listOf(Server.SOCKS5 to s.socksError, Server.HTTP to s.httpError, Server.FILES to s.fileError)) {
            if (error != null) lines += startError(server, error)
        }
        lines += when {
            s.hotspotActive -> Text(R.string.tether_status_hotspot_on, listOf(s.hotspotSsid.orEmpty(), s.groupInterface.orEmpty()))
            s.hotspotStarting -> Text(R.string.tether_status_hotspot_starting)
            s.hotspotProblem != null -> Text(R.string.tether_status_hotspot_problem, listOf(hotspot(s.hotspotProblem, s.hotspotDetail)))
            else -> Text(R.string.tether_status_hotspot_off)
        }
        return lines
    }

    /** 오가는 양과 센 수 — 라이브 영역 밖이고 1초마다 다시 쓴다. 상한으로 닫은 연결은 손님 거르기와 따로 센다. */
    fun traffic(t: Traffic): List<Text> {
        val lines = arrayListOf(Text(R.string.tether_status_bytes, listOf(t.bytesIn, t.bytesOut)))
        if (t.rejected > 0 && t.lastRejection != null) lines += Text(R.string.tether_status_rejected, listOf(t.rejected, rejection(t.lastRejection)))
        val refused = t.refusedBudget + t.refusedSessions + t.refusedUdp
        if (refused > 0) lines += Text(R.string.tether_status_refused, listOf(refused, t.refusedBudget, t.refusedSessions, t.refusedUdp))
        return lines
    }

    fun startError(server: Server, error: StartError): Text = when (error) {
        StartError.TERMINATED -> Text(R.string.tether_err_terminated)
        StartError.DIED -> Text(R.string.tether_err_died, listOf(serverName(server)))
        // SOCKS5 는 고정 포트 하나(옮기지 않는다 — PC 의 런처가 1080 만 쓴다). HTTP·파일은 기본 포트부터 열 개를 다 해 본 범위(사용자 결정
        // 2026-10-10 — 다음 포트로 옮긴다).
        StartError.PORT_TAKEN -> when (server) {
            Server.SOCKS5 -> Text(R.string.tether_err_socks_port, listOf(Socks5Server.BASE_PORT))
            Server.HTTP -> Text(
                R.string.tether_err_http_port,
                listOf(HttpProxyServer.BASE_PORT, HttpProxyServer.BASE_PORT + HttpProxyServer.PORT_ATTEMPTS - 1),
            )
            Server.FILES -> Text(R.string.tether_err_file_port, listOf(FileServer.BASE_PORT, FileServer.BASE_PORT + FileServer.PORT_ATTEMPTS - 1))
        }
    }

    private fun serverName(server: Server): Text = when (server) {
        Server.SOCKS5 -> Text(R.string.tether_server_name_socks)
        Server.HTTP -> Text(R.string.tether_server_name_http)
        Server.FILES -> Text(R.string.tether_server_name_files)
    }

    fun hotspot(problem: HotspotProblem, detail: String?): Text = when (problem) {
        HotspotProblem.P2P_UNAVAILABLE -> Text(R.string.tether_hotspot_p2p_unavailable)
        HotspotProblem.NOT_ASCII -> Text(R.string.tether_credential_ascii)
        HotspotProblem.LENGTH -> Text(R.string.tether_credential_length)
        HotspotProblem.WEAK -> Text(R.string.tether_credential_weak)
        HotspotProblem.SSID_TOO_LONG -> Text(R.string.tether_credential_ssid_long)
        HotspotProblem.SECRET_UNREADABLE -> Text(R.string.tether_hotspot_secret_unreadable)
        HotspotProblem.WIFI_OFF -> Text(R.string.tether_hotspot_wifi_off)
        HotspotProblem.LOCATION_OFF -> Text(R.string.tether_hotspot_location_off)
        HotspotProblem.P2P_DISABLED -> Text(R.string.tether_hotspot_p2p_disabled)
        HotspotProblem.INVALID_CONFIG -> Text(R.string.tether_hotspot_invalid, listOf(detail.orEmpty()))
        HotspotProblem.PERMISSION -> Text(R.string.tether_hotspot_permission)
        HotspotProblem.CREATE_ERROR -> Text(R.string.tether_hotspot_create_error)
        HotspotProblem.UNSUPPORTED -> Text(R.string.tether_hotspot_unsupported)
        HotspotProblem.CREATE_FAILED -> Text(R.string.tether_hotspot_create_failed, listOf(detail.orEmpty()))
        HotspotProblem.BUSY_NO_GROUP -> Text(R.string.tether_hotspot_busy_no_group)
        HotspotProblem.BUSY_OTHER_GROUP -> Text(R.string.tether_hotspot_busy_other_group)
        HotspotProblem.CREATE_THREW -> Text(R.string.tether_hotspot_create_threw, listOf(detail.orEmpty()))
        HotspotProblem.NOT_FORMED -> Text(R.string.tether_hotspot_not_formed)
        HotspotProblem.GROUP_LOST -> Text(R.string.tether_hotspot_group_lost)
        HotspotProblem.REMOVE_FAILED -> Text(R.string.tether_hotspot_remove_failed, listOf(detail.orEmpty()))
    }

    /** 자격 증명의 문제 → 핫스팟의 문제(같은 글을 쓴다). */
    fun credential(problem: Passphrases.Problem): HotspotProblem = when (problem) {
        Passphrases.Problem.NOT_ASCII -> HotspotProblem.NOT_ASCII
        Passphrases.Problem.LENGTH -> HotspotProblem.LENGTH
        Passphrases.Problem.WEAK -> HotspotProblem.WEAK
        Passphrases.Problem.SSID_TOO_LONG -> HotspotProblem.SSID_TOO_LONG
    }

    /**
     * 거절한 연결의 마지막 까닭 — 손님의 주소는 없다. 사용자 폰에서 손님이 모두 막히면 무엇이 어긋났는지 보이게 덧말(인터페이스 이름·읽은
     * netId·읽지 못한 까닭)을 싣는다(기기의 값은 확인하지 못했다).
     */
    fun rejection(rejection: PeerRejection): Text = when (rejection.reason) {
        PeerRejection.Reason.NOT_GROUP_OWNER_ADDRESS -> Text(R.string.tether_rejected_not_group)
        PeerRejection.Reason.NOT_GROUP_CLIENT -> Text(R.string.tether_rejected_not_client)
        PeerRejection.Reason.NO_GROUP -> Text(R.string.tether_rejected_no_group)
        PeerRejection.Reason.NOT_GROUP_INTERFACE ->
            rejection.interfaceName?.let { Text(R.string.tether_rejected_interface, listOf(it, rejection.groupInterface.orEmpty())) }
                ?: Text(R.string.tether_rejected_interface_unknown, listOf(rejection.groupInterface.orEmpty()))
        PeerRejection.Reason.WRONG_NETWORK -> Text(R.string.tether_rejected_network, listOf(rejection.netId ?: -1, PeerFilter.LOCAL_NET_ID))
        PeerRejection.Reason.NETWORK_UNKNOWN -> Text(R.string.tether_rejected_network_unknown, listOf(rejection.detail.orEmpty()))
    }

    /** 포트 칸 — 못 묶었으면 줄표. */
    fun port(port: Int): String = if (port > 0) port.toString() else "—"
}
