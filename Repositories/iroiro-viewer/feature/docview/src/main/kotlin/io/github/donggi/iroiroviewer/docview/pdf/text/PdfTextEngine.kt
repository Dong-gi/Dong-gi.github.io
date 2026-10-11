package io.github.donggi.iroiroviewer.docview.pdf.text

import io.github.donggi.iroiroviewer.docview.pdf.crypt.ByteArraySource
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfArray
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfCursor
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfDict
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfFile
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfName
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfNum
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfObj
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfParser
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfRef
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfSecurity
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfSource
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfStr
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfSyntaxException
import io.github.donggi.iroiroviewer.docview.pdf.crypt.UnsupportedEncryption
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * PDF 쪽의 **글자와 그 자리**를 꺼낸다(글 고르기). 그리는 것은 여전히 pdfium 이고, 이것은 그 위에 고르는 상자를
 * 얹을 자리를 잰다.
 *
 * ## 왜 직접 하는가
 *
 * 레거시 `PdfRenderer`(API 34 이하 — 사용자의 기기)는 쪽의 글자를 주지 않는다. 글을 주는 것은 API 35 의 재구현과 확장 13 의
 * `PdfRendererPreV` 뿐이다(CLAUDE.md 'SDK 확장과 PDF'). 그래서 11단계가 세운 해석기(xref·객체 스트림·복호기) 위에 **글만
 * 보는 내용 해석기**를 얹었다 — 색·선·그림은 건너뛰고, 글자의 자리를 정하는 연산자(명세 9.3·9.4)와 그 상태(`q`·`Q`·`cm`)만 돈다.
 *
 * ## 좌표
 *
 * 글자의 자리는 **보이는 쪽**의 포인트다 — 자르기 상자(`/CropBox`, 없으면 `/MediaBox`)의 왼쪽 위가 원점이고 아래가 +y, 쪽의
 * `/Rotate` 를 돌린 뒤의 모양이다. pdfium 이 쪽을 그리는 상자와 방향이 그것이다. 화면은 이것을 그 쪽의 폭·높이로 나눠 비율로
 * 얹는다(`PdfTextLayout`).
 *
 * ## 막는 것
 *
 * 쪽 수·연산자 수·글자 수·폼 XObject 깊이에 상한이 있다([MAX_OPS] 등). 넘으면 거기까지 꺼낸 글을 쓴다 — 고르기가 쪽의 앞부분에서
 * 만 될 뿐 앱이 멎지 않는다.
 */
