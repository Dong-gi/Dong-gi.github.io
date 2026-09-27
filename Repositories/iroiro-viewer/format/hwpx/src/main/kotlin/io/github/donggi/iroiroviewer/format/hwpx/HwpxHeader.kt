package io.github.donggi.iroiroviewer.format.hwpx

import io.github.donggi.iroiroviewer.format.html.HancomNumbers
import io.github.donggi.iroiroviewer.safety.ParseLimitExceededException
import io.github.donggi.iroiroviewer.safety.ParseLimits
import org.xmlpull.v1.XmlPullParser

/**
 * 글자 모양 하나(`hh:charPr`). 흐름 렌더가 쓰는 것만 든다.
 *
 * @param height 글자 크기, HWPUNIT(**1000 = 10pt**, 스키마의 설명). 한글 글자의 상대 크기(`hh:relSz@hangul`)를 곱한 값이다.
 * @param underline 밑줄의 자리 — [LINE_NONE]·[LINE_BELOW]·[LINE_ABOVE]. 가운데 줄(`CENTER`)은 취소선이다([strike]).
 * @param strike 취소선. `hh:strikeout` 은 거의 모든 글자 모양에 적혀 있으므로 있다는 것만으로 켜지 않는다 —
 *   `shape="NONE"` 과 `"3D"` 는 꺼짐으로 본다(아래 [HwpxHeaderParser] 의 주석).
 * @param effect 외곽선·그림자·양각·음각. 흐름 렌더가 그리지 않으므로 센다.
 */
internal class CharFormat(
    val height: Int?,
    val color: String?,
    val shade: String?,
    val bold: Boolean,
    val italic: Boolean,
    val underline: Int,
    val strike: Boolean,
    val superscript: Boolean,
    val subscript: Boolean,
    val effect: Boolean,
    val font: String?,
) {
    companion object {
        val DEFAULT = CharFormat(1000, null, null, false, false, LINE_NONE, false, false, false, false, null)

        /** 밑줄 없음. 값은 HWP 5.0 의 밑줄 자리(`CharShape.underlinePos` — 1 아래, 3 위)와 같게 두었다. */
        const val LINE_NONE = 0

        /** 글자 아래 줄(`BOTTOM`) — `underline`. */
        const val LINE_BELOW = 1

        /** 글자 위 줄(`TOP`) — `overline`. */
        const val LINE_ABOVE = 3
    }
}

/**
 * 문단 모양 하나(`hh:paraPr`). 길이는 **pt** 로 바꿔 둔다.
 *
 * @param headingType `NONE`·`OUTLINE`(개요)·`NUMBER`(번호 문단)·`BULLET`(글머리표).
 * @param headingIdRef 번호·글머리표의 id. 개요는 0 이고 구역의 `outlineShapeIDRef` 를 쓴다.
 * @param headingLevel 수준, **0 부터**(번호 정의의 `paraHead@level` 은 1 부터다).
 */
internal class ParaFormat(
    val align: String?,
    val left: Double,
    val right: Double,
    val indent: Double,
    val before: Double,
    val after: Double,
    val lineHeight: Double?,
    val headingType: String,
    val headingIdRef: Int,
    val headingLevel: Int,
) {
    companion object {
        val DEFAULT = ParaFormat(null, 0.0, 0.0, 0.0, 0.0, 0.0, null, "NONE", 0, 0)
    }
}

/**
 * 번호 정의의 수준 하나(`hh:paraHead`). [text] 는 `^1.` 같은 모양 문자열, [shape] 는 번호 모양(`HancomNumbers` 의 값 —
 * `numFormat` 의 이름을 [HancomNumbers.shapeOf] 로 옮긴 것).
 */
internal class LevelDef(val start: Int, val shape: Int, val text: String)

/**
 * 번호 정의 하나(`hh:numbering`). 수준은 0..9(= `paraHead@level` 1..10).
 *
 * [starts]·[shapes] 는 수준마다의 시작 번호와 모양이다 — **없는 수준은 1 과 아라비아 숫자**(HWP 5.0 의 `NumberingDef` 와 같다).
 * `^n` 경로가 적히지 않은 위 수준을 가리킬 때 쓴다.
 */
