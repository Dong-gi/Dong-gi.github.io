package io.github.donggi.iroiroviewer.charset

import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 창 경계에서 글자가 깨지지 않는가.
 *
 * **창 크기를 1바이트부터 돌린다.** 한 문자가 4바이트인 인코딩에서 1바이트 창은 최악의
 * 경우이고, 거기서 원본이 복원되면 그 사이의 어떤 크기에서도 복원된다. 실제 뷰어는
 * 64KiB 창을 쓰지만, 시험은 코드가 **구조적으로** 옳은지를 물어야 한다.
 *
 * 이 시험이 없으면 깨짐을 알아챌 방법이 없다 — 잘못된 구현은 예외를 던지지 않고
 * 조용히 `U+FFFD` 를 남긴다.
 */
class WindowedDecoderTest {

    /** 한글·라틴·한자·이모지를 섞는다. 이모지는 UTF-8 에서 4바이트, UTF-16 에서 서러게이트 쌍이다. */
    private val sample = "가나다 abc 漢字 😀 라마바 xyz 日本語 🚀 사아자"

    private fun decodeWindowed(bytes: ByteArray, charset: Charset, window: Int): String {
        val sb = StringBuilder()
        WindowedDecoder(charset.newDecoder(), window).decodeAll(ByteArrayInputStream(bytes)) { buf, off, len ->
            sb.appendRange(buf, off, off + len)
        }
        return sb.toString()
    }

    private fun charsetOf(e: TextEncoding): Charset? = e.charset

    @Test
    fun `창 크기 1부터 9까지 원본이 복원된다`() {
        for (enc in listOf(TextEncoding.UTF_8, TextEncoding.UTF_16LE, TextEncoding.UTF_16BE)) {
            val cs = charsetOf(enc) ?: continue
            val bytes = sample.toByteArray(cs)
            for (window in 1..9) {
                val back = decodeWindowed(bytes, cs, window)
                assertEquals(sample, back, "${enc.label} 창 $window 에서 깨졌다")
            }
        }
    }

    @Test
    fun `한국어 인코딩도 작은 창에서 복원된다`() {
        val cs = charsetOf(TextEncoding.CP949) ?: return
        // CP949 에 없는 글자(이모지·일부 한자)는 뺀다. 인코딩할 수 없는 것을 시험하는 것이
        // 아니라 **경계 처리**를 시험하는 것이다.
        val korean = "가나다 abc 라마바 xyz 사아자 차카타파하"
        val bytes = korean.toByteArray(cs)
        for (window in 1..9) {
            assertEquals(korean, decodeWindowed(bytes, cs, window), "CP949 창 $window 에서 깨졌다")
        }
    }

    @Test
    fun `일본어 인코딩도 작은 창에서 복원된다`() {
        val cs = charsetOf(TextEncoding.SHIFT_JIS) ?: return
        val japanese = "あいうえお abc カタカナ 漢字 xyz ｱｲｳ"
        val bytes = japanese.toByteArray(cs)
        for (window in 1..9) {
            assertEquals(japanese, decodeWindowed(bytes, cs, window), "Shift_JIS 창 $window 에서 깨졌다")
        }
    }

    /**
     * **순진한 방법이 실제로 깨지는 것을 확인한다.**
     *
     * 고친 코드가 옳다는 것만 시험하면, 나중에 누군가 '간단하게' 되돌렸을 때 아무도
     * 모른다. 무엇이 왜 틀렸는지를 시험이 기억한다.
     */
    @Test
    fun `창마다 String 을 새로 만들면 깨진다 — 그래서 이 클래스가 있다`() {
        val cs = charsetOf(TextEncoding.UTF_8)!!
        val bytes = sample.toByteArray(cs)
        val naive = StringBuilder()
        var i = 0
        while (i < bytes.size) {
            val n = minOf(5, bytes.size - i)
            naive.append(String(bytes, i, n, cs))
            i += n
        }
        assertTrue(naive.contains('�'), "순진한 방법이 깨지지 않았다 — 시험 전제가 틀렸다")
        assertTrue(naive.toString() != sample)
        // 같은 입력을 제대로 된 방법으로.
        assertEquals(sample, decodeWindowed(bytes, cs, 5))
    }

    @Test
    fun `빈 입력과 한 글자 입력`() {
        val cs = charsetOf(TextEncoding.UTF_8)!!
        assertEquals("", decodeWindowed(ByteArray(0), cs, 8))
        assertEquals("가", decodeWindowed("가".toByteArray(cs), cs, 1))
        assertEquals("😀", decodeWindowed("😀".toByteArray(cs), cs, 1))
    }

    @Test
    fun `깨진 바이트가 있어도 나머지를 읽는다`() {
        val cs = charsetOf(TextEncoding.UTF_8)!!
        // 유효한 앞부분 + 깨진 바이트 + 유효한 뒷부분.
        val bytes = "앞".toByteArray(cs) + byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "뒤".toByteArray(cs)
        val back = decodeWindowed(bytes, cs, 3)
        assertTrue(back.startsWith("앞"), "앞부분을 잃었다: $back")
        assertTrue(back.endsWith("뒤"), "뒷부분을 잃었다: $back")
        assertTrue(back.contains('�'), "깨진 바이트가 표시되지 않았다")
    }

    @Test
    fun `한도를 주면 거기서 멈춘다`() {
        val cs = charsetOf(TextEncoding.UTF_8)!!
        val long = "가".repeat(10_000)
        var produced = 0L
        WindowedDecoder(cs.newDecoder(), 64).decodeAll(
            ByteArrayInputStream(long.toByteArray(cs)),
            limitChars = 100,
        ) { _, _, len -> produced += len }
        assertTrue(produced in 100..200, "한도 근처에서 멈춰야 한다. 실제 $produced")
    }
}
