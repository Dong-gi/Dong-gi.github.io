package io.github.donggi.iroiroviewer.ui.image

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 움직이는 WebP·APNG 판별.
 *
 * **이 판정이 틀리면 사용자 파일이 망가진다.** 움직이는 WebP 에 EXIF 를 쓰면 컨테이너가
 * 통째로 다시 쓰이는데 프레임이 보존되는지 확인하지 못했고, 그래서 우리는 그런 파일에는
 * 회전을 저장하지 않기로 했다. 판정이 거짓 음성을 내면 그 보호가 그대로 사라진다.
 *
 * 컨테이너를 손으로 만들어 시험한다 — 픽셀 데이터가 없어도 우리 판별기는 청크 **머리만**
 * 보므로 답이 같고, 20KB짜리 실제 표본을 저장소에 커밋하지 않아도 된다.
 */
class ImageFormatsTest {

    private fun le32(v: Int) = byteArrayOf(
        (v and 0xFF).toByte(),
        ((v shr 8) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(),
        ((v shr 24) and 0xFF).toByte(),
    )

    private fun be32(v: Int) = byteArrayOf(
        ((v shr 24) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(),
        ((v shr 8) and 0xFF).toByte(),
        (v and 0xFF).toByte(),
    )

    private fun tmp(name: String, bytes: ByteArray): File =
        File.createTempFile(name, null).apply { writeBytes(bytes) ; deleteOnExit() }

    /** `RIFF....WEBP` + 청크들. */
    private fun webp(vararg chunks: Pair<String, ByteArray>): ByteArray {
        val body = chunks.fold(ByteArray(0)) { acc, (tag, data) ->
            val padded = if (data.size % 2 == 1) data + byteArrayOf(0) else data
            acc + tag.toByteArray(Charsets.US_ASCII) + le32(data.size) + padded
        }
        return "RIFF".toByteArray(Charsets.US_ASCII) + le32(4 + body.size) +
            "WEBP".toByteArray(Charsets.US_ASCII) + body
    }

    private fun png(vararg chunks: Pair<String, ByteArray>): ByteArray {
        val sig = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        return chunks.fold(sig) { acc, (tag, data) ->
            // CRC 는 우리 판별기가 보지 않는다. 0 으로 채운다.
            acc + be32(data.size) + tag.toByteArray(Charsets.US_ASCII) + data + be32(0)
        }
    }

    @Test
    fun `정지 WebP 는 움직이지 않는 것으로 본다`() {
        val f = tmp("still", webp("VP8 " to ByteArray(20)))
        assertFalse(ImageFormats.isAnimatedWebp(f))
    }

    @Test
    fun `ANIM 청크가 있으면 움직이는 WebP 다`() {
        // 실제 애니메이션 WebP 의 모양: VP8X(플래그) → ANIM → ANMF 들.
        val vp8x = ByteArray(10).also { it[0] = 0x02 } // 애니메이션 비트
        val f = tmp("anim", webp("VP8X" to vp8x, "ANIM" to ByteArray(6), "ANMF" to ByteArray(20)))
        assertTrue(ImageFormats.isAnimatedWebp(f))
    }

    @Test
    fun `VP8X 플래그만으로도 잡는다`() {
        // ANIM 이 뒤쪽에 있어도 VP8X 의 비트 하나로 먼저 알 수 있다.
        val vp8x = ByteArray(10).also { it[0] = 0x02 }
        val f = tmp("flag", webp("VP8X" to vp8x, "VP8L" to ByteArray(8)))
        assertTrue(ImageFormats.isAnimatedWebp(f))
    }

    @Test
    fun `WebP 가 아니면 거짓이다`() {
        assertFalse(ImageFormats.isAnimatedWebp(tmp("jpg", ByteArray(64))))
        assertFalse(ImageFormats.isAnimatedWebp(tmp("tiny", ByteArray(4))))
        assertFalse(ImageFormats.isAnimatedWebp(File("없는파일.webp")))
    }

    @Test
    fun `깨진 길이에 끝없이 돌지 않는다`() {
        // 길이가 음수로 읽히는 청크. 여기서 멈춰야 한다.
        val bad = "RIFF".toByteArray(Charsets.US_ASCII) + le32(1000) +
            "WEBP".toByteArray(Charsets.US_ASCII) +
            "VP8X".toByteArray(Charsets.US_ASCII) + le32(-1)
        assertFalse(ImageFormats.isAnimatedWebp(tmp("bad", bad)))
    }

    @Test
    fun `acTL 이 있으면 APNG 이다`() {
        val f = tmp("apng", png("IHDR" to ByteArray(13), "acTL" to ByteArray(8), "IDAT" to ByteArray(10)))
        assertTrue(ImageFormats.isApng(f))
    }

    @Test
    fun `IDAT 를 먼저 만나면 정지 PNG 이다`() {
        // acTL 은 명세상 IDAT 앞에 와야 한다. IDAT 뒤의 acTL 은 유효하지 않으므로
        // 거기서 멈추는 것이 옳다 — 끝까지 훑으면 깨진 파일에서 비용만 든다.
        val f = tmp("still", png("IHDR" to ByteArray(13), "IDAT" to ByteArray(10), "acTL" to ByteArray(8)))
        assertFalse(ImageFormats.isApng(f))
    }

    @Test
    fun `PNG 서명이 아니면 거짓이다`() {
        assertFalse(ImageFormats.isApng(tmp("notpng", ByteArray(64) { 1 })))
    }

    // ---- 바이트 입구 ------------------------------------------------------------

    /**
     * **파일과 바이트가 같은 답을 낸다.** 9단계가 바이트 입구를 더하면서 생긴 계약이다.
     *
     * 아카이브 안의 쪽에는 경로가 없어 바이트로만 물을 수 있고, 두 입구가 갈리면
     * 같은 그림이 뷰어에서는 움직이고 회전 게이트에서는 정지로 판정되는 일이 생긴다.
     */
    @Test
    fun `바이트와 파일이 같은 답을 낸다`() {
        val cases = listOf(
            webp("VP8 " to ByteArray(20)) to false,
            webp("ANIM" to ByteArray(6)) to true,
            webp("VP8X" to (byteArrayOf(0x02) + ByteArray(9))) to true,
            webp("VP8X" to (byteArrayOf(0x10) + ByteArray(9))) to false,
        )
        for ((bytes, expected) in cases) {
            assertEquals(expected, ImageFormats.isAnimatedWebp(tmp("both", bytes)), "파일")
            assertEquals(expected, ImageFormats.isAnimatedWebp(bytes), "바이트")
        }
    }

    @Test
    fun `APNG 도 바이트에서 같다`() {
        val a = png("IHDR" to ByteArray(13), "acTL" to ByteArray(8), "IDAT" to ByteArray(4))
        val s = png("IHDR" to ByteArray(13), "IDAT" to ByteArray(4))
        assertTrue(ImageFormats.isApng(a))
        assertTrue(ImageFormats.isApng(a))
        assertTrue(ImageFormats.isApng(a, 0, a.size))
        assertFalse(ImageFormats.isApng(s, 0, s.size))
    }

    /** 부분 배열로 물어도 된다 — 버퍼를 잘라 복사하지 않으려는 것이다. */
    @Test
    fun `앞뒤에 다른 바이트가 붙어 있어도 본다`() {
        val inner = webp("ANIM" to ByteArray(6))
        val padded = ByteArray(7) { 0x5A } + inner + ByteArray(11) { 0x5A }
        assertTrue(ImageFormats.isAnimatedWebp(padded, 7, inner.size))
        // 범위를 벗어나면 조용히 거짓이다. 예외를 던지지 않는다.
        assertFalse(ImageFormats.isAnimatedWebp(padded, 7, padded.size))
        assertFalse(ImageFormats.isAnimatedWebp(padded, -1, 4))
    }

    @Test
    fun `GIF 는 매직으로 가른다`() {
        assertTrue(ImageFormats.isGif("GIF89a".toByteArray(Charsets.US_ASCII) + ByteArray(20)))
        assertTrue(ImageFormats.isGif("GIF87a".toByteArray(Charsets.US_ASCII) + ByteArray(20)))
        assertFalse(ImageFormats.isGif("GIF88a".toByteArray(Charsets.US_ASCII) + ByteArray(20)))
        assertFalse(ImageFormats.isGif(ByteArray(3)))
    }

    /**
     * **APNG 은 '틀 수 있는' 애니메이션이 아니다.**
     *
     * 움직이는 파일이지만 플랫폼이 첫 장면만 준다. 이 둘을 한 함수로 합치면 화면이
     * "움직입니다" 라고 말해 놓고 멈춘 그림을 보여 주게 된다.
     */
    @Test
    fun `APNG 은 틀 수 있는 것으로 세지 않는다`() {
        val a = png("IHDR" to ByteArray(13), "acTL" to ByteArray(8), "IDAT" to ByteArray(4))
        assertTrue(ImageFormats.isApng(a))
        assertFalse(ImageFormats.playsAnimated(a))

        val g = "GIF89a".toByteArray(Charsets.US_ASCII) + ByteArray(20)
        assertTrue(ImageFormats.playsAnimated(g))
        assertTrue(ImageFormats.playsAnimated(webp("ANIM" to ByteArray(6))))
        assertFalse(ImageFormats.playsAnimated(webp("VP8 " to ByteArray(20))))
    }
}
