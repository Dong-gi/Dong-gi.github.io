package io.github.donggi.iroiroviewer.format.html

import io.github.donggi.iroiroviewer.format.FlowDocument
import io.github.donggi.iroiroviewer.format.FlowWarnings
import io.github.donggi.iroiroviewer.format.FormatId
import io.github.donggi.iroiroviewer.format.ParseWarning
import io.github.donggi.iroiroviewer.format.UnsupportedFeatures
import io.github.donggi.iroiroviewer.safety.ParseLimitExceededException
import io.github.donggi.iroiroviewer.safety.ParseLimits
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * 흐름 문서(docx·xlsx·pptx — 12단계, HWPX·HWP 5.0 — 13단계)가 함께 서는 바탕. 변환기는 **부분 하나의
 * 본문**만 쓰면 된다([renderBody]). 그림을 꺼내 오는 꾸러미는 [FlowPackage] 하나로 추렸다 — OOXML 의 OPC,
 * HWPX 의 OWPML 매니페스트, HWP 의 CFB 가 각자 구현한다(12단계에는 `format:opc` 의 `OpcFlowDocument` 였고,
 * 13단계가 여기로 옮겼다).
 *
 * ## 여기서 한 번에 지키는 것
 *
 * 변환기가 각자 지키면 한 곳이 빠뜨리는 날이 온다. 그래서 여기 모았다.
 *
 * * **잠금.** [partHtml]·[openResource] 는 WebView 의 다른 스레드에서 온다. 꾸러미(ZIP·CFB 리더)는
 *   스레드 안전하지 않으므로 모든 접근을 이 객체의 잠금으로 세운다. 변환기가 여는 동안 꾸러미를
 *   읽을 때도 [locked] 안에서 읽는다.
 * * **닫힌 뒤.** null 을 준다. 예외를 던지지 않는다 — 화면이 사라지는 중이다.
 * * **위생.** 본문은 `FlowHtml.finish` 를 지난다([FlowDocument] 의 주석).
 * * **자원.** 화면이 그릴 수 있는 그림만 내준다([isDisplayableImage]). EMF·WMF 는 WebView 가
 *   못 그리고, 내주면 깨진 그림 아이콘만 뜬다 — 변환기가 애초에 `img` 를 쓰지 않고 센다.
 * * **부분 하나의 실패는 부분 하나로 끝난다.** 깨진 시트 하나 때문에 통합 문서를 못 여는 일은
 *   없어야 한다. 그 부분 자리에 안내 한 줄을 두고 [FlowWarnings.PART_FAILED] 를 남긴다.
 *   취소와 스레드 인터럽트만 위로 올린다.
 * * **캐시.** 최근 부분 셋의 HTML 만 든다. 시트 하나가 수 MB 일 수 있다.
 */
