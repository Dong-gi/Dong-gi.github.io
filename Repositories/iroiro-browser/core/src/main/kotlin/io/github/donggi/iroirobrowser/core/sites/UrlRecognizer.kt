package io.github.donggi.iroirobrowser.core.sites

import java.util.Locale

/**
 * [SiteUrls.recognize] 의 몸. 주소를 `java.net.URI` 에 맡기지 않고 손으로 가른다 — 받을 꼴이 좁고, 맡기면 그 해석기가 너그럽게 읽는 것
 * (사용자 정보·백슬래시·퍼센트로 쓴 호스트)을 우리가 하나하나 다시 막아야 한다. **모르는 꼴은 모두 null** — 메뉴에 '받기' 가 안 뜰 뿐이다.
 *
 * - 스킴은 http·https(대소문자 무시), `//` 뒤의 권한부에 사용자 정보(`@`)·백슬래시·퍼센트·빈칸·비ASCII 가 있으면 null.
 * - 호스트는 정확히 `pixiv.net`·`www.pixiv.net` / `hitomi.la`(대소문자 무시) — `pixiv.net.evil.com`·끝의 점·IPv6 는 null. 포트는 없거나 그 스킴의
 *   기본값만.
 * - 질의·조각은 경로를 가른 뒤 버린다(pixiv 의 옛 `member.php` 류만 질의를 읽는다). 경로의 퍼센트는 풀지 않는다 — 번호는 맨 숫자여야 한다.
 */
internal object UrlRecognizer {

    /** 이보다 긴 주소는 보지 않는다(주소창·길게 누르기에서 오는 글이다 — 크기를 묶어 둔다). */
    private const val MAX_URL_CHARS = 8 * 1024

    private val HITOMI_KINDS = setOf("galleries", "reader", "doujinshi", "manga", "cg", "gamecg", "imageset", "anime")
    private val PIXIV_USER_TABS = setOf("illustrations", "manga", "artworks")

    fun recognize(url: String): SiteTarget? {
        if (url.length > MAX_URL_CHARS) return null
        val parts = split(url.trim { it <= ' ' }) ?: return null
        return when (parts.host) {
            "pixiv.net", "www.pixiv.net" -> pixiv(parts)
            "hitomi.la" -> hitomi(parts)
            else -> null
        }
    }

    private class Parts(val host: String, val path: String, val query: String?)

    private fun split(url: String): Parts? {
        val colon = url.indexOf(':')
        if (colon <= 0) return null
        val defaultPort = when (url.substring(0, colon).lowercase(Locale.ROOT)) {
            "https" -> 443
            "http" -> 80
            else -> return null
        }
        if (!url.startsWith("//", colon + 1)) return null
        val authorityStart = colon + 3
        var end = authorityStart
        while (end < url.length && url[end] != '/' && url[end] != '?' && url[end] != '#') end++
        val authority = url.substring(authorityStart, end)
        if (authority.isEmpty()) return null
        for (c in authority) {
            if (c == '@' || c == '\\' || c == '%' || c == '[' || c == ']' || c <= ' ' || c.code >= 0x7F) return null
        }
        val portColon = authority.indexOf(':')
        val host = (if (portColon >= 0) authority.substring(0, portColon) else authority).lowercase(Locale.ROOT)
        if (portColon >= 0) {
            val port = authority.substring(portColon + 1)
            // 빈 포트(`host:`)는 기본 포트다(URL 표준). 그 밖에는 기본 포트만 — 다른 포트의 pixiv 는 pixiv 가 아니다.
            if (port.isNotEmpty() && (port.length > 5 || !port.all { it in '0'..'9' } || port.toInt() != defaultPort)) return null
        }
        val rest = url.substring(end)
        val hash = rest.indexOf('#')
        val beforeFragment = if (hash >= 0) rest.substring(0, hash) else rest
        val question = beforeFragment.indexOf('?')
        val path = if (question >= 0) beforeFragment.substring(0, question) else beforeFragment
        val query = if (question >= 0) beforeFragment.substring(question + 1) else null
        if (path.any { it <= ' ' || it == '\\' }) return null
        return Parts(host, path, query)
    }

    /** `/a/b/` → [a, b]. 가운데의 빈 성분(`//`)은 null. 끝의 `/` 는 [trailingSlash] 일 때만 받는다. */
    private fun segments(path: String, trailingSlash: Boolean): List<String>? {
        if (path.isEmpty() || path == "/") return emptyList()
        if (!path.startsWith('/')) return null
        var body = path.substring(1)
        if (body.endsWith('/')) {
            if (!trailingSlash) return null
            body = body.substring(0, body.length - 1)
        }
        val parts = body.split('/')
        if (parts.any { it.isEmpty() }) return null
        return parts
    }

