package io.github.donggi.iroiroviewer.format.hwp5

import io.github.donggi.iroiroviewer.format.CorruptFormatException

/**
 * HWP 5.0 의 형식이 깨졌다 — 서명·레코드 머리·압축·배포용 자료 어느 것이든.
 *
 * **메시지에는 우리가 쓴 고정 문장만 넣는다**(파일에서 읽은 값도 경로도 없다). 공용 `toOpenFailure()` 가
 * `CorruptFormatException` 을 '깨진 파일' 로 옮기고 그 메시지가 `OpenFailure.detail` 이 된다.
 */
internal class HwpFormatException(message: String) : CorruptFormatException(message)

/** 레코드 태그(`HWPTAG_BEGIN` = 0x10 에서 센 값). 명세 표 13·57 [SPEC p.22/17, p.38/33]. */
internal object HwpTag {
    /** `HWPTAG_BEGIN`. 이보다 작은 태그는 '특별한 용도' 로 비워 두었다. */
    const val BEGIN = 0x10
    const val DOCUMENT_PROPERTIES = 0x10
    const val ID_MAPPINGS = 0x11
    const val BIN_DATA = 0x12
    const val FACE_NAME = 0x13
    const val BORDER_FILL = 0x14
    const val CHAR_SHAPE = 0x15
    const val TAB_DEF = 0x16
    const val NUMBERING = 0x17
    const val BULLET = 0x18
    const val PARA_SHAPE = 0x19
    const val STYLE = 0x1A
    const val DISTRIBUTE_DOC_DATA = 0x1C

    const val PARA_HEADER = 0x42
    const val PARA_TEXT = 0x43
    const val PARA_CHAR_SHAPE = 0x44
    const val PARA_LINE_SEG = 0x45
    const val CTRL_HEADER = 0x47
    const val LIST_HEADER = 0x48
    const val FOOTNOTE_SHAPE = 0x4A
    const val SHAPE_COMPONENT = 0x4C
    const val TABLE = 0x4D
    const val SHAPE_COMPONENT_LINE = 0x4E
    const val SHAPE_COMPONENT_CURVE = 0x53
    const val SHAPE_COMPONENT_OLE = 0x54
    const val SHAPE_COMPONENT_PICTURE = 0x55
    const val CTRL_DATA = 0x57
    const val EQEDIT = 0x58
    const val SHAPE_COMPONENT_TEXTART = 0x5A
    const val FORM_OBJECT = 0x5B
    const val CHART_DATA = 0x5F
    const val VIDEO_DATA = 0x62
}

/**
 * 컨트롤 ID — 명세의 `MAKE_4CHID(a,b,c,d) = (a<<24)|(b<<16)|(c<<8)|d` [SPEC p.41/36]. 파일에는 작은 끝(little-endian)
 * 으로 적혀 글자가 거꾸로 보이지만(`'secd'` → `64 63 65 73`), [u32] 로 읽으면 이 값과 같다.
 */
internal object CtrlId {
    fun of(s: String): Int {
        require(s.length == 4)
        return (s[0].code shl 24) or (s[1].code shl 16) or (s[2].code shl 8) or s[3].code
    }

    val SECD = of("secd")
    val COLD = of("cold")
    val TABLE = of("tbl ")
    val GSO = of("gso ")
    val EQUATION = of("eqed")
    val HEADER = of("head")
    val FOOTER = of("foot")
    val FOOTNOTE = of("fn  ")
    val ENDNOTE = of("en  ")
    val AUTO_NUMBER = of("atno")
    val NEW_NUMBER = of("nwno")
    val PAGE_HIDE = of("pghd")
    val PAGE_ODD_EVEN = of("pgct")
    val PAGE_NUMBER_POS = of("pgnp")
    val INDEX_MARK = of("idxm")
    val BOOKMARK = of("bokm")
    val OVERLAP = of("tcps")
    val RUBY = of("tdut")
    val HIDDEN_COMMENT = of("tcmt")
    val FORM = of("form")

