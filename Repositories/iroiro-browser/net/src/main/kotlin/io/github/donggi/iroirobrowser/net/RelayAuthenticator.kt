package io.github.donggi.iroirobrowser.net

import java.net.Authenticator
import java.net.PasswordAuthentication

/**
 * 앱의 `HttpURLConnection` 이 중계의 407 에 답하게 한다 — **중계에게만**. 프로세스 전체에 하나 건다(`Authenticator.setDefault`).
 *
 * 가리는 것: 묻는 쪽이 프록시이고(`RequestorType.PROXY`), 포트가 지금 중계의 포트이고, 주소가 루프백이다. 이름은 보지 않는다 — 플랫폼은
 * 프록시 주소를 거꾸로 찾은 이름(127.0.0.1 → `localhost`)으로 넘긴다(실측). 사이트의 401(`SERVER`)에는 아무것도 주지 않는다.
 *
 * 예외 규칙으로 **바로** 붙는 연결(`Proxy.NO_PROXY` — 사설망·루프백의 다른 곳)이 407 을 내도 여기까지 오지 않는다 — JDK 는 프록시를 쓰지
 * 않는 연결의 407 에 프록시 인증을 묻지 않고(시험 `HttpFetcherTest`), 안드로이드의 OkHttp 는 그 407 을 `ProtocolException` 으로 끝낸다(소스로
 * 읽은 것 — 기기에서 재지 않았다). 그 연결의 401 은 `SERVER` 라 위에서 거른다.
 */
class RelayAuthenticator(private val current: () -> LocalRelay?) : Authenticator() {

    override fun getPasswordAuthentication(): PasswordAuthentication? {
        val relay = current() ?: return null
        if (requestorType != RequestorType.PROXY) return null
        if (requestingPort != relay.port) return null
        if (requestingSite?.isLoopbackAddress != true) return null
        return PasswordAuthentication(relay.sessionUser, relay.sessionPassword.toCharArray())
    }
}
