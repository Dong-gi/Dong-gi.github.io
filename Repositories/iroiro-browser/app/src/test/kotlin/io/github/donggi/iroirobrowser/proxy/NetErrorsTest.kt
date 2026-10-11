package io.github.donggi.iroirobrowser.proxy

import io.github.donggi.iroirobrowser.core.Streams
import io.github.donggi.iroirobrowser.net.HttpFetcher
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 실패 까닭의 글([NetErrors]). 플랫폼이 준 영어 글은 목록·알림에 그대로 나가지 않아야 하고(앞 판은 `Connection reset` 따위가 나왔다), 이 앱이
 * 스스로 적은 한국어 까닭은 그대로 남아야 한다.
 */
class NetErrorsTest {

    private fun hangulOnly(text: String) = text.none { it in 'A'..'Z' || it in 'a'..'z' }

    @Test
    fun 플랫폼의_영어_글은_그대로_나가지_않는다() {
        val english = listOf(
            SocketException("Connection reset"),
            SocketException("Software caused connection abort"),
            EOFException("\\n not found: limit=0 content=…"),
            IOException("unexpected end of stream on Connection{127.0.0.1:39123, proxy=HTTP @ /127.0.0.1:39123}"),
            SSLHandshakeException("Handshake failed"),
            IOException("Some other thing went wrong"),
            Streams.TooLarge(8L * 1024 * 1024),
        )
        for (e in english) {
            val text = NetErrors.describe(e)
            assertTrue(hangulOnly(text), "영어가 남았다: ${e.message} → $text")
        }
        assertEquals("연결이 끊겼습니다", NetErrors.describe(SocketException("Connection reset")))
        assertEquals("연결이 끊겼습니다", NetErrors.describe(IOException("unexpected end of stream on Connection{x}")))
        assertEquals("보안 연결을 맺지 못했습니다", NetErrors.describe(SSLHandshakeException("Handshake failed")))
        assertEquals("연결에 문제가 생겼습니다", NetErrors.describe(IOException("Some other thing went wrong")))
    }

    @Test
    fun 갈래를_아는_실패는_그_말로() {
        assertEquals("응답이 없습니다(시간 초과)", NetErrors.describe(SocketTimeoutException("Read timed out")))
        assertEquals("이름을 찾지 못했습니다", NetErrors.describe(UnknownHostException("example.invalid")))
        assertEquals("연결하지 못했습니다", NetErrors.describe(ConnectException("Failed to connect to /127.0.0.1:1")))
        assertEquals(NetErrors.RELAY_BLOCKED, NetErrors.describe(IOException("Unexpected response code for CONNECT: 502")))
        assertEquals("프록시가 계정을 받지 않았습니다", NetErrors.describe(IOException("Failed to authenticate with proxy")))
        assertEquals("넘겨주기가 너무 많습니다", NetErrors.describe(HttpFetcher.TooManyRedirects("https://a.test/")))
        assertEquals("http·https 가 아닌 주소입니다", NetErrors.describe(HttpFetcher.UnsupportedScheme("ftp://a.test/")))
        // 길이 아직 없다(프록시를 켰는데 중계가 서지 않았다) — 아무 데도 붙지 않았다.
        assertEquals(NetErrors.PROXY_NOT_READY, NetErrors.describe(HttpFetcher.NoRoute()))
        assertEquals("프록시가 준비되지 않았습니다", NetErrors.PROXY_NOT_READY)
    }

    @Test
    fun 이_앱이_적은_까닭은_그대로() {
        assertEquals("받다가 끊겼습니다", NetErrors.describe(IOException("받다가 끊겼습니다")))
        assertEquals("프록시가 준비되지 않았습니다", NetErrors.describe(IOException("프록시가 준비되지 않았습니다")))
        // 한국어 까닭이 든 소켓 예외도(받기가 감싼 것) 그대로 둔다.
        assertEquals("범위가 맞지 않습니다", NetErrors.describe(IOException("범위가 맞지 않습니다")))
    }
}
