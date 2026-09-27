package io.github.donggi.iroiroviewer.format.hwpx

import io.github.donggi.iroiroviewer.format.ByteArrayDocumentSource
import io.github.donggi.iroiroviewer.format.FlowWarnings
import io.github.donggi.iroiroviewer.format.OpenFailure
import io.github.donggi.iroiroviewer.format.OpenOutcome
import io.github.donggi.iroiroviewer.format.html.HancomChunkPolicy
import io.github.donggi.iroiroviewer.format.hwpx.Hwpx.p
import io.github.donggi.iroiroviewer.format.hwpx.Hwpx.pRaw
import io.github.donggi.iroiroviewer.format.hwpx.Hwpx.run
import io.github.donggi.iroiroviewer.format.hwpx.Hwpx.sec
import io.github.donggi.iroiroviewer.format.hwpx.Hwpx.tbl
import io.github.donggi.iroiroviewer.format.hwpx.Hwpx.tc
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 깨진 문서·상한·취소·닫힘 — 무너지지 않는 것. */
class HwpxRobustnessTest {

    @Test
    fun 깨진_구역은_깨진_자리까지_보여_주고_부분_실패를_알린다() {
        val broken = sec(p("살아남는 글")).removeSuffix("</hs:sec>") + "<hp:p><hp:run><hp:t>깨진</hp:run></hp:p>" + p("못 가는 글") + "</hs:sec>"
        val t = TinyHwpx().section(broken).section(sec(p("멀쩡한 둘째 구역")))
        Hwpx.doc(t).use { doc ->
            assertEquals(2, doc.parts.size)
            val html = doc.body(0)
            assertTrue("살아남는 글" in html, html)
            assertFalse("못 가는 글" in html)
            assertFalse("part-failed" in html, "부분 전체를 ⚠ 로 바꾸지 않는다")
            assertTrue(doc.warnings.any { it.code == FlowWarnings.PART_FAILED }, doc.warnings.toString())
            assertTrue("멀쩡한 둘째 구역" in doc.body(1))
        }
    }

    @Test
    fun 구역이_모두_깨졌으면_미리보기_글을_보인다() {
        val t = TinyHwpx().section("<hs:sec 깨짐").section("이것도 XML 이 아니다")
            .file("Preview/PrvText.txt", "<보 도 자 료>\r\n첫 줄 글\r\n둘째 줄".toByteArray())
        Hwpx.doc(t).use { doc ->
            assertEquals(1, doc.parts.size)
            val html = doc.body()
            assertTrue("class=\"preview\"" in html, html)
            assertEquals(listOf("<보 도 자 료>", "첫 줄 글", "둘째 줄"), paragraphs(html))
            val failed = doc.warnings.filter { it.code == FlowWarnings.PART_FAILED }.map { it.detail }
            assertEquals(listOf(FlowWarnings.PART_NUMBER_PREFIX + "1", FlowWarnings.PART_NUMBER_PREFIX + "2"), failed)
        }
    }

