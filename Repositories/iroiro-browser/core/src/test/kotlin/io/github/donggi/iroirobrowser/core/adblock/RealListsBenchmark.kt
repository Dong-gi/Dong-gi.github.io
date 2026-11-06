package io.github.donggi.iroirobrowser.core.adblock

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 실제 목록으로 잰다 — `local/adblock-lists` 의 `.txt`(git 무시, 손으로 내려받아 둔다)가 있을 때만. 없으면 지나간다.
 * 읽는 시간·규칙 수·건너뛴 수·요청 하나에 드는 시간·일반 숨김 CSS 의 크기를 찍고, 흔한 광고 주소를 막는지 본다.
 */
class RealListsBenchmark {

    @Test
    fun 실제_목록() {
        val dir = listOf(File("../local/adblock-lists"), File("local/adblock-lists")).firstOrNull { it.isDirectory } ?: return
        val files = dir.listFiles { f -> f.name.endsWith(".txt") }?.sortedBy { it.name } ?: return
        if (files.isEmpty()) return
        val t0 = System.nanoTime()
        val parsed = files.map { f -> f.name to FilterParser.parse(f.readText()) }
        val t1 = System.nanoTime()
        val blocker = AdBlocker(parsed.map { it.second })
        val t2 = System.nanoTime()
        parsed.forEach { (name, p) ->
            val hosts = p.network.count { it.host != null }
            val regex = p.network.count { it.regex != null }
            val noToken = p.network.count { it.host == null && it.regex == null && it.token == null }
            println("$name: 네트워크 ${p.network.size}(호스트 $hosts · 정규식 $regex · 낱말 없음 $noToken) · 숨김 ${p.cosmetic.size} · 건너뜀 ${p.skipped}")
        }
        println("읽기 ${(t1 - t0) / 1_000_000} ms · 엔진 ${(t2 - t1) / 1_000_000} ms · 네트워크 ${blocker.networkRuleCount} · 숨김 ${blocker.cosmeticRuleCount}")

        val page = blocker.page("https://www.example.com/news/1")
        val start = blocker.startCss(page)
        val classes = listOf("header", "nav", "content", "article", "ad", "ads", "advertisement", "banner", "sidebar", "footer", "adsbygoogle", "sponsored")
        val keyed = blocker.genericCss(page, classes, listOf("main", "ad", "google_ads"))
        println("일반 숨김: 열쇠 없는 것 ${blocker.genericAlwaysCount} · 열쇠 있는 것 ${blocker.genericKeyedCount}")
        println("쪽을 열 때의 CSS ${start.length / 1024} KB(${start.count { it == '\n' }} 줄) · 흔한 이름 ${classes.size + 3}개에 맞는 CSS ${keyed.length} 바이트(${keyed.count { it == '\n' }} 줄)")

        val urls = listOf(
            "https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js",
            "https://www.googletagmanager.com/gtag/js?id=G-XXXX",
            "https://www.google-analytics.com/analytics.js",
            "https://securepubads.g.doubleclick.net/tag/js/gpt.js",
            "https://static.example.com/js/app.js",
            "https://www.example.com/images/logo.png",
            "https://cdn.example.com/ads/banner300x250.gif",
            "https://www.example.com/api/v1/items?page=2",
        )
        urls.forEach { u ->
            val types = RequestType.guess(u, "*/*", isMainFrame = false)
            println("  ${if (blocker.shouldBlock(u, page, types) != null) "막음" else "통과"}  $u  ← ${blocker.shouldBlock(u, page, types)?.text ?: ""}")
        }
        // 속도 — 흔한 주소를 섞어 여러 번.
        val n = 200_000
        val t3 = System.nanoTime()
        var blocked = 0
        for (i in 0 until n) {
            val u = urls[i % urls.size].replace("example.com", "example$i.com")
            if (blocker.shouldBlock(u, page, RequestType.SCRIPT or RequestType.XHR) != null) blocked++
        }
        val t4 = System.nanoTime()
        println("요청 $n 개: ${(t4 - t3) / 1_000_000} ms(하나에 ${(t4 - t3) / n / 1000.0} µs), 막음 $blocked")
        assertTrue(blocker.shouldBlock(urls[0], page, RequestType.SCRIPT) != null, "adsbygoogle 를 막지 않았다")
        assertTrue(blocker.shouldBlock(urls[4], page, RequestType.SCRIPT) == null, "보통 스크립트를 막았다")
        // 종류를 모르는 제3자 요청을 막지 않는다 — 앞 판은 EasyPrivacy 의 `*$ping,third-party` 로 이것들을 막아 fast.com 이 0 을 보였다(2026-10-05).
        val thirdParty = listOf(
            "https://fast.com/" to "https://ipv4-c024-mia006-ix.1.oca.nflxvideo.net/speedtest/range/0-2048?c=us&n=1&v=1&e=1&t=x",
            "https://www.youtube.com/watch?v=x" to "https://rr1---sn-abc.googlevideo.com/videoplayback?expire=1&id=o-x&itag=18",
            "https://news.example.com/" to "https://api.example-cdn.net/v1/items?page=2",
        )
        for ((doc, u) in thirdParty) for (method in listOf("GET", "POST")) {
            val rule = blocker.shouldBlock(u, blocker.page(doc), RequestType.guess(u, "*/*", false, method))
            assertTrue(rule == null, "$method $u 를 막았다 ← ${rule?.text}")
        }
        regexRules(files, urls + thirdParty.map { it.second })
        longUrls(blocker, page)
    }

