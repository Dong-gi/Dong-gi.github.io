package io.github.donggi.iroiroviewer.webhost

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 가짜 출처의 계약. **계측 시험인 것은 `android.net.Uri` 때문이다** — 그 클래스는
 * 순수 JVM 에서 스텁이라 아무것도 답하지 않는다.
 *
 * 여기서 지키는 것은 하나다: **우리 호스트가 아닌 것을 우리 것으로 착각하지 않는다.**
 * 착각하면 문서 안의 주소 하나가 우리 자원 공급기로 들어오고, 그쪽은 책 안의 경로라고
 * 믿고 찾는다.
 */
class WebHostTest {

    @Test
    fun 우리_경로를_되찾는다() {
        val url = WebHost.urlFor("OEBPS/text/ch1.xhtml")
        assertEquals("OEBPS/text/ch1.xhtml", WebHost.pathOf(Uri.parse(url)))
    }

    @Test
    fun 한글_이름을_견딘다() {
        val url = WebHost.urlFor("OEBPS/그림/표지 사진.png")
        assertEquals("OEBPS/그림/표지 사진.png", WebHost.pathOf(Uri.parse(url)))
    }

    @Test
    fun 조각과_질의가_경로를_더럽히지_않는다() {
        val url = WebHost.urlFor("OEBPS/ch1.xhtml") + "#note1"
        assertEquals("OEBPS/ch1.xhtml", WebHost.pathOf(Uri.parse(url)))
    }

    @Test
    fun 다른_호스트는_우리가_아니다() {
        for (url in listOf(
            "https://evil.example.com/OEBPS/ch1.xhtml",
            "https://book.iroiro.invalid.evil.com/x",
            "http://book.iroiro.invalid/x",
            "file:///sdcard/x.png",
            "content://media/external/images/1",
            "javascript:alert(1)",
        )) {
            assertNull(url, WebHost.pathOf(Uri.parse(url)))
        }
    }

    @Test
    fun 뿌리_자체는_경로가_아니다() {
        assertNull(WebHost.pathOf(Uri.parse("https://book.iroiro.invalid/")))
    }

    @Test
    fun 호스트_대소문자를_가리지_않는다() {
        assertEquals("a.png", WebHost.pathOf(Uri.parse("HTTPS://BOOK.IROIRO.INVALID/a.png")))
    }

    @Test
    fun 경로를_끊는_글자를_감춘다() {
        // 이름에 `?` 가 들어 있으면 그 뒤가 질의가 되어 경로가 잘린다.
        val url = WebHost.urlFor("OEBPS/무엇인가?.png")
        assertEquals("OEBPS/무엇인가?.png", WebHost.pathOf(Uri.parse(url)))
        assertTrue("%3F" in url || "%3f" in url)
    }
}
