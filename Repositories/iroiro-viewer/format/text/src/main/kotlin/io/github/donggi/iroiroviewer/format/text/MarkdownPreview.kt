package io.github.donggi.iroiroviewer.format.text

import io.github.donggi.iroiroviewer.charset.TextEncoding
import io.github.donggi.iroiroviewer.charset.WindowedDecoder
import io.github.donggi.iroiroviewer.format.UnsupportedFeatures
import io.github.donggi.iroiroviewer.format.html.CssValues
import io.github.donggi.iroiroviewer.format.html.FlowHtml
import io.github.donggi.iroiroviewer.format.html.HtmlSanitizer
import io.github.donggi.iroiroviewer.format.html.HtmlWriter
import io.github.donggi.iroiroviewer.format.html.StyleBuilder
import io.github.donggi.iroiroviewer.safety.ParseLimits
import io.github.donggi.iroiroviewer.safety.TextLimits
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.Locale

/**
 * 마크다운 미리보기(14단계). `.md` 를 **읽기 좋은 HTML 한 벌**로 바꾼다.
 *
 * ## 무엇을 하는가
 *
 * CommonMark 의 부분집합과 GFM 의 표·취소선·할 일 목록을 푼다([MdBlockParser]·[MdInlineParser]). 결과는
 * `format:html` 의 [HtmlWriter] 로만 쓰고, 흐름 문서와 같은 끝([FlowHtml.finish] — 위생 → CSS 거르기 → 껍데기)을
 * 지난다. 화면은 그것을 잠긴 WebView(자바스크립트 꺼짐)에 띄운다 — 문서와 같은 길이다.
 *
 * ## 무엇을 하지 않는가
 *
 * - **날것의 HTML 을 살리지 않는다.** 명세는 `<div>` 를 그대로 내보내지만 여기서는 글자로 보인다.
 * - **바깥 링크를 따라가지 않는다.** 글자만 링크처럼 칠한다. 누를 수 있는 것은 `#제목` 뿐이다.
 * - **바깥 그림을 싣지 않는다.** 이 파일과 같은 폴더(그 아래 포함)의 그림만, 그것도 이름이 아니라
 *   번호(`img/3`)로 가리킨다([images]). 파일을 실제로 여는 것은 화면 쪽이고, 거기서 폴더를 벗어나는지
 *   다시 본다.
 *
 * ## 상한
 *
 * 원문 [MAX_SOURCE_BYTES], 결과 [MAX_HTML_CHARS], 블록 수·중첩 깊이·줄 안 노드 수(각 파서의 동반 객체).
 * 상한에 닿으면 던지지 않고 앞부분을 보이며 [Result.truncated] 로 알린다.
 */
object MarkdownPreview {

    /** 미리보기를 만드는 원문의 상한. README 는 크게 잡아야 수백 KB 다. 넘으면 화면이 그렇게 말한다. */
    const val MAX_SOURCE_BYTES: Long = 2L * 1024 * 1024

    /** 결과 HTML 의 상한(글자). WebView 가 한 번에 배치하는 양이라 흐름 문서의 부분 상한보다 작게 둔다. */
    const val MAX_HTML_CHARS = 4 * 1024 * 1024

    /** 한 문서가 가리킬 수 있는 그림 수. */
    const val MAX_IMAGES = 500

    private const val IMAGE_PREFIX = "img/"

