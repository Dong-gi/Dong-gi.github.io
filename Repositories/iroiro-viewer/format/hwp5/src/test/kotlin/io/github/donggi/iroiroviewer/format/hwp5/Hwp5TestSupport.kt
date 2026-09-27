package io.github.donggi.iroiroviewer.format.hwp5

import io.github.donggi.iroiroviewer.format.ByteArrayDocumentSource
import io.github.donggi.iroiroviewer.format.FlowDocument
import io.github.donggi.iroiroviewer.format.OpenOutcome
import io.github.donggi.iroiroviewer.format.cfb.TinyCfb
import io.github.donggi.iroiroviewer.safety.ParseLimits
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * HWP 5.0 시험의 짜개. 실세계 문서를 커밋하지 않으므로(CLAUDE.md '표본') 시험이 보는 구조는 전부 여기서 만든다 —
 * 레코드 흐름(머리의 태그·수준·크기와 0xFFF 늘인 모양), `DocInfo`, 구역, 그리고 `TinyCfb` 로 싼 CFB 한 벌.
 * 압축은 실물과 같이 `Deflater(nowrap = true)`(raw DEFLATE)다.
 *
 * 레코드의 모양은 명세가 아니라 **실물에서 읽은 모양**을 따른다(13단계 조사 — `LIST_HEADER` 의 8바이트 앞머리, 칸 주소의 자리,
 * 74바이트 글자 모양, 두 배로 적힌 문단 여백).
 */
internal class Bytes {
    private val out = ByteArrayOutputStream()

    fun u8(v: Int) = apply { out.write(v and 0xFF) }
    fun u16(v: Int) = apply { u8(v); u8(v ushr 8) }
    fun i32(v: Int) = apply { u16(v); u16(v ushr 16) }
    fun u32(v: Long) = i32(v.toInt())
    fun bytes(b: ByteArray) = apply { out.write(b) }
    fun zeros(n: Int) = apply { repeat(n) { u8(0) } }

    /** `WORD len` + `WCHAR[len]`. */
    fun wstr(s: String) = apply {
        u16(s.length)
        for (c in s) u16(c.code)
    }

    fun size(): Int = out.size()
    fun toByteArray(): ByteArray = out.toByteArray()
}

internal object Rec {
    /** 레코드 하나. 크기가 0xFFF 이상이면 늘인 모양(크기 칸 0xFFF + 32비트 크기)으로 적는다 — 명세의 '4095 이상'. */
    fun record(tag: Int, level: Int, payload: ByteArray): ByteArray {
        val b = Bytes()
        if (payload.size >= 0xFFF) {
            b.i32(tag or (level shl 10) or (0xFFF shl 20))
            b.i32(payload.size)
        } else {
            b.i32(tag or (level shl 10) or (payload.size shl 20))
        }
        b.bytes(payload)
        return b.toByteArray()
    }

    fun ctrlId(s: String): Int = CtrlId.of(s)

    fun join(parts: List<ByteArray>): ByteArray {
        val b = ByteArrayOutputStream()
        for (p in parts) b.write(p)
        return b.toByteArray()
    }
}

/** 문단 하나를 짠다. 글자 조각을 차례로 붙이고, 컨트롤은 자기 레코드(컨트롤 수준에서 시작)를 함께 준다. */
internal class P(private val paraShape: Int = 0, private val style: Int = 0) {
    private val units = ArrayList<Int>()
    private val ctrls = ArrayList<(Int) -> ByteArray>()
    private val runs = ArrayList<Pair<Int, Int>>()
    var last = false
    var instanceId = 0L

    /** 글자. [charShape] 를 주면 이 자리에서 그 글자 모양이 시작된다. */
    fun text(s: String, charShape: Int? = null) = apply {
        charShape?.let { runs.add(units.size to it) }
        for (c in s) units.add(c.code)
    }

    fun br() = apply { units.add(10) }
    fun nbsp() = apply { units.add(30) }
    fun hyphen() = apply { units.add(24) }

