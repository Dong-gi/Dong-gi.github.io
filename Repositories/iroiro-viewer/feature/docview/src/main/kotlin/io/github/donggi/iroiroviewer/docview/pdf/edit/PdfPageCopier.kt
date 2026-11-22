package io.github.donggi.iroiroviewer.docview.pdf.edit

import io.github.donggi.iroiroviewer.docview.pdf.PdfOutline
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfArray
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfCryptRules
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfDict
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfFile
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfName
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfNull
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfNum
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfObj
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfRef
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfSecurity
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfSink
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfSource
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfStr
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfSyntaxException
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfWeights
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfWriter
import io.github.donggi.iroiroviewer.docview.pdf.crypt.UnsupportedEncryption
import io.github.donggi.iroiroviewer.docview.pdf.text.PdfNavigation
import io.github.donggi.iroiroviewer.docview.pdf.text.PdfObjects
import io.github.donggi.iroiroviewer.docview.pdf.text.PdfTextEngine
import java.io.ByteArrayOutputStream
import java.util.IdentityHashMap

/**
 * PDF 의 **쪽을 골라 새 PDF 로** 옮겨 쓴다 — 쪽 뽑기(원본 하나)와 합치기(원본 여럿)가 같은 길이다.
 *
 * ## 무엇을 옮기는가
 *
 * 고른 쪽 객체에서 시작해 **간접 참조를 따라 닿는 객체만** 옮긴다(자원·글꼴·그림·주석 …). 번호는 1부터 새로 매긴다 — 원본마다 번호가
 * 겹치므로 합치기에서 충돌이 없다. 쪽 나무는 새로 짓는다(뿌리 하나에 쪽을 차례로 — `/Parent`·`/Kids`·`/Count`). 쪽이 물려받는 넷
 * (`/Resources`·`/MediaBox`·`/CropBox`·`/Rotate`)은 쪽에 펴 넣는다 — 옛 쪽 나무를 옮기지 않으므로 물려받을 곳이 없다.
 *
 * **따라가지 않는 참조** — 쪽 나무의 마디(빠진 쪽·중간 마디)와 문서 뿌리(`/Catalog`)를 가리키는 것은 `null` 로 적는다. 링크의 목적지·
 * 주석의 `/P` 가 빠진 쪽을 가리키면 그 쪽을 따라 옛 쪽 나무 전체(빠진 쪽의 내용까지)가 딸려 온다. 옮긴 객체가 쪽(`/Type /Page`·`/Pages`)이면
 * 그 자리에도 `null` 을 적는다(나무에 없는 고아 쪽).
 *
 * | 무엇 | 정한 것 |
 * |---|---|
 * | 목적지(`/Dest`·`/A` GoTo — 링크·위젯 어디서 오든) | 쪽을 적은 배열로 풀어(이름 목적지도) **남은 쪽이면 그 배열로** 다시 적고, 빠진 쪽이거나 풀지 못하면 그 목적지를 뗀다. 그런 **링크 주석은 쪽에서 지운다** — 눌러도 아무 데로도 가지 않는 링크를 남기지 않는다. 다시 적는 일은 객체를 쓰는 자리에서 한다(쪽보다 먼저 다른 주석의 `/IRT` 로 끌려온 링크도 같다) |
 * | 동작 | **바깥 주소(URI)·이름 동작(Named)·GoTo 만** 옮긴다. 자바스크립트·`/Launch`·다른 파일(GoToR)·양식 제출 같은 나머지 동작은 떼고, 뒤에 잇는 동작(`/Next`)과 추가 동작(`/AA` — 쪽·주석의 열기·닫기·마우스, 자바스크립트가 주로 여기 산다)은 옮기지 않는다 |
 * | 목차(`/Outlines`) | **남은 쪽을 가리키는 항목만** 새로 짓는다(빠진 쪽의 항목은 버리고 그 아이는 위로 올린다). 목적지는 그 쪽의 맞춤(`/Fit`)이다 — 자리·색·굵기는 옮기지 않는다. 합치기면 원본마다 파일 이름의 항목을 위에 두고 그 아래에 둔다 |
 * | 양식(`/AcroForm`)·구조 트리(태그)·기사 흐름(`/B`)·이름 나무·문서 정보(`/Info`)·문서 자바스크립트·첨부 | 옮기지 않는다. 위젯은 보이는 모양(`/AP`)으로만 남는다 — 위젯의 `/Parent`(필드 나무)는 떼어 다른 쪽의 위젯이 딸려 오지 않게 한다. **첨부 주석(`FileAttachment`)은 쪽에서 지운다**(끌려와도 `/FS` 를 뗀다) |
 * | 선택 내용(`/OCProperties`) | 옮긴다(원본 하나면 그대로, 여럿이면 `/OCGs` 와 꺼 둔 것 `/OFF` 만 모아서) — 없으면 꺼 두었던 층이 보인다 |
 * | 압축된 스트림 | **압축된 채로** 옮긴다(풀지 않는다). 잠긴 문서는 암호만 벗긴다 |
 *
 * ## 암호 — 결과는 언제나 잠기지 않은 PDF 다(사용자 2026-11-22)
 *
 * 소유자 암호만 걸린 문서는 빈 사용자 암호로, 사용자 암호 문서는 부르는 쪽이 준 암호([Source.password] — 그 문서를 열 때 넣은 세션
 * 암호)로 **우리 복호화기가 풀어** 평문으로 쓴다. 암호가 없거나 틀리면 [Refusal.LOCKED], 암호로는 열 수 없는 방식(인증서·전용 처리기)은
 * [Refusal.LOCK_UNSUPPORTED]. 암호 배열은 부르는 쪽의 것이다 — 여기서는 덮지 않고, 세운 보안 처리기(문서 열쇠)는 끝에서 덮는다.
 *
 * ## 막는 것
 *
 * 임의의 사용자 파일이다 — 객체 그래프에 고리(방문 표로 한 번만), 쪽 나무의 고리·겹친 직접 사전(방문 마디 수 — `PdfTextEngine.MAX_TREE_VISITS`),
 * 수백만 객체([Limits.maxObjects]), 수만 쪽([Limits.maxPages]), 결과 크기([Limits.maxOutputBytes]), 읽는 일([readBudget] —
 * 캐시가 무게로 묶이고 다시 푼 것까지 센다), 쪽마다 펼칠 뻔한 공유 `/Annots`(간접 배열은 결과에서도 한 객체로 나눠 쓴다). 해석기의
 * 상한(깊이·문자열·배열)은 [io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfParser] 의 것이다. 객체마다·스트림 64 KiB 마다·나무 마디
 * 256 개마다 취소를 본다. 메모리가 모자라거나 스택이 넘치면 [Refusal.TOO_BIG], 그 밖의 해석 실패는 [Refusal.UNREADABLE] 이다(취소와
 * 쓰는 곳의 입출력 예외는 그대로 올라간다).
 */
