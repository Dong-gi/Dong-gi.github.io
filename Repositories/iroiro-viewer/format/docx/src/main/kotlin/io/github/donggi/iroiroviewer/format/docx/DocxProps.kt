package io.github.donggi.iroiroviewer.format.docx

import io.github.donggi.iroiroviewer.format.html.CssValues
import io.github.donggi.iroiroviewer.format.opc.OoxmlXml
import io.github.donggi.iroiroviewer.safety.ParseLimits
import io.github.donggi.iroiroviewer.safety.nextGuarded
import org.xmlpull.v1.XmlPullParser

/**
 * 글자 속성(`w:rPr`) 한 벌. **null 은 '이 층이 정하지 않았다'** 는 뜻이다 — 문서 기본값 → 문단 스타일
 * 사슬 → 글자 스타일 사슬 → 직접 서식 순서로 [overlay] 해서 실제 값을 얻는다.
 *
 * 색·강조의 빈 문자열은 '정했는데 자동(없음)' 이다. null(정하지 않음)과 가르는 이유는 스타일이
 * 빨간색을 준 글자에 직접 서식이 `auto` 를 주면 **빨간색을 지워야** 하기 때문이다.
 *
 * 켜고 끄는 속성(굵게·기울임)은 명세상 스타일 사이에서 **뒤집기(toggle)** 로 겹치지만, 여기서는
 * 뒤의 층이 덮어쓴다. 흔한 경우(제목 스타일의 굵게를 직접 서식이 끄는 것)는 둘이 같은 답을 내고,
 * 갈리는 것은 굵은 글자 스타일을 굵은 문단 스타일 안에 쓴 드문 경우뿐이다.
 */
internal data class RunProps(
    val bold: Boolean? = null,
    val italic: Boolean? = null,
    val underline: Boolean? = null,
    val strike: Boolean? = null,
    /** `superscript`·`subscript`·`baseline`. */
    val vertAlign: String? = null,
    /** `#rrggbb`, 자동이면 빈 문자열. */
    val color: String? = null,
    /** `#rrggbb`, 없음이면 빈 문자열. */
    val highlight: String? = null,
    /** 글자 음영(`w:shd` 의 채움). `#rrggbb`, 자동이면 빈 문자열. */
    val shading: String? = null,
    /** 반 포인트 단위(`w:sz`). 22 가 11pt 다. */
    val sizeHalfPt: Int? = null,
    val caps: Boolean? = null,
    val smallCaps: Boolean? = null,
    /** 숨긴 글자(`w:vanish`). 워드도 기본으로 보이지 않는다. */
    val vanish: Boolean? = null,
    /** 검사를 지난 CSS `font-family` 값. */
    val font: String? = null,
    /** 그릴 수 없는 글자 효과(광선·그림자·반사·윤곽…)가 걸려 있다. */
    val effect: Boolean? = null,
) {
    /** [over] 가 정한 칸이 이긴다. */
    fun overlay(over: RunProps?): RunProps = if (over == null || over == EMPTY) this else RunProps(
        bold = over.bold ?: bold,
        italic = over.italic ?: italic,
        underline = over.underline ?: underline,
        strike = over.strike ?: strike,
        vertAlign = over.vertAlign ?: vertAlign,
        color = over.color ?: color,
        highlight = over.highlight ?: highlight,
        shading = over.shading ?: shading,
        sizeHalfPt = over.sizeHalfPt ?: sizeHalfPt,
        caps = over.caps ?: caps,
        smallCaps = over.smallCaps ?: smallCaps,
        vanish = over.vanish ?: vanish,
        font = over.font ?: font,
        effect = over.effect ?: effect,
    )

    companion object {
        val EMPTY = RunProps()
    }
}

/** `w:rPr` 하나를 읽은 것 — 글자 스타일 id 와 직접 서식. */
internal class RunPr(val styleId: String?, val props: RunProps)

/**
 * 문단 속성(`w:pPr`) 한 벌. 역시 null 은 '정하지 않았다' 다.
 *
 * 들여쓰기는 **트윕**(1/20pt)이다. 첫 줄 들여쓰기와 내어쓰기는 명세에서 서로를 배제하므로
 * 부호 있는 값 하나([indFirst] — 첫 줄이면 양수, 내어쓰기면 음수)로 든다. 그래야 스타일이 준
 * 첫 줄 들여쓰기를 직접 서식의 내어쓰기가 한 칸으로 덮는다.
 */
internal class ParaProps {
    var styleId: String? = null
    var numId: Int? = null
    var ilvl: Int? = null
    var outlineLvl: Int? = null
    var jc: String? = null
    var indLeft: Int? = null
    var indRight: Int? = null
    var indFirst: Int? = null
    var shading: String? = null

    /** 이 문단이 끝내는 구역(`pPr/sectPr`)에 머리글·바닥글이 걸려 있다. */
    var sectionHasHeaders: Boolean = false

    /**
     * 문단 표시(¶) 자체가 **지운 변경**이다(`pPr/rPr/del`·`moveFrom`). 워드의 최종본 보기에서 이 문단은 다음
     * 문단에 합쳐져 따로 서지 않는다 — 번호도 세지 않는다.
     */
    var markDeleted: Boolean = false
}

