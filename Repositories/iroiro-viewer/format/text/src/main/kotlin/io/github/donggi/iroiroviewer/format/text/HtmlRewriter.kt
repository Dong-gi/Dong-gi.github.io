package io.github.donggi.iroiroviewer.format.text

import io.github.donggi.iroiroviewer.format.html.CharRefs
import io.github.donggi.iroiroviewer.format.html.CssRefs
import java.util.Locale

/**
 * HTML 미리보기의 **앞 패스**. 위생기(`HtmlSanitizer`)에 넘기기 전에 문서가 가리키는 것을 전부 우리 번호(`/r/N`)로 바꿔 적는다.
 *
 * ## 위생기를 고치지 않고 앞에 하나를 두는 이유
 *
 * 위생기는 EPUB·마크다운 미리보기가 함께 쓰고, 그쪽의 리졸버는 **책 안의 이름**을 받는다. 사용자의 HTML 은 다르다 — 이름이 퍼센트로
 * 적히기도 날것으로 적히기도 하고(공백·한글), 그림 대신 `srcset`·`<picture>` 를 쓴다. 위생기는 주소의 공백을 지우므로(함정 표)
 * 그대로 넘기면 그림이 조용히 사라진다. 그래서 여기서 **브라우저가 읽는 대로** 주소를 읽어 번호로 바꾸고, 위생기의 리졸버는 우리
 * 번호만 통과시킨다([HtmlPreview]). 위생은 위생기가 그대로 한다 — 여기서 잘못 읽어도 결과는 위생기를 한 번 더 지난다(겹친 방어).
 * CSS 의 주소는 위생기와 **한 벌**인 `CssRefs` 가 읽는다(이스케이프로 쓴 `\75 rl(`·`image-set` 의 문자열까지 — 두 겹이 같은 것을 본다).
 *
 * ## 무엇을 바꾸는가
 *
 * * 그림 — `<img src>`·`srcset`(하나를 골라 `src` 로)·`<picture><source srcset>`, `background` 속성, SVG 의 `<image href>`.
 * * 스타일시트 — `<link rel="stylesheet" href>`. 파일은 화면이 읽어 와 [HtmlPreview.finish] 가 고친다.
 * * CSS — `<style>` 과 `style=""` 의 주소. `style=""` 은 문자 참조를 먼저 푼다(`url&lpar;…` 도 주소다).
 * * 링크 — 같은 쪽의 `#자리` 는 그대로, 이 파일 자신을 가리키는 `page.html#자리` 는 `#자리` 로, **나머지는 누를 수 있되 아무
 *   일도 하지 않는 주소**([HtmlPreview.LINK])로. 다른 쪽·파일을 따라가는 것은 아직 하지 않는다.
 * * 스크립트 — **여기서 통째로 버린다.** 위생기는 `<script>` 를 중첩으로 세어 끝을 찾는데, 스크립트 안의 문자열 `"<script>"` 하나가
 *   그 셈을 틀어 **뒤의 본문까지 지운다**(브라우저는 스크립트를 중첩하지 않는다). 끝은 명세의 스크립트 자료 상태로 찾는다([ScriptData]).
 *   짝 없는 `</script>` 도 버린다.
 *
 * 그 밖의 URL 속성(`action`·`cite`·`iframe src`…)은 `#자리` 가 아니면 지운다 — 위생기의 리졸버에 닿는 상대 주소가 **우리 번호뿐**
 * 이어야 문서가 번호를 흉내 내지 못한다.
 *
 * ## 크기는 쓰는 도중에 잰다
 *
 * 결과의 상한([maxChars])은 표시 하나를 **쓰기 전에** 잰다 — 속성 값의 따옴표 하나가 `&quot;` 여섯 자가 되므로, 다 만든 뒤에 재면
 * 상한의 몇 배를 이미 쥔 뒤다(`"` 8 Mi 자짜리 속성 하나가 192 MB 힙을 넘겼다). 그리고 **위생기가 키울 것을 여기서
 * 먼저 키운다** — 태그가 아닌 `<` 는 `&lt;` 로, 속성 값의 `"`·`<`·`>` 는 참조로, `<style>` 의 `<` 는 `\3c ` 로, 값 없는 속성은 `=""`
 * 로, 스스로 닫은 요소는 닫는 태그와 함께 적는다. 그러면 위생기의 결과가 우리 상한 안에서 거의 그대로다.
 *
 * ## 무엇을 그대로 두는가
 *
 * 그럴 일이 없는 태그는 **글자 그대로** 옮긴다. 주석·`<![CDATA[`(위생기도 버린다)만 여기서 버리고, DOCTYPE 은 그대로 두어
 * 위생기가 내부 부분집합까지 안다(앞에서 지우면 남은 `]>` 가 화면에 찍힌다). 태그를 읽는 규칙은 위생기의 것과 **글자까지 같다** —
 * 둘이 같은 태그를 보아야 한다.
 */
