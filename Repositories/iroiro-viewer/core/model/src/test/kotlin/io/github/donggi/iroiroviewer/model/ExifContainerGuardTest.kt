package io.github.donggi.iroiroviewer.model

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.util.zip.CRC32
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** ExifInterface 에 넘기기 전의 덩이 머리 검사. 표본은 모두 손으로 짓는다(정상 셋은 통과, 길이·남은 크기·잘림은 거절). */
class ExifContainerGuardTest {

    // ---- PNG ----

    private fun pngChunk(d: DataOutputStream, type: String, data: ByteArray, declared: Long = data.size.toLong()) {
        d.writeInt(declared.toInt())
        val t = type.toByteArray(Charsets.ISO_8859_1)
        d.write(t)
        d.write(data)
        d.writeInt(CRC32().apply { update(t); update(data) }.value.toInt())
    }

    private fun png(build: (DataOutputStream) -> Unit): ByteArray {
        val out = ByteArrayOutputStream()
        val d = DataOutputStream(out)
        d.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        pngChunk(d, "IHDR", byteArrayOf(0, 0, 0, 1, 0, 0, 0, 1, 8, 2, 0, 0, 0))
        build(d)
        return out.toByteArray()
    }

    private fun pngEnd(d: DataOutputStream) = pngChunk(d, "IEND", ByteArray(0))

    @Test
    fun 정상_PNG_는_통과한다() {
        assertTrue(ExifContainerGuard.allows(png { pngChunk(it, "IDAT", ByteArray(20)); pngEnd(it) }))
        assertTrue(ExifContainerGuard.allows(png { pngChunk(it, "eXIf", ByteArray(64)); pngChunk(it, "IDAT", ByteArray(20)); pngEnd(it) }))
        assertTrue(ExifContainerGuard.allows(png { pngChunk(it, "tEXt", "Comment\u0000hi".toByteArray()); pngEnd(it) }))
    }

    @Test
    fun 검토가_재현한_57바이트_PNG_는_거절한다() {
        // IHDR 뒤에 길이 0x7FFFFFF0 의 eXIf — 몸이 16바이트뿐이다.
        val evil = png { d ->
            d.writeInt(0x7FFFFFF0)
            d.write("eXIf".toByteArray())
            d.write(ByteArray(16))
        }
        assertTrue(evil.size == 57)
        assertFalse(ExifContainerGuard.allows(evil))
    }

    @Test
    fun 남은_크기를_넘는_덩이는_거절한다() {
        // 메타데이터가 아닌 덩이라도 적힌 길이가 파일보다 길면 깨진 그릇이다.
        assertFalse(ExifContainerGuard.allows(png { d -> d.writeInt(1000); d.write("IDAT".toByteArray()); d.write(ByteArray(10)) }))
    }

    private val xmpKeyword = "XML:com.adobe.xmp".toByteArray() + ByteArray(5)

    @Test
    fun ExifInterface_가_잡는_덩이만_상한을_넘으면_거절한다() {
        // 192 MB 를 넘지 않아도 — 실제로 그만큼 있는 파일이면 할당이 성공해 다른 스레드가 죽는다. 상한(8 MiB)으로 막는다.
        val big = (ExifContainerGuard.MAX_METADATA + 1).toInt()
        assertFalse(ExifContainerGuard.allows(png { pngChunk(it, "eXIf", ByteArray(big)); pngEnd(it) }))
        // XMP iTXt 는 `길이 − 22` 를 잡는다.
        assertFalse(ExifContainerGuard.allows(png { pngChunk(it, "iTXt", xmpKeyword + ByteArray(big)); pngEnd(it) }))
        // 그 밖의 글 덩이·그림 덩이는 범위만 본다 — 생성 이미지의 큰 JSON(tEXt·다른 iTXt)을 거절하지 않는다.
        assertTrue(ExifContainerGuard.allows(png { pngChunk(it, "tEXt", ByteArray(big)); pngEnd(it) }))
        assertTrue(ExifContainerGuard.allows(png { pngChunk(it, "iTXt", "parameters".toByteArray() + ByteArray(big)); pngEnd(it) }))
        assertTrue(ExifContainerGuard.allows(png { pngChunk(it, "IDAT", ByteArray(big)); pngEnd(it) }))
    }

