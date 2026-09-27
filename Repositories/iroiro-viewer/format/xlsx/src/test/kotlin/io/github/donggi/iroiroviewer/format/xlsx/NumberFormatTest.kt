package io.github.donggi.iroiroviewer.format.xlsx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 표시 형식. 기대값은 Excel 의 표시(명세의 예와 널리 알려진 출력)에서 왔다 — 우리 구현을 돌려서 적은
 * 값이 아니다. 날짜는 Python `datetime` 으로 따로 확인한 일련번호를 쓴다(45366 = 2024-03-15).
 */
class NumberFormatTest {

    private fun f(code: String, v: Double, date1904: Boolean = false): String = NumberFormat.parse(code).format(v, date1904).text

    private fun builtin(id: Int, v: Double): String = f(NumberFormat.builtinCode(id)!!, v)

    // ---- 일반 형식 ----

    @Test
    fun 일반_형식은_부동소수_찌꺼기를_지운다() {
        assertEquals("0.3", NumberFormat.general(0.1 + 0.2))
        assertEquals("0.333333333", NumberFormat.general(1.0 / 3))
        assertEquals("-0.333333333", NumberFormat.general(-1.0 / 3))
        assertEquals("666666.6667", NumberFormat.general(2.0 / 3 * 1e6))
    }

    @Test
    fun 일반_형식은_열한_자를_넘으면_지수로() {
        assertEquals("1.23457E+12", NumberFormat.general(1234567890123.0))
        assertEquals("1E+15", NumberFormat.general(1e15))
        assertEquals("1E-10", NumberFormat.general(1e-10))
        assertEquals("12345678901", NumberFormat.general(12345678901.0))
    }

    @Test
    fun 일반_형식의_정수와_소수() {
        assertEquals("0", NumberFormat.general(0.0))
        assertEquals("100", NumberFormat.general(100.0))
        assertEquals("-1.5", NumberFormat.general(-1.5))
        assertEquals("123456.789", NumberFormat.general(123456.789012))
        assertEquals("100", f("General", 100.0))
    }

    @Test
    fun 좁은_칸의_일반_형식은_소수를_줄이고_넘치면_지수로() {
        assertEquals("323832.5", NumberFormat.general(323832.4567, 8))
        assertEquals("-323832", NumberFormat.general(-323832.4567, 8))
        assertEquals("12345678", NumberFormat.general(12345678.0, 8))
        assertEquals("1.23E+08", NumberFormat.general(123456789.0, 8))
        assertEquals("1E+08", NumberFormat.general(99999999.6, 8))
        assertEquals("0.123457", NumberFormat.general(0.123456789, 8))
        assertEquals("1.23E-05", NumberFormat.general(0.00001234, 8))
        // 넓으면 11자에서 멈춘다.
        assertEquals("323832.4567", NumberFormat.general(323832.4567, 40))
        assertEquals("323832.5", NumberFormat.GENERAL.format(323832.4567, generalWidth = 8).text)
    }

    // ---- 숫자 ----

    @Test
    fun 소수_자리는_15자리에서_먼저_반올림한다() {
        // 2.675 의 이진값은 2.67499999… 다. Excel 은 2.68 을 보인다.
        assertEquals("2.68", f("0.00", 2.675))
        assertEquals("0.50", f("0.00", 0.5))
        assertEquals("3", f("0", 2.5))
    }

    @Test
    fun 천_단위_쉼표() {
        assertEquals("1,234,567", f("#,##0", 1234567.0))
        assertEquals("-1,234.50", f("#,##0.00", -1234.5))
        assertEquals("0", f("#,##0", 0.0))
        assertEquals("999", f("#,##0", 999.0))
    }

    @Test
    fun 끝의_쉼표는_천으로_나눈다() {
        assertEquals("1,235", f("#,##0,", 1234567.0))
        assertEquals("1.2", f("0.0,,", 1234567.0))
    }

    @Test
    fun 백분율() {
        assertEquals("26%", f("0%", 0.256))
        assertEquals("25.60%", f("0.00%", 0.256))
        assertEquals("25.60%", builtin(10, 0.256))
    }