internal class HtmlRewriter(
    private val src: String,
    /** XHTML 인가. 스스로 닫은 `<script/>`·`<style/>` 의 뜻이 갈린다(XHTML 은 빈 요소, HTML 은 여는 태그). */
    private val xhtml: Boolean,
    /** 미리보는 파일의 이름. 이 이름을 가리키는 링크는 같은 쪽 안의 자리다. */
    private val selfName: String?,
    private val assets: AssetTable,
    private val withheld: Withheld,
    private val maxChars: Int,
    private val checkpoint: () -> Unit,
) {
    private val n = src.length
    private val out = StringBuilder(minOf(n, maxChars) + 256)
    private val css = CssRewriter(assets, withheld, checkpoint)

    /** 결과 상한([maxChars])에 닿아 뒤를 버렸다. */
    var truncated = false
        private set

    /** 내용보다 먼저 온 첫 DOCTYPE 의 속(`<!DOCTYPE` 뒤, `>` 앞). 없으면 null. */
    var doctype: String? = null
        private set

    /** 그 DOCTYPE 이 `>` 로 닫혔는가. */
    var doctypeClosed = false
        private set

    /** 내용(공백 아닌 글·태그)이 나왔는가. 그 뒤의 DOCTYPE 은 모드를 정하지 못한다. */
    private var sawContent = false

    /** 열린 `<picture>` 의 깊이와, 그 안의 `<source>` 가 준 그림(그 안의 `<img>` 에 실을 것이 없을 때 쓴다). */
    private var pictureDepth = 0
    private var pictureSource: String? = null

    private var marks = 0

    fun run(): String {
        var i = 0
        while (i < n && !truncated) {
            val lt = src.indexOf('<', i)
            val end = if (lt < 0) n else lt
            if (end > i) text(i, end)
            if (lt < 0 || truncated) break
            if (++marks % CHECK_EVERY == 0) checkpoint()
            i = markup(lt)
        }
        return out.toString()
    }

    private fun room(): Int = maxChars - out.length

    // ---- 글과 표시 -----------------------------------------------------------------

    /** 글. 상한에 닿으면 들어가는 데까지 쓴다 — 서로게이트 쌍과 문자 참조(`&amp;` 의 앞부분)는 가르지 않는다. */
    private fun text(from: Int, to: Int) {
        if (!sawContent) {
            for (k in from until to) {
                if (!isHtmlSpace(src[k])) {
                    sawContent = true
                    break
                }
            }
        }
        val room = room()
        if (to - from <= room) {
            out.append(src, from, to)
            return
        }
        truncated = true
        var cut = from + maxOf(room, 0)
        if (cut > from && Character.isHighSurrogate(src[cut - 1])) cut--
        val amp = src.lastIndexOf('&', cut - 1)
        if (amp >= from && cut - amp <= MAX_ENTITY && src.indexOf(';', amp) !in amp until cut) cut = amp
        out.append(src, from, cut)
    }

    /** 표시 하나를 통째로 쓴다. 상한을 넘으면 쓰지 않고 멈춘다 — 태그를 반쯤 쓰지 않는다. */
    private fun whole(piece: CharSequence) {
        if (piece.length > room()) {
            truncated = true
            return
        }
        out.append(piece)
    }

    private fun whole(from: Int, to: Int) {
        if (to - from > room()) {
            truncated = true
            return
        }
        out.append(src, from, to)
    }

    private fun markup(lt: Int): Int = when {
        src.startsWith("<!--", lt) -> commentEnd(lt + 4)
        // 위생기도 `]]>` 까지 버린다 — 같은 자리에서 버린다.
        src.startsWith("<![CDATA[", lt) -> src.indexOf("]]>", lt + 9).let { if (it < 0) n else it + 3 }
        src.startsWith("<!", lt) -> declaration(lt)
        src.startsWith("<?", lt) -> src.indexOf('>', lt + 2).let { gt ->
            val end = if (gt < 0) n else gt + 1
            whole(lt, end)
            end
        }
        else -> tag(lt)
    }

    /**
     * 주석의 끝. 명세대로 `<!-->`·`<!--->` 는 그 자리에서 끝난 빈 주석이고, 끝은 `-->` 또는 `--!>` 다. `--` 를 찾아 가며 보므로 선형이다
     * (주석마다 `--!>` 를 파일 끝까지 찾으면 제곱이 된다 — 함정 표의 '되풀이될 수 있는 것의 탐색 범위').
     */
    private fun commentEnd(from: Int): Int {
        if (src.startsWith(">", from)) return from + 1
        if (src.startsWith("->", from)) return from + 2
        var k = from
        while (true) {
            val d = src.indexOf("--", k)
            if (d < 0) return n
            if (src.startsWith(">", d + 2)) return d + 3
            if (src.startsWith("!>", d + 2)) return d + 4
            k = d + 1
        }
    }

    /** `<!…>`. DOCTYPE 이면 모드를 정할 글을 적어 두고, 어느 것이든 그대로 옮긴다(위생기가 지운다). */
    private fun declaration(lt: Int): Int {
        val gt = src.indexOf('>', lt + 2)
        val end = if (gt < 0) n else gt + 1
        if (src.regionMatches(lt, "<!DOCTYPE", 0, 9, ignoreCase = true)) {
            if (!sawContent && doctype == null) {
                doctype = src.substring(lt + 9, minOf(if (gt < 0) n else gt, lt + 9 + MAX_DOCTYPE))
                doctypeClosed = gt >= 0 && gt - (lt + 9) <= MAX_DOCTYPE
            }
            sawContent = true
        }
        whole(lt, end)
        return end
    }

    private fun tag(lt: Int): Int {
        val t = HtmlTags.read(src, lt)
        sawContent = true
        if (t == null) {
            // 태그가 아니다. `&lt;` 로 적는다 — 위생기도 그렇게 바꾸지만, 그쪽에서 넷이 되면 우리 상한 밖에서 커진다.
            whole("&lt;")
            return lt + 1
        }
        if (t.closing) {
            when (t.lower) {
                // **짝 없는 `</script>` 는 버린다.** 스크립트는 여는 태그부터 닫는 태그까지 통째로 버렸으니 여기 온 것은 짝이 없는
                // 것이다. 브라우저도 버린다(위생기도 이제 그 태그만 버리지만 — 예전에는 뒤의 본문을 통째로 지웠다 — 겹쳐 둔다).
                "script" -> return t.end
                "picture" -> if (pictureDepth > 0 && --pictureDepth == 0) pictureSource = null
            }
            whole(lt, t.end)
            return t.end
        }
        return when (t.lower) {
            "script" -> script(t)
            "source" -> {
                // 위생기가 어차피 버린다. `<picture>` 안이면 그 그림만 기억해 둔다.
                source(t)
                t.end
            }
            "style" -> style(lt, t)
            else -> {
                element(lt, t)
                if (t.lower == "picture" && !t.selfClosing) pictureDepth++
                t.end
            }
        }
    }

    // ---- 스크립트·스타일 -------------------------------------------------------------

    /** 스크립트를 통째로 버린다. 돌았을 스크립트면 센다(`type="application/ld+json"` 같은 자료 덩이는 돌지 않는다). */
    private fun script(t: HtmlTags.Tag): Int {
        if (isExecutable(t)) withheld.scripts++
        // XHTML 의 `<script/>` 는 빈 요소다(위생기와 같다). **HTML 에서는 여는 태그다** — 브라우저는 그 뒤를 `</script>` 까지
        // 스크립트로 읽는다. 빈 요소로 보면 그 사이의 글이 우리 화면에만 보인다.
        if (t.selfClosing && xhtml) return t.end
        val close = ScriptData.end(src, t.end)
        if (close >= n) return n
        val gt = src.indexOf('>', close)
        return if (gt < 0) n else gt + 1
    }

    private fun isExecutable(t: HtmlTags.Tag): Boolean {
        val type = t.attr("type")?.let { PreviewUrls.decodeEntities(it) }?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
        if (type.isNullOrEmpty() || type == "module") return true
        return "javascript" in type || "ecmascript" in type || type == "text/jscript" || type == "text/livescript"
    }

    /**
     * `<style>` 의 CSS 를 고친다. 끝은 위생기와 **같은 규칙**(다음 `</style`)으로 찾는다. 닫는 태그는 다음 회전이 옮긴다.
     * XHTML 의 스스로 닫힌 `<style/>` 에는 내용이 없다(위생기와 같다 — 그 뒤를 CSS 로 읽으면 본문이 통째로 사라진다). HTML 에서는
     * 여는 태그라 그 뒤가 CSS 다(브라우저와 같다) — 위생기가 빈 요소로 읽지 않게 `/` 를 뗀 태그를 쓴다.
     */
    private fun style(lt: Int, t: HtmlTags.Tag): Int {
        val open = !t.selfClosing || !xhtml
        element(lt, t, forceOpen = open)
        if (!open || truncated) return t.end
        val close = src.indexOf("</style", t.end, ignoreCase = true).let { if (it < 0) n else it }
        val raw = scoped(t, CssUrls.withoutCdata(src.substring(t.end, close)))
        // **문자 참조가 있으면 푼 글을 고치고 `&` 를 `\26 ` 으로 적는다** — 위생기와 같은 규칙이다(`HtmlSanitizer` 의 `styleBody`).
        // `<svg>`·`<math>` 안의 `<style>` 은 브라우저가 참조를 풀어 `&#117;rl(…)` 이 주소가 된다. 우리 표에 없는 이름이 있으면 그 블록을
        // 비운다(위생기는 블록째 버린다).
        val decoded = if ('&' in raw) CharRefs.decodeText(raw) else null
        if (decoded != null && decoded.unknown) return close
        val refs = decoded != null && decoded.text != raw
        val body = if (refs) decoded!!.text else raw
        val rewritten = css.rewrite(body, "", room())
        if (rewritten == null) {
            truncated = true
            return close
        }
        // `<` 를 `\3c ` 로 — 위생기도 그렇게 바꾸는데(`<style>` 안의 `</head>` 로 껍데기를 속이는 길을 막으려고), 그쪽에서 넷이
        // 되면 우리 상한 밖에서 커진다. CSS 로서는 같은 글자다. 참조를 푼 블록의 `&` 도 같다.
        val grows = rewritten.count { it == '<' || (refs && it == '&') }
        if (rewritten.length + 3L * grows > room()) {
            truncated = true
            return close
        }
        var out = if ('<' in rewritten) rewritten.replace("<", "\\3c ") else rewritten
        if (refs) out = out.replace("&", "\\26 ")
        whole(out)
        return close
    }

    /**
     * `<style>` 의 조건을 CSS 안으로 옮긴다. **위생기는 `<style>` 의 속성을 버린다** — 그대로 두면 `media="print"` 인 인쇄용 규칙
     * (메뉴·광고를 숨기는 규칙이 흔하다)이 화면에 걸린다. 그래서 `@media print{…}` 로 싼다 — 싸기 전에 중괄호의 짝을 맞춘다
     * ([CssUrls.balanced]: 짝 없는 `}` 하나가 우리 `@media` 를 먼저 닫으면 그 뒤의 인쇄용 규칙이 화면에 걸린다). 조건에 우리가 아는
     * 글자 밖의 것이 있으면 브라우저가 틀린 조건을 다루듯 아무 데에도 걸지 않는다. CSS 가 아닌 `type` 도 브라우저는 걸지 않는다.
     */
    private fun scoped(t: HtmlTags.Tag, body: String): String {
        if (!isCssType(t.attr("type"))) return ""
        val media = t.attr("media")?.let { PreviewUrls.decodeEntities(it).trim() } ?: return body
        if (media.isEmpty() || media.equals("all", ignoreCase = true)) return body
        if (!media.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it in MEDIA_PUNCTUATION }) return ""
        return "@media $media{${CssUrls.balanced(body)}}"
    }

    private fun isCssType(type: String?): Boolean {
        val v = type?.let { PreviewUrls.decodeEntities(it) }?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
        return v.isNullOrEmpty() || v == "text/css"
    }

    // ---- 요소 ------------------------------------------------------------------------

    /** 고친 속성. 바꾸지 않은 것은 원문을 옮긴다. */
    private sealed interface Edit {
        data object Keep : Edit
        data object Drop : Edit

        /** 고친 값을 쓰면 상한을 넘는다 — 그 자리에서 멈춘다. */
        data object TooBig : Edit

        /** 우리가 쓴 값. 이미 속성 안에 둘 수 있는 글이다(번호·`#자리`·[escapeAttr] 를 지난 것). */
        class Set(val value: String) : Edit
    }

    /** 쓸 속성 하나. [raw] 면 [value] 는 원문의 값이라 따옴표·꺾쇠만 참조로 바꿔 적는다(`&` 는 원문의 참조라 그대로). */
    private class Piece(val name: String, val value: String, val raw: Boolean)

    /**
     * 시작 태그 하나. 바꿀 것도 위생기가 키울 것(값 없는 속성, 값의 `"`·`<`·`>`, 스스로 닫은 요소)도 없으면 원문 그대로 옮긴다.
     * @param forceOpen 스스로 닫혔어도 여는 태그로 쓴다(HTML 의 `<style/>`).
     */
    private fun element(lt: Int, t: HtmlTags.Tag, forceOpen: Boolean = false) {
        val name = t.lower
        val img = name == "img" || name == "image"
        val newSrc = if (img) imageSource(t) else null
        val sheetLink = name == "link" && relHas(t, "stylesheet")
        val pieces = ArrayList<Piece>(t.attrs.size + 1)
        var changed = forceOpen && t.selfClosing
        var srcSeen = false
        val seen = HashSet<String>()
        for (a in t.attrs) {
            val edit = when {
                // **같은 이름의 속성은 첫째만 산다**(HTML 토큰화 규칙). 위생기는 둘째도 옮겨 적지만 브라우저가 버린다 — 여기서 버리면
                // 속성마다 하는 일(스타일시트 판정 등)이 태그 하나에 한 번으로 묶인다.
                !seen.add(a.lower) -> Edit.Drop
                img && a.lower == "src" -> {
                    srcSeen = true
                    if (newSrc != null) Edit.Set(newSrc) else Edit.Drop
                }
                sheetLink && a.lower == "href" -> stylesheet(t, a.value.orEmpty())
                else -> attribute(name, a, img)
            }
            when (edit) {
                Edit.Keep -> {
                    val v = a.value
                    if (v == null || needsEscape(v)) changed = true
                    pieces.add(Piece(a.name, v.orEmpty(), raw = true))
                }
                Edit.Drop -> changed = true
                Edit.TooBig -> {
                    truncated = true
                    return
                }
                is Edit.Set -> {
                    changed = true
                    pieces.add(Piece(a.name, edit.value, raw = false))
                }
            }
        }
        if (newSrc != null && !srcSeen) {
            pieces.add(Piece("src", newSrc, raw = false))
            changed = true
        }
        val selfClose = t.selfClosing && !forceOpen
        val void = name in VOID
        if (selfClose && !void) changed = true
        if (!changed) {
            whole(lt, t.end)
            return
        }
        // **쓰기 전에 잰다.** 원문의 값은 참조로 바꾸면 길어진다(따옴표 하나가 여섯 자).
        var length = 2L + t.name.length
        for (p in pieces) length += 4L + p.name.length + if (p.raw) escapedRawLength(p.value) else p.value.length.toLong()
        if (selfClose) length += if (void) 1 else 3L + t.name.length
        if (length > room()) {
            truncated = true
            return
        }
        out.append('<').append(t.name)
        for (p in pieces) {
            out.append(' ').append(p.name).append("=\"")
            if (p.raw) appendRawEscaped(p.value) else out.append(p.value)
            out.append('"')
        }
        when {
            selfClose && void -> out.append("/>")
            // 위생기가 쓸 닫는 태그를 우리가 쓴다(XHTML 의 뜻 — 위생기의 판단과 같다). 그래야 위생기의 결과가 우리 상한 안에 든다.
            selfClose -> out.append("></").append(t.name).append('>')
            else -> out.append('>')
        }
    }

    private fun attribute(element: String, a: HtmlTags.Attr, img: Boolean): Edit {
        val v = a.value.orEmpty()
        return when (a.lower) {
            "style" -> styleAttribute(v)
            "background" -> resource(v)
            // 위생기도 지운다(그때는 '바깥 참조' 로 센다 — 우리가 고른 뒤라 셀 것이 없다).
            "srcset", "imagesrcset" -> Edit.Drop
            "sizes" -> if (img) Edit.Drop else Edit.Keep
            "href", "xlink:href" -> when (element) {
                "a", "area" -> navigation(v)
                "image", "feimage" -> resource(v)
                else -> fragmentOnly(v)
            }
            in OTHER_URL_ATTRS -> fragmentOnly(v)
            // SVG 의 표현 속성(`filter`·`mask`·`fill` …)의 값은 CSS 다 — `style=""` 처럼 문자 참조를 푼 글의 주소를 번호로 바꾼다. 위생기도
            // 같은 속성을 CSS 로 거른다(`HtmlSanitizer.CSS_URL_ATTRS`) — 두 겹이 같은 것을 본다.
            in CSS_URL_ATTRS -> styleAttribute(v)
            else -> Edit.Keep
        }
    }

    /**
     * `style=""` 의 CSS. **문자 참조를 먼저 푼다** — 브라우저는 푼 값을 CSS 로 읽는다(`url&lpar;…&rpar;` 도 주소다). 우리 표에 없는
     * 이름 참조가 남으면 그 글을 믿지 않고 속성째 버린다(위생기도 같은 판단을 한다 — 같은 `CharRefs` 다).
     */
    private fun styleAttribute(raw: String): Edit {
        val decoded = CharRefs.decode(raw)
        if (decoded.unknown) return Edit.Drop
        val v = decoded.text
        // 함수(`(`)도 규칙(`@`)도 없으면 주소가 있을 자리가 없다.
        if ('(' !in v && '@' !in v) return Edit.Keep
        val room = room()
        val rewritten = css.rewrite(v, "", room) ?: return Edit.TooBig
        if (escapedAttrLength(rewritten) > room) return Edit.TooBig
        return Edit.Set(escapeAttr(rewritten))
    }

    private fun resource(raw: String): Edit = when (val r = image(raw)) {
        is Res.Token -> Edit.Set(r.url)
        Res.Fragment -> Edit.Keep
        Res.External -> {
            withheld.remote++
            Edit.Drop
        }
        Res.Script -> {
            withheld.scripts++
            Edit.Drop
        }
        Res.Nothing -> Edit.Drop
    }

    /** 이 쪽 안의 자리(`#…`)면 남기고, 아니면 지운다. */
    private fun fragmentOnly(raw: String): Edit =
        when (val t = PreviewUrls.resolve(PreviewUrls.decodeEntities(raw), "")) {
            is PreviewUrls.Target.Fragment -> fragment(raw, t)
            else -> Edit.Drop
        }

    /**
     * 자리 주소를 남긴다. 문자 참조로 적은 것(`&#35;sec`)은 **푼 모양으로** 적는다 — 위생기는 주소 속성의 참조를 풀지 않아 `#` 로
     * 시작하지 않는 그 값을 리졸버에 묻고, 리졸버는 우리 번호가 아니라며 지운다.
     */
    private fun fragment(raw: String, t: PreviewUrls.Target.Fragment): Edit =
        if (raw.trimStart().startsWith("#")) Edit.Keep else Edit.Set(escapeAttr(t.raw))

    /**
     * 링크. 같은 쪽의 자리는 그대로, 이 파일 자신의 자리는 `#자리` 로, 나머지는 [HtmlPreview.LINK] 로 — 링크의 모양(`a:link`)은 남고
     * 누르면 화면이 아무것도 하지 않는다. 스크립트 주소(`javascript:`)는 스크립트로 센다.
     */
    private fun navigation(raw: String): Edit = when (val t = PreviewUrls.resolve(PreviewUrls.decodeEntities(raw), "")) {
        is PreviewUrls.Target.Fragment -> fragment(raw, t)
        is PreviewUrls.Target.Self -> t.fragment?.let { Edit.Set("#" + escapeAttr(it)) } ?: Edit.Set(HtmlPreview.LINK)
        is PreviewUrls.Target.Local -> {
            val self = selfName != null && t.path.equals(selfName, ignoreCase = true)
            if (self && t.fragment != null) Edit.Set("#" + escapeAttr(t.fragment)) else Edit.Set(HtmlPreview.LINK)
        }
        is PreviewUrls.Target.Outside -> {
            if (t.script) withheld.scripts++
            Edit.Set(HtmlPreview.LINK)
        }
        else -> Edit.Set(HtmlPreview.LINK)
    }

    /**
     * `<link rel="stylesheet">` 의 주소. 폴더 안의 `.css` 만 싣는다. **번갈이 스타일시트**(`alternate stylesheet`)는 브라우저가 처음에
     * 적용하지 않으므로 싣지 않는다. 실을 수 없으면 주소를 지운다 — 위생기는 주소 없는 스타일시트를 버린다.
     */
    private fun stylesheet(t: HtmlTags.Tag, raw: String): Edit {
        if (relHas(t, "alternate") || !isCssType(t.attr("type"))) return Edit.Drop
        return when (val r = PreviewUrls.resolve(PreviewUrls.decodeEntities(raw), "")) {
            is PreviewUrls.Target.Local ->
                if (PreviewTypes.isStylesheetName(r.path)) assets.sheet(r.path)?.let { Edit.Set(it) } ?: Edit.Drop else Edit.Drop
            is PreviewUrls.Target.Outside -> {
                if (r.script) withheld.scripts++ else withheld.remote++
                Edit.Drop
            }
            else -> Edit.Drop
        }
    }

    private fun relHas(t: HtmlTags.Tag, word: String): Boolean {
        val rel = t.attr("rel") ?: return false
        return PreviewUrls.decodeEntities(rel).lowercase(Locale.ROOT).split(' ', '\t', '\n', '\r', '\u000C').contains(word)
    }

    // ---- 그림 ------------------------------------------------------------------------

    private sealed interface Res {
        class Token(val url: String) : Res
        data object Fragment : Res
        data object External : Res
        data object Script : Res
        data object Nothing : Res
    }

    private fun image(raw: String, decoded: Boolean = false): Res {
        val v = if (decoded) raw else PreviewUrls.decodeEntities(raw)
        return when (val t = PreviewUrls.resolve(v, "")) {
            is PreviewUrls.Target.Local -> assets.disk(t.path, PreviewAsset.Use.IMAGE)?.let { Res.Token(it) } ?: Res.Nothing
            is PreviewUrls.Target.Data -> css.data(t.url, PreviewAsset.Use.IMAGE)?.let { Res.Token(it) } ?: Res.Nothing
            is PreviewUrls.Target.Fragment -> Res.Fragment
            is PreviewUrls.Target.Outside -> if (t.script) Res.Script else Res.External
            else -> Res.Nothing
        }
    }

    /**
     * `<img>` 의 그림. 브라우저처럼 **`src` 는 1x 후보**다 — `srcset` 에 1x 도 폭 서술자도 없을 때만 후보에 든다(명세). 그 가운데
     * 실을 수 있는 것을 고른다([PreviewUrls.preferred] — 2x 이하에서 큰 것부터). 예전에는 `src` 가 언제나 이겨서, 늦게 싣는 쪽의 1×1
     * `data:` 자리표시 그림이 `srcset` 의 진짜 그림을 가렸다.
     *
     * 고른 것이 하나도 실리지 않으면 `src` 를 본다(저장한 쪽은 `srcset` 이 바깥 주소로 남고 `src` 만 폴더에 들어 있는 일이 흔하다).
     * 그래도 없으면 감싼 `<picture>` 의 `<source>` 가 준 것.
     */
    private fun imageSource(t: HtmlTags.Tag): String? {
        val src = t.attr("src")?.let { PreviewUrls.decodeEntities(it) }
        val set = t.attr("srcset")?.let { PreviewUrls.parseSrcset(PreviewUrls.decodeEntities(it)) }.orEmpty()
        val candidates = ArrayList(set)
        val srcIsCandidate = src != null && src.isNotBlank() && set.none { it.width != null || it.density == 1.0 }
        if (srcIsCandidate) candidates.add(PreviewUrls.Candidate(src!!, 1.0, null))
        var external = false
        for (c in PreviewUrls.preferred(candidates)) {
            when (val r = image(c.url, decoded = true)) {
                is Res.Token -> return r.url
                Res.External -> external = true
                else -> Unit
            }
        }
        if (src != null && !srcIsCandidate) {
            when (val r = image(src, decoded = true)) {
                is Res.Token -> return r.url
                Res.External -> external = true
                else -> Unit
            }
        }
        if (pictureDepth > 0) pictureSource?.let { return it }
        if (external) withheld.remote++
        return null
    }

    /** `<picture>` 안의 `<source>` 에서 처음으로 실을 수 있는 그림을 기억한다. `media` 는 보지 않는다(첫 것을 쓴다). */
    private fun source(t: HtmlTags.Tag) {
        if (pictureDepth == 0 || pictureSource != null) return
        val set = t.attr("srcset") ?: return
        for (c in PreviewUrls.preferred(PreviewUrls.parseSrcset(PreviewUrls.decodeEntities(set)))) {
            val r = image(c.url, decoded = true)
            if (r is Res.Token) {
                pictureSource = r.url
                return
            }
        }
    }

    // ---- 도우미 -----------------------------------------------------------------------

    /** 원문의 값을 그대로 옮기면 위생기가 키울 글자(`"`·`<`·`>`)가 있는가. */
    private fun needsEscape(v: String): Boolean = v.any { it == '"' || it == '<' || it == '>' }

    private fun escapedRawLength(v: String): Long {
        var length = v.length.toLong()
        for (c in v) {
            when (c) {
                '"' -> length += 5
                '<', '>' -> length += 3
            }
        }
        return length
    }

    /** 원문의 값을 큰따옴표 안에 둔다. `&` 는 건드리지 않는다 — 원문의 참조가 그대로 뜻을 갖는다. */
    private fun appendRawEscaped(v: String) {
        if (!needsEscape(v)) {
            out.append(v)
            return
        }
        for (c in v) {
            when (c) {
                '"' -> out.append("&quot;")
                '<' -> out.append("&lt;")
                '>' -> out.append("&gt;")
                else -> out.append(c)
            }
        }
    }

    companion object {
        /** 이만큼의 표시마다 [checkpoint] 를 부른다. */
        private const val CHECK_EVERY = 512

        /** 잘린 자리에서 되돌아볼 문자 참조의 길이(`&#x10FFFF;`). */
        private const val MAX_ENTITY = 12

        /** DOCTYPE 의 속은 이만큼만 본다. 실물은 200자를 넘지 않는다. */
        private const val MAX_DOCTYPE = 1024

        /** `media` 조건에 받는 글자(영문·숫자 밖). `<` 는 받지 않는다 — 위생기가 `<style>` 안의 `<` 를 이스케이프해 조건이 깨진다. */
        private const val MEDIA_PUNCTUATION = " \t\n\r,:()._-/>="

        /** 위생기의 URL 속성 가운데 우리가 그림·링크로 다루지 않는 것. `#자리` 가 아니면 지운다. */
        private val OTHER_URL_ATTRS = setOf("src", "poster", "action", "formaction", "cite", "longdesc", "data", "usemap")

        /** SVG 의 표현 속성 가운데 값에 `<url>` 을 받는 것 — 위생기의 표(`HtmlSanitizer.CSS_URL_ATTRS`)와 같아야 한다. */
        private val CSS_URL_ATTRS = setOf(
            "fill", "stroke", "filter", "mask", "clip-path", "marker", "marker-start", "marker-mid", "marker-end", "cursor",
        )

        /**
         * 짝 없는 요소 — 위생기의 표(`HtmlSanitizer.VOID`)와 같아야 한다. 스스로 닫은 것을 위생기처럼 적을 때(짝 없는 것은 `/>`,
         * 나머지는 닫는 태그와 함께) 쓴다. 어긋나도 위생은 그대로다 — 위생기가 결과를 한 번 더 고친다(크기 셈만 어긋난다).
         */
        private val VOID = setOf(
            "area", "base", "br", "col", "embed", "frame", "hr", "img", "input", "link",
            "meta", "param", "source", "track", "wbr",
        )

        private fun isHtmlSpace(c: Char): Boolean = c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\u000C'

        /** 우리가 쓰는 값(푼 글)을 큰따옴표 안에 둔다. 이번에는 `&` 도 바꾼다 — 푼 글이라 `&` 가 참조로 다시 읽히면 안 된다. */
        fun escapeAttr(v: String): String {
            if (v.none { it == '&' || it == '"' || it == '<' || it == '>' }) return v
            val sb = StringBuilder(v.length + 16)
            for (c in v) {
                when (c) {
                    '&' -> sb.append("&amp;")
                    '"' -> sb.append("&quot;")
                    '<' -> sb.append("&lt;")
                    '>' -> sb.append("&gt;")
                    else -> sb.append(c)
                }
            }
            return sb.toString()
        }

        /** [escapeAttr] 가 쓸 글자 수. 쓰기 전에 상한을 재려고. */
        fun escapedAttrLength(v: String): Long {
            var length = v.length.toLong()
            for (c in v) {
                when (c) {
                    '&', '<', '>' -> length += if (c == '&') 4 else 3
                    '"' -> length += 5
                }
            }
            return length
        }
    }
}

