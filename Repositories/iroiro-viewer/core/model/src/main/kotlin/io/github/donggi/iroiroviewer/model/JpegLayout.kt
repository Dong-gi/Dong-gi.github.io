package io.github.donggi.iroiroviewer.model

import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile

/**
 * JPEG 의 짜임 — 첫 스캔(SOS) 앞의 머리 덩이들과 **주 그림의 끝(EOI 뒤)**.
 *
 * 공유 사본을 지을 때 쓴다([copyForShare]). 모션 포토(구글 `MicroVideo`·삼성)는 주 그림의 EOI **뒤에** MP4 를 덧붙이고, 그 MP4 의 위치는
 * `©xyz` 상자에 ISO 6709 글로 적힌다 — EXIF·XMP 를 지워도 그대로 실린다. 사본은 주 그림의 끝에서 자른다(원본은 그대로 — 사본에서 움직이는 부분을
 * 잃는 것은 받아들인다). IPTC(APP13 `Photoshop 3.0`)도 사본에서 뺀다 — 도시·나라가 숫자 레코드로 들어 있어 글자 검사로는 보이지 않는다.
 *
 * EOI 는 **덩이를 걸어** 찾는다 — 머리 덩이는 길이로 건너뛰고, SOS 뒤의 엔트로피 자료에서는 `FF 00`(채움)·`FF D0`~`FF D7`(RST)·`FF FF`(채움)을
 * 지나 다음 표지를 찾는다(점진 JPEG 은 스캔이 여럿이고 그 사이에 DHT 따위가 온다). 그림 자료 안의 우연한 `FF D9` 에 속지 않는다. 짜임이 어긋나면
 * (길이가 파일을 넘는다·EOI 가 없다) null — 부르는 쪽은 자르지 않는다.
 */
object JpegLayout {

    /** 덩이 하나 — [start] 는 `FF` 의 자리, [end] 는 끝(포함하지 않는다). */
    data class Segment(val marker: Int, val start: Long, val end: Long)

    /**
     * @param headers 첫 SOS 앞의 덩이들(APPn·DQT·SOF·DHT…).
     * @param scanStart 첫 SOS 의 자리.
     * @param mainEnd 주 그림의 EOI 뒤 — 여기서부터 파일 끝까지가 덧붙은 것이다.
     */
    data class Layout(val headers: List<Segment>, val scanStart: Long, val mainEnd: Long, val length: Long) {
        /** EOI 뒤에 덧붙은 것이 있는가. */
        val hasTrailer: Boolean get() = mainEnd < length
    }

    /** 바이트를 자리로 읽는 것. 끝이면 -1. */
    fun interface Source {
        fun byteAt(pos: Long): Int
    }

    const val APP13 = 0xED

    fun read(bytes: ByteArray): Layout? = read(bytes.size.toLong()) { p -> if (p < bytes.size) bytes[p.toInt()].toInt() and 0xFF else -1 }

    /** 파일에서. JPEG 이 아니면 null. */
    fun read(file: File): Layout? = RandomAccessFile(file, "r").use { raf -> read(raf.length(), Buffered(raf)) }

    /** JPEG(앞이 `FF D8`)인데 짜임을 읽지 못하는가. JPEG 이 아니면 false. */
    fun isUnreadableJpeg(file: File): Boolean = RandomAccessFile(file, "r").use { raf ->
        val src = Buffered(raf)
        val length = raf.length()
        length >= 2 && src.byteAt(0) == 0xFF && src.byteAt(1) == 0xD8 && read(length, src) == null
    }

    fun read(length: Long, src: Source): Layout? {
        if (length < 4 || src.byteAt(0) != 0xFF || src.byteAt(1) != 0xD8) return null
        val headers = ArrayList<Segment>()
        var scanStart = -1L
        var pos = 2L
        while (pos + 1 < length) {
            if (src.byteAt(pos) != 0xFF) return null
            var m = src.byteAt(pos + 1)
            // 표지 앞의 채움 FF.
            while (m == 0xFF) {
                pos++
                if (pos + 1 >= length) return null
                m = src.byteAt(pos + 1)
            }
            val at = pos
            when {
                m == 0xD9 -> return if (scanStart < 0) null else Layout(headers, scanStart, at + 2, length)
                m == 0x01 || m in 0xD0..0xD7 -> pos = at + 2
                else -> {
                    if (at + 4 > length) return null
                    val len = (src.byteAt(at + 2) shl 8) or src.byteAt(at + 3)
                    if (len < 2) return null
                    val end = at + 2 + len
                    if (end > length) return null
                    if (m == 0xDA) {
                        if (scanStart < 0) scanStart = at
                        pos = entropyEnd(end, length, src) ?: return null
                    } else {
                        if (scanStart < 0) headers += Segment(m, at, end)
                        pos = end
                    }
                }
            }
        }
        return null
    }

    /** 엔트로피 자료를 지나 다음 표지(RST 가 아닌 것)의 자리. 끝까지 없으면 null. */
    private fun entropyEnd(from: Long, length: Long, src: Source): Long? {
        var p = from
        while (p + 1 < length) {
            if (src.byteAt(p) != 0xFF) {
                p++
                continue
            }
            val b = src.byteAt(p + 1)
            when {
                b == 0x00 || b in 0xD0..0xD7 -> p += 2
                b == 0xFF -> p += 1
                else -> return p
            }
        }
        return null
    }

    /**
     * 공유 사본을 쓴다 — JPEG 이면 SOI · APP13 을 뺀 머리 덩이들 · 첫 SOS 부터 주 그림의 EOI 까지(덧붙은 것은 버린다). 짜임을 읽지 못한 JPEG 과
     * JPEG 이 아닌 것은 글자 그대로 옮긴다(그 뒤의 위치 검사가 거른다).
     */
    fun copyForShare(src: File, out: OutputStream) {
        val layout = try {
            read(src)
        } catch (e: IOException) {
            null
        }
        RandomAccessFile(src, "r").use { raf ->
            val buf = ByteArray(64 * 1024)
            fun copy(from: Long, to: Long) {
                raf.seek(from)
                var left = to - from
                while (left > 0) {
                    val n = raf.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                    if (n < 0) throw IOException("파일이 짧다")
                    out.write(buf, 0, n)
                    left -= n
                }
            }
            if (layout == null) {
                copy(0, raf.length())
                return
            }
            copy(0, 2)
            for (s in layout.headers) if (s.marker != APP13) copy(s.start, s.end)
            copy(layout.scanStart, layout.mainEnd)
        }
    }

    /** 덩이 걷기용 읽개 — 64 KiB 창 하나. */
    private class Buffered(private val raf: RandomAccessFile) : Source {
        private val buf = ByteArray(64 * 1024)
        private var base = -1L
        private var size = 0

        override fun byteAt(pos: Long): Int {
            if (base < 0 || pos < base || pos >= base + size) {
                raf.seek(pos)
                size = maxOf(0, raf.read(buf))
                base = pos
                if (size == 0) return -1
            }
            return buf[(pos - base).toInt()].toInt() and 0xFF
        }
    }
}
