package io.github.donggi.iroiroviewer.format.pptx

import io.github.donggi.iroiroviewer.format.opc.OoxmlXml
import io.github.donggi.iroiroviewer.safety.ParseLimits
import io.github.donggi.iroiroviewer.safety.nextGuarded
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

/**
 * PresentationML·DrawingML 부분 하나를 **흘려 읽어** 값([PartModel] 등)으로 만든다.
 *
 * ## DOM 이 아니다
 *
 * 풀 파서로 한 번 지나가며 우리가 그리는 데 쓰는 것만 형식 있는 값에 담는다. 모르는 요소는
 * 하위째 건너뛴다(`children`). 깊이는 [nextGuarded] 가, 개수는 아래 상한이 묶는다 — 넘으면
 * 뒷부분을 버리고 [truncated] 를 세운다. 슬라이드 하나가 도형 수십만 개를 적어 두어도
 * 메모리가 그 수를 따라가지 않는다.
 *
 * ## 이름공간을 보지 않는다
 *
 * `p:sp`(슬라이드)와 `dsp:sp`(SmartArt 그림)가 같은 모양이고, 과도기·엄격 두 벌의 이름공간이
 * 있다. `OoxmlXml` 의 판단대로 **지역 이름**으로 가른다. 관계 id(`r:embed`)만 이름공간을 본다.
 *
 * ## 대체 내용(`mc:AlternateContent`)
 *
 * **`Fallback` 을 고른다.** `Choice` 는 새 기능(p14·a14)을 요구하는 내용이고, 우리는 그것을
 * '이해한다' 고 말할 수 없다. 오피스도 모르는 `Choice` 는 버리고 `Fallback` 을 그린다 —
 * 수식(a14:m)이 그림으로, 새 전환 효과가 옛 효과로 내려오는 것이 그 길이다. `Fallback` 이
 * 없으면 첫 `Choice` 를 쓴다.
 *
 * 한 번 쓰고 버린다(개수 상한이 인스턴스에 쌓인다).
 */
internal class DrawingParser(private val p: XmlPullParser, private val limits: ParseLimits) {

    private var shapeCount = 0
    private var paraCount = 0
    private var cellCount = 0
    private var runCount = 0

    /** 상한에 걸려 무언가를 버렸다. */
    var truncated = false
        private set

    private var skippedGroups = 0
    private var unknownElements = 0

    /** 색·문단 서식·글자 서식처럼 위 상한이 세지 않는 값의 수. [weight] 에 들어간다. */
    private var madeValues = 0

    /** 모은 글자 수. [weight] 에 들어간다. */
    private var textChars = 0L

    /**
     * 읽은 것이 메모리에서 대략 얼마나 무거운가(값 하나 = 1, 글자 32 개 = 1).
     *
     * 레이아웃·마스터를 캐시에 둘지 문서가 이것으로 정한다. 부분 하나는 32MB 까지 들어오고
     * 값으로 옮기면 세 배쯤 불어난다(31MB 레이아웃이 97MB 를 붙들었다). 개수만 센 캐시
     * (레이아웃 32 개)에 그런 것이 쌓이면 폰 힙이 넘어간다.
     */
    val weight: Long get() = shapeCount.toLong() + paraCount + runCount + cellCount + madeValues + textChars / 32

    // ---- 부분 --------------------------------------------------------------------

    /**
     * 슬라이드(`p:sld`)·레이아웃(`p:sldLayout`)·마스터(`p:sldMaster`)·SmartArt 그림(`dsp:drawing`).
     * 뿌리 요소가 무엇이든 같은 모양으로 읽는다 — 있는 것만 채운다.
     */
    fun parsePart(name: String): PartModel {
        val root = toRoot() ?: return emptyPart(name)
        val hidden = root == "sld" && attr("show").let { it == "0" || it.equals("false", true) }
        val showMasterSp = bool("showMasterSp") ?: true
        var bg: Background? = null
        var shapes: List<Shape> = emptyList()
        var clrMap: Map<String, String>? = null
        var title: ListStyle? = null
        var body: ListStyle? = null
        var other: ListStyle? = null
        children { n ->
            when (n) {
                "cSld" -> children { c ->
                    when (c) {
                        "bg" -> bg = parseBackground()
                        "spTree" -> shapes = parseTree(0)
                    }
                }
                // SmartArt 그림은 `cSld` 없이 바로 `spTree` 다.
                "spTree" -> shapes = parseTree(0)
                "clrMap" -> clrMap = clrMapAttrs()
                "clrMapOvr" -> children { c -> if (c == "overrideClrMapping") clrMap = clrMapAttrs() }
                "txStyles" -> children { c ->
                    when (c) {
                        "titleStyle" -> title = parseListStyle()
                        "bodyStyle" -> body = parseListStyle()
                        "otherStyle" -> other = parseListStyle()
                    }
                }
            }
        }
        return PartModel(
            name, hidden, showMasterSp, bg, shapes, clrMap, title, body, other,
            truncated, skippedGroups, unknownElements,
        )
    }

