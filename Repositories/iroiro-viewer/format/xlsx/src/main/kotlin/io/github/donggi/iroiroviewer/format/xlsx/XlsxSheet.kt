package io.github.donggi.iroiroviewer.format.xlsx

import io.github.donggi.iroiroviewer.format.html.CssValues
import io.github.donggi.iroiroviewer.format.html.HtmlWriter
import io.github.donggi.iroiroviewer.format.html.StyleBuilder
import io.github.donggi.iroiroviewer.format.opc.OoxmlXml
import io.github.donggi.iroiroviewer.safety.ParseLimitExceededException
import io.github.donggi.iroiroviewer.safety.ParseLimits
import io.github.donggi.iroiroviewer.safety.nextGuarded
import org.xmlpull.v1.XmlPullParser
import java.time.DateTimeException
import java.time.LocalDate
import java.util.BitSet

/** `cols/col` 하나 — 열 범위의 너비(글자 단위, [SheetScan] 의 주석)·숨김·서식. */
internal class ColSpec(val min: Int, val max: Int, val widthChars: Double?, val hidden: Boolean, val style: Int)

/**
 * 시트의 **1차 훑기** 결과. 값은 읽지 않고 모양만 본다.
 *
 * ## 왜 두 번 읽는가
 *
 * 병합(`mergeCells`)과 셀 사이 연결(`hyperlinks`)은 명세상 `sheetData` **뒤에** 온다. 그런데 병합을
 * 알아야 첫 행부터 `colspan`·`rowspan` 을 쓸 수 있다. 셀을 전부 메모리에 들고 있다가 쓰는 길은 수십 MB
 * 시트에서 힙이 넘친다. 그래서 한 번은 모양만(행 번호 비트 집합·최대 열·병합), 한 번은 값을 흘려
 * 쓴다. 풀기를 두 번 하는 값이 셀을 다 드는 값보다 싸다.
 *
 * 행 번호를 비트 집합에 드는 것은 **빈 행 접기** 때문이다 — 1,000,000 행에 셀 하나가 있는 시트가
 * 백만 줄이 되지 않으려면 어디가 비었는지 미리 알아야 한다(최대 128 KiB).
 *
 * ## 열 너비는 글자 단위로 든다
 *
 * 파일의 열 너비는 pt 가 아니라 **기본 글꼴의 숫자 폭** 몇 개인가다(여백 포함, 기본 9.140625).
 * pt 로 바꾸는 것은 그리는 쪽([SheetRenderer])이 한다 — 화면이 실제로 쓸 글꼴의 숫자 폭을 알아야
 * 칸에 든 글자가 Excel 에서처럼 들어간다.
 */
internal class SheetScan {
    var defaultRowHeightPt = 15.0
    var defaultColWidthChars = DEFAULT_COL_CHARS
    val cols = ArrayList<ColSpec>()

    /** 칸이 하나라도 있거나 높이를 손으로 정한 행. */
    val rows = BitSet()

    /** 값(`v`·`is`·`f`)이 있는 칸을 가진 행. 잘렸는지 판단할 때만 쓴다. */
    val valueRows = BitSet()
    val hiddenRows = BitSet()
    var maxCol = 0
    var maxValueCol = 0
    val merges = ArrayList<CellRange>()

    /** 셀(`CellRefs.pack`) → 같은 통합 문서의 다른 시트 이름. 바깥 주소는 여기 오지 않는다. */
    val links = HashMap<Long, String>()
    var showGrid = true

    /** 시트 XML 이 상한(크기·깊이)에서 잘렸다. 읽은 데까지 그린다. */
    var inputTruncated = false

    companion object {
        /** Excel 기본 열 — Calibri 11 에서 64px, 숫자 폭 7px 로 9.142857 자. */
        const val DEFAULT_COL_CHARS = 64.0 / 7.0

        /** Excel 이 받는 열 너비의 상한(글자). */
        private const val MAX_COL_CHARS = 255.0

        fun scan(p: XmlPullParser, limits: ParseLimits): SheetScan {
            val s = SheetScan()
            try {
                if (!XlsxXml.toRoot(p, limits)) return s
                var sawView = false
                XlsxXml.children(p, limits) { name ->
                    when (name) {
                        "sheetViews" -> XlsxXml.children(p, limits) { v ->
                            if (v == "sheetView" && !sawView) {
                                sawView = true
                                s.showGrid = !isFalse(OoxmlXml.attr(p, "showGridLines"))
                            }
                        }
                        "sheetFormatPr" -> readFormat(p, s)
                        "cols" -> XlsxXml.children(p, limits) { c ->
                            if (c == "col" && s.cols.size < XlsxLimits.MAX_COL_SPECS) readCol(p)?.let { s.cols.add(it) }
                        }
                        "sheetData" -> scanRows(p, limits, s)
                        "mergeCells" -> XlsxXml.children(p, limits) { m ->
                            if (m == "mergeCell" && s.merges.size < XlsxLimits.MAX_MERGES) {
                                OoxmlXml.attr(p, "ref")?.let { CellRefs.parseRange(it) }?.let { s.merges.add(it) }
                            }
                        }
                        "hyperlinks" -> XlsxXml.children(p, limits) { h ->
                            if (h == "hyperlink" && s.links.size < XlsxLimits.MAX_LINKS) readLink(p, s)
                        }
                        else -> Unit
                    }
                }
            } catch (e: ParseLimitExceededException) {
                s.inputTruncated = true
            }
            return s
        }

        private fun readFormat(p: XmlPullParser, s: SheetScan) {
            positive(OoxmlXml.attr(p, "defaultRowHeight"))?.let { s.defaultRowHeightPt = it.coerceAtMost(XlsxLimits.MAX_ROW_HEIGHT_PT) }
            val dcw = positive(OoxmlXml.attr(p, "defaultColWidth"))
            val bcw = positive(OoxmlXml.attr(p, "baseColWidth"))
            s.defaultColWidthChars = when {
                dcw != null -> dcw
                // baseColWidth 는 여백을 뺀 글자 수다. 여백·격자선 5px 을 더하고 Excel 처럼 8px 의
                // 배수로 올린다(8 → 61px → 64px). 숫자 폭은 Calibri 11 의 7px 로 본다.
                bcw != null -> Math.ceil((bcw * 7.0 + 5.0) / 8.0) * 8.0 / 7.0
                else -> DEFAULT_COL_CHARS
            }.coerceIn(0.3, MAX_COL_CHARS)
        }

        private fun readCol(p: XmlPullParser): ColSpec? {
            val min = OoxmlXml.int(p, "min") ?: return null
            val max = OoxmlXml.int(p, "max") ?: return null
            if (min < 1 || max < min) return null
            val width = OoxmlXml.attr(p, "width")?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() }
            val hidden = isTrue(OoxmlXml.attr(p, "hidden")) || (width != null && width <= 0.0)
            return ColSpec(
                min = min,
                max = minOf(max, CellRefs.MAX_COLUMNS),
                widthChars = width?.takeIf { it > 0 }?.coerceIn(0.3, MAX_COL_CHARS),
                hidden = hidden,
                style = OoxmlXml.int(p, "style") ?: -1,
            )
        }

