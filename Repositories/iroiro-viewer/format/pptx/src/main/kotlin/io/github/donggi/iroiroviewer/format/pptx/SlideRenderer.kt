package io.github.donggi.iroiroviewer.format.pptx

import io.github.donggi.iroiroviewer.format.UnsupportedFeatures
import io.github.donggi.iroiroviewer.format.html.CssValues
import io.github.donggi.iroiroviewer.format.html.HtmlWriter
import io.github.donggi.iroiroviewer.format.html.StyleBuilder
import io.github.donggi.iroiroviewer.format.opc.OpcNames
import io.github.donggi.iroiroviewer.format.opc.OpcRelationship
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * 그리는 쪽이 패키지에 묻는 것. 문서(`PptxFlowDocument`)가 **자기 잠금 안에서** 답한다 —
 * 그리기는 언제나 `renderBody` 안에서 일어나고 그것이 잠금 안이다.
 */
internal interface RenderHost {
    fun relationship(source: String, id: String): OpcRelationship?

    /** 패키지 안의 이름을 실제 항목 이름으로. 없으면 null. */
    fun canonical(name: String): String?

    fun isDisplayableImage(name: String): Boolean

    /** 슬라이드 부분 이름 → 번호, 슬라이드가 아니면 -1. 슬라이드 사이의 링크에 쓴다. */
    fun slideIndexOf(partName: String): Int

    /** 번호 → 가상 경로(`~part-3.html`). */
    fun partPath(index: Int): String

    /** SmartArt 의 캐시된 그림(`dsp:drawing`). 없거나 못 읽으면 null. */
    fun diagramDrawing(source: String, dataRelId: String): PartModel?
}

/**
 * 슬라이드 하나를 HTML 로 — 이 모듈의 핵심.
 *
 * ## 화면 폭에 맞춘다
 *
 * 슬라이드는 `width:100vw` 인 상자이고, **모든 길이를 `vw` 로** 적는다(EMU ÷ 슬라이드 폭 × 100).
 * 글자 크기도 같다(pt ÷ 슬라이드 폭 pt × 100). 그래서 화면 폭이 무엇이든 슬라이드가 통째로
 * 같은 비율로 줄고 늘며, 도형과 글자의 관계가 흐트러지지 않는다. 확대는 화면(핀치)이 한다.
 *
 * ## 상속 — pptx 가 어려운 곳
 *
 * 슬라이드의 개체 틀은 자리·글자 크기·색을 거의 적지 않는다. 적지 않은 것은 **레이아웃의 같은
 * 개체 틀**, 그다음 **마스터의 개체 틀**, 그다음 **마스터의 글자 서식표**(`txStyles`), 마지막으로
 * **프레젠테이션의 기본 서식**(`defaultTextStyle`)에서 온다. 어느 개체 틀이 '같은' 것인지는
 * [matchLayout]·[matchMaster] 가 정한다(ECMA-376 §19.3.1.36 의 `idx`·`type`).
 *
 * ## 순서
 *
 * 문서 순서가 쌓는 순서다(뒤에 적힌 것이 위). 마스터의 장식 → 레이아웃의 장식 → 슬라이드의 도형.
 * 레이아웃·마스터의 **개체 틀은 그리지 않는다** — 그것은 슬라이드가 채울 틀이지 내용이 아니다.
 *
 * ## 무엇을 버렸는지 센다
 *
 * [counts] 에 **그것이 사는 부분의 이름**으로 센다. 마스터의 EMF 로고는 슬라이드마다 그려지지만
 * 한 번만 세어야 한다 — 문서가 부분별로 한 번만 옮겨 적는다.
 */
