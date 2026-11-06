package io.github.donggi.iroiroviewer.webhost

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 스크립트를 켠 쪽의 요청 가르기([ScriptPage]) — WebView 없이 답할 수 있는 몫. 기기에서는 시험 쪽(`probe.html`)으로 같은 것을 손으로
 * 확인했다(CLAUDE.md 의 '스크립트 실행' 절).
 */
class ScriptPageTest {

    @Test
    fun `서비스 워커가 받아 오는 스크립트를 알아본다`() {
        assertTrue(ScriptPage.isServiceWorkerFetch(mapOf("Service-Worker" to "script")))
        // 머리 이름은 대소문자를 가리지 않는다(HTTP).
        assertTrue(ScriptPage.isServiceWorkerFetch(mapOf("service-worker" to "script")))
        assertFalse(ScriptPage.isServiceWorkerFetch(mapOf("Accept" to "*/*")))
        assertFalse(ScriptPage.isServiceWorkerFetch(emptyMap()))
        assertFalse(ScriptPage.isServiceWorkerFetch(null))
    }

    /**
     * **클립보드 막음의 꼴**(2026-10-16 보안 점검). 쪽이 같은 폴더의 파일을 읽어 클립보드로 내보내지 못하게 하는 문서 시작 스크립트다 — 그
     * 동작은 기기에서 본다(JVM 에 자바스크립트 엔진이 없다). 여기서는 무엇을 막는지·쪽이 되돌릴 수 없게 걸었는지·틀마다 거는지를 글로 본다.
     */
    @Test
    fun `클립보드 막음은 세 길을 쪽보다 먼저 막는다`() {
        val js = ScriptPage.CLIPBOARD_GUARD
        // 코틀린 글자열(달러는 틀)과 셸이 먹지 않게.
        assertFalse('$' in js, "달러 기호")
        assertFalse(js.any { it.code == 92 }, "역슬래시")
        // 1. execCommand 의 copy·cut — 명령을 한 번만 글로 바꿔 그 글을 넘긴다.
        assertTrue("define(D, 'execCommand'" in js)
        assertTrue("function clipboardEvent(type) { return type === 'copy' || type === 'cut'; }" in js)
        assertTrue("var c = toText(command);" in js && "arguments[0] = c;" in js && "return false;" in js)
        // 2. navigator.clipboard 의 쓰기 둘.
        assertTrue("var CLIPBOARD = ['write', 'writeText'];" in js && "define(C, CLIPBOARD[i]" in js)
        // 3. copy·cut 사건 — 창의 포착 단계에서 멈추고, 그 사건의 clipboardData 는 null.
        assertTrue("w.addEventListener('copy', stop, true);" in js && "w.addEventListener('cut', stop, true);" in js)
        assertTrue("define(E, 'clipboardData'" in js && "return null;" in js)
    }

    @Test
    fun `쪽이 되돌리지 못하게 건다`() {
        val js = ScriptPage.CLIPBOARD_GUARD
        // 세 막음은 쓰기·지우기가 안 되는 속성(마지막 인자 true — writable·configurable 이 거짓).
        assertTrue(Regex("""define\(D, 'execCommand', data\(function execCommand[\s\S]*?\}, exec\.enumerable, true\)\)""").containsMatchIn(js))
        assertTrue(Regex("""define\(C, CLIPBOARD\[i\], data\([\s\S]*?\}, m\.enumerable, true\)\)""").containsMatchIn(js))
        assertTrue(Regex("""define\(E, 'clipboardData', accessor\([\s\S]*?\}, undefined, cd\.enumerable, true\)\)""").containsMatchIn(js))
        // 고리가 부르는 내장 함수는 쪽보다 먼저 쥔 사본이다 — 쪽이 나중에 바꿔도 바뀐 것을 부르지 않는다.
        val captured = listOf(
            "var apply = Reflect.apply;", "var create = Object.create;", "var define = Object.defineProperty;",
            "var describe = Object.getOwnPropertyDescriptor;", "var lowerCase = String.prototype.toLowerCase;",
            "var stopNow = Event.prototype.stopImmediatePropagation;", "var seenHas = WeakSet.prototype.has;", "var seenAdd = WeakSet.prototype.add;",
        )
        val body = js.substringAfter("function clipboardEvent(")
        for (c in captured) assertTrue(js.indexOf(c) in 0 until js.indexOf("function clipboardEvent("), c)
        // 쥔 뒤에는 그 이름을 다시 찾아 부르지 않는다(바꿔치기된 것을 부르게 된다).
        for (raw in listOf("Reflect.apply", ".forEach(", ".push(", ".pop(", "seen.has(", "seen.add(", "Object.defineProperty(", "e.stopImmediatePropagation(")) {
            assertFalse(raw in body, "쥔 사본 대신 $raw 를 부른다")
        }
        // 속성을 거는 설명 객체는 원형이 없다 — 쪽이 Object.prototype 에 심은 value·get 이 끼지 않게.
        assertTrue("var d = create(null);" in js)
    }

