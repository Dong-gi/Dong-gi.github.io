package io.github.donggi.iroiroviewer.format.hwp5

import io.github.donggi.iroiroviewer.format.html.HancomNumbers

/**
 * 글자 모양(`HWPTAG_CHAR_SHAPE`, 표 33) 가운데 흐름 렌더가 쓰는 것. 실물은 74바이트다(명세는 72 — 5.0.3.0 의
 * 취소선 색이 뒤에 붙는다). 앞 50바이트(속성까지)가 없으면 기본 모양으로 친다.
 *
 * @param faceHangul 한글 글꼴 번호 — **한글 무리 안에서 0부터** 센다(글꼴 레코드에는 언어가 없다, [DocInfo.hangulFaces]).
 * @param relSizeHangul 한글의 상대 크기(%, 10–250).
 * @param baseSize 기준 크기(1/100 pt).
 * @param textColor `0x00bbggrr`. 위 바이트가 차 있으면 '색 없음' 이다(-1).
 * @param shadeColor 음영 색. 기본값이 `0xFFFFFFFF`(없음)이다.
 */
internal class CharShape(
    val faceHangul: Int,
    val relSizeHangul: Int,
    val baseSize: Int,
    val attr: Long,
    val textColor: Int,
    val shadeColor: Int,
) {
    val italic: Boolean get() = bit(0)
    val bold: Boolean get() = bit(1)

    /** 밑줄 자리(비트 2–3): 0 없음, 1 아래, 2 가운데(= 취소선, pyhwp·hwplib), 3 위. */
    val underlinePos: Int get() = ((attr ushr 2) and 3L).toInt()

    val superscript: Boolean get() = bit(15)
    val subscript: Boolean get() = bit(16)

    /**
     * 취소선(비트 18–20, hwplib 은 18만 본다). 밑줄 자리 '가운데' 도 취소선이다. **다만 모양(비트 26–29)이 3D 단선(15)이면
     * 긋지 않는다** — HWPX 변환기와 같은 판단이다. 공문서의 글자 모양 가운데 꽤 많은 수(K19 는 512개 중 112개)가
     * '여부 1 · 밑줄 자리 가운데 · 모양 3D 단선' 이고 한컴의 HWPX 내보내기도 같은 글자 모양을 `strikeout shape="3D"`
     * 로 적는데(짝 표본 K19↔K27 에서 번호마다 대조했다), 그 모양이 걸린 글이 '보도자료'·'배포'·보도 날짜이고 정책브리핑의
     * 바로보기도 줄을 긋지 않는다. HWPX 표본에서는 '바탕글' 까지 그 모양이다. 한글이 왜 긋지 않는지는 확인하지 못했다.
     */
    val strike: Boolean get() = (((attr ushr 18) and 7L) != 0L || underlinePos == 2) && strikeShape != STRIKE_SHAPE_3D

    /** 취소선 모양(비트 26–29, 선 종류 표). 15 가 3D 단선이다. */
    val strikeShape: Int get() = ((attr ushr 26) and 15L).toInt()

    /** 외곽선(8–10)·그림자(11–12)·양각(13)·음각(14). 그리지 않고 센다. */
    val effect: Boolean get() = ((attr ushr 8) and 0x7FL) != 0L

    /** 실제 크기(1/100 pt). 상대 크기가 0 이면 100% 로 본다. */
    val sizeCentiPt: Int
        get() {
            val rel = if (relSizeHangul in 10..250) relSizeHangul else 100
            return (baseSize.toLong() * rel / 100).toInt()
        }

    private fun bit(n: Int): Boolean = (attr ushr n) and 1L == 1L

    companion object {
        val DEFAULT = CharShape(0, 100, 1000, 0, 0, -1)

        /** 선 종류 15 — 3D 단선. HWPX 의 `strikeout shape="3D"` 와 같은 값이다([strike]). */
        const val STRIKE_SHAPE_3D = 15

        fun parse(r: PayloadReader): CharShape {
            val face = r.u16()
            r.skip(12) // 나머지 언어의 글꼴 6
            r.skip(14) // 장평 7 · 자간 7
            val rel = r.u8()
            r.skip(6 + 7) // 나머지 상대 크기 · 글자 위치 7
            val base = r.i32()
            val attr = r.u32()
            r.skip(2) // 그림자 간격
            val color = colorOf(r.u32Or(0))
            r.skip(minOf(4, r.remaining)) // 밑줄 색
            val shade = colorOf(r.u32Or(0xFFFFFFFFL))
            return CharShape(face, rel, base, attr, color, shade)
        }
    }
}

