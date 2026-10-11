package io.github.donggi.iroirobrowser.proxy

import io.github.donggi.iroirobrowser.core.Streams
import io.github.donggi.iroirobrowser.net.HttpFetcher
import io.github.donggi.iroirobrowser.net.LocalRelay
import java.io.EOFException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.ProtocolException
import java.net.SocketException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * 앱의 HTTP(내려받기·광고 목록)가 실패한 까닭을 사람의 말로. 중계가 막은 것(정하지 않음·원격 불통·계정 거절)은 https 면 `CONNECT` 의 502 로,
 * http 면 `X-Iroiro-Relay` 머리가 달린 응답으로 온다 — 둘 다 '프록시를 거치지 못했다' 로 적는다.
 *
 * **플랫폼이 준 영어 글은 그대로 보이지 않는다**(앞 판은 `Connection reset`·`unexpected end of stream` 따위가 목록·알림에 나왔다) — 갈래를
 * 알 수 있으면 그 말로, 모르면 '연결에 문제가 생겼습니다' 로. 이 앱이 스스로 적은 까닭(한국어)은 그대로 둔다. 순수 함수라 기기 없이 시험한다.
 */
object NetErrors {

    fun describe(e: IOException): String {
        val message = e.message.orEmpty()
        return when {
            // 프록시를 켰는데 중계가 아직 서지 않았다 — 아무 데도 붙지 않았다(NetworkPath.httpRoute).
            e is HttpFetcher.NoRoute -> PROXY_NOT_READY
            e is HttpFetcher.UnsupportedScheme -> "http·https 가 아닌 주소입니다"
            e is HttpFetcher.TooManyRedirects -> "넘겨주기가 너무 많습니다"
            e is Streams.TooLarge -> "받은 것이 너무 큽니다"
            e is InterruptedIOException -> "응답이 없습니다(시간 초과)"
            "CONNECT: 502" in message -> RELAY_BLOCKED
            "CONNECT: 407" in message || "authenticate with proxy" in message -> "프록시가 계정을 받지 않았습니다"
            e is UnknownHostException -> "이름을 찾지 못했습니다"
            e is ConnectException -> "연결하지 못했습니다"
            // 이 앱이 스스로 적은 까닭(받다가 끊겼습니다·범위가 맞지 않습니다·프록시가 준비되지 않았습니다 …).
            message.any { it in '가'..'힣' } -> message
            e is SSLException -> "보안 연결을 맺지 못했습니다"
            e is SocketException || e is EOFException || e is ProtocolException -> "연결이 끊겼습니다"
            // 플랫폼 HTTP(OkHttp)의 'unexpected end of stream on …' — 상대가 답하다 끊었다.
            message.contains("end of stream", ignoreCase = true) -> "연결이 끊겼습니다"
            else -> "연결에 문제가 생겼습니다"
        }
    }

    /** 오류 응답(200·304 가 아닌 것)의 까닭 — 중계가 만든 것이면 그렇게. */
    fun describeStatus(response: HttpFetcher.Response): String =
        if (response.header(LocalRelay.MARKER) != null) RELAY_BLOCKED else "HTTP ${response.code}"

    /**
     * 목적지가 아니라 **프록시가 만든 답**인가 — 중계(`X-Iroiro-Relay`)나 원격 Squid(`X-Squid-Error` — 접근 목록이 막은 http 요청). 그런 403 은
     * 주소가 만료된 것이 아니다.
     */
    fun isProxyAnswer(response: HttpFetcher.Response): Boolean =
        response.header(LocalRelay.MARKER) != null || response.header("X-Squid-Error") != null

    const val RELAY_BLOCKED = "프록시를 거치지 못했습니다 — 프록시를 정하지 않았거나 닿지 못합니다"
    const val PROXY_NOT_READY = "프록시가 준비되지 않았습니다"
}