    // 개체(`gso ` 안의 SHAPE_COMPONENT 가 적는 종류) — 표 67.
    val PICTURE = of("\$pic")
    val CONTAINER = of("\$con")
    val OLE = of("\$ole")
    val LINE = of("\$lin")
    val RECT = of("\$rec")
    val ELLIPSE = of("\$ell")
    val ARC = of("\$arc")
    val POLYGON = of("\$pol")
    val CURVE = of("\$cur")

    /** 개체 연결선. 실물 K04 가 둘 쓴다. */
    val CONNECT_LINE = of("\$col")

    // 필드 — 표 128. 첫 글자가 `%` 다.
    val FIELD_HYPERLINK = of("%hlk")
    val FIELD_CROSS_REF = of("%xrf")
    val FIELD_BOOKMARK = of("%bmk")
    val FIELD_MEMO = of("%%me")

    fun isField(id: Int): Boolean = (id ushr 24) == '%'.code

    /**
     * 그림을 뺀 그리기 개체(선·사각형·타원·호·다각형·곡선·개체 연결선). 모양 자체는 그리지 않는다. 처음에는 연결선이 빠져
     * 아무것도 세지 않고 버렸다(13단계 짝 대조 — K04 의 둘. HWPX 는 `hp:connectLine` 을 다른 도형과 같이 센다).
     */
    fun isDrawing(id: Int): Boolean =
        id == LINE || id == RECT || id == ELLIPSE || id == ARC || id == POLYGON || id == CURVE || id == CONNECT_LINE
}

// ---- 작은 끝 정수 ----------------------------------------------------------------------------------

internal fun ByteArray.u8(o: Int): Int = this[o].toInt() and 0xFF

internal fun ByteArray.u16(o: Int): Int = (this[o].toInt() and 0xFF) or ((this[o + 1].toInt() and 0xFF) shl 8)

internal fun ByteArray.i16(o: Int): Int = u16(o).toShort().toInt()

internal fun ByteArray.i32(o: Int): Int =
    (this[o].toInt() and 0xFF) or ((this[o + 1].toInt() and 0xFF) shl 8) or
        ((this[o + 2].toInt() and 0xFF) shl 16) or ((this[o + 3].toInt() and 0xFF) shl 24)

internal fun ByteArray.u32(o: Int): Long = i32(o).toLong() and 0xFFFFFFFFL

/**
 * 레코드 흐름을 **앞으로만** 읽는 커서 [SPEC p.21/16 §4.1]. 머리는 32비트 하나 —
 * 태그 10비트(0–9), 수준 10비트(10–19), 크기 12비트(20–31). 크기 칸이 전부 1(0xFFF)이면 **다음 32비트가 크기**다
 * — 명세가 '4095 바이트 이상' 이라 적으므로 딱 4095바이트짜리도 늘인 모양을 쓴다(pyhwp·hwplib 이 같이 읽는다).
 *
 * ## 엄격하게
 *
 * 머리가 잘렸거나 크기가 스트림을 넘으면 [HwpFormatException] 이다. 실물 17개의 스트림 전부가 마지막 바이트에서
 * 정확히 끝났다(13단계 조사) — 끝에 남는 부스러기를 너그럽게 받을 이유가 없다. 크기는 `Long` 으로 읽어 남은
 * 길이와 견준 뒤에만 `Int` 로 옮긴다.
 *
 * ## 트리
 *
 * 레코드는 **수준**으로 트리를 이룬다 — 한 레코드는 앞에서 가장 가까운, 수준이 하나 작은 레코드의 자식이다.
 * 자식 전체를 건너뛰는 것은 [skipSubtree] 다. 커서는 바이트 배열 위의 자리일 뿐이라 같은 배열에 커서를 여럿
 * 세워 트리의 다른 가지를 따로 걸을 수 있다(표의 칸을 행 순서로 다시 걸을 때 쓴다).
 */
internal class RecordCursor(val data: ByteArray, start: Int, private val end: Int = data.size) {

    /** 다음 레코드 머리의 자리. */
    var pos: Int = start
        private set

    /** [peek] 이 읽은 레코드. */
    var tag = 0
        private set
    var level = 0
        private set
    var size = 0
        private set

