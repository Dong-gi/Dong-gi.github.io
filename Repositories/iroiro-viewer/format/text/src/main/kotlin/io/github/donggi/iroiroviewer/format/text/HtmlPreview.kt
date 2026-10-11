package io.github.donggi.iroiroviewer.format.text

import io.github.donggi.iroiroviewer.charset.TextEncoding
import io.github.donggi.iroiroviewer.format.UnsupportedFeatures
import io.github.donggi.iroiroviewer.format.html.HtmlSanitizer
import io.github.donggi.iroiroviewer.safety.ParseLimitExceededException
import io.github.donggi.iroiroviewer.safety.ParseLimits
import java.io.InputStream
import java.util.Locale

/**
 * HTML 미리보기(14단계 뒤). `.html`·`.htm`·`.xhtml` 을 **그 파일의 HTML 그대로** 잠긴 WebView 에 띄울 한 벌로 만든다.
 *
 * ## 길
 *
 * 1. [prepare] — 앞 패스([HtmlRewriter]). 문서가 가리키는 그림·스타일시트·글꼴을 우리 번호(`/r/N`)로 바꿔 적고, 스크립트를 버리고,
 *    첫 DOCTYPE 으로 그릴 모드를 정한다([DocMode]).
 * 2. 화면이 [Prepared.stylesheets] 를 디스크에서 읽는다 — 폴더 밖·심볼릭 링크·크기는 화면이 본다. 상한은 [loadStylesheets].
 * 3. [finish] — 스타일시트를 그 파일의 폴더 기준으로 고쳐 `HtmlSanitizer.sanitizeCss` 에 거르고, 문서를 `HtmlSanitizer.sanitize`
 *    에 거르고(리졸버는 **우리 번호만** 통과시킨다), 앞에 얇은 머리를 붙인다([shell]).
 *
 * ## 껍데기를 쓰지 않는다(`HtmlShell` 이 아니라 [shell])
 *
 * `HtmlShell.wrap` 은 책(EPUB)과 마크다운 미리보기를 위한 것이다 — 읽기 CSS(`body{margin:0;padding:16px 18px 32px;word-break:keep-all}`,
 * `img{height:auto}`, `pre{white-space:pre-wrap}`)를 `<head>` 의 **끝**에 넣어 같은 특이도에서 이기게 하고, 언제나
 * `<!DOCTYPE html>` 을 붙인다. 사용자의 쪽에는 둘 다 틀리다. 쪽 자신의 `body{margin:0 auto;max-width:…}` 가 지고(가운데 맞춘 쪽이
 * 왼쪽에 붙는다), 쪽마다 여백이 덧붙고, 한국어의 줄 나눔 규칙이 일본어 쪽에 걸리며, 쿼크 모드로 만든 옛 쪽이 표준 모드로 다시
 * 짜인다. 그래서 여기서는 **맨 앞에** 머리를 둔다 — 쪽의 스타일이 모두 그 뒤에 오므로 같은 특이도에서 쪽이 이기고, 우리 규칙은
 * `:where()` 라 특이도가 0 이다(쪽의 `*{…}` 에도 진다). 우리가 얹는 것은 둘뿐이다 — 글자 확대(`textZoom`)가 늘 같은 배율로 걸리게
 * 하는 `-webkit-text-size-adjust:100%`, 그림이 화면 폭을 넘지 않게 하는 `max-width:100%`(폭·높이가 적힌 그림은 비율을 지키게
 * `height:auto`). 바탕은 칠하지 않는다 — 쪽이 정하지 않으면 WebView 의 흰 바탕이고, `html` 에 바탕을 칠하면 쪽의 `body` 바탕이
 * 화면 전체로 번지지 않는다(CSS 의 바탕 전파).
 *
 * ## 글자 코드
 *
 * 원문은 텍스트 뷰어가 이미 판정한 인코딩으로 푼다([readSource]) — 뷰어에서 인코딩을 바꾸면 미리보기도 다시 만든다. 결과는 UTF-8
 * 로 내주고(`WebResource` 의 `utf-8`), 머리에 `<meta charset="utf-8">` 을 하나 둔다. 문서의 `<meta charset>`·`http-equiv` 는
 * 위생기가 지운다 — 남아 있어도 전송 쪽의 인코딩이 이기지만, 겹쳐 둔다.
 *
 * ## 하지 않는 것
 *
 * 스크립트·폼(태그만 지우고 글은 남긴다)·프레임·삽입 개체·바깥 주소·다른 쪽으로 가는 링크(누를 수는 있되 아무 일도 하지 않는다,
 * [LINK])·`@import`(위생기가 지운다)·`<base>`(위생기가 지운다 — 주소는 언제나 이 파일의 폴더 기준이다).
 *
 * 스크립트가 곧 내용인 쪽(보안 메일)은 사용자가 고르면 이 길을 거치지 않고 **바이트 그대로** 스크립트를 켠 WebView 에 내준다
 * ([LivePage], [Result.scripted] 가 그 단추를 단다).
 */
