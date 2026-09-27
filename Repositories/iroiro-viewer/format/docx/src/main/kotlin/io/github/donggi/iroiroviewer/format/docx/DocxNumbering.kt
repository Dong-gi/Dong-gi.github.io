package io.github.donggi.iroiroviewer.format.docx

import io.github.donggi.iroiroviewer.format.opc.OoxmlXml
import io.github.donggi.iroiroviewer.safety.ParseLimits
import org.xmlpull.v1.XmlPullParser

/**
 * 번호 수준 하나(`w:lvl`).
 *
 * @param start 첫 값. 명세의 기본값은 0 이다(워드는 언제나 적는다).
 * @param restart `w:lvlRestart`. null 이면 '더 높은 수준이 나아가면 다시 시작', 0 이면 '다시 시작하지 않음',
 *   n 이면 '수준 n(1 부터) 이상이 나아갈 때만'.
 * @param indFirst 부호 있는 첫 줄 들여쓰기(트윕) — [ParaProps.indFirst] 와 같은 약속.
 * @param font 글머리표 글꼴(`w:rFonts/@ascii`). Symbol·Wingdings 의 사설 영역 글자를 읽을 수 있는
 *   글자로 옮길 때 쓴다.
 */
internal class LevelDef(
    val start: Int,
    val numFmt: String,
    val customFormat: String?,
    val lvlText: String?,
    val restart: Int?,
    val isLegal: Boolean,
    val pStyle: String?,
    val suffix: String,
    val indLeft: Int?,
    val indFirst: Int?,
    val font: String?,
)

/**
 * 번호 하나(`w:num`)를 풀어 낸 것.
 *
 * @param abstractKey 세는 열쇠. **워드는 같은 추상 번호를 쓰는 목록을 이어 센다** — 번호를 새로
 *   시작하려면 새 `w:num` 에 `startOverride` 를 건다(python-docx 도 그렇게 한다). 목록 스타일
 *   (`numStyleLink`)을 따라간 뒤의 추상 번호다.
 */
internal class ResolvedNum(
    val numId: Int,
    val abstractKey: Int,
    val levels: Array<LevelDef?>,
    val startOverrides: Map<Int, Int>,
)

/**
 * `numbering.xml` — 추상 번호(`w:abstractNum`)와 그것을 가리키는 번호(`w:num`).
 *
 * 목록 스타일(`w:numStyleLink` → 번호 스타일 → `numId` → 추상 번호) 사슬은 문서가 적는 값이라
 * 고리가 생길 수 있다. 방문 집합으로 끊는다.
 */