    /** `presentation.xml` — 슬라이드 크기, 슬라이드 순서, 기본 글자 서식. */
    fun parsePresentation(): Presentation {
        toRoot() ?: return Presentation(null, null, emptyList(), null, false)
        // 첫 슬라이드의 번호(슬라이드 번호 필드가 쓴다). 없으면 1. 터무니없는 값은 1 로 둔다.
        val firstSlideNum = OoxmlXml.int(p, "firstSlideNum")?.takeIf { it in 0..MAX_FIRST_SLIDE_NUM } ?: 1
        var cx: Long? = null
        var cy: Long? = null
        val ids = ArrayList<String>()
        var overflow = false
        var defaults: ListStyle? = null
        children { n ->
            when (n) {
                "sldIdLst" -> children { c ->
                    if (c == "sldId") {
                        if (ids.size >= MAX_SLIDE_IDS) overflow = true
                        else OoxmlXml.rel(p, "id")?.let { ids.add(it) }
                    }
                }
                "sldSz" -> {
                    cx = long("cx")
                    cy = long("cy")
                }
                "defaultTextStyle" -> defaults = parseListStyle()
            }
        }
        return Presentation(cx, cy, ids, defaults, overflow, firstSlideNum)
    }

    /** 테마(`a:theme`) — 색 12 개, 글꼴, 선 굵기. */
    fun parseTheme(): Theme {
        val colors = HashMap<String, Rgba>()
        toRoot() ?: return Theme(colors, null, null, null, null, emptyList())
        var major: Array<String?> = arrayOfNulls(3)
        var minor: Array<String?> = arrayOfNulls(3)
        val widths = ArrayList<Long>()
        children { n ->
            if (n == "themeElements") children { e ->
                when (e) {
                    "clrScheme" -> children { slot ->
                        if (slot in ColorResolver.SCHEME_SLOTS) {
                            colorChild()?.let { spec -> ColorResolver.PLAIN.resolve(spec)?.let { colors[slot] = it } }
                        }
                    }
                    "fontScheme" -> children { f ->
                        when (f) {
                            "majorFont" -> major = parseFontCollection()
                            "minorFont" -> minor = parseFontCollection()
                        }
                    }
                    "fmtScheme" -> children { s ->
                        if (s == "lnStyleLst") children { l ->
                            if (l == "ln" && widths.size < MAX_THEME_LINES) widths.add((long("w") ?: DEFAULT_LINE_W).coerceIn(0, MAX_EMU))
                        }
                    }
                }
            }
        }
        return Theme(colors, major[0], minor[0], major[1] ?: major[2], minor[1] ?: minor[2], widths)
    }

    /**
     * 글꼴 묶음 — [0] 라틴, [1] 동아시아, [2] 한글 문자 체계(`a:font script="Hang"`).
     * 오피스 기본 테마는 동아시아 글꼴을 비워 두고 문자 체계별 표에 한글 글꼴을 적는다.
     */
    private fun parseFontCollection(): Array<String?> {
        val out = arrayOfNulls<String>(3)
        children { n ->
            when (n) {
                "latin" -> out[0] = attr("typeface")?.takeIf { it.isNotBlank() }
                "ea" -> out[1] = attr("typeface")?.takeIf { it.isNotBlank() }
                "font" -> if (attr("script") == "Hang") out[2] = attr("typeface")?.takeIf { it.isNotBlank() }
            }
        }
        return out
    }

    // ---- 도형 나무 -----------------------------------------------------------------

    /** `spTree`·`grpSp`·`Choice`·`Fallback` 의 자식 도형들. */
    private fun parseTree(groupDepth: Int): List<Shape> {
        val out = ArrayList<Shape>()
        children { n -> shapeChild(n, groupDepth, out) }
        return out
    }

    private fun shapeChild(n: String, groupDepth: Int, out: MutableList<Shape>) {
        when (n) {
            "sp", "cxnSp", "pic", "graphicFrame", "grpSp" -> {
                if (shapeCount >= MAX_SHAPES) {
                    truncated = true
                    return
                }
                if (n == "grpSp" && groupDepth >= MAX_GROUP_DEPTH) {
                    // 32 겹을 넘는 묶음은 사람이 만든 것이 아니다. 하위째 버리고 센다.
                    skippedGroups++
                    return
                }
                shapeCount++
                out.add(
                    when (n) {
                        "sp" -> parseSp(false)
                        "cxnSp" -> parseSp(true)
                        "pic" -> parsePic()
                        "graphicFrame" -> parseFrame()
                        else -> parseGroup(groupDepth + 1)
                    }
                )
            }
            "AlternateContent" -> alternate { parseTree(groupDepth) }?.let { out.addAll(it) }
            "contentPart" -> unknownElements++
        }
    }

