package io.github.donggi.iroiroviewer.format.hwp5

import io.github.donggi.iroiroviewer.format.FlowOutline
import io.github.donggi.iroiroviewer.format.html.FlowDocumentBase
import io.github.donggi.iroiroviewer.format.html.HancomChunkMeter
import io.github.donggi.iroiroviewer.format.html.HancomChunkPolicy
import io.github.donggi.iroiroviewer.format.html.HancomNumbers
import io.github.donggi.iroiroviewer.format.html.HancomTitles
import io.github.donggi.iroiroviewer.format.html.HtmlWriter

/** 여는 설정. 기본값이 제품의 값이고, 시험만 바꾼다. */
internal data class Hwp5Options(
    /**
     * 긴 구역을 부분으로 나누는 기준 — HWPX 변환기와 같은 것이다([HancomChunkPolicy]). **구역이 바뀌면 언제나 끊는다**(구역은
     * 한글이 쪽 설정을 바꾸는 단위이고, 조각 하나가 구역 둘에 걸치지 않아야 다시 그릴 때 한 스트림만 푼다).
     */
    val chunk: HancomChunkPolicy = HancomChunkPolicy(),
    /** 조각 하나의 HTML 상한(글자). */
    val maxChars: Int = HtmlWriter.DEFAULT_MAX_CHARS,
    /**
     * 그림 하나를 `img` 로 적는 상한(풀린 바이트). 바탕이 그림을 내주는 상한([FlowDocumentBase.MAX_RESOURCE_BYTES])과 같아야
     * 한다 — 넘는 그림은 바탕이 내주지 않아 깨진 그림만 뜬다. 시험만 작게 한다(큰 그림을 만들지 않고 두 길을 보려고).
     */
    val maxImageBytes: Long = FlowDocumentBase.MAX_RESOURCE_BYTES,
)

/**
 * 각주/미주 모양(`HWPTAG_FOOTNOTE_SHAPE`, 명세 표 133) — 번호의 모양·사용자 기호·앞뒤 장식·시작 번호·번호 매기기(이어서/구역마다).
 * 알맹이는 `UINT32 속성, WCHAR 사용자 기호, WCHAR 앞 장식, WCHAR 뒤 장식, UINT16 시작 번호 …` 다. 실물은 앞 장식 없이 뒤에
 * `)` 를 붙인다(`1)`).
 *
 * 모양은 [HancomNumbers] 의 값이다 — 사용자 기호(0x81)·네 글자 되풀이(0x80)도 남긴다. 처음에는 16 을 넘는 모양을 숫자로
 * 바꾸고 사용자 기호를 읽지 않아, `*` 로 적은 각주가 HWP 로만 숫자로 보였다(13단계 짝 대조).
 */
internal class NoteShape(
    val shape: Int,
    val userChar: String,
    val prefix: String,
    val suffix: String,
    val start: Int,
    /** 0 앞 구역에 이어서, 1 구역마다 새로, 2 쪽마다 새로(흐름에는 쪽이 없어 '이어서' 로 본다). */
    val numbering: Int,
) {
    /** [n] 번째(1부터) 주석의 표지. 보이는 번호는 시작 번호에서 센다. */
    fun label(n: Int): String = HancomNumbers.noteLabel(n + start - 1, shape, userChar, prefix, suffix)

    companion object {
        val DEFAULT = NoteShape(HancomNumbers.DIGIT, "", "", ")", 1, 0)

        fun parse(data: ByteArray, payload: Int, size: Int): NoteShape {
            if (size < 12) return DEFAULT
            val attr = data.u32(payload)
            fun ch(o: Int): String =
                data.u16(payload + o).takeIf { it in 0x20..0xFFFF && it !in 0xD800..0xDFFF }?.toChar()?.toString().orEmpty()
            return NoteShape(
                shape = HancomNumbers.shapeOf((attr and 0xFF).toInt()),
                userChar = ch(4),
                prefix = ch(6),
                suffix = ch(8),
                start = data.u16(payload + 10).coerceIn(0, 100_000),
                numbering = ((attr ushr 10) and 3L).toInt(),
            )
        }
    }
}

/**
 * 구역 정의(`secd`)가 정하는 것. 구역의 첫 문단에 있으므로 구역 중간에서 시작하는 조각은 이것을 상태로 이어받는다.
 *
 * @param outlineNumbering 개요 문단이 쓰는 번호 정의(1부터). 개요 문단의 문단 모양은 번호 칸이 0 이다 — 번호는 구역이
 *   준다(표 129 의 '번호 문단 모양 ID').
 */