    /** 마크다운 확장자인가. 미리보기 단추를 이 파일들에만 띄운다. */
    fun isMarkdownName(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase(Locale.ROOT) in EXTENSIONS

    private val EXTENSIONS = setOf("md", "markdown", "mdown", "mkd", "mkdn", "mdwn")

    /**
     * 미리보기 쪽의 경로. **번호가 붙는다** — 글자 크기를 바꿔 다시 만든 쪽을 WebView 가 새로 읽게 하려면
     * 주소가 달라야 한다(같은 주소면 다시 불러오지 않는다).
     */
    fun pagePath(generation: Int): String = "md-$generation.html"

    /** [index] 번째 그림의 경로. */
    fun imagePath(index: Int): String = IMAGE_PREFIX + index

    /** 그림 경로면 번호, 아니면 null. */
    fun imageIndexOf(path: String): Int? {
        if (!path.startsWith(IMAGE_PREFIX)) return null
        val rest = path.substring(IMAGE_PREFIX.length)
        if (rest.isEmpty() || rest.length > 6 || !rest.all { it in '0'..'9' }) return null
        return rest.toInt()
    }

    class Result(
        /** 껍데기까지 씌운 문서 하나. */
        val html: String,
        /** `img/N` 이 가리키는 **이 파일 기준의 상대 경로**(정규화한 것). 화면이 폴더 밖인지 다시 본다. */
        val images: List<String>,
        /** 상한에 닿아 뒤를 버렸다. */
        val truncated: Boolean,
    )

    /**
     * @param fontPercent 본문 글자 크기(기본 100). 텍스트 뷰어의 글자 크기 설정을 따른다.
     * @param checkpoint 줄 몇백 개마다 불린다 — 부르는 쪽이 취소를 여기서 던진다.
     */
    fun render(markdown: String, fontPercent: Int = 100, checkpoint: () -> Unit = {}): Result {
        val body = body(markdown, checkpoint)
        val css = BASE_CSS + fontRule(fontPercent)
        val html = FlowHtml.finish(body.html, css, RESOLVER, ParseLimits.DEFAULT, UnsupportedFeatures())
        return Result(html, body.images, body.truncated)
    }

    /** 위생·껍데기 전의 본문. 시험이 구조를 이것으로 본다. */
    internal fun body(markdown: String, checkpoint: () -> Unit = {}, maxChars: Int = MAX_HTML_CHARS): Result {
        val parser = MdBlockParser(checkpoint)
        val doc = parser.parse(markdown)
        val w = HtmlWriter(maxChars)
        val renderer = MdRenderer(parser.refs, w, checkpoint)
        renderer.document(doc)
        w.closeAll()
        return Result(w.toString(), renderer.images, parser.truncated || w.full)
    }

    /**
     * 파일의 글을 읽는다. BOM 은 바이트로 건너뛰고([bomLength]), [maxBytes] 에서 끊는다.
     *
     * 판정은 텍스트 뷰어가 이미 했다 — 여기서는 그 인코딩으로 디코드만 한다(판정은 `feature`, 디코딩은 `format`).
     */
    fun readSource(
        openAt: (Long) -> InputStream,
        encoding: TextEncoding,
        bomLength: Int,
        maxBytes: Long = MAX_SOURCE_BYTES,
    ): String {
        val cs = encoding.charset ?: return ""
        val sb = StringBuilder()
        openAt(bomLength.toLong()).use { raw ->
            WindowedDecoder(cs.newDecoder()).decodeAll(LimitedStream(raw, maxBytes)) { buf, off, len ->
                sb.append(buf, off, len)
            }
        }
        return sb.toString()
    }

    /**
     * 그림 목적지를 **이 파일 기준의 정규화한 상대 경로**로. 실을 수 없으면 null(그림 대신 대체 글이 보인다).
     *
     * 싣지 않는 것: 스킴이 붙은 것(`http:`·`data:`·`file:`·`content:`, 드라이브 문자 `C:` 까지), 절대 경로,
     * 조각뿐인 것, 파일 폴더 위로 올라가는 `..`. 조각(`#…`)과 질의(`?…`)는 뗀다 — GitHub 식 `a.png?raw=true`
     * 가 흔하다. 퍼센트 인코딩은 푼다(`my%20image.png`).
     */
    fun localImagePath(dest: String): String? {
        val d = dest.trim()
        if (d.isEmpty() || d.length > 2048) return null
        if (d[0] == '#' || d[0] == '/' || d[0] == '\\') return null
        if (hasScheme(d)) return null
        val path = d.substringBefore('#').substringBefore('?')
        val decoded = percentDecode(path)
        if (decoded.any { it == '\\' || it.code < 0x20 }) return null
        val parts = ArrayList<String>()
        for (seg in decoded.split('/')) {
            when (seg) {
                "", "." -> Unit
                ".." -> {
                    if (parts.isEmpty()) return null
                    parts.removeAt(parts.lastIndex)
                }
                else -> parts.add(seg)
            }
        }
        if (parts.isEmpty()) return null
        return parts.joinToString("/")
    }

    private fun hasScheme(url: String): Boolean {
        val colon = url.indexOf(':')
        if (colon <= 0) return false
        for (i in 0 until colon) {
            val c = url[i]
            val ok = if (i == 0) c.isLetter() else c.isLetterOrDigit() || c == '+' || c == '-' || c == '.'
            if (!ok) return false
        }
        return true
    }

    /** `%XX` 를 UTF-8 로 푼다. 잘못된 인코딩이면 원문 그대로(`100%.png` 는 그런 이름일 수 있다). */
    private fun percentDecode(s: String): String {
        if ('%' !in s) return s
        val bytes = ByteArrayOutputStream(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%') {
                if (i + 2 >= s.length) return s
                val hi = Character.digit(s[i + 1], 16)
                val lo = Character.digit(s[i + 2], 16)
                if (hi < 0 || lo < 0) return s
                bytes.write(hi * 16 + lo)
                i += 3
            } else {
                val enc = c.toString().toByteArray(Charsets.UTF_8)
                bytes.write(enc, 0, enc.size)
                i++
            }
        }
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes.toByteArray()))
                .toString()
        } catch (e: CharacterCodingException) {
            s
        }
    }

    /**
     * 위생기의 리졸버. **우리가 쓴 그림 번호만** 통과시킨다 — 문서가 스스로 적은 상대 주소는 여기까지 오지
     * 않는다(우리가 목적지를 번호로 바꿔 적었다). `#자리` 는 리졸버에 닿기 전에 위생기가 통과시킨다.
     */
    private val RESOLVER = HtmlSanitizer.Resolver { url -> url.takeIf { imageIndexOf(it) != null } }

    private fun fontRule(percent: Int): String {
        val v = CssValues.percent(percent.toDouble(), 50.0, 300.0) ?: return ""
        return "body{font-size:$v;}"
    }

    /**
     * 읽기용 스타일. **종이는 흰색**이다 — 잠긴 WebView 가 흰 바탕으로 뜨고(문서와 같다) 코드 블록의 색도 그
     * 바탕에 맞췄다. 색은 GitHub 의 밝은 테마 근처에서 골랐다(사람들이 README 를 보던 모양).
     * 할 일 상자는 글꼴의 기호(☐·☑)에 기대지 않고 테두리로 그린다 — 폰에 그 기호가 없을 수 있다.
     */
    private const val BASE_CSS = """
body{line-height:1.6;color:#1f2328;background:#ffffff;}
h1,h2,h3,h4,h5,h6{line-height:1.25;margin:1.2em 0 .6em;font-weight:600;}
h1{font-size:1.9em;padding-bottom:.3em;border-bottom:1px solid #d1d9e0;}
h2{font-size:1.45em;padding-bottom:.3em;border-bottom:1px solid #d1d9e0;}
h3{font-size:1.2em;}
h4{font-size:1em;}
h5{font-size:.9em;}
h6{font-size:.85em;color:#59636e;}
p,ul,ol,blockquote,pre,.tbl{margin:0 0 1em;}
ul,ol{padding-left:1.6em;}
li+li{margin-top:.25em;}
li>ul,li>ol{margin-bottom:0;}
code{font-family:monospace;font-size:.9em;background:#eff1f3;padding:.15em .35em;border-radius:4px;}
pre{background:#f6f8fa;padding:12px 14px;border-radius:6px;overflow-x:auto;white-space:pre;line-height:1.45;}
pre code{background:none;padding:0;border-radius:0;font-size:.85em;}
blockquote{margin-left:0;margin-right:0;padding:0 1em;color:#59636e;border-left:4px solid #d1d9e0;}
hr{border:0;border-top:2px solid #d1d9e0;margin:1.5em 0;}
.tbl{overflow-x:auto;}
.tbl table{max-width:none;border-collapse:collapse;}
th,td{border:1px solid #d1d9e0;padding:6px 12px;}
th{background:#f6f8fa;font-weight:600;}
a{color:#0969da;}
.link{color:#0969da;}
del{color:#59636e;}
.img-alt{display:inline-block;border:1px dashed #9198a1;border-radius:4px;padding:0 .4em;color:#59636e;font-size:.9em;}
li.task{list-style:none;}
.task-box{display:inline-block;position:relative;box-sizing:border-box;width:.95em;height:.95em;border:2px solid #59636e;border-radius:3px;vertical-align:-.12em;margin:0 .45em 0 -1.35em;}
.task-box.done{background:#0969da;border-color:#0969da;}
.task-box.done::after{content:"";position:absolute;left:.22em;top:.02em;width:.2em;height:.45em;border:solid #ffffff;border-width:0 .13em .13em 0;transform:rotate(45deg);}
.t-kw{color:#a626a4;}
.t-lit{color:#0184bc;}
.t-str{color:#2e7d32;}
.t-num{color:#986801;}
.t-com{color:#8c8c94;}
.t-tag{color:#c2371f;}
.t-attr{color:#986801;}
"""
}

