package io.github.donggi.iroiroviewer.io

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 새 파일의 이름(`NewFiles.derivedName`)·잘라 저장의 형식 고르기(`ImageSaver.outputFor`)·구간의 시각 꼴(`ClipTime`).
 */
class NewFilesAndSaverTest {

    @Test
    fun `새 이름은 원본 이름에 꼬리와 확장자를 붙인다`() {
        assertEquals("사진_crop.jpg", NewFiles.derivedName("사진.jpg", "crop", "jpg"))
        assertEquals("a.b_clip.mp4", NewFiles.derivedName("a.b.mkv", "clip", "mp4"))
        assertEquals("README_crop.png", NewFiles.derivedName("README", "crop", "png"))
        // 점으로 시작하는 이름은 확장자가 아니라 이름이다.
        assertEquals(".hidden_crop.png", NewFiles.derivedName(".hidden", "crop", "png"))
    }

    @Test
    fun `긴 이름은 앞부분만 잘라 꼬리를 지킨다`() {
        val long = "가".repeat(120) + ".jpg" // 360바이트 + 확장자
        val name = NewFiles.derivedName(long, "crop", "jpg")
        assertTrue(name.toByteArray().size <= PathRules.MAX_NAME_BYTES, "255바이트 안이어야 한다")
        assertTrue(name.endsWith("_crop.jpg"), "꼬리와 확장자가 남아야 한다: $name")
        assertTrue(name.startsWith("가가가"))
        // 글자가 반으로 잘리지 않는다(UTF-8 로 되돌려 같아야 한다).
        assertEquals(name, String(name.toByteArray(), Charsets.UTF_8))
    }

    @Test
    fun `확장자는 소문자로`() {
        assertEquals("jpg", NewFiles.extensionOf("A.JPG"))
        assertEquals("", NewFiles.extensionOf("README"))
        assertEquals("", NewFiles.extensionOf(".nomedia"))
    }

    @Test
    fun `잘라 저장의 형식 — 원본과 같게, 못 쓰면 가장 가까운 것으로`() {
        val jpeg = ImageSaver.outputFor("image/jpeg", "jpeg", hasAlpha = false, losslessWebp = false)
        assertEquals(ImageSaver.Encoding.JPEG, jpeg.encoding)
        assertEquals("jpeg", jpeg.extension, "JPEG 의 다른 이름은 원본 확장자를 지킨다")
        assertTrue(jpeg.copyDates)
        assertEquals("jpg", ImageSaver.outputFor("image/jpeg", "dat", false, false).extension)

        assertEquals(ImageSaver.Encoding.PNG, ImageSaver.outputFor("image/png", "png", true, false).encoding)
        assertEquals(ImageSaver.Encoding.WEBP_LOSSLESS, ImageSaver.outputFor("image/webp", "webp", false, true).encoding)
        assertEquals(ImageSaver.Encoding.WEBP_LOSSY, ImageSaver.outputFor("image/webp", "webp", false, false).encoding)
        // 플랫폼이 그 형식으로 쓰지 못하는 무손실 형식은 PNG.
        assertEquals(ImageSaver.Encoding.PNG, ImageSaver.outputFor("image/gif", "gif", false, false).encoding)
        assertEquals(ImageSaver.Encoding.PNG, ImageSaver.outputFor("image/bmp", "bmp", false, false).encoding)
        // 플랫폼 디코더는 아이콘을 `image/x-ico` 로 말한다 — 그 이름을 몰라 아이콘이 JPEG(투명이 검게)이 됐다.
        assertEquals(ImageSaver.Encoding.PNG, ImageSaver.outputFor("image/x-ico", "ico", false, false).encoding)
        // HEIC 는 JPEG 로(날짜를 옮긴다), 투명하면 PNG 로.
        val heic = ImageSaver.outputFor("image/heif", "heic", false, false)
        assertEquals(ImageSaver.Encoding.JPEG, heic.encoding)
        assertEquals("jpg", heic.extension)
        assertTrue(heic.copyDates)
        val alpha = ImageSaver.outputFor("image/avif", "avif", true, false)
        assertEquals(ImageSaver.Encoding.PNG, alpha.encoding)
        assertFalse(alpha.copyDates)
    }

