package io.github.donggi.iroiroviewer.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 재생 화면의 틀 — 시스템 바와 조작부를 언제 거두는가(바가 늘 보였고, 조작부가 저절로 숨지 않았다. 둘 다 기기에서
 * 확인됨). 거는 일은 액티비티가, 판정은 [PlayerChrome] 이 한다.
 */
class PlayerChromeTest {

    @Test
    fun `영상을 보는데 조작부를 거뒀으면 바도 거둔다`() {
        assertTrue(PlayerChrome.barsHidden(showingVideo = true, controlsShown = false, touchLocked = false))
    }

    /** 조작부가 나오면 바도 함께 나온다(VLC) — 시계·배터리를 보려고 두드리는 사람도 있다. */
    @Test
    fun `조작부가 나오면 바를 되돌린다`() {
        assertFalse(PlayerChrome.barsHidden(showingVideo = true, controlsShown = true, touchLocked = false))
    }

    /** 목록을 펼쳤거나 소리 항목이면 영상이 보이지 않는다 — 그때는 여느 화면처럼 바를 둔다. */
    @Test
    fun `영상이 보이지 않으면 바를 거두지 않는다`() {
        assertFalse(PlayerChrome.barsHidden(showingVideo = false, controlsShown = false, touchLocked = false))
        assertFalse(PlayerChrome.barsHidden(showingVideo = false, controlsShown = false, touchLocked = true))
    }

    /** 터치 잠금 중에는 조작부가 없다 — 액티비티가 든 '떠 있다' 값이 무엇이든 바도 거둔다. */
    @Test
    fun `터치 잠금 중에는 바를 거둔다`() {
        assertTrue(PlayerChrome.barsHidden(showingVideo = true, controlsShown = true, touchLocked = true))
    }

    private fun allowed(
        showingVideo: Boolean = true,
        playing: Boolean = true,
        inPip: Boolean = false,
        scrubbing: Boolean = false,
        panelOpen: Boolean = false,
        touching: Boolean = false,
        touchExploration: Boolean = false,
    ) = PlayerChrome.autoHideAllowed(showingVideo, playing, inPip, scrubbing, panelOpen, touching, touchExploration)

    @Test
    fun `영상을 트는 중이고 아무것도 하지 않으면 저절로 거둔다`() {
        assertTrue(allowed())
    }

    /**
     * 무엇이든 하는 중이면 거두지 않는다 — 손 밑에서 단추가 사라지거나, 고르던 메뉴·시트·눈금·구간 칸이 함께 사라지면 그것은
     * '저절로' 가 아니라 방해다. 멈춰 있으면 거두지 않는다(멈춘 화면에서 조작부를 찾는 사람이 대부분이다).
     */
    @Test
    fun `무엇을 하는 중이면 거두지 않는다`() {
        assertFalse(allowed(playing = false), "멈춤")
        assertFalse(allowed(scrubbing = true), "막대를 끄는 중")
        assertFalse(allowed(panelOpen = true), "메뉴·시트·눈금·구간 칸")
        assertFalse(allowed(touching = true), "손가락이 닿아 있다")
        assertFalse(allowed(inPip = true), "PiP")
    }

    /** 소리 화면에서 막대까지 감추면 아무것도 없는 빈 화면이다 — 영상이 보일 때만 거둔다. */
    @Test
    fun `영상이 보이지 않으면 거두지 않는다`() {
        assertFalse(allowed(showingVideo = false))
    }

    /** 화면 낭독기 사용자는 초점을 옮기는 사이에 단추가 사라지면 다시 찾을 길이 없다. */
    @Test
    fun `화면 낭독기가 켜져 있으면 거두지 않는다`() {
        assertFalse(allowed(touchExploration = true))
    }

    /** 큰 재생기들이 3~4초다 — 짧으면 읽기 전에 사라지고, 길면 볼 때마다 기다린다. */
    @Test
    fun `거두기까지 3초에서 4초`() {
        assertTrue(PlayerChrome.AUTO_HIDE_MS in 3_000L..4_000L, "${PlayerChrome.AUTO_HIDE_MS}ms")
        assertTrue(PlayerChrome.UNLOCK_HINT_MS in 1_500L..4_000L, "${PlayerChrome.UNLOCK_HINT_MS}ms")
    }
}
