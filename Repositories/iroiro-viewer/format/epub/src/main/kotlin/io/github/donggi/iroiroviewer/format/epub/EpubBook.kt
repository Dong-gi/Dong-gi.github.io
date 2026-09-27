package io.github.donggi.iroiroviewer.format.epub

import io.github.donggi.iroiroviewer.format.FormatId
import io.github.donggi.iroiroviewer.format.OpenedDocument
import io.github.donggi.iroiroviewer.format.ParseWarning
import io.github.donggi.iroiroviewer.format.UnsupportedFeatures
import io.github.donggi.iroiroviewer.format.archive.ArchiveEntry
import io.github.donggi.iroiroviewer.format.archive.ArchiveReader
import io.github.donggi.iroiroviewer.format.html.HtmlSanitizer
import io.github.donggi.iroiroviewer.format.html.HtmlShell
import io.github.donggi.iroiroviewer.safety.ParseLimits
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 열린 EPUB 하나.
 *
 * ## 무엇을 들고 있고 무엇을 들고 있지 않은가
 *
 * 들고 있는 것은 **차례와 이름표**뿐이다 — 어떤 엔트리가 몇 번째 장인지, 그 엔트리의
 * MIME 이 무엇인지. **본문은 들고 있지 않는다.** 화면이 보고 있는 장 하나만 그때그때
 * 읽는다. 300쪽짜리 책의 XHTML 을 전부 메모리에 펴 두면 그것만으로 수십 MB이고,
 * 9단계가 만화 쪽을 힙에만 두기로 하면서 '살아 있는 양' 을 예산으로 묶은 것과 같은 문제다.
 *
 * ## 이름이 아니라 인덱스로 찾는다
 *
 * ZIP 명세는 같은 이름의 엔트리를 금지하지 않는다(`ArchiveEntry` 의 주석). 그래서 이름
 * → 인덱스 표를 **한 번** 만들고, 같은 이름이 둘이면 **먼저 나온 것**을 쓴다. 그 사실을
 * 경고로 남긴다 — 조용히 고르면 사용자가 보는 것이 왜 그 그림인지 아무도 모른다.
 *
 * ## 본문은 위생기를 지나서만 나간다
 *
 * [chapterHtml] 말고 본문을 꺼내는 길이 없다. 자원([openResource])은 바이트 그대로
 * 나가지만 그쪽은 그림·글꼴이고, XHTML·CSS 는 반드시 [HtmlSanitizer] 를 지난다.
 */
