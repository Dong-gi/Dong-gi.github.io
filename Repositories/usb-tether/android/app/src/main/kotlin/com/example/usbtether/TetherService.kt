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
 * Foreground service. On start it always runs:
 *   - SOCKS5 proxy starting on 0.0.0.0:SOCKS_BASE_PORT, falling back to +1 if taken
 *   - HTTP proxy   starting on 0.0.0.0:HTTP_BASE_PORT, falling back to +1 if taken
 *
 * The actually-bound ports are published in [socksPort] / [httpPort] and surfaced
 * to the UI — never rely on the base constants for what to display or connect to.
 *
 * The Wi-Fi Direct GO is **toggled separately** via ACTION_HOTSPOT_ON / ACTION_HOTSPOT_OFF
 * (battery-expensive). When ACTION_HOTSPOT_ON is dispatched, EXTRA_SSID and EXTRA_PASSPHRASE
 * supply the credentials.
 *
 * All outbound sockets are opened by Android's OS, so the carrier sees only normal
 * phone-originated traffic regardless of the path the client used.
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
        // Assign before calling start() — WifiHotspot's preflight failure paths
        // invoke the result callback synchronously, and an .also { hs.start {...} }
        // chain would clobber a synchronous `hotspot = null` set inside that callback.
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
        // Restart the service when the user swipes the app away from recents.
        // Without this, the system calls onDestroy() and the proxy servers stop.
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

        /** Actual SOCKS5 port (base or base+1), -1 if not bound. */
        @Volatile var socksPort: Int = -1
            internal set
        /** Actual HTTP port (base or base+1), -1 if not bound. */
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
