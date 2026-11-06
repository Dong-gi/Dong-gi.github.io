package io.github.donggi.iroirobrowser.core.media

import io.github.donggi.iroirobrowser.core.UriRefs
import io.github.donggi.iroirobrowser.core.adblock.Domains
import io.github.donggi.iroirobrowser.core.adblock.Request
import java.util.Locale

/**
 * 쪽이 낸 하위 요청의 머리 — 탭마다 **메모리에만** 잠깐 쥔다(2026-10-16 — m3u8 열쇠 오류 고침). 열쇠를 받을 때 쪽의 재생기가 그 열쇠에 실제로
 * 보낸 Referer 와 스크립트가 단 머리(hls.js 의 `xhrSetup` 따위)를 그대로 싣는다. 앞 판은 맨 위 쪽의 주소로 Referer 를 지어 모든 요청에 실었고(재생기가
 * 다른 사이트의 틀 안에 있으면 그 틀의 출처와 다르다) Origin 도 스크립트의 머리도 싣지 못했다 — 그런 열쇠 서버는 '200 + 열쇠 아닌 것' 을 준다.
 *
 * WebView 는 `shouldInterceptRequest` 에 그 요청의 머리를 주고 Referer 도 담아 준다(크로미움 `aw_proxying_url_loader_factory.cc` — 소스로 읽었다,
 * 기기의 판에서는 재지 않았다). 쿠키·Origin·Sec-Fetch 는 그 뒤에 망이 붙여 여기에 없다 — Origin 은 Referer 의 출처로 짓는다([Replay.origin]).
 * 열쇠 요청이 재생목록을 읽기 전에 지나가므로 무엇이 열쇠인지 모르는 채 최근 요청을 상한을 두고 쥔다([CAPACITY]·[MAX_AGE_MS]·[MAX_CHARS]).
 *
 * **값은 토큰일 수 있다**(`Authorization`·주소의 질의) — 기록·디스크·로그에 쓰지 않는다. 탭을 닫으면([clear]) 다른 사이트의 문서가 커밋되면
 * ([committed]) 버린다.
 *
 * 스레드 — WebView 의 스레드가 적고([record]) 화면이 커밋을 알리고([committed]) 내려받기 일꾼이 읽는다([replayFor]). 모두 이 객체의 자물쇠 안에서.
 */