class EpubBook internal constructor(
    private val reader: ArchiveReader,
    private val pkg: EpubPackage.Package,
    /** ZIP 이름 → 엔트리. 같은 이름이 둘이면 먼저 나온 것이다. */
    private val byPath: Map<String, ArchiveEntry>,
    override val warnings: List<ParseWarning>,
    private val limits: ParseLimits,
    /** 난독화를 풀 글꼴(ZIP 이름 → 열쇠). [openResource] 가 여기 있는 것을 풀며 준다. */
    private val masks: Map<String, FontObfuscation.Mask> = emptyMap(),
) : OpenedDocument {

    /** 읽는 차례의 한 장. */
    data class Chapter(val path: String, val mediaType: String)

    override val formatId: FormatId = FormatId.EPUB

    /**
     * 버린 것의 집계. **장을 읽을 때마다 는다.**
     *
     * 열자마자 0 인 것이 정직한 상태다 — 본문을 아직 읽지 않았으니 무엇을 버렸는지도
     * 아직 모른다. PDF 가 '무엇을 못 그렸는지 알 수 없어' 언제나 0 인 것과 다르다.
     */
    override val unsupported: UnsupportedFeatures = UnsupportedFeatures()

    val title: String get() = pkg.title

    val language: String? get() = pkg.language

    /** 읽는 차례. `linear="no"` 는 빠져 있다. */
    val spine: List<Chapter>

    /** 목차. 없으면 비어 있다. */
    val toc: List<Toc>

    /** 목차 한 줄. [spineIndex] 가 -1 이면 차례 밖을 가리킨다. */
    data class Toc(val title: String, val path: String, val depth: Int, val spineIndex: Int)

    private val closed = AtomicBoolean(false)

    /** 눈에 보이지 않는 글자라 소스에 그대로 적지 않는다. */
    private val BOM = '﻿'

    init {
        val byId = pkg.items.associateBy { it.id }
        spine = pkg.spine.mapNotNull { id ->
            val item = byId[id] ?: return@mapNotNull null
            if (item.path !in byPath) return@mapNotNull null
            Chapter(item.path, item.mediaType)
        }
        val order = spine.withIndex().associate { (i, c) -> c.path to i }
        toc = readToc(byId).map { Toc(it.title, it.href, it.depth, order[it.href] ?: -1) }
    }

    /** 이 이름의 자원이 책 안에 있는가. 위생기의 리졸버가 묻는다. */
    fun has(path: String): Boolean = path in byPath

    /**
     * 자원 하나를 읽는 스트림. 없으면 null. **부르는 쪽이 닫는다.**
     *
     * 난독화된 글꼴이면 **풀린 바이트**를 준다([FontObfuscation]) — 부르는 쪽(WebView 에
     * 자원을 내주는 화면)은 난독화를 알 필요가 없다.
     */
    fun openResource(path: String): InputStream? {
        if (closed.get()) return null
        val entry = byPath[path] ?: return null
        if (!entry.isReadable) return null
        val stream = reader.open(entry)
        val mask = masks[path] ?: return stream
        return FontObfuscation.wrap(stream, mask)
    }

    /** 이 자원의 MIME. 매니페스트가 말한 값이고, 없으면 null. */
    fun mediaTypeOf(path: String): String? =
        pkg.items.firstOrNull { it.path == path }?.mediaType?.takeIf { it.isNotBlank() }

    /**
     * 장 하나를 **위생을 거친 HTML** 로.
     *
     * 상대 주소는 **그대로 남긴다.** 우리 쪽에서 절대 주소로 바꾸지 않는 이유는, 화면이
     * 장을 `https://<우리 호스트>/<ZIP 이름>` 으로 띄우므로 WebView 가 상대 주소를 그
     * 기준으로 알아서 푼다는 것이다. 우리가 한 번 더 풀면 **두 벌의 경로 계산**이 생기고,
     * 둘이 어긋나는 날 그림이 조용히 사라진다.
     *
     * 대신 위생기에게 **'이 주소가 책 안에 있는가' 를 묻게** 한다. 없는 것을 가리키는
     * 주소는 지워지므로, WebView 가 우리 호스트에 없는 것을 요청하는 일 자체가 줄어든다.
     */
    fun chapterHtml(index: Int, extraCss: String = ""): String? {
        val chapter = spine.getOrNull(index) ?: return null
        // **차례에 글이 아닌 것이 올 수 있다.** 만화형 EPUB 은 JPEG 를 곧바로 차례에 넣는다(IDPF 의
        // haruko-jpeg·page-blanche-bitmaps-in-spine). 그것을 글로 읽으면 JPEG 바이트가 UTF-8 로 풀려
        // 뜻 없는 글자와 가짜 태그가 화면을 채웠다(실세계 말뭉치가 잡았다).
        val type = contentTypeOf(chapter)
        if (isBinary(type)) return binaryChapter(chapter, type, extraCss)
        val raw = readText(chapter.path) ?: return null
        val base = EpubHref.dirOf(chapter.path)
        val result = HtmlSanitizer.sanitize(
            raw,
            { href -> if (isInBook(base, href)) href else null },
            limits,
        )
        for ((kind, n) in result.dropped.snapshot()) unsupported.record(kind, n)
        // **읽기용 껍데기를 여기서 씌운다.** 화면이 `format:html` 을 직접 보게 하면
        // `feature:*` 이 포맷 모듈을 둘 보게 되어 의존 표를 어긴다. 껍데기는 어차피
        // 12·13단계의 문서와도 같은 것이라 포맷 쪽에 두는 편이 맞다.
        // 장이 언어를 적지 않았으면 책의 `dc:language` 를 쓴다 — 줄 나눔 규칙(`keep-all`)이 언어를 보고, 한자의 자형도
        // 언어를 따른다(적지 않으면 기기의 언어로 그린다).
        return HtmlShell.wrap(result.html, extraCss, pkg.language)
    }

    /**
     * 스타일시트 하나를 **위생을 거쳐** 돌려준다.
     *
     * 장 안의 `<style>` 은 [chapterHtml] 이 이미 보지만, `<link rel="stylesheet">` 로
     * 걸린 **파일**은 화면이 따로 가져간다. 그 길에도 같은 위생을 걸지 않으면 책의
     * CSS 한 줄이 바깥을 가리킬 수 있다 — 위생의 구멍은 한 군데면 충분히 뚫린다.
     *
     * @return CSS 가 아니거나 없으면 null.
     */
    fun styleSheet(path: String): String? {
        val css = readText(path) ?: return null
        val base = EpubHref.dirOf(path)
        val result = HtmlSanitizer.sanitizeCss(
            css,
            { href -> if (isInBook(base, href)) href else null },
            limits,
        )
        for ((kind, n) in result.dropped.snapshot()) unsupported.record(kind, n)
        return result.html
    }

    /**
     * 장의 주소(`<a href="ch2.xhtml">`)가 차례의 몇 번째인가.
     *
     * 화면이 링크를 가로채 장을 넘길 때 쓴다. 차례 밖이면 -1 이다.
     */
    fun spineIndexOf(path: String): Int = spine.indexOfFirst { it.path == path }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        reader.close()
    }

    // ---- 안쪽 -------------------------------------------------------------------

    /**
     * 차례에 올라온 그림·소리 같은 **글이 아닌 것**.
     *
     * 그림이면 그 그림 하나를 보여 주는 쪽을 만든다. 명세(EPUB 3.3)는 읽는 쪽이 그 형식을 그릴 수 없을
     * 때만 대체본(`fallback`)을 쓰라고 한다 — WebView 는 JPEG·PNG 를 그린다. 그리고 만화형 책의 대체본은
     * 대개 '이 기기는 그림 차례를 못 그린다' 는 안내나 줄거리 한 장이라 열세 쪽이 모두 같은 글이 된다.
     *
     * **그림을 `data:` 로 싣는다.** 장의 주소가 곧 그 그림 파일의 주소라 `<img src>` 로 가리키면 화면이
     * 같은 주소를 다시 청하고, 화면 쪽 공급기는 차례에 있는 주소를 **장**(HTML)으로 내준다. `data:image/…`
     * 는 위생기가 원래 남기는 모양이다(`Urls`) — 우리가 만든 쪽도 위생기를 지난다.
     *
     * 그릴 수 없는 형식(소리·영상·글꼴, 모르는 그림)이면 빈 쪽을 내고 **버린 것으로 센다** — 뜻 없는
     * 글자를 보여 주는 것보다 '빠진 것이 있다' 고 말하는 편이 정직하다.
     */
    private fun binaryChapter(chapter: Chapter, mediaType: String, extraCss: String): String? {
        val type = IMAGE_TYPES[mediaType]
        if (type == null) {
            unsupported.record(
                if (mediaType.startsWith("image/")) UnsupportedFeatures.UNSUPPORTED_IMAGE
                else UnsupportedFeatures.UNKNOWN_ELEMENT
            )
            return HtmlShell.wrap("", extraCss)
        }
        val stream = openResource(chapter.path) ?: return null
        val bytes = stream.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                // 선언된 크기를 믿지 않고 실제로 읽은 바이트를 센다(저장소 규칙).
                if (total > MAX_SPINE_IMAGE_BYTES) {
                    unsupported.record(UnsupportedFeatures.UNSUPPORTED_IMAGE)
                    return HtmlShell.wrap("", extraCss)
                }
                out.write(buffer, 0, n)
            }
            out.toByteArray()
        }
        val html = "<div style=\"text-align:center\"><img alt=\"\" src=\"data:$type;base64," +
            java.util.Base64.getEncoder().encodeToString(bytes) + "\"/></div>"
        val result = HtmlSanitizer.sanitize(html, { null }, limits)
        for ((kind, n) in result.dropped.snapshot()) unsupported.record(kind, n)
        return HtmlShell.wrap(result.html, extraCss)
    }

    /**
     * 차례 항목을 무엇으로 읽을까. 매니페스트의 `media-type` 을 따르되, **`application/octet-stream`
     * ('모른다')·빈 값만 앞 바이트를 보고 가른다.**
     *
     * 형식을 모른다고 적은 항목을 통째로 '글이 아닌 것' 으로 보면, 형식만 잘못 적은 XHTML 장이 빈 쪽이 된다
     * — 그림을 차례에 두는 책을 고치기 전(글로 읽었다)에는 보이던 장이다. 그림 서명이면 그림으로, `<` 로
     * 시작하면 글로 읽는다. 어느 쪽도 아니면 적힌 대로 둔다(`octet-stream` 은 빈 쪽, 빈 값은 예전처럼 글).
     */
    private fun contentTypeOf(chapter: Chapter): String {
        val declared = chapter.mediaType
        if (declared.isNotEmpty() && declared != "application/octet-stream") return declared
        val head = openResource(chapter.path)?.use { input ->
            val buf = ByteArray(SNIFF_BYTES)
            var n = 0
            while (n < buf.size) {
                val k = input.read(buf, n, buf.size - n)
                if (k < 0) break
                n += k
            }
            buf.copyOf(n)
        } ?: return declared
        return sniff(head) ?: declared
    }

    private fun isInBook(base: String, href: String): Boolean {
        val path = EpubHref.resolve(base, href) ?: return false
        return path in byPath
    }

    private fun readText(path: String): String? {
        val stream = openResource(path) ?: return null
        val bytes = stream.use { it.readBytes() }
        // **XHTML 의 인코딩은 UTF-8 이 명세의 기본값이다.** BOM 이 있으면 떼고,
        // 아니면 UTF-8 로 읽는다 — 다른 인코딩을 쓴 EPUB 은 명세 위반이고, 그때는
        // 대체 문자가 뜨더라도 그 사실이 보이는 편이 낫다.
        val text = bytes.toString(Charsets.UTF_8)
        return if (text.isNotEmpty() && text[0] == BOM) text.substring(1) else text
    }

    private fun readToc(byId: Map<String, EpubPackage.Item>): List<EpubToc.Entry> {
        // EPUB3 의 nav 를 먼저 본다. 둘 다 있는 책에서 nav 가 더 정확하다
        // (ncx 는 하위 호환용으로 남겨 두기만 하는 경우가 많다).
        val nav = pkg.items.firstOrNull { it.properties.split(' ').contains("nav") }
        if (nav != null) {
            runToc(nav.path) { stream, base -> EpubToc.fromNav(stream, base, limits) }
                ?.let { if (it.isNotEmpty()) return it }
        }
        val ncx = pkg.ncxId?.let { byId[it] }
            ?: pkg.items.firstOrNull { it.mediaType == "application/x-dtbncx+xml" }
        if (ncx != null) {
            runToc(ncx.path) { stream, base -> EpubToc.fromNcx(stream, base, limits) }
                ?.let { return it }
        }
        return emptyList()
    }

    /**
     * 목차 하나를 읽되 **실패하면 목차 없이 간다.**
     *
     * 목차가 깨진 것은 책을 못 여는 이유가 아니다. 다만 취소는 삼키지 않는다 —
     * `runCatching` 을 쓰지 않고 종류를 적어 잡는 이유가 그것이다(저장소 규칙).
     */
    private inline fun runToc(
        path: String,
        read: (InputStream, String) -> List<EpubToc.Entry>,
    ): List<EpubToc.Entry>? = try {
        openResource(path)?.use { read(it, EpubHref.dirOf(path)) }
    } catch (e: java.util.concurrent.CancellationException) {
        throw e
    } catch (t: Throwable) {
        null
    }

    private companion object {
        /**
         * 차례에 올라와도 그대로 그리는 그림 → `data:` 에 적을 MIME. **표에 있는 값만 쓴다** — 매니페스트의
         * `media-type` 은 책이 적은 글자라 그대로 이어 붙이면 속성을 빠져나갈 수 있다.
         * SVG 는 여기 없다 — 글(XML)로 읽어 본문에 넣는 지금 길이 그 안의 그림·글자까지 살린다.
         */
        val IMAGE_TYPES = mapOf(
            "image/jpeg" to "image/jpeg",
            "image/jpg" to "image/jpeg",
            "image/png" to "image/png",
            "image/gif" to "image/gif",
            "image/webp" to "image/webp",
        )

        /**
         * 차례의 그림 하나를 이만큼까지 싣는다. `data:` 로 실으면 한 벌이 여러 벌이 된다 — 바이트, `base64`
         * 글자(1.33배, UTF-16 이라 다시 2배), 위생기의 결과, 껍데기, 화면이 보내는 UTF-8. 8 MiB 면 잠깐 동안
         * 100 MB 안쪽이다. 만화 한 쪽의 JPEG 는 대개 1 MB 아래다.
         */
        const val MAX_SPINE_IMAGE_BYTES = 8L * 1024 * 1024

        /** 형식을 모르는 차례 항목에서 앞을 이만큼 본다. 글이면 `<` 앞에 빈 줄이 여럿 올 수 있다. */
        const val SNIFF_BYTES = 512

        /**
         * 앞 바이트로 형식을 가른다. 모르면 null. 그림은 [IMAGE_TYPES] 의 넷만 서명으로 알아본다 — 그 밖의
         * 그림은 어차피 그리지 않는다.
         */
        fun sniff(b: ByteArray): String? {
            fun at(i: Int, vararg sig: Int) =
                b.size >= i + sig.size && sig.indices.all { (b[i + it].toInt() and 0xFF) == sig[it] }
            return when {
                at(0, 0xFF, 0xD8, 0xFF) -> "image/jpeg"
                at(0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> "image/png"
                at(0, 0x47, 0x49, 0x46, 0x38) -> "image/gif"
                at(0, 0x52, 0x49, 0x46, 0x46) && at(8, 0x57, 0x45, 0x42, 0x50) -> "image/webp"
                else -> {
                    // UTF-8 BOM 과 XML 의 공백 넷을 건너뛴 첫 글자가 `<` 면 글이다.
                    var i = if (at(0, 0xEF, 0xBB, 0xBF)) 3 else 0
                    while (i < b.size && b[i].toInt().toChar() in " \t\r\n") i++
                    if (i < b.size && b[i] == '<'.code.toByte()) "application/xhtml+xml" else null
                }
            }
        }

        /** 글로 읽으면 안 되는 차례 항목인가. MIME 이 비었거나 모르는 글 형식이면 예전처럼 글로 읽는다. */
        fun isBinary(mediaType: String): Boolean =
            (mediaType.startsWith("image/") && mediaType != "image/svg+xml") ||
                mediaType.startsWith("audio/") || mediaType.startsWith("video/") ||
                mediaType.startsWith("font/") || mediaType == "application/octet-stream" ||
                mediaType == "application/pdf" || mediaType == "application/vnd.ms-opentype" ||
                mediaType.startsWith("application/font-") || mediaType.startsWith("application/x-font")
    }
}
