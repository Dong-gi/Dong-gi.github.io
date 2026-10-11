package io.github.donggi.iroirobrowser.tether.core

/**
 * Wi-Fi Direct 의 소식(연결 변경 방송·그룹 조회)이 **이 앱의 그룹**에 대해 무엇을 말하는가 — 앱의 서비스·핫스팟이 쓰는 판정. 안드로이드의
 * `WifiP2pInfo`·`WifiP2pGroup` 을 앱이 풀어 값으로 넘긴다(기기 없이 시험한다).
 *
 * '이 앱의 그룹' 은 이 폰이 그룹 오너이고 이름이 우리가 만든 SSID 인 그룹이다. 같은 대역(192.168.49.0/24)과 같은 로컬 망을 쓰는 남의 그룹
 * (옛 테더 앱 등)은 우리 그룹이 아니다 — 손님 거르기는 우리 그룹의 인터페이스로만 받는다([PeerFilter]).
 */
object GroupWatch {

    /** 그 그룹이 이 앱의 것인가 — 이 폰이 오너이고 이름이 [ourSsid] 다. */
    fun isOurs(owner: Boolean, networkName: String?, ourSsid: String?): Boolean =
        owner && ourSsid != null && networkName == ourSsid

    /**
     * 그 소식이 **이 앱의 그룹이 서 있다**고 말하면 그 인터페이스 이름(`WifiP2pGroup.getInterface()`), 아니면 null — 그룹이 서지 않았다,
     * 이 폰이 손님이다, 남의 그룹이다, 인터페이스 이름이 비었다.
     */
    fun ourInterface(formed: Boolean, owner: Boolean, networkName: String?, interfaceName: String?, ourSsid: String?): String? =
        interfaceName?.takeIf { formed && isOurs(owner, networkName, ourSsid) && it.isNotBlank() }
}
