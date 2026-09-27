package io.github.donggi.iroiroviewer.format.html

/**
 * 한글의 번호 모양 — 문단 번호·개요 번호·각주/미주·자동 번호가 같은 표를 쓴다. **두 한글 변환기가 이 표 하나를 쓴다.**
 *
 * 모양 번호는 HWP 5.0 의 값(명세 표 41·134)이다. HWPX 는 이름(`NumberType1`·`NumberType2` — `DIGIT`·`CIRCLED_DIGIT`…)으로
 * 적으므로 [shapeOf] 로 옮긴다. 처음에는 변환기마다 표가 있었고 같은 문서의 번호가 달랐다(13단계 짝 대조) — 동그라미 숫자
 * 21~50(HWP `21`·HWPX `㉑`), 26 을 넘는 영문자(HWP `ab`·HWPX `bb`), 시작 번호 0(HWP `0`·HWPX `1`).
 *
 * **한글이 어떻게 하는지 확인하지 못한 것**(고른 쪽을 적는다):
 * * 26 을 넘는 영문자는 같은 글자를 겹친다(`aa`·`bb` — 워드의 방식).
 * * 모양 0x80('네 글자가 차례로 되풀이', HWPX `SYMBOL`)은 어떤 글자인지 명세·스키마에 없다 — 숫자로 쓴다. 처음에는 HWPX 가
 *   `•` 를 적어 모든 각주가 같은 표지가 되었다.
 * * 표에 없는 값(동그라미 숫자 51 이상 등)은 아라비아 숫자다. 빈 표지보다 낫다.
 */
object HancomNumbers {
    const val DIGIT = 0
    const val CIRCLED_DIGIT = 1
    const val ROMAN_CAPITAL = 2
    const val ROMAN_SMALL = 3
    const val LATIN_CAPITAL = 4
    const val LATIN_SMALL = 5
    const val CIRCLED_LATIN_CAPITAL = 6
    const val CIRCLED_LATIN_SMALL = 7
    const val HANGUL_SYLLABLE = 8
    const val CIRCLED_HANGUL_SYLLABLE = 9
    const val HANGUL_JAMO = 10
    const val CIRCLED_HANGUL_JAMO = 11
    const val HANGUL_PHONETIC = 12
    const val IDEOGRAPH = 13
    const val CIRCLED_IDEOGRAPH = 14
    const val DECAGON_CIRCLE = 15
    const val DECAGON_CIRCLE_HANJA = 16

    /** 네 글자가 차례로 되풀이(각주·미주). 어떤 글자인지 모른다 — 숫자로 쓴다. */
    const val SYMBOL = 0x80

    /** 사용자가 고른 글자(각주·미주). 글자는 따로 적혀 있다([noteLabel]). */
    const val USER_CHAR = 0x81

    /** 번호를 쓰지 않는다(HWPX `NONE`). */
    const val NONE = -1

    /** 셈에서 '아직 쓰이지 않은 수준'. */
    const val UNSET = Int.MIN_VALUE

    /** HWP 5.0 의 모양 번호를 우리 값으로. 모르는 번호는 숫자. */
    fun shapeOf(code: Int): Int = when (code) {
        in DIGIT..DECAGON_CIRCLE_HANJA, SYMBOL, USER_CHAR -> code
        else -> DIGIT
    }

    /** HWPX 의 모양 이름을 우리 값으로. 없거나 모르는 이름은 숫자. */
    fun shapeOf(name: String?): Int = when (name?.uppercase()) {
        null, "DIGIT" -> DIGIT
        "CIRCLED_DIGIT" -> CIRCLED_DIGIT
        "ROMAN_CAPITAL" -> ROMAN_CAPITAL
        "ROMAN_SMALL" -> ROMAN_SMALL
        "LATIN_CAPITAL" -> LATIN_CAPITAL
        "LATIN_SMALL" -> LATIN_SMALL
        "CIRCLED_LATIN_CAPITAL" -> CIRCLED_LATIN_CAPITAL
        "CIRCLED_LATIN_SMALL" -> CIRCLED_LATIN_SMALL
        "HANGUL_SYLLABLE" -> HANGUL_SYLLABLE
        "CIRCLED_HANGUL_SYLLABLE" -> CIRCLED_HANGUL_SYLLABLE
        "HANGUL_JAMO" -> HANGUL_JAMO
        "CIRCLED_HANGUL_JAMO" -> CIRCLED_HANGUL_JAMO
        "HANGUL_PHONETIC" -> HANGUL_PHONETIC
        "IDEOGRAPH" -> IDEOGRAPH
        "CIRCLED_IDEOGRAPH" -> CIRCLED_IDEOGRAPH
        "DECAGON_CIRCLE" -> DECAGON_CIRCLE
        "DECAGON_CIRCLE_HANJA" -> DECAGON_CIRCLE_HANJA
        "SYMBOL" -> SYMBOL
        "USER_CHAR" -> USER_CHAR
        "NONE" -> NONE
        else -> DIGIT
    }

