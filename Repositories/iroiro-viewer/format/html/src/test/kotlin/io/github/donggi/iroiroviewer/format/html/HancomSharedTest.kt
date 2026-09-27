package io.github.donggi.iroiroviewer.format.html

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 두 한글 변환기가 같이 쓰는 규칙. 처음에는 변환기마다 한 벌씩 있어 같은 문서가 포맷에 따라 달랐다(13단계 짝 대조) —
 * 여기 있는 값은 **두 포맷의 답**이다.
 */
class HancomSharedTest {

    @Test
    fun 번호_모양은_HWP_번호와_HWPX_이름이_같은_답을_낸다() {
        val names = listOf(
            "DIGIT", "CIRCLED_DIGIT", "ROMAN_CAPITAL", "ROMAN_SMALL", "LATIN_CAPITAL", "LATIN_SMALL",
            "CIRCLED_LATIN_CAPITAL", "CIRCLED_LATIN_SMALL", "HANGUL_SYLLABLE", "CIRCLED_HANGUL_SYLLABLE", "HANGUL_JAMO",
            "CIRCLED_HANGUL_JAMO", "HANGUL_PHONETIC", "IDEOGRAPH", "CIRCLED_IDEOGRAPH", "DECAGON_CIRCLE", "DECAGON_CIRCLE_HANJA",
        )
        for ((code, name) in names.withIndex()) {
            assertEquals(HancomNumbers.shapeOf(code), HancomNumbers.shapeOf(name), name)
            for (v in listOf(0, 1, 3, 14, 21, 27, 50, 51)) {
                assertEquals(
                    HancomNumbers.format(v, HancomNumbers.shapeOf(code)),
                    HancomNumbers.format(v, HancomNumbers.shapeOf(name)),
                    "$name $v",
                )
            }
        }
        assertEquals(HancomNumbers.SYMBOL, HancomNumbers.shapeOf(0x80))
        assertEquals(HancomNumbers.SYMBOL, HancomNumbers.shapeOf("SYMBOL"))
        assertEquals(HancomNumbers.USER_CHAR, HancomNumbers.shapeOf(0x81))
        assertEquals(HancomNumbers.USER_CHAR, HancomNumbers.shapeOf("user_char"))
        assertEquals(HancomNumbers.DIGIT, HancomNumbers.shapeOf(17))
        assertEquals(HancomNumbers.DIGIT, HancomNumbers.shapeOf("모르는 모양"))
    }

    @Test
    fun 두_변환기가_갈렸던_값() {
        // 동그라미 숫자 21~50 — HWP 는 숫자로, HWPX 는 동그라미로 적었다.
        assertEquals("⑳", HancomNumbers.format(20, HancomNumbers.CIRCLED_DIGIT))
        assertEquals("㉑", HancomNumbers.format(21, HancomNumbers.CIRCLED_DIGIT))
        assertEquals("㉟", HancomNumbers.format(35, HancomNumbers.CIRCLED_DIGIT))
        assertEquals("㊱", HancomNumbers.format(36, HancomNumbers.CIRCLED_DIGIT))
        assertEquals("㊿", HancomNumbers.format(50, HancomNumbers.CIRCLED_DIGIT))
        assertEquals("51", HancomNumbers.format(51, HancomNumbers.CIRCLED_DIGIT))
        // 26 을 넘는 영문자 — HWP 는 `ab`, HWPX 는 `bb` 였다. 같은 글자를 겹친다.
        assertEquals("z", HancomNumbers.format(26, HancomNumbers.LATIN_SMALL))
        assertEquals("aa", HancomNumbers.format(27, HancomNumbers.LATIN_SMALL))
        assertEquals("BB", HancomNumbers.format(28, HancomNumbers.LATIN_CAPITAL))
        // '네 글자가 차례로 되풀이' 는 어떤 글자인지 모른다 — 숫자다(HWPX 는 늘 `•` 였다).
        assertEquals("3", HancomNumbers.format(3, HancomNumbers.SYMBOL))
        assertEquals("", HancomNumbers.format(3, HancomNumbers.NONE))
    }

