package io.github.donggi.iroiroviewer.format.pptx

import io.github.donggi.iroiroviewer.format.FlowDocument
import io.github.donggi.iroiroviewer.format.FlowKind
import io.github.donggi.iroiroviewer.format.FlowOutline
import io.github.donggi.iroiroviewer.format.FlowPart
import io.github.donggi.iroiroviewer.format.FlowWarnings
import io.github.donggi.iroiroviewer.format.FormatId
import io.github.donggi.iroiroviewer.format.ProgressSink
import io.github.donggi.iroiroviewer.format.UnsupportedFeatures
import io.github.donggi.iroiroviewer.format.html.CssValues
import io.github.donggi.iroiroviewer.format.opc.OpcFlowDocument
import io.github.donggi.iroiroviewer.format.opc.OpcNames
import io.github.donggi.iroiroviewer.format.opc.OpcPackage
import io.github.donggi.iroiroviewer.format.opc.OpcRelationship
import io.github.donggi.iroiroviewer.safety.ParseLimits
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.xmlpull.v1.XmlPullParser
import java.io.InterruptedIOException
import kotlin.coroutines.CoroutineContext

/**
 * 프레젠테이션(pptx) 을 흐름 문서로 여는 입구. `app/FormatRegistry` 가 `OoxmlOpener` 에 이어 준다.
 */
object PptxDocument {

    /**
     * 패키지를 흐름 문서로. **패키지의 주인이 돌려준 문서로 넘어간다** — 문서를 닫으면 패키지도
     * 닫힌다. 던지면 여는이가 패키지를 닫고 `toOpenFailure` 로 옮긴다.
     */
    suspend fun open(pkg: OpcPackage, limits: ParseLimits, progress: ProgressSink): FlowDocument {
        // 잠금(`synchronized`) 안에서는 정지 함수를 부를 수 없다. 문맥을 먼저 잡아 두고 잠금 안에서는
        // 정지하지 않는 `ensureActive` 만 부른다.
        val context = currentCoroutineContext()
        val doc = PptxFlowDocument(pkg, limits)
        doc.load(context, progress)
        return doc
    }
}

/**
 * 슬라이드 한 장이 부분 하나다.
 *
 * ## 여는 동안 하는 일
 *
 * `presentation.xml` 에서 슬라이드 크기와 **순서**(`sldIdLst`)를 읽고, 슬라이드마다 한 번 훑어
 * 제목(목차)과 애니메이션 여부를 얻는다(`PptxScan`). 도형을 읽고 그리는 것은 화면이 그 슬라이드를
 * 청할 때다(`renderBody`).
 *
 * ## 숨긴 슬라이드
 *
 * **넣는다.** 슬라이드 쇼에서 건너뛸 뿐 문서의 내용이다 — 읽는 사람은 그것을 보려고 파일을 열었을
 * 수 있다.
 *
 * ## 캐시
 *
 * 슬라이드 수십 장이 레이아웃 몇 개와 마스터 하나를 함께 쓴다. 레이아웃·마스터·테마는 읽은 것을
 * 들고 있는다(개수 상한이 있는 LRU). 슬라이드 자체의 HTML 캐시는 바탕(`OpcFlowDocument`)의 것이다.
 */
