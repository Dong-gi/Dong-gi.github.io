package io.github.donggi.iroiroviewer.format.xlsx

import io.github.donggi.iroiroviewer.safety.ParseLimits
import io.github.donggi.iroiroviewer.safety.SafeXml
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 색 — 명도 조정(tint), 테마 번호의 교환, 옛 팔레트.
 *
 * 명도 조정의 기대값은 **Excel 색 선택기가 보여 주는 값**이다(흰색 배경1 −15% = D9D9D9 따위,
 * 오피스 사용자라면 한 번쯤 본 값들). 우리 식을 돌려서 적은 것이 아니다.
 */
class XlsxColorsTest {

    private fun hex(rgb: Int) = XlsxColors.hex(rgb)

    @Test
    fun 명도_조정은_Excel_색_선택기와_맞는다() {
        assertEquals("#d9d9d9", hex(XlsxColors.applyTint(0xFFFFFF, -0.1499984740745262)))
        assertEquals("#bfbfbf", hex(XlsxColors.applyTint(0xFFFFFF, -0.249977111117893)))
        assertEquals("#808080", hex(XlsxColors.applyTint(0xFFFFFF, -0.499984740745262)))
        assertEquals("#7f7f7f", hex(XlsxColors.applyTint(0x000000, 0.499984740745262)))
        assertEquals("#595959", hex(XlsxColors.applyTint(0x000000, 0.34998626667073579)))
        // Office 2007 테마의 강조1(4F81BD) '40% 더 밝게'.
        assertEquals("#95b3d7", hex(XlsxColors.applyTint(0x4F81BD, 0.3999755851924192)))
        assertEquals("#4f81bd", hex(XlsxColors.applyTint(0x4F81BD, 0.0)))
    }

    @Test
    fun 테마_번호는_앞의_두_쌍을_바꿔_읽는다() {
        // 테마 파일의 순서(dk1, lt1, dk2, lt2 …)를 일부러 알아보기 쉬운 색으로 채운다.
        val xml = """<?xml version="1.0"?>
            <a:theme xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><a:themeElements>
            <a:clrScheme name="t">
              <a:dk1><a:sysClr val="windowText" lastClr="111111"/></a:dk1>
              <a:lt1><a:sysClr val="window" lastClr="EEEEEE"/></a:lt1>
              <a:dk2><a:srgbClr val="222222"/></a:dk2>
              <a:lt2><a:srgbClr val="DDDDDD"/></a:lt2>
              <a:accent1><a:srgbClr val="AA0001"/></a:accent1>
              <a:accent6><a:srgbClr val="AA0006"/></a:accent6>
              <a:hlink><a:srgbClr val="0000EE"/></a:hlink>
            </a:clrScheme></a:themeElements></a:theme>"""
        val p = SafeXml.newParser(xml.byteInputStream(), null, ParseLimits.DEFAULT)
        val theme = XlsxStyles.readTheme(p, ParseLimits.DEFAULT)
        assertEquals(0xEEEEEE, theme[0]) // lt1
        assertEquals(0x111111, theme[1]) // dk1 — 기본 글꼴 `theme="1"` 이 이 색이다
        assertEquals(0xDDDDDD, theme[2]) // lt2
        assertEquals(0x222222, theme[3]) // dk2
        assertEquals(0xAA0001, theme[4])
        assertEquals(0xAA0006, theme[9])
        assertEquals(0x0000EE, theme[10])
        // 적히지 않은 칸은 Office 기본값.
        assertEquals(XlsxColors.DEFAULT_THEME[11], theme[11])
    }

    @Test
    fun 색_하나를_푼다() {
        val theme = XlsxColors.DEFAULT_THEME
        val pal = XlsxColors.DEFAULT_PALETTE
        assertEquals("#ff0000", XlsxColors.resolve(ColorSpec(rgb = "FFFF0000"), theme, pal))
        assertEquals("#000000", XlsxColors.resolve(ColorSpec(theme = 1), theme, pal))
        assertEquals("#ffffff", XlsxColors.resolve(ColorSpec(theme = 0), theme, pal))
        assertEquals("#95b3d7", XlsxColors.resolve(ColorSpec(theme = 4, tint = 0.3999755851924192), theme, pal))
        assertNull(XlsxColors.resolve(ColorSpec(auto = true, rgb = "FFFF0000"), theme, pal))
        assertNull(XlsxColors.resolve(ColorSpec(theme = 99), theme, pal))
        // 64·65 는 시스템 전경·배경 — '자동' 이다.
        assertNull(XlsxColors.resolve(ColorSpec(indexed = 64), theme, pal))
        // 색 코드에 CSS 를 끼워 넣으려는 값은 버린다.
        assertNull(XlsxColors.resolve(ColorSpec(rgb = "red;x"), theme, pal))
    }

    @Test
    fun 옛_팔레트는_openpyxl_과_같다() {
        // openpyxl 3.1.5 `COLOR_INDEX` 에서 몇 칸을 골랐다.
        val pal = XlsxColors.DEFAULT_PALETTE
        assertEquals(64, pal.size)
        assertEquals(0xFF0000, pal[10])
        assertEquals(0xC0C0C0, pal[22])
        assertEquals(0x9999FF, pal[24])
        assertEquals(0x969696, pal[55])
        assertEquals(0x333333, pal[63])
    }
}
