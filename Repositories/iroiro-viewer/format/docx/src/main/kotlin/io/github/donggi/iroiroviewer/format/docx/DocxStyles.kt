package io.github.donggi.iroiroviewer.format.docx

import io.github.donggi.iroiroviewer.format.opc.OoxmlXml
import io.github.donggi.iroiroviewer.safety.ParseLimits
import org.xmlpull.v1.XmlPullParser

/**
 * 스타일 사슬(`basedOn`)을 따라 겹친 문단 스타일 하나.
 *
 * @param headingLevel 제목이면 0..8(워드의 개요 수준). 본문이면 null.
 * @param numStyleId 번호([numId])를 준 스타일의 id. 번호 수준을 그 스타일에 묶어 둔 목록(`w:lvl/w:pStyle`)이
 *   있어 수준을 찾을 때 쓴다.
 * @param run 문서 기본값과 사슬의 `w:rPr` 를 겹친 글자 속성 — 문단 전체의 바탕이다.
 */
internal class ResolvedPara(
    val headingLevel: Int?,
    val isTitle: Boolean,
    val numId: Int?,
    val ilvl: Int?,
    val numStyleId: String?,
    val jc: String?,
    val indLeft: Int?,
    val indRight: Int?,
    val indFirst: Int?,
    val shading: String?,
    val run: RunProps,
)

/**
 * `styles.xml` — 문단·글자·번호 스타일과 문서 기본값.
 *
 * ## 사슬은 끊어서 따른다
 *
 * `basedOn` 은 문서가 적는 값이라 **고리**(A → B → A)를 만들 수 있다. 방문 집합과 길이 상한
 * ([MAX_CHAIN])으로 끊는다. 겹친 결과는 스타일마다 한 번만 계산해 둔다([paragraphCache]) —
 * 문단 수만 개가 같은 스타일을 쓴다.
 *
 * ## 제목은 이름으로도 알아본다
 *
 * 개요 수준(`w:outlineLvl`)이 1차 근거다. 없으면 **스타일 이름**(`heading 1`)을 본다 — 한국어
 * 워드는 스타일 id 를 숫자(`1`)로 적지만 내장 스타일의 이름은 영어로 둔다. 스타일 표가 아예 없는
 * 문서는 id(`Heading1`)로 짐작한다. 개요 수준 9 는 '본문' 이다 — `TOC Heading` 이 제목 1 을
 * 잇되 목차에 들지 않으려고 쓰는 값이다.
 */