internal class DocxNumbering private constructor(
    private val abstracts: Map<Int, AbstractDef>,
    private val nums: Map<Int, NumDef>,
    private val styles: DocxStyles,
) {
    private class AbstractDef(
        val id: Int,
        val levels: Array<LevelDef?>,
        val numStyleLink: String?,
    )

    private class NumDef(
        val abstractId: Int,
        val startOverrides: Map<Int, Int>,
        val levelOverrides: Map<Int, LevelDef>,
    )

    private val cache = HashMap<Int, ResolvedNum?>()

    /** 번호 id 하나를 푼다. 없거나(`0` 포함) 가리키는 추상 번호가 없으면 null. */
    fun resolve(numId: Int): ResolvedNum? {
        if (cache.containsKey(numId)) return cache[numId]
        val r = doResolve(numId)
        if (cache.size < MAX_NUMS) cache[numId] = r
        return r
    }

    /** 이 스타일에 묶인 수준(`w:lvl/w:pStyle`). 없으면 null. */
    fun levelForStyle(num: ResolvedNum, styleId: String): Int? {
        for (i in num.levels.indices) if (num.levels[i]?.pStyle == styleId) return i
        return null
    }

    private fun doResolve(numId: Int): ResolvedNum? {
        val num = nums[numId] ?: return null
        var abs = abstracts[num.abstractId] ?: return null
        val seen = HashSet<Int>()
        while (true) {
            val link = abs.numStyleLink ?: break
            if (!seen.add(abs.id) || seen.size > MAX_LINKS) break
            val linkedNumId = styles.numberingStyleNumId(link) ?: break
            val linked = nums[linkedNumId] ?: break
            abs = abstracts[linked.abstractId] ?: break
        }
        val levels = Array(LEVELS) { num.levelOverrides[it] ?: abs.levels[it] }
        return ResolvedNum(numId, abs.id, levels, num.startOverrides)
    }

    companion object {
        const val LEVELS = 9
        private const val MAX_ABSTRACTS = 5_000
        private const val MAX_NUMS = 10_000
        private const val MAX_LINKS = 16

        fun empty(styles: DocxStyles) = DocxNumbering(emptyMap(), emptyMap(), styles)

        /** [p] 는 `w:numbering` 에 서 있다. */
        fun parse(p: XmlPullParser, limits: ParseLimits, styles: DocxStyles): DocxNumbering {
            val abstracts = HashMap<Int, AbstractDef>()
            val nums = HashMap<Int, NumDef>()
            DocxProps.eachChild(p, limits) { name ->
                when (name) {
                    "abstractNum" -> {
                        val id = OoxmlXml.int(p, "abstractNumId")
                        val levels = arrayOfNulls<LevelDef>(LEVELS)
                        var link: String? = null
                        DocxProps.eachChild(p, limits) { n ->
                            when (n) {
                                "lvl" -> {
                                    val ilvl = OoxmlXml.int(p, "ilvl")
                                    val def = readLevel(p, limits)
                                    if (ilvl != null && ilvl in 0 until LEVELS && levels[ilvl] == null) levels[ilvl] = def
                                }
                                "numStyleLink" -> {
                                    link = OoxmlXml.attr(p, "val")
                                    OoxmlXml.skip(p, limits)
                                }
                                else -> OoxmlXml.skip(p, limits)
                            }
                        }
                        if (id != null && abstracts.size < MAX_ABSTRACTS && id !in abstracts) {
                            abstracts[id] = AbstractDef(id, levels, link)
                        }
                    }
                    "num" -> {
                        val numId = OoxmlXml.int(p, "numId")
                        var abstractId: Int? = null
                        val starts = HashMap<Int, Int>()
                        val overrides = HashMap<Int, LevelDef>()
                        DocxProps.eachChild(p, limits) { n ->
                            when (n) {
                                "abstractNumId" -> {
                                    abstractId = OoxmlXml.int(p, "val")
                                    OoxmlXml.skip(p, limits)
                                }
                                "lvlOverride" -> {
                                    val ilvl = OoxmlXml.int(p, "ilvl")
                                    DocxProps.eachChild(p, limits) { o ->
                                        when (o) {
                                            "startOverride" -> {
                                                val v = OoxmlXml.int(p, "val")
                                                if (ilvl != null && ilvl in 0 until LEVELS && v != null) starts[ilvl] = v
                                                OoxmlXml.skip(p, limits)
                                            }
                                            "lvl" -> {
                                                val def = readLevel(p, limits)
                                                if (ilvl != null && ilvl in 0 until LEVELS) overrides[ilvl] = def
                                            }
                                            else -> OoxmlXml.skip(p, limits)
                                        }
                                    }
                                }
                                else -> OoxmlXml.skip(p, limits)
                            }
                        }
                        val a = abstractId
                        if (numId != null && a != null && nums.size < MAX_NUMS && numId !in nums) {
                            nums[numId] = NumDef(a, starts, overrides)
                        }
                    }
                    else -> OoxmlXml.skip(p, limits)
                }
            }
            return DocxNumbering(abstracts, nums, styles)
        }

        /** [p] 는 `w:lvl` 에 서 있다. */
        private fun readLevel(p: XmlPullParser, limits: ParseLimits): LevelDef {
            val b = LevelBuilder()
            readLevelChildren(p, limits, b)
            return LevelDef(
                start = b.start ?: 0,
                numFmt = b.numFmt ?: "decimal",
                customFormat = b.customFormat,
                lvlText = b.lvlText,
                restart = b.restart,
                isLegal = b.isLegal,
                pStyle = b.pStyle,
                suffix = b.suffix ?: "tab",
                indLeft = b.indLeft,
                indFirst = b.indFirst,
                font = b.font,
            )
        }

        private class LevelBuilder {
            var start: Int? = null
            var numFmt: String? = null
            var customFormat: String? = null
            var lvlText: String? = null
            var restart: Int? = null
            var isLegal = false
            var pStyle: String? = null
            var suffix: String? = null
            var indLeft: Int? = null
            var indFirst: Int? = null
            var font: String? = null
        }

        private fun readLevelChildren(p: XmlPullParser, limits: ParseLimits, b: LevelBuilder) {
            DocxProps.eachChild(p, limits) { n ->
                when (n) {
                    "start" -> b.start = OoxmlXml.int(p, "val")
                    "numFmt" -> {
                        b.numFmt = OoxmlXml.attr(p, "val")
                        b.customFormat = OoxmlXml.attr(p, "format")?.take(64)
                    }
                    "lvlText" -> b.lvlText = OoxmlXml.attr(p, "val")?.take(64)
                    "lvlRestart" -> b.restart = OoxmlXml.int(p, "val")
                    "isLgl" -> b.isLegal = OoxmlXml.onOff(p)
                    "pStyle" -> b.pStyle = OoxmlXml.attr(p, "val")
                    "suff" -> b.suffix = OoxmlXml.attr(p, "val")
                    "pPr" -> {
                        val pp = DocxProps.readPPr(p, limits)
                        b.indLeft = pp.indLeft
                        b.indFirst = pp.indFirst
                        return@eachChild
                    }
                    "rPr" -> {
                        DocxProps.eachChild(p, limits) { r ->
                            if (r == "rFonts") b.font = OoxmlXml.attr(p, "ascii") ?: OoxmlXml.attr(p, "hAnsi")
                            OoxmlXml.skip(p, limits)
                        }
                        return@eachChild
                    }
                    // 2010 년 이후의 번호 모양(`custom` 등)은 호환 블록 안에 온다.
                    "AlternateContent" -> {
                        DocxCompat.choose(p, limits) { readLevelChildren(p, limits, b) }
                        return@eachChild
                    }
                }
                OoxmlXml.skip(p, limits)
            }
        }
    }
}

