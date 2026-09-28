package io.github.donggi.iroiroviewer.format.docx

import io.github.donggi.iroiroviewer.format.FlowOutline
import io.github.donggi.iroiroviewer.format.UnsupportedFeatures
import io.github.donggi.iroiroviewer.format.opc.OpcPackage
import io.github.donggi.iroiroviewer.format.opc.OpcRelationship
import io.github.donggi.iroiroviewer.safety.ParseLimits

/**
 * 긴 문서를 조각(part)으로 나누는 기준. 글자 수가 기준을 넘으면 **다음 제목 바로 앞**에서 끊고,
 * 제목이 오지 않으면 두 배쯤에서 아무 블록 앞에서나 끊는다. 대부분의 문서는 한 조각이다.
 *
 * 시험이 작은 값으로 조각 나누기를 태울 수 있게 값으로 둔다.
 */
internal data class ChunkPolicy(
    val softChars: Int = 150_000,
    val softBlocks: Int = 1_500,
    val hardChars: Int = 300_000,
    val hardBlocks: Int = 3_000,
    /**
     * 조각마다 찍어 두는 시작 상태([WalkState.approxBytes])의 합의 상한. 넘으면 **더 끊지 않는다** — 남은
     * 본문은 마지막 조각 하나가 되고, 그리기의 글자 상한이 자른다(`FlowWarnings.TRUNCATED`).
     *
     * 상태에는 문서가 쓴 목록마다의 번호가 들어 있어서, 목록 수천 개를 쓴 뒤 빈 문단 수백만 개로 조각
     * 수백 개를 만드는 6MB 짜리 문서가 조각마다 1MB 넘게 복사하게 해 여는 동안 기억을 다 쓴다(실측).
     */
    val snapshotBudget: Long = 16L * 1024 * 1024,
)

/** 여는 설정. 기본값이 제품의 값이고, 시험만 바꾼다. */
internal data class DocxOptions(
    val chunk: ChunkPolicy = ChunkPolicy(),
    /** 조각 하나의 HTML 상한(글자). */
    val maxChars: Int = io.github.donggi.iroiroviewer.format.html.HtmlWriter.DEFAULT_MAX_CHARS,
)

/** 각주·미주의 번호 모양(`w:settings` 의 `w:footnotePr`·`w:endnotePr`). */
internal class NoteFormat(val numFmt: String, val start: Int) {
    fun label(n: Int): String = ListMarkers.format(n + start - 1, numFmt)
}

/**
 * 한 문서를 읽는 동안 변하지 않는 것들 — 훑기와 그리기가 함께 쓴다.
 *
 * @param features 버린 것의 집계. **훑기만 센다**([WalkConfig.recordFeatures]) — 그리기는 캐시에서
 *   밀려난 조각을 다시 그리므로 거기서 세면 같은 그림이 두 번 세어진다.
 */
internal class DocxEnv(
    val pkg: OpcPackage,
    val limits: ParseLimits,
    val main: String,
    val styles: DocxStyles,
    val numbering: DocxNumbering,
    val footnotesPart: String?,
    val endnotesPart: String?,
    val footnoteFormat: NoteFormat,
    val endnoteFormat: NoteFormat,
    val features: UnsupportedFeatures,
    val isDisplayableImage: (String) -> Boolean,
    /** 메모 부분에서 읽은 것([DocxComments]). 없거나 읽지 못했으면 [DocxComments.NONE]. */
    val comments: DocxComments = DocxComments.NONE,
) {
    /** 머리글·바닥글은 문서에 한 번만 센다. */
    var headerFooterRecorded = false

    /**
     * 훑기가 만난(본문·각주에서 가리킨) 메모의 `w:id`. 훑기는 **이것들의 본문만** 걸어 버린 것을 센다 — 가리키지 않은 메모는
     * 워드도 보이지 않는다.
     */
    val referencedComments = HashSet<Int>()

    private val relationshipMaps = HashMap<String, Map<String, OpcRelationship>>()

    /**
     * 부분 하나의 관계를 id 로 찾는 표. **부분마다 한 번만** 짓는다 — 각주는 하나마다 걷기를 새로 만드는데,
     * 걷기마다 지으면 각주 수 × 관계 수(각각 만·오만까지)가 되어 조각 하나를 그리는 데 수십 초가 걸린다.
     * 부르는 쪽이 모두 문서의 잠금 안에 있어 동기화하지 않는다.
     */
    fun relationshipsOf(part: String): Map<String, OpcRelationship> =
        relationshipMaps.getOrPut(part) { pkg.relationships(part).associateBy { it.id } }
}

