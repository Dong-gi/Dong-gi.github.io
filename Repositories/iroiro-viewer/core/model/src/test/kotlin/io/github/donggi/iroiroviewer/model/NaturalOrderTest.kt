package io.github.donggi.iroiroviewer.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NaturalOrderTest {

    private val collator = NameSortKey.koreanCollator()

    private fun sorted(vararg names: String): List<String> =
        names.sortedBy { NameSortKey.of(it, collator) }

    @Test
    fun `숫자를 수로 본다`() {
        // 문자열 비교는 10 을 2 앞에 둔다. 만화 한 권이 그렇게 늘어서면 쓸 수 없다.
        assertEquals(
            listOf("1.jpg", "2.jpg", "9.jpg", "10.jpg", "100.jpg"),
            sorted("10.jpg", "100.jpg", "2.jpg", "1.jpg", "9.jpg"),
        )
    }

    @Test
    fun `앞의 0 이 달라도 수로는 같고 자릿수로 갈린다`() {
        val r = sorted("01화.txt", "1화.txt", "002화.txt")
        assertEquals(listOf("1화.txt", "01화.txt", "002화.txt"), r)
    }

    @Test
    fun `숫자와 글자가 섞인 이름`() {
        assertEquals(
            listOf("2권.txt", "10권.txt", "부록.txt"),
            sorted("부록.txt", "10권.txt", "2권.txt"),
        )
    }

    @Test
    fun `한국어는 사전 순서로 선다`() {
        // 코드포인트 순서가 아니라 가나다 순서여야 한다.
        assertEquals(
            listOf("가나다.txt", "나다라.txt", "다라마.txt", "하늘.txt"),
            sorted("하늘.txt", "다라마.txt", "가나다.txt", "나다라.txt"),
        )
    }

    @Test
    fun `대소문자만 다르면 붙어 선다`() {
        val r = sorted("b.txt", "A.txt", "a.txt", "B.txt")
        // A/a 가 붙고 B/b 가 붙는다. 둘 사이 순서는 대조기가 정한다.
        assertTrue(r.indexOf("A.txt") < r.indexOf("b.txt"))
        assertTrue(r.indexOf("a.txt") < r.indexOf("b.txt"))
    }

    @Test
    fun `아주 긴 숫자열은 글자로 다룬다`() {
        // Long 에 담기지 않는 자리. 해시값 같은 이름이 실제로 들어온다.
        val long1 = "9".repeat(30) + ".bin"
        val long2 = "1".repeat(30) + ".bin"
        val r = sorted(long1, long2)
        assertEquals(listOf(long2, long1), r)
    }

    @Test
    fun `빈 이름과 숫자만 있는 이름도 죽지 않는다`() {
        sorted("", "0", "00", "123")
    }
}
