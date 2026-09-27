package io.github.donggi.iroiroviewer.format.html

import io.github.donggi.iroiroviewer.format.UnsupportedFeatures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * CSS 에서 지우는 것은 셋뿐이다 — `@import`, 바깥 `url(…)`, 옛 스크립트 문법.
 *
 * **글꼴·여백·색을 건드리지 않는 것도 이 시험의 대상이다.** 안전하게 만든다고 조판을
 * 지우면 읽는 사람이 보는 것은 '지켜진 문서' 가 아니라 **망가진 문서**다.
 */
class CssTest {

    // 스킴 판정은 위생기 쪽(`Urls`)의 일이다. 리졸버는 상대경로만 답한다.
    private val local = HtmlSanitizer.Resolver { url -> "iro://book/$url" }

    private fun clean(css: String): Pair<String, UnsupportedFeatures> {
        val dropped = UnsupportedFeatures()
        return Css.sanitize(css, local, dropped) to dropped
    }

    @Test
    fun 조판은_그대로_남는다() {
        val (out, dropped) = clean(
            "body{font-family:'Noto Serif';margin:2em;color:#333;line-height:1.7}" +
                "@media screen and (max-width:600px){body{margin:1em}}"
        )
        assertTrue("Noto Serif" in out, out)
        assertTrue("line-height:1.7" in out, out)
        assertTrue("@media" in out, out)
        assertTrue(dropped.isEmpty, dropped.snapshot().toString())
    }

    @Test
    fun import_는_규칙째_사라진다() {
        val (out, dropped) = clean("@import url('other.css');body{color:red}")
        assertFalse("@import" in out, out)
        assertTrue("color:red" in out, out)
        assertEquals(1, dropped.snapshot()[UnsupportedFeatures.REMOTE_REFERENCE])
    }

    @Test
    fun 블록형_import_도_사라진다() {
        val (out, _) = clean("@import url('x.css') screen {}\nbody{color:red}")
        assertFalse("@import" in out, out)
        assertTrue("color:red" in out, out)
    }

    @Test
    fun 바깥_url_은_none_이_된다() {
        val (out, dropped) = clean("body{background:url(https://evil/p.gif) no-repeat}")
        assertFalse("evil" in out, out)
        assertTrue("none" in out, out)
        // 선언을 통째로 지우지 않는다 — 중괄호 균형이 깨지면 뒤의 규칙이 함께 무너진다.
        assertTrue(out.trim().endsWith("}"), out)
        assertEquals(1, dropped.snapshot()[UnsupportedFeatures.REMOTE_REFERENCE])
    }

    @Test
    fun 문서_안_url_은_주소만_바뀐다() {
        val (out, _) = clean("""@font-face{src:url("fonts/a.otf") format("opentype")}""")
        assertTrue("""url("iro://book/fonts/a.otf")""" in out, out)
        assertTrue("opentype" in out, out)
    }

    @Test
    fun 우리가_만든_url_을_빠져나갈_수_없다() {
        // 원본의 따옴표·괄호가 결과에 남으면 그 자리에서 값이 끝나고 뒤가 CSS 가 된다.
        val (out, _) = clean("""body{background:url(a.png"); color:red}""")
        val values = Regex("""url\("([^"]*)"\)""").findAll(out).map { it.groupValues[1] }.toList()
        assertTrue(values.isNotEmpty(), out)
        for (v in values) {
            assertFalse('"' in v || '(' in v || ')' in v || '\'' in v, "값에 괄호·따옴표가 남았다: $v")
        }
    }

    @Test
    fun 옛_스크립트_문법을_막는다() {
        for (poison in listOf("width:expression(alert(1))", "behavior:url(x.htc)", "-moz-binding:url(x)")) {
            val (out, dropped) = clean("body{$poison}")
            assertFalse("expression(" in out, out)
            assertFalse("behavior:" in out, out)
            assertFalse("-moz-binding" in out, out)
            assertEquals(1, dropped.snapshot()[UnsupportedFeatures.SCRIPT], out)
        }
    }

    @Test
    fun 주석_안에_숨겨도_소용없다() {
        val (out, _) = clean("body{/* url(https://evil/x) */ color:red}")
        assertFalse("evil" in out, out)
        assertTrue("color:red" in out, out)
    }

    @Test
    fun 닫히지_않은_것에서_멈추지_않는다() {
        clean("body{background:url(")
        clean("@import url('x'")
        clean("/* 끝나지 않는 주석")
        clean("body{")
    }
}
