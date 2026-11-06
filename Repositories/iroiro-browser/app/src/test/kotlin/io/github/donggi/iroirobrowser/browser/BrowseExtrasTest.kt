package io.github.donggi.iroirobrowser.browser

import io.github.donggi.iroirobrowser.core.LiveTabs
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 2026-10-16 의 작은 넷 — 정체 감추기(Media Integrity·파비콘), 링크를 새 탭에서 열면 곧바로 그 탭으로, 살아 있는 탭 수 설정, 고른 글 검색,
 * 복사한 링크 열기. 판단은 순수 함수로, 화면·WebView 의 이음새는 소스로 본다(메뉴·클립보드는 기기에서).
 */
class BrowseExtrasTest {

    @Test
    fun 고른_글은_다듬어_검색어로() {
        assertEquals("고양이 사진", SelectionSearch.query("  고양이\n\t사진  "))
        // 방향 제어 문자는 뺀다 — 남의 글이 검색어의 차례를 뒤집지 못하게.
        assertEquals("abc", SelectionSearch.query("a" + Char(0x202E) + "bc"))
        assertEquals(SelectionSearch.MAX_QUERY, SelectionSearch.query("가".repeat(SelectionSearch.MAX_QUERY * 2))!!.length)
        assertNull(SelectionSearch.query(""))
        assertNull(SelectionSearch.query(" \n\t "))
        assertNull(SelectionSearch.query(Char(0x202E).toString()))
    }

    @Test
    fun 고른_글을_읽는_스크립트() {
        val script = SelectionSearch.SCRIPT
        // 코틀린의 날 글자열·셸이 먹는 글자를 쓰지 않는다.
        assertFalse(script.contains(Char(92)), "역슬래시")
        assertFalse(script.contains(Char(36)), "달러")
        // 비밀번호 칸은 읽지 않는다 — 입력칸의 고른 글도, 문서의 고르기도.
        assertEquals(2, Regex("type !== 'password'|type === 'password'").findAll(script).count())
        assertTrue("slice(0, 2000)" in script)
        assertTrue("document.hasFocus()" in script)
    }

    @Test
    fun 살아_있는_탭_수는_범위_안으로() {
        assertTrue(LiveTabs.DEFAULT in LiveTabs.CHOICES)
        assertEquals(LiveTabs.CHOICES.sorted(), LiveTabs.CHOICES)
        assertEquals(LiveTabs.CHOICES.first(), LiveTabs.clamp(0))
        assertEquals(LiveTabs.CHOICES.first(), LiveTabs.clamp(-5))
        assertEquals(LiveTabs.CHOICES.last(), LiveTabs.clamp(1_000))
        assertEquals(5, LiveTabs.clamp(5))
        val store = main("kotlin/io/github/donggi/iroirobrowser/proxy/SettingsStore.kt")
        assertTrue("LiveTabs.clamp(prefs.getInt(K_LIVE_TABS, LiveTabs.DEFAULT))" in store)
        assertTrue("putInt(K_LIVE_TABS, LiveTabs.clamp(value))" in store)
    }

    @Test
    fun 화면이_설정의_탭_수로_접는다() {
        val activity = main("kotlin/io/github/donggi/iroirobrowser/browser/BrowserActivity.kt")
        assertFalse("MAX_LIVE_TABS" in activity)
        val trim = body(activity, "private fun trimLiveTabs()")
        assertTrue("app.settings.liveTabs" in trim, trim)
        // 설정에서 줄이고 돌아오면 넘는 탭을 접는다.
        assertTrue(Regex("applyForceZoom[(][)]\\s*\\n\\s*//[^\\n]*\\n\\s*trimLiveTabs[(][)]").containsMatchIn(activity))
        val settings = main("kotlin/io/github/donggi/iroirobrowser/settings/SettingsActivity.kt")
        assertTrue("set = { app.settings.liveTabs = it }" in settings)
    }

