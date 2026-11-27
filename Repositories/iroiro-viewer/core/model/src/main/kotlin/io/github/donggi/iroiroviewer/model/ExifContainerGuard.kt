package io.github.donggi.iroiroviewer.model

import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * ExifInterface 에 **넘겨도 되는 파일인가**를 그릇의 덩이와 그 안의 TIFF(EXIF) 블록까지 걸어 본다.
 *
 * ## 왜 필요한가
 *
 * ExifInterface 1.4.2 는 **적힌 길이대로** `new byte[길이]` 를 잡는 자리가 여럿이다. 길이를 `0x7FFFFFF0` 으로 적으면 `OutOfMemoryError` 를
 * 던지고 그것은 잡을 수 있다(할당이 실패했으니 힙은 그대로다). 그러나 길이를 힙 바로 아래(수백 MB)로 적으면 **할당이 성공한다** — 그 순간
 * 주 스레드·썸네일 스레드의 작은 할당이 대신 실패해 앱이 죽는다. 잡는 것으로는 막을 수 없고, 넘기기 전에 길이를 보아야 한다.
 *
 * ## 할당하는 자리(1.4.2 바이트코드로 확인)
 *
 * - **그릇의 덩이** — PNG 의 첫 `eXIf` 와 키워드가 `XML:com.adobe.xmp` 인 첫 `iTXt`(`길이 − 22`), WebP 의 `EXIF` 청크. JPEG 의 세그먼트는
 *   16비트 길이라 64 KiB 를 넘지 못한다.
 * - **TIFF 블록 안의 IFD 칸**(`readImageFileDirectory`) — 칸마다 `개수 × 단위` 를 **`Integer.MAX_VALUE` 이하인지만** 보고 `new byte[…]` 한
 *   뒤에 읽는다. 블록이 34바이트여도 XMP 칸(0x02BC, BYTE) 개수 2억이면 200 MB 를 잡는다. JPEG 의 APP1 `Exif\0\0` 본문·PNG `eXIf`·WebP `EXIF`
 *   (앞의 `Exif\0\0` 은 떼고) 가 모두 이 길을 탄다. 단위는 [FORMAT_BYTES](형식 13 이 1 이다), **형식 7(UNDEFINED)은 그 태그의 본디 형식으로
 *   바꿔** 센다(그 태그를 아는 표의 `primaryFormat` — RATIONAL 이면 8). 모르는 태그·잘못된 형식(0, 14 이상)·개수 × 단위가 음수이거나 상한을
 *   넘는 칸은 할당 없이 건너뛴다.
 * - **썸네일** — 썸네일 IFD 의 `JPEGInterchangeFormatLength` 만큼(서술자 없이 연 흐름 — 만화 쪽처럼 바이트에서 열면), 띠(`StripByteCounts`)의
 *   합만큼 + 띠마다 한 번 더(언제나) 잡는다. **할당 크기는 길이로만 정해진다** — 오프셋은 잡은 뒤의 건너뛰기에만 쓰인다.
 * - 포인터(Exif·GPS·Interop·SubIFD)와 다음 IFD 는 할당하지 않고 따라간다. 이미 읽은 IFD 자리는 다시 읽지 않는다.
 * - **HEIF·AVIF**(`getHeifAttributes`) — 입력을 `mark(Integer.MAX_VALUE)` 로 감싸 추출기가 읽은 만큼 파일 앞부분을 통째로 힙에 쌓는다. 그래서
 *   **이 갈래를 타지 않는다**: 플랫폼 추출기(`MediaMetadataRetriever` 키 33·34 — 이 순수 JVM 모듈은 부를 수 없어 [heifLocator] 로 꽂는다, `app`)가
 *   준 자리의 Exif 블록을 검사기가 읽어 걸어 보고, 그 블록만 `STREAM_TYPE_EXIF_DATA_ONLY` 로 넘긴다([openForExif]). 그 갈래
 *   (`getStandaloneAttributes`)는 `Exif\0\0` 를 확인하고 나머지를 HEIF 갈래와 같은 `readExifSegment`·`setThumbnailData` 로 읽는다.
 * - **내용이 TIFF 인 파일**(`II*\0`·`MM\0*`·ORF·RW2, 그리고 RAF) — 이름이 `.jpg` 여도 ExifInterface 는 내용으로 갈래를 골라 TIFF 로 통째 읽는다
 *   (`getRawAttributes`). 이 앱은 RAW 를 다루지 않으므로 **넘기지 않는다**(UNSAFE).
 *
 * ## 판정
 *
 * 그릇은 ExifInterface 와 같은 길로 걷는다(아래 각 함수). TIFF 블록은 IFD0 → 포인터·다음 IFD 를 방문 집합과 [MAX_IFDS]·[MAX_ENTRIES] 로 따라가며
 * **아는 태그든 모르는 태그든** 칸마다 ExifInterface 가 잡을 수 있는 크기(형식 7 은 [UNDEFINED_TAGS] 가 아니면 [WIDEST_UNIT] 로)를 파일 전체에서
 * 더해 [MAX_METADATA] 를 넘으면 UNSAFE 다. 썸네일 길이·띠도 여기 든다. 칸 수천 개가 같은 자리를 가리키는 꼴도 이것이 막는다.
 *
 * **자료가 블록 밖인 것은 거절하지 않는다** — ExifInterface 는 같은 크기를 잡고 읽다 EOF 로 멈출 뿐이고(크기는 예산이 묶었다), MakerNote 가 블록
 * 끝을 넘거나 썸네일 길이가 몇 바이트 긴 실물 사진도 날짜·방향은 읽힌다. 마찬가지로 **ExifInterface 가 할당 전에 스스로 멈추는 자리는 따라 멈출
 * 뿐 UNSAFE 로 치지 않는다** — TIFF 머리가 아님, 첫 IFD 오프셋이 8 보다 작거나 블록 밖, 개수가 0 이하인 IFD, 0 이하이거나 블록 밖인 포인터·다음
 * IFD, 블록 끝에서 잘린 IFD, 잘못된 형식(0·14 이상)이나 음수 개수의 칸. 개수 0 인 칸은 0 바이트를 잡는다(통과).
 *
 * 판정은 **내용의 서명**으로 한다(ExifInterface 도 확장자가 아니라 내용으로 갈래를 고른다).
 */
