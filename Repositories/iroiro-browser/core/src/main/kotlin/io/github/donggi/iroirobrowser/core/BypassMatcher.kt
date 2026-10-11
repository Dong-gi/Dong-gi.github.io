package io.github.donggi.iroirobrowser.core

import java.net.URL

/**
 * 앱이 스스로 내는 HTTP(내려받기·찾은 미디어 미리 읽기·광고 목록)가 **프록시를 거치지 않고 바로** 갈 주소를 가린다 — WebView 에 넘기는 예외
 * 규칙([Bypass.rules] 와, [Bypass.simpleHostnames] 면 `<local>`)을 크로미움의 `net::ProxyBypassRules` 처럼 읽는다. 사용자 결정(2026-10-10):
 * '앱 쪽 HTTP 요청(내려받기·광고 목록 등)도 사설망 예외 규칙을 따르게'.
 *
 * **안전의 약속 — 크로미움이 프록시로 보낼 주소를 바로 보내지 않는다.** 거꾸로(크로미움은 바로 여는데 여기는 프록시)는 된다 — 그 받기가 예외를
 * 따르기 전처럼 실패할 뿐이다(fail-closed). 그래서 확실하지 않은 것은 모두 프록시다. 이름을 풀지 않는다(DNS 를 묻지 않는다). 견주는 호스트는
 * 자바가 실제로 붙을 호스트(`URL.getHost`)다 — 바로 붙는 곳이 언제나 예외에 든 곳이게.
 *
 * 크로미움과 같게 읽는 것:
 * - 호스트 무늬 — `example.com` 은 그 이름 하나, `*.example.com` 은 `.example.com` 으로 끝나는 이름(깊이 상관없이 — `base::MatchPattern`)이고
 *   `example.com` 자신은 아니다. ASCII 소문자로 견준다. 무늬는 점 네 개의 IPv4 글자에도 견준다(크로미움은 `url.host()` 의 글자에 견준다).
 * - 무늬 없는 점 네 개의 IPv4(`192.168.1.10`)는 그 주소 하나(크로미움의 IP 규칙).
 * - IP 대역 — 주소가 IP 로 적혔을 때만(이름을 풀지 않는다), 앞 N 비트. 대역 주소의 나머지 비트는 보지 않는다(`10.1.2.3/8` 은 10.0.0.0/8).
 * - `<local>` — 점이 없고 IP 가 아닌 이름.
 * - 크로미움이 늘 거는 암묵 규칙(이 앱은 `<-loopback>` 을 넣지 않는다) — `localhost`, 루프백(127.0.0.0/8·`::1`), 링크 로컬(169.254.0.0/16·
 *   `fe80::/10`).
 *
 * 크로미움과 다른 것 — 모두 '여기는 프록시' 쪽이다:
 * 1. `*.localhost` — 크로미움은 스스로 루프백으로 풀어 바로 가지만 앱의 HTTP 는 시스템 DNS 로 푼다(망의 DNS 가 공인 주소를 줄 수 있다).
 * 2. 끝에 점이 있는 이름(`nas.local.`·`localhost.`·`10.0.0.1.`) — 크로미움은 `localhost.` 를 암묵 규칙으로 보고 IPv4 의 끝 점을 뗀다.
 * 3. 점 네 개의 십진 꼴이 아닌 IPv4(`127.1`·`0x7f.0.0.1`·`010.0.0.1`·`2130706433`·앞의 0) — 크로미움은 IPv4 로 고쳐 읽지만 자바·OkHttp 가
 *    같은 주소에 붙는다고 보일 수 없다. 끝 칸이 숫자인 그 밖의 이름(`foo.123` — 크로미움에는 깨진 주소)도.
 * 4. 사용자 정보가 든 주소(`user@host`), 포트가 1~65535 밖, ASCII 밖의 글자·`%`·`\`·빈칸 따위가 든 호스트, 빈 호스트 — 자바·OkHttp·크로미움이
 *    같은 호스트로 읽는다고 보일 수 없다. 유니코드 호스트도(WebView 가 넘기는 주소는 이미 퓨니코드다).
 * 5. IPv4 를 실은 IPv6(`[::ffff:192.168.0.1]`)를 IPv4 대역에, IPv4 를 IPv6 대역(`::ffff:0:0/96`)에 견주지 않는다(크로미움은 견준다). IPv4 를
 *    실은 루프백·링크 로컬도 암묵 규칙으로 보지 않는다.
 * 6. 규칙 쪽 — 크로미움이 다르게 읽을 수 있는 규칙은 없는 셈 친다: 앞의 0·짧은 꼴·16진의 IPv4, 스킴·포트가 붙은 것, 가운데의 별표·물음표, 별표
 *    하나, 괄호가 붙은 IPv6 대역, ASCII 밖의 글자. `<-loopback>`(암묵 규칙을 빼는 규칙)이 하나라도 있으면 아무것도 바로 보내지 않는다.
 *
 * 만든 뒤 바뀌지 않는다 — 여러 스레드가 함께 읽는다.
 */
