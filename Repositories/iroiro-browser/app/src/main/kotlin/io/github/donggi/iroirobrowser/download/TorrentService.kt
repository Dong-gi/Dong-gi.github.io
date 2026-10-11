package io.github.donggi.iroirobrowser.download

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import io.github.donggi.iroirobrowser.R
import io.github.donggi.iroirobrowser.WebApp
import io.github.donggi.iroirobrowser.torrent.TorrentState
import io.github.donggi.iroirobrowser.torrent.TorrentStatus

/**
 * 토렌트를 받는 동안 프로세스를 붙잡는 포그라운드 서비스(`dataSync`)와 그 알림('받는 중' — 진행·속도·피어, 일시 정지). 엔진은 서비스 밖
 * ([TorrentCenter] — 프로세스에 하나)에 있고, 이 서비스는 붙잡는 일만 한다:
 *
 * - **받으려는 토렌트가 있거나 옮길 파일이 남은 동안만** 선다([TorrentRules.keepsForeground]) — 없으면 스스로 내려간다(사용자 결정 — 받을 것이
 *   없으면 서비스를 내린다). 망을 기다리는 동안도 선다(프로세스가 살아 있어야 망이 돌아올 때 이어 받는다).
 * - **화면이 꺼져도 받는다**(사용자 결정 — `WAKE_LOCK`): 받는 동안만 부분 wake lock 을 쥔다. 10분 동안 나아간 것이 없으면 놓는다
 *   ([TorrentRules.StallWatch] — 피어가 없어 기다리는 토렌트가 배터리를 다 쓰지 않게). 시간 제한(10분)을 걸고 1분마다 새로 건다 — 앱이 멈춰도
 *   잠금이 남지 않게.
 * - 그 동안 기본 망이 Wi-Fi 면 Wi-Fi 잠금(`WIFI_MODE_FULL_HIGH_PERF` — 화면이 꺼진 동안에도 Wi-Fi 가 절전으로 내려가지 않는다. Android 14 부터
 *   플랫폼이 이것을 LOW_LATENCY(화면이 켜져 있고 앞에 있을 때만)로 바꿔 끼운다 — 사용자의 폰(12)에서는 문서대로 든다).
 *
 * 내려받기([DownloadService])와 따로 된 서비스·알림(번호 [ONGOING_ID])이다 — 붙잡는 것(잠금)과 단추(토렌트마다 멈춤·이어받기)가 다르고, 내려받기
 * 서비스의 서고 내리는 차례(7단계의 '포그라운드 서비스의 차례')를 건드리지 않으려고. 채널은 함께 쓴다(사용자에게는 같은 '내려받기' 다).
 * 서고 내리는 차례는 내려받기와 같다 — 시작 요청마다 곧바로 서고, **마지막 요청의 번호로** 멈춘다.
 */
class TorrentService : Service() {

    private lateinit var center: TorrentCenter
    private var lastStartId = 0
    private var wakeLock: PowerManager.WakeLock? = null
    private var wakeRefreshedAt = 0L
    private var wifiLock: WifiManager.WifiLock? = null
    private val stall = TorrentRules.StallWatch()

    /** 마지막으로 낸 알림의 글 — 같으면 다시 내지 않는다(1초마다 고치면 알림이 늘 바쁘다). */
    private var lastNotice: String? = null
    private val listener = TorrentCenter.Listener { refresh() }

