package io.github.donggi.iroirobrowser.browser

import org.w3c.dom.Element
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 유튜브 광고 가리기(B1)·건너뛰기(B2 — [YtGuard])의 순수 판단과 스크립트의 약속. 스크립트는 JVM 에서 돌릴 수 없어(PC 의 node 가 작은 DOM
 * 흉내로 따로 돌렸다) 여기서는 앱과 주고받는 이름, 넣지 않기로 한 것(쪽의 세계 고치기·스크립트의 누르기·감기·배속·opacity)이 글에 없는지, 앱이
 * 그것을 실제로 잇는지(소스)를 본다.
 */
class YtGuardTest {

    private val script = YtGuard.SCRIPT

    @Test
    fun 출처_규칙은_유튜브와_퍼가기의_이름과_그_아래_이름이다() {
        val star = "https://" + "*."
        assertEquals(
            setOf("https://youtube.com", star + "youtube.com", "https://youtube-nocookie.com", star + "youtube-nocookie.com"),
            YtGuard.ORIGIN_RULES,
        )
    }

    @Test
    fun 유튜브의_출처만_켠다() {
        assertTrue(YtGuard.isYouTubeOrigin("https", "m.youtube.com", -1))
        assertTrue(YtGuard.isYouTubeOrigin("https", "www.youtube.com", 443))
        assertTrue(YtGuard.isYouTubeOrigin("https", "youtube.com", -1))
        assertTrue(YtGuard.isYouTubeOrigin("https", "music.youtube.com", -1))
        assertTrue(YtGuard.isYouTubeOrigin("https", "www.youtube-nocookie.com", -1)) // 퍼가기
        assertTrue(YtGuard.isYouTubeOrigin("https", "M.YouTube.com", -1))
        assertFalse(YtGuard.isYouTubeOrigin("http", "m.youtube.com", -1)) // 평문 — 규칙이 https 만
        assertFalse(YtGuard.isYouTubeOrigin("https", "m.youtube.com", 8443)) // 기본 포트만
        assertFalse(YtGuard.isYouTubeOrigin("https", "notyoutube.com", -1))
        assertFalse(YtGuard.isYouTubeOrigin("https", "youtube.com.evil.example", -1))
        assertFalse(YtGuard.isYouTubeOrigin("https", "evil-youtube.com", -1))
        assertFalse(YtGuard.isYouTubeOrigin("https", "youtu.be", -1))
        assertFalse(YtGuard.isYouTubeOrigin("https", "", -1))
        assertFalse(YtGuard.isYouTubeOrigin("https", null, -1))
        assertFalse(YtGuard.isYouTubeOrigin(null, "m.youtube.com", -1))
    }

    @Test
    fun 앱의_출처_판단은_스크립트를_거는_규칙과_같다() {
        val hosts = listOf(
            "youtube.com", "www.youtube.com", "m.youtube.com", "a.b.youtube.com", "youtube-nocookie.com", "www.youtube-nocookie.com",
            "notyoutube.com", "youtube.com.evil.example", "evil-youtube.com", "youtube.co", "youtu.be", "ytimg.com", "nocookie.com",
            "www.youtube-nocookie.com.evil.example", "M.YouTube.com", "",
        )
        for (scheme in listOf("https", "http", "HTTPS", "file")) {
            for (port in listOf(-1, 443, 80, 8443)) {
                for (host in hosts) {
                    assertEquals(matchesRules(scheme, host, port), YtGuard.isYouTubeOrigin(scheme, host, port), "$scheme $host $port")
                }
            }
        }
    }

