package io.github.donggi.iroiroviewer.format.xlsx

import io.github.donggi.iroiroviewer.format.FlowDocument
import io.github.donggi.iroiroviewer.format.FlowKind
import io.github.donggi.iroiroviewer.format.FlowOutline
import io.github.donggi.iroiroviewer.format.FlowPart
import io.github.donggi.iroiroviewer.format.FlowWarnings
import io.github.donggi.iroiroviewer.format.FormatId
import io.github.donggi.iroiroviewer.format.ProgressSink
import io.github.donggi.iroiroviewer.format.UnsupportedFeatures
import io.github.donggi.iroiroviewer.format.html.CssValues
import io.github.donggi.iroiroviewer.format.html.HtmlWriter
import io.github.donggi.iroiroviewer.format.html.StyleBuilder
import io.github.donggi.iroiroviewer.format.opc.OpcFlowDocument
import io.github.donggi.iroiroviewer.format.opc.OpcNames
import io.github.donggi.iroiroviewer.format.opc.OpcPackage
import io.github.donggi.iroiroviewer.safety.ParseLimits
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.io.InterruptedIOException
import java.util.BitSet
import java.util.concurrent.CancellationException
import kotlin.coroutines.CoroutineContext

/**
 * 통합 문서(xlsx) 을 흐름 문서로 여는 입구. `app/FormatRegistry` 가 `OoxmlOpener` 에 이어 준다.
 */
object XlsxDocument {

    /**
     * 패키지를 흐름 문서로. **패키지의 주인이 돌려준 문서로 넘어간다** — 문서를 닫으면 패키지도
     * 닫힌다. 던지면 여는이가 패키지를 닫고 `toOpenFailure` 로 옮긴다.
     */
    suspend fun open(pkg: OpcPackage, limits: ParseLimits, progress: ProgressSink): FlowDocument {
        val context = currentCoroutineContext()
        val doc = XlsxFlowDocument(pkg, limits)
        // 던지면 문서를 닫지 않는다 — 문서를 닫으면 패키지가 닫히는데, 패키지는 여는이가 닫는다.
        doc.load(context)
        progress.report(2, 3)
        return doc
    }
}

/**
 * 시트 하나가 부분 하나다.
 *
 * ## 여는 때 읽는 것과 그릴 때 읽는 것
 *
 * 여는 때는 **모든 시트가 함께 쓰는 것**만 읽는다 — 통합 문서(시트 목록·날짜 체계), 테마, 서식,
 * 공유 문자열. 시트 본문은 화면이 그 시트를 청할 때 읽는다(`renderBody`). 시트가 백 개인 통합
 * 문서를 열자마자 백 개를 다 풀면 첫 화면이 늦다.
 *
 * ## 무엇을 버렸는지 세는 것은 부분마다 한 번
 *
 * 바탕의 캐시가 셋뿐이라 같은 시트가 여러 번 그려진다. 그릴 때마다 차트·메모를 세면 배지의 숫자가
 * 시트를 오갈 때마다 는다. 그래서 부분마다 처음 그릴 때만 센다([counted]).
 */
internal class XlsxFlowDocument(pkg: OpcPackage, limits: ParseLimits) : OpcFlowDocument(pkg, limits, FormatId.XLSX) {

    private class SheetRef(val part: String, val name: String)

    @Volatile
    private var titleValue: String = ""

    @Volatile
    private var partList: List<FlowPart> = emptyList()

    @Volatile
    private var outlineList: List<FlowOutline> = emptyList()

    override val title: String get() = titleValue
    override val kind: FlowKind = FlowKind.SHEETS
    override val parts: List<FlowPart> get() = partList
    override val outline: List<FlowOutline> get() = outlineList
    override val css: String = CSS

    // 아래는 잠금 안에서만 읽고 쓴다(`load` 와 `renderBody` 가 둘 다 잠금 안이다).
    private var sheets: List<SheetRef> = emptyList()
    private var partByName: Map<String, Int> = emptyMap()
    private var styles: XlsxStyles = XlsxStyles.EMPTY
    private var strings: SharedStrings = SharedStrings.EMPTY
    private var date1904 = false
    private val counted = BitSet()
    private var formulaCounted = false

