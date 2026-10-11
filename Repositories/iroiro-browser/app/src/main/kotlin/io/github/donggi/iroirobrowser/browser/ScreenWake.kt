package io.github.donggi.iroirobrowser.browser

/*
 * 재생 중 화면 켜 두기(사용자 요청, 2026-10-08: '브라우저 > 미디어 재생 중엔 화면 꺼짐을 방지하도록 수정해주세요'). 이 파일은 그 판단(틀마다
 * 재생 중인 미디어를 모아 화면을 켜 둘지 — 화면 밖의 계산이라 시험한다)이다. 틀의 알림은 BridgeScripts.BRIDGE 의 '재생 중' 절이 보내고
 * PageBridge 가 받으며, 창의 FLAG_KEEP_SCREEN_ON 은 BrowserActivity 의 updateScreenOn 이 건다.
 *
 * WebView 는(153, 에뮬레이터에서 쟀다) **소리가 나는 영상**이 재생되는 동안만 스스로 화면을 켜 둔다 — 페이지 안이든 전체 화면이든. 소리만
 * 재생하는 것, 음소거하거나 음량이 0 인 영상(전체 화면이어도)은 켜 두지 않는다. 이 앱은 거기에 더한다 — 소리가 나는 미디어(영상과 소리 모두)와
 * 전체 화면으로 보는 영상(음소거여도).
 */

/**
 * 탭 하나에서 재생 중인 미디어 — 틀마다의 마지막 알림. 메인 스레드에서만 쓴다.
 *
 * 틀은 바뀔 때마다 알리고, 재생 중이면 10초마다 다시 알린다(BRIDGE). [STALE_MS] 넘게 소식이 없는 틀은 잊는다 — 틀이 알리지 못하고 사라진
 * 때(다른 프로세스의 틀이 죽었다 등) 화면이 영영 켜져 있지 않게.
 */
class PlayingMedia {

    /** 합친 상태 — [audible] 소리가 나는(음소거·음량 0 이 아닌) 미디어가 재생 중이다, [video] 영상 요소가 재생 중이다(음소거여도). */
    data class State(val audible: Boolean, val video: Boolean) {
        companion object {
            val NONE = State(audible = false, video = false)
        }
    }

    private class Entry(val state: State, val at: Long)

    private val frames = HashMap<String, Entry>()

    /** 틀 [frame] 의 알림(시각 [now] — 밀리초, 단조) — 합친 상태가 바뀌었으면 true. 재생 중이 아니면 그 틀을 잊는다. */
    fun report(frame: String, audible: Boolean, video: Boolean, now: Long): Boolean {
        val before = state(now)
        frames.values.removeAll { now - it.at > STALE_MS }
        if (audible || video) frames[frame] = Entry(State(audible, video), now) else frames.remove(frame)
        return state(now) != before
    }

    /** 모두 잊는다 — 주 틀에 새 문서, 렌더러가 죽었다, WebView 를 새로 만들었다, 탭을 닫는다. 합친 상태가 바뀌었으면 true. */
    fun clear(now: Long): Boolean {
        val before = state(now)
        frames.clear()
        return before != State.NONE
    }

    /** 시각 [now] 의 합친 상태 — 오래된 틀은 뺀다. */
    fun state(now: Long): State {
        var audible = false
        var video = false
        for (e in frames.values) {
            if (now - e.at > STALE_MS) continue
            audible = audible || e.state.audible
            video = video || e.state.video
        }
        return State(audible, video)
    }

    /** 살아 있는 틀 가운데 하나가 처음으로 오래되는 때(없으면 null) — 그때 화면이 다시 본다. */
    fun nextStale(now: Long): Long? = frames.values.filter { now - it.at <= STALE_MS }.minOfOrNull { it.at + STALE_MS + 1 }

    companion object {
        /** 틀의 다시 알림(10초)을 두 번 놓치면 잊는다. */
        const val STALE_MS = 30_000L
    }
}

/** 화면을 켜 둘지의 판단. */
object ScreenWake {

    /**
     * 켜 둔다 — 지금 탭에서 소리가 나는 미디어가 재생 중이거나, 전체 화면([fullscreenVideo] — WebView 의 전체 화면 뷰가 떠 있다)으로 보는 동안
     * 영상이 재생 중일 때(음소거여도 — 전체 화면은 보고 있다는 뜻이다). **페이지 안의 음소거 영상은 켜 두지 않는다** — 저절로 도는 음소거 배경
     * 영상(자동 재생은 음소거여야 한다)이 화면을 붙잡지 않게. WebView 의 기본 동작도 그렇다.
     */
    fun wanted(state: PlayingMedia.State, fullscreenVideo: Boolean): Boolean = state.audible || (fullscreenVideo && state.video)
}
