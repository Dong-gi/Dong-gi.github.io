package io.github.donggi.iroiroviewer.format.docx

import io.github.donggi.iroiroviewer.format.UnsupportedFeatures
import io.github.donggi.iroiroviewer.format.html.CssValues
import io.github.donggi.iroiroviewer.format.html.HtmlWriter
import io.github.donggi.iroiroviewer.format.html.StyleBuilder
import io.github.donggi.iroiroviewer.format.opc.OoxmlXml
import io.github.donggi.iroiroviewer.format.opc.OpcNames
import io.github.donggi.iroiroviewer.format.opc.OpcRelationship
import io.github.donggi.iroiroviewer.safety.nextGuarded
import org.xmlpull.v1.XmlPullParser

/** 블록이 어디에 있는가. 최상위([BODY])만 번호를 받고 조각의 경계가 된다. */
internal enum class Where { BODY, CELL, TEXTBOX, NOTE }

/**
 * 본문이 가리킨 각주·미주 하나. 조각의 끝에 그 본문을 모아 그릴 때 쓴다.
 *
 * @param backId 본문 쪽 참조 표지의 `id`. 각주의 번호를 누르면 거기로 돌아간다.
 */
internal class NoteRef(val endnote: Boolean, val id: Int, val label: String, val backId: String?)

/**
 * 걷기 한 번의 설정. 훑기(여는 동안 한 번)와 그리기(조각마다)가 **같은 걷기**를 쓰고, 다른 것은
 * 여기 적힌 것뿐이다.
 *
 * @param scan 훑기면 있다. 조각 경계·제목·책갈피·행 합치기를 모은다.
 * @param recordFeatures 버린 것을 센다. 훑기만 켠다([DocxEnv.features] 의 주석).
 * @param start 그리기에서 이 조각의 첫 최상위 블록. 앞의 블록은 들여다보지 않고 건너뛴다.
 * @param end 그리기에서 이 조각이 끝나는 블록(포함하지 않는다).
 * @param notes 각주·미주(와 메모)의 본문을 걷는 중이다.
 * @param smartArts 훑기가 읽어 둔 SmartArt 의 글([DocxLayout.smartArts]). 그리기만 읽는다.
 * @param comments 그리기에서 이 조각이 가리킨 메모를 모으는 곳. 본문과 각주의 걷기가 함께 쓴다.
 * @param inComment 메모의 본문을 걷는 중이다 — 메모 안의 메모 표지는 따르지 않는다(워드가 만들지 않는다).
 */
internal class WalkConfig(
    val sourcePart: String,
    val scan: ScanCollector? = null,
    val recordFeatures: Boolean = false,
    val start: Int = 0,
    val end: Int = Int.MAX_VALUE,
    val chunkIndex: Int = 0,
    val bookmarkChunks: Map<String, Int> = emptyMap(),
    val tableSpans: Map<Int, IntArray> = emptyMap(),
    val partHref: (Int) -> String = { "" },
    val onTruncated: () -> Unit = {},
    val notes: Boolean = false,
    val checkCancel: () -> Unit = {},
    val smartArts: Map<Int, SmartArtText> = emptyMap(),
    val comments: CommentSink? = null,
    val inComment: Boolean = false,
)

/** 걷기를 멈춘다 — 조각의 끝에 닿았거나 쓰기 상한에 닿았다. 제어 흐름이라 스택을 담지 않는다. */
internal object StopWalk : RuntimeException() {
    private fun readResolve(): Any = StopWalk
    override fun fillInStackTrace(): Throwable = this
}

/**
 * `document.xml`(과 각주 부분)을 **흐름으로** 걸어가며 HTML 을 쓴다. DOM 을 만들지 않는다.
 *
 * ## 훑기와 그리기가 같은 걷기다
 *
 * 긴 문서를 조각으로 나누려면 여는 동안 한 번 훑어야 하고(경계·목차), 그리려면 조각마다 다시 걸어야
 * 한다. 둘을 다른 코드로 두면 **조각의 시작에서 목록 번호·각주 번호·표 번호가 어긋난다** — 훑기가 세지
 * 않은 글상자 안의 번호 문단 하나로 뒤 조각 전체의 번호가 밀린다. 그래서 걷기는 하나이고, 훑기는
 * 쓰는 곳([sink])이 없는 걷기다. 훑기가 조각의 시작마다 찍어 둔 상태([WalkState])에서 그리기가
 * 다시 걷기 시작한다.
 *
 * 이 약속을 지키는 방법은 하나다 — **두 모드가 같은 요소를 같은 순서로 소비한다.** 보이지 않게 할
 * 것은 건너뛰지 말고 쓰기만 끈다([suppress]). 건너뛰는 것은 두 모드가 똑같이 건너뛰는 것(지운 글,
 * 깊이 상한을 넘은 표)뿐이다.
 *
 * ## HTML 의 모양
 *
 * 문단은 `p`(제목은 `h1`..`h6`), 목록은 **중첩 목록을 만들지 않고** 번호를 계산해 적은 `p.li` 다.
 * 워드의 목록은 문단마다 수준을 따로 가지므로 `ul` 로 다시 짜면 수준이 건너뛰는 문서에서 무너진다.
 * 글상자는 문단 안에 `div` 를 둘 수 없으므로 **문단을 거기서 끊고** `div.tb` 로 쓴 뒤 문단을 이어 연다.
 * 표의 행 합치기(`vMerge`)는 뒤의 행을 봐야 알 수 있어서 훑기가 계산해 두고([ScanCollector.tableSpans])
 * 그리기는 그 값을 읽기만 한다 — 그래서 표를 통째로 기억에 올리지 않는다.
 */
