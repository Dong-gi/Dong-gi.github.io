package io.github.donggi.iroirobrowser.core.adblock

import java.util.Locale

/**
 * 요소 고르기(페이지에서 거슬리는 부분을 골라 숨긴다)가 넘겨받은 요소 하나 — 고른 요소와 그 조상들이 차례로 온다(고른 것이 맨 앞, `body` 가
 * 맨 뒤 — `html` 은 넣지 않는다). 고르는 스크립트가 DOM 에서 읽은 것이고 **이름은 페이지가 정한 글**이라 그대로 CSS 에 넣지 않는다
 * ([HideSelectors.escape]).
 *
 * @property tag 요소의 이름(`localName` — SVG 의 `foreignObject` 처럼 대소문자가 뜻이 있을 수 있어 그대로 둔다)
 * @property nthOfType 같은 이름의 형제 가운데 몇째인가(1 부터 — CSS 의 `:nth-of-type`)
 * @property ofType 같은 이름의 형제 수(자기 포함) — 1 이면 `:nth-of-type` 이 필요 없다
 * @property attributes 시험용 속성([HideSelectors.TEST_ATTRIBUTES] — `data-testid` 등)의 이름과 값
 */
class PickedNode(
    val tag: String,
    val id: String = "",
    val classes: List<String> = emptyList(),
    val nthOfType: Int = 1,
    val ofType: Int = 1,
    val attributes: List<Pair<String, String>> = emptyList(),
)

/**
 * 고른 요소를 다시 찾을 CSS 선택자를 짓는다 — **다음에 그 사이트를 다시 열어도 같은 요소를 가리키게.**
 *
 * - 안정적인 이름을 먼저 쓴다: id → 시험용 속성(`data-testid` — CSS-in-JS 로 클래스가 모두 무작위인 사이트의 거의 유일한 손잡이) → 클래스.
 * - 버리는 이름: **해시처럼 보이는 무작위 이름**([isRandomName] — CSS-in-JS·CSS 모듈·원자 CSS·세션마다 매기는 id), 상태를 뜻하는 클래스
 *   ([isStateName] — `active`·`is-open` — 다시 열면 없을 수 있다), 꼴을 뜻하는 흔한 클래스([isUtility] — `row`·`mt-3`·`md:flex` — 사이트 전체에
 *   거는 규칙이라 홀로 쓰면 다른 페이지의 엉뚱한 것까지 숨긴다).
 * - 이름으로 안 되면 **이름이 있는 가까운 조상에서** `>` 와 `:nth-of-type` 으로 내려가는 경로 — 가까운 조상부터 짓는다(경로 줄이기). 마지막은
 *   `body` 에서. 경로의 각 걸음에도 안정적인 클래스를 붙인다 — 구조가 바뀌어 그 자리에 다른 것이 오면 맞지 않게(엉뚱한 것을 숨기기보다 낫다).
 * - 짓기만 한다. 그 선택자가 지금 페이지에서 **고른 요소 하나만** 가리키는지는 고르는 스크립트가 재고, [choose] 가 그 결과로 고른다.
 *
 * 다 지은 선택자는 [isSafeSelector] 를 지난다 — 페이지가 정한 이름이 규칙을 벗어나 다른 것을 숨기는 CSS 가 되지 않게.
 */
object HideSelectors {

    /** 스크립트가 잰 결과 하나 — 지금 페이지에서 맞은 수(선택자가 잘못이면 -1)와 고른 요소가 그 안에 있는가. */
    class Check(val count: Int, val hit: Boolean)

    /** 보이는 영역의 이만큼 넘게 덮는 요소는 숨기기 전에 한 번 더 묻는다 — 페이지 전체를 감싼 틀이거나 화면을 덮는 창이다. */
    const val BROAD_COVER = 0.7

    /** 선택자 하나의 길이 한도(저장 파일·주입하는 CSS). */
    const val MAX_SELECTOR = 1000

    /** 이보다 긴 이름은 쓰지 않는다 — 사람이 지은 이름이 아니다. */
    const val MAX_NAME = 64

    /** id 처럼 쓰는 시험용 속성 — 앞의 것이 먼저. 값을 따옴표 없이 식별자로 적는다([escape]). */
    val TEST_ATTRIBUTES = listOf("data-testid", "data-test-id", "data-test", "data-qa", "data-cy")