/**
 * 블록 나무를 HTML 로 쓴다. **글자는 [HtmlWriter.text] 로만**, 속성 값은 쓰개의 인자로만 들어간다.
 */
internal class MdRenderer(
    refs: MutableMap<String, String>,
    private val w: HtmlWriter,
    private val checkpoint: () -> Unit,
) {
    private val inlines = MdInlineParser(refs)

    /** `img/N` 의 N 번째가 가리키는 상대 경로. */
    val images = ArrayList<String>()
    private val imageIndex = HashMap<String, Int>()
    private val slugs = HashMap<String, Int>()
    private var leaves = 0

    fun document(doc: MdBlock) {
        for (b in doc.children) block(b, tight = false)
    }

    private fun block(b: MdBlock, tight: Boolean) {
        if (w.full) return
        if (++leaves % 256 == 0) checkpoint()
        when (b.type) {
            BlockType.PARAGRAPH -> paragraph(b.content?.toString().orEmpty(), tight)
            BlockType.HEADING -> heading(b)
            BlockType.BLOCK_QUOTE -> {
                w.start("blockquote")
                for (c in b.children) block(c, tight = false)
                w.end("blockquote")
            }
            BlockType.LIST -> list(b)
            BlockType.CODE_BLOCK -> code(b)
            BlockType.THEMATIC_BREAK -> w.void("hr")
            BlockType.TABLE -> table(b)
            BlockType.DOCUMENT, BlockType.ITEM -> for (c in b.children) block(c, tight)
        }
    }

    /** 촘촘한 목록의 문단은 `<p>` 로 싸지 않는다(명세). */
    private fun paragraph(text: String, tight: Boolean) {
        val trimmed = text.trimMd()
        if (trimmed.isEmpty()) return
        if (!tight) w.start("p")
        inline(inlines.parse(trimmed))
        if (!tight) w.end("p")
    }

    private fun heading(b: MdBlock) {
        val root = inlines.parse(b.content?.toString().orEmpty().trimMd())
        val slug = uniqueSlug(slugify(plainText(root)))
        val tag = "h" + b.level.coerceIn(1, 6)
        w.start(tag, "id" to slug.ifEmpty { null })
        inline(root)
        w.end(tag)
    }

    private fun list(b: MdBlock) {
        val d = b.list ?: return
        val tag = if (d.ordered) "ol" else "ul"
        w.start(tag, "start" to if (d.ordered && d.start != 1) d.start.toString() else null)
        for (item in b.children) {
            if (w.full) break
            val task = taskOf(item)
            w.start("li", "class" to if (task != null) "task" else null)
            for ((i, c) in item.children.withIndex()) {
                if (i == 0 && task != null) {
                    // 상자를 문단 안에 둔다 — 느슨한 목록에서 `<p>` 밖에 두면 상자와 글이 두 줄로 갈린다.
                    val text = c.content?.toString().orEmpty().substring(task.second).trimMd()
                    if (!b.tight) w.start("p")
                    // 상자는 그림일 뿐이라 화면 낭독기가 체크 여부를 모른다. 역할과 상태를 적어 둔다 — 누를 수 없으니
                    // '사용할 수 없음' 이다. 낭독기가 제 언어로 읽으므로 우리 문구가 필요 없다.
                    w.start(
                        "span",
                        "class" to if (task.first) "task-box done" else "task-box",
                        "role" to "checkbox",
                        "aria-checked" to task.first.toString(),
                        "aria-disabled" to "true",
                    ).end("span")
                    if (text.isNotEmpty()) inline(inlines.parse(text))
                    if (!b.tight) w.end("p")
                } else {
                    block(c, tight = b.tight)
                }
            }
            w.end("li")
        }
        w.end(tag)
    }

    /**
     * GFM 할 일 항목인가. (체크됐는가, 떼어 낼 앞머리 길이). 첫 블록이 `[ ]`·`[x]` 로 시작하는 문단일 때만.
     */
    private fun taskOf(item: MdBlock): Pair<Boolean, Int>? {
        val first = item.children.firstOrNull() ?: return null
        if (first.type != BlockType.PARAGRAPH) return null
        val t = first.content ?: return null
        if (t.length < 3 || t[0] != '[' || t[2] != ']') return null
        val mark = t[1]
        if (mark != ' ' && mark != 'x' && mark != 'X') return null
        if (t.length > 3 && t[3] != ' ' && t[3] != '\n') return null
        return (mark != ' ') to 3
    }

    private fun code(b: MdBlock) {
        val lang = b.info.substringBefore(' ').takeIf { it.isNotEmpty() && it.length <= 32 && it.all(::isLangChar) }
        w.start("pre")
        w.start("code", "class" to lang?.let { "language-$it" })
        val highlighter = lang?.let(::highlighterFor)
        if (highlighter == null) {
            w.text(b.literal)
        } else {
            writeHighlighted(w, b.literal, highlighter)
        }
        w.end("code")
        w.end("pre")
    }

    /** 코드 블록의 언어 이름 → 뷰어의 훑개. 모르면 null(칠하지 않는다). */
    private fun highlighterFor(lang: String): RowHighlighter? {
        val key = lang.lowercase(Locale.ROOT)
        val ext = LANG_ALIASES[key] ?: key
        val h = TextLanguage.forFileName("x.$ext")
        return if (h === PlainHighlighter) null else h
    }

    private fun isLangChar(c: Char): Boolean =
        c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '-' || c == '_' || c == '+' || c == '#' || c == '.'

    private fun table(b: MdBlock) {
        w.start("div", "class" to "tbl")
        w.start("table")
        w.start("thead")
        w.start("tr")
        for ((i, cell) in b.header.withIndex()) {
            w.start("th", "style" to alignStyle(b.aligns.getOrElse(i) { Align.NONE }))
            if (cell.isNotEmpty()) inline(inlines.parse(cell))
            w.end("th")
        }
        w.end("tr")
        w.end("thead")
        val rows = b.rows.orEmpty()
        if (rows.isNotEmpty()) {
            w.start("tbody")
            for (row in rows) {
                if (w.full) break
                w.start("tr")
                for ((i, cell) in row.withIndex()) {
                    w.start("td", "style" to alignStyle(b.aligns.getOrElse(i) { Align.NONE }))
                    if (cell.isNotEmpty()) inline(inlines.parse(cell))
                    w.end("td")
                }
                w.end("tr")
            }
            w.end("tbody")
        }
        w.end("table")
        w.end("div")
    }

    private fun alignStyle(a: Align): String? {
        val word = when (a) {
            Align.NONE -> return null
            Align.LEFT -> "left"
            Align.CENTER -> "center"
            Align.RIGHT -> "right"
        }
        return StyleBuilder().add("text-align", CssValues.keyword(word, ALIGN_WORDS)).build()
    }

    // ---- 줄 안 -------------------------------------------------------------------

    private fun inline(parent: MdInline) {
        var n = parent.first
        while (n != null && !w.full) {
            when (n.type) {
                InlineType.TEXT -> n.buf?.let { w.text(it) }
                InlineType.SOFT_BREAK -> w.text("\n")
                InlineType.HARD_BREAK -> w.void("br")
                InlineType.CODE -> w.start("code").text(n.buf ?: "").end("code")
                InlineType.EMPH -> wrap("em", n)
                InlineType.STRONG -> wrap("strong", n)
                InlineType.STRIKE -> wrap("del", n)
                InlineType.LINK -> if (n.dest.startsWith("#") && n.dest.length > 1) {
                    w.start("a", "href" to anchorHref(n.dest))
                    inline(n)
                    w.end("a")
                } else {
                    // 바깥(또는 다른 파일)을 가리킨다. 누를 수 없고, 링크였다는 것만 색으로 남긴다.
                    w.start("span", "class" to "link")
                    inline(n)
                    w.end("span")
                }
                InlineType.IMAGE -> image(n)
                InlineType.AUTOLINK -> w.start("span", "class" to "link").text(n.buf ?: "").end("span")
                InlineType.ROOT -> inline(n)
            }
            n = n.next
        }
    }

    private fun wrap(tag: String, n: MdInline) {
        w.start(tag)
        inline(n)
        w.end(tag)
    }

    private fun image(n: MdInline) {
        val alt = plainText(n)
        val rel = MarkdownPreview.localImagePath(n.dest)
        val index = rel?.let { r ->
            imageIndex[r] ?: if (images.size < MarkdownPreview.MAX_IMAGES) {
                images.add(r)
                (images.size - 1).also { imageIndex[r] = it }
            } else {
                null
            }
        }
        if (index != null) {
            w.void("img", "src" to MarkdownPreview.imagePath(index), "alt" to alt)
        } else if (alt.isNotEmpty()) {
            // 실을 수 없는 그림(바깥 주소·폴더 밖)은 대체 글만 테두리 안에 보인다.
            w.start("span", "class" to "img-alt").text(alt).end("span")
        }
    }

    /**
     * 같은 문서 안의 자리. 제목의 id 는 [slugify] 가 소문자로 만들므로 가리키는 쪽도 소문자로 맞춘다 —
     * `[설치](#Install)` 처럼 대문자로 적은 README 가 흔하고, 크롬의 조각 비교는 대소문자를 가린다.
     */
    private fun anchorHref(dest: String): String = "#" + dest.substring(1).lowercase(Locale.ROOT)

    private fun uniqueSlug(base: String): String {
        if (base.isEmpty()) return base
        val n = slugs[base]
        return if (n == null) {
            slugs[base] = 1
            base
        } else {
            slugs[base] = n + 1
            "$base-$n"
        }
    }

    companion object {
        private val ALIGN_WORDS = setOf("left", "center", "right")

        /** 코드 블록에 흔히 적는 언어 이름 → 뷰어가 아는 확장자. */
        private val LANG_ALIASES = mapOf(
            "kotlin" to "kt", "javascript" to "js", "typescript" to "ts", "python" to "py", "python3" to "py",
            "shell" to "sh", "bash" to "sh", "zsh" to "sh", "console" to "sh", "c++" to "cpp", "csharp" to "cs",
            "c#" to "cs", "rust" to "rs", "ruby" to "rb", "golang" to "go", "yml" to "yaml", "jsonc" to "json",
            "gradle" to "kts", "groovy" to "kts", "objc" to "c", "objective-c" to "c",
        )

        /**
         * 코드 블록을 뷰어의 훑개로 칠한다. 줄마다 상태를 넘기고 진짜 줄바꿈에서 씻는다([RowHighlighter] 의 약속).
         * 색은 클래스로만 준다 — 값이 CSS 로 들어가는 길을 만들지 않는다.
         *
         * **긴 줄은 [TextLimits.SEGMENT_CHARS] 글자씩 끊어 칠한다** — 뷰어의 행과 같은 크기다. 훑개는 한 번에 받은 줄의
         * 조각을 모두 목록에 담으므로, 2 MiB 짜리 한 줄(`1 1 1 …`)을 통째로 넘기면 쓰개가 상한에 닿아 쓰지도 않을 조각이
         * 100만 개 쌓인다. 끊은 자리는 줄이 끝난 것이 아니라 상태를 씻지 않고 넘긴다(뷰어와 같다). 쓰개가 차면 멈춘다.
         */
        internal fun writeHighlighted(w: HtmlWriter, text: String, h: RowHighlighter) {
            var state = RowHighlighter.START
            val spans = ArrayList<Span>(16)
            var lineStart = 0
            while (lineStart <= text.length && !w.full) {
                val nl = text.indexOf('\n', lineStart)
                val lineEnd = if (nl < 0) text.length else nl
                var from = lineStart
                do {
                    val to = minOf(lineEnd, from + TextLimits.SEGMENT_CHARS)
                    val row = text.substring(from, to)
                    spans.clear()
                    state = h.highlight(row, state, spans)
                    writeSpans(w, row, spans)
                    from = to
                } while (from < lineEnd && !w.full)
                if (nl < 0) break
                w.text("\n")
                state = h.afterNewline(state)
                lineStart = nl + 1
            }
        }

        private fun writeSpans(w: HtmlWriter, row: String, spans: List<Span>) {
            var at = 0
            for (sp in spans) {
                if (w.full) return
                val s0 = sp.start.coerceIn(at, row.length)
                val e0 = sp.end.coerceIn(s0, row.length)
                if (s0 > at) w.text(row.substring(at, s0))
                val cls = classOf(sp.kind)
                if (cls == null) {
                    w.text(row.substring(s0, e0))
                } else if (e0 > s0) {
                    w.start("span", "class" to cls).text(row.substring(s0, e0)).end("span")
                }
                at = e0
            }
            if (at < row.length) w.text(row.substring(at))
        }

        private fun classOf(kind: TokenKind): String? = when (kind) {
            TokenKind.PLAIN -> null
            TokenKind.KEYWORD -> "t-kw"
            TokenKind.LITERAL -> "t-lit"
            TokenKind.STRING -> "t-str"
            TokenKind.NUMBER -> "t-num"
            TokenKind.COMMENT -> "t-com"
            TokenKind.TAG -> "t-tag"
            TokenKind.ATTRIBUTE -> "t-attr"
        }

        /**
         * GitHub 식 자리 이름. 소문자로, 글자·숫자·`-`·`_` 만 남기고, 공백은 `-` 로. 한글은 글자라 남는다.
         */
        fun slugify(text: String): String {
            val out = StringBuilder(text.length)
            for (c in text.trim().lowercase(Locale.ROOT)) {
                when {
                    c.isLetterOrDigit() || c == '-' || c == '_' -> out.append(c)
                    c == ' ' -> out.append('-')
                    else -> Unit
                }
            }
            return out.toString()
        }

        /** 노드 아래의 글자만(대체 글·자리 이름). 깊이는 파서가 이미 묶었다. */
        fun plainText(n: MdInline): String {
            val sb = StringBuilder()
            collect(n, sb)
            return sb.toString()
        }

        private fun collect(n: MdInline, sb: StringBuilder) {
            var c = n.first
            while (c != null) {
                when (c.type) {
                    InlineType.TEXT, InlineType.CODE, InlineType.AUTOLINK -> c.buf?.let { sb.append(it) }
                    InlineType.SOFT_BREAK, InlineType.HARD_BREAK -> sb.append(' ')
                    else -> collect(c, sb)
                }
                c = c.next
            }
        }
    }
}
