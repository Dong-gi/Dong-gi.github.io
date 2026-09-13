package io.github.donggi.iroiroviewer.ui.image

import java.io.File
import java.io.RandomAccessFile

/**
 * 파일의 **겉모습이 아니라 내용**으로 판별해야 하는 몇 가지.
 *
 * 확장자만으로는 답할 수 없는 질문이 둘 있다 — "이 WebP 는 움직이는가", "이 PNG 는
 * APNG 인가". 둘 다 확장자가 같다.
 *
 * ## 왜 파일과 바이트 두 입구가 있는가
 *
 * 6단계에서는 질문이 "**우리가 이 파일을 고쳐도 되는가**"(EXIF 회전 저장) 하나였고
 * 대상은 언제나 디스크 위의 파일이었다. 9단계가 두 번째 질문을 더한다 —
 * "**이 쪽을 움직이게 틀어야 하는가**". 그 대상은 아카이브 안의 쪽이고 경로가 없다
 * (`ImageIo.decodeFitted(bytes, …)` 가 같은 이유로 바이트 입구를 둔다).
 *
 * 두 입구가 **같은 훑개**를 쓰게 [Peek] 으로 한 겹 얇게 감쌌다. 같은 규칙을 두 번 적으면
 * 한쪽만 고치는 날이 온다.
 */
object ImageFormats {

    /**
     * 움직이는 WebP 인가.
     *
     * ## 왜 이것을 알아야 하는가
     *
     * 첫째, `ExifInterface.saveAttributes()` 의 WebP 경로는 컨테이너를 **통째로 다시
     * 쓴다.** 그 과정에서 애니메이션 프레임(`ANMF` 청크들)이 보존되는지 우리는 확인하지
     * 못했다 — 표본을 만들 도구가 없었다. 확인하지 못한 것을 통과시키면, 실패했을 때
     * 사용자 파일이 **원자적으로, 되돌릴 수 없게** 프레임을 잃는다.
     *
     * 둘째, 만화 쪽이 움직이는 WebP 이면 정지 비트맵이 아니라 `AnimatedImageDrawable`
     * 로 틀어야 한다. 플랫폼이 정지 WebP 와 같은 MIME 을 주므로 이 판별이 먼저다.
     *
     * ## 어떻게 판별하는가
     *
     * RIFF 컨테이너의 청크 머리만 훑는다. `RIFF....WEBP` 다음에 `VP8X`(확장 헤더)가 오고,
     * 그 플래그의 **애니메이션 비트** 또는 `ANIM` 청크가 있으면 움직이는 것이다.
     * 픽셀은 한 바이트도 읽지 않는다.
     */
    fun isAnimatedWebp(file: File): Boolean =
        runCatching { RandomAccessFile(file, "r").use { animatedWebp(FilePeek(it)) } }
            .getOrDefault(false)

    /** 메모리 위의 바이트로 같은 것을 묻는다. 아카이브 안의 쪽이 이쪽이다. */
    fun isAnimatedWebp(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): Boolean =
        runCatching { animatedWebp(BytePeek(bytes, offset, length)) }.getOrDefault(false)

    /**
     * APNG(움직이는 PNG)인가.
     *
     * 플랫폼은 APNG 을 `image/png` 에 `isAnimated=false` 로 준다 — 정지 PNG 과 구별할
     * 방법이 **`acTL` 청크를 우리가 직접 찾는 것**뿐이다. 화면이 "이 파일은 움직이지만
     * 첫 장면만 보여 줍니다" 라고 정직하게 말하려면 이것이 먼저 있어야 한다.
     */
    fun isApng(file: File): Boolean =
        runCatching { RandomAccessFile(file, "r").use { apng(FilePeek(it)) } }
            .getOrDefault(false)

    /** 메모리 위의 바이트로 같은 것을 묻는다. */
    fun isApng(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): Boolean =
        runCatching { apng(BytePeek(bytes, offset, length)) }.getOrDefault(false)

    /**
     * 플랫폼이 **틀 수 있는** 애니메이션인가.
     *
     * APNG 은 여기서 거짓이다. 움직이는 파일이지만 안드로이드 디코더가 첫 장면만 주고,
     * 청크를 우리가 분해해 합성하는 것은 CRC 를 다시 계산해 skia 에 먹이는 일이라
     * 방어 상한과 악성 표본이 먼저 필요하다(6단계가 미룬 이유 그대로다).
     * 그래서 [isApng] 는 따로 물어서 **말해 주는 데**만 쓴다.
     */
    fun playsAnimated(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): Boolean =
        isGif(bytes, offset, length) || isAnimatedWebp(bytes, offset, length)

    /** GIF 인가. 한 장짜리 GIF 도 참이다 — 플랫폼이 그것을 알아서 정지로 그린다. */
    fun isGif(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): Boolean {
        if (length < 6 || offset < 0 || offset + length > bytes.size) return false
        val head = String(bytes, offset, 6, Charsets.US_ASCII)
        return head == "GIF87a" || head == "GIF89a"
    }