    private fun parseGroup(depth: Int): Shape {
        var nv = Nv.EMPTY
        var xfrm: Xfrm? = null
        val kids = ArrayList<Shape>()
        children { n ->
            when (n) {
                "nvGrpSpPr" -> nv = parseNv()
                "grpSpPr" -> xfrm = parseSpPr().xfrm
                else -> shapeChild(n, depth, kids)
            }
        }
        return GroupShape(nv, xfrm, kids)
    }

    private fun parseSp(connector: Boolean): Shape {
        var nv = Nv.EMPTY
        var spPr = SpPr.EMPTY
        var style: ShapeStyle? = null
        var text: TextBody? = null
        var txXfrm: Xfrm? = null
        children { n ->
            when (n) {
                "nvSpPr", "nvCxnSpPr" -> nv = parseNv()
                "spPr" -> spPr = parseSpPr()
                "style" -> style = parseStyle()
                "txBody" -> text = parseTextBody()
                "txXfrm" -> txXfrm = parseXfrm()
            }
        }
        return SpShape(nv, spPr, style, text, txXfrm, connector)
    }

    private fun parsePic(): PicShape {
        var nv = Nv.EMPTY
        var spPr = SpPr.EMPTY
        var blip: Fill.Picture? = null
        children { n ->
            when (n) {
                "nvPicPr" -> nv = parseNv()
                "blipFill" -> blip = parseBlipFill()
                "spPr" -> spPr = parseSpPr()
            }
        }
        return PicShape(nv, spPr, blip)
    }

    private fun parseFrame(): Shape {
        var nv = Nv.EMPTY
        var xfrm: Xfrm? = null
        var content: FrameContent = FrameContent.Other
        children { n ->
            when (n) {
                "nvGraphicFramePr" -> nv = parseNv()
                "xfrm" -> xfrm = parseXfrm()
                "graphic" -> children { g -> if (g == "graphicData") content = parseGraphicData() }
            }
        }
        return FrameShape(nv, xfrm, content)
    }

    private fun parseGraphicData(): FrameContent {
        var content: FrameContent? = null
        children { n -> graphicChild(n)?.let { if (content == null) content = it } }
        return content ?: FrameContent.Other
    }

    /** `graphicData` 의 자식 하나. 모르는 것이면 null(건너뛴다). */
    private fun graphicChild(n: String): FrameContent? = when (n) {
        "tbl" -> FrameContent.TableContent(parseTable())
        // `c:chart` 와 새 차트(`cx:chart`)가 같은 지역 이름이다. 둘 다 그리지 않는다.
        "chart" -> FrameContent.Chart
        "relIds" -> FrameContent.Diagram(OoxmlXml.rel(p, "dm"))
        "oleObj" -> FrameContent.Ole(parseOle())
        "AlternateContent" -> alternate {
            var inner: FrameContent? = null
            children { m -> graphicChild(m)?.let { if (inner == null) inner = it } }
            inner
        }
        else -> null
    }

    /** 삽입 개체. 오피스 2010~ 는 미리 보기를 `p:pic` 으로 안에 넣는다. */
    private fun parseOle(): PicShape? {
        var pic: PicShape? = null
        children { n ->
            when (n) {
                "pic" -> if (pic == null) pic = parsePic()
                "AlternateContent" -> alternate {
                    var inner: PicShape? = null
                    children { m -> if (m == "pic" && inner == null) inner = parsePic() }
                    inner
                }?.let { if (pic == null) pic = it }
            }
        }
        return pic
    }

    /**
     * 대체 내용 하나. [branch] 는 `Choice`·`Fallback` 의 시작 태그에서 불려 그 끝까지 읽는다.
     * `Fallback` 을 고르고, 없으면 첫 `Choice`(위 주석).
     */
    private fun <T> alternate(branch: () -> T): T? {
        var choice: T? = null
        var sawChoice = false
        var fallback: T? = null
        var sawFallback = false
        children { n ->
            when (n) {
                "Choice" -> if (!sawChoice) {
                    sawChoice = true
                    choice = branch()
                }
                "Fallback" -> if (!sawFallback) {
                    sawFallback = true
                    fallback = branch()
                }
            }
        }
        return if (sawFallback) fallback else choice
    }

    // ---- 비시각 속성 ------------------------------------------------------------------