object ExifContainerGuard {

    /** 판정. */
    enum class Verdict {
        /** 넘기지 마라 — 길이가 터무니없거나 그릇이 깨졌거나, 이 앱이 넘기지 않는 TIFF 계열이다. */
        UNSAFE,

        /** 넘겨도 되지만 EXIF 가 없다(JPEG 의 APP1 `Exif`·PNG 의 `eXIf`·WebP 의 `EXIF` 가 없다) — 날짜·방향을 읽으려고 열 까닭이 없다. */
        NO_EXIF,

        /** 넘겨도 된다(EXIF 가 있거나, 있는지 모르는 그릇). */
        SAFE,

        /**
         * 내용이 ISO BMFF(`ftyp` — HEIF·AVIF)다. **파일째로는 넘기지 않는다** — ExifInterface 1.4.2 의 HEIF 갈래는 입력을
         * `BufferedInputStream` + `mark(Integer.MAX_VALUE)` 로 감싸 추출기가 읽은 가장 먼 자리까지 파일 앞부분을 통째로 힙에 쌓는다(바이트코드 —
         * 앞부분이 큰 HEIF 하나로 `OutOfMemoryError`). Exif 는 [openForExif] 가 블록만 꺼내 준다. [allows] 는 이것을 넘기지 않는다.
         */
        HEIF,
    }

    /**
     * HEIF·AVIF 에서 플랫폼 추출기(`MediaMetadataRetriever`)가 돌려준 글자 그대로 — Exif 자리·길이(키 33·34), 그리고 ExifInterface 가 Exif 앞에
     * 먼저 넣어 두는 그림의 폭·높이·회전(그림이 있으면 키 29·30·31, 없고 영상이 있으면 18·19·24). 없으면 null.
     */
    data class HeifBoxes(
        val exifOffset: String?,
        val exifLength: String?,
        val width: String? = null,
        val height: String? = null,
        val rotation: String? = null,
    )

    /** [HeifBoxes] 를 얻는 한 함수짜리 이음매. **검사기가 연 그 서술자**를 받는다(경로를 다시 열지 않는다 — 같은 inode). 열지 못하면 null. */
    fun interface HeifLocator {
        fun locate(fd: FileDescriptor): HeifBoxes?
    }

    /** `app` 이 시작할 때 꽂는다(플랫폼 추출기). 꽂히지 않으면 HEIF·AVIF 의 Exif 는 읽지 않는다. */
    @Volatile
    var heifLocator: HeifLocator? = null

    /** ExifInterface 의 `STREAM_TYPE_FULL_IMAGE_DATA` — 파일째. 시험이 라이브러리와 견준다. */
    const val STREAM_TYPE_FULL_IMAGE_DATA = 0

    /** ExifInterface 의 `STREAM_TYPE_EXIF_DATA_ONLY` — `Exif\0\0` 머리 + TIFF 블록만(`getStandaloneAttributes` 가 머리를 확인하고 뗀다). */
    const val STREAM_TYPE_EXIF_DATA_ONLY = 1

    /**
     * ExifInterface 에 넘길 입력 — `ExifInterface(input, streamType)` 로 연다. 폭·높이·방향은 읽은 값을 [orFallback] 에 지나게 한다. 닫는 것은
     * 부른 쪽이다(`use`). 닫은 뒤에도 [orFallback] 은 쓸 수 있다.
     */
    class ExifInput internal constructor(
        val input: InputStream,
        val streamType: Int,
        private val fallbacks: Map<String, Int>,
    ) : Closeable {
        /**
         * ExifInterface 가 [tag] 로 읽은 값 [read] — 0 이면 HEIF 추출기의 폭(`ImageWidth`)·높이(`ImageLength`)·회전(`Orientation`)으로 대신한다.
         * ExifInterface 의 HEIF 갈래는 그 셋을 Exif 앞에 넣어 두고 Exif 에 있으면 덮게 하는데, 블록만 열면 Exif 에 없는 셋을
         * `addDefaultValuesForCompatibility` 가 0 으로 채운다(바이트코드). 파일째 연 입력과 그 밖의 태그는 [read] 그대로다.
         */
        fun orFallback(tag: String, read: Int): Int = if (read == 0) fallbacks[tag] ?: read else read

        override fun close() = input.close()
    }