object HtmlPreview {

    /** 원문의 상한. 저장한 웹 쪽은 1~5 MB 가 흔하다(스크립트를 버리면 그보다 작아진다). 넘으면 화면이 상한을 말한다. */
    const val MAX_SOURCE_BYTES: Long = 8L * 1024 * 1024

    /** 앞 패스가 쓰는 결과의 상한(글자). 닿으면 표시 하나의 경계에서 멈추고 [Result.truncated] 로 알린다. */
    const val MAX_HTML_CHARS = 10 * 1024 * 1024

    /** 한 쪽이 가리킬 수 있는 것(그림·글꼴·스타일시트·`data:`)의 수. 넘으면 그 뒤의 것은 싣지 않는다. */
    const val MAX_ASSETS = 2_000

    /** 스타일시트 수. 저장한 쪽은 수십 개다. */
    const val MAX_SHEETS = 64

    /** 스타일시트 하나와 그 합의 상한. 위생을 거친 CSS 를 메모리에 들고 있는다. */
    const val MAX_SHEET_BYTES: Long = 4L * 1024 * 1024
    const val MAX_SHEETS_TOTAL_BYTES: Long = 12L * 1024 * 1024

    /** `data:` 하나와 그 합의 상한(푼 바이트). SingleFile 처럼 모든 것을 품은 쪽이 있다. */
    const val MAX_DATA_ITEM_BYTES: Long = 8L * 1024 * 1024
    const val MAX_DATA_BYTES: Long = 16L * 1024 * 1024

    /**
     * 다른 쪽·파일·바깥으로 가는 링크가 받는 주소. 링크의 모양(`a:link`)은 남고, 누르면 화면이 받아 삼킨다 — 위생기의 리졸버가 이것을
     * 통과시키고, 화면의 `onNavigate` 가 '우리가 처리했다' 로 답한다. 따라가는 것은 나중의 일이다.
     */
    const val LINK = "/~link"

    private const val ASSET_PREFIX = "r/"
    private val EXTENSIONS = setOf("html", "htm", "xhtml")

    /** HTML 확장자인가. 미리보기 단추를 이 파일들에도 띄운다. */
    fun isHtmlName(name: String): Boolean = extensionOf(name) in EXTENSIONS

    /** XHTML 인가 — 브라우저가 로컬 파일을 그러듯 확장자로 정한다. XHTML 은 언제나 표준 모드로 그린다. */
    fun isXhtmlName(name: String): Boolean = extensionOf(name) == "xhtml"

    private fun extensionOf(name: String): String = name.substringAfterLast('.', "").lowercase(Locale.ROOT)

    /** 쪽의 경로. **번호가 붙는다** — 다시 만든 쪽을 WebView 가 새로 읽게 하려면 주소가 달라야 한다(마크다운과 같다). */
    fun pagePath(generation: Int): String = "page-$generation.html"

    /** [index] 번째 것의 주소. 문서와 스타일시트 어디서든 같은 것을 가리키게 뿌리 기준으로 적는다. */
    internal fun assetUrl(index: Int): String = "/$ASSET_PREFIX$index"

    /** 화면이 받은 경로(`WebHost.pathOf` — 앞의 `/` 가 없다)가 우리 번호면 그 번호. */
    fun assetIndexOf(path: String): Int? {
        if (!path.startsWith(ASSET_PREFIX)) return null
        val rest = path.substring(ASSET_PREFIX.length)
        if (rest.isEmpty() || rest.length > 6 || !rest.all { it in '0'..'9' }) return null
        return rest.toInt()
    }

    /**
     * 파일의 글을 읽는다. BOM 은 바이트로 건너뛰고([bomLength]), [maxBytes] 에서 끊는다. 판정은 텍스트 뷰어가 했다 — 여기서는 그
     * 인코딩으로 디코드만 한다([MarkdownPreview.readSource] 와 한 벌이다).
     */
    fun readSource(
        openAt: (Long) -> InputStream,
        encoding: TextEncoding,
        bomLength: Int,
        maxBytes: Long = MAX_SOURCE_BYTES,
    ): String = readPreviewSource(openAt, encoding, bomLength, maxBytes)

