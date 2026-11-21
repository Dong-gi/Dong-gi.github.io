package io.github.donggi.iroiroviewer.playback

import io.github.donggi.iroiroviewer.charset.CharsetDetector
import io.github.donggi.iroiroviewer.charset.TextEncoding
import org.junit.Test
import java.util.concurrent.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * SAMI → SRT(`SamiToSrt`)와, 그 뒤에 싱크를 거는 한 곳(`SubtitleRewrite`). 표본은 모두 이 시험을 위해 지은 합성 글이다.
 *
 * media3 의 `SubripParser` 는 안드로이드(`Html.fromHtml`)라 여기서 돌릴 수 없다 — 대신 그 파서의 시각 줄 정규식(1.11.1 바이트코드)으로
 * 내보낸 글의 모양을 본다([assertSrtShape]).
 */
class SamiToSrtTest {

    private fun sami(body: String, style: String = KR_EN_STYLE) =
        "<SAMI>\r\n<HEAD>\r\n<TITLE>합성</TITLE>\r\n<STYLE TYPE=\"text/css\">\r\n<!--\r\n$style\r\n-->\r\n</STYLE>\r\n</HEAD>\r\n" +
            "<BODY>\r\n$body\r\n</BODY>\r\n</SAMI>\r\n"

    @Test
    fun `기본 — br 은 줄바꿈, nbsp 는 지움, 원문 줄바꿈은 빈칸`() {
        val out = SamiToSrt.convert(
            sami(
                "<SYNC Start=1000><P Class=KRCC>안녕<br>하세요\r\n" +
                    "<SYNC Start=2500><P Class=KRCC>&nbsp;\r\n" +
                    "<SYNC Start=3000><P Class=KRCC>둘째\r\n줄이 아니라 빈칸\r\n" +
                    "<SYNC Start=4000><P Class=KRCC>&nbsp;",
            ),
        )
        assertEquals(
            "1\n00:00:01,000 --> 00:00:02,500\n안녕\n하세요\n\n" +
                "2\n00:00:03,000 --> 00:00:04,000\n둘째 줄이 아니라 빈칸\n\n",
            out,
        )
        assertSrtShape(out)
    }

    @Test
    fun `여러 언어면 한국어 클래스를 고르고, 다른 언어만 바뀌는 시점은 한국어를 지우지 않는다`() {
        val out = SamiToSrt.convert(
            sami(
                // 영어가 파일에서 먼저 나온다 — 그래도 한국어다.
                "<SYNC Start=1000><P Class=ENCC>Hello<P Class=KRCC>안녕\r\n" +
                    "<SYNC Start=1500><P Class=ENCC>&nbsp;\r\n" +
                    "<SYNC Start=3000><P Class=KRCC>&nbsp;<P Class=ENCC>Bye",
            ),
        )
        assertEquals("1\n00:00:01,000 --> 00:00:03,000\n안녕\n\n", out)
    }

    @Test
    fun `한국어가 없으면 글이 있는 첫 클래스`() {
        val out = SamiToSrt.convert(
            sami(
                "<SYNC Start=0><P Class=JPCC>&nbsp;<P Class=ENCC>Hello\r\n" +
                    "<SYNC Start=500><P Class=JPCC>こんにちは\r\n" +
                    "<SYNC Start=900><P Class=ENCC>&nbsp;<P Class=JPCC>&nbsp;",
                style = ".ENCC { Name:English; lang:en-US; }\r\n.JPCC { Name:Japanese; lang:ja-JP; }",
            ),
        )
        assertEquals("1\n00:00:00,000 --> 00:00:00,900\nHello\n\n", out)
    }

    @Test
    fun `CSS 의 lang ko 로 한국어 클래스를 알아본다`() {
        val style = ".ENCC { Name:English; lang:en-US; }\r\n.SUBTTL {\r\n  Name: Korean;\r\n  lang: ko-KR;\r\n}"
        assertEquals(setOf("SUBTTL"), SamiToSrt.koreanClassesInStyle(style, style.length))
        val out = SamiToSrt.convert(sami("<SYNC Start=10><P Class=ENCC>Hi<P Class=subttl>안녕", style = style))
        assertEquals("1\n00:00:00,010 --> 00:00:05,010\n안녕\n\n", out)
        assertEquals("KOCC", SamiToSrt.chooseClass(listOf("ENCC", "KOCC"), emptySet()))
        assertEquals("ENCC", SamiToSrt.chooseClass(listOf("ENCC", "JPCC"), emptySet()))
        assertEquals("", SamiToSrt.chooseClass(emptyList(), emptySet()))
    }

