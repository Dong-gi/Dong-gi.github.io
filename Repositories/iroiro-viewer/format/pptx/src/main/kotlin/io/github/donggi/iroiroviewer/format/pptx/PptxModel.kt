package io.github.donggi.iroiroviewer.format.pptx

// 슬라이드·레이아웃·마스터를 읽어 만든 **값**. DOM 이 아니다 — 우리가 그리는 데 쓰는 것만
// 골라 담은 형식이고, 개수는 읽는 쪽(`DrawingParser`)이 상한으로 묶는다.
//
// 거의 모든 칸이 null 을 허락한다. pptx 의 핵심이 **상속**이라 '적혀 있지 않다' 와 '기본값이다'
// 를 갈라야 한다 — 슬라이드에 없으면 레이아웃, 레이아웃에 없으면 마스터를 본다(`SlideRenderer`).

/** EMU 상자. 크기는 음수가 아니다. */
internal class Box(val x: Long, val y: Long, val cx: Long, val cy: Long)

/**
 * 변환(`a:xfrm`·`p:xfrm`). [child] 는 묶음(`grpSpPr`)에만 있는 자식 좌표계(`chOff`·`chExt`).
 *
 * @param rot 도(°). 파일에는 1/60000 도로 적혀 있다.
 */
internal class Xfrm(
    val box: Box,
    val rot: Double = 0.0,
    val flipH: Boolean = false,
    val flipV: Boolean = false,
    val child: Box? = null,
)

internal enum class ColorKind { SRGB, SCHEME, SYSTEM, PRESET, HSL, SCRGB }

/** 색 변환 하나(`a:lumMod val="75000"`). 값은 파일의 정수 그대로(대개 1/1000 %). */
internal class ColorMod(val name: String, val value: Int)

/**
 * 아직 풀지 않은 색. 테마 색(`schemeClr`)은 **그리는 슬라이드의 색 대응표**로 풀어야 하므로
 * 읽을 때 정하지 않는다 — 같은 마스터 도형이 슬라이드의 `clrMapOvr` 에 따라 다른 색이 된다.
 *
 * @param nums `hslClr`(색상·채도·명도)·`scrgbClr`(r·g·b) 의 수.
 */
internal class ColorSpec(
    val kind: ColorKind,
    val value: String,
    val last: String? = null,
    val nums: IntArray? = null,
    val mods: List<ColorMod> = emptyList(),
)

/** 채우기. 그라데이션은 첫 멈춤점의 색만, 무늬는 전경색만 쓴다. */
internal sealed class Fill {
    object None : Fill()
    class Solid(val color: ColorSpec) : Fill()
    class Gradient(val first: ColorSpec?) : Fill()
    class Pattern(val fg: ColorSpec?) : Fill()
    class Picture(val embed: String?, val link: String?, val crop: Crop?, val tile: Boolean) : Fill()

    /** 묶음의 채우기를 따른다. 우리는 묶음에 채우기를 그리지 않으므로 없는 것과 같다. */
    object Group : Fill()
}

/** 그림 자르기(`a:srcRect`). 1/1000 % 단위, 음수면 여백이다. */
internal class Crop(val l: Int, val t: Int, val r: Int, val b: Int)

/** 윤곽선. [fill] 이 [Fill.None] 이면 선이 없다. */
internal class Line(val width: Long?, val fill: Fill?, val dash: String?)

/** 모양. [preset] 이 null 이면 자유형(`custGeom`)이다. */
internal class Geometry(val preset: String?, val adj: Int?)

internal class SpPr(val xfrm: Xfrm?, val geom: Geometry?, val fill: Fill?, val line: Line?) {
    companion object {
        val EMPTY = SpPr(null, null, null, null)
    }
}

/** 테마 서식 참조(`a:lnRef`·`a:fillRef`) — 번호와 그 자리에 넣을 색. */
internal class StyleRef(val idx: Int, val color: ColorSpec?)

/** 글꼴 참조(`a:fontRef idx="minor"`) — 테마 글꼴과 글자색. */
internal class FontRef(val major: Boolean, val color: ColorSpec?)

internal class ShapeStyle(val line: StyleRef?, val fill: StyleRef?, val font: FontRef?)

/** 글상자 속성(`a:bodyPr`). 여백은 EMU. */
internal class BodyProps(
    val lIns: Long?,
    val tIns: Long?,
    val rIns: Long?,
    val bIns: Long?,
    val anchor: String?,
    val wrap: String?,
    val vert: String?,
    val fontScale: Int?,
    val lnSpcReduction: Int?,
    val warp: String?,
)

/** 줄·문단 간격. [pct] 는 1/1000 %(100000 = 한 줄), [pts] 는 1/100 pt. */
internal class Spacing(val pct: Int?, val pts: Int?)

