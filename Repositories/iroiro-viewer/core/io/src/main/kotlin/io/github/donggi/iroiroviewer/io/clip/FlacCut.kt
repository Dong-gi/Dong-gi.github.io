package io.github.donggi.iroiroviewer.io.clip

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.OutputStream
import java.io.RandomAccessFile

/**
 * FLAC 의 한 구간 — **프레임 경계**에서 자르고(보통 4096표본, 44.1 kHz 에서 약 0.09초) 다시 인코딩하지 않는다. 무손실은 그대로다.
 *
 * ## 프레임을 옮기면서 고치는 것
 *
 * FLAC 프레임 머리에는 **자기 번호**(고정 블록이면 프레임 번호, 가변이면 첫 표본 번호)가 적혀 있다. 그대로 옮기면 자른 파일의 첫
 * 프레임이 1,234번이라 재생기가 위치를 1:23 부터로 그리거나(media3 는 머리의 번호로 시각을 정한다) 탐색이 어긋난다. 그래서 번호를
 * 0 부터 다시 매기고, 번호의 길이가 바뀌므로 **머리의 CRC-8 과 프레임 전체의 CRC-16 을 다시 계산한다.** 소리 데이터(하위 프레임)는
 * 한 비트도 바꾸지 않는다.
 *
 * ## 메타데이터
 *
 * `STREAMINFO` 는 새로 쓴다 — 전체 표본 수를 자른 길이로, MD5 는 0(모른다 — 명세가 허락한다), 최소·최대 프레임 크기는 실제 값으로.
 * `VORBIS_COMMENT`(제목·가수)와 `PICTURE`(앨범 그림)는 옮긴다. `SEEKTABLE`·`CUESHEET` 는 원본의 자리를 가리켜 버린다.
 *
 * ## 프레임의 끝을 찾는 법
 *
 * FLAC 에는 프레임 길이가 적혀 있지 않다. 다음 프레임의 동기 부호(`FF F8`/`FF F9`)를 찾되, 소리 데이터 속의 우연한 같은 바이트에
 * 속지 않게 **세 겹으로 확인한다** — 그 자리의 머리가 CRC-8 로 맞고, 같은 흐름의 머리이며(블록 방식·표본율·표본 크기), 그 앞까지의
 * CRC-16 이 맞아야 끝이다. libFLAC 이 동기를 다시 잡는 방법과 같다.
 *
 * ## 기억하는 것
 *
 * 고른 구간의 프레임마다 **길이 한 수만** 든다(원시 배열) — 담는 프레임은 이어져 있어 자리는 첫 프레임에서 길이를 더해 안다. 머리는
 * 쓸 때 다시 읽는다 — 프레임마다 객체를 들면 긴 파일에서 수백 MB 가 됐다. 옮길 메타데이터 블록(주석·그림, 최대
 * [MAX_KEPT_BYTES])도 원본의 자리만 들고 쓸 때 옮긴다 — 예전에는 그림을 읽어 든 위에 머리 한 벌을 묶어(두 배까지 자라는 버퍼) 또
 * 복사했다.
 */
internal object FlacCut {

    /** 옮길 메타데이터 블록 — 원본에서 몸이 [offset] 부터 [length] 바이트. */
    private class KeptBlock(val type: Int, val offset: Long, val length: Int)