    /** 앞 패스의 결과. [finish] 에 **한 번** 넘긴다(표를 이어 쓰고, 앞 패스의 글을 넘겨준 뒤 놓는다). */
    class Prepared internal constructor(
        html: String,
        internal val assets: AssetTable,
        internal val withheld: Withheld,
        internal val standards: Boolean,
        internal val truncated: Boolean,
    ) {
        private var html: String? = html

        /** 읽어 와야 할 스타일시트 — 이 파일 폴더 기준의 상대 경로, 문서 차례. */
        val stylesheets: List<String> = assets.sheetEntries().map { it.second }

        /**
         * 앞 패스의 글을 넘겨주고 놓는다 — 큰 쪽은 앞 패스의 글과 위생기의 결과가 각각 수십 MB 다. 이것이 들고 있으면 화면이 쪽을
         * 띄우는 내내 두 벌이 남는다.
         */
        internal fun take(): String = checkNotNull(html) { "finish 는 한 번만 부른다" }.also { html = null }
    }

    /**
     * @param xhtml XHTML 인가([isXhtmlName]). 그러면 DOCTYPE 과 상관없이 표준 모드다.
     * @param selfName 이 파일의 이름. `page.html#자리` 를 같은 쪽 안의 자리로 읽는다.
     * @param checkpoint 표시 몇백 개마다 불린다 — 부르는 쪽이 취소를 여기서 던진다.
     */
    fun prepare(
        source: String,
        xhtml: Boolean,
        selfName: String?,
        maxChars: Int = MAX_HTML_CHARS,
        checkpoint: () -> Unit = {},
    ): Prepared {
        // 앞의 BOM 글자(U+FEFF)는 뗀다 — 뷰어가 판정과 다른 인코딩을 손으로 고르면 BOM 바이트를 건너뛰지 않아 글자로 남는다.
        val text = if (source.isNotEmpty() && source[0].code == BOM) source.substring(1) else source
        val assets = AssetTable()
        val withheld = Withheld()
        val rewriter = HtmlRewriter(text, xhtml, selfName, assets, withheld, maxChars, checkpoint)
        val html = rewriter.run()
        val standards = xhtml || DocMode.of(rewriter.doctype, rewriter.doctypeClosed) != DocMode.Mode.QUIRKS
        return Prepared(html, assets, withheld, standards, rewriter.truncated)
    }

