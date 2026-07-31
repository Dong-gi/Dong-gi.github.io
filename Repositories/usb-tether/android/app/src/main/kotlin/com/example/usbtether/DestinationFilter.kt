package com.example.usbtether

import java.net.Inet6Address
import java.net.InetAddress

/**
 * 프록시가 연결해도 되는 목적지를 판별한다.
 *
 * ## 왜 필요한가
 *
 * 이전 구현은 클라이언트가 준 host/port 로 그대로 `connect()` 했다. 목적지 검사가
 * 전혀 없었으므로 프록시가 다음을 그대로 중계했다.
 *
 *  - **폰의 `127.0.0.1`.** 루프백에만 바인딩한 서비스는 "같은 기기"를 신뢰해 인증을
 *    생략하는 경우가 많다. 프록시가 그 전제를 무력화한다. 이 기기에서 실측한
 *    `adb shell ss -ltn` 에 실제 표적이 있었다: `127.0.0.1:53`(루프백 전용 리졸버),
 *    `0.0.0.0:6600`, `*:8487`, `*:8488`. 서로의 프록시(`CONNECT 127.0.0.1:8282`)도
 *    포함되므로, 한쪽에만 규칙을 걸면 체이닝으로 우회된다.
 *  - **폰이 붙어 있는 LAN.** `CONNECT 192.168.1.1:80` 으로 공유기 관리 페이지, NAS,
 *    프린터, IoT 에 도달한다. Wi-Fi Direct 망에서 사용자의 홈·사내 LAN 으로 피벗하는
 *    경로이며 폰이 브리지 역할을 한다.
 *
 * ## DNS 리바인딩
 *
 * 이름을 한 번 해석해 통과시킨 뒤 문자열을 다시 `InetSocketAddress` 에 넘기면 재해석이
 * 일어나 검사를 우회할 수 있다(TTL 0 으로 두 번째 응답만 사설 주소를 주는 공격).
 * 그래서 [resolve] 는 통과한 **주소 객체**를 돌려주고, 호출부는 반드시 그 객체로
 * 연결해야 한다.
 *
 * ## 포트는 제한하지 않는다 (의도)
 *
 * 포트 허용 목록(80/443 등)은 이 앱의 용도와 맞지 않는다. PC 한 대의 트래픽 전량을
 * 통과시키는 테더링이므로 메일·SSH·게임 등이 전부 깨진다. 25번(스팸 릴레이) 차단도
 * 고려했지만, 폰의 앱은 이미 `INTERNET` 권한으로 직접 25번을 열 수 있어 실익이 적고
 * 정상 사용을 깨뜨린다. 위험의 본질은 포트가 아니라 **도달 범위**이므로 주소로 막는다.
 */
object DestinationFilter {

    /** [resolve] 의 결과. */
    sealed interface Result {
        /** 통과. [address] 로 연결해야 한다(문자열로 재해석하면 리바인딩에 노출된다). */
        data class Allowed(val address: InetAddress) : Result

        /** 거부. [reason] 은 로그용이며 호스트명을 담지 않는다. */
        data class Rejected(val reason: String) : Result
    }

    /**
     * 목적지 호스트를 해석하고 정책을 적용한다.
     *
     * 여러 주소로 해석되면 허용되는 첫 주소를 채택한다. 전부 차단 대상이면 거부한다.
     *
     * @param host IPv4/IPv6 리터럴 또는 도메인
     */
    fun resolve(host: String): Result {
        val candidates = try {
            InetAddress.getAllByName(host)
        } catch (_: Exception) {
            return Result.Rejected("이름 해석 실패")
        }
        if (candidates.isEmpty()) return Result.Rejected("해석 결과 없음")

        candidates.firstOrNull { !isBlocked(it) }?.let { return Result.Allowed(it) }
        return Result.Rejected("로컬/사설 대역")
    }

    /**
     * 주소가 차단 대상인지 판정한다.
     *
     * 차단: 와일드카드(0.0.0.0, ::), 루프백, 링크 로컬, 사설 대역(RFC1918),
     * CGNAT(100.64.0.0/10), `0.0.0.0/8`, 멀티캐스트, IPv6 ULA(fc00::/7).
     */
    internal fun isBlocked(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress) return true
        if (address.isLoopbackAddress) return true
        if (address.isLinkLocalAddress) return true
        if (address.isSiteLocalAddress) return true
        if (address.isMulticastAddress) return true

        // IPv4-mapped IPv6 로 우회할 수 있으므로 정규화한 뒤 IPv4 규칙을 다시 본다.
        val v4 = address.asIpv4()
        if (v4 != null) {
            val a = v4.address[0].toInt() and 0xFF
            val b = v4.address[1].toInt() and 0xFF
            if (a == 0) return true                          // 0.0.0.0/8
            if (a == 100 && b in 64..127) return true        // 100.64.0.0/10 CGNAT
            return false
        }

        if (address is Inet6Address) {
            // fc00::/7 (ULA). Java 에 판정 헬퍼가 없어 직접 본다.
            val first = address.address[0].toInt() and 0xFF
            if (first and 0xFE == 0xFC) return true
        }
        return false
    }
}