/** `COLORREF`(`0x00bbggrr`)를 `0xrrggbb` 로. 위 바이트가 차 있으면 '없음'(-1). */
internal fun colorOf(ref: Long): Int {
    if (ref ushr 24 != 0L) return -1
    val r = (ref and 0xFF).toInt()
    val g = ((ref ushr 8) and 0xFF).toInt()
    val b = ((ref ushr 16) and 0xFF).toInt()
    return (r shl 16) or (g shl 8) or b
}

/**
 * 문단 모양(`HWPTAG_PARA_SHAPE`, 표 43). 여백·들여쓰기·간격은 **HWPUNIT 의 두 배**로 적혀 있다 — 같은 문서의 HWP 와
 * HWPX(K11·K33)를 견주면 HWPX 의 `hh:margin`(HWPUNIT) 값이 정확히 절반이다(13단계 실측, pyhwp 의 `doubled_margin` 과 같다).
 * 그래서 pt 로는 `값 / 200`.
 *
 * @param numberingId 번호·글머리표 번호(**1부터**, 0 은 없음 — 실물의 글머리표 5개 문서가 1..5 를 쓴다).
 */
internal class ParaShape(
    val attr1: Long,
    val left: Int,
    val right: Int,
    val indent: Int,
    val spaceBefore: Int,
    val spaceAfter: Int,
    val lineSpacingOld: Int,
    val numberingId: Int,
    val attr3: Long?,
    val lineSpacing: Long?,
) {
    /** 정렬(비트 2–4): 0 양쪽, 1 왼쪽, 2 오른쪽, 3 가운데, 4 배분, 5 나눔. */
    val align: Int get() = ((attr1 ushr 2) and 7L).toInt()

    /** 문단 머리 모양(비트 23–24): 0 없음, 1 개요, 2 번호, 3 글머리표. */
    val headType: Int get() = ((attr1 ushr 23) and 3L).toInt()

    /** 문단 수준(비트 25–27) — 1수준이 0 이다. */
    val headLevel: Int get() = ((attr1 ushr 25) and 7L).toInt()

    /**
     * 줄 간격(%). 5.0.2.5 부터는 속성 3 의 종류(비트 0–4)와 뒤 칸의 값을, 그 전에는 속성 1 의 비트 0–1 과 옛 칸을 쓴다.
     * '글자에 따라'(0)가 아니면 null — 고정값·여백만은 글꼴 크기를 모르면 옮길 수 없다.
     */
    val lineSpacingPercent: Int?
        get() {
            val kind: Int
            val value: Long
            if (attr3 != null && lineSpacing != null) {
                kind = (attr3 and 0x1F).toInt()
                value = lineSpacing
            } else {
                kind = (attr1 and 3L).toInt()
                value = lineSpacingOld.toLong()
            }
            return if (kind == 0 && value in 50..500) value.toInt() else null
        }

    companion object {
        val DEFAULT = ParaShape(0, 0, 0, 0, 0, 0, 160, 0, null, null)

        fun parse(r: PayloadReader): ParaShape {
            val attr1 = r.u32()
            val left = r.i32()
            val right = r.i32()
            val indent = r.i32()
            val before = r.i32()
            val after = r.i32()
            val lsOld = r.i32Or(160)
            if (r.has(2)) r.skip(2) // 탭 정의
            val numbering = r.u16Or(0)
            if (r.has(2 + 8)) r.skip(2 + 8) // 테두리/배경 · 테두리 간격
            val attr2 = r.u32Or(-1L)
            val attr3 = if (attr2 >= 0 && r.has(8)) r.u32() else null
            val ls = if (attr3 != null) r.u32() else null
            return ParaShape(attr1, left, right, indent, before, after, lsOld, numbering, attr3, ls)
        }
    }
}

