package io.github.donggi.iroirobrowser.browser

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * WebRTC 가드([PageScripts.WEBRTC_GUARD])의 꼴 — 쪽이 바꿀 수 있는 내장 함수를 나중에 부르지 않는가(쪽이
 * `Object.getOwnPropertyNames`·`RegExp.prototype.test` 를 바꾼 뒤 만든 빈 틀에 `RTCPeerConnection` 이 남는다 — 에뮬레이터에서 재현). 실제로 지우는지는 기기에서 본다
 * (CLAUDE.md '쪽의 결정·UA·DRM' 과 실험 webrtc2).
 */
class WebRtcGuardTest {

    private val script = PageScripts.WEBRTC_GUARD

    /** 쪽보다 먼저 도는 앞부분(붙잡기)과 그 뒤(틀이 생길 때 도는 것). */
    private val captures = script.substringBefore("var hooked = new WS();")
    private val body = script.substringAfter("var hooked = new WS();")

    @Test
    fun 쓸_내장_함수는_처음에_붙잡는다() {
        for (c in listOf(
            "var apply = Reflect.apply;",
            "ownNames = Object.getOwnPropertyNames",
            "getDesc = Object.getOwnPropertyDescriptor",
            "defProp = Object.defineProperty",
            "wsHas = WS.prototype.has, wsAdd = WS.prototype.add",
            "var listen = EventTarget.prototype.addEventListener;",
            "observe = MO.prototype.observe",
            "var lengthOf = lenDesc && lenDesc.get;",
        )) {
            assertTrue(c in captures, c)
        }
    }

    @Test
    fun 그_뒤로는_쪽이_바꿀_수_있는_것을_부르지_않는다() {
        // 쪽이 미리 바꿔 두면 새 틀의 RTC 가 남거나(앞의 셋) 훑기가 끝나지 않는다(pop).
        for (bad in listOf(".forEach(", ".push(", ".pop(", ".apply(", ".call(", ".test(", ".has(", ".add(", "Object.", "Reflect.", "WeakSet",
            "RegExp", "new RegExp", "/^(")) {
            assertFalse(bad in body, bad)
        }
        // 붙잡은 것으로만 부른다.
        assertTrue("apply(wsHas, hooked, [doc])" in body)
        assertTrue("apply(wsAdd, hooked, [doc])" in body)
        assertTrue("apply(ownNames, null, [w])" in body)
        assertTrue("apply(lengthOf, w, [])" in body)
        assertTrue("apply(listen, w.document, ['load', sweep, true])" in body)
        assertTrue("apply(observe, new MO(sweep), [w.document, o])" in body)
        // 감싼 메서드도 붙잡은 apply 로 원래 것을 부른다.
        assertTrue("return apply(f, this, arguments);" in body)
        assertTrue("apply(set, this, [v]);" in body)
        assertTrue("return apply(get, this, []);" in body)
    }

    @Test
    fun 속성_설명은_원형이_없는_객체로_짓는다() {
        // 쪽이 Object.prototype 에 get·set 따위를 더해도 설명에 섞이지 않게.
        assertTrue("function bare() { return apply(create, null, [null]); }" in body)
        assertTrue("var d = bare();" in body)
        assertTrue(Regex("var nd = bare\\(\\);").findAll(body).count() == 3)
        assertTrue("var o = bare();" in body)
        // 객체 글자({ value: …}·{ get: …})로 설명을 짓지 않는다.
        assertFalse("{ value:" in body)
        assertFalse("{ get:" in body)
        assertFalse("{ childList:" in body)
    }

    @Test
    fun 틀_나무는_연결_목록과_붙잡은_틀_수로_훑는다() {
        assertTrue("var top = { w: root, next: null };" in body)
        assertTrue("top = { w: w[i], next: top };" in body)
        assertTrue("var n = childCount(w);" in body)
    }

    @Test
    fun 갈고리를_거는_길목은_그대로다() {
        // 실험 webrtc2 의 15길을 막은 목록 — 꼴을 바꾸며 빠뜨리지 않았는가.
        for (name in listOf("'appendChild'", "'insertBefore'", "'replaceChild'", "'insertAdjacentHTML'", "'setHTMLUnsafe'", "'moveBefore'",
            "'write'", "'writeln'", "'execCommand'", "'insertNode'", "'surroundContents'", "'innerHTML'", "'outerHTML'", "'HTMLIFrameElement'",
            "'HTMLFrameElement'", "'HTMLObjectElement'", "'contentWindow'", "'contentDocument'")) {
            assertTrue(name in script, name)
        }
        assertTrue("w.open = function ()" in body)
        assertTrue("o.childList = true; o.subtree = true;" in body)
        assertTrue(script.trimEnd().endsWith("sweep();\n})();"))
    }

    @Test
    fun 날_글자열이_먹는_글자를_쓰지_않는다() {
        assertFalse('$' in script)
        assertFalse('\\' in script)
    }
}
