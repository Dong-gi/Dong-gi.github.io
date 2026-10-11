package io.github.donggi.iroiroviewer.io.clip

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.OutputStream
import java.io.RandomAccessFile

/**
 * MPEG 오디오(MP3·MP2·MP1)의 한 구간 — **프레임 경계**에서 자른다. MP3 프레임은 1152표본(44.1 kHz 에서 약 26 ms)이라 시작과 끝이
 * 그만큼 넓어질 수 있다.
 *
 * ## 무엇을 옮기는가
 *
 * - **ID3v2·ID3v1 태그는 그대로 옮긴다** — 제목·가수·앨범 그림. 잘라 낸 곡이 어느 곡의 것인지 남는다. 다만 ID3v2 의 `TLEN`(길이)은
 *   자른 길이로 고쳐 쓴다 — 그대로 두면 태그를 믿는 재생기가 원본 길이를 그린다.
 * - **Xing·Info·VBRI 머리 프레임은 새로 쓴다.** 원본의 것은 전체 프레임 수·바이트 수를 적고 있어, 그대로 두면 자른 파일의 길이가
 *   원본 길이로 보인다(가변 비트율이면 탐색도 틀린다). 같은 프레임 머리로 `Xing`(가변)·`Info`(고정) 를 쓰고 프레임 수·바이트 수와
 *   **목차(TOC)** 를 적는다 — 목차가 없으면 media3 의 `XingSeeker` 는 탐색하지 못하고 처음으로 돌아간다. LAME 꼬리
 *   (무간격 재생의 지연값)는 버린다 — 자른 곳에는 맞지 않는다.
 *
 * ## 훑는 범위
 *
 * 프레임 수로 시각을 세므로 **고른 끝까지만** 훑는다 — 파일 끝까지 훑지 않는다(두 시간짜리의 앞 10초를 자르는데 끝까지
 * 읽었고, 그동안 취소가 닿지 않았다).
 *
 * ## 기억하는 것
 *
 * 고른 구간의 프레임을 **이어진 덩어리**(자리·끝)로만 든다 — 프레임 사이에 쓰레기가 없으면 덩어리 하나다. 목차에 쓸 프레임 자리는
 * 성기게([SparseOffsets]). 예전에는 프레임마다 자리·길이를 들고 목차를 지을 때 자리를 한 벌 더 지어, 저비트율·초장시간 구간('여기서
 * 끝까지' 의 오디오북)에서 순간 100 MiB 를 넘게 들었다.
 *
 * ## 알려진 한계 — 비트 저장고
 *
 * MP3 프레임은 앞 프레임에 남은 자리에 자기 데이터를 빌려 쓸 수 있다(비트 저장고). 그래서 잘라 낸 **첫 프레임 한둘은 디코더가 버리거나
 * 짧게 소리가 깨질 수 있다.** 다시 인코딩하지 않고 자르는 도구(mp3DirectCut 등)가 모두 받아들이는 대가다.
 */
internal object Mp3Cut {

    /** 프레임 머리 하나. */
    data class Header(
        /** 1·2·25(=2.5). */
        val version: Int,
        /** 1·2·3. */
        val layer: Int,
        val bitrateKbps: Int,
        val sampleRate: Int,
        val padding: Int,
        /** 0 스테레오 · 1 조인트 · 2 듀얼 · 3 모노. */
        val channelMode: Int,
        val frameLength: Int,
        val samplesPerFrame: Int,
    ) {
        /** 같은 흐름의 프레임인가 — 판·층·표본율이 같아야 한다(비트율·패딩·채널 모드는 프레임마다 바뀔 수 있다). */
        fun sameStream(o: Header) = version == o.version && layer == o.layer && sampleRate == o.sampleRate

        /** 측면 정보의 길이 — Xing 머리가 그 뒤에 온다. */
        val sideInfoLength: Int
            get() = if (version == 1) (if (channelMode == 3) 17 else 32) else (if (channelMode == 3) 9 else 17)
    }