/**
 * 목록 번호를 센다 — **복사할 수 있는** 상태다. 긴 문서를 조각으로 나눌 때 조각의 시작에서 이
 * 상태를 찍어 두어야 뒤 조각의 번호가 1 부터 다시 시작하지 않는다.
 */
internal class ListCounters private constructor(
    private val values: HashMap<Int, IntArray>,
    private val used: HashMap<Int, BooleanArray>,
    private val pending: HashMap<Int, IntArray>,
    private val seenNums: HashSet<Int>,
) {
    constructor() : this(HashMap(), HashMap(), HashMap(), HashSet())

    fun copy(): ListCounters = ListCounters(
        HashMap(values.mapValues { it.value.copyOf() }),
        HashMap(used.mapValues { it.value.copyOf() }),
        HashMap(pending.mapValues { it.value.copyOf() }),
        HashSet(seenNums),
    )

    /**
     * [copy] 하나가 차지하는 대략의 바이트 — 목록마다 표 셋의 항목과 배열 셋(약 300), 쓴 번호마다
     * 집합 항목 하나(약 50). 조각 경계의 상한(`ChunkPolicy.snapshotBudget`)이 쓴다.
     */
    fun approxBytes(): Long = values.size * 300L + seenNums.size * 50L

    /**
     * 수준 [ilvl] 을 한 칸 나아가고 그 문단의 **표지 글자**를 돌려준다(없으면 빈 문자열).
     *
     * 규칙은 셋이다. ① 처음 쓰는 수준은 시작값에서 연다. ② 한 수준이 나아가면 그보다 깊은 수준은
     * `lvlRestart` 가 허락하는 만큼 다시 시작한다. ③ 번호(`w:num`)의 `startOverride` 는 그 번호가
     * **처음 쓰일 때** 한 번 건다 — 같은 추상 번호의 다른 목록이 이어 세던 것을 거기서 끊는다.
     */
    fun next(num: ResolvedNum, ilvl: Int): String {
        val key = num.abstractKey
        if (!values.containsKey(key) && values.size >= MAX_LISTS) return ""
        val vals = values.getOrPut(key) { IntArray(DocxNumbering.LEVELS) }
        val usedArr = used.getOrPut(key) { BooleanArray(DocxNumbering.LEVELS) }
        val pend = pending.getOrPut(key) { IntArray(DocxNumbering.LEVELS) { NONE } }
        if (num.startOverrides.isNotEmpty() && seenNums.size < MAX_LISTS && seenNums.add(num.numId)) {
            for ((lvl, v) in num.startOverrides) {
                usedArr[lvl] = false
                pend[lvl] = v
            }
        }
        val level = num.levels[ilvl]
        if (!usedArr[ilvl]) {
            vals[ilvl] = if (pend[ilvl] != NONE) pend[ilvl] else level?.start ?: 0
            pend[ilvl] = NONE
            usedArr[ilvl] = true
        } else if (vals[ilvl] < Int.MAX_VALUE) {
            vals[ilvl]++
        }
        for (k in ilvl + 1 until DocxNumbering.LEVELS) {
            val restart = num.levels[k]?.restart
            if (restart == null || (restart != 0 && ilvl < restart)) usedArr[k] = false
        }
        if (level == null) return ""
        return ListMarkers.marker(level, ilvl) { j ->
            when {
                usedArr[j] -> vals[j]
                pend[j] != NONE -> pend[j]
                else -> num.levels[j]?.start ?: 0
            } to (num.levels[j]?.numFmt ?: "decimal")
        }
    }

    companion object {
        private const val NONE = Int.MIN_VALUE
        private const val MAX_LISTS = 10_000
    }
}

