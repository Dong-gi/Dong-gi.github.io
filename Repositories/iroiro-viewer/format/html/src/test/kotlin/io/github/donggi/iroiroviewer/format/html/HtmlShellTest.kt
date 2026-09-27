package io.github.donggi.iroiroviewer.format.html

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HtmlShellTest {

    @Test
    fun 띄어_쓰지_않는_언어는_keep_all_을_되돌리고_책의_언어를_기본으로_단다() {
        // keep-all 은 한국어의 규칙이다 — 일본어·중국어에 걸면 문장 부호에서만 줄이 나뉜다(실세계 말뭉치 검토).
        val none = HtmlShell.wrap("<html><head></head><body>글</body></html>", lang = "ja")
        assertTrue(":lang(ja),:lang(zh){word-break:normal;}" in none, none)
        assertTrue("<html lang=\"ja\">" in none, none)
        // 장이 적은 언어가 이긴다(`lang`·`xml:lang` 어느 쪽이든).
        val ko = HtmlShell.wrap("<html lang=\"ko\"><head></head><body>글</body></html>", lang = "ja")
        assertTrue("<html lang=\"ko\">" in ko && "lang=\"ja\"" !in ko, ko)
        val xml = HtmlShell.wrap("<html xml:lang=\"zh\"><head></head><body>글</body></html>", lang = "ja")
        assertTrue("lang=\"ja\"" !in xml, xml)
        // 책이 적은 값이 속성 밖으로 새지 않는다.
        val bad = HtmlShell.wrap("<html><head></head><body>글</body></html>", lang = "ja\"><b>x</b>")
        assertTrue("<html>" in bad && "<b>" !in bad, bad)
        // 머리가 없는 조각도.
        val frag = HtmlShell.wrap("<p>조각</p>", lang = "zh-Hant")
        assertTrue("<html lang=\"zh-Hant\">" in frag, frag)
    }

    @Test
    fun 스타일이_head_끝에_들어간다() {
        val out = HtmlShell.wrap("<html><head><link rel='x'/></head><body>글</body></html>")
        val link = out.indexOf("<link")
        val style = out.indexOf("<style>")
        // **책의 스타일시트보다 뒤여야 한다.** 앞에 넣으면 우리 여백이 덮인다.
        assertTrue(link in 0 until style, out)
        assertTrue(style < out.indexOf("</head>"), out)
    }

    @Test
    fun head_만_있고_닫히지_않아도_넣는다() {
        val out = HtmlShell.wrap("<html><head><body>글")
        assertTrue("<style>" in out, out)
        assertTrue(out.indexOf("<style>") > out.indexOf("<head"), out)
    }

    @Test
    fun head_가_없으면_문서를_만든다() {
        val out = HtmlShell.wrap("<p>조각</p>")
        assertTrue("<html>" in out, out)
        assertTrue("viewport" in out, out)
        assertTrue("조각" in out, out)
    }

    @Test
    fun 접두어가_같은_태그에_속지_않는다() {
        // `</header>` 를 `</head` 로 보면 스타일이 본문 한가운데로 들어간다.
        val out = HtmlShell.wrap("<html><body><header>머리</header><p>글</p></body></html>")
        assertTrue(out.indexOf("<style>") < out.indexOf("<header>"), out)
    }

    @Test
    fun 제_head_를_가진_문서도_표준_모드로_나간다() {
        // 위생기가 DOCTYPE 을 지운 XHTML 장(EPUB)이 이 갈래로 온다. DOCTYPE 이 없으면 WebView 가 쿼크 모드로
        // 그려, 표가 본문의 글자 크기를 물려받지 않는다(말뭉치 검토가 크롬에서 실측).
        for (doc in listOf("\n<html><head><title>t</title></head><body>글</body></html>", "<html><head><body>글")) {
            val out = HtmlShell.wrap(doc)
            assertTrue(out.startsWith("<!DOCTYPE html>"), out)
            assertEquals(1, Regex("(?i)<!DOCTYPE").findAll(out).count(), out)
        }
        // 이미 있으면 하나만 남는다.
        val kept = HtmlShell.wrap("<!doctype html><html><head></head><body></body></html>")
        assertEquals(1, Regex("(?i)<!DOCTYPE").findAll(kept).count(), kept)
    }

    @Test
    fun 화면이_얹는_스타일이_우리_것_뒤에_온다() {
        val out = HtmlShell.wrap("<html><head></head><body></body></html>", "body{font-size:120%}")
        assertTrue(out.indexOf("font-size:120%") > out.indexOf("padding"), out)
    }

    @Test
    fun 날것의_글_안의_head_에_속지_않는다() {
        // `<title>` 안의 `</head>` 에 스타일을 끼우면 우리 CSS 가 제목 글자가 되고, `<xmp>` 안이면 화면에 찍힌다.
        val out = HtmlShell.wrap("<html><head><title>가</head>나</title><xmp></head></xmp></head><body>글</body></html>")
        val style = out.indexOf("<style>")
        assertTrue(style > out.indexOf("</xmp>"), out)
        assertTrue(style < out.indexOf("<body>"), out)
    }

    @Test
    fun 다른_속성의_값에_든_lang_에_속지_않고_빈_lang_은_바꾼다() {
        val inTitle = HtmlShell.wrap("<html title=\" lang=x\"><head></head><body>日本語</body></html>", lang = "ja")
        assertTrue("<html title=\" lang=x\" lang=\"ja\">" in inTitle, inTitle)
        val empty = HtmlShell.wrap("<html lang=\"\"><head></head><body>日本語</body></html>", lang = "ja")
        assertTrue("<html lang=\"ja\">" in empty, empty)
        assertEquals(1, Regex(" lang=").findAll(empty.substringBefore("<head>")).count(), empty)
    }
}