class BypassMatcher(bypass: Bypass) {

    /** 앞 [bits] 비트가 같은 주소 — 같은 꼴(IPv4 끼리·IPv6 끼리)만 견준다. */
    private class Block(val prefix: ByteArray, val bits: Int) {
        fun contains(address: ByteArray): Boolean {
            if (address.size != prefix.size) return false
            val whole = bits / 8
            for (i in 0 until whole) if (address[i] != prefix[i]) return false
            val rest = bits % 8
            if (rest == 0) return true
            val mask = (0xff shl (8 - rest)) and 0xff
            return (address[whole].toInt() and mask) == (prefix[whole].toInt() and mask)
        }
    }

    private val exactHosts: Set<String>

    /** `*.example.com` 의 `.example.com`. */
    private val suffixes: List<String>
    private val blocks: List<Block>
    private val simpleHostnames: Boolean

    /** `<-loopback>` 이 있었다 — 크로미움에서 암묵 규칙이 차례에 따라 빠지므로 아무것도 바로 보내지 않는다. */
    private val bypassNothing: Boolean

    init {
        val exact = HashSet<String>()
        val ends = ArrayList<String>()
        val ranges = ArrayList<Block>()
        var simple = bypass.simpleHostnames
        var nothing = false
        for (raw in bypass.rules()) {
            // 크로미움은 ASCII 공백만 벗긴다(TrimWhitespaceASCII) — 코틀린의 trim() 은 유니코드 공백까지 벗긴다.
            val rule = raw.trim { it == ' ' || it in '\u0009'..'\u000D' }
            val lower = asciiLower(rule)
            when {
                rule.isEmpty() -> {}
                lower == SUBTRACT_IMPLICIT -> nothing = true
                lower == LOCAL -> simple = true
                "://" in rule -> {} // 스킴이 붙은 규칙 — 이 앱의 설정은 만들지 않는다. 크로미움은 그 스킴에만 건다 — 없는 셈 친다.
                '/' in rule -> cidr(rule)?.let(ranges::add)
                else -> hostRule(lower, exact, ends, ranges)
            }
        }
        exactHosts = exact
        suffixes = ends
        blocks = ranges
        simpleHostnames = simple
        bypassNothing = nothing
    }

    /** 이 주소(http·https)를 프록시 없이 바로 여는가 — 주소는 자바가 읽은 그대로(`URL.getHost`·`getUserInfo`·`getPort`). */
    fun bypasses(url: URL): Boolean {
        if (bypassNothing) return false
        if (url.protocol != "http" && url.protocol != "https") return false
        if (url.userInfo != null) return false
        val port = url.port
        if (port != -1 && port !in 1..65535) return false
        return bypassesHost(url.host)
    }

    /** 호스트 하나(`URL.getHost` 의 꼴 — IPv6 는 괄호째)를 바로 여는가. */
    fun bypassesHost(host: String?): Boolean {
        if (bypassNothing || host.isNullOrEmpty()) return false
        if (host.startsWith('[')) {
            if (host.length < 3 || !host.endsWith(']')) return false
            val address = IpLiterals.ipv6Bytes(host.substring(1, host.length - 1)) ?: return false
            // 호스트 무늬는 괄호가 든 글자에 맞을 수 없고(무늬에 괄호가 없다), `<local>` 은 IP 를 보지 않는다.
            return isLoopbackOrLinkLocalV6(address) || blocks.any { it.contains(address) }
        }
        if (host.any { it.code >= 0x80 }) return false
        val h = asciiLower(host)
        if (!isHostName(h)) return false
        if (IpLiterals.endsInNumber(h)) {
            // 크로미움이 IPv4 로 읽는 꼴 — 점 네 개의 십진 꼴이면 그 글자가 크로미움의 `url.host()` 와 같다. 아니면 프록시.
            val address = IpLiterals.ipv4Bytes(h) ?: return false
            return isLoopbackOrLinkLocalV4(address) || blocks.any { it.contains(address) } || matchesSuffix(h)
        }
        return h == LOCALHOST || h in exactHosts || matchesSuffix(h) || (simpleHostnames && '.' !in h)
    }