    @Test
    fun `클래스 없는 글 — P 없이 SYNC 바로 뒤, Class 없는 P`() {
        val out = SamiToSrt.convert(
            "<sami><body>\n<sync start=100>첫째\n<sync start=200><p>둘째\n<sync start=300>&nbsp;\n</body></sami>",
        )
        assertEquals("1\n00:00:00,100 --> 00:00:00,200\n첫째\n\n2\n00:00:00,200 --> 00:00:00,300\n둘째\n\n", out)
    }

    @Test
    fun `대소문자·따옴표·단위가 붙은 속성`() {
        val out = SamiToSrt.convert(
            "<SAMI><BODY>" +
                "<sync START=\"1000\"><p CLASS='krcc'>가</p>" +
                "<Sync start = 2000ms ><P class = KRCC >나" +
                "<SYNC Start=+3000 End=3500><P Class=\"KRCC\">다" +
                "<SYNC Start=9000><P Class=KRCC>&nbsp;",
        )
        assertEquals(
            "1\n00:00:01,000 --> 00:00:02,000\n가\n\n" +
                "2\n00:00:02,000 --> 00:00:03,000\n나\n\n" +
                // End 가 다음 시점보다 먼저 끝낸다.
                "3\n00:00:03,000 --> 00:00:03,500\n다\n\n",
            out,
        )
    }

    @Test
    fun `엔티티를 풀고 SRT 쪽 fromHtml 이 되돌리게 다시 적는다`() {
        val out = SamiToSrt.convert(
            "<SYNC Start=0><P>a &lt;b&gt; &amp; &quot;c&quot; &#65;&#x42;&#x1F600; &nbsp세 &bogus; a < b && c" +
                "<SYNC Start=1000>&NBSP;",
        )
        assertEquals("1\n00:00:00,000 --> 00:00:01,000\na &lt;b&gt; &amp; \"c\" AB😀 세 &amp;bogus; a &lt; b &amp;&amp; c\n\n", out)
    }

    @Test
    fun `서식 태그는 모두 걷고 글만 남긴다`() {
        val out = SamiToSrt.convert(
            "<SYNC Start=0><P Class=KRCC><font color=\"#ffff00\">노랑</font> <b>굵게</b> <i>기울</i><ruby>漢<rt>한</rt></ruby>" +
                "<SYNC Start=10><P Class=KRCC>&nbsp;",
        )
        assertEquals("1\n00:00:00,000 --> 00:00:00,010\n노랑 굵게 기울漢한\n\n", out)
    }

    @Test
    fun `주석은 건너뛰고, 끝이 없는 주석은 다음 꺾쇠까지만`() {
        val out = SamiToSrt.convert(
            "<SYNC Start=0><P>보임<!-- <SYNC Start=500><P>숨김 -->\n" +
                "<SYNC Start=1000><P>둘째 <!-- 끝없는 주석 > 셋째\n" +
                "<SYNC Start=2000>&nbsp;",
        )
        assertEquals(
            "1\n00:00:00,000 --> 00:00:01,000\n보임\n\n2\n00:00:01,000 --> 00:00:02,000\n둘째 셋째\n\n",
            out,
        )
    }

    @Test
    fun `뒤집힌 차례는 시각 순으로, 같은 시각은 나중 것이 이긴다`() {
        val out = SamiToSrt.convert(
            "<SYNC Start=3000><P>셋\n<SYNC Start=1000><P>하나\n<SYNC Start=2000><P>틀린 둘\n<SYNC Start=2000><P>둘\n" +
                "<SYNC Start=4000>&nbsp;",
        )
        assertEquals(
            "1\n00:00:01,000 --> 00:00:02,000\n하나\n\n2\n00:00:02,000 --> 00:00:03,000\n둘\n\n" +
                "3\n00:00:03,000 --> 00:00:04,000\n셋\n\n",
            out,
        )
    }

