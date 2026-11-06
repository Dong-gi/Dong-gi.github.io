package io.github.donggi.iroirobrowser.core.backup

import io.github.donggi.iroirobrowser.core.backup.BackupException.Reason
import io.github.donggi.iroirobrowser.core.sites.Json
import io.github.donggi.iroirobrowser.core.sites.JsonException

/**
 * 설정 백업 파일 — 판 번호를 단 JSON 하나(`{"format":"iroiro-web-backup","v":1,…}`). 칸은 CLAUDE.md '설정·북마크 백업' 의 표.
 *
 * - **쓰기** — 사람이 읽을 수 있게 맨 위의 칸마다 한 줄, 목록은 항목마다 한 줄. 글은 [Json.write](제어 글자·짝 없는 서로게이트를 이스케이프).
 * - **읽기** — 남이 준 파일로 본다. [Json.parse] 가 상한([LIMITS] — 글자 수·깊이·글 하나의 길이·값의 수)을 먼저 보고, `format` 이 다르면
 *   [Reason.NOT_BACKUP], 판이 [VERSION] 보다 크면 [Reason.NEWER_VERSION] 으로 통째로 거절한다(새 판의 칸을 반만 알아듣고 가져오지 않게).
 *   판 안에서 모르는 칸은 무시한다. 칸마다 꼴이 틀리거나 검사([BackupRules] — 설정 화면과 같은 검사)를 지나지 못한 값은 그 값만 건너뛰고
 *   센다([Decoded.skipped]). 목록이 상한(`MAX_` 로 시작하는 상수)을 넘으면 통째로 거절한다 — 이 앱이 쓴 파일은 넘지 않는다.
 */
object BackupJson {

    const val FORMAT = "iroiro-web-backup"
    const val VERSION = 1

    /** 파일의 바이트 상한 — 앱이 읽을 때 이만큼까지만 읽는다(글자 수도 이것을 넘지 않는다). */
    const val MAX_BYTES = 8 * 1024 * 1024

    const val MAX_BOOKMARKS = 20_000

    /** 방문 기록은 앱이 최근 1만 곳까지만 둔다(`Library`) — 그보다 많은 파일은 이 앱이 쓴 것이 아니다. */
    const val MAX_HISTORY = 10_000
    const val MAX_HIDDEN = 5_000
    const val MAX_SITES = 2_000
    const val MAX_LISTS = 100
    const val MAX_BYPASS = 500

    /** 깊이는 이 꼴의 네 겹이면 되는데 넉넉히, 값은 위 상한을 모두 채운 파일(약 28만)보다 조금 넉넉히 — 나무의 메모리를 묶는다. */
    internal val LIMITS = Json.Limits(maxChars = MAX_BYTES, maxDepth = 16, maxStringChars = 16 * 1024, maxValues = 400_000)

    class Decoded(val backup: Backup, val skipped: Int)

    fun encode(backup: Backup): String {
        val root = LinkedHashMap<String, Any?>()
        root["format"] = FORMAT
        root["v"] = VERSION.toLong()
        backup.app?.let { root["app"] = it }
        backup.created?.let { root["created"] = it }
        backup.settings?.let { root["settings"] = settingsTree(it) }
        backup.proxy?.let { root["proxy"] = linkedMapOf("host" to it.host, "port" to it.port.toLong(), "user" to it.user) }
        backup.adblock?.let { root["adblock"] = adblockTree(it) }
        backup.hidden?.let { list -> root["hiddenElements"] = list.map { linkedMapOf("site" to it.site, "selector" to it.selector) } }
        backup.popupSites?.let { root["popupSites"] = it }
        backup.bookmarks?.let { list ->
            root["bookmarks"] = list.map { linkedMapOf<String, Any>("url" to it.url, "title" to it.title, "created" to it.created) }
        }
        backup.history?.let { list ->
            root["history"] = list.map {
                linkedMapOf<String, Any>(
                    "url" to it.url, "title" to it.title, "lastVisit" to it.lastVisit, "visits" to it.visits.toLong(), "typed" to it.typed.toLong(),
                )
            }
        }
        return pretty(root)
    }