    private fun parseNv(): Nv {
        var name: String? = null
        var descr: String? = null
        var hidden = false
        var ph: Ph? = null
        var media = false
        children { n ->
            when (n) {
                "cNvPr" -> {
                    // 속성 값은 길이 상한이 없다(부분 전체의 32MB 까지). `descr` 는 그림의 `alt` 로
                    // 본문에 그대로 나가는데, `HtmlWriter` 는 **쓴 뒤에야** 상한을 보므로 수천만 자짜리
                    // 값 하나가 쓰기 상한(8M 자)을 몇 배 넘기고 위생기의 입력 상한(32M 자)에 걸려
                    // `partHtml` 이 던진다. 읽을 때 자른다.
                    name = attr("name")?.let { clip(it, MAX_ATTR_CHARS) }
                    descr = attr("descr")?.let { clip(it, MAX_ATTR_CHARS) }
                    hidden = bool("hidden") ?: false
                }
                "nvPr" -> children { c ->
                    when (c) {
                        "ph" -> ph = Ph(attr("type")?.takeIf { it.isNotBlank() } ?: "obj", (long("idx") ?: 0L).coerceAtLeast(0))
                        "videoFile", "audioFile", "quickTimeFile", "audioCd", "wavAudioFile" -> media = true
                        "extLst" -> children { e ->
                            if (e == "ext") children { m -> if (m == "media") media = true }
                        }
                    }
                }
            }
        }
        return Nv(name, descr, hidden, ph, media)
    }

    // ---- 도형 속성 -------------------------------------------------------------------

    private fun parseSpPr(): SpPr {
        var xfrm: Xfrm? = null
        var geom: Geometry? = null
        var fill: Fill? = null
        var line: Line? = null
        children { n ->
            when (n) {
                "xfrm" -> xfrm = parseXfrm()
                "prstGeom" -> geom = parsePrstGeom()
                "custGeom" -> geom = Geometry(null, null)
                "ln" -> line = parseLine()
                else -> if (n in FILL_ELEMENTS) fill = parseFill(n)
            }
        }
        return SpPr(xfrm, geom, fill, line)
    }

    private fun parseXfrm(): Xfrm? {
        val rot = (int("rot") ?: 0) / 60_000.0
        val flipH = bool("flipH") ?: false
        val flipV = bool("flipV") ?: false
        var off: LongArray? = null
        var ext: LongArray? = null
        var chOff: LongArray? = null
        var chExt: LongArray? = null
        children { n ->
            when (n) {
                "off" -> off = longArrayOf(coord("x"), coord("y"))
                "ext" -> ext = longArrayOf(size("cx"), size("cy"))
                "chOff" -> chOff = longArrayOf(coord("x"), coord("y"))
                "chExt" -> chExt = longArrayOf(size("cx"), size("cy"))
            }
        }
        val e = ext ?: return null
        val o = off ?: longArrayOf(0, 0)
        val child = if (chOff != null || chExt != null) {
            val co = chOff ?: o
            val ce = chExt ?: e
            Box(co[0], co[1], ce[0], ce[1])
        } else {
            null
        }
        return Xfrm(Box(o[0], o[1], e[0], e[1]), if (rot.isFinite()) rot else 0.0, flipH, flipV, child)
    }

    private fun parsePrstGeom(): Geometry {
        val prst = attr("prst")
        var adj: Int? = null
        children { n ->
            if (n == "avLst") children { g ->
                if (g == "gd" && (attr("name") == "adj" || attr("name") == "adj1") && adj == null) {
                    adj = attr("fmla")?.trim()?.removePrefix("val")?.trim()?.toIntOrNull()
                }
            }
        }
        return Geometry(prst, adj)
    }

    private fun parseLine(): Line {
        val w = long("w")?.coerceIn(0, MAX_EMU)
        var fill: Fill? = null
        var dash: String? = null
        children { n ->
            when (n) {
                "prstDash" -> dash = attr("val")
                else -> if (n in FILL_ELEMENTS) fill = parseFill(n)
            }
        }
        return Line(w, fill, dash)
    }

    private fun parseStyle(): ShapeStyle {
        var line: StyleRef? = null
        var fill: StyleRef? = null
        var font: FontRef? = null
        children { n ->
            when (n) {
                "lnRef" -> {
                    val idx = int("idx") ?: 0
                    line = StyleRef(idx, colorChild())
                }
                "fillRef" -> {
                    val idx = int("idx") ?: 0
                    fill = StyleRef(idx, colorChild())
                }
                "fontRef" -> {
                    val major = attr("idx") == "major"
                    font = FontRef(major, colorChild())
                }
            }
        }
        return ShapeStyle(line, fill, font)
    }

    /** 채우기 요소 하나. [FILL_ELEMENTS] 에 든 이름에서만 부른다. */
    private fun parseFill(n: String): Fill? = when (n) {
        "noFill" -> Fill.None
        "solidFill" -> colorChild()?.let { Fill.Solid(it) }
        "gradFill" -> {
            var first: ColorSpec? = null
            children { c ->
                if (c == "gsLst") children { gs ->
                    if (gs == "gs" && first == null) first = colorChild()
                }
            }
            Fill.Gradient(first)
        }
        "pattFill" -> {
            var fg: ColorSpec? = null
            children { c -> if (c == "fgClr") fg = colorChild() }
            Fill.Pattern(fg)
        }
        "blipFill" -> parseBlipFill()
        "grpFill" -> Fill.Group
        else -> null
    }