    @Test
    fun `마지막 글은 정한 시간만 뜨고, 시각이 없거나 터무니없는 SYNC 는 버린다`() {
        val out = SamiToSrt.convert(
            "<SYNC><P>시각 없음<SYNC Start=abc><P>글자<SYNC Start=-5><P>음수<SYNC Start=1234567890><P>열 자리" +
                "<SYNC Start=3600000><P>한 시간",
        )
        assertEquals("1\n01:00:00,000 --> 01:00:05,000\n한 시간\n\n", out)
    }

    @Test
    fun `깨진 마크업 — 닫는 꺾쇠가 빠진 태그, 닫지 않은 TITLE`() {
        val out = SamiToSrt.convert(
            "<SAMI><HEAD><TITLE>닫지 않음<BODY><SYNC Start=1000<P Class=KRCC>안녕<SYNC Start=2000><P Class=KRCC>&nbsp;",
        )
        assertEquals("1\n00:00:01,000 --> 00:00:02,000\n안녕\n\n", out)
    }

    @Test
    fun `STYLE 과 TITLE 의 글은 자막이 아니고 BODY 가 닫히면 읽지 않는다`() {
        val out = SamiToSrt.convert(
            "<SYNC Start=0><P>본문<TITLE>제목</TITLE><STYLE>.X{}</STYLE>" +
                "<SYNC Start=100>&nbsp;</BODY><SYNC Start=200><P>밖",
        )
        assertEquals("1\n00:00:00,000 --> 00:00:00,100\n본문\n\n", out)
    }

    @Test
    fun `SYNC 가 없으면 SAMI 가 아니다 — 그대로 둔다`() {
        val srt = "1\n00:00:01,000 --> 00:00:02,000\n이름만 smi\n"
        assertFalse(SamiToSrt.looksLikeSami(srt))
        assertEquals(srt, SamiToSrt.convert(srt))
        assertEquals("", SamiToSrt.convert(""))
    }

    @Test
    fun `줄 길이와 줄 수를 누른다 — 대리 쌍을 가르지 않는다`() {
        val long = "가".repeat(SamiToSrt.MAX_LINE_CHARS - 1) + "😀" + "나".repeat(10)
        val many = (1..40).joinToString("<br>") { "줄$it" }
        val out = SamiToSrt.convert("<SYNC Start=0><P>$long<SYNC Start=10><P>$many<SYNC Start=20>&nbsp;")
        val cues = out.split("\n\n").filter { it.isNotBlank() }
        val first = cues[0].lines()[2]
        assertEquals("가".repeat(SamiToSrt.MAX_LINE_CHARS - 1), first)
        assertEquals(SamiToSrt.MAX_CUE_LINES, cues[1].lines().size - 2)
        assertSrtShape(out)
    }

    @Test
    fun `제어 문자는 버리고 빈 줄은 내보내지 않는다`() {
        val out = SamiToSrt.convert("<SYNC Start=0><P>가\u0000\u0007나<br><br>  <br>다&#1;<SYNC Start=5>&nbsp;")
        // `&#1;` 도 제어 문자다 — 풀고 나서 버린다.
        assertEquals("1\n00:00:00,000 --> 00:00:00,005\n가나\n다\n\n", out)
    }

    @Test
    fun `취소되면 던진다`() {
        val body = (0 until 50_000).joinToString("") { "<SYNC Start=$it><P>x" }
        assertFailsWith<CancellationException> { SamiToSrt.convert(body) { true } }
    }

    // ---- 적대적 입력 — 모두 입력 길이에 비례해 끝나야 한다 ----------------------------------------------

    @Test(timeout = 10_000)
    fun `끝없는 태그`() {
        val out = SamiToSrt.convert("<SYNC Start=0><P>앞<font " + "a".repeat(4_000_000))
        assertEquals("1\n00:00:00,000 --> 00:00:05,000\n앞\n\n", out)
    }

    @Test(timeout = 10_000)
    fun `여는 꺾쇠만 수백만 개, 끝없는 주석 수십만 개`() {
        val lt = SamiToSrt.convert("<SYNC Start=0><P>" + "<a".repeat(1_000_000))
        assertEquals("", lt)
        val comments = SamiToSrt.convert("<SYNC Start=0><P>x" + "<!--".repeat(500_000))
        assertTrue(comments.startsWith("1\n00:00:00,000 --> 00:00:05,000\nx\n"), comments.take(80))
        val entities = SamiToSrt.convert("<SYNC Start=0><P>" + "&".repeat(2_000_000))
        assertSrtShape(entities)
    }