/** 미리보기가 버린 것 가운데 화면이 말할 것 — 돌지 않은 스크립트, 싣지 않은 바깥 자원. */
internal class Withheld {
    var scripts = 0
    var remote = 0
}

/**
 * CSS 의 주소를 번호로 바꾼다. 문서의 `<style>`·`style=""` 과 스타일시트 파일이 함께 쓴다 — 파일 안의 주소는 **그 파일의 폴더**
 * 기준이다([rewrite] 의 `baseDir`). 주소를 찾는 일은 위생기와 한 벌인 `CssRefs` 가 한다.
 */
internal class CssRewriter(
    private val assets: AssetTable,
    private val withheld: Withheld,
    private val checkpoint: () -> Unit,
) {
    /** @return 결과가 [limit] 를 넘으면 null — 부르는 쪽이 거기서 멈춘다. */
    fun rewrite(css: String, baseDir: String, limit: Int = Int.MAX_VALUE): String? =
        CssRefs.rewrite(css, limit, checkpoint, onImport = { url -> countImport(url) }) { value -> map(value, baseDir) }

    /**
     * `@import` 는 위생기처럼 지운다. **바깥을 가리킬 때만** '바깥 자원' 으로 센다 — 위생기는 모든 `@import` 를 바깥 참조로 세서,
     * 폴더 안의 시트를 끌어오는 멀쩡한 쪽에도 '바깥 자원은 싣지 않습니다' 가 떴다.
     */
    private fun countImport(url: String?) {
        val t = url?.let { PreviewUrls.resolve(it, "") } as? PreviewUrls.Target.Outside ?: return
        if (t.script) withheld.scripts++ else withheld.remote++
    }

    private fun map(value: String, baseDir: String): CssRefs.Out = when (val t = PreviewUrls.resolve(value, baseDir)) {
        is PreviewUrls.Target.Fragment -> CssRefs.Out.Keep
        is PreviewUrls.Target.Local ->
            // **다른 SVG 파일의 조각은 가리키지 않는다**(위생기와 같은 판단) — `filter`·`mask` 는 그 문서를 가져와 요소를 쓰는데,
            // 번호로 바꾸면 위생기가 `.svg#` 를 알아보지 못해 그 판단을 비켜 간다.
            if (t.fragment != null && isSvg(t.path)) {
                withheld.remote++
                CssRefs.Out.None
            } else {
                assets.disk(t.path, PreviewAsset.Use.STYLE)?.let { CssRefs.Out.Url(it) } ?: CssRefs.Out.None
            }
        is PreviewUrls.Target.Data -> data(t.url, PreviewAsset.Use.STYLE)?.let { CssRefs.Out.Url(it) } ?: CssRefs.Out.None
        is PreviewUrls.Target.Outside -> {
            if (t.script) withheld.scripts++ else withheld.remote++
            CssRefs.Out.None
        }
        else -> CssRefs.Out.None
    }

    /** `data:` 를 풀어 표에 담는다. 형식이 우리 표에 없거나([PreviewTypes.ofData]) 상한을 넘으면 null. */
    fun data(url: String, use: PreviewAsset.Use): String? {
        val decoded = PreviewUrls.decodeData(url, HtmlPreview.MAX_DATA_ITEM_BYTES) ?: return null
        val type = PreviewTypes.ofData(decoded.mediaType, use) ?: return null
        return assets.data(type, decoded.bytes, url.takeIf { it.length <= MAX_KEY_CHARS })
    }

    private fun isSvg(path: String): Boolean =
        path.endsWith(".svg", ignoreCase = true) || path.endsWith(".svgz", ignoreCase = true)

    private companion object {
        /** 같은 `data:` 를 한 번만 담으려고 주소를 열쇠로 드는 길이의 상한. */
        const val MAX_KEY_CHARS = 4096
    }
}