    private const val MAX_CLASSES = 3
    private const val MAX_STEP_CLASSES = 2
    private const val MAX_ANCHORS = 3
    private const val MAX_PATH = 32

    /** 페이지 전체라 고르지 않는 것. */
    private val WHOLE_PAGE = setOf("html", "body", "head")

    /** 고를 수 있는 요소인가 — `html`·`body`·`head` 는 페이지 전체다. */
    fun isPickable(node: PickedNode): Boolean = node.tag.isNotEmpty() && node.tag.lowercase(Locale.ROOT) !in WHOLE_PAGE

    /** 화면(보이는 영역)의 [cover] 몫(0~1)을 덮는다 — 숨기기 전에 한 번 더 묻는다. */
    fun isBroad(cover: Double): Boolean = cover >= BROAD_COVER

    // ── 이름 가리기 ─────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * 도구가 지어 붙인 무작위 이름인가 — 다시 열면(또는 사이트가 고치면) 바뀐다. 이름 통째로는 알려진 꼴([GENERATED] — emotion·styled-components·
     * React Native Web·Meta 의 원자 CSS·Angular·Ember·React 의 useId …)을, 조각(`-`·`_`·`:`·`.`·`/` 로 가른다)마다는 해시의 생김새를 본다
     * ([looksHashed]).
     */
    fun isRandomName(name: String): Boolean {
        if (name.isEmpty() || name.length > MAX_NAME) return true
        if (GENERATED.any { it.matches(name) }) return true
        return name.split('-', '_', ':', '.', '/').any { looksHashed(it) }
    }

    /** 상태를 뜻하는 클래스인가 — 고른 때에는 붙어 있어도 다시 열면 없을 수 있다(`active`·`is-open`·`ng-touched`). */
    fun isStateName(name: String): Boolean {
        val n = name.lowercase(Locale.ROOT)
        return n in STATE || STATE_PREFIXES.any { n.startsWith(it) }
    }

    /** 꼴을 뜻하는 흔한 클래스인가 — 페이지마다 수십 개라 홀로 쓰면 다른 페이지의 엉뚱한 것까지 숨긴다(`row`·`mt-3`·`col-md-6`·`md:flex`). */
    fun isUtility(name: String): Boolean {
        val n = name.lowercase(Locale.ROOT)
        return n in UTILITY || ':' in n || '[' in n || '/' in n || UTILITY_SHAPE.matches(n)
    }

    /**
     * 이름 조각 하나가 해시처럼 보이는가 — 글자와 숫자가 자주 바뀌거나(`x1n2onr6`·`3xYz9`·`a3f9c2e1`), 숫자가 넷 넘게 붙거나(`ember1234`),
     * 대문자 곁에 홀로 선 소문자가 셋 넘거나(`jZHkUe`), 홀소리가 없다(`rnnxgb`). 한 번 바뀌는 것(`top100`·`mp4player`)·광고 칸의 크기
     * (`ad300x250`)·영어 아닌 글자는 이름으로 본다.
     */
    private fun looksHashed(s: String): Boolean {
        if (s.length < 5 || s.any { it.code >= 0x80 } || SIZE.matches(s)) return false
        var digits = 0
        var letters = 0
        var flips = 0
        var previous = 0
        for (c in s) {
            val kind = when (c) {
                in '0'..'9' -> 2
                in 'a'..'z', in 'A'..'Z' -> 1
                else -> 0
            }
            if (kind == 2) digits++ else if (kind == 1) letters++
            if (kind != 0 && previous != 0 && kind != previous) flips++
            if (kind != 0) previous = kind
        }
        if (digits > 0 && letters > 0) return flips >= 3 || (flips == 2 && digits >= 2) || digits >= 4
        if (digits > 0) return false
        if (loneLowercase(s) >= 3) return true
        return s.length >= 6 && s.all { it in 'a'..'z' || it in 'A'..'Z' } && s.none { it.lowercaseChar() in VOWELS }
    }

    /** 대문자 곁에 홀로 선 소문자의 수 — `jZHkUe` 는 셋(j·k·e), `navBarTop` 은 0, `iPhone`·`MyAdBox` 는 하나·둘. */
    private fun loneLowercase(s: String): Int {
        var count = 0
        var i = 0
        while (i < s.length) {
            if (s[i] !in 'a'..'z') {
                i++
                continue
            }
            val start = i
            while (i < s.length && s[i] in 'a'..'z') i++
            val besideUpper = (start > 0 && s[start - 1] in 'A'..'Z') || (i < s.length && s[i] in 'A'..'Z')
            if (i - start == 1 && besideUpper) count++
        }
        return count
    }

    // ── 선택자 짓기 ─────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * [chain](고른 것이 맨 앞)에서 선택자 후보를 **쓰고 싶은 차례로** 짓는다 — id → 시험용 속성 → 클래스 → 이름 있는 조상 아래의 그 이름 →
     * 이름 있는 가까운 조상에서의 경로 → `body` 에서의 경로. 고를 수 없는 요소면 빈 목록. 지금 페이지에서 그 요소 하나만 맞는지는 모른다 —
     * 스크립트가 재고 [choose] 가 고른다.
     */
    fun candidates(chain: List<PickedNode>): List<String> {
        val target = chain.firstOrNull() ?: return emptyList()
        if (!isPickable(target)) return emptyList()
        val out = LinkedHashSet<String>()
        stableId(target)?.let { out += "#" + escape(it) }
        val attribute = attributePart(target)
        attribute?.let { out += it }
        val classes = classPart(target, MAX_CLASSES)
        classes?.let { out += it }
        // 받은 조상은 body 까지(body 는 경로의 머리로만 쓴다). 너무 깊어 body 까지 받지 못했으면 받은 데까지.
        val bodyAt = chain.indexOfFirst { isBody(it) }.takeIf { it > 0 }
        val ancestors = 1 until (bodyAt ?: chain.size)
        // 그 이름을, 이름이 있는 가까운 조상 아래에서(`#sidebar div.ad`) — 이름만으로는 여럿에 맞을 때.
        val own = attribute ?: classes
        val named = ancestors.filter { anchorOf(chain[it]) != null }.take(MAX_ANCHORS)
        if (own != null) named.forEach { j -> anchorOf(chain[j])?.let { out += "$it $own" } }
        // 경로 — 이름이 있는 가까운 조상부터(짧다), 마지막은 body(또는 받은 맨 위)에서.
        for (j in named + (bodyAt ?: chain.lastIndex)) {
            if (j <= 0 || j > MAX_PATH) continue
            val head = if (isBody(chain[j])) "body" else anchorOf(chain[j]) ?: step(chain[j])
            out += buildString {
                append(head)
                for (i in j - 1 downTo 0) append(" > ").append(step(chain[i]))
            }
        }
        return out.map { it.trim() }.filter { isSafeSelector(it) }.distinct()
    }

    /** 스크립트가 잰 결과([checks] — [candidates] 와 같은 차례)로 고른다 — 고른 요소를 가리키고 **그것 하나만** 맞는 첫 후보. 없으면 null. */
    fun choose(candidates: List<String>, checks: List<Check>): String? {
        for (i in candidates.indices) {
            val check = checks.getOrNull(i) ?: return null
            if (check.count == 1 && check.hit) return candidates[i]
        }
        return null
    }

    /** 막대에 보일 짧은 설명 — `div#top.ad-slot.wide…` 꼴(이름은 페이지의 것 그대로, 길면 줄이고 제어·방향 글자는 뺀다). */
    fun describe(node: PickedNode): String = buildString {
        append(clip(node.tag))
        if (node.id.isNotEmpty()) append('#').append(clip(node.id))
        node.classes.take(2).forEach { append('.').append(clip(it)) }
        if (node.classes.size > 2) append('…')
    }

    private fun clip(s: String): String {
        val clean = s.filterNot { it.code < 0x20 || it.code == 0x7F || it in BIDI }
        return if (clean.length > 24) clean.take(23) + "…" else clean
    }

    private fun isBody(node: PickedNode) = node.tag.equals("body", ignoreCase = true)

    private fun stableId(node: PickedNode): String? = node.id.takeIf { it.isNotEmpty() && !isRandomName(it) }

    /** 쓸 만한 클래스 — 무작위·상태·꼴이 아닌 것. */
    private fun names(node: PickedNode): List<String> =
        node.classes.filter { it.isNotEmpty() && !isRandomName(it) && !isStateName(it) && !isUtility(it) }.distinct()

    private fun classPart(node: PickedNode, max: Int): String? {
        val names = names(node).take(max)
        if (names.isEmpty()) return null
        return escape(node.tag) + names.joinToString("") { "." + escape(it) }
    }

    /** `tag[data-testid=값]` — 값을 식별자로 적어 따옴표가 필요 없다. */
    private fun attributePart(node: PickedNode): String? {
        for (name in TEST_ATTRIBUTES) {
            val value = node.attributes.firstOrNull { it.first == name }?.second ?: continue
            if (value.isEmpty() || isRandomName(value)) continue
            return escape(node.tag) + "[" + name + "=" + escape(value) + "]"
        }
        return null
    }

    /** 이름으로 가리킬 수 있는 조상 — id → 시험용 속성 → 클래스. 없으면 null. */
    private fun anchorOf(node: PickedNode): String? =
        stableId(node)?.let { "#" + escape(it) } ?: attributePart(node) ?: classPart(node, MAX_CLASSES)

    /** 경로의 한 걸음 — 이름 + 쓸 만한 클래스(둘까지) + 같은 이름의 형제가 있으면 `:nth-of-type`. */
    private fun step(node: PickedNode): String = buildString {
        append(escape(node.tag))
        names(node).take(MAX_STEP_CLASSES).forEach { append('.').append(escape(it)) }
        if (node.ofType > 1) append(":nth-of-type(").append(node.nthOfType).append(')')
    }

    // ── CSS 의 글 ──────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * CSS 식별자로 적는다(`CSS.escape` 의 규칙 — 머리의 숫자·`-` 뒤의 숫자·홀로 선 `-`). 글자·숫자·`-`·`_`·한글 같은 비 ASCII 는 그대로,
     * 그 밖의 기호는 역빗금으로. **규칙을 끝내거나 새로 열 수 있는 글자**(`{`·`}`·`;`)와 빈칸·제어 글자는 16 진 이스케이프(`\7b `)로 적어
     * 선택자에 날것으로 남지 않게 한다 — 페이지가 정한 이름(`x{}body{display:none}`)이 다른 것을 숨기는 규칙이 되지 않는다([isSafeSelector]).
     */
    fun escape(ident: String): String {
        val sb = StringBuilder(ident.length + 8)
        for ((i, ch) in ident.withIndex()) {
            val c = ch.code
            when {
                c == 0 -> sb.append('�')
                c < 0x20 || c == 0x7F -> hex(sb, c)
                i == 0 && ch in '0'..'9' -> hex(sb, c)
                i == 1 && ch in '0'..'9' && ident[0] == '-' -> hex(sb, c)
                i == 0 && ch == '-' && ident.length == 1 -> sb.append("\\-")
                c >= 0x80 || ch == '-' || ch == '_' || ch in '0'..'9' || ch in 'a'..'z' || ch in 'A'..'Z' -> sb.append(ch)
                ch == '{' || ch == '}' || ch == ';' || ch == ' ' -> hex(sb, c)
                else -> sb.append('\\').append(ch)
            }
        }
        return sb.toString()
    }

    private fun hex(sb: StringBuilder, c: Int) {
        sb.append('\\').append(Integer.toHexString(c)).append(' ')
    }

    /**
     * 사이트의 페이지에 넣어도 되는 선택자인가 — 규칙을 끝내거나 새로 열 수 있는 글자(날것의 `{`·`}`·`;`)·따옴표·제어 글자가 없고 괄호가
     * 맞는다(닫히지 않은 `[`·`(` 는 뒤의 규칙까지 삼킨다). [escape] 로 지은 것은 늘 지난다 — 저장 파일에서 읽은 것도 이것으로 거른다.
     */
    fun isSafeSelector(selector: String): Boolean {
        if (selector.isBlank() || selector.length > MAX_SELECTOR) return false
        var paren = 0
        var bracket = 0
        var i = 0
        while (i < selector.length) {
            val c = selector[i]
            when {
                c.code < 0x20 || c.code == 0x7F -> return false
                c == '{' || c == '}' || c == ';' || c == '"' || c == '\'' -> return false
                c == '\\' -> {
                    // 역빗금 뒤의 한 글자는 이름의 일부다(16 진 이스케이프면 나머지 숫자도 이름의 글자다).
                    i++
                    if (i >= selector.length || selector[i].code < 0x20) return false
                }
                c == '(' -> paren++
                c == ')' -> if (--paren < 0) return false
                c == '[' -> bracket++
                c == ']' -> if (--bracket < 0) return false
            }
            i++
        }
        return paren == 0 && bracket == 0
    }

    // ── 표 ────────────────────────────────────────────────────────────────────────────────────────────────────

    /** 도구가 지어 붙이는 이름의 꼴 — 이름 통째로 맞춘다. */
    private val GENERATED = listOf(
        // emotion·React Native Web(css-1x2y3z, css-175oi2r, css-1abc2de-Button), styled-components(sc-bdVaJa, sc-1x2y3z-0 — 대문자나 숫자가 있다)
        Regex("""css-[0-9a-z]{5,}(-.*)?"""),
        Regex("""sc-(?=[0-9a-zA-Z]*[0-9A-Z])[0-9a-zA-Z]{4,}(-\d+)?"""),
        // styled-jsx, Svelte, React Native Web 의 원자 클래스(r-1awozwy), Meta 의 원자 CSS(x1n2onr6 — 숫자가 섞인 것만)
        Regex("""jsx-\d+"""),
        Regex("""svelte-[0-9a-z]{5,}"""),
        Regex("""r-(?=[0-9a-z]*\d)[0-9a-z]{6,10}"""),
        Regex("""x(?=[0-9a-z]*\d)[0-9a-z]{5,8}"""),
        // Angular 애니메이션, Ember·Ext JS·jQuery UI·MUI·JSS 가 세션마다 매기는 번호, YUI·MUI(v4)·Headless UI
        Regex("""ng-tns-c\d+-\d+"""),
        Regex("""(ember|ext-gen|ext-comp-|ext-element-|ui-id-|mui-|jss)\d+"""),
        Regex("""yui_.*"""),
        Regex("""makeStyles-.+"""),
        Regex("""headlessui-.+-\d+"""),
        // React 의 useId(:r1:, «r2», _r_3_)
        Regex(""".*[:«][rR][0-9a-zA-Z]*[:»].*"""),
        Regex("""_r_[0-9a-z]+_"""),
    )

    /** 광고 칸의 크기(`ad300x250`·`728x90`) — 숫자가 섞여도 사람이 지은 이름이다. */
    private val SIZE = Regex("""[A-Za-z]*\d{2,4}x\d{2,4}[A-Za-z]*""")

    private const val VOWELS = "aeiouy"

    /** 방향 제어 글자 — 설명이 거꾸로 그려지지 않게 뺀다. */
    private const val BIDI = "\u200E\u200F\u202A\u202B\u202C\u202D\u202E\u2066\u2067\u2068\u2069"

    private val STATE = setOf(
        "active", "open", "opened", "show", "shown", "showing", "visible", "hidden", "hide", "in", "fade", "selected", "current",
        "hover", "focus", "focused", "focus-visible", "expanded", "collapsed", "collapse", "collapsing", "loading", "loaded",
        "lazyload", "lazyloaded", "lazyloading", "sticky", "stuck", "fixed", "affix", "affix-top", "affix-bottom", "disabled",
        "checked", "animated", "animating", "visited", "pinned", "scrolled", "on", "off",
    )
    private val STATE_PREFIXES = listOf("is-", "is_", "has-", "has_", "ng-")

    private val UTILITY = setOf(
        "container", "container-fluid", "wrapper", "wrap", "inner", "outer", "content", "contents", "main", "row", "row-fluid", "col",
        "column", "columns", "box", "item", "items", "list", "text", "title", "link", "links", "image", "img", "icon", "button", "btn",
        "header", "footer", "section", "block", "card", "body", "page", "panel", "grid", "flex", "clearfix", "clear", "group", "label",
        "nav", "menu", "media", "info", "left", "right", "center", "top", "bottom", "small", "large", "big", "first", "last", "odd",
        "even", "relative", "absolute", "static", "inline", "inline-block", "inline-flex", "truncate", "uppercase", "lowercase",
        "rounded", "shadow", "border", "items-center", "items-start", "items-end", "justify-center", "justify-between",
        "justify-end", "justify-start", "text-center", "text-left", "text-right", "pull-left", "pull-right", "span", "widget",
        "module", "component", "element",
    )

    /** 숫자·크기 낱말로 끝나는 꼴 클래스 — `mt-3`·`col-md-6`·`d-none`·`w-full`·`mx-auto`. */
    private val UTILITY_SHAPE = Regex("""-?[a-z]+(-[a-z]+)*-(\d+(\.\d+)?|auto|full|screen|px|none)""")
}