    @Test(timeout = 10_000)
    fun `깊은 중첩`() {
        val depth = 500_000
        val out = SamiToSrt.convert("<SYNC Start=0><P Class=KRCC>" + "<font><b>".repeat(depth) + "깊다" + "</b></font>".repeat(depth))
        assertEquals("1\n00:00:00,000 --> 00:00:05,000\n깊다\n\n", out)
    }

    @Test(timeout = 20_000)
    fun `백만 SYNC — 상한에서 읽기를 멈춘다`() {
        val sb = StringBuilder()
        for (t in 0 until 1_000_000) sb.append("<SYNC Start=").append(t).append("><P>").append(t % 10)
        val out = SamiToSrt.convert(sb.toString())
        val cues = out.count { it == '>' } // 시각 줄의 `-->` 하나씩 — 글에는 `>` 가 없다.
        assertEquals(SamiToSrt.MAX_SYNCS, cues)
        assertTrue(out.endsWith("${SamiToSrt.MAX_SYNCS}\n00:01:39,999 --> 00:01:44,999\n9\n\n"), out.takeLast(80))
    }

    @Test(timeout = 10_000)
    fun `한 속성이 수백만 자`() {
        val out = SamiToSrt.convert("<SYNC Start=\"" + "1".repeat(3_000_000) + "\"><P>x<SYNC Start=1 Class=" + "=".repeat(1_000_000) + "><P>y")
        assertEquals("1\n00:00:00,001 --> 00:00:05,001\ny\n\n", out)
    }

    /** `SubripParser` 는 `fromHtml` 앞에서 중괄호 + 역슬래시 조각을 지우고 정렬 조각이면 자막을 옮긴다(1.11.1) — 글의 그것은 끊어 둔다. */
    @Test
    fun `중괄호 역슬래시 조각을 끊어 SRT 파서가 지우거나 옮기지 않게 한다`() {
        val out = SamiToSrt.convert("""<SYNC Start=0><P>{\an8}위로 {\i1}x \y {a}<SYNC Start=10>&nbsp;""")
        val zw = 0x200B.toChar()
        assertEquals("1\n00:00:00,000 --> 00:00:00,010\n{$zw\\an8}위로 {$zw\\i1}x \\y {a}\n\n", out)
        // media3 1.11.1 의 `SUBRIP_TAG_PATTERN`·`SUBRIP_ALIGNMENT_TAG` 와 같은 정규식이 하나도 걸리지 않는다.
        assertFalse(Regex("""\{\\.*?\}""").containsMatchIn(out))
        assertFalse(Regex("""\{\\an[1-9]\}""").containsMatchIn(out))
    }

    @Test(timeout = 10_000)
    fun `한 시점에 P 가 수십만 개 — 조각 수를 누르고 큐 줄 수를 지킨다`() {
        val out = SamiToSrt.convert("<SYNC Start=0>" + "<P Class=KRCC>가<P>나".repeat(300_000))
        val lines = out.removeSuffix("\n\n").split('\n').drop(2)
        assertEquals(SamiToSrt.MAX_CUE_LINES, lines.size)
        assertEquals(listOf("가", "나"), lines.take(2))
    }

    @Test(timeout = 20_000)
    fun `내보내는 글은 상한에서 큐 경계로 멈춘다`() {
        val cue = (1..SamiToSrt.MAX_CUE_LINES).joinToString("<br>") { "&amp;".repeat(SamiToSrt.MAX_LINE_CHARS) }
        val sb = StringBuilder()
        for (t in 0 until 200) sb.append("<SYNC Start=").append(t * 1000).append("><P>").append(cue)
        val out = SamiToSrt.convert(sb.toString())
        // 큐 하나가 약 8만 자(`&` 하나가 `&amp;` 다섯 자로 다시 적힌다) — 200개면 1,600만 자가 될 것을 상한에서 멈췄다.
        assertTrue(out.length >= SamiToSrt.MAX_OUTPUT_CHARS && out.length < SamiToSrt.MAX_OUTPUT_CHARS + 100_000, "${out.length}")
        assertSrtShape(out)
    }