    /** 인라인 컨트롤(8칸, 짝 `CTRL_HEADER` 없음) — 탭(9)·필드 끝(4). */
    fun inline(code: Int) = apply {
        units.add(code)
        repeat(6) { units.add(0x4141) } // 알맹이 자리에 글자처럼 보이는 값을 둔다 — 새어 나오면 'A' 가 보인다.
        units.add(code)
    }

    fun tab() = inline(9)
    fun fieldEnd() = inline(4)

    /** 확장 컨트롤(8칸) — 알맹이 앞 4바이트가 ID, 레코드는 [records] 가 컨트롤 수준을 받아 만든다. */
    fun ctrl(code: Int, id: String, records: (Int) -> ByteArray) = apply {
        val v = CtrlId.of(id)
        units.add(code)
        units.add(v and 0xFFFF)
        units.add(v ushr 16)
        repeat(4) { units.add(0x4242) }
        units.add(code)
        ctrls.add(records)
    }

    fun bytes(level: Int = 0): ByteArray {
        val all = ArrayList(units)
        all.add(13)
        val n = all.size
        val parts = ArrayList<ByteArray>()
        val header = Bytes()
            .u32((n.toLong() and 0x7FFFFFFF) or (if (last) 0x80000000L else 0L))
            .u32(0) // 컨트롤 마스크
            .u16(paraShape)
            .u8(style)
            .u8(0)
            .u16(maxOf(1, runs.size))
            .u16(0)
            .u16(1)
            .u32(instanceId)
            .u16(0)
        parts.add(Rec.record(HwpTag.PARA_HEADER, level, header.toByteArray()))
        if (n > 1) {
            val t = Bytes()
            for (u in all) t.u16(u)
            parts.add(Rec.record(HwpTag.PARA_TEXT, level + 1, t.toByteArray()))
        }
        val cs = Bytes()
        val rs = if (runs.isEmpty() || runs[0].first != 0) listOf(0 to 0) + runs else runs
        for ((pos, id) in rs) cs.u32(pos.toLong()).u32(id.toLong())
        parts.add(Rec.record(HwpTag.PARA_CHAR_SHAPE, level + 1, cs.toByteArray()))
        parts.add(Rec.record(HwpTag.PARA_LINE_SEG, level + 1, ByteArray(36)))
        for (c in ctrls) parts.add(c(level + 1))
        return Rec.join(parts)
    }
}

/** 컨트롤의 레코드들. 인자 `lc` 는 `CTRL_HEADER` 의 수준. */
internal object Ctrl {

    /** 개체 공통 속성(표 69) — 폭·높이(HWPUNIT)·인스턴스 번호·설명. */
    fun common(id: String, width: Int = 0, height: Int = 0, instance: Long = 0, description: String = ""): ByteArray =
        Bytes().i32(CtrlId.of(id)).u32(0).i32(0).i32(0).i32(width).i32(height).i32(0)
            .u16(0).u16(0).u16(0).u16(0).u32(instance).i32(0).wstr(description).toByteArray()

    /** 목록 머리(8바이트 앞머리 + 덧붙임). */
    fun listHeader(count: Int, flags: Long = 0, extra: ByteArray = ByteArray(0)): ByteArray =
        Bytes().u16(count).u16(0).u32(flags).bytes(extra).toByteArray()

    fun paras(list: List<P>, level: Int): ByteArray {
        list.lastOrNull()?.last = true
        return Rec.join(list.map { it.bytes(level) })
    }

    /** 칸 하나. */
    class Cell(
        val row: Int,
        val col: Int,
        val paras: List<P>,
        val rowSpan: Int = 1,
        val colSpan: Int = 1,
        val borderFill: Int = 0,
        val vAlign: Int = 0,
    )

