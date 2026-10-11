package io.github.donggi.iroirobrowser.browser

import io.github.donggi.iroirobrowser.core.adblock.HideSelectors
import io.github.donggi.iroirobrowser.core.adblock.PickedNode
import kotlin.math.max
import kotlin.math.min

/**
 * 요소 고르기의 통로 약속 — 앱([PageBridge]·[ElementPicker])과 고르는 스크립트([BridgeScripts.BRIDGE] 의 '요소 고르기')가 주고받는 알림의
 * 이름과 꼴. 스크립트는 JVM 에서 돌릴 수 없어, 시험(PickProtocolTest)이 이 이름들이 스크립트에 그대로 있는지 보고 스크립트가 보내는 꼴
 * (JSON 을 읽은 나무 — 맵·목록·수·글·참거짓)을 이 파일의 읽기가 받아들이는지 본다. 안드로이드 클래스를 쓰지 않는다(시험이 JVM 에서 돈다).
 *
 * - 앱 → 스크립트(그 탭의 **주 틀에만**, 알림마다 고르기 번호 `id`): [START] · [AT](`x`·`y`) · [WIDER] · [NARROWER] · [SCROLL](`x`·`y`·`dx`·
 *   `dy`) · [CHECK](`sels` — 선택자 후보) · [STOP].
 * - 스크립트 → 앱: [INFO](고른 요소 — [info]) · [RECT](자리가 바뀌었다 — `rect`) · [CHECKED](후보마다 잰 결과 — `res`) · [NONE](그 자리에 고를
 *   것이 없다) · [GONE](고른 요소가 페이지에서 사라졌다).
 *
 * **좌표는 보이는 영역(visual viewport)에 대한 비율**(0~1, 왼쪽 위가 0)이다. 앱은 WebView 의 픽셀을, 스크립트는 CSS 픽셀을 쓰는데 그 사이의
 * 배율(기기 밀도 × 페이지 확대)을 어느 쪽도 알 필요가 없다 — WebView 는 보이는 영역을 제 크기에 꼭 맞게 그린다.
 */
object PickProtocol {
    const val START = "pick-start"
    const val AT = "pick-at"
    const val WIDER = "pick-up"
    const val NARROWER = "pick-down"
    const val SCROLL = "pick-scroll"
    const val CHECK = "pick-check"
    const val STOP = "pick-stop"

    const val INFO = "pick-info"
    const val RECT = "pick-rect"
    const val CHECKED = "pick-checked"
    const val NONE = "pick-none"
    const val GONE = "pick-gone"

    /** 앱이 보내는 칸 — 스크립트는 `m.x` 처럼 읽는다. */
    val APP_KEYS = listOf("x", "y", "dx", "dy", "sels")

    /**
     * 스크립트가 보내는 칸 — 스크립트는 `{ rect: … }` 처럼 적는다. 고른 요소는 `nodes`·`rect`·`w`·`h`(CSS 픽셀 크기)·`up`·`down`(넓게·좁게를
     * 할 수 있는가), 요소 하나는 `t`(이름)·`i`(id)·`c`(클래스)·`n`(같은 이름의 형제 가운데 몇째)·`k`(그 형제 수)·`a`(시험용 속성 — `[이름, 값]`),
     * 잰 결과는 `res` 의 `n`(맞은 수)·`hit`(고른 요소가 그 안에 있는가).
     */
    val SCRIPT_KEYS = listOf("nodes", "rect", "w", "h", "up", "down", "res", "t", "i", "c", "n", "k", "a", "hit")

    /** 받아들이는 크기의 한도 — 스크립트도 같은 한도로 줄여 보낸다. */
    const val MAX_CHAIN = 64
    const val MAX_CLASSES = 16
    const val MAX_NAME = 100

    /** 보이는 영역에 대한 비율로 적은 네모 — 화면 밖에 걸친 요소면 0~1 밖으로 나간다. */
    class Box(val left: Double, val top: Double, val right: Double, val bottom: Double) {
        /** 보이는 영역을 덮는 몫(0~1) — 영역 밖으로 나간 부분은 뺀다. */
        fun cover(): Double {
            val w = (min(right, 1.0) - max(left, 0.0)).coerceAtLeast(0.0)
            val h = (min(bottom, 1.0) - max(top, 0.0)).coerceAtLeast(0.0)
            return w * h
        }
    }