    @Test
    fun 새_탭에서_열면_곧바로_그_탭으로() {
        val activity = main("kotlin/io/github/donggi/iroirobrowser/browser/BrowserActivity.kt")
        assertFalse("openInBackground" in activity)
        val open = body(activity, "private fun openInNewTab(opener: Tab, url: String)")
        assertTrue("newTab(select = true, opener = opener)" in open, open)
        assertFalse("Toast" in open, open)
        // 고리와 그림의 '새 탭에서 열기' 가 모두 이 길로.
        assertEquals(2, Regex("openInNewTab[(]tab, (url|src)[)]").findAll(activity).count())
        assertFalse("opened_in_background" in main("res/values/strings.xml"))
    }

    @Test
    fun 글_고르기_메뉴에_검색() {
        val view = main("kotlin/io/github/donggi/iroirobrowser/browser/PullToRefresh.kt")
        val start = body(view, "override fun startActionMode(callback: ActionMode.Callback, type: Int): ActionMode?")
        // 떠 있는 메뉴(글 고르기)만 감싸고, 줄을 더할 곳이 없으면(통로 없음) 그대로.
        assertTrue("type != ActionMode.TYPE_FLOATING" in start, start)
        assertTrue("SearchItemCallback(callback" in start, start)
        val callback = body(view, "internal class SearchItemCallback(")
        // 메뉴를 다시 지어도 다시 더하고, 메뉴의 자리 셈은 크로미움의 것을 그대로 쓴다.
        assertTrue("override fun onPrepareActionMode" in callback && "addItem(menu)" in callback)
        // '복사' 바로 뒤에, 넘침(⋮)으로 밀리지 않게 — 그리고 '복사' 가 없는 메뉴(비밀번호 칸 등)에서는 걷는다.
        assertTrue("setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS or MenuItem.SHOW_AS_ACTION_WITH_TEXT)" in callback, callback)
        assertTrue("menu.add(Menu.NONE, R.id.menu_search_selection, copy.order, label)" in callback, callback)
        assertTrue("menu.removeItem(R.id.menu_search_selection)" in callback, callback)
        assertTrue("inner.onGetContentRect(mode, view, outRect)" in callback)
        assertTrue("inner.onActionItemClicked(mode, item)" in callback)
        val activity = main("kotlin/io/github/donggi/iroirobrowser/browser/BrowserActivity.kt")
        assertTrue("if (bridge != null) (view as? PageWebView)?.onSearchSelection = { mode -> searchSelection(tab, mode) }" in activity)
        // 고른 채 뒤로 가기는 고르기만 푼다 — 쪽을 뒤로 넘기거나 앱을 뒤로 보내기 전에.
        val back = body(activity, "private fun handleBack()")
        assertTrue(back.indexOf("finishSelection()") in 0 until back.indexOf("canGoBack()"), back)
        assertTrue("inner.onDestroyActionMode(mode)" in callback && "onDestroyed(mode)" in callback)
        val search = body(activity, "private fun searchSelection(tab: Tab, mode: ActionMode)")
        // 고른 글을 다 읽은 뒤에 메뉴를 닫는다 — 먼저 닫으면 고르기가 풀려 글이 빈다.
        assertTrue(search.indexOf("bridge.selectedText") in 0 until search.indexOf("mode.finish()"), search)
        assertTrue("openInNewTab(tab, app.settings.searchEngine.searchUrl(query))" in search, search)
        val bridge = main("kotlin/io/github/donggi/iroirobrowser/browser/PageBridge.kt")
        assertTrue("frame.proxy.executeJavaScript(SelectionSearch.SCRIPT" in bridge)
        assertTrue(Regex("<item name=\"menu_search_selection\" type=\"id\" */>").containsMatchIn(main("res/values/strings_browse.xml")))
    }