internal class NumberingDef(val id: Int, val levels: Array<LevelDef?>) {
    val starts: IntArray = IntArray(HwpxLimits.NUMBER_LEVELS) { levels.getOrNull(it)?.start ?: 1 }
    val shapes: IntArray = IntArray(HwpxLimits.NUMBER_LEVELS) { levels.getOrNull(it)?.shape ?: HancomNumbers.DIGIT }
}

/** 테두리·배경 하나(`hh:borderFill`). 표의 칸이 쓴다. */
internal class BorderFill(val background: String?, val noBorder: Boolean)

/** 스타일 하나(`hh:style`). */
internal class StyleDef(val name: String, val paraPr: Int?, val charPr: Int?)

/**
 * `Contents/header.xml` 을 읽은 것 — 본문이 id 로 가리키는 표들(`hh:refList`).
 *
 * **id 는 `@id` 속성이다**, 목록 안의 차례가 아니다(조사 노트의 교정). 실물은 둘이 같지만 손으로 고친
 * 문서는 어긋난다.
 */
internal class HwpxHeader(
    val charFormats: Map<Int, CharFormat>,
    val paraFormats: Map<Int, ParaFormat>,
    val numberings: Map<Int, NumberingDef>,
    val bullets: Map<Int, String>,
    val borderFills: Map<Int, BorderFill>,
    val styles: Map<Int, StyleDef>,
) {
    /**
     * 문서의 바탕 글자 모양 — '바탕글' 스타일(id 0)의 것, 없으면 글자 모양 0. **문단 요소**의 글자 크기 %를 여기에 잰다 —
     * 글자 덩이는 문단의 바탕 글자 모양에 잰다(13단계 짝 대조, `HwpxWalker.baseCss`·`runCss`).
     */
    val defaultChar: CharFormat by lazy {
        styles[0]?.charPr?.let { charFormats[it] } ?: charFormats[0] ?: CharFormat.DEFAULT
    }

    val defaultHeight: Int get() = defaultChar.height?.takeIf { it > 0 } ?: 1000

    fun char(id: Int?): CharFormat = id?.let { charFormats[it] } ?: defaultChar

    fun para(id: Int?): ParaFormat = id?.let { paraFormats[it] } ?: ParaFormat.DEFAULT

    companion object {
        val EMPTY = HwpxHeader(emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap())
    }
}

