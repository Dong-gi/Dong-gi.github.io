package io.github.donggi.iroiroviewer.format.xlsx

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate

/**
 * 서식을 입힌 값. [color] 는 서식 코드의 색(`[Red]`)을 `#rrggbb` 로 옮긴 것이고, 없으면 null —
 * 그러면 글꼴의 색을 따른다.
 */
internal data class Formatted(val text: String, val color: String? = null)

/**
 * 셀의 **표시 형식**(number format) 하나 — ECMA-376 Part 1 §18.8.30·§18.8.31.
 *
 * ## 왜 따로 떼었는가
 *
 * 시트 변환에서 가장 틀리기 쉬운 곳이 여기다. 같은 45366.57 이 `yyyy-mm-dd` 로는 날짜,
 * `0.00%` 로는 백분율, `[h]:mm` 로는 누적 시간이 된다. 패키지·HTML 과 상관없는 **순수 함수**로
 * 두어야 시험을 수십 개 걸 수 있다.
 *
 * ## 무엇을 하는가
 *
 * * 구역 `;` — 양수·음수·0·글자. 조건 구역(`[<100]`)은 최선을 다해 흉내 낸다.
 * * 글자 그대로 `"…"`·`\x`, 폭만 차지하는 `_x`(공백 하나), 채우기 `*x`(버린다 — 칸 폭을 모른다).
 * * 색 `[Red]`·`[Color10]`, 로케일 `[$-412]`(버린다 — 기호가 있으면 `[$₩-412]` 의 `₩` 만 남긴다).
 * * 숫자 `0 # ? . ,`(천 단위와 끝 쉼표의 1000 나누기), `%`, `E+`/`E-`, 분수 `# ?/?`(최선).
 * * 날짜·시각 `y m d h s AM/PM A/P [h] [mm] [ss] .0`. 1900 체계의 **윤년 버그**(일련번호 60 이
 *   존재하지 않는 1900-02-29)와 1904 체계를 따른다.
 * * 일반(General) — Excel 처럼 **11자 안쪽**으로 줄인다(0.1+0.2 → 0.3, 1234567890123 → 1.23457E+12).
 *
 * ## 무엇을 하지 않는가 — 정직하게
 *
 * * 로케일에 따라 모양이 바뀌는 내장 형식(14번의 '짧은 날짜' 등)은 명세의 **불변 형식**으로 그린다.
 *   한국어 Excel 이 14번을 `yyyy-mm-dd` 로 보여 주는 것과 다르다.
 * * 달·요일 이름은 영어다(서식의 출력이지 화면 문구가 아니다). 한국·일본 Excel 에만 있는
 *   `aaa`·`aaaa`(요일)는 한국어 이름으로, `[$-411]` 이 붙었으면 일본어 이름으로 그린다.
 * * 칸 폭을 모르므로 `*` 채우기와 '칸이 좁으면 `####`' 은 흉내 내지 않는다.
 *
 * **이상한 코드에 던지지 않는다.** 읽다 실패하면 일반 형식으로 돌아간다 — 셀 하나의 서식 때문에
 * 시트가 안 보이면 안 된다.
 */
internal class NumberFormat private constructor(private val sections: List<Section>) {

    /** 숫자에 쓰는 구역들(최대 셋). 글자 구역은 [textSection]. */
    private val numeric: List<Section>

    /** 글자 값에 쓰는 구역. 없으면 글자는 그대로 보인다. */
    private val textSection: Section?

    init {
        if (sections.size >= 4) {
            numeric = sections.subList(0, 3)
            textSection = sections[3]
        } else {
            numeric = sections.filter { !it.hasAt }
            textSection = sections.firstOrNull { it.hasAt }
        }
    }

    /** 첫 숫자 구역이 날짜·시각인가. `t="d"` 셀과 날짜 판정에 쓴다. */
    val isDate: Boolean get() = numeric.firstOrNull()?.type == SectionType.DATE

    /** 이 형식이 일반(General) 그 자체인가. 칸 폭에 맞춰 줄이는 것은 일반 형식뿐이다. */
    val isGeneral: Boolean get() = this === GENERAL

    /**
     * 숫자 하나를 그린다.
     *
     * @param date1904 통합 문서의 날짜 체계(`workbookPr/@date1904`).
     * @param generalWidth 일반 형식일 때 칸에 들어가는 숫자 수. Excel 은 일반 형식의 수를 **칸 폭에
     *   맞춰** 줄인다(좁은 칸의 323832.4567 → 323832.5). 넓어도 11자를 넘지 않는다.
     */
    fun format(value: Double, date1904: Boolean = false, generalWidth: Int = 11): Formatted {
        if (!value.isFinite()) return Formatted("#NUM!")
        if (this === GENERAL) return Formatted(general(value, generalWidth))
        return try {
            formatChecked(value, date1904)
        } catch (e: RuntimeException) {
            // 우리 코드의 빈틈이다. 셀 하나 때문에 시트를 잃지 않게 일반 형식으로 돌아간다.
            Formatted(general(value))
        }
    }

    /** 글자 하나를 그린다. 글자 구역이 없으면 그대로. */
    fun formatText(text: String): Formatted {
        val sec = textSection ?: return Formatted(text)
        val out = StringBuilder()
        for (t in sec.tokens) {
            when (t) {
                is Lit -> out.append(t.text)
                At -> out.append(text)
                else -> Unit
            }
        }
        return Formatted(out.toString(), sec.color)
    }

    private fun formatChecked(value: Double, date1904: Boolean): Formatted {
        if (numeric.isEmpty()) return Formatted(general(value))
        val pick = pick(value) ?: return Formatted(general(value))
        val sec = pick.section
        val text = when (sec.type) {
            SectionType.DATE -> formatDate(sec, value, date1904) ?: return Formatted(general(value))
            SectionType.EMPTY -> ""
            else -> {
                val body = formatNumber(sec, kotlin.math.abs(value))
                if (pick.signed && value < 0) "-$body" else body
            }
        }
        return Formatted(text, sec.color)
    }

    private class Pick(val section: Section, val signed: Boolean)

    /**
     * 어느 구역인가. 조건이 없으면 명세의 규칙(양·음·0), 있으면 조건을 차례로 본다.
     *
     * 음수 구역이 따로 있으면 **부호를 떼고** 그린다 — `#,##0;(#,##0)` 의 괄호가 부호 노릇을 한다.
     */
    private fun pick(value: Double): Pick? {
        if (numeric.any { it.condition != null }) {
            for (s in numeric) {
                val c = s.condition ?: continue
                if (c.test(value)) return Pick(s, !c.impliesNegative)
            }
            val rest = numeric.firstOrNull { it.condition == null } ?: return null
            return Pick(rest, true)
        }
        return when {
            numeric.size == 1 -> Pick(numeric[0], true)
            value > 0 -> Pick(numeric[0], true)
            value < 0 -> Pick(numeric[1], false)
            else -> Pick(if (numeric.size >= 3) numeric[2] else numeric[0], true)
        }
    }