    @Test
    fun 클립보드는_누를_때만_읽는다() {
        val source = main("kotlin/io/github/donggi/iroirobrowser/browser/AddressSuggestions.kt")
        // 내용(primaryClip)은 '복사한 링크 열기' 를 누를 때만 — 그 전에는 설명만(안드로이드 12 는 읽을 때마다 알린다).
        val reads = Regex("[.]primaryClip(?!Description)").findAll(source).map { it.range.first }.toList()
        val open = source.indexOf("private fun openClipLink()")
        assertEquals(1, reads.size, "primaryClip 을 읽는 곳")
        assertTrue(reads.single() > open && reads.single() < source.indexOf("\n    }\n", open))
        val has = body(source, "private fun clipHasLink()")
        assertTrue("primaryClipDescription" in has)
        // 오래된 클립은 두지 않고, 분류를 모르면(막 복사했거나 분류 서비스가 없다) 두고 누를 때 본다 — 에뮬레이터는 분류가 끝나지 않은 채였다.
        assertTrue("CLIP_MAX_AGE_MS" in has, has)
        assertTrue("classificationStatus != ClipDescription.CLASSIFICATION_COMPLETE) return true" in has, has)
        assertTrue("description.timestamp == usedClipStamp" in has, has)
        // content: 주소를 글로 바꾸려고 남의 제공자를 열지 않는다.
        assertFalse(".coerceToText(" in source)
    }

    @Test
    fun 앱의_정체를_덜_드러낸다() {
        val source = main("kotlin/io/github/donggi/iroirobrowser/browser/WebViews.kt")
        val hide = body(source, "private fun hideIdentity(settings: WebSettings)")
        assertTrue("WebViewMediaIntegrityApiStatusConfig.WEBVIEW_MEDIA_INTEGRITY_API_DISABLED" in hide, hide)
        assertTrue("WebSettingsCompat.setDownloadFaviconsEnabled(settings, false)" in hide, hide)
        assertTrue("isFeatureSupported(WebViewFeature.WEBVIEW_MEDIA_INTEGRITY_API_STATUS)" in hide, hide)
        assertTrue("isFeatureSupported(WebViewFeature.DOWNLOAD_FAVICONS_ENABLED)" in hide, hide)
        assertTrue(Regex("setAcceptThirdPartyCookies[(]view, false[)]\\s*\\n\\s*hideIdentity[(]view.settings[)]").containsMatchIn(source))
    }

    /** [signature] 로 시작하는 선언의 몸(첫 `{` 부터 짝이 맞는 `}` 까지). */
    private fun body(source: String, signature: String): String {
        val at = source.indexOf(signature)
        assertTrue(at >= 0, signature)
        val open = source.indexOf('{', at + signature.length - 1)
        var depth = 0
        for (i in open until source.length) {
            when (source[i]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return source.substring(at, i + 1)
            }
        }
        error("짝이 맞지 않는다: $signature")
    }

    private fun main(path: String) = File("src/main/$path").readText().replace("\r\n", "\n")

    @Test
    fun 보관_기간을_돌아올_때마다_건다() {
        val activity = main("kotlin/io/github/donggi/iroirobrowser/browser/BrowserActivity.kt")
        assertTrue(Regex("trimLiveTabs[(][)]\\s*\\n\\s*//[^\\n]*\\n\\s*applyRetention[(][)]").containsMatchIn(activity))
        val retention = body(activity, "private fun applyRetention()")
        assertTrue("app.library.expireHistory(it)" in retention, retention)
        assertTrue("clearCache(true)" in retention && "app.settings.cacheClearedAt = now" in retention, retention)
        val library = main("kotlin/io/github/donggi/iroirobrowser/library/Library.kt")
        // 마지막으로 간 때로만 — 북마크는 건드리지 않는다.
        // 몸을 끝의 들여쓴 '}' 까지로 자른다 — 기본값 `= {}` 의 중괄호가 짝 셈을 먼저 끝낸다.
        val expire = library.substring(library.indexOf("fun expireHistory(cutoff: Long")).substringBefore("\n    }\n")
        assertTrue("delete(\"history\", \"last_visit < ?\"" in expire, expire)
        val settings = main("kotlin/io/github/donggi/iroirobrowser/settings/SettingsActivity.kt")
        assertTrue("set = { app.settings.historyDays = it }" in settings && "set = { app.settings.cacheDays = it }" in settings)
        // 손으로 다 지우면 캐시 주기를 다시 센다.
        assertTrue("app.settings.cacheClearedAt = System.currentTimeMillis()" in body(settings, "private fun clearData()"))
    }
}