    /** 값 하나를 모양대로. */
    fun format(value: Int, shape: Int): String = when (shape) {
        NONE -> ""
        CIRCLED_DIGIT -> circledDigit(value)
        ROMAN_CAPITAL -> roman(value)?.uppercase() ?: value.toString()
        ROMAN_SMALL -> roman(value) ?: value.toString()
        LATIN_CAPITAL -> latin(value)?.uppercase() ?: value.toString()
        LATIN_SMALL -> latin(value) ?: value.toString()
        CIRCLED_LATIN_CAPITAL -> enclosed(value, 0x24B6, 26) // Ⓐ
        CIRCLED_LATIN_SMALL -> enclosed(value, 0x24D0, 26) // ⓐ
        HANGUL_SYLLABLE -> cycle(value, GANADA)
        CIRCLED_HANGUL_SYLLABLE -> enclosed(value, 0x326E, 14) // ㉮
        HANGUL_JAMO -> cycle(value, JAMO)
        CIRCLED_HANGUL_JAMO -> enclosed(value, 0x3260, 14) // ㉠
        HANGUL_PHONETIC -> sino(value, KOREAN_DIGITS, KOREAN_UNITS)
        IDEOGRAPH -> sino(value, HANJA_DIGITS, HANJA_UNITS)
        CIRCLED_IDEOGRAPH -> enclosed(value, 0x3280, 10) // ㊀
        DECAGON_CIRCLE -> cycle(value, STEMS)
        DECAGON_CIRCLE_HANJA -> cycle(value, STEMS_HANJA)
        else -> value.toString()
    }

    /**
     * 번호 형식 하나를 채운다(명세 표 38). `^1`..`^9`·`^10` 은 그 수준의 값을 그 수준의 모양으로, `^n` 은 1수준부터 이
     * 수준까지의 경로(`1.1.1`), `^N` 은 경로 뒤에 마침표를 하나 더. 아직 쓰이지 않은 수준([UNSET])을 가리키면 비워 둔다.
     *
     * @param level 이 문단의 수준(0부터).
     * @param values 수준마다의 지금 값([Counters.next] 가 준 것).
     * @param shapes 수준마다의 모양.
     */
    fun fill(template: String, level: Int, values: IntArray, shapes: IntArray): String {
        val out = StringBuilder(template.length + 8)
        fun value(k: Int) {
            if (k < values.size && values[k] != UNSET) out.append(format(values[k], shapes.getOrElse(k) { DIGIT }))
        }
        var i = 0
        while (i < template.length) {
            val c = template[i]
            if (c == '^' && i + 1 < template.length) {
                val d = template[i + 1]
                when {
                    d == 'n' || d == 'N' -> {
                        for (k in 0..level.coerceIn(0, values.size - 1)) {
                            if (values[k] == UNSET) break
                            if (k > 0) out.append('.')
                            value(k)
                        }
                        if (d == 'N') out.append('.')
                        i += 2
                        continue
                    }
                    d in '1'..'9' -> {
                        // `^10` — 확장 수준(한글 2010 뒤). 두 자리를 먼저 본다.
                        val ten = d == '1' && i + 2 < template.length && template[i + 2] == '0'
                        value(if (ten) 9 else d - '1')
                        i += if (ten) 3 else 2
                        continue
                    }
                }
            }
            out.append(c)
            i++
        }
        return out.toString()
    }

    /**
     * 각주·미주·자동 번호의 표지 — 앞 장식 + 번호 + 뒤 장식(`1)`). [USER_CHAR] 면 [userChar] 를 쓴다(비었으면 숫자).
     * 처음에는 HWP 변환기가 사용자 글자를 읽지 않아 `*` 로 적은 각주(K26 의 `*` 표시)가 숫자가 되었다.
     */
    fun noteLabel(value: Int, shape: Int, userChar: String, prefix: String, suffix: String): String {
        val core = if (shape == USER_CHAR) userChar.ifEmpty { value.toString() } else format(value, shape)
        return prefix + core + suffix
    }

    /** ①..⑳, ㉑..㉟, ㊱..㊿. 그 밖은 숫자. */
    private fun circledDigit(value: Int): String = when (value) {
        in 1..20 -> (0x2460 + value - 1).toChar().toString()
        in 21..35 -> (0x3251 + value - 21).toChar().toString()
        in 36..50 -> (0x32B1 + value - 36).toChar().toString()
        else -> value.toString()
    }

    private fun enclosed(value: Int, base: Int, count: Int): String =
        if (value in 1..count) (base + value - 1).toChar().toString() else value.toString()

