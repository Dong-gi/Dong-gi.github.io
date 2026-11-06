package io.github.donggi.iroirobrowser.core.backup

import io.github.donggi.iroirobrowser.core.adblock.HideRule
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * 설정 백업 읽개의 퍼저 — 올바른 파일을 글자 단위로 뒤틀어(지우기·끼우기·바꾸기·잘라 내기·되풀이) 읽힌다. 결과는 백업이거나 [BackupException]
 * 이어야 하고 그 밖의 예외(인덱스·형 변환·숫자 변환·스택 넘침)는 하나도 없어야 한다. 받은 것은 늘 검사를 지난 꼴이고(주소는
 * [BackupRules.webUrl] 을 다시 지나도 같다, 제목은 다시 다듬어도 같다) 다시 쓰고 읽으면 같다. 고정 씨앗.
 */
class BackupFuzzTest {

    private val now = 1_800_000_000_000L

    private fun mutate(seed: String, random: Random, alphabet: String): String {
        val s = StringBuilder(seed)
        repeat(1 + random.nextInt(4)) {
            when (random.nextInt(5)) {
                0 -> if (s.isNotEmpty()) s.deleteCharAt(random.nextInt(s.length))
                1 -> s.insert(random.nextInt(s.length + 1), alphabet[random.nextInt(alphabet.length)])
                2 -> if (s.isNotEmpty()) s.setCharAt(random.nextInt(s.length), alphabet[random.nextInt(alphabet.length)])
                3 -> if (s.isNotEmpty()) s.delete(random.nextInt(s.length), s.length)
                else -> if (s.isNotEmpty()) {
                    val from = random.nextInt(s.length)
                    val to = minOf(s.length, from + random.nextInt(30))
                    s.insert(from, s.substring(from, to).repeat(1 + random.nextInt(5)))
                }
            }
        }
        return s.toString()
    }

    private fun checkBookmarks(list: List<BookmarkEntry>) {
        for (b in list) {
            assertEquals(b.url, BackupRules.webUrl(b.url), b.url)
            assertEquals(b.title, BackupRules.cleanTitle(b.title), b.title)
            assertTrue(b.created in 1..(now + 24L * 60 * 60 * 1000), b.created.toString())
        }
        assertEquals(list.size, list.map { it.url }.toSet().size)
    }

    @Test
    fun 설정_백업_뒤틀기() {
        val random = Random(16_002)
        val seed = BackupJson.encode(
            Backup(
                app = "0.9.0",
                created = 1_790_000_000_000L,
                settings = Backup.Settings("bing", true, false, true, "https://cloudflare.com/cdn-cgi/trace", Backup.BypassPart(listOf("*.local"), listOf("10.0.0.0/8"), true, true)),
                proxy = Backup.ProxyAccount("proxy.example.com", 10443, "me"),
                adblock = Backup.AdBlock(true, false, listOf(Backup.ListState("easylist", true)), listOf(Backup.CustomList("https://l.example/a.txt", "목록", false)), listOf("ok.example")),
                hidden = listOf(HideRule("example.com", "div.ad > a[href]")),
                popupSites = listOf("pay.example"),
                bookmarks = listOf(BookmarkEntry("https://example.com/", "예시 \"x\"", 1_700_000_000_000L)),
                history = listOf(HistoryEntry("https://example.com/a", "A", 1_700_000_000_000L, 3, 1)),
            ),
        )
        val alphabet = "{}[]\":,\\/ 0123456789-+.eEtruefalsnhps" + Char(0) + Char(0xD800) + "\n가u"
        var decoded = 0
        repeat(20_000) { round ->
            val text = mutate(seed, random, alphabet)
            val result = try {
                BackupJson.decode(text, now)
            } catch (e: BackupException) {
                return@repeat
            } catch (e: Throwable) {
                fail("판 $round: ${e.javaClass.name}: ${e.message}\n${text.take(400)}")
            }
            decoded++
            val backup = result.backup
            checkBookmarks(backup.bookmarks.orEmpty())
            backup.history.orEmpty().forEach {
                assertEquals(it.url, BackupRules.webUrl(it.url))
                assertTrue(it.visits >= 1 && it.typed in 0..it.visits)
            }
            backup.hidden.orEmpty().forEach { assertEquals(it, BackupRules.hideRule(it.site, it.selector)) }
            backup.popupSites.orEmpty().forEach { assertEquals(it, BackupRules.site(it)) }
            backup.settings?.bypass?.hosts.orEmpty().forEach { assertEquals(it, BackupRules.bypassHost(it)) }
            backup.settings?.bypass?.cidrs.orEmpty().forEach { assertEquals(it, BackupRules.bypassCidr(it)) }
            // 읽은 것을 다시 쓰고 읽으면 같고, 그때는 건너뛸 것이 없다.
            val again = BackupJson.decode(BackupJson.encode(backup), now)
            assertEquals(backup, again.backup, "판 $round")
            assertEquals(0, again.skipped, "판 $round")
        }
        assertTrue(decoded > 0, "뒤튼 글이 하나도 읽히지 않았다")
    }
}