internal class SlideRenderer(
    private val host: RenderHost,
    private val slideCx: Long,
    private val slideCy: Long,
    private val slide: PartModel,
    private val layout: PartModel?,
    private val master: PartModel?,
    private val theme: Theme?,
    private val presDefaults: ListStyle?,
    maxChars: Int = HtmlWriter.DEFAULT_MAX_CHARS,
    /** 이 슬라이드의 번호(첫 슬라이드 번호 + 차례). 슬라이드 번호 필드가 이것으로 보인다. */
    private val slideNumber: Int = 1,
) {
    private val w = HtmlWriter(maxChars)

    /** 부분 이름 → 버린 것의 개수. */
    val counts = LinkedHashMap<String, UnsupportedFeatures>()

    /** 상한에 걸려 슬라이드의 일부를 그리지 못했다. */
    var truncated = false
        private set

    /** 색 대응표는 **슬라이드 → 레이아웃 → 마스터** 순서로 덮어쓴 것 하나다. 마스터의 도형도 이것으로 칠한다. */
    private val colors = ColorResolver(
        theme?.colors.orEmpty(),
        slide.clrMap ?: layout?.clrMap ?: master?.clrMap ?: emptyMap(),
    )

    /** SmartArt 그림을 그리는 중이다. */
    private var inDrawing = false

    private val layoutPhs = PhIndex(layout?.shapes.orEmpty())
    private val masterPhs = PhIndex(master?.shapes.orEmpty())

    /**
     * 개체 틀 찾기표. 목록을 도형마다 네 번씩 훑으면 슬라이드의 틀 1 만 × 레이아웃의 틀 1 만이 되어
     * 한 장을 그리는 데 수 초가 들었다(데스크톱, 잠금을 쥔 채로). **적힌 순서대로 넣고 먼저 들어간 것을
     * 지키므로** 목록에서 `firstOrNull` 로 찾던 것과 같은 틀이 나온다.
     */
    private class PhIndex(shapes: List<Shape>) {
        val byIdxClass = HashMap<Pair<Long, String>, Shape>()
        val byIdx = HashMap<Long, Shape>()
        val byType = HashMap<String, Shape>()
        val byClass = HashMap<String, Shape>()

        init {
            for (s in shapes) {
                val ph = s.nv.ph ?: continue
                val cls = phClass(ph.type)
                byIdxClass.putIfAbsent(ph.idx to cls, s)
                byIdx.putIfAbsent(ph.idx, s)
                byType.putIfAbsent(ph.type, s)
                byClass.putIfAbsent(cls, s)
            }
        }
    }

    fun render(): String {
        val st = StyleBuilder().add("width", "calc(var(--u)*100)").add("height", vw(slideCy.toDouble()))
        val bgImage = background(st)
        w.start("div", "class" to "slide", "style" to st.build())
        bgImage?.let { (url, tile) ->
            w.void("img", "class" to "bgimg", "src" to url, "alt" to "", "style" to if (tile) "object-fit:cover" else null)
        }
        // '배경 그래픽 숨기기'(슬라이드의 showMasterSp="0")는 레이아웃과 마스터의 장식을 모두 가린다.
        // 레이아웃의 showMasterSp="0" 은 마스터의 것만 가린다.
        if (master != null && slide.showMasterSp && layout?.showMasterSp != false) {
            renderShapes(master.shapes, Space.ROOT, Tree(master.name, decorations = true, slideLevel = false))
        }
        if (layout != null && slide.showMasterSp) {
            renderShapes(layout.shapes, Space.ROOT, Tree(layout.name, decorations = true, slideLevel = false))
        }
        renderShapes(slide.shapes, Space.ROOT, Tree(slide.name, decorations = false, slideLevel = true))
        w.end("div")
        for (part in listOfNotNull(slide, layout, master)) countParsed(part)
        if (slide.truncated || w.full) truncated = true
        return w.toString()
    }

    // ---- 배경 --------------------------------------------------------------------------

    /** 배경색을 [st] 에 적고, 배경 그림이 있으면 (주소, 바둑판인가)를 준다. 슬라이드 → 레이아웃 → 마스터. */
    private fun background(st: StyleBuilder): Pair<String, Boolean>? {
        val owner = listOfNotNull(slide, layout, master).firstOrNull { it.background != null }
        val bg = owner?.background
        if (owner == null || bg == null) {
            st.add("background-color", Rgba.WHITE.css())
            return null
        }
        var image: Pair<String, Boolean>? = null
        val color = when (val fill = bg.fill) {
            is Fill.Solid -> colors.resolve(fill.color)
            is Fill.Gradient -> colors.resolve(fill.first)
            is Fill.Pattern -> colors.resolve(fill.fg)
            is Fill.Picture -> {
                image = imageUrl(fill, owner.name)?.let { it to fill.tile }
                null
            }
            Fill.None, Fill.Group -> null
            null -> colors.resolve(bg.refColor)
        }
        st.add("background-color", (color ?: Rgba.WHITE).css())
        return image
    }

    // ---- 도형 나무 ------------------------------------------------------------------------

    /** 좌표계 하나 — EMU 좌표를 **담는 상자 기준** EMU 로 옮긴다. 묶음마다 하나씩 겹친다. */
    private class Space(val ox: Double, val oy: Double, val sx: Double, val sy: Double) {
        fun left(b: Box) = (b.x - ox) * sx
        fun top(b: Box) = (b.y - oy) * sy
        fun width(b: Box) = b.cx * sx
        fun height(b: Box) = b.cy * sy

        /**
         * 묶음 안의 좌표계. 자식 좌표(`chOff`·`chExt`)가 묶음의 자리(`off`·`ext`)로 늘거나 준다.
         * 겹친 묶음은 배율이 곱해진다. `chExt` 가 0 이면 늘이지 않는다(0 으로 나누지 않는다).
         */
        fun child(frame: Box, child: Box): Space {
            val kx = if (child.cx > 0) frame.cx.toDouble() / child.cx else 1.0
            val ky = if (child.cy > 0) frame.cy.toDouble() / child.cy else 1.0
            return Space(
                child.x.toDouble(),
                child.y.toDouble(),
                (sx * kx).coerceIn(MIN_SCALE, MAX_SCALE),
                (sy * ky).coerceIn(MIN_SCALE, MAX_SCALE),
            )
        }

        companion object {
            val ROOT = Space(0.0, 0.0, 1.0, 1.0)
            const val MIN_SCALE = 1e-4
            const val MAX_SCALE = 1e4
        }
    }

    /**
     * @param part 이 나무의 관계를 풀 부분(그리고 버린 것을 셀 부분).
     * @param decorations 레이아웃·마스터의 장식만 그린다(개체 틀은 건너뛴다).
     * @param slideLevel 슬라이드의 나무 — 개체 틀이 레이아웃·마스터에서 상속받는다.
     */
    private class Tree(val part: String, val decorations: Boolean, val slideLevel: Boolean)

    private fun renderShapes(shapes: List<Shape>, space: Space, tree: Tree) {
        for (s in shapes) {
            if (w.full) return
            if (s.nv.hidden) continue
            if (tree.decorations && s.nv.ph != null) continue
            when (s) {
                is SpShape -> renderSp(s, space, tree)
                is PicShape -> renderPic(s, space, tree)
                is FrameShape -> renderFrame(s, space, tree)
                is GroupShape -> renderGroup(s, space, tree)
            }
        }
    }

    private fun renderGroup(g: GroupShape, space: Space, tree: Tree) {
        val x = g.xfrm
        if (x == null) {
            renderShapes(g.children, space, tree)
            return
        }
        val st = StyleBuilder()
        place(st, space, x.box)
        // 묶음의 회전은 상자째 돌린다 — 자식이 묶음의 가운데를 축으로 함께 돈다(파워포인트와 같다).
        // 묶음의 뒤집기는 따르지 않는다(자식의 글자까지 거울상이 된다).
        st.add("transform", transform(x.rot, flipH = false, flipV = false))
        w.start("div", "class" to "grp", "style" to st.build())
        renderShapes(g.children, space.child(x.box, x.child ?: x.box), tree)
        w.end("div")
    }

    // ---- 개체 틀 상속 --------------------------------------------------------------------

    private class Chain(val layout: Shape?, val master: Shape?)

    private fun chainOf(s: Shape, tree: Tree): Chain {
        val ph = s.nv.ph ?: return NO_CHAIN
        if (!tree.slideLevel) return NO_CHAIN
        val l = matchLayout(ph)
        val m = matchMaster(l?.nv?.ph ?: ph)
        return Chain(l, m)
    }

    /**
     * 슬라이드의 개체 틀 → 레이아웃의 개체 틀. ECMA-376 §19.3.1.36 은 `idx` 로 짝을 짓게 하고,
     * 오피스는 `idx` 가 맞지 않으면 `type` 으로 찾는다. 차례:
     *
     * 1. `idx` 가 같고 부류(제목·본문·날짜…)가 같다
     * 2. `idx` 가 같다(0 이 아닐 때만 — 0 은 '적지 않았다' 와 구별되지 않는다)
     * 3. `type` 이 같다
     * 4. 부류가 같다(`obj` 가 `body` 를 찾는 길)
     */
    private fun matchLayout(ph: Ph): Shape? {
        val cls = phClass(ph.type)
        return layoutPhs.byIdxClass[ph.idx to cls]
            ?: (if (ph.idx != 0L) layoutPhs.byIdx[ph.idx] else null)
            ?: layoutPhs.byType[ph.type]
            ?: layoutPhs.byClass[cls]
    }

    /**
     * 레이아웃(또는 레이아웃이 없는 슬라이드)의 개체 틀 → 마스터의 개체 틀. 마스터는 부류마다
     * 하나씩만 두므로 `type` 으로 찾는다 — `ctrTitle` 은 `title`, `subTitle`·`obj`·`pic` 따위는 `body`.
     * (python-pptx 의 `_LayoutPlaceholder._base_placeholder` 와 같은 대응이다.)
     */
    private fun matchMaster(ph: Ph): Shape? = masterPhs.byType[ph.type] ?: masterPhs.byClass[phClass(ph.type)]

    /** 레이아웃도 마스터도 자리를 모를 때. 글자를 잃지 않으려고 부류별로 흔한 자리에 둔다. */
    private fun fallbackBox(ph: Ph?): Box {
        val cx = slideCx.toDouble()
        val cy = slideCy.toDouble()
        fun box(x: Double, y: Double, bw: Double, bh: Double) =
            Box((cx * x).toLong(), (cy * y).toLong(), (cx * bw).toLong(), (cy * bh).toLong())
        return when (ph?.let { phClass(it.type) }) {
            null -> box(0.0, 0.0, 1.0, 1.0)
            "title" -> box(0.05, 0.04, 0.9, 0.18)
            "body" -> box(0.05, 0.25, 0.9, 0.65)
            else -> box(0.05, 0.9, 0.9, 0.07)
        }
    }

    // ---- 도형 -------------------------------------------------------------------------

    /** 풀어 둔 윤곽선. */
    private class Stroke(val width: Double, val color: Rgba, val dash: String?)

    private fun renderSp(s: SpShape, space: Space, tree: Tree) {
        val chain = chainOf(s, tree)
        val lsp = chain.layout as? SpShape
        val msp = chain.master as? SpShape
        val ph = s.nv.ph
        val hasText = s.text?.hasText == true
        // 빈 개체 틀은 슬라이드 쇼에서 보이지 않는다 — 편집 화면의 안내문만 있다.
        if (ph != null && !hasText && s.spPr.fill == null) return
        val xfrm = s.spPr.xfrm ?: chain.layout?.xfrm ?: chain.master?.xfrm
            ?: (if (hasText) Xfrm(fallbackBox(ph)) else null)
            ?: return
        val style = s.style ?: lsp?.style ?: msp?.style
        val fill = s.spPr.fill ?: lsp?.spPr?.fill ?: msp?.spPr?.fill
            ?: style?.fill?.takeIf { it.idx > 0 }?.color?.let { Fill.Solid(it) }
        val stroke = resolveStroke(s.spPr.line ?: lsp?.spPr?.line ?: msp?.spPr?.line, style)
        val geom = s.spPr.geom ?: lsp?.spPr?.geom ?: msp?.spPr?.geom
        val preset = if (geom == null) "rect" else geom.preset
        if (s.connector || preset in LINE_PRESETS) {
            renderLine(xfrm, stroke, space, tree, approximate = preset !in LINE_PRESETS)
            return
        }

        val b = xfrm.box
        val st = StyleBuilder()
        place(st, space, b)
        // 글자가 있는 도형은 뒤집지 않는다 — 파워포인트는 좌우 뒤집기에서 글자를 거울상으로 만들지
        // 않고, 상하 뒤집기는 글자를 180° 돌린다.
        st.add(
            "transform",
            if (hasText) transform(xfrm.rot + if (xfrm.flipV) 180.0 else 0.0, flipH = false, flipV = false)
            else transform(xfrm.rot, xfrm.flipH, xfrm.flipV),
        )
        val fillColor = colorOf(fill)
        val polygon = preset?.let { POLYGONS[it] }
        if (polygon == null) {
            fillColor?.let { st.add("background-color", it.css()) }
            stroke?.let { st.add("border", border(it)) }
        }
        val visible = fillColor != null || fill is Fill.Picture || stroke != null
        when {
            preset == null -> if (visible) count(tree.part, UnsupportedFeatures.SHAPE)
            preset in RECT_LIKE -> Unit
            preset in ROUNDED -> {
                val r = min(space.width(b), space.height(b)) * (geom?.adj ?: DEFAULT_ROUND_ADJ).coerceIn(0, 50_000) / 100_000.0
                st.add("border-radius", vw(r))
            }
            preset in ELLIPSES -> st.add("border-radius", "50%")
            // 다각형은 채우기만 오린다(윤곽선은 그리지 않는다). 채우기 없이 선만 있으면 아무것도 안 보이니 센다.
            polygon != null -> if (fillColor == null && fill !is Fill.Picture && stroke != null) count(tree.part, UnsupportedFeatures.SHAPE)
            else -> if (visible) count(tree.part, UnsupportedFeatures.SHAPE)
        }
        if (fill is Fill.Picture) st.add("overflow", "hidden")
        w.start("div", "class" to "sp", "style" to st.build())
        if (polygon != null && fillColor != null) {
            w.start("div", "class" to "geo", "style" to StyleBuilder().add("background-color", fillColor.css()).add("clip-path", polygon).build())
            w.end("div")
        }
        if (fill is Fill.Picture) {
            val url = imageUrl(fill, tree.part)
            if (url != null) w.void("img", "class" to "fill", "src" to url, "alt" to "", "style" to cropStyle(fill.crop))
        }
        val text = s.text
        if (text != null && hasText && s.txXfrm == null) renderText(text, chain, style, ph?.type, tree)
        w.end("div")
        // SmartArt 그림의 글상자는 도형과 다른 자리에 있을 수 있다(`dsp:txXfrm`).
        val tx = s.txXfrm
        if (text != null && hasText && tx != null) {
            val ts = StyleBuilder()
            place(ts, space, tx.box)
            ts.add("transform", transform(tx.rot, flipH = false, flipV = false))
            w.start("div", "class" to "sp", "style" to ts.build())
            renderText(text, chain, style, ph?.type, tree)
            w.end("div")
        }
    }

    /**
     * 선(연결선·`line` 모양). 상자의 대각선이다 — 뒤집기가 어느 대각선인지 정하고, 회전은 상자의
     * 가운데를 축으로 두 끝을 돌린다. 꺾인·굽은 연결선은 곧은 선으로 그리고 센다.
     */
    private fun renderLine(xfrm: Xfrm, stroke: Stroke?, space: Space, tree: Tree, approximate: Boolean) {
        if (approximate) count(tree.part, UnsupportedFeatures.SHAPE)
        val s = stroke ?: return
        val b = xfrm.box
        val l = space.left(b)
        val t = space.top(b)
        val bw = space.width(b)
        val bh = space.height(b)
        var x1 = if (xfrm.flipH) l + bw else l
        var y1 = if (xfrm.flipV) t + bh else t
        var x2 = if (xfrm.flipH) l else l + bw
        var y2 = if (xfrm.flipV) t else t + bh
        if (xfrm.rot % 360.0 != 0.0) {
            val cx = l + bw / 2
            val cy = t + bh / 2
            val rad = xfrm.rot * PI / 180
            val c = cos(rad)
            val sn = sin(rad)
            val nx1 = cx + (x1 - cx) * c - (y1 - cy) * sn
            val ny1 = cy + (x1 - cx) * sn + (y1 - cy) * c
            val nx2 = cx + (x2 - cx) * c - (y2 - cy) * sn
            val ny2 = cy + (x2 - cx) * sn + (y2 - cy) * c
            x1 = nx1; y1 = ny1; x2 = nx2; y2 = ny2
        }
        val angle = atan2(y2 - y1, x2 - x1) * 180 / PI
        val st = StyleBuilder()
            .add("left", vw(x1))
            .add("top", vw(y1))
            .add("width", vw(hypot(x2 - x1, y2 - y1)))
            .add("border-top", border(s))
            .add("transform", transform(angle, flipH = false, flipV = false))
        w.start("div", "class" to "ln", "style" to st.build())
        w.end("div")
    }

    private fun renderPic(s: PicShape, space: Space, tree: Tree) {
        val chain = chainOf(s, tree)
        val xfrm = s.xfrm ?: chain.layout?.xfrm ?: chain.master?.xfrm ?: return
        // 동영상·소리는 틀지 않는다. 포스터 그림은 그린다.
        if (s.nv.media) count(tree.part, UnsupportedFeatures.EMBEDDED_OBJECT)
        // 그림 없는 그림 개체 틀은 빈 틀이다.
        val blip = s.blip ?: return
        drawPicture(blip, xfrm, resolveStroke(s.spPr.line, null), space, tree, s.nv.descr)
    }

    private fun drawPicture(blip: Fill.Picture, xfrm: Xfrm, stroke: Stroke?, space: Space, tree: Tree, alt: String?) {
        val url = imageUrl(blip, tree.part)
        val st = StyleBuilder()
        place(st, space, xfrm.box)
        st.add("transform", transform(xfrm.rot, xfrm.flipH, xfrm.flipV))
        stroke?.let { st.add("border", border(it)) }
        if (url == null) {
            // 그릴 수 없는 그림(EMF·WMF·바깥 파일) — 자리만 남긴다. 문장은 화면 몫이다(배지가 센다).
            w.start("div", "class" to "sp missing", "style" to st.build())
            w.end("div")
            return
        }
        w.start("div", "class" to "pic", "style" to st.build())
        w.void("img", "src" to url, "alt" to alt.orEmpty(), "style" to cropStyle(blip.crop))
        w.end("div")
    }

    /**
     * 자르기(`srcRect`)를 그대로 따른다 — 상자는 `overflow:hidden` 이고, 그림을 보이는 부분이
     * 상자를 채우도록 키워 옮긴다. 모두 수로 만든 값이다.
     */
    private fun cropStyle(crop: Crop?): String? {
        if (crop == null || (crop.l == 0 && crop.t == 0 && crop.r == 0 && crop.b == 0)) return null
        val fl = crop.l / 100_000.0
        val ft = crop.t / 100_000.0
        val visW = (1.0 - fl - crop.r / 100_000.0).coerceAtLeast(0.01)
        val visH = (1.0 - ft - crop.b / 100_000.0).coerceAtLeast(0.01)
        val wPct = 100.0 / visW
        val hPct = 100.0 / visH
        return StyleBuilder()
            .add("left", CssValues.percent(-fl * wPct, -100_000.0, 100_000.0))
            .add("top", CssValues.percent(-ft * hPct, -100_000.0, 100_000.0))
            .add("width", CssValues.percent(wPct, 0.0, 100_000.0))
            .add("height", CssValues.percent(hPct, 0.0, 100_000.0))
            .build()
    }

    private fun renderFrame(s: FrameShape, space: Space, tree: Tree) {
        val chain = chainOf(s, tree)
        val xfrm = s.xfrm ?: chain.layout?.xfrm ?: chain.master?.xfrm ?: return
        when (val c = s.content) {
            is FrameContent.TableContent -> renderTable(c.table, xfrm, space, tree)
            FrameContent.Chart -> {
                // 차트는 그리지 않는다. 빈 테두리 상자를 두어 '여기 무언가 있었다' 가 보이게 한다.
                count(tree.part, UnsupportedFeatures.CHART)
                emptyBox("chart", xfrm, space)
            }
            is FrameContent.Diagram -> {
                // SmartArt 그림 안의 SmartArt 는 따라가지 않는다 — 그림이 자기를 가리키는 고리를 여기서 끊는다.
                val drawing = if (inDrawing) null else c.dataRelId?.let { host.diagramDrawing(tree.part, it) }
                if (drawing == null) {
                    count(tree.part, UnsupportedFeatures.SMART_ART)
                    emptyBox("missing", xfrm, space)
                } else {
                    renderDrawing(drawing, xfrm, space)
                }
            }
            is FrameContent.Ole -> {
                count(tree.part, UnsupportedFeatures.EMBEDDED_OBJECT)
                val pic = c.fallback?.blip
                if (pic != null) drawPicture(pic, xfrm, null, space, tree, null) else emptyBox("missing", xfrm, space)
            }
            FrameContent.Other -> {
                count(tree.part, UnsupportedFeatures.EMBEDDED_OBJECT)
                emptyBox("missing", xfrm, space)
            }
        }
    }

    /**
     * SmartArt 의 캐시된 그림. 도형 좌표가 **그래픽 틀의 왼쪽 위를 원점으로** 적혀 있으므로 틀의
     * 자리에 상자를 하나 두고 그 안에 늘이지 않고 그린다. 관계는 그림 부분의 것으로 푼다.
     */
    private fun renderDrawing(drawing: PartModel, frame: Xfrm, space: Space) {
        val st = StyleBuilder()
        place(st, space, frame.box)
        st.add("transform", transform(frame.rot, flipH = false, flipV = false))
        w.start("div", "class" to "grp", "style" to st.build())
        inDrawing = true
        try {
            renderShapes(drawing.shapes, Space(0.0, 0.0, space.sx, space.sy), Tree(drawing.name, decorations = false, slideLevel = false))
        } finally {
            inDrawing = false
        }
        w.end("div")
        countParsed(drawing)
    }

    private fun emptyBox(cls: String, xfrm: Xfrm, space: Space) {
        val st = StyleBuilder()
        place(st, space, xfrm.box)
        st.add("transform", transform(xfrm.rot, flipH = false, flipV = false))
        w.start("div", "class" to "sp $cls", "style" to st.build())
        w.end("div")
    }

    // ---- 표 ---------------------------------------------------------------------------

    private fun renderTable(t: Table, xfrm: Xfrm, space: Space, tree: Tree) {
        val b = xfrm.box
        val gridW = t.cols.sum()
        val st = StyleBuilder()
            .add("left", vw(space.left(b)))
            .add("top", vw(space.top(b)))
            .add("width", vw((if (gridW > 0) gridW else b.cx).toDouble() * space.sx))
        w.start("table", "class" to "tbl", "style" to st.build())
        if (t.cols.isNotEmpty()) {
            w.start("colgroup")
            for (c in t.cols) w.void("col", "style" to StyleBuilder().add("width", vw(c * space.sx)).build())
            w.end("colgroup")
        }
        val look = TableLook.of(t)
        for ((ri, row) in t.rows.withIndex()) {
            if (w.full) break
            w.start("tr", "style" to StyleBuilder().add("height", vw(row.height * space.sy)).build())
            for (cell in row.cells) {
                // 병합된 칸은 앞 칸의 colspan·rowspan 이 덮는다. 적어 두면 표가 옆으로 밀린다.
                if (cell.hMerge || cell.vMerge) continue
                renderCell(cell, look?.row(t, ri), tree)
            }
            w.end("tr")
        }
        w.end("table")
    }

    private fun renderCell(cell: TableCell, look: TableLook.RowLook?, tree: Tree) {
        val st = StyleBuilder()
        st.add(
            "padding",
            listOf(cell.marT ?: DEFAULT_TB_INSET, cell.marR ?: DEFAULT_LR_INSET, cell.marB ?: DEFAULT_TB_INSET, cell.marL ?: DEFAULT_LR_INSET)
                .map { vw(it.toDouble()) ?: "0" }.joinToString(" "),
        )
        st.add("vertical-align", when (cell.anchor) { "ctr" -> "middle"; "b" -> "bottom"; else -> null })
        val fill = cell.fill
        val fillColor = if (fill != null) colorOf(fill) else look?.fill?.let { colors.resolve(it) }
        fillColor?.let { st.add("background-color", it.css()) }
        val sides = listOf("border-left" to cell.left, "border-right" to cell.right, "border-top" to cell.top, "border-bottom" to cell.bottom)
        for ((prop, line) in sides) {
            when {
                line?.fill is Fill.None -> st.add(prop, "none")
                line != null -> resolveStroke(line, null)?.let { st.add(prop, border(it)) }
                look != null -> look.border?.let { colors.resolve(it) }?.let { st.add(prop, border(Stroke(TableLook.BORDER_W, it, null))) }
            }
        }
        w.start(
            "td",
            "colspan" to cell.gridSpan.takeIf { it > 1 }?.toString(),
            "rowspan" to cell.rowSpan.takeIf { it > 1 }?.toString(),
            "style" to st.build(),
        )
        val text = cell.text
        if (text != null) {
            paragraphs(text.paras, listOf(text.lstStyle, master?.otherStyle, presDefaults), look?.text, null, null, false, DEFAULT_SIZE, tree)
        }
        w.end("td")
    }

    // ---- 글 ---------------------------------------------------------------------------

    private fun renderText(body: TextBody, chain: Chain, style: ShapeStyle?, phType: String?, tree: Tree) {
        val lb = (chain.layout as? SpShape)?.text
        val mb = (chain.master as? SpShape)?.text
        val props = listOfNotNull(body.bodyPr, lb?.bodyPr, mb?.bodyPr)
        val lIns = props.firstNotNullOfOrNull { it.lIns } ?: DEFAULT_LR_INSET
        val tIns = props.firstNotNullOfOrNull { it.tIns } ?: DEFAULT_TB_INSET
        val rIns = props.firstNotNullOfOrNull { it.rIns } ?: DEFAULT_LR_INSET
        val bIns = props.firstNotNullOfOrNull { it.bIns } ?: DEFAULT_TB_INSET
        val anchor = props.firstNotNullOfOrNull { it.anchor }
        val wrap = props.firstNotNullOfOrNull { it.wrap }
        val vert = props.firstNotNullOfOrNull { it.vert } ?: "horz"
        val warp = body.bodyPr?.warp
        if (warp != null && warp != "textNoShape") count(tree.part, UnsupportedFeatures.TEXT_EFFECT)
        val st = StyleBuilder()
            .add("padding", listOf(tIns, rIns, bIns, lIns).map { vw(it.toDouble()) ?: "0" }.joinToString(" "))
            .add("justify-content", when (anchor) { "ctr" -> "center"; "b" -> "flex-end"; else -> null })
        when (vert) {
            "horz" -> Unit
            // 세로쓰기. 동아시아 글자는 서고 라틴 글자는 눕는다 — `vert` 는 파워포인트에서 전부 눕지만 가깝다.
            "vert", "eaVert", "mongolianVert" -> st.add("writing-mode", "vertical-rl")
            else -> count(tree.part, UnsupportedFeatures.TEXT_EFFECT)
        }
        val cls = phType?.let { phClass(it) }
        val masterStyle = when (cls) {
            "title" -> master?.titleStyle
            "body" -> master?.bodyStyle
            else -> master?.otherStyle
        }
        val fontRef = style?.font?.let { f ->
            RunProps(
                latin = if (f.major) "+mj-lt" else "+mn-lt",
                ea = if (f.major) "+mj-ea" else "+mn-ea",
                fill = f.color?.let { Fill.Solid(it) },
            )
        }
        w.start("div", "class" to "tx", "style" to st.build())
        paragraphs(
            body.paras,
            listOf(body.lstStyle, lb?.lstStyle, mb?.lstStyle, masterStyle, presDefaults),
            fontRef,
            body.bodyPr?.fontScale,
            body.bodyPr?.lnSpcReduction,
            wrap == "none",
            if (cls == "title") DEFAULT_TITLE_SIZE else DEFAULT_SIZE,
            tree,
        )
        w.end("div")
    }

    /** 풀어 둔 글자 모양. [size] 는 1/100 pt. */
    private class RunStyle(
        val size: Double,
        val bold: Boolean,
        val italic: Boolean,
        val underline: Boolean,
        val strike: Boolean,
        val baseline: Int,
        val cap: String?,
        val latin: String?,
        val ea: String?,
        val color: Rgba,
        val highlight: Rgba?,
        val link: String?,
        val lang: String?,
    )

    /**
     * 문단들을 그린다.
     *
     * @param lists 서식표의 사슬. **[0] 은 도형 자신의 것**이고 그 뒤로 레이아웃·마스터 개체 틀,
     *   마스터 서식표, 프레젠테이션 기본값이 온다. null 칸은 건너뛴다.
     * @param extra 도형 서식의 글꼴 참조(`fontRef`)·표 서식의 글자. **도형 자신의 서식표 바로 뒤**에
     *   끼운다 — 사용자가 적은 서식은 이기고, 마스터의 기본 글자색(tx1)에는 이긴다. 기본 도형의 흰 글자가 이것이다.
     */
    private fun paragraphs(
        paras: List<Para>,
        lists: List<ListStyle?>,
        extra: RunProps?,
        fontScale: Int?,
        lnSpcReduction: Int?,
        nowrap: Boolean,
        defaultSize: Int,
        tree: Tree,
    ) {
        val scale = (fontScale ?: 100_000).coerceIn(1_000, 100_000) / 100_000.0
        val reduction = (lnSpcReduction ?: 0).coerceIn(0, 90_000)
        // 자동 번호. 같은 단계에서 같은 모양이 이어지면 센다. 얕은 단계가 나오면 깊은 단계는 다시 센다.
        val counters = IntArray(9)
        val schemes = arrayOfNulls<String>(9)
        val active = BooleanArray(9)
        var fancy = false
        for (para in paras) {
            if (w.full) return
            val level = (para.pPr?.lvl ?: 0).coerceIn(0, 8)
            val pChain = ArrayList<ParaProps>(12)
            val rChain = ArrayList<RunProps>(12)
            para.pPr?.let { pp ->
                pChain.add(pp)
                pp.defRPr?.let { rChain.add(it) }
            }
            for ((i, ls) in lists.withIndex()) {
                ls?.level(level)?.let { pp ->
                    pChain.add(pp)
                    pp.defRPr?.let { rChain.add(it) }
                }
                ls?.def?.let { pp ->
                    pChain.add(pp)
                    pp.defRPr?.let { rChain.add(it) }
                }
                if (i == 0 && extra != null) rChain.add(extra)
            }
            val bullet = pChain.pick { it.bullet }
            if (!para.isEmpty) {
                for (k in level + 1 until 9) active[k] = false
                if (bullet is Bullet.Auto) {
                    if (active[level] && schemes[level] == bullet.scheme) {
                        counters[level]++
                    } else {
                        counters[level] = bullet.startAt
                        schemes[level] = bullet.scheme
                        active[level] = true
                    }
                } else {
                    active[level] = false
                }
            }
            if (para.items.any { it is TextRun && it.rPr?.fancy == true }) fancy = true
            renderPara(para, pChain, rChain, bullet, counters[level], scale, reduction, nowrap, defaultSize, tree)
        }
        if (fancy) count(tree.part, UnsupportedFeatures.TEXT_EFFECT)
    }

    private fun renderPara(
        para: Para,
        pChain: List<ParaProps>,
        rChain: List<RunProps>,
        bullet: Bullet?,
        number: Int,
        scale: Double,
        reduction: Int,
        nowrap: Boolean,
        defaultSize: Int,
        tree: Tree,
    ) {
        val firstProps = para.items.firstNotNullOfOrNull { (it as? TextRun)?.takeIf { r -> r.text.isNotEmpty() }?.rPr }
            ?: para.items.firstNotNullOfOrNull { (it as? LineBreak)?.rPr }
            ?: para.endRPr
        val first = resolveRun(firstProps, rChain, defaultSize)
        val size = first.size * scale
        val marL = pChain.pick { it.marL } ?: 0L
        val indent = pChain.pick { it.indent } ?: 0L
        val st = StyleBuilder()
        st.add("text-align", ALIGN[pChain.pick { it.algn }])
        if (marL != 0L) st.add("padding-left", vw(marL.toDouble()))
        if (indent != 0L) st.add("text-indent", vw(indent.toDouble()))
        st.add("font-size", fontVw(size))
        st.add("line-height", lineHeight(pChain.pick { it.lnSpc }, reduction))
        st.add("margin-top", spacing(pChain.pick { it.spcBef }, size))
        st.add("margin-bottom", spacing(pChain.pick { it.spcAft }, size))
        if (nowrap) st.add("white-space", "pre")
        w.start("p", "style" to st.build())
        if (!para.isEmpty && bullet != null && bullet !is Bullet.None) writeBullet(bullet, number, pChain, first, scale, indent)
        for (item in para.items) {
            when (item) {
                is TextRun -> {
                    // 슬라이드 번호 필드는 적힌 글(마스터에서는 '‹#›')이 아니라 이 슬라이드의 번호로 보인다.
                    val text = if (item.slideNumber) slideNumber.toString() else item.text
                    if (text.isNotEmpty()) writeRun(resolveRun(item.rPr, rChain, defaultSize), text, scale, tree)
                }
                is LineBreak -> w.void("br")
            }
        }
        // 빈 문단도 높이를 가진다(끝 글자 크기로). 줄바꿈으로 끝나는 문단은 빈 줄이 하나 더 보인다.
        if (para.isEmpty || para.items.lastOrNull() is LineBreak) w.void("br")
        w.end("p")
    }

    private fun writeBullet(bullet: Bullet, number: Int, pChain: List<ParaProps>, first: RunStyle, scale: Double, indent: Long) {
        val buFont = pChain.pick { it.buFont }
        val (text, keepFont) = when (bullet) {
            is Bullet.Char -> Bullets.bulletChar(bullet.char, buFont?.typeface)
            is Bullet.Auto -> Bullets.autoNumber(bullet.scheme, number) to true
            else -> "•" to true
        }
        val color = pChain.pick { it.buColor }?.color?.let { colors.resolve(it) } ?: first.color
        val buSize = pChain.pick { it.buSize }
        val sizeHpt = when {
            buSize?.pct != null -> first.size * buSize.pct.coerceIn(25_000, 400_000) / 100_000.0
            buSize?.pts != null -> buSize.pts.coerceIn(100, 400_000).toDouble()
            else -> first.size
        } * scale
        val st = StyleBuilder().add("font-size", fontVw(sizeHpt)).add("color", color.css())
        if (keepFont) {
            val face = buFont?.typeface
            st.add("font-family", if (face != null) fontFamily(face, null) else fontFamily(first.latin, first.ea))
        }
        // 내어쓰기(음수 indent)면 글머리가 그 폭을 차지해 본문이 marL 에서 시작한다.
        if (indent < 0) st.add("min-width", vw(-indent.toDouble())) else st.add("padding-right", "0.4em")
        w.start("span", "class" to "bu", "style" to st.build()).text(text).end("span")
    }

    private fun resolveRun(own: RunProps?, chain: List<RunProps>, defaultSize: Int): RunStyle {
        fun <T> pick(f: (RunProps) -> T?): T? {
            if (own != null) f(own)?.let { return it }
            for (r in chain) f(r)?.let { return it }
            return null
        }
        val link = own?.link
        var color = when (val fill = pick { it.fill }) {
            is Fill.Solid -> colors.resolve(fill.color)
            is Fill.Gradient -> colors.resolve(fill.first)
            is Fill.Pattern -> colors.resolve(fill.fg)
            Fill.None -> Rgba(0.0, 0.0, 0.0, 0.0)
            else -> null
        } ?: colors.schemeColor("tx1") ?: Rgba.BLACK
        // 하이퍼링크는 테마의 링크 색과 밑줄로 그린다(파워포인트의 기본 동작).
        if (link != null) color = colors.schemeColor("hlink") ?: color
        val u = pick { it.u }
        return RunStyle(
            size = (pick { it.sz } ?: defaultSize).toDouble(),
            bold = pick { it.b } ?: false,
            italic = pick { it.i } ?: false,
            underline = link != null || (u != null && u != "none"),
            strike = pick { it.strike }.let { it != null && it != "noStrike" },
            baseline = pick { it.baseline } ?: 0,
            cap = pick { it.cap },
            latin = pick { it.latin },
            ea = pick { it.ea },
            color = color,
            highlight = pick { it.highlight }?.let { colors.resolve(it) },
            link = link,
            lang = pick { it.lang },
        )
    }

    private fun writeRun(rs: RunStyle, text: String, scale: Double, tree: Tree) {
        var size = rs.size * scale
        if (rs.baseline != 0) size *= SCRIPT_SCALE
        val st = StyleBuilder()
        st.add("font-size", fontVw(size))
        if (rs.bold) st.add("font-weight", "bold")
        if (rs.italic) st.add("font-style", "italic")
        val deco = listOfNotNull(if (rs.underline) "underline" else null, if (rs.strike) "line-through" else null)
        if (deco.isNotEmpty()) st.add("text-decoration", deco.joinToString(" "))
        st.add("color", rs.color.css())
        st.add("font-family", fontFamily(rs.latin, rs.ea))
        if (rs.baseline > 0) st.add("vertical-align", "super") else if (rs.baseline < 0) st.add("vertical-align", "sub")
        when (rs.cap) {
            "all" -> st.add("text-transform", "uppercase")
            "small" -> st.add("font-variant", "small-caps")
        }
        rs.highlight?.let { st.add("background-color", it.css()) }
        // 슬라이드 사이의 링크만 살린다. 바깥 주소는 따라가지 않는다(위생기가 어차피 지운다) — 글자만 남긴다.
        // 버린 것은 센다. 다른 형식에서는 위생기가 `href` 를 지우며 세는 것을 여기서는 애초에 쓰지 않으므로
        // 우리가 세지 않으면 배지에 나타나지 않는다.
        val rel = rs.link?.takeIf { it.isNotEmpty() }?.let { host.relationship(tree.part, it) }
        if (rel != null && rel.external) count(tree.part, UnsupportedFeatures.REMOTE_REFERENCE)
        val href = rel
            ?.takeIf { !it.external }
            ?.let { host.slideIndexOf(it.target) }
            ?.takeIf { it >= 0 }
            ?.let { host.partPath(it) }
        if (href != null) w.start("a", "href" to href)
        // 한자는 한중일이 같은 코드 포인트를 쓰면서 자형이 다르다(한자 통합). 화면의 기본 언어는
        // 한국어라, 일본어·중국어 조각에 언어를 달지 않으면 그 한자만 한국 자형으로 그려진다.
        val lang = rs.lang?.takeIf { it.startsWith("ja", ignoreCase = true) || it.startsWith("zh", ignoreCase = true) }
        w.start("span", "lang" to lang, "style" to st.build()).text(text).end("span")
        if (href != null) w.end("a")
    }

    /** 테마 글꼴(`+mj-lt`)을 풀고 CSS 글꼴 목록으로. 이름은 `CssValues.fontFamily` 를 지난다. */
    private fun fontFamily(latin: String?, ea: String?): String? {
        val names = listOfNotNull(themeFont(latin), themeFont(ea)).distinct().mapNotNull { CssValues.fontFamily(it) }
        return if (names.isEmpty()) null else names.joinToString(",")
    }

    private fun themeFont(face: String?): String? = when (face) {
        null -> null
        "+mj-lt" -> theme?.majorLatin
        "+mn-lt" -> theme?.minorLatin
        "+mj-ea" -> theme?.majorEa
        "+mn-ea" -> theme?.minorEa
        else -> if (face.startsWith("+")) null else face
    }

    /** 줄 간격. 백분율은 '한 줄 = 글자 크기의 1.2 배' 를 기준으로 곱한다. */
    private fun lineHeight(spc: Spacing?, reduction: Int): String? {
        spc?.pct?.let { return number(1.2 * (it - reduction).coerceIn(10_000, 1_000_000) / 100_000.0) }
        spc?.pts?.let { return fontVw(it.coerceIn(0, 400_000).toDouble()) }
        if (reduction > 0) return number(1.2 * (100_000 - reduction) / 100_000.0)
        return null
    }

    /** 문단 앞뒤 간격. 백분율은 줄 높이(글자 크기 × 1.2)에 대한 비율이다. */
    private fun spacing(spc: Spacing?, sizeHpt: Double): String? {
        spc?.pct?.let { return vw(it.coerceIn(0, 1_000_000) / 100_000.0 * 1.2 * sizeHpt * EMU_PER_HPT) }
        spc?.pts?.let { return fontVw(it.coerceIn(0, 400_000).toDouble()) }
        return null
    }

    // ---- 색·선·그림 ---------------------------------------------------------------------

    private fun colorOf(fill: Fill?): Rgba? = when (fill) {
        is Fill.Solid -> colors.resolve(fill.color)
        is Fill.Gradient -> colors.resolve(fill.first)
        is Fill.Pattern -> colors.resolve(fill.fg)
        else -> null
    }?.takeIf { !it.isTransparent }

    /**
     * 윤곽선을 푼다. 선의 색이 없으면 도형 서식의 선 참조(`lnRef`)에서, 굵기가 없으면 테마의
     * 선 목록에서 가져온다. 색이 없으면(선 없음) null.
     */
    private fun resolveStroke(line: Line?, style: ShapeStyle?): Stroke? {
        val ref = style?.line?.takeIf { it.idx > 0 }
        val fill = line?.fill ?: ref?.color?.let { Fill.Solid(it) }
        val color = colorOf(fill) ?: return null
        val width = line?.width ?: ref?.let { theme?.lineWidths?.getOrNull(it.idx - 1) } ?: DEFAULT_LINE_W
        return Stroke(width.coerceAtLeast(MIN_LINE_W).toDouble(), color, line?.dash)
    }

    private fun border(s: Stroke): String? {
        val width = vw(s.width) ?: return null
        val style = when (s.dash) {
            "dot", "sysDot" -> "dotted"
            null, "solid" -> "solid"
            else -> "dashed"
        }
        return "$width $style ${s.color.css()}"
    }

    /**
     * 그림 관계를 풀어 본문에 적을 주소로. 그릴 수 없으면 null 이고 그 까닭을 센다 —
     * 바깥 파일은 '연결된 바깥 파일', 없는 부분·그릴 수 없는 형식(EMF·WMF)은 '그릴 수 없는 그림'.
     */
    private fun imageUrl(pic: Fill.Picture, source: String): String? {
        val id = pic.embed
        if (id == null) {
            if (pic.link != null) count(source, UnsupportedFeatures.LINKED_FILE)
            return null
        }
        val rel = host.relationship(source, id)
        if (rel == null) {
            count(source, UnsupportedFeatures.UNSUPPORTED_IMAGE)
            return null
        }
        if (rel.external) {
            count(source, UnsupportedFeatures.LINKED_FILE)
            return null
        }
        val name = host.canonical(rel.target)
        if (name == null || !host.isDisplayableImage(name)) {
            count(source, UnsupportedFeatures.UNSUPPORTED_IMAGE)
            return null
        }
        return OpcNames.toUrl(name)
    }

    // ---- 바탕 ---------------------------------------------------------------------------

    private fun countParsed(part: PartModel) {
        if (part.skippedGroups > 0) count(part.name, UnsupportedFeatures.SHAPE, part.skippedGroups)
        if (part.unknownElements > 0) count(part.name, UnsupportedFeatures.UNKNOWN_ELEMENT, part.unknownElements)
    }

    private fun count(part: String, kind: String, n: Int = 1) {
        counts.getOrPut(part) { UnsupportedFeatures() }.record(kind, n)
    }

    private fun place(st: StyleBuilder, space: Space, b: Box) {
        st.add("left", vw(space.left(b)))
            .add("top", vw(space.top(b)))
            .add("width", vw(space.width(b)))
            .add("height", vw(space.height(b)))
    }

    /** EMU(담는 상자 기준) → `vw`. */
    /**
     * EMU → **슬라이드 단위**(`--u` 는 슬라이드 폭의 1%). 값은 `CssValues.vw` 가 자르고 적은 숫자를 그대로
     * 쓰고 단위만 바꾼다 — 화면이 슬라이드보다 가로로 길면(가로로 든 태블릿) 폭이 아니라 **높이에** 맞춰야
     * 슬라이드가 한 화면에 들어오는데, `vw` 로 적어 두면 그 전환을 CSS 한 줄로 할 수 없다
     * ([PptxDocument] 의 `fitCss`). 12단계 기기 확인에서 4:3 슬라이드의 아래쪽이 화면 밖으로 나가 잡혔다.
     */
    private fun vw(emu: Double): String? =
        CssValues.vw(emu / slideCx * 100.0)?.removeSuffix("vw")?.let { "calc(var(--u)*$it)" }

    /** 1/100 pt → `vw`. */
    private fun fontVw(hpt: Double): String? = vw(hpt * EMU_PER_HPT)

    private inline fun <T> List<ParaProps>.pick(f: (ParaProps) -> T?): T? {
        for (x in this) f(x)?.let { return it }
        return null
    }

    companion object {
        /** 개체 틀의 부류. 서식표(title·body·other)와 마스터의 짝을 고르는 데 쓴다. */
        fun phClass(type: String): String = when (type) {
            "title", "ctrTitle" -> "title"
            "dt", "ftr", "sldNum", "hdr" -> type
            else -> "body"
        }

        /** `transform` 값. 수로만 만든다. 할 것이 없으면 null. */
        fun transform(rot: Double, flipH: Boolean, flipV: Boolean): String? {
            val parts = ArrayList<String>(3)
            val r = rot % 360.0
            if (r.isFinite() && r != 0.0) parts.add("rotate(" + number(r) + "deg)")
            if (flipH) parts.add("scaleX(-1)")
            if (flipV) parts.add("scaleY(-1)")
            return if (parts.isEmpty()) null else parts.joinToString(" ")
        }

        /** 단위 없는 수. 소수 넷째 자리까지, 끝의 0 을 뗀다(`CssValues` 와 같은 모양). */
        fun number(v: Double): String {
            val s = String.format(Locale.ROOT, "%.4f", if (v.isFinite()) v else 0.0).trimEnd('0').trimEnd('.')
            return if (s == "-0" || s.isEmpty()) "0" else s
        }

        private val NO_CHAIN = Chain(null, null)

        /** 1/100 pt 의 EMU(12700 EMU = 1 pt). */
        const val EMU_PER_HPT = 127.0

        /** 글상자 여백의 기본값(EMU) — 좌우 0.1 인치, 위아래 0.05 인치. */
        const val DEFAULT_LR_INSET = 91_440L
        const val DEFAULT_TB_INSET = 45_720L

        /** 아무 서식도 없을 때의 글자 크기(1/100 pt). 제목은 오피스 기본 마스터의 44pt. */
        const val DEFAULT_SIZE = 1_800
        const val DEFAULT_TITLE_SIZE = 4_400

        private const val DEFAULT_LINE_W = 9_525L

        /** 너무 가는 선은 화면에서 사라진다. 0.25pt 보다 가늘게 그리지 않는다. */
        private const val MIN_LINE_W = 3_175L

        private const val DEFAULT_ROUND_ADJ = 16_667

        /** 위·아래첨자의 크기. */
        private const val SCRIPT_SCALE = 2.0 / 3.0

        private val ALIGN = mapOf("l" to "left", "ctr" to "center", "r" to "right", "just" to "justify", "dist" to "justify")

        private val LINE_PRESETS = setOf("line", "straightConnector1")

        private val RECT_LIKE = setOf("rect", "flowChartProcess", "snip1Rect", "frame")
        private val ROUNDED = setOf("roundRect", "flowChartAlternateProcess", "round1Rect", "round2SameRect")
        private val ELLIPSES = setOf("ellipse", "flowChartConnector")

        /**
         * 흔한 다각형의 `clip-path`. **상수다** — 문서의 조정값(`adj`)은 따르지 않고 기본 모양으로 그린다.
         */
        private val POLYGONS = mapOf(
            "triangle" to "polygon(50% 0,100% 100%,0 100%)",
            "rtTriangle" to "polygon(0 0,100% 100%,0 100%)",
            "diamond" to "polygon(50% 0,100% 50%,50% 100%,0 50%)",
            "flowChartDecision" to "polygon(50% 0,100% 50%,50% 100%,0 50%)",
            "parallelogram" to "polygon(25% 0,100% 0,75% 100%,0 100%)",
            "trapezoid" to "polygon(25% 0,75% 0,100% 100%,0 100%)",
            "pentagon" to "polygon(50% 0,100% 38%,81% 100%,19% 100%,0 38%)",
            "hexagon" to "polygon(25% 0,75% 0,100% 50%,75% 100%,25% 100%,0 50%)",
            "octagon" to "polygon(29% 0,71% 0,100% 29%,100% 71%,71% 100%,29% 100%,0 71%,0 29%)",
            "homePlate" to "polygon(0 0,85% 0,100% 50%,85% 100%,0 100%)",
            "chevron" to "polygon(0 0,85% 0,100% 50%,85% 100%,0 100%,15% 50%)",
            "rightArrow" to "polygon(0 25%,70% 25%,70% 0,100% 50%,70% 100%,70% 75%,0 75%)",
            "leftArrow" to "polygon(30% 0,30% 25%,100% 25%,100% 75%,30% 75%,30% 100%,0 50%)",
            "upArrow" to "polygon(50% 0,100% 30%,75% 30%,75% 100%,25% 100%,25% 30%,0 30%)",
            "downArrow" to "polygon(25% 0,75% 0,75% 70%,100% 70%,50% 100%,0 70%,25% 70%)",
        )
    }
}

