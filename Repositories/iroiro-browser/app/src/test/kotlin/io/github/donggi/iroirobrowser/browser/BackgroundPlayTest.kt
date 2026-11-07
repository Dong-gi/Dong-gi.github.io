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

    @Test
    fun 그려지지_않는_동안에는_화면_밖에_그린다() {
        // 크로미움에 '보임' 인 채 아무도 그리지 않으면 영상 프레임이 소비되지 않아 재생이 선다(사용자 기기 — 2초 끊긴 뒤 144p, 에뮬레이터 —
        // DECODER_UNDERFLOW). 회의 탭이 재생 중이고, 앱이 뒤에 있거나(onPause 부터) 다른 탭을 보는 동안만.
        val activity = main("kotlin/io/github/donggi/iroirobrowser/browser/BrowserActivity.kt")
        val sync = activity.substringAfter("private fun syncOffscreen() {").substringBefore("\n    }\n")
        assertTrue("tab == bg && (!foreground || tab != current) && bgPlaying(tab)" in sync)
        val syncPlay = activity.substringAfter("private fun syncBackgroundPlay() {").substringBefore("\n    }\n")
        assertTrue("syncOffscreen()" in syncPlay)
        // 떼거나 없애면 멈추고, 투명하게 남았던 탭이 다시 보이면 새로 그리게 한다(투명도만 바꾸면 쪽에 새 그림을 청하지 않는다).
        val page = main("kotlin/io/github/donggi/iroirobrowser/browser/PullToRefresh.kt")
        assertTrue("override fun onDetachedFromWindow() {\n        offscreen?.stop()" in page)
        assertTrue("override fun destroy() {\n        offscreen?.stop()" in page)
        assertTrue("if (!value) invalidate()" in page)
        // 하드웨어 캔버스에 그리고(그래야 WebView 의 그리기가 렌더 스레드에서 돈다) 그린 것은 곧바로 버린다.
        val draw = main("kotlin/io/github/donggi/iroirobrowser/browser/OffscreenDraw.kt")
        assertTrue("surface.lockHardwareCanvas()" in draw)
        assertTrue("view.draw(canvas)" in draw)
        assertTrue("r.acquireLatestImage()?.close()" in draw)
    }

    @Test
    fun 화면_밖에는_뷰가_다시_그리기를_청할_때만_그린다() {
        // vsync 마다(초당 60번) 그리면 30 fps 영상에서 앞에서 볼 때의 두 배를 그린다.
        // 크로미움(AwContents)이 다시 그리기를 청하는 두 길을 모두 받아 화면 밖 그리기에 알린다.
        val page = main("kotlin/io/github/donggi/iroirobrowser/browser/PullToRefresh.kt")
        val invalidate = page.substringAfter("override fun invalidate() {").substringBefore("\n    }\n")
        assertTrue("super.invalidate()" in invalidate)
        assertTrue("offscreen?.invalidated()" in invalidate)
        val onAnimation = page.substringAfter("override fun postInvalidateOnAnimation() {").substringBefore("\n    }\n")
        assertTrue("super.postInvalidateOnAnimation()" in onAnimation)
        assertTrue("offscreen?.invalidated()" in onAnimation)
        // 상위 생성자 안에서도 invalidate 가 불린다 — 그때는 화면 밖 그리기가 아직 없다(널이어도 넘어간다).
        assertTrue("private var offscreen: OffscreenDraw? = null" in page)
        // 청이 있을 때만 그리고, 그리기 전에 청을 내린다(그리는 동안 들어온 청은 다음 프레임에 그린다).
        val draw = main("kotlin/io/github/donggi/iroirobrowser/browser/OffscreenDraw.kt")
        val frame = draw.substringAfter("override fun doFrame(frameTimeNanos: Long) {").substringBefore("\n    }\n")
        assertTrue("if (due(wasRequested, now - requestedAt, requestsOnly)) {" in frame)
        assertTrue(frame.indexOf("requested = false") in 0 until frame.indexOf("drawOnce()"))
        // 처음 한 번은 청이 없어도 그린다.
        val start = draw.substringAfter("fun start() {").substringBefore("\n    }\n")
        assertTrue("invalidated()" in start)
    }

    @Test
    fun 청이_끊기면_예전처럼_프레임마다_그린다() {
        assertTrue(OffscreenDraw.due(requested = true, sinceRequestMs = 0))
        assertTrue(OffscreenDraw.due(requested = true, sinceRequestMs = 10_000))
        assertFalse(OffscreenDraw.due(requested = false, sinceRequestMs = 0))
        assertFalse(OffscreenDraw.due(requested = false, sinceRequestMs = OffscreenDraw.FALLBACK_MS - 1))
        assertTrue(OffscreenDraw.due(requested = false, sinceRequestMs = OffscreenDraw.FALLBACK_MS))
        // 재생 중 영상의 청 사이(24 fps 면 42 ms)보다 넉넉히 길고, 끊김을 사람이 느끼기 전에 물러선다.
        assertTrue(OffscreenDraw.FALLBACK_MS in 200L..1_000L)
    }

    @Test
    fun 영상을_숨긴_동안에는_물러서지_않는다() {
        // 숨은 영상은 다시 그리기를 청하지 않는다 — 그때 물러서면 청이 없다는 까닭으로 vsync 마다 그린다.
        assertFalse(OffscreenDraw.due(requested = false, sinceRequestMs = 10_000, requestsOnly = true))
        assertTrue(OffscreenDraw.due(requested = true, sinceRequestMs = 0, requestsOnly = true))
        // 화면 밖 그리기를 켤 때마다 내린다 — 숨긴 것이 확인되어야 다시 올린다.
        val draw = main("kotlin/io/github/donggi/iroirobrowser/browser/OffscreenDraw.kt")
        val start = draw.substringAfter("fun start() {").substringBefore("\n    }\n")
        assertTrue("requestsOnly = false" in start)
        assertTrue("if (due(wasRequested, now - requestedAt, requestsOnly)) {" in draw)
    }

    @Test
    fun 화면_밖에_그리는_동안_영상을_숨기고_그치면_되돌린다() {
        val activity = main("kotlin/io/github/donggi/iroirobrowser/browser/BrowserActivity.kt")
        val sync = activity.substringAfter("private fun syncOffscreen() {").substringBefore("\n    }\n")
        assertTrue("hideVideos(tab, view, want)" in sync)
        // 숨긴 영상이 있다고 확인되고 아직 화면 밖에 그리는 중일 때만 청만 받는다(늦게 온 답이 되돌린 뒤를 덮지 않게).
        val hide = activity.substringAfter("private fun hideVideos(tab: Tab, view: PageWebView, hide: Boolean) {").substringBefore("\n    }\n")
        assertTrue("if (hide && hidden && view.drawOffscreen) view.offscreenRequestsOnly = true" in hide)
        // 틀마다의 실행 세계에서(쪽의 세계가 아니다), 이 프로세스의 무작위 이름으로.
        val bridge = main("kotlin/io/github/donggi/iroirobrowser/browser/PageBridge.kt")
        val fn = bridge.substringAfter("fun hideVideos(hide: Boolean, done: (Boolean) -> Unit = {}) {").substringBefore("\n    }\n")
        assertTrue("BackgroundPlay.hideVideoScript(HIDE_ID, hide)" in fn)
        assertTrue("frame.proxy.executeJavaScript(script," in fn)
        assertTrue("private val HIDE_ID = randomName(\"s\")" in bridge)
    }

    @Test
    fun 영상_숨기기_스크립트() {
        val on = BackgroundPlay.hideVideoScript("s0a1", hide = true)
        val off = BackgroundPlay.hideVideoScript("s0a1", hide = false)
        for (s in listOf(on, off)) {
            assertFalse(BackgroundPlay.HIDE_ID_PLACEHOLDER in s)
            assertFalse(BackgroundPlay.HIDE_FLAG_PLACEHOLDER in s)
            assertTrue("getElementById('s0a1')" in s)
        }
        assertTrue("if (true) {" in on)
        assertTrue("if (false) {" in off)
        // 영상만 숨긴다 — 건너뛰기 단추(B2) 같은 다른 요소는 그대로.
        assertTrue("s.textContent = 'video{visibility:hidden!important}';" in BackgroundPlay.HIDE_VIDEO_SCRIPT)
        assertTrue("if (s) s.remove();" in BackgroundPlay.HIDE_VIDEO_SCRIPT)
        // 코틀린의 날 글자열·셸이 먹는 글자를 쓰지 않는다.
        assertFalse('\\' in BackgroundPlay.HIDE_VIDEO_SCRIPT)
        assertFalse('$' in BackgroundPlay.HIDE_VIDEO_SCRIPT)
        // 이름은 영문·숫자만 받는다(스크립트의 글자열에 그대로 들어간다).
        for (bad in listOf("", "a'b", "a b", "a-b")) {
            assertTrue(runCatching { BackgroundPlay.hideVideoScript(bad, hide = true) }.isFailure, bad)
        }
    }

    @Test
    fun 같은_탭을_다시_골라도_밤_모드가_바뀌어도_재생_중인_쪽을_떼지_않는다() {
        // 쪽을 떼었다 붙이면 쪽이 잠깐 숨고, 유튜브는 그 틈을 보고 재생을 멈춘다(화면을 켤 때 밤 모드가 바뀌는 때 등).
        val activity = main("kotlin/io/github/donggi/iroirobrowser/browser/BrowserActivity.kt")
        val select = activity.substringAfter("private fun select(tab: Tab) {").substringBefore("\n    }\n")
        assertTrue("leaving == tab -> Unit" in select)
        assertTrue("it.parent === content -> it.onResume()" in select)
        val config = activity.substringAfter("override fun onConfigurationChanged(newConfig: Configuration) {").substringBefore("\n    }\n")
        assertTrue("if (holdsRebuild()) {" in config)
        assertTrue("rebuildPending = true" in config)
        val holds = activity.substringAfter("private fun holdsRebuild(): Boolean =").substringBefore("\n\n")
        assertTrue("bgTab != null" in holds)
        assertTrue("?.audible == true" in holds)
        // 재생이 바뀌거나 회가 닫히면 미뤄 둔 것을 본다.
        val onPlaying = activity.substringAfter("override fun onPlayingChanged(tab: Tab) {").substringBefore("\n        }\n")
        assertTrue("if (rebuildPending) main.post { maybeRebuild() }" in onPlaying)
        val syncPlay = activity.substringAfter("private fun syncBackgroundPlay() {").substringBefore("\n    }\n")
        assertTrue("if (rebuildPending) main.post { maybeRebuild() }" in syncPlay)
    }

    @Test
    fun 유튜브의_주_틀은_늘_보임을_읽는다() {
        // 사용자 결정 — 브레이브의 방식. 주 틀에서만, 문서의 보임 둘을 '보임' 으로 덮고 사건은 창의 포착 단계에서 멈춘다.
        val s = BackgroundPlay.VISIBILITY_SCRIPT
        assertTrue("if (window.top !== window) return;" in s)
        assertTrue("keep('visibilityState', 'visible');" in s)
        assertTrue("keep('hidden', false);" in s)
        assertTrue("window.addEventListener('visibilitychange', stop, true);" in s)
        assertTrue("function stop(e) { e.stopImmediatePropagation(); }" in s)
        // 쪽의 함수를 바꿔 끼우지 않는다(브레이브의 탭 도우미는 document.addEventListener 를 바꿔 끼운다 — 그러지 않는다).
        assertFalse("addEventListener =" in s)
        assertFalse("prototype" in s)
        // 코틀린의 날 글자열·셸이 먹지 않게.
        assertFalse(Char(92) in s)
        assertFalse(Char(36) in s)
        // 쪽의 세계에, 문서 시작으로, 유튜브 출처 규칙으로 건다(앱의 실행 세계가 아니다 — 그 세계의 문서 객체는 따로다).
        val webViews = main("kotlin/io/github/donggi/iroirobrowser/browser/WebViews.kt")
        assertTrue("WebViewCompat.addDocumentStartJavaScript(view, BackgroundPlay.VISIBILITY_SCRIPT, YtGuard.ORIGIN_RULES)" in webViews)
    }
}
