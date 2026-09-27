package io.github.donggi.iroiroviewer.format.hwp5

import io.github.donggi.iroiroviewer.format.UnsupportedFeatures
import io.github.donggi.iroiroviewer.format.html.BulletGlyphs
import io.github.donggi.iroiroviewer.format.html.CssValues
import io.github.donggi.iroiroviewer.format.html.FlowUrls
import io.github.donggi.iroiroviewer.format.html.HancomChars
import io.github.donggi.iroiroviewer.format.html.HancomNumbers
import io.github.donggi.iroiroviewer.format.html.HtmlWriter
import io.github.donggi.iroiroviewer.format.html.StyleBuilder
import java.util.BitSet

/** 문단이 어디에 있는가. 최상위([BODY])만 번호를 받고 조각의 경계·제목이 된다. */
internal enum class Where { BODY, CELL, TEXTBOX, NOTE, CAPTION }

/**
 * 본문이 가리킨 각주·미주 하나. 조각의 끝에 그 본문을 모아 그릴 때 쓴다.
 *
 * @param seq 이 부분에서 센 차례(각주·미주 따로, 1부터). `id` 가 이것으로 정해진다 — 보이는 번호([label])는 되풀이될 수 있다.
 * @param ctrlPos 그 주석의 `CTRL_HEADER` 레코드 자리(구역 바이트 안). 본문은 그 아래의 문단 목록이다.
 */
internal class NoteRef(val endnote: Boolean, val seq: Int, val label: String, val ctrlPos: Int) {
    val id: String get() = (if (endnote) "en-" else "fn-") + seq
    val backId: String get() = (if (endnote) "enref-" else "fnref-") + seq
}

/** 최상위 문단 하나의 자리 — 구역 안의 차례, 문서 전체의 번호, 머리의 바이트 자리. */
internal class Top(val ordinal: Int, val global: Int, val offset: Int)

/**
 * 걷기 한 번의 설정. 훑기(여는 동안 한 번)와 그리기(조각마다)가 **같은 걷기**를 쓰고, 다른 것은 여기 적힌 것뿐이다.
 *
 * @param scan 훑기면 있다.
 * @param recordFeatures 버린 것을 센다. 훑기만 켠다 — 그리기는 캐시에서 밀려난 조각을 다시 그리므로 거기서 세면 두 번 센다.
 * @param globalBase 이 구역의 차례 0 번 문단의 문서 전체 번호.
 * @param layout 그리기면 있다 — 문서 안 링크와 `id` 를 푼다.
 * @param notes 각주·미주의 본문을 걷는 중이다.
 */
internal class WalkConfig(
    val scan: ScanCollector? = null,
    val recordFeatures: Boolean = false,
    val section: Int = 0,
    val startOrdinal: Int = 0,
    val endOrdinal: Int = Int.MAX_VALUE,
    val globalBase: Int = 0,
    val chunkIndex: Int = 0,
    val layout: Hwp5Layout? = null,
    val partHref: (Int) -> String = { "" },
    val onTruncated: () -> Unit = {},
    val checkCancel: () -> Unit = {},
    val notes: Boolean = false,
)

/** 걷기를 멈춘다 — 조각의 끝에 닿았거나 쓰기 상한에 닿았다. 제어 흐름이라 스택을 담지 않는다. */
internal object StopWalk : RuntimeException() {
    private fun readResolve(): Any = StopWalk
    override fun fillInStackTrace(): Throwable = this
}

/**
 * 한 문서를 읽는 동안 변하지 않는 것들 — 훑기와 그리기가 함께 쓴다.
 *
 * @param binName 바이너리 데이터 스트림 이름(`BIN0001.jpg`) → 꾸러미의 이름. 스트림이 없으면 null.
 * @param fitsResource 꾸러미의 그림(이름, 취소 확인)이 바탕이 내주는 상한 안에 드는가([Hwp5Package.fitsResource]).
 */
internal class Hwp5Env(
    val info: DocInfo,
    val features: UnsupportedFeatures,
    val isDisplayableImage: (String) -> Boolean,
    val binName: (String) -> String?,
    val fitsResource: (String, () -> Unit) -> Boolean,
) {
    /** 머리말·꼬리말은 문서에 한 번만 센다. */
    var headerFooterRecorded = false

    private val runs = arrayOfNulls<RunStyle>(minOf(info.charShapes.size, Hwp5Limits.MAX_TABLE_ENTRIES))

    /** 문서의 기본 글자 — 스타일 0(바탕글)의 글자 모양. */
    val defaultRun: RunStyle = info.style(0)?.let { st -> info.charShape(st.charShapeId)?.let { runOf(it) } }
        ?: info.charShape(0)?.let { runOf(it) } ?: RunStyle.DEFAULT

    private fun runOf(cs: CharShape) = RunStyle.of(cs, info.faceName(cs.faceHangul))

    /** 글자 모양 [id] 의 모양. 없는 번호면 문서 기본. */
    fun run(id: Int): RunStyle {
        if (id !in runs.indices) return defaultRun
        runs[id]?.let { return it }
        val cs = info.charShape(id) ?: return defaultRun
        return runOf(cs).also { runs[id] = it }
    }
}

/**
 * 구역 하나(의 조각)를 **레코드 흐름으로** 걸어가며 HTML 을 쓴다. 트리를 만들지 않는다 — 구역 바이트 위에서 커서만 옮긴다.
 *
 * ## 훑기와 그리기가 같은 걷기다
 *
 * 12단계 docx 의 `DocxWalker` 와 같은 약속이다 — 훑기는 쓰는 곳([sink])이 없는 걷기이고, 두 모드가 **같은 레코드를 같은
 * 차례로** 소비한다. 보이지 않게 할 것(겹쳐 가려진 칸)은 건너뛰지 않고 쓰기만 끈다([suppress]). 그래야 조각의 시작에서
 * 번호 문단·각주 번호가 어긋나지 않는다.
 *
 * ## 트리의 모양
 *
 * 문단은 `PARA_HEADER`(수준 L) 아래 수준 L+1 에 글자(`PARA_TEXT`)·글자 모양(`PARA_CHAR_SHAPE`)·컨트롤(`CTRL_HEADER`)을
 * 둔다. **확장 컨트롤 글자와 `CTRL_HEADER` 는 차례로 짝이 맞는다** — 실물 17개의 문단 46,961개 전부가 그랬다(13단계 조사).
 * 표 칸·글상자·각주의 문단 목록은 `LIST_HEADER` **다음에 같은 수준으로** 이어진다(자식이 아니라 형제 — pyhwp·hwplib 이
 * 같이 읽고 실물이 그렇다). 목록의 끝은 문단 머리 글자 수의 비트 31 이다.
 *
 * ## HTML 의 모양
 *
 * 문단은 `p`(개요 문단은 `h1`..`h6`), 번호·글머리표는 **계산해 적은 표지**(`span.mk`)다. 표·글상자·캡션은 문단 안의
 * 컨트롤이지만 `p` 안에 둘 수 없어 **문단을 거기서 끊고** 블록으로 쓴 뒤 문단을 이어 연다. 그림·수식·각주 표지는 글자처럼
 * 문단 안에 둔다. 표는 칸의 주소(행·열·병합)가 레코드에 적혀 있어 **칸을 행 차례로 다시 모아** 그린다(적힌 차례를 믿지
 * 않는다) — 훑기도 같은 차례로 걷는다.
 */
