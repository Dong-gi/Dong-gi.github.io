package io.github.donggi.iroirobrowser.core.adblock

import java.util.Locale
import java.util.regex.PatternSyntaxException

/** 네트워크 규칙 하나 — 막기 또는 예외(`@@`). [FilterParser] 가 만든다. */
class NetworkRule(
    /** 목록의 한 줄 그대로 — `$badfilter` 가 이것으로 짝을 찾는다. */
    val text: String,
    val exception: Boolean,
    /** 주소 무늬. 정규식 규칙이면 null. */
    val pattern: UrlPattern?,
    val regex: Regex?,
    /** `||호스트^` 꼴(호스트 표에 넣는다). 아니면 null. */
    val host: String?,
    /** 걸리는 요청 종류([RequestType] 마스크). */
    val types: Int,
    val party: Int,
    /** `$domain=` — 이 쪽(문서)에서만 / 이 쪽에서는 빼고. */
    val includeDomains: List<String>?,
    val excludeDomains: List<String>?,
    val important: Boolean,
    /** 낱말 색인의 낱말(호스트 규칙·정규식은 null). */
    val token: String?,
    /** 예외의 `$document` — 그 쪽 전체에서 아무것도 막지 않는다. */
    val document: Boolean,
    /** 예외의 `$elemhide` — 그 쪽에서 요소를 숨기지 않는다. */
    val elemhide: Boolean,
    /** 예외의 `$generichide` — 그 쪽에서 일반 숨김 규칙을 쓰지 않는다. */
    val generichide: Boolean,
    /** `$badfilter` 규칙이면 그것이 끄는 규칙의 글(이 규칙 자신은 아무것도 맞추지 않는다). */
    val badfilterOf: String? = null,
) {
    /**
     * 이 요청에 걸리는가(무늬·종류·제1자/제3자·쪽의 도메인).
     *
     * 요청의 종류는 어림이라([RequestType.guess]) 여럿일 수 있다. **막는 규칙은 그 모두가 자기 종류일 때만** 걸리고(확실할 때만 막는다),
     * **예외는 하나라도 겹치면** 걸린다(의심스러우면 통과시킨다). 종류를 좁힌 막는 규칙(`$ping`·`$script` …)은 목록이 그 종류만 막으려고
     * 좁힌 것이다 — 겹치기만 해도 막던 앞 판에서는 EasyPrivacy 의 `*$ping,third-party` 가 종류를 모르는 **모든 제3자 요청**을 막았다
     * (fast.com 의 측정 서버·영상 CDN·남의 API — 2026-10-05, 실제 프록시를 넣은 사용자가 잡았다).
     */
    fun matches(req: Request): Boolean {
        val typeOk = if (exception) types and req.types != 0 else req.types != 0 && req.types and types.inv() == 0
        return typeOk && matchesIgnoringType(req)
    }

    /** 종류는 보지 않고 — 쪽 단위 예외(`$document` 등)를 쪽의 주소와 견줄 때. */
    fun matchesIgnoringType(req: Request): Boolean {
        when (party) {
            FIRST_PARTY -> if (req.thirdParty) return false
            THIRD_PARTY -> if (!req.thirdParty) return false
        }
        if (includeDomains != null && includeDomains.none { Domains.isSameOrSubdomain(req.documentHost, it) }) return false
        if (excludeDomains != null && excludeDomains.any { Domains.isSameOrSubdomain(req.documentHost, it) }) return false
        if (host != null) return Domains.isSameOrSubdomain(req.host, host)
        if (regex != null) return regex.containsMatchIn(if (regex.options.contains(RegexOption.IGNORE_CASE)) req.urlLower else req.url)
        val p = pattern ?: return true
        return p.matches(if (p.matchCase) req.url else req.urlLower, req.hostStart, req.hostEnd)
    }

    override fun toString(): String = text

    companion object {
        const val ANY_PARTY = 0
        const val FIRST_PARTY = 1
        const val THIRD_PARTY = 2
    }
}

/** 요소 숨김 규칙 하나(`##`·`#@#`). [include] 가 비면 모든 쪽(일반 규칙). */
class CosmeticRule(val selector: String, val exception: Boolean, val include: List<String>, val exclude: List<String>)

/** 목록 하나를 읽은 것. */
class ParsedList(val network: List<NetworkRule>, val cosmetic: List<CosmeticRule>, val skipped: Int)