    // ---- 숫자 -----------------------------------------------------------------

    private fun formatNumber(sec: Section, v: Double): String {
        val t = sec.tokens
        val slash = t.indexOfFirst { it === Slash }
        if (slash >= 0 && slash > 0 && t[slash - 1] is Digit) return formatFraction(sec, v, slash)
        if (t.any { it is Exp }) return formatScientific(sec, v)
        if (t.none { it is Digit }) return formatLiteralOnly(sec, v)
        return formatFixed(t, v)
    }

    /** 자리표가 없는 구역 — 글자와 `General` 만 있다(`"합계 "General`, `"없음"`). */
    private fun formatLiteralOnly(sec: Section, v: Double): String {
        val out = StringBuilder()
        for (tok in sec.tokens) {
            when (tok) {
                is Lit -> out.append(tok.text)
                GeneralTok -> out.append(general(v))
                Percent -> out.append('%')
                Slash -> out.append('/')
                Point -> out.append('.')
                else -> Unit
            }
        }
        return out.toString()
    }

    /** 고정 소수(`#,##0.00`·`0%`·`000-0000`). */
    private fun formatFixed(t: List<Tok>, v: Double): String {
        val point = t.indexOfFirst { it === Point }
        val intEnd = if (point >= 0) point else t.size
        val lastDigit = t.indexOfLast { it is Digit }
        var thousands = false
        var scale = 0
        for (i in t.indices) {
            if (t[i] !== Comma) continue
            val before = (0 until minOf(i, intEnd)).any { t[it] is Digit }
            val afterInInt = i < intEnd && (i + 1 until intEnd).any { t[it] is Digit }
            when {
                before && afterInInt -> thousands = true
                // 마지막 자리표 뒤(또는 정수 자리의 끝)에 붙은 쉼표는 1000 으로 나눈다(`#,##0,` → 천 단위).
                before && (i > lastDigit || !afterInInt) -> scale++
            }
        }
        val percents = t.count { it === Percent }
        val decimals = if (point < 0) 0 else (point + 1 until t.size).count { t[it] is Digit }

        var x = exact15(v)
        if (percents > 0) x = x.movePointRight(2 * minOf(percents, 4))
        if (scale > 0) x = x.movePointLeft(3 * minOf(scale, 6))
        x = x.setScale(decimals, RoundingMode.HALF_UP)
        val plain = x.toPlainString()
        val intPart = plain.substringBefore('.')
        val intDigits = if (intPart == "0") "" else intPart
        val fracDigits = if (decimals > 0) plain.substringAfter('.', "").padEnd(decimals, '0') else ""

        val out = StringBuilder()
        out.append(formatIntZone(t, 0, intEnd, intDigits, thousands))
        if (point >= 0) {
            out.append('.')
            val lastNonZero = fracDigits.indexOfLast { it != '0' }
            var j = 0
            for (i in point + 1 until t.size) {
                when (val tok = t[i]) {
                    is Digit -> {
                        out.append(
                            if (j <= lastNonZero) fracDigits[j].toString() else emptyDigit(tok.ch),
                        )
                        j++
                    }
                    is Lit -> out.append(tok.text)
                    Percent -> out.append('%')
                    Point -> out.append('.')
                    Slash -> out.append('/')
                    else -> Unit
                }
            }
        }
        return out.toString()
    }

    /**
     * 정수 자리를 채운다. 숫자는 **오른쪽 자리표부터** 들어가고, 자리표보다 긴 앞자리는 맨 왼쪽
     * 자리표가 다 받는다(`00` 에 12345 → 12345). 빈 자리표는 `0` 이면 0, `?` 면 공백, `#` 이면 없음.
     *
     * 천 단위 쉼표가 있고 자리표 사이에 글자가 끼지 않았으면 묶어서 쉼표를 넣는다. 글자가 낀
     * 모양(`000-0000`)에 천 단위까지 섞는 것은 드물어 자리 채우기만 한다.
     */
    private fun formatIntZone(t: List<Tok>, from: Int, to: Int, digits: String, thousands: Boolean): String {
        val holders = (from until to).filter { t[it] is Digit }
        val first = holders.firstOrNull()
        val last = holders.lastOrNull()
        val out = StringBuilder()
        if (first == null || last == null) {
            for (i in from until to) appendLiteral(out, t[i])
            return out.toString()
        }
        val p = holders.size
        val d = digits.length
        fun digitFor(k: Int): String {
            val ch = (t[holders[k]] as Digit).ch
            if (k == 0 && d > p) return digits.substring(0, d - p + 1)
            val idx = d - p + k
            return if (idx >= 0) digits[idx].toString() else emptyDigit(ch)
        }
        val literalInside = (first..last).any { t[it] is Lit }
        if (thousands && !literalInside) {
            for (i in from until first) appendLead(out, t[i])
            val body = StringBuilder()
            for (k in 0 until p) body.append(digitFor(k))
            out.append(group(body.toString()))
            for (i in last + 1 until to) appendLiteral(out, t[i])
            return out.toString()
        }
        var k = 0
        for (i in from until to) {
            if (t[i] is Digit) {
                out.append(digitFor(k))
                k++
            } else if (i < first) {
                appendLead(out, t[i])
            } else {
                appendLiteral(out, t[i])
            }
        }
        return out.toString()
    }

    /**
     * 정수 자리표보다 **앞선** 글자. 자리표 앞의 쉼표는 천 단위도 1000 나누기도 아닌 글자다 — Excel 은 `,#` 에
     * 1234567 을 ',1234567' 로 보인다(POI 의 진리표). 예전에는 쉼표를 떨궜다.
     */
    private fun appendLead(out: StringBuilder, tok: Tok) {
        if (tok === Comma) out.append(',') else appendLiteral(out, tok)
    }

    private fun appendLiteral(out: StringBuilder, tok: Tok) {
        when (tok) {
            is Lit -> out.append(tok.text)
            Percent -> out.append('%')
            Slash -> out.append('/')
            else -> Unit
        }
    }