        private fun readLink(p: XmlPullParser, s: SheetScan) {
            // 관계(r:id)가 붙은 것은 바깥 주소다 — 링크가 될 수 없다(위생기가 스킴을 버린다).
            if (OoxmlXml.rel(p, "id") != null) return
            val location = OoxmlXml.attr(p, "location") ?: return
            val range = OoxmlXml.attr(p, "ref")?.let { CellRefs.parseRange(it) } ?: return
            val sheet = sheetOfLocation(location) ?: return
            s.links[CellRefs.pack(range.top, range.left)] = sheet
        }

        /** `'내 시트'!A1`·`Sheet2!B3` → 시트 이름. 시트가 없는 주소(정의된 이름)는 null. */
        fun sheetOfLocation(location: String): String? {
            val loc = location.removePrefix("#").trim()
            if (loc.startsWith("'")) {
                val out = StringBuilder()
                var i = 1
                while (i < loc.length) {
                    val c = loc[i]
                    if (c == '\'') {
                        if (i + 1 < loc.length && loc[i + 1] == '\'') {
                            out.append('\'')
                            i += 2
                            continue
                        }
                        return if (i + 1 < loc.length && loc[i + 1] == '!') out.toString() else null
                    }
                    out.append(c)
                    i++
                }
                return null
            }
            val bang = loc.indexOf('!')
            return if (bang > 0) loc.substring(0, bang) else null
        }

        private fun scanRows(p: XmlPullParser, limits: ParseLimits, s: SheetScan) {
            val depth = p.depth
            var lastRow = 0
            while (true) {
                val ev = p.nextGuarded(limits)
                if (ev == XmlPullParser.END_DOCUMENT) return
                if (ev == XmlPullParser.END_TAG && p.depth <= depth) return
                if (ev != XmlPullParser.START_TAG || p.depth != depth + 1) continue
                if (p.name != "row") {
                    OoxmlXml.skip(p, limits)
                    continue
                }
                val r = OoxmlXml.int(p, "r")?.takeIf { it in 1..CellRefs.MAX_ROWS } ?: (lastRow + 1)
                lastRow = r
                if (r > CellRefs.MAX_ROWS) {
                    OoxmlXml.skip(p, limits)
                    continue
                }
                val ht = OoxmlXml.attr(p, "ht")?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() }
                if (isTrue(OoxmlXml.attr(p, "hidden")) || (ht != null && ht <= 0.0)) s.hiddenRows.set(r)
                val custom = isTrue(OoxmlXml.attr(p, "customHeight")) && ht != null
                var any = false
                var anyValue = false
                var col = 0
                val rowDepth = p.depth
                while (true) {
                    val e2 = p.nextGuarded(limits)
                    if (e2 == XmlPullParser.END_DOCUMENT) return
                    if (e2 == XmlPullParser.END_TAG && p.depth <= rowDepth) break
                    if (e2 != XmlPullParser.START_TAG || p.depth != rowDepth + 1) continue
                    if (p.name != "c") {
                        OoxmlXml.skip(p, limits)
                        continue
                    }
                    col = OoxmlXml.attr(p, "r")?.let { CellRefs.columnOf(it) }?.takeIf { it > 0 } ?: (col + 1)
                    var hasValue = false
                    XlsxXml.children(p, limits) { k -> if (k == "v" || k == "is" || k == "f") hasValue = true }
                    if (col > CellRefs.MAX_COLUMNS) continue
                    any = true
                    if (col > s.maxCol) s.maxCol = col
                    if (hasValue) {
                        anyValue = true
                        if (col > s.maxValueCol) s.maxValueCol = col
                    }
                }
                if (any || custom) s.rows.set(r)
                if (anyValue) s.valueRows.set(r)
            }
        }

        fun isTrue(v: String?): Boolean = v == "1" || v.equals("true", ignoreCase = true)
        fun isFalse(v: String?): Boolean = v == "0" || v.equals("false", ignoreCase = true)

        private fun positive(v: String?): Double? = v?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
    }
}