/**
 * `header.xml` 을 흘려 읽는다.
 *
 * ## `hp:switch` — 어느 가지를 읽는가
 *
 * 문단 모양의 여백·줄 간격은 `hp:switch` 로 두 벌 적혀 있다 — `hp:case hp:required-namespace=".../2016/HwpUnitChar"`
 * 와 `hp:default`. 표본을 재 보니 **`case` 의 값이 `default` 의 정확히 절반**이다(여백·들여쓰기·문단 간격). 어느
 * 쪽이 참인지는 한국 정책브리핑의 바로보기(Synap 변환기, 독립 오라클)로 가렸다: K26 의 '□ 구인인원 및 채용인원'
 * 문단은 `case` 가 내어쓰기 -3790·위 간격 1000, `default` 가 -7580·2000 인데 오라클은 `text-indent:-37.9pt;
 * margin-top:10.0pt` 다 — **`case` 가 HWPUNIT(1/100 pt)** 이고 `default` 는 그 두 배(HWP 5.0 의 문단 모양이
 * 쓰는 단위)다. `hp:switch` 가 없는 옛 파일(K28, xmlVersion 1.2)은 **값이 두 배인 쪽**을 곧바로 적는다 — 같은
 * 오라클에서 -4216 이 -21.1pt 였다. 그래서 `case` 를 알아들으면 `case`(÷100), 아니면 `default`·직접 값(÷200).
 *
 * ## 취소선
 *
 * 표본 아홉의 글자 모양 2,243개 가운데 560개(25%, K26 은 447개 중 385개)가 `hh:strikeout shape="3D"` 이고, 그중에는 '바탕글' 의 글자 모양도
 * 있다. 그것을 취소선으로 그리면 본문 전체에 줄이 그어진다. 그래서 `NONE`·`3D` 는 꺼짐으로 본다.
 * '꺼진 취소선의 모양 값이 남은 것' 이라는 처음의 짐작은 **틀렸다** — 같은 보도자료의 HWP(K19)와 번호마다 대조하니 HWP
 * 5.0 쪽도 '여부' 비트가 켜져 있었다(여부 1 · 밑줄 자리 가운데 · 모양 15 = 3D 단선). 두 포맷 모두 '켜짐' 으로 적혀
 * 있는데 한글도 바로보기도 줄을 긋지 않는다. 그래서 두 변환기가 **3D 모양 자체를 꺼짐으로** 본다(`Hwp5DocInfo` 의
 * `CharShape.strike`). 왜 그런지는 확인하지 못했다.
 *
 * ## 밑줄의 자리와 상대 크기
 *
 * `hh:underline@type` 은 줄의 **자리**다 — `BOTTOM` 은 밑줄, `TOP` 은 윗줄, `CENTER` 는 취소선. 처음에는 `NONE` 이 아니면
 * 전부 밑줄로 그려 윗줄·가운데 줄이 밑줄이 되었다(13단계 짝 대조). HWP 5.0 의 밑줄 자리(1 아래·2 가운데·3 위)와 같게 옮기고,
 * 가운데 줄도 HWP 처럼 **취소선 모양이 3D 면 긋지 않는다**(`CharShape.strike` 의 식 그대로).
 *
 * `hh:relSz@hangul` 은 한글 글자의 상대 크기(%)다. HWP 5.0 의 `CharShape.sizeCentiPt` 처럼 크기에 곱하고, **10–250 밖의 값은
 * 100 으로** 본다(자르지 않는다 — HWP 쪽 식과 같게). 표본의 글자 모양은 전부 100 이라 시험 파일로만 확인했다.
 */
internal object HwpxHeaderParser {

    /** 알아듣는 `hp:switch` 가지. */
    const val HWPUNITCHAR_NS = "http://www.hancom.co.kr/hwpml/2016/HwpUnitChar"

    /** `case` 의 값은 HWPUNIT(1pt = 100), `default`·직접 값은 그 두 배. */
    private const val UNITS_PER_PT_CASE = 100.0
    private const val UNITS_PER_PT_LEGACY = 200.0