    /**
     * 정규식 규칙(2026-10-16 — 되짚지 않는 엔진 [ListRegex]). 목록의 정규식 줄은 java.util.regex 가 받는 것을 모두 받아야 하고, 같은 답이어야
     * 하며, 일부러 지은 긴 주소에서도 걸음 예산 안이어야 한다(넘으면 그 규칙이 꺼진다 — 쪽이 목록의 규칙을 끌 수 없어야 한다).
     */
    private fun regexRules(files: List<File>, sampleUrls: List<String>) {
        val sources = files.flatMap { f -> f.readLines().mapNotNull { regexSourceOf(it.trim()) } }
        val corpus = sampleUrls.map { it.lowercase() } + listOf(
            "https://a.pussyspace.com/yip/videos/202601/02/123456789/original/12.jpg",
            "https://a.pussyspace.com/yip/videos/202601/02/123456789/thumbs_12/1(m=eabcdefaaaa)(mh=abcdefghij_-1234)2.jpg",
            "https://b.pussyspace.net/xvs/videos/thumbs169l/ab/cd/ef/0123456789abcdef0123456789abcdef-1/0123456789abcdef0123456789abcdef.12.jpg",
            "https://a.pussyspace.com/sb/t/1234567/1/ab/w:300/t12-enh/abc-def-ghi.jpg",
            "https://c.pussyspace.com/ajz/a/b/c/d/0123-456-7-8-h264.mp4-9.jpg",
            "http://104.154.1.2/" + "a".repeat(120),
            "https://" + "a".repeat(31) + ".me/" + "b".repeat(31) + ".js",
            "https://news.example.club/abc/def",
            "https://x.xyz/0123456789",
        )
        val long = listOf(
            "https://x.test/" + "a".repeat(16_000),
            "https://x.test/" + "1".repeat(16_000),
            "https://x.test/" + "a.".repeat(8_000),
            "https://x.test/" + "/".repeat(16_000),
            "https://" + "a.".repeat(8_000) + "club/x",
            "https://a.pussyspace.com/jz/1/2/3/4/" + "0".repeat(16_000),
            "https://a.pussyspace.com/sb/t/1234567/1/ab/w:300/t12-enh/" + "a-".repeat(8_000) + "a",
            "https://a.pussyspace.com/yip/videos/202601/02/123456789/original/" + "1".repeat(16_000),
            "http://104.154.".repeat(1_000),
            "https://".repeat(2_000),
            "https://" + "a".repeat(16_000) + ".me/" + "b".repeat(100),
            kotlin.random.Random(3).let { r -> "https://x.test/" + String(CharArray(16_000) { "abc012./-_?=&%:".random(r) }) },
        )
        var javaOk = 0
        var oursOk = 0
        var worst = 0.0
        var worstAt = ""
        var worstPair: Pair<ListRegex, String>? = null
        for (src in sources) {
            val java = runCatching { Regex(src, RegexOption.IGNORE_CASE) }.getOrNull()
            val ours = ListRegex.compile(src, ignoreCase = true)
            if (java != null) javaOk++
            if (ours != null) oursOk++
            assertTrue(java == null || ours != null, "java.util.regex 는 받는데 우리 엔진은 받지 않는다: /$src/")
            if (java == null || ours == null) continue
            for (u in corpus) assertEquals(java.containsMatchIn(u), ours.find(u, Long.MAX_VALUE) == ListRegex.Result.MATCH, "/$src/ ← $u")
            for (u in long) {
                val steps = ours.stepsFor(u)
                if (steps.toDouble() / u.length > worst) {
                    worst = steps.toDouble() / u.length
                    worstAt = "/${src.take(60)}/ ← ${u.take(40)}"
                    worstPair = ours to u
                }
                assertTrue(steps <= ListRegex.budgetFor(u.length), "/$src/ 가 긴 주소에서 예산을 넘는다(걸음 $steps, 길이 ${u.length})")
            }
        }
        assertTrue(sources.isNotEmpty())
        // 걸음 하나의 값 — 예산을 다 쓰는 데 드는 시간을 어림한다(이 PC 의 JVM — 폰은 몇 배).
        worstPair?.let { (re, u) ->
            val steps = re.stepsFor(u)
            val t0 = System.nanoTime()
            repeat(20) { re.find(u, Long.MAX_VALUE) }
            println("걸음 하나에 ${"%.2f".format((System.nanoTime() - t0).toDouble() / 20 / steps)} ns(가장 무거운 규칙, ${u.length} 글자)")
        }
        println("정규식 줄 ${sources.size} · java.util.regex 가 받음 $javaOk · 우리 엔진이 받음 $oursOk · 일부러 지은 긴 주소에서 한 글자에 걸음 최대 ${"%.1f".format(worst)}(예산 ${ListRegex.STEPS_PER_CHAR}) — $worstAt")
    }

