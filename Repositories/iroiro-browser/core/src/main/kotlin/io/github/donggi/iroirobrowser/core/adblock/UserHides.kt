package io.github.donggi.iroirobrowser.core.adblock

import java.util.Locale

/** 사용자가 숨긴 요소 하나 — `사이트##선택자`(광고 목록의 요소 숨김과 같은 꼴). [site] 는 등록 도메인 어림([Domains.registrable]). */
class HideRule(val site: String, val selector: String) {
    override fun equals(other: Any?): Boolean = other is HideRule && other.site == site && other.selector == selector

    override fun hashCode(): Int = site.hashCode() * 31 + selector.hashCode()

    override fun toString(): String = "$site##$selector"
}

/**
 * 사용자가 숨긴 요소의 규칙들(페이지의 메뉴 '요소 숨기기'). **광고 목록과 따로 둔다** — 목록 갱신·목록 빼기가 지우지 않고, 목록의 예외
 * (`#@#`·`$elemhide`)와 광고 차단의 켜기·사이트별 끄기가 닿지 않는다(사용자가 직접 고른 것이라 늘 건다).
 *
 * 규칙의 범위는 **그 사이트**(등록 도메인 — `www.example.com` 에서 숨기면 `m.example.com` 에서도). 찾기는 광고 차단의 요소 숨김 엔진
 * ([CosmeticEngine])에 사이트를 도메인으로 준 규칙으로 넣어 같은 길로 고른다 — 그 사이트와 그 아래 이름에만 걸린다. 저장은 목록과 같은 글
 * ([format]·[parse] — 한 줄에 `사이트##선택자` 하나)이라 사람이 읽을 수 있다.
 *
 * 만든 뒤 바뀌지 않는다 — 고치면 새것을 돌려준다(여러 스레드가 읽어도 된다).
 */
class UserHides(rules: List<HideRule>) {

    /** 더한 차례(같은 것은 한 번). */
    val rules: List<HideRule> = rules.distinct()

    private val engine = CosmeticEngine(this.rules.map { CosmeticRule(it.selector, exception = false, include = listOf(it.site), exclude = emptyList()) })

    val size: Int get() = rules.size

    operator fun contains(rule: HideRule): Boolean = rule in rules

    /** [host] 의 페이지에 거는 선택자 — 그 호스트가 든 사이트의 규칙. */
    fun selectorsFor(host: String): List<String> =
        if (rules.isEmpty() || host.isEmpty()) emptyList() else engine.startSelectors(host.lowercase(Locale.ROOT), generic = false)

    /** [host] 의 페이지에 넣는 CSS(광고 차단의 요소 숨김과 같은 꼴 — [CosmeticEngine.css]). 없으면 빈 글. */
    fun cssFor(host: String): String = CosmeticEngine.css(selectorsFor(host))

    /** 사이트 하나의 규칙 — 더한 차례. */
    fun rulesOf(site: String): List<HideRule> = rules.filter { it.site == site }

    /** 규칙이 있는 사이트 — 이름 차례. */
    fun sites(): List<String> = rules.map { it.site }.distinct().sorted()

    fun plus(rule: HideRule): UserHides = if (rule in rules) this else UserHides(rules + rule)

    fun plus(added: List<HideRule>): UserHides = UserHides(rules + added)

    fun minus(rule: HideRule): UserHides = UserHides(rules - rule)

    fun minusSite(site: String): UserHides = UserHides(rules.filter { it.site != site })

    /** 저장할 글 — 머리말 한 줄 + 규칙마다 한 줄. */
    fun format(): String = buildString {
        append(HEADER).append('\n')
        rules.forEach { append(it.site).append("##").append(it.selector).append('\n') }
    }

    companion object {
        const val HEADER = "! Web — 사용자가 숨긴 요소. 한 줄에 사이트##선택자 하나(광고 목록의 요소 숨김과 같은 꼴)"

        /** 사이트 이름의 글자 — 호스트 이름과 IP(IPv6 의 `[…]` 포함). 목록 문법의 기호(`,`·`~`·`#`·`/`·`|`·`$`·`^`·`@`·`*`)는 없다. */
        private val SITE = Regex("""[a-z0-9._\-\[\]:]{1,253}""")

        /** [host] 의 사이트에 [selector] 를 숨기는 규칙. 사이트나 선택자를 쓸 수 없으면 null. */
        fun rule(host: String, selector: String): HideRule? {
            val site = siteOf(host)
            val s = selector.trim()
            if (!isValidSite(site) || !HideSelectors.isSafeSelector(s)) return null
            return HideRule(site, s)
        }

        /** 규칙의 범위 — 등록 도메인 어림(대소문자·끝의 점 없이). */
        fun siteOf(host: String): String = Domains.registrable(host.lowercase(Locale.ROOT).trimEnd('.'))

        fun isValidSite(site: String): Boolean = SITE.matches(site) && !site.startsWith('.') && !site.endsWith('.')

        /**
         * 저장한 글을 읽는다 — 빈 줄·주석(`!`)·머리(`[`)는 건너뛰고, 쓸 수 없는 줄(사이트가 이상하거나 선택자가 [HideSelectors.isSafeSelector] 를
         * 지나지 못한다)은 버린다. 같은 규칙은 한 번.
         */
        fun parse(text: String): UserHides {
            val out = ArrayList<HideRule>()
            text.lineSequence().forEach { raw ->
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("!") || line.startsWith("[")) return@forEach
                val i = line.indexOf("##")
                if (i <= 0) return@forEach
                val site = line.substring(0, i).trim().lowercase(Locale.ROOT)
                val selector = line.substring(i + 2).trim()
                if (isValidSite(site) && HideSelectors.isSafeSelector(selector)) out += HideRule(site, selector)
            }
            return UserHides(out)
        }
    }
}