    /** ExifInterface 가 잡는 메타데이터 덩이 하나의 상한이자, TIFF 블록 안의 칸들이 잡는 크기의 합의 상한. 실물의 EXIF 는 대개 수십 KiB 다. */
    const val MAX_METADATA: Long = 8L shl 20

    /** 덩이(세그먼트·청크·채움 바이트) 수 상한 — 0 길이 덩이 수백만 개로 훑기를 붙잡지 못하게. */
    private const val MAX_CHUNKS = 200_000

    /** 파일 하나에서 걷는 IFD 수 상한. 실물은 다섯 남짓이다(IFD0·Exif·GPS·Interop·IFD1). */
    const val MAX_IFDS = 64

    /** 파일 하나에서 보는 IFD 칸 수 상한. 실물은 수백이다. */
    const val MAX_ENTRIES = 65_536

    /** ExifInterface 1.4.2 의 `IFD_FORMAT_BYTES_PER_FORMAT` — 형식 번호(1~13)의 단위. **형식 13(IFD)이 1 이다**(명세는 4). 시험이 라이브러리와 견준다. */
    val FORMAT_BYTES: List<Int> = listOf(0, 1, 1, 2, 4, 8, 1, 1, 2, 4, 8, 4, 8, 1)

    /**
     * 형식 7(UNDEFINED)로 적혀도 ExifInterface 가 **1 바이트로** 잡는 태그 — 그 번호를 아는 **모든** 표에서 `primaryFormat` 이 UNDEFINED 다
     * (`MakerNote`·`UserComment`·`FlashpixVersion`·GPS 의 처리 방식 따위). 그 밖의 번호는 형식 7 이면 [WIDEST_UNIT] 로 센다. 시험이 라이브러리
     * 표와 견준다.
     */
    val UNDEFINED_TAGS: Set<Int> = setOf(
        0x001B, 0x001C, 0x002E, 0x8828, 0x9101, 0x927C, 0x9286, 0xA000, 0xA20C, 0xA300, 0xA301, 0xA302, 0xA40B,
    )

    /** ExifInterface 의 어느 태그든 본디 형식의 가장 큰 단위(RATIONAL·DOUBLE). */
    const val WIDEST_UNIT = 8

    /** 따라가는 포인터 — SubIFD·Exif·GPS·Interop. ExifInterface 가 JPEG·PNG·WebP 에서 따라가는 것 전부다(ORF 의 둘은 ORF 갈래에서만). */
    val POINTER_TAGS: Set<Int> = setOf(SUB_IFD, 0x8769, 0x8825, 0xA005)

    private const val SUB_IFD = 0x014A
    private const val JPEG_INTERCHANGE_FORMAT = 0x0201
    private const val JPEG_INTERCHANGE_FORMAT_LENGTH = 0x0202
    private const val STRIP_BYTE_COUNTS = 0x0117
    private const val UNDEFINED = 7

    /** [pos] 에서 [n] 바이트를 [dst] 에 읽는다. 끝이면 읽은 만큼(더 적게) 돌려준다. */
    fun interface Source {
        fun read(pos: Long, dst: ByteArray, n: Int): Int
    }

    /** 바이트 토막 — HEIF 는 넘기지 않는다. */
    fun allows(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): Boolean =
        passes(inspect(bytes, offset, length))

    /**
     * 이 파일을 **경로째** ExifInterface 에 넘겨도 되는가(쓰기·정제 — 회전·위치 지우기·공유 사본). HEIF·AVIF 는 넘기지 않는다([Verdict.HEIF]).
     * 읽기만 할 것이면 [openForExif] 를 쓴다(HEIF 의 Exif 도 읽힌다). 열지 못하면 `IOException` 을 던진다 — 부르는 쪽이 '열지 못했다' 로 가른다.
     */
    fun allows(file: File): Boolean = passes(inspect(file))

    private fun passes(v: Verdict) = v == Verdict.SAFE || v == Verdict.NO_EXIF

    /** 바이트 배열의 한 토막(만화 쪽처럼 이미 메모리에 있는 것). 범위가 배열 밖이면 [Verdict.UNSAFE]. */
    fun inspect(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): Verdict {
        if (offset < 0 || length < 0 || offset > bytes.size - length) return Verdict.UNSAFE
        return inspect(length.toLong()) { pos, dst, n ->
            if (pos >= length) {
                0
            } else {
                val k = minOf(n.toLong(), length - pos).toInt()
                System.arraycopy(bytes, offset + pos.toInt(), dst, 0, k)
                k
            }
        }
    }

    /** 파일. HEIF·AVIF 는 [Verdict.HEIF] 다(블록은 [openForExif] 가 본다). */
    fun inspect(file: File): Verdict = RandomAccessFile(file, "r").use { inspect(it.channel) }

    /** 이미 연 파일의 채널. **위치를 지정해 읽으므로 채널의 위치를 옮기지 않는다.** */
    fun inspect(channel: FileChannel): Verdict = inspect(channel.size(), sourceOf(channel))