internal sealed class Bullet {
    object None : Bullet()
    class Char(val char: String) : Bullet()
    class Auto(val scheme: String, val startAt: Int) : Bullet()

    /** 그림 글머리. 그림을 그리지 않고 점으로 대신한다. */
    object Picture : Bullet()
}

/** 글머리 색. [color] 가 null 이면 글자색을 따른다(`buClrTx`). */
internal class BulletColor(val color: ColorSpec?)

/** 글머리 글꼴. [typeface] 가 null 이면 글자 글꼴을 따른다(`buFontTx`). */
internal class BulletFont(val typeface: String?)

/** 글머리 크기. 둘 다 null 이면 글자 크기를 따른다(`buSzTx`). */
internal class BulletSize(val pct: Int?, val pts: Int?)

/** 문단 속성(`a:pPr`·`a:lvlNpPr`). */
internal class ParaProps(
    val lvl: Int? = null,
    val algn: String? = null,
    val marL: Long? = null,
    val indent: Long? = null,
    val lnSpc: Spacing? = null,
    val spcBef: Spacing? = null,
    val spcAft: Spacing? = null,
    val bullet: Bullet? = null,
    val buColor: BulletColor? = null,
    val buFont: BulletFont? = null,
    val buSize: BulletSize? = null,
    val defRPr: RunProps? = null,
)

/**
 * 글자 속성(`a:rPr`·`a:defRPr`·`a:endParaRPr`).
 *
 * @param sz 1/100 pt.
 * @param baseline 1/1000 %. 양수면 위첨자.
 * @param link 하이퍼링크의 관계 id(`hlinkClick r:id`). 빈 문자열이면 동작만 있는 링크다.
 * @param fancy 그라데이션·그림으로 글자를 채웠다 — 첫 색으로 그리고 '글자 효과' 로 센다.
 * @param lang 언어 태그(`ja-JP`). 모양을 검사한 뒤에만 담는다(`DrawingParser`).
 */
internal class RunProps(
    val sz: Int? = null,
    val b: Boolean? = null,
    val i: Boolean? = null,
    val u: String? = null,
    val strike: String? = null,
    val baseline: Int? = null,
    val cap: String? = null,
    val latin: String? = null,
    val ea: String? = null,
    val fill: Fill? = null,
    val highlight: ColorSpec? = null,
    val link: String? = null,
    val fancy: Boolean = false,
    val lang: String? = null,
)

/** 단계별 문단 서식(`a:lstStyle`·`p:titleStyle`…). [levels] 는 9 칸(lvl1..lvl9). */
internal class ListStyle(val def: ParaProps?, val levels: Array<ParaProps?>) {
    fun level(i: Int): ParaProps? = levels.getOrNull(i)
}

internal sealed class RunItem
/**
 * 글자 조각. [slideNumber] 는 슬라이드 번호 필드(`a:fld type="slidenum"`)다 — 적힌 글은 저장할 때의 값이고
 * 마스터·레이아웃에서는 `‹#›` 이다. 그릴 때 **그 슬라이드의 번호**로 바꾼다(마스터 모형은 슬라이드끼리 나눠 쓴다).
 */
internal class TextRun(val text: String, val rPr: RunProps?, val slideNumber: Boolean = false) : RunItem()
internal class LineBreak(val rPr: RunProps?) : RunItem()

internal class Para(val pPr: ParaProps?, val items: List<RunItem>, val endRPr: RunProps?) {
    /** 글자가 하나도 없다(줄바꿈도 없다). 글머리를 그리지 않고 번호도 세지 않는다. */
    val isEmpty: Boolean get() = items.none { it is LineBreak || (it is TextRun && it.text.isNotEmpty()) }
}

internal class TextBody(val bodyPr: BodyProps?, val lstStyle: ListStyle?, val paras: List<Para>) {
    /** 보이는 글자가 있는가. 빈 개체 틀을 그리지 않을 때 쓴다. */
    val hasText: Boolean get() = paras.any { p -> p.items.any { it is TextRun && it.text.isNotBlank() } }
}

/**
 * 개체 틀(`p:ph`). 형식을 적지 않으면 `obj`, 번호를 적지 않으면 0 이다(ECMA-376 §19.3.1.36).
 */
internal class Ph(val type: String, val idx: Long)

/** 비시각 속성(`p:nvSpPr` 등)에서 쓰는 것. */
internal class Nv(
    val name: String?,
    val descr: String?,
    val hidden: Boolean,
    val ph: Ph?,
    val media: Boolean,
) {
    companion object {
        val EMPTY = Nv(null, null, false, null, false)
    }
}

internal sealed class Shape {
    abstract val nv: Nv
    abstract val xfrm: Xfrm?
}

