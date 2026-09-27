package io.github.donggi.iroiroviewer.format.html

/**
 * 위생을 거친 본문을 **화면에 띄울 문서 하나로** 만든다.
 *
 * ## 스타일을 문서 뒤에 얹는다
 *
 * 우리 스타일을 `<head>` 의 **끝**에 넣는다. 앞에 넣으면 책이 가진 스타일시트가 우리
 * 것을 덮어써서 여백도 글자 크기도 책 마음대로가 된다. 뒤에 넣으면 같은 특이도에서
 * 우리가 이긴다.
 *
 * **그래도 책의 조판을 지우지 않는다.** 우리가 손대는 것은 셋뿐이다 — 화면 폭에 맞추는
 * 것, 그림이 화면을 넘지 않게 하는 것, 그리고 읽을 만한 여백. 글꼴·색·줄 간격은
 * 만든 사람이 정한 것이고, 그것을 덮으면 남는 것은 '안전한 문서' 가 아니라 **망가진
 * 문서**다(`Css` 의 주석과 같은 판단이다).
 */
object HtmlShell {

    /**
     * 읽기용 기본 스타일.
     *
     * `overflow-wrap` 은 긴 URL 한 줄이 가로 스크롤을 만드는 것을 막는다 — 세로로 읽는
     * 화면에서 가로 스크롤이 생기면 쪽을 넘기는 제스처와 다툰다.
     *
     * `word-break:keep-all` 은 낱말 사이를 띄어 쓰는 **한국어의 규칙**이다(낱말 가운데서 줄을 나누지 않는다). 띄어 쓰지
     * 않는 일본어·중국어에 걸면 문장 부호에서만 줄이 나뉘어 줄 끝이 들쭉날쭉하고, 긴 구절은 `overflow-wrap` 이 아무
     * 데서나 자른다(크롬에서 실측 — 실세계 말뭉치 검토). 그래서 그 둘은 되돌린다. 문서의 언어는 `lang` 으로 안다([wrap]).
     */
    private const val READER_CSS = """
html{-webkit-text-size-adjust:100%;}
body{margin:0;padding:16px 18px 32px;overflow-wrap:break-word;word-break:keep-all;}
:lang(ja),:lang(zh){word-break:normal;}
img,svg,video,table{max-width:100%;height:auto;}
pre{white-space:pre-wrap;}
"""

    /**
     * @param html [HtmlSanitizer] 를 지난 본문.
     * @param extraCss 화면이 더 얹고 싶은 것(글자 크기 등). 이미 위생을 거친 값이어야 한다.
     * @param lang 문서가 스스로 언어를 적지 않았을 때 쓸 언어(EPUB 의 `dc:language`). 언어 태그 모양이 아니면 버린다 —
     *   책이 적은 값이라 속성 밖으로 새면 안 된다. 문서가 `lang`·`xml:lang` 을 적었으면 그것이 이긴다.
     */
    fun wrap(html: String, extraCss: String = "", lang: String? = null): String {
        val style = "<style>$READER_CSS$extraCss</style>"
        val tagLang = lang?.trim()?.takeIf { LANG_TAG.matches(it) }
        val headClose = indexOfTag(html, "</head")
        if (headClose >= 0) return standards(withLang(html.substring(0, headClose) + style + html.substring(headClose), tagLang))

        val headOpen = indexOfTag(html, "<head")
        if (headOpen >= 0) {
            val close = html.indexOf('>', headOpen)
            if (close >= 0) {
                return standards(withLang(html.substring(0, close + 1) + style + html.substring(close + 1), tagLang))
            }
        }

        // `<head>` 가 없는 조각이 들어올 수 있다(12·13단계의 파서는 본문만 만든다).
        // 그때는 문서 하나를 통째로 만든다.
        val langAttr = tagLang?.let { " lang=\"$it\"" } ?: ""
        return "<!DOCTYPE html><html$langAttr><head><meta charset=\"utf-8\"/>" +
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"/>" +
            style + "</head><body>" + html + "</body></html>"
    }

    /**
     * 문서 맨 앞에 `<!DOCTYPE html>` 을 둔다(이미 있으면 그대로).
     *
     * **없으면 WebView 가 쿼크 모드로 그린다.** 위생기는 DOCTYPE 을 지우고(XHTML 의 DOCTYPE 에는 엔티티
     * 선언이 섞인다), 제 `<head>` 를 가진 문서(EPUB 의 장)는 위의 두 갈래로 오므로 DOCTYPE 없이 나가
     * 왔다. 원본 XHTML 은 XML 이라 어느 읽기 프로그램에서도 표준 모드인데, 우리만 `text/html` 로 내주며
     * 쿼크 모드가 됐다 — 쿼크 모드의 표는 본문의 글자 크기를 물려받지 않는다(크롬에서 실측: 본문
     * 30px, 표 칸 16px). 글 대조로는 보이지 않는 결함이라 말뭉치 검토가 뒤늦게 잡았다.
     */
    private fun standards(doc: String): String =
        if (doc.trimStart().startsWith("<!DOCTYPE", ignoreCase = true)) doc else "<!DOCTYPE html>$doc"

