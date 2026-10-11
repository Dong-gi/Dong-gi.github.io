package io.github.donggi.iroiroviewer.format.text

import java.util.Locale

/**
 * 미리보기 쪽이 싣는 것 하나. 쪽에는 **번호로만** 적힌다(마크다운 `img/N`, HTML `/r/N`) — 그 번호가 가리키는 것이 이것이다.
 *
 * 이름을 쪽에 그대로 적지 않는 까닭은 둘이다. ① 위생기(`Urls.rewrite`)가 URL 속성에서 공백을 지우고 `#`·`?`·`%` 를 조각·질의·
 * 인코딩으로 읽으므로, 사용자 파일의 이름(`My Page_files/그림 (1).png`)을 그대로 적으면 그림이 조용히 사라진다(함정 표).
 * ② 쪽이 가리킬 수 있는 것을 **우리가 만든 표 하나**로 묶는다 — WebView 가 청할 수 있는 것은 이 표에 있는 것뿐이고, 화면은
 * 그 표의 항목을 디스크에서 다시 고른다(폴더 밖·심볼릭 링크·크기는 그쪽에서 본다).
 */
sealed interface PreviewAsset {

    /** 무엇으로 쓰이는가. 내줄 수 있는 형식이 이것으로 갈린다([PreviewTypes.ofFile]). */
    enum class Use {
        /** HTML 의 `<img>`·`background` 속성 — 그림만. */
        IMAGE,

        /** CSS 가 가리킨 것(`background-image`·`@font-face`) — 그림과 글꼴. 어느 쪽인지는 CSS 를 이해해야 알 수 있어 묶었다. */
        STYLE,

        /**
         * 마크다운의 그림. **마크다운 미리보기(14단계)의 표 그대로다** — HTML 이 더한 형식(`jfif`·`ico`)을 마크다운에 들이지 않는다.
         * 마크다운의 동작은 HTML 미리보기를 세우며 바뀌지 않아야 했다.
         */
        MARKDOWN,
    }

    /** 디스크의 파일. [path] 는 미리보는 파일의 폴더 기준 상대 경로다(정규화했고 퍼센트 인코딩을 푼 이름). */
    class Disk(val path: String, val use: Use) : PreviewAsset

    /** 스타일시트. [css] 는 위생을 거친 CSS(UTF-8)이고, 읽지 못했으면 null 이다(그때 화면은 404 를 준다). */
    class Sheet(val path: String, val css: ByteArray?) : PreviewAsset

    /** `data:` 주소를 푼 것. [mimeType] 은 문서가 적은 값이 아니라 우리 표에서 고친 값이다([PreviewTypes.ofData]). */
    class Data(val mimeType: String, val bytes: ByteArray) : PreviewAsset
}

/**
 * 미리보기가 **내주는 형식의 유일한 표.** 파일은 확장자로, `data:` 는 적힌 형식으로 찾되 둘 다 이 표에 있는 것만 내준다 — 내용을
 * 보고 짐작하지 않는다(함정 표의 `rawResourceType`: 크롬은 형식이 비었거나 이상한 파일도 스타일시트로 적용한다).
 *
 * SVG 는 언제나 그림으로만 실린다(`<img>`·CSS 바탕) — 그 안의 스크립트·바깥 참조는 돌지 않는다. 다른 SVG 문서의 조각을 가리키는
 * 길(`<use href="x.svg#g">`·`filter:url(x.svg#f)`)은 위생기와 HTML 미리보기가 막는다.
 */
object PreviewTypes {

    /** 마크다운 미리보기(14단계)가 싣던 그림. 글자 하나 바꾸지 않고 옮겼다(`MarkdownImages.TYPES`). */
    private val MARKDOWN_IMAGES = mapOf(
        "png" to "image/png",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "gif" to "image/gif",
        "webp" to "image/webp",
        "bmp" to "image/bmp",
        "svg" to "image/svg+xml",
        "avif" to "image/avif",
    )