    /**
     * androidx.webkit 의 출처 규칙(WebViewCompat.addWebMessageListener 의 문서)을 그대로 옮긴 것 — 포트가 없는 규칙은 그 스킴의 기본 포트만,
     * 별표 꼴은 그 아래 이름만(이름 자체는 아니다). 스크립트는 WebView 가 이 규칙으로 걸고 앱은 [YtGuard.isYouTubeOrigin] 으로 답하므로 둘이
     * 같아야 한다.
     */
    private fun matchesRules(scheme: String, host: String, port: Int): Boolean {
        val s = scheme.lowercase(Locale.ROOT)
        val h = host.lowercase(Locale.ROOT)
        val effective = when {
            port != -1 -> port
            s == "https" -> 443
            s == "http" -> 80
            else -> -1
        }
        return YtGuard.ORIGIN_RULES.any { rule ->
            val ruleScheme = rule.substringBefore("://")
            val pattern = rule.substringAfter("://")
            val defaultPort = if (ruleScheme == "https") 443 else 80
            when {
                ruleScheme != s || effective != defaultPort || h.isEmpty() -> false
                pattern.startsWith("*.") -> h.endsWith(pattern.substring(1)) && h != pattern.substring(2)
                else -> h == pattern
            }
        }
    }

    @Test
    fun 광고_차단이_켜져_있고_그_사이트에서_끄지_않았을_때만_켠다() {
        val asked = ArrayList<String>()
        val allowed: (String) -> Boolean = { host ->
            asked += host
            host == "m.youtube.com"
        }
        assertTrue(YtGuard.wanted(adBlockOn = true, topHost = "example.com", siteAllowed = allowed)) // 다른 사이트의 퍼가기
        assertFalse(YtGuard.wanted(adBlockOn = true, topHost = "m.youtube.com", siteAllowed = allowed)) // 그 사이트에서 껐다 — 탈출구
        assertFalse(YtGuard.wanted(adBlockOn = false, topHost = "example.com", siteAllowed = allowed)) // 광고 차단을 껐다
        assertFalse(YtGuard.wanted(adBlockOn = true, topHost = "", siteAllowed = allowed)) // 주 문서를 모른다
        // 주 문서의 호스트를 그대로 묻고, 끈 때·모를 때는 묻지 않는다.
        assertEquals(listOf("example.com", "m.youtube.com"), asked)
    }

    @Test
    fun 스크립트가_통로의_자리와_약속한_이름을_쓴다() {
        assertTrue("globalThis['${BridgeScripts.OBJECT_PLACEHOLDER}']" in script)
        assertTrue("send({ op: '${YtGuard.HELLO}' })" in script)
        assertTrue("m.op !== '${YtGuard.GUARD}'" in script)
        // 앱이 보내는 칸(on·label)과 스크립트가 보내는 칸(g).
        assertTrue("m.on" in script && "m.label" in script && "o.g = gid" in script)
        // 건너뛰기(자리 — 보이는 영역에 대한 비율)와 광고가 나오는 동안.
        assertTrue("send({ op: '${YtGuard.SKIP}', x: rx, y: ry })" in script)
        assertTrue("send({ op: '${YtGuard.AD}', on: any })" in script && "send({ op: '${YtGuard.AD}', on: false })" in script)
        // 앱으로 가는 길은 주입 객체 하나뿐이다.
        assertEquals(1, script.split("postMessage(").size - 1)
        assertTrue("ch.postMessage(" in script)
    }

    @Test
    fun 가리기는_visibility_로만_하고_건너뛰기_칸은_되살린다() {
        // opacity 는 쌓임 순서를 바꿔 건너뛰기 단추 자리의 맨 위를 영상으로 만들었다(에뮬레이터).
        assertFalse("opacity" in script)
        assertTrue("{visibility:hidden!important}" in script)
        assertTrue("{visibility:visible!important}" in script)
        assertTrue("pointer-events:none" in script)
        assertTrue("'ad-showing'" in script && "'html5-video-player'" in script)
        val hide = script.substringAfter("var HIDE = [").substringBefore("];")
        val keep = script.substringAfter("var KEEP = [").substringBefore("];")
        assertTrue("'video'" in hide && "'.ytp-ad-player-overlay'" in hide)
        assertTrue("'.ytp-ad-skip-ad-slot'" in keep)
        // 건너뛰기 칸은 가리는 목록에 없다.
        assertFalse("skip" in hide)
    }

    @Test
    fun 쪽의_세계와_재생을_건드리지_않는다() {
        // 쪽의 함수·전역을 감싸지 않고(원형·속성 정의·창에 알림), 스크립트가 누르거나(click·사건 만들기 — 건너뛰기는 앱이 진짜 터치로 누른다)
        // 감거나(B3) 배속을 바꾸지 않는다. 네트워크도 없다.
        listOf(
            ".prototype", "defineProperty", "window.postMessage", "click(", "dispatchEvent", "currentTime", "playbackRate",
            "eval(", "Function(", "fetch(", "XMLHttpRequest", "innerHTML", "setAttribute", "localStorage",
        ).forEach { assertFalse(it in script, it) }
    }

