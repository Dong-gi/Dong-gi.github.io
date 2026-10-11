package io.github.donggi.iroiroviewer.editor

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 편집기 화면의 글로 된 약속 — 매니페스트·문구·엔진의 첫 쪽·어댑터를 글로 읽어 견준다(EngineKindsTest 와 같은 방식).
 *
 * - 설정이 바뀌어도 화면을 다시 만들지 않는다(글꼴 크기를 바꾸면 고친 것을 잃었다 — '경미 17').
 * - 문구 규칙(조사는 라틴 글자·숫자 뒤에도 붙인다)과 '다른 앱으로 열기' 의 한 벌(core:io).
 * - 앱과 어댑터 셋(rhwp·ONLYOFFICE·probe)이 같은 말을 쓴다 — 뒤로 가기·읽던 자리·열기 실패의 갈래·링크의 prompt.
 */
class EditorScreenFilesTest {

    private fun text(path: String) = File(path).readText()

    private val manifest = text("src/main/AndroidManifest.xml")
    private val strings = text("src/main/res/values/strings.xml")
    private val activity = text("src/main/kotlin/io/github/donggi/iroiroviewer/editor/EditorActivity.kt")
    private val rhwp = text("src/main/assets/engines/rhwp/iroiro-rhwp.js")
    private val onlyoffice = text("src/main/assets/engines/onlyoffice/iroiro-onlyoffice.js")
    private val inner = text("src/main/assets/engines/onlyoffice/iroiro-oo-inner.js")
    private val probe = text("src/main/assets/engines/probe/probe.js")
    private val host = text("src/main/assets/engines/onlyoffice/host.html")

    /** 조사를 띄운 자리 — 라틴 글자·숫자·닫는 괄호 뒤의 빈칸 + 조사 하나(뒤에 한글이 이어지면 조사가 아니다). */
    private val spacedParticle = Regex("[A-Za-z0-9)\\]] (은|는|이|가|을|를|의|에|에서|로|으로|와|과|도|만|까지|부터|이고|입니다)(?![가-힣])")

    @Test
    fun theEditorKeepsItselfAcrossConfigurationChanges() {
        val activityTag = manifest.substringAfter("android:name=\".EditorActivity\"").substringBefore(">")
        val changes = Regex("android:configChanges=\"([^\"]+)\"").find(activityTag)?.groupValues?.get(1)?.split('|')?.toSet()
        val required = setOf(
            "orientation", "screenSize", "smallestScreenSize", "screenLayout", "uiMode", "density", "fontScale", "fontWeightAdjustment",
            "locale", "layoutDirection", "keyboard", "keyboardHidden", "navigation", "touchscreen", "mcc", "mnc", "colorMode", "grammaticalGender",
        )
        assertEquals(emptySet(), required - (changes ?: emptySet()), "configChanges 에 빠진 것")
    }

    @Test
    fun editorStringsFollowTheWordingRules() {
        val values = Regex("<string name=\"([^\"]+)\">([^<]*)</string>").findAll(strings).associate { it.groupValues[1] to it.groupValues[2] }
        assertTrue(values.size > 20, "문구를 읽지 못했다")
        for ((name, value) in values) assertFalse(spacedParticle.containsMatchIn(value), "$name: 조사를 띄웠다 — $value")
        assertEquals("이 기기의 WebView가 필요한 기능을 지원하지 않습니다. Android System WebView를 업데이트하세요.", values["editor_error_webview"])
        // 쪽의 alert·confirm 의 확인 단추 — 기기 언어를 따르지 않는다.
        assertEquals("확인", values["editor_ok"])
        // '다른 앱으로 열기' 와 그 고르는 창의 이름은 core:io 의 한 벌을 쓴다.
        assertFalse(values.keys.any { it.startsWith("editor_open_with") }, "편집기 모듈에 따로 둔 '다른 앱으로 열기' 문구")
        assertTrue("IoR.string.io_open_with_no_app" in activity && "IoR.string.io_open_with_any" in activity)
    }

    @Test
    fun theOnlyofficeAboutPanelFollowsTheWordingRules() {
        val block = inner.substringAfter("const LICENSES = [").substringBefore("panel.appendChild(box);")
        val literals = Regex("'([^'\\n]*[가-힣][^'\\n]*)'").findAll(block).map { it.groupValues[1] }.toList()
        assertTrue(literals.size >= 10, "(i) 칸의 글을 읽지 못했다: $literals")
        for (s in literals) assertFalse(spacedParticle.containsMatchIn(s), "조사를 띄웠다 — $s")
    }

