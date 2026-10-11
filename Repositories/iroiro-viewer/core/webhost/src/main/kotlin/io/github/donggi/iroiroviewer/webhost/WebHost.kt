package io.github.donggi.iroiroviewer.webhost

import android.net.Uri
import java.io.InputStream
import java.security.SecureRandom

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
 * 요청이 닿을 곳이 없다. (1차 방어는 `INTERNET` 을 선언하지 않는 것이다 — 2026-10-05 에 SFTP 로 그것을 열었다가 10-09 에 다시
 * 걷었다. 그 뒤에 가로채기와 `blockNetworkLoads` 가 서고 이것이 마지막에 선다 — 방어는 겹쳐 두어야 한 겹이 무너져도 남는다.)
 *
 * `https` 를 쓰는 것은 WebView 가 스킴에 따라 **혼합 콘텐츠·보안 출처 규칙**을 달리
 * 적용하기 때문이다. 알 수 없는 사용자 정의 스킴은 불투명 출처가 되어 CSS·글꼴이 조용히
 * 막히는 판이 있다.
 *
 * ## 스크립트를 돌리는 쪽은 저마다의 호스트를 받는다([isolatedHost])
 *
 * 잠긴 쪽(책·문서·미리보기)은 스크립트가 꺼져 있어 한 호스트를 나눠 써도 서로에게 아무것도 남기지 못한다. 스크립트를 켠 쪽은
 * 다르다 — 브라우저는 저장소(IndexedDB·캐시)와 **서비스 워커**를 출처마다 둔다. 같은 호스트를 쓰면 한 쪽이 등록한 서비스 워커가
 * 그 뒤에 여는 책의 장 요청을 가로채 다른 글을 보여 줄 수 있다. 그래서 그런 쪽은 열 때마다 새로 지은 호스트에서 연다 — 무엇을 남겨도
 * 그것을 읽을 **다른** 쪽이 오지 않는다. (액티비티가 다시 서면 — 밤 모드·언어 — 같은 쪽이 같은 호스트로 다시 읽힌다. 같은 문서가 제
 * 것을 다시 보는 것이다.)
 */
object WebHost {

    const val SCHEME = "https"
    const val HOST = "book.iroiro.invalid"

    /** 스크립트를 돌리는 쪽들의 호스트가 붙는 자리. `.invalid` 아래라 어디에도 닿지 않는다(위). */
    private const val ISOLATED_DOMAIN = "live.iroiro.invalid"

    private val random = SecureRandom()

    /** 책 안의 경로 하나를 우리 쪽 URL 로. [host] 는 그 쪽의 호스트([HOST] 또는 [isolatedHost] 가 준 것). */
    fun urlFor(path: String, host: String = HOST): String = "$SCHEME://$host/" + encode(path)

    /**
     * 우리 쪽 URL 이면 책 안의 경로, 아니면 null. [host] 가 아닌 호스트는 — 우리의 다른 호스트라도 — 우리 것이 아니다.
     *
     * **경로를 정규화하지 않는다.** `..` 을 푸는 것은 자원을 내주는 쪽(`ResourceProvider`)
     * 의 일이다 — 그쪽이 책의 구조를 알고, 여기는 호스트만 안다.
     */
    fun pathOf(uri: Uri, host: String = HOST): String? {
        if (!uri.scheme.equals(SCHEME, ignoreCase = true)) return null
        if (!uri.host.equals(host, ignoreCase = true)) return null
        return uri.path?.removePrefix("/")?.takeIf { it.isNotEmpty() }
    }

    /**
     * 스크립트를 돌리는 쪽 하나만의 호스트. 부를 때마다 다르다(128비트 난수) — 위 '저마다의 호스트'. 글자는 소문자 16진뿐이라 URL 에
     * 그대로 적힌다.
     */
    fun isolatedHost(): String {
        val bytes = ByteArray(ISOLATED_BYTES).also { random.nextBytes(it) }
        val sb = StringBuilder(ISOLATED_BYTES * 2 + ISOLATED_DOMAIN.length + 2)
        sb.append('s')
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(Character.forDigit(v ushr 4, 16)).append(Character.forDigit(v and 0x0F, 16))
        }
        return sb.append('.').append(ISOLATED_DOMAIN).toString()
    }

    private const val ISOLATED_BYTES = 16

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
 * **호스트는 포맷을 모른다.** EPUB 이든 SVG·HTML 미리보기든 이 인터페이스 하나로 들어온다 —
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