    suspend fun plan(raf: RandomAccessFile, startUs: Long, endUs: Long, sourceExt: String): CutPlan {
        val r = WindowReader(raf)
        // 표준은 아니지만 앞에 ID3v2 를 단 FLAC 이 있다(태그 편집기가 붙인다). 건너뛴다 — 옮기지 않는다(FLAC 의 태그는 VORBIS_COMMENT 다).
        val base = id3v2Length(r)
        if (r.ascii(base, 4) != "fLaC") return CutPlan.Unsupported
        var pos = base + 4
        var streamInfo: ByteArray? = null
        val kept = ArrayList<KeptBlock>()
        val seekPoints = ArrayList<LongArray>() // [표본 번호, 첫 프레임에서의 바이트]
        var keptBytes = 0L
        while (true) {
            val h = r.u8(pos)
            val l0 = r.u8(pos + 1)
            val l1 = r.u8(pos + 2)
            val l2 = r.u8(pos + 3)
            // 머리가 파일 끝에 걸려 잘렸으면 길이 바이트가 -1 이다 — 그대로 셈하면 길이가 음수가 되어 `ByteArray(-1)` 로 던졌다.
            if (h < 0 || l0 < 0 || l1 < 0 || l2 < 0) return CutPlan.Unsupported
            val last = h and 0x80 != 0
            val type = h and 0x7F
            val len = (l0.toLong() shl 16) or (l1.toLong() shl 8) or l2.toLong()
            val body = pos + 4
            if (type == 127 || body + len > r.length) return CutPlan.Unsupported
            when (type) {
                TYPE_STREAMINFO -> {
                    if (len != 34L || streamInfo != null) return CutPlan.Unsupported
                    streamInfo = ByteArray(34).also { r.read(body, it) }
                }
                TYPE_SEEKTABLE -> {
                    val b = ByteArray(len.toInt().coerceAtMost(MAX_SEEKTABLE_BYTES))
                    r.read(body, b)
                    var i = 0
                    while (i + 18 <= b.size) {
                        val sample = be(b, i, 8)
                        if (sample != -1L) seekPoints += longArrayOf(sample, be(b, i + 8, 8))
                        i += 18
                    }
                }
                TYPE_VORBIS_COMMENT, TYPE_PICTURE -> if (keptBytes + len <= MAX_KEPT_BYTES) {
                    kept += KeptBlock(type, body, len.toInt())
                    keptBytes += len
                }
            }
            pos = body + len
            if (last) break
        }
        val info = streamInfo ?: return CutPlan.Unsupported
        val firstFrame = pos
        val maxBlock = be(info, 2, 2).toInt()
        val rate = ((info[10].toLong() and 0xFF) shl 12) or ((info[11].toLong() and 0xFF) shl 4) or
            ((info[12].toLong() and 0xFF) shr 4)
        if (rate <= 0 || maxBlock <= 0) return CutPlan.Unsupported
        val maxFrameSize = be(info, 7, 3).let { if (it > 0) it + MAX_FRAME_SLACK else DEFAULT_MAX_FRAME }

        val startSample = startUs.coerceAtLeast(0) * rate / 1_000_000L
        val endSample = (endUs.coerceAtMost(MAX_END_US) * rate + 999_999L) / 1_000_000L

        // 목차가 있으면 고른 곳 바로 앞의 자리로 건너뛴다. 없으면(또는 그 자리가 머리가 아니면) 처음부터 훑는다.
        var scan = firstFrame
        seekPoints.filter { it[0] <= startSample }.maxByOrNull { it[0] }?.let { p ->
            val at = firstFrame + p[1]
            if (at < r.length && parseHeader(r, at) != null) scan = at
        }
        val first = parseHeader(r, scan) ?: return CutPlan.Unsupported

        val lengths = IntArrayList()
        var firstOffset = -1L
        var firstSample = -1L
        var firstNumber = 0L
        var totalSamples = 0L
        var minSize = Long.MAX_VALUE
        var maxSize = 0L
        var frameBytes = 0L
        var seen = 0L
        var at = scan
        var header: FrameHeader? = first
        while (header != null) {
            // 긴 파일을 처음부터 훑을 수 있다(목차가 없다) — 프레임 수백 개마다 취소를 본다.
            if (seen++ % 256 == 0L) currentCoroutineContext().ensureActive()
            val end = frameEnd(r, at, header, first, maxFrameSize) ?: break
            val sample = if (header.variable) header.number else header.number * maxBlock
            if (sample >= endSample) break
            if (sample + header.blockSize > startSample) {
                // 늘리기 **전에** 상한을 본다.
                if (lengths.size >= MAX_FRAMES) return CutPlan.TooLong
                if (firstSample < 0) {
                    firstSample = sample
                    firstNumber = header.number
                    firstOffset = at
                }
                lengths.add((end - at).toInt())
                // 다시 쓴 머리의 길이 — 번호의 길이만 바뀐다.
                val n = header.number - (if (header.variable) firstSample else firstNumber)
                val size = (end - at) - header.numberLength + encodeNumber(n).size
                minSize = minOf(minSize, size)
                maxSize = maxOf(maxSize, size)
                frameBytes += size
                totalSamples = sample + header.blockSize - firstSample
            } else if (firstSample >= 0) {
                // 번호가 뒤로 간 깨진 흐름 — 담던 프레임이 이어지지 않는다(쓸 때 자리를 길이로만 이어 센다). 거기를 끝으로 본다.
                break
            }
            at = end
            header = if (at < r.length) parseHeader(r, at) else null
        }
        if (lengths.size == 0) return CutPlan.Empty

        val newInfo = streamInfoFor(info, minSize, maxSize, totalSamples)
        // 머리 — `fLaC` + STREAMINFO + 옮길 블록. 블록의 몸은 쓸 때 원본에서 옮긴다(머리말 '기억하는 것').
        val headLength = 4L + 4 + newInfo.size + kept.sumOf { 4L + it.length }
        val ext = if (sourceExt == "flac") sourceExt else "flac"
        val count = lengths.size

        return CutPlan.Ok(
            object : AudioCut {
                override val startUs = firstSample * 1_000_000L / rate
                override val endUs = (firstSample + totalSamples) * 1_000_000L / rate
                override val outputBytes = headLength + frameBytes
                override val extension = ext

                override suspend fun writeTo(out: OutputStream, onBytes: (Long) -> Unit) {
                    val buf = ByteArray(COPY_BUFFER)
                    out.write("fLaC".toByteArray(Charsets.US_ASCII))
                    out.write(blockHeader(TYPE_STREAMINFO, newInfo.size, last = kept.isEmpty()))
                    out.write(newInfo)
                    onBytes(4L + 4 + newInfo.size)
                    kept.forEachIndexed { i, block ->
                        // 머리는 계획할 때 읽었던 것이다 — 그사이 파일이 바뀌었으면 멈춘다(쓰다 만 임시본은 부르는 쪽이 버린다).
                        val was = ByteArray(4)
                        raf.seek(block.offset - 4)
                        raf.readFully(was)
                        if ((was[0].toInt() and 0x7F) != block.type || u24(was, 1) != block.length) {
                            throw java.io.IOException("FLAC 메타데이터 블록이 바뀌었다")
                        }
                        out.write(blockHeader(block.type, block.length, last = i == kept.lastIndex))
                        onBytes(4)
                        copyRange(raf, block.offset, block.length.toLong(), out, buf, onBytes)
                    }
                    val reader = WindowReader(raf)
                    var offset = firstOffset
                    for (i in 0 until count) {
                        currentCoroutineContext().ensureActive()
                        val length = lengths[i].toLong()
                        // 머리는 계획할 때 읽었던 것이다 — 그사이 파일이 바뀌었으면 멈춘다(쓰다 만 임시본은 부르는 쪽이 버린다).
                        val h = parseHeader(reader, offset) ?: throw java.io.IOException("FLAC 프레임 머리가 바뀌었다")
                        val newHeader = h.rewrite(h.number - (if (h.variable) firstSample else firstNumber))
                        out.write(newHeader)
                        var crc = Crc.crc16(0, newHeader, 0, newHeader.size)
                        // 하위 프레임(소리)은 그대로 옮기며 CRC-16 을 이어 센다. 원본의 CRC-16(마지막 두 바이트)은 버린다.
                        var left = length - h.length - 2
                        raf.seek(offset + h.length)
                        while (left > 0) {
                            val n = raf.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                            if (n < 0) throw java.io.EOFException("FLAC 프레임이 계획보다 짧다")
                            out.write(buf, 0, n)
                            crc = Crc.crc16(crc, buf, 0, n)
                            left -= n
                        }
                        out.write((crc shr 8) and 0xFF)
                        out.write(crc and 0xFF)
                        onBytes(length - h.length + newHeader.size)
                        offset += length
                    }
                }
            }
        )
    }

