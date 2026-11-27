package io.github.donggi.iroiroviewer.ui.image

import androidx.exifinterface.media.ExifInterface
import io.github.donggi.iroiroviewer.model.ExifContainerGuard
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * [ExifContainerGuard] 를 **실제 ExifInterface 1.4.2** 와 견준다(이 모듈의 의존이다 — JVM 에서 돈다, 모의 android.jar 는 기본값을 돌려준다).
 *
 * - 점검자가 재현한 꼴(IFD 칸 하나의 개수로 큰 할당)을 JPEG APP1·PNG `eXIf`·WebP `EXIF`·내용이 TIFF 인 파일로 짓고, **ExifInterface 가 정말로
 *   그 크기를 잡으려 드는지**(개수 `0x7FFFFFF0` → 힙을 넘어 `OutOfMemoryError`) 와 검사기가 그것을 넘기지 않는지를 함께 본다. 검사기가 옛 판
 *   (JPEG·TIFF 는 통과, PNG·WebP 는 덩이 길이만)으로 돌아가면 깨진다.
 * - 사진다운 EXIF 는 검사기를 지나고 ExifInterface 가 방향·찍은 날을 읽는다(검사기가 멀쩡한 것을 막지 않는다).
 * - 검사기가 옮겨 적은 라이브러리의 표(형식 단위·UNDEFINED 태그·포인터)가 라이브러리와 같다 — 판을 올리면 여기서 드러난다.
 * - HEIF 는 검사기가 걸어 본 Exif 블록만 `STREAM_TYPE_EXIF_DATA_ONLY` 로 연다 — 앞부분이 큰 HEIF 에서도 블록 몫만 잡고 날짜·방향이 읽힌다
 *   (HEIF 갈래 자체는 JVM 에서 돌지 않는다 — `SDK_INT` 가 0 이다. 파일째 넘기면 이 시험이 깨진다).
 */
class ExifGuardLibraryTest {

    private class E(val tag: Int, val type: Int, val count: Long, val value: Long)

    /** 리틀 엔디언 TIFF 블록. [ifds] 는 (자리, 칸들, 다음 IFD). */
    private fun tiff(size: Int, ifds: List<Triple<Int, List<E>, Long>>, data: List<Pair<Int, ByteArray>> = emptyList()): ByteArray {
        val b = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        b.put('I'.code.toByte()).put('I'.code.toByte()).putShort(42).putInt(ifds.first().first)
        for ((at, entries, next) in ifds) {
            b.position(at)
            b.putShort(entries.size.toShort())
            for (e in entries) {
                b.putShort(e.tag.toShort()).putShort(e.type.toShort()).putInt(e.count.toInt())
                if (e.type == 3 && e.count == 1L) b.putShort(e.value.toShort()).putShort(0) else b.putInt(e.value.toInt())
            }
            b.putInt(next.toInt())
        }
        for ((at, bytes) in data) {
            b.position(at)
            b.put(bytes)
        }
        return b.array()
    }

    private val exifId = "Exif\u0000\u0000".toByteArray(Charsets.ISO_8859_1)