    /** 네 바이트에서 머리를 읽는다. 머리가 아니면 null(자유 비트율도 null — 프레임 길이를 알 수 없다). */
    fun parseHeader(b0: Int, b1: Int, b2: Int, b3: Int): Header? {
        if (b0 != 0xFF || (b1 and 0xE0) != 0xE0) return null
        val version = when ((b1 shr 3) and 3) {
            0 -> 25
            2 -> 2
            3 -> 1
            else -> return null
        }
        val layer = when ((b1 shr 1) and 3) {
            1 -> 3
            2 -> 2
            3 -> 1
            else -> return null
        }
        val bitrateIndex = (b2 shr 4) and 0xF
        if (bitrateIndex == 0 || bitrateIndex == 15) return null
        val rateIndex = (b2 shr 2) and 3
        if (rateIndex == 3) return null
        if ((b3 and 3) == 2) return null // 강조(emphasis) 2 는 예약값이다
        val bitrate = bitrateOf(version, layer, bitrateIndex)
        val sampleRate = SAMPLE_RATES[if (version == 1) 0 else if (version == 2) 1 else 2][rateIndex]
        val padding = (b2 shr 1) and 1
        val spf = when (layer) {
            1 -> 384
            2 -> 1152
            else -> if (version == 1) 1152 else 576
        }
        val length = when (layer) {
            1 -> (12 * bitrate * 1000 / sampleRate + padding) * 4
            2 -> 144 * bitrate * 1000 / sampleRate + padding
            else -> (if (version == 1) 144 else 72) * bitrate * 1000 / sampleRate + padding
        }
        if (length < 4) return null
        return Header(version, layer, bitrate, sampleRate, padding, (b3 shr 6) and 3, length, spf)
    }

    /** [b] 의 [at] 자리 네 바이트에서 머리를 읽는다. 모자라면 null. */
    fun parseHeader(b: ByteArray, at: Int = 0, size: Int = b.size): Header? {
        if (at < 0 || at + 4 > size) return null
        return parseHeader(b[at].toInt() and 0xFF, b[at + 1].toInt() and 0xFF, b[at + 2].toInt() and 0xFF, b[at + 3].toInt() and 0xFF)
    }

    private fun bitrateOf(version: Int, layer: Int, index: Int): Int = when {
        version == 1 && layer == 1 -> V1L1[index]
        version == 1 && layer == 2 -> V1L2[index]
        version == 1 -> V1L3[index]
        layer == 1 -> V2L1[index]
        else -> V2L23[index]
    }

    private fun headerAt(r: ByteSource, pos: Long): Header? {
        val b0 = r.u8(pos)
        if (b0 != 0xFF) return null
        return parseHeader(b0, r.u8(pos + 1), r.u8(pos + 2), r.u8(pos + 3))
    }

    /**
     * [from] 부터 [limit] 바이트 안에서 **이어지는 두 프레임**의 첫 자리를 찾는다. 머리 하나만 보면 데이터 속의 우연한 `FF Ex` 에
     * 속는다 — 그 프레임 끝에 같은 흐름의 머리가 또 있어야 믿는다. 파일 끝의 마지막 프레임은 다음 머리가 없어도 믿는다.
     */
    private fun findSync(r: ByteSource, from: Long, limit: Long, end: Long, like: Header?): Long {
        var p = from
        val stop = minOf(end - 4, from + limit)
        while (p <= stop) {
            val h = headerAt(r, p)
            if (h != null && (like == null || h.sameStream(like)) && p + h.frameLength <= end) {
                val next = p + h.frameLength
                if (next == end) return p
                val n = headerAt(r, next)
                if (n != null && n.sameStream(h)) return p
            }
            p++
        }
        return -1
    }

    /** 이 파일이 MPEG 오디오로 보이는가(ID3v2 를 건너뛰고 이어지는 두 프레임). 판별에 쓴다. */
    fun looksLikeMp3(raf: RandomAccessFile): Boolean {
        val r = WindowReader(raf)
        val start = id3v2Length(r)
        return findSync(r, start, SNIFF_LIMIT, r.length, null) == start || (start > 0 && findSync(r, start, 4096, r.length, null) >= 0)
    }

