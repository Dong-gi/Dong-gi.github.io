package io.github.donggi.iroiroviewer.format.archive

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * tar 를 **바이트 단위로** 짓는 시험용 작성기.
 *
 * 정상 표본은 우리가 아닌 도구(GNU tar·bsdtar·파이썬, `src/test/resources/tar/`)와 commons-compress 의 작성기가
 * 만든다. 이것은 **도구가 만들어 주지 않는 모양** — 거짓 크기·깨진 검사합·링크에 붙은 크기·옛 GNU 희소 머리 —
 * 을 짓는 데 쓴다. 검사합만 명세대로 계산하고 나머지는 부르는 쪽이 정한다.
 */
internal class TarBytes {

    private val out = ByteArrayOutputStream()

    /**
     * 머리 한 칸. [size] 는 크기 칸에 **적는 값**이고 실제 자료는 [data] 다 — 둘을 다르게 줄 수 있다.
     * [sizeField] 를 주면 크기 칸을 그 바이트로 채운다(256진·음수 시험).
     */
    fun header(
        name: String,
        type: Char = '0',
        size: Long = 0,
        mtime: Long = 1_789_207_200L,
        magic: Magic = Magic.USTAR,
        nameBytes: ByteArray = name.toByteArray(StandardCharsets.UTF_8),
        prefix: ByteArray = ByteArray(0),
        sizeField: ByteArray? = null,
        corruptChecksum: Boolean = false,
        extra: (ByteArray) -> Unit = {},
    ): TarBytes {
        val h = ByteArray(512)
        nameBytes.copyInto(h, 0, 0, minOf(100, nameBytes.size))
        octal(h, 100, 8, 420)
        octal(h, 108, 8, 0)
        octal(h, 116, 8, 0)
        if (sizeField != null) sizeField.copyInto(h, 124) else octal(h, 124, 12, size)
        octal(h, 136, 12, mtime)
        h[156] = type.code.toByte()
        when (magic) {
            Magic.USTAR -> {
                "ustar".toByteArray().copyInto(h, 257)
                h[262] = 0
                h[263] = '0'.code.toByte()
                h[264] = '0'.code.toByte()
            }
            Magic.GNU -> {
                "ustar ".toByteArray().copyInto(h, 257)
                h[263] = ' '.code.toByte()
                h[264] = 0
            }
            Magic.V7 -> Unit
        }
        prefix.copyInto(h, 345, 0, minOf(155, prefix.size))
        extra(h)
        // 검사합: 검사합 칸을 공백으로 보고 전 바이트를 부호 없이 더한다. 6자리 8진 + NUL + 공백.
        for (i in 148 until 156) h[i] = ' '.code.toByte()
        var sum = h.sumOf { it.toInt() and 0xFF }
        if (corruptChecksum) sum += 1
        val digits = sum.toString(8).padStart(6, '0')
        digits.toByteArray().copyInto(h, 148)
        h[154] = 0
        h[155] = ' '.code.toByte()
        out.write(h)
        return this
    }

    /** 자료와 512 배수 채움. */
    fun data(bytes: ByteArray): TarBytes {
        out.write(bytes)
        val pad = (512 - bytes.size % 512) % 512
        out.write(ByteArray(pad))
        return this
    }

    fun file(name: String, body: ByteArray, magic: Magic = Magic.USTAR, mtime: Long = 1_789_207_200L): TarBytes =
        header(name, '0', body.size.toLong(), mtime, magic).data(body)

    /** 채움 없이 날것을 붙인다(잘린 파일·옛 GNU 희소 확장 머리). */
    fun raw(bytes: ByteArray): TarBytes {
        out.write(bytes)
        return this
    }

    /** 끝 표시 두 칸. */
    fun end(): ByteArray {
        out.write(ByteArray(1024))
        return out.toByteArray()
    }

    /** 끝 표시 없이 지금까지. */
    fun bytes(): ByteArray = out.toByteArray()

    enum class Magic { USTAR, GNU, V7 }

    companion object {
        private fun octal(h: ByteArray, off: Int, len: Int, v: Long) {
            val s = v.toString(8).padStart(len - 1, '0')
            s.toByteArray().copyInto(h, off)
            h[off + len - 1] = 0
        }

        fun gzip(tar: ByteArray): ByteArray = ByteArrayOutputStream().also { b ->
            GzipCompressorOutputStream(b).use { it.write(tar) }
        }.toByteArray()

        fun bzip2(tar: ByteArray): ByteArray = ByteArrayOutputStream().also { b ->
            BZip2CompressorOutputStream(b).use { it.write(tar) }
        }.toByteArray()

        fun xz(tar: ByteArray): ByteArray = ByteArrayOutputStream().also { b ->
            XZOutputStream(b, LZMA2Options(1)).use { it.write(tar) }
        }.toByteArray()

        fun write(file: File, bytes: ByteArray): File {
            file.parentFile?.mkdirs()
            file.writeBytes(bytes)
            return file
        }
    }
}
