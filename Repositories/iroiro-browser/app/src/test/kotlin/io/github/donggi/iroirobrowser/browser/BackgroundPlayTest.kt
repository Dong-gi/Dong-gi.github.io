package io.github.donggi.iroirobrowser.browser

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 뒤에서 재생 잇기([BackgroundPlay])의 판단과, 화면·서비스·매니페스트·통로가 그것을 잇는지(소스). WebView 를 보이는 채로 두는 것과 알림·잠금
 * 화면은 기기에서 본다(CLAUDE.md '뒤에서 재생 잇기').
 */
class BackgroundPlayTest {

    private val audible = PlayingMedia.State(audible = true, video = true)
    private val muted = PlayingMedia.State(audible = false, video = true)

    @Test
    fun 유튜브에서_소리가_나는_재생만_뒤에서_잇는다() {
        for (url in listOf(
            "https://m.youtube.com/watch?v=abc",
            "https://www.youtube.com/watch?v=abc",
            "https://youtube.com/watch?v=abc",
            "https://music.youtube.com/watch?v=abc",
            "https://M.YOUTUBE.COM/watch?v=abc",
            "https://m.youtube.com:443/watch?v=abc",
        )) {
            assertTrue(BackgroundPlay.wanted(url, audible), url)
            // 음소거로 저절로 도는 영상(자동 재생)은 듣고 있는 것이 아니다.
            assertFalse(BackgroundPlay.wanted(url, muted), url)
            assertFalse(BackgroundPlay.wanted(url, PlayingMedia.State.NONE), url)
            // 광고 가리기가 음소거한 광고가 나오는 중이면 영상이 재생 중인 것으로 족하다 — 멈춘 것은 아니다.
            assertTrue(BackgroundPlay.wanted(url, muted, adShowing = true), url)
            assertFalse(BackgroundPlay.wanted(url, PlayingMedia.State.NONE, adShowing = true), url)
            assertFalse(BackgroundPlay.wanted(url, PlayingMedia.State(audible = false, video = false), adShowing = true), url)
        }
    }

    @Test
    fun 유튜브가_아니면_잇지_않는다() {
        for (url in listOf(
            "http://m.youtube.com/watch?v=abc",
            "https://m.youtube.com:8443/watch?v=abc",
            "https://youtube.com.example.org/watch",
            "https://notyoutube.com/watch",
            "https://example.com/youtube.com",
            "https://vimeo.com/1",
            "",
            "about:blank",
            "https://m.youtube.com/watch?v=a b",
        )) {
            assertFalse(BackgroundPlay.wanted(url, audible), url)
            assertFalse(BackgroundPlay.wanted(url, muted, adShowing = true), url)
        }
    }

    @Test
    fun 알림의_제목은_유튜브_꼬리를_뗀다() {
        assertEquals("고양이 영상", BackgroundPlay.titleOf("고양이 영상 - YouTube", "유튜브"))
        assertEquals("고양이 영상", BackgroundPlay.titleOf("  고양이 영상 - YouTube  ", "유튜브"))
        // 꼬리가 없으면 그대로, 비면 대신할 이름.
        assertEquals("YouTube", BackgroundPlay.titleOf("YouTube", "유튜브"))
        assertEquals("유튜브", BackgroundPlay.titleOf("", "유튜브"))
        assertEquals("유튜브", BackgroundPlay.titleOf(" - YouTube", "유튜브"))
        // 꼬리를 먼저 떼고 줄인다 — 긴 제목에서 꼬리가 반쯤 남지 않게.
        assertEquals("가".repeat(BackgroundPlay.MAX_TEXT), BackgroundPlay.titleOf("가".repeat(BackgroundPlay.MAX_TEXT) + " - YouTube", "유튜브"))
    }

    @Test
    fun 쪽이_정한_글은_다듬어_싣는다() {
        // 제어 문자·줄바꿈·겹친 빈칸은 빈칸 하나로, 앞뒤는 벗긴다.
        assertEquals("a b c", BackgroundPlay.cleanText("  a\u0000\n\tb   c \r\n"))
        // 방향 제어 문자는 뺀다 — 남의 글이 알림의 뒤 글을 뒤집지 못하게.
        assertEquals("abcdef", BackgroundPlay.cleanText("abc\u202Edef"))
        assertEquals("채널", BackgroundPlay.cleanText("\u2066채널\u2069\u200F"))
        // 코드 포인트로 센다(서로게이트 쌍을 가르지 않는다).
        val emoji = "\uD83D\uDE00"
        assertEquals(emoji.repeat(BackgroundPlay.MAX_TEXT), BackgroundPlay.cleanText(emoji.repeat(BackgroundPlay.MAX_TEXT + 50)))
        assertEquals("", BackgroundPlay.cleanText("\u202E \n"))
    }

