package io.github.donggi.iroirobrowser.core

import java.util.Locale

/**
 * 주소 참조 풀기 — RFC 3986 5.2 의 꼴(브라우저와 hls.js 가 푸는 꼴). 재생목록·DASH 목록의 상대 주소를 이것으로 푼다.
 *
 * `java.net.URI.resolve` 는 RFC 2396 이라 브라우저와 갈린다 — 질의만 있는 참조(`?k=1`)에서 마지막 이름을 떼고(`/a/b/?k=1` — 브라우저는
 * `/a/b/c.m3u8?k=1`), 뿌리 위로 가는 `..` 를 남기고(`http://a/../g`), 길이 빈 바탕(`http://a` 와 `b`)에서 사이 빗금을 빠뜨린다. 그리고 그것이
 * 받지 않는 글자(`|`·`{`·`^` 따위)가 든 참조는 아예 풀지 못한다.
 * 여기서는 RFC 3986 부록 B 의 꼴로만 나누므로 글자를 따지지 않는다 — 그런 글자는 붙는 HTTP 가 다룬다.
 */
object UriRefs {

    /** [ref] 를 [base] 에 대어 푼다. [base] 에 스킴이 없으면(풀 바탕이 없다) [ref] 그대로. */
    fun resolve(base: String, ref: String): String {
        val r = split(ref)
        if (r.scheme != null) return compose(r.scheme, r.authority, removeDots(r.path), r.query, r.fragment)
        val b = split(base)
        val scheme = b.scheme ?: return ref
        val authority: String?
        val path: String
        val query: String?
        if (r.authority != null) {
            authority = r.authority
            path = removeDots(r.path)
            query = r.query
        } else {
            authority = b.authority
            if (r.path.isEmpty()) {
                path = b.path
                query = r.query ?: b.query
            } else {
                path = removeDots(if (r.path.startsWith("/")) r.path else merge(b, r.path))
                query = r.query
            }
        }
        return compose(scheme, authority, path, query, r.fragment)
    }

    /**
     * 주소의 출처 — `https://host` 꼴(스킴·호스트는 소문자, 사용자 정보는 떼고, 기본 포트는 뺀다). http·https 가 아니거나 호스트가 없거나 포트가
     * 숫자가 아니면 null.
     */
    fun origin(url: String): String? {
        val p = split(url)
        val scheme = p.scheme?.lowercase(Locale.ROOT) ?: return null
        if (scheme != "http" && scheme != "https") return null
        val authority = p.authority?.substringAfterLast('@') ?: return null
        val host: String
        val port: String?
        if (authority.startsWith("[")) {
            val close = authority.indexOf(']')
            if (close < 0) return null
            host = authority.substring(0, close + 1)
            val rest = authority.substring(close + 1)
            port = if (rest.startsWith(":")) rest.substring(1) else if (rest.isEmpty()) null else return null
        } else {
            val colon = authority.lastIndexOf(':')
            host = if (colon < 0) authority else authority.substring(0, colon)
            port = if (colon < 0) null else authority.substring(colon + 1)
        }
        if (host.isEmpty()) return null
        val number = when {
            port.isNullOrEmpty() -> null
            port.all { it in '0'..'9' } && port.length <= 5 -> port.toInt()
            else -> return null
        }
        val default = if (scheme == "https") 443 else 80
        val portPart = if (number == null || number == default) "" else ":$number"
        return "$scheme://${host.lowercase(Locale.ROOT)}$portPart"
    }

    private class Parts(val scheme: String?, val authority: String?, val path: String, val query: String?, val fragment: String?)

    /** RFC 3986 부록 B 의 꼴. 질의 기호는 글자 묶음으로 적었다(빗금 앞 표시 없이). */
    private val SHAPE = Regex("(([^:/?#]+):)?(//([^/?#]*))?([^?#]*)([?]([^#]*))?(#(.*))?")

    /** 스킴이 없는 꼴 — 스킴처럼 생겼으나 스킴의 글자가 아닌 앞머리(`a b:c`)는 길로 다시 본다. */
    private val NO_SCHEME = Regex("(//([^/?#]*))?([^?#]*)([?]([^#]*))?(#(.*))?")

    /** 스킴 — 글자로 시작하고 글자·숫자·`+`·`-`·`.` 만(RFC 3986 3.1, 브라우저도 그렇다). */
    private val SCHEME = Regex("[A-Za-z][A-Za-z0-9+.-]*")

    private fun split(s: String): Parts {
        val m = SHAPE.matchEntire(s) ?: return Parts(null, null, s, null, null)
        val g = m.groups
        val scheme = g[2]?.value
        if (scheme == null || SCHEME.matches(scheme)) {
            return Parts(scheme, g[4]?.value, g[5]?.value.orEmpty(), g[7]?.value, g[9]?.value)
        }
        val n = NO_SCHEME.matchEntire(s)?.groups ?: return Parts(null, null, s, null, null)
        return Parts(null, n[2]?.value, n[3]?.value.orEmpty(), n[5]?.value, n[7]?.value)
    }

    /** RFC 3986 5.2.3 — 바탕에 권한부가 있고 길이 비었으면 빗금 하나, 아니면 바탕 길의 마지막 빗금까지에 잇는다. */
    private fun merge(base: Parts, path: String): String {
        if (base.authority != null && base.path.isEmpty()) return "/$path"
        val slash = base.path.lastIndexOf('/')
        return if (slash < 0) path else base.path.substring(0, slash + 1) + path
    }

    /**
     * RFC 3986 5.2.4 — 점 마디를 걷는다. 나간 마디는 앞의 빗금과 함께 목록에 쌓고 `..` 는 마지막 하나를 뺀다(뿌리 위로는 가지 않는다).
     * 목록의 끝을 뺄 때 `removeAt(size - 1)` 을 쓴다 — `removeLast` 는 JDK 21 로 컴파일하면 안드로이드 12 에 없는 함수를 부른다.
     */
    private fun removeDots(path: String): String {
        if (!path.contains('.')) return path
        var input = path
        val out = ArrayList<String>()
        while (input.isNotEmpty()) {
            when {
                input.startsWith("../") -> input = input.substring(3)
                input.startsWith("./") -> input = input.substring(2)
                input.startsWith("/./") -> input = input.substring(2)
                input == "/." -> input = "/"
                input.startsWith("/../") -> {
                    input = input.substring(3)
                    if (out.isNotEmpty()) out.removeAt(out.size - 1)
                }
                input == "/.." -> {
                    input = "/"
                    if (out.isNotEmpty()) out.removeAt(out.size - 1)
                }
                input == "." || input == ".." -> input = ""
                else -> {
                    val next = input.indexOf('/', if (input.startsWith("/")) 1 else 0)
                    out += if (next < 0) input else input.substring(0, next)
                    input = if (next < 0) "" else input.substring(next)
                }
            }
        }
        return out.joinToString("")
    }

    /** RFC 3986 5.3. */
    private fun compose(scheme: String?, authority: String?, path: String, query: String?, fragment: String?): String = buildString {
        if (scheme != null) append(scheme).append(':')
        if (authority != null) append("//").append(authority)
        append(path)
        if (query != null) append('?').append(query)
        if (fragment != null) append('#').append(fragment)
    }
}
