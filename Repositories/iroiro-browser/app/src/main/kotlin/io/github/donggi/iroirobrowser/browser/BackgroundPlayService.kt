package io.github.donggi.iroirobrowser.browser

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import io.github.donggi.iroirobrowser.R

/**
 * 화면(BrowserActivity)과 뒤에서 재생 잇기의 서비스 사이 — 같은 프로세스·메인 스레드. 화면이 지금 회의 모양([state] — 없으면 회가 없다)을
 * 적고, 서 있는 서비스가 그것을 알림과 미디어 세션에 옮긴다. 알림·잠금 화면·이어폰 단추는 [controller](화면)로 간다.
 */
object BackgroundPlayback {

    /** 알림에 보일 회의 모양 — 제목, 재생 중인가. */
    data class State(val title: String, val playing: Boolean)

    /** 단추를 받는 화면. */
    interface Controller {
        fun play()
        fun pause()

        /** 알림의 '닫기' — 멈추고 회를 닫는다. */
        fun close()
    }

    var state: State? = null
        private set

    /** 단추를 받는 화면 — BrowserActivity 가 걸고 걷는다. */
    var controller: Controller? = null

    /** 서 있는 서비스가 듣는다(없으면 서비스가 없다). */
    internal var listener: (() -> Unit)? = null

    /**
     * 회의 모양을 바꾼다. 회가 생겼는데 서비스가 없으면 세운다 — 화면이 앞에 있거나 막 뒤로 가는 순간(onPause)에만 부른다(뒤에서는 포그라운드
     * 서비스를 세우지 못한다). 회가 없어지면 서비스가 스스로 내려간다.
     */
    fun update(context: Context, next: State?) {
        if (state == next) return
        state = next
        val running = listener
        if (running != null) {
            running()
        } else if (next != null) {
            try {
                context.startForegroundService(Intent(context, BackgroundPlayService::class.java))
            } catch (e: IllegalStateException) {
                // 뒤에서 시작하기 제한(ForegroundServiceStartNotAllowedException) — 앱을 죽이지 않는다. 재생은 서비스 없이 잇는다(화면이 꺼지면 멈출 수 있다).
            }
        }
    }
}

/**
 * 뒤에서 재생 잇기([BackgroundPlay])의 포그라운드 서비스(`mediaPlayback`)와 그 알림·미디어 세션(잠금 화면·이어폰 단추).
 *
 * 화면이 [BackgroundPlayback.update] 로 세우고, 회가 없어지면 스스로 내려간다(요청마다 곧바로 포그라운드에 서고 마지막 요청의 번호로 멈춘다 —
 * CLAUDE.md 함정 '포그라운드 서비스의 차례'). 서 있는 것만으로 앱이 정리되지 않고 Doze 에서도 망을 쓴다. 재생 중인 동안은 부분 wake lock 과
 * Wi-Fi 잠금도 쥔다 — 서비스 없이 화면을 끄자 재생이 멈췄다(2026-10-11 에뮬레이터). 멈춘 동안은 놓는다.
 *
 * 내보내지 않는다(다른 앱이 단추를 부르지 못하게). 작업이 지워지면(최근 앱에서 밀기) 시스템이 함께 멈춘다(`stopWithTask`) — 탭이 사라졌다.
 */
class BackgroundPlayService : Service() {

    private val main = Handler(Looper.getMainLooper())

    /** 마지막으로 받은 시작 요청의 번호 — 그 번호로 멈춘다(그 사이 새 요청이 왔으면 시스템이 멈추지 않는다). */
    private var lastStartId = 0

