package io.github.donggi.iroiroviewer.format.html

import io.github.donggi.iroiroviewer.format.UnsupportedFeatures
import io.github.donggi.iroiroviewer.safety.ParseLimitExceededException
import io.github.donggi.iroiroviewer.safety.ParseLimits

/**
 * 문서에서 나온 HTML 을 **우리가 그릴 수 있는 모양으로 줄인다.**
 *
 * ## 이 모듈이 따로 있는 이유
 *
 * 11단계의 EPUB 이 첫 사용자이고, 12·13단계의 docx·HWPX 도 같은 길을 탄다 — 쪽 재현을
 * 포기하고 **흐름 렌더**로 가기로 한 이상(요구사항의 '지원하지 않는 것'), 그 셋이 내놓는
 * 것은 결국 HTML 이다. 그러면 위생 규칙도 한 벌이어야 한다. 5단계가 문구를 두 벌 두고
 * 양쪽 다 조용히 실패한 것이 이 저장소의 교훈이다.
 *
 * ## DOM 을 만들지 않는다
 *
 * 저장소 규칙이 'DOM 파싱 금지' 이고, 여기서는 그것이 안전 문제이기도 하다 — 임의의
 * 사용자 파일에서 온 HTML 은 깊이 수만 겹으로 중첩될 수 있고, 트리를 만들면 그 깊이가
 * 그대로 스택이 된다. **토큰을 흘려보내며 고친다.** 그래서 이 함수는 잘못 닫힌 태그를
 * 고쳐 주지도 않는다 — 그 일은 WebView 의 파서가 우리보다 잘한다.
 *
 * ## 무엇을 막는가
 *
 * `INTERNET` 을 선언하지 않는 것이 이미 OS 수준에서 소켓을 막는다. 그런데도 여기서 원격
 * 참조를 **지우는** 이유는 둘이다. ① 막힌 요청이라도 WebView 는 시도하고, 그동안 화면이
 * 비어 보인다. ② `INTERNET` 결정은 언젠가 누가 뒤집을 수 있는 한 줄이고, 그때 이 위생이
 * 없으면 EPUB 의 추적 픽셀이 그날 바로 살아난다. **방어는 겹쳐 두어야 한 겹이 무너져도
 * 남는다.**
 *
 * 스크립트는 WebView 쪽에서도 끄지만(`javaScriptEnabled = false`) 같은 이유로 여기서도
 * 지운다.
 */
object HtmlSanitizer {

    /**
     * 문서 안의 상대 URL 을 **우리가 내줄 수 있는 URL** 로 바꾼다.
     *
     * @return 내줄 수 없으면 null. 그러면 그 속성이 통째로 지워진다.
     */
    fun interface Resolver {
        fun resolve(url: String): String?
    }

    /**
     * @param html 고친 결과.
     * @param dropped 버린 것의 종류별 개수. 화면이 '간이 렌더' 배지에 쓴다.
     */
    data class Result(val html: String, val dropped: UnsupportedFeatures)

    /**
     * 통째로 버리는 요소. **내용까지 버린다.**
     *
     * `template` 이 여기 있는 이유는 그 안의 것이 그려지지 않아야 하는데 우리가 태그만
     * 지우면 내용이 본문으로 튀어나오기 때문이다.
     */
    private val DROP_TREE = setOf(
        "script", "iframe", "frame", "frameset", "noframes", "object", "embed",
        "applet", "template",
    )

    /**
     * 태그만 지우고 **내용은 남기는** 요소.
     *
     * 폼과 미디어가 여기 있다. 내용까지 버리면 대체 텍스트(`<video>` 안의 안내문,
     * `<noscript>` 안의 본문)가 함께 사라지는데, 그것은 **읽는 사람이 보려던 글**이다.
     */
    private val UNWRAP = setOf(
        "form", "button", "select", "option", "optgroup", "textarea", "label",
        "fieldset", "legend", "canvas", "noscript", "audio", "video", "picture",
    )