/**
 * 무엇을 어디에 그릴지 — 보이는 열, 그릴 행(빈 행을 접은 뒤), 병합의 자리.
 *
 * ## 행 계획
 *
 * 채워진 행 사이에 빈 행이 [XlsxLimits.GAP_ROWS] 개를 넘게 이어지면 **한 줄(`tr.gap`)로 접는다.**
 * 병합이 덮는 행은 접지 않는다 — 접으면 `rowspan` 이 가운데서 끊긴다. 숨긴 행은 그리지 않는다
 * (Excel 도 보여 주지 않는다). 모든 줄은 [XlsxLimits.MAX_RENDERED_ROWS] 에서 멈춘다.
 *
 * ## 병합
 *
 * 병합의 **보이는 첫 칸**이 병합을 대표한다(맨 윗행·맨 왼열이 숨었을 수 있다). 겹치는 병합은 먼저
 * 온 것만 받는다 — 겹친 `rowspan` 은 표를 비틀어 뒤의 칸이 전부 한 칸씩 밀린다. 겹침은 계획의 격자
 * (최대 2,000 × 256 비트, [XlsxLimits.MAX_RENDERED_ROWS])로 가린다.
 */
internal class SheetPlan private constructor(
    val visibleCols: IntArray,
    /** 보이는 열마다 너비(글자 단위). pt 로는 [SheetRenderer] 가 바꾼다. */
    val colWidthChars: DoubleArray,
    val colStyle: IntArray,
    private val colToVisible: IntArray,
    val rowNumbers: IntArray,
    val gap: BooleanArray,
    private val anchors: HashMap<Long, Long>,
    val rowsTruncated: Boolean,
    val colsTruncated: Boolean,
) {
    val width: Int get() = visibleCols.size
    val size: Int get() = rowNumbers.size

    /** 열 번호의 보이는 자리. 숨었거나 그리지 않는 열이면 -1. */
    fun visibleOf(col: Int): Int = if (col >= 1 && col < colToVisible.size) colToVisible[col] else -1

    /** 계획의 (줄, 보이는 열) 이 병합의 대표 칸이면 `rowspan shl 32 or colspan`, 아니면 0. */
    fun anchorAt(planIndex: Int, vis: Int): Long = anchors[key(planIndex, vis)] ?: 0L

    companion object {
        private fun key(planIndex: Int, vis: Int): Long = (planIndex.toLong() shl 16) or vis.toLong()

        fun build(scan: SheetScan): SheetPlan {
            // ---- 열 ----
            var width0 = maxOf(scan.maxCol, 1)
            for (m in scan.merges) width0 = maxOf(width0, minOf(m.right, CellRefs.MAX_COLUMNS))
            val hidden = BooleanArray(width0 + 1)
            val widths = DoubleArray(width0 + 1) { scan.defaultColWidthChars }
            val styles = IntArray(width0 + 1) { -1 }
            for (spec in scan.cols) {
                val hi = minOf(spec.max, width0)
                for (c in spec.min..hi) {
                    if (spec.hidden) hidden[c] = true
                    spec.widthChars?.let { widths[c] = it }
                    if (spec.style >= 0) styles[c] = spec.style
                }
            }
            val colToVisible = IntArray(width0 + 2) { -1 }
            val vis = ArrayList<Int>()
            for (c in 1..width0) {
                if (hidden[c]) continue
                if (vis.size >= XlsxLimits.MAX_RENDERED_COLUMNS) break
                colToVisible[c] = vis.size
                vis.add(c)
            }
            if (vis.isEmpty()) {
                // 전부 숨었다. 빈 표라도 모양은 있어야 그림·도형이 그 아래 선다.
                colToVisible[1] = 0
                vis.add(1)
            }
            val visibleCols = vis.toIntArray()
            val colsTruncated = vis.size >= XlsxLimits.MAX_RENDERED_COLUMNS && scan.maxValueCol > visibleCols.last()
            // 열 번호 → 그 열 이상에서 처음 보이는 자리(없으면 폭). 병합의 대표 열과 colspan 을 O(1) 로.
            val nextVis = IntArray(width0 + 2)
            nextVis[width0 + 1] = visibleCols.size
            for (c in width0 downTo 1) nextVis[c] = if (colToVisible[c] >= 0) colToVisible[c] else nextVis[c + 1]

            // ---- 행 ----
            val occupied = scan.rows.clone() as BitSet
            for (m in scan.merges) {
                val bottom = minOf(m.bottom, m.top + XlsxLimits.MAX_RENDERED_ROWS - 1, CellRefs.MAX_ROWS)
                occupied.set(m.top, bottom + 1)
            }
            val rowNumbers = ArrayList<Int>()
            val gaps = ArrayList<Boolean>()
            val cap = XlsxLimits.MAX_RENDERED_ROWS
            var prev = 0
            var r = occupied.nextSetBit(1)
            outer@ while (r in 1..CellRefs.MAX_ROWS && rowNumbers.size < cap) {
                if (scan.hiddenRows[r]) {
                    r = occupied.nextSetBit(r + 1)
                    continue
                }
                val between = r - prev - 1
                val empties = if (between <= 0) 0 else between - scan.hiddenRows.get(prev + 1, r).cardinality()
                if (empties > XlsxLimits.GAP_ROWS) {
                    rowNumbers.add(prev + 1)
                    gaps.add(true)
                } else {
                    for (x in prev + 1 until r) {
                        if (scan.hiddenRows[x]) continue
                        if (rowNumbers.size >= cap) break@outer
                        rowNumbers.add(x)
                        gaps.add(false)
                    }
                }
                if (rowNumbers.size >= cap) break
                rowNumbers.add(r)
                gaps.add(false)
                prev = r
                r = occupied.nextSetBit(r + 1)
            }
            if (rowNumbers.isEmpty()) {
                rowNumbers.add(1)
                gaps.add(false)
            }
            val lastPlanned = rowNumbers.last()
            val visibleValues = scan.valueRows.clone() as BitSet
            visibleValues.andNot(scan.hiddenRows)
            val rowsTruncated = visibleValues.nextSetBit(lastPlanned + 1) >= 0

            val rowArr = rowNumbers.toIntArray()
            val gapArr = gaps.toBooleanArray()

            // ---- 병합 ----
            val anchors = HashMap<Long, Long>()
            val w = visibleCols.size
            val grid = BitSet()
            var work = 0L
            for (m in scan.merges.sortedWith(compareBy({ it.top }, { it.left }))) {
                if (work > XlsxLimits.MAX_MERGE_WORK) break
                val pi = firstAtOrAfter(rowArr, m.top)
                if (pi < 0 || gapArr[pi] || rowArr[pi] > m.bottom) continue
                if (m.left > width0) continue
                val vj = nextVis[m.left]
                if (vj >= w || visibleCols[vj] > m.right) continue
                val colspan = nextVis[minOf(m.right, width0) + 1] - vj
                var k = pi
                while (k < rowArr.size && !gapArr[k] && rowArr[k] <= m.bottom) k++
                val rowspan = k - pi
                work += rowspan
                if (colspan <= 0 || rowspan <= 0 || (colspan == 1 && rowspan == 1)) continue
                var overlap = false
                for (row in pi until pi + rowspan) {
                    val base = row * w + vj
                    val hit = grid.nextSetBit(base)
                    if (hit >= 0 && hit < base + colspan) {
                        overlap = true
                        break
                    }
                }
                if (overlap) continue
                for (row in pi until pi + rowspan) grid.set(row * w + vj, row * w + vj + colspan)
                anchors[key(pi, vj)] = (rowspan.toLong() shl 32) or colspan.toLong()
            }

            return SheetPlan(
                visibleCols = visibleCols,
                colWidthChars = DoubleArray(visibleCols.size) { widths[visibleCols[it]] },
                colStyle = IntArray(visibleCols.size) { styles[visibleCols[it]] },
                colToVisible = colToVisible,
                rowNumbers = rowArr,
                gap = gapArr,
                anchors = anchors,
                rowsTruncated = rowsTruncated,
                colsTruncated = colsTruncated,
            )
        }

        /** 오름차순 [rows] 에서 [target] 이상인 첫 자리. 없으면 -1. */
        private fun firstAtOrAfter(rows: IntArray, target: Int): Int {
            var lo = 0
            var hi = rows.size
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (rows[mid] < target) lo = mid + 1 else hi = mid
            }
            return if (lo < rows.size) lo else -1
        }
    }
}

