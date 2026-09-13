package io.github.donggi.iroiroviewer.playback

import android.content.Context
import android.content.Intent

/**
 * 큰 재생 화면을 **여는** 한 함수짜리 이음매.
 *
 * ## 왜 필요한가
 *
 * 미니 바([MiniPlayer])는 `core:playback` 에 있고 재생 화면은 `feature:player` 에 있다.
 * core 가 feature 를 보면 **계층이 뒤집힌다.** 8단계의 `ExtractSupport.Runner`, 9단계의
 * `ui.CoverSupport` 와 **같은 형태의 문제**이고 같은 해법을 쓴다 — 화면을 전혀 모르는
 * 함수 하나를 여기 두고, 구현은 `feature:player` 가, 꽂는 것은 `app` 이 한다.
 *
 * ## 왜 `Context` 하나만 받는가
 *
 * 인텐트를 조립하는 일과 `launchMode="singleTask"` 선언은 **한 몸**이고 그 선언은
 * `feature:player` 의 매니페스트에 있다. 둘을 같은 모듈에 두어야 나중에 한쪽만 바뀌는 일이
 * 없다. `core:playback` 은 화면이 어떤 인텐트로 어느 태스크에 뜨는지 몰라야 한다 —
 * `CoverBytes.cover(path): ByteArray?` 가 포맷을 전혀 모르는 것과 같은 기준이다.
 */
interface PlayerLauncher {
    /**
     * 큰 재생 화면을 연다. 이미 떠 있으면 그것을 앞으로 가져온다.
     *
     * @param showPlaylist 들어가자마자 재생목록을 펼칠 것인가. 폴더의 ▶ 로 들어온 길과
     *   **소리 파일을 탭한 길**이 그렇다 — 앞은 '이 폴더를 재생목록으로 열어라' 이고,
     *   뒤는 그 화면에 볼 것이 제목 한 줄뿐이라 목록이 가릴 것이 없다. 알림을 누른 길도
     *   true 다. **영상 파일을 탭한 길만 false** — 그 사람이 고른 것은 목록이 아니라 그
     *   영상이고, 목록을 펼치면 그것을 가린다.
     *
     *   큐가 하나뿐이면 화면이 펼치지 않는다(`PlayerActivity` 의 `playlistVisible`).
     */
    fun open(context: Context, showPlaylist: Boolean)

    /**
     * [open] 과 같은 곳으로 가는 **인텐트**. 실행하지는 않는다.
     *
     * **알림은 눌러 달라고 `PendingIntent` 를 미리 받아 둔다.** 세션이 만들어지는 시점에는
     * 아직 아무도 누르지 않았으므로 '지금 연다' 는 쓸 수 없고, 인텐트 자체가 필요하다.
     * 그래서 이음매가 함수 둘이 됐다 — `fun interface` 였던 것이 그냥 `interface` 가 된
     * 이유가 이것이다. 인텐트를 조립하는 일과 `launchMode="singleTask"` 선언은 여전히
     * 한 몸이라 둘 다 `feature:player` 에 남는다.
     */
    fun intent(context: Context, showPlaylist: Boolean): Intent
}

object PlayerSupport {

    /** `app` 이 시작할 때 꽂는다. 꽂히지 않으면 미니 바를 눌러도 아무 일도 없다. */
    @Volatile
    var launcher: PlayerLauncher? = null

    fun open(context: Context, showPlaylist: Boolean = false) {
        launcher?.open(context, showPlaylist)
    }

    /** 꽂히지 않았으면 null. 알림에 누를 곳을 달지 않는 것이 아무 데나 보내는 것보다 낫다. */
    fun intent(context: Context, showPlaylist: Boolean = false): Intent? =
        launcher?.intent(context, showPlaylist)
}