    @Test
    fun 건너뛰기는_건너뛸_수_있게_된_단추의_가운데만_알린다() {
        // 크기가 생긴 단추(그 전에는 칸이 display:none — 실측)의 가운데가 맨 위일 때, 보이는 영역 안일 때만. 2초에 한 번, 광고 미디어마다 다섯 번.
        val skip = script.substringAfter("var SKIP = [").substringBefore("];")
        assertTrue("'.ytp-ad-skip-button-modern'" in skip, skip)
        assertTrue("if (r.width < 4 || r.height < 4) continue;" in script)
        assertTrue("document.elementFromPoint(x, y)" in script && "top === b || b.contains(top)" in script)
        assertTrue("if (!(rx > 0 && rx < 1 && ry > 0 && ry < 1)) return;" in script)
        assertTrue("var SKIP_EVERY_MS = 2000, SKIP_MAX = 5, ARM_MS = 1500;" in script)
        // 알린 뒤 잠깐 단추 밖의 신뢰된 누름(재생기 안·고리)을 막는다.
        val guard = script.substringAfter("addEventListener('click', function (e) {").substringBefore("}, true);")
        assertTrue("!e.isTrusted" in guard && "armedUntil" in guard && "armed.contains(t)" in guard, guard)
        assertTrue("e.preventDefault();" in guard && "e.stopImmediatePropagation();" in guard, guard)
    }

    @Test
    fun 누를_자리는_비율을_뷰의_픽셀로_바꾸고_벗어나면_누르지_않는다() {
        assertEquals(540f to 1200f, YtGuard.tapPoint(0.5, 0.5, 1080, 2400))
        assertEquals(0.25f * 1080 to 0.1f * 2400, YtGuard.tapPoint(0.25, 0.1, 1080, 2400))
        for ((x, y) in listOf(0.0 to 0.5, 1.0 to 0.5, 0.5 to 0.0, 0.5 to 1.0, -0.1 to 0.5, 0.5 to 1.5, Double.NaN to 0.5, 0.5 to Double.NaN,
            Double.POSITIVE_INFINITY to 0.5)) {
            assertEquals(null, YtGuard.tapPoint(x, y, 1080, 2400), "$x $y")
        }
        assertEquals(null, YtGuard.tapPoint(0.5, 0.5, 0, 2400))
        assertEquals(null, YtGuard.tapPoint(0.5, 0.5, 1080, 0))
    }

    @Test
    fun 앱은_주_틀의_것만_켜져_있을_때_진짜_터치로_누른다() {
        val bridge = source("PageBridge.kt")
        assertTrue("YtGuard.SKIP -> onYtSkip(o, origin, isMainFrame)" in bridge)
        val onSkip = bridge.substringAfter("private fun onYtSkip(").substringBefore("\n    }\n")
        assertTrue("if (!isMainFrame || !YtGuard.isYouTubeOrigin(origin.scheme, origin.host, origin.port)) return" in onSkip, onSkip)
        assertTrue("if (!events.ytGuardWanted(origin.host.orEmpty())) return" in onSkip, onSkip)
        assertTrue("YtGuard.SKIP_MIN_GAP_MS" in onSkip, onSkip)
        // 광고가 나오는 동안은 주 틀의 것만 들고, 쪽이 바뀌면 잊는다.
        assertTrue("YtGuard.AD -> onYtAd(o.optBoolean(\"on\"), origin, isMainFrame)" in bridge)
        assertTrue("ytAdShowing = false" in bridge.substringAfter("private fun forgetPlaying() {").substringBefore("\n    }\n"))
        // 화면 — 뷰의 픽셀로 바꿔 그 탭의 WebView(전체 화면 영상이면 그 화면)를 누른다. 앱이 뒤에 있어도.
        val activity = source("BrowserActivity.kt")
        val onSkipUi = activity.substringAfter("override fun onYtSkip(tab: Tab, x: Double, y: Double) {").substringBefore("\n        }\n")
        assertTrue("YtGuard.tapPoint(x, y, target.width, target.height)" in onSkipUi, onSkipUi)
        assertTrue("page.tap(point.first, point.second)" in onSkipUi && "customView?.takeIf { tab == current }" in onSkipUi, onSkipUi)
        assertFalse("foreground &&" in onSkipUi || "if (!foreground)" in onSkipUi, "앞에 있을 때만으로 묶지 않는다")
        // 누르기 — 손가락 하나의 터치스크린 사건, 진짜 손가락이 닿아 있으면 넣지 않는다, 투명하게 남은 탭에도(막는 dispatchTouchEvent 를 비켜).
        val view = source("PullToRefresh.kt")
        val tap = view.substringAfter("fun tap(x: Float, y: Float): Boolean {").substringBefore("\n    }\n")
        assertTrue("if (touching || !isAttachedToWindow) return false" in tap && "super.dispatchTouchEvent(it)" in tap, tap)
        assertTrue("MotionEvent.TOOL_TYPE_FINGER" in view && "InputDevice.SOURCE_TOUCHSCREEN" in view)
    }

