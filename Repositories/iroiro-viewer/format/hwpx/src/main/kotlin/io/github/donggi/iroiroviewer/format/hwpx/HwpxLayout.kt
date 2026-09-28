package io.github.donggi.iroiroviewer.format.hwpx

import io.github.donggi.iroiroviewer.format.FlowOutline
import io.github.donggi.iroiroviewer.format.UnsupportedFeatures
import io.github.donggi.iroiroviewer.format.html.HancomChunkMeter
import io.github.donggi.iroiroviewer.format.html.HancomChunkPolicy
import io.github.donggi.iroiroviewer.format.html.HancomNumbers
import io.github.donggi.iroiroviewer.format.html.HancomTitles
import io.github.donggi.iroiroviewer.format.html.HtmlWriter
import io.github.donggi.iroiroviewer.safety.ParseLimits

/**
 * 여는 설정. 기본값이 제품의 값이고 시험만 바꾼다.
 *
 * 조각 나누기의 기준([chunk])은 HWP 5.0 변환기와 같은 `HancomChunkPolicy` 다 — 표의 칸 하나를 **블록 하나**로 센다
 * ([ScanCollector.addCell]). 처음에는 칸을 글자 60자로 쳐서 같은 보도자료가 HWP 로 6부분, HWPX 로 4부분이었다(13단계 짝 대조).
 */
internal data class HwpxOptions(
    val chunk: HancomChunkPolicy = HancomChunkPolicy(),
    /** 조각 하나의 본문 HTML 상한(글자). 각주는 그 4분의 1 을 따로 쓴다. */
    val maxChars: Int = HtmlWriter.DEFAULT_MAX_CHARS,
)

/**
 * 구역 하나의 설정 — 첫 문단의 `hp:secPr` 에서 읽는다. 조각이 구역의 중간에서 시작하면 훑기가 읽어 둔 것을
 * 쓴다(그리기는 `secPr` 을 다시 지나지 않는다).
 *
 * @param outlineNumbering 개요 문단이 쓰는 번호 정의(`outlineShapeIDRef`). 한글의 기본값은 1.
 * @param footnote 각주 모양(표지와 번호 매기기). [endnote] 는 미주.
 */
internal class SectionInfo(
    val outlineNumbering: Int,
    val footnote: NoteSettings,
    val endnote: NoteSettings,
) {
    companion object {
        val DEFAULT = SectionInfo(1, NoteSettings.DEFAULT, NoteSettings.DEFAULT)
    }
}

/**
 * 본문을 걸어가며 **쌓이는** 상태. 조각의 시작에서 [copy] 로 찍어 두고 그 조각을 그릴 때 거기서 다시 걷는다 —
 * 뒤 조각의 번호가 앞 조각에서 이어진다. 이 목록에 없는 상태를 걷기에 더하면 조각의 경계에서 어긋난다.
 */
internal class WalkState(
    val numbers: HancomNumbers.Counters = HancomNumbers.Counters(),
    /** 우리가 센 각주·미주의 수(구역마다 새로 세는 문서는 그 구역의 `hp:secPr` 에서 0 이 된다). */
    var footnoteNo: Int = 0,
    var endnoteNo: Int = 0,
    /** 구역 안에서 문서 차례로 센 표의 번호. 훑기가 잰 칸 정보(`HwpxLayout.tables`)를 찾는 열쇠다. */
    var tableOrdinal: Int = 0,
    /** 변경 추적의 '지운 글' 안에 있다(`hp:deleteBegin` … `hp:deleteEnd`). 0 보다 크면 글을 보이지 않는다. */
    var deleting: Int = 0,
    /**
     * 문서 차례로 센 그림·묶음 개체의 번호. 훑기가 읽어 둔 설명문(`HwpxLayout.alts`)을 찾는 열쇠다 — `hp:shapeComment` 는
     * 그림(`hc:img`)보다 **뒤에** 적혀 있어 그리기가 `img` 를 쓸 때는 아직 모른다(표의 칸 정보와 같은 장치).
     */
    var objectOrdinal: Int = 0,
) {
    fun copy(): WalkState = WalkState(numbers.copy(), footnoteNo, endnoteNo, tableOrdinal, deleting, objectOrdinal)

    fun approxBytes(): Long = 64L + numbers.approxBytes()
}

