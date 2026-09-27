package io.github.donggi.iroiroviewer.format.pptx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 색 계산과 글머리 — 순수 계산이라 손으로 계산한 값과 견준다.
 *
 * 기대값의 출처: `4472C4` 의 '어둡게 25%'(`lumMod 75000`)는 파워포인트가 `2F5597` 로 보여 주고,
 * '밝게 40%'(`lumMod 60000` + `lumOff 40000`)는 `8FAADC` 로 보여 준다. HSL 로 손 계산해도 같다
 * (h=218.4°, s=0.5203, l=0.5176 → l×0.75=0.3882 → (47,85,151)).
 */
class ColorMathTest {

    private fun scheme(name: String, vararg mods: Pair<String, Int>) =
        ColorSpec(ColorKind.SCHEME, name, mods = mods.map { ColorMod(it.first, it.second) })

    private fun srgb(hex: String, vararg mods: Pair<String, Int>) =
        ColorSpec(ColorKind.SRGB, hex, mods = mods.map { ColorMod(it.first, it.second) })

    private val office = ColorResolver(emptyMap(), emptyMap())

    @Test
    fun 어둡게_25퍼센트는_HSL_명도를_곱한다() {
        assertEquals("#2f5597", office.resolve(scheme("accent1", "lumMod" to 75_000))!!.hex())
    }

    @Test
    fun 밝게_40퍼센트는_명도를_곱하고_더한다() {
        assertEquals("#8faadc", office.resolve(srgb("4472C4", "lumMod" to 60_000, "lumOff" to 40_000))!!.hex())
        // 순서가 뜻을 갖는다 — 더한 뒤 곱하면 다른 색이다.
        val reversed = office.resolve(srgb("4472C4", "lumOff" to 40_000, "lumMod" to 60_000))!!.hex()
        assert(reversed != "#8faadc") { reversed }
    }

    @Test
    fun 음영과_농도는_선형_RGB_에서_섞는다() {
        // 흰색 50% 음영: 선형 0.5 → sRGB 1.055×0.5^(1/2.4)−0.055 = 0.7354 → 188(0xBC).
        // sRGB 에서 그대로 곱했다면 128(0x80)이다 — 이 시험이 공간을 가린다.
        assertEquals("#bcbcbc", office.resolve(srgb("FFFFFF", "shade" to 50_000))!!.hex())
        assertEquals("#bcbcbc", office.resolve(srgb("000000", "tint" to 50_000))!!.hex())
    }

    @Test
    fun 알파는_rgba_로_불투명은_16진으로() {
        val half = office.resolve(srgb("FF0000", "alpha" to 50_000))!!
        assertEquals("rgba(255,0,0,0.5)", half.css())
        assertEquals("#ff0000", office.resolve(srgb("FF0000"))!!.css())
        // 엄격(Strict) 표기 `50%` 는 읽는 쪽에서 50000 으로 바뀐다(DrawingParser). 계산은 같은 값을 받는다.
        assertEquals("rgba(0,0,0,0)", Rgba(0.0, 0.0, 0.0, 0.0).css())
    }

    @Test
    fun 테마색은_색_대응표를_거친다() {
        val theme = mapOf("dk1" to Rgba.parseHex("111111")!!, "lt1" to Rgba.parseHex("EEEEEE")!!, "lt2" to Rgba.parseHex("DDDDDD")!!)
        val normal = ColorResolver(theme, mapOf("bg1" to "lt1", "tx1" to "dk1", "bg2" to "lt2"))
        assertEquals("#111111", normal.resolve(scheme("tx1"))!!.hex())
        assertEquals("#dddddd", normal.resolve(scheme("bg2"))!!.hex())
        // 어두운 배경 마스터는 대응표를 뒤집는다 — 같은 tx1 이 밝은 색이 된다.
        val dark = ColorResolver(theme, mapOf("bg1" to "dk1", "tx1" to "lt1"))
        assertEquals("#eeeeee", dark.resolve(scheme("tx1"))!!.hex())
        assertEquals("#111111", dark.resolve(scheme("bg1"))!!.hex())
        // 테마에 없는 슬롯은 오피스 기본 테마에서.
        assertEquals("#4472c4", normal.resolve(scheme("accent1"))!!.hex())
    }

