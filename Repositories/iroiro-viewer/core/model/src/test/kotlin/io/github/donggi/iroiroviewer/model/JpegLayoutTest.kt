package io.github.donggi.iroiroviewer.model

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** JPEG 의 짜임(`JpegLayout`)과 EXIF 밖의 위치(`MetadataLocation.fileHasLocation`) — 모션 포토·IPTC·PNG 글 덩이. */
class JpegLayoutTest {

    private val dir: File = Files.createTempDirectory("jl").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun b(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    private fun seg(marker: Int, payload: ByteArray): ByteArray {
        val len = payload.size + 2
        return b(0xFF, marker, len shr 8, len and 0xFF) + payload
    }

    private val soi = b(0xFF, 0xD8)
    private val eoi = b(0xFF, 0xD9)
    private val app1 = seg(0xE1, "Exif\u0000\u0000II*\u0000".toByteArray(Charsets.ISO_8859_1))
    private val app13 = seg(0xED, "Photoshop 3.0\u00008BIM".toByteArray(Charsets.ISO_8859_1) + b(0x04, 0x04, 0, 0, 0, 0, 0, 9, 0x1C, 0x02, 0x5A, 0, 5) + "Seoul".toByteArray())
    private val dqt = seg(0xDB, ByteArray(65))
    private val sos = seg(0xDA, b(1, 1, 0, 0, 63, 0))

    /** 엔트로피 자료 — `FF 00`(채움) 뒤의 `D9`, RST, `FF FF` 채움을 품는다. 이것들을 EOI 로 읽으면 안 된다. */
    private val entropy = b(0x12, 0xFF, 0x00, 0xD9, 0x34, 0xFF, 0xD0, 0x56, 0xFF, 0xFF, 0xD1, 0x78)

    /** 모션 포토 꼴의 덧붙은 MP4 — `udta` 안의 `©xyz` 에 ISO 6709 위치. */
    private val trailer = b(0, 0, 0, 0x18) + "ftypmp42".toByteArray() + b(0, 0, 0, 0x1C, 0xA9) + "xyz".toByteArray() + "+37.5665+126.9780/".toByteArray()

    private fun file(name: String, bytes: ByteArray) = File(dir, name).apply { writeBytes(bytes) }

    @Test
    fun `엔트로피 자료의 채움과 RST 를 지나 주 그림의 끝을 찾는다`() {
        val main = soi + app1 + app13 + dqt + sos + entropy + eoi
        val layout = JpegLayout.read(main + trailer)!!
        assertEquals(main.size.toLong(), layout.mainEnd)
        assertTrue(layout.hasTrailer)
        assertEquals(listOf(0xE1, 0xED, 0xDB), layout.headers.map { it.marker })
        assertEquals((soi.size + app1.size + app13.size + dqt.size).toLong(), layout.scanStart)
        assertFalse(JpegLayout.read(main)!!.hasTrailer)
    }

    /** 점진 JPEG — 스캔이 여럿이고 그 사이에 DHT 가 온다. 마지막 스캔 뒤의 EOI 가 끝이다. */
    @Test
    fun `스캔이 여럿인 JPEG`() {
        val dht = seg(0xC4, ByteArray(20))
        val main = soi + dqt + sos + entropy + dht + sos + entropy + eoi
        val layout = JpegLayout.read(main + trailer)!!
        assertEquals(main.size.toLong(), layout.mainEnd)
        assertEquals(listOf(0xDB), layout.headers.map { it.marker })
    }

    @Test
    fun `짜임이 어긋나면 null`() {
        assertNull(JpegLayout.read(soi + dqt + sos + entropy)) // EOI 가 없다
        assertNull(JpegLayout.read(soi + b(0xFF, 0xE1, 0x40, 0x00, 1, 2))) // 덩이 길이가 파일을 넘는다
        assertNull(JpegLayout.read("PNG.....".toByteArray()))
        assertNull(JpegLayout.read(soi + dqt + eoi)) // 스캔 없이 끝났다
    }

    /** 사본은 SOI · APP13 을 뺀 머리 · 첫 SOS 부터 EOI 까지 — 덧붙은 영상과 IPTC 가 빠지고 위치 검사를 지난다. 원본은 위치가 있다. */
    @Test
    fun `공유 사본은 덧붙은 영상과 IPTC 를 뺀다`() {
        val src = file("motion.jpg", soi + app1 + app13 + dqt + sos + entropy + eoi + trailer)
        assertTrue(MetadataLocation.fileHasLocation(src))
        val out = ByteArrayOutputStream()
        JpegLayout.copyForShare(src, out)
        assertContentEquals(soi + app1 + dqt + sos + entropy + eoi, out.toByteArray())
        assertFalse(MetadataLocation.fileHasLocation(file("copy.jpg", out.toByteArray())))
    }

    /** 덧붙은 것도 APP13 도 없으면 글자 그대로. 짜임을 못 읽는 것도 글자 그대로(뒤의 검사가 거른다). */
    @Test
    fun `고칠 것이 없으면 그대로 옮긴다`() {
        val plain = soi + app1 + dqt + sos + entropy + eoi
        val out = ByteArrayOutputStream()
        JpegLayout.copyForShare(file("plain.jpg", plain), out)
        assertContentEquals(plain, out.toByteArray())
        val broken = soi + dqt + sos + entropy
        val out2 = ByteArrayOutputStream()
        JpegLayout.copyForShare(file("broken.jpg", broken), out2)
        assertContentEquals(broken, out2.toByteArray())
    }

    /** 짜임을 읽지 못한 JPEG(EOI 없음)은 위치가 있는 것으로 — 사본은 보내지 않고, 원본 자리는 '지울 수 없는 형식' 으로 건너뛴다. */
    @Test
    fun `짜임을 못 읽은 JPEG 은 닫는 쪽으로`() {
        val broken = file("broken.jpg", soi + app1 + dqt + sos + entropy)
        assertTrue(JpegLayout.isUnreadableJpeg(broken))
        assertTrue(MetadataLocation.fileHasLocation(broken))
        assertFalse(JpegLayout.isUnreadableJpeg(file("ok.jpg", soi + app1 + dqt + sos + entropy + eoi)))
        assertFalse(JpegLayout.isUnreadableJpeg(file("p.png", pngSig + ihdr + iend)))
    }

    /** IPTC 의 도시 레코드(`1C 02 5A`)만으로도 위치가 있다. 위치가 아닌 레코드(설명 `1C 02 78`)는 아니다. */
    @Test
    fun `IPTC 의 위치 레코드`() {
        assertTrue(MetadataLocation.fileHasLocation(file("iptc.jpg", soi + app13 + dqt + sos + entropy + eoi)))
        val caption = seg(0xED, "Photoshop 3.0\u0000".toByteArray(Charsets.ISO_8859_1) + b(0x1C, 0x02, 0x78, 0, 2) + "hi".toByteArray())
        assertFalse(MetadataLocation.fileHasLocation(file("caption.jpg", soi + caption + dqt + sos + entropy + eoi)))
    }

    /** 짧은 표지(`city`)는 머리 덩이에서만 — XMP 의 `photoshop:City` 는 걸리고, 그림 자료 속의 우연한 `city` 는 걸리지 않는다. */
    @Test
    fun `짧은 표지는 메타데이터 자리에서만`() {
        val xmp = seg(0xE1, "http://ns.adobe.com/xap/1.0/\u0000<x photoshop:City=\"Seoul\"/>".toByteArray(Charsets.ISO_8859_1))
        assertTrue(MetadataLocation.fileHasLocation(file("xmp.jpg", soi + xmp + dqt + sos + entropy + eoi)))
        val noisy = b(0x11) + "CiTy".toByteArray() + b(0x22)
        assertFalse(MetadataLocation.fileHasLocation(file("noisy.jpg", soi + dqt + sos + noisy + eoi)))
        // 긴 이름은 어디서든.
        val longInData = b(0x11) + "Iptc4xmpExt:LocationShown".toByteArray() + b(0x22)
        assertTrue(MetadataLocation.fileHasLocation(file("long.jpg", soi + dqt + sos + longInData + eoi)))
    }

    private fun chunk(type: String, data: ByteArray): ByteArray {
        val n = data.size
        return b(n ushr 24, (n shr 16) and 0xFF, (n shr 8) and 0xFF, n and 0xFF) + type.toByteArray() + data + ByteArray(4)
    }

    private val pngSig = b(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private val ihdr = chunk("IHDR", ByteArray(13))
    private val iend = chunk("IEND", ByteArray(0))

    /** PNG — 압축된 글 덩이(`zTXt`·압축 `iTXt`)는 볼 수 없어 위치가 있는 것으로 친다. 풀린 글 덩이는 짧은 표지까지 본다. 그 밖은 없다. */
    @Test
    fun `PNG 의 글 덩이`() {
        assertTrue(MetadataLocation.fileHasLocation(file("z.png", pngSig + ihdr + chunk("zTXt", "Raw profile type iptc\u0000\u0000xx".toByteArray()) + iend)))
        assertTrue(MetadataLocation.fileHasLocation(file("ic.png", pngSig + ihdr + chunk("iTXt", "XML:com.adobe.xmp\u0000\u0001\u0000xx".toByteArray()) + iend)))
        assertTrue(MetadataLocation.fileHasLocation(file("t.png", pngSig + ihdr + chunk("tEXt", "Comment\u0000city hall".toByteArray()) + iend)))
        assertFalse(MetadataLocation.fileHasLocation(file("plain.png", pngSig + ihdr + chunk("tEXt", "Software\u0000Iro".toByteArray()) + iend)))
    }
}
