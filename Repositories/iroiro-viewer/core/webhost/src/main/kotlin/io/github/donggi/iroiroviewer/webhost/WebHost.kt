package io.github.donggi.iroiroviewer.webhost

import android.net.Uri
import java.io.InputStream

/**
 * 문서를 그리는 WebView 가 쓰는 **가짜 출처**.
 *
 * ## 왜 출처가 필요한가
 *
 * `loadData` 로 HTML 만 밀어 넣으면 그 쪽의 출처가 `null` 이 되어 상대 주소가 풀리지
 * 않는다. 그림도 CSS 도 붙지 않는다. `loadDataWithBaseURL` 로 기준을 줄 수도 있지만,
 * 그러면 **장을 넘길 때마다 기준이 달라지는 것을 우리가 관리**해야 한다.
 *
 * 대신 **우리 호스트의 URL 로 쪽을 연다.** 그러면 상대 주소를 WebView 가 알아서 풀고
 * (그 계산은 그쪽이 우리보다 정확하다), 우리는 `shouldInterceptRequest` 에서 들어오는
 * 요청의 경로만 보면 된다. 경로 계산이 한 벌로 끝난다.
 *
 * ## `.invalid` 를 쓰는 이유
 *
 * `invalid` 는 RFC 2606 이 **절대 등록되지 않도록 예약한** 최상위 도메인이다. 누군가
 * 이 이름을 사는 일이 정의상 일어날 수 없으므로, 우리 가로채기가 한 번 빗나가도 그
 * 요청이 닿을 곳이 없다. (`INTERNET` 을 선언하지 않는 것이 1차 방어이고 이것이 2차다 —
 * 방어는 겹쳐 두어야 한 겹이 무너져도 남는다.)
 *
 * `https` 를 쓰는 것은 WebView 가 스킴에 따라 **혼합 콘텐츠·보안 출처 규칙**을 달리
 * 적용하기 때문이다. 알 수 없는 사용자 정의 스킴은 불투명 출처가 되어 CSS·글꼴이 조용히
 * 막히는 판이 있다.
 */
object WebHost {

    const val SCHEME = "https"
    const val HOST = "book.iroiro.invalid"

    private const val PREFIX = "$SCHEME://$HOST/"

    /** 책 안의 경로 하나를 우리 쪽 URL 로. */
    fun urlFor(path: String): String = PREFIX + encode(path)

    /**
     * 우리 쪽 URL 이면 책 안의 경로, 아니면 null.
     *
     * **경로를 정규화하지 않는다.** `..` 을 푸는 것은 자원을 내주는 쪽(`ResourceProvider`)
     * 의 일이다 — 그쪽이 책의 구조를 알고, 여기는 호스트만 안다.
     */
    fun pathOf(uri: Uri): String? {
        if (!uri.scheme.equals(SCHEME, ignoreCase = true)) return null
        if (!uri.host.equals(HOST, ignoreCase = true)) return null
        return uri.path?.removePrefix("/")?.takeIf { it.isNotEmpty() }
    }

    /**
     * 경로의 글자 가운데 URL 에서 뜻이 달라지는 것만 바꾼다.
     *
     * `Uri.encode` 는 `/` 까지 바꾸므로 쓸 수 없고, 전부 바꾸면 한글 파일명이 읽기
     * 어려워진다. 실제로 막아야 하는 것은 **경로를 끊는 글자**다 — `?`·`#`·공백.
     */
    private fun encode(path: String): String = buildString(path.length) {
        for (c in path) when (c) {
            '?' -> append("%3F")
            '#' -> append("%23")
            ' ' -> append("%20")
            '"' -> append("%22")
            '\\' -> append("%5C")
            else -> append(c)
        }
    }
}

/**
 * 호스트가 내줄 수 있는 것 하나.
 *
 * **호스트는 포맷을 모른다.** EPUB 이든 12단계의 docx 든 이 인터페이스 하나로 들어온다 —
 * `ExtractSupport`·`CoverSupport`·`PlayerSupport` 와 같은 모양의 이음매이고, 계층이
 * 뒤집히지 않게 하는 장치도 같다.
 */
fun interface ResourceProvider {
    /** @param path `WebHost.pathOf` 가 준 경로. 없으면 null 을 주면 404 가 나간다. */
    fun open(path: String): WebResource?
}

/**
 * 내주는 한 덩이.
 *
 * @param mimeType `text/html` 처럼 **타입만**. 파라미터(`; charset=…`)를 붙이면 WebView 가
 *   그 문자열을 통째로 타입으로 보고 다운로드로 처리한다.
 * @param encoding 글자 자원이면 `utf-8`, 이진이면 null.
 * @param stream 호스트가 닫는다.
 */
class WebResource(
    val mimeType: String,
    val encoding: String?,
    val stream: InputStream,
)