    /** 지수 표기(`0.00E+00`, 공학 표기 `##0.0E+0`). */
    private fun formatScientific(sec: Section, v: Double): String {
        val t = sec.tokens
        val e = t.indexOfFirst { it is Exp }
        val expTok = t[e] as Exp
        val point = (0 until e).firstOrNull { t[it] === Point } ?: -1
        val intEnd = if (point >= 0) point else e
        val intHolders = (0 until intEnd).count { t[it] is Digit }.coerceAtLeast(1)
        val decimals = if (point < 0) 0 else (point + 1 until e).count { t[it] is Digit }
        val expDigits = (e + 1 until t.size).count { t[it] is Digit }.coerceIn(1, 5)

        var exponent = 0
        var mant = BigDecimal.ZERO.setScale(decimals)
        if (v != 0.0) {
            val x = exact15(v)
            val step = intHolders.coerceAtMost(8)
            exponent = Math.floorDiv(floorLog10(x), step) * step
            mant = x.movePointLeft(exponent).setScale(decimals, RoundingMode.HALF_UP)
            // 반올림이 자리를 넘기면(9.99 → 10.0) 지수를 한 단계 올린다.
            if (mant >= BigDecimal.TEN.pow(step)) {
                exponent += step
                mant = x.movePointLeft(exponent).setScale(decimals, RoundingMode.HALF_UP)
            }
        }
        val plain = mant.toPlainString()
        val intPart = plain.substringBefore('.')
        val intDigits = if (intPart == "0") "" else intPart
        val fracDigits = if (decimals > 0) plain.substringAfter('.', "").padEnd(decimals, '0') else ""

        val out = StringBuilder()
        out.append(formatIntZone(t, 0, intEnd, intDigits, false))
        if (point >= 0) {
            out.append('.')
            var j = 0
            for (i in point + 1 until e) {
                when (val tok = t[i]) {
                    is Digit -> {
                        out.append(fracDigits[j])
                        j++
                    }
                    else -> appendLiteral(out, tok)
                }
            }
        }
        out.append(expTok.letter)
        if (exponent < 0) out.append('-') else if (expTok.plus) out.append('+')
        out.append(kotlin.math.abs(exponent).toString().padStart(expDigits, '0'))
        // 지수 자리표는 위에서 한꺼번에 썼다. 남은 것은 글자뿐이다.
        for (i in e + 1 until t.size) if (t[i] !is Digit) appendLiteral(out, t[i])
        return out.toString()
    }

    /**
     * 분수(`# ?/?`·`?/8`). **최선을 다할 뿐이다** — 분모 자리 수 안에서 가장 가까운 분수를 연분수로
     * 찾는다. 정수 자리표가 있으면 대분수, 없으면 가분수.
     */
    private fun formatFraction(sec: Section, v: Double, slash: Int): String {
        val t = sec.tokens
        var numStart = slash
        while (numStart - 1 >= 0 && t[numStart - 1] is Digit) numStart--
        val numHolders = slash - numStart
        val intHolders = (0 until numStart).filter { t[it] is Digit }
        val hasInt = intHolders.isNotEmpty()

        var denEnd = slash + 1
        while (denEnd < t.size && t[denEnd] is Digit) denEnd++
        val denHolders = denEnd - (slash + 1)
        var fixedDen = 0L
        var suffixFrom = denEnd
        var suffixLead = ""
        if (denHolders == 0 && denEnd < t.size) {
            // 고정 분모(`?/8`·`# ?/10`·`# ??/100` — Excel 의 '10분의'·'100분의' 형식). 자르는 쪽이 `0` 을
            // 자리표로 떼어 내므로 `10` 은 Lit("1")·Digit('0') 두 토큰으로 온다. 앞 글자의 숫자에
            // 뒤따르는 `0` 과 숫자 글자를 이어 읽어야 분모가 1 로 줄지 않는다.
            val first = t[denEnd] as? Lit
            val lead = first?.text?.takeWhile { it in '0'..'9' }.orEmpty()
            if (first != null && lead.isNotEmpty() && lead[0] != '0') {
                val den = StringBuilder(lead)
                var rest = first.text.substring(lead.length)
                var k = denEnd + 1
                while (rest.isEmpty() && k < t.size) {
                    val tok = t[k]
                    if (tok is Digit && tok.ch == '0') {
                        den.append('0')
                    } else if (tok is Lit) {
                        val more = tok.text.takeWhile { it in '0'..'9' }
                        den.append(more)
                        rest = tok.text.substring(more.length)
                    } else {
                        break
                    }
                    k++
                }
                if (den.length <= MAX_FIXED_DENOMINATOR_DIGITS) {
                    fixedDen = den.toString().toLong()
                    suffixLead = rest
                    suffixFrom = k
                }
            }
        }
        if (denHolders == 0 && fixedDen <= 0L) return formatFixed(t.filter { it !== Slash }, v)

        val x = exact15(v)
        var whole = if (hasInt) x.setScale(0, RoundingMode.FLOOR).toBigInteger() else BigInteger.ZERO
        val frac = x.subtract(BigDecimal(whole)).toDouble()
        var num: Long
        var den: Long
        if (fixedDen > 0L) {
            den = fixedDen
            num = BigDecimal(frac).multiply(BigDecimal(den)).setScale(0, RoundingMode.HALF_UP).toLong()
        } else {
            val maxDen = (Math.pow(10.0, denHolders.coerceAtMost(5).toDouble()) - 1).toLong()
            val r = approximate(frac, maxDen)
            num = r.first
            den = r.second
        }
        if (hasInt && num == den && den > 0) {
            whole = whole.add(BigInteger.ONE)
            num = 0
        }
        if (!hasInt) num += whole.toLong() * den

        val out = StringBuilder()
        for (i in 0 until (intHolders.firstOrNull() ?: numStart)) appendLiteral(out, t[i])
        if (hasInt) {
            val wholeDigits = if (whole.signum() == 0) (if (num == 0L) "0" else "") else whole.toString()
            val intZone = formatIntZone(t, intHolders.first(), intHolders.last() + 1, wholeDigits, false)
            out.append(intZone)
            if (num == 0L) {
                // 분수 부분이 없으면 정수만 — Excel 은 그 자리를 공백으로 채우는데 HTML 이 어차피 접는다.
                for (i in suffixFrom until t.size) appendLiteral(out, t[i])
                return out.toString().trimEnd()
            }
            // 정수 자리가 숫자 없이 비었으면(0 을 `#` 이 지웠다) 정수와 분자 사이의 글자도 쓰지 않는다 — Excel 은
            // `#\:#/#` 에 0.75 를 ':3/4' 가 아니라 '3/4' 로 보인다(POI 의 진리표 NumberFormatTests.xlsx).
            if (intZone.any { it.isDigit() }) for (i in intHolders.last() + 1 until numStart) appendLiteral(out, t[i])
        }
        val numText = num.toString()
        out.append(padHolders(numText, t.subList(numStart, slash), left = true))
        out.append('/')
        if (fixedDen > 0L) {
            out.append(fixedDen.toString()).append(suffixLead)
        } else {
            out.append(padHolders(den.toString(), t.subList(slash + 1, denEnd), left = false))
        }
        for (i in suffixFrom until t.size) appendLiteral(out, t[i])
        return out.toString()
    }