/**
 * 2차 — 값을 읽어 표를 쓴다. 계획([SheetPlan])에 있는 줄만, 계획의 순서대로.
 *
 * ## 칸의 모양
 *
 * * 머리글 행(열 글자)과 머리글 열(행 번호)은 `th` 다 — Excel 이 그리는 틀 그 자체이고 문장이 아니다.
 * * 숫자는 오른쪽, 글자는 왼쪽, 참·거짓과 오류는 가운데(Excel 의 '일반' 맞춤). 서식이 맞춤을
 *   적었으면 그것이 이긴다.
 * * 줄 바꿈이 없는 글자는 **오른쪽 칸이 비었으면 넘쳐 보인다**(`ov`) — Excel 에서 A1 의 제목이
 *   옆 칸까지 이어져 보이는 것이 그것이다. 옆이 차 있으면 잘리고, 넘쳐도 **다음 글자 칸 앞에서** 잘린다.
 * * 바깥 주소 연결은 링크가 될 수 없다(위생기가 스킴을 버린다). 글자만 남는다. 같은 통합 문서의
 *   다른 시트로 가는 연결만 `~part-N.html` 로 잇는다.
 * * 병합의 테두리 — Excel 은 병합 안의 칸마다 자기 테두리를 적는다. '바깥쪽 테두리' 를 그은 A1:C1 은
 *   A1 에 왼·위·아래, C1 에 오른·위·아래가 있다. 대표 칸(A1)의 서식만 쓰면 오른쪽 선이 사라지므로
 *   **같은 줄의 오른쪽 끝 칸**에 오른쪽 선이 있으면 그것을 쓴다(없으면 대표 칸의 것을 둔다 — 모든 칸에
 *   같은 서식을 적는 도구가 있다). 아래쪽 선은 아래 줄을 아직 읽지 않았으므로 대표 칸의 것뿐이다 —
 *   여러 줄 병합의 아래쪽 선은 사라질 수 있다.
 */