    /**
     * **읽기만 하는 호출부의 문**(방향·정보 패널·찍은 날·날짜 옮기기) — 넘겨도 되는 파일이면 그 파일을, HEIF·AVIF 면 검사기가 걸어 본 Exif
     * 블록(`Exif\0\0` 머리 포함)만 [STREAM_TYPE_EXIF_DATA_ONLY] 로 돌려준다(ExifInterface 의 HEIF 갈래를 타지 않는다). 넘기지 않을 것이면 null —
     * UNSAFE, HEIF 인데 추출기가 없거나 Exif 가 없거나 블록이 [MAX_METADATA] 를 넘는 것, [requireExif] 이고 EXIF 가 없는 것. 열지 못하면
     * `IOException` 을 던진다.
     */
    fun openForExif(file: File, requireExif: Boolean = false): ExifInput? {
        val stream = FileInputStream(file)
        var handed = false
        try {
            val opened = openForExif(stream, requireExif)
            handed = opened?.input === stream
            return opened
        } finally {
            if (!handed) stream.close()
        }
    }

    /**
     * 이미 연 [stream] 으로 — 넘겨도 되면 **그 스트림째**(위치는 처음 그대로 — 검사는 채널에서 자리를 지정해 읽는다. 검사한 파일 = 읽는 파일),
     * HEIF 면 블록의 새 스트림. [stream] 은 부른 쪽이 닫는다. HEIF 의 추출기에는 이 스트림의 서술자를 넘긴다(경로를 다시 열지 않는다).
     */
    fun openForExif(stream: FileInputStream, requireExif: Boolean = false): ExifInput? {
        val channel = stream.channel
        val size = channel.size()
        val source = sourceOf(channel)
        return when (inspect(size, source)) {
            Verdict.SAFE -> ExifInput(stream, STREAM_TYPE_FULL_IMAGE_DATA, emptyMap())
            Verdict.NO_EXIF -> if (requireExif) null else ExifInput(stream, STREAM_TYPE_FULL_IMAGE_DATA, emptyMap())
            Verdict.UNSAFE -> null
            Verdict.HEIF -> heif(stream.fd, size, source)
        }
    }

    private fun sourceOf(channel: FileChannel) = Source { pos, dst, n ->
        val buf = ByteBuffer.wrap(dst, 0, n)
        var p = pos
        while (buf.hasRemaining()) {
            val r = channel.read(buf, p)
            if (r < 0) break
            p += r
        }
        n - buf.remaining()
    }

    /** [size] 바이트짜리 그릇. 갈래는 ExifInterface 의 `getMimeType` 과 같은 서명으로 고른다(HEIF 는 [Verdict.HEIF] 로 돌려준다). */
    fun inspect(size: Long, source: Source): Verdict {
        val head = ByteArray(RAF_ID.size)
        val got = source.read(0, head, head.size)
        val walk = Walk()
        return when {
            got >= 3 && isJpeg(head) -> jpeg(size, source, walk)
            got >= 8 && isPng(head) -> png(size, source, walk)
            got >= 12 && isWebp(head) -> webp(size, head, source, walk)
            got >= 8 && isBmff(head) -> Verdict.HEIF
            isTiffFamily(head, got) -> Verdict.UNSAFE
            else -> Verdict.SAFE
        }
    }

    /**
     * HEIF·AVIF 의 Exif 블록 — 추출기가 [fd] 로 Exif 자리(키 33)·길이(키 34)를 주면, ExifInterface 의 `getHeifAttributes` 처럼 길이가 6 보다 커야
     * 하고 첫 6 바이트가 `Exif\0\0` 이어야 한다(아니면 Exif 가 없는 것). 길이는 [MAX_METADATA] 이하이고 파일 안이어야 하며, 그 뒤 `길이 − 6`
     * 바이트의 TIFF 블록을 다른 그릇과 같이 걷는다(블록 폭탄은 null). 블록을 **이 채널에서 자리를 지정해** 읽어(추출기가 서술자의 위치를 옮겨도
     * 상관없다) 머리째 [STREAM_TYPE_EXIF_DATA_ONLY] 입력으로 돌려준다. 추출기가 없거나 답하지 못하거나 숫자가 아니면 null.
     */
    private fun heif(fd: FileDescriptor, size: Long, source: Source): ExifInput? {
        val locator = heifLocator ?: return null
        val boxes = try {
            locator.locate(fd)
        } catch (e: Exception) {
            null
        } ?: return null
        val offset = boxes.exifOffset?.toIntOrNull() ?: return null
        val length = boxes.exifLength?.toIntOrNull() ?: return null
        if (length <= EXIF_ID.size || offset < 0 || length > MAX_METADATA || offset.toLong() + length > size) return null
        val block = ByteArray(length)
        if (source.read(offset.toLong(), block, block.size) < block.size) return null
        if (!(0 until EXIF_ID.size).all { block[it] == EXIF_ID[it] }) return null
        if (!tiff(block, EXIF_ID.size, length - EXIF_ID.size, primaryMayBeThumbnail = false, Walk())) return null
        return ExifInput(ByteArrayInputStream(block), STREAM_TYPE_EXIF_DATA_ONLY, heifFallbacks(boxes))
    }

    /** ExifInterface 의 HEIF 갈래가 Exif 앞에 넣는 셋 — 회전 90·180·270 은 방향 6·3·8, 그 밖의 수는 1(바이트코드의 `lookupswitch`). */
    private fun heifFallbacks(boxes: HeifBoxes): Map<String, Int> = buildMap {
        boxes.width?.toIntOrNull()?.takeIf { it >= 0 }?.let { put("ImageWidth", it) }
        boxes.height?.toIntOrNull()?.takeIf { it >= 0 }?.let { put("ImageLength", it) }
        boxes.rotation?.toIntOrNull()?.let { put("Orientation", when (it) { 90 -> 6; 180 -> 3; 270 -> 8; else -> 1 }) }
    }