    suspend fun plan(raf: RandomAccessFile, startUs: Long, endUs: Long, sourceExt: String): CutPlan {
        val r = WindowReader(raf)
        val id3 = id3v2Length(r)
        // 끝의 ID3v1(128바이트 'TAG').
        val hasV1 = r.length >= id3 + 128 && r.ascii(r.length - 128, 3) == "TAG"
        val audioEnd = if (hasV1) r.length - 128 else r.length
        val first = findSync(r, id3, RESYNC_LIMIT, audioEnd, null)
        if (first < 0) return CutPlan.Unsupported
        val firstHeader = headerAt(r, first) ?: return CutPlan.Unsupported

        // 첫 프레임이 Xing·Info·VBRI 머리인가 — 소리가 아니라 목차다.
        var info: InfoFrame? = null
        val xingAt = first + 4 + firstHeader.sideInfoLength
        when (r.ascii(xingAt, 4)) {
            "Xing" -> info = InfoFrame(first, firstHeader, "Xing", xingAt - first)
            "Info" -> info = InfoFrame(first, firstHeader, "Info", xingAt - first)
        }
        if (info == null && r.ascii(first + 4 + 32, 4) == "VBRI") {
            // VBRI(프라운호퍼)는 Xing 으로 바꿔 쓴다 — 같은 프레임 머리에 Xing 자리가 있다.
            info = InfoFrame(first, firstHeader, "Xing", 4L + firstHeader.sideInfoLength)
        }

        // 프레임 차례 = 시각. 고른 구간의 프레임만 기억하고, 끝을 넘으면 그만 훑는다.
        val spf = firstHeader.samplesPerFrame.toLong()
        val rate = firstHeader.sampleRate.toLong()
        val unit = spf * 1_000_000L
        val startFrame = startUs.coerceAtLeast(0) * rate / unit
        val endFrame = (endUs.coerceAtMost(MAX_END_US) * rate + unit - 1) / unit
        if (endFrame <= startFrame) return CutPlan.Empty

        // 담을 프레임 — 이어진 덩어리 [시작, 끝) 의 쌍(머리말 '기억하는 것'). 목차에 쓸 자리는 새 파일에서의 자리(머리 프레임 처음부터).
        val runs = LongArrayList()
        var runStart = -1L
        var runEnd = -1L
        val toc = SparseOffsets()
        val infoLength = info?.header?.frameLength?.toLong() ?: 0L
        var n = 0L
        var audioBytes = 0L
        var index = 0L
        var vbr = false
        var firstBitrate = -1
        var pos = if (info != null) first + firstHeader.frameLength else first
        while (pos + 4 <= audioEnd && index < endFrame) {
            if (index % CANCEL_EVERY == 0L) currentCoroutineContext().ensureActive()
            val h = headerAt(r, pos)
            if (h != null && h.sameStream(firstHeader) && pos + h.frameLength <= audioEnd) {
                if (index >= startFrame) {
                    if (n >= MAX_FRAMES) return CutPlan.TooLong
                    if (pos != runEnd) {
                        // 쓰레기를 건너 새 덩어리가 시작된다. 늘리기 **전에** 상한을 본다(늘린 뒤에 보면 상한에서 배열이 한 번 더
                        // 두 배가 된다).
                        if (runStart >= 0) {
                            if (runs.size >= MAX_RUNS * 2) return CutPlan.TooLong
                            runs.add(runStart)
                            runs.add(runEnd)
                        }
                        runStart = pos
                        runEnd = pos
                    }
                    runEnd += h.frameLength
                    toc.add(infoLength + audioBytes)
                    audioBytes += h.frameLength
                    n++
                    if (firstBitrate < 0) firstBitrate = h.bitrateKbps else if (h.bitrateKbps != firstBitrate) vbr = true
                }
                index++
                pos += h.frameLength
                continue
            }
            // 프레임 사이의 쓰레기(잘린 프레임·APE 태그 앞) — 가까운 곳에서 다시 맞춘다. 못 찾으면 거기서 끝난다.
            val again = findSync(r, pos + 1, RESYNC_LIMIT, audioEnd, firstHeader)
            if (again < 0) break
            pos = again
        }
        if (n == 0L) return CutPlan.Empty
        runs.add(runStart)
        runs.add(runEnd)

        val infoBytes = info?.let { rebuildInfo(r, it, toc, audioBytes, vbr) }
        // TLEN — 자른 길이(밀리초)로. 고칠 수 없는 꼴이면 그대로 옮긴다.
        val tlen = if (id3 > 0) Id3.tlenDigits(r, id3) else null
        val tlenBytes = tlen?.let { Id3.digits(n * spf * 1000 / rate, (it.last - it.first + 1).toInt()) }
        val totalBytes = id3 + (infoBytes?.size ?: 0) + audioBytes + if (hasV1) 128 else 0
        val ext = if (sourceExt in MPEG_EXTENSIONS) sourceExt else if (firstHeader.layer == 3) "mp3" else "mp${firstHeader.layer}"

        return CutPlan.Ok(
            object : AudioCut {
                override val startUs = startFrame * unit / rate
                override val endUs = (startFrame + n) * unit / rate
                override val outputBytes = totalBytes
                override val extension = ext

                override suspend fun writeTo(out: OutputStream, onBytes: (Long) -> Unit) {
                    val buf = ByteArray(COPY_BUFFER)
                    if (id3 > 0) {
                        if (tlen != null && tlenBytes != null) {
                            copyRange(raf, 0, tlen.first, out, buf, onBytes)
                            out.write(tlenBytes)
                            onBytes(tlenBytes.size.toLong())
                            copyRange(raf, tlen.last + 1, id3 - tlen.last - 1, out, buf, onBytes)
                        } else {
                            copyRange(raf, 0, id3, out, buf, onBytes)
                        }
                    }
                    infoBytes?.let {
                        out.write(it)
                        onBytes(it.size.toLong())
                    }
                    // 이어진 덩어리째 옮긴다(대개 덩어리 하나다).
                    for (i in 0 until runs.size step 2) copyRange(raf, runs[i], runs[i + 1] - runs[i], out, buf, onBytes)
                    if (hasV1) copyRange(raf, r.length - 128, 128, out, buf, onBytes)
                }
            }
        )
    }

