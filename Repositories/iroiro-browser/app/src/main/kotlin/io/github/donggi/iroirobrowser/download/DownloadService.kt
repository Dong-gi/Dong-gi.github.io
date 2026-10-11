package io.github.donggi.iroirobrowser.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import io.github.donggi.iroirobrowser.R
import io.github.donggi.iroirobrowser.WebApp

/**
 * 받는 동안 앱이 뒤로 가도 죽지 않게 하는 포그라운드 서비스(`dataSync`). 받는 것이 없어지면 스스로 멈춘다. 알림 하나에 진행을 싣고
 * (하나면 '일시 정지'·'받기 취소(지움)' 단추, 여럿이면 줄마다의 진행과 '모두 일시 정지'), 끝난 것은 따로 알린다 — 다 받은 것(누르면 **기본 앱으로**
 * 연다), 멈춘 것('이어받기' 단추 — 주소가 만료됐으면 그 까닭과 '원래 페이지 열기'), 실패한 것. 갤러리 받기는 단위로 센 진행과 받은 파일 수,
 * 취소 단추는 '그만 받기'(받은 파일을 남긴다), 멈추면 '원래 페이지 열기' 도(로그인·봇 확인을 그 페이지에서 푼다).
 */
class DownloadService : Service() {

    private lateinit var center: DownloadCenter
    private val notified = HashMap<Long, DownloadCenter.Status>()

    /** 마지막으로 받은 시작 요청의 번호. 0 이면 아직 하나도 받지 못했다([refresh] 가 멈추지 않는다). */
    private var lastStartId = 0

    private val listener = DownloadCenter.Listener { item ->
        if (item != null) {
            if (item.isActive) {
                // 이어받기를 눌렀다 — 멈춤 알림을 거둔다.
                if (notified.remove(item.id) != null) getSystemService(NotificationManager::class.java).cancel(FINISHED_BASE + item.id.toInt())
            } else if (notified[item.id] != item.status) {
                notified[item.id] = item.status
                notifyFinished(item)
            }
        }
        refresh()
    }