internal class DocxWalker(
    private val env: DocxEnv,
    private val sink: HtmlWriter?,
    private val state: WalkState,
    private val cfg: WalkConfig,
) {
    private val limits = env.limits

    private val rels: Map<String, OpcRelationship> by lazy { env.relationshipsOf(cfg.sourcePart) }

    /** 그리는 동안 만난 각주·미주 참조. 조각의 끝에 그 본문을 모아 그린다. */
    val noteRefs = ArrayList<NoteRef>()

    /** 각주 본문을 그리는 중이면 그 각주. 본문 안의 `w:footnoteRef` 가 번호를 적는다. */
    var currentNote: NoteRef? = null

    /** 0 보다 크면 걷되 쓰지 않는다(합쳐진 칸, 숨긴 글상자). */
    private var suppress = 0
    private var para: ParaCtx? = null
    private var topNext = 0
    private var topCurrent = -1
    private var tableDepth = 0
    private var textBoxDepth = 0
    private val pendingAnchors = ArrayList<String>()
    private var emptyRun = 0
    private var ticks = 0
    private var noteLinkPending: String? = null

    /** 훑기가 읽은 SmartArt — 데이터 부분의 이름 → 읽은 글(못 읽었으면 null). [readSmartArt] 의 주석. */
    private val smartArtReads = HashMap<String, SmartArtText?>()

    /** 그리기에서 본문을 걷는 중 — 조각 밖의 최상위 블록을 건너뛴다. */
    private val bodyRange: Boolean = cfg.scan == null && !cfg.notes

    private val writing: Boolean get() = sink != null && suppress == 0

    // ---- 입구 -------------------------------------------------------------------

    /** 본문 부분 하나를 걷는다. [p] 는 막 입력을 물린 파서다. */
    fun document(p: XmlPullParser) {
        try {
            var ev = p.eventType
            while (ev != XmlPullParser.START_TAG) {
                if (ev == XmlPullParser.END_DOCUMENT) return
                ev = p.nextGuarded(limits)
            }
            children(p) { name ->
                if (name == "body") {
                    blocks(p, Where.BODY)
                    // 마지막 블록 뒤의 책갈피는 이을 문단이 없다. 그대로 두면 그것을 가리키는 링크가 갈 곳을 잃는다.
                    flushAnchors()
                } else {
                    skip(p)
                }
            }
        } catch (_: StopWalk) {
            // 조각의 끝 — 정상이다.
        }
    }

    /** 각주·미주 하나(`w:footnote`)의 본문을 걷는다. 쓰기 상한에 닿으면 [StopWalk] 를 던진다. */
    fun note(p: XmlPullParser, ref: NoteRef?) {
        currentNote = ref
        blocks(p, Where.NOTE)
        currentNote = null
    }

    // ---- 쓰기 -------------------------------------------------------------------

    private fun start(tag: String, vararg attrs: Pair<String, String?>) {
        if (!writing) return
        val w = sink!!
        // 상한에 닿은 뒤의 여는 태그는 `HtmlWriter` 가 쓰지 않고 열린 것으로만 세는데, 짝을 맞추는 닫는
        // 태그는 상한과 관계없이 쓴다. 문단 하나에 글자 덩이가 수백만이면 덩이마다 `</span>` 이 붙어
        // 결과가 상한을 몇 배로 넘고, 위생기의 입력 상한까지 넘으면 조각이 통째로 빈다. 그래서 여기서 멈춘다.
        if (w.full) throw StopWalk
        w.start(tag, *attrs)
    }

    private fun end(tag: String) {
        if (writing) sink!!.end(tag)
    }

    private fun void(tag: String, vararg attrs: Pair<String, String?>) {
        if (writing) sink!!.void(tag, *attrs)
    }

    private fun text(s: CharSequence) {
        if (writing) sink!!.text(s)
    }

    private fun record(kind: String, n: Int = 1) {
        if (cfg.recordFeatures) env.features.record(kind, n)
    }

    private fun headerFooter() {
        if (cfg.recordFeatures && !env.headerFooterRecorded) {
            env.headerFooterRecorded = true
            env.features.record(UnsupportedFeatures.HEADER_FOOTER)
        }
    }

    private fun skip(p: XmlPullParser) = OoxmlXml.skip(p, limits)

    private inline fun children(p: XmlPullParser, onChild: (String) -> Unit) = DocxProps.eachChild(p, limits, onChild)

    /** 내용 조절(`w:sdt`)·사용자 XML·스마트 태그 — 겉을 벗기고 속을 같은 자리의 것으로 걷는다. */
    private inline fun container(p: XmlPullParser, handle: (String) -> Unit) {
        children(p) { name ->
            when (name) {
                "sdtContent" -> children(p) { handle(it) }
                "sdtPr", "sdtEndPr", "customXmlPr", "smartTagPr" -> skip(p)
                else -> handle(name)
            }
        }
    }

    /** 호환 블록 — [DocxCompat] 의 규칙으로 가지 하나를 걷는다. */
    private inline fun alternate(p: XmlPullParser, handle: (String) -> Unit) {
        val taken = DocxCompat.choose(p, limits) { children(p) { handle(it) } }
        if (!taken) record(UnsupportedFeatures.UNKNOWN_ELEMENT)
    }

    private fun tick() {
        if (++ticks and 63 == 0) cfg.checkCancel()
    }

    // ---- 블록 -------------------------------------------------------------------

    private fun blocks(p: XmlPullParser, where: Where) {
        children(p) { name -> block(p, name, where) }
    }

    private fun block(p: XmlPullParser, name: String, where: Where) {
        when (name) {
            "p" -> paragraph(p, where)
            "tbl" -> table(p, where)
            "sdt", "customXml" -> container(p) { block(p, it, where) }
            "ins", "moveTo" -> blocks(p, where)
            "bookmarkStart" -> bookmark(p)
            "AlternateContent" -> alternate(p) { block(p, it, where) }
            "sectPr" -> if (DocxProps.readSectPr(p, limits)) headerFooter()
            "altChunk" -> {
                record(UnsupportedFeatures.EMBEDDED_OBJECT)
                skip(p)
            }
            "oMathPara", "oMath" -> mathBlock(p, where)
            // 지운 것(`w:del`·`w:moveFrom`)과 모르는 것은 건너뛴다.
            else -> skip(p)
        }
    }

    /**
     * 최상위 블록이면 번호를 매긴다. 그리는 중이면 조각 밖을 건너뛰고([SKIPPED]), 끝에 닿으면 멈춘다.
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
        if (sink?.full == true) throw StopWalk
        val savedPara = para
        val savedTop = topCurrent
        if (index >= 0) topCurrent = index
        para = null
        var begun = false
        children(p) { name ->
            if (!begun) {
                begun = true
                if (name == "pPr") {
                    beginParagraph(DocxProps.readPPr(p, limits), index)
                    return@children
                }
                beginParagraph(null, index)
            }
            inline(p, name)
        }
        if (!begun) beginParagraph(null, index)
        para?.let { finishParagraph(it) }
        para = savedPara
        topCurrent = savedTop
    }

    /**
     * 문단의 속성을 정하고 번호를 센다. **최상위 블록이면 번호를 세기 전에** 훑기에게 알린다 — 조각이
     * 여기서 끊기면 찍어 둔 상태에 이 문단의 번호가 들어가 있으면 안 된다.
     */
    private fun beginParagraph(pp: ParaProps?, index: Int) {
        val rs = env.styles.paragraph(pp?.styleId)
        val direct = pp?.outlineLvl
        val level = if (direct != null) direct.takeIf { it in 0..8 } else rs.headingLevel
        val heading = level != null
        if (index >= 0) cfg.scan?.onTopBlock(index, heading, state)
        if (pp?.sectionHasHeaders == true) headerFooter()

        var marker: String? = null
        var lvl: LevelDef? = null
        var ilvl = 0
        val numId = pp?.numId ?: rs.numId
        // 문단 표시를 지운 문단은 최종본에서 따로 서지 않는다 — 번호를 세면 뒤의 번호가 하나씩 밀리고,
        // 표지를 그리면 빈 글머리표가 남는다(Tika 의 `delins.docx` 에서 빈 '•' 둘이 보였다).
        val markDeleted = pp?.markDeleted == true
        if (numId != null && numId > 0 && !markDeleted) {
            val num = env.numbering.resolve(numId)
            if (num != null) {
                ilvl = (pp?.ilvl ?: rs.ilvl ?: rs.numStyleId?.let { env.numbering.levelForStyle(num, it) } ?: 0)
                    .coerceIn(0, DocxNumbering.LEVELS - 1)
                lvl = num.levels[ilvl]
                marker = state.lists.next(num, ilvl).takeIf { it.isNotEmpty() }
            }
        }

        // 들여쓰기: 직접 서식 > 번호 수준 > 스타일. 워드는 번호 수준의 들여쓰기를 스타일보다 앞세운다
        // (`List Paragraph` 의 들여쓰기가 목록의 들여쓰기에 진다).
        val indLeft = pp?.indLeft ?: lvl?.indLeft ?: rs.indLeft
        val indFirst = pp?.indFirst ?: lvl?.indFirst ?: rs.indFirst
        val indRight = pp?.indRight ?: rs.indRight

        val tag = when {
            level == null -> "p"
            level <= 5 -> "h${level + 1}"
            else -> "p"
        }
        val classes = ArrayList<String>(2)
        if (level != null && level > 5) classes.add("h7")
        if (rs.isTitle) classes.add("title")
        if (marker != null) classes.add("li")

        val style = StyleBuilder()
        style.add("text-align", align(pp?.jc ?: rs.jc))
        var left = indLeft?.let { it / TWIPS_PER_PT }
        var first = indFirst?.let { it / TWIPS_PER_PT }
        if (marker != null && left == null) {
            left = DEFAULT_LIST_INDENT_PT * (ilvl + 1)
            if (first == null) first = -DEFAULT_LIST_INDENT_PT
        }
        left?.let { style.add("margin-left", CssValues.pt(it, 0.0, MAX_INDENT_PT)) }
        indRight?.let { style.add("margin-right", CssValues.pt(it / TWIPS_PER_PT, 0.0, MAX_INDENT_PT)) }
        first?.let { if (it != 0.0) style.add("text-indent", CssValues.pt(it, -MAX_INDENT_PT, MAX_INDENT_PT)) }
        (pp?.shading ?: rs.shading)?.takeIf { it.isNotEmpty() }?.let { style.add("background-color", it) }
        paragraphRunCss(rs.run, heading, style)

        val hanging = first?.takeIf { it < 0 }?.let { -it }
        val markerStyle = if (marker != null && hanging != null && lvl?.suffix != "nothing" && lvl?.suffix != "space") {
            StyleBuilder().add("min-width", CssValues.pt(hanging, 0.0, MAX_INDENT_PT)).build()
        } else {
            null
        }

        val pc = ParaCtx(
            tag = tag,
            id = if (index >= 0 && heading) DocxIds.heading(index) else null,
            cls = classes.joinToString(" ").ifEmpty { null },
            style = style.build(),
            marker = marker,
            markerStyle = markerStyle,
            runBase = rs.run,
        )
        pc.markDeleted = markDeleted
        if (heading && index >= 0 && cfg.scan != null) {
            val t = StringBuilder()
            if (marker != null) t.append(marker).append(' ')
            pc.headingText = t
            pc.headingLevel = level
        }
        para = pc
        // 제목(목차가 가리킨다)과 번호 문단(표지가 보여야 한다)은 비어 있어도 연다. 표시를 지운 문단은
        // 남은 글이 있을 때만 선다.
        if ((heading || marker != null) && !markDeleted) ensureOpen(pc)
    }

    /** 문단 스타일의 글자 속성은 문단 요소에 한 번 적는다. 글자마다 적으면 HTML 이 몇 배가 된다. */
    private fun paragraphRunCss(run: RunProps, heading: Boolean, style: StyleBuilder) {
        val base = env.styles.defaultSizeHalfPt
        val size = run.sizeHalfPt
        // 제목은 크기를 늘 적는다 — 적지 않으면 브라우저의 `h1{font-size:2em}` 이 워드의 크기를 덮는다.
        if (size != null && (heading || size != base)) {
            style.add("font-size", CssValues.percent(size * 100.0 / base, MIN_FONT_PERCENT, MAX_FONT_PERCENT))
        }
        when (run.bold) {
            true -> style.add("font-weight", "bold")
            false -> if (heading) style.add("font-weight", "normal")
            null -> Unit
        }
        if (run.italic == true) style.add("font-style", "italic")
        run.color?.takeIf { it.isNotEmpty() }?.let { style.add("color", it) }
        style.add("font-family", run.font)
        if (run.caps == true) style.add("text-transform", "uppercase")
        if (run.smallCaps == true) style.add("font-variant", "small-caps")
    }

    /** 문단을 연다(처음 쓸 것이 생길 때). 이어 여는 것(글상자 뒤)은 번호·`id` 없이 연다. */
    private fun ensureOpen(pc: ParaCtx) {
        if (pc.opened) return
        pc.opened = true
        pc.gen++
        if (pc.continuation) {
            start(pc.tag, "class" to pc.cls, "style" to pc.style)
            flushAnchors()
            return
        }
        start(pc.tag, "id" to pc.id, "class" to pc.cls, "style" to pc.style)
        flushAnchors()
        if (pc.marker != null) {
            start("span", "class" to "mk", "style" to pc.markerStyle)
            text(pc.marker)
            end("span")
            pc.visible = true
        }
    }

    /**
     * 문단을 닫는다. 빈 문단은 줄 하나(`br`)로 남긴다 — 워드에서 빈 줄로 띄운 간격이 사라지지 않게.
     * 다만 잇달아 [MAX_EMPTY_RUN] 개를 넘는 빈 문단은 버린다(빈 문단 수천 개로 화면을 채운 문서가 있다).
     */
    private fun finishParagraph(pc: ParaCtx) {
        pc.headingText?.let { t -> if (topCurrent >= 0) cfg.scan?.heading(topCurrent, pc.headingLevel, t.toString()) }
        if (!pc.opened) {
            if (pendingAnchors.isEmpty()) {
                // 표시를 지운 빈 문단은 빈 줄로도 남기지 않는다 — 최종본에는 없는 줄이다.
                if (pc.continuation || pc.markDeleted) return
                if (emptyRun >= MAX_EMPTY_RUN) {
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

    /** 글상자를 쓰려고 문단을 끊는다. 뒤의 글은 [ensureOpen] 이 이어 연다. */
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
        for (f in state.fields) f.link = null
    }

    private fun mathBlock(p: XmlPullParser, where: Where) {
        val index = enterTop(p, where)
        if (index == SKIPPED) return
        val savedPara = para
        val savedTop = topCurrent
        if (index >= 0) topCurrent = index
        beginParagraph(null, index)
        math(p)
        para?.let { finishParagraph(it) }
        para = savedPara
        topCurrent = savedTop
    }

    // ---- 문단 안 -------------------------------------------------------------------

    private fun inline(p: XmlPullParser, name: String) {
        when (name) {
            "r" -> runElement(p)
            "hyperlink" -> hyperlink(p)
            "fldSimple" -> fldSimple(p)
            "smartTag", "customXml", "sdt", "dir", "bdo", "ins", "moveTo" -> container(p) { inline(p, it) }
            "bookmarkStart" -> bookmark(p)
            "oMath", "oMathPara" -> math(p)
            "AlternateContent" -> alternate(p) { inline(p, it) }
            // 지운 글(`w:del`·`w:moveFrom`)은 보이지 않는다. 메모 범위(`w:commentRangeStart`…)는 칠하지 않는다 — 표지는
            // 범위 끝의 `w:commentReference` 가 적는다.
            else -> skip(p)
        }
    }

    private fun runElement(p: XmlPullParser) {
        val pc = para
        if (pc == null) {
            skip(p)
            return
        }
        var rc = RunCtx(pc.runBase)
        noteLinkPending = null
        children(p) { name ->
            if (name == "rPr") {
                val r = DocxProps.readRPr(p, limits)
                rc = RunCtx(pc.runBase.overlay(env.styles.character(r.styleId)).overlay(r.props))
            } else {
                runContent(p, name, rc)
            }
        }
        noteLinkPending = null
    }

    private fun runContent(p: XmlPullParser, name: String, rc: RunCtx) {
        when (name) {
            "t" -> {
                val t = OoxmlXml.collectText(p, limits, MAX_TEXT)
                // 글자 요소 하나가 상한을 넘으면 잘렸다 — 조용히 자르지 않고 알린다.
                if (t.length >= MAX_TEXT) cfg.onTruncated()
                emit(t, rc)
            }
            "tab", "ptab" -> {
                skip(p)
                emit("\t", rc)
            }
            "br" -> {
                val type = OoxmlXml.attr(p, "type")
                skip(p)
                // 쪽·단 나눔은 흐름 렌더에 뜻이 없다(쪽 재현을 포기했다).
                if (type == null || type == "textWrapping") lineBreak(rc)
            }
            "cr" -> {
                skip(p)
                lineBreak(rc)
            }
            "noBreakHyphen" -> {
                skip(p)
                emit("-", rc)
            }
            "sym" -> sym(p, rc)
            "drawing", "pict" -> graphicsRoot(p, rc)
            "object" -> {
                record(UnsupportedFeatures.EMBEDDED_OBJECT)
                skip(p)
            }
            "footnoteReference" -> noteReference(p, rc, endnote = false)
            "endnoteReference" -> noteReference(p, rc, endnote = true)
            "footnoteRef", "endnoteRef" -> noteMark(p, rc)
            "commentReference" -> commentReference(p, rc)
            "fldChar" -> fieldChar(p, rc)
            "instrText" -> instrText(p)
            "ruby" -> ruby(p, rc)
            "AlternateContent" -> alternate(p) { runContent(p, it, rc) }
            "contentPart" -> {
                record(UnsupportedFeatures.SHAPE)
                skip(p)
            }
            // `w:delText`·`w:delInstrText`(지운 글), `w:softHyphen`, `w:lastRenderedPageBreak`, 구분선…
            else -> skip(p)
        }
    }

    /** 지금 글이 보이는가 — 필드의 명령 부분 밖이고 숨긴 글이 아니다. */
    private fun visible(rc: RunCtx): Boolean = fieldsVisible() && !rc.hidden

    private fun fieldsVisible(): Boolean {
        for (f in state.fields) if (!f.result) return false
        return true
    }

    /** 글자 한 덩이. 문단 바탕과 다른 서식만 `span` 에 적는다. */
    private fun emit(s: String, rc: RunCtx) {
        if (s.isEmpty() || !visible(rc)) return
        val pc = para ?: return
        cfg.scan?.addChars(s.length)
        pc.headingText?.let { if (it.length < MAX_HEADING_CHARS) it.append(s, 0, minOf(s.length, MAX_HEADING_CHARS - it.length)) }
        if (rc.props.effect == true && !pc.effectRecorded) {
            pc.effectRecorded = true
            record(UnsupportedFeatures.TEXT_EFFECT)
        }
        ensureOpen(pc)
        pc.visible = true
        val link = noteLinkPending?.takeIf { pc.linkDepth == 0 }
        noteLinkPending = null
        if (!writing) return
        val style = rc.css(pc.runBase, env.styles.defaultSizeHalfPt)
        val wrap = rc.wrap
        if (style != null) start("span", "style" to style)
        if (wrap != null) start(wrap)
        if (link != null) start("a", "href" to link)
        text(s)
        if (link != null) end("a")
        if (wrap != null) end(wrap)
        if (style != null) end("span")
    }

    private fun lineBreak(rc: RunCtx) {
        if (!visible(rc)) return
        val pc = para ?: return
        ensureOpen(pc)
        pc.visible = true
        void("br")
    }

    /** 기호(`w:sym`). Symbol·Wingdings 의 흔한 글자만 옮기고 모르는 것은 버린다(두부보다 낫다). */
    private fun sym(p: XmlPullParser, rc: RunCtx) {
        val font = OoxmlXml.attr(p, "font")
        val code = OoxmlXml.attr(p, "char")?.trim()?.toIntOrNull(16)
        skip(p)
        if (code == null || code !in 0x20..0xFFFF) return
        val s = ListMarkers.glyph(code, font) ?: return
        emit(s, rc)
    }

    private fun ruby(p: XmlPullParser, rc: RunCtx) {
        var rt = ""
        children(p) { name ->
            when (name) {
                // 명세의 차례가 `w:rt` → `w:rubyBase` 라 윗글을 먼저 글자로만 받아 둔다.
                "rt" -> rt = plainText(p, MAX_RUBY_CHARS)
                "rubyBase" -> {
                    val pc = para
                    val show = pc != null && visible(rc)
                    var gen = -1
                    if (show) {
                        ensureOpen(pc)
                        start("ruby")
                        gen = pc.gen
                    }
                    children(p) { inline(p, it) }
                    if (show && pc.opened && pc.gen == gen) {
                        if (rt.isNotEmpty()) {
                            start("rt")
                            text(rt)
                            end("rt")
                        }
                        end("ruby")
                    }
                }
                else -> skip(p)
            }
        }
    }

    /** 하위의 `t` 요소 글자만 모은다. */
    private fun plainText(p: XmlPullParser, max: Int): String {
        val sb = StringBuilder()
        children(p) { name ->
            if (name == "t" && sb.length < max) {
                sb.append(OoxmlXml.collectText(p, limits, max - sb.length))
            } else {
                sb.append(plainText(p, max - sb.length))
            }
        }
        return sb.toString()
    }

    private fun math(p: XmlPullParser) {
        record(UnsupportedFeatures.EQUATION)
        val t = DocxMath.text(p, limits)
        if (t.isEmpty() || !fieldsVisible()) return
        val pc = para ?: return
        cfg.scan?.addChars(t.length)
        ensureOpen(pc)
        pc.visible = true
        start("span", "class" to "math")
        text(t)
        end("span")
    }

    // ---- 링크·책갈피·필드 -------------------------------------------------------------------

    /**
     * `w:hyperlink`. 바깥 주소(`r:id`)는 **링크로 만들지 않는다** — 위생기가 스킴이 붙은 주소를
     * 지우고, 이 앱은 네트워크를 쓰지 않는다. 글자만 `span.ext` 로 표시한다. 문서 안 주소(`w:anchor`)는
     * 책갈피가 있는 조각을 찾아 링크로 만든다.
     */
    private fun hyperlink(p: XmlPullParser) {
        val rid = OoxmlXml.rel(p, "id")
        val anchor = OoxmlXml.attr(p, "anchor")
        val pc = para
        var tag: String? = null
        var gen = -1
        if (pc != null && fieldsVisible()) {
            if (rid != null) {
                ensureOpen(pc)
                start("span", "class" to "ext")
                tag = "span"
            } else if (anchor != null && pc.linkDepth == 0) {
                val href = anchorHref(anchor)
                if (href != null) {
                    ensureOpen(pc)
                    start("a", "href" to href)
                    tag = "a"
                    pc.linkDepth++
                }
            }
            gen = pc.gen
        }
        children(p) { inline(p, it) }
        if (tag != null && pc != null && pc.opened && pc.gen == gen) {
            end(tag)
            if (tag == "a") pc.linkDepth--
        }
    }

    /** 책갈피가 있는 조각을 가리키는 주소. 책갈피가 없으면 null(갈 곳 없는 링크는 만들지 않는다). */
    private fun anchorHref(name: String): String? {
        val chunk = cfg.bookmarkChunks[name] ?: return null
        val id = DocxIds.bookmark(name)
        return if (chunk == cfg.chunkIndex) "#$id" else cfg.partHref(chunk) + "#" + id
    }

    private fun bookmark(p: XmlPullParser) {
        val name = OoxmlXml.attr(p, "name")
        skip(p)
        // `_GoBack` 은 워드가 마지막 편집 자리에 두는 것이라 가리킬 일이 없다.
        if (name.isNullOrEmpty() || name == "_GoBack" || cfg.notes) return
        val scan = cfg.scan
        if (scan != null) {
            scan.bookmark(name, if (topCurrent >= 0) topCurrent else topNext)
            return
        }
        if (!writing) return
        val id = DocxIds.bookmark(name)
        val pc = para
        if (pc != null) {
            ensureOpen(pc)
            start("span", "id" to id)
            end("span")
        } else if ((topCurrent >= 0 || topNext in cfg.start until cfg.end) && pendingAnchors.size < MAX_PENDING) {
            // 블록 사이의 책갈피는 다음 문단이 열릴 때 그 안에 둔다(`tr` 안에 `span` 을 둘 수 없다).
            pendingAnchors.add(id)
        }
    }

    private fun fieldChar(p: XmlPullParser, rc: RunCtx) {
        val type = OoxmlXml.attr(p, "fldCharType")
        // 옛 양식의 확인란(`FORMCHECKBOX`)은 결과 글이 비어 있고 상자는 워드가 스스로 그린다 — 켜짐·꺼짐은 필드
        // 시작의 `ffData` 에 적힌다. 그리지 않으면 확인란이 통째로 사라져 체크 여부를 알 길이 없다(POI 의
        // `checkboxes.docx` 에서 'unchecked:'·'Or checked:' 뒤가 비어 있었다).
        var checkbox: Boolean? = null
        if (type == "begin") {
            children(p) { name ->
                if (name == "ffData") {
                    children(p) { f -> if (f == "checkBox") checkbox = readCheckBox(p) else skip(p) }
                } else {
                    skip(p)
                }
            }
        } else {
            skip(p)
        }
        val fields = state.fields
        when (type) {
            "begin" -> if (state.ignoredFieldBegins > 0 || fields.size >= MAX_FIELD_DEPTH) {
                state.ignoredFieldBegins++
            } else {
                // 필드를 세우기 전에 쓴다 — 세운 뒤에는 명령 부분이라 보이지 않는다.
                checkbox?.let { emit(if (it) CHECKED_BOX else EMPTY_BOX, rc) }
                fields.add(FieldFrame())
            }
            "separate" -> if (state.ignoredFieldBegins == 0 && fields.isNotEmpty()) {
                val f = fields[fields.size - 1]
                if (!f.result) {
                    f.result = true
                    openFieldLink(f)
                }
            }
            "end" -> if (state.ignoredFieldBegins > 0) {
                state.ignoredFieldBegins--
            } else if (fields.isNotEmpty()) {
                closeFieldLink(fields.removeAt(fields.size - 1))
            }
        }
    }

    /** `w:checkBox` — `w:checked` 가 있으면 그것, 없으면 `w:default`(ECMA-376 Part 1 §17.16.7·§17.16.11). */
    private fun readCheckBox(p: XmlPullParser): Boolean {
        var checked: Boolean? = null
        var byDefault = false
        children(p) { c ->
            when (c) {
                "checked" -> checked = onOff(OoxmlXml.attr(p, "val"))
                "default" -> byDefault = onOff(OoxmlXml.attr(p, "val"))
            }
            skip(p)
        }
        return checked ?: byDefault
    }

    /** 켬·끔 값(`ST_OnOff`). 값이 없으면 켬이다. */
    private fun onOff(v: String?): Boolean =
        v == null || !(v == "0" || v.equals("false", ignoreCase = true) || v.equals("off", ignoreCase = true))

    private fun instrText(p: XmlPullParser) {
        val f = state.fields.lastOrNull()
        if (f != null && !f.result && state.ignoredFieldBegins == 0 && f.instr.length < MAX_INSTR) {
            f.instr.append(OoxmlXml.collectText(p, limits, MAX_INSTR - f.instr.length))
        } else {
            skip(p)
        }
    }

    private fun fldSimple(p: XmlPullParser) {
        val f = FieldFrame(result = true, instr = StringBuilder(OoxmlXml.attr(p, "instr").orEmpty().take(MAX_INSTR)))
        openFieldLink(f)
        children(p) { inline(p, it) }
        closeFieldLink(f)
    }

    /**
     * 필드 결과를 링크로 감싼다 — 목차(`HYPERLINK \l "_Toc…"`)와 상호 참조(`REF … \h`)가 문서 안을
     * 가리킨다. 바깥 주소는 글자만 표시한다.
     */
    private fun openFieldLink(f: FieldFrame) {
        if (!fieldsVisible()) return
        val pc = para ?: return
        val target = FieldInstr.parse(f.instr) ?: return
        if (target.anchor != null) {
            if (pc.linkDepth > 0) return
            val href = anchorHref(target.anchor) ?: return
            ensureOpen(pc)
            start("a", "href" to href)
            pc.linkDepth++
            f.link = "a"
        } else {
            ensureOpen(pc)
            start("span", "class" to "ext")
            f.link = "span"
        }
        f.linkGen = pc.gen
    }

    private fun closeFieldLink(f: FieldFrame) {
        val tag = f.link ?: return
        f.link = null
        val pc = para ?: return
        if (pc.opened && pc.gen == f.linkGen) {
            end(tag)
            if (tag == "a") pc.linkDepth--
        }
    }

    // ---- 각주·미주 -------------------------------------------------------------------

    private fun noteReference(p: XmlPullParser, rc: RunCtx, endnote: Boolean) {
        val id = OoxmlXml.int(p, "id")
        val custom = OoxmlXml.attr(p, "customMarkFollows").let { it == "1" || it == "true" || it == "on" }
        skip(p)
        if (cfg.notes || id == null) return
        // 번호는 보이든 말든 센다 — 두 모드가 같은 값을 세야 한다.
        val number = if (custom) 0 else if (endnote) ++state.endnoteNo else ++state.footnoteNo
        if (!visible(rc)) return
        val pc = para ?: return
        val prefix = if (endnote) "en" else "fn"
        val label = if (custom) "*" else (if (endnote) env.endnoteFormat else env.footnoteFormat).label(number)
        val backId = if (custom) null else "${prefix}ref-$number"
        if (writing && noteRefs.size < MAX_NOTES) noteRefs.add(NoteRef(endnote, id, label, backId))
        if (custom) {
            // 사용자 표지(`*`)는 뒤따르는 글자가 표지다. 그 글자를 링크로 감싼다.
            noteLinkPending = "#$prefix-$id"
            return
        }
        cfg.scan?.addChars(label.length)
        ensureOpen(pc)
        pc.visible = true
        start("sup", "class" to "fnref", "id" to backId)
        if (pc.linkDepth == 0) start("a", "href" to "#$prefix-$id")
        text(label)
        end("sup")
    }

    /** 각주 본문 안의 번호 자리(`w:footnoteRef`). */
    private fun noteMark(p: XmlPullParser, rc: RunCtx) {
        skip(p)
        val note = currentNote ?: return
        if (!visible(rc)) return
        val pc = para ?: return
        ensureOpen(pc)
        pc.visible = true
        start("sup", "class" to "fnnum")
        note.backId?.let { start("a", "href" to "#$it") }
        text(note.label)
        end("sup")
    }

    // ---- 메모 -------------------------------------------------------------------

    /**
     * 메모 표지(`w:commentReference`) — 메모가 달린 범위의 끝에 온다. 그 자리에 작은 표지(`[머리글자 번호]`)를 두고, 메모의 본문은
     * 조각의 끝에 각주처럼 모아 그린다([CommentSink]). 표지를 누르면 본문으로, 본문의 머리를 누르면 표지로 간다.
     *
     * **배지는 보이지 않는 것만 센다** — 메모 부분이 없거나 읽지 못했거나(보조 부분의 실패는 따로 알렸다) 가리키는 메모가 없으면
     * 보일 글이 없으므로 [UnsupportedFeatures.COMMENT] 로 센다. 보인 메모는 세지 않는다(12단계는 그리지 않고 셌다).
     * 숨긴 글·필드 명령 안의 표지는 워드도 보이지 않으므로 표지도 본문도 없다.
     */
    private fun commentReference(p: XmlPullParser, rc: RunCtx) {
        val id = OoxmlXml.int(p, "id")
        skip(p)
        if (cfg.inComment || id == null || !visible(rc)) return
        val info = env.comments.find(id)
        if (info == null) {
            record(UnsupportedFeatures.COMMENT)
            return
        }
        // 훑기(본문·각주)가 가리킨 메모만 그 본문을 걸어 버린 것을 센다(`DocxEnv.referencedComments`).
        if (cfg.recordFeatures) env.referencedComments.add(id)
        val pc = para ?: return
        cfg.scan?.addChars(info.label.length)
        ensureOpen(pc)
        pc.visible = true
        if (!writing) return
        val (listed, backId) = cfg.comments?.add(info) ?: (false to null)
        start("sup", "class" to "cmref", "id" to backId)
        if (listed && pc.linkDepth == 0) start("a", "href" to "#${info.noteId}")
        text(info.label)
        end("sup")
    }

    // ---- SmartArt -------------------------------------------------------------------

    /**
     * SmartArt(`dgm:relIds`) — **캐시된 그림의 글**을 블록으로 그린다([DocxSmartArt]). 도형의 모양은 그리지 않으므로 글이 없는
     * 도형(화살표·바탕)은 도형으로 센다 — 글상자가 없는 도형을 세는 것과 같은 판단이다. 그림을 찾지 못하거나 못 읽으면
     * 예전처럼 SmartArt 로 센다(pptx 와 같다). 보인 SmartArt 는 SmartArt 로 세지 않는다.
     *
     * 글은 **훑기가 읽어** 번호([WalkState.smartArtOrdinal])로 적어 두고 그리기는 그것을 쓴다 — 두 모드의 셈과 모양이 한 번
     * 읽은 값에서 나온다. 번호는 본문의 걷기 상태에 있어 조각이 중간에서 시작해도 같다. 각주·메모 안의 SmartArt 는 그 걷기가
     * 따로 세므로(각주마다 새 상태) 적어 둘 열쇠가 없다 — 드물어서 예전처럼 센다.
     */
    private fun smartArt(dataRelId: String?, g: GraphicCtx) {
        if (cfg.notes) {
            record(UnsupportedFeatures.SMART_ART)
            return
        }
        val ordinal = state.smartArtOrdinal++
        val scan = cfg.scan
        if (scan != null) {
            val art = if (ordinal < MAX_SMART_ARTS) readSmartArt(dataRelId) else null
            if (art == null || !scan.smartArt(ordinal, art)) {
                record(UnsupportedFeatures.SMART_ART)
                return
            }
            record(UnsupportedFeatures.SHAPE, art.textless)
            scan.addChars(art.chars)
            return
        }
        val art = cfg.smartArts[ordinal] ?: return
        if (!g.visible || art.shapes.isEmpty()) return
        para?.let { breakParagraph(it) }
        start("div", "class" to "sa")
        for (shape in art.shapes) {
            for (sp in shape) {
                val style = if (sp.level > 0) StyleBuilder().add("margin-left", CssValues.pt(sp.level * SMART_ART_INDENT_PT, 0.0, MAX_INDENT_PT)).build() else null
                start("p", "style" to style)
                for ((i, line) in sp.text.split('\n').withIndex()) {
                    if (i > 0) void("br")
                    text(line)
                }
                end("p")
            }
        }
        end("div")
        // 글이 보였다 — 앞의 빈 문단 셈을 잇지 않는다. 이으면 상자 바로 뒤의 빈 줄이 '빈 문단이 넷째' 로 사라진다
        // (상자를 담은 문단은 끊긴 채 닫혀 [finishParagraph] 가 셈을 되돌리지 않는다).
        emptyRun = 0
        if (art.truncated) cfg.onTruncated()
    }

    /**
     * 훑기에서 SmartArt 하나의 캐시된 그림을 읽는다. 없거나 깨졌으면 null(부르는 쪽이 SmartArt 로 센다). 취소만 위로 올린다.
     *
     * **같은 데이터 부분은 한 번만 읽는다**([smartArtReads]) — 수백 개의 SmartArt 가 32 MB 짜리 부분 하나를 가리키게 만든 파일은
     * 그만큼을 되풀이해 풀게 한다(pptx 의 `diagramDrawing` 캐시와 같은 까닭).
     */
    private fun readSmartArt(dataRelId: String?): SmartArtText? {
        val data = dataRelId?.let { rels[it] }?.takeIf { !it.external } ?: return null
        val key = OpcNames.key(data.target)
        if (smartArtReads.containsKey(key)) return smartArtReads[key]
        val art = try {
            DocxSmartArt.drawingPart(env.pkg, limits, cfg.sourcePart, data, cfg.checkCancel)?.let { name ->
                env.pkg.parser(name)?.let { (p, stream) -> stream.use { DocxSmartArt.read(p, limits, checkCancel = cfg.checkCancel) } }
            }
        } catch (e: java.util.concurrent.CancellationException) {
            throw e
        } catch (e: java.io.InterruptedIOException) {
            throw e
        } catch (e: Exception) {
            null
        }
        smartArtReads[key] = art
        return art
    }

    // ---- 표 -------------------------------------------------------------------

    private class TableCtx(val spans: IntArray?, val merge: MergeTracker?) {
        var rows = 0
        var cells = 0
        var gridCol = 0
        var truncated = false
    }

    private fun table(p: XmlPullParser, where: Where) {
        val index = enterTop(p, where)
        if (index == SKIPPED) return
        if (sink?.full == true) throw StopWalk
        if (tableDepth >= MAX_TABLE_DEPTH) {
            cfg.onTruncated()
            skip(p)
            return
        }
        val savedTop = topCurrent
        val savedPara = para
        if (index >= 0) {
            topCurrent = index
            cfg.scan?.onTopBlock(index, false, state)
        }
        val ordinal = state.tableOrdinal++
        val t = TableCtx(
            spans = if (sink != null) cfg.tableSpans[ordinal] else null,
            merge = if (cfg.scan != null) MergeTracker() else null,
        )
        para = null
        flushAnchors()
        emptyRun = 0
        start("table")
        tableDepth++
        children(p) { name -> rowLevel(p, name, t) }
        tableDepth--
        end("table")
        t.merge?.result()?.let { cfg.scan?.tableSpans(ordinal, it) }
        para = savedPara
        topCurrent = savedTop
    }

    private fun rowLevel(p: XmlPullParser, name: String, t: TableCtx) {
        when (name) {
            "tr" -> row(p, t)
            "sdt", "customXml" -> container(p) { rowLevel(p, it, t) }
            "bookmarkStart" -> bookmark(p)
            "AlternateContent" -> alternate(p) { rowLevel(p, it, t) }
            else -> skip(p)
        }
    }

    private fun truncateTable(t: TableCtx) {
        if (!t.truncated) {
            t.truncated = true
            cfg.onTruncated()
        }
    }

    private fun row(p: XmlPullParser, t: TableCtx) {
        if (t.truncated || t.rows >= MAX_ROWS) {
            truncateTable(t)
            skip(p)
            return
        }
        if (sink?.full == true) throw StopWalk
        t.rows++
        tick()
        t.gridCol = 0
        start("tr")
        children(p) { name -> cellLevel(p, name, t) }
        end("tr")
        t.merge?.endRow()
    }

    private fun cellLevel(p: XmlPullParser, name: String, t: TableCtx) {
        when (name) {
            "tc" -> cell(p, t)
            "trPr" -> {
                var before = 0
                children(p) { n ->
                    if (n == "gridBefore") before = OoxmlXml.int(p, "val") ?: 0
                    skip(p)
                }
                // 앞을 비운 행(`w:gridBefore`)은 빈 칸 하나로 자리를 맞춘다.
                if (before > 0) {
                    val span = before.coerceAtMost(MAX_GRID_SPAN)
                    start("td", "class" to "gap", "colspan" to span.toString())
                    end("td")
                    t.gridCol += span
                }
            }
            "sdt", "customXml" -> container(p) { cellLevel(p, it, t) }
            "bookmarkStart" -> bookmark(p)
            "AlternateContent" -> alternate(p) { cellLevel(p, it, t) }
            else -> skip(p)
        }
    }

    private class CellProps(val gridSpan: Int, val vMerge: Int, val fill: String?)

    private fun readTcPr(p: XmlPullParser): CellProps {
        var span = 1
        var vMerge = MergeTracker.NONE
        var fill: String? = null
        children(p) { n ->
            when (n) {
                "gridSpan" -> span = OoxmlXml.int(p, "val") ?: 1
                // 값이 없으면 '이어짐' 이다.
                "vMerge" -> vMerge = if (OoxmlXml.attr(p, "val") == "restart") MergeTracker.RESTART else MergeTracker.CONTINUE
                "shd" -> fill = DocxProps.fill(p)?.takeIf { it.isNotEmpty() }
            }
            skip(p)
        }
        return CellProps(span.coerceIn(1, MAX_GRID_SPAN), vMerge, fill)
    }

    private fun cell(p: XmlPullParser, t: TableCtx) {
        if (t.truncated || t.cells >= MAX_CELLS) {
            truncateTable(t)
            skip(p)
            return
        }
        val cellIndex = t.cells++
        val savedPara = para
        para = null
        var begun = false
        var shown = true
        children(p) { name ->
            if (!begun) {
                begun = true
                if (name == "tcPr") {
                    shown = beginCell(readTcPr(p), t, cellIndex)
                    return@children
                }
                shown = beginCell(null, t, cellIndex)
            }
            if (shown) {
                block(p, name, Where.CELL)
            } else {
                // 위 칸에 합쳐진 칸 — 워드도 그 내용을 보이지 않는다. 걷기는 한다([DocxWalker] 의 주석).
                suppress++
                block(p, name, Where.CELL)
                suppress--
            }
        }
        if (!begun) shown = beginCell(null, t, cellIndex)
        if (shown) end("td")
        para = savedPara
    }

    /** 칸을 연다. 위 칸에 합쳐진 칸이면 열지 않고 거짓. */
    private fun beginCell(cp: CellProps?, t: TableCtx, cellIndex: Int): Boolean {
        val gridSpan = cp?.gridSpan ?: 1
        t.merge?.cell(t.gridCol, cp?.vMerge ?: MergeTracker.NONE)
        t.gridCol += gridSpan
        val rowspan = t.spans?.let { if (cellIndex < it.size) it[cellIndex] else 1 } ?: 1
        if (rowspan == 0) return false
        val style = cp?.fill?.let { StyleBuilder().add("background-color", it).build() }
        start(
            "td",
            "colspan" to gridSpan.takeIf { it > 1 }?.toString(),
            "rowspan" to rowspan.takeIf { it > 1 }?.toString(),
            "style" to style,
        )
        return true
    }

    // ---- 그림·글상자 -------------------------------------------------------------------

    private class GraphicCtx(val visible: Boolean) {
        var widthPt: Double? = null
        var widthUsed = false
        var alt: String? = null
        var content = 0
        var vmlWidthPt: Double? = null
    }

    /** `w:drawing`(DrawingML)·`w:pict`(VML). 그림·글상자·차트·SmartArt·도형을 가른다. */
    private fun graphicsRoot(p: XmlPullParser, rc: RunCtx) {
        graphics(p, GraphicCtx(visible(rc)), 0)
    }

    private fun graphics(p: XmlPullParser, g: GraphicCtx, depth: Int) {
        if (depth > MAX_GRAPHIC_DEPTH) {
            skip(p)
            return
        }
        children(p) { name -> graphic(p, name, g, depth) }
    }

    private fun graphic(p: XmlPullParser, name: String, g: GraphicCtx, depth: Int) {
        when (name) {
            "extent" -> {
                if (g.widthPt == null) g.widthPt = OoxmlXml.long(p, "cx")?.let { it / EMU_PER_PT }
                skip(p)
            }
            "docPr" -> {
                if (g.alt == null) {
                    g.alt = (
                        OoxmlXml.attr(p, "descr")?.takeIf { it.isNotBlank() }
                            ?: OoxmlXml.attr(p, "title")?.takeIf { it.isNotBlank() }
                            ?: OoxmlXml.attr(p, "name")
                        )?.take(MAX_ALT_CHARS)
                }
                skip(p)
            }
            "blip" -> {
                val embed = OoxmlXml.rel(p, "embed")
                val link = OoxmlXml.rel(p, "link")
                skip(p)
                image(embed, link, g, g.widthPt)
            }
            "imagedata" -> {
                val id = OoxmlXml.rel(p, "id") ?: OoxmlXml.attr(p, "relid")
                val linked = OoxmlXml.rel(p, "href") ?: OoxmlXml.attr(p, "src")
                skip(p)
                image(id, linked, g, g.vmlWidthPt ?: g.widthPt)
            }
            "chart" -> {
                record(UnsupportedFeatures.CHART)
                g.content++
                skip(p)
            }
            "relIds" -> {
                g.content++
                val data = OoxmlXml.rel(p, "dm")
                skip(p)
                smartArt(data, g)
            }
            "OLEObject" -> {
                record(UnsupportedFeatures.EMBEDDED_OBJECT)
                g.content++
                skip(p)
            }
            "contentPart" -> {
                record(UnsupportedFeatures.SHAPE)
                g.content++
                skip(p)
            }
            "txbxContent" -> {
                g.content++
                textBox(p, g)
            }
            "wsp", "sp", "shape", "rect", "roundrect", "oval", "line", "polyline", "arc", "curve" -> shape(p, name, g, depth)
            "AlternateContent" -> alternate(p) { graphic(p, it, g, depth + 1) }
            // 그릴 것이 없는 모양 정보. 들어가 봐야 시간만 쓴다.
            in LEAF_GRAPHICS -> skip(p)
            else -> graphics(p, g, depth + 1)
        }
    }

    private fun shape(p: XmlPullParser, name: String, g: GraphicCtx, depth: Int) {
        // VML 의 가로줄(`o:hr="t"`)은 워드의 '가로줄' 단추가 만든다. 버린 것으로 세지 않는다.
        val rule = OoxmlXml.attr(p, "hr") == "t"
        if (name != "wsp" && name != "sp") vmlWidth(OoxmlXml.attr(p, "style"))?.let { g.vmlWidthPt = it }
        val before = g.content
        graphics(p, g, depth + 1)
        if (g.content == before && !rule) record(UnsupportedFeatures.SHAPE)
    }

    /**
     * 그림 하나. 관계가 가리키는 부분을 **본문 부분의 폴더 기준으로** 푼 이름(관계 파일이 이미 풀어 둔다)을
     * `OpcNames.toUrl` 로 적는다. 바깥 파일을 가리키면(`r:link`) 연결된 파일로, 화면이 못 그리는 형식
     * (EMF·WMF·TIFF)이면 그릴 수 없는 그림으로 센다.
     */
    private fun image(embed: String?, link: String?, g: GraphicCtx, width: Double?) {
        if (embed == null) {
            if (link != null) {
                record(UnsupportedFeatures.LINKED_FILE)
                g.content++
            }
            return
        }
        g.content++
        val r = rels[embed]
        if (r == null) {
            record(UnsupportedFeatures.UNSUPPORTED_IMAGE)
            return
        }
        if (r.external) {
            record(UnsupportedFeatures.LINKED_FILE)
            return
        }
        val name = env.pkg.canonical(r.target)
        if (name == null || !env.isDisplayableImage(name)) {
            record(UnsupportedFeatures.UNSUPPORTED_IMAGE)
            return
        }
        if (!g.visible) return
        val pc = para ?: return
        ensureOpen(pc)
        pc.visible = true
        // 그룹 안의 그림 여럿에 그룹의 폭을 다 줄 수는 없다. 첫 그림에만 준다.
        val w = if (!g.widthUsed) width else null
        g.widthUsed = true
        val style = w?.let { StyleBuilder().add("width", CssValues.pt(it, 1.0, MAX_IMAGE_PT)).build() }
        void("img", "src" to OpcNames.toUrl(name), "alt" to (g.alt ?: ""), "style" to style)
    }

    /** 글상자. 문단을 끊고 그 자리에 `div.tb` 로 쓴다. */
    private fun textBox(p: XmlPullParser, g: GraphicCtx) {
        if (textBoxDepth >= MAX_TEXTBOX_DEPTH || tableDepth >= MAX_TABLE_DEPTH) {
            cfg.onTruncated()
            skip(p)
            return
        }
        val hide = !g.visible
        val pc = para
        if (!hide && pc != null) breakParagraph(pc)
        if (hide) suppress++
        start("div", "class" to "tb")
        textBoxDepth++
        para = null
        blocks(p, Where.TEXTBOX)
        para = pc
        textBoxDepth--
        end("div")
        if (hide) suppress--
    }

    /** VML 의 `style="width:120pt;…"` 에서 폭(pt). 단위가 없으면 px 다. */
    private fun vmlWidth(style: String?): Double? {
        if (style == null || style.length > 2048) return null
        for (decl in style.split(';')) {
            val colon = decl.indexOf(':')
            if (colon < 0 || decl.substring(0, colon).trim().lowercase() != "width") continue
            val v = decl.substring(colon + 1).trim().lowercase()
            val num = v.takeWhile { it.isDigit() || it == '.' }.toDoubleOrNull() ?: return null
            return when {
                v.endsWith("pt") -> num
                v.endsWith("in") -> num * 72
                v.endsWith("cm") -> num * 72 / 2.54
                v.endsWith("mm") -> num * 72 / 25.4
                v.endsWith("pc") -> num * 12
                else -> num * 0.75
            }
        }
        return null
    }

    // ---- 문단·글자의 상태 -------------------------------------------------------------------

    /**
     * 문단 하나. 여는 태그는 **처음 쓸 것이 생길 때** 쓴다([ensureOpen]) — 비었는지 알아야 빈 문단을
     * 줄일 수 있고, 글상자로 끊긴 뒤에는 번호 없이 이어 열어야 하기 때문이다.
     *
     * @param gen 문단을 (다시) 열 때마다 는다. 링크를 연 쪽이 닫을 때 같은 문단인지 확인한다 — 글상자로
     *   끊겼으면 링크는 이미 함께 닫혔다.
     */
    private class ParaCtx(
        val tag: String,
        val id: String?,
        val cls: String?,
        val style: String?,
        val marker: String?,
        val markerStyle: String?,
        val runBase: RunProps,
    ) {
        var opened = false
        var visible = false
        var continuation = false
        var gen = 0
        var linkDepth = 0
        var headingText: StringBuilder? = null
        var headingLevel = 0
        var effectRecorded = false

        /** 문단 표시가 지운 변경이다(`ParaProps.markDeleted`). */
        var markDeleted = false
    }

    /** 글자 한 벌(`w:r`)의 겹친 속성과, 거기서 만든 CSS(한 번만 만든다). */
    private class RunCtx(val props: RunProps) {
        private var cssDone = false
        private var cssValue: String? = null

        val hidden: Boolean get() = props.vanish == true

        val wrap: String?
            get() = when (props.vertAlign) {
                "superscript" -> "sup"
                "subscript" -> "sub"
                else -> null
            }

        fun css(base: RunProps, defaultSize: Int): String? {
            if (!cssDone) {
                cssValue = runCss(props, base, defaultSize)
                cssDone = true
            }
            return cssValue
        }
    }

    companion object {
        private const val SKIPPED = -2

        const val MAX_TABLE_DEPTH = 8
        const val MAX_ROWS = 5_000
        const val MAX_CELLS = 100_000
        private const val MAX_TEXTBOX_DEPTH = 3

        /** 문서 하나에서 글을 읽는 SmartArt 의 수. 넘는 것은 읽지 않고 센다 — 부분 하나가 32 MB 까지 들어온다. */
        const val MAX_SMART_ARTS = 500

        /** SmartArt 글의 목록 수준 하나의 들여쓰기(pt). */
        private const val SMART_ART_INDENT_PT = 12.0
        private const val MAX_GRAPHIC_DEPTH = 32
        private const val MAX_GRID_SPAN = 64
        private const val MAX_FIELD_DEPTH = 16
        private const val MAX_INSTR = 1024

        /**
         * 옛 양식 확인란의 모양(☐·☒). 안드로이드의 기호 글꼴 사본(`NotoSansSymbols-Regular-Subsetted`)의 cmap 에 두
         * 글자가 있음을 확인했다 — 기기 화면에서 그려 보지는 않았다.
         */
        private const val EMPTY_BOX = "☐"
        private const val CHECKED_BOX = "☒"
        private const val MAX_PENDING = 64
        private const val MAX_NOTES = 10_000
        private const val MAX_TEXT = 1_000_000
        private const val MAX_RUBY_CHARS = 200
        private const val MAX_ALT_CHARS = 300
        private const val MAX_HEADING_CHARS = 300
        const val MAX_EMPTY_RUN = 3

        private const val TWIPS_PER_PT = 20.0
        private const val EMU_PER_PT = 12700.0
        private const val DEFAULT_LIST_INDENT_PT = 18.0
        private const val MAX_INDENT_PT = 144.0
        private const val MAX_IMAGE_PT = 2000.0
        private const val MIN_FONT_PERCENT = 30.0
        private const val MAX_FONT_PERCENT = 400.0

        private val LEAF_GRAPHICS = setOf(
            "shapetype", "effectExtent", "simplePos", "positionH", "positionV", "cNvGraphicFramePr",
            "wrapSquare", "wrapTight", "wrapThrough", "wrapTopAndBottom", "wrapNone", "bodyPr", "style",
            "prstGeom", "custGeom", "xfrm", "ln", "effectLst", "solidFill", "noFill", "gradFill", "extLst",
            "stroke", "fill", "path", "formulas", "handles", "textpath", "lock", "wrap", "anchorlock", "shadow",
        )

        private fun align(jc: String?): String? = when (jc) {
            "left", "start" -> "left"
            "center" -> "center"
            "right", "end" -> "right"
            "both", "distribute", "justify", "thaiDistribute" -> "justify"
            else -> null
        }

        /**
         * 글자 한 벌의 CSS — **문단 바탕([base])과 다른 것만.** 물려받지 않는 장식(밑줄·취소선)과
         * 배경(형광펜·음영)은 언제나 적는다.
         */
        fun runCss(props: RunProps, base: RunProps, defaultSize: Int): String? {
            val sb = StyleBuilder()
            if (props.bold != base.bold) sb.add("font-weight", if (props.bold == true) "bold" else "normal")
            if (props.italic != base.italic) sb.add("font-style", if (props.italic == true) "italic" else "normal")
            if (props.color != base.color) sb.add("color", props.color?.ifEmpty { "initial" })
            if (props.font != base.font) sb.add("font-family", props.font)
            if (props.caps != base.caps) sb.add("text-transform", if (props.caps == true) "uppercase" else "none")
            if (props.smallCaps != base.smallCaps) sb.add("font-variant", if (props.smallCaps == true) "small-caps" else "normal")
            val size = props.sizeHalfPt
            if (size != null && size != base.sizeHalfPt) {
                sb.add("font-size", CssValues.percent(size * 100.0 / (base.sizeHalfPt ?: defaultSize), MIN_FONT_PERCENT, MAX_FONT_PERCENT))
            }
            val deco = when {
                props.underline == true && props.strike == true -> "underline line-through"
                props.underline == true -> "underline"
                props.strike == true -> "line-through"
                else -> null
            }
            sb.add("text-decoration", deco)
            val bg = props.highlight?.takeIf { it.isNotEmpty() } ?: props.shading?.takeIf { it.isNotEmpty() }
            sb.add("background-color", bg)
            return sb.build()
        }
    }
}

/**
 * 행 합치기(`w:vMerge`)를 훑기에서 계산한다. 칸마다(문서 순서) 그릴 `rowspan` — 합쳐져 사라지는 칸은 0.
 *
 * 위 행에서 **같은 격자 열에서 시작한** 칸을 찾아 잇는다. 이어짐(`continue`)인데 위에 시작이 없으면
 * 보통 칸으로 둔다(깨진 문서를 워드도 그렇게 그린다).
 */
internal class MergeTracker {
    private var spans = IntArray(16)
    private var count = 0
    private var any = false
    private var prev = HashMap<Int, Int>()
    private var cur = HashMap<Int, Int>()

    fun cell(gridCol: Int, vMerge: Int) {
        if (count == spans.size) spans = spans.copyOf(spans.size * 2)
        val idx = count++
        if (vMerge == CONTINUE) {
            val origin = prev[gridCol]
            if (origin != null) {
                spans[origin]++
                spans[idx] = 0
                cur[gridCol] = origin
                any = true
                return
            }
        }
        spans[idx] = 1
        cur[gridCol] = idx
    }

    fun endRow() {
        prev = cur
        cur = HashMap()
    }

    /** 합친 칸이 하나도 없으면 null(적어 둘 것이 없다). */
    fun result(): IntArray? = if (any) spans.copyOf(count) else null

    companion object {
        const val NONE = 0
        const val RESTART = 1
        const val CONTINUE = 2
    }
}

/** 필드 명령(`HYPERLINK \l "책갈피"`)에서 가리키는 곳. [anchor] 가 null 이면 바깥 주소다. */
internal class FieldTarget(val anchor: String?)

internal object FieldInstr {

    /** 링크가 되는 필드만 답한다. 나머지(`PAGE`·`TOC`·`MERGEFIELD`…)는 null. */
    fun parse(instr: CharSequence): FieldTarget? {
        val tokens = tokenize(instr)
        if (tokens.isEmpty()) return null
        return when (tokens[0].uppercase()) {
            "HYPERLINK" -> {
                var url: String? = null
                var anchor: String? = null
                var i = 1
                while (i < tokens.size) {
                    val t = tokens[i]
                    when {
                        t == "\\l" -> {
                            anchor = tokens.getOrNull(i + 1)
                            i++
                        }
                        // 인자를 받는 스위치(`\o "풍선 도움말"`·`\t "창"`).
                        t == "\\o" || t == "\\t" -> i++
                        t.startsWith("\\") -> Unit
                        url == null -> url = t
                    }
                    i++
                }
                when {
                    url != null -> FieldTarget(null)
                    anchor != null -> FieldTarget(anchor)
                    else -> null
                }
            }
            "REF", "PAGEREF" -> {
                val name = tokens.getOrNull(1)
                if (name != null && tokens.any { it.equals("\\h", ignoreCase = true) }) FieldTarget(name) else null
            }
            else -> null
        }
    }

    /** 공백으로 가르되 큰따옴표 안은 한 덩이로. */
    private fun tokenize(s: CharSequence): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var quoted = false
        for (c in s) {
            when {
                c == '"' -> {
                    if (quoted) {
                        out.add(cur.toString())
                        cur.setLength(0)
                    }
                    quoted = !quoted
                }
                c.isWhitespace() && !quoted -> if (cur.isNotEmpty()) {
                    out.add(cur.toString())
                    cur.setLength(0)
                }
                else -> cur.append(c)
            }
            if (out.size > 32) break
        }
        if (cur.isNotEmpty()) out.add(cur.toString())
        return out
    }
}