    fun table(rows: Int, cols: Int, cells: List<Cell>, caption: List<P>? = null, rowCounts: IntArray? = null, instance: Long = 0): (Int) -> ByteArray = { lc ->
        val parts = ArrayList<ByteArray>()
        parts.add(Rec.record(HwpTag.CTRL_HEADER, lc, common("tbl ", instance = instance)))
        if (caption != null) {
            val capExtra = Bytes().u32(3).i32(0).u16(0).i32(0).toByteArray() // 아래
            parts.add(Rec.record(HwpTag.LIST_HEADER, lc + 1, listHeader(caption.size, extra = capExtra)))
            parts.add(paras(caption, lc + 1))
        }
        val counts = rowCounts ?: IntArray(rows) { r -> cells.count { it.row == r } }
        val t = Bytes().u32(0).u16(rows).u16(cols).u16(0).u16(0).u16(0).u16(0).u16(0)
        for (c in counts) t.u16(c)
        t.u16(0).u16(0)
        parts.add(Rec.record(HwpTag.TABLE, lc + 1, t.toByteArray()))
        for (c in cells) {
            val extra = Bytes().u16(c.col).u16(c.row).u16(c.colSpan).u16(c.rowSpan).i32(1000).i32(500)
                .u16(0).u16(0).u16(0).u16(0).u16(c.borderFill).i32(1000).toByteArray()
            parts.add(Rec.record(HwpTag.LIST_HEADER, lc + 1, listHeader(c.paras.size, flags = (c.vAlign.toLong() shl 5), extra = extra)))
            parts.add(paras(c.paras, lc + 1))
        }
        Rec.join(parts)
    }

    /** `SHAPE_COMPONENT` 알맹이. 맨 위 개체는 ID 를 두 번, 묶음 안은 한 번. */
    fun component(kind: String, top: Boolean, width: Int = 0): ByteArray {
        val b = Bytes().i32(CtrlId.of(kind))
        if (top) b.i32(CtrlId.of(kind))
        b.i32(0).i32(0).u16(0).u16(1).i32(width).i32(100).i32(width).i32(100).u32(0).u16(0).i32(0).i32(0)
        b.u16(0).zeros(48) // 행렬 수 0 + 옮김 행렬
        return b.toByteArray()
    }

    /** 그림 알맹이(표 107) — 자리 71 의 BinItem 차례. */
    fun pictureRecord(binItem: Int): ByteArray = Bytes().zeros(71).u16(binItem).u8(0).u32(0).toByteArray()

    fun picture(binItem: Int, width: Int = 7200, alt: String = ""): (Int) -> ByteArray = { lc ->
        Rec.join(
            listOf(
                Rec.record(HwpTag.CTRL_HEADER, lc, common("gso ", width = width, description = alt)),
                Rec.record(HwpTag.SHAPE_COMPONENT, lc + 1, component("\$pic", true, width)),
                Rec.record(HwpTag.SHAPE_COMPONENT_PICTURE, lc + 2, pictureRecord(binItem)),
            )
        )
    }

    /** 글상자가 든 사각형. */
    fun textBox(paras: List<P>): (Int) -> ByteArray = { lc ->
        Rec.join(
            listOf(
                Rec.record(HwpTag.CTRL_HEADER, lc, common("gso ")),
                Rec.record(HwpTag.SHAPE_COMPONENT, lc + 1, component("\$rec", true)),
                Rec.record(HwpTag.LIST_HEADER, lc + 2, listHeader(paras.size, extra = ByteArray(25))),
                paras(paras, lc + 2),
                Rec.record(0x4F, lc + 2, ByteArray(9)), // SHAPE_COMPONENT_RECTANGLE
            )
        )
    }

    /** 글상자 없는 그리기 개체 하나(`$rec`·`$lin`·`$col` …). 모양 레코드는 크기만 맞춘 빈 것이다. */
    fun drawing(kind: String): (Int) -> ByteArray = { lc ->
        Rec.join(
            listOf(
                Rec.record(HwpTag.CTRL_HEADER, lc, common("gso ")),
                Rec.record(HwpTag.SHAPE_COMPONENT, lc + 1, component(kind, true)),
                Rec.record(HwpTag.SHAPE_COMPONENT_LINE, lc + 2, ByteArray(18)),
            )
        )
    }