    fun parse(p: XmlPullParser, limits: ParseLimits, checkCancel: () -> Unit): HwpxHeader {
        if (!HwpxXml.toRoot(p, limits)) return HwpxHeader.EMPTY
        val fonts = HashMap<Int, String>()
        val chars = HashMap<Int, CharFormat>()
        val paras = HashMap<Int, ParaFormat>()
        val numberings = HashMap<Int, NumberingDef>()
        val bullets = HashMap<Int, String>()
        val fills = HashMap<Int, BorderFill>()
        val styles = HashMap<Int, StyleDef>()
        var count = 0
        fun counted() {
            if (++count > HwpxLimits.MAX_HEADER_ITEMS) {
                throw ParseLimitExceededException("maxHeaderItems", "머리 항목 ${count}개")
            }
            if (count and 255 == 0) checkCancel()
        }
        // `hh:head` > `hh:refList` > 목록들. 목록 이름으로 들어가고, 모르는 것은 건너뛴다.
        HwpxXml.eachChild(p, limits) { top ->
            if (top != "refList") {
                HwpxXml.skip(p, limits)
                return@eachChild
            }
            HwpxXml.eachChild(p, limits) { list ->
                when (list) {
                    "fontfaces" -> HwpxXml.eachChild(p, limits) { face ->
                        // 글꼴 id 는 **언어마다 따로** 0 부터다. 글자 모양은 한글 글꼴(`fontRef@hangul`)을 쓴다 —
                        // 한국어 문서의 본문 글꼴이 그것이다.
                        if (face == "fontface" && HwpxXml.attr(p, "lang").equals("HANGUL", ignoreCase = true)) {
                            HwpxXml.eachChild(p, limits) { font ->
                                if (font == "font") {
                                    val id = HwpxXml.int(p, "id")
                                    val name = HwpxXml.attr(p, "face")
                                    if (id != null && name != null) fonts.putIfAbsent(id, name)
                                    counted()
                                }
                                HwpxXml.skip(p, limits)
                            }
                        } else {
                            HwpxXml.skip(p, limits)
                        }
                    }
                    "charProperties" -> HwpxXml.eachChild(p, limits) { name ->
                        if (name == "charPr") {
                            val id = HwpxXml.int(p, "id")
                            val cf = readCharPr(p, limits, fonts)
                            if (id != null) chars.putIfAbsent(id, cf)
                            counted()
                        } else {
                            HwpxXml.skip(p, limits)
                        }
                    }
                    "paraProperties" -> HwpxXml.eachChild(p, limits) { name ->
                        if (name == "paraPr") {
                            val id = HwpxXml.int(p, "id")
                            val pf = readParaPr(p, limits)
                            if (id != null) paras.putIfAbsent(id, pf)
                            counted()
                        } else {
                            HwpxXml.skip(p, limits)
                        }
                    }
                    "numberings" -> HwpxXml.eachChild(p, limits) { name ->
                        if (name == "numbering") {
                            val id = HwpxXml.int(p, "id")
                            val levels = readParaHeads(p, limits)
                            if (id != null) numberings.putIfAbsent(id, NumberingDef(id, levels))
                            counted()
                        } else {
                            HwpxXml.skip(p, limits)
                        }
                    }
                    "bullets" -> HwpxXml.eachChild(p, limits) { name ->
                        if (name == "bullet") {
                            val id = HwpxXml.int(p, "id")
                            val ch = HwpxXml.attr(p, "char")
                            if (id != null) bullets.putIfAbsent(id, ch?.take(4).orEmpty())
                            counted()
                        }
                        HwpxXml.skip(p, limits)
                    }
                    "borderFills" -> HwpxXml.eachChild(p, limits) { name ->
                        if (name == "borderFill") {
                            val id = HwpxXml.int(p, "id")
                            val bf = readBorderFill(p, limits)
                            if (id != null) fills.putIfAbsent(id, bf)
                            counted()
                        } else {
                            HwpxXml.skip(p, limits)
                        }
                    }
                    "styles" -> HwpxXml.eachChild(p, limits) { name ->
                        if (name == "style") {
                            val id = HwpxXml.int(p, "id")
                            val sd = StyleDef(
                                name = HwpxXml.attr(p, "name").orEmpty().take(200),
                                paraPr = HwpxXml.int(p, "paraPrIDRef"),
                                charPr = HwpxXml.int(p, "charPrIDRef"),
                            )
                            if (id != null) styles.putIfAbsent(id, sd)
                            counted()
                        }
                        HwpxXml.skip(p, limits)
                    }
                    else -> HwpxXml.skip(p, limits)
                }
            }
        }
        return HwpxHeader(chars, paras, numberings, bullets, fills, styles)
    }