    @Test
    fun 샵과_물음표와_영() {
        assertEquals(".5", f("#.##", 0.5))
        assertEquals("5.", f("#.##", 5.0))
        assertEquals("00123", f("00000", 123.0))
        assertEquals("12345", f("00", 12345.0))
        assertEquals(" 5", f("??", 5.0))
        assertEquals("1.5 ", f("0.0?", 1.5))
    }

    @Test
    fun 자리표_사이의_글자는_제자리에() {
        assertEquals("123-4567", f("000-0000", 1234567.0))
        assertEquals("(010) 123-4567", f("(000) 000-0000", 101234567.0))
    }

    @Test
    fun 음수_구역은_부호를_떼고_괄호를_쓴다() {
        assertEquals("(1,234)", f("#,##0_);(#,##0)", -1234.0))
        // `_)` 는 괄호 폭만큼 비운다 — 공백 하나.
        assertEquals("1,234 ", f("#,##0_);(#,##0)", 1234.0))
        val red = NumberFormat.parse(NumberFormat.builtinCode(38)!!).format(-5.0)
        assertEquals("(5)", red.text)
        assertEquals("#ff0000", red.color)
    }

    @Test
    fun 세_구역과_빈_구역() {
        assertEquals("zero", f("0.00;[Red]-0.00;\"zero\"", 0.0))
        assertEquals("-1.50", f("0.00;[Red]-0.00;\"zero\"", -1.5))
        assertEquals("", f("0;-0;", 0.0))
        assertEquals("", f(";;;", 5.0))
    }

    @Test
    fun 한_구역이면_음수에_부호를_붙인다() {
        assertEquals("-$1,234.50", f("\"$\"#,##0.00", -1234.5))
        assertEquals("$1,234.50", f("\"$\"#,##0.00", 1234.5))
    }

    @Test
    fun 회계_형식() {
        assertEquals(" $1,234.50 ", builtin(44, 1234.5))
        assertEquals(" $(1,234.50)", builtin(44, -1234.5))
    }

    @Test
    fun 글자_그대로와_역슬래시() {
        assertEquals("5 kg", f("0\\ \"kg\"", 5.0))
        assertEquals("#5", f("\\#0", 5.0))
        assertEquals("합계 12", f("\"합계 \"General", 12.0))
    }

    @Test
    fun 지수() {
        assertEquals("1.23E+04", f("0.00E+00", 12345.0))
        assertEquals("1.20E-04", f("0.00E+00", 0.00012))
        assertEquals("12.3E+3", f("##0.0E+0", 12345.0))
        assertEquals("1.00E+01", f("0.00E+00", 9.999))
        assertEquals("0.00E+00", f("0.00E+00", 0.0))
    }

    @Test
    fun 분수는_최선을_다한다() {
        assertEquals("5 1/4", f("# ?/?", 5.25))
        assertEquals("3 1/2", f("# ?/?", 3.5))
        assertEquals("5", f("# ?/?", 5.0))
        assertEquals("1/3", f("# ??/??", 0.333).trim())
        assertEquals("5/8", f("?/8", 0.625))
        assertEquals("7/2", f("?/?", 3.5))
    }

    @Test
    fun 영이_든_고정_분모() {
        // Excel 의 '10분의'(`# ?/10`)·'100분의'(`# ??/100`) 형식. `0` 이 자리표로 잘려 분모가 1 로
        // 줄던 결함의 회귀 시험 — 5.37 이 `5` 로 보였다.
        assertEquals("5 4/10", f("# ?/10", 5.37))
        assertEquals("37/100", f("# ??/100", 0.37).trim())
        assertEquals("37/100", f("?/100", 0.37))
        assertEquals("5", f("# ?/10", 5.02))
        assertEquals("5 4/10 cm", f("# ?/10\" cm\"", 5.37))
        assertEquals("2 1/105", f("# ?/105", 2.0 + 1.0 / 105))
    }

    @Test
    fun 조건_구역() {
        assertEquals("small", f("[<100]\"small\";\"big\"", 5.0))
        assertEquals("big", f("[<100]\"small\";\"big\"", 500.0))
        assertEquals("1,235K", f("[>=1000]#,##0,\"K\";0", 1234567.0))
        assertEquals("5", f("[>=1000]#,##0,\"K\";0", 5.0))
    }

