package io.github.donggi.iroiroviewer.format.html

import io.github.donggi.iroiroviewer.format.UnsupportedFeatures

/**
 * 문서 안의 URL 하나를 어떻게 다룰지 정하는 **한 곳**.
 *
 * ## 스킴 판정을 [HtmlSanitizer.Resolver] 에게 맡기지 않는다
 *
 * 처음에는 "바깥인지 아닌지는 리졸버가 안다" 고 두었는데, 그러면 리졸버를 쓰는 쪽마다
 * (11단계의 EPUB, 12·13단계의 docx·HWPX) **스킴 검사를 한 벌씩 다시 써야 한다.**
 * 한 곳이 `data:text/html` 을 빠뜨리는 날 그 문서는 창 안에서 새 문서를 연다.
 *
 * 그래서 규칙을 여기 못 박는다. 리졸버가 답하는 것은 **'이 상대경로를 내줄 수 있는가'**
 * 하나이고, 스킴이 붙은 것은 리졸버에 닿지도 않는다.
 *
 * | 모양 | 결과 |
 * |---|---|
 * | `#주석1` | 그대로. 같은 쪽 안의 이동이라 내줄 것이 없다 |
 * | `data:image/…` | 그대로. 바깥으로 나가지 않고 표지가 흔히 이 모양이다 |
 * | `//cdn/x.png` | 버린다. 스킴만 생략했을 뿐 바깥이다 |
 * | `http:` · `file:` · `content:` · `data:text/html` | 버린다 |
 * | `javascript:` · `vbscript:` | 버린다. **스크립트로 센다** |
 * | 그 밖(상대경로) | 리졸버에게 묻는다 |
 */
internal object Urls {

    private const val DATA_IMAGE = "data:image/"

    fun rewrite(
        raw: String,
        resolve: HtmlSanitizer.Resolver,
        dropped: UnsupportedFeatures,
    ): String? {
        // **공백과 제어문자를 먼저 턴다.** `java\nscript:` 처럼 가운데를 끊어 스킴 검사를
        // 피해 가는 것이 오래된 수법이다.
        val value = raw.filterNot { it.isWhitespace() || it.code < 0x20 }
        if (value.isEmpty()) return null
        if (value.startsWith("#")) return value
        if (value.startsWith(DATA_IMAGE, ignoreCase = true)) return value
        if (value.startsWith("//")) {
            dropped.record(UnsupportedFeatures.REMOTE_REFERENCE)
            return null
        }
        val scheme = schemeOf(value)
        if (scheme != null) {
            dropped.record(
                if (scheme == "javascript" || scheme == "vbscript") UnsupportedFeatures.SCRIPT
                else UnsupportedFeatures.REMOTE_REFERENCE
            )
            return null
        }
        val resolved = resolve.resolve(value)
        if (resolved == null) dropped.record(UnsupportedFeatures.REMOTE_REFERENCE)
        return resolved
    }

    /**
     * 스킴이 붙어 있으면 소문자 이름, 아니면 null.
     *
     * RFC 3986 의 문법 그대로다 — 첫 글자는 영문, 그 뒤는 영문·숫자·`+`·`-`·`.`, 그리고
     * `:`. **`:` 앞에 `/` 가 있으면 스킴이 아니다**(`a/b:c.png` 는 경로다).
     */
    fun schemeOf(url: String): String? {
        val colon = url.indexOf(':')
        if (colon <= 0) return null
        for (i in 0 until colon) {
            val c = url[i]
            val ok = if (i == 0) c.isLetter()
            else c.isLetterOrDigit() || c == '+' || c == '-' || c == '.'
            if (!ok) return null
        }
        return url.substring(0, colon).lowercase()
    }
}