    override fun onCreate() {
        super.onCreate()
        center = WebApp.of(this).downloads
        ensureChannels(this)
        center.addListener(listener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // **시작 요청마다 곧바로 포그라운드에 선다**(이미 서 있으면 알림만 바뀐다). `startForegroundService` 의 요청마다 시스템은
        // `startForeground` 를 기다리고, 그 전에 서비스가 내려가면 앱을 죽인다(ForegroundServiceDidNotStartInTimeException — 2026-10-05
        // 가로채기를 저장한 직후 실험실에서 실제로 죽었다). 예전에는 처음 한 번만 섰다 — [refresh] 가 포그라운드를 내린 뒤에 온 새 요청은
        // 서지 않은 채 남았다.
        lastStartId = startId
        startForeground(ONGOING_ID, ongoing(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        val target = intent?.getLongExtra(EXTRA_ID, -1)?.let { center.find(it) }
        when (intent?.action) {
            ACTION_RESUME -> target?.let { center.resume(it) }
            ACTION_PAUSE -> target?.let { center.pause(it) }
            ACTION_CANCEL -> target?.let { center.cancel(it) }
            ACTION_PAUSE_ALL -> center.items().filter { it.isActive }.forEach { center.pause(it) }
        }
        refresh()
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        // Android 15 이상: dataSync 는 하루 6시간까지다. 넘으면 시스템이 부른다 — 받던 것은 멈춰 두고(이어받을 수 있게) 나간다.
        center.items().filter { it.isActive }.forEach { center.pause(it) }
        stopSelf()
    }

    override fun onDestroy() {
        center.removeListener(listener)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun refresh() {
        if (center.activeCount() == 0) {
            // 끝났다는 알림은 메인 스레드에 줄 서 오므로 **서비스가 생기고 첫 요청이 닿기 전에** 올 수 있다(마무리처럼 몇 ms 에 끝나는 일).
            // 그때 멈추면 시스템이 기다리던 `startForeground` 가 오지 않은 채 서비스가 내려가 앱이 죽는다 — 첫 요청이 와서 선 뒤에 멈춘다.
            if (lastStartId == 0) return
            stopForeground(STOP_FOREGROUND_REMOVE)
            // **마지막으로 받은 요청의 번호로** 멈춘다. 그 사이 새 받기가 `startForegroundService` 를 불렀으면 시스템이 그 요청을 이미
            // 적어 두어 번호가 맞지 않고, 서비스는 멈추지 않는다 — 곧 올 그 요청이 다시 포그라운드에 세운다.
            stopSelf(lastStartId)
            return
        }
        getSystemService(NotificationManager::class.java).notify(ONGOING_ID, ongoing())
    }

    /**
     * 받는 중 알림. 하나면 그 진행과 '일시 정지'(이을 수 있는 것만)·'받기 취소(지움)' 단추, 여럿이면 줄마다의 진행(앞 판은 첫 것의 진행만
     * 보였다)과 '모두 일시 정지'.
     */
    private fun ongoing(): Notification {
        val active = center.items().filter { it.isActive }
        val builder = Notification.Builder(this, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openList())
        if (active.size <= 1) {
            val one = active.firstOrNull()
            val gallery = one?.gallery
            builder.setContentTitle(one?.fileName ?: getString(R.string.download_title))
            when {
                one == null -> builder.setProgress(0, 0, true)
                one.merging -> builder.setProgress(0, 0, true).setContentText(DownloadTexts.merging(this, one))
                one.retrying > 0 -> builder.setProgress(0, 0, true).setContentText(getString(R.string.download_retrying, one.retrying, one.error ?: ""))
                // 갤러리 받기 — 단위로 센 막대와 'n / m개' 또는 '작품 a / b · 파일 n'.
                gallery != null -> {
                    val (max, now) = GalleryRules.bar(gallery.units, gallery.unitsDone)
                    builder.setProgress(max, now, false).setContentText(DownloadTexts.gallery(this, gallery))
                }
                one.parts > 0 -> builder.setProgress(one.parts, one.partsDone, false)
                    .setContentText(getString(R.string.download_parts, one.partsDone, one.parts))
                one.total > 0 -> {
                    val percent = DownloadTexts.percent(one)
                    builder.setProgress(100, percent, false).setContentText("$percent%")
                }
                else -> builder.setProgress(0, 0, true)
            }
            if (one != null) {
                if (one.pausable && !one.merging) builder.addAction(action(R.string.download_pause, ACTION_PAUSE, one.id))
                // 갤러리의 취소는 받은 파일을 남긴다 — '받기 취소(지움)' 가 아니라 '그만 받기'.
                val cancel = if (one.kind == DownloadCenter.Kind.SET) R.string.gallery_stop else R.string.download_cancel
                builder.addAction(action(cancel, ACTION_CANCEL, one.id))
            }
        } else {
            val lines = active.map { "${it.fileName} · ${DownloadTexts.progress(this, it)}" }
            val style = Notification.InboxStyle()
            lines.take(MAX_LINES).forEach { style.addLine(it) }
            if (lines.size > MAX_LINES) style.setSummaryText(getString(R.string.download_more, lines.size - MAX_LINES))
            builder.setContentTitle(getString(R.string.download_many, active.size))
                .setContentText(lines.first())
                .setStyle(style)
                .setProgress(0, 0, true)
            if (active.any { it.pausable && !it.merging }) builder.addAction(action(R.string.download_pause_all, ACTION_PAUSE_ALL, -1))
        }
        return builder.build()
    }

    /** 알림 단추 — 이 서비스에 [what] 을 맡긴다(눌렀을 때 포그라운드로 서야 하므로 `getForegroundService`). */
    private fun action(label: Int, what: String, id: Long): Notification.Action {
        val intent = PendingIntent.getForegroundService(
            this,
            id.toInt(),
            Intent(this, DownloadService::class.java).setAction(what).putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Action.Builder(null, getString(label), intent).build()
    }

    private fun notifyFinished(item: DownloadCenter.Item) {
        if (item.status == DownloadCenter.Status.CANCELLED) return
        val nm = getSystemService(NotificationManager::class.java)
        val id = FINISHED_BASE + item.id.toInt()
        val builder = Notification.Builder(this, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(item.savedName)
            .setAutoCancel(true)
        when (item.status) {
            DownloadCenter.Status.DONE -> {
                // 누르면 기본 앱으로 연다(정해 둔 앱이 없으면 시스템이 고르는 창) — 앞 판은 누를 때마다 앱 고르기 창(chooser)이었다.
                val open = center.openIntent(item)
                val tap = if (open != null) {
                    PendingIntent.getActivity(this, item.id.toInt(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                } else {
                    openList()
                }
                // 갤러리는 받은 파일 수와(건너뛴 것이 있으면) 그 수 — 누르면 첫 파일을 연다(받은 것이 없으면 목록).
                val gallery = item.gallery
                val text = if (gallery != null) {
                    listOfNotNull(getString(R.string.gallery_notice_done, gallery.filesSaved), item.note).joinToString(" · ")
                } else {
                    item.note ?: getString(R.string.download_done)
                }
                builder.setContentText(text).setContentIntent(tap)
            }
            DownloadCenter.Status.PAUSED -> {
                // 주소가 만료됐으면 이어받기를 권하지 않고 까닭과 '원래 페이지 열기' 를 앞에 둔다(이어받기도 남긴다 — 잠깐 막혔던 것일 수 있다).
                val text = if (item.expired) item.error.orEmpty() else getString(R.string.download_paused_notice, item.error ?: "")
                builder.setContentText(text)
                    .setStyle(Notification.BigTextStyle().bigText(text))
                    .setContentIntent(openList())
                // 갤러리도 원래 페이지를 둔다 — 로그인이 필요하거나 사이트가 막았으면(봇 확인) 그 페이지에서 풀고 이어받는다.
                if ((item.expired || item.kind == DownloadCenter.Kind.SET) && item.pageUrl != null) {
                    val page = PendingIntent.getActivity(
                        this,
                        item.id.toInt(),
                        Intent(this, DownloadsActivity::class.java).setAction(DownloadsActivity.ACTION_OPEN_PAGE)
                            .putExtra(DownloadsActivity.EXTRA_ID, item.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                    builder.addAction(Notification.Action.Builder(null, getString(R.string.download_open_page), page).build())
                }
                builder.addAction(action(R.string.download_resume, ACTION_RESUME, item.id))
            }
            else -> builder.setContentText(getString(R.string.download_failed, item.error ?: "")).setContentIntent(openList())
        }
        nm.notify(id, builder.build())
    }

    private fun openList(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, DownloadsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        /** 받는 중·다 받음의 알림 채널 — 토렌트([TorrentService]·[TorrentNotices])도 함께 쓴다(사용자에게는 같은 '내려받기' 다). */
        internal const val CHANNEL_ONGOING = "downloads"
        internal const val CHANNEL_DONE = "downloads-done"
        private const val ONGOING_ID = 1
        private const val FINISHED_BASE = 1000
        private const val ACTION_RESUME = "io.github.donggi.iroirobrowser.action.RESUME_DOWNLOAD"
        private const val ACTION_PAUSE = "io.github.donggi.iroirobrowser.action.PAUSE_DOWNLOAD"
        private const val ACTION_CANCEL = "io.github.donggi.iroirobrowser.action.CANCEL_DOWNLOAD"
        private const val ACTION_PAUSE_ALL = "io.github.donggi.iroirobrowser.action.PAUSE_ALL_DOWNLOADS"
        private const val EXTRA_ID = "id"

        /** 여럿을 받을 때 알림에 보이는 줄의 수. */
        private const val MAX_LINES = 5

        fun ensureRunning(context: Context) {
            try {
                context.startForegroundService(Intent(context, DownloadService::class.java))
            } catch (e: IllegalStateException) {
                // 앱이 뒤에 있을 때 시스템이 포그라운드 서비스를 열어 주지 않을 수 있다(Android 12 의 '뒤에서 시작하기' 제한 —
                // `ForegroundServiceStartNotAllowedException`). 앱을 죽이지 않는다 — 받기는 일꾼에서 그대로 돌고, 그동안 프로세스가 죽으면
                // 받던 것은 '멈춤' 으로 돌아와 이어받을 수 있다.
            }
        }

        fun ensureChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ONGOING, context.getString(R.string.channel_downloads), NotificationManager.IMPORTANCE_LOW),
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_DONE, context.getString(R.string.channel_downloads_done), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }
}
