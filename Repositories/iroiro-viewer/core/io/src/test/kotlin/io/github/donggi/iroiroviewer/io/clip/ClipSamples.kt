package io.github.donggi.iroiroviewer.io.clip

import java.io.ByteArrayOutputStream

/**
 * 시험이 짓는 소리 표본 — WAV·MP3·FLAC. 저장소에 표본을 넣지 않는다(표본 규칙) — 시험이 그때그때 짓는다.
 */
internal object ClipSamples {

    /** 16비트 PCM WAV. [frames] 개의 표본 묶음, 값은 묶음 번호에서 지어 자른 자리를 바이트로 확인할 수 있게 한다. */
    fun wav(rate: Int, channels: Int, frames: Int, list: ByteArray? = null, extensible: Boolean = false): ByteArray {
        val blockAlign = channels * 2
        val data = ByteArray(frames * blockAlign)
        for (f in 0 until frames) for (c in 0 until channels) {
            val v = ((f * 7 + c * 3) and 0x7FFF)
            data[f * blockAlign + c * 2] = v.toByte()
            data[f * blockAlign + c * 2 + 1] = (v shr 8).toByte()
        }
        val fmt = ByteArray(if (extensible) 40 else 16)
        fmt.putU16(0, if (extensible) 0xFFFE else 1)
        fmt.putU16(2, channels)
        fmt.putU32le(4, rate.toLong())
        fmt.putU32le(8, (rate * blockAlign).toLong())
        fmt.putU16(12, blockAlign)
        fmt.putU16(14, 16)
        if (extensible) {
            fmt.putU16(16, 22)
            fmt.putU16(18, 16)
            fmt.putU32le(20, 3)
            fmt.putU16(24, 1) // 하위 형식 PCM(GUID 의 앞 두 바이트)
        }
        val out = ByteArrayOutputStream()
        out.write("RIFF".toByteArray())
        out.write(ByteArray(4))
        out.write("WAVE".toByteArray())
        chunk(out, "fmt ", fmt)
        list?.let { chunk(out, "LIST", it) }
        chunk(out, "data", data)
        val b = out.toByteArray()
        b.putU32le(4, (b.size - 8).toLong())
        return b
    }

    private fun chunk(out: ByteArrayOutputStream, id: String, body: ByteArray) {
        out.write(id.toByteArray())
        val size = ByteArray(4).also { it.putU32le(0, body.size.toLong()) }
        out.write(size)
        out.write(body)
        if (body.size % 2 == 1) out.write(0)
    }

    private fun ByteArray.putU16(at: Int, v: Int) {
        this[at] = v.toByte()
        this[at + 1] = (v shr 8).toByte()
    }

    // ---- MP3 -----------------------------------------------------------------

    /**
     * MPEG-1 Layer III 프레임 하나(128 kbps · 44.1 kHz · 스테레오, CRC 없음 — 417바이트, 패딩이면 418). 측면 정보가 0 이라 디코더는
     * 무음으로 푼다. 마지막 바이트에 [mark] 를 적어 어느 프레임인지 알아본다(주 데이터 자리라 구조에는 영향이 없다).
     */
    fun mp3Frame(mark: Int, padding: Boolean = false): ByteArray {
        val len = 417 + if (padding) 1 else 0
        val b = ByteArray(len)
        b[0] = 0xFF.toByte()
        b[1] = 0xFB.toByte()
        b[2] = ((9 shl 4) or (if (padding) 2 else 0)).toByte()
        b[3] = 0x00
        b[len - 1] = mark.toByte()
        return b
    }

    /** Xing 머리 프레임(가변 비트율 목차) — 전체 [frames]·[bytes] 를 적는다. */
    fun xingFrame(frames: Int, bytes: Int): ByteArray {
        val b = mp3Frame(0)
        b[len(b) - 1] = 0
        val at = 4 + 32
        "Xing".toByteArray().copyInto(b, at)
        b.putU32be(at + 4, 0x0F)
        b.putU32be(at + 8, frames.toLong())
        b.putU32be(at + 12, bytes.toLong())
        return b
    }

    private fun len(b: ByteArray) = b.size