    /** HTML 이 싣는 그림 — 마크다운의 것에 웹에서 흔한 두 이름(`jfif`·`ico`)을 더했다. */
    private val IMAGES = mapOf(
        "png" to "image/png",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "jfif" to "image/jpeg",
        "gif" to "image/gif",
        "webp" to "image/webp",
        "bmp" to "image/bmp",
        "svg" to "image/svg+xml",
        "avif" to "image/avif",
        "ico" to "image/x-icon",
    )

    /** 웹 글꼴. `eot` 는 인터넷 익스플로러 것이라 크롬이 읽지 않는다. */
    private val FONTS = mapOf(
        "woff2" to "font/woff2",
        "woff" to "font/woff",
        "ttf" to "font/ttf",
        "otf" to "font/otf",
    )

    /** `data:` 에 적힌 그림 형식 → 내줄 형식. 흔한 비표준 이름(`image/jpg`)도 받아 고친다. */
    private val DATA_IMAGES = mapOf(
        "image/png" to "image/png",
        "image/jpeg" to "image/jpeg",
        "image/jpg" to "image/jpeg",
        "image/pjpeg" to "image/jpeg",
        "image/gif" to "image/gif",
        "image/webp" to "image/webp",
        "image/bmp" to "image/bmp",
        "image/svg+xml" to "image/svg+xml",
        "image/avif" to "image/avif",
        "image/x-icon" to "image/x-icon",
        "image/vnd.microsoft.icon" to "image/x-icon",
    )

    /** `data:` 에 적힌 글꼴 형식. CSS 가 글꼴을 품어 둘 때(아이콘 글꼴) 실제로 쓰이는 이름들이다. */
    private val DATA_FONTS = mapOf(
        "font/woff2" to "font/woff2",
        "font/woff" to "font/woff",
        "font/ttf" to "font/ttf",
        "font/otf" to "font/otf",
        "font/sfnt" to "font/ttf",
        "application/font-woff2" to "font/woff2",
        "application/x-font-woff2" to "font/woff2",
        "application/font-woff" to "font/woff",
        "application/x-font-woff" to "font/woff",
        "application/x-font-ttf" to "font/ttf",
        "application/x-font-truetype" to "font/ttf",
        "application/font-sfnt" to "font/ttf",
        "application/x-font-opentype" to "font/otf",
        "application/vnd.ms-opentype" to "font/otf",
    )

    private const val STYLESHEET_EXTENSION = "css"

    /** 파일 이름의 확장자로 형식을. [use] 에 맞지 않는 형식이면 null(`<img>` 가 가리킨 글꼴은 내주지 않는다). */
    fun ofFile(name: String, use: PreviewAsset.Use): String? {
        val ext = extensionOf(name)
        return when (use) {
            PreviewAsset.Use.MARKDOWN -> MARKDOWN_IMAGES[ext]
            PreviewAsset.Use.IMAGE -> IMAGES[ext]
            PreviewAsset.Use.STYLE -> IMAGES[ext] ?: FONTS[ext]
        }
    }

    /** 스타일시트로 읽을 파일인가. `<link rel="stylesheet">` 가 가리켜도 확장자가 `.css` 가 아니면 읽지 않는다. */
    fun isStylesheetName(name: String): Boolean = extensionOf(name) == STYLESHEET_EXTENSION

    /** `data:` 의 형식([mediaType], 매개변수를 뗀 소문자)으로 내줄 형식을. 표에 없으면 null. 마크다운은 `data:` 를 싣지 않는다. */
    fun ofData(mediaType: String, use: PreviewAsset.Use): String? {
        val type = mediaType.substringBefore(';').trim().lowercase(Locale.ROOT)
        return when (use) {
            PreviewAsset.Use.MARKDOWN -> null
            PreviewAsset.Use.IMAGE -> DATA_IMAGES[type]
            PreviewAsset.Use.STYLE -> DATA_IMAGES[type] ?: DATA_FONTS[type]
        }
    }

    /**
     * 스크립트를 실행하는 쪽([LivePage])이 같은 폴더에서 싣는 파일의 형식. 위생을 거친 쪽의 표(그림·글꼴)에 **쪽이 스스로 읽는 것**을
     * 더했다 — 스타일시트·스크립트·자료(`json`·`xml`·`txt`)·다른 HTML(`<iframe>`). 그 쪽도 스크립트를 돈다(사용자가 이 쪽에서 고른
     * 실행이 그 폴더에 걸린다). 형식은 여기서도 **이름으로만** 정한다 — 내용을 보고 짐작하지 않는다. 소리·영상·PDF 는 싣지 않는다.
     */
    fun ofLive(name: String): String? {
        val ext = extensionOf(name)
        return IMAGES[ext] ?: FONTS[ext] ?: LIVE[ext]
    }

