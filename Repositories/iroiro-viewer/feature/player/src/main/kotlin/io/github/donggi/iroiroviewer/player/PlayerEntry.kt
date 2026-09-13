package io.github.donggi.iroiroviewer.player

import android.content.Context
import android.content.Intent
import io.github.donggi.iroiroviewer.playback.PlayerLauncher

/**
 * [PlayerSupport][io.github.donggi.iroiroviewer.playback.PlayerSupport] 의 구현.
 * 꽂는 것은 `app` 이다(`IroiroApp.onCreate`).
 *
 * **`FLAG_ACTIVITY_NEW_TASK` 가 필요한 이유**는 부르는 쪽이 액티비티가 아닐 수 있기
 * 때문이다 — 미니 바는 컴포저블이고 거기서 얻는 `Context` 가 언제나 액티비티라는 보장이
 * 없다. 화면이 이미 떠 있으면 `launchMode="singleTask"` 가 새로 만들지 않고 앞으로
 * 가져온다.
 */
object PlayerEntry : PlayerLauncher {

    /** 들어가자마자 재생목록을 펼칠 것인가. `singleTask` 라 `onNewIntent` 로도 온다. */
    const val EXTRA_SHOW_PLAYLIST = "io.github.donggi.iroiroviewer.player.SHOW_PLAYLIST"

    override fun open(context: Context, showPlaylist: Boolean) {
        context.startActivity(intent(context, showPlaylist))
    }

    override fun intent(context: Context, showPlaylist: Boolean): Intent =
        Intent(context, PlayerActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(EXTRA_SHOW_PLAYLIST, showPlaylist)
}