/**
 * 번호 문단의 수준 하나(문단 머리 정보 12바이트 + 번호 형식) — 표 38–40.
 *
 * @param format 번호 형식. `^1`..`^7`(확장 `^8`..`^10`)이 그 수준의 값, `^N`·`^n` 이 수준 경로다. `^1` 꼴은 명세에 없는데
 *   실물 전부가 쓴다(13단계 조사 — 명세는 `^n`·`^N` 만 적는다).
 * @param start 이 수준의 시작 번호(5.0.2.5 부터). 없으면 1.
 */
internal class LevelDef(val attr: Long, val format: String, val start: Int) {
    /** 번호 모양(비트 5–9, hwplib `getParagraphNumberFormat`) — 명세 표 40 은 비트 0–4 까지만 적는다. 값은 표 41. */
    val shape: Int get() = ((attr ushr 5) and 0x1F).toInt()
}

/** 문단 번호(`HWPTAG_NUMBERING`). 수준 10(명세 7 + 5.1 의 확장 3). */
internal class NumberingDef(val levels: Array<LevelDef?>) {
    /** 수준마다의 시작 번호(없는 수준은 1). */
    val starts: IntArray = IntArray(Hwp5Limits.NUMBERING_LEVELS) { levels.getOrNull(it)?.start ?: 1 }

    /** 수준마다의 번호 모양 — HWPX 와 한 표([HancomNumbers])의 값이다. 없는 수준과 모르는 모양은 아라비아 숫자. */
    val shapes: IntArray = IntArray(Hwp5Limits.NUMBERING_LEVELS) { HancomNumbers.shapeOf(levels.getOrNull(it)?.shape ?: 0) }

    companion object {
        fun parse(r: PayloadReader): NumberingDef {
            val levels = arrayOfNulls<LevelDef>(Hwp5Limits.NUMBERING_LEVELS)
            val attrs = LongArray(Hwp5Limits.NUMBERING_LEVELS)
            val formats = arrayOfNulls<String>(Hwp5Limits.NUMBERING_LEVELS)
            for (k in 0 until 7) {
                attrs[k] = r.u32()
                r.skip(8) // 너비 보정 · 본문과의 거리 · 글자 모양
                formats[k] = r.wstr()
            }
            if (r.has(2)) r.skip(2) // 시작 번호(문서 전체) — 수준별 시작 번호가 있으면 그쪽이 앞선다.
            val starts = IntArray(Hwp5Limits.NUMBERING_LEVELS) { 1 }
            if (r.has(28)) for (k in 0 until 7) starts[k] = clampStart(r.u32())
            // 5.1 의 확장 수준 8–10. 명세는 5.1.0.0 부터라 적지만 hwplib 처럼 바이트가 남아 있으면 읽는다.
            var ext = 7
            while (ext < Hwp5Limits.NUMBERING_LEVELS && r.has(14)) {
                attrs[ext] = r.u32()
                r.skip(8)
                formats[ext] = r.wstr()
                ext++
            }
            if (ext == Hwp5Limits.NUMBERING_LEVELS && r.has(12)) for (k in 7 until 10) starts[k] = clampStart(r.u32())
            for (k in 0 until Hwp5Limits.NUMBERING_LEVELS) {
                val f = formats[k] ?: continue
                levels[k] = LevelDef(attrs[k], f, starts[k])
            }
            return NumberingDef(levels)
        }

        private fun clampStart(v: Long): Int = if (v in 0..100_000) v.toInt() else 1
    }
}

