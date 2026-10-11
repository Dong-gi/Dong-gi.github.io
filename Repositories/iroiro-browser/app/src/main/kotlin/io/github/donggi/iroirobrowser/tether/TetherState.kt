package io.github.donggi.iroirobrowser.tether

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import io.github.donggi.iroirobrowser.tether.core.ChangeCoalescer
import io.github.donggi.iroirobrowser.tether.core.PeerRejection
import io.github.donggi.iroirobrowser.tether.core.Refusal
import io.github.donggi.iroirobrowser.tether.core.StartError
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicLong

/**
 * 테더의 지금 상태 — **`:tether` 프로세스 안의 정적 필드**(usb-tether 의 TetherService 의 companion 과 같은 자리). 서비스가 쓰고 화면·타일·
 * 트램폴린이 읽는다. 그래서 테더의 구성 요소는 매니페스트에서 모두 `android:process=":tether"` 다 — 다른 프로세스에서 읽으면 늘 처음 값이다(앱의
 * 시험 `TetherIsolationTest` 가 매니페스트를 본다). 손님 거르개([io.github.donggi.iroirobrowser.tether.core.PeerFilter])도 여기의
 * [groupInterface] 를 읽는다.
 *
 * 켜기·끄기는 하나다(사용자 결정 2026-10-10) — 화면의 단추·타일·트램폴린은 [tetheringOn] 하나로 켜고 끈다. 핫스팟이 서지 못하거나 내려가면
 * 서비스가 테더링을 통째로 끄고 그 까닭([hotspotProblem])은 여기 남아 화면이 보인다. 사용자가 끄면 [clearProblem] 이 지운다.
 *
 * 바뀌면 메인 스레드에서 듣개를 부른다 — [CHANGE_WINDOW_MS] 안의 바뀜은 한 번으로 합친다([ChangeCoalescer]). 브로드캐스트를 쓰지
 * 않는다 — 한 프로세스 안이라 필요 없고, 내보낸 수신기의 틈을 만들지 않는다(usb-tether 는 타일이 패키지 브로드캐스트를 받았다). 연결마다 바뀌는
 * 수(주고받은 양·거절·상한)는 알리지 않는다 — 화면이 1초마다 읽는다.
 */
internal object TetherState {

    /** 서비스가 떠 있는가(서버를 세우려 했는가 — 서버마다의 포트는 아래). 테더링이 켜져 있는지는 [tetheringOn]. */
    @Volatile
    var serviceRunning = false

    /**
     * 실제로 묶인 포트. 못 묶었으면 -1(그 까닭이 아래에). SOCKS5 는 1080 하나, HTTP 프록시·파일 서버는 기본 포트가 차 있으면 다음 포트로
     * 옮겨 선다(사용자 결정 2026-10-10) — 화면·알림은 기본 상수가 아니라 이 값을 보인다.
     */
    @Volatile
    var socksPort = -1

    @Volatile
    var httpPort = -1

    @Volatile
    var filePort = -1

    /** 서버마다 마지막으로 세우지 못한(또는 받기 고리가 죽어 내려간) 까닭. */
    @Volatile
    var socksError: StartError? = null

    @Volatile
    var httpError: StartError? = null

    @Volatile
    var fileError: StartError? = null

    /** 시스템이 서비스를 포그라운드로 세우지 못하게 했다(뒤에서 시작하기 제한) — 화면에서 다시 켜야 한다. */
    @Volatile
    var foregroundDenied = false

    /** 우리 그룹이 선 것을 확인했다(인터페이스까지). 그룹 만들기를 청한 뒤 확인하기 전은 [hotspotStarting]. */
    @Volatile
    var hotspotActive = false

    /** 그룹 만들기를 청했고 아직 서는 것을 확인하지 못했다. */
    @Volatile
    var hotspotStarting = false

    /** 핫스팟이 켜져 있을 때 실제 SSID(앞머리 포함). */
    @Volatile
    var hotspotSsid: String? = null

    /**
     * 떠 있는 **우리** 그룹의 인터페이스 이름(`WifiP2pGroup.getInterface()`) — 손님 거르개가 그룹 오너 주소가 붙은 인터페이스와 견준다.
     * 그룹이 없거나 아직 확인하지 못했으면 null — 그동안은 모든 손님을 거절한다.
     */
    @Volatile
    var groupInterface: String? = null

    /**
     * 핫스팟을 켜거나 끄지 못한 까닭과 그 덧말(프레임워크의 코드·예외 이름). 그 때문에 테더링이 꺼졌으면 꺼진 뒤에도 남는다 — 다음 켜기나
     * 사용자의 끄기([clearProblem])가 지운다.
     */
    @Volatile
    var hotspotProblem: HotspotProblem? = null

    @Volatile
    var hotspotDetail: String? = null

    /**
     * 테더링이 켜져 있는가(핫스팟을 켜는 중 포함) — 화면의 단추·타일·트램폴린이 이것 하나로 켜고 끈다(사용자 결정 2026-10-10 — 서버 셋과
     * 핫스팟의 켜기·끄기를 하나로). 판정은 [TetherTexts.isOn].
     */
    val tetheringOn: Boolean
        get() = TetherTexts.isOn(serviceRunning, hotspotActive, hotspotStarting)