    @Test
    fun 번호_모양_하나하나() {
        fun f(v: Int, s: Int) = HancomNumbers.format(v, s)
        assertEquals("XIV", f(14, HancomNumbers.ROMAN_CAPITAL))
        assertEquals("iv", f(4, HancomNumbers.ROMAN_SMALL))
        assertEquals("Ⓒ", f(3, HancomNumbers.CIRCLED_LATIN_CAPITAL))
        assertEquals("ⓒ", f(3, HancomNumbers.CIRCLED_LATIN_SMALL))
        assertEquals("다", f(3, HancomNumbers.HANGUL_SYLLABLE))
        assertEquals("가", f(15, HancomNumbers.HANGUL_SYLLABLE))
        assertEquals("㉰", f(3, HancomNumbers.CIRCLED_HANGUL_SYLLABLE))
        assertEquals("ㄷ", f(3, HancomNumbers.HANGUL_JAMO))
        assertEquals("㉢", f(3, HancomNumbers.CIRCLED_HANGUL_JAMO))
        assertEquals("이십삼", f(23, HancomNumbers.HANGUL_PHONETIC))
        assertEquals("百十一", f(111, HancomNumbers.IDEOGRAPH))
        assertEquals("㊂", f(3, HancomNumbers.CIRCLED_IDEOGRAPH))
        assertEquals("병", f(3, HancomNumbers.DECAGON_CIRCLE))
        assertEquals("丙", f(3, HancomNumbers.DECAGON_CIRCLE_HANJA))
        assertEquals("0", f(0, HancomNumbers.CIRCLED_DIGIT))
        assertEquals("10000", f(10_000, HancomNumbers.HANGUL_PHONETIC))
    }

    @Test
    fun 번호_형식을_채운다() {
        val shapes = intArrayOf(HancomNumbers.DIGIT, HancomNumbers.HANGUL_SYLLABLE, HancomNumbers.CIRCLED_DIGIT) +
            IntArray(7)
        val c = HancomNumbers.Counters()
        val starts = IntArray(10) { 1 }
        c.next(7, 0, starts)
        c.next(7, 1, starts)
        val v = c.next(7, 2, starts)
        assertEquals("1.가.①", HancomNumbers.fill("^1.^2.^3", 2, v, shapes))
        assertEquals("1.1.1", HancomNumbers.fill("^n", 2, v, IntArray(10)))
        assertEquals("1.1.1.", HancomNumbers.fill("^N", 2, v, IntArray(10)))
        // 아직 쓰이지 않은 깊은 수준은 비워 둔다.
        assertEquals("(①)", HancomNumbers.fill("(^3)^5", 2, v, shapes))
        // `^10` 은 수준 10.
        val deep = HancomNumbers.Counters().next(1, 9, IntArray(10) { 4 })
        assertEquals("4-4", HancomNumbers.fill("^1-^10", 9, deep, IntArray(10)))
    }

    @Test
    fun 셈은_깊은_수준을_비우고_시작_번호_0_을_지킨다() {
        val c = HancomNumbers.Counters()
        val starts = intArrayOf(0, 5) + IntArray(8) { 1 }
        assertEquals(0, c.next(1, 0, starts)[0])
        assertEquals(5, c.next(1, 1, starts)[1])
        assertEquals(6, c.next(1, 1, starts)[1])
        val back = c.next(1, 0, starts)
        assertEquals(1, back[0])
        assertEquals(HancomNumbers.UNSET, back[1])
        assertEquals(5, c.next(1, 1, starts)[1])
        // 2수준부터 시작한 정의는 위 수준을 시작 번호로 채운다.
        val skipped = HancomNumbers.Counters().next(2, 2, IntArray(10) { 3 })
        assertEquals(listOf(3, 3, 3), skipped.take(3))
        // 찍어 둔 셈은 따로 논다.
        val snap = c.copy()
        c.next(1, 1, starts)
        assertEquals(6, snap.next(1, 1, starts)[1])
    }

    @Test
    fun 각주_표지() {
        assertEquals("1)", HancomNumbers.noteLabel(1, HancomNumbers.DIGIT, "", "", ")"))
        assertEquals("(iii)", HancomNumbers.noteLabel(3, HancomNumbers.ROMAN_SMALL, "", "(", ")"))
        // 사용자 글자(K26 의 `*` 표시).
        assertEquals("*", HancomNumbers.noteLabel(4, HancomNumbers.USER_CHAR, "*", "", ""))
        assertEquals("4", HancomNumbers.noteLabel(4, HancomNumbers.USER_CHAR, "", "", ""))
    }