    /** 묶음(`$con`) 안의 그림 둘. */
    fun group(vararg binItems: Int): (Int) -> ByteArray = { lc ->
        val parts = arrayListOf(
            Rec.record(HwpTag.CTRL_HEADER, lc, common("gso ", width = 20000)),
            Rec.record(HwpTag.SHAPE_COMPONENT, lc + 1, component("\$con", true, 20000)),
        )
        for (b in binItems) {
            parts.add(Rec.record(HwpTag.SHAPE_COMPONENT, lc + 2, component("\$pic", false, 5000)))
            parts.add(Rec.record(HwpTag.SHAPE_COMPONENT_PICTURE, lc + 3, pictureRecord(b)))
        }
        Rec.join(parts)
    }

    fun note(endnote: Boolean, paras: List<P>): (Int) -> ByteArray = { lc ->
        Rec.join(
            listOf(
                Rec.record(HwpTag.CTRL_HEADER, lc, Bytes().i32(CtrlId.of(if (endnote) "en  " else "fn  ")).u32(1).u16(0).u16(')'.code).u32(0).u32(0).toByteArray()),
                Rec.record(HwpTag.LIST_HEADER, lc + 1, listHeader(paras.size)),
                paras(paras, lc + 1),
            )
        )
    }

    /** 자동 번호(표 142 — 속성, 번호, 사용자 기호, 앞 장식, 뒤 장식). */
    fun autoNumber(kind: Int, number: Int, shape: Int = 0, prefix: Char? = null, suffix: Char? = null, userChar: Char? = null): (Int) -> ByteArray = { lc ->
        Rec.record(
            HwpTag.CTRL_HEADER, lc,
            Bytes().i32(CtrlId.of("atno")).u32((kind or (shape shl 4)).toLong()).u16(number).u16(userChar?.code ?: 0)
                .u16(prefix?.code ?: 0).u16(suffix?.code ?: 0).toByteArray(),
        )
    }

    /** 새 번호 지정(표 144 — 속성의 비트 0–3 이 번호 종류, 번호). */
    fun newNumber(kind: Int, number: Int): (Int) -> ByteArray = { lc ->
        Rec.record(HwpTag.CTRL_HEADER, lc, Bytes().i32(CtrlId.of("nwno")).u32(kind.toLong()).u16(number).toByteArray())
    }

    /** 수식. [captionDir] 은 캡션의 자리(0 왼쪽, 1 오른쪽, 2 위, 3 아래). */
    fun equation(script: String, caption: List<P>? = null, captionDir: Int = 3): (Int) -> ByteArray = { lc ->
        val parts = arrayListOf(Rec.record(HwpTag.CTRL_HEADER, lc, common("eqed")))
        if (caption != null) {
            val capExtra = Bytes().u32(captionDir.toLong()).i32(0).u16(0).i32(0).toByteArray()
            parts.add(Rec.record(HwpTag.LIST_HEADER, lc + 1, listHeader(caption.size, extra = capExtra)))
            parts.add(paras(caption, lc + 1))
        }
        parts.add(Rec.record(HwpTag.EQEDIT, lc + 1, Bytes().u32(0).wstr(script).i32(1000).u32(0).toByteArray()))
        Rec.join(parts)
    }

    /** 필드 시작(표 152). */
    fun field(id: String, command: String): (Int) -> ByteArray = { lc ->
        Rec.record(HwpTag.CTRL_HEADER, lc, Bytes().i32(CtrlId.of(id)).u32(0).u8(0).wstr(command).u32(7).toByteArray())
    }

