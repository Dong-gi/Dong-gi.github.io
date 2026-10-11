package io.github.donggi.iroirobrowser.core.sites

import java.io.IOException

/**
 * 사이트의 답을 우리 실패로 가른다 — 두 사이트가 함께 쓴다. 가르는 기준은 KDoc 의 [SiteException.Reason] 그대로다: 없음(404·410)은
 * NOT_FOUND, 막음(403·HTML 확인 페이지)은 BLOCKED, 로그인(401)은 LOGIN_REQUIRED, 꼴이 다르면 UNEXPECTED. **프록시가 만든 답**
 * ([SiteHttpException.byProxy])은 사이트의 말이 아니므로 손대지 않고 그대로 던진다. 그 밖의 코드(429·5xx 등)도 그대로 — 잠깐의 일일 수 있어
 * 앱이 다시 해 볼지 정한다.
 */
internal object SiteResponses {

    fun classify(e: SiteHttpException, missing: SiteException.Reason, what: String): IOException {
        if (e.byProxy) return e
        return when (e.code) {
            404, 410 -> SiteException(missing, "$what: HTTP ${e.code}")
            401 -> SiteException(SiteException.Reason.LOGIN_REQUIRED, "$what: HTTP 401")
            403 -> SiteException(SiteException.Reason.BLOCKED, "$what: HTTP 403")
            else -> e
        }
    }

    /** 바이트를 글로 — UTF-8(깨진 바이트는 U+FFFD), 맨 앞의 BOM 은 뗀다. */
    fun text(bytes: ByteArray): String {
        val text = String(bytes, Charsets.UTF_8)
        return if (text.startsWith('\uFEFF')) text.substring(1) else text
    }

    /** 첫 글자(공백 뒤)가 `<` — JSON·JS 를 청했는데 HTML 이 왔다(Cloudflare 의 확인 페이지·로그인 벽·오류 페이지). */
    fun looksLikeHtml(text: String): Boolean {
        val i = firstNonSpace(text)
        return i < text.length && text[i] == '<'
    }

    fun firstNonSpace(text: String, from: Int = 0): Int {
        var i = from
        while (i < text.length && text[i].isWhitespace()) i++
        return i
    }

    /** 사이트의 JSON — 상한을 넘으면 TOO_LARGE, 꼴이 틀리면 UNEXPECTED. HTML 이면 BLOCKED. */
    fun json(text: String, what: String): Any? {
        if (looksLikeHtml(text)) throw SiteException(SiteException.Reason.BLOCKED, "$what: JSON 대신 HTML")
        return try {
            Json.parse(text, Json.SITE)
        } catch (e: JsonException) {
            throw jsonFailure(e, what)
        }
    }

    fun jsonFailure(e: JsonException, what: String): SiteException =
        SiteException(if (e.tooLarge) SiteException.Reason.TOO_LARGE else SiteException.Reason.UNEXPECTED, "$what: ${e.message}")

    fun unexpected(detail: String): Nothing = throw SiteException(SiteException.Reason.UNEXPECTED, detail)

    /** 정수 — JSON 의 정수, 또는 숫자만 든 글(pixiv 는 번호를 글로 준다). 소수·음의 0 같은 것은 null. */
    fun integer(value: Any?): Long? = when (value) {
        is Long -> value
        is String -> if (value.isNotEmpty() && value.length <= 18 && value.all { it in '0'..'9' }) value.toLong() else null
        else -> null
    }

    /** 참 — `true`, 0 이 아닌 정수, 글 `"1"`·`"true"`(hitomi 는 깃발을 1·0 으로 준다). */
    fun truthy(value: Any?): Boolean = when (value) {
        is Boolean -> value
        is Long -> value != 0L
        is Double -> value != 0.0
        is String -> value == "1" || value.equals("true", ignoreCase = true)
        else -> false
    }

    /** 양의 정수 번호(자릿수 상한) — 사이트의 번호와 주소의 번호에 함께 쓴다. */
    fun positiveId(text: String): Long? {
        if (text.isEmpty() || text.length > MAX_ID_DIGITS || text[0] == '0') return null
        if (!text.all { it in '0'..'9' }) return null
        return text.toLong()
    }

    /** 번호의 자릿수 상한 — pixiv 의 작품 번호가 2026 년에 9자리(1.5억)다. 12자리면 넉넉하고 Long 에 든다. */
    const val MAX_ID_DIGITS = 12
}
