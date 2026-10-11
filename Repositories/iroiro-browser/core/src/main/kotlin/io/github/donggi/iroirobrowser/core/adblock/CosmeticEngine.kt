package io.github.donggi.iroirobrowser.core.adblock

/**
 * 요소 숨김(`##`·`#@#`) — 쪽의 호스트에 맞는 선택자를 고른다.
 *
 * - 도메인 없는 `##선택자` 는 **일반 규칙**(모든 쪽), 도메인이 붙은 것은 그 도메인과 그 아래 이름에만.
 * - `~도메인` 은 그 도메인에서는 빼라는 것, `도메인#@#선택자` 는 그 도메인에서 그 선택자를 쓰지 말라는 것, 도메인 없는 `#@#선택자` 는 어디서도.
 *
 * **일반 규칙은 다 넣지 않는다**(EasyList 만으로 1만 4천 줄·560 KB — 쪽마다 넣기에 무겁다). uBO 처럼 둘로 가른다:
 * 선택자에 클래스·id(`.이름`·`#이름`)가 있으면 그 클래스·id 가 쪽에 **실제로 있을 때만** 넣고([genericFor] — 쪽이 가진 이름을 받아),
 * 없는 것(속성 선택자 등)만 처음부터 넣는다([startSelectors]).
 */
class CosmeticEngine(rules: List<CosmeticRule>) {

    /** 클래스·id 를 열쇠로 한 일반 선택자 — 열쇠는 `.이름` 또는 `#이름`. */
    private val genericByKey = HashMap<String, ArrayList<String>>()

    /** 열쇠가 없는 일반 선택자 — 늘 넣는다. */
    private val genericAlways = ArrayList<String>()
    private val specific = HashMap<String, LinkedHashSet<String>>()
    private val notOn = HashMap<String, HashSet<String>>()
    private val exceptedOn = HashMap<String, HashSet<String>>()
    private val exceptedEverywhere = HashSet<String>()

    val size: Int
    val alwaysCount: Int get() = genericAlways.size
    val keyedCount: Int

    init {
        val generic = LinkedHashSet<String>()
        for (r in rules) {
            when {
                r.exception && r.include.isEmpty() -> exceptedEverywhere += r.selector
                r.exception -> r.include.forEach { exceptedOn.getOrPut(it) { HashSet() } += r.selector }
                r.include.isEmpty() -> {
                    generic += r.selector
                    r.exclude.forEach { notOn.getOrPut(it) { HashSet() } += r.selector }
                }
                else -> {
                    r.include.forEach { specific.getOrPut(it) { LinkedHashSet() } += r.selector }
                    r.exclude.forEach { notOn.getOrPut(it) { HashSet() } += r.selector }
                }
            }
        }
        generic.removeAll(exceptedEverywhere)
        var keyed = 0
        for (s in generic) {
            val key = keyOf(s)
            if (key == null) {
                genericAlways += s
            } else {
                genericByKey.getOrPut(key) { ArrayList(1) } += s
                keyed++
            }
        }
        keyedCount = keyed
        size = generic.size + specific.values.sumOf { it.size }
    }

    /** 쪽을 열 때 넣을 선택자 — 그 호스트의 것과(일반 규칙을 쓰면) 열쇠 없는 일반 규칙. */
    fun startSelectors(host: String, generic: Boolean): List<String> {
        val skip = skipped(host)
        val out = ArrayList<String>()
        if (generic) genericAlways.filterTo(out) { skip == null || it !in skip }
        for (s in Domains.suffixes(host)) {
            specific[s]?.forEach { sel -> if ((skip == null || sel !in skip) && sel !in exceptedEverywhere) out += sel }
        }
        return out
    }

    /** 쪽이 가진 클래스·id 에 맞는 일반 선택자. [classes]·[ids] 는 앞에 점·샵이 없는 이름. */
    fun genericFor(host: String, classes: Collection<String>, ids: Collection<String>): List<String> {
        if (genericByKey.isEmpty()) return emptyList()
        val skip = skipped(host)
        val out = ArrayList<String>()
        fun take(key: String) {
            genericByKey[key]?.forEach { if (skip == null || it !in skip) out += it }
        }
        classes.forEach { take(".$it") }
        ids.forEach { take("#$it") }
        return out
    }

    private fun skipped(host: String): HashSet<String>? {
        var skip: HashSet<String>? = null
        for (s in Domains.suffixes(host)) {
            notOn[s]?.let { (skip ?: HashSet<String>().also { h -> skip = h }).addAll(it) }
            exceptedOn[s]?.let { (skip ?: HashSet<String>().also { h -> skip = h }).addAll(it) }
        }
        return skip
    }

    companion object {
        /**
         * 숨길 선택자들의 CSS — **선택자마다 한 줄**(잘못된 선택자 하나가 나머지를 망치지 않게). 광고 목록의 숨김([AdBlocker])과 사용자가 숨긴
         * 요소([UserHides])가 함께 쓴다. 숨길 것이 없으면 빈 글.
         */
        fun css(selectors: List<String>): String {
            if (selectors.isEmpty()) return ""
            val sb = StringBuilder(selectors.size * 40)
            for (s in selectors) sb.append(s).append("{display:none!important}\n")
            return sb.toString()
        }

        /**
         * 선택자의 열쇠 — 맨 바깥(괄호·대괄호·따옴표 밖)의 첫 `.이름`·`#이름`. 그 이름을 가진 요소가 쪽에 없으면 선택자는 맞을 수 없다.
         * `:not(.x)` 안의 것은 열쇠가 아니다(없어야 맞는 조건이다). 이스케이프가 든 이름은 쓰지 않는다.
         */
        fun keyOf(selector: String): String? {
            var bracket = 0
            var paren = 0
            var quote = ' '
            var i = 0
            while (i < selector.length) {
                val c = selector[i]
                when {
                    quote != ' ' -> if (c == '\\') i++ else if (c == quote) quote = ' '
                    c == '"' || c == '\'' -> quote = c
                    c == '\\' -> i++
                    c == '[' -> bracket++
                    c == ']' -> bracket--
                    c == '(' -> paren++
                    c == ')' -> paren--
                    (c == '.' || c == '#') && bracket == 0 && paren == 0 -> {
                        var j = i + 1
                        while (j < selector.length && (selector[j].isLetterOrDigit() || selector[j] == '-' || selector[j] == '_')) j++
                        if (j > i + 1 && (j == selector.length || selector[j] != '\\')) return selector.substring(i, j)
                    }
                }
                i++
            }
            return null
        }
    }
}