    @Test
    fun eXIf_뒤의_큰_XMP_iTXt_도_본다() {
        // 재검토가 재현한 꼴 — 작은 eXIf 뒤에 길이 0x7FFFFFF0 의 XMP iTXt. ExifInterface 는 eXIf 에서 멈추지 않고 XMP 까지 찾는다.
        val bypass = png { d ->
            pngChunk(d, "eXIf", byteArrayOf('M'.code.toByte(), 'M'.code.toByte(), 0, 42, 0, 0, 0, 8, 0, 0, 0, 0, 0, 0))
            d.writeInt(0x7FFFFFF0)
            d.write("iTXt".toByteArray())
            d.write(xmpKeyword)
            d.write(ByteArray(16))
        }
        assertFalse(ExifContainerGuard.allows(bypass))
        // 범위 안이지만 상한을 넘는 XMP 가 eXIf 뒤에 있어도.
        val big = (ExifContainerGuard.MAX_METADATA + 1).toInt()
        assertFalse(ExifContainerGuard.allows(png { pngChunk(it, "eXIf", ByteArray(14)); pngChunk(it, "iTXt", xmpKeyword + ByteArray(big)); pngEnd(it) }))
        // 둘을 다 찾았으면 그 뒤는 ExifInterface 도 읽지 않는다 — 여기도 멈춘다.
        assertTrue(ExifContainerGuard.allows(png { d ->
            pngChunk(d, "eXIf", ByteArray(14))
            pngChunk(d, "iTXt", xmpKeyword + ByteArray(4))
            d.writeInt(0x7FFFFFF0)
            d.write("tEXt".toByteArray())
        }))
    }

