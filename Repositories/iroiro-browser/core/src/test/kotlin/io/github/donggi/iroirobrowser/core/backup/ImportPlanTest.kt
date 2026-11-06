package io.github.donggi.iroirobrowser.core.backup

import io.github.donggi.iroirobrowser.core.Bypass
import io.github.donggi.iroirobrowser.core.ProxySettings
import io.github.donggi.iroirobrowser.core.adblock.HideRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 가져오기의 결정 — 모음은 합치고 낱개 설정은 바꾸고, 프록시 비밀번호는 같은 계정일 때만 남는다. */
class ImportPlanTest {

    private val proxy = ProxySettings.DEFAULT.copy(host = "proxy.example.com", port = 10443, user = "me", password = "pw")

    private fun current(
        bookmarks: Set<String> = emptySet(),
        history: Set<String> = emptySet(),
        cap: Int = 10_000,
        proxySettings: ProxySettings = proxy,
    ) = CurrentState(
        bookmarkUrls = bookmarks,
        historyUrls = history,
        historyCap = cap,
        hidden = listOf(HideRule("example.com", "div.ad")),
        popupSites = setOf("pay.example"),
        adblockEnabled = true,
        adblockWifiOnly = false,
        adblockAllowed = setOf("ok.example"),
        lists = listOf(
            CurrentState.ListInfo("easylist", "https://easylist.to/easylist/easylist.txt", builtin = true, enabled = true),
            CurrentState.ListInfo("youslist", "https://y.example/youslist.txt", builtin = true, enabled = true),
            CurrentState.ListInfo("custom-1", "https://lists.example.org/mine.txt", builtin = false, enabled = false),
        ),
        searchEngine = "google",
        fullscreen = true,
        forceZoom = true,
        torrentWifiOnly = false,
        liveTabs = 5,
        historyDays = 0,
        cacheDays = 0,
        proxy = proxySettings,
    )

    @Test
    fun 같은_것만_든_백업은_바뀌는_것이_없다() {
        val c = current(bookmarks = setOf("https://a.example/"), history = setOf("https://h.example/"))
        val backup = Backup(
            settings = Backup.Settings("google", true, true, false, proxy.exitCheckUrl, Backup.BypassPart(proxy.bypass.hosts, proxy.bypass.cidrs, true, true)),
            proxy = Backup.ProxyAccount("proxy.example.com", 10443, "me"),
            adblock = Backup.AdBlock(true, false, listOf(Backup.ListState("easylist", true)), emptyList(), listOf("ok.example")),
            hidden = listOf(HideRule("example.com", "div.ad")),
            popupSites = listOf("pay.example"),
            bookmarks = listOf(BookmarkEntry("https://a.example/", "A", 1)),
        )
        val plan = ImportPlan.of(c, backup)
        assertTrue(plan.isEmpty)
        assertEquals(1, plan.bookmarksKnown)
        assertNull(plan.proxy)
        assertEquals(0, plan.settingsChanged)
        assertFalse(plan.needsPassword)
    }

    @Test
    fun 모음은_합친다() {
        val c = current(bookmarks = setOf("https://a.example/"), history = setOf("https://h1.example/"))
        val backup = Backup(
            hidden = listOf(HideRule("example.com", "div.ad"), HideRule("example.com", "#top")),
            popupSites = listOf("pay.example", "new.example", "new.example"),
            adblock = Backup.AdBlock(allowedSites = listOf("ok.example", "off.example")),
            bookmarks = listOf(BookmarkEntry("https://a.example/", "덮지 않는다", 5), BookmarkEntry("https://b.example/", "B", 6)),
            history = listOf(
                HistoryEntry("https://h1.example/", "", 10, 3, 1),
                HistoryEntry("https://h2.example/", "새", 11, 1, 0),
                HistoryEntry("https://h2.example/", "같은 주소", 12, 9, 0),
            ),
        )
        val plan = ImportPlan.of(c, backup, skipped = 4)
        assertEquals(listOf(BookmarkEntry("https://b.example/", "B", 6)), plan.bookmarks)
        assertEquals(1, plan.bookmarksKnown)
        assertEquals(2, plan.history.size)
        assertEquals(1, plan.historyNew)
        assertEquals(1, plan.historyMerged)
        assertFalse(plan.historyTrimmed)
        assertEquals(listOf(HideRule("example.com", "#top")), plan.hidden)
        assertEquals(listOf("new.example"), plan.popupSites)
        assertEquals(listOf("off.example"), plan.allowedSites)
        assertEquals(4, plan.skipped)
        assertNull(plan.proxy)
        assertFalse(plan.isEmpty)
    }

