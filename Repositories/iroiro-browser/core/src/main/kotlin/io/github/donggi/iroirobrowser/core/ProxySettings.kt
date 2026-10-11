package io.github.donggi.iroirobrowser.core

import java.net.IDN
import java.util.Locale

/**
 * 사용자의 프록시 설정 — TLS 로 감싼 HTTP 프록시 하나(서버는 설정 › 프록시의 '서버 구성 파일 받기' 로 세운다), Basic 계정,
 * 예외(호스트·IP 대역·사설망·점 없는 이름).
 *
 * **비밀번호를 문자열로 싣지 않는다**([toString]).
 */
class ProxySettings(
    /** 꺼져 있으면 직접 연결이다(사용자가 고른 것). 켜져 있는데 [isComplete] 가 아니면 **아무 데도 가지 않는다**(fail-closed). */
    val enabled: Boolean,
    val host: String,
    val port: Int,
    val user: String,
    val password: String,
    val bypass: Bypass,
    /** 출구 확인 주소 — `ip=`·`loc=` 줄을 주는 쪽. */
    val exitCheckUrl: String,
) {
    val isComplete: Boolean get() = host.isNotBlank() && port in 1..65535

    fun copy(
        enabled: Boolean = this.enabled,
        host: String = this.host,
        port: Int = this.port,
        user: String = this.user,
        password: String = this.password,
        bypass: Bypass = this.bypass,
        exitCheckUrl: String = this.exitCheckUrl,
    ) = ProxySettings(enabled, host, port, user, password, bypass, exitCheckUrl)

    /**
     * 길이 같은가 — 같으면 저장해도 중계를 다시 세우지 않는다(다시 세우면 열린 쪽을 모두 다시 부르고 받던 HLS·DASH 가 끊긴다 — 설정에서
     * '저장' 만 눌러도 그랬다). 꺼져 있으면 켜기만 본다(주소·계정·예외는 길에 쓰이지 않는다). 출구 확인 주소는 길이 아니다.
     */
    fun sameRoute(other: ProxySettings): Boolean =
        enabled == other.enabled && (
            !enabled || (host.trim() == other.host.trim() && port == other.port && user == other.user && password == other.password &&
                bypass == other.bypass)
            )

    override fun toString(): String = "ProxySettings(enabled=$enabled, $host:$port, bypass=$bypass)"

    companion object {
        /** 기본 포트 — 서버 구성(`setup-squid.sh`)의 `--port` 기본값. */
        const val DEFAULT_PORT = 10443

        val DEFAULT = ProxySettings(
            enabled = true,
            host = "",
            port = DEFAULT_PORT,
            user = "",
            password = "",
            bypass = Bypass.DEFAULT,
            exitCheckUrl = ExitTrace.DEFAULT_URL,
        )

        /** 호스트 칸의 문제. 없으면 null. 스킴·경로를 붙여 넣으면 떼어 달라고 한다(조용히 고치지 않는다). */
        fun checkHost(host: String): String? {
            val h = host.trim()
            return when {
                h.isEmpty() -> "호스트를 넣어 주세요"
                "://" in h || '/' in h -> "주소가 아니라 호스트 이름만 넣어 주세요(예: proxy.example.com)"
                h.any { it.isWhitespace() } -> "빈칸이 들어 있습니다"
                ':' in h && !IpLiterals.isIpv6(h.trim('[', ']')) -> "포트는 아래 칸에 따로 넣어 주세요"
                else -> null
            }
        }

        fun checkPort(port: String): String? {
            val p = port.trim().toIntOrNull() ?: return "포트는 숫자입니다"
            return if (p in 1..65535) null else "포트는 1~65535 입니다"
        }
    }
}

/**
 * 프록시를 거치지 않는 곳. WebView(`ProxyConfig.addBypassRule`)가 받는 꼴로 만든다 — 호스트 무늬(`*.local`), IP 대역(`10.0.0.0/8`),
 * 점 없는 이름(`bypassSimpleHostnames`). 루프백·링크 로컬은 WebView 가 이미 거른다(그래도 확장처럼 적어 둔다).
 *
 * **IP 대역은 IP 로 적힌 주소에만** 걸린다(WebView 는 예외를 가리려고 이름을 풀지 않는다 — 실험 bypass-control). 사설망이 아닌 대역을
 * 넣으면 그 주소로는 **실제 IP 가 드러난다** — 설정 화면이 알린다.
 */
