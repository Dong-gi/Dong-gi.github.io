package io.github.donggi.iroiroviewer.player

/**
 * 재생 화면의 '틀'(시스템 바·조작부)을 언제 거두는가 — **순수 판정**이라 JVM 시험이 박는다(`PlayerChromeTest`).
 *
 * ## 시스템 바
 *
 * 영상이 보이는데 조작부를 거뒀으면 상태 표시줄·내비게이션 바도 함께 거둔다 — VLC 는 조작부와 함께, YouTube·MX 는 가로 전체
 * 화면에서 거두고 가장자리를 밀 때만 잠깐 보여 준다(`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`). 그 전에는 바를 거두는 코드가 한 줄도
 * 없어, 가로로 꽉 찬 영상 위에 시계·배터리가 겹치고 3버튼 내비게이션이 밝은 띠로 남았다(기기에서 확인됨). 조작부가 나오면·목록을
 * 펼치면·소리 항목이면 되돌린다. **거는 것은 액티비티다**(`PlayerActivity` 의 불변식 2 — 방향 잠금과 같은 방식).
 *
 * ## 조작부 저절로 거두기
 *
 * 재생 중이면 마지막으로 만진 뒤 [AUTO_HIDE_MS] 가 지나 조작부를 거둔다(VLC·MX·YouTube 가 3~4초). 그 전에는 처음 값이 '보임' 이고
 * 바꾸는 곳이 두드림 하나뿐이라, 볼 때마다 한 번 두드려야 했고 가로에서는 판이 자막을 덮었다(기기에서 확인됨 — 12초 뒤에도 그대로).
 * 거두지 않는 때를 여기 한곳에 모은다 — 사람이 무엇을 하는 중이면(누르고 있다·막대를 끈다·메뉴·시트·눈금·구간 칸이 열려 있다)
 * 거두지 않는다. 손 밑에서 단추가 사라지거나, 고르던 칸이 함께 사라지면 그것은 저절로가 아니라 방해다. **화면 낭독기가 켜져 있으면
 * 거두지 않는다** — 초점을 옮기는 사이에 단추가 사라지면 낭독기 사용자는 그것을 다시 찾을 길이 없다.
 */
internal object PlayerChrome {

    /**
     * 시스템 바를 거둘 것인가. 영상이 보이고(목록이 덮지 않았다) 조작부를 거뒀을 때 — 터치 잠금 중에는 조작부가 없으므로 언제나다.
     *
     * @param showingVideo 영상이 실제로 보인다 — `hasVideo && !목록` . 테마를 어둡게 못 박는 조건과 **같은 식**이다.
     */
    fun barsHidden(showingVideo: Boolean, controlsShown: Boolean, touchLocked: Boolean): Boolean =
        showingVideo && (!controlsShown || touchLocked)

    /**
     * 지금 조작부를 저절로 거둬도 되는가. 하나라도 거짓이면 시계를 세우지 않는다 — 조건이 다시 참이 되면 처음부터 잰다
     * (그래서 '만지면 시계를 다시 잰다' 가 된다: 손을 대는 동안 [touching] 이 참이다).
     *
     * @param scrubbing 재생 막대를 끄는 중.
     * @param panelOpen 조작부에 딸린 것이 열려 있다 — ⋮ 메뉴·소리와 자막 시트·배속 눈금·구간 칸, 그리고 저장이 도는 중(진행 줄).
     * @param touching 손가락이 화면에 닿아 있다.
     * @param touchExploration 화면 낭독기(TalkBack)의 '터치하여 탐색' 이 켜져 있다.
     */
    fun autoHideAllowed(
        showingVideo: Boolean,
        playing: Boolean,
        inPip: Boolean,
        scrubbing: Boolean,
        panelOpen: Boolean,
        touching: Boolean,
        touchExploration: Boolean,
    ): Boolean = showingVideo && playing && !inPip && !scrubbing && !panelOpen && !touching && !touchExploration

    /**
     * 조작부를 거두기까지. 큰 재생기들이 3~4초다 — 짧으면 읽기 전에 사라지고, 길면 볼 때마다 기다린다.
     */
    const val AUTO_HIDE_MS = 3_500L

    /** 터치 잠금 중에 두드리면 잠금 풀기 단추가 떠 있는 시간. */
    const val UNLOCK_HINT_MS = 2_500L
}