    /**
     * 새 STREAMINFO — 최소·최대 프레임 크기를 실제 값으로, 표본 수를 담은 길이로, MD5 는 0(모른다 — 명세가 허락한다). 나머지(표본율·
     * 채널·표본 크기·블록 크기)는 그대로다. 소리 뽑기(`AudioExtract`)도 쓴다.
     */
    fun streamInfoFor(info: ByteArray, minFrame: Long, maxFrame: Long, totalSamples: Long): ByteArray {
        val out = info.copyOf()
        put(out, 4, 3, if (minFrame == Long.MAX_VALUE) 0 else minFrame.coerceAtMost(0xFFFFFF))
        put(out, 7, 3, maxFrame.coerceAtMost(0xFFFFFF))
        // 표본 수(36비트)는 바이트 13의 아래 4비트부터 17까지다. 위 4비트(표본 크기의 끝)는 그대로 둔다.
        out[13] = ((out[13].toInt() and 0xF0) or ((totalSamples shr 32).toInt() and 0x0F)).toByte()
        put(out, 14, 4, totalSamples and 0xFFFFFFFFL)
        for (i in 18 until 34) out[i] = 0 // MD5 — 모른다
        return out
    }

    /**
     * 메타데이터 블록 머리(4바이트) — 종류와 몸의 길이(24비트), 마지막 블록이면 '끝' 표시. 블록은 STREAMINFO 가 먼저다. 몸은 부르는
     * 쪽이 곧바로 이어 쓴다 — 머리째 한 벌로 묶지 않는다(그림이 수십 MB 일 수 있다 — 머리말 '기억하는 것'). 소리 뽑기(`AudioExtract`)도 쓴다.
     */
    fun blockHeader(type: Int, length: Int, last: Boolean): ByteArray = byteArrayOf(
        ((if (last) 0x80 else 0) or type).toByte(),
        (length shr 16).toByte(),
        (length shr 8).toByte(),
        length.toByte(),
    )