internal class SectionSettings(val outlineNumbering: Int, val foot: NoteShape, val end: NoteShape) {
    companion object {
        val DEFAULT = SectionSettings(1, NoteShape.DEFAULT, NoteShape.DEFAULT)
    }
}

/**
 * 본문을 걸어가며 **쌓이는** 상태. 조각의 시작에서 [copy] 로 찍어 두고, 그 조각을 그릴 때 거기서 다시 걷는다 —
 * 뒤 조각의 번호 문단·각주 번호가 앞 조각에서 이어진다. 이 목록에 없는 상태를 걷기에 더하면 조각의 경계에서 어긋난다.
 */
internal class WalkState(
    val numbers: HancomNumbers.Counters = HancomNumbers.Counters(),
    var footnoteNo: Int = 0,
    var endnoteNo: Int = 0,
    var section: SectionSettings = SectionSettings.DEFAULT,
) {
    fun copy(): WalkState = WalkState(numbers.copy(), footnoteNo, endnoteNo, section)

    fun approxBytes(): Long = 64L + numbers.approxBytes()
}

/**
 * 조각 하나 — 구역 [section] 의 최상위 문단 `[startOrdinal, endOrdinal)`.
 *
 * @param startOffset 첫 문단 머리의 자리(구역 바이트 안). 그릴 때 앞 문단을 건너뛰지 않고 거기서 바로 시작한다.
 * @param globalStart 문서 전체에서 센 첫 문단의 번호. 본문의 `id`(`p-번호`)가 이것으로 정해진다.
 */
internal class Chunk(
    val section: Int,
    val startOrdinal: Int,
    val startOffset: Int,
    val endOrdinal: Int,
    val globalStart: Int,
    val label: String,
    val state: WalkState,
)

/**
 * 여는 동안 한 번 훑어 얻은 것.
 *
 * @param anchorBlocks `id` 를 받는 최상위 문단(제목과, 문서 안 링크가 가리키는 문단).
 * @param linkTargets 인스턴스 번호 → 최상위 문단. 한글의 문서 안 링크(`%hlk`·`%xrf`)는 책갈피 이름이 아니라 **문단(또는
 *   표)의 인스턴스 번호**를 가리킨다 — 명세 S01 의 차례 94개가 `?#645989673` 꼴로 문단 머리의 인스턴스 번호를 가리켰다(13단계 실측).
 * @param bookmarks 책갈피 이름 → 최상위 문단.
 */
internal class Hwp5Layout(
    val chunks: List<Chunk>,
    val outline: List<FlowOutline>,
    val anchorBlocks: Set<Int>,
    val linkTargets: Map<Long, Int>,
    val bookmarks: Map<String, Int>,
) {
    private val starts = IntArray(chunks.size) { chunks[it].globalStart }

    /** 최상위 문단 [global] 이 든 조각. */
    fun partOf(global: Int): Int {
        var lo = 0
        var hi = starts.size - 1
        if (hi < 0) return 0
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (starts[mid] <= global) lo = mid else hi = mid - 1
        }
        return lo
    }

    companion object {
        val EMPTY = Hwp5Layout(emptyList(), emptyList(), emptySet(), emptyMap(), emptyMap())

        /** 최상위 문단의 `id`. */
        fun anchorId(global: Int): String = "p-$global"
    }
}

/**
 * 훑기가 모으는 것 — 조각의 경계, 제목, 문서 안 링크의 대상, 책갈피.
 *
 * 경계는 최상위 문단 하나를 **시작하기 직전**(문단 모양을 읽은 뒤, 번호를 세기 전)에 정한다. 그 자리에서 찍은 상태가
 * 그 조각을 그릴 때의 출발점이 된다. 무게는 HWPX 변환기와 같은 저울([HancomChunkMeter])로 잰다 — 칸 하나가 블록 하나다.
 */