    @Test
    fun 시스템색과_미리정한_색() {
        assertEquals("#123456", office.resolve(ColorSpec(ColorKind.SYSTEM, "windowText", last = "123456"))!!.hex())
        assertEquals("#000000", office.resolve(ColorSpec(ColorKind.SYSTEM, "windowText"))!!.hex())
        assertEquals("#00008b", office.resolve(ColorSpec(ColorKind.PRESET, "dkBlue"))!!.hex())
        assertEquals("#ff0000", office.resolve(ColorSpec(ColorKind.PRESET, "red"))!!.hex())
        assertNull(office.resolve(ColorSpec(ColorKind.PRESET, "noSuchColor")))
    }

    @Test
    fun HSL_과_scRGB_색() {
        // 색상 120°(= 7200000), 채도 100%, 명도 50% → 순수 초록.
        assertEquals("#00ff00", office.resolve(ColorSpec(ColorKind.HSL, "", nums = intArrayOf(7_200_000, 100_000, 50_000)))!!.hex())
        // scRGB 는 선형 값이다 — 50% 는 sRGB 188.
        assertEquals("#bc0000", office.resolve(ColorSpec(ColorKind.SCRGB, "", nums = intArrayOf(50_000, 0, 0)))!!.hex())
    }

    @Test
    fun 보색과_회색() {
        assertEquals("#00ffff", office.resolve(srgb("FF0000", "comp" to 0))!!.hex())
        // 0.3×1 = 0.3 → 76.5 → 77(0x4D). 반올림은 floor(x+0.5).
        assertEquals("#4d4d4d", office.resolve(srgb("FF0000", "gray" to 0))!!.hex())
    }

    @Test
    fun 깨진_16진은_색이_아니다() {
        assertNull(Rgba.parseHex("12345"))
        assertNull(Rgba.parseHex("GG0000"))
        assertNull(office.resolve(srgb("red; background:url(x)")))
    }

    @Test
    fun 자동_번호의_모양() {
        assertEquals("3.", Bullets.autoNumber("arabicPeriod", 3))
        assertEquals("3)", Bullets.autoNumber("arabicParenR", 3))
        assertEquals("(3)", Bullets.autoNumber("arabicParenBoth", 3))
        assertEquals("c.", Bullets.autoNumber("alphaLcPeriod", 3))
        assertEquals("aa.", Bullets.autoNumber("alphaLcPeriod", 27))
        assertEquals("B)", Bullets.autoNumber("alphaUcParenR", 2))
        assertEquals("IV.", Bullets.autoNumber("romanUcPeriod", 4))
        assertEquals("xiv.", Bullets.autoNumber("romanLcPeriod", 14))
        assertEquals("③", Bullets.autoNumber("circleNumDbPlain", 3))
        assertEquals("21", Bullets.autoNumber("circleNumDbPlain", 21))
        assertEquals("２．", Bullets.autoNumber("arabicDbPeriod", 2))
        assertEquals("二十一.", Bullets.autoNumber("ea1JpnKorPeriod", 21))
        // 모르는 모양은 1. 로.
        assertEquals("5.", Bullets.autoNumber("hebrew2Minus", 5))
    }

    @Test
    fun 기호_글꼴의_글머리를_유니코드로_옮긴다() {
        assertEquals("▪" to false, Bullets.bulletChar("§", "Wingdings"))
        assertEquals("✓" to false, Bullets.bulletChar("", "Wingdings"))
        assertEquals("•" to false, Bullets.bulletChar("·", "Symbol"))
        // 모르는 기호 글꼴 글자는 점으로. 보통 글꼴은 그대로.
        assertEquals("•" to false, Bullets.bulletChar("Z", "Wingdings 2"))
        assertEquals("–" to true, Bullets.bulletChar("–", "Arial"))
    }
}
