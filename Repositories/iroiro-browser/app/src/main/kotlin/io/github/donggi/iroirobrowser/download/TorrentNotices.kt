package io.github.donggi.iroirobrowser.download

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.github.donggi.iroirobrowser.R

/**
 * 토렌트 하나의 끝난 소식 — 다 받음·오류·멈춤·파일을 고르세요·옮기지 못함. 받는 중 알림은 서비스([TorrentService])가 내고, 이 소식들은 서비스가
 * 서 있지 않아도 낸다(엔진의 듣개가 부른다). 내려받기와 같은 '다 받음' 채널을 쓴다(사용자에게는 같은 '내려받기' 다 — [DownloadService.ensureChannels]).
 * 토렌트마다 알림 자리는 하나다([TorrentRules.noticeId]) — 새 소식이 앞 소식을 갈아 끼운다(멈춤 → 이어받기 → 다 받음).
 */
internal class TorrentNotices(private val app: Context) {

    private val nm: NotificationManager get() = app.getSystemService(NotificationManager::class.java)

    /** 다 받았다 — 누르면 목록(파일은 '내려받기' 폴더로 옮긴다 — 옮기는 중일 수 있다). */
    fun finished(id: String, title: String) {
        post(id, base(title).setContentText(app.getString(R.string.torrent_notice_finished)).setContentIntent(openList()))
    }

    /** 받기를 멈춘 오류 — 까닭과 '이어받기'(공간을 비웠으면 다시 할 수 있다). */
    fun error(id: String, title: String, reason: String) {
        val text = app.getString(R.string.torrent_notice_error, reason)
        post(
            id,
            base(title).setContentText(text).setStyle(Notification.BigTextStyle().bigText(text)).setContentIntent(openList())
                .addAction(action(id, R.string.download_resume, TorrentService.ACTION_RESUME)),
        )
    }

    /** 알림의 단추·시간 제한으로 멈췄다 — 이어받기를 알림에 남긴다(받는 중 알림은 사라진다). */
    fun paused(id: String, title: String, reason: String) {
        post(
            id,
            base(title).setContentText(reason).setStyle(Notification.BigTextStyle().bigText(reason)).setContentIntent(openList())
                .addAction(action(id, R.string.download_resume, TorrentService.ACTION_RESUME)),
        )
    }

    /** 마그넷의 파일 목록이 왔다 — 받을 파일을 고를 때까지 멈춰 있다. 누르면 목록 화면이 고르는 창을 연다. */
    fun chooseFiles(id: String, title: String) {
        val intent = Intent(app, DownloadsActivity::class.java)
            .setAction(DownloadsActivity.ACTION_CHOOSE_TORRENT)
            .putExtra(DownloadsActivity.EXTRA_TORRENT, id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val tap = PendingIntent.getActivity(app, TorrentRules.noticeId(id), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        post(id, base(title).setContentText(app.getString(R.string.torrent_notice_choose)).setContentIntent(tap))
    }

    /** 다 받은 파일을 '내려받기' 로 옮기지 못했다(세 번 해 본 뒤·공간 부족). */
    fun moveFailed(id: String, title: String, reason: String) {
        val text = app.getString(R.string.torrent_notice_move_failed, reason)
        post(id, base(title).setContentText(text).setStyle(Notification.BigTextStyle().bigText(text)).setContentIntent(openList()))
    }

    fun cancel(id: String) {
        nm.cancel(TorrentRules.noticeId(id))
    }

    private fun base(title: String): Notification.Builder {
        DownloadService.ensureChannels(app)
        return Notification.Builder(app, DownloadService.CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(title)
            .setSubText(app.getString(R.string.torrent_direct_short))
            .setAutoCancel(true)
    }

    private fun post(id: String, builder: Notification.Builder) {
        nm.notify(TorrentRules.noticeId(id), builder.build())
    }

    /** 알림 단추 — 서비스에 맡긴다(이어받으면 포그라운드로 서야 하므로 `getForegroundService`). */
    private fun action(id: String, label: Int, what: String): Notification.Action {
        val intent = PendingIntent.getForegroundService(
            app,
            TorrentRules.noticeId(id),
            Intent(app, TorrentService::class.java).setAction(what).putExtra(TorrentService.EXTRA_TORRENT, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Action.Builder(null, app.getString(label), intent).build()
    }

    private fun openList(): PendingIntent = PendingIntent.getActivity(
        app,
        0,
        Intent(app, DownloadsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE,
    )
}