    /**
     * 분자는 오른쪽에, 분모는 왼쪽에 붙인다. 모자란 자리는 `?` 면 공백, `0` 이면 0.
     *
     * **분모의 `0` 자리는 앞에 채운다** — 뒤에 채우면 수가 바뀐다(`0#/000` 에 3/4 가 '03/400' 이 됐다. Excel 은
     * '03/004' — POI 의 진리표). 분모의 `?` 는 뒤에 공백으로 채워 빗금의 자리를 맞춘다.
     */
    private fun padHolders(text: String, holders: List<Tok>, left: Boolean): String {
        if (text.length >= holders.size) return text
        val missing = holders.size - text.length
        val pad = StringBuilder()
        val zeros = StringBuilder()
        for (i in 0 until missing) {
            val ch = (holders[if (left) i else text.length + i] as? Digit)?.ch ?: '#'
            if (!left && ch == '0') zeros.append('0') else pad.append(emptyDigit(ch))
        }
        return if (left) pad.toString() + text else zeros.toString() + text + pad.toString()
    }

    // ---- 날짜 -----------------------------------------------------------------

    /** 일련번호를 날짜·시각으로. 범위를 벗어나면(음수·9999년 뒤) null — 부르는 쪽이 일반 형식으로 그린다. */
    private fun formatDate(sec: Section, serial: Double, date1904: Boolean): String? {
        if (serial < 0 || serial > MAX_SERIAL) return null
        val sub = sec.tokens.maxOfOrNull { if (it is DatePart && it.kind == DateKind.SUBSECOND) it.len else 0 } ?: 0
        val subDigits = sub.coerceAtMost(3)
        val scale = POW10[subDigits]
        // **초(또는 보이는 소수 초) 단위로 먼저 반올림한다.** 0.99999 일이 23:59:59 가 아니라 다음 날
        // 0 시가 되는 것이 Excel 이다. `kotlin.math.round` 는 짝수 쪽으로 가므로 floor(x+0.5) 로 적는다.
        val ticks = Math.floor(serial * 86400.0 * scale + 0.5).toLong()
        val dayTicks = 86400L * scale
        val days = ticks / dayTicks
        val rem = ticks % dayTicks
        val secOfDay = rem / scale
        val subValue = rem % scale
        val civil = civil(days, date1904)
        val hour = (secOfDay / 3600).toInt()
        val minute = ((secOfDay / 60) % 60).toInt()
        val second = (secOfDay % 60).toInt()
        val totalSeconds = ticks / scale
        val twelve = sec.tokens.any { it is AmPm }

        val out = StringBuilder()
        for (tok in sec.tokens) {
            when (tok) {
                is Lit -> out.append(tok.text)
                is AmPm -> {
                    val pm = hour >= 12
                    val s = if (tok.short) (if (pm) "P" else "A") else (if (pm) "PM" else "AM")
                    // 소문자를 따르는 것은 `a/p` 뿐이다 — `am/pm` 은 적힌 모양과 상관없이 `AM`·`PM` 이다
                    // (Excel 이 계산해 둔 POI 의 날짜 진리표 `DateFormatTests.xlsx` 가 그렇게 적는다).
                    out.append(if (tok.lower && tok.short) s.lowercase() else s)
                }
                is DatePart -> out.append(datePart(tok, civil, hour, minute, second, totalSeconds, subValue, subDigits, twelve, sec.locale))
                Point -> out.append('.')
                Comma -> out.append(',')
                Percent -> out.append('%')
                Slash -> out.append('/')
                is Digit -> if (tok.ch == '0') out.append('0')
                else -> Unit
            }
        }
        return out.toString()
    }

    private fun datePart(
        tok: DatePart,
        c: Civil,
        hour: Int,
        minute: Int,
        second: Int,
        totalSeconds: Long,
        subValue: Long,
        subDigits: Int,
        twelve: Boolean,
        locale: Int,
    ): String = when (tok.kind) {
        DateKind.YEAR -> if (tok.len <= 2) two(c.year % 100) else c.year.toString().padStart(4, '0')
        DateKind.MONTH -> when (tok.len) {
            1 -> c.month.toString()
            2 -> two(c.month)
            3 -> MONTHS[c.month - 1].substring(0, 3)
            4 -> MONTHS[c.month - 1]
            else -> MONTHS[c.month - 1].substring(0, 1)
        }
        DateKind.DAY -> when (tok.len) {
            1 -> c.day.toString()
            2 -> two(c.day)
            3 -> DAYS[c.dow].substring(0, 3)
            else -> DAYS[c.dow]
        }
        DateKind.WEEKDAY_CJK -> {
            val names = if (locale == LOCALE_JA) (if (tok.len >= 4) JA_DAYS_LONG else JA_DAYS) else (if (tok.len >= 4) KO_DAYS_LONG else KO_DAYS)
            names[c.dow]
        }
        DateKind.HOUR -> {
            val h = if (twelve) (if (hour % 12 == 0) 12 else hour % 12) else hour
            if (tok.len >= 2) two(h) else h.toString()
        }
        DateKind.MINUTE -> if (tok.len >= 2) two(minute) else minute.toString()
        DateKind.SECOND -> if (tok.len >= 2) two(second) else second.toString()
        DateKind.ELAPSED_HOURS -> (totalSeconds / 3600).toString().padStart(tok.len, '0')
        DateKind.ELAPSED_MINUTES -> (totalSeconds / 60).toString().padStart(tok.len, '0')
        DateKind.ELAPSED_SECONDS -> totalSeconds.toString().padStart(tok.len, '0')
        DateKind.SUBSECOND -> subValue.toString().padStart(subDigits, '0').take(tok.len.coerceAtMost(subDigits))
    }

    // ---- 모양 -----------------------------------------------------------------

    private sealed interface Tok
    private class Lit(val text: String) : Tok
    private class Digit(val ch: Char) : Tok
    private object Point : Tok
    private object Comma : Tok
    private object Percent : Tok
    private class Exp(val plus: Boolean, val letter: Char) : Tok
    private object Slash : Tok
    private object At : Tok
    private object GeneralTok : Tok
    private class DatePart(val kind: DateKind, val len: Int) : Tok
    private class AmPm(val short: Boolean, val lower: Boolean) : Tok