/**
 * 복합 필드(`w:fldChar` begin → 명령 → separate → 결과 → end) 하나. 결과만 보인다.
 *
 * @param link 이 필드가 연 태그(`a` 또는 `span`). 문단이 끝나면 저절로 닫히므로 문단마다 지운다.
 */
internal class FieldFrame(var result: Boolean = false, val instr: StringBuilder = StringBuilder()) {
    var link: String? = null
    var linkGen: Int = -1

    fun copy(): FieldFrame = FieldFrame(result, StringBuilder(instr))
}

/**
 * 본문을 걸어가며 **쌓이는** 상태. 조각의 시작에서 [copy] 로 찍어 두고, 그 조각을 그릴 때 거기서
 * 다시 걷기 시작한다 — 그래서 뒤 조각의 목록 번호·각주 번호가 앞 조각에서 이어진다.
 *
 * 이 목록에 없는 상태를 걷기에 더하면 조각의 경계에서 어긋난다. 더할 때는 여기에 더한다.
 */
internal class WalkState(
    val lists: ListCounters = ListCounters(),
    var footnoteNo: Int = 0,
    var endnoteNo: Int = 0,
    /** 문서 순서로 센 표의 번호. 훑기가 계산한 행 합치기([ScanCollector.tableSpans])를 찾는 열쇠다. */
    var tableOrdinal: Int = 0,
    val fields: ArrayList<FieldFrame> = ArrayList(),
    /** 깊이 상한 때문에 받지 않은 필드 시작의 수. 짝이 되는 끝도 받지 않는다. */
    var ignoredFieldBegins: Int = 0,
    /** 문서 순서로 센 본문의 SmartArt 번호. 훑기가 읽어 둔 글([ScanCollector.smartArt])을 찾는 열쇠다. */
    var smartArtOrdinal: Int = 0,
) {
    fun copy(): WalkState = WalkState(
        lists.copy(), footnoteNo, endnoteNo, tableOrdinal,
        ArrayList(fields.map { it.copy() }), ignoredFieldBegins, smartArtOrdinal,
    )

    /**
     * [copy] 하나가 차지하는 대략의 바이트. 조각 경계의 상한([ChunkPolicy.snapshotBudget])이 쓴다.
     * 상태를 더할 때 크기가 문서에 달린 것이면 여기에도 더한다.
     */
    fun approxBytes(): Long {
        var n = 64L + lists.approxBytes()
        for (f in fields) n += 64L + 2L * f.instr.length
        return n
    }
}

/** 조각 하나 — 최상위 블록 `[start, end)` 와 그 시작의 상태. */
internal class Chunk(val start: Int, val end: Int, val label: String, val state: WalkState)

/** 여는 동안 한 번 훑어 얻은 것. */
internal class DocxLayout(
    val title: String,
    val chunks: List<Chunk>,
    val outline: List<FlowOutline>,
    /** 책갈피 이름 → 조각 번호. 문서 안 링크(`w:anchor`)가 다른 조각을 가리킬 때 쓴다. */
    val bookmarks: Map<String, Int>,
    val tableSpans: Map<Int, IntArray>,
    /** SmartArt 번호([WalkState.smartArtOrdinal]) → 캐시된 그림에서 읽은 글. 읽지 못한 것은 없다(SmartArt 로 셌다). */
    val smartArts: Map<Int, SmartArtText> = emptyMap(),
) {
    companion object {
        val EMPTY = DocxLayout("", emptyList(), emptyList(), emptyMap(), emptyMap())
    }
}

/**
 * 훑기가 모으는 것 — 조각의 경계, 제목, 책갈피, 표의 행 합치기, SmartArt 의 글.
 *
 * 경계는 최상위 블록 하나를 **시작하기 직전**(문단이면 속성을 읽은 뒤, 번호를 세기 전)에 정한다.
 * 그 자리에서 찍은 상태가 그 조각을 그릴 때의 출발점이 된다.
 */