    /** 남이 준 글을 읽는다 — [now] 는 때의 검사([BackupRules.time])와 없는 때를 채우는 데 쓴다. 받지 않으면 [BackupException]. */
    fun decode(text: String, now: Long): Decoded {
        val tree = try {
            Json.parse(text, LIMITS)
        } catch (e: JsonException) {
            throw BackupException(if (e.tooLarge) Reason.TOO_LARGE else Reason.MALFORMED, e.message ?: "")
        }
        val root = tree as? Map<*, *> ?: throw BackupException(Reason.NOT_BACKUP, "맨 위가 객체가 아니다")
        if (root["format"] != FORMAT) throw BackupException(Reason.NOT_BACKUP, "format 이 다르다")
        val version = root["v"] as? Long ?: throw BackupException(Reason.MALFORMED, "판(v)이 없다")
        if (version > VERSION) throw BackupException(Reason.NEWER_VERSION, "판 $version")
        if (version < 1) throw BackupException(Reason.MALFORMED, "판 $version")
        val r = Reader(now)
        val backup = Backup(
            // 자르고 나서 다듬는다 — 거꾸로면 자른 끝에 빈칸이 남아 다시 읽을 때 달라진다.
            app = r.string(root, "app")?.let { BackupRules.cleanTitle(BackupRules.clip(it, 64)) },
            created = r.long(root, "created")?.let { BackupRules.time(it, now) ?: r.skip() },
            settings = r.obj(root, "settings")?.let(r::settings),
            proxy = r.obj(root, "proxy")?.let(r::proxy),
            adblock = r.obj(root, "adblock")?.let(r::adblock),
            hidden = r.list(root, "hiddenElements", MAX_HIDDEN)?.let(r::hidden),
            popupSites = r.list(root, "popupSites", MAX_SITES)?.let { r.strings(it, BackupRules::site) },
            bookmarks = r.list(root, "bookmarks", MAX_BOOKMARKS)?.let(r::bookmarks),
            history = r.list(root, "history", MAX_HISTORY)?.let(r::history),
        )
        return Decoded(backup, r.skipped)
    }

    // ── 쓰기 ──────────────────────────────────────────────────────────────────────────────────────────────────

    private fun settingsTree(s: Backup.Settings): Map<String, Any?> = LinkedHashMap<String, Any?>().apply {
        s.searchEngine?.let { put("searchEngine", it) }
        s.fullscreen?.let { put("fullscreen", it) }
        s.forceZoom?.let { put("forceZoom", it) }
        s.torrentWifiOnly?.let { put("torrentWifiOnly", it) }
        s.liveTabs?.let { put("liveTabs", it.toLong()) }
        s.historyDays?.let { put("historyDays", it.toLong()) }
        s.cacheDays?.let { put("cacheDays", it.toLong()) }
        s.exitCheckUrl?.let { put("exitCheckUrl", it) }
        s.bypass?.let { b ->
            put("bypass", LinkedHashMap<String, Any?>().apply {
                b.hosts?.let { put("hosts", it) }
                b.cidrs?.let { put("cidrs", it) }
                b.privateNetworks?.let { put("privateNetworks", it) }
                b.simpleHostnames?.let { put("simpleHostnames", it) }
            })
        }
    }

    private fun adblockTree(a: Backup.AdBlock): Map<String, Any?> = LinkedHashMap<String, Any?>().apply {
        a.enabled?.let { put("enabled", it) }
        a.wifiOnly?.let { put("wifiOnly", it) }
        a.lists?.let { list -> put("lists", list.map { linkedMapOf("id" to it.id, "on" to it.on) }) }
        a.customLists?.let { list -> put("customLists", list.map { linkedMapOf("url" to it.url, "title" to it.title, "on" to it.on) }) }
        a.allowedSites?.let { put("allowedSites", it) }
    }

    /** 맨 위의 칸마다 한 줄, 맨 위의 목록은 항목마다 한 줄 — 그 안쪽은 [Json.write] 의 한 줄. */
    private fun pretty(root: Map<String, Any?>): String = buildString {
        append("{\n")
        var first = true
        for ((key, value) in root) {
            if (!first) append(",\n")
            first = false
            append("  ").append(Json.write(key)).append(": ")
            if (value is List<*> && value.isNotEmpty()) {
                append("[\n")
                value.forEachIndexed { i, item ->
                    if (i > 0) append(",\n")
                    append("    ").append(Json.write(item))
                }
                append("\n  ]")
            } else {
                append(Json.write(value))
            }
        }
        append("\n}\n")
    }

    // ── 읽기 ──────────────────────────────────────────────────────────────────────────────────────────────────

    /** 나무에서 칸을 꺼낸다 — 꼴이 틀린 값은 null 로 돌리고 [skipped] 를 센다(JSON 의 null 은 '없다' 로 친다). */
    private class Reader(private val now: Long) {
        var skipped = 0

        fun skip(): Nothing? {
            skipped++
            return null
        }

        fun obj(m: Map<*, *>, key: String): Map<*, *>? = m[key]?.let { it as? Map<*, *> ?: skip() }

        fun string(m: Map<*, *>, key: String): String? = m[key]?.let { it as? String ?: skip() }

        fun bool(m: Map<*, *>, key: String): Boolean? = m[key]?.let { it as? Boolean ?: skip() }

        fun long(m: Map<*, *>, key: String): Long? = m[key]?.let { it as? Long ?: skip() }

        /** 목록 — [max] 를 넘으면 통째로 거절한다(이 앱이 쓴 파일은 넘지 않는다). */
        fun list(m: Map<*, *>, key: String, max: Int): List<*>? {
            val value = m[key] ?: return null
            val list = value as? List<*> ?: return skip()
            if (list.size > max) throw BackupException(Reason.TOO_LARGE, "$key ${list.size}개(상한 $max)")
            return list
        }

        /** 글의 목록 — [check] 를 지난 것만, 같은 것은 한 번(차례는 처음 나온 자리). */
        fun strings(list: List<*>, check: (String) -> String?): List<String> {
            val out = LinkedHashSet<String>()
            for (item in list) {
                val s = (item as? String)?.let(check)
                if (s == null) skipped++ else out += s
            }
            return out.toList()
        }

