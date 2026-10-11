package io.github.donggi.iroirobrowser.core.adblock

import java.io.File
import kotlin.test.Test
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
    }
}