    /**
     * `<html>` 에 언어가 없으면 [lang] 을 단다. 뿌리 요소가 없거나 이미 적혀 있으면 그대로.
     *
     * 언어를 **속성으로 읽는다** — 태그 글자에서 `lang=` 을 찾으면 다른 속성의 값(`title=" lang=x"`)에 속고, 빈 `lang=""`
     * (위생기가 빈 `xml:lang` 을 옮긴 것)도 '적혀 있다' 로 읽어 책의 언어를 버렸다(검토가 잡았다). 빈 `lang` 은 바꾼다.
     */
    private fun withLang(doc: String, lang: String?): String {
        if (lang == null) return doc
        val at = indexOfTag(doc, "<html")
        if (at < 0) return doc
        val end = doc.indexOf('>', at)
        if (end < 0) return doc
        var tag = doc.substring(at, end)
        var emptyLang: MatchResult? = null
        for (m in ATTR.findAll(tag)) {
            val name = m.groupValues[1].lowercase()
            if (name != "lang" && name != "xml:lang") continue
            if (m.groupValues[2].isNotBlank()) return doc
            if (name == "lang" && emptyLang == null) emptyLang = m
        }
        if (emptyLang != null) tag = tag.removeRange(emptyLang.range)
        val insertAt = if (tag.endsWith("/")) tag.length - 1 else tag.length
        return doc.substring(0, at) + tag.substring(0, insertAt) + " lang=\"$lang\"" + tag.substring(insertAt) + doc.substring(end)
    }

    /** BCP 47 의 모양(`ja`·`zh-Hant`·`ko-KR`). 뜻까지 검사하지는 않는다 — 속성 밖으로 새지 않게 할 뿐이다. */
    private val LANG_TAG = Regex("[A-Za-z]{1,8}(-[A-Za-z0-9]{1,8}){0,7}")

    /** 따옴표로 싼 속성 하나(위생기가 쓰는 모양 — 값 안의 `"` 는 `&quot;` 로 나간다). */
    private val ATTR = Regex("\\s([A-Za-z_:][-A-Za-z0-9_:.]*)\\s*=\\s*\"([^\"]*)\"")

    /**
     * 내용이 **날것의 글**인 요소(HTML 파서가 그 안의 `<` 를 태그로 읽지 않는다). 그 안의 `</head`·`<html` 은 태그가
     * 아니므로 [indexOfTag] 가 건너뛴다 — `<title>` 안의 `</head>` 에 우리 스타일을 끼우면 스타일이 제목 글자가 되고,
     * `<xmp>` 안이면 CSS 가 화면에 찍힌다(검토가 잡았다).
     */
    private val RAW_TEXT = listOf("style", "title", "textarea", "xmp", "noembed", "script")

    /**
     * 태그 하나의 자리. **문자열 검색이 아니다** — `</heading>` 처럼 접두어가 같은 것을 골라내야 하고, 주석과
     * 날것의 글([RAW_TEXT]) 안의 글자는 태그가 아니므로 건너뛴다.
     */
    private fun indexOfTag(html: String, prefix: String): Int {
        var from = 0
        while (true) {
            val lt = html.indexOf('<', from)
            if (lt < 0) return -1
            if (html.startsWith("<!--", lt)) {
                val close = html.indexOf("-->", lt + 4)
                if (close < 0) return -1
                from = close + 3
                continue
            }
            val raw = RAW_TEXT.firstOrNull { tagAt(html, lt, "<$it") }
            if (raw != null) {
                val close = html.indexOf("</$raw", lt + raw.length + 1, ignoreCase = true)
                if (close < 0) return -1
                from = close + raw.length + 2
                continue
            }
            if (tagAt(html, lt, prefix)) return lt
            from = lt + 1
        }
    }

    /** [at] 에 [prefix] 로 시작하는 태그가 있는가(`<head` 와 `<header` 를 가른다). */
    private fun tagAt(html: String, at: Int, prefix: String): Boolean {
        if (!html.startsWith(prefix, at, ignoreCase = true)) return false
        val after = at + prefix.length
        if (after >= html.length) return false
        val c = html[after]
        return c == '>' || c == '/' || c.isWhitespace()
    }
}
