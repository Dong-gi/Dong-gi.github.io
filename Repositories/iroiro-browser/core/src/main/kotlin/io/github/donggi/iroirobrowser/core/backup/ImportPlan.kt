package io.github.donggi.iroirobrowser.core.backup

import io.github.donggi.iroirobrowser.core.Bypass
import io.github.donggi.iroirobrowser.core.ProxySettings
import io.github.donggi.iroirobrowser.core.adblock.HideRule

/**
 * 가져오기 전의 지금 상태 — 앱이 모아 넘긴다(북마크·방문 기록의 주소는 DB 에서, 나머지는 설정·광고 차단·숨긴 요소·팝업 허용에서).
 * [historyCap] 은 앱이 방문 기록을 두는 상한(넘으면 오래된 것부터 지운다).
 */
class CurrentState(
    val bookmarkUrls: Set<String>,
    val historyUrls: Set<String>,
    val historyCap: Int,
    val hidden: Collection<HideRule>,
    val popupSites: Set<String>,
    val adblockEnabled: Boolean,
    val adblockWifiOnly: Boolean,
    val adblockAllowed: Set<String>,
    val lists: List<ListInfo>,
    val searchEngine: String,
    val fullscreen: Boolean,
    val forceZoom: Boolean,
    val torrentWifiOnly: Boolean,
    val liveTabs: Int,
    val historyDays: Int,
    val cacheDays: Int,
    val proxy: ProxySettings,
) {
    /** 광고 목록 하나 — [builtin] 은 앱에 들어 있는 목록(나머지는 사용자가 주소로 더한 것). */
    class ListInfo(val id: String, val url: String, val builtin: Boolean, val enabled: Boolean)
}

/**
 * 가져오기의 결정 — 지금 상태와 백업을 견줘 무엇을 바꿀지 정한다. 순수 함수라 확인 창이 말하는 수와 실제로 하는 일이 같은 계산에서 나온다.
 *
 * - **모음은 합친다** — 북마크·방문 기록·숨긴 요소·팝업 허용·광고 차단을 끈 사이트·사용자가 더한 광고 목록. 이미 있는 것은 그대로다(북마크의
 *   이름·넣은 때를 덮지 않는다). 방문 기록은 있는 주소면 간 횟수·친 횟수·마지막 때를 큰 쪽으로 합친다(같은 파일을 두 번 가져와도 늘지 않는다
 *   — 그 일은 앱의 `Library.importRows` 가 SQL 로 한다).
 * - **낱개 설정은 바꾼다** — 검색 엔진·전체 화면·확대 허용·토렌트 Wi-Fi·살아 있는 탭 수·방문 기록 보관·캐시 비우기·광고 차단의 켜기와 Wi-Fi·
 *   목록마다의 켜기·출구 확인 주소·예외 규칙.
 *   예외 규칙은 설정 화면의 칸처럼 칸째 바꾼다 — 합치면 사용자가 일부러 지운 기본 규칙(`*.local` 등)이 되살아난다.
 * - **프록시** — 파일에 주소·사용자가 있으면 바꾸고, **호스트와 사용자가 그대로일 때만** 저장된 비밀번호를 둔다(다른 계정·다른 서버에 옛
 *   비밀번호를 보내지 않는다 — 포트만 바뀐 것은 같은 계정으로 본다). 프록시 스위치(켜기·끄기)는 건드리지 않는다.
 * - 들어 있는 목록의 id 를 이 앱이 모르면(판이 바뀌어 빠진 목록) 건너뛰고 센다.
 */