    private fun readCharPr(p: XmlPullParser, limits: ParseLimits, fonts: Map<Int, String>): CharFormat {
        val baseHeight = HwpxXml.int(p, "height")
        val color = HwpxXml.color(HwpxXml.attr(p, "textColor"))
        val shade = HwpxXml.color(HwpxXml.attr(p, "shadeColor"))
        var bold = false
        var italic = false
        var underline = CharFormat.LINE_NONE
        var centerLine = false
        var strikeShape: String? = null
        var sup = false
        var sub = false
        var effect = false
        var font: String? = null
        var relative = 100
        HwpxXml.eachChild(p, limits) { name ->
            when (name) {
                "fontRef" -> font = HwpxXml.int(p, "hangul")?.let { fonts[it] }
                "relSz" -> relative = HwpxXml.int(p, "hangul") ?: 100
                // 굵게·기울임은 **있다는 것**이 켜짐이다(빈 요소).
                "bold" -> bold = true
                "italic" -> italic = true
                "underline" -> when (HwpxXml.attr(p, "type")?.uppercase()) {
                    "BOTTOM" -> underline = CharFormat.LINE_BELOW
                    "TOP" -> underline = CharFormat.LINE_ABOVE
                    "CENTER" -> centerLine = true
                }
                "strikeout" -> strikeShape = HwpxXml.attr(p, "shape")
                "supscript" -> sup = true
                "subscript" -> sub = true
                "outline" -> if (HwpxXml.attr(p, "type").let { it != null && !it.equals("NONE", true) }) effect = true
                "shadow" -> if (HwpxXml.attr(p, "type").let { it != null && !it.equals("NONE", true) }) effect = true
                "emboss", "engrave" -> effect = true
            }
            HwpxXml.skip(p, limits)
        }
        // HWP 5.0 의 `CharShape.strike` 와 같은 식 — (취소선이 켜졌거나 가운데 줄) 이고 모양이 3D 가 아니다.
        val strikeOn = strikeShape != null && !strikeShape.equals("NONE", true)
        val strike = (strikeOn || centerLine) && !strikeShape.equals("3D", true)
        // HWP 5.0 의 `CharShape.sizeCentiPt` 와 같은 식 — 10–250 밖의 상대 크기는 100 이다.
        val rel = if (relative in 10..250) relative else 100
        val height = baseHeight?.let { (it.toLong() * rel / 100).coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt() }
        return CharFormat(height, color, shade, bold, italic, underline, strike, sup, sub, effect, font)
    }

    /** 여백 한 벌 — 가지 하나(`case`·`default`·직접)에서 읽은 날것. */
    private class Spacing {
        var intent: Int? = null
        var left: Int? = null
        var right: Int? = null
        var prev: Int? = null
        var next: Int? = null
        var lineType: String? = null
        var lineValue: Int? = null
        var any = false
    }

    private fun readParaPr(p: XmlPullParser, limits: ParseLimits): ParaFormat {
        var align: String? = null
        var headingType = "NONE"
        var headingIdRef = 0
        var headingLevel = 0
        val direct = Spacing()
        var chosen: Spacing? = null
        var chosenIsCase = false
        HwpxXml.eachChild(p, limits) { name ->
            when (name) {
                "align" -> {
                    align = HwpxXml.attr(p, "horizontal")
                    HwpxXml.skip(p, limits)
                }
                "heading" -> {
                    headingType = HwpxXml.attr(p, "type")?.uppercase() ?: "NONE"
                    headingIdRef = HwpxXml.int(p, "idRef") ?: 0
                    headingLevel = (HwpxXml.int(p, "level") ?: 0).coerceIn(0, HwpxLimits.NUMBER_LEVELS - 1)
                    HwpxXml.skip(p, limits)
                }
                "margin", "lineSpacing" -> readSpacing(p, limits, name, direct)
                "switch" -> {
                    val (s, isCase) = readSwitch(p, limits)
                    if (s != null && chosen == null) {
                        chosen = s
                        chosenIsCase = isCase
                    }
                }
                else -> HwpxXml.skip(p, limits)
            }
        }
        val src = chosen?.takeIf { it.any } ?: direct
        val perPt = if (chosen != null && chosenIsCase && src === chosen) UNITS_PER_PT_CASE else UNITS_PER_PT_LEGACY
        fun pt(v: Int?) = (v ?: 0) / perPt
        val lineHeight = if (src.lineType.equals("PERCENT", true)) src.lineValue?.let { it / 100.0 } else null
        return ParaFormat(
            align = align,
            left = pt(src.left),
            right = pt(src.right),
            indent = pt(src.intent),
            before = pt(src.prev),
            after = pt(src.next),
            lineHeight = lineHeight,
            headingType = headingType,
            headingIdRef = headingIdRef,
            headingLevel = headingLevel,
        )
    }

