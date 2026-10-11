package io.github.donggi.iroirobrowser.core

import java.util.Locale

/** IP 주소 글자열을 이름 풀이 없이 가린다(`InetAddress.getByName` 은 이름이면 DNS 를 묻는다 — 쓰지 않는다). */
object IpLiterals {

    fun isIpv4(s: String): Boolean {
        val parts = s.split('.')
        return parts.size == 4 && parts.all { p -> p.isNotEmpty() && p.length <= 3 && p.all(Char::isDigit) && p.toInt() <= 255 }
    }

    /** RFC 4291 의 글꼴. 끝에 IPv4 가 붙은 꼴(`::ffff:1.2.3.4`)도 받는다. 영역 표시(`%eth0`)는 받지 않는다. */
    fun isIpv6(s: String): Boolean {
        if (s.isEmpty() || s.length > 45 || '%' in s) return false
        var body = s
        var groupsFromV4 = 0
        val lastColon = s.lastIndexOf(':')
        if (lastColon >= 0 && '.' in s.substring(lastColon + 1)) {
            if (!isIpv4(s.substring(lastColon + 1))) return false
            body = s.substring(0, lastColon + 1) + "0"
            groupsFromV4 = 1
        }
        val doubleColon = body.indexOf("::")
        if (doubleColon >= 0 && body.indexOf("::", doubleColon + 1) >= 0) return false
        val groups = if (doubleColon >= 0) {
            val left = body.substring(0, doubleColon)
            val right = body.substring(doubleColon + 2)
            val l = if (left.isEmpty()) emptyList() else left.split(':')
            val r = if (right.isEmpty()) emptyList() else right.split(':')
            if ((l + r).any { !isGroup(it) }) return false
            if (l.size + r.size + groupsFromV4 > 7) return false
            return true
        } else {
            body.split(':')
        }
        // IPv4 꼬리는 두 묶음(32비트)이다 — 위에서 "0" 한 묶음으로 바꿔 두었으니 하나를 더 센다.
        return groups.size + groupsFromV4 == 8 && groups.all(::isGroup)
    }

    private fun isGroup(g: String): Boolean = g.length in 1..4 && g.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }

    /**
     * 점 네 개의 십진 IPv4 를 4 바이트로 — **크로미움이 같은 값으로 읽는 꼴만**: 칸마다 ASCII 숫자 1~3 개, 0~255, 앞에 0 을 붙이지 않는다
     * (`0` 하나는 된다). 크로미움(URL 의 IPv4 해석 — `url::IPv4AddressToNumber`)은 `010` 을 8진수 8 로, `0x0a` 를 16진수로, `10.1` 을
     * 10.0.0.1 로 읽고 `08` 은 깨진 주소로 본다 — [isIpv4] 는 그것들을 십진수로 받아 버린다(그리고 유니코드 숫자도 받는다). 아니면 null.
     */
    fun ipv4Bytes(s: String): ByteArray? {
        val parts = s.split('.')
        if (parts.size != 4) return null
        val out = ByteArray(4)
        for ((i, p) in parts.withIndex()) {
            if (p.isEmpty() || p.length > 3 || !p.all { it in '0'..'9' } || (p.length > 1 && p[0] == '0')) return null
            val v = p.toInt()
            if (v > 255) return null
            out[i] = v.toByte()
        }
        return out
    }

    /**
     * IPv6 글자(괄호 없이)를 16 바이트로 — [isIpv6] 이고, 묶음이 ASCII 16진수이고, 끝에 IPv4 가 붙었으면 그것도 [ipv4Bytes] 의 꼴일 때만.
     * 영역 표시(`%`)는 받지 않는다(크로미움의 URL 도 받지 않는다). 아니면 null.
     */
    fun ipv6Bytes(s: String): ByteArray? {
        if (!isIpv6(s)) return null
        var body = s
        val lastColon = s.lastIndexOf(':')
        if ('.' in s.substring(lastColon + 1)) {
            val v4 = ipv4Bytes(s.substring(lastColon + 1)) ?: return null
            val hi = ((v4[0].toInt() and 0xff) shl 8) or (v4[1].toInt() and 0xff)
            val lo = ((v4[2].toInt() and 0xff) shl 8) or (v4[3].toInt() and 0xff)
            body = s.substring(0, lastColon + 1) + hi.toString(16) + ":" + lo.toString(16)
        }
        val doubleColon = body.indexOf("::")
        val groups: List<String> = if (doubleColon >= 0) {
            val left = body.substring(0, doubleColon).let { if (it.isEmpty()) emptyList() else it.split(':') }
            val right = body.substring(doubleColon + 2).let { if (it.isEmpty()) emptyList() else it.split(':') }
            val missing = 8 - left.size - right.size
            if (missing < 1) return null
            left + List(missing) { "0" } + right
        } else {
            body.split(':')
        }
        if (groups.size != 8) return null
        val out = ByteArray(16)
        for ((i, g) in groups.withIndex()) {
            if (g.isEmpty() || g.length > 4 || !g.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
            val v = g.toInt(16)
            out[2 * i] = (v shr 8).toByte()
            out[2 * i + 1] = v.toByte()
        }
        return out
    }

    /**
     * 크로미움이 이 호스트를 **IPv4 로 읽으려 드는가** — 마지막 칸(끝 점을 하나 떼고)이 ASCII 숫자뿐이거나 `0x` 와 16진수뿐이면 그렇다(WHATWG URL 의
     * 'ends in a number'). 그런 호스트는 점 네 개의 십진 꼴([ipv4Bytes])이 아니면 크로미움이 다른 주소로 읽거나 깨진 주소로 본다 — 이름으로 다루면
     * 안 된다. 소문자로 견준다(`0X` 도 된다).
     */
    fun endsInNumber(host: String): Boolean {
        val h = if (host.endsWith('.')) host.dropLast(1) else host
        val last = h.substringAfterLast('.').lowercase(Locale.ROOT)
        if (last.isEmpty()) return false
        if (last.all { it in '0'..'9' }) return true
        return last.startsWith("0x") && last.substring(2).all { it in '0'..'9' || it in 'a'..'f' }
    }
}