    /** 원본의 Xing·Info·VBRI 머리 프레임. [tagOffset] 은 프레임 안에서 'Xing'·'Info' 를 쓸 자리다. */
    private class InfoFrame(val offset: Long, val header: Header, val tag: String, val tagOffset: Long)

    /**
     * 머리 프레임을 새로 쓴다 — 원본 프레임의 머리 네 바이트와 길이를 그대로 두고, 나머지를 0 으로 비운 뒤 태그·깃발·프레임 수·
     * 바이트 수·목차를 적는다. 측면 정보가 0 이면 디코더는 이 프레임을 무음으로 푼다. 태그는 원본을 따르되 VBRI 는 Xing 이다.
     *
     * @param offsets 담는 프레임마다 새 파일에서의 자리(머리 프레임 처음부터 — 옮긴 뒤에는 프레임이 이어 붙는다). 성기게 든 것이다.
     */
    private fun rebuildInfo(
        r: ByteSource,
        info: InfoFrame,
        offsets: SparseOffsets,
        audioBytes: Long,
        vbr: Boolean,
    ): ByteArray? {
        val len = info.header.frameLength
        if (info.tagOffset + 16 > len) return null
        val b = ByteArray(len)
        if (r.read(info.offset, b, 0, 4) != 4) return null
        val frames = offsets.count.toInt()
        val dataSize = len + audioBytes
        val toc = XingInfo.toc(frames, dataSize) { offsets.offsetOf(it.toLong()) }
        XingInfo.fill(b, info.tagOffset.toInt(), tag = if (info.tag == "Info" && !vbr) "Info" else "Xing", frames, dataSize, toc)
        return b
    }

