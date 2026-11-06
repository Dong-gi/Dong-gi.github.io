package io.github.donggi.iroiroviewer.io

import io.github.donggi.iroiroviewer.model.PathNames
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **이 앱이 짓거나 받는 이름에서 글의 방향을 바꾸는 글자를 `_` 로**(2026-10-16 보안 점검). `invoice` + U+202E + `fdp.apk` 가 화면에서
 * 'invoicekpa.pdf' 로 보이는 이름을 이 앱이 디스크에 만들지 않는다 — 압축 풀기(조각마다 [PathRules.sanitize]), 다른 앱에서 받은 사본의 이름
 * ([IncomingFiles.nameFor]), 휴지통 되살리기가 그 관문을 지난다. 사용자가 직접 적는 이름([PathRules.validate])은 고쳐 주지 않는다 — 화면에서
 * 드러낸다(`PathNames.forDisplay`).
 *
 * 시험 글에 그 글자를 날것으로 적지 않는다 — `Char(코드)` 로 짓는다.
 */
class BidiNamesTest {

    private val controls = listOf(0x061C, 0x200E, 0x200F, 0x202A, 0x202B, 0x202C, 0x202D, 0x202E, 0x2066, 0x2067, 0x2068, 0x2069).map(::Char)

    @Test
    fun 짓는_이름에서는_밑줄로_바꾼다() {
        assertEquals("invoice_fdp.apk", PathRules.sanitize("invoice" + Char(0x202E) + "fdp.apk"))
        for (c in controls) {
            val name = PathRules.sanitize("a" + c + "b.txt")
            assertEquals("a_b.txt", name, "U+%04X".format(c.code))
        }
        // 확장자가 바뀌지 않고, 다른 글자(한글·아랍 글자·잇는 글자)는 그대로다.
        val keep = "보고서 " + Char(0x0627) + "👍🏽" + Char(0x200D) + ".pdf"
        assertEquals(keep, PathRules.sanitize(keep))
    }

    @Test
    fun 다른_앱에서_받은_사본의_이름도_바꾼다() {
        val name = IncomingFiles.nameFor("statement" + Char(0x202E) + "fdp.exe", "application/pdf") { null }
        assertTrue(name.none(PathNames::isBidiControl), name)
        assertEquals("statement_fdp.exe", name)
    }

    @Test
    fun 사용자가_적는_이름은_고치지_않는다() {
        // 고쳐 주지 않는다는 결정(validate 의 KDoc) — 대신 목록이 그 글자를 보이는 표시로 적는다.
        val typed = "메모" + Char(0x200F) + ".txt"
        assertEquals(PathRules.Verdict.Ok(typed), PathRules.validate(typed))
        assertEquals("메모" + PathNames.BIDI_MARK + ".txt", PathNames.forDisplay(typed))
    }
}