    private var session: MediaSession? = null
    private var shown: BackgroundPlayback.State? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeRefreshedAt = 0L
    private val tick = Runnable { refresh() }

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
        session = MediaSession(this, SESSION_TAG).apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    BackgroundPlayback.controller?.play()
                }

                override fun onPause() {
                    BackgroundPlayback.controller?.pause()
                }

                override fun onStop() {
                    BackgroundPlayback.controller?.close()
                }
            })
            isActive = true
        }
        BackgroundPlayback.listener = listener
    }

    /** [BackgroundPlayback] 에 거는 듣개 — 이 객체의 것인지 견주려고 한 번만 만든다. */
    private val listener: () -> Unit = { refresh() }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 요청마다 곧바로 포그라운드에 선다 — 시스템은 startForegroundService 마다 startForeground 를 기다린다.
        lastStartId = startId
        val state = BackgroundPlayback.state ?: shown ?: BackgroundPlayback.State(getString(R.string.bgplay_site), playing = false)
        startForeground(NOTICE_ID, notice(state), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        shown = state
        when (intent?.action) {
            ACTION_PLAY -> BackgroundPlayback.controller?.play()
            ACTION_PAUSE -> BackgroundPlayback.controller?.pause()
            ACTION_CLOSE -> BackgroundPlayback.controller?.close()
        }
        refresh()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        main.removeCallbacks(tick)
        releaseLocks()
        if (BackgroundPlayback.listener === listener) BackgroundPlayback.listener = null
        session?.release()
        session = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** 회가 없어졌으면 내려가고, 아니면 알림·세션·잠금을 지금 모양으로. 재생 중이면 1분마다 다시 본다(wake lock 의 시간 제한을 새로 건다). */
    private fun refresh() {
        main.removeCallbacks(tick)
        val state = BackgroundPlayback.state
        if (state == null) {
            // 내려가는 중에 새 회가 오면 새 서비스로 서게 듣개를 곧바로 걷는다(멈춤이 끝나기 전에 이 객체에 알림을 다시 내지 않게).
            if (BackgroundPlayback.listener === listener) BackgroundPlayback.listener = null
            releaseLocks()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(lastStartId)
            return
        }
        session?.let { s ->
            s.setMetadata(
                MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, state.title)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, getString(R.string.bgplay_site))
                    .build(),
            )
            s.setPlaybackState(
                PlaybackState.Builder()
                    .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP)
                    .setState(
                        if (state.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                        PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                        if (state.playing) 1f else 0f,
                    )
                    .build(),
            )
        }
        if (state != shown) {
            shown = state
            getSystemService(NotificationManager::class.java).notify(NOTICE_ID, notice(state))
        }
        if (state.playing) {
            holdLocks()
            main.postDelayed(tick, REFRESH_MS)
        } else {
            releaseLocks()
        }
    }

    /** 재생 중인 동안 CPU 와 Wi-Fi 를 붙잡는다(시간 제한을 건다 — 앱이 놓지 못해도 남지 않게). */
    private fun holdLocks() {
        val now = SystemClock.elapsedRealtime()
        val wake = wakeLock ?: getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_TAG)
            .apply { setReferenceCounted(false) }
            .also { wakeLock = it }
        if (!wake.isHeld || now - wakeRefreshedAt > REFRESH_MS) {
            wake.acquire(WAKE_TIMEOUT_MS)
            wakeRefreshedAt = now
        }
        @Suppress("DEPRECATION") // TorrentService 와 같다 — 12 에서는 문서대로, 14 부터는 플랫폼이 LOW_LATENCY 로 바꿔 낀다
        val wifi = wifiLock ?: getSystemService(WifiManager::class.java)?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, WAKE_TAG)
            ?.apply { setReferenceCounted(false) }
            ?.also { wifiLock = it }
        if (wifi != null && !wifi.isHeld) wifi.acquire()
    }

    private fun releaseLocks() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wifiLock?.takeIf { it.isHeld }?.release()
    }

    private fun notice(state: BackgroundPlayback.State): Notification {
        val toggle = if (state.playing) {
            Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_pause), getString(R.string.bgplay_pause), action(ACTION_PAUSE)).build()
        } else {
            Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_play), getString(R.string.bgplay_play), action(ACTION_PLAY)).build()
        }
        val close = Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_close), getString(R.string.bgplay_close), action(ACTION_CLOSE)).build()
        val style = Notification.MediaStyle().setShowActionsInCompactView(0, 1)
        session?.let { style.setMediaSession(it.sessionToken) }
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_play)
            .setContentTitle(state.title)
            .setContentText(getString(R.string.bgplay_site))
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(openBrowser())
            .addAction(toggle)
            .addAction(close)
            .setStyle(style)
            .build()
    }

    /** 알림의 단추 — 이 서비스로(서 있는 동안만 보이므로 이미 돌고 있다). */
    private fun action(name: String): PendingIntent = PendingIntent.getService(
        this,
        name.hashCode(),
        Intent(this, BackgroundPlayService::class.java).setAction(name),
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
        private const val CHANNEL = "playback"

        /** 앱 전체의 알림 번호 — 내려받기 1·녹화 2·토렌트 3·테더링 4(CLAUDE.md '알림 번호 — 앱 전체'). */
        const val NOTICE_ID = 5
        private const val SESSION_TAG = "Web"
        private const val WAKE_TAG = "Web:background-play"
        private const val REFRESH_MS = 60_000L
        private const val WAKE_TIMEOUT_MS = 10 * 60_000L
        private const val ACTION_PLAY = "io.github.donggi.iroirobrowser.action.BGPLAY_PLAY"
        private const val ACTION_PAUSE = "io.github.donggi.iroirobrowser.action.BGPLAY_PAUSE"
        private const val ACTION_CLOSE = "io.github.donggi.iroirobrowser.action.BGPLAY_CLOSE"

        fun ensureChannel(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.channel_playback), NotificationManager.IMPORTANCE_LOW),
            )
        }
    }
}