    // ---- 훑개 ------------------------------------------------------------------

    private fun animatedWebp(p: Peek): Boolean {
        if (p.size < 16) return false
        val header = ByteArray(12)
        if (!p.read(0, header, 12)) return false
        if (String(header, 0, 4, Charsets.US_ASCII) != "RIFF") return false
        if (String(header, 8, 4, Charsets.US_ASCII) != "WEBP") return false

        var pos = 12L
        var scanned = 0
        // 청크를 앞에서 몇 개만 본다. ANIM 은 언제나 앞쪽에 있고, 깨진 파일에서
        // 끝없이 도는 것을 막는다.
        val tag = ByteArray(4)
        val len = ByteArray(4)
        while (pos + 8 <= p.size && scanned < MAX_CHUNKS) {
            if (!p.read(pos, tag, 4)) return false
            if (!p.read(pos + 4, len, 4)) return false
            val size = le32(len)
            if (size < 0) return false
            when (String(tag, Charsets.US_ASCII)) {
                "ANIM", "ANMF" -> return true
                "VP8X" -> {
                    // VP8X 의 첫 바이트가 기능 플래그다. 0x02 가 애니메이션.
                    val flags = ByteArray(1)
                    if (size >= 1 && p.read(pos + 8, flags, 1) &&
                        (flags[0].toInt() and 0x02) != 0
                    ) {
                        return true
                    }
                }
            }
            // 청크는 짝수 경계에 놓인다.
            pos += 8 + size + (size and 1)
            scanned++
        }
        return false
    }

    private fun apng(p: Peek): Boolean {
        if (p.size < 16) return false
        val sig = ByteArray(8)
        if (!p.read(0, sig, 8)) return false
        if (!sig.contentEquals(PNG_SIGNATURE)) return false

        var pos = 8L
        var scanned = 0
        val len = ByteArray(4)
        val tag = ByteArray(4)
        while (pos + 8 <= p.size && scanned < MAX_CHUNKS) {
            if (!p.read(pos, len, 4)) return false
            val size = be32(len)
            if (size < 0) return false
            if (!p.read(pos + 4, tag, 4)) return false
            when (String(tag, Charsets.US_ASCII)) {
                // acTL 은 반드시 IDAT 앞에 온다. IDAT 를 만나면 정지 PNG 이다.
                "acTL" -> return true
                "IDAT" -> return false
            }
            pos += 12L + size // 길이(4) + 종류(4) + 데이터 + CRC(4)
            scanned++
        }
        return false
    }

    /**
     * 앞에서부터 몇 바이트씩 들여다보는 것. **파일과 바이트 배열이 같은 규칙을 쓰게 한다.**
     *
     * 읽지 못하면 예외가 아니라 `false` 다 — 판별기의 답은 언제나 '그렇다/아니다' 여야
     * 하고, 잘린 파일에서 예외가 나면 부르는 쪽마다 `runCatching` 을 적게 된다.
     */
    private interface Peek {
        val size: Long
        fun read(pos: Long, dst: ByteArray, len: Int): Boolean
    }

    private class FilePeek(private val raf: RandomAccessFile) : Peek {
        override val size: Long get() = raf.length()
        override fun read(pos: Long, dst: ByteArray, len: Int): Boolean {
            if (pos < 0 || pos + len > raf.length()) return false
            raf.seek(pos)
            raf.readFully(dst, 0, len)
            return true
        }
    }

    private class BytePeek(
        private val bytes: ByteArray,
        private val offset: Int,
        length: Int,
    ) : Peek {
        override val size: Long =
            if (offset < 0 || length < 0 || offset + length > bytes.size) 0L else length.toLong()

        override fun read(pos: Long, dst: ByteArray, len: Int): Boolean {
            if (pos < 0 || pos + len > size) return false
            System.arraycopy(bytes, offset + pos.toInt(), dst, 0, len)
            return true
        }
    }

    private fun le32(b: ByteArray): Int {
        val v = (b[0].toInt() and 0xFF) or
            ((b[1].toInt() and 0xFF) shl 8) or
            ((b[2].toInt() and 0xFF) shl 16) or
            ((b[3].toInt() and 0xFF) shl 24)
        // 2GB 를 넘는 청크는 우리가 다룰 것이 아니다.
        return if (v < 0) -1 else v
    }

    private fun be32(b: ByteArray): Int {
        val v = ((b[0].toInt() and 0xFF) shl 24) or
            ((b[1].toInt() and 0xFF) shl 16) or
            ((b[2].toInt() and 0xFF) shl 8) or
            (b[3].toInt() and 0xFF)
        return if (v < 0) -1 else v
    }

    private const val MAX_CHUNKS = 64

    private val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )
}