internal class ScanCollector(
    private val policy: ChunkPolicy,
    initial: WalkState,
    private val checkCancel: () -> Unit,
) {
    private class Heading(val block: Int, val level: Int, val text: String)

    private val starts = arrayListOf(0)
    private val snapshots = arrayListOf(initial.copy())
    private var snapshotBytes = initial.approxBytes()
    private var saturated = false
    private var chars = 0L
    private var blocks = 0
    private val headings = ArrayList<Heading>()
    private val bookmarkBlocks = HashMap<String, Int>()
    private val spans = HashMap<Int, IntArray>()
    private var storedSpanCells = 0
    private val smartArts = HashMap<Int, SmartArtText>()
    private var storedSmartArtBytes = 0L

    fun onTopBlock(index: Int, heading: Boolean, state: WalkState) {
        val cut = !saturated && blocks > 0 && (
            (heading && (chars >= policy.softChars || blocks >= policy.softBlocks)) ||
                chars >= policy.hardChars || blocks >= policy.hardBlocks
            )
        if (cut) {
            val cost = state.approxBytes()
            if (snapshotBytes + cost > policy.snapshotBudget) {
                // 상태의 대부분인 목록 번호는 쌓이기만 한다. 한 번 넘으면 끝까지 끊지 않는다.
                saturated = true
            } else {
                snapshotBytes += cost
                starts.add(index)
                snapshots.add(state.copy())
                chars = 0
                blocks = 0
            }
        }
        blocks++
        if (index and 63 == 0) checkCancel()
    }

    fun addChars(n: Int) {
        chars += n
    }

    fun heading(block: Int, level: Int, text: String) {
        if (headings.size >= MAX_OUTLINE) return
        val clean = text.replace('\t', ' ').replace('\n', ' ').trim().replace(SPACES, " ").take(MAX_TITLE)
        if (clean.isNotEmpty()) headings.add(Heading(block, level, clean))
    }

    fun bookmark(name: String, block: Int) {
        if (bookmarkBlocks.size < MAX_BOOKMARKS) bookmarkBlocks.putIfAbsent(name, block)
    }

    /** 행 합치기가 있는 표만 적는다. 문서 전체의 합에 상한을 둔다 — 넘으면 뒤의 표는 합치지 않고 그린다. */
    fun tableSpans(ordinal: Int, cellSpans: IntArray) {
        if (storedSpanCells + cellSpans.size > MAX_SPAN_CELLS) return
        storedSpanCells += cellSpans.size
        spans[ordinal] = cellSpans
    }

    /**
     * SmartArt 하나의 글을 적어 둔다. 문서 전체의 **무게**([SmartArtText.approxBytes])에 상한을 둔다 — 넘으면 적지 않고
     * 거짓(부르는 쪽이 SmartArt 로 센다). 글자 수가 아니라 무게로 재는 까닭은 그 함수의 주석. 같은 그림을 여러 번 가리키면
     * 그때마다 센다(넉넉한 쪽으로 틀린다). 그리기는 여기 적힌 것만 그리므로 훑기의 셈과 그리기의 모양이 어긋나지 않는다.
     */
    fun smartArt(ordinal: Int, art: SmartArtText): Boolean {
        val cost = art.approxBytes()
        if (storedSmartArtBytes + cost > MAX_SMART_ART_BYTES) return false
        storedSmartArtBytes += cost
        smartArts[ordinal] = art
        return true
    }

    fun build(title: String): DocxLayout {
        val chunkOf = { block: Int ->
            var lo = 0
            var hi = starts.size - 1
            while (lo < hi) {
                val mid = (lo + hi + 1) ushr 1
                if (starts[mid] <= block) lo = mid else hi = mid - 1
            }
            lo
        }
        val labels = arrayOfNulls<String>(starts.size)
        val outline = ArrayList<FlowOutline>(headings.size)
        for (h in headings) {
            val c = chunkOf(h.block)
            if (labels[c] == null) labels[c] = h.text
            outline.add(FlowOutline(h.text, h.level.coerceIn(0, 8), c, DocxIds.heading(h.block)))
        }
        val chunks = starts.indices.map { i ->
            val end = if (i + 1 < starts.size) starts[i + 1] else Int.MAX_VALUE
            Chunk(starts[i], end, labels[i].orEmpty(), snapshots[i])
        }
        val bookmarks = HashMap<String, Int>(bookmarkBlocks.size)
        for ((name, block) in bookmarkBlocks) bookmarks[name] = chunkOf(block)
        return DocxLayout(title, chunks, outline, bookmarks, spans, smartArts)
    }

    companion object {
        private const val MAX_OUTLINE = 5_000
        private const val MAX_TITLE = 200
        private const val MAX_BOOKMARKS = 20_000
        private const val MAX_SPAN_CELLS = 2_000_000

        /**
         * 적어 두는 SmartArt 글의 무게 합(바이트, [SmartArtText.approxBytes]). 글이 꽉 찬 것([DocxSmartArt.MAX_CHARS])도 200개쯤
         * 들고, 사람이 만든 수백 자짜리는 문서당 개수 상한(`DocxWalker.MAX_SMART_ARTS`)까지 다 든다.
         */
        const val MAX_SMART_ART_BYTES = 8L * 1024 * 1024
        private val SPACES = Regex("\\s{2,}")
    }
}