/**
 * `w:pPr`·`w:rPr`·`w:sectPr` 을 읽는 것. 스타일 표(`styles.xml`)와 본문이 **같은 함수**를 쓴다 —
 * 두 벌이면 스타일에서는 먹는 속성이 본문에서는 안 먹는 날이 온다.
 *
 * 고친 흔적(`w:pPrChange`·`w:rPrChange`)은 **옛 속성**이라 읽지 않는다. 모르는 요소는 건너뛰므로
 * 저절로 그렇게 된다.
 */
internal object DocxProps {

    /** [p] 는 `w:pPr` 의 시작 태그에 서 있다. 끝나면 그 끝 태그에 서 있다. */
    fun readPPr(p: XmlPullParser, limits: ParseLimits): ParaProps {
        val out = ParaProps()
        eachChild(p, limits) { name ->
            when (name) {
                "pStyle" -> {
                    out.styleId = OoxmlXml.attr(p, "val")
                    OoxmlXml.skip(p, limits)
                }
                "numPr" -> eachChild(p, limits) { n ->
                    when (n) {
                        "ilvl" -> out.ilvl = OoxmlXml.int(p, "val")
                        "numId" -> out.numId = OoxmlXml.int(p, "val")
                    }
                    OoxmlXml.skip(p, limits)
                }
                "outlineLvl" -> {
                    out.outlineLvl = OoxmlXml.int(p, "val")
                    OoxmlXml.skip(p, limits)
                }
                "jc" -> {
                    out.jc = OoxmlXml.attr(p, "val")
                    OoxmlXml.skip(p, limits)
                }
                "ind" -> {
                    out.indLeft = twips(p, "left") ?: twips(p, "start")
                    out.indRight = twips(p, "right") ?: twips(p, "end")
                    val hanging = twips(p, "hanging")
                    out.indFirst = if (hanging != null) -hanging else twips(p, "firstLine")
                    OoxmlXml.skip(p, limits)
                }
                "shd" -> {
                    out.shading = fill(p)
                    OoxmlXml.skip(p, limits)
                }
                "sectPr" -> out.sectionHasHeaders = readSectPr(p, limits)
                // 문단 표시의 글자 속성. 서식은 문단 스타일이 이미 주므로 **지운 변경인지**만 본다.
                "rPr" -> eachChild(p, limits) { r ->
                    if (r == "del" || r == "moveFrom") out.markDeleted = true
                    OoxmlXml.skip(p, limits)
                }
                else -> OoxmlXml.skip(p, limits)
            }
        }
        return out
    }

    /** [p] 는 `w:rPr` 의 시작 태그에 서 있다. */
    fun readRPr(p: XmlPullParser, limits: ParseLimits): RunPr {
        var styleId: String? = null
        var bold: Boolean? = null
        var italic: Boolean? = null
        var underline: Boolean? = null
        var strike: Boolean? = null
        var dstrike: Boolean? = null
        var vertAlign: String? = null
        var color: String? = null
        var highlight: String? = null
        var shading: String? = null
        var size: Int? = null
        var caps: Boolean? = null
        var smallCaps: Boolean? = null
        var vanish: Boolean? = null
        var font: String? = null
        var effect: Boolean? = null
        eachChild(p, limits) { name ->
            when (name) {
                "rStyle" -> styleId = OoxmlXml.attr(p, "val")
                "b" -> bold = OoxmlXml.onOff(p)
                "i" -> italic = OoxmlXml.onOff(p)
                "u" -> underline = OoxmlXml.attr(p, "val")?.let { it != "none" } ?: true
                "strike" -> strike = OoxmlXml.onOff(p)
                "dstrike" -> dstrike = OoxmlXml.onOff(p)
                "vertAlign" -> vertAlign = OoxmlXml.attr(p, "val")
                "color" -> color = OoxmlXml.attr(p, "val")?.let { if (it == "auto") "" else CssValues.hexColor(it) }
                "highlight" -> highlight = OoxmlXml.attr(p, "val")?.let { highlightColor(it) }
                "shd" -> shading = fill(p)
                "sz" -> size = OoxmlXml.int(p, "val")?.takeIf { it > 0 }
                "caps" -> caps = OoxmlXml.onOff(p)
                "smallCaps" -> smallCaps = OoxmlXml.onOff(p)
                "vanish" -> vanish = OoxmlXml.onOff(p)
                "rFonts" -> font = fontFamily(p)
                "effect" -> if (OoxmlXml.attr(p, "val").let { it != null && it != "none" }) effect = true
                // 옛 효과는 켜고 끄는 값이 있고(`w:shadow w:val="0"`), 2010 년의 효과(w14)는 있으면 켜진 것이다.
                // `OoxmlXml.onOff` 가 속성이 없으면 참을 주므로 두 경우를 한 줄로 읽는다.
                "outline", "shadow", "emboss", "imprint", "glow", "reflection", "textOutline",
                "textFill", "props3d", "scene3d" -> if (OoxmlXml.onOff(p)) effect = true
            }
            OoxmlXml.skip(p, limits)
        }
        val strikeAny = when {
            strike == true || dstrike == true -> true
            strike == false || dstrike == false -> false
            else -> null
        }
        return RunPr(
            styleId,
            RunProps(
                bold = bold, italic = italic, underline = underline, strike = strikeAny,
                vertAlign = vertAlign, color = color, highlight = highlight, shading = shading,
                sizeHalfPt = size, caps = caps, smallCaps = smallCaps, vanish = vanish, font = font,
                effect = effect,
            ),
        )
    }

