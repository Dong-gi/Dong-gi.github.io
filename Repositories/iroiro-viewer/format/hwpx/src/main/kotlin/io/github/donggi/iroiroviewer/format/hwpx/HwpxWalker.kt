package io.github.donggi.iroiroviewer.format.hwpx

import io.github.donggi.iroiroviewer.format.UnsupportedFeatures
import io.github.donggi.iroiroviewer.format.html.BulletGlyphs
import io.github.donggi.iroiroviewer.format.html.CssValues
import io.github.donggi.iroiroviewer.format.html.FlowDocumentBase
import io.github.donggi.iroiroviewer.format.html.FlowUrls
import io.github.donggi.iroiroviewer.format.html.HancomAlt
import io.github.donggi.iroiroviewer.format.html.HancomChars
import io.github.donggi.iroiroviewer.format.html.HancomFonts
import io.github.donggi.iroiroviewer.format.html.HancomNumbers
import io.github.donggi.iroiroviewer.format.html.HtmlWriter
import io.github.donggi.iroiroviewer.format.html.StyleBuilder
import io.github.donggi.iroiroviewer.safety.nextGuarded
import org.xmlpull.v1.XmlPullParser

/** 문단이 어디에 있는가. 구역 바로 아래([BODY])만 번호를 받고 조각의 경계가 된다. */
internal enum class Where { BODY, CELL, BOX, NOTE, CAPTION }

/**
 * 걷기 한 번의 설정. 훑기(여는 동안 한 번)와 그리기(조각마다)가 **같은 걷기**를 쓰고, 다른 것은 여기 적힌 것뿐이다.
 *
 * @param scan 훑기면 있다. 조각 경계·제목·책갈피·표의 칸 정보를 모은다.
 * @param recordFeatures 버린 것을 센다. 훑기만 켠다([HwpxEnv.features] 의 주석).
 * @param start 그리기에서 이 조각의 첫 최상위 문단. 앞의 문단은 들여다보지 않고 건너뛴다.
 * @param end 그리기에서 이 조각이 끝나는 문단(포함하지 않는다).
 * @param sectionInfo 걷기를 시작할 때의 구역 설정. 구역의 처음부터 걸으면 기본값에서 시작해 `hp:secPr` 이 고친다.
 * @param maxChars 본문 쓰개의 상한 — 개체 뒤에 붙일 캡션을 따로 쓸 때 남은 몫을 잰다([HwpxWalker.caption]).
 */
internal class WalkConfig(
    val section: Int,
    val scan: ScanCollector? = null,
    val recordFeatures: Boolean = false,
    val start: Int = 0,
    val end: Int = Int.MAX_VALUE,
    val partIndex: Int = 0,
    val bookmarkParts: Map<String, Int> = emptyMap(),
    val tables: Map<Long, IntArray> = emptyMap(),
    /** 훑기가 읽어 둔 그림의 설명문([HwpxLayout.alts]). 그리기만 읽는다. */
    val alts: Map<Long, String> = emptyMap(),
    val partHref: (Int) -> String = { "" },
    val onTruncated: () -> Unit = {},
    val checkCancel: () -> Unit = {},
    val sectionInfo: SectionInfo = SectionInfo.DEFAULT,
    val maxChars: Int = HtmlWriter.DEFAULT_MAX_CHARS,
)

/** 걷기를 멈춘다 — 조각의 끝에 닿았거나 본문의 쓰기 상한에 닿았다. 제어 흐름이라 스택을 담지 않는다. */
internal object StopWalk : RuntimeException() {
    private fun readResolve(): Any = StopWalk
    override fun fillInStackTrace(): Throwable = this
}

/**
 * 각주·미주를 모아 두는 곳. HWPX 의 각주는 **참조 자리에 본문이 통째로 들어 있다**(`hp:ctrl > hp:footNote >
 * hp:subList`). 그 자리에서 본문을 따로 쓰고 본문 쪽에는 표지만 남긴다. 조각의 끝에 본문 뒤로 붙는다.
 *
 * **각주를 모두 쓴 뒤 미주를 쓴다**(각자 만난 차례로) — HWP 5.0 변환기의 `renderNotes` 와 같은 차례다. 처음에는 만난 차례
 * 그대로 섞어 적어, 같은 보도자료의 주석 차례가 포맷마다 달랐다(13단계 짝 대조). 그래서 주석 하나를 제 쓰개([remaining] 만큼)에
 * 써서 종류별 더미에 잇는다([commit]).
 *
 * 본문과 따로 쓰므로 본문의 상한과 따로 논다 — 본문이 상한에 닿아도 이미 쓴 각주는 온전하다. 두 더미를 합친 양이 [maxChars] 다.
 */
internal class NoteSink(private val maxChars: Int) {
    private val foot = HtmlWriter(Int.MAX_VALUE)
    private val end = HtmlWriter(Int.MAX_VALUE)
    private val used: Int get() = foot.length + end.length

    /** 더 쓸 몫이 없다. */
    val full: Boolean get() = used >= maxChars

    /** 주석 하나에 줄 몫 — 남은 양. */
    val remaining: Int get() = maxOf(0, maxChars - used)

    /** 다 쓴 주석 하나([w] 는 [remaining] 만큼의 쓰개)를 그 종류의 더미에 잇는다. */
    fun commit(endnote: Boolean, w: HtmlWriter) {
        w.closeAll()
        (if (endnote) end else foot).append(w)
    }

    /** 다 쓴 HTML — 각주 더미, 미주 더미의 차례. 주석이 없으면 빈 문자열. */
    fun finish(): String {
        if (used == 0) return ""
        // 두 더미는 이미 상한에 묶였다 — 합칠 때 자르지 않도록 넉넉히 잡는다.
        val w = HtmlWriter(used + FRAME_CHARS)
        w.start("section", "class" to "notes")
        w.void("hr")
        w.append(foot)
        w.append(end)
        return w.closeAll().toString()
    }

    private companion object {
        /** `<section class="notes"><hr>` 의 길이보다 넉넉히. */
        const val FRAME_CHARS = 64
    }
}

/**
 * 구역 XML(`Contents/sectionN.xml`)을 **흐름으로** 걸어가며 HTML 을 쓴다. DOM 을 만들지 않는다.
 *
 * ## 훑기와 그리기가 같은 걷기다
 *
 * docx 의 `DocxWalker` 와 같은 약속이다 — 긴 구역을 조각으로 나누려면 여는 동안 한 번 훑어야 하고, 그리려면
 * 조각마다 다시 걸어야 한다. 둘을 다른 코드로 두면 조각의 시작에서 번호가 어긋난다. 그래서 걷기는 하나이고
 * 훑기는 쓰는 곳이 없는 걷기다. **두 모드가 같은 요소를 같은 순서로 소비한다** — 보이지 않게 할 것은
 * 건너뛰지 말고 쓰기만 끈다([suppress]). 두 모드가 똑같이 건너뛰는 것(머리말·꼬리말, 깊이 상한을 넘은 표)만
 * 건너뛴다.
 *
 * ## HTML 의 모양
 *
 * 문단은 `p`(개요 문단은 `h1`..`h6`), 번호 문단은 **번호를 계산해 적은** `p.li` 다(docx 와 같은 판단).
 * 문단 요소는 **바탕 글자 모양**(첫 `hp:run` 의 것)의 크기·굵기·색을 들고, 글자 덩이는 그와 다른 것만 적는다 — HWP 5.0
 * 변환기와 같은 모양이다(13단계 짝 대조, [settle]).
 * 표·글상자·캡션은 문단 안(`hp:run` 안)에 들어 있지만 `p` 안에 `table`·`div` 를 둘 수 없으므로 **문단을 거기서
 * 끊고** 쓴 뒤 문단을 이어 연다. 아래·오른쪽 캡션은 개체 **뒤에** 붙인다([caption]). 표의 합치기는 칸의 내용 **뒤에**
 * 적혀 있어 훑기가 재 두고(`TableScan`) 그리기는 읽기만 한다.
 */
