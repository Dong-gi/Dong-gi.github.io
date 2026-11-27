package io.github.donggi.iroiroviewer.model

import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.RandomAccessFile

/**
 * 파일 안에 **EXIF 밖에 적힌 위치**가 있는가 — XMP 의 `exif:GPSLatitude`·`photoshop:City`·`Iptc4xmpExt:LocationShown`, DJI 의
 * `drone-dji:GpsLatitude`, IPTC(APP13)의 도시·나라 레코드, 모션 포토의 덧붙은 MP4 `©xyz` 상자.
 *
 * EXIF 의 GPS 칸은 숫자 태그라 이 이름들이 바이트로 나오지 않는다. ExifInterface 의 `setAttribute` 는 EXIF 칸을 지울 뿐 이것들을 고치지 못하므로,
 * 위치 지우기는 이것으로 **지운 뒤에도 남았는가**(사본)·**지울 수 있는가**(원본 자리 — XMP 를 통째로 지우면 모션 포토의 `MicroVideo` 가 깨지고 덧붙은
 * 영상은 손댈 수 없다)를 본다.
 *
 * ## 이름 두 벌 — 어디서 찾는가
 *
 * - **긴 이름**([LONG] — `gpslatitude`·`gpslongitude`·`locationshown`·`locationcreated`·`sublocation`)은 어디서든 찾는다(그림 자료 속에서 우연히
 *   맞을 확률이 없다).
 * - **짧은 표지**([SHORT] — `city`·`©xyz`)는 우연히 맞기 쉬워(4바이트) **메타데이터 자리에서만** 찾는다 — JPEG 의 첫 SOS 앞 덩이들, PNG 의 글 덩이.
 *   `©xyz` 는 JPEG 의 EOI 뒤(모션 포토의 MP4)에서도 찾는다(MP4 안은 압축 자료라 맞을 수 있다 — 잘못 맞으면 안전한 쪽(건너뛰기·보내지 않기)이다).
 * - **WebP 는 `XMP ` 청크가 있기만 하면 위치가 있는 것으로 친다** — ExifInterface 가 그 청크를 지우지 못한다([webpHasXmp]).
 * - 그 밖의 형식은 파일 전체에서 긴 이름만 찾는다.
 *
 * 대소문자를 가리지 않는다(DJI 는 `GpsLatitude`). 읽는 메모리는 버퍼 하나다.
 *
 * **PNG 의 압축된 글 덩이**(`zTXt`, 압축 표시가 켜진 `iTXt` — 'Raw profile type iptc/xmp' 따위)는 풀지 않으므로 볼 수 없다 — 있으면 **위치가 있는 것으로
 * 친다**(사본은 보내지 않는다 — 더 간단하고 안전한 쪽).
 */
object MetadataLocation {

    private val LONG = listOf("gpslatitude", "gpslongitude", "locationshown", "locationcreated", "sublocation").map { it.ascii() }
    private val SHORT = listOf("city".ascii(), byteArrayOf(0xA9.toByte(), 'x'.code.toByte(), 'y'.code.toByte(), 'z'.code.toByte()))
    private val XYZ = listOf(SHORT[1])

    /** IPTC(IIM) 응용 레코드 2 의 위치 데이터셋 — 도시(90)·하위 지역(92)·주(95)·나라 코드(100)·나라(101). 앞은 `1C 02`. */
    private val IPTC_LOCATION_SETS = setOf(90, 92, 95, 100, 101)

    private const val BUFFER = 64 * 1024

    private fun String.ascii() = toByteArray(Charsets.US_ASCII)

    /** [input] 을 끝까지 읽어 **긴 이름**이 있는가. 닫지 않는다. */
    fun contains(input: InputStream): Boolean = scan(input, LONG)

    /** 바이트 배열에서 **긴 이름**(시험이 부른다). */
    fun contains(bytes: ByteArray): Boolean = find(bytes, 0, bytes.size, LONG)