    @Test
    fun 미리보기_글은_UTF_16LE_도_읽는다() {
        val utf16 = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "미리 보기".toByteArray(Charsets.UTF_16LE)
        assertEquals("미리 보기", HwpxFlowDocument.decodePreview(utf16))
        assertEquals("미리 보기", HwpxFlowDocument.decodePreview("미리 보기".toByteArray(Charsets.UTF_16LE)))
        assertEquals("미리 보기", HwpxFlowDocument.decodePreview("미리 보기".toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun 미리보기가_글이_아니면_보이지_않는다() {
        // 구역을 못 읽은 까닭이 암호라면 미리보기도 암호문이다. UTF-16LE 는 어떤 바이트도 글자로 읽으므로 거르지 않으면
        // 뜻 없는 글자가 '문서의 앞부분' 으로 보인다.
        val noise = ByteArray(600).also { java.util.Random(3).nextBytes(it) }
        val t = TinyHwpx().section("\u0001\u0002 암호문 같은 구역").file("Preview/PrvText.txt", noise)
        assertEquals(OpenFailure.Corrupt("보여 줄 구역이 없다"), (Hwpx.open(t.build()) as OpenOutcome.Failed).failure)

        // 배포용 문서면 '잠긴 문서' 로 알린다(미리보기가 먼저 가로채지 않는다).
        val settings = """<?xml version="1.0" encoding="UTF-8"?><ha:HWPApplicationSetting xmlns:ha="http://www.hancom.co.kr/hwpml/2011/app"><ha:docdistribute key="abc"/></ha:HWPApplicationSetting>"""
        val dist = TinyHwpx().section("\u0001\u0002 잠긴 바이트").file("Preview/PrvText.txt", noise).file("settings.xml", settings.toByteArray())
        assertTrue((Hwpx.open(dist.build()) as OpenOutcome.Failed).failure is OpenFailure.Encrypted)

        // 짧은 잡음(16 자)도 거른다. 한글이 쓴 미리보기 모양은 통과한다(표의 칸을 `<` `>` 로 감싸고 사설 영역 기호가 드물게 섞인다).
        repeat(200) { seed ->
            val short = ByteArray(32).also { java.util.Random(seed.toLong()).nextBytes(it) }
            assertFalse(HwpxFlowDocument.looksLikeText(String(short, Charsets.UTF_16LE)), "잡음 $seed")
        }
        assertTrue(HwpxFlowDocument.looksLikeText("<보 도 자 료>\r\n□ 2023년 기업생멸행정통계 결과 ① 활동기업 705만 개(+1.2%), ㎡당 3,000원  끝\r\n"))
    }

    @Test
    fun 구역이_모두_깨지고_미리보기도_없으면_깨진_파일이다() {
        val o = Hwpx.open(TinyHwpx().section("<hs:sec 깨짐").build())
        assertEquals(OpenFailure.Corrupt("보여 줄 구역이 없다"), (o as OpenOutcome.Failed).failure)
    }

    @Test
    fun 구역이_모두_상한에_걸리면_깨진_것이_아니라_너무_크다() {
        val deep = sec("<hp:x>".repeat(300) + "</hp:x>".repeat(300) + p("못 가는 글"))
        val o = Hwpx.open(TinyHwpx().section(deep).build())
        assertTrue((o as OpenOutcome.Failed).failure is OpenFailure.TooLarge, o.toString())
    }

    @Test
    fun 배포용_문서의_본문을_읽지_못하면_잠긴_문서로_알린다() {
        // 배포용 HWPX 의 본문이 어떻게 잠기는지는 알려져 있지 않다 — 짐작해서 풀지 않는다.
        val settings = """<?xml version="1.0" encoding="UTF-8"?><ha:HWPApplicationSetting xmlns:ha="http://www.hancom.co.kr/hwpml/2011/app"><ha:docdistribute key="abc" nocopy="1" noprint="1"/></ha:HWPApplicationSetting>"""
        val t = TinyHwpx().section("\u0001\u0002 잠긴 바이트").file("settings.xml", settings.toByteArray())
        val o = Hwpx.open(t.build())
        assertTrue((o as OpenOutcome.Failed).failure is OpenFailure.Encrypted, o.toString())

        // 본문이 읽히는 배포용 문서는 그대로 보인다.
        val readable = TinyHwpx().section(sec(p("배포용 본문"))).file("settings.xml", settings.toByteArray())
        Hwpx.doc(readable).use { doc -> assertTrue("배포용 본문" in doc.body()) }
    }

    @Test
    fun XML_깊이_상한을_넘으면_거기까지만_그리고_알린다() {
        val deep = "<hp:switch><hp:default>".repeat(140) + "<hp:t>너무 깊은 글</hp:t>" + "</hp:default></hp:switch>".repeat(140)
        val t = TinyHwpx().section(sec(p("앞 문단"), pRaw(run(deep))))
        Hwpx.doc(t).use { doc ->
            val html = doc.body()
            assertTrue("앞 문단" in html)
            assertFalse("너무 깊은 글" in html)
            assertTrue(doc.warnings.any { it.code == FlowWarnings.TRUNCATED }, doc.warnings.toString())
        }
    }

    @Test
    fun 쓰기_상한에_닿으면_앞부분을_닫아서_주고_알린다() {
        val body = (1..400).map { p("문단 $it 입니다") }.toTypedArray()
        Hwpx.doc(TinyHwpx().section(sec(*body)), HwpxOptions(maxChars = 3000)).use { doc ->
            val html = doc.body()
            assertTrue("문단 1 입니다" in html)
            assertFalse("문단 400 입니다" in html)
            assertTrue(doc.warnings.any { it.code == FlowWarnings.TRUNCATED })
            assertEquals(html.occurrences("<p"), html.occurrences("</p>"))
        }
    }

    @Test
    fun 쓰기_상한에_닿은_뒤에는_닫는_태그도_늘지_않는다() {
        // 문단 하나, 글자 덩이 2 만 개(위 첨자). 걷기가 멈추지 않으면 덩이마다 `</sup>` 이 붙어 상한의 몇 배가 된다.
        val pieces = (1..20_000).joinToString("") { "<hp:t>${'a' + it % 26}</hp:t>" }
        val t = TinyHwpx().section(sec(pRaw(run(pieces, charPr = 2)), p("뒤 문단")))
        Hwpx.doc(t, HwpxOptions(maxChars = 3000)).use { doc ->
            val html = doc.body()
            assertTrue(html.length < 3000 + 500, "길이 ${html.length}")
            assertEquals(html.occurrences("<sup"), html.occurrences("</sup>"))
            assertTrue(doc.warnings.any { it.code == FlowWarnings.TRUNCATED })
        }
    }

    @Test
    fun 표의_깊이_상한과_행_상한() {
        var nested = p("가장 안쪽")
        repeat(HwpxLimits.MAX_TABLE_DEPTH + 2) { nested = pRaw(run(tbl(1, 1, tc(0, 0, nested)))) }
        Hwpx.doc(TinyHwpx().section(sec(nested))).use { doc ->
            assertFalse("가장 안쪽" in doc.body())
            assertEquals(HwpxLimits.MAX_TABLE_DEPTH, doc.body().occurrences("<table>"))
            assertTrue(doc.warnings.any { it.code == FlowWarnings.TRUNCATED })
        }
    }

    @Test
    fun 조각의_표_칸_정보가_조각을_넘어도_맞는다() {
        // 합친 칸이 있는 표가 둘째 조각에 있다 — 표 번호가 조각의 시작 상태에서 이어져야 칸 정보를 찾는다.
        val spanned = pRaw(run(tbl(2, 2, tc(0, 0, p("둘째 표 머리"), colSpan = 2), tc(0, 1, p("x")) + tc(1, 1, p("y")))))
        val first = pRaw(run(tbl(1, 2, tc(0, 0, p("첫 표 a"), colSpan = 1) + tc(1, 0, p("첫 표 b"), rowSpan = 1))))
        val filler = (1..12).map { p("채움 $it") }.toTypedArray()
        val options = HwpxOptions(chunk = HancomChunkPolicy(softChars = 10, softBlocks = 5, hardChars = 20, hardBlocks = 5))
        Hwpx.doc(TinyHwpx().section(sec(first, *filler, spanned)), options).use { doc ->
            val last = doc.parts.size - 1
            assertTrue(last > 0)
            assertTrue(Regex("<td colspan=\"2\"><p[^>]*>둘째 표 머리").containsMatchIn(doc.body(last)), doc.body(last))
        }
    }

    @Test
    fun 닫힌_문서는_null_을_준다() {
        val doc = Hwpx.doc(TinyHwpx().section(sec(p("본문"))))
        doc.close()
        assertNull(doc.partHtml(0))
        assertNull(doc.openResource("Contents/header.xml"))
        doc.close()
    }

    @Test
    fun 그림이_아닌_항목은_내주지_않는다() {
        Hwpx.doc(TinyHwpx().section(sec(p("본문"))).file("Scripts/x.js", "alert(1)".toByteArray())).use { doc ->
            assertNull(doc.openResource("Contents/section0.xml"))
            assertNull(doc.openResource("Scripts/x.js"))
            assertNull(doc.openResource("../../etc/passwd"))
        }
    }

    @Test
    fun 취소는_실패가_아니라_취소로_올라간다() {
        val bytes = TinyHwpx().section(sec(*(1..2000).map { p("문단 $it") }.toTypedArray())).build()
        assertFailsWith<CancellationException> {
            runBlocking {
                val job = async(start = CoroutineStart.UNDISPATCHED) {
                    coroutineContext.cancel()
                    HwpxOpener(null).open(ByteArrayDocumentSource(bytes, "x.hwpx"))
                }
                job.await()
            }
        }
    }

    @Test
    fun 다시_그려도_버린_것을_두_번_세지_않는다() {
        val pic = """<hp:pic><hc:img binaryItemIDRef="none"/></hp:pic>"""
        val sections = (1..5).map { sec(pRaw(run(pic)), p("구역 $it")) }
        val t = TinyHwpx()
        sections.forEach { t.section(it) }
        Hwpx.doc(t).use { doc ->
            repeat(3) { for (i in doc.parts.indices) doc.partHtml(i) }
            assertEquals(5, doc.unsupported.snapshot()[io.github.donggi.iroiroviewer.format.UnsupportedFeatures.UNSUPPORTED_IMAGE])
        }
    }
}
