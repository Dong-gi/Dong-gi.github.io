package io.github.donggi.iroiroviewer.format.html

import io.github.donggi.iroiroviewer.format.UnsupportedFeatures
import io.github.donggi.iroiroviewer.safety.ParseLimitExceededException

/**
 * CSS 안의 바깥 참조를 지운다.
 *
 * **CSS 를 이해하려 들지 않는다.** 파서를 만들면 선택자 문법과 중첩 규칙(`@media`,
 * `@supports`)을 전부 따라가야 하는데, 우리가 막으려는 것은 셋뿐이다 — 다른 파일을
 * 끌어오는 `@import`, 바깥을 가리키는 주소(`url(…)`·`image-set(…)`), 그리고 옛 브라우저에서
 * 스크립트가 되던 문법(`expression(`·`behavior:`·`-moz-binding`).
 *
 * 앞의 둘은 **글자로 찾지 않는다**([CssRefs]) — CSS 는 이름의 이스케이프를 푼 뒤에 함수와 규칙을 가르므로 `\75 rl(…)`·
 * `@\69mport` 도 같은 것이고, `image-set("…")` 은 따옴표만으로 주소다. 글자로 찾던 판은 그것들을 지나쳤다(그때는
 * 가로채기의 빈 404 와 `INTERNET` 미선언이 막고 있었다).
 *
 * 나머지는 그대로 둔다. 글꼴·여백·색은 문서가 읽히는 모양 그 자체이고, 우리가 거르면
 * 만든 사람이 의도한 조판이 사라진다.
 */
internal object Css {

    /** 옛 브라우저에서 스크립트가 되던 문법. 지금 WebView 는 무시하지만 겹쳐 막는다. */
    private val POISON = listOf("expression(", "behavior:", "-moz-binding", "javascript:")

    /**
     * `<style>` 블록이나 스타일시트 파일 하나.
     *
     * @param limit 결과가 이보다 길어지면 상한 예외 — 쓰는 도중에 잰다.
     * @param checkpoint 참조 몇백 개마다 — 부르는 쪽이 취소를 여기서 던진다.
     */
    fun sanitize(
        css: String,
        resolve: HtmlSanitizer.Resolver,
        dropped: UnsupportedFeatures,
        limit: Int = Int.MAX_VALUE,
        checkpoint: () -> Unit = {},
    ): String = rewrite(stripComments(css), resolve, dropped, limit, checkpoint)

    /** `style="…"` 속성 하나. 선언만 들어 있어야 하지만 `@import` 를 적어 둔 것도 같은 길로 지운다. */
    fun sanitizeDeclarations(
        css: String,
        resolve: HtmlSanitizer.Resolver,
        dropped: UnsupportedFeatures,
        limit: Int = Int.MAX_VALUE,
        checkpoint: () -> Unit = {},
    ): String = rewrite(stripComments(css), resolve, dropped, limit, checkpoint)

    private fun stripComments(css: String): String {
        if ("/*" !in css) return css
        val out = StringBuilder(css.length)
        var i = 0
        while (i < css.length) {
            val open = css.indexOf("/*", i)
            if (open < 0) {
                out.append(css, i, css.length)
                break
            }
            out.append(css, i, open)
            val close = css.indexOf("*/", open + 2)
            i = if (close < 0) css.length else close + 2
        }
        return out.toString()
    }