    private val SAMPLE_RATES = arrayOf(
        intArrayOf(44100, 48000, 32000),
        intArrayOf(22050, 24000, 16000),
        intArrayOf(11025, 12000, 8000),
    )
    private val V1L1 = intArrayOf(0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448)
    private val V1L2 = intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384)
    private val V1L3 = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320)
    private val V2L1 = intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256)
    private val V2L23 = intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160)

    private val MPEG_EXTENSIONS = setOf("mp3", "mp2", "mp1", "mpga", "mpa")

    /** 판별할 때 첫 프레임을 찾는 거리. ID3 바로 뒤에 프레임이 와야 MP3 로 본다(쓰레기를 두고 시작하는 파일은 드물다). */
    private const val SNIFF_LIMIT = 0L

    /** 흐름이 끊긴 자리에서 다시 맞출 때 훑는 거리. */
    private const val RESYNC_LIMIT = 64L * 1024

    /**
     * 담을 프레임 수의 상한 — Xing 의 프레임 수 칸(32비트)과 그 셈의 Int 에 드는 만큼. 프레임마다 아무것도 들지 않게 된 뒤로는 메모리의
     * 상한이 아니다(예전에는 44.1 kHz 에서 약 60시간 — 프레임마다 12바이트를 들었다).
     */
    private const val MAX_FRAMES = Int.MAX_VALUE.toLong()

    /** 이어진 덩어리 수의 상한(16 MiB) — 프레임마다 쓰레기를 끼운 흐름이면 덩어리가 프레임 수만큼 는다. 넘으면 너무 긴 구간으로 본다. */
    private const val MAX_RUNS = 1 shl 20

    /** '끝까지' 를 뜻하는 큰 끝을 셈이 넘치지 않는 값으로 누른다(열흘 — 표본율을 곱해도 Long 안이다). */
    private const val MAX_END_US = 10L * 24 * 3600 * 1_000_000

    /** 몇 프레임마다 취소를 볼까. */
    private const val CANCEL_EVERY = 4096L

    private const val COPY_BUFFER = 64 * 1024
}

/**
 * MP3 의 Xing·Info 머리 프레임 — 프레임 수·바이트 수·목차(TOC). 자르기([Mp3Cut])와 소리 뽑기(`AudioExtract`)가 함께 쓴다.
 *
 * 목차 100 칸의 i 번째는 'i% 시각의 프레임이 머리 프레임 처음에서 떨어진 바이트 / 전체 바이트 × 256' 이다. media3 의 `XingSeeker` 가
 * 이 자리(머리 프레임 처음)와 전체 크기(바이트 칸)로 읽는다.
 */
internal object XingInfo {

    /** 태그(4) + 깃발(4) + 프레임 수(4) + 바이트 수(4) + 목차(100). */
    const val TAG_BYTES = 116

    /**
     * 소리 프레임의 머리 [template](네 바이트)과 같은 판·층·표본율·채널 모드로 머리 프레임을 새로 짓는다 — 태그와 목차가 들어갈 만큼
     * 큰 비트율을 고른다. 몸이 0 이라 디코더는 무음으로 푼다(LAME 의 Info 프레임과 같다). CRC 깃발은 끈다(우리 프레임에는 CRC 가
     * 없다 — 켜 두면 태그 자리가 두 바이트 밀린다).
     *
     * @return 프레임(태그 자리는 0)과 태그 자리. 층 III 이 아니거나 맞는 비트율이 없으면 null.
     */
    fun synthesize(template: ByteArray): Pair<ByteArray, Int>? {
        val h = Mp3Cut.parseHeader(template) ?: return null
        if (h.layer != 3) return null
        val need = 4 + h.sideInfoLength + TAG_BYTES
        val b1 = (template[1].toInt() and 0xFF) or 0x01
        val rateBits = template[2].toInt() and 0x0C
        val b3 = template[3].toInt() and 0xC0
        for (index in 1..14) {
            val b2 = (index shl 4) or rateBits
            val hh = Mp3Cut.parseHeader(0xFF, b1, b2, b3) ?: continue
            if (hh.frameLength >= need) {
                val f = ByteArray(hh.frameLength)
                f[0] = 0xFF.toByte()
                f[1] = b1.toByte()
                f[2] = b2.toByte()
                f[3] = b3.toByte()
                return f to 4 + hh.sideInfoLength
            }
        }
        return null
    }

    /**
     * 프레임 [frame] 의 [at] 자리에 태그를 적는다. 목차는 자리가 넉넉할 때만(없으면 프레임 수·바이트 수만 — media3 는 그때 길이는
     * 알되 탐색하지 못한다).
     */
    fun fill(frame: ByteArray, at: Int, tag: String, frames: Int, bytes: Long, toc: ByteArray?) {
        for (i in at until frame.size) frame[i] = 0
        tag.toByteArray(Charsets.US_ASCII).copyInto(frame, at)
        val withToc = toc != null && at + TAG_BYTES <= frame.size
        frame.putU32be(at + 4, if (withToc) 7 else 3) // 프레임 수 + 바이트 수 (+ 목차)
        frame.putU32be(at + 8, frames.toLong())
        frame.putU32be(at + 12, bytes)
        if (withToc) toc!!.copyInto(frame, at + 16)
    }