internal class PptxFlowDocument(
    pkg: OpcPackage,
    limits: ParseLimits,
) : OpcFlowDocument(pkg, limits, FormatId.PPTX) {

    @Volatile
    private var docTitle = ""

    @Volatile
    private var partList: List<FlowPart> = emptyList()

    @Volatile
    private var outlineList: List<FlowOutline> = emptyList()

    private val slideNames = ArrayList<String>()
    private val slideIndex = HashMap<String, Int>()
    private var slideCx = DEFAULT_CX
    private var slideCy = DEFAULT_CY
    private var defaultTextStyle: ListStyle? = null
    private var firstSlideNum = 1

    private val layouts = Lru<Cached<PartModel>>(MAX_CACHED_LAYOUTS)
    private val masters = Lru<Cached<PartModel>>(MAX_CACHED_MASTERS)
    private val themes = Lru<Cached<Theme>>(MAX_CACHED_THEMES)

    /** 버린 것을 이미 옮겨 적은 부분들 — 마스터의 것은 슬라이드마다 그려져도 한 번만 센다. */
    private val committed = HashSet<String>()

    /**
     * 부분의 관계를 id 로 찾는 표(부분 이름 → id → 관계). `OpcPackage.relationship` 은 목록을 처음부터
     * 훑는데, 관계 파일 하나에 5만 개까지 들어오고 조각마다 링크를 걸 수 있어 한 장을 그리는 데
     * 조각 수 × 관계 수만큼 비교한다(2 만 조각 × 5 만 관계가 데스크톱에서 5 초였다). 그리는 동안
     * 보는 부분은 슬라이드·레이아웃·마스터·SmartArt 그림 넷 남짓이라 몇 개만 든다.
     */
    private val relIndex = Lru<Map<String, OpcRelationship>>(MAX_REL_INDEX)

    /**
     * 이번 그리기에서 읽은 SmartArt 그림(원 부분 + 데이터 부분 → 그림). [renderBody] 마다 비운다.
     * 그래픽 틀 수천 개가 같은 데이터 부분을 가리키면 틀마다 두 부분을 다시 읽었다(500 틀에 9.5 초).
     */
    private val diagrams = HashMap<String, Cached<PartModel>>()

    /** 이번 그리기에서 SmartArt 때문에 실제로 부분을 읽은 횟수. [MAX_DIAGRAM_READS] 에서 멈춘다. */
    private var diagramReads = 0

    override val title: String get() = docTitle
    override val kind: FlowKind = FlowKind.SLIDES
    override val parts: List<FlowPart> get() = partList
    override val outline: List<FlowOutline> get() = outlineList
    /** 부분마다 얹는 CSS. 슬라이드 크기(숫자)로 만든 맞춤 규칙이 붙는다 — 문서의 글자는 섞이지 않는다. */
    override val css: String get() = CSS + fitCss(slideCx, slideCy)

    /** 슬라이드 폭(EMU). 시험이 높이 계산을 확인한다. */
    internal val width: Long get() = slideCx
    internal val height: Long get() = slideCy

    /** 캐시에 든 레이아웃·마스터 수. 시험이 무거운 부분을 붙들지 않는지 확인한다. */
    internal val cachedPartCount: Int get() = locked { layouts.size + masters.size }

    fun load(context: CoroutineContext, progress: ProgressSink) {
        val main = locked { pkg.mainDocument ?: pkg.canonical("ppt/presentation.xml") } ?: return
        // 프레젠테이션 부분이 깨졌으면 문서 전체를 읽을 수 없다 — 던진다(여는이가 '깨진 파일' 로 옮긴다).
        val pres = locked { parse(main) { DrawingParser(it, limits).parsePresentation() } } ?: return
        applySize(pres.cx, pres.cy)
        defaultTextStyle = pres.defaultTextStyle
        firstSlideNum = pres.firstSlideNum

        var overflow = pres.overflow
        val names = ArrayList<String>()
        for (id in pres.slideRelIds) {
            context.ensureActive()
            if (names.size >= MAX_SLIDES) {
                overflow = true
                break
            }
            val name = locked {
                relationshipById(main, id)
                    ?.takeIf { !it.external && it.typeName == "slide" }
                    ?.let { pkg.canonical(it.target) }
            } ?: continue
            names.add(name)
        }

        val parts = ArrayList<FlowPart>(names.size)
        val outline = ArrayList<FlowOutline>(names.size)
        val commentParts = HashSet<String>()
        for ((i, name) in names.withIndex()) {
            context.ensureActive()
            val scan = locked { scanSlide(name) }
            val comments = locked { countComments(name, commentParts) }
            // 애니메이션·전환은 **슬라이드마다 한 번** 센다(효과 하나하나가 아니라).
            if (scan.animated) unsupported.record(UnsupportedFeatures.ANIMATION)
            unsupported.record(UnsupportedFeatures.COMMENT, comments)
            slideIndex.putIfAbsent(OpcNames.key(name), i)
            parts.add(FlowPart(partPath(i), scan.title))
            outline.add(FlowOutline(scan.title, 0, i, null))
        }
        locked {
            slideNames.addAll(names)
            partList = parts
            outlineList = outline
        }
        docTitle = locked { coreTitle() }
        // 상한을 넘은 슬라이드는 보여 주지 않는다. 부분 이름이 아니라 문서 전체의 일이라 detail 을 비운다.
        if (overflow) warn(FlowWarnings.TRUNCATED)
        progress.report(2, 3)
    }

    override fun renderBody(index: Int): String {
        diagrams.clear()
        diagramReads = 0
        try {
            return renderSlide(index)
        } finally {
            // 그림을 다음 그리기까지 붙들지 않는다.
            diagrams.clear()
        }
    }

    private fun renderSlide(index: Int): String {
        val name = slideNames[index]
        // 슬라이드가 없거나 깨졌으면 던진다 — 바탕이 그 부분만 실패로 처리한다.
        val slide = parse(name) { DrawingParser(it, limits).parsePart(name) }
            ?: throw IllegalStateException("슬라이드 부분이 없다")
        val layoutName = relatedPart(name, "slideLayout", listOf(name))
        val layout = layoutName?.let { cachedPart(layouts, it) }
        val masterName = layoutName?.let { relatedPart(it, "slideMaster", listOf(name, it)) }
        val master = masterName?.let { cachedPart(masters, it) }
        val themeName = masterName?.let { relatedPart(it, "theme", listOf(name, layoutName, it)) }
        val theme = themeName?.let { cachedTheme(it) }

        val renderer = SlideRenderer(host, slideCx, slideCy, slide, layout, master, theme, defaultTextStyle, slideNumber = firstSlideNum + index)
        val html = renderer.render()
        if (renderer.truncated) warn(FlowWarnings.TRUNCATED, partName(index))
        for ((part, counts) in renderer.counts) {
            if (committed.add(OpcNames.key(part))) {
                for ((kind, n) in counts.snapshot()) unsupported.record(kind, n)
            }
        }
        return html
    }

    // ---- 여는 동안 ----------------------------------------------------------------------

    /**
     * 슬라이드 크기. 명세 범위(ST_SlideSizeCoordinate) 밖이거나 없으면 4:3 기본값. 비율은 1:10..10:1 로
     * 누른다 — 높이가 `vw` 로 적히므로 터무니없는 비율은 화면을 수십 배 길게 만든다.
     */
    private fun applySize(cx: Long?, cy: Long?) {
        if (cx == null || cy == null || cx !in MIN_SLIDE_EMU..MAX_SLIDE_EMU || cy !in MIN_SLIDE_EMU..MAX_SLIDE_EMU) {
            slideCx = DEFAULT_CX
            slideCy = DEFAULT_CY
            return
        }
        slideCx = cx
        slideCy = cy.coerceIn(cx / MAX_ASPECT, cx * MAX_ASPECT)
    }

    /** 잠금 안에서 부른다. 한 장이 깨졌다고 문서를 못 여는 것은 아니다 — 제목만 비운다. */
    private fun scanSlide(name: String): SlideScan = try {
        parse(name) { PptxScan.scan(it, limits) } ?: EMPTY_SCAN
    } catch (e: InterruptedIOException) {
        throw e
    } catch (e: Exception) {
        EMPTY_SCAN
    }

    /**
     * 슬라이드에 달린 메모의 수. 메모 부분이 깨졌으면 하나로 센다(무언가 있었다는 것은 안다).
     *
     * **메모 부분 하나는 문서 전체에서 한 번만 읽는다**([seen]). 관계 5만 개가 같은 부분을 가리킬 수
     * 있고, 이 일은 슬라이드 사이의 `ensureActive` 보다 안쪽이라 여는 시간 상한도 끊지 못한다
     * (관계 2,000 개가 500KB 부분 하나를 가리키자 20 초였다).
     *
     * 슬라이드의 관계 파일이 깨졌으면(깊이 상한 따위) 0 이다 — 여는 동안 던지면 **문서 전체**가
     * 열리지 않는다. 그 슬라이드는 그릴 때 같은 관계를 다시 읽다가 그 부분만 실패한다.
     */
    private fun countComments(name: String, seen: MutableSet<String>): Int {
        val rels = try {
            pkg.relationshipsOfType(name, "comments")
        } catch (e: InterruptedIOException) {
            throw e
        } catch (e: Exception) {
            return 0
        }
        var total = 0
        for (rel in rels) {
            if (total >= MAX_COMMENTS) break
            if (!seen.add(OpcNames.key(rel.target))) continue
            total += try {
                parse(rel.target) { PptxScan.countComments(it, limits, MAX_COMMENTS - total) } ?: 0
            } catch (e: InterruptedIOException) {
                throw e
            } catch (e: Exception) {
                1
            }
        }
        return total
    }

    private fun coreTitle(): String {
        val core = pkg.relationshipsOfType(null, "core-properties").firstOrNull { pkg.has(it.target) }?.target
            ?: CORE_PART.takeIf { pkg.has(it) }
            ?: return ""
        return try {
            parse(core) { PptxScan.coreTitle(it, limits) }.orEmpty()
        } catch (e: InterruptedIOException) {
            throw e
        } catch (e: Exception) {
            ""
        }
    }

    // ---- 그리는 동안(잠금 안) ------------------------------------------------------------

    /** 부분 하나를 파서에 물려 [block] 을 돌린다. 없으면 null. 스트림은 여기서 닫는다. */
    private fun <T> parse(name: String, block: (XmlPullParser) -> T): T? {
        val (p, stream) = pkg.parser(name) ?: return null
        stream.use { return block(p) }
    }

    /**
     * [source] 의 관계 가운데 [type] 인 첫 부분. [exclude] 에 든 부분(지금까지 지나온 것)을 가리키면
     * 버린다 — 레이아웃이 슬라이드를, 마스터가 레이아웃을 가리키는 고리를 여기서 끊는다.
     */
    private fun relatedPart(source: String, type: String, exclude: List<String>): String? {
        val seen = exclude.map { OpcNames.key(it) }
        return pkg.relationshipsOfType(source, type)
            .firstNotNullOfOrNull { rel -> pkg.canonical(rel.target) }
            ?.takeIf { OpcNames.key(it) !in seen }
    }

    /**
     * 레이아웃·마스터. 못 읽으면 null 을 기억한다 — 슬라이드는 그것 없이도 글자를 보여 준다.
     *
     * **무거운 것은 기억하지 않는다**([DrawingParser.weight]). 캐시는 개수(레이아웃 32·마스터 8)로만
     * 묶이는데 부분 하나가 값으로 100MB 가까이 불어날 수 있다. 보통의 레이아웃·마스터는 가중치가
     * 수백이라 이 문턱에 닿지 않는다. 무거운 것은 그릴 때마다 다시 읽는다(한 번 그리는 데 하나씩이다).
     */
    private fun cachedPart(cache: Lru<Cached<PartModel>>, name: String): PartModel? {
        val key = OpcNames.key(name)
        cache[key]?.let { return it.value }
        var weight = 0L
        val v = try {
            parse(name) { p ->
                val parser = DrawingParser(p, limits)
                parser.parsePart(name).also { weight = parser.weight }
            }
        } catch (e: InterruptedIOException) {
            throw e
        } catch (e: Exception) {
            // 슬라이드는 그리되 물려받을 자리·서식이 빠진다. 말한다(`warn` 은 같은 것을 한 번만 남긴다).
            warn(FlowWarnings.AUX_FAILED, name)
            null
        }
        if (weight <= MAX_CACHED_WEIGHT) cache[key] = Cached(v)
        return v
    }

    private fun cachedTheme(name: String): Theme? {
        val key = OpcNames.key(name)
        themes[key]?.let { return it.value }
        val v = try {
            parse(name) { DrawingParser(it, limits).parseTheme() }
        } catch (e: InterruptedIOException) {
            throw e
        } catch (e: Exception) {
            warn(FlowWarnings.AUX_FAILED, name)
            null
        }
        themes[key] = Cached(v)
        return v
    }

    /** 잠금 안에서 부른다. [relIndex] 의 주석. 같은 id 가 둘이면 먼저 적힌 것(`OpcPackage.relationship` 과 같다). */
    private fun relationshipById(source: String, id: String): OpcRelationship? {
        val key = OpcNames.key(source)
        val byId = relIndex[key] ?: HashMap<String, OpcRelationship>().also { m ->
            for (rel in pkg.relationships(source)) m.putIfAbsent(rel.id, rel)
            relIndex[key] = m
        }
        return byId[id]
    }

    private val host = object : RenderHost {
        override fun relationship(source: String, id: String): OpcRelationship? = relationshipById(source, id)

        override fun canonical(name: String): String? = pkg.canonical(name)

        override fun isDisplayableImage(name: String): Boolean = this@PptxFlowDocument.isDisplayableImage(name)

        override fun slideIndexOf(partName: String): Int = slideIndex[OpcNames.key(partName)] ?: -1

        override fun partPath(index: Int): String = this@PptxFlowDocument.partPath(index)

        /**
         * 같은 데이터 부분은 한 번 그리는 동안 한 번만 읽는다([diagrams]). 서로 다른 것도
         * [MAX_DIAGRAM_READS] 번까지만 읽고 그 뒤로는 null(화면은 SmartArt 로 센다) — 한 장에
         * SmartArt 가 그보다 많은 문서는 사람이 만든 것이 아니고, 부분 하나가 32MB 까지 들어온다.
         */
        override fun diagramDrawing(source: String, dataRelId: String): PartModel? {
            val data = relationshipById(source, dataRelId)?.takeIf { !it.external } ?: return null
            val key = OpcNames.key(source) + "\n" + OpcNames.key(data.target)
            diagrams[key]?.let { return it.value }
            if (diagramReads >= MAX_DIAGRAM_READS) return null
            diagramReads++
            var weight = 0L
            val v = try {
                findDiagramDrawing(source, data)?.let { name ->
                    parse(name) { p ->
                        val parser = DrawingParser(p, limits)
                        parser.parsePart(name).also { weight = parser.weight }
                    }
                }
            } catch (e: InterruptedIOException) {
                throw e
            } catch (e: Exception) {
                null
            }
            // 무거운 그림은 붙들지 않는다(레이아웃 캐시와 같은 문턱). 다시 가리키면 다시 읽고, 그것도 횟수에 든다.
            if (weight <= MAX_CACHED_WEIGHT) diagrams[key] = Cached(v)
            return v
        }
    }

    /**
     * SmartArt 의 캐시된 그림 부분. 데이터 부분의 `dataModelExt relId` 가 **슬라이드의 관계** 하나를
     * 가리킨다. 그것이 없는 파일은 번호로 짝짓는다(`data3.xml` ↔ `drawing3.xml` — 오피스가 붙이는 이름).
     */
    private fun findDiagramDrawing(source: String, data: OpcRelationship): String? {
        val drawings = pkg.relationshipsOfType(source, "diagramDrawing")
        val byExt = parse(data.target) { PptxScan.diagramDrawingRelId(it, limits) }
            ?.let { id -> drawings.firstOrNull { it.id == id } }
        val rel = byExt ?: run {
            val number = DIGITS.find(data.target.substringAfterLast('/'))?.value ?: return null
            drawings.firstOrNull { DIGITS.find(it.target.substringAfterLast('/'))?.value == number }
        } ?: return null
        val name = pkg.canonical(rel.target) ?: return null
        return name.takeIf { OpcNames.key(it) != OpcNames.key(source) }
    }

    /** 실패도 기억하는 캐시 칸. */
    private class Cached<T>(val value: T?)

    private class Lru<V>(private val max: Int) : LinkedHashMap<String, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, V>?): Boolean = size > max
    }

    companion object {
        /** 슬라이드 상한. 넘으면 앞의 2,000 장만 보여 주고 알린다. */
        const val MAX_SLIDES = 2_000

        /** 4:3 기본 크기(10 × 7.5 인치). */
        const val DEFAULT_CX = 9_144_000L
        const val DEFAULT_CY = 6_858_000L

        /** ST_SlideSizeCoordinate 는 1..56 인치다. 조금 넉넉히 받는다. */
        private const val MIN_SLIDE_EMU = 12_700L
        private const val MAX_SLIDE_EMU = 512_064_000L
        private const val MAX_ASPECT = 10L

        private const val MAX_COMMENTS = 100_000
        private const val MAX_CACHED_LAYOUTS = 32
        private const val MAX_CACHED_MASTERS = 8
        private const val MAX_CACHED_THEMES = 8
        private const val MAX_REL_INDEX = 6

        /** 캐시에 둘 수 있는 부분의 가중치(`DrawingParser.weight`). 1MB 안팎이다. */
        const val MAX_CACHED_WEIGHT = 20_000L

        /** 한 번 그리는 동안 SmartArt 때문에 부분을 읽는 횟수. */
        const val MAX_DIAGRAM_READS = 8
        private const val CORE_PART = "docProps/core.xml"

        private val EMPTY_SCAN = SlideScan("", false)
        private val DIGITS = Regex("[0-9]+")

        /**
         * 슬라이드 CSS. **상수다**(문서의 값은 인라인 `style` 로만, `CssValues` 를 지나서 들어간다).
         * 읽기용 기본 스타일(`HtmlShell`) 뒤에 붙어 같은 특이도에서 이긴다 — 본문 여백을 없애고,
         * 그림·표의 `max-width:100%` 를 푼다(슬라이드 밖으로 걸친 그림이 줄어들면 안 된다).
         * `.bu` 의 `text-indent:0` 은 문단의 내어쓰기가 글머리 상자 안으로 상속되지 않게 한다.
         */
        /**
         * 슬라이드를 **한 화면에 맞춘다.** 길이는 전부 `--u`(슬라이드 폭의 1%)로 적혀 있다(`SlideRenderer.vw`).
         * 화면이 슬라이드보다 세로로 길면(세로로 든 폰) 폭에 맞추고 — `--u` 가 `1vw` — 가로로 길면(가로로 든
         * 태블릿) 높이에 맞춘다 — `--u` 가 `1vh × 폭/높이`. 발표 자료를 보는 앱은 모두 슬라이드 한 장을
         * 통째로 보여 준다. 폭에만 맞추면 4:3 슬라이드의 아래 4분의 1 이 가로 화면 밖으로 나갔다.
         */
        internal fun fitCss(cx: Long, cy: Long): String {
            val g = gcd(cx, cy).coerceAtLeast(1)
            val w = cx / g
            val h = cy / g
            val perVh = CssValues.vw(cx.toDouble() / cy)?.removeSuffix("vw") ?: "1.3333"
            return ".slide{--u:1vw;margin:0 auto;}" +
                "@media (min-aspect-ratio: $w/$h){.slide{--u:${perVh}vh;}}"
        }

        private tailrec fun gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)

        const val CSS = """
body{margin:0;padding:0;background:#303030;}
.slide{position:relative;overflow:hidden;margin:0;}
.sp,.pic,.grp,.ln,.bgimg,.tbl{position:absolute;box-sizing:border-box;}
.slide img,.slide table{max-width:none;max-height:none;}
.bgimg{left:0;top:0;width:100%;height:100%;}
.pic{overflow:hidden;}
.pic img,.sp img.fill{position:absolute;left:0;top:0;width:100%;height:100%;}
.geo{position:absolute;left:0;top:0;right:0;bottom:0;}
.tx{position:absolute;left:0;top:0;right:0;bottom:0;display:flex;flex-direction:column;box-sizing:border-box;}
.tx p,.tbl p{margin:0;white-space:pre-wrap;line-height:1.2;}
.bu{display:inline-block;text-indent:0;white-space:pre;}
.tbl{border-collapse:collapse;table-layout:fixed;}
.tbl td{border:1px solid rgba(128,128,128,0.6);overflow:hidden;box-sizing:border-box;}
.ln{height:0;transform-origin:0 0;}
.chart,.missing{border:1px dashed rgba(128,128,128,0.8);}
"""
    }
}
