package io.github.donggi.iroiroviewer.charset

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 이진 판별.
 *
 * **틀리는 방향이 둘인데 무게가 다르다.** 이진 파일을 텍스트로 열면 화면이 지저분해질
 * 뿐이지만, **텍스트를 이진으로 거부하면 사용자가 자기 파일을 못 연다.** 그래서 이
 * 시험의 절반은 "거부하면 안 되는 것을 거부하지 않는가" 다.
 */
class BinarySnifferTest {

    private fun text(s: String, enc: TextEncoding): ByteArray =
        s.toByteArray(enc.charset!!)

    @Test
    fun `평범한 텍스트는 텍스트다`() {
        assertIs<BinarySniffer.Verdict.Text>(BinarySniffer.sniff(text("hello world\n", TextEncoding.UTF_8)))
        assertIs<BinarySniffer.Verdict.Text>(BinarySniffer.sniff(text("한글 문서입니다\n", TextEncoding.UTF_8)))
        assertIs<BinarySniffer.Verdict.Text>(BinarySniffer.sniff(text("한글 문서입니다\n", TextEncoding.CP949)))
    }

    @Test
    fun `NUL 이 있으면 이진이다`() {
        val bytes = byteArrayOf(0x68, 0x69, 0x00, 0x21)
        assertEquals(BinarySniffer.Verdict.Binary, BinarySniffer.sniff(bytes))
    }

    @Test
    fun `BOM 이 있으면 널이 있어도 텍스트다`() {
        // UTF-16LE BOM + "hi" — 널이 두 개 있지만 텍스트다.
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x68, 0x00, 0x69, 0x00)
        val v = BinarySniffer.sniff(bytes)
        assertIs<BinarySniffer.Verdict.Text>(v)
        assertEquals(TextEncoding.UTF_16LE, v.utf16Hint)
    }

    /**
     * **BOM 없는 UTF-16 을 거부하면 안 된다.**
     *
     * 이것이 '제어문자 비율' 방식이 무너지는 자리다 — 영문 UTF-16 은 바이트의 절반이
     * 널이라 어떤 비율 기준으로도 이진으로 보인다.
     */
    @Test
    fun `BOM 없는 영문 UTF-16 을 이진으로 거부하지 않는다`() {
        val le = "The quick brown fox jumps".toByteArray(TextEncoding.UTF_16LE.charset!!)
        val vle = BinarySniffer.sniff(le)
        assertIs<BinarySniffer.Verdict.Text>(vle)
        assertEquals(TextEncoding.UTF_16LE, vle.utf16Hint)

        val be = "The quick brown fox jumps".toByteArray(TextEncoding.UTF_16BE.charset!!)
        val vbe = BinarySniffer.sniff(be)
        assertIs<BinarySniffer.Verdict.Text>(vbe)
        assertEquals(TextEncoding.UTF_16BE, vbe.utf16Hint)
    }

    /**
     * 한글만 있는 BOM 없는 UTF-16 은 널이 없어 그냥 텍스트로 통과한다.
     * **인코딩을 맞히는 것은 이 클래스의 일이 아니다** — 거부하지 않으면 된다.
     */
    @Test
    fun `한글만 있는 UTF-16 도 거부되지 않는다`() {
        val bytes = "가나다라마바사아자차카타파하".toByteArray(TextEncoding.UTF_16LE.charset!!)
        assertIs<BinarySniffer.Verdict.Text>(BinarySniffer.sniff(bytes))
    }

    @Test
    fun `널이 양쪽 자리에 고루 있으면 이진이다`() {
        // 실행 파일처럼 널이 아무 데나 있는 것.
        val bytes = ByteArray(256) { if (it % 3 == 0) 0 else (it and 0x7F).toByte() }
        assertEquals(BinarySniffer.Verdict.Binary, BinarySniffer.sniff(bytes))
    }

    @Test
    fun `빈 파일은 텍스트다`() {
        assertIs<BinarySniffer.Verdict.Text>(BinarySniffer.sniff(ByteArray(0)))
    }

    @Test
    fun `앞머리만 채워진 배열도 길이를 지킨다`() {
        val buf = ByteArray(1024)
        val src = text("hello", TextEncoding.UTF_8)
        src.copyInto(buf)
        // length 를 주지 않으면 뒤의 널 1019개 때문에 이진이 된다.
        assertEquals(BinarySniffer.Verdict.Binary, BinarySniffer.sniff(buf))
        assertIs<BinarySniffer.Verdict.Text>(BinarySniffer.sniff(buf, src.size))
    }

    @Test
    fun `모든 바이트값이 든 파일은 이진이다`() {
        val bytes = ByteArray(256) { it.toByte() }
        assertEquals(BinarySniffer.Verdict.Binary, BinarySniffer.sniff(bytes))
    }

    @Test
    fun `PNG 와 ZIP 머리는 이진이다`() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13)
        assertEquals(BinarySniffer.Verdict.Binary, BinarySniffer.sniff(png))
        val zip = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x00, 0x00, 0x08, 0x00)
        assertEquals(BinarySniffer.Verdict.Binary, BinarySniffer.sniff(zip))
    }

    @Test
    fun `CP949 한글은 널이 없어 텍스트다`() {
        val bytes = text("한글만 잔뜩 들어 있는 파일입니다. 널 바이트는 없습니다.", TextEncoding.CP949)
        assertTrue(bytes.none { it == 0.toByte() })
        assertIs<BinarySniffer.Verdict.Text>(BinarySniffer.sniff(bytes))
    }

    /**
     * **UTF-16 이 아닌 것을 UTF-16 이라고 말하면 안 된다.**
     *
     * 검토가 재현해 온 결함이다 — UTF-8 한국어·Shift_JIS·GBK 파일이 전부 UTF-16LE 로
     * 판정됐다. 판정(Text/Binary)은 맞았지만 **힌트가 틀렸고**, 그 힌트를 믿는 쪽은
     * UTF-8 파일을 UTF-16 으로 읽게 된다. 가장 조용한 실패다.
     */
    @Test
    fun `UTF-16 이 아닌 것에 UTF-16 힌트를 주지 않는다`() {
        val nl = "\n"
        val cases = listOf(
            "ASCII 소스" to text("fun main() { println(\"hello world, a test\") }$nl", TextEncoding.UTF_8),
            "UTF-8 한국어" to text("한글 텍스트 파일입니다. 인코딩을 자동으로 판정해야 합니다.$nl", TextEncoding.UTF_8),
            "Shift_JIS 일본어" to text("日本語のテキストファイルです。エンコーディングを判定します。$nl", TextEncoding.SHIFT_JIS),
            "GB18030 중국어" to text("这是一个中文文本文件。需要自动判定编码。$nl", TextEncoding.GB18030),
            "CP949 한국어" to text("한글 텍스트 파일입니다. 인코딩을 판정합니다.$nl", TextEncoding.CP949),
        )
        for ((name, bytes) in cases) {
            val v = BinarySniffer.sniff(bytes)
            assertIs<BinarySniffer.Verdict.Text>(v, "$name 이 이진으로 거부됐다")
            assertEquals(null, v.utf16Hint, "$name 에 UTF-16 힌트가 붙었다")
        }
    }
}