internal class Hwp5Walker(
    private val env: Hwp5Env,
    private val sink: HtmlWriter?,
    private val state: WalkState,
    private val cfg: WalkConfig,
) {
    private var data: ByteArray = ByteArray(0)

    /** 그리는 동안 만난 각주·미주 참조. 조각의 끝에 그 본문을 모아 그린다. */
    val noteRefs = ArrayList<NoteRef>()

    /** 이 걷기(부분 하나)에서 센 각주·미주의 차례 — 링크의 `id`([NoteRef.seq]). */
    private var footnoteSeq = 0
    private var endnoteSeq = 0

    /** 주석 본문을 그리는 중이면 그 주석 — 첫 문단을 열 때(또는 [markAtAutoNumber] 면 주석의 자동 번호 자리에) 번호를 적는다. */
    var pendingNoteMark: NoteRef? = null

    /**
     * 주석 본문의 첫 문단에 주석 자신의 자동 번호(`atno`, 종류 1·2)가 있다 — 번호를 문단 머리가 아니라 **그 자리**에 적는다.
     * 한글은 주석 번호를 그 자리에 그린다. 머리에 적으면 `주 1) …` 가 `1)주 …` 가 된다(13단계 검토, 표본 K09 와 바로보기).
     */
    private var markAtAutoNumber = false

    private var suppress = 0
    private var para: ParaCtx? = null
    private var currentTop = -1
    private var tableDepth = 0
    private var textBoxDepth = 0
    private var nesting = 0
    private var emptyRun = 0
    private var ticks = 0

    /**
     * 지금 그리는 곳의 **알려진 바탕색**(칸의 면 색·글상자의 채우기). -1 이면 모른다(화면의 흰 바탕으로 본다). 흰 글자를 적을지
     * 가를 때 쓴다([RunStyle.shownColor]).
     */
    private var background = -1

    private val lightBackground: Boolean get() = background < 0 || RunStyle.isLight(background)

    /**
     * 흰 면 색을 적지 않아도 되는가 — **둘러싼 바탕이 흰 화면일 때만** 그렇다. 어두운 칸 안의 흰 칸에서 흰색을 빼면 안쪽
     * 칸이 투명해져 검은 글자가 남색 위에 놓였다(검토가 잡았다). HWPX 변환기와 같은 규칙이다.
     */
    private fun redundantWhite(fill: Int): Boolean = fill == 0xFFFFFF && (background < 0 || background == 0xFFFFFF)

    private val writing: Boolean get() = sink != null && suppress == 0

    /** 문단 안의 열린 필드 하나(시작 글자 3 … 끝 글자 4). [tag] 는 이 필드가 연 태그(`a`·`span`). */
    private class FieldFrame(var tag: String?, val gen: Int)

    // ---- 입구 ----------------------------------------------------------------------------------

    /**
     * 구역 하나를 [from] 자리부터 걷는다. 구역의 본문은 **비트 31 이 켜진 최상위 문단**에서 끝난다 — 그 뒤에 오는 수준 0 의
     * 레코드(확장 바탕쪽·메모, hwplib `ForSection`)는 본문이 아니다. 조각의 끝([WalkConfig.endOrdinal])에 닿으면 멈춘다.
     */
    fun section(bytes: ByteArray, from: Int) {
        data = bytes
        val cur = RecordCursor(bytes, from)
        var ordinal = cfg.startOrdinal
        var ended = false
        try {
            while (cur.peek()) {
                if (cur.level != 0 || cur.tag != HwpTag.PARA_HEADER || ended) {
                    cur.skipWithChildren()
                    continue
                }
                if (ordinal >= cfg.endOrdinal) break
                val top = Top(ordinal, cfg.globalBase + ordinal, cur.pos)
                if (paragraph(cur, 0, Where.BODY, top)) ended = true
                ordinal++
            }
        } catch (_: StopWalk) {
            // 쓰기 상한 — 부르는 쪽이 `sink.full` 을 보고 알린다.
        }
    }

    /** 각주·미주 하나의 본문(주석 모드). 쓰기 상한에 닿으면 [StopWalk] 를 던진다. */
    fun noteBody(bytes: ByteArray, ref: NoteRef?) {
        data = bytes
        val cur = RecordCursor(bytes, ref?.ctrlPos ?: return)
        if (!cur.peek() || cur.tag != HwpTag.CTRL_HEADER) return
        val list = firstList(cur) ?: return
        pendingNoteMark = ref
        markAtAutoNumber = hasOwnNumber(list)
        walkList(list, Where.NOTE)
        // 자동 번호가 있다고 보았는데 그 자리에 닿지 못했으면(깨진 짝) 번호를 잃지 않게 끝에 적는다.
        if (pendingNoteMark != null && markAtAutoNumber) {
            markAtAutoNumber = false
            val pc = ParaCtx("p", null, null, null, null, env.defaultRun)
            ensureOpenIn(pc)
            end(pc.tag)
        }
        pendingNoteMark = null
        markAtAutoNumber = false
    }

    /** 목록의 첫 문단에 주석 번호(`atno` 의 종류 1·2) 컨트롤이 있는가. 첫 문단의 자식만 본다. */
    private fun hasOwnNumber(list: ListRef): Boolean {
        if (list.count == 0) return false
        val cur = RecordCursor(data, list.first)
        if (!cur.peek() || cur.tag != HwpTag.PARA_HEADER) return false
        val lvl = cur.level
        cur.advance()
        while (cur.peek() && cur.level > lvl) {
            if (cur.level == lvl + 1 && cur.tag == HwpTag.CTRL_HEADER && cur.size >= 8 &&
                data.i32(cur.payload) == CtrlId.AUTO_NUMBER
            ) {
                val kind = (data.u32(cur.payload + 4) and 0xF).toInt()
                if (kind == NUMBER_FOOTNOTE || kind == NUMBER_ENDNOTE) return true
            }
            cur.advance()
        }
        return false
    }

    /** 훑기에서 주석 본문의 버린 것을 센다(번호·상태와 관계없다). */
    private fun scanNote(ctrlPos: Int) {
        val walker = Hwp5Walker(
            env,
            sink = null,
            state = WalkState(section = state.section),
            cfg = WalkConfig(recordFeatures = true, notes = true, checkCancel = cfg.checkCancel),
        )
        walker.data = data
        val cur = RecordCursor(data, ctrlPos)
        if (!cur.peek()) return
        val list = walker.firstList(cur) ?: return
        walker.walkList(list, Where.NOTE)
    }

    // ---- 쓰기 -----------------------------------------------------------------------------------

    private fun start(tag: String, vararg attrs: Pair<String, String?>) {
        if (!writing) return
        val w = sink!!
        // 상한에 닿은 뒤의 여는 태그는 `HtmlWriter` 가 쓰지 않고 열린 것으로만 센다. 짝 맞추는 닫는 태그가 쌓이지 않게 멈춘다.
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

    private fun record(kind: String) {
        if (cfg.recordFeatures) env.features.record(kind)
    }

    private fun tick() {
        if (++ticks and 63 == 0) cfg.checkCancel()
    }

    // ---- 문단 목록 --------------------------------------------------------------------------------

    /** `LIST_HEADER` 하나와 그 뒤의 문단들. [header] 는 목록 머리의 알맹이 자리. */
    private class ListRef(val first: Int, val count: Int, val level: Int, val header: Int, val headerSize: Int) {
        /** 목록 머리의 속성(자리 4 — 명세의 6바이트 모양이 틀렸다, 실물은 8바이트 앞머리). */
        fun flags(data: ByteArray): Long = if (headerSize >= 8) data.u32(header + 4) else 0L
    }

    /** [cur] 가 가리키는 `LIST_HEADER` 와 그 뒤의 문단들을 소비해 목록을 만든다. */
    private fun readList(cur: RecordCursor): ListRef {
        val lvl = cur.level
        val header = cur.payload
        val headerSize = cur.size
        cur.advance()
        cur.skipSubtree(lvl)
        val first = cur.pos
        var count = 0
        while (count < Hwp5Limits.MAX_LIST_PARAGRAPHS && cur.peek() && cur.tag == HwpTag.PARA_HEADER && cur.level == lvl) {
            val last = cur.size >= 4 && (data.i32(cur.payload) ushr 31) == 1
            cur.skipWithChildren()
            count++
            if (last) break
        }
        return ListRef(first, count, lvl, header, headerSize)
    }

    /** 컨트롤([cur] 는 그 `CTRL_HEADER`) 아래의 첫 문단 목록. */
    private fun firstList(cur: RecordCursor): ListRef? {
        val lc = cur.level
        cur.advance()
        while (cur.peek() && cur.level > lc) {
            if (cur.level == lc + 1 && cur.tag == HwpTag.LIST_HEADER) return readList(cur)
            cur.skipWithChildren()
        }
        return null
    }

    private fun walkList(list: ListRef, where: Where) {
        if (nesting >= Hwp5Limits.MAX_NESTING) {
            cfg.onTruncated()
            return
        }
        nesting++
        val savedPara = para
        para = null
        try {
            val cur = RecordCursor(data, list.first)
            for (k in 0 until list.count) {
                if (!cur.peek() || cur.tag != HwpTag.PARA_HEADER || cur.level != list.level) break
                paragraph(cur, list.level, where, null)
            }
        } finally {
            para = savedPara
            nesting--
        }
    }

    // ---- 문단 -----------------------------------------------------------------------------------

    /**
     * 문단 하나([cur] 는 그 `PARA_HEADER`). 끝나면 [cur] 는 문단의 자식 트리 뒤에 있다. 비트 31(목록의 마지막)을 돌려준다.
     */
    private fun paragraph(cur: RecordCursor, level: Int, where: Where, top: Top?): Boolean {
        if (sink?.full == true) throw StopWalk
        tick()
        val hp = cur.payload
        val hs = cur.size
        val rawChars = if (hs >= 4) data.u32(hp) else 0L
        val last = (rawChars ushr 31) == 1L
        val nChars = (rawChars and 0x7FFFFFFF).toInt()
        val paraShapeId = if (hs >= 10) data.u16(hp + 8) else 0
        val instanceId = if (hs >= 22) data.u32(hp + 18) else 0L
        cur.advance()

        // 자식 모으기 — 글자·글자 모양은 자리만, 컨트롤은 머리의 자리를 차례로.
        var textOff = -1
        var textLen = 0
        var csOff = -1
        var csLen = 0
        var ctrls = IntArray(4)
        var nCtrls = 0
        // 스트림이 이 문단의 자식 트리 안에서 깨졌으면 **모은 데까지는 그리고** 그 뒤에 알린다 — 깨진 자리 바로 앞의 문단까지
        // 잃지 않게. 깨진 뒤의 컨트롤 자리는 모으지 않았으므로 그 컨트롤만 빠진다.
        var broken: HwpFormatException? = null
        try {
            while (cur.peek() && cur.level > level) {
                if (cur.level == level + 1) {
                    when (cur.tag) {
                        HwpTag.PARA_TEXT -> if (textOff < 0) {
                            textOff = cur.payload
                            textLen = cur.size
                        }
                        HwpTag.PARA_CHAR_SHAPE -> if (csOff < 0) {
                            csOff = cur.payload
                            csLen = cur.size
                        }
                        HwpTag.CTRL_HEADER -> if (nCtrls < Hwp5Limits.MAX_CTRLS_PER_PARAGRAPH) {
                            if (nCtrls == ctrls.size) ctrls = ctrls.copyOf(ctrls.size * 2)
                            ctrls[nCtrls++] = cur.pos
                        }
                    }
                }
                cur.advance()
            }
        } catch (e: HwpFormatException) {
            broken = e
        }

        val ps = env.info.paraShape(paraShapeId) ?: ParaShape.DEFAULT
        val outline = outlineLevel(ps)
        val heading = where == Where.BODY && top != null && outline != null
        val savedTop = currentTop
        if (top != null) {
            currentTop = top.global
            cfg.scan?.onTopBlock(cfg.section, top.ordinal, top.offset, top.global, heading, state)
            cfg.scan?.instance(instanceId, top.global)
        }

        // 글자 모양 구간. 첫 구간의 모양이 문단의 바탕이다.
        val nRuns = if (csOff >= 0) minOf(csLen / 8, Hwp5Limits.MAX_CHAR_RUNS) else 0
        val runPos = IntArray(nRuns)
        val runId = IntArray(nRuns)
        for (k in 0 until nRuns) {
            runPos[k] = data.u32(csOff + 8 * k).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            runId[k] = data.u32(csOff + 8 * k + 4).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
        val base = if (nRuns > 0) env.run(runId[0]) else env.defaultRun

        val marker = marker(ps)
        val pc = beginParagraph(ps, base, outline, heading, marker, top)
        val savedPara = para
        para = pc
        try {
            if (textOff >= 0) renderText(textOff, minOf(textLen / 2, nChars), runPos, runId, ctrls, nCtrls, base)
            finishParagraph(pc)
        } finally {
            para = savedPara
            currentTop = savedTop
        }
        if (broken != null) throw broken
        return last
    }

    /**
     * 개요 수준(0부터) — 문단 모양의 머리 모양이 '개요'(1)일 때만 그 수준, 아니면 null. HWPX 가 `headingType="OUTLINE"` 만
     * 보는 것과 같은 규칙이다. 처음에는 스타일 이름이 `개요 N` 인 문단도 제목으로 보았는데 HWPX 에는 그 규칙이 없었다(13단계
     * 짝 대조). 명세 S01·S02 에서 `개요 N` 스타일의 문단은 전부 머리 모양도 개요였다 — 이름 규칙이 더하는 것이 없다.
     */
    private fun outlineLevel(ps: ParaShape): Int? = if (ps.headType == HEAD_OUTLINE) ps.headLevel else null

    /** 문단 머리의 표지 — 개요·번호는 셈으로, 글머리표는 글자로. 표지가 없으면 null. */
    private fun marker(ps: ParaShape): String? = when (ps.headType) {
        HEAD_OUTLINE -> numbered(state.section.outlineNumbering, ps.headLevel)
        2 -> numbered(ps.numberingId, ps.headLevel)
        3 -> env.info.bullet(ps.numberingId)?.let { BulletGlyphs.map(it.char) }
        else -> null
    }

    private fun numbered(id1: Int, level: Int): String? {
        val def = env.info.numbering(id1) ?: return null
        val lvl = level.coerceIn(0, Hwp5Limits.NUMBERING_LEVELS - 1)
        val format = def.levels[lvl]?.format ?: return null
        val values = state.numbers.next(id1, lvl, def.starts)
        return HancomNumbers.fill(format, lvl, values, def.shapes).takeIf { it.isNotEmpty() }
    }

    /**
     * 문단 요소의 모양을 정한다. 여는 것은 처음 쓸 것이 생길 때다([ensureOpen]) — 제목(목차가 가리킨다)·번호 문단(표지가
     * 보여야 한다)·링크 대상은 비어 있어도 연다.
     */
    private fun beginParagraph(ps: ParaShape, base: RunStyle, outline: Int?, heading: Boolean, marker: String?, top: Top?): ParaCtx {
        val tag = when {
            !heading || outline == null -> "p"
            outline <= 5 -> "h${outline + 1}"
            else -> "p"
        }
        val cls = if (heading && outline != null && outline > 5) "h7" else null
        val style = StyleBuilder()
        style.add("text-align", align(ps.align))
        // 들여쓰기(음수면 내어쓰기): 한글의 내어쓰기는 첫 줄이 왼쪽 여백에서, 나머지 줄이 그만큼 들어간다.
        val left = ps.left / UNITS_PER_PT
        val indent = ps.indent / UNITS_PER_PT
        val marginLeft = if (indent < 0) left - indent else left
        // 음수 여백(쪽 여백 안으로 내민 문단)은 흐름에서 0 이다 — `0pt` 를 적지 않는다. 처음에는 0 으로 누른 값을 적어 같은
        // 보도자료가 HWPX(0 보다 클 때만 적는다)와 문단 모양이 달랐다(13단계 짝 대조, K19/K27 의 13곳).
        if (marginLeft > 0.0) style.add("margin-left", CssValues.pt(marginLeft, 0.0, Hwp5Limits.MAX_INDENT_PT))
        if (ps.right > 0) style.add("margin-right", CssValues.pt(ps.right / UNITS_PER_PT, 0.0, Hwp5Limits.MAX_INDENT_PT))
        if (indent != 0.0) style.add("text-indent", CssValues.pt(indent, -Hwp5Limits.MAX_INDENT_PT, Hwp5Limits.MAX_INDENT_PT))
        if (ps.spaceBefore > 0) style.add("margin-top", CssValues.pt(ps.spaceBefore / UNITS_PER_PT, 0.0, Hwp5Limits.MAX_SPACING_PT))
        if (ps.spaceAfter > 0) style.add("margin-bottom", CssValues.pt(ps.spaceAfter / UNITS_PER_PT, 0.0, Hwp5Limits.MAX_SPACING_PT))
        // 줄 간격은 **단위 없는 수**로 — 백분율은 문단에서 길이로 풀린 뒤 자식에게 그 길이로 물려져, 문단보다 큰 글자에 좁은
        // 줄을 준다(13단계 짝 대조: K19 의 14pt '’24년 공공부문 일자리는 287.5만 개' 가 19.2pt 줄에 눌렸다). HWPX 와 같은 모양이다.
        ps.lineSpacingPercent?.takeIf { it != DEFAULT_LINE_SPACING }?.let {
            style.add("line-height", CssValues.number(it / 100.0, 0.5, 5.0))
        }
        base.paragraphCss(env.defaultRun, heading, style, lightBackground)

        val anchor = top != null && cfg.layout?.anchorBlocks?.contains(top.global) == true
        val pc = ParaCtx(
            tag = tag,
            id = if (top != null && (heading || anchor)) Hwp5Layout.anchorId(top.global) else null,
            cls = cls,
            style = style.build(),
            marker = marker,
            base = base,
        )
        if (heading && top != null && cfg.scan != null) {
            pc.headingText = StringBuilder().also { if (marker != null) it.append(marker).append(' ') }
            pc.headingLevel = outline ?: 0
            pc.headingGlobal = top.global
        }
        if (pc.id != null || marker != null) ensureOpenIn(pc)
        return pc
    }

    /** 문단을 연다. 이어 여는 것(블록 뒤)은 번호·`id` 없이 연다. */
    private fun ensureOpenIn(pc: ParaCtx) {
        if (pc.opened) return
        pc.opened = true
        pc.gen++
        if (pc.continuation) {
            start(pc.tag, "class" to pc.cls, "style" to pc.style)
            return
        }
        start(pc.tag, "id" to pc.id, "class" to pc.cls, "style" to pc.style)
        if (!markAtAutoNumber) writeNoteMark(pc)
        if (pc.marker != null) {
            start("span", "class" to "mk")
            text(pc.marker)
            end("span")
            pc.visible = true
        }
    }

    /** 주석 본문의 번호(본문의 표지로 돌아가는 링크). 한 번만 적는다. */
    private fun writeNoteMark(pc: ParaCtx) {
        val ref = pendingNoteMark ?: return
        pendingNoteMark = null
        start("sup", "class" to "fnnum")
        // 자동 번호가 하이퍼링크 필드 안에 있으면 링크를 겹쳐 열지 않는다(`a` 안의 `a` 는 바깥 링크를 끊는다).
        if (pc.linkDepth == 0) start("a", "href" to "#" + ref.backId)
        text(ref.label)
        end("sup")
        pc.visible = true
    }

    private fun ensureOpen(): ParaCtx? {
        val pc = para ?: return null
        ensureOpenIn(pc)
        return pc
    }

    /**
     * 문단을 닫는다. 빈 문단은 줄 하나(`br`)로 남긴다 — 한글 문서는 빈 문단으로 간격을 띄운다. 잇달아 [Hwp5Limits.MAX_EMPTY_RUN]
     * 개를 넘는 빈 문단은 버린다.
     */
    private fun finishParagraph(pc: ParaCtx) {
        pc.headingText?.let { t -> cfg.scan?.heading(pc.headingGlobal, pc.headingLevel, t.toString()) }
        if (!pc.opened) {
            if (pc.continuation) return
            if (emptyRun >= Hwp5Limits.MAX_EMPTY_RUN) {
                emptyRun++
                return
            }
            ensureOpenIn(pc)
        }
        if (!pc.visible) {
            void("br")
            emptyRun++
        } else {
            emptyRun = 0
        }
        end(pc.tag)
        pc.opened = false
    }

    /** 블록(표·글상자·캡션)을 쓰려고 문단을 끊는다. 뒤의 글은 [ensureOpenIn] 이 이어 연다. */
    private fun breakParagraph() {
        val pc = para ?: return
        if (pc.opened) {
            end(pc.tag)
            pc.opened = false
            if (pc.visible) emptyRun = 0
        }
        pc.continuation = true
        pc.linkDepth = 0
        // 끝 태그를 쓰면서 열린 링크도 함께 닫혔다(`HtmlWriter.end` 는 안쪽까지 닫는다).
        for (f in pc.fields) f.tag = null
    }

    // ---- 글자 -----------------------------------------------------------------------------------

    /**
     * 글자 흐름(`PARA_TEXT`)을 걷는다 [SPEC 표 6]. 0–31 은 제어 글자다 — **글자 컨트롤**(1칸: 0·10·13·24–31)과
     * **인라인·확장 컨트롤**(8칸: 코드, 12바이트, 같은 코드). 확장 컨트롤의 12바이트 앞 4바이트가 컨트롤 ID 이고, 그것과
     * 차례가 맞는 `CTRL_HEADER` 가 내용을 든다. 8칸 컨트롤이 글자 수를 넘어 걸치면 거기서 끝낸다.
     */
    private fun renderText(off: Int, n: Int, runPos: IntArray, runId: IntArray, ctrls: IntArray, nCtrls: Int, base: RunStyle) {
        var r = 0
        var rs = base
        var nextCtrl = 0
        val buf = StringBuilder()
        var i = 0
        while (i < n) {
            if (r + 1 < runPos.size && runPos[r + 1] <= i) {
                if (buf.isNotEmpty()) emit(buf, rs)
                while (r + 1 < runPos.size && runPos[r + 1] <= i) r++
                rs = env.run(runId[r])
            }
            val c = data.u16(off + 2 * i)
            if (c >= 32) {
                // 대리 쌍은 한 글자로 읽는다 — 한컴의 사설 영역 글자가 보조 평면에 있다([HancomChars]).
                val low = if (c in 0xD800..0xDBFF && i + 1 < n) data.u16(off + 2 * i + 2) else 0
                if (low in 0xDC00..0xDFFF) {
                    val cp = 0x10000 + ((c - 0xD800) shl 10) + (low - 0xDC00)
                    val mapped = HancomChars.map(cp)
                    if (mapped != null) buf.append(mapped) else buf.append(c.toChar()).append(low.toChar())
                    i += 2
                    continue
                }
                val mapped = HancomChars.map(c)
                if (mapped != null) buf.append(mapped) else buf.append(c.toChar())
                i++
                continue
            }
            when (c) {
                PARA_BREAK -> break
                LINE_BREAK -> {
                    if (buf.isNotEmpty()) emit(buf, rs)
                    lineBreak()
                    i++
                }
                HYPHEN -> {
                    buf.append('-')
                    i++
                }
                NBSP, FIXED_SPACE -> {
                    buf.append('\u00A0')
                    i++
                }
                else -> {
                    if (c !in EIGHT_UNIT) {
                        i++ // 쓰지 않는 글자 컨트롤(0·25–29)
                        continue
                    }
                    if (i + 8 > n) break
                    when (c) {
                        TAB -> buf.append('\t')
                        FIELD_END -> {
                            if (buf.isNotEmpty()) emit(buf, rs)
                            fieldEnd()
                        }
                        in EXTENDED -> {
                            if (buf.isNotEmpty()) emit(buf, rs)
                            val id = data.i32(off + 2 * i + 2)
                            // 차례로 짝짓되 ID 를 확인한다. 어긋나면 같은 ID 를 가진 다음 것을 찾는다(없으면 이 컨트롤만 버린다).
                            var k = nextCtrl
                            // 찾는 폭을 묶는다 — 모든 ID 가 어긋나게 만든 문단이 컨트롤 수의 제곱만큼 돌지 않게.
                            val limit = minOf(nCtrls, nextCtrl + MAX_CTRL_SEARCH)
                            while (k < limit && headerId(ctrls[k]) != id) k++
                            if (k == limit) k = nCtrls
                            if (k < nCtrls) {
                                nextCtrl = k + 1
                                control(c, id, ctrls[k], rs)
                            } else {
                                record(UnsupportedFeatures.UNKNOWN_ELEMENT)
                            }
                        }
                        // 나머지 인라인 컨트롤(제목 차례 표시 등)은 그릴 것이 없다.
                    }
                    i += 8
                }
            }
        }
        if (buf.isNotEmpty()) emit(buf, rs)
    }

    private fun headerId(pos: Int): Int {
        val cur = RecordCursor(data, pos)
        return if (cur.peek() && cur.size >= 4) data.i32(cur.payload) else 0
    }

    /** 글자 한 덩이. 문단 바탕과 다른 모양만 `span` 에 적는다. 적고 나면 [buf] 를 비운다. */
    private fun emit(buf: StringBuilder, rs: RunStyle) {
        val s = buf.toString()
        buf.setLength(0)
        emitText(s, rs)
    }

    private fun emitText(s: String, rs: RunStyle) {
        if (s.isEmpty()) return
        val pc = para ?: return
        cfg.scan?.addChars(s.length)
        pc.headingText?.let { if (it.length < Hwp5Limits.MAX_HEADING_CHARS) it.append(s, 0, minOf(s.length, Hwp5Limits.MAX_HEADING_CHARS - it.length)) }
        if (rs.effect && !pc.effectRecorded) {
            pc.effectRecorded = true
            record(UnsupportedFeatures.TEXT_EFFECT)
        }
        ensureOpenIn(pc)
        pc.visible = true
        if (!writing) return
        val style = rs.css(pc.base, lightBackground)
        val wrap = rs.wrap
        if (style != null) start("span", "style" to style)
        if (wrap != null) start(wrap)
        text(s)
        if (wrap != null) end(wrap)
        if (style != null) end("span")
    }

    private fun lineBreak() {
        val pc = ensureOpen() ?: return
        pc.visible = true
        pc.headingText?.append(' ')
        void("br")
    }

    // ---- 컨트롤 ---------------------------------------------------------------------------------

    private fun control(code: Int, id: Int, pos: Int, rs: RunStyle) {
        when (id) {
            CtrlId.SECD -> sectionDef(pos)
            CtrlId.TABLE -> table(pos)
            CtrlId.GSO -> gso(pos)
            CtrlId.EQUATION -> equation(pos)
            CtrlId.FOOTNOTE -> note(pos, endnote = false)
            CtrlId.ENDNOTE -> note(pos, endnote = true)
            CtrlId.HEADER, CtrlId.FOOTER -> if (cfg.recordFeatures && !env.headerFooterRecorded) {
                env.headerFooterRecorded = true
                env.features.record(UnsupportedFeatures.HEADER_FOOTER)
            }
            CtrlId.AUTO_NUMBER -> autoNumber(pos, rs)
            CtrlId.NEW_NUMBER -> newNumber(pos)
            CtrlId.BOOKMARK -> bookmark(pos)
            CtrlId.OVERLAP -> overlap(pos, rs)
            CtrlId.RUBY -> ruby(pos)
            CtrlId.HIDDEN_COMMENT -> record(UnsupportedFeatures.COMMENT)
            CtrlId.FORM -> record(UnsupportedFeatures.FORM)
            // 쪽 모양만 바꾸는 것들 — 흐름 렌더에는 뜻이 없다(쪽 재현을 포기했다).
            CtrlId.COLD, CtrlId.PAGE_HIDE, CtrlId.PAGE_ODD_EVEN, CtrlId.PAGE_NUMBER_POS,
            CtrlId.INDEX_MARK -> Unit
            else -> if (code == FIELD_START || CtrlId.isField(id)) fieldStart(id, pos) else record(UnsupportedFeatures.UNKNOWN_ELEMENT)
        }
    }

    /** 구역 정의 — 개요 번호 정의(자리 18)와 각주·미주 모양(자식 `FOOTNOTE_SHAPE` 둘, 각주가 먼저). */
    private fun sectionDef(pos: Int) {
        val cur = RecordCursor(data, pos)
        if (!cur.peek()) return
        val lc = cur.level
        val p = cur.payload
        val outline = if (cur.size >= 20) data.u16(p + 18) else 0
        cur.advance()
        var foot: NoteShape? = null
        var end: NoteShape? = null
        while (cur.peek() && cur.level > lc) {
            if (cur.level == lc + 1 && cur.tag == HwpTag.FOOTNOTE_SHAPE) {
                val shape = NoteShape.parse(data, cur.payload, cur.size)
                if (foot == null) foot = shape else if (end == null) end = shape
            }
            cur.advance()
        }
        val prev = state.section
        val settings = SectionSettings(
            outlineNumbering = if (outline > 0) outline else prev.outlineNumbering,
            foot = foot ?: prev.foot,
            end = end ?: prev.end,
        )
        state.section = settings
        if (settings.foot.numbering == 1) state.footnoteNo = 0
        if (settings.end.numbering == 1) state.endnoteNo = 0
    }

    // ---- 표 ------------------------------------------------------------------------------------

    private class Cell(
        val row: Int,
        val col: Int,
        val rowSpan: Int,
        val colSpan: Int,
        val fill: Int,
        val noBorder: Boolean,
        val vAlign: Int,
        val list: ListRef,
    )

    /**
     * 표(`tbl `). 자식은 [캡션 목록] → `TABLE` → 칸마다 `LIST_HEADER` + 문단들이다. 칸의 주소(열·행·병합)는 목록 머리의 자리
     * 8 부터 적혀 있다(표 80, hwplib `ForCell`). 가려진 칸은 **아예 적지 않는다**(실물의 3×3 표가 칸 7개였다).
     */
    private fun table(pos: Int) {
        val cur = RecordCursor(data, pos)
        if (!cur.peek()) return
        val lc = cur.level
        registerInstance(cur)
        if (tableDepth >= Hwp5Limits.MAX_TABLE_DEPTH || nesting >= Hwp5Limits.MAX_NESTING) {
            cfg.onTruncated()
            return
        }
        cur.advance()
        var caption: ListRef? = null
        var rows = 0
        var cols = 0
        var sawTable = false
        var truncated = false
        val cells = ArrayList<Cell>()
        while (cur.peek() && cur.level > lc) {
            if (cur.level != lc + 1) {
                cur.advance()
                continue
            }
            when (cur.tag) {
                HwpTag.TABLE -> {
                    if (cur.size >= 8) {
                        rows = data.u16(cur.payload + 4)
                        cols = data.u16(cur.payload + 6)
                    }
                    sawTable = true
                    cur.advance()
                }
                HwpTag.LIST_HEADER -> if (!sawTable) {
                    val list = readList(cur)
                    if (caption == null) caption = list
                } else {
                    val p = cur.payload
                    val s = cur.size
                    val list = readList(cur)
                    if (cells.size >= Hwp5Limits.MAX_CELLS) {
                        truncated = true
                    } else {
                        cells.add(
                            Cell(
                                row = if (s >= 12) data.u16(p + 10) else 0,
                                col = if (s >= 10) data.u16(p + 8) else cells.size,
                                rowSpan = if (s >= 16) data.u16(p + 14) else 1,
                                colSpan = if (s >= 14) data.u16(p + 12) else 1,
                                fill = if (s >= 34) env.info.fillColor(data.u16(p + 32)) else -1,
                                noBorder = s >= 34 && env.info.noBorder(data.u16(p + 32)),
                                vAlign = ((list.flags(data) ushr 5) and 3L).toInt(),
                                list = list,
                            )
                        )
                    }
                }
                else -> cur.skipWithChildren()
            }
        }
        if (truncated) cfg.onTruncated()
        // 칸도 조각의 무게로 센다. 공문서는 본문 대부분이 표라(실물 K01 은 문단 16,000개 가운데 15,400개가 칸 안이다)
        // 최상위 문단만 세면 표 수백 개가 한 조각이 되고, WebView 는 칸 수에 비례해 배치가 느리다(12단계 실측).
        cfg.scan?.addBlocks(cells.size)
        val maxRow = cells.maxOfOrNull { it.row + 1 } ?: 0
        val maxCol = cells.maxOfOrNull { it.col + 1 } ?: 0
        var nRows = if (rows > 0) rows else maxRow
        val nCols = (if (cols > 0) cols else maxCol).coerceAtMost(Hwp5Limits.MAX_COLS)
        if (nRows > Hwp5Limits.MAX_ROWS) {
            nRows = Hwp5Limits.MAX_ROWS
            cfg.onTruncated()
        }
        cells.sortWith(compareBy<Cell>({ it.row }, { it.col }))
        val grid = overlapGrid(cells, nRows, nCols)

        breakParagraph()
        emptyRun = 0
        val capTop = caption != null && captionOnTop(caption)
        if (capTop) renderCaption(caption)
        start("table")
        tableDepth++
        var idx = 0
        var r = 0
        while (r < nRows) {
            if (sink?.full == true) throw StopWalk
            tick()
            if (!writing) {
                // 쓰지 않는 걷기(훑기·가려진 칸 안)는 칸이 있는 행만 본다. 행 수는 문서가 적는 값이라, 칸 없는 표 수십만 개가
                // 저마다 5,000행을 돌게 할 수 있다(13단계 검토 — 쓰는 걷기는 쓴 양이 `HtmlWriter` 의 상한에 묶인다).
                if (idx >= cells.size || cells[idx].row >= nRows) break
                r = maxOf(r, cells[idx].row)
            }
            start("tr")
            while (idx < cells.size && cells[idx].row == r) {
                cell(cells[idx], nRows, nCols, grid)
                idx++
            }
            end("tr")
            r++
        }
        // 격자 밖의 칸 — 보이지 않지만 훑기와 같은 차례로 걷는다.
        while (idx < cells.size) {
            suppress++
            try {
                walkList(cells[idx].list, Where.CELL)
            } finally {
                suppress--
            }
            idx++
        }
        tableDepth--
        end("table")
        // 표는 보이는 것이라 빈 문단의 이어짐을 끊는다. 마지막 칸의 빈 줄 수가 남으면 표 뒤의 빈 문단(간격)이 사라졌다.
        emptyRun = 0
        if (caption != null && !capTop) renderCaption(caption)
    }

    /** 겹침 검사의 격자 — [cols] 가 한 행의 폭이다. */
    private class Grid(val cols: Int, val bits: BitSet)

    /** 이 걷기가 지금까지 잡은 격자 칸의 합([overlapGrid]). 시험이 본다. */
    internal var gridSlots = 0L
        private set

    /**
     * 겹침 검사의 격자. 칸이 **둘 이상**일 때만, 칸들이 **실제로 덮는 넓이**만큼 잡는다. 표 머리의 행·열 수는 문서가 적는
     * 값이라 그것으로 잡으면 칸 없는 표 하나하나가 5,000×800 격자(0.5 MB)를 잡는다 — 13단계 검토에서 9 KB 파일의 표 2만 개가
     * 여는 데 0.9초였다. 걷기 하나가 잡는 합도 묶는다. 못 잡으면 검사를 건너뛴다(칸은 그대로 그린다 — 겹친 칸은 깨진 표에만 있다).
     */
    private fun overlapGrid(cells: List<Cell>, nRows: Int, nCols: Int): Grid? {
        if (cells.size < 2) return null
        var rows = 0
        var cols = 0
        for (c in cells) {
            if (c.row >= nRows || c.col >= nCols) continue
            rows = maxOf(rows, c.row + c.rowSpan.coerceIn(1, nRows - c.row))
            cols = maxOf(cols, c.col + c.colSpan.coerceIn(1, nCols - c.col))
        }
        val slots = rows.toLong() * cols
        if (slots <= 0 || slots > Hwp5Limits.MAX_GRID_SLOTS || gridSlots + slots > Hwp5Limits.MAX_GRID_SLOTS_PER_WALK) return null
        gridSlots += slots
        return Grid(cols, BitSet(slots.toInt()))
    }

    private fun cell(c: Cell, nRows: Int, nCols: Int, grid: Grid?) {
        var shown = c.col < nCols && c.row < nRows
        val colSpan = if (shown) c.colSpan.coerceIn(1, nCols - c.col) else 1
        val rowSpan = if (shown) c.rowSpan.coerceIn(1, nRows - c.row) else 1
        if (shown && grid != null) {
            // 이미 다른 칸이 덮은 자리에 걸치면 그리지 않는다(깨진 표). 자리는 그린 칸만 차지한다. 격자는 모든 칸의 넓이를 덮는다.
            val w = grid.cols
            loop@ for (rr in c.row until c.row + rowSpan) {
                for (cc in c.col until c.col + colSpan) {
                    if (grid.bits[rr * w + cc]) {
                        shown = false
                        break@loop
                    }
                }
            }
            if (shown) for (rr in c.row until c.row + rowSpan) grid.bits.set(rr * w + c.col, rr * w + c.col + colSpan)
        }
        if (!shown) {
            suppress++
            try {
                walkList(c.list, Where.CELL)
            } finally {
                suppress--
            }
            return
        }
        val style = StyleBuilder()
        if (c.fill >= 0 && !redundantWhite(c.fill)) style.add("background-color", RunStyle.hex(c.fill))
        // 바탕 스타일의 칸은 가운데 정렬이다(한글의 기본값 — `HancomCss.FLOW`). 위·아래인 칸만 적는다. 처음에는 이 모듈의
        // 바탕이 '위' 라 가운데인 칸마다 `middle` 을 적었고, HWPX 는 바탕이 가운데였다(13단계 짝 대조).
        style.add(
            "vertical-align",
            when (c.vAlign) {
                V_ALIGN_TOP -> "top"
                V_ALIGN_BOTTOM -> "bottom"
                else -> null
            },
        )
        start(
            "td",
            "colspan" to colSpan.takeIf { it > 1 }?.toString(),
            "rowspan" to rowSpan.takeIf { it > 1 }?.toString(),
            "class" to if (c.noBorder) "nb" else null,
            "style" to style.build(),
        )
        val savedBackground = background
        if (c.fill >= 0) background = c.fill
        // 빈 문단 세기는 칸마다 새로 — 빈 칸도 줄 하나를 남겨 행 높이를 지킨다. 표 전체로 세면 빈 칸이 셋을 넘게 이어진
        // 뒤로는 칸이 줄 없이 납작해진다(K19 에서 144칸). HWPX 변환기와 같다.
        emptyRun = 0
        try {
            walkList(c.list, Where.CELL)
        } finally {
            background = savedBackground
        }
        end("td")
    }

    /**
     * 한글이 개체마다 저절로 넣는 설명문인가 — 첫 줄이 개체 종류의 이름 + `입니다.`([AUTO_COMMENT_HEADS]·클립아트)이고
     * 뒤 줄이 전부 `열쇠: 값`(`원본 그림의 이름`·`원본 그림의 크기`, 사진이면 EXIF 줄)인 것. 대체 글로 쓰지 않는다: 화면 낭독기가 '원본 그림의 이름
     * CLP000043080017.bmp' 를 읽고, 그림이 깨지면 그 글이 화면에 뜬다. HWPX 변환기는 설명문을 쓰지 않으므로 이것으로 두
     * 변환기의 결과도 같아진다(13단계 짝 대조 — K19 의 그림 25개 중 20개, 표본 전체의 설명문 205개가 전부 이 틀이었다). 사람이 쓴 설명은 남긴다.
     * 모양으로 짐작한 규칙이다 — 한컴이 설명문의 틀을 적은 공개 자료는 찾지 못했다.
     */
    private fun isAutoComment(s: String): Boolean {
        val lines = s.trim().lines()
        val first = lines.first().trim()
        if (first.isEmpty()) return true
        if (first !in AUTO_COMMENT_HEADS && !CLIP_ART_HEAD.matches(first)) return false
        // 뒤 줄은 전부 `열쇠: 값` 이다 — `원본 그림의 이름`·`원본 그림의 크기`, 사진이면 `사진 찍은 날짜`·`프로그램 이름`·
        // `색 대표`·`EXIF 버전`(표본 205개). 사람이 이어 쓴 설명이 있으면 남긴다.
        return lines.drop(1).all { it.isBlank() || ':' in it }
    }

    /** 캡션의 자리(목록 머리 자리 8 의 속성 비트 0–1): 0 왼쪽, 1 오른쪽, 2 위, 3 아래. 왼쪽·위면 개체 앞에 둔다. */
    private fun captionOnTop(list: ListRef): Boolean {
        val attr = if (list.headerSize >= 12) data.u32(list.header + 8) else 3L
        val dir = (attr and 3L).toInt()
        return dir == 0 || dir == 2
    }

    private fun renderCaption(list: ListRef) {
        breakParagraph()
        start("div", "class" to "cap")
        walkList(list, Where.CAPTION)
        end("div")
    }

    private fun registerInstance(cur: RecordCursor) {
        // 개체 공통 속성(표 69)의 인스턴스 번호는 자리 36. 상호 참조(`%xrf`)가 표를 가리킬 때 쓴다.
        if (cfg.scan != null && cur.size >= 40) cfg.scan.instance(data.u32(cur.payload + 36), currentTop)
    }

    // ---- 그리기 개체 ------------------------------------------------------------------------------

    /**
     * 그리기 개체(`gso `). 개체 공통 속성 뒤에 [캡션 목록], 그다음 `SHAPE_COMPONENT` 하나(묶음이면 그 아래 여럿)다. 컨트롤 ID 가
     * 두 번 적힌다 — `CTRL_HEADER` 는 언제나 `gso `, 종류(`$pic`·`$rec`·`$con`…)는 `SHAPE_COMPONENT` 가 적는다(명세 rev 1.3 에
     * `gso ` 가 없다 — hwplib·rhwp 와 실물이 그렇다).
     */
    private fun gso(pos: Int) {
        val cur = RecordCursor(data, pos)
        if (!cur.peek()) return
        val lc = cur.level
        val p = cur.payload
        val size = cur.size
        registerInstance(cur)
        val widthHu = if (size >= 20) data.i32(p + 16) else 0
        val alt = (if (size >= 46) PayloadReader(data, p + 44, size - 44).let { r ->
            try {
                r.wstr(Hwp5Limits.MAX_ALT_CHARS)
            } catch (e: HwpFormatException) {
                ""
            }
        } else "").takeUnless(::isAutoComment).orEmpty()
        cur.advance()
        // 캡션 목록이 개체보다 **앞에** 적혀 있다. 위·왼쪽 캡션이면 개체 앞에, 아니면 뒤에 쓴다.
        var caption: ListRef? = null
        var captionDone = false
        var sawShape = false
        while (cur.peek() && cur.level > lc) {
            if (cur.level == lc + 1) {
                if (cur.tag == HwpTag.LIST_HEADER && !sawShape && caption == null) {
                    caption = readList(cur)
                    continue
                }
                if (cur.tag == HwpTag.SHAPE_COMPONENT && !sawShape) {
                    sawShape = true
                    if (caption != null && captionOnTop(caption)) {
                        renderCaption(caption)
                        captionDone = true
                    }
                    shape(cur, top = true, widthHu = widthHu, alt = alt, depth = 0)
                    continue
                }
            }
            cur.skipWithChildren()
        }
        if (caption != null && !captionDone) renderCaption(caption)
    }

    /**
     * `SHAPE_COMPONENT` 하나([cur]). 자식은 [글상자 목록], 모양 레코드(그림·사각형…), 묶음이면 자식 `SHAPE_COMPONENT` 들이다.
     * 맨 위 개체는 ID 를 두 번, 묶음 안의 개체는 한 번 적는다(hwplib `readInContainer`) — 그래서 뒤의 칸 자리가 다르다.
     */
    private fun shape(cur: RecordCursor, top: Boolean, widthHu: Int, alt: String, depth: Int) {
        val ls = cur.level
        val p = cur.payload
        val size = cur.size
        val kind = if (size >= 4) data.i32(p) else 0
        val base = if (top) 8 else 4
        // 지금 폭(표 83: x·y·묶음 수준·판·처음 폭·처음 높이 다음). 묶음 안의 그림은 이것으로 크기를 짐작한다.
        val currentWidth = if (size >= base + 24) data.i32(p + base + 20) else 0
        val width = if (top && widthHu > 0) widthHu else currentWidth
        val fill = if (CtrlId.isDrawing(kind)) shapeFill(p, size, base) else -1
        var hasTextBox = false
        cur.advance()
        while (cur.peek() && cur.level > ls) {
            if (cur.level != ls + 1) {
                cur.advance()
                continue
            }
            when (cur.tag) {
                HwpTag.SHAPE_COMPONENT -> if (depth + 1 >= Hwp5Limits.MAX_SHAPE_DEPTH) {
                    cfg.onTruncated()
                    cur.skipWithChildren()
                } else {
                    shape(cur, top = false, widthHu = 0, alt = alt, depth = depth + 1)
                }
                HwpTag.LIST_HEADER -> {
                    hasTextBox = true
                    textBox(readList(cur), fill)
                }
                HwpTag.SHAPE_COMPONENT_PICTURE -> {
                    picture(cur.payload, cur.size, width, alt)
                    cur.skipWithChildren()
                }
                HwpTag.SHAPE_COMPONENT_OLE, HwpTag.VIDEO_DATA -> {
                    record(UnsupportedFeatures.EMBEDDED_OBJECT)
                    cur.skipWithChildren()
                }
                HwpTag.CHART_DATA -> {
                    record(UnsupportedFeatures.CHART)
                    cur.skipWithChildren()
                }
                HwpTag.SHAPE_COMPONENT_TEXTART -> {
                    record(UnsupportedFeatures.TEXT_EFFECT)
                    cur.skipWithChildren()
                }
                else -> cur.skipWithChildren()
            }
        }
        // 선·사각형·타원 따위는 모양(선·채우기·자리)을 그리지 않는다. **글상자가 없는 것만** 버린 것으로 센다 — 글상자의 글은
        // 위에서 `div.tb` 로 그렸고 채우기도 칠했다. HWPX·docx 가 그렇게 센다. 처음에는 글상자가 있어도 세어 같은 보도자료의
        // '도형' 배지가 HWP 로만 컸다(13단계 짝 대조 — K01 은 8, 글상자 없는 것은 3).
        if (CtrlId.isDrawing(kind) && !hasTextBox) record(UnsupportedFeatures.SHAPE)
    }

    /**
     * 그림(`SHAPE_COMPONENT_PICTURE`, 표 107). 자리 71 의 16비트가 **바이너리 데이터의 차례**(1부터 — 저장소 번호가 아니다,
     * 실물 K05 는 저장소 번호가 45부터다)다. 그 레코드의 저장소 번호와 확장자로 `BinData/BIN%04X.확장자` 를 찾는다.
     *
     * **풀어서 자원 하나의 상한(32 MiB)을 넘는 그림도 그릴 수 없는 그림이다** — 바탕이 내주지 않아 `img` 를 쓰면 깨진 그림만
     * 뜨고 배지는 말이 없었다. HWPX 는 처음부터 그렇게 센다(13단계 짝 대조).
     */
    private fun picture(p: Int, size: Int, widthHu: Int, alt: String) {
        val id = if (size >= 73) data.u16(p + 71) else 0
        val item = env.info.binItem(id)
        if (item == null) {
            record(UnsupportedFeatures.UNSUPPORTED_IMAGE)
            return
        }
        if (item.isLink) {
            // 문서 밖의 파일을 가리킨다 — 따라가지 않는다.
            record(UnsupportedFeatures.LINKED_FILE)
            return
        }
        val name = item.streamName?.let { env.binName(it) }
        if (name == null || !env.isDisplayableImage(name) || !env.fitsResource(name, cfg.checkCancel)) {
            record(UnsupportedFeatures.UNSUPPORTED_IMAGE)
            return
        }
        if (suppress > 0) return
        val pc = ensureOpen() ?: return
        pc.visible = true
        val style = if (widthHu > 0) {
            StyleBuilder().add("width", CssValues.pt(widthHu / HWPUNIT_PER_PT, 1.0, Hwp5Limits.MAX_IMAGE_PT)).build()
        } else {
            null
        }
        void("img", "src" to FlowUrls.encode(name), "alt" to alt, "style" to style)
    }

    /** 글상자. 문단을 끊고 그 자리에 `div.tb` 로 쓴다. */
    private fun textBox(list: ListRef, fill: Int) {
        if (textBoxDepth >= Hwp5Limits.MAX_TEXTBOX_DEPTH || tableDepth >= Hwp5Limits.MAX_TABLE_DEPTH) {
            cfg.onTruncated()
            return
        }
        breakParagraph()
        val style = if (fill >= 0 && !redundantWhite(fill)) StyleBuilder().add("background-color", RunStyle.hex(fill)).build() else null
        start("div", "class" to "tb", "style" to style)
        textBoxDepth++
        val savedBackground = background
        if (fill >= 0) background = fill
        try {
            walkList(list, Where.TEXTBOX)
        } finally {
            textBoxDepth--
            background = savedBackground
        }
        end("div")
    }

    /**
     * 그리기 개체의 채우기 색(단색일 때). `SHAPE_COMPONENT` 의 표 83 칸 42바이트 뒤에 변환 행렬(개수 + 48 + 개수×96),
     * 테두리 선 정보(13, 표 87), 채우기 정보(표 28 — 테두리/배경의 것과 같다)가 온다. 실물의 사각형들에서 이 자리가 맞았다
     * (13단계). 모자라거나 단색이 아니면 -1.
     */
    private fun shapeFill(p: Int, size: Int, base: Int): Int {
        var o = base + 42
        if (size < o + 2) return -1
        val count = data.u16(p + o)
        if (count > MAX_MATRICES) return -1
        o += 2 + 48 + count * 96 + 13
        if (size < o + 8) return -1
        val type = data.u32(p + o)
        if (type and 1L == 0L) return -1
        return colorOf(data.u32(p + o + 4))
    }

    // ---- 수식·주석·번호 ----------------------------------------------------------------------------

    /**
     * 수식(`eqed`). **조판하지 않는다** — 스크립트(한글 수식 편집기의 `EQN` 문법)를 글자 그대로 `span.math` 에 보이고 센다.
     * 스크립트의 문법은 명세 밖의 별도 문서다. `EQEDIT` 는 속성(4) 다음 길이 붙은 문자열이다(표 105 — hwplib 과 같이 스크립트만 읽는다).
     *
     * 캡션은 표·그리기 개체와 같이 자리를 따른다([captionOnTop]) — 위·왼쪽이면 캡션 상자를 먼저 두고 수식은 문단을 이어 연다.
     * 처음에는 수식만 캡션을 언제나 뒤에 두었다(13단계 짝 대조).
     */
    private fun equation(pos: Int) {
        record(UnsupportedFeatures.EQUATION)
        val cur = RecordCursor(data, pos)
        if (!cur.peek()) return
        val lc = cur.level
        registerInstance(cur)
        cur.advance()
        var caption: ListRef? = null
        var script: String? = null
        while (cur.peek() && cur.level > lc) {
            if (cur.level == lc + 1) {
                if (cur.tag == HwpTag.LIST_HEADER && caption == null && script == null) {
                    caption = readList(cur)
                    continue
                }
                if (cur.tag == HwpTag.EQEDIT && script == null) {
                    script = try {
                        PayloadReader(data, cur.payload, cur.size).let { r ->
                            r.skip(4)
                            r.wstr(Hwp5Limits.MAX_EQUATION_CHARS)
                        }
                    } catch (e: HwpFormatException) {
                        null
                    }
                }
            }
            cur.skipWithChildren()
        }
        val t = script?.trim().orEmpty()
        val capTop = caption != null && captionOnTop(caption)
        if (capTop) renderCaption(caption)
        if (t.isNotEmpty() && suppress == 0) {
            val pc = ensureOpen()
            if (pc != null) {
                cfg.scan?.addChars(t.length)
                pc.visible = true
                start("span", "class" to "math")
                text(t)
                end("span")
            }
        }
        if (caption != null && !capTop) renderCaption(caption)
    }

    /**
     * 각주·미주. 번호는 **우리가 센다**(문서 차례로, 구역 정의의 '구역마다 새로' 를 따른다) — 컨트롤 머리에도 번호가 있지만
     * 셈이 한곳이어야 조각의 경계에서 어긋나지 않는다(실물에서 두 값은 늘 같았다). 새 번호 지정([newNumber])만 셈을 옮긴다.
     * 표지 모양은 각주/미주 모양의 것이다. 본문은 조각의 끝에 모아 그린다.
     *
     * 링크의 `id` 는 번호가 아니라 **이 부분에서 센 차례**다 — 새 번호 지정은 번호를 되돌릴 수 있어(한 구역 안에서 `1)` 이
     * 두 번) 번호로 지으면 두 주석이 한 `id` 를 나눠 뒤의 표지가 앞의 본문으로 간다. HWPX 도 부분마다 따로 센다.
     */
    private fun note(pos: Int, endnote: Boolean) {
        if (cfg.notes) return
        val number = if (endnote) ++state.endnoteNo else ++state.footnoteNo
        val label = (if (endnote) state.section.end else state.section.foot).label(number)
        if (cfg.recordFeatures) scanNote(pos)
        if (suppress > 0) return
        val pc = ensureOpen() ?: return
        pc.visible = true
        cfg.scan?.addChars(label.length)
        val ref = NoteRef(endnote, if (endnote) ++endnoteSeq else ++footnoteSeq, label, pos)
        if (sink != null && noteRefs.size < Hwp5Limits.MAX_NOTES) noteRefs.add(ref)
        start("sup", "class" to "fnref", "id" to ref.backId)
        if (pc.linkDepth == 0) start("a", "href" to "#" + ref.id)
        text(label)
        end("sup")
    }

    /**
     * 자동 번호(`atno`, 표 142 — `UINT32 속성, UINT16 번호, WCHAR 사용자 기호, WCHAR 앞 장식, WCHAR 뒤 장식`). 종류 0(쪽)은
     * 흐름에 쪽이 없어 버린다. 각주·미주 본문 안의 번호(종류 1·2)는 **우리가 센 번호**([pendingNoteMark] — 본문의 표지로
     * 돌아가는 링크)로 바꿔 그 자리에 적는다. 적힌 번호를 쓰지 않는 것은 셈이 한곳이어야 하기 때문이다([note]). 나머지(그림·
     * 표·수식 번호)는 **적힌 번호**를 모양·앞뒤 장식과 함께 쓴다 — 모양과 사용자 기호는 HWPX 의 `hp:autoNum` 과 한 규칙이다
     * ([HancomNumbers.noteLabel], 13단계 짝 대조).
     */
    private fun autoNumber(pos: Int, rs: RunStyle) {
        val cur = RecordCursor(data, pos)
        if (!cur.peek() || cur.size < 10) return
        val p = cur.payload
        val attr = data.u32(p + 4)
        val kind = (attr and 0xF).toInt()
        if (kind == 0) return
        if ((kind == NUMBER_FOOTNOTE || kind == NUMBER_ENDNOTE) && cfg.notes) {
            if (markAtAutoNumber && pendingNoteMark != null && suppress == 0) ensureOpen()?.let { writeNoteMark(it) }
            return
        }
        val number = data.u16(p + 8)
        val shape = HancomNumbers.shapeOf(((attr ushr 4) and 0xFF).toInt())
        fun ch(o: Int): String = if (cur.size >= o + 2) data.u16(p + o).takeIf { it >= 0x20 }?.toChar()?.toString().orEmpty() else ""
        emitText(HancomNumbers.noteLabel(number, shape, ch(10), ch(12), ch(14)), rs)
    }

    /**
     * 새 번호 지정(`nwno`, 표 144 — `UINT32 속성`(비트 0–3 이 번호 종류: 1 각주, 2 미주, 3 그림, 4 표, 5 수식), `UINT16 번호`).
     * 각주·미주면 **다음** 주석이 그 번호를 보이게 셈을 옮긴다. HWPX 는 한글이 저장한 각주 번호(`@number`)가 이것을 이미 담고
     * 있어 같은 번호가 된다 — 처음에는 이 컨트롤을 버려 같은 문서의 각주 번호가 HWP 로만 1부터 이어졌다(13단계 짝 대조).
     * 그림·표·수식 번호는 자동 번호가 적힌 번호를 쓰므로([autoNumber]) 옮길 셈이 없다.
     *
     * 셈은 걷기 상태([WalkState])에 들어 있어 훑기와 그리기가 같은 자리에서 같은 값을 얻는다. 보이는 번호는 시작 번호에서
     * 세므로([NoteShape.label]) 셈은 `번호 - 시작 번호` 로 둔다.
     */
    private fun newNumber(pos: Int) {
        val cur = RecordCursor(data, pos)
        if (!cur.peek() || cur.size < 10) return
        val p = cur.payload
        val kind = (data.u32(p + 4) and 0xF).toInt()
        val number = data.u16(p + 8)
        when (kind) {
            NUMBER_FOOTNOTE -> state.footnoteNo = number - state.section.foot.start
            NUMBER_ENDNOTE -> state.endnoteNo = number - state.section.end.start
        }
    }

    /**
     * 책갈피(`bokm`). 이름은 자식 `CTRL_DATA`(파라미터 셋)의 첫 문자열 항목이다(명세 §4.3.10.11 — 어느 항목이 이름인지는
     * 적혀 있지 않아 첫 문자열을 쓴다). 훑기가 이름 → 최상위 문단을 적어 두고, 그 문단이 `id` 를 받는다.
     */
    private fun bookmark(pos: Int) {
        val scan = cfg.scan ?: return
        val cur = RecordCursor(data, pos)
        if (!cur.peek()) return
        val lc = cur.level
        cur.advance()
        while (cur.peek() && cur.level > lc) {
            if (cur.level == lc + 1 && cur.tag == HwpTag.CTRL_DATA) {
                parameterString(cur.payload, cur.size)?.let { scan.bookmark(it, currentTop) }
                return
            }
            cur.skipWithChildren()
        }
    }

    /** 파라미터 셋(표 50–52)의 첫 문자열(`PIT_BSTR`, 형 1) 항목. 다른 형의 너비가 명세에서 모호해 첫 항목들만 본다. */
    private fun parameterString(p: Int, size: Int): String? = try {
        val r = PayloadReader(data, p, size)
        r.u16() // 셋 ID
        val n = r.i16()
        var found: String? = null
        var k = 0
        while (k < n && k < 8 && found == null) {
            r.u16() // 항목 ID
            val type = r.u16()
            if (type != 1) break
            found = r.wstr(Hwp5Limits.MAX_TITLE_CHARS).takeIf { it.isNotBlank() }
            k++
        }
        found
    } catch (e: HwpFormatException) {
        null
    }

    /**
     * 글자 겹침(`tcps`, 표 150) — 겹칠 글자를 이어 적는다. 공문서의 절 번호(네모 안의 숫자)가 여기 든다 — 겹칠 글자가 한컴의
     * 사설 영역 글자다(표본 K19). 옮길 수 있는 것은 옮기고 남은 사설 영역 글자는 버리고 센다([HancomChars.compose] — HWPX 의
     * `hp:compose` 와 한 규칙). 처음에는 남은 글자를 그대로 적어 두부가 보였다(13단계 짝 대조).
     */
    private fun overlap(pos: Int, rs: RunStyle) {
        val cur = RecordCursor(data, pos)
        if (!cur.peek() || cur.size < 6) return
        val s = try {
            PayloadReader(data, cur.payload + 4, cur.size - 4).wstr(64)
        } catch (e: HwpFormatException) {
            return
        }
        val composed = HancomChars.compose(s)
        if (composed.dropped) record(UnsupportedFeatures.TEXT_EFFECT)
        emitText(composed.text, rs)
    }

    /** 덧말(`tdut`, 표 151) — 본문 글 위에 작은 글. `ruby` 로 쓴다. */
    private fun ruby(pos: Int) {
        val cur = RecordCursor(data, pos)
        if (!cur.peek() || cur.size < 6) return
        val (main, sub) = try {
            val r = PayloadReader(data, cur.payload + 4, cur.size - 4)
            HancomChars.mapAll(r.wstr(Hwp5Limits.MAX_TITLE_CHARS)) to HancomChars.mapAll(r.wstr(Hwp5Limits.MAX_TITLE_CHARS))
        } catch (e: HwpFormatException) {
            return
        }
        if (main.isEmpty() || suppress > 0) return
        val pc = ensureOpen() ?: return
        pc.visible = true
        cfg.scan?.addChars(main.length)
        start("ruby")
        text(main)
        if (sub.isNotEmpty()) {
            start("rt")
            text(sub)
            end("rt")
        }
        end("ruby")
    }

    // ---- 필드 -----------------------------------------------------------------------------------

    /**
     * 필드 시작(글자 3, 표 152). 보이는 글은 끝 글자(4)까지의 글이다. 하이퍼링크(`%hlk`)·상호 참조(`%xrf`)만 링크로 감싼다 —
     * **바깥 주소는 링크로 만들지 않는다**(이 앱은 네트워크를 쓰지 않고, 위생기가 스킴이 붙은 주소를 지운다). 글자만 `span.ext`
     * 로 표시한다. 문서 안 링크는 훑기가 적어 둔 대상의 조각을 찾아 `a` 로.
     */
    private fun fieldStart(id: Int, pos: Int) {
        val pc = para ?: return
        if (pc.fields.size >= MAX_FIELD_DEPTH) {
            pc.ignoredFields++
            return
        }
        val frame = FieldFrame(null, pc.gen)
        pc.fields.add(frame)
        if (id == CtrlId.FIELD_MEMO) record(UnsupportedFeatures.COMMENT)
        if (id != CtrlId.FIELD_HYPERLINK && id != CtrlId.FIELD_CROSS_REF) return
        val cur = RecordCursor(data, pos)
        if (!cur.peek() || cur.size < 11) return
        val command = try {
            PayloadReader(data, cur.payload + 9, cur.size - 9).wstr(Hwp5Limits.MAX_STRING_CHARS)
        } catch (e: HwpFormatException) {
            return
        }
        val target = HwpLinks.parse(command)
        if (target is HwpLinks.Instance) cfg.scan?.linkTarget(target.id)
        if (!writing) return
        when (target) {
            is HwpLinks.Instance, is HwpLinks.Bookmark -> {
                if (pc.linkDepth > 0) return
                val layout = cfg.layout ?: return
                val global = (if (target is HwpLinks.Instance) layout.linkTargets[target.id] else layout.bookmarks[(target as HwpLinks.Bookmark).name])
                    ?: return
                val part = layout.partOf(global)
                val anchor = Hwp5Layout.anchorId(global)
                val href = if (part == cfg.chunkIndex) "#$anchor" else cfg.partHref(part) + "#" + anchor
                ensureOpenIn(pc)
                start("a", "href" to href)
                pc.linkDepth++
                frame.tag = "a"
            }
            HwpLinks.External -> {
                ensureOpenIn(pc)
                start("span", "class" to "ext")
                frame.tag = "span"
            }
            HwpLinks.None -> Unit
        }
    }

    private fun fieldEnd() {
        val pc = para ?: return
        if (pc.ignoredFields > 0) {
            pc.ignoredFields--
            return
        }
        if (pc.fields.isEmpty()) return
        val f = pc.fields.removeAt(pc.fields.size - 1)
        val tag = f.tag ?: return
        if (pc.opened && pc.gen == f.gen) {
            end(tag)
            if (tag == "a") pc.linkDepth--
        }
    }

    // ---- 상태 -----------------------------------------------------------------------------------

    /**
     * 문단 하나. 여는 태그는 **처음 쓸 것이 생길 때** 쓴다 — 비었는지 알아야 빈 문단을 줄일 수 있고, 블록으로 끊긴 뒤에는
     * 번호 없이 이어 열어야 하기 때문이다.
     *
     * @param gen 문단을 (다시) 열 때마다 는다. 링크를 연 쪽이 닫을 때 같은 문단인지 확인한다.
     */
    private class ParaCtx(
        val tag: String,
        val id: String?,
        val cls: String?,
        val style: String?,
        val marker: String?,
        val base: RunStyle,
    ) {
        var opened = false
        var visible = false
        var continuation = false
        var gen = 0
        var linkDepth = 0
        var headingText: StringBuilder? = null
        var headingLevel = 0
        var headingGlobal = -1
        var effectRecorded = false

        /**
         * 이 문단 안에서 열린 필드. **문단마다 따로** 든다 — 문단 안의 표 칸도 문단이라, 걷기에 하나만 두면 칸의 문단이
         * 바깥 문단의 열린 링크를 지운다.
         */
        val fields = ArrayList<FieldFrame>(2)
        var ignoredFields = 0
    }

    companion object {
        /** 문단 머리 모양(문단 모양 속성 1 의 비트 23–24) '개요'. */
        private const val HEAD_OUTLINE = 1

        /** 자동 번호·새 번호 지정의 번호 종류(속성의 비트 0–3) — 각주·미주. */
        private const val NUMBER_FOOTNOTE = 1
        private const val NUMBER_ENDNOTE = 2

        /**
         * 한글이 저절로 넣는 설명문의 첫 줄 — 개체 종류의 이름 + `입니다.`. 표본에서 본 것은 그림·사각형·묶음 개체·개체
         * 연결선·다각형·클립아트(`장식11-4입니다.`)이고, 나머지는 같은 틀의 개체 종류다. **아무 `…입니다.` 나 받지 않는다** —
         * '조직도입니다.' 는 사람이 쓴 설명일 수 있다(검토가 잡았다).
         */
        private val AUTO_COMMENT_HEADS = setOf(
            "그림", "사각형", "타원", "선", "호", "다각형", "곡선", "묶음 개체", "개체 연결선", "글맵시", "글상자",
            "OLE 개체", "차트", "동영상", "수식", "표",
        ).map { "${it}입니다." }.toSet()

        /** 클립아트의 설명문 첫 줄(`장식11-4입니다.`). */
        private val CLIP_ART_HEAD = Regex("장식[0-9]+(-[0-9]+)*입니다\\.")

        /** 문단 모양의 여백·간격은 HWPUNIT 의 두 배로 적혀 있다([ParaShape]). 1 pt = 100 HWPUNIT. */
        private const val UNITS_PER_PT = 200.0

        private const val HWPUNIT_PER_PT = 100.0

        /** 한글의 기본 줄 간격(글자에 따라 160%). 이 값이면 적지 않는다 — 바탕 CSS 가 같은 값(`1.6`)이다. */
        private const val DEFAULT_LINE_SPACING = 160

        /** 칸의 세로 정렬(목록 머리 속성의 비트 5–6 — 명세의 '문단 리스트 헤더'): 0 위, 1 가운데, 2 아래. */
        private const val V_ALIGN_TOP = 0
        private const val V_ALIGN_BOTTOM = 2

        private const val MAX_FIELD_DEPTH = Hwp5Limits.MAX_FIELD_DEPTH
        private const val MAX_CTRL_SEARCH = Hwp5Limits.MAX_CTRL_SEARCH
        private const val MAX_MATRICES = Hwp5Limits.MAX_SHAPE_MATRICES

        const val FIELD_START = 3
        const val FIELD_END = 4
        const val TAB = 9
        const val LINE_BREAK = 10
        const val PARA_BREAK = 13
        const val HYPHEN = 24
        const val NBSP = 30
        const val FIXED_SPACE = 31

        /** 8칸을 차지하는 컨트롤(인라인 + 확장) — pyhwp `controlchar.py` 와 같은 분류. */
        private val EIGHT_UNIT = setOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 11, 12, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23)

        /** 확장 컨트롤 — `CTRL_HEADER` 가 짝으로 있다. */
        private val EXTENDED = setOf(1, 2, 3, 11, 12, 14, 15, 16, 17, 18, 21, 22, 23)

        /**
         * 정렬. **양쪽 정렬(0)과 왼쪽(1)은 적지 않는다** — 한글 문서는 거의 모든 문단이 양쪽 정렬이라 적으면 문단마다 같은
         * 선언이 붙고, 좁은 폰 화면에서 `word-break:keep-all` 과 양쪽 정렬이 만나면 줄 사이가 크게 벌어진다. 읽기 쉬운 쪽을
         * 골랐다. 배분·나눔(4·5)은 문서가 일부러 고른 것이라 양쪽 정렬로 옮긴다.
         */
        private fun align(a: Int): String? = when (a) {
            2 -> "right"
            3 -> "center"
            4, 5 -> "justify"
            else -> null
        }
    }
}
