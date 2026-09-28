package io.github.donggi.iroiroviewer.docview

import io.github.donggi.iroiroviewer.data.ReaderAppearance
import io.github.donggi.iroiroviewer.format.FlowKind
import io.github.donggi.iroiroviewer.format.epub.ReaderStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 읽는 모양을 문서의 종류마다 어디까지 거는가(`ReaderLooks` 의 표). 틀리면 슬라이드의 글자가 상자를 넘거나 시트의 채움 색이
 * 지워진다 — 화면에서는 '조금 이상하다' 로만 보인다.
 */
class ReaderLooksTest {

    private val dark = ReaderAppearance(fontPercent = 150, margin = ReaderAppearance.MARGIN_WIDE, theme = ReaderAppearance.THEME_DARK)

    @Test
    fun 시스템_바탕은_기기의_밤_모드를_따른다() {
        assertEquals(ReaderStyle.Tone.DARK, ReaderLooks.tone(ReaderAppearance.THEME_SYSTEM, systemDark = true))
        assertEquals(ReaderStyle.Tone.LIGHT, ReaderLooks.tone(ReaderAppearance.THEME_SYSTEM, systemDark = false))
        // 고른 바탕은 기기와 무관하다.
        assertEquals(ReaderStyle.Tone.SEPIA, ReaderLooks.tone(ReaderAppearance.THEME_SEPIA, systemDark = true))
        assertEquals(ReaderStyle.Tone.LIGHT, ReaderLooks.tone(ReaderAppearance.THEME_LIGHT, systemDark = true))
        // 모르는 값(다음 판이 적은 값)은 시스템으로.
        assertEquals(ReaderStyle.Tone.DARK, ReaderLooks.tone(99, systemDark = true))
    }

    @Test
    fun 글자_크기는_한_칸씩_범위_안에서() {
        assertEquals(110, ReaderLooks.larger(ReaderAppearance(fontPercent = 100)).fontPercent)
        assertEquals(90, ReaderLooks.smaller(ReaderAppearance(fontPercent = 100)).fontPercent)
        assertEquals(ReaderAppearance.MAX_FONT_PERCENT, ReaderLooks.larger(ReaderAppearance(fontPercent = 200)).fontPercent)
        assertEquals(ReaderAppearance.MIN_FONT_PERCENT, ReaderLooks.smaller(ReaderAppearance(fontPercent = 70)).fontPercent)
        // 칸에 맞지 않는 값은 칸으로 맞춘 뒤 옮긴다(105 → 110, 105 → 90).
        assertEquals(110, ReaderLooks.larger(ReaderAppearance(fontPercent = 105)).fontPercent)
        assertEquals(90, ReaderLooks.smaller(ReaderAppearance(fontPercent = 105)).fontPercent)
        // 손으로 고친 값이 WebView 에 닿지 않게 한 번 더 누른다.
        assertEquals(200, ReaderLooks.textZoom(5000))
    }

    @Test
    fun 글에는_셋_다_시트에는_글자_크기만_슬라이드에는_아무것도() {
        val doc = ReaderLooks.forFlow(FlowKind.DOCUMENT, dark, systemDark = false)
        assertEquals(150, doc.textZoom)
        assertTrue("#121212" in doc.css && "padding-left:30pt" in doc.css, doc.css)
        assertEquals(ReaderStyle.background(ReaderStyle.Tone.DARK), doc.background)

        val sheet = ReaderLooks.forFlow(FlowKind.SHEETS, dark, systemDark = false)
        assertEquals(150, sheet.textZoom)
        // 칸의 채움 색은 값의 뜻이다 — 어두운 바탕으로 덮지 않는다.
        assertEquals("", sheet.css)
        assertEquals(0xFFFFFFFF.toInt(), sheet.background)

        val slides = ReaderLooks.forFlow(FlowKind.SLIDES, dark, systemDark = true)
        // 슬라이드는 화면 단위로 길이를 적었다 — 글자만 키우면 상자를 넘는다.
        assertEquals(100, slides.textZoom)
        assertEquals("", slides.css)

        assertEquals(emptySet(), ReaderLooks.controlsForFlow(FlowKind.SLIDES))
        assertEquals(setOf(ReaderLooks.Control.FONT), ReaderLooks.controlsForFlow(FlowKind.SHEETS))
    }

    @Test
    fun 고정_레이아웃_쪽에는_아무것도_걸지_않고_화면_크기로_맞춘다() {
        val fixed = ReaderLooks.forEpub(fixed = true, a = dark, systemDark = true, viewWidth = 411.0, viewHeight = 683.0)
        assertEquals(100, fixed.textZoom)
        assertEquals(ReaderStyle.FIXED_BACKDROP, fixed.background)
        assertEquals(ReaderStyle.Tone.LIGHT, fixed.style.tone)
        assertEquals(411.0, fixed.style.viewWidth)
        // 회전하면(크기가 바뀌면) 쪽을 다시 맞춘다.
        val rotated = ReaderLooks.forEpub(fixed = true, a = dark, systemDark = true, viewWidth = 683.0, viewHeight = 411.0)
        assertNotEquals(fixed.contentKey, rotated.contentKey)

        val flow = ReaderLooks.forEpub(fixed = false, a = dark, systemDark = false, viewWidth = 411.0, viewHeight = 683.0)
        assertEquals(150, flow.textZoom)
        assertEquals(ReaderStyle.Tone.DARK, flow.style.tone)
        assertEquals(ReaderStyle.Margin.WIDE, flow.style.margin)

        assertTrue(ReaderLooks.controlsForEpub(allFixed = true).isEmpty())
        assertEquals(3, ReaderLooks.controlsForEpub(allFixed = false).size)
    }

    @Test
    fun 글자_크기만_바꾸면_쪽을_다시_읽지_않는다() {
        // 다시 읽는 열쇠(`contentKey`)에 글자 크기가 들면 −/+ 를 누를 때마다 쪽을 다시 읽어 깜빡인다. `textZoom` 은 그 자리에서 건다.
        val a = ReaderLooks.forFlow(FlowKind.DOCUMENT, dark, systemDark = false)
        val b = ReaderLooks.forFlow(FlowKind.DOCUMENT, dark.copy(fontPercent = 160), systemDark = false)
        assertEquals(a.contentKey, b.contentKey)
        assertNotEquals(a.textZoom, b.textZoom)
        val e1 = ReaderLooks.forEpub(false, dark, false, 411.0, 683.0)
        val e2 = ReaderLooks.forEpub(false, dark.copy(fontPercent = 160), false, 411.0, 683.0)
        assertEquals(e1.contentKey, e2.contentKey)
        // 흐름 장은 화면 크기가 바뀌어도 다시 읽지 않는다(글이 스스로 흐른다).
        val e3 = ReaderLooks.forEpub(false, dark, false, 683.0, 411.0)
        assertEquals(e1.contentKey, e3.contentKey)
        // 바탕을 바꾸면 다시 읽는다.
        val e4 = ReaderLooks.forEpub(false, dark.copy(theme = ReaderAppearance.THEME_SEPIA), false, 411.0, 683.0)
        assertFalse(e1.contentKey == e4.contentKey)
    }
}