    private fun pixiv(parts: Parts): SiteTarget? {
        val segs = segments(parts.path, trailingSlash = true) ?: return null
        if (segs.isEmpty()) return null
        if (segs.size == 1) {
            when (segs[0].lowercase(Locale.ROOT)) {
                // 옛 주소. member_illust.php 는 illust_id 가 있으면 작품, id 만 있으면 그 작가의 작품 목록이다.
                "member.php" -> return queryId(parts.query, "id")?.let { SiteTarget.PixivUser(it) }
                "member_illust.php" -> {
                    if (hasParam(parts.query, "illust_id")) return queryId(parts.query, "illust_id")?.let { SiteTarget.PixivArtwork(it) }
                    return queryId(parts.query, "id")?.let { SiteTarget.PixivUser(it) }
                }
            }
        }
        // 언어 머리(`/en/`·`/ko/`·`/zh-tw/` …)는 작품·작가 주소 앞에서만 뗀다.
        val rest = if (segs.size >= 2 && isLanguage(segs[0])) segs.subList(1, segs.size) else segs
        val head = rest[0].lowercase(Locale.ROOT)
        return when {
            head == "artworks" && rest.size == 2 -> SiteResponses.positiveId(rest[1])?.let { SiteTarget.PixivArtwork(it) }
            head == "users" && rest.size == 2 -> SiteResponses.positiveId(rest[1])?.let { SiteTarget.PixivUser(it) }
            head == "users" && rest.size == 3 && rest[2].lowercase(Locale.ROOT) in PIXIV_USER_TABS ->
                SiteResponses.positiveId(rest[1])?.let { SiteTarget.PixivUser(it) }
            else -> null
        }
    }

    /** `en`·`ja`·`ko`·`zh` 같은 두 글자, 또는 `zh-tw`·`zh_TW` 꼴. */
    private fun isLanguage(segment: String): Boolean {
        val main = segment.substringBefore('-').substringBefore('_')
        if (main.length != 2 || !main.all { it in 'a'..'z' || it in 'A'..'Z' }) return false
        if (main.length == segment.length) return true
        val region = segment.substring(3)
        return segment[2] in "-_" && region.length in 2..4 && region.all { it in 'a'..'z' || it in 'A'..'Z' }
    }

    private fun params(query: String?): List<Pair<String, String>> {
        if (query.isNullOrEmpty()) return emptyList()
        return query.split('&').filter { it.isNotEmpty() }.map { it.substringBefore('=') to it.substringAfter('=', "") }
    }

    private fun hasParam(query: String?, name: String): Boolean = params(query).any { it.first == name }

    /** 그 이름의 값이 꼭 하나이고 양의 정수일 때 — 둘이면(어느 쪽을 서버가 읽는지 모른다) null. */
    private fun queryId(query: String?, name: String): Long? {
        val values = params(query).filter { it.first == name }
        if (values.size != 1) return null
        return SiteResponses.positiveId(values[0].second)
    }

    /**
     * 첫 성분이 갤러리 종류([HITOMI_KINDS])이고 둘째가 `….html` — `.html` 바로 앞의 숫자가 번호인데, 그 앞이 `/`(숫자뿐인 이름)나 `-`(제목-번호)
     * 여야 한다(`title2023.html` 처럼 숫자가 낱말에 붙은 것은 번호인지 모른다). 태그·검색·목록 쪽은 첫 성분이 달라 null.
     */
    private fun hitomi(parts: Parts): SiteTarget? {
        val segs = segments(parts.path, trailingSlash = false) ?: return null
        if (segs.size != 2 || segs[0].lowercase(Locale.ROOT) !in HITOMI_KINDS) return null
        val file = segs[1]
        if (file.length <= 5 || !file.endsWith(".html", ignoreCase = true)) return null
        val stem = file.substring(0, file.length - 5)
        var digits = stem.length
        while (digits > 0 && stem[digits - 1] in '0'..'9') digits--
        if (digits == stem.length) return null
        if (digits > 0 && stem[digits - 1] != '-') return null
        return SiteResponses.positiveId(stem.substring(digits))?.let { SiteTarget.HitomiGallery(it) }
    }
}