    private fun u24(b: ByteArray, at: Int): Int =
        ((b[at].toInt() and 0xFF) shl 16) or ((b[at + 1].toInt() and 0xFF) shl 8) or (b[at + 2].toInt() and 0xFF)

    /**
     * 프레임 머리. [bytes] 는 원본 머리 전부(CRC-8 포함), [numberLength] 는 그 안의 번호(4바이트째부터)의 길이다.
     */
    class FrameHeader(
        val bytes: ByteArray,
        val variable: Boolean,
        val number: Long,
        val numberLength: Int,
        val blockSize: Int,
        /** 같은 흐름인지 견줄 값 — 두 번째 바이트(블록 방식), 표본율 부호, 표본 크기 부호. */
        val b1: Int,
        val rateCode: Int,
        val sizeCode: Int,
    ) {
        val length: Int get() = bytes.size

        fun sameStream(o: FrameHeader) = b1 == o.b1 && rateCode == o.rateCode && sizeCode == o.sizeCode

        /** 번호를 [n] 으로 바꾼 새 머리(CRC-8 까지). */
        fun rewrite(n: Long): ByteArray {
            val coded = encodeNumber(n)
            val tailStart = 4 + numberLength
            val tail = bytes.copyOfRange(tailStart, bytes.size - 1) // 선택 필드(블록 크기·표본율), CRC-8 은 뺀다
            val out = ByteArray(4 + coded.size + tail.size + 1)
            bytes.copyInto(out, 0, 0, 4)
            coded.copyInto(out, 4)
            tail.copyInto(out, 4 + coded.size)
            out[out.size - 1] = Crc.crc8(out, 0, out.size - 1).toByte()
            return out
        }
    }