    private fun parseBlipFill(): Fill.Picture {
        var embed: String? = null
        var link: String? = null
        var crop: Crop? = null
        var tile = false
        children { n ->
            when (n) {
                "blip" -> {
                    embed = OoxmlXml.rel(p, "embed")?.takeIf { it.isNotBlank() }
                    link = OoxmlXml.rel(p, "link")?.takeIf { it.isNotBlank() }
                }
                "srcRect" -> crop = Crop(pct("l") ?: 0, pct("t") ?: 0, pct("r") ?: 0, pct("b") ?: 0)
                "tile" -> tile = true
            }
        }
        return Fill.Picture(embed, link, crop, tile)
    }

    private fun parseBackground(): Background {
        var fill: Fill? = null
        var ref: ColorSpec? = null
        children { n ->
            when (n) {
                "bgPr" -> children { f -> if (f in FILL_ELEMENTS && fill == null) fill = parseFill(f) }
                "bgRef" -> ref = colorChild()
            }
        }
        return Background(fill, ref)
    }

    // ---- 글 --------------------------------------------------------------------------

    private fun parseTextBody(): TextBody {
        var bodyPr: BodyProps? = null
        var lst: ListStyle? = null
        val paras = ArrayList<Para>()
        children { n ->
            when (n) {
                "bodyPr" -> bodyPr = parseBodyPr()
                "lstStyle" -> lst = parseListStyle()
                "p" -> {
                    if (paraCount >= MAX_PARAGRAPHS) {
                        truncated = true
                    } else {
                        paraCount++
                        paras.add(parsePara())
                    }
                }
            }
        }
        return TextBody(bodyPr, lst, paras)
    }

    private fun parseBodyPr(): BodyProps {
        val l = long("lIns")?.coerceIn(0, MAX_EMU)
        val t = long("tIns")?.coerceIn(0, MAX_EMU)
        val r = long("rIns")?.coerceIn(0, MAX_EMU)
        val b = long("bIns")?.coerceIn(0, MAX_EMU)
        val anchor = attr("anchor")
        val wrap = attr("wrap")
        val vert = attr("vert")
        var fontScale: Int? = null
        var reduction: Int? = null
        var warp: String? = null
        children { n ->
            when (n) {
                "normAutofit" -> {
                    fontScale = pct("fontScale")
                    reduction = pct("lnSpcReduction")
                }
                "prstTxWarp" -> warp = attr("prst")
            }
        }
        return BodyProps(l, t, r, b, anchor, wrap, vert, fontScale, reduction, warp)
    }

    private fun parseListStyle(): ListStyle {
        var def: ParaProps? = null
        val levels = arrayOfNulls<ParaProps>(9)
        children { n ->
            if (n == "defPPr") {
                def = parsePPr()
            } else if (n.length == 7 && n.startsWith("lvl") && n.endsWith("pPr") && n[3] in '1'..'9') {
                levels[n[3] - '1'] = parsePPr()
            }
        }
        return ListStyle(def, levels)
    }

    private fun parsePPr(): ParaProps {
        val lvl = int("lvl")?.coerceIn(0, 8)
        val algn = attr("algn")
        val marL = long("marL")?.coerceIn(-MAX_EMU, MAX_EMU)
        val indent = long("indent")?.coerceIn(-MAX_EMU, MAX_EMU)
        var lnSpc: Spacing? = null
        var spcBef: Spacing? = null
        var spcAft: Spacing? = null
        var bullet: Bullet? = null
        var buColor: BulletColor? = null
        var buFont: BulletFont? = null
        var buSize: BulletSize? = null
        var defRPr: RunProps? = null
        children { n ->
            when (n) {
                "lnSpc" -> lnSpc = parseSpacing()
                "spcBef" -> spcBef = parseSpacing()
                "spcAft" -> spcAft = parseSpacing()
                "buClrTx" -> buColor = BulletColor(null)
                "buClr" -> buColor = BulletColor(colorChild())
                "buSzTx" -> buSize = BulletSize(null, null)
                "buSzPct" -> buSize = BulletSize(pct("val"), null)
                "buSzPts" -> buSize = BulletSize(null, int("val"))
                "buFontTx" -> buFont = BulletFont(null)
                "buFont" -> buFont = BulletFont(attr("typeface"))
                "buNone" -> bullet = Bullet.None
                "buAutoNum" -> bullet = Bullet.Auto(attr("type") ?: "arabicPeriod", (int("startAt") ?: 1).coerceIn(1, 32767))
                "buChar" -> bullet = Bullet.Char(attr("char").orEmpty().take(2))
                "buBlip" -> bullet = Bullet.Picture
                "defRPr" -> defRPr = parseRPr()
            }
        }
        madeValues++
        return ParaProps(lvl, algn, marL, indent, lnSpc, spcBef, spcAft, bullet, buColor, buFont, buSize, defRPr)
    }

