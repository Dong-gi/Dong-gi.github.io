package com.example.usbtether

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.util.concurrent.atomic.AtomicLong

/**
 * 포그라운드 서비스. 기동하면 항상 다음 두 서버를 띄운다.
 *   - SOCKS5 프록시: `0.0.0.0:`[Socks5Server.BASE_PORT] 고정. 점유돼 있으면
 *     폴백하지 않고 실패한다
 *   - HTTP 프록시:   `0.0.0.0:`[HttpProxyServer.BASE_PORT] 부터 +9 까지 폴백하고,
 *     열 개가 모두 막혀 있으면 실패한다
 *
 * 어느 쪽이 실패하든 원인이 [proxyError] 로 올라온다. 실제로 바인딩된 포트는
 * [socksPort] / [httpPort] 에 게시되어 UI 에 표시된다. 표시하거나 접속할 포트를
 * 기본 상수에서 가져오면 안 된다.
 *
 * Wi-Fi Direct GO 는 배터리 소모가 크므로 ACTION_HOTSPOT_ON / ACTION_HOTSPOT_OFF 로
 * **따로** 토글한다. ACTION_HOTSPOT_ON 을 보낼 때 EXTRA_SSID 와 EXTRA_PASSPHRASE 로
 * 자격증명을 함께 전달한다.
 *
 * 외부로 나가는 모든 소켓은 Android OS 가 열기 때문에, 클라이언트가 어느 경로를
 * 썼는지와 무관하게 통신사가 보는 것은 평범한 폰 발신 트래픽이다 — 단, 헤더
 * 수준까지다. 페이로드로 드러나는 것에 대해서는 [Socks5Server] 의 KDoc 참고.
 */
class TetherService : Service() {

    private var socks: Socks5Server? = null
    private var http: HttpProxyServer? = null
    private var hotspot: WifiHotspot? = null

    /**
     * 각 프록시의 마지막 기동 실패 원인.
     *
     * 서버 인스턴스에 물어보지 않고 따로 들고 있는다. 실패한 인스턴스는 캐시하지
     * 않으므로([startProxiesIfNeeded]) 실패 직후 참조가 사라지기 때문이다.
     */
    private var socksError: String? = null
    private var httpError: String? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
        Log.i(TAG, "TetherService starting (SOCKS5 base:${Socks5Server.BASE_PORT}, HTTP base:${HttpProxyServer.BASE_PORT})")

        bytesIn.set(0)
        bytesOut.set(0)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startProxiesIfNeeded()
        refreshNotification()

        when (intent?.action) {
            ACTION_HOTSPOT_ON -> {
                val ssid = intent.getStringExtra(EXTRA_SSID).orEmpty()
                val pass = intent.getStringExtra(EXTRA_PASSPHRASE).orEmpty()
                startHotspot(ssid, pass)
            }
            ACTION_HOTSPOT_OFF -> stopHotspot()
        }