    /** [pos] 의 프레임 머리. 머리가 아니거나 CRC-8 이 틀리면 null. */
    fun parseHeader(r: ByteSource, pos: Long): FrameHeader? {
        val b0 = r.u8(pos)
        val b1 = r.u8(pos + 1)
        if (b0 != 0xFF || (b1 and 0xFE) != 0xF8) return null
        val b2 = r.u8(pos + 2)
        val b3 = r.u8(pos + 3)
        if (b2 < 0 || b3 < 0) return null
        val bsCode = b2 shr 4
        val rateCode = b2 and 0x0F
        val channel = b3 shr 4
        val sizeCode = (b3 shr 1) and 7
        if (bsCode == 0 || rateCode == 15 || channel > 10 || sizeCode == 3 || (b3 and 1) != 0) return null
        // 번호(UTF-8 꼴, 1~7바이트).
        val lead = r.u8(pos + 4)
        if (lead < 0) return null
        val numberLength = when {
            lead and 0x80 == 0 -> 1
            lead and 0xE0 == 0xC0 -> 2
            lead and 0xF0 == 0xE0 -> 3
            lead and 0xF8 == 0xF0 -> 4
            lead and 0xFC == 0xF8 -> 5
            lead and 0xFE == 0xFC -> 6
            lead == 0xFE -> 7
            else -> return null
        }
        val variable = b1 and 1 == 1
        if (!variable && numberLength > 6) return null
        var number = if (numberLength == 1) lead.toLong() else (lead and (0xFF shr (numberLength + 1))).toLong()
        if (numberLength == 7) number = 0
        for (i in 1 until numberLength) {
            val c = r.u8(pos + 4 + i)
            if (c < 0 || c and 0xC0 != 0x80) return null
            number = (number shl 6) or (c and 0x3F).toLong()
        }
        var at = pos + 4 + numberLength
        val blockSize = when (bsCode) {
            1 -> 192
            in 2..5 -> 576 shl (bsCode - 2)
            6 -> (r.u8(at).also { at += 1 }.takeIf { it >= 0 } ?: return null) + 1
            7 -> {
                val hi = r.u8(at)
                val lo = r.u8(at + 1)
                at += 2
                if (hi < 0 || lo < 0) return null
                ((hi shl 8) or lo) + 1
            }
            else -> 256 shl (bsCode - 8)
        }
        when (rateCode) {
            12 -> at += 1
            13, 14 -> at += 2
        }
        val length = (at - pos + 1).toInt()
        val bytes = ByteArray(length)
        if (r.read(pos, bytes) != length) return null
        if (Crc.crc8(bytes, 0, length - 1) != (bytes[length - 1].toInt() and 0xFF)) return null
        return FrameHeader(bytes, variable, number, numberLength, blockSize, b1, rateCode, sizeCode)
    }

    /**
     * [pos] 에서 시작한 프레임의 끝(다음 프레임의 첫 바이트, 또는 바이트의 끝). 확인하지 못하면 null — 거기서 흐름이 끝난다고 본다.
     * 소리 뽑기(`AudioExtract`)도 그릇의 표본 하나 안에서 쓴다 — 표본이 프레임 둘을 품을 수 있고, **이어 붙인 두 프레임은 CRC-16 이
     * 그대로 맞는다**(앞 프레임의 CRC 를 품은 채 센 CRC 는 0 이 되어 뒤 프레임의 CRC 와 같아진다 — 시험이 잡았다). 그래서 경계는 CRC
     * 하나가 아니라 이 세 겹으로만 안다.
     */
    fun frameEnd(r: ByteSource, pos: Long, h: FrameHeader, first: FrameHeader, maxSize: Long): Long? {
        var crc = Crc.crc16(0, h.bytes, 0, h.length)
        // q 는 다음 머리 후보. CRC 는 [pos, q - 2) 까지여야 하므로 두 바이트 늦게 따라간다.
        var q = pos + h.length
        val limit = minOf(r.length, pos + maxSize)
        var prev2 = -1 // q-2 의 바이트
        var prev1 = -1 // q-1 의 바이트
        while (q <= limit) {
            // 후보: q 가 파일 끝이거나 q 에 같은 흐름의 머리가 있다. 그때 [pos, q-2) 의 CRC 가 q-2, q-1 의 두 바이트와 같아야 한다.
            if (prev2 >= 0) {
                val stored = (prev2 shl 8) or prev1
                if (q == r.length) {
                    if (crc == stored) return q
                } else if (r.u8(q) == 0xFF && (r.u8(q + 1) and 0xFE) == 0xF8 && crc == stored) {
                    val next = parseHeader(r, q)
                    if (next != null && next.sameStream(first)) return q
                } else if (crc == stored && tagTailAt(r, q)) {
                    // 끝에 태그 꼬리(ID3v1·APE·ID3v2)를 단 FLAC — 그 앞이 마지막 프레임의 끝이다. 예전에는 끝을 파일 끝이나 다음 동기
                    // 부호로만 받아 끝까지 자르면 마지막 프레임을 잃었다.
                    return q
                }
            }
            if (q == r.length) return null
            // 한 바이트 나아간다 — 두 바이트 늦게 CRC 에 넣는다.
            if (prev2 >= 0) crc = Crc.crc16Byte(crc, prev2)
            prev2 = prev1
            prev1 = r.u8(q)
            q++
        }
        return null
    }