/**
 * 광고 목록(ABP·uBO·AdGuard 의 공통 문법과 hosts 꼴)을 읽는다. **맞출 수 없는 규칙은 건너뛴다** — 응답을 고치는 것(`$csp`·`$replace`·
 * `$removeparam`·`$redirect-rule` 만 단 것), 스크립트를 끼우는 것(`##+js`·`#%#`·`#$#`), HTML 거르기(`##^`), uBO·ABP 의 절차 선택자(`:has-text`
 * 등), 팝업 막기, 모르는 선택지. 모르는 것을 넓게 막기보다 건너뛰는 쪽을 고른다(쪽이 깨지지 않게).
 */
object FilterParser {

    fun parse(text: String): ParsedList {
        val network = ArrayList<NetworkRule>()
        val cosmetic = ArrayList<CosmeticRule>()
        var skipped = 0
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("!") || line.startsWith("[") || (line.startsWith("#") && !line.startsWith("##") && !line.startsWith("#@#"))) {
                return@forEach
            }
            val cosmeticRule = cosmetic(line)
            when {
                cosmeticRule != null -> cosmetic += cosmeticRule
                isCosmeticSyntax(line) -> skipped++
                else -> network(line)?.let { network += it } ?: skipped++
            }
        }
        return ParsedList(network, cosmetic, skipped)
    }

    // ── 요소 숨김 ──────────────────────────────────────────────────────────────────────────────────────────────

    private val COSMETIC_MARKERS = listOf("#@#", "##", "#?#", "#@?#", "#$#", "#@$#", "#%#", "#@%#", "#^#")

    /** 도메인 부분 뒤에 요소 숨김의 표지가 있는가(지원하지 않는 꼴까지 — 그것들은 네트워크 규칙으로 읽지 않고 건너뛴다). */
    private fun isCosmeticSyntax(line: String): Boolean {
        val i = line.indexOf('#')
        if (i < 0) return false
        val domains = line.substring(0, i)
        return domains.none { it == '/' || it == '|' || it == '$' || it == '^' || it == '@' } &&
            COSMETIC_MARKERS.any { line.startsWith(it, i) }
    }

    private fun cosmetic(line: String): CosmeticRule? {
        val i = line.indexOf('#')
        if (i < 0) return null
        val exception = line.startsWith("#@#", i)
        if (!exception && !line.startsWith("##", i)) return null
        val domainsPart = line.substring(0, i)
        if (domainsPart.any { it == '/' || it == '|' || it == '$' || it == '^' || it == '@' }) return null
        val selector = line.substring(i + if (exception) 3 else 2).trim()
        if (selector.isEmpty() || !isPlainSelector(selector)) return null
        val include = ArrayList<String>()
        val exclude = ArrayList<String>()
        if (domainsPart.isNotEmpty()) {
            for (d in domainsPart.split(',')) {
                val name = d.trim().lowercase(Locale.ROOT)
                if (name.isEmpty()) continue
                // uBO 의 `example.*`(모든 최상위 도메인)는 어림할 수 없다 — 그 이름은 뺀다.
                if ('*' in name) continue
                if (name.startsWith("~")) exclude += name.substring(1) else include += name
            }
            if (include.isEmpty() && exclude.isEmpty()) return null
        }
        return CosmeticRule(selector, exception, include, exclude)
    }

    /** 브라우저가 그대로 아는 CSS 선택자인가 — uBO·ABP·AdGuard 의 절차 선택자와 스크립트·HTML 규칙은 아니다. */
    private fun isPlainSelector(s: String): Boolean {
        if (s.startsWith("+") || s.startsWith("^")) return false
        if ('{' in s || '}' in s) return false
        return PROCEDURAL.none { s.contains(it, ignoreCase = true) }
    }

    private val PROCEDURAL = listOf(
        ":has-text(", ":contains(", ":-abp-", ":xpath(", ":upward(", ":matches-css", ":matches-attr", ":matches-path", ":matches-prop",
        ":min-text-length(", ":watch-attr(", ":remove(", ":remove-attr(", ":remove-class(", ":style(", ":others(", ":if(", ":if-not(",
        ":nth-ancestor(", ":shadow", ":matches-media(", "+js(",
    )

    // ── 네트워크 ──────────────────────────────────────────────────────────────────────────────────────────────

    private val HOSTS_PREFIXES = listOf("0.0.0.0 ", "127.0.0.1 ", "0.0.0.0\t", "127.0.0.1\t")
    private val HOST_CHARS = Regex("[a-z0-9.-]+")
    private val HOST_RULE = Regex("\\|\\|([a-z0-9-]+(\\.[a-z0-9-]+)*)\\^")
    private val OPTION = Regex("~?[a-z0-9_-]+(=.*)?")

    /** 한 줄을 네트워크 규칙으로. 쓸 수 없으면 null. */
    fun network(line: String): NetworkRule? {
        HOSTS_PREFIXES.firstOrNull { line.startsWith(it) }?.let { prefix ->
            val host = line.substring(prefix.length).trim().substringBefore(' ').substringBefore('#').lowercase(Locale.ROOT)
            if (host.isEmpty() || host == "localhost" || host == "0.0.0.0" || !HOST_CHARS.matches(host)) return null
            return hostRule("||$host^", host, exception = false)
        }
        var rest = line
        val exception = rest.startsWith("@@")
        if (exception) rest = rest.substring(2)
        var options = emptyList<String>()
        val dollar = rest.lastIndexOf('$')
        if (dollar >= 0) {
            val parts = rest.substring(dollar + 1).split(',')
            if (parts.all { OPTION.matches(it.lowercase(Locale.ROOT)) }) {
                options = parts
                rest = rest.substring(0, dollar)
            }
        }
        if (options.any { it.trim().equals("badfilter", ignoreCase = true) }) {
            // 같은 글의 규칙을 끈다 — `$badfilter` 를 뺀 글이 그 규칙의 글이다.
            val kept = options.filterNot { it.trim().equals("badfilter", ignoreCase = true) }
            val target = (if (exception) "@@" else "") + rest + (if (kept.isEmpty()) "" else "$" + kept.joinToString(","))
            return NetworkRule(line, exception, null, null, null, 0, NetworkRule.ANY_PARTY, null, null, false, null, false, false, false, target)
        }
        val o = Options()
        for (opt in options) if (!o.read(opt, exception)) return null
        val pageLevel = o.document || o.elemhide || o.generichide
        // 쪽 단위 예외만 적었으면 쪽에만 걸린다(그 쪽의 요청 하나하나를 풀어 주는 규칙이 아니다).
        if (pageLevel && !o.typesGiven) o.types = 0
        // 주 문서만 막으라는 규칙은 쓰지 않는다(막은 쪽을 보일 화면이 없다).
        if (!pageLevel && (o.blocksDocumentOnly || o.types == 0)) return null
        if (rest.length > 2 && rest.startsWith("/") && rest.endsWith("/")) {
            val regex = try {
                Regex(rest.substring(1, rest.length - 1), if (o.matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE))
            } catch (e: PatternSyntaxException) {
                return null
            }
            return NetworkRule(line, exception, null, regex, null, o.types, o.party, o.include, o.exclude, o.important, null,
                o.document, o.elemhide, o.generichide)
        }
        val lower = rest.lowercase(Locale.ROOT)
        // `||호스트^` — 호스트 표(목록의 규칙 대부분이 이 꼴이다).
        if (!o.matchCase) {
            HOST_RULE.matchEntire(lower)?.let { m ->
                return NetworkRule(line, exception, null, null, m.groupValues[1], o.types, o.party, o.include, o.exclude, o.important, null,
                    o.document, o.elemhide, o.generichide)
            }
        }
        val pattern = UrlPattern.parse(rest, o.matchCase) ?: return null
        return NetworkRule(line, exception, pattern, null, null, o.types, o.party, o.include, o.exclude, o.important,
            token(if (o.matchCase) rest.lowercase(Locale.ROOT) else lower), o.document, o.elemhide, o.generichide)
    }

    private fun hostRule(text: String, host: String, exception: Boolean) = NetworkRule(
        text, exception, null, null, host, RequestType.DEFAULT, NetworkRule.ANY_PARTY, null, null, false, null, false, false, false,
    )

    /** 선택지를 읽는 동안의 상태. */
    private class Options {
        /** 걸리는 종류. 종류를 하나도 적지 않으면 기본(주 문서·팝업 빼고 다). */
        var types = RequestType.DEFAULT
        var typesGiven = false
            private set
        private var negatedTypes = 0
        var party = NetworkRule.ANY_PARTY
        var include: List<String>? = null
        var exclude: List<String>? = null
        var important = false
        var matchCase = false
        var document = false
        var elemhide = false
        var generichide = false
        private var sawDocumentBlock = false

        /** 막는 규칙이 종류로 `$document` 만 적었다 — 쓰지 않는다. */
        val blocksDocumentOnly: Boolean get() = sawDocumentBlock && !typesGiven

        /** 선택지 하나. 쓸 수 없는 것이면 false(규칙을 버린다). */
        fun read(rawOpt: String, exception: Boolean): Boolean {
            val opt = rawOpt.trim()
            val negated = opt.startsWith("~")
            val name = opt.removePrefix("~").substringBefore('=').lowercase(Locale.ROOT)
            val value = opt.substringAfter('=', "")
            val type = RequestType.byName(name)
            if (type != null) {
                when {
                    // 팝업만 막는 규칙은 쓰지 않는다(`~popup` 은 아무것도 바꾸지 않는다).
                    type == RequestType.POPUP -> return negated
                    type == RequestType.DOCUMENT && !negated -> if (exception) document = true else sawDocumentBlock = true
                    negated -> negatedTypes = negatedTypes or type
                    else -> {
                        if (!typesGiven) types = 0
                        typesGiven = true
                        types = types or type
                    }
                }
                if (negatedTypes != 0) types = (if (typesGiven) types else RequestType.DEFAULT) and negatedTypes.inv()
                return true
            }
            when (name) {
                "third-party", "3p", "strict3p" -> party = if (negated) NetworkRule.FIRST_PARTY else NetworkRule.THIRD_PARTY
                "first-party", "1p", "strict1p" -> party = if (negated) NetworkRule.THIRD_PARTY else NetworkRule.FIRST_PARTY
                "domain", "from" -> {
                    val inc = ArrayList<String>()
                    val exc = ArrayList<String>()
                    for (d in value.split('|')) {
                        val n = d.trim().lowercase(Locale.ROOT)
                        if (n.isEmpty() || '*' in n || n.startsWith("/")) continue
                        if (n.startsWith("~")) exc += n.substring(1) else inc += n
                    }
                    if (inc.isEmpty() && exc.isEmpty()) return false
                    include = inc.takeIf { it.isNotEmpty() }
                    exclude = exc.takeIf { it.isNotEmpty() }
                }
                "important" -> important = true
                "match-case" -> matchCase = true
                "all" -> {
                    types = RequestType.DEFAULT
                    typesGiven = true
                }
                "elemhide", "ehide" -> if (exception) elemhide = true else return false
                "generichide", "ghide" -> if (exception) generichide = true else return false
                // 막고 무해한 것으로 바꿔 주라는 규칙 — 막기만 한다(예외 쪽은 바꿔 주기만 끄는 것이라 쓰지 않는다).
                "redirect", "rewrite" -> if (exception) return false
                // 쓰지 않는 것 — 응답 고치기·주소 고치기·남의 쪽 띄우기·IP 거르기 등.
                else -> return false
            }
            return true
        }
    }

    /**
     * 낱말 색인의 낱말 — 무늬 안에서 앞뒤가 확실히 끊긴(앵커·구분 글자) 가장 긴 글자·숫자 묶음. 그 낱말은 맞는 주소에 **통째로** 들어 있다.
     * 너무 흔한 낱말은 피한다. 없으면 null(색인 밖 목록).
     */
    internal fun token(pattern: String): String? {
        var p = pattern
        val hostAnchor = p.startsWith("||")
        val startAnchor = !hostAnchor && p.startsWith("|")
        if (hostAnchor) p = p.substring(2) else if (startAnchor) p = p.substring(1)
        val endAnchor = p.endsWith("|")
        if (endAnchor) p = p.dropLast(1)
        var best: String? = null
        var i = 0
        while (i < p.length) {
            if (!isTokenChar(p[i])) {
                i++
                continue
            }
            val start = i
            while (i < p.length && isTokenChar(p[i])) i++
            val end = i
            val boundedLeft = if (start == 0) (hostAnchor || startAnchor) else p[start - 1] != '*'
            val boundedRight = if (end == p.length) endAnchor else p[end] != '*'
            val word = p.substring(start, end)
            if (boundedLeft && boundedRight && word.length >= 2 && word !in COMMON && (best == null || word.length > best.length)) best = word
        }
        return best
    }

    internal fun isTokenChar(c: Char): Boolean = c in 'a'..'z' || c in '0'..'9' || c == '%'

    private val COMMON = setOf("http", "https", "www", "com", "net", "org", "html", "php", "js", "css", "png", "jpg", "gif")
}
