package io.github.donggi.iroirobrowser.core

import java.util.Locale
import kotlin.math.ln

/**
 * 주소창의 제안 — **방문 기록·북마크에서만** 고른다. 친 글을 밖(검색 엔진)에 묻지 않는다(검색어 제안을 하지 않는다 — 친 글이 프록시 너머로
 * 새지 않게). 순수 계산이라 JVM 시험이 답한다. 만든 뒤에는 바뀌지 않으므로 기록이 바뀌면 새로 만든다.
 *
 * 꼴은 크롬(HistoryURL·HistoryQuick 제공자)과 파이어폭스(awesomebar)의 관행을 따랐다(조사 2026-10-05, 그 소스를 읽었다 — CLAUDE.md '주소창과 제안').
 *
 * - **인라인 완성**([inline]): 친 글이 **ASCII 한 낱말**이고 어떤 주소(앞의 `https://`·`www.` 를 뗀 것)의 앞머리이면 그 뒤를 채운다. 호스트까지
 *   치는 동안은 호스트까지만(`na` → `naver.com` — 파이어폭스), `/`·`?`·`:` 를 친 뒤에는 주소 전체까지. 한글 등이 섞이면 채우지 않는다(두 엔진
 *   모두 — 조합 중인 글과 부딪친다). **아무 곳이나 채우지 않는다** — 주소창에서 친 적이 있거나([Entry.typed]) 북마크했거나 두 번 넘게 간 곳만
 *   (크롬은 친 적이 있는 곳만, 파이어폭스는 간 곳 모두 — 그 사이).
 * - **목록**([suggest]): 친 글을 빈칸으로 가른 낱말마다 제목이나 주소에 들어 있어야 한다. 맞은 자리(주소의 앞머리 > 낱말의 앞머리 > 가운데)의
 *   무게에 자주·최근 간 정도([frecency])를 곱해 늘어놓는다. 인라인 완성이 있으면 그 주소가 맨 위다(Enter 가 갈 곳). 빈 글이면 [top].
 */
class Suggester(entries: Collection<Entry>) {

    /** 기록 한 줄 — 방문 기록과 북마크를 주소로 합친 것. */
    class Entry(
        val url: String,
        val title: String,
        val visits: Int,
        /** 주소창에서 쳐서(또는 제안을 골라) 간 횟수. */
        val typed: Int,
        /** 마지막으로 간 때(북마크만이면 넣은 때) — 1970 년부터의 밀리초. */
        val lastVisit: Long,
        val bookmarked: Boolean,
    )

    /** 목록의 한 줄. [display] 는 줄에 보일 주소(`https://`·`www.` 를 떼고 퍼센트 부호화를 푼 것). */
    class Suggestion(val url: String, val title: String, val display: String, val bookmarked: Boolean)

    /** 인라인 완성 — 주소창에 보일 글([text] — 친 글로 시작한다)과 Enter 를 누르면 갈 주소([url]). */
    class Inline(val text: String, val url: String)

    private class Item(val entry: Entry) {
        val urlLower = entry.url.lowercase(Locale.ROOT)

        /** 앞의 스킴과 `www.` 를 뗀 주소(소문자) — 인라인 완성과 앞머리 맞추기의 기준. */
        val display = displayUrl(entry.url).lowercase(Locale.ROOT)

        /** [display] 의 호스트 부분(포트 포함). */
        val host = hostOf(display)
        val titleLower = entry.title.lowercase(Locale.ROOT)
    }

    private val items = entries.map(::Item)

    /** 친 글 하나에 대한 답 — 인라인 완성과 목록. */
    class Result(val inline: Inline?, val rows: List<Suggestion>)

    fun query(input: String, now: Long, limit: Int = DEFAULT_LIMIT): Result {
        val top = inline(input, now)
        return Result(top, suggest(input, now, limit, top))
    }

    /** 빈 글로 초점을 얻었을 때의 목록 — 자주·최근 간 곳(같은 곳은 한 줄). 크롬의 '자주 간 곳' 자리. */
    fun top(now: Long, limit: Int = DEFAULT_LIMIT): List<Suggestion> {
        val out = ArrayList<Suggestion>()
        val seen = HashSet<String>()
        for (item in items.sortedByDescending { frecency(it.entry, now) }) {
            if (out.size >= limit) break
            if (!seen.add(sameKey(item.entry.url))) continue
            out += Suggestion(item.entry.url, item.entry.title, prettyUrl(item.entry.url), item.entry.bookmarked)
        }
        return out
    }

