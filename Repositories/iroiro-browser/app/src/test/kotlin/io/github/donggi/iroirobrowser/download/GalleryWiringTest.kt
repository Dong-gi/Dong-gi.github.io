package io.github.donggi.iroirobrowser.download

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 갤러리 받기(pixiv·hitomi)의 배선을 **소스와 매니페스트로** 박는다 — 기기 없이 볼 수 있는 것만. 사용자 결정(2026-10-11)과 그 권고:
 *
 * - 사이트에 묻는 것도 파일을 받는 것도 내려받기의 길(`DownloadCenter.fetcher()` = `NetworkPath.httpRoute()`)뿐이다 — 세 번째 '바로 나감' 이 없다.
 *   해석기(`GalleryResolver`)와 그것에 주는 fetch 는 내려받기 센터에서만 짓는다.
 * - 다른 앱의 VIEW 인텐트로 주소를 받지 않는다(이 브라우저 안에서 연 페이지뿐 — 사용자 결정 '다른 앱의 링크 받기 — 보류' 그대로).
 * - 화면의 글은 짧다(사용자: '설명이 너무 장황합니다').
 */
class GalleryWiringTest {

    private val download = File("src/main/kotlin/io/github/donggi/iroirobrowser/download")

    @Test
    fun 앱의_HTTP_는_내려받기의_길_하나로만() {
        val center = File(download, "DownloadCenter.kt").readText()
        // HttpFetcher 를 짓는 곳은 fetcher() 하나이고 그 길은 NetworkPath.httpRoute() 다.
        assertEquals(1, Regex("""HttpFetcher\(""").findAll(center).count())
        assertTrue(Regex("""HttpFetcher\(\s*network\.httpRoute\(\)""").containsMatchIn(center))
        // 해석기에 주는 fetch 는 siteFetch(getBytes — 같은 fetcher)뿐이다.
        val resolvers = Regex("""GalleryResolver\(([^)]*)""").findAll(center).map { it.groupValues[1].trim() }.toList()
        assertEquals(2, resolvers.size, "계획 읽기와 받기 — 둘")
        for (args in resolvers) assertTrue(args.startsWith("siteFetch("), args)
        assertTrue(Regex("""getBytes\(url, userAgent, referer, item, patience, limit, stop\)""").containsMatchIn(center))
        // 갤러리의 다른 파일은 망에 손대지 않는다.
        for (name in listOf("GalleryRules.kt", "GalleryPrompt.kt", "DownloadSets.kt")) {
            val text = File(download, name).readText()
            for (word in listOf("HttpFetcher", "openConnection", "HttpURLConnection", "Socket(", "URL(", "SiteFetch", "GalleryResolver(")) {
                assertFalse(text.contains(word), "$name 이 $word 를 쓴다")
            }
        }
    }

    @Test
    fun 해석기와_fetch_는_내려받기_센터에서만_짓는다() {
        val sources = File("src/main/kotlin").walkTopDown().filter { it.isFile && it.name.endsWith(".kt") }.toList()
        assertTrue(sources.size > 50, "소스를 찾지 못했다")
        for (file in sources) {
            if (file.name == "DownloadCenter.kt") continue
            val text = file.readText()
            assertFalse(Regex("""GalleryResolver\(""").containsMatchIn(text), "${file.name} 이 해석기를 짓는다")
            assertFalse(Regex("""SiteFetch\s*\{""").containsMatchIn(text), "${file.name} 이 fetch 를 짓는다")
        }
    }

    @Test
    fun 다른_앱의_링크는_받지_않는다() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertFalse(manifest.contains("android.intent.action.VIEW"), "VIEW 인텐트 필터가 생겼다")
        for (host in listOf("pixiv", "hitomi")) assertFalse(manifest.contains(host, ignoreCase = true), "매니페스트에 $host")
    }

    @Test
    fun 갤러리의_글은_짧다() {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/values/strings_gallery.xml")).documentElement
        val nodes = root.getElementsByTagName("string")
        val strings = (0 until nodes.length).map { nodes.item(it) as Element }.associate { it.getAttribute("name") to it.textContent }
        assertTrue(strings.size >= 30, "글을 찾지 못했다")
        // 한두 문장 — 까닭과 할 일만(세부는 CLAUDE.md 에).
        for ((name, text) in strings) assertTrue(text.length <= 60, "$name 이 길다(${text.length}자): $text")
        // 그만두기는 받은 파일을 남긴다 — 보통 파일의 '받기 취소(지움)' 와 이름이 달라야 한다.
        assertEquals("그만 받기", strings["gallery_stop"])
    }
}
