package io.github.donggi.iroiroviewer.text

import io.github.donggi.iroiroviewer.charset.TextEncoding
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 편집의 디코드·인코드([EditCodec]). **열고 손대지 않고 저장하면 같은 바이트**여야 한다 — 인코딩·BOM·줄 끝·마지막
 * 줄바꿈이 그대로. 고친 곳만 바뀌어야 한다. 그리고 그것을 지킬 수 없는 파일은 열지 않는다(읽을 수 없는 바이트·다시 쓰면 바뀌는 글자).
 */
class EditCodecTest {

    /** 열고, (고치지 않고) 저장한 바이트. */
    private fun roundTrip(bytes: ByteArray, forced: TextEncoding? = null): Pair<EditCodec.Decoded.Ok, ByteArray> {
        val decoded = assertIs<EditCodec.Decoded.Ok>(EditCodec.decode(bytes, bytes.size, forced))
        val encoded = assertIs<EditCodec.Encoded.Ok>(EditCodec.encode(decoded.text, decoded.format))
        return decoded to encoded.bytes
    }

    private val bomUtf8 = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    @Test
    fun `UTF-8 LF 마지막 줄바꿈 — 그대로 돌아온다`() {
        val bytes = "첫 줄\n둘째 줄\n".toByteArray(Charsets.UTF_8)
        val (d, back) = roundTrip(bytes)
        assertEquals(TextFormat(TextEncoding.UTF_8, bom = false, lineEnding = LineEnding.LF), d.format)
        assertEquals("첫 줄\n둘째 줄\n", d.text)
        assertContentEquals(bytes, back)
    }

    @Test
    fun `BOM 과 CRLF 를 지키고 편집기에는 BOM 글자도 CR 도 없다`() {
        val bytes = bomUtf8 + "a\r\n한글\r\nlast".toByteArray(Charsets.UTF_8)
        val (d, back) = roundTrip(bytes)
        assertTrue(d.format.bom)
        assertEquals(LineEnding.CRLF, d.format.lineEnding)
        // 편집기의 글 — BOM 은 바이트로 떼었고(보이지 않는 U+FEFF 가 붙지 않는다), 줄 끝은 \n 하나다.
        assertEquals("a\n한글\nlast", d.text)
        assertContentEquals(bytes, back)
    }

    @Test
    fun `마지막 줄바꿈은 있던 대로 — 붙이지도 떼지도 않는다`() {
        val without = "끝에 줄바꿈 없음".toByteArray(Charsets.UTF_8)
        assertContentEquals(without, roundTrip(without).second)
        val with = "끝에 줄바꿈 있음\r\n".toByteArray(Charsets.UTF_8)
        assertContentEquals(with, roundTrip(with).second)
        // 빈 파일(새 글 파일)은 빈 채로, UTF-8 BOM 없이.
        val (d, back) = roundTrip(ByteArray(0))
        assertEquals(TextFormat(TextEncoding.UTF_8, bom = false, lineEnding = LineEnding.LF), d.format)
        assertEquals(0, back.size)
    }