    /** 알맹이(payload)의 시작 자리. */
    var payload = 0
        private set

    private var nextPos = -1
    private var peekedAt = -1

    init {
        require(start in 0..end && end <= data.size) { "범위 밖 커서" }
    }

    /**
     * 다음 레코드의 머리를 읽는다(소비하지 않는다). 끝이면 거짓. 깨졌으면 던진다. 같은 자리에서 여러 번 불러도 된다.
     */
    fun peek(): Boolean {
        if (peekedAt == pos) return true
        if (pos >= end) return false
        if (end - pos < 4) throw HwpFormatException("레코드 머리가 잘렸다")
        val h = data.i32(pos)
        var p = pos + 4
        var s = (h ushr 20).toLong()
        if (s == 0xFFFL) {
            if (end - p < 4) throw HwpFormatException("레코드 머리가 잘렸다")
            s = data.u32(p)
            p += 4
        }
        if (s > (end - p).toLong()) throw HwpFormatException("레코드가 스트림보다 길다")
        tag = h and 0x3FF
        level = (h ushr 10) and 0x3FF
        size = s.toInt()
        payload = p
        nextPos = p + size
        peekedAt = pos
        return true
    }

    /** [peek] 한 레코드를 소비한다(자식은 그대로 둔다 — 다음이 첫 자식이다). */
    fun advance() {
        check(peekedAt == pos) { "peek 없이 advance" }
        pos = nextPos
    }

    /** [parentLevel] 보다 깊은 레코드를 전부 건너뛴다 — 부모의 자식 트리 전체. */
    fun skipSubtree(parentLevel: Int) {
        while (peek() && level > parentLevel) advance()
    }

    /** 지금 레코드와 그 자식 전체를 건너뛴다. */
    fun skipWithChildren() {
        check(peekedAt == pos) { "peek 없이 skip" }
        val l = level
        advance()
        skipSubtree(l)
    }
}

/**
 * 레코드 알맹이 하나를 읽는 것. 명세가 틀린 곳이 많아(표 4 의 크기 대부분) **적힌 만큼만 읽고, 모자라면 기본값**
 * 으로 두는 너그러운 읽기([has]·`…Or`)와, 반드시 있어야 하는 칸을 읽는 엄격한 읽기(모자라면 던진다)를 함께 준다.
 */
internal class PayloadReader(private val data: ByteArray, start: Int, length: Int) {
    private val end = start + length
    var pos = start
        private set

    val remaining: Int get() = end - pos

    fun has(n: Int): Boolean = end - pos >= n

    private fun need(n: Int) {
        if (end - pos < n) throw HwpFormatException("레코드가 짧다")
    }

    fun skip(n: Int) {
        need(n)
        pos += n
    }

    fun u8(): Int {
        need(1)
        return data.u8(pos).also { pos += 1 }
    }

    fun u16(): Int {
        need(2)
        return data.u16(pos).also { pos += 2 }
    }

    fun i16(): Int {
        need(2)
        return data.i16(pos).also { pos += 2 }
    }

    fun i32(): Int {
        need(4)
        return data.i32(pos).also { pos += 4 }
    }

    fun u32(): Long {
        need(4)
        return data.u32(pos).also { pos += 4 }
    }

    fun u16Or(default: Int): Int = if (has(2)) u16() else default

    fun i32Or(default: Int): Int = if (has(4)) i32() else default

    fun u32Or(default: Long): Long = if (has(4)) u32() else default

    /**
     * `WORD len` + `WCHAR[len]`. 길이가 레코드를 넘으면 던진다. [max] 자를 넘는 뒷부분은 읽고 버린다.
     * 짝 잃은 대리 문자는 그대로 둔다 — `HtmlWriter` 가 쓸 때 거른다.
     */
    fun wstr(max: Int = Hwp5Limits.MAX_STRING_CHARS): String {
        val n = u16()
        need(2 * n)
        val keep = minOf(n, max)
        val chars = CharArray(keep)
        for (k in 0 until keep) chars[k] = data.u16(pos + 2 * k).toChar()
        pos += 2 * n
        return String(chars)
    }
}