    private fun jpeg(t: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
        val len = 2 + exifId.size + t.size
        out.write(byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (len shr 8).toByte(), len.toByte()))
        out.write(exifId)
        out.write(t)
        out.write(byteArrayOf(0xFF.toByte(), 0xDA.toByte(), 0, 2, 0x11, 0x22, 0xFF.toByte(), 0xD9.toByte()))
        return out.toByteArray()
    }

    private fun png(t: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        fun chunk(type: String, data: ByteArray) {
            out.write(ByteBuffer.allocate(4).putInt(data.size).array())
            val tb = type.toByteArray(Charsets.ISO_8859_1)
            out.write(tb)
            out.write(data)
            out.write(ByteBuffer.allocate(4).putInt(CRC32().apply { update(tb); update(data) }.value.toInt()).array())
        }
        chunk("IHDR", byteArrayOf(0, 0, 0, 1, 0, 0, 0, 1, 8, 2, 0, 0, 0)) // ExifInterface 는 IHDR 이 첫 덩이여야 한다
        chunk("eXIf", t) // CRC 도 맞아야 TIFF 를 읽는다
        chunk("IEND", ByteArray(0))
        return out.toByteArray()
    }

    private fun webp(t: ByteArray): ByteArray {
        val body = ByteArrayOutputStream()
        body.write("WEBP".toByteArray())
        body.write("EXIF".toByteArray())
        body.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(t.size).array())
        body.write(t)
        if (t.size % 2 == 1) body.write(0)
        val out = ByteArrayOutputStream()
        out.write("RIFF".toByteArray())
        out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(body.size()).array())
        body.writeTo(out)
        return out.toByteArray()
    }

    /** 점검자의 TIFF — IFD0 의 XMP(0x02BC, BYTE) 칸 하나, 자료는 26 에. 34 바이트. */
    private fun xmpBomb(count: Long) = tiff(34, listOf(Triple(8, listOf(E(0x02BC, 1, count, 26)), 0L)))

    /** 서술자 없이(바이트에서) 연다 — 만화 쪽이 그렇게 연다. 썸네일도 그때 잡는다. */
    private fun open(bytes: ByteArray) = ExifInterface(ByteArrayInputStream(bytes))

    @Test
    fun ExifInterface_가_IFD_칸의_개수만큼_잡으려_들고_검사기가_넘기지_않는다() {
        val bomb = xmpBomb(0x7FFF_FFF0)
        val files = mapOf("jpeg" to jpeg(bomb), "png" to png(bomb), "webp" to webp(bomb), "webp+id" to webp(exifId + bomb), "tiff" to bomb)
        for ((name, file) in files) {
            // 라이브러리가 이 칸에 2 GiB 를 잡으려 든다(힙을 넘으니 던진다 — 힙 아래의 개수면 그만큼 잡는 데 성공한다).
            assertFailsWith<OutOfMemoryError>(name) { open(file) }
            assertEquals(ExifContainerGuard.Verdict.UNSAFE, ExifContainerGuard.inspect(file), name)
        }
        // 크기가 34~91 바이트다.
        assertTrue(files.values.all { it.size <= 100 })
    }

    @Test
    fun 썸네일_길이만큼도_잡으려_들고_검사기가_넘기지_않는다() {
        // IFD1 의 JPEGInterchangeFormatLength — 서술자 없이 열면 `handleThumbnailFromJfif` 가 그만큼 잡는다.
        val t = tiff(
            64,
            listOf(
                Triple(8, listOf(E(0x0112, 3, 1, 1)), 26L),
                Triple(26, listOf(E(0x0201, 4, 1, 60), E(0x0202, 4, 1, 0x7FFF_FFF0)), 0L),
            ),
        )
        assertFailsWith<OutOfMemoryError> { open(jpeg(t)) }
        assertEquals(ExifContainerGuard.Verdict.UNSAFE, ExifContainerGuard.inspect(jpeg(t)))
    }

    /** RIFF 크기 칸이 [riff] 인 WebP — 첫 청크 [type] 의 길이를 [declared] 로 적는다(몸은 [data]). */
    private fun webpRiff(riff: Int, type: String, data: ByteArray, declared: Int = data.size): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("RIFF".toByteArray())
        out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(riff).array())
        out.write("WEBP".toByteArray())
        out.write(type.toByteArray(Charsets.ISO_8859_1))
        out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(declared).array())
        out.write(data)
        return out.toByteArray()
    }

    @Test
    fun RIFF_크기가_작은_WebP_의_첫_EXIF_청크도_잡으려_들고_검사기가_넘기지_않는다() {
        for (riff in listOf(0, 3)) {
            // 첫 청크 EXIF 의 길이 — RIFF 크기를 보지 않고 곧바로 그만큼 잡는다.
            val long = webpRiff(riff, "EXIF", ByteArray(16), declared = 0x7FFF_FFF0)
            assertFailsWith<OutOfMemoryError>("riff=$riff 길이") { open(long) }
            assertEquals(ExifContainerGuard.Verdict.UNSAFE, ExifContainerGuard.inspect(long), "riff=$riff 길이")
            assertEquals(ExifContainerGuard.Verdict.UNSAFE, ExifContainerGuard.inspect(webpRiff(riff, "EXIF", ByteArray(16), declared = 200_000_000)), "riff=$riff 200MB")
            // 첫 청크 EXIF 안의 IFD 개수 폭탄.
            val ifd = webpRiff(riff, "EXIF", xmpBomb(0x7FFF_FFF0))
            assertFailsWith<OutOfMemoryError>("riff=$riff IFD") { open(ifd) }
            assertEquals(ExifContainerGuard.Verdict.UNSAFE, ExifContainerGuard.inspect(ifd), "riff=$riff IFD")
            assertEquals(ExifContainerGuard.Verdict.UNSAFE, ExifContainerGuard.inspect(webpRiff(riff, "EXIF", xmpBomb(200_000_000))), "riff=$riff IFD 2억")
        }
    }

    @Test
    fun MakerNote_가_블록을_넘거나_썸네일_길이가_긴_사진도_검사기를_지나고_라이브러리가_읽는다() {
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
        // MakerNote 가 블록 끝(600)을 넘는다 · 썸네일 길이가 블록 끝을 16 바이트 넘는다.
        for ((name, block) in mapOf("makernote" to photo(3000, 50), "thumb+16" to photo(100, 150 + 16))) {
            val file = jpeg(block)
            assertEquals(ExifContainerGuard.Verdict.SAFE, ExifContainerGuard.inspect(file), name)
            val exif = open(file)
            assertEquals(6, exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0), name)
            assertEquals("2026:11:27 10:00:00", exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL), name)
        }
    }

    @Test
    fun 사진다운_EXIF_는_검사기를_지나고_라이브러리가_읽는다() {
        val date = "2026:11:27 10:00:00\u0000".toByteArray(Charsets.ISO_8859_1)
        val thumb = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
        val block = tiff(
            6000,
            listOf(
                Triple(8, listOf(E(0x0112, 3, 1, 6), E(0x8769, 4, 1, 100), E(0x8825, 4, 1, 200)), 300L),
                Triple(100, listOf(E(0x9003, 2, 20, 400), E(0x927C, 7, 3000, 500)), 0L),
                Triple(200, listOf(E(0x0001, 2, 2, 'N'.code.toLong()), E(0x0002, 5, 3, 420)), 0L),
                Triple(300, listOf(E(0x0103, 3, 1, 6), E(0x0201, 4, 1, 3600), E(0x0202, 4, 1, thumb.size.toLong())), 0L),
            ),
            data = listOf(400 to date, 3600 to thumb),
        )
        for ((name, file) in mapOf("jpeg" to jpeg(block), "png" to png(block), "webp" to webp(block), "webp+id" to webp(exifId + block))) {
            assertEquals(ExifContainerGuard.Verdict.SAFE, ExifContainerGuard.inspect(file), name)
            val exif = open(file)
            assertEquals(6, exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0), name)
            assertEquals("2026:11:27 10:00:00", exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL), name)
        }
    }

    /** 라이브러리의 비공개 정적 필드. */
    private fun field(name: String): Any? = ExifInterface::class.java.getDeclaredField(name).apply { isAccessible = true }.get(null)

    @Test
    fun 검사기가_옮겨_적은_표가_라이브러리와_같다() {
        assertEquals(ExifContainerGuard.FORMAT_BYTES, (field("IFD_FORMAT_BYTES_PER_FORMAT") as IntArray).toList())

        @Suppress("UNCHECKED_CAST")
        val maps = field("sExifTagMapsForReading") as Array<HashMap<Int, Any>>
        val tagClass = Class.forName("androidx.exifinterface.media.ExifInterface\$ExifTag")
        val primary = tagClass.getDeclaredField("primaryFormat").apply { isAccessible = true }
        val formats = HashMap<Int, MutableSet<Int>>()
        for (m in maps) for ((tag, t) in m) formats.getOrPut(tag) { HashSet<Int>() }.add(primary.getInt(t))
        // UNDEFINED 로 세는 태그는 그 번호를 아는 모든 표에서 UNDEFINED(7) 다 — 그래야 형식 7 의 칸을 1 바이트로 세어도 된다.
        for (tag in ExifContainerGuard.UNDEFINED_TAGS) assertEquals<Set<Int>?>(setOf(7), formats[tag]?.toSet(), "0x%04X".format(tag))
        // 형식 7 을 본디 형식으로 바꿀 때 가장 큰 단위가 [WIDEST_UNIT] 을 넘지 않는다.
        val widest = formats.values.flatMap { it.toList() }.maxOf { f: Int -> ExifContainerGuard.FORMAT_BYTES[f] }
        assertTrue(widest <= ExifContainerGuard.WIDEST_UNIT)

        // 포인터 — JPEG·PNG·WebP 가 읽는 표(IFD0·Exif·GPS·Interop·썸네일·미리보기 — 0~5)에 든 포인터는 모두 검사기가 따라간다.
        @Suppress("UNCHECKED_CAST")
        val pointers = (field("sExifPointerTagMap") as HashMap<Int, Int>).keys
        val readable = (0..5).flatMap { maps[it].keys }.toSet()
        assertEquals(pointers.filter { it in readable }.toSet(), ExifContainerGuard.POINTER_TAGS)
    }

    /** 찍은 날만 있고 방향이 없는 TIFF 블록(HEIF 는 방향을 대개 `irot` 에 둔다 — 추출기의 회전으로 채운다). */
    private fun dateOnly(): ByteArray = tiff(
        200,
        listOf(
            Triple(8, listOf(E(0x8769, 4, 1, 40)), 0L),
            Triple(40, listOf(E(0x9003, 2, 20, 100)), 0L),
        ),
        data = listOf(100 to "2026:11:27 10:00:00\u0000".toByteArray(Charsets.ISO_8859_1)),
    )

    /** `ftyp` 상자, 그 뒤 [front] 바이트의 빈 자리, 그 뒤 `Exif\0\0` + [block] — 앞부분이 큰 HEIF. 돌려주는 것은 (파일, Exif 자리). */
    private fun bigHeif(front: Long, block: ByteArray): Pair<java.io.File, Long> {
        val f = java.nio.file.Files.createTempFile("guard", ".heic").toFile()
        val at = 24 + front
        java.io.RandomAccessFile(f, "rw").use {
            it.write(ByteBuffer.allocate(24).putInt(24).put("ftypheic".toByteArray()).putInt(0).put("mif1heic".toByteArray()).array())
            it.seek(at)
            it.write(exifId + block)
        }
        return f to at
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

    /** 이 스레드가 [body] 동안 힙에 잡은 바이트(HotSpot 의 스레드 할당 계수 — GC 와 상관없다). */
    private fun <T> allocatedBy(body: () -> T): Pair<T, Long> {
        val mx = java.lang.management.ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val id = Thread.currentThread().id
        val before = mx.getThreadAllocatedBytes(id)
        val r = body()
        return r to (mx.getThreadAllocatedBytes(id) - before)
    }

    @Test
    fun 검사기가_적은_입력_갈래가_라이브러리와_같다() {
        assertEquals(ExifInterface.STREAM_TYPE_FULL_IMAGE_DATA, ExifContainerGuard.STREAM_TYPE_FULL_IMAGE_DATA)
        assertEquals(ExifInterface.STREAM_TYPE_EXIF_DATA_ONLY, ExifContainerGuard.STREAM_TYPE_EXIF_DATA_ONLY)
        // HEIF 의 기본값을 거는 태그 이름과 회전 → 방향.
        assertEquals(listOf("ImageWidth", "ImageLength", "Orientation"), listOf(ExifInterface.TAG_IMAGE_WIDTH, ExifInterface.TAG_IMAGE_LENGTH, ExifInterface.TAG_ORIENTATION))
        assertEquals(
            listOf(6, 3, 8),
            listOf(ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_ROTATE_180, ExifInterface.ORIENTATION_ROTATE_270),
        )
        // EXIF_DATA_ONLY 는 `Exif\0\0` 머리를 확인하고 뗀다 — 머리 없이 주면 아무것도 읽지 않는다(그래서 검사기는 머리째 돌려준다).
        val block = dateOnly()
        assertEquals(
            "2026:11:27 10:00:00",
            ExifInterface(ByteArrayInputStream(exifId + block), ExifInterface.STREAM_TYPE_EXIF_DATA_ONLY).getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL),
        )
        assertEquals(null, ExifInterface(ByteArrayInputStream(block), ExifInterface.STREAM_TYPE_EXIF_DATA_ONLY).getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
        // 그 갈래도 IFD 칸의 개수만큼 잡으려 든다 — 블록을 검사기가 먼저 걸어야 한다.
        assertFailsWith<OutOfMemoryError> {
            ExifInterface(ByteArrayInputStream(exifId + xmpBomb(0x7FFF_FFF0)), ExifInterface.STREAM_TYPE_EXIF_DATA_ONLY)
        }
    }

    @Test
    fun 앞부분이_큰_HEIF_도_Exif_블록만_읽어_날짜와_방향이_나온다() {
        val front = 64L shl 20
        val (f, at) = bigHeif(front, dateOnly())
        try {
            withLocator({ ExifContainerGuard.HeifBoxes(at.toString(), (6 + 200).toString(), "4032", "3024", "90") }) {
                val (read, allocated) = allocatedBy {
                    ExifContainerGuard.openForExif(f)!!.use { src ->
                        assertEquals(ExifInterface.STREAM_TYPE_EXIF_DATA_ONLY, src.streamType)
                        val exif = ExifInterface(src.input, src.streamType)
                        Triple(
                            exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL),
                            src.orFallback(ExifInterface.TAG_ORIENTATION, exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)),
                            src.orFallback(ExifInterface.TAG_IMAGE_WIDTH, exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)),
                        )
                    }
                }
                assertEquals(Triple("2026:11:27 10:00:00", ExifInterface.ORIENTATION_ROTATE_90, 4032), read)
                // 파일 앞부분(64 MiB)을 쌓지 않는다 — 잡은 것은 블록과 라이브러리의 몫뿐이다.
                assertTrue(allocated < 4L shl 20, "할당 ${allocated shr 10} KiB")
            }
            // 블록 폭탄은 그 자리에 있어도 넘기지 않는다.
            val (g, at2) = bigHeif(front, xmpBomb(200_000_000))
            try {
                withLocator({ ExifContainerGuard.HeifBoxes(at2.toString(), (6 + 34).toString()) }) {
                    assertEquals(null, ExifContainerGuard.openForExif(g))
                }
            } finally {
                g.delete()
            }
        } finally {
            f.delete()
        }
    }
}