    @Test
    fun `UTF-16LE BOM 과 CR 만 쓰는 옛 맥 줄 끝`() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "가\r나\r".toByteArray(Charsets.UTF_16LE)
        val (d, back) = roundTrip(bytes)
        assertEquals(TextEncoding.UTF_16LE, d.format.encoding)
        assertTrue(d.format.bom)
        assertEquals(LineEnding.CR, d.format.lineEnding)
        assertEquals("가\n나\n", d.text)
        assertContentEquals(bytes, back)
    }

    @Test
    fun `CP949 는 판정한 그대로 다시 쓴다`() {
        val cp949 = TextEncoding.CP949.charset!!
        val bytes = "한글로 쓴 메모입니다.\r\n두 번째 줄 — 똠방각하\r\n".toByteArray(cp949)
        val (d, back) = roundTrip(bytes)
        assertEquals(TextEncoding.CP949, d.format.encoding)
        assertContentEquals(bytes, back)
    }

    @Test
    fun `뷰어에서 고른 인코딩으로 연다`() {
        val sjis = TextEncoding.SHIFT_JIS.charset!!
        val bytes = "日本語のメモ\n".toByteArray(sjis)
        val (d, back) = roundTrip(bytes, forced = TextEncoding.SHIFT_JIS)
        assertEquals(TextEncoding.SHIFT_JIS, d.format.encoding)
        assertContentEquals(bytes, back)
    }

    @Test
    fun `고친 곳만 바뀐다`() {
        val bytes = bomUtf8 + "하나\r\n둘\r\n셋".toByteArray(Charsets.UTF_8)
        val d = assertIs<EditCodec.Decoded.Ok>(EditCodec.decode(bytes))
        val edited = d.text.replace("둘", "둘둘") + "\n넷"
        val out = assertIs<EditCodec.Encoded.Ok>(EditCodec.encode(edited, d.format)).bytes
        assertContentEquals(bomUtf8 + "하나\r\n둘둘\r\n셋\r\n넷".toByteArray(Charsets.UTF_8), out)
    }

    @Test
    fun `섞인 줄 끝은 가장 많은 것으로 맞추고 그 수를 든다`() {
        val bytes = "a\r\nb\nc\r\nd".toByteArray(Charsets.UTF_8)
        val d = assertIs<EditCodec.Decoded.Ok>(EditCodec.decode(bytes))
        assertEquals(LineEnding.CRLF, d.format.lineEnding)
        val mixed = assertNotNull(d.format.mixed)
        assertEquals(1, mixed.lf)
        assertEquals(2, mixed.crlf)
        assertEquals(0, mixed.cr)
        val out = assertIs<EditCodec.Encoded.Ok>(EditCodec.encode(d.text, d.format)).bytes
        assertContentEquals("a\r\nb\r\nc\r\nd".toByteArray(Charsets.UTF_8), out)
    }

    @Test
    fun `줄 끝의 수가 같으면 먼저 나온 것 — 없으면 LF`() {
        assertEquals(LineEnding.LF, LineEndings.count("a\nb\r\nc").dominant())
        assertEquals(LineEnding.CRLF, LineEndings.count("a\r\nb\nc").dominant())
        assertEquals(LineEnding.LF, LineEndings.count("한 줄").dominant())
        assertNull(assertIs<EditCodec.Decoded.Ok>(EditCodec.decode("a\nb\n".toByteArray())).format.mixed)
    }

    @Test
    fun `쓸 수 없는 글자의 자리와 글자를 돌려준다 — CRLF 로 길어진 자리를 편집기의 자리로 되돌린다`() {
        val format = TextFormat(TextEncoding.CP949, bom = false, lineEnding = LineEnding.CRLF)
        val text = "가\n나다😀라"
        val stuck = assertIs<EditCodec.Encoded.Unmappable>(EditCodec.encode(text, format))
        assertEquals(text.indexOf("😀"), stuck.index)
        assertEquals("😀", stuck.sample)
        // UTF-8 로 바꾸면 쓴다 — BOM 없이, 줄 끝은 그대로.
        val utf8 = format.asUtf8()
        assertEquals(TextFormat(TextEncoding.UTF_8, bom = false, lineEnding = LineEnding.CRLF), utf8)
        val out = assertIs<EditCodec.Encoded.Ok>(EditCodec.encode(text, utf8)).bytes
        assertContentEquals("가\r\n나다😀라".toByteArray(Charsets.UTF_8), out)
    }

    @Test
    fun `Latin-1 파일에 한글을 치면 쓸 수 없다`() {
        val format = TextFormat(TextEncoding.LATIN1, bom = false, lineEnding = LineEnding.LF)
        val stuck = assertIs<EditCodec.Encoded.Unmappable>(EditCodec.encode("café 한", format))
        assertEquals(5, stuck.index)
        assertEquals("한", stuck.sample)
    }

    @Test
    fun `읽을 수 없는 바이트가 있으면 열지 않는다`() {
        // C3 다음에 이어지는 바이트가 아니다 — 뷰어는 � 로 보이지만 저장하면 그 바이트가 사라진다.
        val bytes = byteArrayOf('a'.code.toByte(), 0xC3.toByte(), 0x28)
        val refused = assertIs<EditCodec.Decoded.Refused>(EditCodec.decode(bytes, bytes.size, TextEncoding.UTF_8))
        assertEquals(EditCodec.Why.MALFORMED, refused.why)
        assertEquals(TextEncoding.UTF_8, refused.encoding)
    }

    @Test
    fun `다시 쓰면 바뀌는 글자가 있으면 열지 않는다`() {
        // JDK 의 Big5 는 A1 5A 를 U+FF3F 로 읽고 그것을 A1 C4 로 쓴다 — 저장하면 손대지 않은 글자가 바뀐다.
        val bytes = byteArrayOf(0xA1.toByte(), 0x5A)
        val refused = assertIs<EditCodec.Decoded.Refused>(EditCodec.decode(bytes, bytes.size, TextEncoding.BIG5))
        assertEquals(EditCodec.Why.NOT_ROUND_TRIP, refused.why)
    }

    @Test
    fun `글이 아니거나 UTF-32 면 열지 않는다 — 뷰어와 같은 판정`() {
        val binary = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0, 0, 0, 0x0D)
        assertEquals(EditCodec.Why.BINARY, assertIs<EditCodec.Decoded.Refused>(EditCodec.decode(binary)).why)
        val utf32 = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0, 0, 'a'.code.toByte(), 0, 0, 0)
        assertEquals(EditCodec.Why.UNSUPPORTED_ENCODING, assertIs<EditCodec.Decoded.Refused>(EditCodec.decode(utf32)).why)
    }

    @Test
    fun `줄 끝 바꾸기는 편집기와 파일 사이를 오간다`() {
        assertEquals("a\nb\nc\n", LineEndings.normalize("a\r\nb\rc\n"))
        val plain = "줄 끝 없음"
        assertTrue(LineEndings.normalize(plain) === plain)
        assertEquals("a\r\nb", LineEndings.apply("a\nb", LineEnding.CRLF).toString())
        assertEquals("a\rb", LineEndings.apply("a\nb", LineEnding.CR).toString())
    }
}