data class Bypass(
    val hosts: List<String>,
    val cidrs: List<String>,
    /** RFC 1918 사설 대역 + IPv6 고유 지역 주소(fc00::/7). */
    val privateNetworks: Boolean,
    /** 점 없는 이름(사내망 이름). */
    val simpleHostnames: Boolean,
) {
    /** WebView 에 넘길 규칙(점 없는 이름은 따로 — [simpleHostnames]). 겹치는 것은 한 번. */
    fun rules(): List<String> = LinkedHashSet<String>().apply {
        hosts.forEach { add(it) }
        cidrs.forEach { add(it) }
        if (privateNetworks) PRIVATE_NETWORKS.forEach { add(it) }
    }.toList()

    companion object {
        val PRIVATE_NETWORKS = listOf("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "fc00::/7")

        /** 기본 예외 — 옛 확장의 기본값에서 PC 에서만 뜻이 있던 테더링 대역(`10.42.0.0/24`·`192.168.49.0/24`)을 뺀 것. */
        val DEFAULT = Bypass(
            hosts = listOf("localhost", "*.local", "*.internal"),
            cidrs = listOf("127.0.0.0/8", "169.254.0.0/16"),
            privateNetworks = true,
            simpleHostnames = true,
        )

        /** 여러 줄(또는 쉼표)의 글을 낱말로. `#` 뒤는 주석. */
        fun split(text: String): List<String> = text.lines()
            .map { it.substringBefore('#') }
            .flatMap { it.split(',') }
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        /**
         * 호스트 무늬 하나를 WebView 의 꼴로. 틀리면 [Invalid]. `.example.com` 은 `*.example.com` 으로, 국제화 이름은 퓨니코드로.
         *
         * 끝 칸이 숫자(또는 `0x…`)인 무늬 없는 이름은 크로미움이 **IP 주소**로 읽는다 — `10.1` 은 10.0.0.1, `010.0.0.1` 은 8진수라 8.0.0.1 이다
         * (사설망을 적었다고 믿는 사이 공인 주소가 예외가 된다). 그래서 점 네 개의 십진 꼴([IpLiterals.ipv4Bytes])만 받는다.
         */
        fun normalizeHost(raw: String): String {
            var s = raw.trim().lowercase(Locale.ROOT)
            if (s.startsWith('.')) s = "*$s"
            if (s == "*" || s == "*.") throw Invalid("'*' 하나는 모든 곳을 예외로 만듭니다 — 프록시를 끄려면 위의 스위치를 쓰세요")
            val wildcard = s.startsWith("*.")
            val body = if (wildcard) s.substring(2) else s
            if (body.isEmpty() || body.any { it.isWhitespace() } || '/' in body || ':' in body) throw Invalid("호스트 무늬가 아닙니다: $raw")
            val ascii = try {
                IDN.toASCII(body, IDN.ALLOW_UNASSIGNED).lowercase(Locale.ROOT)
            } catch (e: IllegalArgumentException) {
                throw Invalid("호스트 이름이 아닙니다: $raw")
            }
            if (!ascii.split('.').all { LABEL.matches(it) }) throw Invalid("호스트 이름이 아닙니다: $raw")
            if (!wildcard && IpLiterals.endsInNumber(ascii) && IpLiterals.ipv4Bytes(ascii) == null) {
                throw Invalid("IP 주소는 0~255 의 숫자 넷을 점으로 이어 적어 주세요(앞에 0 없이): $raw")
            }
            return if (wildcard) "*.$ascii" else ascii
        }

        /**
         * IP 대역 하나를 `주소/길이` 로. 길이 없는 주소는 그 주소 하나(/32·/128). 길이 0 은 받지 않는다(모든 주소).
         *
         * IPv4 의 칸에 앞의 0 을 받지 않는다 — 크로미움은 `010.0.0.0/8` 을 8진수로 읽어 **8.0.0.0/8**(공인 대역)을 예외로 만들고, `08.…` 은
         * 받지 않아 WebView 에 길을 거는 순간 던진다. 그런 꼴은 [IpLiterals.ipv4Bytes]·[IpLiterals.ipv6Bytes] 가 거른다.
         */
        fun normalizeCidr(raw: String): String {
            val s = raw.trim().removePrefix("[").let { if (it.contains("]")) it.replace("]", "") else it }
            val slash = s.indexOf('/')
            val addr = if (slash < 0) s else s.substring(0, slash)
            val v4 = IpLiterals.ipv4Bytes(addr) != null
            val v6 = !v4 && IpLiterals.ipv6Bytes(addr) != null
            if (!v4 && !v6) {
                // 점으로 나눈 칸(IPv6 면 끝의 IPv4)에 앞의 0 이 있었는가 — 그 까닭이면 그렇게 말한다.
                val leadingZero = '.' in addr && addr.substringAfterLast(':').split('.').any { it.length > 1 && it[0] == '0' }
                throw Invalid(if (leadingZero) "IP 주소의 숫자 앞에 0 을 붙이지 마세요(크로미움은 8진수로 읽습니다): $raw" else "IP 주소가 아닙니다: $raw")
            }
            val max = if (v4) 32 else 128
            val len = if (slash < 0) max else s.substring(slash + 1).toIntOrNull() ?: throw Invalid("대역의 길이가 숫자가 아닙니다: $raw")
            if (len !in 1..max) throw Invalid("대역의 길이는 1~$max 입니다: $raw")
            return "${addr.lowercase(Locale.ROOT)}/$len"
        }

        /** 사설망(RFC 1918·고유 지역)·루프백·링크 로컬 밖의 대역인가 — 그런 대역은 실제 IP 를 드러낸다(설정 화면의 경고). */
        fun isPublicCidr(cidr: String): Boolean {
            val addr = cidr.substringBefore('/')
            if (IpLiterals.isIpv4(addr)) {
                val o = addr.split('.').map(String::toInt)
                return !(o[0] == 10 || o[0] == 127 || (o[0] == 172 && o[1] in 16..31) || (o[0] == 192 && o[1] == 168) ||
                    (o[0] == 169 && o[1] == 254))
            }
            val first = addr.lowercase(Locale.ROOT)
            return !(first.startsWith("fc") || first.startsWith("fd") || first.startsWith("fe8") || first.startsWith("fe9") ||
                first.startsWith("fea") || first.startsWith("feb") || first == "::1")
        }

        private val LABEL = Regex("""[a-z0-9_]([a-z0-9_\-]{0,61}[a-z0-9_])?""")
    }

    class Invalid(message: String) : IllegalArgumentException(message)
}
