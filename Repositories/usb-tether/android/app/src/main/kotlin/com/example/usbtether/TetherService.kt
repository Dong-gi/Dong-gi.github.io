package com.example.usbtether

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.concurrent.atomic.AtomicLong

/**
 * Foreground service that runs a SOCKS5 proxy on 127.0.0.1:LOCAL_PORT.
 * PC connects via `adb forward tcp:1080 tcp:1080` and routes traffic through tun2proxy.
 * All outbound sockets are opened by Android's OS, so the carrier sees only normal
 * phone-originated traffic.
 */
class TetherService : Service() {

    private var server: Socks5Server? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
        Log.i(TAG, "TetherService starting SOCKS5 on port $LOCAL_PORT")

        tcpCount = 0
        bytesIn.set(0)
        bytesOut.set(0)

        server = Socks5Server(
            port = LOCAL_PORT,
            onTcpCount = { tcpCount = it },
            onBytesIn  = { bytesIn.addAndGet(it) },
            onBytesOut = { bytesOut.addAndGet(it) },
        ).also { it.start() }

        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.i(TAG, "TetherService stopping")
        server?.stop()
        isRunning = false
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "USB Tether", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "SOCKS5 proxy running"
                }
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("USB Tether")
            .setContentText("SOCKS5 proxy on localhost:$LOCAL_PORT")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val TAG = "TetherService"
        const val NOTIF_ID = 1
        const val CHANNEL_ID = "usb_tether"
        const val LOCAL_PORT = 1080

        @Volatile var isRunning = false
            private set

        @Volatile var tcpCount = 0
            internal set

        val bytesIn = AtomicLong(0)
        val bytesOut = AtomicLong(0)
    }
}