    @Test
    fun 스크립트에_역슬래시와_달러_기호가_없다() {
        // 코틀린의 날 글자열·셸이 먹지 않게(PageScripts 의 선례). 코틀린의 글자열 틀도 끼어들지 않는다.
        assertFalse(Char(BACKSLASH) in script)
        assertFalse(Char(DOLLAR) in script)
    }

    @Test
    fun 표시_글은_짧고_CSS_글자열을_깨뜨리지_않는다() {
        val label = strings().getValue("ytguard_label")
        assertTrue(label.isNotBlank() && label.length <= 12, label)
        // 스크립트가 빼는 글자가 처음부터 없다(빼면 표시가 바뀐다).
        assertTrue(label.none { it.code < 32 || it.code == BACKSLASH || it in "\"'<>{};" }, label)
    }

    @Test
    fun 통로가_같은_세계에_유튜브_출처로만_건다() {
        val bridge = source("PageBridge.kt")
        val install = bridge.substringAfter("fun install(view: WebView)").substringBefore("\n    }\n")
        // 통로 뒤에, 같은 세계(world)에, 유튜브 규칙으로.
        val at = install.indexOf("YtGuard.SCRIPT.replace(BridgeScripts.OBJECT_PLACEHOLDER, OBJECT)")
        assertTrue(at > install.indexOf("BridgeScripts.BRIDGE.replace"), "통로 뒤에 건다")
        assertTrue("YtGuard.ORIGIN_RULES,\n                world," in install.substring(at))
        // 쪽의 세계로 물러서지 않는다 — 실행 세계를 모르는 WebView 면 걸지 않는다.
        val all = File(DIR).listFiles { f -> f.name.endsWith(".kt") }.orEmpty().joinToString("\n") { source(it.name) }
        assertFalse(Regex("addDocumentStartJavaScript[(][^)]*YtGuard").containsMatchIn(all))
        // 화면이 광고 차단의 켜기와 그 사이트의 끄기로 답하고, 설정에서 돌아오면 다시 알린다.
        val activity = source("BrowserActivity.kt")
        assertTrue("YtGuard.wanted(it.enabled, topHost, it::isAllowed)" in activity)
        assertTrue("it.bridge?.refreshYtGuard()" in activity.substringAfter("override fun onResume()").substringBefore("override fun onPause()"))
    }

    /** 소스의 글 — 이 저장소는 CRLF 와 LF 파일이 섞여 있어(PageBridge 는 CRLF) 줄 끝을 LF 로 맞춰 읽는다. */
    private fun source(name: String): String = File("$DIR/$name").readText().replace("\r\n", "\n")

    private fun strings(): Map<String, String> {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/values/strings_ytguard.xml")).documentElement
        val nodes = root.getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as Element }.associate { it.getAttribute("name") to it.textContent }
    }

    private companion object {
        const val DIR = "src/main/kotlin/io/github/donggi/iroirobrowser/browser"
        const val BACKSLASH = 92
        const val DOLLAR = 36
    }
}
