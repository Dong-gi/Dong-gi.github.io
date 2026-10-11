package io.github.donggi.iroirobrowser.core.sites

import io.github.donggi.iroirobrowser.core.SafeFileName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SiteNamesTest {

    @Test
    fun 화면_글() {
        assertEquals("줄 바꿈 제목", SiteNames.display(" 줄\r\n바꿈\t\t제목 "))
        assertEquals("a b", SiteNames.display("a \u0000b"))
        // 방향 제어 문자는 빼고, 홀로 선 서로게이트는 U+FFFD.
        assertEquals("gpj.exe", SiteNames.display("gpj.\u202Eexe"))
        assertEquals("a�b", SiteNames.display("a\uD800b"))
        assertEquals("😀".repeat(3), SiteNames.display("😀".repeat(10), max = 3))
        assertEquals("é", SiteNames.display("é"))
        assertNull(SiteNames.display(null))
        assertNull(SiteNames.display(" \n\t "))
        assertNull(SiteNames.display("\u202E"))
    }

    @Test
    fun 저장_이름() {
        assertEquals("é", SiteNames.clean("é"))
        assertEquals("a_b", SiteNames.clean("a\uD800b"))
        assertEquals("a_b", SiteNames.clean("a/b"))
        assertEquals(SafeFileName.FALLBACK, SiteNames.clean(" .. "))
    }

    @Test
    fun 자르기는_코드_포인트_경계에서() {
        val text = "a😀b"
        assertEquals("a", SiteNames.takeCodePoints(text, 1))
        assertEquals("a😀", SiteNames.takeCodePoints(text, 2))
        assertEquals(text, SiteNames.takeCodePoints(text, 99))
        assertEquals("a", SiteNames.limitUtf8(text, 4))
        assertEquals("a😀", SiteNames.limitUtf8(text, 5))
        assertEquals("가", SiteNames.limitUtf8("가나", 5))
        assertEquals(8, SiteNames.utf8Length("a가😀"))
    }

    @Test
    fun 다시_다듬어도_그대로인_이름() {
        assertTrue(SiteNames.isClean("001.avif"))
        assertTrue(SiteNames.isClean("작가 이름"))
        for (bad in listOf("", ".", "..", "a/b", "a\\b", " a", "a ", "a.", "a\u0000", "a\u202Eb", "a\uD800", "x".repeat(201))) {
            assertFalse(SiteNames.isClean(bad), bad)
        }
    }

    @Test
    fun 겹침_꼬리() {
        assertEquals(listOf("a.avif", "b.avif"), SiteNames.unique(listOf("a.avif", "b.avif")))
        assertEquals(listOf("a", "a (2)", "A (3)"), SiteNames.unique(listOf("a", "a", "A")))
        // 200 바이트에 꽉 찬 이름 — 꼬리를 붙이려고 앞을 줄인다(그래서 다시 다듬어도 그대로).
        val long = "가".repeat(65) + ".avif"
        val unique = SiteNames.unique(listOf(long, long))
        assertEquals("가".repeat(63) + " (2).avif", unique[1])
        assertTrue(unique.all { SiteNames.isClean(it) })
    }
}
