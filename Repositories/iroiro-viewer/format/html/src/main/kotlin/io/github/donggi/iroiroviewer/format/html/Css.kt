package io.github.donggi.iroiroviewer.format.html

import io.github.donggi.iroiroviewer.format.UnsupportedFeatures

/**
 * CSS 안의 바깥 참조를 지운다.
 *
 * **CSS 를 이해하려 들지 않는다.** 파서를 만들면 선택자 문법과 중첩 규칙(`@media`,
 * `@supports`)을 전부 따라가야 하는데, 우리가 막으려는 것은 셋뿐이다 — 다른 파일을
 * 끌어오는 `@import`, 바깥을 가리키는 `url(…)`, 그리고 옛 브라우저에서 스크립트가 되던
 * 문법(`expression(`·`behavior:`·`-moz-binding`). 셋 다 **글자로 찾을 수 있다.**
 *
 * 나머지는 그대로 둔다. 글꼴·여백·색은 문서가 읽히는 모양 그 자체이고, 우리가 거르면
 * 만든 사람이 의도한 조판이 사라진다.
 */
internal object Css {

    /** 옛 브라우저에서 스크립트가 되던 문법. 지금 WebView 는 무시하지만 겹쳐 막는다. */
    private val POISON = listOf("expression(", "behavior:", "-moz-binding", "javascript:")

    /** `<style>` 블록 하나. */
    fun sanitize(
        css: String,
        resolve: HtmlSanitizer.Resolver,
        dropped: UnsupportedFeatures,
    ): String = rewrite(stripImports(stripComments(css), dropped), resolve, dropped)

    /** `style="…"` 속성 하나. 선언만 들어 있어 `@import` 가 있을 자리가 없다. */
    fun sanitizeDeclarations(
        css: String,
        resolve: HtmlSanitizer.Resolver,
        dropped: UnsupportedFeatures,
    ): String = rewrite(stripComments(css), resolve, dropped)

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

    /**
     * `@import` 규칙을 통째로 버린다.
     *
     * 끌어온 스타일시트를 우리가 내줄 수도 있지만, 그러려면 **CSS 안에서 다시 상대경로를
     * 풀고 그 안의 `@import` 를 또 따라가야** 한다. 깊이 상한이 필요한 재귀가 하나 더
     * 생기는 값을 치르기에는 EPUB 에서 `@import` 가 드물다. 지우고 그 사실을 센다.
     */
    private fun stripImports(css: String, dropped: UnsupportedFeatures): String {
        if (!css.contains("@import", ignoreCase = true)) return css
        val out = StringBuilder(css.length)
        var i = 0
        while (i < css.length) {
            val at = css.indexOf("@import", i, ignoreCase = true)
            if (at < 0) {
                out.append(css, i, css.length)
                break
            }
            out.append(css, i, at)
            dropped.record(UnsupportedFeatures.REMOTE_REFERENCE)
            // 규칙은 `;` 또는 블록으로 끝난다. 둘 중 먼저 오는 것까지 버린다.
            val semi = css.indexOf(';', at)
            val brace = css.indexOf('{', at)
            i = when {
                semi >= 0 && (brace < 0 || semi < brace) -> semi + 1
                brace >= 0 -> skipBlock(css, brace)
                else -> css.length
            }
        }
        return out.toString()
    }

    private fun skipBlock(css: String, open: Int): Int {
        var depth = 0
        var i = open
        while (i < css.length) {
            when (css[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return i + 1
                }
            }
            i++
        }
        return css.length
    }

    private fun rewrite(
        css: String,
        resolve: HtmlSanitizer.Resolver,
        dropped: UnsupportedFeatures,
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
        if (!text.contains("url(", ignoreCase = true)) return text

        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val at = text.indexOf("url(", i, ignoreCase = true)
            if (at < 0) {
                out.append(text, i, text.length)
                break
            }
            out.append(text, i, at)
            val close = text.indexOf(')', at)
            if (close < 0) {
                // 닫히지 않은 `url(`. 남겨 두면 뒤의 글이 전부 URL 로 읽힌다.
                dropped.record(UnsupportedFeatures.REMOTE_REFERENCE)
                break
            }
            val raw = text.substring(at + 4, close).trim().trim('"', '\'')
            // 스킴 판정은 [Urls] 한 곳이다. 여기서 또 적으면 두 벌이 된다.
            // **다른 SVG 파일의 조각(`x.svg#f`)은 가리키지 않는다.** `filter`·`mask`·`clip-path`·`marker` 는 그 문서를 가져와
            // 가리킨 요소를 쓰는데, SVG 는 위생을 거치지 않은 날것으로 나간다(`<use>` 와 같은 까닭 — `HtmlSanitizer`).
            // 같은 문서 안(`#f`)은 그대로 둔다.
            val resolved = if (isOtherSvgFragment(raw)) {
                dropped.record(UnsupportedFeatures.REMOTE_REFERENCE)
                null
            } else {
                Urls.rewrite(raw, resolve, dropped)
            }
            if (resolved == null) {
                // **`none` 으로 바꾼다.** 선언을 지우면 중괄호 균형이 깨지고, 빈
                // `url()` 로 두면 WebView 가 그 쪽을 다시 가져오려 든다.
                out.append("none")
            } else {
                out.append("url(\"").append(escape(resolved)).append("\")")
            }
            i = close + 1
        }
        return out.toString()
    }

    /** 다른 파일인 SVG 의 조각을 가리키는가(`a/b.svg#id`). 같은 문서 안(`#id`)은 아니다. */
    private fun isOtherSvgFragment(raw: String): Boolean {
        val hash = raw.indexOf('#')
        if (hash <= 0) return false
        val path = raw.substring(0, hash).substringBefore('?')
        return path.endsWith(".svg", ignoreCase = true) || path.endsWith(".svgz", ignoreCase = true)
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
            '"', '\'', '(', ')', '\\', '\n', '\r' -> Unit
            else -> append(c)
        }
    }
}