    @Test
    fun 제목은_방향_문자를_버리고_공백을_줄인다() {
        assertEquals("가 나 다", HancomTitles.clean(" 가\t\t나\n다 "))
        assertEquals("abc", HancomTitles.clean("a‮b⁦c‏"))
        assertEquals(HancomTitles.MAX_CHARS, HancomTitles.clean("가".repeat(1_000)).length)
        assertEquals("", HancomTitles.clean("\u0001‪"))
    }

    @Test
    fun 부분은_블록이나_글자가_넘친_뒤_제목_앞에서_끊는다() {
        val m = HancomChunkMeter(HancomChunkPolicy(softChars = 10, softBlocks = 5, hardChars = 20, hardBlocks = 8))
        assertFalse(m.wantsCut(heading = true), "처음에는 끊을 것이 없다")
        m.onBlock()
        m.addBlocks(4) // 칸 넷 — 블록이다
        assertFalse(m.wantsCut(heading = false))
        assertTrue(m.wantsCut(heading = true), "부드러운 기준은 제목 앞에서만")
        assertTrue(m.cut(1))
        assertFalse(m.wantsCut(heading = true))
        m.onBlock()
        m.addChars(20)
        assertTrue(m.wantsCut(heading = false), "딱딱한 기준은 아무 문단 앞에서나")
    }

    @Test
    fun 찍어_둘_상태가_예산을_넘으면_더_끊지_않는다() {
        val m = HancomChunkMeter(HancomChunkPolicy(hardBlocks = 1, snapshotBudget = 10))
        m.onBlock()
        assertTrue(m.cut(6))
        m.onBlock()
        assertFalse(m.cut(6))
        assertTrue(m.saturated)
        assertFalse(m.wantsCut(heading = true))
        // 구역의 경계는 예산과 관계없이 끊는다.
        assertTrue(m.cut(6, force = true))
    }

    @Test
    fun 겹칠_글자는_옮기고_남은_사설_영역_글자를_버린다() {
        val ok = HancomChars.compose(String(Character.toChars(0xF02B6)))
        assertEquals("６", ok.text)
        assertFalse(ok.dropped)
        val bad = HancomChars.compose("가" + String(Character.toChars(0xF1234)) + "나")
        assertEquals("가나", bad.text)
        assertTrue(bad.dropped)
        val plain = HancomChars.compose("①")
        assertEquals("①", plain.text)
        assertFalse(plain.dropped)
    }

    @Test
    fun 줄_간격은_단위_없는_수() {
        assertEquals("1.3", CssValues.number(1.3, 0.5, 5.0))
        assertEquals("1", CssValues.number(1.0, 0.5, 5.0))
        assertEquals("0.5", CssValues.number(0.1, 0.5, 5.0))
        assertEquals("5", CssValues.number(80.0, 0.5, 5.0))
        assertEquals(null, CssValues.number(Double.NaN, 0.5, 5.0))
    }

    @Test
    fun 따로_쓴_것을_잇는다() {
        val side = HtmlWriter()
        side.start("div", "class" to "cap").text("<표 1>").end("div")
        val w = HtmlWriter()
        w.start("table").end("table").append(side)
        assertEquals("<table></table><div class=\"cap\">&lt;표 1&gt;</div>", w.toString())
        val open = HtmlWriter().start("p")
        assertFailsWith<IllegalArgumentException> { w.append(open) }
        // 상한을 넘으면 거기서 멈춘다.
        val small = HtmlWriter(maxChars = 10)
        small.append(side)
        assertTrue(small.full)
    }

    @Test
    fun 바탕_스타일은_캡션을_줄이지_않고_칸을_가운데에_둔다() {
        val css = HancomCss.FLOW
        assertTrue("vertical-align:middle" in css)
        assertTrue("line-height:1.6" in css)
        assertFalse(Regex("line-height:[0-9.]+%").containsMatchIn(css), "백분율 줄 간격은 자식에게 길이로 물려진다")
        assertFalse(".92em" in css)
        // 위생기가 받아들이는 CSS 다.
        val dropped = io.github.donggi.iroiroviewer.format.UnsupportedFeatures()
        assertEquals(css, Css.sanitize(css, { null }, dropped))
        assertTrue(dropped.isEmpty)
    }
}
