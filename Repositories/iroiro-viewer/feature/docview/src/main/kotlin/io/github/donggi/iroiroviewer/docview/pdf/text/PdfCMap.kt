package io.github.donggi.iroiroviewer.docview.pdf.text

import io.github.donggi.iroiroviewer.docview.pdf.crypt.ByteArraySource
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfArray
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfCursor
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfName
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfNum
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfObj
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfParser
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfStr
import io.github.donggi.iroiroviewer.docview.pdf.crypt.PdfSyntaxException
import java.nio.charset.Charset

/**
 * CMap — 코드를 글자로(`/ToUnicode`), 또는 코드를 CID 로(Type0 글꼴의 `/Encoding` 스트림). 명세 9.10.3 과 Adobe 기술 노트
 * 5014·5411 의 문법이다.
 *
 * ## 무엇을 읽는가
 *
 * `codespacerange`(코드가 몇 바이트인가), `bfchar`·`bfrange`(→ 글자), `cidchar`·`cidrange`(→ CID). `usecmap` 은 이름만
 * 기억한다(미리 정의된 CMap 을 잇는 것인데 그 파일들을 싣지 않았다 — [PredefinedCMaps]).
 *
 * ## 막는 것
 *
 * 남이 만든 스트림이다. 항목 수를 [MAX_ENTRIES] 에서 자르고, 범위 하나가 덮는 코드 수는 세지 않는다(범위는 범위로 두고
 * 찾을 때 계산한다 — `<0000> <FFFF>` 하나를 6만 개로 펼치지 않는다).
 */