    private enum class DateKind {
        YEAR, MONTH, DAY, HOUR, MINUTE, SECOND,
        ELAPSED_HOURS, ELAPSED_MINUTES, ELAPSED_SECONDS, SUBSECOND, WEEKDAY_CJK,
    }

    private enum class SectionType { NUMBER, DATE, TEXT, EMPTY }

    private class Condition(val op: String, val value: Double) {
        fun test(v: Double): Boolean = when (op) {
            "<" -> v < value
            "<=" -> v <= value
            ">" -> v > value
            ">=" -> v >= value
            "=" -> v == value
            "<>" -> v != value
            else -> false
        }

        /** `[<0]` 처럼 음수를 고르는 조건이면 부호를 떼고 그린다 — 괄호 따위가 부호 노릇을 한다고 본다. */
        val impliesNegative: Boolean get() = (op == "<" && value <= 0.0) || (op == "<=" && value < 0.0)
    }

    private class Section(
        val tokens: List<Tok>,
        val color: String?,
        val condition: Condition?,
        val locale: Int,
    ) {
        val hasAt: Boolean = tokens.any { it === At }
        val type: SectionType = when {
            tokens.isEmpty() -> SectionType.EMPTY
            tokens.any { it is DatePart || it is AmPm } -> SectionType.DATE
            hasAt -> SectionType.TEXT
            else -> SectionType.NUMBER
        }
    }

    private class Civil(val year: Int, val month: Int, val day: Int, val dow: Int)

    companion object {
        /** 일반(General) 형식. */
        val GENERAL = NumberFormat(listOf(Section(listOf(GeneralTok), null, null, 0)))

        /** 9999-12-31 의 일련번호(1900 체계). 그 뒤는 Excel 도 날짜로 그리지 않는다. */
        private const val MAX_SERIAL = 2958465.99999

        /** 서식 코드 하나의 길이 상한. Excel 은 255자까지 받는다. 넉넉히 둔다. */
        private const val MAX_CODE_CHARS = 1024

        private const val LOCALE_JA = 0x411

        /** 고정 분모의 자리 수 상한. 넘으면 분모로 보지 않는다(분자 × 분모가 `Long` 을 넘지 않게). */
        private const val MAX_FIXED_DENOMINATOR_DIGITS = 9

        private val POW10 = longArrayOf(1, 10, 100, 1000)

        private val MONTHS = arrayOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December",
        )
        private val DAYS = arrayOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

        // 한국·일본 Excel 의 `aaa`·`aaaa`. 서식이 내놓는 **값**이지 화면 문구가 아니다.
        private val KO_DAYS = arrayOf("일", "월", "화", "수", "목", "금", "토")
        private val KO_DAYS_LONG = arrayOf("일요일", "월요일", "화요일", "수요일", "목요일", "금요일", "토요일")
        private val JA_DAYS = arrayOf("日", "月", "火", "水", "木", "金", "土")
        private val JA_DAYS_LONG = arrayOf("日曜日", "月曜日", "火曜日", "水曜日", "木曜日", "金曜日", "土曜日")

        private val COLORS = mapOf(
            "black" to "#000000", "blue" to "#0000ff", "cyan" to "#00ffff", "green" to "#00ff00",
            "magenta" to "#ff00ff", "red" to "#ff0000", "white" to "#ffffff", "yellow" to "#ffff00",
        )

        /**
         * 내장 형식 코드 — ECMA-376 Part 1 §18.8.30 의 불변 형식. 값은 openpyxl 3.1.5 의
         * `BUILTIN_FORMATS` 와 대조했다(그쪽의 44번은 구역 구분 `;` 가 빠져 있어 명세대로 고쳤다).
         * 5–8·41–44 는 명세가 '로케일에 따른다' 고 적은 통화 형식이라 `$` 로 둔다.
         */
        private val BUILTIN = mapOf(
            0 to "General",
            1 to "0",
            2 to "0.00",
            3 to "#,##0",
            4 to "#,##0.00",
            5 to "\"$\"#,##0_);(\"$\"#,##0)",
            6 to "\"$\"#,##0_);[Red](\"$\"#,##0)",
            7 to "\"$\"#,##0.00_);(\"$\"#,##0.00)",
            8 to "\"$\"#,##0.00_);[Red](\"$\"#,##0.00)",
            9 to "0%",
            10 to "0.00%",
            11 to "0.00E+00",
            12 to "# ?/?",
            13 to "# ??/??",
            14 to "mm-dd-yy",
            15 to "d-mmm-yy",
            16 to "d-mmm",
            17 to "mmm-yy",
            18 to "h:mm AM/PM",
            19 to "h:mm:ss AM/PM",
            20 to "h:mm",
            21 to "h:mm:ss",
            22 to "m/d/yy h:mm",
            37 to "#,##0_);(#,##0)",
            38 to "#,##0_);[Red](#,##0)",
            39 to "#,##0.00_);(#,##0.00)",
            40 to "#,##0.00_);[Red](#,##0.00)",
            41 to "_(* #,##0_);_(* \\(#,##0\\);_(* \"-\"_);_(@_)",
            42 to "_(\"$\"* #,##0_);_(\"$\"* \\(#,##0\\);_(\"$\"* \"-\"_);_(@_)",
            43 to "_(* #,##0.00_);_(* \\(#,##0.00\\);_(* \"-\"??_);_(@_)",
            44 to "_(\"$\"* #,##0.00_);_(\"$\"* \\(#,##0.00\\);_(\"$\"* \"-\"??_);_(@_)",
            45 to "mm:ss",
            46 to "[h]:mm:ss",
            47 to "mmss.0",
            48 to "##0.0E+0",
            49 to "@",
        )

        /**
         * 내장 번호의 코드. 모르는 번호면 null(일반 형식으로 그린다).
         *
         * 27–36·50–58 은 한중일 로케일의 날짜 형식이다(명세의 로케일별 표). 어느 로케일인지 파일이
         * 말해 주지 않으므로 **날짜라는 것만** 살린다 — 일련번호 45366 이 그대로 보이는 것보다
         * `2024-03-15` 가 낫다. 32·33 은 시각이다.
         */
        fun builtinCode(id: Int): String? {
            BUILTIN[id]?.let { return it }
            return when (id) {
                32 -> "h:mm"
                33 -> "h:mm:ss"
                in 27..36, in 50..58 -> "yyyy-mm-dd"
                else -> null
            }
        }

        /** 코드를 읽는다. **던지지 않는다** — 읽지 못하면 일반 형식이다. */
        fun parse(code: String): NumberFormat {
            if (code.isEmpty() || code.equals("General", ignoreCase = true)) return GENERAL
            if (code.length > MAX_CODE_CHARS) return GENERAL
            return try {
                val sections = splitSections(code).take(4).map { parseSection(it) }
                if (sections.isEmpty()) GENERAL else NumberFormat(sections)
            } catch (e: RuntimeException) {
                GENERAL
            }
        }

