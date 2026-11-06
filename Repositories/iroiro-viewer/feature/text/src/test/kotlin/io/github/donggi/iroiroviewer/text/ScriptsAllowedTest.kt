package io.github.donggi.iroiroviewer.text

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * '스크립트 실행' 단추는 **클립보드 막음을 걸 수 있는 WebView 에서만** 선다(`WebHost.scriptsAllowed` — 2026-10-16 보안 점검). 걸지 못하는
 * WebView 에서 켜면 쪽이 같은 폴더의 파일을 클립보드로 내보낼 수 있다(`core:webhost` 의 `ScriptPage.CLIPBOARD_GUARD`). 배선을 소스로 본다 —
 * 이 저장소의 관례.
 */
class ScriptsAllowedTest {

    @Test
    fun 스크립트_실행_단추는_막음을_걸_수_있을_때만_선다() {
        val screen = File("src/main/kotlin/io/github/donggi/iroiroviewer/text/TextViewerScreen.kt").readText()
        val actions = Regex("""\(runScripts to onRunScripts\)\.takeIf \{ ([^}]*) \}""").findAll(screen).map { it.groupValues[1] }.toList()
        assertEquals(3, actions.size, "'스크립트 실행' 을 다는 자리는 셋이다(너무 큼·만들지 못함·버린 것 알림): $actions")
        for (a in actions) assertTrue(a.startsWith("scriptsAllowed && "), a)
        assertTrue("val scriptsAllowed = remember { WebHost.scriptsAllowed() }" in screen)
    }
}