    /** 짝이 없는 요소. 지울 때 여는 태그만 지우면 된다. */
    private val VOID = setOf(
        "area", "base", "br", "col", "embed", "hr", "img", "input", "link",
        "meta", "param", "source", "track", "wbr",
    )

    /** 여는 태그를 지우되 짝도 없는 것. `base` 는 상대 URL 을 통째로 바꿔 버린다. */
    private val DROP_VOID = setOf("base", "meta", "input", "param", "source", "track", "area")

    /** 값이 URL 인 속성. 이것만 [Resolver] 를 지난다. */
    private val URL_ATTRS = setOf(
        "href", "src", "xlink:href", "poster", "background", "action", "formaction",
        "cite", "longdesc", "data", "usemap",
    )

    /** 값이 URL 목록이라 우리가 다루지 않는 속성. 통째로 지운다. */
    private val DROP_ATTRS = setOf("srcset", "imagesrcset", "ping", "integrity", "crossorigin")

    /**
     * **바깥 파일로 걸린 스타일시트**를 고친다.
     *
     * 이것이 없으면 위생에 구멍이 하나 남는다 — 장 안의 `<style>` 은 [sanitize] 가 보지만
     * `<link rel="stylesheet" href="book.css">` 로 걸린 파일은 우리 손을 거치지 않고
     * 그대로 WebView 에 들어간다. 그 파일 안의 `url(https://…)` 한 줄이면 `INTERNET` 결정을
     * 뒤집는 날 추적 픽셀이 살아난다.
     */
    fun sanitizeCss(
        css: String,
        resolve: Resolver,
        limits: ParseLimits = ParseLimits.DEFAULT,
    ): Result {
        val cap = limits.maxXmlBytes
        if (css.length > cap) {
            throw ParseLimitExceededException("maxXmlBytes", "${css.length}자 (상한 $cap)")
        }
        val dropped = UnsupportedFeatures()
        return Result(Css.sanitize(css, resolve, dropped), dropped)
    }