        /** `;` 로 나눈다. 따옴표·대괄호 안과 `\` 뒤의 `;` 는 구분자가 아니다. */
        private fun splitSections(code: String): List<String> {
            val out = ArrayList<String>()
            val cur = StringBuilder()
            var i = 0
            var quoted = false
            var bracket = false
            while (i < code.length) {
                val c = code[i]
                when {
                    quoted -> {
                        cur.append(c)
                        if (c == '"') quoted = false
                    }
                    bracket -> {
                        cur.append(c)
                        if (c == ']') bracket = false
                    }
                    c == '\\' || c == '_' || c == '*' -> {
                        cur.append(c)
                        if (i + 1 < code.length) cur.append(code[++i])
                    }
                    c == '"' -> {
                        quoted = true
                        cur.append(c)
                    }
                    c == '[' -> {
                        bracket = true
                        cur.append(c)
                    }
                    c == ';' -> {
                        out.add(cur.toString())
                        cur.setLength(0)
                    }
                    else -> cur.append(c)
                }
                i++
            }
            out.add(cur.toString())
            return out
        }

        private fun parseSection(s: String): Section {
            val toks = ArrayList<Tok>()
            var color: String? = null
            var condition: Condition? = null
            var locale = 0
            val lit = StringBuilder()
            fun flush() {
                if (lit.isNotEmpty()) {
                    toks.add(Lit(lit.toString()))
                    lit.setLength(0)
                }
            }
            fun add(t: Tok) {
                flush()
                toks.add(t)
            }
            fun run(from: Int, ch: Char): Int {
                var j = from
                while (j < s.length && s[j].equals(ch, ignoreCase = true)) j++
                return j - from
            }

            var i = 0
            while (i < s.length) {
                val c = s[i]
                when {
                    c == '"' -> {
                        val close = s.indexOf('"', i + 1)
                        val end = if (close < 0) s.length else close
                        lit.append(s, i + 1, end)
                        i = end + 1
                        continue
                    }
                    c == '\\' -> {
                        if (i + 1 < s.length) lit.append(s[i + 1])
                        i += 2
                        continue
                    }
                    c == '_' -> {
                        // 다음 글자의 폭만큼 비운다. 폭을 모르니 공백 하나.
                        lit.append(' ')
                        i += 2
                        continue
                    }
                    c == '*' -> {
                        // 칸을 채우는 반복 — 칸 폭을 모르므로 버린다.
                        i += 2
                        continue
                    }
                    c == '[' -> {
                        val close = s.indexOf(']', i + 1)
                        if (close < 0) {
                            lit.append(s, i, s.length)
                            break
                        }
                        val inner = s.substring(i + 1, close)
                        val low = inner.lowercase()
                        when {
                            low in COLORS -> color = COLORS[low]
                            low.startsWith("color") -> {
                                val n = low.removePrefix("color").trim().toIntOrNull()
                                if (n != null && n in 1..56) color = XlsxColors.hex(XlsxColors.DEFAULT_PALETTE[n + 7])
                            }
                            inner.isNotEmpty() && inner[0] in "<>=" -> condition = parseCondition(inner) ?: condition
                            inner.isNotEmpty() && inner.all { it == 'h' || it == 'H' } -> add(DatePart(DateKind.ELAPSED_HOURS, inner.length))
                            inner.isNotEmpty() && inner.all { it == 'm' || it == 'M' } -> add(DatePart(DateKind.ELAPSED_MINUTES, inner.length))
                            inner.isNotEmpty() && inner.all { it == 's' || it == 'S' } -> add(DatePart(DateKind.ELAPSED_SECONDS, inner.length))
                            inner.startsWith("$") -> {
                                // [$기호-로케일]. 기호는 글자로 남기고 로케일은 요일 이름을 고를 때만 쓴다.
                                val body = inner.substring(1)
                                val dash = body.lastIndexOf('-')
                                val symbol = if (dash >= 0) body.substring(0, dash) else body
                                if (symbol.isNotEmpty()) lit.append(symbol)
                                if (dash >= 0) {
                                    val hex = body.substring(dash + 1)
                                    val v = hex.toIntOrNull(16)
                                    if (v != null) locale = v and 0xFFFF
                                }
                            }
                            else -> Unit // [DBNum1] 같은 로케일 전용 지시는 버린다.
                        }
                        i = close + 1
                        continue
                    }
                    c == '0' || c == '#' || c == '?' -> add(Digit(c))
                    c == '.' -> add(Point)
                    c == ',' -> add(Comma)
                    c == '%' -> add(Percent)
                    c == '/' -> add(Slash)
                    c == '@' -> add(At)
                    (c == 'E' || c == 'e') && i + 1 < s.length && (s[i + 1] == '+' || s[i + 1] == '-') -> {
                        add(Exp(s[i + 1] == '+', c))
                        i += 2
                        continue
                    }
                    (c == 'G' || c == 'g') && s.regionMatches(i, "General", 0, 7, ignoreCase = true) -> {
                        add(GeneralTok)
                        i += 7
                        continue
                    }
                    (c == 'A' || c == 'a') && s.regionMatches(i, "AM/PM", 0, 5, ignoreCase = true) -> {
                        add(AmPm(short = false, lower = c == 'a'))
                        i += 5
                        continue
                    }
                    (c == 'A' || c == 'a') && s.regionMatches(i, "A/P", 0, 3, ignoreCase = true) -> {
                        add(AmPm(short = true, lower = c == 'a'))
                        i += 3
                        continue
                    }
                    (c == 'a' || c == 'A') && run(i, 'a') >= 3 -> {
                        val n = run(i, 'a')
                        add(DatePart(DateKind.WEEKDAY_CJK, n))
                        i += n
                        continue
                    }
                    c == 'y' || c == 'Y' || c == 'e' || c == 'E' -> {
                        val n = run(i, c)
                        // `e` 는 한중일 로케일의 연도다. 네 자리로 본다.
                        add(DatePart(DateKind.YEAR, if (c == 'e' || c == 'E') 4 else n))
                        i += n
                        continue
                    }
                    c == 'm' || c == 'M' -> {
                        val n = run(i, 'm')
                        add(DatePart(DateKind.MONTH, n))
                        i += n
                        continue
                    }
                    c == 'd' || c == 'D' -> {
                        val n = run(i, 'd')
                        add(DatePart(DateKind.DAY, n))
                        i += n
                        continue
                    }
                    c == 'h' || c == 'H' -> {
                        val n = run(i, 'h')
                        add(DatePart(DateKind.HOUR, n))
                        i += n
                        continue
                    }
                    c == 's' || c == 'S' -> {
                        val n = run(i, 's')
                        add(DatePart(DateKind.SECOND, n))
                        i += n
                        continue
                    }
                    c == 'g' || c == 'G' -> Unit // 연호 이름(한중일). 그리지 않는다.
                    else -> lit.append(c)
                }
                i++
            }
            flush()
            val tokens = if (toks.any { it is DatePart || it is AmPm }) resolveDate(toks) else toks
            return Section(tokens, color, condition, locale)
        }

