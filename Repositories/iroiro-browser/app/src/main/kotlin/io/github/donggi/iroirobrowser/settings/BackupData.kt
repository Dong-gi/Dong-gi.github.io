package io.github.donggi.iroirobrowser.settings

import io.github.donggi.iroirobrowser.BuildConfig
import io.github.donggi.iroirobrowser.WebApp
import io.github.donggi.iroirobrowser.core.SearchEngine
import io.github.donggi.iroirobrowser.core.backup.Backup
import io.github.donggi.iroirobrowser.core.backup.BackupJson
import io.github.donggi.iroirobrowser.core.backup.BackupRules
import io.github.donggi.iroirobrowser.core.backup.BookmarkEntry
import io.github.donggi.iroirobrowser.core.backup.CurrentState
import io.github.donggi.iroirobrowser.core.backup.HistoryEntry
import io.github.donggi.iroirobrowser.core.backup.ImportPlan

/**
 * 설정 › 백업의 앱 쪽 — 지금 상태를 모으고([snapshot]·[current]) 가져오기의 결정을 실제로 건다([apply]). **메인 스레드에서 부른다** — 설정·광고
 * 차단·숨긴 요소·팝업 허용이 메인 스레드의 것이다. 북마크·방문 기록은 `Library` 가 일꾼 스레드에서 따로 읽고 쓴다(`exportRows`·`importRows`).
 * 무엇을 넣고 빼는지·왜 그런지는 `:core` 의 [Backup] 과 CLAUDE.md '설정·북마크 백업'.
 */
internal object BackupData {

    /** 내보낼 백업과 그 수 — 북마크·방문 기록은 가져올 때와 같은 검사로 거른 것이다(그대로 다시 가져올 수 있게). */
    class Exported(val backup: Backup, val bookmarks: Int, val history: Int)

    /**
     * 지금 상태를 백업으로. [bookmarks]·[history] 는 Library 에서 읽은 것(기록은 넣기로 골랐을 때만 — 아니면 null). 프록시는 [withProxy] 이고
     * 호스트가 있을 때만 주소·포트·사용자를 넣는다 — **비밀번호와 프록시 스위치는 넣지 않는다.**
     */
    fun snapshot(app: WebApp, withProxy: Boolean, bookmarks: List<BookmarkEntry>, history: List<HistoryEntry>?): Exported {
        val s = app.network.settings
        val ads = app.adblock
        val marks = cleanBookmarks(bookmarks)
        val visits = history?.let(::cleanHistory)
        val backup = Backup(
            app = BuildConfig.VERSION_NAME,
            created = System.currentTimeMillis(),
            settings = Backup.Settings(
                searchEngine = app.settings.searchEngine.id,
                fullscreen = app.settings.fullscreen,
                forceZoom = app.settings.forceZoom,
                torrentWifiOnly = app.settings.torrentWifiOnly,
                liveTabs = app.settings.liveTabs,
                historyDays = app.settings.historyDays,
                cacheDays = app.settings.cacheDays,
                exitCheckUrl = s.exitCheckUrl,
                bypass = Backup.BypassPart(s.bypass.hosts, s.bypass.cidrs, s.bypass.privateNetworks, s.bypass.simpleHostnames),
            ),
            proxy = if (withProxy && s.host.isNotBlank()) Backup.ProxyAccount(s.host.trim(), s.port, s.user) else null,
            adblock = Backup.AdBlock(
                enabled = ads.enabled,
                wifiOnly = ads.wifiOnly,
                lists = ads.lists.filter { it.builtin }.map { Backup.ListState(it.id, it.enabled) },
                customLists = ads.lists.filter { !it.builtin }.map { Backup.CustomList(it.url, it.title, it.enabled) },
                // 쓰다 보면 느는 목록은 가져오기의 상한까지만(넘는 파일은 통째로 거절된다 — 실제로는 닿지 않는 수다).
                allowedSites = ads.allowedSites().take(BackupJson.MAX_SITES),
            ),
            hidden = app.hidden.rules.rules.take(BackupJson.MAX_HIDDEN),
            popupSites = app.popups.sites().take(BackupJson.MAX_SITES),
            bookmarks = marks,
            history = visits,
        )
        return Exported(backup, marks.size, visits?.size ?: 0)
    }

