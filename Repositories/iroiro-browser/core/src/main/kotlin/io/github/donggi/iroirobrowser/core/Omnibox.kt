package io.github.donggi.iroirobrowser.core

import java.net.IDN
import java.util.Locale

/**
 * 주소창에 친 글을 **주소**로 볼지 **검색어**로 볼지 가른다.
 *
 * - 스킴이 있으면: `http`·`https` 는 주소, `about:`·`data:` 는 주소, `javascript:` 는 **검색어**(주소창에서 스크립트를 돌리지 않는다).
 * - `magnet:` 은 **토렌트**(사용자 결정 ⑳, 2026-10-09) — 앞 판은 검색어가 되어 info-hash 와 트래커가 검색 엔진에 갔다. 빈칸이 들었거나 깨진
 *   마그넷이어도 검색어로 돌리지 않는다(그 글을 읽고 받지 않을 까닭을 말하는 것은 토렌트 쪽이다).
 * - 스킴이 없으면 `호스트[:포트][/…]` 꼴인지 본다. 점이 있고 마지막 낱말이 최상위 도메인 꼴(글자 2~63 개 또는 `xn--`)이면 주소,
 *   `localhost`·IP 주소도 주소, 그 밖(낱말 하나, 빈칸이 든 글, `@` 가 든 글)은 검색어다.
 * - 스킴 없이 친 도메인은 **https** 로 연다(프록시 너머의 평문 구간을 줄인다). `localhost`·IP 는 http — 공유기 관리 쪽 같은 곳은 대개
 *   평문이다. 국제화 도메인(`한국.kr`)은 퓨니코드로 바꾼다.
 */
object Omnibox {

    sealed interface Target {
        data class Url(val url: String) : Target
        data class Search(val query: String) : Target

        /** 마그넷 주소 — 쪽으로 열지도 검색하지도 않고 토렌트로 넘긴다. */
        data class Magnet(val uri: String) : Target
    }

    /** 열 주소. 빈 글이면 null. 마그넷도 null 이다 — 열 쪽이 없다(부르는 쪽이 [classify] 로 먼저 가른다). */
    fun resolve(input: String, engine: SearchEngine): String? = when (val target = classify(input)) {
        null -> null
        is Target.Url -> target.url
        is Target.Search -> engine.searchUrl(target.query)
        is Target.Magnet -> null
    }

    fun classify(raw: String): Target? {
        val input = raw.trim()
        if (input.isEmpty()) return null
        val scheme = SCHEME.find(input)?.groupValues?.get(1)?.lowercase(Locale.ROOT)
        when (scheme) {
            "http", "https" -> return if (input.any { it.isWhitespace() }) Target.Search(input) else web(input, scheme)
            "about", "data" -> return Target.Url(input)
            "javascript" -> return Target.Search(input)
            "magnet" -> return Target.Magnet(input)
        }
        if (input.any { it.isWhitespace() }) return Target.Search(input)
        return schemeless(input) ?: Target.Search(input)
    }

    /** `http(s):` 로 시작한 것. `https:example.com` 처럼 `//` 가 빠졌으면 채운다. 호스트가 없으면 검색어. */
    private fun web(input: String, scheme: String): Target {
        val rest = input.substring(scheme.length + 1).trimStart('/')
        if (rest.isEmpty() || rest.startsWith('?') || rest.startsWith('#')) return Target.Search(input)
        return Target.Url("$scheme://$rest")
    }

    private fun schemeless(input: String): Target? {
        val end = input.indexOfAny(charArrayOf('/', '?', '#')).let { if (it < 0) input.length else it }
        val authority = input.substring(0, end)
        val rest = input.substring(end)
        if (authority.isEmpty() || '@' in authority) return null
        val (rawHost, port) = splitHostPort(authority) ?: return null
        if (port != null && port !in 1..65535) return null
        val portPart = port?.let { ":$it" } ?: ""
        if (IpLiterals.isIpv4(rawHost) || isBracketedIpv6(rawHost) || rawHost.equals("localhost", ignoreCase = true)) {
            return Target.Url("http://${rawHost.lowercase(Locale.ROOT)}$portPart$rest")
        }
        val host = toAsciiHost(rawHost.trimEnd('.')) ?: return null
        val labels = host.split('.')
        if (labels.size < 2 || labels.any { !LABEL.matches(it) }) return null
        if (!TLD.matches(labels.last())) return null
        return Target.Url("https://$host$portPart$rest")
    }

    /** `호스트:포트` 를 가른다. IPv6 는 대괄호로만 받는다. */
    private fun splitHostPort(authority: String): Pair<String, Int?>? {
        if (authority.startsWith('[')) {
            val close = authority.indexOf(']')
            if (close < 0) return null
            val host = authority.substring(0, close + 1)
            val after = authority.substring(close + 1)
            return when {
                after.isEmpty() -> host to null
                after.startsWith(':') -> after.substring(1).toIntOrNull()?.let { host to it }
                else -> null
            }
        }
        val colon = authority.lastIndexOf(':')
        if (colon < 0) return authority to null
        if (authority.indexOf(':') != colon) return null
        val port = authority.substring(colon + 1).toIntOrNull() ?: return null
        return authority.substring(0, colon) to port
    }

    private fun toAsciiHost(host: String): String? = try {
        IDN.toASCII(host, IDN.ALLOW_UNASSIGNED).lowercase(Locale.ROOT)
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun isBracketedIpv6(host: String): Boolean =
        host.length > 2 && host.first() == '[' && host.last() == ']' && IpLiterals.isIpv6(host.substring(1, host.length - 1))

    private val SCHEME = Regex("""^([a-zA-Z][a-zA-Z0-9+.\-]*):""")
    private val LABEL = Regex("""[a-z0-9_]([a-z0-9_\-]{0,61}[a-z0-9_])?""")
    private val TLD = Regex("""[a-z]{2,63}|xn--[a-z0-9\-]{1,59}""")
}
