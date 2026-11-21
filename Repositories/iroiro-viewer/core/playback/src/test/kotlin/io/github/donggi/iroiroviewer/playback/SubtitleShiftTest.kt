package io.github.donggi.iroiroviewer.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 외부 자막의 싱크 옮기기 — 시각 글자를 고쳐 쓰는 일(`SubtitleShift`)과 그 값을 고르는 걸음. */
class SubtitleShiftTest {

    @Test
    fun `SRT 의 시각 둘을 옮기고 글과 줄 끝은 그대로 둔다`() {
        val srt = "1\r\n00:00:01,000 --> 00:00:02,500\r\n안녕 --> 하세요\r\n\r\n2\r\n00:59:59,950 --> 01:00:00,050\r\n끝\r\n"
        val out = SubtitleShift.apply(srt, "srt", 300)
        assertEquals(
            "1\r\n00:00:01,300 --> 00:00:02,800\r\n안녕 --> 하세요\r\n\r\n2\r\n01:00:00,250 --> 01:00:00,350\r\n끝\r\n",
            out,
        )
    }

    @Test
    fun `앞당겨 0 아래로 가면 0 에 멈춘다`() {
        val out = SubtitleShift.apply("00:00:00,200 --> 00:00:01,000\n", "srt", -500)
        assertEquals("00:00:00,000 --> 00:00:00,500\n", out)
    }

    @Test
    fun `VTT 는 시가 없는 시각도 읽고 뒤의 설정을 남긴다`() {
        val vtt = "WEBVTT\n\n00:01.000 --> 00:02.000 position:10% align:start\n글\n"
        val out = SubtitleShift.apply(vtt, "vtt", -100)
        assertEquals("WEBVTT\n\n00:00:00.900 --> 00:00:01.900 position:10% align:start\n글\n", out)
    }

    @Test
    fun `ASS 는 Format 줄이 정한 칸만 옮기고 글 속의 쉼표를 지킨다`() {
        val ass = """
            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:01.00,0:00:03.50,Default,,0,0,0,,하나, 둘, 0:00:09.00
            Comment: 0,0:00:01.00,0:00:03.50,Default,,0,0,0,,그대로
        """.trimIndent()
        val out = SubtitleShift.apply(ass, "ass", 1_200)
        val lines = out.lines()
        assertEquals("Dialogue: 0,0:00:02.20,0:00:04.70,Default,,0,0,0,,하나, 둘, 0:00:09.00", lines[2])
        assertEquals("Comment: 0,0:00:01.00,0:00:03.50,Default,,0,0,0,,그대로", lines[3])
    }

    @Test
    fun `SSA 의 다른 칸 차례도 Format 줄을 따른다`() {
        val ssa = "Format: Marked, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n" +
            "Dialogue: Marked=0,0:00:05.00,0:00:06.00,*Default,NTP,0000,0000,0000,!Effect,글\n"
        val out = SubtitleShift.apply(ssa, "ssa", -100)
        assertTrue(out.contains("Dialogue: Marked=0,0:00:04.90,0:00:05.90,*Default"), out)
    }

    @Test
    fun `0 이거나 모르는 형식이면 손대지 않는다`() {
        val srt = "00:00:01,000 --> 00:00:02,000\n"
        assertEquals(srt, SubtitleShift.apply(srt, "srt", 0))
        assertEquals(srt, SubtitleShift.apply(srt, "ttml", 500))
    }

    @Test
    fun `걸음은 0_1초에 맞추고 10분에서 멈춘다`() {
        assertEquals(100, SubtitleShift.step(0, SubtitleShift.STEP_MS))
        assertEquals(-200, SubtitleShift.step(-100, -SubtitleShift.STEP_MS))
        assertEquals(0, SubtitleShift.step(100, -SubtitleShift.STEP_MS))
        // 걸음에 어긋난 값(손으로 고친 주소)도 걸음 위로 돌아온다.
        assertEquals(200, SubtitleShift.step(149, 100))
        assertEquals(SubtitleShift.MAX_MS, SubtitleShift.step(SubtitleShift.MAX_MS, 100))
        assertEquals(-SubtitleShift.MAX_MS, SubtitleShift.step(-SubtitleShift.MAX_MS, -100))
    }

    @Test
    fun `주소의 값은 읽을 수 있을 때만 쓰고 끝에서 누른다`() {
        assertEquals(0, SubtitleShift.parse(null))
        assertEquals(0, SubtitleShift.parse("abc"))
        assertEquals(-300, SubtitleShift.parse("-300"))
        assertEquals(SubtitleShift.MAX_MS, SubtitleShift.parse("99999999999"))
    }

    @Test
    fun `글 자막만 옮길 수 있다`() {
        assertTrue(SubtitleShift.supports("영화.ko.srt"))
        assertTrue(SubtitleShift.supports("a.ASS"))
        assertFalse(SubtitleShift.supports("a.ttml"))
        assertFalse(SubtitleShift.supports(null))
    }

    @Test
    fun `홀로 선 CR 줄 끝도 줄로 읽고 그대로 둔다`() {
        val src = "1\r00:00:01,000 --> 00:00:02,000\rA\r"
        assertEquals("1\r00:00:01,500 --> 00:00:02,500\rA\r", SubtitleShift.apply(src, "srt", 500))
        val mixed = "1\r\n00:00:01,000 --> 00:00:02,000\nA\r"
        assertEquals("1\r\n00:00:00,900 --> 00:00:01,900\nA\r", SubtitleShift.apply(mixed, "srt", -100))
    }

    @Test
    fun `기기의 로캘과 상관없이 ASCII 숫자로 적는다`() {
        val saved = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar-EG"))
            assertEquals("00:00:01,500 --> 00:00:02,500", SubtitleShift.apply("00:00:01,000 --> 00:00:02,000", "srt", 500))
            val ass = "Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,A"
            assertEquals("Dialogue: 0,0:00:01.50,0:00:02.50,Default,,0,0,0,,A", SubtitleShift.apply(ass, "ass", 500))
        } finally {
            java.util.Locale.setDefault(saved)
        }
    }
}