    /** 여는 때의 읽기. 단계 사이에서 취소를 본다. 잠금 안에서 돈다(바탕의 규칙). */
    fun load(context: CoroutineContext) {
        val workbook = locked { pkg.mainDocument ?: pkg.canonical("xl/workbook.xml") } ?: return
        val info = locked { withParser(workbook) { p -> WorkbookInfo.read(p, limits) } } ?: return
        date1904 = info.date1904
        titleValue = locked { readTitle() }
        context.ensureActive()

        val theme = locked {
            optional(null) { relatedPart(workbook, "theme", "xl/theme/theme1.xml")?.let { part -> withParser(part) { p -> XlsxStyles.readTheme(p, limits) } } }
        } ?: XlsxColors.DEFAULT_THEME
        context.ensureActive()

        locked {
            val part = relatedPart(workbook, "styles", "xl/styles.xml")
            if (part != null) {
                styles = optional(null) { withParser(part) { p -> XlsxStyles.read(p, limits, theme) } } ?: run {
                    // 서식을 잃었다. 값은 그대로 보이지만 글꼴·칠·표시 형식이 기본값이 된다.
                    warn(FlowWarnings.AUX_FAILED, part)
                    XlsxStyles.EMPTY
                }
            }
        }
        context.ensureActive()

        locked {
            val part = relatedPart(workbook, "sharedStrings", "xl/sharedStrings.xml")
            if (part != null) {
                val read = optional(null) { withParser(part) { p -> SharedStrings.read(p, limits) { context.ensureActive() } } }
                strings = read ?: SharedStrings.EMPTY
                // 못 읽은 것과 상한에서 자른 것은 다르다 — 앞은 보조 부분의 실패, 뒤는 문서 전체의 '너무 크다'.
                if (read == null) warn(FlowWarnings.AUX_FAILED, part)
                else if (read.truncated) warn(FlowWarnings.TRUNCATED, "")
            }
        }
        context.ensureActive()

        locked { buildParts(workbook, info) }
    }

    /**
     * 부분을 정한다. 보이는 워크시트만 — 숨긴 시트(`hidden`·`veryHidden`)는 Excel 도 보여 주지
     * 않는다. **보이는 워크시트가 하나도 없을 때만** 숨긴 것까지 연다(빈 화면보다 낫다).
     * 차트 시트·대화 상자 시트·매크로 시트는 그리지 않고 센다.
     */
    private fun buildParts(workbook: String, info: WorkbookInfo) {
        val rels = pkg.relationships(workbook)
        class Candidate(val entry: SheetEntry, val part: String)
        val worksheets = ArrayList<Candidate>()
        for (entry in info.sheets) {
            val rel = entry.relId?.let { id -> rels.firstOrNull { it.id == id } }
            val type = rel?.typeName
            when {
                rel == null || rel.external -> if (entry.visible) warn(FlowWarnings.PART_FAILED, SheetNames.display(entry.name))
                type == "worksheet" -> {
                    val part = pkg.canonical(rel.target)
                    if (part != null) worksheets.add(Candidate(entry, part))
                    else if (entry.visible) warn(FlowWarnings.PART_FAILED, SheetNames.display(entry.name))
                }
                type == "chartsheet" -> if (entry.visible) unsupported.record(UnsupportedFeatures.CHART)
                // 엑셀 4 매크로 시트는 이름 그대로 매크로다. 돌리지 않는다.
                // 매크로가 든 통합 문서(`.xlsm`)는 바탕이 이미 한 번 셌다 — 두 번 세지 않는다.
                type == "xlMacrosheet" || type == "xlIntlMacrosheet" ->
                    if (entry.visible && !pkg.hasMacros) unsupported.record(UnsupportedFeatures.MACRO)
                else -> if (entry.visible) unsupported.record(UnsupportedFeatures.UNKNOWN_ELEMENT)
            }
        }
        val shown = worksheets.filter { it.entry.visible }.ifEmpty { worksheets }
        if (info.sheetsTruncated) warn(FlowWarnings.TRUNCATED, "")
        sheets = shown.map { SheetRef(it.part, it.entry.name) }
        val byName = HashMap<String, Int>()
        sheets.forEachIndexed { i, s -> byName.putIfAbsent(s.name.lowercase(), i) }
        partByName = byName
        // 사람에게 보이는 이름은 다듬은 것, 시트 사이 링크를 찾는 열쇠는 적힌 그대로다.
        outlineList = sheets.mapIndexed { i, s -> FlowOutline(SheetNames.display(s.name), 0, i, null) }
        partList = sheets.mapIndexed { i, s -> FlowPart(partPath(i), SheetNames.display(s.name)) }
    }