/** 조각 하나 — 구역 [section] 의 최상위 문단 `[start, end)` 와 그 시작의 상태. */
internal class Chunk(val section: Int, val start: Int, val end: Int, val label: String, val state: WalkState)

/** 여는 동안 한 번 훑어 얻은 것. */
internal class HwpxLayout(
    val chunks: List<Chunk>,
    val outline: List<FlowOutline>,
    /** 책갈피 이름 → 조각 번호. 문서 안 링크가 다른 조각을 가리킬 때 쓴다. */
    val bookmarks: Map<String, Int>,
    /** (구역, 표 번호) → 칸마다의 정보([TableScan.result] 의 모양). 합치기·빈칸·겹친 칸이 없는 표는 없다. */
    val tables: Map<Long, IntArray>,
    val sections: List<SectionInfo>,
    /** (구역, 개체 번호 — [WalkState.objectOrdinal]) → 그림의 대체 글. 사람이 쓴 설명문만 있다(`HancomAlt`). */
    val alts: Map<Long, String> = emptyMap(),
) {
    companion object {
        val EMPTY = HwpxLayout(emptyList(), emptyList(), emptyMap(), emptyMap(), emptyList())

        fun tableKey(section: Int, ordinal: Int): Long = (section.toLong() shl 32) or (ordinal.toLong() and 0xFFFFFFFFL)

        /** 그림·묶음 개체의 열쇠. 모양은 [tableKey] 와 같다(번호가 따로 센 것일 뿐이다). */
        fun objectKey(section: Int, ordinal: Int): Long = tableKey(section, ordinal)
    }
}

/**
 * 한 문서를 읽는 동안 변하지 않는 것 — 훑기와 그리기가 함께 쓴다.
 *
 * @param features 버린 것의 집계. **훑기만 센다** — 그리기는 캐시에서 밀려난 조각을 다시 그리므로 거기서 세면
 *   같은 그림이 두 번 세어진다.
 */
internal class HwpxEnv(
    val pkg: HwpxPackage,
    val limits: ParseLimits,
    val header: HwpxHeader,
    val features: UnsupportedFeatures,
    val isDisplayableImage: (String) -> Boolean,
) {
    /** 머리말·꼬리말은 문서에 한 번만 센다. */
    var headerFooterRecorded = false

    /** 글자 CSS — 바탕이 밝은가(0·1)마다 (글자 모양, 문단 바탕 글자 모양) → CSS. 모두 합쳐 [MAX_CACHE] 개까지 든다. */
    private val charCss = Array(2) { HashMap<Long, String?>() }
    private var charCached = 0

    /** 문단 CSS — (바탕이 밝은가, 제목인가)(0..3)마다 (문단 모양, 문단 바탕 글자 모양) → CSS. 모두 합쳐 [MAX_CACHE] 개까지. */
    private val paraCss = Array(4) { HashMap<Long, String?>() }
    private var paraCached = 0

    /**
     * 글자 덩이의 CSS(**문단 바탕 글자 모양 [baseId] 와 다른 것만** — `HwpxWalker.runCss`). (모양, 바탕 모양, 바탕이 밝은가)마다
     * 한 번 만든다 — 밝은 바탕 위의 흰 글자는 색을 적지 않으므로 같은 모양도 바탕에 따라 CSS 가 둘이다.
     */
    fun charCss(charId: Int?, baseId: Int?, lightBackground: Boolean, build: (CharFormat, CharFormat) -> String?): String? {
        val map = charCss[if (lightBackground) 1 else 0]
        val key = pairKey(charId, baseId)
        if (map.containsKey(key)) return map[key]
        val v = build(header.char(charId), header.char(baseId))
        if (charCached < MAX_CACHE) {
            map[key] = v
            charCached++
        }
        return v
    }

    /**
     * 문단 요소의 CSS — 문단 모양과 **문단 바탕 글자 모양**(첫 글자 덩이의 것, `HwpxWalker.paraCss`). (문단 모양, 바탕 모양,
     * 바탕이 밝은가, 제목인가)마다 한 번 만든다.
     */
    fun paraCss(paraPrId: Int?, baseId: Int?, lightBackground: Boolean, heading: Boolean, build: (ParaFormat, CharFormat) -> String?): String? {
        val map = paraCss[(if (lightBackground) 1 else 0) or (if (heading) 2 else 0)]
        val key = pairKey(paraPrId, baseId)
        if (map.containsKey(key)) return map[key]
        val v = build(header.para(paraPrId), header.char(baseId))
        if (paraCached < MAX_CACHE) {
            map[key] = v
            paraCached++
        }
        return v
    }

    private companion object {
        const val MAX_CACHE = 100_000

        /** id 둘을 열쇠 하나로. 없는 id(null)는 -1 — 머리에 없는 id 와 같이 기본 모양이다. */
        fun pairKey(a: Int?, b: Int?): Long = ((a ?: -1).toLong() shl 32) or ((b ?: -1).toLong() and 0xFFFFFFFFL)
    }
}

