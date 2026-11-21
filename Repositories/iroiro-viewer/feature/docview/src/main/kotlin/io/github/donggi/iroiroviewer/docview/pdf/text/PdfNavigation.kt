package io.github.donggi.iroiroviewer.docview.pdf.text

import io.github.donggi.iroiroviewer.docview.pdf.PdfLinks
import io.github.donggi.iroiroviewer.docview.pdf.PdfOutline
import io.github.donggi.iroiroviewer.docview.pdf.PdfSearch
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfArray
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfDict
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfName
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfNum
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfObj
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfRef
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfStr
import kotlin.math.max
import kotlin.math.min

/**
 * 문서 안을 **옮겨 다니는 길** — 목차(`/Outlines`)와 쪽의 링크 주석(`/Annots` 의 `/Subtype /Link`). 플랫폼이 주지 않는 둘이다:
 * `PdfRenderer` 에 목차 API 가 없고, `Page.getGotoLinks` 는 `/A GoTo` 꼴만 준다(`PdfLinks` 머리말 — 실측).
 *
 * 글 꺼내기([PdfTextEngine])의 객체 읽기([PdfObjects])와 쪽 목록을 그대로 쓴다 — 새 해석기가 아니다. 그래서 같은 원본을 가진
 * 문서에서만 돈다: 평문 파일, 소유자 암호만 걸린 파일(빈 암호로 푼다 — 문자열도 [PdfObjects] 가 푼다), 우리가 풀어 메모리 파일에
 * 둔 평문. 플랫폼이 직접 푼 암호 문서는 평문이 우리 손에 없어 둘 다 없다.
 *
 * ## 목적지(명세 12.3.2)
 *
 * * **배열** `[쪽 /XYZ …]` — 첫 칸이 쪽 객체의 참조다. 정수면 0부터의 쪽 번호로 읽는다(pdfium 과 같다 — 명세는 다른 문서를 가리킬
 *   때만 정수를 쓰지만 같은 문서에 그렇게 적은 파일이 있다). 자리(`/XYZ` 의 좌표)는 쓰지 않는다 — 쪽으로 간다.
 * * **이름**(`/Dests` 사전의 열쇠, PDF 1.1) · **문자열**(`/Names` → `/Dests` 이름 나무의 열쇠, PDF 1.2+). pdfium 처럼 둘 다 나무를
 *   먼저, 사전을 다음에 찾는다. 값은 배열이거나 `/D` 를 든 사전이다.
 * * 링크·목차 항목은 `/Dest` 로 바로 적거나 `/A << /S /GoTo /D … >>` 로 적는다. 다른 동작(`GoToR`·`URI`·`Named`·`JavaScript`)은
 *   따르지 않는다 — 다른 파일·바깥 주소·스크립트다.
 *
 * ## 막는 것
 *
 * 임의의 사용자 파일이다. 목차는 **방문 집합**(같은 마디를 두 번 따르지 않는다 — `/Next` 가 앞을 가리키는 순환), **항목 수**
 * ([PdfOutline.MAX_ENTRIES]), **깊이**([PdfOutline.MAX_DEPTH]), **방문 수**([MAX_VISITS] — 풀리지 않는 참조만 이어진 사슬)로 묶는다.
 * 이름 나무도 방문 집합·깊이·방문 수로 묶고, 펴서 들지 않고 `/Limits` 로 내려가 찾는다(이름이 수만인 TeX 문서가 있다 — 메모리가
 * 찾는 열쇠 수만큼만 든다). 시간 상한과 취소는 부르는 쪽(`PdfDocument`)이 건다. 넘으면 거기까지 읽은 것을 쓴다.
 */
