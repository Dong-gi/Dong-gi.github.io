package io.github.donggi.iroiroviewer.format.archive

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.lz4.FramedLZ4CompressorOutputStream
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.LZMAOutputStream
import org.tukaani.xz.XZOutputStream
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater

/**
 * 압축 스트림 표본을 **코드로** 만든다(`ArchiveSamples` 와 같은 까닭 — 폭탄과 악의적인 이름을 공개 저장소에 두지 않는다).
 *
 * 작성기는 이미 들인 라이브러리의 것이다 — bzip2·LZ4(commons-compress), xz·LZMA(org.tukaani.xz). gzip 은 **멤버를 손으로
 * 짓는다**([gzipMember]) — 도구가 만들어 주지 않는 머리(악의적인 FNAME, FEXTRA·FCOMMENT·FHCRC, 틀린 CRC)가 필요하다.
 * `.Z` 는 commons-compress 에 작성기가 없어 [ZBytes] 를 쓴다.
 */
internal object StreamBytes {

    fun bzip2(data: ByteArray): ByteArray = ByteArrayOutputStream().also { b ->
        BZip2CompressorOutputStream(b).use { it.write(data) }
    }.toByteArray()

    fun xz(data: ByteArray, preset: Int = 1): ByteArray = ByteArrayOutputStream().also { b ->
        XZOutputStream(b, LZMA2Options(preset)).use { it.write(data) }
    }.toByteArray()

    fun lz4(data: ByteArray, params: FramedLZ4CompressorOutputStream.Parameters = FramedLZ4CompressorOutputStream.Parameters.DEFAULT): ByteArray =
        ByteArrayOutputStream().also { b ->
            FramedLZ4CompressorOutputStream(b, params).use { it.write(data) }
        }.toByteArray()

    /**
     * LZMA-alone. [knownSize] 가 참이면 머리에 풀린 크기를 적고(LZMA SDK 의 모양), 거짓이면 '모름' 과 끝 표시(xz 의 모양)다.
     */
    fun lzma(data: ByteArray, knownSize: Boolean = false, preset: Int = 1): ByteArray = ByteArrayOutputStream().also { b ->
        LZMAOutputStream(b, LZMA2Options(preset), if (knownSize) data.size.toLong() else -1L).use { it.write(data) }
    }.toByteArray()

    fun gzip(data: ByteArray, name: ByteArray? = null, mtime: Long = 0L): ByteArray = gzipMember(data, name = name, mtime = mtime)

    /**
     * gzip 멤버 하나를 RFC 1952 대로 짓는다. 자료는 `Deflater`(날것)가 압축하고 트레일러의 CRC32·ISIZE 를 붙인다.
     *
     * @param badCrc 트레일러의 CRC32 를 틀리게 적는다.
     * @param badSize 트레일러의 ISIZE 를 틀리게 적는다.
     */
    fun gzipMember(
        data: ByteArray,
        name: ByteArray? = null,
        comment: ByteArray? = null,
        extra: ByteArray? = null,
        headerCrc: Boolean = false,
        mtime: Long = 0L,
        badCrc: Boolean = false,
        badSize: Boolean = false,
        level: Int = Deflater.DEFAULT_COMPRESSION,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        var flg = 0
        if (headerCrc) flg = flg or 0x02
        if (extra != null) flg = flg or 0x04
        if (name != null) flg = flg or 0x08
        if (comment != null) flg = flg or 0x10
        out.write(0x1F)
        out.write(0x8B)
        out.write(8)
        out.write(flg)
        for (i in 0 until 4) out.write((mtime ushr (8 * i)).toInt() and 0xFF)
        out.write(0)
        out.write(3)
        if (extra != null) {
            out.write(extra.size and 0xFF)
            out.write((extra.size shr 8) and 0xFF)
            out.write(extra)
        }
        if (name != null) {
            out.write(name)
            out.write(0)
        }
        if (comment != null) {
            out.write(comment)
            out.write(0)
        }
        if (headerCrc) {
            val crc = CRC32().apply { update(out.toByteArray()) }.value
            out.write((crc and 0xFF).toInt())
            out.write(((crc shr 8) and 0xFF).toInt())
        }
        val deflater = Deflater(level, true)
        try {
            deflater.setInput(data)
            deflater.finish()
            val buf = ByteArray(64 * 1024)
            while (!deflater.finished()) {
                val n = deflater.deflate(buf)
                out.write(buf, 0, n)
            }
        } finally {
            deflater.end()
        }
        var crc = CRC32().apply { update(data) }.value
        if (badCrc) crc = crc xor 1L
        var isize = data.size.toLong() and 0xFFFF_FFFFL
        if (badSize) isize = (isize + 1) and 0xFFFF_FFFFL
        for (i in 0 until 4) out.write(((crc ushr (8 * i)) and 0xFF).toInt())
        for (i in 0 until 4) out.write(((isize ushr (8 * i)) and 0xFF).toInt())
        return out.toByteArray()
    }