internal class ScanCollector(
    policy: HancomChunkPolicy,
    private val checkCancel: () -> Unit,
) {
    private class Start(val section: Int, val ordinal: Int, val offset: Int, val global: Int, val state: WalkState)
    private class Heading(val global: Int, val level: Int, val text: String)

    private val meter = HancomChunkMeter(policy)
    private val starts = ArrayList<Start>()
    private var lastSection = -1
    private val headings = ArrayList<Heading>()
    private val instances = HashMap<Long, Int>()
    private val targets = HashSet<Long>()
    private val bookmarkBlocks = HashMap<String, Int>()

    /** 지금까지 본 최상위 문단 수. */
    var topBlocks = 0
        private set

    fun onTopBlock(section: Int, ordinal: Int, offset: Int, global: Int, heading: Boolean, state: WalkState) {
        topBlocks++
        val newSection = section != lastSection
        // 구역의 경계는 예산과 관계없이 끊는다(`force`) — 조각이 구역 둘에 걸치지 않는다는 약속이 먼저다. 구역은 많아야 256.
        val cut = if (newSection) {
            meter.cut(state.approxBytes(), force = true)
        } else {
            meter.wantsCut(heading) && meter.cut(state.approxBytes())
        }
        if (cut) starts.add(Start(section, ordinal, offset, global, state.copy()))
        lastSection = section
        meter.onBlock()
        if (global and 63 == 0) checkCancel()
    }

    fun addChars(n: Int) {
        meter.addChars(n)
    }

    /** 최상위 문단 밖의 블록(표의 칸)을 조각의 무게에 더한다. 경계는 여전히 최상위 문단 앞에서만 생긴다. */
    fun addBlocks(n: Int) {
        meter.addBlocks(n)
    }

    /**
     * 제목 하나. 글은 목차 줄과 **부분의 이름**이 되고, 부분의 이름은 알림 문장에도 들어간다(`partName` → 경고의 `detail`).
     * 다듬기는 HWPX 변환기와 한 규칙이다([HancomTitles.clean] — 방향을 바꾸는 문자를 버리고, 제어 문자는 공백으로, 200자까지).
     */
    fun heading(global: Int, level: Int, text: String) {
        if (headings.size >= Hwp5Limits.MAX_OUTLINE) return
        val clean = HancomTitles.clean(text)
        if (clean.isNotEmpty()) headings.add(Heading(global, level, clean))
    }

    /** 문단·개체의 인스턴스 번호가 이 최상위 문단 안에 있다. */
    fun instance(id: Long, global: Int) {
        if (id <= 0 || global < 0 || instances.size >= Hwp5Limits.MAX_INSTANCE_IDS) return
        instances.putIfAbsent(id, global)
    }

    /** 문서 안 링크가 이 인스턴스 번호를 가리킨다. */
    fun linkTarget(id: Long) {
        if (targets.size < Hwp5Limits.MAX_LINK_TARGETS) targets.add(id)
    }

    fun bookmark(name: String, global: Int) {
        if (global >= 0 && bookmarkBlocks.size < Hwp5Limits.MAX_BOOKMARKS) bookmarkBlocks.putIfAbsent(name, global)
    }

    fun build(): Hwp5Layout {
        val chunkGlobals = IntArray(starts.size) { starts[it].global }
        fun chunkOf(global: Int): Int {
            var lo = 0
            var hi = chunkGlobals.size - 1
            while (lo < hi) {
                val mid = (lo + hi + 1) ushr 1
                if (chunkGlobals[mid] <= global) lo = mid else hi = mid - 1
            }
            return lo
        }
        val labels = arrayOfNulls<String>(starts.size)
        val outline = ArrayList<FlowOutline>(headings.size)
        val anchors = HashSet<Int>()
        for (h in headings) {
            val c = chunkOf(h.global)
            if (labels[c] == null) labels[c] = h.text
            outline.add(FlowOutline(h.text, h.level.coerceIn(0, 8), c, Hwp5Layout.anchorId(h.global)))
            anchors.add(h.global)
        }
        val links = HashMap<Long, Int>()
        for (id in targets) {
            val g = instances[id] ?: continue
            links[id] = g
            anchors.add(g)
        }
        for (g in bookmarkBlocks.values) anchors.add(g)
        val chunks = starts.indices.map { i ->
            val s = starts[i]
            val next = starts.getOrNull(i + 1)
            val end = if (next != null && next.section == s.section) next.ordinal else Int.MAX_VALUE
            Chunk(s.section, s.ordinal, s.offset, end, s.global, labels[i].orEmpty(), s.state)
        }
        return Hwp5Layout(chunks, outline, anchors, links, HashMap(bookmarkBlocks))
    }
}
