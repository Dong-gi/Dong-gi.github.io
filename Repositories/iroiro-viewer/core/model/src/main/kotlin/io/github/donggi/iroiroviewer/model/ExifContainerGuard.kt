package io.github.donggi.iroiroviewer.model

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * ExifInterface 에 **넘겨도 되는 그릇인가**를 덩이 머리만 훑어 본다.
 *
 * ## 왜 필요한가
 *
 * ExifInterface 1.4.2 는 PNG 의 `eXIf` 덩이·XMP `iTXt` 덩이와 WebP 의 `EXIF` 청크를 **적힌 길이대로** `new byte[길이]` 로 잡는다. 길이를
 * `0x7FFFFFF0` 으로 적은 57바이트 PNG 는 `OutOfMemoryError` 를 던지고, 그것은 잡을 수 있다(할당이 실패했으니 힙은 그대로다). 그러나 길이를
 * 힙 바로 아래(192 MB 따위)로 적고 그만큼 채우면 **할당이 성공한다** — 그 순간 주 스레드·썸네일 스레드의 작은 할당이 대신 실패해 앱이 죽는다.
 * 잡는 것으로는 막을 수 없고, 넘기기 전에 길이를 보아야 한다.
 *
 * ## ExifInterface 와 같은 길을 걷는다(1.4.2 바이트코드로 확인)
 *
 * - **PNG**(`getPngAttributes`) — 서명 뒤 덩이를 앞에서부터 읽는다. 첫 `eXIf` 는 길이만큼 잡고, 키워드가 `XML:com.adobe.xmp` 인 첫 `iTXt`
 *   는 `길이 − 22` 만큼 잡는다. **둘을 다 찾거나 `IEND` 나 파일 끝에 닿아야** 멈춘다 — `eXIf` 에서 멈추면 그 뒤의 큰 XMP `iTXt` 를 놓친다
 *   (재검토가 그 꼴로 검사를 지나 OOM 을 냈다). 그래서 여기도 같은 조건으로 멈춘다. 덩이마다 `4 + 4 + 길이 + 4` 가 파일 안이어야 하고,
 *   **잡히는 둘만** [MAX_METADATA] 이하여야 한다(`tEXt`·`zTXt`·다른 `iTXt` 는 범위만 본다 — 생성 이미지의 큰 JSON 을 거절하지 않게).
 *   `IEND` 없이 덩이 경계에서 끝난 파일(덜 받은 파일)은 통과 — ExifInterface 도 거기서 `EOFException` 으로 멈춘다(잡지 않는다).
 * - **WebP**(`getWebpAttributes`) — `RIFF` 뒤 청크를 읽다 `EXIF` 를 만나면 그 길이만큼 잡고 멈춘다. 다른 청크(`XMP ` 포함)는 건너뛸 뿐
 *   잡지 않는다. 청크는 `RIFF` 의 끝과 파일의 끝 안이어야 하고 `EXIF` 는 [MAX_METADATA] 이하. 마지막 청크의 채움 바이트가 파일에 없어
 *   `RIFF` 크기가 파일보다 1 바이트 큰 것은 받는다(흔하다).
 * - **JPEG** — 세그먼트 길이가 16비트라 할당이 64 KiB 를 넘지 못한다. 통과.
 * - 그 밖의 형식은 통과 — ExifInterface 가 그 갈래에서 이렇게 잡는 자리를 확인하지 못했다(HEIF 는 플랫폼 추출기가 길이를 준다).
 *
 * 판정은 **내용의 서명**으로 한다(ExifInterface 도 확장자가 아니라 내용으로 갈래를 고른다).
 */
object ExifContainerGuard {

    /** 판정. */
    enum class Verdict {
        /** 넘기지 마라 — 길이가 터무니없거나 그릇이 깨졌다. */
        UNSAFE,

        /** 넘겨도 되지만 EXIF 가 없다(PNG 의 `eXIf`·WebP 의 `EXIF` 가 없다) — 날짜·방향을 읽으려고 열 까닭이 없다. */
        NO_EXIF,

        /** 넘겨도 된다(EXIF 가 있거나, 있는지 모르는 그릇). */
        SAFE,
    }

    /** ExifInterface 가 잡는 메타데이터 덩이 하나의 상한. 실물의 EXIF 는 대개 수십 KiB, 큰 미리보기를 품어도 수 MiB 다. */
    const val MAX_METADATA: Long = 8L shl 20

    /** 덩이 수 상한 — 0 길이 덩이 수백만 개로 훑기를 붙잡지 못하게. */
    private const val MAX_CHUNKS = 200_000