internal class PdfTextEngine private constructor(
    private val objs: PdfObjects,
    private val pages: List<PageInfo>,
) {
    val pageCount: Int get() = pages.size

    /** 쪽 하나의 사정 — 물려받은 자원·상자·회전을 펴 둔 것. */
    private class PageInfo(val dict: PdfDict, val resources: PdfDict?, val box: DoubleArray, val rotate: Int)

    /**
     * 읽어 둔 글꼴 — **못 읽은 것도**(null) 기억하고, 수를 묶는다. 쪽마다 같은 글꼴을 다시 읽지 않고, 망가진 글꼴을 참조마다 다시
     * 풀지도 않는다.
     */
    private val fonts = object : LinkedHashMap<Any, PdfFont?>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Any, PdfFont?>?): Boolean = size > MAX_FONTS
    }

    /** 스트림 번호별로 읽어 둔 CMap(글꼴 여럿이 같은 `/ToUnicode` 를 가리킨다). */
    private val cmaps = object : LinkedHashMap<Int, PdfCMap?>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, PdfCMap?>?): Boolean = size > MAX_CMAPS
    }

    private var checkCancelled: () -> Unit = {}

    /**
     * 쪽 [index] 의 글자들. 범위 밖이면 빈 쪽. **한 번에 하나만 부른다**(글꼴·객체 캐시를 나눠 쓴다 — 부르는 쪽이 잠근다).
     *
     * @param checkCancelled 연산자 천 개마다·스트림마다 부른다. 취소면 여기서 던져라.
     */
    fun page(index: Int, checkCancelled: () -> Unit = {}): PageText {
        this.checkCancelled = checkCancelled
        objs.checkCancelled = checkCancelled
        val p = pages.getOrNull(index) ?: return PageText(0f, 0f, emptyList())
        val (w, h) = displaySize(p)
        val collector = Collector(p, w, h)
        val contents = contentData(p.dict["Contents"])
        if (contents != null) collector.runPage(contents, p.resources)
        return PageText(w.toFloat(), h.toFloat(), collector.glyphs)
    }

    private fun displaySize(p: PageInfo): Pair<Double, Double> {
        val w = p.box[2] - p.box[0]
        val h = p.box[3] - p.box[1]
        return if (p.rotate == 90 || p.rotate == 270) h to w else w to h
    }

    /** `/Contents` — 스트림 하나 또는 배열. 배열이면 이어 붙인다(명세는 스트림 사이의 경계에 뜻을 두지 않는다). */
    private fun contentData(obj: PdfObj?): ByteArray? {
        val refs = when (val c = objs.resolve(obj)) {
            is PdfArray -> c.items
            else -> listOf(obj ?: return null)
        }
        val out = ByteArrayOutputStream()
        for (r in refs.take(MAX_CONTENT_PARTS)) {
            val s = objs.stream(r) ?: continue
            val data = objs.data(s) ?: continue
            out.write(data)
            out.write('\n'.code)
            if (out.size() > PdfObjects.MAX_STREAM) break
        }
        return out.toByteArray().takeIf { it.isNotEmpty() }
    }

    private fun fontFor(resources: PdfDict?, name: String): PdfFont? {
        val fontDict = objs.dict(resources?.get("Font")) ?: return null
        val ref = fontDict[name] ?: return null
        val key: Any = (ref as? PdfRef)?.num ?: ref
        if (fonts.containsKey(key)) return fonts[key]
        val font = PdfFont.load(objs, ref, cmaps)
        fonts[key] = font
        return font
    }

    // ---- 내용 해석 -------------------------------------------------------------------

    private inner class Collector(private val page: PageInfo, private val width: Double, private val height: Double) {
        val glyphs = ArrayList<Glyph>()
        private var ops = 0

        /**
         * 이 쪽에서 해석한 바이트(내용 + 폼). **연산자가 없는 바이트**(공백·주석)는 [MAX_OPS] 에 세이지 않으므로 따로 묶는다 —
         * 공백뿐인 32 MiB 폼을 `/X Do` 로 백만 번 부르면 연산자 상한에 닿지 않고 몇 시간을 돈다.
         */
        private var parsedBytes = 0L

        /** 이 쪽에서 푼 폼 — 같은 폼을 부를 때마다 다시 풀지 않는다. */
        private val forms = HashMap<Int, ByteArray?>()
        private var formBytes = 0L

        private inner class State(
            var ctm: DoubleArray,
            var charSpacing: Double = 0.0,
            var wordSpacing: Double = 0.0,
            var hScale: Double = 1.0,
            var leading: Double = 0.0,
            var font: PdfFont? = null,
            var fontSize: Double = 0.0,
            var rise: Double = 0.0,
        ) {
            fun copy() = State(ctm.copyOf(), charSpacing, wordSpacing, hScale, leading, font, fontSize, rise)
        }

        fun runPage(data: ByteArray, resources: PdfDict?) = run(data, resources, State(IDENTITY.copyOf()), 0)

        /**
         * 내용 스트림 하나를 돈다. [initial] 은 시작 상태 — 쪽이면 빈 상태, **폼이면 `Do` 를 부른 자리의 상태**(글꼴·크기·간격을
         * 물려받는다 — 명세 8.10.1. 처음에는 빈 상태로 시작해 `Do` 앞의 `Tf` 에 기대는 폼의 글이 빠졌다).
         */
        private fun run(data: ByteArray, resources: PdfDict?, initial: State, depth: Int) {
            parsedBytes += data.size
            if (parsedBytes > MAX_PARSED_BYTES) return
            val cursor = PdfCursor(ByteArraySource(data), 0)
            val parser = PdfParser(cursor)
            val stack = ArrayList<State>()
            var gs = initial
            var tm = IDENTITY.copyOf()
            var tlm = IDENTITY.copyOf()
            val operands = ArrayList<PdfObj>()

            fun num(i: Int): Double = (operands.getOrNull(i) as? PdfNum)?.text?.toDoubleOrNull() ?: 0.0

            fun td(tx: Double, ty: Double) {
                tlm = multiply(doubleArrayOf(1.0, 0.0, 0.0, 1.0, tx, ty), tlm)
                tm = tlm.copyOf()
            }

            fun show(bytes: ByteArray) {
                val font = gs.font ?: return
                font.decode(bytes, checkCancelled) { code ->
                    if (glyphs.size >= MAX_GLYPHS) return@decode false
                    tm = emit(gs, font, tm, code)
                    true
                }
            }

            while (true) {
                if (++ops > MAX_OPS || glyphs.size >= MAX_GLYPHS) return
                if (ops and 0x3FF == 0) checkCancelled()
                val tok = try {
                    parser.parseObject()
                } catch (e: PdfSyntaxException) {
                    return // 망가진 내용 — 거기까지 꺼낸 것을 쓴다
                } ?: return
                if (tok !is PdfParser.Keyword) {
                    if (operands.size < MAX_OPERANDS) operands += tok
                    continue
                }
                when (tok.text) {
                    "q" -> if (stack.size < MAX_STACK) stack += gs.copy()
                    "Q" -> if (stack.isNotEmpty()) gs = stack.removeAt(stack.size - 1)
                    "cm" -> if (operands.size >= 6) gs.ctm = multiply(DoubleArray(6) { num(it) }, gs.ctm)
                    "BT" -> {
                        tm = IDENTITY.copyOf()
                        tlm = IDENTITY.copyOf()
                    }
                    "Tc" -> gs.charSpacing = num(0)
                    "Tw" -> gs.wordSpacing = num(0)
                    "Tz" -> gs.hScale = num(0) / 100.0
                    "TL" -> gs.leading = num(0)
                    "Ts" -> gs.rise = num(0)
                    "Tf" -> {
                        val name = (operands.getOrNull(0) as? PdfName)?.name
                        gs.font = name?.let { fontFor(resources, it) }
                        gs.fontSize = num(1)
                    }
                    "Td" -> td(num(0), num(1))
                    "TD" -> {
                        gs.leading = -num(1)
                        td(num(0), num(1))
                    }
                    "Tm" -> if (operands.size >= 6) {
                        tlm = DoubleArray(6) { num(it) }
                        tm = tlm.copyOf()
                    }
                    "T*" -> td(0.0, -gs.leading)
                    "Tj" -> (operands.getOrNull(0) as? PdfStr)?.let { show(it.bytes) }
                    "'" -> {
                        td(0.0, -gs.leading)
                        (operands.getOrNull(0) as? PdfStr)?.let { show(it.bytes) }
                    }
                    "\"" -> {
                        gs.wordSpacing = num(0)
                        gs.charSpacing = num(1)
                        td(0.0, -gs.leading)
                        (operands.getOrNull(2) as? PdfStr)?.let { show(it.bytes) }
                    }
                    "TJ" -> (operands.getOrNull(0) as? PdfArray)?.items?.forEach { item ->
                        when (item) {
                            is PdfStr -> show(item.bytes)
                            is PdfNum -> {
                                // 숫자는 글자 크기의 천분율만큼 거꾸로 민다(양수가 왼쪽).
                                val adj = -(item.text.toDoubleOrNull() ?: 0.0) / 1000.0 * gs.fontSize
                                tm = if (gs.font?.vertical == true) {
                                    multiply(doubleArrayOf(1.0, 0.0, 0.0, 1.0, 0.0, adj), tm)
                                } else {
                                    multiply(doubleArrayOf(1.0, 0.0, 0.0, 1.0, adj * gs.hScale, 0.0), tm)
                                }
                            }
                            else -> Unit
                        }
                    }
                    "Do" -> (operands.getOrNull(0) as? PdfName)?.name?.let { form(resources, it, gs, depth) }
                    "BI" -> if (!skipInlineImage(parser, cursor)) return
                }
                operands.clear()
            }
        }

        /** 글자 하나를 적고 다음 글자의 글 행렬을 돌려준다(명세 9.4.4). */
        private fun emit(gs: State, font: PdfFont, tm: DoubleArray, code: PdfFont.Code): DoubleArray {
            val fs = gs.fontSize
            val spacing = gs.charSpacing + if (code.singleByteSpace) gs.wordSpacing else 0.0
            if (font.vertical) {
                val trm = multiply(multiply(doubleArrayOf(fs, 0.0, 0.0, fs, 0.0, gs.rise), tm), gs.ctm)
                // 세로쓰기의 원점은 글자의 위 가운데다 — 가로 원점에서 위치 벡터만큼 옮긴 자리.
                val w = 1.0 // 세로쓰기 글자는 가로 폭을 모른다(`/W` 는 가로다) — 한 칸으로 친다.
                val (vx, vy) = font.verticalOrigin(w)
                record(code.text, trm, -vx, font.descent - vy, w - vx, font.ascent - vy, 0.0, code.width, fs)
                val ty = code.width * fs + spacing
                return multiply(doubleArrayOf(1.0, 0.0, 0.0, 1.0, 0.0, ty), tm)
            }
            val trm = multiply(multiply(doubleArrayOf(fs * gs.hScale, 0.0, 0.0, fs, 0.0, gs.rise), tm), gs.ctm)
            record(code.text, trm, 0.0, font.descent, code.width, font.ascent, code.width, 0.0, fs)
            val tx = (code.width * fs + spacing) * gs.hScale
            return multiply(doubleArrayOf(1.0, 0.0, 0.0, 1.0, tx, 0.0), tm)
        }

        /**
         * 글리프 공간의 상자(`x0..x1 × y0..y1`, 글자 크기에 대한 비율)를 보이는 쪽으로 옮겨 적는다. 나아가는 방향은
         * 원점에서 (`advX`, `advY`) 로 간 자리다.
         */
        private fun record(
            text: String?,
            trm: DoubleArray,
            x0: Double,
            y0: Double,
            x1: Double,
            y1: Double,
            advX: Double,
            advY: Double,
            fontSize: Double,
        ) {
            val xs = DoubleArray(4)
            val ys = DoubleArray(4)
            corner(trm, x0, y0, xs, ys, 0)
            corner(trm, x1, y0, xs, ys, 1)
            corner(trm, x1, y1, xs, ys, 2)
            corner(trm, x0, y1, xs, ys, 3)
            val o = toPage(trm, 0.0, 0.0)
            val end = toPage(trm, advX, advY)
            val up = toPage(trm, 0.0, 1.0)
            val size = hypot(up[0] - o[0], up[1] - o[1])
            if (!size.isFinite() || size <= 0.0 || size > MAX_GLYPH_SIZE) return
            var dx = end[0] - o[0]
            var dy = end[1] - o[1]
            val adv = hypot(dx, dy)
            if (adv > 0) {
                dx /= adv
                dy /= adv
            } else {
                // 폭이 0 인 글자(결합 기호) — 방향은 글자의 위쪽에 수직이다.
                dx = (up[1] - o[1]) / size * -1.0
                dy = (up[0] - o[0]) / size
                if (abs(dx) + abs(dy) == 0.0) dx = 1.0
            }
            val left = xs.min()
            val right = xs.max()
            val top = ys.min()
            val bottom = ys.max()
            if (!(left.isFinite() && right.isFinite() && top.isFinite() && bottom.isFinite())) return
            // 쪽 밖으로 아주 멀리 나간 글자(숨긴 글·인쇄 표지)는 고를 수 없다 — 담지 않는다.
            if (right < -width || left > 2 * width || bottom < -height || top > 2 * height) return
            glyphs += Glyph(
                text = text.orEmpty(),
                left = left.toFloat(),
                top = top.toFloat(),
                right = right.toFloat(),
                bottom = bottom.toFloat(),
                originX = o[0].toFloat(),
                originY = o[1].toFloat(),
                dirX = dx.toFloat(),
                dirY = dy.toFloat(),
                advance = adv.toFloat(),
                size = size.toFloat(),
            )
        }

        private fun corner(trm: DoubleArray, x: Double, y: Double, xs: DoubleArray, ys: DoubleArray, i: Int) {
            val p = toPage(trm, x, y)
            xs[i] = p[0]
            ys[i] = p[1]
        }

        /** 글리프 공간의 점 → 사용자 공간 → 보이는 쪽. */
        private fun toPage(trm: DoubleArray, x: Double, y: Double): DoubleArray {
            val ux = trm[0] * x + trm[2] * y + trm[4]
            val uy = trm[1] * x + trm[3] * y + trm[5]
            return display(ux, uy)
        }

        private fun display(x: Double, y: Double): DoubleArray {
            val b = page.box
            return when (page.rotate) {
                90 -> doubleArrayOf(y - b[1], x - b[0])
                180 -> doubleArrayOf(b[2] - x, y - b[1])
                270 -> doubleArrayOf(b[3] - y, b[2] - x)
                else -> doubleArrayOf(x - b[0], b[3] - y)
            }
        }

        /** 폼 XObject 를 따라 들어간다. 그림은 건너뛴다. */
        private fun form(resources: PdfDict?, name: String, gs: State, depth: Int) {
            if (depth >= MAX_FORM_DEPTH) return
            val xobjects = objs.dict(resources?.get("XObject")) ?: return
            val ref = xobjects[name] ?: return
            val s = objs.stream(ref) ?: return
            if (objs.name(s.dict["Subtype"]) != "Form") return
            val data = if (forms.containsKey(s.num)) {
                forms[s.num]
            } else {
                // 이 쪽에서 푸는 폼의 양을 묶는다 — 큰 폼 여럿을 불러도 메모리가 쪽 하나 몫을 넘지 않게.
                if (formBytes > MAX_FORM_BYTES) return
                objs.data(s).also {
                    forms[s.num] = it
                    formBytes += it?.size ?: 0
                }
            } ?: return
            val m = objs.array(s.dict["Matrix"])?.items?.mapNotNull { objs.number(it) }
                ?.takeIf { it.size == 6 }?.toDoubleArray() ?: IDENTITY
            val inner = objs.dict(s.dict["Resources"]) ?: resources
            val start = gs.copy().apply { ctm = multiply(m, gs.ctm) }
            run(data, inner, start, depth + 1)
        }

        /**
         * `BI … ID <바이트> EI` 를 건너뛴다. 바이트 속에 `EI` 가 우연히 있을 수 있어, **앞뒤가 공백인** `EI` 를 찾는다(pdf.js 와
         * 같은 판단). 못 찾으면 false — 그 뒤는 해석하지 않는다.
         */
        private fun skipInlineImage(parser: PdfParser, cursor: PdfCursor): Boolean {
            // 머리의 키·값을 ID 까지 읽는다.
            var guard = 0
            while (true) {
                val t = try {
                    parser.parseObject()
                } catch (e: PdfSyntaxException) {
                    return false
                } ?: return false
                if (t is PdfParser.Keyword && t.text == "ID") break
                if (++guard > 1000) return false
            }
            cursor.next() // ID 뒤의 공백 하나
            var prev = ' '.code
            while (true) {
                val c = cursor.next()
                if (c < 0) return false
                if (c == 'E'.code && isWhite(prev) && cursor.peek() == 'I'.code) {
                    cursor.next()
                    val after = cursor.peek()
                    if (after < 0 || isWhite(after)) return true
                }
                prev = c
            }
        }

        private fun isWhite(c: Int) = c == 0 || c == 9 || c == 10 || c == 12 || c == 13 || c == 32
    }

    companion object {
        private val IDENTITY = doubleArrayOf(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)

        const val MAX_PAGES = 200_000
        const val MAX_OPS = 2_000_000
        const val MAX_GLYPHS = 100_000
        private const val MAX_FONTS = 512
        private const val MAX_CMAPS = 64

        /** 쪽 하나에서 해석하는 바이트(내용 + 폼을 부를 때마다). */
        private const val MAX_PARSED_BYTES = 256L * 1024 * 1024

        /** 쪽 하나에서 풀어 두는 폼의 바이트. */
        private const val MAX_FORM_BYTES = 96L * 1024 * 1024
        private const val MAX_OPERANDS = 4096
        private const val MAX_STACK = 256
        private const val MAX_FORM_DEPTH = 12
        private const val MAX_CONTENT_PARTS = 4096
        private const val MAX_TREE_DEPTH = 64

        /** 한 글자가 쪽보다 훨씬 크면 숨긴 글이나 망가진 행렬이다. */
        private const val MAX_GLYPH_SIZE = 20_000.0

        /**
         * 연다. **암호가 걸렸고 빈 암호로 풀리지 않으면 null** — 그런 문서의 평문은 우리가 만든 메모리 파일에 있고, 그것을
         * 넘기는 쪽이 [source] 로 그것을 준다(`PdfDocument`).
         *
         * @throws PdfSyntaxException 파일이 PDF 로 읽히지 않는다.
         */
        fun open(source: PdfSource, checkCancelled: () -> Unit = {}): PdfTextEngine? {
            val file = PdfFile.open(source, checkCancelled)
            val probe = PdfObjects(file, null, checkCancelled)
            val encrypt = probe.dict(file.trailer["Encrypt"])
            val security = if (encrypt != null) {
                val id = ((file.trailer["ID"] as? PdfArray)?.items?.firstOrNull() as? PdfStr)?.bytes ?: ByteArray(0)
                try {
                    PdfSecurity.open(encrypt, id, CharArray(0))
                } catch (e: UnsupportedEncryption) {
                    null
                } ?: return null
            } else {
                null
            }
            val objs = if (security == null) probe else PdfObjects(file, security, checkCancelled)
            val pages = pageTree(objs, file.trailer, checkCancelled)
            return PdfTextEngine(objs, pages)
        }

        /** 쪽 나무를 편다 — 물려받는 것(자원·상자·회전)을 쪽마다 적어 둔다. pdfium 이 세는 차례와 같다(나무의 차례). */
        private fun pageTree(objs: PdfObjects, trailer: PdfDict, checkCancelled: () -> Unit): List<PageInfo> {
            val root = objs.dict(trailer["Root"]) ?: return emptyList()
            val out = ArrayList<PageInfo>()
            val seen = HashSet<Int>()

            class Inherit(val resources: PdfObj?, val media: PdfObj?, val crop: PdfObj?, val rotate: PdfObj?)

            fun walk(nodeRef: PdfObj?, inherit: Inherit, depth: Int) {
                if (depth > MAX_TREE_DEPTH || out.size >= MAX_PAGES) return
                (nodeRef as? PdfRef)?.let { if (!seen.add(it.num)) return }
                val node = objs.dict(nodeRef) ?: return
                if (out.size and 0xFF == 0) checkCancelled()
                val here = Inherit(
                    node["Resources"] ?: inherit.resources,
                    node["MediaBox"] ?: inherit.media,
                    node["CropBox"] ?: inherit.crop,
                    node["Rotate"] ?: inherit.rotate,
                )
                val kids = objs.array(node["Kids"])
                val type = objs.name(node["Type"])
                if (kids != null && type != "Page") {
                    for (k in kids.items) walk(k, here, depth + 1)
                    return
                }
                // 아이가 없는 중간 마디는 쪽이 아니다(pdfium 도 세지 않는다).
                if (type == "Pages") return
                val media = box(objs, here.media) ?: doubleArrayOf(0.0, 0.0, 612.0, 792.0)
                // 자르기 상자는 미디어 상자 안으로 자른다(pdfium 과 같다).
                val crop = box(objs, here.crop)?.let { c ->
                    doubleArrayOf(max(c[0], media[0]), max(c[1], media[1]), min(c[2], media[2]), min(c[3], media[3]))
                }?.takeIf { it[2] > it[0] && it[3] > it[1] } ?: media
                val rot = ((objs.number(here.rotate)?.toInt() ?: 0) % 360 + 360) % 360
                out += PageInfo(node, objs.dict(here.resources), crop, if (rot % 90 == 0) rot else 0)
            }

            walk(root["Pages"], Inherit(null, null, null, null), 0)
            return out
        }

        private fun box(objs: PdfObjects, obj: PdfObj?): DoubleArray? {
            val v = objs.array(obj)?.items?.mapNotNull { objs.number(it) } ?: return null
            if (v.size < 4) return null
            return doubleArrayOf(min(v[0], v[2]), min(v[1], v[3]), max(v[0], v[2]), max(v[1], v[3]))
                .takeIf { it[2] - it[0] > 0 && it[3] - it[1] > 0 }
        }

        /** 행렬 곱 `m1 × m2` — 점에 m1 을 먼저, m2 를 나중에(명세의 행 벡터 꼴). */
        fun multiply(m1: DoubleArray, m2: DoubleArray): DoubleArray = doubleArrayOf(
            m1[0] * m2[0] + m1[1] * m2[2],
            m1[0] * m2[1] + m1[1] * m2[3],
            m1[2] * m2[0] + m1[3] * m2[2],
            m1[2] * m2[1] + m1[3] * m2[3],
            m1[4] * m2[0] + m1[5] * m2[2] + m2[4],
            m1[4] * m2[1] + m1[5] * m2[3] + m2[5],
        )
    }
}

/**
 * 한 쪽의 글자들. [width]·[height] 는 보이는 쪽의 크기(포인트), 글자의 자리도 같은 단위다(왼쪽 위 원점, 아래가 +y).
 * 글자의 차례는 **내용 스트림의 차례**다 — 대개 읽는 차례이고, pdfium·pdf.js 도 이것을 쓴다.
 */
internal class PageText(val width: Float, val height: Float, val glyphs: List<Glyph>)

/**
 * 글자 하나. [text] 는 비어 있을 수 있다(글자를 모르는 코드 — 상자는 있다). [originX]·[originY] 는 기준선 위의 원점,
 * [dirX]·[dirY] 는 나아가는 방향(단위 벡터), [advance] 는 나아간 거리, [size] 는 글자 크기(전부 보이는 쪽의 포인트).
 */
internal class Glyph(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val originX: Float,
    val originY: Float,
    val dirX: Float,
    val dirY: Float,
    val advance: Float,
    val size: Float,
)
