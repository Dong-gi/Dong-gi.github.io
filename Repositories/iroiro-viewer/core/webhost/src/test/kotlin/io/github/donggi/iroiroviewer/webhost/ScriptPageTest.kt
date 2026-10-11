package io.github.donggi.iroiroviewer.webhost

import kotlin.test.Test
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