    override fun renderBody(index: Int): String {
        val sheet = sheets[index]
        val label = partName(index)
        val local = UnsupportedFeatures()
        val scan = withParser(sheet.part) { p -> SheetScan.scan(p, limits) } ?: throw IOException("시트 부분이 없다")
        val plan = SheetPlan.build(scan)
        val w = HtmlWriter()
        var uncached = false
        val renderer = SheetRenderer(
            plan = plan,
            scan = scan,
            styles = styles,
            strings = strings,
            date1904 = date1904,
            linkTarget = { name -> partByName[name.lowercase()]?.takeIf { it != index }?.let { partPath(it) } },
            w = w,
            onUncachedFormula = { uncached = true },
        )
        withParser(sheet.part) { p -> renderer.render(p, limits) } ?: throw IOException("시트 부분이 없다")
        var truncated = renderer.truncated
        if (!w.full) renderDrawings(sheet.part, w, local)
        if (w.full) truncated = true
        countComments(sheet.part, local)
        if (uncached && !formulaCounted) {
            // 계산되지 않은 수식은 통합 문서에 한 번만 센다 — 셀마다 세면 숫자가 뜻을 잃는다.
            formulaCounted = true
            unsupported.record(UnsupportedFeatures.FORMULA_CACHED)
        }
        if (!counted[index]) {
            counted.set(index)
            for ((k, n) in local.snapshot()) unsupported.record(k, n)
        }
        if (truncated) warn(FlowWarnings.TRUNCATED, label)
        w.closeAll()
        return w.toString()
    }

    /** 그림은 표 아래에, 그림판에 걸린 순서대로. 차트·SmartArt 는 세기만 한다. */
    private fun renderDrawings(sheetPart: String, w: HtmlWriter, local: UnsupportedFeatures) {
        var opened = false
        for (drawing in annexes(sheetPart, "drawing")) {
            val items = optional(null) { withParser(drawing) { p -> XlsxDrawing.read(p, limits) } }
            if (items == null) {
                // 그림판이 깨졌다. 표는 살린다 — 그림이 빠졌다는 것만 센다.
                local.record(UnsupportedFeatures.UNKNOWN_ELEMENT)
                continue
            }
            for (item in items) {
                if (w.full) return
                when (item) {
                    is DrawingItem.Picture -> {
                        val src = pictureSource(drawing, item, local) ?: continue
                        if (!opened) {
                            w.start("div", "class" to "pics")
                            opened = true
                        }
                        val width = item.widthEmu?.takeIf { it > 0 }?.let { CssValues.pt(it / EMU_PER_PT, 1.0, 2000.0) }
                        w.void("img", "src" to src, "alt" to item.alt.orEmpty(), "style" to StyleBuilder().add("width", width).build())
                    }
                    is DrawingItem.Shape -> {
                        if (!item.textBox) local.record(UnsupportedFeatures.SHAPE)
                        if (item.text.isNotEmpty()) {
                            if (!opened) {
                                w.start("div", "class" to "pics")
                                opened = true
                            }
                            w.start("div", "class" to "shape").text(item.text).end("div")
                        }
                    }
                    DrawingItem.Chart -> local.record(UnsupportedFeatures.CHART)
                    DrawingItem.SmartArt -> local.record(UnsupportedFeatures.SMART_ART)
                    DrawingItem.Connector -> local.record(UnsupportedFeatures.SHAPE)
                    DrawingItem.Other -> local.record(UnsupportedFeatures.UNKNOWN_ELEMENT)
                }
            }
        }
        if (opened) w.end("div")
    }

    /** 그림의 `src`. 그릴 수 없으면 null 을 주고 까닭을 센다. */
    private fun pictureSource(drawing: String, item: DrawingItem.Picture, local: UnsupportedFeatures): String? {
        val id = item.embed
        if (id == null) {
            local.record(if (item.link != null) UnsupportedFeatures.LINKED_FILE else UnsupportedFeatures.UNSUPPORTED_IMAGE)
            return null
        }
        val rel = pkg.relationship(drawing, id)
        if (rel == null) {
            local.record(UnsupportedFeatures.UNSUPPORTED_IMAGE)
            return null
        }
        if (rel.external) {
            local.record(UnsupportedFeatures.LINKED_FILE)
            return null
        }
        val name = pkg.canonical(rel.target)
        if (name == null || !isDisplayableImage(name)) {
            local.record(UnsupportedFeatures.UNSUPPORTED_IMAGE)
            return null
        }
        return OpcNames.toUrl(name)
    }

    private fun countComments(sheetPart: String, local: UnsupportedFeatures) {
        var n = 0
        for (part in annexes(sheetPart, "comments")) {
            n += optional(1) { withParser(part) { p -> XlsxDrawing.countComments(p, limits, "comment") } } ?: 0
        }
        if (n == 0) {
            for (part in annexes(sheetPart, "threadedComment")) {
                n += optional(1) { withParser(part) { p -> XlsxDrawing.countComments(p, limits, "threadedComment") } } ?: 0
            }
        }
        local.record(UnsupportedFeatures.COMMENT, n)
    }

    /**
     * 시트에 딸린 [typeName] 부분들 — **같은 부분은 한 번만**, 많아야 [XlsxLimits.MAX_SHEET_ANNEXES] 개.
     *
     * 명세상 워크시트의 그림판·메모는 하나씩이다. 관계 파일은 공격자가 적는 값이라 같은 대상을 수천 번
     * 적을 수 있고, 그대로 따르면 그림이 수천 벌 그려지고 메모가 수천 배로 세이며, 큰 그림판 하나를
     * 그 수만큼 다시 푼다(부분 하나를 열 때마다 ZIP 예산이 되돌려진다).
     */
    private fun annexes(sheetPart: String, typeName: String): List<String> =
        pkg.relationshipsOfType(sheetPart, typeName).mapNotNull { pkg.canonical(it.target) }.distinct().take(XlsxLimits.MAX_SHEET_ANNEXES)