/**
 * 훑기가 모으는 것 — 조각의 경계, 제목, 책갈피, 표의 칸 정보, 그림의 설명문, 구역 설정.
 *
 * 경계는 최상위 문단 하나를 **시작하기 직전**(문단 모양을 읽은 뒤, 번호를 세기 전)에 정한다. 그 자리에서 찍은
 * 상태가 그 조각을 그릴 때의 출발점이다.
 */
internal class ScanCollector(
    policy: HancomChunkPolicy,
    private val checkCancel: () -> Unit,
) {
    private class Heading(val section: Int, val block: Int, val level: Int, val text: String)
    private class Start(val section: Int, val block: Int, val state: WalkState)

    private val starts = ArrayList<Start>()

    /** 부분의 무게 — HWP 5.0 변환기와 같은 셈(`HancomChunkMeter`: 글자와 블록, 칸 하나가 블록 하나). */
    private val meter = HancomChunkMeter(policy)
    private var section = -1
    private val headings = ArrayList<Heading>()
    private val bookmarkAt = HashMap<String, Pair<Int, Int>>()
    private val tables = HashMap<Long, IntArray>()
    private var storedCells = 0
    private val alts = HashMap<Long, String>()
    private val sectionInfos = ArrayList<SectionInfo>()

    /** 구역 하나를 시작한다 — 언제나 새 조각이다(찍어 둘 상태의 예산과 관계없이). */
    fun beginSection(index: Int, state: WalkState) {
        section = index
        while (sectionInfos.size <= index) sectionInfos.add(SectionInfo.DEFAULT)
        meter.cut(state.approxBytes(), force = true)
        starts.add(Start(index, 0, state.copy()))
    }

    fun sectionInfo(info: SectionInfo) {
        if (section >= 0) sectionInfos[section] = info
    }

    fun onTopBlock(index: Int, heading: Boolean, state: WalkState) {
        if (meter.wantsCut(heading) && meter.cut(state.approxBytes())) starts.add(Start(section, index, state.copy()))
        meter.onBlock()
        if (index and 63 == 0) checkCancel()
    }

    fun addChars(n: Int) {
        meter.addChars(n)
    }

    /**
     * 표 칸 하나 — **블록 하나**로 센다(HWP 5.0 의 `addBlocks(칸 수)` 와 같다). 칸은 글자가 적어도 태그와 배치 비용이 크다 —
     * WebView 의 표 배치가 느린 쪽이다(12단계 실측: 칸 10만 개의 표가 배치에 45초).
     */
    fun addCell() {
        meter.addBlocks(1)
    }

    /** 제목 하나. 목차 줄과 부분의 이름은 HWP 5.0 과 같이 다듬는다(`HancomTitles` — 방향 문자를 버리고 200자까지). */
    fun heading(block: Int, level: Int, text: String) {
        if (headings.size >= MAX_OUTLINE) return
        val clean = HancomTitles.clean(text)
        if (clean.isNotEmpty()) headings.add(Heading(section, block, level, clean))
    }

    fun bookmark(name: String, block: Int) {
        if (bookmarkAt.size < MAX_BOOKMARKS) bookmarkAt.putIfAbsent(name, section to block)
    }

    /** 칸 정보가 필요한 표만 적는다. 문서 전체의 합에 상한을 둔다 — 넘으면 뒤의 표는 합치지 않고 그린다. */
    fun table(key: Long, cells: IntArray) {
        if (storedCells + cells.size > HwpxLimits.MAX_STORED_SPAN_CELLS * 2) return
        storedCells += cells.size
        tables[key] = cells
    }

    /** 그림·묶음 개체의 설명문([alt] 는 이미 `HancomAlt.of` 를 지난 것). 문서 전체의 수에 상한을 둔다. */
    fun alt(key: Long, alt: String) {
        if (alt.isEmpty() || alts.size >= HwpxLimits.MAX_STORED_ALTS) return
        alts[key] = alt
    }

    fun build(): HwpxLayout {
        // 조각 찾기: (구역, 문단) 이 속하는 마지막 시작.
        fun chunkOf(s: Int, block: Int): Int {
            var lo = 0
            var hi = starts.size - 1
            var found = -1
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                val st = starts[mid]
                if (st.section < s || (st.section == s && st.block <= block)) {
                    found = mid
                    lo = mid + 1
                } else {
                    hi = mid - 1
                }
            }
            return found.coerceAtLeast(0)
        }
        val labels = arrayOfNulls<String>(starts.size)
        val outline = ArrayList<FlowOutline>(headings.size)
        for (h in headings) {
            val c = chunkOf(h.section, h.block)
            if (labels[c] == null) labels[c] = h.text
            outline.add(FlowOutline(h.text, h.level.coerceIn(0, 8), c, HwpxIds.heading(h.section, h.block)))
        }
        val chunks = starts.indices.map { i ->
            val st = starts[i]
            val next = starts.getOrNull(i + 1)
            val end = if (next != null && next.section == st.section) next.block else Int.MAX_VALUE
            Chunk(st.section, st.block, end, labels[i].orEmpty(), st.state)
        }
        val bookmarks = HashMap<String, Int>(bookmarkAt.size)
        for ((name, at) in bookmarkAt) bookmarks[name] = chunkOf(at.first, at.second)
        return HwpxLayout(chunks, outline, bookmarks, tables, sectionInfos.toList(), alts)
    }

    companion object {
        private const val MAX_OUTLINE = 5_000
        private const val MAX_BOOKMARKS = 20_000
    }
}

