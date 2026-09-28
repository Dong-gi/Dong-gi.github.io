package io.github.donggi.iroiroviewer.format.epub

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 읽는 모양(여백·바탕·고정 쪽 맞춤)을 CSS 로 옮기는 규칙.
 *
 * 화면에서 보면 '조금 좁다·조금 넘친다' 로만 보이는 값들이라 숫자로 박는다 — 특히 **고정 쪽이 화면을 1 화소라도 넘으면
 * 스크롤 막대가 생기고 쪽이 흔들린다.** 배율을 올림하면 그렇게 된다.
 */
class ReaderStyleTest {

    @Test
    fun 맞춤_배율은_작은_쪽에_맞추고_내린다() {
        // 1200×1600 쪽을 411×683 화면에: 폭 0.3425 · 높이 0.42687… → 폭이 이긴다.
        assertEquals(0.3425, ReaderStyle.fitScale(1200, 1600, 411.0, 683.0), 1e-9)
        // 가로 화면이면 높이가 이긴다. 0.25666… 을 올리지 않는다(0.2566).
        assertEquals(0.2566, ReaderStyle.fitScale(1200, 1600, 800.0, 410.66), 1e-9)
        // 모르는 크기는 1 — 줄이지 않는다.
        assertEquals(1.0, ReaderStyle.fitScale(1200, 1600, 0.0, 683.0))
        assertEquals(1.0, ReaderStyle.fitScale(0, 1600, 411.0, 683.0))
    }

    @Test
    fun 고정_쪽은_화폭을_못_박고_배율을_그대로_적는다() {
        val css = ReaderStyle.fixedPage(FixedLayout.Viewport(1200, 1600), 411.0, 683.0)
        // 1200px = 900pt, 1600px = 1200pt. `CssValues` 가 px 을 내지 않아 pt 로 옮긴다.
        assertTrue("width:900pt" in css, css)
        assertTrue("height:1200pt" in css, css)
        // 배율은 **백분율로** 넷째 자리까지 — `number` 로 적으면 0.3425 가 0.34 로, 0.3499 는 0.35 로 올라간다.
        assertTrue("zoom:34.25%" in css, css)
        assertTrue("position:relative" in css, css)
        // 세로 가운데: (683 / 0.3425 − 1600) / 2 = 197.08…px → 147.81pt. 가로는 폭에 맞췄으니 0.
        assertTrue("margin-left:0pt" in css, css)
        assertTrue(Regex("margin-top:147\\.8\\d*pt").containsMatchIn(css), css)
        // 껍데기의 읽기용 여백을 되돌린다. `!important` 없이 — 책이 클래스로 준 여백은 살린다.
        assertTrue("body{margin:0;padding:0}" in css, css)
        assertFalse("!important" in css, css)
    }

    @Test
    fun 고정_쪽을_맞춘_결과가_화면을_넘지_않는다() {
        // 여러 화면·화폭에서 (여백 + 화폭 × 배율) ≤ 화면 을 확인한다. 넘으면 스크롤이 생긴다.
        val views = listOf(411.0 to 683.0, 411.43 to 700.19, 800.0 to 410.66, 1280.0 to 752.0, 360.0 to 560.0)
        val pages = listOf(1200 to 1600, 1536 to 2048, 768 to 1024, 2048 to 1536, 595 to 842)
        for ((vw, vh) in views) for ((pw, ph) in pages) {
            val s = ReaderStyle.fitScale(pw, ph, vw, vh)
            val left = ((vw / s - pw) / 2.0).coerceAtLeast(0.0)
            val top = ((vh / s - ph) / 2.0).coerceAtLeast(0.0)
            assertTrue((left + pw) * s <= vw + 1e-9, "폭이 넘친다: $pw×$ph in $vw×$vh")
            assertTrue((top + ph) * s <= vh + 1e-9, "높이가 넘친다: $pw×$ph in $vw×$vh")
        }
    }

    @Test
    fun 화폭을_모르면_그림만_화면에_맞춘다() {
        val css = ReaderStyle.fixedPage(null, 411.0, 683.0)
        assertTrue("max-height:100vh" in css, css)
        assertFalse("zoom" in css, css)
    }

    @Test
    fun 여백은_body_의_안쪽_여백이다() {
        val narrow = ReaderStyle.flow(ReaderStyle.Margin.NARROW, ReaderStyle.Tone.LIGHT)
        val wide = ReaderStyle.flow(ReaderStyle.Margin.WIDE, ReaderStyle.Tone.LIGHT)
        assertTrue("padding-left:6pt !important" in narrow, narrow)
        assertTrue("padding-right:30pt !important" in wide, wide)
        // 책이 준 바깥 여백이 우리 값에 더해지지 않게.
        assertTrue("margin-left:0 !important" in wide, wide)
        // 밝은 바탕은 문서의 색을 그대로 둔다.
        assertFalse("color" in narrow, narrow)
    }

    @Test
    fun 어두운_바탕은_글자색을_덮고_그림은_건드리지_않는다() {
        val css = ReaderStyle.flow(ReaderStyle.Margin.NORMAL, ReaderStyle.Tone.DARK)
        assertTrue("html,body{background-color:#121212 !important}" in css, css)
        assertTrue("color:#e0e0e0 !important" in css, css)
        // 그림 뒤에만 흰 바탕 — 투명한 선화가 묻히지 않게. 뒤집기 필터는 쓰지 않는다(그림의 색이 바뀐다).
        assertTrue("img,svg,video,canvas{background-color:#ffffff !important}" in css, css)
        assertFalse("filter" in css, css)
        assertFalse("invert" in css, css)
    }

    @Test
    fun 세피아는_종이만_바꾸고_글자색은_덮지_않는다() {
        val css = ReaderStyle.flow(ReaderStyle.Margin.NORMAL, ReaderStyle.Tone.SEPIA)
        assertTrue("background-color:#f4ecd8 !important" in css, css)
        // 글자색은 특이도 0 으로만 — 문서가 적은 색이 이긴다.
        assertTrue(":where(body){color:#3b2f22}" in css, css)
        assertFalse("color:#3b2f22 !important" in css, css)
    }

    @Test
    fun 흐름_문서에_얹으면_머리_끝에_들어간다() {
        val doc = "<!DOCTYPE html><html><head><style>p{color:red}</style></head><body><p>글</p></body></html>"
        val css = ReaderStyle.flow(ReaderStyle.Margin.WIDE, ReaderStyle.Tone.DARK)
        val out = ReaderStyle.apply(doc, css)
        val ours = out.indexOf("padding-left:30pt")
        assertTrue(ours > out.indexOf("p{color:red}"), out)
        assertTrue(ours < out.indexOf("</head>"), out)
        // 얹을 것이 없으면 그대로.
        assertEquals(doc, ReaderStyle.apply(doc, ""))
    }
}