    @Test
    fun 방문_기록이_상한을_넘으면_알린다() {
        val c = current(history = setOf("https://h1.example/", "https://h2.example/"), cap = 3)
        val backup = Backup(history = listOf(HistoryEntry("https://h3.example/", "", 1, 1, 0), HistoryEntry("https://h4.example/", "", 1, 1, 0)))
        assertTrue(ImportPlan.of(c, backup).historyTrimmed)
        assertFalse(ImportPlan.of(current(cap = 3), backup).historyTrimmed)
    }

    @Test
    fun 광고_목록은_id와_주소로_찾고_모르는_것은_센다() {
        val backup = Backup(
            adblock = Backup.AdBlock(
                enabled = false,
                wifiOnly = true,
                lists = listOf(Backup.ListState("easylist", true), Backup.ListState("youslist", false), Backup.ListState("gone-list", true)),
                customLists = listOf(
                    Backup.CustomList("https://lists.example.org/mine.txt", "내 목록", true),
                    Backup.CustomList("https://lists.example.org/new.txt", "새 목록", false),
                    Backup.CustomList("https://lists.example.org/new.txt", "같은 주소", true),
                    // 들어 있는 목록의 주소 — 이미 위에서 id 로 고친 목록이라 두 번 고치지 않는다.
                    Backup.CustomList("https://y.example/youslist.txt", "youslist", true),
                ),
            ),
        )
        val plan = ImportPlan.of(current(), backup)
        assertEquals(listOf(Backup.ListState("youslist", false), Backup.ListState("custom-1", true)), plan.listToggles)
        assertEquals(listOf(Backup.CustomList("https://lists.example.org/new.txt", "새 목록", false)), plan.listsToAdd)
        assertEquals(false, plan.adblockEnabled)
        assertEquals(true, plan.adblockWifiOnly)
        assertEquals(2, plan.skipped) // gone-list, 두 번째 youslist
        assertEquals(2, plan.settingsChanged)
    }

    @Test
    fun 낱개_설정은_다를_때만_바꾼다() {
        val backup = Backup(settings = Backup.Settings(searchEngine = "naver", fullscreen = true, forceZoom = false, torrentWifiOnly = true, liveTabs = 8))
        val plan = ImportPlan.of(current(), backup)
        assertEquals("naver", plan.searchEngine)
        assertNull(plan.fullscreen)
        assertEquals(false, plan.forceZoom)
        assertEquals(true, plan.torrentWifiOnly)
        assertEquals(8, plan.liveTabs)
        assertEquals(4, plan.settingsChanged)
        // 같은 수면 바꾸지 않는다 — 옛 파일(칸이 없다)도 지금 값을 둔다.
        assertNull(ImportPlan.of(current(), Backup(settings = Backup.Settings(liveTabs = 5))).liveTabs)
        assertNull(ImportPlan.of(current(), Backup(settings = Backup.Settings())).liveTabs)
        assertNull(plan.proxy)
    }

    @Test
    fun 예외_규칙은_칸째_바꾸고_공인_대역을_알린다() {
        // 사용자가 기본 규칙 하나(*.internal)를 지운 백업 — 합치면 되살아난다.
        val backup = Backup(settings = Backup.Settings(bypass = Backup.BypassPart(hosts = listOf("localhost", "*.local"), cidrs = listOf("8.8.8.0/24", "10.0.0.0/8"))))
        val plan = ImportPlan.of(current(), backup)
        val next = assertNotNull(plan.proxy)
        assertEquals(Bypass(listOf("localhost", "*.local"), listOf("8.8.8.0/24", "10.0.0.0/8"), true, true), next.bypass)
        assertTrue(plan.bypassChanged)
        assertEquals(listOf("8.8.8.0/24"), plan.publicCidrs)
        // 새로 바로 가게 되는 것만 이름을 보인다(지운 *.internal 은 프록시로 가는 쪽이다).
        assertEquals(listOf("8.8.8.0/24", "10.0.0.0/8"), plan.bypassAdded)
        assertFalse(plan.privateOpened)
        assertFalse(plan.simpleOpened)
        // 계정은 그대로 — 비밀번호를 둔다.
        assertEquals("pw", next.password)
        assertFalse(plan.accountChanged)
        assertFalse(plan.passwordCleared)
        assertEquals(0, plan.settingsChanged)
        assertFalse(plan.isEmpty)
        // 스위치는 건드리지 않는다.
        assertEquals(proxy.enabled, next.enabled)
        val off = current(proxySettings = proxy.copy(enabled = false))
        assertEquals(false, ImportPlan.of(off, backup).proxy!!.enabled)
    }

