package io.github.donggi.iroirobrowser.core.backup

import io.github.donggi.iroirobrowser.core.adblock.HideRule
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 설정 백업 JSON — 표본은 합성만(실제 북마크·프록시 주소가 아니다 — 예시 주소는 example 도메인). */
class BackupJsonTest {

    private val now = 1_800_000_000_000L

    private fun decode(text: String) = BackupJson.decode(text, now)

    private val full = Backup(
        app = "0.9.0",
        created = 1_799_000_000_000L,
        settings = Backup.Settings(
            searchEngine = "naver",
            fullscreen = false,
            forceZoom = true,
            torrentWifiOnly = true,
            liveTabs = 8,
            exitCheckUrl = "https://cloudflare.com/cdn-cgi/trace",
            bypass = Backup.BypassPart(listOf("localhost", "*.local", "nas.example"), listOf("127.0.0.0/8", "10.1.0.0/16"), true, false),
            historyDays = 30,
            cacheDays = 7,
        ),
        proxy = Backup.ProxyAccount("proxy.example.com", 10443, "myproxyuser"),
        adblock = Backup.AdBlock(
            enabled = true,
            wifiOnly = false,
            lists = listOf(Backup.ListState("easylist", true), Backup.ListState("youslist", false)),
            customLists = listOf(Backup.CustomList("https://lists.example.org/ko.txt", "한국어 목록", true)),
            allowedSites = listOf("example.com", "example.co.kr"),
        ),
        hidden = listOf(HideRule("example.com", "div.ad"), HideRule("example.org", "#banner > a:nth-of-type(2)")),
        popupSites = listOf("pay.example"),
        bookmarks = listOf(
            BookmarkEntry("https://example.com/", "예시 \"따옴표\" \\ 역슬래시", 1_700_000_000_123L),
            BookmarkEntry("http://192.168.0.1/", "공유기", 1_600_000_000_000L),
        ),
        history = listOf(HistoryEntry("https://example.com/a", "A", 1_790_000_000_000L, 12, 3)),
    )

    @Test
    fun 왕복() {
        val text = BackupJson.encode(full)
        val decoded = decode(text)
        assertEquals(full, decoded.backup)
        assertEquals(0, decoded.skipped)
        // 빈 백업도.
        assertEquals(Backup(), decode(BackupJson.encode(Backup())).backup)
    }

    @Test
    fun 사람이_읽을_수_있는_꼴이고_비밀번호_칸이_없다() {
        val text = BackupJson.encode(full)
        val lines = text.lines()
        assertEquals("{", lines.first())
        assertEquals("  \"format\": \"iroiro-web-backup\",", lines[1])
        assertEquals("  \"v\": 1,", lines[2])
        // 북마크는 한 줄에 하나.
        assertTrue(lines.any { it.startsWith("    {\"url\":\"https://example.com/\"") }, text)
        for (word in listOf("password", "secret", "passphrase", "cookie", "session", "tether")) {
            assertFalse(word in text.lowercase(), word)
        }
    }

    @Test
    fun 다른_파일과_새_판은_거절한다() {
        fun reason(text: String) = assertFailsWith<BackupException>(text) { decode(text) }.reason
        assertEquals(BackupException.Reason.NOT_BACKUP, reason("""{"format":"other","v":1}"""))
        assertEquals(BackupException.Reason.NOT_BACKUP, reason("""{"v":1}"""))
        assertEquals(BackupException.Reason.NOT_BACKUP, reason("""[1,2]"""))
        assertEquals(BackupException.Reason.NOT_BACKUP, reason("\"iroiro-web-backup\""))
        assertEquals(BackupException.Reason.NEWER_VERSION, reason("""{"format":"iroiro-web-backup","v":2}"""))
        assertEquals(BackupException.Reason.MALFORMED, reason("""{"format":"iroiro-web-backup"}"""))
        assertEquals(BackupException.Reason.MALFORMED, reason("""{"format":"iroiro-web-backup","v":"1"}"""))
        assertEquals(BackupException.Reason.MALFORMED, reason("""{"format":"iroiro-web-backup","v":0}"""))
        assertEquals(BackupException.Reason.MALFORMED, reason("""{"format":"iroiro-web-backup","v":1,}"""))
        assertEquals(BackupException.Reason.MALFORMED, reason(""))
        assertEquals(BackupException.Reason.MALFORMED, reason("<!DOCTYPE NETSCAPE-Bookmark-file-1>"))
    }

