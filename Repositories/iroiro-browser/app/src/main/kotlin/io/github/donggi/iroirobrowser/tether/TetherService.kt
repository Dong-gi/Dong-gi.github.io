package io.github.donggi.iroirobrowser.tether

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcelable
import android.util.Log
import android.widget.Toast
import io.github.donggi.iroirobrowser.BuildConfig
import io.github.donggi.iroirobrowser.R
import io.github.donggi.iroirobrowser.tether.core.FileServer
import io.github.donggi.iroirobrowser.tether.core.GroupWatch
import io.github.donggi.iroirobrowser.tether.core.HttpProxyServer
import io.github.donggi.iroirobrowser.tether.core.PassphraseVault
import io.github.donggi.iroirobrowser.tether.core.PeerFilter
import io.github.donggi.iroirobrowser.tether.core.Socks5Server
import io.github.donggi.iroirobrowser.tether.core.StartError
import io.github.donggi.iroirobrowser.tether.core.TcpServer
import io.github.donggi.iroirobrowser.tether.core.TetherEnvironment
import io.github.donggi.iroirobrowser.tether.core.TetherLog
import io.github.donggi.iroirobrowser.tether.core.ThreadBudget

/**
 * 테더의 포그라운드 서비스(`specialUse`) — **`:tether` 프로세스**에서 돈다(브라우저의 WebView·중계·프록시 계정이 올라오지 않는 프로세스 —
 * WebApp.onCreate·CLAUDE.md '테더링'). **떠 있는 것이 곧 테더링이 켜진 것이다** — 켜기·끄기는 하나다(사용자 결정 2026-10-10: '서버 3종과
 * 핫스팟의 시작/종료를 통합 … 셀룰러 데이터만으로 모든 기기에서 인터넷을 사용하도록 하기 위하여 만든 앱'). 시작 요청([start])이 오면 서버
 * 셋과 Wi-Fi Direct 핫스팟을 함께 세우고, 멈추면([stop] — 서비스를 멈춘다) [onDestroy] 가 함께 내린다:
 *
 *  - SOCKS5 `0.0.0.0:1080` — PC 의 tun2proxy. **1080 하나** — 차 있으면 옮기지 않고 까닭을 보인다(PC 의 런처가 1080 만 쓴다)
 *  - HTTP 프록시 `0.0.0.0:8282` — PC 의 `HTTP_PROXY`. 차 있으면 8283…8291 로 옮겨 선다(사용자 결정 2026-10-10)
 *  - 파일 서버 `0.0.0.0:8080` — 차 있으면 8081…8089. 공유 폴더를 고르지 않아도 뜬다(브라우저에서 '폴더를 고르세요' 를 보는 편이 아무 반응이
 *    없는 것보다 알기 쉽다)
 *
 * 세운 포트(옮겨 섰으면 그 포트)와 실패의 까닭은 [TetherState] 에 둔다(화면·알림이 읽는다). 서버 하나가 서지 못해도 테더링은 켜진 채이고 그
 * 서버의 줄에 까닭이 보인다. 돌던 서버의 받기 고리가 죽으면 그 서버는 포트를 놓고 내려가며 여기서 상태를 내리고 다음 시작 요청에서 다시 세운다.
 * 자격 증명은 인텐트에 싣지 않고 이 서비스가 설정([TetherSettings])에서 읽는다(usb-tether 는 패스프레이즈를 인텐트의 덧붙임으로
 * 넘겼다).
 *
 * **핫스팟이 서지 못하거나(권한·위치·Wi-Fi 꺼짐·BUSY·15초 안에 서지 않음 …) 켜진 뒤 밖에서 내려가면(Wi-Fi 끄기·Channel 을 잃음) 테더링을
 * 통째로 끈다**([endTethering]) — 핫스팟 없는 서버는 어떤 손님도 받지 못한다(손님 거르기 — 우리 그룹이 없으면 NO_GROUP). 까닭은
 * [TetherState.hotspotProblem] 에 남아 화면이 보인다.
 *
 * 그룹의 소식은 Wi-Fi Direct 의 연결 변경 방송을 **동적으로**(내보내지 않게) 받아 안다 — 켜져 있던 우리 그룹이 밖에서 내려가면 조회로 확인하고
 * 테더링을 끈다(예전에는 '켜짐' 이 남았다). 손님 거르개는 그 그룹의 인터페이스 이름([TetherState.groupInterface])과 견줘 우리
 * 그룹이 떠 있을 때만 받는다.
 *
 * `START_STICKY` 이고 매니페스트의 `stopWithTask="false"` 와 [onTaskRemoved] 로 최근 앱에서 밀어 없애도 다시 선다(usb-tether 의 동작 그대로).
 * 시스템이 다시 세우는 sticky 포그라운드 서비스는 안드로이드 12 의 '뒤에서 포그라운드 서비스 시작하기' 제한의 예외다. 시작 요청은 모두 '켜기'
 * 라 그 재시작도 핫스팟을 다시 켜 본다 — 이 앱의 화면이 앞에 없어 BUSY 면 떠 있는 우리 그룹을 다시 쓰고(같은 SSID·패스프레이즈), 그것도 없으면
 * 테더링을 끄고 까닭을 남긴다.
 *
 * 알림 번호는 [NOTICE_ID](4) — 앱 전체의 표는 CLAUDE.md '테더링' 의 '알림 번호'. 채널은 테더 전용(`tether`, 이름 '테더링'). 알림에는 단추가
 * 하나('테더링 끄기')다.
 */