    /** 구역 정의 — 개요 번호 정의와 각주·미주 모양. */
    fun section(outlineNumbering: Int = 1, foot: ByteArray? = null, end: ByteArray? = null): (Int) -> ByteArray = { lc ->
        val parts = arrayListOf(
            Rec.record(HwpTag.CTRL_HEADER, lc, Bytes().i32(CtrlId.of("secd")).u32(0).u16(0).u16(0).u16(0).u32(8000).u16(outlineNumbering).zeros(18).toByteArray()),
            Rec.record(0x49, lc + 1, ByteArray(40)),
        )
        parts.add(Rec.record(HwpTag.FOOTNOTE_SHAPE, lc + 1, foot ?: noteShape()))
        parts.add(Rec.record(HwpTag.FOOTNOTE_SHAPE, lc + 1, end ?: noteShape()))
        Rec.join(parts)
    }

    /** 각주/미주 모양(표 133 — 속성, 사용자 기호, 앞 장식, 뒤 장식, 시작 번호 …). */
    fun noteShape(shape: Int = 0, prefix: Char? = null, suffix: Char? = ')', start: Int = 1, numbering: Int = 0, userChar: Char? = null): ByteArray =
        Bytes().u32((shape or (numbering shl 10)).toLong()).u16(userChar?.code ?: 0).u16(prefix?.code ?: 0).u16(suffix?.code ?: 0).u16(start)
            .zeros(16).toByteArray()

    fun header(): (Int) -> ByteArray = { lc ->
        Rec.join(
            listOf(
                Rec.record(HwpTag.CTRL_HEADER, lc, Bytes().i32(CtrlId.of("head")).u32(0).toByteArray()),
                Rec.record(HwpTag.LIST_HEADER, lc + 1, listHeader(1, extra = ByteArray(10))),
                P().text("머리말 글").also { it.last = true }.bytes(lc + 1),
            )
        )
    }
}

/** 글자 모양 하나의 짜개(74바이트). */
internal class CS(
    val size: Int = 1000,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Int = 0,
    val strike: Boolean = false,
    val sup: Boolean = false,
    val sub: Boolean = false,
    val color: Int = 0,
    val face: Int = 0,
    val shade: Long = 0xFFFFFFFFL,
    val effect: Boolean = false,
    /** 취소선 모양(비트 26–29). 15 는 3D 단선. */
    val strikeShape: Int = 0,
) {
    fun bytes(): ByteArray {
        var attr = 0L
        if (italic) attr = attr or 1
        if (bold) attr = attr or 2
        attr = attr or (underline.toLong() shl 2)
        if (effect) attr = attr or (1L shl 11)
        if (sup) attr = attr or (1L shl 15)
        if (sub) attr = attr or (1L shl 16)
        if (strike) attr = attr or (1L shl 18)
        attr = attr or (strikeShape.toLong() shl 26)
        // COLORREF 는 0x00bbggrr.
        val ref = ((color and 0xFF) shl 16) or (color and 0xFF00) or ((color ushr 16) and 0xFF)
        val b = Bytes()
        repeat(7) { b.u16(face) }
        repeat(7) { b.u8(100) }
        repeat(7) { b.u8(0) }
        repeat(7) { b.u8(100) }
        repeat(7) { b.u8(0) }
        b.i32(size).u32(attr).u8(0).u8(0).i32(ref).i32(0).u32(shade).i32(0x00B2B2B2).u16(0).i32(0)
        return b.toByteArray()
    }
}

/** 문단 모양 하나의 짜개(54바이트). 여백·들여쓰기·간격은 **HWPUNIT 의 두 배**로 받는다(실물과 같다). */
internal class PS(
    val align: Int = 0,
    val left: Int = 0,
    val right: Int = 0,
    val indent: Int = 0,
    val before: Int = 0,
    val after: Int = 0,
    val headType: Int = 0,
    val headLevel: Int = 0,
    val numbering: Int = 0,
    val lineSpacing: Int = 160,
) {
    fun bytes(): ByteArray {
        val attr1 = (align.toLong() shl 2) or (headType.toLong() shl 23) or (headLevel.toLong() shl 25)
        return Bytes().u32(attr1).i32(left).i32(right).i32(indent).i32(before).i32(after).i32(lineSpacing)
            .u16(0).u16(numbering).u16(0).zeros(8).u32(0).u32(0).u32(lineSpacing.toLong()).toByteArray()
    }
}