class ImportPlan(
    /** 더할 북마크(없는 주소만). */
    val bookmarks: List<BookmarkEntry>,
    /** 이미 있어 그대로 둘 북마크의 수. */
    val bookmarksKnown: Int,
    /** 합칠 방문 기록(파일의 것 모두 — 있는 주소는 합친다). */
    val history: List<HistoryEntry>,
    /** [history] 가운데 지금 없는 주소의 수. */
    val historyNew: Int,
    /** 합친 뒤 상한을 넘어 오래된 기록이 지워진다. */
    val historyTrimmed: Boolean,
    val hidden: List<HideRule>,
    val popupSites: List<String>,
    val allowedSites: List<String>,
    /** 켜기·끄기를 바꿀 목록(지금 목록의 id). */
    val listToggles: List<Backup.ListState>,
    /** 더할 목록(사용자가 주소로 더했던 것). */
    val listsToAdd: List<Backup.CustomList>,
    /** 바뀌는 낱개 설정 — 그대로면 null. */
    val adblockEnabled: Boolean?,
    val adblockWifiOnly: Boolean?,
    val searchEngine: String?,
    val fullscreen: Boolean?,
    val forceZoom: Boolean?,
    val torrentWifiOnly: Boolean?,
    val liveTabs: Int?,
    val historyDays: Int?,
    val cacheDays: Int?,
    /** 저장할 새 프록시 설정(예외 규칙·출구 확인 주소·주소·사용자·비밀번호) — 그대로면 null. */
    val proxy: ProxySettings?,
    val bypassChanged: Boolean,
    /**
     * 새 예외 규칙에서 **새로 바로 가게 되는 것** — 지금 규칙에 없던 호스트 무늬·IP 대역. 확인 창이 이름을 보인다 — 손댄 파일이 `*.google.com`
     * 을 넣으면 그곳에 실제 IP 가 드러난다(빠지는 규칙은 프록시로 가는 쪽이라 이름을 보이지 않는다).
     */
    val bypassAdded: List<String>,
    /** 사설망·점 없는 이름을 새로 바로 보낸다. */
    val privateOpened: Boolean,
    val simpleOpened: Boolean,
    val exitChanged: Boolean,
    /** 프록시의 호스트·포트·사용자가 바뀐다. */
    val accountChanged: Boolean,
    /** 저장된 비밀번호를 지운다(호스트나 사용자가 바뀌었다). */
    val passwordCleared: Boolean,
    /** 가져온 뒤 비밀번호를 넣어야 쓸 수 있다(계정이 바뀌었고 비밀번호가 없다). */
    val needsPassword: Boolean,
    /** 새 예외 규칙 가운데 사설망이 아닌 대역 — 설정 화면처럼 알린다(그곳에는 실제 IP 가 드러난다). */
    val publicCidrs: List<String>,
    /** 파일에서 알아보지 못해 건너뛴 값의 수(읽기의 것과 이 앱이 모르는 목록). */
    val skipped: Int,
) {
    val historyMerged: Int get() = history.size - historyNew

    /** 바뀌는 낱개 설정(켜기·끄기와 검색 엔진)의 수 — 예외 규칙·출구 확인 주소·프록시 주소는 확인 창이 따로 알린다. */
    val settingsChanged: Int
        get() = listOf(adblockEnabled, adblockWifiOnly, searchEngine, fullscreen, forceZoom, torrentWifiOnly, liveTabs, historyDays, cacheDays)
            .count { it != null }

    /** 바뀌는 것이 하나도 없다. */
    val isEmpty: Boolean
        get() = bookmarks.isEmpty() && history.isEmpty() && hidden.isEmpty() && popupSites.isEmpty() && allowedSites.isEmpty() &&
            listToggles.isEmpty() && listsToAdd.isEmpty() && settingsChanged == 0 && proxy == null

    companion object {
        /** [skipped] 는 읽기가 건너뛴 수([BackupJson.Decoded.skipped]) — 여기서 센 것과 더한다. */
        fun of(current: CurrentState, backup: Backup, skipped: Int = 0): ImportPlan {
            var skip = skipped

            val knownBookmarks = HashSet(current.bookmarkUrls)
            val bookmarks = ArrayList<BookmarkEntry>()
            var bookmarksKnown = 0
            for (b in backup.bookmarks.orEmpty()) {
                if (knownBookmarks.add(b.url)) bookmarks += b else bookmarksKnown++
            }

            val history = backup.history.orEmpty().distinctBy { it.url }
            val historyNew = history.count { it.url !in current.historyUrls }

            val knownHidden = HashSet(current.hidden)
            val hidden = backup.hidden.orEmpty().filter { knownHidden.add(it) }
            val popups = backup.popupSites.orEmpty().distinct().filter { it !in current.popupSites }
            val ads = backup.adblock
            val allowed = ads?.allowedSites.orEmpty().distinct().filter { it !in current.adblockAllowed }

            // 목록 — 들어 있는 것은 id 로, 사용자가 더한 것은 주소로 찾는다. 한 목록을 두 번 고치지 않는다(뒤의 것은 건너뛴다).
            val toggles = ArrayList<Backup.ListState>()
            val adds = ArrayList<Backup.CustomList>()
            val touched = HashSet<String>()
            for (state in ads?.lists.orEmpty()) {
                val list = current.lists.firstOrNull { it.builtin && it.id == state.id }
                if (list == null || !touched.add(list.id)) {
                    skip++
                } else if (list.enabled != state.on) {
                    toggles += Backup.ListState(list.id, state.on)
                }
            }
            for (custom in ads?.customLists.orEmpty()) {
                val list = current.lists.firstOrNull { it.url == custom.url }
                if (list == null) {
                    if (adds.none { it.url == custom.url }) adds += custom
                } else if (!touched.add(list.id)) {
                    skip++
                } else if (list.enabled != custom.on) {
                    toggles += Backup.ListState(list.id, custom.on)
                }
            }

            val s = backup.settings
            val cur = current.proxy
            val part = s?.bypass
            val bypass = if (part == null) {
                cur.bypass
            } else {
                Bypass(
                    hosts = part.hosts ?: cur.bypass.hosts,
                    cidrs = part.cidrs ?: cur.bypass.cidrs,
                    privateNetworks = part.privateNetworks ?: cur.bypass.privateNetworks,
                    simpleHostnames = part.simpleHostnames ?: cur.bypass.simpleHostnames,
                )
            }
            val exit = s?.exitCheckUrl ?: cur.exitCheckUrl
            val account = backup.proxy
            val host = account?.host ?: cur.host
            val port = account?.port ?: cur.port
            val user = account?.user ?: cur.user
            val sameAccount = host.trim() == cur.host.trim() && user == cur.user
            val password = if (sameAccount) cur.password else ""
            val bypassChanged = bypass != cur.bypass
            val before = (cur.bypass.hosts + cur.bypass.cidrs).toSet()
            val bypassAdded = (bypass.hosts + bypass.cidrs).distinct().filter { it !in before }
            val exitChanged = exit != cur.exitCheckUrl
            val accountChanged = host != cur.host || port != cur.port || user != cur.user
            val proxyChanged = bypassChanged || exitChanged || accountChanged || password != cur.password

            return ImportPlan(
                bookmarks = bookmarks,
                bookmarksKnown = bookmarksKnown,
                history = history,
                historyNew = historyNew,
                historyTrimmed = current.historyUrls.size + historyNew > current.historyCap,
                hidden = hidden,
                popupSites = popups,
                allowedSites = allowed,
                listToggles = toggles,
                listsToAdd = adds,
                adblockEnabled = changed(ads?.enabled, current.adblockEnabled),
                adblockWifiOnly = changed(ads?.wifiOnly, current.adblockWifiOnly),
                searchEngine = changed(s?.searchEngine, current.searchEngine),
                fullscreen = changed(s?.fullscreen, current.fullscreen),
                forceZoom = changed(s?.forceZoom, current.forceZoom),
                torrentWifiOnly = changed(s?.torrentWifiOnly, current.torrentWifiOnly),
                liveTabs = changed(s?.liveTabs, current.liveTabs),
                historyDays = changed(s?.historyDays, current.historyDays),
                cacheDays = changed(s?.cacheDays, current.cacheDays),
                proxy = if (proxyChanged) cur.copy(host = host, port = port, user = user, password = password, bypass = bypass, exitCheckUrl = exit) else null,
                bypassChanged = bypassChanged,
                bypassAdded = bypassAdded,
                privateOpened = bypass.privateNetworks && !cur.bypass.privateNetworks,
                simpleOpened = bypass.simpleHostnames && !cur.bypass.simpleHostnames,
                exitChanged = exitChanged,
                accountChanged = accountChanged,
                passwordCleared = cur.password.isNotEmpty() && password.isEmpty(),
                needsPassword = accountChanged && password.isEmpty() && user.isNotEmpty(),
                publicCidrs = if (bypassChanged) bypass.cidrs.filter(Bypass::isPublicCidr) else emptyList(),
                skipped = skip,
            )
        }

        private fun <T> changed(new: T?, old: T): T? = new?.takeIf { it != old }
    }
}
