package io.github.donggi.iroirobrowser.core.adblock

import io.github.donggi.iroirobrowser.core.IpLiterals

/**
 * 호스트 이름의 도구 — 상위 이름 늘어놓기와 등록 도메인(eTLD+1) **어림**.
 *
 * 공개 접미사 목록(PSL)은 쓰지 않는다(내려받거나 들여와야 한다). 대신 흔한 두 단계 접미사(`co.kr`·`co.uk`…)와 사이트마다 따로인 호스팅 접미사
 * (`github.io`…)만 안다 — 그 밖의 나라별 두 단계 접미사에서는 '같은 사이트' 를 넓게 본다(제3자 요청을 제1자로 보아 `$third-party` 규칙이 덜
 * 걸린다). 쓰는 곳은 광고 규칙의 `$third-party` 판정, 사이트별 광고 차단·팝업 허용(같은 사이트로 묶는 단위), 가로채기를 그 사이트에
 * 묶는 판정(`PageBridge.siteOf`), 사용자가 숨긴 요소의 범위([UserHides])다 — 어림이 넓게 보는 접미사에서는 모두 '같은 사이트' 쪽으로 기운다.
 */
object Domains {

    /** [host] 와 그 상위 이름들 — `a.b.example.com` → `a.b.example.com`, `b.example.com`, `example.com`, `com`. IP 는 그 하나만. */
    fun suffixes(host: String): List<String> {
        if (host.isEmpty()) return emptyList()
        if (isIp(host)) return listOf(host)
        val out = ArrayList<String>(4)
        var start = 0
        while (true) {
            out += host.substring(start)
            val dot = host.indexOf('.', start)
            if (dot < 0 || dot == host.length - 1) break
            start = dot + 1
        }
        return out
    }

    /** 등록 도메인 어림 — 이 둘이 다르면 제3자다. */
    fun registrable(host: String): String {
        if (host.isEmpty() || isIp(host)) return host
        val labels = host.trimEnd('.').split('.')
        if (labels.size <= 2) return host.trimEnd('.')
        val lastTwo = labels.takeLast(2).joinToString(".")
        val keep = if (lastTwo in MULTI_LABEL_SUFFIXES) 3 else 2
        return labels.takeLast(minOf(keep, labels.size)).joinToString(".")
    }

    fun isThirdParty(requestHost: String, documentHost: String): Boolean =
        documentHost.isNotEmpty() && registrable(requestHost) != registrable(documentHost)

    /** [host] 가 [domain] 이거나 그 아래 이름인가. */
    fun isSameOrSubdomain(host: String, domain: String): Boolean =
        host == domain || (host.length > domain.length && host.endsWith(domain) && host[host.length - domain.length - 1] == '.')

    private fun isIp(host: String): Boolean = IpLiterals.isIpv4(host) || host.startsWith("[") || ':' in host

    /** 이름 하나 아래가 등록 도메인인 접미사(어림 — PSL 의 일부). */
    private val MULTI_LABEL_SUFFIXES = setOf(
        // 한국
        "co.kr", "or.kr", "go.kr", "ac.kr", "ne.kr", "re.kr", "pe.kr", "ms.kr", "hs.kr", "es.kr", "sc.kr", "kg.kr", "mil.kr",
        // 일본
        "co.jp", "ne.jp", "or.jp", "ac.jp", "go.jp", "ad.jp", "ed.jp", "gr.jp", "lg.jp",
        // 영국·호주·뉴질랜드·남아공
        "co.uk", "org.uk", "ac.uk", "gov.uk", "me.uk", "net.uk", "ltd.uk", "plc.uk",
        "com.au", "net.au", "org.au", "edu.au", "gov.au", "co.nz", "org.nz", "co.za",
        // 중국·대만·홍콩·싱가포르·인도
        "com.cn", "net.cn", "org.cn", "gov.cn", "com.tw", "net.tw", "org.tw", "com.hk", "com.sg", "co.in",
        // 미주·유럽 일부
        "com.br", "net.br", "org.br", "com.mx", "com.ar", "com.tr", "co.id", "com.vn", "co.th", "com.my", "com.ph",
        // 사이트마다 따로인 호스팅(PSL 의 사설 접미사 가운데 흔한 것)
        "github.io", "gitlab.io", "blogspot.com", "appspot.com", "herokuapp.com", "netlify.app", "vercel.app", "pages.dev",
        "workers.dev", "web.app", "firebaseapp.com", "azurewebsites.net", "cloudfront.net",
    )
}