internal class PdfCMap private constructor(
    /** 바이트 수별 코드 공간(`lo..hi`, 길이). 비어 있으면 모른다. */
    val codespaces: List<Codespace>,
    private val chars: HashMap<Long, String>,
    /** **(길이, 시작) 차례로 늘어놓았다** — 코드마다 범위를 처음부터 훑으면 20만 범위 × 수백만 코드가 된다. */
    private val ranges: List<BfRange>,
    private val cidChars: HashMap<Long, Int>,
    private val cidRanges: List<CidRange>,
    /** `usecmap` 이 이은 미리 정의된 CMap 의 이름. */
    val usesName: String?,
) {
    data class Codespace(val length: Int, val lo: Long, val hi: Long)

    private class BfRange(val length: Int, val lo: Long, val hi: Long, val base: String?, val list: List<String>?)

    private class CidRange(val length: Int, val lo: Long, val hi: Long, val cid: Int)

    val hasUnicode: Boolean get() = chars.isNotEmpty() || ranges.isNotEmpty()

    val hasCids: Boolean get() = cidChars.isNotEmpty() || cidRanges.isNotEmpty()

    /** 코드([length] 바이트, 큰 쪽이 앞)의 글자. 모르면 null. */
    fun unicode(code: Long, length: Int): String? {
        chars[key(code, length)]?.let { return it }
        val r = findRange(ranges.size, { ranges[it].length }, { ranges[it].lo }, { ranges[it].hi }, code, length)
            ?.let { ranges[it] } ?: return null
        val off = (code - r.lo).toInt()
        if (r.list != null) return r.list.getOrNull(off)
        val base = r.base ?: return null
        if (base.isEmpty()) return null
        // 마지막 UTF-16 단위를 늘린다(pdf.js 와 같은 판단 — 명세는 '마지막 바이트' 라고 적지만 두 바이트 글자가 흔하다).
        val last = base[base.length - 1].code + off
        if (last > 0xFFFF) return null
        return base.substring(0, base.length - 1) + last.toChar()
    }

    /** 코드의 CID. 모르면 null. */
    fun cid(code: Long, length: Int): Int? {
        cidChars[key(code, length)]?.let { return it }
        val i = findRange(cidRanges.size, { cidRanges[it].length }, { cidRanges[it].lo }, { cidRanges[it].hi }, code, length)
            ?: return null
        val r = cidRanges[i]
        return r.cid + (code - r.lo).toInt()
    }

    /**
     * (길이, 시작) 차례로 늘어놓은 범위들에서 [code] 를 담은 것 — 시작이 [code] 이하인 마지막 범위부터 몇 개 앞까지 본다
     * (범위가 조금 겹친 CMap 도 받는다). 이분 탐색이라 범위가 20만이어도 코드 하나에 스무 걸음 남짓이다.
     */
    private inline fun findRange(
        size: Int,
        lengthOf: (Int) -> Int,
        loOf: (Int) -> Long,
        hiOf: (Int) -> Long,
        code: Long,
        length: Int,
    ): Int? {
        var lo = 0
        var hi = size - 1
        var at = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val l = lengthOf(mid)
            if (l < length || (l == length && loOf(mid) <= code)) {
                at = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        var i = at
        var steps = 0
        while (i >= 0 && steps < OVERLAP_STEPS && lengthOf(i) == length) {
            if (code >= loOf(i) && code <= hiOf(i)) return i
            i--
            steps++
        }
        return null
    }

    /**
     * 바이트열에서 코드 하나의 길이 — 코드 공간이 정한다. 맞는 공간이 없으면 가장 짧은 공간의 길이(명세가 그렇게 하라고
     * 한다 — 그 바이트들을 '정의되지 않은 코드' 하나로 친다).
     */
    fun codeLength(bytes: ByteArray, at: Int): Int {
        if (codespaces.isEmpty()) return 0
        for (cs in codespaces) {
            if (at + cs.length > bytes.size) continue
            var v = 0L
            for (i in 0 until cs.length) v = (v shl 8) or (bytes[at + i].toLong() and 0xFF)
            if (v in cs.lo..cs.hi && sameShape(v, cs)) return cs.length
        }
        return codespaces.minOf { it.length }.coerceAtMost(bytes.size - at).coerceAtLeast(1)
    }

    /** 코드 공간은 바이트마다의 범위다(`<8140> <FEFE>` 는 첫 바이트 81~FE, 둘째 40~FE). 수로 견주면 그 사이가 샌다. */
    private fun sameShape(v: Long, cs: Codespace): Boolean {
        for (i in 0 until cs.length) {
            val shift = 8 * (cs.length - 1 - i)
            val b = (v shr shift) and 0xFF
            val lo = (cs.lo shr shift) and 0xFF
            val hi = (cs.hi shr shift) and 0xFF
            if (b < lo || b > hi) return false
        }
        return true
    }

    companion object {
        const val MAX_ENTRIES = 200_000
        private const val MAX_CODESPACES = 64

        /** 겹친 범위를 찾으러 거슬러 보는 수. */
        private const val OVERLAP_STEPS = 8

        private fun key(code: Long, length: Int): Long = (length.toLong() shl 40) or code

        /** CMap 스트림을 읽는다. 문법이 틀렸으면 읽은 데까지 쓴다. */
        fun parse(data: ByteArray): PdfCMap {
            val codespaces = ArrayList<Codespace>()
            val chars = HashMap<Long, String>()
            val ranges = ArrayList<BfRange>()
            val cidChars = HashMap<Long, Int>()
            val cidRanges = ArrayList<CidRange>()
            var uses: String? = null
            var count = 0
            val parser = PdfParser(PdfCursor(ByteArraySource(data), 0))
            var prev: PdfObj? = null

            fun next(): PdfObj? = try {
                parser.parseObject()
            } catch (e: PdfSyntaxException) {
                null
            }

            loop@ while (count < MAX_ENTRIES) {
                val tok = next() ?: break
                if (tok !is PdfParser.Keyword) {
                    prev = tok
                    continue
                }
                when (tok.text) {
                    "usecmap" -> uses = (prev as? PdfName)?.name
                    "begincodespacerange" -> while (true) {
                        val lo = next() as? PdfStr ?: break
                        val hi = next() as? PdfStr ?: break
                        if (lo.bytes.isEmpty() || lo.bytes.size != hi.bytes.size || lo.bytes.size > 4) continue
                        codespaces += Codespace(lo.bytes.size, toLong(lo.bytes), toLong(hi.bytes))
                        if (++count >= MAX_ENTRIES) break@loop
                    }
                    "beginbfchar" -> while (true) {
                        val src = next() as? PdfStr ?: break
                        val dst = next() ?: break
                        if (src.bytes.isEmpty() || src.bytes.size > 4) continue
                        val text = when (dst) {
                            is PdfStr -> utf16(dst.bytes)
                            is PdfName -> PdfEncodings.unicodeOf(dst.name)
                            else -> null
                        } ?: continue
                        chars[key(toLong(src.bytes), src.bytes.size)] = text
                        if (++count >= MAX_ENTRIES) break@loop
                    }
                    "beginbfrange" -> while (true) {
                        val lo = next() as? PdfStr ?: break
                        val hi = next() as? PdfStr ?: break
                        val dst = next() ?: break
                        if (lo.bytes.isEmpty() || lo.bytes.size != hi.bytes.size || lo.bytes.size > 4) continue
                        val l = toLong(lo.bytes)
                        val h = toLong(hi.bytes)
                        if (h < l) continue
                        ranges += when (dst) {
                            is PdfStr -> BfRange(lo.bytes.size, l, h, utf16(dst.bytes), null)
                            is PdfArray -> BfRange(
                                lo.bytes.size, l, h, null,
                                dst.items.take(MAX_ENTRIES).map { (it as? PdfStr)?.let { s -> utf16(s.bytes) } ?: "" },
                            )
                            else -> continue
                        }
                        if (++count >= MAX_ENTRIES) break@loop
                    }
                    "begincidchar" -> while (true) {
                        val src = next() as? PdfStr ?: break
                        val cid = (next() as? PdfNum)?.int ?: break
                        if (src.bytes.isEmpty() || src.bytes.size > 4) continue
                        cidChars[key(toLong(src.bytes), src.bytes.size)] = cid
                        if (++count >= MAX_ENTRIES) break@loop
                    }
                    "begincidrange" -> while (true) {
                        val lo = next() as? PdfStr ?: break
                        val hi = next() as? PdfStr ?: break
                        val cid = (next() as? PdfNum)?.int ?: break
                        if (lo.bytes.isEmpty() || lo.bytes.size != hi.bytes.size || lo.bytes.size > 4) continue
                        cidRanges += CidRange(lo.bytes.size, toLong(lo.bytes), toLong(hi.bytes), cid)
                        if (++count >= MAX_ENTRIES) break@loop
                    }
                }
                prev = tok
            }
            // 긴 코드 공간을 먼저 본다 — 1바이트 공간이 2바이트 코드의 첫 바이트를 먼저 먹지 않게. 코드 공간은 코드마다 훑으므로
            // 수를 묶는다(실물 CMap 은 열을 넘지 않는다).
            codespaces.sortByDescending { it.length }
            val spaces = if (codespaces.size > MAX_CODESPACES) codespaces.subList(0, MAX_CODESPACES).toList() else codespaces
            ranges.sortWith(compareBy<BfRange>({ it.length }, { it.lo }))
            cidRanges.sortWith(compareBy<CidRange>({ it.length }, { it.lo }))
            return PdfCMap(spaces, chars, ranges, cidChars, cidRanges, uses)
        }

        private fun toLong(b: ByteArray): Long {
            var v = 0L
            for (x in b) v = (v shl 8) or (x.toLong() and 0xFF)
            return v
        }

        /** 목적지 글자는 UTF-16BE 다. 한 바이트로 적은 것(명세 위반이지만 흔하다)은 Latin-1 로 읽는다. */
        private fun utf16(b: ByteArray): String? {
            if (b.isEmpty()) return ""
            if (b.size == 1) return (b[0].toInt() and 0xFF).toChar().toString()
            val s = String(b, Charsets.UTF_16BE)
            return s.takeIf { it.none { c -> c == '�' } } ?: s
        }
    }
}

/**
 * 이름으로만 오는 **미리 정의된 CMap**(`KSCms-UHC-H` 등). Adobe 의 CMap 파일들을 싣지 않았으므로 CID 는 모른다 — 대신
 * 그 CMap 이 따르는 **문자 인코딩**으로 바이트를 바로 글자로 읽는다. 한국어 옛 PDF(글꼴을 싣지 않고 `HYGoThic-Medium` 에
 * `KSCms-UHC-H` 를 거는 문서)가 그 모양이다.
 *
 * 폭은 CID 를 몰라 `/DW` 로 친다 — 한 바이트 코드(ASCII)는 그 절반이다(전각·반각). 고르는 상자가 조금 어긋날 수 있다.
 */
internal object PredefinedCMaps {

    /** 이 이름의 CMap 이 따르는 인코딩. 모르는 이름이면 null. `Identity-H/V` 도 null 이다(글자를 모른다). */
    fun charsetOf(name: String): Charset? {
        val base = name.removeSuffix("-H").removeSuffix("-V")
        val cs = when {
            base.startsWith("Uni") && (base.endsWith("UCS2") || base.endsWith("UTF16")) -> "UTF-16BE"
            base.startsWith("Uni") && base.endsWith("UTF8") -> "UTF-8"
            base.startsWith("Uni") && base.endsWith("UTF32") -> "UTF-32BE"
            base == "KSCms-UHC" || base == "KSCms-UHC-HW" || base == "KSCpc-EUC" -> "x-windows-949"
            base == "KSC-EUC" -> "EUC-KR"
            base == "90ms-RKSJ" || base == "90msp-RKSJ" || base == "90pv-RKSJ" || base == "83pv-RKSJ" -> "windows-31j"
            base == "EUC" -> "EUC-JP"
            base == "GBK-EUC" || base == "GBKp-EUC" || base == "GBpc-EUC" -> "GBK"
            base == "GB-EUC" -> "GB2312"
            base == "GBK2K" -> "GB18030"
            base == "B5pc" || base == "ETen-B5" || base == "HKscs-B5" || base == "ETenms-B5" -> "Big5"
            else -> null
        } ?: return null
        return charset(cs)
    }

    /** 이 기기(JVM)에 그 인코딩이 없으면 별칭을 찾는다 — 안드로이드는 CP949 를 `EUC-KR` 로 둔다(CLAUDE.md 문자셋 표). */
    private fun charset(name: String): Charset? {
        val tries = when (name) {
            "x-windows-949" -> listOf("x-windows-949", "MS949", "windows-949", "EUC-KR")
            "windows-31j" -> listOf("windows-31j", "MS932", "Shift_JIS")
            else -> listOf(name)
        }
        for (t in tries) {
            try {
                if (Charset.isSupported(t)) return Charset.forName(t)
            } catch (e: IllegalArgumentException) {
                // 이름 꼴이 틀렸다 — 다음 후보.
            }
        }
        return null
    }

    /**
     * 그 인코딩에서 코드 하나의 바이트 수. 앞 바이트로 정한다(각 인코딩의 이중 바이트 규칙).
     */
    fun codeLength(charsetName: String, bytes: ByteArray, at: Int): Int {
        val b = bytes[at].toInt() and 0xFF
        val two = when (charsetName.uppercase()) {
            "UTF-16BE" -> return if (at + 1 < bytes.size && (b and 0xFC) == 0xD8 && at + 3 < bytes.size) 4 else 2
            "UTF-8" -> return when {
                b < 0x80 -> 1
                b < 0xE0 -> 2
                b < 0xF0 -> 3
                else -> 4
            }.coerceAtMost(bytes.size - at)
            "UTF-32BE" -> return 4.coerceAtMost(bytes.size - at)
            "SHIFT_JIS", "WINDOWS-31J", "MS932" -> b in 0x81..0x9F || b in 0xE0..0xFC
            "EUC-JP" -> b == 0x8E || b in 0xA1..0xFE
            else -> b in 0x81..0xFE // CP949·EUC-KR·GBK·GB2312·GB18030(두 바이트까지)·Big5
        }
        return if (two && at + 1 < bytes.size) 2 else 1
    }
}