    /** 사용자가 테더링을 끈다 — 지난 까닭을 지운다(실패로 꺼진 것만 까닭이 남는다). */
    fun clearProblem() {
        hotspotProblem = null
        hotspotDetail = null
        changed()
    }

    /** 이번에 켠 테더링의 수를 0 으로(서비스가 설 때) — 지난번의 거절 수가 새 세션에 섞이지 않게. 꺼진 뒤에는 다음에 켤 때까지 남는다. */
    fun resetCounters() {
        bytesIn.set(0)
        bytesOut.set(0)
        rejected.set(0)
        lastRejection = null
        refusedSessions.set(0)
        refusedBudget.set(0)
        refusedUdp.set(0)
    }

    /** 손님에게서 받은·손님에게 보낸 바이트(이번에 켠 뒤로). */
    val bytesIn = AtomicLong()
    val bytesOut = AtomicLong()

    /** 거절한 연결 수와 마지막 까닭(손님의 주소는 싣지 않는다). */
    val rejected = AtomicLong()

    @Volatile
    var lastRejection: PeerRejection? = null

    /** 상한 때문에 닫은 연결 수 — 손님 거르기와 따로. */
    val refusedSessions = AtomicLong()
    val refusedBudget = AtomicLong()
    val refusedUdp = AtomicLong()

    fun countRefusal(refusal: Refusal) {
        when (refusal) {
            Refusal.SESSION_LIMIT -> refusedSessions.incrementAndGet()
            Refusal.THREAD_BUDGET -> refusedBudget.incrementAndGet()
            Refusal.UDP_LIMIT -> refusedUdp.incrementAndGet()
        }
    }

    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    /** 바뀜을 합친다 — 창 안의 것은 한 번(메인 스레드). */
    private val coalescer = ChangeCoalescer(
        windowMs = CHANGE_WINDOW_MS,
        nowMs = { SystemClock.uptimeMillis() },
        schedule = { delayMs, task -> main.postDelayed({ task() }, delayMs) },
        dispatch = { listeners.forEach { it() } },
    )

    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    /** 아무 스레드에서나 — 듣개는 메인 스레드에서 불린다. [CHANGE_WINDOW_MS] 안의 것은 합친다. */
    fun changed() {
        coalescer.changed()
    }

    /** 바뀜을 합치는 창. */
    private const val CHANGE_WINDOW_MS = 250L
}

/**
 * 핫스팟을 켜거나 끄지 못한 까닭. 화면의 글은 [TetherTexts.hotspot] 이 고른다. [REMOVE_FAILED] 밖의 것은 켜는 길(과 [GROUP_LOST] 는 켜진 뒤)의
 * 것이고, 그때 서비스는 테더링을 통째로 끈다(TetherService.endTethering — 켜기·끄기는 하나).
 */
internal enum class HotspotProblem {
    /** 이 기기에 Wi-Fi P2P 서비스가 없다. */
    P2P_UNAVAILABLE,

    /** 패스프레이즈에 출력 가능한 ASCII 밖의 글자가 있다. */
    NOT_ASCII,

    /** 패스프레이즈가 8–63자가 아니다. */
    LENGTH,

    /** 뻔한 패스프레이즈. */
    WEAK,

    /** 앞머리까지 SSID 가 32바이트를 넘는다. */
    SSID_TOO_LONG,

    /** 저장한 패스프레이즈를 열지 못했다(기기 키) — 덮어쓰지 않았다. 화면에서 새로 넣어야 한다. */
    SECRET_UNREADABLE,

    WIFI_OFF,
    LOCATION_OFF,

    /** Wi-Fi Direct 가 꺼져 있다(모바일 핫스팟이 켜져 있는 등). */
    P2P_DISABLED,

    /** 프레임워크가 SSID·패스프레이즈를 받지 않았다(덧말: 예외의 글). */
    INVALID_CONFIG,

    /** 권한이 없다(위치·근처 기기). */
    PERMISSION,

    /** createGroup 이 ERROR — 다른 핫스팟·Wi-Fi Direct 앱. */
    CREATE_ERROR,
    UNSUPPORTED,

    /** createGroup 이 다른 코드로 실패(덧말: 코드). */
    CREATE_FAILED,

    /** BUSY 인데 떠 있는 그룹이 없다. */
    BUSY_NO_GROUP,

    /** BUSY 이고 다른 자격 증명의 그룹이 떠 있다(옛 테더 앱 등). */
    BUSY_OTHER_GROUP,

    /** createGroup 이 던졌다(덧말: 예외 이름). */
    CREATE_THREW,

    /** 그룹 만들기는 받아들여졌는데 정한 시간 안에 그룹이 서지 않았다. */
    NOT_FORMED,

    /** 켜져 있던 우리 그룹이 밖에서 내려갔다(Wi-Fi 끄기·다른 앱이 Wi-Fi Direct 를 가져감·프레임워크와의 연결이 끊김). */
    GROUP_LOST,

    /** removeGroup 이 실패(덧말: 코드 또는 예외 이름)했고 그룹이 아직 떠 있다. */
    REMOVE_FAILED,
}