    fun sanitize(
        input: String,
        resolve: Resolver,
        limits: ParseLimits = ParseLimits.DEFAULT,
    ): Result {
        val cap = limits.maxXmlBytes
        if (input.length > cap) {
            throw ParseLimitExceededException("maxXmlBytes", "${input.length}자 (상한 $cap)")
        }
        val out = StringBuilder(input.length)
        val dropped = UnsupportedFeatures()
        var i = 0
        var doctypeSeen = false

        while (i < input.length) {
            val lt = input.indexOf('<', i)
            if (lt < 0) {
                out.append(input, i, input.length)
                break
            }
            out.append(input, i, lt)

            // 주석·DOCTYPE·처리 명령은 그대로 버린다. 그릴 것이 없고, 조건부 주석처럼
            // 옛 브라우저만 읽던 것이 섞여 있을 수 있다.
            if (input.startsWith("<!--", lt)) {
                i = skipTo(input, lt + 4, "-->")
                continue
            }
            if (input.startsWith("<![CDATA[", lt)) {
                // CDATA 는 XHTML 의 스타일·스크립트에서 온다. 내용을 그대로 두면
                // 그 안의 `<` 가 태그로 읽히므로 통째로 버린다.
                i = skipTo(input, lt + 9, "]]>")
                continue
            }
            if (input.startsWith("<!DOCTYPE", lt, ignoreCase = true)) {
                // 내부 부분집합은 **첫 DOCTYPE 에서만** 찾는다. 문서에 DOCTYPE 은 하나뿐인데, 되풀이된 DOCTYPE 마다
                // 끝을 멀리까지 찾으면 입력 길이의 제곱이 된다(검토가 잰 값: 0.5 MB 에 22초).
                i = if (doctypeSeen) skipTo(input, lt + 9, ">") else skipDoctype(input, lt + 9)
                doctypeSeen = true
                continue
            }
            if (input.startsWith("<!", lt) || input.startsWith("<?", lt)) {
                i = skipTo(input, lt + 2, ">")
                continue
            }

            val tag = readTag(input, lt)
            if (tag == null) {
                // `<` 가 태그의 시작이 아니다. 글자로 취급하되 태그로 읽히지 않게 한다.
                out.append("&lt;")
                i = lt + 1
                continue
            }

            if (tag.name in DROP_TREE) {
                dropped.record(kindOf(tag.name))
                i = if (tag.selfClosing || tag.name in VOID) tag.end
                else skipElement(input, tag.end, tag.name)
                continue
            }
            if (tag.closing) {
                // **짝 없는 요소의 닫는 태그는 쓰지 않는다.** XHTML 의 `<br></br>` 은 줄바꿈 하나지만 HTML
                // 파서는 `</br>` 을 `<br>` 로 읽어 줄바꿈이 둘이 된다(HTML 명세의 예외 규칙). 다른 짝 없는 요소의
                // 닫는 태그는 HTML 이 버리므로 쓰지 않아도 뜻이 같다 — `<div/>` 와 같은 갈래의 결함이다.
                if (tag.name in UNWRAP || tag.name in VOID) {
                    i = tag.end
                    continue
                }
                out.append("</").append(tag.name).append('>')
                i = tag.end
                continue
            }
            if (tag.name in UNWRAP || tag.name in DROP_VOID) {
                // **`<meta>` 는 세지 않는다.** 글자 코드·화면 폭 같은 문서 정보라 읽는 사람이 잃는 것이 없는데,
                // 세면 거의 모든 장이 '알 수 없는 요소' 를 하나씩 보태 멀쩡한 책마다 알림이 뜬다(실세계
                // 말뭉치: 2,014장짜리 책에서 2,014개). 알림은 **보이던 것이 빠졌을 때**의 말이다.
                if (tag.name != "meta") dropped.record(kindOf(tag.name))
                i = tag.end
                continue
            }
            if (tag.name == "style") {
                // **스스로 닫힌 `<style/>` 에는 내용이 없다.** 아래처럼 다음 `</style` 까지를 CSS 로 읽으면
                // 그 뒤의 본문 전체가 CSS 위생기로 들어가 글이 통째로 사라진다.
                if (tag.selfClosing) {
                    i = tag.end
                    continue
                }
                val close = input.indexOf("</style", tag.end, ignoreCase = true)
                val bodyEnd = if (close < 0) input.length else close
                val css = styleText(Css.sanitize(input.substring(tag.end, bodyEnd), resolve, dropped))
                if (css.isNotBlank()) out.append("<style>").append(css).append("</style>")
                i = if (close < 0) input.length else skipTo(input, close, ">")
                continue
            }

            writeStart(out, tag, resolve, dropped)
            i = tag.end
        }

        if (out.length > cap) {
            throw ParseLimitExceededException("maxXmlBytes", "결과 ${out.length}자 (상한 $cap)")
        }
        return Result(out.toString(), dropped)
    }

    // ---- 태그 하나 --------------------------------------------------------------

    private class Tag(
        val name: String,
        val closing: Boolean,
        val selfClosing: Boolean,
        val attrs: List<Pair<String, String>>,
        /** 태그 다음 글자의 자리. */
        val end: Int,
    )