    @Test
    fun 모르는_칸은_무시하고_꼴이_틀린_값은_세고_건너뛴다() {
        val text = """
            {"format":"iroiro-web-backup","v":1,"future":{"x":1},
             "settings":{"searchEngine":"yahoo","fullscreen":"yes","forceZoom":true,"exitCheckUrl":"ftp://x/",
                         "bypass":{"hosts":["*.ok.example","*",7,"010.0.0.1"],"cidrs":["010.0.0.0/8","192.168.0.0/16"],"privateNetworks":1}},
             "proxy":{"host":"https://proxy.example.com","port":10443,"user":"u"},
             "adblock":{"enabled":true,"lists":[{"id":"easylist","on":false},{"id":"Bad Id","on":true},{"id":"x"},3],
                        "customLists":[{"url":"javascript:x"},{"url":"https://l.example/a.txt"}],"allowedSites":["ok.example","a b"]},
             "hiddenElements":[{"site":"example.com","selector":"div{}"},{"site":"example.com","selector":"div.ad"},{"site":"example.com"}],
             "popupSites":["pay.example","*bad*"],
             "bookmarks":[{"url":"javascript:alert(1)"},{"url":"https://ok.example/","title":7,"created":-5},"x",
                          {"url":"https://ok.example/","title":"같은 주소"}],
             "history":[{"url":"https://h.example/","visits":99999999,"typed":5,"lastVisit":99999999999999999},{"url":"data:,x"}]}
        """.trimIndent()
        val d = decode(text)
        val s = d.backup.settings!!
        assertNull(s.searchEngine)
        assertNull(s.fullscreen)
        assertEquals(true, s.forceZoom)
        assertNull(s.exitCheckUrl)
        assertEquals(listOf("*.ok.example"), s.bypass!!.hosts)
        assertEquals(listOf("192.168.0.0/16"), s.bypass!!.cidrs)
        assertNull(s.bypass!!.privateNetworks)
        assertNull(d.backup.proxy)
        val ads = d.backup.adblock!!
        assertEquals(listOf(Backup.ListState("easylist", false)), ads.lists)
        assertEquals(listOf(Backup.CustomList("https://l.example/a.txt", "l.example", true)), ads.customLists)
        assertEquals(listOf("ok.example"), ads.allowedSites)
        assertEquals(listOf(HideRule("example.com", "div.ad")), d.backup.hidden)
        assertEquals(listOf("pay.example"), d.backup.popupSites)
        assertEquals(listOf(BookmarkEntry("https://ok.example/", "", now)), d.backup.bookmarks)
        assertEquals(listOf(HistoryEntry("https://h.example/", "", now, BackupRules.MAX_VISITS, 5)), d.backup.history)
        // searchEngine·fullscreen·exitCheckUrl·hosts 셋·cidrs 하나·privateNetworks·proxy·lists 셋·customLists 하나·allowedSites 하나·
        // hidden 둘·popupSites 하나·bookmarks 둘(javascript:·"x")·history 하나 — 같은 주소는 세지 않고 합친다.
        assertEquals(1 + 1 + 1 + 3 + 1 + 1 + 1 + 3 + 1 + 1 + 2 + 1 + 2 + 1, d.skipped)
    }

    @Test
    fun 프록시_칸은_셋이_모두_맞아야_한다() {
        fun proxy(json: String) = decode("""{"format":"iroiro-web-backup","v":1,"proxy":$json}""").backup.proxy
        assertEquals(Backup.ProxyAccount("proxy.example.com", 10443, "me"), proxy("""{"host":" proxy.example.com ","port":10443,"user":"me"}"""))
        assertEquals(Backup.ProxyAccount("proxy.example.com", 443, ""), proxy("""{"host":"proxy.example.com","port":443}"""))
        assertNull(proxy("""{"host":"proxy.example.com","port":0,"user":"me"}"""))
        assertNull(proxy("""{"host":"proxy.example.com","port":"10443","user":"me"}"""))
        assertNull(proxy("""{"host":"","port":10443,"user":"me"}"""))
        assertNull(proxy("""{"host":"proxy.example.com","port":10443,"user":"a\r\nb"}"""))
        assertNull(proxy("""{"port":10443,"user":"me","password":"x"}"""))
    }