internal class DocxStyles private constructor(
    private val styles: Map<String, StyleDef>,
    private val defaultParagraph: String?,
    /** 문서 기본 글자 속성(`w:rPrDefault`). */
    val defaults: RunProps,
    /** 문서 기본 문단 속성(`w:pPrDefault`). */
    val defaultPara: ParaProps,
) {
    private class StyleDef(
        val id: String,
        val type: String,
        val isDefault: Boolean,
        val name: String?,
        val basedOn: String?,
        val pPr: ParaProps?,
        val rPr: RunProps?,
    )

    private val paragraphCache = HashMap<String, ResolvedPara>()
    private val characterCache = HashMap<String, RunProps>()

    /** 본문의 기본 글자 크기(반 포인트). 명세의 기본값은 10pt 다. */
    val defaultSizeHalfPt: Int get() = defaults.sizeHalfPt ?: 20

    /** 문단 스타일 하나를 사슬째 겹친다. id 가 없거나 모르는 id 면 기본 문단 스타일이다. */
    fun paragraph(styleId: String?): ResolvedPara {
        val key = styleId ?: ""
        paragraphCache[key]?.let { return it }
        val resolved = resolveParagraph(styleId)
        if (paragraphCache.size < MAX_STYLES) paragraphCache[key] = resolved
        return resolved
    }

    /** 글자 스타일(`w:rStyle`)을 사슬째 겹친 것. 없으면 null. */
    fun character(styleId: String?): RunProps? {
        if (styleId == null) return null
        characterCache[styleId]?.let { return it }
        val chain = chainOf(styleId)
        if (chain.isEmpty()) return null
        var props = RunProps.EMPTY
        for (s in chain.asReversed()) props = props.overlay(s.rPr)
        if (characterCache.size < MAX_STYLES) characterCache[styleId] = props
        return props
    }

    /** 번호 스타일(`w:type="numbering"`)이 가리키는 `numId`. 목록이 목록 스타일에 묶일 때 쓴다. */
    fun numberingStyleNumId(styleId: String): Int? = styles[styleId]?.pPr?.numId

    private fun resolveParagraph(styleId: String?): ResolvedPara {
        val start = styleId?.takeIf { it in styles } ?: defaultParagraph
        val chain = if (start != null) chainOf(start) else emptyList()

        var level: Int? = null
        var decided = false
        for (s in chain) {
            val lvl = s.pPr?.outlineLvl ?: continue
            level = lvl
            decided = true
            break
        }
        if (!decided) {
            for (s in chain) {
                val lvl = headingFromName(s.name) ?: continue
                level = lvl
                decided = true
                break
            }
        }
        // 스타일 표에 없는 id 는 id 로 짐작한다(표가 통째로 없는 문서).
        if (!decided && styleId != null && styleId !in styles) level = headingFromId(styleId)
        val isTitle = chain.firstOrNull()?.name?.trim()?.equals("Title", ignoreCase = true) == true
        if (isTitle && level == null) level = 0

        var numId: Int? = null
        var ilvl: Int? = null
        var numStyleId: String? = null
        for (s in chain) {
            val pp = s.pPr ?: continue
            if (pp.numId != null || pp.ilvl != null) {
                numId = pp.numId
                ilvl = pp.ilvl
                numStyleId = s.id
                break
            }
        }

        var run = defaults
        for (s in chain.asReversed()) run = run.overlay(s.rPr)

        return ResolvedPara(
            headingLevel = level?.takeIf { it in 0..8 },
            isTitle = isTitle,
            numId = numId,
            ilvl = ilvl,
            numStyleId = numStyleId,
            jc = chain.firstNotNullOfOrNull { it.pPr?.jc } ?: defaultPara.jc,
            indLeft = chain.firstNotNullOfOrNull { it.pPr?.indLeft } ?: defaultPara.indLeft,
            indRight = chain.firstNotNullOfOrNull { it.pPr?.indRight } ?: defaultPara.indRight,
            indFirst = chain.firstNotNullOfOrNull { it.pPr?.indFirst } ?: defaultPara.indFirst,
            shading = chain.firstNotNullOfOrNull { it.pPr?.shading },
            run = run,
        )
    }

    /** 자신에서 뿌리 쪽으로. 고리와 긴 사슬을 끊는다. */
    private fun chainOf(id: String): List<StyleDef> {
        val out = ArrayList<StyleDef>(4)
        val seen = HashSet<String>()
        var cur: String? = id
        while (cur != null && out.size < MAX_CHAIN && seen.add(cur)) {
            val s = styles[cur] ?: break
            out.add(s)
            cur = s.basedOn
        }
        return out
    }

    companion object {
        /** 스타일 사슬의 길이 상한. 워드가 만드는 사슬은 대여섯이다. */
        private const val MAX_CHAIN = 32

        /** 스타일 수 상한. 수천이면 이미 이상하다(워드의 내장 잠재 스타일도 400 남짓이다). */
        private const val MAX_STYLES = 10_000

        /** 스타일 표가 없는 문서. */
        val EMPTY = DocxStyles(emptyMap(), null, RunProps.EMPTY, ParaProps())

        private val HEADING_NAME = Regex("^(?:heading|제목)\\s*([1-9])$", RegexOption.IGNORE_CASE)
        private val HEADING_ID = Regex("^heading\\s*([1-9])$", RegexOption.IGNORE_CASE)

        fun headingFromName(name: String?): Int? =
            name?.trim()?.let { HEADING_NAME.find(it) }?.groupValues?.get(1)?.toInt()?.minus(1)

        private fun headingFromId(id: String): Int? =
            HEADING_ID.find(id.trim())?.groupValues?.get(1)?.toInt()?.minus(1)

        /** [p] 는 `styles.xml` 의 뿌리(`w:styles`)에 선 파서다(첫 START_TAG 까지 나아가 있어야 한다). */
        fun parse(p: XmlPullParser, limits: ParseLimits): DocxStyles {
            val styles = HashMap<String, StyleDef>()
            var defaultParagraph: String? = null
            var defaults = RunProps.EMPTY
            var defaultPara = ParaProps()
            DocxProps.eachChild(p, limits) { name ->
                when (name) {
                    "docDefaults" -> DocxProps.eachChild(p, limits) { d ->
                        when (d) {
                            "rPrDefault" -> DocxProps.eachChild(p, limits) { r ->
                                if (r == "rPr") defaults = DocxProps.readRPr(p, limits).props else OoxmlXml.skip(p, limits)
                            }
                            "pPrDefault" -> DocxProps.eachChild(p, limits) { r ->
                                if (r == "pPr") defaultPara = DocxProps.readPPr(p, limits) else OoxmlXml.skip(p, limits)
                            }
                            else -> OoxmlXml.skip(p, limits)
                        }
                    }
                    "style" -> {
                        val def = readStyle(p, limits)
                        if (def != null && styles.size < MAX_STYLES && def.id !in styles) {
                            styles[def.id] = def
                            if (def.type == "paragraph" && def.isDefault && defaultParagraph == null) defaultParagraph = def.id
                        }
                    }
                    else -> OoxmlXml.skip(p, limits)
                }
            }
            return DocxStyles(styles, defaultParagraph, defaults, defaultPara)
        }

        private fun readStyle(p: XmlPullParser, limits: ParseLimits): StyleDef? {
            val id = OoxmlXml.attr(p, "styleId")
            val type = OoxmlXml.attr(p, "type") ?: "paragraph"
            val isDefault = OoxmlXml.attr(p, "default").let { it == "1" || it == "true" || it == "on" }
            var name: String? = null
            var basedOn: String? = null
            var pPr: ParaProps? = null
            var rPr: RunProps? = null
            DocxProps.eachChild(p, limits) { n ->
                when (n) {
                    "name" -> {
                        name = OoxmlXml.attr(p, "val")?.take(128)
                        OoxmlXml.skip(p, limits)
                    }
                    "basedOn" -> {
                        basedOn = OoxmlXml.attr(p, "val")
                        OoxmlXml.skip(p, limits)
                    }
                    "pPr" -> pPr = DocxProps.readPPr(p, limits)
                    "rPr" -> rPr = DocxProps.readRPr(p, limits).props
                    else -> OoxmlXml.skip(p, limits)
                }
            }
            if (id == null) return null
            return StyleDef(id, type, isDefault, name, basedOn, pPr, rPr)
        }
    }
}
