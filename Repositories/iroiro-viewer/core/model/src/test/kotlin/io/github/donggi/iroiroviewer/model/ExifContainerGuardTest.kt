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
        // APP1 `Exif` 가 없는 JPEG 도 열 까닭이 없다.
        assertEquals(ExifContainerGuard.Verdict.NO_EXIF, ExifContainerGuard.inspect(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())))
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
    fun 덩이가_파일_밖인_JPEG_와_모르는_그릇은_통과한다() {
        // APP1 의 길이가 파일보다 길다 — ExifInterface 는 그 세그먼트를 읽다 EOF 로 멈춘다(TIFF 를 걷지 않는다).
        assertTrue(ExifContainerGuard.allows(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE1.toByte(), 0xFF.toByte(), 0xFF.toByte())))
        assertTrue(ExifContainerGuard.allows(byteArrayOf(1, 2, 3)))
        assertTrue(ExifContainerGuard.allows(ByteArray(0)))
        // HEIF(ftyp)는 바이트만으로는 판정할 수 없다 — 추출기에 물을 파일이 없으니 넘기지 않는다.
        val heic = byteArrayOf(0, 0, 0, 24) + "ftypheic".toByteArray() + ByteArray(12)
        assertEquals(ExifContainerGuard.Verdict.HEIF, ExifContainerGuard.inspect(heic))
        assertFalse(ExifContainerGuard.allows(heic))
    }

    // ---- TIFF 블록(ExifInterface 의 readImageFileDirectory) ----

    /** 칸 하나 — 값은 4 바이트 칸에 그대로 들어간다(4 바이트를 넘는 자료면 오프셋). */
    private class E(val tag: Int, val type: Int, val count: Long, val value: Long)

    /**
     * 작은 TIFF 블록을 손으로 짓는다(리틀 엔디언 — [big] 이면 빅 엔디언). [ifds] 는 (자리, 칸들, 다음 IFD) — 첫 IFD 가 IFD0 이다. [data] 는
     * (자리, 바이트). 블록 길이는 [size].
     */
    private fun tiff(
        size: Int,
        ifds: List<Triple<Int, List<E>, Long>>,
        data: List<Pair<Int, ByteArray>> = emptyList(),
        big: Boolean = false,
    ): ByteArray {
        val b = java.nio.ByteBuffer.allocate(size).order(if (big) java.nio.ByteOrder.BIG_ENDIAN else java.nio.ByteOrder.LITTLE_ENDIAN)
        if (big) b.put('M'.code.toByte()).put('M'.code.toByte()) else b.put('I'.code.toByte()).put('I'.code.toByte())
        b.putShort(42).putInt(ifds.first().first)
        for ((at, entries, next) in ifds) {
            b.position(at)
            b.putShort(entries.size.toShort())
            for (e in entries) {
                b.putShort(e.tag.toShort()).putShort(e.type.toShort()).putInt(e.count.toInt())
                if ((e.type == 3 || e.type == 8) && e.count == 1L) b.putShort(e.value.toShort()).putShort(0) else b.putInt(e.value.toInt())
            }
            b.putInt(next.toInt())
        }
        for ((at, bytes) in data) {
            b.position(at)
            b.put(bytes)
        }
        return b.array()
    }

    /** TIFF 블록을 APP1 `Exif\0\0` 로 싼 JPEG(SOS·EOI 까지). */
    private fun jpegWith(vararg tiffs: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
        for (t in tiffs) {
            val len = 2 + 6 + t.size
            out.write(byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (len shr 8).toByte(), len.toByte()))
            out.write("Exif\u0000\u0000".toByteArray(Charsets.ISO_8859_1))
            out.write(t)
        }
        out.write(byteArrayOf(0xFF.toByte(), 0xDA.toByte(), 0, 2, 0x11, 0x22, 0xFF.toByte(), 0xD9.toByte()))
        return out.toByteArray()
    }

    /** 점검자가 재현한 꼴 — IFD0 에 XMP(0x02BC, BYTE) 한 칸, 개수 [count], 자료는 26 에. 34 바이트. */
    private fun xmpBomb(count: Long, big: Boolean = false) = tiff(34, listOf(Triple(8, listOf(E(0x02BC, 1, count, 26)), 0L)), big = big)

    private fun inspectJpeg(vararg t: ByteArray) = ExifContainerGuard.inspect(jpegWith(*t))

    @Test
    fun IFD_칸의_개수가_블록을_넘으면_JPEG_PNG_WebP_모두_거절한다() {
        for (big in listOf(false, true)) {
            val bomb = xmpBomb(200_000_000, big)
            assertEquals(ExifContainerGuard.Verdict.UNSAFE, inspectJpeg(bomb), "JPEG big=$big")
            assertFalse(ExifContainerGuard.allows(png { pngChunk(it, "eXIf", bomb); pngEnd(it) }), "PNG big=$big")
            assertFalse(ExifContainerGuard.allows(webp(listOf("EXIF" to bomb))), "WebP big=$big")
            // WebP 의 EXIF 앞에 `Exif\0\0` 이 붙은 꼴(ExifInterface 가 뗀다).
            assertFalse(ExifContainerGuard.allows(webp(listOf("EXIF" to "Exif\u0000\u0000".toByteArray(Charsets.ISO_8859_1) + bomb))), "WebP+id big=$big")
        }
        // 작은 개수는 통과한다 — 자료가 블록 밖이어도(26 + 9 > 34) ExifInterface 는 9 바이트를 잡고 EOF 로 멈출 뿐이다.
        assertEquals(ExifContainerGuard.Verdict.SAFE, inspectJpeg(xmpBomb(8)))
        assertEquals(ExifContainerGuard.Verdict.SAFE, inspectJpeg(xmpBomb(9)))
        // 예산의 경계 — 8 MiB 는 지나고 한 바이트 더는 거절.
        assertEquals(ExifContainerGuard.Verdict.SAFE, inspectJpeg(xmpBomb(ExifContainerGuard.MAX_METADATA)))
        assertEquals(ExifContainerGuard.Verdict.UNSAFE, inspectJpeg(xmpBomb(ExifContainerGuard.MAX_METADATA + 1)))
        // 부호 비트가 켜진 개수 — ExifInterface 는 개수 × 단위가 음수라 건너뛴다.
        assertEquals(ExifContainerGuard.Verdict.SAFE, inspectJpeg(xmpBomb(0x8000_0010L)))
    }

    @Test
    fun 내용이_TIFF_계열이면_이름과_상관없이_넘기지_않는다() {
        assertEquals(ExifContainerGuard.Verdict.UNSAFE, ExifContainerGuard.inspect(xmpBomb(200_000_000)))
        // 멀쩡한 TIFF 도 — 이 앱은 RAW·TIFF 를 ExifInterface 에 넘기지 않는다.
        assertEquals(ExifContainerGuard.Verdict.UNSAFE, ExifContainerGuard.inspect(xmpBomb(8)))
        assertEquals(ExifContainerGuard.Verdict.UNSAFE, ExifContainerGuard.inspect(xmpBomb(8, big = true)))
        assertEquals(ExifContainerGuard.Verdict.UNSAFE, ExifContainerGuard.inspect("IIRO".toByteArray() + ByteArray(20))) // ORF
        assertEquals(ExifContainerGuard.Verdict.UNSAFE, ExifContainerGuard.inspect("IIU\u0000".toByteArray() + ByteArray(20))) // RW2
        assertEquals(ExifContainerGuard.Verdict.UNSAFE, ExifContainerGuard.inspect("FUJIFILMCCD-RAW 0201".toByteArray() + ByteArray(80)))
        val f = Files.createTempFile("guard", ".jpg").toFile()
        try {
            f.writeBytes(xmpBomb(200_000_000))
            assertFalse(ExifContainerGuard.allows(f))
        } finally {
            f.delete()
        }
    }

    @Test
    fun 형식_7_은_본디_형식의_가장_큰_단위로_예산에_센다() {
        // 블록 안의 1,000,000 바이트를 가리키는 형식 7 칸 — 모르는 번호면 8 배(RATIONAL 일 수 있다)로 세어 8 MiB 를 넘는다.
        val n = 1_100_000
        val unknown = tiff(n + 64, listOf(Triple(8, listOf(E(0x9205, 7, n.toLong(), 40)), 0L)))
        assertFalse(ExifContainerGuard.allows(png { pngChunk(it, "eXIf", unknown); pngEnd(it) }))
        // MakerNote 처럼 어느 표에서나 UNDEFINED 인 태그는 1 바이트다.
        val makerNote = tiff(n + 64, listOf(Triple(8, listOf(E(0x927C, 7, n.toLong(), 40)), 0L)))
        assertTrue(ExifContainerGuard.allows(png { pngChunk(it, "eXIf", makerNote); pngEnd(it) }))
    }

    @Test
    fun 같은_자리를_가리키는_칸_수천_개는_예산이_막는다() {
        // 칸 하나하나는 블록 안(60,000 바이트)이지만 ExifInterface 는 칸마다 새로 잡는다 — 200 칸이면 12 MB.
        val entries = List(200) { E(0x02BC, 1, 60_000, 4000) }
        val block = tiff(64_000, listOf(Triple(8, entries, 0L)))
        assertEquals(ExifContainerGuard.Verdict.UNSAFE, ExifContainerGuard.inspect(png { pngChunk(it, "eXIf", block); pngEnd(it) }))
        // 열 칸이면 600 KB — 통과.
        val few = tiff(64_000, listOf(Triple(8, entries.take(10), 0L)))
        assertEquals(ExifContainerGuard.Verdict.SAFE, ExifContainerGuard.inspect(png { pngChunk(it, "eXIf", few); pngEnd(it) }))
    }

    @Test
    fun 포인터와_다음_IFD_를_따라가며_그_안의_칸도_본다() {
        // IFD0 → Exif(0x8769) → Interop(0xA005) 의 칸이 터무니없다.
        val viaInterop = tiff(
            80,
            listOf(
                Triple(8, listOf(E(0x8769, 4, 1, 26)), 0L),
                Triple(26, listOf(E(0xA005, 4, 1, 44)), 0L),
                Triple(44, listOf(E(0x0001, 2, 300_000_000, 70)), 0L),
            ),
        )
        assertEquals(ExifContainerGuard.Verdict.UNSAFE, inspectJpeg(viaInterop))
        // GPS(0x8825)·다음 IFD.
        val viaGps = tiff(60, listOf(Triple(8, listOf(E(0x8825, 4, 1, 26)), 0L), Triple(26, listOf(E(0x0002, 5, 50_000_000, 50)), 0L)))
        assertEquals(ExifContainerGuard.Verdict.UNSAFE, inspectJpeg(viaGps))
        val viaNext = tiff(60, listOf(Triple(8, listOf(E(0x0112, 3, 1, 6)), 26L), Triple(26, listOf(E(0x02BC, 1, 50_000_000, 50)), 0L)))
        assertEquals(ExifContainerGuard.Verdict.UNSAFE, inspectJpeg(viaNext))
        // SubIFD(0x014A).
        val viaSub = tiff(60, listOf(Triple(8, listOf(E(0x014A, 4, 1, 26)), 0L), Triple(26, listOf(E(0x02BC, 1, 50_000_000, 50)), 0L)))
        assertEquals(ExifContainerGuard.Verdict.UNSAFE, inspectJpeg(viaSub))
        // 포인터가 자기 자신·서로를 가리키는 고리는 멈춘다(ExifInterface 도 읽은 자리는 다시 읽지 않는다).
        val loop = tiff(60, listOf(Triple(8, listOf(E(0x8769, 4, 1, 26)), 8L), Triple(26, listOf(E(0x8769, 4, 1, 8)), 26L)))
        assertEquals(ExifContainerGuard.Verdict.SAFE, inspectJpeg(loop))
    }

    @Test
    fun IFD_사슬이_상한을_넘으면_거절한다() {
        // 칸 하나짜리 IFD 를 다음 IFD 로 잇는다 — ExifInterface 는 IFD 마다 한 겹 더 재귀한다.
        fun chain(k: Int): ByteArray {
            val step = 18
            val ifds = List(k) { i -> Triple(8 + i * step, listOf(E(0x0112, 3, 1, 1)), if (i + 1 < k) (8 + (i + 1) * step).toLong() else 0L) }
            return tiff(8 + k * step + 8, ifds)
        }
        assertEquals(ExifContainerGuard.Verdict.SAFE, inspectJpeg(chain(ExifContainerGuard.MAX_IFDS)))
        assertEquals(ExifContainerGuard.Verdict.UNSAFE, inspectJpeg(chain(ExifContainerGuard.MAX_IFDS + 1)))
    }

    @Test
    fun 썸네일_길이와_띠의_합은_예산에_든다() {
        fun thumb(offset: Long, length: Long) = tiff(
            80,
            listOf(
                Triple(8, listOf(E(0x0112, 3, 1, 1)), 26L),
                Triple(26, listOf(E(0x0201, 4, 1, offset), E(0x0202, 4, 1, length)), 0L),
            ),
        )
        assertEquals(ExifContainerGuard.Verdict.SAFE, inspectJpeg(thumb(60, 20)))
        // 길이가 블록 끝을 조금 넘어도(실물에 있다) 거절하지 않는다 — 잡는 크기는 길이뿐이다.
        assertEquals(ExifContainerGuard.Verdict.SAFE, inspectJpeg(thumb(60, 36)))
        assertEquals(ExifContainerGuard.Verdict.UNSAFE, inspectJpeg(thumb(1, 0x7FFFFFF0)))
        assertEquals(ExifContainerGuard.Verdict.UNSAFE, inspectJpeg(thumb(60, ExifContainerGuard.MAX_METADATA + 1)))
        // 띠 — 합만큼, 그리고 띠마다 한 번 더 잡는다(합의 두 배를 센다).
        fun strips(type: Int, count: Long, value: Long) = tiff(
            80,
            listOf(
                Triple(8, listOf(E(0x0112, 3, 1, 1)), 26L),
                Triple(26, listOf(E(0x0111, 4, 1, 60), E(0x0117, type, count, value)), 0L),
            ),
        )
        assertEquals(ExifContainerGuard.Verdict.SAFE, inspectJpeg(strips(3, 2, 10L or (10L shl 16))))
        assertEquals(ExifContainerGuard.Verdict.SAFE, inspectJpeg(strips(3, 2, 60_000L or (60_000L shl 16))))
        assertEquals(ExifContainerGuard.Verdict.SAFE, inspectJpeg(strips(4, 1, ExifContainerGuard.MAX_METADATA / 2 - 64)))
        assertEquals(ExifContainerGuard.Verdict.UNSAFE, inspectJpeg(strips(4, 1, ExifContainerGuard.MAX_METADATA / 2 + 1)))
        // JPEG 의 IFD0 은 썸네일 자리가 아니다(본 그림의 띠 칸을 적은 파일이 있다). PNG 는 IFD0 과 썸네일을 맞바꿀 수 있어 본다.
        val primaryStrips = tiff(60, listOf(Triple(8, listOf(E(0x0117, 4, 1, 5_000_000)), 0L)))
        assertEquals(ExifContainerGuard.Verdict.SAFE, inspectJpeg(primaryStrips))
        assertFalse(ExifContainerGuard.allows(png { pngChunk(it, "eXIf", primaryStrips); pngEnd(it) }))
    }

    @Test
    fun ExifInterface_가_할당_전에_멈추는_자리는_통과한다() {
        // 잘못된 형식(0·14)의 칸 — 개수가 터무니없어도 ExifInterface 는 건너뛴다.
        assertEquals(ExifContainerGuard.Verdict.SAFE, inspectJpeg(tiff(40, listOf(Triple(8, listOf(E(0x02BC, 0, 300_000_000, 26), E(0x02BC, 14, 300_000_000, 26)), 0L)))))
        // 개수 0 — 0 바이트.
        assertEquals(ExifContainerGuard.Verdict.SAFE, inspectJpeg(tiff(40, listOf(Triple(8, listOf(E(0x02BC, 1, 0, 0)), 0L)))))
        // 블록 밖을 가리키는 다음 IFD·포인터, 0 이하의 개수.
        assertEquals(ExifContainerGuard.Verdict.SAFE, inspectJpeg(tiff(40, listOf(Triple(8, listOf(E(0x8769, 4, 1, 0x7FFF_FFFF)), 0x7FFF_FFFFL)))))
        assertEquals(ExifContainerGuard.Verdict.SAFE, inspectJpeg(tiff(40, listOf(Triple(8, emptyList(), 0L)))))
        // TIFF 머리가 아니면(바이트 차례·42·첫 IFD) 거기서 멈춘다.
        val notTiff = xmpBomb(200_000_000).also { it[0] = 'X'.code.toByte() }
        assertEquals(ExifContainerGuard.Verdict.SAFE, inspectJpeg(notTiff))
        val badStart = xmpBomb(200_000_000).also { it[2] = 43 }
        assertEquals(ExifContainerGuard.Verdict.SAFE, inspectJpeg(badStart))
        val lowFirst = xmpBomb(200_000_000).also { it[4] = 4 }
        assertEquals(ExifContainerGuard.Verdict.SAFE, inspectJpeg(lowFirst))
        // 블록 끝에서 잘린 IFD — 들어 있는 칸만 본다.
        val cut = xmpBomb(8).copyOf(20)
        assertEquals(ExifContainerGuard.Verdict.SAFE, inspectJpeg(cut))
    }

    @Test
    fun 사진다운_EXIF_는_통과한다() {
        // IFD0(방향·Exif·GPS 포인터) → Exif(찍은 날 ASCII 20, MakerNote 3000) → GPS(위도 RATIONAL 3) / IFD1(썸네일 2000).
        val date = "2026:11:27 10:00:00\u0000".toByteArray(Charsets.ISO_8859_1)
        val block = tiff(
            6000,
            listOf(
                Triple(8, listOf(E(0x0112, 3, 1, 6), E(0x8769, 4, 1, 100), E(0x8825, 4, 1, 200)), 300L),
                Triple(100, listOf(E(0x9003, 2, 20, 400), E(0x927C, 7, 3000, 500)), 0L),
                Triple(200, listOf(E(0x0002, 5, 3, 420)), 0L),
                Triple(300, listOf(E(0x0103, 3, 1, 6), E(0x0201, 4, 1, 3600), E(0x0202, 4, 1, 2000)), 0L),
            ),
            data = listOf(400 to date),
        )
        assertEquals(ExifContainerGuard.Verdict.SAFE, inspectJpeg(block))
        assertEquals(ExifContainerGuard.Verdict.SAFE, inspectJpeg(block, block)) // APP1 둘
        assertEquals(ExifContainerGuard.Verdict.SAFE, ExifContainerGuard.inspect(png { pngChunk(it, "eXIf", block); pngEnd(it) }))
        assertEquals(ExifContainerGuard.Verdict.SAFE, ExifContainerGuard.inspect(webp(listOf("VP8 " to ByteArray(10), "EXIF" to block))))
        // 둘째 APP1 이 터무니없으면 거절(ExifInterface 는 APP1 마다 읽는다).
        assertEquals(ExifContainerGuard.Verdict.UNSAFE, inspectJpeg(block, xmpBomb(200_000_000)))
    }

    /** 재검산이 낸 회귀 — ExifInterface 가 읽는 실물의 버릇. 블록 밖 자료를 거절하던 판은 둘 다 UNSAFE 였다(날짜·방향을 잃었다). */
    @Test
    fun MakerNote_가_블록을_넘거나_썸네일_길이가_긴_사진은_통과한다() {
        val date = "2026:11:27 10:00:00\u0000".toByteArray(Charsets.ISO_8859_1)
        fun photo(makerNoteCount: Long, thumbLength: Long) = tiff(
            600,
            listOf(
                Triple(8, listOf(E(0x0112, 3, 1, 6), E(0x8769, 4, 1, 100)), 300L),
                Triple(100, listOf(E(0x9003, 2, 20, 400), E(0x927C, 7, makerNoteCount, 500)), 0L),
                Triple(300, listOf(E(0x0103, 3, 1, 6), E(0x0201, 4, 1, 450), E(0x0202, 4, 1, thumbLength)), 0L),
            ),
            data = listOf(400 to date),
        )
        assertEquals(ExifContainerGuard.Verdict.SAFE, inspectJpeg(photo(100, 50)))
        // MakerNote 가 블록 끝(600)을 넘는다(500 + 3000).
        assertEquals(ExifContainerGuard.Verdict.SAFE, inspectJpeg(photo(3000, 50)))
        // 썸네일 길이가 블록 끝을 16 바이트 넘는다.
        assertEquals(ExifContainerGuard.Verdict.SAFE, inspectJpeg(photo(100, 150 + 16)))
    }

    /** RIFF 크기 칸이 0~4 인 WebP — ExifInterface 는 RIFF 크기와 상관없이 12 자리의 첫 청크를 읽고 EXIF 면 곧바로 잡는다(재검산의 우회). */
    @Test
    fun RIFF_크기가_작아도_첫_청크가_EXIF_면_본다() {
        fun webpRiff(riff: Int, vararg chunks: Pair<String, ByteArray>, declared: Long? = null): ByteArray {
            val out = ByteArrayOutputStream()
            out.write("RIFF".toByteArray())
            out.write(le(riff.toLong()))
            out.write("WEBP".toByteArray())
            for ((type, data) in chunks) {
                out.write(type.toByteArray(Charsets.ISO_8859_1))
                out.write(le(declared ?: data.size.toLong()))
                out.write(data)
                if (data.size % 2 == 1) out.write(0)
            }
            return out.toByteArray()
        }
        for (riff in listOf(0, 3, 4)) {
            // 첫 청크 EXIF 의 길이 200 MB — 파일에 없어도 ExifInterface 는 먼저 잡는다.
            assertEquals(ExifContainerGuard.Verdict.UNSAFE, ExifContainerGuard.inspect(webpRiff(riff, "EXIF" to ByteArray(16), declared = 200_000_000)), "riff=$riff 길이")
            // 첫 청크 EXIF 안의 IFD 개수 폭탄.
            assertEquals(ExifContainerGuard.Verdict.UNSAFE, ExifContainerGuard.inspect(webpRiff(riff, "EXIF" to xmpBomb(200_000_000))), "riff=$riff IFD")
            // 음수 길이(ExifInterface 는 `NegativeArraySizeException`).
            assertEquals(ExifContainerGuard.Verdict.UNSAFE, ExifContainerGuard.inspect(webpRiff(riff, "EXIF" to ByteArray(16), declared = 0xFFFFFFF0L)), "riff=$riff 음수")
            // 멀쩡한 EXIF 는 통과하고, 첫 청크가 EXIF 가 아니면 RIFF 끝을 넘어 멈춘다(그 뒤의 EXIF 는 읽지 않는다).
            assertEquals(ExifContainerGuard.Verdict.SAFE, ExifContainerGuard.inspect(webpRiff(riff, "EXIF" to xmpBomb(8))), "riff=$riff 정상")
            assertEquals(
                ExifContainerGuard.Verdict.NO_EXIF,
                ExifContainerGuard.inspect(webpRiff(riff, "VP8 " to ByteArray(10), "EXIF" to xmpBomb(200_000_000))),
                "riff=$riff 둘째",
            )
        }
    }

    // ---- HEIF·AVIF(추출기의 Exif 자리) ----

    /** `ftyp` 상자 뒤 [body] — [exifAt] 자리에 `Exif\0\0` + TIFF 블록을 둔다. */
    private fun heif(tiffBlock: ByteArray, exifAt: Int = 64): ByteArray {
        val out = ByteArray(exifAt + 6 + tiffBlock.size + 16)
        val box = byteArrayOf(0, 0, 0, 24) + "ftypheic".toByteArray() + ByteArray(12)
        box.copyInto(out)
        "Exif\u0000\u0000".toByteArray(Charsets.ISO_8859_1).copyInto(out, exifAt)
        tiffBlock.copyInto(out, exifAt + 6)
        return out
    }

    private fun <T> withLocator(locator: ExifContainerGuard.HeifLocator?, body: () -> T): T {
        val before = ExifContainerGuard.heifLocator
        ExifContainerGuard.heifLocator = locator
        try {
            return body()
        } finally {
            ExifContainerGuard.heifLocator = before
        }
    }

    /** [path] 를 열어 [ExifContainerGuard.openForExif] 로 묻고, 돌려받은 입력의 (갈래, 바이트) — 넘기지 않으면 null. */
    private fun openBytes(bytes: ByteArray, requireExif: Boolean = false): Pair<Int, ByteArray>? {
        val f = Files.createTempFile("guard", ".jpg").toFile() // 이름은 .jpg 여도 내용이 HEIF 다
        try {
            f.writeBytes(bytes)
            return ExifContainerGuard.openForExif(f, requireExif)?.use { it.streamType to it.input.readBytes() }
        } finally {
            f.delete()
        }
    }

    @Test
    fun HEIF_는_추출기가_준_Exif_블록만_돌려준다() {
        val good = heif(xmpBomb(8))
        val bomb = heif(xmpBomb(200_000_000))
        val asked = mutableListOf<java.io.FileDescriptor>()
        fun at(exifLength: Int) = ExifContainerGuard.HeifLocator { fd ->
            asked += fd
            ExifContainerGuard.HeifBoxes("64", exifLength.toString())
        }
        withLocator(at(6 + 34)) {
            // 블록은 `Exif\0\0` 머리째 — EXIF_DATA_ONLY 가 그 머리를 확인하고 뗀다. 파일째로는 넘기지 않는다.
            val (type, block) = openBytes(good)!!
            assertEquals(ExifContainerGuard.STREAM_TYPE_EXIF_DATA_ONLY, type)
            assertEquals(good.copyOfRange(64, 64 + 40).toList(), block.toList())
            assertEquals(null, openBytes(bomb))
            val f = Files.createTempFile("guard", ".heic").toFile()
            try {
                f.writeBytes(good)
                // 경로째 넘기는 길(쓰기·정제)에는 HEIF 가 없다 — 검사는 추출기에 묻지 않는다.
                assertEquals(ExifContainerGuard.Verdict.HEIF, ExifContainerGuard.inspect(f))
                assertFalse(ExifContainerGuard.allows(f))
                java.io.RandomAccessFile(f, "r").use { assertEquals(ExifContainerGuard.Verdict.HEIF, ExifContainerGuard.inspect(it.channel)) }
                // 갤러리처럼 이미 연 스트림으로 물으면 추출기에 **그 서술자**를 넘긴다(경로를 다시 열지 않는다).
                asked.clear()
                java.io.FileInputStream(f).use { stream ->
                    val src = ExifContainerGuard.openForExif(stream, requireExif = true)!!
                    assertEquals(listOf(stream.fd), asked.toList())
                    assertTrue(src.input !== stream)
                    assertEquals(0L, stream.channel.position())
                }
            } finally {
                f.delete()
            }
        }
        // 길이 ≤ 6 은 ExifInterface 도 할당 전에 멈춘다 — Exif 가 없는 것. 길이가 상한을 넘거나 파일 밖이면 넘기지 않는다.
        withLocator(at(6)) { assertEquals(null, openBytes(bomb)) }
        withLocator(at((ExifContainerGuard.MAX_METADATA + 7).toInt())) { assertEquals(null, openBytes(good)) }
        withLocator(at(6 + 34 + 1000)) { assertEquals(null, openBytes(good)) }
        // `Exif\0\0` 이 아니면 Exif 가 없는 것('Invalid identifier') — 폭탄 블록도 걷지 않는다.
        withLocator(at(6 + 34)) { assertEquals(null, openBytes(bomb.copyOf().also { it[64] = 'X'.code.toByte() })) }
        // 자리가 없거나, 추출기가 없거나 답하지 못하거나 숫자가 아니면 넘기지 않는다.
        withLocator({ ExifContainerGuard.HeifBoxes(null, null) }) { assertEquals(null, openBytes(good)) }
        withLocator(null) { assertEquals(null, openBytes(good)) }
        withLocator({ null }) { assertEquals(null, openBytes(good)) }
        withLocator({ throw IllegalStateException("추출기") }) { assertEquals(null, openBytes(good)) }
        withLocator({ ExifContainerGuard.HeifBoxes("x", "40") }) { assertEquals(null, openBytes(good)) }
    }

    @Test
    fun HEIF_의_폭_높이_회전은_Exif_가_0_일_때_대신한다() {
        val good = heif(xmpBomb(8))
        val f = Files.createTempFile("guard", ".heic").toFile()
        try {
            f.writeBytes(good)
            for ((rotation, orientation) in listOf("90" to 6, "180" to 3, "270" to 8, "0" to 1, "45" to 1)) {
                withLocator({ ExifContainerGuard.HeifBoxes("64", "40", "4032", "3024", rotation) }) {
                    val src = ExifContainerGuard.openForExif(f)!!.also { it.close() }
                    assertEquals(listOf(4032, 3024, orientation), listOf("ImageWidth", "ImageLength", "Orientation").map { src.orFallback(it, 0) })
                    // Exif 에 있으면 그것이 이긴다. 그 밖의 태그는 그대로.
                    assertEquals(listOf(100, 200, 3), listOf("ImageWidth" to 100, "ImageLength" to 200, "Orientation" to 3).map { (t, v) -> src.orFallback(t, v) })
                    assertEquals(0, src.orFallback("LightSource", 0))
                }
            }
            withLocator({ ExifContainerGuard.HeifBoxes("64", "40", "x", null, null) }) {
                val src = ExifContainerGuard.openForExif(f)!!.also { it.close() }
                assertEquals(listOf(0, 0, 0), listOf("ImageWidth", "ImageLength", "Orientation").map { src.orFallback(it, 0) })
            }
        } finally {
            f.delete()
        }
    }

    @Test
    fun 넘겨도_되는_파일은_그_스트림째_돌려준다() {
        val f = Files.createTempFile("guard", ".png").toFile()
        try {
            f.writeBytes(png { pngEnd(it) })
            java.io.FileInputStream(f).use { stream ->
                // EXIF 가 없는 PNG — 읽기만 하는 쪽은 열고(폭·높이), 찍은 날을 찾는 갤러리는 열지 않는다.
                assertTrue(ExifContainerGuard.openForExif(stream)!!.input === stream)
                assertEquals(null, ExifContainerGuard.openForExif(stream, requireExif = true))
                assertEquals(0L, stream.channel.position())
            }
            ExifContainerGuard.openForExif(f)!!.use {
                assertEquals(ExifContainerGuard.STREAM_TYPE_FULL_IMAGE_DATA, it.streamType)
                assertEquals(0, it.orFallback("Orientation", 0)) // 파일째 연 입력에는 대신할 값이 없다
            }
            f.writeBytes(png { d -> d.writeInt(0x7FFFFFF0); d.write("eXIf".toByteArray()); d.write(ByteArray(16)) })
            assertEquals(null, ExifContainerGuard.openForExif(f))
        } finally {
            f.delete()
        }
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