internal class PdfPageCopier private constructor(
    private val sink: PdfSink,
    private val limits: Limits,
    private val checkCancelled: () -> Unit,
) {

    /** 원본 하나 — 무엇을 어디서 가져오는가. */
    class Source(
        /** 목차에 적는 이름(합치기의 파일 항목). */
        val title: String,
        val src: PdfSource,
        /** 0부터의 쪽 번호, 오름차순·겹침 없이. null 이면 전부. */
        val pages: List<Int>?,
        /** 화면(플랫폼)이 센 쪽 수. 우리가 센 것과 다르면 고른 쪽이 다른 쪽일 수 있어 거절한다. null 이면 보지 않는다. */
        val expectedCount: Int? = null,
        /** 사용자 암호(세션 암호). 부르는 쪽의 배열이다 — 여기서 덮지 않는다. */
        val password: CharArray? = null,
    )

    data class Limits(
        val maxSources: Int = 100,
        val maxPages: Int = 20_000,
        val maxObjects: Int = 500_000,
        val maxOutputBytes: Long = 4L * 1024 * 1024 * 1024,
        val maxOutlineEntries: Int = 50_000,
        /** 원본마다 읽는 예산 — [readBudget]. */
        val readBudget: Long = PdfPageCopier.readBudget(),
    )

    enum class Refusal {
        /** 사용자 암호가 필요한데 없거나 틀렸다. */
        LOCKED,

        /** 암호로는 열 수 없는 방식(인증서·전용 처리기)으로 잠겼다. */
        LOCK_UNSUPPORTED,

        /** PDF 로 읽히지 않는다, 또는 쪽 나무를 화면과 같게 세지 못한다. */
        UNREADABLE,

        /** 고른 쪽이 문서에 없다(그사이 파일이 바뀌었다). */
        CHANGED,

        /** 쪽·객체·결과 크기가 상한을 넘는다. */
        TOO_BIG,
    }

    sealed interface Outcome {
        data class Done(val pages: Int) : Outcome
        data class Refused(val reason: Refusal) : Outcome
    }

    /** 잠금 — 없음, 소유자 암호만(빈 사용자 암호로 풀린다), 사용자 암호, 암호로는 못 여는 방식. */
    enum class Lock { NONE, OWNER_ONLY, PASSWORD, UNSUPPORTED }

    sealed interface Inspection {
        /** [pageCount] 는 잠겨 읽지 못했으면(암호가 없거나 틀렸다) null. 준 암호로 풀렸으면 [Lock.PASSWORD] 에 쪽 수가 있다. */
        data class Readable(val pageCount: Int?, val lock: Lock) : Inspection
        data object Unreadable : Inspection
    }

    private class Refused(val reason: Refusal) : Exception()

    /**
     * 쪽 하나 — **쪽 사전을 들지 않는다.** 쪽 사전 하나가 8 MiB 일 수 있고 쪽은 수만이다(쪽 80 개가 저마다 8 MiB 를 품은 730 KB 파일이 힙을
     * 채웠다 — 독립 검토 3차). 참조로 들고 쓸 때 다시 읽는다. 물려받는 넷([INHERITED] 차례)도 작은 값(참조·수·이름)만 들고, 그 밖의 값은
     * 그것을 가진 마디의 번호([owners])로 들어 쓸 때 다시 읽는다.
     */
    private class PageNode(
        val ref: PdfRef?,
        /** 참조 없이 나무에 놓인 쪽 사전 — 위 마디의 값 안에 이미 있다. 참조가 있으면 null. */
        val direct: PdfDict?,
        /** 물려받은 값(작은 것) — 없거나 마디에서 다시 읽을 것이면 null. */
        val inherited: Array<PdfObj?>,
        /** 물려받은 큰 값을 가진 마디의 번호(없으면 -1). */
        val owners: IntArray,
    )

    /** 연 원본. */
    private class Opened(
        val title: String,
        val file: PdfFile,
        val objs: PdfObjects,
        val security: PdfSecurity?,
        val root: PdfDict,
        val pages: List<PageNode>,
        /** 쪽 나무의 마디 번호(쪽·중간 마디) — 따라가지 않는다. */
        val treeNodes: Set<Int>,
        /** 따라가지 않는 그 밖의 번호 — 문서 뿌리, `/Encrypt`. */
        val stops: Set<Int>,
        val version: String,
        val lock: Lock,
    ) {
        val navigation: PdfNavigation by lazy {
            PdfNavigation(
                objs,
                // 목적지 풀기는 쪽의 번호만 쓴다 — 쪽 사전은 빈 것으로(들고 있지 않다).
                pages.map { PdfTextEngine.PageInfo(EMPTY_DICT, null, UNIT_BOX, 0, it.ref?.num ?: -1) },
                root,
            )
        }
    }

    /**
     * 원본마다 — 옛 번호 → 새 번호, 남은 쪽의 옛 번호 → 새 번호, 간접 `/Annots` 배열의 옛 번호 → 결과의 배열 번호(빈 배열이면 0). 공유
     * `/Annots` 하나를 쪽마다 펴 쓰면 244 KB 가 180 MB 가 된다(독립 검토) — 결과에서도 한 객체로 나눠 쓴다.
     */
    private class Mapping {
        val numbers = HashMap<Int, Int>()
        val keptPages = HashMap<Int, Int>()
        val annots = HashMap<Int, Int>()

        /**
         * 위 마디에서 물려받은 직접 사전·배열 → 결과의 번호(0 이면 펴 넣는다). 그 값을 가진 마디에 번호가 있으면 (마디, 칸)으로
         * ([byOwner]), 없으면(직접 놓인 마디) 값 그 자체로 가린다.
         */
        val inherited = IdentityHashMap<PdfObj, Int>()
        val byOwner = HashMap<Long, Int>()

        /** 객체 스트림 속 자리([PdfObjects.sharedKey]) → 결과의 번호. */
        val byPlace = HashMap<Long, Int>()
    }

    private class Job(val source: Int, val ref: PdfRef, val num: Int)

    /** 연 원본 — 열다 실패해도 이미 연 것의 보안 처리기(문서 열쇠)는 끝에서 덮는다. */
    private val opened = ArrayList<Opened>()
    private lateinit var maps: List<Mapping>
    private val offsets = LongArrayList()
    private var nextNum = 0
    private val queue = ArrayDeque<Job>()

    private fun run(sources: List<Source>, onPage: (Int, Int, String) -> Unit): Outcome = try {
        runOpened(sources, onPage)
    } finally {
        for (o in opened) o.security?.wipe()
    }

    private fun runOpened(sources: List<Source>, onPage: (Int, Int, String) -> Unit): Outcome {
        if (sources.isEmpty() || sources.size > limits.maxSources) return Outcome.Refused(Refusal.TOO_BIG)
        for (s in sources) {
            val o = open(s.title, s.src, s.password, checkCancelled, limits.readBudget)
            opened += o
            if (s.expectedCount != null && o.pages.size != s.expectedCount) throw Refused(Refusal.UNREADABLE)
            if (s.pages != null && s.pages.any { it !in o.pages.indices }) throw Refused(Refusal.CHANGED)
        }
        maps = sources.map { Mapping() }
        val chosen = sources.mapIndexed { i, s -> s.pages ?: opened[i].pages.indices.toList() }
        val total = chosen.sumOf { it.size }
        if (total == 0) throw Refused(Refusal.CHANGED)
        if (total > limits.maxPages) throw Refused(Refusal.TOO_BIG)

        // 번호 — 1 문서 뿌리, 2 쪽 나무, 3… 쪽(고른 차례), 그 뒤는 만나는 차례.
        val pageNums = ArrayList<Int>(total)
        nextNum = 3
        chosen.forEachIndexed { s, list ->
            for (p in list) {
                val n = nextNum++
                pageNums += n
                opened[s].pages[p].ref?.let { maps[s].keptPages.putIfAbsent(it.num, n) }
            }
        }
        offsets.set(0, 0)

        val version = opened.maxOf { it.version }
        emit("%PDF-$version\n%âãÏÓ\n".toByteArray(Charsets.ISO_8859_1))

        var done = 0
        chosen.forEachIndexed { s, list ->
            for (p in list) {
                checkCancelled()
                writePage(s, opened[s].pages[p], pageNums[done])
                drain()
                done++
                onPage(done, total, opened[s].title)
            }
        }

        val outlinesNum = writeOutlines(chosen, pageNums)
        val ocProperties = ocProperties()
        drain()

        // 쪽 나무 — 뿌리 하나에 쪽을 차례로(명세가 허락하는 가장 단순한 나무).
        val kids = PdfArray(pageNums.mapTo(ArrayList(pageNums.size)) { PdfRef(it, 0) })
        writePlain(2, dictOf("Type" to PdfName("Pages"), "Kids" to kids, "Count" to PdfNum(total.toString())))
        val catalog = dictOf("Type" to PdfName("Catalog"), "Pages" to PdfRef(2, 0))
        if (outlinesNum != null) catalog.map["Outlines"] = PdfRef(outlinesNum, 0)
        if (ocProperties != null) catalog.map["OCProperties"] = ocProperties
        if (opened.size == 1) opened[0].root["Lang"]?.let { lang -> catalog.map["Lang"] = transform(0, lang) }
        drain()
        writePlain(1, catalog)
        writeXref()
        return Outcome.Done(total)
    }

    // ---- 쪽 ----------------------------------------------------------------------------------

    private fun writePage(s: Int, page: PageNode, num: Int) {
        val o = opened[s]
        // 쪽 사전을 다시 읽는다(들고 있지 않았다). 그사이 읽히지 않거나 쪽이 아니게 되었으면 파일이 바뀌었다 — 다른 객체를 쪽으로 쓰지 않는다.
        val dict = page.direct ?: o.objs.dict(page.ref)?.takeIf { isPageLeaf(o.objs, it) } ?: throw Refused(Refusal.CHANGED)
        val d = LinkedHashMap(dict.map)
        d.remove("Parent")
        d.remove("B") // 기사 흐름 — 문서의 `/Threads` 를 옮기지 않는다
        d.remove("AA") // 쪽의 열기·닫기 동작 — 자바스크립트가 산다
        d["Type"] = PdfName("Page")
        // 물려받은 값 — 위 마디의 **직접** 사전·배열이 크면 그 값마다 한 객체로 써 두고 쪽마다 가리킨다(새 번호라 [transform] 뒤에
        // 붙인다). 쪽마다 펴 넣으면 120 KB 짜리 `/Resources` 를 쪽 2,000 개가 물려받는 문서가 285 KB 에서 318 MB 가 된다(독립 검토 2차).
        // 작은 값(미디어 상자·글꼴 하나짜리 자원)은 그대로 펴 넣는다 — 흔한 문서의 결과가 바뀌지 않게.
        val shared = ArrayList<Pair<String, Int>>(4)
        for ((i, key) in INHERITED.withIndex()) {
            if (d[key] != null) continue
            val owner = page.owners[i]
            // 나무를 걸을 때 그 마디에 있던 값이 지금 없으면 파일이 바뀌었다(빈 자원으로 조용히 채우지 않는다).
            val value = page.inherited[i]
                ?: owner.takeIf { it >= 0 }?.let { o.objs.dict(PdfRef(it, 0))?.get(key) ?: throw Refused(Refusal.CHANGED) }
            if (value == null) {
                // 어디에도 없으면 기본값(작은 직접 값).
                when (key) {
                    "Resources" -> d[key] = PdfDict(LinkedHashMap())
                    "MediaBox" -> d[key] = DEFAULT_MEDIA_BOX
                }
                continue
            }
            if (value !is PdfDict && value !is PdfArray) {
                d[key] = value
                continue
            }
            val m = maps[s]
            val placeKey = (owner.toLong() shl 2) or i.toLong()
            val n = (if (owner >= 0) m.byOwner[placeKey] else m.inherited[value]) ?: run {
                val made = if (PdfWeights.of(value) < SHARE_INHERITED_WEIGHT) 0 else newNumber()
                if (owner >= 0) m.byOwner[placeKey] = made else m.inherited[value] = made
                if (made != 0) writePlain(made, transform(s, value))
                made
            }
            if (n == 0) d[key] = value else shared += key to n
        }
        // 간접 배열이면 결과의 번호(새 번호라 [transform] 을 지나지 않게 뒤에 붙인다).
        var sharedAnnots = 0
        when (val annots = d["Annots"]) {
            null -> Unit
            // 간접 배열 — 결과에서도 한 객체로 나눠 쓴다(같은 배열을 가리키는 쪽이 여럿이다).
            is PdfRef -> {
                val m = maps[s]
                sharedAnnots = m.annots[annots.num] ?: run {
                    val kept = keptAnnotations(s, annots)
                    val n = if (kept.items.isEmpty()) 0 else newNumber()
                    m.annots[annots.num] = n
                    if (n != 0) writePlain(n, transform(s, kept))
                    n
                }
                d.remove("Annots")
            }
            else -> {
                val kept = keptAnnotations(s, annots)
                if (kept.items.isEmpty()) d.remove("Annots") else d["Annots"] = kept
            }
        }
        val out = transform(s, PdfDict(d)) as PdfDict
        if (sharedAnnots != 0) out.map["Annots"] = PdfRef(sharedAnnots, 0)
        for ((key, n) in shared) out.map[key] = PdfRef(n, 0)
        // 쪽 객체의 옛 번호는 [Mapping.keptPages] 가 이미 이 번호로 가리킨다(링크·`/P`).
        out.map["Parent"] = PdfRef(2, 0)
        writePlain(num, out)
    }

    /**
     * 쪽의 주석 가운데 옮길 것 — 원본의 값 그대로(참조·직접 사전). 다시 적는 일(목적지·동작·필드 나무)은 [sanitize] 가 쓰는 자리에서 한다.
     * 여기서는 **빼기만** 한다: 갈 곳을 잃은 링크(빠진 쪽·풀지 못한 이름·옮기지 않는 동작만 든 것)와 첨부 주석.
     */
    private fun keptAnnotations(s: Int, annots: PdfObj): PdfArray {
        val o = opened[s]
        val items = o.objs.array(annots)?.items ?: return PdfArray(ArrayList())
        val out = ArrayList<PdfObj>()
        // 같은 주석을 두 번 적은 배열이 있다 — 한 번만 남긴다(qpdf 가 '중복 항목' 으로 경고한다).
        val seen = HashSet<Int>()
        // 객체 스트림의 같은 자리를 가리키는 다른 번호도 같은 주석이다 — 결과에서 한 객체가 된다([mapRef]).
        val seenPlaces = HashSet<Long>()
        for ((i, item) in items.withIndex()) {
            if (i >= MAX_ANNOTS) break
            if (i and 0x3F == 0) checkCancelled()
            if (item is PdfRef && !seen.add(item.num)) continue
            if (item is PdfRef && o.objs.sharedKey(item)?.let { !seenPlaces.add(it) } == true) continue
            val a = o.objs.dict(item) ?: continue
            when (o.objs.name(a["Subtype"])) {
                "Link" -> if (!linkLeadsSomewhere(s, a)) continue
                "FileAttachment" -> continue
            }
            out += item
        }
        return PdfArray(out)
    }

    /** 이 링크가 옮긴 뒤에도 어딘가로 가는가 — 남은 쪽의 목적지가 있거나, 옮기는 동작(URI·Named·남은 쪽으로 가는 GoTo)이 있다. */
    private fun linkLeadsSomewhere(s: Int, a: PdfDict): Boolean {
        a["Dest"]?.let { return destinationFor(s, it) != null }
        val action = a["A"] ?: return false
        return keptAction(s, action) != null
    }

    /**
     * 옮겨도 되는 동작이면 그 값(GoTo 는 목적지를 다시 적은 새 사전), 아니면 null. URI·Named 는 원본 값 그대로 돌려주고 그 안의 `/Next` 는
     * [sanitize] 가 뗀다.
     */
    private fun keptAction(s: Int, action: PdfObj): PdfObj? {
        val o = opened[s]
        val a = o.objs.dict(action) ?: return null
        return when (o.objs.name(a["S"])) {
            "URI", "Named" -> action
            "GoTo" -> destinationFor(s, a["D"] ?: return null)?.let { dictOf("S" to PdfName("GoTo"), "D" to it) }
            else -> null
        }
    }

    /**
     * 옮기는 사전 하나를 고친다(원본은 그대로, 바꿀 것이 있을 때만 새 사전). 주석이든 동작이든 쓰는 자리에서 한 번에 — 쪽보다 먼저 다른
     * 객체(`/IRT`·`/Popup`)로 끌려온 링크도 같은 규칙을 지난다(독립 검토: 그 링크가 옛 목적지 `[null …]` 로 남았다).
     */
    private fun sanitize(s: Int, d: PdfDict): PdfDict {
        if (d["Subtype"] == null && d["S"] == null && d["A"] == null && d["AA"] == null) return d
        val o = opened[s]
        val subtype = o.objs.name(d["Subtype"])
        val type = o.objs.name(d["Type"])
        // 주석과 동작만 고친다 — `/S`·`/A`·`/Dest` 라는 이름의 칸은 다른 사전(글꼴·색 공간·사용자 사전)에도 온다(독립 검토 2차).
        val isAnnot = type == "Annot" || (subtype != null && type == null)
        val isAction = !isAnnot && (type == null || type == "Action") && o.objs.name(d["S"]) in ACTION_TYPES
        var out: LinkedHashMap<String, PdfObj>? = null
        fun edit(): LinkedHashMap<String, PdfObj> = out ?: LinkedHashMap(d.map).also { out = it }
        // 추가 동작(열기·닫기·눌림 …)은 어느 사전에서든 뗀다 — 자바스크립트가 사는 자리다.
        if (d["AA"] != null) edit().remove("AA")
        if (isAction && d["Next"] != null) edit().remove("Next")
        if (isAnnot) {
            when (subtype) {
                "Widget" -> if (d["Parent"] != null) edit().remove("Parent")
                "FileAttachment" -> if (d["FS"] != null) edit().remove("FS")
            }
            d["Dest"]?.let { dest ->
                val explicit = destinationFor(s, dest)
                if (explicit == null) edit().remove("Dest") else edit()["Dest"] = explicit
            }
        }
        // 주석의 `/A`, 그리고 동작으로 풀리는 `/A`(알려진 `/S`) — 그 밖의 `/A` 는 남의 칸이다.
        d["A"]?.let { action ->
            if (isAnnot || o.objs.name(o.objs.dict(action)?.get("S")) in ACTION_TYPES) {
                val kept = keptAction(s, action)
                if (kept == null) edit().remove("A") else if (kept !== action) edit()["A"] = kept
            }
        }
        return out?.let { PdfDict(it) } ?: d
    }

    /**
     * 목적지 → 남은 쪽을 가리키는 배열(원본의 쪽 참조 그대로 — [transform] 이 새 번호로 옮긴다). 빠진 쪽·풀지 못한 목적지면 null.
     * 첫 칸이 정수(0부터의 쪽 — 다른 파일을 가리키는 꼴이지만 pdfium 은 이 문서의 쪽으로 읽는다)면 그 쪽의 참조로 바꾼다.
     */
    private fun destinationFor(s: Int, dest: PdfObj): PdfArray? {
        val o = opened[s]
        val arr = o.navigation.explicitDestination(dest, checkCancelled) ?: return null
        if (arr.items.isEmpty()) return null
        val ref: PdfRef = when (val first = arr.items[0]) {
            is PdfRef -> first
            is PdfNum -> first.int?.let { o.pages.getOrNull(it)?.ref } ?: return null
            else -> return null
        }
        if (ref.num !in maps[s].keptPages) return null
        val items = ArrayList<PdfObj>(arr.items)
        items[0] = ref
        // 자리 칸이 참조면 그 값으로(번호를 옮길 필요가 없게).
        for (k in 1 until items.size) items[k] = o.objs.resolve(items[k]) ?: PdfNull
        return PdfArray(items)
    }

    // ---- 객체 그래프 ---------------------------------------------------------------------------

    /** 값 안의 참조를 새 번호로. 처음 만난 참조는 번호를 받고 줄에 선다. 새 값을 돌려준다. */
    private fun transform(s: Int, v: PdfObj, depth: Int = 0): PdfObj {
        if (depth > MAX_VALUE_DEPTH) return PdfNull
        return when (v) {
            is PdfRef -> mapRef(s, v)
            is PdfArray -> PdfArray(v.items.mapTo(ArrayList(v.items.size)) { transform(s, it, depth + 1) })
            is PdfDict -> {
                val d = sanitize(s, v)
                val out = LinkedHashMap<String, PdfObj>(d.map.size)
                for ((k, x) in d.map) out[k] = transform(s, x, depth + 1)
                PdfDict(out)
            }
            else -> v
        }
    }

    private fun mapRef(s: Int, ref: PdfRef): PdfObj {
        val m = maps[s]
        m.keptPages[ref.num]?.let { return PdfRef(it, 0) }
        val o = opened[s]
        if (ref.num in o.treeNodes || ref.num in o.stops) return PdfNull
        m.numbers[ref.num]?.let { return PdfRef(it, 0) }
        // 객체 스트림의 같은 자리를 가리키는 다른 번호 — 같은 값이다. 결과에서도 한 객체로(칸 300 개가 8 MiB 값 하나를 가리키면 300 벌을 쓴다).
        val key = o.objs.sharedKey(ref)
        if (key != null) {
            m.byPlace[key]?.let { shared ->
                m.numbers[ref.num] = shared
                return PdfRef(shared, 0)
            }
        }
        val n = newNumber()
        m.numbers[ref.num] = n
        if (key != null) m.byPlace[key] = n
        queue.addLast(Job(s, ref, n))
        return PdfRef(n, 0)
    }

    /** 새 객체 번호 — 객체 수 상한([Limits.maxObjects])을 여기서 본다. */
    private fun newNumber(): Int {
        if (nextNum - 3 >= limits.maxObjects) throw Refused(Refusal.TOO_BIG)
        return nextNum++
    }

    /** 줄 선 객체를 다 쓴다. 쓰는 동안 새로 만난 참조도 줄에 서므로 줄이 빌 때까지. */
    private fun drain() {
        while (true) {
            val job = queue.removeFirstOrNull() ?: return
            checkCancelled()
            writeCopied(job)
        }
    }

    private fun writeCopied(job: Job) {
        val o = opened[job.source]
        val stream = o.objs.stream(job.ref)
        if (stream != null) {
            val type = stream.dict.name("Type")
            // 옛 xref·객체 스트림은 옮기지 않는다 — 새 파일의 번호표와 어긋난다.
            if (type == "XRef" || type == "ObjStm") writePlain(job.num, PdfNull) else writeStream(job.source, stream, job.num)
            return
        }
        val v = o.objs.resolve(job.ref)
        val value = when {
            v == null -> PdfNull
            // 나무에 없는 쪽(고아) — 따라가면 그 쪽과 그 위의 나무가 딸려 온다.
            v is PdfDict && (v.name("Type") == "Page" || v.name("Type") == "Pages") -> PdfNull
            else -> transform(job.source, v)
        }
        writePlain(job.num, value)
    }

    private fun writeStream(s: Int, stream: PdfObjects.Stream, num: Int) {
        val o = opened[s]
        val sec = o.security
        val raw = stream.dict
        val dict = if (sec != null) {
            PdfCryptRules.decryptStrings(sec, raw, stream.num, stream.gen, lenient = true) as PdfDict
        } else {
            PdfDict(LinkedHashMap(raw.map))
        }
        val method = sec?.let { PdfCryptRules.streamMethod(it, raw) }
        if (sec != null) PdfCryptRules.stripCryptFilter(dict)
        val rawLen = o.file.streamLength(PdfFile.Indirect(stream.num, stream.gen, raw, stream.dataStart)) { o.objs.resolve(it) }
        // `/Length` 를 떼고 옮긴다 — 참조면 그 객체가 딸려 오지 않게. 아래에서 실제 길이로 적는다.
        dict.map.remove("Length")
        val out = transform(s, dict) as PdfDict
        var key: ByteArray? = null
        val decryptor = if (sec != null && method != null && method != PdfSecurity.Method.IDENTITY) {
            val k = sec.objectKey(stream.num, stream.gen, method)
            key = k
            sec.decryptor(method, k)
        } else {
            null
        }
        val decrypt = decryptor != null
        out.map["Length"] = PdfNum(if (decrypt) LENGTH_PLACEHOLDER else rawLen.toString())

        val start = sink.position
        offsets.set(num, start)
        val header = ByteArrayOutputStream()
        header.writeAscii("$num 0 obj\n")
        PdfWriter.write(out, header)
        val bytes = header.toByteArray()
        // 자리 채움은 사전의 맨 끝이다(위에서 마지막에 넣었다) — 복호기와 같은 까닭(16진 문자열 속 0 다섯).
        val placeholderAt = if (decrypt) start + lastIndexOf(bytes, LENGTH_PLACEHOLDER.toByteArray(Charsets.ISO_8859_1)) else -1L
        emit(bytes)
        emit(STREAM_OPEN)

        var written = 0L
        var pos = stream.dataStart
        val end = stream.dataStart + rawLen
        while (pos < end) {
            checkCancelled()
            val want = minOf(COPY_CHUNK.toLong(), end - pos).toInt()
            val got = o.file.readBytes(pos, want)
            if (got.isEmpty()) break
            pos += got.size
            val chunk = decryptor?.update(got, 0, got.size) ?: got
            if (chunk.isNotEmpty()) {
                emit(chunk)
                written += chunk.size
            }
        }
        decryptor?.finish()?.let {
            if (it.isNotEmpty()) {
                emit(it)
                written += it.size
            }
        }
        // AES-256 의 열쇠는 문서 열쇠 그 자체라 덮지 않는다(복호기와 같다).
        key?.takeIf { method != PdfSecurity.Method.AES256 }?.fill(0)
        if (!decrypt && written != rawLen) {
            // 파일이 그사이 짧아졌다 — 적은 길이와 실제가 어긋난 스트림을 남기지 않는다.
            throw Refused(Refusal.CHANGED)
        }
        emit(STREAM_CLOSE)
        if (placeholderAt >= start) {
            sink.patch(placeholderAt, written.toString().padStart(LENGTH_PLACEHOLDER.length, '0').toByteArray(Charsets.ISO_8859_1))
        }
    }

    private fun writePlain(num: Int, value: PdfObj) {
        offsets.set(num, sink.position)
        val out = ByteArrayOutputStream()
        out.writeAscii("$num 0 obj\n")
        PdfWriter.write(value, out)
        out.writeAscii("\nendobj\n")
        emit(out.toByteArray())
    }

    // ---- 목차 ----------------------------------------------------------------------------------

    private class OutlineItem(val title: String, val pageNum: Int, val depth: Int, var num: Int = 0)

    /** 남은 쪽의 목차를 짓는다. 없으면 null. */
    private fun writeOutlines(chosen: List<List<Int>>, pageNums: List<Int>): Int? {
        val items = ArrayList<OutlineItem>()
        val merge = opened.size > 1
        var base = 0
        chosen.forEachIndexed { s, list ->
            val newIndex = HashMap<Int, Int>(list.size * 2)
            list.forEachIndexed { i, p -> newIndex[p] = base + i }
            if (merge && list.isNotEmpty()) items += OutlineItem(opened[s].title, pageNums[base], 0)
            val outline = try {
                opened[s].navigation.outline(checkCancelled)
            } catch (e: PdfSyntaxException) {
                PdfOutline.EMPTY
            }
            // 빠진 쪽의 항목은 버리고 그 아이를 남은 조상 밑으로 올린다 — 남은 조상의 원래 깊이를 쌓아 두고 견준다.
            val ancestors = ArrayList<Int>()
            for (e in outline.entries) {
                while (ancestors.isNotEmpty() && ancestors.last() >= e.depth) ancestors.removeAt(ancestors.size - 1)
                val target = e.page?.let { newIndex[it] } ?: continue
                if (items.size >= limits.maxOutlineEntries) break
                items += OutlineItem(e.title, pageNums[target], ancestors.size + if (merge) 1 else 0)
                ancestors += e.depth
            }
            base += list.size
        }
        if (items.isEmpty()) return null

        val rootNum = nextNum++
        for (it in items) it.num = nextNum++
        // 부모·형제·아이를 깊이로 셈한다(앞차례로 편 목록).
        val parent = IntArray(items.size) { -1 }
        val prev = IntArray(items.size) { -1 }
        val next = IntArray(items.size) { -1 }
        val first = IntArray(items.size) { -1 }
        val last = IntArray(items.size) { -1 }
        val children = IntArray(items.size)
        val stack = ArrayList<Int>()
        var firstTop = -1
        var lastTop = -1
        for (i in items.indices) {
            val d = items[i].depth
            while (stack.size > d) stack.removeAt(stack.size - 1)
            val p = stack.lastOrNull() ?: -1
            parent[i] = p
            val sibling = if (p >= 0) last[p] else lastTop
            if (sibling >= 0) {
                prev[i] = sibling
                next[sibling] = i
            }
            if (p >= 0) {
                if (first[p] < 0) first[p] = i
                last[p] = i
                children[p]++
            } else {
                if (firstTop < 0) firstTop = i
                lastTop = i
            }
            stack += i
        }
        var visibleTop = 0
        for (i in items.indices) {
            checkCancelled()
            val d = dictOf(
                "Title" to PdfStr(utf16(items[i].title)),
                "Parent" to PdfRef(if (parent[i] >= 0) items[parent[i]].num else rootNum, 0),
            )
            if (prev[i] >= 0) d.map["Prev"] = PdfRef(items[prev[i]].num, 0)
            if (next[i] >= 0) d.map["Next"] = PdfRef(items[next[i]].num, 0)
            if (first[i] >= 0) {
                d.map["First"] = PdfRef(items[first[i]].num, 0)
                d.map["Last"] = PdfRef(items[last[i]].num, 0)
                // 합치기의 파일 항목만 펼쳐 둔다(아이들은 접힌다 — 보이는 자손은 바로 아래 아이들뿐이다).
                val open = merge && items[i].depth == 0
                d.map["Count"] = PdfNum((if (open) children[i] else -children[i]).toString())
                if (open && parent[i] < 0) visibleTop += children[i]
            }
            if (parent[i] < 0) visibleTop++
            d.map["Dest"] = PdfArray(mutableListOf(PdfRef(items[i].pageNum, 0), PdfName("Fit")))
            writePlain(items[i].num, d)
        }
        writePlain(
            rootNum,
            dictOf(
                "Type" to PdfName("Outlines"),
                "First" to PdfRef(items[firstTop].num, 0),
                "Last" to PdfRef(items[lastTop].num, 0),
                "Count" to PdfNum(visibleTop.toString()),
            ),
        )
        return rootNum
    }

    // ---- 선택 내용 -------------------------------------------------------------------------------

    /** `/OCProperties` — 원본 하나면 그대로, 여럿이면 `/OCGs` 와 꺼 둔 것(`/OFF`)만 모은다. 없으면 null. */
    private fun ocProperties(): PdfObj? {
        val found = opened.indices.mapNotNull { s -> opened[s].objs.dict(opened[s].root["OCProperties"])?.let { s to it } }
        if (found.isEmpty()) return null
        if (opened.size == 1) return transform(0, found[0].second)
        val ocgs = ArrayList<PdfObj>()
        val off = ArrayList<PdfObj>()
        for ((s, ocp) in found) {
            opened[s].objs.array(ocp["OCGs"])?.items?.forEach { ocgs += transform(s, it) }
            opened[s].objs.dict(ocp["D"])?.let { d -> opened[s].objs.array(d["OFF"])?.items?.forEach { off += transform(s, it) } }
        }
        if (ocgs.isEmpty()) return null
        val d = dictOf()
        if (off.isNotEmpty()) d.map["OFF"] = PdfArray(off)
        return dictOf("OCGs" to PdfArray(ocgs), "D" to d)
    }

    // ---- 끝 ------------------------------------------------------------------------------------

    /** 고전 xref 표. 번호는 1부터 빈틈없이 썼다. */
    private fun writeXref() {
        val size = nextNum
        val xrefAt = sink.position
        val out = ByteArrayOutputStream()
        out.writeAscii("xref\n0 $size\n0000000000 65535 f \n")
        for (n in 1 until size) {
            if (n and 0xFFF == 0) checkCancelled()
            // 로캘을 박는다 — 기기의 로캘을 따르면 아랍어·페르시아어 등에서 `%d` 가 그 문자의 숫자를 써서 xref 가 깨진다.
            out.writeAscii(String.format(java.util.Locale.ROOT, "%010d %05d n \n", offsets.get(n), 0))
            if (out.size() > 1 shl 20) {
                emit(out.toByteArray())
                out.reset()
            }
        }
        out.writeAscii("trailer\n")
        PdfWriter.write(dictOf("Size" to PdfNum(size.toString()), "Root" to PdfRef(1, 0)), out)
        out.writeAscii("\nstartxref\n$xrefAt\n%%EOF\n")
        emit(out.toByteArray())
    }

    private fun emit(b: ByteArray) {
        if (sink.position + b.size > limits.maxOutputBytes) throw Refused(Refusal.TOO_BIG)
        sink.write(b, 0, b.size)
    }

    /** 늘어나는 `Long` 배열 — 객체 50만 개의 자리를 `Long` 상자 없이. */
    private class LongArrayList {
        private var a = LongArray(1024)
        fun set(i: Int, v: Long) {
            if (i >= a.size) a = a.copyOf(maxOf(a.size * 2, i + 1))
            a[i] = v
        }
        fun get(i: Int): Long = if (i < a.size) a[i] else 0L
    }

    companion object {
        /** 쪽 하나에서 옮기는 주석의 상한. */
        private const val MAX_ANNOTS = 10_000

        /** 값의 깊이 — 해석기가 이미 256 에서 끊는다. 우리가 지은 값(덮어쓴 주석)도 같은 범위다. */
        private const val MAX_VALUE_DEPTH = 512

        private const val MAX_TREE_DEPTH = 64

        /**
         * 나무의 잎(쪽)인가 — `/Kids` 가 있으면 `/Type /Page` 일 때만, 없으면 `/Type /Pages` 가 아닐 때(`/Type` 을 빠뜨린 쪽이 있다).
         * 나무를 걸을 때와 쓸 때 다시 읽은 쪽을 볼 때 같은 판단이다. `PdfTextEngine` 의 나무 걷기와 같은 규칙.
         */
        private fun isPageLeaf(objs: PdfObjects, node: PdfDict): Boolean {
            val type = objs.name(node["Type"])
            return if (objs.array(node["Kids"]) != null) type == "Page" else type != "Pages"
        }

        /** 쪽이 위 마디에서 물려받는 넷(명세 표 31) — [PageNode] 의 칸 차례. */
        private val INHERITED = arrayOf("Resources", "MediaBox", "CropBox", "Rotate")
        private val EMPTY_DICT = PdfDict(LinkedHashMap())

        /** 물려받은 직접 값을 쪽마다 펴 넣지 않고 한 객체로 나눠 쓰는 무게(어림 — [PdfWeights]). */
        private const val SHARE_INHERITED_WEIGHT = 4096L

        /** 명세(표 198)의 동작 이름 — 이것을 `/S` 로 든 사전만 동작으로 본다. */
        private val ACTION_TYPES = setOf(
            "GoTo", "GoToR", "GoToE", "GoToDp", "Launch", "Thread", "URI", "Sound", "Movie", "Hide", "Named", "SubmitForm",
            "ResetForm", "ImportData", "JavaScript", "SetOCGState", "Rendition", "Trans", "GoTo3DView", "RichMediaExecute",
        )
        private const val COPY_CHUNK = 64 * 1024
        private const val LENGTH_PLACEHOLDER = "0000000000"

        private val UNIT_BOX = doubleArrayOf(0.0, 0.0, 1.0, 1.0)
        private val STREAM_OPEN = "\nstream\n".toByteArray(Charsets.ISO_8859_1)
        private val STREAM_CLOSE = "\nendstream\nendobj\n".toByteArray(Charsets.ISO_8859_1)

        /** 물려받을 미디어 상자도 없는 쪽 — pdfium·글 꺼내기와 같은 레터(612×792pt). */
        private val DEFAULT_MEDIA_BOX: PdfArray
            get() = PdfArray(mutableListOf(PdfNum("0"), PdfNum("0"), PdfNum("612"), PdfNum("792")))

        /**
         * 원본 하나를 읽는 예산([PdfObjects] 의 것 — 객체 스트림을 푼 바이트, 그 안의 값을 읽느라 지나간 바이트, 캐시에 두지 못한 큰 값. 다시
         * 한 것도 센다) — 넘으면 [Refusal.TOO_BIG]. 이 프로세스의 힙 상한의 1/4 과 1 GiB 가운데 작은 것(힙이 256 MB 인 기기에서 1 GiB 는
         * 예산이 아니다 — 독립 검토 3차).
         */
        fun readBudget(): Long = minOf(Runtime.getRuntime().maxMemory() / 4, MAX_READ_BUDGET)

        private const val MAX_READ_BUDGET = 1L shl 30

        /**
         * 쪽을 골라 [sink] 에 새 PDF 를 쓴다.
         *
         * @param checkCancelled 객체마다·스트림 64 KiB 마다 부른다. 취소면 `CancellationException` 을 던져라(그대로 올라간다).
         * @param onPage 쪽 하나를 (그 쪽이 끌어온 객체까지) 다 쓸 때마다 — (쓴 쪽 수, 전체, 원본 이름).
         * @throws java.io.IOException 쓰는 곳([sink])이 던진 것 — 그대로 올라간다(공간 부족 등).
         */
        fun write(
            sources: List<Source>,
            sink: PdfSink,
            limits: Limits = Limits(),
            checkCancelled: () -> Unit = {},
            onPage: (Int, Int, String) -> Unit = { _, _, _ -> },
        ): Outcome = guarded({ Outcome.Refused(it) }) {
            PdfPageCopier(sink, limits, checkCancelled).run(sources, onPage)
        }

        /**
         * 저장 전에 본다 — 쪽 수와 잠금. 객체를 다 훑지 않는다(쪽 나무만).
         *
         * @param password 사용자 암호(세션 암호) — 주면 그것으로 풀어 쪽 수를 센다. 덮지 않는다(부르는 쪽의 배열이다).
         */
        fun inspect(
            src: PdfSource,
            password: CharArray? = null,
            budget: Long = readBudget(),
            checkCancelled: () -> Unit = {},
        ): Inspection =
            guarded({
                when (it) {
                    Refusal.LOCKED -> Inspection.Readable(null, Lock.PASSWORD)
                    Refusal.LOCK_UNSUPPORTED -> Inspection.Readable(null, Lock.UNSUPPORTED)
                    else -> Inspection.Unreadable
                }
            }) {
                val o = open("", src, password, checkCancelled, budget)
                try {
                    Inspection.Readable(o.pages.size, o.lock)
                } finally {
                    o.security?.wipe()
                }
            }

        /**
         * 해석의 실패를 거절로 옮긴다 — 취소와 쓰는 곳의 입출력 예외([PdfSyntaxException] 이 아닌 `IOException`)는 그대로 올리고, 메모리
         * 부족·스택 넘침·예산 초과는 '너무 크다', 그 밖의 해석 실패는 '읽지 못함'. 남이 지은 PDF 하나가 큐의 작업을 죽이지 않게.
         */
        private inline fun <T> guarded(refused: (Refusal) -> T, block: () -> T): T = try {
            block()
        } catch (e: Refused) {
            refused(e.reason)
        } catch (e: PdfSyntaxException) {
            refused(Refusal.UNREADABLE)
        } catch (e: java.util.concurrent.CancellationException) {
            throw e
        } catch (e: java.io.IOException) {
            throw e
        } catch (e: PdfObjects.BudgetExceeded) {
            refused(Refusal.TOO_BIG)
        } catch (e: OutOfMemoryError) {
            refused(Refusal.TOO_BIG)
        } catch (e: StackOverflowError) {
            refused(Refusal.TOO_BIG)
        } catch (e: RuntimeException) {
            refused(Refusal.UNREADABLE)
        }

        private fun open(title: String, src: PdfSource, password: CharArray?, checkCancelled: () -> Unit, budget: Long): Opened {
            val version = headerVersion(src) ?: throw Refused(Refusal.UNREADABLE)
            val file = PdfFile.open(src, checkCancelled)
            val probe = PdfObjects(file, null, checkCancelled, budget)
            val encryptRef = file.trailer["Encrypt"]
            val encrypt = probe.dict(encryptRef)
            var lock = Lock.NONE
            val security = if (encrypt != null) {
                val id = ((file.trailer["ID"] as? PdfArray)?.items?.firstOrNull() as? PdfStr)?.bytes ?: ByteArray(0)
                try {
                    PdfSecurity.open(encrypt, id, CharArray(0))?.also { lock = Lock.OWNER_ONLY }
                        ?: password?.takeIf { it.isNotEmpty() }?.let { PdfSecurity.open(encrypt, id, it) }?.also { lock = Lock.PASSWORD }
                } catch (e: UnsupportedEncryption) {
                    throw Refused(Refusal.LOCK_UNSUPPORTED)
                } ?: throw Refused(Refusal.LOCKED)
            } else {
                null
            }
            try {
                val objs = if (security == null) probe else PdfObjects(file, security, checkCancelled, budget)
                val rootRef = file.trailer["Root"]
                val root = objs.dict(rootRef) ?: throw Refused(Refusal.UNREADABLE)
                val treeNodes = HashSet<Int>()
                val pages = pageTree(objs, root, treeNodes, checkCancelled)
                val stops = HashSet<Int>()
                (rootRef as? PdfRef)?.let { stops += it.num }
                (encryptRef as? PdfRef)?.let { stops += it.num }
                val catalogVersion = objs.name(root["Version"])?.takeIf { VERSION.matches(it) }
                return Opened(title, file, objs, security, root, pages, treeNodes, stops, maxOf(version, catalogVersion ?: version), lock)
            } catch (t: Throwable) {
                // 여기서 던지면 부르는 쪽은 처리기를 받지 못한다 — 문서 열쇠를 여기서 덮는다.
                security?.wipe()
                throw t
            }
        }

        /**
         * 쪽 나무를 편다 — `PdfTextEngine` 의 것과 같은 차례·같은 규칙(그래야 화면이 센 쪽 번호가 우리 번호다). 다른 것은 물려받는 넷을
         * **원본의 값 그대로**(참조) 들고 있는 것이다 — 그 값을 새 쪽에 옮겨 적는다. 들르는 마디를 직접 사전까지 센다
         * ([PdfTextEngine.MAX_TREE_VISITS] — 직접 사전 둘이 같은 간접 `/Kids` 를 가리키면 겹마다 두 배가 된다).
         */
        private fun pageTree(objs: PdfObjects, root: PdfDict, nodes: MutableSet<Int>, checkCancelled: () -> Unit): List<PageNode> {
            val out = ArrayList<PageNode>()
            var visits = 0

            /** 물려받는 넷의 값과 그 값을 가진 마디의 번호(참조 없는 마디면 -1). */
            class Inherit(val values: Array<PdfObj?>, val owners: IntArray)

            fun walk(nodeRef: PdfObj?, inherit: Inherit, depth: Int) {
                if (depth > MAX_TREE_DEPTH || out.size >= PdfTextEngine.MAX_PAGES) return
                if (++visits > PdfTextEngine.MAX_TREE_VISITS) return
                if (visits and 0xFF == 0) checkCancelled()
                (nodeRef as? PdfRef)?.let { if (!nodes.add(it.num)) return }
                val node = objs.dict(nodeRef) ?: return
                if (!isPageLeaf(objs, node)) {
                    val kids = objs.array(node["Kids"]) ?: return
                    val self = (nodeRef as? PdfRef)?.num ?: -1
                    val here = Inherit(
                        Array(INHERITED.size) { node[INHERITED[it]] ?: inherit.values[it] },
                        IntArray(INHERITED.size) { if (node[INHERITED[it]] != null) self else inherit.owners[it] },
                    )
                    for (k in kids.items) walk(k, here, depth + 1)
                    return
                }
                // 쪽 자신의 값은 쓸 때 쪽 사전에서 읽는다. 물려받은 것은 작은 값만 들고, 큰 값은 그 마디의 번호로.
                val held = Array(INHERITED.size) { i ->
                    inherit.values[i]?.takeIf { v -> inherit.owners[i] < 0 || v is PdfRef || v is PdfNum || v is PdfName }
                }
                val owners = IntArray(INHERITED.size) { i -> if (held[i] == null && inherit.values[i] != null) inherit.owners[i] else -1 }
                out += PageNode(nodeRef as? PdfRef, if (nodeRef is PdfRef) null else node, held, owners)
            }

            walk(root["Pages"], Inherit(arrayOfNulls(INHERITED.size), IntArray(INHERITED.size) { -1 }), 0)
            return out
        }

        private val VERSION = Regex("""[12]\.\d""")

        /** 머리의 판(`%PDF-1.7`). 첫 1024 바이트 안에 없으면 PDF 가 아니다(null). 1.4 아래는 1.4 로 — 투명도 등을 쓴 쪽이 섞일 수 있다. */
        private fun headerVersion(src: PdfSource): String? {
            val head = ByteArray(minOf(1024L, src.size).toInt())
            var done = 0
            while (done < head.size) {
                val n = src.read(done.toLong(), head, done, head.size - done)
                if (n <= 0) break
                done += n
            }
            val text = String(head, 0, done, Charsets.ISO_8859_1)
            val at = text.indexOf("%PDF-")
            if (at < 0) return null
            val v = VERSION.find(text, at + 5)?.takeIf { it.range.first == at + 5 }?.value ?: "1.4"
            return maxOf(v, "1.4")
        }

        private fun dictOf(vararg pairs: Pair<String, PdfObj>): PdfDict = PdfDict(linkedMapOf(*pairs))

        /** 글 문자열 — UTF-16BE 와 BOM(명세 7.9.2.2). */
        private fun utf16(s: String): ByteArray = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + s.toByteArray(Charsets.UTF_16BE)

        private fun lastIndexOf(hay: ByteArray, needle: ByteArray): Int {
            outer@ for (i in hay.size - needle.size downTo 0) {
                for (j in needle.indices) if (hay[i + j] != needle[j]) continue@outer
                return i
            }
            return -1
        }

        private fun ByteArrayOutputStream.writeAscii(s: String) = write(s.toByteArray(Charsets.ISO_8859_1))
    }
}