    /** 목차 100 칸. [offsetOf] 는 프레임 차례 → 머리 프레임 처음에서의 자리. 프레임이 없으면 null. */
    fun toc(frames: Int, dataSize: Long, offsetOf: (Int) -> Long): ByteArray? {
        if (frames <= 0 || dataSize <= 0) return null
        val out = ByteArray(100)
        for (i in 0 until 100) {
            val frame = (i.toLong() * frames / 100).toInt().coerceAtMost(frames - 1)
            out[i] = (offsetOf(frame) * 256 / dataSize).coerceIn(0, 255).toByte()
        }
        return out
    }
}

/** ID3v2 안을 고치는 작은 셈 — `TLEN`(길이, 밀리초)을 찾아 같은 길이의 숫자로 바꾼다. */
internal object Id3 {

    /**
     * `TLEN` 값의 숫자 글자가 놓인 자리(파일 안의 바이트, 끝 포함). 못 찾거나 고칠 수 없는 꼴(비동기화·압축·암호화·UTF-16·숫자가
     * 아닌 값)이면 null. ID3v2.3·2.4 만 본다.
     */
    fun tlenDigits(r: ByteSource, tagLength: Long): LongRange? {
        if (tagLength < 20 || r.ascii(0, 3) != "ID3") return null
        val major = r.u8(3)
        if (major != 3 && major != 4) return null
        val flags = r.u8(5)
        if (flags < 0 || flags and 0x80 != 0) return null // 태그 전체 비동기화 — 바이트가 바뀌어 있다
        var pos = 10L
        if (flags and 0x40 != 0) {
            // 확장 머리. 2.3 은 크기(4)가 자기를 빼고, 2.4 는 동기 안전 정수로 자기를 넣어 적는다.
            val size = if (major == 4) synchsafe(r, pos) else be32(r, pos)
            if (size < 0) return null
            pos += if (major == 4) size else size + 4
        }
        val end = tagLength - if (flags and 0x10 != 0) 10 else 0
        var frames = 0
        while (pos + 10 <= end && frames++ < MAX_FRAMES) {
            if (r.u8(pos) == 0) break // 채움이 시작됐다
            val id = r.ascii(pos, 4)
            val size = if (major == 4) synchsafe(r, pos + 4) else be32(r, pos + 4)
            val frameFlags = (r.u8(pos + 8) shl 8) or r.u8(pos + 9)
            val body = pos + 10
            if (size <= 0 || body + size > end) return null
            if (id == "TLEN") {
                // 2.3: 압축 0x80·암호 0x40·묶음 0x20, 2.4: 묶음 0x40·압축 0x08·암호 0x04·비동기화 0x02·길이 0x01(둘째 바이트).
                val bad = if (major == 4) frameFlags and 0x004F != 0 else frameFlags and 0x00E0 != 0
                if (bad) return null
                val encoding = r.u8(body)
                if (encoding != 0 && encoding != 3) return null
                val from = body + 1
                var to = body + size // 끝은 빼고
                while (to > from && r.u8(to - 1) == 0) to--
                if (to <= from) return null
                for (p in from until to) if (r.u8(p) !in '0'.code..'9'.code) return null
                return from until to
            }
            pos = body + size
        }
        return null
    }

    /** [value] 를 [width] 자리 숫자로(앞을 0 으로 채운다). 자리가 모자라면 null — 그때는 고치지 않는다. */
    fun digits(value: Long, width: Int): ByteArray? {
        val s = value.coerceAtLeast(0).toString()
        if (s.length > width) return null
        return s.padStart(width, '0').toByteArray(Charsets.US_ASCII)
    }

    private fun synchsafe(r: ByteSource, at: Long): Long {
        var v = 0L
        for (i in 0 until 4) {
            val b = r.u8(at + i)
            if (b < 0 || b >= 0x80) return -1
            v = (v shl 7) or b.toLong()
        }
        return v
    }

    private fun be32(r: ByteSource, at: Long): Long {
        var v = 0L
        for (i in 0 until 4) {
            val b = r.u8(at + i)
            if (b < 0) return -1
            v = (v shl 8) or b.toLong()
        }
        return v
    }

    private const val MAX_FRAMES = 10_000
}