    /** [pos] 에서 [n] 바이트를 [dst] 에 읽는다. 끝이면 읽은 만큼(더 적게) 돌려준다. */
    fun interface Source {
        fun read(pos: Long, dst: ByteArray, n: Int): Int
    }

    fun allows(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): Boolean =
        inspect(bytes, offset, length) != Verdict.UNSAFE

    /** 열지 못하면 `IOException` 을 던진다 — 부르는 쪽이 '열지 못했다' 로 가른다. */
    fun allows(file: File): Boolean = inspect(file) != Verdict.UNSAFE

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

    fun inspect(file: File): Verdict = RandomAccessFile(file, "r").use { inspect(it.channel) }

    /**
     * 이미 연 파일의 채널. **위치를 지정해 읽으므로 채널의 위치를 옮기지 않는다** — 같은 서술자로 곧이어 ExifInterface 가 읽어도 된다
     * (검사한 파일 = 읽는 파일).
     */
    fun inspect(channel: FileChannel): Verdict = inspect(channel.size()) { pos, dst, n ->
        val buf = ByteBuffer.wrap(dst, 0, n)
        var p = pos
        while (buf.hasRemaining()) {
            val r = channel.read(buf, p)
            if (r < 0) break
            p += r
        }
        n - buf.remaining()
    }

    /** [size] 바이트짜리 그릇. */
    fun inspect(size: Long, source: Source): Verdict {
        val head = ByteArray(12)
        val got = source.read(0, head, 12)
        return when {
            got >= 8 && isPng(head) -> png(size, source)
            got >= 12 && isWebp(head) -> webp(size, head, source)
            else -> Verdict.SAFE
        }
    }

    private val PNG_SIG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    /** ExifInterface 의 `PNG_ITXT_XMP_KEYWORD` — 키워드와 그 뒤의 NUL 다섯(압축 표시·방식·언어·번역 키워드의 빈 칸). */
    private val XMP_KEYWORD = "XML:com.adobe.xmp".toByteArray(Charsets.ISO_8859_1) + ByteArray(5)

    private fun isPng(h: ByteArray) = (0 until 8).all { h[it] == PNG_SIG[it] }

    private fun isWebp(h: ByteArray) =
        h[0] == 'R'.code.toByte() && h[1] == 'I'.code.toByte() && h[2] == 'F'.code.toByte() && h[3] == 'F'.code.toByte() &&
            h[8] == 'W'.code.toByte() && h[9] == 'E'.code.toByte() && h[10] == 'B'.code.toByte() && h[11] == 'P'.code.toByte()

    private fun png(size: Long, source: Source): Verdict {
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

    private fun webp(size: Long, head: ByteArray, source: Source): Verdict {
        val riffEnd = 8 + leInt(head, 4)
        // 마지막 청크의 채움 바이트가 빠진 파일은 RIFF 가 1 바이트 길다 — 받는다. 청크의 몸은 파일 안이어야 한다.
        if (riffEnd > size + 1) return Verdict.UNSAFE
        val limit = minOf(riffEnd, size)
        val hdr = ByteArray(8)
        var pos = 12L
        var chunks = 0
        while (pos < limit) {
            if (++chunks > MAX_CHUNKS) return Verdict.UNSAFE
            if (limit - pos < 8 || source.read(pos, hdr, 8) < 8) return Verdict.UNSAFE
            val type = String(hdr, 0, 4, Charsets.ISO_8859_1)
            val length = leInt(hdr, 4)
            if (pos + 8 + length > limit) return Verdict.UNSAFE
            // ExifInterface 는 EXIF 를 만나면 그만큼 잡고 멈춘다 — 그 뒤는 읽지 않는다.
            if (type == "EXIF") return if (length > MAX_METADATA) Verdict.UNSAFE else Verdict.SAFE
            pos += 8 + length + (length and 1L)
        }
        return Verdict.NO_EXIF
    }

    private fun beInt(b: ByteArray, at: Int): Long =
        ((b[at].toLong() and 0xFF) shl 24) or ((b[at + 1].toLong() and 0xFF) shl 16) or
            ((b[at + 2].toLong() and 0xFF) shl 8) or (b[at + 3].toLong() and 0xFF)

    private fun leInt(b: ByteArray, at: Int): Long =
        (b[at].toLong() and 0xFF) or ((b[at + 1].toLong() and 0xFF) shl 8) or
            ((b[at + 2].toLong() and 0xFF) shl 16) or ((b[at + 3].toLong() and 0xFF) shl 24)
}