    /**
     * 내보낼 북마크 — 가져올 때와 같은 검사([BackupRules.webUrl]·[BackupRules.cleanTitle])를 지난 것, 넣은 차례에서 최근 [BackupJson.MAX_BOOKMARKS]
     * 개까지(가져오기가 그보다 많은 파일을 거절한다).
     */
    fun cleanBookmarks(list: List<BookmarkEntry>): List<BookmarkEntry> = list.mapNotNull { b ->
        BackupRules.webUrl(b.url)?.let { BookmarkEntry(it, BackupRules.cleanTitle(b.title), b.created) }
    }.distinctBy { it.url }.takeLast(BackupJson.MAX_BOOKMARKS)

    /**
     * 내보낼 방문 기록 — 같은 검사를 지난 것, 최근 [BackupJson.MAX_HISTORY] 곳까지. Library 는 넘친 기록을 100번 적을 때마다 지우므로 표에 1만 곳보다
     * 조금 많을 수 있다 — 그대로 내보내면 가져오기가 파일을 통째로 거절한다.
     */
    private fun cleanHistory(list: List<HistoryEntry>): List<HistoryEntry> = list.mapNotNull { h ->
        BackupRules.webUrl(h.url)?.let {
            val visits = h.visits.coerceIn(1, BackupRules.MAX_VISITS)
            HistoryEntry(it, BackupRules.cleanTitle(h.title), h.lastVisit, visits, h.typed.coerceIn(0, visits))
        }
    }.distinctBy { it.url }.take(BackupJson.MAX_HISTORY)

    /** 가져오기 전의 지금 상태 — [bookmarks]·[history] 는 Library 의 주소(`knownUrls`). */
    fun current(app: WebApp, bookmarks: Set<String>, history: Set<String>) = CurrentState(
        bookmarkUrls = bookmarks,
        historyUrls = history,
        historyCap = app.library.historyCap,
        hidden = app.hidden.rules.rules,
        popupSites = app.popups.sites().toSet(),
        adblockEnabled = app.adblock.enabled,
        adblockWifiOnly = app.adblock.wifiOnly,
        adblockAllowed = app.adblock.allowedSites().toSet(),
        lists = app.adblock.lists.map { CurrentState.ListInfo(it.id, it.url, it.builtin, it.enabled) },
        searchEngine = app.settings.searchEngine.id,
        fullscreen = app.settings.fullscreen,
        forceZoom = app.settings.forceZoom,
        torrentWifiOnly = app.settings.torrentWifiOnly,
        liveTabs = app.settings.liveTabs,
        historyDays = app.settings.historyDays,
        cacheDays = app.settings.cacheDays,
        proxy = app.network.settings,
    )

    /**
     * 가져오기의 결정을 건다 — 북마크·방문 기록은 빼고(Library 가 따로). 화면들이 쓰는 길을 그대로 지난다: 설정의 저장(곧바로 쓰는 것은
     * `commit`), 토렌트의 망 조건 다시 묻기, 광고 차단의 켜기·목록(켠 목록을 아직 받지 않았으면 받는다), 숨긴 요소, 팝업 허용, 그리고 프록시
     * 설정은 설정 화면의 '저장' 과 같은 [io.github.donggi.iroirobrowser.proxy.NetworkPath.update](길이 바뀌면 중계를 새로 세우고 판이 오른다 —
     * 그대로면 저장만).
     */
    fun apply(app: WebApp, plan: ImportPlan) {
        val settings = app.settings
        plan.searchEngine?.let { settings.searchEngine = SearchEngine.byId(it) }
        plan.fullscreen?.let { settings.fullscreen = it }
        plan.forceZoom?.let { settings.forceZoom = it }
        // 줄였으면 브라우저 화면으로 돌아갈 때(onResume) 넘는 탭을 접는다 — 설정 화면에서 고를 때와 같다.
        plan.liveTabs?.let { settings.liveTabs = it }
        // 보관 기간도 브라우저 화면으로 돌아갈 때 걸린다(applyRetention).
        plan.historyDays?.let { settings.historyDays = it }
        plan.cacheDays?.let { settings.cacheDays = it }
        plan.torrentWifiOnly?.let {
            settings.torrentWifiOnly = it
            app.torrents.settingsChanged()
        }
        val ads = app.adblock
        plan.adblockEnabled?.let(ads::setEnabled)
        plan.adblockWifiOnly?.let(ads::setWifiOnly)
        for (toggle in plan.listToggles) ads.lists.firstOrNull { it.id == toggle.id }?.let { ads.setListEnabled(it, toggle.on) }
        for (list in plan.listsToAdd) ads.addList(list.url, list.title, list.on)
        ads.allowSites(plan.allowedSites)
        if (plan.hidden.isNotEmpty()) app.hidden.restore(plan.hidden)
        app.popups.allowAll(plan.popupSites)
        plan.proxy?.let { app.network.update(it) }
    }
}