    override fun onCreate() {
        super.onCreate()
        center = WebApp.of(this).torrents
        DownloadService.ensureChannels(this)
        center.addListener(listener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 시작 요청마다 곧바로 포그라운드에 선다 — 시스템은 startForegroundService 마다 startForeground 를 기다리고, 그 전에 서비스가 내려가면
        // 앱을 죽인다(CLAUDE.md '함정' 의 '포그라운드 서비스의 차례').
        lastStartId = startId
        lastNotice = null
        startForeground(ONGOING_ID, ongoing(center.statuses()).first, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        val id = intent?.getStringExtra(EXTRA_TORRENT)
        when (intent?.action) {
            ACTION_PAUSE -> id?.let { center.pause(it, getString(R.string.torrent_paused_by_notice)) }
            ACTION_PAUSE_ALL -> center.pauseAll(getString(R.string.torrent_paused_by_notice))
            ACTION_RESUME -> id?.let { center.resume(it) }
        }
        refresh()
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        // Android 15 이상: dataSync 는 앱 전체가 하루 6시간까지(내려받기·녹화와 나눈다). 넘으면 시스템이 부른다 — 멈춰 두고(이어받을 수 있게)
        // 알림에 까닭과 '이어받기' 를 남기고 나간다.
        center.pauseAll(getString(R.string.torrent_paused_by_timeout))
        releaseLocks()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        center.removeListener(listener)
        releaseLocks()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun refresh() {
        val all = center.statuses()
        if (!TorrentRules.keepsForeground(all.map { it.state }, center.busy)) {
            // 첫 요청이 와서 선 뒤에만 멈춘다(내려받기 서비스와 같은 까닭 — 서지 않은 채 내려가면 앱이 죽는다).
            if (lastStartId == 0) return
            releaseLocks()
            stopForeground(STOP_FOREGROUND_REMOVE)
            // 그 사이 새 요청이 왔으면 번호가 맞지 않아 멈추지 않는다 — 곧 올 그 요청이 다시 세운다.
            stopSelf(lastStartId)
            return
        }
        holdLocks(all)
        val (notice, key) = ongoing(all)
        if (key == lastNotice) return
        lastNotice = key
        getSystemService(NotificationManager::class.java).notify(ONGOING_ID, notice)
    }

    /** 받는 동안만 CPU 를(그리고 Wi-Fi 면 Wi-Fi 를) 붙잡는다. 메인 스레드. */
    private fun holdLocks(all: List<TorrentStatus>) {
        val now = SystemClock.elapsedRealtime()
        if (!stall.needsCpu(now, all, center.moving)) {
            releaseLocks()
            return
        }
        val wake = wakeLock ?: getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_TAG)
            .apply { setReferenceCounted(false) }
            .also { wakeLock = it }
        if (!wake.isHeld || now - wakeRefreshedAt > WAKE_REFRESH_MS) {
            // 시간 제한을 건다 — 앱이 멈춰 놓지 못해도 잠금이 남지 않게. 다시 걸면 제한이 처음부터 센다(참조를 세지 않는 잠금).
            wake.acquire(WAKE_TIMEOUT_MS)
            wakeRefreshedAt = now
        }
        if (center.onWifi()) {
            @Suppress("DEPRECATION") // 아래 머리말 — 12 에서는 문서대로, 14 부터는 플랫폼이 LOW_LATENCY 로 바꿔 낀다
            val wifi = wifiLock ?: getSystemService(WifiManager::class.java)?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, WAKE_TAG)
                ?.apply { setReferenceCounted(false) }
                ?.also { wifiLock = it }
            if (wifi != null && !wifi.isHeld) wifi.acquire()
        } else {
            wifiLock?.takeIf { it.isHeld }?.release()
        }
    }

    private fun releaseLocks() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wifiLock?.takeIf { it.isHeld }?.release()
    }