    private fun parseSpacing(): Spacing {
        var pctV: Int? = null
        var pts: Int? = null
        children { n ->
            when (n) {
                "spcPct" -> pctV = pct("val")
                "spcPts" -> pts = int("val")
            }
        }
        return Spacing(pctV, pts)
    }

    private fun parseRPr(): RunProps {
        val sz = int("sz")?.coerceIn(100, 400_000)
        val b = bool("b")
        val i = bool("i")
        val u = attr("u")
        val strike = attr("strike")
        val baseline = pct("baseline")
        val cap = attr("cap")
        val lang = attr("lang")?.takeIf { it.length <= 35 && LANG_TAG.matches(it) }
        var latin: String? = null
        var ea: String? = null
        var fill: Fill? = null
        var fancy = false
        var highlight: ColorSpec? = null
        var link: String? = null
        children { n ->
            when (n) {
                "latin" -> latin = attr("typeface")?.takeIf { it.isNotBlank() }
                "ea" -> ea = attr("typeface")?.takeIf { it.isNotBlank() }
                "highlight" -> highlight = colorChild()
                "hlinkClick" -> link = OoxmlXml.rel(p, "id").orEmpty()
                else -> if (n in FILL_ELEMENTS) {
                    fill = parseFill(n)
                    if (n == "gradFill" || n == "blipFill" || n == "pattFill") fancy = true
                }
            }
        }
        madeValues++
        return RunProps(sz, b, i, u, strike, baseline, cap, latin, ea, fill, highlight, link, fancy, lang)
    }

    private fun parsePara(): Para {
        var pPr: ParaProps? = null
        val items = ArrayList<RunItem>()
        var end: RunProps? = null
        children { n ->
            if (n == "endParaRPr") end = parseRPr() else paraChild(n, items) { pPr = it }
        }
        return Para(pPr, items, end)
    }

    /**
     * 조각을 하나 더 받을 수 있는가. 문단 하나의 상한과 **부분 전체의 상한**을 함께 본다 —
     * 문단마다 5,000 개씩 2 만 문단이면 1 억 개가 되어, 문단 상한만으로는 메모리가 묶이지 않는다.
     */
    private fun roomForRun(items: List<RunItem>): Boolean {
        if (items.size >= MAX_RUNS || runCount >= MAX_RUNS_PER_PART) {
            truncated = true
            return false
        }
        runCount++
        return true
    }

    /** 문단의 자식 하나. 처리했으면 Unit, 모르는 것이면 null. */
    private fun paraChild(n: String, items: MutableList<RunItem>, onPPr: (ParaProps) -> Unit): Unit? {
        when (n) {
            "pPr" -> onPPr(parsePPr())
            "r", "fld" -> {
                if (!roomForRun(items)) return Unit
                // 슬라이드 번호 필드는 그릴 때 번호로 바꾼다(`TextRun.slideNumber`). 날짜 필드는 저장할 때의 글을 둔다.
                val slideNumber = n == "fld" && OoxmlXml.attr(p, "type") == "slidenum"
                var rPr: RunProps? = null
                var text = ""
                children { c ->
                    when (c) {
                        "rPr" -> rPr = parseRPr()
                        "t" -> text = OoxmlXml.collectText(p, limits, MAX_RUN_CHARS)
                    }
                }
                textChars += text.length
                items.add(TextRun(text, rPr, slideNumber))
            }
            "br" -> {
                if (!roomForRun(items)) return Unit
                var rPr: RunProps? = null
                children { c -> if (c == "rPr") rPr = parseRPr() }
                items.add(LineBreak(rPr))
            }
            "AlternateContent" -> {
                val chosen = alternate {
                    val inner = ArrayList<RunItem>()
                    children { c -> paraChild(c, inner) { } }
                    inner
                }
                chosen?.let { items.addAll(it.take((MAX_RUNS - items.size).coerceAtLeast(0))) }
            }
            else -> return null
        }
        return Unit
    }

    // ---- 표 -----------------------------------------------------------------------------

    private fun parseTable(): Table {
        val cols = ArrayList<Long>()
        val rows = ArrayList<TableRow>()
        var styleId: String? = null
        var firstRow = false
        var bandRow = false
        children { n ->
            when (n) {
                "tblPr" -> {
                    firstRow = bool("firstRow") ?: false
                    bandRow = bool("bandRow") ?: false
                    children { c -> if (c == "tableStyleId") styleId = OoxmlXml.collectText(p, limits, 64).trim() }
                }
                "tblGrid" -> children { c ->
                    if (c == "gridCol") {
                        if (cols.size < MAX_TABLE_COLS) cols.add(size("w")) else truncated = true
                    }
                }
                "tr" -> if (rows.size < MAX_TABLE_ROWS) rows.add(parseRow()) else truncated = true
            }
        }
        return Table(cols, rows, styleId, firstRow, bandRow)
    }