/**
 * 표 하나의 칸 배치를 훑기에서 잰다.
 *
 * ## 왜 훑기에서 재는가
 *
 * `hp:tc` 의 자식 차례가 `hp:subList`(내용) → `hp:cellAddr` → `hp:cellSpan` 이다. 흘려 쓰는 그리기는 칸을 열
 * 때(`<td colspan rowspan>`) 합치기를 알아야 하는데 그 값은 **내용 뒤에** 온다. 표를 통째로 기억에 올리지 않으려고
 * 훑기가 칸마다의 값을 적어 두고 그리기는 읽기만 한다(docx 의 세로 합치기와 같은 장치).
 *
 * ## 무엇을 적는가
 *
 * HTML 의 표는 칸을 **차례로** 놓는다(위 행의 `rowspan` 이 차지한 자리를 건너뛰며). 한글은 칸마다 주소
 * (`cellAddr`)를 적는다. 둘이 어긋나는 자리를 고친다:
 *
 * * **빈 자리**(주소가 건너뛴 열) — 앞에 빈 칸(`td.gap`)을 넣는다.
 * * **겹친 칸**(이미 다른 칸이 차지한 자리) — 크기가 0 인 칸은 한글이 남긴 '비활성 칸' 이라 그리지 않는다
 *   (python-hwpx 의 판단). 크기가 있으면 글을 잃지 않으려고 합치기 없이 그린다.
 *
 * 한글이 쓴 표본의 표 400개에는 둘 다 없었다(가려진 칸은 아예 빠져 있다). 손으로 고친 파일을 위한 것이다.
 *
 * 칸 하나에 정수 둘 — `[colspan | gap << 16]`, `[rowspan | 세로 정렬 << 28 | skip << 31]`.
 *
 * **세로 정렬도 여기 적는다.** 칸의 `hp:subList/@vertAlign` 은 칸을 여는 태그보다 뒤에 와서 그리기가 `td` 를 쓸 때는
 * 아직 모른다. 가운데(한글의 기본, CSS 의 기본값)가 아닌 칸만 적는다 — 처음에는 적지 않아 K27 의 4,578칸 가운데 46칸
 * (위 41·아래 5)이 HWP 판과 달리 가운데로 그려졌다(13단계 짝 대조).
 */
internal class TableScan(colCount: Int) {
    private var busy = IntArray(colCount.coerceIn(1, HwpxLimits.MAX_SPAN))
    private var data = IntArray(32)
    private var count = 0
    private var interesting = false
    private var row = -1
    private var cursor = 0

    fun newRow() {
        row++
        cursor = 0
    }