abstract class FlowDocumentBase<P : FlowPackage>(
    protected val pkg: P,
    protected val limits: ParseLimits,
    override val formatId: FormatId,
) : FlowDocument {

    override val unsupported: UnsupportedFeatures = UnsupportedFeatures()

    private val warningList = ArrayList<ParseWarning>()

    /**
     * 패키지의 경고와 이 문서의 경고. **읽을 때마다 합친다** — 관계 파일은 그리는 동안 처음 읽히므로
     * (그림을 찾을 때) 만들 때 한 번 베껴 두면 그 뒤에 깨진 관계가 화면에 닿지 않는다. 패키지는
     * 이 잠금 안에서만 만지므로 여기서 읽어도 안전하다.
     */
    override val warnings: List<ParseWarning> get() = synchronized(lock) { (pkg.warnings + warningList).distinct() }

    /** 모든 패키지 접근을 세우는 잠금. */
    protected val lock = Any()

    @Volatile
    private var closed = false

    /**
     * 닫기가 **청해졌다**(잠금을 잡기 전에 선다). 화면이 떠나는 동안 WebView 가 줄 세워 둔 부분 요청이
     * 하나씩 잠금을 잡고 끝까지 그리면 닫기가 그 뒤에 줄을 선다 — 이 깃발을 보고 그리기 전에 돌아선다.
     */
    @Volatile
    private var closeRequested = false

    init {
        // 매크로는 **돌리지 않는다**(읽기 전용이다). 들어 있다는 사실만 알린다 — 사용자가
        // 이 앱에서 본 것과 오피스에서 본 것이 다를 수 있다는 뜻이기 때문이다.
        if (pkg.hasMacros) {
            unsupported.record(UnsupportedFeatures.MACRO)
            warn(FlowWarnings.MACROS)
        }
    }

    private val cache = object : LinkedHashMap<Int, String>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, String>?): Boolean = size > CACHE_PARTS
    }

    /** 잠금 안에서 돈다. 여는 동안 변환기가 패키지를 읽을 때 쓴다. */
    protected inline fun <T> locked(block: () -> T): T = synchronized(lock) { block() }

    /** 경고를 남긴다. 같은 코드·내용은 한 번만. */
    protected fun warn(code: String, detail: String = "") {
        synchronized(lock) {
            val w = ParseWarning(code, detail)
            if (w !in warningList) warningList.add(w)
        }
    }

    /**
     * 부분 하나의 **`<body>` 안쪽 HTML** 을 쓴다. 잠금 안에서 불린다. `HtmlWriter` 로 쓰고,
     * 상한에 닿았으면(`HtmlWriter.full`) [warn] 으로 [FlowWarnings.TRUNCATED] 를 남긴다.
     *
     * 던져도 된다 — 부분 하나의 실패로 처리된다(위 주석).
     */
    protected abstract fun renderBody(index: Int): String

    /** 부분마다 얹는 CSS. 상수여야 한다(문서의 값을 섞지 않는다). */
    protected abstract val css: String

    /** 부분의 가상 경로(`~part-3.html`). 폴더를 넣지 않는다([FlowPart] 의 주석). */
    protected fun partPath(index: Int): String = "$PART_PREFIX$index.html"

    /**
     * 경고의 `detail` 에 싣는 부분 이름. 이름이 없으면(제목 없는 슬라이드·제목 없는 조각) **번호**를
     * 싣는다(1부터, [FlowWarnings.PART_NUMBER_PREFIX] 를 붙여) — 빈 문자열이면 서로 다른 부분의 경고가
     * 하나로 합쳐지고, 화면은 어느 부분인지 말할 수 없다. 앞머리가 있어야 화면이 '몇 번째 부분' 으로 읽는다
     * (숫자만 보고 읽으면 '2024' 라는 시트가 번호가 된다). 변환기도 부분 하나의 경고에는
     * 이것을 쓴다. 문서 전체의 경고(시트 수 상한 등)는 빈 문자열로 둔다.
     */
    protected fun partName(index: Int): String =
        parts[index].label.ifBlank { FlowWarnings.PART_NUMBER_PREFIX + (index + 1) }

    /** 위생기가 버린 것을 이미 센 부분. 캐시에서 밀려나 다시 그려도 배지의 수가 늘지 않는다. */
    private val countedParts = java.util.BitSet()

    final override fun partHtml(index: Int): String? {
        if (closeRequested) return null
        return synchronized(lock) { partHtmlLocked(index) }
    }

    private fun partHtmlLocked(index: Int): String? {
        if (closed || closeRequested || index !in parts.indices) return null
        cache[index]?.let { return it }
        val body = try {
            renderBody(index)
        } catch (e: java.util.concurrent.CancellationException) {
            throw e
        } catch (e: java.io.InterruptedIOException) {
            throw e
        } catch (e: ParseLimitExceededException) {
            warn(FlowWarnings.TRUNCATED, partName(index))
            failedBody()
        } catch (t: Exception) {
            warn(FlowWarnings.PART_FAILED, partName(index))
            failedBody()
        }
        val dropped = UnsupportedFeatures()
        // 위생기의 입력 상한(`maxXmlBytes`)을 넘는 본문은 여기서 던진다. 그대로 두면 화면의 자원
        // 제공자가 잡아 404 가 되어 **빈 화면에 아무 말도 없다**. 부분 하나의 '너무 크다' 로 끝낸다.
        val html = try {
            FlowHtml.finish(body, css, resolver, limits, dropped)
        } catch (e: ParseLimitExceededException) {
            warn(FlowWarnings.TRUNCATED, partName(index))
            FlowHtml.finish(failedBody(), css, resolver, limits, UnsupportedFeatures())
        }
        if (!countedParts[index]) {
            countedParts.set(index)
            for ((kind, n) in dropped.snapshot()) unsupported.record(kind, n)
        }
        cache[index] = html
        return html
    }

    /**
     * 부분을 그리지 못했을 때의 본문. **문장은 화면 몫이지만** 빈 화면보다는 표시가 낫다 — 기호 하나만 둔다.
     * 색을 **스스로 든다** — 포맷의 바탕색(슬라이드의 어두운 회색)에 따라 보이지 않는 일이 있었다.
     */
    private fun failedBody(): String =
        "<p class=\"part-failed\" style=\"color:#9e9e9e;font-size:2em;text-align:center;margin:2em 0\">&#9888;</p>"

    final override fun openResource(path: String): InputStream? = synchronized(lock) {
        if (closed) return null
        val name = pkg.canonical(path) ?: return null
        if (!isDisplayableImage(name)) return null
        val bytes = pkg.readBytes(name, MAX_RESOURCE_BYTES) ?: return null
        ByteArrayInputStream(bytes)
    }

    final override fun mediaTypeOf(path: String): String? = synchronized(lock) {
        if (closed) null else pkg.contentType(path)?.let { imageTypeOf(it) }
    }

    /**
     * 화면이 그릴 수 있는 그림인가. 콘텐츠 형식을 먼저 보고, 없으면 확장자를 본다.
     * 변환기는 `img` 를 쓰기 전에 이것을 묻고, 거짓이면 [UnsupportedFeatures.UNSUPPORTED_IMAGE] 를 센다.
     */
    fun isDisplayableImage(partName: String): Boolean {
        val type = pkg.contentType(partName)?.lowercase()
        if (type != null) return imageTypeOf(type) != null
        return partName.substringAfterLast('.', "").lowercase() in DISPLAYABLE_EXTENSIONS
    }

    /**
     * 닫는다. **잠금을 기다리므로 주 스레드에서 부르지 마라** — 큰 부분을 그리는 중이면 그 끝을 기다린다
     * (`DocViewModel` 이 IO 에서 닫는다). [closeRequested] 가 줄 선 요청을 먼저 돌려세운다.
     */
    override fun close() {
        closeRequested = true
        synchronized(lock) {
            if (closed) return
            closed = true
            cache.clear()
            pkg.close()
        }
    }

    /**
     * 위생기의 리졸버. 본문 안의 상대 주소 가운데 **패키지에 있는 그림**과 **다른 부분의 가상
     * 경로**만 내준다. 같은 부분 안의 조각(`#id`)은 위생기가 리졸버에 묻지 않고 그대로 둔다.
     *
     * 그림은 [FlowUrls.encode] 의 모양으로 **다시 적는다** — 변환기가 인코딩을 빠뜨려도
     * 여기서 한 모양으로 모인다.
     */
    private val resolver = HtmlSanitizer.Resolver { url ->
        val path = url.substringBefore('#')
        when {
            path.startsWith(PART_PREFIX) && parts.any { it.path == path } -> url
            pkg.has(path) && isDisplayableImage(path) -> pkg.canonical(path)?.let { FlowUrls.encode(it) }
            else -> null
        }
    }

    private fun imageTypeOf(contentType: String): String? {
        val t = contentType.lowercase().substringBefore(';').trim()
        return if (t in DISPLAYABLE_TYPES) t else null
    }

    companion object {
        /** 부분 가상 경로의 앞머리. 꾸러미의 이름과 겹치지 않게 물결표로 연다. */
        const val PART_PREFIX = "~part-"

        /** 그림 하나의 상한. EPUB 과 같다. */
        const val MAX_RESOURCE_BYTES = 32L * 1024 * 1024

        private const val CACHE_PARTS = 3

        private val DISPLAYABLE_TYPES = setOf(
            "image/png", "image/jpeg", "image/jpg", "image/gif", "image/webp", "image/bmp", "image/svg+xml",
        )

        private val DISPLAYABLE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "svg")
    }
}