    /** 목록 — 많아야 [limit] 줄. 빈 글이면 빈 목록. [top] 이 있으면(인라인 완성) 그 주소가 맨 위다. */
    fun suggest(input: String, now: Long, limit: Int = DEFAULT_LIMIT, top: Inline? = inline(input, now)): List<Suggestion> {
        val terms = input.lowercase(Locale.ROOT).split(WHITESPACE).filter { it.isNotEmpty() }.take(MAX_TERMS)
        if (terms.isEmpty() || limit <= 0) return emptyList()
        val scored = ArrayList<Pair<Item, Double>>()
        for (item in items) {
            var weight = 0.0
            for ((i, term) in terms.withIndex()) {
                val w = termWeight(item, term, first = i == 0)
                if (w == 0.0) {
                    weight = 0.0
                    break
                }
                weight += w
            }
            if (weight > 0.0) scored += item to weight * frecency(item.entry, now)
        }
        scored.sortWith(compareByDescending<Pair<Item, Double>> { it.second }.thenByDescending { it.first.entry.lastVisit })
        val out = ArrayList<Suggestion>()
        // 같은 곳을 두 줄로 보이지 않는다 — 스킴·`www.`·조각(`#…`)·끝의 `/` 만 다른 주소는 하나(점수가 높은 것)로.
        val seen = HashSet<String>()
        // 인라인 완성이 갈 곳을 맨 위에 — Enter 가 갈 곳과 첫 줄이 같아야 한다.
        top?.let { t ->
            val item = items.firstOrNull { it.entry.url == t.url }
            out += Suggestion(t.url, item?.entry?.title.orEmpty(), prettyUrl(t.url), item?.entry?.bookmarked == true)
            seen += sameKey(t.url)
        }
        for ((item, _) in scored) {
            if (out.size >= limit) break
            if (!seen.add(sameKey(item.entry.url))) continue
            out += Suggestion(item.entry.url, item.entry.title, prettyUrl(item.entry.url), item.entry.bookmarked)
        }
        return out
    }

    /**
     * 인라인 완성. 친 글이 빈칸 없는 한 낱말이고 어떤 주소의 앞머리일 때만 — 없으면 null. 돌려주는 글은 **친 글 그대로**에 뒤를 이은 것이다
     * (대소문자를 바꾸지 않는다). 채울 것이 없으면(친 글이 이미 주소 전체면) null.
     */
    fun inline(input: String, now: Long): Inline? {
        if (input.isEmpty() || input.any { it.isWhitespace() || it.code >= 0x80 }) return null
        val q = input.lowercase(Locale.ROOT)
        val full = q.startsWith("http://") || q.startsWith("https://")
        val www = !full && q.startsWith("www.")
        // `/`·`?`·`:`(포트)를 치면 주소 전체로 채운다(파이어폭스). 스킴을 쳤으면 그 뒤만 본다.
        val path = (if (full) q.substringAfter("://") else q).any { it == '/' || it == '?' || it == ':' }
        // 후보 글 → (점수 합, 가장 나은 줄). 호스트까지만 친 동안은 같은 호스트의 여러 쪽이 그 호스트 하나로 모인다.
        val candidates = HashMap<String, Pair<Double, Item>>()
        for (item in items) {
            val e = item.entry
            if (e.typed <= 0 && !e.bookmarked && e.visits < MIN_VISITS_FOR_INLINE) continue
            val target = when {
                full && path -> item.urlLower.removeSuffixIfRoot()
                full -> item.urlLower.substringBefore("://") + "://" + hostOf(item.urlLower.substringAfter("://"))
                path -> item.display
                else -> item.host
            }.let { if (www) "www.$it" else it }
            if (!target.startsWith(q) || target.length <= q.length) continue
            val score = frecency(e, now)
            val before = candidates[target]
            candidates[target] = if (before == null) score to item else (before.first + score) to (if (score > frecency(before.second.entry, now)) item else before.second)
        }
        val best = candidates.entries.maxWithOrNull(compareBy<Map.Entry<String, Pair<Double, Item>>> { it.value.first }.thenBy { -it.key.length }) ?: return null
        val text = input + best.key.substring(q.length)
        val url = destination(best.key, best.value.second, full, path)
        return Inline(text, url)
    }

    /**
     * 인라인 완성의 갈 곳. 경로까지 채웠으면 그 줄의 주소. 호스트까지만 채웠으면 그 호스트의 첫 쪽이 기록에 있으면 그 주소(가장 자주 간 것 —
     * `#` 이나 끝의 `/` 만 다른 것도 첫 쪽으로 치고 조각은 뗀다), 없으면 가장 나은 줄의 스킴과 호스트(`www.` 그대로)로 지은 첫 쪽.
     */
    private fun destination(target: String, best: Item, full: Boolean, path: Boolean): String {
        if (full || path) return best.entry.url
        val host = best.host
        val root = items.filter { it.host == host && sameKey(it.entry.url) == host }.maxByOrNull { it.entry.visits + 2 * it.entry.typed }
        if (root != null) return root.entry.url.substringBefore('#')
        val url = best.entry.url
        return url.substringBefore("://", "https") + "://" + hostOf(url.substringAfter("://")) + "/"
    }

    private fun termWeight(item: Item, term: String, first: Boolean): Double {
        if (first && (item.display.startsWith(term) || item.urlLower.startsWith(term) || "www.${item.display}".startsWith(term))) return W_URL_PREFIX
        if (wordPrefix(item.titleLower, term) || wordPrefix(item.display, term)) return W_WORD_PREFIX
        // 한 글자는 낱말의 앞머리일 때만 — 가운데의 한 글자는 거의 모든 줄에 맞는다. 다만 한글·한자 한 글자는 뜻이 있다.
        if (term.length < 2 && term.all { it.code < 0x80 }) return 0.0
        if (item.titleLower.contains(term) || item.display.contains(term)) return W_CONTAINS
        return 0.0
    }