/**
 * 표 서식 가운데 **오피스 기본값 하나**('보통 스타일 2 - 강조 1')를 흉내 낸다.
 *
 * 기본 제공 표 서식은 파일에 저장되지 않는다 — `tableStyleId` 로 GUID 만 적힌다. 파워포인트와
 * python-pptx 가 새 표에 붙이는 것이 이 하나라, 이것만 알아도 대부분의 표가 머리 행의 색을 얻는다.
 * 다른 GUID 는 서식 없이(회색 칸선) 그린다.
 */
internal object TableLook {

    const val MEDIUM_STYLE_2_ACCENT_1 = "{5C22544A-7EE6-4342-B048-85BDC9FD1C3A}"

    /** 칸선 굵기(EMU). 1 pt. */
    const val BORDER_W = 12_700.0

    /** 행 하나의 모양 — 칸 채우기, 글자(굵기·색), 칸선 색. */
    class RowLook(val fill: ColorSpec?, val text: RunProps?, val border: ColorSpec?)

    /**
     * 머리 행(`firstRow`)은 강조 1 에 흰 굵은 글자, 나머지는 강조 1 의 옅은 색이고 줄무늬(`bandRow`)면
     * 한 줄 걸러 조금 더 짙다(tint 40% / 20%). 칸선은 흰 1 pt.
     */
    class Look {
        fun row(t: Table, index: Int): RowLook {
            if (t.firstRow && index == 0) {
                return RowLook(scheme("accent1"), RunProps(b = true, fill = Fill.Solid(scheme("lt1"))), scheme("lt1"))
            }
            val bodyIndex = if (t.firstRow) index - 1 else index
            val tint = if (t.bandRow && bodyIndex % 2 == 0) 40_000 else 20_000
            return RowLook(scheme("accent1", ColorMod("tint", tint)), RunProps(fill = Fill.Solid(scheme("dk1"))), scheme("lt1"))
        }

        private fun scheme(name: String, vararg mods: ColorMod) = ColorSpec(ColorKind.SCHEME, name, mods = mods.toList())
    }

    private val LOOK = Look()

    fun of(t: Table): Look? = if (t.styleId.equals(MEDIUM_STYLE_2_ACCENT_1, ignoreCase = true)) LOOK else null
}