    @Test
    fun 색_지정() {
        assertEquals("#0000ff", NumberFormat.parse("[Blue]0").format(5.0).color)
        // [Color10] 은 옛 팔레트의 17번(10 + 7) — 008000.
        assertEquals("#008000", NumberFormat.parse("[Color10]0").format(5.0).color)
        assertNull(NumberFormat.parse("0").format(5.0).color)
    }

    @Test
    fun 글자_구역() {
        val fmt = NumberFormat.parse("0.00;-0.00;0;\"<\"@\">\"")
        assertEquals("<x>", fmt.formatText("x").text)
        assertEquals("abc", NumberFormat.parse("0.00").formatText("abc").text)
        assertEquals("", NumberFormat.parse(";;;").formatText("hidden").text)
        // `@` 만 있는 형식의 숫자는 일반 형식으로 보인다.
        assertEquals("123", f("@", 123.0))
    }

    // ---- 날짜·시각 ----

    @Test
    fun 날짜() {
        assertEquals("2024-03-15", f("yyyy-mm-dd", 45366.0))
        assertEquals("2024-03-15 13:45", f("yyyy-mm-dd hh:mm", 45366.57291666666))
        assertEquals("Mar 15, 2024", f("mmm d, yyyy", 45366.0))
        assertEquals("March", f("mmmm", 45366.0))
        assertEquals("M", f("mmmmm", 45366.0))
        assertEquals("Fri", f("ddd", 45366.0))
    }

    @Test
    fun 내장_날짜_형식() {
        assertEquals("03-15-24", builtin(14, 45366.0))
        assertEquals("15-Mar-24", builtin(15, 45366.0))
        assertEquals("3/15/24 13:45", builtin(22, 45366.57291666666))
    }

    @Test
    fun 윤년_버그_1900년() {
        assertEquals("1900-02-28", f("yyyy-mm-dd", 59.0))
        assertEquals("1900-02-29", f("yyyy-mm-dd", 60.0))
        assertEquals("1900-03-01", f("yyyy-mm-dd", 61.0))
        assertEquals("1900-01-01", f("yyyy-mm-dd", 1.0))
        assertEquals("1900-01-00", f("yyyy-mm-dd", 0.0))
        // Excel 의 1900-01-01 은 일요일이다(실제는 월요일). 61 부터는 실제 달력과 같다(1900-03-01 은 목요일).
        assertEquals("Sunday", f("dddd", 1.0))
        assertEquals("Thursday", f("dddd", 61.0))
    }

    @Test
    fun 날짜_체계_1904() {
        assertEquals("1904-01-01", f("yyyy-mm-dd", 0.0, date1904 = true))
        // 두 체계의 차이는 1462 일이다.
        assertEquals("2024-03-15", f("yyyy-mm-dd", 45366.0 - 1462, date1904 = true))
        assertEquals("Friday", f("dddd", 0.0, date1904 = true))
    }

    /**
     * POI 의 표시 형식 진리표(`NumberFormatTests.xlsx` — Excel 이 `TEXT()` 로 계산해 둔 값)가 잡은 셋. 자리표 앞의
     * 쉼표는 글자이고, 빈 `?` 자리 사이에 들었을 쉼표는 공백으로 자리를 차지하며, 0 이 지운 정수 자리 뒤의
     * 글자는 쓰지 않는다. 셋 다 예전에는 달랐다(쉼표를 떨구고, 공백이 하나 모자라고, ':3/4' 였다).
     */
    @Test
    fun 진리표가_잡은_쉼표와_분수() {
        assertEquals(",1234567", f(",#", 1234567.0))
        assertEquals("-,1234567", f(",0", -1234567.0))
        assertEquals("    1,234,567", f("?,?????????", 1234567.0))
        assertEquals(" 1,234,567", f("?,???????", 1234567.0))
        assertEquals("1,234,567", f("?,??????", 1234567.0))
        assertEquals("|3/4|", f("|#\\:#/#|", 0.75))
        assertEquals("|--3/4|", f("|#-#-#\\:#/#|", 0.75))
        // 정수가 있으면 글자는 그대로다.
        assertEquals("|23:3/4|", f("|#\\:#/#|", 23.75))
        assertEquals("|-2-3:3/4|", f("|#-#-#\\:#/#|", 23.75))
        // 분모의 `0` 자리는 앞에 채운다 — 뒤에 채우면 3/4 가 3/400 이 된다.
        assertEquals("|2:3  03/004", f("|#\\:? ?0#/000", 23.75))
        assertEquals("3/004", f("?/000", 0.75))
    }

