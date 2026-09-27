package io.github.donggi.iroiroviewer.format.xlsx

import io.github.donggi.iroiroviewer.format.html.CssValues
import io.github.donggi.iroiroviewer.format.opc.OoxmlXml
import io.github.donggi.iroiroviewer.safety.ParseLimits
import io.github.donggi.iroiroviewer.safety.nextGuarded
import org.xmlpull.v1.XmlPullParser

/** 글꼴 하나(`fonts/font`). 색은 이미 푼 `#rrggbb`. */
internal data class FontSpec(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
    val sizePt: Double? = null,
    val color: String? = null,
    val name: String? = null,
)

/** 테두리 하나. 값은 이미 검사를 지난 CSS 선언 값(`1px solid #000000`)이거나 null. */
internal data class BorderSpec(val left: String?, val right: String?, val top: String?, val bottom: String?)

/** 셀 서식 하나(`cellXfs/xf`). */
internal data class XfSpec(
    val numFmtId: Int = 0,
    val fontId: Int = 0,
    val fillId: Int = 0,
    val borderId: Int = 0,
    val horizontal: String? = null,
    val vertical: String? = null,
    val wrap: Boolean = false,
    val indent: Int = 0,
)

/**
 * `styles.xml` — 셀 서식(`cellXfs`)과 그것이 가리키는 글꼴·채우기·테두리·표시 형식.
 *
 * ## 무엇을 버리는가
 *
 * 무늬 채우기(`gray125` 따위)는 버리고 **단색(`solid`)만** 칠한다 — 무늬를 CSS 로 흉내 내면 글자가
 * 읽히지 않는다. 그라데이션은 첫 색 하나로 칠한다. 글자 회전·축소 맞춤·보호는 버린다.
 * 셀 스타일(`cellStyleXfs`)은 읽지 않는다 — `cellXfs` 가 이미 풀린 값을 들고 있다.
 *
 * ## 조건부 서식(`dxfs`)은 읽지 않는다
 *
 * 그 안에도 `font`·`fill`·`numFmt` 가 있으므로 **뿌리의 직계 자식만** 본다. 이름으로만 찾으면
 * 조건부 서식의 글꼴이 글꼴 표에 섞여 번호가 전부 밀린다.
 */