class TetherService : Service() {

    private var socks: Socks5Server? = null
    private var http: HttpProxyServer? = null
    private var files: FileServer? = null
    private var hotspot: WifiHotspot? = null
    private lateinit var settings: TetherSettings
    private val main = Handler(Looper.getMainLooper())

    /** 서버 셋이 함께 쓰는 스레드 예산(ThreadBudget 의 머리말). */
    private val budget = ThreadBudget()

    /** 마지막으로 받은 시작 요청의 번호 — 스스로 멈출 때 그 뒤에 온 요청까지 버리지 않게(`stopSelfResult(startId)`). */
    private var lastStartId = 0

    /** 테더링을 끄기로 했고 시스템이 이 서비스를 내릴 참이다 — 알림을 다시 내걸지 않는다. 다음 시작 요청이 오면 되돌린다. */
    private var ending = false

    /** Wi-Fi Direct 의 연결 변경 — 그룹이 서고 내려가는 소식. 내보내지 않는다(13 이상 — 12 이하는 시스템만 보낼 수 있는 보호된 방송이다). */
    private val p2pReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION) return
            val info = intent.parcelable<WifiP2pInfo>(WifiP2pManager.EXTRA_WIFI_P2P_INFO)
            val group = intent.parcelable<WifiP2pGroup>(WifiP2pManager.EXTRA_WIFI_P2P_GROUP)
            onP2pChanged(
                formed = info?.groupFormed == true,
                owner = info?.isGroupOwner == true && group?.isGroupOwner == true,
                networkName = group?.networkName,
                interfaceName = group?.`interface`,
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        settings = TetherSettings(this)
        ensureChannel(this)
        TetherState.resetCounters()
        val filter = IntentFilter(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(p2pReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            // 12 이하에는 깃발이 없다 — 이 방송은 시스템만 보낼 수 있다(보호된 방송).
            registerReceiver(p2pReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        if (intent?.action == ACTION_STOP) {
            // 알림의 '테더링 끄기' — 사용자가 끈다(까닭을 남기지 않는다). 그 뒤에 온 켜기가 있으면 멈추지 않는다(그 요청이 이긴다).
            TetherState.clearProblem()
            ending = stopSelfResult(startId)
            return START_NOT_STICKY
        }
        // 시작 요청마다 곧바로 포그라운드에 선다(브라우저의 서비스들과 같은 차례 — CLAUDE.md '함정' 의 '포그라운드 서비스의 차례').
        if (!goForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        ending = false
        // 시작 요청은 모두 '테더링 켜기' 다 — 화면·트램폴린(타일)의 켜기, 최근 앱에서 밀어 없앤 뒤의 다시 세우기, 시스템이 프로세스를 다시
        // 띄운 sticky 재시작(intent 가 null). 이미 켜져 있으면 둘 다 아무것도 하지 않는다(받기 고리가 죽어 내려간 서버만 다시 세운다).
        TetherState.serviceRunning = true
        startServersIfNeeded()
        startHotspot()
        TetherState.changed()
        refreshNotice()
        // 핫스팟이 그 자리에서 실패해(저장값을 열지 못함·권한 …) 끄기로 했으면 시스템이 다시 세우지 않게.
        return if (ending) START_NOT_STICKY else START_STICKY
    }

    /**
     * 포그라운드로 선다. 시스템이 막으면(뒤에서 시작하기 제한 — `ForegroundServiceStartNotAllowedException`) 앱을 죽이지 않고 false — 화면이
     * '다시 켜세요' 를 보인다. 테더 프로세스가 죽으면 PC 의 인터넷이 끊기므로 여기서 던지게 두지 않는다.
     */
    private fun goForeground(): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTICE_ID, notice(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            // 14 밑에서는 매니페스트의 종류(specialUse)를 그대로 쓴다.
            startForeground(NOTICE_ID, notice())
        }
        TetherState.foregroundDenied = false
        true
    } catch (e: IllegalStateException) {
        Log.w(TAG, "포그라운드로 서지 못했다: ${e.javaClass.simpleName}")
        TetherState.foregroundDenied = true
        TetherState.changed()
        false
    }

    /**
     * 아직 뜨지 않은 서버를 띄운다. **실패한 서버는 들고 있지 않는다** — 들고 있으면 다음 시작 요청이 `== null` 만 보고 다시 해 보지 않아, 포트를
     * 쥔 앱을 끈 뒤에도 사용자가 서비스를 껐다 켜야 했다(usb-tether 의 주석 그대로). 받기 고리가 죽어 내려간 서버([onServerDied])도 그렇게
     * 비워 두므로 여기서 다시 선다. 상태에 두는 포트는 실제로 묶인 것이다(HTTP·파일은 옮겨 섰을 수 있다).
     */
    private fun startServersIfNeeded() {
        val env = environment()
        if (socks == null) {
            val s = Socks5Server(env)
            TetherState.socksPort = s.start()
            TetherState.socksError = s.lastError
            if (TetherState.socksPort > 0) socks = s
        }
        if (http == null) {
            val h = HttpProxyServer(env)
            TetherState.httpPort = h.start()
            TetherState.httpError = h.lastError
            if (TetherState.httpPort > 0) http = h
        }
        if (files == null) {
            val f = FileServer(env, SharedFolder(this, settings), ui = { assets.open(UI_ASSET).use { it.readBytes() } })
            TetherState.filePort = f.start()
            TetherState.fileError = f.lastError
            if (TetherState.filePort > 0) files = f
        }
    }

    private fun environment(): TetherEnvironment = TetherEnvironment(
        // 우리 그룹이 떠 있고 그룹 오너 주소가 정확히 그 그룹의 인터페이스에 붙었을 때만 받는다. 들어온 망 보기(받은 소켓의
        // SO_MARK)는 앱이 읽을 공개 API 가 없어 넣지 않는다 — PeerFilter 의 머리말 4.
        peers = PeerFilter(groupInterface = { TetherState.groupInterface }),
        budget = budget,
        log = LOG,
        onBytesIn = { TetherState.bytesIn.addAndGet(it) },
        onBytesOut = { TetherState.bytesOut.addAndGet(it) },
        // 연결마다 바뀌는 수는 알리지 않는다 — 화면이 1초마다 읽는다(거절이 몰릴 때 메인 스레드를 밀지 않게).
        onRejected = {
            TetherState.rejected.incrementAndGet()
            TetherState.lastRejection = it
        },
        onRefused = { TetherState.countRefusal(it) },
        onServerDied = { server -> main.post { onServerDied(server) } },
    )

    /**
     * 돌던 서버의 받기 고리가 죽어 서버가 포트를 놓고 내려갔다(메인 스레드). 들고 있던 것을 비우고 상태를 내린다 — 다음 시작 요청(테더링을 껐다
     * 켜기·최근 앱에서 밀어 없앤 뒤의 다시 세우기)이 새로 세운다. 화면은 그 서버의 줄에 까닭을 보인다. 테더링은 켜진 채다.
     */
    private fun onServerDied(server: TcpServer) {
        when {
            server === socks -> {
                socks = null
                TetherState.socksPort = -1
                TetherState.socksError = StartError.DIED
            }
            server === http -> {
                http = null
                TetherState.httpPort = -1
                TetherState.httpError = StartError.DIED
            }
            server === files -> {
                files = null
                TetherState.filePort = -1
                TetherState.fileError = StartError.DIED
            }
            else -> return
        }
        TetherState.changed()
        refreshNotice()
    }

    /** 핫스팟을 켠다 — 이미 켜는 중이거나 켜져 있으면 아무것도 하지 않는다. 서지 못하면 테더링을 통째로 끈다([endTethering]). */
    private fun startHotspot() {
        if (hotspot != null) return
        TetherState.hotspotProblem = null
        TetherState.hotspotDetail = null
        // 화면과 같은 값 — 같은 프로세스의 보관소가 쥔다. 잠근 값을 열지 못했으면 새 난수로 켜지 않고 까닭을 보인다.
        val passphrase = when (val current = settings.passphrase()) {
            is PassphraseVault.Current.Ready -> current.passphrase
            PassphraseVault.Current.Unreadable -> {
                endTethering(HotspotProblem.SECRET_UNREADABLE, null)
                return
            }
        }
        // start() 전에 넣는다 — 사전 점검의 실패는 결과를 그 자리에서 부르므로, 뒤에 넣으면 그 안에서 비운 것을 덮어쓴다.
        val hs = WifiHotspot(this, settings.ssid(), passphrase, onChannelLost = ::onChannelLost)
        hotspot = hs
        TetherState.hotspotStarting = true
        hs.start { ok ->
            // 늦게 온 결과는 거른다 — 켜기 → 끄기 → 켜기 사이에 앞 인스턴스의 결과가 남아 있을 수 있다. 거르지 않으면 그것이 새 인스턴스의 상태를
            // 덮고 `hotspot` 을 비워 **새 인스턴스가 만든 그룹을 내릴 길을 없앤다**(화면·타일은 꺼짐인데 무선망은 떠 있다).
            if (hotspot !== hs) {
                hs.release()
                return@start
            }
            TetherState.hotspotStarting = false
            TetherState.hotspotActive = ok
            TetherState.hotspotSsid = if (ok) hs.displaySsid else null
            TetherState.groupInterface = if (ok) hs.groupInterface else null
            if (!ok) {
                // 그룹을 만들지 못한 인스턴스도 Channel 을 쥐고 있다 — 닫는다. stop() 이 아니라 release() — 만든 적 없는 그룹에 removeGroup 을
                // 걸면 다른 앱의 그룹을 내릴 수 있다(서지 않은 우리 그룹은 WifiHotspot 이 이미 내렸다).
                hotspot = null
                hs.release()
                endTethering(hs.problem, hs.detail)
                return@start
            }
            TetherState.changed()
            refreshNotice()
        }
    }

    /**
     * 핫스팟이 서지 못했거나 켜진 뒤 내려갔다 — **테더링을 통째로 끈다**(사용자 결정 2026-10-10 — 켜기·끄기는 하나). 핫스팟 없는 서버는 어떤
     * 손님도 받지 못한다(손님 거르기 — 우리 그룹이 없으면 NO_GROUP). 까닭은 상태에 남겨 화면(상태 줄)이 보이고 토스트로도 알린다 —
     * [onDestroy] 는 그것을 지우지 않는다. 그 사이 새 시작 요청이 와 있으면 멈추지 않는다(`stopSelfResult(startId)` — 그 요청이 핫스팟을 다시
     * 켠다).
     */
    private fun endTethering(problem: HotspotProblem?, detail: String?) {
        clearHotspotState()
        TetherState.hotspotProblem = problem
        TetherState.hotspotDetail = detail
        Log.w(TAG, "핫스팟이 서지 못했거나 내려가 테더링을 끈다: $problem")
        toast(applicationContext, problem, detail)
        TetherState.changed()
        ending = stopSelfResult(lastStartId)
    }

    private fun clearHotspotState() {
        TetherState.hotspotActive = false
        TetherState.hotspotStarting = false
        TetherState.hotspotSsid = null
        TetherState.groupInterface = null
    }

    /**
     * Wi-Fi Direct 의 연결 변경 방송(메인 스레드). 우리 그룹이 섰다고 하면 기다리던 인스턴스에 알리고(조회보다 빠르다), 켜져 있던 우리 그룹이
     * 없다고 하면 늦게 온 옛 방송일 수 있어 **조회로 확인한 뒤** 테더링을 끈다.
     */
    private fun onP2pChanged(formed: Boolean, owner: Boolean, networkName: String?, interfaceName: String?) {
        // 핫스팟이 없으면(테더링을 끄는 사이) 볼 것이 없다.
        val hs = hotspot ?: return
        val ours = GroupWatch.ourInterface(formed, owner, networkName, interfaceName, hs.displaySsid)
        if (ours != null) {
            hs.noteGroup(ours)
            if (TetherState.hotspotActive && TetherState.groupInterface != ours) {
                TetherState.groupInterface = ours
                TetherState.changed()
            }
            return
        }
        // 켜는 중의 소식은 WifiHotspot 의 조회가 맡는다(그룹이 서기 전의 '아직 없음' 일 수 있다).
        if (!TetherState.hotspotActive) return
        hs.confirm { stillUp ->
            if (hotspot === hs && stillUp == null) hotspotLost(hs)
        }
    }

    /** 프레임워크와의 Channel 이 끊겼다 — 그룹을 더 볼 수도 내릴 수도 없다. 켜는 중이었으면 실패, 켜져 있었으면 내려간 것으로 친다. */
    private fun onChannelLost(hs: WifiHotspot) {
        if (hotspot !== hs) return
        hotspotLost(hs)
    }

    /** 켜져 있던(또는 켜는 중이던) 우리 그룹이 밖에서 내려갔다 — 테더링을 끈다. 그룹은 이미 없으니 내리지 않고 Channel 만 닫는다. */
    private fun hotspotLost(hs: WifiHotspot) {
        hotspot = null
        hs.release()
        Log.w(TAG, "우리 Wi-Fi Direct 그룹이 밖에서 내려갔다")
        endTethering(HotspotProblem.GROUP_LOST, null)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 최근 앱에서 밀어 없앨 때 다시 세운다(usb-tether 의 동작 그대로 — stopWithTask=false 와 함께). 켜져 있는 동안이면 이 시작 요청은
        // 아무것도 바꾸지 않는다(이미 선 서버·핫스팟). 시스템이 막으면 그대로 둔다.
        try {
            startForegroundService(Intent(applicationContext, TetherService::class.java))
        } catch (e: IllegalStateException) {
            Log.w(TAG, "다시 세우지 못했다: ${e.javaClass.simpleName}")
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(p2pReceiver)
        } catch (e: IllegalArgumentException) {
            // 걸지 못했다(onCreate 가 중간에 던짐) — 걷을 것이 없다.
        }
        // 끄기는 핫스팟과 서버를 함께 내린다(켜기·끄기는 하나 — 사용자 결정 2026-10-10). 그룹을 내린 결과는 이 서비스가 내려간 뒤에 온다 —
        // 내리지 못했고 그룹이 정말 남았으면(WifiHotspot.stop 이 조회로 확인한다) 까닭을 남긴다. 그 사이 테더링을 다시 켰으면 덮지 않는다.
        val hs = hotspot
        hotspot = null
        val app = applicationContext
        hs?.stop { removed ->
            if (!removed && !TetherState.serviceRunning) {
                TetherState.hotspotProblem = hs.problem
                TetherState.hotspotDetail = hs.detail
                toast(app, hs.problem, hs.detail)
                TetherState.changed()
            }
        }
        files?.stop()
        http?.stop()
        socks?.stop()
        files = null
        http = null
        socks = null
        TetherState.serviceRunning = false
        clearHotspotState()
        TetherState.socksPort = -1
        TetherState.httpPort = -1
        TetherState.filePort = -1
        TetherState.socksError = null
        TetherState.httpError = null
        TetherState.fileError = null
        TetherState.changed()
        super.onDestroy()
    }

    private fun refreshNotice() {
        if (!TetherState.serviceRunning || ending) return
        getSystemService(NotificationManager::class.java).notify(NOTICE_ID, notice())
    }

    /** 알림 — 테더링이 켜진 동안만(포그라운드 서비스의 알림). 핫스팟(SSID)과 실제 포트, 단추 하나 '테더링 끄기'. 누르면 테더 화면. */
    private fun notice(): Notification {
        val ports = getString(
            R.string.tether_notice_ports,
            TetherTexts.port(TetherState.socksPort),
            TetherTexts.port(TetherState.httpPort),
            TetherTexts.port(TetherState.filePort),
        )
        val title = TetherState.hotspotSsid?.let { getString(R.string.tether_notice_on, it) } ?: getString(R.string.tether_notice_starting)
        val tap = PendingIntent.getActivity(
            this,
            0,
            Intent(this, TetherActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        // 끄기는 이 서비스로(서 있는 동안만 보이므로 이미 돌고 있다 — 포그라운드로 다시 설 일이 없어 getService).
        val stop = PendingIntent.getService(
            this,
            0,
            Intent(this, TetherService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_tether)
            .setContentTitle(title)
            .setContentText(ports)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(tap)
            .addAction(Notification.Action.Builder(null, getString(R.string.tether_toggle_stop), stop).build())
            .build()
    }

    companion object {
        private const val TAG = "WebTether"

        /**
         * 알림 번호. 앱 전체(같은 UID 라 프로세스를 나눠도 한 공간)의 번호: 내려받기 진행 1, 녹화·가로채기 2, 토렌트 진행 3, **테더 4**, 끝난
         * 내려받기 1000 + 번호(1 부터 늘어난다), 토렌트 소식 0x10000000 + (info-hash 를 흩은 24비트) — 겹치지 않는다(CLAUDE.md '테더링').
         */
        const val NOTICE_ID = 4

        private const val CHANNEL = "tether"

        /** 파일 서버의 화면(`GET /`). */
        private const val UI_ASSET = "tether/fileman.html"

        /** 알림의 '테더링 끄기'. 켜기에는 액션이 없다 — 시작 요청은 모두 켜기다. */
        private const val ACTION_STOP = "io.github.donggi.iroirobrowser.action.TETHER_STOP"

        /** 서버의 기록 — 목적지·손님의 주소는 싣지 않는다(TetherLog 의 규칙). 디버그 빌드만 verbose 까지. 거절·상한은 서버가 초당 한 줄로 묶는다. */
        private val LOG = TetherLog { level, tag, message, error ->
            when (level) {
                TetherLog.Level.VERBOSE -> if (BuildConfig.DEBUG) Log.v(tag, message)
                TetherLog.Level.INFO -> Log.i(tag, message)
                TetherLog.Level.WARN -> Log.w(tag, message)
                TetherLog.Level.ERROR -> Log.e(tag, message, error)
            }
        }

        fun ensureChannel(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.tether_channel), NotificationManager.IMPORTANCE_LOW),
            )
        }

        /**
         * 테더링을 켠다 — 서버 셋과 핫스팟을 함께(앞에 있는 화면에서 — 테더 화면·트램폴린. `createGroup` 은 이 앱의 화면이 앞에 있어야 선다).
         * 권한은 부르는 쪽이 먼저 본다. 시스템이 막으면 false — 앱을 죽이지 않는다.
         */
        fun start(context: Context): Boolean = try {
            context.startForegroundService(Intent(context, TetherService::class.java))
            true
        } catch (e: IllegalStateException) {
            Log.w(TAG, "서비스를 세우지 못했다: ${e.javaClass.simpleName}")
            false
        }

        /**
         * 테더링을 끈다 — 서비스를 멈추면 핫스팟과 서버가 함께 내려간다([onDestroy]). 사용자가 끈 것이라 지난 까닭을 지운다. 멈추기는 뒤에서도
         * 막히지 않는다(타일). 켜져 있을 때만 부른다([TetherState.tetheringOn] — 그때는 이미 포그라운드로 섰으므로 막 청한 시작을 끊어 앱이
         * 죽는 일이 없다).
         */
        fun stop(context: Context) {
            TetherState.clearProblem()
            context.stopService(Intent(context, TetherService::class.java))
        }

        /** 핫스팟의 까닭을 토스트로 — 서비스가 내려간 뒤에 온 결과도 쓰므로 애플리케이션 컨텍스트로. */
        private fun toast(context: Context, problem: HotspotProblem?, detail: String?) {
            if (problem == null) return
            val text = TetherTexts.hotspot(problem, detail)
            Toast.makeText(context, context.getString(text.res, *text.args.toTypedArray()), Toast.LENGTH_LONG).show()
        }

        /** 방송의 덧붙임 — 형식 있는 판은 14 부터 쓴다(13 의 그 판에는 결함이 있다 — AndroidX IntentCompat 이 같은 까닭으로 가른다). */
        private inline fun <reified T : Parcelable> Intent.parcelable(name: String): T? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                getParcelableExtra(name, T::class.java)
            } else {
                @Suppress("DEPRECATION")
                getParcelableExtra(name) as? T
            }
    }
}