/**
 * 번호 모양. 값과 모양 이름(`w:numFmt`)을 글자로 옮긴다.
 *
 * 한국어 모양(가나다·ㄱㄴㄷ·원문자·일이삼·하나둘셋)을 먼저 챙긴다 — 이 앱의 사용자가 가장 자주
 * 만나는 문서가 한국어 워드·한컴 문서다. 모르는 모양은 아라비아 숫자로 쓴다(번호가 아예 없는
 * 것보다 낫다).
 */
internal object ListMarkers {

    /**
     * 수준 하나의 표지. `lvlText` 의 `%1`..`%9` 를 각 수준의 값으로 바꾼다.
     *
     * @param valueOf 수준 번호 → (값, 모양).
     */
    fun marker(level: LevelDef, ilvl: Int, valueOf: (Int) -> Pair<Int, String>): String {
        if (level.numFmt == "bullet") return bullet(level.lvlText, level.font)
        val template = level.lvlText ?: return ""
        val out = StringBuilder(template.length + 8)
        var i = 0
        while (i < template.length) {
            val c = template[i]
            if (c == '%' && i + 1 < template.length && template[i + 1] in '1'..'9') {
                val j = template[i + 1] - '1'
                val (value, fmt) = valueOf(j)
                // 법률식(`isLgl`)이면 상위 수준도 아라비아 숫자로 쓴다(`1.1.3`, `I.1.3` 이 아니라).
                val shown = if (level.isLegal && j < ilvl) "decimal" else if (j == ilvl) level.numFmt else fmt
                out.append(format(value, shown, if (j == ilvl) level.customFormat else null))
                i += 2
                continue
            }
            out.append(mapPrivateUse(c, level.font))
            i++
        }
        return out.toString()
    }