class PageRequests(
    private val capacity: Int = CAPACITY,
    private val maxAgeMs: Long = MAX_AGE_MS,
    private val maxChars: Int = MAX_CHARS,
) {
    /** 쪽의 요청이 무엇과 맞았는가 — 실패 글의 갈래([HlsKeys.Report])에도 싣는다. */
    enum class Match {
        /** 열쇠 주소 그대로 — 쪽이 그 주소를 청했다. */
        EXACT,

        /** 쪽이 같은 길에 질의를 더한 주소를 청했다(스크립트가 붙인 토큰) — 그 주소를 청한다. */
        PAGE_QUERY,

        /** 같은 길·다른 질의 — 우리 주소에 쪽의 머리만 싣는다. */
        SAME_PATH,

        /** 열쇠 요청은 못 찾고 그 재생목록의 요청만 찾았다 — Referer(와 Origin)만 싣는다. */
        PLAYLIST,
    }

    private class Entry(val url: String, val key: String, val path: String, val headers: Map<String, String>, val referer: String?, val at: Long) {
        /** 쥔 글자 수(어림 — 주소에서 지은 열쇠·길도 센다). [MAX_CHARS] 를 이것으로 센다. */
        val chars: Int = url.length + key.length + path.length + (referer?.length ?: 0) + headers.entries.sumOf { it.key.length + it.value.length }
    }

    /** 열쇠를 쪽처럼 청하는 법. 값(머리·Referer)은 메모리에만 — 이 객체를 디스크·기록에 쓰지 않는다. */
    class Replay internal constructor(
        /** 청할 주소 — [Match.PAGE_QUERY] 면 쪽이 청한 주소, 아니면 열쇠 주소. */
        val url: String,
        /** 쪽이 보낸 Referer — null 이면 보내지 않는다(쪽이 보내지 않았다). */
        val referer: String?,
        val match: Match,
        private val headers: Map<String, String>,
        /** 그 머리를 보낸 요청의 출처 — 스크립트가 단 머리는 이 출처로만 싣는다. */
        private val headerOrigin: String?,
    ) {
        /** [url] 과 다른 출처의 Referer 면 그 출처(브라우저는 다른 출처로의 XHR 에 Origin 을 단다). 같은 출처거나 Referer 가 없으면 null. */
        val origin: String? = referer?.let { UriRefs.origin(it) }?.takeIf { it != UriRefs.origin(url) }

        /** 실을 스크립트의 머리 수 — 이름·값은 내놓지 않는다(진단·시험용). */
        val headerCount: Int get() = headers.size

        /**
         * [hop] 에 실을 머리 — 스크립트가 단 머리는 쪽이 보낸 요청과 **같은 출처**에만 싣는다(넘겨주기로 다른 곳에 가면 싣지 않는다 — 인증 토큰이 새지
         * 않게. 브라우저도 다른 출처로 넘겨줄 때 `Authorization` 을 뗀다). Origin 은 [origin] 이 있으면 홉마다. Referer 는 부르는 쪽이 싣는다.
         */
        fun headersFor(hop: String): Map<String, String> = buildMap {
            if (headerOrigin != null && UriRefs.origin(hop) == headerOrigin) putAll(headers)
            origin?.let { put("Origin", it) }
        }
    }

    private val lock = Any()
    private val entries = LinkedHashMap<String, Entry>()
    private var site: String? = null
    private var chars = 0

    /** 쥔 요청 수(시험용). */
    val size: Int get() = synchronized(lock) { entries.size }

    /**
     * 하위 요청 하나를 적는다 — 쥘 만한 것만([shouldRecord]), 머리는 고른 것만([select]). 수([CAPACITY])나 글자 수([MAX_CHARS])를 넘으면 가장
     * 오래된 것부터 버린다. WebView 의 스레드.
     */
    fun record(url: String, method: String?, headers: Map<String, String>?, nowMs: Long) {
        val h = headers.orEmpty()
        if (!shouldRecord(url, method, valueOf(h, "Accept"))) return
        val clean = url.substringBefore('#')
        val entry = Entry(clean, keyOf(clean), pathOf(clean), select(h), refererOf(h), nowMs)
        synchronized(lock) {
            entries.remove(entry.key)?.let { chars -= it.chars }
            entries[entry.key] = entry
            chars += entry.chars
            val eldest = entries.values.iterator()
            while ((entries.size > capacity || chars > maxChars) && eldest.hasNext()) {
                chars -= eldest.next().chars
                eldest.remove()
            }
        }
    }

    /**
     * 주 틀에 새 문서가 커밋됐다 — 다른 사이트(등록 도메인)면 쥐던 것을 버린다. 그 탐색이 시작된 때([navigationStartMs] — [record] 와 같은 시계)를
     * 알면 그 뒤에 적은 것은 새 문서의 것이라 남긴다(커밋 알림은 새 문서의 첫 요청보다 늦게 올 수 있다 — FoundMediaLog 와 같은 까닭).
     */
    fun committed(url: String, navigationStartMs: Long?) {
        val next = siteOf(url)
        synchronized(lock) {
            if (next == site) return
            site = next
            if (navigationStartMs == null) entries.clear() else entries.values.removeAll { it.at < navigationStartMs }
            chars = entries.values.sumOf { it.chars }
        }
    }

    /** 모두 버린다 — 탭을 닫았다. */
    fun clear() {
        synchronized(lock) {
            entries.clear()
            chars = 0
            site = null
        }
    }

    /**
     * [keyUrl](재생목록이 가리키는 열쇠)을 쪽처럼 청하는 법. 찾는 차례 — 그 주소 그대로 → 같은 길에 쪽이 질의를 더한 주소(하나일 때만 — 여럿이면 어느
     * 것인지 모른다) → 같은 길의 다른 질의(우리 주소에 머리만) → 그 재생목록([playlistUrl])의 요청(Referer 만). 없으면 null(앞 판처럼 받는다).
     */
    fun replayFor(keyUrl: String, playlistUrl: String?, nowMs: Long): Replay? {
        val clean = keyUrl.substringBefore('#')
        val key = keyOf(clean)
        val path = pathOf(clean)
        val all = synchronized(lock) {
            if (entries.values.removeAll { nowMs - it.at > maxAgeMs }) chars = entries.values.sumOf { it.chars }
            entries.values.toList()
        }
        all.lastOrNull { it.key == key }?.let { return replay(it, clean, Match.EXACT) }
        val samePath = all.filter { it.path == path }
        if (samePath.isNotEmpty()) {
            val added = samePath.filter { addsQuery(it.url, clean) }
            if (added.isNotEmpty() && added.all { it.key == added.last().key }) return replay(added.last(), added.last().url, Match.PAGE_QUERY)
            return replay(samePath.last(), clean, Match.SAME_PATH)
        }
        val playlist = playlistUrl?.substringBefore('#') ?: return null
        val p = all.lastOrNull { it.key == keyOf(playlist) } ?: all.lastOrNull { it.path == pathOf(playlist) } ?: return null
        return Replay(clean, p.referer, Match.PLAYLIST, emptyMap(), null)
    }

    private fun replay(e: Entry, url: String, match: Match) = Replay(url, e.referer, match, e.headers, UriRefs.origin(e.url))

    companion object {
        /** 쥐는 요청 수 — 정적 자원·조각은 쥐지 않으므로 XHR·fetch 만 센다(열쇠는 재생을 시작할 때 한 번 오고 그 뒤로 밀리지 않는다). */
        const val CAPACITY = 128

        /** 쥐는 시간 — 쪽을 열고 재생을 시작한 뒤 '찾은 미디어' 를 고르기까지. */
        const val MAX_AGE_MS = 30 * 60_000L

        /**
         * 쥐는 글자 수 — 쪽이 머리를 부풀려(머리 24개 × 4 KB) 앱의 메모리를 쓰지 못하게(탭마다 1 MB 남짓 — UTF-16). 보통의 XHR 은 머리 대여섯에 1~2천
         * 글자라 128개를 채워도 이 안이다(어림 — 재지 않았다). 접은 탭도 쥔 채 있다.
         */
        const val MAX_CHARS = 512 * 1024

        const val MAX_URL = 8 * 1024
        const val MAX_HEADERS = 24
        const val MAX_NAME = 64
        const val MAX_VALUE = 4 * 1024

        /**
         * 쥐지 않는 머리(소문자) — 까닭은 CLAUDE.md '찾은 미디어·HLS·DASH' 의 표. 쿠키는 앱이 홉마다 CookieManager 에서 싣는다. 연결·조건·부분 요청의
         * 머리는 HttpURLConnection 이 정하거나 열쇠에 206·304 를 부른다. 압축은 플랫폼 HTTP 가 스스로 청하고 푼다(옮기면 br 따위가 와서 풀지 못한다).
         * User-Agent 는 앱이 같은 값을 싣는다. X-Requested-With 는 WebView 의 표지다(이 앱은 비운다). 프록시 계정은 중계의 것이다. Referer 는 따로
         * 쥐고 Origin 은 그것에서 짓는다.
         */
        val DROPPED: Set<String> = setOf(
            "cookie", "cookie2",
            "host", "connection", "keep-alive", "proxy-authorization", "proxy-connection", "te", "trailer", "transfer-encoding",
            "upgrade", "expect", "content-length",
            "range", "if-range", "if-match", "if-none-match", "if-modified-since", "if-unmodified-since",
            "accept-encoding", "user-agent", "x-requested-with", "origin", "referer",
        )

        /** 쥐지 않는 요청의 `Accept` 머리 — 그림·스타일·문서(틀)·미디어 요소. 열쇠는 XHR·fetch 라 별표 둘이거나 비었다. */
        private val SKIPPED_ACCEPT = listOf("image/", "text/css", "text/html", "application/xhtml", "video/", "audio/")

        /** 쥐지 않는 확장자 — 그림·스타일·스크립트·글꼴과 조각(쪽마다 수백이라 열쇠를 밀어낸다). 재생목록(`m3u8`)은 쥔다(Referer 를 빌린다). */
        private val SKIPPED_EXTENSIONS = setOf(
            "png", "jpg", "jpeg", "gif", "webp", "avif", "svg", "ico", "bmp", "css", "js", "mjs", "woff", "woff2", "ttf", "otf", "eot", "map",
            "ts", "m4s", "m4v", "m4a", "mp4", "aac", "mp3", "webm", "vtt", "cmfv", "cmfa",
        )

        /** 토큰의 글자 가운데 글자·숫자가 아닌 것(RFC 9110 5.6.2 — 달러와 역따옴표는 뺐다, 그런 이름은 쥐지 않는다). */
        private const val TOKEN_MARKS = "!#%&'*+-.^_|~"

        /** 쥘 만한 요청인가 — GET, http(s), 8 KB 이하의 주소, 그림·스타일·문서·미디어 요소가 아니고 정적 자원·조각의 확장자가 아닌 것. */
        fun shouldRecord(url: String, method: String?, accept: String?): Boolean {
            if (method != null && !method.equals("GET", ignoreCase = true)) return false
            if (url.length > MAX_URL) return false
            val lower = url.lowercase(Locale.ROOT)
            if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
            val a = accept?.trim()?.lowercase(Locale.ROOT).orEmpty()
            if (SKIPPED_ACCEPT.any { a.startsWith(it) }) return false
            return MediaSniffer.extension(url) !in SKIPPED_EXTENSIONS
        }

        /** 실을 머리 — [DROPPED] 밖이고, 이름은 토큰 글자 64자까지, 값은 출력할 수 있는 ASCII(와 탭) 4 KB 까지, [MAX_HEADERS] 개까지. */
        fun select(headers: Map<String, String>): Map<String, String> {
            val out = LinkedHashMap<String, String>()
            for ((name, value) in headers) {
                if (out.size >= MAX_HEADERS) break
                if (name.lowercase(Locale.ROOT) in DROPPED) continue
                if (!isToken(name) || !isFieldValue(value)) continue
                out[name] = value
            }
            return out
        }

        /** 쪽이 보낸 Referer — http(s) 주소이고 머리의 값으로 쓸 수 있을 때만. */
        fun refererOf(headers: Map<String, String>): String? {
            val v = valueOf(headers, "Referer")?.trim() ?: return null
            if (v.length > MAX_URL || !isFieldValue(v)) return null
            return v.takeIf { UriRefs.origin(it) != null }
        }

        private fun valueOf(headers: Map<String, String>, name: String): String? =
            headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

        private fun isToken(name: String) =
            name.isNotEmpty() && name.length <= MAX_NAME && name.all { c -> c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c in TOKEN_MARKS }

        private fun isFieldValue(value: String) = value.length <= MAX_VALUE && value.all { c -> c.code == 9 || c.code in 0x20..0x7E }

        /** 견주는 열쇠 — 출처(소문자·기본 포트 없이)와 그 뒤(길·질의). 조각은 이미 뗐다. */
        private fun keyOf(url: String): String = (UriRefs.origin(url) ?: url.substringBefore("//").lowercase(Locale.ROOT)) + rest(url)

        /** 견주는 길 — [keyOf] 에서 질의를 뗀 것. */
        private fun pathOf(url: String): String = keyOf(url).substringBefore('?')

        /** 권한부 뒤(길·질의) — 길이 비었으면 빗금 하나. */
        private fun rest(url: String): String {
            val afterScheme = url.substringAfter("://", "")
            val cut = afterScheme.indexOfFirst { it == '/' || it == '?' || it == '#' }
            if (cut < 0) return "/"
            val tail = afterScheme.substring(cut)
            return if (tail.startsWith("/")) tail else "/$tail"
        }

        /** [recorded] 의 질의가 [ours] 의 질의를 모두 담고 더 있는가 — 쪽이 스크립트로 질의를 더했다. */
        private fun addsQuery(recorded: String, ours: String): Boolean {
            val r = queryOf(recorded)
            val o = queryOf(ours)
            if (r.size <= o.size) return false
            val pool = r.toMutableList()
            for (p in o) if (!pool.remove(p)) return false
            return true
        }

        private fun queryOf(url: String): List<String> = url.substringAfter('?', "").split('&').filter { it.isNotEmpty() }

        /** 사이트(등록 도메인) — 광고 차단·팝업 허용과 같은 어림([Domains]). */
        private fun siteOf(url: String): String = Domains.registrable(Request.hostOf(url))
    }
}