internal class HwpxWalker(
    private val env: HwpxEnv,
    private val main: HtmlWriter?,
    private val notes: NoteSink?,
    private val state: WalkState,
    private val cfg: WalkConfig,
) {
    private val limits = env.limits
    private val header = env.header

    /** 지금 쓰는 곳 — 본문([main]), 주석 하나의 쓰개, 또는 개체 뒤에 붙일 캡션의 쓰개. 훑기면 null. */
    private var out: HtmlWriter? = main

    /** [out] 의 상한. 개체 뒤에 붙일 캡션을 따로 쓸 때 남은 몫을 잰다([caption]). */
    private var outCap: Int = cfg.maxChars

    /** 0 보다 크면 걷되 쓰지 않는다(겹친 칸, 쓸 자리가 없는 각주). */
    private var suppress = 0
    private var para: ParaCtx? = null
    private var topNext = 0
    private var topCurrent = -1
    private var tableDepth = 0
    private var subListDepth = 0
    private var graphicDepth = 0
    private val pendingAnchors = ArrayList<String>()
    private var emptyRun = 0
    private var ticks = 0
    private var sectionInfo = cfg.sectionInfo
    private var markpen: String? = null

    /**
     * 지금 그리는 곳의 **알려진 바탕색**(칸의 면 색·글상자의 채우기). null 이면 모른다(화면의 흰 바탕으로 본다). 흰 글자를
     * 적을지 가를 때 쓴다([runCss]) — HWP 5.0 변환기(`Hwp5Walker.background`)와 같은 규칙이다.
     */
    private var background: String? = null

    private val lightBackground: Boolean get() = background.let { it == null || isLightColor(it) }

    /**
     * 흰 면 색을 적지 않아도 되는가 — **둘러싼 바탕이 흰 화면일 때만** 그렇다. 어두운 칸 안의 흰 칸에서 흰색을 빼면 안쪽
     * 칸이 투명해져 검은 글자가 남색 위에 놓였다(검토가 잡았다). HWP 5.0 변환기와 같은 규칙이다.
     */
    private fun redundantWhite(fill: String): Boolean = fill == WHITE && background.let { it == null || it == WHITE }
    private var currentNote: NoteCtx? = null
    private var noteSeq = 0
    private val fields = ArrayList<FieldFrame>()

    /**
     * 둘러싼 묶음 개체의 설명문 — 제 설명문이 없는 그림이 물려받는다. HWP 5.0 은 설명문이 개체 공통 속성에 있어 묶음 하나에
     * 하나뿐이고, 그것이 묶음 안의 모든 그림의 대체 글이 된다(`Hwp5Walker.gso`). 같은 문서가 포맷에 따라 달리 읽히지 않게 한다.
     */
    private var inheritedAlt: String? = null

    /** 이 걷기가 본 최상위 문단의 수. 훑기가 '구역 하나가 통째로 실패했다' 를 가를 때 쓴다. */
    val topBlocks: Int get() = topNext

    /** 그리기 — 조각 밖의 최상위 문단을 건너뛴다. */
    private val bodyRange: Boolean = cfg.scan == null

    private val writing: Boolean get() = out != null && suppress == 0

    // ---- 입구 -------------------------------------------------------------------

    /**
     * 구역 하나(`hs:sec`)를 걷는다. [p] 는 막 입력을 물린 파서다.
     *
     * @return 뿌리가 구역(`sec`)이었는가. JVM 시험의 kxml2 는 XML 이 아닌 입력을 예외 없이 `END_DOCUMENT` 로
     *   끝내기도 한다(CLAUDE.md 함정 표) — 예외만 보면 '깨진 구역' 을 '빈 구역' 으로 읽는다.
     */
    fun section(p: XmlPullParser): Boolean {
        try {
            if (!HwpxXml.toRoot(p, limits) || p.name != "sec") return false
            children(p) { name -> if (name == "p") paragraph(p, Where.BODY) else skip(p) }
            flushAnchors()
        } catch (_: StopWalk) {
            // 조각의 끝 — 정상이다.
        }
        return true
    }

    // ---- 쓰기 -------------------------------------------------------------------

    private fun start(tag: String, vararg attrs: Pair<String, String?>) {
        if (!writing) return
        val w = out!!
        // 본문이 상한에 닿은 뒤의 여는 태그는 `HtmlWriter` 가 쓰지 않고 열린 것으로만 세는데, 짝을 맞추는 닫는
        // 태그는 쓴다. 덩이가 수백만이면 닫는 태그만으로 상한을 몇 배로 넘는다(docx 검토가 잡았다). 여기서 멈춘다.
        // 각주 쪽과 따로 쓰는 캡션([caption])은 멈추지 않는다 — 본문은 계속 그려야 한다. 그쪽은 `HtmlWriter` 가 짝을 맞춰 준다.
        if (w.full && w === main) throw StopWalk
        w.start(tag, *attrs)
    }

    private fun end(tag: String) {
        if (writing) out!!.end(tag)
    }

    private fun void(tag: String, vararg attrs: Pair<String, String?>) {
        if (writing) out!!.void(tag, *attrs)
    }

    private fun text(s: CharSequence) {
        if (writing) out!!.text(s)
    }

    private fun record(kind: String) {
        if (cfg.recordFeatures) env.features.record(kind)
    }

    private fun headerFooter() {
        if (cfg.recordFeatures && !env.headerFooterRecorded) {
            env.headerFooterRecorded = true
            env.features.record(UnsupportedFeatures.HEADER_FOOTER)
        }
    }

    private fun skip(p: XmlPullParser) = HwpxXml.skip(p, limits)

    private inline fun children(p: XmlPullParser, onChild: (String) -> Unit) = HwpxXml.eachChild(p, limits, onChild)

    private fun tick() {
        if (++ticks and 63 == 0) cfg.checkCancel()
    }

    /**
     * `hp:switch` — 알아듣는 `hp:case` 가 있으면 그것, 없으면 `hp:default`(한컴 모델 `Compatibility.cpp`).
     * 가지 하나만 걷는다. 둘 다 없으면 모르는 요소로 센다.
     */
    private inline fun choose(p: XmlPullParser, handle: (String) -> Unit) {
        var taken = false
        children(p) { branch ->
            val take = !taken && when (branch) {
                "case" -> HwpxXml.attr(p, "required-namespace") in UNDERSTOOD_NAMESPACES
                "default" -> true
                else -> false
            }
            if (take) {
                taken = true
                children(p) { handle(it) }
            } else {
                skip(p)
            }
        }
        if (!taken) record(UnsupportedFeatures.UNKNOWN_ELEMENT)
    }

    // ---- 블록 -------------------------------------------------------------------

    /** `hp:subList` 의 문단들. */
    private fun blocks(p: XmlPullParser, where: Where) {
        children(p) { name ->
            when (name) {
                "p" -> paragraph(p, where)
                "switch" -> choose(p) { if (it == "p") paragraph(p, where) else skip(p) }
                else -> skip(p)
            }
        }
    }

    /**
     * 최상위 문단이면 번호를 매긴다. 그리는 중이면 조각 밖을 건너뛰고([SKIPPED]), 끝에 닿으면 멈춘다.
     * 최상위가 아니면 -1.
     */
    private fun enterTop(p: XmlPullParser, where: Where): Int {
        if (where != Where.BODY) return -1
        val index = topNext++
        if (bodyRange) {
            if (index >= cfg.end) throw StopWalk
            if (index < cfg.start) {
                skip(p)
                return SKIPPED
            }
        }
        tick()
        return index
    }

    private fun paragraph(p: XmlPullParser, where: Where) {
        val index = enterTop(p, where)
        if (index == SKIPPED) return
        if (out === main && main?.full == true) throw StopWalk
        val style = HwpxXml.int(p, "styleIDRef")?.let { header.styles[it] }
        val paraPrId = HwpxXml.int(p, "paraPrIDRef") ?: style?.paraPr
        val savedPara = para
        val savedTop = topCurrent
        if (index >= 0) {
            topCurrent = index
            // 형광펜은 문단을 넘지 않는 것으로 본다 — 조각이 문단 앞에서 시작하므로 상태에 넣지 않아도 된다.
            markpen = null
        }
        beginParagraph(paraPrId, index, style?.charPr)
        children(p) { name ->
            when (name) {
                "run" -> run(p, style?.charPr)
                "switch" -> choose(p) { if (it == "run") run(p, style?.charPr) else skip(p) }
                // `hp:linesegarray` 는 한글의 줄 배치 캐시다. 흐름 렌더는 쓰지 않는다.
                else -> skip(p)
            }
        }
        para?.let { finishParagraph(it) }
        para = savedPara
        topCurrent = savedTop
    }

    /**
     * 문단의 모양을 정하고 번호를 센다. **최상위 문단이면 번호를 세기 전에** 훑기에게 알린다 — 조각이 여기서
     * 끊기면 찍어 둔 상태에 이 문단의 번호가 들어가 있으면 안 된다.
     *
     * 문단 요소의 `style` 은 아직 정하지 않는다 — 문단의 **바탕 글자 모양**(첫 `hp:run` 의 것)을 알아야 한다([settle]).
     * 그래서 제목·번호 문단을 여는 것도 첫 덩이의 시작(또는 덩이 없는 문단의 끝)까지 미룬다([ParaCtx.eager]).
     *
     * @param styleChar 문단 스타일의 글자 모양 — `charPrIDRef` 가 없는 덩이와 덩이 없는 문단의 바탕.
     */
    private fun beginParagraph(paraPrId: Int?, index: Int, styleChar: Int?) {
        val pf = header.para(paraPrId)
        val outline = pf.headingType == "OUTLINE"
        val heading = outline && index >= 0
        if (index >= 0) cfg.scan?.onTopBlock(index, heading, state)

        var marker: String? = null
        when (pf.headingType) {
            "OUTLINE", "NUMBER" -> {
                val id = if (outline) sectionInfo.outlineNumbering else pf.headingIdRef
                marker = header.numberings[id]?.let { numbered(it, pf.headingLevel) }
            }
            "BULLET" -> marker = header.bullets[pf.headingIdRef]?.let { bullet(it) }
        }

        val level = pf.headingLevel
        val tag = when {
            !heading -> "p"
            level <= 5 -> "h${level + 1}"
            else -> "p"
        }
        val classes = ArrayList<String>(2)
        if (heading && level > 5) classes.add("h7")
        if (marker != null) classes.add("li")
        val pc = ParaCtx(
            tag = tag,
            id = if (heading) HwpxIds.heading(cfg.section, index) else null,
            cls = classes.joinToString(" ").ifEmpty { null },
            marker = marker,
            continuationCls = if (heading && level > 5) "h7" else null,
            paraPrId = paraPrId,
            heading = heading,
            styleChar = styleChar,
            // 제목(목차가 가리킨다)과 번호 문단(표지가 보여야 한다)은 비어 있어도 연다.
            eager = heading || marker != null,
        )
        if (heading && cfg.scan != null) {
            val t = StringBuilder()
            if (marker != null) t.append(marker).append(' ')
            pc.headingText = t
            pc.headingLevel = level
        }
        para = pc
    }

    /**
     * 번호 문단의 표지. 그 수준의 번호 형식이 없으면 세지 않고 표지도 없다 — HWP 5.0 의 `numbered` 와 같은 차례다(형식을 먼저
     * 보고 센다). 셈·모양·형식 채우기는 두 변환기가 `HancomNumbers` 하나를 쓴다(13단계 짝 대조 — 시작 번호 0 이 HWPX 에서만 1 이었다).
     */
    private fun numbered(def: NumberingDef, level: Int): String? {
        val lvl = level.coerceIn(0, HwpxLimits.NUMBER_LEVELS - 1)
        val template = def.levels[lvl]?.text ?: return null
        val values = state.numbers.next(def.id, lvl, def.starts)
        return HancomNumbers.fill(template, lvl, values, def.shapes).takeIf { it.isNotEmpty() }
    }

    /**
     * 문단의 **바탕 글자 모양**을 정하고 문단 요소의 `style` 을 만든다 — 문단 모양의 CSS 에 바탕 글자의 크기·굵기·기울임·색·
     * 명조를 더한다(문서 기본과 다른 것만, [paraCss]). HWP 5.0 의 `beginParagraph` → `RunStyle.paragraphCss` 와 같다.
     *
     * 처음에는 HWPX 만 바탕 글자를 문단에 적지 않아 빈 문단의 `br`·줄 높이·번호 표지·각주 표지가 모두 **문서 기본 크기**로
     * 그려졌다 — 같은 보도자료의 빈 문단 874개가 HWP(K01)는 30–73%, HWPX(K25)는 전부 100% 였고, 글 문단의 99%가 글보다 37%쯤
     * 높은 줄 상자를 가졌다(13단계 짝 대조).
     */
    private fun settle(pc: ParaCtx, charId: Int?) {
        pc.baseSet = true
        pc.baseChar = charId
        val light = lightBackground
        pc.style = env.paraCss(pc.paraPrId, charId, light, pc.heading) { pf, base -> paraCss(pf, base, pc.heading, light) }
    }

    /** 문단을 연다(처음 쓸 것이 생길 때). 이어 여는 것(표·글상자 뒤)은 번호·`id` 없이 연다. */
    private fun ensureOpen(pc: ParaCtx) {
        if (pc.opened) return
        if (!pc.baseSet) settle(pc, pc.styleChar)
        pc.opened = true
        pc.gen++
        if (pc.continuation) {
            start(pc.tag, "class" to pc.continuationCls, "style" to pc.style)
            flushAnchors()
            return
        }
        start(pc.tag, "id" to pc.id, "class" to pc.cls, "style" to pc.style)
        flushAnchors()
        if (pc.marker != null) {
            start("span", "class" to "mk")
            text(pc.marker)
            end("span")
            pc.visible = true
        }
    }

    /**
     * 문단을 닫는다. 빈 문단은 줄 하나(`br`)로 남긴다 — 한글 문서는 빈 문단으로 간격을 띄운다. 다만 잇달아
     * [HwpxLimits.MAX_EMPTY_RUN] 개를 넘는 빈 문단은 버린다.
     */
    private fun finishParagraph(pc: ParaCtx) {
        // 덩이가 하나도 없던 문단 — 바탕은 스타일의 글자 모양(없으면 문서 기본). 제목·번호 문단은 비어 있어도 연다.
        if (!pc.baseSet) {
            settle(pc, pc.styleChar)
            if (pc.eager) ensureOpen(pc)
        }
        pc.headingText?.let { t -> if (topCurrent >= 0) cfg.scan?.heading(topCurrent, pc.headingLevel, t.toString()) }
        if (!pc.opened) {
            if (pendingAnchors.isEmpty()) {
                if (pc.continuation) return
                if (emptyRun >= HwpxLimits.MAX_EMPTY_RUN) {
                    emptyRun++
                    return
                }
            }
            ensureOpen(pc)
        }
        if (!pc.visible) {
            void("br")
            emptyRun++
        } else {
            emptyRun = 0
        }
        end(pc.tag)
        pc.opened = false
        resetFieldLinks()
    }

    /** 표·글상자를 쓰려고 문단을 끊는다. 뒤의 글은 [ensureOpen] 이 이어 연다. */
    private fun breakParagraph(pc: ParaCtx) {
        if (pc.opened) {
            end(pc.tag)
            pc.opened = false
        }
        pc.continuation = true
        pc.linkDepth = 0
        resetFieldLinks()
    }

    private fun flushAnchors() {
        if (pendingAnchors.isEmpty()) return
        for (id in pendingAnchors) {
            start("span", "id" to id)
            end("span")
        }
        pendingAnchors.clear()
    }

    private fun resetFieldLinks() {
        for (f in fields) f.tag = null
    }

    // ---- 글자 -------------------------------------------------------------------

    /**
     * 글자 덩이(`hp:run`). 문단의 **첫 덩이**가 문단의 바탕 글자 모양을 정한다([settle]) — 미뤄 둔 제목·번호 문단은 그때,
     * 덩이의 자식보다 먼저 연다(첫 덩이 안의 표보다 제목의 `id` 와 번호 표지가 앞에 온다 — 예전의 차례 그대로).
     */
    private fun run(p: XmlPullParser, styleChar: Int?) {
        val pc = para
        if (pc == null) {
            skip(p)
            return
        }
        val charId = HwpxXml.int(p, "charPrIDRef") ?: styleChar
        if (!pc.baseSet) {
            settle(pc, charId)
            if (pc.eager) ensureOpen(pc)
        }
        val rc = RunCtx(charId, pc.baseChar)
        children(p) { name -> runChild(p, name, rc) }
    }

    private fun runChild(p: XmlPullParser, name: String, rc: RunCtx) {
        when (name) {
            "t" -> textElement(p, rc)
            "secPr" -> secPr(p)
            "ctrl" -> ctrl(p, rc)
            "tbl" -> table(p)
            "pic" -> picture(p)
            "container" -> container(p, rc)
            in SHAPES -> shape(p)
            "equation" -> equation(p)
            "ole", "video" -> {
                record(UnsupportedFeatures.EMBEDDED_OBJECT)
                embedded(p)
            }
            "chart" -> {
                record(UnsupportedFeatures.CHART)
                embedded(p)
            }
            "textart" -> textArt(p, rc)
            "compose" -> compose(p, rc)
            "dutmal" -> dutmal(p, rc)
            in FORM_CONTROLS -> {
                record(UnsupportedFeatures.FORM)
                skip(p)
            }
            "switch" -> choose(p) { runChild(p, it, rc) }
            "unknownObj" -> {
                record(UnsupportedFeatures.UNKNOWN_ELEMENT)
                skip(p)
            }
            // `hp:tab`·`hp:lineBreak` 가 `hp:run` 바로 아래에 오는 파일이 있다(python-hwpx 가 그렇게 읽는다).
            else -> inlineMark(p, name, rc)
        }
    }

    /**
     * `hp:t` — 글자와 안쪽 요소(탭·줄바꿈·묶음 빈칸·형광펜·변경 추적…)가 섞인 내용. 차례대로 쓴다.
     * 끝나면 `hp:t` 의 끝 태그에 서 있다.
     */
    private fun textElement(p: XmlPullParser, rc: RunCtx) {
        val depth = p.depth
        var got = 0
        while (true) {
            when (p.nextGuarded(limits)) {
                XmlPullParser.TEXT -> {
                    // 한컴의 사설 영역 글자(절 번호의 네모 숫자 등)는 기기에서 두부다 — HWP 5.0 과 같은 표로 옮긴다.
                    val t = HancomChars.mapAll(p.text ?: "")
                    if (got < HwpxLimits.MAX_TEXT) {
                        val take = minOf(t.length, HwpxLimits.MAX_TEXT - got)
                        emit(if (take == t.length) t else t.substring(0, take), rc)
                        got += take
                        // 글자 요소 하나가 상한을 넘으면 잘렸다 — 조용히 자르지 않고 알린다.
                        if (take < t.length) cfg.onTruncated()
                    }
                }
                XmlPullParser.START_TAG -> inlineMark(p, p.name, rc)
                XmlPullParser.END_TAG -> if (p.depth == depth) return
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    /** 글자 안의 표시 요소 하나. 끝나면 그 요소의 끝 태그에 서 있다. */
    private fun inlineMark(p: XmlPullParser, name: String, rc: RunCtx) {
        when (name) {
            "tab" -> emit("\t", rc)
            "lineBreak" -> lineBreak()
            // 묶음 빈칸·고정폭 빈칸. 줄이 거기서 나뉘지 않아야 한다. (바로보기의 변환기도 `&#160;` 으로 쓴다.)
            "nbSpace", "fwSpace" -> emit(" ", rc)
            // 한컴 모델은 `hypen`(철자가 틀렸다), 스키마는 `hyphen` 이라 적는다. 둘 다 받는다.
            "hyphen", "hypen" -> emit("-", rc)
            "markpenBegin" -> markpen = HwpxXml.color(HwpxXml.attr(p, "color")) ?: DEFAULT_MARKPEN
            "markpenEnd" -> markpen = null
            // 변경 추적: 지운 글은 보이지 않고, 넣은 글은 보통 글로 보인다(docx 와 같다).
            "deleteBegin" -> state.deleting = minOf(state.deleting + 1, MAX_DELETE_DEPTH)
            "deleteEnd" -> if (state.deleting > 0) state.deleting--
            // `insertBegin`·`insertEnd`·`titleMark`(차례 표시)·`unknownch` — 그릴 것이 없다.
        }
        skip(p)
    }

    /** 글자 한 덩이. 문단 바탕 글자 모양과 다른 서식만 `span` 에 적는다([runCss]). */
    private fun emit(s: String, rc: RunCtx) {
        if (s.isEmpty() || state.deleting > 0) return
        val pc = para ?: return
        cfg.scan?.addChars(s.length)
        pc.headingText?.let { if (it.length < HwpxLimits.MAX_HEADING_CHARS) it.append(s, 0, minOf(s.length, HwpxLimits.MAX_HEADING_CHARS - it.length)) }
        if (rc.format.effect && !pc.effectRecorded) {
            pc.effectRecorded = true
            record(UnsupportedFeatures.TEXT_EFFECT)
        }
        ensureOpen(pc)
        pc.visible = true
        if (!writing) return
        val base = rc.css
        val pen = markpen
        // 어두운 형광펜 위의 흰 글자는 흰색으로 적는다 — 글자 CSS 는 형광펜을 모른 채(모양마다 한 번) 만들어져 밝은 바탕이면
        // 흰색을 뺐다(검토가 잡았다).
        val penText = if (pen != null && !isLightColor(pen)) rc.format.color?.takeIf(::isLightColor) else null
        val style = if (pen == null) base else listOfNotNull(base, StyleBuilder().add("background-color", pen).add("color", penText).build()).joinToString(";")
        val wrap = rc.wrap
        if (style != null) start("span", "style" to style)
        if (wrap != null) start(wrap)
        text(s)
        if (wrap != null) end(wrap)
        if (style != null) end("span")
    }

    private fun lineBreak() {
        if (state.deleting > 0) return
        val pc = para ?: return
        ensureOpen(pc)
        pc.visible = true
        // 제목 안의 줄바꿈은 목차·부분 이름에서 공백이다 — `A<br>B` 의 제목은 `A B`(HWP 5.0 의 `lineBreak` 와 같다). 처음에는
        // 적지 않아 `AB` 가 되었다(13단계 짝 대조).
        pc.headingText?.let { if (it.length < HwpxLimits.MAX_HEADING_CHARS) it.append(' ') }
        void("br")
    }

    /** 한글의 글머리표는 기호 글꼴의 사설 영역 글자일 때가 있다 — HWP 5.0 과 같은 표로 옮긴다. */
    private fun bullet(raw: String): String = if (raw.isEmpty()) "•" else BulletGlyphs.mapAll(raw)

    // ---- 조판 부호(`hp:ctrl`) -------------------------------------------------------------------

    private fun ctrl(p: XmlPullParser, rc: RunCtx) {
        children(p) { name ->
            when (name) {
                "fieldBegin" -> fieldBegin(p)
                "fieldEnd" -> fieldEnd(p)
                "bookmark" -> bookmark(p)
                // 머리말·꼬리말은 쪽에 붙는 것이라 흐름 렌더에 자리가 없다. 한 번 센다(12단계와 같다).
                "header", "footer" -> {
                    headerFooter()
                    skip(p)
                }
                "footNote" -> note(p, endnote = false)
                "endNote" -> note(p, endnote = true)
                "autoNum" -> autoNum(p, rc)
                "newNum" -> newNumber(p)
                // 숨은 설명 — 본문과 따로 보일 자리가 없다(12단계의 메모와 같은 판단).
                "hiddenComment" -> {
                    record(UnsupportedFeatures.COMMENT)
                    skip(p)
                }
                // 단 정의·쪽 번호 위치·감추기·찾아보기 표시 — 흐름 렌더에 뜻이 없다.
                else -> skip(p)
            }
        }
    }

    /**
     * 구역 정의. 개요 번호 정의와 각주·미주 모양(표지와 번호 매기기)을 읽는다. 구역마다 새로 세는 주석은 여기서 셈을 0 으로
     * 돌린다 — HWP 5.0 의 `sectionDef` 와 같다. 훑기와 그리기가 같은 자리에서 돌리므로 조각의 시작 상태와 어긋나지 않는다.
     */
    private fun secPr(p: XmlPullParser) {
        val outlineRef = HwpxXml.int(p, "outlineShapeIDRef")
        var foot = sectionInfo.footnote
        var endn = sectionInfo.endnote
        children(p) { name ->
            when (name) {
                "footNotePr" -> foot = readNotePr(p, foot)
                "endNotePr" -> endn = readNotePr(p, endn)
                else -> skip(p)
            }
        }
        sectionInfo = SectionInfo(outlineRef ?: sectionInfo.outlineNumbering, foot, endn)
        if (foot.numbering == NoteSettings.ON_SECTION) state.footnoteNo = 0
        if (endn.numbering == NoteSettings.ON_SECTION) state.endnoteNo = 0
        cfg.scan?.sectionInfo(sectionInfo)
    }

    /** `hp:footNotePr`·`hp:endNotePr` — 적히지 않은 것은 [prev] 의 값. */
    private fun readNotePr(p: XmlPullParser, prev: NoteSettings): NoteSettings {
        var fmt = prev.format
        var numbering = prev.numbering
        var newNum = prev.newNum
        children(p) { name ->
            when (name) {
                "autoNumFormat" -> fmt = readAutoNumFormat(p)
                "numbering" -> {
                    numbering = NoteSettings.numberingOf(HwpxXml.attr(p, "type"))
                    // HWP 5.0 의 `NoteShape.start` 와 같은 범위.
                    newNum = (HwpxXml.int(p, "newNum") ?: 1).coerceIn(0, MAX_START)
                    skip(p)
                }
                else -> skip(p)
            }
        }
        return NoteSettings(fmt, numbering, newNum)
    }

    private fun readAutoNumFormat(p: XmlPullParser): AutoNumFormat {
        val f = AutoNumFormat(
            shape = HancomNumbers.shapeOf(HwpxXml.attr(p, "type")),
            userChar = HwpxXml.attr(p, "userChar").orEmpty().take(8),
            prefix = HwpxXml.attr(p, "prefixChar").orEmpty().take(8),
            suffix = HwpxXml.attr(p, "suffixChar").orEmpty().take(8),
        )
        skip(p)
        return f
    }

    /**
     * 자동 번호. 쪽 번호는 흐름 렌더에 뜻이 없어 쓰지 않는다. 각주 본문 안의 각주 번호는 **돌아가는 링크**로 — 번호는 저장된
     * `num` 이 아니라 **그 주석의 번호**([note] 가 정한 것)다. 쪽마다 새로 세는 문서는 `num` 이 쪽마다 1 로 돌아가므로, 셈이
     * 한곳이어야 본문의 표지와 같다(HWP 5.0 의 `autoNumber` 도 주석 번호를 제 셈으로 바꾼다). 그림·표·수식 번호는 한글이 저장할
     * 때 셈한 값(`num`)을 그대로 쓴다.
     */
    private fun autoNum(p: XmlPullParser, rc: RunCtx) {
        val num = HwpxXml.int(p, "num")?.takeIf { it in 0..MAX_NUMBER }
        val type = HwpxXml.attr(p, "numType")?.uppercase()
        var fmt: AutoNumFormat? = null
        children(p) { name -> if (name == "autoNumFormat") fmt = readAutoNumFormat(p) else skip(p) }
        when (type) {
            "PAGE", "TOTAL_PAGE" -> Unit
            "FOOTNOTE", "ENDNOTE" -> {
                val note = currentNote ?: return
                val label = (fmt ?: note.format).label(note.number)
                note.label = label
                if (state.deleting > 0) return
                val pc = para ?: return
                ensureOpen(pc)
                pc.visible = true
                start("sup", "class" to "fnnum")
                start("a", "href" to "#${note.backId}")
                text(label)
                end("sup")
            }
            else -> emit((fmt ?: AutoNumFormat.DEFAULT).label(num ?: 1), rc)
        }
    }

    /**
     * 새 번호 지정(`hp:newNum` — `@num`, `@numType`). 각주·미주면 **다음** 주석이 그 번호를 보이도록 우리 셈을 옮긴다 — HWP 5.0 의
     * `newNumber`(`nwno`)와 같은 셈이다(`번호 - 시작 번호`, 보이는 번호는 시작 번호에서 센다 — [note]).
     *
     * 이어 세는 문서의 주석은 한글이 저장한 번호(`@number`)를 쓰고 그 번호가 이것을 이미 담고 있어 달라지지 않는다. 갈리는 것은
     * **우리 셈을 쓰는 주석** — 쪽마다 새로 세는 문서(`ON_PAGE`)와 저장한 번호가 없는 주석이다. 처음에는 이 요소를 버려 그런
     * 주석이 같은 문서의 HWP 판과 다른 번호를 보였다(13단계에서 미룬 것). 쪽·그림·표·수식 번호는 옮길 셈이 없다 — 자동 번호가
     * 저장된 `num` 을 쓴다([autoNum]). 표본의 `hp:newNum` 은 전부 쪽 번호였다(K25·K26·K27·K33).
     */
    private fun newNumber(p: XmlPullParser) {
        val num = HwpxXml.int(p, "num")?.takeIf { it in 0..MAX_NUMBER }
        val type = HwpxXml.attr(p, "numType")?.uppercase()
        skip(p)
        if (num == null) return
        when (type) {
            "FOOTNOTE" -> state.footnoteNo = num - sectionInfo.footnote.newNum
            "ENDNOTE" -> state.endnoteNo = num - sectionInfo.endnote.newNum
        }
    }

    // ---- 필드·책갈피 -------------------------------------------------------------------

    /**
     * 필드의 시작. 보이는 글은 `fieldBegin` 과 `fieldEnd` 사이의 글이다(누름틀의 안내문·메모의 글은
     * `fieldBegin` 안의 `hp:subList`·매개변수라 그리지 않는다).
     *
     * 하이퍼링크의 주소는 `@name` 이 아니라 매개변수 `Command` 에 있다(python-hwpx 의 관찰, 조사 노트). **바깥
     * 주소는 링크로 만들지 않는다** — 위생기가 스킴이 붙은 주소를 지우고 이 앱은 네트워크를 쓰지 않는다. 글자만
     * `span.ext` 로 표시한다(docx 와 같다). 문서 안 주소(책갈피)는 책갈피가 있는 조각을 찾아 링크로 만든다.
     */
    private fun fieldBegin(p: XmlPullParser) {
        val id = HwpxXml.attr(p, "id")
        val type = HwpxXml.attr(p, "type")?.uppercase()
        var command: String? = null
        children(p) { name ->
            if (name == "parameters") {
                children(p) { param ->
                    if (param == "stringParam" && command == null && HwpxXml.attr(p, "name") == "Command") {
                        command = HwpxXml.collectText(p, limits, HwpxLimits.MAX_PARAM_CHARS)
                    } else {
                        skip(p)
                    }
                }
            } else {
                skip(p)
            }
        }
        if (type == "MEMO") record(UnsupportedFeatures.COMMENT)
        if (fields.size >= HwpxLimits.MAX_FIELD_DEPTH) return
        val frame = FieldFrame(id)
        fields.add(frame)
        if (type == "HYPERLINK" && state.deleting == 0) openLink(frame, HyperlinkCommand.parse(command))
    }

    private fun openLink(frame: FieldFrame, target: HyperlinkTarget) {
        val pc = para ?: return
        if (target.bookmark != null) {
            if (pc.linkDepth > 0) return
            val href = anchorHref(target.bookmark) ?: return
            ensureOpen(pc)
            start("a", "href" to href)
            pc.linkDepth++
            frame.tag = "a"
        } else {
            ensureOpen(pc)
            start("span", "class" to "ext")
            frame.tag = "span"
        }
        frame.gen = pc.gen
    }

    private fun fieldEnd(p: XmlPullParser) {
        val begin = HwpxXml.attr(p, "beginIDRef")
        skip(p)
        if (fields.isEmpty()) return
        var at = fields.size - 1
        if (begin != null) {
            val match = fields.indexOfLast { it.id == begin }
            if (match >= 0) at = match
        }
        while (fields.size > at) closeField(fields.removeAt(fields.size - 1))
    }

    private fun closeField(f: FieldFrame) {
        val tag = f.tag ?: return
        f.tag = null
        val pc = para ?: return
        if (pc.opened && pc.gen == f.gen) {
            end(tag)
            if (tag == "a") pc.linkDepth--
        }
    }

    /** 책갈피가 있는 조각을 가리키는 주소. 책갈피가 없으면 null(갈 곳 없는 링크는 만들지 않는다). */
    private fun anchorHref(name: String): String? {
        val part = cfg.bookmarkParts[name] ?: return null
        val id = HwpxIds.bookmark(name)
        return if (part == cfg.partIndex) "#$id" else cfg.partHref(part) + "#" + id
    }

    private fun bookmark(p: XmlPullParser) {
        val name = HwpxXml.attr(p, "name")
        skip(p)
        if (name.isNullOrEmpty() || currentNote != null) return
        val scan = cfg.scan
        if (scan != null) {
            scan.bookmark(name, if (topCurrent >= 0) topCurrent else topNext)
            return
        }
        if (!writing) return
        val id = HwpxIds.bookmark(name)
        val pc = para
        if (pc != null) {
            ensureOpen(pc)
            start("span", "id" to id)
            end("span")
        } else if (pendingAnchors.size < HwpxLimits.MAX_PENDING_ANCHORS) {
            pendingAnchors.add(id)
        }
    }

    // ---- 각주·미주 -------------------------------------------------------------------

    /**
     * 각주·미주 하나. 본문은 그 자리에 들어 있다 — 주석 하나의 쓰개에 써서 주석 쪽([NoteSink])의 그 종류 더미에 잇고, 본문
     * 쪽에는 번호 표지를 남긴다. 링크의 `id` 는 우리가 부분마다 따로 센다(번호는 되풀이될 수 있다).
     *
     * **번호** — 쪽마다 새로 세는 문서(`ON_PAGE`)는 **우리 셈**(+ 시작 번호 − 1)이다: 흐름에는 쪽이 없고, 한글이 저장한 번호
     * (`@number`)는 쪽마다 1 로 돌아가 같은 번호가 되풀이된다(HWP 5.0 도 '쪽마다' 를 '이어서' 로 센다). 그 밖에는 한글이 저장한
     * 번호를, 없으면 우리 셈을 쓴다. 구역마다 새로 세는 문서는 [secPr] 가 셈을 돌린다.
     *
     * 아래쪽 캡션을 따로 쓰는 동안([caption])에도 주석을 적는다 — [out] 이 본문이 아니라 캡션의 쓰개일 뿐 결국 본문에 붙는다.
     * 그 주석의 본문은 **만난 차례**(개체의 칸보다 먼저)로 주석 쪽에 들어간다. 캡션 안의 주석은 드물고(표본에 없다), 차례가
     * 조금 어긋나는 편이 본문을 잃는 것보다 낫다.
     */
    private fun note(p: XmlPullParser, endnote: Boolean) {
        // 각주 안의 각주는 한글이 만들지 않는다. 깊이를 막는다.
        if (currentNote != null || subListDepth >= HwpxLimits.MAX_SUBLIST_DEPTH) {
            skip(p)
            return
        }
        val counter = if (endnote) ++state.endnoteNo else ++state.footnoteNo
        val settings = if (endnote) sectionInfo.endnote else sectionInfo.footnote
        val ours = (counter.toLong() + settings.newNum - 1).coerceIn(0L, MAX_NUMBER.toLong()).toInt()
        val saved = HwpxXml.int(p, "number")?.takeIf { it in 1..MAX_NUMBER }
        val number = if (settings.numbering == NoteSettings.ON_PAGE) ours else saved ?: ours
        val seq = ++noteSeq
        val prefix = if (endnote) "en" else "fn"
        val noteId = "$prefix-$seq"
        val backId = "${prefix}ref-$seq"
        val ctx = NoteCtx(backId, number, settings.format)
        ctx.label = settings.format.label(number)

        val sink = notes
        // 주석 안이 아니면 [out] 은 본문이거나 본문에 붙을 캡션의 쓰개다(위 KDoc).
        val writeNote = main != null && sink != null && suppress == 0 && out != null &&
            !sink.full && seq <= HwpxLimits.MAX_NOTES
        val noteCap = if (writeNote) sink.remaining else 0
        val noteOut = if (writeNote) HtmlWriter(noteCap) else null
        val savedOut = out
        val savedCap = outCap
        val savedPara = para
        val savedPen = markpen
        val savedEmpty = emptyRun
        // 각주 본문은 **부분 끝의 흰 화면**에 그려진다 — 부른 칸의 바탕을 물려받으면 흰 글자를 흰 화면에 적는다(검토가
        // 잡았다. HWP 5.0 은 각주를 새 걷개로 그려 처음부터 그렇다).
        val savedBackground = background
        background = null
        out = noteOut
        outCap = noteCap
        if (noteOut != null) start("div", "id" to noteId, "class" to "note")
        currentNote = ctx
        para = null
        markpen = null
        subListDepth++
        children(p) { name -> if (name == "subList") blocks(p, Where.NOTE) else skip(p) }
        subListDepth--
        currentNote = null
        if (noteOut != null) {
            end("div")
            sink!!.commit(endnote, noteOut)
        }
        out = savedOut
        outCap = savedCap
        para = savedPara
        markpen = savedPen
        emptyRun = savedEmpty
        background = savedBackground
        if (main != null && sink != null && (sink.full || noteOut?.full == true)) cfg.onTruncated()

        val pc = para ?: return
        if (state.deleting > 0) return
        val label = ctx.label
        cfg.scan?.addChars(label.length)
        ensureOpen(pc)
        pc.visible = true
        start("sup", "class" to "fnref", "id" to if (writeNote) backId else null)
        if (writeNote && pc.linkDepth == 0) start("a", "href" to "#$noteId")
        text(label)
        end("sup")
    }

    // ---- 표 -------------------------------------------------------------------

    private class TableCtx(val cells: IntArray?, val scan: TableScan?) {
        var rows = 0
        var cellCount = 0
        var truncated = false
        var started = false
    }

    private fun table(p: XmlPullParser) {
        if (tableDepth >= HwpxLimits.MAX_TABLE_DEPTH) {
            cfg.onTruncated()
            skip(p)
            return
        }
        if (out === main && main?.full == true) throw StopWalk
        para?.let { breakParagraph(it) }
        val ordinal = state.tableOrdinal++
        val key = HwpxLayout.tableKey(cfg.section, ordinal)
        val t = TableCtx(
            cells = if (cfg.scan == null) cfg.tables[key] else null,
            scan = if (cfg.scan != null) TableScan(HwpxXml.int(p, "colCnt") ?: 1) else null,
        )
        val savedPara = para
        para = null
        flushAnchors()
        emptyRun = 0
        tableDepth++
        var after: HtmlWriter? = null
        children(p) { name ->
            when (name) {
                // 캡션은 스키마의 차례상 행보다 앞에 온다 — 아래쪽 캡션은 따로 써 두었다가 표 뒤에 붙인다([caption]).
                "caption" -> after = joinCaptions(after, caption(p))
                "tr" -> {
                    if (!t.started) {
                        start("table")
                        t.started = true
                    }
                    row(p, t)
                }
                else -> skip(p)
            }
        }
        tableDepth--
        if (t.started) end("table")
        // 표는 보이는 것이라 빈 문단의 이어짐을 끊는다. 마지막 칸의 빈 줄 수가 남으면 표 뒤의 빈 문단(간격)이 사라졌다.
        emptyRun = 0
        placeCaption(after)
        t.scan?.result()?.let { cfg.scan?.table(key, it) }
        para = savedPara
    }

    private fun truncateTable(t: TableCtx) {
        if (!t.truncated) {
            t.truncated = true
            cfg.onTruncated()
        }
    }

    private fun row(p: XmlPullParser, t: TableCtx) {
        if (t.truncated || t.rows >= HwpxLimits.MAX_ROWS) {
            truncateTable(t)
            skip(p)
            return
        }
        if (out === main && main?.full == true) throw StopWalk
        t.rows++
        tick()
        t.scan?.newRow()
        start("tr")
        children(p) { name -> if (name == "tc") cell(p, t) else skip(p) }
        end("tr")
    }

    private fun cell(p: XmlPullParser, t: TableCtx) {
        if (t.truncated || t.cellCount >= HwpxLimits.MAX_CELLS) {
            truncateTable(t)
            skip(p)
            return
        }
        val i = t.cellCount++
        cfg.scan?.addCell()
        val cells = t.cells
        val fill = HwpxXml.int(p, "borderFillIDRef")?.let { header.borderFills[it] }
        val gap = cells?.let { TableScan.gap(it, i) } ?: 0
        val hidden = cells?.let { TableScan.skip(it, i) } ?: false
        repeat(minOf(gap, HwpxLimits.MAX_SPAN)) {
            start("td", "class" to "gap")
            end("td")
        }
        if (hidden) {
            // 겹친 '비활성 칸'. 걷기는 한다([HwpxWalker] 의 주석).
            suppress++
        } else {
            val colspan = cells?.let { TableScan.colSpan(it, i) } ?: 1
            val rowspan = cells?.let { TableScan.rowSpan(it, i) } ?: 1
            start(
                "td",
                "colspan" to colspan.takeIf { it > 1 }?.toString(),
                "rowspan" to rowspan.takeIf { it > 1 }?.toString(),
                "class" to if (fill?.noBorder == true) "nb" else null,
                // 흰 면 색은 적지 않는다(화면이 이미 희다) — HWP 5.0 과 같다. 바탕으로는 든다(흰 글자를 가를 때).
                // 세로 정렬은 훑기가 적어 둔 값이다(`TableScan` — `hp:subList` 가 이 태그보다 뒤에 온다).
                "style" to StyleBuilder()
                    .add("background-color", fill?.background?.takeUnless(::redundantWhite))
                    .add(
                        "vertical-align",
                        when (cells?.let { TableScan.vAlign(it, i) }) {
                            TableScan.V_TOP -> "top"
                            TableScan.V_BOTTOM -> "bottom"
                            else -> null
                        },
                    )
                    .build(),
            )
        }
        val savedPara = para
        val savedBackground = background
        fill?.background?.let { background = it }
        para = null
        emptyRun = 0
        var colAddr: Int? = null
        var colSpan = 1
        var rowSpan = 1
        var zero = false
        var vAlign = TableScan.V_CENTER
        children(p) { name ->
            when (name) {
                "subList" -> {
                    vAlign = TableScan.vAlignOf(HwpxXml.attr(p, "vertAlign"))
                    blocks(p, Where.CELL)
                }
                "cellAddr" -> {
                    colAddr = HwpxXml.int(p, "colAddr")
                    skip(p)
                }
                "cellSpan" -> {
                    colSpan = HwpxXml.int(p, "colSpan") ?: 1
                    rowSpan = HwpxXml.int(p, "rowSpan") ?: 1
                    skip(p)
                }
                "cellSz" -> {
                    zero = HwpxXml.int(p, "width") == 0 || HwpxXml.int(p, "height") == 0
                    skip(p)
                }
                else -> skip(p)
            }
        }
        para = savedPara
        background = savedBackground
        if (hidden) suppress-- else end("td")
        t.scan?.cell(colAddr, colSpan, rowSpan, zero, vAlign)
    }

    /**
     * 캡션(`hp:caption`) 하나. 스키마의 차례상 캡션은 **개체보다 앞에**(표의 행·그림·수식 스크립트보다 먼저) 적혀 있다.
     * 위·왼쪽 캡션은 그 자리에 쓰고, **아래·오른쪽 캡션은 따로 쓴 쓰개를 돌려준다** — 부르는 쪽이 개체를 다 쓴 뒤
     * [placeCaption] 으로 붙인다. HWP 5.0 변환기(`captionOnTop`)와 같은 자리다. 처음에는 자리를 보지 않고 전부 개체 앞에 써서
     * K27 의 아래쪽 캡션 `* 공공비영리단체 포함` 이 표 위에 보였다(두 오라클 모두 표 뒤, 13단계 짝 대조).
     *
     * 따로 쓰는 쓰개의 상한은 지금 쓰개([out])의 **남은 몫**이다 — 붙인 뒤 본문이 상한을 크게 넘지 않는다. 쓰지 않는 걷기(훑기·
     * 가려진 칸)는 그 자리에서 걷는다. 두 모드가 같은 요소를 같은 차례로 소비한다([HwpxWalker] 의 약속).
     *
     * `side` 가 없으면 아래로 본다 — HWP 5.0 이 캡션의 속성을 읽지 못했을 때와 같고, 한글이 캡션을 넣는 기본 자리다.
     */
    private fun caption(p: XmlPullParser): HtmlWriter? {
        // 뒤에 붙일 캡션이면 지금 쓰개를 [host] 로 두고 따로 쓴다. 아니면(위·왼쪽, 쓰지 않는 걷기) 그 자리에서.
        val host = out?.takeIf { writing && captionAfter(HwpxXml.attr(p, "side")) }
        val savedCap = outCap
        val savedPara = para
        var buf: HtmlWriter? = null
        if (host != null) {
            outCap = maxOf(0, savedCap - host.length)
            buf = HtmlWriter(outCap)
            out = buf
            // 문단은 붙일 때 끊는다([placeCaption]) — 지금 끊으면 그 끝 태그가 따로 쓰는 쪽으로 간다.
            para = null
        }
        box(p, CAPTION_CLASS, Where.CAPTION)
        if (host == null) return null
        para = savedPara
        out = host
        outCap = savedCap
        return buf?.closeAll()
    }

    /**
     * 개체 뒤에 붙일 캡션(따로 쓴 것). 문단 안의 개체(그림·수식)면 문단을 끊고 붙인다 — 뒤의 글은 [ensureOpen] 이 이어 연다.
     * 쓴 것이 없으면(깊이 상한으로 건너뛴 캡션) 문단을 끊지 않는다.
     */
    private fun placeCaption(buf: HtmlWriter?) {
        if (buf == null || buf.length == 0) return
        para?.let { breakParagraph(it) }
        if (writing) out!!.append(buf)
    }

    /** 캡션이 둘 적힌 개체(깨진 파일) — 둘 다 붙인다. */
    private fun joinCaptions(a: HtmlWriter?, b: HtmlWriter?): HtmlWriter? {
        if (a == null) return b
        if (b != null) a.append(b)
        return a
    }

    /** 캡션을 개체 뒤에 둘 것인가 — 아래·오른쪽(없거나 모르는 값도). */
    private fun captionAfter(side: String?): Boolean = when (side?.uppercase()) {
        "TOP", "LEFT" -> false
        else -> true
    }

    /**
     * 글상자·캡션 — 문단을 끊고 그 자리에 `div` 로 쓴다. [p] 는 `hp:subList` 를 가진 요소(`hp:drawText`·`hp:caption`).
     * [fill] 은 글상자를 품은 도형의 채우기 색 — 칠하고(흰색은 빼고) 바탕으로 든다. 한글 문서는 표지·차례 상자에 파란 도형
     * 위 흰 글자를 흔히 쓰는데, 채우기를 칠하지 않으면 그 글이 흰 화면에서 사라진다(HWP 5.0 의 `textBox` 와 같다).
     */
    private fun box(p: XmlPullParser, cls: String, where: Where, fill: String? = null) {
        if (subListDepth >= HwpxLimits.MAX_SUBLIST_DEPTH || tableDepth >= HwpxLimits.MAX_TABLE_DEPTH) {
            cfg.onTruncated()
            skip(p)
            return
        }
        val pc = para
        if (pc != null) breakParagraph(pc)
        start("div", "class" to cls, "style" to fill?.takeUnless(::redundantWhite)?.let { StyleBuilder().add("background-color", it).build() })
        subListDepth++
        para = null
        val savedBackground = background
        if (fill != null) background = fill
        children(p) { name -> if (name == "subList") blocks(p, where) else skip(p) }
        background = savedBackground
        para = pc
        subListDepth--
        end("div")
    }

    // ---- 그림·도형·수식 -------------------------------------------------------------------

    /**
     * 그림(`hp:pic`). 크기는 그림보다 앞에 오는 `hp:curSz`(HWPUNIT)에서 — `hp:sz` 는 그림 뒤에 온다.
     *
     * **대체 글은 설명문(`hp:shapeComment`)이다** — HWP 5.0 의 개체 설명문과 같은 글이고 같은 규칙(`HancomAlt`)으로 가린다.
     * 설명문은 스키마의 차례상 `hc:img` **뒤에** 오므로 훑기가 개체 번호([WalkState.objectOrdinal])로 적어 두고 그리기가
     * 읽는다(표의 칸 정보 `TableScan` 과 같은 장치). 처음에는 HWPX 만 대체 글을 늘 비웠다(13단계에서 미룬 것).
     */
    private fun picture(p: XmlPullParser) {
        val key = HwpxLayout.objectKey(cfg.section, state.objectOrdinal++)
        val alt = cfg.alts[key] ?: inheritedAlt.orEmpty()
        var width: Double? = null
        var after: HtmlWriter? = null
        children(p) { name ->
            when (name) {
                "curSz" -> {
                    HwpxXml.int(p, "width")?.takeIf { it > 0 }?.let { width = it / HWPUNIT_PER_PT }
                    skip(p)
                }
                "orgSz" -> {
                    if (width == null) HwpxXml.int(p, "width")?.takeIf { it > 0 }?.let { width = it / HWPUNIT_PER_PT }
                    skip(p)
                }
                "img" -> {
                    val id = HwpxXml.attr(p, "binaryItemIDRef")
                    skip(p)
                    image(id, width, alt)
                }
                "caption" -> after = joinCaptions(after, caption(p))
                "shapeComment" -> shapeComment(p, key)
                else -> skip(p)
            }
        }
        placeCaption(after)
    }

    /**
     * 설명문(`hp:shapeComment`) — 훑기가 읽어 사람이 쓴 것만 개체 번호로 적어 둔다. 그리기는 건너뛴다(이미 적어 둔 값을
     * 쓴다). 두 모드 모두 이 요소를 끝까지 소비한다([HwpxWalker] 의 약속).
     */
    private fun shapeComment(p: XmlPullParser, key: Long) {
        val scan = cfg.scan
        if (scan == null) {
            skip(p)
            return
        }
        scan.alt(key, HancomAlt.of(HwpxXml.collectText(p, limits, HancomAlt.MAX_READ_CHARS)))
    }

    /**
     * 그림 하나. `binaryItemIDRef` → `content.hpf` 의 `opf:item/@id` → `@href`(스키마의 설명). 패키지에 없거나
     * 바깥을 가리키면 연결된 파일로, 화면이 못 그리는 형식(BMP 는 그린다 — WMF·EMF·TIFF 는 못 그린다)이면 그릴 수
     * 없는 그림으로 센다.
     *
     * **자원 하나의 상한([FlowDocumentBase.MAX_RESOURCE_BYTES])을 넘는 그림도 그릴 수 없는 그림이다.** 바탕은 그것을
     * 내주지 않으므로 `img` 를 쓰면 깨진 그림 표시만 뜨고 배지에는 아무것도 없다. 한글은 붙여 넣은 그림을 BMP 로
     * 두어 이것이 흔하다 — K28 의 BMP 셋이 67~70 MB 다.
     */
    private fun image(id: String?, width: Double?, alt: String) {
        if (id.isNullOrEmpty()) {
            record(UnsupportedFeatures.UNSUPPORTED_IMAGE)
            return
        }
        val item = env.pkg.item(id)
        if (item == null) {
            record(UnsupportedFeatures.UNSUPPORTED_IMAGE)
            return
        }
        val name = item.name
        if (name == null) {
            val external = !item.embedded || EXTERNAL.containsMatchIn(item.href)
            record(if (external) UnsupportedFeatures.LINKED_FILE else UnsupportedFeatures.UNSUPPORTED_IMAGE)
            return
        }
        if (!env.isDisplayableImage(name) || env.pkg.declaredSize(name) > FlowDocumentBase.MAX_RESOURCE_BYTES) {
            record(UnsupportedFeatures.UNSUPPORTED_IMAGE)
            return
        }
        if (state.deleting > 0) return
        val pc = para ?: return
        ensureOpen(pc)
        pc.visible = true
        val style = width?.let { StyleBuilder().add("width", CssValues.pt(it, 1.0, MAX_IMAGE_PT)).build() }
        void("img", "src" to FlowUrls.encode(name), "alt" to alt, "style" to style)
    }

    /**
     * 묶음 개체 — 안의 그림·도형·수식을 차례로. 묶음 자신의 캡션도 쓴다(처음에는 건너뛰어 그 글을 잃었다). 묶음의 설명문은
     * 제 설명문이 없는 안의 그림이 물려받는다([inheritedAlt]) — 설명문이 안의 개체들 **뒤에** 오므로 훑기가 적어 둔 값이다.
     */
    private fun container(p: XmlPullParser, rc: RunCtx) {
        if (graphicDepth >= HwpxLimits.MAX_GRAPHIC_DEPTH) {
            skip(p)
            return
        }
        graphicDepth++
        val key = HwpxLayout.objectKey(cfg.section, state.objectOrdinal++)
        val savedAlt = inheritedAlt
        cfg.alts[key]?.let { inheritedAlt = it }
        var after: HtmlWriter? = null
        children(p) { name ->
            when (name) {
                in OBJECTS -> runChild(p, name, rc)
                "caption" -> after = joinCaptions(after, caption(p))
                "shapeComment" -> shapeComment(p, key)
                else -> skip(p)
            }
        }
        inheritedAlt = savedAlt
        graphicDepth--
        placeCaption(after)
    }

    /**
     * OLE 개체·동영상·차트 — 그리지 않고 센다(부르는 쪽). **캡션은 쓴다** — 처음에는 개체를 통째로 건너뛰어 캡션의 글을 잃었다
     * (13단계 짝 대조). HWP 5.0 은 이것들이 모두 그리기 개체(`gso`)라 종류와 관계없이 캡션을 그린다.
     */
    private fun embedded(p: XmlPullParser) {
        var after: HtmlWriter? = null
        children(p) { name -> if (name == "caption") after = joinCaptions(after, caption(p)) else skip(p) }
        placeCaption(after)
    }

    /**
     * 그리기 개체(사각형·타원·선…). 글상자(`hp:drawText`)가 있으면 그 글을 `div.tb` 로 쓴다 — 도형의 모양은
     * 그리지 않지만 글은 잃지 않는다. 글이 없는 도형만 센다(docx 의 판단과 같다: 글상자는 세지 않는다).
     */
    private fun shape(p: XmlPullParser) {
        var hadText = false
        var fill: String? = null
        var after: HtmlWriter? = null
        children(p) { name ->
            when (name) {
                // 채우기(`hc:fillBrush`)는 스키마 차례상 글상자보다 앞에 온다.
                "fillBrush" -> fill = brushColor(p)
                "drawText" -> {
                    hadText = true
                    box(p, "tb", Where.BOX, fill)
                }
                "caption" -> after = joinCaptions(after, caption(p))
                else -> skip(p)
            }
        }
        placeCaption(after)
        if (!hadText) record(UnsupportedFeatures.SHAPE)
    }

    /** 채우기의 면 색(`hc:winBrush/@faceColor`). 무늬·그러데이션·그림 채우기는 모른다(null). */
    private fun brushColor(p: XmlPullParser): String? {
        var color: String? = null
        children(p) { brush ->
            if (brush == "winBrush" && color == null) color = HwpxXml.color(HwpxXml.attr(p, "faceColor"))
            skip(p)
        }
        return color
    }

    /**
     * 수식 — 한글의 수식 스크립트를 글자 그대로 `span.math` 로(docx 가 수식을 글자로 쓰는 것과 같다). 캡션도 쓴다 — 처음에는
     * 스크립트만 읽어 수식의 캡션 글을 잃었다(13단계 짝 대조).
     */
    private fun equation(p: XmlPullParser) {
        record(UnsupportedFeatures.EQUATION)
        var script = ""
        var after: HtmlWriter? = null
        children(p) { name ->
            when (name) {
                "script" -> script = HwpxXml.collectText(p, limits, HwpxLimits.MAX_SCRIPT_CHARS)
                "caption" -> after = joinCaptions(after, caption(p))
                else -> skip(p)
            }
        }
        val t = script.trim()
        val pc = para
        if (t.isNotEmpty() && state.deleting == 0 && pc != null) {
            cfg.scan?.addChars(t.length)
            ensureOpen(pc)
            pc.visible = true
            start("span", "class" to "math")
            text(t)
            end("span")
        }
        placeCaption(after)
    }

    /**
     * 글맵시 — 모양은 그리지 않고 글만. 캡션도 쓴다(다른 개체와 같은 자리 규칙, [caption]) — 처음에는 요소를 통째로 건너뛰어
     * 캡션의 글을 잃었다. HWP 5.0 은 글맵시도 그리기 개체(`gso`)라 캡션을 그린다(13단계 짝 대조의 후속 검토).
     */
    private fun textArt(p: XmlPullParser, rc: RunCtx) {
        val t = HwpxXml.attr(p, "text")?.take(HwpxLimits.MAX_SHORT_TEXT)?.let(HancomChars::mapAll)
        record(UnsupportedFeatures.TEXT_EFFECT)
        var after: HtmlWriter? = null
        children(p) { name -> if (name == "caption") after = joinCaptions(after, caption(p)) else skip(p) }
        if (!t.isNullOrBlank()) emit(t, rc)
        placeCaption(after)
    }

    /**
     * 글자 겹치기. 공문서의 절 번호(네모 안의 숫자)가 여기 든다 — 한컴의 사설 영역 글자라 두 변환기가 같은 규칙
     * ([HancomChars.compose])으로 옮기고, 그러고도 남은 사설 영역 글자(한글 전용 글꼴의 기호)는 버리고 센다.
     */
    private fun compose(p: XmlPullParser, rc: RunCtx) {
        val c = HancomChars.compose(HwpxXml.attr(p, "composeText").orEmpty().take(HwpxLimits.MAX_SHORT_TEXT))
        skip(p)
        if (c.dropped) record(UnsupportedFeatures.TEXT_EFFECT)
        if (c.text.isNotEmpty()) emit(c.text, rc)
    }

    /** 덧말 — 본말 위에 작은 글자. `ruby` 로 쓴다. */
    private fun dutmal(p: XmlPullParser, rc: RunCtx) {
        var mainText = ""
        var subText = ""
        children(p) { name ->
            when (name) {
                "mainText" -> mainText = HancomChars.mapAll(HwpxXml.collectText(p, limits, HwpxLimits.MAX_SHORT_TEXT))
                "subText" -> subText = HancomChars.mapAll(HwpxXml.collectText(p, limits, HwpxLimits.MAX_SHORT_TEXT))
                else -> skip(p)
            }
        }
        if (mainText.isEmpty() || state.deleting > 0) return
        if (subText.isEmpty()) {
            emit(mainText, rc)
            return
        }
        val pc = para ?: return
        cfg.scan?.addChars(mainText.length + subText.length)
        ensureOpen(pc)
        pc.visible = true
        start("ruby")
        text(mainText)
        start("rt")
        text(subText)
        end("ruby")
    }

    // ---- 모양 -------------------------------------------------------------------

    /**
     * 문단 하나. 여는 태그는 **처음 쓸 것이 생길 때** 쓴다([ensureOpen]) — 비었는지 알아야 빈 문단을 줄일 수
     * 있고, 표·글상자로 끊긴 뒤에는 번호 없이 이어 열어야 하기 때문이다.
     *
     * @param gen 문단을 (다시) 열 때마다 는다. 링크를 연 쪽이 닫을 때 같은 문단인지 확인한다.
     */
    private class ParaCtx(
        val tag: String,
        val id: String?,
        val cls: String?,
        val marker: String?,
        /** 이어 열 때의 클래스 — 번호 표지(`li`)는 처음 연 조각에만 있다. */
        val continuationCls: String?,
        val paraPrId: Int?,
        val heading: Boolean,
        /** 스타일의 글자 모양 — 덩이 없는 문단의 바탕. */
        val styleChar: Int?,
        /** 비어 있어도 연다(제목·번호 문단). 바탕이 정해질 때 연다([run]·[finishParagraph]). */
        val eager: Boolean,
    ) {
        /** 바탕 글자 모양(첫 덩이의 것)이 정해졌다([settle]). 그 전에는 [style] 이 없다. */
        var baseSet = false
        var baseChar: Int? = null
        var style: String? = null
        var opened = false
        var visible = false
        var continuation = false
        var gen = 0
        var linkDepth = 0
        var headingText: StringBuilder? = null
        var headingLevel = 0
        var effectRecorded = false
    }

    /**
     * 글자 한 벌(`hp:run`)의 모양과, 거기서 만든 CSS — 문단 바탕 글자 모양 [baseId] 와 다른 것만((모양, 바탕)마다 한 번 —
     * [HwpxEnv.charCss]).
     */
    private inner class RunCtx(private val charId: Int?, private val baseId: Int?) {
        val format: CharFormat = header.char(charId)
        val css: String? by lazy(LazyThreadSafetyMode.NONE) {
            val light = lightBackground
            env.charCss(charId, baseId, light) { f, base -> runCss(f, base, light) }
        }
        val wrap: String? = when {
            format.superscript -> "sup"
            format.subscript -> "sub"
            else -> null
        }
    }

    /** 각주 하나를 걷는 동안의 것. [label] 은 본문 쪽 표지(각주 본문의 자동 번호가 고친다). */
    private class NoteCtx(val backId: String, val number: Int, val format: AutoNumFormat) {
        var label: String = ""
    }

    /** 열린 필드 하나. [tag] 는 이 필드가 연 태그(`a`·`span`) — 문단이 끝나면 저절로 닫히므로 문단마다 지운다. */
    private class FieldFrame(val id: String?) {
        var tag: String? = null
        var gen = -1
    }

    /**
     * 글자 덩이의 CSS — **물려받는 것**(크기·굵기·기울임·색·명조)은 문단 바탕 [base] 와 다른 것만 적는다(글자마다 적으면 HTML
     * 이 몇 배가 된다). 물려받지 않는 장식(밑줄·윗줄·취소선)과 음영은 언제나 적는다. HWP 5.0 의 `RunStyle.css(base)` 와 같은
     * 규칙이다 — 크기는 바탕의 %, 바탕이 굵은데 덩이가 굵지 않으면 `normal`, 바탕에 색이 있는데 덩이에 없으면 `initial`.
     *
     * 흰 글자 규칙은 [shownColor] 다. 형광펜은 여기서 모른다([emit] 이 더한다).
     */
    private fun runCss(f: CharFormat, base: CharFormat, lightBackground: Boolean): String? {
        val style = StyleBuilder()
        val height = heightOf(f)
        // 바탕에 크기가 없으면 문단이 크기를 적지 않았다 — 문서 기본 크기를 물려받는다.
        val baseHeight = heightOf(base) ?: header.defaultHeight
        if (height != null && height != baseHeight) {
            style.add("font-size", CssValues.percent(height * 100.0 / baseHeight, MIN_FONT_PERCENT, MAX_FONT_PERCENT))
        }
        if (f.bold != base.bold) style.add("font-weight", if (f.bold) "bold" else "normal")
        if (f.italic != base.italic) style.add("font-style", if (f.italic) "italic" else "normal")
        val color = shownColor(f, lightBackground)
        if (color != shownColor(base, lightBackground)) style.add("color", color ?: "initial")
        // 글꼴 이름은 적지 않는다 — 한컴 글꼴(휴먼명조·한양중고딕…)은 안드로이드에 없고, 칸 수만 개의 표에서 이름이
        // 글자마다 붙으면 HTML 이 수백 KB 는다(K25 실측). 명조 계열만 `serif` 로 옮긴다(`HancomFonts`) — 안드로이드의
        // 기본(고딕)과 달라 보여야 하는 유일한 차이다. 바탕에 글꼴이 없으면 문서 기본의 글꼴을 물려받은 것이다.
        val baseFont = base.font ?: header.defaultChar.font
        val serif = HancomFonts.isSerif(f.font ?: baseFont)
        if (serif != HancomFonts.isSerif(baseFont)) style.add("font-family", if (serif) "serif" else "sans-serif")
        style.add("text-decoration", decoration(f))
        // 음영(`shadeColor`)은 흰색이면 칠하지 않는다 — 한글이 '음영 없음' 을 흰색으로 적는 파일이 있다.
        f.shade?.takeIf { it != WHITE }?.let { style.add("background-color", it) }
        return style.build()
    }

    /**
     * 문단 요소에 한 번 적는 **바탕 글자**의 CSS — 문서 기본 글자 모양과 다른 것만. HWP 5.0 의 `RunStyle.paragraphCss` 와 같다:
     * **제목이면 크기와 굵기를 언제나 적는다**(적지 않으면 브라우저의 `h1{font-size:2em}` 이 한글의 크기를 덮는다 — 바탕 CSS 의
     * `h1{font-size:1em}` 은 안전망이다). 색은 **보이는** 색([shownColor])이 기본과 다를 때만, 보이는 색이 없으면 적지 않는다.
     */
    private fun baseCss(base: CharFormat, heading: Boolean, lightBackground: Boolean, style: StyleBuilder) {
        val doc = header.defaultChar
        val docHeight = header.defaultHeight
        val height = heightOf(base)
        if (height != null && (heading || height != docHeight)) {
            style.add("font-size", CssValues.percent(height * 100.0 / docHeight, MIN_FONT_PERCENT, MAX_FONT_PERCENT))
        }
        if (heading || base.bold != doc.bold) style.add("font-weight", if (base.bold) "bold" else "normal")
        if (base.italic != doc.italic) style.add("font-style", if (base.italic) "italic" else "normal")
        val color = shownColor(base, lightBackground)
        if (color != null && color != shownColor(doc, lightBackground)) style.add("color", color)
        val serif = HancomFonts.isSerif(base.font ?: doc.font)
        if (serif != HancomFonts.isSerif(doc.font)) style.add("font-family", if (serif) "serif" else "sans-serif")
    }

    /**
     * 실제로 적을 글자 색. [lightBackground] 면 흰 글자(세 채널 모두 0xE0 이상)의 색을 적지 않는다(null) — 흐름 렌더는 도형·
     * 그림을 글 뒤에 깔지 못해 흰 글자가 흰 화면에서 **사라진다.** 바탕을 아는 곳(칸의 면 색·글상자의 채우기)에서는 문서의 색을
     * 그대로 쓴다. **글자 자신의 음영이 바탕이다** — 음영이 어두우면 흰 글자는 그 위에서 읽힌다(칸의 면 색만 보고 지우면 남색
     * 음영 위의 흰 글자가 검게 사라졌다 — 검토가 잡았다). 흰 음영은 음영이 아니다. HWP 5.0 의 `RunStyle.shownColor` 와 같다.
     */
    private fun shownColor(f: CharFormat, lightBackground: Boolean): String? {
        val shade = f.shade?.takeIf { it != WHITE }
        val onLight = if (shade != null) isLightColor(shade) else lightBackground
        return f.color?.takeUnless { onLight && isLightColor(it) }
    }

    /** 장식 — 밑줄·윗줄([CharFormat.underline])과 취소선. HWP 5.0 의 `RunStyle.css` 와 같은 조합이다. */
    private fun decoration(f: CharFormat): String? = when {
        f.underline == CharFormat.LINE_BELOW && f.strike -> "underline line-through"
        f.underline == CharFormat.LINE_ABOVE && f.strike -> "overline line-through"
        f.underline == CharFormat.LINE_BELOW -> "underline"
        f.underline == CharFormat.LINE_ABOVE -> "overline"
        f.strike -> "line-through"
        else -> null
    }

    private fun heightOf(f: CharFormat): Int? = f.height?.takeIf { it > 0 }

    /**
     * 문단 요소의 CSS — 문단 모양에 바탕 글자([baseCss])를 더한다. 내어쓰기(들여쓰기가 음수)는 한글처럼 **첫 줄만 왼쪽으로**
     * 나오게 `margin-left` 를 그만큼 늘리고 `text-indent` 를 음수로 둔다(바로보기의 변환기도 그렇게 쓴다).
     */
    private fun paraCss(f: ParaFormat, base: CharFormat, heading: Boolean, lightBackground: Boolean): String? {
        val style = StyleBuilder()
        style.add("text-align", ALIGN[f.align?.uppercase()])
        val indent = f.indent
        val left = f.left + if (indent < 0) -indent else 0.0
        if (left > 0.01) style.add("margin-left", CssValues.pt(left, 0.0, MAX_INDENT_PT))
        if (f.right > 0.01) style.add("margin-right", CssValues.pt(f.right, 0.0, MAX_INDENT_PT))
        if (indent < -0.01 || indent > 0.01) style.add("text-indent", CssValues.pt(indent, -MAX_INDENT_PT, MAX_INDENT_PT))
        if (f.before > 0.01) style.add("margin-top", CssValues.pt(f.before, 0.0, MAX_SPACING_PT))
        if (f.after > 0.01) style.add("margin-bottom", CssValues.pt(f.after, 0.0, MAX_SPACING_PT))
        // 한글의 기본 줄 간격(160%)은 CSS 가 이미 준다 — 문단마다 적지 않는다.
        f.lineHeight?.let { lh ->
            if (lh in MIN_LINE_HEIGHT..MAX_LINE_HEIGHT && kotlin.math.abs(lh - DEFAULT_LINE_HEIGHT) > 0.001) {
                style.add("line-height", CssValues.number(lh, MIN_LINE_HEIGHT, MAX_LINE_HEIGHT))
            }
        }
        baseCss(base, heading, lightBackground, style)
        return style.build()
    }

    companion object {
        private const val SKIPPED = -2
        private const val MAX_NUMBER = 1_000_000
        private const val MAX_DELETE_DEPTH = 64
        private const val HWPUNIT_PER_PT = 100.0
        private const val MAX_IMAGE_PT = 2000.0
        // 들여쓰기·간격·글자 크기의 상한은 HWP 5.0 변환기(`Hwp5Limits`·`RunStyle`)와 같은 값이다 — 같은 문서가 포맷에
        // 따라 달리 보이지 않게(13단계 짝 표본 대조: 차례 줄의 내어쓰기가 HWP 200pt, HWPX 255pt 였다). 폰 화면의 폭이
        // 300pt 남짓이라 큰 여백은 글 칸을 몇 글자로 줄인다.
        private const val MAX_INDENT_PT = 200.0
        private const val MAX_SPACING_PT = 72.0
        private const val MIN_FONT_PERCENT = 30.0
        private const val MAX_FONT_PERCENT = 400.0
        private const val DEFAULT_MARKPEN = "#ffff00"
        private const val DEFAULT_LINE_HEIGHT = 1.6
        private const val MIN_LINE_HEIGHT = 0.5
        private const val MAX_LINE_HEIGHT = 5.0

        /** 캡션의 클래스 — 바탕 CSS(`HancomCss.FLOW`)의 `.cap`. HWP 5.0 과 같다(처음에는 HWPX 만 `caption` 이었다). */
        private const val CAPTION_CLASS = "cap"

        /** 각주·미주의 시작 번호 상한 — HWP 5.0 의 `NoteShape.start` 와 같다. */
        private const val MAX_START = 100_000

        /** `hp:switch` 에서 알아듣는 `required-namespace`. 수식·차트·단위 확장. */
        private val UNDERSTOOD_NAMESPACES = setOf(
            HwpxHeaderParser.HWPUNITCHAR_NS,
            "http://www.hancom.co.kr/hwpml/2016/ooxmlchart",
        )

        private val SHAPES = setOf("rect", "ellipse", "arc", "polygon", "curve", "connectLine", "line")

        private val FORM_CONTROLS = setOf("btn", "radioBtn", "checkBtn", "comboBox", "listBox", "edit", "scrollBar")

        /** 묶음 개체 안에 올 수 있는 개체. */
        private val OBJECTS = SHAPES + setOf(
            "pic", "container", "equation", "ole", "chart", "video", "textart", "switch", "tbl",
        )

        /**
         * 정렬. **양쪽 정렬(`JUSTIFY`)과 왼쪽은 적지 않는다** — HWP 5.0 변환기와 같은 판단이다. 한글 문서는 거의 모든
         * 문단이 양쪽 정렬이고, 좁은 폰 화면에서 `word-break:keep-all` 과 양쪽 정렬이 만나면 낱말 사이가 크게 벌어진다
         * (13단계 기기 확인에서 보았다). 배분·나눔(`DISTRIBUTE`·`DISTRIBUTE_SPACE`)은 문서가 일부러 고른 것이라 옮긴다.
         */
        private val ALIGN = mapOf(
            "CENTER" to "center",
            "RIGHT" to "right",
            "DISTRIBUTE" to "justify",
            "DISTRIBUTE_SPACE" to "justify",
        )

        private val EXTERNAL = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")

        /** 밝은 색인가 — `#rrggbb` 의 세 채널이 모두 0xE0 이상(흰색에 가까운 것만). HWP 5.0 의 `RunStyle.isLight` 와 같다. */
        fun isLightColor(hex: String): Boolean {
            if (hex.length != 7 || hex[0] != '#') return false
            val v = hex.substring(1).toIntOrNull(16) ?: return false
            return (v ushr 16 and 0xFF) >= LIGHT && (v ushr 8 and 0xFF) >= LIGHT && (v and 0xFF) >= LIGHT
        }

        private const val LIGHT = 0xE0
        private const val WHITE = "#ffffff"
    }
}

/** 하이퍼링크 필드가 가리키는 곳. [bookmark] 가 null 이면 바깥 주소다. */
internal class HyperlinkTarget(val bookmark: String?)

/**
 * 하이퍼링크 필드의 `Command` 매개변수 읽기(python-hwpx `hyperlink_form.py` 의 관찰 — 한컴의 명세 문서로는
 * 확인하지 못했다): `대상;종류;…`, 대상 안의 `: ? ; #` 는 역슬래시로 이스케이프되고, 문서 안 책갈피는 `?이름` 으로
 * 적힌다. 대상 뒤의 `|풍선 도움말` 은 버린다.
 */
internal object HyperlinkCommand {

    fun parse(command: String?): HyperlinkTarget {
        if (command.isNullOrBlank()) return HyperlinkTarget(null)
        val first = StringBuilder()
        var i = 0
        while (i < command.length) {
            val c = command[i]
            if (c == '\\' && i + 1 < command.length) {
                first.append(command[i + 1])
                i += 2
                continue
            }
            if (c == ';') break
            first.append(c)
            i++
        }
        val target = first.toString().substringBefore('|').trim()
        if (target.startsWith("?")) {
            val name = target.substring(1).removePrefix("#").trim()
            if (name.isNotEmpty()) return HyperlinkTarget(name)
        }
        return HyperlinkTarget(null)
    }
}