    @Test
    fun theOnlyofficePageIsNamedInKorean() {
        assertTrue("<title>편집기</title>" in host)
        // 예전의 따로 된 앱 이름('Editor 앱')이 어댑터 머리에 남지 않게.
        for ((name, src) in listOf("rhwp" to rhwp, "onlyoffice" to onlyoffice)) {
            assertFalse(src.lineSequence().take(3).any { "Editor 앱" in it }, "$name 머리 주석")
        }
    }

    @Test
    fun everyAdapterAnswersBackAndWhere() {
        for ((name, src) in listOf("rhwp" to rhwp, "onlyoffice" to onlyoffice, "probe" to probe)) {
            assertTrue(Regex("op === 'where'").containsMatchIn(src) && "op: 'position'" in src, "$name 이 읽던 자리에 답하지 않는다")
            assertTrue(Regex("op === 'back'").containsMatchIn(src) && "op: 'back-result'" in src, "$name 이 뒤로 가기에 답하지 않는다")
        }
    }

    @Test
    fun openFailuresCarryAReason() {
        // 갈래가 없으면 앱이 '그 밖' 으로 읽어 암호를 그만둔 문서에도 '다시 열기' 대신 '다른 앱으로 열기' 를 단다.
        assertTrue("send({ op: 'open-failed', reason })" in rhwp)
        assertTrue("send({ op: 'open-failed', reason: reason || 'other' })" in onlyoffice)
        assertTrue("'cancelled'" in rhwp && "'cancelled'" in onlyoffice)
        assertFalse("{ op: 'open-failed' }" in rhwp || "{ op: 'open-failed' }" in onlyoffice || "{ op: 'open-failed' }" in probe)
    }

    @Test
    fun bothEnginesAskTheAppWithTheSameLinkPrompt() {
        val app = Regex("LINK_PROMPT = \"([^\"]+)\"").find(activity)?.groupValues?.get(1)
        assertEquals("iroiro:open-link", app)
        for ((name, src) in listOf("rhwp" to rhwp, "onlyoffice" to onlyoffice)) {
            assertEquals(app, Regex("const LINK_PROMPT = '([^']+)'").find(src)?.groupValues?.get(1), name)
        }
        // ONLYOFFICE 의 링크는 틀 안의 window.open 에서 온다 — 틀 쪽이 바깥 쪽에 넘긴다.
        assertTrue("outer.openLink(url.href)" in inner && "openLink }" in onlyoffice)
    }

    @Test
    fun theOnlyofficeAdapterDoesNotEchoTheAppsRefusal() {
        // 앱이 받기를 거절하면(save-failed) 앱은 그 자리에서 까닭을 이미 띄웠다. 쪽이 그것을 save-error·transfer-error 로 되알리면 앱이 일반
        // 실패 창을 그 위에 하나 더 띄워 까닭이 가려졌다(2026-10-10 기기 — 세션을 지운 뒤의 저장). rhwp 는 같은 것을 AbortError 로 가른다.
        val transfer = onlyoffice.substringAfter("async function transferNow").substringBefore("\n  }\n")
        assertEquals(3, Regex("""throw refused\(""").findAll(transfer).count(), "앱이 false 로 답한 세 자리는 거절로 던진다")
        assertFalse("throw new Error('앱이 받" in transfer || "throw new Error('앱이 덩이" in transfer)
        assertEquals(1, Regex("""op: 'save-error'""").findAll(onlyoffice).count())
        assertTrue("if (!appKnows(e)) send({ op: 'save-error' });" in onlyoffice)
        assertTrue("if (!appKnows(e)) send({ op: 'transfer-error', purpose: 'export' });" in onlyoffice)
        assertTrue("if (!appKnows(e)) send({ op: 'transfer-error', purpose: exportName ? 'export' : 'print' });" in onlyoffice)
        assertTrue("if (isAbort(e)) return e;" in rhwp, "rhwp 는 앱이 거절한 것(AbortError)을 save-error 로 되알리지 않는다")
    }
}
