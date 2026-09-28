package io.github.donggi.iroiroviewer.format.epub

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 고정 레이아웃 쪽의 **화폭을 어디서 읽는가**. 틀리면 쪽이 화면보다 커져 스크롤이 생기거나(작게 읽음) 반쯤 빈 화면이 된다
 * (크게 읽음) — 기기에서 보면 '조금 이상하다' 로만 보여 원인을 찾기 어렵다. 그래서 글자로 박는다.
 */
class FixedLayoutTest {

    @Test
    fun 뷰포트_글의_여러_모양을_읽는다() {
        assertEquals(FixedLayout.Viewport(1200, 1600), FixedLayout.parseViewport("width=1200, height=1600"))
        assertEquals(FixedLayout.Viewport(1200, 1600), FixedLayout.parseViewport("width=1200;height=1600"))
        assertEquals(FixedLayout.Viewport(1200, 1600), FixedLayout.parseViewport(" HEIGHT = 1600 , Width = 1200px "))
        // 소수는 반올림한다(짝수 반올림이 아니다 — `floor(x + 0.5)`).
        assertEquals(FixedLayout.Viewport(1201, 1600), FixedLayout.parseViewport("width=1200.5, height=1600"))
    }

    @Test
    fun 고정_화폭이_아닌_뷰포트는_읽지_않는다() {
        // 흐름 쪽이 흔히 적는 값이다. 이것을 화폭으로 읽으면 흐름 쪽이 줄어든다.
        assertNull(FixedLayout.parseViewport("width=device-width, initial-scale=1"))
        assertNull(FixedLayout.parseViewport("width=1200"))
        assertNull(FixedLayout.parseViewport("width=0, height=1600"))
        assertNull(FixedLayout.parseViewport("width=-5, height=1600"))
        assertNull(FixedLayout.parseViewport("width=99999999, height=1600"))
        assertNull(FixedLayout.parseViewport("width=NaN, height=1600"))
        assertNull(FixedLayout.parseViewport(""))
        assertNull(FixedLayout.parseViewport(null))
    }

    @Test
    fun 머리의_뷰포트를_찾고_주석_안의_것은_건너뛴다() {
        val html = """<?xml version="1.0"?><html><head><title>1</title>
            |<!-- <meta name="viewport" content="width=10, height=10"/> -->
            |<meta charset="utf-8"/>
            |<meta data-name="viewport" content="width=20, height=20"/>
            |<meta content="width=1200, height=1600" name="viewport"/>
            |</head><body><p>글</p></body></html>
        """.trimMargin()
        assertEquals(FixedLayout.Viewport(1200, 1600), FixedLayout.fromHtml(html))
    }

    @Test
    fun 본문_안의_뷰포트는_화폭이_아니다() {
        val html = """<html><head><title>1</title></head>
            |<body><meta name="viewport" content="width=1200, height=1600"/></body></html>
        """.trimMargin()
        assertNull(FixedLayout.fromHtml(html))
    }

    @Test
    fun SVG_쪽은_뿌리의_크기나_viewBox_를_쓴다() {
        assertEquals(
            FixedLayout.Viewport(600, 800),
            FixedLayout.fromSvg("""<?xml version="1.0"?><svg xmlns="http://www.w3.org/2000/svg" width="600" height="800px"/>"""),
        )
        // 백분율은 화폭이 아니다 — viewBox 로 간다.
        assertEquals(
            FixedLayout.Viewport(1536, 2048),
            FixedLayout.fromSvg("""<!-- 주석 --><svg width="100%" height="100%" viewBox="0 0 1536 2048"></svg>"""),
        )
        assertEquals(
            FixedLayout.Viewport(300, 400),
            FixedLayout.fromSvg("""<svg:svg xmlns:svg="http://www.w3.org/2000/svg" viewBox="0,0,300,400"/>"""),
        )
        // `<svgx>` 는 다른 요소다.
        assertNull(FixedLayout.fromSvg("""<svgx width="1" height="1"/>"""))
    }
}
