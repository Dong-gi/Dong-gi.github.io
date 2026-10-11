package io.github.donggi.iroiroviewer.format.html

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * CSS 의 참조를 **CSS 문법대로** 찾는가. 글자로 찾던 판이 지나쳤던 것(이스케이프로 쓴 함수·규칙 이름, `image-set` 의 문자열)과
 * 잘못 보던 것(문자열 안의 `url(`, 괄호가 든 주소)을 박는다.
 */
class CssRefsTest {

    /** 주소를 `[주소]` 로 바꿔 적어 무엇을 주소로 읽었는지 본다. */
    private fun urls(css: String): String = CssRefs.rewrite(css) { CssRefs.Out.Url("[$it]") }!!

    @Test
    fun 주소를_문법대로_읽는다() {
        assertEquals("a{b:url(\"[x.png]\")}", urls("a{b:url(x.png)}"))
        assertEquals("a{b:url(\"[x y.png]\")}", urls("a{b:url( \"x y.png\" )}"))
        assertEquals("a{b:url(\"[q(1).png]\")}", urls("a{b:url('q(1).png')}"))
        assertEquals("a{b:url(\"[a\"b.png]\")}", urls("a{b:url(\"a\\\"b.png\")}"))
        assertEquals("a{b:url(\"[(2).png]\")}", urls("a{b:url(\\(2\\).png)}"))
        // 16진 이스케이프 — 뒤의 공백 하나는 이스케이프의 끝이다.
        assertEquals("a{b:url(\"[가.png]\")}", urls("a{b:URL(\\AC00 .png)}"))
        // 문자열·주석 안의 `url(` 은 주소가 아니다. 이름의 일부인 `xurl(` 도 아니다.
        assertEquals("a{content:\"url(x)\"}", urls("a{content:\"url(x)\"}"))
        assertEquals("a{content:'it\\'s url(x)'}", urls("a{content:'it\\'s url(x)'}"))
        assertEquals("a{}/* url(x) */", urls("a{}/* url(x) */"))
        assertEquals("x{b:xurl(y)}", urls("x{b:xurl(y)}"))
        // 한 줄짜리 문자열이 줄바꿈으로 깨져도 뒤의 주소는 읽는다.
        assertEquals("a{content:\"x\n;b:url(\"[y]\")}", urls("a{content:\"x\n;b:url(y)}"))
    }

    /** 우회 — 이름의 이스케이프를 풀면 `url(` 이다. */
    @Test
    fun 이스케이프로_쓴_함수_이름도_주소다() {
        assertEquals("a{b:url(\"[https://x/y.png]\")}", urls("a{b:\\75 rl(https://x/y.png)}"))
        assertEquals("a{b:url(\"[https://x/y.png]\")}", urls("a{b:u\\72l(https://x/y.png)}"))
        assertEquals("a{b:url(\"[z.png]\")}", urls("a{b:\\55RL(z.png)}"))
        assertEquals("a{b:url(\"[s.png]\")}", urls("a{b:src(\"s.png\")}"))
        // 이스케이프된 다른 이름은 그대로다(바꾸지 않은 글은 원문 그대로 옮긴다).
        assertEquals("a{\\63olor:red}", urls("a{\\63olor:red}"))
    }

    @Test
    fun image_set_의_문자열은_주소다() {
        assertEquals(
            "a{b:image-set(url(\"[a.png]\") 1x, url(\"[b.png]\") 2x)}",
            urls("a{b:image-set(\"a.png\" 1x, url(b.png) 2x)}"),
        )
        assertEquals(
            "a{b:-webkit-image-set(url(\"[a.png]\") 1x)}",
            urls("a{b:-webkit-image-set('a.png' 1x)}"),
        )
        // `type()` 안의 문자열은 형식이지 주소가 아니다. image-set 이 닫힌 뒤의 문자열도 아니다.
        assertEquals(
            "a{b:image-set(url(\"[a.webp]\") type(\"image/webp\"));content:\"c\"}",
            urls("a{b:image-set(\"a.webp\" type(\"image/webp\"));content:\"c\"}"),
        )
        assertEquals("a{b:\\69mage-set(url(\"[e.png]\"))}", urls("a{b:\\69mage-set(\"e.png\")}"))
    }