internal class XlsxStyles private constructor(
    private val numFmtCodes: Map<Int, String>,
    private val fonts: List<FontSpec>,
    private val fills: List<String?>,
    private val borders: List<BorderSpec?>,
    private val xfs: List<XfSpec>,
) {

    /** 기본 글꼴 — 첫 셀 서식의 글꼴('표준' 스타일). 칸마다 이것과 다른 것만 적는다. */
    val defaultFont: FontSpec = xfs.getOrNull(0)?.let { fonts.getOrNull(it.fontId) } ?: fonts.getOrNull(0) ?: FontSpec(sizePt = 11.0)

    private val formats = HashMap<Int, NumberFormat>()
    private val fullCss = HashMap<Int, List<Pair<String, String>>>()
    private val emptyCss = HashMap<Int, List<Pair<String, String>>>()

    fun xf(index: Int): XfSpec? = xfs.getOrNull(index)

    /** 이 셀 서식의 표시 형식. 모르는 번호는 일반 형식. */
    fun numberFormat(xfIndex: Int): NumberFormat {
        val id = xfs.getOrNull(xfIndex)?.numFmtId ?: 0
        return formats.getOrPut(id) {
            val code = numFmtCodes[id] ?: NumberFormat.builtinCode(id)
            if (code == null) NumberFormat.GENERAL else NumberFormat.parse(code)
        }
    }

    /** 이 셀 서식의 글꼴 크기(pt). 모르면 null. */
    fun fontSizePt(xfIndex: Int): Double? = xfs.getOrNull(xfIndex)?.let { fonts.getOrNull(it.fontId)?.sizePt }

    /** 글자가 줄을 바꾸는가(`wrapText`, 또는 양쪽 맞춤·균등 분할). */
    fun wraps(xfIndex: Int): Boolean {
        val xf = xfs.getOrNull(xfIndex) ?: return false
        return xf.wrap || xf.horizontal == "justify" || xf.horizontal == "distributed"
    }

    /** 가로 맞춤이 적혀 있는가(`general` 이 아닌가). 적혀 있으면 값의 종류로 맞추지 않는다. */
    fun horizontal(xfIndex: Int): String? = xfs.getOrNull(xfIndex)?.horizontal?.takeIf { it != "general" }

    /**
     * 글자가 있는 칸의 인라인 스타일(검사를 지난 값만). 기본 글꼴과 다른 것만 적는다.
     *
     * **표에 없는 번호는 캐시에 넣지 않는다.** 번호는 셀의 `s` 로 문서가 적는 값이라, 시트마다 서로 다른
     * 가짜 번호 20만 개를 적으면 문서를 연 동안 캐시만 끝없이 자란다. 표 안의 번호는 [xfs] 크기로 묶인다.
     */
    fun cellCss(xfIndex: Int): List<Pair<String, String>> =
        if (xfIndex !in xfs.indices) emptyList() else fullCss.getOrPut(xfIndex) { build(xfIndex, full = true) }

    /** 빈 칸의 인라인 스타일 — 칠과 테두리만. 글꼴은 그릴 글자가 없다. 캐시는 [cellCss] 와 같은 규칙이다. */
    fun emptyCellCss(xfIndex: Int): List<Pair<String, String>> =
        if (xfIndex !in xfs.indices) emptyList() else emptyCss.getOrPut(xfIndex) { build(xfIndex, full = false) }

    /** 캐시에 든 스타일 수. 시험이 캐시가 문서의 번호에 끌려 자라지 않는지 본다. */
    internal val cachedCssCount: Int get() = fullCss.size + emptyCss.size

    /**
     * 이 셀 서식의 오른쪽 테두리(CSS 값). 병합의 오른쪽 가장자리 칸에서 읽는다 — Excel 은 병합의
     * 테두리를 가장자리 칸마다 따로 적는다([SheetRenderer] 의 주석).
     */
    fun borderRight(xfIndex: Int): String? = xfs.getOrNull(xfIndex)?.let { borders.getOrNull(it.borderId)?.right }

    private fun build(xfIndex: Int, full: Boolean): List<Pair<String, String>> {
        val xf = xfs.getOrNull(xfIndex) ?: return emptyList()
        val out = ArrayList<Pair<String, String>>(6)
        fun add(prop: String, value: String?) {
            if (value != null) out.add(prop to value)
        }
        if (full) {
            val f = fonts.getOrNull(xf.fontId)
            val d = defaultFont
            if (f != null) {
                if (f.bold != d.bold) add("font-weight", if (f.bold) "bold" else "normal")
                if (f.italic != d.italic) add("font-style", if (f.italic) "italic" else "normal")
                if (f.underline != d.underline || f.strike != d.strike) {
                    val deco = listOfNotNull(if (f.underline) "underline" else null, if (f.strike) "line-through" else null)
                    add("text-decoration", if (deco.isEmpty()) "none" else deco.joinToString(" "))
                }
                if (f.sizePt != null && f.sizePt != d.sizePt) add("font-size", CssValues.pt(f.sizePt, 1.0, 409.0))
                // 색이 없는 글꼴은 '자동' — 창의 글자색(검정)이다. 기본 글꼴과 견줄 때도 그렇게 본다.
                val color = f.color ?: AUTO_TEXT
                if (color != (d.color ?: AUTO_TEXT)) add("color", color)
                if (f.name != null && f.name != d.name) add("font-family", CssValues.fontFamily(f.name)?.let { "$it,sans-serif" })
            }
            add(
                "text-align",
                when (xf.horizontal) {
                    "left", "fill" -> "left"
                    "center", "centerContinuous" -> "center"
                    "right" -> "right"
                    "justify", "distributed" -> "justify"
                    else -> null
                },
            )
            add(
                "vertical-align",
                when (xf.vertical) {
                    "top" -> "top"
                    "center", "justify", "distributed" -> "middle"
                    else -> null
                },
            )
            if (xf.indent > 0) {
                // Excel 의 들여쓰기 한 단계는 대략 글자 세 개 폭이다.
                add(if (xf.horizontal == "right") "padding-right" else "padding-left", CssValues.pt(xf.indent * 7.5 + 2.0, 0.0, 300.0))
            }
        }
        add("background-color", fills.getOrNull(xf.fillId))
        borders.getOrNull(xf.borderId)?.let { b ->
            add("border-left", b.left)
            add("border-right", b.right)
            add("border-top", b.top)
            add("border-bottom", b.bottom)
        }
        return out
    }

    companion object {
        private const val AUTO_TEXT = "#000000"

        /** `styles.xml` 이 없거나 읽지 못했을 때. 전부 일반 형식·기본 글꼴이다. */
        val EMPTY = XlsxStyles(emptyMap(), emptyList(), emptyList(), emptyList(), emptyList())

        /**
         * 읽는다. 깨진 파일이면 던진다 — 부르는 쪽이 [EMPTY] 로 돌아간다(서식을 잃을 뿐 값은 보인다).
         *
         * @param theme Excel 번호 순서의 테마 색([XlsxColors] 의 주석).
         */
        fun read(p: XmlPullParser, limits: ParseLimits, theme: IntArray): XlsxStyles {
            if (!XlsxXml.toRoot(p, limits)) return EMPTY
            val numFmts = HashMap<Int, String>()
            val fonts = ArrayList<RawFont>()
            val fills = ArrayList<RawFill>()
            val borders = ArrayList<RawBorder>()
            val xfs = ArrayList<XfSpec>()
            var palette: IntArray? = null
            val cap = XlsxLimits.MAX_STYLE_ENTRIES

            XlsxXml.children(p, limits) { section ->
                when (section) {
                    "numFmts" -> XlsxXml.children(p, limits) { name ->
                        if (name == "numFmt" && numFmts.size < XlsxLimits.MAX_NUM_FMTS) {
                            val id = OoxmlXml.int(p, "numFmtId")
                            val code = OoxmlXml.attr(p, "formatCode")
                            if (id != null && code != null) numFmts[id] = code
                        }
                    }
                    "fonts" -> XlsxXml.children(p, limits) { name ->
                        if (name == "font" && fonts.size < cap) fonts.add(readFont(p, limits))
                    }
                    "fills" -> XlsxXml.children(p, limits) { name ->
                        if (name == "fill" && fills.size < cap) fills.add(readFill(p, limits))
                    }
                    "borders" -> XlsxXml.children(p, limits) { name ->
                        if (name == "border" && borders.size < cap) borders.add(readBorder(p, limits))
                    }
                    "cellXfs" -> XlsxXml.children(p, limits) { name ->
                        if (name == "xf" && xfs.size < cap) xfs.add(readXf(p, limits))
                    }
                    "colors" -> XlsxXml.children(p, limits) { name ->
                        if (name == "indexedColors") palette = readPalette(p, limits)
                    }
                    else -> Unit
                }
            }

            val pal = palette ?: XlsxColors.DEFAULT_PALETTE
            fun color(spec: ColorSpec?): String? = XlsxColors.resolve(spec, theme, pal)
            return XlsxStyles(
                numFmtCodes = numFmts,
                fonts = fonts.map { f ->
                    FontSpec(f.bold, f.italic, f.underline, f.strike, f.sizePt, color(f.color), f.name)
                },
                fills = fills.map { f -> color(f.color) },
                borders = borders.map { b ->
                    BorderSpec(
                        borderCss(b.left, color(b.leftColor)),
                        borderCss(b.right, color(b.rightColor)),
                        borderCss(b.top, color(b.topColor)),
                        borderCss(b.bottom, color(b.bottomColor)),
                    ).takeIf { it.left != null || it.right != null || it.top != null || it.bottom != null }
                },
                xfs = xfs,
            )
        }

        /** 테두리 모양 → CSS. Excel 의 열세 가지를 굵기와 선 모양 넷으로 줄인다. */
        private fun borderCss(style: String?, color: String?): String? {
            val shape = when (style) {
                null, "", "none" -> return null
                "thin" -> "1px solid"
                "medium" -> "2px solid"
                "thick" -> "3px solid"
                "double" -> "3px double"
                "dashed", "dashDot", "dashDotDot" -> "1px dashed"
                "mediumDashed", "mediumDashDot", "mediumDashDotDot", "slantDashDot" -> "2px dashed"
                "dotted", "hair" -> "1px dotted"
                else -> "1px solid"
            }
            return "$shape ${color ?: "#000000"}"
        }

        private class RawFont(
            val bold: Boolean,
            val italic: Boolean,
            val underline: Boolean,
            val strike: Boolean,
            val sizePt: Double?,
            val color: ColorSpec?,
            val name: String?,
        )

        private class RawFill(val color: ColorSpec?)

        private class RawBorder(
            val left: String?,
            val leftColor: ColorSpec?,
            val right: String?,
            val rightColor: ColorSpec?,
            val top: String?,
            val topColor: ColorSpec?,
            val bottom: String?,
            val bottomColor: ColorSpec?,
        )

        private fun readFont(p: XmlPullParser, limits: ParseLimits): RawFont {
            var bold = false
            var italic = false
            var underline = false
            var strike = false
            var size: Double? = null
            var color: ColorSpec? = null
            var name: String? = null
            XlsxXml.children(p, limits) { tag ->
                when (tag) {
                    "b" -> bold = OoxmlXml.onOff(p)
                    "i" -> italic = OoxmlXml.onOff(p)
                    "strike" -> strike = OoxmlXml.onOff(p)
                    // `<u/>` 는 한 줄 밑줄, `val="none"` 만 꺼짐이다.
                    "u" -> underline = OoxmlXml.attr(p, "val") != "none"
                    "sz" -> size = OoxmlXml.attr(p, "val")?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
                    "color" -> color = ColorSpec.read(p)
                    "name", "rFont" -> name = OoxmlXml.attr(p, "val")
                    else -> Unit
                }
            }
            return RawFont(bold, italic, underline, strike, size, color, name)
        }

        private fun readFill(p: XmlPullParser, limits: ParseLimits): RawFill {
            var color: ColorSpec? = null
            XlsxXml.children(p, limits) { tag ->
                when (tag) {
                    "patternFill" -> {
                        val solid = OoxmlXml.attr(p, "patternType") == "solid"
                        XlsxXml.children(p, limits) { c ->
                            if (c == "fgColor" && solid) color = ColorSpec.read(p)
                        }
                    }
                    "gradientFill" -> XlsxXml.children(p, limits) { stop ->
                        if (stop == "stop" && color == null) {
                            XlsxXml.children(p, limits) { c -> if (c == "color" && color == null) color = ColorSpec.read(p) }
                        }
                    }
                    else -> Unit
                }
            }
            return RawFill(color)
        }

        private fun readBorder(p: XmlPullParser, limits: ParseLimits): RawBorder {
            val styles = HashMap<String, String?>()
            val colors = HashMap<String, ColorSpec?>()
            XlsxXml.children(p, limits) { tag ->
                // 엄격·새 파일은 left/right 대신 start/end 를 쓴다.
                val side = when (tag) {
                    "left", "start" -> "left"
                    "right", "end" -> "right"
                    "top" -> "top"
                    "bottom" -> "bottom"
                    else -> null
                }
                if (side != null) {
                    styles[side] = OoxmlXml.attr(p, "style")
                    XlsxXml.children(p, limits) { c -> if (c == "color") colors[side] = ColorSpec.read(p) }
                }
            }
            return RawBorder(
                styles["left"], colors["left"], styles["right"], colors["right"],
                styles["top"], colors["top"], styles["bottom"], colors["bottom"],
            )
        }

        private fun readXf(p: XmlPullParser, limits: ParseLimits): XfSpec {
            val numFmtId = OoxmlXml.int(p, "numFmtId") ?: 0
            val fontId = OoxmlXml.int(p, "fontId") ?: 0
            val fillId = OoxmlXml.int(p, "fillId") ?: 0
            val borderId = OoxmlXml.int(p, "borderId") ?: 0
            var horizontal: String? = null
            var vertical: String? = null
            var wrap = false
            var indent = 0
            XlsxXml.children(p, limits) { tag ->
                if (tag == "alignment") {
                    horizontal = OoxmlXml.attr(p, "horizontal")
                    vertical = OoxmlXml.attr(p, "vertical")
                    wrap = OoxmlXml.attr(p, "wrapText")?.let { it == "1" || it.equals("true", true) } == true
                    indent = (OoxmlXml.int(p, "indent") ?: 0).coerceIn(0, 40)
                }
            }
            return XfSpec(numFmtId, fontId, fillId, borderId, horizontal, vertical, wrap, indent)
        }

        private fun readPalette(p: XmlPullParser, limits: ParseLimits): IntArray? {
            val out = XlsxColors.DEFAULT_PALETTE.copyOf()
            var i = 0
            XlsxXml.children(p, limits) { tag ->
                if (tag == "rgbColor" && i < out.size) {
                    CssValues.hexColor(OoxmlXml.attr(p, "rgb"))?.let { out[i] = it.substring(1).toInt(16) }
                    i++
                }
            }
            return if (i == 0) null else out
        }

        /**
         * `theme1.xml` 의 색 배합(`clrScheme`)을 **Excel 번호 순서**로. 없는 칸은 Office 기본값.
         * 첫 번째 `clrScheme` 만 본다 — `extraClrSchemeLst` 에 딸린 것은 쓰이지 않는 후보다.
         */
        fun readTheme(p: XmlPullParser, limits: ParseLimits): IntArray {
            val out = XlsxColors.DEFAULT_THEME.copyOf()
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && p.name == "clrScheme") {
                    XlsxXml.children(p, limits) { slotName ->
                        val slot = XlsxColors.THEME_SLOTS[slotName]
                        if (slot != null) {
                            XlsxXml.children(p, limits) { c ->
                                val v = when (c) {
                                    "srgbClr" -> OoxmlXml.attr(p, "val")
                                    "sysClr" -> OoxmlXml.attr(p, "lastClr") ?: when (OoxmlXml.attr(p, "val")) {
                                        "windowText" -> "000000"
                                        "window" -> "FFFFFF"
                                        else -> null
                                    }
                                    else -> null
                                }
                                CssValues.hexColor(v)?.let { out[slot] = it.substring(1).toInt(16) }
                            }
                        }
                    }
                    return out
                }
                ev = p.nextGuarded(limits)
            }
            return out
        }
    }
}
