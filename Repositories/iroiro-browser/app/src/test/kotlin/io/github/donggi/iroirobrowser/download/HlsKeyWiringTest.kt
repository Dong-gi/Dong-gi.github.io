package io.github.donggi.iroirobrowser.download

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * m3u8 열쇠 오류 고침(2026-10-16)의 배선을 **소스와 자원으로** 박는다 — 기기 없이 볼 수 있는 것만. 판단(주소 풀기·열쇠 후보·쪽의 머리 고르기)은
 * `:core` 의 시험(UriRefsTest·HlsKeysTest·PageRequestsTest)이 본다.
 */
class HlsKeyWiringTest {

    private val main = File("src/main/kotlin/io/github/donggi/iroirobrowser")

    /** 소스의 글 — 줄 끝의 CR 은 뗀다(이 저장소에는 CRLF 파일이 섞여 있다). */
    private fun source(path: String): String = File(main, path).readText().replace(Char(13).toString(), "")

    /** [head] 로 시작하는 함수의 몸 — 다음 함수의 머리(네 칸 들여 쓴 `fun`) 앞까지. */
    private fun body(text: String, head: String): String {
        val start = text.indexOf(head)
        assertTrue(start >= 0, "$head 를 찾지 못했다")
        val next = Regex("(?m)^    (private |internal |override )?fun ").find(text, start + head.length)?.range?.first ?: text.length
        return text.substring(start, next)
    }

    @Test
    fun 재생목록은_넘겨주기_뒤의_주소에_대어_푼다() {
        val center = source("download/DownloadCenter.kt")
        assertTrue(center.contains("HlsPlaylist.parse(fetched.bytes.decodeToString(), fetched.url)"))
        assertTrue(center.contains("DashManifest.parse(fetched.bytes.decodeToString(), fetched.url)"))
        assertFalse(center.contains("HlsPlaylist.parse(bytes.decodeToString(), url)"), "처음 주소에 대어 푼다")
        assertFalse(center.contains("DashManifest.parse(bytes.decodeToString(), url)"), "처음 주소에 대어 푼다")
        // 찾은 미디어의 미리 읽기도 같다(그쪽은 처음부터 그랬다).
        assertTrue(source("browser/MediaProbe.kt").contains("HlsPlaylist.parse(bytes.decodeToString(), r.url)"))
    }

    @Test
    fun 열쇠는_쪽의_머리로_받고_첫_조각으로_확인한다() {
        val center = source("download/DownloadCenter.kt")
        val load = body(center, "private fun loadKey(")
        assertTrue(load.contains("replayFor(keyUri, track.playlistUrl"))
        assertTrue(load.contains("keyHeaders(item, replay, u)"))
        assertTrue(load.contains("HlsKeys.candidates(fetched.bytes)"))
        assertTrue(load.contains("keepTooLarge = true"), "64 KB 를 넘은 응답도 넘겨주기 수를 잃지 않게")
        val pump = body(center, "private fun pump(")
        assertTrue(pump.contains("HlsKeys.pick("))
        assertTrue(pump.contains("SequenceInputStream(ByteArrayInputStream(probe.bytes), body)"), "읽은 앞머리를 이어 쓴다(다시 받지 않는다)")
        // 정확히 16바이트만 받던 옛 검사가 남지 않았다.
        assertFalse(center.contains("it.size != 16"))
    }

    @Test
    fun 쪽의_머리는_디스크와_기록에_남지_않는다() {
        val center = source("download/DownloadCenter.kt")
        for (head in listOf("private fun toJson(i: Item)", "private fun fromJson(")) {
            assertFalse(body(center, head).contains("pageRequests"), "$head 가 쪽의 머리를 적는다")
        }
        // 기록(debug)에는 꼴과 수만 — 주소·머리의 값을 싣지 않는다.
        val lines = center.lines().filter { it.contains("debug(") && it.contains("hls key") }
        assertTrue(lines.size >= 2)
        for (line in lines) {
            for (word in listOf("keyUri", "target", ".referer", "headersFor", "replay.url", "fetched.url", "playlistUrl")) {
                assertFalse(line.contains(word), "기록에 $word: $line")
            }
        }
        val log = File("../core/src/main/kotlin/io/github/donggi/iroirobrowser/core/media/PageRequests.kt").readText()
        for (word in listOf("Log.", "println(", "System.out", "System.err")) assertFalse(log.contains(word), "PageRequests 가 $word 로 적는다")
    }

    @Test
    fun 탭이_요청을_쥐고_닫거나_떠나면_버린다() {
        val activity = source("browser/BrowserActivity.kt")
        val intercept = body(activity, "override fun intercept(")
        val block = intercept.indexOf("adBlock(tab, request, url)")
        val record = intercept.indexOf("tab.requests.record(url, request.method, request.requestHeaders")
        assertTrue(block in 0 until record, "막은 요청(나가지 않은 것)은 쥐지 않는다 — 광고 차단 뒤에 적는다")
        assertTrue(body(activity, "private fun onNavigationCommitted(").contains("tab.requests.committed(url, navigationStart)"))
        assertTrue(body(activity, "private fun closeTabs(").contains("tab.requests.clear()"))
        assertTrue(source("browser/MediaChooser.kt").contains("request.tab.requests)"), "받기에 그 탭의 요청을 넘긴다")
    }

    @Test
    fun 실패_글은_짧고_안내가_붙는다() {
        val strings = readStrings("src/main/res/values/strings.xml") + readStrings("src/main/res/values/strings_hls_key.xml")
        val prefix = strings.getValue("download_key_invalid")
        assertTrue(strings.getValue("download_key_failed").startsWith(prefix), "실패 창이 그 머리로 안내를 붙인다")
        val texts = strings.filterKeys { it.startsWith("download_key_") }
        assertTrue(texts.size >= 17, "글을 찾지 못했다")
        for ((name, text) in texts) assertTrue(text.length <= 60, "$name 이 길다(${text.length}자): $text")
        val activity = source("download/DownloadsActivity.kt")
        assertTrue(body(activity, "private fun failureMessage(").contains("R.string.download_key_hint"))
    }

    private fun readStrings(path: String): Map<String, String> {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path)).documentElement
        val nodes = root.getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as Element }.associate { it.getAttribute("name") to it.textContent }
    }
}