    /** 고른 요소 — [chain] 은 고른 것이 맨 앞, `body` 가 맨 뒤. [cssWidth]·[cssHeight] 는 요소의 CSS 픽셀 크기(막대의 설명에 쓴다). */
    class Info(
        val chain: List<PickedNode>,
        val box: Box,
        val cssWidth: Int,
        val cssHeight: Int,
        val canWiden: Boolean,
        val canNarrow: Boolean,
    ) {
        val target: PickedNode get() = chain.first()

        /** 자리만 바뀐 것(스크롤·배치) — 나머지는 그대로. */
        fun moved(to: Box) = Info(chain, to, cssWidth, cssHeight, canWiden, canNarrow)
    }

    /** [INFO] 의 칸을 읽는다(JSON 을 읽은 나무). 꼴이 어긋나면 null — 그 알림은 버린다(그 자리에 고를 것이 없다고 본다). */
    fun info(m: Map<String, Any?>): Info? {
        val nodes = m["nodes"] as? List<*> ?: return null
        if (nodes.isEmpty() || nodes.size > MAX_CHAIN) return null
        val chain = nodes.map { node(it) ?: return null }
        val box = box(m["rect"]) ?: return null
        val w = number(m["w"]) ?: return null
        val h = number(m["h"]) ?: return null
        return Info(chain, box, w.toInt().coerceAtLeast(0), h.toInt().coerceAtLeast(0), m["up"] == true, m["down"] == true)
    }

    /** `rect` — `[왼쪽, 위, 오른쪽, 아래]`(유한한 수, 오른쪽·아래가 왼쪽·위보다 작지 않다). */
    fun box(v: Any?): Box? {
        val l = v as? List<*> ?: return null
        if (l.size != 4) return null
        val n = l.map { number(it) ?: return null }
        if (n[2] < n[0] || n[3] < n[1]) return null
        return Box(n[0], n[1], n[2], n[3])
    }

    /** `res` — 후보와 같은 차례의 `{n, hit}`. 하나라도 꼴이 어긋나면 null(아무것도 고르지 않는다). */
    fun checks(v: Any?): List<HideSelectors.Check>? {
        val l = v as? List<*> ?: return null
        return l.map { item ->
            val o = item as? Map<*, *> ?: return null
            HideSelectors.Check(number(o["n"])?.toInt() ?: return null, o["hit"] == true)
        }
    }

    /** 요소 하나 — `{t, i, c, n, k, a}`. 이름이 없거나 몇째·형제 수가 어긋나면 null. 너무 긴 이름·목록에 없는 속성은 버린다. */
    fun node(v: Any?): PickedNode? {
        val o = v as? Map<*, *> ?: return null
        val tag = (o["t"] as? String)?.takeIf { it.isNotEmpty() && it.length <= MAX_NAME } ?: return null
        val id = (o["i"] as? String)?.takeIf { it.length <= MAX_NAME }.orEmpty()
        val classes = (o["c"] as? List<*>).orEmpty().filterIsInstance<String>().filter { it.isNotEmpty() && it.length <= MAX_NAME }.take(MAX_CLASSES)
        val nth = number(o["n"])?.toInt() ?: return null
        val of = number(o["k"])?.toInt() ?: return null
        if (nth < 1 || of < nth) return null
        val attributes = (o["a"] as? List<*>).orEmpty().mapNotNull { pair ->
            val p = pair as? List<*> ?: return@mapNotNull null
            val name = p.getOrNull(0) as? String ?: return@mapNotNull null
            val value = p.getOrNull(1) as? String ?: return@mapNotNull null
            if (name in HideSelectors.TEST_ATTRIBUTES && value.length <= MAX_NAME) name to value else null
        }
        return PickedNode(tag, id, classes, nth, of, attributes)
    }

    /** 유한한 수만(JSON 의 수는 Int·Long·Double 로 온다). */
    private fun number(v: Any?): Double? = (v as? Number)?.toDouble()?.takeIf { it.isFinite() }
}