    /**
     * 받는 중 알림과 그 글의 열쇠(같으면 다시 내지 않는다). 하나면 그 이름·상태 글([TorrentTexts.summary])·진행과 '일시 정지', 여럿이면 줄마다의
     * 상태와 '모두 일시 정지'. 받을 것은 없고 옮길 파일만 남았으면 그것을. 부제는 늘 '프록시를 거치지 않음' — 토렌트는 브라우저의 fail-closed 의
     * 예외다.
     */
    private fun ongoing(all: List<TorrentStatus>): Pair<Notification, String> {
        val running = all.filter { TorrentRules.isRunning(it.state) }
        val sizes = TorrentTexts.Sizes { android.text.format.Formatter.formatShortFileSize(this, it) }
        val builder = Notification.Builder(this, DownloadService.CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSubText(getString(R.string.torrent_direct_short))
            .setContentIntent(openList())
        val key: String
        when {
            running.isEmpty() -> {
                val text = getString(if (center.moving) R.string.torrent_notice_moving else R.string.torrent_notice_preparing)
                builder.setContentTitle(getString(R.string.torrent_title)).setContentText(text).setProgress(0, 0, true)
                key = text
            }
            running.size == 1 -> {
                val s = running[0]
                val title = TorrentTexts.text(this, TorrentTexts.title(s))
                val text = TorrentTexts.join(this, TorrentTexts.summary(s, center.extra(s), sizes))
                builder.setContentTitle(title).setContentText(text)
                val percent = TorrentTexts.percent(s)
                if (s.state == TorrentState.DOWNLOADING && percent >= 0) builder.setProgress(100, percent, false) else builder.setProgress(0, 0, true)
                builder.addAction(action(R.string.download_pause, ACTION_PAUSE, s.id))
                key = "${s.id}|$title|$text"
            }
            else -> {
                val lines = running.map { TorrentTexts.text(this, TorrentTexts.title(it)) + " · " + TorrentTexts.join(this, TorrentTexts.summary(it, center.extra(it), sizes)) }
                val style = Notification.InboxStyle()
                lines.take(MAX_LINES).forEach { style.addLine(it) }
                if (lines.size > MAX_LINES) style.setSummaryText(getString(R.string.download_more, lines.size - MAX_LINES))
                builder.setContentTitle(getString(R.string.torrent_many, running.size))
                    .setContentText(lines.first())
                    .setStyle(style)
                    .setProgress(0, 0, true)
                    .addAction(action(R.string.download_pause_all, ACTION_PAUSE_ALL, null))
                key = lines.joinToString("\n")
            }
        }
        return builder.build() to key
    }

    /** 알림 단추 — 이 서비스에 맡긴다(눌렀을 때 포그라운드로 서야 하므로 `getForegroundService`). */
    private fun action(label: Int, what: String, id: String?): Notification.Action {
        val intent = PendingIntent.getForegroundService(
            this,
            (id?.let { TorrentRules.noticeId(it) } ?: 0) + what.hashCode(),
            Intent(this, TorrentService::class.java).setAction(what).apply { id?.let { putExtra(EXTRA_TORRENT, it) } },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Action.Builder(null, getString(label), intent).build()
    }

    private fun openList(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, DownloadsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        /** 내려받기(1)·녹화(2)의 알림과 겹치지 않게. */
        private const val ONGOING_ID = 3
        internal const val ACTION_PAUSE = "io.github.donggi.iroirobrowser.action.PAUSE_TORRENT"
        internal const val ACTION_PAUSE_ALL = "io.github.donggi.iroirobrowser.action.PAUSE_ALL_TORRENTS"
        internal const val ACTION_RESUME = "io.github.donggi.iroirobrowser.action.RESUME_TORRENT"
        internal const val EXTRA_TORRENT = "torrent"
        private const val WAKE_TAG = "Web:torrent"

        /** wake lock 의 시간 제한과 다시 거는 간격. */
        private const val WAKE_TIMEOUT_MS = 10 * 60_000L
        private const val WAKE_REFRESH_MS = 60_000L

        /** 여럿을 받을 때 알림에 보이는 줄의 수. */
        private const val MAX_LINES = 5

        /** 받기를 시작했다 — 서비스를 세운다(앞에 있을 때·알림의 단추에서 부른다). 시스템이 열어 주지 않으면 그대로 둔다(엔진은 그대로 돈다). */
        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, TorrentService::class.java))
            } catch (e: IllegalStateException) {
                // 뒤에서 시작하기 제한(ForegroundServiceStartNotAllowedException) — 앱을 죽이지 않는다. 그동안 프로세스가 죽으면 다음에 뜰 때
                // 멈춤으로 돌아와 이어받는다.
            }
        }
    }
}