    /**
     * `hp:switch` 하나. 알아듣는 `case`(HwpUnitChar) 가 있으면 그것, 없으면 `default`. `case` 가 먼저 오므로
     * 한 번 훑어 고를 수 있다.
     *
     * @return 고른 가지의 여백과, 그것이 `case` 인가.
     */
    private fun readSwitch(p: XmlPullParser, limits: ParseLimits): Pair<Spacing?, Boolean> {
        var picked: Spacing? = null
        var isCase = false
        HwpxXml.eachChild(p, limits) { branch ->
            val take = when (branch) {
                "case" -> picked == null && HwpxXml.attr(p, "required-namespace") == HWPUNITCHAR_NS
                "default" -> picked == null
                else -> false
            }
            if (!take) {
                HwpxXml.skip(p, limits)
                return@eachChild
            }
            val s = Spacing()
            HwpxXml.eachChild(p, limits) { name ->
                if (name == "margin" || name == "lineSpacing") readSpacing(p, limits, name, s) else HwpxXml.skip(p, limits)
            }
            picked = s
            isCase = branch == "case"
        }
        return picked to isCase
    }

    private fun readSpacing(p: XmlPullParser, limits: ParseLimits, name: String, s: Spacing) {
        s.any = true
        if (name == "lineSpacing") {
            s.lineType = HwpxXml.attr(p, "type")
            s.lineValue = HwpxXml.int(p, "value")
            HwpxXml.skip(p, limits)
            return
        }
        // `hh:margin` 의 자식 `hc:intent`(한컴의 철자 — 들여쓰기)·`left`·`right`·`prev`·`next`.
        HwpxXml.eachChild(p, limits) { part ->
            val v = HwpxXml.int(p, "value")?.coerceIn(-MAX_UNITS, MAX_UNITS)
            when (part) {
                "intent", "indent" -> s.intent = v
                "left" -> s.left = v
                "right" -> s.right = v
                "prev" -> s.prev = v
                "next" -> s.next = v
            }
            HwpxXml.skip(p, limits)
        }
    }

    /** `hh:numbering` 의 `hh:paraHead` 들. 글자 내용이 번호 모양(`^1.`)이다. */
    private fun readParaHeads(p: XmlPullParser, limits: ParseLimits): Array<LevelDef?> {
        val levels = arrayOfNulls<LevelDef>(HwpxLimits.NUMBER_LEVELS)
        HwpxXml.eachChild(p, limits) { name ->
            if (name != "paraHead") {
                HwpxXml.skip(p, limits)
                return@eachChild
            }
            val level = HwpxXml.int(p, "level")
            // 시작 번호 0 은 0 이다(`HancomNumbers.Counters` — 처음에는 1 로 올려 보였다, 13단계 짝 대조).
            val start = (HwpxXml.int(p, "start") ?: 1).coerceIn(0, 100_000)
            val shape = HancomNumbers.shapeOf(HwpxXml.attr(p, "numFormat"))
            val text = HwpxXml.collectText(p, limits, MAX_FORMAT_CHARS)
            if (level != null && level in 1..HwpxLimits.NUMBER_LEVELS && levels[level - 1] == null) {
                levels[level - 1] = LevelDef(start, shape, text)
            }
        }
        return levels
    }

    private fun readBorderFill(p: XmlPullParser, limits: ParseLimits): BorderFill {
        var background: String? = null
        var sides = 0
        var none = 0
        HwpxXml.eachChild(p, limits) { name ->
            when (name) {
                "leftBorder", "rightBorder", "topBorder", "bottomBorder" -> {
                    sides++
                    if (HwpxXml.attr(p, "type").equals("NONE", ignoreCase = true)) none++
                    HwpxXml.skip(p, limits)
                }
                "fillBrush" -> HwpxXml.eachChild(p, limits) { brush ->
                    if (brush == "winBrush" && background == null) {
                        // `alpha` 가 있어도 흐름 렌더는 불투명한 색으로 칠한다. 무늬(`hatchStyle`)는 그리지 않는다.
                        background = HwpxXml.color(HwpxXml.attr(p, "faceColor"))
                    }
                    HwpxXml.skip(p, limits)
                }
                else -> HwpxXml.skip(p, limits)
            }
        }
        return BorderFill(background, sides == 4 && none == 4)
    }

    private const val MAX_FORMAT_CHARS = 64
    private const val MAX_UNITS = 10_000_000
}