    private val PNG_SIG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    /** ExifInterface 의 `PNG_ITXT_XMP_KEYWORD` — 키워드와 그 뒤의 NUL 다섯(압축 표시·방식·언어·번역 키워드의 빈 칸). */
    private val XMP_KEYWORD = "XML:com.adobe.xmp".toByteArray(Charsets.ISO_8859_1) + ByteArray(5)

    /** ExifInterface 의 `IDENTIFIER_EXIF_APP1`. JPEG APP1 과 WebP `EXIF` 청크의 앞머리(WebP 에서는 있으면 뗀다). */
    private val EXIF_ID = "Exif\u0000\u0000".toByteArray(Charsets.ISO_8859_1)

    /** ExifInterface 의 `RAF_SIGNATURE`. */
    private val RAF_ID = "FUJIFILMCCD-RAW".toByteArray(Charsets.ISO_8859_1)

    private fun isJpeg(h: ByteArray) = h[0] == 0xFF.toByte() && h[1] == 0xD8.toByte() && h[2] == 0xFF.toByte()

    private fun isPng(h: ByteArray) = (0 until 8).all { h[it] == PNG_SIG[it] }

    /** ISO BMFF — 4 바이트 자리의 `ftyp` 상자. ExifInterface 는 상표(heic·mif1·avif …)까지 보지만 여기서는 상자만 보아 더 넓게 잡는다. */
    private fun isBmff(h: ByteArray) =
        h[4] == 'f'.code.toByte() && h[5] == 't'.code.toByte() && h[6] == 'y'.code.toByte() && h[7] == 'p'.code.toByte()

    private fun isWebp(h: ByteArray) =
        h[0] == 'R'.code.toByte() && h[1] == 'I'.code.toByte() && h[2] == 'F'.code.toByte() && h[3] == 'F'.code.toByte() &&
            h[8] == 'W'.code.toByte() && h[9] == 'E'.code.toByte() && h[10] == 'B'.code.toByte() && h[11] == 'P'.code.toByte()

    /**
     * ExifInterface 가 TIFF 로 읽는 내용인가. 서명이 맞지 않는 파일은 `getRawAttributes` 로 가서 바이트 차례 표지(`II`·`MM`)가 아니면 곧바로
     * 멈춘다 — 그러니 앞 두 바이트가 `II`·`MM` 인 것(TIFF·DNG·ORF·RW2·PEF …)과 RAF 가 그 갈래의 전부다.
     */
    private fun isTiffFamily(h: ByteArray, got: Int): Boolean {
        if (got >= 2) {
            val a = h[0].toInt()
            if (a == h[1].toInt() && (a == 'I'.code || a == 'M'.code)) return true
        }
        return got >= RAF_ID.size && h.contentEquals(RAF_ID)
    }

    /**
     * JPEG(`getJpegAttributes`) — SOI 뒤 `FF (FF)* 표지` 를 차례로 읽는다. 표지 앞이 `FF` 가 아니면, EOI·SOS 면, 길이가 2 보다 작거나 파일
     * 밖이면 ExifInterface 도 멈춘다. 다른 세그먼트(RSTn 포함)는 길이만큼 건너뛴다. **APP1 이 `Exif\0\0` 으로 시작하면 그 뒤가 TIFF 블록이다**
     * — 세그먼트마다(여럿이어도 모두).
     */
    private fun jpeg(size: Long, source: Source, walk: Walk): Verdict {
        val b = ByteArray(EXIF_ID.size)
        var pos = 2L
        var steps = 0
        var exif = false
        scan@ while (true) {
            if (++steps > MAX_CHUNKS) return Verdict.UNSAFE
            if (source.read(pos, b, 1) < 1 || b[0] != 0xFF.toByte()) break
            pos++
            var marker: Int
            do {
                if (source.read(pos, b, 1) < 1) break@scan
                pos++
                marker = b[0].toInt() and 0xFF
                if (marker == 0xFF && ++steps > MAX_CHUNKS) return Verdict.UNSAFE
            } while (marker == 0xFF)
            if (marker == 0xD9 || marker == 0xDA) break
            if (source.read(pos, b, 2) < 2) break
            pos += 2
            val length = (((b[0].toInt() and 0xFF) shl 8) or (b[1].toInt() and 0xFF)) - 2
            if (length < 0 || pos + length > size) break
            if (marker == 0xE1 && length >= EXIF_ID.size) {
                if (source.read(pos, b, EXIF_ID.size) < EXIF_ID.size) break
                if (b.contentEquals(EXIF_ID)) {
                    exif = true
                    val payload = ByteArray(length)
                    if (source.read(pos, payload, length) < length) break
                    if (!tiff(payload, EXIF_ID.size, length - EXIF_ID.size, primaryMayBeThumbnail = false, walk)) return Verdict.UNSAFE
                }
            }
            pos += length
        }
        return if (exif) Verdict.SAFE else Verdict.NO_EXIF
    }