/** 글머리표(`HWPTAG_BULLET`) — 머리 정보 12바이트 뒤의 글자 하나. */
internal class BulletDef(val char: Char) {
    companion object {
        fun parse(r: PayloadReader): BulletDef {
            r.skip(12)
            return BulletDef(r.u16().toChar())
        }
    }
}

/**
 * 스타일(`HWPTAG_STYLE`). 문단이 가리키는 것은 문단 모양·글자 모양 번호라, 스타일은 **바탕글(0)의 글자 모양**(문서의 기본
 * 글자)을 보려고만 읽는다. 이름(`개요 1`)은 읽지 않는다 — 제목은 문단 모양의 머리 모양으로만 가린다(13단계 짝 대조, HWPX 와 같다).
 */
internal class StyleDef(val paraShapeId: Int, val charShapeId: Int) {
    companion object {
        fun parse(r: PayloadReader): StyleDef {
            r.wstr(MAX_NAME) // 이름
            r.wstr(MAX_NAME) // 영문 이름
            r.skip(4) // 속성 · 다음 스타일 · 언어
            val ps = r.u16Or(0)
            val cs = r.u16Or(0)
            return StyleDef(ps, cs)
        }

        private const val MAX_NAME = 128
    }
}

/**
 * 바이너리 데이터(`HWPTAG_BIN_DATA`, 표 17–18). 나머지 칸은 **종류에 따라** 다르다(hwplib `ForBinData` — 명세는 한 줄로
 * 늘어놓는다): 연결(LINK)이면 경로 둘, 삽입(EMBEDDING)·저장소(STORAGE)면 저장소 번호와 확장자.
 *
 * @param compression 비트 4–5: 0 `FileHeader` 를 따른다, 1 언제나 압축, 2 압축하지 않음. 실물이 이 칸대로다 —
 *   '압축하지 않음' 인 JPEG 97개가 날것이고, '따른다' 인 것은 전부 풀린다(13단계 조사).
 * @param streamName `BIN%04X.확장자`(16진 대문자 — 실물의 `BIN000A.jpg`). 연결이거나 확장자가 이상하면 null.
 */
internal class BinItem(val type: Int, val compression: Int, val streamName: String?) {
    val isLink: Boolean get() = type == TYPE_LINK

    companion object {
        const val TYPE_LINK = 0

        fun parse(r: PayloadReader): BinItem {
            val attr = r.u16()
            val type = attr and 0xF
            val compression = (attr ushr 4) and 3
            if (type == TYPE_LINK) return BinItem(type, compression, null)
            val id = r.u16()
            val ext = r.wstr(16)
            val ok = ext.isNotEmpty() && ext.length <= 8 && ext.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }
            return BinItem(type, compression, if (ok) "BIN%04X.%s".format(java.util.Locale.ROOT, id, ext) else null)
        }
    }
}

/**
 * `DocInfo` 스트림 — 문서 전체가 함께 쓰는 표들. **표의 번호는 레코드의 차례**다(명세는 번호 칸을 두지 않는다).
 * 그래서 레코드 하나가 깨져도 **자리는 지킨다** — 기본 항목을 넣고 [brokenRecords] 를 센다. 하나를 빼면 뒤의 모든
 * 번호가 하나씩 밀려 문서 전체의 서식이 어긋난다.
 *
 * 번호의 바탕(0 또는 1)은 pyhwp 의 XSL 과 hwplib 이 같다 — 글자 모양·문단 모양·스타일은 0부터, 테두리/배경·
 * 바이너리 데이터·번호·글머리표는 1부터(0 은 없음).
 */
