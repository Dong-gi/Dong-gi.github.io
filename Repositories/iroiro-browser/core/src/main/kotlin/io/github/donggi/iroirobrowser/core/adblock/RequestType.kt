package io.github.donggi.iroirobrowser.core.adblock

import java.util.Locale

/**
 * 요청의 종류(비트 마스크) — 광고 규칙의 `$script`·`$image` 같은 선택지와 견준다. **WebView 는 요청의 종류를 알려 주지 않는다** — [guess] 가
 * `Accept` 머리와 확장자로 어림하고, 확실하지 않으면 여러 종류를 함께 돌려준다(그중 하나라도 규칙과 맞으면 맞는다).
 */
object RequestType {
    const val DOCUMENT = 1
    const val SUBDOCUMENT = 1 shl 1
    const val STYLESHEET = 1 shl 2
    const val SCRIPT = 1 shl 3
    const val IMAGE = 1 shl 4
    const val FONT = 1 shl 5
    const val MEDIA = 1 shl 6
    const val OBJECT = 1 shl 7
    const val XHR = 1 shl 8
    const val PING = 1 shl 9
    const val WEBSOCKET = 1 shl 10
    const val OTHER = 1 shl 11
    const val POPUP = 1 shl 12

    /** 종류를 적지 않은 규칙이 걸리는 것 — 주 문서와 팝업은 빼고 다(ABP·uBO 와 같다). */
    const val DEFAULT = SUBDOCUMENT or STYLESHEET or SCRIPT or IMAGE or FONT or MEDIA or OBJECT or XHR or PING or WEBSOCKET or OTHER

    const val ALL = DEFAULT or DOCUMENT or POPUP

    /** 규칙의 선택지 이름 → 종류. 모르는 이름이면 null. */
    fun byName(name: String): Int? = when (name) {
        "document", "doc" -> DOCUMENT
        "subdocument", "frame" -> SUBDOCUMENT
        "stylesheet", "css" -> STYLESHEET
        "script" -> SCRIPT
        "image" -> IMAGE
        "font" -> FONT
        "media" -> MEDIA
        "object" -> OBJECT
        "xmlhttprequest", "xhr" -> XHR
        "ping", "beacon" -> PING
        "websocket" -> WEBSOCKET
        "other" -> OTHER
        "popup" -> POPUP
        else -> null
    }

    /**
     * 요청의 종류를 어림한다. 주 문서는 확실하고, `Accept` 로는 문서(틀)·CSS·그림만 확실히 갈린다(크로미움이 그 셋에만 다른 값을 단다).
     * GET 이 아닌 요청(POST 등)은 스크립트·그림·글꼴·미디어일 수 없다 — XHR(fetch)이거나 핑(`sendBeacon`·`<a ping>`)이다. 핑은 언제나
     * POST 라 GET 은 핑이 아니다. 나머지(`* / *`)는 확장자로 고르고, 그래도 모르면 스크립트·XHR·글꼴·미디어·그 밖을 함께 돌려준다 —
     * 여럿이면 종류를 좁힌 막는 규칙은 걸리지 않는다([NetworkRule.matches]).
     */
    fun guess(url: String, accept: String?, isMainFrame: Boolean, method: String = "GET"): Int {
        if (isMainFrame) return DOCUMENT
        val a = accept?.lowercase(Locale.ROOT).orEmpty()
        when {
            a.startsWith("text/html") || a.startsWith("application/xhtml") -> return SUBDOCUMENT
            a.startsWith("text/css") -> return STYLESHEET
            a.startsWith("image/") -> return IMAGE
            a.startsWith("application/json") -> return XHR
        }
        if (!method.equals("GET", ignoreCase = true) && !method.equals("HEAD", ignoreCase = true)) return XHR or PING
        return when (extension(url)) {
            "js", "mjs" -> SCRIPT
            "css" -> STYLESHEET
            "png", "jpg", "jpeg", "gif", "webp", "svg", "ico", "avif", "bmp" -> IMAGE
            "woff", "woff2", "ttf", "otf", "eot" -> FONT
            // hls.js·dash.js 는 조각을 XHR 로 받지만 미디어로 본다 — 그 주소 자체가 영상·소리라 `$media` 규칙이 노린 것이다(앞 판과 같다).
            "mp4", "webm", "m3u8", "mpd", "ts", "m4s", "mp3", "aac", "ogg", "oga", "wav", "m4a", "flac" -> MEDIA
            "html", "htm" -> SUBDOCUMENT or XHR
            else -> SCRIPT or XHR or FONT or MEDIA or OTHER
        }
    }

    /** 주소 경로의 확장자(소문자). 없으면 빈 글. */
    internal fun extension(url: String): String {
        val path = url.substringBefore('#').substringBefore('?')
        val afterScheme = path.substringAfter("://", path)
        val slash = afterScheme.indexOf('/')
        if (slash < 0) return ""
        val last = afterScheme.substring(slash + 1).substringAfterLast('/')
        val dot = last.lastIndexOf('.')
        return if (dot < 0 || dot == last.length - 1) "" else last.substring(dot + 1).lowercase(Locale.ROOT)
    }
}