    /** ID3v2.3 태그(제목 하나). */
    fun id3v2(title: String): ByteArray {
        val text = byteArrayOf(0) + title.toByteArray(Charsets.ISO_8859_1)
        val frame = ByteArrayOutputStream()
        frame.write("TIT2".toByteArray())
        frame.write(ByteArray(4).also { it.putU32be(0, text.size.toLong()) })
        frame.write(byteArrayOf(0, 0))
        frame.write(text)
        val body = frame.toByteArray()
        val head = byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 3, 0, 0) + syncsafe(body.size)
        return head + body
    }

    private fun syncsafe(n: Int) = byteArrayOf(
        ((n shr 21) and 0x7F).toByte(),
        ((n shr 14) and 0x7F).toByte(),
        ((n shr 7) and 0x7F).toByte(),
        (n and 0x7F).toByte(),
    )

    /** ID3v1 태그(128바이트). */
    fun id3v1(): ByteArray = ByteArray(128).also { "TAG".toByteArray().copyInto(it) ; "title".toByteArray().copyInto(it, 3) }

    // ---- FLAC ----------------------------------------------------------------

    /**
     * 손으로 짓는 FLAC — 하위 프레임이 VERBATIM(날것의 표본)이라 인코더 없이 지을 수 있고, 잘라 낸 결과를 시험이 직접 풀어 견줄 수
     * 있다. 16비트, 블록 크기는 머리에 16비트로 적는다(부호 7), 표본율은 STREAMINFO 를 따른다(부호 0).
     *
     * @param samples 채널마다 표본.
     * @param variable 가변 블록(머리에 표본 번호를 적는다)이면 참.
     * @param seekTable 목차를 단다(블록마다 한 점).
     */
    fun flac(
        samples: Array<ShortArray>,
        rate: Int,
        blockSize: Int,
        variable: Boolean = false,
        comment: String? = "TITLE=clip",
        seekTable: Boolean = false,
    ): ByteArray {
        val channels = samples.size
        val n = samples[0].size
        val frames = ArrayList<ByteArray>()
        var start = 0
        var index = 0
        while (start < n) {
            val bs = minOf(blockSize, n - start)
            frames += flacFrame(samples, start, bs, if (variable) start.toLong() else index.toLong(), variable, channels)
            start += bs
            index++
        }
        val info = ByteArray(34)
        info.putU16be(0, blockSize)
        info.putU16be(2, blockSize)
        info.put24(4, frames.minOf { it.size })
        info.put24(7, frames.maxOf { it.size })
        // 표본율(20) · 채널-1(3) · 표본크기-1(5) · 전체 표본(36)
        val packed = (rate.toLong() shl 44) or ((channels - 1).toLong() shl 41) or (15L shl 36) or n.toLong()
        for (i in 0 until 8) info[10 + i] = (packed shr (56 - 8 * i)).toByte()
        val blocks = ArrayList<Pair<Int, ByteArray>>()
        blocks += 0 to info
        if (seekTable) {
            val st = ByteArrayOutputStream()
            var offset = 0L
            frames.forEachIndexed { i, f ->
                val p = ByteArray(18)
                p.putLong(0, i.toLong() * blockSize)
                p.putLong(8, offset)
                p.putU16be(16, minOf(blockSize, n - i * blockSize))
                st.write(p)
                offset += f.size
            }
            blocks += 3 to st.toByteArray()
        }
        comment?.let {
            val vc = ByteArrayOutputStream()
            fun le32(v: Int) = vc.write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte()))
            val vendor = "iroiro-test".toByteArray()
            le32(vendor.size)
            vc.write(vendor)
            le32(1)
            val c = it.toByteArray()
            le32(c.size)
            vc.write(c)
            blocks += 4 to vc.toByteArray()
        }
        val out = ByteArrayOutputStream()
        out.write("fLaC".toByteArray())
        blocks.forEachIndexed { i, (type, body) ->
            out.write((if (i == blocks.lastIndex) 0x80 else 0) or type)
            out.write(byteArrayOf((body.size shr 16).toByte(), (body.size shr 8).toByte(), body.size.toByte()))
            out.write(body)
        }
        frames.forEach { out.write(it) }
        return out.toByteArray()
    }

    private fun flacFrame(samples: Array<ShortArray>, start: Int, bs: Int, number: Long, variable: Boolean, channels: Int): ByteArray {
        val h = ByteArrayOutputStream()
        h.write(0xFF)
        h.write(if (variable) 0xF9 else 0xF8)
        h.write((7 shl 4) or 0) // 블록 크기: 16비트로 적는다 · 표본율: STREAMINFO
        h.write(((channels - 1) shl 4) or (4 shl 1)) // 독립 채널 · 16비트
        h.write(FlacCut.encodeNumber(number))
        h.write(((bs - 1) shr 8) and 0xFF)
        h.write((bs - 1) and 0xFF)
        val head = h.toByteArray()
        val frame = ByteArrayOutputStream()
        frame.write(head)
        frame.write(Crc.crc8(head, 0, head.size))
        for (c in 0 until channels) {
            frame.write(0x02) // VERBATIM
            for (i in 0 until bs) {
                val v = samples[c][start + i].toInt()
                frame.write((v shr 8) and 0xFF)
                frame.write(v and 0xFF)
            }
        }
        val body = frame.toByteArray()
        val crc = Crc.crc16(0, body, 0, body.size)
        return body + byteArrayOf((crc shr 8).toByte(), crc.toByte())
    }

    private fun ByteArray.putU16be(at: Int, v: Int) {
        this[at] = (v shr 8).toByte()
        this[at + 1] = v.toByte()
    }

    private fun ByteArray.put24(at: Int, v: Int) {
        this[at] = (v shr 16).toByte()
        this[at + 1] = (v shr 8).toByte()
        this[at + 2] = v.toByte()
    }

    private fun ByteArray.putLong(at: Int, v: Long) {
        for (i in 0 until 8) this[at + i] = (v shr (56 - 8 * i)).toByte()
    }

    /**
     * VERBATIM FLAC 을 시험 쪽에서 푼다 — 프레임을 동기 부호와 CRC-16 으로 갈라, 머리의 번호와 표본을 돌려준다. 우리 자르개의 결과를
     * **다른 길로** 다시 읽어 견주려는 것이다(머리·CRC 를 바르게 다시 썼는가).
     */
    fun decodeVerbatim(b: ByteArray, channels: Int): Decoded {
        check(String(b, 0, 4) == "fLaC")
        var pos = 4
        var info: ByteArray? = null
        val types = ArrayList<Int>()
        while (true) {
            val h = b[pos].toInt() and 0xFF
            val len = ((b[pos + 1].toInt() and 0xFF) shl 16) or ((b[pos + 2].toInt() and 0xFF) shl 8) or (b[pos + 3].toInt() and 0xFF)
            types += h and 0x7F
            if (h and 0x7F == 0) info = b.copyOfRange(pos + 4, pos + 4 + len)
            pos += 4 + len
            if (h and 0x80 != 0) break
        }
        val firstFrame = pos
        val numbers = ArrayList<Long>()
        val frames = ArrayList<ByteArray>()
        val out = Array(channels) { ArrayList<Short>() }
        while (pos < b.size) {
            check((b[pos].toInt() and 0xFF) == 0xFF) { "동기 부호가 없다: $pos" }
            // 번호의 길이.
            val lead = b[pos + 4].toInt() and 0xFF
            val nl = when {
                lead and 0x80 == 0 -> 1
                lead and 0xE0 == 0xC0 -> 2
                lead and 0xF0 == 0xE0 -> 3
                lead and 0xF8 == 0xF0 -> 4
                lead and 0xFC == 0xF8 -> 5
                lead and 0xFE == 0xFC -> 6
                else -> 7
            }
            var number = if (nl == 1) lead.toLong() else (lead and (0xFF shr (nl + 1))).toLong()
            for (i in 1 until nl) number = (number shl 6) or (b[pos + 4 + i].toLong() and 0x3F)
            val bs = (((b[pos + 4 + nl].toInt() and 0xFF) shl 8) or (b[pos + 5 + nl].toInt() and 0xFF)) + 1
            val headLen = 4 + nl + 2
            check(Crc.crc8(b, pos, headLen) == (b[pos + headLen].toInt() and 0xFF)) { "CRC-8 이 틀렸다" }
            var p = pos + headLen + 1
            for (c in 0 until channels) {
                check(b[p].toInt() == 0x02)
                p++
                for (i in 0 until bs) {
                    out[c] += (((b[p].toInt() and 0xFF) shl 8) or (b[p + 1].toInt() and 0xFF)).toShort()
                    p += 2
                }
            }
            val crc = Crc.crc16(0, b, pos, p - pos)
            check(crc == (((b[p].toInt() and 0xFF) shl 8) or (b[p + 1].toInt() and 0xFF))) { "CRC-16 이 틀렸다" }
            numbers += number
            frames += b.copyOfRange(pos, p + 2)
            pos = p + 2
        }
        return Decoded(info!!, types, numbers, Array(channels) { c -> out[c].toShortArray() }, firstFrame, frames)
    }

    /**
     * @param firstFrameOffset 첫 프레임의 자리(그 앞이 `fLaC` + 메타데이터 블록 — 그릇의 CodecPrivate 와 같은 모양이다).
     * @param frames 프레임들(머리부터 CRC-16 까지) — 그릇에서 뽑은 표본처럼 쓴다.
     */
    class Decoded(
        val streamInfo: ByteArray,
        val blockTypes: List<Int>,
        val numbers: List<Long>,
        val samples: Array<ShortArray>,
        val firstFrameOffset: Int = 0,
        val frames: List<ByteArray> = emptyList(),
    ) {
        val totalSamples: Long
            get() = ((streamInfo[13].toLong() and 0x0F) shl 32) or
                ((streamInfo[14].toLong() and 0xFF) shl 24) or ((streamInfo[15].toLong() and 0xFF) shl 16) or
                ((streamInfo[16].toLong() and 0xFF) shl 8) or (streamInfo[17].toLong() and 0xFF)
    }
}
