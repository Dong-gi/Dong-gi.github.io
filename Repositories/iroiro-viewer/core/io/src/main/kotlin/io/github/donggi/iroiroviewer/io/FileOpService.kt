package io.github.donggi.iroiroviewer.io

import io.github.donggi.iroiroviewer.model.IroDispatchers
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 파일 작업이 도는 동안 프로세스를 붙잡고 진행을 알린다.
 *
 * 작업 자체는 [FileOpManager] 가 한다. 이 서비스는 **살아 있는 것이 일**이다 —
 * 사용자가 앱을 나가도 5GB 복사가 끊기지 않게 하고, 알림으로 진행과 취소를 준다.
 *
 * `foregroundServiceType` 은 `dataSync` 이고, **API 34 부터는 타입마다 대응하는 권한이
 * 따로 필요하다.** `FOREGROUND_SERVICE_DATA_SYNC` 가 없으면 `startForeground` 가
 * `SecurityException` 으로 즉사한다 — 매니페스트에서 이 둘을 함께 본다.
 */
class FileOpService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + IroDispatchers.io)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startInForeground(buildNotification(null))
        scope.launch {
            FileOpManager.state.collectLatest { state ->
                when (state) {
                    is FileOpManager.State.Running -> notify(buildNotification(state))
                    else -> Unit
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            // **여기서 stopSelf 를 부르지 않는다.** 멈추는 것은 큐를 다 비운 워커의 일이다.
            // 서비스가 먼저 내려가면 남은 작업이 포그라운드 보호 없이 계속 돌게 된다.
            FileOpManager.cancelAll()
            return START_NOT_STICKY
        }
        // 다시 시작하지 않는다. 작업 목록은 메모리에만 있어서 되살려도 할 일이 없고,
        // 파일 작업을 사용자 모르게 다시 돌리는 것은 위험하다.
        return START_NOT_STICKY
    }

    /**
     * Android 15 부터 `dataSync` 서비스는 24시간에 6시간만 돌 수 있고, 넘기면 이것이
     * 불린다. **여기서 스스로 멈추지 않으면 시스템이 ANR 로 죽인다.**
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        Iro.e(message = "파일 작업이 시간 제한에 걸려 중단되었다")
        FileOpManager.cancelAll()
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /**
     * 이름을 `startForeground` 로 두면 Service 의 메서드를 가린다.
     *
     * **실패해도 앱을 죽이지 않는다.** 백그라운드 시작 제한에 걸리면 여기서 예외가
     * 나는데, 그것으로 프로세스가 죽으면 복사 중이던 파일이 반쪽으로 남는다. 알림
     * 없이 도는 편이 낫다. 성공을 [FileOpManager] 에 알리는 것도 중요하다 — 그 신호가
     * 오기 전에 정지 요청이 가면 시스템이 앱을 죽인다.
     */
    private fun startInForeground(notification: Notification) {
        val ok = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.isSuccess
        if (ok) {
            FileOpManager.onServiceForeground()
        } else {
            Iro.e(message = "포그라운드로 올라가지 못했다. 알림 없이 계속한다")
            stopSelf()
        }
    }

    private fun notify(notification: Notification) {
        getSystemService<NotificationManager>()?.notify(NOTIFICATION_ID, notification)
    }

    private fun createChannel() {
        val manager = getSystemService<NotificationManager>() ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.io_channel_file_ops),
                // 진행 알림은 소리를 내지 않는다. 복사 한 번에 알림음이 울리면 성가시다.
                NotificationManager.IMPORTANCE_LOW,
            )
        )
    }

    private fun buildNotification(state: FileOpManager.State.Running?): Notification {
        val cancelIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, FileOpService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = getString(
            when (state?.kind) {
                FileOpManager.Kind.COPY -> R.string.io_op_copying
                FileOpManager.Kind.MOVE -> R.string.io_op_moving
                FileOpManager.Kind.TRASH -> R.string.io_op_trashing
                FileOpManager.Kind.DELETE, FileOpManager.Kind.PURGE -> R.string.io_op_deleting
                FileOpManager.Kind.RESTORE -> R.string.io_op_restoring
                FileOpManager.Kind.ROTATE -> R.string.io_op_rotating
                FileOpManager.Kind.EXTRACT -> R.string.io_op_extracting
                // 정합성 검사는 알림을 띄우는 작업이 아니다. 여기 오면 준비 문구로 둔다.
                FileOpManager.Kind.RECONCILE, null -> R.string.io_op_preparing
            }
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, getString(R.string.io_op_cancel), cancelIntent)

        val p = state?.progress
        if (p != null && p.filesTotal > 0) {
            builder.setContentText(p.currentName)
            if (p.bytesTotal > 0) {
                val percent = ((p.bytesDone * 100) / p.bytesTotal).toInt().coerceIn(0, 100)
                builder.setProgress(100, percent, false)
            } else {
                builder.setProgress(p.filesTotal, p.filesDone, false)
            }
            builder.setSubText("${p.filesDone} / ${p.filesTotal}")
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    private companion object {
        const val CHANNEL_ID = "file_ops"
        const val NOTIFICATION_ID = 1001
        const val ACTION_CANCEL = "io.github.donggi.iroiroviewer.CANCEL_FILE_OP"
    }
}