    companion object {
        /** 기록 줄의 수 — 키보드가 덮은 화면에 드는 만큼(크롬은 주소 줄을 다섯까지, 파이어폭스는 기록 셋). */
        const val DEFAULT_LIMIT = 6
        private const val MAX_TERMS = 6
        private const val MIN_VISITS_FOR_INLINE = 2
        private const val W_URL_PREFIX = 8.0
        private const val W_WORD_PREFIX = 3.0
        private const val W_CONTAINS = 1.0
        private const val DAY = 24 * 60 * 60 * 1000L
        private val WHITESPACE = Regex("\\s+")

        /**
         * 자주·최근 간 정도. 간 횟수와 친 횟수는 로그로 눌러(백 번 간 곳이 한 번 간 곳의 백 배가 되지 않게) 더하고, 마지막으로 간 때가
         * 오래될수록 줄인다 — 크롬·파이어폭스와 같은 마디(4일 1.0 · 14일 0.7 · 31일 0.5 · 90일 0.3 · 365일 0.1)를 크롬처럼 마디 사이는 곧게 잇는다.
         * (두 엔진은 방문마다의 시각을 열 번까지 보는데 이 기록에는 마지막으로 간 때뿐이다 — 모든 방문을 그때로 친다.)
         */
        fun frecency(e: Entry, now: Long): Double {
            val ageDays = (now - e.lastVisit).coerceAtLeast(0L) / DAY.toDouble()
            val base = 0.5 + ln(1.0 + e.visits) + 2.0 * ln(1.0 + e.typed) + (if (e.bookmarked) 1.0 else 0.0)
            return base * recency(ageDays)
        }

        /** 마지막으로 간 뒤 지난 날수에 따른 가중 — 마디 사이를 곧게 잇는다. */
        internal fun recency(ageDays: Double): Double {
            if (ageDays <= RECENCY_DAYS[0]) return RECENCY_WEIGHTS[0]
            for (i in 1 until RECENCY_DAYS.size) {
                if (ageDays <= RECENCY_DAYS[i]) {
                    val t = (ageDays - RECENCY_DAYS[i - 1]) / (RECENCY_DAYS[i] - RECENCY_DAYS[i - 1])
                    return RECENCY_WEIGHTS[i - 1] + t * (RECENCY_WEIGHTS[i] - RECENCY_WEIGHTS[i - 1])
                }
            }
            return RECENCY_WEIGHTS.last()
        }

        private val RECENCY_DAYS = doubleArrayOf(4.0, 14.0, 31.0, 90.0, 365.0)
        private val RECENCY_WEIGHTS = doubleArrayOf(1.0, 0.7, 0.5, 0.3, 0.1)

        /** 앞의 `http://`·`https://`·`www.` 를 떼고, 경로가 `/` 하나뿐이면 그것도 뗀다. 대소문자는 그대로. */
        fun displayUrl(url: String): String {
            var s = url.trim()
            val lower = s.lowercase(Locale.ROOT)
            s = when {
                lower.startsWith("https://") -> s.substring(8)
                lower.startsWith("http://") -> s.substring(7)
                else -> s
            }
            if (s.startsWith("www.", ignoreCase = true)) s = s.substring(4)
            val slash = s.indexOf('/')
            if (slash >= 0 && slash == s.length - 1) s = s.substring(0, slash)
            return s
        }

        /** 줄에 보일 주소 — [displayUrl] 에 퍼센트 부호화를 푼 것(엄격한 UTF-8 로 풀릴 때만 — 아니면 그대로). */
        fun prettyUrl(url: String): String {
            val d = displayUrl(url)
            return if ('%' in d) PercentCoding.decodeUtf8(d) ?: d else d
        }

        /** 같은 곳인지 가리는 열쇠 — [displayUrl] 에서 조각(`#…`)과 끝의 `/` 를 떼고 소문자로. */
        fun sameKey(url: String): String = displayUrl(url).substringBefore('#').trimEnd('/').lowercase(Locale.ROOT)

        /** [text] 안에 낱말의 앞머리로 [term] 이 있는가 — 맨 앞이거나 앞 글자가 글자·숫자가 아닌 자리. */
        internal fun wordPrefix(text: String, term: String): Boolean {
            var from = 0
            while (true) {
                val i = text.indexOf(term, from)
                if (i < 0) return false
                if (i == 0 || !text[i - 1].isLetterOrDigit()) return true
                from = i + 1
            }
        }

        /** 스킴을 뗀 주소의 호스트 부분(포트 포함) — 첫 `/`·`?`·`#` 앞까지. */
        private fun hostOf(rest: String): String =
            rest.substring(0, rest.indexOfAny(charArrayOf('/', '?', '#')).let { if (it < 0) rest.length else it })

        private fun String.removeSuffixIfRoot(): String {
            val rest = substringAfter("://", this)
            val slash = rest.indexOf('/')
            return if (slash >= 0 && slash == rest.length - 1) dropLast(1) else this
        }
    }
}