    /** 값 하나를 모양대로. */
    fun format(value: Int, numFmt: String, customFormat: String? = null): String = when (numFmt) {
        "none" -> ""
        "decimal", "decimalHalfWidth" -> value.toString()
        "decimalZero" -> if (value in 0..9) "0$value" else value.toString()
        "upperRoman" -> roman(value)?.uppercase() ?: value.toString()
        "lowerRoman" -> roman(value) ?: value.toString()
        "upperLetter" -> letters(value)?.uppercase() ?: value.toString()
        "lowerLetter" -> letters(value) ?: value.toString()
        "ordinal" -> ordinal(value)
        "ganada" -> cycle(value, GANADA)
        "chosung" -> cycle(value, CHOSUNG)
        "decimalEnclosedCircle", "decimalEnclosedCircleChinese" -> enclosed(value, 0x2460, 20)
        "decimalEnclosedParen" -> enclosed(value, 0x2474, 20)
        "decimalEnclosedFullstop" -> enclosed(value, 0x2488, 20)
        "decimalFullWidth", "decimalFullWidth2" -> fullWidth(value)
        "koreanDigital" -> sinoKorean(value, KOREAN_DIGITS, KOREAN_UNITS) ?: value.toString()
        "koreanDigital2", "ideographTraditional", "chineseCounting", "japaneseCounting", "ideographDigital" ->
            sinoKorean(value, HANJA_DIGITS, HANJA_UNITS) ?: value.toString()
        // 명세의 설명만으로는 둘이 갈리지 않는다. 둘 다 고유어 수사로 쓴다(확인하지 못했다).
        "koreanCounting", "koreanLegal" -> nativeKorean(value) ?: value.toString()
        "custom" -> custom(value, customFormat)
        else -> value.toString()
    }

    /**
     * 글머리표. 워드는 Symbol·Wingdings 글꼴의 **사설 영역 글자**(U+F0B7 등)를 적는데, 그 글꼴이 없는
     * 기기에서는 두부(□)가 된다. 흔한 것은 뜻이 같은 유니코드 글자로 옮기고, 모르는 사설 영역 글자는
     * 가운뎃점으로 쓴다.
     */
    fun bullet(text: String?, font: String?): String {
        if (text.isNullOrEmpty()) return "•"
        val sb = StringBuilder(text.length)
        for (c in text) sb.append(mapPrivateUse(c, font))
        return sb.toString()
    }

    /** 표지 안의 글자 하나. 옮길 수 없는 사설 영역 글자는 가운뎃점으로 — 두부보다 낫다. */
    private fun mapPrivateUse(c: Char, font: String?): String {
        val code = c.code
        glyph(code, font)?.let { return it }
        return if (code in 0xE000..0xF8FF) "•" else c.toString()
    }

    /**
     * 기호 글꼴의 글자 하나를 유니코드로. 옮길 필요가 없는 보통 글자는 그대로, 모르는 기호는 null.
     *
     * 워드는 기호 글꼴의 글자를 `U+F0xx`(사설 영역) 또는 ASCII 자리(`§` + Wingdings)로 적는다.
     * **글꼴을 모르면 흔한 글머리표 표**로 짐작한다 — `U+F0A7` 은 Symbol 이면 ♣ 이지만 실제 문서에서는
     * 언제나 Wingdings 의 작은 네모(▪)다.
     */
    fun glyph(code: Int, font: String?): String? {
        val wingdings = font?.contains("Wingdings", ignoreCase = true) == true
        val symbol = font?.contains("Symbol", ignoreCase = true) == true
        val low = when {
            code in 0xF000..0xF0FF -> code - 0xF000
            (wingdings || symbol) && code in 0x20..0xFF -> code
            code in 0xE000..0xF8FF -> return null
            else -> return code.toChar().toString()
        }
        return when {
            wingdings -> WINGDINGS[low]
            symbol -> SymbolFont.map(low)
            else -> GUESS[low] ?: SymbolFont.map(low)
        }
    }

    /**
     * Wingdings 의 흔한 글머리표. 모르는 자리는 null. `v`(0x76)는 네 쪽 마름모(❖)이고 검은 마름모(◆)는 `u`(0x75)다 —
     * 워드의 글머리표 목록에 나오는 것은 앞의 것이다.
     */
    private val WINGDINGS = mapOf(
        0x6C to "●", 0x6E to "■", 0x6F to "□", 0x71 to "❑", 0x75 to "◆", 0x76 to "❖",
        0xA7 to "▪", 0xD8 to "➢", 0xE8 to "➔", 0xFB to "✗", 0xFC to "✓",
        0xFD to "☒", 0xFE to "☑", 0x4A to "☺", 0x4C to "☹", 0x9F to "•",
    )