    private val LIVE = mapOf(
        "css" to "text/css",
        "js" to "text/javascript",
        "mjs" to "text/javascript",
        "json" to "application/json",
        "html" to "text/html",
        "htm" to "text/html",
        "xhtml" to "application/xhtml+xml",
        "xml" to "text/xml",
        "txt" to "text/plain",
    )

    /**
     * 마지막 점 뒤(소문자). **마크다운 미리보기의 규칙 그대로다** — 점으로 시작하는 이름(`.png`)도 그 뒤를 확장자로 본다. 경로를 받으면
     * 폴더 이름의 점 뒤가 나오지만(`a.d/x` → `d/x`) 그런 확장자는 표에 없다.
     */
    private fun extensionOf(name: String): String = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
}

/**
 * 쪽이 가리키는 것을 모으는 표. **번호가 곧 주소다**([HtmlPreview.assetUrl]). 같은 것을 두 번 가리키면 같은 번호를 준다.
 *
 * 상한이 둘이다 — 항목 수([maxAssets])와 `data:` 를 푼 바이트의 합([maxDataBytes]). 넘으면 null 을 주고, 부르는 쪽은 그 참조를
 * 싣지 않는다(그림 하나가 빠질 뿐 쪽은 그려진다).
 */
internal class AssetTable(
    private val maxAssets: Int = HtmlPreview.MAX_ASSETS,
    private val maxDataBytes: Long = HtmlPreview.MAX_DATA_BYTES,
    private val maxSheets: Int = HtmlPreview.MAX_SHEETS,
) {
    private val items = ArrayList<PreviewAsset>()
    private val byKey = HashMap<String, Int>()
    private var dataBytes = 0L
    private var sheets = 0

    val size: Int get() = items.size

    fun disk(path: String, use: PreviewAsset.Use): String? =
        add("d${use.ordinal}:$path") { PreviewAsset.Disk(path, use) }

    fun sheet(path: String): String? {
        byKey["s:$path"]?.let { return HtmlPreview.assetUrl(it) }
        if (sheets >= maxSheets) return null
        return add("s:$path") { PreviewAsset.Sheet(path, null) }?.also { sheets++ }
    }

    /**
     * @param key 같은 `data:` 를 한 번만 담기 위한 열쇠(대개 주소 그대로). 길면 null 을 준다 — 수 MB 짜리 주소를 열쇠로 들면
     *   같은 바이트를 두 벌 쥔다.
     */
    fun data(mimeType: String, bytes: ByteArray, key: String?): String? {
        key?.let { k -> byKey["b:$k"]?.let { return HtmlPreview.assetUrl(it) } }
        if (dataBytes + bytes.size > maxDataBytes) return null
        val url = add(if (key != null) "b:$key" else null) { PreviewAsset.Data(mimeType, bytes) } ?: return null
        dataBytes += bytes.size
        return url
    }

    /** 스타일시트 항목(번호, 경로) — 문서 차례. */
    fun sheetEntries(): List<Pair<Int, String>> =
        items.withIndex().mapNotNull { (i, a) -> (a as? PreviewAsset.Sheet)?.let { i to it.path } }

    fun setSheet(index: Int, css: ByteArray?) {
        val old = items[index] as PreviewAsset.Sheet
        items[index] = PreviewAsset.Sheet(old.path, css)
    }

    fun snapshot(): List<PreviewAsset> = ArrayList(items)

    private inline fun add(key: String?, make: () -> PreviewAsset): String? {
        if (key != null) byKey[key]?.let { return HtmlPreview.assetUrl(it) }
        if (items.size >= maxAssets) return null
        items.add(make())
        val index = items.size - 1
        if (key != null) byKey[key] = index
        return HtmlPreview.assetUrl(index)
    }
}