    /** `<` 자리에서 시작해 태그 하나를 읽는다. 태그가 아니면 null. */
    private fun readTag(s: String, start: Int): Tag? {
        var i = start + 1
        if (i >= s.length) return null
        val closing = s[i] == '/'
        if (closing) i++
        val nameStart = i
        while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '-' || s[i] == ':')) i++
        if (i == nameStart) return null
        val name = s.substring(nameStart, i).lowercase()

        val attrs = ArrayList<Pair<String, String>>()
        var selfClosing = false
        while (i < s.length) {
            while (i < s.length && s[i].isWhitespace()) i++
            if (i >= s.length) break
            if (s[i] == '>') { i++; break }
            if (s[i] == '/') {
                selfClosing = true
                i++
                continue
            }
            val attrStart = i
            while (i < s.length && !s[i].isWhitespace() && s[i] != '=' && s[i] != '>' && s[i] != '/') i++
            if (i == attrStart) { i++; continue }
            val attr = s.substring(attrStart, i).lowercase()
            while (i < s.length && s[i].isWhitespace()) i++
            var value = ""
            if (i < s.length && s[i] == '=') {
                i++
                while (i < s.length && s[i].isWhitespace()) i++
                if (i < s.length && (s[i] == '"' || s[i] == '\'')) {
                    val quote = s[i]
                    val valueStart = ++i
                    while (i < s.length && s[i] != quote) i++
                    value = s.substring(valueStart, minOf(i, s.length))
                    if (i < s.length) i++
                } else {
                    val valueStart = i
                    while (i < s.length && !s[i].isWhitespace() && s[i] != '>') i++
                    value = s.substring(valueStart, i)
                }
            }
            attrs.add(attr to value)
        }
        return Tag(name, closing, selfClosing, attrs, i)
    }

    private fun writeStart(
        out: StringBuilder,
        tag: Tag,
        resolve: Resolver,
        dropped: UnsupportedFeatures,
    ) {
        // `<link>` 는 스타일시트만 남긴다. 다른 `rel`(preload·prefetch·license·author)은 그리는 데 쓰지 않는
        // 힌트라 **세지 않고** 버린다 — 예전에는 세되 그 주소도 따로 세어, 장마다 있는 `rel="license"
        // href="http://…"` 하나가 '바깥을 가리키는 참조' 둘이 됐다(실세계 말뭉치가 잡았다).
        if (tag.name == "link") {
            val rel = tag.attrs.firstOrNull { it.first == "rel" }?.second?.lowercase().orEmpty()
            if (!rel.split(' ', '\t', '\n', '\r').contains("stylesheet")) return
        }
        val kept = ArrayList<Pair<String, String>>(tag.attrs.size)
        for ((name, value) in tag.attrs) {
            when {
                // **`on…` 으로 시작하는 것은 예외 없이 지운다.** 목록을 적어 두면 그
                // 목록에 없는 새 이벤트가 그대로 지나간다.
                name.startsWith("on") -> dropped.record(UnsupportedFeatures.EVENT_HANDLER)
                name in DROP_ATTRS -> dropped.record(UnsupportedFeatures.REMOTE_REFERENCE)
                // **`<use>` 는 같은 문서 안(`#id`)만 가리킨다.** 다른 SVG 파일을 가리키면 브라우저가 그 문서를
                // 가져와 가리킨 부분을 장 안에 **복제**한다 — 그 SVG 는 위생을 거치지 않고 날것으로 나가므로(자원)
                // 그 안의 바깥 그림·링크가 장 안에서 살아난다(검토가 크롬에서 재현했다).
                tag.name == "use" && name in USE_HREF && !value.trimStart().startsWith("#") ->
                    dropped.record(UnsupportedFeatures.REMOTE_REFERENCE)
                name == "style" -> {
                    val css = Css.sanitizeDeclarations(value, resolve, dropped)
                    if (css.isNotBlank()) kept.add(name to css)
                }
                name in URL_ATTRS -> {
                    val url = Urls.rewrite(value, resolve, dropped)
                    if (url != null) kept.add(name to url)
                }
                isSafeName(name) -> kept.add(name to value)
                else -> Unit
            }
        }
        // 스타일시트인데 주소가 남지 않았으면 버린다. 주소를 버린 일은 [Urls] 가 이미 셌다 — 두 번 세지 않는다.
        if (tag.name == "link" && kept.none { it.first == "href" }) return
        // **`xml:lang` 을 `lang` 으로도 적는다.** XHTML 은 XML 이라 `xml:lang` 이 문서의 언어를 정하지만, 결과는
        // `text/html` 로 나가고 HTML 파서에서 그것은 뜻 없는 속성이다 — 크롬에서 실측: `:lang(ja)` 가 맞지 않고
        // 로캘이 `auto` 다. 그러면 일본어·중국어 책의 한자가 기기 로캘(한국어)의 자형으로 그려진다(한자 통합).
        // 실세계 말뭉치의 일본어 세로쓰기 책 둘이 `<html xml:lang="ja">` 만 적었다. `lang` 이 따로 있으면 그것이 이긴다.
        val xmlLang = kept.firstOrNull { it.first == "xml:lang" }?.second
        if (xmlLang != null && kept.none { it.first == "lang" }) kept.add("lang" to xmlLang)

        out.append('<').append(tag.name)
        for ((name, value) in kept) {
            out.append(' ').append(name).append("=\"").append(attrValue(value)).append('"')
        }
        when {
            tag.name in VOID -> out.append("/>")
            // **짝이 있는 요소를 스스로 닫았으면 닫는 태그를 우리가 쓴다.** 원본은 XHTML 이라
            // `<div/>`·`<a id="p1"/>`·`<title/>` 이 빈 요소지만, 결과는 `text/html` 로 나가고 HTML 파서는
            // 그 `/` 를 무시한다 — 뒤의 본문이 통째로 그 안에 들어간다. `<title/>` 이면 본문 전체가 제목이
            // 되어 화면이 빈다(실세계 말뭉치의 선형대수 책이 `<div/>`·`<span/>` 370개를 썼다). SVG·MathML
            // 안에서는 원래 스스로 닫기가 유효하므로 짝을 써도 뜻이 같다.
            tag.selfClosing -> out.append("></").append(tag.name).append('>')
            else -> out.append('>')
        }
    }

    private fun kindOf(name: String): String = when (name) {
        "script" -> UnsupportedFeatures.SCRIPT
        "iframe", "frame", "frameset", "noframes" -> UnsupportedFeatures.FRAME
        "object", "embed", "applet" -> UnsupportedFeatures.EMBEDDED_OBJECT
        "form", "button", "select", "option", "optgroup", "textarea", "label",
        "fieldset", "legend", "input" -> UnsupportedFeatures.FORM
        "audio", "video", "picture", "canvas", "template", "base", "meta",
        "param", "source", "track", "area" -> UnsupportedFeatures.UNKNOWN_ELEMENT
        else -> UnsupportedFeatures.UNKNOWN_ELEMENT
    }

    /**
     * 짝이 맞는 닫는 태그까지 건너뛴다.
     *
     * **중첩을 센다.** `<object><object></object></object>` 에서 첫 번째 `</object>` 를
     * 끝으로 보면 뒤의 하나가 본문으로 새어 나온다.
     */
    private fun skipElement(s: String, from: Int, name: String): Int {
        var depth = 1
        var i = from
        while (i < s.length) {
            val lt = s.indexOf('<', i)
            if (lt < 0) return s.length
            val tag = readTag(s, lt)
            if (tag == null) {
                i = lt + 1
                continue
            }
            if (tag.name == name) {
                if (tag.closing) {
                    depth--
                    if (depth == 0) return tag.end
                } else if (!tag.selfClosing) {
                    depth++
                }
            }
            i = tag.end
        }
        return s.length
    }

    private fun skipTo(s: String, from: Int, marker: String): Int {
        val at = s.indexOf(marker, from)
        return if (at < 0) s.length else at + marker.length
    }

    /**
     * `<!DOCTYPE …>` 를 건너뛴다. **내부 부분집합(`[ … ]`)이 있으면 그 끝까지.**
     *
     * XHTML 은 DOCTYPE 안에 엔티티 선언을 둘 수 있다(`<!ENTITY x SYSTEM "…">`). 첫 `>` 에서 끝내면 그것은
     * 엔티티 선언의 끝이라 남은 `]>` 가 글이 되어 **화면 맨 위에 찍힌다**(W3C 의 XXE 시험 책이 그랬다).
     * 부분집합의 끝은 `]` 뒤의 `>` 로 찾는다 — 따옴표를 세면 짝이 안 맞는 따옴표 하나가 본문 전체를
     * 삼킨다. 끝을 못 찾으면 예전처럼 첫 `>` 에서 끝낸다.
     */
    private fun skipDoctype(s: String, from: Int): Int {
        val gt = s.indexOf('>', from)
        if (gt < 0) return s.length
        // `[` 는 첫 `>` 앞에서만 찾는다. 입력 끝까지 찾으면 DOCTYPE 이 되풀이될 때마다 나머지 전체를 훑는다.
        var open = -1
        for (k in from until gt) {
            if (s[k] == '[') {
                open = k
                break
            }
        }
        if (open < 0) return gt + 1
        // 끝(`]` 뒤 공백 뒤 `>`)은 [MAX_DOCTYPE_SUBSET] 안에서만 찾는다 — 실물의 내부 부분집합은 몇 KB 다. 그보다 멀리
        // 있는 `]>` 는 부분집합의 끝이 아니라 본문의 글자일 가능성이 크고, 거기까지를 버리면 본문이 사라진다.
        val limit = minOf(s.length, open + MAX_DOCTYPE_SUBSET)
        var k = open + 1
        while (k < limit) {
            if (s[k] == ']') {
                var j = k + 1
                while (j < limit && s[j].isWhitespace()) j++
                if (j < limit && s[j] == '>') return j + 1
                k = j
                continue
            }
            k++
        }
        return gt + 1
    }

    private const val MAX_DOCTYPE_SUBSET = 64 * 1024

    /** `<use>` 의 주소 속성. */
    private val USE_HREF = setOf("href", "xlink:href")

    /**
     * `<style>` 에 넣는 CSS 의 `<` 를 CSS 이스케이프(`\3c `)로 바꾼다. **CSS 는 그 안의 `<` 를 막지 않으므로** 구멍이 둘
     * 있었다(말뭉치 검토가 크롬에서 재현했다).
     *
     * 1. 책의 `<style>` 안의 `</head>` 를 읽기용 껍데기가 진짜 `</head>` 로 알고 거기에 우리 `<style>` 을 끼우면, 우리
     *    `</style>` 이 책의 것을 먼저 닫아 **뒤의 CSS 글자가 위생을 거치지 않은 마크업**이 된다(`<img onerror>`·`<iframe>`).
     * 2. `<svg>`·`<math>` 안의 `<style>` 은 HTML 파서에게 보통 요소라 **안의 글이 마크업으로 읽힌다.**
     *
     * CSS 에서 `<` 는 선택자에도 값에도 뜻이 없고(문자열 안에서는 이스케이프가 같은 글자다), `<!--`(CDO)는 브라우저가
     * 버린다. 바꿔도 잃는 것이 없다. 스타일시트 **파일**(`sanitizeCss`)은 HTML 로 읽히지 않으므로 바꾸지 않는다.
     */
    private fun styleText(css: String): String = if ('<' in css) css.replace("<", "\\3c ") else css

    /**
     * 그대로 내보내도 되는 속성 이름인가.
     *
     * 이름에 `"` 나 공백이 섞이면 우리가 만드는 태그가 깨진다 — 그것이 곧 속성 주입이다.
     */
    private fun isSafeName(name: String): Boolean =
        name.isNotEmpty() && name.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == ':' || it == '.' }

    /**
     * 속성 값을 따옴표 안에 넣을 수 있게 한다.
     *
     * **`&` 는 건드리지 않는다.** 원본의 값은 이미 유효한 HTML 이라 `&amp;` 같은 엔티티가
     * 들어 있고, 여기서 다시 이스케이프하면 화면에 `&amp;amp;` 가 뜬다. 막아야 하는 것은
     * 따옴표와 꺾쇠로 태그를 빠져나가는 것 하나다.
     */
    private fun attrValue(value: String): String = buildString(value.length) {
        for (c in value) when (c) {
            '"' -> append("&quot;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            else -> append(c)
        }
    }
}