internal class PdfNavigation(
    private val objs: PdfObjects,
    private val pages: List<PdfTextEngine.PageInfo>,
    private val root: PdfDict?,
) {
    /** 쪽 객체 번호 → 0부터의 쪽 번호. 같은 객체가 두 번 나오면(망가진 쪽 나무) 앞의 것. */
    private val pageByNum: Map<Int, Int> by lazy {
        val m = HashMap<Int, Int>(pages.size * 2)
        pages.forEachIndexed { i, p -> if (p.num >= 0) m.putIfAbsent(p.num, i) }
        m
    }

    /** 찾아 본 이름 → 목적지(없으면 null). 목차와 링크가 같은 이름을 여러 번 가리킨다. */
    private val named = object : LinkedHashMap<String, PdfObj?>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PdfObj?>?): Boolean = size > MAX_NAMED_CACHE
    }

    private var checkCancelled: () -> Unit = {}

    // ---- 목차 ----------------------------------------------------------------------------

    fun outline(checkCancelled: () -> Unit): PdfOutline {
        this.checkCancelled = checkCancelled
        val outlines = objs.dict(root?.get("Outlines")) ?: return PdfOutline.EMPTY
        val titles = ArrayList<String>()
        val targets = ArrayList<Int?>()
        val depths = ArrayList<Int>()
        val opens = ArrayList<Boolean>()
        val seen = HashSet<Int>()
        // 뿌리 자신도 센다 — `/First` 가 뿌리를 가리키는 순환.
        (root?.get("Outlines") as? PdfRef)?.let { seen += it.num }
        // 앞차례로 돈다 — 꺼낸 마디를 적고, 다음 형제를 쌓고, 첫 아이를 그 위에 쌓는다(아이가 형제보다 먼저 나온다). 되부르지 않는다:
        // 깊이가 스택이 되면 악성 파일이 스레드를 넘친다.
        val stack = ArrayList<Pair<PdfObj?, Int>>()
        stack += outlines["First"] to 0
        var visits = 0
        while (stack.isNotEmpty() && titles.size < PdfOutline.MAX_ENTRIES) {
            if (++visits > MAX_VISITS) break
            if (visits and 0x3F == 0) checkCancelled()
            val (ref, depth) = stack.removeAt(stack.size - 1)
            if (ref is PdfRef && !seen.add(ref.num)) continue // 순환 — 이 갈래를 끊는다
            val node = objs.dict(ref) ?: continue
            stack += node["Next"] to depth
            titles += decodeText((objs.resolve(node["Title"]) as? PdfStr)?.bytes)
            targets += destinationOf(node)
            depths += depth
            opens += (objs.number(node["Count"]) ?: 0.0) > 0.0
            // 상한 깊이의 항목은 남기되 그 아이들은 버린다.
            if (depth + 1 < PdfOutline.MAX_DEPTH) node["First"]?.let { stack += it to depth + 1 }
        }
        val entries = ArrayList<PdfOutline.Entry>(titles.size)
        for (i in titles.indices) {
            val hasChildren = i + 1 < depths.size && depths[i + 1] > depths[i]
            entries += PdfOutline.Entry(titles[i], targets[i], depths[i], opens[i], hasChildren)
        }
        return PdfOutline(entries)
    }

    /** 목차 항목·링크의 목적지 — `/Dest` 가 먼저, 없으면 `/A` 의 GoTo. */
    private fun destinationOf(node: PdfDict): Int? {
        node["Dest"]?.let { return pageOf(it) }
        return goToPage(node["A"])
    }

    private fun goToPage(action: PdfObj?): Int? {
        val a = objs.dict(action) ?: return null
        if (objs.name(a["S"]) != "GoTo") return null
        return pageOf(a["D"])
    }

    // ---- 링크 ----------------------------------------------------------------------------

    fun links(index: Int, withActions: Boolean, checkCancelled: () -> Unit): List<PdfLinks.Link> {
        // **부를 때마다 바꿔 끼운다** — 앞 일(목차를 읽던 코루틴)의 것이 남으면 그 일이 끝난 뒤 그것이 취소 예외를 던져 이 일을 죽인다
        // (에뮬레이터에서 두드림이 소리 없이 사라졌다).
        this.checkCancelled = checkCancelled
        val page = pages.getOrNull(index) ?: return emptyList()
        val annots = objs.array(page.dict["Annots"]) ?: return emptyList()
        val out = ArrayList<PdfLinks.Link>()
        for ((n, a) in annots.items.withIndex()) {
            if (n >= MAX_ANNOTS || out.size >= PdfLinks.MAX_PER_PAGE) break
            if (n and 0x3F == 0) checkCancelled()
            val d = objs.dict(a) ?: continue
            if (objs.name(d["Subtype"]) != "Link") continue
            val dest = d["Dest"]
            val target = when {
                dest != null -> pageOf(dest)
                withActions -> goToPage(d["A"])
                else -> null
            } ?: continue
            val box = boxOf(page, d["Rect"]) ?: continue
            out += PdfLinks.Link(listOf(box), target)
        }
        return out
    }

    /**
     * `/Rect`(사용자 공간 — 왼쪽 아래가 원점)를 **보이는 쪽**의 상자로(왼쪽 위가 원점, `/Rotate` 를 돌리고 자르기 상자에 맞춘 것 —
     * 플랫폼의 링크·찾기와 같은 좌표, [PdfTextEngine.PageInfo.display]). 쪽 안으로 자르고, 수가 아니거나(NaN·무한) 넷이 아니거나 잘라서
     * 넓이가 없으면 null.
     */
    private fun boxOf(page: PdfTextEngine.PageInfo, rect: PdfObj?): PdfSearch.Box? {
        val v = objs.array(rect)?.items ?: return null
        if (v.size < 4) return null
        val n = DoubleArray(4) { objs.number(v[it]) ?: return null }
        if (n.any { !it.isFinite() }) return null
        val a = page.display(n[0], n[1])
        val b = page.display(n[2], n[3])
        val w = page.displayWidth
        val h = page.displayHeight
        val left = max(min(a[0], b[0]), 0.0)
        val right = min(max(a[0], b[0]), w)
        val top = max(min(a[1], b[1]), 0.0)
        val bottom = min(max(a[1], b[1]), h)
        if (!(right > left && bottom > top)) return null
        return PdfSearch.Box(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
    }

    // ---- 목적지 ----------------------------------------------------------------------------

    /** 목적지 → 0부터의 쪽 번호. 풀지 못하거나 문서에 없는 쪽이면 null. */
    fun pageOf(dest: PdfObj?): Int? {
        var cur = dest
        // 이름 → 사전(`/D`) → 배열. 그 이상 이어지는 것은 망가진 파일이다.
        repeat(MAX_DEST_HOPS) {
            when (val d = objs.resolve(cur)) {
                is PdfArray -> return explicitPage(d)
                is PdfName -> cur = lookupName(d.name)
                is PdfStr -> cur = lookupName(String(d.bytes, Charsets.ISO_8859_1))
                is PdfDict -> cur = d["D"]
                else -> return null
            }
        }
        return null
    }

    private fun explicitPage(dest: PdfArray): Int? = when (val first = dest.items.firstOrNull()) {
        is PdfRef -> pageByNum[first.num]
        is PdfNum -> first.int?.takeIf { it in pages.indices }
        else -> null
    }

    /** 이름 목적지. 이름 나무(`/Names` → `/Dests`)를 먼저, 옛 `/Dests` 사전을 다음에(pdfium 의 차례). */
    private fun lookupName(key: String): PdfObj? {
        if (named.containsKey(key)) return named[key]
        val tree = objs.dict(root?.get("Names"))?.get("Dests")
        val found = searchNameTree(tree, key) ?: objs.dict(root?.get("Dests"))?.get(key)
        named[key] = found
        return found
    }

    /**
     * 이름 나무에서 [key] 를 찾는다(명세 7.9.6). 아이의 `/Limits` 가 열쇠를 품는 갈래로만 내려간다 — `/Limits` 가 없는 아이는 들어가 본다.
     * 열쇠는 바이트의 차례로 견준다(ISO-8859-1 로 옮긴 글자의 차례가 그것이다).
     */
    private fun searchNameTree(rootNode: PdfObj?, key: String): PdfObj? {
        if (rootNode == null) return null
        val seen = HashSet<Int>()
        val stack = ArrayList<Pair<PdfObj?, Int>>()
        stack += rootNode to 0
        var visits = 0
        while (stack.isNotEmpty()) {
            if (++visits > MAX_TREE_VISITS) return null
            if (visits and 0x3F == 0) checkCancelled()
            val (ref, depth) = stack.removeAt(stack.size - 1)
            if (ref is PdfRef && !seen.add(ref.num)) continue
            val node = objs.dict(ref) ?: continue
            // 마디 하나의 `/Names` 도 `/Kids` 도 해석기 상한(400만 칸)까지 길 수 있고 칸마다 참조를 풀 수 있다 — 마디 수만 세면
            // 시간 상한이 그 안에 닿지 않는다(마디 하나를 훑는 데 몇 분). 칸 256 개마다 취소를 본다.
            objs.array(node["Names"])?.items?.let { names ->
                var i = 0
                while (i + 1 < names.size) {
                    if (i and 0x1FF == 0) checkCancelled()
                    val k = keyOf(objs.resolve(names[i]))
                    if (k == key) return names[i + 1]
                    i += 2
                }
            }
            if (depth >= MAX_TREE_DEPTH) continue
            val kids = objs.array(node["Kids"])?.items ?: continue
            // 뒤에서부터 쌓아 앞의 아이를 먼저 본다.
            for (j in kids.indices.reversed()) {
                if (j and 0xFF == 0) checkCancelled()
                val kid = kids[j]
                val limits = objs.array(objs.dict(kid)?.get("Limits"))?.items
                if (limits != null && limits.size >= 2) {
                    val lo = keyOf(objs.resolve(limits[0]))
                    val hi = keyOf(objs.resolve(limits[1]))
                    if (lo != null && hi != null && (key < lo || key > hi)) continue
                }
                stack += kid to depth + 1
            }
        }
        return null
    }

    private fun keyOf(o: PdfObj?): String? = when (o) {
        is PdfStr -> String(o.bytes, Charsets.ISO_8859_1)
        // 열쇠는 문자열이어야 하지만 이름으로 적은 파일이 있다.
        is PdfName -> o.name
        else -> null
    }

    companion object {
        /** 목차를 돌며 꺼내는 마디의 상한(풀리지 않는 참조도 센다). */
        private const val MAX_VISITS = PdfOutline.MAX_ENTRIES * 4

        /** 쪽 하나에서 훑는 주석의 상한. */
        private const val MAX_ANNOTS = 10_000

        /** 이름 나무 하나를 찾는 동안 보는 마디의 상한. */
        private const val MAX_TREE_VISITS = 10_000
        private const val MAX_TREE_DEPTH = 32
        private const val MAX_DEST_HOPS = 6
        private const val MAX_NAMED_CACHE = 4096

        /**
         * 문서의 **글 문자열**(목차 제목 — 명세 7.9.2.2)을 글자로. UTF-16BE(BOM `FE FF`), UTF-8(BOM `EF BB BF` — PDF 2.0), 그리고
         * BOM 이 없으면 PDFDocEncoding 이다. 명세 밖의 UTF-16LE(`FF FE`)도 읽는다(pdfium 이 읽는다). UTF-16 안의 언어 표지
         * (`ESC 말 ESC`)를 지우고, 제어 글자는 빈칸으로, 빈칸은 하나로, 길이는 [PdfOutline.MAX_TITLE] 에서 자른다.
         */
        fun decodeText(bytes: ByteArray?): String {
            val b = bytes ?: return ""
            val raw = when {
                b.size >= 2 && b[0] == 0xFE.toByte() && b[1] == 0xFF.toByte() ->
                    String(b, 2, (b.size - 2) and 1.inv(), Charsets.UTF_16BE)
                b.size >= 2 && b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte() ->
                    String(b, 2, (b.size - 2) and 1.inv(), Charsets.UTF_16LE)
                b.size >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte() ->
                    String(b, 3, b.size - 3, Charsets.UTF_8)
                else -> buildString(b.size) { for (x in b) append(PDF_DOC[x.toInt() and 0xFF]) }
            }
            return clean(raw)
        }

        private fun clean(raw: String): String {
            val out = StringBuilder(min(raw.length, PdfOutline.MAX_TITLE + 1))
            var i = 0
            var space = false
            while (i < raw.length && out.length < PdfOutline.MAX_TITLE) {
                val c = raw[i]
                if (c == '\u001B') {
                    // 언어 표지 — 다음 ESC 까지 버린다. 닫는 ESC 가 없으면 끝까지.
                    val end = raw.indexOf('\u001B', i + 1)
                    i = if (end < 0) raw.length else end + 1
                    continue
                }
                i++
                if (c.isWhitespace() || c < ' ' || c in '\u007F'..'\u009F') {
                    space = out.isNotEmpty()
                    continue
                }
                if (space) {
                    out.append(' ')
                    space = false
                    if (out.length >= PdfOutline.MAX_TITLE) break
                }
                out.append(c)
            }
            // 자른 자리가 대리 쌍의 가운데면 앞 반쪽을 버린다.
            if (out.isNotEmpty() && Character.isHighSurrogate(out[out.length - 1])) out.setLength(out.length - 1)
            return out.toString()
        }

        /**
         * PDFDocEncoding(명세 부록 D.2) — 0x18~0x1F 의 발음 기호, 0x80~0xA0 의 문장 부호·합자·유로를 빼면 latin-1 이다. 정의되지 않은
         * 자리(0x7F·0x9F·0xAD 와 낮은 제어 글자)는 제어 글자로 두어 [clean] 이 빈칸으로 바꾼다. 표는 pypdf 6.17 의 것과 견줬다.
         */
        private val PDF_DOC: CharArray = CharArray(256) { it.toChar() }.also { t ->
            val low = intArrayOf(0x02D8, 0x02C7, 0x02C6, 0x02D9, 0x02DD, 0x02DB, 0x02DA, 0x02DC)
            for (i in low.indices) t[0x18 + i] = low[i].toChar()
            val high = intArrayOf(
                0x2022, 0x2020, 0x2021, 0x2026, 0x2014, 0x2013, 0x0192, 0x2044, 0x2039, 0x203A, 0x2212, 0x2030, 0x201E, 0x201C, 0x201D, 0x2018,
                0x2019, 0x201A, 0x2122, 0xFB01, 0xFB02, 0x0141, 0x0152, 0x0160, 0x0178, 0x017D, 0x0131, 0x0142, 0x0153, 0x0161, 0x017E, 0x009F,
                0x20AC,
            )
            for (i in high.indices) t[0x80 + i] = high[i].toChar()
            t[0xAD] = 0.toChar()
        }
    }
}
