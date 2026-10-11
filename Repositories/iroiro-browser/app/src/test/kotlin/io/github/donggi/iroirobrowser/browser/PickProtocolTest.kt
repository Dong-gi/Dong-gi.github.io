package io.github.donggi.iroirobrowser.browser

import io.github.donggi.iroirobrowser.core.adblock.HideSelectors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 요소 고르기의 통로 약속 — 고르는 스크립트(BridgeScripts.BRIDGE)는 JVM 에서 돌릴 수 없어, 스크립트의 글에 약속한 이름이 그대로 있는지와
 * 스크립트가 보낼 꼴(JSON.stringify → org.json 이 읽은 나무 — 정수는 Integer, 소수는 Double)을 앱이 받아들이는지·어긋난 것을 버리는지 본다.
 */
class PickProtocolTest {

    private val script = BridgeScripts.BRIDGE

    private fun node(
        t: Any?,
        i: Any? = "",
        c: Any? = emptyList<String>(),
        n: Any? = 1,
        k: Any? = 1,
        a: Any? = emptyList<Any>(),
    ) = mapOf("t" to t, "i" to i, "c" to c, "n" to n, "k" to k, "a" to a)

    private fun info(nodes: Any?, rect: Any? = listOf(0.1, 0.2, 0.6, 0.4)) = mapOf(
        "op" to PickProtocol.INFO, "id" to 7, "frame" to "f0", "nodes" to nodes, "rect" to rect, "w" to 320, "h" to 50,
        "up" to true, "down" to false,
    )

    @Test
    fun 스크립트가_약속한_이름을_그대로_쓴다() {
        listOf(PickProtocol.START, PickProtocol.AT, PickProtocol.WIDER, PickProtocol.NARROWER, PickProtocol.SCROLL, PickProtocol.CHECK, PickProtocol.STOP)
            .forEach { assertTrue("case '$it':" in script, it) }
        listOf(PickProtocol.INFO, PickProtocol.RECT, PickProtocol.CHECKED, PickProtocol.NONE, PickProtocol.GONE)
            .forEach { assertTrue("pickSend('$it'" in script, it) }
        PickProtocol.APP_KEYS.forEach { assertTrue("m.$it" in script, it) }
        PickProtocol.SCRIPT_KEYS.forEach { assertTrue(Regex("[{ ,]$it: ").containsMatchIn(script), it) }
        HideSelectors.TEST_ATTRIBUTES.forEach { assertTrue("'$it'" in script, it) }
        // 받아들이는 한도가 같다.
        assertTrue("chain.length < ${PickProtocol.MAX_CHAIN}" in script)
        assertTrue("o.c.length < ${PickProtocol.MAX_CLASSES}" in script)
        assertTrue("length <= ${PickProtocol.MAX_NAME}" in script)
        // 고르기는 주 틀에서만 시작하고, 페이지의 DOM 을 바꾸지 않는다(테두리는 앱의 덮개가 그린다).
        assertTrue("case 'pick-start': if (isTop)" in script)
        val picker = script.substringAfter("var pk = null;").substringBefore("ch.addEventListener('message'")
        listOf("appendChild", "createElement", "insertBefore", ".style.", "innerHTML", "setAttribute", "fetch(").forEach {
            assertFalse(it in picker, it)
        }
    }

    @Test
    fun 고른_요소를_읽는다() {
        val m = info(
            listOf(
                node("div", "top-ad", listOf("ad-slot", "wide"), 2, 3, listOf(listOf("data-testid", "banner"), listOf("onclick", "x"))),
                node("main", "content"),
                node("body"),
            ),
        )
        val i = assertNotNull(PickProtocol.info(m))
        assertEquals(listOf("div", "main", "body"), i.chain.map { it.tag })
        assertEquals("top-ad", i.target.id)
        assertEquals(listOf("ad-slot", "wide"), i.target.classes)
        assertEquals(2, i.target.nthOfType)
        assertEquals(3, i.target.ofType)
        // 목록에 없는 속성은 버린다.
        assertEquals(listOf("data-testid" to "banner"), i.target.attributes)
        assertEquals(320, i.cssWidth)
        assertEquals(50, i.cssHeight)
        assertTrue(i.canWiden)
        assertFalse(i.canNarrow)
        assertEquals(0.5 * 0.2, i.box.cover(), 1e-9)
        // 고른 것에서 코어의 규칙으로 후보를 짓는다.
        assertEquals("#top-ad", HideSelectors.candidates(i.chain).first())
    }