    /**
     * 칸 하나(문서 차례). 주소를 몰라도(`cellAddr` 가 없다) 부른다 — 그때는 HTML 의 차례를 그대로 믿는다.
     */
    fun cell(colAddr: Int?, colSpan: Int, rowSpan: Int, zeroSize: Boolean, vAlign: Int = V_CENTER) {
        val cs = colSpan.coerceIn(1, HwpxLimits.MAX_SPAN)
        val rs = rowSpan.coerceIn(1, HwpxLimits.MAX_ROWS)
        var gap = 0
        var skip = false
        var drawSpan = true
        var place = nextFree(cursor)
        val want = colAddr?.takeIf { it in 0 until HwpxLimits.MAX_SPAN }
        if (want != null && want != place) {
            if (want > place && !occupied(want)) {
                // 빈 자리 — 차지되지 않은 열마다 빈 칸 하나.
                for (c in place until want) if (!occupied(c)) gap++
                place = want
            } else {
                // 겹친 칸 — 주소가 이미 지나갔거나 다른 칸이 차지한 자리다.
                skip = zeroSize
                drawSpan = false
            }
        }
        val va = vAlign.coerceIn(V_CENTER, V_BOTTOM)
        if (skip || gap > 0 || cs > 1 || rs > 1 || va != V_CENTER) interesting = true
        push(
            (if (drawSpan) cs else 1) or (gap.coerceAtMost(0x7FFF) shl 16),
            (if (drawSpan) rs else 1) or (va shl V_SHIFT) or (if (skip) Int.MIN_VALUE else 0),
        )
        if (!skip) {
            val span = if (drawSpan) cs else 1
            val down = if (drawSpan) rs else 1
            for (c in place until minOf(place + span, HwpxLimits.MAX_SPAN)) {
                ensure(c)
                busy[c] = maxOf(busy[c], row + down)
            }
            cursor = place + span
        }
    }

    private fun occupied(c: Int): Boolean = c < busy.size && busy[c] > row

    private fun nextFree(from: Int): Int {
        var c = from
        while (c < HwpxLimits.MAX_SPAN && occupied(c)) c++
        return c
    }

    private fun ensure(c: Int) {
        if (c >= busy.size) busy = busy.copyOf(minOf(maxOf(c + 1, busy.size * 2), HwpxLimits.MAX_SPAN))
    }

    private fun push(a: Int, b: Int) {
        if (count * 2 + 2 > data.size) data = data.copyOf(data.size * 2)
        data[count * 2] = a
        data[count * 2 + 1] = b
        count++
    }

    /** 적어 둘 것이 없으면 null(모든 칸이 1×1 이고 빈 자리·겹침이 없다). */
    fun result(): IntArray? = if (interesting) data.copyOf(count * 2) else null

    companion object {
        fun colSpan(cells: IntArray, i: Int): Int = if (i * 2 < cells.size) cells[i * 2] and 0xFFFF else 1
        fun gap(cells: IntArray, i: Int): Int = if (i * 2 < cells.size) cells[i * 2] ushr 16 else 0
        fun rowSpan(cells: IntArray, i: Int): Int = if (i * 2 + 1 < cells.size) cells[i * 2 + 1] and ROWSPAN_MASK else 1
        fun skip(cells: IntArray, i: Int): Boolean = i * 2 + 1 < cells.size && cells[i * 2 + 1] < 0

        /** 칸의 세로 정렬([V_CENTER]·[V_TOP]·[V_BOTTOM]). */
        fun vAlign(cells: IntArray, i: Int): Int = if (i * 2 + 1 < cells.size) (cells[i * 2 + 1] ushr V_SHIFT) and 3 else V_CENTER

        /** `hp:subList/@vertAlign` 을 우리 값으로. 모르는 값은 가운데. */
        fun vAlignOf(raw: String?): Int = when (raw?.uppercase()) {
            "TOP" -> V_TOP
            "BOTTOM" -> V_BOTTOM
            else -> V_CENTER
        }

        const val V_CENTER = 0
        const val V_TOP = 1
        const val V_BOTTOM = 2

        /** `rowspan` 은 [HwpxLimits.MAX_ROWS](5,000)로 묶여 28비트 안에 든다. */
        private const val V_SHIFT = 28
        private const val ROWSPAN_MASK = (1 shl V_SHIFT) - 1
    }
}
