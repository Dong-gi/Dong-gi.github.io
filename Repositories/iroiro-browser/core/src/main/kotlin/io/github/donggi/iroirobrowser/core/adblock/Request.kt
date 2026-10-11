package io.github.donggi.iroirobrowser.core.adblock

import java.util.Locale

/**
 * 막을지 볼 요청 하나 — 주소, 그 요청을 낸 쪽(문서)의 호스트, 요청의 종류([RequestType] 마스크). 주소를 한 번만 소문자로 바꾸고 호스트의 자리와
 * 제1자/제3자를 미리 정한다(규칙 수십 개와 견주는 동안 다시 하지 않게).
 */
class Request(val url: String, documentHost: String, val types: Int) {

    val urlLower: String = url.lowercase(Locale.ROOT)
    val hostStart: Int
    val hostEnd: Int
    val host: String
    val documentHost: String = documentHost.lowercase(Locale.ROOT)
    val thirdParty: Boolean

    init {
        val range = hostRange(urlLower)
        hostStart = range.first
        hostEnd = range.last + 1
        host = if (hostEnd > hostStart) urlLower.substring(hostStart, hostEnd) else ""
        thirdParty = Domains.isThirdParty(host, this.documentHost)
    }

    /** 주소의 낱말들(글자·숫자·`%` 의 묶음) — 낱말 색인을 찾는다. */
    val tokens: List<String> by lazy(LazyThreadSafetyMode.NONE) {
        val out = ArrayList<String>(16)
        var i = 0
        val s = urlLower
        while (i < s.length) {
            if (!FilterParser.isTokenChar(s[i])) {
                i++
                continue
            }
            val start = i
            while (i < s.length && FilterParser.isTokenChar(s[i])) i++
            if (i - start >= 2) out += s.substring(start, i)
        }
        out
    }

    companion object {
        /** [url](소문자) 안의 호스트 자리 — 사용자 정보(`user@`)와 포트는 뺀다. 없으면 빈 범위. */
        fun hostRange(url: String): IntRange {
            val scheme = url.indexOf("://")
            if (scheme < 0) return IntRange.EMPTY
            val start = scheme + 3
            var end = url.length
            for (i in start until url.length) {
                val c = url[i]
                if (c == '/' || c == '?' || c == '#') {
                    end = i
                    break
                }
            }
            var hostStart = start
            val at = url.lastIndexOf('@', end - 1)
            if (at >= start) hostStart = at + 1
            var hostEnd = end
            if (hostStart < end && url[hostStart] == '[') {
                val close = url.indexOf(']', hostStart)
                if (close in hostStart until end) hostEnd = close + 1
            } else {
                val colon = url.indexOf(':', hostStart)
                if (colon in hostStart until end) hostEnd = colon
            }
            return if (hostEnd > hostStart) hostStart until hostEnd else IntRange.EMPTY
        }

        /** 주소의 호스트(소문자). */
        fun hostOf(url: String): String {
            val lower = url.lowercase(Locale.ROOT)
            val r = hostRange(lower)
            return if (r.isEmpty()) "" else lower.substring(r.first, r.last + 1)
        }
    }
}