    @Test
    fun 오전_오후() {
        assertEquals("6:00 PM", f("h:mm AM/PM", 0.75))
        assertEquals("12:00 AM", f("h:mm AM/PM", 0.0))
        assertEquals("12:30 PM", f("h:mm AM/PM", 0.5 + 30.0 / 1440))
        assertEquals("9:05 a", f("h:mm a/p", 9.0 / 24 + 5.0 / 1440))
        // 소문자 `am/pm` 도 `AM`·`PM` 으로 보인다 — 소문자를 따르는 것은 `a/p` 뿐이다. POI 의 날짜 진리표
        // (Excel 이 계산해 둔 값)가 'hh:mm:ss.000 am/pm' 에 '02:35:27.000 PM' 을 적는다. 예전에는 'pm' 이었다.
        assertEquals("02:35:27.000 PM", f("hh:mm:ss.000 am/pm", 0.6079513888888889))
        assertEquals("2:35:27 p", f("h:m:s a/p", 0.6079513888888889))
    }

    @Test
    fun 분과_달을_가린다() {
        assertEquals("01:30", f("mm:ss", 90.0 / 86400))
        assertEquals("10:07", f("h:mm", 10.0 / 24 + 7.0 / 1440))
        assertEquals("2024-03-15 10:07:05", f("yyyy-mm-dd hh:mm:ss", 45366 + (10 * 3600 + 7 * 60 + 5) / 86400.0))
    }

    @Test
    fun 누적_시간() {
        assertEquals("36:00:00", f("[h]:mm:ss", 1.5))
        assertEquals("30:00", f("[mm]:ss", 0.5 / 24))
        assertEquals("5400", f("[ss]", 1.5 / 24))
    }

    @Test
    fun 초를_먼저_반올림하고_분은_자른다() {
        val t = (10 * 3600 + 29 * 60 + 59.6) / 86400.0
        assertEquals("10:30", f("h:mm", t))
        assertEquals("10:29", f("h:mm", (10 * 3600 + 29 * 60 + 40) / 86400.0))
        // 반올림이 자정을 넘기면 날짜도 넘어간다.
        assertEquals("2024-03-16 00:00:00", f("yyyy-mm-dd hh:mm:ss", 45366.99999999))
    }

    @Test
    fun 소수_초() {
        assertEquals("00:01.5", f("mm:ss.0", 1.5 / 86400))
        assertEquals("00:01.50", f("mm:ss.00", 1.5 / 86400))
    }

    @Test
    fun 범위_밖의_날짜는_일반_형식() {
        assertEquals("-1", f("yyyy-mm-dd", -1.0))
        assertEquals("3000000", f("yyyy-mm-dd", 3_000_000.0))
    }

    @Test
    fun 로케일_지시는_버리고_기호는_남긴다() {
        assertEquals("2024년 3월 15일 금요일", f("[\$-412]yyyy\"년\" m\"월\" d\"일\" aaaa", 45366.0))
        assertEquals("₩1,234", f("[\$₩-412]#,##0", 1234.0))
        assertEquals("(금)", f("(aaa)", 45366.0))
        assertEquals("金曜日", f("[\$-411]aaaa", 45366.0))
    }

    @Test
    fun 한중일_내장_번호는_날짜로_살린다() {
        assertEquals("yyyy-mm-dd", NumberFormat.builtinCode(31))
        assertEquals("2024-03-15", builtin(31, 45366.0))
        assertNull(NumberFormat.builtinCode(200))
        assertTrue(NumberFormat.parse(NumberFormat.builtinCode(14)!!).isDate)
    }

    @Test
    fun 이상한_코드에_던지지_않는다() {
        for (code in listOf("[Red", "\"abc", "0.0.0", "[<abc]0", "#,,,,##0.0E+", "yyyy[", "?/", "E+", "[\$", "\\")) {
            val out = NumberFormat.parse(code).format(1234.5)
            assertTrue(out.text.length < 100, code)
        }
        assertEquals("1234.5", f("x".repeat(2000), 1234.5))
    }
}