    @Test
    fun 상한() {
        val tooMany = """{"format":"iroiro-web-backup","v":1,"popupSites":[""" + (0..BackupJson.MAX_SITES).joinToString(",") { "\"s$it.example\"" } + "]}"
        assertEquals(BackupException.Reason.TOO_LARGE, assertFailsWith<BackupException> { decode(tooMany) }.reason)
        val deep = """{"format":"iroiro-web-backup","v":1,"x":""" + "[".repeat(40) + "]".repeat(40) + "}"
        assertEquals(BackupException.Reason.MALFORMED, assertFailsWith<BackupException> { decode(deep) }.reason)
        val big = """{"format":"iroiro-web-backup","v":1,"app":"""" + "a".repeat(BackupJson.MAX_BYTES) + "\"}"
        assertEquals(BackupException.Reason.TOO_LARGE, assertFailsWith<BackupException> { decode(big) }.reason)
        // 상한을 채운 파일은 읽힌다.
        val fits = Backup(
            bookmarks = List(BackupJson.MAX_BOOKMARKS) { BookmarkEntry("https://e$it.example/", "북마크 $it", 1_700_000_000_000L + it) },
            history = List(BackupJson.MAX_HISTORY) { HistoryEntry("https://h$it.example/", "기록 $it", 1_700_000_000_000L + it, 1 + it % 7, it % 2) },
        )
        val text = BackupJson.encode(fits)
        assertTrue(text.length < BackupJson.MAX_BYTES)
        assertEquals(fits, decode(text).backup)
    }

    /** 무작위 백업 → 글 → 백업 이 같은가(값은 검사를 지난 꼴로 짓는다 — 검사가 바꾸는 값은 위 시험이 본다). 고정 씨앗. */
    @Test
    fun 왕복_속성() {
        val random = Random(1016)
        val pool = "aZ가\"\\/{}[]:,&<>" + Char(0xD83D) + Char(0xDE00) + Char(0x202E) + Char(1) + "\n\t "
        fun text(n: Int) = BackupRules.cleanTitle(buildString { repeat(random.nextInt(n)) { append(pool[random.nextInt(pool.length)]) } })
        fun time() = 1_000_000_000L + random.nextLong(now - 1_000_000_000L)
        repeat(300) { round ->
            val backup = Backup(
                app = if (random.nextBoolean()) "0.9.${random.nextInt(9)}" else null,
                created = if (random.nextBoolean()) time() else null,
                settings = if (random.nextBoolean()) {
                    Backup.Settings(
                        searchEngine = listOf("google", "bing", null).random(random),
                        fullscreen = listOf(true, false, null).random(random),
                        exitCheckUrl = if (random.nextBoolean()) "https://x.example/${random.nextInt(99)}" else null,
                        bypass = if (random.nextBoolean()) Backup.BypassPart(listOf("*.local", "nas"), emptyList(), random.nextBoolean(), null) else null,
                    )
                } else null,
                adblock = if (random.nextBoolean()) Backup.AdBlock(wifiOnly = random.nextBoolean(), allowedSites = emptyList()) else null,
                bookmarks = List(random.nextInt(5)) { BookmarkEntry("https://b$it.example/${random.nextInt(9)}", text(20), time()) },
                history = List(random.nextInt(5)) {
                    val visits = 1 + random.nextInt(50)
                    HistoryEntry("https://h$it.example/", text(10), time(), visits, random.nextInt(visits + 1))
                },
            )
            val back = decode(BackupJson.encode(backup))
            assertEquals(backup, back.backup, "판 $round")
            assertEquals(0, back.skipped, "판 $round")
        }
    }

    @Test
    fun 살아_있는_탭의_수는_범위_밖이면_건너뛴다() {
        val d = decode("""{"format":"iroiro-web-backup","v":1,"settings":{"liveTabs":99}}""")
        assertNull(d.backup.settings!!.liveTabs)
        assertEquals(1, d.skipped)
        // 꼴이 틀려도(글) 그 값만 — 옆의 칸은 그대로 읽는다.
        val e = decode("""{"format":"iroiro-web-backup","v":1,"settings":{"liveTabs":"8","fullscreen":true}}""")
        assertNull(e.backup.settings!!.liveTabs)
        assertEquals(true, e.backup.settings!!.fullscreen)
        assertEquals(1, e.skipped)
    }

    @Test
    fun 보관_기간은_고르는_값만() {
        val d = decode("""{"format":"iroiro-web-backup","v":1,"settings":{"historyDays":3,"cacheDays":7}}""")
        assertNull(d.backup.settings!!.historyDays)
        assertEquals(7, d.backup.settings!!.cacheDays)
        assertEquals(1, d.skipped)
        val e = decode("""{"format":"iroiro-web-backup","v":1,"settings":{"historyDays":0,"cacheDays":99999999999}}""")
        assertEquals(0, e.backup.settings!!.historyDays)
        assertNull(e.backup.settings!!.cacheDays)
        assertEquals(1, e.skipped)
    }
}
