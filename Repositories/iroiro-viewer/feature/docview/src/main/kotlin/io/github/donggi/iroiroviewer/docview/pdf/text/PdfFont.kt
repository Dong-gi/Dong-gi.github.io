package io.github.donggi.iroiroviewer.docview.pdf.text

import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfArray
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfDict
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfName
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfNum
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfObj
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfRef
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * 글 꺼내기가 아는 글꼴 — **코드를 나누고, 글자로 옮기고, 폭을 잰다.** 글리프의 모양은 모른다(그리는 것은 pdfium 이다).
 *
 * ## 폭이 왜 필요한가
 *
 * 글자의 자리는 앞 글자의 폭만큼 밀린 자리다(명세 9.4.4). 폭을 틀리면 고르는 상자가 글자에서 어긋나고, 낱말 사이의 빈
 * 자리(띄어쓰기를 넣을지)를 잘못 잰다. 폭은 글꼴 사전이 적는다 — 한 바이트 글꼴은 `/Widths`, 합성 글꼴은 `/W`. 그것이 없는
 * 표준 14 글꼴(옛 문서의 Helvetica·Times)은 **어림값**을 쓴다(그 글꼴들의 폭 표를 싣지 않았다).
 */
internal class PdfFont private constructor(
    /** 합성 글꼴(Type0) — 코드가 여러 바이트일 수 있다. */
    val composite: Boolean,
    /** 세로쓰기(`-V` CMap). 글자가 아래로 나아간다. */
    val vertical: Boolean,
    /** 글자 상자의 위·아래(글리프 공간 ÷ 1000). */
    val ascent: Double,
    val descent: Double,
    private val toUnicode: PdfCMap?,
    private val simple: SimpleCodes?,
    private val cidCodes: CidCodes?,
) {
    /** 한 바이트 글꼴의 코드별 글자·폭. */
    private class SimpleCodes(val unicode: Array<String?>, val widths: DoubleArray)

    /** 합성 글꼴의 코드 나누기·CID·폭. */
    private class CidCodes(
        val encoding: PdfCMap?,
        val charset: Charset?,
        val charsetName: String?,
        val identity: Boolean,
        val widths: CidWidths,
        val verticalDefault: DoubleArray,
    )

    /**
     * 꺼낸 코드 하나. [width] 는 **글자 크기에 대한 비율**(글리프 공간 ÷ 1000 — Type3 은 `/FontMatrix` 로 옮겨 같은 단위로 둔다).
     * 세로쓰기면 세로 나아감이고 음수가 아래다.
     */
    class Code(val bytes: Int, val text: String?, val width: Double, val singleByteSpace: Boolean)

    /**
     * 바이트열을 코드들로 — 글자와 폭을 함께. [out] 이 false 를 돌려주면 거기서 멈춘다(글자 수 상한에 닿았다 — 남은 바이트를
     * 끝까지 풀지 않는다). [check] 는 코드 [CHECK_EVERY] 개마다 부른다 — 문자열 하나가 16 MiB 일 수 있다.
     */
    fun decode(bytes: ByteArray, check: () -> Unit, out: (Code) -> Boolean) {
        if (!composite) {
            val s = simple!!
            for ((i, b) in bytes.withIndex()) {
                if (i % CHECK_EVERY == CHECK_EVERY - 1) check()
                val c = b.toInt() and 0xFF
                val text = toUnicode?.unicode(c.toLong(), 1) ?: s.unicode[c]
                if (!out(Code(1, text, s.widths[c] / 1000.0, c == 32))) return
            }
            return
        }
        val cc = cidCodes!!
        var at = 0
        var codes = 0
        while (at < bytes.size) {
            if (++codes % CHECK_EVERY == 0) check()
            val len = when {
                cc.encoding != null && cc.encoding.codespaces.isNotEmpty() -> cc.encoding.codeLength(bytes, at)
                cc.charsetName != null -> PredefinedCMaps.codeLength(cc.charsetName, bytes, at)
                else -> 2.coerceAtMost(bytes.size - at)
            }.coerceIn(1, bytes.size - at)
            var code = 0L
            for (i in 0 until len) code = (code shl 8) or (bytes[at + i].toLong() and 0xFF)
            val cid: Int? = when {
                cc.identity -> code.toInt()
                cc.encoding != null -> cc.encoding.cid(code, len)
                else -> null
            }
            val text = toUnicode?.unicode(code, len)
                ?: cc.charset?.let { decodeWith(it, bytes, at, len) }
            val width = if (vertical) {
                cid?.let { cc.widths.vertical(it) } ?: cc.verticalDefault[1]
            } else {
                cid?.let { cc.widths.horizontal(it) }
                    // CID 를 모르면(미리 정의된 CMap) 기본 폭 — 한 바이트(ASCII)는 반각으로 친다.
                    ?: if (len == 1) cc.widths.default / 2 else cc.widths.default
            }
            if (!out(Code(len, text, width / 1000.0, len == 1 && code == 32L))) return
            at += len
        }
    }

    /** 세로쓰기에서 글자의 위치 벡터(글리프 공간 ÷ 1000) — 글자 원점에서 가로 원점까지. */
    fun verticalOrigin(width: Double): Pair<Double, Double> {
        val vy = cidCodes?.verticalDefault?.get(0) ?: 880.0
        return width / 2 to vy / 1000.0
    }

    private fun decodeWith(cs: Charset, bytes: ByteArray, at: Int, len: Int): String? {
        val dec = cs.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            dec.decode(java.nio.ByteBuffer.wrap(bytes, at, len)).toString()
        } catch (e: java.nio.charset.CharacterCodingException) {
            null
        }
    }

    /** `/W` 표. 범위로 들고 찾을 때 고른다(표 하나가 CID 수만 개를 덮는다). */
    class CidWidths(
        val default: Double,
        private val starts: IntArray,
        private val ends: IntArray,
        private val values: Array<DoubleArray?>,
        private val single: DoubleArray,
        private val verticalDefaultAdvance: Double,
        private val vStarts: IntArray,
        private val vEnds: IntArray,
        private val vSingle: DoubleArray,
        private val vValues: Array<DoubleArray?>,
    ) {
        fun horizontal(cid: Int): Double {
            val i = find(starts, ends, cid) ?: return default
            values[i]?.let { return it.getOrElse(cid - starts[i]) { default } }
            return single[i]
        }

        /** 세로 나아감(`/W2` 의 w1y). 없으면 `/DW2` 의 둘째 값. */
        fun vertical(cid: Int): Double {
            val i = find(vStarts, vEnds, cid) ?: return verticalDefaultAdvance
            vValues[i]?.let { arr -> return arr.getOrElse((cid - vStarts[i]) * 3) { verticalDefaultAdvance } }
            return vSingle[i]
        }

        private fun find(starts: IntArray, ends: IntArray, cid: Int): Int? {
            var lo = 0
            var hi = starts.size - 1
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                when {
                    cid < starts[mid] -> hi = mid - 1
                    cid > ends[mid] -> lo = mid + 1
                    else -> return mid
                }
            }
            return null
        }
    }

    companion object {

        /** 코드 몇 개마다 취소를 보는가. */
        private const val CHECK_EVERY = 4096

        /**
         * 글꼴 사전을 읽는다. 읽을 수 없으면(사전이 아니다) null — 그 글꼴로 그린 글자는 꺼내지 않는다.
         *
         * @param cmaps 스트림 번호별로 읽어 둔 CMap. **여러 글꼴이 같은 `/ToUnicode` 를 가리키면 한 번만 읽는다**(4 MiB CMap 하나를
         *   글꼴마다 다시 풀면 작은 파일이 메모리를 다 쓴다).
         */
        fun load(objs: PdfObjects, ref: PdfObj?, cmaps: MutableMap<Int, PdfCMap?> = HashMap()): PdfFont? {
            val dict = objs.dict(ref) ?: return null
            val subtype = objs.name(dict["Subtype"])
            val toUnicode = toUnicodeOf(objs, dict["ToUnicode"], cmaps)
            return if (subtype == "Type0") {
                loadComposite(objs, dict, toUnicode, cmaps)
            } else {
                loadSimple(objs, dict, subtype, toUnicode)
            }
        }

        private fun toUnicodeOf(objs: PdfObjects, obj: PdfObj?, cmaps: MutableMap<Int, PdfCMap?>): PdfCMap? {
            // `/ToUnicode /Identity-H` 같은 이름은 '코드가 곧 UTF-16' 이라는 뜻으로 쓰인다(pdf.js 와 같이 읽는다).
            if (objs.name(obj)?.startsWith("Identity") == true) return null
            return cmapOf(objs, obj as? PdfRef ?: return null, cmaps)?.takeIf { it.hasUnicode }
        }

        private fun cmapOf(objs: PdfObjects, ref: PdfRef, cmaps: MutableMap<Int, PdfCMap?>): PdfCMap? {
            if (cmaps.containsKey(ref.num)) return cmaps[ref.num]
            val parsed = objs.stream(ref)?.let { s -> objs.data(s, MAX_CMAP_BYTES) }?.let { PdfCMap.parse(it) }
            cmaps[ref.num] = parsed
            return parsed
        }

        private fun descriptorMetrics(objs: PdfObjects, fd: PdfDict?): Pair<Double, Double> {
            var asc = fd?.let { objs.number(it["Ascent"]) } ?: 0.0
            var desc = fd?.let { objs.number(it["Descent"]) } ?: 0.0
            // 0·뒤집힌 값·터무니없는 값을 적은 글꼴이 흔하다. 그러면 흔한 비율로 둔다.
            if (!(asc > 0 && asc < 3000)) asc = 800.0
            if (!(desc < 0 && desc > -3000)) desc = -200.0
            return asc / 1000.0 to desc / 1000.0
        }

        // ---- 한 바이트 글꼴 -----------------------------------------------------------

        private fun loadSimple(objs: PdfObjects, dict: PdfDict, subtype: String?, toUnicode: PdfCMap?): PdfFont {
            val fd = objs.dict(dict["FontDescriptor"])
            val baseFont = objs.name(dict["BaseFont"]).orEmpty()
            val flags = fd?.let { objs.number(it["Flags"]) }?.toInt() ?: 0
            val symbolic = flags and 4 != 0
            val type3 = subtype == "Type3"

            // 인코딩: 기본 표 + 차이. 기본 표는 이름이 말하고, 없으면 글꼴의 종류가 정한다.
            val unicode = arrayOfNulls<String>(256)
            val encObj = objs.resolve(dict["Encoding"])
            val baseName = when (encObj) {
                is PdfName -> encObj.name
                is PdfDict -> objs.name(encObj["BaseEncoding"])
                else -> null
            }
            val stdName = standardName(baseFont)
            val base = PdfEncodings.table(baseName) ?: when {
                // 글꼴의 내장 인코딩을 따라야 하는 자리 — 그것을 읽지 않으므로 흔한 쪽을 고른다.
                stdName == "Symbol" || stdName == "ZapfDingbats" -> null
                symbolic && subtype == "TrueType" -> PdfEncodings.WIN_ANSI
                else -> PdfEncodings.STANDARD
            }
            if (base != null) for (c in 0 until 256) if (base[c] != 0) unicode[c] = String(Character.toChars(base[c]))
            if (encObj is PdfDict) applyDifferences(objs, encObj, unicode)
            // 기호 글꼴의 코드가 글자 그대로인 것(낱자만 쓴 부분 글꼴)이 흔하다. 모르는 자리만 ASCII 로 채운다.
            if (base == null) for (c in 0x20..0x7E) if (unicode[c] == null && stdName == null) unicode[c] = c.toChar().toString()

            // 폭.
            val fontMatrix = if (type3) objs.array(dict["FontMatrix"])?.items?.mapNotNull { objs.number(it) } else null
            val glyphScale = fontMatrix?.getOrNull(0)?.takeIf { it != 0.0 && it.isFinite() }?.let { kotlin.math.abs(it) } ?: 0.001
            val widths = DoubleArray(256)
            val missing = fd?.let { objs.number(it["MissingWidth"]) } ?: 0.0
            val first = objs.number(dict["FirstChar"])?.toInt() ?: 0
            val wArr = objs.array(dict["Widths"])?.items
            val fallback = standardWidth(stdName)
            for (c in 0 until 256) {
                val w = wArr?.getOrNull(c - first)?.let { objs.number(it) }
                widths[c] = when {
                    w != null -> w * glyphScale * 1000.0
                    wArr != null -> missing
                    else -> fallback(c, unicode[c])
                }
            }
            val (asc, desc) = if (type3) type3Metrics(objs, dict, fontMatrix) else descriptorMetrics(objs, fd)
            return PdfFont(
                composite = false,
                vertical = false,
                ascent = asc,
                descent = desc,
                toUnicode = toUnicode,
                simple = SimpleCodes(unicode, widths),
                cidCodes = null,
            )
        }

        private fun applyDifferences(objs: PdfObjects, enc: PdfDict, unicode: Array<String?>) {
            val diffs = objs.array(enc["Differences"])?.items ?: return
            var code = 0
            for (item in diffs.take(MAX_DIFFERENCES)) {
                when (val v = objs.resolve(item)) {
                    is PdfNum -> code = v.int ?: code
                    is PdfName -> {
                        if (code in 0..255) unicode[code] = PdfEncodings.unicodeOf(v.name)
                        code++
                    }
                    else -> Unit
                }
            }
        }

        private fun type3Metrics(objs: PdfObjects, dict: PdfDict, matrix: List<Double>?): Pair<Double, Double> {
            val bbox = objs.array(dict["FontBBox"])?.items?.mapNotNull { objs.number(it) }
            val sy = matrix?.getOrNull(3)?.takeIf { it != 0.0 && it.isFinite() } ?: 0.001
            if (bbox == null || bbox.size < 4 || bbox[3] <= bbox[1]) return 0.8 to -0.2
            val top = bbox[3] * sy
            val bottom = bbox[1] * sy
            return if (top > bottom) top to bottom else 0.8 to -0.2
        }

        /** 표준 14 글꼴의 이름(부분 글꼴의 `ABCDEF+` 와 `,Bold` 를 떼고). 아니면 null. */
        private fun standardName(baseFont: String): String? {
            val name = baseFont.substringAfter('+').substringBefore(',').substringBefore('-')
            return when {
                name.startsWith("Courier") -> "Courier"
                name.startsWith("Helvetica") || name.startsWith("Arial") -> "Helvetica"
                name.startsWith("Times") -> "Times"
                name == "Symbol" -> "Symbol"
                name.startsWith("ZapfDingbats") -> "ZapfDingbats"
                else -> null
            }
        }

        /**
         * `/Widths` 가 없을 때의 어림 폭(글리프 공간). 고정폭은 정확하고, 비례폭은 글자 꼴로 나눈 평균이다 — 상자가 몇 % 어긋날
         * 수 있다(표준 14 의 폭 표를 싣지 않았다).
         */
        private fun standardWidth(std: String?): (Int, String?) -> Double = when (std) {
            "Courier" -> { _, _ -> 600.0 }
            else -> { _, text ->
                val ch = text?.firstOrNull()
                val times = std == "Times"
                when {
                    ch == null -> 500.0
                    ch == ' ' -> if (times) 250.0 else 278.0
                    ch.isDigit() -> if (times) 500.0 else 556.0
                    ch == 'i' || ch == 'l' || ch == 'j' || ch == '.' || ch == ',' || ch == '\'' -> if (times) 278.0 else 222.0
                    ch == 'm' || ch == 'w' -> if (times) 722.0 else 833.0
                    ch.isUpperCase() -> if (times) 667.0 else 667.0
                    else -> if (times) 444.0 else 500.0
                }
            }
        }

        // ---- 합성 글꼴 ----------------------------------------------------------------

        private fun loadComposite(
            objs: PdfObjects,
            dict: PdfDict,
            toUnicode: PdfCMap?,
            cmaps: MutableMap<Int, PdfCMap?>,
        ): PdfFont {
            val encObj = objs.resolve(dict["Encoding"])
            var encodingName: String? = null
            var encoding: PdfCMap? = null
            when (encObj) {
                is PdfName -> encodingName = encObj.name
                else -> (dict["Encoding"] as? PdfRef)?.let { ref ->
                    encoding = cmapOf(objs, ref, cmaps)
                    encodingName = encoding?.usesName
                }
            }
            val name = encodingName.orEmpty()
            val identity = name == "Identity-H" || name == "Identity-V"
            val vertical = name.endsWith("-V") || (objs.dict(dict["Encoding"])?.let { objs.number(it["WMode"]) } == 1.0)
            val charset = if (!identity && name.isNotEmpty()) PredefinedCMaps.charsetOf(name) else null
            // 실린 CMap 이 코드 공간을 말하지 않고 미리 정의된 것을 잇기만 하면, 그 인코딩의 규칙으로 나눈다.
            val charsetName = if (encoding?.codespaces.isNullOrEmpty() && charset != null) charset.name() else null

            val descendant = objs.array(dict["DescendantFonts"])?.items?.firstOrNull()?.let { objs.dict(it) }
            val fd = descendant?.let { objs.dict(it["FontDescriptor"]) }
            val (asc, desc) = descriptorMetrics(objs, fd)
            val dw = descendant?.let { objs.number(it["DW"]) } ?: 1000.0
            val dw2 = descendant?.let { objs.array(it["DW2"]) }?.items?.mapNotNull { objs.number(it) }
                ?.takeIf { it.size >= 2 }?.toDoubleArray() ?: doubleArrayOf(880.0, -1000.0)
            val widths = cidWidths(objs, descendant, dw, dw2[1])
            return PdfFont(
                composite = true,
                vertical = vertical,
                ascent = asc,
                descent = desc,
                toUnicode = toUnicode,
                simple = null,
                cidCodes = CidCodes(encoding, charset, charsetName, identity, widths, dw2),
            )
        }

        private fun cidWidths(objs: PdfObjects, descendant: PdfDict?, dw: Double, dw2y: Double): CidWidths {
            data class Run(val start: Int, val end: Int, val values: DoubleArray?, val single: Double)

            // **값의 수를 표 전체로 묶는다.** 같은 간접 배열을 수만 번 가리키는 `/W` 가 몇 KB 로 수십 GB 를 청했다.
            var budget = MAX_W_VALUES

            fun runs(arr: List<PdfObj>?, stride: Int): List<Run> {
                if (arr == null) return emptyList()
                val out = ArrayList<Run>()
                var i = 0
                while (i < arr.size && out.size < MAX_W_RUNS && budget > 0) {
                    val first = objs.number(arr[i])?.toInt() ?: break
                    val next = objs.resolve(arr.getOrNull(i + 1))
                    if (next is PdfArray) {
                        val vals = next.items.take(minOf(budget, MAX_W_RUNS)).mapNotNull { objs.number(it) }.toDoubleArray()
                        budget -= vals.size + 1
                        if (vals.isNotEmpty()) out += Run(first, first + vals.size / stride - 1, vals, 0.0)
                        i += 2
                    } else {
                        val last = (next as? PdfNum)?.int ?: break
                        val w = objs.number(arr.getOrNull(i + 2)) ?: break
                        if (last >= first) out += Run(first, last, null, w)
                        budget--
                        // `첫 끝 w` 또는 세로(`/W2`)의 `첫 끝 w1y vx vy`.
                        i += 3 + (stride - 1)
                    }
                }
                return out.sortedBy { it.start }
            }

            val h = runs(descendant?.let { objs.array(it["W"]) }?.items, 1)
            val v = runs(descendant?.let { objs.array(it["W2"]) }?.items, 3)
            return CidWidths(
                default = dw,
                starts = h.map { it.start }.toIntArray(),
                ends = h.map { it.end }.toIntArray(),
                values = h.map { it.values }.toTypedArray(),
                single = h.map { it.single }.toDoubleArray(),
                verticalDefaultAdvance = dw2y,
                vStarts = v.map { it.start }.toIntArray(),
                vEnds = v.map { it.end }.toIntArray(),
                vSingle = v.map { it.single }.toDoubleArray(),
                vValues = v.map { it.values }.toTypedArray(),
            )
        }

        private const val MAX_CMAP_BYTES = 4 * 1024 * 1024
        private const val MAX_DIFFERENCES = 4096
        private const val MAX_W_RUNS = 65_536

        /** `/W`·`/W2` 에서 읽는 값의 수 전체(실물 CJK 글꼴은 수만이다). */
        private const val MAX_W_VALUES = 262_144
    }
}