    /** [pos] 부터 파일 끝까지가 태그 꼬리인가 — ID3v1(정확히 128바이트 'TAG'), APE('APETAGEX'), 뒤에 붙인 ID3v2('ID3'). */
    private fun tagTailAt(r: ByteSource, pos: Long): Boolean {
        val rest = r.length - pos
        return (rest == 128L && r.ascii(pos, 3) == "TAG") || r.ascii(pos, 8) == "APETAGEX" || r.ascii(pos, 3) == "ID3"
    }

    /** 번호를 UTF-8 꼴로(최대 36비트, 7바이트). */
    fun encodeNumber(v: Long): ByteArray = when {
        v < 0x80 -> byteArrayOf(v.toByte())
        v < 0x800 -> multi(v, 2, 0xC0)
        v < 0x10000 -> multi(v, 3, 0xE0)
        v < 0x200000 -> multi(v, 4, 0xF0)
        v < 0x4000000 -> multi(v, 5, 0xF8)
        v < 0x80000000L -> multi(v, 6, 0xFC)
        else -> multi(v, 7, 0xFE)
    }

    private fun multi(v: Long, n: Int, lead: Int): ByteArray {
        val out = ByteArray(n)
        var x = v
        for (i in n - 1 downTo 1) {
            out[i] = (0x80 or (x and 0x3F).toInt()).toByte()
            x = x shr 6
        }
        out[0] = (lead or x.toInt()).toByte()
        return out
    }

    private fun be(b: ByteArray, at: Int, n: Int): Long {
        var v = 0L
        for (i in 0 until n) v = (v shl 8) or (b[at + i].toLong() and 0xFF)
        return v
    }

    private fun put(b: ByteArray, at: Int, n: Int, v: Long) {
        for (i in 0 until n) b[at + i] = (v shr (8 * (n - 1 - i))).toByte()
    }

    const val TYPE_STREAMINFO = 0
    private const val TYPE_SEEKTABLE = 3
    const val TYPE_VORBIS_COMMENT = 4
    const val TYPE_PICTURE = 6
    private const val MAX_SEEKTABLE_BYTES = 1 shl 20
    const val MAX_KEPT_BYTES = 32L * 1024 * 1024

    /** 담을 프레임 수의 상한(길이 하나씩 — 16 MB). 넘으면 너무 긴 구간으로 본다([CutPlan.TooLong]). */
    private const val MAX_FRAMES = 4_000_000

    /** '끝까지' 를 뜻하는 큰 끝을 셈이 넘치지 않는 값으로 누른다(열흘 — FLAC 의 가장 큰 표본율을 곱해도 Long 안이다). */
    private const val MAX_END_US = 10L * 24 * 3600 * 1_000_000

    /** STREAMINFO 가 최대 프레임 크기를 모를 때(0) 한 프레임에서 끝을 찾을 거리. */
    private const val DEFAULT_MAX_FRAME = 16L * 1024 * 1024

    /** 적힌 최대 크기보다 조금 더 본다(적은 도구가 틀렸을 수 있다). */
    private const val MAX_FRAME_SLACK = 64L * 1024
    private const val COPY_BUFFER = 64 * 1024
}

/** FLAC 의 CRC. 둘 다 반사 없이, 초기값 0. */
internal object Crc {
    private val T8 = IntArray(256) { i ->
        var c = i
        repeat(8) { c = if (c and 0x80 != 0) ((c shl 1) xor 0x07) and 0xFF else (c shl 1) and 0xFF }
        c
    }
    private val T16 = IntArray(256) { i ->
        var c = i shl 8
        repeat(8) { c = if (c and 0x8000 != 0) ((c shl 1) xor 0x8005) and 0xFFFF else (c shl 1) and 0xFFFF }
        c
    }

    /** CRC-8(다항식 0x07). */
    fun crc8(b: ByteArray, off: Int, len: Int): Int {
        var c = 0
        for (i in off until off + len) c = T8[(c xor (b[i].toInt() and 0xFF)) and 0xFF]
        return c
    }

    /** CRC-16(다항식 0x8005)를 [c] 에서 이어 센다. */
    fun crc16(c: Int, b: ByteArray, off: Int, len: Int): Int {
        var x = c
        for (i in off until off + len) x = crc16Byte(x, b[i].toInt() and 0xFF)
        return x
    }

    fun crc16Byte(c: Int, byte: Int): Int = ((c shl 8) xor T16[((c shr 8) xor byte) and 0xFF]) and 0xFFFF
}
