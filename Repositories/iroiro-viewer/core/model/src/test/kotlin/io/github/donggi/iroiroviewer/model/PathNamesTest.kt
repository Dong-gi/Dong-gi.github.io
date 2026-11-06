package io.github.donggi.iroiroviewer.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PathNamesTest {

    @Test
    fun `마지막 조각만 준다`() {
        assertEquals("보고서 (최종).hwpx", PathNames.lastSegment("/storage/emulated/0/Documents/보고서 (최종).hwpx"))
        assertEquals("a.pdf", PathNames.lastSegment("a.pdf"))
        assertEquals("", PathNames.lastSegment(""))
    }

    @Test
    fun `끝의 빗금은 떼고 자른다`() {
        // 폴더로 연 만화의 경로가 빗금으로 끝날 수 있다(`File` 의 정규화와 같은 답).
        assertEquals("만화", PathNames.lastSegment("/storage/emulated/0/만화/"))
        assertEquals("", PathNames.lastSegment("/"))
    }

    /** 방향을 바꾸는 보이지 않는 글자 열둘 — 그리고 그것이 아닌 비슷한 글자(잇는 글자·아랍 글자)는 건드리지 않는다. */
    @Test
    fun `방향 제어 글자를 가린다`() {
        val controls = listOf(0x061C, 0x200E, 0x200F, 0x202A, 0x202B, 0x202C, 0x202D, 0x202E, 0x2066, 0x2067, 0x2068, 0x2069)
        for (code in controls) assertTrue(PathNames.isBidiControl(Char(code)), "U+%04X".format(code))
        // 잇는 글자(ZWJ·ZWNJ — 이모지와 인도 글자에 쓴다)·아랍 글자·한글·빈칸은 아니다.
        for (code in listOf(0x200C, 0x200D, 0x2029, 0x0627, 0xAC00, 0x0020, 0x202F, 0x2065, 0x206A)) {
            assertFalse(PathNames.isBidiControl(Char(code)), "U+%04X".format(code))
        }
    }

    /** 화면에 보일 이름 — `invoice` + U+202E + `fdp.apk` 가 'invoicekpa.pdf' 로 보이지 않게 그 글자를 보이는 표시로 바꾼다. 실제 이름은 그대로다. */
    @Test
    fun `화면에는 방향 제어 글자를 보이는 표시로 적는다`() {
        val spoofed = "invoice" + Char(0x202E) + "fdp.apk"
        val shown = PathNames.forDisplay(spoofed)
        assertEquals("invoice" + PathNames.BIDI_MARK + "fdp.apk", shown)
        assertTrue(shown.endsWith(".apk"), "진짜 확장자가 끝에 보인다")
        assertTrue(shown.none(PathNames::isBidiControl))
        // 여럿이어도, 격리(U+2066~2069)여도.
        assertEquals("a" + PathNames.BIDI_MARK + "b" + PathNames.BIDI_MARK + "c", PathNames.forDisplay("a" + Char(0x2067) + "b" + Char(0x2069) + "c"))
        // 그 글자가 없으면 같은 문자열 그대로(아랍 글자·이모지·잇는 글자도).
        val plain = "보고서 (최종) " + Char(0x0627) + "👍🏽" + Char(0x200D) + ".pdf"
        assertSame(plain, PathNames.forDisplay(plain))
    }

    @Test
    fun `역슬래시는 이름의 글자다`() {
        // 안드로이드에서는 구분자가 아니다 — 윈도의 JVM 이 읽는 대로 자르면 기기와 다른 답을 시험한다.
        assertEquals("a\\b.pdf", PathNames.lastSegment("/storage/emulated/0/a\\b.pdf"))
    }
}