    @Test
    fun 알림은_화면의_영상_제목을_먼저_쓴다() {
        // 유튜브 모바일 웹은 쪽 제목이 'YouTube' 뿐이고 WebView 에는 미디어 세션이 없다 — 실행 세계가 유튜브에서만 제목 칸을 읽어 보낸다.
        assertTrue("if (h !== 'youtube.com' && h.slice(-12) !== '.youtube.com') return '';" in BridgeScripts.BRIDGE)
        assertTrue("document.querySelector('.ytVideoMetadataViewModelTitle')" in BridgeScripts.BRIDGE)
        assertTrue("title: s ? mediaTitle() : ''" in BridgeScripts.BRIDGE)
        // 앱은 재생 중인 틀의 것만 다듬어 들고, 바뀌면(다음 영상) 알림을 고치며, 쪽이 바뀌면 잊는다.
        val bridge = main("kotlin/io/github/donggi/iroirobrowser/browser/PageBridge.kt")
        assertTrue("""(audible || video) && onMediaTitle(o.optString("title"))""" in bridge)
        assertTrue("if (titleChanged) events.onMediaTitleChanged(tab)" in bridge)
        val forget = bridge.substringAfter("private fun forgetPlaying() {").substringBefore("\n    }\n")
        assertTrue("mediaTitle = \"\"" in forget)
        val activity = main("kotlin/io/github/donggi/iroirobrowser/browser/BrowserActivity.kt")
        assertTrue("tab.bridge?.mediaTitle?.takeIf { it.isNotEmpty() }" in activity)
        val onTitle = activity.substringAfter("override fun onMediaTitleChanged(tab: Tab) {").substringBefore("\n        }\n")
        assertTrue("if (tab == bgTab) syncBackgroundPlay()" in onTitle)
    }

    @Test
    fun 알림은_앱이_뒤에_있거나_그_탭을_보지_않을_때만이다() {
        assertTrue(BackgroundPlay.serviceWanted(alive = true, foreground = false, isCurrent = true))
        assertTrue(BackgroundPlay.serviceWanted(alive = true, foreground = true, isCurrent = false))
        assertTrue(BackgroundPlay.serviceWanted(alive = true, foreground = false, isCurrent = false))
        // 앞에서 그 탭을 보고 있다 — 알림이 필요 없다.
        assertFalse(BackgroundPlay.serviceWanted(alive = true, foreground = true, isCurrent = true))
        assertFalse(BackgroundPlay.serviceWanted(alive = false, foreground = false, isCurrent = false))
    }

    @Test
    fun 멈춘_뒤_정한_시간이_지나야_회를_닫는다() {
        val at = 1_000_000L
        assertFalse(BackgroundPlay.expired(null, at))
        assertFalse(BackgroundPlay.expired(at, at))
        assertFalse(BackgroundPlay.expired(at, at + BackgroundPlay.PAUSE_GRACE_MS - 1))
        assertTrue(BackgroundPlay.expired(at, at + BackgroundPlay.PAUSE_GRACE_MS))
        // 시계가 거꾸로 간 값으로는 닫지 않는다.
        assertFalse(BackgroundPlay.expired(at, at - 1))
    }

    // ── 이음새(소스·매니페스트) ──

    private fun main(path: String) = File("src/main/$path").readText().replace("\r\n", "\n")

    @Test
    fun 서비스는_mediaPlayback_이고_내보내지_않으며_작업과_함께_멈춘다() {
        val manifest = main("AndroidManifest.xml")
        assertTrue("android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" in manifest)
        val service = Regex("""<service\s+android:name="\.browser\.BackgroundPlayService"[^>]*/>""").find(manifest)?.value
        checkNotNull(service) { "매니페스트에 BackgroundPlayService 가 없다" }
        assertTrue("""android:exported="false"""" in service)
        assertTrue("""android:foregroundServiceType="mediaPlayback"""" in service)
        assertTrue("""android:stopWithTask="true"""" in service)
        val code = main("kotlin/io/github/donggi/iroirobrowser/browser/BackgroundPlayService.kt")
        assertTrue("FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK" in code)
    }

    @Test
    fun 통로가_재생과_멈춤을_받는다() {
        // 앱이 보내는 이름(PageBridge.mediaControl)과 쪽의 실행 세계가 받는 이름(BRIDGE)이 같다.
        assertTrue("case 'media-control': mediaControl(!!m.play); break;" in BridgeScripts.BRIDGE)
        val bridge = main("kotlin/io/github/donggi/iroirobrowser/browser/PageBridge.kt")
        assertTrue("""put("op", "media-control").put("play", play)""" in bridge)
    }

    @Test
    fun 화면은_뒤로_갈_때와_탭을_떠날_때_회를_연다() {
        val activity = main("kotlin/io/github/donggi/iroirobrowser/browser/BrowserActivity.kt")
        // 뒤로 갈 때 — 재생 중인 탭은 멈추지 않는다(onPause 를 부르지 않고 창이 보인다고 알린다).
        val onPause = activity.substringAfter("override fun onPause() {").substringBefore("\n    }\n")
        assertTrue("beginBackgroundPlay(tab)" in onPause)
        // 광고가 나오는 중인지(음소거된 광고)를 함께 본다.
        assertTrue("BackgroundPlay.wanted(tab.url, state, adShowing = tab.bridge?.ytAdShowing == true)" in activity)
        assertTrue("keepWindowVisible = true" in onPause)
        // 탭을 떠날 때 — 떼지 않고 보이지 않게 둔다(떼었다 붙이면 크로미움이 숨김을 알려 재생이 멈춘다).
        assertTrue("it.backstaged = true" in activity)
        // 회의 탭은 접지 않고 이웃 탭 보기에 빌려 가지 않는다.
        assertTrue("it != current && it != bgTab &&" in activity)
        assertTrue("!neighbor.crashed && neighbor != bgTab" in activity)
    }
}