    @Test
    fun `무손실 WebP 는 VP8L 청크로 안다`() {
        fun webp(chunk: String): File = File.createTempFile("webp", ".webp").also { f ->
            val body = chunk.toByteArray() + byteArrayOf(4, 0, 0, 0, 1, 2, 3, 4)
            val size = 4 + body.size
            f.writeBytes(
                "RIFF".toByteArray() + byteArrayOf(size.toByte(), 0, 0, 0) + "WEBP".toByteArray() + body
            )
        }
        val lossless = webp("VP8L")
        val lossy = webp("VP8 ")
        try {
            assertTrue(ImageSaver.isLosslessWebp(lossless))
            assertFalse(ImageSaver.isLosslessWebp(lossy))
        } finally {
            lossless.delete()
            lossy.delete()
        }
    }

    /**
     * 잘라 저장이 날짜를 입힌 뒤 임시본이 온전한가 — `saveAttributes` 는 SOI 뒤에 EXIF 를 끼우고 나머지를 그대로 옮기므로 온전하면
     * 끝의 바이트가 같다. 되쓰다 잘렸으면(던지지 않고 잘린 채 남는 길도 있다) 끝이 다르다 — 그때는 날짜 없이 다시 쓴다.
     */
    @Test
    fun `날짜를 입힌 임시본은 끝의 바이트로 온전한지 안다`() {
        // 가짜 JPEG — SOI + 머리 흉내 + '화소' 10,000바이트 + EOI.
        val body = ByteArray(10_000) { (it * 7 + 3).toByte() }
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + ByteArray(300) { 0x11 } + body + byteArrayOf(0xFF.toByte(), 0xD9.toByte())
        val f = File.createTempFile("crop", ".part")
        try {
            f.writeBytes(jpeg)
            val tail = ImageSaver.JpegTail.of(f)!!
            assertTrue(tail.keptIn(f), "손대지 않았으면 온전하다(날짜를 옮기지 못한 것은 실패가 아니다)")
            // SOI 뒤에 EXIF(APP1)를 끼운 꼴 — 온전하다.
            val app1 = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), 0, 40) + ByteArray(38) { 0x45 }
            f.writeBytes(jpeg.copyOfRange(0, 2) + app1 + jpeg.copyOfRange(2, jpeg.size))
            assertTrue(tail.keptIn(f))
            // 다시 쓰다 마지막 버퍼를 잃었다 — 길어졌어도(EXIF 만큼) 끝이 다르다.
            val rewritten = f.readBytes()
            f.writeBytes(rewritten.copyOfRange(0, rewritten.size - 30))
            assertFalse(tail.keptIn(f))
            // 반쯤 잘렸다·텅 비었다.
            f.writeBytes(jpeg.copyOfRange(0, jpeg.size / 2))
            assertFalse(tail.keptIn(f))
            f.writeBytes(ByteArray(0))
            assertFalse(tail.keptIn(f))
            // 길이는 같은데 끝이 다르다.
            f.writeBytes(jpeg.copyOfRange(0, jpeg.size - 1) + byteArrayOf(0))
            assertFalse(tail.keptIn(f))
        } finally {
            f.delete()
        }
        // 읽을 것이 없으면 뜨지 않는다(부르는 쪽은 날짜를 입히지 않고 그대로 놓는다).
        val tiny = File.createTempFile("crop", ".part")
        try {
            tiny.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
            assertEquals(null, ImageSaver.JpegTail.of(tiny))
        } finally {
            tiny.delete()
        }
    }

    @Test
    fun `결과 한 장의 화소 상한은 힙의 셋째 몫과 캔버스 상한 가운데 작은 것`() {
        // 힙 192 MB → 64 MB / 4 = 16,777,216 화소.
        assertEquals(192L * 1024 * 1024 / 3 / 4, ImageSaver.maxPixels(192L * 1024 * 1024))
        // 힙 1 GB → 캔버스 상한(100 MiB)이 이긴다.
        assertEquals(100L * 1024 * 1024 / 4, ImageSaver.maxPixels(1024L * 1024 * 1024))
    }

    @Test
    fun `구간의 시각은 10분의 1초까지`() {
        assertEquals("0:00.0", ClipTime.format(0))
        assertEquals("0:12.3", ClipTime.format(12_345))
        assertEquals("1:02.0", ClipTime.format(62_099))
        assertEquals("1:02:03.4", ClipTime.format(3_723_400))
        assertEquals("0:00.0", ClipTime.format(-5))
    }
}