/** 위생기의 `readTag` 와 **글자까지 같은** 태그 읽기. 둘이 다르게 읽으면 우리가 고친 속성이 위생기에게는 다른 속성이 된다. */
internal object HtmlTags {

    class Attr(val name: String, val value: String?) {
        val lower: String = name.lowercase(Locale.ROOT)
    }

    class Tag(val name: String, val closing: Boolean, val selfClosing: Boolean, val attrs: List<Attr>, val end: Int) {
        val lower: String = name.lowercase(Locale.ROOT)

        /** 그 이름의 첫 속성 값(원문). 값 없이 적힌 속성은 빈 글이다. */
        fun attr(lowerName: String): String? = attrs.firstOrNull { it.lower == lowerName }?.let { it.value.orEmpty() }
    }

    fun read(s: String, start: Int): Tag? {
        var i = start + 1
        if (i >= s.length) return null
        val closing = s[i] == '/'
        if (closing) i++
        val nameStart = i
        while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '-' || s[i] == ':')) i++
        if (i == nameStart) return null
        val name = s.substring(nameStart, i)
        val attrs = ArrayList<Attr>()
        var selfClosing = false
        while (i < s.length) {
            while (i < s.length && s[i].isWhitespace()) i++
            if (i >= s.length) break
            if (s[i] == '>') {
                i++
                break
            }
            if (s[i] == '/') {
                selfClosing = true
                i++
                continue
            }
            val attrStart = i
            while (i < s.length && !s[i].isWhitespace() && s[i] != '=' && s[i] != '>' && s[i] != '/') i++
            if (i == attrStart) {
                i++
                continue
            }
            val attr = s.substring(attrStart, i)
            while (i < s.length && s[i].isWhitespace()) i++
            var value: String? = null
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
            attrs.add(Attr(attr, value))
        }
        return Tag(name, closing, selfClosing, attrs, i)
    }
}