    private fun parseRow(): TableRow {
        val h = size("h")
        val cells = ArrayList<TableCell>()
        children { n ->
            if (n == "tc") {
                if (cells.size >= MAX_TABLE_COLS || cellCount >= MAX_TABLE_CELLS) {
                    truncated = true
                } else {
                    cellCount++
                    cells.add(parseCell())
                }
            }
        }
        return TableRow(h, cells)
    }

    private fun parseCell(): TableCell {
        val gridSpan = (int("gridSpan") ?: 1).coerceIn(1, MAX_TABLE_COLS)
        val rowSpan = (int("rowSpan") ?: 1).coerceIn(1, MAX_TABLE_ROWS)
        val hMerge = bool("hMerge") ?: false
        val vMerge = bool("vMerge") ?: false
        var text: TextBody? = null
        var mar = arrayOfNulls<Long>(4)
        var anchor: String? = null
        var fill: Fill? = null
        val borders = arrayOfNulls<Line>(4)
        children { n ->
            when (n) {
                "txBody" -> text = parseTextBody()
                "tcPr" -> {
                    mar = arrayOf(long("marL"), long("marR"), long("marT"), long("marB")).map { it?.coerceIn(0, MAX_EMU) }.toTypedArray()
                    anchor = attr("anchor")
                    children { c ->
                        when (c) {
                            "lnL" -> borders[0] = parseLine()
                            "lnR" -> borders[1] = parseLine()
                            "lnT" -> borders[2] = parseLine()
                            "lnB" -> borders[3] = parseLine()
                            else -> if (c in FILL_ELEMENTS) fill = parseFill(c)
                        }
                    }
                }
            }
        }
        return TableCell(
            gridSpan, rowSpan, hMerge, vMerge, text, mar[0], mar[1], mar[2], mar[3], anchor, fill,
            borders[0], borders[1], borders[2], borders[3],
        )
    }

    // ---- 색 -----------------------------------------------------------------------------

    /** 지금 요소의 자식 가운데 첫 색. 요소 끝까지 읽는다. */
    private fun colorChild(): ColorSpec? {
        var c: ColorSpec? = null
        children { n -> if (c == null && n in COLOR_ELEMENTS) c = parseColor(n) }
        return c
    }

    private fun parseColor(n: String): ColorSpec? {
        val spec: Triple<ColorKind, String, IntArray?> = when (n) {
            "srgbClr" -> Triple(ColorKind.SRGB, attr("val").orEmpty(), null)
            "schemeClr" -> Triple(ColorKind.SCHEME, attr("val").orEmpty(), null)
            "sysClr" -> Triple(ColorKind.SYSTEM, attr("val").orEmpty(), null)
            "prstClr" -> Triple(ColorKind.PRESET, attr("val").orEmpty(), null)
            "hslClr" -> Triple(ColorKind.HSL, "", intArrayOf(int("hue") ?: 0, pct("sat") ?: 0, pct("lum") ?: 0))
            "scrgbClr" -> Triple(ColorKind.SCRGB, "", intArrayOf(pct("r") ?: 0, pct("g") ?: 0, pct("b") ?: 0))
            else -> return null
        }
        val last = if (n == "sysClr") attr("lastClr") else null
        val mods = ArrayList<ColorMod>()
        children { m ->
            if (mods.size < MAX_COLOR_MODS) {
                val v = pct("val")
                if (v != null) mods.add(ColorMod(m, v)) else if (m in NO_VALUE_MODS) mods.add(ColorMod(m, 0))
            }
        }
        madeValues += 1 + mods.size
        return ColorSpec(spec.first, spec.second, last, spec.third, mods)
    }

    private fun clrMapAttrs(): Map<String, String> {
        val out = HashMap<String, String>()
        for (i in 0 until minOf(p.attributeCount, 32)) {
            out[p.getAttributeName(i)] = p.getAttributeValue(i)
        }
        return out
    }

    // ---- 바탕 ---------------------------------------------------------------------------

    private fun emptyPart(name: String) = PartModel(name, false, true, null, emptyList(), null, null, null, null, false, 0, 0)

    private fun toRoot(): String? {
        var e = p.eventType
        while (e != XmlPullParser.START_TAG) {
            if (e == XmlPullParser.END_DOCUMENT) return null
            e = p.nextGuarded(limits)
        }
        return p.name
    }

    /**
     * 지금 선 요소(START_TAG)의 자식들을 차례로 [block] 에 건넨다. 끝나면 그 요소의 END_TAG 에 선다.
     *
     * [block] 은 자식 하나를 **끝까지 읽거나 전혀 건드리지 않아야** 한다. 건드리지 않았으면
     * (여전히 그 시작 태그에 서 있으면) 여기서 하위째 건너뛴다 — 모르는 요소를 조용히 넘기는 길이다.
     */
    private fun children(block: (String) -> Unit) {
        val depth = p.depth
        while (true) {
            val e = p.nextGuarded(limits)
            // 요소 가운데서 문서가 끝났다. 안드로이드의 파서는 여기서 던지고 JVM 시험의 kxml2 는
            // 조용히 END_DOCUMENT 를 준다 — 둘이 같게 보이도록 우리가 던진다(그 슬라이드만 실패한다).
            if (e == XmlPullParser.END_DOCUMENT) throw XmlPullParserException("요소가 닫히지 않았다")
            if (e == XmlPullParser.END_TAG && p.depth <= depth) return
            if (e == XmlPullParser.START_TAG) {
                val d = p.depth
                block(p.name)
                if (p.eventType == XmlPullParser.START_TAG && p.depth == d) OoxmlXml.skip(p, limits)
            }
        }
    }