        private fun parseCondition(inner: String): Condition? {
            val op = when {
                inner.startsWith("<=") -> "<="
                inner.startsWith(">=") -> ">="
                inner.startsWith("<>") -> "<>"
                inner.startsWith("<") -> "<"
                inner.startsWith(">") -> ">"
                inner.startsWith("=") -> "="
                else -> return null
            }
            val v = inner.substring(op.length).trim().toDoubleOrNull() ?: return null
            if (!v.isFinite()) return null
            return Condition(op, v)
        }

        /**
         * 날짜 구역을 마무리한다.
         *
         * * `m`·`mm` 은 **바로 앞의 날짜 요소가 시(`h`)이거나 바로 뒤가 초(`s`)이면 분**이다
         *   (`h:mm`, `mm:ss`). 그 밖에는 달이다.
         * * 초 뒤의 `.0`·`.00` 은 소수 초다.
         */
        private fun resolveDate(toks: List<Tok>): List<Tok> {
            val out = ArrayList<Tok>(toks.size)
            fun prevDate(from: Int): DatePart? {
                for (j in from - 1 downTo 0) {
                    val t = out.getOrNull(j) ?: continue
                    if (t is DatePart) return t
                    if (t is AmPm) return null
                }
                return null
            }
            fun nextDate(from: Int): DatePart? {
                for (j in from + 1 until toks.size) {
                    val t = toks[j]
                    if (t is DatePart) return t
                    if (t is AmPm) return null
                }
                return null
            }
            var i = 0
            while (i < toks.size) {
                val t = toks[i]
                if (t is DatePart && t.kind == DateKind.MONTH && t.len <= 2) {
                    val prev = prevDate(out.size)
                    val next = nextDate(i)
                    val minute = (prev != null && (prev.kind == DateKind.HOUR || prev.kind == DateKind.ELAPSED_HOURS)) ||
                        (next != null && (next.kind == DateKind.SECOND || next.kind == DateKind.ELAPSED_SECONDS))
                    out.add(if (minute) DatePart(DateKind.MINUTE, t.len) else t)
                    i++
                    continue
                }
                if (t === Point && i + 1 < toks.size && toks[i + 1] is Digit && (toks[i + 1] as Digit).ch == '0') {
                    var j = i + 1
                    while (j < toks.size && toks[j] is Digit && (toks[j] as Digit).ch == '0') j++
                    out.add(Lit("."))
                    out.add(DatePart(DateKind.SUBSECOND, j - i - 1))
                    i = j
                    continue
                }
                out.add(t)
                i++
            }
            return out
        }

        // ---- 일반 형식 -------------------------------------------------------------

        /**
         * 일반(General) 형식. Excel 은 표준 폭의 칸에 **11자**(음수는 부호를 빼고 11자)까지 보인다.
         *
         * 알고리즘은 SheetJS SSF 의 `general_fmt_num` 을 따랐다(Apache-2.0, 그쪽 시험이 Excel 의
         * 실제 출력에서 왔다). 다만 10¹⁰ 대의 수를 SSF 는 자르고 여기서는 반올림한다 —
         * Excel 은 12345678901.5 를 12345678902 로 보인다.
         *
         * @param width 칸에 들어가는 숫자 수. 11 보다 좁으면 [generalFit] 이 소수 자리를 줄이고, 정수
         *   부분조차 넘치면 지수 표기로 간다(기본 폭의 123456789 → 1.23E+08, Excel 과 같다).
         */
        fun general(value: Double, width: Int = 11): String {
            if (value == 0.0) return "0"
            if (!value.isFinite()) return "#NUM!"
            if (width < 11) return generalFit(value, maxOf(width, 1))
            val x = exact15(value)
            val neg = x.signum() < 0
            val width = if (neg) 12 else 11
            val v = floorLog10(x.abs())
            val o = when {
                v in -4..-1 -> toPrecision(x, 10 + v)
                kotlin.math.abs(v) <= 9 -> {
                    val a = stripDecimal(toFixed(x, 12))
                    if (a.length <= width) {
                        a
                    } else {
                        val b = toPrecision(x, 10)
                        if (b.length <= width) b else toExponential(x, 5)
                    }
                }
                v == 10 -> toFixed(x, 0)
                else -> {
                    val a = stripDecimal(toFixed(x, 11))
                    if (a.length > width || a == "0" || a == "-0") toPrecision(x, 6) else a
                }
            }
            return stripDecimal(normalizeExp(o.uppercase()))
        }

        /**
         * 좁은 칸의 일반 형식. 부호도 한 자를 차지한다. 소수는 들어가는 만큼만 남기고(반올림), 정수
         * 부분이 넘치거나 0 으로 반올림될 작은 수는 지수 표기(`d.ddE+XX`)로 간다.
         */
        private fun generalFit(value: Double, width: Int): String {
            val x = exact15(value)
            val neg = x.signum() < 0
            val w = if (neg) width - 1 else width
            val v = floorLog10(x.abs())
            fun fits(s: String) = s.length - (if (neg) 1 else 0) <= w
            if (v >= 0 && v + 1 <= w) {
                val s = stripDecimal(toFixed(x, maxOf(0, w - v - 2)))
                if (fits(s)) return s
            } else if (v in -4..-1 && w >= 3) {
                val s = stripDecimal(toFixed(x, w - 2))
                if (s != "0" && fits(s)) return s
            }
            // 가수 한 자리 + 점 + `E+XX` 넷 — 남는 자리가 소수다.
            val decimals = (w - 6).coerceAtLeast(0)
            return stripDecimal(normalizeExp(toExponential(x, decimals).uppercase()))
        }

        private val STRIP_DECIMAL = Regex("""(?:\.0*|(\.\d*[1-9])0+)$""")
        private val EXP_MANTISSA = Regex("""(?:\.0*|(\.\d*[1-9])0+)[Ee]""")
        private val EXP_ONE_DIGIT = Regex("""(E[+-])(\d)$""")

        private fun stripDecimal(o: String): String =
            if ('.' !in o) o else STRIP_DECIMAL.replace(o) { it.groupValues[1] }