/**
 * 스크립트의 끝 — HTML 명세의 '스크립트 자료' 상태들(13.2.5.4~)을 줄인 것. 보통은 `</script` 뒤에 공백·`/`·`>` 가 오는 첫 자리지만,
 * `<!--` 안에서 `<script` 를 만나면(옛 쪽의 `document.write("<script…></script>")`) 그다음 `</script>` 는 스크립트의 글이다 —
 * `-->` 가 그 상태를 푼다. 한 글자씩 한 번만 보므로 선형이다.
 */
internal object ScriptData {

    /** [from] 부터 스크립트 본문을 읽어 닫는 `</script` 의 `<` 자리를 준다. 없으면 문자열 길이. */
    fun end(s: String, from: Int): Int {
        val n = s.length
        var i = from
        var state = DATA
        var dashes = 0
        while (i < n) {
            val c = s[i]
            if (state == DATA) {
                if (c == '<') {
                    if (isEndTag(s, i)) return i
                    if (s.startsWith("<!--", i)) {
                        state = ESCAPED
                        dashes = 2
                        i += 4
                        continue
                    }
                }
                i++
                continue
            }
            when (c) {
                '-' -> dashes++
                '>' -> {
                    if (dashes >= 2) state = DATA
                    dashes = 0
                }
                '<' -> {
                    dashes = 0
                    if (state == ESCAPED) {
                        if (isEndTag(s, i)) return i
                        if (nameAt(s, i + 1)) {
                            state = DOUBLE_ESCAPED
                            i += 7
                            continue
                        }
                    } else if (s.startsWith("/", i + 1) && nameAt(s, i + 2)) {
                        state = ESCAPED
                        i += 8
                        continue
                    }
                }
                else -> dashes = 0
            }
            i++
        }
        return n
    }

    private const val DATA = 0
    private const val ESCAPED = 1
    private const val DOUBLE_ESCAPED = 2

    private fun isEndTag(s: String, i: Int): Boolean = s.startsWith("</", i) && nameAt(s, i + 2)

    /** [at] 에 `script` 가 있고 그 뒤가 공백·`/`·`>` 인가. */
    private fun nameAt(s: String, at: Int): Boolean {
        if (!s.regionMatches(at, "script", 0, 6, ignoreCase = true)) return false
        val after = at + 6
        if (after >= s.length) return false
        val c = s[after]
        return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\u000C' || c == '/' || c == '>'
    }
}