    /**
     * [file] 에 EXIF 밖의 위치가 있는가(머리말의 규칙대로 형식마다). 읽지 못하면 `IOException`.
     */
    fun fileHasLocation(file: File): Boolean {
        val head = ByteArray(12)
        val n = FileInputStream(file).use { readUpTo(it, head) }
        // 긴 이름은 형식과 상관없이 파일 전체에서.
        if (FileInputStream(file).use { contains(it) }) return true
        return when {
            n >= 2 && head[0] == 0xFF.toByte() && head[1] == 0xD8.toByte() -> jpegHasLocation(file)
            n >= 8 && head.copyOf(8).contentEquals(PNG_SIGNATURE) -> pngHasLocation(file)
            n == 12 && String(head, 0, 4, Charsets.ISO_8859_1) == "RIFF" && String(head, 8, 4, Charsets.ISO_8859_1) == "WEBP" -> webpHasXmp(file)
            else -> false
        }
    }

    private fun readUpTo(input: InputStream, dst: ByteArray): Int {
        var done = 0
        while (done < dst.size) {
            val r = input.read(dst, done, dst.size - done)
            if (r < 0) break
            done += r
        }
        return done
    }

    /**
     * WebP 에 `XMP ` 청크가 있는가 — **있으면 위치가 있는 것으로 친다.** ExifInterface 1.4.2 는 WebP 의 `XMP ` 청크를 지우지 못하고(EXIF 칸만
     * 다시 쓴다), XMP 는 긴 이름 말고도(`photoshop:City`·`exif:GPSDestLatitude` 따위) 위치를 적는다 — 사본은 '위치를 지우지 못한 것' 이 되고
     * 원본 자리에서는 건너뛴다. 청크를 [MAX_WEBP_CHUNKS] 넘게 훑어도 끝나지 않으면 남은 것을 보지 못했으니 있는 것으로 친다.
     */
    private fun webpHasXmp(file: File): Boolean {
        RandomAccessFile(file, "r").use { raf ->
            val length = raf.length()
            val hdr = ByteArray(8)
            var pos = 12L
            var chunks = 0
            while (pos + 8 <= length) {
                if (++chunks > MAX_WEBP_CHUNKS) return true
                raf.seek(pos)
                raf.readFully(hdr)
                if (String(hdr, 0, 4, Charsets.ISO_8859_1) == "XMP ") return true
                val size = (hdr[4].toLong() and 0xFF) or ((hdr[5].toLong() and 0xFF) shl 8) or
                    ((hdr[6].toLong() and 0xFF) shl 16) or ((hdr[7].toLong() and 0xFF) shl 24)
                pos += 8 + size + (size and 1L)
            }
        }
        return false
    }

    private const val MAX_WEBP_CHUNKS = 100_000

    /**
     * 긴 이름은 이미 보았다 — 머리 덩이의 짧은 표지·IPTC, EOI 뒤의 `©xyz`. **짜임을 읽지 못하면 위치가 있는 것으로 친다** — 짧은 표지·IPTC·
     * 덧붙은 영상을 볼 자리를 모르므로(보지 못한 것을 '없다' 고 하지 않는다). 사본은 보내지 않고, 원본 자리는 그 앞에서 '지울 수 없는 형식' 으로 건너뛴다
     * ([JpegLayout.isUnreadableJpeg]).
     */
    private fun jpegHasLocation(file: File): Boolean {
        val layout = JpegLayout.read(file) ?: return true
        RandomAccessFile(file, "r").use { raf ->
            for (s in layout.headers) {
                val bytes = readRange(raf, s.start, s.end)
                if (find(bytes, 0, bytes.size, SHORT)) return true
                if (s.marker == JpegLayout.APP13 && hasIptcLocation(bytes)) return true
            }
            if (layout.hasTrailer) {
                raf.seek(layout.mainEnd)
                if (scan(RafStream(raf, layout.length - layout.mainEnd), XYZ)) return true
            }
        }
        return false
    }