/** 바이너리 데이터 하나. [compression] 은 BIN_DATA 의 압축 칸(0 따른다, 1 압축, 2 날것). */
internal class Bin(val storageId: Int, val ext: String, val data: ByteArray, val compression: Int = 0, val link: Boolean = false)

/** `DocInfo` 의 짜개. */
internal class DocInfoSpec {
    var sectionCount = 1
    var faces = listOf("함초롬바탕", "HY헤드라인M")
    val charShapes = arrayListOf(CS())
    val paraShapes = arrayListOf(PS())
    /** 번호 정의 — 수준마다 (모양, 형식). */
    val numberings = ArrayList<List<Pair<Int, String>>>()

    /** 번호 정의(차례, 0부터)의 수준별 시작 번호 일곱. 없으면 모두 1. */
    val numberingStarts = HashMap<Int, IntArray>()
    val bullets = ArrayList<Char>()
    val styles = arrayListOf("바탕글" to 0)
    val bins = ArrayList<Bin>()
    val borderFills = ArrayList<Int>()

    /** 네 변이 모두 '없음' 인 테두리/배경의 번호(1부터). 나머지는 네 변이 실선이다. */
    val borderlessFills = HashSet<Int>()

    fun bytes(): ByteArray {
        val parts = ArrayList<ByteArray>()
        parts.add(Rec.record(HwpTag.DOCUMENT_PROPERTIES, 0, Bytes().u16(sectionCount).zeros(24).toByteArray()))
        val idm = Bytes().i32(bins.size)
        repeat(7) { idm.i32(faces.size) }
        idm.i32(borderFills.size).i32(charShapes.size).i32(0).i32(numberings.size).i32(bullets.size)
            .i32(paraShapes.size).i32(styles.size).i32(0).i32(0).i32(0)
        parts.add(Rec.record(HwpTag.ID_MAPPINGS, 0, idm.toByteArray()))
        for (b in bins) {
            val attr = (if (b.link) 0 else 1) or (b.compression shl 4)
            val r = Bytes().u16(attr)
            if (b.link) r.wstr("C:\\secret\\a.png").wstr("a.png") else r.u16(b.storageId).wstr(b.ext)
            parts.add(Rec.record(HwpTag.BIN_DATA, 1, r.toByteArray()))
        }
        repeat(7) { for (f in faces) parts.add(Rec.record(HwpTag.FACE_NAME, 1, Bytes().u8(0).wstr(f).toByteArray())) }
        for ((k, bg) in borderFills.withIndex()) {
            // 한 변씩 종류·굵기·색(6바이트) 넷, 그리고 대각선 6바이트. 한글은 '없음' 인 변에도 굵기를 적는다 — 굵기 1(0.12 mm)을
            // 넣어 두면 종류 넷을 먼저 읽는 틀린 해석이 '없음' 을 '있음' 으로 읽어 시험이 그것을 가른다.
            val sideType = if ((k + 1) in borderlessFills) 0 else 1
            val r = Bytes().u16(0)
            repeat(4) { r.u8(sideType).u8(1).i32(0) }
            r.zeros(6)
            if (bg < 0) r.u32(0) else r.u32(1).i32(((bg and 0xFF) shl 16) or (bg and 0xFF00) or ((bg ushr 16) and 0xFF)).i32(0).i32(-1)
            r.u32(0)
            parts.add(Rec.record(HwpTag.BORDER_FILL, 1, r.toByteArray()))
        }
        for (c in charShapes) parts.add(Rec.record(HwpTag.CHAR_SHAPE, 1, c.bytes()))
        for ((i, n) in numberings.withIndex()) {
            val r = Bytes()
            for (k in 0 until 7) {
                val (shape, format) = n.getOrElse(k) { 0 to "" }
                r.u32((shape shl 5).toLong()).u16(0).u16(0).u32(0).wstr(format)
            }
            r.u16(1)
            val starts = numberingStarts[i]
            for (k in 0 until 7) r.u32((starts?.getOrNull(k) ?: 1).toLong())
            parts.add(Rec.record(HwpTag.NUMBERING, 1, r.toByteArray()))
        }
        for (b in bullets) parts.add(Rec.record(HwpTag.BULLET, 1, Bytes().u32(0).u16(0).u16(0).u32(0).u16(b.code).i32(0).zeros(5).u16(0).toByteArray()))
        for (p in paraShapes) parts.add(Rec.record(HwpTag.PARA_SHAPE, 1, p.bytes()))
        for ((name, cs) in styles) {
            parts.add(Rec.record(HwpTag.STYLE, 1, Bytes().wstr(name).wstr("Normal").u8(0).u8(0).u16(1042).u16(0).u16(cs).u16(0).toByteArray()))
        }
        return Rec.join(parts)
    }
}