internal class SheetRenderer(
    private val plan: SheetPlan,
    private val scan: SheetScan,
    private val styles: XlsxStyles,
    private val strings: SharedStrings,
    private val date1904: Boolean,
    /** 시트 이름 → 그 부분의 가상 경로. 없거나 지금 시트면 null. */
    private val linkTarget: (String) -> String?,
    private val w: HtmlWriter,
    /** 계산 결과가 없는 수식을 만났다. */
    private val onUncachedFormula: () -> Unit,
) {
    /** 무언가를 그리지 못하고 멈췄다(행·열·칸·글자 상한, 잘린 입력). */
    var truncated = false
        private set

    private val width = plan.width
    private var cells = 0

    /**
     * 화면 글꼴의 숫자 한 자 폭(pt). 열 너비의 단위가 '기본 글꼴의 숫자 폭' 이므로 여기 곱해 pt 로 옮긴다.
     *
     * 파일이 적은 글꼴(Calibri 0.507 em, 맑은 고딕 0.551 em — 윈도의 글꼴 파일에서 쟀다)이 기기에는
     * 없고, WebView 는 라틴 숫자를 Roboto 로 그린다. Roboto 는 Arial(0.556 em, 쟀다)과 폭이 거의 같다 —
     * **Roboto 자체는 기기에서 재지 않았다.** 그래서 0.56 em 으로 잡는다. Calibri 로 짠 통합 문서는
     * Excel 보다 조금 넓게 보이지만, 칸에 든 숫자가 Excel 에서처럼 들어간다.
     */
    private val digitPt = (styles.defaultFont.sizePt ?: 11.0).coerceIn(1.0, 409.0) * DIGIT_EM

    /** 보이는 열마다 pt. */
    private val colPt = DoubleArray(width) { (plan.colWidthChars[it] * digitPt).coerceIn(1.0, XlsxLimits.MAX_COL_WIDTH_PT) }
    private var planIndex = 0
    private var stopped = false

    /** 보이는 열마다, 윗줄의 `rowspan` 이 덮고 있는 마지막 계획 줄. */
    private val coveredUntil = IntArray(width) { -1 }

    // 한 줄의 칸들. 줄마다 다시 쓴다(열이 최대 256).
    private val present = BooleanArray(width)
    private val xfOf = IntArray(width)
    private val kindOf = ByteArray(width)
    private val textOf = arrayOfNulls<String>(width)
    private val colorOf = arrayOfNulls<String>(width)
    private val linkOf = arrayOfNulls<String>(width)

    fun render(p: XmlPullParser, limits: ParseLimits) {
        openTable()
        try {
            if (XlsxXml.toRoot(p, limits)) streamSheet(p, limits)
        } catch (e: ParseLimitExceededException) {
            truncated = true
        }
        // XML 에 없던 계획 줄(병합이 덮는 빈 행 따위)과, 입력이 잘린 뒤의 줄.
        while (!stopped && planIndex < plan.size) emitPlanned()
        w.end("tbody")
        w.end("table")
        if (plan.rowsTruncated || plan.colsTruncated || scan.inputTruncated) truncated = true
    }

    private fun openTable() {
        val d = styles.defaultFont
        val total = ROW_HEADER_PT + colPt.sum()
        val style = StyleBuilder()
            .add("width", CssValues.pt(total, 0.0, 1_000_000.0))
            .add("font-size", d.sizePt?.let { CssValues.pt(it, 1.0, 409.0) })
            .add("font-family", d.name?.let { CssValues.fontFamily(it) }?.let { "$it,sans-serif" })
            .add("color", d.color?.takeIf { it != "#000000" })
            .build()
        w.start("table", "class" to if (scan.showGrid) "sheet" else "sheet nogrid", "style" to style)
        w.start("colgroup")
        w.void("col", "style" to StyleBuilder().add("width", CssValues.pt(ROW_HEADER_PT)).build())
        for (j in 0 until width) {
            w.void("col", "style" to StyleBuilder().add("width", CssValues.pt(colPt[j], 0.0, XlsxLimits.MAX_COL_WIDTH_PT)).build())
        }
        w.end("colgroup")
        w.start("thead").start("tr").start("th").end("th")
        for (j in 0 until width) w.start("th").text(CellRefs.columnName(plan.visibleCols[j])).end("th")
        w.end("tr").end("thead")
        w.start("tbody")
    }

    private fun streamSheet(p: XmlPullParser, limits: ParseLimits) {
        val depth = p.depth
        while (true) {
            val ev = p.nextGuarded(limits)
            if (ev == XmlPullParser.END_DOCUMENT) return
            if (ev == XmlPullParser.END_TAG && p.depth <= depth) return
            if (ev != XmlPullParser.START_TAG || p.depth != depth + 1) continue
            if (p.name == "sheetData") {
                streamRows(p, limits)
                // 병합·연결은 1차에서 읽었다. 뒤는 볼 것이 없다.
                return
            }
            OoxmlXml.skip(p, limits)
        }
    }

    private fun streamRows(p: XmlPullParser, limits: ParseLimits) {
        val depth = p.depth
        var lastRow = 0
        while (!stopped && planIndex < plan.size) {
            val ev = p.nextGuarded(limits)
            if (ev == XmlPullParser.END_DOCUMENT) return
            if (ev == XmlPullParser.END_TAG && p.depth <= depth) return
            if (ev != XmlPullParser.START_TAG || p.depth != depth + 1) continue
            if (p.name != "row") {
                OoxmlXml.skip(p, limits)
                continue
            }
            val r = OoxmlXml.int(p, "r")?.takeIf { it in 1..CellRefs.MAX_ROWS } ?: (lastRow + 1)
            lastRow = r
            // 앞의 계획 줄(빈 행·접힌 줄)을 먼저 쓴다. 순서가 뒤집힌 행은 이미 지나갔으므로 버린다.
            while (!stopped && planIndex < plan.size && plan.rowNumbers[planIndex] < r) emitPlanned()
            if (stopped || planIndex >= plan.size) return
            if (!plan.gap[planIndex] && plan.rowNumbers[planIndex] == r) {
                val rowStyle = if (SheetScan.isTrue(OoxmlXml.attr(p, "customFormat"))) OoxmlXml.int(p, "s") ?: -1 else -1
                val ht = OoxmlXml.attr(p, "ht")?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
                readCells(p, limits, r)
                emitRow(r, rowStyle, ht)
                planIndex++
            } else {
                OoxmlXml.skip(p, limits)
            }
        }
    }

    private fun clearRow() {
        java.util.Arrays.fill(present, false)
        java.util.Arrays.fill(textOf, null)
        java.util.Arrays.fill(colorOf, null)
        java.util.Arrays.fill(linkOf, null)
    }

    private fun readCells(p: XmlPullParser, limits: ParseLimits, row: Int) {
        clearRow()
        val depth = p.depth
        var col = 0
        while (true) {
            val ev = p.nextGuarded(limits)
            if (ev == XmlPullParser.END_DOCUMENT) return
            if (ev == XmlPullParser.END_TAG && p.depth <= depth) return
            if (ev != XmlPullParser.START_TAG || p.depth != depth + 1) continue
            if (p.name != "c") {
                OoxmlXml.skip(p, limits)
                continue
            }
            col = OoxmlXml.attr(p, "r")?.let { CellRefs.columnOf(it) }?.takeIf { it > 0 } ?: (col + 1)
            val vis = plan.visibleOf(col)
            if (vis < 0) {
                OoxmlXml.skip(p, limits)
                continue
            }
            val xf = OoxmlXml.int(p, "s")?.takeIf { it >= 0 } ?: 0
            val type = OoxmlXml.attr(p, "t")
            var v: String? = null
            var inline: String? = null
            var formula = false
            XlsxXml.children(p, limits) { k ->
                when (k) {
                    "v" -> v = OoxmlXml.collectText(p, limits, XlsxLimits.MAX_CELL_CHARS)
                    "is" -> inline = XlsxXml.readRichText(p, limits)
                    "f" -> formula = true
                    else -> Unit
                }
            }
            store(vis, xf, type, v, inline, formula)
            scan.links[CellRefs.pack(row, col)]?.let { linkOf[vis] = linkTarget(it) }
        }
    }

    /** 칸 하나의 값을 보이는 글자로. */
    private fun store(vis: Int, xf: Int, type: String?, v: String?, inline: String?, formula: Boolean) {
        present[vis] = true
        xfOf[vis] = xf
        kindOf[vis] = KIND_NONE
        textOf[vis] = null
        colorOf[vis] = null
        // 계산 결과(`v`)가 없는 수식. openpyxl 처럼 빈 `<v></v>` 를 적는 도구도 있다 — 글자 결과(`str`)의
        // 빈 문자열은 정상 결과이므로 뺀다.
        if (formula && inline == null && (v == null || (v.isEmpty() && type != "str"))) {
            onUncachedFormula()
            return
        }
        val nf = styles.numberFormat(xf)
        fun textValue(s: String) {
            val f = nf.formatText(s)
            kindOf[vis] = KIND_TEXT
            textOf[vis] = f.text
            colorOf[vis] = f.color
        }
        fun numberValue(f: Formatted) {
            kindOf[vis] = KIND_NUMBER
            textOf[vis] = f.text
            colorOf[vis] = f.color
        }
        when (type) {
            "s" -> v?.trim()?.toIntOrNull()?.let { textValue(strings[it]) }
            "str" -> textValue(XlsxXml.decode(v.orEmpty()))
            "inlineStr" -> textValue(inline ?: XlsxXml.decode(v.orEmpty()))
            "b" -> if (v != null) {
                kindOf[vis] = KIND_CENTER
                val t = v.trim()
                textOf[vis] = if (t == "1" || t.equals("true", true)) "TRUE" else "FALSE"
            }
            "e" -> if (v != null) {
                kindOf[vis] = KIND_CENTER
                textOf[vis] = v.trim()
            }
            "d" -> if (v != null) {
                val serial = isoSerial(v, date1904)
                if (serial != null && nf.isDate) numberValue(nf.format(serial, date1904)) else textValue(v.trim())
            }
            else -> {
                val raw = v?.trim()
                if (!raw.isNullOrEmpty()) {
                    val d = raw.toDoubleOrNull()
                    if (d != null && d.isFinite()) {
                        numberValue(nf.format(d, date1904, if (nf.isGeneral) generalWidth(vis, xf) else 11))
                    } else {
                        textValue(raw)
                    }
                }
            }
        }
    }

    /**
     * 일반 형식의 수가 이 칸에 몇 자 들어가는가 — Excel 이 좁은 칸에서 소수를 줄이는 폭이다.
     * 병합이면 덮는 열을 합치고, 글꼴이 기본보다 크면 그만큼 줄인다.
     */
    private fun generalWidth(vis: Int, xf: Int): Int {
        val span = plan.anchorAt(planIndex, vis)
        val colspan = if (span == 0L) 1 else (span and 0xFFFFFFFFL).toInt()
        var chars = 0.0
        for (k in vis until minOf(width, vis + colspan)) chars += plan.colWidthChars[k]
        val base = styles.defaultFont.sizePt ?: 11.0
        val scale = base / (styles.fontSizePt(xf) ?: base)
        return Math.floor((chars - PADDING_CHARS) * scale).toInt().coerceIn(1, 11)
    }

    /** 계획 줄 가운데 XML 에 없던 것 — 접힌 줄이거나 빈 행. */
    private fun emitPlanned() {
        // 칸 상한에 닿았으면 줄을 열지 않는다 — 열고 나서 멈추면 칸 없는 행 머리 하나가 남는다.
        // 이 줄은 XML 에 없던 줄이라 칸 배열이 앞 줄의 것이다 — 배열을 보지 않고 행 단위로만 가린다.
        if (cells >= XlsxLimits.MAX_RENDERED_CELLS || w.full) {
            stop(w.full || valueRowFrom(plan.rowNumbers[planIndex]))
            return
        }
        if (plan.gap[planIndex]) {
            w.start("tr", "class" to "gap")
            w.start("th").end("th")
            w.start("td", "colspan" to if (width > 1) width.toString() else null).end("td")
            w.end("tr")
            cells++
            planIndex++
            return
        }
        clearRow()
        emitRow(plan.rowNumbers[planIndex], -1, null)
        planIndex++
    }

    private fun emitRow(row: Int, rowStyle: Int, htPt: Double?) {
        if (cells >= XlsxLimits.MAX_RENDERED_CELLS || w.full) {
            stop(w.full || valuesLeft(row, 0))
            return
        }
        val height = (htPt ?: scan.defaultRowHeightPt).coerceIn(1.0, XlsxLimits.MAX_ROW_HEIGHT_PT)
        w.start("tr", "style" to StyleBuilder().add("height", CssValues.pt(height)).build())
        w.start("th").text(row.toString()).end("th")
        for (j in 0 until width) {
            if (coveredUntil[j] >= planIndex) continue
            if (cells >= XlsxLimits.MAX_RENDERED_CELLS) {
                stop(valuesLeft(row, j))
                break
            }
            val span = plan.anchorAt(planIndex, j)
            val rowspan = if (span == 0L) 1 else (span ushr 32).toInt()
            val colspan = if (span == 0L) 1 else (span and 0xFFFFFFFFL).toInt()
            for (k in j until minOf(width, j + colspan)) coveredUntil[k] = planIndex + rowspan - 1
            emitCell(j, colspan, rowspan, rowStyle)
            if (w.full) {
                stop()
                break
            }
        }
        w.end("tr")
    }

    /**
     * 그리기를 멈춘다. [lost] 는 멈춘 자리 뒤에 **보일 값**이 남았는가다 — 남은 것이 서식만 입힌 빈 칸이면
     * 잃은 글이 없으므로 '줄였다' 를 알리지 않는다(계획의 행·열 상한과 같은 기준). 실세계 표본(POI 의
     * `57181.xlsm`, General 시트)은 값이 131행에서 끝나고 테두리만 그은 빈 칸이 264행까지 이어져, 199행의
     * 칸 상한에서 잃은 것 없이 '줄였다' 를 냈다.
     */
    private fun stop(lost: Boolean = true) {
        stopped = true
        if (lost) truncated = true
    }

    /** 지금 줄([row], 칸이 읽혀 있다)의 [fromVis] 열부터, 또는 그 아래 보이는 행에 값이 남았는가. */
    private fun valuesLeft(row: Int, fromVis: Int): Boolean {
        for (k in fromVis until width) if (present[k] && textOf[k] != null) return true
        return valueRowFrom(row + 1)
    }

    /**
     * [row] 부터 아래에 값이 든 **보이는** 행이 있는가. 숨긴 열에만 값이 있는 행도 센다(1차가 열을 가리지 않고
     * 세었다) — 모자라게 알리는 것보다 한 번 더 알리는 편이 낫다.
     */
    private fun valueRowFrom(row: Int): Boolean {
        var r = scan.valueRows.nextSetBit(row)
        while (r >= 0 && scan.hiddenRows[r]) r = scan.valueRows.nextSetBit(r + 1)
        return r >= 0
    }

    private fun emitCell(j: Int, colspan: Int, rowspan: Int, rowStyle: Int) {
        val has = present[j]
        val xf = if (has) xfOf[j] else if (rowStyle >= 0) rowStyle else plan.colStyle[j]
        val text = if (has) textOf[j] else null
        val kind = if (has) kindOf[j] else KIND_NONE
        val style = StyleBuilder()
        var cls: String? = null
        var overflowPt = 0.0
        if (text.isNullOrEmpty()) {
            if (xf >= 0) for ((k, v) in styles.emptyCellCss(xf)) style.add(k, v)
        } else {
            for ((k, v) in styles.cellCss(xf)) style.add(k, v)
            // 표시 형식의 색(`[Red]`)이 글꼴 색을 이긴다 — 뒤에 적으면 CSS 가 뒤의 것을 쓴다.
            style.add("color", colorOf[j])
            val classes = ArrayList<String>(2)
            val horizontal = styles.horizontal(xf)
            if (horizontal == null) {
                when (kind) {
                    KIND_NUMBER -> classes.add("n")
                    KIND_CENTER -> classes.add("c")
                }
            }
            if (styles.wraps(xf)) {
                classes.add("w")
            } else if (kind == KIND_TEXT && colspan == 1 && (horizontal == null || horizontal == "left")) {
                val room = overflowRoom(j)
                if (room > 0.0) {
                    classes.add("ov")
                    overflowPt = colPt[j] + room
                }
            }
            if (classes.isNotEmpty()) cls = classes.joinToString(" ")
        }
        if (colspan > 1) {
            // 병합의 오른쪽 선은 같은 줄의 오른쪽 끝 칸에서 읽는다(위 '칸의 모양'). 뒤에 적은 선언이 이긴다.
            val edge = j + colspan - 1
            if (present[edge]) style.add("border-right", styles.borderRight(xfOf[edge]))
        }
        w.start(
            "td",
            "class" to cls,
            "colspan" to if (colspan > 1) colspan.toString() else null,
            "rowspan" to if (rowspan > 1) rowspan.toString() else null,
            "style" to style.build(),
        )
        if (!text.isNullOrEmpty()) {
            // 넘치는 글자는 폭을 정한 상자에 담는다 — 상자의 끝이 다음 글자 칸의 왼쪽 선이다([overflowRoom]).
            val box = if (overflowPt > 0.0) {
                StyleBuilder().add("width", CssValues.pt(overflowPt - CELL_PADDING_PT, 1.0, 1_000_000.0)).build()
            } else {
                null
            }
            if (box != null) w.start("div", "style" to box)
            val link = linkOf[j]
            if (link != null) w.start("a", "href" to link).text(text).end("a") else w.text(text)
            if (box != null) w.end("div")
        }
        w.end("td")
        cells++
    }

    /**
     * 글자가 오른쪽으로 넘쳐 보일 수 있는 폭(pt) — 이어지는 빈 칸들의 폭 합. 바로 오른쪽이 차 있으면 0.
     *
     * Excel 처럼 **다음에 글자가 있는 칸(또는 병합) 앞에서 멈춘다.** 바로 옆 칸만 보고 `overflow:visible`
     * 로 두면 글자가 끝없이 뻗어 두 칸 너머 칸의 글자 위에 겹쳐 그려지고 표 밖까지 나간다.
     */
    private fun overflowRoom(j: Int): Double {
        var room = 0.0
        var n = j + 1
        while (n < width) {
            if (coveredUntil[n] >= planIndex || plan.anchorAt(planIndex, n) != 0L) break
            if (present[n] && !textOf[n].isNullOrEmpty()) break
            room += colPt[n]
            n++
        }
        return room
    }

    companion object {
        /** 행 번호 열의 너비. 일곱 자리(1,048,576)가 9pt 로 들어간다. */
        const val ROW_HEADER_PT = 36.0

        /** 화면 글꼴의 숫자 폭(em). [digitPt] 의 주석. */
        const val DIGIT_EM = 0.56

        /** 칸 좌우 여백과 격자선이 먹는 몫(숫자 폭 단위). Excel 의 5px 을 7px 숫자로 나눈 값이다. */
        private const val PADDING_CHARS = 5.0 / 7.0

        /** `td` 의 좌우 여백(CSS 의 `padding:0 3px`, 6px = 4.5pt). 넘치는 글자 상자의 폭에서 뺀다. */
        private const val CELL_PADDING_PT = 4.5

        private const val KIND_NONE: Byte = 0
        private const val KIND_TEXT: Byte = 1
        private const val KIND_NUMBER: Byte = 2

        /** 참·거짓·오류 — 가운데 맞춤. */
        private const val KIND_CENTER: Byte = 3

        private val ISO = Regex("""(\d{4})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2})(?::(\d{2})(\.\d{1,9})?)?)?(?:Z|[+-]\d{2}:?\d{2})?""")

        /**
         * `t="d"` 의 ISO 8601 날짜를 일련번호로. 시간대는 버린다(셀에는 벽시계 값이 보인다).
         * 1900 체계에서 1900-03-01 앞의 날은 윤년 버그만큼 하나 당긴다.
         */
        fun isoSerial(s: String, date1904: Boolean): Double? {
            val m = ISO.matchEntire(s.trim()) ?: return null
            val g = m.groupValues
            val date = try {
                LocalDate.of(g[1].toInt(), g[2].toInt(), g[3].toInt())
            } catch (e: DateTimeException) {
                return null
            }
            val epoch = date.toEpochDay()
            val days = if (date1904) {
                epoch - LocalDate.of(1904, 1, 1).toEpochDay()
            } else {
                val d = epoch - LocalDate.of(1899, 12, 30).toEpochDay()
                if (d < 61) d - 1 else d
            }
            if (days < 0) return null
            val h = g[4].toIntOrNull() ?: 0
            val mi = g[5].toIntOrNull() ?: 0
            val sec = g[6].toIntOrNull() ?: 0
            val frac = g[7].takeIf { it.isNotEmpty() }?.toDoubleOrNull() ?: 0.0
            if (h > 23 || mi > 59 || sec > 60) return null
            return days + (h * 3600 + mi * 60 + sec + frac) / 86400.0
        }
    }
}