    @Test
    fun 비밀번호는_호스트와_사용자가_그대로일_때만_남는다() {
        fun plan(host: String, port: Int, user: String) = ImportPlan.of(current(), Backup(proxy = Backup.ProxyAccount(host, port, user)))
        // 포트만 바뀌었다 — 같은 계정.
        plan("proxy.example.com", 443, "me").let {
            assertEquals("pw", it.proxy!!.password)
            assertTrue(it.accountChanged)
            assertFalse(it.passwordCleared)
            assertFalse(it.needsPassword)
        }
        // 호스트가 바뀌었다 — 옛 비밀번호를 새 서버에 보내지 않는다.
        plan("other.example.com", 10443, "me").let {
            assertEquals("", it.proxy!!.password)
            assertTrue(it.passwordCleared)
            assertTrue(it.needsPassword)
            assertEquals("other.example.com", it.proxy.host)
        }
        // 사용자가 바뀌었다.
        plan("proxy.example.com", 10443, "you").let {
            assertEquals("", it.proxy!!.password)
            assertTrue(it.needsPassword)
        }
        // 비밀번호가 없던 앱(다시 깐 앱)에 넣는다 — 지울 것은 없지만 넣어야 한다.
        val fresh = ImportPlan.of(current(proxySettings = ProxySettings.DEFAULT), Backup(proxy = Backup.ProxyAccount("proxy.example.com", 10443, "me")))
        assertFalse(fresh.passwordCleared)
        assertTrue(fresh.needsPassword)
        assertEquals("proxy.example.com", fresh.proxy!!.host)
        assertEquals(ProxySettings.DEFAULT.bypass, fresh.proxy.bypass)
        // 사용자 없는 프록시면 비밀번호도 없다.
        assertFalse(ImportPlan.of(current(proxySettings = ProxySettings.DEFAULT), Backup(proxy = Backup.ProxyAccount("p.example", 1, ""))).needsPassword)
    }

    @Test
    fun 출구_확인_주소() {
        val plan = ImportPlan.of(current(), Backup(settings = Backup.Settings(exitCheckUrl = "https://exit.example/trace")))
        assertTrue(plan.exitChanged)
        assertEquals("https://exit.example/trace", plan.proxy!!.exitCheckUrl)
        assertEquals("pw", plan.proxy.password)
        assertEquals(0, plan.settingsChanged)
        assertFalse(plan.bypassChanged)
        assertFalse(plan.isEmpty)
    }

    @Test
    fun 사설망과_점_없는_이름을_새로_열면_알린다() {
        val closed = proxy.copy(bypass = Bypass(listOf("localhost"), emptyList(), privateNetworks = false, simpleHostnames = false))
        val backup = Backup(settings = Backup.Settings(bypass = Backup.BypassPart(privateNetworks = true, simpleHostnames = true)))
        val plan = ImportPlan.of(current(proxySettings = closed), backup)
        assertTrue(plan.privateOpened)
        assertTrue(plan.simpleOpened)
        assertEquals(emptyList<String>(), plan.bypassAdded)
        // 닫는 쪽은 알리지 않는다.
        val shut = ImportPlan.of(current(), Backup(settings = Backup.Settings(bypass = Backup.BypassPart(privateNetworks = false))))
        assertTrue(shut.bypassChanged)
        assertFalse(shut.privateOpened)
        assertEquals(emptyList<String>(), shut.bypassAdded)
    }

    @Test
    fun 보관_기간도_낱개_설정처럼() {
        val plan = ImportPlan.of(current(), Backup(settings = Backup.Settings(historyDays = 90, cacheDays = 0)))
        assertEquals(90, plan.historyDays)
        assertNull(plan.cacheDays) // 같은 값
        assertEquals(1, plan.settingsChanged)
    }
}
