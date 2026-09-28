package io.github.donggi.iroiroviewer.docview

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `doc_progress.locator`·`progress` 의 모양. 이 칸은 다음 판의 앱이 읽으므로 **모양이 곧 약속**이다 — 고치면 옛 기록이
 * 조용히 버려진다. 그리고 경로·글이 들어가지 않는다는 저장소 규칙을 여기서 박는다.
 */
class DocLocatorTest {

    @Test
    fun 장_번호와_비율을_적고_되읽는다() {
        val s = DocLocator.encode(5, 0.42137f)
        assertEquals("5:0.4213", s)
        assertEquals(0.4213f, DocLocator.decode(s, 5)!!, 1e-6f)
        // 끝까지 읽었다.
        assertEquals("0:1.0000", DocLocator.encode(0, 1f))
        assertEquals(1f, DocLocator.decode("0:1.0000", 0))
    }

    @Test
    fun 처음이면_칸을_비워_둔다() {
        // 장 번호는 `page` 칸이 이미 든다 — 처음이면 적을 것이 없다.
        assertNull(DocLocator.encode(3, 0f))
        assertNull(DocLocator.encode(3, Float.NaN))
        assertNull(DocLocator.encode(-1, 0.5f))
    }

    @Test
    fun 기기의_언어가_소수점을_쉼표로_써도_같은_모양이다() {
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("2:0.5000", DocLocator.encode(2, 0.5f))
        } finally {
            Locale.setDefault(before)
        }
    }

    @Test
    fun 장_번호가_page_와_다르면_비율을_믿지_않는다() {
        // 다른 장의 비율로 옮기면 엉뚱한 곳에 떨어진다 — 장의 처음이 낫다.
        assertNull(DocLocator.decode("5:0.4213", 4))
    }

    @Test
    fun 우리가_쓰지_않은_모양은_읽지_않는다() {
        for (bad in listOf(
            null, "", "5", "5:", ":0.5", "5:0.5:1", "a:0.5", "5:1.5", "5:-0.1", "5:NaN", "5:0,5", " 5:0.5",
            "OEBPS/ch1.xhtml:0.5", "5:0.5#note", "12345678:0.5", "5:" + "0".repeat(40),
        )) {
            assertNull(DocLocator.decode(bad, 5), "읽혀서는 안 된다: $bad")
        }
    }

    @Test
    fun 적는_값에_경로도_글도_없다() {
        // 모양이 숫자·쌍점·점뿐이다.
        for ((i, f) in listOf(0 to 0.1f, 17 to 0.999f, 2013 to 0.5f)) {
            val s = DocLocator.encode(i, f)!!
            assertTrue(s.all { it.isDigit() || it == ':' || it == '.' }, s)
        }
    }

    @Test
    fun 진행은_문서_전체에서_얼마나_왔는가다() {
        assertEquals(0.5, DocLocator.progress(2, 0.5f, 5)!!, 1e-9)
        assertEquals(1.0, DocLocator.progress(4, 1f, 5)!!, 1e-9)
        assertEquals(0.0, DocLocator.progress(0, 0f, 5)!!, 1e-9)
        assertNull(DocLocator.progress(0, 0f, 0))
    }

    @Test
    fun 흐름_문서는_자리를_locator_에_본_몫을_progress_에_적는다() {
        // 한 화면에 드는 마지막 장 — 자리는 0(칸을 비운다), 본 몫은 1 이라 진행이 끝에 닿는다('다 읽음').
        val last = DocLocator.fields(paged = false, index = 4, fraction = 0f, seen = 1f, count = 5)
        assertNull(last.locator)
        assertEquals(1.0, last.progress!!, 1e-9)
        // 긴 장의 가운데 — 자리와 본 몫이 다르다(화면 한 장만큼).
        val mid = DocLocator.fields(paged = false, index = 2, fraction = 0.4f, seen = 0.5f, count = 5)
        assertEquals("2:0.4000", mid.locator)
        assertEquals(0.5, mid.progress!!, 1e-9)
    }

    @Test
    fun PDF_는_두_칸을_비운다() {
        // 배지는 `progress` 가 있으면 먼저 쓴다 — PDF 에 몫을 적으면 '4/8쪽' 이 '50%' 가 되고 한 쪽짜리 PDF 는 열기만 해도
        // '다 읽음' 이 된다. 쪽 문서의 자리는 `page`·`page_count` 가 말한다.
        assertEquals(DocLocator.Fields(null, null), DocLocator.fields(paged = true, index = 0, fraction = 0f, seen = 1f, count = 1))
        assertEquals(DocLocator.Fields(null, null), DocLocator.fields(paged = true, index = 3, fraction = 0f, seen = 0f, count = 8))
    }
}