    @Test
    fun import_를_이스케이프와_함께_지운다() {
        val seen = ArrayList<String?>()
        fun strip(css: String) = CssRefs.rewrite(css, onImport = { seen.add(it) }) { CssRefs.Out.Keep }!!
        assertEquals("b{c:d}", strip("@import url(\"a.css\");b{c:d}"))
        assertEquals("b{c:d}", strip("@\\69mport \"b.css\";b{c:d}"))
        assertEquals("b{c:d}", strip("@IMPORT 'c.css' screen;b{c:d}"))
        assertEquals("b{c:d}", strip("@im\\port url(d.css) print{}b{c:d}"))
        // 주소 안의 `;` 에서 규칙이 끝나지 않는다.
        assertEquals("b{c:d}", strip("@import \"e;f.css\";b{c:d}"))
        assertEquals<List<String?>>(listOf("a.css", "b.css", "c.css", "d.css", "e;f.css"), seen)
        // `@import` 가 아닌 규칙은 그대로다.
        assertEquals("@media print{a{b:c}}", strip("@media print{a{b:c}}"))
        assertEquals("@importx{}", strip("@importx{}"))
    }

    @Test
    fun 망가진_주소는_none_으로_바꾸고_그_뒤를_잇는다() {
        assertEquals("a{b:none}c{d:url(\"[e]\")}", urls("a{b:url(x y)}c{d:url(e)}"))
        assertEquals("a{b:none}c{d:url(\"[e]\")}", urls("a{b:url(x\"y)}c{d:url(e)}"))
        assertEquals("a{b:none}", urls("a{b:url(\"x\ny\")}"))
        assertEquals("a{b:none}", urls("a{b:src(x)}"))
        // 닫히지 않은 주소 — 명세는 그 주소를 받는다.
        assertEquals("a{b:url(\"[x.png]\")", urls("a{b:url(x.png"))
    }

    @Test
    fun 바꿔_적는_세_갈래() {
        val out = CssRefs.rewrite("a{b:url(#g)}c{d:url(x)}e{f:url( y )}") {
            when (it) {
                "#g" -> CssRefs.Out.Keep
                "x" -> CssRefs.Out.None
                else -> CssRefs.Out.Url("/r/0")
            }
        }
        assertEquals("a{b:url(#g)}c{d:none}e{f:url(\"/r/0\")}", out)
    }

    @Test
    fun 상한을_넘으면_쓰다가_멈춘다() {
        assertNull(CssRefs.rewrite("a{b:url(x)}".repeat(100), limit = 500) { CssRefs.Out.Url("/r/123456") })
        assertEquals("a{}", CssRefs.rewrite("a{}", limit = 3) { CssRefs.Out.Keep })
    }

    @Test
    fun 참조가_있는지만_묻는다() {
        assertTrue(CssRefs.hasRefs("url(#a)"))
        assertTrue(CssRefs.hasRefs("\\75 rl(x)"))
        assertTrue(CssRefs.hasRefs("image-set(\"a.png\" 1x)"))
        assertTrue(CssRefs.hasRefs("@import 'a'"))
        assertFalse(CssRefs.hasRefs("rgb(1,2,3)"))
        assertFalse(CssRefs.hasRefs("0;1;0"))
        assertFalse(CssRefs.hasRefs("\"url(x)\""))
    }

    /**
     * 되풀이되는 것마다 선형인가. 크기는 **제곱인 판이 확실히 넘는 크기**다 — 예전 `@import` 지우기는 규칙마다 `;`·`{` 를 각각
     * 끝까지 찾아 1.5 M 자에서 수십 초였다(0.8 M 자 1초, 8 MiB 135초).
     */
    @Test
    fun 되풀이되는_것이_제곱_시간을_만들지_않는다() {
        val size = 1_500_000
        val inputs = listOf(
            "@import;".repeat(size / 8),
            "@import{}".repeat(size / 9),
            "@import \"".repeat(size / 9),
            "url(".repeat(size / 4),
            "a{b:url(\"".repeat(size / 9),
            "a{b:url(x y ".repeat(size / 12),
            "image-set(".repeat(size / 10) + "\"a\"",
            "@\\69mport ".repeat(size / 11),
            "\\75 rl(".repeat(size / 7),
            "'\\".repeat(size / 2),
            "/*".repeat(size / 2),
        )
        for (css in inputs) {
            val t0 = System.nanoTime()
            CssRefs.rewrite(css) { CssRefs.Out.None }
            val ms = (System.nanoTime() - t0) / 1_000_000
            assertTrue(ms < 3_000, "${css.take(12)}…: ${ms}ms")
        }
    }
}