    @Test
    fun EXIF_가_없는_PNG_는_열_까닭이_없다고_답한다() {
        assertEquals(ExifContainerGuard.Verdict.NO_EXIF, ExifContainerGuard.inspect(png { pngChunk(it, "IDAT", ByteArray(20)); pngEnd(it) }))
        assertEquals(ExifContainerGuard.Verdict.SAFE, ExifContainerGuard.inspect(png { pngChunk(it, "eXIf", ByteArray(14)); pngEnd(it) }))
        assertEquals(ExifContainerGuard.Verdict.SAFE, ExifContainerGuard.inspect(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())))
    }

    @Test
    fun IEND_없이_덩이_경계에서_끝난_PNG_는_받고_덩이_한가운데서_잘린_것은_거절한다() {
        val whole = png { pngChunk(it, "IDAT", ByteArray(20)); pngEnd(it) }
        // 덜 받은 파일 — ExifInterface 도 파일 끝에서 멈춘다.
        assertTrue(ExifContainerGuard.allows(whole.copyOf(whole.size - 12)))
        assertFalse(ExifContainerGuard.allows(whole.copyOf(whole.size - 15))) // IDAT 의 끝(CRC)이 잘렸다
        assertFalse(ExifContainerGuard.allows(whole.copyOf(whole.size - 8))) // IEND 머리가 잘렸다
    }

    // ---- WebP ----

    private fun webp(chunks: List<Pair<String, ByteArray>>, riffDelta: Int = 0, declared: Map<String, Long> = emptyMap()): ByteArray {
        val body = ByteArrayOutputStream()
        for ((type, data) in chunks) {
            body.write(type.toByteArray(Charsets.ISO_8859_1))
            body.write(le(declared[type] ?: data.size.toLong()))
            body.write(data)
            if (data.size % 2 == 1) body.write(0)
        }
        val b = body.toByteArray()
        val out = ByteArrayOutputStream()
        out.write("RIFF".toByteArray())
        out.write(le((4 + b.size + riffDelta).toLong()))
        out.write("WEBP".toByteArray())
        out.write(b)
        return out.toByteArray()
    }

    private fun le(v: Long) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())

    @Test
    fun 정상_WebP_는_통과한다() {
        assertEquals(
            ExifContainerGuard.Verdict.SAFE,
            ExifContainerGuard.inspect(webp(listOf("VP8X" to ByteArray(10), "VP8 " to ByteArray(31), "EXIF" to ByteArray(40)))),
        )
        assertEquals(ExifContainerGuard.Verdict.NO_EXIF, ExifContainerGuard.inspect(webp(listOf("VP8 " to ByteArray(31)))))
        // XMP 청크는 ExifInterface 가 잡지 않는다 — 범위만 본다.
        val big = (ExifContainerGuard.MAX_METADATA + 2).toInt()
        assertTrue(ExifContainerGuard.allows(webp(listOf("VP8 " to ByteArray(10), "XMP " to ByteArray(big)))))
    }

    @Test
    fun 마지막_청크의_채움_바이트가_빠진_WebP_는_받는다() {
        val padded = webp(listOf("VP8 " to ByteArray(31)))
        // RIFF 크기는 채움을 셈한 그대로, 파일에서는 마지막 한 바이트(채움)가 빠졌다.
        assertTrue(ExifContainerGuard.allows(padded.copyOf(padded.size - 1)))
        // 두 바이트가 빠지면 청크의 몸이 잘린 것이다.
        assertFalse(ExifContainerGuard.allows(padded.copyOf(padded.size - 2)))
    }

    @Test
    fun WebP_의_EXIF_길이가_남은_크기를_넘거나_상한을_넘으면_거절한다() {
        assertFalse(ExifContainerGuard.allows(webp(listOf("EXIF" to ByteArray(16)), declared = mapOf("EXIF" to 0x7FFFFFF0L))))
        val big = (ExifContainerGuard.MAX_METADATA + 2).toInt()
        assertFalse(ExifContainerGuard.allows(webp(listOf("VP8 " to ByteArray(10), "EXIF" to ByteArray(big)))))
    }

    @Test
    fun RIFF_크기가_파일보다_크거나_청크가_잘린_WebP_는_거절한다() {
        assertFalse(ExifContainerGuard.allows(webp(listOf("VP8 " to ByteArray(10)), riffDelta = 100)))
        val whole = webp(listOf("VP8 " to ByteArray(10), "EXIF" to ByteArray(20)))
        // RIFF 크기를 그대로 둔 채 끝을 자르면 RIFF 가 파일 밖을 가리킨다.
        assertFalse(ExifContainerGuard.allows(whole.copyOf(whole.size - 5)))
    }

    // ---- JPEG·그 밖 ----

    @Test
    fun JPEG_와_모르는_그릇은_통과한다() {
        // APP1 의 길이는 16비트라 할당이 64 KiB 를 넘지 못한다.
        assertTrue(ExifContainerGuard.allows(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE1.toByte(), 0xFF.toByte(), 0xFF.toByte())))
        assertTrue(ExifContainerGuard.allows(byteArrayOf(1, 2, 3)))
        assertTrue(ExifContainerGuard.allows(ByteArray(0)))
    }

    @Test
    fun 바이트_토막과_파일이_같은_답을_준다() {
        val evil = png { d -> d.writeInt(0x7FFFFFF0); d.write("eXIf".toByteArray()); d.write(ByteArray(16)) }
        // 앞뒤에 다른 바이트가 붙은 배열의 한 토막(만화 쪽).
        val framed = ByteArray(5) + evil + ByteArray(7)
        assertFalse(ExifContainerGuard.allows(framed, 5, evil.size))
        val good = png { pngEnd(it) }
        assertTrue(ExifContainerGuard.allows(ByteArray(3) + good, 3, good.size))
        // 토막의 범위가 배열 밖이면 거절.
        assertFalse(ExifContainerGuard.allows(good, 2, good.size))

        val f = Files.createTempFile("guard", ".png").toFile()
        try {
            f.writeBytes(evil)
            assertFalse(ExifContainerGuard.allows(f))
            f.writeBytes(good)
            assertTrue(ExifContainerGuard.allows(f))
            // 이미 연 채널 — 위치를 지정해 읽으므로 채널의 위치는 그대로다(곧이어 같은 서술자로 읽는 쪽이 처음부터 읽는다).
            java.io.FileInputStream(f).use { input ->
                assertEquals(ExifContainerGuard.Verdict.NO_EXIF, ExifContainerGuard.inspect(input.channel))
                assertEquals(0L, input.channel.position())
                assertEquals(0x89, input.read())
            }
        } finally {
            f.delete()
        }
    }
}