    /**
     * PNG(`getPngAttributes`) — 서명 뒤 덩이를 앞에서부터 읽는다. 첫 `eXIf` 는 길이만큼 잡아 **TIFF 블록으로 걷고**(그 뒤 `validateImages` 가
     * IFD0 과 썸네일을 바꿔 칠 수 있어 IFD0 의 썸네일 칸도 본다), 키워드가 `XML:com.adobe.xmp` 인 첫 `iTXt` 는 `길이 − 22` 만큼 잡는다. **둘을
     * 다 찾거나 `IEND` 나 파일 끝에 닿아야** 멈춘다 — `eXIf` 에서 멈추면 그 뒤의 큰 XMP `iTXt` 를 놓친다. 덩이마다 `4 + 4 + 길이 + 4` 가
     * 파일 안이어야 하고, **잡히는 둘만** [MAX_METADATA] 이하여야 한다(`tEXt`·`zTXt`·다른 `iTXt` 는 범위만 본다 — 생성 이미지의 큰 JSON 을
     * 거절하지 않게). `IEND` 없이 덩이 경계에서 끝난 파일(덜 받은 파일)은 통과 — ExifInterface 도 거기서 `EOFException` 으로 멈춘다.
     */
    private fun png(size: Long, source: Source, walk: Walk): Verdict {
        val hdr = ByteArray(8)
        val keyword = ByteArray(XMP_KEYWORD.size)
        var pos = 8L
        var chunks = 0
        var exif = false
        var xmp = false
        while (!(exif && xmp)) {
            if (pos == size) break // 덩이 경계에서 끝났다 — ExifInterface 도 여기서 멈춘다
            if (++chunks > MAX_CHUNKS) return Verdict.UNSAFE
            if (source.read(pos, hdr, 8) < 8) return Verdict.UNSAFE // 덩이 머리가 잘렸다
            val length = beInt(hdr, 0)
            val type = String(hdr, 4, 4, Charsets.ISO_8859_1)
            val end = pos + 8 + length + 4
            if (end > size) return Verdict.UNSAFE
            if (type == "IEND") break
            if (type == "eXIf" && !exif) {
                if (length > MAX_METADATA) return Verdict.UNSAFE
                exif = true
                val data = ByteArray(length.toInt())
                if (source.read(pos + 8, data, data.size) < data.size) return Verdict.UNSAFE
                if (!tiff(data, 0, data.size, primaryMayBeThumbnail = true, walk)) return Verdict.UNSAFE
            } else if (type == "iTXt" && !xmp && length >= keyword.size) {
                if (source.read(pos + 8, keyword, keyword.size) < keyword.size) return Verdict.UNSAFE
                if (keyword.contentEquals(XMP_KEYWORD)) {
                    if (length - keyword.size > MAX_METADATA) return Verdict.UNSAFE
                    xmp = true
                }
            }
            pos = end
        }
        return if (exif) Verdict.SAFE else Verdict.NO_EXIF
    }

    /**
     * WebP(`getWebpAttributes`) — ExifInterface 의 고리를 그대로 따른다(바이트코드 73-232). 12 바이트 자리부터 **RIFF 크기와 상관없이** 청크 머리를
     * 읽고, `EXIF` 면 적힌 길이만큼 곧바로 잡아(앞의 `Exif\0\0` 은 떼고) TIFF 블록으로 읽고 멈춘다 — 그래서 `EXIF` 는 길이가 0 이상
     * [MAX_METADATA] 이하이고 파일 안이어야 한다(음수면 `NegativeArraySizeException`). 다른 청크(`XMP ` 포함)는 잡지 않고, 길이를 짝수로 올려
     * 'ExifInterface 가 센 자리 + 길이' 가 RIFF 끝과 같으면 멈추고 넘으면 멈추고('invalid chunk size'), 아니면 건너뛴다. 센 자리는 int 라 넘칠 수
     * 있다 — 그것도 그대로 따른다. 따로 더 엄격히 보는 것 하나 — RIFF 크기가 파일보다 2 바이트 이상 크면(덜 받은 파일) 넘기지 않는다(마지막
     * 청크의 채움 바이트만 빠진 흔한 파일은 받는다).
     */
    private fun webp(size: Long, head: ByteArray, source: Source, walk: Walk): Verdict {
        if (8 + leInt(head, 4) > size + 1) return Verdict.UNSAFE
        val riffEnd = leInt(head, 4).toInt() + 8 // ExifInterface 의 int 셈 그대로
        val hdr = ByteArray(8)
        var at = 12L // 흐름이 실제로 읽은 자리
        var counted = 12 // ExifInterface 가 세는 자리(int)
        var chunks = 0
        while (true) {
            if (++chunks > MAX_CHUNKS) return Verdict.UNSAFE
            if (source.read(at, hdr, 8) < 8) return Verdict.NO_EXIF // EOF — ExifInterface 도 여기서 멈춘다
            at += 8
            counted += 8
            val type = String(hdr, 0, 4, Charsets.ISO_8859_1)
            val length = leInt(hdr, 4).toInt()
            if (type == "EXIF") {
                if (length < 0 || length > MAX_METADATA || at + length > size) return Verdict.UNSAFE
                val data = ByteArray(length)
                if (source.read(at, data, data.size) < data.size) return Verdict.UNSAFE
                val skip = if (data.size >= EXIF_ID.size && data.copyOf(EXIF_ID.size).contentEquals(EXIF_ID)) EXIF_ID.size else 0
                return if (tiff(data, skip, data.size - skip, primaryMayBeThumbnail = false, walk)) Verdict.SAFE else Verdict.UNSAFE
            }
            val padded = if (length % 2 == 1) length + 1 else length
            if (counted + padded >= riffEnd) return Verdict.NO_EXIF // 같으면 끝, 넘으면 'invalid chunk size'
            if (padded > 0) at += padded // skipFully 는 0 이하를 건너뛰지 않는다
            counted += padded
        }
    }