    /** [size] 바이트의 0 을 압축한 gzip 멤버 — 한 번 짓고 되풀이해 이어 붙이면 큰 폭탄을 싸게 만든다. */
    fun zeroMember(size: Int): ByteArray = gzipMember(ByteArray(size), level = Deflater.BEST_COMPRESSION)

    /**
     * **빈 블록**만 [blocks] 개 이은 xz 스트림 하나. 풀면 아무것도 나오지 않는다 — 블록마다 머리 12바이트(LZMA2 거르개 하나, 사전
     * [dictByte]) + LZMA2 끝 표시 `00` + 채움 3바이트이고 검사값은 없다(검사 종류 None). 작성기는 빈 블록을 쓰지 않아 손으로 짓는다
     * (xz 파일 형식 1.2.1 — 2.1.1 머리, 3.1 블록 머리, 4 인덱스, 2.1.2 꼬리). 뒤에 다른 xz 스트림을 이어 붙여도 된다(명세가 받는다).
     *
     * 이 모양이 쓰인 곳: org.tukaani.xz 는 블록을 열 때마다 사전을 새로 잡는다(`CompressedStreams` 의 주석).
     *
     * @param dictByte LZMA2 사전 크기 바이트. 28 이 64 MiB 다 — `(2 | (b and 1)) shl (b / 2 + 11)`.
     */
    fun xzEmptyBlocks(blocks: Int, dictByte: Int = 28): ByteArray {
        val out = ByteArrayOutputStream()
        fun le32(to: ByteArrayOutputStream, v: Long) {
            for (i in 0 until 4) to.write(((v ushr (8 * i)) and 0xFF).toInt())
        }
        fun crc(b: ByteArray): Long = CRC32().apply { update(b) }.value

        // 스트림 머리: 매직 · 플래그 2(검사 None) · 플래그의 CRC32.
        val flags = byteArrayOf(0x00, 0x00)
        out.write(byteArrayOf(0xFD.toByte(), 0x37, 0x7A, 0x58, 0x5A, 0x00))
        out.write(flags)
        le32(out, crc(flags))
        // 블록 머리: 크기 칸 2 → (2 + 1) × 4 = 12바이트 · 블록 플래그 0(거르개 하나, 크기 칸 없음) · 거르개 21(LZMA2) · 속성
        // 길이 1 · 사전 · 채움 3 · 앞 8바이트의 CRC32.
        val header = byteArrayOf(0x02, 0x00, 0x21, 0x01, dictByte.toByte(), 0, 0, 0)
        val headerCrc = crc(header)
        repeat(blocks) {
            out.write(header)
            le32(out, headerCrc)
            out.write(0x00)
            out.write(ByteArray(3))
        }
        // 인덱스: 표시 00 · 레코드 수 · 레코드마다 (채우지 않은 크기 13 = 머리 12 + 자료 1, 풀린 크기 0) · 4의 배수 채움 · CRC32.
        val index = ByteArrayOutputStream()
        fun vli(v: Long) {
            var x = v
            while (x >= 0x80) {
                index.write(((x and 0x7F) or 0x80).toInt())
                x = x ushr 7
            }
            index.write(x.toInt())
        }
        index.write(0x00)
        vli(blocks.toLong())
        repeat(blocks) {
            vli(13)
            vli(0)
        }
        while (index.size() % 4 != 0) index.write(0)
        val indexBody = index.toByteArray()
        out.write(indexBody)
        le32(out, crc(indexBody))
        // 꼬리: CRC32 · 뒤로 크기(인덱스 길이 / 4 - 1) · 플래그 · `YZ`.
        val footer = ByteArrayOutputStream()
        le32(footer, (indexBody.size + 4) / 4 - 1L)
        footer.write(flags)
        val footerBytes = footer.toByteArray()
        le32(out, crc(footerBytes))
        out.write(footerBytes)
        out.write('Y'.code)
        out.write('Z'.code)
        return out.toByteArray()
    }
}