internal class DocInfo(
    val sectionCount: Int,
    val binItems: List<BinItem>,
    val hangulFaces: List<String>,
    private val borderFillColors: IntArray,
    private val borderless: BooleanArray,
    val charShapes: List<CharShape>,
    val paraShapes: List<ParaShape>,
    val numberings: List<NumberingDef>,
    val bullets: List<BulletDef>,
    val styles: List<StyleDef>,
    val brokenRecords: Int,
) {
    fun charShape(id: Int): CharShape? = charShapes.getOrNull(id)
    fun paraShape(id: Int): ParaShape? = paraShapes.getOrNull(id)
    fun style(id: Int): StyleDef? = styles.getOrNull(id)
    fun numbering(id1: Int): NumberingDef? = if (id1 >= 1) numberings.getOrNull(id1 - 1) else null
    fun bullet(id1: Int): BulletDef? = if (id1 >= 1) bullets.getOrNull(id1 - 1) else null
    fun binItem(id1: Int): BinItem? = if (id1 >= 1) binItems.getOrNull(id1 - 1) else null
    fun faceName(index: Int): String? = hangulFaces.getOrNull(index)

    /** 테두리/배경의 면 색(단색 채우기일 때). 없으면 -1. */
    fun fillColor(id1: Int): Int = if (id1 >= 1 && id1 <= borderFillColors.size) borderFillColors[id1 - 1] else -1

    /**
     * 네 변이 모두 '없음' 인 테두리/배경인가. 그런 칸은 테두리를 그리지 않는다(`td.nb`) — HWPX 변환기와 같은 규칙이다. 처음에는
     * 테두리 종류를 읽지 않아 보도자료 머리의 '보도시점·배포' 줄처럼 선이 없는 칸에도 모두 선을 그었다(13단계 짝 대조).
     */
    fun noBorder(id1: Int): Boolean = id1 >= 1 && id1 <= borderless.size && borderless[id1 - 1]

    companion object {
        val EMPTY = DocInfo(0, emptyList(), emptyList(), IntArray(0), BooleanArray(0), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), 0)

        /**
         * 풀어 낸 `DocInfo` 를 읽는다. 레코드 머리가 깨졌으면 [HwpFormatException] — 부르는 쪽이 문서 전체를 기본 서식으로
         * 그리고 알린다. 레코드 하나의 알맹이가 이상한 것은 여기서 삼킨다(위 주석).
         */
        fun parse(bytes: ByteArray, checkCancel: () -> Unit = {}): DocInfo {
            var sectionCount = 0
            var idMappings: IntArray? = null
            val bin = ArrayList<BinItem>()
            val faces = ArrayList<String>()
            val fills = ArrayList<Int>()
            val noBorders = ArrayList<Boolean>()
            val chars = ArrayList<CharShape>()
            val paras = ArrayList<ParaShape>()
            val nums = ArrayList<NumberingDef>()
            val bullets = ArrayList<BulletDef>()
            val styles = ArrayList<StyleDef>()
            var broken = 0
            var count = 0

            val cur = RecordCursor(bytes, 0)
            while (cur.peek()) {
                if (++count > Hwp5Limits.MAX_DOCINFO_RECORDS) throw HwpFormatException("DocInfo 레코드가 너무 많다")
                if (count and 1023 == 0) checkCancel()
                val r = PayloadReader(bytes, cur.payload, cur.size)
                fun <T> add(list: MutableList<T>, default: T, read: (PayloadReader) -> T) {
                    if (list.size >= Hwp5Limits.MAX_TABLE_ENTRIES) return
                    list.add(
                        try {
                            read(r)
                        } catch (e: HwpFormatException) {
                            broken++
                            default
                        }
                    )
                }
                when (cur.tag) {
                    HwpTag.DOCUMENT_PROPERTIES -> if (cur.size >= 2) sectionCount = r.u16()
                    HwpTag.ID_MAPPINGS -> {
                        val n = minOf(cur.size / 4, 18)
                        idMappings = IntArray(n) { r.i32() }
                    }
                    HwpTag.BIN_DATA -> add(bin, BinItem(1, 0, null)) { BinItem.parse(it) }
                    HwpTag.FACE_NAME -> add(faces, "") { it.u8(); it.wstr(64) }
                    HwpTag.BORDER_FILL -> {
                        add(fills, -1) { fillColorOf(it) }
                        add(noBorders, false) { noBorderOf(PayloadReader(bytes, cur.payload, cur.size)) }
                    }
                    HwpTag.CHAR_SHAPE -> add(chars, CharShape.DEFAULT) { CharShape.parse(it) }
                    HwpTag.PARA_SHAPE -> add(paras, ParaShape.DEFAULT) { ParaShape.parse(it) }
                    HwpTag.NUMBERING -> add(nums, NumberingDef(arrayOfNulls(Hwp5Limits.NUMBERING_LEVELS))) { NumberingDef.parse(it) }
                    HwpTag.BULLET -> add(bullets, BulletDef('•')) { BulletDef.parse(it) }
                    HwpTag.STYLE -> add(styles, StyleDef(0, 0)) { StyleDef.parse(it) }
                }
                cur.advance()
            }
            return DocInfo(
                sectionCount = sectionCount,
                binItems = bin,
                hangulFaces = hangulGroup(faces, idMappings),
                borderFillColors = fills.toIntArray(),
                borderless = noBorders.toBooleanArray(),
                charShapes = chars,
                paraShapes = paras,
                numberings = nums,
                bullets = bullets,
                styles = styles,
                brokenRecords = broken,
            )
        }

        /**
         * 글꼴 레코드에는 언어가 없다 — 한글·영어·한자·일어·기타·기호·사용자 차례로 이어 적고, 무리마다의 개수는
         * `ID_MAPPINGS` 의 1..7 칸이 준다(hwplib `addFaceNameByIDMappings`). 글자 모양의 글꼴 번호는 **무리 안에서
         * 0부터**다. 개수가 맞지 않으면 전부를 한글 무리로 본다(글꼴 이름은 모양일 뿐이라 틀려도 글자는 잃지 않는다).
         */
        private fun hangulGroup(faces: List<String>, idMappings: IntArray?): List<String> {
            if (idMappings == null || idMappings.size < 8) return faces
            var sum = 0L
            for (k in 1..7) {
                // 음수 칸이 합을 맞춰 한글 칸이 레코드 수를 넘게 할 수 있다 — 그러면 `subList` 가 던져 DocInfo 전체(서식·그림 목록)를 잃었다.
                if (idMappings[k] < 0) return faces
                sum += idMappings[k].toLong()
            }
            if (sum != faces.size.toLong()) return faces
            return faces.subList(0, idMappings[1])
        }

        /**
         * 테두리/배경(표 23). 테두리 넷은 **변마다 {종류, 굵기, 색}** 이 섞여 있다(명세의 배열 모양이 틀렸다 — hwplib·pyhwp·
         * rhwp 가 같이 읽는다). 칸 배경에 필요한 것은 단색 채우기의 면 색 하나뿐이라 그 뒤는 보지 않는다.
         */
        /**
         * 네 변(왼쪽·오른쪽·위·아래)이 모두 종류 0('없음')인가. 한 변이 **종류·굵기·색**(1 + 1 + 4바이트)을 한데 적는다 —
         * 명세의 표는 종류 넷을 먼저 적는 배열처럼 보이지만, 짝 표본(K19 ↔ K27)의 713개가 번호마다 한 변씩 적은 모양으로만
         * HWPX 의 `NONE` 과 맞았다(hwplib·pyhwp 도 그렇게 읽는다).
         */
        private fun noBorderOf(r: PayloadReader): Boolean {
            r.skip(2) // 속성
            var none = true
            repeat(4) {
                if (r.u8() != 0) none = false
                r.skip(5) // 굵기 · 색
            }
            return none
        }

        private fun fillColorOf(r: PayloadReader): Int {
            r.skip(2 + 6 * 4 + 6) // 속성 · 테두리 넷 · 대각선
            val type = r.u32()
            if (type and 1L == 0L) return -1
            return colorOf(r.u32())
        }
    }
}
