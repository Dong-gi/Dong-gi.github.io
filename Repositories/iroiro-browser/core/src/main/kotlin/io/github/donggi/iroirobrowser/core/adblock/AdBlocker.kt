package io.github.donggi.iroirobrowser.core.adblock

/**
 * 광고 차단 엔진 — 목록들을 읽어 만든다. 만든 뒤에는 바뀌지 않으므로 여러 스레드가 함께 쓴다(WebView 는 요청마다 다른 스레드에서 묻는다).
 *
 * - 네트워크: 막는 규칙 가운데 `$important` 를 먼저, 그다음 보통 규칙을 찾고, 예외(`@@`)가 맞으면 막지 않는다. 쪽(문서) 단위의 예외
 *   (`$document`·`$elemhide`·`$generichide`)는 [page] 가 정한다.
 * - 찾기: `||호스트^` 꼴은 **호스트 표**(요청 호스트와 그 상위 이름만 본다), 나머지는 **낱말 색인**(주소의 낱말이 든 칸만 본다), 낱말이 없는 것은
 *   늘 본다. `$badfilter` 가 가리킨 규칙은 만들 때 뺀다.
 * - 요소 숨김: 쪽을 열 때 [startCss](그 사이트의 것 + 클래스·id 가 없는 일반 규칙), 쪽을 다 연 뒤 [genericCss](쪽이 가진 클래스·id 에
 *   맞는 일반 규칙) — [CosmeticEngine] 의 머리말.
 */
class AdBlocker(lists: List<ParsedList>) {

    /** 쪽 하나에 대한 결정 — 그 쪽에서 아무것도 막지 않는가, 요소를 숨기지 않는가, 일반 숨김을 쓰지 않는가. */
    class Page(val url: String, val host: String, val allowAll: Boolean, val noCosmetic: Boolean, val noGenericCosmetic: Boolean)

    private val importantBlock = Index()
    private val block = Index()
    private val allow = Index()
    private val pageRules = ArrayList<NetworkRule>()
    private val cosmetic: CosmeticEngine

    val networkRuleCount: Int
    val cosmeticRuleCount: Int
    val skippedCount: Int = lists.sumOf { it.skipped }

    init {
        val bad = HashSet<String>()
        lists.forEach { l -> l.network.forEach { if (it.badfilterOf != null) bad += it.badfilterOf } }
        var count = 0
        for (l in lists) {
            for (r in l.network) {
                if (r.badfilterOf != null || r.text in bad) continue
                count++
                when {
                    r.exception && (r.document || r.elemhide || r.generichide) -> {
                        pageRules += r
                        if (r.types != 0) allow.add(r)
                    }
                    r.exception -> allow.add(r)
                    r.important -> importantBlock.add(r)
                    else -> block.add(r)
                }
            }
        }
        networkRuleCount = count
        cosmetic = CosmeticEngine(lists.flatMap { it.cosmetic })
        cosmeticRuleCount = cosmetic.size
    }

    /** 쪽(주 문서)의 결정. 주 문서를 열 때 한 번. */
    fun page(documentUrl: String): Page {
        val host = Request.hostOf(documentUrl)
        val req = Request(documentUrl, host, RequestType.DOCUMENT)
        var allowAll = false
        var noCosmetic = false
        var noGeneric = false
        for (r in pageRules) {
            if (!r.matchesIgnoringType(req)) continue
            if (r.document) allowAll = true
            if (r.elemhide) noCosmetic = true
            if (r.generichide) noGeneric = true
        }
        return Page(documentUrl, host, allowAll, noCosmetic || allowAll, noGeneric)
    }

    /** 이 요청을 막는가 — 막으면 막은 규칙, 아니면 null. */
    fun shouldBlock(url: String, page: Page?, types: Int): NetworkRule? {
        if (page?.allowAll == true) return null
        val req = Request(url, page?.host.orEmpty(), types)
        importantBlock.find(req)?.let { return it }
        val rule = block.find(req) ?: return null
        return if (allow.find(req) != null) null else rule
    }

    /** 쪽을 열 때 넣을 CSS — 그 사이트의 숨김 규칙과 열쇠(클래스·id) 없는 일반 규칙. 숨길 것이 없으면 빈 글. */
    fun startCss(page: Page): String =
        if (page.noCosmetic) "" else rules(cosmetic.startSelectors(page.host, generic = !page.noGenericCosmetic))

    /** 쪽이 가진 클래스·id([classes]·[ids] — 점·샵 없는 이름)에 맞는 일반 숨김 규칙의 CSS. */
    fun genericCss(page: Page, classes: Collection<String>, ids: Collection<String>): String =
        if (page.noCosmetic || page.noGenericCosmetic) "" else rules(cosmetic.genericFor(page.host, classes, ids))

    /** 열쇠 없는 일반 규칙의 수·열쇠 있는 일반 규칙의 수(재기에). */
    val genericAlwaysCount: Int get() = cosmetic.alwaysCount
    val genericKeyedCount: Int get() = cosmetic.keyedCount

    /** 선택자마다 한 줄 — 잘못된 선택자 하나가 나머지를 망치지 않게([CosmeticEngine.css] — 사용자가 숨긴 요소와 같은 꼴). */
    private fun rules(selectors: List<String>): String = CosmeticEngine.css(selectors)

    /** 규칙을 찾는 색인 하나 — 호스트 표·낱말 칸·늘 볼 것. */
    private class Index {
        val byHost = HashMap<String, ArrayList<NetworkRule>>()
        val byToken = HashMap<String, ArrayList<NetworkRule>>()
        val others = ArrayList<NetworkRule>()

        fun add(r: NetworkRule) {
            when {
                r.host != null -> byHost.getOrPut(r.host) { ArrayList(1) } += r
                r.token != null -> byToken.getOrPut(r.token) { ArrayList(1) } += r
                else -> others += r
            }
        }

        fun find(req: Request): NetworkRule? {
            if (byHost.isNotEmpty() && req.host.isNotEmpty()) {
                for (h in Domains.suffixes(req.host)) {
                    byHost[h]?.let { list -> for (r in list) if (r.matches(req)) return r }
                }
            }
            if (byToken.isNotEmpty()) {
                for (t in req.tokens) {
                    byToken[t]?.let { list -> for (r in list) if (r.matches(req)) return r }
                }
            }
            for (r in others) if (r.matches(req)) return r
            return null
        }
    }
}
