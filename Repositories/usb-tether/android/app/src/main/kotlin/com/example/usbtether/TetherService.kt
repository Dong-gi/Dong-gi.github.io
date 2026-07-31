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
 *   - SOCKS5 프록시: `0.0.0.0:SOCKS_BASE_PORT` 부터, 점유돼 있으면 +1 로 폴백
 *   - HTTP 프록시:   `0.0.0.0:HTTP_BASE_PORT` 부터, 점유돼 있으면 +1 로 폴백
 *
 * 실제로 바인딩된 포트는 [socksPort] / [httpPort] 에 게시되어 UI 에 표시된다.
 * 표시하거나 접속할 포트를 기본 상수에서 가져오면 안 된다.
 *
 * Wi-Fi Direct GO 는 배터리 소모가 크므로 ACTION_HOTSPOT_ON / ACTION_HOTSPOT_OFF 로
 * **따로** 토글한다. ACTION_HOTSPOT_ON 을 보낼 때 EXTRA_SSID 와 EXTRA_PASSPHRASE 로
 * 자격증명을 함께 전달한다.
 *
 * 외부로 나가는 모든 소켓은 Android OS 가 열기 때문에, 클라이언트가 어느 경로를
 * 썼는지와 무관하게 통신사가 보는 것은 평범한 폰 발신 트래픽뿐이다.
 */
class TetherService : Service() {

    private var socks: Socks5Server? = null
    private var http: HttpProxyServer? = null
    private var hotspot: WifiHotspot? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
        Log.i(TAG, "TetherService starting (SOCKS5 base:${Socks5Server.BASE_PORT}, HTTP base:${HttpProxyServer.BASE_PORT})")

        bytesIn.set(0)
        bytesOut.set(0)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (socks == null) {
            val s = Socks5Server(
                onBytesIn = { bytesIn.addAndGet(it) },
                onBytesOut = { bytesOut.addAndGet(it) },
            )
            socksPort = s.start()
            socks = s
        }
        if (http == null) {
            val h = HttpProxyServer(
                onBytesIn = { bytesIn.addAndGet(it) },
                onBytesOut = { bytesOut.addAndGet(it) },
            )
            httpPort = h.start()
            http = h
        }
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

    private fun startHotspot(ssid: String, passphrase: String) {
        if (hotspot != null) return
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
        hotspot?.stop()
        hotspot = null
        hotspotActive = false
        hotspotSsid = null
        hotspotError = null
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

        /** 실제 SOCKS5 포트(기본 또는 기본+1). 바인딩되지 않았으면 -1. */
        @Volatile var socksPort: Int = -1
            internal set
        /** 실제 HTTP 포트(기본 또는 기본+1). 바인딩되지 않았으면 -1. */
        @Volatile var httpPort: Int = -1
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