    private fun readTitle(): String {
        val core = pkg.relationshipsOfType(null, "core-properties").firstOrNull()?.let { pkg.canonical(it.target) } ?: return ""
        return optional("") { withParser(core) { p -> CoreTitle.read(p, limits) } } ?: ""
    }

    /** 통합 문서의 관계로 찾고, 관계가 없으면 관례의 이름으로 찾는다(관계를 빠뜨리는 도구가 있다). */
    private fun relatedPart(workbook: String, typeName: String, conventional: String): String? {
        val rel = pkg.relationshipsOfType(workbook, typeName).firstOrNull()
        return (rel?.let { pkg.canonical(it.target) }) ?: pkg.canonical(conventional)
    }

    /** XML 부분 하나를 읽고 스트림을 닫는다. 부분이 없으면 null. */
    private inline fun <T> withParser(part: String, block: (XmlPullParser) -> T): T? {
        val (p, stream) = pkg.parser(part) ?: return null
        return stream.use { block(p) }
    }

    /**
     * 없어도 되는 것을 읽는다 — 실패하면 [fallback]. 취소와 인터럽트는 삼키지 않는다
     * (CLAUDE.md '코드': 경계 catch 의 첫 줄은 언제나 취소를 되던진다).
     */
    private inline fun <T> optional(fallback: T, block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: InterruptedIOException) {
        throw e
    } catch (e: Exception) {
        fallback
    }

    companion object {
        private const val EMU_PER_PT = 12700.0

        /**
         * 시트의 모양. 바탕(`HtmlShell` 의 읽기 CSS) 뒤에 붙으므로 같은 특이도에서 이긴다.
         *
         * * 표는 화면 폭에 맞추지 않는다(`max-width:none`) — 열 너비가 곧 시트의 모양이다. 넓으면
         *   화면을 밀거나 집어서 줄인다(시트는 확대·축소가 켜져 있다).
         * * 격자선은 **0.5px** 이다. 테두리가 겹치는 칸에서 CSS 는 넓은 쪽을 고르므로, 격자선이 문서의
         *   1px 테두리를 이기지 못하게 한다(같은 폭이면 왼쪽·위쪽 칸이 이겨 테두리가 사라진다).
         * * 줄 바꿈이 없는 칸은 한 줄이고 넘치면 자른다. 오른쪽이 빈 칸만 넘쳐 보인다(`ov`) — 글자는 폭을
         *   정한 `div` 에 담겨 다음 글자 칸 앞에서 잘린다(`SheetRenderer.overflowRoom`).
         * * 줄 높이 1.25 — 한글 글꼴(Noto CJK)의 기본 줄 높이는 1.45 em 쯤이라, 그대로 두면 11pt 글자가
         *   15pt 행을 넘겨 모든 행이 Excel 보다 높아진다.
         */
        private const val CSS = """
body{padding:8px;}
table.sheet{border-collapse:collapse;table-layout:fixed;max-width:none;font-size:11pt;font-family:sans-serif;color:#000;background:#fff;}
table.sheet td{border:0.5px solid #d4d4d4;padding:0 3px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;vertical-align:bottom;word-break:normal;line-height:1.25;}
table.sheet th{border:0.5px solid #bdbdbd;padding:0 3px;background:#f2f2f2;color:#666;font-weight:normal;font-size:9pt;text-align:center;white-space:nowrap;overflow:hidden;}
table.sheet thead th{position:sticky;top:0;z-index:2;}
table.sheet tbody th{position:sticky;left:0;z-index:1;}
table.sheet thead th:first-child{left:0;z-index:3;}
table.sheet td.n{text-align:right;}
table.sheet td.c{text-align:center;}
table.sheet td.w{white-space:pre-wrap;overflow-wrap:anywhere;}
table.sheet td.ov{overflow:visible;text-overflow:clip;}
table.sheet td.ov>div{overflow:hidden;text-overflow:clip;}
table.sheet.nogrid td{border-color:transparent;}
table.sheet tr.gap th,table.sheet tr.gap td{height:6px;padding:0;background:#ececec;}
table.sheet a{color:inherit;}
div.pics{margin-top:12px;}
div.pics img{display:block;margin:0 0 8px;max-width:100%;height:auto;}
div.shape{margin:0 0 8px;padding:4px 8px;border:1px dashed #aaa;white-space:pre-wrap;}
"""
    }
}