    /** `w:sectPr` — 머리글·바닥글이 걸려 있는가. 쪽 재현을 포기했으므로 그 밖은 보지 않는다. */
    fun readSectPr(p: XmlPullParser, limits: ParseLimits): Boolean {
        var has = false
        eachChild(p, limits) { name ->
            if (name == "headerReference" || name == "footerReference") has = true
            OoxmlXml.skip(p, limits)
        }
        return has
    }

    /**
     * 지금 선 요소의 자식마다 [onChild] 를 부른다. [onChild] 는 **자식 요소를 끝까지 소비해야** 한다
     * (끝 태그에 서서 돌아온다). 본문 변환기의 `children` 과 같은 약속이다.
     */
    inline fun eachChild(p: XmlPullParser, limits: ParseLimits, onChild: (String) -> Unit) {
        val depth = p.depth
        while (true) {
            val ev = p.nextGuarded(limits)
            if (ev == XmlPullParser.END_DOCUMENT) return
            if (ev == XmlPullParser.END_TAG && p.depth == depth) return
            if (ev == XmlPullParser.START_TAG) onChild(p.name)
        }
    }

    /** 트윕 속성. 정수가 아닌 값(`720.0`)을 쓰는 도구가 있어 실수도 받는다. */
    fun twips(p: XmlPullParser, name: String): Int? {
        val raw = OoxmlXml.attr(p, name)?.trim() ?: return null
        raw.toIntOrNull()?.let { return it }
        val d = raw.toDoubleOrNull() ?: return null
        if (d.isNaN() || d.isInfinite()) return null
        return d.coerceIn(-1_000_000.0, 1_000_000.0).toInt()
    }

    /**
     * `w:shd` 의 배경색. 자동·무늬 없음이면 빈 문자열, 모르는 값이면 null. 표의 칸(`w:tcPr/w:shd`)도 쓴다.
     *
     * 무늬가 `solid`(100%)면 칸을 덮는 것은 채움(`w:fill`)이 아니라 **무늬 색(`w:color`)** 이다(ST_Shd).
     * 옛 문서가 검은 바탕에 흰 글자를 이렇게 적는데, 채움만 보면 흰 글자가 흰 바탕에서 사라진다.
     */
    fun fill(p: XmlPullParser): String? {
        if (OoxmlXml.attr(p, "val") == "solid") CssValues.hexColor(OoxmlXml.attr(p, "color"))?.let { return it }
        val f = OoxmlXml.attr(p, "fill") ?: return null
        return if (f == "auto") "" else CssValues.hexColor(f)
    }

    /**
     * `w:rFonts` 를 CSS 글꼴 목록으로. 테마 글꼴(`asciiTheme`)은 테마를 읽지 않으므로 버린다.
     * 고정폭 글꼴이면 끝에 `monospace` 를 붙인다 — 기기에 그 글꼴이 없어도 코드 조각의 칸은 맞는다.
     */
    private fun fontFamily(p: XmlPullParser): String? {
        val names = LinkedHashSet<String>()
        for (attr in FONT_ATTRS) {
            val raw = OoxmlXml.attr(p, attr) ?: continue
            CssValues.fontFamily(raw)?.let { names.add(it) }
        }
        if (names.isEmpty()) return null
        val mono = names.any { n -> MONO_HINTS.any { n.contains(it, ignoreCase = true) } }
        return names.joinToString(",") + if (mono) ",monospace" else ""
    }

    private val FONT_ATTRS = listOf("ascii", "hAnsi", "eastAsia")

    private val MONO_HINTS = listOf("Courier", "Consolas", "Mono", "Menlo", "D2Coding", "Lucida Console")

    /** `w:highlight` 의 이름(`yellow`)을 색으로. 모르는 이름은 null(무시), `none` 은 빈 문자열. */
    fun highlightColor(name: String): String? = if (name == "none") "" else HIGHLIGHT[name]

    private val HIGHLIGHT = mapOf(
        "yellow" to "#ffff00", "green" to "#00ff00", "cyan" to "#00ffff", "magenta" to "#ff00ff",
        "blue" to "#0000ff", "red" to "#ff0000", "darkBlue" to "#000080", "darkCyan" to "#008080",
        "darkGreen" to "#008000", "darkMagenta" to "#800080", "darkRed" to "#800000",
        "darkYellow" to "#808000", "darkGray" to "#808080", "lightGray" to "#c0c0c0",
        "black" to "#000000", "white" to "#ffffff",
    )
}