    /** 파일 하나를 걷는 동안의 셈 — 할당 예산과 IFD·칸 수. JPEG 의 APP1 여럿이 한 셈을 나눠 쓴다(ExifInterface 가 앞의 것을 쥔 채 읽는다). */
    private class Walk {
        var budget = MAX_METADATA
        var ifds = 0
        var entries = 0L

        fun spend(n: Long): Boolean {
            budget -= n
            return budget >= 0
        }
    }

    /** IFD 가 어디서 왔는가 — 썸네일 칸을 볼 자리를 가른다. */
    private enum class Kind {
        /** IFD0. PNG 에서만 썸네일이 될 수 있다(`validateImages` 의 맞바꿈). */
        PRIMARY,

        /** Exif·GPS·Interop — 그 표에 썸네일 태그가 없다. */
        SUB,

        /** 다음 IFD·SubIFD — 썸네일·미리보기 자리. */
        CHAIN,
    }

    private class Ifd(val at: Long, val kind: Kind)

    /** TIFF 블록 [b] 의 [base] 부터 [len] 바이트. 자리는 블록 첫 바이트(바이트 차례 표지)에서 센다 — ExifInterface 의 오프셋과 같다. */
    private class Tiff(private val b: ByteArray, private val base: Int, val len: Int, private val le: Boolean) {
        private fun at(p: Long, i: Int) = b[base + p.toInt() + i].toInt() and 0xFF

        fun u16(p: Long): Int = if (le) at(p, 0) or (at(p, 1) shl 8) else (at(p, 0) shl 8) or at(p, 1)

        fun s16(p: Long): Int = u16(p).toShort().toInt()

        fun u32(p: Long): Long =
            if (le) {
                at(p, 0).toLong() or (at(p, 1).toLong() shl 8) or (at(p, 2).toLong() shl 16) or (at(p, 3).toLong() shl 24)
            } else {
                (at(p, 0).toLong() shl 24) or (at(p, 1).toLong() shl 16) or (at(p, 2).toLong() shl 8) or at(p, 3).toLong()
            }

        fun s32(p: Long): Long = u32(p).toInt().toLong()
    }

    /**
     * TIFF 블록 하나를 걷는다(`readExifSegment` → `parseTiffHeaders` → `readImageFileDirectory`). 거짓이면 UNSAFE. 규칙은 머리말의 '판정'.
     *
     * @param primaryMayBeThumbnail IFD0 의 썸네일 칸도 본다(PNG — `validateImages` 가 IFD0 과 썸네일을 맞바꿀 수 있다).
     */
    private fun tiff(b: ByteArray, base: Int, len: Int, primaryMayBeThumbnail: Boolean, walk: Walk): Boolean {
        if (len < 8) return true // 머리도 다 없다 — ExifInterface 는 EOF 로 멈춘다
        val le = when {
            b[base] == 'I'.code.toByte() && b[base + 1] == 'I'.code.toByte() -> true
            b[base] == 'M'.code.toByte() && b[base + 1] == 'M'.code.toByte() -> false
            else -> return true // 'Invalid byte order' — IFD 를 읽기 전에 멈춘다
        }
        val t = Tiff(b, base, len, le)
        if (t.u16(2) != 42) return true // 'Invalid start code'
        val first = t.s32(4)
        if (first < 8 || first >= len) return true // 'Invalid first Ifd offset' 이거나 건너뛰다 EOF
        val queue = ArrayDeque<Ifd>()
        val visited = HashSet<Long>()
        queue.addLast(Ifd(first, Kind.PRIMARY))
        while (queue.isNotEmpty()) {
            val ifd = queue.removeFirst()
            if (!visited.add(ifd.at)) continue // ExifInterface 도 읽은 자리는 다시 읽지 않는다
            if (++walk.ifds > MAX_IFDS) return false
            if (ifd.at + 2 > len) continue
            val n = t.s16(ifd.at)
            if (n <= 0) continue // 개수를 부호 있는 short 로 읽고 0 이하면 그 IFD 를 끝낸다(다음 IFD 도 읽지 않는다)
            walk.entries += n
            if (walk.entries > MAX_ENTRIES) return false
            val thumbnail = ifd.kind == Kind.CHAIN || (ifd.kind == Kind.PRIMARY && primaryMayBeThumbnail)
            var jfifOffset = -1L
            var jfifLength = -1L
            var strips = -1L
            var whole = true
            for (i in 0 until n) {
                val e = ifd.at + 2 + 12L * i
                if (e + 12 > len) {
                    whole = false // 블록 끝에서 잘린 IFD — ExifInterface 는 여기서 EOF 로 멈춘다
                    break
                }
                val tag = t.u16(e)
                val format = t.u16(e + 2)
                val count = t.u32(e + 4)
                if (format !in 1..13) continue // 'data format is invalid' — 할당 없이 건너뛴다
                // 개수는 int 로 읽힌다 — 부호 비트가 켜지면 개수 × 단위가 음수라 'number of components is invalid' 로 건너뛴다.
                if (count > Int.MAX_VALUE) continue
                val allocUnit = when {
                    format != UNDEFINED -> FORMAT_BYTES[format]
                    tag in UNDEFINED_TAGS -> 1
                    else -> WIDEST_UNIT
                }
                // 자료가 블록 밖이어도 거절하지 않는다 — 잡는 크기는 개수 × 단위뿐이고(예산이 묶는다) 읽다 EOF 로 멈출 뿐이다.
                if (!walk.spend(count * allocUnit)) return false
                if (tag in POINTER_TAGS) {
                    val target = pointer(t, e, format, count)
                    if (target != null && target in 1 until len) queue.addLast(Ifd(target, if (tag == SUB_IFD) Kind.CHAIN else Kind.SUB))
                }
                if (thumbnail) {
                    when (tag) {
                        JPEG_INTERCHANGE_FORMAT -> firstValue(t, e, format, count)?.let { jfifOffset = it }
                        JPEG_INTERCHANGE_FORMAT_LENGTH -> firstValue(t, e, format, count)?.let { jfifLength = it }
                        STRIP_BYTE_COUNTS -> stripSum(t, e, format, count)?.let { strips = it }
                    }
                }
            }
            // 썸네일 — `handleThumbnailFromJfif` 는 길이만큼(오프셋은 잡은 뒤에 건너뛰는 데만 쓴다), `handleThumbnailFromStrips` 는 띠의 합만큼
            // 잡고 띠마다 그 길이만큼 한 번 더 잡는다. 크기는 길이로만 정해지므로 자리가 블록 밖인지는 보지 않는다 — 예산에만 센다.
            // (오프셋이 0 이하면 ExifInterface 는 잡지 않지만 여기서는 길이만 보고 센다 — 더 엄격한 쪽이다.)
            if (jfifLength > 0 && !walk.spend(jfifLength)) return false
            if (strips > 0 && !walk.spend(2 * strips)) return false
            if (whole) {
                val nextAt = ifd.at + 2 + 12L * n
                if (nextAt + 4 <= len) {
                    val next = t.s32(nextAt)
                    if (next in 1 until len) queue.addLast(Ifd(next, Kind.CHAIN))
                }
            }
        }
        return true
    }