    private fun cycle(value: Int, letters: String): String =
        if (value < 1) value.toString() else letters[(value - 1) % letters.length].toString()

    /** a..z, aa..zz …(같은 글자를 겹친다 — 위 KDoc). */
    private fun latin(value: Int): String? {
        if (value < 1 || value > 26 * MAX_LATIN_REPEAT) return null
        val c = 'a' + (value - 1) % 26
        return c.toString().repeat((value - 1) / 26 + 1)
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

    /** 1..9999 를 일·십·백·천으로. 십·백·천 앞의 '일' 은 읽지 않는다. 그 밖은 숫자. */
    private fun sino(value: Int, digits: Array<String>, units: Array<String>): String {
        if (value !in 1..9999) return value.toString()
        val sb = StringBuilder()
        var rest = value
        var unit = 1000
        for (pos in 3 downTo 0) {
            val d = rest / unit
            rest %= unit
            if (d != 0) {
                if (!(d == 1 && pos > 0)) sb.append(digits[d])
                sb.append(units[pos])
            }
            unit /= 10
        }
        return sb.toString()
    }

    private const val MAX_LATIN_REPEAT = 30
    private const val GANADA = "가나다라마바사아자차카타파하"
    private const val JAMO = "ㄱㄴㄷㄹㅁㅂㅅㅇㅈㅊㅋㅌㅍㅎ"
    private const val STEMS = "갑을병정무기경신임계"
    private const val STEMS_HANJA = "甲乙丙丁戊己庚辛壬癸"
    private val KOREAN_DIGITS = arrayOf("", "일", "이", "삼", "사", "오", "육", "칠", "팔", "구")
    private val KOREAN_UNITS = arrayOf("", "십", "백", "천")
    private val HANJA_DIGITS = arrayOf("", "一", "二", "三", "四", "五", "六", "七", "八", "九")
    private val HANJA_UNITS = arrayOf("", "十", "百", "千")
    private val ROMAN = listOf(
        1000 to "m", 900 to "cm", 500 to "d", 400 to "cd", 100 to "c", 90 to "xc",
        50 to "l", 40 to "xl", 10 to "x", 9 to "ix", 5 to "v", 4 to "iv", 1 to "i",
    )

    /**
     * 문단 번호의 셈 — 번호 정의(번호)마다 수준 [LEVELS] 개의 지금 값. 한 수준을 셀 때 **그보다 깊은 수준은 비운다**(다음에
     * 쓰일 때 시작 번호부터). 위 수준이 아직 쓰이지 않았으면(2수준부터 시작한 문서) 시작 번호로 채워 보인다. **시작 번호 0 은
     * 0 이다**(처음에는 HWPX 쪽이 0 을 '아직 세지 않음' 으로 써서 1 로 보였다).
     *
     * 부분의 시작마다 [copy] 로 찍어 두므로 **작게** 든다 — 12단계 docx 가 목록 번호 상태를 통째로 복사하다 5.8 MB 짜리
     * 문서에서 메모리를 다 썼다. 쓰인 정의만 들고, 정의의 수에 상한을 둔다(넘으면 새 정의는 늘 처음 값으로 보인다).
     */
    class Counters private constructor(private val map: HashMap<Int, IntArray>) {

        constructor() : this(HashMap())

        /**
         * 번호 정의 [id] 의 수준 [level] 을 하나 센다. 돌려주는 배열은 수준마다의 지금 값이다(읽기만 한다). 처음 쓰이는 수준은
         * [starts] 의 값에서 시작한다.
         */
        fun next(id: Int, level: Int, starts: IntArray): IntArray {
            val existing = map[id]
            val v = existing ?: IntArray(LEVELS) { UNSET }
            if (existing == null && map.size < MAX_DEFS) map[id] = v
            val l = level.coerceIn(0, LEVELS - 1)
            v[l] = if (v[l] == UNSET) startOf(starts, l) else minOf(v[l] + 1, MAX_VALUE)
            for (k in l + 1 until LEVELS) v[k] = UNSET
            for (k in 0 until l) if (v[k] == UNSET) v[k] = startOf(starts, k)
            return v
        }

        private fun startOf(starts: IntArray, k: Int): Int = starts.getOrElse(k) { 1 }.coerceIn(0, MAX_VALUE)

        fun copy(): Counters {
            val m = HashMap<Int, IntArray>(map.size * 2)
            for ((k, v) in map) m[k] = v.copyOf()
            return Counters(m)
        }

        fun approxBytes(): Long = 64L + map.size * (48L + 4L * LEVELS)

        companion object {
            /** 수준의 수 — `^1`..`^10`. */
            const val LEVELS = 10
            private const val MAX_DEFS = 4_096
            private const val MAX_VALUE = 1_000_000
        }
    }
}