/**
 * HWP 5.0 파일 한 벌의 짜개. [flags] 는 `FileHeader` 의 속성(기본은 압축).
 */
internal class HwpFile {
    var version = 0x05000304
    var flags = 1L
    val docInfo = DocInfoSpec()
    var docInfoRaw: ByteArray? = null
    val sections = ArrayList<ByteArray>()
    var prvText: String? = null
    var distributionSeed = 0x5BF764BE
    var distributionFlags = 0x8001
    var headerOverride: ByteArray? = null

    val compressed: Boolean get() = flags and 1L != 0L

    fun section(vararg paras: P): HwpFile {
        paras.last().last = true
        sections.add(Rec.join(paras.map { it.bytes(0) }))
        return this
    }

    fun build(): ByteArray {
        val cfb = TinyCfb()
        val fh = headerOverride ?: Bytes().bytes("HWP Document File".toByteArray(Charsets.US_ASCII)).zeros(15)
            .i32(version).u32(flags).u32(0).u32(0).zeros(208).toByteArray()
        cfb.stream("FileHeader", fh)
        val di = docInfoRaw ?: docInfo.bytes()
        cfb.stream("DocInfo", if (compressed) deflate(di) else di)
        val distribution = flags and 4L != 0L
        for ((i, s) in sections.withIndex()) {
            if (distribution) {
                cfb.stream("ViewText/Section$i", encryptDistribution(s, compressed, distributionSeed + i, distributionFlags))
                // 배포용 문서의 BodyText 에는 안내 문단 하나가 있다(실물). 읽으면 안 된다.
                val placeholder = P().text("배포용 문서 안내").also { it.last = true }.bytes(0)
                cfb.stream("BodyText/Section$i", if (compressed) deflate(placeholder) else placeholder)
            } else {
                cfb.stream("BodyText/Section$i", if (compressed) deflate(s) else s)
            }
        }
        for (b in docInfo.bins) {
            if (b.link) continue
            val name = "BIN%04X.%s".format(java.util.Locale.ROOT, b.storageId, b.ext)
            val packed = when (b.compression) {
                1 -> deflate(b.data)
                2 -> b.data
                else -> if (compressed) deflate(b.data) else b.data
            }
            cfb.stream("BinData/$name", packed)
        }
        prvText?.let { t ->
            val p = Bytes()
            for (c in t) p.u16(c.code)
            p.u16(0)
            cfb.stream("PrvText", p.toByteArray())
        }
        return cfb.build().bytes
    }

