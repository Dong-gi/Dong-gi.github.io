package io.github.donggi.iroiroviewer.browser

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle

/**
 * 목록이 **재생 실패**를 말하고 지우는 규칙.
 *
 * 재생 실패(`PlaybackConnection.State.failure`)는 읽는 곳이 둘이다 — 재생 화면(`feature:player` 의 `PlayerActivity`)은
 * 그 자리에서 문구와 '다른 앱으로 열기' 를 보이고 **지우지 않는다.** 목록은 스낵바로 한 번 말하고 **지운다**(말한 것을
 * 다시 말하지 않으려고). 둘이 부딪히는 것이 문제였다.
 *
 * ## 왜 STARTED 가 아니라 RESUMED 인가
 *
 * 재생 화면은 **다른 액티비티**다. 목록에서 `song.wma` 를 누르면 재생 화면이 목록 위에 뜨는데, 그 창이 목록을 다 덮기
 * 전까지 목록의 액티비티는 PAUSED 이면서 **STARTED** 다. `collectAsStateWithLifecycle` 의 기본값(STARTED)으로 들으면
 * 그 사이에 온 실패가 가려지는 목록의 스낵바에 실리고, 스낵바의 시계는 프레임이 아니라 `delay` 로 도므로 목록이 멈춘
 * 뒤에도 흘러 4초쯤 뒤에 `consumeFailure` 가 불린다. 재생 화면의 문구와 단추가 3~4초 뒤 사라지고, 돌아와도 목록은 이미
 * 말한 셈이라 아무것도 없었다(에뮬레이터에서 확인된 결함).
 *
 * 그래서 목록은 **맨 앞(RESUMED)** 일 때만 듣고 말한다([IN_FRONT]). 맨 앞에서 내려가면(재생 화면·고르는 창·권한 창이
 * 위에 떴다) 보이던 스낵바를 거두고 **지우지 않는다** — 재생 화면이 같은 실패를 말하고 있다. 다시 맨 앞에 서면 실패가
 * 아직 있을 때만([tellOnce] 의 `pending`) **한 번** 말하고 지운다. 목록이 말해 준다는 약속(재생 화면의 주석 '나가면 목록이
 * 한 번 더 말해 주고')은 그대로다.
 *
 * 내려가는 것을 재구성이 아니라 **수명 주기의 알림**으로 안다(`repeatOnLifecycle`) — 화면이 멈추면 재구성이 멎는다(함정
 * 표). 그리고 취소된 코루틴은 스낵바가 닫혔다는 소식을 받아도 이어 달리지 않으므로(재개가 취소를 이기지 못한다) 거둔
 * 스낵바 뒤에서 지우는 일이 없다.
 *
 * **PiP 는 다르다.** 재생 화면이 화면 속 화면이면 목록이 맨 앞이다. 그때는 목록이 말하고 지운다 — PiP 창은 실패 문구를
 * 그리지 않으므로(재생 화면이 PiP 에서 그 판을 빼 둔다) 보이는 것 가운데 잃는 것이 없고, 사용자는 목록에서 읽었다.
 */
internal object PlaybackNotice {

    /** 목록이 재생 실패를 듣고 말하는 가장 낮은 상태. */
    val IN_FRONT = Lifecycle.State.RESUMED

    /**
     * 실패 하나를 [lifecycle] 이 맨 앞인 동안에만 [show] 하고, 끝까지 보였을 때만 [consume] 한다. 한 번 말했으면 다시
     * 서도 말하지 않는다. 부르는 쪽(화면의 `LaunchedEffect`)이 실패가 바뀌거나 사라지면 이 함수를 취소한다.
     *
     * @param pending 그 실패가 아직 남아 있는가. 맨 앞에 설 때마다 **말하기 전에** 묻는다 — 떠나 있는 동안 재생 화면에서
     *   다른 곡을 틀었으면 실패는 이미 지워졌는데, 화면이 그것을 다시 그리기(한 프레임) 전에 옛 문구를 띄우지 않으려고.
     *   **지우기 전에도** 묻는다. [consume] 은 커넥션의 실패를 무엇이든 지우므로(`consumeFailure`), 스낵바가 끝나는 순간과
     *   새 실패가 온 순간이 한 프레임 안에 겹치면(재구성이 이 일을 거두기 전) 말하지 않은 새 실패를 지우게 된다 — 그러면
     *   목록도, 뒤에 여는 재생 화면(문구와 '다른 앱으로 열기')도 그것을 말하지 못한다.
     */
    suspend fun tellOnce(
        lifecycle: Lifecycle,
        pending: () -> Boolean,
        show: suspend () -> Unit,
        consume: () -> Unit,
    ) {
        var told = false
        lifecycle.repeatOnLifecycle(IN_FRONT) {
            if (told || !pending()) return@repeatOnLifecycle
            show()
            told = true
            if (pending()) consume()
        }
    }
}