    @Test
    fun `틀마다 건다`() {
        val js = ScriptPage.CLIPBOARD_GUARD
        // 같은 출처의 빈 틀에는 문서 시작 스크립트가 들어가지 않는다 — 틀이 생길 수 있는 길마다 틀 나무를 훑는다(Web 의 WebRTC 가드 2판과 같은 목록).
        for (way in listOf(
            "'appendChild', 'insertBefore', 'replaceChild'", "'insertAdjacentHTML'", "'write', 'writeln'", "'insertNode', 'surroundContents'",
            "var MARKUP = ['innerHTML', 'outerHTML'];", "var FRAME_GETTERS = ['contentWindow', 'contentDocument'];",
            "w.document.addEventListener('load', sweep, true);", "new w.MutationObserver(sweep).observe(w.document, { childList: true, subtree: true });",
            "w.open = function",
        )) {
            assertTrue(way in js, way)
        }
        // 훑기는 창마다 한 번 — 막음(guard)과 갈고리(hook)를 함께.
        assertTrue("guard(w);\n          hook(w);" in js)
        // execCommand 의 다른 명령(insertHTML)도 틀을 만들 수 있다 — 부른 뒤에 훑는다.
        assertTrue("try { return apply(run, this, arguments); } finally { sweep(); }" in js)
        assertTrue(js.trimEnd().endsWith("sweep();\n})();"))
    }

    /** 응답 머리의 권한 정책 — 비동기 클립보드를 쪽과 그 아래 모든 틀에서 끈다(플랫폼이 막는 겹). */
    @Test
    fun `응답 머리가 비동기 클립보드를 끈다`() {
        assertEquals("clipboard-write=(), clipboard-read=()", ScriptPage.OFFLINE_HEADERS["Permissions-Policy"])
        assertEquals("connect-src 'self' data: blob:", ScriptPage.OFFLINE_HEADERS["Content-Security-Policy"])
    }

    /**
     * 배선 — 스크립트는 **막음을 건 뒤에만** 켠다. 걸지 못하면(문서 시작 스크립트를 모르는 WebView) 자바스크립트를 끈 채로 둔다. 거는 자리는 쪽을
     * 싣기 전(뷰를 만드는 곳 — 쪽은 `update` 에서 싣는다)이고, 규칙은 모든 출처(`*` — 쪽이 만든 data:·샌드박스 틀까지)다.
     */
    @Test
    fun `막음을 건 뒤에만 스크립트를 켠다`() {
        val view = File("src/main/kotlin/io/github/donggi/iroiroviewer/webhost/LockedWebView.kt").readText()
        val factory = view.substringAfter("factory = { context ->").substringBefore("update = { view ->")
        val gate = factory.indexOf("if (scripts && ScriptPage.installClipboardGuard(this)) {")
        assertTrue(gate >= 0, "막음을 거는 자리가 없다")
        assertEquals(1, Regex("""javaScriptEnabled = true""").findAll(view).count(), "스크립트를 켜는 자리는 하나다")
        assertTrue(factory.indexOf("settings.javaScriptEnabled = true") > gate, "막음보다 먼저 켠다")
        assertFalse("loadUrl" in factory || ".open(" in factory, "뷰를 만드는 자리에서 쪽을 싣는다")
        val page = File("src/main/kotlin/io/github/donggi/iroiroviewer/webhost/ScriptPage.kt").readText()
        assertTrue("WebViewCompat.addDocumentStartJavaScript(view, CLIPBOARD_GUARD, setOf(\"*\"))" in page)
        assertTrue("if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return false" in page)
    }

    @Test
    fun `쪽 전체를 다른 곳으로 바꾸는 요청만 거둔다`() {
        // 다른 경로·다른 호스트(경로 null)로 가는 쪽 전체의 요청 — 폼의 POST 가 그 길로 온다.
        assertTrue(ScriptPage.refusesNavigation(isForMainFrame = true, requestPath = "f/other.html", pagePath = "f/"))
        assertTrue(ScriptPage.refusesNavigation(isForMainFrame = true, requestPath = null, pagePath = "f/"))
        // 쪽 자신을 다시 읽는 것은 막지 않는다.
        assertFalse(ScriptPage.refusesNavigation(isForMainFrame = true, requestPath = "f/", pagePath = "f/"))
        // 쪽 안의 자원·프레임은 이 문이 아니다(폴더 안인지는 자원 공급기가 본다).
        assertFalse(ScriptPage.refusesNavigation(isForMainFrame = false, requestPath = "f/frame.html", pagePath = "f/"))
        assertFalse(ScriptPage.refusesNavigation(isForMainFrame = false, requestPath = null, pagePath = "f/"))
    }
}
