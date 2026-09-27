package io.github.donggi.iroiroviewer.format.html

import io.github.donggi.iroiroviewer.format.UnsupportedFeatures
import io.github.donggi.iroiroviewer.safety.ParseLimits

/**
 * 변환기가 쓴 본문을 **화면에 띄울 문서 하나로** 마무리한다 — 흐름 문서(`FlowDocument`)의 공통 끝.
 *
 * 순서가 뜻을 갖는다. ① 본문을 [HtmlSanitizer] 에 통과시키고(변환기가 `HtmlWriter` 로 썼더라도 —
 * 겹쳐 둔 방어다), ② 포맷의 CSS 를 [Css] 로 한 번 거르고, ③ [HtmlShell] 로 싼다. CSS 는 변환기가
 * 적는 상수지만 거르는 비용이 작고, 언젠가 누가 문서의 값을 섞어 넣는 날을 막아 둔다.
 */
object FlowHtml {

    /**
     * @param body `<body>` 안쪽 HTML.
     * @param css 포맷이 얹는 스타일(상수).
     * @param resolve 본문 안의 상대 주소를 내줄 수 있는지 답한다.
     * @param dropped 버린 것을 여기에 더한다(문서의 `unsupported`).
     */
    fun finish(
        body: String,
        css: String,
        resolve: HtmlSanitizer.Resolver,
        limits: ParseLimits,
        dropped: UnsupportedFeatures,
    ): String {
        val result = HtmlSanitizer.sanitize(body, resolve, limits)
        for ((kind, n) in result.dropped.snapshot()) dropped.record(kind, n)
        val safeCss = Css.sanitize(css, resolve, UnsupportedFeatures())
        return HtmlShell.wrap(result.html, safeCss)
    }
}
