package io.github.donggi.iroirobrowser.browser

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import io.github.donggi.iroirobrowser.R

/**
 * 녹화·가로채기 동안 앱을 붙잡아 두는 포그라운드 서비스와 그 알림('녹화 중 · 1:23 · 12 MB' — 저장·취소).
 *
 * 앞 판은 녹화·가로채기 동안 서비스가 없어, 받는 중에 다른 앱에 다녀오는 사이 시스템이 앱을 정리하면 담은 것이 말없이 사라졌다 — 앱이 뜰 때 그
 * 폴더를 지우기도 했다. 이제는 받는 동안 서고(화면이 [start]), 받는 것이 없어지면 스스로 내려간다. 종류는 내려받기와 같은
 * `dataSync` 다 — 매니페스트의 `foregroundServiceType` 과 `FOREGROUND_SERVICE_DATA_SYNC` 권한이 짝이다(종류와 권한이 어긋나면
 * `startForeground` 가 예외로 앱을 죽인다). 그래도 앱이 정리되었으면 다음에 뜰 때 남은 것을 저장할지 묻는다([CaptureRecovery]).
 *
 * 무엇을 받는지는 화면이 [CaptureRegistry] 에 적고, 알림의 단추는 그 화면에 넘긴다. 내보내지 않는다(다른 앱이 저장·취소를 부르지 못하게).
 */
class CaptureService : Service() {

    private val main = Handler(Looper.getMainLooper())

    /** 마지막으로 받은 시작 요청의 번호 — 그 번호로 멈춘다(그 사이 새 요청이 왔으면 시스템이 멈추지 않는다 — DownloadService 와 같은 까닭). */
    private var lastStartId = 0

    private val tick = Runnable { refresh() }

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 요청마다 곧바로 포그라운드에 선다 — 시스템은 startForegroundService 마다 startForeground 를 기다리고, 그 전에 서비스가 내려가면 앱을
        // 죽인다(CLAUDE.md '함정' 의 '포그라운드 서비스의 차례').
        lastStartId = startId
        startForeground(NOTICE_ID, notice(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        when (intent?.action) {
            ACTION_SAVE -> CaptureRegistry.host?.onNoticeAction(save = true)
            ACTION_CANCEL -> CaptureRegistry.host?.onNoticeAction(save = false)
        }
        main.removeCallbacks(tick)
        main.postDelayed(tick, TICK_MS)
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        // Android 15 이상: dataSync 는 하루 6시간까지 — 넘으면 내려간다(받는 것은 화면에서 그대로 돈다. 앱이 정리되면 다음에 뜰 때 묻는다).
        main.removeCallbacks(tick)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        main.removeCallbacks(tick)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** 받는 것이 없어졌으면 내려가고, 아니면 알림의 글(받은 양·시간)을 고친다. */
    private fun refresh() {
        if (CaptureRegistry.snapshot().isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(lastStartId)
            return
        }
        getSystemService(NotificationManager::class.java).notify(NOTICE_ID, notice())
        main.postDelayed(tick, TICK_MS)
    }

    private fun notice(): Notification {
        val active = CaptureRegistry.snapshot()
        val first = active.firstOrNull()
        val title = when {
            active.size > 1 -> getString(R.string.capture_notice_many, active.size)
            first != null -> first.title
            else -> getString(R.string.capture_menu)
        }
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openBrowser())
        if (first != null) {
            builder.setContentText(CaptureText.status(this, first.recording, first.capture))
                .addAction(Notification.Action.Builder(null, getString(R.string.capture_save), action(ACTION_SAVE)).build())
                .addAction(Notification.Action.Builder(null, getString(R.string.capture_cancel), action(ACTION_CANCEL)).build())
        }
        return builder.build()
    }

    /** 알림의 단추 — 이 서비스로(서 있는 동안만 보이므로 이미 돌고 있다). */
    private fun action(name: String): PendingIntent = PendingIntent.getService(
        this,
        name.hashCode(),
        Intent(this, CaptureService::class.java).setAction(name),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** 알림을 누르면 브라우저로(덧붙임 없이 — 브라우저 화면은 내보낸 화면이라 덧붙임을 받지 않는다). */
    private fun openBrowser(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, BrowserActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val CHANNEL = "capture"

        /** 내려받기의 알림(1, 1000 + 번호)과 겹치지 않게. */
        private const val NOTICE_ID = 2
        private const val TICK_MS = 2_000L
        private const val ACTION_SAVE = "io.github.donggi.iroirobrowser.action.SAVE_CAPTURE"
        private const val ACTION_CANCEL = "io.github.donggi.iroirobrowser.action.CANCEL_CAPTURE"

        /** 받기를 시작했다 — 서비스를 세운다(앞에 있을 때 부른다). 시스템이 열어 주지 않으면 그대로 둔다(받기는 화면에서 돈다). */
        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, CaptureService::class.java))
            } catch (e: IllegalStateException) {
                // 뒤에서 시작하기 제한(ForegroundServiceStartNotAllowedException) — 앱을 죽이지 않는다.
            }
        }

        fun ensureChannel(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.channel_capture), NotificationManager.IMPORTANCE_LOW),
            )
        }
    }
}