        private fun normalizeExp(o: String): String {
            if ('E' !in o) return o
            val a = EXP_MANTISSA.replace(o) { it.groupValues[1] + "E" }
            return EXP_ONE_DIGIT.replace(a) { it.groupValues[1] + "0" + it.groupValues[2] }
        }

        /** 자바스크립트 `toFixed` 와 같은 모양(절대값 기준 반올림). */
        private fun toFixed(x: BigDecimal, n: Int): String = x.setScale(n, RoundingMode.HALF_UP).toPlainString()

        /** 자바스크립트 `toPrecision` 과 같은 모양. 지수가 -7 이하이거나 자리 수 이상이면 지수 표기. */
        private fun toPrecision(x: BigDecimal, p: Int): String {
            val r = x.round(MathContext(p, RoundingMode.HALF_UP))
            if (r.signum() == 0) return "0"
            val sign = if (r.signum() < 0) "-" else ""
            val a = r.abs()
            val e = floorLog10(a)
            return if (e < -6 || e >= p) {
                val m = a.movePointLeft(e).setScale(p - 1, RoundingMode.HALF_UP).toPlainString()
                sign + m + "e" + (if (e >= 0) "+" else "-") + kotlin.math.abs(e)
            } else {
                sign + a.setScale(maxOf(0, p - 1 - e), RoundingMode.HALF_UP).toPlainString()
            }
        }

        /** 자바스크립트 `toExponential` 과 같은 모양. */
        private fun toExponential(x: BigDecimal, f: Int): String {
            val r = x.round(MathContext(f + 1, RoundingMode.HALF_UP))
            val sign = if (r.signum() < 0) "-" else ""
            val a = r.abs()
            val e = floorLog10(a)
            val m = a.movePointLeft(e).setScale(f, RoundingMode.HALF_UP).toPlainString()
            return sign + m + "e" + (if (e >= 0) "+" else "-") + kotlin.math.abs(e)
        }

        /**
         * 두 배 정밀도 값을 **유효숫자 15자리**로. Excel 은 15자리 넘게 보여 주지 않고, 그 자리에서
         * 먼저 반올림한다 — 2.675 의 이진값(2.67499999…)이 `0.00` 으로 2.68 이 되는 것이 그래서다.
         */
        private fun exact15(v: Double): BigDecimal = BigDecimal(v).round(MathContext(15, RoundingMode.HALF_UP))

        /** 10 을 밑으로 한 로그의 바닥. 0 이 아닌 양수에만. */
        private fun floorLog10(a: BigDecimal): Int = a.precision() - a.scale() - 1

        private fun two(n: Int): String = if (n < 10) "0$n" else n.toString()

        private fun emptyDigit(ch: Char): String = when (ch) {
            '0' -> "0"
            '?' -> " "
            else -> ""
        }

        /**
         * 천 단위 쉼표. 앞의 공백(`?` 자리)은 그대로 두되, **빈 `?` 자리 사이에 들었을 쉼표도 자리를 차지한다** —
         * Excel 은 그 쉼표를 공백으로 채운다(POI 의 진리표: `?,?????????` 에 1234567 → 공백 넷 + '1,234,567').
         */
        private fun group(body: String): String {
            val lead0 = body.takeWhile { !it.isDigit() }
            val digits = body.substring(lead0.length)
            val padSeps = if (lead0.isNotEmpty() && lead0.all { it == ' ' }) {
                (body.length - 1) / 3 - (maxOf(digits.length, 1) - 1) / 3
            } else {
                0
            }
            val lead = if (padSeps > 0) " ".repeat(padSeps) + lead0 else lead0
            if (digits.length <= 3) return lead + digits
            val out = StringBuilder(digits.length + digits.length / 3)
            val first = digits.length % 3
            if (first > 0) out.append(digits, 0, first)
            var i = first
            while (i < digits.length) {
                if (out.isNotEmpty()) out.append(',')
                out.append(digits, i, i + 3)
                i += 3
            }
            return lead + out.toString()
        }

        /**
         * 일련번호의 날짜 부분(일 수)을 달력 날짜로.
         *
         * **1900 체계는 1900 년을 윤년으로 센다**(Lotus 1-2-3 과 맞추려던 Excel 의 오래된 버그). 그래서
         * 60 은 존재하지 않는 1900-02-29 이고, 0 은 1900-01-00 이다. 요일도 그 버그를 따른다 —
         * Excel 의 1900-01-01 은 일요일이다(실제는 월요일). 61(1900-03-01) 부터는 실제 달력과 같다.
         */
        private fun civil(days: Long, date1904: Boolean): Civil {
            if (date1904) {
                val d = LocalDate.of(1904, 1, 1).plusDays(days)
                return Civil(d.year, d.monthValue, d.dayOfMonth, ((days + 5) % 7).toInt())
            }
            val dow = ((days + 6) % 7).toInt()
            return when {
                days == 0L -> Civil(1900, 1, 0, dow)
                days == 60L -> Civil(1900, 2, 29, dow)
                days < 60L -> {
                    val d = LocalDate.of(1899, 12, 31).plusDays(days)
                    Civil(d.year, d.monthValue, d.dayOfMonth, dow)
                }
                else -> {
                    val d = LocalDate.of(1899, 12, 30).plusDays(days)
                    Civil(d.year, d.monthValue, d.dayOfMonth, dow)
                }
            }
        }

        /** 연분수로 [maxDen] 이하의 분모 가운데 [x] 에 가장 가까운 분수. */
        private fun approximate(x: Double, maxDen: Long): Pair<Long, Long> {
            if (x <= 0.0 || maxDen < 1) return 0L to 1L
            var p0 = 0L
            var q0 = 1L
            var p1 = 1L
            var q1 = 0L
            var r = x
            repeat(64) {
                val a = Math.floor(r).toLong()
                val p2 = a * p1 + p0
                val q2 = a * q1 + q0
                if (q2 > maxDen) {
                    // 반수렴분수 가운데 분모 한도 안의 것과 앞 수렴분수를 견준다. 첫 바퀴의 분모는
                    // 언제나 1 이라(q1 = 0 → q2 = 1) 여기 올 때 q1 은 1 이상이다.
                    val k = (maxDen - q0) / q1
                    val pk = p0 + k * p1
                    val qk = q0 + k * q1
                    val better = qk > 0 && kotlin.math.abs(x - pk.toDouble() / qk) < kotlin.math.abs(x - p1.toDouble() / q1)
                    return if (better) pk to qk else p1 to q1
                }
                p0 = p1
                q0 = q1
                p1 = p2
                q1 = q2
                val f = r - a
                if (f < 1e-12) return p1 to q1
                r = 1.0 / f
            }
            return p1 to q1
        }
    }
}