/** 도형(`p:sp`)과 연결선(`p:cxnSp`). [txXfrm] 은 SmartArt 그림(`dsp:`)의 글상자 자리다. */
internal class SpShape(
    override val nv: Nv,
    val spPr: SpPr,
    val style: ShapeStyle?,
    val text: TextBody?,
    val txXfrm: Xfrm?,
    val connector: Boolean,
) : Shape() {
    override val xfrm: Xfrm? get() = spPr.xfrm
}

/** 그림(`p:pic`). 동영상·소리도 이것이다([Nv.media]) — 포스터 그림만 그린다. */
internal class PicShape(
    override val nv: Nv,
    val spPr: SpPr,
    val blip: Fill.Picture?,
) : Shape() {
    override val xfrm: Xfrm? get() = spPr.xfrm
}

/** 그래픽 틀(`p:graphicFrame`) — 표·차트·SmartArt·삽입 개체. */
internal class FrameShape(
    override val nv: Nv,
    override val xfrm: Xfrm?,
    val content: FrameContent,
) : Shape()

/** 묶음(`p:grpSp`). */
internal class GroupShape(
    override val nv: Nv,
    override val xfrm: Xfrm?,
    val children: List<Shape>,
) : Shape()

internal sealed class FrameContent {
    class TableContent(val table: Table) : FrameContent()
    object Chart : FrameContent()

    /** SmartArt. [dataRelId] 는 데이터 부분(`r:dm`)의 관계 id — 캐시된 그림을 찾는 실마리다. */
    class Diagram(val dataRelId: String?) : FrameContent()

    /** 삽입 개체(OLE). [fallback] 은 오피스가 저장해 둔 미리 보기 그림. */
    class Ole(val fallback: PicShape?) : FrameContent()
    object Other : FrameContent()
}

internal class Table(
    val cols: List<Long>,
    val rows: List<TableRow>,
    val styleId: String?,
    val firstRow: Boolean,
    val bandRow: Boolean,
)

internal class TableRow(val height: Long, val cells: List<TableCell>)

internal class TableCell(
    val gridSpan: Int,
    val rowSpan: Int,
    val hMerge: Boolean,
    val vMerge: Boolean,
    val text: TextBody?,
    val marL: Long?,
    val marR: Long?,
    val marT: Long?,
    val marB: Long?,
    val anchor: String?,
    val fill: Fill?,
    val left: Line?,
    val right: Line?,
    val top: Line?,
    val bottom: Line?,
)

/** 배경(`p:bg`). `bgPr` 의 채우기이거나 테마 배경 참조(`bgRef`)의 색이다. */
internal class Background(val fill: Fill?, val refColor: ColorSpec?)

/**
 * 부분 하나(슬라이드·레이아웃·마스터·SmartArt 그림)를 읽은 것.
 *
 * @param name 부분 이름. **이 부분 안의 관계 id 는 이 이름의 관계로 푼다** — 마스터의 로고를
 *   슬라이드의 관계로 찾으면 엉뚱한 그림이 나온다.
 * @param clrMap 마스터면 `p:clrMap`, 슬라이드·레이아웃이면 `overrideClrMapping`(없으면 null — 마스터 것을 쓴다).
 * @param truncated 개수 상한에 걸려 뒷부분을 버렸다.
 * @param skippedGroups 너무 깊게 겹친 묶음이라 버린 수.
 * @param unknownElements 모르는 개체(잉크 `contentPart`)의 수.
 */
internal class PartModel(
    val name: String,
    val hidden: Boolean,
    val showMasterSp: Boolean,
    val background: Background?,
    val shapes: List<Shape>,
    val clrMap: Map<String, String>?,
    val titleStyle: ListStyle?,
    val bodyStyle: ListStyle?,
    val otherStyle: ListStyle?,
    val truncated: Boolean,
    val skippedGroups: Int,
    val unknownElements: Int,
)

/**
 * 테마에서 쓰는 것 — 색 12 개, 제목·본문 글꼴, 선 굵기 목록(`lnStyleLst`).
 */
internal class Theme(
    val colors: Map<String, Rgba>,
    val majorLatin: String?,
    val minorLatin: String?,
    val majorEa: String?,
    val minorEa: String?,
    val lineWidths: List<Long>,
)

/**
 * `presentation.xml` 에서 쓰는 것.
 *
 * @param slideRelIds `sldIdLst` 의 순서 그대로. **이 순서가 슬라이드 순서다** — 부분 이름
 *   (`slide1.xml`)의 순서가 아니다. 슬라이드를 옮기면 이름은 그대로고 이 목록만 바뀐다.
 * @param overflow 상한보다 많이 적혀 있었다.
 */
internal class Presentation(
    val cx: Long?,
    val cy: Long?,
    val slideRelIds: List<String>,
    val defaultTextStyle: ListStyle?,
    val overflow: Boolean,
    /** 첫 슬라이드의 번호(`presentation/@firstSlideNum`, 없으면 1). 슬라이드 번호 필드가 쓴다. */
    val firstSlideNum: Int = 1,
)