    /**
     * 포인터 칸이 가리키는 자리. ExifInterface 는 형식 7 을 본디 형식(ULONG)으로 바꾸고, `개수 × 단위` 가 4 를 넘으면 칸의 오프셋에서, 아니면
     * 칸 안에서 첫 값을 읽는다(USHORT·ULONG·SSHORT·SLONG·IFD 만 — 나머지는 따라가지 않는다). 읽을 자리가 블록 밖이면 null.
     */
    private fun pointer(t: Tiff, e: Long, format: Int, count: Long): Long? {
        val f = if (format == UNDEFINED) 4 else format
        val size = when (f) {
            3, 8 -> 2
            4, 9, 13 -> 4
            else -> return null
        }
        val at = if (count * FORMAT_BYTES[f] > 4) t.u32(e + 8) else e + 8
        if (at + size > t.len) return null
        return when (f) {
            3 -> t.u16(at).toLong()
            8 -> t.s16(at).toLong()
            4 -> t.u32(at)
            else -> t.s32(at)
        }
    }

    /** `JPEGInterchangeFormat`·`…Length`(본디 ULONG — USHORT·형식 7 도 받는다)의 첫 값. 읽을 수 없거나 개수가 0 이면 null. */
    private fun firstValue(t: Tiff, e: Long, format: Int, count: Long): Long? {
        if (count < 1) return null
        val f = if (format == UNDEFINED) 4 else format
        val unit = when (f) {
            3 -> 2
            4 -> 4
            else -> return null
        }
        val at = if (count * unit > 4) t.u32(e + 8) else e + 8
        if (at + unit > t.len) return null
        return if (f == 3) t.u16(at).toLong() else t.u32(at)
    }

    /**
     * `StripByteCounts`(본디 USHORT — ULONG·형식 7 도 받는다)의 합. 값이 블록 밖이면 null — ExifInterface 는 그 칸을 읽다 EOF 로 멈춰 띠를
     * 잡지 못한다. 합이 [MAX_METADATA] 를 넘는 순간 멈춘다(그것으로 판정이 났다).
     */
    private fun stripSum(t: Tiff, e: Long, format: Int, count: Long): Long? {
        val f = if (format == UNDEFINED) 3 else format
        val unit = when (f) {
            3 -> 2
            4 -> 4
            else -> return null
        }
        val bytes = count * unit
        val at = if (bytes > 4) t.u32(e + 8) else e + 8
        if (at + bytes > t.len) return null
        var sum = 0L
        for (i in 0 until count) {
            val p = at + i * unit
            sum += if (f == 3) t.u16(p).toLong() else t.u32(p)
            if (sum > MAX_METADATA) break
        }
        return sum
    }

    private fun beInt(b: ByteArray, at: Int): Long =
        ((b[at].toLong() and 0xFF) shl 24) or ((b[at + 1].toLong() and 0xFF) shl 16) or
            ((b[at + 2].toLong() and 0xFF) shl 8) or (b[at + 3].toLong() and 0xFF)

    private fun leInt(b: ByteArray, at: Int): Long =
        (b[at].toLong() and 0xFF) or ((b[at + 1].toLong() and 0xFF) shl 8) or
            ((b[at + 2].toLong() and 0xFF) shl 16) or ((b[at + 3].toLong() and 0xFF) shl 24)
}