    // ---- 인코딩과 싱크 — 바이트 길의 한 곳(`SubtitleRewrite`) --------------------------------------------

    @Test
    fun `CP949 SAMI — 긴 영문 머리 뒤의 한글로 판정이 맞는다`() {
        val cp949 = charset("x-windows-949")
        val text = sami(
            (1..30).joinToString("\r\n") { "<SYNC Start=${it * 1000}><P Class=KRCC>대사 $it 번째입니다<br>둘째 줄" } +
                "\r\n<SYNC Start=31000><P Class=KRCC>&nbsp;",
        )
        val raw = text.toByteArray(cp949)
        val detection = CharsetDetector.detect(raw)
        assertEquals(TextEncoding.CP949, detection.encoding)
        val utf8 = String(raw, cp949).toByteArray(Charsets.UTF_8)
        val out = String(SubtitleRewrite.apply(utf8, "smi", 0), Charsets.UTF_8)
        assertTrue(out.startsWith("1\n00:00:01,000 --> 00:00:02,000\n대사 1 번째입니다\n둘째 줄\n\n"), out.take(80))
        assertSrtShape(out)
    }

    @Test
    fun `싱크는 바꾼 SRT 에 건다 — smi 에도 sami 에도`() {
        val smi = "<SYNC Start=1000><P>가<SYNC Start=2000>&nbsp;".toByteArray()
        assertEquals(
            "1\n00:00:01,300 --> 00:00:02,300\n가\n\n",
            String(SubtitleRewrite.apply(smi, "smi", 300), Charsets.UTF_8),
        )
        assertEquals(
            "1\n00:00:00,000 --> 00:00:00,500\n가\n\n",
            String(SubtitleRewrite.apply(smi, "sami", -1500), Charsets.UTF_8),
        )
        // 이름만 smi 인 SRT — 그대로 SRT 로 옮긴다.
        val srt = "1\n00:00:01,000 --> 00:00:02,000\n가\n".toByteArray()
        assertEquals("1\n00:00:01,100 --> 00:00:02,100\n가\n", String(SubtitleRewrite.apply(srt, "smi", 100), Charsets.UTF_8))
    }

    @Test
    fun `할 일이 없으면 바이트를 건드리지 않는다`() {
        val bytes = byteArrayOf(0x31, 0x0A, 0xFF.toByte())
        assertTrue(SubtitleRewrite.apply(bytes, "srt", 0) === bytes)
        assertTrue(SubtitleRewrite.apply(bytes, "ass", 0) === bytes)
        // `<SYNC` 가 없는 `.smi`(이름만 SAMI)도 — 디코딩을 거치면 0xFF 가 U+FFFD 로 바뀐다.
        assertTrue(SubtitleRewrite.apply(bytes, "smi", 0) === bytes)
        assertTrue(SubtitleRewrite.apply(bytes, "sami", 0) === bytes)
    }

    /** 내보낸 글이 media3 `SubripParser` 가 읽는 꼴인가 — 번호 줄, 그 파서의 시각 줄 정규식, 빈 줄 없는 글, 빈 줄로 끝. */
    private fun assertSrtShape(out: String) {
        if (out.isEmpty()) return
        assertTrue(out.endsWith("\n\n"))
        var expected = 1
        for (cue in out.removeSuffix("\n\n").split("\n\n")) {
            val lines = cue.split('\n')
            assertEquals(expected.toString(), lines[0])
            assertTrue(SUBRIP_TIMING.matches(lines[1]), lines[1])
            assertTrue(lines.size >= 3 && lines.drop(2).all { it.isNotEmpty() }, cue)
            expected++
        }
    }

    private companion object {
        val SUBRIP_TIMING = Regex(
            """\s*((?:(\d+):)?(\d+):(\d+)(?:,(\d{3}))?)\s*-->\s*((?:(\d+):)?(\d+):(\d+)(?:,(\d{3}))?)\s*""",
        )
        const val KR_EN_STYLE = "P { margin-left:8pt; font-size:20pt; }\r\n" +
            ".KRCC { Name:Korean; lang:ko-KR; SAMIType:CC; }\r\n.ENCC { Name:English; lang:en-US; SAMIType:CC; }"
    }
}
