package io.github.donggi.iroiroviewer

import android.view.KeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 창에 거는 것의 규칙 — 어느 화면이 바의 아이콘을 밝게 두고 화면을 켜 두는가, 볼륨 키의 방향, 화면 켜 두기의 시간 셈.
 * 거는 일 자체(`ViewerWindowHost`)는 창이 있어야 하므로 기기에서 본다.
 */
class ViewerWindowRulesTest {

    @Test
    fun `어두운 뷰어는 그림·만화·문서뿐이다`() {
        assertTrue(ViewerScreens.darkBars(AppScreen.Viewer("/storage/emulated/0/a.jpg")))
        assertTrue(ViewerScreens.darkBars(AppScreen.Comic("/storage/emulated/0/a.cbz", -1)))
        assertTrue(ViewerScreens.darkBars(AppScreen.Doc("/storage/emulated/0/a.pdf")))
        // 테마의 바탕을 쓰는 화면은 테마(밤 모드)를 따른다.
        assertFalse(ViewerScreens.darkBars(AppScreen.Text("/storage/emulated/0/a.txt")))
        assertFalse(ViewerScreens.darkBars(AppScreen.Edit("/storage/emulated/0/a.txt")))
        assertFalse(ViewerScreens.darkBars(AppScreen.Editor("/storage/emulated/0/a.docx", "docx")))
        assertFalse(ViewerScreens.darkBars(AppScreen.Archive("/storage/emulated/0/a.zip")))
        assertFalse(ViewerScreens.darkBars(AppScreen.Browser))
        assertFalse(ViewerScreens.darkBars(AppScreen.Settings))
    }

    @Test
    fun `화면을 켜 두는 것은 읽는 화면이다 — 글은 글 뷰어를 건드리지 않고 화면의 종류로 정한다`() {
        assertTrue(ViewerScreens.reading(AppScreen.Comic("/storage/emulated/0/a.cbz", -1)))
        assertTrue(ViewerScreens.reading(AppScreen.Doc("/storage/emulated/0/a.epub")))
        assertTrue(ViewerScreens.reading(AppScreen.Text("/storage/emulated/0/a.txt")))
        assertFalse(ViewerScreens.reading(AppScreen.Viewer("/storage/emulated/0/a.jpg")))
        // 편집기는 읽는 화면이 아니다 — 치는 동안의 조작이 화면을 깨워 둔다.
        assertFalse(ViewerScreens.reading(AppScreen.Edit("/storage/emulated/0/a.txt")))
        // 편집기로 연 자리는 편집기(따로 된 화면)가 앞에 있다 — 이 앱의 창이 켜 둘 것이 없다.
        assertFalse(ViewerScreens.reading(AppScreen.Editor("/storage/emulated/0/a.xlsx", "xlsx")))
        assertFalse(ViewerScreens.reading(AppScreen.Browser))
    }

    @Test
    fun `아래 키는 다음 쪽, 위 키는 앞 쪽이고 다른 키는 삼키지 않는다`() {
        assertEquals(true, VolumeKeys.forwardOf(KeyEvent.KEYCODE_VOLUME_DOWN))
        assertEquals(false, VolumeKeys.forwardOf(KeyEvent.KEYCODE_VOLUME_UP))
        assertNull(VolumeKeys.forwardOf(KeyEvent.KEYCODE_VOLUME_MUTE))
        assertNull(VolumeKeys.forwardOf(KeyEvent.KEYCODE_BACK))
    }

    @Test
    fun `화면 켜 두기는 마지막 조작부터 센다`() {
        val tenMinutes = 10 * 60_000L
        assertEquals(tenMinutes, KeepAwake.remainingMs(nowMs = 5_000, lastMs = 5_000, limitMs = tenMinutes))
        assertEquals(tenMinutes - 60_000, KeepAwake.remainingMs(nowMs = 65_000, lastMs = 5_000, limitMs = tenMinutes))
        assertEquals(0, KeepAwake.remainingMs(nowMs = tenMinutes + 5_001, lastMs = 5_000, limitMs = tenMinutes))
        // 끈 설정(0분)은 언제나 0.
        assertEquals(0, KeepAwake.remainingMs(nowMs = 5_000, lastMs = 5_000, limitMs = 0))
        // 마지막 조작이 지금보다 뒤로 적혀도 상한을 넘지 않는다.
        assertEquals(tenMinutes, KeepAwake.remainingMs(nowMs = 1_000, lastMs = 9_000, limitMs = tenMinutes))
    }
}