        isRunning = true
        return START_STICKY
    }

    /**
     * 아직 뜨지 않은 프록시를 기동한다.
     *
     * **실패한 인스턴스를 필드에 남기지 않는다.** 남기면 이후 onStartCommand 가
     * `== null` 검사만 보고 재시도하지 않으므로, 포트를 점유한 앱을 끈 뒤에도
     * 사용자가 서비스를 Stop/Start 해야 벗어난다.
     *
     * 두 실패 원인을 합쳐 [proxyError] 로 게시한다. SOCKS5 는 포트를 옮기지 않고
     * HTTP 는 10회 폴백 뒤 포기하는데, 어느 쪽이든 조용히 넘기면 UI 에는 이유 없는
     * `—` 만 남는다.
     */
    private fun startProxiesIfNeeded() {
        if (socks == null) {
            val s = Socks5Server(
                onBytesIn = { bytesIn.addAndGet(it) },
                onBytesOut = { bytesOut.addAndGet(it) },
            )
            socksPort = s.start()
            socksError = if (socksPort > 0) null else s.lastError
            if (socksPort > 0) socks = s
        }
        if (http == null) {
            val h = HttpProxyServer(
                onBytesIn = { bytesIn.addAndGet(it) },
                onBytesOut = { bytesOut.addAndGet(it) },
            )
            httpPort = h.start()
            httpError = if (httpPort > 0) null else h.lastError
            if (httpPort > 0) http = h
        }
        proxyError = listOfNotNull(socksError, httpError)
            .joinToString("\n")
            .ifEmpty { null }
    }

    private fun startHotspot(ssid: String, passphrase: String) {
        if (hotspot != null) {
            // 이미 기동 중이어도 상태를 알려야 한다. 타일이 클릭 직후
            // STATE_UNAVAILABLE 로 바꾸고 이 브로드캐스트를 기다리기 때문이다.
            broadcastHotspotState()
            return
        }
        if (ssid.isEmpty() || passphrase.isEmpty()) {
            hotspotError = "SSID/passphrase required"
            broadcastHotspotState()
            return
        }
        hotspotError = null
        // start() 호출 전에 대입해야 한다. WifiHotspot 의 사전 점검 실패 경로는
        // 결과 콜백을 동기적으로 호출하는데, .also { hs.start {...} } 형태로 쓰면
        // 그 콜백 안에서 동기적으로 설정한 `hotspot = null` 을 덮어써 버린다.
        val hs = WifiHotspot(applicationContext, ssid, passphrase)
        hotspot = hs
        hs.start { ok ->
            hotspotActive = ok
            hotspotSsid = if (ok) hs.displaySsid else null
            hotspotError = if (ok) null else hs.lastError
            if (!ok) hotspot = null
            broadcastHotspotState()
        }
    }

    private fun stopHotspot() {
        val hs = hotspot
        hotspot = null
        hotspotActive = false
        hotspotSsid = null
        hotspotError = null
        // 제거 결과를 기다리지 않고 먼저 off 를 게시한다 — 타일이 클릭 직후
        // STATE_UNAVAILABLE 로 바꾸고 이 브로드캐스트를 기다리기 때문이다.
        // 제거가 실패하면(그룹이 아직 살아 있을 수 있다) 콜백에서 사유를 붙여
        // 한 번 더 알린다.
        if (hs != null) {
            hs.stop { removed ->
                if (!removed) {
                    hotspotError = hs.lastError
                    broadcastHotspotState()
                }
            }
        }
        broadcastHotspotState()
    }

    private fun broadcastHotspotState() {
        hotspotError?.let {
            Toast.makeText(applicationContext, it, Toast.LENGTH_LONG).show()
        }
        sendBroadcast(Intent(ACTION_HOTSPOT_STATE_CHANGED).setPackage(packageName))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 사용자가 최근 앱 목록에서 앱을 밀어 없앨 때 서비스를 다시 띄운다.
        // 이 처리가 없으면 시스템이 onDestroy() 를 호출해 프록시 서버가 멈춘다.
        val restart = Intent(applicationContext, TetherService::class.java)
        restart.setPackage(packageName)
        startForegroundService(restart)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        Log.i(TAG, "TetherService stopping")
        hotspot?.stop()
        hotspot = null
        hotspotActive = false
        hotspotSsid = null
        hotspotError = null
        http?.stop()
        http = null
        socks?.stop()
        socks = null
        socksPort = -1
        httpPort = -1
        socksError = null
        httpError = null
        proxyError = null
        isRunning = false
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "USB Tether", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Proxy servers and Wi-Fi hotspot running"
                }
            )
        }
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val socksLabel = if (socksPort > 0) socksPort.toString() else "—"
        val httpLabel = if (httpPort > 0) httpPort.toString() else "—"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("USB Tether")
            .setContentText("SOCKS5:$socksLabel  HTTP:$httpLabel")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .build()
    }

    private fun refreshNotification() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification())
    }

    companion object {
        const val TAG = "TetherService"
        const val NOTIF_ID = 1
        const val CHANNEL_ID = "usb_tether"

        const val EXTRA_SSID = "ssid"
        const val EXTRA_PASSPHRASE = "passphrase"

        const val ACTION_HOTSPOT_ON = "com.example.usbtether.HOTSPOT_ON"
        const val ACTION_HOTSPOT_OFF = "com.example.usbtether.HOTSPOT_OFF"
        const val ACTION_HOTSPOT_STATE_CHANGED = "com.example.usbtether.HOTSPOT_STATE_CHANGED"

        @Volatile var isRunning = false
            private set

        /** SOCKS5 포트. 폴백이 없으므로 성공 시 항상 [Socks5Server.BASE_PORT], 실패 시 -1. */
        @Volatile var socksPort: Int = -1
            internal set
        /** 실제 HTTP 포트(기본 ~ 기본+9). 바인딩되지 않았으면 -1. */
        @Volatile var httpPort: Int = -1
            internal set

        /** 프록시 기동 실패 원인. 정상이면 null. */
        @Volatile var proxyError: String? = null
            internal set

        @Volatile var hotspotActive = false
            internal set
        @Volatile var hotspotSsid: String? = null
            internal set
        @Volatile var hotspotError: String? = null
            internal set

        val bytesIn = AtomicLong(0)
        val bytesOut = AtomicLong(0)
    }
}