    companion object {
        fun deflate(b: ByteArray): ByteArray {
            val d = Deflater(Deflater.DEFAULT_COMPRESSION, true)
            d.setInput(b)
            d.finish()
            val out = ByteArrayOutputStream()
            val buf = ByteArray(4096)
            while (!d.finished()) out.write(buf, 0, d.deflate(buf))
            d.end()
            return out.toByteArray()
        }

        /**
         * 배포용 스트림을 **만든다** — 시험 쪽의 암호기. 복호화기와 같은 알고리즘(hwp-foss)을 따로 짠 것이다: 256바이트에 씨앗과
         * SHA-1 16진 문자열(열쇠)을 두고, MSVC `rand()` 로 4바이트 뒤를 XOR 한 뒤, 본문(압축했으면 압축한 것)을 16바이트로
         * 채워 AES-128-ECB 로 잠근다. 실물 셋(S01·S02·O01)이 독립 오라클이다(`Hwp5SampleTest`).
         */
        fun encryptDistribution(plain: ByteArray, compressed: Boolean, seed: Int, flags: Int, sha1Hex: String = "E390A4B1C2D3E4F5061728394A5B6C7D8E9FA0B1"): ByteArray {
            val body = if (compressed) deflate(plain) else plain
            val padded = body.copyOf((body.size + 15) / 16 * 16)
            val data = ByteArray(256)
            val rnd = java.util.Random(seed.toLong())
            rnd.nextBytes(data)
            for (i in 0 until 4) data[i] = (seed ushr (8 * i)).toByte()
            val off = 4 + (seed and 0xF)
            for ((k, c) in sha1Hex.withIndex()) {
                data[off + 2 * k] = c.code.toByte()
                data[off + 2 * k + 1] = 0
            }
            data[off + 80] = flags.toByte()
            data[off + 81] = (flags ushr 8).toByte()
            val key = data.copyOfRange(off, off + 16)
            // MSVC rand() 로 뒤섞기.
            var s = seed
            var n = 0
            var k = 0
            for (i in 0 until 256) {
                if (n == 0) {
                    s = s * 214013 + 2531011
                    k = (s ushr 16) and 0x7FFF and 0xFF
                    s = s * 214013 + 2531011
                    n = ((s ushr 16) and 0x7FFF and 0xF) + 1
                }
                if (i >= 4) data[i] = (data[i].toInt() xor k).toByte()
                n--
            }
            val cipher = Cipher.getInstance("AES/ECB/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
            val enc = cipher.doFinal(padded)
            return Bytes().i32(HwpTag.DISTRIBUTE_DOC_DATA or (256 shl 20)).bytes(data).bytes(enc).toByteArray()
        }
    }
}

internal object Hwp5 {
    fun outcome(bytes: ByteArray, options: Hwp5Options = Hwp5Options(), password: CharArray? = null): OpenOutcome = runBlocking {
        Hwp5Opener(password, ParseLimits.DEFAULT, options).open(ByteArrayDocumentSource(bytes, "t.hwp"))
    }

    fun open(bytes: ByteArray, options: Hwp5Options = Hwp5Options()): FlowDocument {
        val o = outcome(bytes, options)
        check(o is OpenOutcome.Success) { "열리지 않았다: $o" }
        return o.document as FlowDocument
    }

    fun open(file: HwpFile, options: Hwp5Options = Hwp5Options()): FlowDocument = open(file.build(), options)
}

/** 부분 하나의 `<body>` 안쪽. */
internal fun FlowDocument.body(index: Int = 0): String =
    partHtml(index)!!.substringAfter("<body>").substringBeforeLast("</body>")

private val TAG = Regex("<[^>]+>")

/** HTML 의 글자만(태그를 떼고 엔티티를 푼다). */
internal fun plain(html: String): String =
    TAG.replace(html, "").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&#39;", "'").replace("&amp;", "&")

internal fun String.occurrences(needle: String): Int {
    var n = 0
    var i = indexOf(needle)
    while (i >= 0) {
        n++
        i = indexOf(needle, i + needle.length)
    }
    return n
}