        /** 객체의 목록 — [read] 가 null 을 주거나 객체가 아니면 그 항목을 건너뛰고 센다. */
        fun <T> objects(list: List<*>, read: (Map<*, *>) -> T?): List<T> {
            val out = ArrayList<T>(list.size)
            for (item in list) {
                val value = (item as? Map<*, *>)?.let(read)
                if (value == null) skipped++ else out += value
            }
            return out
        }

        fun settings(m: Map<*, *>) = Backup.Settings(
            searchEngine = string(m, "searchEngine")?.let { BackupRules.searchEngine(it) ?: skip() },
            fullscreen = bool(m, "fullscreen"),
            forceZoom = bool(m, "forceZoom"),
            torrentWifiOnly = bool(m, "torrentWifiOnly"),
            liveTabs = long(m, "liveTabs")?.let { BackupRules.liveTabs(it) ?: skip() },
            historyDays = long(m, "historyDays")?.let { BackupRules.historyDays(it) ?: skip() },
            cacheDays = long(m, "cacheDays")?.let { BackupRules.cacheDays(it) ?: skip() },
            exitCheckUrl = string(m, "exitCheckUrl")?.let { BackupRules.settingsUrl(it) ?: skip() },
            bypass = obj(m, "bypass")?.let { b ->
                Backup.BypassPart(
                    hosts = list(b, "hosts", MAX_BYPASS)?.let { strings(it, BackupRules::bypassHost) },
                    cidrs = list(b, "cidrs", MAX_BYPASS)?.let { strings(it, BackupRules::bypassCidr) },
                    privateNetworks = bool(b, "privateNetworks"),
                    simpleHostnames = bool(b, "simpleHostnames"),
                )
            },
        )

        /** 프록시의 주소·사용자 — 셋 가운데 하나라도 못 쓰면 칸째 건너뛴다(주소만 바꾸고 옛 사용자를 남기는 일이 없게). */
        fun proxy(m: Map<*, *>): Backup.ProxyAccount? {
            val host = (m["host"] as? String)?.let(BackupRules::proxyHost)
            val port = (m["port"] as? Long)?.let(BackupRules::proxyPort)
            val user = (m["user"] ?: "").let { it as? String }?.let(BackupRules::proxyUser)
            if (host == null || port == null || user == null) return skip()
            return Backup.ProxyAccount(host, port, user)
        }

        fun adblock(m: Map<*, *>) = Backup.AdBlock(
            enabled = bool(m, "enabled"),
            wifiOnly = bool(m, "wifiOnly"),
            lists = list(m, "lists", MAX_LISTS)?.let { list ->
                objects(list) { o ->
                    val id = (o["id"] as? String)?.let(BackupRules::listId)
                    val on = o["on"] as? Boolean
                    if (id == null || on == null) null else Backup.ListState(id, on)
                }.distinctBy { it.id }
            },
            customLists = list(m, "customLists", MAX_LISTS)?.let { list ->
                objects(list) { o ->
                    val url = (o["url"] as? String)?.let(BackupRules::settingsUrl) ?: return@objects null
                    // 이름이 없으면 광고 차단 화면이 더할 때와 같이 주소의 호스트.
                    val title = (o["title"] as? String)?.let(BackupRules::cleanTitle)?.takeIf { it.isNotEmpty() }
                        ?: BackupRules.cleanTitle(url.substringAfter("://").substringBefore('/'))
                    Backup.CustomList(url, title, o["on"] as? Boolean ?: true)
                }.distinctBy { it.url }
            },
            allowedSites = list(m, "allowedSites", MAX_SITES)?.let { strings(it, BackupRules::site) },
        )

        fun hidden(list: List<*>) = objects(list) { o ->
            val site = o["site"] as? String
            val selector = o["selector"] as? String
            if (site == null || selector == null) null else BackupRules.hideRule(site, selector)
        }.distinct()

        fun bookmarks(list: List<*>) = objects(list) { o ->
            val url = (o["url"] as? String)?.let(BackupRules::webUrl) ?: return@objects null
            val title = (o["title"] as? String)?.let(BackupRules::cleanTitle) ?: ""
            val created = (o["created"] as? Long)?.let { BackupRules.time(it, now) } ?: now
            BookmarkEntry(url, title, created)
        }.distinctBy { it.url }

        fun history(list: List<*>) = objects(list) { o ->
            val url = (o["url"] as? String)?.let(BackupRules::webUrl) ?: return@objects null
            val title = (o["title"] as? String)?.let(BackupRules::cleanTitle) ?: ""
            val lastVisit = (o["lastVisit"] as? Long)?.let { BackupRules.time(it, now) } ?: now
            val visits = ((o["visits"] as? Long) ?: 1L).coerceIn(1L, BackupRules.MAX_VISITS.toLong()).toInt()
            val typed = ((o["typed"] as? Long) ?: 0L).coerceIn(0L, visits.toLong()).toInt()
            HistoryEntry(url, title, lastVisit, visits, typed)
        }.distinctBy { it.url }
    }
}