    /** IPTC 데이터셋 표지 `1C 02 <번호>` 가운데 위치인 것이 있는가. */
    internal fun hasIptcLocation(bytes: ByteArray): Boolean {
        for (i in 0 until bytes.size - 2) {
            if (bytes[i] == 0x1C.toByte() && bytes[i + 1] == 0x02.toByte() && (bytes[i + 2].toInt() and 0xFF) in IPTC_LOCATION_SETS) return true
        }
        return false
    }

    private fun pngHasLocation(file: File): Boolean {
        RandomAccessFile(file, "r").use { raf ->
            val length = raf.length()
            var pos = 8L
            while (pos + 12 <= length) {
                raf.seek(pos)
                val len = raf.readInt().toLong() and 0xFFFFFFFFL
                val typeBytes = ByteArray(4)
                raf.readFully(typeBytes)
                val type = String(typeBytes, Charsets.US_ASCII)
                val dataStart = pos + 8
                val dataEnd = dataStart + len
                if (dataEnd + 4 > length) return false // 긴 이름 검사로 끝
                when (type) {
                    "zTXt" -> return true
                    "tEXt", "iTXt" -> {
                        if (len > MAX_TEXT_CHUNK) return true
                        val data = readRange(raf, dataStart, dataEnd)
                        if (type == "iTXt" && iTxtCompressed(data)) return true
                        if (find(data, 0, data.size, SHORT)) return true
                    }
                    "IEND" -> return false
                }
                pos = dataEnd + 4
            }
        }
        return false
    }

    /** `iTXt` 의 압축 표시 — 키워드 NUL 다음 한 바이트. 짜임이 어긋나면 압축된 것으로 친다. */
    private fun iTxtCompressed(data: ByteArray): Boolean {
        val nul = data.indexOf(0)
        if (nul < 0 || nul + 1 >= data.size) return true
        return data[nul + 1].toInt() != 0
    }

    /** 글 덩이 하나를 통째로 읽는 상한 — 넘으면 위치가 있는 것으로 친다(보지 못한 것을 '없다' 고 하지 않는다). */
    private const val MAX_TEXT_CHUNK = 8L shl 20

    private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    private fun readRange(raf: RandomAccessFile, from: Long, to: Long): ByteArray {
        val n = (to - from).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
        val out = ByteArray(n)
        raf.seek(from)
        raf.readFully(out)
        return out
    }

    private fun scan(input: InputStream, needles: List<ByteArray>): Boolean {
        val keep = needles.maxOf { it.size } - 1
        val buf = ByteArray(BUFFER + keep)
        var carried = 0
        while (true) {
            val n = input.read(buf, carried, BUFFER)
            if (n < 0) return false
            val len = carried + n
            if (find(buf, 0, len, needles)) return true
            // 경계에 걸친 이름을 놓치지 않게 끝의 몇 바이트를 다음 차례 앞에 둔다.
            carried = minOf(keep, len)
            System.arraycopy(buf, len - carried, buf, 0, carried)
        }
    }

    private fun find(buf: ByteArray, from: Int, to: Int, needles: List<ByteArray>): Boolean {
        for (needle in needles) {
            val last = to - needle.size
            var i = from
            while (i <= last) {
                var k = 0
                while (k < needle.size && lower(buf[i + k]) == needle[k]) k++
                if (k == needle.size) return true
                i++
            }
        }
        return false
    }

    private fun lower(b: Byte): Byte = if (b in 'A'.code.toByte()..'Z'.code.toByte()) (b + 32).toByte() else b

    /** 파일의 한 자리부터 [left] 바이트를 읽는 흐름. */
    private class RafStream(private val raf: RandomAccessFile, private var left: Long) : InputStream() {
        override fun read(): Int {
            if (left <= 0) return -1
            left--
            return raf.read()
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (left <= 0) return -1
            val n = raf.read(b, off, minOf(len.toLong(), left).toInt())
            if (n > 0) left -= n
            return n
        }
    }
}