    @Test
    fun 꼴이_어긋난_알림은_버린다() {
        assertNull(PickProtocol.info(info(emptyList<Any>())))
        assertNull(PickProtocol.info(info(listOf(node("")))))
        // 페이지가 localName 을 덮어 글이 아닌 것이 왔다.
        assertNull(PickProtocol.info(info(listOf(node(5)))))
        assertNull(PickProtocol.info(info(listOf(node("div", n = 3, k = 2)))))
        assertNull(PickProtocol.info(info(listOf(node("div", n = 0)))))
        assertNull(PickProtocol.info(info(listOf(node("div", n = "1")))))
        assertNull(PickProtocol.info(info(listOf(node("div")), rect = listOf(0.1, 0.2, 0.6))))
        assertNull(PickProtocol.info(info(listOf(node("div")), rect = listOf(0.1, Double.NaN, 0.6, 0.4))))
        assertNull(PickProtocol.info(info(listOf(node("div")), rect = listOf(0.6, 0.2, 0.1, 0.4))))
        assertNull(PickProtocol.info(info(List(PickProtocol.MAX_CHAIN + 1) { node("div") })))
        assertNull(PickProtocol.info(mapOf("nodes" to listOf(node("div")))))
        // 한 조상이라도 어긋나면 통째로 버린다(경로가 틀린 선택자를 짓지 않게).
        assertNull(PickProtocol.info(info(listOf(node("div"), node(null), node("body")))))
    }

    @Test
    fun 이름의_한도() {
        val long = "a".repeat(PickProtocol.MAX_NAME + 1)
        val i = assertNotNull(PickProtocol.info(info(listOf(node("div", long, listOf(long, "ok", 7) + List(20) { "c$it" })))))
        assertEquals("", i.target.id)
        assertEquals(listOf("ok") + List(15) { "c$it" }, i.target.classes)
    }

    @Test
    fun 잰_결과를_읽는다() {
        val res = listOf(mapOf("n" to 3, "hit" to true), mapOf("n" to 1, "hit" to true), mapOf("n" to -1, "hit" to false))
        val checks = assertNotNull(PickProtocol.checks(res))
        assertEquals(listOf(3, 1, -1), checks.map { it.count })
        assertEquals("b", HideSelectors.choose(listOf("a", "b", "c"), checks))
        assertNull(PickProtocol.checks(listOf(mapOf("hit" to true))))
        assertNull(PickProtocol.checks("x"))
    }

    @Test
    fun 덮는_몫() {
        assertEquals(1.0, PickProtocol.Box(0.0, 0.0, 1.0, 1.0).cover(), 1e-9)
        // 화면보다 큰 틀·반쯤 화면 밖·화면 아래.
        assertEquals(1.0, PickProtocol.Box(-0.5, -2.0, 1.5, 3.0).cover(), 1e-9)
        assertEquals(0.25, PickProtocol.Box(0.5, 0.5, 1.5, 1.5).cover(), 1e-9)
        assertEquals(0.0, PickProtocol.Box(0.2, 1.2, 0.8, 1.6).cover(), 1e-9)
        assertTrue(HideSelectors.isBroad(PickProtocol.Box(0.0, 0.1, 1.0, 0.9).cover()))
        assertFalse(HideSelectors.isBroad(PickProtocol.Box(0.0, 0.0, 1.0, 0.5).cover()))
        assertNotNull(PickProtocol.box(listOf(0, 0, 1, 1)))
    }

    @Test
    fun 자리만_바뀐다() {
        val i = assertNotNull(PickProtocol.info(info(listOf(node("div")))))
        val moved = i.moved(PickProtocol.Box(0.0, 0.0, 0.5, 0.5))
        assertEquals(0.25, moved.box.cover(), 1e-9)
        assertEquals(i.chain, moved.chain)
        assertEquals(i.cssWidth, moved.cssWidth)
    }
}