    private fun matchesSuffix(host: String): Boolean = suffixes.any { host.length > it.length && host.endsWith(it) }

    private companion object {
        const val LOCAL = "<local>"
        const val SUBTRACT_IMPLICIT = "<-loopback>"
        const val LOCALHOST = "localhost"

        /** ASCII 대문자만 소문자로 — 크로미움의 ToLowerASCII. 다른 글자는 그대로 두어 아래의 글자 검사가 거른다. */
        fun asciiLower(s: String): String {
            if (s.none { it in 'A'..'Z' }) return s
            val out = CharArray(s.length)
            for (i in s.indices) {
                val c = s[i]
                out[i] = if (c in 'A'..'Z') c + ('a' - 'A') else c
            }
            return String(out)
        }

        /** 점으로 나눈 칸이 모두 비지 않았고 ASCII 소문자·숫자·`-`·`_` 뿐인가(끝의 점·빈 칸은 안 된다). */
        fun isHostName(h: String): Boolean {
            if (h.isEmpty() || h.startsWith('.') || h.endsWith('.') || ".." in h) return false
            return h.all { it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_' || it == '.' }
        }

        /** 호스트 칸의 규칙 하나(ASCII 소문자로 바꾼 것) — 크로미움이 같게 읽는다고 확신하는 꼴만 넣는다. */
        fun hostRule(rule: String, exact: MutableSet<String>, ends: MutableList<String>, ranges: MutableList<Block>) {
            // 포트·IPv6(`:`), 괄호, 물음표, ASCII 밖의 글자가 들었으면 건너뛴다.
            if (!rule.all { it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_' || it == '.' || it == '*' }) return
            val pattern = if (rule.startsWith('.')) "*$rule" else rule
            if ('*' !in pattern) {
                if (IpLiterals.endsInNumber(pattern)) {
                    // 크로미움은 이것을 IP 규칙으로 읽는다(`010.0.0.1` 은 8.0.0.1, `10.1` 은 10.0.0.1) — 점 네 개의 십진 꼴만 그 주소 하나로.
                    IpLiterals.ipv4Bytes(pattern)?.let { ranges += Block(it, 32) }
                    return
                }
                if (isHostName(pattern)) exact += pattern
                return
            }
            // `*.이름` 꼴만 — 별표 하나(모든 곳)·가운데의 별표·`*이름`(점 없이 붙은 것)은 크로미움이 더 넓게 읽는다. 없는 셈 친다.
            if (!pattern.startsWith("*.")) return
            val body = pattern.substring(2)
            if ('*' !in body && isHostName(body)) ends += ".$body"
        }

        /** `주소/길이` — ASCII 숫자 길이, 앞의 0 없는 IPv4 또는 괄호 없는 IPv6. 아니면 null(없는 셈). 길이 0 도 크로미움처럼 받는다. */
        fun cidr(rule: String): Block? {
            val slash = rule.indexOf('/')
            if (slash != rule.lastIndexOf('/')) return null
            val length = rule.substring(slash + 1)
            if (length.isEmpty() || length.length > 3 || !length.all { it in '0'..'9' }) return null
            val bits = length.toInt()
            val address = rule.substring(0, slash)
            val bytes = IpLiterals.ipv4Bytes(address) ?: (if (':' in address) IpLiterals.ipv6Bytes(address) else null) ?: return null
            if (bits > bytes.size * 8) return null
            return Block(bytes, bits)
        }

        /** 크로미움의 `IPAddress::IsLoopback`·`IsLinkLocal`(IPv4 쪽) — 127.0.0.0/8, 169.254.0.0/16. */
        fun isLoopbackOrLinkLocalV4(a: ByteArray): Boolean {
            val first = a[0].toInt() and 0xff
            return first == 127 || (first == 169 && (a[1].toInt() and 0xff) == 254)
        }

        /** 같은 것의 IPv6 쪽 — `::1`, `fe80::/10`. IPv4 를 실은 꼴은 보지 않는다. */
        fun isLoopbackOrLinkLocalV6(a: ByteArray): Boolean {
            if ((a[0].toInt() and 0xff) == 0xfe && (a[1].toInt() and 0xc0) == 0x80) return true
            for (i in 0 until 15) if (a[i].toInt() != 0) return false
            return a[15].toInt() == 1
        }
    }
}