    /** 글꼴을 모를 때 — 워드의 기본 글머리표 목록에 나오는 것들(Symbol 의 •, 나머지는 Wingdings). */
    private val GUESS = mapOf(
        0xB7 to "•", 0xA7 to "▪", 0xD8 to "➢", 0x76 to "❖", 0xFC to "✓",
        0x6E to "■", 0x71 to "❑", 0x6C to "●",
    )

    private val GANADA = "가나다라마바사아자차카타파하"
    private val CHOSUNG = "ㄱㄴㄷㄹㅁㅂㅅㅇㅈㅊㅋㅌㅍㅎ"
    private val KOREAN_DIGITS = arrayOf("", "일", "이", "삼", "사", "오", "육", "칠", "팔", "구")
    private val KOREAN_UNITS = arrayOf("", "십", "백", "천")
    private val HANJA_DIGITS = arrayOf("", "一", "二", "三", "四", "五", "六", "七", "八", "九")
    private val HANJA_UNITS = arrayOf("", "十", "百", "千")
    private val NATIVE_ONES = arrayOf("", "하나", "둘", "셋", "넷", "다섯", "여섯", "일곱", "여덟", "아홉")
    private val NATIVE_TENS = arrayOf("", "열", "스물", "서른", "마흔", "쉰", "예순", "일흔", "여든", "아흔")

    private fun cycle(value: Int, letters: String): String =
        if (value < 1) value.toString() else letters[(value - 1) % letters.length].toString()

    private fun enclosed(value: Int, base: Int, count: Int): String =
        if (value in 1..count) (base + value - 1).toChar().toString() else value.toString()

    private fun fullWidth(value: Int): String {
        val s = value.toString()
        val sb = StringBuilder(s.length)
        for (c in s) sb.append(if (c in '0'..'9') (0xFF10 + (c - '0')).toChar() else c)
        return sb.toString()
    }

    /** 1..9999 를 일·십·백·천으로. 십·백·천 앞의 '일' 은 읽지 않는다(십일, 백). */
    private fun sinoKorean(value: Int, digits: Array<String>, units: Array<String>): String? {
        if (value !in 1..9999) return null
        val sb = StringBuilder()
        var rest = value
        for (pos in 3 downTo 0) {
            var unit = 1
            repeat(pos) { unit *= 10 }
            val d = rest / unit
            rest %= unit
            if (d == 0) continue
            if (!(d == 1 && pos > 0)) sb.append(digits[d])
            sb.append(units[pos])
        }
        return sb.toString()
    }

    /** 1..99 를 하나·둘·열·스물로. */
    private fun nativeKorean(value: Int): String? {
        if (value !in 1..99) return null
        return NATIVE_TENS[value / 10] + NATIVE_ONES[value % 10]
    }

    private fun roman(value: Int): String? {
        if (value !in 1..3999) return null
        val sb = StringBuilder()
        var n = value
        for ((v, s) in ROMAN) {
            while (n >= v) {
                sb.append(s)
                n -= v
            }
        }
        return sb.toString()
    }

    private val ROMAN = listOf(
        1000 to "m", 900 to "cm", 500 to "d", 400 to "cd", 100 to "c", 90 to "xc",
        50 to "l", 40 to "xl", 10 to "x", 9 to "ix", 5 to "v", 4 to "iv", 1 to "i",
    )

    /** 워드의 글자 번호는 26 을 넘으면 **같은 글자를 겹친다**(27 → aa, 28 → bb). 너무 크면 숫자로. */
    private fun letters(value: Int): String? {
        if (value < 1 || value > 26 * 30) return null
        val c = 'a' + (value - 1) % 26
        val times = (value - 1) / 26 + 1
        val sb = StringBuilder(times)
        repeat(times) { sb.append(c) }
        return sb.toString()
    }

    private fun ordinal(value: Int): String {
        val mod100 = value % 100
        val suffix = if (mod100 in 11..13) "th" else when (value % 10) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
        return "$value$suffix"
    }