    private fun rewrite(
        css: String,
        resolve: HtmlSanitizer.Resolver,
        dropped: UnsupportedFeatures,
        limit: Int,
        checkpoint: () -> Unit,
    ): String {
        var text = css
        for (poison in POISON) {
            if (text.contains(poison, ignoreCase = true)) {
                dropped.record(UnsupportedFeatures.SCRIPT)
                // 이름을 못 알아보게 한 글자만 바꾼다. 선언 전체를 지우면 그 자리의
                // 중괄호 균형이 깨져 뒤의 규칙까지 함께 무너진다.
                text = replaceIgnoreCase(text, poison, "_blocked_")
            }
        }
        // 함수(`(`)도 규칙(`@`)도 없으면 참조가 있을 자리가 없다.
        if ('(' !in text && '@' !in text) {
            if (text.length > limit) throw ParseLimitExceededException("maxXmlBytes", "CSS ${text.length}자 (상한 $limit)")
            return text
        }
        // `@import` 는 규칙째 지운다 — 끌어온 스타일시트를 우리가 내줄 수도 있지만, 그러려면 CSS 안에서 다시 상대경로를 풀고
        // 그 안의 `@import` 를 또 따라가야 한다. 깊이 상한이 필요한 재귀가 하나 더 생기는 값을 치르기에는 드물다.
        return CssRefs.rewrite(
            text,
            limit = limit,
            checkpoint = checkpoint,
            onImport = { dropped.record(UnsupportedFeatures.REMOTE_REFERENCE) },
        ) { raw ->
            // 스킴 판정은 [Urls] 한 곳이다. 여기서 또 적으면 두 벌이 된다.
            //
            // **다른 파일의 조각(`x.svg#f`)으로 요소를 가리키지 못하게 한다.** `filter`·`mask`·`clip-path`·`marker`·`fill` 은 그 문서를
            // 가져와 가리킨 요소를 쓰는데, 그 문서는 위생을 거치지 않은 날것으로 나간다(`<use>` 와 같은 까닭 — `HtmlSanitizer`).
            // 같은 문서 안(`#f`)은 그대로 둔다. 예전에는 이름이 `.svg` 로 끝날 때만 막아 둘이 지나갔다:
            //
            // 1. 퍼센트로 적은 확장자(`x.sv%67#f`) — 책의 리졸버는 퍼센트를 풀어 찾는다. 이름은 **푼 뒤에** 본다([looksSvg]).
            // 2. 이름은 SVG 가 아닌데 매니페스트가 SVG 라고 적은 것(`x.dat`) — 책은 매니페스트의 형식으로 내준다. 이름으로는 가릴 수 없어
            //    **조각을 떼고** 가리킨다. 조각이 없으면 요소를 가리킬 수 없어 그 속성은 무시되고, 그림·글꼴로 쓰는 자리(`font.eot?#iefix`
            //    같은 옛 글꼴 꼼수)는 같은 파일을 그대로 싣는다(그림으로 실린 SVG 는 바깥을 싣지 않는다).
            val hash = raw.indexOf('#')
            when {
                hash <= 0 -> Urls.rewrite(raw, resolve, dropped)?.let { CssRefs.Out.Url(escape(it)) } ?: CssRefs.Out.None
                looksSvg(raw.substring(0, hash)) -> {
                    dropped.record(UnsupportedFeatures.REMOTE_REFERENCE)
                    CssRefs.Out.None
                }
                else -> Urls.rewrite(raw.substring(0, hash), resolve, dropped)?.let { CssRefs.Out.Url(escape(it)) } ?: CssRefs.Out.None
            }
        } ?: throw ParseLimitExceededException("maxXmlBytes", "CSS 결과가 상한 ${limit}자를 넘었다")
    }

    /** SVG 파일의 이름인가 — **퍼센트를 푼** 경로로 본다(책의 리졸버가 풀어서 찾는다). 질의(`?…`)는 뗀다. */
    private fun looksSvg(raw: String): Boolean {
        val path = percentDecodeAscii(raw.substringBefore('?'))
        return path.endsWith(".svg", ignoreCase = true) || path.endsWith(".svgz", ignoreCase = true)
    }

    /** `%XX` 를 그 바이트의 글자로. 확장자를 보는 데만 쓰므로 ASCII 밖은 신경 쓰지 않는다. */
    private fun percentDecodeAscii(s: String): String {
        if ('%' !in s) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%' && i + 2 < s.length) {
                val hi = Character.digit(s[i + 1], 16)
                val lo = Character.digit(s[i + 2], 16)
                if (hi >= 0 && lo >= 0) {
                    sb.append(((hi shl 4) or lo).toChar())
                    i += 3
                    continue
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    private fun replaceIgnoreCase(text: String, needle: String, with: String): String {
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val at = text.indexOf(needle, i, ignoreCase = true)
            if (at < 0) {
                out.append(text, i, text.length)
                break
            }
            out.append(text, i, at).append(with)
            i = at + needle.length
        }
        return out.toString()
    }

    /** 따옴표와 괄호를 막는다. 우리가 만드는 `url("…")` 를 빠져나가지 못하게 한다. */
    private fun escape(url: String): String = buildString(url.length) {
        for (c in url) when (c) {
            '"', '\'', '(', ')', '\\', '\n', '\r', '\u000C' -> Unit
            else -> append(c)
        }
    }
}