    /**
     * 스타일시트를 읽는다. **상한은 여기서 지킨다** — 하나 [MAX_SHEET_BYTES], 합 [MAX_SHEETS_TOTAL_BYTES]. [read] 는 경로와 그 파일에
     * 허락한 크기를 받아 바이트를 주거나(그보다 크면 null) 못 읽으면 null 을 준다. 화면이 디스크 IO 에서 부른다.
     */
    fun loadStylesheets(paths: List<String>, read: (path: String, maxBytes: Long) -> ByteArray?): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        var total = 0L
        for (path in paths) {
            if (out.size >= MAX_SHEETS || path in out) continue
            val room = MAX_SHEETS_TOTAL_BYTES - total
            if (room <= 0) break
            val bytes = read(path, minOf(MAX_SHEET_BYTES, room)) ?: continue
            if (bytes.size > minOf(MAX_SHEET_BYTES, room)) continue
            total += bytes.size
            out[path] = bytes
        }
        return out
    }

    class Result(
        /** 화면에 띄울 문서 하나(머리까지 붙인 것). */
        val html: String,
        /** `/r/N` 이 가리키는 것. 화면이 디스크에서 다시 고른다. */
        val assets: List<PreviewAsset>,
        /** 상한에 닿아 뒤를 버렸다. */
        val truncated: Boolean,
        /** 스크립트·프레임·삽입 개체·바깥 자원 가운데 무엇이든 버렸다 — 화면이 한 줄로 말한다. */
        val withheld: Boolean,
        /**
         * 버린 것 가운데 **스크립트가 있었다**(`<script>`·`on…` 속성·`javascript:` 주소·프레임). 화면이 그 쪽에만 '스크립트 실행' 을 단다
         * ([LivePage]) — 바깥 그림만 버린 쪽에 그 단추를 달면 눌러도 달라지는 것이 없다.
         */
        val scripted: Boolean = false,
    )

    /**
     * @param sheets [loadStylesheets] 가 읽은 것.
     * @param sheetEncoding 스타일시트가 BOM·`@charset` 으로 스스로 말하지 않을 때 쓸 인코딩 — 문서의 인코딩이다(CSS 명세의 환경 인코딩).
     */
    fun finish(
        prepared: Prepared,
        sheets: Map<String, ByteArray>,
        sheetEncoding: TextEncoding?,
        checkpoint: () -> Unit = {},
    ): Result = finish(prepared, sheets, sheetEncoding, checkpoint, SANITIZER_CAP)

    /** [cap] 은 위생기의 상한(글자). 시험이 작은 값으로 넘친 쪽의 길을 탄다. */
    internal fun finish(
        prepared: Prepared,
        sheets: Map<String, ByteArray>,
        sheetEncoding: TextEncoding?,
        checkpoint: () -> Unit,
        cap: Int,
    ): Result {
        val dropped = UnsupportedFeatures()
        val fallback = CssText.fallbackOf(sheetEncoding)
        val css = CssRewriter(prepared.assets, prepared.withheld, checkpoint)
        val limits = ParseLimits.DEFAULT.copy(maxXmlBytes = cap.toLong())
        for ((index, path) in prepared.assets.sheetEntries()) {
            checkpoint()
            val bytes = sheets[path] ?: continue
            // 파일 안의 주소는 **그 파일의 폴더** 기준이다(`page_files/style.css` 의 `url(bg.png)` 는 `page_files/bg.png`).
            val dir = path.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
            // 상한을 넘는 시트는 싣지 않는다 — 시트 하나가 쪽 전체를 실패로 만들지 않는다.
            val rewritten = css.rewrite(CssText.decode(bytes, fallback), dir, cap) ?: continue
            val clean = try {
                HtmlSanitizer.sanitizeCss(rewritten, RESOLVER, limits, checkpoint)
            } catch (e: ParseLimitExceededException) {
                continue
            }
            for ((kind, count) in clean.dropped.snapshot()) dropped.record(kind, count)
            prepared.assets.setSheet(index, clean.html.toByteArray(Charsets.UTF_8))
        }
        checkpoint()
        val html = prepared.take()
        var truncated = prepared.truncated
        val body = try {
            HtmlSanitizer.sanitize(html, RESOLVER, limits, checkpoint)
        } catch (e: ParseLimitExceededException) {
            // 앞 패스가 위생기가 키울 것을 먼저 키워 두었으므로 여기 올 일은 드물다. 그래도 오면 위생기가 **어떤 입력이든** 상한 안에
            // 쓸 수 있는 길이(글자 하나가 많아야 여섯 자 — 따옴표의 `&quot;`)까지 잘라 다시 거른다. 자르는 자리는 `<` 앞이다 — 앞 패스는
            // 태그가 아닌 `<` 를 모두 `&lt;` 로 적었으므로 `<` 는 언제나 표시의 시작이다.
            truncated = true
            val cut = html.lastIndexOf('<', cap / MAX_GROWTH).let { if (it <= 0) minOf(html.length, cap / MAX_GROWTH) else it }
            HtmlSanitizer.sanitize(html.substring(0, cut), RESOLVER, limits, checkpoint)
        }
        for ((kind, count) in body.dropped.snapshot()) dropped.record(kind, count)
        return Result(
            html = shell(body.html, prepared.standards),
            assets = prepared.assets.snapshot(),
            truncated = truncated,
            withheld = withheldOf(prepared.withheld, dropped),
            scripted = scriptedOf(prepared.withheld, dropped),
        )
    }

    /**
     * 위생기에 주는 상한(글자) — 앞 패스 결과의 두 배. 기본값(32 Mi 자)이면 `<` 가 `&lt;` 넷이 되는 입력이 33 M 자 쪽으로 커져 화면이
     * 그대로 들고 있었다. 앞 패스가 위생기가 키울 것을 먼저 키워 두므로 정상인 쪽은 이 안에 넉넉히 든다.
     */
    private const val SANITIZER_CAP = 2 * MAX_HTML_CHARS

    /** 위생기가 입력 한 글자를 많아야 몇 자로 쓰는가(속성 값의 `"` → `&quot;`). 넘친 쪽을 다시 거를 때 자를 길이다. */
    private const val MAX_GROWTH = 6

    private const val BOM = 0xFEFF

    /** 스타일시트 없이 한 번에(시험·작은 쪽). */
    fun render(
        source: String,
        xhtml: Boolean = false,
        selfName: String? = null,
        sheets: Map<String, ByteArray> = emptyMap(),
        sheetEncoding: TextEncoding? = TextEncoding.UTF_8,
        checkpoint: () -> Unit = {},
    ): Result = finish(prepare(source, xhtml, selfName, checkpoint = checkpoint), sheets, sheetEncoding, checkpoint)

    /**
     * 위생기의 리졸버. **우리가 적은 번호와 [LINK] 만** 통과시킨다 — 문서가 스스로 적은 상대 주소는 앞 패스가 모두 번호로 바꾸거나
     * 지웠다. 그래서 보통은 이 검사에 걸릴 것이 없다 — 앞 패스가 놓친 자리를 막는 **두 번째 겹**이다. `#자리` 는 리졸버에 닿기 전에
     * 위생기가 통과시킨다.
     */
    internal val RESOLVER = HtmlSanitizer.Resolver { url ->
        url.takeIf { it == LINK || (it.startsWith("/") && assetIndexOf(it.substring(1)) != null) }
    }

    /**
     * 스크립트를 켜면 달라질 것이 있었는가. 프레임도 든다 — `<iframe srcdoc>` 의 스크립트는 위생기에게 '프레임' 으로만 세어져, 스크립트만
     * 보면 그런 쪽에 단추가 붙지 않았다. 바깥 그림만 버린 쪽에는 붙지 않는다.
     */
    private fun scriptedOf(w: Withheld, dropped: UnsupportedFeatures): Boolean {
        if (w.scripts > 0) return true
        val counts = dropped.snapshot()
        return SCRIPTED_KINDS.any { (counts[it] ?: 0) > 0 }
    }

    private val SCRIPTED_KINDS = listOf(
        UnsupportedFeatures.SCRIPT,
        UnsupportedFeatures.EVENT_HANDLER,
        UnsupportedFeatures.FRAME,
    )

    private fun withheldOf(w: Withheld, dropped: UnsupportedFeatures): Boolean {
        if (w.scripts > 0 || w.remote > 0) return true
        val counts = dropped.snapshot()
        return WITHHELD_KINDS.any { (counts[it] ?: 0) > 0 }
    }

    /**
     * 화면이 '싣지 않았다' 고 말할 것. 폼(태그만 지우고 글은 남긴다)·모르는 요소는 들지 않는다. **위생기의 '바깥 참조' 도 들지 않는다** —
     * 위생기는 `@import` 를 모두, `integrity`·`crossorigin` 속성까지 바깥 참조로 세어 폴더 안의 것만 쓰는 멀쩡한 쪽에도 안내가 떴다.
     * 바깥 자원은 앞 패스가 주소를 읽을 때 정확히 센다([Withheld.remote]).
     */
    private val WITHHELD_KINDS = listOf(
        UnsupportedFeatures.SCRIPT,
        UnsupportedFeatures.EVENT_HANDLER,
        UnsupportedFeatures.FRAME,
        UnsupportedFeatures.EMBEDDED_OBJECT,
    )

    /**
     * 머리. DOCTYPE(표준 모드일 때만), 글자 코드, 그리고 쪽의 어떤 규칙에도 지는 최소한의 CSS(위 'HtmlShell 을 쓰지 않는다').
     * `<html>` 보다 앞에 두어도 된다 — HTML 파서가 암묵의 `<head>` 를 열고, 뒤에 오는 문서의 `<html lang=…>`·`<body class=…>` 의
     * 속성을 그 요소들에 옮겨 붙인다(명세의 'in head'·'in body' 규칙). 그래서 `</head>` 를 글자로 찾을 일이 없다.
     */
    internal fun shell(body: String, standards: Boolean): String {
        val sb = StringBuilder(body.length + 256)
        if (standards) sb.append("<!DOCTYPE html>")
        sb.append("<meta charset=\"utf-8\">")
        sb.append("<style>").append(SHELL_CSS)
        if (standards) sb.append(STANDARDS_CSS)
        sb.append("</style>")
        sb.append(body)
        return sb.toString()
    }

    private const val SHELL_CSS =
        ":where(html){-webkit-text-size-adjust:100%}" +
            ":where(img){max-width:100%}"

    /**
     * 폭·높이가 적힌 그림이 화면 폭에 맞춰 줄 때 비율을 지키게 한다 — **표준 모드에서만.** 쿼크 모드의 옛 쪽은 투명한 1×1 그림에
     * `width=1 height=50` 을 적어 틈을 만들었는데(spacer), `height:auto` 는 그 그림의 본디 비율(1:1)을 따라 높이를 1 로 만든다 — 쓴
     * 사람이 본 모양을 지키겠다는 [DocMode] 의 판단과 어긋난다. 표준 모드의 그런 그림은 드물고, 그때도 비율대로 줄어든다.
     */
    private const val STANDARDS_CSS = ":where(img[width][height]){height:auto}"
}