    /**
     * 워드 2010 의 `custom` 모양. 흔한 것은 0 을 채운 자릿수(`001, 002, 003, ...`)뿐이라 그것만 읽고,
     * 나머지는 숫자로 쓴다.
     */
    private fun custom(value: Int, format: String?): String {
        val first = format?.substringBefore(',')?.trim().orEmpty()
        if (first.isNotEmpty() && first.length <= 9 && first.all { it in '0'..'9' }) {
            val s = value.toString()
            if (s.length >= first.length) return s
            val sb = StringBuilder(first.length)
            repeat(first.length - s.length) { sb.append('0') }
            return sb.append(s).toString()
        }
        return value.toString()
    }
}

/**
 * Symbol 글꼴의 자리 → 유니코드. `w:sym`(`w:font="Symbol"`)과 글머리표가 쓴다.
 * 그리스 문자와 흔한 수학 기호만 옮긴다. 모르는 자리는 null.
 */
internal object SymbolFont {
    fun map(code: Int): String? {
        if (code in 0x20..0x7E) {
            GREEK_AND_MATH[code]?.let { return it }
            // 나머지 ASCII 자리(숫자·괄호·연산자)는 Symbol 에서도 같은 글자다.
            if (code in 0x21..0x3F || code == 0x5B || code == 0x5D || code == 0x5F || code in 0x7B..0x7D) {
                return code.toChar().toString()
            }
            return null
        }
        return HIGH[code]
    }

    private val GREEK_AND_MATH: Map<Int, String> = buildMap {
        put(0x22, "∀"); put(0x24, "∃"); put(0x27, "∋"); put(0x2A, "∗"); put(0x2D, "−")
        put(0x40, "≅"); put(0x5C, "∴"); put(0x5E, "⊥"); put(0x7E, "∼")
        val upper = "ΑΒΧΔΕΦΓΗΙϑΚΛΜΝΟΠΘΡΣΤΥςΩΞΨΖ"
        for (i in upper.indices) put(0x41 + i, upper[i].toString())
        val lower = "αβχδεφγηιϕκλμνοπθρστυϖωξψζ"
        for (i in lower.indices) put(0x61 + i, lower[i].toString())
    }

    private val HIGH: Map<Int, String> = mapOf(
        0xA1 to "ϒ", 0xA2 to "′", 0xA3 to "≤", 0xA4 to "⁄", 0xA5 to "∞",
        0xA6 to "ƒ", 0xA7 to "♣", 0xA8 to "♦", 0xA9 to "♥", 0xAA to "♠",
        0xAB to "↔", 0xAC to "←", 0xAD to "↑", 0xAE to "→", 0xAF to "↓",
        0xB0 to "°", 0xB1 to "±", 0xB2 to "″", 0xB3 to "≥", 0xB4 to "×",
        0xB5 to "∝", 0xB6 to "∂", 0xB7 to "•", 0xB8 to "÷", 0xB9 to "≠",
        0xBA to "≡", 0xBB to "≈", 0xBC to "…", 0xBF to "↵", 0xC0 to "ℵ",
        0xC1 to "ℑ", 0xC2 to "ℜ", 0xC3 to "℘", 0xC4 to "⊗", 0xC5 to "⊕",
        0xC6 to "∅", 0xC7 to "∩", 0xC8 to "∪", 0xC9 to "⊃", 0xCA to "⊇",
        0xCB to "⊄", 0xCC to "⊂", 0xCD to "⊆", 0xCE to "∈", 0xCF to "∉",
        0xD0 to "∠", 0xD1 to "∇", 0xD2 to "®", 0xD3 to "©", 0xD4 to "™",
        0xD5 to "∏", 0xD6 to "√", 0xD7 to "⋅", 0xD8 to "¬", 0xD9 to "∧",
        0xDA to "∨", 0xDB to "⇔", 0xDC to "⇐", 0xDD to "⇑", 0xDE to "⇒",
        0xDF to "⇓", 0xE0 to "◊", 0xE1 to "〈", 0xE5 to "∑", 0xF1 to "〉",
        0xF2 to "∫",
    )
}
