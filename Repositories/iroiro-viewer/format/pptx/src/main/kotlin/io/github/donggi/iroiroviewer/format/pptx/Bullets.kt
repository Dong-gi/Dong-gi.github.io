package io.github.donggi.iroiroviewer.format.pptx

/**
 * 글머리 — 자동 번호(`a:buAutoNum`)의 모양과 기호 글꼴의 글머리 글자.
 *
 * 번호 모양(ST_TextAutonumberScheme)은 40 가지가 넘는다. 자주 쓰이는 것(아라비아·로마·영문자·
 * 동그라미 숫자·전각 숫자·한자 숫자)을 따르고, **모르는 모양은 `1.` 로 그린다** — 번호가 조금
 * 다른 것이 번호가 없는 것보다 낫다.
 */
internal object Bullets {

    /** [n] 번째(1 부터) 번호의 글자. */
    fun autoNumber(scheme: String, n: Int): String {
        val v = n.coerceAtLeast(1)
        return when (scheme) {
            "arabicPlain" -> "$v"
            "arabicPeriod" -> "$v."
            "arabicParenR" -> "$v)"
            "arabicParenBoth" -> "($v)"
            "arabic1Minus", "arabic2Minus" -> "$v-"
            "arabicDbPlain" -> fullWidth(v)
            "arabicDbPeriod" -> fullWidth(v) + "．"
            "alphaLcPeriod" -> alpha(v, false) + "."
            "alphaUcPeriod" -> alpha(v, true) + "."
            "alphaLcParenR" -> alpha(v, false) + ")"
            "alphaUcParenR" -> alpha(v, true) + ")"
            "alphaLcParenBoth" -> "(" + alpha(v, false) + ")"
            "alphaUcParenBoth" -> "(" + alpha(v, true) + ")"
            "romanLcPeriod" -> roman(v).lowercase() + "."
            "romanUcPeriod" -> roman(v) + "."
            "romanLcParenR" -> roman(v).lowercase() + ")"
            "romanUcParenR" -> roman(v) + ")"
            "romanLcParenBoth" -> "(" + roman(v).lowercase() + ")"
            "romanUcParenBoth" -> "(" + roman(v) + ")"
            // 동그라미 숫자는 유니코드에 20(흰 바탕)·10(검은 바탕·산세리프)까지만 있다. 넘으면 숫자로.
            "circleNumDbPlain" -> if (v <= 20) (0x2460 + v - 1).toChar().toString() else "$v"
            "circleNumWdWhitePlain" -> if (v <= 10) (0x2780 + v - 1).toChar().toString() else "$v"
            "circleNumWdBlackPlain" -> if (v <= 10) (0x2776 + v - 1).toChar().toString() else "$v"
            "ea1ChsPlain", "ea1ChtPlain", "ea1JpnKorPlain" -> hanNumber(v)
            "ea1ChsPeriod", "ea1ChtPeriod", "ea1JpnKorPeriod" -> hanNumber(v) + "."
            "ea1JpnChsDbPeriod" -> fullWidth(v) + "．"
            else -> "$v."
        }
    }

    /** `a`..`z`, 그다음은 `aa`·`bb` — 오피스의 영문자 번호는 글자를 되풀이한다. */
    private fun alpha(n: Int, upper: Boolean): String {
        val letter = ('a' + (n - 1) % 26).let { if (upper) it.uppercaseChar() else it }
        val times = ((n - 1) / 26 + 1).coerceAtMost(MAX_REPEAT)
        val sb = StringBuilder(times)
        repeat(times) { sb.append(letter) }
        return sb.toString()
    }

    /** 로마 숫자. 3999 를 넘으면 아라비아 숫자로 둔다. */
    fun roman(n: Int): String {
        if (n !in 1..3999) return "$n"
        val values = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
        val symbols = arrayOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")
        var rest = n
        val sb = StringBuilder()
        for (i in values.indices) {
            while (rest >= values[i]) {
                sb.append(symbols[i])
                rest -= values[i]
            }
        }
        return sb.toString()
    }

    private fun fullWidth(n: Int): String = n.toString().map { (it.code - '0'.code + 0xFF10).toChar() }.joinToString("")

    /** 한자 숫자(一·十一·二十). 100 이상은 아라비아 숫자로 둔다. */
    private fun hanNumber(n: Int): String {
        if (n !in 1..99) return "$n"
        val digits = "〇一二三四五六七八九"
        val tens = n / 10
        val ones = n % 10
        val sb = StringBuilder()
        if (tens > 1) sb.append(digits[tens])
        if (tens >= 1) sb.append('十')
        if (ones > 0) sb.append(digits[ones])
        return sb.toString()
    }

    /**
     * 글머리 글자. 기호 글꼴(Wingdings·Symbol)은 **글자 칸에 모양을 넣어 둔 글꼴**이라
     * 그 글꼴이 없는 화면에서는 `§`·`Ø` 같은 엉뚱한 글자가 뜬다. 흔한 것만 유니코드로 옮기고,
     * 모르는 기호 글꼴 글자는 `•` 로 둔다.
     *
     * @return 옮긴 글자와, 글꼴을 그대로 써도 되는가(기호 글꼴을 옮겼으면 거짓).
     */
    fun bulletChar(char: String, typeface: String?): Pair<String, Boolean> {
        val face = typeface?.lowercase().orEmpty()
        val symbolFont = face.startsWith("wingdings") || face == "symbol" || face.startsWith("webdings")
        if (!symbolFont) return (char.ifEmpty { "•" }) to true
        var code = char.firstOrNull()?.code ?: return "•" to false
        // 기호 글꼴의 글자를 사용자 영역(U+F0xx)에 적는 파일이 있다.
        if (code in 0xF000..0xF0FF) code -= 0xF000
        val mapped = if (face == "symbol") SYMBOL[code] else WINGDINGS[code]
        return (mapped ?: "•") to false
    }

    private val WINGDINGS = mapOf(
        0x6C to "●", 0x6E to "■", 0x71 to "❑", 0x75 to "◆", 0x76 to "❖",
        0xA7 to "▪", 0xD8 to "➢", 0xFC to "✓", 0x77 to "⬥", 0xA8 to "◻",
    )

    private val SYMBOL = mapOf(0xB7 to "•", 0x2D to "−", 0xAE to "→", 0xDE to "⇒")

    private const val MAX_REPEAT = 8
}