    private fun attr(name: String): String? = OoxmlXml.attr(p, name)

    private fun int(name: String): Int? = OoxmlXml.int(p, name)

    private fun long(name: String): Long? = OoxmlXml.long(p, name)

    private fun bool(name: String): Boolean? {
        val v = attr(name) ?: return null
        return !(v == "0" || v.equals("false", true) || v.equals("off", true))
    }

    /** 좌표(음수 허용). 터무니없는 값은 누른다 — 뒤에서 `Double` 로 곱해도 넘치지 않게. */
    private fun coord(name: String): Long = (long(name) ?: 0L).coerceIn(-MAX_EMU, MAX_EMU)

    /** 크기(음수 불가). */
    private fun size(name: String): Long = (long(name) ?: 0L).coerceIn(0, MAX_EMU)

    /** [max] 자로 자른다. 대리 문자 쌍이 갈리면 반쪽은 `HtmlWriter` 가 버린다. 새 문자열이라 긴 원문은 붙들지 않는다. */
    private fun clip(s: String, max: Int): String = if (s.length > max) s.substring(0, max) else s

    /**
     * 백분율. 과도기는 정수(`50000` = 50%), 엄격은 `50%` 로 적는다 — 둘 다 1/1000 % 정수로.
     */
    private fun pct(name: String): Int? {
        val v = attr(name)?.trim() ?: return null
        if (v.endsWith("%")) {
            val d = v.dropLast(1).trim().toDoubleOrNull() ?: return null
            if (!d.isFinite()) return null
            return (d * 1000).coerceIn(-1e9, 1e9).toInt()
        }
        return v.toIntOrNull()
    }

    companion object {
        /** 부분 하나의 도형 수(묶음 안 포함). */
        const val MAX_SHAPES = 10_000

        /** `firstSlideNum` 을 믿는 범위. 넘는 값은 없던 것으로 친다(번호가 화면을 넘치는 글자가 되지 않게). */
        const val MAX_FIRST_SLIDE_NUM = 9_999

        /** 묶음이 겹칠 수 있는 깊이. */
        const val MAX_GROUP_DEPTH = 32

        /** 부분 하나의 문단 수. */
        const val MAX_PARAGRAPHS = 20_000

        /** 문단 하나의 조각(run·줄바꿈) 수. */
        const val MAX_RUNS = 5_000

        /** 부분 하나의 조각 수(모든 문단을 합쳐). */
        const val MAX_RUNS_PER_PART = 100_000

        /** 조각 하나의 글자 수. */
        const val MAX_RUN_CHARS = 64 * 1024

        /** 도형 이름·설명(`cNvPr name`·`descr`)의 글자 수. 설명은 그림의 `alt` 가 된다. */
        const val MAX_ATTR_CHARS = 2_000

        const val MAX_TABLE_ROWS = 1_000
        const val MAX_TABLE_COLS = 200
        const val MAX_TABLE_CELLS = 20_000

        /** 색 하나에 걸 수 있는 변환 수. */
        const val MAX_COLOR_MODS = 16

        /** `sldIdLst` 에서 읽는 수. 슬라이드 상한(2,000)을 넘었는지 알 만큼만 더 읽는다. */
        const val MAX_SLIDE_IDS = 2_001

        /** 좌표의 절댓값 상한. ST_Coordinate 의 범위(±27273042316900)보다 훨씬 작지만 그릴 수 있는 크기보다 훨씬 크다. */
        const val MAX_EMU = 1_000_000_000_000L

        private const val MAX_THEME_LINES = 8
        private const val DEFAULT_LINE_W = 9525L

        val COLOR_ELEMENTS = setOf("srgbClr", "schemeClr", "sysClr", "prstClr", "hslClr", "scrgbClr")

        val FILL_ELEMENTS = setOf("noFill", "solidFill", "gradFill", "pattFill", "blipFill", "grpFill")

        /** BCP 47 언어 태그의 모양(`ja-JP`·`zh-Hant-TW`). 이 밖의 값은 속성으로 옮기지 않는다. */
        private val LANG_TAG = Regex("[A-Za-z]{2,3}(-[A-Za-z0-9]{1,8})*")

        /** 값 없이 쓰는 색 변환. */
        private val NO_VALUE_MODS = setOf("comp", "inv", "gray")
    }
}