/**
 * compress(1)·ncompress 와 같은 모양의 `.Z`(LZW) 작성기. commons-compress 에 `.Z` 작성기가 없어 시험이 쓴다.
 *
 * ## 오라클이 우리가 아니게
 *
 * 같은 알고리즘을 파이썬으로 먼저 짓고(`lzw_z.py`, 저장소에 넣지 않는 도구), 그 출력을 **GNU gzip 1.14 의 `gzip -dc`(unlzw)**
 * 로 풀어 원본과 바이트까지 같은지 확인했다 — 글·0·무작위·섞인 것·빈 것·1~2바이트 × 코드 폭 9·12·16 × CLEAR 없음·300·1000
 * × 블록 모드 끔, 70가지 모두. 이 코틀린 판이 그 파이썬 판과 **같은 바이트**를 내는지는 `StreamOracleTest` 가 커밋한 표본
 * (`stream/hello.txt.Z`)으로 견준다. 그래서 commons-compress 의 해제기와 이 작성기가 서로를 맞춘 것만은 아니다.
 *
 * ## ncompress 의 `output()` 을 따른다
 *
 * - 코드는 작은 끝(LSB 먼저)으로 비트를 채운다.
 * - 코드 8개(= 코드 폭 바이트)를 한 묶음으로 내보낸다.
 * - 코드 폭이 커질 때와 CLEAR 뒤에는 묶음을 **통째로**(코드 폭 바이트) 내보낸다 — 읽는 쪽은 폭이 바뀐 것을 그 묶음을 다
 *   읽은 뒤에야 알기 때문이다(commons-compress 의 `reAlignReading`). 끝에서는 남은 비트만큼만.
 * - 블록 모드면 첫 자유 코드가 257 이다(256 = CLEAR).
 */
internal object ZBytes {

    /**
     * @param clearAfter 이 수만큼 코드를 낸 뒤 CLEAR 를 한 번 낸다(블록 모드만). -1 이면 내지 않는다.
     */
    fun compress(data: ByteArray, maxBits: Int = 16, blockMode: Boolean = true, clearAfter: Int = -1): ByteArray {
        require(maxBits in 9..16)
        val out = ByteArrayOutputStream()
        out.write(0x1F)
        out.write(0x9D)
        out.write((if (blockMode) 0x80 else 0) or maxBits)
        if (data.isEmpty()) return out.toByteArray()
        val maxMaxCode = 1 shl maxBits
        var nBits = 9
        var maxCode = (1 shl nBits) - 1
        var freeEnt = if (blockMode) 257 else 256
        var offset = 0
        var buf = ByteArray(16)
        var clearFlag = false

        fun output(code: Int) {
            if (code >= 0) {
                var c = code
                var left = nBits
                var o = offset
                while (left > 0) {
                    val byteIdx = o shr 3
                    val bitIdx = o and 7
                    val take = minOf(8 - bitIdx, left)
                    buf[byteIdx] = (buf[byteIdx].toInt() or ((c and ((1 shl take) - 1)) shl bitIdx)).toByte()
                    c = c ushr take
                    left -= take
                    o += take
                }
                offset += nBits
                if (offset == nBits shl 3) {
                    out.write(buf, 0, nBits)
                    buf = ByteArray(16)
                    offset = 0
                }
                if (freeEnt > maxCode || clearFlag) {
                    if (offset > 0) {
                        out.write(buf, 0, nBits)
                        buf = ByteArray(16)
                    }
                    offset = 0
                    if (clearFlag) {
                        nBits = 9
                        maxCode = (1 shl nBits) - 1
                        clearFlag = false
                    } else {
                        nBits++
                        maxCode = if (nBits == maxBits) maxMaxCode else (1 shl nBits) - 1
                    }
                }
            } else if (offset > 0) {
                out.write(buf, 0, (offset + 7) / 8)
                offset = 0
            }
        }

        val table = HashMap<Int, Int>()
        var ent = data[0].toInt() and 0xFF
        var codes = 0
        for (i in 1 until data.size) {
            val c = data[i].toInt() and 0xFF
            val key = (ent shl 8) or c
            val found = table[key]
            if (found != null) {
                ent = found
                continue
            }
            output(ent)
            codes++
            ent = c
            if (freeEnt < maxMaxCode) table[key] = freeEnt++
            if (blockMode && clearAfter > 0 && codes == clearAfter) {
                table.clear()
                freeEnt = 257
                clearFlag = true
                output(256)
            }
        }
        output(ent)
        output(-1)
        return out.toByteArray()
    }
}