    /** 쪽이 지을 수 있는 아주 긴 주소(1 MB 안팎) — 엔진 전체가 곧 끝나야 한다(되풀이한 조각·낱말·긴 호스트). */
    private fun longUrls(blocker: AdBlocker, page: AdBlocker.Page) {
        val crafted = listOf(
            "https://x.test" + "/ads/".repeat(200_000),
            "https://x.test/" + "banner-ad-".repeat(100_000),
            "https://" + "ad.".repeat(300_000) + "example/x.js",
            "https://x.test/?" + "utm_source=x&ad=1&".repeat(50_000),
            "https://x.test/" + "a".repeat(1_000_000),
        )
        for (u in crafted) {
            val t0 = System.nanoTime()
            for (types in listOf(RequestType.SCRIPT, RequestType.IMAGE, RequestType.guess(u, "*/*", false))) blocker.shouldBlock(u, page, types)
            val ms = (System.nanoTime() - t0) / 1_000_000
            println("긴 주소 ${u.length} 글자(${u.take(24)}…): ${ms} ms")
            assertTrue(ms < 5_000, "긴 주소 하나에 ${ms} ms")
        }
    }

    /** 목록 한 줄이 정규식 규칙이면 그 무늬(`/` 사이) — [FilterParser] 와 같은 갈래로(요소 숨김·주석 밖, 선택지를 떼고). */
    private fun regexSourceOf(line: String): String? {
        if (line.isEmpty() || line.startsWith("!") || line.startsWith("[")) return null
        if (COSMETIC.any { line.contains(it) }) return null
        var rest = line.removePrefix("@@")
        val dollar = rest.lastIndexOf('$')
        if (dollar >= 0 && rest.substring(dollar + 1).split(',').all { OPTION.matches(it.lowercase()) }) rest = rest.substring(0, dollar)
        return if (rest.length > 2 && rest.startsWith("/") && rest.endsWith("/")) rest.substring(1, rest.length - 1) else null
    }

    private val COSMETIC = listOf("##", "#@#", "#?#", "#\$#", "#%#", "#^#")
    private val OPTION = Regex("~?[a-z0-9_-]+(=.*)?")
}
